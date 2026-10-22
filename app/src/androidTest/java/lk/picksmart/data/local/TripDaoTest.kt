package lk.picksmart.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import lk.picksmart.data.local.entity.toDomain
import lk.picksmart.data.local.entity.toEntity
import lk.picksmart.domain.model.CommissionType
import lk.picksmart.domain.model.FareMode
import lk.picksmart.domain.model.IncomeType
import lk.picksmart.domain.model.OtherIncome
import lk.picksmart.domain.model.Platform
import lk.picksmart.domain.model.Trip
import lk.picksmart.domain.model.TripRates
import lk.picksmart.domain.model.TripStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class TripDaoTest {

    private lateinit var db: PicksmartDatabase

    private val trip = Trip(
        id = 0,
        vehicleId = 1,
        createdAtMillis = 1_000,
        updatedAtMillis = 1_000,
        tripDateTime = Instant.parse("2026-10-21T11:12:00Z"),
        platform = Platform.PICKME,
        status = TripStatus.CANCELLED,
        fareCents = 100_000,
        fareMode = FareMode.GROSS_BEFORE_COMMISSION,
        commissionType = CommissionType.PERCENT,
        commissionHundredths = 1_250,
        pickupKm = 2.5,
        tripKm = 0.0,
        returnKm = 0.5,
        otherCostsCents = 0,
        cancellationFeeCents = 10_000,
        notes = "Rider didn't come",
        rates = TripRates(30_000, 40.0, 200, 6_000),
    )

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PicksmartDatabase::class.java)
            .build()
    }

    @After
    fun close() = db.close()

    @Test
    fun tripReadsBackExactlyIncludingItsRateSnapshot() = runTest {
        val id = db.tripDao().insert(trip.toEntity())
        assertEquals(trip.copy(id = id), db.tripDao().get(id)!!.toDomain())
    }

    @Test
    fun tripsAreListedNewestFirst() = runTest {
        val dao = db.tripDao()
        val older = dao.insert(trip.copy(tripDateTime = Instant.parse("2026-10-20T10:00:00Z")).toEntity())
        val newer = dao.insert(trip.copy(tripDateTime = Instant.parse("2026-10-21T10:00:00Z")).toEntity())
        assertEquals(listOf(newer, older), dao.observeAll().first().map { it.id })
    }

    @Test
    fun deleteRemovesOnlyThatTrip() = runTest {
        val dao = db.tripDao()
        val keep = dao.insert(trip.toEntity())
        val remove = dao.insert(trip.toEntity())
        assertEquals(1, dao.delete(remove))
        assertNull(dao.get(remove))
        assertEquals(listOf(keep), dao.observeAll().first().map { it.id })
        assertEquals(0, dao.delete(remove))
    }

    @Test
    fun otherIncomeReadsBackAndListsNewestFirst() = runTest {
        val dao = db.otherIncomeDao()
        val income = OtherIncome(0, LocalDate.of(2026, 10, 20), IncomeType.BONUS, Platform.PICKME, 150_000, "Quest", 1, 1)
        val first = dao.insert(income.toEntity())
        val second = dao.insert(income.copy(date = LocalDate.of(2026, 10, 21), platform = null, note = null).toEntity())
        assertEquals(income.copy(id = first), dao.get(first)!!.toDomain())
        assertEquals(listOf(second, first), dao.observeAll().first().map { it.id })
    }
}
