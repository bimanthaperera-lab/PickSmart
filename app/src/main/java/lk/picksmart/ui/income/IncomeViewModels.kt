package lk.picksmart.ui.income

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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import lk.picksmart.PicksmartApplication
import lk.picksmart.data.repository.OtherIncomeRepository
import lk.picksmart.domain.model.OtherIncome
import lk.picksmart.ui.navigation.EditIncomeRoute
import lk.picksmart.util.ColomboZone
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

sealed interface IncomeListUiState {
    data object Loading : IncomeListUiState
    data class Loaded(val entries: List<OtherIncome>, val monthTotalCents: Long, val today: LocalDate) : IncomeListUiState
}

class IncomeListViewModel(income: OtherIncomeRepository, clock: Clock) : ViewModel() {
    val state: StateFlow<IncomeListUiState> = income.all.map { entries ->
        val today = clock.instant().atZone(ColomboZone).toLocalDate()
        IncomeListUiState.Loaded(entries, entries.totalInMonth(YearMonth.from(today)), today)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IncomeListUiState.Loading)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as PicksmartApplication).container
                IncomeListViewModel(container.otherIncome, container.clock)
            }
        }
    }
}

sealed interface EditIncomeUiState {
    data object Loading : EditIncomeUiState
    data object NotFound : EditIncomeUiState

    data class Editing(
        val form: IncomeFormState,
        val isNew: Boolean,
        val today: LocalDate,
        val amountError: AmountError? = null,
        val saving: Boolean = false,
        val saveFailed: Boolean = false,
        val done: Boolean = false,
    ) : EditIncomeUiState
}

/** Adds an income entry, or edits one when [EditIncomeRoute.incomeId] isn't [EditIncomeRoute.NEW]. */
class EditIncomeViewModel(
    savedStateHandle: SavedStateHandle,
    private val income: OtherIncomeRepository,
    clock: Clock,
) : ViewModel() {

    private val incomeId = savedStateHandle.toRoute<EditIncomeRoute>().incomeId

    private val _state = MutableStateFlow<EditIncomeUiState>(EditIncomeUiState.Loading)
    val state: StateFlow<EditIncomeUiState> = _state.asStateFlow()

    init {
        val today = clock.instant().atZone(ColomboZone).toLocalDate()
        viewModelScope.launch {
            _state.value = if (incomeId == EditIncomeRoute.NEW) {
                EditIncomeUiState.Editing(IncomeFormState(date = today), isNew = true, today = today)
            } else {
                income.get(incomeId)?.let { EditIncomeUiState.Editing(it.toFormState(), isNew = false, today = today) }
                    ?: EditIncomeUiState.NotFound
            }
        }
    }

    fun updateForm(form: IncomeFormState) = editing { it.copy(form = form, amountError = null, saveFailed = false) }

    fun save() {
        val current = _state.value as? EditIncomeUiState.Editing ?: return
        if (current.saving) return
        val input = when (val result = current.form.validate()) {
            is IncomeFormResult.Invalid -> return editing { it.copy(amountError = result.amountError) }
            is IncomeFormResult.Valid -> result.input
        }
        run(current) {
            if (current.isNew) {
                income.create(input)
                true
            } else {
                income.update(incomeId, input)
            }
        }
    }

    /** Called only after the rider confirms in the dialog (CLAUDE.md N7). */
    fun delete() {
        val current = _state.value as? EditIncomeUiState.Editing ?: return
        if (current.isNew || current.saving) return
        run(current) {
            income.delete(incomeId)
            true
        }
    }

    private fun run(current: EditIncomeUiState.Editing, action: suspend () -> Boolean) {
        _state.value = current.copy(saving = true, saveFailed = false)
        viewModelScope.launch {
            try {
                _state.value = if (action()) current.copy(saving = false, done = true) else EditIncomeUiState.NotFound
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = current.copy(saving = false, saveFailed = true)
            }
        }
    }

    private fun editing(change: (EditIncomeUiState.Editing) -> EditIncomeUiState.Editing) =
        _state.update { if (it is EditIncomeUiState.Editing) change(it) else it }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as PicksmartApplication).container
                EditIncomeViewModel(createSavedStateHandle(), container.otherIncome, container.clock)
            }
        }
    }
}
