package lk.picksmart.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import lk.picksmart.PicksmartApplication
import lk.picksmart.data.repository.TripRepository
import lk.picksmart.data.repository.VehicleRepository
import lk.picksmart.domain.calculator.ProfitCalculator
import lk.picksmart.domain.usecase.VehicleScope
import lk.picksmart.domain.usecase.activeVehicleScope
import lk.picksmart.domain.usecase.forVehicle
import lk.picksmart.util.ColomboZone
import java.time.Clock
import java.time.LocalDate

sealed interface HistoryUiState {
    data object Loading : HistoryUiState

    /** No trips saved at all yet. */
    data object Empty : HistoryUiState

    /**
     * [days] is empty when the filter, or the active vehicle, has no trips.
     *
     * @property vehicleName The active vehicle the list is for, when the rider has several; null
     *   with a single vehicle, whose list is every trip.
     */
    data class Loaded(
        val filter: HistoryFilter,
        val days: List<HistoryDay>,
        val today: LocalDate,
        val vehicleName: String?,
    ) : HistoryUiState
}

/** The active vehicle's trips (so switching the vehicle switches the list), newest first. */
class HistoryViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val trips: TripRepository,
    vehicles: VehicleRepository,
    calculator: ProfitCalculator,
    clock: Clock,
) : ViewModel() {

    private val filter = savedStateHandle.getStateFlow(FILTER_KEY, HistoryFilter.ALL)

    val state: StateFlow<HistoryUiState> = combine(trips.all, filter, vehicles.defaultVehicle, vehicles.allVehicles) { all, filter, active, vehicleList ->
        if (all.isEmpty()) {
            HistoryUiState.Empty
        } else {
            val scope = activeVehicleScope(active, vehicleList)
            val days = groupTripsByDay(all.forVehicle(scope), filter, calculator)
            val vehicleName = active?.name?.takeIf { scope != VehicleScope.All }
            HistoryUiState.Loaded(filter, days, clock.instant().atZone(ColomboZone).toLocalDate(), vehicleName)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState.Loading)

    private val _deleteFailed = MutableStateFlow(false)

    /** The last swipe delete failed; cleared by the next one. */
    val deleteFailed: StateFlow<Boolean> = _deleteFailed.asStateFlow()

    fun setFilter(filter: HistoryFilter) {
        savedStateHandle[FILTER_KEY] = filter
    }

    /** Called only after the rider confirms in the dialog (CLAUDE.md N7). */
    fun delete(tripId: Long) {
        _deleteFailed.value = false
        viewModelScope.launch {
            try {
                trips.delete(tripId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _deleteFailed.value = true
            }
        }
    }

    companion object {
        private const val FILTER_KEY = "filter"

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as PicksmartApplication).container
                HistoryViewModel(createSavedStateHandle(), container.trips, container.vehicles, container.profitCalculator, container.clock)
            }
        }
    }
}
