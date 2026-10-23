package lk.picksmart.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import lk.picksmart.R
import lk.picksmart.ui.components.Wordmark
import lk.picksmart.ui.theme.NearBlack
import lk.picksmart.ui.theme.Spacing
import kotlin.math.roundToInt

// Android 12+ centres the launcher icon on its splash. On a dark background, which matches the
// icon's own, it drops the icon background and draws the icon at 288 dp; on a light one it keeps
// the near-black circle and draws it at 240 dp. In the 108-unit adaptive icon the circle is 72
// units and the pin 58 (design/brand), giving the sizes below. The splash starts with the pin
// exactly there, so the handoff from the system splash can't be seen.
private val SystemCircleDark = 192.dp
private val SystemCircleLight = 160.dp
private const val PinPerCircle = 58f / 72f
private const val PinAspect = 304.3f / 434.0f // picksmart_mark viewport, width / height

/** The pin settles in a [FinalCircle] circle, [PinRise] above centre. */
private val FinalCircle = 120.dp
private val PinRise = 56.dp
private val WordsGap = 24.dp

private const val PinMillis = 500
private const val WordsDelayMillis = 200
private const val WordsMillis = 400
private const val ActionsMillis = 300

/**
 * Brand screen at launch, in the app's own light or dark theme. It opens exactly like the system
 * splash (the pin alone, centred), then the pin shrinks and rises while the "Picksmart" wordmark
 * and the tagline fade in under it.
 */
@Composable
fun SplashScreen() {
    val pin = remember { Animatable(0f) }
    val words = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { pin.animateTo(1f, tween(PinMillis, easing = FastOutSlowInEasing)) }
        launch { words.animateTo(1f, tween(WordsMillis, WordsDelayMillis, FastOutSlowInEasing)) }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        SplashLogo(pinProgress = pin.value, wordsProgress = words.value, screenHeight = maxHeight)
    }
}

/**
 * The first-launch welcome: the splash's last frame, with [actions] ("Get started" and so on)
 * fading in at the bottom. The logo sits exactly where the splash leaves it, so the splash hands
 * over to this screen without anything moving. At large font sizes the page scrolls.
 */
@Composable
fun WelcomeScreen(actions: @Composable ColumnScope.() -> Unit) {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) { shown.animateTo(1f, tween(ActionsMillis, easing = FastOutSlowInEasing)) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val screenHeight = maxHeight
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = screenHeight),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            SplashLogo(pinProgress = 1f, wordsProgress = 1f, screenHeight = screenHeight)
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.xl, bottom = Spacing.l)
                    .graphicsLayer {
                        alpha = shown.value
                        translationY = (1f - shown.value) * 8.dp.toPx()
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
                content = actions,
            )
        }
    }
}

/**
 * The pin and, under it, the wordmark and tagline, laid out against the full [screenHeight] so the
 * splash and the welcome screen put them in the same place. [pinProgress] 0 is the system splash's
 * pin; 1 is the settled pin. [wordsProgress] fades the words in.
 */
@Composable
private fun SplashLogo(pinProgress: Float, wordsProgress: Float, screenHeight: Dp) {
    val onLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val startCircle = if (onLight) SystemCircleLight else SystemCircleDark
    val finalScale = FinalCircle / startCircle
    val pinHeight = startCircle * PinPerCircle

    Box(Modifier.fillMaxWidth()) {
        // The circle is the launcher icon's background. On the dark theme it matches the screen,
        // so only the pin shows, as on the system splash; on the light one it frames the pin.
        // Drawn at the start size and scaled down, so the pin stays sharp.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = (screenHeight - startCircle) / 2)
                .offset { IntOffset(0, -(PinRise.toPx() * pinProgress).roundToInt()) }
                .size(startCircle)
                .graphicsLayer {
                    val scale = 1f - (1f - finalScale) * pinProgress
                    scaleX = scale
                    scaleY = scale
                }
                .background(NearBlack, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painterResource(R.drawable.picksmart_mark),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(pinHeight * PinAspect, pinHeight),
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(
                    top = screenHeight / 2 - PinRise + FinalCircle / 2 + WordsGap,
                    start = Spacing.screen,
                    end = Spacing.screen,
                )
                .graphicsLayer {
                    alpha = wordsProgress
                    translationY = (1f - wordsProgress) * 8.dp.toPx()
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Wordmark(
                Modifier
                    .widthIn(max = 220.dp)
                    .fillMaxWidth(),
            )
            Text(
                stringResource(R.string.tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.l),
            )
        }
    }
}
