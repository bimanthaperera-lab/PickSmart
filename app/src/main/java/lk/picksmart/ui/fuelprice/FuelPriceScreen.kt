package lk.picksmart.ui.fuelprice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import lk.picksmart.R
import lk.picksmart.domain.calculator.fuelCostPerKmCents
import lk.picksmart.domain.model.FuelType
import lk.picksmart.domain.model.Vehicle
import lk.picksmart.domain.usecase.PublishedPriceAction
import lk.picksmart.ui.checkride.STACK_FONT_SCALE
import lk.picksmart.ui.components.ChoiceGrid
import lk.picksmart.ui.components.DetailHeader
import lk.picksmart.ui.components.EmptyState
import lk.picksmart.ui.components.ErrorState
import lk.picksmart.ui.components.LoadingState
import lk.picksmart.ui.components.NoticeCard
import lk.picksmart.ui.components.NumberField
import lk.picksmart.ui.components.PicksmartCard
import lk.picksmart.ui.components.PrimaryButton
import lk.picksmart.ui.components.SecondaryButton
import lk.picksmart.ui.components.SwitchRow
import lk.picksmart.ui.components.message
import lk.picksmart.ui.components.rememberMoneyFormatter
import lk.picksmart.ui.components.rememberRecordDate
import lk.picksmart.ui.theme.PicksmartTheme
import lk.picksmart.ui.theme.Spacing
import lk.picksmart.ui.vehicle.label
import lk.picksmart.util.ColomboZone
import lk.picksmart.util.todayInColombo
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Fuel price (CLAUDE.md section 8a): a new published price as a prompt, the default vehicle's fuel
 * type and price with where it came from, and the auto-update switch. Works offline from the last
 * saved price list.
 */
@Composable
fun FuelPriceScreen(
    onBack: () -> Unit,
    onAddVehicle: () -> Unit,
    viewModel: FuelPriceViewModel = viewModel(factory = FuelPriceViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ready = state as? FuelPriceUiState.Ready
    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        DetailHeader(stringResource(R.string.fuel_price_title), R.drawable.ic_arrow_back, stringResource(R.string.action_back), onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            when (val s = state) {
                FuelPriceUiState.Loading -> LoadingState()
                FuelPriceUiState.NoVehicle -> EmptyState(
                    iconRes = R.drawable.ic_fuel,
                    title = stringResource(R.string.settings_add_vehicle),
                    body = stringResource(R.string.fuel_price_no_vehicle_body),
                    actionLabel = stringResource(R.string.settings_add_vehicle),
                    onAction = onAddVehicle,
                )
                is FuelPriceUiState.Ready -> FuelPriceContent(
                    state = s,
                    onFormChange = viewModel::updateForm,
                    onKeep = viewModel::keep,
                    onAccept = viewModel::accept,
                    onAutoUpdate = viewModel::setAutoUpdate,
                    onDismissNotice = viewModel::dismissAutoNotice,
                )
            }
        }
        // Only when there is something to save; the prompt and the switch apply at once.
        if (ready != null && (ready.form.isChanged || ready.failed)) {
            Column(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.m)) {
                if (ready.failed) ErrorState(stringResource(R.string.fuel_price_save_failed), Modifier.padding(bottom = Spacing.m))
                if (ready.form.isChanged) {
                    PrimaryButton(stringResource(R.string.fuel_price_save), viewModel::save, Modifier.fillMaxWidth(), enabled = !ready.busy)
                }
            }
        }
    }
}

@Composable
private fun FuelPriceContent(
    state: FuelPriceUiState.Ready,
    onFormChange: (FuelPriceFormState) -> Unit,
    onKeep: (PublishedPriceAction.Offer) -> Unit,
    onAccept: (PublishedPriceAction.Offer) -> Unit,
    onAutoUpdate: (Boolean) -> Unit,
    onDismissNotice: () -> Unit,
) {
    val vehicle = state.prices.vehicle ?: return
    val form = state.form
    val money = rememberMoneyFormatter()
    val offer = state.prices.action as? PublishedPriceAction.Offer
    if (offer != null) NewPriceCard(vehicle, offer, enabled = !state.busy, onKeep = { onKeep(offer) }, onAccept = { onAccept(offer) })
    val effectiveFrom = vehicle.fuelPriceEffectiveDate
    if (state.prices.autoApplied && effectiveFrom != null) {
        NoticeCard(
            title = stringResource(R.string.fuel_price_auto_title),
            body = stringResource(
                R.string.fuel_price_auto_body,
                stringResource(vehicle.fuelType.label),
                money.format(vehicle.fuelPricePerLitreCents),
                rememberRecordDate(effectiveFrom, todayInColombo()),
            ),
            actionLabel = stringResource(R.string.action_ok),
            onAction = onDismissNotice,
        )
    }
    ChoiceGrid(
        label = stringResource(R.string.fuel_price_type_label, vehicle.name),
        options = FuelType.entries,
        selected = form.fuelType,
        optionLabel = { stringResource(it.label) },
        onSelect = { onFormChange(form.copy(fuelType = it)) },
    )
    NumberField(
        value = form.price,
        onValueChange = { onFormChange(form.copy(price = it)) },
        label = stringResource(R.string.fuel_price_field),
        prefix = stringResource(R.string.currency_prefix),
        suffix = stringResource(R.string.unit_per_litre),
        // A changed price will be saved as the rider's own.
        supportingText = if (form.isChanged) stringResource(R.string.fuel_price_source_manual) else fuelPriceSourceText(vehicle, state.prices.listStale),
        error = state.priceError?.message(R.string.fuel_price_error_required),
        imeAction = ImeAction.Done,
    )
    if (state.configured) {
        SwitchRow(
            title = stringResource(R.string.fuel_price_auto_switch),
            body = stringResource(R.string.fuel_price_auto_switch_body),
            checked = vehicle.autoUpdateFuelPrice,
            onCheckedChange = { if (!state.busy) onAutoUpdate(it) },
        )
    }
    InfoLine(state)
}

/** The "New CPC price" prompt: both prices, their fuel cost per km, then Keep or Update. */
@Composable
private fun NewPriceCard(
    vehicle: Vehicle,
    offer: PublishedPriceAction.Offer,
    enabled: Boolean,
    onKeep: () -> Unit,
    onAccept: () -> Unit,
) {
    val status = PicksmartTheme.status
    val money = rememberMoneyFormatter()
    val stacked = LocalDensity.current.fontScale > STACK_FONT_SCALE
    val oldPerKm = fuelCostPerKmCents(vehicle.fuelPricePerLitreCents, vehicle.fuelEfficiencyKmPerLitre)
    val newPerKm = fuelCostPerKmCents(offer.priceCents, vehicle.fuelEfficiencyKmPerLitre)
    // The arrow of the change is in the numbers; colour only adds to it.
    val newPerKmColor = when {
        oldPerKm == null || newPerKm == null || newPerKm == oldPerKm -> status.onBelowTargetContainer
        newPerKm > oldPerKm -> status.belowTarget
        else -> status.profit
    }
    PicksmartCard(Modifier.fillMaxWidth(), containerColor = status.belowTargetContainer) {
        Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(stringResource(R.string.fuel_price_new_label), style = MaterialTheme.typography.labelLarge, color = status.belowTarget)
            Text(
                stringResource(R.string.fuel_price_new_title, stringResource(vehicle.fuelType.label), money.format(offer.priceCents)),
                style = MaterialTheme.typography.titleLarge,
                color = status.onBelowTargetContainer,
            )
            Text(
                stringResource(R.string.fuel_price_from, rememberFullDate(offer.effectiveFrom)),
                style = MaterialTheme.typography.bodyMedium,
                color = status.onBelowTargetContainer,
            )
        }
        val boxes = @Composable { modifier: Modifier ->
            PriceBox(
                stringResource(R.string.fuel_price_yours),
                money.format(vehicle.fuelPricePerLitreCents),
                oldPerKm?.let { stringResource(R.string.fuel_price_per_km, money.format(it)) },
                MaterialTheme.colorScheme.onSurfaceVariant,
                modifier,
            )
            PriceBox(
                stringResource(R.string.fuel_price_new),
                money.format(offer.priceCents),
                newPerKm?.let { stringResource(R.string.fuel_price_per_km, money.format(it)) },
                newPerKmColor,
                modifier,
            )
        }
        if (stacked) {
            Column(Modifier.padding(vertical = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                boxes(Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.padding(vertical = Spacing.m), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                boxes(Modifier.weight(1f))
            }
        }
        Text(stringResource(R.string.fuel_price_new_note), style = MaterialTheme.typography.bodyMedium, color = status.onBelowTargetContainer)
        val keep = stringResource(R.string.fuel_price_keep, money.format(vehicle.fuelPricePerLitreCents))
        val update = stringResource(R.string.fuel_price_update)
        if (stacked) {
            Column(Modifier.padding(top = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                PrimaryButton(update, onAccept, Modifier.fillMaxWidth(), enabled = enabled)
                SecondaryButton(keep, onKeep, Modifier.fillMaxWidth(), enabled = enabled)
            }
        } else {
            Row(Modifier.padding(top = Spacing.m), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                SecondaryButton(keep, onKeep, Modifier.weight(1f), enabled = enabled)
                PrimaryButton(update, onAccept, Modifier.weight(1f), enabled = enabled)
            }
        }
    }
}

@Composable
private fun PriceBox(label: String, price: String, perKm: String?, perKmColor: Color, modifier: Modifier) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, modifier = modifier) {
        Column(Modifier.padding(Spacing.m).semantics(mergeDescendants = true) {}) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(price, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            if (perKm != null) Text(perKm, style = MaterialTheme.typography.bodyMedium, color = perKmColor)
        }
    }
}

/** How prices are checked, and when they last were (or that this build can't check them). */
@Composable
private fun InfoLine(state: FuelPriceUiState.Ready) {
    val lastCheck = state.lastCheckAtMillis
    val lines = if (state.configured) {
        listOf(
            stringResource(R.string.fuel_price_info),
            if (lastCheck == null) {
                stringResource(R.string.fuel_price_never_checked)
            } else {
                stringResource(R.string.fuel_price_last_checked, rememberCheckTime(lastCheck))
            },
        )
    } else {
        listOf(stringResource(R.string.fuel_price_not_configured))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.Top) {
        Icon(
            painterResource(R.drawable.ic_info),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** "1 Oct 2026" */
@Composable
private fun rememberFullDate(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    val pattern = stringResource(R.string.pattern_date_field)
    return remember(date, pattern, locale) { DateTimeFormatter.ofPattern(pattern, locale).format(date) }
}

/** "23 Oct, 10:15 AM" in Colombo time. */
@Composable
private fun rememberCheckTime(millis: Long): String {
    val dateTime = remember(millis) { Instant.ofEpochMilli(millis).atZone(ColomboZone) }
    val locale = LocalConfiguration.current.locales[0]
    val timePattern = stringResource(R.string.pattern_time)
    val time = remember(dateTime, timePattern, locale) { DateTimeFormatter.ofPattern(timePattern, locale).format(dateTime) }
    return stringResource(R.string.fuel_price_checked_at, rememberRecordDate(dateTime.toLocalDate(), todayInColombo()), time)
}
