package lk.picksmart.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import lk.picksmart.R
import lk.picksmart.ui.theme.PicksmartTheme
import lk.picksmart.ui.theme.Spacing

/**
 * An amber card that asks for attention without blocking, e.g. "Sample values in use", with an
 * optional action below.
 */
@Composable
fun NoticeCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int = R.drawable.ic_info,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val status = PicksmartTheme.status
    PicksmartCard(modifier, containerColor = status.belowTargetContainer) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.Top) {
            Icon(painterResource(iconRes), contentDescription = null, tint = status.belowTarget, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = status.onBelowTargetContainer)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = status.onBelowTargetContainer)
            }
        }
        if (actionLabel != null && onAction != null) {
            SecondaryButton(
                actionLabel,
                onAction,
                Modifier
                    .align(Alignment.End)
                    .padding(top = Spacing.m),
            )
        }
    }
}
