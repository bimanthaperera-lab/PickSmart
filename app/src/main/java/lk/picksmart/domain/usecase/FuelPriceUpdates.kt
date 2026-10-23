package lk.picksmart.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import lk.picksmart.data.preferences.UserPreferencesRepository
import lk.picksmart.data.repository.VehicleRepository
import lk.picksmart.domain.model.FuelPriceList
import lk.picksmart.domain.model.FuelPriceSource
import lk.picksmart.domain.model.Vehicle
import java.time.Clock

/**
 * The default vehicle's fuel price next to the published list.
 *
 * @property action What to do about the published price (see [publishedPriceAction]).
 * @property listStale The list hasn't been refreshed for over 45 days, so a published price "may
 *   be out of date".
 * @property autoApplied A published price was applied automatically and the rider hasn't
 *   dismissed the notice yet.
 */
data class FuelPriceState(
    val vehicle: Vehicle?,
    val list: FuelPriceList?,
    val action: PublishedPriceAction,
    val listStale: Boolean,
    val autoApplied: Boolean,
)

/**
 * Applies the published fuel price rules of CLAUDE.md section 8a to the default vehicle. The
 * rules themselves are the pure functions in `PublishedFuelPrice.kt`; this class reads and writes
 * the repositories.
 */
class FuelPriceUpdates(
    private val vehicles: VehicleRepository,
    private val preferences: UserPreferencesRepository,
    private val clock: Clock,
) {
    val state: Flow<FuelPriceState> = combine(
        vehicles.defaultVehicle,
        preferences.fuelPriceList,
        preferences.keptFuelPrices,
        preferences.fuelPriceAutoNoticeVehicleId,
    ) { vehicle, list, kept, noticeVehicleId ->
        FuelPriceState(
            vehicle = vehicle,
            list = list,
            action = vehicle?.let { publishedPriceAction(it, list, kept) } ?: PublishedPriceAction.None,
            listStale = list != null && isFuelPriceListStale(list, clock.millis()),
            // A price typed since then replaces the notice's news, so it stops showing.
            autoApplied = vehicle != null && noticeVehicleId == vehicle.id &&
                vehicle.fuelPriceSource == FuelPriceSource.PUBLISHED,
        )
    }

    /**
     * Applies the published price to the default vehicle if the rider turned auto-update on, and
     * leaves a one-time notice. Runs after each price check.
     */
    suspend fun applyAutomatic() {
        val vehicle = vehicles.defaultVehicle.first() ?: return
        val action = publishedPriceAction(vehicle, preferences.fuelPriceList.first(), preferences.keptFuelPrices.first())
        if (action is PublishedPriceAction.Apply &&
            vehicles.setPublishedFuelPrice(vehicle.id, action.priceCents, action.effectiveFrom)
        ) {
            preferences.setFuelPriceAutoNoticeVehicleId(vehicle.id)
        }
    }

    /** "Update" on the prompt: the vehicle takes the published price. */
    suspend fun accept(vehicleId: Long, offer: PublishedPriceAction.Offer) {
        vehicles.setPublishedFuelPrice(vehicleId, offer.priceCents, offer.effectiveFrom)
    }

    /** "Keep" on the prompt: the price stays, and this list isn't offered again. */
    suspend fun keep(vehicleId: Long, offer: PublishedPriceAction.Offer) {
        preferences.addKeptFuelPrice(keptFuelPriceKey(vehicleId, offer.effectiveFrom))
    }

    /**
     * Turns auto-update on or off. Turning it on applies a different published price straight
     * away; the rider is looking at the screen, so no separate notice is left.
     */
    suspend fun setAutoUpdate(vehicleId: Long, on: Boolean) {
        if (!vehicles.setAutoUpdateFuelPrice(vehicleId, on) || !on) return
        val vehicle = vehicles.get(vehicleId) ?: return
        val action = publishedPriceAction(vehicle, preferences.fuelPriceList.first(), emptySet())
        if (action is PublishedPriceAction.Apply) vehicles.setPublishedFuelPrice(vehicleId, action.priceCents, action.effectiveFrom)
    }

    suspend fun dismissAutoNotice() {
        preferences.setFuelPriceAutoNoticeVehicleId(null)
    }
}
