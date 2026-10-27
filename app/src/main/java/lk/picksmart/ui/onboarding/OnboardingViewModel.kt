package lk.picksmart.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import lk.picksmart.PicksmartApplication
import lk.picksmart.data.account.AccountProvider
import lk.picksmart.data.preferences.UserPreferencesRepository
import lk.picksmart.data.sync.SyncProblem
import lk.picksmart.data.sync.SyncProvider
import lk.picksmart.data.sync.SyncStatus
import lk.picksmart.domain.model.FuelType
import lk.picksmart.domain.model.VehicleInput
import lk.picksmart.domain.usecase.CompleteOnboarding
import lk.picksmart.domain.usecase.FIRST_SPACE
import lk.picksmart.domain.usecase.fuelPriceSuggestions
import lk.picksmart.ui.vehicle.FieldError
import lk.picksmart.ui.vehicle.VehicleField
import lk.picksmart.ui.vehicle.VehicleFormResult
import lk.picksmart.ui.vehicle.VehicleFormState
import lk.picksmart.ui.vehicle.validate

enum class OnboardingStep { WELCOME, INTRO, RESTORE, VEHICLE, PERMISSIONS, SAFETY }

/** Bringing back a signed-in rider's backup: still running, or why it stopped. */
enum class RestoreState { RUNNING, OFFLINE, UNVERIFIED, FAILED }

/** How many "how it works" pages the intro has. */
const val INTRO_PAGE_COUNT = 3

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    /** The intro page on screen, 0-based; kept so back from the vehicle step returns to it. */
    val introPage: Int = 0,
    val form: VehicleFormState = VehicleFormState(),
    val errors: Map<VehicleField, FieldError> = emptyMap(),
    /** The vehicle to save when the rider accepts the safety notice. */
    val pending: VehicleInput? = null,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val restore: RestoreState = RestoreState.RUNNING,
    /** The rider's backup brought back a vehicle, so setup finishes without a new one. */
    val restored: Boolean = false,
)

/**
 * First-launch setup: welcome, the "how it works" intro, the rider's own vehicle numbers, the
 * optional permissions (notifications, Read ride offers), then the safety notice. A rider who
 * signs in from the welcome screen skips the intro: their backup is restored first, and if it
 * brings back a vehicle they go straight to the permissions. Nothing new is saved until the rider
 * accepts the notice; finishing flips the onboarding flag, which takes the app to Home.
 *
 * When another account signs in on a phone already set up, the app opens a new, empty space for
 * their records (`DataSpaces.kt`) and comes here signed in ([addingAccount]). Their backup is
 * restored and setup finishes at once; a new account sets up its vehicle and finishes. The
 * permissions and the safety notice were already seen on this phone, so they are skipped.
 */
class OnboardingViewModel(
    private val completeOnboarding: CompleteOnboarding,
    private val account: AccountProvider,
    preferences: UserPreferencesRepository,
    private val sync: SyncProvider,
    addingAccount: Boolean = false,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    /** Setting up the space of an account added to this phone: no permissions or safety notice. */
    private var quickSetup = false

    init {
        if (addingAccount) {
            viewModelScope.launch {
                if (account.profile.first() != null) {
                    quickSetup = true
                    signedIn()
                }
            }
        }
    }

    /** Whether the welcome screen offers "I already have an account": sign-in exists and nobody is signed in. */
    val canSignIn: StateFlow<Boolean> =
        (if (account.isAvailable) account.profile.map { it == null } else flowOf(false))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * Prices to fill in when the rider picks a fuel type: a published CPC price saved on the
     * phone, or one remembered from an earlier vehicle (`fuelPriceSuggestions`).
     */
    val fuelPriceSuggestions: StateFlow<Map<FuelType, Long>> =
        combine(preferences.ownFuelPrices, preferences.fuelPriceList) { own, published -> fuelPriceSuggestions(null, own, published) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun start() = _state.update { it.copy(step = OnboardingStep.INTRO, introPage = 0) }

    fun showIntroPage(page: Int) = _state.update { it.copy(introPage = page.coerceIn(0, INTRO_PAGE_COUNT - 1)) }

    /** Last intro page done, or "Skip": on to the vehicle. */
    fun finishIntro() = _state.update { it.copy(step = OnboardingStep.VEHICLE) }

    private var restoring: Job? = null

    /** Signed in or registered from the welcome screen: the intro is skipped, and the backup restored. */
    fun signedIn() {
        _state.update { it.copy(step = OnboardingStep.RESTORE) }
        restore()
    }

    /**
     * Downloads the rider's backup (CLAUDE.md section 14). Setup waits here until it has finished
     * (owner decision 2026-10-27), so nothing the rider enters is merged with the backup later; on
     * a problem it keeps trying, and [skipRestore] is the way out. A new account simply has none.
     */
    fun restore() {
        restoring?.cancel()
        _state.update { it.copy(restore = RestoreState.RUNNING) }
        restoring = viewModelScope.launch {
            val newAccount = account.profile.first()?.isNewAccount == true
            while (true) {
                when (val status = sync.syncNow()) {
                    is SyncStatus.Idle -> break
                    is SyncStatus.Stopped -> {
                        // A new account has no backup to bring back, so a problem here (for example
                        // an app installed outside Play, which can't back up) isn't worth stopping
                        // for. Backup tries again later, and the Account tab shows how it stands.
                        if (newAccount) {
                            _state.update { it.copy(step = OnboardingStep.VEHICLE) }
                            return@launch
                        }
                        _state.update { it.copy(restore = status.problem.restoreState()) }
                        delay(RESTORE_RETRY_MILLIS)
                    }
                    // Backup isn't running for this account's records yet: the sign-in is still
                    // being set up. Wait for it rather than going on with nothing restored.
                    SyncStatus.Off, SyncStatus.Running -> delay(RESTORE_WAIT_MILLIS)
                }
            }
            val restored = completeOnboarding.hasVehicle()
            if (restored && quickSetup) {
                _state.update { it.copy(restored = true) }
                finish()
                return@launch
            }
            _state.update { it.copy(step = if (restored) OnboardingStep.PERMISSIONS else OnboardingStep.VEHICLE, restored = restored) }
        }
    }

    /**
     * The rider won't wait for the restore: a fresh start without an account (owner decision
     * 2026-10-27). Signing out stops the restore and backup for this phone, so nothing new is
     * merged with the backup; the app then shows the records made without an account (setup again
     * when there are none). The account's backup stays in the cloud for the next sign-in.
     */
    fun skipRestore() {
        restoring?.cancel()
        viewModelScope.launch { account.signOut() }
    }

    private fun SyncProblem.restoreState() = when (this) {
        SyncProblem.OFFLINE -> RestoreState.OFFLINE
        SyncProblem.UNVERIFIED -> RestoreState.UNVERIFIED
        else -> RestoreState.FAILED
    }

    fun updateForm(form: VehicleFormState) = _state.update {
        // Clear an error as soon as the rider edits; it's re-checked on Continue.
        it.copy(form = form, errors = it.errors.filterKeys { field -> fieldValue(it.form, field) == fieldValue(form, field) })
    }

    /** Validates the form; moves on only when it's complete. [defaultName] fills a blank name. */
    fun continueWithForm(defaultName: String) {
        when (val result = _state.value.form.validate(defaultName)) {
            is VehicleFormResult.Invalid -> _state.update { it.copy(errors = result.errors) }
            is VehicleFormResult.Valid -> if (quickSetup) {
                _state.update { it.copy(errors = emptyMap(), pending = result.input) }
                finish()
            } else {
                _state.update { it.copy(errors = emptyMap(), pending = result.input, step = OnboardingStep.PERMISSIONS) }
            }
        }
    }

    /** Permissions allowed or skipped: on to the safety notice. */
    fun finishPermissions() = _state.update { it.copy(step = OnboardingStep.SAFETY) }

    /**
     * Back one step. The screen only routes system Back here after the welcome step, and handles
     * Back between intro pages itself.
     */
    fun back() {
        val current = _state.value
        if (current.saving) return
        if (current.step == OnboardingStep.RESTORE) {
            // Leaving while signed in would let setup go on without the backup, so back skips.
            skipRestore()
            quickSetup = false
        }
        _state.update {
            when (current.step) {
                OnboardingStep.WELCOME -> it
                OnboardingStep.INTRO -> it.copy(step = OnboardingStep.WELCOME)
                OnboardingStep.RESTORE -> it.copy(step = OnboardingStep.WELCOME)
                OnboardingStep.VEHICLE -> it.copy(step = OnboardingStep.INTRO, introPage = INTRO_PAGE_COUNT - 1)
                OnboardingStep.PERMISSIONS ->
                    if (it.restored) it.copy(step = OnboardingStep.WELCOME, restored = false) else it.copy(step = OnboardingStep.VEHICLE)
                OnboardingStep.SAFETY -> it.copy(step = OnboardingStep.PERMISSIONS, saveFailed = false)
            }
        }
    }

    fun finish() {
        val current = _state.value
        val input = current.pending
        if (current.saving || (input == null && !current.restored)) return
        _state.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            try {
                if (input != null) completeOnboarding(input, isSample = false) else completeOnboarding.withRestoredVehicle()
                // On success the onboarding flag flips and the app moves to Home.
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, saveFailed = true) }
            }
        }
    }

    private fun fieldValue(form: VehicleFormState, field: VehicleField): String = when (field) {
        VehicleField.FUEL_PRICE -> form.fuelPrice
        VehicleField.EFFICIENCY -> form.efficiency
        VehicleField.MAINTENANCE -> form.maintenance
        VehicleField.TARGET -> form.target
    }

    companion object {
        /** How long a stopped restore waits before trying again on its own. */
        private const val RESTORE_RETRY_MILLIS = 15_000L

        /** How often a restore checks whether backup has started for the signed-in account. */
        private const val RESTORE_WAIT_MILLIS = 1_000L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as PicksmartApplication
                val c = app.container
                OnboardingViewModel(c.completeOnboarding, c.account, c.userPreferences, c.sync, addingAccount = c.space.value.id != FIRST_SPACE)
            }
        }
    }
}
