package lk.picksmart.domain.usecase

import lk.picksmart.domain.model.FuelPriceList
import lk.picksmart.domain.model.FuelPriceSource
import lk.picksmart.domain.model.FuelType
import lk.picksmart.domain.model.Vehicle
import lk.picksmart.domain.model.VehicleInput
import lk.picksmart.domain.model.VehicleType

/**
 * Sample rates offered during onboarding, for a rider who wants to look around first. They are
 * placeholders, not real prices: the vehicle is flagged [Vehicle.isSample] and the UI labels it
 * "Sample" until the rider saves their own values.
 */
fun sampleVehicleInput(name: String): VehicleInput = VehicleInput(
    name = name,
    type = VehicleType.MOTORCYCLE,
    fuelType = FuelType.PETROL_92,
    fuelPricePerLitreCents = 30_000,
    fuelEfficiencyKmPerLitre = 40.0,
    maintenanceReservePerKmCents = 200,
    targetProfitPerKmCents = null,
)

/** A new vehicle from the rider's input. A typed price is always [FuelPriceSource.MANUAL]. */
fun newVehicle(input: VehicleInput, isSample: Boolean, nowMillis: Long): Vehicle = Vehicle(
    id = 0,
    name = input.name,
    type = input.type,
    fuelType = input.fuelType,
    fuelPricePerLitreCents = input.fuelPricePerLitreCents,
    fuelPriceSource = FuelPriceSource.MANUAL,
    fuelPriceEffectiveDate = null,
    autoUpdateFuelPrice = false,
    fuelEfficiencyKmPerLitre = input.fuelEfficiencyKmPerLitre,
    maintenanceReservePerKmCents = input.maintenanceReservePerKmCents,
    targetProfitPerKmCents = input.targetProfitPerKmCents,
    isSample = isSample,
    archived = false,
    createdAtMillis = nowMillis,
    updatedAtMillis = nowMillis,
)

/**
 * Applies the rider's edits to [current].
 *
 * - Saving from the form means the rider has reviewed the values, so the vehicle stops being a
 *   sample.
 * - Fuel type and price follow [applyFuelPriceEdit]: a price set by hand becomes
 *   [FuelPriceSource.MANUAL] and turns auto-update off (CLAUDE.md section 8a), unless it is
 *   exactly the [published] price for the chosen fuel type.
 * - Past trips are unaffected: they keep their own rate snapshot.
 */
fun applyVehicleEdit(current: Vehicle, input: VehicleInput, nowMillis: Long, published: FuelPriceList? = null): Vehicle =
    applyFuelPriceEdit(current, input.fuelType, input.fuelPricePerLitreCents, nowMillis, published).copy(
        name = input.name,
        type = input.type,
        fuelEfficiencyKmPerLitre = input.fuelEfficiencyKmPerLitre,
        maintenanceReservePerKmCents = input.maintenanceReservePerKmCents,
        targetProfitPerKmCents = input.targetProfitPerKmCents,
        isSample = false,
        updatedAtMillis = nowMillis,
    )

/**
 * Sets the fuel type and price the rider chose (the Fuel price screen, or Edit vehicle). With
 * nothing changed, the vehicle is returned as it was. Other rates, and the "Sample" label on
 * them, stay as they were.
 *
 * - The [published] price for the chosen fuel type (e.g. after switching from Petrol 92 to
 *   Diesel, which fills in the Diesel CPC price) stays [FuelPriceSource.PUBLISHED] with its date,
 *   and auto-update stays as it was.
 * - Any other price was typed by hand: it becomes [FuelPriceSource.MANUAL] and auto-update turns
 *   off, so a published price never overwrites it (CLAUDE.md section 8a).
 */
fun applyFuelPriceEdit(
    current: Vehicle,
    fuelType: FuelType,
    priceCents: Long,
    nowMillis: Long,
    published: FuelPriceList? = null,
): Vehicle {
    if (fuelType == current.fuelType && priceCents == current.fuelPricePerLitreCents) return current
    if (published != null && published.priceFor(fuelType) == priceCents) {
        return applyPublishedPrice(current.copy(fuelType = fuelType), priceCents, published.effectiveFrom, nowMillis)
    }
    return current.copy(
        fuelType = fuelType,
        fuelPricePerLitreCents = priceCents,
        fuelPriceSource = FuelPriceSource.MANUAL,
        fuelPriceEffectiveDate = null,
        autoUpdateFuelPrice = false,
        updatedAtMillis = nowMillis,
    )
}

/**
 * Sets the profit target per km from Check a Ride (null turns it off). Only the target changes;
 * past trips keep the target in their own snapshot, so their bands don't move.
 */
fun applyTargetUpdate(current: Vehicle, targetCents: Long?, nowMillis: Long): Vehicle =
    current.copy(targetProfitPerKmCents = targetCents, updatedAtMillis = nowMillis)

/**
 * Sets the km per litre the rider accepted from a measured reading. Only efficiency changes: the
 * other rates (and the "Sample" label on them) stay as they were, and past trips keep their own
 * snapshot.
 */
fun applyEfficiencyUpdate(current: Vehicle, kmPerLitre: Double, nowMillis: Long): Vehicle =
    current.copy(fuelEfficiencyKmPerLitre = kmPerLitre, updatedAtMillis = nowMillis)
