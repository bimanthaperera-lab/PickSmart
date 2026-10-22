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
    )

    private fun valid(state: VehicleFormState): VehicleInput =
        (state.validate("Motorcycle") as VehicleFormResult.Valid).input

    private fun errors(state: VehicleFormState): Map<VehicleField, FieldError> =
        (state.validate("Motorcycle") as VehicleFormResult.Invalid).errors

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
            ),
            valid(filled),
        )
    }

    @Test
    fun `optional fields can be left empty`() {
        val input = valid(filled.copy(name = "  ", maintenance = "", target = ""))
        assertEquals("Motorcycle", input.name)
        assertEquals(0L, input.maintenanceReservePerKmCents)
        assertEquals(null, input.targetProfitPerKmCents)
    }

    @Test
    fun `fuel price and efficiency are required`() {
        assertEquals(
            mapOf(VehicleField.FUEL_PRICE to FieldError.REQUIRED, VehicleField.EFFICIENCY to FieldError.REQUIRED),
            errors(filled.copy(fuelPrice = "", efficiency = "")),
        )
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
}
