package lk.picksmart.domain.usecase

import lk.picksmart.domain.calculator.ProfitCalculator
import lk.picksmart.domain.model.CommissionType
import lk.picksmart.domain.model.Expense
import lk.picksmart.domain.model.ExpenseCategory
import lk.picksmart.domain.model.FareMode
import lk.picksmart.domain.model.FuelPriceSource
import lk.picksmart.domain.model.FuelType
import lk.picksmart.domain.model.FuelLog
import lk.picksmart.domain.model.IncomeType
import lk.picksmart.domain.model.OtherIncome
import lk.picksmart.domain.model.Platform
import lk.picksmart.domain.model.TripInput
import lk.picksmart.domain.model.TripRates
import lk.picksmart.domain.model.TripStatus
import lk.picksmart.domain.model.Vehicle
import lk.picksmart.domain.model.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Sample data only: the amounts are made up to make the arithmetic easy to check by hand. */
class VehicleScopeTest {

    private val calculator = ProfitCalculator()
    private val day = LocalDate.of(2026, 10, 21)
    private val today = DateRange.day(day)

    /** G1 at 4:42 PM Colombo on vehicle 1: net 705.00, after fee 800.00, fuel 75.00, 10 km. */
    private val bikeTrip = newTrip(
        TripInput(
            tripDateTime = Instant.parse("2026-10-21T11:12:00Z"),
            platform = Platform.PICKME,
            status = TripStatus.COMPLETED,
            fareCents = 100_000,
            fareMode = FareMode.GROSS_BEFORE_COMMISSION,
            commissionType = CommissionType.AMOUNT,
            commissionHundredths = 20_000,
            pickupKm = 2.0,
            tripKm = 8.0,
            returnKm = 0.0,
            otherCostsCents = 0,
            cancellationFeeCents = null,
            notes = null,
            rates = TripRates(30_000, 40.0, 200, null),
        ),
        vehicleId = BIKE,
        nowMillis = 1,
    ).copy(id = 1)

    /** Vehicle 2, Rs. 500 fare with no fee, 20 km at 20 km/L and Rs. 300/L: 500 − 300 − 40 = 160.00. */
    private val tukTrip = bikeTrip.copy(
        id = 2,
        vehicleId = TUK,
        fareCents = 50_000,
        commissionHundredths = 0,
        pickupKm = 0.0,
        tripKm = 20.0,
        rates = TripRates(30_000, 20.0, 200, null),
    )

    private fun expense(cents: Long, vehicleId: Long?) =
        Expense(0, vehicleId, day, ExpenseCategory.PARKING, cents, null, null, 0, 0)

    private fun fuel(cents: Long, vehicleId: Long) = FuelLog(0, vehicleId, day, 1.0, cents, cents, null, true, 0, 0)

    private val records = AccountingRecords(
        trips = listOf(bikeTrip, tukTrip, bikeTrip.copy(id = 3, status = TripStatus.OFFER_CHECK)),
        otherIncome = listOf(OtherIncome(0, day, IncomeType.BONUS, null, 25_000, null, 0, 0)),
        expenses = listOf(expense(10_000, BIKE), expense(7_000, TUK), expense(3_000, null)),
        fuelLogs = listOf(fuel(40_000, BIKE), fuel(60_000, TUK)),
    )

    @Test
    fun allVehiclesIsEveryRecord() {
        assertSame(records, records.forVehicle(VehicleScope.All))
        val s = calculator.summarizePeriod(today, records)
        assertEquals(111_500L, s.estimatedProfitCents) // 705.00 + 160.00 + 250.00 bonus
        assertEquals(20_000L, s.expensesCents)
    }

    @Test
    fun oneVehicleCountsOnlyItsOwnTripsFuelAndExpenses() {
        val bike = calculator.summarizePeriod(today, records.forVehicle(VehicleScope.One(BIKE)))
        assertEquals(70_500L, bike.estimatedProfitCents)
        assertEquals(0L, bike.otherIncomeCents)
        assertEquals(1, bike.completedTrips)
        assertEquals(1, bike.offers) // the offer was checked on the bike; it still counts nowhere
        assertEquals(10.0, bike.distanceKm, 0.0)
        assertEquals(10_000L, bike.expensesCents)
        assertEquals(40_000L, bike.fuelLogCents)
        assertEquals(30_000L, bike.cashProfitCents) // 800 − 100 − 400

        val tuk = calculator.summarizePeriod(today, records.forVehicle(VehicleScope.One(TUK)))
        assertEquals(16_000L, tuk.estimatedProfitCents)
        assertEquals(7_000L, tuk.expensesCents)
        assertEquals(60_000L, tuk.fuelLogCents)
    }

    @Test
    fun vehiclesPlusSharedRecordsAddUpToAllVehiclesWithNothingCountedTwice() {
        val all = calculator.summarizePeriod(today, records)
        val parts = listOf(BIKE, TUK).map { calculator.summarizePeriod(today, records.forVehicle(VehicleScope.One(it))) }
        val sharedIncome = 25_000L
        val sharedExpenses = 3_000L
        assertEquals(all.estimatedProfitCents, parts.sumOf { it.estimatedProfitCents } + sharedIncome)
        assertEquals(all.cashProfitCents, parts.sumOf { it.cashProfitCents } + sharedIncome - sharedExpenses)
        assertEquals(all.expensesCents, parts.sumOf { it.expensesCents } + sharedExpenses)
        assertEquals(all.fuelLogCents, parts.sumOf { it.fuelLogCents })
        assertEquals(all.distanceKm, parts.sumOf { it.distanceKm }, 0.0)
    }

    @Test
    fun sharedRecordsAreFlaggedOnlyInTheirRange() {
        assertTrue(records.hasSharedRecordsIn(today))
        assertFalse(records.hasSharedRecordsIn(DateRange.day(day.plusDays(1))))
        val onlyVehicleRecords = records.copy(otherIncome = emptyList(), expenses = listOf(expense(10_000, BIKE)))
        assertFalse(onlyVehicleRecords.hasSharedRecordsIn(today))
        assertTrue(onlyVehicleRecords.copy(expenses = listOf(expense(1, null))).hasSharedRecordsIn(today))
    }

    @Test
    fun reportsFollowTheActiveVehicleOnlyWhenThereAreSeveral() {
        assertEquals(VehicleScope.All, activeVehicleScope(vehicle(BIKE), listOf(vehicle(BIKE))))
        assertEquals(VehicleScope.All, activeVehicleScope(null, emptyList()))
        assertEquals(VehicleScope.One(TUK), activeVehicleScope(vehicle(TUK), listOf(vehicle(BIKE), vehicle(TUK))))
        // An archived vehicle's trips are its own, so they leave the active vehicle's figures.
        val archived = vehicle(BIKE).copy(archived = true)
        assertEquals(VehicleScope.One(TUK), activeVehicleScope(vehicle(TUK), listOf(archived, vehicle(TUK))))
    }

    @Test
    fun switchingTheActiveVehicleSwitchesTheFigures() {
        val both = listOf(vehicle(BIKE), vehicle(TUK))
        fun profitWith(active: Long) =
            calculator.summarizePeriod(today, records.forVehicle(activeVehicleScope(vehicle(active), both))).estimatedProfitCents
        assertEquals(70_500L, profitWith(BIKE))
        assertEquals(16_000L, profitWith(TUK))
    }

    private fun vehicle(id: Long) = Vehicle(
        id, "Sample $id", VehicleType.MOTORCYCLE, FuelType.PETROL_92, 30_000, FuelPriceSource.MANUAL, null, false,
        40.0, 200, null, isSample = false, archived = false, createdAtMillis = 0, updatedAtMillis = 0,
    )

    @Test
    fun aVehicleWithNoRecordsIsEmpty() {
        val s = calculator.summarizePeriod(today, records.forVehicle(VehicleScope.One(99)))
        assertTrue(s.hasNoRecords)
        assertEquals(0L, s.estimatedProfitCents)
    }

    private companion object {
        const val BIKE = 1L
        const val TUK = 2L
    }
}
