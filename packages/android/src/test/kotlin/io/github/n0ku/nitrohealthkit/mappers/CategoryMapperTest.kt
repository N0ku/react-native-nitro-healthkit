package io.github.n0ku.nitrohealthkit.mappers

import androidx.health.connect.client.records.MenstruationFlowRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryMapperTest {

    private val start: Instant = Instant.parse("2025-01-01T22:00:00Z")
    private val end: Instant = Instant.parse("2025-01-02T06:00:00Z")
    private val md = Metadata.manualEntry()

    @Test
    fun `flatten SleepSessionRecord with stages returns one point per stage`() {
        val session = SleepSessionRecord(
            startTime = start,
            startZoneOffset = ZoneOffset.UTC,
            endTime = end,
            endZoneOffset = ZoneOffset.UTC,
            stages = listOf(
                SleepSessionRecord.Stage(
                    startTime = start,
                    endTime = start.plusSeconds(3600),
                    stage = SleepSessionRecord.STAGE_TYPE_LIGHT,
                ),
                SleepSessionRecord.Stage(
                    startTime = start.plusSeconds(3600),
                    endTime = start.plusSeconds(7200),
                    stage = SleepSessionRecord.STAGE_TYPE_DEEP,
                ),
                SleepSessionRecord.Stage(
                    startTime = start.plusSeconds(7200),
                    endTime = end,
                    stage = SleepSessionRecord.STAGE_TYPE_REM,
                ),
            ),
            title = null,
            notes = null,
            metadata = md,
        )
        val out = CategoryMapper.flatten(CategoryMapper.CT_SLEEP_ANALYSIS, listOf(session))
        assertEquals(3, out.size)
        // Encoded values follow the iOS coding (3=light, 4=deep, 5=rem) for parity with iOS module.
        assertEquals(3.0, out[0].value, 0.0)
        assertEquals(4.0, out[1].value, 0.0)
        assertEquals(5.0, out[2].value, 0.0)
    }

    @Test
    fun `flatten SleepSessionRecord without stages emits a single envelope`() {
        val session = SleepSessionRecord(
            startTime = start,
            startZoneOffset = ZoneOffset.UTC,
            endTime = end,
            endZoneOffset = ZoneOffset.UTC,
            stages = emptyList(),
            title = null,
            notes = null,
            metadata = md,
        )
        val out = CategoryMapper.flatten(CategoryMapper.CT_SLEEP_ANALYSIS, listOf(session))
        assertEquals(1, out.size)
        assertEquals(1.0, out[0].value, 0.0) // SLEEP_VALUE_ASLEEP_UNSPECIFIED
    }

    @Test
    fun `flatten MenstruationFlowRecord exposes the flow level`() {
        val record = MenstruationFlowRecord(
            time = start,
            zoneOffset = ZoneOffset.UTC,
            flow = MenstruationFlowRecord.FLOW_MEDIUM,
            metadata = md,
        )
        val out = CategoryMapper.flatten(CategoryMapper.CT_MENSTRUAL_FLOW, listOf(record))
        assertEquals(1, out.size)
        assertEquals(MenstruationFlowRecord.FLOW_MEDIUM.toDouble(), out[0].value, 0.0)
    }

    @Test
    fun `buildRecord returns SleepSessionRecord for sleep analysis`() {
        val record = CategoryMapper.buildRecord(
            iosType = CategoryMapper.CT_SLEEP_ANALYSIS,
            value = 4.0, // deep
            start = start,
            end = end,
        )
        assertNotNull(record)
        assertTrue(record is SleepSessionRecord)
    }

    @Test
    fun `buildRecord returns null for Apple-only categories`() {
        val record = CategoryMapper.buildRecord(
            iosType = "HKCategoryTypeIdentifierAppleStandHour",
            value = 1.0,
            start = start,
            end = end,
        )
        assertNull(record)
    }
}
