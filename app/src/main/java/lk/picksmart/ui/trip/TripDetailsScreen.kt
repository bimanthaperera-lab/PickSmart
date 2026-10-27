package lk.picksmart.ui.trip

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import lk.picksmart.R
import lk.picksmart.domain.calculator.ProfitResult
import lk.picksmart.domain.model.CommissionType
import lk.picksmart.domain.model.FareMode
import lk.picksmart.domain.model.Trip
import lk.picksmart.domain.model.TripStatus
import lk.picksmart.ui.components.DetailHeader
import lk.picksmart.ui.components.EmptyState
import lk.picksmart.ui.components.ErrorState
import lk.picksmart.ui.components.ListGroup
import lk.picksmart.ui.components.ListRow
import lk.picksmart.ui.components.LoadingState
import lk.picksmart.ui.components.PicksmartCard
import lk.picksmart.ui.components.PrimaryButton
import lk.picksmart.ui.components.ResultBandChip
import lk.picksmart.ui.components.SectionLabel
import lk.picksmart.ui.components.rememberMoneyFormatter
import lk.picksmart.ui.history.StatusTag
import lk.picksmart.ui.theme.PicksmartTheme
import lk.picksmart.ui.theme.Spacing
import lk.picksmart.ui.vehicle.icon
import lk.picksmart.util.ColomboZone
import lk.picksmart.util.MoneyFormatter
import lk.picksmart.util.dateFormatter
import lk.picksmart.util.formatDecimal
import lk.picksmart.util.hundredthsToInput

/**
 * A saved trip, read-only: what was recorded, and how its estimate is made up from the trip's own
 * rate snapshot. Edit opens the trip form; Delete asks first (CLAUDE.md N7).
 */
@Composable
fun TripDetailsScreen(
    onBack: () -> Unit,
    onEdit: (tripId: Long) -> Unit,
    viewModel: TripDetailsViewModel = viewModel(factory = TripDetailsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val loaded = state as? TripDetailsUiState.Loaded

    LaunchedEffect(state) { if (state == TripDetailsUiState.Deleted) onBack() }

    Column(Modifier.fillMaxSize()) {
        DetailHeader(stringResource(R.string.trip_details_title), R.drawable.ic_arrow_back, stringResource(R.string.action_back), onBack) {
            if (loaded != null) {
                IconButton(onClick = { confirmDelete = true }, enabled = !loaded.deleting) {
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
        ) {
            when (val s = state) {
                TripDetailsUiState.Loading, TripDetailsUiState.Deleted -> LoadingState()
                TripDetailsUiState.NotFound -> EmptyState(
                    iconRes = R.drawable.ic_info,
                    title = stringResource(R.string.trip_not_found_title),
                    body = stringResource(R.string.trip_not_found_body),
                    actionLabel = stringResource(R.string.action_close),
                    onAction = onBack,
                )
                is TripDetailsUiState.Loaded -> TripDetails(s)
            }
        }

        if (loaded != null) {
            Column(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.m)) {
                if (loaded.deleteFailed) ErrorState(stringResource(R.string.trip_delete_failed), Modifier.padding(bottom = Spacing.m))
                PrimaryButton(
                    stringResource(R.string.trip_edit_title),
                    { onEdit(loaded.trip.id) },
                    Modifier.fillMaxWidth(),
                    enabled = !loaded.deleting,
                    iconRes = R.drawable.ic_edit,
                )
            }
        }
    }

    if (confirmDelete) {
        TripDeleteDialog(
            onConfirm = {
                confirmDelete = false
                viewModel.delete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** The one confirmation every trip delete goes through (CLAUDE.md N7). */
@Composable
internal fun TripDeleteDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trip_delete_title)) },
        text = { Text(stringResource(R.string.trip_delete_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun TripDetails(state: TripDetailsUiState.Loaded) {
    val trip = state.trip
    val money = rememberMoneyFormatter()
    val cancelled = trip.status == TripStatus.CANCELLED

    SummaryCard(trip, state.estimate)

    SectionLabel(stringResource(R.string.trip_details_money))
    ListGroup(rows = moneyRows(trip, state.estimate, money))

    SectionLabel(stringResource(R.string.check_result_distance))
    ListGroup(
        rows = buildList {
            add {
                ListRow(
                    stringResource(if (cancelled) R.string.trip_pickup_driven else R.string.check_pickup),
                    value = kmText(trip.pickupKm),
                    iconRes = R.drawable.ic_pin,
                )
            }
            if (!cancelled) add { ListRow(stringResource(R.string.check_trip), value = kmText(trip.tripKm), iconRes = R.drawable.ic_route) }
            add { ListRow(stringResource(R.string.check_return), value = kmText(trip.returnKm), iconRes = R.drawable.ic_return) }
            val total = trip.pickupKm + (if (cancelled) 0.0 else trip.tripKm) + trip.returnKm
            add {
                ListRow(
                    stringResource(R.string.trip_details_total_km),
                    value = kmText(total),
                    valueColor = MaterialTheme.colorScheme.onSurface,
                    iconRes = R.drawable.ic_gauge,
                )
            }
        },
    )

    SectionLabel(stringResource(R.string.trip_details_more))
    ListGroup(
        rows = buildList {
            add {
                ListRow(
                    stringResource(R.string.trip_details_vehicle),
                    value = state.vehicleName ?: "—",
                    iconRes = state.vehicleType?.icon ?: R.drawable.ic_vehicle_other,
                )
            }
            trip.notes?.takeIf { it.isNotBlank() }?.let { note ->
                add { ListRow(stringResource(R.string.trip_details_note), subtitle = note, iconRes = R.drawable.ic_note) }
            }
        },
    )

    Spacer(Modifier.height(Spacing.l))
    RatesLine(trip.rates)
}

@Composable
private fun SummaryCard(trip: Trip, estimate: ProfitResult?) {
    val money = rememberMoneyFormatter()
    val colors = MaterialTheme.colorScheme
    val status = PicksmartTheme.status
    val isOffer = trip.status == TripStatus.OFFER_CHECK

    val locale = LocalConfiguration.current.locales[0]
    val datePattern = stringResource(R.string.pattern_day_with_year)
    val timePattern = stringResource(R.string.pattern_time)
    val whenText = remember(trip.tripDateTime, datePattern, timePattern, locale) {
        val at = trip.tripDateTime.atZone(ColomboZone)
        dateFormatter(datePattern, locale).format(at) to dateFormatter(timePattern, locale).format(at)
    }.let { (date, time) -> stringResource(R.string.trip_details_when, date, time) }

    PicksmartCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(colors.surfaceContainerHighest, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(trip.platform.initials), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            }
            Spacer(Modifier.width(Spacing.m))
            Column(Modifier.weight(1f)) {
                Text(stringResource(trip.platform.label), style = MaterialTheme.typography.titleMedium)
                Text(whenText, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            Spacer(Modifier.width(Spacing.s))
            StatusTag(trip.status)
        }

        Spacer(Modifier.height(Spacing.l))
        Column(Modifier.semantics(mergeDescendants = true) {}) {
            Text(
                stringResource(if (isOffer) R.string.trip_net_estimate_offer else R.string.trip_net_profit),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            if (estimate == null) {
                Text(stringResource(R.string.history_row_unavailable), style = MaterialTheme.typography.titleLarge)
            } else {
                val net = estimate.netProfitCents
                Text(
                    money.formatSigned(net),
                    style = MaterialTheme.typography.headlineLarge,
                    color = when {
                        isOffer -> colors.onSurface
                        net < 0 -> status.loss
                        else -> status.profit
                    },
                )
            }
        }
        if (estimate != null) {
            Spacer(Modifier.height(Spacing.s))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                ResultBandChip(estimate.band)
                Text(
                    estimate.netProfitPerKmCents?.let { stringResource(R.string.check_per_km_value, money.format(it)) }
                        ?: stringResource(R.string.check_per_km_none),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        if (trip.status == TripStatus.CANCELLED) {
            Spacer(Modifier.height(Spacing.s))
            Text(stringResource(R.string.trip_details_cancelled_note), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}

/**
 * Income first, then each cost as a negative amount, so the rows add up to the net shown above.
 * A cost that took money is in the loss colour; its minus sign carries the same meaning.
 */
@Composable
private fun moneyRows(trip: Trip, estimate: ProfitResult?, money: MoneyFormatter): List<@Composable () -> Unit> {
    val payout = trip.fareMode == FareMode.PAYOUT_AFTER_COMMISSION
    val incomeColor = MaterialTheme.colorScheme.onSurface
    val lossColor = PicksmartTheme.status.loss
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

    @Composable
    fun CostRow(@StringRes title: Int, @DrawableRes icon: Int, cents: Long, subtitle: String? = null) = ListRow(
        stringResource(title),
        subtitle = subtitle,
        value = if (cents > 0) money.format(-cents) else money.format(0),
        valueColor = if (cents > 0) lossColor else mutedColor,
        iconRes = icon,
    )

    return buildList {
        if (trip.status == TripStatus.CANCELLED) {
            add {
                ListRow(
                    stringResource(R.string.trip_cancellation_fee),
                    value = money.format(trip.cancellationFeeCents ?: 0),
                    valueColor = incomeColor,
                    iconRes = R.drawable.ic_wallet,
                )
            }
        } else {
            val fareLabel = when {
                payout -> R.string.check_fare_payout
                trip.status == TripStatus.OFFER_CHECK -> R.string.check_fare_gross
                else -> R.string.trip_fare_received
            }
            add {
                ListRow(
                    stringResource(fareLabel),
                    subtitle = if (payout) stringResource(R.string.trip_details_fee_taken) else null,
                    value = money.format(trip.fareCents),
                    valueColor = incomeColor,
                    iconRes = R.drawable.ic_wallet,
                )
            }
            if (!payout && estimate != null) {
                add {
                    CostRow(
                        R.string.check_fee,
                        R.drawable.ic_percent,
                        estimate.commissionCents,
                        subtitle = if (trip.commissionType == CommissionType.PERCENT) {
                            stringResource(R.string.trip_details_fee_percent, hundredthsToInput(trip.commissionHundredths))
                        } else {
                            null
                        },
                    )
                }
            }
        }
        if (estimate != null) {
            add { CostRow(R.string.trip_details_fuel, R.drawable.ic_fuel, estimate.fuelCostCents) }
            add { CostRow(R.string.reports_line_reserve, R.drawable.ic_wrench, estimate.maintenanceReserveCents) }
        }
        if (trip.status != TripStatus.CANCELLED) {
            add { CostRow(R.string.check_other_costs, R.drawable.ic_coins, trip.otherCostsCents) }
        }
    }
}

@Composable
private fun kmText(km: Double): String = stringResource(R.string.check_km_value, formatDecimal(km, 1))
