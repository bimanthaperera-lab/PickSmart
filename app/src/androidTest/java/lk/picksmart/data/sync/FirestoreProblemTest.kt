package lk.picksmart.data.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** A device test: Firestore's error codes need Android classes to load. */
@RunWith(AndroidJUnit4::class)
class FirestoreProblemTest {

    @Test
    fun refusalFromVerifiedAppIsRulesOrSignIn() = runTest {
        assertEquals(SyncProblem.REFUSED, firestoreProblem(Code.PERMISSION_DENIED) { true })
        assertEquals(SyncProblem.REFUSED, firestoreProblem(Code.UNAUTHENTICATED) { true })
    }

    @Test
    fun refusalFromUnverifiedAppSaysSo() = runTest {
        assertEquals(SyncProblem.UNVERIFIED, firestoreProblem(Code.PERMISSION_DENIED) { false })
        assertEquals(SyncProblem.UNVERIFIED, firestoreProblem(Code.UNAUTHENTICATED) { false })
    }

    @Test
    fun otherErrorsNeverAskAppCheck() = runTest {
        var asked = false
        val check: suspend () -> Boolean = { asked = true; false }
        assertEquals(SyncProblem.OFFLINE, firestoreProblem(Code.UNAVAILABLE, check))
        assertEquals(SyncProblem.OFFLINE, firestoreProblem(Code.DEADLINE_EXCEEDED, check))
        assertEquals(SyncProblem.FAILED, firestoreProblem(Code.INTERNAL, check))
        assertFalse(asked)
    }
}
