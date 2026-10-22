package lk.picksmart.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import lk.picksmart.PicksmartApplication
import lk.picksmart.data.repository.TripRepository
import lk.picksmart.domain.calculator.ProfitCalculator
import lk.picksmart.util.ColomboZone
import java.time.Clock
import java.time.LocalDate

sealed interface HistoryUiState {
    data object Loading : HistoryUiState

    /** No trips saved at all yet. */
    data object Empty : HistoryUiState

    /** [days] is empty when the filter matches nothing. */
    data class Loaded(val filter: HistoryFilter, val days: List<HistoryDay>, val today: LocalDate) : HistoryUiState
}

class HistoryViewModel(
    private val savedStateHandle: SavedStateHandle,
    trips: TripRepository,
    calculator: ProfitCalculator,
    clock: Clock,
) : ViewModel() {

    private val filter = savedStateHandle.getStateFlow(FILTER_KEY, HistoryFilter.ALL)

    val state: StateFlow<HistoryUiState> = combine(trips.all, filter) { all, filter ->
        if (all.isEmpty()) {
            HistoryUiState.Empty
        } else {
            HistoryUiState.Loaded(filter, groupTripsByDay(all, filter, calculator), clock.instant().atZone(ColomboZone).toLocalDate())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState.Loading)

    fun setFilter(filter: HistoryFilter) {
        savedStateHandle[FILTER_KEY] = filter
    }

    companion object {
        private const val FILTER_KEY = "filter"

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as PicksmartApplication).container
                HistoryViewModel(createSavedStateHandle(), container.trips, container.profitCalculator, container.clock)
            }
        }
    }
}
