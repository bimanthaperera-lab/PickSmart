package lk.picksmart.domain.usecase

import lk.picksmart.domain.model.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Sample vehicles only. */
class VehicleMatchTest {

    private fun match(restored: VehicleIdentity, vararg candidates: VehicleIdentity) =
        matchVehicle(restored, candidates.toList()) { it }

    private val bike = VehicleIdentity("Sample bike", VehicleType.MOTORCYCLE, "WP ABC-1234")

    @Test
    fun `the same plate is the same vehicle, whatever the name or spacing`() {
        val local = VehicleIdentity("My scooter", VehicleType.MOTORCYCLE, "wp abc 1234")
        assertEquals(local, match(bike, local))
    }

    @Test
    fun `the same name and type match when a plate is missing`() {
        val local = VehicleIdentity("  sample   BIKE ", VehicleType.MOTORCYCLE, null)
        assertEquals(local, match(bike, local))
        assertEquals(bike, match(local, bike))
    }

    @Test
    fun `different plates are different vehicles, even with the same name`() {
        assertNull(match(bike, bike.copy(plateNumber = "WP XYZ-9999")))
    }

    @Test
    fun `the same name on another type is a different vehicle`() {
        assertNull(match(bike.copy(plateNumber = null), VehicleIdentity("Sample bike", VehicleType.THREE_WHEELER, null)))
    }

    @Test
    fun `a plate match wins over a name match`() {
        val byName = bike.copy(plateNumber = null)
        val byPlate = VehicleIdentity("Other name", VehicleType.MOTORCYCLE, "WP ABC-1234")
        assertEquals(byPlate, match(bike, byName, byPlate))
    }

    @Test
    fun `nothing alike, nothing matched`() {
        assertNull(match(bike, VehicleIdentity("Sample car", VehicleType.CAR, null)))
    }
}
