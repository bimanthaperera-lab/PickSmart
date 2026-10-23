package lk.picksmart.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeParseException
import lk.picksmart.domain.model.FuelPriceList
import lk.picksmart.domain.model.FuelType
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

    /**
     * The last valid published fuel price list (CLAUDE.md section 8a), or null before the first
     * successful download. A list with any part missing reads as null rather than half a list.
     */
    val fuelPriceList: Flow<FuelPriceList?> = dataStore.data.map { prefs ->
        val effectiveFrom = prefs[Keys.FUEL_PRICE_EFFECTIVE_FROM]?.toLocalDateOrNull() ?: return@map null
        val fetchedAt = prefs[Keys.FUEL_PRICE_FETCHED_AT] ?: return@map null
        val prices = FuelType.entries.associateWith { prefs[Keys.fuelPriceCents(it)] ?: return@map null }
        FuelPriceList(effectiveFrom, fetchedAt, prices)
    }.distinctUntilChanged()

    /** Saves a list that passed validation. Replaces the previous one in a single write. */
    suspend fun setFuelPriceList(list: FuelPriceList) {
        dataStore.edit { prefs ->
            prefs[Keys.FUEL_PRICE_EFFECTIVE_FROM] = list.effectiveFrom.toString()
            prefs[Keys.FUEL_PRICE_FETCHED_AT] = list.fetchedAtMillis
            FuelType.entries.forEach { type -> list.priceFor(type)?.let { prefs[Keys.fuelPriceCents(type)] = it } }
        }
    }

    /** When the price file last answered (UTC epoch millis), for the 12-hour throttle. */
    val fuelPriceLastCheckAt: Flow<Long?> = dataStore.data
        .map { it[Keys.FUEL_PRICE_LAST_CHECK_AT] }
        .distinctUntilChanged()

    suspend fun setFuelPriceLastCheckAt(millis: Long) {
        dataStore.edit { it[Keys.FUEL_PRICE_LAST_CHECK_AT] = millis }
    }

    /**
     * Published prices the rider chose to keep their own price over, as keys from
     * `keptFuelPriceKey`. The prompt is not shown again for those.
     */
    val keptFuelPrices: Flow<Set<String>> = dataStore.data
        .map { it[Keys.FUEL_PRICE_KEPT].orEmpty() }
        .distinctUntilChanged()

    suspend fun addKeptFuelPrice(key: String) {
        dataStore.edit { it[Keys.FUEL_PRICE_KEPT] = it[Keys.FUEL_PRICE_KEPT].orEmpty() + key }
    }

    /**
     * Set when a published price was applied automatically and the rider hasn't seen the notice
     * yet: the vehicle id, so the notice shows once. Cleared when the rider dismisses it.
     */
    val fuelPriceAutoNoticeVehicleId: Flow<Long?> = dataStore.data
        .map { it[Keys.FUEL_PRICE_AUTO_NOTICE_VEHICLE_ID] }
        .distinctUntilChanged()

    suspend fun setFuelPriceAutoNoticeVehicleId(id: Long?) {
        dataStore.edit { it.setOrRemove(Keys.FUEL_PRICE_AUTO_NOTICE_VEHICLE_ID, id) }
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

        // Published fuel prices (CLAUDE.md section 8a). The list is stored as separate keys, one
        // price per fuel type, so adding a fuel type later only adds a key.
        val FUEL_PRICE_EFFECTIVE_FROM = stringPreferencesKey("fuel_price_effective_from")
        val FUEL_PRICE_FETCHED_AT = longPreferencesKey("fuel_price_fetched_at")
        fun fuelPriceCents(type: FuelType) = longPreferencesKey("fuel_price_cents_${type.name.lowercase()}")
        val FUEL_PRICE_LAST_CHECK_AT = longPreferencesKey("fuel_price_last_check_at")
        val FUEL_PRICE_KEPT = stringSetPreferencesKey("fuel_price_kept")
        val FUEL_PRICE_AUTO_NOTICE_VEHICLE_ID = longPreferencesKey("fuel_price_auto_notice_vehicle_id")
    }
}

/** An unknown stored name (e.g. written by a newer version) falls back to [default]. */
private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
    this?.let { stored -> enumValues<E>().firstOrNull { it.name == stored } } ?: default

private fun Double?.validKm(): Double? = this?.takeIf { it.isFinite() && it >= 0 }

private fun String.toLocalDateOrNull(): LocalDate? = try {
    LocalDate.parse(this)
} catch (e: DateTimeParseException) {
    null
}
