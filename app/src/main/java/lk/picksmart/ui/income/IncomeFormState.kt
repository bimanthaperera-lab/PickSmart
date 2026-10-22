package lk.picksmart.ui.income

import lk.picksmart.domain.model.IncomeType
import lk.picksmart.domain.model.OtherIncome
import lk.picksmart.domain.model.OtherIncomeInput
import lk.picksmart.domain.model.Platform
import lk.picksmart.util.hundredthsToInput
import lk.picksmart.util.parseRupeesToCents
import java.time.LocalDate
import java.time.YearMonth

/** What the rider has typed on Add / Edit income. The amount stays text until [validate]. */
data class IncomeFormState(
    val type: IncomeType = IncomeType.INCENTIVE,
    val platform: Platform? = null,
    val date: LocalDate,
    val amount: String = "",
    val note: String = "",
)

enum class AmountError { REQUIRED, INVALID, ZERO }

sealed interface IncomeFormResult {
    data class Valid(val input: OtherIncomeInput) : IncomeFormResult
    data class Invalid(val amountError: AmountError) : IncomeFormResult
}

/** The amount is required and must be more than 0; everything else is optional or preset. */
fun IncomeFormState.validate(): IncomeFormResult {
    val cents = when {
        amount.isBlank() -> return IncomeFormResult.Invalid(AmountError.REQUIRED)
        else -> parseRupeesToCents(amount) ?: return IncomeFormResult.Invalid(AmountError.INVALID)
    }
    if (cents == 0L) return IncomeFormResult.Invalid(AmountError.ZERO)
    return IncomeFormResult.Valid(OtherIncomeInput(date, type, platform, cents, note))
}

fun OtherIncome.toFormState() = IncomeFormState(
    type = type,
    platform = platform,
    date = date,
    amount = hundredthsToInput(amountCents),
    note = note.orEmpty(),
)

/** Total of the entries dated in [month]. */
fun List<OtherIncome>.totalInMonth(month: YearMonth): Long =
    filter { YearMonth.from(it.date) == month }.sumOf { it.amountCents }
