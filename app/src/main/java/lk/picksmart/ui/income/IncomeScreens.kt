package lk.picksmart.ui.income

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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import lk.picksmart.R
import lk.picksmart.domain.model.IncomeType
import lk.picksmart.domain.model.OtherIncome
import lk.picksmart.domain.model.Platform
import lk.picksmart.ui.components.ChoiceChips
import lk.picksmart.ui.components.ChoiceGrid
import lk.picksmart.ui.components.DateField
import lk.picksmart.ui.components.DetailHeader
import lk.picksmart.ui.components.EmptyState
import lk.picksmart.ui.components.ErrorState
import lk.picksmart.ui.components.FieldLabel
import lk.picksmart.ui.components.LoadingState
import lk.picksmart.ui.components.MonthTotalCard
import lk.picksmart.ui.components.NumberField
import lk.picksmart.ui.components.PrimaryButton
import lk.picksmart.ui.components.RecordRow
import lk.picksmart.ui.components.SectionLabel
import lk.picksmart.ui.components.TextInputField
import lk.picksmart.ui.components.rememberRecordDate
import lk.picksmart.ui.components.rememberMoneyFormatter
import lk.picksmart.ui.theme.PicksmartTheme
import lk.picksmart.ui.theme.Spacing
import lk.picksmart.ui.trip.label
import java.time.LocalDate

/** Other income: this month's total, the entries newest first, and "Add income". */
@Composable
fun IncomeListScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (incomeId: Long) -> Unit,
    viewModel: IncomeListViewModel = viewModel(factory = IncomeListViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        DetailHeader(stringResource(R.string.income_title), R.drawable.ic_arrow_back, stringResource(R.string.action_back), onBack)
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            when (val s = state) {
                IncomeListUiState.Loading -> item { LoadingState() }
                is IncomeListUiState.Loaded -> {
                    item { MonthTotal(s.monthTotalCents) }
                    if (s.entries.isEmpty()) {
                        item {
                            EmptyState(
                                iconRes = R.drawable.ic_add,
                                title = stringResource(R.string.income_empty_title),
                                body = stringResource(R.string.income_help),
                                modifier = Modifier.padding(top = Spacing.m),
                            )
                        }
                    } else {
                        item { SectionLabel(stringResource(R.string.income_recent)) }
                        items(s.entries, key = { it.id }) { entry -> IncomeRow(entry, s.today) { onOpen(entry.id) } }
                        item {
                            Text(
                                stringResource(R.string.income_help),
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
            stringResource(R.string.income_add),
            onAdd,
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen, vertical = Spacing.m),
            iconRes = R.drawable.ic_add,
        )
    }
}

@Composable
private fun IncomeRow(entry: OtherIncome, today: LocalDate, onClick: () -> Unit) {
    val money = rememberMoneyFormatter()
    val date = rememberRecordDate(entry.date, today)
    val type = stringResource(entry.type.label)
    val title = entry.platform?.let { stringResource(R.string.history_row_title, type, stringResource(it.label)) } ?: type
    RecordRow(
        title = title,
        subtitle = entry.note?.let { stringResource(R.string.income_row_subtitle, date, it) } ?: date,
        amount = money.formatSigned(entry.amountCents),
        onClick = onClick,
        amountColor = PicksmartTheme.status.profit,
    )
}

@Composable
private fun MonthTotal(cents: Long) {
    MonthTotalCard(
        label = stringResource(R.string.income_month_total),
        amount = rememberMoneyFormatter().formatSigned(cents),
        note = stringResource(R.string.income_month_note),
        amountColor = PicksmartTheme.status.profit,
    )
}

@Composable
fun EditIncomeScreen(
    onClose: () -> Unit,
    viewModel: EditIncomeViewModel = viewModel(factory = EditIncomeViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val editing = state as? EditIncomeUiState.Editing
    LaunchedEffect(editing?.done) { if (editing?.done == true) onClose() }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        DetailHeader(
            stringResource(if (editing?.isNew == false) R.string.income_edit_title else R.string.income_add_title),
            R.drawable.ic_close,
            stringResource(R.string.action_close),
            onClose,
        ) {
            if (editing?.isNew == false) {
                IconButton(onClick = { confirmDelete = true }, enabled = !editing.saving) {
                    Icon(
                        painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.income_delete),
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
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            when (val s = state) {
                EditIncomeUiState.Loading -> LoadingState()
                EditIncomeUiState.NotFound -> EmptyState(
                    iconRes = R.drawable.ic_info,
                    title = stringResource(R.string.income_not_found_title),
                    body = stringResource(R.string.income_not_found_body),
                    actionLabel = stringResource(R.string.action_close),
                    onAction = onClose,
                )
                is EditIncomeUiState.Editing -> IncomeForm(s, viewModel::updateForm)
            }
        }
        if (editing != null) {
            Column(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.m)) {
                if (editing.saveFailed) ErrorState(stringResource(R.string.income_save_failed), Modifier.padding(bottom = Spacing.m))
                PrimaryButton(stringResource(R.string.income_save), viewModel::save, Modifier.fillMaxWidth(), enabled = !editing.saving)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.income_delete_title)) },
            text = { Text(stringResource(R.string.income_delete_body)) },
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

@Composable
private fun IncomeForm(state: EditIncomeUiState.Editing, onChange: (IncomeFormState) -> Unit) {
    val form = state.form
    ChoiceGrid(
        label = stringResource(R.string.income_type),
        options = IncomeType.entries,
        selected = form.type,
        optionLabel = { stringResource(it.label) },
        onSelect = { onChange(form.copy(type = it)) },
    )
    NumberField(
        value = form.amount,
        onValueChange = { onChange(form.copy(amount = it)) },
        label = stringResource(R.string.income_amount),
        prefix = stringResource(R.string.currency_prefix),
        error = state.amountError?.let {
            stringResource(
                when (it) {
                    AmountError.REQUIRED -> R.string.income_error_amount_required
                    AmountError.INVALID -> R.string.vehicle_error_invalid
                    AmountError.ZERO -> R.string.income_error_amount_zero
                },
            )
        },
    )
    Column {
        FieldLabel(stringResource(R.string.income_platform))
        // Null is "None": income doesn't have to belong to a platform.
        ChoiceChips(
            options = listOf<Platform?>(null) + Platform.entries,
            selected = form.platform,
            optionLabel = { stringResource(it?.label ?: R.string.income_platform_none) },
            onSelect = { onChange(form.copy(platform = it)) },
        )
    }
    DateField(stringResource(R.string.income_date), form.date, { onChange(form.copy(date = it)) }, Modifier.fillMaxWidth(), maxDate = state.today)
    TextInputField(
        value = form.note,
        onValueChange = { onChange(form.copy(note = it)) },
        label = stringResource(R.string.income_note),
        placeholder = stringResource(R.string.income_note_placeholder),
        maxLength = NOTE_MAX_LENGTH,
        imeAction = ImeAction.Done,
    )
}

private const val NOTE_MAX_LENGTH = 100
