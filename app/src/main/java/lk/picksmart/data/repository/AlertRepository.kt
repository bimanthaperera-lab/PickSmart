package lk.picksmart.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import lk.picksmart.data.local.dao.AlertDao
import lk.picksmart.data.local.entity.toDomain
import lk.picksmart.data.local.entity.toEntity
import lk.picksmart.domain.model.Alert
import lk.picksmart.domain.usecase.alertCutoffMillis
import java.time.Clock

/** The in-app alerts inbox (CLAUDE.md section 9a), kept on the phone for 30 days. */
class AlertRepository(
    private val dao: AlertDao,
    private val clock: Clock,
) {
    /** Every alert, newest first. */
    val all: Flow<List<Alert>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    val unreadCount: Flow<Int> = dao.observeUnreadCount()

    /** Adds [alert] unless one with the same key is already there. Returns true when it was added. */
    suspend fun add(alert: Alert): Boolean = dao.insert(alert.copy(id = 0).toEntity()) != -1L

    /** Opening the inbox marks every alert read. */
    suspend fun markAllRead() = dao.markAllRead()

    /** Deletes alerts older than 30 days. */
    suspend fun deleteOld() {
        dao.deleteOlderThan(alertCutoffMillis(clock.millis()))
    }
}
