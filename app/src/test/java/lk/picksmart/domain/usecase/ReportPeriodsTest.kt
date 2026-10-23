package lk.picksmart.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ReportPeriodsTest {

    private fun d(month: Int, day: Int, year: Int = 2026) = LocalDate.of(year, month, day)

    @Test
    fun weeksRunMondayToSunday() {
        val expected = DateRange(d(10, 19), d(10, 25))
        assertEquals(expected, ReportPeriod.WEEK.rangeContaining(d(10, 19))) // Monday
        assertEquals(expected, ReportPeriod.WEEK.rangeContaining(d(10, 21))) // Wednesday
        assertEquals(expected, ReportPeriod.WEEK.rangeContaining(d(10, 25))) // Sunday
    }

    @Test
    fun aWeekCanCrossAMonthAndAYear() {
        assertEquals(DateRange(d(12, 28), d(1, 3, 2027)), ReportPeriod.WEEK.rangeContaining(d(1, 1, 2027)))
    }

    @Test
    fun monthsAreCalendarMonths() {
        assertEquals(DateRange(d(10, 1), d(10, 31)), ReportPeriod.MONTH.rangeContaining(d(10, 21)))
        assertEquals(DateRange(d(2, 1, 2028), d(2, 29, 2028)), ReportPeriod.MONTH.rangeContaining(d(2, 10, 2028)))
    }

    @Test
    fun aDayIsOneDate() {
        assertEquals(DateRange(d(10, 21), d(10, 21)), ReportPeriod.DAY.rangeContaining(d(10, 21)))
    }

    @Test
    fun shiftingMovesWholePeriods() {
        assertEquals(DateRange(d(10, 20), d(10, 20)), ReportPeriod.DAY.shift(d(10, 21), -1))
        assertEquals(DateRange(d(10, 12), d(10, 18)), ReportPeriod.WEEK.shift(d(10, 19), -1))
        assertEquals(DateRange(d(10, 26), d(11, 1)), ReportPeriod.WEEK.shift(d(10, 19), 1))
        assertEquals(DateRange(d(2, 1), d(2, 28)), ReportPeriod.MONTH.shift(d(1, 31), 1))
        assertEquals(DateRange(d(12, 1, 2025), d(12, 31, 2025)), ReportPeriod.MONTH.shift(d(1, 1), -1))
    }
}
