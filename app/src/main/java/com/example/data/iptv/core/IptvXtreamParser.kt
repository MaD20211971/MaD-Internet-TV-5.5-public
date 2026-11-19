package com.example.data.iptv.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Verarbeitet Xtream Codes Player-API JSON-Antworten für:
 * - Live-TV (get_live_streams + get_live_categories)
 * - VOD Filme (get_vod_streams + get_vod_categories)
 * - Serien (get_series + get_series_categories)
 */
object IptvXtreamParser {

    /**
     * Parst eine Xtream Codes Kategorie-JSON-Antwort und gibt eine Map von category_id -> category_name zurück.
     */
    fun parseCategories(jsonString: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val trimmed = jsonString.trim()
            if (trimmed.startsWith("[")) {
                val array = JSONArray(trimmed)
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val id = obj.optString("category_id", "").trim()
                    val name = obj.optString("category_name", "").trim()
                    if (id.isNotBlank() && name.isNotBlank()) {
                        map[id] = name
                    }
                }
            }
        } catch (_: Exception) {}
        return map
    }

    /**
     * Parst Xtream Codes Live-TV Streams.
     */
    fun parseLiveStreams(
        jsonString: String,
        baseUrl: String,
        username: String,
        password: String,
        categories: Map<String, String> = emptyMap(),
        startIndex: Int = 0
    ): List<IptvStreamItem> {
        val items = mutableListOf<IptvStreamItem>()
        val cleanBase = baseUrl.trimEnd('/')

        try {
            val array = JSONArray(jsonString.trim())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val streamId = obj.optString("stream_id", "").trim()
                if (streamId.isBlank()) continue

                val name = obj.optString("name", "Kanal $streamId").trim()
                val catId = obj.optString("category_id", "").trim()
                val catName = obj.optString("category_name", "").trim()
                val resolvedCat = catName.ifBlank { categories[catId] ?: "Live TV" }
                val epgId = obj.optString("epg_channel_id", "").trim().ifBlank { null }
                val icon = obj.optString("stream_icon", "").trim().ifBlank { null }

                val tier = IptvRegionClassifier.classify(
                    rawCategory = resolvedCat,
                    name = name,
                    tvgCountry = null,
                    tvgId = epgId
                )

                if (tier != null) {
                    val streamUrl = "$cleanBase/live/$username/$password/$streamId.m3u8"
                    items.add(
                        IptvStreamItem(
                            id = UUID.nameUUIDFromBytes("xtream_live_${streamId}".toByteArray()).toString(),
                            name = name,
                            streamUrl = streamUrl,
                            type = IptvContentType.LIVE_TV,
                            originalCategory = resolvedCat,
                            regionTier = tier,
                            tvgId = epgId ?: streamId,
                            tvgName = name,
                            logoUrl = icon,
                            sourceProvider = "XTREAM",
                            extraAttributes = mapOf("stream_id" to streamId, "category_id" to catId),
                            originalProviderIndex = startIndex + i
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        return items
    }

    /**
     * Parst Xtream Codes VOD / Filme Streams.
     */
    fun parseVodStreams(
        jsonString: String,
        baseUrl: String,
        username: String,
        password: String,
        categories: Map<String, String> = emptyMap(),
        startIndex: Int = 0
    ): List<IptvStreamItem> {
        val items = mutableListOf<IptvStreamItem>()
        val cleanBase = baseUrl.trimEnd('/')

        try {
            val array = JSONArray(jsonString.trim())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val streamId = obj.optString("stream_id", "").trim()
                if (streamId.isBlank()) continue

                val name = obj.optString("name", "Film $streamId").trim()
                val catId = obj.optString("category_id", "").trim()
                val catName = obj.optString("category_name", "").trim()
                val resolvedCat = catName.ifBlank { categories[catId] ?: "VOD Movies" }
                val icon = obj.optString("stream_icon", "").trim().ifBlank { null }
                val ext = obj.optString("container_extension", "mp4").trim().ifBlank { "mp4" }

                val tier = IptvRegionClassifier.classify(
                    rawCategory = resolvedCat,
                    name = name,
                    tvgCountry = null,
                    tvgId = null
                )

                if (tier != null) {
                    val streamUrl = "$cleanBase/movie/$username/$password/$streamId.$ext"
                    items.add(
                        IptvStreamItem(
                            id = UUID.nameUUIDFromBytes("xtream_vod_${streamId}".toByteArray()).toString(),
                            name = name,
                            streamUrl = streamUrl,
                            type = IptvContentType.VOD_MOVIE,
                            originalCategory = resolvedCat,
                            regionTier = tier,
                            tvgId = null,
                            tvgName = name,
                            logoUrl = icon,
                            containerExtension = ext,
                            sourceProvider = "XTREAM",
                            extraAttributes = mapOf("stream_id" to streamId, "category_id" to catId),
                            originalProviderIndex = startIndex + i
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        return items
    }

    /**
     * Parst Xtream Codes Serien.
     */
    fun parseSeries(
        jsonString: String,
        baseUrl: String,
        username: String,
        password: String,
        categories: Map<String, String> = emptyMap(),
        startIndex: Int = 0
    ): List<IptvStreamItem> {
        val items = mutableListOf<IptvStreamItem>()
        val cleanBase = baseUrl.trimEnd('/')

        try {
            val array = JSONArray(jsonString.trim())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val seriesId = obj.optString("series_id", "").trim()
                if (seriesId.isBlank()) continue

                val name = obj.optString("name", obj.optString("title", "Serie $seriesId")).trim()
                val catId = obj.optString("category_id", "").trim()
                val catName = obj.optString("category_name", "").trim()
                val resolvedCat = catName.ifBlank { categories[catId] ?: "Serien" }
                val cover = obj.optString("cover", "").trim().ifBlank { null }

                val tier = IptvRegionClassifier.classify(
                    rawCategory = resolvedCat,
                    name = name,
                    tvgCountry = null,
                    tvgId = null
                )

                if (tier != null) {
                    // Serien-Info URL für Episoden-Abruf
                    val seriesUrl = "$cleanBase/player_api.php?username=$username&password=$password&action=get_series_info&series_id=$seriesId"
                    items.add(
                        IptvStreamItem(
                            id = UUID.nameUUIDFromBytes("xtream_series_${seriesId}".toByteArray()).toString(),
                            name = name,
                            streamUrl = seriesUrl,
                            type = IptvContentType.SERIES,
                            originalCategory = resolvedCat,
                            regionTier = tier,
                            tvgId = seriesId,
                            tvgName = name,
                            logoUrl = cover,
                            seriesTitle = name,
                            sourceProvider = "XTREAM",
                            extraAttributes = mapOf("series_id" to seriesId, "category_id" to catId),
                            originalProviderIndex = startIndex + i
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        return items
    }
}
