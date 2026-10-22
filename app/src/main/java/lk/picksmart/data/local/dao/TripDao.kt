package lk.picksmart.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import lk.picksmart.data.local.entity.TripEntity

@Dao
interface TripDao {

    /** Returns the new row id. */
    @Insert
    suspend fun insert(trip: TripEntity): Long

    @Update
    suspend fun update(trip: TripEntity)

    /** Returns the number of rows removed (0 if it was already gone). */
    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun get(id: Long): TripEntity?

    /** Newest first. */
    @Query("SELECT * FROM trips ORDER BY trip_date_time DESC, id DESC")
    fun observeAll(): Flow<List<TripEntity>>
}
