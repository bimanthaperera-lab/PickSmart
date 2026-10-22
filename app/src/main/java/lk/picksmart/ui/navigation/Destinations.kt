package lk.picksmart.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import kotlinx.serialization.Serializable
import lk.picksmart.R

@Serializable data object HomeRoute
@Serializable data object CheckRideRoute
@Serializable data object HistoryRoute
@Serializable data object ReportsRoute
@Serializable data object SettingsRoute

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

/** The five bottom-bar tabs (CLAUDE.md section 9), in display order. */
enum class TopLevelDestination(
    val route: Any,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
) {
    HOME(HomeRoute, R.string.nav_home, R.drawable.ic_nav_home),
    CHECK_RIDE(CheckRideRoute, R.string.nav_check_ride, R.drawable.ic_nav_check_ride),
    HISTORY(HistoryRoute, R.string.nav_history, R.drawable.ic_nav_history),
    REPORTS(ReportsRoute, R.string.nav_reports, R.drawable.ic_nav_reports),
    SETTINGS(SettingsRoute, R.string.nav_settings, R.drawable.ic_nav_settings),
}
