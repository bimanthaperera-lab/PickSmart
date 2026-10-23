package lk.picksmart.data.remote

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import lk.picksmart.data.preferences.UserPreferencesRepository
import lk.picksmart.domain.model.FuelType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class FuelPriceSourceTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.prefs() = UserPreferencesRepository(
        PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(tmp.root, "prefs.preferences_pb") }),
    )

    /** A clock the test moves by hand. */
    private class MutableClock(var now: Instant = Instant.parse("2026-10-23T04:00:00Z")) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
        fun advance(d: Duration) {
            now += d
        }
    }

    /** Returns [responses] in order (the last one repeats) and counts the calls. */
    private class FakeFetcher(vararg responses: FetchResult) : FuelPriceFetcher {
        private val queue = responses.toMutableList()
        var calls = 0
        override suspend fun fetch(): FetchResult {
            calls++
            return if (queue.size > 1) queue.removeAt(0) else queue.single()
        }
    }

    private fun body(p92: Long, effectiveFrom: String = "2026-10-01") = FetchResult.Success(
        """{"schema": 1, "effectiveFrom": "$effectiveFrom", "pricesCentsPerLitre":
            {"PETROL_92": $p92, "PETROL_95": 47500, "AUTO_DIESEL": 39200, "SUPER_DIESEL": 52800}}""",
    )

    @Test
    fun `a valid file is cached with its fetch time`() = runTest {
        val prefs = prefs()
        val clock = MutableClock()
        val source = FuelPriceSource(FakeFetcher(body(41_400)), prefs, clock)

        assertEquals(FuelPriceRefresh.UPDATED, source.refreshIfDue())
        val list = prefs.fuelPriceList.first()!!
        assertEquals(41_400L, list.priceFor(FuelType.PETROL_92))
        assertEquals(clock.millis(), list.fetchedAtMillis)
        assertEquals(clock.millis(), prefs.fuelPriceLastCheckAt.first())
    }

    @Test
    fun `checks at most once every 12 hours`() = runTest {
        val clock = MutableClock()
        val fetcher = FakeFetcher(body(41_400))
        val source = FuelPriceSource(fetcher, prefs(), clock)

        assertEquals(FuelPriceRefresh.UPDATED, source.refreshIfDue())
        clock.advance(Duration.ofHours(11).plusMinutes(59))
        assertEquals(FuelPriceRefresh.NOT_DUE, source.refreshIfDue())
        assertEquals(1, fetcher.calls)

        clock.advance(Duration.ofMinutes(1))
        assertEquals(FuelPriceRefresh.UPDATED, source.refreshIfDue())
        assertEquals(2, fetcher.calls)
    }

    @Test
    fun `a clock set back before the last check doesn't block checks`() = runTest {
        val clock = MutableClock()
        val fetcher = FakeFetcher(body(41_400))
        val source = FuelPriceSource(fetcher, prefs(), clock)
        source.refreshIfDue()
        clock.advance(Duration.ofDays(-3))
        assertEquals(FuelPriceRefresh.UPDATED, source.refreshIfDue())
        assertEquals(2, fetcher.calls)
    }

    @Test
    fun `offline keeps the last valid list and tries again next time`() = runTest {
        val prefs = prefs()
        val clock = MutableClock()
        val fetcher = FakeFetcher(body(41_400), FetchResult.NoConnection, FetchResult.NoConnection, body(42_000))
        val source = FuelPriceSource(fetcher, prefs, clock)
        source.refreshIfDue()
        val checkedAt = clock.millis()

        clock.advance(Duration.ofHours(13))
        assertEquals(FuelPriceRefresh.NO_CONNECTION, source.refreshIfDue())
        assertEquals(41_400L, prefs.fuelPriceList.first()!!.priceFor(FuelType.PETROL_92))
        // A failed connection isn't a check, so the throttle doesn't hold the next try back.
        assertEquals(checkedAt, prefs.fuelPriceLastCheckAt.first())
        clock.advance(Duration.ofMinutes(5))
        assertEquals(FuelPriceRefresh.NO_CONNECTION, source.refreshIfDue())
        clock.advance(Duration.ofMinutes(5))
        assertEquals(FuelPriceRefresh.UPDATED, source.refreshIfDue())
        assertEquals(42_000L, prefs.fuelPriceList.first()!!.priceFor(FuelType.PETROL_92))
    }

    @Test
    fun `a rejected file keeps the last valid list but counts as a check`() = runTest {
        val prefs = prefs()
        val clock = MutableClock()
        // Rs. 414 to Rs. 900 is more than a 40% change.
        val fetcher = FakeFetcher(body(41_400), body(90_000, effectiveFrom = "2026-11-01"))
        val source = FuelPriceSource(fetcher, prefs, clock)
        source.refreshIfDue()
        val firstFetch = prefs.fuelPriceList.first()!!

        clock.advance(Duration.ofHours(12))
        assertEquals(FuelPriceRefresh.REJECTED, source.refreshIfDue())
        assertEquals(firstFetch, prefs.fuelPriceList.first())
        assertEquals(clock.millis(), prefs.fuelPriceLastCheckAt.first())
        clock.advance(Duration.ofHours(1))
        assertEquals(FuelPriceRefresh.NOT_DUE, source.refreshIfDue())
    }

    @Test
    fun `a server error saves nothing but counts as a check`() = runTest {
        val prefs = prefs()
        val clock = MutableClock()
        val source = FuelPriceSource(FakeFetcher(FetchResult.ServerError), prefs, clock)
        assertEquals(FuelPriceRefresh.SERVER_ERROR, source.refreshIfDue())
        assertNull(prefs.fuelPriceList.first())
        assertEquals(clock.millis(), prefs.fuelPriceLastCheckAt.first())
    }

    @Test
    fun `without a URL nothing is downloaded`() = runTest {
        val prefs = prefs()
        val source = FuelPriceSource(null, prefs, MutableClock())
        assertEquals(FuelPriceRefresh.NOT_CONFIGURED, source.refreshIfDue())
        assertNull(prefs.fuelPriceLastCheckAt.first())
    }
}
