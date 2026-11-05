package lk.picksmart.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import lk.picksmart.R
import lk.picksmart.ui.theme.PicksmartTheme
import lk.picksmart.ui.theme.Spacing

/**
 * An amber card that asks for attention without blocking, e.g. "Sample values in use", with an
 * optional action below.
 */
@Composable
fun NoticeCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int = R.drawable.ic_info,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
) {
    val status = PicksmartTheme.status
    PicksmartCard(modifier, containerColor = status.belowTargetContainer) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.Top) {
            Icon(painterResource(iconRes), contentDescription = null, tint = status.belowTarget, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = status.onBelowTargetContainer)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = status.onBelowTargetContainer)
            }
        }
        if (primaryLabel != null && onPrimary != null) {
            NoticeButtons(primaryLabel, onPrimary, actionLabel, onAction, Modifier.padding(top = Spacing.m))
        } else if (actionLabel != null && onAction != null) {
            SecondaryButton(
                actionLabel,
                onAction,
                Modifier
                    .align(Alignment.End)
                    .padding(top = Spacing.m),
            )
        }
    }
}

/**
 * A choice: the secondary action, then the primary one, side by side at the end. When the two
 * labels don't fit on one line at their full width (a long translation, or a large font), they
 * stack full width with the primary on top, so neither label is squeezed or wrapped.
 */
@Composable
private fun NoticeButtons(
    primaryLabel: String,
    onPrimary: () -> Unit,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val buttons = @Composable {
        PrimaryButton(primaryLabel, onPrimary)
        if (actionLabel != null && onAction != null) SecondaryButton(actionLabel, onAction)
    }
    // The buttons are measured at their natural width, not from intrinsic widths: on a Samsung
    // phone in Sinhala the primary button came out as a thin bar beside the other one, and
    // measuring them directly doesn't depend on how a font reports its intrinsic width.
    SubcomposeLayout(modifier.fillMaxWidth()) { constraints ->
        val gap = Spacing.s.roundToPx()
        val width = constraints.maxWidth
        val inline = subcompose(NoticeSlot.INLINE, buttons).map { it.measure(Constraints(maxWidth = width)) }
        if (inline.sumOf { it.width } + gap * (inline.size - 1) <= width) {
            val placeables = inline
            val height = placeables.maxOf { it.height }
            layout(width, height) {
                var x = width
                placeables.forEach {
                    x -= it.width
                    it.placeRelative(x, (height - it.height) / 2)
                    x -= gap
                }
            }
        } else {
            val placeables = subcompose(NoticeSlot.STACKED, buttons).map { it.measure(Constraints.fixedWidth(width)) }
            layout(width, placeables.sumOf { it.height } + gap * (placeables.size - 1)) {
                var y = 0
                placeables.forEach {
                    it.placeRelative(0, y)
                    y += it.height + gap
                }
            }
        }
    }
}

/**
 * A quiet, neutral note with an info icon, e.g. why a report may be incomplete. Unlike
 * [NoticeCard] it doesn't ask for attention; the optional action is a text button.
 */
@Composable
fun InfoNote(
    text: String,
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int = R.drawable.ic_info,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    PicksmartCard(modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.Top) {
            Icon(
                painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) { Text(actionLabel) }
        }
    }
}

private enum class NoticeSlot { INLINE, STACKED }
