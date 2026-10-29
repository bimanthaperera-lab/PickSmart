package lk.picksmart.ui.trip

import lk.picksmart.domain.calculator.InputField
import lk.picksmart.domain.calculator.ProfitCalculator
import lk.picksmart.domain.model.CommissionType
import lk.picksmart.domain.model.LastCheckInputs
import lk.picksmart.domain.model.Platform
import lk.picksmart.domain.model.TripInput
import lk.picksmart.domain.model.TripRates
import lk.picksmart.domain.model.TripStatus
import lk.picksmart.domain.usecase.OfferReading
import lk.picksmart.domain.usecase.OfferToSave
import lk.picksmart.domain.usecase.countedProfitCents
import lk.picksmart.domain.usecase.newTrip
import lk.picksmart.ui.checkride.CheckRideError
import lk.picksmart.ui.checkride.CheckRideFormState
import lk.picksmart.ui.checkride.CheckRideOutcome
import lk.picksmart.ui.navigation.EditTripRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class TripFormStateTest {

    private val calculator = ProfitCalculator()
    private val g1Rates = TripRates(30_000, 40.0, 200, null)

    /** G1 typed as a completed trip on 21 Oct at 4:42 PM Colombo time. */
    private val g1 = TripFormState(
        status = TripStatus.COMPLETED,
        date = LocalDate.of(2026, 10, 21),
        time = LocalTime.of(16, 42),
        ride = CheckRideFormState(
            platform = Platform.PICKME,
            fare = "1000",
            commissionType = CommissionType.AMOUNT,
            commission = "200",
            pickupKm = "2",
            tripKm = "8",
        ),
    )

    private fun valid(form: TripFormState): TripInput =
        (form.validate(g1Rates, calculator) as? TripFormResult.Valid)?.input
            ?: throw AssertionError("Expected valid, got ${form.validate(g1Rates, calculator)}")

    @Test
    fun `a completed trip saves the G1 values in Colombo time`() {
        val input = valid(g1)
        assertEquals(Instant.parse("2026-10-21T11:12:00Z"), input.tripDateTime)
        assertEquals(Platform.PICKME, input.platform)
        assertEquals(100_000L, input.fareCents)
        assertEquals(20_000L, input.commissionHundredths)
        assertEquals(g1Rates, input.rates)
        assertNull(input.cancellationFeeCents)
        assertEquals(70_500L, calculator.countedProfitCents(newTrip(input, 1, 1)))
    }

    @Test
    fun `a completed trip needs a fare`() {
        val result = g1.copy(ride = g1.ride.copy(fare = "")).validate(g1Rates, calculator)
        assertEquals(TripFormResult.Invalid(mapOf(InputField.FARE to CheckRideError.REQUIRED)), result)
    }

    @Test
    fun `a cancelled trip shows and saves fee minus fuel and reserve for pickup and return`() {
        // Fee Rs. 100; 2 km pickup + 1 km return: fuel 22.50, reserve 6.00 → 71.50.
        val form = g1.copy(status = TripStatus.CANCELLED, cancellationFee = "100", ride = g1.ride.copy(returnKm = "1"))
        val shown = form.evaluate(g1Rates, calculator) as CheckRideOutcome.Calculated
        assertEquals(7_150L, shown.result.netProfitCents)

        val input = valid(form)
        assertEquals(10_000L, input.cancellationFeeCents)
        assertEquals(7_150L, calculator.countedProfitCents(newTrip(input, 1, 1)))
    }

    @Test
    fun `a cancelled trip needs no fare and no fee`() {
        val form = g1.copy(status = TripStatus.CANCELLED, ride = g1.ride.copy(fare = ""))
        val input = valid(form)
        assertNull(input.cancellationFeeCents)
        assertEquals(0L, input.fareCents)
        // 2 km driven for nothing: -(15.00 + 4.00).
        assertEquals(-1_900L, calculator.countedProfitCents(newTrip(input, 1, 1)))
    }

    @Test
    fun `an over-100 percent fee hidden in a cancelled trip doesn't block saving`() {
        val form = g1.copy(
            status = TripStatus.CANCELLED,
            ride = g1.ride.copy(commissionType = CommissionType.PERCENT, commission = "150"),
        )
        assertTrue(form.validate(g1Rates, calculator) is TripFormResult.Valid)
    }

    @Test
    fun `a saved trip fills the form back exactly`() {
        val trip = newTrip(valid(g1.copy(notes = "Airport run")), vehicleId = 1, nowMillis = 1)
        val form = trip.toFormState()
        assertEquals(g1.copy(notes = "Airport run"), form)
        assertEquals(trip, newTrip(valid(form), 1, 1).copy(syncId = trip.syncId))
    }

    @Test
    fun `a saved cancelled trip fills the form back`() {
        val form = g1.copy(status = TripStatus.CANCELLED, cancellationFee = "100", ride = g1.ride.copy(returnKm = "1"))
        val trip = newTrip(valid(form), vehicleId = 1, nowMillis = 1)
        val back = trip.toFormState()
        assertEquals("100", back.cancellationFee)
        assertEquals("", back.ride.tripKm)
        assertEquals("1", back.ride.returnKm)
        assertEquals(calculator.countedProfitCents(trip), calculator.countedProfitCents(newTrip(valid(back), 1, 1)))
    }

    @Test
    fun `a new trip takes the platform and fee from the last check but not its distances`() {
        val last = LastCheckInputs(
            platform = Platform.OWN_DELIVERY,
            commissionType = CommissionType.AMOUNT,
            commissionHundredths = 5_000,
            otherCostsCents = 1_000,
            pickupKm = 3.0,
            tripKm = 9.0,
        )
        val form = newTripForm(last, LocalDate.of(2026, 10, 21), LocalTime.of(9, 5, 33))
        assertEquals(TripStatus.COMPLETED, form.status)
        assertEquals(LocalTime.of(9, 5), form.time)
        assertEquals(Platform.OWN_DELIVERY, form.ride.platform)
        assertEquals("50", form.ride.commission)
        assertEquals("", form.ride.fare)
        assertEquals("", form.ride.pickupKm)
        assertEquals("", form.ride.tripKm)
        assertEquals("", form.ride.otherCosts)
    }

    /** Sample offer (made-up numbers): G1's fare and distances, read at 4:42:20 PM Colombo time. */
    private val g1Offer = OfferToSave(OfferReading(100_000, 2.0, 8.0), Platform.UBER, Instant.parse("2026-10-21T11:12:20Z"))

    @Test
    fun `an offer fills a completed trip that saves as G1`() {
        val last = LastCheckInputs(platform = Platform.PICKME, commissionType = CommissionType.AMOUNT, commissionHundredths = 20_000, tripKm = 5.0)
        val form = offerTripForm(last, g1Offer)
        assertEquals(TripStatus.COMPLETED, form.status)
        assertEquals(LocalDate.of(2026, 10, 21), form.date)
        assertEquals(LocalTime.of(16, 42), form.time)
        assertEquals(Platform.UBER, form.ride.platform)
        assertEquals("1000", form.ride.fare)
        assertEquals("2", form.ride.pickupKm)
        assertEquals("8", form.ride.tripKm)
        assertEquals("", form.ride.returnKm)
        assertEquals(70_500L, calculator.countedProfitCents(newTrip(valid(form), 1, 1)))
    }

    @Test
    fun `an offer without a pickup distance leaves the pickup empty`() {
        val form = offerTripForm(LastCheckInputs(), g1Offer.copy(reading = OfferReading(85_000, null, 6.5)))
        assertEquals("850", form.ride.fare)
        assertEquals("", form.ride.pickupKm)
        assertEquals("6.5", form.ride.tripKm)
    }

    @Test
    fun `places from an offer fill the form and are saved`() {
        // Sample places, made up.
        val reading = OfferReading(100_000, 2.0, 8.0, pickupLocation = "Sample Road", dropOffLocation = "Example Mawatha")
        val form = offerTripForm(LastCheckInputs(), g1Offer.copy(reading = reading))
        assertEquals("Sample Road", form.ride.pickupLocation)
        assertEquals("Example Mawatha", form.ride.dropOffLocation)
        val trip = newTrip(valid(form), 1, 1)
        assertEquals("Sample Road", trip.pickupLocation)
        assertEquals("Example Mawatha", trip.dropOffLocation)
        assertEquals(form.ride.pickupLocation, trip.toFormState().ride.pickupLocation)
    }

    @Test
    fun `typed places are trimmed and blank ones are not saved`() {
        val form = g1.copy(ride = g1.ride.copy(pickupLocation = "  Sample   Road ", dropOffLocation = "   "))
        val trip = newTrip(valid(form), 1, 1)
        assertEquals("Sample Road", trip.pickupLocation)
        assertNull(trip.dropOffLocation)
        assertEquals("", trip.toFormState().ride.dropOffLocation)
    }

    @Test
    fun `the Add trip route carries an offer and back`() {
        assertEquals(g1Offer, EditTripRoute.forOffer(g1Offer).offer())
        val noPickup = g1Offer.copy(reading = OfferReading(85_000, null, 6.5))
        assertEquals(noPickup, EditTripRoute.forOffer(noPickup).offer())
        val placed = g1Offer.copy(reading = OfferReading(85_000, 1.0, 6.5, pickupLocation = "Sample Road", dropOffLocation = "Example Mawatha"))
        assertEquals(placed, EditTripRoute.forOffer(placed).offer())
        assertNull(EditTripRoute(EditTripRoute.NEW).offer())
        assertNull(EditTripRoute(5).offer())
    }
}
