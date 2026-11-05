package lk.picksmart.glance

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import lk.picksmart.R
import lk.picksmart.data.preferences.AppLanguageStore
import lk.picksmart.domain.calculator.ProfitResult
import lk.picksmart.domain.calculator.ResultBand
import lk.picksmart.util.MoneyFormatter

/**
 * The offer reader's result, drawn over the ride app as an accessibility overlay (CLAUDE.md
 * sections 9a and 9b). It sits in the top corner, far from the Accept button in the lower part of
 * the screen, so it never covers the ride app's controls. Touches outside it pass straight through.
 * Holding it hides it until the next offer ([onHidden]). Its words follow the rider's app language
 * ([AppLanguageStore.localized]), read each time it is drawn.
 */
class OfferBadge(private val context: Context, private val onHidden: () -> Unit) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var view: View? = null

    val isShown: Boolean get() = view != null

    fun show(result: ProfitResult, vehicleName: String) {
        val words = AppLanguageStore.localized(context)
        val badge = view ?: LayoutInflater.from(words).inflate(R.layout.offer_badge, null).also { created ->
            created.setOnLongClickListener {
                hide()
                onHidden()
                true
            }
            windowManager.addView(created, layoutParams())
            view = created
        }
        badge.contentDescription = badge.showOfferResult(words, result, vehicleName) + ". " +
            words.getString(R.string.offer_badge_hide_hint)
    }

    fun hide() {
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        // Top corner, below the status bar and the ride app's top bar; never the lower part of the
        // screen, where Accept buttons sit (section 9a).
        gravity = Gravity.TOP or Gravity.END
        val density = context.resources.displayMetrics.density
        x = (12 * density).toInt()
        y = (88 * density).toInt()
    }
}

/**
 * Fills an `offer_badge` layout with [result] in [words]' language, and returns what it says, for
 * a content description. The floating bubble shows the same card when it is on.
 */
internal fun View.showOfferResult(words: Context, result: ProfitResult, vehicleName: String): String {
    val money = MoneyFormatter(words.getString(R.string.currency_prefix))
    val (label, icon, color) = result.band.look()
    val tint = ContextCompat.getColor(context, color)
    findViewById<ImageView>(R.id.offer_badge_icon).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(tint)
    }
    findViewById<TextView>(R.id.offer_badge_band).apply {
        text = words.getString(label)
        setTextColor(tint)
    }
    val net = money.formatSigned(result.netProfitCents)
    val amounts = result.netProfitPerKmCents?.let { words.getString(R.string.offer_badge_amounts, net, money.format(it)) } ?: net
    findViewById<TextView>(R.id.offer_badge_amounts).text = amounts
    // The tip is already in the profit above; this line says so.
    val tip = result.tipCents.takeIf { it > 0 }?.let { words.getString(R.string.offer_badge_tip, money.format(it)) }
    findViewById<TextView>(R.id.offer_badge_tip).apply {
        text = tip
        visibility = if (tip == null) View.GONE else View.VISIBLE
    }
    val source = words.getString(R.string.offer_badge_source, vehicleName)
    findViewById<TextView>(R.id.offer_badge_source).text = source
    findViewById<TextView>(R.id.offer_badge_powered).text = words.getString(R.string.offer_badge_powered)
    return listOfNotNull(words.getString(label), amounts, tip, source).joinToString(". ")
}

private fun ResultBand.look(): Triple<Int, Int, Int> = when (this) {
    ResultBand.GOOD -> Triple(R.string.band_good, R.drawable.ic_band_good, R.color.badge_profit)
    ResultBand.PROFIT -> Triple(R.string.band_profit, R.drawable.ic_band_profit, R.color.badge_profit)
    ResultBand.BELOW_TARGET -> Triple(R.string.band_below_target, R.drawable.ic_band_below_target, R.color.badge_below_target)
    ResultBand.LOSS -> Triple(R.string.band_loss, R.drawable.ic_band_loss, R.color.badge_loss)
    ResultBand.BREAK_EVEN -> Triple(R.string.band_break_even, R.drawable.ic_band_break_even, R.color.off_white)
}
