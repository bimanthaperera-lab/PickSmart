package lk.picksmart.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Spacing scale. Screens use [screen] as their side padding. */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val screen = 20.dp

    /** Minimum touch target (CLAUDE.md section 9). */
    val touchTarget = 48.dp

    /** Height of primary and secondary buttons. */
    val buttonHeight = 56.dp
}

val PicksmartShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    // Inputs and small cards.
    medium = RoundedCornerShape(16.dp),
    // Cards and buttons.
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
