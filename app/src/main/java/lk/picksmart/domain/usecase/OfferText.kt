package lk.picksmart.domain.usecase

import lk.picksmart.domain.calculator.CalculationResult
import lk.picksmart.domain.calculator.ProfitCalculator
import lk.picksmart.domain.calculator.ProfitInput
import lk.picksmart.domain.calculator.ProfitResult
import lk.picksmart.domain.model.CommissionType
import lk.picksmart.domain.model.FareMode
import lk.picksmart.domain.model.LastCheckInputs
import lk.picksmart.domain.model.Platform
import lk.picksmart.domain.model.Vehicle
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/**
 * The numbers found on a ride offer by the offer reader (CLAUDE.md section 9b). Untrusted (N6):
 * shown as an estimate, never saved as it is.
 *
 * @property pickupKm Distance to the pickup, or null when the offer didn't say which part is which.
 * @property tripKm Distance of the trip itself.
 * @property tipCents A tip shown on the offer ("+ LKR 200" on PickMe's card), paid on top of the
 *   fare; 0 when there is none.
 */
data class OfferReading(val fareCents: Long, val pickupKm: Double?, val tripKm: Double?, val tipCents: Long = 0) {
    val totalKm: Double get() = (pickupKm ?: 0.0) + (tripKm ?: 0.0)
}

/**
 * An offer the reader showed, for the "Save this trip?" notification: its numbers, which ride
 * app it came from and when it was read. It only fills the Add trip form; the rider checks and
 * saves it there (N6).
 */
data class OfferToSave(val reading: OfferReading, val platform: Platform, val readAt: Instant)

private val MONEY_BEFORE = Regex("""(?i)(?<!\w)(?:rs\.?|lkr|රු\.?)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")
private val MONEY_AFTER = Regex("""(?i)(?<![\w.,])([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(?:lkr|rs\.?)(?!\w)""")
private val DISTANCE = Regex("""(?i)(?<![\w.,])([0-9]+(?:[.,][0-9]{1,2})?)\s*(?:km|kms|kilometres?|kilometers?)(?!\w)""")
private val PICKUP_WORDS = Regex("""(?i)\b(away|pick ?-?up|to pick|to customer|to rider)\b""")
private val TRIP_WORDS = Regex("""(?i)\b(trip|ride|drop ?-?off|journey|destination)\b""")

/** A fare drawn as two texts, the amount in a large font and the currency beside it ("299.50", "LKR"). */
private val BARE_AMOUNT = Regex("""[0-9][0-9,]*(?:\.[0-9]{1,2})?""")
private val CURRENCY_ONLY = Regex("""(?i)lkr|rs\.?|රු\.?""")

/** Nine or more digits in a row, with single spaces or dashes allowed between them: a phone number or an id. */
private val LONG_NUMBER = Regex("""[0-9](?:[ -]?[0-9]){8,}""")

/** Fares above this (Rs. 1,000,000) are not a ride; distances above [MAX_KM] are not either. */
private const val MAX_FARE_CENTS = 100_000_000L
private const val MAX_KM = 500.0

/**
 * Finds the fare and distances in the text of an offer card, given as the screen's text lines in
 * reading order. Returns null unless both a fare and at least one distance are found, so the badge
 * never shows a result built on a guess.
 *
 * - **Fare:** the first amount marked "Rs", "Rs.", "LKR" or "රු", before or after the number,
 *   with thousands separators allowed ("LKR 1,250.00", "850 LKR"). The amount and the currency may
 *   be two texts next to each other ("299.50", "LKR"), as on PickMe's card. An amount after a "+"
 *   is never the fare.
 * - **Tip:** the first amount after a "+" ("+ LKR 200" on PickMe's card, "+LKR 50"), or 0.
 * - **Distances:** numbers followed by "km". A distance whose words say "away" or "pickup" is the
 *   pickup; one whose words say "trip", "ride" or "drop-off" is the trip. Its words are its line,
 *   up to the distances either side of it, so a whole card read as one text still splits up.
 *   Unlabelled distances fill the pickup, then the trip, in reading order; a single unlabelled
 *   distance is the trip. "4,2 km" reads as 4.2.
 * - Lines with nine or more digits in a row (phone numbers, ids) are ignored.
 */
fun parseOfferText(lines: List<String>): OfferReading? {
    val usable = lines.map { it.trim() }.filter { line -> line.isNotEmpty() && !LONG_NUMBER.containsMatchIn(line) }
    val amounts = amountsIn(usable)
    val fare = amounts.firstOrNull { !it.afterPlus }?.cents ?: return null
    val tip = amounts.firstOrNull { it.afterPlus }?.cents ?: 0L

    var pickup: Double? = null
    var trip: Double? = null
    val unlabelled = mutableListOf<Double>()
    for (line in usable) {
        val matches = DISTANCE.findAll(line).toList()
        matches.forEachIndexed { i, match ->
            val km = match.groupValues[1].replace(',', '.').toBigDecimalOrNull()?.toDouble() ?: return@forEachIndexed
            if (km > MAX_KM) return@forEachIndexed
            val from = matches.getOrNull(i - 1)?.range?.last?.plus(1) ?: 0
            val to = matches.getOrNull(i + 1)?.range?.first ?: line.length
            val words = line.substring(from, to)
            when {
                PICKUP_WORDS.containsMatchIn(words) && pickup == null -> pickup = km
                TRIP_WORDS.containsMatchIn(words) && trip == null -> trip = km
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
    return OfferReading(fare, pickup, trip, tip)
}

/**
 * An [OfferReading] from numbers handed between parts of the app (the "Save this trip?"
 * notification, the Add trip route), or null unless [parseOfferText] could have found them: a
 * fare above 0 up to Rs. 1,000,000, and at least one distance, each finite and 0 to [MAX_KM]. A NaN,
 * infinite or huge value would otherwise reach the trip form and break it.
 */
fun offerReadingOrNull(fareCents: Long, pickupKm: Double?, tripKm: Double?, tipCents: Long = 0): OfferReading? {
    if (fareCents !in 1..MAX_FARE_CENTS) return null
    if (tipCents !in 0..MAX_FARE_CENTS) return null
    if (pickupKm == null && tripKm == null) return null
    if (listOfNotNull(pickupKm, tripKm).any { !it.isFinite() || it !in 0.0..MAX_KM }) return null
    return OfferReading(fareCents, pickupKm, tripKm, tipCents)
}

/** An amount marked with a currency, and whether a "+" comes just before it (a tip, not the fare). */
private class Amount(val cents: Long, val afterPlus: Boolean)

/**
 * Every amount in reading order: those within one line, and those split over a line and the next
 * (an amount and its currency as two texts, in either order).
 */
private fun amountsIn(lines: List<String>): List<Amount> = buildList {
    for (i in lines.indices) {
        val line = lines[i]
        val before = lines.getOrNull(i - 1).orEmpty()
        val inLine = (MONEY_BEFORE.findAll(line) + MONEY_AFTER.findAll(line)).sortedBy { it.range.first }
        for (match in inLine) {
            val cents = centsOf(match.groupValues[1]) ?: continue
            add(Amount(cents, isAfterPlus(before + " " + line.substring(0, match.range.first))))
        }
        val next = lines.getOrNull(i + 1) ?: continue
        val split = when {
            BARE_AMOUNT.matches(line) && CURRENCY_ONLY.matches(next) -> line
            CURRENCY_ONLY.matches(line) && BARE_AMOUNT.matches(next) -> next
            else -> continue
        }
        centsOf(split)?.let { add(Amount(it, isAfterPlus(before))) }
    }
}

/** True when the text before an amount ends with "+", ignoring spaces: "+LKR 50", "+ LKR 200". */
private fun isAfterPlus(textBefore: String): Boolean = textBefore.trimEnd().endsWith('+')

private fun centsOf(amount: String): Long? {
    val cents = amount.replace(",", "").toBigDecimalOrNull()
        ?.movePointRight(2)?.setScale(0, RoundingMode.HALF_UP) ?: return null
    if (cents.signum() <= 0 || cents > BigDecimal.valueOf(MAX_FARE_CENTS)) return null
    return cents.longValueExact()
}

/**
 * The profit of [reading] with the same calculator and rates as Check a Ride: [vehicle]'s rates
 * and target, and the fee settings the rider last used ([last]), so a payout-mode rider gets
 * payout maths. The offer's tip is added after the commission. No return distance or other costs
 * are added; the rider can add them on Check a Ride. Null only if the stored rates can't be calculated.
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
        tipCents = reading.tipCents,
    )
    return (calculate(input) as? CalculationResult.Success)?.result
}
