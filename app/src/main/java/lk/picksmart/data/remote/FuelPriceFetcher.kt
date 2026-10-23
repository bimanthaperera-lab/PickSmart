package lk.picksmart.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** What one attempt to download the price file gave. */
sealed interface FetchResult {
    data class Success(val body: String) : FetchResult

    /** The server answered, but not with the file (e.g. 404, or the file was too big). */
    data object ServerError : FetchResult

    /** No answer: offline, DNS failure or timeout. */
    data object NoConnection : FetchResult
}

/** Downloads the published fuel price file. */
fun interface FuelPriceFetcher {
    suspend fun fetch(): FetchResult
}

/**
 * A plain HTTPS GET of [url] (CLAUDE.md section 8a). It sends no user data, identifiers, cookies
 * or analytics: no cache, no cookie handler, and a fixed User-Agent instead of the default one,
 * which names the phone model and Android build. Gives up after [TIMEOUT_MILLIS].
 */
class HttpsFuelPriceFetcher(private val url: String) : FuelPriceFetcher {

    override suspend fun fetch(): FetchResult =
        withTimeoutOrNull(TIMEOUT_MILLIS) { runInterruptible(Dispatchers.IO) { download() } }
            ?: FetchResult.NoConnection

    private fun download(): FetchResult {
        val connection = try {
            URL(url).openConnection() as? HttpURLConnection
        } catch (e: IOException) {
            return FetchResult.NoConnection
        } ?: return FetchResult.ServerError
        return try {
            connection.connectTimeout = TIMEOUT_MILLIS.toInt()
            connection.readTimeout = TIMEOUT_MILLIS.toInt()
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return FetchResult.ServerError
            val body = connection.inputStream.use { it.readAtMost(MAX_BYTES) }
            if (body == null) FetchResult.ServerError else FetchResult.Success(body.decodeToString())
        } catch (e: IOException) {
            FetchResult.NoConnection
        } finally {
            connection.disconnect()
        }
    }

    /** The whole stream, or null if it is longer than [limit] bytes. */
    private fun InputStream.readAtMost(limit: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = read(buffer)
            if (read < 0) return out.toByteArray()
            out.write(buffer, 0, read)
            if (out.size() > limit) return null
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val USER_AGENT = "Picksmart"

        /** The real file is well under 1 KB; anything this large is not it. */
        const val MAX_BYTES = 64 * 1024
    }
}
