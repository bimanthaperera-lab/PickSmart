package lk.picksmart.ui.account

import android.graphics.Bitmap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import lk.picksmart.data.account.AccountProvider
import lk.picksmart.data.account.ProfilePhoto
import lk.picksmart.data.account.ProfilePhotoCache
import lk.picksmart.data.preferences.UserPreferencesRepository
import lk.picksmart.domain.model.PersonalDetails
import lk.picksmart.domain.model.UserProfile

/** What the Account tab and Profile screen know about the rider's account. */
sealed interface AccountUi {
    data object Loading : AccountUi

    /** Built without Firebase: there is no sign-in to offer. */
    data object Unavailable : AccountUi

    data object SignedOut : AccountUi

    /** [ownPhoto] is true when [photo] is one the rider chose, which they can remove. */
    data class SignedIn(
        val profile: UserProfile,
        val details: PersonalDetails,
        val photo: Bitmap?,
        val ownPhoto: Boolean = false,
    ) : AccountUi {
        val mobile: String? get() = details.mobile
    }
}

/**
 * The account with the details the rider added and the cached photo. The photo arrives a moment
 * later, and loads again when the rider chooses or removes one.
 */
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
            val photo = photos.changes
                .mapLatest { photos.load(profile.uid, profile.photoUrl) }
                .onStart { emit(ProfilePhoto(null, own = false)) }
            combine(preferences.personalDetails(profile.uid), photo) { details, loaded ->
                AccountUi.SignedIn(profile, details, loaded.bitmap, loaded.own)
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
