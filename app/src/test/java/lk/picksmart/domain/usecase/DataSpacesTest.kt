package lk.picksmart.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sample account ids only. */
class DataSpacesTest {

    private fun signIn(
        registry: SpaceRegistry,
        uid: String,
        move: Boolean = false,
        hasRecords: Boolean = true,
        backupOwner: String? = null,
        newSpaceId: String = "x1",
    ) = signInSpace(registry, uid, move, hasRecords, backupOwner, newSpaceId)

    @Test
    fun `a new account keeps the records made without one when the rider moves them`() {
        val after = signIn(SpaceRegistry(), "rider-a", move = true)
        assertEquals(SpaceRegistry(active = FIRST_SPACE, owners = mapOf(FIRST_SPACE to "rider-a")), after)
        assertNull(after.noAccountSpace())
    }

    @Test
    fun `a new account starts empty when the rider keeps the records without an account`() {
        val after = signIn(SpaceRegistry(), "rider-a", move = false)
        assertEquals(SpaceRegistry(active = "x1", owners = mapOf("x1" to "rider-a")), after)
        // They come back as no-account records after a sign-out.
        assertEquals(FIRST_SPACE, signOutSpace(after, newSpaceId = "x2").active)
    }

    @Test
    fun `only a new account facing records nobody owns is asked`() {
        val empty = SpaceRegistry()
        assertTrue(asksToMoveRecords(empty, "rider-a", newAccount = true, activeHasRecords = true, activeBackupOwner = null))
        // An account that existed before never takes them.
        assertFalse(asksToMoveRecords(empty, "rider-a", newAccount = false, activeHasRecords = true, activeBackupOwner = null))
        // Nothing to move.
        assertFalse(asksToMoveRecords(empty, "rider-a", newAccount = true, activeHasRecords = false, activeBackupOwner = null))
        // The records on screen already belong to an account.
        val owned = SpaceRegistry(owners = mapOf(FIRST_SPACE to "rider-b"))
        assertFalse(asksToMoveRecords(owned, "rider-a", newAccount = true, activeHasRecords = true, activeBackupOwner = null))
        assertFalse(asksToMoveRecords(empty, "rider-a", newAccount = true, activeHasRecords = true, activeBackupOwner = "rider-b"))
        // The account already has its own space here.
        val hasSpace = SpaceRegistry(owners = mapOf("x1" to "rider-a"))
        assertFalse(asksToMoveRecords(hasSpace, "rider-a", newAccount = true, activeHasRecords = true, activeBackupOwner = null))
    }

    @Test
    fun `an existing account never takes over the records made without it`() {
        val after = signIn(SpaceRegistry(), "rider-a")
        assertEquals(SpaceRegistry(active = "x1", owners = mapOf("x1" to "rider-a")), after)
        assertEquals(FIRST_SPACE, after.noAccountSpace())
    }

    @Test
    fun `an existing account uses the no-account space while it is still empty`() {
        val after = signIn(SpaceRegistry(), "rider-a", hasRecords = false)
        assertEquals(SpaceRegistry(active = FIRST_SPACE, owners = mapOf(FIRST_SPACE to "rider-a")), after)
    }

    @Test
    fun `signing in again goes back to the same records`() {
        val registry = SpaceRegistry(active = FIRST_SPACE, owners = mapOf(FIRST_SPACE to "rider-a"))
        assertEquals(registry, signIn(registry, "rider-a", backupOwner = "rider-a"))
    }

    @Test
    fun `signing out shows the records made without an account again`() {
        val signedIn = signIn(SpaceRegistry(), "rider-a")
        val signedOut = signOutSpace(signedIn, newSpaceId = "x2")
        assertEquals(SpaceRegistry(active = FIRST_SPACE, owners = mapOf("x1" to "rider-a")), signedOut)
        // And signing in again goes back to the account's own records.
        assertEquals("x1", signIn(signedOut, "rider-a").active)
    }

    @Test
    fun `signing out of the account that took over the first space opens an empty no-account space`() {
        val signedIn = signIn(SpaceRegistry(), "rider-a", move = true)
        val signedOut = signOutSpace(signedIn, newSpaceId = "x2")
        assertEquals(SpaceRegistry(active = "x2", owners = mapOf(FIRST_SPACE to "rider-a"), noAccount = "x2"), signedOut)
        // Records made there stay apart from an existing account that signs in next...
        val other = signIn(signedOut, "rider-b", newSpaceId = "x3")
        assertEquals("x3", other.active)
        assertEquals("x2", other.noAccountSpace())
        // ...and come back after it signs out.
        assertEquals("x2", signOutSpace(other, newSpaceId = "x4").active)
    }

    @Test
    fun `a new account made after signing out takes over the no-account space`() {
        val signedOut = SpaceRegistry(active = "x2", owners = mapOf(FIRST_SPACE to "rider-a"), noAccount = "x2")
        val after = signIn(signedOut, "rider-b", move = true, newSpaceId = "x3")
        assertEquals(SpaceRegistry(active = "x2", owners = mapOf(FIRST_SPACE to "rider-a", "x2" to "rider-b")), after)
        assertEquals("x3", signOutSpace(after, newSpaceId = "x3").active)
    }

    @Test
    fun `signing out while already on the no-account space changes nothing`() {
        val registry = SpaceRegistry(active = FIRST_SPACE, owners = mapOf("x1" to "rider-a"))
        assertEquals(registry, signOutSpace(registry, newSpaceId = "x2"))
    }

    @Test
    fun `switching back and forth never mixes the accounts or the no-account records`() {
        var registry = SpaceRegistry()
        registry = signIn(registry, "rider-a", newSpaceId = "x1")
        registry = signOutSpace(registry, "x2")
        registry = signIn(registry, "rider-b", newSpaceId = "x2")
        registry = signOutSpace(registry, "x3")
        registry = signIn(registry, "rider-a", newSpaceId = "x3")
        assertEquals("x1", registry.active)
        registry = signOutSpace(registry, "x3")
        assertEquals(FIRST_SPACE, registry.active)
        assertEquals(mapOf("x1" to "rider-a", "x2" to "rider-b"), registry.owners)
    }

    @Test
    fun `a phone from before spaces knows the owner from its backup`() {
        // Rider A backed up from this phone before the registry existed; B signs in.
        val after = signIn(SpaceRegistry(), "rider-b", backupOwner = "rider-a")
        assertEquals(SpaceRegistry(active = "x1", owners = mapOf(FIRST_SPACE to "rider-a", "x1" to "rider-b")), after)
        // A signs in later and finds their records where they were.
        assertEquals(FIRST_SPACE, signIn(after, "rider-a", backupOwner = "rider-b", newSpaceId = "x2").active)
    }

    @Test
    fun `the owner of a phone from before spaces keeps their records`() {
        val after = signIn(SpaceRegistry(), "rider-a", backupOwner = "rider-a")
        assertEquals(SpaceRegistry(active = FIRST_SPACE, owners = mapOf(FIRST_SPACE to "rider-a")), after)
    }

    @Test
    fun `a deleted account's space is left empty and unowned, others stay`() {
        val registry = SpaceRegistry(active = "x1", owners = mapOf(FIRST_SPACE to "rider-a", "x1" to "rider-b"))
        val after = afterAccountDeleted(registry, "x1")
        assertEquals(SpaceRegistry(active = "x1", owners = mapOf(FIRST_SPACE to "rider-a"), noAccount = "x1"), after)
        // The next account to sign in there, even an existing one, starts from it rather than from A's.
        assertEquals("x1", signIn(after, "rider-c", hasRecords = false, newSpaceId = "x2").active)
        assertEquals(FIRST_SPACE, signIn(after, "rider-a", newSpaceId = "x2").active)
    }

    @Test
    fun `deleting an account brings back the records made without one`() {
        val registry = SpaceRegistry(active = "x1", owners = mapOf("x1" to "rider-a"))
        assertEquals(SpaceRegistry(active = FIRST_SPACE), afterAccountDeleted(registry, "x1"))
    }

    @Test
    fun `a deleted account's space is released even after the sign-out switched away from it`() {
        val signedOut = signOutSpace(SpaceRegistry(active = "x1", owners = mapOf("x1" to "rider-a")), "x2")
        assertEquals(SpaceRegistry(active = FIRST_SPACE), afterAccountDeleted(signedOut, "x1"))
    }

    @Test
    fun `the first space keeps the original database file`() {
        assertEquals("picksmart.db", databaseName(FIRST_SPACE))
        assertEquals("picksmart-x1.db", databaseName("x1"))
    }
}
