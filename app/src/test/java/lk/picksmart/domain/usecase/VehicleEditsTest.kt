package lk.picksmart.domain.usecase

import lk.picksmart.domain.calculator.CalculationResult
import lk.picksmart.domain.calculator.ProfitCalculator
import lk.picksmart.domain.calculator.ProfitInput
import lk.picksmart.domain.calculator.ResultBand
import lk.picksmart.domain.model.CommissionType
import lk.picksmart.domain.model.FareMode
import lk.picksmart.domain.model.FuelPriceSource
import lk.picksmart.domain.model.FuelType
import lk.picksmart.domain.model.Vehicle
import lk.picksmart.domain.model.VehicleInput
import lk.picksmart.domain.model.VehicleType
import lk.picksmart.domain.model.rates
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
    fun `editing sets and clears the number plate`() {
        val withPlate = applyVehicleEdit(published, input.copy(plateNumber = "WP CAB-1234"), nowMillis = 2_000)
        assertEquals("WP CAB-1234", withPlate.plateNumber)
        assertNull(applyVehicleEdit(withPlate, input, nowMillis = 3_000).plateNumber)
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

    @Test
    fun `accepting a measured km per litre changes only efficiency`() {
        val sample = published.copy(isSample = true)
        val updated = applyEfficiencyUpdate(sample, 36.3, nowMillis = 9_000)
        assertEquals(sample.copy(fuelEfficiencyKmPerLitre = 36.3, updatedAtMillis = 9_000), updated)
        // The published price and its auto-update setting are untouched.
        assertEquals(FuelPriceSource.PUBLISHED, updated.fuelPriceSource)
        assertTrue(updated.autoUpdateFuelPrice)
    }

    @Test
    fun `setting a target per km changes only the target`() {
        val sample = published.copy(isSample = true)
        val updated = applyTargetUpdate(sample, 2_000, nowMillis = 9_000)
        assertEquals(sample.copy(targetProfitPerKmCents = 2_000, updatedAtMillis = 9_000), updated)
        assertNull(applyTargetUpdate(updated, null, nowMillis = 9_500).targetProfitPerKmCents)
    }

    @Test
    fun `a new target changes the band of new checks, not of saved trips`() {
        val calculator = ProfitCalculator()
        // The ride from the screenshot: Rs. 60 gross, 20% fee, 2 + 3 km, Rs. 300/L, 40 km/L, Rs. 2/km.
        val vehicle = published.copy(fuelPricePerLitreCents = 30_000)
        fun check(v: Vehicle) = ProfitInput(
            fareCents = 6_000,
            fareMode = FareMode.GROSS_BEFORE_COMMISSION,
            commissionType = CommissionType.PERCENT,
            commissionHundredths = 2_000,
            pickupKm = 2.0,
            tripKm = 3.0,
            returnKm = 0.0,
            fuelEfficiencyKmPerLitre = v.fuelEfficiencyKmPerLitre,
            fuelPricePerLitreCents = v.fuelPricePerLitreCents,
            maintenanceReservePerKmCents = v.maintenanceReservePerKmCents,
            otherCostsCents = 0,
            targetProfitPerKmCents = v.targetProfitPerKmCents,
        )
        val before = (calculator.calculate(check(vehicle)) as CalculationResult.Success).result
        assertEquals(50L, before.netProfitCents)
        assertEquals(ResultBand.PROFIT, before.band)

        val withTarget = applyTargetUpdate(vehicle, 2_000, nowMillis = 9_000)
        val after = (calculator.calculate(check(withTarget)) as CalculationResult.Success).result
        assertEquals(ResultBand.BELOW_TARGET, after.band)
        // A trip saved earlier keeps its own snapshot, without the new target.
        assertNull(vehicle.rates().targetProfitPerKmCents)
    }
}
