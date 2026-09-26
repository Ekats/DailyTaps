package io.github.ekats.dailytaps.domain

import io.github.ekats.dailytaps.data.EventKind
import io.github.ekats.dailytaps.data.SlotEntity
import io.github.ekats.dailytaps.data.SlotType
import io.github.ekats.dailytaps.data.TapEventEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object Days {
    fun of(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate().toEpochDay()

    fun today(zone: ZoneId = ZoneId.systemDefault()): Long = LocalDate.now(zone).toEpochDay()

    fun startOfDayMillis(epochDay: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        LocalDate.ofEpochDay(epochDay).atStartOfDay(zone).toInstant().toEpochMilli()

    fun hourOf(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): Int =
        Instant.ofEpochMilli(timestamp).atZone(zone).hour
}

data class DayValue(val epochDay: Long, val value: Double)

data class ValuePoint(val timestamp: Long, val value: Double)

data class SlotTotal(val slotId: Long, val taps: Int)

data class Summary(
    val totalTaps: Int,
    val todayTaps: Int,
    val activeDays: Int,
    val currentStreak: Int,
    val bestStreak: Int,
    val dailyAverage: Double,
)

data class ValueSummary(val min: Double, val max: Double, val average: Double, val last: Double, val count: Int)

/**
 * Everything the stats screen draws, computed from events in [fromDay]..[toDay] (inclusive).
 * [completion] is only filled when [slot] is set: per day, whether the slot ended that day "done"
 * (a non-first state, a counter at its target, or a logged value).
 */
data class StatsReport(
    val fromDay: Long,
    val toDay: Long,
    val summary: Summary,
    val dailyTaps: List<DayValue>,
    val hourly: List<Int>,
    val perSlot: List<SlotTotal>,
    val slot: SlotEntity?,
    val completion: List<DayValue>,
    val completionRate: Double?,
    val counterDaily: List<DayValue>,
    val values: List<ValuePoint>,
    val valueSummary: ValueSummary?,
)

object Stats {

    fun isTap(e: TapEventEntity) = e.kind != EventKind.ADJUST

    fun report(
        events: List<TapEventEntity>,
        fromDay: Long,
        toDay: Long,
        slot: SlotEntity? = null,
        carryOver: Boolean = true,
        zone: ZoneId = ZoneId.systemDefault(),
    ): StatsReport {
        val inRange = events
            .filter { slot == null || it.slotId == slot.id }
            .filter { Days.of(it.timestamp, zone) in fromDay..toDay }
        val taps = inRange.filter(::isTap)
        val byDay = taps.groupingBy { Days.of(it.timestamp, zone) }.eachCount()
        val days = (fromDay..toDay).toList()
        val dailyTaps = days.map { DayValue(it, (byDay[it] ?: 0).toDouble()) }

        val activeSet = byDay.keys
        val summary = Summary(
            totalTaps = taps.size,
            todayTaps = byDay[toDay] ?: 0,
            activeDays = activeSet.size,
            currentStreak = currentStreak(activeSet, toDay),
            bestStreak = bestStreak(activeSet),
            dailyAverage = if (days.isEmpty()) 0.0 else taps.size.toDouble() / days.size,
        )

        val hourly = MutableList(24) { 0 }
        taps.forEach { hourly[Days.hourOf(it.timestamp, zone)]++ }

        val perSlot = taps.groupingBy { it.slotId }.eachCount()
            .map { (id, n) -> SlotTotal(id, n) }
            .sortedByDescending { it.taps }

        val completion = if (slot != null) completion(slot, inRange, days, carryOver, zone) else emptyList()
        val completionRate = completion.takeIf { it.isNotEmpty() }?.let { c -> c.count { it.value > 0 }.toDouble() / c.size }

        val counterDaily = if (slot?.type == SlotType.COUNTER) {
            val sums = inRange.filter { it.kind == EventKind.PRESS }
                .groupBy { Days.of(it.timestamp, zone) }
                .mapValues { (_, list) -> list.sumOf { it.delta }.toDouble() }
            days.map { DayValue(it, sums[it] ?: 0.0) }
        } else {
            emptyList()
        }

        val values = inRange.filter { it.kind == EventKind.VALUE && it.value != null }
            .map { ValuePoint(it.timestamp, it.value!!) }
        val valueSummary = values.takeIf { it.isNotEmpty() }?.let { v ->
            ValueSummary(
                min = v.minOf { it.value },
                max = v.maxOf { it.value },
                average = v.sumOf { it.value } / v.size,
                last = v.last().value,
                count = v.size,
            )
        }

        return StatsReport(
            fromDay, toDay, summary, dailyTaps, hourly, perSlot, slot,
            completion, completionRate, counterDaily, values, valueSummary,
        )
    }

    /**
     * 1.0 for days the slot finished "done", 0.0 otherwise. Unless the board resets daily, a state
     * carries over to the following days, so a toggle left on counts until it is switched off.
     * The state before [days] starts is taken as the first one.
     */
    private fun completion(
        slot: SlotEntity,
        events: List<TapEventEntity>,
        days: List<Long>,
        carryOver: Boolean,
        zone: ZoneId,
    ): List<DayValue> {
        val byDay = events.sortedBy { it.timestamp }.groupBy { Days.of(it.timestamp, zone) }
        var state = 0
        var count = 0
        return days.map { day ->
            val todays = byDay[day].orEmpty()
            if (!carryOver) {
                state = 0
                count = 0
            }
            todays.lastOrNull()?.let {
                state = it.stateIndex
                count = it.count
            }
            val done = when (slot.type) {
                SlotType.VALUE -> todays.any { it.kind == EventKind.VALUE }
                SlotType.COUNTER -> SlotLogic.counterProgress(count, slot.counterTarget) >= 1f
                SlotType.STATES -> state != 0
            }
            DayValue(day, if (done) 1.0 else 0.0)
        }
    }

    /** Consecutive active days ending today, or ending yesterday when today has no taps yet. */
    fun currentStreak(active: Set<Long>, today: Long): Int {
        var day = if (today in active) today else today - 1
        var n = 0
        while (day in active) {
            n++
            day--
        }
        return n
    }

    fun bestStreak(active: Set<Long>): Int {
        var best = 0
        var run = 0
        var prev: Long? = null
        for (d in active.sorted()) {
            run = if (prev != null && d == prev + 1) run + 1 else 1
            best = maxOf(best, run)
            prev = d
        }
        return best
    }
}
