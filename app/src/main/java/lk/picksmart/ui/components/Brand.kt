package lk.picksmart.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import lk.picksmart.R
import lk.picksmart.ui.theme.NearBlack
import lk.picksmart.util.MoneyFormatter

/**
 * The pin mark. On a dark background it is drawn as is; on a light one it sits in a near-black
 * circle, because its yellow and off-white parts vanish on light surfaces (CLAUDE.md section 9).
 */
@Composable
fun BrandMark(modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val onLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val description = stringResource(R.string.app_name)
    if (onLight) {
        Box(
            modifier
                .size(size)
                .background(NearBlack, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(R.drawable.picksmart_mark), description, Modifier.size(size * 0.62f))
        }
    } else {
        Image(painterResource(R.drawable.picksmart_mark), description, modifier.size(size))
    }
}

/** The app's [MoneyFormatter], using the translatable currency prefix from `strings.xml`. */
@Composable
fun rememberMoneyFormatter(): MoneyFormatter {
    val prefix = stringResource(R.string.currency_prefix)
    return remember(prefix) { MoneyFormatter(prefix) }
}
