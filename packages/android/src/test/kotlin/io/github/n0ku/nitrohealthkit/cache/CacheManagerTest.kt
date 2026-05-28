package io.github.n0ku.nitrohealthkit.cache

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheManagerTest {

    @Test
    fun `set and get returns the same value within TTL`() {
        val cache = CacheManager()
        cache.set("k1", "hello", ttlSeconds = 60)
        assertEquals("hello", cache.get<String>("k1"))
    }

    @Test
    fun `get returns null for missing key`() {
        val cache = CacheManager()
        assertNull(cache.get<String>("missing"))
    }

    @Test
    fun `get returns null after TTL expiration`() {
        val cache = CacheManager()
        cache.set("expiring", "value", ttlSeconds = 0)
        // ttlSeconds = 0 means anything stored even an instant ago is already expired.
        Thread.sleep(1)
        assertNull(cache.get<String>("expiring"))
    }

    @Test
    fun `clear removes all entries`() {
        val cache = CacheManager()
        cache.set("a", 1, ttlSeconds = 60)
        cache.set("b", 2, ttlSeconds = 60)
        cache.clear()
        assertEquals(0, cache.size())
        assertNull(cache.get<Int>("a"))
    }

    @Test
    fun `has returns false for expired entries`() {
        val cache = CacheManager()
        cache.set("ephemeral", "v", ttlSeconds = 0)
        Thread.sleep(1)
        assertFalse(cache.has("ephemeral"))
    }

    @Test
    fun `eviction triggers when over maxSize`() {
        val cache = CacheManager()
        cache.configure(maxSize = 4)
        repeat(8) { i -> cache.set("k$i", i, ttlSeconds = 60) }
        // After exceeding maxSize, the cache should self-prune to <= maxSize.
        assertTrue("size should not exceed maxSize but was ${cache.size()}", cache.size() <= 4)
    }

    @Test
    fun `generateKey is deterministic across identical inputs`() {
        val start = Instant.parse("2025-01-01T00:00:00Z")
        val end = Instant.parse("2025-01-02T00:00:00Z")
        val a = CacheManager.generateKey("STEPS", start, end, options = "sum")
        val b = CacheManager.generateKey("STEPS", start, end, options = "sum")
        assertEquals(a, b)
    }

    @Test
    fun `generateKey differs when options change`() {
        val start = Instant.parse("2025-01-01T00:00:00Z")
        val end = Instant.parse("2025-01-02T00:00:00Z")
        val sum = CacheManager.generateKey("STEPS", start, end, options = "sum")
        val avg = CacheManager.generateKey("STEPS", start, end, options = "avg")
        assertNotNull(sum)
        assertTrue(sum != avg)
    }
}
