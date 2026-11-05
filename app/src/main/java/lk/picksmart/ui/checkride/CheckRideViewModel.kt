package lk.picksmart.ui.checkride

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import lk.picksmart.PicksmartApplication
import lk.picksmart.data.preferences.UserPreferencesRepository
import lk.picksmart.data.repository.TripRepository
import lk.picksmart.data.repository.VehicleRepository
import lk.picksmart.domain.calculator.ProfitCalculator
import lk.picksmart.domain.model.TripStatus
import lk.picksmart.domain.model.Vehicle
import lk.picksmart.domain.model.rates
import lk.picksmart.domain.usecase.tripInputFromCheck
import java.time.Clock

sealed interface CheckRideUiState {
    data object Loading : CheckRideUiState

    /** No default vehicle (its row is missing). Check a Ride needs one for its rates. */
    data object NoVehicle : CheckRideUiState

    /** @property vehicle The active vehicle, whose rates the check uses. It is switched from the Account tab. */
    data class Ready(
        val vehicle: Vehicle,
        val form: CheckRideFormState,
        val outcome: CheckRideOutcome,
        val saving: Boolean = false,
    ) : CheckRideUiState
}

/** One-off confirmations shown after a save. */
enum class CheckRideMessage { OFFER_SAVED, TRIP_SAVED, SAVE_FAILED, TARGET_SAVE_FAILED }

/**
 * Check a Ride: recalculates on every keystroke, entirely on the device. The form starts from the
 * last-used values and the default vehicle's rates, and remembers its values (not the fare, not
 * the rate overrides) for next time. A result can be saved as an offer or as a completed trip.
 */
class CheckRideViewModel(
    private val vehicles: VehicleRepository,
    private val trips: TripRepository,
    private val preferences: UserPreferencesRepository,
    private val calculator: ProfitCalculator,
    private val clock: Clock,
    private val refreshFuelPrices: () -> Unit,
) : ViewModel() {

    /** Null until the remembered values have loaded. */
    private val form = MutableStateFlow<CheckRideFormState?>(null)
    private val saving = MutableStateFlow(false)

    private val _messages = Channel<CheckRideMessage>(Channel.BUFFERED)
    val messages: Flow<CheckRideMessage> = _messages.receiveAsFlow()

    val state: StateFlow<CheckRideUiState> = combine(
        vehicles.defaultVehicle,
        form,
        saving,
    ) { vehicle, form, saving ->
        when {
            form == null -> CheckRideUiState.Loading
            vehicle == null -> CheckRideUiState.NoVehicle
            else -> CheckRideUiState.Ready(vehicle, form, form.evaluate(vehicle.rates(), calculator), saving)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CheckRideUiState.Loading)

    init {
        viewModelScope.launch {
            val remembered = preferences.lastCheckInputs.first().toFormState()
            form.update { it ?: remembered }
        }
        rememberInputs()
    }

    /**
     * The rider opened Check a Ride: check the published fuel price in the background (throttled
     * to every 12 hours). The form never waits for it.
     */
    fun onShown() = refreshFuelPrices()

    fun updateForm(newForm: CheckRideFormState) {
        form.value = newForm
    }

    /** Opens the per-check rate fields, starting from the vehicle's current rates. */
    fun startOverrides(vehicle: Vehicle) = form.update { it?.copy(overrides = it.overrides ?: vehicle.toRateOverrides()) }

    /** Goes back to the vehicle's saved rates. */
    fun clearOverrides() = form.update { it?.copy(overrides = null) }

    /**
     * Saves the current result as an [TripStatus.OFFER_CHECK] ("Save offer") or a
     * [TripStatus.COMPLETED] trip ("I took it"), timed now, then starts a new check: the fare, the
     * locations and any rate overrides clear, everything else stays.
     */
    fun save(status: TripStatus) {
        val ready = state.value as? CheckRideUiState.Ready ?: return
        val calculated = ready.outcome as? CheckRideOutcome.Calculated ?: return
        if (saving.value) return
        saving.value = true
        viewModelScope.launch {
            val message = try {
                trips.create(
                    ready.vehicle.id,
                    tripInputFromCheck(
                        calculated.input,
                        ready.form.platform,
                        status,
                        clock.instant(),
                        pickupLocation = ready.form.pickupLocation,
                        dropOffLocation = ready.form.dropOffLocation,
                    ),
                )
                form.update { it?.copy(fare = "", overrides = null, pickupLocation = "", dropOffLocation = "") }
                if (status == TripStatus.OFFER_CHECK) CheckRideMessage.OFFER_SAVED else CheckRideMessage.TRIP_SAVED
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CheckRideMessage.SAVE_FAILED
            } finally {
                saving.value = false
            }
            _messages.send(message)
        }
    }

    /**
     * Saves the rider's profit target per km on the vehicle (null turns it off). The result
     * recalculates with it at once; saved trips keep the target in their own snapshot.
     */
    fun setTarget(targetCents: Long?) {
        val vehicle = (state.value as? CheckRideUiState.Ready)?.vehicle ?: return
        viewModelScope.launch {
            try {
                vehicles.setTargetProfitPerKm(vehicle.id, targetCents)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.send(CheckRideMessage.TARGET_SAVE_FAILED)
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun rememberInputs() {
        viewModelScope.launch {
            form.filterNotNull()
                .map { it.toLastCheckInputs() }
                .distinctUntilChanged()
                // The first value is the one just loaded; there's nothing new to save.
                .drop(1)
                .debounce(SAVE_DELAY_MILLIS)
                .collect { inputs ->
                    try {
                        preferences.setLastCheckInputs(inputs)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Remembering is a convenience; a failed write must never disturb the check.
                    }
                }
        }
    }

    companion object {
        private const val SAVE_DELAY_MILLIS = 400L

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as PicksmartApplication).container
                CheckRideViewModel(
                    container.vehicles,
                    container.trips,
                    container.userPreferences,
                    container.profitCalculator,
                    container.clock,
                    container::refreshFuelPrices,
                )
            }
        }
    }
}
