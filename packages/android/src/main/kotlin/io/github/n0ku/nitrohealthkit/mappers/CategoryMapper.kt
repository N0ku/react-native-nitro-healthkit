package io.github.n0ku.nitrohealthkit.mappers

import android.util.Log
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.CervicalMucusRecord
import androidx.health.connect.client.records.IntermenstrualBleedingRecord
import androidx.health.connect.client.records.MenstruationFlowRecord
import androidx.health.connect.client.records.OvulationTestRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SexualActivityRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import com.margelo.nitro.packages.CategoryDataPoint
import java.time.Instant
import java.time.ZoneOffset
import kotlin.reflect.KClass

/**
 * Maps the iOS `HKCategoryTypeIdentifier*` namespace onto Health Connect category-like records.
 *
 * Particular care is needed for sleep: `HKCategoryTypeIdentifierSleepAnalysis` is a flat
 * stream of stage samples on iOS, whereas Health Connect groups them inside [SleepSessionRecord].
 * We flatten the stages back into one [CategoryDataPoint] per stage to preserve the
 * shape `fetch-health-data.android.ts` and `fetch-health-data.ios.ts` consume today.
 */
object CategoryMapper {

    private const val TAG = "CategoryMapper"

    fun recordTypeFor(iosType: String): KClass<out Record>? = when (iosType) {
        CT_SLEEP_ANALYSIS -> SleepSessionRecord::class
        CT_MENSTRUAL_FLOW -> MenstruationFlowRecord::class
        CT_INTERMENSTRUAL_BLEEDING -> IntermenstrualBleedingRecord::class
        CT_OVULATION_TEST_RESULT -> OvulationTestRecord::class
        CT_CERVICAL_MUCUS_QUALITY -> CervicalMucusRecord::class
        CT_SEXUAL_ACTIVITY -> SexualActivityRecord::class
        else -> null
    }

    fun readPermissionFor(iosType: String): String? =
        recordTypeFor(iosType)?.let { HealthPermission.getReadPermission(it) }

    fun writePermissionFor(iosType: String): String? =
        recordTypeFor(iosType)?.let { HealthPermission.getWritePermission(it) }

    fun flatten(iosType: String, records: List<Record>): List<CategoryDataPoint> =
        records.flatMap { record -> samplesFor(iosType, record) }

    @Suppress("CyclomaticComplexMethod")
    private fun samplesFor(iosType: String, record: Record): List<CategoryDataPoint> = when (record) {
        is SleepSessionRecord -> {
            if (record.stages.isNotEmpty()) {
                record.stages.map { stage ->
                    CategoryDataPoint(
                        value = sleepStageToIosValue(stage.stage).toDouble(),
                        startDate = stage.startTime,
                        endDate = stage.endTime,
                        metadata = mapOf(
                            "stage" to stage.stage.toString(),
                            "sessionId" to record.metadata.id,
                        ),
                    )
                }
            } else {
                // No stage data — emit a single "asleep" envelope so the JS side still has something.
                listOf(
                    CategoryDataPoint(
                        value = SLEEP_VALUE_ASLEEP_UNSPECIFIED.toDouble(),
                        startDate = record.startTime,
                        endDate = record.endTime,
                        metadata = mapOf("stage" to "session", "sessionId" to record.metadata.id),
                    ),
                )
            }
        }
        is MenstruationFlowRecord -> listOf(
            CategoryDataPoint(
                value = record.flow.toDouble(),
                startDate = record.time,
                endDate = record.time,
                metadata = null,
            ),
        )
        is IntermenstrualBleedingRecord -> listOf(
            CategoryDataPoint(
                value = 1.0,
                startDate = record.time,
                endDate = record.time,
                metadata = null,
            ),
        )
        is OvulationTestRecord -> listOf(
            CategoryDataPoint(
                value = record.result.toDouble(),
                startDate = record.time,
                endDate = record.time,
                metadata = null,
            ),
        )
        is CervicalMucusRecord -> listOf(
            CategoryDataPoint(
                value = record.appearance.toDouble(),
                startDate = record.time,
                endDate = record.time,
                metadata = mapOf("sensation" to record.sensation.toString()),
            ),
        )
        is SexualActivityRecord -> listOf(
            CategoryDataPoint(
                value = record.protectionUsed.toDouble(),
                startDate = record.time,
                endDate = record.time,
                metadata = null,
            ),
        )
        else -> emptyList()
    }

    @Suppress("CyclomaticComplexMethod")
    fun buildRecord(
        iosType: String,
        value: Double,
        start: Instant,
        end: Instant,
    ): Record? {
        val md = Metadata.manualEntry()
        val safeEnd = if (!end.isAfter(start)) start.plusSeconds(1) else end
        return when (iosType) {
            CT_SLEEP_ANALYSIS -> SleepSessionRecord(
                startTime = start,
                startZoneOffset = ZoneOffset.UTC,
                endTime = safeEnd,
                endZoneOffset = ZoneOffset.UTC,
                stages = listOf(
                    SleepSessionRecord.Stage(
                        startTime = start,
                        endTime = safeEnd,
                        stage = iosValueToSleepStage(value.toInt()),
                    ),
                ),
                title = null,
                notes = null,
                metadata = md,
            )
            CT_MENSTRUAL_FLOW -> MenstruationFlowRecord(
                time = start,
                zoneOffset = ZoneOffset.UTC,
                flow = value.toInt(),
                metadata = md,
            )
            CT_INTERMENSTRUAL_BLEEDING -> IntermenstrualBleedingRecord(
                time = start,
                zoneOffset = ZoneOffset.UTC,
                metadata = md,
            )
            else -> {
                Log.w(
                    TAG,
                    "writeCategoryData: type \"$iosType\" has no Health Connect equivalent — skipping write",
                )
                null
            }
        }
    }

    /**
     * Mirrors `fetch-health-data.ios.ts`'s `mapSleepStageValue` so iOS and Android
     * return overlapping numeric codes (0 = unknown/out_of_bed, 1 = asleep_unspec/awake on iOS, ...).
     * We pick the iOS coding because the consuming JS uses iOS conventions.
     */
    private fun sleepStageToIosValue(stage: Int): Int = when (stage) {
        SleepSessionRecord.STAGE_TYPE_AWAKE -> 2 // awake
        SleepSessionRecord.STAGE_TYPE_SLEEPING -> 1 // asleep unspecified
        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> 0
        SleepSessionRecord.STAGE_TYPE_LIGHT -> 3 // light → "asleepCore" in HK
        SleepSessionRecord.STAGE_TYPE_DEEP -> 4
        SleepSessionRecord.STAGE_TYPE_REM -> 5
        SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> 2
        SleepSessionRecord.STAGE_TYPE_UNKNOWN -> 0
        else -> 0
    }

    private fun iosValueToSleepStage(value: Int): Int = when (value) {
        2 -> SleepSessionRecord.STAGE_TYPE_AWAKE
        1 -> SleepSessionRecord.STAGE_TYPE_SLEEPING
        0 -> SleepSessionRecord.STAGE_TYPE_OUT_OF_BED
        3 -> SleepSessionRecord.STAGE_TYPE_LIGHT
        4 -> SleepSessionRecord.STAGE_TYPE_DEEP
        5 -> SleepSessionRecord.STAGE_TYPE_REM
        else -> SleepSessionRecord.STAGE_TYPE_UNKNOWN
    }

    private const val SLEEP_VALUE_ASLEEP_UNSPECIFIED = 1

    const val CT_SLEEP_ANALYSIS = "HKCategoryTypeIdentifierSleepAnalysis"
    const val CT_MENSTRUAL_FLOW = "HKCategoryTypeIdentifierMenstrualFlow"
    const val CT_INTERMENSTRUAL_BLEEDING = "HKCategoryTypeIdentifierIntermenstrualBleeding"
    const val CT_OVULATION_TEST_RESULT = "HKCategoryTypeIdentifierOvulationTestResult"
    const val CT_CERVICAL_MUCUS_QUALITY = "HKCategoryTypeIdentifierCervicalMucusQuality"
    const val CT_SEXUAL_ACTIVITY = "HKCategoryTypeIdentifierSexualActivity"
}
