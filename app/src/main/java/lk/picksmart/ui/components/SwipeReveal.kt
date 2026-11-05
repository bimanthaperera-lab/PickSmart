package lk.picksmart.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import lk.picksmart.ui.theme.Spacing
import kotlin.math.roundToInt

/** A button shown behind a row once the row is swiped aside. */
data class SwipeAction(
    val label: String,
    @DrawableRes val iconRes: Int,
    val container: Color,
    val content: Color,
    val onClick: () -> Unit,
)

/** Which edge's action is showing: [LEFT] after a swipe to the right, [RIGHT] after a swipe to the left. */
enum class SwipeSide { LEFT, RIGHT }

/**
 * A row that slides sideways to show one action on each edge. The row stays put until a swipe
 * passes half the action's width (or is flung), then rests open until [revealed] changes, so only
 * one row in a list is open at a time when the caller keeps one shared value.
 *
 * Swiping only reveals the buttons; nothing happens until one is tapped. Swipes aren't available to
 * screen readers, so give [content] the same actions as semantics custom actions.
 */
@Composable
fun SwipeRevealRow(
    leftAction: SwipeAction,
    rightAction: SwipeAction,
    revealed: SwipeSide?,
    onRevealedChange: (SwipeSide?) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    content: @Composable () -> Unit,
) {
    val reach = with(LocalDensity.current) { SWIPE_REACH.toPx() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scope = rememberCoroutineScope()
    var offsetX by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf<Job?>(null) }

    fun settle(side: SwipeSide?, velocity: Float = 0f) {
        val target = when (side) {
            SwipeSide.LEFT -> reach
            SwipeSide.RIGHT -> -reach
            null -> 0f
        }
        settling?.cancel()
        settling = scope.launch { animate(offsetX, target, velocity) { value, _ -> offsetX = value } }
    }

    // Follows changes from outside, such as another row opening or an action being tapped.
    LaunchedEffect(revealed) { settle(revealed) }

    Box(
        modifier
            .fillMaxWidth()
            .draggable(
                state = rememberDraggableState { delta -> offsetX = (offsetX + delta).coerceIn(-reach, reach) },
                orientation = Orientation.Horizontal,
                // Drag deltas follow the layout direction; the offset below is always screen left-to-right.
                reverseDirection = rtl,
                onDragStarted = { settling?.cancel() },
                onDragStopped = { velocity ->
                    val screenVelocity = if (rtl) -velocity else velocity
                    val projected = offsetX + screenVelocity * FLING_PROJECTION_SECONDS
                    val side = when {
                        offsetX > 0 && projected > reach / 2 -> SwipeSide.LEFT
                        offsetX < 0 && projected < -reach / 2 -> SwipeSide.RIGHT
                        else -> null
                    }
                    settle(side, screenVelocity)
                    if (side != revealed) onRevealedChange(side)
                },
            ),
    ) {
        // Drawn only while the row is moved, so nothing shows through its rounded corners at rest.
        if (offsetX != 0f) {
            Box(Modifier.matchParentSize()) {
                val action = if (offsetX > 0) leftAction else rightAction
                ActionButton(
                    action,
                    shape,
                    Modifier
                        .align(if (offsetX > 0) AbsoluteAlignment.CenterLeft else AbsoluteAlignment.CenterRight)
                        .width(SWIPE_REACH - ACTION_GAP)
                        .fillMaxHeight(),
                )
            }
        }
        Box(Modifier.absoluteOffset { IntOffset(offsetX.roundToInt(), 0) }) { content() }
    }
}

@Composable
private fun ActionButton(action: SwipeAction, shape: Shape, modifier: Modifier) {
    Column(
        modifier
            .clip(shape)
            .background(action.container)
            .clickable(role = Role.Button, onClick = action.onClick)
            .padding(Spacing.xs),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(action.iconRes), contentDescription = null, tint = action.content, modifier = Modifier.size(24.dp))
        Text(
            action.label,
            style = MaterialTheme.typography.labelMedium,
            color = action.content,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/** How far a row moves to show an action: the action's width plus a small gap. */
private val SWIPE_REACH = 96.dp
private val ACTION_GAP = 8.dp

/** A fling counts as travelling this long at its release speed when deciding where to rest. */
private const val FLING_PROJECTION_SECONDS = 0.15f
