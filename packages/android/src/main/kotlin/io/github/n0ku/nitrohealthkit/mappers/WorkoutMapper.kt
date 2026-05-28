package io.github.n0ku.nitrohealthkit.mappers

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.margelo.nitro.packages.WorkoutDataPoint
import java.time.Duration

/**
 * Translates Health Connect [ExerciseSessionRecord] entries into the
 * [WorkoutDataPoint] shape expected by `fetch-health-data.ios.ts`.
 *
 * The `workoutActivityType` numeric IDs are kept aligned with `HKWorkoutActivityType`'s
 * raw values (the iOS module's `getWorkouts` ships those directly) so app code keeps
 * working without per-platform branching.
 */
object WorkoutMapper {

    fun map(record: ExerciseSessionRecord): WorkoutDataPoint {
        val type = record.exerciseType
        val activityName = activityName(type)
        val duration = Duration.between(record.startTime, record.endTime).seconds.toDouble()
        return WorkoutDataPoint(
            workoutActivityType = activityName.iosRawValue.toDouble(),
            workoutActivityName = activityName.displayName,
            duration = duration,
            totalEnergyBurned = null,
            totalDistance = null,
            startDate = record.startTime,
            endDate = record.endTime,
            metadata = mapOf(
                "title" to (record.title.orEmpty()),
                "notes" to (record.notes.orEmpty()),
                "exerciseType" to type.toString(),
                "sourceApp" to (record.metadata.dataOrigin.packageName),
                "location" to "outdoor",
            ),
        )
    }

    private data class Activity(val iosRawValue: Int, val displayName: String)

    /**
     * Mapping table between Health Connect's [ExerciseSessionRecord] exercise IDs and
     * the [HealthKit `HKWorkoutActivityType`](https://developer.apple.com/documentation/healthkit/hkworkoutactivitytype)
     * raw values used on iOS. Only the most common activity types are mapped;
     * everything else falls back to `Other` (raw value 3000).
     */
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun activityName(exerciseType: Int): Activity = when (exerciseType) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> Activity(37, "Running")
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> Activity(37, "Running")
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> Activity(52, "Walking")
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> Activity(13, "Cycling")
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> Activity(46, "Swimming")
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> Activity(57, "Yoga")
        ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> Activity(34, "Pilates")
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> Activity(50, "Strength Training")
        ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> Activity(50, "Weightlifting")
        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> Activity(63, "HIIT")
        ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> Activity(14, "Elliptical")
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING,
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> Activity(35, "Rowing")
        ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING,
        ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE -> Activity(58, "Stair Climbing")
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> Activity(22, "Hiking")
        ExerciseSessionRecord.EXERCISE_TYPE_SOCCER -> Activity(45, "Soccer")
        ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL -> Activity(8, "Basketball")
        ExerciseSessionRecord.EXERCISE_TYPE_TENNIS -> Activity(54, "Tennis")
        ExerciseSessionRecord.EXERCISE_TYPE_GOLF -> Activity(20, "Golf")
        ExerciseSessionRecord.EXERCISE_TYPE_BASEBALL -> Activity(7, "Baseball")
        ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AMERICAN -> Activity(3, "American Football")
        ExerciseSessionRecord.EXERCISE_TYPE_DANCING -> Activity(12, "Dance")
        ExerciseSessionRecord.EXERCISE_TYPE_BOXING -> Activity(11, "Boxing")
        ExerciseSessionRecord.EXERCISE_TYPE_ROCK_CLIMBING -> Activity(10, "Climbing")
        ExerciseSessionRecord.EXERCISE_TYPE_SKIING -> Activity(43, "Skiing")
        ExerciseSessionRecord.EXERCISE_TYPE_SNOWBOARDING -> Activity(44, "Snowboarding")
        ExerciseSessionRecord.EXERCISE_TYPE_MARTIAL_ARTS -> Activity(28, "Martial Arts")
        ExerciseSessionRecord.EXERCISE_TYPE_VOLLEYBALL -> Activity(56, "Volleyball")
        ExerciseSessionRecord.EXERCISE_TYPE_HANDBALL -> Activity(21, "Handball")
        ExerciseSessionRecord.EXERCISE_TYPE_ICE_HOCKEY -> Activity(23, "Hockey")
        ExerciseSessionRecord.EXERCISE_TYPE_RUGBY -> Activity(38, "Rugby")
        ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT -> Activity(3000, "Other")
        else -> Activity(3000, "Other")
    }
}
