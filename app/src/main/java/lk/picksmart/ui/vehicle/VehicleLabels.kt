package lk.picksmart.ui.vehicle

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import lk.picksmart.R
import lk.picksmart.domain.model.FuelType
import lk.picksmart.domain.model.Vehicle
import lk.picksmart.domain.model.VehicleType
import lk.picksmart.ui.components.rememberMoneyFormatter
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

/** "Rs. 300.00/L · 40 km/L · Rs. 2.00/km reserve" */
@Composable
fun vehicleRatesSummary(vehicle: Vehicle): String {
    val money = rememberMoneyFormatter()
    return stringResource(
        R.string.vehicle_rates_summary,
        money.format(vehicle.fuelPricePerLitreCents),
        decimalToInput(vehicle.fuelEfficiencyKmPerLitre),
        money.format(vehicle.maintenanceReservePerKmCents),
    )
}
