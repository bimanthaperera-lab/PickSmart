package lk.picksmart.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import lk.picksmart.R
import lk.picksmart.ui.components.BrandMark
import lk.picksmart.ui.components.Wordmark
import lk.picksmart.ui.theme.Spacing

/** How long the logo takes to appear. The tagline follows [TaglineDelayMillis] later. */
private const val SplashEnterMillis = 400
private const val TaglineDelayMillis = 120

/**
 * Brand screen shown briefly at launch, in the app's own light or dark theme: the pin mark, the
 * "Picksmart" wordmark and the tagline. The system splash before it shows only the background,
 * so the background is drawn at once and only the logo and tagline fade and grow in.
 */
@Composable
fun SplashScreen() {
    val logo = remember { Animatable(0f) }
    val tagline = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { logo.animateTo(1f, tween(SplashEnterMillis, easing = FastOutSlowInEasing)) }
        launch {
            tagline.animateTo(
                1f,
                tween(SplashEnterMillis - TaglineDelayMillis, TaglineDelayMillis, FastOutSlowInEasing),
            )
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(Spacing.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            Modifier.graphicsLayer {
                alpha = logo.value
                val scale = 0.9f + 0.1f * logo.value
                scaleX = scale
                scaleY = scale
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandMark(size = 96.dp)
            Wordmark(
                Modifier
                    .padding(top = Spacing.xl)
                    .widthIn(max = 220.dp)
                    .fillMaxWidth(),
            )
        }
        Text(
            stringResource(R.string.tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = Spacing.l)
                .graphicsLayer {
                    alpha = tagline.value
                    translationY = (1f - tagline.value) * 8.dp.toPx()
                },
        )
    }
}
