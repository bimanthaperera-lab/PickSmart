package lk.picksmart

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import lk.picksmart.data.preferences.AppLanguageStore
import lk.picksmart.domain.model.ThemeMode
import lk.picksmart.domain.usecase.OfferToSave
import lk.picksmart.ui.account.MoveRecordsDialog
import lk.picksmart.ui.navigation.PicksmartApp
import lk.picksmart.ui.notifications.OfferSaveNotification
import lk.picksmart.ui.onboarding.OnboardingScreen
import lk.picksmart.ui.splash.SplashScreen
import lk.picksmart.ui.theme.PicksmartTheme

class MainActivity : ComponentActivity() {
    /** An offer from a tap on "Save this trip?", waiting for the app to open Add trip with it. */
    private var offerToSave by mutableStateOf<OfferToSave?>(null)

    // The rider's language below Android 13 (AppLanguageStore); Android applies it itself on 13+.
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLanguageStore.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Android 12+ fades its splash out over the app, dimming the logo for a moment. The app's
        // first frame shows the same pin in the same place, so drop the system one at once.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { it.remove() }
        }
        val container = (application as PicksmartApplication).container
        // App start: check the published fuel price in the background (CLAUDE.md section 8a).
        if (savedInstanceState == null) {
            container.refreshFuelPrices()
            offerToSave = OfferSaveNotification.offerFrom(intent)
        }
        // Another account signed in, or the rider signed out, so other records are now the ones on
        // screen (DataSpaces.kt).
        // Start again, so every screen and ViewModel is built on them, never on the last rider's.
        val shownSpace = container.space.value.id
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.space.first { it.id != shownSpace }
                startActivity(
                    Intent(this@MainActivity, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                )
            }
        }

        setContent {
            // The app waits for the stored theme and setup flag, so a rider never sees a flash of
            // the wrong theme or of onboarding. Once loaded they stay loaded, so a recreated
            // screen has them at once.
            val startup by container.startupSettings.collectAsStateWithLifecycle()
            val themeMode = startup?.themeMode
            val onboardingComplete = startup?.onboardingComplete

            // The splash is drawn on the very first frame, before those load, so the logo on the
            // system splash never gives way to a blank screen. It stays for SplashMillis and until
            // the settings are ready, then fades into the app. Once per launch: not on rotation.
            var splashDone by rememberSaveable { mutableStateOf(false) }
            if (!splashDone) {
                LaunchedEffect(Unit) {
                    delay(SplashMillis)
                    snapshotFlow { startup != null }.first { it }
                    splashDone = true
                }
            }
            // Before the stored theme loads, the phone's setting decides. On Android 12+ that is
            // already the app's own theme (setApplicationNightMode below). Kept for the whole
            // splash so it never changes colour halfway.
            val phoneDark = isSystemInDarkTheme()
            val splashDark = remember { phoneDark }

            // The splash fades out, growing slightly, while the app fades in underneath it. A new
            // rider's welcome screen has the logo exactly where the splash leaves it, so there the
            // switch is instant and only the welcome's buttons fade in.
            AnimatedContent(
                targetState = !splashDone,
                transitionSpec = {
                    if (onboardingComplete == false) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        fadeIn(tween(SplashExitMillis)) togetherWith
                            (fadeOut(tween(SplashExitMillis)) + scaleOut(tween(SplashExitMillis), targetScale = 1.04f))
                    }
                },
                label = "splash",
            ) { splash ->
                val mode = themeMode
                val setupDone = onboardingComplete
                when {
                    splash -> PicksmartTheme(darkTheme = splashDark) { SplashScreen() }
                    mode != null && setupDone != null -> AppContent(mode, setupDone)
                }
            }
        }
    }

    @Composable
    private fun AppContent(mode: ThemeMode, onboardingComplete: Boolean) {
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
            if (onboardingComplete) PicksmartApp(offerToSave, onOfferOpened = { offerToSave = null }) else OnboardingScreen()

            // A new account was just created on a phone with records made without one.
            val container = (application as PicksmartApplication).container
            val askingToMoveRecords by container.askingToMoveRecords.collectAsStateWithLifecycle()
            if (askingToMoveRecords) MoveRecordsDialog(onAnswer = container::answerMoveRecords)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        OfferSaveNotification.offerFrom(intent)?.let { offerToSave = it }
    }

    private companion object {
        /** Kept short: riders often open the app to check an offer quickly. */
        const val SplashMillis = 900L
        const val SplashExitMillis = 300

        // Scrims for three-button navigation on older Android versions, where the bar can't be fully transparent.
        val LightScrim = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
        val DarkScrim = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
    }
}
