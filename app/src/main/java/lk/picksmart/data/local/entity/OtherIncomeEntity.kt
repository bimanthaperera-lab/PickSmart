package lk.picksmart.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import lk.picksmart.domain.model.IncomeType
import lk.picksmart.domain.model.OtherIncome
import lk.picksmart.domain.model.Platform
import java.time.LocalDate

/** Room row for [OtherIncome]. `date` is an ISO `yyyy-MM-dd` Sri Lankan calendar date. */
@Entity(tableName = "other_income", indices = [Index("date")])
data class OtherIncomeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val type: IncomeType,
    val platform: Platform?,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    val note: String?,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long,
)

fun OtherIncomeEntity.toDomain() = OtherIncome(
    id = id,
    date = date,
    type = type,
    platform = platform,
    amountCents = amountCents,
    note = note,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)

fun OtherIncome.toEntity() = OtherIncomeEntity(
    id = id,
    date = date,
    type = type,
    platform = platform,
    amountCents = amountCents,
    note = note,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)
