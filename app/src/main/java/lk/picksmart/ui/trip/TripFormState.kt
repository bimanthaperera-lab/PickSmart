package lk.picksmart.ui.trip

import lk.picksmart.domain.calculator.InputField
import lk.picksmart.domain.calculator.ProfitCalculator
import lk.picksmart.domain.model.FareMode
import lk.picksmart.domain.model.LastCheckInputs
import lk.picksmart.domain.model.Trip
import lk.picksmart.domain.model.TripInput
import lk.picksmart.domain.model.TripRates
import lk.picksmart.domain.model.TripStatus
import lk.picksmart.domain.usecase.OfferToSave
import lk.picksmart.ui.checkride.CheckRideError
import lk.picksmart.ui.checkride.CheckRideFormState
import lk.picksmart.ui.checkride.CheckRideOutcome
import lk.picksmart.ui.checkride.evaluate
import lk.picksmart.ui.checkride.toFormState
import lk.picksmart.util.ColomboZone
import lk.picksmart.util.decimalToInput
import lk.picksmart.util.hundredthsToInput
import lk.picksmart.util.parseRupeesToCents
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * What the rider has typed on Add / Edit trip. The fare, fee and distance fields reuse
 * [CheckRideFormState] (without rate overrides: a trip uses its own rate snapshot), so the parsing
 * and validation rules are the same as on Check a Ride.
 */
data class TripFormState(
    val status: TripStatus,
    val date: LocalDate,
    val time: LocalTime,
    val ride: CheckRideFormState,
    val cancellationFee: String = "",
    val notes: String = "",
)

sealed interface TripFormResult {
    data class Valid(val input: TripInput) : TripFormResult

    /**
     * For a cancelled trip, errors on [InputField.FARE] belong to the cancellation fee field.
     * [resultTooLarge] is set when every field is valid but the result can't be held in cents.
     */
    data class Invalid(
        val errors: Map<InputField, CheckRideError>,
        val resultTooLarge: Boolean = false,
    ) : TripFormResult
}

/**
 * The live estimate for the form. A cancelled trip is its fee (0 when empty) minus fuel and
 * reserve for the pickup and return driven, with no commission (CLAUDE.md section 7), so it is
 * evaluated as a payout of the fee with no trip distance or other costs.
 */
fun TripFormState.evaluate(rates: TripRates, calculator: ProfitCalculator): CheckRideOutcome = when (status) {
    TripStatus.CANCELLED -> ride.copy(
        fare = cancellationFee.ifBlank { "0" },
        fareMode = FareMode.PAYOUT_AFTER_COMMISSION,
        tripKm = "",
        otherCosts = "",
        overrides = null,
    ).evaluate(rates, calculator)
    TripStatus.COMPLETED, TripStatus.OFFER_CHECK -> ride.copy(overrides = null).evaluate(rates, calculator)
}

/** Checks the form for saving. Completed trips and offers need a fare; a cancellation fee is optional. */
fun TripFormState.validate(rates: TripRates, calculator: ProfitCalculator): TripFormResult =
    when (val outcome = evaluate(rates, calculator)) {
        CheckRideOutcome.NeedsFare -> TripFormResult.Invalid(mapOf(InputField.FARE to CheckRideError.REQUIRED))
        is CheckRideOutcome.Invalid -> TripFormResult.Invalid(outcome.errors, outcome.resultTooLarge)
        is CheckRideOutcome.Calculated -> {
            val cancelled = status == TripStatus.CANCELLED
            val check = outcome.input
            TripFormResult.Valid(
                TripInput(
                    tripDateTime = ZonedDateTime.of(date, time, ColomboZone).toInstant(),
                    platform = ride.platform,
                    status = status,
                    // A cancelled trip keeps the fare and fee it was offered with (hidden, never counted).
                    fareCents = if (cancelled) parseRupeesToCents(ride.fare) ?: 0 else check.fareCents,
                    fareMode = ride.fareMode,
                    commissionType = ride.commissionType,
                    commissionHundredths = if (cancelled) parseRupeesToCents(ride.commission) ?: 0 else check.commissionHundredths,
                    pickupKm = check.pickupKm,
                    tripKm = check.tripKm,
                    returnKm = check.returnKm,
                    otherCostsCents = check.otherCostsCents,
                    cancellationFeeCents = if (cancelled) parseRupeesToCents(cancellationFee) else null,
                    notes = notes,
                    rates = rates,
                ),
            )
        }
    }

/** Fills the form from a saved trip, for editing. */
fun Trip.toFormState(): TripFormState {
    val local = tripDateTime.atZone(ColomboZone)
    return TripFormState(
        status = status,
        date = local.toLocalDate(),
        time = local.toLocalTime().withSecond(0).withNano(0),
        ride = CheckRideFormState(
            platform = platform,
            fare = if (fareCents == 0L && status == TripStatus.CANCELLED) "" else hundredthsToInput(fareCents),
            fareMode = fareMode,
            commissionType = commissionType,
            commission = if (commissionHundredths == 0L) "" else hundredthsToInput(commissionHundredths),
            otherCosts = if (otherCostsCents == 0L) "" else hundredthsToInput(otherCostsCents),
            pickupKm = if (pickupKm == 0.0) "" else decimalToInput(pickupKm),
            tripKm = if (tripKm == 0.0) "" else decimalToInput(tripKm),
            returnKm = if (returnKm == 0.0) "" else decimalToInput(returnKm),
        ),
        cancellationFee = cancellationFeeCents?.let(::hundredthsToInput).orEmpty(),
        notes = notes.orEmpty(),
    )
}

/**
 * A blank completed trip at [date] and [time]. The platform and fee setup come from the last
 * check, since they rarely change; distances and costs start empty because this is a different
 * ride.
 */
fun newTripForm(last: LastCheckInputs, date: LocalDate, time: LocalTime) = TripFormState(
    status = TripStatus.COMPLETED,
    date = date,
    time = time.withSecond(0).withNano(0),
    ride = last.copy(otherCostsCents = null, pickupKm = null, tripKm = null, returnKm = null).toFormState(),
)

/**
 * A completed trip filled from an offer the reader showed: its fare, distances and ride app, at
 * the time it was read, with the fee setup of the last check (as on the badge). The rider checks
 * it before saving (N6).
 */
fun offerTripForm(last: LastCheckInputs, offer: OfferToSave): TripFormState {
    val local = offer.readAt.atZone(ColomboZone)
    val blank = newTripForm(last.copy(platform = offer.platform), local.toLocalDate(), local.toLocalTime())
    val reading = offer.reading
    return blank.copy(
        ride = blank.ride.copy(
            fare = hundredthsToInput(reading.fareCents),
            pickupKm = reading.pickupKm?.let(::decimalToInput).orEmpty(),
            tripKm = reading.tripKm?.let(::decimalToInput).orEmpty(),
        ),
    )
}
