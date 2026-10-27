package lk.picksmart.data.sync

import lk.picksmart.data.local.dao.SyncDao
import lk.picksmart.data.local.entity.SyncKind
import lk.picksmart.data.local.entity.SyncStateEntity
import lk.picksmart.data.local.entity.VehicleEntity
import lk.picksmart.domain.usecase.MergeAction
import lk.picksmart.domain.usecase.VehicleIdentity
import lk.picksmart.domain.usecase.matchVehicle
import lk.picksmart.domain.usecase.mergeAction

/** The settings that follow the rider to a new phone. Null means "not set". */
data class CloudProfile(val mobileNumber: String?, val activeVehicleSyncId: String?) {
    /** Only what is set, so a phone without a value never clears the cloud's. */
    fun toCloud(): Map<String, Any?> = buildMap {
        mobileNumber?.let { put(MOBILE, it) }
        activeVehicleSyncId?.let { put(ACTIVE_VEHICLE, it) }
    }

    companion object {
        private const val MOBILE = "mobileNumber"
        private const val ACTIVE_VEHICLE = "activeVehicleSyncId"

        fun fromCloud(data: Map<String, Any?>) = CloudProfile(data[MOBILE] as? String, data[ACTIVE_VEHICLE] as? String)
    }
}

/** Where the synced settings live on the phone (DataStore). */
interface SyncedSettings {
    suspend fun current(uid: String): CloudProfile

    /** Fills in what this phone doesn't have yet; never replaces a value the rider set here. */
    suspend fun restore(uid: String, profile: CloudProfile)
}

data class SyncReport(val downloaded: Int, val uploaded: Int)

/**
 * One backup run (CLAUDE.md section 14). The phone's database stays the source of truth:
 *
 * 1. If a different rider signed in since the last run, everything here is marked to upload to
 *    the new account ([SyncDao.startOverFor]).
 * 2. Download: the documents written since the last download, vehicles first, merged record by
 *    record with [mergeAction] (last write wins by `updatedAt`, deletes kept as tombstones). The
 *    first download for a rider also restores their settings, and links a backed-up vehicle to
 *    the same vehicle made on this phone rather than adding a copy ([adoptSameVehicle]).
 * 3. Upload: every row and delete still marked pending, then the settings if they changed.
 *
 * A run that fails part-way is safe to repeat: downloads are idempotent and a row stays pending
 * until its upload is confirmed.
 */
class CloudSync(
    private val dao: SyncDao,
    private val cloud: CloudStore,
    private val settings: SyncedSettings,
    private val transaction: suspend (suspend () -> Unit) -> Unit,
) {
    /** Downloads only when [download] is set or nothing was downloaded for this rider yet. */
    suspend fun sync(uid: String, download: Boolean, nowMillis: Long): SyncReport {
        if (dao.state(SyncDao.OWNER_KEY) != uid) dao.startOverFor(uid)
        val firstDownload = dao.state(DOWNLOADED_KEY) == null
        var downloaded = 0
        if (download || firstDownload) {
            for (kind in SyncKind.ALL) downloaded += pull(uid, kind)
            if (firstDownload) {
                cloud.readProfile(uid)?.let { settings.restore(uid, CloudProfile.fromCloud(it)) }
                dao.setState(SyncStateEntity(DOWNLOADED_KEY, "1"))
            }
        }
        val uploaded = push(uid)
        pushSettings(uid)
        dao.setState(SyncStateEntity(LAST_BACKUP_KEY, nowMillis.toString()))
        return SyncReport(downloaded, uploaded)
    }

    /** When this phone last finished a backup, or null if it never did for the signed-in rider. */
    suspend fun lastBackupMillis(): Long? = dao.state(LAST_BACKUP_KEY)?.toLongOrNull()

    /** Removes the rider's cloud data and forgets that this phone was backed up for them. */
    suspend fun deleteCloudData(uid: String) {
        cloud.deleteEverything(uid)
        dao.clearState()
    }

    private suspend fun pull(uid: String, kind: String): Int {
        val key = WATERMARK_PREFIX + kind
        val since = dao.state(key)?.toLongOrNull()
        val documents = cloud.changedSince(uid, kind, since)
        var written = 0
        transaction {
            val vehicleIds = if (kind == SyncKind.VEHICLES) emptyMap() else dao.allVehicles().associate { it.syncId to it.id }
            // The first download lists every vehicle in the backup, so a vehicle here that isn't
            // among them was made on this phone and never backed up to this account.
            val unmatched = if (kind == SyncKind.VEHICLES && since == null) {
                val inBackup = documents.mapTo(HashSet()) { it.id }
                dao.allVehicles().filterTo(mutableListOf()) { it.syncId !in inBackup }
            } else {
                mutableListOf()
            }
            for (document in documents) {
                if (kind == SyncKind.VEHICLES && adoptSameVehicle(document, unmatched)) {
                    written++
                } else if (merge(kind, document, vehicleIds::get)) {
                    written++
                }
            }
        }
        val latest = documents.mapNotNull { it.serverTimeMillis }.maxOrNull()
        dao.setState(SyncStateEntity(key, maxOf(latest ?: 0, since ?: 0).toString()))
        return written
    }

    /** Applies one downloaded document. True when it changed something on the phone. */
    private suspend fun merge(kind: String, document: CloudDocument, vehicleIdFor: (String) -> Long?): Boolean {
        val id = document.id
        val data = document.data
        val remoteUpdatedAt = CloudRecords.updatedAt(data) ?: return false
        val local = localRecord(kind, id)
        val tombstone = dao.tombstone(kind, id)
        val action = mergeAction(local?.updatedAt, tombstone?.deletedAtMillis, remoteUpdatedAt, CloudRecords.isDeleted(data))
        when (action) {
            MergeAction.KEEP_LOCAL -> return false
            MergeAction.DELETE_LOCAL -> when (kind) {
                SyncKind.TRIPS -> dao.deleteTrip(id)
                SyncKind.OTHER_INCOME -> dao.deleteOtherIncome(id)
                SyncKind.FUEL_LOGS -> dao.deleteFuelLog(id)
                SyncKind.EXPENSES -> dao.deleteExpense(id)
                // Vehicles are archived, never deleted.
                else -> return false
            }
            MergeAction.WRITE_REMOTE -> {
                val localId = local?.id ?: 0
                val isNew = local == null
                when (kind) {
                    SyncKind.VEHICLES -> {
                        val row = CloudRecords.vehicle(id, data, localId) ?: return false
                        if (isNew) dao.insertVehicle(row) else dao.updateVehicle(row)
                    }
                    SyncKind.TRIPS -> {
                        val row = CloudRecords.trip(id, data, localId, vehicleIdFor) ?: return false
                        if (isNew) dao.insertTrip(row) else dao.updateTrip(row)
                    }
                    SyncKind.OTHER_INCOME -> {
                        val row = CloudRecords.otherIncome(id, data, localId) ?: return false
                        if (isNew) dao.insertOtherIncome(row) else dao.updateOtherIncome(row)
                    }
                    SyncKind.FUEL_LOGS -> {
                        val row = CloudRecords.fuelLog(id, data, localId, vehicleIdFor) ?: return false
                        if (isNew) dao.insertFuelLog(row) else dao.updateFuelLog(row)
                    }
                    SyncKind.EXPENSES -> {
                        val row = CloudRecords.expense(id, data, localId, vehicleIdFor) ?: return false
                        if (isNew) dao.insertExpense(row) else dao.updateExpense(row)
                    }
                    else -> return false
                }
                if (tombstone != null) dao.deleteTombstone(kind, id)
            }
        }
        return true
    }

    /**
     * A backed-up vehicle the phone doesn't have yet may be one the rider made here before signing
     * in, such as the same bike (`matchVehicle`: the plate, or the name and type). Then that vehicle
     * takes the backup's sync id instead of a second copy being added, so the backup's trips, fuel
     * logs and expenses find it (owner decision 2026-10-27). Its values follow last write wins like
     * any record; when the phone's are newer, it uploads them over the backup's. True when [document]
     * was used this way.
     */
    private suspend fun adoptSameVehicle(document: CloudDocument, unmatched: MutableList<VehicleEntity>): Boolean {
        if (unmatched.isEmpty() || CloudRecords.isDeleted(document.data)) return false
        if (dao.vehicle(document.id) != null || dao.tombstone(SyncKind.VEHICLES, document.id) != null) return false
        val remoteUpdatedAt = CloudRecords.updatedAt(document.data) ?: return false
        val remote = CloudRecords.vehicle(document.id, document.data, 0) ?: return false
        val same = matchVehicle(remote.identity(), unmatched) { it.identity() } ?: return false
        unmatched.remove(same)
        val row = if (remoteUpdatedAt > same.updatedAtMillis) {
            remote.copy(id = same.id)
        } else {
            same.copy(syncId = document.id, syncPending = true)
        }
        dao.updateVehicle(row)
        return true
    }

    private fun VehicleEntity.identity() = VehicleIdentity(name, type, plateNumber)

    private class LocalRecord(val id: Long, val updatedAt: Long)

    private suspend fun localRecord(kind: String, syncId: String): LocalRecord? = when (kind) {
        SyncKind.VEHICLES -> dao.vehicle(syncId)?.let { LocalRecord(it.id, it.updatedAtMillis) }
        SyncKind.TRIPS -> dao.trip(syncId)?.let { LocalRecord(it.id, it.updatedAtMillis) }
        SyncKind.OTHER_INCOME -> dao.otherIncome(syncId)?.let { LocalRecord(it.id, it.updatedAtMillis) }
        SyncKind.FUEL_LOGS -> dao.fuelLog(syncId)?.let { LocalRecord(it.id, it.updatedAtMillis) }
        SyncKind.EXPENSES -> dao.expense(syncId)?.let { LocalRecord(it.id, it.updatedAtMillis) }
        else -> null
    }

    private suspend fun push(uid: String): Int {
        var uploaded = 0

        val vehicles = dao.pendingVehicles()
        if (vehicles.isNotEmpty()) {
            cloud.write(uid, SyncKind.VEHICLES, vehicles.associate { it.syncId to CloudRecords.vehicle(it) })
            vehicles.forEach { dao.markVehicleBackedUp(it.syncId, it.updatedAtMillis) }
            uploaded += vehicles.size
        }
        val vehicleSyncIds = dao.allVehicles().associate { it.id to it.syncId }

        // A record whose vehicle isn't on the phone can't be described to another device; it stays
        // pending rather than reaching the cloud without one.
        val trips = dao.pendingTrips().filter { it.vehicleId in vehicleSyncIds }
        if (trips.isNotEmpty()) {
            cloud.write(uid, SyncKind.TRIPS, trips.associate { it.syncId to CloudRecords.trip(it, vehicleSyncIds.getValue(it.vehicleId)) })
            trips.forEach { dao.markTripBackedUp(it.syncId, it.updatedAtMillis) }
            uploaded += trips.size
        }

        val income = dao.pendingOtherIncome()
        if (income.isNotEmpty()) {
            cloud.write(uid, SyncKind.OTHER_INCOME, income.associate { it.syncId to CloudRecords.otherIncome(it) })
            income.forEach { dao.markOtherIncomeBackedUp(it.syncId, it.updatedAtMillis) }
            uploaded += income.size
        }

        val fuelLogs = dao.pendingFuelLogs().filter { it.vehicleId in vehicleSyncIds }
        if (fuelLogs.isNotEmpty()) {
            cloud.write(uid, SyncKind.FUEL_LOGS, fuelLogs.associate { it.syncId to CloudRecords.fuelLog(it, vehicleSyncIds.getValue(it.vehicleId)) })
            fuelLogs.forEach { dao.markFuelLogBackedUp(it.syncId, it.updatedAtMillis) }
            uploaded += fuelLogs.size
        }

        val expenses = dao.pendingExpenses()
        if (expenses.isNotEmpty()) {
            // An expense may have no vehicle; one pointing at a vehicle that is gone is kept without it.
            cloud.write(uid, SyncKind.EXPENSES, expenses.associate { it.syncId to CloudRecords.expense(it, it.vehicleId?.let(vehicleSyncIds::get)) })
            expenses.forEach { dao.markExpenseBackedUp(it.syncId, it.updatedAtMillis) }
            uploaded += expenses.size
        }

        for ((kind, tombstones) in dao.pendingTombstones().groupBy { it.kind }) {
            cloud.write(uid, kind, tombstones.associate { it.syncId to CloudRecords.deleted(it.deletedAtMillis) })
            tombstones.forEach { dao.markTombstoneBackedUp(kind, it.syncId, it.deletedAtMillis) }
            uploaded += tombstones.size
        }
        return uploaded
    }

    private suspend fun pushSettings(uid: String) {
        val data = settings.current(uid).toCloud()
        val sent = data.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}=${it.value}" }
        if (data.isEmpty() || dao.state(SETTINGS_SENT_KEY) == sent) return
        cloud.writeProfile(uid, data)
        dao.setState(SyncStateEntity(SETTINGS_SENT_KEY, sent))
    }

    private companion object {
        const val WATERMARK_PREFIX = "downloaded_until_"
        const val DOWNLOADED_KEY = "downloaded_once"
        const val SETTINGS_SENT_KEY = "settings_sent"
        const val LAST_BACKUP_KEY = "last_backup_at"
    }
}
