package com.example.data.repository

import com.example.data.model.ChannelItem
import com.example.data.model.ContentType
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe memory cache for country queries and summaries.
 * Allows zero-latency switching between frequently visited countries (e.g. DE -> AT -> DE).
 */
class CountryMemoryCache {

    data class CacheKey(
        val countryCode: String,
        val playlistId: String?,
        val type: ContentType?,
        val groupTitle: String?,
        val page: Int
    )

    private data class CacheEntry(
        val channels: List<ChannelItem>,
        val timestamp: Long
    )

    private val pageCache = ConcurrentHashMap<CacheKey, CacheEntry>()
    private val ttlMillis: Long = 5 * 60 * 1000L // 5 minutes

    fun get(key: CacheKey): List<ChannelItem>? {
        val entry = pageCache[key] ?: return null
        if (System.currentTimeMillis() - entry.timestamp > ttlMillis) {
            pageCache.remove(key)
            return null
        }
        return entry.channels
    }

    fun put(key: CacheKey, channels: List<ChannelItem>) {
        // Keep memory footprint reasonable
        if (pageCache.size > 200) {
            val oldestKey = pageCache.entries.minByOrNull { it.value.timestamp }?.key
            if (oldestKey != null) {
                pageCache.remove(oldestKey)
            }
        }
        pageCache[key] = CacheEntry(channels, System.currentTimeMillis())
    }

    fun invalidate() {
        pageCache.clear()
    }

    fun invalidateCountry(countryCode: String) {
        val upper = countryCode.trim().uppercase()
        val toRemove = pageCache.keys.filter { it.countryCode.uppercase() == upper }
        for (k in toRemove) {
            pageCache.remove(k)
        }
    }
}
