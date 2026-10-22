package lk.picksmart.domain.usecase

import lk.picksmart.domain.model.FuelPriceSource
import lk.picksmart.domain.model.FuelType
import lk.picksmart.domain.model.VehicleInput
import lk.picksmart.domain.model.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class VehicleEditsTest {

    private val input = VehicleInput(
        name = "Honda Dio",
        type = VehicleType.MOTORCYCLE,
        fuelType = FuelType.PETROL_92,
        fuelPricePerLitreCents = 30_000,
        fuelEfficiencyKmPerLitre = 40.0,
        maintenanceReservePerKmCents = 200,
        targetProfitPerKmCents = null,
    )

    /** A vehicle whose price came from the published list, with auto-update on. */
    private val published = newVehicle(input, isSample = false, nowMillis = 1_000).copy(
        id = 7,
        fuelPricePerLitreCents = 41_400,
        fuelPriceSource = FuelPriceSource.PUBLISHED,
        fuelPriceEffectiveDate = LocalDate.of(2026, 10, 1),
        autoUpdateFuelPrice = true,
    )

    @Test
    fun `a new vehicle has a manual price and no auto-update`() {
        val v = newVehicle(input, isSample = true, nowMillis = 5)
        assertEquals(FuelPriceSource.MANUAL, v.fuelPriceSource)
        assertFalse(v.autoUpdateFuelPrice)
        assertTrue(v.isSample)
        assertEquals(5L, v.createdAtMillis)
        assertEquals(5L, v.updatedAtMillis)
    }

    @Test
    fun `editing the fuel price by hand makes it manual and turns auto-update off`() {
        val edited = applyVehicleEdit(published, input.copy(fuelPricePerLitreCents = 39_900), nowMillis = 2_000)
        assertEquals(39_900L, edited.fuelPricePerLitreCents)
        assertEquals(FuelPriceSource.MANUAL, edited.fuelPriceSource)
        assertFalse(edited.autoUpdateFuelPrice)
        assertNull(edited.fuelPriceEffectiveDate)
    }

    @Test
    fun `changing fuel type also counts as a manual price`() {
        val edited = applyVehicleEdit(
            published,
            input.copy(fuelType = FuelType.PETROL_95, fuelPricePerLitreCents = 41_400),
            nowMillis = 2_000,
        )
        assertEquals(FuelPriceSource.MANUAL, edited.fuelPriceSource)
        assertFalse(edited.autoUpdateFuelPrice)
    }

    @Test
    fun `editing other fields keeps a published price and its auto-update`() {
        val edited = applyVehicleEdit(
            published,
            input.copy(fuelPricePerLitreCents = 41_400, fuelEfficiencyKmPerLitre = 38.0),
            nowMillis = 2_000,
        )
        assertEquals(FuelPriceSource.PUBLISHED, edited.fuelPriceSource)
        assertTrue(edited.autoUpdateFuelPrice)
        assertEquals(LocalDate.of(2026, 10, 1), edited.fuelPriceEffectiveDate)
        assertEquals(38.0, edited.fuelEfficiencyKmPerLitre, 0.0)
    }

    @Test
    fun `saving the form clears the sample flag and keeps identity`() {
        val sample = newVehicle(sampleVehicleInput("Sample"), isSample = true, nowMillis = 1_000).copy(id = 3)
        val edited = applyVehicleEdit(sample, sampleVehicleInput("Sample"), nowMillis = 9_000)
        assertFalse(edited.isSample)
        assertEquals(3L, edited.id)
        assertEquals(1_000L, edited.createdAtMillis)
        assertEquals(9_000L, edited.updatedAtMillis)
    }
}
