package com.example.data.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Singleton holder for Media3 SimpleCache with an LRU evictor.
 * Provides resilient caching of HLS/DASH video chunks to prevent buffering during minor network drops.
 */
@OptIn(UnstableApi::class)
object MediaCacheHolder {
    @Volatile
    private var simpleCache: SimpleCache? = null

    fun getCache(context: Context): SimpleCache {
        return simpleCache ?: synchronized(this) {
            simpleCache ?: run {
                val cacheDir = File(context.cacheDir, "media3_video_cache")
                val evictor = LeastRecentlyUsedCacheEvictor(100L * 1024 * 1024) // 100 MB LRU video chunk cache
                val databaseProvider = StandaloneDatabaseProvider(context.applicationContext)
                SimpleCache(cacheDir, evictor, databaseProvider).also {
                    simpleCache = it
                }
            }
        }
    }
}
