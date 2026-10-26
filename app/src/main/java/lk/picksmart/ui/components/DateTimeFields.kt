package lk.picksmart.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import lk.picksmart.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor

/** A date shown like an input; tapping it opens the date picker. Dates after [maxDate] can't be picked. */
@Composable
fun DateField(
    label: String,
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    maxDate: LocalDate? = null,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    PickerField(label, formatPattern(date, stringResource(R.string.pattern_date_field)), { open = true }, modifier)
    if (!open) return

    PickDateDialog(
        initial = date,
        onPick = onDateChange,
        onDismiss = { open = false },
        maxDate = maxDate,
    )
}

/**
 * The date picker on its own, for a row that isn't a [DateField] (such as Profile's date of birth).
 * Only dates from [minDate] to [maxDate] can be picked. With no [initial] date it opens on
 * [shownWhenEmpty]'s month. [onRemove], when given, adds a Remove button that clears the date.
 */
@Composable
fun PickDateDialog(
    initial: LocalDate?,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
    shownWhenEmpty: LocalDate? = null,
    onRemove: (() -> Unit)? = null,
) {
    // The Material date picker works in UTC midnight millis.
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.let(::utcMillis),
        initialDisplayedMonthMillis = (initial ?: shownWhenEmpty)?.let(::utcMillis),
        yearRange = (minDate?.year ?: DatePickerDefaults.YearRange.first)..(maxDate?.year ?: DatePickerDefaults.YearRange.last),
        selectableDates = remember(minDate, maxDate) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val day = utcDate(utcTimeMillis)
                    return (minDate == null || day >= minDate) && (maxDate == null || day <= maxDate)
                }

                override fun isSelectableYear(year: Int) =
                    (minDate == null || year >= minDate.year) && (maxDate == null || year <= maxDate.year)
            }
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(utcDate(it)) }
                onDismiss()
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            Row {
                if (onRemove != null) {
                    TextButton(onClick = {
                        onRemove()
                        onDismiss()
                    }) { Text(stringResource(R.string.action_remove)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    ) {
        DatePicker(state)
    }
}

/** A time shown like an input; tapping it opens the time picker, in the phone's 12/24-hour style. */
@OptIn(ExperimentalMaterial3Api::class) // TimePicker is still experimental in Material 3.
@Composable
fun TimeField(
    label: String,
    time: LocalTime,
    onTimeChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    PickerField(label, formatPattern(time, stringResource(R.string.pattern_time)), { open = true }, modifier)
    if (!open) return

    val state = rememberTimePickerState(
        initialHour = time.hour,
        initialMinute = time.minute,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    AlertDialog(
        onDismissRequest = { open = false },
        confirmButton = {
            TextButton(onClick = {
                onTimeChange(LocalTime.of(state.hour, state.minute))
                open = false
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
        text = { TimePicker(state) },
    )
}

/** Looks like a text field but opens a picker; screen readers hear one button with its value. */
@Composable
private fun PickerField(label: String, value: String, onClick: () -> Unit, modifier: Modifier) {
    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {},
        )
        // Covers the field so a tap opens the picker instead of placing a cursor.
        Box(
            Modifier
                .matchParentSize()
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = "$label, $value" },
        )
    }
}

/** [value] in a date or time [pattern] from strings.xml, in the language the app is showing. */
@Composable
fun formatPattern(value: TemporalAccessor, pattern: String): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(value, pattern, locale) { DateTimeFormatter.ofPattern(pattern, locale).format(value) }
}

private fun utcDate(utcMillis: Long): LocalDate = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()

private fun utcMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
