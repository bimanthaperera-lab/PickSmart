package lk.picksmart.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import lk.picksmart.R
import lk.picksmart.domain.usecase.PeriodSummary
import lk.picksmart.domain.usecase.FuelPriceState
import lk.picksmart.domain.usecase.PublishedPriceAction
import lk.picksmart.ui.components.BrandMark
import lk.picksmart.ui.components.InfoNote
import lk.picksmart.ui.components.LoadingState
import lk.picksmart.ui.components.NoticeCard
import lk.picksmart.ui.components.rememberRecordDate
import lk.picksmart.ui.vehicle.label
import lk.picksmart.ui.components.PrimaryButton
import lk.picksmart.ui.components.RecordsGroup
import lk.picksmart.ui.components.ScreenTitle
import lk.picksmart.ui.components.SectionLabel
import lk.picksmart.ui.components.Stat
import lk.picksmart.ui.components.StatGrid
import lk.picksmart.ui.components.TabScreen
import lk.picksmart.ui.components.rememberMoneyFormatter
import lk.picksmart.ui.history.HistoryRow
import lk.picksmart.ui.history.TripRow
import lk.picksmart.ui.settings.OfferReaderSwitch
import lk.picksmart.ui.theme.PicksmartTheme
import lk.picksmart.ui.theme.Spacing
import lk.picksmart.util.dateFormatter
import lk.picksmart.util.formatDecimal
import lk.picksmart.util.todayInColombo

/**
 * Today at a glance: estimated net profit, the figures behind it, the way into Check a Ride, the
 * latest trips and the rider's records.
 */
@Composable
fun HomeScreen(
    onCheckRide: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenTrip: (tripId: Long) -> Unit,
    onOpenIncome: () -> Unit,
    onOpenFuelLog: () -> Unit,
    onOpenExpenses: () -> Unit,
    onOpenFuelPrice: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val fuelPrices by viewModel.fuelPrices.collectAsStateWithLifecycle()
    TabScreen {
        HomeHeader()
        when (val s = state) {
            HomeUiState.Loading -> LoadingState()
            is HomeUiState.Loaded -> {
                NetProfitCard(s.summary, s.vehicleName)
                TodayStats(s.summary)
                if (s.sharedLeftOut) InfoNote(stringResource(R.string.vehicle_scope_shared_note))
                PrimaryButton(
                    stringResource(R.string.action_check_ride),
                    onCheckRide,
                    Modifier.fillMaxWidth(),
                    iconRes = R.drawable.ic_nav_check_ride,
                )
                fuelPrices?.let { FuelPriceNotice(it, onOpenFuelPrice, viewModel::dismissFuelPriceNotice) }
                if (s.recent.isNotEmpty()) RecentTrips(s.recent, onOpenHistory, onOpenTrip)
            }
        }
        Column {
            SectionLabel(stringResource(R.string.settings_section_records))
            RecordsGroup(onOpenFuelLog = onOpenFuelLog, onOpenExpenses = onOpenExpenses, onOpenIncome = onOpenIncome)
        }
    }
}

/**
 * A new published fuel price waiting for the rider ("Review" opens Fuel price), or the one-time
 * notice after one was applied automatically. Nothing otherwise.
 */
@Composable
private fun FuelPriceNotice(prices: FuelPriceState, onReview: () -> Unit, onDismiss: () -> Unit) {
    val vehicle = prices.vehicle ?: return
    val money = rememberMoneyFormatter()
    val fuel = stringResource(vehicle.fuelType.label)
    val offer = prices.action as? PublishedPriceAction.Offer
    val effectiveFrom = vehicle.fuelPriceEffectiveDate
    when {
        offer != null -> NoticeCard(
            title = stringResource(R.string.home_fuel_price_new_title),
            body = stringResource(
                R.string.home_fuel_price_new_body,
                fuel,
                money.format(offer.priceCents),
                money.format(vehicle.fuelPricePerLitreCents),
            ),
            iconRes = R.drawable.ic_fuel,
            actionLabel = stringResource(R.string.home_fuel_price_review),
            onAction = onReview,
        )
        prices.autoApplied && effectiveFrom != null -> NoticeCard(
            title = stringResource(R.string.fuel_price_auto_title),
            body = stringResource(
                R.string.fuel_price_auto_body,
                fuel,
                money.format(vehicle.fuelPricePerLitreCents),
                rememberRecordDate(effectiveFrom, todayInColombo()),
            ),
            iconRes = R.drawable.ic_fuel,
            actionLabel = stringResource(R.string.action_ok),
            onAction = onDismiss,
        )
    }
}

@Composable
private fun HomeHeader() {
    val pattern = stringResource(R.string.home_date_pattern)
    val locale = LocalConfiguration.current.locales[0]
    val date = remember(pattern, locale) {
        todayInColombo().format(dateFormatter(pattern, locale))
    }
    // The offer reader's switch sits on the right; at large font sizes it moves under the title.
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            BrandMark()
            Column {
                Text(date, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ScreenTitle(stringResource(R.string.home_title))
            }
        }
        OfferReaderSwitch()
    }
}

/**
 * The day's estimated net profit, large, with an arrow and sign so it never relies on colour. It
 * is labelled "Estimated" because fuel and maintenance are estimates from each trip's rates.
 */
@Composable
private fun NetProfitCard(summary: PeriodSummary, vehicleName: String?) {
    val money = rememberMoneyFormatter()
    val status = PicksmartTheme.status
    val net = summary.estimatedProfitCents
    val color = when {
        net > 0 -> status.profit
        net < 0 -> status.loss
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
    ) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    vehicleName?.let { stringResource(R.string.home_net_title_vehicle, it) } ?: stringResource(R.string.home_net_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { heading() },
                )
                Surface(shape = MaterialTheme.shapes.extraSmall, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Text(
                        stringResource(R.string.home_estimated_tag),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.s, vertical = 2.dp),
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (net != 0L) {
                    Icon(
                        painterResource(if (net > 0) R.drawable.ic_trend_up else R.drawable.ic_trend_down),
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Text(money.format(net), style = MaterialTheme.typography.displaySmall, color = color)
            }
            Text(
                when {
                    summary.isEmpty -> stringResource(R.string.home_net_empty)
                    summary.tripProfitPerKmCents != null ->
                        stringResource(R.string.home_net_per_km, money.format(summary.tripProfitPerKmCents))
                    else -> stringResource(R.string.home_net_basis)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TodayStats(summary: PeriodSummary) {
    val money = rememberMoneyFormatter()
    StatGrid(
        listOf(
            Stat(stringResource(R.string.home_stat_earned), money.format(summary.earnedAfterFeesCents)),
            Stat(stringResource(R.string.home_stat_expenses), money.format(summary.recordedExpensesCents)),
            Stat(stringResource(R.string.home_stat_trips), summary.completedTrips.toString()),
            Stat(stringResource(R.string.home_stat_distance), stringResource(R.string.check_km_value, formatDecimal(summary.distanceKm, 1))),
        ),
    )
}

@Composable
private fun RecentTrips(rows: List<HistoryRow>, onSeeAll: () -> Unit, onOpenTrip: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.home_recent),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            Spacer(Modifier.width(Spacing.s))
            TextButton(onClick = onSeeAll) { Text(stringResource(R.string.home_see_all)) }
        }
        rows.forEach { row -> TripRow(row) { onOpenTrip(row.trip.id) } }
    }
}
