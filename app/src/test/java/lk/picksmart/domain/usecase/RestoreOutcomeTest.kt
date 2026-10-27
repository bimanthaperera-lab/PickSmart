package lk.picksmart.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class RestoreOutcomeTest {

    @Test
    fun `a backup with a vehicle in use is restored`() {
        assertEquals(RestoreOutcome.RESTORED, restoreOutcome(newAccount = false, hasRecords = true, hasVehicle = true))
    }

    @Test
    fun `an existing account with nothing in the cloud is told it has no backup`() {
        assertEquals(RestoreOutcome.NO_BACKUP, restoreOutcome(newAccount = false, hasRecords = false, hasVehicle = false))
    }

    @Test
    fun `a new account goes straight to the vehicle`() {
        assertEquals(RestoreOutcome.SET_UP_VEHICLE, restoreOutcome(newAccount = true, hasRecords = false, hasVehicle = false))
    }

    @Test
    fun `a backup with records but no vehicle in use isn't called empty`() {
        assertEquals(RestoreOutcome.SET_UP_VEHICLE, restoreOutcome(newAccount = false, hasRecords = true, hasVehicle = false))
    }
}
