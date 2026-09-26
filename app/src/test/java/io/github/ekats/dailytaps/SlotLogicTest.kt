package io.github.ekats.dailytaps

import io.github.ekats.dailytaps.data.BoardEntity
import io.github.ekats.dailytaps.data.Converters
import io.github.ekats.dailytaps.data.Defaults
import io.github.ekats.dailytaps.data.Fill
import io.github.ekats.dailytaps.data.GradientDirection
import io.github.ekats.dailytaps.data.SlotEntity
import io.github.ekats.dailytaps.data.SlotStyle
import io.github.ekats.dailytaps.data.SlotType
import io.github.ekats.dailytaps.domain.ColorMath
import io.github.ekats.dailytaps.domain.SlotLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SlotLogicTest {
    private val board = BoardEntity(id = 1)
    private val today = 20_000L

    private fun slot(type: SlotType = SlotType.STATES, states: List<SlotStyle> = Defaults.statesFor(type)) =
        SlotEntity(id = 1, boardId = 1, row = 0, col = 0, type = type, states = states)

    @Test
    fun `toggle flips between two states and wraps`() {
        val on = SlotLogic.pressed(slot(), today)
        assertEquals(1, on.stateIndex)
        assertEquals(today, on.lastChangedDay)
        assertEquals(0, SlotLogic.pressed(on, today).stateIndex)
    }

    @Test
    fun `extra states cycle in order`() {
        val three = slot(states = Defaults.statesFor(SlotType.STATES) + Defaults.onStyle("Third"))
        val seen = generateSequence(three) { SlotLogic.pressed(it, today) }.take(5).map { it.stateIndex }.toList()
        assertEquals(listOf(0, 1, 2, 0, 1), seen)
    }

    @Test
    fun `counter adds its step and reaches the second state at the target`() {
        val c = slot(SlotType.COUNTER).copy(counterStep = 2, counterTarget = 5)
        val a = SlotLogic.pressed(c, today)
        val b = SlotLogic.pressed(SlotLogic.pressed(a, today), today)
        assertEquals(2, a.count)
        assertEquals(0, a.stateIndex)
        assertEquals(6, b.count)
        assertEquals(1, b.stateIndex)
    }

    @Test
    fun `counter progress without a target is all or nothing`() {
        assertEquals(0f, SlotLogic.counterProgress(0, 0))
        assertEquals(1f, SlotLogic.counterProgress(3, 0))
        assertEquals(0.5f, SlotLogic.counterProgress(2, 4))
        assertEquals(1f, SlotLogic.counterProgress(9, 4))
    }

    @Test
    fun `daily reset only applies on a later day`() {
        val resetting = board.copy(resetDaily = true)
        val on = SlotLogic.pressed(slot(), today)
        assertEquals(1, SlotLogic.effective(on, resetting, today).stateIndex)
        assertEquals(0, SlotLogic.effective(on, resetting, today + 1).stateIndex)
        // Without the setting the state survives midnight.
        assertEquals(1, SlotLogic.effective(on, board, today + 1).stateIndex)
    }

    @Test
    fun `pressing after midnight on a resetting board starts from the first state`() {
        val resetting = board.copy(resetDaily = true)
        val yesterdayOn = SlotLogic.pressed(slot(), today - 1)
        val next = SlotLogic.pressed(SlotLogic.effective(yesterdayOn, resetting, today), today)
        assertEquals(1, next.stateIndex)
    }

    @Test
    fun `counter color is blended toward the target look`() {
        val c = slot(
            SlotType.COUNTER,
            listOf(
                SlotStyle("a", Fill.Solid(0xFF000000.toInt()), 0xFFFFFFFF.toInt()),
                SlotStyle("b", Fill.Solid(0xFFFFFFFF.toInt()), 0xFF000000.toInt()),
            ),
        ).copy(count = 1, counterTarget = 2, showStateText = true)
        val look = SlotLogic.appearance(c, board, today)
        assertEquals(Fill.Solid(0xFF808080.toInt()), look.fill)
        assertEquals("1/2", look.stateText)
    }

    @Test
    fun `value slot shows its last value with unit`() {
        val v = slot(SlotType.VALUE).copy(label = "Weight", valueUnit = "kg", showStateText = true)
        assertEquals("–", SlotLogic.appearance(v, board, today).stateText)
        val logged = SlotLogic.withValue(v, 72.5, today)
        val text = SlotLogic.appearance(logged, board, today).stateText!!
        // Decimal separator follows the device locale.
        assert(text == "72.5 kg" || text == "72,5 kg") { text }
        assertEquals(logged.states[1].fill, SlotLogic.appearance(logged, board, today).fill)
    }

    @Test
    fun `state text is hidden unless enabled`() {
        assertNull(SlotLogic.appearance(slot().copy(label = "Gym"), board, today).stateText)
        assertEquals("Off", SlotLogic.appearance(slot().copy(label = "Gym", showStateText = true), board, today).stateText)
    }

    @Test
    fun `changing type keeps colors and resets progress`() {
        val styled = slot(
            states = listOf(
                SlotStyle("Off", Fill.Solid(0xFF111111.toInt()), 1),
                SlotStyle("On", Fill.Gradient(0xFF222222.toInt(), 0xFF333333.toInt()), 2),
            ),
        ).copy(stateIndex = 1)
        val counter = SlotLogic.retyped(styled, SlotType.COUNTER)
        assertEquals(SlotType.COUNTER, counter.type)
        assertEquals(0, counter.stateIndex)
        assertEquals(styled.states.map { it.fill }, counter.states.map { it.fill })
        assertEquals(listOf("Zero", "Target"), counter.states.map { it.name })
    }

    @Test
    fun `gradient blending keeps the direction`() {
        val from = Fill.Solid(0xFF000000.toInt())
        val to = Fill.Gradient(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), GradientDirection.RADIAL)
        val mid = ColorMath.blendFill(from, to, 0.5f) as Fill.Gradient
        assertEquals(GradientDirection.RADIAL, mid.direction)
        assertEquals(0xFF808080.toInt(), mid.start)
    }

    @Test
    fun `hex parsing and formatting round trip`() {
        assertEquals(0xFF1C6B45.toInt(), ColorMath.parseHex("#1c6b45"))
        assertEquals(0x801C6B45.toInt(), ColorMath.parseHex("801C6B45"))
        assertEquals(0xFFAABBCC.toInt(), ColorMath.parseHex("#abc"))
        assertNull(ColorMath.parseHex("zz"))
        assertEquals("#1C6B45", ColorMath.toHex(0xFF1C6B45.toInt()))
        assertEquals("#801C6B45", ColorMath.toHex(0x801C6B45.toInt()))
    }

    @Test
    fun `hsv conversion round trips`() {
        val colors = listOf(0xFF1C6B45, 0xFFE53935, 0xFF1E88E5, 0xFFFDD835, 0xFF000000, 0xFFFFFFFF, 0xFF757575, 0xFF8E24AA).map { it.toInt() }
        colors.forEach { c ->
            val hsv = ColorMath.toHsv(c)
            assertEquals(ColorMath.toHex(c), ColorMath.toHex(ColorMath.fromHsv(255, hsv[0], hsv[1], hsv[2])))
        }
        val red = ColorMath.toHsv(0xFFFF0000.toInt())
        assertEquals(0f, red[0], 0.01f)
        assertEquals(240f, ColorMath.toHsv(0xFF0000FF.toInt())[0], 0.01f)
        assertEquals(0x80FF0000.toInt(), ColorMath.fromHsv(0x80, 360f, 1f, 1f))
    }

    @Test
    fun `styles survive the database converter`() {
        val c = Converters()
        val styles = listOf(
            SlotStyle("Off", Fill.Solid(0x00000000), 0xFFFFFFFF.toInt()),
            SlotStyle("On", Fill.Gradient(0xFF00FF00.toInt(), 0xFF0000FF.toInt(), GradientDirection.BOTTOM_LEFT_TOP_RIGHT), -1),
        )
        assertEquals(styles, c.stringToStyles(c.stylesToString(styles)))
        val fill: Fill = Fill.Gradient(1, 2, GradientDirection.LEFT_RIGHT)
        assertEquals(fill, c.stringToFill(c.fillToString(fill)))
    }
}
