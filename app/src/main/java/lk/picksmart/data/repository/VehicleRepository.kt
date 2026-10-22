package lk.picksmart.data.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import lk.picksmart.data.local.dao.VehicleDao
import lk.picksmart.data.local.entity.toDomain
import lk.picksmart.data.local.entity.toEntity
import lk.picksmart.data.preferences.UserPreferencesRepository
import lk.picksmart.domain.model.Vehicle
import lk.picksmart.domain.model.VehicleInput
import lk.picksmart.domain.usecase.applyEfficiencyUpdate
import lk.picksmart.domain.usecase.applyVehicleEdit
import lk.picksmart.domain.usecase.newVehicle
import java.time.Clock

class VehicleRepository(
    private val dao: VehicleDao,
    private val preferences: UserPreferencesRepository,
    private val clock: Clock,
) {
    /** The default vehicle, or null before setup (or if its row is missing). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val defaultVehicle: Flow<Vehicle?> = preferences.defaultVehicleId.flatMapLatest { id ->
        if (id == null) flowOf(null) else dao.observe(id).map { it?.toDomain() }
    }

    fun observe(id: Long): Flow<Vehicle?> = dao.observe(id).map { it?.toDomain() }

    suspend fun get(id: Long): Vehicle? = dao.get(id)?.toDomain()

    /** Saves a new vehicle and returns its id. */
    suspend fun create(input: VehicleInput, isSample: Boolean = false): Long =
        dao.insert(newVehicle(input, isSample, clock.millis()).toEntity())

    /**
     * Saves the rider's edits (see [applyVehicleEdit] for the fuel price rules).
     * Returns false if the vehicle no longer exists.
     */
    suspend fun update(id: Long, input: VehicleInput): Boolean {
        val current = dao.get(id)?.toDomain() ?: return false
        dao.update(applyVehicleEdit(current, input, clock.millis()).toEntity())
        return true
    }

    /** The rider accepted a measured km/L. Returns false if the vehicle no longer exists. */
    suspend fun setFuelEfficiency(id: Long, kmPerLitre: Double): Boolean {
        val current = dao.get(id)?.toDomain() ?: return false
        dao.update(applyEfficiencyUpdate(current, kmPerLitre, clock.millis()).toEntity())
        return true
    }
}
