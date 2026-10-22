package lk.picksmart.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import lk.picksmart.domain.model.FuelLog
import java.time.LocalDate

/** Room row for [FuelLog]. `date` is an ISO `yyyy-MM-dd` Sri Lankan calendar date. */
@Entity(tableName = "fuel_logs", indices = [Index("date"), Index("vehicle_id")])
data class FuelLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "vehicle_id") val vehicleId: Long,
    val date: LocalDate,
    val litres: Double,
    @ColumnInfo(name = "price_per_litre_cents") val pricePerLitreCents: Long,
    @ColumnInfo(name = "total_cents") val totalCents: Long,
    @ColumnInfo(name = "odometer_km") val odometerKm: Double?,
    @ColumnInfo(name = "full_tank") val fullTank: Boolean,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long,
)

fun FuelLogEntity.toDomain() = FuelLog(
    id = id,
    vehicleId = vehicleId,
    date = date,
    litres = litres,
    pricePerLitreCents = pricePerLitreCents,
    totalCents = totalCents,
    odometerKm = odometerKm,
    fullTank = fullTank,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)

fun FuelLog.toEntity() = FuelLogEntity(
    id = id,
    vehicleId = vehicleId,
    date = date,
    litres = litres,
    pricePerLitreCents = pricePerLitreCents,
    totalCents = totalCents,
    odometerKm = odometerKm,
    fullTank = fullTank,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)
