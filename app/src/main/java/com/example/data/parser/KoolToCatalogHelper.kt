package com.example.data.parser

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Helper for analyzing, resolving, and converting publicly accessible content
 * from http://kool.to / https://kool.to (and mirror https://kool.ws).
 *
 * Public endpoints supported:
 * - https://kool.to/channels (JSON array of Live TV channels: country, id, name, p)
 * - https://kool.to/play/{id}/index.m3u8 (HLS stream endpoint per channel ID)
 * - https://kool.to/live2/play/{id}.ts (MPEG-TS fallback endpoint)
 */
object KoolToCatalogHelper {

    private const val KOOL_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    private val SUFFIX_CLEAN_REGEX = Regex("""\s+\.[a-zA-Z0-9]{1,2}$""")
    private val PLAY_ID_REGEX = Regex("""/(?:play|live2/play)/([0-9]+)""", RegexOption.IGNORE_CASE)

    data class KoolChannelItem(
        val id: String,
        val name: String,
        val country: String,
        val groupTitle: String,
        val streamUrl: String
    )

    fun isKoolToUrl(url: String): Boolean {
        val lower = url.trim().lowercase()
        return lower.contains("kool.to") || lower.contains("kool.ws")
    }

    /**
     * Extracts a direct playable HLS URL (`http://kool.to/play/{id}/index.m3u8`) if the given URL
     * is a kool.to/kool.ws stream or channel play link.
     */
    fun extractPlayableStreamUrl(rawUrl: String): String? {
        val trimmed = rawUrl.trim()
        if (!isKoolToUrl(trimmed)) return null
        val host = if (trimmed.contains("kool.ws", ignoreCase = true)) "http://kool.ws" else "http://kool.to"
        val match = PLAY_ID_REGEX.find(trimmed)
        if (match != null) {
            val id = match.groupValues[1]
            if (id.isNotBlank()) {
                return "$host/play/$id/index.m3u8"
            }
        }
        if (trimmed.contains(".m3u8", ignoreCase = true) || trimmed.endsWith(".ts", ignoreCase = true)) {
            return if (trimmed.startsWith("https://", ignoreCase = true)) {
                "http://" + trimmed.substring("https://".length)
            } else {
                trimmed
            }
        }
        return null
    }

    /**
     * Fetches the public kool.to channel catalog and returns a structured, DACH-prioritized list
     * of channels for crash-free native playback inside Web TV.
     */
    fun fetchKoolChannelItems(httpClient: OkHttpClient): List<KoolChannelItem> {
        val response = executeCatalogRequest(httpClient, "http://kool.to/channels") ?: return emptyList()
        return response.use { res ->
            val bodyText = res.body?.string()?.trim().orEmpty()
            if (bodyText.isEmpty()) return@use emptyList()
            parseJsonCatalogToItems(bodyText)
        }
    }

    fun parseJsonCatalogToItems(jsonText: String, baseHost: String = "http://kool.to"): List<KoolChannelItem> {
        return try {
            val trimmed = jsonText.trim()
            val array = when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> {
                    val obj = JSONObject(trimmed)
                    obj.optJSONArray("channels") ?: obj.optJSONArray("data") ?: JSONArray()
                }
                else -> JSONArray()
            }
            val result = ArrayList<KoolChannelItem>(array.length())
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id", "").ifBlank {
                    item.optLong("id", -1L).takeIf { it >= 0 }?.toString() ?: ""
                }
                val rawName = item.optString("name", "").trim()
                if (id.isBlank() || rawName.isBlank()) continue
                val rawCountry = item.optString("country", "Germany").trim()
                val isoCountry = mapCountryToIso(rawCountry, rawName)
                val cleanName = cleanKoolChannelName(rawName)
                val categoryGroup = buildGroupTitle(isoCountry, cleanName, rawCountry)
                val streamUrl = "$baseHost/play/$id/index.m3u8"
                result.add(
                    KoolChannelItem(
                        id = id,
                        name = cleanName,
                        country = isoCountry,
                        groupTitle = categoryGroup,
                        streamUrl = streamUrl
                    )
                )
            }
            result.sortedWith(
                compareBy<KoolChannelItem> {
                    when (it.country) {
                        "DE" -> 0
                        "AT" -> 1
                        "CH" -> 2
                        else -> 3
                    }
                }.thenBy { it.groupTitle }.thenBy { it.name }
            )
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /**
     * Normalizes user-supplied kool.to URLs (e.g. "http://kool.to", "https://kool.to/")
     * to the public JSON channel catalog endpoint when used as an IPTV playlist source.
     */
    fun resolveCandidateCatalogUrls(rawUrl: String): List<String> {
        val trimmed = rawUrl.trim().removeSuffix("/")
        val lower = trimmed.lowercase()
        return if (lower.endsWith(".m3u") || lower.endsWith(".m3u8")) {
            listOf(trimmed, "http://kool.to/channels", "https://kool.to/channels", "http://kool.ws/channels").distinct()
        } else {
            listOf(
                "http://kool.to/channels",
                "https://kool.to/channels",
                "http://kool.ws/channels",
                "https://kool.ws/channels"
            )
        }
    }

    /**
     * Executes an HTTP request with multi-mirror fallback for kool.to playlist synchronization.
     */
    fun executeCatalogRequest(httpClient: OkHttpClient, rawUrl: String): Response? {
        val candidates = resolveCandidateCatalogUrls(rawUrl)
        var lastResponse: Response? = null
        for (targetUrl in candidates) {
            try {
                lastResponse?.close()
                val scheme = if (targetUrl.startsWith("https://", ignoreCase = true)) "https" else "http"
                val host = if (targetUrl.contains("kool.ws", ignoreCase = true)) "kool.ws" else "kool.to"
                val origin = "$scheme://$host"
                val req = Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", KOOL_USER_AGENT)
                    .header("Referer", "$origin/")
                    .header("Origin", origin)
                    .header("Accept", "application/json, audio/x-mpegurl, */*")
                    .build()
                val res = httpClient.newCall(req).execute()
                if (res.isSuccessful && res.body != null) {
                    return res
                }
                lastResponse = res
            } catch (_: Exception) {
            }
        }
        return lastResponse
    }

    /**
     * Inspects an InputStream; if it contains a kool.to JSON channel array (starts with '[' or '{'),
     * converts it transparently into an #EXTM3U stream so M3UParser can process it with full DACH prioritization.
     */
    fun wrapInputStreamIfJson(inputStream: InputStream): InputStream {
        val buffered = if (inputStream is BufferedInputStream) inputStream else BufferedInputStream(inputStream, 65536)
        buffered.mark(2048)
        val previewBytes = ByteArray(1024)
        val readCount = buffered.read(previewBytes)
        buffered.reset()
        if (readCount <= 0) return buffered

        val previewStr = String(previewBytes, 0, readCount, Charsets.UTF_8).trimStart()
        if (previewStr.startsWith("[") || (previewStr.startsWith("{") && previewStr.contains("\"country\""))) {
            return try {
                val fullJson = buffered.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val m3uContent = convertJsonCatalogToM3u(fullJson)
                ByteArrayInputStream(m3uContent.toByteArray(Charsets.UTF_8))
            } catch (_: Throwable) {
                ByteArrayInputStream("#EXTM3U\n".toByteArray(Charsets.UTF_8))
            }
        }
        return buffered
    }

    /**
     * Converts the public kool.to /channels JSON payload into an Extended M3U (#EXTM3U) string.
     * Uses unencrypted http://kool.to by default because kool.to live streams are served via HTTP.
     */
    fun convertJsonCatalogToM3u(jsonText: String, baseHost: String = "http://kool.to"): String {
        val trimmed = jsonText.trim()
        val array = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            trimmed.startsWith("{") -> {
                val obj = JSONObject(trimmed)
                obj.optJSONArray("channels") ?: obj.optJSONArray("data") ?: JSONArray()
            }
            else -> JSONArray()
        }

        val sb = StringBuilder(array.length() * 140 + 64)
        sb.append("#EXTM3U\n")

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val id = item.optString("id", "").ifBlank { item.optLong("id", -1L).takeIf { it >= 0 }?.toString() ?: "" }
            val rawName = item.optString("name", "").trim()
            if (id.isBlank() || rawName.isBlank()) continue

            val rawCountry = item.optString("country", "Germany").trim()
            val isoCountry = mapCountryToIso(rawCountry, rawName)
            val cleanName = cleanKoolChannelName(rawName)
            val categoryGroup = buildGroupTitle(isoCountry, cleanName, rawCountry)
            val streamUrl = "$baseHost/play/$id/index.m3u8"

            val escapedName = cleanName.replace("\"", "'")
            val escapedGroup = categoryGroup.replace("\"", "'")
            sb.append("#EXTINF:-1 tvg-id=\"kool_")
                .append(id)
                .append("\" tvg-name=\"")
                .append(escapedName)
                .append("\" tvg-country=\"")
                .append(isoCountry)
                .append("\" group-title=\"")
                .append(escapedGroup)
                .append("\",")
                .append(cleanName)
                .append('\n')
                .append("#EXTVLCOPT:http-referrer=")
                .append(baseHost)
                .append("/\n")
                .append("#EXTVLCOPT:http-user-agent=")
                .append(KOOL_USER_AGENT)
                .append('\n')
                .append(streamUrl)
                .append('\n')
        }

        return sb.toString()
    }

    private fun cleanKoolChannelName(rawName: String): String {
        return rawName.replace(SUFFIX_CLEAN_REGEX, "").trim()
    }

    private fun mapCountryToIso(rawCountry: String, channelName: String): String {
        val upperCountry = rawCountry.uppercase()
        val upperName = channelName.uppercase()

        if (upperName.startsWith("ORF ") || upperName.startsWith("ATV ") || upperName.contains("SERVUS TV AT") || upperName.contains("PULS 4") || upperName.contains("OE24")) {
            return "AT"
        }
        if (upperName.startsWith("SRF ") || upperName.startsWith("3+") || upperName.startsWith("4+") || upperName.contains("BLUE SPORT") || upperName.contains("TV24") || upperName.contains("TELEZÜRI")) {
            return "CH"
        }

        return when {
            upperCountry == "GERMANY" || upperCountry == "DE" || upperCountry == "DEUTSCHLAND" -> "DE"
            upperCountry == "AUSTRIA" || upperCountry == "AT" || upperCountry == "ÖSTERREICH" -> "AT"
            upperCountry == "SWITZERLAND" || upperCountry == "CH" || upperCountry == "SCHWEIZ" -> "CH"
            upperCountry == "UNITED KINGDOM" || upperCountry == "UK" || upperCountry == "GB" || upperCountry == "ENGLAND" -> "UK"
            upperCountry == "USA" || upperCountry == "UNITED STATES" || upperCountry == "US" -> "US"
            upperCountry == "FRANCE" || upperCountry == "FR" -> "FR"
            upperCountry == "ITALY" || upperCountry == "IT" -> "IT"
            upperCountry == "SPAIN" || upperCountry == "ES" -> "ES"
            upperCountry == "TURKEY" || upperCountry == "TR" -> "TR"
            upperCountry == "NETHERLANDS" || upperCountry == "NL" -> "NL"
            upperCountry == "POLAND" || upperCountry == "PL" -> "PL"
            upperCountry == "PORTUGAL" || upperCountry == "PT" -> "PT"
            upperCountry == "BALKAN" || upperCountry == "EX-YU" || upperCountry == "CROATIA" || upperCountry == "SERBIA" -> "HR"
            upperCountry == "ALBANIA" || upperCountry == "AL" -> "AL"
            upperCountry == "ROMANIA" || upperCountry == "RO" -> "RO"
            upperCountry == "RUSSIA" || upperCountry == "RU" -> "RU"
            upperCountry == "ARABIA" || upperCountry == "ARABIC" || upperCountry == "AR" -> "AR"
            upperCountry.length == 2 -> upperCountry
            else -> "DE"
        }
    }

    private fun buildGroupTitle(isoCountry: String, cleanName: String, rawCountry: String): String {
        val upper = cleanName.uppercase()
        return when (isoCountry) {
            "DE" -> when {
                upper.contains("DAZN") || upper.contains("SKY SPORT") || upper.contains("SPORT1") || upper.contains("EUROSPORT") || upper.contains("MAGENTA SPORT") || upper.contains("DYN") ->
                    "DE | Kool.to Sport & Bundesliga"
                upper.contains("SKY CINEMA") || upper.contains("SKY ATLANTIC") || upper.contains("SKY ONE") || upper.contains("SKY KRIMI") || upper.contains("SKY DOCUMENTARIES") || upper.contains("SKY NATURE") || upper.contains("WARNER") || upper.contains("13TH STREET") || upper.contains("SYFY") ->
                    "DE | Kool.to Sky & Pay-TV"
                upper.contains("KIKA") || upper.contains("SUPER RTL") || upper.contains("DISNEY") || upper.contains("NICK") || upper.contains("TOGGO") ->
                    "DE | Kool.to Kinder & Familie"
                upper.contains("N-TV") || upper.contains("NTV") || upper.contains("WELT") || upper.contains("PHOENIX") || upper.contains("TAGESSCHAU") || upper.contains("ZDF INFO") ->
                    "DE | Kool.to News & Doku"
                else -> "DE | Kool.to Vollprogramm"
            }
            "AT" -> "AT | Kool.to Österreich"
            "CH" -> "CH | Kool.to Schweiz"
            else -> "$isoCountry | Kool.to ${rawCountry.ifBlank { isoCountry }}"
        }
    }
}
