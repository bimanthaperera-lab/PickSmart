package lk.picksmart.data.sync

import kotlinx.coroutines.flow.first
import lk.picksmart.data.local.dao.SyncDao
import lk.picksmart.data.preferences.UserPreferencesRepository

/**
 * The settings that follow the rider: the mobile number they typed, and which vehicle is active.
 * The vehicle is stored on the phone by its Room id and in the cloud by its sync id.
 */
class DataStoreSyncedSettings(
    private val preferences: UserPreferencesRepository,
    private val dao: SyncDao,
) : SyncedSettings {

    override suspend fun current(uid: String): CloudProfile {
        val activeId = preferences.defaultVehicleId.first()
        val active = activeId?.let { id -> dao.allVehicles().firstOrNull { it.id == id } }
        return CloudProfile(preferences.mobileNumber(uid).first(), active?.syncId)
    }

    override suspend fun restore(uid: String, profile: CloudProfile) {
        if (preferences.mobileNumber(uid).first() == null && profile.mobileNumber != null) {
            preferences.setMobileNumber(uid, profile.mobileNumber)
        }
        val vehicles = dao.allVehicles()
        val activeId = preferences.defaultVehicleId.first()
        if (vehicles.any { it.id == activeId && !it.archived }) return
        val restored = vehicles.firstOrNull { it.syncId == profile.activeVehicleSyncId && !it.archived }
            ?: vehicles.firstOrNull { !it.archived }
        restored?.let { preferences.setDefaultVehicleId(it.id) }
    }
}
