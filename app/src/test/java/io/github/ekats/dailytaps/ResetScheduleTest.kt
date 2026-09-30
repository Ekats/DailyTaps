package io.github.ekats.dailytaps

import io.github.ekats.dailytaps.data.ResetMode
import io.github.ekats.dailytaps.domain.ResetSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ResetScheduleTest {
    private val zone = ZoneId.of("Europe/Tallinn")

    private fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `never has no periods`() {
        val never = ResetSchedule(ResetMode.NEVER)
        assertNull(never.periodStart(at("2026-09-30T12:00"), zone))
        assertNull(never.nextReset(at("2026-09-30T12:00"), zone))
        assertFalse(never.resetBetween(0, at("2026-09-30T12:00"), zone))
    }

    @Test
    fun `daily at a set time`() {
        val daily = ResetSchedule(ResetMode.DAILY, minuteOfDay = 4 * 60)
        // Before 04:00 the period still started yesterday at 04:00.
        assertEquals(at("2026-09-29T04:00"), daily.periodStart(at("2026-09-30T03:59"), zone))
        assertEquals(at("2026-09-30T04:00"), daily.periodStart(at("2026-09-30T04:00"), zone))
        assertEquals(at("2026-10-01T04:00"), daily.nextReset(at("2026-09-30T12:00"), zone))
        assertTrue(daily.resetBetween(at("2026-09-30T03:00"), at("2026-09-30T05:00"), zone))
        assertFalse(daily.resetBetween(at("2026-09-30T05:00"), at("2026-10-01T03:00"), zone))
    }

    @Test
    fun `weekly on Monday at 00 01`() {
        val weekly = ResetSchedule(ResetMode.WEEKLY, minuteOfDay = 1, weekday = 1)
        // 2026-09-28 is a Monday.
        assertEquals(at("2026-09-28T00:01"), weekly.periodStart(at("2026-09-30T12:00"), zone))
        assertEquals(at("2026-09-21T00:01"), weekly.periodStart(at("2026-09-28T00:00"), zone))
        assertEquals(at("2026-10-05T00:01"), weekly.nextReset(at("2026-09-30T12:00"), zone))
        // A tap on Sunday night is cleared by Monday morning, but not by Sunday midnight.
        assertTrue(weekly.resetBetween(at("2026-10-04T23:00"), at("2026-10-05T08:00"), zone))
        assertFalse(weekly.resetBetween(at("2026-10-04T23:00"), at("2026-10-05T00:00"), zone))
    }

    @Test
    fun `monthly clamps to short months`() {
        val monthly = ResetSchedule(ResetMode.MONTHLY, minuteOfDay = 0, monthDay = 31)
        assertEquals(at("2026-09-30T00:00"), monthly.periodStart(at("2026-09-30T12:00"), zone))
        assertEquals(at("2026-10-31T00:00"), monthly.nextReset(at("2026-09-30T12:00"), zone))
        assertEquals(at("2027-02-28T00:00"), monthly.nextReset(at("2027-01-31T12:00"), zone))
    }

    @Test
    fun `daylight saving change keeps the local time`() {
        // Tallinn falls back on 2026-10-25; the reset stays at local midnight.
        val daily = ResetSchedule(ResetMode.DAILY)
        assertEquals(at("2026-10-26T00:00"), daily.nextReset(at("2026-10-25T12:00"), zone))
    }
}
