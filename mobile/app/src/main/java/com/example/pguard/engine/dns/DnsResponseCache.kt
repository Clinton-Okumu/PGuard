package com.example.pguard.engine.dns

import java.util.LinkedHashMap
import java.util.Locale

/**
 * Thread-safe bounded LRU TTL cache for DNS wire responses, keyed by domain, query type,
 * and filtering mode (`family`, `unfiltered`, or `safe:<safeHost>`).
 */
class DnsResponseCache(
    private val clockMs: () -> Long = System::currentTimeMillis,
    private val maxEntries: Int = 512,
    private val minTtlSeconds: Int = 15,
    private val maxTtlSeconds: Int = 300,
    private val defaultTtlSeconds: Int = 60
) {
    private data class CacheEntry(
        val payload: ByteArray,
        val expiresAtMs: Long
    )

    private val lock = Any()
    private val entries = LinkedHashMap<String, CacheEntry>(minOf(maxEntries + 1, 128), 0.75f, true)

    fun get(domain: String, qType: Int, modeKey: String, transactionId: Int): ByteArray? {
        val key = buildKey(domain, qType, modeKey)
        val now = clockMs()
        synchronized(lock) {
            val entry = entries[key] ?: return null
            if (now >= entry.expiresAtMs) {
                entries.remove(key)
                return null
            }
            return DnsPacketCodec.withTransactionId(entry.payload, transactionId)
        }
    }

    fun put(domain: String, qType: Int, modeKey: String, response: ByteArray) {
        if (response.size < 12) return
        val extractedTtl = DnsPacketCodec.extractMinTtlSeconds(response) ?: defaultTtlSeconds
        val clampedTtl = extractedTtl.coerceIn(minTtlSeconds, maxTtlSeconds)
        val now = clockMs()
        val expiresAt = now + clampedTtl * 1000L
        val key = buildKey(domain, qType, modeKey)

        synchronized(lock) {
            if (!entries.containsKey(key) && entries.size >= maxEntries) {
                val iterator = entries.entries.iterator()
                while (iterator.hasNext()) {
                    val existing = iterator.next()
                    if (now >= existing.value.expiresAtMs) {
                        iterator.remove()
                    }
                }
                while (entries.size >= maxEntries) {
                    val oldest = entries.entries.iterator()
                    if (!oldest.hasNext()) break
                    oldest.next()
                    oldest.remove()
                }
            }
            entries[key] = CacheEntry(response.copyOf(), expiresAt)
        }
    }

    fun clear() {
        synchronized(lock) {
            entries.clear()
        }
    }

    internal fun size(): Int = synchronized(lock) { entries.size }

    private fun buildKey(domain: String, qType: Int, modeKey: String): String {
        return "${domain.trim().lowercase(Locale.ROOT)}|$qType|$modeKey"
    }
}
