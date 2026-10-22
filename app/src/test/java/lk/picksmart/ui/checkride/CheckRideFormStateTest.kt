package lk.picksmart.ui.checkride

import lk.picksmart.domain.calculator.InputField
import lk.picksmart.domain.calculator.ProfitCalculator
import lk.picksmart.domain.calculator.ProfitResult
import lk.picksmart.domain.calculator.ResultBand
import lk.picksmart.domain.model.CommissionType
import lk.picksmart.domain.model.FareMode
import lk.picksmart.domain.model.FuelPriceSource
import lk.picksmart.domain.model.FuelType
import lk.picksmart.domain.model.LastCheckInputs
import lk.picksmart.domain.model.Platform
import lk.picksmart.domain.model.Vehicle
import lk.picksmart.domain.model.VehicleType
import lk.picksmart.domain.model.rates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckRideFormStateTest {

    private val calculator = ProfitCalculator()

    /** The G1 rates: Rs. 300/L, 40 km/L, Rs. 2/km reserve, no target. */
    private val vehicle = Vehicle(
        id = 1,
        name = "Bike",
        type = VehicleType.MOTORCYCLE,
        fuelType = FuelType.PETROL_92,
        fuelPricePerLitreCents = 30_000,
        fuelPriceSource = FuelPriceSource.MANUAL,
        fuelPriceEffectiveDate = null,
        autoUpdateFuelPrice = false,
        fuelEfficiencyKmPerLitre = 40.0,
        maintenanceReservePerKmCents = 200,
        targetProfitPerKmCents = null,
        isSample = false,
        archived = false,
        createdAtMillis = 0,
        updatedAtMillis = 0,
    )

    /** G1 as the rider would type it. */
    private val g1Form = CheckRideFormState(
        fare = "1000",
        commissionType = CommissionType.AMOUNT,
        commission = "200",
        pickupKm = "2",
        tripKm = "8",
    )

    private fun result(form: CheckRideFormState, v: Vehicle = vehicle): ProfitResult =
        when (val outcome = form.evaluate(v.rates(), calculator)) {
            is CheckRideOutcome.Calculated -> outcome.result
            else -> throw AssertionError("Expected a result, got $outcome")
        }

    private fun invalid(form: CheckRideFormState): CheckRideOutcome.Invalid =
        form.evaluate(vehicle.rates(), calculator) as? CheckRideOutcome.Invalid
            ?: throw AssertionError("Expected invalid, got ${form.evaluate(vehicle.rates(), calculator)}")

    @Test
    fun `G1 typed into the form gives the golden result`() {
        val r = result(g1Form)
        assertEquals(7_500L, r.fuelCostCents)
        assertEquals(2_000L, r.maintenanceReserveCents)
        assertEquals(20_000L, r.commissionCents)
        assertEquals(70_500L, r.netProfitCents)
        assertEquals(7_050L, r.netProfitPerKmCents)
    }

    @Test
    fun `G2 percent commission typed as 20`() {
        val r = result(g1Form.copy(commissionType = CommissionType.PERCENT, commission = "20"))
        assertEquals(20_000L, r.commissionCents)
        assertEquals(70_500L, r.netProfitCents)
    }

    @Test
    fun `G3 payout mode ignores whatever is in the commission field`() {
        val r = result(g1Form.copy(fare = "800", fareMode = FareMode.PAYOUT_AFTER_COMMISSION, commission = "999"))
        assertEquals(0L, r.commissionCents)
        assertEquals(70_500L, r.netProfitCents)
    }

    @Test
    fun `payout mode ignores an over-100 percent left in the hidden field`() {
        val form = g1Form.copy(
            fareMode = FareMode.PAYOUT_AFTER_COMMISSION,
            commissionType = CommissionType.PERCENT,
            commission = "150",
        )
        assertTrue(form.evaluate(vehicle.rates(), calculator) is CheckRideOutcome.Calculated)
    }

    @Test
    fun `G6 loss with return distance and other costs`() {
        val form = CheckRideFormState(
            fare = "300",
            commissionType = CommissionType.AMOUNT,
            commission = "60",
            otherCosts = "100",
            pickupKm = "5",
            tripKm = "10",
            returnKm = "5",
        )
        val r = result(form)
        assertEquals(15_000L, r.fuelCostCents)
        assertEquals(4_000L, r.maintenanceReserveCents)
        assertEquals(-5_000L, r.netProfitCents)
        assertEquals(ResultBand.LOSS, r.band)
    }

    @Test
    fun `the vehicle target sets the band`() {
        assertEquals(ResultBand.BELOW_TARGET, result(g1Form, vehicle.copy(targetProfitPerKmCents = 8_000)).band)
        assertEquals(ResultBand.GOOD, result(g1Form, vehicle.copy(targetProfitPerKmCents = 7_000)).band)
    }

    @Test
    fun `empty optional fields count as zero`() {
        val r = result(CheckRideFormState(fare = "500"))
        assertEquals(0.0, r.totalKm, 0.0)
        assertEquals(0L, r.commissionCents)
        assertEquals(50_000L, r.netProfitCents)
        assertEquals(null, r.netProfitPerKmCents)
    }

    @Test
    fun `an empty fare waits for input instead of showing a result`() {
        assertEquals(CheckRideOutcome.NeedsFare, g1Form.copy(fare = "").evaluate(vehicle.rates(), calculator))
        assertEquals(CheckRideOutcome.NeedsFare, CheckRideFormState().evaluate(vehicle.rates(), calculator))
    }

    @Test
    fun `a percent above 100 is flagged on the commission field, even before the fare`() {
        val form = g1Form.copy(fare = "", commissionType = CommissionType.PERCENT, commission = "150")
        assertEquals(mapOf(InputField.COMMISSION to CheckRideError.PERCENT_ABOVE_100), invalid(form).errors)
    }

    @Test
    fun `unparseable text is flagged on its own field`() {
        // Sanitised typing only allows digits and one dot, so "unparseable" means too large.
        val form = g1Form.copy(fare = "99999999999999999999", tripKm = "9".repeat(400))
        assertEquals(
            mapOf(InputField.FARE to CheckRideError.INVALID, InputField.TRIP_KM to CheckRideError.INVALID),
            invalid(form).errors,
        )
    }

    @Test
    fun `overrides replace the vehicle rates for this check only`() {
        // 10 km at Rs. 400/L and 20 km/L = Rs. 200 fuel; no reserve.
        val form = g1Form.copy(overrides = RateOverrides(fuelPrice = "400", efficiency = "20", maintenance = ""))
        val r = result(form)
        assertEquals(20_000L, r.fuelCostCents)
        assertEquals(0L, r.maintenanceReserveCents)
        assertEquals(30_000L, vehicle.fuelPricePerLitreCents)
    }

    @Test
    fun `overrides start from the vehicle rates and give the same result`() {
        val form = g1Form.copy(overrides = vehicle.toRateOverrides())
        assertEquals(result(g1Form), result(form))
    }

    @Test
    fun `an overridden efficiency of zero or left empty is flagged`() {
        val zero = g1Form.copy(overrides = RateOverrides("300", "0", "2"))
        assertEquals(mapOf(InputField.FUEL_EFFICIENCY to CheckRideError.MUST_BE_ABOVE_ZERO), invalid(zero).errors)

        val empty = g1Form.copy(overrides = RateOverrides("", "", "2"))
        assertEquals(
            mapOf(
                InputField.FUEL_PRICE to CheckRideError.REQUIRED,
                InputField.FUEL_EFFICIENCY to CheckRideError.REQUIRED,
            ),
            invalid(empty).errors,
        )
    }

    @Test
    fun `numbers too large to hold in cents are reported, not thrown`() {
        val form = CheckRideFormState(fare = "1", tripKm = "1000000000000000000")
        val outcome = invalid(form)
        assertTrue(outcome.resultTooLarge)
        assertTrue(outcome.errors.isEmpty())
    }

    @Test
    fun `remembered values pre-fill everything except the fare`() {
        val last = LastCheckInputs(
            platform = Platform.PICKME,
            fareMode = FareMode.GROSS_BEFORE_COMMISSION,
            commissionType = CommissionType.PERCENT,
            commissionHundredths = 1_250,
            otherCostsCents = 5_000,
            pickupKm = 2.0,
            tripKm = 8.5,
            returnKm = null,
        )
        val form = last.toFormState()
        assertEquals("", form.fare)
        assertEquals(Platform.PICKME, form.platform)
        assertEquals("12.5", form.commission)
        assertEquals("50", form.otherCosts)
        assertEquals("2", form.pickupKm)
        assertEquals("8.5", form.tripKm)
        assertEquals("", form.returnKm)
        assertEquals(null, form.overrides)
        assertEquals(last, form.toLastCheckInputs())
    }

    @Test
    fun `remembering skips empty and unparseable fields`() {
        val last = CheckRideFormState(fare = "1000", commission = ".", pickupKm = "", tripKm = "3").toLastCheckInputs()
        assertEquals(LastCheckInputs(tripKm = 3.0), last)
    }

    @Test
    fun `G1 with three edits from remembered values`() {
        // The rider's fee is remembered; a new offer needs only fare, pickup and trip.
        val start = LastCheckInputs(commissionType = CommissionType.AMOUNT, commissionHundredths = 20_000, pickupKm = 1.0, tripKm = 3.0)
            .toFormState()
        val edited = start.copy(fare = "1000", pickupKm = "2", tripKm = "8")
        assertEquals(70_500L, result(edited).netProfitCents)
    }
}
