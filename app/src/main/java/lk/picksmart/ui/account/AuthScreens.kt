package lk.picksmart.ui.account

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import lk.picksmart.R
import lk.picksmart.ui.components.PicksmartCard
import lk.picksmart.ui.components.PrimaryButton

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

    AuthPage(stringResource(R.string.auth_sign_in_title), onSkip) {
        Intro(R.string.auth_sign_in_body)
        AuthTextField(
            form.email,
            { viewModel.update(form.copy(email = it)) },
            stringResource(R.string.auth_email),
            AuthInput.EMAIL,
            error = errors[AuthField.EMAIL]?.let { stringResource(it.message) },
        )
        AuthTextField(
            form.password,
            { viewModel.update(form.copy(password = it)) },
            stringResource(R.string.auth_password),
            AuthInput.PASSWORD,
            error = errors[AuthField.PASSWORD]?.let { stringResource(it.message) },
            imeAction = ImeAction.Done,
            onDone = viewModel::submit,
        )
        AuthLink(stringResource(R.string.auth_forgot_password), { onForgotPassword(form.email.trim()) }, enabled = !status.busy)
        FormMessage(status.message)
        PrimaryButton(
            stringResource(if (status.busy) R.string.auth_signing_in else R.string.auth_sign_in_action),
            viewModel::submit,
            Modifier.fillMaxWidth(),
            enabled = !status.busy,
        )
        OrDivider()
        GoogleButton(viewModel, enabled = !status.busy)
        AuthLink(stringResource(R.string.auth_to_register), onRegister, enabled = !status.busy)
        AuthLink(stringResource(R.string.auth_skip), onSkip)
        InternetNote()
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

    AuthPage(stringResource(R.string.auth_register_title), onSkip) {
        Intro(R.string.auth_register_body)
        AuthTextField(
            form.name,
            { viewModel.update(form.copy(name = it)) },
            stringResource(R.string.auth_name),
            AuthInput.NAME,
            error = errors[AuthField.NAME]?.let { stringResource(it.message) },
        )
        AuthTextField(
            form.email,
            { viewModel.update(form.copy(email = it)) },
            stringResource(R.string.auth_email),
            AuthInput.EMAIL,
            error = errors[AuthField.EMAIL]?.let { stringResource(it.message) },
        )
        AuthTextField(
            form.mobile,
            { viewModel.update(form.copy(mobile = it)) },
            stringResource(R.string.auth_mobile_optional),
            AuthInput.MOBILE,
            error = errors[AuthField.MOBILE]?.let { stringResource(it.message) },
            supportingText = stringResource(R.string.auth_mobile_hint),
        )
        AuthTextField(
            form.password,
            { viewModel.update(form.copy(password = it)) },
            stringResource(R.string.auth_password),
            AuthInput.NEW_PASSWORD,
            error = errors[AuthField.PASSWORD]?.let { stringResource(it.message, MIN_PASSWORD_LENGTH) },
            supportingText = stringResource(R.string.auth_password_hint, MIN_PASSWORD_LENGTH),
        )
        AuthTextField(
            form.confirmPassword,
            { viewModel.update(form.copy(confirmPassword = it)) },
            stringResource(R.string.auth_confirm_password),
            AuthInput.NEW_PASSWORD,
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
        AuthLink(stringResource(R.string.auth_to_sign_in), onSignIn, enabled = !status.busy)
        AuthLink(stringResource(R.string.auth_skip), onSkip)
        InternetNote()
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
