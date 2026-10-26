package lk.picksmart.domain.usecase

import lk.picksmart.domain.calculator.CalculationResult
import lk.picksmart.domain.calculator.ProfitCalculator
import lk.picksmart.domain.calculator.ProfitInput
import lk.picksmart.domain.calculator.ProfitResult
import lk.picksmart.domain.model.CommissionType
import lk.picksmart.domain.model.FareMode
import lk.picksmart.domain.model.LastCheckInputs
import lk.picksmart.domain.model.Vehicle
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The numbers found on a ride offer by the offer reader (CLAUDE.md section 9b). Untrusted (N6):
 * shown as an estimate, never saved as it is.
 *
 * @property pickupKm Distance to the pickup, or null when the offer didn't say which part is which.
 * @property tripKm Distance of the trip itself.
 */
data class OfferReading(val fareCents: Long, val pickupKm: Double?, val tripKm: Double?) {
    val totalKm: Double get() = (pickupKm ?: 0.0) + (tripKm ?: 0.0)
}

private val MONEY_BEFORE = Regex("""(?i)(?<![\w+])(?:rs\.?|lkr|රු\.?)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")
private val MONEY_AFTER = Regex("""(?i)(?<![\w.,])([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(?:lkr|rs\.?)(?!\w)""")
private val DISTANCE = Regex("""(?i)(?<![\w.,])([0-9]+(?:[.,][0-9]{1,2})?)\s*(?:km|kms|kilometres?|kilometers?)(?!\w)""")
private val PICKUP_WORDS = Regex("""(?i)\b(away|pick ?-?up|to pick|to customer|to rider)\b""")
private val TRIP_WORDS = Regex("""(?i)\b(trip|ride|drop ?-?off|journey|destination)\b""")

/** Fares above this (Rs. 1,000,000) are not a ride; distances above [MAX_KM] are not either. */
private const val MAX_FARE_CENTS = 100_000_000L
private const val MAX_KM = 500.0

/**
 * Finds the fare and distances in the text of an offer card, given as the screen's text lines in
 * reading order. Returns null unless both a fare and at least one distance are found, so the badge
 * never shows a result built on a guess.
 *
 * - **Fare:** the first amount marked "Rs", "Rs.", "LKR" or "රු", before or after the number,
 *   with thousands separators allowed ("LKR 1,250.00", "850 LKR"). An amount with a leading "+"
 *   (a bonus line such as "+LKR 50") is skipped.
 * - **Distances:** numbers followed by "km". A line that says "away" or "pickup" is the pickup; one
 *   that says "trip", "ride" or "drop-off" is the trip. Unlabelled distances fill the pickup, then
 *   the trip, in reading order; a single unlabelled distance is the trip. "4,2 km" reads as 4.2.
 * - Lines with more than 10 digits (phone numbers, ids) are ignored.
 */
fun parseOfferText(lines: List<String>): OfferReading? {
    val usable = lines.map { it.trim() }.filter { line -> line.isNotEmpty() && line.count(Char::isDigit) <= 10 }
    val fare = usable.firstNotNullOfOrNull(::fareIn) ?: return null

    var pickup: Double? = null
    var trip: Double? = null
    val unlabelled = mutableListOf<Double>()
    for (line in usable) {
        for (match in DISTANCE.findAll(line)) {
            val km = match.groupValues[1].replace(',', '.').toBigDecimalOrNull()?.toDouble() ?: continue
            if (km > MAX_KM) continue
            when {
                PICKUP_WORDS.containsMatchIn(line) && pickup == null -> pickup = km
                TRIP_WORDS.containsMatchIn(line) && trip == null -> trip = km
                else -> unlabelled += km
            }
        }
    }
    if (unlabelled.size == 1 && trip == null) {
        trip = unlabelled.single()
    } else {
        for (km in unlabelled) {
            when {
                pickup == null -> pickup = km
                trip == null -> trip = km
            }
        }
    }
    if (pickup == null && trip == null) return null
    return OfferReading(fare, pickup, trip)
}

private fun fareIn(line: String): Long? {
    val match = MONEY_BEFORE.find(line) ?: MONEY_AFTER.find(line) ?: return null
    val start = match.range.first
    if (start > 0 && line[start - 1] == '+') return null
    val cents = match.groupValues[1].replace(",", "").toBigDecimalOrNull()
        ?.movePointRight(2)?.setScale(0, RoundingMode.HALF_UP) ?: return null
    if (cents.signum() <= 0 || cents > BigDecimal.valueOf(MAX_FARE_CENTS)) return null
    return cents.longValueExact()
}

/**
 * The profit of [reading] with the same calculator and rates as Check a Ride: [vehicle]'s rates
 * and target, and the fee settings the rider last used ([last]), so a payout-mode rider gets
 * payout maths. No return distance or other costs are added; the rider can add them on Check a
 * Ride. Null only if the stored rates can't be calculated.
 */
fun ProfitCalculator.checkOffer(reading: OfferReading, vehicle: Vehicle, last: LastCheckInputs): ProfitResult? {
    val gross = last.fareMode == FareMode.GROSS_BEFORE_COMMISSION
    val input = ProfitInput(
        fareCents = reading.fareCents,
        fareMode = last.fareMode,
        commissionType = if (gross) last.commissionType else CommissionType.AMOUNT,
        commissionHundredths = if (gross) last.commissionHundredths ?: 0L else 0L,
        pickupKm = reading.pickupKm ?: 0.0,
        tripKm = reading.tripKm ?: 0.0,
        fuelEfficiencyKmPerLitre = vehicle.fuelEfficiencyKmPerLitre,
        fuelPricePerLitreCents = vehicle.fuelPricePerLitreCents,
        maintenanceReservePerKmCents = vehicle.maintenanceReservePerKmCents,
        targetProfitPerKmCents = vehicle.targetProfitPerKmCents,
    )
    return (calculate(input) as? CalculationResult.Success)?.result
}
