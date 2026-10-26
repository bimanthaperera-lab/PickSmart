package lk.picksmart.glance

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import lk.picksmart.PicksmartApplication
import lk.picksmart.data.preferences.AppLanguageStore
import lk.picksmart.domain.model.LastCheckInputs
import lk.picksmart.domain.model.Platform
import lk.picksmart.domain.model.Vehicle
import lk.picksmart.domain.usecase.OfferReading
import lk.picksmart.domain.usecase.OfferToSave
import lk.picksmart.domain.usecase.checkOffer
import lk.picksmart.domain.usecase.parseOfferText
import lk.picksmart.ui.notifications.OfferSaveNotification
import java.lang.ref.WeakReference
import java.time.Instant

/**
 * The offer reader (CLAUDE.md N5, section 9b). When a listed ride app's window changes, it reads
 * the visible text, finds an offer's fare and distances with [parseOfferText], and shows the
 * estimated profit or loss in an [OfferBadge], using the active vehicle's rates. When the offer
 * leaves the screen it asks, in a notification, whether to save it as a trip
 * ([OfferSaveNotification]); only the numbers go into it, and the rider saves them in the app.
 *
 * It only reads. It never performs an action, gesture or global action, and the text it reads is
 * kept in memory for one calculation and never logged, saved or sent. Which apps it reads is set
 * by `packageNames` in `res/xml/offer_reader.xml`; Android delivers no other app's events.
 */
class OfferReaderService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var badge: OfferBadge

    @Volatile private var vehicle: Vehicle? = null
    @Volatile private var paused = false
    @Volatile private var lastInputs = LastCheckInputs()

    /** The offer shown in the badge, or the one the rider hid, so the same offer isn't redrawn. */
    private var shown: OfferReading? = null
    private var hiddenByRider: OfferReading? = null

    /** Where and when [shown] was read, for the "Save this trip?" notification. */
    private var shownPackage: String? = null
    private var shownAt: Instant? = null

    /** The offer last offered for saving, so the same offer isn't offered again. */
    private var lastAsked: OfferReading? = null

    private val read = Runnable { readActiveWindow() }
    private val clear = Runnable { clearBadge() }

    // The badge's words follow the rider's language below Android 13 too.
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLanguageStore.wrap(newBase))

    override fun onServiceConnected() {
        badge = OfferBadge(this) { hiddenByRider = shown }
        val container = (application as PicksmartApplication).container
        scope.launch { container.vehicles.defaultVehicle.collect { vehicle = it; refresh() } }
        scope.launch { container.userPreferences.lastCheckInputs.collect { lastInputs = it; refresh() } }
        scope.launch {
            container.userPreferences.offerReaderPaused.collect {
                paused = it
                if (it) clearBadge(askToSave = false) else handler.post(read)
            }
        }
        running = WeakReference(this)
        // An offer may already be on screen when the reader is turned on or restarted.
        handler.post(read)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Paused in the app: nothing is read at all.
        if (paused) return
        // Window changes come in bursts; read once they settle.
        handler.removeCallbacks(read)
        handler.postDelayed(read, SETTLE_MS)
    }

    private fun readActiveWindow() {
        if (paused) return
        val root = rootInActiveWindow
        val targets = serviceInfo?.packageNames?.toSet().orEmpty()
        if (root == null || root.packageName?.toString() !in targets) {
            scheduleClear()
            return
        }
        val lines = visibleText(root)
        // Picksmart's own screens are listed only in the debug build, for its sample offer.
        if (root.packageName == packageName && SAMPLE_OFFER_MARKER !in lines) {
            scheduleClear()
            return
        }
        val reading = parseOfferText(lines)
        if (reading == null) {
            // The card may be redrawing; clear only if no offer comes back shortly.
            scheduleClear()
            return
        }
        handler.removeCallbacks(clear)
        if (reading == hiddenByRider) return
        hiddenByRider = null
        if (reading != shown) {
            shownPackage = root.packageName?.toString()
            shownAt = (application as PicksmartApplication).container.clock.instant()
        }
        shown = reading
        refresh()
        handler.postDelayed(clear, SHOW_AT_MOST_MS)
    }

    /** Draws [shown] with the current vehicle and fee settings, or hides the badge without them. */
    private fun refresh() {
        if (!::badge.isInitialized) return
        val reading = shown ?: return
        val active = vehicle
        val result = active?.let { (application as PicksmartApplication).container.profitCalculator.checkOffer(reading, it, lastInputs) }
        if (active == null || result == null) badge.hide() else badge.show(result, active.name)
    }

    private fun scheduleClear() {
        if (shown == null && !badge.isShown) return
        handler.removeCallbacks(clear)
        handler.postDelayed(clear, CLEAR_AFTER_MS)
    }

    /**
     * Hides the badge. When the offer has left the screen ([askToSave]), asks whether to save it:
     * Picksmart can't tell whether the rider accepted it.
     */
    private fun clearBadge(askToSave: Boolean = true) {
        val offer = shown
        val readAt = shownAt
        shown = null
        handler.removeCallbacks(clear)
        if (::badge.isInitialized) badge.hide()
        if (askToSave && offer != null && readAt != null && offer != lastAsked && vehicle != null) {
            lastAsked = offer
            OfferSaveNotification.post(this, OfferToSave(offer, platformOf(shownPackage), readAt))
        }
    }

    /** The ride app an offer came from; Picksmart's own sample and unknown apps use the last-used platform. */
    private fun platformOf(packageName: String?): Platform = when (packageName) {
        UBER_DRIVER -> Platform.UBER
        else -> lastInputs.platform
    }

    /** The text and descriptions of visible nodes, in reading order, up to [MAX_NODES]. */
    private fun visibleText(root: AccessibilityNodeInfo): List<String> {
        val lines = mutableListOf<String>()
        val stack = ArrayDeque<AccessibilityNodeInfo>().apply { addLast(root) }
        var visited = 0
        while (stack.isNotEmpty() && visited < MAX_NODES) {
            val node = stack.removeLast()
            visited++
            if (!node.isVisibleToUser) continue
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let(lines::add)
            node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(lines::add)
            for (i in node.childCount - 1 downTo 0) node.getChild(i)?.let(stack::addLast)
        }
        return lines
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (running?.get() === this) running = null
        handler.removeCallbacksAndMessages(null)
        if (::badge.isInitialized) badge.hide()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val SETTLE_MS = 120L
        private const val CLEAR_AFTER_MS = 1_500L
        private const val SHOW_AT_MOST_MS = 45_000L
        private const val MAX_NODES = 400
        private const val UBER_DRIVER = "com.ubercab.driver"

        /** The connected service, so the app can turn it off completely ([turnOff]). */
        @Volatile private var running: WeakReference<OfferReaderService>? = null

        /**
         * Turns the reader off in Android's Accessibility settings too, as if the rider had done it
         * there. Returns false when it isn't running, so the caller can open those settings.
         */
        fun turnOff(): Boolean {
            val service = running?.get() ?: return false
            service.disableSelf()
            return true
        }

        /** The first line of the debug build's sample offer screen. */
        const val SAMPLE_OFFER_MARKER = "Picksmart sample offer (debug)"

        /** True when the rider has turned the offer reader on in Android's Accessibility settings. */
        fun isEnabled(context: Context): Boolean {
            val component = ComponentName(context, OfferReaderService::class.java)
            return context.getSystemService(AccessibilityManager::class.java)
                .getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { it.resolveInfo.serviceInfo.let { s -> ComponentName(s.packageName, s.name) } == component }
        }
    }
}
