package lk.picksmart.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import lk.picksmart.data.local.dao.TripDao
import lk.picksmart.data.local.entity.toDomain
import lk.picksmart.data.local.entity.toEntity
import lk.picksmart.domain.model.Trip
import lk.picksmart.domain.model.TripInput
import lk.picksmart.domain.usecase.applyTripEdit
import lk.picksmart.domain.usecase.newTrip
import java.time.Clock

class TripRepository(
    private val dao: TripDao,
    private val clock: Clock,
) {
    /** Every trip, newest first. */
    val all: Flow<List<Trip>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun get(id: Long): Trip? = dao.get(id)?.toDomain()

    /** The trip as it changes; null when it doesn't exist (or was deleted). */
    fun observe(id: Long): Flow<Trip?> = dao.observe(id).map { it?.toDomain() }

    /** Saves a new trip for [vehicleId] and returns its id. */
    suspend fun create(vehicleId: Long, input: TripInput): Long =
        dao.insert(newTrip(input, vehicleId, clock.millis()).toEntity())

    /** Returns false if the trip no longer exists. */
    suspend fun update(id: Long, input: TripInput): Boolean {
        val current = dao.get(id)?.toDomain() ?: return false
        dao.update(applyTripEdit(current, input, clock.millis()).toEntity())
        return true
    }

    /** Only called after the rider confirms (CLAUDE.md N7). Cloud backup removes it everywhere. */
    suspend fun delete(id: Long) {
        dao.deleteAndRemember(id, clock.millis())
    }
}
