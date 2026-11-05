package lk.picksmart.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import lk.picksmart.data.local.entity.ExpenseEntity
import lk.picksmart.data.local.entity.SyncKind
import lk.picksmart.data.local.entity.SyncTombstoneEntity

@Dao
interface ExpenseDao {

    /** Returns the new row id. */
    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Update
    suspend fun update(expense: ExpenseEntity)

    /** Returns the number of rows removed (0 if it was already gone). */
    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun get(id: Long): ExpenseEntity?

    /** Newest first. */
    @Query("SELECT * FROM expenses ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<ExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTombstone(tombstone: SyncTombstoneEntity)

    /**
     * Deletes the row and remembers it, so cloud backup removes it on the rider's other devices
     * too. Returns the number of rows removed (0 if it was already gone).
     */
    @Transaction
    suspend fun deleteAndRemember(id: Long, nowMillis: Long): Int {
        val row = get(id) ?: return 0
        insertTombstone(SyncTombstoneEntity(SyncKind.EXPENSES, row.syncId, nowMillis))
        return delete(id)
    }
}
