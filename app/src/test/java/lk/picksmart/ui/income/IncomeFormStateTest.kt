package lk.picksmart.ui.income

import lk.picksmart.domain.model.IncomeType
import lk.picksmart.domain.model.OtherIncome
import lk.picksmart.domain.model.OtherIncomeInput
import lk.picksmart.domain.model.Platform
import lk.picksmart.domain.usecase.newOtherIncome
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class IncomeFormStateTest {

    private val day = LocalDate.of(2026, 10, 20)
    private val form = IncomeFormState(type = IncomeType.BONUS, platform = Platform.PICKME, date = day, amount = "1500", note = "Quest")

    @Test
    fun `a valid entry parses the amount to cents`() {
        assertEquals(
            IncomeFormResult.Valid(OtherIncomeInput(day, IncomeType.BONUS, Platform.PICKME, 150_000, "Quest")),
            form.validate(),
        )
    }

    @Test
    fun `the amount is required and must be above zero`() {
        assertEquals(IncomeFormResult.Invalid(AmountError.REQUIRED), form.copy(amount = " ").validate())
        assertEquals(IncomeFormResult.Invalid(AmountError.ZERO), form.copy(amount = "0.00").validate())
        assertEquals(IncomeFormResult.Invalid(AmountError.INVALID), form.copy(amount = "99999999999999999999").validate())
    }

    @Test
    fun `a saved entry fills the form back`() {
        val saved = newOtherIncome((form.validate() as IncomeFormResult.Valid).input, nowMillis = 1)
        assertEquals(form, saved.toFormState())
    }

    @Test
    fun `month total counts only that calendar month`() {
        fun entry(date: LocalDate, cents: Long) = OtherIncome(0, date, IncomeType.TIP, null, cents, null, 0, 0)
        val entries = listOf(
            entry(LocalDate.of(2026, 9, 30), 10_000),
            entry(LocalDate.of(2026, 10, 1), 30_000),
            entry(LocalDate.of(2026, 10, 31), 145_000),
            entry(LocalDate.of(2026, 11, 1), 50_000),
        )
        assertEquals(175_000L, entries.totalInMonth(YearMonth.of(2026, 10)))
    }
}
