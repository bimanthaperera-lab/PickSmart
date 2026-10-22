package lk.picksmart.domain.usecase

import lk.picksmart.domain.calculator.CalculationResult
import lk.picksmart.domain.calculator.ProfitCalculator
import lk.picksmart.domain.calculator.ProfitInput
import lk.picksmart.domain.calculator.ProfitResult
import lk.picksmart.domain.model.FareMode
import lk.picksmart.domain.model.Trip
import lk.picksmart.domain.model.TripStatus

/**
 * The calculator input for a saved trip, using the trip's own rate snapshot (CLAUDE.md section 7):
 *
 * - [TripStatus.COMPLETED] and [TripStatus.OFFER_CHECK]: the trip as recorded.
 * - [TripStatus.CANCELLED]: the cancellation fee (0 when none) minus estimated fuel and reserve for
 *   the distance actually driven, which is the pickup plus any return. No commission and no other
 *   costs; the trip itself was never driven.
 */
fun Trip.profitInput(): ProfitInput = when (status) {
    TripStatus.CANCELLED -> ProfitInput(
        fareCents = cancellationFeeCents ?: 0,
        fareMode = FareMode.PAYOUT_AFTER_COMMISSION,
        commissionType = commissionType,
        commissionHundredths = 0,
        pickupKm = pickupKm,
        tripKm = 0.0,
        returnKm = returnKm,
        fuelEfficiencyKmPerLitre = rates.fuelEfficiencyKmPerLitre,
        fuelPricePerLitreCents = rates.fuelPricePerLitreCents,
        maintenanceReservePerKmCents = rates.maintenanceReservePerKmCents,
        otherCostsCents = 0,
        targetProfitPerKmCents = rates.targetProfitPerKmCents,
    )
    TripStatus.COMPLETED, TripStatus.OFFER_CHECK -> ProfitInput(
        fareCents = fareCents,
        fareMode = fareMode,
        commissionType = commissionType,
        commissionHundredths = commissionHundredths,
        pickupKm = pickupKm,
        tripKm = tripKm,
        returnKm = returnKm,
        fuelEfficiencyKmPerLitre = rates.fuelEfficiencyKmPerLitre,
        fuelPricePerLitreCents = rates.fuelPricePerLitreCents,
        maintenanceReservePerKmCents = rates.maintenanceReservePerKmCents,
        otherCostsCents = otherCostsCents,
        targetProfitPerKmCents = rates.targetProfitPerKmCents,
    )
}

/**
 * The estimate for one trip, for display. Offers get an estimate too, but it is never counted
 * (see [countedProfitCents]). Null only if stored values can't be calculated, which saving
 * prevents.
 */
fun ProfitCalculator.estimate(trip: Trip): ProfitResult? =
    (calculate(trip.profitInput()) as? CalculationResult.Success)?.result

/** What this trip adds to estimated profit: null for offers, which are never income (N1). */
fun ProfitCalculator.countedProfitCents(trip: Trip): Long? =
    if (trip.status == TripStatus.OFFER_CHECK) null else estimate(trip)?.netProfitCents

/** Estimated profit of [trips]: completed and cancelled trips only; offers are left out (N1). */
fun ProfitCalculator.estimatedTripProfitCents(trips: List<Trip>): Long =
    trips.sumOf { countedProfitCents(it) ?: 0L }
