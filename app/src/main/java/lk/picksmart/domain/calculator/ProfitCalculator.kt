package lk.picksmart.domain.calculator

import lk.picksmart.domain.model.CommissionType
import lk.picksmart.domain.model.FareMode
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Estimates the real profit of one ride (CLAUDE.md section 6). Pure Kotlin with no Android
 * dependencies and no state, so it gives the same answer for the same input everywhere.
 *
 * ## Formulas
 * ```
 * totalKm            = pickupKm + tripKm + returnKm
 * fuelCost           = totalKm × fuelPricePerLitre ÷ fuelEfficiencyKmPerLitre
 * maintenanceReserve = totalKm × maintenanceReservePerKm
 * commission         = 0 in payout mode, the amount for AMOUNT, fare × percent ÷ 100 for PERCENT
 * netProfit          = fare − commission − fuelCost − maintenanceReserve − otherCosts + tip
 * netProfitPerKm     = netProfit ÷ totalKm, or null when totalKm is 0
 * ```
 *
 * ## Rounding policy
 * - All arithmetic is done in [BigDecimal]. Distances and efficiency are converted with
 *   [BigDecimal.valueOf], so 0.1 km is exactly 0.1, not its nearest binary fraction.
 * - Every division is carried to 10 decimal places ([RoundingMode.HALF_UP]) before the final
 *   rounding.
 * - Each money result (fuel cost, reserve, commission, profit per km) is then rounded to whole
 *   cents with [RoundingMode.HALF_UP]. Example: 7 km × Rs. 297.50 ÷ 45 km/L = Rs. 46.2777… →
 *   Rs. 46.28.
 * - Net profit is computed from the already rounded parts, so the breakdown the rider sees
 *   always adds up to the net profit shown.
 * - The result band compares the rounded profit per km with the target, matching what is shown.
 *
 * ## Errors
 * Invalid input returns [CalculationResult.Invalid] listing every problem. It never throws and
 * never produces NaN or Infinity. The commission is only checked in
 * [FareMode.GROSS_BEFORE_COMMISSION], because payout mode hides that field.
 */
class ProfitCalculator {

    fun calculate(input: ProfitInput): CalculationResult {
        val errors = validate(input)
        if (errors.isNotEmpty()) return CalculationResult.Invalid(errors)

        val totalKm = km(input.pickupKm) + km(input.tripKm) + km(input.returnKm)

        val fuelCost = (totalKm * input.fuelPricePerLitreCents.toBigDecimal())
            .divide(km(input.fuelEfficiencyKmPerLitre), DIVISION_SCALE, RoundingMode.HALF_UP)
            .toCents()
        val reserve = (totalKm * input.maintenanceReservePerKmCents.toBigDecimal()).toCents()
        val commission = when {
            input.fareMode == FareMode.PAYOUT_AFTER_COMMISSION -> BigDecimal.ZERO
            input.commissionType == CommissionType.AMOUNT -> input.commissionHundredths.toBigDecimal()
            else -> (input.fareCents.toBigDecimal() * input.commissionHundredths.toBigDecimal())
                .divide(HUNDRED_PERCENT, DIVISION_SCALE, RoundingMode.HALF_UP)
                .toCents()
        }
        // A tip is the rider's in full: it is added after the commission, never charged on.
        val netProfit = input.fareCents.toBigDecimal() - commission - fuelCost - reserve -
            input.otherCostsCents.toBigDecimal() + input.tipCents.toBigDecimal()
        val perKm = if (totalKm.signum() > 0) {
            netProfit.divide(totalKm, DIVISION_SCALE, RoundingMode.HALF_UP).toCents()
        } else {
            null
        }

        val fuelCents = fuelCost.toLongOrNull()
        val reserveCents = reserve.toLongOrNull()
        val commissionCents = commission.toLongOrNull()
        val netCents = netProfit.toLongOrNull()
        val perKmCents = perKm?.toLongOrNull()
        if (fuelCents == null || reserveCents == null || commissionCents == null || netCents == null ||
            (perKm != null && perKmCents == null)
        ) {
            return CalculationResult.Invalid(listOf(ValidationError.ResultTooLarge))
        }

        return CalculationResult.Success(
            ProfitResult(
                totalKm = totalKm.toDouble(),
                fuelCostCents = fuelCents,
                maintenanceReserveCents = reserveCents,
                commissionCents = commissionCents,
                netProfitCents = netCents,
                netProfitPerKmCents = perKmCents,
                band = band(netCents, perKmCents, input.targetProfitPerKmCents),
                tipCents = input.tipCents,
            ),
        )
    }

    private fun validate(input: ProfitInput): List<ValidationError> = buildList {
        fun money(value: Long, field: InputField) {
            if (value < 0) add(ValidationError.Negative(field))
        }

        fun distance(value: Double, field: InputField) {
            when {
                !value.isFinite() -> add(ValidationError.NotANumber(field))
                value < 0 -> add(ValidationError.Negative(field))
            }
        }

        money(input.fareCents, InputField.FARE)
        if (input.fareMode == FareMode.GROSS_BEFORE_COMMISSION) {
            when {
                input.commissionHundredths < 0 -> add(ValidationError.Negative(InputField.COMMISSION))
                input.commissionType == CommissionType.PERCENT &&
                    input.commissionHundredths > MAX_PERCENT_HUNDREDTHS -> add(ValidationError.PercentAbove100)
            }
        }
        distance(input.pickupKm, InputField.PICKUP_KM)
        distance(input.tripKm, InputField.TRIP_KM)
        distance(input.returnKm, InputField.RETURN_KM)
        when {
            !input.fuelEfficiencyKmPerLitre.isFinite() -> add(ValidationError.NotANumber(InputField.FUEL_EFFICIENCY))
            input.fuelEfficiencyKmPerLitre <= 0 -> add(ValidationError.NotPositive(InputField.FUEL_EFFICIENCY))
        }
        money(input.fuelPricePerLitreCents, InputField.FUEL_PRICE)
        money(input.maintenanceReservePerKmCents, InputField.MAINTENANCE_RESERVE)
        money(input.otherCostsCents, InputField.OTHER_COSTS)
        money(input.tipCents, InputField.TIP)
        input.targetProfitPerKmCents?.let { money(it, InputField.TARGET_PROFIT_PER_KM) }
    }

    private fun band(netCents: Long, perKmCents: Long?, targetCents: Long?): ResultBand = when {
        netCents < 0 -> ResultBand.LOSS
        targetCents != null && perKmCents != null ->
            if (perKmCents >= targetCents) ResultBand.GOOD else ResultBand.BELOW_TARGET
        netCents > 0 -> ResultBand.PROFIT
        else -> ResultBand.BREAK_EVEN
    }

    private fun km(value: Double): BigDecimal = BigDecimal.valueOf(value)

    private fun BigDecimal.toCents(): BigDecimal = setScale(0, RoundingMode.HALF_UP)

    private fun BigDecimal.toLongOrNull(): Long? =
        if (this in LONG_MIN..LONG_MAX) longValueExact() else null

    private companion object {
        const val DIVISION_SCALE = 10
        const val MAX_PERCENT_HUNDREDTHS = 10_000L
        val HUNDRED_PERCENT: BigDecimal = BigDecimal.valueOf(MAX_PERCENT_HUNDREDTHS)
        val LONG_MIN: BigDecimal = BigDecimal.valueOf(Long.MIN_VALUE)
        val LONG_MAX: BigDecimal = BigDecimal.valueOf(Long.MAX_VALUE)
    }
}
