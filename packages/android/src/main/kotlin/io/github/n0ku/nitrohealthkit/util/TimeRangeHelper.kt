package io.github.n0ku.nitrohealthkit.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Mirrors `ios/TimeRangeHelper.swift`: given a logical range identifier
 * (`today`, `this_week`, `last_30_days`, `custom`, ...), produces a concrete
 * [startInstant, endInstant) pair anchored on the device's default timezone.
 *
 * The values are kept identical to the [com.margelo.nitro.healthkit.HybridHealthKitSpec]
 * contract on the TS side (see [src/specs/HealthKit.nitro.ts]: `enum TimeRange`).
 */
object TimeRangeHelper {

    data class Range(val start: Instant, val end: Instant)

    fun resolve(
        rangeKey: String,
        customStart: Instant?,
        customEnd: Instant?,
        zone: ZoneId = ZoneId.systemDefault(),
        now: LocalDateTime = LocalDateTime.now(zone),
    ): Range {
        val today: LocalDate = now.toLocalDate()
        return when (rangeKey.lowercase()) {
            "today" -> dayRange(today, zone)
            "yesterday" -> dayRange(today.minusDays(1), zone)
            "this_week" -> weekRange(today, zone)
            "last_week" -> weekRange(today.minusWeeks(1), zone)
            "this_month" -> monthRange(YearMonth.from(today), zone)
            "last_month" -> monthRange(YearMonth.from(today).minusMonths(1), zone)
            "this_year" -> yearRange(today.year, zone)
            "last_year" -> yearRange(today.year - 1, zone)
            "last_7_days" -> rollingDays(today, 7, zone)
            "last_30_days" -> rollingDays(today, 30, zone)
            "last_90_days" -> rollingDays(today, 90, zone)
            "custom" -> Range(
                start = customStart ?: dayRange(today, zone).start,
                end = customEnd ?: dayRange(today, zone).end,
            )
            else -> throw IllegalArgumentException("Unknown time range: $rangeKey")
        }
    }

    private fun dayRange(day: LocalDate, zone: ZoneId): Range = Range(
        start = day.atStartOfDay(zone).toInstant(),
        end = day.atTime(LocalTime.MAX).atZone(zone).toInstant(),
    )

    private fun weekRange(reference: LocalDate, zone: ZoneId): Range {
        val monday = reference.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val sunday = reference.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        return Range(
            start = monday.atStartOfDay(zone).toInstant(),
            end = sunday.atTime(LocalTime.MAX).atZone(zone).toInstant(),
        )
    }

    private fun monthRange(month: YearMonth, zone: ZoneId): Range = Range(
        start = month.atDay(1).atStartOfDay(zone).toInstant(),
        end = month.atEndOfMonth().atTime(LocalTime.MAX).atZone(zone).toInstant(),
    )

    private fun yearRange(year: Int, zone: ZoneId): Range = Range(
        start = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant(),
        end = LocalDate.of(year, 12, 31).atTime(LocalTime.MAX).atZone(zone).toInstant(),
    )

    private fun rollingDays(today: LocalDate, days: Long, zone: ZoneId): Range {
        val start = today.minusDays(days - 1).atStartOfDay(zone).toInstant()
        val end = today.atTime(LocalTime.MAX).atZone(zone).toInstant()
        return Range(start = start, end = end)
    }
}
