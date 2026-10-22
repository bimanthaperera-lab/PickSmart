package lk.picksmart.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import lk.picksmart.domain.model.LastCheckInputs
import lk.picksmart.domain.model.ThemeMode

/**
 * App settings kept in DataStore. Keys are only ever added or migrated, never dropped or renamed
 * without a read-old-write-new step (CLAUDE.md N7).
 */
class UserPreferencesRepository(private val dataStore: DataStore<Preferences>) {

    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        prefs[Keys.THEME_MODE].toEnum(ThemeMode.SYSTEM)
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

    /** The values Check a Ride starts from. Defaults (all empty) before the first check. */
    val lastCheckInputs: Flow<LastCheckInputs> = dataStore.data.map { prefs ->
        val defaults = LastCheckInputs()
        LastCheckInputs(
            platform = prefs[Keys.CHECK_PLATFORM].toEnum(defaults.platform),
            fareMode = prefs[Keys.CHECK_FARE_MODE].toEnum(defaults.fareMode),
            commissionType = prefs[Keys.CHECK_COMMISSION_TYPE].toEnum(defaults.commissionType),
            commissionHundredths = prefs[Keys.CHECK_COMMISSION_HUNDREDTHS]?.takeIf { it >= 0 },
            otherCostsCents = prefs[Keys.CHECK_OTHER_COSTS_CENTS]?.takeIf { it >= 0 },
            pickupKm = prefs[Keys.CHECK_PICKUP_KM].validKm(),
            tripKm = prefs[Keys.CHECK_TRIP_KM].validKm(),
            returnKm = prefs[Keys.CHECK_RETURN_KM].validKm(),
        )
    }.distinctUntilChanged()

    /**
     * The fill-up whose km/L suggestion the rider dismissed with "Not now", so the same reading
     * isn't offered again. A later full-tank fill gives a new reading and a new chance.
     */
    val dismissedEfficiencyLogId: Flow<Long?> = dataStore.data
        .map { it[Keys.DISMISSED_EFFICIENCY_LOG_ID] }
        .distinctUntilChanged()

    suspend fun setDismissedEfficiencyLogId(id: Long) {
        dataStore.edit { it[Keys.DISMISSED_EFFICIENCY_LOG_ID] = id }
    }

    suspend fun setLastCheckInputs(inputs: LastCheckInputs) {
        dataStore.edit {
            it[Keys.CHECK_PLATFORM] = inputs.platform.name
            it[Keys.CHECK_FARE_MODE] = inputs.fareMode.name
            it[Keys.CHECK_COMMISSION_TYPE] = inputs.commissionType.name
            it.setOrRemove(Keys.CHECK_COMMISSION_HUNDREDTHS, inputs.commissionHundredths)
            it.setOrRemove(Keys.CHECK_OTHER_COSTS_CENTS, inputs.otherCostsCents)
            it.setOrRemove(Keys.CHECK_PICKUP_KM, inputs.pickupKm)
            it.setOrRemove(Keys.CHECK_TRIP_KM, inputs.tripKm)
            it.setOrRemove(Keys.CHECK_RETURN_KM, inputs.returnKm)
        }
    }

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

    private fun <T : Any> MutablePreferences.setOrRemove(key: Preferences.Key<T>, value: T?) {
        if (value == null) remove(key) else set(key, value)
    }

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val DEFAULT_VEHICLE_ID = longPreferencesKey("default_vehicle_id")

        // Last-used Check a Ride inputs. An absent key means the field was left empty.
        val CHECK_PLATFORM = stringPreferencesKey("check_platform")
        val CHECK_FARE_MODE = stringPreferencesKey("check_fare_mode")
        val CHECK_COMMISSION_TYPE = stringPreferencesKey("check_commission_type")
        val CHECK_COMMISSION_HUNDREDTHS = longPreferencesKey("check_commission_hundredths")
        val CHECK_OTHER_COSTS_CENTS = longPreferencesKey("check_other_costs_cents")
        val CHECK_PICKUP_KM = doublePreferencesKey("check_pickup_km")
        val CHECK_TRIP_KM = doublePreferencesKey("check_trip_km")
        val CHECK_RETURN_KM = doublePreferencesKey("check_return_km")

        val DISMISSED_EFFICIENCY_LOG_ID = longPreferencesKey("dismissed_efficiency_log_id")
    }
}

/** An unknown stored name (e.g. written by a newer version) falls back to [default]. */
private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
    this?.let { stored -> enumValues<E>().firstOrNull { it.name == stored } } ?: default

private fun Double?.validKm(): Double? = this?.takeIf { it.isFinite() && it >= 0 }
