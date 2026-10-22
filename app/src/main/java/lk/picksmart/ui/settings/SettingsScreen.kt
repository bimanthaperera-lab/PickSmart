package lk.picksmart.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import lk.picksmart.BuildConfig
import lk.picksmart.R
import lk.picksmart.domain.model.ThemeMode
import lk.picksmart.ui.components.ListGroup
import lk.picksmart.ui.components.LoadingState
import lk.picksmart.ui.components.NoticeCard
import lk.picksmart.ui.components.ListRow
import lk.picksmart.ui.components.ScreenTitle
import lk.picksmart.ui.components.SectionLabel
import lk.picksmart.ui.components.TabScreen
import lk.picksmart.ui.navigation.EditVehicleRoute
import lk.picksmart.ui.theme.Spacing
import lk.picksmart.ui.vehicle.vehicleKindSummary
import lk.picksmart.ui.vehicle.vehicleRatesSummary

@Composable
fun SettingsScreen(
    onEditVehicle: (vehicleId: Long) -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val vehicle by viewModel.vehicle.collectAsStateWithLifecycle()
    var showThemeDialog by rememberSaveable { mutableStateOf(false) }

    TabScreen {
        ScreenTitle(stringResource(R.string.settings_title))
        Column {
            VehicleSettings(vehicle, onEditVehicle)
            SectionLabel(stringResource(R.string.settings_section_app))
            ListGroup(
                rows = listOf {
                    ListRow(
                        title = stringResource(R.string.settings_theme),
                        value = themeMode?.let { stringResource(it.label) },
                        onClick = { showThemeDialog = true },
                    )
                },
            )
            SectionLabel(stringResource(R.string.settings_section_about))
            ListGroup(
                rows = listOf {
                    ListRow(title = stringResource(R.string.settings_version), value = BuildConfig.VERSION_NAME)
                },
            )
        }
    }

    val current = themeMode
    if (showThemeDialog && current != null) {
        ThemeDialog(
            selected = current,
            onSelect = {
                viewModel.setThemeMode(it)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false },
        )
    }
}

@Composable
private fun VehicleSettings(section: VehicleSection, onEditVehicle: (Long) -> Unit) {
    SectionLabel(stringResource(R.string.settings_section_vehicle))
    when (section) {
        VehicleSection.Loading -> LoadingState()
        VehicleSection.None -> ListGroup(
            rows = listOf {
                ListRow(
                    title = stringResource(R.string.settings_add_vehicle),
                    subtitle = stringResource(R.string.settings_add_vehicle_body),
                    onClick = { onEditVehicle(EditVehicleRoute.NEW) },
                )
            },
        )
        is VehicleSection.Loaded -> {
            val v = section.vehicle
            if (v.isSample) {
                NoticeCard(
                    title = stringResource(R.string.sample_notice_title),
                    body = stringResource(R.string.settings_sample_body),
                    actionLabel = stringResource(R.string.settings_edit_vehicle),
                    onAction = { onEditVehicle(v.id) },
                    modifier = Modifier.padding(bottom = Spacing.m),
                )
            }
            ListGroup(
                rows = listOf {
                    ListRow(
                        title = if (v.isSample) stringResource(R.string.vehicle_name_sample, v.name) else v.name,
                        subtitle = vehicleKindSummary(v) + "\n" + vehicleRatesSummary(v),
                        onClick = { onEditVehicle(v.id) },
                    )
                },
            )
        }
    }
}

@Composable
private fun ThemeDialog(selected: ThemeMode, onSelect: (ThemeMode) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_theme)) },
        text = {
            Column(Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = Spacing.touchTarget)
                            .selectable(selected = mode == selected, role = Role.RadioButton, onClick = { onSelect(mode) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // The whole row is the control, so the radio itself takes no clicks.
                        RadioButton(selected = mode == selected, onClick = null)
                        Spacer(Modifier.width(Spacing.m))
                        Text(stringResource(mode.label), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@get:StringRes
private val ThemeMode.label: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    }
