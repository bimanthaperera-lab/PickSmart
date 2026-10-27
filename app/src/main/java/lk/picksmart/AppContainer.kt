package lk.picksmart

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import java.io.File
import java.time.Clock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import lk.picksmart.data.account.AccountProvider
import lk.picksmart.data.account.AppCheck
import lk.picksmart.data.account.FirebaseAccountProvider
import lk.picksmart.data.account.NoOpAccountProvider
import lk.picksmart.data.account.ProfilePhotoCache
import lk.picksmart.data.export.CsvExporter
import lk.picksmart.data.local.PhoneDataEraser
import lk.picksmart.data.local.PicksmartDatabase
import lk.picksmart.data.local.SpaceRegistryStore
import lk.picksmart.data.local.dao.SyncDao
import lk.picksmart.data.preferences.UserPreferencesRepository
import lk.picksmart.data.remote.FuelPriceSource
import lk.picksmart.data.remote.HttpsFuelPriceFetcher
import lk.picksmart.data.repository.ExpenseRepository
import lk.picksmart.data.repository.FuelLogRepository
import lk.picksmart.data.repository.OtherIncomeRepository
import lk.picksmart.data.repository.TripRepository
import lk.picksmart.data.repository.VehicleRepository
import lk.picksmart.data.sync.CloudSync
import lk.picksmart.data.sync.CloudSyncProvider
import lk.picksmart.data.sync.DataStoreSyncedSettings
import lk.picksmart.data.sync.FirestoreCloudStore
import lk.picksmart.data.sync.NoOpSyncProvider
import lk.picksmart.data.sync.SyncProvider
import lk.picksmart.domain.calculator.ProfitCalculator
import lk.picksmart.domain.model.UserProfile
import lk.picksmart.domain.model.ThemeMode
import lk.picksmart.domain.usecase.CompleteOnboarding
import lk.picksmart.domain.usecase.DeleteAccount
import lk.picksmart.domain.usecase.FuelPriceUpdates
import lk.picksmart.domain.usecase.MoveRecordsQuestion
import lk.picksmart.domain.usecase.SpaceRegistry
import lk.picksmart.domain.usecase.afterAccountDeleted
import lk.picksmart.domain.usecase.asksToMoveRecords
import lk.picksmart.domain.usecase.databaseName
import lk.picksmart.domain.usecase.signInSpace
import lk.picksmart.domain.usecase.signOutSpace

// One DataStore per process. The file lives under files/datastore/, which Auto Backup includes.
private val Context.userPreferencesStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

/**
 * Manual dependency injection (CLAUDE.md section 3): the single place that builds the app's
 * long-lived objects. Created once by [PicksmartApplication]; ViewModels get what they need
 * from it through their factories.
 *
 * What belongs to the phone (account, fuel prices, photos, theme) lives here. A rider's records
 * live in a [DataSpace], one per account on the phone (`DataSpaces.kt`); [space] is the one on
 * screen, and the record properties below always read from it. When another account signs in,
 * or the rider signs out, [space] changes and MainActivity starts again, so every screen is built
 * on the new records.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Wall-clock time for record timestamps. Dates shown to riders use Asia/Colombo. */
    val clock: Clock = Clock.systemUTC()

    /** Work that should finish even if the screen that started it closes, such as a price check. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val preferencesStore = appContext.userPreferencesStore

    /** The phone's own settings (theme, fuel prices); per-space settings go through [userPreferences]. */
    private val phonePreferences = UserPreferencesRepository(preferencesStore)

    private val spaceRegistry = SpaceRegistryStore(appContext.getSharedPreferences(SpaceRegistryStore.FILE, Context.MODE_PRIVATE))

    /** Serializes switching spaces and deleting an account, which both change the registry. */
    private val spaceLock = Mutex()

    /** Open databases by file name. A file is opened once and stays open, so a switch back reuses it. */
    private val databases = mutableMapOf<String, PicksmartDatabase>()

    /** The space and the account signed in to it; null while nobody who owns it is signed in. */
    private val signedInSpace = MutableStateFlow<SignedInSpace?>(null)

    private val mutableSpace = MutableStateFlow(openSpace(spaceRegistry.load().active))

    /** The rider's records on screen (`DataSpaces.kt`). */
    val space: StateFlow<DataSpace> = mutableSpace.asStateFlow()

    @Volatile private var moveRecordsAnswer: CompletableDeferred<Boolean>? = null
    private val mutableMoveRecordsQuestion = MutableStateFlow<MoveRecordsQuestion?>(null)

    /**
     * Set while an account that just signed in waits for the rider to say whether the records made
     * without an account move to it ([asksToMoveRecords]); answered with [answerMoveRecords].
     */
    val moveRecordsQuestion: StateFlow<MoveRecordsQuestion?> = mutableMoveRecordsQuestion.asStateFlow()

    val userPreferences: UserPreferencesRepository get() = space.value.userPreferences
    val vehicles: VehicleRepository get() = space.value.vehicles
    val trips: TripRepository get() = space.value.trips
    val otherIncome: OtherIncomeRepository get() = space.value.otherIncome
    val fuelLogs: FuelLogRepository get() = space.value.fuelLogs
    val expenses: ExpenseRepository get() = space.value.expenses
    val completeOnboarding: CompleteOnboarding get() = space.value.completeOnboarding
    val csvExporter: CsvExporter get() = space.value.csvExporter
    val fuelPriceUpdates: FuelPriceUpdates get() = space.value.fuelPriceUpdates

    /**
     * Optional cloud backup (CLAUDE.md section 14) of the space on screen: only with an account,
     * and it starts with the app ([start]). It runs in the background and never blocks a screen (N9).
     */
    val sync: SyncProvider get() = space.value.sync

    /** Deletes the cloud backup, the account and the rider's data on this phone (CLAUDE.md section 14). */
    val deleteAccount: DeleteAccount get() = space.value.deleteAccount

    val profitCalculator: ProfitCalculator by lazy { ProfitCalculator() }

    /**
     * The theme and setup flag MainActivity waits for, kept loaded while the app runs. A screen
     * recreated later (below Android 13, a language change does that) has them on its first frame
     * instead of drawing a blank one while DataStore loads. Setup belongs to the space on screen.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val startupSettings: StateFlow<StartupSettings?> by lazy {
        combine(phonePreferences.themeMode, space.flatMapLatest { it.userPreferences.onboardingComplete }, ::StartupSettings)
            .stateIn(applicationScope, SharingStarted.Eagerly, null)
    }

    val fuelPriceSource: FuelPriceSource by lazy {
        val url = BuildConfig.FUEL_PRICE_URL.takeIf { it.isNotBlank() }
        FuelPriceSource(url?.let(::HttpsFuelPriceFetcher), phonePreferences, clock)
    }

    /**
     * The optional account (CLAUDE.md section 14). Firebase only when the build has
     * `google-services.json` and it started; otherwise the app runs signed out with no sign-in.
     * App Check is installed first, so every Firebase request after it carries a token.
     */
    val account: AccountProvider by lazy {
        if (BuildConfig.FIREBASE_ENABLED && FirebaseApp.getApps(appContext).isNotEmpty()) {
            AppCheck.install(FirebaseApp.getInstance())
            FirebaseAccountProvider(FirebaseAuth.getInstance(), appContext)
        } else {
            NoOpAccountProvider
        }
    }

    private val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance().apply {
            // Firestore's own cache only holds writes waiting for the network; the records live in Room.
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder().setSizeBytes(FIRESTORE_CACHE_BYTES).build())
                .build()
        }
    }

    val profilePhotos: ProfilePhotoCache by lazy { ProfilePhotoCache(appContext.filesDir.resolve("profile_photo")) }

    /**
     * Starts following the account: each sign-in puts that account's records on screen
     * ([signInSpace]), then cloud backup starts for them. Called once, from [PicksmartApplication].
     */
    fun start() {
        applicationScope.launch {
            var signedInBefore = false
            account.profile.distinctUntilChangedBy { it?.uid }.collect { profile ->
                // Only a sign-out seen while the app runs switches spaces; a rider who was already
                // signed out when the app started (an update from before) keeps the records shown.
                onAccountChanged(profile, signedOutNow = profile == null && signedInBefore)
                signedInBefore = profile != null
            }
        }
    }

    /**
     * Checks the published fuel price in the background (at most every 12 hours), then applies it
     * to a vehicle that has auto-update on. Called on app start and when Check a Ride opens; never
     * blocks a screen (CLAUDE.md section 8a, N9).
     */
    fun refreshFuelPrices() {
        applicationScope.launch {
            fuelPriceSource.refreshIfDue()
            fuelPriceUpdates.applyAutomatic()
        }
    }

    private suspend fun onAccountChanged(profile: UserProfile?, signedOutNow: Boolean) = spaceLock.withLock {
        if (profile == null) {
            // Signed out: backup stops, and the records made without an account come back on
            // screen. The account's records stay on the phone for its next sign-in.
            signedInSpace.value = null
            if (signedOutNow) {
                val registry = spaceRegistry.load()
                showSpace(registry, signOutSpace(registry, spaceRegistry.newSpaceId(registry)))
            }
            return@withLock
        }
        val dao = space.value.database.syncDao()
        val registry = spaceRegistry.load()
        val activeHasRecords = dao.recordCount() > 0
        val activeBackupOwner = dao.state(SyncDao.OWNER_KEY)
        val move = asksToMoveRecords(registry, profile.uid, activeHasRecords, activeBackupOwner) &&
            askToMoveRecords(if (profile.isNewAccount) MoveRecordsQuestion.NEW_ACCOUNT else MoveRecordsQuestion.EXISTING_ACCOUNT)
        val next = signInSpace(
            registry,
            profile.uid,
            moveNoAccountRecords = move,
            activeHasRecords = activeHasRecords,
            activeBackupOwner = activeBackupOwner,
            newSpaceId = spaceRegistry.newSpaceId(registry),
        )
        showSpace(registry, next)
        signedInSpace.value = SignedInSpace(next.active, profile.uid)
        // Starts backup for these records; a new space is filled from the account's backup.
        space.value.sync
    }

    /**
     * Shows [question] ([moveRecordsQuestion], drawn by MainActivity) and waits for the rider's
     * answer. Nothing changes until they answer; if the app closes first, it asks again on the
     * next start.
     */
    private suspend fun askToMoveRecords(question: MoveRecordsQuestion): Boolean {
        val answer = CompletableDeferred<Boolean>()
        moveRecordsAnswer = answer
        mutableMoveRecordsQuestion.value = question
        return try {
            answer.await()
        } finally {
            mutableMoveRecordsQuestion.value = null
            moveRecordsAnswer = null
        }
    }

    /** The rider's answer: true moves the records made without an account to the account. */
    fun answerMoveRecords(move: Boolean) {
        moveRecordsAnswer?.complete(move)
    }

    /** Saves [next] and, when it shows another space, puts that one on screen. */
    private fun showSpace(registry: SpaceRegistry, next: SpaceRegistry) {
        if (next != registry) spaceRegistry.save(next)
        val current = space.value
        if (next.active != current.id) {
            current.close()
            mutableSpace.value = openSpace(next.active)
        }
    }

    /** After the account owning [id] was deleted and that space's records erased. */
    private suspend fun releaseSpace(id: String) = spaceLock.withLock {
        val registry = spaceRegistry.load()
        showSpace(registry, afterAccountDeleted(registry, id))
    }

    private fun openSpace(id: String): DataSpace {
        val name = databaseName(id)
        val database = synchronized(databases) { databases.getOrPut(name) { PicksmartDatabase.build(appContext, name) } }
        val owner = signedInSpace.map { it?.takeIf { signedIn -> signedIn.space == id }?.uid }
        return DataSpace(id, database, owner)
    }

    /**
     * One space's records and everything built on them: its database, its settings, the
     * repositories, and its backup. Its [scope] ends when the app switches to another space.
     */
    inner class DataSpace(
        val id: String,
        val database: PicksmartDatabase,
        /** The account whose records these are, while it is signed in. */
        private val owner: Flow<String?>,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        val userPreferences = UserPreferencesRepository(preferencesStore, id)

        val vehicles: VehicleRepository by lazy { VehicleRepository(database.vehicleDao(), userPreferences, clock) }

        val trips: TripRepository by lazy { TripRepository(database.tripDao(), clock) }

        val otherIncome: OtherIncomeRepository by lazy { OtherIncomeRepository(database.otherIncomeDao(), clock) }

        val fuelLogs: FuelLogRepository by lazy { FuelLogRepository(database.fuelLogDao(), clock) }

        val expenses: ExpenseRepository by lazy { ExpenseRepository(database.expenseDao(), clock) }

        val completeOnboarding: CompleteOnboarding by lazy { CompleteOnboarding(vehicles, userPreferences) }

        val csvExporter: CsvExporter by lazy {
            CsvExporter(appContext, trips, otherIncome, fuelLogs, expenses, vehicles, profitCalculator)
        }

        val fuelPriceUpdates: FuelPriceUpdates by lazy { FuelPriceUpdates(vehicles, userPreferences, clock) }

        val sync: SyncProvider by lazy {
            if (!account.isAvailable) return@lazy NoOpSyncProvider
            val dao = database.syncDao()
            val cloudSync = CloudSync(
                dao = dao,
                cloud = FirestoreCloudStore(firestore, AppCheck::passes),
                settings = DataStoreSyncedSettings(userPreferences, dao),
                transaction = { block -> database.withTransaction { block() } },
            )
            CloudSyncProvider(owner, cloudSync, dao.observePendingCount(), clock, scope, appContext)
        }

        val deleteAccount: DeleteAccount by lazy {
            val eraser = PhoneDataEraser(database, userPreferences, profilePhotos, File(appContext.cacheDir, CsvExporter.DIRECTORY))
            DeleteAccount(account, sync) { uid ->
                eraser.erase(uid)
                releaseSpace(id)
            }
        }

        fun close() = scope.cancel()
    }
}

/** The space on screen and the account, signed in now, that owns it. */
private data class SignedInSpace(val space: String, val uid: String)

/** Firestore's minimum cache size; it only holds queued writes. */
private const val FIRESTORE_CACHE_BYTES = 1L * 1024 * 1024

/** What the first screen depends on: the rider's theme and whether setup is done. */
data class StartupSettings(val themeMode: ThemeMode, val onboardingComplete: Boolean)
