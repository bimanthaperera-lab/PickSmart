package lk.picksmart.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import lk.picksmart.PicksmartApplication
import lk.picksmart.domain.model.VehicleInput
import lk.picksmart.domain.usecase.CompleteOnboarding
import lk.picksmart.domain.usecase.sampleVehicleInput
import lk.picksmart.ui.vehicle.FieldError
import lk.picksmart.ui.vehicle.VehicleField
import lk.picksmart.ui.vehicle.VehicleFormResult
import lk.picksmart.ui.vehicle.VehicleFormState
import lk.picksmart.ui.vehicle.validate

enum class OnboardingStep { WELCOME, VEHICLE, SAFETY }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val form: VehicleFormState = VehicleFormState(),
    val errors: Map<VehicleField, FieldError> = emptyMap(),
    /** The vehicle to save when the rider accepts the safety notice. */
    val pending: VehicleInput? = null,
    val usingSample: Boolean = false,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
)

/**
 * First-launch setup: welcome, vehicle (or sample values), then the safety notice. Nothing is
 * saved until the rider accepts the notice; finishing flips the onboarding flag, which takes
 * the app to Home.
 */
class OnboardingViewModel(private val completeOnboarding: CompleteOnboarding) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun start() = _state.update { it.copy(step = OnboardingStep.VEHICLE) }

    fun updateForm(form: VehicleFormState) = _state.update {
        // Clear an error as soon as the rider edits; it's re-checked on Continue.
        it.copy(form = form, errors = it.errors.filterKeys { field -> fieldValue(it.form, field) == fieldValue(form, field) })
    }

    /** Validates the form; moves on only when it's complete. [defaultName] fills a blank name. */
    fun continueWithForm(defaultName: String) {
        when (val result = _state.value.form.validate(defaultName)) {
            is VehicleFormResult.Invalid -> _state.update { it.copy(errors = result.errors) }
            is VehicleFormResult.Valid -> _state.update {
                it.copy(errors = emptyMap(), pending = result.input, usingSample = false, step = OnboardingStep.SAFETY)
            }
        }
    }

    /** Skips the form with clearly labelled sample values. */
    fun useSampleValues(sampleName: String) = _state.update {
        it.copy(pending = sampleVehicleInput(sampleName), usingSample = true, step = OnboardingStep.SAFETY)
    }

    /** Back one step. The screen only routes system Back here after the welcome step. */
    fun back() {
        val current = _state.value
        if (current.step == OnboardingStep.WELCOME || current.saving) return
        _state.update { it.copy(step = OnboardingStep.entries[current.step.ordinal - 1], saveFailed = false) }
    }

    fun finish() {
        val current = _state.value
        val input = current.pending ?: return
        if (current.saving) return
        _state.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            try {
                completeOnboarding(input, isSample = current.usingSample)
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
                OnboardingViewModel(app.container.completeOnboarding)
            }
        }
    }
}
