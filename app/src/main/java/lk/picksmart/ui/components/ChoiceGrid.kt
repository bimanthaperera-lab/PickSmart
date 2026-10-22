package lk.picksmart.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import lk.picksmart.ui.theme.Spacing

/**
 * Pick one option from a grid of large tiles, e.g. vehicle type. Behaves as a radio group for
 * screen readers. The selected tile has a yellow border and tinted fill, not colour alone: its
 * border is also thicker.
 */
@Composable
fun <T> ChoiceGrid(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 2,
) {
    Column(modifier) {
        FieldLabel(label)
        BoxWithConstraints {
            // Fall back to one column when the longest label can't fit a tile on one line (large
            // font scales on small phones), rather than breaking words like "Motorcycl-e".
            val labels = options.map { optionLabel(it) }
            val measurer = rememberTextMeasurer()
            val style = MaterialTheme.typography.titleMedium
            val widest = labels.maxOf { measurer.measure(it, style).size.width }
            val tileTextWidth = (maxWidth - Spacing.s * (columns - 1)) / columns - TilePadding * 2
            val fitColumns = if (widest <= with(LocalDensity.current) { tileTextWidth.toPx() }) columns else 1

            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                options.zip(labels).chunked(fitColumns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        row.forEach { (option, text) ->
                            ChoiceTile(text, option == selected, { onSelect(option) }, Modifier.weight(1f))
                        }
                        // Keep tile widths equal in a short last row.
                        repeat(fitColumns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

private val TilePadding = Spacing.m

@Composable
private fun ChoiceTile(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) colors.secondaryContainer else colors.surfaceContainer,
        border = if (selected) BorderStroke(2.dp, colors.primary) else BorderStroke(1.dp, colors.outline),
    ) {
        Box(
            Modifier
                .defaultMinSize(minHeight = Spacing.buttonHeight)
                .padding(horizontal = TilePadding, vertical = Spacing.s),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) colors.onSecondaryContainer else colors.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Small label above a field or group. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(bottom = Spacing.s),
    )
}
