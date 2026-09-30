package io.github.ekats.dailytaps.domain

import io.github.ekats.dailytaps.data.BoardEntity
import io.github.ekats.dailytaps.data.Defaults
import io.github.ekats.dailytaps.data.Fill
import io.github.ekats.dailytaps.data.GradientDirection
import io.github.ekats.dailytaps.data.SlotEntity
import io.github.ekats.dailytaps.data.SlotStyle
import io.github.ekats.dailytaps.data.SlotType
import java.text.DecimalFormat
import kotlin.math.roundToInt

/** What a slot looks like right now: shared by the home screen widget and the in-app grid. */
data class SlotAppearance(
    val fill: Fill,
    val textColor: Int,
    val label: String,
    val stateText: String?,
)

object SlotLogic {

    /** The slot as it should be seen at [now], applying the board's reset schedule. */
    fun effective(slot: SlotEntity, board: BoardEntity, now: Long): SlotEntity =
        if (hasProgress(slot) && ResetSchedule.of(board).resetBetween(slot.lastChangedAt, now)) {
            slot.copy(stateIndex = 0, count = 0, lastValue = null)
        } else {
            slot
        }

    private fun hasProgress(slot: SlotEntity) = slot.stateIndex != 0 || slot.count != 0 || slot.lastValue != null

    /** Result of a tap on a STATES or COUNTER slot. VALUE slots go through [withValue] instead. */
    fun pressed(slot: SlotEntity, now: Long): SlotEntity = when (slot.type) {
        SlotType.STATES -> slot.copy(
            stateIndex = (slot.stateIndex.coerceIn(0, slot.states.lastIndex) + 1) % slot.states.size.coerceAtLeast(1),
            lastChangedAt = now,
        )
        SlotType.COUNTER -> slot.copy(
            count = slot.count + slot.counterStep,
            stateIndex = counterStateIndex(slot.count + slot.counterStep, slot.counterTarget),
            lastChangedAt = now,
        )
        SlotType.VALUE -> slot
    }

    fun withValue(slot: SlotEntity, value: Double, now: Long): SlotEntity =
        slot.copy(lastValue = value, stateIndex = 1, lastChangedAt = now)

    fun withCount(slot: SlotEntity, count: Int, now: Long): SlotEntity =
        slot.copy(count = count, stateIndex = counterStateIndex(count, slot.counterTarget), lastChangedAt = now)

    fun withState(slot: SlotEntity, index: Int, now: Long): SlotEntity =
        slot.copy(stateIndex = index.coerceIn(0, slot.states.lastIndex), lastChangedAt = now)

    fun cleared(slot: SlotEntity, now: Long): SlotEntity =
        slot.copy(stateIndex = 0, count = 0, lastValue = null, lastChangedAt = now)

    /** Switches type, keeping the colors of the first states and resetting progress. */
    fun retyped(slot: SlotEntity, type: SlotType): SlotEntity {
        if (type == slot.type) return slot
        val defaults = Defaults.statesFor(type)
        val states = defaults.mapIndexed { i, d ->
            slot.states.getOrNull(i)?.let { d.copy(fill = it.fill, textColor = it.textColor) } ?: d
        }
        return slot.copy(type = type, states = states, stateIndex = 0, count = 0, lastValue = null)
    }

    private fun counterStateIndex(count: Int, target: Int): Int =
        if (target > 0) (if (count >= target) 1 else 0) else (if (count != 0) 1 else 0)

    /** How far a counter is toward its target, 0..1. Without a target any count is "done". */
    fun counterProgress(count: Int, target: Int): Float = when {
        target > 0 -> (count.toFloat() / target).coerceIn(0f, 1f)
        count != 0 -> 1f
        else -> 0f
    }

    fun appearance(slot: SlotEntity, board: BoardEntity, now: Long): SlotAppearance {
        val s = effective(slot, board, now)
        val states = s.states.ifEmpty { listOf(Defaults.offStyle()) }
        fun style(i: Int): SlotStyle = states[i.coerceIn(0, states.lastIndex)]

        return when (s.type) {
            SlotType.STATES -> {
                val st = style(s.stateIndex)
                SlotAppearance(st.fill, st.textColor, s.label, st.name.takeIf { s.showStateText })
            }
            SlotType.COUNTER -> {
                val t = counterProgress(s.count, s.counterTarget)
                val from = style(0)
                val to = style(1)
                val text = if (s.counterTarget > 0) "${s.count}/${s.counterTarget}" else s.count.toString()
                SlotAppearance(
                    fill = ColorMath.blendFill(from.fill, to.fill, t),
                    textColor = if (t >= 0.5f) to.textColor else from.textColor,
                    label = s.label,
                    stateText = text.takeIf { s.showStateText || s.label.isBlank() },
                )
            }
            SlotType.VALUE -> {
                val st = style(if (s.lastValue != null) 1 else 0)
                val text = s.lastValue?.let { formatValue(it, s.valueUnit) } ?: "–"
                SlotAppearance(st.fill, st.textColor, s.label, text.takeIf { s.showStateText || s.label.isBlank() })
            }
        }
    }

    /**
     * Label size in sp: the slot's own setting, else the board's, else a size that fits the cell
     * ([cellDp] is the shorter side) scaled by the board's text size.
     */
    fun labelSizeSp(slot: SlotEntity, board: BoardEntity, cellDp: Float): Float = when {
        slot.labelSizeSp > 0 -> slot.labelSizeSp.toFloat()
        board.labelSizeSp > 0 -> board.labelSizeSp.toFloat()
        else -> (cellDp * 0.22f).coerceIn(8f, 20f) * board.textScale.factor
    }

    /**
     * How many lines of a [labelSizeSp] label fit in a cell [cellHeightDp] tall, leaving room for
     * the state text when there is one. Assumes 1sp ~ 1dp; a larger font scale just clips later.
     */
    fun labelMaxLines(cellHeightDp: Float, labelSizeSp: Float, hasStateText: Boolean): Int {
        val lineHeight = labelSizeSp * 1.2f
        val stateHeight = if (hasStateText) labelSizeSp * 0.85f * 1.3f else 0f
        val available = cellHeightDp - 6f - stateHeight
        return (available / lineHeight).toInt().coerceIn(1, 10)
    }

    fun formatValue(value: Double, unit: String = ""): String {
        val number = DecimalFormat("0.##").format(value)
        return if (unit.isBlank()) number else "$number $unit"
    }
}

object ColorMath {
    fun alpha(c: Int) = (c ushr 24) and 0xFF
    fun red(c: Int) = (c shr 16) and 0xFF
    fun green(c: Int) = (c shr 8) and 0xFF
    fun blue(c: Int) = c and 0xFF

    fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        (a.coerceIn(0, 255) shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    fun lerp(a: Int, b: Int, t: Float): Int {
        fun ch(x: Int, y: Int) = (x + (y - x) * t).roundToInt()
        return argb(ch(alpha(a), alpha(b)), ch(red(a), red(b)), ch(green(a), green(b)), ch(blue(a), blue(b)))
    }

    fun blendFill(from: Fill, to: Fill, t: Float): Fill = when {
        t <= 0f -> from
        t >= 1f -> to
        from is Fill.Solid && to is Fill.Solid -> Fill.Solid(lerp(from.color, to.color, t))
        else -> {
            val a = asGradient(from, (to as? Fill.Gradient)?.direction)
            val b = asGradient(to, a.direction)
            Fill.Gradient(lerp(a.start, b.start, t), lerp(a.end, b.end, t), b.direction)
        }
    }

    private fun asGradient(fill: Fill, direction: GradientDirection?): Fill.Gradient =
        when (fill) {
            is Fill.Gradient -> fill
            is Fill.Solid -> Fill.Gradient(fill.color, fill.color, direction ?: GradientDirection.TOP_BOTTOM)
        }

    /** Hue 0..360, saturation 0..1, value 0..1 (same results as android.graphics.Color.colorToHSV). */
    fun toHsv(c: Int): FloatArray {
        val r = red(c) / 255f
        val g = green(c) / 255f
        val b = blue(c) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        val h = when {
            d == 0f -> 0f
            max == r -> 60f * (((g - b) / d) % 6f)
            max == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }.let { if (it < 0f) it + 360f else it }
        val s = if (max == 0f) 0f else d / max
        return floatArrayOf(h, s, max)
    }

    fun fromHsv(alpha: Int, h: Float, s: Float, v: Float): Int {
        val hh = ((h % 360f) + 360f) % 360f
        val c = v * s
        val x = c * (1 - kotlin.math.abs((hh / 60f) % 2f - 1))
        val m = v - c
        val (r, g, b) = when {
            hh < 60f -> Triple(c, x, 0f)
            hh < 120f -> Triple(x, c, 0f)
            hh < 180f -> Triple(0f, c, x)
            hh < 240f -> Triple(0f, x, c)
            hh < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun ch(f: Float) = ((f + m) * 255f).roundToInt()
        return argb(alpha, ch(r), ch(g), ch(b))
    }

    /** Relative luminance (WCAG), ignoring alpha. */
    fun luminance(c: Int): Double {
        fun lin(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * lin(red(c)) + 0.7152 * lin(green(c)) + 0.0722 * lin(blue(c))
    }

    /** Black or white, whichever reads better on [background]. */
    fun readableOn(background: Int): Int = if (luminance(background) > 0.4) 0xFF1C1B1F.toInt() else 0xFFFFFFFF.toInt()

    fun primaryColor(fill: Fill): Int = when (fill) {
        is Fill.Solid -> fill.color
        is Fill.Gradient -> lerp(fill.start, fill.end, 0.5f)
    }

    fun toHex(c: Int): String = if (alpha(c) == 0xFF) {
        "#%06X".format(c and 0xFFFFFF)
    } else {
        "#%08X".format(c)
    }

    fun parseHex(text: String): Int? {
        val h = text.trim().removePrefix("#")
        return when (h.length) {
            6 -> h.toLongOrNull(16)?.let { (0xFF000000 or it).toInt() }
            8 -> h.toLongOrNull(16)?.toInt()
            3 -> h.map { "$it$it" }.joinToString("").toLongOrNull(16)?.let { (0xFF000000 or it).toInt() }
            else -> null
        }
    }
}
