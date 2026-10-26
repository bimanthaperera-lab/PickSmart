package lk.picksmart.domain.model

import java.time.LocalDate

/** There is deliberately no fuel category: fuel is recorded only in [FuelLog] (CLAUDE.md section 7). */
enum class ExpenseCategory { MAINTENANCE_REPAIR, PARKING, MOBILE_DATA, TOLLS, OTHER }

/**
 * Money the rider spent on the job, other than fuel. Money is in cents.
 *
 * @property vehicleId The vehicle the cost belongs to, or null when none was set up.
 * @property odometerKm Only kept for maintenance and repairs, where it dates the service.
 */
data class Expense(
    val id: Long,
    val vehicleId: Long?,
    val date: LocalDate,
    val category: ExpenseCategory,
    val amountCents: Long,
    val note: String?,
    val odometerKm: Double?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    /** The same on every device the rider backs up to; see [newSyncId]. Never changes. */
    val syncId: String = newSyncId(),
)

/** The values a rider sets on the expense form, already parsed and validated. */
data class ExpenseInput(
    val date: LocalDate,
    val category: ExpenseCategory,
    val amountCents: Long,
    val note: String?,
    val odometerKm: Double?,
)
