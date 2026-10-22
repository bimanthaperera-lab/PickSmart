package lk.picksmart.domain.usecase

import lk.picksmart.domain.model.ExpenseCategory
import lk.picksmart.domain.model.ExpenseInput
import lk.picksmart.domain.model.FuelLogInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CostRecordsTest {

    private val date = LocalDate.of(2026, 10, 20)

    @Test
    fun expenseKeepsTheOdometerOnlyForMaintenance() {
        val repair = ExpenseInput(date, ExpenseCategory.MAINTENANCE_REPAIR, 450_000, null, 45_000.0)
        assertEquals(45_000.0, newExpense(repair, 1, 10).odometerKm)
        assertNull(newExpense(repair.copy(category = ExpenseCategory.PARKING), 1, 10).odometerKm)
    }

    @Test
    fun blankExpenseNoteIsStoredAsNull() {
        val input = ExpenseInput(date, ExpenseCategory.TOLLS, 30_000, "   ", null)
        assertNull(newExpense(input, null, 10).note)
        assertEquals("Highway", newExpense(input.copy(note = " Highway "), null, 10).note)
    }

    @Test
    fun editsKeepIdVehicleAndCreationTime() {
        val expense = newExpense(ExpenseInput(date, ExpenseCategory.PARKING, 10_000, null, null), 3, 10).copy(id = 7)
        val edited = applyExpenseEdit(expense, ExpenseInput(date, ExpenseCategory.OTHER, 20_000, "x", null), 99)
        assertEquals(7L, edited.id)
        assertEquals(3L, edited.vehicleId)
        assertEquals(10L, edited.createdAtMillis)
        assertEquals(99L, edited.updatedAtMillis)
        assertEquals(20_000L, edited.amountCents)

        val fill = newFuelLog(FuelLogInput(date, 8.0, 41_400, 331_200, null, false), 3, 10).copy(id = 5)
        val editedFill = applyFuelLogEdit(fill, FuelLogInput(date, 9.0, 41_400, 372_600, 1_200.0, true), 99)
        assertEquals(5L, editedFill.id)
        assertEquals(3L, editedFill.vehicleId)
        assertEquals(10L, editedFill.createdAtMillis)
        assertEquals(99L, editedFill.updatedAtMillis)
        assertEquals(9.0, editedFill.litres, 0.0)
    }
}
