package lk.picksmart.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import lk.picksmart.PicksmartApplication
import lk.picksmart.data.repository.AlertRepository
import lk.picksmart.domain.model.Alert

sealed interface AlertsUiState {
    data object Loading : AlertsUiState

    /** [newIds] were unread when the inbox opened; they keep their "New" label while it is open. */
    data class Loaded(val alerts: List<Alert>, val newIds: Set<Long>) : AlertsUiState
}

/** The alerts inbox. Opening it marks every alert read (CLAUDE.md section 9a). */
class AlertsViewModel(alerts: AlertRepository) : ViewModel() {
    private val newIds = MutableStateFlow<Set<Long>?>(null)

    init {
        viewModelScope.launch {
            newIds.value = alerts.all.first().filterNot { it.read }.mapTo(mutableSetOf()) { it.id }
            alerts.markAllRead()
        }
    }

    val state: StateFlow<AlertsUiState> = combine(alerts.all, newIds.filterNotNull()) { list, fresh ->
        AlertsUiState.Loaded(list, fresh)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlertsUiState.Loading)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as PicksmartApplication).container
                AlertsViewModel(container.alerts)
            }
        }
    }
}
