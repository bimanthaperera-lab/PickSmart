package lk.picksmart.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import lk.picksmart.domain.model.FuelPriceSource
import lk.picksmart.domain.model.FuelType
import lk.picksmart.domain.model.Vehicle
import lk.picksmart.domain.model.VehicleType
import java.time.LocalDate

/**
 * Room row for [Vehicle]. Enums are stored by name, dates as ISO `yyyy-MM-dd`, money as cents.
 * Renaming a column or enum constant needs a migration (CLAUDE.md N7).
 */
@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: VehicleType,
    @ColumnInfo(name = "fuel_type") val fuelType: FuelType,
    @ColumnInfo(name = "fuel_price_per_litre_cents") val fuelPricePerLitreCents: Long,
    @ColumnInfo(name = "fuel_price_source") val fuelPriceSource: FuelPriceSource,
    @ColumnInfo(name = "fuel_price_effective_date") val fuelPriceEffectiveDate: LocalDate?,
    @ColumnInfo(name = "auto_update_fuel_price", defaultValue = "0") val autoUpdateFuelPrice: Boolean,
    @ColumnInfo(name = "fuel_efficiency_km_per_litre") val fuelEfficiencyKmPerLitre: Double,
    @ColumnInfo(name = "maintenance_reserve_per_km_cents") val maintenanceReservePerKmCents: Long,
    @ColumnInfo(name = "target_profit_per_km_cents") val targetProfitPerKmCents: Long?,
    @ColumnInfo(name = "is_sample", defaultValue = "0") val isSample: Boolean,
    @ColumnInfo(defaultValue = "0") val archived: Boolean,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long,
)

fun VehicleEntity.toDomain() = Vehicle(
    id = id,
    name = name,
    type = type,
    fuelType = fuelType,
    fuelPricePerLitreCents = fuelPricePerLitreCents,
    fuelPriceSource = fuelPriceSource,
    fuelPriceEffectiveDate = fuelPriceEffectiveDate,
    autoUpdateFuelPrice = autoUpdateFuelPrice,
    fuelEfficiencyKmPerLitre = fuelEfficiencyKmPerLitre,
    maintenanceReservePerKmCents = maintenanceReservePerKmCents,
    targetProfitPerKmCents = targetProfitPerKmCents,
    isSample = isSample,
    archived = archived,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)

fun Vehicle.toEntity() = VehicleEntity(
    id = id,
    name = name,
    type = type,
    fuelType = fuelType,
    fuelPricePerLitreCents = fuelPricePerLitreCents,
    fuelPriceSource = fuelPriceSource,
    fuelPriceEffectiveDate = fuelPriceEffectiveDate,
    autoUpdateFuelPrice = autoUpdateFuelPrice,
    fuelEfficiencyKmPerLitre = fuelEfficiencyKmPerLitre,
    maintenanceReservePerKmCents = maintenanceReservePerKmCents,
    targetProfitPerKmCents = targetProfitPerKmCents,
    isSample = isSample,
    archived = archived,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)
