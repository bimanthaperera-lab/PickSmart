package lk.picksmart.ui.fuel

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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import lk.picksmart.PicksmartApplication
import lk.picksmart.data.preferences.UserPreferencesRepository
import lk.picksmart.data.repository.FuelLogRepository
import lk.picksmart.data.repository.VehicleRepository
import lk.picksmart.domain.model.FuelLog
import lk.picksmart.domain.usecase.EfficiencyReading
import lk.picksmart.domain.usecase.latestEfficiency
import lk.picksmart.domain.usecase.suggestsEfficiencyUpdate
import lk.picksmart.ui.components.FieldError
import lk.picksmart.ui.navigation.EditFuelLogRoute
import lk.picksmart.ui.vehicle.RecordVehicle
import lk.picksmart.util.ColomboZone
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

/**
 * The actual km/L card for the default vehicle.
 *
 * @property reading Null until there are two full-tank fills with odometer readings.
 * @property suggest True when the reading differs noticeably from the vehicle's setting and the
 *   rider hasn't dismissed this reading.
 */
data class EfficiencyUi(
    val vehicleId: Long,
    val vehicleKmPerLitre: Double,
    val reading: EfficiencyReading?,
    val suggest: Boolean,
)

sealed interface FuelListUiState {
    data object Loading : FuelListUiState

    /** @property efficiency Null when there is no vehicle to measure. */
    data class Loaded(
        val entries: List<FuelLog>,
        val monthTotalCents: Long,
        val today: LocalDate,
        val efficiency: EfficiencyUi?,
    ) : FuelListUiState
}

class FuelListViewModel(
    fuelLogs: FuelLogRepository,
    private val vehicles: VehicleRepository,
    private val preferences: UserPreferencesRepository,
    clock: Clock,
) : ViewModel() {

    val state: StateFlow<FuelListUiState> = combine(
        fuelLogs.all,
        vehicles.defaultVehicle,
        preferences.dismissedEfficiencyLogId,
    ) { entries, vehicle, dismissedId ->
        val today = clock.instant().atZone(ColomboZone).toLocalDate()
        val efficiency = vehicle?.let { v ->
            val reading = latestEfficiency(entries, v.id)
            EfficiencyUi(
                vehicleId = v.id,
                vehicleKmPerLitre = v.fuelEfficiencyKmPerLitre,
                reading = reading,
                suggest = reading != null && reading.toLogId != dismissedId &&
                    suggestsEfficiencyUpdate(reading, v.fuelEfficiencyKmPerLitre),
            )
        }
        FuelListUiState.Loaded(entries, entries.totalInMonth(YearMonth.from(today)), today, efficiency)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FuelListUiState.Loading)

    /** The rider tapped "Use … km/L". Only new checks use it; past trips keep their snapshot. */
    fun acceptSuggestion() {
        val efficiency = (state.value as? FuelListUiState.Loaded)?.efficiency ?: return
        val reading = efficiency.reading ?: return
        if (!efficiency.suggest) return
        viewModelScope.launch { vehicles.setFuelEfficiency(efficiency.vehicleId, reading.kmPerLitre) }
    }

    /** "Not now": this reading isn't offered again. The next full-tank fill gives a new one. */
    fun dismissSuggestion() {
        val reading = (state.value as? FuelListUiState.Loaded)?.efficiency?.reading ?: return
        viewModelScope.launch { preferences.setDismissedEfficiencyLogId(reading.toLogId) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as PicksmartApplication).container
                FuelListViewModel(container.fuelLogs, container.vehicles, container.userPreferences, container.clock)
            }
        }
    }
}

sealed interface EditFuelUiState {
    data object Loading : EditFuelUiState
    data object NotFound : EditFuelUiState

    /** A new fill-up needs a vehicle to belong to and to take its price from. */
    data object NoVehicle : EditFuelUiState

    /** @property errors Shown once the rider has tried to save, and kept up to date after that. */
    /** @property vehicle A saved fill-up's vehicle; a new one goes on the active vehicle. */
    data class Editing(
        val form: FuelFormState,
        val isNew: Boolean,
        val today: LocalDate,
        val vehicle: RecordVehicle = RecordVehicle(),
        val errors: Map<FuelField, FieldError> = emptyMap(),
        val saving: Boolean = false,
        val saveFailed: Boolean = false,
        val done: Boolean = false,
    ) : EditFuelUiState
}

/** Adds a fill-up, or edits one when [EditFuelLogRoute.fuelLogId] isn't [EditFuelLogRoute.NEW]. */
class EditFuelViewModel(
    savedStateHandle: SavedStateHandle,
    private val fuelLogs: FuelLogRepository,
    private val vehicles: VehicleRepository,
    clock: Clock,
) : ViewModel() {

    private val fuelLogId = savedStateHandle.toRoute<EditFuelLogRoute>().fuelLogId
    private var vehicleId = 0L
    private var triedSave = false

    private val _state = MutableStateFlow<EditFuelUiState>(EditFuelUiState.Loading)
    val state: StateFlow<EditFuelUiState> = _state.asStateFlow()

    init {
        val today = clock.instant().atZone(ColomboZone).toLocalDate()
        viewModelScope.launch {
            _state.value = if (fuelLogId == EditFuelLogRoute.NEW) {
                val vehicle = vehicles.defaultVehicle.first()
                if (vehicle == null) {
                    EditFuelUiState.NoVehicle
                } else {
                    vehicleId = vehicle.id
                    EditFuelUiState.Editing(newFuelForm(today, vehicle.fuelPricePerLitreCents), isNew = true, today = today)
                }
            } else {
                fuelLogs.get(fuelLogId)?.let { log ->
                    EditFuelUiState.Editing(
                        log.toFormState(),
                        isNew = false,
                        today = today,
                        vehicle = RecordVehicle(vehicles.activeVehicles.first(), log.vehicleId, vehicles.get(log.vehicleId)?.name),
                    )
                } ?: EditFuelUiState.NotFound
            }
        }
    }

    fun updateForm(form: FuelFormState) = editing { it.copy(form = form, errors = errorsFor(form), saveFailed = false) }

    fun save() {
        val current = _state.value as? EditFuelUiState.Editing ?: return
        if (current.saving) return
        val input = when (val result = current.form.validate()) {
            is FuelFormResult.Invalid -> {
                triedSave = true
                return editing { it.copy(errors = result.errors) }
            }
            is FuelFormResult.Valid -> result.input
        }
        run(current) {
            if (current.isNew) {
                fuelLogs.create(vehicleId, input)
                true
            } else {
                fuelLogs.update(fuelLogId, input)
            }
        }
    }

    /** Called only after the rider confirms in the dialog (CLAUDE.md N7). */
    fun delete() {
        val current = _state.value as? EditFuelUiState.Editing ?: return
        if (current.isNew || current.saving) return
        run(current) {
            fuelLogs.delete(fuelLogId)
            true
        }
    }

    private fun errorsFor(form: FuelFormState): Map<FuelField, FieldError> =
        if (triedSave) (form.validate() as? FuelFormResult.Invalid)?.errors.orEmpty() else emptyMap()

    private fun run(current: EditFuelUiState.Editing, action: suspend () -> Boolean) {
        _state.value = current.copy(saving = true, saveFailed = false)
        viewModelScope.launch {
            try {
                _state.value = if (action()) current.copy(saving = false, done = true) else EditFuelUiState.NotFound
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = current.copy(saving = false, saveFailed = true)
            }
        }
    }

    private fun editing(change: (EditFuelUiState.Editing) -> EditFuelUiState.Editing) =
        _state.update { if (it is EditFuelUiState.Editing) change(it) else it }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as PicksmartApplication).container
                EditFuelViewModel(createSavedStateHandle(), container.fuelLogs, container.vehicles, container.clock)
            }
        }
    }
}
