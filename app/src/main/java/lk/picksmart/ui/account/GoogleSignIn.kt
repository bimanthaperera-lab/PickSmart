package lk.picksmart.ui.account

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import lk.picksmart.BuildConfig

/** What Google's account picker gave back. */
sealed interface GoogleTokenResult {
    data class Token(val idToken: String) : GoogleTokenResult

    /** The rider closed the picker: nothing to report. */
    data object Cancelled : GoogleTokenResult

    /** No Google account on the phone. */
    data object NoAccount : GoogleTokenResult

    data object Failed : GoogleTokenResult
}

/**
 * Shows Google's account picker ("Continue with Google") and returns the chosen account's ID
 * token for Firebase. [context] must be the activity, since the picker is shown over it.
 */
suspend fun requestGoogleIdToken(context: Context): GoogleTokenResult {
    val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
    if (clientId.isBlank()) return GoogleTokenResult.Failed
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
        .build()
    return try {
        val credential = CredentialManager.create(context).getCredential(context, request).credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            GoogleTokenResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
        } else {
            GoogleTokenResult.Failed
        }
    } catch (e: GetCredentialCancellationException) {
        GoogleTokenResult.Cancelled
    } catch (e: NoCredentialException) {
        GoogleTokenResult.NoAccount
    } catch (e: GetCredentialException) {
        GoogleTokenResult.Failed
    } catch (e: GoogleIdTokenParsingException) {
        GoogleTokenResult.Failed
    }
}
