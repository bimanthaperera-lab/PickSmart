package lk.picksmart.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import lk.picksmart.R
import lk.picksmart.util.parseDecimal
import lk.picksmart.util.parseRupeesToCents

/** Why a number in a record form can't be saved. */
enum class FieldError { REQUIRED, INVALID, ZERO }

/** The problem with a required rupee amount that must be above 0, or null when it's fine. */
fun positiveCentsError(text: String): FieldError? = when {
    text.isBlank() -> FieldError.REQUIRED
    else -> when (parseRupeesToCents(text)) {
        null -> FieldError.INVALID
        0L -> FieldError.ZERO
        else -> null
    }
}

/** The problem with a required quantity (litres, say) that must be above 0, or null when it's fine. */
fun positiveDecimalError(text: String): FieldError? = when {
    text.isBlank() -> FieldError.REQUIRED
    else -> when (val value = parseDecimal(text)) {
        null -> FieldError.INVALID
        else -> if (value > 0) null else FieldError.ZERO
    }
}

/** Screen text for [this], with [required] naming what to enter. */
@Composable
fun FieldError.message(@StringRes required: Int): String = stringResource(
    when (this) {
        FieldError.REQUIRED -> required
        FieldError.INVALID -> R.string.vehicle_error_invalid
        FieldError.ZERO -> R.string.income_error_amount_zero
    },
)
