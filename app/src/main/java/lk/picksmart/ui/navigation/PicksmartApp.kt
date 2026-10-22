package lk.picksmart.ui.navigation

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import lk.picksmart.ui.checkride.CheckRideScreen
import lk.picksmart.ui.history.HistoryScreen
import lk.picksmart.ui.home.HomeScreen
import lk.picksmart.ui.reports.ReportsScreen
import lk.picksmart.ui.settings.SettingsScreen
import lk.picksmart.ui.vehicle.EditVehicleScreen

/** The app shell: bottom navigation around the five tabs. */
@Composable
fun PicksmartApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // Only the five tabs show the bar; detail screens such as Edit vehicle take the full height.
            val onTopLevel = TopLevelDestination.entries.any { d -> currentDestination?.hasRoute(d.route::class) == true }
            if (onTopLevel) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any { it.hasRoute(destination.route::class) } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navController.navigateToTopLevel(destination) },
                        // The label below names the tab, so the icon needs no description of its own.
                        icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                        label = { NavLabel(stringResource(destination.label)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            // Consumed so a screen's imePadding() only adds the part of the keyboard below the bar.
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            val openCheckRide = { navController.navigateToTopLevel(TopLevelDestination.CHECK_RIDE) }
            composable<HomeRoute> { HomeScreen(onCheckRide = openCheckRide) }
            composable<CheckRideRoute> {
                CheckRideScreen(
                    onAddVehicle = { navController.navigate(EditVehicleRoute(EditVehicleRoute.NEW)) },
                    onOpenHistory = { navController.navigateToTopLevel(TopLevelDestination.HISTORY) },
                )
            }
            composable<HistoryRoute> { HistoryScreen(onCheckRide = openCheckRide) }
            composable<ReportsRoute> { ReportsScreen() }
            composable<SettingsRoute> {
                SettingsScreen(onEditVehicle = { id -> navController.navigate(EditVehicleRoute(id)) })
            }
            composable<EditVehicleRoute> {
                EditVehicleScreen(
                    onClose = {
                        // Guard against a double close (e.g. tapping X as a save finishes) popping past the opener.
                        if (navController.currentDestination?.hasRoute(EditVehicleRoute::class) == true) {
                            navController.popBackStack()
                        }
                    },
                )
            }
        }
    }
}

/**
 * A tab label that shrinks to fit on one line instead of being cut off. Five tabs on a 360 dp
 * phone leave about 72 dp each, which "Check Ride" overflows at large font scales. Screen readers
 * always get the full label.
 */
@Composable
private fun NavLabel(text: String) {
    // NavigationBarItem measures its label without a width limit, so bound it to one tab's
    // width ourselves; otherwise auto-size has nothing to fit against.
    val density = LocalDensity.current
    val windowWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    val maxWidth = windowWidth / TopLevelDestination.entries.size - 8.dp
    // A floor in dp, not sp: an sp floor grows with the font scale and stops fitting.
    val minFontSize = with(density) { 8.dp.toSp() }
    val style = LocalTextStyle.current
    BasicText(
        text = text,
        style = style.copy(color = LocalContentColor.current),
        maxLines = 1,
        // Without this, "Check Ride" wraps at the space and auto-size sees no overflow.
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = minFontSize, maxFontSize = style.fontSize, stepSize = 0.5.sp),
        modifier = Modifier.widthIn(max = maxWidth),
    )
}

/**
 * Switches tab without piling up history: back from any tab returns to Home, and back from Home
 * leaves the app. Each tab keeps its scroll and state when you come back to it.
 */
private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
