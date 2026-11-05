package lk.picksmart.data.account

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import lk.picksmart.domain.model.UserProfile

/**
 * The optional account (CLAUDE.md section 14). The app never needs it: everything works signed
 * out, and a build without Firebase gets [NoOpAccountProvider].
 */
interface AccountProvider {
    /** False when the app was built without Firebase; the Account tab then offers no sign-in. */
    val isAvailable: Boolean

    /** The signed-in rider, or null. The session is kept on the device, so this works offline. */
    val profile: Flow<UserProfile?>

    suspend fun signIn(email: String, password: String): AuthResult

    /** Creates the account, sets its name and sends the verification email. */
    suspend fun register(name: String, email: String, password: String): AuthResult

    /** Signs in with the ID token Google's account picker returned. */
    suspend fun signInWithGoogle(idToken: String): AuthResult

    suspend fun sendPasswordReset(email: String): AuthResult

    suspend fun sendEmailVerification(): AuthResult

    /** Reloads the account from the server, e.g. to see that the email is now verified. */
    suspend fun refresh(): AuthResult

    suspend fun updateName(name: String): AuthResult

    /** Signs out. Never touches the rider's records on the device. */
    suspend fun signOut()

    /**
     * Proves again that the signed-in rider is at the phone. Firebase deletes an account only
     * after a recent sign-in, so [lk.picksmart.domain.usecase.DeleteAccount] asks for this first.
     */
    suspend fun confirmIdentity(proof: IdentityProof): AuthResult

    /** Deletes the sign-in account only; [lk.picksmart.domain.usecase.DeleteAccount] does the rest. */
    suspend fun deleteAccount(): AuthResult
}

/** What the rider gives to confirm it's them: their password, or Google's account picker. */
sealed interface IdentityProof {
    data class Password(val password: String) : IdentityProof

    data class Google(val idToken: String) : IdentityProof
}

/** Whether a rider is signed in; always false in a build without sign-in. Works offline. */
val AccountProvider.signedIn: Flow<Boolean>
    get() = if (isAvailable) profile.map { it != null }.distinctUntilChanged() else flowOf(false)

sealed interface AuthResult {
    data object Success : AuthResult
    data class Failure(val error: AuthError) : AuthResult
}

/** Why an account action failed, in terms the rider can act on. */
enum class AuthError {
    NO_INTERNET,
    WRONG_CREDENTIALS,
    INVALID_EMAIL,
    EMAIL_IN_USE,
    WEAK_PASSWORD,
    OTHER_SIGN_IN_METHOD,
    TOO_MANY_ATTEMPTS,
    ACCOUNT_DISABLED,
    RECENT_SIGN_IN_REQUIRED,

    /** Google's picker returned another account than the one signed in. */
    DIFFERENT_ACCOUNT,
    NOT_AVAILABLE,
    UNKNOWN,
}

/** Used when the app is built without `google-services.json`: always signed out. */
object NoOpAccountProvider : AccountProvider {
    private val unavailable = AuthResult.Failure(AuthError.NOT_AVAILABLE)

    override val isAvailable = false
    override val profile: Flow<UserProfile?> = flowOf(null)
    override suspend fun signIn(email: String, password: String) = unavailable
    override suspend fun register(name: String, email: String, password: String) = unavailable
    override suspend fun signInWithGoogle(idToken: String) = unavailable
    override suspend fun sendPasswordReset(email: String) = unavailable
    override suspend fun sendEmailVerification() = unavailable
    override suspend fun refresh() = unavailable
    override suspend fun updateName(name: String) = unavailable
    override suspend fun signOut() = Unit
    override suspend fun confirmIdentity(proof: IdentityProof) = unavailable
    override suspend fun deleteAccount() = unavailable
}
