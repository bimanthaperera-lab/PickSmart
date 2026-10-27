package lk.picksmart.data.account

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A loaded profile photo, and whether it is one the rider chose (so it can be removed). */
data class ProfilePhoto(val bitmap: Bitmap?, val own: Boolean)

/** What happened to a photo the rider picked. */
enum class PhotoSaveResult { SAVED, UNSUPPORTED_FORMAT, TOO_LARGE, UNREADABLE }

/** Image types a picked photo may be: what phone cameras and galleries save. */
internal val SUPPORTED_PHOTO_TYPES = setOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")

/** The largest picked file accepted, in bytes (20 MB, well above a phone camera photo). */
internal const val MAX_PICKED_PHOTO_BYTES = 20L * 1024 * 1024

/**
 * Why a picked file can't be used, or null when it can. [mimeType] or [sizeBytes] is null when the
 * picker doesn't say; then decoding decides (an unknown size is still read at a reduced scale).
 */
internal fun pickedPhotoProblem(mimeType: String?, sizeBytes: Long?): PhotoSaveResult? = when {
    mimeType != null && mimeType.lowercase() !in SUPPORTED_PHOTO_TYPES -> PhotoSaveResult.UNSUPPORTED_FORMAT
    sizeBytes != null && sizeBytes > MAX_PICKED_PHOTO_BYTES -> PhotoSaveResult.TOO_LARGE
    else -> null
}

/**
 * The rider's profile photo, kept on the device so the Account tab shows it offline (CLAUDE.md
 * section 14). Two small files per user: the Google account photo, a cache removed on sign-out,
 * and a photo the rider chose from their gallery, which wins over it. The chosen photo stays on
 * the phone only (no upload) and is kept on sign-out, like the mobile number; it is removed when
 * the rider removes it or deletes the account.
 */
class ProfilePhotoCache(private val directory: File) {

    private val mutableChanges = MutableStateFlow(0)

    /** Changes whenever a chosen photo is saved or removed, so screens load it again. */
    val changes: StateFlow<Int> = mutableChanges.asStateFlow()

    /** The chosen photo, else the cached Google photo (downloading it first if needed). */
    suspend fun load(uid: String, url: String?): ProfilePhoto = withContext(Dispatchers.IO) {
        val own = ownFileFor(uid)
        if (own.exists()) {
            BitmapFactory.decodeFile(own.path)?.let { return@withContext ProfilePhoto(it, own = true) }
        }
        val file = fileFor(uid)
        if (!file.exists() && url != null) download(url, file)
        ProfilePhoto(if (file.exists()) BitmapFactory.decodeFile(file.path) else null, own = false)
    }

    /**
     * Makes the image at [uri] (from the Photo Picker) the rider's photo: decoded at a small size,
     * cropped to a square, and saved as a JPEG of at most [SIZE_PX] pixels a side. The picked file
     * itself is never kept. Only JPEG, PNG, WebP and HEIC files of up to 20 MB are used.
     */
    suspend fun saveOwn(uid: String, resolver: ContentResolver, uri: Uri): PhotoSaveResult = withContext(Dispatchers.IO) {
        pickedPhotoProblem(resolver.getType(uri), sizeOf(resolver, uri))?.let { return@withContext it }
        val decoded = try {
            decode(resolver, uri)
        } catch (e: IOException) {
            null
        } catch (e: RuntimeException) {
            // A broken or unsupported image (ImageDecoder and BitmapFactory throw several kinds).
            null
        } ?: return@withContext PhotoSaveResult.UNREADABLE
        val square = squareCrop(decoded)
        try {
            directory.mkdirs()
            // Written to a temporary file first, so a half-written photo is never read.
            val partial = File(directory, ownFileFor(uid).name + ".part")
            partial.outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            if (!partial.renameTo(ownFileFor(uid))) return@withContext PhotoSaveResult.UNREADABLE
        } catch (e: IOException) {
            return@withContext PhotoSaveResult.UNREADABLE
        } finally {
            if (square !== decoded) square.recycle()
            decoded.recycle()
        }
        mutableChanges.update { it + 1 }
        PhotoSaveResult.SAVED
    }

    /** Removes the photo the rider chose; the Google photo or initials show again. */
    suspend fun removeOwn(uid: String) = withContext(Dispatchers.IO) {
        ownFileFor(uid).delete()
        mutableChanges.update { it + 1 }
    }

    /** On sign-out: removes the cached Google photo and keeps the one the rider chose. */
    suspend fun clear(uid: String) = withContext(Dispatchers.IO) {
        fileFor(uid).delete()
        Unit
    }

    /** When [uid]'s account is deleted: removes both of their photos. Other accounts' stay. */
    suspend fun forget(uid: String) = withContext(Dispatchers.IO) {
        ownFileFor(uid).delete()
        fileFor(uid).delete()
        mutableChanges.update { it + 1 }
    }

    private fun fileFor(uid: String) = File(directory, "${safe(uid)}.img")

    private fun ownFileFor(uid: String) = File(directory, "${safe(uid)}.own.jpg")

    private fun safe(uid: String) = uid.filter(Char::isLetterOrDigit)

    /** The picked file's size in bytes, or null when the provider doesn't report it. */
    private fun sizeOf(resolver: ContentResolver, uri: Uri): Long? = try {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (column >= 0 && cursor.moveToFirst() && !cursor.isNull(column)) cursor.getLong(column) else null
        }
    } catch (e: RuntimeException) {
        null
    }

    private fun decode(resolver: ContentResolver, uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder also turns the photo the right way up from its EXIF orientation.
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                decoder.setTargetSampleSize(sampleSizeFor(info.size.width, info.size.height, SIZE_PX))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, SIZE_PX) }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun squareCrop(bitmap: Bitmap): Bitmap {
        val side = minOf(bitmap.width, bitmap.height)
        val cropped = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
        if (side <= SIZE_PX) return cropped
        val scaled = cropped.scale(SIZE_PX, SIZE_PX)
        if (cropped !== bitmap) cropped.recycle()
        return scaled
    }

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
        const val SIZE_PX = 512
        const val JPEG_QUALITY = 85
    }
}

/**
 * The power-of-two step to decode a [width] × [height] image at, so its shorter side stays at least
 * [target] pixels while a large camera photo (or a long panorama, whose longer side is kept to
 * [MAX_DECODE_SIDE]) never fills memory.
 */
internal fun sampleSizeFor(width: Int, height: Int, target: Int): Int {
    var sample = 1
    while (minOf(width, height) / (sample * 2) >= target || maxOf(width, height) / sample > MAX_DECODE_SIDE) sample *= 2
    return sample
}

private const val MAX_DECODE_SIDE = 4096

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
