package com.example.data.radio

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * REST client for the public radio-browser.info API (no API key required).
 * Uses automatic mirror failover across official community servers.
 */
class RadioBrowserApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {
    companion object {
        private const val TAG = "RadioBrowserApiClient"
        private const val USER_AGENT = "MaDIPTVPrivat/5.5 (Android; https://www.radio-browser.info)"

        val MIRROR_HOSTS = listOf(
            "https://de1.api.radio-browser.info",
            "https://de2.api.radio-browser.info",
            "https://at1.api.radio-browser.info",
            "https://fi1.api.radio-browser.info",
            "https://all.api.radio-browser.info"
        )
    }

    @Volatile
    private var preferredHostIndex: Int = 0

    /**
     * Fetches Deutschrock stations + popular DACH stations (DE, AT, CH) for local Room caching.
     */
    suspend fun fetchSyncCatalog(
        onStageUpdate: ((String) -> Unit)? = null
    ): List<RadioStationEntity> = withContext(Dispatchers.IO) {
        val merged = LinkedHashMap<String, RadioStationEntity>()
        val now = System.currentTimeMillis()

        onStageUpdate?.invoke("Lade Deutschrock-Sender von radio-browser.info...")
        val deutschrockTag = fetchEndpoint(
            "/json/stations/search?tag=deutschrock&hidebroken=true&order=votes&reverse=true&limit=350",
            now,
            forceDeutschrock = true
        )
        val deutschrockName = fetchEndpoint(
            "/json/stations/search?name=deutschrock&hidebroken=true&order=votes&reverse=true&limit=250",
            now,
            forceDeutschrock = true
        )
        val germanRockTag = fetchEndpoint(
            "/json/stations/search?tag=german+rock&hidebroken=true&order=votes&reverse=true&limit=150",
            now,
            forceDeutschrock = true
        )
        for (station in deutschrockTag + deutschrockName + germanRockTag) {
            merged[station.stationUuid] = station.copy(isDeutschrock = true)
        }

        onStageUpdate?.invoke("Lade Top-Sender aus Deutschland (DE)...")
        val deStations = fetchEndpoint(
            "/json/stations/search?countrycode=DE&hidebroken=true&order=votes&reverse=true&limit=900",
            now
        )
        for (station in deStations) {
            val existing = merged[station.stationUuid]
            merged[station.stationUuid] = if (existing != null) {
                station.copy(isDeutschrock = existing.isDeutschrock || station.isDeutschrock)
            } else {
                station
            }
        }

        onStageUpdate?.invoke("Lade Top-Sender aus Österreich & Schweiz (AT/CH)...")
        val atStations = fetchEndpoint(
            "/json/stations/search?countrycode=AT&hidebroken=true&order=votes&reverse=true&limit=200",
            now
        )
        val chStations = fetchEndpoint(
            "/json/stations/search?countrycode=CH&hidebroken=true&order=votes&reverse=true&limit=200",
            now
        )
        for (station in atStations + chStations) {
            if (!merged.containsKey(station.stationUuid)) {
                merged[station.stationUuid] = station
            }
        }

        if (merged.isEmpty()) {
            throw IllegalStateException("radio-browser.info ist derzeit nicht erreichbar.")
        }

        merged.values.toList()
    }

    /**
     * Performs a live worldwide search directly on radio-browser.info by name and tag.
     */
    suspend fun searchWorldwide(
        query: String,
        onlyMp3: Boolean = false,
        limit: Int = 120
    ): List<RadioStationEntity> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isBlank()) return@withContext emptyList()

        val encoded = URLEncoder.encode(clean, StandardCharsets.UTF_8.name())
        val codecParam = if (onlyMp3) "&codec=MP3" else ""
        val now = System.currentTimeMillis()

        val byName = fetchEndpoint(
            "/json/stations/search?name=$encoded$codecParam&hidebroken=true&order=votes&reverse=true&limit=$limit",
            now
        )
        val byTag = if (byName.size < 40) {
            fetchEndpoint(
                "/json/stations/search?tag=$encoded$codecParam&hidebroken=true&order=votes&reverse=true&limit=${limit / 2}",
                now
            )
        } else {
            emptyList()
        }

        val combined = LinkedHashMap<String, RadioStationEntity>()
        for (st in byName + byTag) {
            if (onlyMp3 && !st.isMp3Stream()) continue
            val prev = combined[st.stationUuid]
            combined[st.stationUuid] = if (prev != null) {
                st.copy(isDeutschrock = prev.isDeutschrock || st.isDeutschrock)
            } else {
                st
            }
        }

        combined.values.sortedWith(
            compareByDescending<RadioStationEntity> { it.isDeutschrock }
                .thenBy {
                    when (it.countryCode) {
                        "DE" -> 0
                        "AT" -> 1
                        "CH" -> 2
                        else -> 3
                    }
                }
                .thenByDescending { it.votes }
                .thenBy { it.name.lowercase() }
        )
    }

    /**
     * Notifies radio-browser.info of a station click (recommended by API guidelines).
     */
    suspend fun reportStationClick(stationUuid: String) = withContext(Dispatchers.IO) {
        if (stationUuid.isBlank()) return@withContext
        val host = MIRROR_HOSTS[preferredHostIndex % MIRROR_HOSTS.size]
        val req = Request.Builder()
            .url("$host/json/url/$stationUuid")
            .header("User-Agent", USER_AGENT)
            .build()
        try {
            client.newCall(req).execute().use { /* fire-and-forget */ }
        } catch (_: Exception) {
            // Non-critical telemetry ping
        }
    }

    private fun fetchEndpoint(
        pathAndQuery: String,
        syncTimestamp: Long,
        forceDeutschrock: Boolean = false
    ): List<RadioStationEntity> {
        var lastError: Throwable? = null
        val totalHosts = MIRROR_HOSTS.size

        for (attempt in 0 until totalHosts) {
            val hostIdx = (preferredHostIndex + attempt) % totalHosts
            val url = "${MIRROR_HOSTS[hostIdx]}$pathAndQuery"
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        lastError = IllegalStateException("HTTP ${response.code} from $url")
                        return@use
                    }
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) {
                        return emptyList()
                    }
                    preferredHostIndex = hostIdx
                    return parseStationsJson(body, syncTimestamp, forceDeutschrock)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Mirror ${MIRROR_HOSTS[hostIdx]} failed: ${e.message}")
                lastError = e
            }
        }

        if (lastError != null) {
            Log.e(TAG, "All radio-browser.info mirrors failed for $pathAndQuery: ${lastError.message}")
        }
        return emptyList()
    }

    internal fun parseStationsJson(
        jsonBody: String,
        syncTimestamp: Long,
        forceDeutschrock: Boolean = false
    ): List<RadioStationEntity> {
        val result = ArrayList<RadioStationEntity>()
        val array = JSONArray(jsonBody)
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            parseSingleStation(obj, syncTimestamp, forceDeutschrock)?.let { result.add(it) }
        }
        return result
    }

    private fun parseSingleStation(
        obj: JSONObject,
        syncTimestamp: Long,
        forceDeutschrock: Boolean
    ): RadioStationEntity? {
        val uuid = obj.optString("stationuuid", "").trim()
        val rawName = obj.optString("name", "").trim()
        val resolvedUrl = obj.optString("url_resolved", "").trim()
        val fallbackUrl = obj.optString("url", "").trim()
        val streamUrl = resolvedUrl.ifBlank { fallbackUrl }

        if (uuid.isBlank() || rawName.isBlank() || streamUrl.isBlank()) return null
        if (!streamUrl.startsWith("http://", ignoreCase = true) && !streamUrl.startsWith("https://", ignoreCase = true)) {
            return null
        }

        val cleanName = rawName.replace(Regex("\\s+"), " ").trim()
        val tags = obj.optString("tags", "").trim()
        val countryCode = obj.optString("countrycode", "").trim().uppercase().ifBlank { "INT" }
        val country = obj.optString("country", "").trim()
        val language = obj.optString("language", "").trim()
        val codec = obj.optString("codec", "").trim().uppercase().ifBlank {
            when {
                streamUrl.contains(".mp3", ignoreCase = true) -> "MP3"
                streamUrl.contains(".aac", ignoreCase = true) -> "AAC"
                streamUrl.contains(".ogg", ignoreCase = true) -> "OGG"
                else -> "MP3"
            }
        }
        val bitrate = obj.optInt("bitrate", 0).coerceAtLeast(0)
        val votes = obj.optInt("votes", 0).coerceAtLeast(0)
        val clickCount = obj.optInt("clickcount", 0).coerceAtLeast(0)
        val favicon = obj.optString("favicon", "").trim()
        val homepage = obj.optString("homepage", "").trim()
        val isDeutschrock = forceDeutschrock || RadioStationEntity.detectDeutschrock(cleanName, tags)

        return RadioStationEntity(
            stationUuid = uuid,
            name = cleanName,
            streamUrl = streamUrl,
            homepage = homepage,
            favicon = favicon,
            countryCode = countryCode,
            country = country,
            language = language,
            tags = tags,
            codec = codec,
            bitrate = bitrate,
            votes = votes,
            clickCount = clickCount,
            isDeutschrock = isDeutschrock,
            isFavorite = false,
            lastSyncedAt = syncTimestamp
        )
    }
}
