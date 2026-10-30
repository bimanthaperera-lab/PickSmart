package lk.picksmart.ui.vehicle

import lk.picksmart.domain.model.FuelType
import lk.picksmart.domain.model.VehicleInput
import lk.picksmart.domain.model.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleFormStateTest {

    private val filled = VehicleFormState(
        name = " Honda Dio ",
        type = VehicleType.MOTORCYCLE,
        fuelType = FuelType.PETROL_92,
        fuelPrice = "300",
        efficiency = "40",
        maintenance = "2",
        target = "60",
        plateNumber = "WP CAB-1234",
    )

    private fun valid(state: VehicleFormState): VehicleInput =
        (state.validate() as VehicleFormResult.Valid).input

    private fun errors(state: VehicleFormState): Map<VehicleField, FieldError> =
        (state.validate() as VehicleFormResult.Invalid).errors

    @Test
    fun `a filled form becomes a vehicle input in cents`() {
        assertEquals(
            VehicleInput(
                name = "Honda Dio",
                type = VehicleType.MOTORCYCLE,
                fuelType = FuelType.PETROL_92,
                fuelPricePerLitreCents = 30_000,
                fuelEfficiencyKmPerLitre = 40.0,
                maintenanceReservePerKmCents = 200,
                targetProfitPerKmCents = 6_000,
                plateNumber = "WP CAB-1234",
            ),
            valid(filled),
        )
    }

    @Test
    fun `maintenance can be left empty`() {
        assertEquals(0L, valid(filled.copy(maintenance = "")).maintenanceReservePerKmCents)
    }

    @Test
    fun `name, plate, fuel price, efficiency and target are required`() {
        assertEquals(
            mapOf(
                VehicleField.NAME to FieldError.REQUIRED,
                VehicleField.PLATE to FieldError.REQUIRED,
                VehicleField.FUEL_PRICE to FieldError.REQUIRED,
                VehicleField.EFFICIENCY to FieldError.REQUIRED,
                VehicleField.TARGET to FieldError.REQUIRED,
            ),
            errors(filled.copy(name = "  ", plateNumber = "   ", fuelPrice = "", efficiency = "", target = "")),
        )
    }

    @Test
    fun `a zero target is rejected`() {
        assertEquals(mapOf(VehicleField.TARGET to FieldError.MUST_BE_ABOVE_ZERO), errors(filled.copy(target = "0")))
        assertEquals(mapOf(VehicleField.TARGET to FieldError.MUST_BE_ABOVE_ZERO), errors(filled.copy(target = "0.00")))
    }

    @Test
    fun `zero efficiency is rejected`() {
        assertEquals(mapOf(VehicleField.EFFICIENCY to FieldError.MUST_BE_ABOVE_ZERO), errors(filled.copy(efficiency = "0")))
        assertEquals(mapOf(VehicleField.EFFICIENCY to FieldError.MUST_BE_ABOVE_ZERO), errors(filled.copy(efficiency = "0.0")))
    }

    @Test
    fun `unparseable numbers are reported per field`() {
        assertEquals(
            mapOf(
                VehicleField.FUEL_PRICE to FieldError.INVALID,
                VehicleField.EFFICIENCY to FieldError.INVALID,
                VehicleField.MAINTENANCE to FieldError.INVALID,
                VehicleField.TARGET to FieldError.INVALID,
            ),
            errors(filled.copy(fuelPrice = ".", efficiency = ".", maintenance = ".", target = ".")),
        )
    }

    @Test
    fun `a saved vehicle round-trips through the form`() {
        val input = valid(filled)
        val vehicle = lk.picksmart.domain.usecase.newVehicle(input, isSample = false, nowMillis = 0)
        assertEquals(input, valid(vehicle.toFormState()))
        assertEquals("300.00", vehicle.toFormState().fuelPrice)
        assertEquals("40", vehicle.toFormState().efficiency)
    }

    @Test
    fun `the number plate is tidied`() {
        assertEquals("WP CAB-1234", valid(filled.copy(plateNumber = " wp  cab-1234 ")).plateNumber)
    }

    @Test
    fun `any plate format is kept, up to the length limit`() {
        assertEquals("253-1234", normalizePlateNumber("253-1234"))
        assertEquals("18 ශ්‍රී 1234", normalizePlateNumber("18  ශ්‍රී 1234"))
        assertEquals("ABCDEFGHIJ 1234", normalizePlateNumber("abcdefghij 12345678"))
        assertEquals("ABCDEFGHIJKLMN", normalizePlateNumber("abcdefghijklmn x"))
    }

    @Test
    fun `a saved plate comes back into the form`() {
        val input = valid(filled.copy(plateNumber = "CAB-1234"))
        val vehicle = lk.picksmart.domain.usecase.newVehicle(input, isSample = false, nowMillis = 1_000)
        assertEquals("CAB-1234", vehicle.toFormState().plateNumber)
    }

    @Test
    fun `the step buttons move the fuel price by whole rupees`() {
        assertEquals("419.00", filled.copy(fuelPrice = "414").stepFuelPrice(5).fuelPrice)
        assertEquals("409.50", filled.copy(fuelPrice = "414.5").stepFuelPrice(-5).fuelPrice)
        assertEquals("5.00", filled.copy(fuelPrice = "").stepFuelPrice(5).fuelPrice)
    }

    @Test
    fun `a step never makes the fuel price negative, too long or replaces bad text`() {
        assertEquals("3", filled.copy(fuelPrice = "3").stepFuelPrice(-5).fuelPrice)
        assertEquals("", filled.copy(fuelPrice = "").stepFuelPrice(-5).fuelPrice)
        assertEquals("9999998", filled.copy(fuelPrice = "9999998").stepFuelPrice(5).fuelPrice)
        assertEquals("12.3.4", filled.copy(fuelPrice = "12.3.4").stepFuelPrice(5).fuelPrice)
    }
}
