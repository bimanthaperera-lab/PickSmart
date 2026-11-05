package lk.picksmart.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import lk.picksmart.ui.theme.Spacing

/**
 * [start] and [end] on one line when both fit at their natural width, with [end] at the far side;
 * otherwise [end] goes under [start] and is given the full width (`stacked` is then true, so it
 * can fill it). The decision comes from measuring, not from the language or the font size, so a
 * long translation or a narrow phone never squeezes either part into a sliver.
 *
 * It is a SubcomposeLayout, so it must not sit inside a parent that asks for intrinsic sizes.
 */
@Composable
fun AdaptiveRow(
    start: @Composable () -> Unit,
    end: @Composable (stacked: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    gap: Dp = Spacing.s,
) {
    SubcomposeLayout(modifier.fillMaxWidth()) { constraints ->
        val width = constraints.maxWidth
        val loose = Constraints(maxWidth = width)
        val gapPx = gap.roundToPx()
        val first = subcompose(Slot.START) { Box { start() } }.single().measure(loose)
        val inline = subcompose(Slot.INLINE) { Box { end(false) } }.single().measure(loose)
        if (first.width + gapPx + inline.width <= width) {
            val height = maxOf(first.height, inline.height)
            layout(width, height) {
                first.placeRelative(0, (height - first.height) / 2)
                inline.placeRelative(width - inline.width, (height - inline.height) / 2)
            }
        } else {
            val below = subcompose(Slot.STACKED) { Box { end(true) } }.single()
                .measure(Constraints(minWidth = width, maxWidth = width))
            layout(width, first.height + gapPx + below.height) {
                first.placeRelative(0, 0)
                below.placeRelative(0, first.height + gapPx)
            }
        }
    }
}

private enum class Slot { START, INLINE, STACKED }
