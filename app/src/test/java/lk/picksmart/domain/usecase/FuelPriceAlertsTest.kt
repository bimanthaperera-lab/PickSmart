package lk.picksmart.domain.usecase

import lk.picksmart.domain.model.FuelPriceSource
import lk.picksmart.domain.model.FuelType
import lk.picksmart.domain.model.VehicleInput
import lk.picksmart.domain.model.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** Sample vehicles and prices for tests, not real rates. */
class FuelPriceAlertsTest {

    private val listDate = LocalDate.of(2026, 10, 1)

    private val vehicle = newVehicle(
        VehicleInput(
            name = "Sample bike",
            type = VehicleType.MOTORCYCLE,
            fuelType = FuelType.PETROL_92,
            fuelPricePerLitreCents = 30_000,
            fuelEfficiencyKmPerLitre = 40.0,
            maintenanceReservePerKmCents = 200,
            targetProfitPerKmCents = null,
        ),
        isSample = false,
        nowMillis = 0,
    ).copy(id = 1)

    private fun state(
        action: PublishedPriceAction = PublishedPriceAction.None,
        autoApplied: Boolean = false,
        vehicle: lk.picksmart.domain.model.Vehicle? = this.vehicle,
    ) = FuelPriceState(vehicle = vehicle, list = null, action = action, listStale = false, autoApplied = autoApplied)

    @Test
    fun `a published price waiting for the rider is news`() {
        val news = fuelPriceNews(state(PublishedPriceAction.Offer(31_000, listDate)))
        assertEquals(FuelPriceNews.NewPrice(vehicle, 31_000, listDate), news)
    }

    @Test
    fun `an automatic update is news`() {
        val applied = vehicle.copy(fuelPriceSource = FuelPriceSource.PUBLISHED, fuelPriceEffectiveDate = listDate)
        val news = fuelPriceNews(state(autoApplied = true, vehicle = applied))
        assertEquals(FuelPriceNews.Applied(applied, listDate), news)
    }

    @Test
    fun `nothing new, or no vehicle, is no news`() {
        assertNull(fuelPriceNews(state()))
        assertNull(fuelPriceNews(state(PublishedPriceAction.Offer(31_000, listDate), vehicle = null)))
    }

    @Test
    fun `the same price list gives the same key, a new list a new one`() {
        val first = fuelPriceNews(state(PublishedPriceAction.Offer(31_000, listDate)))!!.key
        val again = fuelPriceNews(state(PublishedPriceAction.Offer(31_000, listDate)))!!.key
        val next = fuelPriceNews(state(PublishedPriceAction.Offer(32_000, listDate.plusDays(14))))!!.key
        assertEquals(first, again)
        assertNotEquals(first, next)
    }

    @Test
    fun `alerts are kept for 30 days`() {
        val now = 40L * 24 * 60 * 60 * 1000
        assertEquals(10L * 24 * 60 * 60 * 1000, alertCutoffMillis(now))
    }
}
