package lk.picksmart.glance

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
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
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import lk.picksmart.R
import lk.picksmart.data.preferences.AppLanguageStore
import lk.picksmart.domain.calculator.MinimumFare
import lk.picksmart.domain.calculator.ProfitResult
import lk.picksmart.util.MoneyFormatter
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The floating bubble (CLAUDE.md sections 9a and 9b), drawn over other apps in its own small
 * window. Closed, it is only the pin; a tap opens it to the minimum fare per km ("Rs. 91/km") with
 * "Hold to hide" under it, and another tap closes it. Holding it hides it at once ([onHidden]).
 * While the offer reader shows an offer, the bubble itself is the offer badge: the pin gives way to
 * the result card ([showOffer]) until the offer goes ([clearOffer]); holding the card hides it
 * until the next offer, as on the badge.
 *
 * Touches. The window is only as big as the bubble, and it is [WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL]
 * and [WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE], so a touch anywhere else goes straight to the
 * app underneath and the keyboard stays with that app. A touch on the bubble drags it, or opens or
 * closes it when it doesn't move. [WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH] reports a
 * touch elsewhere (without where it was), which closes the open pill, so it never stays over an
 * app the rider is using.
 *
 * Accept buttons sit low on the screen, and some ride apps ignore taps under another app's window,
 * so the bubble snaps to the nearest side edge when dropped and never reaches into the lower
 * quarter of the screen, even when the larger offer card shows. The open pill also closes on its
 * own after [OPEN_FOR_MS].
 *
 * The pill and the card grow out of the pin and shrink back into it (owner request 2026-10-29).
 * The window takes its larger size before growing and its smaller one after shrinking, so nothing
 * is cut off. Android's "Remove animations" setting turns this off. Its words follow the rider's
 * app language, read each time it is drawn.
 */
class FloatingBubble(private val context: Context, private val onHidden: () -> Unit) {

    private enum class Look { CLOSED, OPEN, OFFER }

    private class Offer(val result: ProfitResult, val vehicleName: String, val onHidden: () -> Unit)

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val density = context.resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var root: View? = null
    private var minimum: MinimumFare? = null
    private var open = false
    private var offer: Offer? = null

    /** What is on screen, which can lag behind [look] while it shrinks. */
    private var drawn = Look.CLOSED

    /** Counts changes, so a shrink that finishes after a newer change doesn't undo it. */
    private var change = 0
    private var pillBackground: ValueAnimator? = null

    /** Where the rider left the bubble; it rises above that only while it is too tall to fit. */
    private var anchorY = savedY ?: dp(START_Y_DP)

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
        y = anchorY
    }

    private val autoClose = Runnable { close() }
    private val hold = Runnable { held() }

    private val look: Look get() = when {
        offer != null -> Look.OFFER
        open -> Look.OPEN
        else -> Look.CLOSED
    }

    /** Adds the bubble. False when Android refuses the window, as when the permission was taken away. */
    fun show(): Boolean {
        if (root != null) return true
        val view = LayoutInflater.from(context).inflate(R.layout.floating_bubble, null)
        view.setOnTouchListener(DragOrTap())
        // Taps come through the touch listener's performClick; these also serve TalkBack.
        view.setOnClickListener { if (offer == null) setOpen(!open) }
        view.setOnLongClickListener {
            held()
            true
        }
        // A taller bubble (the offer card) moves up so it never reaches the lower quarter.
        view.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) view.post { keepOnScreen() }
        }
        root = view
        apply(look)
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
        if (drawn == look) apply(look)
    }

    /** Shows an offer's result in place of the pin; [onHidden] runs when the rider holds it to hide it. */
    fun showOffer(result: ProfitResult, vehicleName: String, onHidden: () -> Unit) {
        // The reader looks again every second; the same offer is not drawn again.
        val same = offer?.let { it.result == result && it.vehicleName == vehicleName } == true
        offer = Offer(result, vehicleName, onHidden)
        if (same) return
        handler.removeCallbacks(autoClose)
        open = false
        changeTo(Look.OFFER)
    }

    fun clearOffer() {
        if (offer == null) return
        offer = null
        changeTo(look)
    }

    fun close() = setOpen(false)

    /** Keeps the bubble out of the lower quarter, after the screen turns or its size changes. */
    fun keepOnScreen() {
        val y = safeY(anchorY)
        if (y == params.y) return
        params.y = y
        update()
    }

    fun remove() {
        handler.removeCallbacks(autoClose)
        handler.removeCallbacks(hold)
        pillBackground?.cancel()
        root?.let { runCatching { windowManager.removeView(it) } }
        root = null
    }

    private fun setOpen(value: Boolean) {
        handler.removeCallbacks(autoClose)
        if (offer != null) return
        open = value
        if (value) handler.postDelayed(autoClose, OPEN_FOR_MS)
        changeTo(look)
    }

    /** Holding the offer card hides it until the next offer; holding the bubble hides the bubble. */
    private fun held() {
        root?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        val shownOffer = offer
        if (shownOffer != null) {
            clearOffer()
            shownOffer.onHidden()
        } else {
            remove()
            onHidden()
        }
    }

    /** Moves to [target]: shrinks what is shown back into the pin, then grows the new part out of it. */
    private fun changeTo(target: Look) {
        val view = root ?: return
        val from = drawn
        val step = ++change
        if (from == target) {
            apply(target)
            return
        }
        val leaving = expandingViews(view, from)
        if (leaving.isEmpty()) {
            apply(target)
            grow(view, target)
            return
        }
        drawn = target
        leaving.forEachIndexed { index, part ->
            part.animate().cancel()
            part.pivotX = pinSidePivot(part)
            part.animate()
                .alpha(0f)
                .scaleX(SHRUNK)
                .scaleY(SHRUNK)
                .setDuration(SHRINK_MS)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    if (index != leaving.lastIndex || step != change || root == null) return@withEndAction
                    apply(target)
                    grow(view, target)
                }
                .start()
        }
        if (from == Look.OPEN) fadePill(view, to = 0)
    }

    private fun grow(view: View, target: Look) {
        val parts = expandingViews(view, target)
        parts.forEach { part ->
            part.animate().cancel()
            part.alpha = 0f
        }
        if (target == Look.OPEN) fadePill(view, from = 0, to = 255)
        // The new size is known only after layout.
        view.doOnPreDraw {
            parts.forEach { part ->
                part.pivotX = pinSidePivot(part)
                part.pivotY = part.height / 2f
                part.scaleX = SHRUNK
                part.scaleY = SHRUNK
                part.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(GROW_MS)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }
    }

    /** The parts that come and go: the rate and hint when open, the card while an offer shows. */
    private fun expandingViews(view: View, shown: Look): List<View> = when (shown) {
        Look.CLOSED -> emptyList()
        Look.OPEN -> listOf(view.findViewById(R.id.bubble_rate), view.findViewById(R.id.bubble_hint))
        Look.OFFER -> listOf(view.findViewById(R.id.bubble_offer))
    }

    private fun pinSidePivot(part: View) = if (onRight) part.width.toFloat() else 0f

    private fun fadePill(view: View, from: Int? = null, to: Int) {
        val background = view.findViewById<LinearLayout>(R.id.bubble_pill).background ?: return
        pillBackground?.cancel()
        from?.let { background.alpha = it }
        pillBackground = ValueAnimator.ofInt(from ?: background.alpha, to).apply {
            duration = if (to == 0) SHRINK_MS else GROW_MS
            addUpdateListener { background.alpha = it.animatedValue as Int }
            start()
        }
    }

    /** Draws [shown] at once, at full size. */
    private fun apply(shown: Look) {
        val view = root ?: return
        drawn = shown
        val words = AppLanguageStore.localized(context)
        val rate = rateText(words)
        // On the right edge the pin stays at the edge and the pill opens towards the middle.
        val edge = if (onRight) Gravity.END else Gravity.START
        view.findViewById<LinearLayout>(R.id.bubble_pill).apply {
            visibility = if (shown == Look.OFFER) View.GONE else View.VISIBLE
            background = if (shown == Look.OPEN) pillDrawable() else null
            layoutDirection = if (onRight) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            (layoutParams as LinearLayout.LayoutParams).gravity = edge
        }
        view.findViewById<TextView>(R.id.bubble_rate).apply {
            text = rate
            visibility = if (shown == Look.OPEN) View.VISIBLE else View.GONE
            resetScale()
        }
        view.findViewById<TextView>(R.id.bubble_hint).apply {
            text = words.getString(R.string.offer_badge_hide_hint)
            visibility = if (shown == Look.OPEN) View.VISIBLE else View.GONE
            (layoutParams as LinearLayout.LayoutParams).gravity = edge
            resetScale()
        }
        val card = view.findViewById<View>(R.id.bubble_offer).apply {
            visibility = if (shown == Look.OFFER) View.VISIBLE else View.GONE
            (layoutParams as LinearLayout.LayoutParams).gravity = edge
            resetScale()
        }
        val shownOffer = offer
        view.contentDescription = when {
            shown == Look.OFFER && shownOffer != null ->
                card.showOfferResult(words, shownOffer.result, shownOffer.vehicleName) + ". " +
                    words.getString(R.string.offer_badge_hide_hint)
            shown == Look.OPEN -> words.getString(R.string.bubble_description_open, rate)
            else -> words.getString(R.string.bubble_description_closed)
        }
        params.gravity = Gravity.TOP or edge
        update()
    }

    private fun rateText(words: Context): String {
        val fare = minimum ?: return words.getString(R.string.bubble_rate_unknown)
        val money = MoneyFormatter(words.getString(R.string.currency_prefix))
        val perKm = money.formatWholeUp(fare.perKmCents)
        return if (fare.fixedFeeCents > 0) {
            words.getString(R.string.bubble_rate_with_fee, perKm, money.formatWholeUp(fare.fixedFeeCents))
        } else {
            words.getString(R.string.bubble_rate, perKm)
        }
    }

    private fun pillDrawable(): Drawable? = ContextCompat.getDrawable(context, R.drawable.bg_bubble_pill)?.mutate()

    private fun View.resetScale() {
        animate().cancel()
        alpha = 1f
        scaleX = 1f
        scaleY = 1f
    }

    /** Drops the bubble at the nearest side edge, above the lower quarter of the screen. */
    private fun snap(fingerX: Float) {
        val side = fingerX > screenSize().first / 2f
        params.x = dp(EDGE_DP)
        params.y = safeY(params.y)
        anchorY = params.y
        savedY = anchorY
        if (side != onRight) {
            onRight = side
            apply(drawn)
        } else {
            update()
        }
        if (open) setOpen(true)
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

    /** Moving past the touch slop drags; letting go without moving is a tap; holding still hides. */
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
        const val GROW_MS = 180L
        const val SHRINK_MS = 140L
        const val SHRUNK = 0.6f
        const val SAFE_AREA = 0.75f
        const val EDGE_DP = 8
        const val TOP_DP = 24
        const val START_Y_DP = 140

        /** Where the rider last left it, so turning it off and on keeps the place. */
        var savedY: Int? = null
        var onRight = false
    }
}
