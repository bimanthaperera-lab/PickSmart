package lk.picksmart.ui.vehicle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import lk.picksmart.R
import lk.picksmart.domain.model.FuelType
import lk.picksmart.domain.model.VehicleType
import lk.picksmart.ui.checkride.ErrorText
import lk.picksmart.ui.checkride.HelpText
import lk.picksmart.ui.checkride.InputSection
import lk.picksmart.ui.checkride.STACK_FONT_SCALE
import lk.picksmart.ui.checkride.StepButton
import lk.picksmart.ui.checkride.TextBox
import lk.picksmart.ui.checkride.ValueBox
import lk.picksmart.ui.components.ChoiceGrid
import lk.picksmart.ui.theme.Spacing
import lk.picksmart.util.InputDigits

/**
 * The vehicle fields shared by onboarding and Edit vehicle, each on its own card in the style of
 * Check a Ride: a small upper-case title (with the unit on the right), the box, then a hint.
 * Picking a fuel type fills in its price from [fuelPriceSuggestions] (`fuelPriceSuggestions`);
 * the "−5" / "+5" buttons move the price by Rs. 5. Every field but maintenance is required.
 */
@Composable
fun VehicleFormFields(
    state: VehicleFormState,
    errors: Map<VehicleField, FieldError>,
    onChange: (VehicleFormState) -> Unit,
    modifier: Modifier = Modifier,
    fuelPriceSuggestions: Map<FuelType, Long> = emptyMap(),
) {
    val currency = stringResource(R.string.currency_prefix)
    val perKm = stringResource(R.string.unit_per_km)
    val stacked = LocalDensity.current.fontScale > STACK_FONT_SCALE
    val tileColor = MaterialTheme.colorScheme.surface
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        InputSection(title = stringResource(R.string.vehicle_field_type)) {
            ChoiceGrid(
                label = null,
                options = VehicleType.entries,
                selected = state.type,
                optionLabel = { stringResource(it.label) },
                onSelect = { onChange(state.copy(type = it)) },
                optionIcon = { it.icon },
                largeIcons = true,
                tileColor = tileColor,
            )
        }

        val nameLabel = stringResource(R.string.vehicle_field_name)
        val nameError = errors[VehicleField.NAME]?.let { fieldErrorText(it, R.string.vehicle_error_name_required) }
        InputSection(title = nameLabel) {
            TextBox(
                value = state.name,
                onValueChange = { onChange(state.copy(name = it)) },
                label = nameLabel,
                placeholder = stringResource(R.string.vehicle_field_name_placeholder),
                maxLength = VEHICLE_NAME_MAX_LENGTH,
                error = nameError,
            )
            if (nameError != null) ErrorText(nameError)
        }

        val plateLabel = stringResource(R.string.vehicle_field_plate)
        val plateError = errors[VehicleField.PLATE]?.let { fieldErrorText(it, R.string.vehicle_error_plate_required) }
        InputSection(title = plateLabel) {
            TextBox(
                value = state.plateNumber,
                onValueChange = { onChange(state.copy(plateNumber = it)) },
                label = plateLabel,
                placeholder = stringResource(R.string.vehicle_field_plate_placeholder),
                maxLength = PLATE_NUMBER_MAX_LENGTH,
                capitalization = KeyboardCapitalization.Characters,
                error = plateError,
            )
            if (plateError != null) ErrorText(plateError)
            HelpText(stringResource(R.string.vehicle_field_plate_help))
        }

        InputSection(title = stringResource(R.string.vehicle_field_fuel_type)) {
            ChoiceGrid(
                label = null,
                options = FuelType.entries,
                selected = state.fuelType,
                optionLabel = { stringResource(it.label) },
                onSelect = { onChange(state.withFuelType(it, fuelPriceSuggestions)) },
                tileColor = tileColor,
            )
        }

        val priceLabel = stringResource(R.string.vehicle_field_fuel_price)
        val priceError = errors[VehicleField.FUEL_PRICE]?.let { fieldErrorText(it, R.string.vehicle_error_fuel_price_required) }
        InputSection(title = priceLabel, action = { UnitLabel(stringResource(R.string.unit_per_litre)) }) {
            ValueBox(
                value = state.fuelPrice,
                onValueChange = { onChange(state.copy(fuelPrice = it)) },
                label = priceLabel,
                error = priceError,
                prefix = currency,
                placeholder = stringResource(R.string.vehicle_field_fuel_price_placeholder),
                stacked = stacked,
                buttons = {
                    StepButton(
                        text = stringResource(R.string.vehicle_price_step_down, FUEL_PRICE_STEP_RUPEES),
                        description = stringResource(R.string.vehicle_price_down_description, FUEL_PRICE_STEP_RUPEES),
                        onClick = { onChange(state.stepFuelPrice(-FUEL_PRICE_STEP_RUPEES)) },
                    )
                    StepButton(
                        text = stringResource(R.string.check_fare_add, FUEL_PRICE_STEP_RUPEES),
                        description = stringResource(R.string.vehicle_price_up_description, FUEL_PRICE_STEP_RUPEES),
                        onClick = { onChange(state.stepFuelPrice(FUEL_PRICE_STEP_RUPEES)) },
                    )
                },
            )
            if (priceError != null) ErrorText(priceError)
            HelpText(stringResource(R.string.vehicle_field_fuel_price_help))
        }

        val efficiencyLabel = stringResource(R.string.vehicle_field_efficiency)
        val efficiencyUnit = stringResource(R.string.unit_km_per_litre)
        val efficiencyError = errors[VehicleField.EFFICIENCY]?.let { fieldErrorText(it, R.string.vehicle_error_efficiency_required) }
        InputSection(title = efficiencyLabel, action = { UnitLabel(efficiencyUnit) }) {
            ValueBox(
                value = state.efficiency,
                onValueChange = { onChange(state.copy(efficiency = it)) },
                label = efficiencyLabel,
                error = efficiencyError,
                unit = efficiencyUnit,
                maxDecimals = 1,
                maxIntegerDigits = InputDigits.EFFICIENCY,
            )
            if (efficiencyError != null) ErrorText(efficiencyError)
            HelpText(stringResource(R.string.vehicle_field_efficiency_help))
        }

        val maintenanceLabel = stringResource(R.string.vehicle_field_maintenance)
        val maintenanceError = errors[VehicleField.MAINTENANCE]?.let { fieldErrorText(it, R.string.vehicle_error_invalid) }
        InputSection(title = maintenanceLabel, action = { UnitLabel(perKm) }) {
            ValueBox(
                value = state.maintenance,
                onValueChange = { onChange(state.copy(maintenance = it)) },
                label = maintenanceLabel,
                error = maintenanceError,
                prefix = currency,
                unit = perKm,
                placeholder = stringResource(R.string.amount_placeholder_zero),
            )
            if (maintenanceError != null) ErrorText(maintenanceError)
            HelpText(stringResource(R.string.vehicle_field_maintenance_help))
        }

        val targetLabel = stringResource(R.string.vehicle_field_target)
        val targetError = errors[VehicleField.TARGET]?.let { fieldErrorText(it, R.string.vehicle_error_target_required) }
        InputSection(title = targetLabel, action = { UnitLabel(perKm) }) {
            ValueBox(
                value = state.target,
                onValueChange = { onChange(state.copy(target = it)) },
                label = targetLabel,
                error = targetError,
                prefix = currency,
                unit = perKm,
                placeholder = stringResource(R.string.vehicle_field_target_placeholder),
                imeAction = ImeAction.Done,
            )
            if (targetError != null) ErrorText(targetError)
            HelpText(stringResource(R.string.vehicle_field_target_help))
        }
    }
}

/** The unit on the right of a card's title ("per litre", "/km"). */
@Composable
private fun UnitLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun fieldErrorText(error: FieldError, requiredMessage: Int): String = stringResource(
    when (error) {
        FieldError.REQUIRED -> requiredMessage
        FieldError.MUST_BE_ABOVE_ZERO -> R.string.vehicle_error_above_zero
        FieldError.INVALID -> R.string.vehicle_error_invalid
    },
)

/** Longest vehicle name the form accepts (as the text field before). */
private const val VEHICLE_NAME_MAX_LENGTH = 40

/** How far the fuel price buttons move the price, in rupees. */
private const val FUEL_PRICE_STEP_RUPEES = 5L
