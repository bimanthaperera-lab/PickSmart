package lk.picksmart.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import lk.picksmart.PicksmartApplication
import lk.picksmart.data.account.AccountProvider
import lk.picksmart.domain.model.VehicleInput
import lk.picksmart.domain.usecase.CompleteOnboarding
import lk.picksmart.ui.vehicle.FieldError
import lk.picksmart.ui.vehicle.VehicleField
import lk.picksmart.ui.vehicle.VehicleFormResult
import lk.picksmart.ui.vehicle.VehicleFormState
import lk.picksmart.ui.vehicle.validate

enum class OnboardingStep { WELCOME, INTRO, VEHICLE, SAFETY }

/** How many "how it works" pages the intro has. */
const val INTRO_PAGE_COUNT = 3

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    /** The intro page on screen, 0-based; kept so back from the vehicle step returns to it. */
    val introPage: Int = 0,
    val form: VehicleFormState = VehicleFormState(),
    val errors: Map<VehicleField, FieldError> = emptyMap(),
    /** The vehicle to save when the rider accepts the safety notice. */
    val pending: VehicleInput? = null,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
)

/**
 * First-launch setup: welcome, the "how it works" intro, the rider's own vehicle numbers, then the
 * safety notice. A rider who signs in from the welcome screen goes straight to the vehicle. Nothing
 * is saved until the rider accepts the notice; finishing flips the onboarding flag, which takes
 * the app to Home.
 */
class OnboardingViewModel(
    private val completeOnboarding: CompleteOnboarding,
    account: AccountProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    /** Whether the welcome screen offers "I already have an account": sign-in exists and nobody is signed in. */
    val canSignIn: StateFlow<Boolean> =
        (if (account.isAvailable) account.profile.map { it == null } else flowOf(false))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun start() = _state.update { it.copy(step = OnboardingStep.INTRO, introPage = 0) }

    fun showIntroPage(page: Int) = _state.update { it.copy(introPage = page.coerceIn(0, INTRO_PAGE_COUNT - 1)) }

    /** Last intro page done, or "Skip": on to the vehicle. */
    fun finishIntro() = _state.update { it.copy(step = OnboardingStep.VEHICLE) }

    /** Signed in or registered from the welcome screen: the intro is skipped. */
    fun signedIn() = _state.update { it.copy(step = OnboardingStep.VEHICLE) }

    fun updateForm(form: VehicleFormState) = _state.update {
        // Clear an error as soon as the rider edits; it's re-checked on Continue.
        it.copy(form = form, errors = it.errors.filterKeys { field -> fieldValue(it.form, field) == fieldValue(form, field) })
    }

    /** Validates the form; moves on only when it's complete. [defaultName] fills a blank name. */
    fun continueWithForm(defaultName: String) {
        when (val result = _state.value.form.validate(defaultName)) {
            is VehicleFormResult.Invalid -> _state.update { it.copy(errors = result.errors) }
            is VehicleFormResult.Valid -> _state.update {
                it.copy(errors = emptyMap(), pending = result.input, step = OnboardingStep.SAFETY)
            }
        }
    }

    /**
     * Back one step. The screen only routes system Back here after the welcome step, and handles
     * Back between intro pages itself.
     */
    fun back() {
        val current = _state.value
        if (current.saving) return
        _state.update {
            when (current.step) {
                OnboardingStep.WELCOME -> it
                OnboardingStep.INTRO -> it.copy(step = OnboardingStep.WELCOME)
                OnboardingStep.VEHICLE -> it.copy(step = OnboardingStep.INTRO, introPage = INTRO_PAGE_COUNT - 1)
                OnboardingStep.SAFETY -> it.copy(step = OnboardingStep.VEHICLE, saveFailed = false)
            }
        }
    }

    fun finish() {
        val current = _state.value
        val input = current.pending ?: return
        if (current.saving) return
        _state.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            try {
                completeOnboarding(input, isSample = false)
                // On success the onboarding flag flips and the app moves to Home.
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, saveFailed = true) }
            }
        }
    }

    private fun fieldValue(form: VehicleFormState, field: VehicleField): String = when (field) {
        VehicleField.FUEL_PRICE -> form.fuelPrice
        VehicleField.EFFICIENCY -> form.efficiency
        VehicleField.MAINTENANCE -> form.maintenance
        VehicleField.TARGET -> form.target
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as PicksmartApplication
                OnboardingViewModel(app.container.completeOnboarding, app.container.account)
            }
        }
    }
}
