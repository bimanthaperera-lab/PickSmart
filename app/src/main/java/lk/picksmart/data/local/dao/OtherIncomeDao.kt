package lk.picksmart.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import lk.picksmart.data.local.entity.OtherIncomeEntity
import lk.picksmart.data.local.entity.SyncKind
import lk.picksmart.data.local.entity.SyncTombstoneEntity

@Dao
interface OtherIncomeDao {

    /** Returns the new row id. */
    @Insert
    suspend fun insert(income: OtherIncomeEntity): Long

    @Update
    suspend fun update(income: OtherIncomeEntity)

    /** Returns the number of rows removed (0 if it was already gone). */
    @Query("DELETE FROM other_income WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("SELECT * FROM other_income WHERE id = :id")
    suspend fun get(id: Long): OtherIncomeEntity?

    /** Newest first. */
    @Query("SELECT * FROM other_income ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<OtherIncomeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTombstone(tombstone: SyncTombstoneEntity)

    /**
     * Deletes the row and remembers it, so cloud backup removes it on the rider's other devices
     * too. Returns the number of rows removed (0 if it was already gone).
     */
    @Transaction
    suspend fun deleteAndRemember(id: Long, nowMillis: Long): Int {
        val row = get(id) ?: return 0
        insertTombstone(SyncTombstoneEntity(SyncKind.OTHER_INCOME, row.syncId, nowMillis))
        return delete(id)
    }
}
