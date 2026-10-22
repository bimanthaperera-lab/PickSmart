package lk.picksmart.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Non-breaking space, so "Rs." never wraps away from its number. */
private const val NBSP = ' '

/** Western digits, comma thousands separator and dot decimal, whatever the device locale. */
private val SYMBOLS = DecimalFormatSymbols(Locale.US)

/**
 * The one money formatter for the app (CLAUDE.md section 5): `Rs. 1,250.00`.
 *
 * @param currencyPrefix Comes from `strings.xml` in the UI so it can be translated later.
 */
class MoneyFormatter(private val currencyPrefix: String = "Rs.") {

    /** `Rs. 1,250.00`, or `-Rs. 50.00` for a negative amount. */
    fun format(cents: Long): String = (if (cents < 0) "-" else "") + unsigned(cents)

    /** Like [format] but with a `+` on positive amounts, for profit figures: `+Rs. 705.00`. */
    fun formatSigned(cents: Long): String = when {
        cents > 0 -> "+" + unsigned(cents)
        else -> format(cents)
    }

    private fun unsigned(cents: Long): String =
        currencyPrefix + NBSP + formatDecimal(BigDecimal.valueOf(cents, 2).abs(), 2)
}

/** A plain number with grouping and exactly [decimals] places, rounded half up: `81.5`, `1,234.0`. */
fun formatDecimal(value: Double, decimals: Int): String =
    formatDecimal(BigDecimal.valueOf(value), decimals)

private fun formatDecimal(value: BigDecimal, decimals: Int): String {
    val pattern = if (decimals > 0) "#,##0." + "0".repeat(decimals) else "#,##0"
    return DecimalFormat(pattern, SYMBOLS).apply { roundingMode = RoundingMode.HALF_UP }
        .format(value)
}
