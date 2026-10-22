package lk.picksmart.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import lk.picksmart.domain.model.CommissionType
import lk.picksmart.domain.model.FareMode
import lk.picksmart.domain.model.Platform
import lk.picksmart.domain.model.Trip
import lk.picksmart.domain.model.TripRates
import lk.picksmart.domain.model.TripStatus
import java.time.Instant

/**
 * Room row for [Trip]. The `*_snapshot` columns hold the rates used when the trip was saved, so
 * editing the vehicle never changes a past trip (CLAUDE.md section 8). `trip_date_time` is UTC
 * epoch millis; the day it belongs to is its Asia/Colombo date.
 *
 * There is no foreign key to `vehicles`: vehicles are archived, never deleted, and a later cloud
 * restore may write trips before their vehicle.
 */
@Entity(
    tableName = "trips",
    indices = [Index("trip_date_time"), Index("vehicle_id")],
)
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "vehicle_id") val vehicleId: Long,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long,
    @ColumnInfo(name = "trip_date_time") val tripDateTimeMillis: Long,
    val platform: Platform,
    val status: TripStatus,
    @ColumnInfo(name = "fare_cents") val fareCents: Long,
    @ColumnInfo(name = "fare_mode") val fareMode: FareMode,
    @ColumnInfo(name = "commission_type") val commissionType: CommissionType,
    @ColumnInfo(name = "commission_hundredths") val commissionHundredths: Long,
    @ColumnInfo(name = "pickup_km") val pickupKm: Double,
    @ColumnInfo(name = "trip_km") val tripKm: Double,
    @ColumnInfo(name = "return_km") val returnKm: Double,
    @ColumnInfo(name = "other_costs_cents") val otherCostsCents: Long,
    @ColumnInfo(name = "cancellation_fee_cents") val cancellationFeeCents: Long?,
    val notes: String?,
    @ColumnInfo(name = "fuel_price_per_litre_cents_snapshot") val fuelPricePerLitreCentsSnapshot: Long,
    @ColumnInfo(name = "fuel_efficiency_snapshot") val fuelEfficiencySnapshot: Double,
    @ColumnInfo(name = "maintenance_reserve_per_km_cents_snapshot") val maintenanceReservePerKmCentsSnapshot: Long,
    @ColumnInfo(name = "target_profit_per_km_cents_snapshot") val targetProfitPerKmCentsSnapshot: Long?,
)

fun TripEntity.toDomain() = Trip(
    id = id,
    vehicleId = vehicleId,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    tripDateTime = Instant.ofEpochMilli(tripDateTimeMillis),
    platform = platform,
    status = status,
    fareCents = fareCents,
    fareMode = fareMode,
    commissionType = commissionType,
    commissionHundredths = commissionHundredths,
    pickupKm = pickupKm,
    tripKm = tripKm,
    returnKm = returnKm,
    otherCostsCents = otherCostsCents,
    cancellationFeeCents = cancellationFeeCents,
    notes = notes,
    rates = TripRates(
        fuelPricePerLitreCents = fuelPricePerLitreCentsSnapshot,
        fuelEfficiencyKmPerLitre = fuelEfficiencySnapshot,
        maintenanceReservePerKmCents = maintenanceReservePerKmCentsSnapshot,
        targetProfitPerKmCents = targetProfitPerKmCentsSnapshot,
    ),
)

fun Trip.toEntity() = TripEntity(
    id = id,
    vehicleId = vehicleId,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    tripDateTimeMillis = tripDateTime.toEpochMilli(),
    platform = platform,
    status = status,
    fareCents = fareCents,
    fareMode = fareMode,
    commissionType = commissionType,
    commissionHundredths = commissionHundredths,
    pickupKm = pickupKm,
    tripKm = tripKm,
    returnKm = returnKm,
    otherCostsCents = otherCostsCents,
    cancellationFeeCents = cancellationFeeCents,
    notes = notes,
    fuelPricePerLitreCentsSnapshot = rates.fuelPricePerLitreCents,
    fuelEfficiencySnapshot = rates.fuelEfficiencyKmPerLitre,
    maintenanceReservePerKmCentsSnapshot = rates.maintenanceReservePerKmCents,
    targetProfitPerKmCentsSnapshot = rates.targetProfitPerKmCents,
)
