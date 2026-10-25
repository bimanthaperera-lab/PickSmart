package lk.picksmart.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import lk.picksmart.util.sanitizeDecimalInput

/**
 * A number input with a numeric keyboard. Typing is cleaned with [sanitizeDecimalInput], so the
 * value is always empty or a non-negative number. Use [prefix] for "Rs." and [suffix] for units
 * such as "km", and [trailing] for a small control at the field's end.
 *
 * @param error Shown under the field and announced by screen readers; null when valid.
 */
@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    prefix: String? = null,
    suffix: String? = null,
    maxDecimals: Int = 2,
    supportingText: String? = null,
    error: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    trailing: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(sanitizeDecimalInput(it, maxDecimals)) },
        label = { FieldLabelText(label) },
        placeholder = placeholder?.let { { Text(it) } },
        prefix = prefix?.let { { Text(it) } },
        suffix = suffix?.let { { Text(it) } },
        trailingIcon = trailing,
        supportingText = (error ?: supportingText)?.let { { Text(it) } },
        isError = error != null,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleLarge,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (maxDecimals > 0) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = imeAction,
        ),
        shape = MaterialTheme.shapes.medium,
        colors = fieldColors(),
        modifier = modifier
            .fillMaxWidth()
            .then(if (error != null) Modifier.semantics { error(error) } else Modifier),
    )
}

/** A single-line text input, e.g. a vehicle name. */
@Composable
fun TextInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
    maxLength: Int = 40,
    imeAction: ImeAction = ImeAction.Next,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Words,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(maxLength)) },
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = supportingText?.let { { Text(it) } },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(capitalization = capitalization, imeAction = imeAction),
        shape = MaterialTheme.shapes.medium,
        colors = fieldColors(),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * A field's label on one line. In a narrow field with a [NumberField] `trailing` control (the fee's
 * "Rs." / "%" switch) a label such as "Platform fee" would otherwise wrap onto two lines while the
 * field is empty and break the row; it shrinks a little to fit instead.
 */
@Composable
private fun FieldLabelText(text: String) {
    val size = LocalTextStyle.current.fontSize.takeIf { it.isSpecified } ?: 16.sp
    Text(
        text,
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = size, stepSize = 0.5.sp),
    )
}

@Composable
internal fun fieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    errorContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
)
