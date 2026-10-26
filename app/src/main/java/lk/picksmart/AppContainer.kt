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
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import lk.picksmart.data.account.AccountProvider
import lk.picksmart.data.account.FirebaseAccountProvider
import lk.picksmart.data.account.NoOpAccountProvider
import lk.picksmart.data.account.ProfilePhotoCache
import lk.picksmart.data.export.CsvExporter
import lk.picksmart.data.local.PicksmartDatabase
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
import lk.picksmart.domain.model.ThemeMode
import lk.picksmart.domain.usecase.CompleteOnboarding
import lk.picksmart.domain.usecase.FuelPriceUpdates

// One DataStore per process. The file lives under files/datastore/, which Auto Backup includes.
private val Context.userPreferencesStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

/**
 * Manual dependency injection (CLAUDE.md section 3): the single place that builds the app's
 * long-lived objects. Created once by [PicksmartApplication]; ViewModels get what they need
 * from it through their factories.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Wall-clock time for record timestamps. Dates shown to riders use Asia/Colombo. */
    val clock: Clock = Clock.systemUTC()

    val database: PicksmartDatabase by lazy { PicksmartDatabase.build(appContext) }

    val userPreferences: UserPreferencesRepository by lazy {
        UserPreferencesRepository(appContext.userPreferencesStore)
    }

    val vehicles: VehicleRepository by lazy {
        VehicleRepository(database.vehicleDao(), userPreferences, clock)
    }

    val trips: TripRepository by lazy { TripRepository(database.tripDao(), clock) }

    val otherIncome: OtherIncomeRepository by lazy { OtherIncomeRepository(database.otherIncomeDao(), clock) }

    val fuelLogs: FuelLogRepository by lazy { FuelLogRepository(database.fuelLogDao(), clock) }

    val expenses: ExpenseRepository by lazy { ExpenseRepository(database.expenseDao(), clock) }

    val completeOnboarding: CompleteOnboarding by lazy { CompleteOnboarding(vehicles, userPreferences) }

    val profitCalculator: ProfitCalculator by lazy { ProfitCalculator() }

    val csvExporter: CsvExporter by lazy {
        CsvExporter(appContext, trips, otherIncome, fuelLogs, expenses, vehicles, profitCalculator)
    }

    /** Work that should finish even if the screen that started it closes, such as a price check. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * The theme and setup flag MainActivity waits for, kept loaded while the app runs. A screen
     * recreated later (below Android 13, a language change does that) has them on its first frame
     * instead of drawing a blank one while DataStore loads.
     */
    val startupSettings: StateFlow<StartupSettings?> by lazy {
        combine(userPreferences.themeMode, userPreferences.onboardingComplete, ::StartupSettings)
            .stateIn(applicationScope, SharingStarted.Eagerly, null)
    }

    val fuelPriceSource: FuelPriceSource by lazy {
        val url = BuildConfig.FUEL_PRICE_URL.takeIf { it.isNotBlank() }
        FuelPriceSource(url?.let(::HttpsFuelPriceFetcher), userPreferences, clock)
    }

    val fuelPriceUpdates: FuelPriceUpdates by lazy { FuelPriceUpdates(vehicles, userPreferences, clock) }

    /**
     * The optional account (CLAUDE.md section 14). Firebase only when the build has
     * `google-services.json` and it started; otherwise the app runs signed out with no sign-in.
     */
    val account: AccountProvider by lazy {
        if (BuildConfig.FIREBASE_ENABLED && FirebaseApp.getApps(appContext).isNotEmpty()) {
            FirebaseAccountProvider(FirebaseAuth.getInstance(), appContext)
        } else {
            NoOpAccountProvider
        }
    }

    /**
     * Optional cloud backup (CLAUDE.md section 14): only with an account, and it starts with the
     * app ([PicksmartApplication]). It runs in the background and never blocks a screen (N9).
     */
    val sync: SyncProvider by lazy {
        if (!account.isAvailable) return@lazy NoOpSyncProvider
        val firestore = FirebaseFirestore.getInstance().apply {
            // Firestore's own cache only holds writes waiting for the network; the records live in Room.
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder().setSizeBytes(FIRESTORE_CACHE_BYTES).build())
                .build()
        }
        val dao = database.syncDao()
        val cloudSync = CloudSync(
            dao = dao,
            cloud = FirestoreCloudStore(firestore),
            settings = DataStoreSyncedSettings(userPreferences, dao),
            transaction = { block -> database.withTransaction { block() } },
        )
        CloudSyncProvider(account, cloudSync, dao.observePendingCount(), clock, applicationScope, appContext)
    }

    val profilePhotos: ProfilePhotoCache by lazy { ProfilePhotoCache(appContext.filesDir.resolve("profile_photo")) }

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
}

/** Firestore's minimum cache size; it only holds queued writes. */
private const val FIRESTORE_CACHE_BYTES = 1L * 1024 * 1024

/** What the first screen depends on: the rider's theme and whether setup is done. */
data class StartupSettings(val themeMode: ThemeMode, val onboardingComplete: Boolean)
