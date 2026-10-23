package lk.picksmart.data.remote

import lk.picksmart.domain.model.FuelPriceList
import lk.picksmart.domain.model.FuelType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FuelPriceParserTest {

    private val fixture: String =
        requireNotNull(javaClass.classLoader?.getResource("fuel-prices/valid.json")).readText()

    /** A file like the fixture with the given prices (cents) and date. Null leaves a price out. */
    private fun file(
        p92: Long? = 41_400,
        p95: Long? = 47_500,
        autoDiesel: Long? = 39_200,
        superDiesel: Long? = 52_800,
        effectiveFrom: String = "2026-10-01",
        schema: String = "1",
    ): String {
        val prices = listOfNotNull(
            p92?.let { "\"PETROL_92\": $it" },
            p95?.let { "\"PETROL_95\": $it" },
            autoDiesel?.let { "\"AUTO_DIESEL\": $it" },
            superDiesel?.let { "\"SUPER_DIESEL\": $it" },
        ).joinToString()
        return """{"schema": $schema, "effectiveFrom": "$effectiveFrom", "pricesCentsPerLitre": {$prices}}"""
    }

    private val previous = FuelPriceList(
        effectiveFrom = LocalDate.of(2026, 9, 1),
        fetchedAtMillis = 1,
        pricesCentsPerLitre = mapOf(
            FuelType.PETROL_92 to 40_000,
            FuelType.PETROL_95 to 46_000,
            FuelType.AUTO_DIESEL to 38_000,
            FuelType.SUPER_DIESEL to 50_000,
        ),
    )

    private fun rejected(text: String, prev: FuelPriceList? = null) =
        (parseFuelPriceFile(text, prev, 99) as FuelPriceParseResult.Rejected).reason

    @Test
    fun `the fixture parses into a full list`() {
        val list = (parseFuelPriceFile(fixture, previous = null, fetchedAtMillis = 123) as FuelPriceParseResult.Valid).list
        assertEquals(LocalDate.of(2026, 10, 1), list.effectiveFrom)
        assertEquals(123L, list.fetchedAtMillis)
        assertEquals(41_400L, list.priceFor(FuelType.PETROL_92))
        assertEquals(47_500L, list.priceFor(FuelType.PETROL_95))
        assertEquals(39_200L, list.priceFor(FuelType.AUTO_DIESEL))
        assertEquals(52_800L, list.priceFor(FuelType.SUPER_DIESEL))
    }

    @Test
    fun `unknown fields and fuel types are ignored`() {
        val text = """{"schema": 1, "effectiveFrom": "2026-10-01", "note": "x",
            "pricesCentsPerLitre": {"PETROL_92": 41400, "PETROL_95": 47500, "AUTO_DIESEL": 39200,
            "SUPER_DIESEL": 52800, "KEROSENE": 18500}}"""
        assertTrue(parseFuelPriceFile(text, null, 1) is FuelPriceParseResult.Valid)
    }

    @Test
    fun `an unknown schema is rejected`() {
        assertEquals(FuelPriceRejection.UNKNOWN_SCHEMA, rejected(file(schema = "2")))
    }

    @Test
    fun `a missing price is rejected`() {
        assertEquals(FuelPriceRejection.MISSING_PRICE, rejected(file(superDiesel = null)))
        assertEquals(FuelPriceRejection.MISSING_PRICE, rejected("""{"schema": 1, "effectiveFrom": "2026-10-01"}"""))
    }

    @Test
    fun `a price that isn't whole cents is rejected`() {
        val text = file().replace("41400", "414.5")
        assertEquals(FuelPriceRejection.MISSING_PRICE, rejected(text))
        assertEquals(FuelPriceRejection.MISSING_PRICE, rejected(file().replace("41400", "\"41400\"")))
    }

    @Test
    fun `prices outside Rs 50 to Rs 2,000 are rejected, the limits themselves are allowed`() {
        assertEquals(FuelPriceRejection.PRICE_OUT_OF_RANGE, rejected(file(p92 = 4_999)))
        assertEquals(FuelPriceRejection.PRICE_OUT_OF_RANGE, rejected(file(p92 = 200_001)))
        assertEquals(FuelPriceRejection.PRICE_OUT_OF_RANGE, rejected(file(p92 = -41_400)))
        assertTrue(parseFuelPriceFile(file(p92 = 5_000, p95 = 200_000), null, 1) is FuelPriceParseResult.Valid)
    }

    @Test
    fun `a change of more than 40 percent is rejected, exactly 40 percent is allowed`() {
        // Previous Petrol 92 is Rs. 400.00: 40% is Rs. 160.00.
        assertEquals(FuelPriceRejection.CHANGE_TOO_LARGE, rejected(file(p92 = 56_001), previous))
        assertEquals(FuelPriceRejection.CHANGE_TOO_LARGE, rejected(file(p92 = 23_999), previous))
        assertTrue(parseFuelPriceFile(file(p92 = 56_000), previous, 1) is FuelPriceParseResult.Valid)
        assertTrue(parseFuelPriceFile(file(p92 = 24_000), previous, 1) is FuelPriceParseResult.Valid)
    }

    @Test
    fun `without a previous list there is no change limit`() {
        assertTrue(parseFuelPriceFile(file(p92 = 150_000), null, 1) is FuelPriceParseResult.Valid)
    }

    @Test
    fun `a file dated before the saved list is rejected`() {
        assertEquals(FuelPriceRejection.OLDER_THAN_SAVED, rejected(file(effectiveFrom = "2026-08-31"), previous))
        assertTrue(parseFuelPriceFile(file(effectiveFrom = "2026-09-01"), previous, 1) is FuelPriceParseResult.Valid)
    }

    @Test
    fun `broken files are rejected, not thrown`() {
        assertEquals(FuelPriceRejection.MALFORMED, rejected(""))
        assertEquals(FuelPriceRejection.MALFORMED, rejected("<html>Not found</html>"))
        assertEquals(FuelPriceRejection.MALFORMED, rejected("[1, 2]"))
        assertEquals(FuelPriceRejection.MALFORMED, rejected(file(effectiveFrom = "1 Oct 2026")))
        assertEquals(FuelPriceRejection.MALFORMED, rejected(file(schema = "\"1\"")))
        assertEquals(FuelPriceRejection.MALFORMED, rejected(fixture.dropLast(10)))
    }
}
