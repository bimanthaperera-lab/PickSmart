package lk.picksmart.data.account

import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Firebase App Check (CLAUDE.md section 10): Firestore accepts only requests from the real app, so
 * a modified or repackaged APK can't use cloud backup. The provider comes from the build type
 * (`appCheckProviderFactory` in `src/release` and `src/debug`). It guards backup only; local use
 * never waits for it.
 *
 * Tokens are fetched when Firestore needs one, not refreshed in the background, so an idle phone
 * spends no Play Integrity calls.
 */
object AppCheck {
    /** Call once, before the first Firestore request. */
    fun install(app: FirebaseApp) {
        FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(appCheckProviderFactory(), false)
    }

    /** True when this copy of the app can get an App Check token, so the server could verify it. */
    suspend fun passes(): Boolean = try {
        withTimeout(TOKEN_TIMEOUT_MILLIS) { FirebaseAppCheck.getInstance().getAppCheckToken(false).await() }
        true
    } catch (e: TimeoutCancellationException) {
        false
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    private const val TOKEN_TIMEOUT_MILLIS = 10_000L
}
