package lk.picksmart.domain.calculator

import lk.picksmart.domain.model.CommissionType
import lk.picksmart.domain.model.FareMode
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The lowest fare that still meets the rider's target (CLAUDE.md section 9a, the "magic number"):
 * [perKmCents] for every km driven, plus [fixedFeeCents] once per trip when the commission is a
 * fixed amount. Without a target it is the break-even fare.
 */
data class MinimumFare(val perKmCents: Long, val fixedFeeCents: Long)

/**
 * ```
 * perKm = (targetProfitPerKm + fuelPrice ÷ efficiency + maintenanceReservePerKm) ÷ (1 − percent ÷ 100)
 * ```
 * A percentage commission divides, a fixed one is added after as [MinimumFare.fixedFeeCents], and
 * payout mode has none. Arithmetic is in [BigDecimal] like [ProfitCalculator]: the fuel cost per
 * km isn't rounded on its own, and the result is rounded HALF_UP to whole cents.
 *
 * Returns null when the inputs can't give a sensible answer (a negative amount, an efficiency that
 * isn't a finite number above 0, a commission of 100% or more, or a result too large for a Long).
 */
fun minimumFarePerKm(
    fuelPricePerLitreCents: Long,
    fuelEfficiencyKmPerLitre: Double,
    maintenanceReservePerKmCents: Long,
    targetProfitPerKmCents: Long?,
    fareMode: FareMode,
    commissionType: CommissionType,
    commissionHundredths: Long?,
): MinimumFare? {
    if (fuelPricePerLitreCents < 0 || maintenanceReservePerKmCents < 0) return null
    if (targetProfitPerKmCents != null && targetProfitPerKmCents < 0) return null
    if (!fuelEfficiencyKmPerLitre.isFinite() || fuelEfficiencyKmPerLitre <= 0) return null
    val commission = if (fareMode == FareMode.GROSS_BEFORE_COMMISSION) commissionHundredths ?: 0L else 0L
    if (commission < 0) return null

    val fuelPerKm = fuelPricePerLitreCents.toBigDecimal()
        .divide(BigDecimal.valueOf(fuelEfficiencyKmPerLitre), SCALE, RoundingMode.HALF_UP)
    val costsPerKm = (targetProfitPerKmCents ?: 0L).toBigDecimal() + fuelPerKm + maintenanceReservePerKmCents.toBigDecimal()
    val perKm = when (commissionType) {
        CommissionType.AMOUNT -> costsPerKm
        CommissionType.PERCENT -> {
            val kept = HUNDRED_PERCENT - commission.toBigDecimal()
            if (kept.signum() <= 0) return null
            (costsPerKm * HUNDRED_PERCENT).divide(kept, SCALE, RoundingMode.HALF_UP)
        }
    }.setScale(0, RoundingMode.HALF_UP)
    if (perKm > BigDecimal.valueOf(Long.MAX_VALUE)) return null
    val fixedFee = if (commissionType == CommissionType.AMOUNT) commission else 0L
    return MinimumFare(perKmCents = perKm.longValueExact(), fixedFeeCents = fixedFee)
}

private const val SCALE = 10
private val HUNDRED_PERCENT: BigDecimal = BigDecimal.valueOf(10_000L)
