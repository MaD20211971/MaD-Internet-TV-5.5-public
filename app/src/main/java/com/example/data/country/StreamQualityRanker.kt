package com.example.data.country

import com.example.util.FastLruCache

/**
 * Utility to extract resolution rank and backup status from channel names and quality badges.
 */
object StreamQualityRanker {

    private val rankCache = FastLruCache<String, Int>(4096)

    /**
     * Resolves quality rank where lower numbers indicate higher quality.
     * 1 = 4K / UHD / 2160p
     * 2 = FHD / 1080p / 1080i / Full HD
     * 3 = HD / 720p
     * 4 = SD / 576p / 480p / Low
     * 5 = Standard / Unknown
     * 99 = Backup / Reserve / Alternative Stream
     */
    fun getQualityRank(title: String?, resolutionBadge: String? = null): Int {
        val t = title ?: ""
        val b = resolutionBadge ?: ""
        if (t.isEmpty() && b.isEmpty()) return 5
        val cacheKey = "$t|$b"
        val cached = rankCache.get(cacheKey)
        if (cached != null) return cached

        val raw = "$t $b".trim().uppercase()
        if (raw.isBlank()) return 5

        // Backup streams are placed at the end of identical channels
        val isBackup = raw.contains("BACKUP") || raw.contains("BKP") ||
            raw.contains("RESERVE") || raw.contains("ALT ") ||
            raw.contains("ALTERNATIVE") || raw.contains("(ALT)") ||
            raw.contains("[ALT]") || raw.contains("NOTFALL")

        val result = if (isBackup) {
            99
        } else {
            when {
                raw.contains("4K") || raw.contains("UHD") || raw.contains("2160P") -> 1
                raw.contains("FHD") || raw.contains("1080P") || raw.contains("1080I") ||
                    raw.contains("FULL HD") || raw.contains("FULLHD") || raw.contains("FULL-HD") -> 2
                raw.contains("HD") || raw.contains("720P") -> 3
                raw.contains("SD") || raw.contains("576P") || raw.contains("480P") || raw.contains("LOW") -> 4
                else -> 5
            }
        }

        rankCache.put(cacheKey, result)
        return result
    }

    fun clearCache() {
        rankCache.evictAll()
    }
}
