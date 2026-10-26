package lk.picksmart.ui.reports

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import lk.picksmart.R
import lk.picksmart.domain.usecase.PeriodSummary
import lk.picksmart.domain.usecase.ReportPeriod
import lk.picksmart.ui.components.EmptyState
import lk.picksmart.ui.components.InfoNote
import lk.picksmart.ui.components.LoadingState
import lk.picksmart.ui.components.PicksmartCard
import lk.picksmart.ui.components.DetailHeader
import lk.picksmart.ui.components.SegmentedControl
import lk.picksmart.ui.components.TabScreen
import lk.picksmart.ui.components.rememberMoneyFormatter
import lk.picksmart.ui.theme.PicksmartTheme
import lk.picksmart.ui.theme.Spacing
import lk.picksmart.util.formatDecimal
import java.time.format.DateTimeFormatter

/**
 * Profit for a day, a Monday–Sunday week or a calendar month, in the estimated or the cash view
 * (CLAUDE.md section 7), laid out as in the "Reports — week" mockup. Each view says what it counts,
 * and the report says when data is missing.
 */
@Composable
fun ReportsScreen(
    onBack: () -> Unit,
    onCheckRide: () -> Unit,
    onOpenFuelLog: () -> Unit,
    viewModel: ReportsViewModel = viewModel(factory = ReportsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exportStatus by viewModel.exportStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.reports_export_chooser)
    val subject = stringResource(R.string.reports_export_subject)
    LaunchedEffect(viewModel) {
        viewModel.exportedFiles.collect { files -> context.shareFiles(files, subject, chooserTitle) }
    }
    Column(Modifier.fillMaxSize()) {
        DetailHeader(stringResource(R.string.reports_title), R.drawable.ic_arrow_back, stringResource(R.string.action_back), onBack) {
            // Exports every record, so it is offered whenever there is anything to export.
            if (state is ReportsUiState.Loaded) {
                IconButton(onClick = viewModel::export, enabled = exportStatus != ExportStatus.WORKING) {
                    Icon(painterResource(R.drawable.ic_download), contentDescription = stringResource(R.string.reports_export))
                }
            }
        }
        TabScreen(Modifier.weight(1f)) {
            if (exportStatus == ExportStatus.FAILED) {
                InfoNote(stringResource(R.string.reports_export_failed), iconRes = R.drawable.ic_error)
            }
            when (val s = state) {
                ReportsUiState.Loading -> LoadingState()
                ReportsUiState.Empty -> EmptyState(
                    iconRes = R.drawable.ic_nav_reports,
                    title = stringResource(R.string.reports_empty_title),
                    body = stringResource(R.string.reports_empty_body),
                    actionLabel = stringResource(R.string.action_check_ride),
                    onAction = onCheckRide,
                )
                is ReportsUiState.Loaded -> {
                    SegmentedControl(
                        options = ReportPeriod.entries,
                        selected = s.period,
                        optionLabel = { stringResource(it.label) },
                        onSelect = viewModel::setPeriod,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // The period and the view share a row; at large font sizes the view drops below.
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(Spacing.s),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        PeriodNavigator(s, onPrevious = viewModel::previous, onNext = viewModel::next)
                        SegmentedControl(
                            options = ReportView.entries,
                            selected = s.view,
                            optionLabel = { stringResource(it.label) },
                            onSelect = viewModel::setView,
                            accent = true,
                            fill = false,
                        )
                    }
                    if (s.summary.hasNoRecords) {
                        EmptyState(
                            iconRes = R.drawable.ic_nav_reports,
                            title = stringResource(R.string.reports_period_empty_title),
                            body = stringResource(R.string.reports_period_empty_body),
                        )
                    } else {
                        Report(s, onOpenFuelLog)
                    }
                }
            }
        }
    }
}

@Composable
private fun Report(s: ReportsUiState.Loaded, onOpenFuelLog: () -> Unit) {
    val summary = s.summary
    ProfitHeadline(s)
    if (s.days.isNotEmpty()) DailyProfitChart(s.days, s.view, s.period, s.today)
    Breakdown(s.view, summary)
    when (s.view) {
        ReportView.ESTIMATED -> if (summary.tripsWithoutDistance > 0) {
            InfoNote(
                pluralStringResource(
                    R.plurals.reports_incomplete_distance,
                    summary.tripsWithoutDistance,
                    summary.tripsWithoutDistance,
                    formatDecimal(summary.distanceKm, 1),
                ),
            )
        }
        ReportView.CASH -> if (summary.cashMissingFuel) {
            InfoNote(
                stringResource(R.string.reports_incomplete_fuel),
                iconRes = R.drawable.ic_fuel,
                actionLabel = stringResource(R.string.reports_open_fuel_log),
                onAction = onOpenFuelLog,
            )
        }
    }
    if (summary.offers > 0) {
        Text(
            pluralStringResource(R.plurals.reports_offers_note, summary.offers, summary.offers),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text(
        stringResource(if (s.view == ReportView.ESTIMATED) R.string.reports_estimated_basis else R.string.reports_cash_basis),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Opens the share sheet with the CSV files; the receiving app may only read them. */
private fun Context.shareFiles(files: List<Uri>, subject: String, chooserTitle: String) {
    val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "text/csv"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(files))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        clipData = ClipData.newRawUri(subject, files.first()).apply { files.drop(1).forEach { addItem(ClipData.Item(it)) } }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(send, chooserTitle))
}

private val ReportPeriod.label: Int
    get() = when (this) {
        ReportPeriod.DAY -> R.string.reports_period_day
        ReportPeriod.WEEK -> R.string.reports_period_week
        ReportPeriod.MONTH -> R.string.reports_period_month
    }

private val ReportView.label: Int
    get() = when (this) {
        ReportView.ESTIMATED -> R.string.reports_view_estimated
        ReportView.CASH -> R.string.reports_view_cash
    }

/** ‹ 19 – 25 Oct ›. There is no next period past today's. */
@Composable
private fun PeriodNavigator(s: ReportsUiState.Loaded, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = stringResource(R.string.reports_previous),
                modifier = Modifier.rotate(180f),
            )
        }
        Text(
            periodLabel(s),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        IconButton(onClick = onNext, enabled = !s.isCurrent) {
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = stringResource(R.string.reports_next))
        }
    }
}

/**
 * "Fri 23 Oct", "19 – 25 Oct", "29 Sep – 5 Oct" or "Oct 2026", short so the view toggle stays
 * beside it. The year shows only where it isn't this year's.
 */
@Composable
private fun periodLabel(s: ReportsUiState.Loaded): String {
    val locale = LocalConfiguration.current.locales[0]
    val range = s.range
    val thisYear = s.today.year
    return when (s.period) {
        ReportPeriod.DAY -> {
            val pattern = stringResource(if (range.start.year == thisYear) R.string.pattern_day else R.string.pattern_day_with_year)
            remember(range, pattern, locale) { DateTimeFormatter.ofPattern(pattern, locale).format(range.start) }
        }
        ReportPeriod.WEEK -> {
            val end = range.endInclusive
            val endPattern = stringResource(if (end.year == thisYear) R.string.pattern_short_date else R.string.pattern_date_field)
            val startPattern = stringResource(
                when {
                    range.start.month == end.month -> R.string.pattern_day_of_month
                    range.start.year == end.year -> R.string.pattern_short_date
                    else -> R.string.pattern_date_field
                },
            )
            val startText = remember(range, startPattern, locale) { DateTimeFormatter.ofPattern(startPattern, locale).format(range.start) }
            val endText = remember(range, endPattern, locale) { DateTimeFormatter.ofPattern(endPattern, locale).format(end) }
            stringResource(R.string.reports_range, startText, endText)
        }
        ReportPeriod.MONTH -> {
            val pattern = stringResource(R.string.pattern_month)
            remember(range, pattern, locale) { DateTimeFormatter.ofPattern(pattern, locale).format(range.start) }
        }
    }
}

/**
 * "Estimated net profit · week so far", the amount large, and the per-km line. A loss keeps its
 * minus sign, so the colour is never the only signal.
 */
@Composable
private fun ProfitHeadline(s: ReportsUiState.Loaded) {
    val money = rememberMoneyFormatter()
    val summary = s.summary
    val net = if (s.view == ReportView.ESTIMATED) summary.estimatedProfitCents else summary.cashProfitCents
    val title = stringResource(if (s.view == ReportView.ESTIMATED) R.string.reports_estimated_title else R.string.reports_cash_title)
    val soFar = when (s.period) {
        ReportPeriod.DAY -> R.string.reports_so_far_day
        ReportPeriod.WEEK -> R.string.reports_so_far_week
        ReportPeriod.MONTH -> R.string.reports_so_far_month
    }
    val trips = summary.completedTrips
    val detail = when {
        s.view == ReportView.CASH ->
            pluralStringResource(R.plurals.reports_cash_line, trips, money.format(summary.cashIncomeCents), trips)
        summary.tripProfitPerKmCents != null -> pluralStringResource(
            R.plurals.reports_estimated_line,
            trips,
            money.format(summary.tripProfitPerKmCents),
            formatDecimal(summary.distanceKm, 1),
            trips,
        )
        else -> pluralStringResource(R.plurals.reports_trips_line, trips, trips)
    }
    Column(
        Modifier.semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            if (s.isCurrent) stringResource(R.string.reports_title_so_far, title, stringResource(soFar)) else title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        Text(money.format(net), style = MaterialTheme.typography.displayMedium, color = profitColor(net))
        Text(detail, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun profitColor(cents: Long): Color = when {
    cents > 0 -> PicksmartTheme.status.profit
    cents < 0 -> PicksmartTheme.status.loss
    else -> MaterialTheme.colorScheme.onSurface
}

/**
 * The lines behind the total, as in the mockup. They add up exactly: the estimate's lines are each
 * trip's own calculator lines summed, and the cash lines are the records as entered.
 */
@Composable
private fun Breakdown(view: ReportView, summary: PeriodSummary) {
    val money = rememberMoneyFormatter()
    val lines = buildList {
        add(R.string.reports_line_gross to money.format(summary.tripGrossCents))
        add(R.string.reports_line_fees to money.format(-summary.commissionCents))
        when (view) {
            ReportView.ESTIMATED -> {
                add(R.string.reports_line_estimated_fuel to money.format(-summary.estimatedFuelCents))
                add(R.string.reports_line_reserve to money.format(-summary.reserveCents))
                if (summary.tripOtherCostsCents != 0L) {
                    add(R.string.reports_line_trip_costs to money.format(-summary.tripOtherCostsCents))
                }
                add(R.string.reports_line_other_income to money.formatSigned(summary.otherIncomeCents))
            }
            ReportView.CASH -> {
                add(R.string.reports_line_other_income to money.formatSigned(summary.otherIncomeCents))
                add(R.string.reports_line_expenses to money.format(-summary.expensesCents))
                add(R.string.reports_line_fuel_bought to money.format(-summary.fuelLogCents))
            }
        }
    }
    val net = if (view == ReportView.ESTIMATED) summary.estimatedProfitCents else summary.cashProfitCents
    PicksmartCard(Modifier.fillMaxWidth()) {
        lines.forEach { (label, amount) ->
            BreakdownLine(stringResource(label), amount)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        BreakdownLine(
            stringResource(if (view == ReportView.ESTIMATED) R.string.reports_line_net else R.string.reports_cash_title),
            money.format(net),
            total = true,
            amountColor = profitColor(net),
        )
    }
}

/** A label and its amount; the amount moves under the label when both don't fit (large fonts). */
@Composable
private fun BreakdownLine(label: String, amount: String, total: Boolean = false, amountColor: Color? = null) {
    FlowRow(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.m)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (total) FontWeight.Bold else FontWeight.Normal,
            color = if (total) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            amount,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = amountColor ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}
