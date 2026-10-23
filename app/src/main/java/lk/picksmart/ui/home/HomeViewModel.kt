package lk.picksmart.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import lk.picksmart.PicksmartApplication
import lk.picksmart.data.repository.ExpenseRepository
import lk.picksmart.data.repository.OtherIncomeRepository
import lk.picksmart.data.repository.TripRepository
import lk.picksmart.data.repository.VehicleRepository
import lk.picksmart.domain.calculator.ProfitCalculator
import lk.picksmart.domain.usecase.DaySummary
import lk.picksmart.domain.usecase.FuelPriceState
import lk.picksmart.domain.usecase.FuelPriceUpdates
import lk.picksmart.domain.usecase.estimate
import lk.picksmart.domain.usecase.summarizeDay
import lk.picksmart.ui.history.HistoryRow
import lk.picksmart.util.ColomboZone
import java.time.Clock

sealed interface HomeUiState {
    data object Loading : HomeUiState

    /**
     * @property vehicleName The default vehicle, named on the profit card; null before one exists.
     * @property recent The newest few trips of any day, offers included (they are marked as such).
     */
    data class Loaded(
        val summary: DaySummary,
        val vehicleName: String?,
        val recent: List<HistoryRow>,
    ) : HomeUiState
}

/** Today's figures (estimated view, CLAUDE.md section 7) and the latest trips. Works offline. */
class HomeViewModel(
    trips: TripRepository,
    otherIncome: OtherIncomeRepository,
    expenses: ExpenseRepository,
    vehicles: VehicleRepository,
    calculator: ProfitCalculator,
    clock: Clock,
    private val fuelPriceUpdates: FuelPriceUpdates,
) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        trips.all,
        otherIncome.all,
        expenses.all,
        vehicles.defaultVehicle,
    ) { allTrips, income, costs, vehicle ->
        val today = clock.instant().atZone(ColomboZone).toLocalDate()
        HomeUiState.Loaded(
            summary = calculator.summarizeDay(today, allTrips, income, costs),
            vehicleName = vehicle?.name,
            recent = allTrips.take(RECENT_TRIPS).map { HistoryRow(it, calculator.estimate(it)) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    /** A new published fuel price, or an automatic update to tell the rider about; null until loaded. */
    val fuelPrices: StateFlow<FuelPriceState?> = fuelPriceUpdates.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun dismissFuelPriceNotice() {
        viewModelScope.launch { fuelPriceUpdates.dismissAutoNotice() }
    }

    companion object {
        private const val RECENT_TRIPS = 3

        val Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as PicksmartApplication).container
                HomeViewModel(c.trips, c.otherIncome, c.expenses, c.vehicles, c.profitCalculator, c.clock, c.fuelPriceUpdates)
            }
        }
    }
}
