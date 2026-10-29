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
import lk.picksmart.domain.model.Gender
import lk.picksmart.domain.model.LastCheckInputs
import lk.picksmart.domain.model.PersonalDetails
import lk.picksmart.domain.model.ThemeMode
import lk.picksmart.domain.usecase.FIRST_SPACE

/** Personal details the rider removed on this phone, which cloud backup then removes too. */
data class RemovedDetails(val mobile: Boolean, val gender: Boolean, val birthDate: Boolean)

/**
 * App settings kept in DataStore. Keys are only ever added or migrated, never dropped or renamed
 * without a read-old-write-new step (CLAUDE.md N7).
 *
 * Settings that belong to the phone (theme, Read ride offers paused, the published fuel prices)
 * are shared. Settings that go with a rider's records (setup, active vehicle, last Check a Ride
 * inputs and the like) belong to [space] (`DataSpaces.kt`): the first space keeps the original key
 * names, and any other space uses the same names ending in `@space`, so each account keeps its own.
 */
class UserPreferencesRepository(private val dataStore: DataStore<Preferences>, space: String = FIRST_SPACE) {

    private val keys = SpaceKeys(if (space == FIRST_SPACE) "" else "@$space")

    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        prefs[Keys.THEME_MODE].toEnum(ThemeMode.SYSTEM)
    }.distinctUntilChanged()

    /**
     * True once the rider has finished first-launch setup. Nothing in the app ever sets it back
     * to false, so an update never sends an existing rider through setup again.
     */
    val onboardingComplete: Flow<Boolean> = dataStore.data
        .map { it[keys.ONBOARDING_COMPLETE] ?: false }
        .distinctUntilChanged()

    /**
     * True while the rider has paused the offer reader inside Picksmart (CLAUDE.md section 9b): it
     * stays on in Android's Accessibility settings but reads nothing and shows no badge.
     */
    val offerReaderPaused: Flow<Boolean> = dataStore.data
        .map { it[Keys.OFFER_READER_PAUSED] ?: false }
        .distinctUntilChanged()

    suspend fun setOfferReaderPaused(paused: Boolean) {
        dataStore.edit { it[Keys.OFFER_READER_PAUSED] = paused }
    }

    /** True when the offer reader plays a chime for a new offer's profit or loss (section 9b). Off by default. */
    val offerSound: Flow<Boolean> = dataStore.data
        .map { it[Keys.OFFER_SOUND] ?: false }
        .distinctUntilChanged()

    suspend fun setOfferSound(on: Boolean) {
        dataStore.edit { it[Keys.OFFER_SOUND] = on }
    }

    /** The vehicle Check a Ride pre-fills from, or null before setup. */
    val defaultVehicleId: Flow<Long?> = dataStore.data
        .map { it[keys.DEFAULT_VEHICLE_ID] }
        .distinctUntilChanged()

    /** The values Check a Ride starts from. Defaults (all empty) before the first check. */
    val lastCheckInputs: Flow<LastCheckInputs> = dataStore.data.map { prefs ->
        val defaults = LastCheckInputs()
        LastCheckInputs(
            platform = prefs[keys.CHECK_PLATFORM].toEnum(defaults.platform),
            fareMode = prefs[keys.CHECK_FARE_MODE].toEnum(defaults.fareMode),
            commissionType = prefs[keys.CHECK_COMMISSION_TYPE].toEnum(defaults.commissionType),
            commissionHundredths = prefs[keys.CHECK_COMMISSION_HUNDREDTHS]?.takeIf { it >= 0 },
            otherCostsCents = prefs[keys.CHECK_OTHER_COSTS_CENTS]?.takeIf { it >= 0 },
            pickupKm = prefs[keys.CHECK_PICKUP_KM].validKm(),
            tripKm = prefs[keys.CHECK_TRIP_KM].validKm(),
            returnKm = prefs[keys.CHECK_RETURN_KM].validKm(),
        )
    }.distinctUntilChanged()

    /**
     * The fill-up whose km/L suggestion the rider dismissed with "Not now", so the same reading
     * isn't offered again. A later full-tank fill gives a new reading and a new chance.
     */
    val dismissedEfficiencyLogId: Flow<Long?> = dataStore.data
        .map { it[keys.DISMISSED_EFFICIENCY_LOG_ID] }
        .distinctUntilChanged()

    /**
     * The mobile number the rider typed for this account (CLAUDE.md section 14), or null. It is
     * optional and not verified, and is never used to send SMS.
     */
    fun mobileNumber(uid: String): Flow<String?> = dataStore.data
        .map { it[Keys.mobileNumber(uid)] }
        .distinctUntilChanged()

    /** Saves the number, or removes it when [number] is null or blank. */
    suspend fun setMobileNumber(uid: String, number: String?) {
        dataStore.edit { it.setOrRemove(Keys.mobileNumber(uid), number?.takeIf(String::isNotBlank), Keys.mobileRemoved(uid)) }
    }

    /**
     * The mobile number, gender and birth date the rider added for this account. They are kept on
     * the device when the rider signs out, and backed up with the account (CLAUDE.md section 14).
     */
    fun personalDetails(uid: String): Flow<PersonalDetails> = dataStore.data
        .map { prefs ->
            PersonalDetails(
                mobile = prefs[Keys.mobileNumber(uid)],
                gender = prefs[Keys.gender(uid)]?.let { stored -> Gender.entries.firstOrNull { it.name == stored } },
                birthDate = prefs[Keys.birthDate(uid)]?.toLocalDateOrNull(),
            )
        }
        .distinctUntilChanged()

    /** Saves the gender, or removes it when [gender] is null. */
    suspend fun setGender(uid: String, gender: Gender?) {
        dataStore.edit { it.setOrRemove(Keys.gender(uid), gender?.name, Keys.genderRemoved(uid)) }
    }

    /** Saves the birth date, or removes it when [date] is null. */
    suspend fun setBirthDate(uid: String, date: LocalDate?) {
        dataStore.edit { it.setOrRemove(Keys.birthDate(uid), date?.toString(), Keys.birthDateRemoved(uid)) }
    }

    /**
     * True after the rider skipped restoring this space's backup and started fresh (owner decision
     * 2026-10-27): it stays signed in but doesn't back up until they turn backup on again.
     */
    val backupPaused: Flow<Boolean> = dataStore.data
        .map { it[keys.BACKUP_PAUSED] == true }
        .distinctUntilChanged()

    suspend fun setBackupPaused(paused: Boolean) {
        dataStore.edit { if (paused) it[keys.BACKUP_PAUSED] = true else it.remove(keys.BACKUP_PAUSED) }
    }

    /** Which of [uid]'s personal details the rider removed on this phone and hasn't set again. */
    fun removedDetails(uid: String): Flow<RemovedDetails> = dataStore.data
        .map {
            RemovedDetails(
                mobile = it[Keys.mobileRemoved(uid)] == true,
                gender = it[Keys.genderRemoved(uid)] == true,
                birthDate = it[Keys.birthDateRemoved(uid)] == true,
            )
        }
        .distinctUntilChanged()

    /** Stores [value] under [key], or removes it and remembers the removal in [removed]. */
    private fun MutablePreferences.setOrRemove(key: Preferences.Key<String>, value: String?, removed: Preferences.Key<Boolean>) {
        if (value == null) {
            remove(key)
            this[removed] = true
        } else {
            this[key] = value
            remove(removed)
        }
    }

    /**
     * When the rider deletes the account [uid] (CLAUDE.md section 14): removes this space's
     * settings, including setup, so the app starts again at the welcome screen, and the details
     * kept for that account. The phone's settings (theme, the published fuel price list) and other
     * accounts' settings stay.
     */
    suspend fun clearRiderData(uid: String) {
        dataStore.edit { prefs ->
            keys.all.forEach { prefs.remove(it) }
            Keys.accountKeys(uid).forEach { prefs.remove(it) }
        }
    }

    suspend fun setDismissedEfficiencyLogId(id: Long) {
        dataStore.edit { it[keys.DISMISSED_EFFICIENCY_LOG_ID] = id }
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

    /**
     * The last price the rider used for each fuel type on this phone (cents per litre), so picking
     * that type again fills it in (`fuelPriceSuggestions`). Types never used are missing.
     */
    val ownFuelPrices: Flow<Map<FuelType, Long>> = dataStore.data.map { prefs ->
        FuelType.entries.mapNotNull { type -> prefs[keys.ownFuelPriceCents(type)]?.let { type to it } }.toMap()
    }.distinctUntilChanged()

    /** Remembers these prices, in one write; other fuel types keep theirs. */
    suspend fun rememberFuelPrices(prices: Map<FuelType, Long>) {
        if (prices.isEmpty()) return
        dataStore.edit { prefs -> prices.forEach { (type, cents) -> prefs[keys.ownFuelPriceCents(type)] = cents } }
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
        .map { it[keys.FUEL_PRICE_KEPT].orEmpty() }
        .distinctUntilChanged()

    suspend fun addKeptFuelPrice(key: String) {
        dataStore.edit { it[keys.FUEL_PRICE_KEPT] = it[keys.FUEL_PRICE_KEPT].orEmpty() + key }
    }

    /**
     * Set when a published price was applied automatically and the rider hasn't seen the notice
     * yet: the vehicle id, so the notice shows once. Cleared when the rider dismisses it.
     */
    val fuelPriceAutoNoticeVehicleId: Flow<Long?> = dataStore.data
        .map { it[keys.FUEL_PRICE_AUTO_NOTICE_VEHICLE_ID] }
        .distinctUntilChanged()

    suspend fun setFuelPriceAutoNoticeVehicleId(id: Long?) {
        dataStore.edit { it.setOrRemove(keys.FUEL_PRICE_AUTO_NOTICE_VEHICLE_ID, id) }
    }

    suspend fun setLastCheckInputs(inputs: LastCheckInputs) {
        dataStore.edit {
            it[keys.CHECK_PLATFORM] = inputs.platform.name
            it[keys.CHECK_FARE_MODE] = inputs.fareMode.name
            it[keys.CHECK_COMMISSION_TYPE] = inputs.commissionType.name
            it.setOrRemove(keys.CHECK_COMMISSION_HUNDREDTHS, inputs.commissionHundredths)
            it.setOrRemove(keys.CHECK_OTHER_COSTS_CENTS, inputs.otherCostsCents)
            it.setOrRemove(keys.CHECK_PICKUP_KM, inputs.pickupKm)
            it.setOrRemove(keys.CHECK_TRIP_KM, inputs.tripKm)
            it.setOrRemove(keys.CHECK_RETURN_KM, inputs.returnKm)
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDefaultVehicleId(id: Long) {
        dataStore.edit { it[keys.DEFAULT_VEHICLE_ID] = id }
    }

    /** Records the default vehicle and finishes setup in one atomic write. */
    suspend fun completeOnboarding(defaultVehicleId: Long) {
        dataStore.edit {
            it[keys.DEFAULT_VEHICLE_ID] = defaultVehicleId
            it[keys.ONBOARDING_COMPLETE] = true
        }
    }

    private fun <T : Any> MutablePreferences.setOrRemove(key: Preferences.Key<T>, value: T?) {
        if (value == null) remove(key) else set(key, value)
    }

    /** Settings that belong to the phone, shared by every space. */
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val OFFER_READER_PAUSED = booleanPreferencesKey("offer_reader_paused")
        val OFFER_SOUND = booleanPreferencesKey("offer_sound")

        // Published fuel prices (CLAUDE.md section 8a). The list is stored as separate keys, one
        // price per fuel type, so adding a fuel type later only adds a key.
        val FUEL_PRICE_EFFECTIVE_FROM = stringPreferencesKey("fuel_price_effective_from")
        val FUEL_PRICE_FETCHED_AT = longPreferencesKey("fuel_price_fetched_at")
        fun fuelPriceCents(type: FuelType) = longPreferencesKey("fuel_price_cents_${type.name.lowercase()}")
        val FUEL_PRICE_LAST_CHECK_AT = longPreferencesKey("fuel_price_last_check_at")

        // Account (CLAUDE.md section 14), one key per signed-in user.
        fun mobileNumber(uid: String) = stringPreferencesKey("account_mobile_$uid")
        fun gender(uid: String) = stringPreferencesKey("account_gender_$uid")
        fun birthDate(uid: String) = stringPreferencesKey("account_birth_date_$uid")

        // Set when the rider removes a detail, so cloud backup removes it too; cleared when set again.
        fun mobileRemoved(uid: String) = booleanPreferencesKey("account_mobile_removed_$uid")
        fun genderRemoved(uid: String) = booleanPreferencesKey("account_gender_removed_$uid")
        fun birthDateRemoved(uid: String) = booleanPreferencesKey("account_birth_date_removed_$uid")

        fun accountKeys(uid: String) = listOf(
            mobileNumber(uid), gender(uid), birthDate(uid), mobileRemoved(uid), genderRemoved(uid), birthDateRemoved(uid),
        )
    }

    /**
     * Settings that go with one space's records. Several hold Room ids, which mean nothing in
     * another space's database. [suffix] is empty for the first space, so its keys keep the names
     * they had before spaces existed.
     */
    private class SpaceKeys(private val suffix: String) {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete$suffix")
        val DEFAULT_VEHICLE_ID = longPreferencesKey("default_vehicle_id$suffix")

        // Last-used Check a Ride inputs. An absent key means the field was left empty.
        val CHECK_PLATFORM = stringPreferencesKey("check_platform$suffix")
        val CHECK_FARE_MODE = stringPreferencesKey("check_fare_mode$suffix")
        val CHECK_COMMISSION_TYPE = stringPreferencesKey("check_commission_type$suffix")
        val CHECK_COMMISSION_HUNDREDTHS = longPreferencesKey("check_commission_hundredths$suffix")
        val CHECK_OTHER_COSTS_CENTS = longPreferencesKey("check_other_costs_cents$suffix")
        val CHECK_PICKUP_KM = doublePreferencesKey("check_pickup_km$suffix")
        val CHECK_TRIP_KM = doublePreferencesKey("check_trip_km$suffix")
        val CHECK_RETURN_KM = doublePreferencesKey("check_return_km$suffix")

        val DISMISSED_EFFICIENCY_LOG_ID = longPreferencesKey("dismissed_efficiency_log_id$suffix")

        /** The rider skipped restoring and started fresh, so this space doesn't back up until they turn it on. */
        val BACKUP_PAUSED = booleanPreferencesKey("backup_paused$suffix")

        fun ownFuelPriceCents(type: FuelType) = longPreferencesKey("own_fuel_price_cents_${type.name.lowercase()}$suffix")
        val FUEL_PRICE_KEPT = stringSetPreferencesKey("fuel_price_kept$suffix")
        val FUEL_PRICE_AUTO_NOTICE_VEHICLE_ID = longPreferencesKey("fuel_price_auto_notice_vehicle_id$suffix")

        /** Every key of the space, for [clearRiderData]. */
        val all: List<Preferences.Key<*>> = listOf(
            ONBOARDING_COMPLETE, DEFAULT_VEHICLE_ID, CHECK_PLATFORM, CHECK_FARE_MODE, CHECK_COMMISSION_TYPE,
            CHECK_COMMISSION_HUNDREDTHS, CHECK_OTHER_COSTS_CENTS, CHECK_PICKUP_KM, CHECK_TRIP_KM, CHECK_RETURN_KM,
            DISMISSED_EFFICIENCY_LOG_ID, FUEL_PRICE_KEPT, FUEL_PRICE_AUTO_NOTICE_VEHICLE_ID, BACKUP_PAUSED,
        ) + FuelType.entries.map(::ownFuelPriceCents)
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
