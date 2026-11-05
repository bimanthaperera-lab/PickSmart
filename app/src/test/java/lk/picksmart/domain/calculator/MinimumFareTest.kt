package lk.picksmart.domain.calculator

import lk.picksmart.domain.model.CommissionType
import lk.picksmart.domain.model.FareMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sample data: Rs. 300/L at 40 km/L (Rs. 7.50/km), Rs. 2/km reserve, Rs. 50/km target. */
class MinimumFareTest {

    private fun fare(
        target: Long? = 5_000,
        fareMode: FareMode = FareMode.GROSS_BEFORE_COMMISSION,
        type: CommissionType = CommissionType.PERCENT,
        commission: Long? = 0,
        price: Long = 30_000,
        efficiency: Double = 40.0,
        reserve: Long = 200,
    ) = minimumFarePerKm(price, efficiency, reserve, target, fareMode, type, commission)

    @Test
    fun `target plus fuel plus reserve per km with no commission`() {
        // 50 + 7.50 + 2 = Rs. 59.50/km
        assertEquals(MinimumFare(5_950, 0), fare())
    }

    @Test
    fun `a percentage commission divides`() {
        // 59.50 ÷ 0.8 = 74.375 → Rs. 74.38/km
        assertEquals(MinimumFare(7_438, 0), fare(commission = 2_000))
    }

    @Test
    fun `a fare at the minimum per km meets the target`() {
        val minimum = fare(commission = 2_000)!!
        val result = (
            ProfitCalculator().calculate(
                ProfitInput(
                    fareCents = minimum.perKmCents * 10,
                    fareMode = FareMode.GROSS_BEFORE_COMMISSION,
                    commissionType = CommissionType.PERCENT,
                    commissionHundredths = 2_000,
                    pickupKm = 2.0,
                    tripKm = 8.0,
                    fuelEfficiencyKmPerLitre = 40.0,
                    fuelPricePerLitreCents = 30_000,
                    maintenanceReservePerKmCents = 200,
                    targetProfitPerKmCents = 5_000,
                ),
            ) as CalculationResult.Success
            ).result
        assertEquals(ResultBand.GOOD, result.band)
        assertTrue(result.netProfitPerKmCents!! >= 5_000)
    }

    @Test
    fun `a fixed commission is added once per trip`() {
        assertEquals(MinimumFare(5_950, 20_000), fare(type = CommissionType.AMOUNT, commission = 20_000))
    }

    @Test
    fun `payout mode has no commission`() {
        assertEquals(MinimumFare(5_950, 0), fare(fareMode = FareMode.PAYOUT_AFTER_COMMISSION, commission = 2_000))
        assertEquals(
            MinimumFare(5_950, 0),
            fare(fareMode = FareMode.PAYOUT_AFTER_COMMISSION, type = CommissionType.AMOUNT, commission = 20_000),
        )
    }

    @Test
    fun `no target gives the break-even fare`() {
        assertEquals(MinimumFare(950, 0), fare(target = null))
    }

    @Test
    fun `no commission entered counts as none`() {
        assertEquals(MinimumFare(5_950, 0), fare(commission = null))
    }

    @Test
    fun `rounds half up to the cent without rounding the fuel cost first`() {
        // 297.50 ÷ 45 = 6.6111… ; ÷ 0.9 = 7.3456… → Rs. 7.35/km
        assertEquals(MinimumFare(735, 0), fare(target = null, price = 29_750, efficiency = 45.0, reserve = 0, commission = 1_000))
    }

    @Test
    fun `impossible inputs give null, not NaN or a crash`() {
        assertNull(fare(efficiency = 0.0))
        assertNull(fare(efficiency = Double.NaN))
        assertNull(fare(price = -1))
        assertNull(fare(reserve = -1))
        assertNull(fare(target = -1))
        assertNull(fare(commission = -1))
        assertNull(fare(commission = 10_000))
        assertNull(fare(commission = 12_000))
        assertNull(fare(price = Long.MAX_VALUE, efficiency = 0.001))
    }
}
