package lk.picksmart.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import kotlinx.serialization.Serializable
import lk.picksmart.R

@Serializable data object HomeRoute
@Serializable data object CheckRideRoute
@Serializable data object HistoryRoute
@Serializable data object ReportsRoute
/** The Account tab: profile card and settings (CLAUDE.md section 14). Replaced the Settings tab. */
@Serializable data object AccountRoute

/** Edit a vehicle, or add one when [vehicleId] is [NEW]. Opened from Settings. */
@Serializable
data class EditVehicleRoute(val vehicleId: Long) {
    companion object {
        /** Room ids start at 1, so 0 means "create a new vehicle". */
        const val NEW = 0L
    }
}

/** Add a trip, or edit one when [tripId] isn't [NEW]. Opened from History. */
@Serializable
data class EditTripRoute(val tripId: Long) {
    companion object {
        const val NEW = 0L
    }
}

/** Other income list. Opened from Home and Settings. */
@Serializable data object OtherIncomeRoute

/** Add an income entry, or edit one when [incomeId] isn't [NEW]. */
@Serializable
data class EditIncomeRoute(val incomeId: Long) {
    companion object {
        const val NEW = 0L
    }
}

/** Fuel log list, with the actual km/L card. Opened from Home and Settings. */
@Serializable data object FuelLogRoute

/** Add a fill-up, or edit one when [fuelLogId] isn't [NEW]. */
@Serializable
data class EditFuelLogRoute(val fuelLogId: Long) {
    companion object {
        const val NEW = 0L
    }
}

/** Expenses list. Opened from Home and Settings. */
@Serializable data object ExpensesRoute

/** Add an expense, or edit one when [expenseId] isn't [NEW]. */
@Serializable
data class EditExpenseRoute(val expenseId: Long) {
    companion object {
        const val NEW = 0L
    }
}

/** Sign in with email and password or Google. Opened from the Account tab's profile card. */
@Serializable data object LoginRoute

/** Create an account. Opened from the profile card and from Login. */
@Serializable data object RegisterRoute

/** Send a password reset link, starting from the email already typed on Login. */
@Serializable data class ForgotPasswordRoute(val email: String = "")

/** The signed-in rider's details. Opened by tapping the profile card. */
@Serializable data object ProfileRoute

/** The default vehicle's fuel type and price, with published CPC prices. Opened from Settings and Home. */
@Serializable data object FuelPriceRoute

/**
 * The five bottom-bar tabs (CLAUDE.md section 9), in display order. The selected tab shows
 * [selectedIcon], a filled version of [icon].
 */
enum class TopLevelDestination(
    val route: Any,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    @DrawableRes val selectedIcon: Int,
) {
    HOME(HomeRoute, R.string.nav_home, R.drawable.ic_nav_home, R.drawable.ic_nav_home_filled),
    CHECK_RIDE(CheckRideRoute, R.string.nav_check_ride, R.drawable.ic_nav_check_ride, R.drawable.ic_nav_check_ride_filled),
    HISTORY(HistoryRoute, R.string.nav_history, R.drawable.ic_nav_history, R.drawable.ic_nav_history_filled),
    REPORTS(ReportsRoute, R.string.nav_reports, R.drawable.ic_nav_reports, R.drawable.ic_nav_reports_filled),
    ACCOUNT(AccountRoute, R.string.nav_account, R.drawable.ic_nav_account, R.drawable.ic_nav_account_filled),
}
