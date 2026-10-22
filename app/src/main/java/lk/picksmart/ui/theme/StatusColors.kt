package lk.picksmart.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colours for money outcomes, which Material's scheme has no roles for. Each is always shown
 * with a text label and an icon, never on its own (CLAUDE.md section 9).
 */
@Immutable
data class StatusColors(
    val profit: Color,
    val profitContainer: Color,
    val onProfitContainer: Color,
    val loss: Color,
    val lossContainer: Color,
    val onLossContainer: Color,
    val belowTarget: Color,
    val belowTargetContainer: Color,
    val onBelowTargetContainer: Color,
)

val DarkStatusColors = StatusColors(
    profit = DarkProfit,
    profitContainer = DarkProfitContainer,
    onProfitContainer = DarkOnProfitContainer,
    loss = DarkLoss,
    lossContainer = DarkLossContainer,
    onLossContainer = DarkOnLossContainer,
    belowTarget = DarkBelowTarget,
    belowTargetContainer = DarkBelowTargetContainer,
    onBelowTargetContainer = DarkOnBelowTargetContainer,
)

val LightStatusColors = StatusColors(
    profit = LightProfit,
    profitContainer = LightProfitContainer,
    onProfitContainer = LightOnProfitContainer,
    loss = LightLoss,
    lossContainer = LightLossContainer,
    onLossContainer = LightOnLossContainer,
    belowTarget = LightBelowTarget,
    belowTargetContainer = LightBelowTargetContainer,
    onBelowTargetContainer = LightOnBelowTargetContainer,
)

val LocalStatusColors = staticCompositionLocalOf { DarkStatusColors }
