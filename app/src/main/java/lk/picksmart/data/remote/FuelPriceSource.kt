package lk.picksmart.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import lk.picksmart.data.preferences.UserPreferencesRepository
import java.io.IOException
import java.time.Clock
import java.time.Duration

/** The outcome of [FuelPriceSource.refreshIfDue]. Only tests and logs care; riders see the date. */
enum class FuelPriceRefresh {
    /** This build has no price file URL. */
    NOT_CONFIGURED,

    /** Checked less than 12 hours ago. */
    NOT_DUE,

    /** Offline or no answer. Not counted as a check, so the next app start tries again. */
    NO_CONNECTION,

    /** The server answered without the file. Counted as a check. */
    SERVER_ERROR,

    /** The file failed validation; the last valid list is kept. Counted as a check. */
    REJECTED,

    /** A valid list was saved. */
    UPDATED,
}

/**
 * Fetches the published fuel price file, validates it and caches the last valid list in
 * DataStore (CLAUDE.md section 8a).
 *
 * - At most once every 12 hours, counted from the last time the server answered. A failed
 *   connection doesn't count, so coming back online retries on the next app start or Check a Ride.
 * - Failures are silent: the last valid list stays, and screens read it offline. Nothing here
 *   ever throws to the caller.
 *
 * @param fetcher Null when this build has no price file URL; nothing is downloaded then.
 */
class FuelPriceSource(
    private val fetcher: FuelPriceFetcher?,
    private val preferences: UserPreferencesRepository,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    val isConfigured: Boolean get() = fetcher != null

    suspend fun refreshIfDue(): FuelPriceRefresh {
        val fetcher = fetcher ?: return FuelPriceRefresh.NOT_CONFIGURED
        // One download at a time; a second caller waits and then sees the fresh check time.
        return mutex.withLock {
            try {
                refresh(fetcher)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                // DataStore couldn't be read or written. Try again next time.
                FuelPriceRefresh.NO_CONNECTION
            }
        }
    }

    private suspend fun refresh(fetcher: FuelPriceFetcher): FuelPriceRefresh {
        val now = clock.millis()
        val lastCheck = preferences.fuelPriceLastCheckAt.first()
        // A clock moved back before the last check also counts as due, so it can't block for good.
        if (lastCheck != null && now >= lastCheck && now - lastCheck < MIN_INTERVAL.toMillis()) {
            return FuelPriceRefresh.NOT_DUE
        }
        val body = when (val result = fetcher.fetch()) {
            FetchResult.NoConnection -> return FuelPriceRefresh.NO_CONNECTION
            FetchResult.ServerError -> {
                preferences.setFuelPriceLastCheckAt(now)
                return FuelPriceRefresh.SERVER_ERROR
            }
            is FetchResult.Success -> result.body
        }
        preferences.setFuelPriceLastCheckAt(now)
        return when (val parsed = parseFuelPriceFile(body, preferences.fuelPriceList.first(), now)) {
            is FuelPriceParseResult.Rejected -> FuelPriceRefresh.REJECTED
            is FuelPriceParseResult.Valid -> {
                preferences.setFuelPriceList(parsed.list)
                FuelPriceRefresh.UPDATED
            }
        }
    }

    private companion object {
        val MIN_INTERVAL: Duration = Duration.ofHours(12)
    }
}
