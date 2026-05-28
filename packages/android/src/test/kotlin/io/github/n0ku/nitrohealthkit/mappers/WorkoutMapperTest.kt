package io.github.n0ku.nitrohealthkit.mappers

import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutMapperTest {

    private val start: Instant = Instant.parse("2025-01-01T10:00:00Z")
    private val end: Instant = Instant.parse("2025-01-01T10:30:00Z")
    private val md = Metadata.manualEntry()

    @Test
    fun `running maps to HKWorkoutActivityType raw value 37`() {
        val record = ExerciseSessionRecord(
            startTime = start,
            startZoneOffset = ZoneOffset.UTC,
            endTime = end,
            endZoneOffset = ZoneOffset.UTC,
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
            title = "Morning run",
            notes = null,
            metadata = md,
        )
        val workout = WorkoutMapper.map(record)
        assertEquals(37.0, workout.workoutActivityType, 0.0)
        assertEquals("Running", workout.workoutActivityName)
        assertEquals(1800.0, workout.duration, 0.0)
    }

    @Test
    fun `unknown exercise falls back to Other`() {
        val record = ExerciseSessionRecord(
            startTime = start,
            startZoneOffset = ZoneOffset.UTC,
            endTime = end,
            endZoneOffset = ZoneOffset.UTC,
            exerciseType = Int.MAX_VALUE,
            title = null,
            notes = null,
            metadata = md,
        )
        val workout = WorkoutMapper.map(record)
        assertEquals(3000.0, workout.workoutActivityType, 0.0)
        assertEquals("Other", workout.workoutActivityName)
    }
}
