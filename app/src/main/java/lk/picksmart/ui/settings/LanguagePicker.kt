package lk.picksmart.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import lk.picksmart.R
import lk.picksmart.data.preferences.AppLanguageStore
import lk.picksmart.domain.model.AppLanguage
import lk.picksmart.ui.components.ChoiceChips
import lk.picksmart.ui.theme.Spacing

/**
 * A language's name. English, Sinhala and Tamil are always written in their own script, so a rider
 * can find theirs whatever the app is showing; "Phone's language" is translated.
 */
@Composable
fun AppLanguage.displayName(): String = stringResource(
    when (this) {
        AppLanguage.SYSTEM -> R.string.language_system
        AppLanguage.ENGLISH -> R.string.language_name_en
        AppLanguage.SINHALA -> R.string.language_name_si
        AppLanguage.TAMIL -> R.string.language_name_ta
    },
)

/**
 * The language the rider chose, read again when the app's language changes. On Android 13+ the
 * screen isn't recreated for that (MainActivity handles the change), so it must follow the
 * configuration rather than be read once.
 */
@Composable
fun rememberChosenLanguage(): AppLanguage {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(configuration) { AppLanguageStore.current(context) }
}

/** English, සිංහල, தமிழ் as chips, for the welcome screen. Choosing one redraws the app in it. */
@Composable
fun LanguageChips(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    ChoiceChips(
        options = listOf(AppLanguage.ENGLISH, AppLanguage.SINHALA, AppLanguage.TAMIL),
        selected = remember(configuration) { AppLanguageStore.showing(context) },
        optionLabel = { it.displayName() },
        onSelect = { language -> context.findActivity()?.let { AppLanguageStore.set(it, language) } },
        modifier = modifier,
    )
}

/** Every language, the phone's one first, as radio buttons, for the Account tab. */
@Composable
fun LanguageDialog(selected: AppLanguage, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language)) },
        text = {
            Column(Modifier.selectableGroup()) {
                AppLanguage.entries.forEach { language ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = Spacing.touchTarget)
                            .selectable(
                                selected = language == selected,
                                role = Role.RadioButton,
                                onClick = {
                                    onDismiss()
                                    if (language != selected) context.findActivity()?.let { AppLanguageStore.set(it, language) }
                                },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // The whole row is the control, so the radio itself takes no clicks.
                        RadioButton(selected = language == selected, onClick = null)
                        Spacer(Modifier.width(Spacing.m))
                        Text(language.displayName(), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
