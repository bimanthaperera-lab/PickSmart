package lk.picksmart.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import lk.picksmart.PicksmartApplication
import lk.picksmart.data.account.AccountProvider
import lk.picksmart.data.account.signedIn
import lk.picksmart.data.preferences.UserPreferencesRepository
import lk.picksmart.data.repository.VehicleRepository
import lk.picksmart.domain.model.ThemeMode
import lk.picksmart.domain.model.Vehicle
import lk.picksmart.domain.usecase.FuelPriceState
import lk.picksmart.domain.usecase.FuelPriceUpdates
import lk.picksmart.domain.usecase.canAddVehicle
import lk.picksmart.domain.usecase.vehicleLimit

sealed interface VehicleSection {
    data object Loading : VehicleSection
    data object None : VehicleSection

    /**
     * @property vehicles Every vehicle that isn't archived, oldest first.
     * @property active The active vehicle, whose rates Check a Ride uses.
     * @property canAdd Under the vehicle limit; otherwise "Add vehicle" opens the limit screen.
     */
    data class Loaded(val vehicles: List<Vehicle>, val active: Vehicle, val canAdd: Boolean) : VehicleSection
}

class SettingsViewModel(
    private val preferences: UserPreferencesRepository,
    vehicles: VehicleRepository,
    fuelPriceUpdates: FuelPriceUpdates,
    account: AccountProvider,
) : ViewModel() {

    /** Null until the stored value has loaded. */
    val themeMode: StateFlow<ThemeMode?> = preferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val vehicle: StateFlow<VehicleSection> = combine(
        vehicles.activeVehicles,
        vehicles.defaultVehicle,
        account.signedIn,
    ) { list, default, signedIn ->
        // An active vehicle is always one of the list; fall back to the first if its row is gone.
        val active = default?.takeIf { !it.archived } ?: list.firstOrNull()
        if (active == null) VehicleSection.None else VehicleSection.Loaded(list, active, canAddVehicle(list, vehicleLimit(signedIn)))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehicleSection.Loading)

    /** The vehicle's fuel price next to the published list; null until loaded. */
    val fuelPrices: StateFlow<FuelPriceState?> = fuelPriceUpdates.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as PicksmartApplication).container
                SettingsViewModel(container.userPreferences, container.vehicles, container.fuelPriceUpdates, container.account)
            }
        }
    }
}

