package lk.picksmart.ui.vehicle

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import lk.picksmart.PicksmartApplication
import lk.picksmart.data.preferences.UserPreferencesRepository
import lk.picksmart.data.repository.VehicleRepository
import lk.picksmart.ui.navigation.EditVehicleRoute

sealed interface EditVehicleUiState {
    data object Loading : EditVehicleUiState

    /** The vehicle was removed while the screen was open, or the id is stale. */
    data object NotFound : EditVehicleUiState

    data class Editing(
        val form: VehicleFormState,
        val isNew: Boolean,
        val isSample: Boolean,
        val errors: Map<VehicleField, FieldError> = emptyMap(),
        val saving: Boolean = false,
        val saveFailed: Boolean = false,
        val saved: Boolean = false,
    ) : EditVehicleUiState
}

/** Edits a vehicle, or creates one when [EditVehicleRoute.vehicleId] is [EditVehicleRoute.NEW]. */
class EditVehicleViewModel(
    savedStateHandle: SavedStateHandle,
    private val vehicles: VehicleRepository,
    private val preferences: UserPreferencesRepository,
) : ViewModel() {

    private val vehicleId = savedStateHandle.toRoute<EditVehicleRoute>().vehicleId

    private val _state = MutableStateFlow<EditVehicleUiState>(EditVehicleUiState.Loading)
    val state: StateFlow<EditVehicleUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = if (vehicleId == EditVehicleRoute.NEW) {
                EditVehicleUiState.Editing(VehicleFormState(), isNew = true, isSample = false)
            } else {
                vehicles.get(vehicleId)
                    ?.let { EditVehicleUiState.Editing(it.toFormState(), isNew = false, isSample = it.isSample) }
                    ?: EditVehicleUiState.NotFound
            }
        }
    }

    fun updateForm(form: VehicleFormState) = editing { it.copy(form = form, errors = emptyMap(), saveFailed = false) }

    fun save(defaultName: String) {
        val current = _state.value as? EditVehicleUiState.Editing ?: return
        if (current.saving) return
        val input = when (val result = current.form.validate(defaultName)) {
            is VehicleFormResult.Invalid -> return editing { it.copy(errors = result.errors) }
            is VehicleFormResult.Valid -> result.input
        }
        editing { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            try {
                val ok = if (current.isNew) {
                    val id = vehicles.create(input)
                    // The first vehicle becomes the default, so Check a Ride has something to use.
                    if (preferences.defaultVehicleId.first() == null) preferences.setDefaultVehicleId(id)
                    true
                } else {
                    vehicles.update(vehicleId, input)
                }
                if (ok) editing { it.copy(saving = false, saved = true) } else _state.value = EditVehicleUiState.NotFound
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                editing { it.copy(saving = false, saveFailed = true) }
            }
        }
    }

    private fun editing(change: (EditVehicleUiState.Editing) -> EditVehicleUiState.Editing) =
        _state.update { if (it is EditVehicleUiState.Editing) change(it) else it }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as PicksmartApplication).container
                EditVehicleViewModel(createSavedStateHandle(), container.vehicles, container.userPreferences)
            }
        }
    }
}
