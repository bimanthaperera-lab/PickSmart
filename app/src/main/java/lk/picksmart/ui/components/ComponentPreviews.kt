package lk.picksmart.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import lk.picksmart.R
import lk.picksmart.domain.calculator.ResultBand
import lk.picksmart.ui.theme.PicksmartTheme
import lk.picksmart.ui.theme.Spacing

// Sample values below are for previews only.

@Composable
private fun ComponentGallery() {
    val money = rememberMoneyFormatter()
    var fare by remember { mutableStateOf("1000") }
    Column(
        Modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        ScreenTitle("Components")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            ResultBand.entries.forEach { ResultBandChip(it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            StatCard("Gross income", money.format(685_000), Modifier.weight(1f))
            StatCard("Net", money.formatSigned(70_500), Modifier.weight(1f), PicksmartTheme.status.profit)
        }
        NumberField(value = fare, onValueChange = { fare = it }, label = "Fare offered", prefix = "Rs.")
        NumberField(value = "-", onValueChange = {}, label = "Pickup", suffix = "km", maxDecimals = 1, error = "Can't be below 0")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            SecondaryButton("Save offer", {}, Modifier.weight(1f))
            PrimaryButton("I took it", {}, Modifier.weight(1f))
        }
        PrimaryButton("Check a Ride", {}, Modifier.fillMaxWidth(), iconRes = R.drawable.ic_nav_check_ride)
        EmptyState(R.drawable.ic_nav_history, "No trips yet", "Rides you check will be listed here.")
        ErrorState("Couldn't load your trips.", onRetry = {})
        LoadingState()
    }
}

@Preview(name = "Dark", heightDp = 1400)
@Composable
private fun ComponentGalleryDark() = PicksmartTheme(darkTheme = true) { ComponentGallery() }

@Preview(name = "Light", heightDp = 1400)
@Composable
private fun ComponentGalleryLight() = PicksmartTheme(darkTheme = false) { ComponentGallery() }
