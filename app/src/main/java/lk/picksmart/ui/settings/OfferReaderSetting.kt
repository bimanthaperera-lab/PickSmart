package lk.picksmart.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import lk.picksmart.R
import lk.picksmart.glance.OfferReaderService
import lk.picksmart.ui.theme.Spacing
import lk.picksmart.ui.components.ListGroup
import lk.picksmart.ui.components.ListRow
import lk.picksmart.ui.components.SectionLabel

/**
 * "Read ride offers" (CLAUDE.md section 9b). Android only lets the rider turn an accessibility
 * service on or off in its own settings, so the row explains first (the prominent disclosure) and
 * then opens them. Its On / Off is read again whenever the screen comes back.
 */
@Composable
fun OfferReaderSetting() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(OfferReaderService.isEnabled(context)) }
    var showDialog by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        enabled = OfferReaderService.isEnabled(context)
        onPauseOrDispose { }
    }
    val openSettings = {
        showDialog = false
        try {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            // Every phone has Accessibility settings; nothing else to offer if one somehow doesn't.
        }
    }

    SectionLabel(stringResource(R.string.settings_section_glance))
    ListGroup(
        rows = listOf {
            ListRow(
                title = stringResource(R.string.offer_reader_title),
                subtitle = stringResource(if (enabled) R.string.offer_reader_on else R.string.offer_reader_off),
                onClick = { showDialog = true },
            )
        },
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(stringResource(if (enabled) R.string.offer_reader_turn_off_title else R.string.offer_reader_disclosure_title))
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    Text(stringResource(if (enabled) R.string.offer_reader_turn_off_body else R.string.offer_reader_disclosure_body))
                    if (!enabled) {
                        Text(
                            stringResource(R.string.offer_reader_settings_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = openSettings) {
                    Text(stringResource(if (enabled) R.string.offer_reader_open_settings else R.string.offer_reader_disclosure_accept))
                }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
