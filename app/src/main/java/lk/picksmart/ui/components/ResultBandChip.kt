package lk.picksmart.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import lk.picksmart.R
import lk.picksmart.domain.calculator.ResultBand
import lk.picksmart.ui.theme.PicksmartTheme
import lk.picksmart.ui.theme.Spacing

/**
 * How a [ResultBand] looks: always a text label and an icon, plus colour. [content] is the band
 * colour for labels and icons; [onContainer] is for large text on [container].
 */
data class BandStyle(
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    val content: Color,
    val container: Color,
    val onContainer: Color,
)

@Composable
fun ResultBand.style(): BandStyle {
    val status = PicksmartTheme.status
    return when (this) {
        ResultBand.GOOD -> BandStyle(
            R.string.band_good,
            R.drawable.ic_band_good,
            status.profit,
            status.profitContainer,
            status.onProfitContainer,
        )
        ResultBand.PROFIT -> BandStyle(
            R.string.band_profit,
            R.drawable.ic_band_profit,
            status.profit,
            status.profitContainer,
            status.onProfitContainer,
        )
        ResultBand.BELOW_TARGET -> BandStyle(
            R.string.band_below_target,
            R.drawable.ic_band_below_target,
            status.belowTarget,
            status.belowTargetContainer,
            status.onBelowTargetContainer,
        )
        ResultBand.LOSS -> BandStyle(
            R.string.band_loss,
            R.drawable.ic_band_loss,
            status.loss,
            status.lossContainer,
            status.onLossContainer,
        )
        ResultBand.BREAK_EVEN -> BandStyle(
            R.string.band_break_even,
            R.drawable.ic_band_break_even,
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** A pill showing the band's icon and label, e.g. "✓ Good". */
@Composable
fun ResultBandChip(band: ResultBand, modifier: Modifier = Modifier) {
    val style = band.style()
    Surface(modifier = modifier, shape = MaterialTheme.shapes.extraLarge, color = style.container) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s - 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The label carries the meaning, so the icon is decorative for screen readers.
            Icon(painterResource(style.icon), contentDescription = null, tint = style.content, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.s - 2.dp))
            Text(stringResource(style.label), style = MaterialTheme.typography.labelLarge, color = style.content)
        }
    }
}
