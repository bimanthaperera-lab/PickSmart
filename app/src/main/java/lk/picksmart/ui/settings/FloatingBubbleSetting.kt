package lk.picksmart.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import lk.picksmart.R
import lk.picksmart.glance.FloatingBubbleService
import lk.picksmart.ui.theme.Spacing

/**
 * "Floating bubble" under While riding (CLAUDE.md section 9a). Off by default. Turning it on is
 * [rememberBubbleSwitch]; turning it off, or holding the bubble, hides it.
 */
@Composable
internal fun FloatingBubbleRow(viewModel: OfferReaderViewModel) {
    val bubble = rememberBubbleSwitch(viewModel)
    SwitchListRow(
        title = stringResource(R.string.bubble_title),
        subtitle = stringResource(if (bubble.on && !bubble.allowed) R.string.bubble_permission_needed else R.string.bubble_body),
        checked = bubble.isShowing,
        onCheckedChange = { turnOn -> if (turnOn) bubble.turnOn() else bubble.turnOff() },
    )
}

/** The bubble's switch: [on] is the rider's choice, [allowed] Android's "Display over other apps". */
internal class BubbleSwitch(val on: Boolean, val allowed: Boolean, val turnOn: () -> Unit, val turnOff: () -> Unit) {
    val isShowing: Boolean get() = on && allowed
}

/**
 * The floating bubble's state and the way to turn it on, for While riding and setup's Permissions
 * step. Without the permission, [BubbleSwitch.turnOn] first explains "Display over other apps" and
 * warns that some ride apps ignore taps under an overlay, then opens Android's page for it; coming
 * back with it allowed turns the bubble on.
 */
@Composable
internal fun rememberBubbleSwitch(viewModel: OfferReaderViewModel = viewModel(factory = OfferReaderViewModel.Factory)): BubbleSwitch {
    val context = LocalContext.current
    val on by viewModel.bubble.collectAsStateWithLifecycle()
    var allowed by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var showWarning by rememberSaveable { mutableStateOf(false) }
    var waitingForPermission by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        allowed = Settings.canDrawOverlays(context)
        if (waitingForPermission && allowed) {
            waitingForPermission = false
            viewModel.setBubble(true)
            // It may already have been on, waiting for the permission.
            FloatingBubbleService.startIfAllowed(context)
        }
        onPauseOrDispose { }
    }

    if (showWarning) {
        AlertDialog(
            onDismissRequest = { showWarning = false },
            title = { Text(stringResource(R.string.bubble_warning_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    Text(stringResource(R.string.bubble_warning_body))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWarning = false
                        waitingForPermission = true
                        context.openOverlaySettings()
                    },
                ) { Text(stringResource(R.string.bubble_warning_allow)) }
            },
            dismissButton = { TextButton(onClick = { showWarning = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    return BubbleSwitch(
        on = on,
        allowed = allowed,
        turnOn = {
            if (allowed) {
                viewModel.setBubble(true)
                FloatingBubbleService.startIfAllowed(context)
            } else {
                showWarning = true
            }
        },
        turnOff = { viewModel.setBubble(false) },
    )
}

private fun Context.openOverlaySettings() {
    val forPicksmart = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(forPicksmart)
    } catch (_: ActivityNotFoundException) {
        // Some phones have only the list of all apps.
        runCatching { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
