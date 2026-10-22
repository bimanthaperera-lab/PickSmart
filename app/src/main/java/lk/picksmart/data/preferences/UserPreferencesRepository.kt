package lk.picksmart.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import lk.picksmart.domain.model.ThemeMode

/**
 * App settings kept in DataStore. Keys are only ever added or migrated, never dropped or renamed
 * without a read-old-write-new step (CLAUDE.md N7).
 */
class UserPreferencesRepository(private val dataStore: DataStore<Preferences>) {

    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        // An unknown stored value (e.g. written by a newer version) falls back to the default.
        prefs[Keys.THEME_MODE]?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
            ?: ThemeMode.SYSTEM
    }.distinctUntilChanged()

    /**
     * True once the rider has finished first-launch setup. Nothing in the app ever sets it back
     * to false, so an update never sends an existing rider through setup again.
     */
    val onboardingComplete: Flow<Boolean> = dataStore.data
        .map { it[Keys.ONBOARDING_COMPLETE] ?: false }
        .distinctUntilChanged()

    /** The vehicle Check a Ride pre-fills from, or null before setup. */
    val defaultVehicleId: Flow<Long?> = dataStore.data
        .map { it[Keys.DEFAULT_VEHICLE_ID] }
        .distinctUntilChanged()

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDefaultVehicleId(id: Long) {
        dataStore.edit { it[Keys.DEFAULT_VEHICLE_ID] = id }
    }

    /** Records the default vehicle and finishes setup in one atomic write. */
    suspend fun completeOnboarding(defaultVehicleId: Long) {
        dataStore.edit {
            it[Keys.DEFAULT_VEHICLE_ID] = defaultVehicleId
            it[Keys.ONBOARDING_COMPLETE] = true
        }
    }

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val DEFAULT_VEHICLE_ID = longPreferencesKey("default_vehicle_id")
    }
}
