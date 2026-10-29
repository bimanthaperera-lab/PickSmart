package lk.picksmart.ui.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import lk.picksmart.R
import lk.picksmart.ui.checkride.ErrorText
import lk.picksmart.ui.checkride.HelpText
import lk.picksmart.ui.checkride.InputSection
import lk.picksmart.ui.checkride.NoteBox
import lk.picksmart.ui.checkride.NoteCounter
import lk.picksmart.ui.checkride.QuickAddRow
import lk.picksmart.ui.checkride.ValueBox
import lk.picksmart.util.addRupeesToInput
import lk.picksmart.ui.vehicle.RecordVehicleField
import lk.picksmart.domain.model.Expense
import lk.picksmart.domain.model.ExpenseCategory
import lk.picksmart.ui.components.ChoiceGrid
import lk.picksmart.ui.components.DateField
import lk.picksmart.ui.components.DetailHeader
import lk.picksmart.ui.components.EmptyState
import lk.picksmart.ui.components.ErrorState
import lk.picksmart.ui.components.LoadingState
import lk.picksmart.ui.components.MonthTotalCard
import lk.picksmart.ui.components.PrimaryButton
import lk.picksmart.ui.components.RecordRow
import lk.picksmart.ui.components.SectionLabel
import lk.picksmart.ui.components.message
import lk.picksmart.ui.components.neutralRecordIcon
import lk.picksmart.ui.components.rememberMoneyFormatter
import lk.picksmart.ui.components.rememberRecordDate
import lk.picksmart.ui.theme.Spacing
import java.time.LocalDate
import lk.picksmart.util.InputDigits

/** Expenses: this month's total, the entries newest first, and "Add expense". */
@Composable
fun ExpenseListScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (expenseId: Long) -> Unit,
    viewModel: ExpenseListViewModel = viewModel(factory = ExpenseListViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val money = rememberMoneyFormatter()
    Column(Modifier.fillMaxSize()) {
        DetailHeader(stringResource(R.string.expense_title), R.drawable.ic_arrow_back, stringResource(R.string.action_back), onBack)
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            when (val s = state) {
                ExpenseListUiState.Loading -> item { LoadingState() }
                is ExpenseListUiState.Loaded -> {
                    item {
                        MonthTotalCard(
                            label = stringResource(R.string.expense_month_total),
                            amount = money.format(s.monthTotalCents),
                            note = null,
                        )
                    }
                    if (s.entries.isEmpty()) {
                        item {
                            EmptyState(
                                iconRes = R.drawable.ic_add,
                                title = stringResource(R.string.expense_empty_title),
                                body = stringResource(R.string.expense_help),
                                modifier = Modifier.padding(top = Spacing.m),
                            )
                        }
                    } else {
                        item { SectionLabel(stringResource(R.string.expense_recent)) }
                        items(s.entries, key = { it.id }) { entry -> ExpenseRow(entry, s.today) { onOpen(entry.id) } }
                        item {
                            Text(
                                stringResource(R.string.expense_help),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = Spacing.m),
                            )
                        }
                    }
                }
            }
        }
        PrimaryButton(
            stringResource(R.string.expense_add),
            onAdd,
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen, vertical = Spacing.m),
            iconRes = R.drawable.ic_add,
        )
    }
}

@Composable
private fun ExpenseRow(entry: Expense, today: LocalDate, onClick: () -> Unit) {
    val date = rememberRecordDate(entry.date, today)
    RecordRow(
        icon = neutralRecordIcon(entry.category.icon),
        title = stringResource(entry.category.label),
        subtitle = entry.note?.let { stringResource(R.string.income_row_subtitle, date, it) } ?: date,
        amount = rememberMoneyFormatter().format(entry.amountCents),
        onClick = onClick,
    )
}

@Composable
fun EditExpenseScreen(
    onClose: () -> Unit,
    viewModel: EditExpenseViewModel = viewModel(factory = EditExpenseViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val editing = state as? EditExpenseUiState.Editing
    LaunchedEffect(editing?.done) { if (editing?.done == true) onClose() }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        DetailHeader(
            stringResource(if (editing?.isNew == false) R.string.expense_edit_title else R.string.expense_add_title),
            R.drawable.ic_close,
            stringResource(R.string.action_close),
            onClose,
        ) {
            if (editing?.isNew == false) {
                IconButton(onClick = { confirmDelete = true }, enabled = !editing.saving) {
                    Icon(
                        painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.expense_delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            when (val s = state) {
                EditExpenseUiState.Loading -> LoadingState()
                EditExpenseUiState.NotFound -> EmptyState(
                    iconRes = R.drawable.ic_info,
                    title = stringResource(R.string.expense_not_found_title),
                    body = stringResource(R.string.expense_not_found_body),
                    actionLabel = stringResource(R.string.action_close),
                    onAction = onClose,
                )
                is EditExpenseUiState.Editing -> {
                    RecordVehicleField(s.vehicle, viewModel::selectVehicle, card = true)
                    ExpenseForm(s, viewModel::updateForm)
                }
            }
        }
        if (editing != null) {
            Column(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.m)) {
                if (editing.saveFailed) ErrorState(stringResource(R.string.expense_save_failed), Modifier.padding(bottom = Spacing.m))
                PrimaryButton(stringResource(R.string.expense_save), viewModel::save, Modifier.fillMaxWidth(), enabled = !editing.saving)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.expense_delete_title)) },
            text = { Text(stringResource(R.string.expense_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/**
 * Add / Edit expense in Check a Ride's card style: the category tiles, the amount with quick-add
 * buttons, the date, the odometer (maintenance only) and the note, each on its own card.
 */
@Composable
private fun ExpenseForm(state: EditExpenseUiState.Editing, onChange: (ExpenseFormState) -> Unit) {
    val form = state.form
    InputSection(title = stringResource(R.string.expense_category)) {
        ChoiceGrid(
            label = null,
            options = ExpenseCategory.entries,
            selected = form.category,
            optionLabel = { stringResource(it.label) },
            onSelect = { onChange(form.copy(category = it)) },
            optionIcon = { it.icon },
            optionSubtitle = { stringResource(it.hint) },
            tileColor = MaterialTheme.colorScheme.surface,
        )
    }
    val amountLabel = stringResource(R.string.expense_amount)
    val amountError = state.amountError?.message(R.string.income_error_amount_required)
    InputSection(title = amountLabel) {
        ValueBox(
            value = form.amount,
            onValueChange = { onChange(form.copy(amount = it)) },
            label = amountLabel,
            error = amountError,
            prefix = stringResource(R.string.currency_prefix),
            textStyle = MaterialTheme.typography.headlineMedium,
            onClear = { onChange(form.copy(amount = "")) },
        )
        if (amountError != null) ErrorText(amountError)
        QuickAddRow(onAdd = { onChange(form.copy(amount = addRupeesToInput(form.amount, it))) })
    }
    DateField(
        stringResource(R.string.expense_date),
        form.date,
        { onChange(form.copy(date = it)) },
        Modifier.fillMaxWidth(),
        maxDate = state.today,
        tile = true,
    )
    if (form.showsOdometer) {
        val odometerLabel = stringResource(R.string.expense_odometer)
        val odometerError = state.odometerError?.message(R.string.vehicle_error_invalid)
        InputSection(title = odometerLabel) {
            ValueBox(
                value = form.odometer,
                onValueChange = { onChange(form.copy(odometer = it)) },
                label = odometerLabel,
                error = odometerError,
                unit = stringResource(R.string.unit_km),
                leadingIcon = R.drawable.ic_gauge,
                maxDecimals = 1,
                maxIntegerDigits = InputDigits.ODOMETER,
            )
            if (odometerError != null) ErrorText(odometerError)
            HelpText(stringResource(R.string.expense_odometer_help))
        }
    }
    val noteLabel = stringResource(R.string.expense_note)
    InputSection(title = noteLabel, action = { NoteCounter(form.note.length, NOTE_MAX_LENGTH) }) {
        NoteBox(
            value = form.note,
            onValueChange = { onChange(form.copy(note = it)) },
            label = noteLabel,
            maxLength = NOTE_MAX_LENGTH,
            placeholder = stringResource(R.string.expense_note_placeholder),
        )
    }
}

private const val NOTE_MAX_LENGTH = 100
