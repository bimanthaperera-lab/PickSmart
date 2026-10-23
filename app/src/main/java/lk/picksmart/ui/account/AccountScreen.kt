package lk.picksmart.ui.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import lk.picksmart.PicksmartApplication
import lk.picksmart.R
import lk.picksmart.ui.checkride.STACK_FONT_SCALE
import lk.picksmart.ui.components.PicksmartCard
import lk.picksmart.ui.components.PrimaryButton
import lk.picksmart.ui.components.ScreenTitle
import lk.picksmart.ui.components.SecondaryButton
import lk.picksmart.ui.components.TabScreen
import lk.picksmart.ui.settings.SettingsSections
import lk.picksmart.ui.theme.Spacing

/** The Account tab (CLAUDE.md section 14): the profile card, then every setting below it. */
@Composable
fun AccountScreen(
    onSignUp: () -> Unit,
    onSignIn: () -> Unit,
    onOpenProfile: () -> Unit,
    onEditVehicle: (vehicleId: Long) -> Unit,
    onVehicleLimit: () -> Unit,
    onOpenIncome: () -> Unit,
    onOpenFuelLog: () -> Unit,
    onOpenExpenses: () -> Unit,
    onOpenFuelPrice: () -> Unit,
    viewModel: AccountViewModel = viewModel(factory = AccountViewModel.Factory),
) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    TabScreen {
        ScreenTitle(stringResource(R.string.account_title))
        ProfileCard(account, onSignUp, onSignIn, onOpenProfile)
        SettingsSections(
            onEditVehicle = onEditVehicle,
            onVehicleLimit = onVehicleLimit,
            onOpenIncome = onOpenIncome,
            onOpenFuelLog = onOpenFuelLog,
            onOpenExpenses = onOpenExpenses,
            onOpenFuelPrice = onOpenFuelPrice,
        )
    }
}

@Composable
private fun ProfileCard(account: AccountUi, onSignUp: () -> Unit, onSignIn: () -> Unit, onOpenProfile: () -> Unit) {
    when (account) {
        // Settles within a frame or two; drawing nothing avoids flashing the signed-out card.
        AccountUi.Loading -> Unit
        AccountUi.Unavailable -> PicksmartCard(Modifier.fillMaxWidth()) {
            CardHeader(
                title = stringResource(R.string.account_not_signed_in),
                body = stringResource(R.string.account_unavailable),
            )
        }
        AccountUi.SignedOut -> PicksmartCard(Modifier.fillMaxWidth()) {
            // No profile details while signed out, just what an account gives and the two ways in.
            CardHeader(
                title = stringResource(R.string.account_not_signed_in),
                body = stringResource(R.string.account_benefit),
            )
            Spacer(Modifier.size(Spacing.l))
            SignUpButtons(onSignUp, onSignIn)
        }
        is AccountUi.SignedIn -> SignedInCard(account, onOpenProfile)
    }
}

@Composable
private fun CardHeader(title: String, body: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(photo = null, initials = null)
        Spacer(Modifier.width(Spacing.l))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Side by side, or stacked at large font sizes so the labels never wrap. */
@Composable
private fun SignUpButtons(onSignUp: () -> Unit, onSignIn: () -> Unit) {
    val signUp = stringResource(R.string.account_sign_up)
    val signIn = stringResource(R.string.account_sign_in)
    if (LocalDensity.current.fontScale > STACK_FONT_SCALE) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            PrimaryButton(signUp, onSignUp, Modifier.fillMaxWidth())
            SecondaryButton(signIn, onSignIn, Modifier.fillMaxWidth())
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            PrimaryButton(signUp, onSignUp, Modifier.weight(1f))
            SecondaryButton(signIn, onSignIn, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SignedInCard(account: AccountUi.SignedIn, onOpenProfile: () -> Unit) {
    val profile = account.profile
    val title = profile.name ?: profile.email.orEmpty()
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.account_open_profile), onClick = onOpenProfile)
                .semantics(mergeDescendants = true) {}
                .padding(Spacing.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(account.photo, initialsOf(profile.name, profile.email))
            Spacer(Modifier.width(Spacing.l))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (profile.name != null && profile.email != null) {
                    Text(profile.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!profile.emailVerified) {
                    Text(
                        stringResource(R.string.account_email_not_verified),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.width(Spacing.s))
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

class AccountViewModel(account: Flow<AccountUi>) : ViewModel() {
    val account: StateFlow<AccountUi> = account
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUi.Loading)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as PicksmartApplication).container
                AccountViewModel(accountUi(container.account, container.profilePhotos, container.userPreferences))
            }
        }
    }
}
