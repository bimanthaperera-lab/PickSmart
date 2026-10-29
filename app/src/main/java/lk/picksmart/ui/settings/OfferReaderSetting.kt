package lk.picksmart.ui.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lk.picksmart.ui.theme.PicksmartTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import lk.picksmart.PicksmartApplication
import lk.picksmart.R
import lk.picksmart.data.preferences.UserPreferencesRepository
import lk.picksmart.domain.usecase.OfferChime
import lk.picksmart.glance.OfferChimes
import lk.picksmart.glance.OfferReaderService
import lk.picksmart.ui.components.ListGroup
import lk.picksmart.ui.components.ListRow
import lk.picksmart.ui.components.SectionLabel
import lk.picksmart.ui.theme.Spacing

/**
 * The offer reader's state (CLAUDE.md section 9b). [OFF]: not turned on in Android's Accessibility
 * settings, which only the rider can do there. [ON]: reading. [PAUSED]: on in Android, but paused
 * in Picksmart, so it reads nothing.
 */
enum class OfferReaderStatus { OFF, ON, PAUSED }

/** The pause flag, the sound switch and the floating bubble, kept in DataStore so the services see them too. */
class OfferReaderViewModel(private val preferences: UserPreferencesRepository) : ViewModel() {

    val paused: StateFlow<Boolean> = preferences.offerReaderPaused
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val sound: StateFlow<Boolean> = preferences.offerSound
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val bubble: StateFlow<Boolean> = preferences.floatingBubble
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setPaused(paused: Boolean) {
        viewModelScope.launch { preferences.setOfferReaderPaused(paused) }
    }

    fun setSound(on: Boolean) {
        viewModelScope.launch { preferences.setOfferSound(on) }
    }

    fun setBubble(on: Boolean) {
        viewModelScope.launch { preferences.setFloatingBubble(on) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { OfferReaderViewModel((this[APPLICATION_KEY] as PicksmartApplication).container.userPreferences) }
        }
    }
}

/**
 * Whether the reader is on, read again whenever the screen comes back (from Android's settings),
 * plus a way to read it again at once after turning it off from here.
 */
@Composable
internal fun rememberReaderEnabled(): Pair<Boolean, () -> Unit> {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(OfferReaderService.isEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        enabled = OfferReaderService.isEnabled(context)
        onPauseOrDispose { }
    }
    return enabled to { enabled = OfferReaderService.isEnabled(context) }
}

/**
 * What accepting the disclosure does: clears a leftover pause, asks for notifications (Android
 * 13+, for "Save this trip?"; the reader works without them), then opens Android's Accessibility
 * settings, where only the rider can turn the reader on.
 */
@Composable
internal fun rememberTurnOn(viewModel: OfferReaderViewModel = viewModel(factory = OfferReaderViewModel.Factory)): () -> Unit {
    val context = LocalContext.current
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        context.openAccessibilitySettings()
    }
    return {
        viewModel.setPaused(false)
        val needsAsking = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsAsking) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) else context.openAccessibilitySettings()
    }
}

/**
 * "Read offers" as a small status card with a switch, for the top of Home: a shield and the state
 * (Active in green, Paused in amber, Off) above the label, so it never relies on colour alone.
 * While the reader is on in Android, the switch pauses and resumes it at once. The first time, it
 * shows the disclosure, then opens Android's Accessibility settings, where only the rider can turn
 * it on. With [fillWidth] it takes the full width, the switch at the far end.
 */
@Composable
fun OfferReaderSwitch(
    modifier: Modifier = Modifier,
    fillWidth: Boolean = false,
    viewModel: OfferReaderViewModel = viewModel(factory = OfferReaderViewModel.Factory),
) {
    val (enabled, _) = rememberReaderEnabled()
    val paused by viewModel.paused.collectAsStateWithLifecycle()
    val turnOn = rememberTurnOn(viewModel)
    var showDisclosure by rememberSaveable { mutableStateOf(false) }
    val on = enabled && !paused
    val status = PicksmartTheme.status
    val (stateLabel, stateColor) = when {
        on -> stringResource(R.string.offer_reader_state_on) to status.profit
        enabled -> stringResource(R.string.offer_reader_state_paused) to status.belowTarget
        else -> stringResource(R.string.offer_reader_state_off) to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val locale = LocalConfiguration.current.locales[0]

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, if (on) stateColor.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant),
        modifier = if (fillWidth) modifier.fillMaxWidth() else modifier,
    ) {
        Row(
            Modifier
                .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
                .heightIn(min = Spacing.touchTarget)
                .toggleable(
                    value = on,
                    role = Role.Switch,
                    onValueChange = { turnOn ->
                        when {
                            !enabled -> showDisclosure = true
                            else -> viewModel.setPaused(!turnOn)
                        }
                    },
                )
                .padding(start = Spacing.s, end = Spacing.m, top = Spacing.xs, bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .background(stateColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                // Decorative: the state is read out from the text.
                Icon(painterResource(R.drawable.ic_shield), contentDescription = null, tint = stateColor, modifier = Modifier.size(18.dp))
            }
            Column(if (fillWidth) Modifier.weight(1f) else Modifier) {
                Text(
                    stateLabel.uppercase(locale),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
                    color = stateColor,
                )
                Text(
                    stringResource(R.string.offer_reader_switch_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            // The whole card is the control, so the switch itself takes no clicks.
            Switch(checked = on, onCheckedChange = null)
        }
    }

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

/**
 * "Read ride offers" on the Account tab: On, Paused or Off. Tapping it shows the disclosure when
 * it is off, and otherwise offers Pause or Resume, and Turn off completely (off in Android too).
 * Below it, "Sound on profit or loss" turns the reader's chimes on, playing the profit one as a
 * sample, and "Floating bubble" turns the bubble on ([FloatingBubbleRow]).
 */
@Composable
fun OfferReaderSetting(viewModel: OfferReaderViewModel = viewModel(factory = OfferReaderViewModel.Factory)) {
    val context = LocalContext.current
    val (enabled, recheck) = rememberReaderEnabled()
    val paused by viewModel.paused.collectAsStateWithLifecycle()
    val sound by viewModel.sound.collectAsStateWithLifecycle()
    val turnOn = rememberTurnOn(viewModel)
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val status = when {
        !enabled -> OfferReaderStatus.OFF
        paused -> OfferReaderStatus.PAUSED
        else -> OfferReaderStatus.ON
    }

    SectionLabel(stringResource(R.string.settings_section_glance))
    ListGroup(
        rows = listOf(
            {
                ListRow(
                    title = stringResource(R.string.offer_reader_title),
                    subtitle = stringResource(
                        when (status) {
                            OfferReaderStatus.OFF -> R.string.offer_reader_off
                            OfferReaderStatus.ON -> R.string.offer_reader_on
                            OfferReaderStatus.PAUSED -> R.string.offer_reader_paused
                        },
                    ),
                    onClick = { showDialog = true },
                )
            },
            {
                SwitchListRow(
                    title = stringResource(R.string.offer_sound_title),
                    subtitle = stringResource(R.string.offer_sound_body),
                    checked = sound,
                    onCheckedChange = { on ->
                        viewModel.setSound(on)
                        // A sample, so the rider knows what to listen for.
                        if (on) OfferChimes.play(context, OfferChime.PROFIT)
                    },
                )
            },
            { FloatingBubbleRow(viewModel) },
        ),
    )

    if (!showDialog) return
    when (status) {
        OfferReaderStatus.OFF -> DisclosureDialog(
            onAccept = {
                showDialog = false
                turnOn()
            },
            onDismiss = { showDialog = false },
        )
        OfferReaderStatus.ON, OfferReaderStatus.PAUSED -> ManageDialog(
            paused = status == OfferReaderStatus.PAUSED,
            onPauseOrResume = {
                showDialog = false
                viewModel.setPaused(status == OfferReaderStatus.ON)
            },
            onTurnOff = {
                showDialog = false
                // Turns it off in Android too. If the service isn't running, let the rider do it.
                if (OfferReaderService.turnOff()) recheck() else context.openAccessibilitySettings()
            },
            onDismiss = { showDialog = false },
        )
    }
}

/** A [ListRow]-sized row with a switch at the end. The whole row is the control. */
@Composable
internal fun SwitchListRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .defaultMinSize(minHeight = 64.dp)
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(Spacing.m))
        // The row handles the toggle, so the switch itself takes no clicks.
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** The prominent disclosure (Play's AccessibilityService policy), shown before Android's settings. */
@Composable
internal fun DisclosureDialog(onAccept: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.offer_reader_disclosure_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Text(stringResource(R.string.offer_reader_disclosure_body))
                Text(
                    stringResource(R.string.offer_reader_settings_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onAccept) { Text(stringResource(R.string.offer_reader_disclosure_accept)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ManageDialog(paused: Boolean, onPauseOrResume: () -> Unit, onTurnOff: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.offer_reader_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
                Text(stringResource(if (paused) R.string.offer_reader_manage_paused_body else R.string.offer_reader_manage_on_body))
                OutlinedButton(
                    onClick = onTurnOff,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.offer_reader_turn_off_completely))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onPauseOrResume) {
                Text(stringResource(if (paused) R.string.offer_reader_resume else R.string.offer_reader_pause))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private fun Context.openAccessibilitySettings() {
    try {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // Every phone has Accessibility settings; nothing else to offer if one somehow doesn't.
    }
}
