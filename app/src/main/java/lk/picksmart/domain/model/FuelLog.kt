package lk.picksmart.domain.model

import java.time.LocalDate

/**
 * One fill-up. This is the only place fuel spending is recorded: `Expense` has no fuel category,
 * so fuel is never counted twice (CLAUDE.md section 7). Money is in cents.
 *
 * @property totalCents What the rider paid. Cash profit uses this, not litres × price.
 * @property fullTank True when the tank was filled to the top. Two full-tank fills with odometer
 *   readings give the vehicle's actual km per litre.
 */
data class FuelLog(
    val id: Long,
    val vehicleId: Long,
    val date: LocalDate,
    val litres: Double,
    val pricePerLitreCents: Long,
    val totalCents: Long,
    val odometerKm: Double?,
    val fullTank: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    /** The same on every device the rider backs up to; see [newSyncId]. Never changes. */
    val syncId: String = newSyncId(),
)

/** The values a rider sets on the fuel form, already parsed and validated. */
data class FuelLogInput(
    val date: LocalDate,
    val litres: Double,
    val pricePerLitreCents: Long,
    val totalCents: Long,
    val odometerKm: Double?,
    val fullTank: Boolean,
)
