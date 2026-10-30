package lk.picksmart.ui.alerts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import lk.picksmart.R
import lk.picksmart.domain.model.Alert
import lk.picksmart.domain.model.AlertLink
import lk.picksmart.domain.model.AlertType
import lk.picksmart.ui.components.DetailHeader
import lk.picksmart.ui.components.EmptyState
import lk.picksmart.ui.components.IconTile
import lk.picksmart.ui.components.LoadingState
import lk.picksmart.ui.components.neutralRecordIcon
import lk.picksmart.ui.theme.Spacing
import lk.picksmart.util.ColomboZone
import lk.picksmart.util.dateFormatter
import java.time.Instant

/**
 * The in-app alerts inbox (CLAUDE.md section 9a): newest first, kept for 30 days, readable
 * offline. Opening it marks the alerts read; tapping one with a [AlertLink] opens that screen.
 */
@Composable
fun AlertsScreen(
    onBack: () -> Unit,
    onOpenLink: (AlertLink) -> Unit,
    viewModel: AlertsViewModel = viewModel(factory = AlertsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        DetailHeader(stringResource(R.string.alerts_title), R.drawable.ic_arrow_back, stringResource(R.string.action_back), onBack)
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            when (val s = state) {
                AlertsUiState.Loading -> item { LoadingState() }
                is AlertsUiState.Loaded -> if (s.alerts.isEmpty()) {
                    item {
                        EmptyState(
                            iconRes = R.drawable.ic_bell,
                            title = stringResource(R.string.alerts_empty_title),
                            body = stringResource(R.string.alerts_empty_body),
                            modifier = Modifier.padding(top = Spacing.m),
                        )
                    }
                } else {
                    items(s.alerts, key = { it.id }) { alert ->
                        AlertRow(alert, isNew = alert.id in s.newIds, onClick = alert.link?.let { { onOpenLink(it) } })
                    }
                    item {
                        Text(
                            stringResource(R.string.alerts_kept_note),
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
}

/** One alert: its icon, title (with "New" while unread), body and time. */
@Composable
private fun AlertRow(alert: Alert, isNew: Boolean, onClick: (() -> Unit)?) {
    val pattern = stringResource(R.string.pattern_backup_time)
    val locale = LocalConfiguration.current.locales[0]
    val time = remember(alert.createdAtMillis, pattern, locale) {
        Instant.ofEpochMilli(alert.createdAtMillis).atZone(ColomboZone).format(dateFormatter(pattern, locale))
    }
    val clickable = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (isNew) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .then(clickable)
            .semantics(mergeDescendants = true) {},
    ) {
        Row(
            Modifier
                .defaultMinSize(minHeight = 72.dp)
                .padding(horizontal = Spacing.l, vertical = Spacing.m),
            verticalAlignment = Alignment.Top,
        ) {
            IconTile(neutralRecordIcon(alert.type.icon, tint = MaterialTheme.colorScheme.primary))
            Spacer(Modifier.width(Spacing.m))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), itemVerticalAlignment = Alignment.CenterVertically) {
                    Text(alert.title, style = MaterialTheme.typography.titleMedium)
                    if (isNew) NewTag()
                }
                Text(alert.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The yellow "New" label on an alert that was unread when the inbox opened. */
@Composable
private fun NewTag() {
    Surface(shape = MaterialTheme.shapes.extraSmall, color = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            stringResource(R.string.alerts_new),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = Spacing.s, vertical = 2.dp),
        )
    }
}

private val AlertType.icon: Int
    get() = when (this) {
        AlertType.FUEL_PRICE -> R.drawable.ic_fuel
        else -> R.drawable.ic_bell
    }
