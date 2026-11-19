package com.example.data.iptv.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Parser für MAC-basierte IPTV-Portale (Stalker Middleware / XC Portal API Emulation).
 * Verarbeitet Antworten von:
 * - itv / get_all_channels & get_genres
 * - vod / get_all_records & get_categories
 * - series / get_all_series & get_categories
 */
object IptvMacPortalParser {

    /**
     * Extrahiert ein JSON-Array aus verschiedenen Stalker-Wrapper-Formaten:
     * - {"js": {"data": [...]}}
     * - {"js": [...]}
     * - {"data": [...]}}
     * - [...]
     */
    fun extractDataArray(jsonResponse: String): JSONArray? {
        val trimmed = jsonResponse.trim()
        if (trimmed.startsWith("[")) {
            return try { JSONArray(trimmed) } catch (_: Exception) { null }
        }
        if (trimmed.startsWith("{")) {
            return try {
                val root = JSONObject(trimmed)
                if (root.has("js")) {
                    val js = root.opt("js")
                    if (js is JSONArray) return js
                    if (js is JSONObject) {
                        if (js.has("data")) return js.optJSONArray("data")
                        if (js.has("total_items") && js.has("data")) return js.optJSONArray("data")
                    }
                }
                if (root.has("data")) {
                    val data = root.opt("data")
                    if (data is JSONArray) return data
                }
                null
            } catch (_: Exception) { null }
        }
        return null
    }

    /**
     * Parst Stalker Genre-/Kategorie-Antworten in eine Map (genre_id -> title/name).
     */
    fun parseCategories(jsonResponse: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val array = extractDataArray(jsonResponse) ?: return map

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("id", obj.optString("category_id", "")).trim()
            val title = obj.optString("title", obj.optString("category_name", obj.optString("name", ""))).trim()
            if (id.isNotBlank() && title.isNotBlank()) {
                map[id] = title
            }
        }
        return map
    }

    /**
     * Parst Stalker Live-TV Kanäle (itv / get_all_channels).
     */
    fun parseLiveChannels(
        jsonResponse: String,
        baseUrl: String,
        categories: Map<String, String> = emptyMap(),
        startIndex: Int = 0
    ): List<IptvStreamItem> {
        val items = mutableListOf<IptvStreamItem>()
        val array = extractDataArray(jsonResponse) ?: return items

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("id", "").trim()
            val name = obj.optString("name", obj.optString("o_name", "Kanal $id")).trim()
            if (id.isBlank() && name.isBlank()) continue

            val rawCmd = obj.optString("cmd", "").trim()
            val streamUrl = cleanStalkerUrl(rawCmd, baseUrl)
            if (streamUrl.isBlank()) continue

            val genreId = obj.optString("tv_genre_id", obj.optString("genre_id", "")).trim()
            val catName = obj.optString("category_name", "").trim()
            val resolvedCategory = catName.ifBlank { categories[genreId] ?: "Live TV" }
            val logo = obj.optString("logo", obj.optString("screenshot_uri", "")).trim().ifBlank { null }
            val tvgId = obj.optString("tvg_id", id).trim().ifBlank { id }

            val tier = IptvRegionClassifier.classify(
                rawCategory = resolvedCategory,
                name = name,
                tvgCountry = null,
                tvgId = tvgId
            )

            if (tier != null) {
                items.add(
                    IptvStreamItem(
                        id = UUID.nameUUIDFromBytes("mac_live_${id}_$name".toByteArray()).toString(),
                        name = name,
                        streamUrl = streamUrl,
                        type = IptvContentType.LIVE_TV,
                        originalCategory = resolvedCategory,
                        regionTier = tier,
                        tvgId = tvgId,
                        tvgName = name,
                        logoUrl = formatLogoUrl(logo, baseUrl),
                        sourceProvider = "MAC_PORTAL",
                        extraAttributes = mapOf("stalker_id" to id, "genre_id" to genreId, "raw_cmd" to rawCmd),
                        originalProviderIndex = startIndex + i
                    )
                )
            }
        }

        return items
    }

    /**
     * Parst Stalker VOD Filme (vod / get_all_records).
     */
    fun parseVodRecords(
        jsonResponse: String,
        baseUrl: String,
        categories: Map<String, String> = emptyMap(),
        startIndex: Int = 0
    ): List<IptvStreamItem> {
        val items = mutableListOf<IptvStreamItem>()
        val array = extractDataArray(jsonResponse) ?: return items

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("id", "").trim()
            val name = obj.optString("name", obj.optString("o_name", "Film $id")).trim()
            if (id.isBlank() && name.isBlank()) continue

            val rawCmd = obj.optString("cmd", "").trim()
            val streamUrl = cleanStalkerUrl(rawCmd, baseUrl)
            if (streamUrl.isBlank()) continue

            val catId = obj.optString("category_id", "").trim()
            val catName = obj.optString("category_name", "").trim()
            val resolvedCategory = catName.ifBlank { categories[catId] ?: "VOD Filme" }
            val poster = obj.optString("screenshot_uri", obj.optString("poster", "")).trim().ifBlank { null }

            val tier = IptvRegionClassifier.classify(
                rawCategory = resolvedCategory,
                name = name,
                tvgCountry = null,
                tvgId = null
            )

            if (tier != null) {
                items.add(
                    IptvStreamItem(
                        id = UUID.nameUUIDFromBytes("mac_vod_${id}_$name".toByteArray()).toString(),
                        name = name,
                        streamUrl = streamUrl,
                        type = IptvContentType.VOD_MOVIE,
                        originalCategory = resolvedCategory,
                        regionTier = tier,
                        tvgId = null,
                        tvgName = name,
                        logoUrl = formatLogoUrl(poster, baseUrl),
                        sourceProvider = "MAC_PORTAL",
                        extraAttributes = mapOf("stalker_id" to id, "category_id" to catId, "raw_cmd" to rawCmd),
                        originalProviderIndex = startIndex + i
                    )
                )
            }
        }

        return items
    }

    /**
     * Parst Stalker Serien (series / get_all_series).
     */
    fun parseSeries(
        jsonResponse: String,
        baseUrl: String,
        categories: Map<String, String> = emptyMap(),
        startIndex: Int = 0
    ): List<IptvStreamItem> {
        val items = mutableListOf<IptvStreamItem>()
        val array = extractDataArray(jsonResponse) ?: return items

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("id", obj.optString("series_id", "")).trim()
            val name = obj.optString("title", obj.optString("name", obj.optString("o_name", "Serie $id"))).trim()
            if (id.isBlank() && name.isBlank()) continue

            val catId = obj.optString("category_id", "").trim()
            val catName = obj.optString("category_name", "").trim()
            val resolvedCategory = catName.ifBlank { categories[catId] ?: "Serien" }
            val poster = obj.optString("screenshot_uri", obj.optString("cover", "")).trim().ifBlank { null }

            val tier = IptvRegionClassifier.classify(
                rawCategory = resolvedCategory,
                name = name,
                tvgCountry = null,
                tvgId = null
            )

            if (tier != null) {
                items.add(
                    IptvStreamItem(
                        id = UUID.nameUUIDFromBytes("mac_series_${id}_$name".toByteArray()).toString(),
                        name = name,
                        streamUrl = "$baseUrl/server/load.php?type=series&action=get_ordered_list&movie_id=$id",
                        type = IptvContentType.SERIES,
                        originalCategory = resolvedCategory,
                        regionTier = tier,
                        tvgId = id,
                        tvgName = name,
                        logoUrl = formatLogoUrl(poster, baseUrl),
                        seriesTitle = name,
                        sourceProvider = "MAC_PORTAL",
                        extraAttributes = mapOf("stalker_id" to id, "category_id" to catId),
                        originalProviderIndex = startIndex + i
                    )
                )
            }
        }

        return items
    }

    private fun cleanStalkerUrl(rawCmd: String, baseUrl: String): String {
        if (rawCmd.isBlank()) return ""
        val trimmed = rawCmd.trim()

        // Typische Stalker Prefixes wie "ffrt3 http://..." oder "auto http://..." oder "ffmpeg http://..."
        val httpIdx = trimmed.indexOf("http://", ignoreCase = true)
        val httpsIdx = trimmed.indexOf("https://", ignoreCase = true)
        val targetIdx = when {
            httpIdx >= 0 && httpsIdx >= 0 -> minOf(httpIdx, httpsIdx)
            httpIdx >= 0 -> httpIdx
            httpsIdx >= 0 -> httpsIdx
            else -> -1
        }

        if (targetIdx >= 0) {
            val candidate = trimmed.substring(targetIdx).trim()
            val spaceIdx = candidate.indexOf(' ')
            return if (spaceIdx > 0) candidate.substring(0, spaceIdx).trim() else candidate
        }

        // Relativer Pfad
        if (trimmed.startsWith("/")) {
            return "${baseUrl.trimEnd('/')}$trimmed"
        }

        return trimmed
    }

    private fun formatLogoUrl(logo: String?, baseUrl: String): String? {
        if (logo.isNullOrBlank()) return null
        val trimmed = logo.trim()
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            return trimmed
        }
        if (trimmed.startsWith("/")) {
            return "${baseUrl.trimEnd('/')}$trimmed"
        }
        return "${baseUrl.trimEnd('/')}/$trimmed"
    }
}
