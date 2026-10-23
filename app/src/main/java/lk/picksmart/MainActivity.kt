package lk.picksmart

import android.app.UiModeManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import lk.picksmart.domain.model.ThemeMode
import lk.picksmart.ui.navigation.PicksmartApp
import lk.picksmart.ui.onboarding.OnboardingScreen
import lk.picksmart.ui.splash.SplashScreen
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
            // The brand splash shows once per launch (not again on rotation) for SplashMillis,
            // counted from when it appears. A new rider skips it: the welcome screen shows the logo.
            var splashDone by rememberSaveable { mutableStateOf(false) }
            val mode = themeMode
            val setupDone = onboardingComplete
            if (mode != null && setupDone != null) {
                val showSplash = setupDone && !splashDone
                if (showSplash) {
                    LaunchedEffect(Unit) {
                        delay(SplashMillis)
                        splashDone = true
                    }
                }
                AppContent(mode, setupDone, showSplash)
            }
        }
    }

    @Composable
    private fun AppContent(mode: ThemeMode, onboardingComplete: Boolean, showSplash: Boolean) {
        val darkTheme = when (mode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }

        // Android 12+ draws its own splash before the app starts, from the window background.
        // Telling the system the app's theme makes that match on the next launch, even when the
        // rider's choice differs from the phone's. The activity handles the uiMode change itself.
        LaunchedEffect(mode) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(UiModeManager::class.java).setApplicationNightMode(
                    when (mode) {
                        ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
                        ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
                        ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
                    },
                )
            }
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
            // The splash fades out, growing slightly, while the app fades in underneath it.
            AnimatedContent(
                targetState = showSplash,
                transitionSpec = {
                    fadeIn(tween(SplashExitMillis)) togetherWith
                        (fadeOut(tween(SplashExitMillis)) + scaleOut(tween(SplashExitMillis), targetScale = 1.04f))
                },
                label = "splash",
            ) { splash ->
                when {
                    splash -> SplashScreen()
                    onboardingComplete -> PicksmartApp()
                    else -> OnboardingScreen()
                }
            }
        }
    }

    private companion object {
        /** Kept short: riders often open the app to check an offer quickly. */
        const val SplashMillis = 800L
        const val SplashExitMillis = 300

        // Scrims for three-button navigation on older Android versions, where the bar can't be fully transparent.
        val LightScrim = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
        val DarkScrim = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
    }
}
