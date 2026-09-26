package io.github.ekats.dailytaps.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import io.github.ekats.dailytaps.domain.DayValue
import io.github.ekats.dailytaps.domain.ValuePoint
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/** Rounds [max] up to a clean axis maximum (1, 2, 5 x 10^n) split into [ticks] steps. */
fun niceAxis(min: Double, max: Double, ticks: Int = 4): List<Double> {
    if (max <= min) return listOf(min, min + 1)
    val raw = (max - min) / ticks
    val mag = 10.0.pow(floor(log10(raw)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * mag }.first { it >= raw }
    val lo = floor(min / step) * step
    val hi = ceil(max / step) * step
    val out = mutableListOf<Double>()
    var v = lo
    while (v <= hi + step / 2) {
        out += v
        v += step
    }
    return out
}

fun formatAxis(v: Double): String =
    if (v == floor(v) && kotlin.math.abs(v) < 1e9) "%,d".format(v.toLong()) else "%.1f".format(v)

/** The touch stand-in for hover: tapped value shown above the plot, with a hint otherwise. */
@Composable
private fun Readout(text: String?, hint: String) {
    Text(
        text ?: hint,
        style = MaterialTheme.typography.bodyMedium,
        color = if (text != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp).heightIn(min = 20.dp),
    )
}

private class AxisStyle(val grid: Color, val text: TextStyle)

@Composable
private fun axisStyle() = AxisStyle(
    grid = MaterialTheme.colorScheme.outlineVariant,
    text = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
)

/**
 * Columns growing from a zero baseline. [axisLabel] names the x positions that get a label under
 * the axis (return null to skip); [describe] builds the readout for a tapped column.
 */
@Composable
fun ColumnChart(
    values: List<Double>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 180.dp,
    hint: String,
    axisLabel: (Int) -> String?,
    describe: @Composable (Int) -> String,
) {
    var selected by remember(values) { mutableStateOf<Int?>(null) }
    val measurer = rememberTextMeasurer()
    val axis = axisStyle()
    val density = LocalDensity.current
    val ticks = niceAxis(0.0, values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0)
    val top = ticks.last()
    val highlight = MaterialTheme.colorScheme.onSurface

    Column(modifier) {
        Readout(selected?.let { describe(it) }, hint)
        Canvas(
            Modifier.fillMaxWidth().height(height).pointerInput(values) {
                detectTapGestures { pos ->
                    val left = with(density) { 36.dp.toPx() }
                    val band = (size.width - left) / values.size.coerceAtLeast(1)
                    val i = ((pos.x - left) / band).toInt()
                    selected = if (i in values.indices && i != selected) i else null
                }
            },
        ) {
            val left = 36.dp.toPx()
            val bottom = size.height - 18.dp.toPx()
            val plotH = bottom - 4.dp.toPx()
            drawYAxis(ticks, top, left, bottom, plotH, measurer, axis)

            if (values.isEmpty()) return@Canvas
            val band = (size.width - left) / values.size
            val gap = 2.dp.toPx()
            val barW = (band - gap).coerceIn(1f, 24.dp.toPx())
            val r = minOf(4.dp.toPx(), barW / 2)
            values.forEachIndexed { i, v ->
                val x = left + i * band + (band - barW) / 2
                val h = (v / top * plotH).toFloat()
                if (h > 0f) {
                    drawTopRoundedBar(x, bottom - h, barW, h, r, if (selected == null || selected == i) color else color.copy(alpha = 0.35f))
                }
                if (selected == i) {
                    drawLine(highlight, Offset(x, bottom + 1.dp.toPx()), Offset(x + barW, bottom + 1.dp.toPx()), 2.dp.toPx())
                }
                axisLabel(i)?.let { label ->
                    val m = measurer.measure(label, axis.text)
                    val lx = (x + barW / 2 - m.size.width / 2).coerceIn(left, size.width - m.size.width)
                    drawText(m, topLeft = Offset(lx, bottom + 3.dp.toPx()))
                }
            }
        }
    }
}

private fun DrawScope.drawYAxis(
    ticks: List<Double>,
    top: Double,
    left: Float,
    bottom: Float,
    plotH: Float,
    measurer: TextMeasurer,
    axis: AxisStyle,
    min: Double = 0.0,
) {
    ticks.forEach { t ->
        val y = bottom - ((t - min) / (top - min) * plotH).toFloat()
        drawLine(axis.grid, Offset(left, y), Offset(size.width, y), 1f)
        val m = measurer.measure(formatAxis(t), axis.text)
        drawText(m, topLeft = Offset(left - m.size.width - 6.dp.toPx(), y - m.size.height / 2))
    }
}

/** Bar with a 4dp rounded data end and a square base on the baseline. */
private fun DrawScope.drawTopRoundedBar(x: Float, y: Float, w: Float, h: Float, r: Float, color: Color) {
    val rr = minOf(r, h)
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                left = x, top = y, right = x + w, bottom = y + h,
                topLeftCornerRadius = CornerRadius(rr, rr),
                topRightCornerRadius = CornerRadius(rr, rr),
                bottomLeftCornerRadius = CornerRadius.Zero,
                bottomRightCornerRadius = CornerRadius.Zero,
            ),
        )
    }
    drawPath(path, color)
}

/** Logged values over time: 2dp line, 10% area wash, markers when sparse. */
@Composable
fun LineChart(
    points: List<ValuePoint>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 200.dp,
    hint: String,
    describe: @Composable (ValuePoint) -> String,
    axisLabel: (Long) -> String,
) {
    var selected by remember(points) { mutableStateOf<Int?>(null) }
    val measurer = rememberTextMeasurer()
    val axis = axisStyle()
    val density = LocalDensity.current
    val surface = MaterialTheme.colorScheme.surface
    val lo = points.minOfOrNull { it.value } ?: 0.0
    val hi = points.maxOfOrNull { it.value } ?: 1.0
    val pad = if (hi == lo) max(1.0, kotlin.math.abs(hi) * 0.1) else (hi - lo) * 0.1
    val ticks = niceAxis(if (lo >= 0 && lo - pad < 0) 0.0 else lo - pad, hi + pad)
    val t0 = points.firstOrNull()?.timestamp ?: 0L
    val t1 = points.lastOrNull()?.timestamp ?: 1L
    val span = (t1 - t0).coerceAtLeast(1L).toFloat()

    Column(modifier) {
        Readout(selected?.let { describe(points[it]) }, hint)
        Canvas(
            Modifier.fillMaxWidth().height(height).pointerInput(points) {
                detectTapGestures { pos ->
                    if (points.isEmpty()) return@detectTapGestures
                    val left = with(density) { 40.dp.toPx() }
                    val right = size.width - with(density) { 8.dp.toPx() }
                    val xs = points.map { left + (it.timestamp - t0) / span * (right - left) }
                    val i = xs.indices.minBy { kotlin.math.abs(xs[it] - pos.x) }
                    selected = if (i != selected) i else null
                }
            },
        ) {
            val left = 40.dp.toPx()
            val right = size.width - 8.dp.toPx()
            val bottom = size.height - 18.dp.toPx()
            val plotH = bottom - 6.dp.toPx()
            val min = ticks.first()
            val top = ticks.last()
            drawYAxis(ticks, top, left, bottom, plotH, measurer, axis, min)
            if (points.isEmpty()) return@Canvas

            fun px(p: ValuePoint) = Offset(
                if (points.size == 1) (left + right) / 2 else left + (p.timestamp - t0) / span * (right - left),
                bottom - ((p.value - min) / (top - min) * plotH).toFloat(),
            )
            val offsets = points.map(::px)
            val line = Path().apply {
                offsets.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
            }
            val area = Path().apply {
                addPath(line)
                lineTo(offsets.last().x, bottom)
                lineTo(offsets.first().x, bottom)
                close()
            }
            drawPath(area, color.copy(alpha = 0.1f))
            drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

            val showMarkers = points.size <= 40
            offsets.forEachIndexed { i, o ->
                if (showMarkers || i == offsets.lastIndex || i == selected) {
                    drawCircle(surface, radius = 6.dp.toPx(), center = o)
                    drawCircle(color, radius = 4.dp.toPx(), center = o)
                }
            }
            selected?.let { drawLine(axis.grid, Offset(offsets[it].x, 0f), Offset(offsets[it].x, bottom), 1.dp.toPx()) }

            listOf(t0, t1).distinct().forEachIndexed { i, t ->
                val m = measurer.measure(axisLabel(t), axis.text)
                val x = if (i == 0) left else right - m.size.width
                drawText(m, topLeft = Offset(x, bottom + 3.dp.toPx()))
            }
        }
    }
}

/**
 * Calendar heatmap: one column per week, one row per weekday, starting on [firstDay]. Zero days
 * sit on a neutral cell; the rest use [ramp] from light (few) to dark (many).
 */
@Composable
fun CalendarHeatmap(
    days: List<DayValue>,
    ramp: List<Color>,
    firstDay: DayOfWeek,
    modifier: Modifier = Modifier,
    hint: String,
    describe: @Composable (DayValue) -> String,
) {
    var selected by remember(days) { mutableStateOf<DayValue?>(null) }
    val measurer = rememberTextMeasurer()
    val axis = axisStyle()
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val ring = MaterialTheme.colorScheme.onSurface
    val maxV = days.maxOfOrNull { it.value } ?: 0.0
    if (days.isEmpty()) return

    val start = LocalDate.ofEpochDay(days.first().epochDay)
    val offset = ((start.dayOfWeek.value - firstDay.value) + 7) % 7
    val weeks = (offset + days.size + 6) / 7
    val locale = Locale.getDefault()

    fun bucket(v: Double): Color {
        if (v <= 0 || maxV <= 0) return empty
        val i = ((v / maxV) * ramp.size).toInt().coerceIn(0, ramp.lastIndex)
        return ramp[i]
    }

    Column(modifier) {
        Readout(selected?.let { describe(it) }, hint)
        val labelW = 28.dp
        Canvas(
            Modifier.fillMaxWidth().height(((7 * 16) + 18).dp).pointerInput(days) {
                detectTapGestures { pos ->
                    val lw = labelW.toPx()
                    val cell = minOf((size.width - lw) / weeks, 16.dp.toPx())
                    val top = 16.dp.toPx()
                    val w = ((pos.x - lw) / cell).toInt()
                    val d = ((pos.y - top) / cell).toInt()
                    val index = w * 7 + d - offset
                    val hit = days.getOrNull(index).takeIf { w in 0 until weeks && d in 0..6 }
                    selected = if (hit == selected) null else hit
                }
            },
        ) {
            val lw = labelW.toPx()
            val cell = minOf((size.width - lw) / weeks, 16.dp.toPx())
            val top = 16.dp.toPx()
            val gap = 2.dp.toPx()

            // Weekday labels on rows 1, 3, 5 (Mon/Wed/Fri for a Monday week).
            for (d in listOf(0, 2, 4)) {
                val name = firstDay.plus(d.toLong()).getDisplayName(DateTextStyle.SHORT, locale)
                val m = measurer.measure(name, axis.text)
                drawText(m, topLeft = Offset(0f, top + d * cell + (cell - m.size.height) / 2))
            }
            var lastMonth = -1
            days.forEachIndexed { i, dv ->
                val pos = i + offset
                val w = pos / 7
                val d = pos % 7
                val x = lw + w * cell
                val y = top + d * cell
                drawRoundRect(bucket(dv.value), Offset(x, y), Size(cell - gap, cell - gap), CornerRadius(3.dp.toPx()))
                if (selected == dv) {
                    drawRoundRect(ring, Offset(x, y), Size(cell - gap, cell - gap), CornerRadius(3.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                }
                val date = LocalDate.ofEpochDay(dv.epochDay)
                if (d == 0 && date.monthValue != lastMonth && x + 24.dp.toPx() < size.width) {
                    lastMonth = date.monthValue
                    val m = measurer.measure(date.month.getDisplayName(DateTextStyle.SHORT, locale), axis.text)
                    drawText(m, topLeft = Offset(x, 0f))
                }
            }
        }
        // Legend: less -> more.
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            (listOf(empty) + ramp).forEach {
                Box(Modifier.padding(horizontal = 1.dp).size(12.dp).clip(RoundedCornerShape(3.dp)).background(it))
            }
            Text(formatAxis(maxV), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

data class BarItem(val label: String, val value: Double, val swatch: Color?)

/** Horizontal bars with the value at the tip; a swatch next to the label carries identity. */
@Composable
fun HorizontalBars(items: List<BarItem>, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val maxV = items.maxOfOrNull { it.value }?.coerceAtLeast(1.0) ?: 1.0
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(item.swatch ?: color))
                Text(
                    item.label,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp).width(96.dp),
                )
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    val fraction = (item.value / maxV).toFloat().coerceIn(0f, 1f)
                    if (fraction > 0f) {
                        Box(
                            Modifier.weight(fraction).height(16.dp)
                                .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                                .background(color),
                        )
                    }
                    if (fraction < 1f) Box(Modifier.weight(1f - fraction))
                }
                Text(
                    formatAxis(item.value),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(48.dp),
                )
            }
        }
    }
}
