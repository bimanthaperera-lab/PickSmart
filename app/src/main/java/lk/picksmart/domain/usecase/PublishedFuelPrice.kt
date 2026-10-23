package lk.picksmart.domain.usecase

import lk.picksmart.domain.model.FuelPriceList
import lk.picksmart.domain.model.FuelPriceSource
import lk.picksmart.domain.model.Vehicle
import java.time.Duration
import java.time.LocalDate

/** What to do with a published price for one vehicle (CLAUDE.md section 8a). */
sealed interface PublishedPriceAction {
    /** Nothing: no list, the same price, or the rider already chose to keep theirs. */
    data object None : PublishedPriceAction

    /** Ask the rider, showing both prices. Auto-update is off. */
    data class Offer(val priceCents: Long, val effectiveFrom: LocalDate) : PublishedPriceAction

    /** Apply it and show a one-time notice. The rider turned auto-update on. */
    data class Apply(val priceCents: Long, val effectiveFrom: LocalDate) : PublishedPriceAction
}

/**
 * Compares [vehicle]'s price with the published price for its fuel type.
 *
 * - A different price is applied only when [Vehicle.autoUpdateFuelPrice] is on. Typing a price by
 *   hand turns it off ([applyVehicleEdit]), so a price the rider set is never overwritten; they
 *   are asked instead.
 * - Once the rider taps "Keep" for a list ([kept] holds [keptFuelPriceKey]), that list is not
 *   offered again. A newer list is.
 */
fun publishedPriceAction(vehicle: Vehicle, list: FuelPriceList?, kept: Set<String>): PublishedPriceAction {
    val published = list?.priceFor(vehicle.fuelType) ?: return PublishedPriceAction.None
    return when {
        published == vehicle.fuelPricePerLitreCents -> PublishedPriceAction.None
        vehicle.autoUpdateFuelPrice -> PublishedPriceAction.Apply(published, list.effectiveFrom)
        keptFuelPriceKey(vehicle.id, list.effectiveFrom) in kept -> PublishedPriceAction.None
        else -> PublishedPriceAction.Offer(published, list.effectiveFrom)
    }
}

/** Marks that the rider kept their own price over the list dated [effectiveFrom]. */
fun keptFuelPriceKey(vehicleId: Long, effectiveFrom: LocalDate): String = "$vehicleId@$effectiveFrom"

/**
 * Sets a published price on the vehicle. Only the fuel price and where it came from change;
 * auto-update stays as the rider set it. Past trips keep their own rate snapshot, so only new
 * checks use the new price.
 */
fun applyPublishedPrice(current: Vehicle, priceCents: Long, effectiveFrom: LocalDate, nowMillis: Long): Vehicle =
    current.copy(
        fuelPricePerLitreCents = priceCents,
        fuelPriceSource = FuelPriceSource.PUBLISHED,
        fuelPriceEffectiveDate = effectiveFrom,
        updatedAtMillis = nowMillis,
    )

/** Turns auto-update on or off. Nothing else changes; turning it on applies a newer price separately. */
fun applyAutoUpdateSetting(current: Vehicle, on: Boolean, nowMillis: Long): Vehicle =
    current.copy(autoUpdateFuelPrice = on, updatedAtMillis = nowMillis)

/**
 * True when the app hasn't fetched a valid list for more than 45 days, so a published price may
 * be out of date. A clock set before the fetch counts as fresh.
 */
fun isFuelPriceListStale(list: FuelPriceList, nowMillis: Long): Boolean =
    nowMillis - list.fetchedAtMillis > STALE_AFTER.toMillis()

private val STALE_AFTER: Duration = Duration.ofDays(45)
