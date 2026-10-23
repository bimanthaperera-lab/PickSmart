package lk.picksmart.domain.model

/** How the rider signs in (CLAUDE.md section 14). */
enum class SignInMethod { EMAIL, GOOGLE }

/**
 * The signed-in rider, as the sign-in provider knows them. The optional mobile number isn't part
 * of it: the rider types that, and it is kept on the device.
 */
data class UserProfile(
    val uid: String,
    val name: String?,
    val email: String?,
    val emailVerified: Boolean,
    val photoUrl: String?,
    val method: SignInMethod,
)
