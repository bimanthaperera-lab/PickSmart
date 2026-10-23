package lk.picksmart.domain.calculator

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Fuel cost of one kilometre: price per litre ÷ km per litre, rounded HALF_UP to whole cents,
 * with the same rules as [ProfitCalculator]. Rs. 414.00/L at 40 km/L is Rs. 10.35/km.
 *
 * Returns null when the inputs can't give a sensible answer (a negative price, or an efficiency
 * that isn't a finite number above 0), instead of throwing or producing NaN.
 */
fun fuelCostPerKmCents(fuelPricePerLitreCents: Long, fuelEfficiencyKmPerLitre: Double): Long? {
    if (fuelPricePerLitreCents < 0) return null
    if (!fuelEfficiencyKmPerLitre.isFinite() || fuelEfficiencyKmPerLitre <= 0) return null
    return fuelPricePerLitreCents.toBigDecimal()
        .divide(BigDecimal.valueOf(fuelEfficiencyKmPerLitre), 10, RoundingMode.HALF_UP)
        .setScale(0, RoundingMode.HALF_UP)
        .longValueExact()
}
