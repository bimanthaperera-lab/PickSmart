package lk.picksmart.ui.checkride

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import lk.picksmart.R
import lk.picksmart.domain.calculator.InputField
import lk.picksmart.domain.calculator.ResultBand
import lk.picksmart.domain.model.CommissionType
import lk.picksmart.domain.model.FareMode
import lk.picksmart.domain.model.Platform
import lk.picksmart.domain.model.TripStatus
import lk.picksmart.domain.model.Vehicle
import lk.picksmart.ui.components.ChoiceChips
import lk.picksmart.ui.components.EmptyState
import lk.picksmart.ui.components.LoadingState
import lk.picksmart.ui.components.NumberField
import lk.picksmart.util.centsToInput
import lk.picksmart.util.parseRupeesToCents
import lk.picksmart.ui.components.PicksmartCard
import lk.picksmart.ui.components.PrimaryButton
import lk.picksmart.ui.components.ScreenTitle
import lk.picksmart.ui.settings.OfferReaderSwitch
import lk.picksmart.ui.components.SecondaryButton
import lk.picksmart.ui.components.TabScreen
import lk.picksmart.ui.components.rememberMoneyFormatter
import lk.picksmart.ui.components.style
import lk.picksmart.ui.theme.PicksmartTheme
import lk.picksmart.ui.theme.Spacing
import lk.picksmart.ui.trip.label
import lk.picksmart.ui.vehicle.VehicleRates
import lk.picksmart.ui.vehicle.icon
import lk.picksmart.util.formatDecimal

/**
 * Check a Ride (CLAUDE.md section 9): the fare is focused on arrival, the rest is pre-filled, and
 * the result recalculates as the rider types. The result is pinned to the bottom, above the
 * keyboard, so it is visible without scrolling even on a small phone.
 */
@Composable
fun CheckRideScreen(
    onAddVehicle: () -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: CheckRideViewModel = viewModel(factory = CheckRideViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showTargetDialog by rememberSaveable { mutableStateOf(false) }
    val resources = LocalResources.current
    LaunchedEffect(viewModel) { viewModel.onShown() }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val text = resources.getString(
                when (message) {
                    CheckRideMessage.OFFER_SAVED -> R.string.check_saved_offer
                    CheckRideMessage.TRIP_SAVED -> R.string.check_saved_trip
                    CheckRideMessage.SAVE_FAILED -> R.string.check_save_failed
                    CheckRideMessage.TARGET_SAVE_FAILED -> R.string.target_dialog_failed
                },
            )
            val failed = message == CheckRideMessage.SAVE_FAILED || message == CheckRideMessage.TARGET_SAVE_FAILED
            val action = if (failed) null else resources.getString(R.string.action_view)
            val result = snackbar.showSnackbar(text, actionLabel = action, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) onOpenHistory()
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        // At large font sizes the result could crowd out the form; past this it scrolls inside.
        val panelMaxHeight = maxHeight * 0.55f
        Column(Modifier.fillMaxSize()) {
            TabScreen(Modifier.weight(1f)) {
                // The offer reader's switch sits beside the title, so it takes no height from the
                // result (CLAUDE.md section 9); at large font sizes it moves under it.
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    ScreenTitle(stringResource(R.string.check_ride_title))
                    if (state is CheckRideUiState.Ready) OfferReaderSwitch()
                }
                when (val s = state) {
                    CheckRideUiState.Loading -> LoadingState()
                    CheckRideUiState.NoVehicle -> EmptyState(
                        iconRes = R.drawable.ic_nav_check_ride,
                        title = stringResource(R.string.settings_add_vehicle),
                        body = stringResource(R.string.settings_add_vehicle_body),
                        actionLabel = stringResource(R.string.settings_add_vehicle),
                        onAction = onAddVehicle,
                    )
                    is CheckRideUiState.Ready -> CheckRideForm(
                        state = s,
                        onChange = viewModel::updateForm,
                        onSwitchVehicle = viewModel::switchVehicle,
                        onChangeRates = { viewModel.startOverrides(s.vehicle) },
                        onUseVehicleRates = viewModel::clearOverrides,
                    )
                }
            }
            (state as? CheckRideUiState.Ready)?.let {
                ResultPanel(
                    it,
                    onSave = viewModel::save,
                    onEditTarget = { showTargetDialog = true },
                    modifier = Modifier
                        .padding(horizontal = Spacing.screen)
                        .padding(bottom = Spacing.m)
                        .heightIn(max = panelMaxHeight),
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    val ready = state as? CheckRideUiState.Ready
    if (showTargetDialog && ready != null) {
        TargetDialog(
            currentCents = ready.vehicle.targetProfitPerKmCents,
            onSave = {
                viewModel.setTarget(it)
                showTargetDialog = false
            },
            onDismiss = { showTargetDialog = false },
        )
    }
}

@Composable
private fun CheckRideForm(
    state: CheckRideUiState.Ready,
    onChange: (CheckRideFormState) -> Unit,
    onSwitchVehicle: (vehicleId: Long) -> Unit,
    onChangeRates: () -> Unit,
    onUseVehicleRates: () -> Unit,
) {
    val form = state.form
    val errors = (state.outcome as? CheckRideOutcome.Invalid)?.errors.orEmpty()
    val currency = stringResource(R.string.currency_prefix)
    val km = stringResource(R.string.unit_km)
    val zero = stringResource(R.string.placeholder_zero)
    val payout = form.fareMode == FareMode.PAYOUT_AFTER_COMMISSION
    // Side-by-side fields get too narrow for their labels at large font sizes.
    val stacked = LocalDensity.current.fontScale > STACK_FONT_SCALE

    val fareFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        fareFocus.requestFocus()
        keyboard?.show()
    }

    // One tap switches vehicle; only shown when there is more than one.
    if (state.vehicles.size > 1) {
        ChoiceChips(
            options = state.vehicles,
            selected = state.vehicles.firstOrNull { it.id == state.vehicle.id },
            optionLabel = { it.name },
            optionIcon = { it.type.icon },
            onSelect = { if (it.id != state.vehicle.id) onSwitchVehicle(it.id) },
        )
    }

    ChoiceChips(
        options = Platform.entries,
        selected = form.platform,
        optionLabel = { stringResource(it.label) },
        onSelect = { onChange(form.copy(platform = it)) },
    )

    FareModeSelector(form.fareMode) { onChange(form.copy(fareMode = it)) }

    NumberField(
        value = form.fare,
        onValueChange = { onChange(form.copy(fare = it)) },
        label = stringResource(if (payout) R.string.check_fare_payout else R.string.check_fare_gross),
        modifier = Modifier.focusRequester(fareFocus),
        prefix = currency,
        supportingText = if (payout) stringResource(R.string.check_fare_payout_help) else null,
        error = errors.text(InputField.FARE),
        trailing = if (form.fare.isEmpty()) {
            null
        } else {
            {
                IconButton(onClick = {
                    onChange(form.copy(fare = ""))
                    fareFocus.requestFocus()
                }) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.check_clear_fare))
                }
            }
        },
    )

    FieldRow(
        stacked,
        buildList {
            if (!payout) {
                add { m ->
                    NumberField(
                        value = form.commission,
                        onValueChange = { onChange(form.copy(commission = it)) },
                        label = stringResource(R.string.check_fee),
                        modifier = m,
                        placeholder = zero,
                        error = errors.text(InputField.COMMISSION),
                        trailing = {
                            FeeUnitToggle(form.commissionType) { onChange(form.copy(commissionType = it)) }
                        },
                    )
                }
            }
            add { m ->
                NumberField(
                    value = form.otherCosts,
                    onValueChange = { onChange(form.copy(otherCosts = it)) },
                    label = stringResource(R.string.check_other_costs),
                    modifier = m,
                    placeholder = zero,
                    prefix = currency,
                    error = errors.text(InputField.OTHER_COSTS),
                )
            }
        },
    )

    FieldRow(
        stacked,
        listOf(
            { m ->
                NumberField(
                    value = form.pickupKm,
                    onValueChange = { onChange(form.copy(pickupKm = it)) },
                    label = stringResource(R.string.check_pickup),
                    modifier = m,
                    placeholder = zero,
                    suffix = km,
                    maxDecimals = 1,
                    error = errors.text(InputField.PICKUP_KM),
                )
            },
            { m ->
                NumberField(
                    value = form.tripKm,
                    onValueChange = { onChange(form.copy(tripKm = it)) },
                    label = stringResource(R.string.check_trip),
                    modifier = m,
                    placeholder = zero,
                    suffix = km,
                    maxDecimals = 1,
                    error = errors.text(InputField.TRIP_KM),
                )
            },
            { m ->
                NumberField(
                    value = form.returnKm,
                    onValueChange = { onChange(form.copy(returnKm = it)) },
                    label = stringResource(R.string.check_return),
                    modifier = m,
                    placeholder = zero,
                    suffix = km,
                    maxDecimals = 1,
                    supportingText = stringResource(R.string.check_optional),
                    error = errors.text(InputField.RETURN_KM),
                    imeAction = if (form.overrides == null) ImeAction.Done else ImeAction.Next,
                )
            },
        ),
    )

    val overrides = form.overrides
    if (overrides == null) {
        VehicleRatesLine(state.vehicle, onChangeRates)
    } else {
        RateOverridesCard(
            overrides = overrides,
            errors = errors,
            onChange = { onChange(form.copy(overrides = it)) },
            onUseVehicleRates = onUseVehicleRates,
        )
    }

    SafetyLine()
}

/**
 * "Estimate only. Check offers when you're stopped safely.", starting with a small shield. The
 * shield sits inside the text, so it stays next to the first word when the line wraps.
 */
@Composable
private fun SafetyLine() {
    val text = buildAnnotatedString {
        appendInlineContent(SHIELD_ID)
        append(" ")
        append(stringResource(R.string.check_safety_line))
    }
    val shield = mapOf(
        SHIELD_ID to InlineTextContent(Placeholder(1.3.em, 1.3.em, PlaceholderVerticalAlign.TextCenter)) {
            // Decorative: the text says it all.
            Icon(
                painterResource(R.drawable.ic_shield),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxSize(),
            )
        },
    )
    Text(
        text,
        inlineContent = shield,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

private const val SHIELD_ID = "shield"

@Composable
internal fun FareModeSelector(selected: FareMode, onSelect: (FareMode) -> Unit) {
    val modes = FareMode.entries
    // One shared height, so a label that wraps at large font sizes doesn't leave the other segment short.
    SingleChoiceSegmentedButtonRow(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        modes.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                modifier = Modifier
                    .fillMaxHeight()
                    .defaultMinSize(minHeight = Spacing.touchTarget),
                label = {
                    Text(
                        stringResource(
                            when (mode) {
                                FareMode.GROSS_BEFORE_COMMISSION -> R.string.check_fare_mode_gross
                                FareMode.PAYOUT_AFTER_COMMISSION -> R.string.check_fare_mode_payout
                            },
                        ),
                        textAlign = TextAlign.Center,
                    )
                },
            )
        }
    }
}

/** The "Rs." / "%" switch inside the fee field. */
@Composable
internal fun FeeUnitToggle(type: CommissionType, onSelect: (CommissionType) -> Unit) {
    val amount = type == CommissionType.AMOUNT
    val description = stringResource(if (amount) R.string.check_fee_in_rupees else R.string.check_fee_in_percent)
    val actionLabel = stringResource(if (amount) R.string.check_fee_switch_to_percent else R.string.check_fee_switch_to_rupees)
    val shape = MaterialTheme.shapes.small
    Box(
        Modifier
            .padding(end = Spacing.xs)
            .defaultMinSize(minWidth = Spacing.touchTarget, minHeight = Spacing.touchTarget)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, shape)
            .clickable(role = Role.Button, onClickLabel = actionLabel) {
                onSelect(if (amount) CommissionType.PERCENT else CommissionType.AMOUNT)
            }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(if (amount) R.string.check_fee_unit_amount else R.string.check_fee_unit_percent),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * The vehicle this check uses, as a small card: its icon, name (with a Sample label), and its fuel
 * price, km per litre and maintenance reserve, each with an icon, plus "Change" (for this check
 * only). At large font sizes "Change" moves under the rates so they keep their width.
 */
@Composable
private fun VehicleRatesLine(vehicle: Vehicle, onChangeRates: () -> Unit) {
    val stacked = LocalDensity.current.fontScale > STACK_FONT_SCALE
    val change = @Composable {
        ChangeRatesButton(onChangeRates)
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = Spacing.m, top = Spacing.m, bottom = Spacing.m, end = Spacing.xs)) {
            Row(
                // Stacked rates are tall; the tile then stays by the name instead of floating.
                verticalAlignment = if (stacked) Alignment.Top else Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center,
                ) {
                    // Decorative: the name and type are read out with the text.
                    Icon(
                        painterResource(vehicle.type.icon),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Column(
                    Modifier
                        .weight(1f)
                        .semantics(mergeDescendants = true) {},
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Text(
                            vehicle.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (vehicle.isSample) SampleLabel()
                    }
                    VehicleRates(vehicle)
                }
                if (!stacked) change()
            }
            if (stacked) {
                Box(Modifier.align(Alignment.End)) { change() }
            }
        }
    }
}

@Composable
private fun ChangeRatesButton(onClick: () -> Unit) {
    val description = stringResource(R.string.check_change_rates_description)
    TextButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = description }) {
        Icon(painterResource(R.drawable.ic_edit), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Spacing.xs))
        Text(stringResource(R.string.check_change_rates), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SampleLabel() {
    val status = PicksmartTheme.status
    Surface(shape = MaterialTheme.shapes.extraSmall, color = status.belowTargetContainer) {
        Text(
            stringResource(R.string.check_sample_label),
            style = MaterialTheme.typography.labelMedium,
            color = status.onBelowTargetContainer,
            modifier = Modifier.padding(horizontal = Spacing.s, vertical = 2.dp),
        )
    }
}

@Composable
private fun RateOverridesCard(
    overrides: RateOverrides,
    errors: Map<InputField, CheckRideError>,
    onChange: (RateOverrides) -> Unit,
    onUseVehicleRates: () -> Unit,
) {
    val currency = stringResource(R.string.currency_prefix)
    PicksmartCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(stringResource(R.string.check_overrides_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.check_overrides_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            NumberField(
                value = overrides.fuelPrice,
                onValueChange = { onChange(overrides.copy(fuelPrice = it)) },
                label = stringResource(R.string.vehicle_field_fuel_price),
                prefix = currency,
                suffix = stringResource(R.string.unit_per_litre),
                error = errors.text(InputField.FUEL_PRICE),
            )
            NumberField(
                value = overrides.efficiency,
                onValueChange = { onChange(overrides.copy(efficiency = it)) },
                label = stringResource(R.string.vehicle_field_efficiency),
                suffix = stringResource(R.string.unit_km_per_litre),
                maxDecimals = 1,
                error = errors.text(InputField.FUEL_EFFICIENCY),
            )
            NumberField(
                value = overrides.maintenance,
                onValueChange = { onChange(overrides.copy(maintenance = it)) },
                label = stringResource(R.string.vehicle_field_maintenance),
                placeholder = stringResource(R.string.amount_placeholder_zero),
                prefix = currency,
                suffix = stringResource(R.string.unit_per_km),
                error = errors.text(InputField.MAINTENANCE_RESERVE),
                imeAction = ImeAction.Done,
            )
            SecondaryButton(stringResource(R.string.check_use_vehicle_rates), onUseVehicleRates, Modifier.fillMaxWidth())
        }
    }
}

/** Fields side by side with equal widths, or stacked full width when [stacked]. */
@Composable
internal fun FieldRow(stacked: Boolean, fields: List<@Composable (Modifier) -> Unit>) {
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
            fields.forEach { it(Modifier.fillMaxWidth()) }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            fields.forEach { it(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun ResultPanel(
    state: CheckRideUiState.Ready,
    onSave: (TripStatus) -> Unit,
    onEditTarget: () -> Unit,
    modifier: Modifier,
) {
    when (val outcome = state.outcome) {
        CheckRideOutcome.NeedsFare -> MessagePanel(
            title = stringResource(R.string.check_result_needs_fare_title),
            body = stringResource(R.string.check_result_needs_fare_body),
            modifier = modifier,
        )
        is CheckRideOutcome.Invalid -> MessagePanel(
            title = stringResource(R.string.check_result_invalid_title),
            body = if (outcome.resultTooLarge) stringResource(R.string.check_result_too_large) else null,
            modifier = modifier,
            isError = true,
        )
        is CheckRideOutcome.Calculated -> CalculatedPanel(
            outcome = outcome,
            targetCents = outcome.input.targetProfitPerKmCents,
            saving = state.saving,
            onSave = onSave,
            onEditTarget = onEditTarget,
            modifier = modifier,
        )
    }
}

@Composable
private fun MessagePanel(title: String, body: String?, modifier: Modifier, isError: Boolean = false) {
    PicksmartCard(modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            Icon(
                painterResource(if (isError) R.drawable.ic_error else R.drawable.ic_info),
                contentDescription = null,
                tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (body != null) {
                    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalculatedPanel(
    outcome: CheckRideOutcome.Calculated,
    targetCents: Long?,
    saving: Boolean,
    onSave: (TripStatus) -> Unit,
    onEditTarget: () -> Unit,
    modifier: Modifier,
) {
    val result = outcome.result
    val style = result.band.style()
    val money = rememberMoneyFormatter()
    val net = money.format(result.netProfitCents)
    val perKm = result.netProfitPerKmCents?.let { stringResource(R.string.check_per_km_value, money.format(it)) }
        ?: stringResource(R.string.check_per_km_none)
    val netDescription = stringResource(R.string.check_net_profit_description, net, perKm)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = style.container,
        border = BorderStroke(1.dp, style.content.copy(alpha = 0.4f)),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    // Announced when the band changes, not on every keystroke.
                    modifier = Modifier
                        .weight(1f)
                        .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                ) {
                    Icon(painterResource(style.icon), contentDescription = null, tint = style.content, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(Spacing.s))
                    Text(bandLine(result.band, targetCents), style = MaterialTheme.typography.titleMedium, color = style.content)
                }
                // Without a target any profit is green, however little per km; offer one right
                // here, where the rider sees that, even while the keyboard is open.
                if (targetCents == null) {
                    TextButton(onClick = onEditTarget) {
                        Text(stringResource(R.string.check_target_set), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                itemVerticalAlignment = Alignment.Bottom,
                modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = netDescription },
            ) {
                Text(net, style = MaterialTheme.typography.displaySmall, color = style.onContainer)
                Text(
                    perKm,
                    style = if (result.netProfitPerKmCents != null) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium,
                    color = style.content,
                    modifier = Modifier.padding(bottom = Spacing.xs),
                )
            }
            // While typing, the keyboard takes half the screen; keep just the answer in view.
            if (!WindowInsets.isImeVisible) {
                HorizontalDivider(color = style.content.copy(alpha = 0.3f))
                Breakdown(outcome, style.onContainer)
                if (targetCents != null) TargetLine(targetCents, style.onContainer, onEditTarget)
            }
            SaveButtons(isLoss = result.band == ResultBand.LOSS, enabled = !saving, onSave = onSave)
        }
    }
}

/** The rider's profit target per km, with a way to change or remove it. */
@Composable
private fun TargetLine(targetCents: Long, color: Color, onEdit: () -> Unit) {
    val changeDescription = stringResource(R.string.check_target_change_description)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.check_target_value, rememberMoneyFormatter().format(targetCents)),
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onEdit, modifier = Modifier.semantics { contentDescription = changeDescription }) {
            Text(stringResource(R.string.check_target_change), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Sets the profit target per km. Empty turns it off; it is saved on the vehicle. */
@Composable
private fun TargetDialog(currentCents: Long?, onSave: (Long?) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(currentCents?.let(::centsToInput).orEmpty()) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.target_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Text(stringResource(R.string.target_dialog_body), style = MaterialTheme.typography.bodyMedium)
                NumberField(
                    value = text,
                    onValueChange = {
                        text = it
                        invalid = false
                    },
                    label = stringResource(R.string.target_dialog_field),
                    placeholder = stringResource(R.string.placeholder_zero),
                    prefix = stringResource(R.string.currency_prefix),
                    suffix = stringResource(R.string.unit_per_km),
                    error = if (invalid) stringResource(R.string.vehicle_error_invalid) else null,
                    imeAction = ImeAction.Done,
                    modifier = Modifier.focusRequester(focus),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (text.isBlank()) {
                    onSave(null)
                } else {
                    parseRupeesToCents(text)?.let(onSave) ?: run { invalid = true }
                }
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** "Save offer" keeps it as an offer (never income); "I took it" records a completed trip now. */
@Composable
private fun SaveButtons(isLoss: Boolean, enabled: Boolean, onSave: (TripStatus) -> Unit) {
    val stacked = LocalDensity.current.fontScale > STACK_FONT_SCALE
    FieldRow(
        stacked,
        listOf(
            { m ->
                SecondaryButton(stringResource(R.string.check_save_offer), { onSave(TripStatus.OFFER_CHECK) }, m, enabled = enabled)
            },
            { m ->
                // Taking a loss-making ride is allowed but not encouraged: no yellow fill.
                if (isLoss) {
                    SecondaryButton(stringResource(R.string.check_took_it_anyway), { onSave(TripStatus.COMPLETED) }, m, enabled = enabled)
                } else {
                    PrimaryButton(stringResource(R.string.check_took_it), { onSave(TripStatus.COMPLETED) }, m, enabled = enabled)
                }
            },
        ),
    )
}

@Composable
private fun Breakdown(outcome: CheckRideOutcome.Calculated, color: Color) {
    val money = rememberMoneyFormatter()
    val result = outcome.result
    val items = buildList {
        add(stringResource(R.string.check_result_distance) to stringResource(R.string.check_km_value, formatDecimal(result.totalKm, 1)))
        if (outcome.input.fareMode == FareMode.GROSS_BEFORE_COMMISSION) {
            add(stringResource(R.string.check_result_fee) to money.format(result.commissionCents))
        }
        add(stringResource(R.string.check_result_fuel) to money.format(result.fuelCostCents))
        add(stringResource(R.string.check_result_reserve) to money.format(result.maintenanceReserveCents))
        if (outcome.input.otherCostsCents > 0) {
            add(stringResource(R.string.check_result_other) to money.format(outcome.input.otherCostsCents))
        }
    }
    val columns = if (LocalDensity.current.fontScale > STACK_FONT_SCALE) 2 else 3
    FlowRow(maxItemsInEachRow = columns, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        items.forEach { (label, value) ->
            Column(
                Modifier
                    .fillMaxWidth(1f / columns)
                    .padding(end = Spacing.s)
                    .semantics(mergeDescendants = true) {},
            ) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = color)
                Text(value, style = MaterialTheme.typography.titleSmall, color = color)
            }
        }
    }
}

@Composable
private fun bandLine(band: ResultBand, targetCents: Long?): String {
    val target = targetCents?.let { rememberMoneyFormatter().format(it) }.orEmpty()
    return when (band) {
        ResultBand.GOOD -> stringResource(R.string.check_band_good, target)
        ResultBand.BELOW_TARGET -> stringResource(R.string.check_band_below_target, target)
        ResultBand.PROFIT -> stringResource(R.string.check_band_profit)
        ResultBand.BREAK_EVEN -> stringResource(R.string.check_band_break_even)
        ResultBand.LOSS -> stringResource(R.string.check_band_loss)
    }
}

@Composable
internal fun Map<InputField, CheckRideError>.text(field: InputField): String? = get(field)?.let { error ->
    stringResource(
        when (error) {
            CheckRideError.REQUIRED ->
                if (field == InputField.FUEL_PRICE) R.string.vehicle_error_fuel_price_required else R.string.vehicle_error_efficiency_required
            CheckRideError.INVALID -> R.string.vehicle_error_invalid
            CheckRideError.MUST_BE_ABOVE_ZERO -> R.string.vehicle_error_above_zero
            CheckRideError.PERCENT_ABOVE_100 -> R.string.check_error_percent_above_100
        },
    )
}

internal const val STACK_FONT_SCALE = 1.3f
