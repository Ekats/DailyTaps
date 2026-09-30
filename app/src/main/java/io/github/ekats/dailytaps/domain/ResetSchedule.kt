package io.github.ekats.dailytaps.domain

import io.github.ekats.dailytaps.data.BoardEntity
import io.github.ekats.dailytaps.data.ResetMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/**
 * When a board's slots go back to their first state: never, or every day, week or month at a set
 * local time. Everything is computed from "when did the current period start", so a phone that was
 * off at the reset moment still shows the right state afterwards.
 */
data class ResetSchedule(
    val mode: ResetMode,
    /** Minutes after local midnight, 0..1439. */
    val minuteOfDay: Int = 0,
    /** 1 = Monday .. 7 = Sunday, for [ResetMode.WEEKLY]. */
    val weekday: Int = 1,
    /** 1..31 for [ResetMode.MONTHLY]; clamped to the month's length (31 means "last day"). */
    val monthDay: Int = 1,
) {
    private val time: LocalTime get() = LocalTime.of(minuteOfDay.coerceIn(0, 1439) / 60, minuteOfDay.coerceIn(0, 1439) % 60)

    private fun boundaryOn(date: LocalDate, zone: ZoneId): ZonedDateTime = date.atTime(time).atZone(zone)

    private fun monthBoundary(year: Int, month: Int, zone: ZoneId): ZonedDateTime {
        val first = LocalDate.of(year, month, 1)
        return boundaryOn(first.withDayOfMonth(monthDay.coerceIn(1, first.lengthOfMonth())), zone)
    }

    /** Start of the reset period containing [now], or null when the board never resets. */
    fun periodStart(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val local = Instant.ofEpochMilli(now).atZone(zone)
        val date = local.toLocalDate()
        val start = when (mode) {
            ResetMode.NEVER -> return null
            ResetMode.DAILY -> boundaryOn(date, zone).let { if (it.isAfter(local)) boundaryOn(date.minusDays(1), zone) else it }
            ResetMode.WEEKLY -> {
                val day = DayOfWeek.of(weekday.coerceIn(1, 7))
                val candidate = boundaryOn(date.with(TemporalAdjusters.previousOrSame(day)), zone)
                if (candidate.isAfter(local)) boundaryOn(candidate.toLocalDate().minusWeeks(1), zone) else candidate
            }
            ResetMode.MONTHLY -> {
                val candidate = monthBoundary(date.year, date.monthValue, zone)
                if (candidate.isAfter(local)) {
                    val prev = date.minusMonths(1)
                    monthBoundary(prev.year, prev.monthValue, zone)
                } else {
                    candidate
                }
            }
        }
        return start.toInstant().toEpochMilli()
    }

    /** The first reset strictly after [now], or null when the board never resets. */
    fun nextReset(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val start = periodStart(now, zone) ?: return null
        val s = Instant.ofEpochMilli(start).atZone(zone)
        val next = when (mode) {
            ResetMode.NEVER -> return null
            ResetMode.DAILY -> boundaryOn(s.toLocalDate().plusDays(1), zone)
            ResetMode.WEEKLY -> boundaryOn(s.toLocalDate().plusWeeks(1), zone)
            ResetMode.MONTHLY -> s.toLocalDate().plusMonths(1).let { monthBoundary(it.year, it.monthValue, zone) }
        }
        return next.toInstant().toEpochMilli()
    }

    /** True when a reset happened after [then] and at or before [now]. */
    fun resetBetween(then: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val start = periodStart(now, zone) ?: return false
        return then < start
    }

    companion object {
        fun of(board: BoardEntity) = ResetSchedule(board.resetMode, board.resetMinute, board.resetWeekday, board.resetMonthDay)
    }
}
