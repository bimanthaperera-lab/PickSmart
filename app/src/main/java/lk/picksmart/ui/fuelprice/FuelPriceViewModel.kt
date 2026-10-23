package lk.picksmart.ui.fuelprice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import lk.picksmart.PicksmartApplication
import lk.picksmart.data.preferences.UserPreferencesRepository
import lk.picksmart.data.repository.VehicleRepository
import lk.picksmart.domain.usecase.FuelPriceState
import lk.picksmart.domain.usecase.FuelPriceUpdates
import lk.picksmart.domain.usecase.PublishedPriceAction
import lk.picksmart.ui.components.FieldError

sealed interface FuelPriceUiState {
    data object Loading : FuelPriceUiState
    data object NoVehicle : FuelPriceUiState

    /**
     * @property prices The default vehicle, the published list and what to do about it.
     * @property configured This build has a price file to check; without one, published prices
     *   and the auto-update switch are not offered.
     * @property lastCheckAtMillis When the price file last answered, or null if never.
     */
    data class Ready(
        val prices: FuelPriceState,
        val form: FuelPriceFormState,
        val configured: Boolean,
        val lastCheckAtMillis: Long?,
        val priceError: FieldError? = null,
        val busy: Boolean = false,
        val failed: Boolean = false,
    ) : FuelPriceUiState
}

/**
 * The default vehicle's fuel type and price, the published price prompt, and the auto-update
 * switch (CLAUDE.md section 8a). Reads only what is on the device.
 */
class FuelPriceViewModel(
    private val updates: FuelPriceUpdates,
    private val vehicles: VehicleRepository,
    preferences: UserPreferencesRepository,
    private val configured: Boolean,
) : ViewModel() {

    private val form = MutableStateFlow<FuelPriceFormState?>(null)
    private val status = MutableStateFlow(Status())

    private data class Status(val priceError: FieldError? = null, val busy: Boolean = false, val failed: Boolean = false)

    val state: StateFlow<FuelPriceUiState> = combine(
        updates.state,
        form,
        preferences.fuelPriceLastCheckAt,
        status,
    ) { prices, form, lastCheck, status ->
        when {
            prices.vehicle == null -> FuelPriceUiState.NoVehicle
            form == null -> FuelPriceUiState.Loading
            else -> FuelPriceUiState.Ready(prices, form, configured, lastCheck, status.priceError, status.busy, status.failed)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FuelPriceUiState.Loading)

    init {
        // Follow the vehicle (e.g. after "Update") unless the rider has unsaved changes.
        viewModelScope.launch {
            updates.state.map { it.vehicle }.distinctUntilChanged().collect { vehicle ->
                if (vehicle != null) form.update { current -> if (current == null || !current.isChanged) FuelPriceFormState.from(vehicle) else current }
            }
        }
    }

    fun updateForm(newForm: FuelPriceFormState) {
        form.value = newForm
        status.update { it.copy(priceError = null, failed = false) }
    }

    /** Saves a typed fuel type and price; the price becomes "set by you" and auto-update turns off. */
    fun save() {
        val current = state.value as? FuelPriceUiState.Ready ?: return
        val vehicle = current.prices.vehicle ?: return
        current.form.priceError()?.let { error -> return status.update { it.copy(priceError = error) } }
        val cents = current.form.priceCents() ?: return
        write { vehicles.setFuelPrice(vehicle.id, current.form.fuelType, cents) }
    }

    fun accept(offer: PublishedPriceAction.Offer) = withVehicle { updates.accept(it, offer) }

    fun keep(offer: PublishedPriceAction.Offer) = withVehicle { updates.keep(it, offer) }

    fun setAutoUpdate(on: Boolean) = withVehicle { updates.setAutoUpdate(it, on) }

    fun dismissAutoNotice() {
        viewModelScope.launch { updates.dismissAutoNotice() }
    }

    private fun withVehicle(action: suspend (vehicleId: Long) -> Unit) {
        val id = (state.value as? FuelPriceUiState.Ready)?.prices?.vehicle?.id ?: return
        write { action(id) }
    }

    /** Runs a write, then starts the form again from the saved vehicle. */
    private fun write(change: suspend () -> Unit) {
        if (status.value.busy) return
        status.value = Status(busy = true)
        viewModelScope.launch {
            try {
                val vehicleId = (state.value as? FuelPriceUiState.Ready)?.prices?.vehicle?.id
                change()
                vehicleId?.let { vehicles.get(it) }?.let { form.value = FuelPriceFormState.from(it) }
                status.value = Status()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status.value = Status(failed = true)
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as PicksmartApplication).container
                FuelPriceViewModel(c.fuelPriceUpdates, c.vehicles, c.userPreferences, c.fuelPriceSource.isConfigured)
            }
        }
    }
}
