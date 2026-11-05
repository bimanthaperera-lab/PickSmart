package lk.picksmart.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock

/** What the Account tab says about cloud backup. */
sealed interface SyncStatus {
    /** Signed out, or a build without Firebase: nothing leaves the phone. */
    data object Off : SyncStatus

    data object Running : SyncStatus

    /** [atMillis] is null before the first backup for this rider has finished. */
    data class Idle(val atMillis: Long?) : SyncStatus

    data class Stopped(val problem: SyncProblem, val lastAtMillis: Long?) : SyncStatus

    /** The rider skipped restoring and started fresh: signed in, but not backing up on this phone. */
    data object Paused : SyncStatus
}

/**
 * Optional cloud backup (CLAUDE.md section 14). It runs in the background while a rider is signed
 * in and never blocks a screen; the app works the same with [NoOpSyncProvider].
 */
interface SyncProvider {
    val status: StateFlow<SyncStatus>

    /** Rows and deletes on the phone not backed up yet. */
    val pendingCount: Flow<Int>

    /** Backs up now, downloading other devices' changes too. Returns the status it ended in. */
    suspend fun syncNow(): SyncStatus

    /** Backs up a change that isn't a record (the rider's personal details or photo) shortly. */
    fun backUpSoon()

    /**
     * The rider skipped restoring to start fresh (owner decision 2026-10-27): backup stops for these
     * records on this phone, and what the restore had downloaded is dropped (it stays in the cloud).
     */
    suspend fun startFresh()

    /** Turns backup on again after [startFresh]; the next run brings the backup back and merges it in. */
    fun resume()

    /**
     * Removes the signed-in rider's cloud data. Null when it is gone (or there was none), else why
     * it couldn't be removed, so the rider is told the real reason rather than always "offline".
     */
    suspend fun deleteCloudData(): SyncProblem?
}

object NoOpSyncProvider : SyncProvider {
    override val status: StateFlow<SyncStatus> = MutableStateFlow(SyncStatus.Off).asStateFlow()
    override val pendingCount: Flow<Int> = flowOf(0)
    override suspend fun syncNow(): SyncStatus = SyncStatus.Off
    override fun backUpSoon() = Unit
    override suspend fun startFresh() = Unit
    override fun resume() = Unit
    override suspend fun deleteCloudData(): SyncProblem? = SyncProblem.FAILED
}

/**
 * Runs [CloudSync] for the signed-in rider. [signedIn] is the account whose records this space
 * holds while that account is signed in, else null (`DataSpaces.kt`), so one account's records are
 * never backed up to another's.
 *
 * - a full run (download and upload) when a rider signs in, when the app starts, when the phone
 *   comes back online after a run failed, and when the rider asks;
 * - an upload-only run [UPLOAD_DELAY_MILLIS] after the rider saves or deletes something, so a
 *   busy shift doesn't spend a cloud read on every trip.
 *
 * Runs never overlap. A failure only changes [status]; local use is never affected.
 */
class CloudSyncProvider(
    private val signedIn: Flow<String?>,
    private val sync: CloudSync,
    pending: Flow<Int>,
    private val clock: Clock,
    private val scope: CoroutineScope,
    context: Context,
    /** True while the rider has backup off for these records after starting fresh ([startFresh]). */
    private val paused: Flow<Boolean>,
    private val setPaused: suspend (Boolean) -> Unit,
) : SyncProvider {

    private val mutableStatus = MutableStateFlow<SyncStatus>(SyncStatus.Off)
    override val status: StateFlow<SyncStatus> = mutableStatus.asStateFlow()

    override val pendingCount: Flow<Int> = pending

    private val running = Mutex()
    private var scheduledUpload: Job? = null
    private var lastFullRunMillis = 0L

    init {
        scope.launch {
            combine(signedIn, paused, ::Pair).distinctUntilChanged().collect { (uid, isPaused) ->
                when {
                    uid == null -> {
                        scheduledUpload?.cancel()
                        mutableStatus.value = SyncStatus.Off
                    }
                    isPaused -> {
                        scheduledUpload?.cancel()
                        mutableStatus.value = SyncStatus.Paused
                    }
                    else -> {
                        mutableStatus.value = SyncStatus.Idle(sync.lastBackupMillis())
                        launch { run(download = true) }
                    }
                }
            }
        }
        scope.launch {
            pending.distinctUntilChanged().collect { count -> if (count > 0) scheduleUpload() }
        }
        context.getSystemService(ConnectivityManager::class.java)?.let { connectivity ->
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if ((status.value as? SyncStatus.Stopped)?.problem == SyncProblem.OFFLINE) {
                        scope.launch { run(download = true) }
                    }
                }
            }
            runCatching {
                connectivity.registerDefaultNetworkCallback(callback)
                // The scope ends when the app switches to another account's records.
                scope.coroutineContext[Job]?.invokeOnCompletion { runCatching { connectivity.unregisterNetworkCallback(callback) } }
            }
        }
    }

    override suspend fun syncNow(): SyncStatus = run(download = true, force = true)

    override fun backUpSoon() {
        if (status.value != SyncStatus.Off && status.value != SyncStatus.Paused) scheduleUpload()
    }

    override suspend fun startFresh() {
        setPaused(true)
        scheduledUpload?.cancel()
        // Waits for a run already under way, so nothing it downloads is left behind.
        running.withLock { sync.discardRestored() }
        mutableStatus.value = SyncStatus.Paused
    }

    override fun resume() {
        scope.launch { setPaused(false) }
    }

    override suspend fun deleteCloudData(): SyncProblem? {
        val uid = signedIn.first() ?: return SyncProblem.REFUSED
        scheduledUpload?.cancel()
        return running.withLock {
            try {
                sync.deleteCloudData(uid)
                null
            } catch (e: CloudException) {
                e.problem
            }
        }
    }

    private fun scheduleUpload() {
        scheduledUpload?.cancel()
        scheduledUpload = scope.launch {
            delay(UPLOAD_DELAY_MILLIS)
            // Started apart from this job, so a newer save rescheduling it can't cancel a run midway.
            scope.launch { run(download = false) }
        }
    }

    private suspend fun run(download: Boolean, force: Boolean = false): SyncStatus = running.withLock {
        val uid = signedIn.first() ?: return@withLock SyncStatus.Off.also { mutableStatus.value = it }
        if (paused.first()) return@withLock SyncStatus.Paused.also { mutableStatus.value = it }
        val now = clock.millis()
        // App start, sign-in and reconnecting can arrive together; one download covers them, and
        // the others only upload.
        val downloadNow = download && (force || now - lastFullRunMillis >= MIN_FULL_RUN_GAP_MILLIS)
        mutableStatus.value = SyncStatus.Running
        val result = try {
            sync.sync(uid, downloadNow, now)
            if (downloadNow) lastFullRunMillis = now
            SyncStatus.Idle(now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: CloudException) {
            SyncStatus.Stopped(e.problem, sync.lastBackupMillis())
        } catch (e: Exception) {
            // A local database problem: report it, and leave the records as they are.
            SyncStatus.Stopped(SyncProblem.FAILED, sync.lastBackupMillis())
        }
        // Signed out while it ran: say so rather than reporting a backup.
        mutableStatus.value = if (signedIn.first() == uid) result else SyncStatus.Off
        mutableStatus.value
    }

    private companion object {
        const val UPLOAD_DELAY_MILLIS = 20_000L
        const val MIN_FULL_RUN_GAP_MILLIS = 60_000L
    }
}
