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

    /**
     * Whole rupees rounded up, for a minimum that must be met: `Rs. 91` for Rs. 90.10. Short
     * enough for the floating bubble.
     */
    fun formatWholeUp(cents: Long): String =
        (if (cents < 0) "-" else "") + currencyPrefix + NBSP +
            formatDecimal(BigDecimal.valueOf(cents, 2).setScale(0, RoundingMode.CEILING).abs(), 0)

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

/**
 * A short rupee amount for chart labels, with no prefix: `950`, `4.1k`, `12.0k`, `1.2M`, `-350`.
 * Rounded half up; the full amount is always shown elsewhere.
 */
fun formatCompactRupees(cents: Long): String {
    val rupees = BigDecimal.valueOf(cents, 2).abs()
    val sign = if (cents < 0 && rupees.setScale(0, RoundingMode.HALF_UP).signum() != 0) "-" else ""
    val thousand = BigDecimal(1_000)
    val million = BigDecimal(1_000_000)
    return sign + when {
        rupees.setScale(0, RoundingMode.HALF_UP) < thousand -> formatDecimal(rupees, 0)
        rupees.divide(thousand).setScale(1, RoundingMode.HALF_UP) < thousand -> formatDecimal(rupees.divide(thousand), 1) + "k"
        else -> formatDecimal(rupees.divide(million), 1) + "M"
    }
}
