package lk.picksmart.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import lk.picksmart.data.local.entity.OtherIncomeEntity

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
}
