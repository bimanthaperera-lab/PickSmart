package lk.picksmart.domain.usecase

import lk.picksmart.domain.calculator.ProfitCalculator
import lk.picksmart.domain.model.CommissionType
import lk.picksmart.domain.model.Expense
import lk.picksmart.domain.model.ExpenseCategory
import lk.picksmart.domain.model.FareMode
import lk.picksmart.domain.model.FuelLog
import lk.picksmart.domain.model.IncomeType
import lk.picksmart.domain.model.OtherIncome
import lk.picksmart.domain.model.Platform
import lk.picksmart.domain.model.TripInput
import lk.picksmart.domain.model.TripRates
import lk.picksmart.domain.model.TripStatus
import lk.picksmart.util.ColomboZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Sample data only: the amounts are made up to make the arithmetic easy to check by hand. */
class AccountingTest {

    private val calculator = ProfitCalculator()
    private val day = LocalDate.of(2026, 10, 21) // a Wednesday
    private val week = DateRange(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25)) // Mon–Sun

    /** G1 as a completed trip at 4:42 PM Colombo: net 705.00, after fee 800.00, 10 km. */
    private val g1 = newTrip(
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
        vehicleId = 1,
        nowMillis = 1,
    )

    /** Cancelled after a 5 km pickup with a Rs. 150 fee: 150 − 37.50 fuel − 10.00 reserve = 102.50. */
    private val cancelled = g1.copy(id = 2, status = TripStatus.CANCELLED, tripKm = 0.0, pickupKm = 5.0, cancellationFeeCents = 15_000)

    private fun income(cents: Long, date: LocalDate = day) =
        OtherIncome(0, date, IncomeType.BONUS, null, cents, null, 0, 0)

    private fun expense(cents: Long, date: LocalDate = day) =
        Expense(0, 1, date, ExpenseCategory.PARKING, cents, null, null, 0, 0)

    private fun fuel(cents: Long, date: LocalDate = day) =
        FuelLog(0, 1, date, 1.0, cents, cents, null, true, 0, 0)

    private fun onDate(date: LocalDate, id: Long) =
        g1.copy(id = id, tripDateTime = date.atTime(12, 0).atZone(ColomboZone).toInstant())

    // Home (one day)

    @Test
    fun aDayAddsTripsAndOtherIncomeAndShowsExpensesSeparately() {
        val s = calculator.summarizeDay(
            day,
            trips = listOf(g1, cancelled),
            otherIncome = listOf(income(50_000), income(99_900, day.minusDays(1))),
            expenses = listOf(expense(30_000), expense(77_700, day.plusDays(1))),
        )
        assertEquals(80_750L, s.tripProfitCents) // 705.00 + 102.50
        assertEquals(50_000L, s.otherIncomeCents)
        assertEquals(130_750L, s.estimatedProfitCents)
        assertEquals(145_000L, s.earnedAfterFeesCents) // 800 + 150 fee + 500 income
        assertEquals(30_000L, s.recordedExpensesCents)
        assertEquals(1, s.completedTrips)
        assertEquals(1, s.cancelledTrips)
        assertEquals(15.0, s.distanceKm, 0.0)
        assertEquals(5_383L, s.tripProfitPerKmCents) // 807.50 ÷ 15 = 53.833…
        assertFalse(s.isEmpty)
    }

    @Test
    fun tripsBelongToTheirColomboCalendarDay() {
        val lateOnTheTwentieth = g1.copy(id = 4, tripDateTime = Instant.parse("2026-10-20T18:29:00Z")) // 11:59 PM
        val justAfterMidnight = g1.copy(id = 5, tripDateTime = Instant.parse("2026-10-20T18:31:00Z")) // 12:01 AM
        val s = calculator.summarizeDay(day, listOf(lateOnTheTwentieth, justAfterMidnight), emptyList(), emptyList())
        assertEquals(1, s.completedTrips)
        assertEquals(70_500L, s.estimatedProfitCents)
    }

    @Test
    fun anEmptyDayHasNoPerKmFigure() {
        val s = calculator.summarizeDay(day, listOf(g1.copy(status = TripStatus.OFFER_CHECK)), emptyList(), listOf(expense(30_000)))
        assertTrue(s.isEmpty)
        assertFalse(s.hasNoRecords)
        assertEquals(0L, s.estimatedProfitCents)
        assertNull(s.tripProfitPerKmCents)
        assertEquals(30_000L, s.recordedExpensesCents)
        assertEquals(1, s.offers)
    }

    // Both views

    @Test
    fun aHandCalculatedWeekInBothViews() {
        // Mon: G1 (net 705.00, earned 800.00). Wed: G1 in payout mode, Rs. 800 after the fee
        // (net 705.00, earned 800.00), plus the cancelled trip (net 102.50, fee 150.00).
        // Thu: a Rs. 500 bonus. Fri: a Rs. 300 parking expense and Rs. 1,000 of fuel.
        // An offer on Tuesday and records from the weeks either side count nowhere.
        val payout = onDate(day, 10).copy(fareMode = FareMode.PAYOUT_AFTER_COMMISSION, fareCents = 80_000)
        val s = calculator.summarizePeriod(
            week,
            trips = listOf(
                onDate(week.start, 11),
                onDate(week.start.plusDays(1), 12).copy(status = TripStatus.OFFER_CHECK),
                payout,
                cancelled,
                onDate(week.start.minusDays(1), 13),
                onDate(week.endInclusive.plusDays(1), 14),
            ),
            otherIncome = listOf(income(50_000, day.plusDays(1)), income(12_300, week.start.minusDays(1))),
            expenses = listOf(expense(30_000, day.plusDays(2)), expense(45_600, week.endInclusive.plusDays(1))),
            fuelLogs = listOf(fuel(100_000, day.plusDays(2)), fuel(78_900, week.start.minusDays(1))),
        )
        // Estimated: 705.00 + 705.00 + 102.50 + 500.00 = 2,012.50
        assertEquals(151_250L, s.tripProfitCents)
        assertEquals(201_250L, s.estimatedProfitCents)
        // Its parts: earned 800 + 800 + 150 = 1,750.00; fuel 75 + 75 + 37.50; reserve 20 + 20 + 10.
        assertEquals(175_000L, s.tripEarningsCents)
        assertEquals(18_750L, s.estimatedFuelCents)
        assertEquals(5_000L, s.reserveCents)
        // Cash: 1,750.00 + 500.00 − 300.00 − 1,000.00 = 950.00
        assertEquals(225_000L, s.cashIncomeCents)
        assertEquals(95_000L, s.cashProfitCents)
        assertEquals(2, s.completedTrips)
        assertEquals(1, s.cancelledTrips)
        assertEquals(1, s.offers)
        assertEquals(1, s.fuelFillUps)
        assertEquals(25.0, s.distanceKm, 0.0)
        assertFalse(s.cashMissingFuel)
    }

    @Test
    fun theEstimateIsExactlyTheSumOfItsLines() {
        val withCost = g1.copy(id = 20, otherCostsCents = 4_321)
        val s = calculator.summarizePeriod(DateRange.day(day), listOf(g1, withCost, cancelled), emptyList(), emptyList(), emptyList())
        assertEquals(4_321L, s.tripOtherCostsCents)
        assertEquals(
            s.tripProfitCents,
            s.tripEarningsCents - s.estimatedFuelCents - s.reserveCents - s.tripOtherCostsCents,
        )
    }

    @Test
    fun nothingIsCountedTwice() {
        val base = calculator.summarizePeriod(week, listOf(g1), listOf(income(50_000)), listOf(expense(30_000)), listOf(fuel(100_000)))

        // Each record changes each view by its own amount exactly once, and only where it belongs.
        val moreIncome = calculator.summarizePeriod(week, listOf(g1), listOf(income(50_100)), listOf(expense(30_000)), listOf(fuel(100_000)))
        assertEquals(100L, moreIncome.estimatedProfitCents - base.estimatedProfitCents)
        assertEquals(100L, moreIncome.cashProfitCents - base.cashProfitCents)

        val moreExpense = calculator.summarizePeriod(week, listOf(g1), listOf(income(50_000)), listOf(expense(30_100)), listOf(fuel(100_000)))
        assertEquals(0L, moreExpense.estimatedProfitCents - base.estimatedProfitCents)
        assertEquals(-100L, moreExpense.cashProfitCents - base.cashProfitCents)

        val moreFuel = calculator.summarizePeriod(week, listOf(g1), listOf(income(50_000)), listOf(expense(30_000)), listOf(fuel(100_100)))
        assertEquals(0L, moreFuel.estimatedProfitCents - base.estimatedProfitCents)
        assertEquals(-100L, moreFuel.cashProfitCents - base.cashProfitCents)

        // A higher reserve rate lowers only the estimate: the reserve is not cash.
        val moreReserve = calculator.summarizePeriod(
            week, listOf(g1.copy(rates = g1.rates.copy(maintenanceReservePerKmCents = 210))),
            listOf(income(50_000)), listOf(expense(30_000)), listOf(fuel(100_000)),
        )
        assertEquals(-100L, moreReserve.estimatedProfitCents - base.estimatedProfitCents)
        assertEquals(0L, moreReserve.cashProfitCents - base.cashProfitCents)

        // A higher fare after the fee raises both views once.
        val moreFare = calculator.summarizePeriod(
            week, listOf(g1.copy(fareCents = 100_100)),
            listOf(income(50_000)), listOf(expense(30_000)), listOf(fuel(100_000)),
        )
        assertEquals(100L, moreFare.estimatedProfitCents - base.estimatedProfitCents)
        assertEquals(100L, moreFare.cashProfitCents - base.cashProfitCents)
    }

    @Test
    fun offersNeverCountInEitherView() {
        val offer = g1.copy(id = 3, status = TripStatus.OFFER_CHECK, fareCents = 10_000_000)
        val with = calculator.summarizePeriod(week, listOf(g1, offer), listOf(income(50_000)), listOf(expense(30_000)), listOf(fuel(100_000)))
        val without = calculator.summarizePeriod(week, listOf(g1), listOf(income(50_000)), listOf(expense(30_000)), listOf(fuel(100_000)))
        assertEquals(without.copy(offers = 1), with)
    }

    @Test
    fun theDailyBarsAddUpToTheWeek() {
        val trips = listOf(onDate(week.start, 1), cancelled, onDate(week.endInclusive, 2), onDate(week.endInclusive.plusDays(1), 3))
        val income = listOf(income(50_000), income(1_000, week.endInclusive))
        val expenses = listOf(expense(30_000, week.start))
        val fuel = listOf(fuel(100_000, day.plusDays(1)))
        val total = calculator.summarizePeriod(week, trips, income, expenses, fuel)
        val days = calculator.summarizeEachDay(week, trips, income, expenses, fuel)
        assertEquals(week.dates, days.map { it.range.start })
        assertEquals(7, days.size)
        assertEquals(total.estimatedProfitCents, days.sumOf { it.estimatedProfitCents })
        assertEquals(total.cashProfitCents, days.sumOf { it.cashProfitCents })
        assertEquals(total.completedTrips, days.sumOf { it.completedTrips })
    }

    // Incomplete data

    @Test
    fun saysWhenDataIsIncomplete() {
        val noDistance = g1.copy(id = 30, pickupKm = 0.0, tripKm = 0.0)
        val s = calculator.summarizePeriod(week, listOf(g1, noDistance), emptyList(), emptyList(), emptyList())
        assertEquals(1, s.tripsWithoutDistance)
        assertTrue(s.cashMissingFuel)

        val complete = calculator.summarizePeriod(week, listOf(g1), emptyList(), emptyList(), listOf(fuel(100_000)))
        assertEquals(0, complete.tripsWithoutDistance)
        assertFalse(complete.cashMissingFuel)

        val nothing = calculator.summarizePeriod(week, emptyList(), emptyList(), emptyList(), emptyList())
        assertTrue(nothing.hasNoRecords)
        assertFalse(nothing.cashMissingFuel)
    }

    @Test
    fun aCancelledTripWithNoPickupIsNotIncomplete() {
        val s = calculator.summarizePeriod(week, listOf(cancelled.copy(pickupKm = 0.0)), emptyList(), emptyList(), listOf(fuel(1)))
        assertEquals(0, s.tripsWithoutDistance)
        assertEquals(15_000L, s.tripProfitCents)
    }

    @Test
    fun dateRangeIncludesBothEnds() {
        assertTrue(week.start in week)
        assertTrue(week.endInclusive in week)
        assertFalse(week.start.minusDays(1) in week)
        assertFalse(week.endInclusive.plusDays(1) in week)
        assertEquals(1, DateRange.day(day).dates.size)
    }
}
