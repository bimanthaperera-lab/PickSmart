package lk.picksmart.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * Remembers a deleted record so cloud backup removes it on the rider's other devices too, instead
 * of a stale copy bringing it back (CLAUDE.md section 14). [kind] is one of [SyncKind].
 */
@Entity(tableName = "sync_tombstones", primaryKeys = ["kind", "sync_id"])
data class SyncTombstoneEntity(
    val kind: String,
    @ColumnInfo(name = "sync_id") val syncId: String,
    @ColumnInfo(name = "deleted_at") val deletedAtMillis: Long,
    @ColumnInfo(name = "sync_pending", defaultValue = "1") val syncPending: Boolean = true,
)

/** A bookkeeping value for cloud backup, such as the rider it last ran for. */
@Entity(tableName = "sync_state", primaryKeys = ["key"])
data class SyncStateEntity(val key: String, val value: String)

/** The kinds of record cloud backup copies. Each is also the table and the cloud collection name. */
object SyncKind {
    const val VEHICLES = "vehicles"
    const val TRIPS = "trips"
    const val OTHER_INCOME = "other_income"
    const val FUEL_LOGS = "fuel_logs"
    const val EXPENSES = "expenses"

    /** Vehicles first: the other records point at them. */
    val ALL = listOf(VEHICLES, TRIPS, OTHER_INCOME, FUEL_LOGS, EXPENSES)
}
