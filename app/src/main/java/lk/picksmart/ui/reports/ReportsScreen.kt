package lk.picksmart.ui.reports

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import lk.picksmart.R
import lk.picksmart.ui.components.EmptyState
import lk.picksmart.ui.components.ScreenTitle
import lk.picksmart.ui.components.TabScreen

/** Reports. Shows the empty state until there is data to report on (Phase 7). */
@Composable
fun ReportsScreen() {
    TabScreen {
        ScreenTitle(stringResource(R.string.reports_title))
        EmptyState(
            iconRes = R.drawable.ic_nav_reports,
            title = stringResource(R.string.reports_empty_title),
            body = stringResource(R.string.reports_empty_body),
        )
    }
}
