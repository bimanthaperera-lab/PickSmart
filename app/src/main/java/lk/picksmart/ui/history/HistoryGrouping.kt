package lk.picksmart.ui.history

import lk.picksmart.domain.calculator.ProfitCalculator
import lk.picksmart.domain.calculator.ProfitResult
import lk.picksmart.domain.model.Platform
import lk.picksmart.domain.model.Trip
import lk.picksmart.domain.model.TripStatus
import lk.picksmart.domain.usecase.estimate
import lk.picksmart.domain.usecase.estimatedTripProfitCents
import lk.picksmart.util.ColomboZone
import java.time.LocalDate
import java.util.Locale

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
 * Filters [trips] (newest first) by status and by [matches] (the search), and groups them by
 * their Asia/Colombo date.
 */
fun groupTripsByDay(
    trips: List<Trip>,
    filter: HistoryFilter,
    calculator: ProfitCalculator,
    matches: (Trip) -> Boolean = { true },
): List<HistoryDay> =
    trips.filter { it.status in filter.statuses && matches(it) }
        .groupBy { it.tripDateTime.atZone(ColomboZone).toLocalDate() }
        .map { (date, dayTrips) ->
            val counted = dayTrips.filter { it.status != TripStatus.OFFER_CHECK }
            HistoryDay(
                date = date,
                rows = dayTrips.map { HistoryRow(it, calculator.estimate(it)) },
                estimatedProfitCents = if (counted.isEmpty()) null else calculator.estimatedTripProfitCents(counted),
            )
        }

/**
 * True when every word of [query] is found in the trip: in its note, in its ride app's name (as
 * shown, from [platformNames], or in English), or as its fare or cancellation fee in rupees
 * ("1450", "1,450", "Rs. 1450.50"). A number matches the whole rupees, so "100" doesn't find
 * Rs. 1,000.00; cents, when typed, match from the start ("1450.5" finds Rs. 1,450.50). Case
 * doesn't matter, and a blank query matches every trip.
 */
fun Trip.matchesSearch(query: String, platformNames: Map<Platform, String>): Boolean {
    val words = query.lowercase(Locale.ROOT).split(WHITESPACE).filter { it.isNotEmpty() }
    if (words.isEmpty()) return true
    val texts = listOfNotNull(notes, platformNames[platform], platform.name.replace('_', ' '))
        .map { it.lowercase(Locale.ROOT) }
    val amounts = listOfNotNull(fareCents.takeIf { status != TripStatus.CANCELLED }, cancellationFeeCents)
        .map { "%d.%02d".format(Locale.ROOT, it / 100, it % 100) }
    return words.all { word ->
        if (texts.any { word in it }) return@all true
        val number = word.replace(",", "").removePrefix("lkr").removePrefix("rs.").removePrefix("rs")
        when {
            // A currency word on its own ("Rs.") narrows nothing.
            number.isEmpty() -> true
            number.all { it.isDigit() || it == '.' } -> {
                val rupees = number.substringBefore('.')
                val cents = number.substringAfter('.', "")
                amounts.any { it.substringBefore('.') == rupees && it.substringAfter('.').startsWith(cents) }
            }
            else -> false
        }
    }
}

private val WHITESPACE = Regex("\\s+")
