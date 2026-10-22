package lk.picksmart.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import lk.picksmart.domain.model.Expense
import lk.picksmart.domain.model.ExpenseCategory
import java.time.LocalDate

/** Room row for [Expense]. `date` is an ISO `yyyy-MM-dd` Sri Lankan calendar date. */
@Entity(tableName = "expenses", indices = [Index("date")])
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "vehicle_id") val vehicleId: Long?,
    val date: LocalDate,
    val category: ExpenseCategory,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    val note: String?,
    @ColumnInfo(name = "odometer_km") val odometerKm: Double?,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long,
)

fun ExpenseEntity.toDomain() = Expense(
    id = id,
    vehicleId = vehicleId,
    date = date,
    category = category,
    amountCents = amountCents,
    note = note,
    odometerKm = odometerKm,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)

fun Expense.toEntity() = ExpenseEntity(
    id = id,
    vehicleId = vehicleId,
    date = date,
    category = category,
    amountCents = amountCents,
    note = note,
    odometerKm = odometerKm,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)
