package lk.picksmart.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import lk.picksmart.data.local.entity.SyncKind
import lk.picksmart.data.local.entity.SyncTombstoneEntity
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

    /** Emits null once the trip is deleted. */
    @Query("SELECT * FROM trips WHERE id = :id")
    fun observe(id: Long): Flow<TripEntity?>

    /** Newest first. */
    @Query("SELECT * FROM trips ORDER BY trip_date_time DESC, id DESC")
    fun observeAll(): Flow<List<TripEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTombstone(tombstone: SyncTombstoneEntity)

    /**
     * Deletes the row and remembers it, so cloud backup removes it on the rider's other devices
     * too. Returns the number of rows removed (0 if it was already gone).
     */
    @Transaction
    suspend fun deleteAndRemember(id: Long, nowMillis: Long): Int {
        val row = get(id) ?: return 0
        insertTombstone(SyncTombstoneEntity(SyncKind.TRIPS, row.syncId, nowMillis))
        return delete(id)
    }
}
