package lk.picksmart.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import lk.picksmart.R
import lk.picksmart.ui.components.BrandMark
import lk.picksmart.ui.components.PicksmartCard
import lk.picksmart.ui.components.PrimaryButton
import lk.picksmart.ui.theme.Spacing

/**
 * Sign in with email and password or Google (CLAUDE.md section 14). [onSkip] and the back arrow
 * return to wherever the rider came from; [onSignedIn] runs once they are in.
 */
@Composable
fun LoginScreen(
    onSkip: () -> Unit,
    onSignedIn: () -> Unit,
    onRegister: () -> Unit,
    onForgotPassword: (email: String) -> Unit,
    viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val showErrors by viewModel.showErrors.collectAsStateWithLifecycle()
    LaunchedEffect(status.done) { if (status.done) onSignedIn() }
    val errors = if (showErrors) form.errors() else emptyMap()

    AuthFormPage(
        onBack = onSkip,
        switchLabel = stringResource(R.string.auth_to_register),
        onSwitch = onRegister,
        switchEnabled = !status.busy,
        footer = {
            SkipLink(onSkip)
            AuthNote(R.string.auth_needs_internet)
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = Spacing.l, bottom = Spacing.s),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            BrandMark(size = 64.dp)
            Title(R.string.auth_sign_in_title, TextAlign.Center)
        }
        AuthTextField(
            form.email,
            { viewModel.update(form.copy(email = it)) },
            stringResource(R.string.auth_email),
            AuthInput.EMAIL,
            placeholder = stringResource(R.string.auth_email_placeholder),
            error = errors[AuthField.EMAIL]?.let { stringResource(it.message) },
        )
        // "Forgot password?" sits close under its field, on the right.
        Column(horizontalAlignment = Alignment.End) {
            AuthTextField(
                form.password,
                { viewModel.update(form.copy(password = it)) },
                stringResource(R.string.auth_password),
                AuthInput.PASSWORD,
                placeholder = stringResource(R.string.auth_password_placeholder),
                error = errors[AuthField.PASSWORD]?.let { stringResource(it.message) },
                imeAction = ImeAction.Done,
                onDone = viewModel::submit,
            )
            AuthLink(stringResource(R.string.auth_forgot_password), { onForgotPassword(form.email.trim()) }, enabled = !status.busy)
        }
        FormMessage(status.message)
        PrimaryButton(
            stringResource(if (status.busy) R.string.auth_signing_in else R.string.auth_sign_in_action),
            viewModel::submit,
            Modifier.fillMaxWidth(),
            enabled = !status.busy,
        )
        OrDivider()
        GoogleButton(viewModel, enabled = !status.busy)
    }
}

/** Create an account with email and password, or with Google. */
@Composable
fun RegisterScreen(
    onSkip: () -> Unit,
    onRegistered: () -> Unit,
    onSignIn: () -> Unit,
    viewModel: RegisterViewModel = viewModel(factory = RegisterViewModel.Factory),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val showErrors by viewModel.showErrors.collectAsStateWithLifecycle()
    LaunchedEffect(status.done) { if (status.done) onRegistered() }
    val errors = if (showErrors) form.errors() else emptyMap()

    AuthFormPage(
        onBack = onSkip,
        switchLabel = stringResource(R.string.auth_to_sign_in),
        onSwitch = onSignIn,
        switchEnabled = !status.busy,
        footer = {
            SkipLink(onSkip)
            AuthNote(R.string.auth_register_later)
            AuthNote(R.string.auth_needs_internet)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Title(R.string.auth_register_title, TextAlign.Start)
            Intro(R.string.auth_register_body)
        }
        AuthTextField(
            form.name,
            { viewModel.update(form.copy(name = it)) },
            stringResource(R.string.auth_name),
            AuthInput.NAME,
            placeholder = stringResource(R.string.auth_name_placeholder),
            error = errors[AuthField.NAME]?.let { stringResource(it.message) },
        )
        AuthTextField(
            form.email,
            { viewModel.update(form.copy(email = it)) },
            stringResource(R.string.auth_email),
            AuthInput.EMAIL,
            placeholder = stringResource(R.string.auth_email_placeholder),
            error = errors[AuthField.EMAIL]?.let { stringResource(it.message) },
        )
        AuthTextField(
            form.mobile,
            { viewModel.update(form.copy(mobile = it)) },
            stringResource(R.string.auth_mobile_optional),
            AuthInput.MOBILE,
            placeholder = stringResource(R.string.auth_mobile_placeholder),
            error = errors[AuthField.MOBILE]?.let { stringResource(it.message) },
            supportingText = stringResource(R.string.auth_mobile_hint),
        )
        AuthTextField(
            form.password,
            { viewModel.update(form.copy(password = it)) },
            stringResource(R.string.auth_password),
            AuthInput.NEW_PASSWORD,
            placeholder = stringResource(R.string.auth_password_hint, MIN_PASSWORD_LENGTH),
            error = errors[AuthField.PASSWORD]?.let { stringResource(it.message, MIN_PASSWORD_LENGTH) },
        )
        AuthTextField(
            form.confirmPassword,
            { viewModel.update(form.copy(confirmPassword = it)) },
            stringResource(R.string.auth_confirm_password),
            AuthInput.NEW_PASSWORD,
            placeholder = stringResource(R.string.auth_confirm_password_placeholder),
            error = errors[AuthField.CONFIRM_PASSWORD]?.let { stringResource(it.message) },
            imeAction = ImeAction.Done,
            onDone = viewModel::submit,
        )
        FormMessage(status.message)
        PrimaryButton(
            stringResource(if (status.busy) R.string.auth_creating_account else R.string.auth_register_action),
            viewModel::submit,
            Modifier.fillMaxWidth(),
            enabled = !status.busy,
        )
        OrDivider()
        GoogleButton(viewModel, enabled = !status.busy)
    }
}

/** Sends a password reset link. Says the same thing whether or not the email has an account. */
@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    viewModel: ForgotPasswordViewModel = viewModel(factory = ForgotPasswordViewModel.Factory),
) {
    val email by viewModel.email.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val showErrors by viewModel.showErrors.collectAsStateWithLifecycle()

    AuthPage(stringResource(R.string.auth_reset_title), onBack) {
        if (status.done) {
            PicksmartCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.auth_reset_sent_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.auth_reset_sent_body, email.trim()),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PrimaryButton(stringResource(R.string.auth_back_to_sign_in), onBack, Modifier.fillMaxWidth())
            return@AuthPage
        }
        Intro(R.string.auth_reset_body)
        AuthTextField(
            email,
            viewModel::update,
            stringResource(R.string.auth_email),
            AuthInput.EMAIL,
            placeholder = stringResource(R.string.auth_email_placeholder),
            error = if (showErrors) emailError(email)?.let { stringResource(it.message) } else null,
            imeAction = ImeAction.Done,
            onDone = viewModel::submit,
        )
        FormMessage(status.message)
        PrimaryButton(
            stringResource(if (status.busy) R.string.auth_sending else R.string.auth_reset_action),
            viewModel::submit,
            Modifier.fillMaxWidth(),
            enabled = !status.busy,
        )
        InternetNote()
    }
}

@Composable
private fun Intro(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Title(text: Int, align: TextAlign) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.headlineLarge,
        textAlign = align,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { heading() },
    )
}

@Composable
private fun SkipLink(onSkip: () -> Unit) {
    AuthLink(stringResource(R.string.auth_skip), onSkip, color = MaterialTheme.colorScheme.onSurface)
}
