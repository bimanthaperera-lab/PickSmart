package lk.picksmart.domain.usecase

import lk.picksmart.domain.calculator.ProfitCalculator
import lk.picksmart.domain.model.CommissionType
import lk.picksmart.domain.model.Expense
import lk.picksmart.domain.model.ExpenseCategory
import lk.picksmart.domain.model.FareMode
import lk.picksmart.domain.model.IncomeType
import lk.picksmart.domain.model.OtherIncome
import lk.picksmart.domain.model.Platform
import lk.picksmart.domain.model.TripInput
import lk.picksmart.domain.model.TripRates
import lk.picksmart.domain.model.TripStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class DaySummaryTest {

    private val calculator = ProfitCalculator()
    private val day = LocalDate.of(2026, 10, 21)

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
    fun nothingIsCountedTwiceAndOffersNeverCount() {
        val offer = g1.copy(id = 3, status = TripStatus.OFFER_CHECK, fareCents = 10_000_000)
        val s = calculator.summarizeDay(day, listOf(g1, offer), listOf(income(50_000)), listOf(expense(30_000)))
        // Net is exactly the one trip plus the one income entry: the offer adds nothing, and the
        // expense is shown beside the estimate rather than taken out of it.
        assertEquals(70_500L + 50_000L, s.estimatedProfitCents)
        assertEquals(calculator.estimatedTripProfitCents(listOf(g1)), s.tripProfitCents)
        assertEquals(80_000L + 50_000L, s.earnedAfterFeesCents)
        assertEquals(30_000L, s.recordedExpensesCents)
        assertEquals(1, s.completedTrips)
        assertEquals(10.0, s.distanceKm, 0.0)
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
        assertEquals(0L, s.estimatedProfitCents)
        assertNull(s.tripProfitPerKmCents)
        assertEquals(30_000L, s.recordedExpensesCents)
    }
}
