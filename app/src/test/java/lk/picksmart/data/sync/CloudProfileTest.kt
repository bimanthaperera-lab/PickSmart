package lk.picksmart.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

/** Sample values only. */
class CloudProfileTest {

    @Test
    fun `what is set goes up, and nothing else`() {
        val profile = CloudProfile("0770000000", "vehicle-1", gender = "FEMALE", birthDate = "1990-01-31")
        assertEquals(
            mapOf("mobileNumber" to "0770000000", "activeVehicleSyncId" to "vehicle-1", "gender" to "FEMALE", "birthDate" to "1990-01-31"),
            profile.toCloud(),
        )
        // A phone that never had a detail leaves the cloud's alone.
        assertEquals(mapOf("activeVehicleSyncId" to "vehicle-1"), CloudProfile(null, "vehicle-1").toCloud())
    }

    @Test
    fun `a removed detail is sent as null so the backup drops it`() {
        val profile = CloudProfile(null, null, removed = setOf(CloudProfile.GENDER, CloudProfile.BIRTH_DATE))
        assertEquals(mapOf("gender" to null, "birthDate" to null), profile.toCloud())
    }

    @Test
    fun `a detail set again wins over an old removal`() {
        val profile = CloudProfile(null, null, gender = "MALE", removed = setOf(CloudProfile.GENDER))
        assertEquals(mapOf("gender" to "MALE"), profile.toCloud())
    }

    @Test
    fun `the backup's details come back as they were`() {
        val data = mapOf("mobileNumber" to "0770000000", "gender" to "OTHER", "birthDate" to "2000-12-01", "activeVehicleSyncId" to "v")
        assertEquals(CloudProfile("0770000000", "v", gender = "OTHER", birthDate = "2000-12-01"), CloudProfile.fromCloud(data))
    }
}
