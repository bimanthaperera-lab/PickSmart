package lk.picksmart.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import lk.picksmart.data.local.dao.FuelLogDao
import lk.picksmart.data.local.entity.toDomain
import lk.picksmart.data.local.entity.toEntity
import lk.picksmart.domain.model.FuelLog
import lk.picksmart.domain.model.FuelLogInput
import lk.picksmart.domain.usecase.applyFuelLogEdit
import lk.picksmart.domain.usecase.newFuelLog
import java.time.Clock

class FuelLogRepository(
    private val dao: FuelLogDao,
    private val clock: Clock,
) {
    /** Every fill-up, newest first. */
    val all: Flow<List<FuelLog>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun get(id: Long): FuelLog? = dao.get(id)?.toDomain()

    suspend fun create(vehicleId: Long, input: FuelLogInput): Long =
        dao.insert(newFuelLog(input, vehicleId, clock.millis()).toEntity())

    /** Returns false if the fill-up no longer exists. */
    suspend fun update(id: Long, input: FuelLogInput): Boolean {
        val current = dao.get(id)?.toDomain() ?: return false
        dao.update(applyFuelLogEdit(current, input, clock.millis()).toEntity())
        return true
    }

    /** Only called after the rider confirms (CLAUDE.md N7). Cloud backup removes it everywhere. */
    suspend fun delete(id: Long) {
        dao.deleteAndRemember(id, clock.millis())
    }
}
