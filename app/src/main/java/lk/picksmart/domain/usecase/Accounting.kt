package lk.picksmart.domain.usecase

import lk.picksmart.domain.calculator.CalculationResult
import lk.picksmart.domain.calculator.ProfitCalculator
import lk.picksmart.domain.model.Expense
import lk.picksmart.domain.model.FuelLog
import lk.picksmart.domain.model.OtherIncome
import lk.picksmart.domain.model.Trip
import lk.picksmart.domain.model.TripStatus
import lk.picksmart.util.ColomboZone
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** Asia/Colombo calendar dates from [start] to [endInclusive], both included. */
data class DateRange(val start: LocalDate, val endInclusive: LocalDate) {
    init {
        require(!endInclusive.isBefore(start)) { "endInclusive is before start" }
    }

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(endInclusive)

    /** Every date in the range, in order. */
    val dates: List<LocalDate> get() = generateSequence(start) { it.plusDays(1) }.takeWhile { it in this }.toList()

    companion object {
        fun day(date: LocalDate) = DateRange(date, date)
    }
}

/** The calendar date a trip belongs to (CLAUDE.md section 5). */
val Trip.colomboDate: LocalDate get() = tripDateTime.atZone(ColomboZone).toLocalDate()

/**
 * The figures for a span of days, in both views of CLAUDE.md section 7. Money is in cents. The two
 * views share their income lines but never mix their costs:
 *
 * **Estimated profit** ([estimatedProfitCents]) = [tripProfitCents] + [otherIncomeCents].
 * [tripProfitCents] is the calculator's net for each completed and cancelled trip, from the trip's
 * own rate snapshot, and equals [tripGrossCents] − [commissionCents] − [estimatedFuelCents] −
 * [reserveCents] − [tripOtherCostsCents] exactly. Recorded expenses and fuel logs are not part of it.
 *
 * **Cash profit** ([cashProfitCents]) = [cashIncomeCents] − [expensesCents] − [fuelLogCents].
 * [cashIncomeCents] = [tripEarningsCents] + [otherIncomeCents]. The maintenance reserve and the
 * estimated trip fuel are left out: the reserve is not cash, and real fuel comes from the fuel log.
 * Each trip's own "other costs" are left out too; costs paid in cash are recorded as expenses, so
 * nothing is entered and counted twice.
 *
 * Offers ([TripStatus.OFFER_CHECK]) are never counted in either view (N1); [offers] only counts them.
 *
 * @property tripGrossCents Completed trips' fares as entered (already after the fee in payout mode),
 *   plus cancellation fees.
 * @property commissionCents Platform fees taken from [tripGrossCents]; 0 for payout-mode fares.
 * @property distanceKm Distance actually driven on counted trips: all of a completed trip, the
 *   pickup and return of a cancelled one.
 * @property tripProfitPerKmCents [tripProfitCents] ÷ [distanceKm], rounded half up; other income is
 *   left out because it isn't earned per km. Null with no distance.
 * @property tripsWithoutDistance Completed trips saved with no distance, whose estimate has no fuel
 *   or reserve in it. Reports say the estimate is incomplete when this is above 0.
 */
data class PeriodSummary(
    val range: DateRange,
    val tripProfitCents: Long,
    val otherIncomeCents: Long,
    val tripGrossCents: Long,
    val commissionCents: Long,
    val estimatedFuelCents: Long,
    val reserveCents: Long,
    val tripOtherCostsCents: Long,
    val expensesCents: Long,
    val fuelLogCents: Long,
    val completedTrips: Int,
    val cancelledTrips: Int,
    val offers: Int,
    val fuelFillUps: Int,
    val distanceKm: Double,
    val tripProfitPerKmCents: Long?,
    val tripsWithoutDistance: Int,
) {
    /** Completed trips' fares after the platform fee, plus cancellation fees. */
    val tripEarningsCents: Long get() = tripGrossCents - commissionCents
    val estimatedProfitCents: Long get() = tripProfitCents + otherIncomeCents
    val cashIncomeCents: Long get() = tripEarningsCents + otherIncomeCents
    val cashProfitCents: Long get() = cashIncomeCents - expensesCents - fuelLogCents

    /** Money earned before fuel and maintenance: the same in both views. */
    val earnedAfterFeesCents: Long get() = cashIncomeCents

    /** Recorded expenses, shown beside the estimate on Home, never taken out of it. */
    val recordedExpensesCents: Long get() = expensesCents

    /** True when nothing has been recorded that counts towards income. */
    val isEmpty: Boolean get() = completedTrips == 0 && cancelledTrips == 0 && otherIncomeCents == 0L

    /** True when no record of any kind falls in the range (offers aside). */
    val hasNoRecords: Boolean get() = isEmpty && expensesCents == 0L && fuelFillUps == 0

    /**
     * True when the cash view has income but no fuel bought in the range, so the cash profit is
     * probably too high (fuel is often bought on another day, or not logged).
     */
    val cashMissingFuel: Boolean get() = !isEmpty && fuelFillUps == 0
}

/**
 * Builds [PeriodSummary] for [range] (CLAUDE.md section 7). This is the one place reports, Home and
 * the daily chart get their totals from, so they always agree. Each amount is counted exactly once.
 */
fun ProfitCalculator.summarizePeriod(
    range: DateRange,
    trips: List<Trip>,
    otherIncome: List<OtherIncome>,
    expenses: List<Expense>,
    fuelLogs: List<FuelLog>,
): PeriodSummary {
    val inRange = trips.filter { it.colomboDate in range }
    var tripProfit = 0L
    var gross = 0L
    var commission = 0L
    var fuel = 0L
    var reserve = 0L
    var tripOther = 0L
    var distance = BigDecimal.ZERO
    var withoutDistance = 0
    for (trip in inRange) {
        if (trip.status == TripStatus.OFFER_CHECK) continue
        val input = trip.profitInput()
        val result = (calculate(input) as? CalculationResult.Success)?.result ?: continue
        tripProfit += result.netProfitCents
        gross += input.fareCents
        commission += result.commissionCents
        fuel += result.fuelCostCents
        reserve += result.maintenanceReserveCents
        tripOther += input.otherCostsCents
        val km = BigDecimal.valueOf(input.pickupKm) + BigDecimal.valueOf(input.tripKm) + BigDecimal.valueOf(input.returnKm)
        distance += km
        if (trip.status == TripStatus.COMPLETED && km.signum() == 0) withoutDistance++
    }
    val fuelInRange = fuelLogs.filter { it.date in range }
    return PeriodSummary(
        range = range,
        tripProfitCents = tripProfit,
        otherIncomeCents = otherIncome.filter { it.date in range }.sumOf { it.amountCents },
        tripGrossCents = gross,
        commissionCents = commission,
        estimatedFuelCents = fuel,
        reserveCents = reserve,
        tripOtherCostsCents = tripOther,
        expensesCents = expenses.filter { it.date in range }.sumOf { it.amountCents },
        fuelLogCents = fuelInRange.sumOf { it.totalCents },
        completedTrips = inRange.count { it.status == TripStatus.COMPLETED },
        cancelledTrips = inRange.count { it.status == TripStatus.CANCELLED },
        offers = inRange.count { it.status == TripStatus.OFFER_CHECK },
        fuelFillUps = fuelInRange.size,
        distanceKm = distance.toDouble(),
        tripProfitPerKmCents = if (distance.signum() > 0) {
            BigDecimal.valueOf(tripProfit).divide(distance, 0, RoundingMode.HALF_UP).longValueExact()
        } else {
            null
        },
        tripsWithoutDistance = withoutDistance,
    )
}

/** One Colombo calendar day for Home: [summarizePeriod] over that day alone. */
fun ProfitCalculator.summarizeDay(
    date: LocalDate,
    trips: List<Trip>,
    otherIncome: List<OtherIncome>,
    expenses: List<Expense>,
    fuelLogs: List<FuelLog> = emptyList(),
): PeriodSummary = summarizePeriod(DateRange.day(date), trips, otherIncome, expenses, fuelLogs)

/**
 * A [PeriodSummary] for each day of [range], for the reports chart. Records are grouped by date
 * once, so a month of many trips stays quick; each day goes through [summarizePeriod], so the bars
 * add up to the period's totals.
 */
fun ProfitCalculator.summarizeEachDay(
    range: DateRange,
    trips: List<Trip>,
    otherIncome: List<OtherIncome>,
    expenses: List<Expense>,
    fuelLogs: List<FuelLog>,
): List<PeriodSummary> {
    val tripsByDate = trips.groupBy { it.colomboDate }
    val incomeByDate = otherIncome.groupBy { it.date }
    val expensesByDate = expenses.groupBy { it.date }
    val fuelByDate = fuelLogs.groupBy { it.date }
    return range.dates.map { date ->
        summarizeDay(
            date,
            tripsByDate[date].orEmpty(),
            incomeByDate[date].orEmpty(),
            expensesByDate[date].orEmpty(),
            fuelByDate[date].orEmpty(),
        )
    }
}
