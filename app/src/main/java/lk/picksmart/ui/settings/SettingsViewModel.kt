package lk.picksmart.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import lk.picksmart.PicksmartApplication
import lk.picksmart.data.preferences.UserPreferencesRepository
import lk.picksmart.data.repository.VehicleRepository
import lk.picksmart.domain.model.ThemeMode
import lk.picksmart.domain.model.Vehicle

sealed interface VehicleSection {
    data object Loading : VehicleSection
    data object None : VehicleSection
    data class Loaded(val vehicle: Vehicle) : VehicleSection
}

class SettingsViewModel(
    private val preferences: UserPreferencesRepository,
    vehicles: VehicleRepository,
) : ViewModel() {

    /** Null until the stored value has loaded. */
    val themeMode: StateFlow<ThemeMode?> = preferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val vehicle: StateFlow<VehicleSection> = vehicles.defaultVehicle
        .map { it?.let(VehicleSection::Loaded) ?: VehicleSection.None }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehicleSection.Loading)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as PicksmartApplication).container
                SettingsViewModel(container.userPreferences, container.vehicles)
            }
        }
    }
}
