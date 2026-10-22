package lk.picksmart.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import lk.picksmart.ui.theme.Spacing

/**
 * Pick one option from a row of pills, e.g. the platform. The row scrolls sideways when the pills
 * don't fit (small phones, large fonts). Behaves as a radio group for screen readers; the selected
 * pill has a thicker yellow border and bold text, not colour alone.
 */
@Composable
fun <T> ChoiceChips(
    options: List<T>,
    selected: T?,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val colors = MaterialTheme.colorScheme
            Surface(
                modifier = Modifier.selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option) }),
                shape = MaterialTheme.shapes.extraLarge,
                color = if (isSelected) colors.secondaryContainer else colors.surfaceContainer,
                border = if (isSelected) BorderStroke(2.dp, colors.primary) else BorderStroke(1.dp, colors.outline),
            ) {
                Box(
                    Modifier
                        .defaultMinSize(minHeight = Spacing.touchTarget)
                        .padding(horizontal = Spacing.l, vertical = Spacing.s),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        optionLabel(option),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) colors.onSecondaryContainer else colors.onSurface,
                    )
                }
            }
        }
    }
}
