package lk.picksmart.util

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Parses rupees typed in a field ("300", "297.5", "1250.00") into cents, rounding half up to the
 * cent. Returns null for empty or invalid text, negatives, or amounts too large for a Long.
 */
fun parseRupeesToCents(text: String): Long? {
    val value = text.trim().toBigDecimalOrNull() ?: return null
    if (value.signum() < 0) return null
    val cents = value.movePointRight(2).setScale(0, RoundingMode.HALF_UP)
    return if (cents <= BigDecimal.valueOf(Long.MAX_VALUE)) cents.longValueExact() else null
}

/** Parses a plain non-negative number such as km or km/L. Null for empty, invalid or infinite. */
fun parseDecimal(text: String): Double? =
    text.trim().toBigDecimalOrNull()?.takeIf { it.signum() >= 0 }?.toDouble()?.takeIf { it.isFinite() }

/** Cents as editable text without grouping: 30000 → "300.00". */
fun centsToInput(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString()

/** A number as editable text without trailing zeros: 40.0 → "40", 37.5 → "37.5". */
fun decimalToInput(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
