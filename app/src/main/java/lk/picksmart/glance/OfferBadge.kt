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
import lk.picksmart.domain.calculator.ProfitResult
import lk.picksmart.domain.calculator.ResultBand
import lk.picksmart.util.MoneyFormatter

/**
 * The offer reader's result, drawn over the ride app as an accessibility overlay (CLAUDE.md
 * sections 9a and 9b). It sits in the top corner, far from the Accept button in the lower part of
 * the screen, so it never covers the ride app's controls. Touches outside it pass straight through.
 * Holding it hides it until the next offer ([onHidden]).
 */
class OfferBadge(private val context: Context, private val onHidden: () -> Unit) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val money = MoneyFormatter(context.getString(R.string.currency_prefix))
    private var view: View? = null

    val isShown: Boolean get() = view != null

    fun show(result: ProfitResult, vehicleName: String) {
        val badge = view ?: LayoutInflater.from(context).inflate(R.layout.offer_badge, null).also { created ->
            created.setOnLongClickListener {
                hide()
                onHidden()
                true
            }
            windowManager.addView(created, layoutParams())
            view = created
        }
        val (label, icon, color) = result.band.look()
        val tint = ContextCompat.getColor(context, color)
        badge.findViewById<ImageView>(R.id.offer_badge_icon).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(tint)
        }
        badge.findViewById<TextView>(R.id.offer_badge_band).apply {
            text = context.getString(label)
            setTextColor(tint)
        }
        val net = money.formatSigned(result.netProfitCents)
        val amounts = result.netProfitPerKmCents?.let { context.getString(R.string.offer_badge_amounts, net, money.format(it)) } ?: net
        badge.findViewById<TextView>(R.id.offer_badge_amounts).text = amounts
        val source = context.getString(R.string.offer_badge_source, vehicleName)
        badge.findViewById<TextView>(R.id.offer_badge_source).text = source
        badge.contentDescription = listOf(context.getString(label), amounts, source, context.getString(R.string.offer_badge_hide_hint))
            .joinToString(". ")
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

private fun ResultBand.look(): Triple<Int, Int, Int> = when (this) {
    ResultBand.GOOD -> Triple(R.string.band_good, R.drawable.ic_band_good, R.color.badge_profit)
    ResultBand.PROFIT -> Triple(R.string.band_profit, R.drawable.ic_band_profit, R.color.badge_profit)
    ResultBand.BELOW_TARGET -> Triple(R.string.band_below_target, R.drawable.ic_band_below_target, R.color.badge_below_target)
    ResultBand.LOSS -> Triple(R.string.band_loss, R.drawable.ic_band_loss, R.color.badge_loss)
    ResultBand.BREAK_EVEN -> Triple(R.string.band_break_even, R.drawable.ic_band_break_even, R.color.off_white)
}
