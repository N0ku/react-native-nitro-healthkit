package io.github.n0ku.nitrohealthkit.observers

import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.records.Record
import io.github.n0ku.nitrohealthkit.mappers.CategoryMapper
import io.github.n0ku.nitrohealthkit.mappers.QuantityMapper
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Polls Health Connect's [HealthConnectClient.getChanges] API to surface change events
 * up to JS, mimicking iOS's `HKObserverQuery`. Health Connect does not push events to
 * the app, so this implementation uses periodic polling on a dedicated supervisor scope.
 *
 * Lifecycle: a subscription is started by [subscribe], yields a UUID, and runs until
 * [unsubscribe] or [unsubscribeAll] is called. Subscriptions also stop themselves when
 * the underlying [Job] is cancelled (e.g. process death — the JS side must re-subscribe).
 */
class ChangesObserver(
    private val client: HealthConnectClient,
    private val pollIntervalMillis: Long = DEFAULT_POLL_INTERVAL_MS,
) {

    private data class Subscription(
        val recordType: KClass<out Record>,
        val job: Job,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val subscriptions = ConcurrentHashMap<String, Subscription>()

    fun subscribeQuantity(iosType: String, onChange: (String) -> Unit): String? {
        val recordType = QuantityMapper.recordTypeFor(iosType) ?: run {
            Log.w(TAG, "subscribeQuantity: $iosType has no Health Connect record")
            return null
        }
        return subscribe(recordType, onChange)
    }

    fun subscribeCategory(iosType: String, onChange: (String) -> Unit): String? {
        val recordType = CategoryMapper.recordTypeFor(iosType) ?: run {
            Log.w(TAG, "subscribeCategory: $iosType has no Health Connect record")
            return null
        }
        return subscribe(recordType, onChange)
    }

    fun unsubscribe(id: String) {
        subscriptions.remove(id)?.job?.cancel()
    }

    fun unsubscribeAll() {
        subscriptions.values.forEach { it.job.cancel() }
        subscriptions.clear()
    }

    fun dispose() {
        unsubscribeAll()
        scope.cancel()
    }

    private fun subscribe(recordType: KClass<out Record>, onChange: (String) -> Unit): String {
        val id = UUID.randomUUID().toString()
        val job = scope.launch {
            var token = runCatching {
                client.getChangesToken(
                    androidx.health.connect.client.request.ChangesTokenRequest(setOf(recordType)),
                )
            }.getOrElse {
                Log.w(TAG, "getChangesToken failed for ${recordType.simpleName}: ${it.message}")
                return@launch
            }

            while (isActive) {
                delay(pollIntervalMillis)
                ensureActive()
                val response = runCatching { client.getChanges(token) }.getOrElse {
                    Log.w(TAG, "getChanges failed: ${it.message}")
                    null
                } ?: continue

                token = response.nextChangesToken

                if (response.changesTokenExpired) {
                    val refreshed = try {
                        client.getChangesToken(
                            androidx.health.connect.client.request.ChangesTokenRequest(setOf(recordType)),
                        )
                    } catch (t: Throwable) {
                        Log.w(TAG, "re-issuing changes token failed: ${t.message}")
                        null
                    }
                    if (refreshed != null) token = refreshed
                    continue
                }

                if (response.changes.isNotEmpty()) {
                    // We forward only the new token; the JS side can re-fetch data on its terms.
                    onChange(token)
                }
            }
        }
        subscriptions[id] = Subscription(recordType, job)
        return id
    }

    /**
     * Convenience helper used by the background worker: returns the changes since [token]
     * for a record type, materialised as upserted records (deletes ignored — we treat them
     * as nothing-to-sync from the backend's perspective).
     */
    suspend fun upsertedSince(recordType: KClass<out Record>, token: String): Pair<String, List<Record>> {
        var cursor = token
        val collected = mutableListOf<Record>()
        while (true) {
            val response = client.getChanges(cursor)
            if (response.changesTokenExpired) {
                cursor = client.getChangesToken(
                    androidx.health.connect.client.request.ChangesTokenRequest(setOf(recordType)),
                )
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

    companion object {
        private const val TAG = "HCChangesObserver"
        const val DEFAULT_POLL_INTERVAL_MS = 30_000L
    }
}
