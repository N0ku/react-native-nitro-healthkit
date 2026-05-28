package io.github.n0ku.nitrohealthkit.util

import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeRangeHelperTest {

    private val zone: ZoneId = ZoneId.of("Europe/Paris")
    private val anchor: LocalDateTime = LocalDateTime.of(2026, 5, 13, 14, 30) // Wednesday

    @Test
    fun `today spans from midnight to end of day`() {
        val range = TimeRangeHelper.resolve("today", null, null, zone, anchor)
        val durationDays = Duration.between(range.start, range.end).toDays()
        assertEquals(0L, durationDays) // Same calendar day
        assertTrue(range.end.isAfter(range.start))
    }

    @Test
    fun `last_7_days yields a 7-day window`() {
        val range = TimeRangeHelper.resolve("last_7_days", null, null, zone, anchor)
        val days = Duration.between(range.start, range.end).toDays()
        // 6 full days + the in-day portion → between 6 and 7 inclusive depending on time of day
        assertTrue("expected ≈7 days, got $days", days in 6..7)
    }

    @Test
    fun `custom forwards the provided dates`() {
        val start = java.time.Instant.parse("2025-01-01T00:00:00Z")
        val end = java.time.Instant.parse("2025-02-01T00:00:00Z")
        val range = TimeRangeHelper.resolve("custom", start, end, zone, anchor)
        assertEquals(start, range.start)
        assertEquals(end, range.end)
    }

    @Test
    fun `this_week starts on Monday`() {
        val range = TimeRangeHelper.resolve("this_week", null, null, zone, anchor)
        val mondayStartUtc = range.start.atZone(zone)
        assertEquals(java.time.DayOfWeek.MONDAY, mondayStartUtc.dayOfWeek)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unknown range raises`() {
        TimeRangeHelper.resolve("nope", null, null, zone, anchor)
    }
}
