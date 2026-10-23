package lk.picksmart.ui.account

import androidx.annotation.StringRes
import lk.picksmart.R
import lk.picksmart.data.account.AuthError

/** Firebase accepts 6; 8 is the floor here for a safer password. */
const val MIN_PASSWORD_LENGTH = 8

enum class AuthField { NAME, EMAIL, MOBILE, PASSWORD, CONFIRM_PASSWORD }

enum class AuthFieldError(@StringRes val message: Int) {
    NAME_REQUIRED(R.string.auth_error_name_required),
    EMAIL_REQUIRED(R.string.auth_error_email_required),
    EMAIL_INVALID(R.string.auth_error_email_invalid),
    MOBILE_INVALID(R.string.auth_error_mobile_invalid),
    PASSWORD_REQUIRED(R.string.auth_error_password_required),
    PASSWORD_TOO_SHORT(R.string.auth_error_password_short),
    PASSWORDS_DIFFER(R.string.auth_error_passwords_differ),
}

private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
private val MOBILE_CHARS = Regex("^\\+?[0-9 ()-]+$")

fun emailError(email: String): AuthFieldError? = when {
    email.isBlank() -> AuthFieldError.EMAIL_REQUIRED
    !EMAIL.matches(email.trim()) -> AuthFieldError.EMAIL_INVALID
    else -> null
}

/** The mobile number is optional; when given it needs 9 to 15 digits, e.g. "077 123 4567" or "+94 77 123 4567". */
fun mobileError(mobile: String): AuthFieldError? {
    if (mobile.isBlank()) return null
    val digits = mobile.count(Char::isDigit)
    return if (MOBILE_CHARS.matches(mobile.trim()) && digits in 9..15) null else AuthFieldError.MOBILE_INVALID
}

/** The number as saved: trimmed with single spaces, or null when left empty. */
fun cleanMobile(mobile: String): String? = mobile.trim().replace(Regex("\\s+"), " ").ifEmpty { null }

data class LoginForm(val email: String = "", val password: String = "") {
    fun errors(): Map<AuthField, AuthFieldError> = buildMap {
        emailError(email)?.let { put(AuthField.EMAIL, it) }
        if (password.isEmpty()) put(AuthField.PASSWORD, AuthFieldError.PASSWORD_REQUIRED)
    }
}

data class RegisterForm(
    val name: String = "",
    val email: String = "",
    val mobile: String = "",
    val password: String = "",
    val confirmPassword: String = "",
) {
    fun errors(): Map<AuthField, AuthFieldError> = buildMap {
        if (name.isBlank()) put(AuthField.NAME, AuthFieldError.NAME_REQUIRED)
        emailError(email)?.let { put(AuthField.EMAIL, it) }
        mobileError(mobile)?.let { put(AuthField.MOBILE, it) }
        when {
            password.isEmpty() -> put(AuthField.PASSWORD, AuthFieldError.PASSWORD_REQUIRED)
            password.length < MIN_PASSWORD_LENGTH -> put(AuthField.PASSWORD, AuthFieldError.PASSWORD_TOO_SHORT)
            confirmPassword != password -> put(AuthField.CONFIRM_PASSWORD, AuthFieldError.PASSWORDS_DIFFER)
        }
    }
}

/** What to tell the rider when an account action fails. */
@StringRes
fun AuthError.message(): Int = when (this) {
    AuthError.NO_INTERNET -> R.string.auth_error_no_internet
    AuthError.WRONG_CREDENTIALS -> R.string.auth_error_wrong_credentials
    AuthError.INVALID_EMAIL -> R.string.auth_error_email_invalid
    AuthError.EMAIL_IN_USE -> R.string.auth_error_email_in_use
    AuthError.WEAK_PASSWORD -> R.string.auth_error_weak_password
    AuthError.OTHER_SIGN_IN_METHOD -> R.string.auth_error_other_method
    AuthError.TOO_MANY_ATTEMPTS -> R.string.auth_error_too_many
    AuthError.ACCOUNT_DISABLED -> R.string.auth_error_disabled
    AuthError.RECENT_SIGN_IN_REQUIRED -> R.string.auth_error_recent_sign_in
    AuthError.NOT_AVAILABLE -> R.string.account_unavailable
    AuthError.UNKNOWN -> R.string.auth_error_unknown
}
