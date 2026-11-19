package com.example.util

import android.util.Log

/**
 * Performance monitoring utility for IPTV data operations.
 * Measures and logs key operation latencies in debug / development environments.
 */
object PerformanceMonitor {
    const val TAG = "PerfMonitor"

    inline fun <T> measure(metricName: String, block: () -> T): T {
        val start = System.currentTimeMillis()
        val result = block()
        val duration = System.currentTimeMillis() - start
        Log.d(TAG, "[$metricName] executed in ${duration} ms")
        return result
    }

    suspend inline fun <T> measureSuspend(metricName: String, crossinline block: suspend () -> T): T {
        val start = System.currentTimeMillis()
        val result = block()
        val duration = System.currentTimeMillis() - start
        Log.d(TAG, "[$metricName] executed in ${duration} ms")
        return result
    }

    fun logEvent(metricName: String, durationMs: Long, detail: String = "") {
        val suffix = if (detail.isNotBlank()) " ($detail)" else ""
        Log.d(TAG, "[$metricName] $durationMs ms$suffix")
    }
}
