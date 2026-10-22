package lk.picksmart.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import lk.picksmart.data.local.dao.OtherIncomeDao
import lk.picksmart.data.local.entity.toDomain
import lk.picksmart.data.local.entity.toEntity
import lk.picksmart.domain.model.OtherIncome
import lk.picksmart.domain.model.OtherIncomeInput
import lk.picksmart.domain.usecase.applyOtherIncomeEdit
import lk.picksmart.domain.usecase.newOtherIncome
import java.time.Clock

class OtherIncomeRepository(
    private val dao: OtherIncomeDao,
    private val clock: Clock,
) {
    /** Every entry, newest first. */
    val all: Flow<List<OtherIncome>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun get(id: Long): OtherIncome? = dao.get(id)?.toDomain()

    suspend fun create(input: OtherIncomeInput): Long = dao.insert(newOtherIncome(input, clock.millis()).toEntity())

    /** Returns false if the entry no longer exists. */
    suspend fun update(id: Long, input: OtherIncomeInput): Boolean {
        val current = dao.get(id)?.toDomain() ?: return false
        dao.update(applyOtherIncomeEdit(current, input, clock.millis()).toEntity())
        return true
    }

    /** Only called after the rider confirms (CLAUDE.md N7). */
    suspend fun delete(id: Long) {
        dao.delete(id)
    }
}
