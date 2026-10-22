package lk.picksmart.ui.theme

import androidx.compose.material3.MaterialTheme
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
    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatusColors else LightStatusColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = PicksmartTypography,
            shapes = PicksmartShapes,
            content = content,
        )
    }
}

/** Access to Picksmart-specific theme values, alongside [MaterialTheme]. */
object PicksmartTheme {
    val status: StatusColors
        @Composable
        @ReadOnlyComposable
        get() = LocalStatusColors.current
}
