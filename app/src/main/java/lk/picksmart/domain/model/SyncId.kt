package lk.picksmart.domain.model

import java.util.UUID

/**
 * A new id for a record, the same on every device the rider backs up to (CLAUDE.md section 14).
 * Room's numeric ids differ per phone, so cloud backup matches records by this instead.
 */
fun newSyncId(): String = UUID.randomUUID().toString().replace("-", "")
