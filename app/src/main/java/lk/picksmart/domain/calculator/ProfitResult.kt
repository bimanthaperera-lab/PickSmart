package lk.picksmart.domain.calculator

/**
 * The breakdown for one ride. Every money value is in cents and already rounded, and
 * [netProfitCents] is exactly `fare − commission − fuel − reserve − other` of the values shown
 * here, so the lines on screen always add up.
 *
 * @property netProfitPerKmCents Null when [totalKm] is 0.
 */
data class ProfitResult(
    val totalKm: Double,
    val fuelCostCents: Long,
    val maintenanceReserveCents: Long,
    val commissionCents: Long,
    val netProfitCents: Long,
    val netProfitPerKmCents: Long?,
    val band: ResultBand,
)

/** How good a ride is. The UI pairs each band with a text label and an icon, never colour alone. */
enum class ResultBand {
    /** Net profit is below zero. */
    LOSS,

    /** No target set and net profit is exactly zero. */
    BREAK_EVEN,

    /** No target set (or no distance to divide by) and net profit is above zero. */
    PROFIT,

    /** A target is set and profit per km is below it. */
    BELOW_TARGET,

    /** A target is set and profit per km meets or beats it. */
    GOOD,
}
