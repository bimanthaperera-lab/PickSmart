package lk.picksmart.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import lk.picksmart.R
import lk.picksmart.ui.components.BrandMark
import lk.picksmart.ui.components.EmptyState
import lk.picksmart.ui.components.ListGroup
import lk.picksmart.ui.components.ListRow
import lk.picksmart.ui.components.ScreenTitle
import lk.picksmart.ui.components.TabScreen
import lk.picksmart.ui.theme.Spacing
import lk.picksmart.util.todayInColombo
import java.time.format.DateTimeFormatter

/** Today's summary. The dashboard arrives in Phase 7; until then, the way in and records. */
@Composable
fun HomeScreen(onCheckRide: () -> Unit, onOpenIncome: () -> Unit) {
    TabScreen {
        HomeHeader()
        EmptyState(
            iconRes = R.drawable.ic_nav_check_ride,
            title = stringResource(R.string.home_empty_title),
            body = stringResource(R.string.home_empty_body),
            actionLabel = stringResource(R.string.action_check_ride),
            onAction = onCheckRide,
        )
        ListGroup(
            rows = listOf {
                ListRow(
                    title = stringResource(R.string.settings_other_income),
                    subtitle = stringResource(R.string.settings_other_income_body),
                    onClick = onOpenIncome,
                )
            },
        )
    }
}

@Composable
private fun HomeHeader() {
    val pattern = stringResource(R.string.home_date_pattern)
    val locale = LocalConfiguration.current.locales[0]
    val date = remember(pattern, locale) {
        todayInColombo().format(DateTimeFormatter.ofPattern(pattern, locale))
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        BrandMark()
        Column {
            Text(date, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ScreenTitle(stringResource(R.string.home_title))
        }
    }
}
