package lk.picksmart.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import lk.picksmart.data.local.entity.ExpenseEntity
import lk.picksmart.data.local.entity.FuelLogEntity
import lk.picksmart.data.local.entity.OtherIncomeEntity
import lk.picksmart.data.local.entity.SyncStateEntity
import lk.picksmart.data.local.entity.SyncTombstoneEntity
import lk.picksmart.data.local.entity.TripEntity
import lk.picksmart.data.local.entity.VehicleEntity

/**
 * What cloud backup reads and writes in the local database (CLAUDE.md section 14). Rows written
 * here come from the cloud, so they are saved with `sync_pending = 0`; every other save marks a
 * row pending. Marking a row backed up checks its `updated_at`, so an edit made while it was
 * uploading stays pending.
 */
@Dao
interface SyncDao {

    /** How many rows and deletes are waiting to be backed up. */
    @Query(
        "SELECT (SELECT COUNT(*) FROM vehicles WHERE sync_pending = 1) + " +
            "(SELECT COUNT(*) FROM trips WHERE sync_pending = 1) + " +
            "(SELECT COUNT(*) FROM other_income WHERE sync_pending = 1) + " +
            "(SELECT COUNT(*) FROM fuel_logs WHERE sync_pending = 1) + " +
            "(SELECT COUNT(*) FROM expenses WHERE sync_pending = 1) + " +
            "(SELECT COUNT(*) FROM sync_tombstones WHERE sync_pending = 1)",
    )
    fun observePendingCount(): Flow<Int>

    /** How many records this space holds, backed up or not. */
    @Query(
        "SELECT (SELECT COUNT(*) FROM vehicles) + (SELECT COUNT(*) FROM trips) + " +
            "(SELECT COUNT(*) FROM other_income) + (SELECT COUNT(*) FROM fuel_logs) + " +
            "(SELECT COUNT(*) FROM expenses)",
    )
    suspend fun recordCount(): Int

    // Waiting to be backed up.

    @Query("SELECT * FROM vehicles WHERE sync_pending = 1")
    suspend fun pendingVehicles(): List<VehicleEntity>

    @Query("SELECT * FROM trips WHERE sync_pending = 1")
    suspend fun pendingTrips(): List<TripEntity>

    @Query("SELECT * FROM other_income WHERE sync_pending = 1")
    suspend fun pendingOtherIncome(): List<OtherIncomeEntity>

    @Query("SELECT * FROM fuel_logs WHERE sync_pending = 1")
    suspend fun pendingFuelLogs(): List<FuelLogEntity>

    @Query("SELECT * FROM expenses WHERE sync_pending = 1")
    suspend fun pendingExpenses(): List<ExpenseEntity>

    @Query("SELECT * FROM sync_tombstones WHERE sync_pending = 1")
    suspend fun pendingTombstones(): List<SyncTombstoneEntity>

    // Backed up: only if the row wasn't edited again meanwhile.

    @Query("UPDATE vehicles SET sync_pending = 0 WHERE sync_id = :syncId AND updated_at = :updatedAt")
    suspend fun markVehicleBackedUp(syncId: String, updatedAt: Long)

    @Query("UPDATE trips SET sync_pending = 0 WHERE sync_id = :syncId AND updated_at = :updatedAt")
    suspend fun markTripBackedUp(syncId: String, updatedAt: Long)

    @Query("UPDATE other_income SET sync_pending = 0 WHERE sync_id = :syncId AND updated_at = :updatedAt")
    suspend fun markOtherIncomeBackedUp(syncId: String, updatedAt: Long)

    @Query("UPDATE fuel_logs SET sync_pending = 0 WHERE sync_id = :syncId AND updated_at = :updatedAt")
    suspend fun markFuelLogBackedUp(syncId: String, updatedAt: Long)

    @Query("UPDATE expenses SET sync_pending = 0 WHERE sync_id = :syncId AND updated_at = :updatedAt")
    suspend fun markExpenseBackedUp(syncId: String, updatedAt: Long)

    @Query("UPDATE sync_tombstones SET sync_pending = 0 WHERE kind = :kind AND sync_id = :syncId AND deleted_at = :deletedAt")
    suspend fun markTombstoneBackedUp(kind: String, syncId: String, deletedAt: Long)

    // Looked up by the id shared across devices.

    @Query("SELECT * FROM vehicles WHERE sync_id = :syncId")
    suspend fun vehicle(syncId: String): VehicleEntity?

    @Query("SELECT * FROM trips WHERE sync_id = :syncId")
    suspend fun trip(syncId: String): TripEntity?

    @Query("SELECT * FROM other_income WHERE sync_id = :syncId")
    suspend fun otherIncome(syncId: String): OtherIncomeEntity?

    @Query("SELECT * FROM fuel_logs WHERE sync_id = :syncId")
    suspend fun fuelLog(syncId: String): FuelLogEntity?

    @Query("SELECT * FROM expenses WHERE sync_id = :syncId")
    suspend fun expense(syncId: String): ExpenseEntity?

    @Query("SELECT * FROM sync_tombstones WHERE kind = :kind AND sync_id = :syncId")
    suspend fun tombstone(kind: String, syncId: String): SyncTombstoneEntity?

    @Query("SELECT * FROM vehicles")
    suspend fun allVehicles(): List<VehicleEntity>

    // Written from the cloud.

    @Insert
    suspend fun insertVehicle(vehicle: VehicleEntity): Long

    @Update
    suspend fun updateVehicle(vehicle: VehicleEntity)

    @Insert
    suspend fun insertTrip(trip: TripEntity): Long

    @Update
    suspend fun updateTrip(trip: TripEntity)

    @Insert
    suspend fun insertOtherIncome(income: OtherIncomeEntity): Long

    @Update
    suspend fun updateOtherIncome(income: OtherIncomeEntity)

    @Insert
    suspend fun insertFuelLog(log: FuelLogEntity): Long

    @Update
    suspend fun updateFuelLog(log: FuelLogEntity)

    @Insert
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    /** Deleted on another device. No tombstone: the cloud already has the delete. */
    @Query("DELETE FROM trips WHERE sync_id = :syncId")
    suspend fun deleteTrip(syncId: String)

    @Query("DELETE FROM other_income WHERE sync_id = :syncId")
    suspend fun deleteOtherIncome(syncId: String)

    @Query("DELETE FROM fuel_logs WHERE sync_id = :syncId")
    suspend fun deleteFuelLog(syncId: String)

    @Query("DELETE FROM expenses WHERE sync_id = :syncId")
    suspend fun deleteExpense(syncId: String)

    /** A record another device saved again after this one deleted it: the newer save wins. */
    @Query("DELETE FROM sync_tombstones WHERE kind = :kind AND sync_id = :syncId")
    suspend fun deleteTombstone(kind: String, syncId: String)

    // Bookkeeping.

    @Query("SELECT value FROM sync_state WHERE `key` = :key")
    suspend fun state(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setState(state: SyncStateEntity)

    @Query("DELETE FROM sync_state")
    suspend fun clearState()

    @Query("UPDATE vehicles SET sync_pending = 1")
    suspend fun markVehiclesPending()

    @Query("UPDATE trips SET sync_pending = 1")
    suspend fun markTripsPending()

    @Query("UPDATE other_income SET sync_pending = 1")
    suspend fun markOtherIncomePending()

    @Query("UPDATE fuel_logs SET sync_pending = 1")
    suspend fun markFuelLogsPending()

    @Query("UPDATE expenses SET sync_pending = 1")
    suspend fun markExpensesPending()

    @Query("UPDATE sync_tombstones SET sync_pending = 1")
    suspend fun markTombstonesPending()

    /**
     * A different rider signed in on this phone: everything here is backed up to their account
     * from scratch, and what was downloaded for the last rider is forgotten.
     */
    @Transaction
    suspend fun startOverFor(uid: String) {
        clearState()
        markVehiclesPending()
        markTripsPending()
        markOtherIncomePending()
        markFuelLogsPending()
        markExpensesPending()
        markTombstonesPending()
        setState(SyncStateEntity(OWNER_KEY, uid))
    }

    companion object {
        /** The rider this phone's records were last backed up for. */
        const val OWNER_KEY = "owner_uid"
    }
}
