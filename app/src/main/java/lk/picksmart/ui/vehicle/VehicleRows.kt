package lk.picksmart.ui.vehicle

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import lk.picksmart.R
import lk.picksmart.domain.model.Vehicle
import lk.picksmart.ui.components.IconTile
import lk.picksmart.ui.components.neutralRecordIcon
import lk.picksmart.ui.theme.PicksmartTheme
import lk.picksmart.ui.theme.Spacing

/**
 * One of the rider's other vehicles in the Vehicles list: its type's icon, the name (with its
 * number plate, and "Sample" while it has sample rates), its type and fuel, then its rates with icons. Opens the vehicle.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VehicleRow(vehicle: Vehicle, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = 72.dp)
            .padding(horizontal = Spacing.l, vertical = Spacing.m)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        IconTile(neutralRecordIcon(vehicle.type.icon))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(vehicle.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                vehicle.plateNumber?.let { PlateTag(it) }
                if (vehicle.isSample) SampleTag()
            }
            Text(
                vehicleKindSummary(vehicle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            VehicleRates(vehicle)
        }
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * The vehicle in use, as in the Settings mockup: "Honda Dio", "Motorcycle · default vehicle", and
 * a Switch button when [onSwitch] is set (there is another vehicle). Tapping the card opens it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActiveVehicleCard(vehicle: Vehicle, onOpen: () -> Unit, onSwitch: (() -> Unit)?) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .clickable(role = Role.Button, onClick = onOpen)
                .defaultMinSize(minHeight = 72.dp)
                .padding(start = Spacing.l, end = Spacing.m, top = Spacing.m, bottom = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(vehicle.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    vehicle.plateNumber?.let { PlateTag(it) }
                    if (vehicle.isSample) SampleTag()
                }
                Text(
                    stringResource(R.string.vehicle_default_summary, stringResource(vehicle.type.label)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onSwitch != null) {
                OutlinedButton(
                    onClick = onSwitch,
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.defaultMinSize(minHeight = Spacing.touchTarget),
                ) {
                    Text(stringResource(R.string.vehicle_switch), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/**
 * Pick the vehicle in use: one tap on a vehicle switches to it and closes. Its rates fill Check a
 * Ride and new records; saved records keep their own.
 */
@Composable
fun SwitchVehicleDialog(vehicles: List<Vehicle>, activeId: Long, onSelect: (vehicleId: Long) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vehicle_switch_title)) },
        text = {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    stringResource(R.string.vehicle_switch_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.s),
                )
                vehicles.forEach { vehicle ->
                    val selected = vehicle.id == activeId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(vehicle.id) })
                            .defaultMinSize(minHeight = Spacing.touchTarget),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Icon(
                            painterResource(vehicle.type.icon),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(vehicle.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                vehicle.plateNumber?.let { stringResource(R.string.vehicle_type_and_plate, stringResource(vehicle.type.label), it) }
                                    ?: stringResource(vehicle.type.label),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** The number plate as a small outlined tag, like the plate itself. */
@Composable
private fun PlateTag(plate: String) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            plate,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = Spacing.s, vertical = 2.dp),
        )
    }
}

@Composable
private fun SampleTag() {
    val status = PicksmartTheme.status
    Surface(shape = MaterialTheme.shapes.extraSmall, color = status.belowTargetContainer) {
        Text(
            stringResource(R.string.check_sample_label),
            style = MaterialTheme.typography.labelMedium,
            color = status.onBelowTargetContainer,
            modifier = Modifier.padding(horizontal = Spacing.s, vertical = 2.dp),
        )
    }
}
