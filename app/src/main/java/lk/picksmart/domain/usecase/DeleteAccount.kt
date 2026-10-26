package lk.picksmart.domain.usecase

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import lk.picksmart.data.account.AccountProvider
import lk.picksmart.data.account.AuthError
import lk.picksmart.data.account.AuthResult
import lk.picksmart.data.account.IdentityProof
import lk.picksmart.data.sync.SyncProblem
import lk.picksmart.data.sync.SyncProvider

/** How deleting the account ended. */
sealed interface DeleteAccountResult {
    /** The cloud backup, the account and everything on the phone are gone. */
    data object Deleted : DeleteAccountResult

    /** The rider couldn't be confirmed (wrong password, offline...). Nothing was deleted. */
    data class NotConfirmed(val error: AuthError) : DeleteAccountResult

    /** The cloud backup couldn't be deleted, for [problem]. Nothing was deleted. */
    data class CloudNotDeleted(val problem: SyncProblem) : DeleteAccountResult

    /** The cloud backup is gone, but the account isn't. The phone's records are kept; try again. */
    data class AccountNotDeleted(val error: AuthError) : DeleteAccountResult
}

/**
 * Deletes the rider's account and all their data (CLAUDE.md section 14, owner decision 2026-10-26):
 * the cloud backup, then the sign-in account, then every record and setting on the phone, so a
 * new account made on this phone starts empty. The steps run in that order because the cloud
 * backup can only be reached while the account exists, and the phone's records are erased only
 * once nothing can upload them again.
 *
 * The rider confirms it's them first, because Firebase deletes only after a recent sign-in; a
 * failure there, or an offline phone, deletes nothing. Once started, deleting finishes even if the
 * screen that asked closes.
 */
class DeleteAccount(
    private val account: AccountProvider,
    private val sync: SyncProvider,
    private val erasePhoneData: suspend () -> Unit,
) {
    suspend operator fun invoke(proof: IdentityProof): DeleteAccountResult {
        val confirmed = account.confirmIdentity(proof)
        if (confirmed is AuthResult.Failure) return DeleteAccountResult.NotConfirmed(confirmed.error)
        return withContext(NonCancellable) {
            sync.deleteCloudData()?.let { return@withContext DeleteAccountResult.CloudNotDeleted(it) }
            val deleted = account.deleteAccount()
            if (deleted is AuthResult.Failure) return@withContext DeleteAccountResult.AccountNotDeleted(deleted.error)
            erasePhoneData()
            DeleteAccountResult.Deleted
        }
    }
}
