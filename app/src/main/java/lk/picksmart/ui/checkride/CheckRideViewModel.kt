package lk.picksmart.ui.checkride

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import lk.picksmart.PicksmartApplication
import lk.picksmart.data.preferences.UserPreferencesRepository
import lk.picksmart.data.repository.VehicleRepository
import lk.picksmart.domain.calculator.ProfitCalculator
import lk.picksmart.domain.model.Vehicle

sealed interface CheckRideUiState {
    data object Loading : CheckRideUiState

    /** No default vehicle (its row is missing). Check a Ride needs one for its rates. */
    data object NoVehicle : CheckRideUiState

    data class Ready(
        val vehicle: Vehicle,
        val form: CheckRideFormState,
        val outcome: CheckRideOutcome,
    ) : CheckRideUiState
}

/**
 * Check a Ride: recalculates on every keystroke, entirely on the device. The form starts from the
 * last-used values and the default vehicle's rates, and remembers its values (not the fare, not
 * the rate overrides) for next time.
 */
class CheckRideViewModel(
    vehicles: VehicleRepository,
    private val preferences: UserPreferencesRepository,
    private val calculator: ProfitCalculator,
) : ViewModel() {

    /** Null until the remembered values have loaded. */
    private val form = MutableStateFlow<CheckRideFormState?>(null)

    val state: StateFlow<CheckRideUiState> = combine(vehicles.defaultVehicle, form) { vehicle, form ->
        when {
            form == null -> CheckRideUiState.Loading
            vehicle == null -> CheckRideUiState.NoVehicle
            else -> CheckRideUiState.Ready(vehicle, form, form.evaluate(vehicle, calculator))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CheckRideUiState.Loading)

    init {
        viewModelScope.launch {
            val remembered = preferences.lastCheckInputs.first().toFormState()
            form.update { it ?: remembered }
        }
        rememberInputs()
    }

    fun updateForm(newForm: CheckRideFormState) {
        form.value = newForm
    }

    /** Opens the per-check rate fields, starting from the vehicle's current rates. */
    fun startOverrides(vehicle: Vehicle) = form.update { it?.copy(overrides = it.overrides ?: vehicle.toRateOverrides()) }

    /** Goes back to the vehicle's saved rates. */
    fun clearOverrides() = form.update { it?.copy(overrides = null) }

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
                CheckRideViewModel(container.vehicles, container.userPreferences, container.profitCalculator)
            }
        }
    }
}
