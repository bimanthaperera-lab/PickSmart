package lk.picksmart.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import lk.picksmart.R
import lk.picksmart.ui.checkride.STACK_FONT_SCALE
import lk.picksmart.ui.theme.PicksmartTheme
import lk.picksmart.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Back or close icon, a title, and optional actions on the right. Used by record screens. */
@Composable
fun DetailHeader(
    title: String,
    @DrawableRes navIcon: Int,
    navDescription: String,
    onNav: () -> Unit,
    actions: @Composable () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = Spacing.s, end = Spacing.s, top = Spacing.s),
    ) {
        IconButton(onClick = onNav) { Icon(painterResource(navIcon), contentDescription = navDescription) }
        ScreenTitle(
            title,
            Modifier
                .weight(1f)
                .padding(start = Spacing.s),
        )
        actions()
    }
}

/** A month's total at the top of a record list, e.g. "Spent on fuel this month". */
@Composable
fun MonthTotalCard(label: String, amount: String, note: String?, amountColor: Color = MaterialTheme.colorScheme.onSurface) {
    PicksmartCard(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(amount, style = MaterialTheme.typography.headlineMedium, color = amountColor)
        if (note != null) {
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** How a record's icon tile looks: the icon and its tint on a soft container. */
data class RecordIcon(@DrawableRes val icon: Int, val tint: Color, val container: Color)

/** A neutral icon tile, for costs such as fuel and expenses. */
@Composable
fun neutralRecordIcon(@DrawableRes icon: Int, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) =
    RecordIcon(icon, tint, MaterialTheme.colorScheme.surfaceContainerHighest)

/**
 * One saved record in a list: an icon tile, what it was and when, an optional [tag] such as
 * "Full tank", and its amount. Opens the record when tapped.
 */
@Composable
fun RecordRow(
    icon: RecordIcon,
    title: String,
    subtitle: String,
    amount: String,
    onClick: () -> Unit,
    amountColor: Color = MaterialTheme.colorScheme.onSurface,
    tag: String? = null,
) {
    // At large font sizes a side column for the amount leaves the details too narrow and breaks
    // numbers mid-way, so the amount moves under them (as in History).
    val amountBelow = LocalDensity.current.fontScale > STACK_FONT_SCALE
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {},
    ) {
        Row(
            Modifier
                .defaultMinSize(minHeight = 72.dp)
                .padding(horizontal = Spacing.l, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(icon)
            Spacer(Modifier.width(Spacing.m))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), itemVerticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (tag != null) RecordTag(tag)
                }
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (amountBelow) Text(amount, style = MaterialTheme.typography.titleMedium, color = amountColor)
            }
            if (!amountBelow) {
                Spacer(Modifier.width(Spacing.s))
                Text(amount, style = MaterialTheme.typography.titleMedium, color = amountColor)
            }
        }
    }
}

/** The rounded square holding a record's icon. Decorative: the row's title says what it is. */
@Composable
fun IconTile(icon: RecordIcon, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(44.dp)
            .background(icon.container, MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon.icon), contentDescription = null, tint = icon.tint, modifier = Modifier.size(24.dp))
    }
}

/** A small green label inside a row, e.g. "Full tank". */
@Composable
private fun RecordTag(text: String) {
    val status = PicksmartTheme.status
    Surface(shape = MaterialTheme.shapes.extraSmall, color = status.profitContainer) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = status.onProfitContainer,
            modifier = Modifier.padding(horizontal = Spacing.s, vertical = 2.dp),
        )
    }
}

/** An on/off setting with a label and help text. The whole row is the control. */
@Composable
fun SwitchRow(title: String, body: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .defaultMinSize(minHeight = Spacing.touchTarget)
                .padding(horizontal = Spacing.l, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(Spacing.m))
            // The row handles the toggle, so the switch itself takes no clicks.
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

/** A record's date for a list row: "20 Oct" this year, the full date otherwise. */
@Composable
fun rememberRecordDate(date: LocalDate, today: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    val pattern = stringResource(if (date.year == today.year) R.string.pattern_short_date else R.string.pattern_date_field)
    return remember(date, pattern, locale) { DateTimeFormatter.ofPattern(pattern, locale).format(date) }
}
