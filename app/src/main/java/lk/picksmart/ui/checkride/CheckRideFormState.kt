package lk.picksmart.ui.checkride

import lk.picksmart.domain.calculator.CalculationResult
import lk.picksmart.domain.calculator.InputField
import lk.picksmart.domain.calculator.ProfitCalculator
import lk.picksmart.domain.calculator.ProfitInput
import lk.picksmart.domain.calculator.ProfitResult
import lk.picksmart.domain.calculator.ValidationError
import lk.picksmart.domain.model.CommissionType
import lk.picksmart.domain.model.FareMode
import lk.picksmart.domain.model.LastCheckInputs
import lk.picksmart.domain.model.Platform
import lk.picksmart.domain.model.TripRates
import lk.picksmart.domain.model.Vehicle
import lk.picksmart.util.addRupeesToInput
import lk.picksmart.util.centsToInput
import lk.picksmart.util.decimalToInput
import lk.picksmart.util.hundredthsToInput
import lk.picksmart.util.parseDecimal
import lk.picksmart.util.parseRupeesToCents

/**
 * What the rider has typed on Check a Ride. Number fields stay as text, so a half-typed "12." is
 * never lost. Empty optional fields (commission, other costs, distances) count as 0.
 *
 * @property overrides Rates typed for this check only, or null to use the vehicle's rates.
 * @property pickupLocation Optional free text, saved with the trip; it never affects the result
 *   and isn't remembered for the next check.
 * @property dropOffLocation The optional "Where to", as [pickupLocation].
 */
data class CheckRideFormState(
    val platform: Platform = Platform.UBER,
    val fare: String = "",
    val fareMode: FareMode = FareMode.GROSS_BEFORE_COMMISSION,
    val commissionType: CommissionType = CommissionType.PERCENT,
    val commission: String = "",
    val otherCosts: String = "",
    val pickupKm: String = "",
    val tripKm: String = "",
    val returnKm: String = "",
    val overrides: RateOverrides? = null,
    val pickupLocation: String = "",
    val dropOffLocation: String = "",
)

/** Per-check replacements for the vehicle's rates. They never change the saved vehicle. */
data class RateOverrides(
    val fuelPrice: String,
    val efficiency: String,
    val maintenance: String,
)

enum class CheckRideError {
    /** Left empty but needed (an overridden fuel price or efficiency). */
    REQUIRED,

    /** Not a usable number. */
    INVALID,

    /** Must be more than zero (fuel efficiency). */
    MUST_BE_ABOVE_ZERO,

    /** A percentage commission above 100%. */
    PERCENT_ABOVE_100,
}

/** What Check a Ride shows for the current form. */
sealed interface CheckRideOutcome {
    /** Everything typed so far is fine; the fare is still empty. */
    data object NeedsFare : CheckRideOutcome

    /**
     * Something can't be calculated. [resultTooLarge] is set when every field is valid but the
     * numbers are too big to hold in cents.
     */
    data class Invalid(
        val errors: Map<InputField, CheckRideError>,
        val resultTooLarge: Boolean = false,
    ) : CheckRideOutcome

    data class Calculated(val input: ProfitInput, val result: ProfitResult) : CheckRideOutcome
}

/**
 * Turns the form into a [ProfitInput] with [rates] (unless overridden) and runs [calculator]. The form only checks
 * that each field parses; range rules (percent above 100, efficiency of 0) come from the
 * calculator's own validation, so there is one source of truth for them.
 */
fun CheckRideFormState.evaluate(rates: TripRates, calculator: ProfitCalculator): CheckRideOutcome {
    val errors = mutableMapOf<InputField, CheckRideError>()

    fun money(text: String, field: InputField): Long =
        if (text.isBlank()) 0L else parseRupeesToCents(text) ?: 0L.also { errors[field] = CheckRideError.INVALID }

    fun km(text: String, field: InputField): Double =
        if (text.isBlank()) 0.0 else parseDecimal(text) ?: 0.0.also { errors[field] = CheckRideError.INVALID }

    fun <T : Any> required(text: String, field: InputField, parse: (String) -> T?, fallback: T): T = when {
        text.isBlank() -> fallback.also { errors[field] = CheckRideError.REQUIRED }
        else -> parse(text) ?: fallback.also { errors[field] = CheckRideError.INVALID }
    }

    val fareBlank = fare.isBlank()
    val fareCents = money(fare, InputField.FARE)
    // Hundredths of a percent use the same two-decimal scale as cents, so one parser serves both.
    val commissionHundredths =
        if (fareMode == FareMode.GROSS_BEFORE_COMMISSION) money(commission, InputField.COMMISSION) else 0L
    val otherCostsCents = money(otherCosts, InputField.OTHER_COSTS)
    val pickup = km(pickupKm, InputField.PICKUP_KM)
    val trip = km(tripKm, InputField.TRIP_KM)
    val back = km(returnKm, InputField.RETURN_KM)

    val fuelPriceCents: Long
    val efficiency: Double
    val reserveCents: Long
    if (overrides == null) {
        fuelPriceCents = rates.fuelPricePerLitreCents
        efficiency = rates.fuelEfficiencyKmPerLitre
        reserveCents = rates.maintenanceReservePerKmCents
    } else {
        fuelPriceCents = required(overrides.fuelPrice, InputField.FUEL_PRICE, ::parseRupeesToCents, 0L)
        efficiency = required(overrides.efficiency, InputField.FUEL_EFFICIENCY, ::parseDecimal, 1.0)
        reserveCents = money(overrides.maintenance, InputField.MAINTENANCE_RESERVE)
    }

    if (errors.isNotEmpty()) return CheckRideOutcome.Invalid(errors)

    val input = ProfitInput(
        fareCents = fareCents,
        fareMode = fareMode,
        commissionType = commissionType,
        commissionHundredths = commissionHundredths,
        pickupKm = pickup,
        tripKm = trip,
        returnKm = back,
        fuelEfficiencyKmPerLitre = efficiency,
        fuelPricePerLitreCents = fuelPriceCents,
        maintenanceReservePerKmCents = reserveCents,
        otherCostsCents = otherCostsCents,
        targetProfitPerKmCents = rates.targetProfitPerKmCents,
    )
    // Still calculated with an empty fare (as 0), so range errors such as 150% show right away.
    return when (val calculation = calculator.calculate(input)) {
        is CalculationResult.Invalid -> calculation.errors.toOutcome()
        is CalculationResult.Success ->
            if (fareBlank) CheckRideOutcome.NeedsFare else CheckRideOutcome.Calculated(input, calculation.result)
    }
}

private fun List<ValidationError>.toOutcome(): CheckRideOutcome.Invalid {
    val errors = mutableMapOf<InputField, CheckRideError>()
    var tooLarge = false
    forEach { error ->
        when (error) {
            is ValidationError.Negative -> errors[error.field] = CheckRideError.INVALID
            is ValidationError.NotANumber -> errors[error.field] = CheckRideError.INVALID
            is ValidationError.NotPositive -> errors[error.field] = CheckRideError.MUST_BE_ABOVE_ZERO
            ValidationError.PercentAbove100 -> errors[InputField.COMMISSION] = CheckRideError.PERCENT_ABOVE_100
            ValidationError.ResultTooLarge -> tooLarge = true
        }
    }
    return CheckRideOutcome.Invalid(errors, tooLarge)
}

/**
 * The "+50" / "+100" buttons by the fare: adds [rupees] to it (an empty fare counts as 0). A fare
 * that doesn't parse, or one that would pass the fare field's digit limit, stays as it is.
 */
fun CheckRideFormState.addToFare(rupees: Long): CheckRideFormState = copy(fare = addRupeesToInput(fare, rupees))

/** Starts a new check from the remembered values. The fare always starts empty. */
fun LastCheckInputs.toFormState() = CheckRideFormState(
    platform = platform,
    fareMode = fareMode,
    commissionType = commissionType,
    commission = commissionHundredths?.let(::hundredthsToInput).orEmpty(),
    otherCosts = otherCostsCents?.let(::hundredthsToInput).orEmpty(),
    pickupKm = pickupKm?.let(::decimalToInput).orEmpty(),
    tripKm = tripKm?.let(::decimalToInput).orEmpty(),
    returnKm = returnKm?.let(::decimalToInput).orEmpty(),
)

/** The values to remember for next time. Empty or unparseable fields are stored as empty. */
fun CheckRideFormState.toLastCheckInputs() = LastCheckInputs(
    platform = platform,
    fareMode = fareMode,
    commissionType = commissionType,
    commissionHundredths = parseRupeesToCents(commission),
    otherCostsCents = parseRupeesToCents(otherCosts),
    pickupKm = parseDecimal(pickupKm),
    tripKm = parseDecimal(tripKm),
    returnKm = parseDecimal(returnKm),
)

/** Starts per-check overrides from the vehicle's current rates. */
fun Vehicle.toRateOverrides() = RateOverrides(
    fuelPrice = centsToInput(fuelPricePerLitreCents),
    efficiency = decimalToInput(fuelEfficiencyKmPerLitre),
    maintenance = centsToInput(maintenanceReservePerKmCents),
)
