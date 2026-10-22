package lk.picksmart.domain.model

import java.time.LocalDate

enum class VehicleType { MOTORCYCLE, THREE_WHEELER, CAR, OTHER }

enum class FuelType { PETROL_92, PETROL_95, AUTO_DIESEL, SUPER_DIESEL }

/** Where the vehicle's fuel price came from (CLAUDE.md section 8a). */
enum class FuelPriceSource {
    /** Typed by the rider. Never overwritten by a published price. */
    MANUAL,

    /** Taken from the published CPC price list. */
    PUBLISHED,
}

/**
 * A rider's vehicle and the rates used to estimate each ride. Money is in cents.
 *
 * @property isSample True while the rates are the onboarding sample values; the UI marks them
 *   "Sample" until the rider saves their own.
 */
data class Vehicle(
    val id: Long,
    val name: String,
    val type: VehicleType,
    val fuelType: FuelType,
    val fuelPricePerLitreCents: Long,
    val fuelPriceSource: FuelPriceSource,
    val fuelPriceEffectiveDate: LocalDate?,
    val autoUpdateFuelPrice: Boolean,
    val fuelEfficiencyKmPerLitre: Double,
    val maintenanceReservePerKmCents: Long,
    val targetProfitPerKmCents: Long?,
    val isSample: Boolean,
    val archived: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

/** The values a rider sets on the vehicle form, already parsed and validated. */
data class VehicleInput(
    val name: String,
    val type: VehicleType,
    val fuelType: FuelType,
    val fuelPricePerLitreCents: Long,
    val fuelEfficiencyKmPerLitre: Double,
    val maintenanceReservePerKmCents: Long,
    val targetProfitPerKmCents: Long?,
)
