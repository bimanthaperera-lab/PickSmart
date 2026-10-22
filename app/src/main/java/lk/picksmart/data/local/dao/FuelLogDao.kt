package lk.picksmart.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import lk.picksmart.data.local.entity.FuelLogEntity

@Dao
interface FuelLogDao {

    /** Returns the new row id. */
    @Insert
    suspend fun insert(log: FuelLogEntity): Long

    @Update
    suspend fun update(log: FuelLogEntity)

    /** Returns the number of rows removed (0 if it was already gone). */
    @Query("DELETE FROM fuel_logs WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("SELECT * FROM fuel_logs WHERE id = :id")
    suspend fun get(id: Long): FuelLogEntity?

    /** Newest first. */
    @Query("SELECT * FROM fuel_logs ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<FuelLogEntity>>
}
