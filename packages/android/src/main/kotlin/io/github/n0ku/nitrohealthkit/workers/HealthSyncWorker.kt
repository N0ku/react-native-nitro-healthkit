package io.github.n0ku.nitrohealthkit.workers

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.n0ku.nitrohealthkit.auth.SecureCredentialsStore
import io.github.n0ku.nitrohealthkit.mappers.CategoryMapper
import io.github.n0ku.nitrohealthkit.mappers.QuantityMapper
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Periodic background sync of Health Connect deltas to the configured backend.
 *
 * Registered by [io.github.n0ku.nitrohealthkit.HealthKitModule.registerBackgroundSync] via
 * [WorkManager], the worker runs roughly every `intervalMinutes` (≥ 15 — WorkManager's floor)
 * with a `requiresBatteryNotLow` + `requiresNetwork` constraint set so it does not drain users.
 *
 * Credentials and config are loaded from [SecureCredentialsStore] at execution time, so the
 * worker never has them in plain text inside the [androidx.work.Data] payload.
 */
class HealthSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val store by lazy { SecureCredentialsStore(applicationContext) }

    override suspend fun doWork(): Result = try {
        val jwt = store.jwt()
        val baseUrl = store.apiBaseUrl()
        if (jwt.isNullOrBlank() || baseUrl.isNullOrBlank()) {
            Log.w(TAG, "Missing credentials — skipping sync run")
            Result.success()
        } else {
            executeSync(jwt = jwt, baseUrl = baseUrl)
        }
    } catch (ce: CancellationException) {
        throw ce
    } catch (t: Throwable) {
        Log.e(TAG, "Sync failed: ${t.message}", t)
        Result.retry()
    }

    private suspend fun executeSync(jwt: String, baseUrl: String): Result {
        val context = applicationContext
        val status = HealthConnectClient.getSdkStatus(context)
        if (status != HealthConnectClient.SDK_AVAILABLE) {
            Log.i(TAG, "Health Connect not available (status=$status) — skipping")
            return Result.success()
        }
        val client = HealthConnectClient.getOrCreate(context)
        val typeIdentifiers = store.types().ifEmpty { DEFAULT_TYPES }
        val payload = JSONArray()

        typeIdentifiers.forEach { iosType ->
            val recordType = QuantityMapper.recordTypeFor(iosType)
                ?: CategoryMapper.recordTypeFor(iosType)
                ?: return@forEach
            val (newToken, records) = pollChanges(client, recordType)
            if (records.isNotEmpty()) {
                payload.put(buildPayloadEntry(iosType, records))
            }
            store.saveChangesToken(recordType.qualifiedName ?: recordType.simpleName.orEmpty(), newToken)
        }

        if (payload.length() == 0) {
            return Result.success()
        }

        return postPayload(baseUrl = baseUrl, jwt = jwt, payload = payload)
    }

    private suspend fun pollChanges(
        client: HealthConnectClient,
        recordType: KClass<out Record>,
    ): Pair<String, List<Record>> {
        val key = recordType.qualifiedName ?: recordType.simpleName.orEmpty()

        // Health Connect's SDK eagerly converts every record in a batch
        // to its `androidx.health.*` representation. If even one record in
        // the underlying provider's table has corrupt timestamps (we've
        // seen `startTime must be before endTime` on real devices when a
        // partner app wrote a zero-duration entry), the whole
        // readRecords()/getChanges() call throws and the worker would
        // otherwise retry the same poisoned window forever. We catch the
        // converter's IllegalArgumentException specifically, drop this
        // run's batch, and reset the changes token on the next tick so
        // the worker can move past the bad data.
        val stored: String = store.storedChangesTokens()[key] ?: run {
            val (token, initial) = firstRunSnapshot(client, recordType)
            return token to initial
        }

        var cursor: String = stored
        val collected = mutableListOf<Record>()
        while (true) {
            val response = try {
                client.getChanges(cursor)
            } catch (t: IllegalArgumentException) {
                Log.w(TAG, "getChanges(${recordType.simpleName}) rejected by SDK converter (${t.message}); resetting token")
                val fresh = client.getChangesToken(ChangesTokenRequest(setOf(recordType)))
                return fresh to emptyList()
            }
            if (response.changesTokenExpired) {
                cursor = client.getChangesToken(ChangesTokenRequest(setOf(recordType)))
                return cursor to emptyList()
            }
            response.changes.forEach { change ->
                if (change is UpsertionChange) collected += change.record
            }
            cursor = response.nextChangesToken
            if (!response.hasMore) break
        }
        return cursor to collected
    }

    /**
     * First-run helper. Mirrors `pollChanges`'s defensive shape: if the
     * initial readRecords() blows up on a malformed record, log it, fall
     * back to an empty seed payload, and still return a fresh changes
     * token so subsequent runs make incremental progress instead of
     * re-failing on the same window.
     */
    private suspend fun firstRunSnapshot(
        client: HealthConnectClient,
        recordType: KClass<out Record>,
    ): Pair<String, List<Record>> {
        val token = client.getChangesToken(ChangesTokenRequest(setOf(recordType)))
        val initial = try {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = recordType,
                    timeRangeFilter = TimeRangeFilter.between(Instant.now().minus(INITIAL_LOOKBACK), Instant.now()),
                ),
            ).records
        } catch (t: IllegalArgumentException) {
            Log.w(TAG, "First-run readRecords(${recordType.simpleName}) rejected by SDK converter (${t.message}); skipping seed")
            emptyList()
        }
        return token to initial
    }

    private fun buildPayloadEntry(iosType: String, records: List<Record>): JSONObject {
        val arr = JSONArray()
        QuantityMapper.flatten(iosType, records).forEach { pt ->
            arr.put(
                JSONObject().apply {
                    put("value", pt.value)
                    put("unit", pt.unit)
                    put("startDate", pt.startDate.toString())
                    put("endDate", pt.endDate.toString())
                    pt.metadata?.let { md ->
                        put("metadata", JSONObject(md as Map<*, *>))
                    }
                },
            )
        }
        CategoryMapper.flatten(iosType, records).forEach { pt ->
            arr.put(
                JSONObject().apply {
                    put("value", pt.value)
                    put("startDate", pt.startDate.toString())
                    put("endDate", pt.endDate.toString())
                    pt.metadata?.let { md ->
                        put("metadata", JSONObject(md as Map<*, *>))
                    }
                },
            )
        }
        return JSONObject().apply {
            put("type", iosType)
            put("samples", arr)
        }
    }

    private fun postPayload(baseUrl: String, jwt: String, payload: JSONArray): Result {
        val body = JSONObject().apply {
            put("source", "android-health-connect")
            put("syncedAt", Instant.now().toString())
            put("entries", payload)
        }.toString().toRequestBody("application/json".toMediaType())

        val url = baseUrl.trimEnd('/') + store.syncPath()
        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Authorization", "Bearer $jwt")
            .addHeader("Content-Type", "application/json")
            .build()

        return httpClient.newCall(request).execute().use { response ->
            when {
                response.isSuccessful -> Result.success()
                response.code in 500..599 -> {
                    Log.w(TAG, "Backend returned ${response.code} — will retry")
                    Result.retry()
                }
                response.code == 401 || response.code == 403 -> {
                    // Token rejected: clear so we stop spamming until the app re-registers.
                    Log.w(TAG, "Auth rejected (${response.code}) — clearing credentials")
                    store.clear()
                    Result.success()
                }
                else -> {
                    Log.w(TAG, "Backend returned ${response.code} — giving up this run")
                    Result.success()
                }
            }
        }
    }

    companion object {
        private const val TAG = "HealthSyncWorker"
        const val UNIQUE_WORK_NAME = "healthkit-background-sync"
        private val INITIAL_LOOKBACK: Duration = Duration.ofDays(7)
        private val DEFAULT_TYPES = listOf(
            QuantityMapper.QT_STEPS,
            QuantityMapper.QT_HEART_RATE,
            QuantityMapper.QT_ACTIVE_ENERGY_BURNED,
            QuantityMapper.QT_DISTANCE_WALKING_RUNNING,
            QuantityMapper.QT_FLIGHTS_CLIMBED,
            CategoryMapper.CT_SLEEP_ANALYSIS,
        )

        private val httpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()
        }

        fun enqueue(context: Context, intervalMinutes: Int) {
            val minutes = intervalMinutes.coerceAtLeast(15).toLong() // WorkManager floor
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<HealthSyncWorker>(minutes, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
