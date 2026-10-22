package lk.picksmart.ui.vehicle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import lk.picksmart.R
import lk.picksmart.ui.components.EmptyState
import lk.picksmart.ui.components.ErrorState
import lk.picksmart.ui.components.LoadingState
import lk.picksmart.ui.components.NoticeCard
import lk.picksmart.ui.components.PrimaryButton
import lk.picksmart.ui.components.ScreenTitle
import lk.picksmart.ui.theme.Spacing

@Composable
fun EditVehicleScreen(
    onClose: () -> Unit,
    viewModel: EditVehicleViewModel = viewModel(factory = EditVehicleViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Leave once the save has landed.
    val saved = (state as? EditVehicleUiState.Editing)?.saved == true
    LaunchedEffect(saved) { if (saved) onClose() }

    val isNew = (state as? EditVehicleUiState.Editing)?.isNew == true
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_close))
            }
            ScreenTitle(
                stringResource(if (isNew) R.string.vehicle_add_title else R.string.vehicle_edit_title),
                Modifier.padding(start = Spacing.s),
            )
        }

        when (val s = state) {
            EditVehicleUiState.Loading -> LoadingState()
            EditVehicleUiState.NotFound -> EmptyState(
                iconRes = R.drawable.ic_info,
                title = stringResource(R.string.vehicle_not_found_title),
                body = stringResource(R.string.vehicle_not_found_body),
                actionLabel = stringResource(R.string.action_close),
                onAction = onClose,
            )
            is EditVehicleUiState.Editing -> {
                if (s.isSample) {
                    NoticeCard(
                        title = stringResource(R.string.sample_notice_title),
                        body = stringResource(R.string.vehicle_edit_sample_body),
                    )
                }
                VehicleFormFields(state = s.form, errors = s.errors, onChange = viewModel::updateForm)
                if (s.saveFailed) ErrorState(stringResource(R.string.vehicle_save_failed))
                val defaultName = stringResource(s.form.type.label)
                PrimaryButton(
                    stringResource(R.string.vehicle_save),
                    { viewModel.save(defaultName) },
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.l),
                    enabled = !s.saving,
                )
            }
        }
    }
}
