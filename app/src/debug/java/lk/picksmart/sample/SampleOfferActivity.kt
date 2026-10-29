package lk.picksmart.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import lk.picksmart.glance.OfferReaderService
import lk.picksmart.ui.theme.PicksmartTheme

/**
 * Debug only: a made-up ride offer card for checking the offer reader on an emulator, with no ride
 * app. The values are sample data. Accept shows the next sample and Decline clears the offer, so
 * taps on the bottom buttons can be checked with the badge on screen.
 *
 * Start it with `adb shell am start -n lk.picksmart/lk.picksmart.sample.SampleOfferActivity`. Add
 * `--ez popup true` to show the offer in a popup window that doesn't take focus, the way PickMe
 * shows trips, so it isn't the active window.
 */
class SampleOfferActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val asPopup = intent.getBooleanExtra(EXTRA_POPUP, false)
        setContent {
            PicksmartTheme(darkTheme = isSystemInDarkTheme()) {
                var index by rememberSaveable { mutableIntStateOf(0) }
                val offer = SAMPLES.getOrNull(index)
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .safeDrawingPadding()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(OfferReaderService.SAMPLE_OFFER_MARKER, style = MaterialTheme.typography.labelLarge)
                        if (offer == null || asPopup) {
                            Text("You're online", style = MaterialTheme.typography.headlineSmall)
                        } else {
                            // Long lists use a smaller font so every line stays on screen and is read.
                            val style = if (offer.size > 10) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.headlineSmall
                            offer.forEach { Text(it, style = style) }
                        }
                        if (offer != null && asPopup) {
                            Popup(
                                alignment = Alignment.Center,
                                offset = IntOffset(0, -200),
                                properties = PopupProperties(focusable = false),
                            ) {
                                Surface(tonalElevation = 6.dp, shadowElevation = 6.dp, shape = MaterialTheme.shapes.large) {
                                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(OfferReaderService.SAMPLE_OFFER_MARKER, style = MaterialTheme.typography.labelLarge)
                                        offer.forEach { Text(it, style = MaterialTheme.typography.headlineSmall) }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { index = SAMPLES.size }, modifier = Modifier.weight(1f)) { Text("Decline") }
                            Button(onClick = { index = (index + 1) % (SAMPLES.size + 1) }, modifier = Modifier.weight(1f)) { Text("Accept") }
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val EXTRA_POPUP = "popup"

        /** Sample offers only; the index past the end is "no offer". */
        val SAMPLES = listOf(
            // Laid out like PickMe's card: the amount and "LKR" as two texts, and a "+ LKR" chip.
            listOf("450.75", "LKR", "TUK", "12 min away (5.0 km)", "Sample Road", "9 min  (3.4 km)", "Example Mawatha", "+ LKR 150"),
            listOf("UberX", "LKR 850.00", "4 min (1.2 km) away", "18 min (7.5 km) trip"),
            listOf("UberX", "LKR 300.00", "9 min (3.0 km) away", "35 min (15.0 km) trip"),
            listOf("Tuk", "LKR 1,450.00", "6 min (2.1 km) away", "24 min (9.8 km) trip"),
            listOf("UberX", "LKR 150.00", "12 min (4.0 km) away", "40 min (16.0 km) trip"),
            // A trip scanner with two cards: a long, high fare trip with a "+ LKR" surge chip, then a short one.
            listOf(
                "Trip scanner (2)", "10,250.40", "LKR", "TUK", "1 min away (0.2 km)", "Sample Road",
                "3 hr 40 min  (131.75 km)", "Example Town", "+ LKR 100", "Accept trip",
                "480.25", "LKR", "TUK", "2 min away (0.3 km)", "Example Mawatha", "11 min  (4.8 km)", "Sample Lane",
            ),
            // The same list scrolled so the first card's fare is off screen.
            listOf(
                "TUK", "1 min away (0.2 km)", "Sample Road", "3 hr 40 min  (131.75 km)", "Example Town", "+ LKR 100",
                "Accept trip", "480.25", "LKR", "TUK", "2 min away (0.3 km)", "Example Mawatha", "11 min  (4.8 km)", "Sample Lane",
            ),
        )
    }
}
