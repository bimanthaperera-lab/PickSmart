package lk.picksmart.ui.vehicle

import lk.picksmart.domain.model.FuelType
import lk.picksmart.domain.model.Vehicle
import lk.picksmart.domain.model.VehicleInput
import lk.picksmart.domain.model.VehicleType
import lk.picksmart.util.InputDigits
import lk.picksmart.util.centsToInput
import lk.picksmart.util.decimalToInput
import lk.picksmart.util.parseDecimal
import lk.picksmart.util.parseRupeesToCents
import java.math.BigDecimal
import java.util.Locale

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
    val plateNumber: String = "",
)

/** Longest number plate the form accepts, e.g. "WP CAB-1234" is 11. */
const val PLATE_NUMBER_MAX_LENGTH = 15

enum class VehicleField { NAME, PLATE, FUEL_PRICE, EFFICIENCY, MAINTENANCE, TARGET }

enum class FieldError {
    /** Left empty but needed. */
    REQUIRED,

    /** Must be more than zero (fuel efficiency, profit target when given). */
    MUST_BE_ABOVE_ZERO,

    /** Not a usable number. */
    INVALID,
}

sealed interface VehicleFormResult {
    data class Valid(val input: VehicleInput) : VehicleFormResult
    data class Invalid(val errors: Map<VehicleField, FieldError>) : VehicleFormResult
}

/**
 * Checks the form. The name, number plate, fuel price and efficiency are required (owner request
 * 2026-10-30); efficiency must be more than 0. Maintenance may be left empty (0). The profit target
 * is optional (null when empty) but must be more than 0 when given.
 */
fun VehicleFormState.validate(): VehicleFormResult {
    val errors = mutableMapOf<VehicleField, FieldError>()

    val vehicleName = name.trim().ifEmpty { null }
    if (vehicleName == null) errors[VehicleField.NAME] = FieldError.REQUIRED

    val plate = normalizePlateNumber(plateNumber)
    if (plate == null) errors[VehicleField.PLATE] = FieldError.REQUIRED

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
        val parsed = parseRupeesToCents(target)
        when {
            parsed == null -> {
                errors[VehicleField.TARGET] = FieldError.INVALID
                null
            }
            parsed <= 0 -> {
                errors[VehicleField.TARGET] = FieldError.MUST_BE_ABOVE_ZERO
                null
            }
            else -> parsed
        }
    }

    if (errors.isNotEmpty() || vehicleName == null || plate == null || price == null || kmPerLitre == null ||
        reserve == null
    ) {
        return VehicleFormResult.Invalid(errors)
    }
    return VehicleFormResult.Valid(
        VehicleInput(
            name = vehicleName,
            type = type,
            fuelType = fuelType,
            fuelPricePerLitreCents = price,
            fuelEfficiencyKmPerLitre = kmPerLitre,
            maintenanceReservePerKmCents = reserve,
            targetProfitPerKmCents = targetCents,
            plateNumber = plate,
        ),
    )
}

/**
 * Picks a fuel type and fills in its price from [suggestions] (`fuelPriceSuggestions`); with none,
 * the other type's price is cleared for the rider to type (see [priceAfterFuelTypeSwitch]).
 */
fun VehicleFormState.withFuelType(type: FuelType, suggestions: Map<FuelType, Long>): VehicleFormState =
    copy(fuelType = type, fuelPrice = priceAfterFuelTypeSwitch(fuelPrice, fuelType, type, suggestions))

/**
 * The fuel price after a step button: [rupees] more (or less, when negative), shown with two
 * decimals ("414.00"). An empty price starts from 0. A price that would drop below 0 or grow past
 * the field's [InputDigits.MONEY] digits, or text that isn't a number, stays as it is.
 */
fun VehicleFormState.stepFuelPrice(rupees: Long): VehicleFormState {
    val current = if (fuelPrice.isBlank()) 0L else parseRupeesToCents(fuelPrice) ?: return this
    val cents = current + rupees * 100
    if (cents < 0 || cents >= MAX_PRICE_CENTS) return this
    return copy(fuelPrice = centsToInput(cents))
}

/** One more than the largest price the field takes ([InputDigits.MONEY] whole rupees). */
private val MAX_PRICE_CENTS = BigDecimal.TEN.pow(InputDigits.MONEY).movePointRight(2).toLong()

/** Fills the form from a saved vehicle, for editing. */
fun Vehicle.toFormState() = VehicleFormState(
    name = name,
    type = type,
    fuelType = fuelType,
    fuelPrice = centsToInput(fuelPricePerLitreCents),
    efficiency = decimalToInput(fuelEfficiencyKmPerLitre),
    maintenance = centsToInput(maintenanceReservePerKmCents),
    target = targetProfitPerKmCents?.let(::centsToInput).orEmpty(),
    plateNumber = plateNumber.orEmpty(),
)

/**
 * Tidies a typed number plate: capitals, single spaces, no spaces at the ends, so " wp  cab-1234 "
 * is saved as "WP CAB-1234". Any format is accepted (old plates use Sinhala letters). Null when blank.
 */
fun normalizePlateNumber(text: String): String? =
    text.trim().replace(Regex("\\s+"), " ").uppercase(Locale.ROOT).take(PLATE_NUMBER_MAX_LENGTH).trimEnd().ifEmpty { null }
