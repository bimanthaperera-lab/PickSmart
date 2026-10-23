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

    /** The vehicle's Rs. 300 for Petrol 92, then the published list for the other types. */
    private val fromList = mapOf(FuelType.PETROL_92 to 30_000L) + (FuelType.entries - FuelType.PETROL_92).associateWith { list.priceFor(it)!! }

    @Test
    fun `switching fuel type fills in that type's price`() {
        val diesel = form.withFuelType(FuelType.AUTO_DIESEL, fromList)
        assertEquals("392.00", diesel.price)
        assertTrue(diesel.isPublishedPrice(list))
        assertEquals("475.00", diesel.withFuelType(FuelType.PETROL_95, fromList).price)
    }

    @Test
    fun `switching back to the vehicle's own type restores its own price`() {
        val back = form.withFuelType(FuelType.SUPER_DIESEL, fromList).withFuelType(FuelType.PETROL_92, fromList)
        assertEquals("300.00", back.price)
        assertFalse(back.isChanged)
        assertFalse(back.isPublishedPrice(list))
    }

    @Test
    fun `with no price known for the new type, the old type's price is cleared`() {
        // The reported bug: Petrol 92's price stayed under Petrol 95 when no list was saved.
        val only92 = mapOf(FuelType.PETROL_92 to 30_000L)
        val p95 = form.withFuelType(FuelType.PETROL_95, only92)
        assertEquals("", p95.price)
        assertEquals(FieldError.REQUIRED, p95.priceError())
        // Typing a price for 95, then going back, brings 92's price back.
        assertEquals("300.00", p95.copy(price = "470").withFuelType(FuelType.PETROL_92, only92).price)
    }

    @Test
    fun `a price the rider typed for the new type stays`() {
        val typed = form.copy(price = "310")
        val diesel = typed.withFuelType(FuelType.AUTO_DIESEL, mapOf(FuelType.PETROL_92 to 30_000L))
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
