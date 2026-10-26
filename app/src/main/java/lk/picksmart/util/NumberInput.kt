package lk.picksmart.util

/**
 * How many whole-number digits each kind of number field takes, so a rider can't type or paste a
 * number long enough to overflow a total. Money allows Rs. 9,999,999.99, far above any ride or
 * fill-up, and every sum of such values over years of records still fits a Long of cents.
 */
object InputDigits {
    const val MONEY = 7
    const val DISTANCE = 4
    const val EFFICIENCY = 3
    const val LITRES = 4
    const val ODOMETER = 7
}

/**
 * Cleans what a rider types into a number field: keeps digits and one decimal point, drops
 * everything else (spaces, commas, a pasted "Rs." or "LKR"), and trims decimals beyond
 * [maxDecimals]. Returns the text to show, which is always empty or a valid non-negative number
 * in progress such as `12.`.
 *
 * A dot counts as the decimal point only when it follows a digit or opens the text (".5"), and is
 * followed by a digit or ends the text ("12."). So the dot in a pasted "Rs. 1,000.00" or
 * "Rs.1000" is ignored instead of turning the amount into 0.10.
 */
fun sanitizeDecimalInput(text: String, maxDecimals: Int): String {
    val out = StringBuilder()
    var seenDot = false
    var decimals = 0
    val start = text.indexOfFirst { !it.isWhitespace() }
    for ((i, c) in text.withIndex()) {
        when {
            c in '0'..'9' -> {
                if (seenDot) {
                    if (decimals >= maxDecimals) continue
                    decimals++
                }
                out.append(c)
            }
            c == '.' && !seenDot && maxDecimals > 0 -> {
                val afterDigit = out.isNotEmpty() || i == start
                val next = text.getOrNull(i + 1)
                val beforeDigitOrEnd = next == null || next in '0'..'9'
                if (afterDigit && beforeDigitOrEnd) {
                    seenDot = true
                    if (out.isEmpty()) out.append('0')
                    out.append(c)
                }
            }
        }
    }
    return out.toString()
}

/**
 * What a number field shows after the rider changed [old] to [new]: [new] cleaned with
 * [sanitizeDecimalInput]. A change that would take the whole-number part past [maxIntegerDigits]
 * is refused, so [old] stays and nothing jumps under the cursor; a paste that is too long is cut
 * to the limit instead. A value already over the limit (saved by an older version) can still be
 * shortened.
 */
fun limitDecimalInput(old: String, new: String, maxDecimals: Int, maxIntegerDigits: Int): String {
    val clean = sanitizeDecimalInput(new, maxDecimals)
    val digits = integerDigits(clean)
    if (digits <= maxIntegerDigits || digits <= integerDigits(old)) return clean
    if (integerDigits(old) <= maxIntegerDigits && clean.length <= old.length + 1) return old
    val dot = clean.indexOf('.')
    val whole = if (dot < 0) clean else clean.substring(0, dot)
    return whole.take(maxIntegerDigits) + if (dot < 0) "" else clean.substring(dot)
}

private fun integerDigits(text: String): Int = text.substringBefore('.').count { it in '0'..'9' }
