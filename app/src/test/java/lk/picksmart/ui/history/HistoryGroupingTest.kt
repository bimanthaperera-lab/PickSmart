package lk.picksmart.ui.history

import lk.picksmart.domain.calculator.ProfitCalculator
import lk.picksmart.domain.model.CommissionType
import lk.picksmart.domain.model.FareMode
import lk.picksmart.domain.model.Platform
import lk.picksmart.domain.model.Trip
import lk.picksmart.domain.model.TripRates
import lk.picksmart.domain.model.TripStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class HistoryGroupingTest {

    private val calculator = ProfitCalculator()

    /** G1 completed trip (net 705.00) at the given UTC instant. */
    private fun trip(id: Long, at: String, status: TripStatus = TripStatus.COMPLETED) = Trip(
        id = id,
        vehicleId = 1,
        createdAtMillis = 0,
        updatedAtMillis = 0,
        tripDateTime = Instant.parse(at),
        platform = Platform.PICKME,
        status = status,
        fareCents = 100_000,
        fareMode = FareMode.GROSS_BEFORE_COMMISSION,
        commissionType = CommissionType.AMOUNT,
        commissionHundredths = 20_000,
        pickupKm = 2.0,
        tripKm = if (status == TripStatus.CANCELLED) 0.0 else 8.0,
        returnKm = 0.0,
        otherCostsCents = 0,
        cancellationFeeCents = if (status == TripStatus.CANCELLED) 10_000 else null,
        notes = null,
        rates = TripRates(30_000, 40.0, 200, null),
    )

    // Newest first, as the DAO returns them.
    private val trips = listOf(
        trip(4, "2026-10-21T18:40:00Z", TripStatus.OFFER_CHECK), // 22 Oct 00:10 in Colombo
        trip(3, "2026-10-21T11:12:00Z"), // 21 Oct 16:42
        trip(2, "2026-10-21T08:00:00Z", TripStatus.CANCELLED), // 21 Oct 13:30: 100 - 15 - 4 = 81.00
        trip(1, "2026-10-21T04:00:00Z", TripStatus.OFFER_CHECK), // 21 Oct 09:30
    )

    @Test
    fun `trips group by Colombo date, not UTC date`() {
        val days = groupTripsByDay(trips, HistoryFilter.ALL, calculator)
        assertEquals(listOf(LocalDate.of(2026, 10, 22), LocalDate.of(2026, 10, 21)), days.map { it.date })
        assertEquals(listOf(3L, 2L, 1L), days[1].rows.map { it.trip.id })
    }

    @Test
    fun `day totals leave offers out`() {
        val days = groupTripsByDay(trips, HistoryFilter.ALL, calculator)
        // A day with only an offer has no total at all.
        assertNull(days[0].estimatedProfitCents)
        // 705.00 + 81.00; the 705.00 offer that morning adds nothing.
        assertEquals(78_600L, days[1].estimatedProfitCents)
        // The offers still show their estimate.
        assertEquals(70_500L, days[1].rows.last().estimate!!.netProfitCents)
    }

    @Test
    fun `filters show only that status and total only what is shown`() {
        val offers = groupTripsByDay(trips, HistoryFilter.OFFERS, calculator)
        assertEquals(listOf(4L, 1L), offers.flatMap { it.rows }.map { it.trip.id })
        offers.forEach { assertNull(it.estimatedProfitCents) }

        val cancelled = groupTripsByDay(trips, HistoryFilter.CANCELLED, calculator)
        assertEquals(1, cancelled.size)
        assertEquals(8_100L, cancelled[0].estimatedProfitCents)
    }

    @Test
    fun `no trips gives no days`() {
        assertEquals(emptyList<HistoryDay>(), groupTripsByDay(emptyList(), HistoryFilter.ALL, calculator))
    }

    private val names = mapOf(Platform.PICKME to "පික්මී")

    @Test
    fun `search finds a trip by note, ride app or fare`() {
        val trip = trip(5, "2026-10-21T11:12:00Z").copy(notes = "Airport drop, cash tip", fareCents = 145_050)
        listOf("", "  ", "airport", "CASH tip", "pickme", "පික්මී", "1450", "1,450.5", "Rs. 1450", "rs1450", "pickme 1450")
            .forEach { assertTrue(it, trip.matchesSearch(it, names)) }
        listOf("uber", "450", "1451", "airport uber", "hotel")
            .forEach { assertFalse(it, trip.matchesSearch(it, names)) }
    }

    @Test
    fun `search on a cancelled trip looks at its fee, not the fare`() {
        val cancelled = trip(6, "2026-10-21T08:00:00Z", TripStatus.CANCELLED)
        assertTrue(cancelled.matchesSearch("100", names))
        assertFalse(cancelled.matchesSearch("1000", names))
    }

    @Test
    fun `search narrows the days and their totals`() {
        val days = groupTripsByDay(trips, HistoryFilter.ALL, calculator) { it.matchesSearch("100", names) }
        // Only the cancelled trip's Rs. 100.00 fee; the Rs. 1,000.00 fares are other amounts.
        assertEquals(listOf(2L), days.flatMap { it.rows }.map { it.trip.id })
        assertEquals(8_100L, days.single().estimatedProfitCents)
    }
}
