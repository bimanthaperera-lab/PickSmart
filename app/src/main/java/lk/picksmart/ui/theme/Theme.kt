package lk.picksmart.ui.theme

import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

// primary is the accent for text, focus and selection; primaryContainer is the yellow fill used
// by primary buttons. They are the same yellow in dark mode and split in light mode for contrast.
private val DarkColors = darkColorScheme(
    primary = BrandYellow,
    onPrimary = Ink,
    primaryContainer = BrandYellow,
    onPrimaryContainer = Ink,
    secondaryContainer = DarkSelectedContainer,
    onSecondaryContainer = BrandYellow,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkBackground,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceHigh,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceHigh,
    surfaceContainerHighest = DarkSurfaceHigh,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    error = DarkError,
    onError = Ink,
)

private val LightColors = lightColorScheme(
    primary = LightAccent,
    onPrimary = LightSurface,
    primaryContainer = BrandYellow,
    onPrimaryContainer = Ink,
    secondaryContainer = LightSelectedContainer,
    onSecondaryContainer = LightOnSelectedContainer,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightBackground,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceHigh,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurfaceHigh,
    surfaceContainerHighest = LightSurfaceHigh,
    outline = LightOutline,
    outlineVariant = LightOutline,
    error = LightError,
    onError = LightSurface,
)

@Composable
fun PicksmartTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalStatusColors provides if (darkTheme) DarkStatusColors else LightStatusColors,
        LocalRippleConfiguration provides NoHoverRipple,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = PicksmartTypography,
            shapes = PicksmartShapes,
            content = content,
        )
    }
}

/**
 * Material's ripple without the hover shade. A pointer resting over a control (the emulator sends
 * the computer's mouse as a hovering stylus) kept buttons and rows lighter after a tap, which read
 * as stuck. Fingers can't hover, so riders lose nothing; press and keyboard focus keep Material's
 * values, so focus stays visible for keyboard and switch users.
 */
private val NoHoverRipple = RippleConfiguration(
    rippleAlpha = RippleAlpha(draggedAlpha = 0.16f, focusedAlpha = 0.1f, hoveredAlpha = 0f, pressedAlpha = 0.1f),
)

/** Access to Picksmart-specific theme values, alongside [MaterialTheme]. */
object PicksmartTheme {
    val status: StatusColors
        @Composable
        @ReadOnlyComposable
        get() = LocalStatusColors.current
}
