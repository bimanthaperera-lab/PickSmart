package lk.picksmart.ui.vehicle

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import lk.picksmart.R
import lk.picksmart.domain.model.FuelType
import lk.picksmart.domain.model.Vehicle
import lk.picksmart.domain.model.VehicleType
import lk.picksmart.ui.components.rememberMoneyFormatter
import lk.picksmart.ui.theme.Spacing
import lk.picksmart.util.decimalToInput

@get:StringRes
val VehicleType.label: Int
    get() = when (this) {
        VehicleType.MOTORCYCLE -> R.string.vehicle_type_motorcycle
        VehicleType.THREE_WHEELER -> R.string.vehicle_type_three_wheeler
        VehicleType.CAR -> R.string.vehicle_type_car
        VehicleType.OTHER -> R.string.vehicle_type_other
    }

/** The vehicle drawn in its icon tile, e.g. a scooter or a three-wheeler. */
@get:DrawableRes
val VehicleType.icon: Int
    get() = when (this) {
        VehicleType.MOTORCYCLE -> R.drawable.ic_vehicle_motorcycle
        VehicleType.THREE_WHEELER -> R.drawable.ic_vehicle_three_wheeler
        VehicleType.CAR -> R.drawable.ic_vehicle_car
        VehicleType.OTHER -> R.drawable.ic_vehicle_other
    }

@get:StringRes
val FuelType.label: Int
    get() = when (this) {
        FuelType.PETROL_92 -> R.string.fuel_petrol_92
        FuelType.PETROL_95 -> R.string.fuel_petrol_95
        FuelType.AUTO_DIESEL -> R.string.fuel_auto_diesel
        FuelType.SUPER_DIESEL -> R.string.fuel_super_diesel
    }

/** "Motorcycle · Petrol 92 Octane" */
@Composable
fun vehicleKindSummary(vehicle: Vehicle): String =
    stringResource(R.string.vehicle_kind_summary, stringResource(vehicle.type.label), stringResource(vehicle.fuelType.label))

/**
 * The vehicle's fuel price, km per litre and maintenance reserve, each after its icon, wrapping
 * onto more lines when narrow. Used on the Check a Ride vehicle card and the Vehicles list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VehicleRates(vehicle: Vehicle, modifier: Modifier = Modifier) {
    val money = rememberMoneyFormatter()
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        RateItem(R.drawable.ic_fuel, stringResource(R.string.check_rate_fuel_price, money.format(vehicle.fuelPricePerLitreCents)))
        RateItem(R.drawable.ic_gauge, stringResource(R.string.check_rate_efficiency, decimalToInput(vehicle.fuelEfficiencyKmPerLitre)))
        val reserve = money.format(vehicle.maintenanceReservePerKmCents)
        RateItem(
            R.drawable.ic_wrench,
            stringResource(R.string.check_rate_reserve, reserve),
            description = stringResource(R.string.check_rate_reserve_description, reserve),
        )
    }
}

/**
 * One rate, e.g. a fuel pump and "Rs. 300.00/L". The icon grows with the text, and the text stays
 * on one line so an item moves to the next row whole rather than breaking. [description] replaces
 * what a screen reader says when the icon carries meaning the text leaves out.
 */
@Composable
private fun RateItem(@DrawableRes icon: Int, text: String, description: String? = null) {
    val style = MaterialTheme.typography.bodyMedium
    val iconSize = with(LocalDensity.current) { style.fontSize.toDp() } * 1.1f
    Row(
        modifier = if (description != null) Modifier.clearAndSetSemantics { contentDescription = description } else Modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        // Decorative: the text (or the description) carries the meaning.
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(iconSize),
        )
        Text(text, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
    }
}
