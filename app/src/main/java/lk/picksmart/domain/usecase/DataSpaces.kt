package lk.picksmart.domain.usecase

/**
 * Which records the app shows, when several riders sign in on one phone (owner decisions
 * 2026-10-27). Each account keeps its own records in its own "space": a Room database plus the
 * settings that go with it (active vehicle, last Check a Ride inputs, setup). Two accounts on one
 * phone never see, mix or back up each other's records.
 * Records made without an account are a space of their own too, owned by nobody, shown whenever
 * nobody is signed in. They go into an account only when its rider chooses to move them there
 * ([asksToMoveRecords]).
 *
 * Space `""` is the phone's first one, the original `picksmart.db`, so an existing rider's data
 * stays exactly where it was.
 *
 * @property active The space the app shows now.
 * @property owners Space id → the account whose records it holds.
 * @property noAccount The space for records made without an account, once it isn't the first
 *   space (that one became an account's); null means the first space, while nobody owns it.
 */
data class SpaceRegistry(
    val active: String = FIRST_SPACE,
    val owners: Map<String, String> = emptyMap(),
    val noAccount: String? = null,
) {
    fun spaceOf(uid: String): String? = owners.entries.firstOrNull { it.value == uid }?.key

    /** The space shown while nobody is signed in, or null when it hasn't been made yet. */
    fun noAccountSpace(): String? =
        noAccount?.takeIf { it !in owners } ?: FIRST_SPACE.takeIf { it !in owners }
}

const val FIRST_SPACE = ""

/**
 * Whether to ask [uid] if the records made without an account should move to their account (owner
 * decisions 2026-10-27): when the account has no space of its own on this phone yet, while the
 * space on screen belongs to nobody and has records. A new account starts with them; an existing
 * one merges them with its backup. The answer goes to [signInSpace] as `moveNoAccountRecords`;
 * every other sign-in needs no question.
 */
fun asksToMoveRecords(
    registry: SpaceRegistry,
    uid: String,
    activeHasRecords: Boolean,
    activeBackupOwner: String?,
): Boolean = activeHasRecords && registry.spaceOf(uid) == null &&
    (registry.owners[registry.active] ?: activeBackupOwner) == null

/** The question [asksToMoveRecords] puts: a new account takes the records, an existing one adds them to its backup. */
enum class MoveRecordsQuestion { NEW_ACCOUNT, EXISTING_ACCOUNT }

/**
 * The registry once [uid] has signed in:
 *
 * - An account that already has a space here goes back to it, with everything it had.
 * - Otherwise the space on screen becomes theirs if its backup already belongs to [uid]
 *   ([activeBackupOwner], for phones from before spaces existed), or if nobody owns it and either
 *   [moveNoAccountRecords] (the rider chose to move the records made without an account to it,
 *   [asksToMoveRecords]; an existing account's backup then merges into them) or it has no records
 *   yet ([activeHasRecords]).
 * - Otherwise [uid] gets the new, empty space [newSpaceId], which their backup then fills. The
 *   space on screen keeps its owner, or stays the no-account space, so records made without an
 *   account never go into an account unless its rider chose that.
 */
fun signInSpace(
    registry: SpaceRegistry,
    uid: String,
    moveNoAccountRecords: Boolean,
    activeHasRecords: Boolean,
    activeBackupOwner: String?,
    newSpaceId: String,
): SpaceRegistry {
    registry.spaceOf(uid)?.let { return registry.copy(active = it) }
    val active = registry.active
    val activeOwner = registry.owners[active] ?: activeBackupOwner
    if (activeOwner == uid || (activeOwner == null && (moveNoAccountRecords || !activeHasRecords))) {
        return registry.copy(owners = registry.owners + (active to uid), noAccount = registry.noAccount.takeIf { it != active })
    }
    require(newSpaceId != active && newSpaceId !in registry.owners) { "Space $newSpaceId is in use" }
    return if (activeOwner == null) {
        registry.copy(active = newSpaceId, owners = registry.owners + (newSpaceId to uid), noAccount = active.takeIf { it != FIRST_SPACE })
    } else {
        registry.copy(active = newSpaceId, owners = registry.owners + (active to activeOwner) + (newSpaceId to uid))
    }
}

/**
 * The registry once the rider has signed out: the records made without an account come back on
 * screen ([newSpaceId], empty, when there are none yet), so nothing added while signed out goes
 * into an account. The account's records stay on the phone and come back when it signs in again.
 */
fun signOutSpace(registry: SpaceRegistry, newSpaceId: String): SpaceRegistry {
    if (registry.active !in registry.owners) return registry
    val target = registry.noAccountSpace() ?: newSpaceId
    require(target !in registry.owners) { "Space $target is in use" }
    return registry.copy(active = target, noAccount = target.takeIf { it != FIRST_SPACE })
}

/**
 * The registry once the account owning [space] is deleted and the space erased. It belongs to
 * nobody now, so a new account made on this phone never shows the old records. If it was on
 * screen, the records made without an account come back instead (the erased space stays, as the
 * no-account space, when there are none). Other accounts' spaces are untouched.
 */
fun afterAccountDeleted(registry: SpaceRegistry, space: String): SpaceRegistry {
    val released = registry.copy(owners = registry.owners - space)
    if (released.active != space) return released
    val target = registry.noAccountSpace() ?: space
    return released.copy(active = target, noAccount = target.takeIf { it != FIRST_SPACE })
}

/** The Room database file of [space]. The first space keeps the original name (CLAUDE.md N7). */
fun databaseName(space: String): String = if (space == FIRST_SPACE) "picksmart.db" else "picksmart-$space.db"
