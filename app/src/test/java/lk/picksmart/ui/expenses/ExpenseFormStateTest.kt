package lk.picksmart.ui.expenses

import lk.picksmart.domain.model.ExpenseCategory
import lk.picksmart.ui.components.FieldError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ExpenseFormStateTest {

    private val form = ExpenseFormState(date = LocalDate.of(2026, 10, 22))

    @Test
    fun amountIsRequiredAndAboveZero() {
        assertEquals(ExpenseFormResult.Invalid(FieldError.REQUIRED, null), form.validate())
        assertEquals(ExpenseFormResult.Invalid(FieldError.ZERO, null), form.copy(amount = "0").validate())
        assertEquals(ExpenseFormResult.Invalid(FieldError.INVALID, null), form.copy(amount = ".").validate())
    }

    @Test
    fun maintenanceKeepsTheOdometer() {
        val input = (form.copy(amount = "4500", odometer = "45000").validate() as ExpenseFormResult.Valid).input
        assertEquals(450_000L, input.amountCents)
        assertEquals(45_000.0, input.odometerKm!!, 0.0)
    }

    @Test
    fun aHiddenOdometerIsNeitherCheckedNorKept() {
        val parking = form.copy(category = ExpenseCategory.PARKING, amount = "100", odometer = ".")
        assertNull((parking.validate() as ExpenseFormResult.Valid).input.odometerKm)
        assertEquals(
            ExpenseFormResult.Invalid(null, FieldError.INVALID),
            parking.copy(category = ExpenseCategory.MAINTENANCE_REPAIR).validate(),
        )
    }
}
