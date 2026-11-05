package lk.picksmart.domain.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FuelCostTest {

    @Test
    fun `price per litre divided by km per litre, in cents`() {
        assertEquals(750L, fuelCostPerKmCents(30_000, 40.0))
        assertEquals(1_035L, fuelCostPerKmCents(41_400, 40.0))
    }

    @Test
    fun `rounds half up to the cent`() {
        // Rs. 297.50 / 45 = Rs. 6.6111…
        assertEquals(661L, fuelCostPerKmCents(29_750, 45.0))
        // Rs. 1.25 / 2 = 0.625 → 0.63
        assertEquals(63L, fuelCostPerKmCents(125, 2.0))
    }

    @Test
    fun `impossible inputs give null, not NaN or a crash`() {
        assertNull(fuelCostPerKmCents(30_000, 0.0))
        assertNull(fuelCostPerKmCents(30_000, -1.0))
        assertNull(fuelCostPerKmCents(30_000, Double.NaN))
        assertNull(fuelCostPerKmCents(30_000, Double.POSITIVE_INFINITY))
        assertNull(fuelCostPerKmCents(-1, 40.0))
    }

    @Test
    fun `a cost too large for a Long is null instead of a crash`() {
        assertNull(fuelCostPerKmCents(Long.MAX_VALUE, 0.001))
    }
}
