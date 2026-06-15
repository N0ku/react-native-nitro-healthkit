package io.github.n0ku.nitrohealthkit.mappers

import android.util.Log
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.CyclingPedalingCadenceRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.BloodGlucose
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Power
import androidx.health.connect.client.units.Pressure
import androidx.health.connect.client.units.Temperature
import androidx.health.connect.client.units.Velocity
import androidx.health.connect.client.units.Volume
import com.margelo.nitro.healthkit.QuantityDataPoint
import java.time.Instant
import java.time.ZoneOffset
import kotlin.reflect.KClass

/**
 * Maps the iOS-flavoured [com.margelo.nitro.healthkit.HybridHealthKitSpec] string identifiers
 * (e.g. `HKQuantityTypeIdentifierStepCount`) onto Android Health Connect [Record] types.
 *
 * The contract maintained with the JS side is intentionally loose:
 *  - Unsupported types (Apple-only or types HC simply does not model) read as an empty list
 *    and write as `false`. This is documented behaviour, surfaced via [Log.w].
 *  - All values are coerced into the canonical unit used by HealthKit so that the consuming
 *    code in `fetch-health-data.ios.ts` and `fetch-health-data.android.ts` can share logic.
 */
object QuantityMapper {

    private const val TAG = "QuantityMapper"

    /**
     * A flattened sample produced after reading a Health Connect [Record] and unfolding any
     * inner sample arrays (e.g. [HeartRateRecord.samples] → one [QuantityDataPoint] per beat).
     */
    private data class Sample(
        val value: Double,
        val unit: String,
        val start: Instant,
        val end: Instant,
        val metadata: Map<String, String>? = null,
    )

    /**
     * Resolve the Health Connect record class and unit string for an iOS identifier.
     * Returns null when the type is not supported on Android.
     */
    fun recordTypeFor(iosType: String): KClass<out Record>? = when (iosType) {
        QT_STEPS -> StepsRecord::class
        QT_HEART_RATE -> HeartRateRecord::class
        QT_RESTING_HEART_RATE -> RestingHeartRateRecord::class
        QT_HEART_RATE_VARIABILITY_SDNN -> HeartRateVariabilityRmssdRecord::class
        QT_ACTIVE_ENERGY_BURNED -> ActiveCaloriesBurnedRecord::class
        QT_BASAL_ENERGY_BURNED -> BasalMetabolicRateRecord::class
        QT_DIETARY_ENERGY_CONSUMED -> TotalCaloriesBurnedRecord::class
        QT_DISTANCE_WALKING_RUNNING,
        QT_DISTANCE_CYCLING,
        QT_DISTANCE_SWIMMING,
        QT_DISTANCE_WHEELCHAIR -> DistanceRecord::class
        QT_FLIGHTS_CLIMBED -> FloorsClimbedRecord::class
        QT_BLOOD_GLUCOSE -> BloodGlucoseRecord::class
        QT_BLOOD_PRESSURE_SYSTOLIC,
        QT_BLOOD_PRESSURE_DIASTOLIC -> BloodPressureRecord::class
        QT_BLOOD_OXYGEN_SATURATION -> OxygenSaturationRecord::class
        QT_BODY_TEMPERATURE,
        QT_BASAL_BODY_TEMPERATURE -> BodyTemperatureRecord::class
        QT_BODY_MASS -> WeightRecord::class
        QT_BODY_FAT_PERCENTAGE -> BodyFatRecord::class
        QT_LEAN_BODY_MASS -> LeanBodyMassRecord::class
        QT_HEIGHT -> HeightRecord::class
        QT_RESPIRATORY_RATE -> RespiratoryRateRecord::class
        QT_VO2_MAX -> Vo2MaxRecord::class
        QT_WALKING_SPEED,
        QT_RUNNING_SPEED -> SpeedRecord::class
        QT_RUNNING_POWER,
        QT_CYCLING_POWER -> PowerRecord::class
        QT_CYCLING_CADENCE -> CyclingPedalingCadenceRecord::class
        QT_DIETARY_WATER -> HydrationRecord::class
        QT_BODY_WATER_MASS -> BodyWaterMassRecord::class
        QT_BONE_MASS -> BoneMassRecord::class
        else -> null // Apple-only or unmapped — handled at call site.
    }

    /**
     * Permission strings required to read a given type. May return null when the type is
     * unsupported, in which case the caller should skip permission requests for it.
     */
    fun readPermissionFor(iosType: String): String? =
        recordTypeFor(iosType)?.let { HealthPermission.getReadPermission(it) }

    fun writePermissionFor(iosType: String): String? =
        recordTypeFor(iosType)?.let { HealthPermission.getWritePermission(it) }

    /**
     * Canonical unit string for the iOS type, used by aggregate paths
     * that build [QuantityDataPoint]s from raw HC numbers instead of from
     * full records (where the unit is read off the record itself).
     *
     * Kept aligned with `samplesFor` so daily-aggregate and raw-sample
     * callers ship identical units to JS.
     */
    fun unitFor(iosType: String): String = when (iosType) {
        QT_STEPS, QT_FLIGHTS_CLIMBED -> "count"
        QT_DISTANCE_WALKING_RUNNING,
        QT_DISTANCE_CYCLING,
        QT_DISTANCE_SWIMMING,
        QT_DISTANCE_WHEELCHAIR,
        QT_ELEVATION_GAINED -> "m"
        QT_ACTIVE_ENERGY_BURNED,
        QT_DIETARY_ENERGY_CONSUMED,
        QT_BASAL_ENERGY_BURNED -> "kcal"
        QT_DIETARY_WATER -> "L"
        else -> ""
    }

    /**
     * Flatten a list of Health Connect records of arbitrary type into [QuantityDataPoint]
     * objects suitable to ship across the Nitro bridge.
     */
    fun flatten(iosType: String, records: List<Record>): List<QuantityDataPoint> =
        records.flatMap { record -> samplesFor(iosType, record) }
            .map { sample ->
                QuantityDataPoint(
                    value = sample.value,
                    unit = sample.unit,
                    startDate = sample.start,
                    endDate = sample.end,
                    metadata = sample.metadata,
                )
            }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun samplesFor(iosType: String, record: Record): List<Sample> = when (record) {
        is StepsRecord -> listOf(
            Sample(record.count.toDouble(), "count", record.startTime, record.endTime),
        )
        is FloorsClimbedRecord -> listOf(
            Sample(record.floors, "count", record.startTime, record.endTime),
        )
        is DistanceRecord -> listOf(
            Sample(record.distance.inMeters, "m", record.startTime, record.endTime),
        )
        is ActiveCaloriesBurnedRecord -> listOf(
            Sample(record.energy.inKilocalories, "kcal", record.startTime, record.endTime),
        )
        is TotalCaloriesBurnedRecord -> listOf(
            Sample(record.energy.inKilocalories, "kcal", record.startTime, record.endTime),
        )
        is BasalMetabolicRateRecord -> listOf(
            Sample(
                value = record.basalMetabolicRate.inKilocaloriesPerDay,
                unit = "kcal/day",
                start = record.time,
                end = record.time,
            ),
        )
        is HeartRateRecord -> record.samples.map { sample ->
            Sample(
                value = sample.beatsPerMinute.toDouble(),
                unit = "count/min",
                start = sample.time,
                end = sample.time,
            )
        }
        is RestingHeartRateRecord -> listOf(
            Sample(record.beatsPerMinute.toDouble(), "count/min", record.time, record.time),
        )
        is HeartRateVariabilityRmssdRecord -> listOf(
            Sample(record.heartRateVariabilityMillis, "ms", record.time, record.time),
        )
        is BodyTemperatureRecord -> listOf(
            Sample(record.temperature.inCelsius, "degC", record.time, record.time),
        )
        is BloodGlucoseRecord -> listOf(
            Sample(record.level.inMilligramsPerDeciliter, "mg/dL", record.time, record.time),
        )
        is BloodPressureRecord -> when (iosType) {
            QT_BLOOD_PRESSURE_SYSTOLIC -> listOf(
                Sample(record.systolic.inMillimetersOfMercury, "mmHg", record.time, record.time),
            )
            QT_BLOOD_PRESSURE_DIASTOLIC -> listOf(
                Sample(record.diastolic.inMillimetersOfMercury, "mmHg", record.time, record.time),
            )
            else -> emptyList()
        }
        is OxygenSaturationRecord -> listOf(
            Sample(record.percentage.value, "%", record.time, record.time),
        )
        is RespiratoryRateRecord -> listOf(
            Sample(record.rate, "count/min", record.time, record.time),
        )
        is WeightRecord -> listOf(
            Sample(record.weight.inKilograms, "kg", record.time, record.time),
        )
        is BodyFatRecord -> listOf(
            Sample(record.percentage.value, "%", record.time, record.time),
        )
        is LeanBodyMassRecord -> listOf(
            Sample(record.mass.inKilograms, "kg", record.time, record.time),
        )
        is HeightRecord -> listOf(
            Sample(record.height.inMeters, "m", record.time, record.time),
        )
        is Vo2MaxRecord -> listOf(
            Sample(
                value = record.vo2MillilitersPerMinuteKilogram,
                unit = "ml/kg*min",
                start = record.time,
                end = record.time,
            ),
        )
        is SpeedRecord -> record.samples.map { sample ->
            Sample(
                value = sample.speed.inMetersPerSecond,
                unit = "m/s",
                start = sample.time,
                end = sample.time,
            )
        }
        is PowerRecord -> record.samples.map { sample ->
            Sample(
                value = sample.power.inWatts,
                unit = "W",
                start = sample.time,
                end = sample.time,
            )
        }
        is CyclingPedalingCadenceRecord -> record.samples.map { sample ->
            Sample(
                value = sample.revolutionsPerMinute,
                unit = "rpm",
                start = sample.time,
                end = sample.time,
            )
        }
        is StepsCadenceRecord -> record.samples.map { sample ->
            Sample(
                value = sample.rate,
                unit = "step/min",
                start = sample.time,
                end = sample.time,
            )
        }
        is HydrationRecord -> listOf(
            Sample(record.volume.inLiters, "L", record.startTime, record.endTime),
        )
        is BodyWaterMassRecord -> listOf(
            Sample(record.mass.inKilograms, "kg", record.time, record.time),
        )
        is BoneMassRecord -> listOf(
            Sample(record.mass.inKilograms, "kg", record.time, record.time),
        )
        is ElevationGainedRecord -> listOf(
            Sample(record.elevation.inMeters, "m", record.startTime, record.endTime),
        )
        else -> emptyList()
    }

    /**
     * Aggregate metrics for Health Connect's [androidx.health.connect.client.HealthConnectClient.aggregate].
     * Only types whose iOS counterpart is naturally cumulative or whose HC record exposes an aggregate
     * metric are listed. Returning null means "fallback to summing the flattened samples client-side".
     */
    @Suppress("CyclomaticComplexMethod")
    fun sumAggregateFor(iosType: String): AggregateMetric<*>? = when (iosType) {
        QT_STEPS -> StepsRecord.COUNT_TOTAL
        QT_DISTANCE_WALKING_RUNNING,
        QT_DISTANCE_CYCLING,
        QT_DISTANCE_SWIMMING,
        QT_DISTANCE_WHEELCHAIR -> DistanceRecord.DISTANCE_TOTAL
        QT_FLIGHTS_CLIMBED -> FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL
        QT_ACTIVE_ENERGY_BURNED -> ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL
        QT_DIETARY_ENERGY_CONSUMED -> TotalCaloriesBurnedRecord.ENERGY_TOTAL
        QT_ELEVATION_GAINED -> ElevationGainedRecord.ELEVATION_GAINED_TOTAL
        QT_DIETARY_WATER -> HydrationRecord.VOLUME_TOTAL
        else -> null
    }

    /**
     * Convert a Health Connect aggregate result into the unit expected by [QuantityDataPoint].
     */
    fun coerceAggregateToDouble(iosType: String, raw: Any?): Double {
        if (raw == null) return 0.0
        return when (raw) {
            is Long -> raw.toDouble()
            is Int -> raw.toDouble()
            is Double -> raw
            is Float -> raw.toDouble()
            is Length -> raw.inMeters
            is Energy -> raw.inKilocalories
            is Mass -> raw.inKilograms
            is Volume -> raw.inLiters
            is Velocity -> raw.inMetersPerSecond
            is Power -> raw.inWatts
            is Pressure -> raw.inMillimetersOfMercury
            is Temperature -> raw.inCelsius
            is Percentage -> raw.value
            is BloodGlucose -> raw.inMilligramsPerDeciliter
            else -> {
                Log.w(TAG, "Unable to coerce aggregate of type ${raw.javaClass} for $iosType; returning 0.0")
                0.0
            }
        }
    }

    /**
     * Build a Health Connect [Record] for the given iOS type, ready to be inserted.
     * Returns null when the type is not writable on Android (Apple-only, derived, etc.).
     */
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    fun buildRecord(
        iosType: String,
        value: Double,
        unit: String,
        start: Instant,
        end: Instant,
    ): Record? {
        val md = Metadata.manualEntry()
        // Health Connect requires a positive duration on interval records; clamp degenerate cases.
        val safeEnd = if (!end.isAfter(start)) start.plusSeconds(1) else end
        return when (iosType) {
            QT_STEPS -> StepsRecord(
                startTime = start,
                startZoneOffset = ZoneOffset.UTC,
                endTime = safeEnd,
                endZoneOffset = ZoneOffset.UTC,
                count = value.toLong().coerceAtLeast(0L),
                metadata = md,
            )
            QT_FLIGHTS_CLIMBED -> FloorsClimbedRecord(
                startTime = start,
                startZoneOffset = ZoneOffset.UTC,
                endTime = safeEnd,
                endZoneOffset = ZoneOffset.UTC,
                floors = value,
                metadata = md,
            )
            QT_DISTANCE_WALKING_RUNNING,
            QT_DISTANCE_CYCLING,
            QT_DISTANCE_SWIMMING,
            QT_DISTANCE_WHEELCHAIR -> DistanceRecord(
                startTime = start,
                startZoneOffset = ZoneOffset.UTC,
                endTime = safeEnd,
                endZoneOffset = ZoneOffset.UTC,
                distance = Length.meters(value),
                metadata = md,
            )
            QT_ACTIVE_ENERGY_BURNED -> ActiveCaloriesBurnedRecord(
                startTime = start,
                startZoneOffset = ZoneOffset.UTC,
                endTime = safeEnd,
                endZoneOffset = ZoneOffset.UTC,
                energy = Energy.kilocalories(value),
                metadata = md,
            )
            QT_DIETARY_ENERGY_CONSUMED -> TotalCaloriesBurnedRecord(
                startTime = start,
                startZoneOffset = ZoneOffset.UTC,
                endTime = safeEnd,
                endZoneOffset = ZoneOffset.UTC,
                energy = Energy.kilocalories(value),
                metadata = md,
            )
            QT_HEART_RATE -> HeartRateRecord(
                startTime = start,
                startZoneOffset = ZoneOffset.UTC,
                endTime = safeEnd,
                endZoneOffset = ZoneOffset.UTC,
                samples = listOf(
                    HeartRateRecord.Sample(time = start, beatsPerMinute = value.toLong()),
                ),
                metadata = md,
            )
            QT_RESTING_HEART_RATE -> RestingHeartRateRecord(
                time = start,
                zoneOffset = ZoneOffset.UTC,
                beatsPerMinute = value.toLong(),
                metadata = md,
            )
            QT_BODY_MASS -> WeightRecord(
                time = start,
                zoneOffset = ZoneOffset.UTC,
                weight = Mass.kilograms(value),
                metadata = md,
            )
            QT_HEIGHT -> HeightRecord(
                time = start,
                zoneOffset = ZoneOffset.UTC,
                height = Length.meters(value),
                metadata = md,
            )
            QT_BODY_FAT_PERCENTAGE -> BodyFatRecord(
                time = start,
                zoneOffset = ZoneOffset.UTC,
                percentage = Percentage(value),
                metadata = md,
            )
            QT_BLOOD_OXYGEN_SATURATION -> OxygenSaturationRecord(
                time = start,
                zoneOffset = ZoneOffset.UTC,
                percentage = Percentage(value),
                metadata = md,
            )
            QT_BODY_TEMPERATURE,
            QT_BASAL_BODY_TEMPERATURE -> BodyTemperatureRecord(
                time = start,
                zoneOffset = ZoneOffset.UTC,
                temperature = Temperature.celsius(value),
                metadata = md,
            )
            QT_RESPIRATORY_RATE -> RespiratoryRateRecord(
                time = start,
                zoneOffset = ZoneOffset.UTC,
                rate = value,
                metadata = md,
            )
            QT_DIETARY_WATER -> HydrationRecord(
                startTime = start,
                startZoneOffset = ZoneOffset.UTC,
                endTime = safeEnd,
                endZoneOffset = ZoneOffset.UTC,
                volume = Volume.liters(value),
                metadata = md,
            )
            else -> {
                Log.w(
                    TAG,
                    "writeQuantityData: type \"$iosType\" has no Health Connect equivalent on this device — skipping write",
                )
                null
            }
        }
    }

    // Subset of the HKQuantityTypeIdentifier* constants we actually exercise. They line up
    // with the enum values in `src/specs/HealthKit.nitro.ts` (HealthKitQuantityType).
    const val QT_STEPS = "HKQuantityTypeIdentifierStepCount"
    const val QT_HEART_RATE = "HKQuantityTypeIdentifierHeartRate"
    const val QT_RESTING_HEART_RATE = "HKQuantityTypeIdentifierRestingHeartRate"
    const val QT_HEART_RATE_VARIABILITY_SDNN = "HKQuantityTypeIdentifierHeartRateVariabilitySDNN"
    const val QT_ACTIVE_ENERGY_BURNED = "HKQuantityTypeIdentifierActiveEnergyBurned"
    const val QT_BASAL_ENERGY_BURNED = "HKQuantityTypeIdentifierBasalEnergyBurned"
    const val QT_DIETARY_ENERGY_CONSUMED = "HKQuantityTypeIdentifierDietaryEnergyConsumed"
    const val QT_DISTANCE_WALKING_RUNNING = "HKQuantityTypeIdentifierDistanceWalkingRunning"
    const val QT_DISTANCE_CYCLING = "HKQuantityTypeIdentifierDistanceCycling"
    const val QT_DISTANCE_SWIMMING = "HKQuantityTypeIdentifierDistanceSwimming"
    const val QT_DISTANCE_WHEELCHAIR = "HKQuantityTypeIdentifierDistanceWheelchair"
    const val QT_FLIGHTS_CLIMBED = "HKQuantityTypeIdentifierFlightsClimbed"
    const val QT_BLOOD_GLUCOSE = "HKQuantityTypeIdentifierBloodGlucose"
    const val QT_BLOOD_PRESSURE_SYSTOLIC = "HKQuantityTypeIdentifierBloodPressureSystolic"
    const val QT_BLOOD_PRESSURE_DIASTOLIC = "HKQuantityTypeIdentifierBloodPressureDiastolic"
    const val QT_BLOOD_OXYGEN_SATURATION = "HKQuantityTypeIdentifierOxygenSaturation"
    const val QT_BODY_TEMPERATURE = "HKQuantityTypeIdentifierBodyTemperature"
    const val QT_BASAL_BODY_TEMPERATURE = "HKQuantityTypeIdentifierBasalBodyTemperature"
    const val QT_BODY_MASS = "HKQuantityTypeIdentifierBodyMass"
    const val QT_BODY_FAT_PERCENTAGE = "HKQuantityTypeIdentifierBodyFatPercentage"
    const val QT_LEAN_BODY_MASS = "HKQuantityTypeIdentifierLeanBodyMass"
    const val QT_HEIGHT = "HKQuantityTypeIdentifierHeight"
    const val QT_RESPIRATORY_RATE = "HKQuantityTypeIdentifierRespiratoryRate"
    const val QT_VO2_MAX = "HKQuantityTypeIdentifierVO2Max"
    const val QT_WALKING_SPEED = "HKQuantityTypeIdentifierWalkingSpeed"
    const val QT_RUNNING_SPEED = "HKQuantityTypeIdentifierRunningSpeed"
    const val QT_RUNNING_POWER = "HKQuantityTypeIdentifierRunningPower"
    const val QT_CYCLING_POWER = "HKQuantityTypeIdentifierCyclingPower"
    const val QT_CYCLING_CADENCE = "HKQuantityTypeIdentifierCyclingCadence"
    const val QT_DIETARY_WATER = "HKQuantityTypeIdentifierDietaryWater"
    const val QT_BODY_WATER_MASS = "HKQuantityTypeIdentifierBodyWaterMass"
    const val QT_BONE_MASS = "HKQuantityTypeIdentifierBoneMass"
    const val QT_ELEVATION_GAINED = "HKQuantityTypeIdentifierElevationGained"
}
