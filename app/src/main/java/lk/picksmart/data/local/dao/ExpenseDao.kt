package lk.picksmart.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import lk.picksmart.data.local.entity.ExpenseEntity

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
}
