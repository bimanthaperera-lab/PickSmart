package lk.picksmart.ui.vehicle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import lk.picksmart.R
import lk.picksmart.domain.usecase.ArchivePlan
import lk.picksmart.ui.components.EmptyState
import lk.picksmart.ui.components.ErrorState
import lk.picksmart.ui.components.LoadingState
import lk.picksmart.ui.components.NoticeCard
import lk.picksmart.ui.components.PrimaryButton
import lk.picksmart.ui.components.ScreenTitle
import lk.picksmart.ui.components.SecondaryButton
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
                VehicleFormFields(
                    state = s.form,
                    errors = s.errors,
                    onChange = viewModel::updateForm,
                    fuelPriceSuggestions = s.fuelPriceSuggestions,
                )
                if (s.saveFailed) ErrorState(stringResource(R.string.vehicle_save_failed))
                val defaultName = stringResource(s.form.type.label)
                PrimaryButton(
                    stringResource(R.string.vehicle_save),
                    { viewModel.save(defaultName) },
                    Modifier.fillMaxWidth(),
                    enabled = !s.saving,
                )
                if (!s.isNew) {
                    VehicleActions(
                        name = s.form.name.ifBlank { stringResource(s.form.type.label) },
                        state = s,
                        onSetActive = viewModel::setActive,
                        onArchive = viewModel::archive,
                    )
                }
                Spacer(Modifier.height(Spacing.l))
            }
        }
    }
}

/**
 * "Use this vehicle" for a vehicle that isn't active, and "Archive vehicle" unless it is the
 * rider's only one. Archiving always asks first; for the active vehicle it also asks which one
 * the rider uses now.
 */
@Composable
private fun VehicleActions(
    name: String,
    state: EditVehicleUiState.Editing,
    onSetActive: () -> Unit,
    onArchive: (newActiveId: Long?) -> Unit,
) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        if (state.isActive) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                ActiveTag()
                Text(
                    stringResource(R.string.vehicle_is_active),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            SecondaryButton(stringResource(R.string.vehicle_use_this), onSetActive, Modifier.fillMaxWidth(), enabled = !state.saving)
        }
        val plan = state.archivePlan
        if (plan != null && plan != ArchivePlan.LastVehicle) {
            TextButton(onClick = { confirm = true }, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.vehicle_archive), style = MaterialTheme.typography.labelLarge)
            }
        }
        if (confirm && plan != null) {
            ArchiveDialog(
                name = name,
                plan = plan,
                onArchive = { newActiveId ->
                    confirm = false
                    onArchive(newActiveId)
                },
                onDismiss = { confirm = false },
            )
        }
    }
}

/** Confirms archiving; for the active vehicle the rider first picks which one to use now. */
@Composable
private fun ArchiveDialog(name: String, plan: ArchivePlan, onArchive: (newActiveId: Long?) -> Unit, onDismiss: () -> Unit) {
    val candidates = (plan as? ArchivePlan.ChooseActive)?.candidates.orEmpty()
    var chosen by rememberSaveable { mutableStateOf(candidates.firstOrNull()?.id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vehicle_archive_title, name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Text(stringResource(R.string.vehicle_archive_body))
                if (candidates.isNotEmpty()) {
                    Text(stringResource(R.string.vehicle_archive_choose), style = MaterialTheme.typography.titleSmall)
                    Column(Modifier.selectableGroup()) {
                        candidates.forEach { vehicle ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .selectable(selected = vehicle.id == chosen, role = Role.RadioButton, onClick = { chosen = vehicle.id })
                                    .heightIn(min = Spacing.touchTarget),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = vehicle.id == chosen, onClick = null)
                                Text(vehicle.name, Modifier.padding(start = Spacing.m), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onArchive(chosen.takeIf { candidates.isNotEmpty() }) }) {
                Text(stringResource(R.string.vehicle_archive_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
