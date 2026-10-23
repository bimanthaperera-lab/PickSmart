package lk.picksmart.ui.account

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
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
import lk.picksmart.data.account.ProfilePhotoCache
import lk.picksmart.data.preferences.UserPreferencesRepository
import lk.picksmart.domain.model.SignInMethod
import lk.picksmart.ui.components.DetailHeader
import lk.picksmart.ui.components.ListGroup
import lk.picksmart.ui.components.ListRow
import lk.picksmart.ui.components.LoadingState
import lk.picksmart.ui.components.NoticeCard
import lk.picksmart.ui.components.SecondaryButton
import lk.picksmart.ui.theme.Spacing

/**
 * The signed-in rider's details (CLAUDE.md section 14): photo, name, email, mobile number and
 * sign-in method, with Edit, Sign out and Delete account. Closes itself once signed out.
 */
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory),
) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val signedIn = account as? AccountUi.SignedIn
    LaunchedEffect(account) { if (account is AccountUi.SignedOut || account is AccountUi.Unavailable) onBack() }

    var editing by rememberSaveable { mutableStateOf<ProfileField?>(null) }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

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
                Avatar(signedIn.photo, initialsOf(profile.name, profile.email), size = 96.dp)
                Text(
                    profile.name ?: profile.email.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.m),
                )
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
                            title = stringResource(R.string.profile_sign_in_method),
                            value = stringResource(
                                when (profile.method) {
                                    SignInMethod.EMAIL -> R.string.profile_method_email
                                    SignInMethod.GOOGLE -> R.string.profile_method_google
                                },
                            ),
                        )
                    },
                ),
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
            TextButton(onClick = { confirmDelete = true }, enabled = !status.busy, modifier = Modifier.fillMaxWidth()) {
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
    }

    if (confirmSignOut) {
        ConfirmDialog(
            title = R.string.profile_sign_out_title,
            body = R.string.profile_sign_out_body,
            confirm = R.string.profile_sign_out,
            destructive = false,
            onConfirm = {
                confirmSignOut = false
                viewModel.signOut()
            },
            onDismiss = { confirmSignOut = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = R.string.profile_delete_title,
            body = R.string.profile_delete_body,
            confirm = R.string.profile_delete_confirm,
            destructive = true,
            onConfirm = {
                confirmDelete = false
                viewModel.deleteAccount()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

enum class ProfileField { NAME, MOBILE }

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

/** A running request, and the last thing to tell the rider (an error, or e.g. "email sent"). */
data class ProfileStatus(val busy: Boolean = false, @StringRes val message: Int? = null, val isError: Boolean = false)

class ProfileViewModel(
    accountUi: Flow<AccountUi>,
    private val provider: AccountProvider,
    private val photos: ProfilePhotoCache,
    private val preferences: UserPreferencesRepository,
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
        AuthResult.Success
    }

    /** Signs out. The rider's trips and records stay on the phone (CLAUDE.md section 14). */
    fun signOut() = perform(null) {
        val uid = provider.profile.first()?.uid
        provider.signOut()
        uid?.let { photos.clear(it) }
        AuthResult.Success
    }

    /** Deletes the sign-in account and what is kept for it here; the rider's records stay. */
    fun deleteAccount() = perform(null) {
        val uid = provider.profile.first()?.uid
        val result = provider.deleteAccount()
        if (result == AuthResult.Success && uid != null) {
            preferences.setMobileNumber(uid, null)
            photos.clear(uid)
        }
        result
    }

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
                val container = (this[APPLICATION_KEY] as PicksmartApplication).container
                ProfileViewModel(
                    accountUi(container.account, container.profilePhotos, container.userPreferences),
                    container.account,
                    container.profilePhotos,
                    container.userPreferences,
                )
            }
        }
    }
}
