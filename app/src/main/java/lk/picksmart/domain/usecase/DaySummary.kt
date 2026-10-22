package lk.picksmart.domain.usecase

import lk.picksmart.domain.calculator.CalculationResult
import lk.picksmart.domain.calculator.ProfitCalculator
import lk.picksmart.domain.model.Expense
import lk.picksmart.domain.model.OtherIncome
import lk.picksmart.domain.model.Trip
import lk.picksmart.domain.model.TripStatus
import lk.picksmart.util.ColomboZone
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * One day's figures for Home, in the estimated view of CLAUDE.md section 7. Money is in cents.
 *
 * @property estimatedProfitCents Completed and cancelled trips' estimated profit (after fees,
 *   estimated fuel and maintenance reserve) plus other income. Offers never count (N1). Expenses
 *   and fuel logs are not part of this view; they belong to the cash view.
 * @property earnedAfterFeesCents What the trips paid after the platform fee (cancellation fees for
 *   cancelled trips), plus other income: money earned before fuel and maintenance.
 * @property recordedExpensesCents The day's [Expense] entries, shown beside the estimate, never
 *   subtracted from it. Fuel is not an expense (it lives in the fuel log).
 * @property distanceKm Distance actually driven on counted trips: all of a completed trip, the
 *   pickup and return of a cancelled one.
 * @property tripProfitPerKmCents Trip profit ÷ [distanceKm], rounded half up; other income is left
 *   out because it isn't earned per km. Null with no distance.
 */
data class DaySummary(
    val date: LocalDate,
    val estimatedProfitCents: Long,
    val tripProfitCents: Long,
    val otherIncomeCents: Long,
    val earnedAfterFeesCents: Long,
    val recordedExpensesCents: Long,
    val completedTrips: Int,
    val cancelledTrips: Int,
    val distanceKm: Double,
    val tripProfitPerKmCents: Long?,
) {
    /** True when nothing has been recorded that counts towards the day. */
    val isEmpty: Boolean get() = completedTrips == 0 && cancelledTrips == 0 && otherIncomeCents == 0L
}

/**
 * Builds [DaySummary] for the Asia/Colombo calendar [date] (CLAUDE.md section 7). Each amount is
 * counted exactly once: a trip's estimate from its own rate snapshot, other income as entered,
 * expenses only in their own figure. Offers are skipped entirely.
 */
fun ProfitCalculator.summarizeDay(
    date: LocalDate,
    trips: List<Trip>,
    otherIncome: List<OtherIncome>,
    expenses: List<Expense>,
): DaySummary {
    val counted = trips.filter {
        it.status != TripStatus.OFFER_CHECK && it.tripDateTime.atZone(ColomboZone).toLocalDate() == date
    }
    var tripProfit = 0L
    var tripEarned = 0L
    var distance = BigDecimal.ZERO
    for (trip in counted) {
        val input = trip.profitInput()
        val result = (calculate(input) as? CalculationResult.Success)?.result ?: continue
        tripProfit += result.netProfitCents
        tripEarned += input.fareCents - result.commissionCents
        distance += BigDecimal.valueOf(input.pickupKm) + BigDecimal.valueOf(input.tripKm) + BigDecimal.valueOf(input.returnKm)
    }
    val income = otherIncome.filter { it.date == date }.sumOf { it.amountCents }
    return DaySummary(
        date = date,
        estimatedProfitCents = tripProfit + income,
        tripProfitCents = tripProfit,
        otherIncomeCents = income,
        earnedAfterFeesCents = tripEarned + income,
        recordedExpensesCents = expenses.filter { it.date == date }.sumOf { it.amountCents },
        completedTrips = counted.count { it.status == TripStatus.COMPLETED },
        cancelledTrips = counted.count { it.status == TripStatus.CANCELLED },
        distanceKm = distance.toDouble(),
        tripProfitPerKmCents = if (distance.signum() > 0) {
            BigDecimal.valueOf(tripProfit).divide(distance, 0, RoundingMode.HALF_UP).longValueExact()
        } else {
            null
        },
    )
}
