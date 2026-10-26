package lk.picksmart.probe

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Debug-only feasibility probe (docs/decisions.md, 2026-10-24). It answers one question: does the
 * accessibility tree of a ride app's offer card expose the fare and distances as text?
 *
 * It only reads. It never taps, scrolls, types or performs any action, never draws on screen, and
 * ships only in debug builds (src/debug), so CLAUDE.md N5 still holds for every release build.
 *
 * Output goes to logcat under [TAG]. To keep passenger names and addresses out of the log, a
 * node's text is printed only when it looks like a money, distance or time value; any other text
 * is shown as its length alone.
 */
class ScreenReadProbeService : AccessibilityService() {

    private var lastDumpAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in TARGET_PACKAGES) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastDumpAt < MIN_DUMP_INTERVAL_MS) return
        lastDumpAt = now

        val root = rootInActiveWindow ?: run {
            Log.i(TAG, "$pkg: no active window root (window may be secure or not ready)")
            return
        }
        val started = SystemClock.elapsedRealtime()
        val stats = DumpStats()
        Log.i(TAG, "==== $pkg  event=${AccessibilityEvent.eventTypeToString(event.eventType)} ====")
        dump(root, depth = 0, stats = stats)
        Log.i(
            TAG,
            "==== end: ${stats.nodes} nodes, ${stats.withText} with text, " +
                "${stats.matches} money/km/min matches, ${SystemClock.elapsedRealtime() - started} ms ====",
        )
    }

    private fun dump(node: AccessibilityNodeInfo, depth: Int, stats: DumpStats) {
        if (stats.nodes >= MAX_NODES) return
        stats.nodes++
        val text = node.text?.toString()
        val description = node.contentDescription?.toString()
        if (!text.isNullOrBlank() || !description.isNullOrBlank()) stats.withText++
        if (looksUseful(text) || looksUseful(description)) stats.matches++

        val line = buildString {
            append("  ".repeat(depth.coerceAtMost(20)))
            append(node.className?.toString()?.substringAfterLast('.') ?: "?")
            node.viewIdResourceName?.let { append(" id=").append(it.substringAfterLast('/')) }
            text?.let { append(" text=").append(redact(it)) }
            description?.let { append(" desc=").append(redact(it)) }
        }
        Log.i(TAG, line)

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { dump(it, depth + 1, stats) }
        }
    }

    override fun onInterrupt() = Unit

    private class DumpStats {
        var nodes = 0
        var withText = 0
        var matches = 0
    }

    companion object {
        const val TAG = "PicksmartProbe"

        /**
         * Apps the probe dumps. `com.ubercab.driver` is the Uber Driver app. Add PickMe Driver's
         * package after finding it with `adb shell dumpsys window | grep mCurrentFocus` while it is open,
         * both here and in `android:packageNames` in `res/xml/screen_read_probe.xml`.
         */
        val TARGET_PACKAGES = setOf(
            "com.ubercab.driver",
        )

        private const val MIN_DUMP_INTERVAL_MS = 1_000L
        private const val MAX_NODES = 600

        private val MONEY_OR_DISTANCE = Regex(
            """(?i)(rs\.?|lkr|රු)\s*[\d,]+(\.\d+)?|[\d.,]+\s*(km|kms|min|mins)\b""",
        )
        private val BARE_NUMBER = Regex("""^\s*[\d.,]+\s*$""")

        /** A bare number counts only up to 6 digits, so phone numbers are never printed. */
        fun looksUseful(value: String?): Boolean {
            if (value.isNullOrBlank() || value.count(Char::isDigit) > 10) return false
            if (MONEY_OR_DISTANCE.containsMatchIn(value)) return true
            return BARE_NUMBER.matches(value) && value.count(Char::isDigit) <= 6
        }

        /** Shows money, distance and time values as they are; everything else only as its length. */
        fun redact(value: String): String =
            if (looksUseful(value)) "\"$value\"" else "[${value.length} chars]"
    }
}
