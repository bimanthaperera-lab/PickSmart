package lk.picksmart.domain.model

/** What the entered fare means. */
enum class FareMode {
    /** The fare before the platform takes its commission. */
    GROSS_BEFORE_COMMISSION,

    /** The amount the rider receives; commission is already removed and is not asked for. */
    PAYOUT_AFTER_COMMISSION,
}
