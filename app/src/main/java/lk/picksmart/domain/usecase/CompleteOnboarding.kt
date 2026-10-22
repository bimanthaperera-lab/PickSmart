package lk.picksmart.domain.usecase

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
}
