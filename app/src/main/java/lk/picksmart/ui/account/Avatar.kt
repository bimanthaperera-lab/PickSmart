package lk.picksmart.ui.account

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lk.picksmart.R

/**
 * The rider's photo in a circle, or their initials, or a person icon when signed out. Decorative:
 * the name next to it says who it is.
 */
@Composable
fun Avatar(photo: Bitmap?, initials: String?, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val circle = modifier
        .size(size)
        .clip(CircleShape)
    if (photo != null) {
        val image = remember(photo) { photo.asImageBitmap() }
        Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = circle)
        return
    }
    Box(circle.background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        if (initials != null) {
            Text(
                initials,
                // Sized to the circle, not the font scale: the letters must fit inside it.
                fontSize = (size.value * 0.38f).sp,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
            )
        } else {
            Icon(
                painterResource(R.drawable.ic_nav_account),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}
