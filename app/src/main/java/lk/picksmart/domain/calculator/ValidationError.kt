package lk.picksmart.domain.calculator

/** The [ProfitInput] fields a [ValidationError] can point at, so the UI can mark the right one. */
enum class InputField {
    FARE,
    COMMISSION,
    PICKUP_KM,
    TRIP_KM,
    RETURN_KM,
    FUEL_EFFICIENCY,
    FUEL_PRICE,
    MAINTENANCE_RESERVE,
    OTHER_COSTS,
    TARGET_PROFIT_PER_KM,
    TIP,
}

/** Why an input can't be calculated. Returned, never thrown. */
sealed interface ValidationError {
    /** The field is below zero. */
    data class Negative(val field: InputField) : ValidationError

    /** The field must be above zero (fuel efficiency). */
    data class NotPositive(val field: InputField) : ValidationError

    /** The field is NaN or infinite. */
    data class NotANumber(val field: InputField) : ValidationError

    /** A percentage commission is above 100%. */
    data object PercentAbove100 : ValidationError

    /** The inputs are valid but a result is too large to hold in cents. */
    data object ResultTooLarge : ValidationError
}

/** The outcome of [ProfitCalculator.calculate]. */
sealed interface CalculationResult {
    data class Success(val result: ProfitResult) : CalculationResult

    /** Every problem found, so the UI can show them all at once. Never empty. */
    data class Invalid(val errors: List<ValidationError>) : CalculationResult
}
