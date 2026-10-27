package lk.picksmart.domain.calculator

import lk.picksmart.domain.model.CommissionType
import lk.picksmart.domain.model.FareMode

/**
 * Everything [ProfitCalculator] needs for one ride. Money is in cents; distances and efficiency
 * are plain numbers (CLAUDE.md section 6).
 *
 * @property commissionHundredths The commission in hundredths of its unit, so it stays a whole
 *   number for both types: cents when [commissionType] is [CommissionType.AMOUNT] (Rs. 200 =
 *   20_000), hundredths of a percent when it is [CommissionType.PERCENT] (20% = 2_000, 12.5% =
 *   1_250). Ignored in [FareMode.PAYOUT_AFTER_COMMISSION].
 * @property returnKm Optional unpaid or empty return distance; 0 when not used.
 * @property targetProfitPerKmCents The rider's optional target, or null when none is set.
 * @property tipCents A tip shown on the offer, paid on top of the fare. Added to the profit after
 *   the commission, so no commission is taken from it. 0 when there is none.
 */
data class ProfitInput(
    val fareCents: Long,
    val fareMode: FareMode,
    val commissionType: CommissionType,
    val commissionHundredths: Long,
    val pickupKm: Double,
    val tripKm: Double,
    val returnKm: Double = 0.0,
    val fuelEfficiencyKmPerLitre: Double,
    val fuelPricePerLitreCents: Long,
    val maintenanceReservePerKmCents: Long,
    val otherCostsCents: Long = 0,
    val targetProfitPerKmCents: Long? = null,
    val tipCents: Long = 0,
)
