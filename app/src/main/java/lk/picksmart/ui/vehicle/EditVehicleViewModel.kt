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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import lk.picksmart.PicksmartApplication
import lk.picksmart.data.preferences.UserPreferencesRepository
import lk.picksmart.data.repository.VehicleRepository
import lk.picksmart.domain.model.FuelType
import lk.picksmart.domain.usecase.ArchivePlan
import lk.picksmart.domain.usecase.archivePlan
import lk.picksmart.domain.usecase.fuelPriceSuggestions
import lk.picksmart.ui.navigation.EditVehicleRoute

sealed interface EditVehicleUiState {
    data object Loading : EditVehicleUiState

    /** The vehicle was removed while the screen was open, or the id is stale. */
    data object NotFound : EditVehicleUiState

    /**
     * @property fuelPriceSuggestions The price to fill in for each fuel type (`fuelPriceSuggestions`).
     * @property archivePlan What archiving needs; null while adding a vehicle.
     */
    data class Editing(
        val form: VehicleFormState,
        val isNew: Boolean,
        val isSample: Boolean,
        val fuelPriceSuggestions: Map<FuelType, Long> = emptyMap(),
        val archivePlan: ArchivePlan? = null,
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
            val ownPrices = preferences.ownFuelPrices.first()
            val published = preferences.fuelPriceList.first()
            _state.value = if (vehicleId == EditVehicleRoute.NEW) {
                EditVehicleUiState.Editing(
                    VehicleFormState(),
                    isNew = true,
                    isSample = false,
                    fuelPriceSuggestions = fuelPriceSuggestions(null, ownPrices, published),
                )
            } else {
                vehicles.get(vehicleId)
                    ?.let {
                        EditVehicleUiState.Editing(
                            it.toFormState(),
                            isNew = false,
                            isSample = it.isSample,
                            // A sample price is only an example, so it isn't offered again.
                            fuelPriceSuggestions = fuelPriceSuggestions(it.takeUnless { v -> v.isSample }, ownPrices, published),
                        )
                    }
                    ?: EditVehicleUiState.NotFound
            }
            if (vehicleId == EditVehicleRoute.NEW) return@launch
            // Once loaded, keep the delete action in step with the vehicle list.
            combine(vehicles.activeVehicles, preferences.defaultVehicleId) { list, activeId -> list to activeId }
                .collect { (list, activeId) ->
                    editing { it.copy(archivePlan = archivePlan(list, vehicleId, activeId)) }
                }
        }
    }

    /**
     * Deletes this vehicle from the list after the rider confirmed (an archive: its trips and
     * records stay in History and Reports); when it is the active one, [newActiveId] becomes
     * active. The screen then closes.
     */
    fun archive(newActiveId: Long?) {
        val current = _state.value as? EditVehicleUiState.Editing ?: return
        if (current.saving || current.archivePlan == null || current.archivePlan == ArchivePlan.LastVehicle) return
        editing { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            try {
                vehicles.archive(vehicleId, newActiveId)
                editing { it.copy(saving = false, saved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                editing { it.copy(saving = false, saveFailed = true) }
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
                    // Becomes the default when there is none (or its row is gone), so Check a Ride has rates to use.
                    if (vehicles.defaultVehicle.first() == null) preferences.setDefaultVehicleId(id)
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
