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
import lk.picksmart.data.repository.AlertRepository
import lk.picksmart.data.repository.ExpenseRepository
import lk.picksmart.data.repository.OtherIncomeRepository
import lk.picksmart.data.repository.TripRepository
import lk.picksmart.data.repository.VehicleRepository
import lk.picksmart.domain.calculator.ProfitCalculator
import lk.picksmart.domain.usecase.AccountingRecords
import lk.picksmart.domain.usecase.DateRange
import lk.picksmart.domain.usecase.DistanceRecords
import lk.picksmart.domain.usecase.ReportPeriod
import lk.picksmart.domain.usecase.distanceRecords
import lk.picksmart.domain.usecase.rangeContaining
import lk.picksmart.domain.usecase.PeriodSummary
import lk.picksmart.domain.usecase.FuelPriceState
import lk.picksmart.domain.usecase.FuelPriceUpdates
import lk.picksmart.domain.usecase.VehicleScope
import lk.picksmart.domain.usecase.activeVehicleScope
import lk.picksmart.domain.usecase.estimate
import lk.picksmart.domain.usecase.summarizePeriod
import lk.picksmart.ui.history.HistoryRow
import lk.picksmart.util.ColomboZone
import java.time.Clock

sealed interface HomeUiState {
    data object Loading : HomeUiState

    /**
     * @property vehicleName The active vehicle, named on the profit card; null before one exists.
     * @property sharedLeftOut True when [summary] is one of several vehicles' and today has other
     *   income or expenses with no vehicle, which belong to no single vehicle and so aren't in it.
     * @property recent The active vehicle's newest few trips of any day, offers included (they
     *   are marked as such).
     * @property month This calendar month so far, for the month's profit card.
     * @property distance The active vehicle's all-time distance and longest trip.
     */
    data class Loaded(
        val summary: PeriodSummary,
        val month: PeriodSummary,
        val distance: DistanceRecords,
        val vehicleName: String?,
        val sharedLeftOut: Boolean,
        val recent: List<HistoryRow>,
    ) : HomeUiState
}

/**
 * Today's and this month's figures (estimated view, CLAUDE.md section 7) for the active vehicle,
 * its all-time distance and longest trip, and the latest trips. Switching the vehicle switches the figures. Works offline.
 */
class HomeViewModel(
    trips: TripRepository,
    otherIncome: OtherIncomeRepository,
    expenses: ExpenseRepository,
    vehicles: VehicleRepository,
    calculator: ProfitCalculator,
    clock: Clock,
    private val fuelPriceUpdates: FuelPriceUpdates,
    alerts: AlertRepository,
) : ViewModel() {

    // Home's day is the estimated view, which uses no fill-ups.
    private val records = combine(trips.all, otherIncome.all, expenses.all) { t, i, e -> AccountingRecords(t, i, e, emptyList()) }

    val state: StateFlow<HomeUiState> = combine(records, vehicles.defaultVehicle, vehicles.allVehicles) { all, active, vehicleList ->
        val date = clock.instant().atZone(ColomboZone).toLocalDate()
        val today = DateRange.day(date)
        val scope = activeVehicleScope(active, vehicleList)
        val shown = all.forVehicle(scope)
        HomeUiState.Loaded(
            summary = calculator.summarizePeriod(today, shown),
            month = calculator.summarizePeriod(ReportPeriod.MONTH.rangeContaining(date), shown),
            distance = distanceRecords(shown.trips),
            vehicleName = active?.name,
            sharedLeftOut = scope != VehicleScope.All && all.hasSharedRecordsIn(today),
            recent = shown.trips.take(RECENT_TRIPS).map { HistoryRow(it, calculator.estimate(it)) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    /** A new published fuel price, or an automatic update to tell the rider about; null until loaded. */
    val fuelPrices: StateFlow<FuelPriceState?> = fuelPriceUpdates.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Unread alerts, for the bell's badge. */
    val unreadAlerts: StateFlow<Int> = alerts.unreadCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun dismissFuelPriceNotice() {
        viewModelScope.launch { fuelPriceUpdates.dismissAutoNotice() }
    }

    companion object {
        private const val RECENT_TRIPS = 3

        val Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as PicksmartApplication).container
                HomeViewModel(c.trips, c.otherIncome, c.expenses, c.vehicles, c.profitCalculator, c.clock, c.fuelPriceUpdates, c.alerts)
            }
        }
    }
}
