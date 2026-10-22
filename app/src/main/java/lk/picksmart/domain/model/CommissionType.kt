package lk.picksmart.domain.model

/** How the platform commission is expressed. */
enum class CommissionType {
    /** A fixed rupee amount per trip. */
    AMOUNT,

    /** A percentage of the fare. */
    PERCENT,
}
