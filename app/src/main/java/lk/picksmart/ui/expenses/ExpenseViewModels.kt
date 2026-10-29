package lk.picksmart.ui.expenses

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import lk.picksmart.PicksmartApplication
import lk.picksmart.data.repository.ExpenseRepository
import lk.picksmart.data.repository.VehicleRepository
import lk.picksmart.domain.model.Expense
import lk.picksmart.ui.components.FieldError
import lk.picksmart.ui.navigation.EditExpenseRoute
import lk.picksmart.ui.vehicle.RecordVehicle
import lk.picksmart.util.ColomboZone
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

sealed interface ExpenseListUiState {
    data object Loading : ExpenseListUiState
    data class Loaded(val entries: List<Expense>, val monthTotalCents: Long, val today: LocalDate) : ExpenseListUiState
}

class ExpenseListViewModel(expenses: ExpenseRepository, clock: Clock) : ViewModel() {
    val state: StateFlow<ExpenseListUiState> = expenses.all.map { entries ->
        val today = clock.instant().atZone(ColomboZone).toLocalDate()
        ExpenseListUiState.Loaded(entries, entries.totalInMonth(YearMonth.from(today)), today)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpenseListUiState.Loading)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as PicksmartApplication).container
                ExpenseListViewModel(container.expenses, container.clock)
            }
        }
    }
}

sealed interface EditExpenseUiState {
    data object Loading : EditExpenseUiState
    data object NotFound : EditExpenseUiState

    /** @property vehicle A saved expense's vehicle; a new one goes on the active vehicle. */
    data class Editing(
        val form: ExpenseFormState,
        val isNew: Boolean,
        val today: LocalDate,
        val vehicle: RecordVehicle = RecordVehicle(),
        val amountError: FieldError? = null,
        val odometerError: FieldError? = null,
        val saving: Boolean = false,
        val saveFailed: Boolean = false,
        val done: Boolean = false,
    ) : EditExpenseUiState
}

/** Adds an expense, or edits one when [EditExpenseRoute.expenseId] isn't [EditExpenseRoute.NEW]. */
class EditExpenseViewModel(
    savedStateHandle: SavedStateHandle,
    private val expenses: ExpenseRepository,
    private val vehicles: VehicleRepository,
    clock: Clock,
) : ViewModel() {

    private val expenseId = savedStateHandle.toRoute<EditExpenseRoute>().expenseId

    /** The active vehicle when adding, or null when there is none. */
    private var newVehicleId: Long? = null

    private val _state = MutableStateFlow<EditExpenseUiState>(EditExpenseUiState.Loading)
    val state: StateFlow<EditExpenseUiState> = _state.asStateFlow()

    init {
        val today = clock.instant().atZone(ColomboZone).toLocalDate()
        viewModelScope.launch {
            _state.value = if (expenseId == EditExpenseRoute.NEW) {
                // A new expense belongs to the active vehicle, when there is one.
                newVehicleId = vehicles.defaultVehicle.first()?.id
                EditExpenseUiState.Editing(ExpenseFormState(date = today), isNew = true, today = today)
            } else {
                expenses.get(expenseId)?.let { expense ->
                    val choices = vehicles.activeVehicles.first()
                    val vehicle = RecordVehicle(choices, expense.vehicleId, expense.vehicleId?.let { vehicles.get(it)?.name })
                    EditExpenseUiState.Editing(expense.toFormState(), isNew = false, today = today, vehicle = vehicle)
                } ?: EditExpenseUiState.NotFound
            }
        }
    }

    fun updateForm(form: ExpenseFormState) =
        editing { it.copy(form = form, amountError = null, odometerError = null, saveFailed = false) }

    fun save() {
        val current = _state.value as? EditExpenseUiState.Editing ?: return
        if (current.saving) return
        val input = when (val result = current.form.validate()) {
            is ExpenseFormResult.Invalid ->
                return editing { it.copy(amountError = result.amountError, odometerError = result.odometerError) }
            is ExpenseFormResult.Valid -> result.input
        }
        run(current) {
            if (current.isNew) {
                expenses.create(newVehicleId, input)
                true
            } else {
                expenses.update(expenseId, input)
            }
        }
    }

    /** Called only after the rider confirms in the dialog (CLAUDE.md N7). */
    fun delete() {
        val current = _state.value as? EditExpenseUiState.Editing ?: return
        if (current.isNew || current.saving) return
        run(current) {
            expenses.delete(expenseId)
            true
        }
    }

    private fun run(current: EditExpenseUiState.Editing, action: suspend () -> Boolean) {
        _state.value = current.copy(saving = true, saveFailed = false)
        viewModelScope.launch {
            try {
                _state.value = if (action()) current.copy(saving = false, done = true) else EditExpenseUiState.NotFound
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = current.copy(saving = false, saveFailed = true)
            }
        }
    }

    private fun editing(change: (EditExpenseUiState.Editing) -> EditExpenseUiState.Editing) =
        _state.update { if (it is EditExpenseUiState.Editing) change(it) else it }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as PicksmartApplication).container
                EditExpenseViewModel(createSavedStateHandle(), container.expenses, container.vehicles, container.clock)
            }
        }
    }
}
