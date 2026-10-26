package lk.picksmart.ui.trip

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import lk.picksmart.PicksmartApplication
import lk.picksmart.data.preferences.UserPreferencesRepository
import lk.picksmart.data.repository.TripRepository
import lk.picksmart.data.repository.VehicleRepository
import lk.picksmart.domain.calculator.InputField
import lk.picksmart.domain.calculator.ProfitCalculator
import lk.picksmart.domain.model.TripRates
import lk.picksmart.domain.model.TripStatus
import lk.picksmart.domain.model.rates
import lk.picksmart.ui.checkride.CheckRideError
import lk.picksmart.ui.checkride.CheckRideOutcome
import lk.picksmart.ui.navigation.EditTripRoute
import lk.picksmart.ui.vehicle.RecordVehicle
import lk.picksmart.util.ColomboZone
import java.time.Clock
import java.time.LocalDate

sealed interface EditTripUiState {
    data object Loading : EditTripUiState

    /** The trip was deleted elsewhere, or the id is stale. */
    data object NotFound : EditTripUiState

    /** Adding a trip needs a vehicle for its rates. */
    data object NoVehicle : EditTripUiState

    /**
     * @property statuses The statuses the rider can pick. An offer can stay an offer; a completed
     *   or cancelled trip can't go back to being an offer.
     * @property shownErrors Live errors plus, after a save attempt, the "required" ones.
     * @property vehicle The trip's vehicle; picked while adding, fixed once saved.
     * @property fromOffer A new trip filled from the offer reader's notification, to be checked.
     */
    data class Editing(
        val form: TripFormState,
        val rates: TripRates,
        val isNew: Boolean,
        val statuses: List<TripStatus>,
        val today: LocalDate,
        val outcome: CheckRideOutcome,
        val shownErrors: Map<InputField, CheckRideError>,
        val vehicle: RecordVehicle = RecordVehicle(),
        val fromOffer: Boolean = false,
        val saving: Boolean = false,
        val saveFailed: Boolean = false,
        val done: Boolean = false,
    ) : EditTripUiState
}

/** Adds a trip, or edits one (including marking a saved offer as completed or cancelled). */
class EditTripViewModel(
    savedStateHandle: SavedStateHandle,
    private val trips: TripRepository,
    private val vehicles: VehicleRepository,
    private val preferences: UserPreferencesRepository,
    private val calculator: ProfitCalculator,
    private val clock: Clock,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<EditTripRoute>()
    private val tripId = route.tripId
    private val offer = route.offer()
    private var vehicleId = 0L
    private var recordVehicle = RecordVehicle()
    private var triedSave = false

    private val _state = MutableStateFlow<EditTripUiState>(EditTripUiState.Loading)
    val state: StateFlow<EditTripUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val now = clock.instant().atZone(ColomboZone)
            _state.value = if (tripId == EditTripRoute.NEW) {
                val vehicle = vehicles.defaultVehicle.first()
                if (vehicle == null) {
                    EditTripUiState.NoVehicle
                } else {
                    vehicleId = vehicle.id
                    recordVehicle = RecordVehicle(vehicles.activeVehicles.first(), vehicle.id, vehicle.name, editable = true)
                    val last = preferences.lastCheckInputs.first()
                    val form = offer?.let { offerTripForm(last, it) } ?: newTripForm(last, now.toLocalDate(), now.toLocalTime())
                    editing(form, vehicle.rates(), isNew = true, listOf(TripStatus.COMPLETED, TripStatus.CANCELLED), now.toLocalDate())
                }
            } else {
                val trip = trips.get(tripId)
                if (trip == null) {
                    EditTripUiState.NotFound
                } else {
                    recordVehicle = RecordVehicle(vehicles.activeVehicles.first(), trip.vehicleId, vehicles.get(trip.vehicleId)?.name)
                    val statuses = if (trip.status == TripStatus.OFFER_CHECK) TripStatus.entries else listOf(TripStatus.COMPLETED, TripStatus.CANCELLED)
                    editing(trip.toFormState(), trip.rates, isNew = false, statuses, now.toLocalDate())
                }
            }
        }
    }

    fun updateForm(form: TripFormState) = _state.update { current ->
        if (current is EditTripUiState.Editing) {
            editing(form, current.rates, current.isNew, current.statuses, current.today)
        } else {
            current
        }
    }

    /** While adding: the trip goes on [id], and is calculated and saved with that vehicle's rates. */
    fun selectVehicle(id: Long) {
        val current = _state.value as? EditTripUiState.Editing ?: return
        if (!current.isNew || current.saving) return
        val vehicle = recordVehicle.choices.firstOrNull { it.id == id } ?: return
        vehicleId = vehicle.id
        recordVehicle = recordVehicle.copy(selectedId = vehicle.id, selectedName = vehicle.name)
        _state.value = editing(current.form, vehicle.rates(), current.isNew, current.statuses, current.today)
    }

    fun save() {
        val current = _state.value as? EditTripUiState.Editing ?: return
        if (current.saving) return
        val input = when (val result = current.form.validate(current.rates, calculator)) {
            is TripFormResult.Invalid -> {
                triedSave = true
                return updateForm(current.form)
            }
            is TripFormResult.Valid -> result.input
        }
        _state.value = current.copy(saving = true, saveFailed = false)
        viewModelScope.launch {
            try {
                val ok = if (current.isNew) {
                    trips.create(vehicleId, input)
                    true
                } else {
                    trips.update(tripId, input)
                }
                _state.value = if (ok) current.copy(saving = false, done = true) else EditTripUiState.NotFound
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = current.copy(saving = false, saveFailed = true)
            }
        }
    }

    /** Called only after the rider confirms in the dialog (CLAUDE.md N7). */
    fun delete() {
        val current = _state.value as? EditTripUiState.Editing ?: return
        if (current.isNew || current.saving) return
        _state.value = current.copy(saving = true, saveFailed = false)
        viewModelScope.launch {
            try {
                trips.delete(tripId)
                _state.value = current.copy(saving = false, done = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = current.copy(saving = false, saveFailed = true)
            }
        }
    }

    private fun editing(
        form: TripFormState,
        rates: TripRates,
        isNew: Boolean,
        statuses: List<TripStatus>,
        today: LocalDate,
    ): EditTripUiState.Editing {
        val outcome = form.evaluate(rates, calculator)
        val live = (outcome as? CheckRideOutcome.Invalid)?.errors.orEmpty()
        val shown = if (triedSave) {
            (form.validate(rates, calculator) as? TripFormResult.Invalid)?.errors.orEmpty()
        } else {
            live
        }
        return EditTripUiState.Editing(form, rates, isNew, statuses, today, outcome, shown, recordVehicle, fromOffer = offer != null)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as PicksmartApplication).container
                EditTripViewModel(
                    createSavedStateHandle(),
                    container.trips,
                    container.vehicles,
                    container.userPreferences,
                    container.profitCalculator,
                    container.clock,
                )
            }
        }
    }
}
