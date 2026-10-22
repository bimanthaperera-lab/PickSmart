package lk.picksmart.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import lk.picksmart.R
import lk.picksmart.ui.checkride.STACK_FONT_SCALE
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
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
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
        if (primaryLabel != null && onPrimary != null) {
            // A choice: the secondary action, then the primary one. Stacked full width at large
            // font sizes so neither label is cut.
            if (LocalDensity.current.fontScale > STACK_FONT_SCALE) {
                Column(Modifier.padding(top = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    PrimaryButton(primaryLabel, onPrimary, Modifier.fillMaxWidth())
                    if (actionLabel != null && onAction != null) SecondaryButton(actionLabel, onAction, Modifier.fillMaxWidth())
                }
            } else {
                Row(
                    Modifier
                        .align(Alignment.End)
                        .padding(top = Spacing.m),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    if (actionLabel != null && onAction != null) SecondaryButton(actionLabel, onAction)
                    PrimaryButton(primaryLabel, onPrimary)
                }
            }
        } else if (actionLabel != null && onAction != null) {
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
