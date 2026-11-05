package lk.picksmart.util

import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale

/**
 * The formatter every screen uses for a date or time [pattern] from strings.xml, in [locale].
 *
 * Sinhala gets the month names riders know from calendars (ජන, පෙබ … සැප්, ඔක්, නොවැ, දෙසැ). Some
 * Android versions give the traditional names instead (වප්, ඉල් …), which riders asked not to
 * see. Every other part of the pattern, and every other language, is Android's own.
 */
fun dateFormatter(pattern: String, locale: Locale): DateTimeFormatter {
    if (locale.language != SINHALA) return DateTimeFormatter.ofPattern(pattern, locale)
    val builder = DateTimeFormatterBuilder()
    var i = 0
    while (i < pattern.length) {
        val c = pattern[i]
        when {
            c == '\'' -> {
                // A quoted literal ('at', or '' for an apostrophe) passes through unchanged.
                var end = i + 1
                while (end < pattern.length) {
                    if (pattern[end] == '\'') {
                        if (end + 1 < pattern.length && pattern[end + 1] == '\'') end += 2 else break
                    } else {
                        end++
                    }
                }
                builder.appendPattern(pattern.substring(i, minOf(end + 1, pattern.length)))
                i = end + 1
            }
            c.isLetter() -> {
                var end = i
                while (end < pattern.length && pattern[end] == c) end++
                val run = pattern.substring(i, end)
                val months = when {
                    (c == 'M' || c == 'L') && run.length == 3 -> SinhalaMonthsShort
                    (c == 'M' || c == 'L') && run.length == 4 -> SinhalaMonthsFull
                    else -> null
                }
                if (months != null) builder.appendText(ChronoField.MONTH_OF_YEAR, months) else builder.appendPattern(run)
                i = end
            }
            else -> {
                builder.appendLiteral(c)
                i++
            }
        }
    }
    return builder.toFormatter(locale)
}

private const val SINHALA = "si"

private val SinhalaMonthsShort: Map<Long, String> = listOf(
    "ජන", "පෙබ", "මාර්තු", "අප්‍රේල්", "මැයි", "ජූනි", "ජූලි", "අගෝ", "සැප්", "ඔක්", "නොවැ", "දෙසැ",
).withIndex().associate { (index, name) -> index + 1L to name }

private val SinhalaMonthsFull: Map<Long, String> = listOf(
    "ජනවාරි", "පෙබරවාරි", "මාර්තු", "අප්‍රේල්", "මැයි", "ජූනි", "ජූලි", "අගෝස්තු", "සැප්තැම්බර්", "ඔක්තෝබර්", "නොවැම්බර්", "දෙසැම්බර්",
).withIndex().associate { (index, name) -> index + 1L to name }
