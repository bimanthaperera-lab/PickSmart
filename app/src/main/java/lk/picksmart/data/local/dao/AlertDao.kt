package lk.picksmart.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import lk.picksmart.data.local.entity.AlertEntity

@Dao
interface AlertDao {

    /** Returns the new row id, or -1 when an alert with the same key already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(alert: AlertEntity): Long

    /** Newest first. */
    @Query("SELECT * FROM alerts ORDER BY created_at DESC, id DESC")
    fun observeAll(): Flow<List<AlertEntity>>

    @Query("SELECT COUNT(*) FROM alerts WHERE read = 0")
    fun observeUnreadCount(): Flow<Int>

    @Query("UPDATE alerts SET read = 1 WHERE read = 0")
    suspend fun markAllRead()

    /** Returns the number of alerts removed. */
    @Query("DELETE FROM alerts WHERE created_at < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long): Int
}
