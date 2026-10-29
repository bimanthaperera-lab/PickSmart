package lk.picksmart.ui.trip

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import lk.picksmart.R
import lk.picksmart.ui.vehicle.RecordVehicleField
import lk.picksmart.domain.calculator.InputField
import lk.picksmart.domain.model.CommissionType
import lk.picksmart.domain.model.FareMode
import lk.picksmart.domain.model.Platform
import lk.picksmart.domain.model.TripRates
import lk.picksmart.domain.model.TripStatus
import lk.picksmart.ui.checkride.CheckRideError
import lk.picksmart.ui.checkride.CheckRideFormState
import lk.picksmart.ui.checkride.CheckRideOutcome
import lk.picksmart.ui.checkride.FareBox
import lk.picksmart.ui.checkride.FareModeSelector
import lk.picksmart.ui.checkride.FeeUnitToggle
import lk.picksmart.ui.checkride.InputSection
import lk.picksmart.ui.checkride.NumberTile
import lk.picksmart.ui.checkride.STACK_FONT_SCALE
import lk.picksmart.ui.checkride.TileRow
import lk.picksmart.ui.checkride.TotalKmPill
import lk.picksmart.ui.checkride.addToFare
import lk.picksmart.ui.checkride.text
import lk.picksmart.ui.components.ChoiceChips
import lk.picksmart.ui.components.DateField
import lk.picksmart.ui.components.EmptyState
import lk.picksmart.ui.components.ErrorState
import lk.picksmart.ui.components.LoadingState
import lk.picksmart.ui.components.NoticeCard
import lk.picksmart.ui.components.PicksmartCard
import lk.picksmart.ui.components.PrimaryButton
import lk.picksmart.ui.components.ScreenTitle
import lk.picksmart.ui.components.SwitchRow
import lk.picksmart.ui.components.TimeField
import lk.picksmart.ui.components.rememberMoneyFormatter
import lk.picksmart.ui.components.style
import lk.picksmart.ui.theme.Spacing
import lk.picksmart.util.InputDigits
import lk.picksmart.util.decimalToInput

@Composable
fun EditTripScreen(
    onClose: () -> Unit,
    onAddVehicle: () -> Unit,
    viewModel: EditTripViewModel = viewModel(factory = EditTripViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val editing = state as? EditTripUiState.Editing
    LaunchedEffect(editing?.done) { if (editing?.done == true) onClose() }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = Spacing.s, end = Spacing.s, top = Spacing.s),
        ) {
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_close))
            }
            ScreenTitle(
                stringResource(if (editing?.isNew == false) R.string.trip_edit_title else R.string.trip_add_title),
                Modifier
                    .weight(1f)
                    .padding(start = Spacing.s),
            )
            if (editing?.isNew == false) {
                IconButton(onClick = { confirmDelete = true }, enabled = !editing.saving) {
                    Icon(
                        painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.trip_delete),
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
                EditTripUiState.Loading -> LoadingState()
                EditTripUiState.NotFound -> EmptyState(
                    iconRes = R.drawable.ic_info,
                    title = stringResource(R.string.trip_not_found_title),
                    body = stringResource(R.string.trip_not_found_body),
                    actionLabel = stringResource(R.string.action_close),
                    onAction = onClose,
                )
                EditTripUiState.NoVehicle -> EmptyState(
                    iconRes = R.drawable.ic_nav_check_ride,
                    title = stringResource(R.string.settings_add_vehicle),
                    body = stringResource(R.string.trip_no_vehicle_body),
                    actionLabel = stringResource(R.string.settings_add_vehicle),
                    onAction = onAddVehicle,
                )
                is EditTripUiState.Editing -> {
                    RecordVehicleField(s.vehicle, viewModel::selectVehicle)
                    TripForm(s, viewModel::updateForm, viewModel::setSaveTip)
                }
            }
        }

        if (editing != null) {
            Column(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.m)) {
                if (editing.saveFailed) ErrorState(stringResource(R.string.trip_save_failed), Modifier.padding(bottom = Spacing.m))
                PrimaryButton(
                    stringResource(R.string.trip_save),
                    viewModel::save,
                    Modifier.fillMaxWidth(),
                    enabled = !editing.saving,
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.trip_delete_title)) },
            text = { Text(stringResource(R.string.trip_delete_body)) },
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
 * The trip's fields in the same cards and tiles as Check a Ride: date and time tiles, the fare
 * card (fare box, fee and other costs), the distance card, then the note.
 */
@Composable
private fun TripForm(state: EditTripUiState.Editing, onChange: (TripFormState) -> Unit, onSaveTip: (Boolean) -> Unit) {
    val form = state.form
    val ride = form.ride
    val errors = state.shownErrors
    val currency = stringResource(R.string.currency_prefix)
    val km = stringResource(R.string.unit_km)
    val stacked = LocalDensity.current.fontScale > STACK_FONT_SCALE
    val cancelled = form.status == TripStatus.CANCELLED

    ChoiceChips(
        options = Platform.entries,
        selected = ride.platform,
        optionLabel = { stringResource(it.label) },
        onSelect = { onChange(form.copy(ride = ride.copy(platform = it))) },
    )
    StatusSelector(state.statuses, form.status) { onChange(form.copy(status = it)) }
    if (form.status == TripStatus.OFFER_CHECK) {
        NoticeCard(title = stringResource(R.string.trip_status_offer), body = stringResource(R.string.trip_offer_notice))
    }
    if (state.fromOffer) {
        NoticeCard(title = stringResource(R.string.trip_from_offer_title), body = stringResource(R.string.trip_from_offer_notice))
    }
    if (state.offerTipCents > 0 && form.status == TripStatus.COMPLETED) {
        SwitchRow(
            title = stringResource(R.string.trip_offer_tip_title, rememberMoneyFormatter().format(state.offerTipCents)),
            body = stringResource(R.string.trip_offer_tip_body),
            checked = state.saveTip,
            onCheckedChange = onSaveTip,
        )
    }

    TileRow(
        stacked,
        listOf(
            { m ->
                DateField(
                    stringResource(R.string.trip_date),
                    form.date,
                    { onChange(form.copy(date = it)) },
                    m,
                    maxDate = state.today,
                    tile = true,
                )
            },
            { m -> TimeField(stringResource(R.string.trip_time), form.time, { onChange(form.copy(time = it)) }, m, tile = true) },
        ),
    )

    val fareFocus = remember { FocusRequester() }
    if (cancelled) {
        val feeLabel = stringResource(R.string.trip_cancellation_fee)
        val feeError = errors.text(InputField.FARE)
        InputSection(title = feeLabel) {
            FareBox(
                value = form.cancellationFee,
                onValueChange = { onChange(form.copy(cancellationFee = it)) },
                label = feeLabel,
                focusRequester = fareFocus,
                error = feeError,
                stacked = stacked,
                // The same adding (and digit limit) as the fare's buttons.
                onAdd = { onChange(form.copy(cancellationFee = CheckRideFormState(fare = form.cancellationFee).addToFare(it).fare)) },
            )
            if (feeError != null) ErrorText(feeError)
            HelpText(stringResource(R.string.trip_cancellation_fee_help))
        }
        InputSection(title = stringResource(R.string.check_result_distance)) {
            TileRow(
                stacked,
                listOf(
                    { m ->
                        NumberTile(
                            value = ride.pickupKm,
                            onValueChange = { onChange(form.copy(ride = ride.copy(pickupKm = it))) },
                            label = stringResource(R.string.trip_pickup_driven),
                            modifier = m,
                            suffix = km,
                            maxDecimals = 1,
                            maxIntegerDigits = InputDigits.DISTANCE,
                            error = errors.text(InputField.PICKUP_KM),
                            highlighted = true,
                        )
                    },
                    { m ->
                        NumberTile(
                            value = ride.returnKm,
                            onValueChange = { onChange(form.copy(ride = ride.copy(returnKm = it))) },
                            label = stringResource(R.string.check_return_optional),
                            modifier = m,
                            suffix = km,
                            maxDecimals = 1,
                            maxIntegerDigits = InputDigits.DISTANCE,
                            error = errors.text(InputField.RETURN_KM),
                        )
                    },
                ),
            )
        }
    } else {
        val payout = ride.fareMode == FareMode.PAYOUT_AFTER_COMMISSION
        val calculated = (state.outcome as? CheckRideOutcome.Calculated)?.result
        FareModeSelector(ride.fareMode) { onChange(form.copy(ride = ride.copy(fareMode = it))) }
        val fareLabel = stringResource(
            when {
                payout -> R.string.check_fare_payout
                form.status == TripStatus.OFFER_CHECK -> R.string.check_fare_gross
                else -> R.string.trip_fare_received
            },
        )
        val fareError = errors[InputField.FARE]?.let {
            if (it == CheckRideError.REQUIRED) stringResource(R.string.trip_error_fare_required) else errors.text(InputField.FARE)
        }
        InputSection(title = fareLabel) {
            FareBox(
                value = ride.fare,
                onValueChange = { onChange(form.copy(ride = ride.copy(fare = it))) },
                label = fareLabel,
                focusRequester = fareFocus,
                error = fareError,
                stacked = stacked,
                onAdd = { onChange(form.copy(ride = ride.addToFare(it))) },
            )
            if (fareError != null) ErrorText(fareError)
            if (payout) HelpText(stringResource(R.string.check_fare_payout_help))
            TileRow(
                stacked,
                buildList {
                    if (!payout) {
                        add { m ->
                            // In percent, the fee in rupees for this fare, once there is a result.
                            val feeCents = calculated?.commissionCents
                                ?.takeIf { ride.commissionType == CommissionType.PERCENT && it > 0 }
                            NumberTile(
                                value = ride.commission,
                                onValueChange = { onChange(form.copy(ride = ride.copy(commission = it))) },
                                label = stringResource(R.string.check_fee),
                                modifier = m,
                                error = errors.text(InputField.COMMISSION),
                                note = feeCents?.let { stringResource(R.string.check_fee_equivalent, rememberMoneyFormatter().format(it)) },
                                trailing = {
                                    FeeUnitToggle(ride.commissionType, size = 36.dp) {
                                        onChange(form.copy(ride = ride.copy(commissionType = it)))
                                    }
                                },
                            )
                        }
                    }
                    add { m ->
                        NumberTile(
                            value = ride.otherCosts,
                            onValueChange = { onChange(form.copy(ride = ride.copy(otherCosts = it))) },
                            label = stringResource(R.string.check_other_costs),
                            modifier = m,
                            prefix = currency,
                            error = errors.text(InputField.OTHER_COSTS),
                        )
                    }
                },
            )
        }
        InputSection(
            title = stringResource(R.string.check_result_distance),
            action = calculated?.totalKm?.let { total -> { _ -> TotalKmPill(total) } },
        ) {
            TileRow(
                stacked,
                listOf(
                    { m ->
                        NumberTile(
                            value = ride.pickupKm,
                            onValueChange = { onChange(form.copy(ride = ride.copy(pickupKm = it))) },
                            label = stringResource(R.string.check_pickup),
                            modifier = m,
                            suffix = km,
                            maxDecimals = 1,
                            maxIntegerDigits = InputDigits.DISTANCE,
                            error = errors.text(InputField.PICKUP_KM),
                        )
                    },
                    { m ->
                        NumberTile(
                            value = ride.tripKm,
                            onValueChange = { onChange(form.copy(ride = ride.copy(tripKm = it))) },
                            label = stringResource(R.string.check_trip),
                            modifier = m,
                            suffix = km,
                            maxDecimals = 1,
                            maxIntegerDigits = InputDigits.DISTANCE,
                            error = errors.text(InputField.TRIP_KM),
                            highlighted = true,
                        )
                    },
                    { m ->
                        NumberTile(
                            value = ride.returnKm,
                            onValueChange = { onChange(form.copy(ride = ride.copy(returnKm = it))) },
                            label = stringResource(R.string.check_return_optional),
                            modifier = m,
                            suffix = km,
                            maxDecimals = 1,
                            maxIntegerDigits = InputDigits.DISTANCE,
                            error = errors.text(InputField.RETURN_KM),
                        )
                    },
                ),
            )
        }
    }

    val notesLabel = stringResource(R.string.trip_notes)
    InputSection(title = notesLabel) {
        NoteBox(form.notes, { onChange(form.copy(notes = it)) }, notesLabel)
    }

    RatesLine(state.rates)
    NetResult(state.outcome, isOffer = form.status == TripStatus.OFFER_CHECK)
}

@Composable
private fun StatusSelector(statuses: List<TripStatus>, selected: TripStatus, onSelect: (TripStatus) -> Unit) {
    // Segments split the width, so at large font sizes "Completed" would break mid-word; chips scroll instead.
    if (LocalDensity.current.fontScale > STACK_FONT_SCALE) {
        ChoiceChips(statuses, selected, { stringResource(it.label) }, onSelect)
        return
    }
    SingleChoiceSegmentedButtonRow(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        statuses.forEachIndexed { index, status ->
            SegmentedButton(
                selected = status == selected,
                onClick = { onSelect(status) },
                shape = SegmentedButtonDefaults.itemShape(index, statuses.size),
                modifier = Modifier
                    .fillMaxHeight()
                    .defaultMinSize(minHeight = Spacing.touchTarget),
                label = { Text(stringResource(status.label), textAlign = TextAlign.Center) },
            )
        }
    }
}

/** The fare box marks an error with its outline only; the message goes under it. */
@Composable
private fun ErrorText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun HelpText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * The note, in the same box as the number tiles: a few lines, up to [NOTES_MAX_LENGTH]
 * characters, with the brand outline while it has focus.
 */
@Composable
private fun NoteBox(value: String, onValueChange: (String) -> Unit, label: String) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val border = if (focused) BorderStroke(2.dp, colors.primary) else BorderStroke(1.dp, colors.outlineVariant)
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(it.take(NOTES_MAX_LENGTH)) },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        minLines = 2,
        maxLines = 5,
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = label },
        decorationBox = { field ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(colors.surface)
                    .border(border, shape)
                    .padding(horizontal = Spacing.m, vertical = Spacing.s + 2.dp),
            ) { field() }
        },
    )
}

/** The rate snapshot this trip is calculated with. It never follows later vehicle edits. */
@Composable
internal fun RatesLine(rates: TripRates) {
    val money = rememberMoneyFormatter()
    Text(
        stringResource(
            R.string.trip_rates_summary,
            money.format(rates.fuelPricePerLitreCents),
            decimalToInput(rates.fuelEfficiencyKmPerLitre),
            money.format(rates.maintenanceReservePerKmCents),
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun NetResult(outcome: CheckRideOutcome, isOffer: Boolean) {
    val calculated = outcome as? CheckRideOutcome.Calculated
    if (calculated == null) {
        // Field errors are shown on the fields; a too-large result needs saying here.
        if ((outcome as? CheckRideOutcome.Invalid)?.resultTooLarge == true) {
            PicksmartCard(Modifier.fillMaxWidth()) { Text(stringResource(R.string.check_result_too_large)) }
        }
        return
    }
    val result = calculated.result
    val style = result.band.style()
    val money = rememberMoneyFormatter()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.large,
        color = style.container,
        border = BorderStroke(1.dp, style.content.copy(alpha = 0.4f)),
    ) {
        FlowRow(
            Modifier.padding(Spacing.l),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(if (isOffer) R.string.trip_net_estimate_offer else R.string.trip_net_profit),
                style = MaterialTheme.typography.bodyMedium,
                color = style.onContainer,
            )
            Spacer(Modifier.weight(1f))
            Text(money.format(result.netProfitCents), style = MaterialTheme.typography.titleLarge, color = style.content)
            result.netProfitPerKmCents?.let {
                Text(
                    stringResource(R.string.check_per_km_value, money.format(it)),
                    style = MaterialTheme.typography.titleSmall,
                    color = style.content,
                )
            }
        }
    }
}

private const val NOTES_MAX_LENGTH = 200
