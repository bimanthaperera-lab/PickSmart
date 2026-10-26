package lk.picksmart.domain.usecase

import kotlinx.coroutines.flow.first
import lk.picksmart.data.preferences.UserPreferencesRepository
import lk.picksmart.data.repository.VehicleRepository
import lk.picksmart.domain.model.VehicleInput

/**
 * Finishes first-launch setup: saves the vehicle, then makes it the default and marks setup done
 * in one DataStore write. If the app dies before that write, setup simply runs again; an
 * existing rider's flag is never cleared.
 */
class CompleteOnboarding(
    private val vehicles: VehicleRepository,
    private val preferences: UserPreferencesRepository,
) {
    suspend operator fun invoke(input: VehicleInput, isSample: Boolean) {
        val id = vehicles.create(input, isSample)
        preferences.completeOnboarding(defaultVehicleId = id)
    }

    /** True when the phone has a vehicle in use, for example one restored from the rider's backup. */
    suspend fun hasVehicle(): Boolean = vehicles.activeVehicles.first().isNotEmpty()

    /**
     * Finishes setup for a rider whose backup brought their vehicles back: the vehicle the backup
     * made active stays active, else the first one. Nothing new is saved.
     */
    suspend fun withRestoredVehicle() {
        val inUse = vehicles.activeVehicles.first()
        val activeId = preferences.defaultVehicleId.first()
        val chosen = inUse.firstOrNull { it.id == activeId } ?: inUse.first()
        preferences.completeOnboarding(defaultVehicleId = chosen.id)
    }
}
