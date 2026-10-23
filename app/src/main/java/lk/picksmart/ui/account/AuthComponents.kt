package lk.picksmart.ui.account

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch
import lk.picksmart.R
import lk.picksmart.ui.components.DetailHeader
import lk.picksmart.ui.components.SecondaryButton
import lk.picksmart.ui.components.fieldColors
import lk.picksmart.ui.theme.Spacing

/** A sign-in page: back arrow and title, then a scrolling form that stays above the keyboard. */
@Composable
fun AuthPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        DetailHeader(title, R.drawable.ic_arrow_back, stringResource(R.string.action_back), onBack)
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
            content = content,
        )
    }
}

enum class AuthInput(val keyboard: KeyboardType, val contentType: ContentType) {
    NAME(KeyboardType.Text, ContentType.PersonFullName),
    EMAIL(KeyboardType.Email, ContentType.EmailAddress),
    MOBILE(KeyboardType.Phone, ContentType.PhoneNumber),
    PASSWORD(KeyboardType.Password, ContentType.Password),
    NEW_PASSWORD(KeyboardType.Password, ContentType.NewPassword),
}

/**
 * A text field for the account forms, with the right keyboard and autofill hint for [input].
 * Password fields hide their text, with a Show / Hide button.
 */
@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    input: AuthInput,
    modifier: Modifier = Modifier,
    error: String? = null,
    supportingText: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
) {
    val isPassword = input == AuthInput.PASSWORD || input == AuthInput.NEW_PASSWORD
    var shown by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(MAX_LENGTH)) },
        label = { Text(label) },
        supportingText = (error ?: supportingText)?.let { { Text(it) } },
        isError = error != null,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        visualTransformation = if (isPassword && !shown) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (isPassword) {
            {
                TextButton(onClick = { shown = !shown }) {
                    Text(stringResource(if (shown) R.string.auth_hide_password else R.string.auth_show_password))
                }
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = input.keyboard,
            capitalization = if (input == AuthInput.NAME) KeyboardCapitalization.Words else KeyboardCapitalization.None,
            autoCorrectEnabled = input == AuthInput.NAME,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        shape = MaterialTheme.shapes.medium,
        colors = fieldColors(),
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentType = input.contentType
                if (error != null) error(error)
            },
    )
}

private const val MAX_LENGTH = 100

/** Why the last request failed, read out by screen readers when it appears. */
@Composable
fun FormMessage(@StringRes message: Int?, modifier: Modifier = Modifier) {
    if (message == null) return
    Text(
        stringResource(message),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** A thin line with "or" in the middle, between the email form and "Continue with Google". */
@Composable
fun OrDivider() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
        Text(stringResource(R.string.auth_or), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
    }
}

/** "Continue with Google": opens Google's account picker, then hands the result to [viewModel]. */
@Composable
fun GoogleButton(viewModel: AuthFormViewModel, enabled: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    SecondaryButton(
        stringResource(R.string.auth_continue_google),
        onClick = {
            if (viewModel.googleStarted()) scope.launch { viewModel.googleFinished(requestGoogleIdToken(context)) }
        },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A plain text link such as "Forgot password?" or "Skip — use without an account". */
@Composable
fun AuthLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

/** Sign-in needs internet; the rest of the app doesn't (CLAUDE.md N9). */
@Composable
fun InternetNote() {
    Text(
        stringResource(R.string.auth_needs_internet),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}
