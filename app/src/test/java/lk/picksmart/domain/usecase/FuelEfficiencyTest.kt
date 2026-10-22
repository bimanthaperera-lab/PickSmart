package lk.picksmart.domain.usecase

import lk.picksmart.domain.model.FuelLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FuelEfficiencyTest {

    private fun fill(
        id: Long,
        day: Int,
        litres: Double,
        odometer: Double?,
        full: Boolean = true,
        vehicleId: Long = 1,
    ) = FuelLog(
        id = id,
        vehicleId = vehicleId,
        date = LocalDate.of(2026, 10, day),
        litres = litres,
        pricePerLitreCents = 41_400,
        totalCents = 0,
        odometerKm = odometer,
        fullTank = full,
        createdAtMillis = 0,
        updatedAtMillis = 0,
    )

    @Test
    fun twoFullTanksGiveKmOverLitresAddedAfterTheFirst() {
        // The first fill's own litres were used before the reading started, so they don't count.
        val reading = latestEfficiency(listOf(fill(1, 1, 9.0, 1_000.0), fill(2, 5, 8.0, 1_290.0)), 1)!!
        assertEquals(290.0, reading.km, 0.0)
        assertEquals(8.0, reading.litres, 0.0)
        assertEquals(36.3, reading.kmPerLitre, 0.0) // 36.25 rounds half up
        assertEquals(1L, reading.fromLogId)
        assertEquals(2L, reading.toLogId)
    }

    @Test
    fun fillsInBetweenAddTheirLitres() {
        val logs = listOf(
            fill(1, 1, 9.0, 1_000.0),
            fill(2, 2, 3.0, null, full = false),
            fill(3, 3, 2.0, 1_150.0, full = false),
            fill(4, 4, 1.0, null, full = true), // full but no reading: still adds its litres
            fill(5, 6, 4.0, 1_400.0),
        )
        val reading = latestEfficiency(logs, 1)!!
        assertEquals(400.0, reading.km, 0.0)
        assertEquals(10.0, reading.litres, 0.0)
        assertEquals(40.0, reading.kmPerLitre, 0.0)
    }

    @Test
    fun theLatestPairWins() {
        val logs = listOf(fill(1, 1, 9.0, 1_000.0), fill(2, 5, 8.0, 1_320.0), fill(3, 9, 10.0, 1_670.0))
        val reading = latestEfficiency(logs, 1)!!
        assertEquals(35.0, reading.kmPerLitre, 0.0)
        assertEquals(2L, reading.fromLogId)
        assertEquals(3L, reading.toLogId)
    }

    @Test
    fun fillsAreOrderedByDateNotByListOrder() {
        // A fill entered late for an earlier day still sits in its place.
        val logs = listOf(fill(3, 4, 4.0, 1_200.0), fill(1, 1, 9.0, 1_000.0), fill(2, 2, 1.0, null, full = false))
        assertEquals(40.0, latestEfficiency(logs, 1)!!.kmPerLitre, 0.0)
    }

    @Test
    fun anOdometerThatWentBackwardsFallsBackToTheEarlierPair() {
        val logs = listOf(fill(1, 1, 9.0, 1_000.0), fill(2, 5, 8.0, 1_320.0), fill(3, 9, 10.0, 1_300.0))
        val reading = latestEfficiency(logs, 1)!!
        assertEquals(40.0, reading.kmPerLitre, 0.0)
        assertEquals(2L, reading.toLogId)
    }

    @Test
    fun noReadingWithoutTwoFullTanksWithOdometer() {
        assertNull(latestEfficiency(emptyList(), 1))
        assertNull(latestEfficiency(listOf(fill(1, 1, 9.0, 1_000.0)), 1))
        assertNull(latestEfficiency(listOf(fill(1, 1, 9.0, 1_000.0), fill(2, 5, 8.0, 1_290.0, full = false)), 1))
        assertNull(latestEfficiency(listOf(fill(1, 1, 9.0, 1_000.0), fill(2, 5, 8.0, null)), 1))
        assertNull(latestEfficiency(listOf(fill(1, 1, 9.0, 1_000.0), fill(2, 5, 8.0, 1_000.0)), 1))
    }

    @Test
    fun otherVehiclesAreIgnored() {
        val logs = listOf(fill(1, 1, 9.0, 1_000.0), fill(2, 3, 5.0, 2_000.0, vehicleId = 2), fill(3, 5, 8.0, 1_320.0))
        assertEquals(40.0, latestEfficiency(logs, 1)!!.kmPerLitre, 0.0)
        assertNull(latestEfficiency(logs, 2))
    }

    @Test
    fun suggestionNeedsATenPercentDifferenceEitherWay() {
        fun reading(kmPerLitre: Double) = EfficiencyReading(kmPerLitre, 0.0, 0.0, 1, 2)
        assertTrue(suggestsEfficiencyUpdate(reading(36.0), 40.0))
        assertTrue(suggestsEfficiencyUpdate(reading(44.0), 40.0))
        assertFalse(suggestsEfficiencyUpdate(reading(37.0), 40.0))
        assertFalse(suggestsEfficiencyUpdate(reading(40.0), 40.0))
        assertFalse(suggestsEfficiencyUpdate(reading(30.0), 0.0))
    }
}
