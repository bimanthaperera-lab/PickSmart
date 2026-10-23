package lk.picksmart.ui.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import lk.picksmart.R
import lk.picksmart.domain.usecase.PeriodSummary
import lk.picksmart.domain.usecase.ReportPeriod
import lk.picksmart.ui.components.PicksmartCard
import lk.picksmart.ui.components.rememberMoneyFormatter
import lk.picksmart.ui.theme.PicksmartTheme
import lk.picksmart.ui.theme.Spacing
import lk.picksmart.util.formatCompactRupees
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * One bar per day of a week or month, in the chosen view, as in the "Reports — week" mockup.
 * Profit bars rise and loss bars hang below the zero line, so the sign never relies on colour.
 * Today is brighter with a bold label; days with nothing (and days still to come) get a flat stub.
 * A week shows each bar's amount ("4.1k"); a month is too narrow, so it shows dates every 7 days.
 * Screen readers hear the range and the best and worst days instead of every bar.
 */
@Composable
internal fun DailyProfitChart(days: List<PeriodSummary>, view: ReportView, period: ReportPeriod, today: LocalDate) {
    val money = rememberMoneyFormatter()
    val status = PicksmartTheme.status
    val colors = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    val dayPattern = stringResource(R.string.pattern_day)
    val dayFormat = remember(dayPattern, locale) { DateTimeFormatter.ofPattern(dayPattern, locale) }
    val axisFormat = remember(period, locale) { DateTimeFormatter.ofPattern(if (period == ReportPeriod.WEEK) "EEE" else "d", locale) }
    val narrowFormat = remember(locale) { DateTimeFormatter.ofPattern("EEEEE", locale) }
    val week = period == ReportPeriod.WEEK

    val values = days.map { if (view == ReportView.ESTIMATED) it.estimatedProfitCents else it.cashProfitCents }
    val past = days.indices.filter { !days[it].range.start.isAfter(today) }
    val best = past.maxByOrNull { values[it] }
    val worst = past.minByOrNull { values[it] }
    val title = stringResource(if (view == ReportView.ESTIMATED) R.string.reports_estimated_title else R.string.reports_cash_title)
    val description = if (best != null && worst != null) {
        stringResource(
            R.string.reports_chart_description,
            title,
            dayFormat.format(days.first().range.start),
            dayFormat.format(days.last().range.start),
            money.format(values[best]),
            dayFormat.format(days[best].range.start),
            money.format(values[worst]),
            dayFormat.format(days[worst].range.start),
        )
    } else {
        title
    }

    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = colors.onSurfaceVariant)
    val todayStyle = labelStyle.copy(color = colors.onSurface, fontWeight = FontWeight.Bold)
    val labelHeight = with(LocalDensity.current) { measurer.measure("0", labelStyle).size.height.toDp() }

    fun widest(texts: List<String>, style: TextStyle): Float =
        texts.maxOfOrNull { measurer.measure(it, style).size.width.toFloat() }?.takeIf { it > 0f } ?: 1f

    PicksmartCard(Modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT + Spacing.s + labelHeight)
                .semantics { contentDescription = description },
        ) {
            val maxUp = maxOf(0L, values.maxOrNull() ?: 0L)
            val maxDown = maxOf(0L, -(values.minOrNull() ?: 0L))
            val gap = Spacing.xs.toPx()
            val valueLabel = labelHeight.toPx() + gap
            // Room for the amounts above the tallest bar and below the deepest one.
            val top = if (week && maxUp > 0) valueLabel else 0f
            val bottom = if (week && maxDown > 0) valueLabel else 0f
            val barArea = CHART_HEIGHT.toPx() - top - bottom
            val span = (maxUp + maxDown).takeIf { it > 0 } ?: 1L
            val zeroY = top + barArea * maxUp / span
            val slot = size.width / days.size
            val barWidth = slot * if (week) 0.8f else 0.7f
            val corner = CornerRadius(minOf(barWidth / 4f, 6.dp.toPx()))
            val stub = 4.dp.toPx()
            // At large font sizes "Mon Tue" or "12.5k" can be wider than a bar's slot: use
            // one-letter days ("M T W"), then shrink what still doesn't fit, so labels never collide.
            val fitWidth = slot - 2.dp.toPx()
            val axisTexts = days.map { axisFormat.format(it.range.start) }
            val axisLabels = if (week && widest(axisTexts, todayStyle) > fitWidth) {
                days.map { narrowFormat.format(it.range.start) }
            } else {
                axisTexts
            }
            val axisScale = if (week) minOf(1f, fitWidth / widest(axisLabels, todayStyle)) else 1f
            val valueScale = if (week) minOf(1f, fitWidth / widest(values.map(::formatCompactRupees), todayStyle)) else 1f

            days.forEachIndexed { i, day ->
                val date = day.range.start
                val value = values[i]
                val isToday = date == today
                val x = slot * i + (slot - barWidth) / 2f
                val centre = slot * i + slot / 2f
                if (value == 0L || date.isAfter(today)) {
                    drawRoundRect(
                        color = colors.outline,
                        topLeft = Offset(x, zeroY - stub),
                        size = Size(barWidth, stub),
                        cornerRadius = CornerRadius(stub / 2f),
                    )
                } else {
                    val h = maxOf(barArea * abs(value) / span, stub)
                    val barTop = if (value > 0) zeroY - h else zeroY
                    val base = if (value > 0) status.profit else status.loss
                    drawRoundRect(
                        color = if (isToday) base else base.copy(alpha = 0.6f),
                        topLeft = Offset(x, barTop),
                        size = Size(barWidth, h),
                        cornerRadius = corner,
                    )
                    if (week) {
                        val text = measurer.measure(formatCompactRupees(value), (if (isToday) todayStyle else labelStyle).scaled(valueScale))
                        val y = if (value > 0) barTop - gap - text.size.height else barTop + h + gap
                        drawText(text, topLeft = Offset((centre - text.size.width / 2f).coerceIn(0f, size.width - text.size.width), y))
                    }
                }
                if (week || date.dayOfMonth % 7 == 1) {
                    val text = measurer.measure(axisLabels[i], (if (isToday) todayStyle else labelStyle).scaled(axisScale))
                    val left = (centre - text.size.width / 2f).coerceIn(0f, size.width - text.size.width)
                    drawText(text, topLeft = Offset(left, CHART_HEIGHT.toPx() + Spacing.s.toPx()))
                }
            }
            if (maxDown > 0) {
                drawLine(colors.outline, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 1.dp.toPx())
            }
        }
    }
}

private val CHART_HEIGHT = 180.dp

private fun TextStyle.scaled(factor: Float): TextStyle = if (factor >= 1f) this else copy(fontSize = fontSize * factor)
