package lk.picksmart.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import lk.picksmart.R
import lk.picksmart.ui.components.BrandMark
import lk.picksmart.ui.components.ErrorState
import lk.picksmart.ui.components.LoadingState
import lk.picksmart.ui.components.NoticeCard
import lk.picksmart.ui.components.PrimaryButton
import lk.picksmart.ui.components.ScreenTitle
import lk.picksmart.ui.theme.PicksmartTheme
import lk.picksmart.ui.theme.Spacing
import lk.picksmart.ui.vehicle.VehicleFormFields
import lk.picksmart.ui.vehicle.VehicleFormState
import lk.picksmart.ui.vehicle.label

@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Back steps through setup; on the welcome screen it leaves the app as usual.
    BackHandler(enabled = state.step != OnboardingStep.WELCOME) { viewModel.back() }

    when (state.step) {
        OnboardingStep.WELCOME -> WelcomeStep(onStart = viewModel::start)
        OnboardingStep.VEHICLE -> {
            val defaultName = stringResource(state.form.type.label)
            // Shown as "Motorcycle · Sample" until the rider saves their own values.
            val sampleName = stringResource(R.string.vehicle_type_motorcycle)
            VehicleStep(
                state = state,
                onChange = viewModel::updateForm,
                onContinue = { viewModel.continueWithForm(defaultName) },
                onUseSample = { viewModel.useSampleValues(sampleName) },
            )
        }
        OnboardingStep.SAFETY -> SafetyStep(state = state, onFinish = viewModel::finish)
    }
}

@Composable
private fun WelcomeStep(onStart: () -> Unit) {
    LightSystemBarIcons()
    // The full logo only reads on a dark surface, so the welcome screen is always dark.
    PicksmartTheme(darkTheme = true) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding(),
        ) {
            // At least screen height so the logo centres and the button sits at the bottom, but
            // free to grow and scroll at large font sizes.
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(Spacing.screen),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Spacer(Modifier.height(Spacing.xl))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painterResource(R.drawable.picksmart_logo),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier
                            .widthIn(max = 240.dp)
                            .fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.tagline),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = Spacing.xl),
                    )
                }
                PrimaryButton(
                    stringResource(R.string.onboarding_get_started),
                    onStart,
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xl),
                )
            }
        }
    }
}

/**
 * Light status and navigation bar icons while this is on screen, for the always-dark welcome
 * step even when the app theme is light. Restores the theme's setting when it leaves.
 */
@Composable
private fun LightSystemBarIcons() {
    val activity = LocalActivity.current ?: return
    val view = LocalView.current
    val appIsLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    DisposableEffect(activity, appIsLight) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = appIsLight
            controller.isAppearanceLightNavigationBars = appIsLight
        }
    }
}

@Composable
private fun StepScaffold(
    stepNumber: Int,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen, vertical = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        StepHeader(stepNumber, totalSteps = 2)
        content()
    }
}

@Composable
private fun StepHeader(stepNumber: Int, totalSteps: Int) {
    val stepText = stringResource(R.string.onboarding_step, stepNumber, totalSteps)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stepText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            BrandMark(size = 32.dp)
        }
        // Progress bar; the step text above already says the same for screen readers.
        Row(
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = stepText },
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            repeat(totalSteps) { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .background(
                            if (index < stepNumber) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                            RoundedCornerShape(3.dp),
                        ),
                )
            }
        }
    }
}

@Composable
private fun VehicleStep(
    state: OnboardingUiState,
    onChange: (VehicleFormState) -> Unit,
    onContinue: () -> Unit,
    onUseSample: () -> Unit,
) {
    StepScaffold(stepNumber = 1) {
        ScreenTitle(stringResource(R.string.onboarding_vehicle_title))
        Text(
            stringResource(R.string.onboarding_vehicle_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        VehicleFormFields(state = state.form, errors = state.errors, onChange = onChange)
        PrimaryButton(stringResource(R.string.action_continue), onContinue, Modifier.fillMaxWidth())
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            TextButton(onClick = onUseSample, modifier = Modifier.height(Spacing.touchTarget)) {
                Text(
                    stringResource(R.string.onboarding_use_sample),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                stringResource(R.string.onboarding_sample_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SafetyStep(state: OnboardingUiState, onFinish: () -> Unit) {
    StepScaffold(stepNumber = 2) {
        ScreenTitle(stringResource(R.string.onboarding_safety_title))
        SafetyPoint(R.string.onboarding_safety_estimates)
        SafetyPoint(R.string.onboarding_safety_stopped)
        SafetyPoint(R.string.onboarding_safety_no_reading)
        SafetyPoint(R.string.onboarding_safety_on_device)
        if (state.usingSample) {
            NoticeCard(
                title = stringResource(R.string.sample_notice_title),
                body = stringResource(R.string.onboarding_sample_reminder),
            )
        }
        if (state.saveFailed) {
            ErrorState(stringResource(R.string.onboarding_save_failed))
        }
        if (state.saving) LoadingState()
        PrimaryButton(
            stringResource(if (state.saveFailed) R.string.action_retry else R.string.onboarding_safety_accept),
            onFinish,
            Modifier.fillMaxWidth(),
            enabled = !state.saving,
        )
    }
}

@Composable
private fun SafetyPoint(@StringRes text: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.Top) {
        Icon(
            painterResource(R.drawable.ic_band_good),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}
