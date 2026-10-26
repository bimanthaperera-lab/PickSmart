package lk.picksmart.ui.fuel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import lk.picksmart.R
import lk.picksmart.ui.vehicle.RecordVehicleField
import lk.picksmart.domain.model.FuelLog
import lk.picksmart.ui.components.DateField
import lk.picksmart.ui.components.DetailHeader
import lk.picksmart.ui.components.EmptyState
import lk.picksmart.ui.components.ErrorState
import lk.picksmart.ui.components.LoadingState
import lk.picksmart.ui.components.NoticeCard
import lk.picksmart.ui.components.NumberField
import lk.picksmart.ui.components.PrimaryButton
import lk.picksmart.ui.components.RecordRow
import lk.picksmart.ui.components.Stat
import lk.picksmart.ui.components.StatGrid
import lk.picksmart.ui.components.SectionLabel
import lk.picksmart.ui.components.SwitchRow
import lk.picksmart.ui.components.message
import lk.picksmart.ui.components.neutralRecordIcon
import lk.picksmart.ui.components.rememberMoneyFormatter
import lk.picksmart.ui.components.rememberRecordDate
import lk.picksmart.ui.theme.Spacing
import lk.picksmart.util.InputDigits
import lk.picksmart.util.decimalToInput
import lk.picksmart.util.formatDecimal
import java.time.LocalDate

/** Fuel log: this month's fuel spending, the actual km/L card, and the fill-ups newest first. */
@Composable
fun FuelLogScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (fuelLogId: Long) -> Unit,
    viewModel: FuelListViewModel = viewModel(factory = FuelListViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val money = rememberMoneyFormatter()
    Column(Modifier.fillMaxSize()) {
        DetailHeader(stringResource(R.string.fuel_title), R.drawable.ic_arrow_back, stringResource(R.string.action_back), onBack)
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            when (val s = state) {
                FuelListUiState.Loading -> item { LoadingState() }
                is FuelListUiState.Loaded -> {
                    item { FuelSummary(s.monthTotalCents, s.efficiency) }
                    s.efficiency?.takeIf { it.suggest }?.let { efficiency ->
                        item { EfficiencySuggestion(efficiency, viewModel::acceptSuggestion, viewModel::dismissSuggestion) }
                    }
                    if (s.entries.isEmpty()) {
                        item {
                            EmptyState(
                                iconRes = R.drawable.ic_add,
                                title = stringResource(R.string.fuel_empty_title),
                                body = stringResource(R.string.fuel_help),
                                modifier = Modifier.padding(top = Spacing.m),
                            )
                        }
                    } else {
                        item { SectionLabel(stringResource(R.string.fuel_recent)) }
                        items(s.entries, key = { it.id }) { entry -> FuelRow(entry, s.today) { onOpen(entry.id) } }
                        item {
                            Text(
                                stringResource(R.string.fuel_help),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = Spacing.m),
                            )
                        }
                    }
                }
            }
        }
        PrimaryButton(
            stringResource(R.string.fuel_add),
            onAdd,
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen, vertical = Spacing.m),
            iconRes = R.drawable.ic_add,
        )
    }
}

/**
 * This month's fuel spending beside the measured km/L, with a line saying what the reading is
 * based on (or how to get one).
 */
@Composable
private fun FuelSummary(monthTotalCents: Long, efficiency: EfficiencyUi?) {
    val money = rememberMoneyFormatter()
    val reading = efficiency?.reading
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        StatGrid(
            listOf(
                Stat(stringResource(R.string.fuel_month_total), money.format(monthTotalCents)),
                Stat(
                    stringResource(R.string.efficiency_title),
                    reading?.let { stringResource(R.string.efficiency_value, formatDecimal(it.kmPerLitre, 1)) }
                        ?: stringResource(R.string.efficiency_unknown),
                ),
            ),
        )
        if (efficiency != null) {
            Text(
                if (reading == null) {
                    stringResource(R.string.efficiency_none)
                } else {
                    stringResource(R.string.efficiency_basis, formatDecimal(reading.km, 0), formatDecimal(reading.litres, 2)) +
                        " " + stringResource(R.string.efficiency_assumes)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.s),
            )
        }
    }
}

/**
 * Offered when the measured km/L differs noticeably from the vehicle's setting. The setting only
 * changes if the rider taps "Update"; "Keep" remembers the choice for this reading.
 */
@Composable
private fun EfficiencySuggestion(efficiency: EfficiencyUi, onAccept: () -> Unit, onDismiss: () -> Unit) {
    val reading = efficiency.reading ?: return
    val setting = decimalToInput(efficiency.vehicleKmPerLitre)
    NoticeCard(
        title = stringResource(R.string.efficiency_suggest_title, formatDecimal(reading.kmPerLitre, 1)),
        body = stringResource(R.string.efficiency_suggest_body, setting),
        iconRes = R.drawable.ic_fuel,
        actionLabel = stringResource(R.string.efficiency_keep, setting),
        onAction = onDismiss,
        primaryLabel = stringResource(R.string.efficiency_update),
        onPrimary = onAccept,
        modifier = Modifier.padding(top = Spacing.s),
    )
}

@Composable
private fun FuelRow(entry: FuelLog, today: LocalDate, onClick: () -> Unit) {
    val money = rememberMoneyFormatter()
    val date = rememberRecordDate(entry.date, today)
    RecordRow(
        icon = neutralRecordIcon(R.drawable.ic_fuel, tint = MaterialTheme.colorScheme.primary),
        title = stringResource(R.string.fuel_row_title, formatDecimal(entry.litres, 2), money.format(entry.pricePerLitreCents)),
        subtitle = when {
            entry.odometerKm != null -> stringResource(R.string.fuel_row_subtitle_odometer, date, formatDecimal(entry.odometerKm, 0))
            entry.fullTank -> date
            else -> stringResource(R.string.fuel_row_subtitle_partial, date)
        },
        amount = money.format(entry.totalCents),
        onClick = onClick,
        tag = if (entry.fullTank) stringResource(R.string.fuel_full_tank) else null,
    )
}

@Composable
fun EditFuelScreen(
    onClose: () -> Unit,
    onAddVehicle: () -> Unit,
    viewModel: EditFuelViewModel = viewModel(factory = EditFuelViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val editing = state as? EditFuelUiState.Editing
    LaunchedEffect(editing?.done) { if (editing?.done == true) onClose() }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        DetailHeader(
            stringResource(if (editing?.isNew == false) R.string.fuel_edit_title else R.string.fuel_add_title),
            R.drawable.ic_close,
            stringResource(R.string.action_close),
            onClose,
        ) {
            if (editing?.isNew == false) {
                IconButton(onClick = { confirmDelete = true }, enabled = !editing.saving) {
                    Icon(
                        painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.fuel_delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            when (val s = state) {
                EditFuelUiState.Loading -> LoadingState()
                EditFuelUiState.NotFound -> EmptyState(
                    iconRes = R.drawable.ic_info,
                    title = stringResource(R.string.fuel_not_found_title),
                    body = stringResource(R.string.fuel_not_found_body),
                    actionLabel = stringResource(R.string.action_close),
                    onAction = onClose,
                )
                EditFuelUiState.NoVehicle -> EmptyState(
                    iconRes = R.drawable.ic_nav_check_ride,
                    title = stringResource(R.string.settings_add_vehicle),
                    body = stringResource(R.string.fuel_no_vehicle_body),
                    actionLabel = stringResource(R.string.settings_add_vehicle),
                    onAction = onAddVehicle,
                )
                is EditFuelUiState.Editing -> {
                    RecordVehicleField(s.vehicle, viewModel::selectVehicle)
                    FuelForm(s, viewModel::updateForm)
                }
            }
        }
        if (editing != null) {
            Column(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.m)) {
                if (editing.saveFailed) ErrorState(stringResource(R.string.fuel_save_failed), Modifier.padding(bottom = Spacing.m))
                PrimaryButton(stringResource(R.string.fuel_save), viewModel::save, Modifier.fillMaxWidth(), enabled = !editing.saving)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.fuel_delete_title)) },
            text = { Text(stringResource(R.string.fuel_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun FuelForm(state: EditFuelUiState.Editing, onChange: (FuelFormState) -> Unit) {
    val form = state.form
    val errors = state.errors
    val workedOut = stringResource(R.string.fuel_worked_out)
    NumberField(
        value = form.total,
        onValueChange = { onChange(form.withTotal(it)) },
        label = stringResource(R.string.fuel_total),
        prefix = stringResource(R.string.currency_prefix),
        supportingText = workedOut.takeIf { form.typed == FuelAmount.LITRES && form.total.isNotEmpty() },
        error = errors[FuelField.TOTAL]?.message(R.string.fuel_error_total_required),
    )
    NumberField(
        value = form.litres,
        onValueChange = { onChange(form.withLitres(it)) },
        label = stringResource(R.string.fuel_litres),
        suffix = stringResource(R.string.unit_litre_short),
        maxIntegerDigits = InputDigits.LITRES,
        supportingText = workedOut.takeIf { form.typed == FuelAmount.TOTAL && form.litres.isNotEmpty() },
        error = errors[FuelField.LITRES]?.message(R.string.fuel_error_litres_required),
    )
    NumberField(
        value = form.price,
        onValueChange = { onChange(form.withPrice(it)) },
        label = stringResource(R.string.fuel_price),
        prefix = stringResource(R.string.currency_prefix),
        supportingText = stringResource(R.string.fuel_price_help),
        error = errors[FuelField.PRICE]?.message(R.string.fuel_error_price_required),
    )
    SwitchRow(
        title = stringResource(R.string.fuel_full_tank),
        body = stringResource(R.string.fuel_full_tank_body),
        checked = form.fullTank,
        onCheckedChange = { onChange(form.copy(fullTank = it)) },
    )
    NumberField(
        value = form.odometer,
        onValueChange = { onChange(form.copy(odometer = it)) },
        label = stringResource(R.string.fuel_odometer),
        suffix = stringResource(R.string.unit_km),
        maxDecimals = 1,
        maxIntegerDigits = InputDigits.ODOMETER,
        supportingText = stringResource(R.string.fuel_odometer_help),
        error = errors[FuelField.ODOMETER]?.message(R.string.vehicle_error_invalid),
        imeAction = ImeAction.Done,
    )
    DateField(stringResource(R.string.fuel_date), form.date, { onChange(form.copy(date = it)) }, Modifier.fillMaxWidth(), maxDate = state.today)
}
