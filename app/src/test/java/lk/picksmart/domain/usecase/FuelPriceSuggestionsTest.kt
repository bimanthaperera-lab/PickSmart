package lk.picksmart.domain.usecase

import lk.picksmart.domain.model.FuelPriceList
import lk.picksmart.domain.model.FuelType
import lk.picksmart.domain.model.VehicleInput
import lk.picksmart.domain.model.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Sample data for tests, not real prices. */
class FuelPriceSuggestionsTest {

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
    )

    private val list = FuelPriceList(
        effectiveFrom = LocalDate.of(2026, 10, 1),
        fetchedAtMillis = 0,
        pricesCentsPerLitre = mapOf(
            FuelType.PETROL_92 to 41_400,
            FuelType.PETROL_95 to 47_500,
            FuelType.AUTO_DIESEL to 39_200,
            FuelType.SUPER_DIESEL to 52_800,
        ),
    )

    @Test
    fun `the vehicle's own price wins for its own type`() {
        val prices = fuelPriceSuggestions(vehicle, ownPrices = mapOf(FuelType.PETROL_92 to 29_000), published = list)
        assertEquals(30_000L, prices[FuelType.PETROL_92])
    }

    @Test
    fun `the rider's remembered price wins over the published one`() {
        val prices = fuelPriceSuggestions(vehicle, ownPrices = mapOf(FuelType.PETROL_95 to 46_000), published = list)
        assertEquals(46_000L, prices[FuelType.PETROL_95])
        assertEquals(39_200L, prices[FuelType.AUTO_DIESEL])
    }

    @Test
    fun `with no list and nothing remembered, only the vehicle's type has a price`() {
        assertEquals(mapOf(FuelType.PETROL_92 to 30_000L), fuelPriceSuggestions(vehicle, emptyMap(), null))
        assertTrue(fuelPriceSuggestions(null, emptyMap(), null).isEmpty())
    }
}
