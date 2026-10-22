package lk.picksmart.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import lk.picksmart.data.local.dao.ExpenseDao
import lk.picksmart.data.local.entity.toDomain
import lk.picksmart.data.local.entity.toEntity
import lk.picksmart.domain.model.Expense
import lk.picksmart.domain.model.ExpenseInput
import lk.picksmart.domain.usecase.applyExpenseEdit
import lk.picksmart.domain.usecase.newExpense
import java.time.Clock

class ExpenseRepository(
    private val dao: ExpenseDao,
    private val clock: Clock,
) {
    /** Every expense, newest first. */
    val all: Flow<List<Expense>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun get(id: Long): Expense? = dao.get(id)?.toDomain()

    suspend fun create(vehicleId: Long?, input: ExpenseInput): Long =
        dao.insert(newExpense(input, vehicleId, clock.millis()).toEntity())

    /** Returns false if the expense no longer exists. */
    suspend fun update(id: Long, input: ExpenseInput): Boolean {
        val current = dao.get(id)?.toDomain() ?: return false
        dao.update(applyExpenseEdit(current, input, clock.millis()).toEntity())
        return true
    }

    /** Only called after the rider confirms (CLAUDE.md N7). */
    suspend fun delete(id: Long) {
        dao.delete(id)
    }
}
