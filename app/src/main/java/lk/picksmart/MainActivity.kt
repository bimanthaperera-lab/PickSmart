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
import lk.picksmart.ui.theme.PicksmartTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val preferences = (application as PicksmartApplication).container.userPreferences

        setContent {
            // Wait for the stored theme before drawing, so a rider who chose Dark never sees a
            // flash of the light theme. The window background shows for that moment.
            val themeMode by preferences.themeMode.collectAsStateWithLifecycle(initialValue = null)
            themeMode?.let { AppContent(it) }
        }
    }

    @Composable
    private fun AppContent(mode: ThemeMode) {
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
            PicksmartApp()
        }
    }

    private companion object {
        // Scrims for three-button navigation on older Android versions, where the bar can't be fully transparent.
        val LightScrim = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
        val DarkScrim = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
    }
}
