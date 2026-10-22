package lk.picksmart.domain.usecase

import lk.picksmart.domain.model.FuelLog
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/**
 * The vehicle's real km per litre, measured between two full-tank fills.
 *
 * @property kmPerLitre Rounded half up to one decimal, as shown to the rider.
 * @property toLogId The later of the two fills. A new full-tank fill gives a new reading, so a
 *   dismissed suggestion is remembered by this id.
 */
data class EfficiencyReading(
    val kmPerLitre: Double,
    val km: Double,
    val litres: Double,
    val fromLogId: Long,
    val toLogId: Long,
)

/** A suggestion appears when the reading differs from the vehicle's setting by at least this share. */
const val EFFICIENCY_SUGGESTION_SHARE = 0.10

/**
 * Actual km/L for [vehicleId] (CLAUDE.md section 8): km between two full-tank fills that both have
 * an odometer reading, divided by every litre added after the first of them, up to and including
 * the second. Partial fills, and full fills without a reading, in between still count their litres.
 *
 * Fills are taken in date order (then in the order they were entered). The latest valid pair wins;
 * a pair whose odometer didn't go up (a typo, say) is skipped for the one before it. Returns null
 * until there are two such fills.
 */
fun latestEfficiency(logs: List<FuelLog>, vehicleId: Long): EfficiencyReading? {
    val ordered = logs.filter { it.vehicleId == vehicleId }.sortedWith(compareBy({ it.date }, { it.id }))
    val anchors = ordered.indices.filter { ordered[it].fullTank && ordered[it].odometerKm != null }
    for (k in anchors.lastIndex downTo 1) {
        val from = ordered[anchors[k - 1]]
        val to = ordered[anchors[k]]
        val km = BigDecimal.valueOf(to.odometerKm!!) - BigDecimal.valueOf(from.odometerKm!!)
        val litres = ordered.subList(anchors[k - 1] + 1, anchors[k] + 1)
            .fold(BigDecimal.ZERO) { sum, log -> sum + BigDecimal.valueOf(log.litres) }
        if (km.signum() > 0 && litres.signum() > 0) {
            return EfficiencyReading(
                kmPerLitre = km.divide(litres, 1, RoundingMode.HALF_UP).toDouble(),
                km = km.toDouble(),
                litres = litres.toDouble(),
                fromLogId = from.id,
                toLogId = to.id,
            )
        }
    }
    return null
}

/**
 * True when [reading] differs noticeably from the vehicle's [currentKmPerLitre]. The app only ever
 * suggests the change; the rider decides, and the setting is never changed automatically.
 */
fun suggestsEfficiencyUpdate(reading: EfficiencyReading, currentKmPerLitre: Double): Boolean =
    currentKmPerLitre > 0 && abs(reading.kmPerLitre - currentKmPerLitre) / currentKmPerLitre >= EFFICIENCY_SUGGESTION_SHARE
