package lk.picksmart.domain.usecase

import lk.picksmart.domain.calculator.ProfitCalculator
import lk.picksmart.domain.calculator.ResultBand
import lk.picksmart.domain.model.CommissionType
import lk.picksmart.domain.model.FareMode
import lk.picksmart.domain.model.FuelType
import lk.picksmart.domain.model.LastCheckInputs
import lk.picksmart.domain.model.VehicleInput
import lk.picksmart.domain.model.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Sample data only: these offer cards are made up to cover layouts the parser should handle. They
 * are not copied from any ride app, and contain no names, addresses or phone numbers.
 */
class OfferTextTest {

    @Test
    fun `labelled pickup and trip lines`() {
        val reading = parseOfferText(listOf("UberX", "LKR 850.00", "4 min (1.2 km) away", "18 min (7.5 km) trip", "Accept"))
        assertEquals(OfferReading(85_000, 1.2, 7.5), reading)
    }

    @Test
    fun `numbers the parser could give are accepted`() {
        assertEquals(OfferReading(85_000, 1.2, 7.5), offerReadingOrNull(85_000, 1.2, 7.5))
        assertEquals(OfferReading(85_000, null, 7.5), offerReadingOrNull(85_000, null, 7.5))
        assertEquals(OfferReading(1, 0.0, 500.0), offerReadingOrNull(1, 0.0, 500.0))
        assertEquals(OfferReading(100_000_000, 2.0, null), offerReadingOrNull(100_000_000, 2.0, null))
    }

    @Test
    fun `a fare that isn't a ride is refused`() {
        assertNull(offerReadingOrNull(0, 1.0, 5.0))
        assertNull(offerReadingOrNull(-500, 1.0, 5.0))
        assertNull(offerReadingOrNull(100_000_001, 1.0, 5.0))
        assertNull(offerReadingOrNull(Long.MAX_VALUE, 1.0, 5.0))
    }

    @Test
    fun `a distance that can't be shown in the form is refused`() {
        assertNull(offerReadingOrNull(85_000, Double.POSITIVE_INFINITY, 5.0))
        assertNull(offerReadingOrNull(85_000, 1.0, Double.NaN))
        assertNull(offerReadingOrNull(85_000, -1.0, 5.0))
        assertNull(offerReadingOrNull(85_000, 1.0, 500.01))
        assertNull(offerReadingOrNull(85_000, Double.MAX_VALUE, null))
    }

    @Test
    fun `an offer needs at least one distance`() {
        assertNull(offerReadingOrNull(85_000, null, null))
    }

    @Test
    fun `labels win over reading order`() {
        val reading = parseOfferText(listOf("Rs. 1,250", "Trip: 12 km", "Pickup 2.5 km"))
        assertEquals(OfferReading(125_000, 2.5, 12.0), reading)
    }

    @Test
    fun `unlabelled distances fill pickup then trip`() {
        assertEquals(OfferReading(60_000, 1.0, 6.0), parseOfferText(listOf("Rs 600", "1 km", "6 km")))
    }

    @Test
    fun `a single unlabelled distance is the trip`() {
        assertEquals(OfferReading(45_000, null, 5.5), parseOfferText(listOf("450.00 LKR", "5.5 km")))
    }

    @Test
    fun `money and distance formats`() {
        assertEquals(OfferReading(120_050, null, 4.2), parseOfferText(listOf("LKR1,200.50 · 4,2 km")))
        assertEquals(OfferReading(30_000, null, 3.0), parseOfferText(listOf("රු. 300", "3KM")))
        assertEquals(OfferReading(99_900, null, 10.0), parseOfferText(listOf("Fare: rs.999", "10 kms")))
    }

    @Test
    fun `a bonus line is not the fare`() {
        assertEquals(OfferReading(70_000, null, 8.0), parseOfferText(listOf("+LKR 50 Boost", "LKR 700", "8 km")))
    }

    @Test
    fun `a bonus with a space after the plus is not the fare`() {
        assertEquals(OfferReading(70_000, null, 8.0), parseOfferText(listOf("+ LKR 200", "LKR 700", "8 km")))
        assertNull(parseOfferText(listOf("+ LKR 200", "8 km")))
        assertNull(parseOfferText(listOf("+", "LKR", "200", "8 km")))
    }

    // A card laid out like PickMe's: the fare in a large font with "LKR" after it, the pickup line
    // ("… min away (… km)") and the trip line ("… min (… km)") each over an address, and a "+ LKR"
    // chip. The values and places are made up.
    private val pickMeStyleCard = listOf(
        "TUK",
        "12 min away (5.0 km)",
        "Sample Road, Sampletown",
        "9 min  (3.4 km)",
        "Example Mawatha, Exampleville",
        "+ LKR 150",
        "Accept trip",
    )
    private val pickMeStyleReading = OfferReading(45_075, 5.0, 3.4)

    @Test
    fun `PickMe style card with the fare in one text`() {
        assertEquals(pickMeStyleReading, parseOfferText(listOf("450.75 LKR") + pickMeStyleCard))
        assertEquals(pickMeStyleReading, parseOfferText(listOf("450.75LKR") + pickMeStyleCard))
    }

    @Test
    fun `PickMe style card with the amount and LKR as two texts`() {
        assertEquals(pickMeStyleReading, parseOfferText(listOf("450.75", "LKR") + pickMeStyleCard))
        assertEquals(pickMeStyleReading, parseOfferText(listOf("LKR", "450.75") + pickMeStyleCard))
    }

    @Test
    fun `PickMe style card read as one text`() {
        val merged = (listOf("450.75 LKR") + pickMeStyleCard).joinToString(" ")
        assertEquals(pickMeStyleReading, parseOfferText(listOf(merged)))
    }

    @Test
    fun `an Uber style card read as one text keeps pickup and trip apart`() {
        assertEquals(OfferReading(85_000, 1.2, 7.5), parseOfferText(listOf("UberX LKR 850.00 4 min (1.2 km) away 18 min (7.5 km) trip Accept")))
    }

    @Test
    fun `a bare number next to LKR is the fare only when they touch`() {
        assertNull(parseOfferText(listOf("450", "TUK", "LKR", "5 km")))
    }

    @Test
    fun `long numbers such as phone numbers are ignored`() {
        assertEquals(OfferReading(50_000, null, 4.0), parseOfferText(listOf("Call 077 123 4567 Rs 9", "Rs 500", "4 km")))
        assertEquals(OfferReading(50_000, null, 4.0), parseOfferText(listOf("Call 0771234567 Rs 9", "Rs 500", "4 km")))
    }

    @Test
    fun `no fare or no distance gives nothing`() {
        assertNull(parseOfferText(listOf("4 min (1.2 km) away", "18 min (7.5 km) trip")))
        assertNull(parseOfferText(listOf("LKR 850.00", "18 min")))
        assertNull(parseOfferText(listOf("You are online", "Finding trips")))
        assertNull(parseOfferText(emptyList()))
    }

    @Test
    fun `out of range values are not read`() {
        assertNull(parseOfferText(listOf("LKR 0.00", "5 km")))
        assertNull(parseOfferText(listOf("LKR 2,000,000", "5 km")))
        assertNull(parseOfferText(listOf("LKR 500", "900 km")))
    }

    // checkOffer

    private val calculator = ProfitCalculator()

    /** Sample vehicle: Rs. 300/L, 40 km/L, Rs. 2/km reserve, as in golden test G1. */
    private val vehicle = newVehicle(
        VehicleInput("Sample bike", VehicleType.MOTORCYCLE, FuelType.PETROL_92, 30_000, 40.0, 200, null),
        isSample = false,
        nowMillis = 0,
    )

    @Test
    fun `an offer read as G1 gives G1's result`() {
        val last = LastCheckInputs(fareMode = FareMode.GROSS_BEFORE_COMMISSION, commissionType = CommissionType.PERCENT, commissionHundredths = 2_000)
        val result = calculator.checkOffer(OfferReading(100_000, 2.0, 8.0), vehicle, last)!!
        assertEquals(20_000L, result.commissionCents)
        assertEquals(7_500L, result.fuelCostCents)
        assertEquals(70_500L, result.netProfitCents)
        assertEquals(7_050L, result.netProfitPerKmCents)
        assertEquals(ResultBand.PROFIT, result.band)
    }

    @Test
    fun `payout mode takes no fee and the vehicle's target sets the band`() {
        val last = LastCheckInputs(fareMode = FareMode.PAYOUT_AFTER_COMMISSION, commissionHundredths = 2_000)
        val result = calculator.checkOffer(OfferReading(80_000, 2.0, 8.0), vehicle.copy(targetProfitPerKmCents = 8_000), last)!!
        assertEquals(0L, result.commissionCents)
        assertEquals(70_500L, result.netProfitCents)
        assertEquals(ResultBand.BELOW_TARGET, result.band)
    }

    @Test
    fun `a cheap long offer is a loss`() {
        val result = calculator.checkOffer(OfferReading(10_000, 3.0, 20.0), vehicle, LastCheckInputs())!!
        assertEquals(ResultBand.LOSS, result.band) // 100 − 172.50 fuel − 46 reserve
        assertEquals(-11_850L, result.netProfitCents)
    }
}
