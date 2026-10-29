package lk.picksmart.domain.usecase

import lk.picksmart.domain.model.Trip
import lk.picksmart.domain.model.TripStatus
import java.math.BigDecimal

/**
 * The all-time distance figures on Home.
 *
 * @property totalKm Distance actually driven on every trip, counted as in [summarizePeriod]: all
 *   of a completed trip, the pickup and return of a cancelled one. Offers were never driven (N1).
 * @property drivenTrips The completed and cancelled trips in [totalKm].
 * @property longestTrip The completed trip with the longest trip distance (pickup and return not
 *   counted, since they aren't the ride itself), the newest one on a tie; null when no completed
 *   trip has a trip distance.
 */
data class DistanceRecords(
    val totalKm: Double,
    val drivenTrips: Int,
    val longestTrip: Trip?,
)

/** [DistanceRecords] for [trips], in any order. */
fun distanceRecords(trips: List<Trip>): DistanceRecords {
    var total = BigDecimal.ZERO
    var driven = 0
    var longest: Trip? = null
    for (trip in trips) {
        when (trip.status) {
            TripStatus.OFFER_CHECK -> continue
            TripStatus.CANCELLED -> total += BigDecimal.valueOf(trip.pickupKm) + BigDecimal.valueOf(trip.returnKm)
            TripStatus.COMPLETED -> {
                total += BigDecimal.valueOf(trip.pickupKm) + BigDecimal.valueOf(trip.tripKm) + BigDecimal.valueOf(trip.returnKm)
                val best = longest
                val longer = best == null || trip.tripKm > best.tripKm ||
                    (trip.tripKm == best.tripKm && trip.tripDateTime > best.tripDateTime)
                if (trip.tripKm > 0 && longer) longest = trip
            }
        }
        driven++
    }
    return DistanceRecords(total.toDouble(), driven, longest)
}
