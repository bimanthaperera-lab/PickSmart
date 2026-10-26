package lk.picksmart.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lk.picksmart.data.account.ProfilePhotoCache
import lk.picksmart.data.preferences.UserPreferencesRepository
import java.io.File

/**
 * Erases the rider's data from the phone when they delete their account: every table (records,
 * vehicles, backup bookkeeping), profile photos, CSV exports, and the rider's settings. The theme,
 * the app language and the published fuel price list stay. Settings go last, because clearing
 * them sends the app back to the welcome screen.
 */
class PhoneDataEraser(
    private val database: PicksmartDatabase,
    private val preferences: UserPreferencesRepository,
    private val photos: ProfilePhotoCache,
    private val exportDirectory: File,
) {
    suspend fun erase() {
        withContext(Dispatchers.IO) {
            database.clearAllTables()
            exportDirectory.deleteRecursively()
        }
        photos.clearEverything()
        preferences.clearRiderData()
    }
}
