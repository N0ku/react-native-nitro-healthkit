package io.github.n0ku.nitrohealthkit

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlin.reflect.KClass
import com.margelo.nitro.core.Promise
import com.margelo.nitro.healthkit.BackgroundSyncConfig
import com.margelo.nitro.healthkit.CategoryDataPoint
import com.margelo.nitro.healthkit.HealthData
import com.margelo.nitro.healthkit.HybridHealthKitSpec
import com.margelo.nitro.healthkit.QuantityDataPoint
import com.margelo.nitro.healthkit.WorkoutDataPoint
import io.github.n0ku.nitrohealthkit.auth.SecureCredentialsStore
import io.github.n0ku.nitrohealthkit.cache.CacheManager
import io.github.n0ku.nitrohealthkit.mappers.CategoryMapper
import io.github.n0ku.nitrohealthkit.mappers.QuantityMapper
import io.github.n0ku.nitrohealthkit.mappers.WorkoutMapper
import io.github.n0ku.nitrohealthkit.observers.ChangesObserver
import io.github.n0ku.nitrohealthkit.util.HealthConnectContextHolder
import io.github.n0ku.nitrohealthkit.util.TimeRangeHelper
import io.github.n0ku.nitrohealthkit.workers.HealthSyncWorker
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Concrete Kotlin implementation of [HybridHealthKitSpec].
 *
 * This is the Android counterpart of `ios/HealthKitModule.swift`. The contract surface is
 * deliberately identical so the JS-side abstraction
 * (`app/services/health/fetch-health-data.android.ts` and `.ios.ts`) can collapse into a
 * single platform-agnostic file. Where Health Connect cannot fulfil a request (Apple-only
 * type, missing record class on the host's HC version), the method logs a warning and
 * returns the empty-equivalent rather than throwing — the host app handles missing data
 * gracefully today and we want to preserve that behaviour.
 */
class HealthKitModule : HybridHealthKitSpec() {

    private val cache = CacheManager.shared

    private val appContext: Context get() = HealthConnectContextHolder.require()
    private val credentialsStore by lazy { SecureCredentialsStore(appContext) }

    private val client: HealthConnectClient
        get() = HealthConnectClient.getOrCreate(appContext)

    private val observer: ChangesObserver by lazy { ChangesObserver(client) }

    // region — Availability / permissions

    override fun isHealthKitAvailable(): Promise<Boolean> = Promise.async {
        HealthConnectClient.getSdkStatus(appContext) == HealthConnectClient.SDK_AVAILABLE
    }

    /**
     * Reports whether the default Health Connect permissions are
     * already granted. When at least one required permission is
     * missing, also launches Health Connect's "Manage permissions"
     * screen so the user can grant access without having to navigate
     * there manually — this is what callers expect from "request"
     * (and matches HealthKit's behaviour on iOS, where the system
     * dialog comes up automatically the first time).
     *
     * The proper Activity-based contract
     * ([PermissionController.createRequestPermissionResultContract])
     * still has to live in host-app code if it wants to await the
     * dialog result inside an Activity result callback. The intent
     * we launch here is the same screen the contract would surface,
     * just without the result hand-off — `health-sync.ts` retries
     * the fetch on every `AppState` `active` transition, so when the
     * user returns from HC the data flows in on its own.
     */
    override fun requestAuthorization(): Promise<Boolean> = Promise.async {
        if (HealthConnectClient.getSdkStatus(appContext) != HealthConnectClient.SDK_AVAILABLE) {
            return@async false
        }
        val required = defaultPermissions()
        val granted = client.permissionController.getGrantedPermissions()
        if (granted.containsAll(required)) {
            return@async true
        }
        // Fall back to launching the HC manage-permissions intent.
        // Using application context + NEW_TASK because Nitro modules
        // don't expose the current Activity to us — this still puts
        // the user in front of the right screen.
        runCatching {
            val intent = Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS").apply {
                putExtra("android.intent.extra.PACKAGE_NAME", appContext.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
        }.onFailure {
            Log.w(TAG, "Failed to launch HC permission manager: ${it.message}")
        }
        false
    }

    override fun checkAuthorizationStatus(type: String): Promise<String> = Promise.async {
        val quantityPerm = QuantityMapper.readPermissionFor(type)
        val categoryPerm = CategoryMapper.readPermissionFor(type)
        val permission = quantityPerm ?: categoryPerm ?: return@async NOT_DETERMINED
        val granted = client.permissionController.getGrantedPermissions()
        if (granted.contains(permission)) AUTHORIZED else DENIED
    }

    // endregion

    // region — Reads

    /**
     * Page through Health Connect for the full set of records in `[startDate, endDate]`.
     *
     * `HealthConnectClient.readRecords` returns at most `pageSize` rows (default 1000,
     * hard cap 5000). Without pagination we'd silently truncate large queries — for
     * example a 10-year HeartRate query routinely exceeds 1000 rows and we'd only get
     * an arbitrary slice, which is what makes detail screens look "empty" or skip days.
     *
     * The loop bails after [MAX_PAGES] iterations to avoid pathological histories;
     * 200 × 5000 = 1M records, which is plenty for any sane lookback window.
     */
    private suspend fun <T : Record> readAllRecords(
        recordType: KClass<T>,
        startDate: Instant,
        endDate: Instant,
    ): List<T> = withContext(Dispatchers.IO) {
        // HC 1.1.0-alpha12 has a defect with pageToken-based pagination over wide
        // windows: the second readRecords() call throws "startTime must be before
        // endTime" regardless of the filter we re-supply. We sidestep it by chunking
        // the request into smaller (sub-)windows. Each chunk falls under HC's default
        // page size (~1000 records) for the metric types the host app cares about,
        // so a single readRecords() call per chunk is enough.
        //
        // CHUNK_SIZE picks 90 days as a reasonable trade-off: short enough that even
        // HeartRate (≈ 1-3 records / day worn) stays well under 1000, long enough
        // that a 10-year history is only ~40 chunks.
        val safeEnd = if (endDate.isAfter(startDate)) endDate else startDate.plusMillis(1)
        val accumulated = mutableListOf<T>()

        var chunkStart = startDate
        var chunks = 0
        while (chunkStart.isBefore(safeEnd) && chunks < MAX_PAGES) {
            val chunkEndCandidate = chunkStart.plus(CHUNK_DURATION)
            val chunkEnd = if (chunkEndCandidate.isAfter(safeEnd)) safeEnd else chunkEndCandidate
            if (!chunkEnd.isAfter(chunkStart)) break

            val response = readChunkWithRetry(recordType, chunkStart, chunkEnd)
            if (response != null) {
                accumulated += response.records
                if (response.pageToken != null) {
                    Log.w(TAG, "readAllRecords ${recordType.simpleName}: chunk $chunkStart→$chunkEnd hit pageSize cap (${response.records.size}); window has more records that won't be returned")
                }
            }
            chunkStart = chunkEnd
            chunks++
        }
        if (chunks >= MAX_PAGES) {
            Log.w(TAG, "readAllRecords ${recordType.simpleName}: hit MAX_PAGES guard at $chunks chunks; data may be truncated")
        }
        accumulated
    }

    /**
     * Read a single chunk with exponential backoff on Health Connect's "API call
     * quota exceeded" exception.
     *
     * Health Connect enforces a token-bucket quota per calling package; the
     * exact size depends on Android version but for the API levels we
     * target a tight burst of parallel reads is enough to drain the bucket
     * (see the logcat traces showing `availableQuota: 0.31` immediately
     * after launch). The host app currently fans out 6 record types in
     * parallel via `Promise.all`, and each unfolds into ~52 chunks for the
     * 1-year window, so without pacing we'd try to fire ~310 calls at
     * once.
     *
     * Strategy: serialise all readRecords() calls through a single Mutex
     * and enforce a minimum spacing between releases. The mutex makes the
     * pacing _global_ across record types — without it, six parallel
     * coroutines could each enter their own "wait" path and still hit
     * HC concurrently.
     */
    private suspend fun <T : Record> readChunkWithRetry(
        recordType: KClass<T>,
        start: Instant,
        end: Instant,
    ): androidx.health.connect.client.response.ReadRecordsResponse<T>? {
        var attempt = 0
        var backoffMs = QUOTA_RETRY_INITIAL_MS
        while (true) {
            // Acquire the global gate, pace ourselves, then issue the
            // read. The actual HC RPC happens inside `withLock` so two
            // record types cannot race to call readRecords() in the same
            // tick.
            val response = HC_PACING_MUTEX.withLock {
                val sinceLast = System.nanoTime() - lastHcCallNanos
                val minGapNanos = MIN_HC_GAP_MS * 1_000_000L
                if (sinceLast in 0 until minGapNanos) {
                    kotlinx.coroutines.delay((minGapNanos - sinceLast) / 1_000_000L)
                }
                lastHcCallNanos = System.nanoTime()
                try {
                    Result.success(
                        client.readRecords(
                            ReadRecordsRequest(
                                recordType = recordType,
                                timeRangeFilter = TimeRangeFilter.between(start, end),
                                // Pump the per-call page cap up to HC's hard
                                // maximum (5000). With 30-day chunks the
                                // default of 1000 truncates the current
                                // month for users with dense Steps data
                                // (Samsung Health publishes ~10 records/day
                                // when the watch is worn). At 5000 we cover
                                // ≈ 165 records/day before truncation,
                                // which has comfortable headroom for every
                                // metric the host app surfaces today.
                                pageSize = HC_PAGE_SIZE,
                            ),
                        ),
                    )
                } catch (t: Throwable) {
                    Result.failure<androidx.health.connect.client.response.ReadRecordsResponse<T>>(t)
                }
            }

            response.fold(
                onSuccess = { return it },
                onFailure = { t ->
                    val isQuotaError = (t.message ?: "").contains("quota", ignoreCase = true) ||
                        (t.cause?.message ?: "").contains("quota", ignoreCase = true)
                    if (!isQuotaError || attempt >= QUOTA_RETRY_MAX_ATTEMPTS) {
                        Log.w(TAG, "readAllRecords ${recordType.simpleName} chunk failed [$start→$end] (attempt $attempt): ${t.javaClass.simpleName}: ${t.message}")
                        return null
                    }
                    kotlinx.coroutines.delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(QUOTA_RETRY_MAX_MS)
                    attempt++
                },
            )
        }
    }

    override fun getQuantityData(
        type: String,
        startDate: Instant,
        endDate: Instant,
        aggregationType: String?,
        useCache: Boolean?,
        cacheTTL: Double?,
    ): Promise<Array<QuantityDataPoint>> = Promise.async {
        val useCacheValue = useCache ?: true
        val ttl = (cacheTTL ?: DEFAULT_CACHE_TTL_SECONDS.toDouble()).toLong()
        val key = CacheManager.generateKey(type, startDate, endDate, aggregationType.orEmpty())

        if (useCacheValue) {
            cache.get<Array<QuantityDataPoint>>(key)?.let { return@async it }
        }

        // Health Connect aggregates records from every installed source
        // (Samsung Health, Google Fit, Fitbit, etc.) and exposes them as
        // separate `Record`s. Summing the raw rows client-side therefore
        // double- or triple-counts whenever two of those sources sync the
        // same activity into HC (very common when a user lets Samsung
        // Health *and* the watch's own Google Fit bridge run side-by-side).
        //
        // The fix is to delegate per-day bucketing to HC itself via
        // `aggregateGroupByPeriod`, which resolves overlapping records
        // through the user's "data priority" list (configurable in the
        // Health Connect app's Settings). Callers opt in by passing
        // `aggregationType = "daily"`; raw-sample callers (HeartRate,
        // BloodPressure, etc.) keep the existing `readRecords` path.
        if (aggregationType == "daily" || aggregationType == "daily_sum") {
            val daily = readDailyAggregates(type, startDate, endDate)
            if (useCacheValue) cache.set(key, daily, ttl)
            return@async daily
        }

        val recordType = QuantityMapper.recordTypeFor(type) ?: run {
            Log.w(TAG, "getQuantityData: $type has no Health Connect equivalent")
            return@async emptyArray()
        }

        val records = readAllRecords(recordType, startDate, endDate)
        val flattened = QuantityMapper.flatten(type, records).toTypedArray()
        Log.d(TAG, "getQuantityData $type [$startDate→$endDate]: ${records.size} raw records → ${flattened.size} samples")

        if (useCacheValue) cache.set(key, flattened, ttl)
        flattened
    }

    /**
     * Per-day, source-deduplicated aggregate for the given iOS quantity
     * type. Uses Health Connect's
     * [HealthConnectClient.aggregateGroupByDuration] under the hood,
     * which walks the user's configured "data priority" list and only
     * counts the highest-priority source for any given (timestamp,
     * type) cell — so an activity that both Samsung Health and Google
     * Fit synced into HC isn't double-counted.
     *
     * We deliberately use the `Duration`-based slicer (24h fixed
     * windows aligned to the device's local midnight) instead of the
     * `Period`-based one. The `Period` variant takes a `LocalDateTime`
     * filter and HC interprets each record's bucket using *the record's
     * own stored ZoneOffset*. That worked correctly for users staying
     * in one place, but produced a 1-day shift for users whose device
     * TZ no longer matches the TZ that recorded the data (e.g. a user
     * in Paris whose phone is set to America/Toronto for testing). The
     * `Duration` variant ignores per-record offsets and buckets purely
     * on absolute time aligned to the device-local midnight we compute
     * here — which matches what Samsung Health / Google Fit show in
     * their own UIs.
     *
     * DST trade-off: a fixed 24-hour slicer can't represent the
     * 23- or 25-hour days that bracket a DST transition (twice a year
     * in the EU/US). On those two days the bucket boundary drifts by
     * one hour, which is well within the noise floor of step counts.
     *
     * Returns one [QuantityDataPoint] per day in the requested range,
     * in chronological order, with `value > 0` (zero-valued buckets
     * are dropped — the host app's chart fills missing days
     * client-side).
     */
    private suspend fun readDailyAggregates(
        iosType: String,
        startDate: Instant,
        endDate: Instant,
    ): Array<QuantityDataPoint> = withContext(Dispatchers.IO) {
        val metric = QuantityMapper.sumAggregateFor(iosType) ?: run {
            Log.w(TAG, "readDailyAggregates: no sum metric for $iosType — falling back to raw read")
            // Fallback: hand raw flattening back to the caller via the
            // standard read path. Better to return *something* than to
            // silently drop the type.
            val recordType = QuantityMapper.recordTypeFor(iosType) ?: return@withContext emptyArray()
            return@withContext QuantityMapper.flatten(iosType, readAllRecords(recordType, startDate, endDate)).toTypedArray()
        }
        val unit = QuantityMapper.unitFor(iosType)

        // Snap the request range to device-local midnight on both ends.
        // The slicer is a fixed 24h Duration, so to keep every bucket
        // representing a whole device-local calendar day we have to
        // start exactly on local midnight; the JS layer already does
        // this for `startDate`/`endDate` but we redo it here defensively
        // — callers that hit `getQuantityData` directly may not.
        val zone = ZoneId.systemDefault()
        val startLocalDate = startDate.atZone(zone).toLocalDate()
        val endLocalDate = endDate.atZone(zone).toLocalDate()
        val alignedStart = startLocalDate.atStartOfDay(zone).toInstant()
        val alignedEnd = endLocalDate.plusDays(1).atStartOfDay(zone).toInstant()
        if (!alignedEnd.isAfter(alignedStart)) {
            return@withContext emptyArray()
        }

        val buckets = HC_PACING_MUTEX.withLock {
            val sinceLast = System.nanoTime() - lastHcCallNanos
            val minGapNanos = MIN_HC_GAP_MS * 1_000_000L
            if (sinceLast in 0 until minGapNanos) {
                kotlinx.coroutines.delay((minGapNanos - sinceLast) / 1_000_000L)
            }
            lastHcCallNanos = System.nanoTime()
            try {
                client.aggregateGroupByDuration(
                    AggregateGroupByDurationRequest(
                        metrics = setOf(metric),
                        timeRangeFilter = TimeRangeFilter.between(alignedStart, alignedEnd),
                        timeRangeSlicer = Duration.ofHours(24),
                    ),
                )
            } catch (t: Throwable) {
                Log.w(TAG, "readDailyAggregates $iosType failed: ${t.javaClass.simpleName}: ${t.message}")
                return@withLock emptyList()
            }
        }

        val out = ArrayList<QuantityDataPoint>(buckets.size)
        buckets.forEach { bucket ->
            val raw = bucket.result[metric]
            val value = QuantityMapper.coerceAggregateToDouble(iosType, raw)
            if (value > 0.0) {
                out += QuantityDataPoint(
                    value = value,
                    unit = unit,
                    startDate = bucket.startTime,
                    endDate = bucket.endTime,
                    metadata = null,
                )
            }
        }
        Log.d(TAG, "readDailyAggregates $iosType [$alignedStart→$alignedEnd]: ${buckets.size} buckets → ${out.size} non-empty days")
        out.toTypedArray()
    }

    override fun getAggregatedQuantity(
        type: String,
        startDate: Instant,
        endDate: Instant,
        aggregationType: String,
        useCache: Boolean?,
        cacheTTL: Double?,
    ): Promise<Double> = Promise.async {
        val useCacheValue = useCache ?: true
        val ttl = (cacheTTL ?: DEFAULT_CACHE_TTL_SECONDS.toDouble()).toLong()
        val key = CacheManager.generateKey(type, startDate, endDate, "agg_$aggregationType")

        if (useCacheValue) {
            cache.get<Double>(key)?.let { return@async it }
        }

        val value = aggregateValue(type, startDate, endDate, aggregationType.lowercase())
        if (useCacheValue) cache.set(key, value, ttl)
        value
    }

    private suspend fun aggregateValue(
        iosType: String,
        startDate: Instant,
        endDate: Instant,
        aggregationType: String,
    ): Double {
        val recordType = QuantityMapper.recordTypeFor(iosType) ?: return 0.0
        val metric: AggregateMetric<*>? = QuantityMapper.sumAggregateFor(iosType)

        // Sum path: rely on Health Connect's native aggregate when available, fast and pre-bucketed.
        if (aggregationType == "sum" && metric != null) {
            val response = withContext(Dispatchers.IO) {
                try {
                    client.aggregate(
                        AggregateRequest(
                            metrics = setOf(metric),
                            timeRangeFilter = TimeRangeFilter.between(startDate, endDate),
                        ),
                    )
                } catch (t: Throwable) {
                    Log.w(TAG, "aggregate $iosType: failed [$startDate→$endDate]: ${t.javaClass.simpleName}: ${t.message}")
                    null
                }
            }
            val v = if (response != null) QuantityMapper.coerceAggregateToDouble(iosType, response[metric]) else 0.0
            Log.d(TAG, "aggregate $iosType [$startDate→$endDate] sum=$v")
            return v
        }

        // Fallback: pull samples and reduce client-side. Slower but works for any aggregation.
        val records = readAllRecords(recordType, startDate, endDate)
        val values = QuantityMapper.flatten(iosType, records).map { it.value }
        if (values.isEmpty()) return 0.0
        return when (aggregationType) {
            "average", "avg" -> values.average()
            "min" -> values.min()
            "max" -> values.max()
            "count" -> values.size.toDouble()
            "sum" -> values.sum()
            else -> values.sum()
        }
    }

    override fun getCategoryData(
        type: String,
        startDate: Instant,
        endDate: Instant,
        useCache: Boolean?,
        cacheTTL: Double?,
    ): Promise<Array<CategoryDataPoint>> = Promise.async {
        val useCacheValue = useCache ?: true
        val ttl = (cacheTTL ?: DEFAULT_CACHE_TTL_SECONDS.toDouble()).toLong()
        val key = CacheManager.generateKey(type, startDate, endDate)

        if (useCacheValue) {
            cache.get<Array<CategoryDataPoint>>(key)?.let { return@async it }
        }

        val recordType = CategoryMapper.recordTypeFor(type) ?: run {
            Log.w(TAG, "getCategoryData: $type has no Health Connect equivalent")
            return@async emptyArray()
        }
        val records = readAllRecords(recordType, startDate, endDate)
        val flattened = CategoryMapper.flatten(type, records).toTypedArray()
        Log.d(TAG, "getCategoryData $type [$startDate→$endDate]: ${records.size} raw records → ${flattened.size} samples")
        if (useCacheValue) cache.set(key, flattened, ttl)
        flattened
    }

    override fun getWorkouts(
        startDate: Instant,
        endDate: Instant,
        useCache: Boolean?,
        cacheTTL: Double?,
    ): Promise<Array<WorkoutDataPoint>> = Promise.async {
        val useCacheValue = useCache ?: true
        val ttl = (cacheTTL ?: DEFAULT_CACHE_TTL_SECONDS.toDouble()).toLong()
        val key = CacheManager.generateKey("workouts", startDate, endDate)

        if (useCacheValue) {
            cache.get<Array<WorkoutDataPoint>>(key)?.let { return@async it }
        }
        val records = readAllRecords(ExerciseSessionRecord::class, startDate, endDate)
        val workouts = records.map(WorkoutMapper::map).toTypedArray()
        if (useCacheValue) cache.set(key, workouts, ttl)
        workouts
    }

    override fun getHealthDataForTimeRange(
        timeRange: String,
        customStartDate: Instant?,
        customEndDate: Instant?,
        useCache: Boolean?,
        cacheTTL: Double?,
    ): Promise<HealthData> = Promise.async {
        val range = TimeRangeHelper.resolve(timeRange, customStartDate, customEndDate)
        getHealthDataInternal(range.start, range.end, useCache ?: true, cacheTTL)
    }

    override fun getHealthData(
        startDate: Instant,
        endDate: Instant,
    ): Promise<HealthData> = Promise.async {
        getHealthDataInternal(startDate, endDate, useCache = true, cacheTTL = null)
    }

    private suspend fun getHealthDataInternal(
        startDate: Instant,
        endDate: Instant,
        useCache: Boolean,
        cacheTTL: Double?,
    ): HealthData = coroutineScope {
        val ttl = (cacheTTL ?: DEFAULT_CACHE_TTL_SECONDS.toDouble())
        val stepsDeferred = async {
            runCatching {
                getAggregatedQuantity(
                    QuantityMapper.QT_STEPS,
                    startDate,
                    endDate,
                    "sum",
                    useCache,
                    ttl,
                ).await()
            }.getOrDefault(0.0)
        }
        val heartRateDeferred = async {
            runCatching {
                getAggregatedQuantity(
                    QuantityMapper.QT_HEART_RATE,
                    startDate,
                    endDate,
                    "average",
                    useCache,
                    ttl,
                ).await()
            }.getOrDefault(0.0)
        }
        val activeEnergyDeferred = async {
            runCatching {
                getAggregatedQuantity(
                    QuantityMapper.QT_ACTIVE_ENERGY_BURNED,
                    startDate,
                    endDate,
                    "sum",
                    useCache,
                    ttl,
                ).await()
            }.getOrDefault(0.0)
        }
        val distanceDeferred = async {
            runCatching {
                getAggregatedQuantity(
                    QuantityMapper.QT_DISTANCE_WALKING_RUNNING,
                    startDate,
                    endDate,
                    "sum",
                    useCache,
                    ttl,
                ).await()
            }.getOrDefault(0.0)
        }
        val sleepDeferred = async {
            runCatching {
                val records = client.readRecords(
                    ReadRecordsRequest(
                        recordType = SleepSessionRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startDate, endDate),
                    ),
                ).records
                if (records.isNotEmpty()) "ASLEEP" else null
            }.getOrNull()
        }
        // Each deferred has already started in parallel via async {}; we just join them here.
        val steps = stepsDeferred.await()
        val heartRate = heartRateDeferred.await()
        val activeEnergy = activeEnergyDeferred.await()
        val distance = distanceDeferred.await()
        val sleep = sleepDeferred.await()
        HealthData(
            steps = steps.takeIf { it > 0 },
            heartRate = heartRate.takeIf { it > 0 },
            activeEnergy = activeEnergy.takeIf { it > 0 },
            distance = distance.takeIf { it > 0 },
            sleepAnalysis = sleep,
        )
    }

    // getRealtimeData is intentionally not exposed — Map<String, Double> is not supported
    // by fbjni on Android. Callers should fan out via getAggregatedQuantity.

    // endregion

    // region — Writes

    override fun writeQuantityData(
        type: String,
        value: Double,
        unit: String,
        startDate: Instant,
        endDate: Instant,
        metadata: Map<String, String>?,
    ): Promise<Boolean> = Promise.async {
        val record = QuantityMapper.buildRecord(type, value, unit, startDate, endDate) ?: return@async false
        runCatching {
            withContext(Dispatchers.IO) {
                client.insertRecords(listOf(record))
            }
            true
        }.getOrElse {
            Log.w(TAG, "writeQuantityData failed for $type: ${it.message}")
            false
        }
    }

    override fun writeCategoryData(
        type: String,
        value: Double,
        startDate: Instant,
        endDate: Instant,
        metadata: Map<String, String>?,
    ): Promise<Boolean> = Promise.async {
        val record = CategoryMapper.buildRecord(type, value, startDate, endDate) ?: return@async false
        runCatching {
            withContext(Dispatchers.IO) {
                client.insertRecords(listOf(record))
            }
            true
        }.getOrElse {
            Log.w(TAG, "writeCategoryData failed for $type: ${it.message}")
            false
        }
    }

    // endregion

    // region — Observers

    override fun observeQuantityChanges(
        type: String,
        callback: (String) -> Unit,
    ): Promise<String> = Promise.async {
        observer.subscribeQuantity(type, callback) ?: ""
    }

    override fun observeCategoryChanges(
        type: String,
        callback: (String) -> Unit,
    ): Promise<String> = Promise.async {
        observer.subscribeCategory(type, callback) ?: ""
    }

    override fun removeObserver(subscriptionId: String): Promise<Unit> = Promise.async {
        observer.unsubscribe(subscriptionId)
    }

    override fun removeAllObservers(): Promise<Unit> = Promise.async {
        observer.unsubscribeAll()
    }

    // endregion

    // region — Background sync

    override fun registerBackgroundSync(config: BackgroundSyncConfig): Promise<Unit> = Promise.async {
        credentialsStore.store(
            apiBaseUrl = config.apiBaseUrl,
            jwtToken = config.jwtToken,
            syncPath = config.syncPath,
            types = config.types.toList(),
            intervalMinutes = config.intervalMinutes.toInt(),
        )
        HealthSyncWorker.enqueue(appContext, config.intervalMinutes.toInt())
    }

    override fun unregisterBackgroundSync(): Promise<Unit> = Promise.async {
        HealthSyncWorker.cancel(appContext)
        credentialsStore.clear()
    }

    override fun isBackgroundSyncRegistered(): Promise<Boolean> = Promise.async {
        // Simplest signal we can give from native: do we still have credentials? The work itself
        // is managed by WorkManager and may be deferred, retried, etc. — the credential record
        // is a stable proxy for "the user opted in".
        !credentialsStore.jwt().isNullOrBlank()
    }

    // endregion

    // region — Misc

    override fun clearCache(): Promise<Unit> = Promise.async { cache.clear() }

    override fun getSteps(startDate: Instant, endDate: Instant): Promise<Double> =
        getAggregatedQuantity(QuantityMapper.QT_STEPS, startDate, endDate, "sum", true, null)

    override fun getHeartRate(startDate: Instant, endDate: Instant): Promise<Double> =
        getAggregatedQuantity(QuantityMapper.QT_HEART_RATE, startDate, endDate, "average", true, null)

    override fun seedTestHealthData(): Promise<Unit> = Promise.async {
        Log.i(TAG, "seedTestHealthData is a no-op on Android — use the Health Connect app to inject test data")
    }

    // endregion

    /**
     * The default set of Health Connect permissions the module wants. The host app's
     * AndroidManifest must declare these `<uses-permission>` entries — see the library's
     * own manifest for the canonical list.
     */
    private fun defaultPermissions(): Set<String> = setOf(
        // Activity + heart rate + sleep — the original set the lib has
        // always demanded.
        StepsRecord::class,
        HeartRateRecord::class,
        DistanceRecord::class,
        FloorsClimbedRecord::class,
        ActiveCaloriesBurnedRecord::class,
        TotalCaloriesBurnedRecord::class,
        SleepSessionRecord::class,
        ExerciseSessionRecord::class,
        // Body composition — added in 5.1.0 so the BodyComposition
        // widget/screen works for users without a Withings account but
        // with a Samsung Health / Google Fit / Fitbit scale.
        WeightRecord::class,
        BodyFatRecord::class,
        LeanBodyMassRecord::class,
        HeightRecord::class,
        // Cardio vitals — same rationale. SpO2/HRV/BP/RestingHR are all
        // recorded by Galaxy Watches and similar wearables and exposed
        // via HC, no Withings required.
        BloodPressureRecord::class,
        OxygenSaturationRecord::class,
        HeartRateVariabilityRmssdRecord::class,
        RestingHeartRateRecord::class,
    ).map { androidx.health.connect.client.permission.HealthPermission.getReadPermission(it) }.toSet()

    companion object {
        private const val TAG = "HealthKitModule"
        // Window size used to chunk wide reads. HC 1.1.0-alpha12's pageToken pagination
        // is broken on `between(start, end)` queries past the 2nd page, so we issue many
        // smaller readRecords() calls instead.
        //
        // 7 days is the largest window that reliably stays under HC's default page
        // size (1000 records / chunk) for active users with continuous heart-rate
        // monitoring and granular step tracking. A 10-year history then takes ~520
        // calls, which is well within HC's local-RPC budget (~ms per call).
        // 30-day windows give 12 chunks per type for the 1-year lookback
        // the host app uses today (vs. 52 chunks for 7-day windows). HC's
        // default 1000-record page cap is comfortable for HeartRate even
        // at minute-level cadence (30d × 24h × 60min ≈ 43k samples would
        // overflow, but the recorded data the host app cares about is
        // sparser — Samsung Health logs ~1 sample / minute when the watch
        // is worn). If we ever observe page-cap warnings on real users
        // we'll re-shrink to 14 days.
        private val CHUNK_DURATION: java.time.Duration = java.time.Duration.ofDays(30)
        // Pathological-history guard: 2000 × 30 days ≈ 164 years.
        private const val MAX_PAGES = 2000

        // Quota retry parameters — see [readChunkWithRetry]. Starting
        // backoff is generous because HC's token bucket refills slowly
        // once drained.
        private const val QUOTA_RETRY_INITIAL_MS: Long = 500
        private const val QUOTA_RETRY_MAX_MS: Long = 5000
        private const val QUOTA_RETRY_MAX_ATTEMPTS = 6

        // Global pacing for HC readRecords. We funnel every chunk read
        // through a single Mutex and enforce a minimum gap between calls;
        // a Semaphore-based approach (N permits in parallel) wasn't
        // enough — HC's quota is a single shared bucket and even 4
        // concurrent calls drained it on dense Heart Rate data.
        //
        // HC's token bucket holds ~1.0 token and refills at roughly
        // 2.25 tokens/sec (measured empirically on Samsung S24 / API 34:
        // we observed quota recover from 0.30 → 0.94 over ~280ms while
        // sustained 80ms-spaced calls kept the bucket pinned near zero).
        // We pick a 600ms gap = ~1.67 calls/sec, well below the refill
        // rate, leaving enough headroom for the system to absorb other
        // apps' HC traffic without starving us.
        //
        // For the 1-year × 6-types × 30-day-chunks workload (~72 calls
        // per full historical sync) that's a ~43-second cold-fetch
        // ceiling. The result is cached for 24h afterwards, so users see
        // the latency only on the very first sync (or after their device
        // clears Health Connect's quota bucket).
        private const val MIN_HC_GAP_MS: Long = 600
        // Health Connect caps `pageSize` at 5000 across all supported
        // versions. We always request the maximum because the cost of a
        // larger response is negligible compared to issuing a second
        // chunked call.
        private const val HC_PAGE_SIZE = 5000
        private val HC_PACING_MUTEX = Mutex()
        @Volatile private var lastHcCallNanos: Long = 0L
        private const val NOT_DETERMINED = "notDetermined"
        private const val AUTHORIZED = "sharingAuthorized"
        private const val DENIED = "sharingDenied"
        private const val DEFAULT_CACHE_TTL_SECONDS = 60L
        private const val REALTIME_CACHE_TTL_SECONDS = 30L
    }
}
