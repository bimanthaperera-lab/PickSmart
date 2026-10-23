package lk.picksmart.domain.usecase

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** The spans Reports can show (CLAUDE.md section 5): a calendar day, a Monday–Sunday week, a calendar month. */
enum class ReportPeriod { DAY, WEEK, MONTH }

/** The day, week or month of this kind that contains [date]. */
fun ReportPeriod.rangeContaining(date: LocalDate): DateRange = when (this) {
    ReportPeriod.DAY -> DateRange.day(date)
    ReportPeriod.WEEK -> {
        val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        DateRange(monday, monday.plusDays(6))
    }
    ReportPeriod.MONTH -> DateRange(date.withDayOfMonth(1), date.with(TemporalAdjusters.lastDayOfMonth()))
}

/**
 * The period [steps] periods after (or, when negative, before) the one starting at [start]. A month
 * always moves to the 1st, so 31 January never skips February.
 */
fun ReportPeriod.shift(start: LocalDate, steps: Long): DateRange = when (this) {
    ReportPeriod.DAY -> rangeContaining(start.plusDays(steps))
    ReportPeriod.WEEK -> rangeContaining(start.plusWeeks(steps))
    ReportPeriod.MONTH -> rangeContaining(start.withDayOfMonth(1).plusMonths(steps))
}
