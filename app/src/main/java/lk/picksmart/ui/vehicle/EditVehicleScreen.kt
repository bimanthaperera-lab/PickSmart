package lk.picksmart.ui.vehicle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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

    val editing = state as? EditVehicleUiState.Editing
    val isNew = editing?.isNew == true
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            // The keyboard shrinks the form, so the lower fields and Save can scroll above it.
            .imePadding()
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
                Modifier
                    .weight(1f)
                    .padding(start = Spacing.s),
            )
            // Delete, top right as in the mockup. It removes the vehicle from the list; its
            // records stay (an archive underneath, CLAUDE.md N7).
            if (editing != null && editing.archivePlan != null) {
                IconButton(onClick = { confirmDelete = true }, enabled = !editing.saving) {
                    Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.vehicle_delete))
                }
            }
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
                PrimaryButton(
                    stringResource(R.string.vehicle_save),
                    viewModel::save,
                    Modifier.fillMaxWidth(),
                    enabled = !s.saving,
                )
                Spacer(Modifier.height(Spacing.l))
            }
        }
    }

    val plan = editing?.archivePlan
    if (confirmDelete && editing != null && plan != null) {
        val name = editing.form.name.ifBlank { stringResource(editing.form.type.label) }
        if (plan == ArchivePlan.LastVehicle) {
            OnlyVehicleDialog(onDismiss = { confirmDelete = false })
        } else {
            DeleteDialog(
                name = name,
                plan = plan,
                onDelete = { newActiveId ->
                    confirmDelete = false
                    viewModel.archive(newActiveId)
                },
                onDismiss = { confirmDelete = false },
            )
        }
    }
}

/** The rider's only vehicle can't go: Check a Ride needs one. */
@Composable
private fun OnlyVehicleDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vehicle_delete_only_title)) },
        text = { Text(stringResource(R.string.vehicle_delete_only_body)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}

/**
 * Confirms deleting; for the active vehicle the rider first picks which one to use now. Its trips,
 * fuel and expenses stay in History and Reports.
 */
@Composable
private fun DeleteDialog(name: String, plan: ArchivePlan, onDelete: (newActiveId: Long?) -> Unit, onDismiss: () -> Unit) {
    val candidates = (plan as? ArchivePlan.ChooseActive)?.candidates.orEmpty()
    var chosen by rememberSaveable { mutableStateOf(candidates.firstOrNull()?.id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vehicle_delete_title, name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Text(stringResource(R.string.vehicle_delete_body))
                if (candidates.isNotEmpty()) {
                    Text(stringResource(R.string.vehicle_delete_choose), style = MaterialTheme.typography.titleSmall)
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
            TextButton(
                onClick = { onDelete(chosen.takeIf { candidates.isNotEmpty() }) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.vehicle_delete_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
