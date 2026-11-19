package com.example.data.player

import android.content.Context
import android.content.SharedPreferences

data class WatchProgress(
    val streamUrl: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long
) {
    val progressPercent: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}

object WatchProgressHelper {
    private const val PREFS_NAME = "iptv_watch_progress"
    private const val KEY_PREFIX = "pos_"
    private const val DUR_PREFIX = "dur_"
    private const val TIME_PREFIX = "time_"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun sanitizeKey(key: String): String {
        return key.hashCode().toString()
    }

    fun saveProgress(context: Context, streamUrl: String, positionMs: Long, durationMs: Long) {
        if (streamUrl.isBlank()) return
        val key = sanitizeKey(streamUrl)
        val prefs = getPrefs(context)
        // Only save if watched for more than 10s and not within the last 25s of the video
        if (positionMs > 10_000L && (durationMs <= 0L || positionMs < durationMs - 25_000L)) {
            prefs.edit()
                .putLong(KEY_PREFIX + key, positionMs)
                .putLong(DUR_PREFIX + key, durationMs)
                .putLong(TIME_PREFIX + key, System.currentTimeMillis())
                .apply()
        } else if (durationMs > 0L && positionMs >= durationMs - 25_000L) {
            // Reached near the end of the movie / episode -> mark as finished
            clearProgress(context, streamUrl)
        }
    }

    fun getSavedPosition(context: Context, streamUrl: String): Long {
        if (streamUrl.isBlank()) return 0L
        val key = sanitizeKey(streamUrl)
        return getPrefs(context).getLong(KEY_PREFIX + key, 0L)
    }

    fun getSavedProgress(context: Context, streamUrl: String): WatchProgress? {
        if (streamUrl.isBlank()) return null
        val key = sanitizeKey(streamUrl)
        val prefs = getPrefs(context)
        val pos = prefs.getLong(KEY_PREFIX + key, 0L)
        if (pos <= 0L) return null
        val dur = prefs.getLong(DUR_PREFIX + key, 0L)
        val time = prefs.getLong(TIME_PREFIX + key, 0L)
        return WatchProgress(streamUrl, pos, dur, time)
    }

    fun clearProgress(context: Context, streamUrl: String) {
        if (streamUrl.isBlank()) return
        val key = sanitizeKey(streamUrl)
        getPrefs(context).edit()
            .remove(KEY_PREFIX + key)
            .remove(DUR_PREFIX + key)
            .remove(TIME_PREFIX + key)
            .apply()
    }
}
