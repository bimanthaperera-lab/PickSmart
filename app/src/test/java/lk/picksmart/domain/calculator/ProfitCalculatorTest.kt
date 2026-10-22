package lk.picksmart.domain.calculator

import lk.picksmart.domain.model.CommissionType
import lk.picksmart.domain.model.FareMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfitCalculatorTest {

    private val calculator = ProfitCalculator()

    /** G1 inputs: fare 1000, AMOUNT 200, 2 + 8 + 0 km, 40 km/L, Rs. 300/L, Rs. 2/km, other 0. */
    private val g1 = ProfitInput(
        fareCents = 100_000,
        fareMode = FareMode.GROSS_BEFORE_COMMISSION,
        commissionType = CommissionType.AMOUNT,
        commissionHundredths = 20_000,
        pickupKm = 2.0,
        tripKm = 8.0,
        returnKm = 0.0,
        fuelEfficiencyKmPerLitre = 40.0,
        fuelPricePerLitreCents = 30_000,
        maintenanceReservePerKmCents = 200,
        otherCostsCents = 0,
    )

    private fun success(input: ProfitInput): ProfitResult =
        when (val r = calculator.calculate(input)) {
            is CalculationResult.Success -> r.result
            is CalculationResult.Invalid -> throw AssertionError("Expected success, got ${r.errors}")
        }

    private fun errors(input: ProfitInput): List<ValidationError> =
        when (val r = calculator.calculate(input)) {
            is CalculationResult.Success -> throw AssertionError("Expected validation errors, got ${r.result}")
            is CalculationResult.Invalid -> r.errors.also { assertTrue(it.isNotEmpty()) }
        }

    // ---- Golden tests (CLAUDE.md section 6) ----

    @Test
    fun `G1 gross mode with amount commission`() {
        val r = success(g1)
        assertEquals(10.0, r.totalKm, 0.0)
        assertEquals(7_500L, r.fuelCostCents)
        assertEquals(2_000L, r.maintenanceReserveCents)
        assertEquals(20_000L, r.commissionCents)
        assertEquals(70_500L, r.netProfitCents)
        assertEquals(7_050L, r.netProfitPerKmCents)
        assertEquals(ResultBand.PROFIT, r.band)
    }

    @Test
    fun `G2 gross mode with 20 percent commission`() {
        val r = success(g1.copy(commissionType = CommissionType.PERCENT, commissionHundredths = 2_000))
        assertEquals(20_000L, r.commissionCents)
        assertEquals(70_500L, r.netProfitCents)
    }

    @Test
    fun `G3 payout mode ignores the commission field`() {
        val r = success(
            g1.copy(
                fareCents = 80_000,
                fareMode = FareMode.PAYOUT_AFTER_COMMISSION,
                commissionHundredths = 99_999,
            ),
        )
        assertEquals(0L, r.commissionCents)
        assertEquals(70_500L, r.netProfitCents)
    }

    @Test
    fun `G4 fuel cost rounds half up to cents`() {
        val r = success(
            g1.copy(
                pickupKm = 1.0,
                tripKm = 6.0,
                fuelEfficiencyKmPerLitre = 45.0,
                fuelPricePerLitreCents = 29_750,
                maintenanceReservePerKmCents = 0,
            ),
        )
        assertEquals(7.0, r.totalKm, 0.0)
        // 7 × 297.50 ÷ 45 = 46.2777…
        assertEquals(4_628L, r.fuelCostCents)
    }

    @Test
    fun `G5 zero distance gives no per km figure`() {
        val r = success(
            g1.copy(fareCents = 50_000, commissionHundredths = 0, pickupKm = 0.0, tripKm = 0.0, returnKm = 0.0),
        )
        assertEquals(0L, r.fuelCostCents)
        assertEquals(0L, r.maintenanceReserveCents)
        assertEquals(50_000L, r.netProfitCents)
        assertNull(r.netProfitPerKmCents)
    }

    @Test
    fun `G6 small profit, then loss with other costs`() {
        val g6 = g1.copy(fareCents = 30_000, commissionHundredths = 6_000, pickupKm = 5.0, tripKm = 10.0, returnKm = 5.0)
        val profit = success(g6)
        assertEquals(15_000L, profit.fuelCostCents)
        assertEquals(4_000L, profit.maintenanceReserveCents)
        assertEquals(5_000L, profit.netProfitCents)
        assertEquals(ResultBand.PROFIT, profit.band)

        val loss = success(g6.copy(otherCostsCents = 10_000))
        assertEquals(-5_000L, loss.netProfitCents)
        assertEquals(ResultBand.LOSS, loss.band)
    }

    @Test
    fun `G7 target band`() {
        assertEquals(ResultBand.BELOW_TARGET, success(g1.copy(targetProfitPerKmCents = 8_000)).band)
        assertEquals(ResultBand.GOOD, success(g1.copy(targetProfitPerKmCents = 7_000)).band)
    }

    // ---- Bands ----

    @Test
    fun `exactly meeting the target is good`() {
        assertEquals(ResultBand.GOOD, success(g1.copy(targetProfitPerKmCents = 7_050)).band)
    }

    @Test
    fun `zero net profit without a target is break even`() {
        // Fuel 75.00 + reserve 20.00 = 95.00, no commission.
        val r = success(g1.copy(fareCents = 9_500, commissionHundredths = 0))
        assertEquals(0L, r.netProfitCents)
        assertEquals(ResultBand.BREAK_EVEN, r.band)
    }

    @Test
    fun `a loss stays a loss even when a target is set`() {
        val r = success(g1.copy(fareCents = 1_000, commissionHundredths = 0, targetProfitPerKmCents = 0))
        assertEquals(ResultBand.LOSS, r.band)
    }

    @Test
    fun `target is ignored when there is no distance to divide by`() {
        val r = success(
            g1.copy(pickupKm = 0.0, tripKm = 0.0, targetProfitPerKmCents = 8_000),
        )
        assertEquals(ResultBand.PROFIT, r.band)
    }

    // ---- Validation ----

    @Test
    fun `negative inputs are reported per field`() {
        val e = errors(
            g1.copy(
                fareCents = -1,
                commissionHundredths = -1,
                pickupKm = -1.0,
                tripKm = -1.0,
                returnKm = -1.0,
                fuelPricePerLitreCents = -1,
                maintenanceReservePerKmCents = -1,
                otherCostsCents = -1,
                targetProfitPerKmCents = -1,
            ),
        )
        val expected = listOf(
            InputField.FARE,
            InputField.COMMISSION,
            InputField.PICKUP_KM,
            InputField.TRIP_KM,
            InputField.RETURN_KM,
            InputField.FUEL_PRICE,
            InputField.MAINTENANCE_RESERVE,
            InputField.OTHER_COSTS,
            InputField.TARGET_PROFIT_PER_KM,
        ).map { ValidationError.Negative(it) }
        assertEquals(expected, e)
    }

    @Test
    fun `zero fuel efficiency is rejected`() {
        assertEquals(
            listOf(ValidationError.NotPositive(InputField.FUEL_EFFICIENCY)),
            errors(g1.copy(fuelEfficiencyKmPerLitre = 0.0)),
        )
    }

    @Test
    fun `negative fuel efficiency is rejected`() {
        assertEquals(
            listOf(ValidationError.NotPositive(InputField.FUEL_EFFICIENCY)),
            errors(g1.copy(fuelEfficiencyKmPerLitre = -40.0)),
        )
    }

    @Test
    fun `NaN and infinite numbers are rejected`() {
        assertEquals(
            listOf(
                ValidationError.NotANumber(InputField.PICKUP_KM),
                ValidationError.NotANumber(InputField.TRIP_KM),
                ValidationError.NotANumber(InputField.FUEL_EFFICIENCY),
            ),
            errors(
                g1.copy(
                    pickupKm = Double.NaN,
                    tripKm = Double.POSITIVE_INFINITY,
                    fuelEfficiencyKmPerLitre = Double.NaN,
                ),
            ),
        )
    }

    @Test
    fun `percentage above 100 is rejected`() {
        assertEquals(
            listOf(ValidationError.PercentAbove100),
            errors(g1.copy(commissionType = CommissionType.PERCENT, commissionHundredths = 10_001)),
        )
    }

    @Test
    fun `exactly 100 percent commission takes the whole fare`() {
        val r = success(g1.copy(commissionType = CommissionType.PERCENT, commissionHundredths = 10_000))
        assertEquals(100_000L, r.commissionCents)
    }

    @Test
    fun `an amount commission above 100 rupees is not a percentage error`() {
        val r = success(g1.copy(commissionHundredths = 50_000))
        assertEquals(50_000L, r.commissionCents)
    }

    @Test
    fun `hidden commission field is not validated in payout mode`() {
        success(g1.copy(fareMode = FareMode.PAYOUT_AFTER_COMMISSION, commissionHundredths = -5))
        success(
            g1.copy(
                fareMode = FareMode.PAYOUT_AFTER_COMMISSION,
                commissionType = CommissionType.PERCENT,
                commissionHundredths = 50_000,
            ),
        )
    }

    // ---- Large values ----

    @Test
    fun `very large but valid values do not overflow`() {
        val r = success(
            g1.copy(
                fareCents = Long.MAX_VALUE,
                commissionType = CommissionType.PERCENT,
                commissionHundredths = 10_000,
                pickupKm = 0.0,
                tripKm = 1_000_000.0,
                fuelEfficiencyKmPerLitre = 0.01,
                fuelPricePerLitreCents = 1_000_000_000,
                maintenanceReservePerKmCents = 1_000_000_000,
            ),
        )
        // fare × 100% would overflow a Long multiplication; BigDecimal keeps it exact.
        assertEquals(Long.MAX_VALUE, r.commissionCents)
        // 1,000,000 km × 1e9 cents ÷ 0.01 km/L
        assertEquals(100_000_000_000_000_000L, r.fuelCostCents)
        assertEquals(1_000_000_000_000_000L, r.maintenanceReserveCents)
        assertEquals(-101_000_000_000_000_000L, r.netProfitCents)
        assertEquals(-101_000_000_000L, r.netProfitPerKmCents)
    }

    @Test
    fun `results too large for cents are reported, not thrown`() {
        assertEquals(
            listOf(ValidationError.ResultTooLarge),
            errors(g1.copy(tripKm = 1e300)),
        )
        assertEquals(
            listOf(ValidationError.ResultTooLarge),
            errors(g1.copy(fareCents = 0, commissionHundredths = Long.MAX_VALUE, otherCostsCents = Long.MAX_VALUE)),
        )
    }

    @Test
    fun `largest fare with no costs is kept exactly`() {
        val r = success(
            g1.copy(
                fareCents = Long.MAX_VALUE,
                commissionHundredths = 0,
                pickupKm = 0.0,
                tripKm = 0.0,
            ),
        )
        assertEquals(Long.MAX_VALUE, r.netProfitCents)
    }

    // ---- Rounding and exactness ----

    @Test
    fun `half a cent rounds up in every money field`() {
        // 2.5 km × 1 cent = 2.5 → 3; 50 cents × 1% = 0.5 → 1.
        val r = success(
            g1.copy(
                fareCents = 50,
                commissionType = CommissionType.PERCENT,
                commissionHundredths = 100,
                pickupKm = 0.0,
                tripKm = 2.5,
                fuelEfficiencyKmPerLitre = 1.0,
                fuelPricePerLitreCents = 0,
                maintenanceReservePerKmCents = 1,
            ),
        )
        assertEquals(3L, r.maintenanceReserveCents)
        assertEquals(1L, r.commissionCents)
        // 50 − 1 − 0 − 3 = 46; 46 ÷ 2.5 = 18.4 → 18
        assertEquals(46L, r.netProfitCents)
        assertEquals(18L, r.netProfitPerKmCents)
    }

    @Test
    fun `decimal distances add exactly`() {
        val r = success(g1.copy(pickupKm = 0.1, tripKm = 0.2, returnKm = 0.0))
        assertEquals(0.3, r.totalKm, 0.0)
    }

    @Test
    fun `breakdown always adds up to net profit`() {
        val input = g1.copy(
            fareCents = 123_457,
            commissionType = CommissionType.PERCENT,
            commissionHundredths = 1_750,
            pickupKm = 1.3,
            tripKm = 7.9,
            returnKm = 2.2,
            fuelEfficiencyKmPerLitre = 37.5,
            fuelPricePerLitreCents = 29_750,
            maintenanceReservePerKmCents = 175,
            otherCostsCents = 4_321,
        )
        val r = success(input)
        assertEquals(
            input.fareCents - r.commissionCents - r.fuelCostCents - r.maintenanceReserveCents - input.otherCostsCents,
            r.netProfitCents,
        )
    }
}
