package lk.picksmart.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncMergeTest {

    @Test
    fun `a record only in the cloud is saved here`() {
        assertEquals(MergeAction.WRITE_REMOTE, mergeAction(null, null, 10, remoteDeleted = false))
    }

    @Test
    fun `a newer cloud edit replaces the phone's copy`() {
        assertEquals(MergeAction.WRITE_REMOTE, mergeAction(10, null, 11, remoteDeleted = false))
    }

    @Test
    fun `an older or equal cloud copy never replaces the phone's`() {
        assertEquals(MergeAction.KEEP_LOCAL, mergeAction(10, null, 9, remoteDeleted = false))
        assertEquals(MergeAction.KEEP_LOCAL, mergeAction(10, null, 10, remoteDeleted = false))
    }

    @Test
    fun `a delete newer than the phone's edit removes it`() {
        assertEquals(MergeAction.DELETE_LOCAL, mergeAction(10, null, 11, remoteDeleted = true))
    }

    @Test
    fun `an edit newer than the delete keeps the record`() {
        assertEquals(MergeAction.KEEP_LOCAL, mergeAction(12, null, 11, remoteDeleted = true))
    }

    @Test
    fun `a stale copy can't bring back a record this phone deleted`() {
        assertEquals(MergeAction.KEEP_LOCAL, mergeAction(null, 20, 15, remoteDeleted = false))
        assertEquals(MergeAction.KEEP_LOCAL, mergeAction(null, 20, 20, remoteDeleted = false))
    }

    @Test
    fun `an edit saved elsewhere after this phone deleted it comes back`() {
        assertEquals(MergeAction.WRITE_REMOTE, mergeAction(null, 20, 21, remoteDeleted = false))
    }

    @Test
    fun `a delete of a record this phone never had changes nothing`() {
        assertEquals(MergeAction.KEEP_LOCAL, mergeAction(null, null, 10, remoteDeleted = true))
    }
}
