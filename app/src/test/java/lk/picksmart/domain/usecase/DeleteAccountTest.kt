package lk.picksmart.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import lk.picksmart.data.account.AccountProvider
import lk.picksmart.data.account.AuthError
import lk.picksmart.data.account.AuthResult
import lk.picksmart.data.account.IdentityProof
import lk.picksmart.data.sync.SyncProblem
import lk.picksmart.data.sync.SyncProvider
import lk.picksmart.data.sync.SyncStatus
import lk.picksmart.domain.model.SignInMethod
import lk.picksmart.domain.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class DeleteAccountTest {

    private val steps = mutableListOf<String>()

    private fun deleteAccount(
        confirm: AuthResult = AuthResult.Success,
        cloudProblem: SyncProblem? = null,
        accountDeleted: AuthResult = AuthResult.Success,
    ) = DeleteAccount(
        FakeAccount(confirm, accountDeleted, steps),
        FakeSync(cloudProblem, steps),
        erasePhoneData = { uid -> steps += "erase phone of $uid" },
    )

    @Test
    fun `deletes the cloud backup, then the account, then the phone's data`() = runTest {
        val result = deleteAccount()(IdentityProof.Password("secret"))

        assertEquals(DeleteAccountResult.Deleted, result)
        assertEquals(listOf("confirm", "delete cloud", "delete account", "erase phone of user1"), steps)
    }

    @Test
    fun `a wrong password deletes nothing`() = runTest {
        val result = deleteAccount(confirm = AuthResult.Failure(AuthError.WRONG_CREDENTIALS))(IdentityProof.Password("wrong"))

        assertEquals(DeleteAccountResult.NotConfirmed(AuthError.WRONG_CREDENTIALS), result)
        assertEquals(listOf("confirm"), steps)
    }

    @Test
    fun `another Google account deletes nothing`() = runTest {
        val result = deleteAccount(confirm = AuthResult.Failure(AuthError.DIFFERENT_ACCOUNT))(IdentityProof.Google("token"))

        assertEquals(DeleteAccountResult.NotConfirmed(AuthError.DIFFERENT_ACCOUNT), result)
        assertEquals(listOf("confirm"), steps)
    }

    @Test
    fun `offline, the backup can't be deleted and nothing else is`() = runTest {
        val result = deleteAccount(cloudProblem = SyncProblem.OFFLINE)(IdentityProof.Password("secret"))

        assertEquals(DeleteAccountResult.CloudNotDeleted(SyncProblem.OFFLINE), result)
        assertEquals(listOf("confirm", "delete cloud"), steps)
    }

    @Test
    fun `a refusal from the server is reported as a refusal, not as offline`() = runTest {
        val result = deleteAccount(cloudProblem = SyncProblem.REFUSED)(IdentityProof.Password("secret"))

        assertEquals(DeleteAccountResult.CloudNotDeleted(SyncProblem.REFUSED), result)
        assertEquals(listOf("confirm", "delete cloud"), steps)
    }

    @Test
    fun `if the account isn't deleted, the phone keeps its records`() = runTest {
        val result = deleteAccount(accountDeleted = AuthResult.Failure(AuthError.NO_INTERNET))(IdentityProof.Password("secret"))

        assertEquals(DeleteAccountResult.AccountNotDeleted(AuthError.NO_INTERNET), result)
        assertEquals(listOf("confirm", "delete cloud", "delete account"), steps)
    }
}

private class FakeAccount(
    private val confirm: AuthResult,
    private val delete: AuthResult,
    private val steps: MutableList<String>,
) : AccountProvider {
    override val isAvailable = true
    override val profile = MutableStateFlow<UserProfile?>(UserProfile("user1", null, "a@b.lk", true, null, SignInMethod.EMAIL))

    override suspend fun confirmIdentity(proof: IdentityProof): AuthResult {
        steps += "confirm"
        return confirm
    }

    override suspend fun deleteAccount(): AuthResult {
        steps += "delete account"
        if (delete == AuthResult.Success) profile.value = null
        return delete
    }

    override suspend fun signIn(email: String, password: String) = AuthResult.Success
    override suspend fun register(name: String, email: String, password: String) = AuthResult.Success
    override suspend fun signInWithGoogle(idToken: String) = AuthResult.Success
    override suspend fun sendPasswordReset(email: String) = AuthResult.Success
    override suspend fun sendEmailVerification() = AuthResult.Success
    override suspend fun refresh() = AuthResult.Success
    override suspend fun updateName(name: String) = AuthResult.Success
    override suspend fun signOut() = Unit
}

private class FakeSync(private val problem: SyncProblem?, private val steps: MutableList<String>) : SyncProvider {
    override val status: StateFlow<SyncStatus> = MutableStateFlow(SyncStatus.Idle(null))
    override val pendingCount: Flow<Int> = flowOf(0)
    override suspend fun syncNow(): SyncStatus = status.value

    override fun backUpSoon() = Unit

    override suspend fun startFresh() = Unit

    override fun resume() = Unit

    override suspend fun deleteCloudData(): SyncProblem? {
        steps += "delete cloud"
        return problem
    }
}
