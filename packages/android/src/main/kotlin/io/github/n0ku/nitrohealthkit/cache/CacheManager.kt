package io.github.n0ku.nitrohealthkit.cache

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory TTL cache used by [io.github.n0ku.nitrohealthkit.HealthKitModule] to back the
 * `useCache` / `cacheTTL` parameters of the Nitro spec, matching the iOS [CacheManager.swift].
 *
 * Thread-safe through [ConcurrentHashMap]; values are stored as [Any] and cast on read,
 * mirroring the Swift API which is also untyped at the storage layer.
 */
class CacheManager {
    private data class Entry(
        val value: Any,
        val storedAt: Instant,
        val ttlSeconds: Long,
    ) {
        fun isExpired(now: Instant): Boolean =
            storedAt.plusSeconds(ttlSeconds).isBefore(now)
    }

    private val cache = ConcurrentHashMap<String, Entry>()

    @Volatile
    private var maxSize: Int = DEFAULT_MAX_SIZE

    fun configure(maxSize: Int) {
        this.maxSize = maxSize.coerceAtLeast(1)
    }

    fun <T : Any> set(key: String, value: T, ttlSeconds: Long) {
        if (cache.size >= maxSize) {
            cleanupExpired()
            if (cache.size >= maxSize) {
                evictOldest()
            }
        }
        cache[key] = Entry(value = value, storedAt = Instant.now(), ttlSeconds = ttlSeconds)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(key: String): T? {
        val entry = cache[key] ?: return null
        if (entry.isExpired(Instant.now())) {
            cache.remove(key)
            return null
        }
        return entry.value as? T
    }

    fun has(key: String): Boolean = get<Any>(key) != null

    fun remove(key: String) {
        cache.remove(key)
    }

    fun clear() {
        cache.clear()
    }

    fun size(): Int = cache.size

    private fun cleanupExpired() {
        val now = Instant.now()
        val expiredKeys = cache.entries
            .filter { it.value.isExpired(now) }
            .map { it.key }
        expiredKeys.forEach(cache::remove)
    }

    private fun evictOldest() {
        val toRemove = (maxSize / 4).coerceAtLeast(1)
        cache.entries
            .sortedBy { it.value.storedAt }
            .take(toRemove)
            .forEach { cache.remove(it.key) }
    }

    companion object {
        const val DEFAULT_MAX_SIZE: Int = 100
        const val DEFAULT_TTL_SECONDS: Long = 60

        @JvmStatic
        val shared: CacheManager by lazy { CacheManager() }

        private val isoFormatter: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT

        @JvmStatic
        fun generateKey(
            type: String,
            startDate: Instant,
            endDate: Instant,
            options: String = "",
        ): String =
            "${type}_${isoFormatter.format(startDate)}_${isoFormatter.format(endDate)}_$options"
    }
}
