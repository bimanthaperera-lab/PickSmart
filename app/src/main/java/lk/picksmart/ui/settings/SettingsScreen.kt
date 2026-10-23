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
import lk.picksmart.domain.model.Vehicle
import lk.picksmart.domain.usecase.FuelPriceState
import lk.picksmart.domain.usecase.PublishedPriceAction
import lk.picksmart.ui.components.ListGroup
import lk.picksmart.ui.components.LoadingState
import lk.picksmart.ui.components.NoticeCard
import lk.picksmart.ui.components.RecordsGroup
import lk.picksmart.ui.components.ListRow
import lk.picksmart.ui.components.SectionLabel
import lk.picksmart.ui.components.rememberMoneyFormatter
import lk.picksmart.ui.fuelprice.fuelPriceSourceText
import lk.picksmart.ui.navigation.EditVehicleRoute
import lk.picksmart.ui.theme.Spacing
import lk.picksmart.ui.vehicle.ActiveVehicleCard
import lk.picksmart.ui.vehicle.SwitchVehicleDialog
import lk.picksmart.ui.vehicle.VehicleRow

/**
 * The settings list: vehicles, fuel price, records, theme and version. Shown under the profile card
 * on the Account tab, and works the same whether or not the rider is signed in. Adding a vehicle
 * past the limit opens [onVehicleLimit] instead of the form.
 */
@Composable
fun SettingsSections(
    onEditVehicle: (vehicleId: Long) -> Unit,
    onVehicleLimit: () -> Unit,
    onOpenIncome: () -> Unit,
    onOpenFuelLog: () -> Unit,
    onOpenExpenses: () -> Unit,
    onOpenFuelPrice: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val vehicle by viewModel.vehicle.collectAsStateWithLifecycle()
    val fuelPrices by viewModel.fuelPrices.collectAsStateWithLifecycle()
    var showThemeDialog by rememberSaveable { mutableStateOf(false) }

    Column {
        VehicleSettings(vehicle, fuelPrices, onEditVehicle, onVehicleLimit, viewModel::setActiveVehicle, onOpenFuelPrice)
        SectionLabel(stringResource(R.string.settings_section_records))
        RecordsGroup(onOpenFuelLog = onOpenFuelLog, onOpenExpenses = onOpenExpenses, onOpenIncome = onOpenIncome)
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
private fun VehicleSettings(
    section: VehicleSection,
    fuelPrices: FuelPriceState?,
    onEditVehicle: (Long) -> Unit,
    onVehicleLimit: () -> Unit,
    onSwitchVehicle: (Long) -> Unit,
    onOpenFuelPrice: () -> Unit,
) {
    SectionLabel(stringResource(R.string.settings_section_vehicles))
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
            val v = section.active
            if (v.isSample) {
                NoticeCard(
                    title = stringResource(R.string.sample_notice_title),
                    body = stringResource(R.string.settings_sample_body),
                    actionLabel = stringResource(R.string.settings_edit_vehicle),
                    onAction = { onEditVehicle(v.id) },
                    modifier = Modifier.padding(bottom = Spacing.m),
                )
            }
            var choosing by rememberSaveable { mutableStateOf(false) }
            // The vehicle in use, as in the mockup: its name, "Motorcycle · default vehicle", and
            // Switch when there is another one to switch to. Tapping the card edits it.
            ActiveVehicleCard(
                vehicle = v,
                onOpen = { onEditVehicle(v.id) },
                onSwitch = if (section.vehicles.size > 1) ({ choosing = true }) else null,
            )
            val otherRows: List<@Composable () -> Unit> = section.vehicles.filter { it.id != v.id }.map { vehicle ->
                { VehicleRow(vehicle, onClick = { onEditVehicle(vehicle.id) }) }
            }
            val addRow: @Composable () -> Unit = {
                ListRow(
                    title = stringResource(R.string.settings_add_another_vehicle),
                    subtitle = if (section.vehicles.size > 1) null else stringResource(R.string.settings_add_another_vehicle_body),
                    onClick = { if (section.canAdd) onEditVehicle(EditVehicleRoute.NEW) else onVehicleLimit() },
                )
            }
            ListGroup(modifier = Modifier.padding(top = Spacing.m), rows = otherRows + addRow)
            // The fuel price follows the active vehicle (CLAUDE.md section 9, "Vehicles").
            ListGroup(
                modifier = Modifier.padding(top = Spacing.m),
                rows = listOf { FuelPriceRow(v, showName = section.vehicles.size > 1, fuelPrices, onOpenFuelPrice) },
            )
            if (choosing) {
                SwitchVehicleDialog(
                    vehicles = section.vehicles,
                    activeId = v.id,
                    onSelect = {
                        choosing = false
                        onSwitchVehicle(it)
                    },
                    onDismiss = { choosing = false },
                )
            }
        }
    }
}

/**
 * "Rs. 300.00 · Set by you", or the new CPC price while one is waiting for the rider. With more
 * than one vehicle the title names the active one, whose price it is.
 */
@Composable
private fun FuelPriceRow(vehicle: Vehicle, showName: Boolean, fuelPrices: FuelPriceState?, onOpen: () -> Unit) {
    val money = rememberMoneyFormatter()
    val offer = fuelPrices?.action as? PublishedPriceAction.Offer
    ListRow(
        title = if (showName) stringResource(R.string.settings_fuel_price_for, vehicle.name) else stringResource(R.string.settings_fuel_price),
        subtitle = if (offer != null) {
            stringResource(R.string.settings_fuel_price_new, money.format(offer.priceCents))
        } else {
            stringResource(
                R.string.fuel_price_with_source,
                money.format(vehicle.fuelPricePerLitreCents),
                fuelPriceSourceText(vehicle, fuelPrices?.listStale == true),
            )
        },
        onClick = onOpen,
    )
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
