package lk.picksmart.domain.usecase

import lk.picksmart.domain.calculator.ProfitCalculator
import lk.picksmart.domain.calculator.ProfitInput
import lk.picksmart.domain.model.CommissionType
import lk.picksmart.domain.model.FareMode
import lk.picksmart.domain.model.FuelPriceSource
import lk.picksmart.domain.model.FuelType
import lk.picksmart.domain.model.Platform
import lk.picksmart.domain.model.TripInput
import lk.picksmart.domain.model.TripRates
import lk.picksmart.domain.model.TripStatus
import lk.picksmart.domain.model.Vehicle
import lk.picksmart.domain.model.VehicleType
import lk.picksmart.domain.model.rates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class TripProfitTest {

    private val calculator = ProfitCalculator()

    /** G1 rates: Rs. 300/L, 40 km/L, Rs. 2/km. */
    private val g1Rates = TripRates(30_000, 40.0, 200, null)

    /** G1 as a completed trip: fare 1000, AMOUNT 200, 2 + 8 km → net 705.00. */
    private val g1Input = TripInput(
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
        rates = g1Rates,
    )

    private val completed = newTrip(g1Input, vehicleId = 1, nowMillis = 1)

    @Test
    fun `a completed trip counts its full net profit`() {
        assertEquals(70_500L, calculator.countedProfitCents(completed))
    }

    @Test
    fun `offers are excluded from every total, however profitable`() {
        val offer = completed.copy(status = TripStatus.OFFER_CHECK)
        // The offer still has an estimate to show…
        assertEquals(70_500L, calculator.estimate(offer)!!.netProfitCents)
        // …but adds nothing to income.
        assertNull(calculator.countedProfitCents(offer))
        assertEquals(0L, calculator.estimatedTripProfitCents(listOf(offer, offer.copy(fareCents = 10_000_000))))
        assertEquals(70_500L, calculator.estimatedTripProfitCents(listOf(completed, offer)))
    }

    @Test
    fun `a cancelled trip counts its fee minus fuel and reserve for pickup and return only`() {
        // Fee Rs. 100; 2 km pickup + 1 km return at G1 rates: fuel 22.50, reserve 6.00 → 71.50.
        val cancelled = newTrip(
            g1Input.copy(status = TripStatus.CANCELLED, returnKm = 1.0, otherCostsCents = 5_000, cancellationFeeCents = 10_000),
            vehicleId = 1,
            nowMillis = 1,
        )
        assertEquals(7_150L, calculator.countedProfitCents(cancelled))
    }

    @Test
    fun `a cancelled trip without a fee is a loss of the fuel and reserve driven`() {
        val cancelled = newTrip(g1Input.copy(status = TripStatus.CANCELLED), vehicleId = 1, nowMillis = 1)
        // 2 km: fuel 15.00, reserve 4.00.
        assertEquals(-1_900L, calculator.countedProfitCents(cancelled))
    }

    @Test
    fun `cancelling clears trip distance and costs, and other statuses drop a stray fee`() {
        val cancelled = newTrip(
            g1Input.copy(status = TripStatus.CANCELLED, otherCostsCents = 5_000, cancellationFeeCents = 10_000),
            vehicleId = 1,
            nowMillis = 1,
        )
        assertEquals(0.0, cancelled.tripKm, 0.0)
        assertEquals(0L, cancelled.otherCostsCents)
        assertEquals(10_000L, cancelled.cancellationFeeCents)

        assertNull(newTrip(g1Input.copy(cancellationFeeCents = 10_000), 1, 1).cancellationFeeCents)
    }

    @Test
    fun `a trip keeps its rate snapshot when the vehicle changes later`() {
        val vehicle = Vehicle(
            id = 1, name = "Bike", type = VehicleType.MOTORCYCLE, fuelType = FuelType.PETROL_92,
            fuelPricePerLitreCents = 30_000, fuelPriceSource = FuelPriceSource.MANUAL, fuelPriceEffectiveDate = null,
            autoUpdateFuelPrice = false, fuelEfficiencyKmPerLitre = 40.0, maintenanceReservePerKmCents = 200,
            targetProfitPerKmCents = null, isSample = false, archived = false, createdAtMillis = 0, updatedAtMillis = 0,
        )
        val trip = newTrip(g1Input.copy(rates = vehicle.rates()), vehicle.id, 1)
        // The trip holds its own copy of the rates; the vehicle row can change freely afterwards.
        val repriced = vehicle.copy(fuelPricePerLitreCents = 60_000)
        assertEquals(60_000L, repriced.rates().fuelPricePerLitreCents)
        assertEquals(30_000L, trip.rates.fuelPricePerLitreCents)
        assertEquals(70_500L, calculator.countedProfitCents(trip))
    }

    @Test
    fun `editing keeps id, vehicle and creation time, and stamps the update`() {
        val saved = completed.copy(id = 9, createdAtMillis = 100, updatedAtMillis = 100)
        val edited = applyTripEdit(saved, g1Input.copy(fareCents = 120_000, notes = "  "), nowMillis = 500)
        assertEquals(9L, edited.id)
        assertEquals(1L, edited.vehicleId)
        assertEquals(100L, edited.createdAtMillis)
        assertEquals(500L, edited.updatedAtMillis)
        assertEquals(120_000L, edited.fareCents)
        assertNull(edited.notes)
    }

    @Test
    fun `a saved check keeps its inputs and the rates it was checked with`() {
        val check = ProfitInput(
            fareCents = 100_000,
            fareMode = FareMode.GROSS_BEFORE_COMMISSION,
            commissionType = CommissionType.AMOUNT,
            commissionHundredths = 20_000,
            pickupKm = 2.0,
            tripKm = 8.0,
            fuelEfficiencyKmPerLitre = 20.0,
            fuelPricePerLitreCents = 40_000,
            maintenanceReservePerKmCents = 0,
            targetProfitPerKmCents = 6_000,
        )
        val at = Instant.parse("2026-10-21T11:12:00Z")
        val trip = newTrip(tripInputFromCheck(check, Platform.UBER, TripStatus.OFFER_CHECK, at), vehicleId = 3, nowMillis = 1)
        assertEquals(TripRates(40_000, 20.0, 0, 6_000), trip.rates)
        assertEquals(at, trip.tripDateTime)
        assertEquals(TripStatus.OFFER_CHECK, trip.status)
        assertEquals(check, trip.profitInput())
    }
}
