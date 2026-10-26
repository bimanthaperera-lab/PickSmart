package lk.picksmart.ui.account

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import lk.picksmart.R
import lk.picksmart.data.account.AccountProvider
import lk.picksmart.data.account.AuthError
import lk.picksmart.data.account.AuthResult
import lk.picksmart.data.account.IdentityProof
import lk.picksmart.data.preferences.UserPreferencesRepository
import lk.picksmart.domain.model.SignInMethod
import lk.picksmart.domain.model.UserProfile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.preferences() = UserPreferencesRepository(
        PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(tmp.root, "p.preferences_pb") }),
    )

    @Test
    fun `registering keeps the typed mobile number for the new account`() = runTest {
        val account = FakeAccount()
        val prefs = preferences()
        val viewModel = RegisterViewModel(account, prefs)
        viewModel.update(RegisterForm("Kasun", "kasun@mail.lk", " 077 123  4567", "longenough", "longenough"))
        viewModel.submit()

        // Saving the number goes through DataStore, which finishes off the test thread.
        assertTrue(viewModel.status.first { !it.busy }.done)
        assertEquals("kasun@mail.lk", account.registeredEmail)
        assertEquals("077 123 4567", prefs.mobileNumber(FakeAccount.UID).first())
    }

    @Test
    fun `an invalid form sends nothing and shows its errors`() = runTest {
        val account = FakeAccount()
        val viewModel = LoginViewModel(account)
        viewModel.submit()

        assertTrue(viewModel.showErrors.value)
        assertEquals(0, account.calls)
        assertFalse(viewModel.status.value.done)
    }

    @Test
    fun `a failed sign-in shows a plain message and lets the rider try again`() = runTest {
        val account = FakeAccount(result = AuthResult.Failure(AuthError.WRONG_CREDENTIALS))
        val viewModel = LoginViewModel(account)
        viewModel.update(LoginForm("rider@mail.lk", "wrong"))
        viewModel.submit()

        assertEquals(AuthStatus(message = R.string.auth_error_wrong_credentials), viewModel.status.value)
        viewModel.update(LoginForm("rider@mail.lk", "wrong2"))
        assertNull(viewModel.status.value.message)
    }

    @Test
    fun `closing Google's picker is not an error`() = runTest {
        val viewModel = LoginViewModel(FakeAccount())
        assertTrue(viewModel.googleStarted())
        assertFalse(viewModel.googleStarted())
        viewModel.googleFinished(GoogleTokenResult.Cancelled)
        assertEquals(AuthStatus(), viewModel.status.value)
    }

    @Test
    fun `a Google token signs in with Firebase`() = runTest {
        val account = FakeAccount()
        val viewModel = LoginViewModel(account)
        viewModel.googleStarted()
        viewModel.googleFinished(GoogleTokenResult.Token("token"))
        assertEquals("token", account.googleToken)
        assertTrue(viewModel.status.value.done)
    }

    @Test
    fun `no Google account on the phone says so`() = runTest {
        val viewModel = LoginViewModel(FakeAccount())
        viewModel.googleStarted()
        viewModel.googleFinished(GoogleTokenResult.NoAccount)
        assertEquals(R.string.auth_error_google_no_account, viewModel.status.value.message)
    }
}

/** Signs in whoever asks, as one fixed user, unless told to fail. */
private class FakeAccount(private val result: AuthResult = AuthResult.Success) : AccountProvider {
    private val user = MutableStateFlow<UserProfile?>(null)
    var calls = 0
    var registeredEmail: String? = null
    var googleToken: String? = null

    override val isAvailable = true
    override val profile = user

    private fun signInAs(email: String?, method: SignInMethod): AuthResult {
        calls++
        if (result == AuthResult.Success) user.value = UserProfile(UID, null, email, false, null, method)
        return result
    }

    override suspend fun signIn(email: String, password: String) = signInAs(email, SignInMethod.EMAIL)
    override suspend fun register(name: String, email: String, password: String): AuthResult {
        registeredEmail = email
        return signInAs(email, SignInMethod.EMAIL)
    }
    override suspend fun signInWithGoogle(idToken: String): AuthResult {
        googleToken = idToken
        return signInAs("g@mail.lk", SignInMethod.GOOGLE)
    }
    override suspend fun sendPasswordReset(email: String) = result
    override suspend fun sendEmailVerification() = result
    override suspend fun refresh() = result
    override suspend fun updateName(name: String) = result
    override suspend fun signOut() {
        user.value = null
    }
    override suspend fun confirmIdentity(proof: IdentityProof) = result
    override suspend fun deleteAccount() = result

    companion object {
        const val UID = "user1"
    }
}
