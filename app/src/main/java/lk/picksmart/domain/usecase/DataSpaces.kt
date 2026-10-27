package lk.picksmart.domain.usecase

/**
 * Which records the app shows, when several riders sign in on one phone (owner decision
 * 2026-10-27). Each account keeps its own records in its own "space": a Room database plus the
 * settings that go with it (active vehicle, last Check a Ride inputs, setup). Two accounts on one
 * phone never see, mix or back up each other's records, and the app never asks whose they are.
 *
 * Space `""` is the phone's first one, the original `picksmart.db`, so an existing rider's data
 * stays exactly where it was. A space with no owner holds records made without an account.
 *
 * @property active The space the app shows now.
 * @property owners Space id → the account whose records it holds.
 */
data class SpaceRegistry(val active: String = FIRST_SPACE, val owners: Map<String, String> = emptyMap()) {
    fun spaceOf(uid: String): String? = owners.entries.firstOrNull { it.value == uid }?.key
}

const val FIRST_SPACE = ""

/**
 * The registry once [uid] has signed in:
 *
 * - An account that already has a space here goes back to it, with everything it had.
 * - Otherwise the space on screen becomes theirs if nobody owns it yet (the records made without
 *   an account are kept and backed up, CLAUDE.md section 14), or if its backup already belongs to
 *   [uid] ([activeBackupOwner], for phones from before spaces existed).
 * - Otherwise [uid] gets the new, empty space [newSpaceId], which their backup then fills. The
 *   earlier owner of the space on screen is remembered, so they come back to it.
 *
 * Signing out changes nothing: the records stay on screen and on the phone.
 */
fun signInSpace(registry: SpaceRegistry, uid: String, activeBackupOwner: String?, newSpaceId: String): SpaceRegistry {
    registry.spaceOf(uid)?.let { return registry.copy(active = it) }
    val activeOwner = registry.owners[registry.active] ?: activeBackupOwner
    if (activeOwner == null || activeOwner == uid) {
        return registry.copy(owners = registry.owners + (registry.active to uid))
    }
    require(newSpaceId != registry.active && newSpaceId !in registry.owners) { "Space $newSpaceId is in use" }
    return SpaceRegistry(
        active = newSpaceId,
        owners = registry.owners + (registry.active to activeOwner) + (newSpaceId to uid),
    )
}

/**
 * The registry once the account owning the space on screen is deleted and the space erased: it
 * stays on screen, empty and owned by nobody, so the app starts again at the welcome screen.
 * Other accounts' spaces are untouched.
 */
fun afterAccountDeleted(registry: SpaceRegistry): SpaceRegistry =
    registry.copy(owners = registry.owners - registry.active)

/** The Room database file of [space]. The first space keeps the original name (CLAUDE.md N7). */
fun databaseName(space: String): String = if (space == FIRST_SPACE) "picksmart.db" else "picksmart-$space.db"
