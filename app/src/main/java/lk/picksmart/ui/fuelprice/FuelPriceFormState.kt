package lk.picksmart.ui.fuelprice

import lk.picksmart.domain.model.FuelPriceList
import lk.picksmart.domain.model.FuelType
import lk.picksmart.domain.model.Vehicle
import lk.picksmart.ui.components.FieldError
import lk.picksmart.ui.vehicle.priceAfterFuelTypeSwitch
import lk.picksmart.util.centsToInput
import lk.picksmart.util.parseRupeesToCents

/**
 * The fuel type and price fields on the Fuel price screen, as typed. [baseType] and [basePriceCents]
 * are the vehicle's values the form started from, so the screen knows whether there is anything
 * to save.
 */
data class FuelPriceFormState(
    val baseType: FuelType,
    val basePriceCents: Long,
    val fuelType: FuelType = baseType,
    val price: String = centsToInput(basePriceCents),
) {
    val isChanged: Boolean
        get() = fuelType != baseType || parseRupeesToCents(price) != basePriceCents

    /** The price problem, or null. A price is required; 0 is allowed, as on Edit vehicle. */
    fun priceError(): FieldError? = when {
        price.isBlank() -> FieldError.REQUIRED
        parseRupeesToCents(price) == null -> FieldError.INVALID
        else -> null
    }

    /** The typed price in cents, or null while it is invalid. */
    fun priceCents(): Long? = parseRupeesToCents(price)

    /**
     * Picks a fuel type and fills in its price from [suggestions] (`fuelPriceSuggestions`: the
     * vehicle's own price for its own type, then the rider's last price for that type, then the
     * published one). With none, the other type's price is cleared for the rider to type.
     */
    fun withFuelType(type: FuelType, suggestions: Map<FuelType, Long>): FuelPriceFormState =
        copy(fuelType = type, price = priceAfterFuelTypeSwitch(price, fuelType, type, suggestions))

    /** True when the typed price is exactly the [published] price for the chosen fuel type. */
    fun isPublishedPrice(published: FuelPriceList?): Boolean =
        published != null && published.priceFor(fuelType) == priceCents()

    companion object {
        fun from(vehicle: Vehicle) = FuelPriceFormState(vehicle.fuelType, vehicle.fuelPricePerLitreCents)
    }
}
