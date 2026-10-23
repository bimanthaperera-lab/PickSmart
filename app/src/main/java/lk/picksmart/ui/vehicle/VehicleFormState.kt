package lk.picksmart.ui.vehicle

import lk.picksmart.domain.model.FuelType
import lk.picksmart.domain.model.Vehicle
import lk.picksmart.domain.model.VehicleInput
import lk.picksmart.domain.model.VehicleType
import lk.picksmart.util.centsToInput
import lk.picksmart.util.decimalToInput
import lk.picksmart.util.parseDecimal
import lk.picksmart.util.parseRupeesToCents

/**
 * What the rider has typed on the vehicle form (onboarding and Edit vehicle). Number fields stay
 * as text until [validate], so a half-typed "12." is never lost or reformatted.
 */
data class VehicleFormState(
    val name: String = "",
    val type: VehicleType = VehicleType.MOTORCYCLE,
    val fuelType: FuelType = FuelType.PETROL_92,
    val fuelPrice: String = "",
    val efficiency: String = "",
    val maintenance: String = "",
    val target: String = "",
)

enum class VehicleField { FUEL_PRICE, EFFICIENCY, MAINTENANCE, TARGET }

enum class FieldError {
    /** Left empty but needed. */
    REQUIRED,

    /** Must be more than zero (fuel efficiency). */
    MUST_BE_ABOVE_ZERO,

    /** Not a usable number. */
    INVALID,
}

sealed interface VehicleFormResult {
    data class Valid(val input: VehicleInput) : VehicleFormResult
    data class Invalid(val errors: Map<VehicleField, FieldError>) : VehicleFormResult
}

/**
 * Checks the form. Fuel price and efficiency are required; maintenance may be left empty (0);
 * the target is optional (null when empty). A blank name becomes [defaultName].
 */
fun VehicleFormState.validate(defaultName: String): VehicleFormResult {
    val errors = mutableMapOf<VehicleField, FieldError>()

    val price = if (fuelPrice.isBlank()) {
        errors[VehicleField.FUEL_PRICE] = FieldError.REQUIRED
        null
    } else {
        parseRupeesToCents(fuelPrice).also { if (it == null) errors[VehicleField.FUEL_PRICE] = FieldError.INVALID }
    }

    val kmPerLitre = if (efficiency.isBlank()) {
        errors[VehicleField.EFFICIENCY] = FieldError.REQUIRED
        null
    } else {
        val parsed = parseDecimal(efficiency)
        when {
            parsed == null -> {
                errors[VehicleField.EFFICIENCY] = FieldError.INVALID
                null
            }
            parsed <= 0 -> {
                errors[VehicleField.EFFICIENCY] = FieldError.MUST_BE_ABOVE_ZERO
                null
            }
            else -> parsed
        }
    }

    val reserve = if (maintenance.isBlank()) {
        0L
    } else {
        parseRupeesToCents(maintenance).also { if (it == null) errors[VehicleField.MAINTENANCE] = FieldError.INVALID }
    }

    val targetCents = if (target.isBlank()) {
        null
    } else {
        parseRupeesToCents(target).also { if (it == null) errors[VehicleField.TARGET] = FieldError.INVALID }
    }

    if (errors.isNotEmpty() || price == null || kmPerLitre == null || reserve == null) {
        return VehicleFormResult.Invalid(errors)
    }
    return VehicleFormResult.Valid(
        VehicleInput(
            name = name.trim().ifEmpty { defaultName },
            type = type,
            fuelType = fuelType,
            fuelPricePerLitreCents = price,
            fuelEfficiencyKmPerLitre = kmPerLitre,
            maintenanceReservePerKmCents = reserve,
            targetProfitPerKmCents = targetCents,
        ),
    )
}

/**
 * Picks a fuel type and fills in its price from [suggestions] (`fuelPriceSuggestions`); with none,
 * the other type's price is cleared for the rider to type (see [priceAfterFuelTypeSwitch]).
 */
fun VehicleFormState.withFuelType(type: FuelType, suggestions: Map<FuelType, Long>): VehicleFormState =
    copy(fuelType = type, fuelPrice = priceAfterFuelTypeSwitch(fuelPrice, fuelType, type, suggestions))

/** Fills the form from a saved vehicle, for editing. */
fun Vehicle.toFormState() = VehicleFormState(
    name = name,
    type = type,
    fuelType = fuelType,
    fuelPrice = centsToInput(fuelPricePerLitreCents),
    efficiency = decimalToInput(fuelEfficiencyKmPerLitre),
    maintenance = centsToInput(maintenanceReservePerKmCents),
    target = targetProfitPerKmCents?.let(::centsToInput).orEmpty(),
)
