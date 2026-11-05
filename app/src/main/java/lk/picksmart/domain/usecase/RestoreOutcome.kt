package lk.picksmart.domain.usecase

/** Where setup goes once a signed-in rider's backup has finished downloading into an empty space. */
enum class RestoreOutcome {
    /** The backup brought back a vehicle in use: setup needs no new one. */
    RESTORED,

    /** The account has no backup: the rider is told so, then sets up a vehicle (owner request 2026-10-27). */
    NO_BACKUP,

    /** Straight on to the vehicle: a new account, or a backup with records but no vehicle in use. */
    SET_UP_VEHICLE,
}

/**
 * Decides [RestoreOutcome] from what the restore left in the space. Setup starts from an empty
 * space, so [hasRecords] false means the backup had nothing. A new account is never told it has no
 * backup, since it couldn't have one yet.
 */
fun restoreOutcome(newAccount: Boolean, hasRecords: Boolean, hasVehicle: Boolean): RestoreOutcome = when {
    hasVehicle -> RestoreOutcome.RESTORED
    !hasRecords && !newAccount -> RestoreOutcome.NO_BACKUP
    else -> RestoreOutcome.SET_UP_VEHICLE
}
