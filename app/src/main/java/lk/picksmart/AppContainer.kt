package lk.picksmart

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import lk.picksmart.data.account.AccountProvider
import lk.picksmart.data.account.FirebaseAccountProvider
import lk.picksmart.data.account.NoOpAccountProvider
import lk.picksmart.data.account.ProfilePhotoCache
import lk.picksmart.data.local.PicksmartDatabase
import lk.picksmart.data.preferences.UserPreferencesRepository
import lk.picksmart.data.remote.FuelPriceSource
import lk.picksmart.data.remote.HttpsFuelPriceFetcher
import lk.picksmart.data.repository.ExpenseRepository
import lk.picksmart.data.repository.FuelLogRepository
import lk.picksmart.data.repository.OtherIncomeRepository
import lk.picksmart.data.repository.TripRepository
import lk.picksmart.data.repository.VehicleRepository
import lk.picksmart.domain.calculator.ProfitCalculator
import lk.picksmart.domain.usecase.CompleteOnboarding
import lk.picksmart.domain.usecase.FuelPriceUpdates
import java.time.Clock

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

    /** Work that should finish even if the screen that started it closes, such as a price check. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
