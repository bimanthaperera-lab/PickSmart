package lk.picksmart

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lk.picksmart.domain.model.ThemeMode
import lk.picksmart.ui.navigation.PicksmartApp
import lk.picksmart.ui.onboarding.OnboardingScreen
import lk.picksmart.ui.theme.PicksmartTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as PicksmartApplication).container
        val preferences = container.userPreferences
        // App start: check the published fuel price in the background (CLAUDE.md section 8a).
        if (savedInstanceState == null) container.refreshFuelPrices()

        setContent {
            // Wait for the stored theme and setup flag before drawing, so a rider never sees a
            // flash of the wrong theme or of onboarding. The window background shows meanwhile.
            val themeMode by preferences.themeMode.collectAsStateWithLifecycle(initialValue = null)
            val onboardingComplete by preferences.onboardingComplete.collectAsStateWithLifecycle(initialValue = null)
            val mode = themeMode
            val setupDone = onboardingComplete
            if (mode != null && setupDone != null) AppContent(mode, setupDone)
        }
    }

    @Composable
    private fun AppContent(mode: ThemeMode, onboardingComplete: Boolean) {
        val darkTheme = when (mode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }

        // Keep system bar icons readable when the app theme differs from the phone's.
        DisposableEffect(darkTheme) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                navigationBarStyle = SystemBarStyle.auto(LightScrim, DarkScrim) { darkTheme },
            )
            onDispose {}
        }

        PicksmartTheme(darkTheme = darkTheme) {
            // Setup runs only while the flag is false; finishing it flips the flag and this
            // switches to the app. An update never resets the flag (CLAUDE.md N7).
            if (onboardingComplete) PicksmartApp() else OnboardingScreen()
        }
    }

    private companion object {
        // Scrims for three-button navigation on older Android versions, where the bar can't be fully transparent.
        val LightScrim = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
        val DarkScrim = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
    }
}
