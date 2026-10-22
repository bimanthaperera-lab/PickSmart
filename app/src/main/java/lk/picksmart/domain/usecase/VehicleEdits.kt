package lk.picksmart.domain.usecase

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
 * - If the rider changed the fuel price or fuel type by hand, the price becomes
 *   [FuelPriceSource.MANUAL] and auto-update turns off, so a published price never overwrites a
 *   price they set (CLAUDE.md section 8a). An unchanged price keeps its source.
 * - Past trips are unaffected: they keep their own rate snapshot.
 */
fun applyVehicleEdit(current: Vehicle, input: VehicleInput, nowMillis: Long): Vehicle {
    val priceTouched = input.fuelPricePerLitreCents != current.fuelPricePerLitreCents ||
        input.fuelType != current.fuelType
    return current.copy(
        name = input.name,
        type = input.type,
        fuelType = input.fuelType,
        fuelPricePerLitreCents = input.fuelPricePerLitreCents,
        fuelPriceSource = if (priceTouched) FuelPriceSource.MANUAL else current.fuelPriceSource,
        fuelPriceEffectiveDate = if (priceTouched) null else current.fuelPriceEffectiveDate,
        autoUpdateFuelPrice = if (priceTouched) false else current.autoUpdateFuelPrice,
        fuelEfficiencyKmPerLitre = input.fuelEfficiencyKmPerLitre,
        maintenanceReservePerKmCents = input.maintenanceReservePerKmCents,
        targetProfitPerKmCents = input.targetProfitPerKmCents,
        isSample = false,
        updatedAtMillis = nowMillis,
    )
}
