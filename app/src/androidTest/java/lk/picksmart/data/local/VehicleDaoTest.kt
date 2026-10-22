package lk.picksmart.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import lk.picksmart.data.local.entity.toEntity
import lk.picksmart.domain.model.FuelPriceSource
import lk.picksmart.domain.model.FuelType
import lk.picksmart.domain.model.VehicleInput
import lk.picksmart.domain.model.VehicleType
import lk.picksmart.domain.usecase.newVehicle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class VehicleDaoTest {

    private lateinit var db: PicksmartDatabase

    private val input = VehicleInput(
        name = "Honda Dio",
        type = VehicleType.THREE_WHEELER,
        fuelType = FuelType.AUTO_DIESEL,
        fuelPricePerLitreCents = 39_200,
        fuelEfficiencyKmPerLitre = 37.5,
        maintenanceReservePerKmCents = 175,
        targetProfitPerKmCents = 6_000,
    )

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PicksmartDatabase::class.java)
            .build()
    }

    @After
    fun close() = db.close()

    @Test
    fun insertedVehicleReadsBackExactly() = runTest {
        val vehicle = newVehicle(input, isSample = true, nowMillis = 1_000).copy(
            fuelPriceSource = FuelPriceSource.PUBLISHED,
            fuelPriceEffectiveDate = LocalDate.of(2026, 10, 1),
        )
        val id = db.vehicleDao().insert(vehicle.toEntity())

        assertEquals(vehicle.copy(id = id).toEntity(), db.vehicleDao().get(id))
        assertEquals(vehicle.copy(id = id).toEntity(), db.vehicleDao().observe(id).first())
    }

    @Test
    fun updateReplacesTheRow() = runTest {
        val id = db.vehicleDao().insert(newVehicle(input, isSample = false, nowMillis = 1).toEntity())
        val updated = db.vehicleDao().get(id)!!.copy(name = "Bajaj RE", fuelEfficiencyKmPerLitre = 30.0, updatedAtMillis = 2)
        db.vehicleDao().update(updated)
        assertEquals(updated, db.vehicleDao().get(id))
    }

    @Test
    fun archivedVehiclesAreLeftOutOfTheActiveList() = runTest {
        val dao = db.vehicleDao()
        val first = dao.insert(newVehicle(input, false, 1).toEntity())
        val second = dao.insert(newVehicle(input.copy(name = "Old bike"), false, 2).toEntity())
        dao.update(dao.get(second)!!.copy(archived = true))
        assertEquals(listOf(first), dao.observeActive().first().map { it.id })
    }

    @Test
    fun missingVehicleIsNull() = runTest {
        assertNull(db.vehicleDao().get(99))
    }
}
