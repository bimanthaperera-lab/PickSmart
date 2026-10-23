package lk.picksmart.ui.account

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.material3.TextFieldLabelScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import lk.picksmart.R
import lk.picksmart.ui.components.DetailHeader
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

/**
 * Login and Register, as in the mockups: a back arrow with a link to the other form on the right,
 * then the form, and [footer] (Skip and notes) at the bottom of the screen, or under the form when
 * the form is taller than the screen or the keyboard is open.
 */
@Composable
fun AuthFormPage(
    onBack: () -> Unit,
    switchLabel: String,
    onSwitch: () -> Unit,
    switchEnabled: Boolean,
    footer: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.s, end = Spacing.s, top = Spacing.s),
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
            }
            Spacer(Modifier.weight(1f))
            AuthLink(switchLabel, onSwitch, enabled = switchEnabled)
        }
        BoxWithConstraints(Modifier.weight(1f)) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(horizontal = Spacing.screen, vertical = Spacing.l),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.l), content = content)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = footer,
                )
            }
        }
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
 * A text field for the account forms, with its label above the box, the right keyboard and
 * autofill hint for [input], and [placeholder] inside until the rider types. Password fields hide
 * their text, with an eye button to show it.
 */
@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    input: AuthInput,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    supportingText: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
) {
    // The label-above layout needs the TextFieldState fields, while the forms keep plain Strings.
    // The field owns its text: it starts from [value] (for example a prefilled email) and every
    // change flows out through onValueChange. It never copies [value] back in, because while the
    // rider types fast [value] lags a few characters behind and would undo what they typed.
    val state = rememberTextFieldState(value)
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect { latestOnValueChange(it) }
    }

    val labelSlot: @Composable TextFieldLabelScope.() -> Unit = { Text(label, style = MaterialTheme.typography.titleSmall) }
    val placeholderSlot = placeholder?.let { @Composable { Text(it) } }
    val supportingSlot = (error ?: supportingText)?.let { @Composable { Text(it) } }
    val keyboardOptions = KeyboardOptions(
        keyboardType = input.keyboard,
        capitalization = if (input == AuthInput.NAME) KeyboardCapitalization.Words else KeyboardCapitalization.None,
        autoCorrectEnabled = input == AuthInput.NAME,
        imeAction = imeAction,
    )
    val onKeyboardAction = KeyboardActionHandler { moveOn -> if (imeAction == ImeAction.Done) onDone() else moveOn() }
    val fieldModifier = modifier
        .fillMaxWidth()
        .semantics {
            contentType = input.contentType
            if (error != null) error(error)
        }

    if (input == AuthInput.PASSWORD || input == AuthInput.NEW_PASSWORD) {
        var shown by rememberSaveable { mutableStateOf(false) }
        OutlinedSecureTextField(
            state = state,
            modifier = fieldModifier,
            textStyle = MaterialTheme.typography.bodyLarge,
            labelPosition = TextFieldLabelPosition.Above(),
            label = labelSlot,
            placeholder = placeholderSlot,
            trailingIcon = {
                IconButton(onClick = { shown = !shown }) {
                    Icon(
                        painterResource(if (shown) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                        contentDescription = stringResource(if (shown) R.string.auth_hide_password else R.string.auth_show_password),
                    )
                }
            },
            supportingText = supportingSlot,
            isError = error != null,
            inputTransformation = InputTransformation.maxLength(MAX_LENGTH),
            textObfuscationMode = if (shown) TextObfuscationMode.Visible else TextObfuscationMode.RevealLastTyped,
            keyboardOptions = keyboardOptions,
            onKeyboardAction = onKeyboardAction,
            shape = MaterialTheme.shapes.medium,
            colors = fieldColors(),
        )
    } else {
        OutlinedTextField(
            state = state,
            modifier = fieldModifier,
            textStyle = MaterialTheme.typography.bodyLarge,
            labelPosition = TextFieldLabelPosition.Above(),
            label = labelSlot,
            placeholder = placeholderSlot,
            supportingText = supportingSlot,
            isError = error != null,
            inputTransformation = InputTransformation.maxLength(MAX_LENGTH),
            keyboardOptions = keyboardOptions,
            onKeyboardAction = onKeyboardAction,
            lineLimits = TextFieldLineLimits.SingleLine,
            shape = MaterialTheme.shapes.medium,
            colors = fieldColors(),
        )
    }
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

/**
 * "Continue with Google" with Google's "G": opens Google's account picker, then hands the result
 * to [viewModel]. Phone sign-in from the mockups is left out on purpose (no phone OTP, CLAUDE.md
 * section 14), so this button takes the full width.
 */
@Composable
fun GoogleButton(viewModel: AuthFormViewModel, enabled: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    OutlinedButton(
        onClick = {
            if (viewModel.googleStarted()) scope.launch { viewModel.googleFinished(requestGoogleIdToken(context)) }
        },
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Spacing.buttonHeight),
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        // Decorative: the button text already says what it does. Not tinted, so it keeps Google's
        // colours; faded instead while the button is off.
        Image(
            painterResource(R.drawable.google_g),
            contentDescription = null,
            modifier = Modifier
                .size(22.dp)
                .alpha(if (enabled) 1f else DISABLED_ALPHA),
        )
        Spacer(Modifier.width(Spacing.m))
        Text(stringResource(R.string.auth_continue_google), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

private const val DISABLED_ALPHA = 0.38f

/**
 * A plain text link such as "Forgot password?" (brand colour) or "Skip — use without an account"
 * (pass [color] = onSurface).
 */
@Composable
fun AuthLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(contentColor = color),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

/** A small centred note under a form, e.g. that signing in needs internet (CLAUDE.md N9). */
@Composable
fun AuthNote(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
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
