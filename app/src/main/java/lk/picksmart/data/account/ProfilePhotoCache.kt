package lk.picksmart.data.account

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * The rider's profile photo (the Google account photo), kept on the device so the Account tab
 * shows it offline (CLAUDE.md section 14). One small file per user; removed on sign-out.
 */
class ProfilePhotoCache(private val directory: File) {

    /** The cached photo, downloading it first if needed. Null when there is none or it can't load. */
    suspend fun load(uid: String, url: String?): Bitmap? = withContext(Dispatchers.IO) {
        val file = fileFor(uid)
        if (!file.exists() && url != null) download(url, file)
        if (file.exists()) BitmapFactory.decodeFile(file.path) else null
    }

    suspend fun clear(uid: String) = withContext(Dispatchers.IO) {
        fileFor(uid).delete()
        Unit
    }

    private fun fileFor(uid: String) = File(directory, "${uid.filter(Char::isLetterOrDigit)}.img")

    private fun download(url: String, file: File) {
        if (!url.startsWith("https://")) return
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.useCaches = false
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return
            val bytes = connection.inputStream.use { it.readNBytesCompat(MAX_BYTES + 1) }
            if (bytes.size > MAX_BYTES || BitmapFactory.decodeByteArray(bytes, 0, bytes.size) == null) return
            directory.mkdirs()
            // Written to a temporary file first, so a half-written photo is never read.
            val partial = File(directory, file.name + ".part")
            partial.writeBytes(bytes)
            partial.renameTo(file)
        } catch (e: IOException) {
            // Offline or the photo moved: the initials show instead, and the next load tries again.
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000
        const val MAX_BYTES = 1_000_000
    }
}

/** Reads up to [limit] bytes (InputStream.readNBytes needs API 33). */
private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (out.size() < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
