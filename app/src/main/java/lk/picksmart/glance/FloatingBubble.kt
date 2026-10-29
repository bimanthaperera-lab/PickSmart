package lk.picksmart.glance

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import lk.picksmart.R
import lk.picksmart.data.preferences.AppLanguageStore
import lk.picksmart.domain.calculator.MinimumFare
import lk.picksmart.util.MoneyFormatter
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The floating bubble (CLAUDE.md section 9a), drawn over other apps in its own small window.
 * Closed, it is only the pin; a tap opens it to the minimum fare per km ("Rs. 91/km") with "Hold
 * to hide" under it, and another tap closes it. Holding it hides it at once ([onHidden]).
 *
 * Touches. The window is only as big as the bubble, and it is [WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL]
 * and [WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE], so a touch anywhere else goes straight to the
 * app underneath and the keyboard stays with that app. A touch on the bubble drags it, or opens or
 * closes it when it doesn't move. [WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH] reports a
 * touch elsewhere (without where it was), which closes the bubble, so the open pill never stays over
 * an app the rider is using.
 *
 * Accept buttons sit low on the screen, and some ride apps ignore taps under another app's window,
 * so the bubble snaps to the nearest side edge when dropped and never rests in the lower quarter of
 * the screen. It also closes on its own after [OPEN_FOR_MS] and when an offer badge appears
 * ([close]). Its words follow the rider's app language, read each time it is drawn.
 */
class FloatingBubble(private val context: Context, private val onHidden: () -> Unit) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val density = context.resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var root: View? = null
    private var minimum: MinimumFare? = null
    private var open = false

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
        PixelFormat.TRANSLUCENT,
    ).apply {
        x = dp(EDGE_DP)
        y = savedY ?: dp(START_Y_DP)
    }

    private val autoClose = Runnable { close() }
    private val hold = Runnable { holdToHide() }

    /** Adds the bubble. False when Android refuses the window, as when the permission was taken away. */
    fun show(): Boolean {
        if (root != null) return true
        val view = LayoutInflater.from(context).inflate(R.layout.floating_bubble, null)
        view.setOnTouchListener(DragOrTap())
        // Taps come through the touch listener's performClick; these also serve TalkBack.
        view.setOnClickListener { setOpen(!open) }
        view.setOnLongClickListener {
            holdToHide()
            true
        }
        root = view
        draw()
        return try {
            windowManager.addView(view, params)
            true
        } catch (_: RuntimeException) {
            root = null
            false
        }
    }

    /** The rate the open bubble shows; null when there is no vehicle or its rates can't give one. */
    fun setMinimum(value: MinimumFare?) {
        minimum = value
        draw()
    }

    fun close() = setOpen(false)

    /** Keeps the bubble out of the lower quarter after the screen turns. */
    fun keepOnScreen() {
        params.y = safeY(params.y)
        update()
    }

    fun remove() {
        handler.removeCallbacks(autoClose)
        handler.removeCallbacks(hold)
        root?.let { runCatching { windowManager.removeView(it) } }
        root = null
    }

    private fun setOpen(value: Boolean) {
        handler.removeCallbacks(autoClose)
        open = value
        if (value) handler.postDelayed(autoClose, OPEN_FOR_MS)
        draw()
    }

    private fun holdToHide() {
        root?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        remove()
        onHidden()
    }

    private fun draw() {
        val view = root ?: return
        val words = AppLanguageStore.localized(context)
        val money = MoneyFormatter(words.getString(R.string.currency_prefix))
        val rate = minimum?.let { fare ->
            val perKm = money.formatWholeUp(fare.perKmCents)
            if (fare.fixedFeeCents > 0) {
                words.getString(R.string.bubble_rate_with_fee, perKm, money.formatWholeUp(fare.fixedFeeCents))
            } else {
                words.getString(R.string.bubble_rate, perKm)
            }
        } ?: words.getString(R.string.bubble_rate_unknown)
        // On the right edge the pin stays at the edge and the pill opens towards the middle.
        view.findViewById<LinearLayout>(R.id.bubble_pill).apply {
            background = if (open) ContextCompat.getDrawable(context, R.drawable.bg_bubble_pill) else null
            layoutDirection = if (onRight) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        }
        view.findViewById<TextView>(R.id.bubble_rate).apply {
            text = rate
            visibility = if (open) View.VISIBLE else View.GONE
        }
        view.findViewById<TextView>(R.id.bubble_hint).apply {
            text = words.getString(R.string.offer_badge_hide_hint)
            visibility = if (open) View.VISIBLE else View.GONE
            (layoutParams as LinearLayout.LayoutParams).gravity = if (onRight) Gravity.END else Gravity.START
        }
        view.contentDescription = if (open) {
            words.getString(R.string.bubble_description_open, rate)
        } else {
            words.getString(R.string.bubble_description_closed)
        }
        params.gravity = Gravity.TOP or if (onRight) Gravity.END else Gravity.START
        update()
    }

    /** Drops the bubble at the nearest side edge, above the lower quarter of the screen. */
    private fun snap(fingerX: Float) {
        onRight = fingerX > screenSize().first / 2f
        params.x = dp(EDGE_DP)
        params.y = safeY(params.y)
        savedY = params.y
        if (open) setOpen(true) else draw()
    }

    private fun safeY(y: Int): Int {
        val height = root?.height ?: 0
        val lowest = (screenSize().second * SAFE_AREA).toInt() - height
        return y.coerceIn(dp(TOP_DP), maxOf(dp(TOP_DP), lowest))
    }

    private fun screenSize(): Pair<Int, Int> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        windowManager.currentWindowMetrics.bounds.let { it.width() to it.height() }
    } else {
        context.resources.displayMetrics.let { it.widthPixels to it.heightPixels }
    }

    private fun update() {
        root?.takeIf { it.isAttachedToWindow }?.let { runCatching { windowManager.updateViewLayout(it, params) } }
    }

    private fun dp(value: Int) = (value * density).roundToInt()

    /** Moving past the touch slop drags; letting go without moving is a tap; holding still hides it. */
    private inner class DragOrTap : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_OUTSIDE -> {
                    if (open) close()
                    return false
                }
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    handler.postDelayed(hold, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && hypot(dx, dy) > touchSlop) {
                        dragging = true
                        handler.removeCallbacks(hold)
                        handler.removeCallbacks(autoClose)
                    }
                    if (dragging) {
                        // x counts from the edge the bubble is on.
                        params.x = (startX + if (onRight) -dx else dx).roundToInt().coerceAtLeast(0)
                        params.y = (startY + dy).roundToInt().coerceAtLeast(0)
                        update()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(hold)
                    if (root == null) return true
                    if (dragging) snap(event.rawX) else view.performClick()
                }
                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(hold)
                    if (dragging && root != null) snap(event.rawX)
                }
            }
            return true
        }
    }

    private companion object {
        const val OPEN_FOR_MS = 5_000L
        const val SAFE_AREA = 0.75f
        const val EDGE_DP = 8
        const val TOP_DP = 24
        const val START_Y_DP = 140

        /** Where the rider last left it, so turning it off and on keeps the place. */
        var savedY: Int? = null
        var onRight = false
    }
}
