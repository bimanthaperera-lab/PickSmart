package lk.picksmart.domain.model

import java.time.LocalDate

enum class IncomeType { INCENTIVE, TIP, BONUS, OTHER }

/**
 * Money not tied to a single trip, such as a quest bonus or a tip. Cancellation fees are not
 * entered here: they belong to the cancelled trip, so they are never counted twice (CLAUDE.md N2).
 */
data class OtherIncome(
    val id: Long,
    val date: LocalDate,
    val type: IncomeType,
    val platform: Platform?,
    val amountCents: Long,
    val note: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

/** The values a rider sets on the income form, already parsed and validated. */
data class OtherIncomeInput(
    val date: LocalDate,
    val type: IncomeType,
    val platform: Platform?,
    val amountCents: Long,
    val note: String?,
)
