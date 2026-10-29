package lk.picksmart.glance

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.IBinder
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import lk.picksmart.PicksmartApplication
import lk.picksmart.data.preferences.AppLanguageStore
import lk.picksmart.domain.calculator.ProfitResult
import lk.picksmart.domain.calculator.minimumFarePerKm
import java.lang.ref.WeakReference

/**
 * Keeps the [FloatingBubble] on screen while the rider has it turned on (CLAUDE.md section 9a)
 * and Android allows Picksmart to draw over other apps. The rate follows the active vehicle and
 * the last-used fee settings, like the offer reader's badge. It isn't a foreground service: the
 * visible bubble keeps the app alive, and Android restarts it if it is stopped ([START_STICKY]).
 *
 * Turning the switch off, or holding the bubble to hide it, stops it.
 */
class FloatingBubbleService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var bubble: FloatingBubble? = null

    // The bubble's words follow the rider's language below Android 13 too.
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLanguageStore.wrap(newBase))

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        val container = (application as PicksmartApplication).container
        val shown = FloatingBubble(this) {
            // Held to hide: off until the rider turns it on again in the app.
            scope.launch { container.userPreferences.setFloatingBubble(false) }
        }
        if (!Settings.canDrawOverlays(this) || !shown.show()) {
            stopSelf()
            return
        }
        bubble = shown
        running = WeakReference(this)
        scope.launch {
            container.space.flatMapLatest { space ->
                combine(space.vehicles.defaultVehicle, space.userPreferences.lastCheckInputs) { vehicle, last ->
                    vehicle?.let {
                        minimumFarePerKm(
                            fuelPricePerLitreCents = it.fuelPricePerLitreCents,
                            fuelEfficiencyKmPerLitre = it.fuelEfficiencyKmPerLitre,
                            maintenanceReservePerKmCents = it.maintenanceReservePerKmCents,
                            targetProfitPerKmCents = it.targetProfitPerKmCents,
                            fareMode = last.fareMode,
                            commissionType = last.commissionType,
                            commissionHundredths = last.commissionHundredths,
                        )
                    }
                }
            }.collect { shown.setMinimum(it) }
        }
        scope.launch {
            container.userPreferences.floatingBubble.collect { on -> if (!on) stopSelf() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        bubble?.keepOnScreen()
    }

    override fun onDestroy() {
        if (running?.get() === this) running = null
        bubble?.remove()
        bubble = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        @Volatile private var running: WeakReference<FloatingBubbleService>? = null

        /**
         * Starts the bubble when the rider has turned it on and Android allows it. Called while
         * Picksmart is on screen, since Android doesn't let an app in the background start a service.
         */
        fun startIfAllowed(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            runCatching { context.startService(Intent(context, FloatingBubbleService::class.java)) }
        }

        /**
         * Shows an offer's result in the bubble, which then is the offer badge (section 9b).
         * False when the bubble isn't on screen, so the offer reader draws its own badge.
         */
        fun showOffer(result: ProfitResult, vehicleName: String, onHidden: () -> Unit): Boolean {
            val bubble = running?.get()?.bubble ?: return false
            bubble.showOffer(result, vehicleName, onHidden)
            return true
        }

        /** The offer has gone: back to the pin. */
        fun clearOffer() {
            running?.get()?.bubble?.clearOffer()
        }
    }
}
