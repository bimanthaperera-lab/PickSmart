package lk.picksmart.data.sync

/** One document read from the cloud, with the server time of its last write. */
data class CloudDocument(val id: String, val data: Map<String, Any?>, val serverTimeMillis: Long?)

/** Why a backup didn't finish. */
enum class SyncProblem {
    /** No internet, or the server didn't answer in time. Tried again later. */
    OFFLINE,

    /** The server refused the request, for example because the rider was signed out meanwhile. */
    REFUSED,

    /** The server refused because this copy of the app couldn't be verified (App Check). */
    UNVERIFIED,

    /** Anything else. */
    FAILED,
}

class CloudException(val problem: SyncProblem, cause: Throwable? = null) : Exception(cause)

/**
 * The rider's own space in the cloud, `users/{uid}`, with one collection per [lk.picksmart.data.local.entity.SyncKind].
 * Each call throws [CloudException] when it can't reach or use the server; it never answers from
 * a cache, so an offline phone never mistakes "nothing new" for "nothing there".
 */
interface CloudStore {
    /** Documents in [kind] written at or after [sinceMillis] (server time), oldest first; all of them when null. */
    suspend fun changedSince(uid: String, kind: String, sinceMillis: Long?): List<CloudDocument>

    /** Writes each document whole (keyed by sync id), replacing what was there. */
    suspend fun write(uid: String, kind: String, documents: Map<String, Map<String, Any?>>)

    /** The rider's profile document, or null when there isn't one. */
    suspend fun readProfile(uid: String): Map<String, Any?>?

    /** Merges [data] into the profile document; a null value removes that field. */
    suspend fun writeProfile(uid: String, data: Map<String, Any?>)

    /** The photo the rider chose, as JPEG bytes, or null when none is backed up. */
    suspend fun readPhoto(uid: String): ByteArray?

    /** Replaces the backed-up photo with [jpeg], or removes it when null. */
    suspend fun writePhoto(uid: String, jpeg: ByteArray?)

    /** Removes every document the rider has in the cloud. */
    suspend fun deleteEverything(uid: String)
}
