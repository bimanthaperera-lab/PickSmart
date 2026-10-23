package lk.picksmart.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import lk.picksmart.domain.model.CommissionType
import lk.picksmart.domain.model.FareMode
import lk.picksmart.domain.model.FuelPriceList
import lk.picksmart.domain.model.FuelType
import lk.picksmart.domain.model.LastCheckInputs
import lk.picksmart.domain.model.Platform
import lk.picksmart.domain.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

class UserPreferencesRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.store(file: File = File(tmp.root, "prefs.preferences_pb")) =
        PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { file })

    @Test
    fun `a fresh install starts with setup not done and no vehicle`() = runTest {
        val prefs = UserPreferencesRepository(store())
        assertFalse(prefs.onboardingComplete.first())
        assertNull(prefs.defaultVehicleId.first())
        assertEquals(ThemeMode.SYSTEM, prefs.themeMode.first())
    }

    @Test
    fun `a mobile number is kept per account and removed when cleared`() = runTest {
        val prefs = UserPreferencesRepository(store())
        prefs.setMobileNumber("a", "077 123 4567")
        prefs.setMobileNumber("b", "071 000 0000")
        assertEquals("077 123 4567", prefs.mobileNumber("a").first())
        assertEquals("071 000 0000", prefs.mobileNumber("b").first())
        prefs.setMobileNumber("a", " ")
        assertNull(prefs.mobileNumber("a").first())
        assertEquals("071 000 0000", prefs.mobileNumber("b").first())
    }

    @Test
    fun `completing setup saves the default vehicle and the flag together`() = runTest {
        val prefs = UserPreferencesRepository(store())
        prefs.completeOnboarding(defaultVehicleId = 42)
        assertTrue(prefs.onboardingComplete.first())
        assertEquals(42L, prefs.defaultVehicleId.first())
    }

    @Test
    fun `settings survive reopening the file, as after an app update`() {
        val file = File(tmp.root, "update.preferences_pb")
        runTest {
            UserPreferencesRepository(store(file)).apply {
                completeOnboarding(defaultVehicleId = 1)
                setThemeMode(ThemeMode.DARK)
            }
        }
        // The first store closed with its test scope; open the same file fresh.
        runTest {
            val prefs = UserPreferencesRepository(store(file))
            assertTrue(prefs.onboardingComplete.first())
            assertEquals(1L, prefs.defaultVehicleId.first())
            assertEquals(ThemeMode.DARK, prefs.themeMode.first())
        }
    }

    @Test
    fun `an unknown theme value falls back to system`() = runTest {
        val dataStore = store()
        dataStore.edit { it[stringPreferencesKey("theme_mode")] = "SEPIA" }
        assertEquals(ThemeMode.SYSTEM, UserPreferencesRepository(dataStore).themeMode.first())
    }

    @Test
    fun `check a ride starts empty in gross mode before the first check`() = runTest {
        assertEquals(LastCheckInputs(), UserPreferencesRepository(store()).lastCheckInputs.first())
    }

    @Test
    fun `last check inputs round trip, and clearing a field removes it`() = runTest {
        val prefs = UserPreferencesRepository(store())
        val full = LastCheckInputs(
            platform = Platform.OWN_DELIVERY,
            fareMode = FareMode.PAYOUT_AFTER_COMMISSION,
            commissionType = CommissionType.AMOUNT,
            commissionHundredths = 20_000,
            otherCostsCents = 5_000,
            pickupKm = 2.5,
            tripKm = 8.0,
            returnKm = 1.2,
        )
        prefs.setLastCheckInputs(full)
        assertEquals(full, prefs.lastCheckInputs.first())

        val cleared = full.copy(commissionHundredths = null, returnKm = null)
        prefs.setLastCheckInputs(cleared)
        assertEquals(cleared, prefs.lastCheckInputs.first())
    }

    @Test
    fun `unknown or invalid stored check values fall back to defaults`() = runTest {
        val dataStore = store()
        dataStore.edit {
            it[stringPreferencesKey("check_platform")] = "BOLT"
            it[stringPreferencesKey("check_fare_mode")] = "SOMETHING_NEW"
            it[stringPreferencesKey("check_commission_type")] = "PER_KM"
            it[longPreferencesKey("check_commission_hundredths")] = -5
            it[doublePreferencesKey("check_pickup_km")] = Double.NaN
            it[doublePreferencesKey("check_trip_km")] = -1.0
        }
        assertEquals(LastCheckInputs(), UserPreferencesRepository(dataStore).lastCheckInputs.first())
    }

    @Test
    fun `a dismissed km per litre suggestion is remembered`() = runTest {
        val prefs = UserPreferencesRepository(store())
        assertNull(prefs.dismissedEfficiencyLogId.first())
        prefs.setDismissedEfficiencyLogId(12)
        assertEquals(12L, prefs.dismissedEfficiencyLogId.first())
    }

    private val priceList = FuelPriceList(
        effectiveFrom = LocalDate.of(2026, 10, 1),
        fetchedAtMillis = 1_760_000_000_000,
        pricesCentsPerLitre = mapOf(
            FuelType.PETROL_92 to 41_400,
            FuelType.PETROL_95 to 47_500,
            FuelType.AUTO_DIESEL to 39_200,
            FuelType.SUPER_DIESEL to 52_800,
        ),
    )

    @Test
    fun `the last valid fuel price list is saved and read back`() = runTest {
        val prefs = UserPreferencesRepository(store())
        assertNull(prefs.fuelPriceList.first())
        prefs.setFuelPriceList(priceList)
        assertEquals(priceList, prefs.fuelPriceList.first())
    }

    @Test
    fun `a fuel price list with a part missing reads as no list`() = runTest {
        val dataStore = store()
        val prefs = UserPreferencesRepository(dataStore)
        prefs.setFuelPriceList(priceList)
        dataStore.edit { it.remove(longPreferencesKey("fuel_price_cents_super_diesel")) }
        assertNull(prefs.fuelPriceList.first())
        prefs.setFuelPriceList(priceList)
        dataStore.edit { it[stringPreferencesKey("fuel_price_effective_from")] = "not a date" }
        assertNull(prefs.fuelPriceList.first())
    }

    @Test
    fun `kept prices and the auto-update notice are remembered`() = runTest {
        val prefs = UserPreferencesRepository(store())
        prefs.addKeptFuelPrice("1@2026-10-01")
        prefs.addKeptFuelPrice("1@2026-11-01")
        assertEquals(setOf("1@2026-10-01", "1@2026-11-01"), prefs.keptFuelPrices.first())
        prefs.setFuelPriceAutoNoticeVehicleId(1)
        assertEquals(1L, prefs.fuelPriceAutoNoticeVehicleId.first())
        prefs.setFuelPriceAutoNoticeVehicleId(null)
        assertNull(prefs.fuelPriceAutoNoticeVehicleId.first())
    }
}
