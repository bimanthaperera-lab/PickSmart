package lk.picksmart.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

/** Sample account ids only. */
class DataSpacesTest {

    @Test
    fun `the first account on a phone keeps the records made without one`() {
        val after = signInSpace(SpaceRegistry(), "rider-a", activeBackupOwner = null, newSpaceId = "x1")
        assertEquals(SpaceRegistry(active = FIRST_SPACE, owners = mapOf(FIRST_SPACE to "rider-a")), after)
    }

    @Test
    fun `signing in again goes back to the same records`() {
        val registry = SpaceRegistry(active = FIRST_SPACE, owners = mapOf(FIRST_SPACE to "rider-a"))
        assertEquals(registry, signInSpace(registry, "rider-a", activeBackupOwner = "rider-a", newSpaceId = "x1"))
    }

    @Test
    fun `a second account gets its own empty space, and the first is remembered`() {
        val registry = SpaceRegistry(active = FIRST_SPACE, owners = mapOf(FIRST_SPACE to "rider-a"))
        val after = signInSpace(registry, "rider-b", activeBackupOwner = "rider-a", newSpaceId = "x1")
        assertEquals(SpaceRegistry(active = "x1", owners = mapOf(FIRST_SPACE to "rider-a", "x1" to "rider-b")), after)
    }

    @Test
    fun `switching back and forth never mixes the two accounts`() {
        var registry = SpaceRegistry()
        registry = signInSpace(registry, "rider-a", null, "x1")
        registry = signInSpace(registry, "rider-b", "rider-a", "x1")
        registry = signInSpace(registry, "rider-a", "rider-b", "x2")
        assertEquals(FIRST_SPACE, registry.active)
        registry = signInSpace(registry, "rider-b", "rider-a", "x3")
        assertEquals("x1", registry.active)
        assertEquals(mapOf(FIRST_SPACE to "rider-a", "x1" to "rider-b"), registry.owners)
    }

    @Test
    fun `a phone from before spaces knows the owner from its backup`() {
        // Rider A backed up from this phone before the registry existed; B signs in.
        val after = signInSpace(SpaceRegistry(), "rider-b", activeBackupOwner = "rider-a", newSpaceId = "x1")
        assertEquals(SpaceRegistry(active = "x1", owners = mapOf(FIRST_SPACE to "rider-a", "x1" to "rider-b")), after)
        // A signs in later and finds their records where they were.
        assertEquals(FIRST_SPACE, signInSpace(after, "rider-a", activeBackupOwner = "rider-b", newSpaceId = "x2").active)
    }

    @Test
    fun `the owner of a phone from before spaces keeps their records`() {
        val after = signInSpace(SpaceRegistry(), "rider-a", activeBackupOwner = "rider-a", newSpaceId = "x1")
        assertEquals(SpaceRegistry(active = FIRST_SPACE, owners = mapOf(FIRST_SPACE to "rider-a")), after)
    }

    @Test
    fun `a deleted account's space is left empty and unowned, others stay`() {
        val registry = SpaceRegistry(active = "x1", owners = mapOf(FIRST_SPACE to "rider-a", "x1" to "rider-b"))
        val after = afterAccountDeleted(registry)
        assertEquals(SpaceRegistry(active = "x1", owners = mapOf(FIRST_SPACE to "rider-a")), after)
        // The next account to sign in there, even a new one, starts from it rather than from A's.
        assertEquals("x1", signInSpace(after, "rider-c", activeBackupOwner = null, newSpaceId = "x2").active)
        assertEquals(FIRST_SPACE, signInSpace(after, "rider-a", activeBackupOwner = null, newSpaceId = "x2").active)
    }

    @Test
    fun `the first space keeps the original database file`() {
        assertEquals("picksmart.db", databaseName(FIRST_SPACE))
        assertEquals("picksmart-x1.db", databaseName("x1"))
    }
}
