package lk.picksmart.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import lk.picksmart.R
import lk.picksmart.domain.model.TripStatus
import lk.picksmart.ui.checkride.STACK_FONT_SCALE
import lk.picksmart.ui.components.ChoiceChips
import lk.picksmart.ui.components.EmptyState
import lk.picksmart.ui.components.ErrorState
import lk.picksmart.ui.components.LoadingState
import lk.picksmart.ui.components.ScreenTitle
import lk.picksmart.ui.components.SwipeAction
import lk.picksmart.ui.components.SwipeRevealRow
import lk.picksmart.ui.components.SwipeSide
import lk.picksmart.ui.components.BorderlessButton
import lk.picksmart.ui.components.TabScreen
import lk.picksmart.ui.components.rememberMoneyFormatter
import lk.picksmart.ui.theme.PicksmartTheme
import lk.picksmart.ui.theme.Spacing
import lk.picksmart.ui.trip.TripDeleteDialog
import lk.picksmart.ui.trip.initials
import lk.picksmart.ui.trip.label
import lk.picksmart.util.ColomboZone
import lk.picksmart.util.dateFormatter
import lk.picksmart.util.formatDecimal
import java.time.LocalDate

/**
 * The active vehicle's saved trips (every trip with a single vehicle), newest first, grouped by day. Offers are listed (dashed, marked "Offer") but
 * never counted in a day's total (CLAUDE.md N1).
 */
@Composable
fun HistoryScreen(
    onCheckRide: () -> Unit,
    onAddTrip: () -> Unit,
    onOpenTrip: (tripId: Long) -> Unit,
    onEditTrip: (tripId: Long) -> Unit,
    viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val deleteFailed by viewModel.deleteFailed.collectAsStateWithLifecycle()
    // One row open at a time; not saved, since a swiped row is only a moment's state.
    var revealed by remember { mutableStateOf<Pair<Long, SwipeSide>?>(null) }
    var confirmDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }

    when (val s = state) {
        HistoryUiState.Loading -> TabScreen {
            Header(onAddTrip)
            LoadingState()
        }
        HistoryUiState.Empty -> TabScreen {
            Header(onAddTrip)
            EmptyState(
                iconRes = R.drawable.ic_nav_history,
                title = stringResource(R.string.history_empty_title),
                body = stringResource(R.string.history_empty_body),
                actionLabel = stringResource(R.string.action_check_ride),
                onAction = onCheckRide,
            )
        }
        is HistoryUiState.Loaded -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            item { Header(onAddTrip) }
            if (deleteFailed) item { ErrorState(stringResource(R.string.trip_delete_failed)) }
            item {
                ChoiceChips(
                    options = HistoryFilter.entries,
                    selected = s.filter,
                    optionLabel = { stringResource(it.label) },
                    onSelect = viewModel::setFilter,
                    modifier = Modifier.padding(vertical = Spacing.s),
                )
            }
            s.vehicleName?.let { name ->
                item {
                    Text(
                        stringResource(R.string.vehicle_scope_for, name),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (s.days.isEmpty()) {
                item {
                    EmptyState(
                        iconRes = R.drawable.ic_nav_history,
                        title = stringResource(R.string.history_filter_empty_title),
                        body = if (s.filter == HistoryFilter.ALL && s.vehicleName != null) {
                            stringResource(R.string.history_vehicle_empty_body, s.vehicleName)
                        } else {
                            stringResource(R.string.history_filter_empty_body)
                        },
                    )
                }
            }
            s.days.forEach { day ->
                item(key = "day-${day.date}") { DayHeader(day, s.today) }
                items(day.rows, key = { it.trip.id }) { row ->
                    val id = row.trip.id
                    SwipeableTripRow(
                        row = row,
                        revealed = revealed?.takeIf { it.first == id }?.second,
                        onRevealedChange = { side -> revealed = side?.let { id to it } },
                        onOpen = { if (revealed != null) revealed = null else onOpenTrip(id) },
                        onEdit = {
                            revealed = null
                            onEditTrip(id)
                        },
                        onDelete = { confirmDeleteId = id },
                    )
                }
            }
        }
    }

    confirmDeleteId?.let { id ->
        TripDeleteDialog(
            onConfirm = {
                confirmDeleteId = null
                revealed = null
                viewModel.delete(id)
            },
            onDismiss = { confirmDeleteId = null },
        )
    }
}

/**
 * A trip row that swipes right to show Edit and left to show Delete. Tapping the row opens its
 * details, or closes the open buttons first. Screen readers get both as custom actions.
 */
@Composable
private fun SwipeableTripRow(
    row: HistoryRow,
    revealed: SwipeSide?,
    onRevealedChange: (SwipeSide?) -> Unit,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val editLabel = stringResource(R.string.action_edit)
    val deleteLabel = stringResource(R.string.action_delete)
    SwipeRevealRow(
        leftAction = SwipeAction(editLabel, R.drawable.ic_edit, colors.primaryContainer, colors.onPrimaryContainer, onEdit),
        rightAction = SwipeAction(deleteLabel, R.drawable.ic_delete, colors.errorContainer, colors.onErrorContainer, onDelete),
        revealed = revealed,
        onRevealedChange = onRevealedChange,
    ) {
        TripRow(
            row,
            onClick = onOpen,
            // The buttons sit behind the row, so an offer's see-through card needs a solid fill.
            offerBackground = colors.background,
            modifier = Modifier.semantics {
                customActions = listOf(
                    CustomAccessibilityAction(editLabel) { onEdit(); true },
                    CustomAccessibilityAction(deleteLabel) { onDelete(); true },
                )
            },
        )
    }
}

@Composable
private fun Header(onAddTrip: () -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        ScreenTitle(stringResource(R.string.history_title))
        BorderlessButton(stringResource(R.string.history_add_trip), onAddTrip, iconRes = R.drawable.ic_add)
    }
}

private val HistoryFilter.label: Int
    get() = when (this) {
        HistoryFilter.ALL -> R.string.history_filter_all
        HistoryFilter.COMPLETED -> R.string.history_filter_completed
        HistoryFilter.OFFERS -> R.string.history_filter_offers
        HistoryFilter.CANCELLED -> R.string.history_filter_cancelled
    }

@Composable
private fun DayHeader(day: HistoryDay, today: LocalDate) {
    val money = rememberMoneyFormatter()
    val dayText = formatDay(day.date, today)
    FlowRow(
        Modifier
            .fillMaxWidth()
            .padding(top = Spacing.m, bottom = Spacing.xs)
            .semantics(mergeDescendants = true) { heading() },
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(dayText, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        day.estimatedProfitCents?.let {
            Text(
                stringResource(R.string.history_day_total, money.format(it)),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun formatDay(date: LocalDate, today: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    val pattern = stringResource(if (date.year == today.year) R.string.pattern_day else R.string.pattern_day_with_year)
    val text = remember(date, pattern, locale) { dateFormatter(pattern, locale).format(date) }
    return when (date) {
        today -> stringResource(R.string.history_today, text)
        today.minusDays(1) -> stringResource(R.string.history_yesterday, text)
        else -> text
    }
}

@Composable
internal fun TripRow(
    row: HistoryRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    offerBackground: Color = Color.Transparent,
) {
    val trip = row.trip
    val money = rememberMoneyFormatter()
    val status = PicksmartTheme.status
    val colors = MaterialTheme.colorScheme
    val isOffer = trip.status == TripStatus.OFFER_CHECK
    val shape = MaterialTheme.shapes.large

    val locale = LocalConfiguration.current.locales[0]
    val timePattern = stringResource(R.string.pattern_time)
    val time = remember(trip.tripDateTime, timePattern, locale) {
        dateFormatter(timePattern, locale).format(trip.tripDateTime.atZone(ColomboZone))
    }
    val km = formatDecimal(trip.pickupKm + trip.tripKm + trip.returnKm, 1)
    val detail = when (trip.status) {
        TripStatus.CANCELLED -> trip.cancellationFeeCents?.let { stringResource(R.string.history_row_cancelled_fee, km, money.format(it)) }
            ?: stringResource(R.string.history_row_cancelled_no_fee, km)
        TripStatus.OFFER_CHECK -> stringResource(R.string.history_row_offer_note)
        TripStatus.COMPLETED -> stringResource(R.string.history_row_trip, km, money.format(trip.fareCents))
    }
    val net = row.estimate?.netProfitCents
    val amount = when {
        net == null -> stringResource(R.string.history_row_unavailable)
        isOffer -> stringResource(R.string.history_offer_estimate, money.formatSigned(net))
        else -> money.formatSigned(net)
    }
    val amountColor = when {
        net == null || isOffer -> colors.onSurfaceVariant
        net < 0 -> status.loss
        else -> status.profit
    }
    // At large font sizes a side column is too narrow and would break the amount mid-number.
    val amountBelow = LocalDensity.current.fontScale > STACK_FONT_SCALE

    Surface(
        shape = shape,
        // Offers are outlined with a dashed border instead of filled, so they read as "not income".
        color = if (isOffer) Color.Transparent else colors.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .then(if (isOffer) Modifier.background(offerBackground, shape).dashedBorder(colors.outline) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {},
    ) {
        Row(
            Modifier
                .defaultMinSize(minHeight = 72.dp)
                .padding(horizontal = Spacing.l, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(colors.surfaceContainerHighest, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(trip.platform.initials), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            }
            Spacer(Modifier.width(Spacing.m))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.history_row_title, stringResource(trip.platform.label), time),
                    style = MaterialTheme.typography.titleMedium,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    if (trip.status != TripStatus.COMPLETED) StatusTag(trip.status)
                    Text(detail, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
                if (amountBelow) Text(amount, style = MaterialTheme.typography.titleMedium, color = amountColor)
            }
            if (!amountBelow) {
                Spacer(Modifier.width(Spacing.s))
                Text(
                    amount,
                    style = MaterialTheme.typography.titleMedium,
                    color = amountColor,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(0.7f),
                )
            }
        }
    }
}

@Composable
internal fun StatusTag(tripStatus: TripStatus) {
    val status = PicksmartTheme.status
    val (container, content) = when (tripStatus) {
        TripStatus.CANCELLED -> status.belowTargetContainer to status.onBelowTargetContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurface
    }
    Surface(shape = MaterialTheme.shapes.extraSmall, color = container) {
        Text(
            stringResource(tripStatus.label),
            style = MaterialTheme.typography.labelMedium,
            color = content,
            modifier = Modifier.padding(horizontal = Spacing.s, vertical = 2.dp),
        )
    }
}

private fun Modifier.dashedBorder(color: Color): Modifier = drawBehind {
    val stroke = 1.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(20.dp.toPx()),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
    )
}
