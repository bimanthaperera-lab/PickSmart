package lk.picksmart.ui.checkride

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import lk.picksmart.R
import lk.picksmart.ui.components.EmptyState
import lk.picksmart.ui.components.ScreenTitle
import lk.picksmart.ui.components.TabScreen

/** Check a Ride. The form and live result arrive in Phase 4, after vehicle setup in Phase 3. */
@Composable
fun CheckRideScreen() {
    TabScreen {
        ScreenTitle(stringResource(R.string.check_ride_title))
        EmptyState(
            iconRes = R.drawable.ic_nav_check_ride,
            title = stringResource(R.string.check_ride_empty_title),
            body = stringResource(R.string.check_ride_empty_body),
        )
    }
}
