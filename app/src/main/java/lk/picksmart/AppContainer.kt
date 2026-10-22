package lk.picksmart

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import lk.picksmart.data.preferences.UserPreferencesRepository
import lk.picksmart.domain.calculator.ProfitCalculator

// One DataStore per process. The file lives under files/datastore/, which Auto Backup includes.
private val Context.userPreferencesStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

/**
 * Manual dependency injection (CLAUDE.md section 3): the single place that builds the app's
 * long-lived objects. Created once by [PicksmartApplication]; ViewModels get what they need
 * from it through their factories.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val userPreferences: UserPreferencesRepository by lazy {
        UserPreferencesRepository(appContext.userPreferencesStore)
    }

    val profitCalculator: ProfitCalculator by lazy { ProfitCalculator() }
}
