package lk.picksmart.ui.fuel

import lk.picksmart.domain.model.FuelLog
import lk.picksmart.ui.components.FieldError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class FuelFormStateTest {

    private val today = LocalDate.of(2026, 10, 22)
    private val form = newFuelForm(today, vehiclePriceCents = 41_400)

    @Test
    fun aNewFillUpStartsFromTheVehiclePrice() {
        assertEquals("414.00", form.price)
        assertEquals("", form.total)
        assertEquals(false, form.fullTank)
    }

    @Test
    fun typingTheAmountPaidFillsInTheLitres() {
        val f = form.withTotal("2000")
        assertEquals("4.83", f.litres) // 2000 ÷ 414 = 4.8309…
        assertEquals(FuelAmount.TOTAL, f.typed)
    }

    @Test
    fun typingTheLitresFillsInTheAmountPaid() {
        val f = form.withLitres("8.05")
        assertEquals("3332.7", f.total) // 8.05 × 414 = 3332.70
        assertEquals(FuelAmount.LITRES, f.typed)
    }

    @Test
    fun changingThePriceRecalculatesTheAmountNotTyped() {
        assertEquals("5", form.withTotal("2000").withPrice("400").litres)
        assertEquals("3220", form.withLitres("8.05").withPrice("400").total)
    }

    @Test
    fun theDerivedAmountClearsWhenItCantBeWorkedOut() {
        assertEquals("", form.withTotal("2000").withPrice("").litres)
        assertEquals("", form.withTotal("2000").withPrice("0").litres)
        assertEquals("", form.withTotal("4.83").withTotal("").litres)
    }

    @Test
    fun aValidFillUpKeepsWhatWasPaid() {
        val result = form.withTotal("2000").copy(odometer = "45210.5", fullTank = true).validate() as FuelFormResult.Valid
        assertEquals(200_000L, result.input.totalCents)
        assertEquals(4.83, result.input.litres, 0.0)
        assertEquals(41_400L, result.input.pricePerLitreCents)
        assertEquals(45_210.5, result.input.odometerKm!!, 0.0)
        assertTrue(result.input.fullTank)
    }

    @Test
    fun emptyFormFlagsTheAmountsButNotTheOptionalOdometer() {
        val errors = (form.validate() as FuelFormResult.Invalid).errors
        assertEquals(mapOf(FuelField.TOTAL to FieldError.REQUIRED, FuelField.LITRES to FieldError.REQUIRED), errors)
    }

    @Test
    fun withoutAPriceOnlyThePriceAndTheTypedAmountAreFlagged() {
        val errors = (form.withPrice("").withTotal("2000").validate() as FuelFormResult.Invalid).errors
        assertEquals(mapOf(FuelField.PRICE to FieldError.REQUIRED), errors)
        val litresTyped = (form.withPrice("").withLitres("").validate() as FuelFormResult.Invalid).errors
        assertEquals(mapOf(FuelField.PRICE to FieldError.REQUIRED, FuelField.LITRES to FieldError.REQUIRED), litresTyped)
    }

    @Test
    fun zeroAmountsAndABadOdometerAreRejected() {
        val errors = (form.withTotal("0").copy(odometer = ".").validate() as FuelFormResult.Invalid).errors
        assertEquals(FieldError.ZERO, errors[FuelField.TOTAL])
        assertEquals(FieldError.ZERO, errors[FuelField.LITRES])
        assertEquals(FieldError.INVALID, errors[FuelField.ODOMETER])
    }

    @Test
    fun hugeValuesDontOverflow() {
        assertNull(totalFor(1e30, 41_400))
        assertEquals("", form.withLitres("99999999999999999999").total)
    }

    @Test
    fun aSavedFillUpShowsItsStoredValues() {
        val log = FuelLog(3, 1, today, 4.83, 41_400, 200_000, null, true, 0, 0)
        val f = log.toFormState()
        assertEquals("2000", f.total)
        assertEquals("4.83", f.litres)
        assertEquals("414.00", f.price)
        assertEquals("", f.odometer)
        assertTrue(f.fullTank)
    }

    @Test
    fun monthTotalCountsOnlyThatMonth() {
        val log = FuelLog(1, 1, today, 4.83, 41_400, 200_000, null, false, 0, 0)
        val logs = listOf(log, log.copy(id = 2, totalCents = 50_000), log.copy(id = 3, date = LocalDate.of(2026, 9, 30)))
        assertEquals(250_000L, logs.totalInMonth(YearMonth.of(2026, 10)))
    }
}
