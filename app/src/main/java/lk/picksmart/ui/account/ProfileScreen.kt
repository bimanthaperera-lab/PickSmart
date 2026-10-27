package lk.picksmart.ui.account

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import lk.picksmart.PicksmartApplication
import lk.picksmart.R
import lk.picksmart.data.account.AccountProvider
import lk.picksmart.data.account.AuthResult
import lk.picksmart.data.account.IdentityProof
import lk.picksmart.data.account.PhotoSaveResult
import lk.picksmart.data.account.ProfilePhotoCache
import lk.picksmart.data.preferences.UserPreferencesRepository
import lk.picksmart.data.sync.SyncProblem
import lk.picksmart.data.sync.SyncProvider
import lk.picksmart.domain.model.Gender
import lk.picksmart.domain.model.SignInMethod
import lk.picksmart.domain.usecase.DeleteAccount
import lk.picksmart.domain.usecase.DeleteAccountResult
import lk.picksmart.ui.components.DetailHeader
import lk.picksmart.ui.components.ListGroup
import lk.picksmart.ui.components.ListRow
import lk.picksmart.ui.components.LoadingState
import lk.picksmart.ui.components.NoticeCard
import lk.picksmart.ui.components.PickDateDialog
import lk.picksmart.ui.components.SecondaryButton
import lk.picksmart.ui.components.formatPattern
import lk.picksmart.ui.theme.Spacing
import lk.picksmart.util.todayInColombo
import java.time.LocalDate

/**
 * The signed-in rider's details (CLAUDE.md section 14): photo, name, email, mobile number, gender
 * and date of birth, with Edit, Sign out and Delete account. Closes itself once signed out. The
 * photo the rider chooses, the gender and the date of birth stay on the phone.
 */
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory),
) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val signedIn = account as? AccountUi.SignedIn
    // While the account is being deleted it signs out first; the screen stays until the phone's
    // data is erased too, and then the app returns to the welcome screen by itself.
    LaunchedEffect(account, status.busy) {
        if ((account is AccountUi.SignedOut || account is AccountUi.Unavailable) && !status.busy) onBack()
    }

    var editing by rememberSaveable { mutableStateOf<ProfileField?>(null) }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    // The sign-in method of the account being deleted, while the warning is open.
    var deleting by rememberSaveable { mutableStateOf<SignInMethod?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var choosingGender by rememberSaveable { mutableStateOf(false) }
    var pickingBirthDate by rememberSaveable { mutableStateOf(false) }
    // The Photo Picker needs no storage permission, and shares only the image the rider picks.
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::savePhoto)
    }
    val choosePhoto = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        DetailHeader(stringResource(R.string.profile_title), R.drawable.ic_arrow_back, stringResource(R.string.action_back), onBack)
        if (signedIn == null) {
            LoadingState()
            return@Column
        }
        val profile = signedIn.profile
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                val changePhoto = stringResource(R.string.profile_change_photo_description)
                Avatar(
                    signedIn.photo,
                    initialsOf(profile.name, profile.email),
                    size = 96.dp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(enabled = !status.busy, role = Role.Button, onClick = choosePhoto)
                        .semantics { contentDescription = changePhoto },
                )
                Text(
                    profile.name ?: profile.email.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.m),
                )
                Row(horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = choosePhoto, enabled = !status.busy) {
                        Text(stringResource(R.string.profile_change_photo))
                    }
                    if (signedIn.ownPhoto) {
                        TextButton(onClick = viewModel::removePhoto, enabled = !status.busy) {
                            Text(stringResource(R.string.profile_remove_photo))
                        }
                    }
                }
            }

            if (!profile.emailVerified && profile.email != null) {
                NoticeCard(
                    title = stringResource(R.string.profile_verify_title),
                    body = stringResource(R.string.profile_verify_body, profile.email),
                    actionLabel = stringResource(R.string.profile_resend_email),
                    onAction = viewModel::resendVerification,
                    primaryLabel = stringResource(R.string.profile_check_verified),
                    onPrimary = viewModel::checkVerified,
                )
            }

            ListGroup(
                rows = listOf(
                    {
                        ListRow(
                            title = stringResource(R.string.auth_name),
                            value = profile.name ?: stringResource(R.string.profile_not_set),
                            onClick = { editing = ProfileField.NAME },
                        )
                    },
                    {
                        ListRow(
                            title = stringResource(R.string.auth_email),
                            subtitle = profile.email,
                            value = stringResource(if (profile.emailVerified) R.string.profile_verified else R.string.profile_not_verified),
                        )
                    },
                    {
                        ListRow(
                            title = stringResource(R.string.profile_mobile),
                            subtitle = stringResource(
                                if (signedIn.mobile != null) R.string.profile_mobile_not_verified else R.string.profile_mobile_optional,
                            ),
                            value = signedIn.mobile ?: stringResource(R.string.profile_add),
                            onClick = { editing = ProfileField.MOBILE },
                        )
                    },
                    {
                        ListRow(
                            title = stringResource(R.string.profile_gender),
                            subtitle = stringResource(R.string.profile_mobile_optional),
                            value = signedIn.details.gender?.let { stringResource(it.label) } ?: stringResource(R.string.profile_add),
                            onClick = { choosingGender = true },
                        )
                    },
                    {
                        ListRow(
                            title = stringResource(R.string.profile_birth_date),
                            subtitle = stringResource(R.string.profile_mobile_optional),
                            value = signedIn.details.birthDate?.let { formatPattern(it, stringResource(R.string.pattern_date_field)) }
                                ?: stringResource(R.string.profile_add),
                            onClick = { pickingBirthDate = true },
                        )
                    },
                ),
            )
            Text(
                stringResource(R.string.profile_photo_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            status.message?.let { message ->
                Text(
                    stringResource(message),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (status.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            SecondaryButton(
                stringResource(R.string.profile_sign_out),
                { confirmSignOut = true },
                Modifier.fillMaxWidth(),
                enabled = !status.busy,
            )
            TextButton(
                onClick = {
                    viewModel.clearMessage()
                    deleting = profile.method
                },
                enabled = !status.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.profile_delete_account), color = MaterialTheme.colorScheme.error)
            }
            Text(
                stringResource(R.string.profile_records_stay),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        editing?.let { field ->
            EditFieldDialog(
                field = field,
                initial = when (field) {
                    ProfileField.NAME -> profile.name.orEmpty()
                    ProfileField.MOBILE -> signedIn.mobile.orEmpty()
                },
                onSave = { value ->
                    editing = null
                    when (field) {
                        ProfileField.NAME -> viewModel.saveName(value)
                        ProfileField.MOBILE -> viewModel.saveMobile(value)
                    }
                },
                onDismiss = { editing = null },
            )
        }
        if (choosingGender) {
            GenderDialog(
                selected = signedIn.details.gender,
                onSelect = {
                    choosingGender = false
                    viewModel.saveGender(it)
                },
                onRemove = {
                    choosingGender = false
                    viewModel.saveGender(null)
                },
                onDismiss = { choosingGender = false },
            )
        }
        if (pickingBirthDate) {
            val today = todayInColombo()
            PickDateDialog(
                initial = signedIn.details.birthDate,
                onPick = viewModel::saveBirthDate,
                onDismiss = { pickingBirthDate = false },
                minDate = EarliestBirthDate,
                maxDate = today,
                shownWhenEmpty = today.minusYears(BIRTH_DATE_START_YEARS_AGO),
                onRemove = if (signedIn.details.birthDate != null) {
                    { viewModel.saveBirthDate(null) }
                } else {
                    null
                },
            )
        }
    }

    if (confirmSignOut) {
        ConfirmDialog(
            title = R.string.profile_sign_out_title,
            body = R.string.profile_sign_out_body_apart,
            confirm = R.string.profile_sign_out,
            destructive = false,
            onConfirm = {
                confirmSignOut = false
                viewModel.signOut()
            },
            onDismiss = { confirmSignOut = false },
        )
    }
    deleting?.let { method ->
        DeleteAccountDialog(
            method = method,
            status = status,
            onDeleteWithPassword = { viewModel.deleteAccount(IdentityProof.Password(it)) },
            onDeleteWithGoogle = {
                scope.launch {
                    when (val result = requestGoogleIdToken(context)) {
                        is GoogleTokenResult.Token -> viewModel.deleteAccount(IdentityProof.Google(result.idToken))
                        GoogleTokenResult.Cancelled -> Unit
                        GoogleTokenResult.NoAccount -> viewModel.showError(R.string.auth_error_google_no_account)
                        GoogleTokenResult.Failed -> viewModel.showError(R.string.auth_error_google_failed)
                    }
                }
            },
            onDismiss = {
                deleting = null
                viewModel.clearMessage()
            },
        )
    }
}

enum class ProfileField { NAME, MOBILE }

/** With no date of birth yet, the picker opens this many years back rather than on this month. */
private const val BIRTH_DATE_START_YEARS_AGO = 25L

@get:StringRes
private val Gender.label: Int
    get() = when (this) {
        Gender.MALE -> R.string.gender_male
        Gender.FEMALE -> R.string.gender_female
        Gender.OTHER -> R.string.gender_other
        Gender.PREFER_NOT_TO_SAY -> R.string.gender_prefer_not_to_say
    }

@Composable
private fun GenderDialog(selected: Gender?, onSelect: (Gender) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_gender)) },
        text = {
            Column(Modifier.selectableGroup()) {
                Gender.entries.forEach { gender ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = Spacing.touchTarget)
                            .selectable(selected = gender == selected, role = Role.RadioButton, onClick = { onSelect(gender) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // The whole row is the control, so the radio itself takes no clicks.
                        RadioButton(selected = gender == selected, onClick = null)
                        Spacer(Modifier.width(Spacing.m))
                        Text(stringResource(gender.label), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        dismissButton = if (selected != null) {
            { TextButton(onClick = onRemove) { Text(stringResource(R.string.action_remove)) } }
        } else {
            null
        },
    )
}

@Composable
private fun EditFieldDialog(field: ProfileField, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    var tried by rememberSaveable { mutableStateOf(false) }
    val error = when (field) {
        ProfileField.NAME -> if (text.isBlank()) AuthFieldError.NAME_REQUIRED else null
        ProfileField.MOBILE -> mobileError(text)
    }
    val save = {
        tried = true
        if (error == null) onSave(text)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (field == ProfileField.NAME) R.string.profile_edit_name else R.string.profile_edit_mobile))
        },
        text = {
            AuthTextField(
                text,
                { text = it },
                stringResource(if (field == ProfileField.NAME) R.string.auth_name else R.string.auth_mobile_optional),
                if (field == ProfileField.NAME) AuthInput.NAME else AuthInput.MOBILE,
                error = if (tried) error?.let { stringResource(it.message) } else null,
                supportingText = if (field == ProfileField.MOBILE) stringResource(R.string.auth_mobile_hint) else null,
                imeAction = ImeAction.Done,
                onDone = save,
            )
        },
        confirmButton = { TextButton(onClick = save) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ConfirmDialog(
    @StringRes title: Int,
    @StringRes body: Int,
    @StringRes confirm: Int,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(confirm),
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * The warning before deleting the account (CLAUDE.md section 14): it lists everything that goes,
 * the cloud backup and every record on this phone included, and asks the rider to confirm it's
 * them (their password, or Google's account picker) before anything is deleted. It stays open on
 * an error, so a wrong password can be typed again.
 */
@Composable
private fun DeleteAccountDialog(
    method: SignInMethod,
    status: ProfileStatus,
    onDeleteWithPassword: (String) -> Unit,
    onDeleteWithGoogle: () -> Unit,
    onDismiss: () -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    val canDelete = !status.busy && (method == SignInMethod.GOOGLE || password.isNotEmpty())
    val delete = {
        if (canDelete) {
            if (method == SignInMethod.EMAIL) onDeleteWithPassword(password) else onDeleteWithGoogle()
        }
    }
    AlertDialog(
        onDismissRequest = { if (!status.busy) onDismiss() },
        title = { Text(stringResource(R.string.profile_delete_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Text(stringResource(R.string.profile_delete_body))
                listOf(
                    R.string.profile_delete_item_account,
                    R.string.profile_delete_item_backup,
                    R.string.profile_delete_item_phone,
                ).forEach { item ->
                    Text(stringResource(R.string.profile_delete_item, stringResource(item)))
                }
                Text(
                    stringResource(R.string.profile_delete_export_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (method == SignInMethod.EMAIL) {
                    AuthTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = stringResource(R.string.profile_delete_password_label),
                        input = AuthInput.PASSWORD,
                        supportingText = stringResource(R.string.profile_delete_password_hint),
                        imeAction = ImeAction.Done,
                        onDone = delete,
                    )
                } else {
                    Text(stringResource(R.string.profile_delete_google_hint), style = MaterialTheme.typography.bodyMedium)
                }
                when {
                    status.busy -> Text(
                        stringResource(R.string.profile_deleting),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    status.isError -> status.message?.let { message ->
                        Text(
                            stringResource(message),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = delete, enabled = canDelete) {
                Text(stringResource(R.string.profile_delete_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !status.busy) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Why the cloud backup wasn't deleted, in words the rider can act on. Nothing else was deleted. */
@StringRes
private fun cloudDeleteMessage(problem: SyncProblem): Int = when (problem) {
    SyncProblem.OFFLINE -> R.string.profile_delete_cloud_failed
    SyncProblem.REFUSED -> R.string.profile_delete_cloud_refused
    SyncProblem.UNVERIFIED -> R.string.profile_delete_cloud_unverified
    SyncProblem.FAILED -> R.string.profile_delete_cloud_error
}

/** A running request, and the last thing to tell the rider (an error, or e.g. "email sent"). */
data class ProfileStatus(val busy: Boolean = false, @StringRes val message: Int? = null, val isError: Boolean = false)

class ProfileViewModel(
    accountUi: Flow<AccountUi>,
    private val provider: AccountProvider,
    private val photos: ProfilePhotoCache,
    private val preferences: UserPreferencesRepository,
    private val resolver: ContentResolver,
    private val deleter: DeleteAccount,
    /** Backs up the personal details and photo after the rider changes them. */
    private val sync: SyncProvider,
) : ViewModel() {
    val account: StateFlow<AccountUi> = accountUi
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUi.Loading)

    private val mutableStatus = MutableStateFlow(ProfileStatus())
    val status: StateFlow<ProfileStatus> = mutableStatus.asStateFlow()

    init {
        // Picks up an email verified since last time. Offline, the cached details simply stay.
        viewModelScope.launch { provider.refresh() }
    }

    fun resendVerification() = perform(R.string.profile_verification_sent) { provider.sendEmailVerification() }

    fun checkVerified() = perform(null) { provider.refresh() }

    fun saveName(name: String) = perform(null) { provider.updateName(name.trim()) }

    fun saveMobile(mobile: String) = perform(null) {
        val uid = provider.profile.first()?.uid
        if (uid != null) preferences.setMobileNumber(uid, cleanMobile(mobile))
        sync.backUpSoon()
        AuthResult.Success
    }

    /** Makes the picked image the rider's photo; only a small square copy is kept. */
    fun savePhoto(uri: Uri) {
        if (mutableStatus.value.busy) return
        mutableStatus.value = ProfileStatus(busy = true)
        viewModelScope.launch {
            val uid = provider.profile.first()?.uid
            val result = if (uid == null) PhotoSaveResult.SAVED else photos.saveOwn(uid, resolver, uri)
            if (result == PhotoSaveResult.SAVED) sync.backUpSoon()
            mutableStatus.value = when (result) {
                PhotoSaveResult.SAVED -> ProfileStatus()
                PhotoSaveResult.UNSUPPORTED_FORMAT -> ProfileStatus(message = R.string.profile_photo_unsupported, isError = true)
                PhotoSaveResult.TOO_LARGE -> ProfileStatus(message = R.string.profile_photo_too_large, isError = true)
                PhotoSaveResult.UNREADABLE -> ProfileStatus(message = R.string.profile_photo_failed, isError = true)
            }
        }
    }

    fun removePhoto() = perform(null) {
        provider.profile.first()?.uid?.let { photos.removeOwn(it) }
        sync.backUpSoon()
        AuthResult.Success
    }

    /** Saves the gender, or removes it when [gender] is null. */
    fun saveGender(gender: Gender?) = perform(null) {
        provider.profile.first()?.uid?.let { preferences.setGender(it, gender) }
        sync.backUpSoon()
        AuthResult.Success
    }

    /** Saves the date of birth, or removes it when [date] is null. A date out of range is ignored. */
    fun saveBirthDate(date: LocalDate?) = perform(null) {
        val uid = provider.profile.first()?.uid
        if (uid != null && (date == null || isValidBirthDate(date, todayInColombo()))) preferences.setBirthDate(uid, date)
        sync.backUpSoon()
        AuthResult.Success
    }

    /** Signs out. The rider's trips and records stay on the phone (CLAUDE.md section 14). */
    fun signOut() = perform(null) {
        val uid = provider.profile.first()?.uid
        provider.signOut()
        uid?.let { photos.clear(it) }
        AuthResult.Success
    }

    /**
     * Confirms it's the rider with [proof], then deletes the cloud backup, the account and all
     * their data on this phone ([DeleteAccount]). Afterwards the app is back at the welcome screen.
     */
    fun deleteAccount(proof: IdentityProof) {
        if (mutableStatus.value.busy) return
        mutableStatus.value = ProfileStatus(busy = true)
        viewModelScope.launch {
            mutableStatus.value = when (val result = deleter(proof)) {
                DeleteAccountResult.Deleted -> ProfileStatus()
                is DeleteAccountResult.NotConfirmed -> ProfileStatus(message = result.error.message(), isError = true)
                is DeleteAccountResult.CloudNotDeleted -> ProfileStatus(message = cloudDeleteMessage(result.problem), isError = true)
                is DeleteAccountResult.AccountNotDeleted ->
                    ProfileStatus(message = R.string.profile_delete_account_failed, isError = true)
            }
        }
    }

    fun showError(@StringRes message: Int) {
        mutableStatus.value = ProfileStatus(message = message, isError = true)
    }

    fun clearMessage() {
        if (!mutableStatus.value.busy) mutableStatus.value = ProfileStatus()
    }

    /** Runs one request at a time. */
    private fun perform(@StringRes successMessage: Int?, action: suspend () -> AuthResult) {
        if (mutableStatus.value.busy) return
        mutableStatus.value = ProfileStatus(busy = true)
        viewModelScope.launch {
            mutableStatus.value = when (val result = action()) {
                AuthResult.Success -> ProfileStatus(message = successMessage)
                is AuthResult.Failure -> ProfileStatus(message = result.error.message(), isError = true)
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as PicksmartApplication
                val container = application.container
                ProfileViewModel(
                    accountUi(container.account, container.profilePhotos, container.userPreferences),
                    container.account,
                    container.profilePhotos,
                    container.userPreferences,
                    application.contentResolver,
                    container.deleteAccount,
                    container.sync,
                )
            }
        }
    }
}
