package lk.picksmart

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import lk.picksmart.data.local.PicksmartDatabase
import lk.picksmart.data.preferences.UserPreferencesRepository
import lk.picksmart.data.repository.OtherIncomeRepository
import lk.picksmart.data.repository.TripRepository
import lk.picksmart.data.repository.VehicleRepository
import lk.picksmart.domain.calculator.ProfitCalculator
import lk.picksmart.domain.usecase.CompleteOnboarding
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

    val completeOnboarding: CompleteOnboarding by lazy { CompleteOnboarding(vehicles, userPreferences) }

    val profitCalculator: ProfitCalculator by lazy { ProfitCalculator() }
}
