package lk.picksmart.ui.onboarding

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import lk.picksmart.R
import lk.picksmart.ui.components.PicksmartCard
import lk.picksmart.ui.components.PrimaryButton
import lk.picksmart.ui.components.ScreenTitle
import lk.picksmart.ui.components.SecondaryButton
import lk.picksmart.ui.settings.DisclosureDialog
import lk.picksmart.ui.settings.rememberBubbleSwitch
import lk.picksmart.ui.settings.rememberReaderEnabled
import lk.picksmart.ui.settings.rememberTurnOn
import lk.picksmart.ui.theme.Spacing

/**
 * The optional permissions, asked during setup (owner decisions 2026-10-26 and 2026-10-30):
 * notifications, for "Save this trip?" and reminders; Read ride offers, which only the rider can
 * turn on in Android's Accessibility settings, after the disclosure (CLAUDE.md section 9b); and the
 * floating bubble, after its overlay warning (section 9a). All can be skipped; the app works without them. Each row shows its state again when the rider comes back
 * from Android's settings.
 */
@Composable
internal fun PermissionsStep(onContinue: () -> Unit) {
    ScreenTitle(stringResource(R.string.onboarding_permissions_title))
    Text(
        stringResource(R.string.onboarding_permissions_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    NotificationsRow()
    ReaderRow()
    BubbleRow()
    PrimaryButton(stringResource(R.string.action_continue), onContinue, Modifier.fillMaxWidth())
}

@Composable
private fun NotificationsRow() {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(context.notificationsAllowed()) }
    // After a refusal Android may not ask again, so the button opens the app's notification settings.
    var refused by rememberSaveable { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted
        refused = !granted
    }
    LifecycleResumeEffect(Unit) {
        allowed = context.notificationsAllowed()
        onPauseOrDispose { }
    }
    PermissionCard(
        iconRes = R.drawable.ic_bell,
        title = stringResource(R.string.permission_notifications_title),
        body = stringResource(R.string.permission_notifications_body),
        granted = allowed,
        grantedLabel = stringResource(R.string.permission_allowed),
        actionLabel = stringResource(if (refused) R.string.permission_open_settings else R.string.permission_allow),
        onAction = {
            if (!refused && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ask.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.openNotificationSettings()
            }
        },
    )
}

@Composable
private fun ReaderRow() {
    val (enabled, _) = rememberReaderEnabled()
    val turnOn = rememberTurnOn()
    var showDisclosure by rememberSaveable { mutableStateOf(false) }
    PermissionCard(
        iconRes = R.drawable.ic_visibility,
        title = stringResource(R.string.offer_reader_title),
        body = stringResource(R.string.permission_reader_body),
        granted = enabled,
        grantedLabel = stringResource(R.string.permission_on),
        actionLabel = stringResource(R.string.permission_turn_on),
        onAction = { showDisclosure = true },
    )
    if (showDisclosure) {
        DisclosureDialog(
            onAccept = {
                showDisclosure = false
                turnOn()
            },
            onDismiss = { showDisclosure = false },
        )
    }
}

/** The floating bubble, which needs "Display over other apps" (owner decision 2026-10-30). */
@Composable
private fun BubbleRow() {
    val bubble = rememberBubbleSwitch()
    PermissionCard(
        iconRes = R.drawable.ic_pin,
        title = stringResource(R.string.bubble_title),
        body = stringResource(R.string.permission_bubble_body),
        granted = bubble.isShowing,
        grantedLabel = stringResource(R.string.permission_on),
        actionLabel = stringResource(R.string.permission_turn_on),
        onAction = bubble.turnOn,
    )
}

@Composable
private fun PermissionCard(
    @DrawableRes iconRes: Int,
    title: String,
    body: String,
    granted: Boolean,
    grantedLabel: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    PicksmartCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.Top) {
                Icon(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(24.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (granted) {
                Row(
                    Modifier.heightIn(min = Spacing.touchTarget),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_band_good),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(grantedLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            } else {
                SecondaryButton(actionLabel, onAction, Modifier.fillMaxWidth())
            }
        }
    }
}

/** False when notifications are off for Picksmart, including when Android 13+'s permission isn't granted. */
private fun Context.notificationsAllowed(): Boolean = NotificationManagerCompat.from(this).areNotificationsEnabled()

private fun Context.openNotificationSettings() {
    try {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: ActivityNotFoundException) {
        // Every phone has these settings; nothing else to offer if one somehow doesn't.
    }
}
