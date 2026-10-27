package lk.picksmart.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import lk.picksmart.ui.checkride.STACK_FONT_SCALE
import lk.picksmart.ui.theme.Spacing

/** A rounded, filled card. Content is padded and stacked vertically. */
@Composable
fun PicksmartCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = containerColor,
    ) {
        Column(Modifier.padding(Spacing.l), content = content)
    }
}

/** A small card with a label above a large value, e.g. "Gross income / Rs. 6,850.00". */
@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    PicksmartCard(modifier) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor)
    }
}

/** One figure for [StatGrid]. */
data class Stat(val label: String, val value: String, val valueColor: Color? = null)

/**
 * Small stat cards two to a row, each pair the same height. Above [STACK_FONT_SCALE] they take one
 * row each, so large numbers never wrap mid-way.
 */
@Composable
fun StatGrid(stats: List<Stat>, modifier: Modifier = Modifier) {
    val perRow = if (LocalDensity.current.fontScale > STACK_FONT_SCALE) 1 else 2
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        stats.chunked(perRow).forEach { row ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                row.forEach { stat ->
                    StatCard(
                        stat.label,
                        stat.value,
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .semantics(mergeDescendants = true) {},
                        valueColor = stat.valueColor ?: MaterialTheme.colorScheme.onSurface,
                    )
                }
                // An odd last card keeps its half width.
                if (row.size < perRow) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Screen title at the top of each tab; shrinks a little to stay on one line in longer languages. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    FitText(
        text = text,
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.semantics { heading() },
    )
}

/** Small grey label above a group of rows, e.g. "Records" or "App". */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .padding(start = Spacing.s, top = Spacing.l, bottom = Spacing.s)
            .semantics { heading() },
    )
}
