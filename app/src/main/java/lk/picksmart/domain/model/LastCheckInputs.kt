package lk.picksmart.domain.model

/**
 * The Check a Ride values remembered between checks, so a typical check only needs the fare and
 * distances. The fare itself is never remembered: a stale fare would show a result that looks
 * like an answer for the new offer. Per-check rate overrides are not remembered either, so the
 * vehicle's rates always apply to a new session.
 *
 * Null means the field was left empty.
 *
 * @property commissionHundredths In hundredths of the [commissionType]'s unit, as in
 *   `ProfitInput.commissionHundredths`.
 */
data class LastCheckInputs(
    val fareMode: FareMode = FareMode.GROSS_BEFORE_COMMISSION,
    val commissionType: CommissionType = CommissionType.PERCENT,
    val commissionHundredths: Long? = null,
    val otherCostsCents: Long? = null,
    val pickupKm: Double? = null,
    val tripKm: Double? = null,
    val returnKm: Double? = null,
)
