package lk.picksmart.ui.history

import lk.picksmart.domain.calculator.ProfitCalculator
import lk.picksmart.domain.calculator.ProfitResult
import lk.picksmart.domain.model.Trip
import lk.picksmart.domain.model.TripStatus
import lk.picksmart.domain.usecase.estimate
import lk.picksmart.domain.usecase.estimatedTripProfitCents
import lk.picksmart.util.ColomboZone
import java.time.LocalDate

enum class HistoryFilter(val statuses: Set<TripStatus>) {
    ALL(TripStatus.entries.toSet()),
    COMPLETED(setOf(TripStatus.COMPLETED)),
    OFFERS(setOf(TripStatus.OFFER_CHECK)),
    CANCELLED(setOf(TripStatus.CANCELLED)),
}

/**
 * One trip in History or on Home, with its estimate (null only if the stored values can't be
 * calculated). Both lists show one vehicle's trips, so rows don't name the vehicle.
 */
data class HistoryRow(val trip: Trip, val estimate: ProfitResult?)

/**
 * The trips of one Sri Lankan calendar day, newest first.
 *
 * @property estimatedProfitCents Estimated profit of the day's shown completed and cancelled
 *   trips, or null when none are shown (for example a day of offers only). Offers never count.
 */
data class HistoryDay(
    val date: LocalDate,
    val rows: List<HistoryRow>,
    val estimatedProfitCents: Long?,
)

/**
 * Filters [trips] (newest first) and groups them by their Asia/Colombo date.
 */
fun groupTripsByDay(
    trips: List<Trip>,
    filter: HistoryFilter,
    calculator: ProfitCalculator,
): List<HistoryDay> =
    trips.filter { it.status in filter.statuses }
        .groupBy { it.tripDateTime.atZone(ColomboZone).toLocalDate() }
        .map { (date, dayTrips) ->
            val counted = dayTrips.filter { it.status != TripStatus.OFFER_CHECK }
            HistoryDay(
                date = date,
                rows = dayTrips.map { HistoryRow(it, calculator.estimate(it)) },
                estimatedProfitCents = if (counted.isEmpty()) null else calculator.estimatedTripProfitCents(counted),
            )
        }
