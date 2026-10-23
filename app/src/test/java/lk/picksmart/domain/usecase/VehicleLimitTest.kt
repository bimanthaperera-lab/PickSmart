package lk.picksmart.domain.usecase

import lk.picksmart.domain.model.FuelType
import lk.picksmart.domain.model.VehicleInput
import lk.picksmart.domain.model.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sample vehicles for tests, not real rates. */
class VehicleLimitTest {

    private fun vehicle(id: Long, archived: Boolean = false) = newVehicle(
        VehicleInput(
            name = "Sample $id",
            type = VehicleType.MOTORCYCLE,
            fuelType = FuelType.PETROL_92,
            fuelPricePerLitreCents = 30_000,
            fuelEfficiencyKmPerLitre = 40.0,
            maintenanceReservePerKmCents = 200,
            targetProfitPerKmCents = null,
        ),
        isSample = false,
        nowMillis = 0,
    ).copy(id = id, archived = archived)

    @Test
    fun `the limit is 1 signed out and 3 signed in`() {
        assertEquals(1, vehicleLimit(signedIn = false))
        assertEquals(3, vehicleLimit(signedIn = true))
    }

    @Test
    fun `a rider under the limit can add a vehicle`() {
        assertTrue(canAddVehicle(emptyList(), vehicleLimit(signedIn = false)))
        assertTrue(canAddVehicle(listOf(vehicle(1), vehicle(2)), vehicleLimit(signedIn = true)))
    }

    @Test
    fun `a rider at the limit can't add another`() {
        assertFalse(canAddVehicle(listOf(vehicle(1)), vehicleLimit(signedIn = false)))
        assertFalse(canAddVehicle(listOf(vehicle(1), vehicle(2), vehicle(3)), vehicleLimit(signedIn = true)))
    }

    @Test
    fun `archived vehicles don't count`() {
        assertTrue(canAddVehicle(listOf(vehicle(1, archived = true)), vehicleLimit(signedIn = false)))
        assertTrue(canAddVehicle(listOf(vehicle(1), vehicle(2, archived = true), vehicle(3, archived = true)), 2))
    }

    @Test
    fun `a rider over the limit after signing out keeps everything but can't add more`() {
        val three = listOf(vehicle(1), vehicle(2), vehicle(3))
        assertFalse(canAddVehicle(three, vehicleLimit(signedIn = false)))
        // Nothing is taken away: the list is untouched, and archiving down under the limit works.
        assertEquals(ArchivePlan.Archive, archivePlan(three, vehicleId = 3, activeVehicleId = 1))
        assertFalse(canAddVehicle(three.map { if (it.id >= 2) it.copy(archived = true) else it }, vehicleLimit(signedIn = false)))
        assertTrue(canAddVehicle(three.map { if (it.id >= 2) it.copy(archived = true) else it }, vehicleLimit(signedIn = true)))
    }

    @Test
    fun `the last active vehicle can't be archived`() {
        assertEquals(ArchivePlan.LastVehicle, archivePlan(listOf(vehicle(1)), vehicleId = 1, activeVehicleId = 1))
        // Archived ones don't count as another vehicle to fall back on.
        assertEquals(
            ArchivePlan.LastVehicle,
            archivePlan(listOf(vehicle(1), vehicle(2, archived = true)), vehicleId = 1, activeVehicleId = 1),
        )
    }

    @Test
    fun `archiving the active vehicle asks which becomes active`() {
        val plan = archivePlan(listOf(vehicle(1), vehicle(2), vehicle(3, archived = true)), vehicleId = 1, activeVehicleId = 1)
        assertEquals(listOf(2L), (plan as ArchivePlan.ChooseActive).candidates.map { it.id })
    }

    @Test
    fun `archiving another vehicle needs no choice`() {
        assertEquals(ArchivePlan.Archive, archivePlan(listOf(vehicle(1), vehicle(2)), vehicleId = 2, activeVehicleId = 1))
    }

    @Test
    fun `archiving keeps everything but the flag`() {
        val v = vehicle(1)
        val archived = applyArchive(v, nowMillis = 5)
        assertTrue(archived.archived)
        assertEquals(v.copy(archived = true, updatedAtMillis = 5), archived)
    }
}
