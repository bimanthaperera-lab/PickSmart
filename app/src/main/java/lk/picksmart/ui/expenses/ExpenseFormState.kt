package lk.picksmart.ui.expenses

import lk.picksmart.domain.model.Expense
import lk.picksmart.domain.model.ExpenseCategory
import lk.picksmart.domain.model.ExpenseInput
import lk.picksmart.ui.components.FieldError
import lk.picksmart.ui.components.positiveCentsError
import lk.picksmart.util.decimalToInput
import lk.picksmart.util.hundredthsToInput
import lk.picksmart.util.parseDecimal
import lk.picksmart.util.parseRupeesToCents
import java.time.LocalDate
import java.time.YearMonth

/** What the rider has typed on Add / Edit expense. Numbers stay text until [validate]. */
data class ExpenseFormState(
    val category: ExpenseCategory = ExpenseCategory.MAINTENANCE_REPAIR,
    val date: LocalDate,
    val amount: String = "",
    val note: String = "",
    val odometer: String = "",
)

/** The odometer field only shows for maintenance and repairs. */
val ExpenseFormState.showsOdometer: Boolean get() = category == ExpenseCategory.MAINTENANCE_REPAIR

sealed interface ExpenseFormResult {
    data class Valid(val input: ExpenseInput) : ExpenseFormResult
    data class Invalid(val amountError: FieldError?, val odometerError: FieldError?) : ExpenseFormResult
}

/** The amount is required and must be more than 0. A hidden odometer is never checked or kept. */
fun ExpenseFormState.validate(): ExpenseFormResult {
    val amountError = positiveCentsError(amount)
    val odometerError = FieldError.INVALID.takeIf { showsOdometer && odometer.isNotBlank() && parseDecimal(odometer) == null }
    if (amountError != null || odometerError != null) return ExpenseFormResult.Invalid(amountError, odometerError)
    return ExpenseFormResult.Valid(
        ExpenseInput(
            date = date,
            category = category,
            amountCents = parseRupeesToCents(amount)!!,
            note = note,
            odometerKm = if (showsOdometer) parseDecimal(odometer) else null,
        ),
    )
}

fun Expense.toFormState() = ExpenseFormState(
    category = category,
    date = date,
    amount = hundredthsToInput(amountCents),
    note = note.orEmpty(),
    odometer = odometerKm?.let(::decimalToInput).orEmpty(),
)

/** Total of the expenses dated in [month]. */
fun List<Expense>.totalInMonth(month: YearMonth): Long =
    filter { YearMonth.from(it.date) == month }.sumOf { it.amountCents }
