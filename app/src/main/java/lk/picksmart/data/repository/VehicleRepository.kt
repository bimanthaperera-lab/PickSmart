package lk.picksmart.data.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import lk.picksmart.data.local.dao.VehicleDao
import lk.picksmart.data.local.entity.toDomain
import lk.picksmart.data.local.entity.toEntity
import lk.picksmart.data.preferences.UserPreferencesRepository
import lk.picksmart.domain.model.FuelPriceList
import lk.picksmart.domain.model.FuelPriceSource
import lk.picksmart.domain.model.FuelType
import lk.picksmart.domain.model.Vehicle
import lk.picksmart.domain.model.VehicleInput
import lk.picksmart.domain.usecase.applyArchive
import lk.picksmart.domain.usecase.applyAutoUpdateSetting
import lk.picksmart.domain.usecase.applyEfficiencyUpdate
import lk.picksmart.domain.usecase.applyFuelPriceEdit
import lk.picksmart.domain.usecase.applyPublishedPrice
import lk.picksmart.domain.usecase.applyTargetUpdate
import lk.picksmart.domain.usecase.applyVehicleEdit
import lk.picksmart.domain.usecase.archivePlan
import lk.picksmart.domain.usecase.keptFuelPriceKey
import lk.picksmart.domain.usecase.newVehicle
import java.time.Clock
import java.time.LocalDate

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

    /** Every vehicle that isn't archived, oldest first. */
    val activeVehicles: Flow<List<Vehicle>> = dao.observeActive().map { rows -> rows.map { it.toDomain() } }

    /** Every vehicle, archived ones included, oldest first; for naming the vehicle on old records. */
    val allVehicles: Flow<List<Vehicle>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    /** Makes [id] the active vehicle: its rates pre-fill Check a Ride and new records. */
    suspend fun setActive(id: Long) = preferences.setDefaultVehicleId(id)

    /**
     * Archives [id] (see [archivePlan]); when it is the active vehicle, [newActiveId] becomes active
     * first, so there is never a moment without one. Its trips and records stay in History and
     * Reports. Returns false if the vehicle no longer exists.
     */
    suspend fun archive(id: Long, newActiveId: Long? = null): Boolean {
        val current = dao.get(id)?.toDomain() ?: return false
        if (newActiveId != null) preferences.setDefaultVehicleId(newActiveId)
        dao.update(applyArchive(current, clock.millis()).toEntity())
        return true
    }

    suspend fun get(id: Long): Vehicle? = dao.get(id)?.toDomain()

    /** Saves a new vehicle and returns its id. Its fuel price is remembered for its fuel type. */
    suspend fun create(input: VehicleInput, isSample: Boolean = false): Long {
        val id = dao.insert(newVehicle(input, isSample, clock.millis()).toEntity())
        if (!isSample) preferences.rememberFuelPrices(mapOf(input.fuelType to input.fuelPricePerLitreCents))
        return id
    }

    /**
     * Saves the rider's edits (see [applyVehicleEdit] for the fuel price rules).
     * Returns false if the vehicle no longer exists.
     *
     * A price typed by hand also counts as keeping it over the published list already saved, so
     * the rider isn't asked about that list right after entering what they pay. A newer list is
     * still offered.
     */
    suspend fun update(id: Long, input: VehicleInput): Boolean {
        val current = dao.get(id)?.toDomain() ?: return false
        val published = preferences.fuelPriceList.first()
        save(current, applyVehicleEdit(current, input, clock.millis(), published), published)
        return true
    }

    /**
     * Sets the fuel type and price from the Fuel price screen (see [applyFuelPriceEdit]); like
     * [update], a typed price counts as keeping it over the saved published list. Returns false
     * if the vehicle no longer exists.
     */
    suspend fun setFuelPrice(id: Long, fuelType: FuelType, priceCents: Long): Boolean {
        val current = dao.get(id)?.toDomain() ?: return false
        val published = preferences.fuelPriceList.first()
        save(current, applyFuelPriceEdit(current, fuelType, priceCents, clock.millis(), published), published)
        return true
    }

    /**
     * Writes [edited]; a new price the rider typed by hand marks [published] as kept. When the
     * fuel type or price changed, both the old and the new price are remembered for their types,
     * so switching back fills the old one in again.
     */
    private suspend fun save(current: Vehicle, edited: Vehicle, published: FuelPriceList?) {
        dao.update(edited.toEntity())
        rememberPriceChange(current, edited)
        val typedByHand = edited.fuelPriceSource == FuelPriceSource.MANUAL &&
            edited.fuelPricePerLitreCents != current.fuelPricePerLitreCents
        if (typedByHand && published != null) preferences.addKeptFuelPrice(keptFuelPriceKey(current.id, published.effectiveFrom))
    }

    /** Sets or clears the profit target per km. Returns false if the vehicle no longer exists. */
    suspend fun setTargetProfitPerKm(id: Long, targetCents: Long?): Boolean {
        val current = dao.get(id)?.toDomain() ?: return false
        dao.update(applyTargetUpdate(current, targetCents, clock.millis()).toEntity())
        return true
    }

    /** Sets a published fuel price. Returns false if the vehicle no longer exists. */
    suspend fun setPublishedFuelPrice(id: Long, priceCents: Long, effectiveFrom: LocalDate): Boolean {
        val current = dao.get(id)?.toDomain() ?: return false
        val edited = applyPublishedPrice(current, priceCents, effectiveFrom, clock.millis())
        dao.update(edited.toEntity())
        rememberPriceChange(current, edited)
        return true
    }

    private suspend fun rememberPriceChange(current: Vehicle, edited: Vehicle) {
        val changed = edited.fuelType != current.fuelType || edited.fuelPricePerLitreCents != current.fuelPricePerLitreCents
        // A sample price is an example, not something the rider pays, so it isn't kept for later.
        if (!changed) return
        val prices = buildMap {
            if (!current.isSample) put(current.fuelType, current.fuelPricePerLitreCents)
            put(edited.fuelType, edited.fuelPricePerLitreCents)
        }
        preferences.rememberFuelPrices(prices)
    }

    /** Turns automatic fuel price updates on or off. Returns false if the vehicle no longer exists. */
    suspend fun setAutoUpdateFuelPrice(id: Long, on: Boolean): Boolean {
        val current = dao.get(id)?.toDomain() ?: return false
        dao.update(applyAutoUpdateSetting(current, on, clock.millis()).toEntity())
        return true
    }

    /** The rider accepted a measured km/L. Returns false if the vehicle no longer exists. */
    suspend fun setFuelEfficiency(id: Long, kmPerLitre: Double): Boolean {
        val current = dao.get(id)?.toDomain() ?: return false
        dao.update(applyEfficiencyUpdate(current, kmPerLitre, clock.millis()).toEntity())
        return true
    }
}
