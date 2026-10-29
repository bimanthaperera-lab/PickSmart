package lk.picksmart.ui.vehicle

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import lk.picksmart.R
import lk.picksmart.domain.model.Vehicle
import lk.picksmart.ui.checkride.InputSection
import lk.picksmart.ui.components.FieldLabel

/**
 * The vehicle a saved trip, fill-up or expense belongs to. A new record always goes on the active
 * vehicle (owner decision 2026-10-29), so only Edit screens show this, and never let it change: a
 * trip's rates snapshot always matches the vehicle it was recorded with.
 *
 * @property choices The rider's vehicles that aren't archived.
 * @property selectedId The record's vehicle.
 * @property selectedName Its name, also when it has since been archived.
 */
data class RecordVehicle(
    val choices: List<Vehicle> = emptyList(),
    val selectedId: Long? = null,
    val selectedName: String? = null,
) {
    /** Worth showing: the rider has more than one vehicle, or the record's vehicle is archived. */
    val shown: Boolean
        get() = selectedName != null && (choices.size > 1 || (selectedId != null && choices.none { it.id == selectedId }))
}

/**
 * The saved record's vehicle name, hidden with one vehicle. With [card] it sits on its own card
 * ([InputSection]), as on Edit fill-up and Edit expense.
 */
@Composable
fun RecordVehicleField(vehicle: RecordVehicle, card: Boolean = false) {
    if (!vehicle.shown || vehicle.selectedName == null) return
    val label = stringResource(R.string.record_vehicle_label)
    val name = @Composable {
        Text(vehicle.selectedName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
    if (card) {
        InputSection(title = label) { name() }
    } else {
        Column {
            FieldLabel(label)
            name()
        }
    }
}
