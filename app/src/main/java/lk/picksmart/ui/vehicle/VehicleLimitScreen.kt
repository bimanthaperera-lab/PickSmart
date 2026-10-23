package lk.picksmart.ui.vehicle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import lk.picksmart.PicksmartApplication
import lk.picksmart.R
import lk.picksmart.data.account.AccountProvider
import lk.picksmart.data.account.signedIn
import lk.picksmart.domain.model.VehicleType
import lk.picksmart.ui.components.DetailHeader
import lk.picksmart.ui.components.IconTile
import lk.picksmart.ui.components.LoadingState
import lk.picksmart.ui.components.PrimaryButton
import lk.picksmart.ui.components.SecondaryButton
import lk.picksmart.ui.components.neutralRecordIcon
import lk.picksmart.ui.theme.Spacing

/** Whether the rider is signed in, for the vehicle limit screen; null while loading. */
class VehicleLimitViewModel(account: AccountProvider) : ViewModel() {
    val signInAvailable: Boolean = account.isAvailable

    val signedIn: StateFlow<Boolean?> = account.signedIn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        val Factory = viewModelFactory {
            initializer { VehicleLimitViewModel((this[APPLICATION_KEY] as PicksmartApplication).container.account) }
        }
    }
}

/**
 * Opens instead of the vehicle form when the rider is at the vehicle limit (CLAUDE.md section 9,
 * "Vehicles"): signed out, "Sign in to add up to 3 vehicles" with Sign up and Sign in; signed in,
 * "More vehicles with Premium", which is coming soon (Phase 8d). Nothing the rider has is touched.
 */
@Composable
fun VehicleLimitScreen(
    onBack: () -> Unit,
    onSignUp: () -> Unit,
    onSignIn: () -> Unit,
    viewModel: VehicleLimitViewModel = viewModel(factory = VehicleLimitViewModel.Factory),
) {
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        DetailHeader(stringResource(R.string.vehicle_limit_header), R.drawable.ic_arrow_back, stringResource(R.string.action_back), onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            when (signedIn) {
                null -> LoadingState()
                false -> SignedOutLimit(viewModel.signInAvailable, onSignUp, onSignIn, onBack)
                true -> SignedInLimit(onBack)
            }
        }
    }
}

@Composable
private fun SignedOutLimit(signInAvailable: Boolean, onSignUp: () -> Unit, onSignIn: () -> Unit, onBack: () -> Unit) {
    LimitText(
        title = stringResource(R.string.vehicle_limit_signed_out_title),
        body = stringResource(R.string.vehicle_limit_signed_out_body),
    )
    if (signInAvailable) {
        PrimaryButton(stringResource(R.string.account_sign_up), onSignUp, Modifier.fillMaxWidth())
        SecondaryButton(stringResource(R.string.account_sign_in), onSignIn, Modifier.fillMaxWidth())
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.vehicle_limit_not_now), style = MaterialTheme.typography.labelLarge)
        }
    } else {
        Text(stringResource(R.string.account_unavailable), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PrimaryButton(stringResource(R.string.action_ok), onBack, Modifier.fillMaxWidth())
    }
}

@Composable
private fun SignedInLimit(onBack: () -> Unit) {
    LimitText(
        title = stringResource(R.string.vehicle_limit_signed_in_title),
        body = stringResource(R.string.vehicle_limit_signed_in_body),
    )
    PrimaryButton(stringResource(R.string.action_ok), onBack, Modifier.fillMaxWidth())
}

@Composable
private fun LimitText(title: String, body: String) {
    IconTile(neutralRecordIcon(VehicleType.THREE_WHEELER.icon, tint = MaterialTheme.colorScheme.primary))
    Text(
        title,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.semantics { heading() },
    )
    Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
