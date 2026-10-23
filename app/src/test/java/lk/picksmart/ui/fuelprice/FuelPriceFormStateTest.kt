package lk.picksmart.ui.fuelprice

import lk.picksmart.domain.model.FuelPriceList
import lk.picksmart.domain.model.FuelType
import lk.picksmart.ui.components.FieldError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FuelPriceFormStateTest {

    private val form = FuelPriceFormState(baseType = FuelType.PETROL_92, basePriceCents = 30_000)

    @Test
    fun `starts from the vehicle with nothing to save`() {
        assertEquals("300.00", form.price)
        assertFalse(form.isChanged)
        // Retyping the same amount differently isn't a change.
        assertFalse(form.copy(price = "300").isChanged)
    }

    @Test
    fun `a new price or fuel type is a change`() {
        assertTrue(form.copy(price = "414").isChanged)
        assertTrue(form.copy(fuelType = FuelType.AUTO_DIESEL).isChanged)
    }

    private val list = FuelPriceList(
        effectiveFrom = LocalDate.of(2026, 10, 1),
        fetchedAtMillis = 0,
        pricesCentsPerLitre = mapOf(
            FuelType.PETROL_92 to 41_400,
            FuelType.PETROL_95 to 47_500,
            FuelType.AUTO_DIESEL to 39_200,
            FuelType.SUPER_DIESEL to 52_800,
        ),
    )

    @Test
    fun `switching fuel type fills in that type's published price`() {
        val diesel = form.withFuelType(FuelType.AUTO_DIESEL, list)
        assertEquals("392.00", diesel.price)
        assertTrue(diesel.isPublishedPrice(list))
        assertEquals("475.00", diesel.withFuelType(FuelType.PETROL_95, list).price)
    }

    @Test
    fun `switching back to the vehicle's own type restores its own price`() {
        val back = form.withFuelType(FuelType.SUPER_DIESEL, list).withFuelType(FuelType.PETROL_92, list)
        assertEquals("300.00", back.price)
        assertFalse(back.isChanged)
        assertFalse(back.isPublishedPrice(list))
    }

    @Test
    fun `without a published list the typed price stays`() {
        val typed = form.copy(price = "310")
        val diesel = typed.withFuelType(FuelType.AUTO_DIESEL, null)
        assertEquals("310", diesel.price)
        assertFalse(diesel.isPublishedPrice(null))
    }

    @Test
    fun `the price is required`() {
        assertEquals(FieldError.REQUIRED, form.copy(price = "").priceError())
        assertEquals(FieldError.INVALID, form.copy(price = ".").priceError())
        assertNull(form.copy(price = "0").priceError())
        assertEquals(41_450L, form.copy(price = "414.5").priceCents())
    }
}
