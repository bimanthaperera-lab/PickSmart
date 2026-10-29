package lk.picksmart.domain.usecase

import lk.picksmart.domain.model.CommissionType
import lk.picksmart.domain.model.FareMode
import lk.picksmart.domain.model.Platform
import lk.picksmart.domain.model.Trip
import lk.picksmart.domain.model.TripRates
import lk.picksmart.domain.model.TripStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** Sample trips only; the places are made up. */
class DistanceRecordsTest {

    private fun trip(id: Long, status: TripStatus, pickup: Double, trip: Double, back: Double = 0.0, at: String = "2026-10-21T11:00:00Z") = Trip(
        id = id,
        vehicleId = 1,
        createdAtMillis = 0,
        updatedAtMillis = 0,
        tripDateTime = Instant.parse(at),
        platform = Platform.UBER,
        status = status,
        fareCents = 100_000,
        fareMode = FareMode.GROSS_BEFORE_COMMISSION,
        commissionType = CommissionType.AMOUNT,
        commissionHundredths = 0,
        pickupKm = pickup,
        tripKm = trip,
        returnKm = back,
        otherCostsCents = 0,
        cancellationFeeCents = null,
        notes = null,
        rates = TripRates(30_000, 40.0, 200, null),
    )

    @Test
    fun `total counts what was driven and never an offer`() {
        val records = distanceRecords(
            listOf(
                trip(1, TripStatus.COMPLETED, 2.0, 8.0, 1.0),
                trip(2, TripStatus.CANCELLED, 3.0, 0.0, 0.5),
                trip(3, TripStatus.OFFER_CHECK, 5.0, 40.0),
            ),
        )
        assertEquals(14.5, records.totalKm, 0.0)
        assertEquals(2, records.drivenTrips)
    }

    @Test
    fun `the longest trip is the longest ride, not the longest pickup`() {
        val far = trip(1, TripStatus.COMPLETED, 9.0, 4.0).copy(pickupLocation = "Sample Road", dropOffLocation = "Example Mawatha")
        val long = trip(2, TripStatus.COMPLETED, 1.0, 12.5)
        val offer = trip(3, TripStatus.OFFER_CHECK, 0.0, 50.0)
        assertEquals(2L, distanceRecords(listOf(far, long, offer)).longestTrip?.id)
    }

    @Test
    fun `a tie goes to the newest trip`() {
        val older = trip(1, TripStatus.COMPLETED, 0.0, 10.0, at = "2026-10-20T08:00:00Z")
        val newer = trip(2, TripStatus.COMPLETED, 0.0, 10.0, at = "2026-10-22T08:00:00Z")
        assertEquals(2L, distanceRecords(listOf(older, newer)).longestTrip?.id)
        assertEquals(2L, distanceRecords(listOf(newer, older)).longestTrip?.id)
    }

    @Test
    fun `no trips, or none with a trip distance, give no longest trip`() {
        assertEquals(DistanceRecords(0.0, 0, null), distanceRecords(emptyList()))
        assertNull(distanceRecords(listOf(trip(1, TripStatus.COMPLETED, 2.0, 0.0))).longestTrip)
    }
}
