package lk.picksmart.ui.reports

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import lk.picksmart.PicksmartApplication
import lk.picksmart.data.export.CsvExporter
import lk.picksmart.data.repository.ExpenseRepository
import lk.picksmart.data.repository.FuelLogRepository
import lk.picksmart.data.repository.OtherIncomeRepository
import lk.picksmart.data.repository.TripRepository
import lk.picksmart.domain.calculator.ProfitCalculator
import lk.picksmart.domain.model.Expense
import lk.picksmart.domain.model.FuelLog
import lk.picksmart.domain.model.OtherIncome
import lk.picksmart.domain.model.Trip
import lk.picksmart.domain.usecase.DateRange
import lk.picksmart.domain.usecase.PeriodSummary
import lk.picksmart.domain.usecase.ReportPeriod
import lk.picksmart.domain.usecase.rangeContaining
import lk.picksmart.domain.usecase.shift
import lk.picksmart.domain.usecase.summarizeEachDay
import lk.picksmart.domain.usecase.summarizePeriod
import lk.picksmart.util.todayInColombo
import java.time.Clock
import java.time.LocalDate

/** The CSV export: idle, writing the files, or failed (shown under the title until the next try). */
enum class ExportStatus { IDLE, WORKING, FAILED }

/** Which of the two views in CLAUDE.md section 7 the report shows. They are never mixed. */
enum class ReportView { ESTIMATED, CASH }

sealed interface ReportsUiState {
    data object Loading : ReportsUiState

    /** Nothing has been recorded yet, in any period. */
    data object Empty : ReportsUiState

    /**
     * @property days One summary per day of [range] for the chart; empty for a single day.
     * @property isCurrent True when [range] contains today, so there is no later period to go to.
     */
    data class Loaded(
        val period: ReportPeriod,
        val view: ReportView,
        val range: DateRange,
        val today: LocalDate,
        val summary: PeriodSummary,
        val days: List<PeriodSummary>,
        val isCurrent: Boolean,
    ) : ReportsUiState
}

/**
 * Reports for a day, week or month, in the estimated or cash view. All totals come from
 * [summarizePeriod], the same use case Home uses, so the two always agree. Works offline.
 */
class ReportsViewModel(
    trips: TripRepository,
    otherIncome: OtherIncomeRepository,
    expenses: ExpenseRepository,
    fuelLogs: FuelLogRepository,
    calculator: ProfitCalculator,
    private val clock: Clock,
    private val exporter: CsvExporter,
) : ViewModel() {

    /** [start] is the first day of the period shown, or null for the one containing today. */
    private data class Selection(val period: ReportPeriod, val start: LocalDate?, val view: ReportView)

    private data class Records(
        val trips: List<Trip>,
        val income: List<OtherIncome>,
        val expenses: List<Expense>,
        val fuel: List<FuelLog>,
    )

    private val selection = MutableStateFlow(Selection(ReportPeriod.WEEK, null, ReportView.ESTIMATED))

    private val records = combine(trips.all, otherIncome.all, expenses.all, fuelLogs.all, ::Records)

    val state: StateFlow<ReportsUiState> = combine(records, selection) { r, sel ->
        if (r.trips.isEmpty() && r.income.isEmpty() && r.expenses.isEmpty() && r.fuel.isEmpty()) {
            return@combine ReportsUiState.Empty
        }
        val today = todayInColombo(clock)
        val range = sel.period.rangeContaining(sel.start ?: today)
        ReportsUiState.Loaded(
            period = sel.period,
            view = sel.view,
            range = range,
            today = today,
            summary = calculator.summarizePeriod(range, r.trips, r.income, r.expenses, r.fuel),
            days = if (sel.period == ReportPeriod.DAY) {
                emptyList()
            } else {
                calculator.summarizeEachDay(range, r.trips, r.income, r.expenses, r.fuel)
            },
            isCurrent = today in range,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsUiState.Loading)

    private val _exportStatus = MutableStateFlow(ExportStatus.IDLE)
    val exportStatus: StateFlow<ExportStatus> = _exportStatus.asStateFlow()

    private val exports = Channel<List<Uri>>(Channel.CONFLATED)

    /** The files of each finished export, once, for the share sheet. */
    val exportedFiles: Flow<List<Uri>> = exports.receiveAsFlow()

    /** Writes every record (all dates, not just the period shown) to CSV files to share. */
    fun export() {
        if (_exportStatus.value == ExportStatus.WORKING) return
        _exportStatus.value = ExportStatus.WORKING
        viewModelScope.launch {
            val files = runCatching { exporter.export(todayInColombo(clock)) }.getOrNull()
            if (files != null) exports.send(files)
            _exportStatus.value = if (files != null) ExportStatus.IDLE else ExportStatus.FAILED
        }
    }

    /** Switching between day, week and month keeps the date in view: the week of the day shown, and so on. */
    fun setPeriod(period: ReportPeriod) = selection.update {
        it.copy(period = period, start = it.start?.let { start -> startOrCurrent(period.rangeContaining(start)) })
    }

    fun setView(view: ReportView) = selection.update { it.copy(view = view) }

    fun previous() = move(-1)

    fun next() = move(1)

    private fun move(steps: Long) = selection.update {
        val start = it.start ?: it.period.rangeContaining(todayInColombo(clock)).start
        val range = it.period.shift(start, steps)
        // Never past the current period: nothing is recorded in the future.
        if (range.start.isAfter(todayInColombo(clock))) it else it.copy(start = startOrCurrent(range))
    }

    /** Null (follow today) when [range] is the current period, so it stays current past midnight. */
    private fun startOrCurrent(range: DateRange): LocalDate? = if (todayInColombo(clock) in range) null else range.start

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as PicksmartApplication).container
                ReportsViewModel(c.trips, c.otherIncome, c.expenses, c.fuelLogs, c.profitCalculator, c.clock, c.csvExporter)
            }
        }
    }
}
