package lk.picksmart.ui.account

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import lk.picksmart.PicksmartApplication
import lk.picksmart.R
import lk.picksmart.data.account.AccountProvider
import lk.picksmart.data.account.AuthResult
import lk.picksmart.data.preferences.UserPreferencesRepository
import lk.picksmart.ui.navigation.ForgotPasswordRoute

/** Where a sign-in form is: running a request, showing why one failed, or finished. */
data class AuthStatus(val busy: Boolean = false, @StringRes val message: Int? = null, val done: Boolean = false)

/** Shared by the forms: one request at a time, failures turned into a message, and Google sign-in. */
abstract class AuthFormViewModel(protected val account: AccountProvider) : ViewModel() {
    private val mutableStatus = MutableStateFlow(AuthStatus())
    val status: StateFlow<AuthStatus> = mutableStatus.asStateFlow()

    /** Shows field errors once the rider has tried to submit, not while they first type. */
    protected val submitted = MutableStateFlow(false)

    /** Runs after any successful sign-in, before the form reports it is done. */
    protected open suspend fun afterSignIn() = Unit

    protected fun run(action: suspend () -> AuthResult) {
        if (mutableStatus.value.busy) return
        mutableStatus.value = AuthStatus(busy = true)
        viewModelScope.launch {
            mutableStatus.value = when (val result = action()) {
                AuthResult.Success -> {
                    afterSignIn()
                    AuthStatus(done = true)
                }
                is AuthResult.Failure -> AuthStatus(message = result.error.message())
            }
        }
    }

    protected fun clearMessage() {
        mutableStatus.update { if (it.busy) it else it.copy(message = null) }
    }

    /** Google's picker is open: block the buttons until it returns. False if a request is running. */
    fun googleStarted(): Boolean {
        if (mutableStatus.value.busy) return false
        mutableStatus.value = AuthStatus(busy = true)
        return true
    }

    fun googleFinished(result: GoogleTokenResult) {
        mutableStatus.value = when (result) {
            GoogleTokenResult.Cancelled -> AuthStatus()
            GoogleTokenResult.NoAccount -> AuthStatus(message = R.string.auth_error_google_no_account)
            GoogleTokenResult.Failed -> AuthStatus(message = R.string.auth_error_google_failed)
            is GoogleTokenResult.Token -> AuthStatus()
        }
        if (result is GoogleTokenResult.Token) run { account.signInWithGoogle(result.idToken) }
    }
}

class LoginViewModel(account: AccountProvider) : AuthFormViewModel(account) {
    private val mutableForm = MutableStateFlow(LoginForm())
    val form: StateFlow<LoginForm> = mutableForm.asStateFlow()
    val showErrors: StateFlow<Boolean> = submitted.asStateFlow()

    fun update(form: LoginForm) {
        mutableForm.value = form
        clearMessage()
    }

    fun submit() {
        submitted.value = true
        val form = mutableForm.value
        if (form.errors().isNotEmpty()) return
        run { account.signIn(form.email.trim(), form.password) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { LoginViewModel((this[APPLICATION_KEY] as PicksmartApplication).container.account) }
        }
    }
}

class RegisterViewModel(
    account: AccountProvider,
    private val preferences: UserPreferencesRepository,
) : AuthFormViewModel(account) {
    private val mutableForm = MutableStateFlow(RegisterForm())
    val form: StateFlow<RegisterForm> = mutableForm.asStateFlow()
    val showErrors: StateFlow<Boolean> = submitted.asStateFlow()

    fun update(form: RegisterForm) {
        mutableForm.value = form
        clearMessage()
    }

    fun submit() {
        submitted.value = true
        val form = mutableForm.value
        if (form.errors().isNotEmpty()) return
        run { account.register(form.name.trim(), form.email.trim(), form.password) }
    }

    /** Keeps the mobile number the rider typed, for this account (also after "Continue with Google"). */
    override suspend fun afterSignIn() {
        val mobile = mutableForm.value.mobile
        if (mobileError(mobile) != null) return
        val uid = account.profile.first()?.uid ?: return
        cleanMobile(mobile)?.let { preferences.setMobileNumber(uid, it) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as PicksmartApplication).container
                RegisterViewModel(container.account, container.userPreferences)
            }
        }
    }
}

class ForgotPasswordViewModel(account: AccountProvider, savedStateHandle: SavedStateHandle) : AuthFormViewModel(account) {
    private val mutableEmail = MutableStateFlow(savedStateHandle.toRoute<ForgotPasswordRoute>().email)
    val email: StateFlow<String> = mutableEmail.asStateFlow()
    val showErrors: StateFlow<Boolean> = submitted.asStateFlow()

    fun update(email: String) {
        mutableEmail.value = email
        clearMessage()
    }

    fun submit() {
        submitted.value = true
        val email = mutableEmail.value
        if (emailError(email) != null) return
        run { account.sendPasswordReset(email.trim()) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                ForgotPasswordViewModel(
                    (this[APPLICATION_KEY] as PicksmartApplication).container.account,
                    createSavedStateHandle(),
                )
            }
        }
    }
}
