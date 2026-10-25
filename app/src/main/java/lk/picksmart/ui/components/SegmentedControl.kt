package lk.picksmart.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lk.picksmart.ui.theme.Spacing

/**
 * A row of segments with one selected, e.g. Day / Week / Month on Reports. The selected segment is
 * filled and bold, not colour alone, and the group reads as radio buttons. [accent] gives it an
 * outline and a brand-tinted selection, for a toggle that sits beside other controls (Estimated /
 * Cash); it is also more compact, about 44 dp tall, and Compose still extends each segment's touch
 * area to 48 dp. With [fill] each segment takes an equal share of the width.
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    fill: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = if (accent) colors.surface else colors.surfaceContainer,
        border = if (accent) BorderStroke(1.dp, colors.outline) else null,
    ) {
        Row(
            Modifier
                .padding(if (accent) ACCENT_INSET else Spacing.xs)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                Surface(
                    modifier = Modifier
                        .then(if (fill) Modifier.weight(1f) else Modifier)
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option) }),
                    shape = MaterialTheme.shapes.medium,
                    color = when {
                        !isSelected -> colors.surfaceContainer.copy(alpha = 0f)
                        accent -> colors.secondaryContainer
                        else -> colors.surfaceContainerHighest
                    },
                ) {
                    Box(
                        Modifier
                            .defaultMinSize(minHeight = if (accent) ACCENT_SEGMENT_HEIGHT else Spacing.touchTarget)
                            .padding(
                                horizontal = if (fill) Spacing.s else Spacing.m,
                                vertical = if (accent) Spacing.xs else Spacing.s,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        // One line, shrinking to fit its share of the width at large font sizes, so
                        // "Month" never breaks mid-word. The floor is in dp so it stays small.
                        val style = if (accent) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall
                        BasicText(
                            optionLabel(option),
                            style = style.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) colors.onSurface else colors.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            ),
                            maxLines = 1,
                            softWrap = false,
                            autoSize = TextAutoSize.StepBased(
                                minFontSize = with(LocalDensity.current) { 10.dp.toSp() },
                                maxFontSize = style.fontSize,
                                stepSize = 0.5.sp,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** Height of one segment in the compact [SegmentedControl] with `accent`. */
private val ACCENT_SEGMENT_HEIGHT = 36.dp

/** Gap between the compact control's outline and its segments. */
private val ACCENT_INSET = 3.dp
