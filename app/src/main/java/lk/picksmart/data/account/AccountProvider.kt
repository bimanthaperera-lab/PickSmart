package lk.picksmart.data.account

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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

    /** Deletes the sign-in account. The rider's records on the device stay. */
    suspend fun deleteAccount(): AuthResult
}

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
    override suspend fun deleteAccount() = unavailable
}
