package lk.picksmart.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import lk.picksmart.R
import lk.picksmart.ui.account.ForgotPasswordScreen
import lk.picksmart.ui.account.LoginScreen
import lk.picksmart.ui.account.RegisterScreen
import lk.picksmart.ui.components.BrandMark
import lk.picksmart.ui.components.ErrorState
import lk.picksmart.ui.components.LoadingState
import lk.picksmart.ui.components.PrimaryButton
import lk.picksmart.ui.components.ScreenTitle
import lk.picksmart.ui.navigation.ForgotPasswordRoute
import lk.picksmart.ui.navigation.LoginRoute
import lk.picksmart.ui.navigation.RegisterRoute
import lk.picksmart.ui.navigation.popIfOn
import lk.picksmart.ui.splash.WelcomeScreen
import lk.picksmart.ui.theme.Spacing
import lk.picksmart.ui.vehicle.VehicleFormFields
import lk.picksmart.ui.vehicle.VehicleFormState
import lk.picksmart.ui.vehicle.label

/** The setup steps (welcome, intro, vehicle, safety); the sign-in screens open on top of it. */
@Serializable private data object SetupRoute

/**
 * First-launch setup. "I already have an account" opens the same Login and Register screens as
 * the Account tab; signing in there carries on to the vehicle step.
 */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModel.Factory)) {
    val navController = rememberNavController()
    // There is no Surface above setup, so give plain text the theme's colour here.
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        SetupNavHost(navController, viewModel)
    }
}

@Composable
private fun SetupNavHost(navController: NavHostController, viewModel: OnboardingViewModel) {
    NavHost(navController, startDestination = SetupRoute) {
        composable<SetupRoute> {
            SetupFlow(viewModel, onSignIn = { navController.navigate(LoginRoute) })
        }
        // Login and Register swap places rather than stack, so back always returns to the welcome.
        composable<LoginRoute> {
            AuthPane {
                LoginScreen(
                    onSkip = { navController.popIfOn(LoginRoute::class) },
                    onSignedIn = {
                        navController.popIfOn(LoginRoute::class)
                        viewModel.signedIn()
                    },
                    onRegister = { navController.navigate(RegisterRoute) { popUpTo<LoginRoute> { inclusive = true } } },
                    onForgotPassword = { email -> navController.navigate(ForgotPasswordRoute(email)) },
                )
            }
        }
        composable<RegisterRoute> {
            AuthPane {
                RegisterScreen(
                    onSkip = { navController.popIfOn(RegisterRoute::class) },
                    onRegistered = {
                        navController.popIfOn(RegisterRoute::class)
                        viewModel.signedIn()
                    },
                    onSignIn = { navController.navigate(LoginRoute) { popUpTo<RegisterRoute> { inclusive = true } } },
                )
            }
        }
        composable<ForgotPasswordRoute> {
            AuthPane { ForgotPasswordScreen(onBack = { navController.popIfOn(ForgotPasswordRoute::class) }) }
        }
    }
}

/** The account screens expect the app's scaffold; here they get its background and insets instead. */
@Composable
private fun AuthPane(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        content()
    }
}

@Composable
private fun SetupFlow(viewModel: OnboardingViewModel, onSignIn: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val canSignIn by viewModel.canSignIn.collectAsStateWithLifecycle()
    // Back steps through setup; on the welcome screen it leaves the app as usual.
    BackHandler(enabled = state.step != OnboardingStep.WELCOME) { viewModel.back() }

    when (state.step) {
        OnboardingStep.WELCOME -> WelcomeStep(canSignIn, onStart = viewModel::start, onSignIn = onSignIn)
        OnboardingStep.INTRO -> IntroStep(
            initialPage = state.introPage,
            onPageShown = viewModel::showIntroPage,
            onDone = viewModel::finishIntro,
        )
        OnboardingStep.VEHICLE -> {
            val defaultName = stringResource(state.form.type.label)
            VehicleStep(
                state = state,
                onChange = viewModel::updateForm,
                onContinue = { viewModel.continueWithForm(defaultName) },
            )
        }
        OnboardingStep.SAFETY -> SafetyStep(state = state, onFinish = viewModel::finish)
    }
}

@Composable
private fun WelcomeStep(canSignIn: Boolean, onStart: () -> Unit, onSignIn: () -> Unit) {
    WelcomeScreen {
        PrimaryButton(stringResource(R.string.onboarding_get_started), onStart, Modifier.fillMaxWidth())
        if (canSignIn) {
            TextButton(
                onClick = onSignIn,
                modifier = Modifier.heightIn(min = Spacing.touchTarget),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            ) {
                Text(
                    stringResource(R.string.onboarding_have_account),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** One "how it works" page: what to do, and two short tips. */
private data class IntroPage(
    @DrawableRes val icon: Int,
    @StringRes val title: Int,
    @StringRes val body: Int,
    @StringRes val tips: List<Int>,
)

private val IntroPages = listOf(
    IntroPage(
        R.drawable.ic_nav_check_ride,
        R.string.intro_check_title,
        R.string.intro_check_body,
        listOf(R.string.intro_check_tip_costs, R.string.intro_check_tip_bands),
    ),
    IntroPage(
        R.drawable.ic_plus_circle,
        R.string.intro_record_title,
        R.string.intro_record_body,
        listOf(R.string.intro_record_tip_costs, R.string.intro_record_tip_income),
    ),
    IntroPage(
        R.drawable.ic_trend_up,
        R.string.intro_profit_title,
        R.string.intro_profit_body,
        listOf(R.string.intro_profit_tip_offline, R.string.intro_profit_tip_rates),
    ),
).also { check(it.size == INTRO_PAGE_COUNT) }

/**
 * How the app works, one swipeable page at a time, with Next and Skip. Back goes to the previous
 * page, and from the first one to the welcome screen.
 */
@Composable
private fun IntroStep(initialPage: Int, onPageShown: (Int) -> Unit, onDone: () -> Unit) {
    val pager = rememberPagerState(initialPage = initialPage) { IntroPages.size }
    val scope = rememberCoroutineScope()
    val lastPage = pager.currentPage == IntroPages.lastIndex
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect(onPageShown) }
    BackHandler(enabled = pager.currentPage > 0) {
        scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = Spacing.screen, end = Spacing.s, top = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandMark(size = 32.dp)
            Spacer(Modifier.weight(1f))
            // Skip leaves the intro, never the setup: the vehicle still needs the rider's numbers.
            if (!lastPage) {
                TextButton(
                    onClick = onDone,
                    modifier = Modifier.height(Spacing.touchTarget),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                ) {
                    Text(stringResource(R.string.intro_skip), style = MaterialTheme.typography.labelLarge)
                }
            } else {
                Spacer(Modifier.height(Spacing.touchTarget))
            }
        }
        HorizontalPager(
            state = pager,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page -> IntroPageContent(IntroPages[page]) }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen, vertical = Spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            PageDots(current = pager.currentPage, count = IntroPages.size)
            PrimaryButton(
                stringResource(if (lastPage) R.string.intro_done else R.string.intro_next),
                onClick = {
                    if (lastPage) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun IntroPageContent(page: IntroPage) {
    // Scrolls on its own, so a page still fits at large font sizes.
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen, vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.l, Alignment.CenterVertically),
    ) {
        Box(
            Modifier
                .size(96.dp)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            // Decorative: the title says what the page is about.
            Icon(
                painterResource(page.icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp),
            )
        }
        Text(
            stringResource(page.title),
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = Spacing.s)
                .semantics { heading() },
        )
        Text(
            stringResource(page.body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = Spacing.s)
                .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
                .padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            page.tips.forEach { CheckPoint(it) }
        }
    }
}

/** Where the rider is in the intro: the current page is a wider yellow bar. */
@Composable
private fun PageDots(current: Int, count: Int) {
    val description = stringResource(R.string.intro_page, current + 1, count)
    Row(
        Modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        repeat(count) { index ->
            Box(
                Modifier
                    .height(8.dp)
                    .width(if (index == current) 24.dp else 8.dp)
                    .background(
                        if (index == current) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        CircleShape,
                    ),
            )
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
    }
}

@Composable
private fun SafetyStep(state: OnboardingUiState, onFinish: () -> Unit) {
    StepScaffold(stepNumber = 2) {
        ScreenTitle(stringResource(R.string.onboarding_safety_title))
        CheckPoint(R.string.onboarding_safety_estimates)
        CheckPoint(R.string.onboarding_safety_stopped)
        CheckPoint(R.string.onboarding_safety_no_reading)
        CheckPoint(R.string.onboarding_safety_on_device)
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
private fun CheckPoint(@StringRes text: Int) {
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
