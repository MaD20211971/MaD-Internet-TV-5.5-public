package com.example.data.livetvlight

import android.content.Context
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

data class CachedCatalogSnapshot(
    val channels: List<LiveTvLightChannel>,
    val syncedAtMs: Long
)

/**
 * Persistenter JSON-Cache für die direkt von 2ix2.com eingelesene Live-TV-light-Senderliste.
 * Sorgt für 0 ms Sofortstart beim Öffnen und bewahrt echte Seiten-URLs sowie Originallogos
 * zuverlässig über App-Neustarts hinweg.
 */
object LiveTvLightCatalogStore {

    private const val PREFS_NAME = "live_tv_light_2ix2_cache"
    private const val KEY_CHANNELS_JSON = "channels_json_v2"
    private const val KEY_SYNCED_AT_MS = "synced_at_ms_v2"

    fun loadCachedChannels(context: Context): CachedCatalogSnapshot? {
        return try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val rawJson = prefs.getString(KEY_CHANNELS_JSON, null) ?: return null
            if (rawJson.isBlank()) return null
            val syncedAtMs = prefs.getLong(KEY_SYNCED_AT_MS, 0L)
            val channels = deserializeChannels(rawJson)
            if (channels.isEmpty()) null else CachedCatalogSnapshot(channels, syncedAtMs)
        } catch (_: Exception) {
            null
        }
    }

    fun saveCachedChannels(
        context: Context,
        channels: List<LiveTvLightChannel>,
        syncedAtMs: Long = System.currentTimeMillis()
    ) {
        if (channels.isEmpty()) return
        try {
            val rawJson = serializeChannels(channels)
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_CHANNELS_JSON, rawJson)
                .putLong(KEY_SYNCED_AT_MS, syncedAtMs)
                .apply()
        } catch (_: Exception) {
        }
    }

    fun clearCache(context: Context) {
        try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
        } catch (_: Exception) {
        }
    }

    internal fun serializeChannels(channels: List<LiveTvLightChannel>): String {
        val array = JSONArray()
        for (ch in channels) {
            val obj = JSONObject().apply {
                put("id", ch.id)
                put("name", ch.name)
                put("category", ch.category)
                put("countryCode", ch.countryCode)
                put("logoUrl", ch.logoUrl)
                put("fallbackLogoUrl", ch.fallbackLogoUrl)
                put("logoBadge", ch.logoBadge)
                put("brandColor", ch.brandColor.value.toLong())
                put("pageUrl", ch.pageUrl)
                put("primaryStreamUrl", ch.primaryStreamUrl)
                put("fallbackStreamUrls", JSONArray(ch.fallbackStreamUrls))
                put("internalResolverKey", ch.internalResolverKey)
                put("alternateResolverKeys", JSONArray(ch.alternateResolverKeys))
                put("tvgId", ch.tvgId)
            }
            array.put(obj)
        }
        return array.toString()
    }

    internal fun deserializeChannels(rawJson: String): List<LiveTvLightChannel> {
        return try {
            val array = JSONArray(rawJson)
            val result = ArrayList<LiveTvLightChannel>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id").trim()
                val name = obj.optString("name").trim()
                if (id.isBlank() || name.isBlank()) continue

                val fallbackStreams = obj.optJSONArray("fallbackStreamUrls").toStringList()
                val altSlugs = obj.optJSONArray("alternateResolverKeys").toStringList()
                val colorBits = obj.optLong("brandColor", 0L)
                val brandColor = if (colorBits != 0L) Color(colorBits.toULong()) else Color(0xFF0284C7)

                result.add(
                    LiveTvLightChannel(
                        id = id,
                        name = name,
                        category = obj.optString("category", "Privatsender").ifBlank { "Privatsender" },
                        countryCode = obj.optString("countryCode", "DE").ifBlank { "DE" },
                        logoUrl = obj.optString("logoUrl", ""),
                        fallbackLogoUrl = obj.optString("fallbackLogoUrl", ""),
                        logoBadge = obj.optString("logoBadge", name.take(4).uppercase()),
                        brandColor = brandColor,
                        pageUrl = obj.optString("pageUrl", ""),
                        primaryStreamUrl = obj.optString("primaryStreamUrl", ""),
                        fallbackStreamUrls = fallbackStreams,
                        internalResolverKey = obj.optString("internalResolverKey", ""),
                        alternateResolverKeys = altSlugs,
                        tvgId = obj.optString("tvgId", ""),
                        supportsInstantStart = false
                    )
                )
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null || length() == 0) return emptyList()
        val list = ArrayList<String>(length())
        for (i in 0 until length()) {
            val item = optString(i).trim()
            if (item.isNotBlank()) list.add(item)
        }
        return list
    }
}
