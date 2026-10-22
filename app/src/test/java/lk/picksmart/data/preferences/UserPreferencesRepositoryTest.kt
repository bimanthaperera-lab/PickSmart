package lk.picksmart.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
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
}
