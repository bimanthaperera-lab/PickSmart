package lk.picksmart.data.sync

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import lk.picksmart.data.account.await
import lk.picksmart.data.local.entity.SyncKind
import java.util.Date

/**
 * [CloudStore] on Cloud Firestore: `users/{uid}` is the profile and `users/{uid}/{kind}/{syncId}`
 * the records. The security rules in `firestore.rules` let a rider reach only their own `users/{uid}`.
 *
 * Reads go to the server ([Source.SERVER]), never Firestore's cache. Writes are batched, and each
 * document also gets a server timestamp so other devices can ask for "what changed since".
 *
 * [appVerified] tells a refusal by App Check apart from one by the security rules.
 */
class FirestoreCloudStore(
    private val firestore: FirebaseFirestore,
    private val appVerified: suspend () -> Boolean,
) : CloudStore {

    override suspend fun changedSince(uid: String, kind: String, sinceMillis: Long?): List<CloudDocument> = call {
        var query: Query = records(uid, kind).orderBy(SERVER_TIME).limit(PAGE_SIZE.toLong())
        if (sinceMillis != null) query = query.whereGreaterThanOrEqualTo(SERVER_TIME, Timestamp(Date(sinceMillis)))
        val documents = mutableListOf<CloudDocument>()
        var last: DocumentSnapshot? = null
        do {
            val page = (last?.let(query::startAfter) ?: query).get(Source.SERVER).await()
            page.documents.mapTo(documents) {
                CloudDocument(it.id, it.data.orEmpty(), it.getTimestamp(SERVER_TIME)?.toDate()?.time)
            }
            last = page.documents.lastOrNull()
        } while (page.size() == PAGE_SIZE)
        documents
    }

    override suspend fun write(uid: String, kind: String, documents: Map<String, Map<String, Any?>>) = call {
        for (chunk in documents.entries.chunked(PAGE_SIZE)) {
            val batch = firestore.batch()
            for ((syncId, data) in chunk) batch.set(records(uid, kind).document(syncId), data + (SERVER_TIME to FieldValue.serverTimestamp()))
            // Offline, a commit waits for the network; Firestore keeps it queued, and the rows stay
            // pending here so the next run sends them again.
            withTimeout(WRITE_TIMEOUT_MILLIS) { batch.commit().await() }
        }
    }

    override suspend fun readProfile(uid: String): Map<String, Any?>? = call {
        profile(uid).get(Source.SERVER).await().data
    }

    override suspend fun writeProfile(uid: String, data: Map<String, Any?>) = call {
        withTimeout(WRITE_TIMEOUT_MILLIS) { profile(uid).set(data, SetOptions.merge()).await() }
        Unit
    }

    override suspend fun deleteEverything(uid: String) = call {
        for (kind in SyncKind.ALL) {
            do {
                val page = records(uid, kind).limit(PAGE_SIZE.toLong()).get(Source.SERVER).await()
                if (page.isEmpty) break
                val batch = firestore.batch()
                page.documents.forEach { batch.delete(it.reference) }
                withTimeout(WRITE_TIMEOUT_MILLIS) { batch.commit().await() }
            } while (page.size() == PAGE_SIZE)
        }
        withTimeout(WRITE_TIMEOUT_MILLIS) { profile(uid).delete().await() }
        Unit
    }

    private fun profile(uid: String) = firestore.collection(USERS).document(uid)

    private fun records(uid: String, kind: String) = profile(uid).collection(kind)

    /** Turns Firestore's failures into a [SyncProblem]. */
    private suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (e: TimeoutCancellationException) {
        throw CloudException(SyncProblem.OFFLINE, e)
    } catch (e: CancellationException) {
        throw e
    } catch (e: FirebaseFirestoreException) {
        throw CloudException(firestoreProblem(e.code, appVerified), e)
    } catch (e: Exception) {
        throw CloudException(SyncProblem.FAILED, e)
    }

    private companion object {
        const val USERS = "users"
        const val SERVER_TIME = "serverUpdatedAt"

        /** Firestore allows 500 writes per batch; this leaves room. */
        const val PAGE_SIZE = 400
        const val WRITE_TIMEOUT_MILLIS = 30_000L
    }
}

/**
 * What a Firestore error code means for the rider. Firestore answers a request that failed App
 * Check the same way as one the security rules refused, so a refusal asks [appVerified] which it was.
 */
internal suspend fun firestoreProblem(code: FirebaseFirestoreException.Code, appVerified: suspend () -> Boolean): SyncProblem =
    when (code) {
        FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> SyncProblem.OFFLINE
        FirebaseFirestoreException.Code.PERMISSION_DENIED, FirebaseFirestoreException.Code.UNAUTHENTICATED ->
            if (appVerified()) SyncProblem.REFUSED else SyncProblem.UNVERIFIED
        else -> SyncProblem.FAILED
    }
