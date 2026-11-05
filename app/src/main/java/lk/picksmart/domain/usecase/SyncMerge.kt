package lk.picksmart.domain.usecase

/** What cloud backup does with one record downloaded from the cloud. */
enum class MergeAction {
    /** The copy on this phone is as new or newer; it is uploaded if it is waiting to be. */
    KEEP_LOCAL,

    /** Save the cloud copy here (a new record, or a newer edit from another device). */
    WRITE_REMOTE,

    /** Another device deleted it after this phone's last edit. */
    DELETE_LOCAL,
}

/**
 * Last write wins, by each record's `updatedAt` (CLAUDE.md section 14). The phone's own data is
 * never replaced by an older or empty cloud copy, and a delete is kept as a tombstone so a stale
 * copy can't bring the record back. A tie keeps the phone's copy.
 *
 * @param localUpdatedAt The record's `updatedAt` on this phone, or null when it isn't here.
 * @param localDeletedAt When this phone deleted it, or null when it hasn't.
 * @param remoteUpdatedAt The cloud copy's `updatedAt` (for a delete, when it was deleted).
 */
fun mergeAction(localUpdatedAt: Long?, localDeletedAt: Long?, remoteUpdatedAt: Long, remoteDeleted: Boolean): MergeAction =
    when {
        localUpdatedAt != null ->
            when {
                remoteUpdatedAt <= localUpdatedAt -> MergeAction.KEEP_LOCAL
                remoteDeleted -> MergeAction.DELETE_LOCAL
                else -> MergeAction.WRITE_REMOTE
            }
        remoteDeleted -> MergeAction.KEEP_LOCAL
        localDeletedAt != null && localDeletedAt >= remoteUpdatedAt -> MergeAction.KEEP_LOCAL
        else -> MergeAction.WRITE_REMOTE
    }
