package lk.picksmart.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import lk.picksmart.ui.theme.Spacing

/**
 * Pick one option from a grid of large tiles, e.g. vehicle type. Behaves as a radio group for
 * screen readers. The selected tile has a yellow border and tinted fill, not colour alone: its
 * border is also thicker.
 *
 * With [optionIcon] each tile shows a small icon (the same 20 dp as [ChoiceChips]) before its
 * label, keeping the plain tiles' height so icon and plain grids match on one form (Edit vehicle).
 * Labels may wrap between words, tiles in a row share one height, and an odd last tile takes the
 * whole row (e.g. "Other" under four expense categories).
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
    optionIcon: ((T) -> Int)? = null,
) {
    Column(modifier) {
        FieldLabel(label)
        BoxWithConstraints {
            // Fall back to one column when a label can't fit a tile (large font scales on small
            // phones), rather than breaking words like "Motorcycl-e". Plain tiles keep each label
            // on one line; icon tiles may wrap between words, so only the longest word must fit.
            val labels = options.map { optionLabel(it) }
            val measurer = rememberTextMeasurer()
            val style = MaterialTheme.typography.titleMedium
            val widest = labels
                .flatMap { if (optionIcon != null) it.split(' ') else listOf(it) }
                .maxOf { measurer.measure(it, style).size.width }
            val iconSpace = if (optionIcon != null) TileIconSize + Spacing.s else 0.dp
            val tileTextWidth = (maxWidth - Spacing.s * (columns - 1)) / columns - TilePadding * 2 - iconSpace
            val fitColumns = if (widest <= with(LocalDensity.current) { tileTextWidth.toPx() }) columns else 1

            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                options.zip(labels).chunked(fitColumns).forEach { row ->
                    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        row.forEach { (option, text) ->
                            ChoiceTile(
                                text = text,
                                icon = optionIcon?.invoke(option),
                                selected = option == selected,
                                onClick = { onSelect(option) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                        }
                        // Plain tiles keep equal widths in a short last row; icon tiles fill it.
                        if (optionIcon == null) repeat(fitColumns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

private val TilePadding = Spacing.m
private val TileIconSize = 20.dp

@Composable
private fun ChoiceTile(text: String, @DrawableRes icon: Int?, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val content = if (selected) colors.onSecondaryContainer else colors.onSurface
    Surface(
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) colors.secondaryContainer else colors.surfaceContainer,
        border = if (selected) BorderStroke(2.dp, colors.primary) else BorderStroke(1.dp, colors.outline),
    ) {
        Row(
            Modifier
                .defaultMinSize(minHeight = Spacing.buttonHeight)
                .padding(horizontal = TilePadding, vertical = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                // Decorative: the label says what the option is.
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    tint = if (selected) colors.primary else colors.onSurfaceVariant,
                    modifier = Modifier.size(TileIconSize),
                )
            }
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                color = content,
                textAlign = if (icon != null) TextAlign.Start else TextAlign.Center,
                modifier = if (icon != null) Modifier else Modifier.fillMaxWidth(),
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
