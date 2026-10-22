package lk.picksmart.util

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
