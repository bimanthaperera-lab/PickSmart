package lk.picksmart.data.sync

import kotlinx.coroutines.flow.first
import lk.picksmart.data.account.ProfilePhotoCache
import lk.picksmart.data.local.dao.SyncDao
import lk.picksmart.data.preferences.UserPreferencesRepository
import lk.picksmart.domain.model.Gender
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * The settings and details that follow the rider: the mobile number, gender, date of birth and
 * photo they chose (owner decision 2026-10-27), and which vehicle is active. The vehicle is stored
 * on the phone by its Room id and in the cloud by its sync id.
 */
class DataStoreSyncedSettings(
    private val preferences: UserPreferencesRepository,
    private val dao: SyncDao,
    private val photos: ProfilePhotoCache,
) : SyncedSettings {

    override suspend fun current(uid: String): CloudProfile {
        val activeId = preferences.defaultVehicleId.first()
        val active = activeId?.let { id -> dao.allVehicles().firstOrNull { it.id == id } }
        val details = preferences.personalDetails(uid).first()
        val removed = preferences.removedDetails(uid).first()
        return CloudProfile(
            mobileNumber = details.mobile,
            activeVehicleSyncId = active?.syncId,
            gender = details.gender?.name,
            birthDate = details.birthDate?.toString(),
            removed = buildSet {
                if (removed.mobile) add(CloudProfile.MOBILE)
                if (removed.gender) add(CloudProfile.GENDER)
                if (removed.birthDate) add(CloudProfile.BIRTH_DATE)
            },
        )
    }

    override suspend fun restore(uid: String, profile: CloudProfile) {
        val details = preferences.personalDetails(uid).first()
        val removed = preferences.removedDetails(uid).first()
        if (details.mobile == null && !removed.mobile && profile.mobileNumber != null) {
            preferences.setMobileNumber(uid, profile.mobileNumber)
        }
        val gender = profile.gender?.let { name -> Gender.entries.firstOrNull { it.name == name } }
        if (details.gender == null && !removed.gender && gender != null) preferences.setGender(uid, gender)
        val birthDate = profile.birthDate?.toLocalDateOrNull()
        if (details.birthDate == null && !removed.birthDate && birthDate != null) preferences.setBirthDate(uid, birthDate)

        val vehicles = dao.allVehicles()
        val activeId = preferences.defaultVehicleId.first()
        if (vehicles.any { it.id == activeId && !it.archived }) return
        val restored = vehicles.firstOrNull { it.syncId == profile.activeVehicleSyncId && !it.archived }
            ?: vehicles.firstOrNull { !it.archived }
        restored?.let { preferences.setDefaultVehicleId(it.id) }
    }

    override suspend fun photo(uid: String): ByteArray? = photos.ownBytes(uid)

    override suspend fun restorePhoto(uid: String, jpeg: ByteArray): Boolean = photos.restoreOwn(uid, jpeg)

    private fun String.toLocalDateOrNull(): LocalDate? = try {
        LocalDate.parse(this)
    } catch (e: DateTimeParseException) {
        null
    }
}
