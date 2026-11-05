package lk.picksmart.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lk.picksmart.data.account.ProfilePhotoCache
import lk.picksmart.data.preferences.UserPreferencesRepository
import java.io.File

/**
 * Erases a rider's data from the phone when they delete their account: every table of their
 * space's database (records, vehicles, backup bookkeeping), their profile photos, CSV exports,
 * and their space's settings. Other accounts' spaces on the phone, the theme, the app language
 * and the published fuel price list stay. Settings go last, because clearing them sends the app
 * back to the welcome screen.
 */
class PhoneDataEraser(
    private val database: PicksmartDatabase,
    private val preferences: UserPreferencesRepository,
    private val photos: ProfilePhotoCache,
    private val exportDirectory: File,
) {
    suspend fun erase(uid: String) {
        withContext(Dispatchers.IO) {
            database.clearAllTables()
            exportDirectory.deleteRecursively()
        }
        photos.forget(uid)
        preferences.clearRiderData(uid)
    }
}
