package lk.picksmart.ui.vehicle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import lk.picksmart.R
import lk.picksmart.domain.model.FuelType
import lk.picksmart.domain.model.VehicleType
import lk.picksmart.ui.components.ChoiceGrid
import lk.picksmart.ui.components.NumberField
import lk.picksmart.ui.components.TextInputField
import lk.picksmart.ui.theme.Spacing

/**
 * The vehicle fields shared by onboarding and Edit vehicle. Picking a fuel type fills in its price
 * from [fuelPriceSuggestions] (`fuelPriceSuggestions`). The optional number plate shows only when
 * [showPlateNumber] is set (Edit vehicle), so onboarding stays short.
 */
@Composable
fun VehicleFormFields(
    state: VehicleFormState,
    errors: Map<VehicleField, FieldError>,
    onChange: (VehicleFormState) -> Unit,
    modifier: Modifier = Modifier,
    fuelPriceSuggestions: Map<FuelType, Long> = emptyMap(),
    showPlateNumber: Boolean = false,
) {
    val currency = stringResource(R.string.currency_prefix)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
        ChoiceGrid(
            label = stringResource(R.string.vehicle_field_type),
            options = VehicleType.entries,
            selected = state.type,
            optionLabel = { stringResource(it.label) },
            onSelect = { onChange(state.copy(type = it)) },
            optionIcon = { it.icon },
            largeIcons = true,
        )
        TextInputField(
            value = state.name,
            onValueChange = { onChange(state.copy(name = it)) },
            label = stringResource(R.string.vehicle_field_name),
            placeholder = stringResource(R.string.vehicle_field_name_placeholder),
        )
        if (showPlateNumber) {
            TextInputField(
                value = state.plateNumber,
                onValueChange = { onChange(state.copy(plateNumber = it)) },
                label = stringResource(R.string.vehicle_field_plate),
                placeholder = stringResource(R.string.vehicle_field_plate_placeholder),
                supportingText = stringResource(R.string.vehicle_field_plate_help),
                maxLength = PLATE_NUMBER_MAX_LENGTH,
                capitalization = KeyboardCapitalization.Characters,
            )
        }
        ChoiceGrid(
            label = stringResource(R.string.vehicle_field_fuel_type),
            options = FuelType.entries,
            selected = state.fuelType,
            optionLabel = { stringResource(it.label) },
            onSelect = { onChange(state.withFuelType(it, fuelPriceSuggestions)) },
        )
        NumberField(
            value = state.fuelPrice,
            onValueChange = { onChange(state.copy(fuelPrice = it)) },
            label = stringResource(R.string.vehicle_field_fuel_price),
            placeholder = stringResource(R.string.vehicle_field_fuel_price_placeholder),
            prefix = currency,
            suffix = stringResource(R.string.unit_per_litre),
            supportingText = stringResource(R.string.vehicle_field_fuel_price_help),
            error = errors[VehicleField.FUEL_PRICE]?.let { fieldErrorText(it, R.string.vehicle_error_fuel_price_required) },
        )
        NumberField(
            value = state.efficiency,
            onValueChange = { onChange(state.copy(efficiency = it)) },
            label = stringResource(R.string.vehicle_field_efficiency),
            suffix = stringResource(R.string.unit_km_per_litre),
            maxDecimals = 1,
            supportingText = stringResource(R.string.vehicle_field_efficiency_help),
            error = errors[VehicleField.EFFICIENCY]?.let { fieldErrorText(it, R.string.vehicle_error_efficiency_required) },
        )
        NumberField(
            value = state.maintenance,
            onValueChange = { onChange(state.copy(maintenance = it)) },
            label = stringResource(R.string.vehicle_field_maintenance),
            placeholder = stringResource(R.string.amount_placeholder_zero),
            prefix = currency,
            suffix = stringResource(R.string.unit_per_km),
            supportingText = stringResource(R.string.vehicle_field_maintenance_help),
            error = errors[VehicleField.MAINTENANCE]?.let { fieldErrorText(it, R.string.vehicle_error_invalid) },
        )
        NumberField(
            value = state.target,
            onValueChange = { onChange(state.copy(target = it)) },
            label = stringResource(R.string.vehicle_field_target),
            placeholder = stringResource(R.string.vehicle_field_target_placeholder),
            prefix = currency,
            suffix = stringResource(R.string.unit_per_km),
            supportingText = stringResource(R.string.vehicle_field_target_help),
            error = errors[VehicleField.TARGET]?.let { fieldErrorText(it, R.string.vehicle_error_invalid) },
            imeAction = ImeAction.Done,
        )
    }
}

@Composable
private fun fieldErrorText(error: FieldError, requiredMessage: Int): String = stringResource(
    when (error) {
        FieldError.REQUIRED -> requiredMessage
        FieldError.MUST_BE_ABOVE_ZERO -> R.string.vehicle_error_above_zero
        FieldError.INVALID -> R.string.vehicle_error_invalid
    },
)
