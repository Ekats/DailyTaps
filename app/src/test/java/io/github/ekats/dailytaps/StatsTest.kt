package io.github.ekats.dailytaps

import io.github.ekats.dailytaps.data.BoardEntity
import io.github.ekats.dailytaps.data.BoardWithSlots
import io.github.ekats.dailytaps.data.CsvExport
import io.github.ekats.dailytaps.data.EventKind
import io.github.ekats.dailytaps.data.EventSource
import io.github.ekats.dailytaps.data.ResetMode
import io.github.ekats.dailytaps.data.SlotEntity
import io.github.ekats.dailytaps.data.SlotType
import io.github.ekats.dailytaps.data.TapEventEntity
import io.github.ekats.dailytaps.domain.Days
import io.github.ekats.dailytaps.domain.ResetSchedule
import io.github.ekats.dailytaps.domain.Stats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter
import java.time.LocalDate
import java.time.ZoneId

class StatsTest {
    private val zone = ZoneId.of("Europe/Tallinn")
    private val day0 = LocalDate.of(2026, 9, 1).toEpochDay()

    private fun at(day: Long, hour: Int = 12) =
        LocalDate.ofEpochDay(day).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun event(
        day: Long,
        hour: Int = 12,
        slotId: Long = 1,
        kind: EventKind = EventKind.PRESS,
        state: Int = 1,
        delta: Int = 0,
        count: Int = 0,
        value: Double? = null,
    ) = TapEventEntity(
        boardId = 1, slotId = slotId, timestamp = at(day, hour), kind = kind, source = EventSource.WIDGET,
        stateIndex = state, delta = delta, count = count, value = value,
    )

    @Test
    fun `daily series covers every day and skips corrections`() {
        val events = listOf(
            event(day0), event(day0, 18), event(day0 + 2),
            event(day0 + 2, kind = EventKind.ADJUST),
        )
        val r = Stats.report(events, day0, day0 + 3, zone = zone)
        assertEquals(listOf(2.0, 0.0, 1.0, 0.0), r.dailyTaps.map { it.value })
        assertEquals(3, r.summary.totalTaps)
        assertEquals(2, r.summary.activeDays)
        assertEquals(2, r.hourly[12])
        assertEquals(1, r.hourly[18])
    }

    @Test
    fun `days follow local midnight, not UTC`() {
        // 00:30 in Tallinn is still the previous day in UTC.
        val r = Stats.report(listOf(event(day0 + 1, hour = 0)), day0, day0 + 1, zone = zone)
        assertEquals(listOf(0.0, 1.0), r.dailyTaps.map { it.value })
        assertEquals(day0 + 1, Days.of(at(day0 + 1, 0), zone))
    }

    @Test
    fun `streaks count consecutive active days`() {
        val active = setOf(day0, day0 + 1, day0 + 2, day0 + 5, day0 + 6)
        assertEquals(3, Stats.bestStreak(active))
        assertEquals(2, Stats.currentStreak(active, day0 + 6))
        // Today not tapped yet: the streak up to yesterday still counts.
        assertEquals(2, Stats.currentStreak(active, day0 + 7))
        assertEquals(0, Stats.currentStreak(active, day0 + 8))
    }

    @Test
    fun `toggle left on counts as done until switched off`() {
        val slot = SlotEntity(id = 1, boardId = 1, row = 0, col = 0)
        val events = listOf(event(day0, state = 1), event(day0 + 2, state = 0))
        val carried = Stats.report(events, day0, day0 + 3, slot, ResetSchedule(ResetMode.NEVER), zone = zone)
        assertEquals(listOf(1.0, 1.0, 0.0, 0.0), carried.completion.map { it.value })
        assertEquals(0.5, carried.completionRate!!, 1e-9)

        val daily = Stats.report(events, day0, day0 + 3, slot, ResetSchedule(ResetMode.DAILY), zone = zone)
        assertEquals(listOf(1.0, 0.0, 0.0, 0.0), daily.completion.map { it.value })
    }

    @Test
    fun `weekly reset keeps a toggle done until the reset`() {
        val slot = SlotEntity(id = 1, boardId = 1, row = 0, col = 0)
        // 2026-09-01 is a Tuesday; the weekly reset is Monday 00:01, which falls on day0 + 6.
        val weekly = ResetSchedule(ResetMode.WEEKLY, minuteOfDay = 1, weekday = 1)
        val r = Stats.report(listOf(event(day0, state = 1)), day0, day0 + 7, slot, weekly, zone = zone)
        assertEquals(listOf(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.0, 0.0), r.completion.map { it.value })
    }

    @Test
    fun `counter totals and target completion`() {
        val slot = SlotEntity(id = 1, boardId = 1, row = 0, col = 0, type = SlotType.COUNTER, counterTarget = 3)
        val events = listOf(
            event(day0, delta = 1, count = 1), event(day0, delta = 1, count = 2), event(day0, delta = 1, count = 3),
            event(day0 + 1, delta = 1, count = 1),
        )
        val r = Stats.report(events, day0, day0 + 1, slot, ResetSchedule(ResetMode.DAILY), zone = zone)
        assertEquals(listOf(3.0, 1.0), r.counterDaily.map { it.value })
        assertEquals(listOf(1.0, 0.0), r.completion.map { it.value })
    }

    @Test
    fun `value summary`() {
        val slot = SlotEntity(id = 1, boardId = 1, row = 0, col = 0, type = SlotType.VALUE)
        val events = listOf(
            event(day0, kind = EventKind.VALUE, value = 70.0),
            event(day0 + 1, kind = EventKind.VALUE, value = 72.0),
            event(day0 + 2, kind = EventKind.VALUE, value = 71.0),
        )
        val v = Stats.report(events, day0, day0 + 3, slot, zone = zone).valueSummary!!
        assertEquals(70.0, v.min, 0.0)
        assertEquals(72.0, v.max, 0.0)
        assertEquals(71.0, v.average, 1e-9)
        assertEquals(71.0, v.last, 0.0)
    }

    @Test
    fun `slot filter only counts that slot`() {
        val slot = SlotEntity(id = 2, boardId = 1, row = 0, col = 1)
        val r = Stats.report(listOf(event(day0, slotId = 1), event(day0, slotId = 2)), day0, day0, slot, zone = zone)
        assertEquals(1, r.summary.totalTaps)
    }

    @Test
    fun `csv quotes fields that need it`() {
        assertEquals("plain", CsvExport.quote("plain"))
        assertEquals("\"a,b\"", CsvExport.quote("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvExport.quote("say \"hi\""))

        val board = BoardWithSlots(BoardEntity(id = 1, title = "Habits, daily"), listOf(SlotEntity(id = 1, boardId = 1, row = 0, col = 0, label = "Gym")))
        val out = StringWriter()
        CsvExport.write(out, listOf(board), listOf(event(day0)), zone)
        val lines = out.toString().trim().lines()
        assertEquals(2, lines.size)
        assertTrue(lines[1], lines[1].startsWith("2026-09-01T12:00:00+03:00,\"Habits, daily\",Gym,1,1,STATES,PRESS,WIDGET,On,"))
    }
}
