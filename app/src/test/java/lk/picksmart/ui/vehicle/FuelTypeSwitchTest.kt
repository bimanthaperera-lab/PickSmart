package lk.picksmart.ui.vehicle

import lk.picksmart.domain.model.FuelType
import org.junit.Assert.assertEquals
import org.junit.Test

class FuelTypeSwitchTest {

    private val known = mapOf(FuelType.PETROL_92 to 41_400L, FuelType.AUTO_DIESEL to 39_200L)

    @Test
    fun `fills in the new type's price`() {
        assertEquals("392.00", priceAfterFuelTypeSwitch("414.00", FuelType.PETROL_92, FuelType.AUTO_DIESEL, known))
        // Even over something typed: the rider chose a type with a known price.
        assertEquals("414.00", priceAfterFuelTypeSwitch("500", FuelType.PETROL_95, FuelType.PETROL_92, known))
    }

    @Test
    fun `clears the old type's price when the new type has none`() {
        assertEquals("", priceAfterFuelTypeSwitch("414.00", FuelType.PETROL_92, FuelType.PETROL_95, known))
        // Typed differently but the same amount still counts as the old type's price.
        assertEquals("", priceAfterFuelTypeSwitch("414", FuelType.PETROL_92, FuelType.PETROL_95, known))
    }

    @Test
    fun `keeps what the rider typed when the new type has none`() {
        assertEquals("470", priceAfterFuelTypeSwitch("470", FuelType.PETROL_92, FuelType.PETROL_95, known))
        assertEquals("470", priceAfterFuelTypeSwitch("470", FuelType.PETROL_95, FuelType.SUPER_DIESEL, emptyMap()))
        assertEquals("", priceAfterFuelTypeSwitch("", FuelType.PETROL_92, FuelType.PETROL_95, known))
    }

    @Test
    fun `the vehicle form uses the same rule`() {
        val form = VehicleFormState(fuelType = FuelType.PETROL_92, fuelPrice = "414.00")
        assertEquals("", form.withFuelType(FuelType.PETROL_95, known).fuelPrice)
        assertEquals(FuelType.PETROL_95, form.withFuelType(FuelType.PETROL_95, known).fuelType)
        assertEquals("392.00", form.withFuelType(FuelType.AUTO_DIESEL, known).fuelPrice)
    }
}
