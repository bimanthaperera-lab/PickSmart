package lk.picksmart.ui.trip

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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import lk.picksmart.PicksmartApplication
import lk.picksmart.data.repository.TripRepository
import lk.picksmart.data.repository.VehicleRepository
import lk.picksmart.domain.calculator.ProfitCalculator
import lk.picksmart.domain.calculator.ProfitResult
import lk.picksmart.domain.model.Trip
import lk.picksmart.domain.model.VehicleType
import lk.picksmart.domain.usecase.estimate
import lk.picksmart.ui.navigation.TripDetailsRoute

sealed interface TripDetailsUiState {
    data object Loading : TripDetailsUiState

    /** The id is stale: the trip was never on this phone, or was deleted before the screen opened. */
    data object NotFound : TripDetailsUiState

    /** The trip was deleted while the screen was open (here, or from Edit trip); the screen closes. */
    data object Deleted : TripDetailsUiState

    /**
     * @property estimate The trip's result from its own rate snapshot; null only if the stored
     *   values can't be calculated.
     */
    data class Loaded(
        val trip: Trip,
        val vehicleName: String?,
        val vehicleType: VehicleType?,
        val estimate: ProfitResult?,
        val deleting: Boolean = false,
        val deleteFailed: Boolean = false,
    ) : TripDetailsUiState
}

/** One saved trip, read-only, kept current while the rider edits it. */
class TripDetailsViewModel(
    savedStateHandle: SavedStateHandle,
    private val trips: TripRepository,
    vehicles: VehicleRepository,
    calculator: ProfitCalculator,
) : ViewModel() {

    private val tripId = savedStateHandle.toRoute<TripDetailsRoute>().tripId
    private val deleting = MutableStateFlow(DeleteState.IDLE)
    private var wasLoaded = false

    val state: StateFlow<TripDetailsUiState> =
        combine(trips.observe(tripId), vehicles.allVehicles, deleting) { trip, vehicleList, delete ->
            when {
                trip == null -> if (wasLoaded || delete == DeleteState.RUNNING) TripDetailsUiState.Deleted else TripDetailsUiState.NotFound
                else -> {
                    wasLoaded = true
                    val vehicle = vehicleList.firstOrNull { it.id == trip.vehicleId }
                    TripDetailsUiState.Loaded(
                        trip = trip,
                        vehicleName = vehicle?.name,
                        vehicleType = vehicle?.type,
                        estimate = calculator.estimate(trip),
                        deleting = delete == DeleteState.RUNNING,
                        deleteFailed = delete == DeleteState.FAILED,
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripDetailsUiState.Loading)

    /** Called only after the rider confirms in the dialog (CLAUDE.md N7). */
    fun delete() {
        if (deleting.value == DeleteState.RUNNING) return
        deleting.value = DeleteState.RUNNING
        viewModelScope.launch {
            try {
                trips.delete(tripId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                deleting.value = DeleteState.FAILED
            }
        }
    }

    private enum class DeleteState { IDLE, RUNNING, FAILED }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as PicksmartApplication).container
                TripDetailsViewModel(createSavedStateHandle(), container.trips, container.vehicles, container.profitCalculator)
            }
        }
    }
}
