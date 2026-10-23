package lk.picksmart.domain.usecase

import lk.picksmart.domain.calculator.ProfitCalculator
import lk.picksmart.domain.model.CommissionType
import lk.picksmart.domain.model.FareMode
import lk.picksmart.domain.model.FuelPriceList
import lk.picksmart.domain.model.FuelPriceSource
import lk.picksmart.domain.model.FuelType
import lk.picksmart.domain.model.Platform
import lk.picksmart.domain.model.Trip
import lk.picksmart.domain.model.TripStatus
import lk.picksmart.domain.model.VehicleInput
import lk.picksmart.domain.model.VehicleType
import lk.picksmart.domain.model.rates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class PublishedFuelPriceTest {

    private val oct1: LocalDate = LocalDate.of(2026, 10, 1)

    /** The rider's own Rs. 300.00 price, auto-update off (the default). */
    private val manual = newVehicle(
        VehicleInput(
            name = "Honda Dio",
            type = VehicleType.MOTORCYCLE,
            fuelType = FuelType.PETROL_92,
            fuelPricePerLitreCents = 30_000,
            fuelEfficiencyKmPerLitre = 40.0,
            maintenanceReservePerKmCents = 200,
            targetProfitPerKmCents = null,
        ),
        isSample = false,
        nowMillis = 1_000,
    ).copy(id = 3)

    private val list = FuelPriceList(
        effectiveFrom = oct1,
        fetchedAtMillis = 0,
        pricesCentsPerLitre = mapOf(
            FuelType.PETROL_92 to 41_400,
            FuelType.PETROL_95 to 47_500,
            FuelType.AUTO_DIESEL to 39_200,
            FuelType.SUPER_DIESEL to 52_800,
        ),
    )

    @Test
    fun `with auto-update off a different price is only offered, never applied`() {
        assertEquals(PublishedPriceAction.Offer(41_400, oct1), publishedPriceAction(manual, list, emptySet()))
    }

    @Test
    fun `a manual price is never overwritten when auto-update is off, whatever the list says`() {
        // The prompt is the only thing a manual price ever gets; Apply needs auto-update on.
        listOf(5_000L, 29_999L, 30_001L, 200_000L).forEach { published ->
            val l = list.copy(pricesCentsPerLitre = list.pricesCentsPerLitre + (FuelType.PETROL_92 to published))
            assertTrue(publishedPriceAction(manual, l, emptySet()) !is PublishedPriceAction.Apply)
        }
    }

    @Test
    fun `with auto-update on a different price is applied`() {
        val auto = manual.copy(autoUpdateFuelPrice = true)
        assertEquals(PublishedPriceAction.Apply(41_400, oct1), publishedPriceAction(auto, list, emptySet()))
    }

    @Test
    fun `typing a price by hand turns auto-update off, so the next list is offered, not applied`() {
        val auto = applyPublishedPrice(manual.copy(autoUpdateFuelPrice = true), 41_400, oct1, nowMillis = 2_000)
        val typed = applyFuelPriceEdit(auto, FuelType.PETROL_92, 42_000, nowMillis = 3_000)
        assertEquals(FuelPriceSource.MANUAL, typed.fuelPriceSource)
        assertFalse(typed.autoUpdateFuelPrice)
        assertNull(typed.fuelPriceEffectiveDate)
        val next = list.copy(effectiveFrom = oct1.plusMonths(1), pricesCentsPerLitre = list.pricesCentsPerLitre + (FuelType.PETROL_92 to 43_000))
        assertEquals(PublishedPriceAction.Offer(43_000, oct1.plusMonths(1)), publishedPriceAction(typed, next, emptySet()))
    }

    @Test
    fun `an unchanged fuel price edit leaves the vehicle as it was`() {
        val published = applyPublishedPrice(manual.copy(autoUpdateFuelPrice = true), 41_400, oct1, nowMillis = 2_000)
        assertSame(published, applyFuelPriceEdit(published, FuelType.PETROL_92, 41_400, nowMillis = 3_000))
    }

    @Test
    fun `a fuel price edit keeps the sample label and the other rates`() {
        val sample = manual.copy(isSample = true)
        val edited = applyFuelPriceEdit(sample, FuelType.PETROL_95, 47_000, nowMillis = 3_000)
        assertTrue(edited.isSample)
        assertEquals(FuelType.PETROL_95, edited.fuelType)
        assertEquals(40.0, edited.fuelEfficiencyKmPerLitre, 0.0)
        assertEquals(200L, edited.maintenanceReservePerKmCents)
    }

    @Test
    fun `the same price needs nothing`() {
        val same = manual.copy(fuelPricePerLitreCents = 41_400)
        assertEquals(PublishedPriceAction.None, publishedPriceAction(same, list, emptySet()))
        assertEquals(PublishedPriceAction.None, publishedPriceAction(same.copy(autoUpdateFuelPrice = true), list, emptySet()))
    }

    @Test
    fun `no list means nothing to offer`() {
        assertEquals(PublishedPriceAction.None, publishedPriceAction(manual, null, emptySet()))
    }

    @Test
    fun `keep is remembered for that list only`() {
        val kept = setOf(keptFuelPriceKey(manual.id, oct1))
        assertEquals(PublishedPriceAction.None, publishedPriceAction(manual, list, kept))
        // Another vehicle, or a newer list, is still offered.
        assertTrue(publishedPriceAction(manual.copy(id = 4), list, kept) is PublishedPriceAction.Offer)
        assertTrue(publishedPriceAction(manual, list.copy(effectiveFrom = oct1.plusDays(14)), kept) is PublishedPriceAction.Offer)
    }

    @Test
    fun `the offer follows the vehicle's fuel type`() {
        val diesel = manual.copy(fuelType = FuelType.AUTO_DIESEL)
        assertEquals(PublishedPriceAction.Offer(39_200, oct1), publishedPriceAction(diesel, list, emptySet()))
    }

    @Test
    fun `applying a published price records its source and date and keeps auto-update as set`() {
        val updated = applyPublishedPrice(manual, 41_400, oct1, nowMillis = 9_000)
        assertEquals(41_400L, updated.fuelPricePerLitreCents)
        assertEquals(FuelPriceSource.PUBLISHED, updated.fuelPriceSource)
        assertEquals(oct1, updated.fuelPriceEffectiveDate)
        assertFalse(updated.autoUpdateFuelPrice)
        assertEquals(9_000L, updated.updatedAtMillis)
        assertEquals(manual.fuelEfficiencyKmPerLitre, updated.fuelEfficiencyKmPerLitre, 0.0)
    }

    @Test
    fun `past trip results stay the same after a price update`() {
        val calculator = ProfitCalculator()
        // G1 from CLAUDE.md section 6, saved with the Rs. 300.00 snapshot.
        val trip = Trip(
            id = 1,
            vehicleId = manual.id,
            createdAtMillis = 0,
            updatedAtMillis = 0,
            tripDateTime = Instant.parse("2026-09-20T05:00:00Z"),
            platform = Platform.UBER,
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
            rates = manual.rates(),
        )
        val before = calculator.countedProfitCents(trip)
        val updated = applyPublishedPrice(manual, 41_400, oct1, nowMillis = 9_000)

        assertEquals(70_500L, before)
        assertEquals(before, calculator.countedProfitCents(trip))
        // A new check with the same numbers uses the new price: fuel Rs. 103.50 instead of Rs. 75.00.
        assertEquals(67_650L, calculator.countedProfitCents(trip.copy(rates = updated.rates())))
    }

    @Test
    fun `a list is out of date after 45 days without a fetch`() {
        val fetched = Instant.parse("2026-10-01T00:00:00Z").toEpochMilli()
        val l = list.copy(fetchedAtMillis = fetched)
        assertFalse(isFuelPriceListStale(l, fetched + Duration.ofDays(45).toMillis()))
        assertTrue(isFuelPriceListStale(l, fetched + Duration.ofDays(45).toMillis() + 1))
        assertFalse(isFuelPriceListStale(l, fetched - 1))
    }
}
