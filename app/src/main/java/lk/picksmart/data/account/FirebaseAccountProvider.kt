package lk.picksmart.data.account

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import lk.picksmart.domain.model.SignInMethod
import lk.picksmart.domain.model.UserProfile

/** Firebase Authentication with email/password and Google (CLAUDE.md section 14). */
class FirebaseAccountProvider(
    private val auth: FirebaseAuth,
    private val context: Context,
) : AccountProvider {

    override val isAvailable = true

    // Firebase reports sign-in and sign-out, but not a changed name or a newly verified email,
    // so those bump this after they land.
    private val changes = MutableStateFlow(0)

    private val signedIn: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override val profile: Flow<UserProfile?> = combine(signedIn, changes) { user, _ -> user?.toProfile() }
        .distinctUntilChanged()

    override suspend fun signIn(email: String, password: String) = attempt {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    override suspend fun register(name: String, email: String, password: String) = attempt {
        val user = checkNotNull(auth.createUserWithEmailAndPassword(email, password).await().user)
        user.updateProfile(userProfileChangeRequest { displayName = name }).await()
        // The account exists at this point; a verification email that fails to send can be resent
        // from the Profile screen, so it doesn't fail the registration.
        runCatching { user.sendEmailVerification().await() }
        changes.value++
    }

    override suspend fun signInWithGoogle(idToken: String) = attempt {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
    }

    override suspend fun sendPasswordReset(email: String) = attempt {
        auth.sendPasswordResetEmail(email).await()
    }

    override suspend fun sendEmailVerification() = attempt {
        checkNotNull(auth.currentUser).sendEmailVerification().await()
    }

    override suspend fun refresh() = attempt {
        auth.currentUser?.reload()?.await()
        changes.value++
    }

    override suspend fun updateName(name: String) = attempt {
        checkNotNull(auth.currentUser).updateProfile(userProfileChangeRequest { displayName = name }).await()
        changes.value++
    }

    override suspend fun signOut() {
        auth.signOut()
        // Forget the Google account chosen last time, so the picker shows again next sign-in.
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    override suspend fun confirmIdentity(proof: IdentityProof) = attempt {
        val user = checkNotNull(auth.currentUser)
        val credential = when (proof) {
            is IdentityProof.Password -> EmailAuthProvider.getCredential(checkNotNull(user.email), proof.password)
            is IdentityProof.Google -> GoogleAuthProvider.getCredential(proof.idToken, null)
        }
        user.reauthenticate(credential).await()
    }

    override suspend fun deleteAccount() = attempt {
        checkNotNull(auth.currentUser).delete().await()
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    private suspend fun attempt(block: suspend () -> Unit): AuthResult = try {
        block()
        AuthResult.Success
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AuthResult.Failure(e.toAuthError())
    }
}

private fun FirebaseUser.toProfile() = UserProfile(
    uid = uid,
    name = displayName?.takeIf { it.isNotBlank() },
    email = email?.takeIf { it.isNotBlank() },
    emailVerified = isEmailVerified,
    photoUrl = photoUrl?.toString(),
    method = if (providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }) SignInMethod.GOOGLE else SignInMethod.EMAIL,
    // Firebase sets both times when it creates the account; a later sign-in moves only the last.
    isNewAccount = metadata?.let { abs(it.lastSignInTimestamp - it.creationTimestamp) < NEW_ACCOUNT_MILLIS } ?: false,
)

private const val NEW_ACCOUNT_MILLIS = 60_000L

private fun Exception.toAuthError(): AuthError = when (this) {
    is FirebaseNetworkException -> AuthError.NO_INTERNET
    is FirebaseTooManyRequestsException -> AuthError.TOO_MANY_ATTEMPTS
    is FirebaseAuthWeakPasswordException -> AuthError.WEAK_PASSWORD
    is FirebaseAuthRecentLoginRequiredException -> AuthError.RECENT_SIGN_IN_REQUIRED
    is FirebaseAuthUserCollisionException ->
        if (errorCode == "ERROR_EMAIL_ALREADY_IN_USE") AuthError.EMAIL_IN_USE else AuthError.OTHER_SIGN_IN_METHOD
    is FirebaseAuthInvalidCredentialsException ->
        if (errorCode == "ERROR_INVALID_EMAIL") AuthError.INVALID_EMAIL else AuthError.WRONG_CREDENTIALS
    is FirebaseAuthInvalidUserException -> when (errorCode) {
        "ERROR_USER_DISABLED" -> AuthError.ACCOUNT_DISABLED
        "ERROR_USER_MISMATCH" -> AuthError.DIFFERENT_ACCOUNT
        else -> AuthError.WRONG_CREDENTIALS
    }
    else -> AuthError.UNKNOWN
}
