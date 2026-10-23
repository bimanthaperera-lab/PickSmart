package lk.picksmart.ui.fuelprice

import lk.picksmart.domain.model.FuelType
import lk.picksmart.ui.components.FieldError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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

    @Test
    fun `the price is required`() {
        assertEquals(FieldError.REQUIRED, form.copy(price = "").priceError())
        assertEquals(FieldError.INVALID, form.copy(price = ".").priceError())
        assertNull(form.copy(price = "0").priceError())
        assertEquals(41_450L, form.copy(price = "414.5").priceCents())
    }
}
