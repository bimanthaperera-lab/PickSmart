package lk.picksmart.domain.usecase

import lk.picksmart.domain.calculator.ProfitCalculator
import lk.picksmart.domain.model.Expense
import lk.picksmart.domain.model.FuelLog
import lk.picksmart.domain.model.OtherIncome
import lk.picksmart.domain.model.Trip
import lk.picksmart.domain.model.Vehicle

/** Whose records a report or Home's day counts: every vehicle's, or one vehicle's. */
sealed interface VehicleScope {
    data object All : VehicleScope

    data class One(val vehicleId: Long) : VehicleScope
}

/**
 * Reports and Home follow the active vehicle, so switching vehicles switches the figures. A rider
 * with a single vehicle (archived ones included in [vehicles]) sees every record, other income
 * too, as before vehicles could be switched; so does one with no active vehicle yet.
 */
fun activeVehicleScope(active: Vehicle?, vehicles: List<Vehicle>): VehicleScope =
    if (active != null && vehicles.size > 1) VehicleScope.One(active.id) else VehicleScope.All

/** The records [summarizePeriod] works from, before or after [forVehicle]. */
data class AccountingRecords(
    val trips: List<Trip>,
    val otherIncome: List<OtherIncome>,
    val expenses: List<Expense>,
    val fuelLogs: List<FuelLog>,
) {
    val isEmpty: Boolean get() = trips.isEmpty() && otherIncome.isEmpty() && expenses.isEmpty() && fuelLogs.isEmpty()

    /**
     * The records that belong to [scope]. For one vehicle: its trips, its fill-ups and the expenses
     * saved on it. Other income (incentives, tips, bonuses) has no vehicle, and neither do expenses
     * saved without one, so they are left out rather than guessed; [All] counts them. Each record
     * belongs to at most one vehicle, so the vehicles' totals never count anything twice (N2).
     */
    fun forVehicle(scope: VehicleScope): AccountingRecords = when (scope) {
        VehicleScope.All -> this
        is VehicleScope.One -> AccountingRecords(
            trips = trips.filter { it.vehicleId == scope.vehicleId },
            otherIncome = emptyList(),
            expenses = expenses.filter { it.vehicleId == scope.vehicleId },
            fuelLogs = fuelLogs.filter { it.vehicleId == scope.vehicleId },
        )
    }

    /**
     * True when [range] has records that no single vehicle owns (other income, or expenses saved
     * without a vehicle), so a one-vehicle report says they are not in it.
     */
    fun hasSharedRecordsIn(range: DateRange): Boolean =
        otherIncome.any { it.date in range } || expenses.any { it.vehicleId == null && it.date in range }
}

/** [summarizePeriod] over [records]. */
fun ProfitCalculator.summarizePeriod(range: DateRange, records: AccountingRecords): PeriodSummary =
    summarizePeriod(range, records.trips, records.otherIncome, records.expenses, records.fuelLogs)

/** [summarizeEachDay] over [records]. */
fun ProfitCalculator.summarizeEachDay(range: DateRange, records: AccountingRecords): List<PeriodSummary> =
    summarizeEachDay(range, records.trips, records.otherIncome, records.expenses, records.fuelLogs)
