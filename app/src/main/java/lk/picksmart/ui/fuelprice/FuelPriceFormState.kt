package lk.picksmart.ui.fuelprice

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

    companion object {
        fun from(vehicle: Vehicle) = FuelPriceFormState(vehicle.fuelType, vehicle.fuelPricePerLitreCents)
    }
}
