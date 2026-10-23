package lk.picksmart.ui.account

import android.graphics.Bitmap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import lk.picksmart.data.account.AccountProvider
import lk.picksmart.data.account.ProfilePhotoCache
import lk.picksmart.data.preferences.UserPreferencesRepository
import lk.picksmart.domain.model.UserProfile

/** What the Account tab and Profile screen know about the rider's account. */
sealed interface AccountUi {
    data object Loading : AccountUi

    /** Built without Firebase: there is no sign-in to offer. */
    data object Unavailable : AccountUi

    data object SignedOut : AccountUi

    data class SignedIn(val profile: UserProfile, val mobile: String?, val photo: Bitmap?) : AccountUi
}

/** The account with its mobile number and cached photo. The photo arrives a moment later. */
@OptIn(ExperimentalCoroutinesApi::class)
fun accountUi(
    account: AccountProvider,
    photos: ProfilePhotoCache,
    preferences: UserPreferencesRepository,
): Flow<AccountUi> {
    if (!account.isAvailable) return flowOf(AccountUi.Unavailable)
    return account.profile.flatMapLatest { profile ->
        if (profile == null) {
            flowOf(AccountUi.SignedOut)
        } else {
            val photo = flow {
                emit(null)
                emit(photos.load(profile.uid, profile.photoUrl))
            }
            combine(preferences.mobileNumber(profile.uid), photo) { mobile, bitmap ->
                AccountUi.SignedIn(profile, mobile, bitmap)
            }
        }
    }
}

/**
 * Up to two letters for the avatar: the first letters of the first two words of the name, or the
 * first letter of the email. Null when there is neither, so the avatar shows a person icon.
 */
fun initialsOf(name: String?, email: String?): String? {
    val words = name.orEmpty().split(' ').filter { it.isNotBlank() }
    val fromName = words.take(2).mapNotNull { word -> word.firstOrNull { it.isLetterOrDigit() } }
    val letters = fromName.ifEmpty { listOfNotNull(email?.firstOrNull { it.isLetterOrDigit() }) }
    return letters.joinToString("").uppercase().ifEmpty { null }
}
