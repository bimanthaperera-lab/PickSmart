package lk.picksmart.ui.vehicle

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import lk.picksmart.R
import lk.picksmart.domain.model.Vehicle
import lk.picksmart.ui.checkride.InputSection
import lk.picksmart.ui.components.ChoiceChips
import lk.picksmart.ui.components.FieldLabel

/**
 * The vehicle a trip, fill-up or expense belongs to.
 *
 * @property choices The rider's vehicles that aren't archived.
 * @property selectedId The record's vehicle; while adding, the active one until the rider picks.
 * @property selectedName Its name, also when it has since been archived.
 * @property editable True while adding. A saved record keeps its vehicle, so a trip's rates
 *   snapshot always matches the vehicle it was recorded with.
 */
data class RecordVehicle(
    val choices: List<Vehicle> = emptyList(),
    val selectedId: Long? = null,
    val selectedName: String? = null,
    val editable: Boolean = false,
) {
    /** Worth showing: the rider has more than one vehicle, or the record's vehicle is archived. */
    val shown: Boolean
        get() = choices.size > 1 || (selectedId != null && choices.none { it.id == selectedId })
}

/**
 * Chips to pick the vehicle while adding, or the saved record's vehicle name. Hidden with one
 * vehicle. With [card] it sits on its own card ([InputSection]), as on Add expense.
 */
@Composable
fun RecordVehicleField(vehicle: RecordVehicle, onSelect: (vehicleId: Long) -> Unit, card: Boolean = false) {
    if (!vehicle.shown) return
    val label = stringResource(R.string.record_vehicle_label)
    if (card) {
        InputSection(title = label) { VehicleChoice(vehicle, onSelect) }
    } else {
        Column {
            FieldLabel(label)
            VehicleChoice(vehicle, onSelect)
        }
    }
}

@Composable
private fun VehicleChoice(vehicle: RecordVehicle, onSelect: (vehicleId: Long) -> Unit) {
    if (vehicle.editable) {
        ChoiceChips(
            options = vehicle.choices,
            selected = vehicle.choices.firstOrNull { it.id == vehicle.selectedId },
            optionLabel = { it.name },
            optionIcon = { it.type.icon },
            onSelect = { onSelect(it.id) },
        )
    } else if (vehicle.selectedName != null) {
        Text(vehicle.selectedName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}
