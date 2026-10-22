package lk.picksmart.domain.model

import java.time.Instant

enum class Platform { UBER, PICKME, COURIER, OWN_DELIVERY, OTHER }

enum class TripStatus {
    /** A checked offer. Never counted as income (CLAUDE.md N1). */
    OFFER_CHECK,
    COMPLETED,
    CANCELLED,
}

/**
 * The rates a trip was calculated with, copied from the vehicle (or the rider's per-check
 * overrides) when the trip was saved. Changing the vehicle later never changes a saved trip.
 */
data class TripRates(
    val fuelPricePerLitreCents: Long,
    val fuelEfficiencyKmPerLitre: Double,
    val maintenanceReservePerKmCents: Long,
    val targetProfitPerKmCents: Long?,
)

fun Vehicle.rates() = TripRates(
    fuelPricePerLitreCents = fuelPricePerLitreCents,
    fuelEfficiencyKmPerLitre = fuelEfficiencyKmPerLitre,
    maintenanceReservePerKmCents = maintenanceReservePerKmCents,
    targetProfitPerKmCents = targetProfitPerKmCents,
)

/**
 * One ride: a checked offer, a completed trip or a cancelled one. Money is in cents; computed
 * values (fuel cost, net profit) are not stored but derived through the calculator.
 *
 * @property commissionHundredths As in `ProfitInput.commissionHundredths`.
 * @property cancellationFeeCents The fee paid for a cancelled trip, or null when there was none.
 */
data class Trip(
    val id: Long,
    val vehicleId: Long,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val tripDateTime: Instant,
    val platform: Platform,
    val status: TripStatus,
    val fareCents: Long,
    val fareMode: FareMode,
    val commissionType: CommissionType,
    val commissionHundredths: Long,
    val pickupKm: Double,
    val tripKm: Double,
    val returnKm: Double,
    val otherCostsCents: Long,
    val cancellationFeeCents: Long?,
    val notes: String?,
    val rates: TripRates,
)

/** What the rider sets when saving or editing a trip, already parsed and validated. */
data class TripInput(
    val tripDateTime: Instant,
    val platform: Platform,
    val status: TripStatus,
    val fareCents: Long,
    val fareMode: FareMode,
    val commissionType: CommissionType,
    val commissionHundredths: Long,
    val pickupKm: Double,
    val tripKm: Double,
    val returnKm: Double,
    val otherCostsCents: Long,
    val cancellationFeeCents: Long?,
    val notes: String?,
    val rates: TripRates,
)
