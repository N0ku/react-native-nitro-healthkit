package io.github.n0ku.nitrohealthkit.mappers

import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuantityMapperTest {

    private val start: Instant = Instant.parse("2025-01-01T08:00:00Z")
    private val end: Instant = Instant.parse("2025-01-01T09:00:00Z")
    private val md = Metadata.manualEntry()

    @Test
    fun `recordTypeFor returns null for Apple-only types`() {
        assertNull(QuantityMapper.recordTypeFor("HKQuantityTypeIdentifierAppleExerciseTime"))
        assertNull(QuantityMapper.recordTypeFor("HKQuantityTypeIdentifierAppleStandTime"))
    }

    @Test
    fun `recordTypeFor resolves steps and heart rate`() {
        assertEquals(StepsRecord::class, QuantityMapper.recordTypeFor(QuantityMapper.QT_STEPS))
        assertEquals(HeartRateRecord::class, QuantityMapper.recordTypeFor(QuantityMapper.QT_HEART_RATE))
    }

    @Test
    fun `flatten StepsRecord yields one sample with count`() {
        val record = StepsRecord(
            startTime = start,
            startZoneOffset = ZoneOffset.UTC,
            endTime = end,
            endZoneOffset = ZoneOffset.UTC,
            count = 1234L,
            metadata = md,
        )
        val out = QuantityMapper.flatten(QuantityMapper.QT_STEPS, listOf(record))
        assertEquals(1, out.size)
        assertEquals(1234.0, out[0].value, 0.0)
        assertEquals("count", out[0].unit)
    }

    @Test
    fun `flatten HeartRateRecord unrolls each sample`() {
        val record = HeartRateRecord(
            startTime = start,
            startZoneOffset = ZoneOffset.UTC,
            endTime = end,
            endZoneOffset = ZoneOffset.UTC,
            samples = listOf(
                HeartRateRecord.Sample(time = start, beatsPerMinute = 72L),
                HeartRateRecord.Sample(time = start.plusSeconds(60), beatsPerMinute = 78L),
                HeartRateRecord.Sample(time = start.plusSeconds(120), beatsPerMinute = 80L),
            ),
            metadata = md,
        )
        val out = QuantityMapper.flatten(QuantityMapper.QT_HEART_RATE, listOf(record))
        assertEquals(3, out.size)
        assertEquals(72.0, out[0].value, 0.0)
        assertEquals(80.0, out[2].value, 0.0)
        assertEquals("count/min", out[0].unit)
    }

    @Test
    fun `flatten DistanceRecord uses metres`() {
        val record = DistanceRecord(
            startTime = start,
            startZoneOffset = ZoneOffset.UTC,
            endTime = end,
            endZoneOffset = ZoneOffset.UTC,
            distance = Length.meters(4321.0),
            metadata = md,
        )
        val out = QuantityMapper.flatten(QuantityMapper.QT_DISTANCE_WALKING_RUNNING, listOf(record))
        assertEquals(4321.0, out[0].value, 0.0)
        assertEquals("m", out[0].unit)
    }

    @Test
    fun `flatten ActiveCaloriesBurnedRecord uses kilocalories`() {
        val record = ActiveCaloriesBurnedRecord(
            startTime = start,
            startZoneOffset = ZoneOffset.UTC,
            endTime = end,
            endZoneOffset = ZoneOffset.UTC,
            energy = Energy.kilocalories(123.4),
            metadata = md,
        )
        val out = QuantityMapper.flatten(QuantityMapper.QT_ACTIVE_ENERGY_BURNED, listOf(record))
        assertEquals(123.4, out[0].value, 1e-6)
        assertEquals("kcal", out[0].unit)
    }

    @Test
    fun `flatten unknown type returns empty list`() {
        val record = WeightRecord(
            time = start,
            zoneOffset = ZoneOffset.UTC,
            weight = Mass.kilograms(80.0),
            metadata = md,
        )
        // The type asked for is Apple-only, so even with a record we should get nothing.
        val out = QuantityMapper.flatten("HKQuantityTypeIdentifierAppleStandTime", listOf(record))
        assertTrue(out.isEmpty())
    }

    @Test
    fun `buildRecord returns null for unmapped types`() {
        val record = QuantityMapper.buildRecord(
            iosType = "HKQuantityTypeIdentifierAppleExerciseTime",
            value = 30.0,
            unit = "min",
            start = start,
            end = end,
        )
        assertNull(record)
    }

    @Test
    fun `buildRecord builds StepsRecord for STEPS`() {
        val record = QuantityMapper.buildRecord(
            iosType = QuantityMapper.QT_STEPS,
            value = 250.0,
            unit = "count",
            start = start,
            end = end,
        )
        assertNotNull(record)
        assertTrue(record is StepsRecord)
        assertEquals(250L, (record as StepsRecord).count)
    }

    @Test
    fun `buildRecord clamps inverted dates`() {
        val record = QuantityMapper.buildRecord(
            iosType = QuantityMapper.QT_FLIGHTS_CLIMBED,
            value = 3.0,
            unit = "count",
            start = end,
            end = start, // intentionally inverted
        )
        assertNotNull(record)
        val flights = record as FloorsClimbedRecord
        assertTrue(flights.endTime.isAfter(flights.startTime))
    }

    @Test
    fun `coerceAggregateToDouble handles primitives and Health Connect units`() {
        assertEquals(42.0, QuantityMapper.coerceAggregateToDouble(QuantityMapper.QT_STEPS, 42L), 0.0)
        assertEquals(
            500.0,
            QuantityMapper.coerceAggregateToDouble(
                QuantityMapper.QT_DISTANCE_WALKING_RUNNING,
                Length.meters(500.0),
            ),
            0.0,
        )
        assertEquals(
            123.0,
            QuantityMapper.coerceAggregateToDouble(
                QuantityMapper.QT_ACTIVE_ENERGY_BURNED,
                Energy.kilocalories(123.0),
            ),
            0.0,
        )
        // Unmapped type returns 0.0 with a warning logged.
        assertEquals(0.0, QuantityMapper.coerceAggregateToDouble(QuantityMapper.QT_STEPS, Any()), 0.0)
    }
}
