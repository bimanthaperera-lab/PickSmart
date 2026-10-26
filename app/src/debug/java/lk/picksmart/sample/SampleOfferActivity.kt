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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import lk.picksmart.glance.OfferReaderService
import lk.picksmart.ui.theme.PicksmartTheme

/**
 * Debug only: a made-up ride offer card for checking the offer reader on an emulator, with no ride
 * app. The values are sample data. Accept shows the next sample and Decline clears the offer, so
 * taps on the bottom buttons can be checked with the badge on screen.
 *
 * Start it with `adb shell am start -n lk.picksmart/lk.picksmart.sample.SampleOfferActivity`.
 */
class SampleOfferActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
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
                        if (offer == null) {
                            Text("You're online", style = MaterialTheme.typography.headlineSmall)
                        } else {
                            offer.forEach { Text(it, style = MaterialTheme.typography.headlineSmall) }
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
        /** Sample offers only; the index past the end is "no offer". */
        val SAMPLES = listOf(
            listOf("UberX", "LKR 850.00", "4 min (1.2 km) away", "18 min (7.5 km) trip"),
            listOf("UberX", "LKR 300.00", "9 min (3.0 km) away", "35 min (15.0 km) trip"),
            listOf("Tuk", "LKR 1,450.00", "6 min (2.1 km) away", "24 min (9.8 km) trip"),
            listOf("UberX", "LKR 150.00", "12 min (4.0 km) away", "40 min (16.0 km) trip"),
        )
    }
}
