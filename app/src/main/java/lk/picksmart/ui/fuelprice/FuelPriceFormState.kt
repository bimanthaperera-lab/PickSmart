package lk.picksmart.ui.fuelprice

import lk.picksmart.domain.model.FuelPriceList
import lk.picksmart.domain.model.FuelType
import lk.picksmart.domain.model.Vehicle
import lk.picksmart.ui.components.FieldError
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
     * Picks a fuel type and fills in its price: the vehicle's own price for its own type,
     * otherwise that type's [published] price. With no published price for it, the typed price
     * stays for the rider to change.
     */
    fun withFuelType(type: FuelType, published: FuelPriceList?): FuelPriceFormState {
        val cents = if (type == baseType) basePriceCents else published?.priceFor(type)
        return copy(fuelType = type, price = cents?.let(::centsToInput) ?: price)
    }

    /** True when the typed price is exactly the [published] price for the chosen fuel type. */
    fun isPublishedPrice(published: FuelPriceList?): Boolean =
        published != null && published.priceFor(fuelType) == priceCents()

    companion object {
        fun from(vehicle: Vehicle) = FuelPriceFormState(vehicle.fuelType, vehicle.fuelPricePerLitreCents)
    }
}
