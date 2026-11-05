package lk.picksmart.domain.usecase

import lk.picksmart.domain.calculator.ProfitInput
import lk.picksmart.domain.model.OtherIncome
import lk.picksmart.domain.model.OtherIncomeInput
import lk.picksmart.domain.model.Platform
import lk.picksmart.domain.model.Trip
import lk.picksmart.domain.model.TripInput
import lk.picksmart.domain.model.TripRates
import lk.picksmart.domain.model.TripStatus
import java.time.Instant

/**
 * A trip from a Check a Ride result. The snapshot is the rates the check actually used, including
 * any per-check overrides, so the saved trip shows exactly what the rider saw. The locations are
 * the optional pickup and "Where to" the rider typed.
 */
fun tripInputFromCheck(
    check: ProfitInput,
    platform: Platform,
    status: TripStatus,
    at: Instant,
    pickupLocation: String? = null,
    dropOffLocation: String? = null,
) = TripInput(
    tripDateTime = at,
    platform = platform,
    status = status,
    fareCents = check.fareCents,
    fareMode = check.fareMode,
    commissionType = check.commissionType,
    commissionHundredths = check.commissionHundredths,
    pickupKm = check.pickupKm,
    tripKm = check.tripKm,
    returnKm = check.returnKm,
    otherCostsCents = check.otherCostsCents,
    cancellationFeeCents = null,
    notes = null,
    rates = TripRates(
        fuelPricePerLitreCents = check.fuelPricePerLitreCents,
        fuelEfficiencyKmPerLitre = check.fuelEfficiencyKmPerLitre,
        maintenanceReservePerKmCents = check.maintenanceReservePerKmCents,
        targetProfitPerKmCents = check.targetProfitPerKmCents,
    ),
    pickupLocation = pickupLocation,
    dropOffLocation = dropOffLocation,
)

/** A new trip from the rider's input, with the rates snapshot it was calculated with. */
fun newTrip(input: TripInput, vehicleId: Long, nowMillis: Long): Trip {
    val clean = input.normalized()
    return Trip(
        id = 0,
        vehicleId = vehicleId,
        createdAtMillis = nowMillis,
        updatedAtMillis = nowMillis,
        tripDateTime = clean.tripDateTime,
        platform = clean.platform,
        status = clean.status,
        fareCents = clean.fareCents,
        fareMode = clean.fareMode,
        commissionType = clean.commissionType,
        commissionHundredths = clean.commissionHundredths,
        pickupKm = clean.pickupKm,
        tripKm = clean.tripKm,
        returnKm = clean.returnKm,
        otherCostsCents = clean.otherCostsCents,
        cancellationFeeCents = clean.cancellationFeeCents,
        notes = clean.notes,
        rates = clean.rates,
        pickupLocation = clean.pickupLocation,
        dropOffLocation = clean.dropOffLocation,
    )
}

/**
 * Applies the rider's edits. The id, vehicle and creation time never change. The rates come from
 * the input, which the edit form fills from the trip's own snapshot, so editing a trip never
 * picks up the vehicle's newer rates.
 */
fun applyTripEdit(current: Trip, input: TripInput, nowMillis: Long): Trip =
    newTrip(input, current.vehicleId, nowMillis).copy(
        id = current.id,
        createdAtMillis = current.createdAtMillis,
        syncId = current.syncId,
    )

/**
 * A cancelled trip was never driven, so its trip distance and trip costs are cleared; they would
 * otherwise sit hidden in the record. Blank notes and locations are stored as null. A
 * cancellation fee only belongs to a cancelled trip.
 */
private fun TripInput.normalized(): TripInput = copy(
    tripKm = if (status == TripStatus.CANCELLED) 0.0 else tripKm,
    otherCostsCents = if (status == TripStatus.CANCELLED) 0 else otherCostsCents,
    cancellationFeeCents = if (status == TripStatus.CANCELLED) cancellationFeeCents else null,
    notes = notes?.trim()?.ifEmpty { null },
    pickupLocation = cleanLocation(pickupLocation),
    dropOffLocation = cleanLocation(dropOffLocation),
)

/** The longest pickup or "Where to" location kept, in characters. */
const val LOCATION_MAX_LENGTH = 120

private val WHITESPACE = Regex("""\s+""")

/**
 * A location as stored: spaces and line breaks collapsed to single spaces, trimmed, cut to
 * [LOCATION_MAX_LENGTH], and null when nothing is left.
 */
fun cleanLocation(text: String?): String? =
    text?.replace(WHITESPACE, " ")?.trim()?.take(LOCATION_MAX_LENGTH)?.trim()?.ifEmpty { null }

fun newOtherIncome(input: OtherIncomeInput, nowMillis: Long) = OtherIncome(
    id = 0,
    date = input.date,
    type = input.type,
    platform = input.platform,
    amountCents = input.amountCents,
    note = input.note?.trim()?.ifEmpty { null },
    createdAtMillis = nowMillis,
    updatedAtMillis = nowMillis,
)

fun applyOtherIncomeEdit(current: OtherIncome, input: OtherIncomeInput, nowMillis: Long): OtherIncome =
    newOtherIncome(input, nowMillis).copy(
        id = current.id,
        createdAtMillis = current.createdAtMillis,
        syncId = current.syncId,
    )
