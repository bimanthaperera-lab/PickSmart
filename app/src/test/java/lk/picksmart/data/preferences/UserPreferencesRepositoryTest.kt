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
import lk.picksmart.domain.model.LastCheckInputs
import lk.picksmart.domain.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

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
            it[stringPreferencesKey("check_fare_mode")] = "SOMETHING_NEW"
            it[stringPreferencesKey("check_commission_type")] = "PER_KM"
            it[longPreferencesKey("check_commission_hundredths")] = -5
            it[doublePreferencesKey("check_pickup_km")] = Double.NaN
            it[doublePreferencesKey("check_trip_km")] = -1.0
        }
        assertEquals(LastCheckInputs(), UserPreferencesRepository(dataStore).lastCheckInputs.first())
    }
}
