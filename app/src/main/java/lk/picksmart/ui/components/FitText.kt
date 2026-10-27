package lk.picksmart.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified

/**
 * Text that shrinks a little, down to [minScale] of its size, until it fits in [lines] lines
 * without breaking a word. Sinhala and Tamil labels are often much longer than English, and on a
 * 360 dp phone they would otherwise take an extra line or split a word. When even the smallest
 * size doesn't fit, it wraps at that size rather than being cut off, so nothing is ever hidden.
 */
@Composable
fun FitText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    lines: Int = 1,
    minScale: Float = 0.75f,
) {
    var scale by remember(text, style, lines) { mutableFloatStateOf(1f) }
    var settled by remember(text, style, lines) { mutableStateOf(false) }
    Text(
        text = text,
        color = color,
        textAlign = textAlign,
        style = style.copy(fontSize = style.fontSize.scaled(scale), lineHeight = style.lineHeight.scaled(scale)),
        onTextLayout = { layout ->
            if (settled) return@Text
            if ((layout.lineCount > lines || layout.breaksMidWord(text)) && scale > minScale) {
                scale = (scale - STEP).coerceAtLeast(minScale)
            } else {
                settled = true
            }
        },
        // The sizes tried on the way are never drawn, so the text doesn't flicker.
        modifier = modifier.drawWithContent { if (settled) drawContent() },
    )
}

private const val STEP = 0.05f

private fun TextUnit.scaled(factor: Float): TextUnit = if (isSpecified) this * factor else this

/** True when a line ends inside a word, as a long word does when it can't fit the width. */
private fun TextLayoutResult.breaksMidWord(text: String): Boolean = (0 until lineCount - 1).any { line ->
    val end = getLineEnd(line)
    end in 1 until text.length && !text[end - 1].isWhitespace() && !text[end].isWhitespace()
}
