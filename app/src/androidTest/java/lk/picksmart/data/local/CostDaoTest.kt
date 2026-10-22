package lk.picksmart.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import lk.picksmart.data.local.entity.toDomain
import lk.picksmart.data.local.entity.toEntity
import lk.picksmart.domain.model.Expense
import lk.picksmart.domain.model.ExpenseCategory
import lk.picksmart.domain.model.FuelLog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Fuel logs and expenses read back exactly, including their optional fields. */
@RunWith(AndroidJUnit4::class)
class CostDaoTest {

    private lateinit var db: PicksmartDatabase

    private val fill = FuelLog(
        id = 0,
        vehicleId = 1,
        date = LocalDate.of(2026, 10, 20),
        litres = 8.05,
        pricePerLitreCents = 41_400,
        totalCents = 333_270,
        odometerKm = 45_210.5,
        fullTank = true,
        createdAtMillis = 1_000,
        updatedAtMillis = 2_000,
    )

    private val expense = Expense(
        id = 0,
        vehicleId = null,
        date = LocalDate.of(2026, 10, 20),
        category = ExpenseCategory.MAINTENANCE_REPAIR,
        amountCents = 450_000,
        note = "Chain and sprocket",
        odometerKm = 45_000.0,
        createdAtMillis = 1_000,
        updatedAtMillis = 1_000,
    )

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PicksmartDatabase::class.java)
            .build()
    }

    @After
    fun close() = db.close()

    @Test
    fun fuelLogReadsBackAndListsNewestFirst() = runTest {
        val dao = db.fuelLogDao()
        val first = dao.insert(fill.toEntity())
        val second = dao.insert(fill.copy(date = LocalDate.of(2026, 10, 22), odometerKm = null, fullTank = false).toEntity())
        assertEquals(fill.copy(id = first), dao.get(first)!!.toDomain())
        assertNull(dao.get(second)!!.odometerKm)
        assertEquals(listOf(second, first), dao.observeAll().first().map { it.id })
        assertEquals(1, dao.delete(first))
        assertEquals(listOf(second), dao.observeAll().first().map { it.id })
    }

    @Test
    fun expenseReadsBackAndListsNewestFirst() = runTest {
        val dao = db.expenseDao()
        val first = dao.insert(expense.toEntity())
        val second = dao.insert(
            expense.copy(vehicleId = 1, date = LocalDate.of(2026, 10, 21), category = ExpenseCategory.PARKING, note = null, odometerKm = null)
                .toEntity(),
        )
        assertEquals(expense.copy(id = first), dao.get(first)!!.toDomain())
        assertEquals(listOf(second, first), dao.observeAll().first().map { it.id })
        assertEquals(1, dao.delete(second))
        assertNull(dao.get(second))
    }
}
