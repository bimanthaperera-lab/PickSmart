package lk.picksmart.domain.usecase

import lk.picksmart.domain.model.FuelPriceList
import lk.picksmart.domain.model.FuelType
import lk.picksmart.domain.model.Vehicle

/**
 * The price to fill in for each fuel type when the rider picks one (Fuel price screen, Edit
 * vehicle, onboarding), in cents per litre. For each type, the first of:
 *
 * 1. the [vehicle]'s own price, for its own fuel type;
 * 2. the last price the rider used for that type on this phone ([ownPrices]), because the
 *    rider's own number wins over a published one (CLAUDE.md section 8a);
 * 3. the [published] CPC price for it.
 *
 * A type with none of these is left out, and the form leaves its price for the rider to type.
 */
fun fuelPriceSuggestions(
    vehicle: Vehicle?,
    ownPrices: Map<FuelType, Long>,
    published: FuelPriceList?,
): Map<FuelType, Long> = FuelType.entries.mapNotNull { type ->
    val cents = when {
        vehicle != null && vehicle.fuelType == type -> vehicle.fuelPricePerLitreCents
        else -> ownPrices[type] ?: published?.priceFor(type)
    }
    cents?.let { type to it }
}.toMap()
