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
import lk.picksmart.ui.checkride.ErrorText
import lk.picksmart.ui.checkride.HelpText
import lk.picksmart.ui.checkride.InputSection
import lk.picksmart.ui.checkride.NumberTile
import lk.picksmart.ui.checkride.QuickAddRow
import lk.picksmart.ui.checkride.STACK_FONT_SCALE
import lk.picksmart.ui.checkride.TileRow
import lk.picksmart.ui.checkride.ValueBox
import lk.picksmart.util.addRupeesToInput
import lk.picksmart.util.parseDecimal
import lk.picksmart.util.parseRupeesToCents
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import lk.picksmart.ui.vehicle.RecordVehicleField
import lk.picksmart.domain.model.FuelLog
import lk.picksmart.ui.components.DateField
import lk.picksmart.ui.components.DetailHeader
import lk.picksmart.ui.components.EmptyState
import lk.picksmart.ui.components.ErrorState
import lk.picksmart.ui.components.LoadingState
import lk.picksmart.ui.components.NoticeCard
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
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
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
                    RecordVehicleField(s.vehicle, card = true)
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

/**
 * Add / Edit fill-up in Check a Ride's card style: what was paid (with quick add), the litres and
 * price per litre side by side with the sum they make, full tank, the odometer and the date.
 * Typing the amount or the litres fills in the other from the price ([withTotal], [withLitres]).
 */
@Composable
private fun FuelForm(state: EditFuelUiState.Editing, onChange: (FuelFormState) -> Unit) {
    val form = state.form
    val errors = state.errors
    val currency = stringResource(R.string.currency_prefix)
    val workedOut = stringResource(R.string.fuel_worked_out)
    val stacked = LocalDensity.current.fontScale > STACK_FONT_SCALE

    val totalLabel = stringResource(R.string.fuel_total)
    val totalError = errors[FuelField.TOTAL]?.message(R.string.fuel_error_total_required)
    InputSection(title = totalLabel) {
        ValueBox(
            value = form.total,
            onValueChange = { onChange(form.withTotal(it)) },
            label = totalLabel,
            error = totalError,
            prefix = currency,
            textStyle = MaterialTheme.typography.headlineMedium,
            onClear = { onChange(form.withTotal("")) },
        )
        if (totalError != null) ErrorText(totalError)
        if (form.typed == FuelAmount.LITRES && form.total.isNotEmpty()) HelpText(workedOut)
        QuickAddRow(
            onAdd = { onChange(form.withTotal(addRupeesToInput(form.total, it))) },
            amounts = FUEL_QUICK_ADD_RUPEES,
        )
    }

    InputSection(title = stringResource(R.string.fuel_litres_and_price)) {
        TileRow(
            stacked,
            listOf(
                { m ->
                    NumberTile(
                        value = form.litres,
                        onValueChange = { onChange(form.withLitres(it)) },
                        label = stringResource(R.string.fuel_litres),
                        modifier = m,
                        suffix = stringResource(R.string.unit_litre_short),
                        maxIntegerDigits = InputDigits.LITRES,
                        error = errors[FuelField.LITRES]?.message(R.string.fuel_error_litres_required),
                        note = stringResource(R.string.fuel_auto).takeIf { form.typed == FuelAmount.TOTAL && form.litres.isNotEmpty() },
                    )
                },
                { m ->
                    NumberTile(
                        value = form.price,
                        onValueChange = { onChange(form.withPrice(it)) },
                        label = stringResource(R.string.fuel_price),
                        modifier = m,
                        prefix = currency,
                        error = errors[FuelField.PRICE]?.message(R.string.fuel_error_price_required),
                        highlighted = true,
                    )
                },
            ),
        )
        FuelSumLine(form)
        HelpText(stringResource(R.string.fuel_price_help))
    }

    SwitchRow(
        title = stringResource(R.string.fuel_full_tank),
        body = stringResource(R.string.fuel_full_tank_body),
        checked = form.fullTank,
        onCheckedChange = { onChange(form.copy(fullTank = it)) },
    )

    val odometerLabel = stringResource(R.string.fuel_odometer)
    val odometerError = errors[FuelField.ODOMETER]?.message(R.string.vehicle_error_invalid)
    InputSection(title = odometerLabel) {
        ValueBox(
            value = form.odometer,
            onValueChange = { onChange(form.copy(odometer = it)) },
            label = odometerLabel,
            error = odometerError,
            unit = stringResource(R.string.unit_km),
            leadingIcon = R.drawable.ic_gauge,
            maxDecimals = 1,
            maxIntegerDigits = InputDigits.ODOMETER,
            imeAction = ImeAction.Done,
        )
        if (odometerError != null) ErrorText(odometerError)
        HelpText(stringResource(R.string.fuel_odometer_help))
    }

    DateField(
        stringResource(R.string.fuel_date),
        form.date,
        { onChange(form.copy(date = it)) },
        Modifier.fillMaxWidth(),
        maxDate = state.today,
        tile = true,
    )
}

/**
 * "4.83 L × Rs. 414.00 = Rs. 2,000.00": how the litres, price and amount paid fit together, once
 * all three are numbers. One of them is worked out from the others, so the rider can check it.
 */
@Composable
private fun FuelSumLine(form: FuelFormState) {
    val litres = parseDecimal(form.litres)?.takeIf { it > 0 } ?: return
    val priceCents = parseRupeesToCents(form.price)?.takeIf { it > 0 } ?: return
    val totalCents = parseRupeesToCents(form.total)?.takeIf { it > 0 } ?: return
    val money = rememberMoneyFormatter()
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Icon(
            painterResource(R.drawable.ic_info),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            stringResource(
                R.string.fuel_sum_line,
                formatDecimal(litres, 2),
                stringResource(R.string.unit_litre_short),
                money.format(priceCents),
                money.format(totalCents),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Quick-add amounts for a fill-up, in rupees: riders often ask for a round sum of fuel. */
private val FUEL_QUICK_ADD_RUPEES = listOf(500L, 1_000L, 1_500L, 2_000L)
