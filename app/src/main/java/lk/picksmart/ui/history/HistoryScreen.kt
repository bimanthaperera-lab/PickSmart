package lk.picksmart.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import lk.picksmart.R
import lk.picksmart.ui.components.EmptyState
import lk.picksmart.ui.components.ScreenTitle
import lk.picksmart.ui.components.TabScreen

/** Trip history. Shows the empty state until trips are stored (Phase 5). */
@Composable
fun HistoryScreen(onCheckRide: () -> Unit) {
    TabScreen {
        ScreenTitle(stringResource(R.string.history_title))
        EmptyState(
            iconRes = R.drawable.ic_nav_history,
            title = stringResource(R.string.history_empty_title),
            body = stringResource(R.string.history_empty_body),
            actionLabel = stringResource(R.string.action_check_ride),
            onAction = onCheckRide,
        )
    }
}
