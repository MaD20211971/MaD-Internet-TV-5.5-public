package com.example.androidtvlight.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.androidtvlight.data.epg.TvEpgProvider
import com.example.androidtvlight.domain.model.AmbientLightScene
import com.example.androidtvlight.domain.model.ChannelEpgSchedule
import com.example.androidtvlight.domain.model.QuickLaunchTile
import com.example.androidtvlight.domain.model.TvChannelItem
import com.example.androidtvlight.domain.model.TvLightSettings
import com.example.androidtvlight.domain.model.TvRubrik
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "android_tv_light_prefs")

class TvLightRepository(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val epgProvider = TvEpgProvider(ioDispatcher)

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val m3u8Pattern: Pattern = Pattern.compile(
        """https?://[^"'\s<>]+\.m3u8[^"'\s<>]*""",
        Pattern.CASE_INSENSITIVE
    )

    private object Keys {
        val INSTALLATION_PIN_VERIFIED = booleanPreferencesKey("installation_pin_verified")
        val HIGH_CONTRAST = booleanPreferencesKey("high_contrast_mode")
        val LARGE_TEXT = booleanPreferencesKey("large_text_mode")
        val SHOW_REMOTE_OVERLAY = booleanPreferencesKey("show_virtual_remote_overlay")
        val BACKGROUND_PLAYBACK = booleanPreferencesKey("background_playback_enabled")
        val PREVENT_STANDBY = booleanPreferencesKey("prevent_standby_enabled")
        val EPG_AUTO_UPDATE_ENABLED = booleanPreferencesKey("epg_auto_update_enabled")
        val EPG_AUTO_UPDATE_INTERVAL_MINUTES = intPreferencesKey("epg_auto_update_interval_minutes")
        val ACTIVE_SCENE_ID = stringPreferencesKey("active_ambient_scene_id")
        val AMBIENT_BRIGHTNESS = floatPreferencesKey("ambient_brightness")
        val AMBIENT_PULSE = booleanPreferencesKey("ambient_dynamic_pulse")
        val WARM_FILTER = floatPreferencesKey("warm_eye_care_filter")
        val SLEEP_TIMER = intPreferencesKey("sleep_timer_minutes")
        val FAVORITE_CHANNELS = stringSetPreferencesKey("favorite_channel_ids")
        val LAST_CHANNEL_ID = stringPreferencesKey("last_selected_channel_id")
    }

    val settingsFlow: Flow<TvLightSettings> = context.dataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs ->
            TvLightSettings(
                installationPinVerified = prefs[Keys.INSTALLATION_PIN_VERIFIED] ?: false,
                highContrastMode = prefs[Keys.HIGH_CONTRAST] ?: false,
                largeTextMode = prefs[Keys.LARGE_TEXT] ?: false,
                showVirtualRemoteOverlay = prefs[Keys.SHOW_REMOTE_OVERLAY] ?: false,
                backgroundPlaybackEnabled = prefs[Keys.BACKGROUND_PLAYBACK] ?: true,
                preventStandbyEnabled = prefs[Keys.PREVENT_STANDBY] ?: true,
                epgAutoUpdateEnabled = prefs[Keys.EPG_AUTO_UPDATE_ENABLED] ?: true,
                epgAutoUpdateIntervalMinutes = prefs[Keys.EPG_AUTO_UPDATE_INTERVAL_MINUTES] ?: 15,
                activeAmbientSceneId = prefs[Keys.ACTIVE_SCENE_ID] ?: "deep_ocean_bias",
                ambientBrightness = prefs[Keys.AMBIENT_BRIGHTNESS] ?: 0.82f,
                ambientDynamicPulse = prefs[Keys.AMBIENT_PULSE] ?: true,
                warmEyeCareFilter = prefs[Keys.WARM_FILTER] ?: 0.15f,
                sleepTimerMinutes = prefs[Keys.SLEEP_TIMER] ?: 0,
                favoriteChannelIds = prefs[Keys.FAVORITE_CHANNELS]
                    ?: setOf("de_ard", "de_zdf", "de_rtl", "at_orf1", "at_servus_tv"),
                lastSelectedChannelId = prefs[Keys.LAST_CHANNEL_ID] ?: "de_ard"
            )
        }

    suspend fun syncEpgSchedules(
        channels: List<TvChannelItem>,
        performNetworkFetch: Boolean = true
    ): List<ChannelEpgSchedule> =
        epgProvider.syncLiveEpgAndBuildSchedules(
            channels = channels,
            nowMillis = System.currentTimeMillis(),
            performNetworkFetch = performNetworkFetch
        )

    fun getLastEpgSyncFormatted(): String =
        epgProvider.formatTimestamp(epgProvider.lastSyncTimestampMillis)

    suspend fun updateEpgAutoUpdateEnabled(enabled: Boolean): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            context.dataStore.edit { it[Keys.EPG_AUTO_UPDATE_ENABLED] = enabled }
        }
    }

    suspend fun updateEpgAutoUpdateIntervalMinutes(minutes: Int): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            context.dataStore.edit {
                it[Keys.EPG_AUTO_UPDATE_INTERVAL_MINUTES] = minutes.coerceIn(5, 120)
            }
        }
    }

    suspend fun updateInstallationPinVerified(verified: Boolean): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            context.dataStore.edit { it[Keys.INSTALLATION_PIN_VERIFIED] = verified }
        }
    }

    suspend fun updateBackgroundPlayback(enabled: Boolean): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            context.dataStore.edit { it[Keys.BACKGROUND_PLAYBACK] = enabled }
        }
    }

    suspend fun updatePreventStandby(enabled: Boolean): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            context.dataStore.edit { it[Keys.PREVENT_STANDBY] = enabled }
        }
    }

    suspend fun updateHighContrast(enabled: Boolean): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            context.dataStore.edit { it[Keys.HIGH_CONTRAST] = enabled }
        }
    }

    suspend fun updateLargeText(enabled: Boolean): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            context.dataStore.edit { it[Keys.LARGE_TEXT] = enabled }
        }
    }

    suspend fun updateVirtualRemoteOverlay(visible: Boolean): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            context.dataStore.edit { it[Keys.SHOW_REMOTE_OVERLAY] = visible }
        }
    }

    suspend fun selectAmbientScene(sceneId: String): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            context.dataStore.edit { it[Keys.ACTIVE_SCENE_ID] = sceneId }
        }
    }

    suspend fun updateAmbientBrightness(value: Float): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            context.dataStore.edit { it[Keys.AMBIENT_BRIGHTNESS] = value.coerceIn(0.15f, 1.0f) }
        }
    }

    suspend fun updateAmbientPulse(enabled: Boolean): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            context.dataStore.edit { it[Keys.AMBIENT_PULSE] = enabled }
        }
    }

    suspend fun updateWarmEyeCareFilter(value: Float): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            context.dataStore.edit { it[Keys.WARM_FILTER] = value.coerceIn(0f, 0.7f) }
        }
    }

    suspend fun updateSleepTimer(minutes: Int): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            context.dataStore.edit { it[Keys.SLEEP_TIMER] = minutes.coerceAtLeast(0) }
        }
    }

    suspend fun toggleFavoriteChannel(channelId: String): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            context.dataStore.edit { prefs ->
                val current = prefs[Keys.FAVORITE_CHANNELS]
                    ?: setOf("de_ard", "de_zdf", "de_rtl", "at_orf1", "at_servus_tv")
                prefs[Keys.FAVORITE_CHANNELS] = if (current.contains(channelId)) {
                    current - channelId
                } else {
                    current + channelId
                }
            }
        }
    }

    suspend fun setLastSelectedChannel(channelId: String): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            context.dataStore.edit { it[Keys.LAST_CHANNEL_ID] = channelId }
        }
    }

    /**
     * Dynamically resolves live `.m3u8` stream URLs for both 2ix2.com channels and
     * TV2 (`https://oha.to/mediaurl.json` / `oha.to`) channels.
     */
    suspend fun resolveLiveStreamUrls(channel: TvChannelItem): List<String> = withContext(ioDispatcher) {
        val discovered = mutableListOf<String>()

        // 1. If this is a DE TV2 channel, resolve via mediahubmx-resolve.json & mediaurl.json
        if (channel.rubrik == TvRubrik.TV2_OHA ||
            channel.primaryStreamUrl.contains("oha.to", ignoreCase = true) ||
            channel.pageUrl.contains("oha.to", ignoreCase = true)
        ) {
            val candidatePlayUrl = channel.primaryStreamUrl
            if (candidatePlayUrl.isNotBlank()) {
                val resolveEndpoints = listOf(
                    "https://oha.to/mediahubmx-resolve.json",
                    "https://kool.to/mediahubmx-resolve.json",
                    "https://vavoo.to/mediahubmx-resolve.json"
                )
                for (endpoint in resolveEndpoints) {
                    runCatching {
                        val payload = JSONObject().apply {
                            put("language", "de")
                            put("region", "DE")
                            put("url", candidatePlayUrl)
                            put("clientVersion", "3.0.2")
                        }.toString()
                        val req = Request.Builder()
                            .url(endpoint)
                            .header("User-Agent", "MediaHubMX/2")
                            .header("Accept", "application/json")
                            .header("Referer", "https://oha.to/")
                            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                            .build()
                        okHttpClient.newCall(req).execute().use { resp ->
                            if (resp.isSuccessful) {
                                val text = resp.body?.string().orEmpty().trim()
                                if (text.isNotEmpty()) {
                                    when (val parsed = JSONTokener(text).nextValue()) {
                                        is JSONArray -> {
                                            for (i in 0 until parsed.length()) {
                                                val u = parsed.optJSONObject(i)?.optString("url").orEmpty()
                                                if (u.startsWith("http") && !discovered.contains(u)) {
                                                    discovered.add(u)
                                                }
                                            }
                                        }
                                        is JSONObject -> {
                                            val u = parsed.optString("url").orEmpty()
                                            if (u.startsWith("http") && !discovered.contains(u)) {
                                                discovered.add(u)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (discovered.isNotEmpty()) break
                }
            }
        }

        // 2. Also inspect channel.pageUrl for .m3u8 links
        val refererHeader = when {
            channel.rubrik == TvRubrik.TV3_LIVE || channel.pageUrl.contains("tele24seven.com", ignoreCase = true) ->
                "https://tele24seven.com/"
            channel.rubrik == TvRubrik.TV2_OHA || channel.pageUrl.contains("oha.to", ignoreCase = true) ->
                "https://oha.to/"
            else ->
                "https://www.2ix2.com/"
        }

        val shouldInspectPageHtml = !channel.pageUrl.contains("#country-channels", ignoreCase = true)
        if (shouldInspectPageHtml) {
            runCatching {
                val request = Request.Builder()
                    .url(channel.pageUrl.substringBefore('#'))
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                    )
                    .header("Referer", refererHeader)
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        val matcher = m3u8Pattern.matcher(body)
                        while (matcher.find()) {
                            val url = matcher.group()?.trim()
                            if (!url.isNullOrBlank() && !discovered.contains(url)) {
                                discovered.add(url)
                            }
                        }

                        // If page embeds a player iframe, also inspect the first iframe src for .m3u8
                        if (discovered.isEmpty() && body.contains("<iframe", ignoreCase = true)) {
                            val iframePattern = Pattern.compile(
                                """<iframe[^>]+src=["']([^"']+)["']""",
                                Pattern.CASE_INSENSITIVE
                            )
                            val iframeMatcher = iframePattern.matcher(body)
                            while (iframeMatcher.find() && discovered.size < 4) {
                                val rawSrc = iframeMatcher.group(1)?.trim().orEmpty()
                                val iframeUrl = when {
                                    rawSrc.startsWith("http://") || rawSrc.startsWith("https://") -> rawSrc
                                    rawSrc.startsWith("//") -> "https:$rawSrc"
                                    rawSrc.startsWith("/") -> "https://tele24seven.com$rawSrc"
                                    else -> ""
                                }
                                if (iframeUrl.isNotBlank()) {
                                    runCatching {
                                        val iframeReq = Request.Builder()
                                            .url(iframeUrl)
                                            .header(
                                                "User-Agent",
                                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                                            )
                                            .header("Referer", channel.pageUrl.substringBefore('#'))
                                            .build()
                                        okHttpClient.newCall(iframeReq).execute().use { iframeResp ->
                                            if (iframeResp.isSuccessful) {
                                                val iframeBody = iframeResp.body?.string().orEmpty()
                                                val m2 = m3u8Pattern.matcher(iframeBody)
                                                while (m2.find()) {
                                                    val u2 = m2.group()?.trim()
                                                    if (!u2.isNullOrBlank() && !discovered.contains(u2)) {
                                                        discovered.add(u2)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        val combined = buildList {
            addAll(discovered)
            if (channel.primaryStreamUrl.isNotBlank()) add(channel.primaryStreamUrl)
            addAll(channel.fallbackStreamUrls)
        }.distinct()
        combined
    }

    /**
     * Fetches and parses live TV channels from `https://oha.to/mediaurl.json` (and the associated
     * `https://oha.to/channels` catalog referenced by the oha.to MediaURL bundle) for the TV2 menu entry.
     */
    suspend fun fetchTv2OhaChannels(): List<TvChannelItem> = withContext(ioDispatcher) {
        val baseDeAt = getInitialDeAtChannels()
        val parsedItems = mutableListOf<TvChannelItem>()
        var nextChannelNumber = 201

        // 1. Fetch https://oha.to/mediaurl.json directly
        val mediaUrlEndpoints = mutableListOf(
            "https://oha.to/mediaurl.json",
            "https://oha.to/channels"
        )

        val visitedEndpoints = HashSet<String>()
        var idx = 0
        while (idx < mediaUrlEndpoints.size && parsedItems.size < 120) {
            val endpointUrl = mediaUrlEndpoints[idx++]
            if (!visitedEndpoints.add(endpointUrl)) continue

            runCatching {
                val req = Request.Builder()
                    .url(endpointUrl)
                    .header("User-Agent", "MediaHubMX/2")
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Referer", "https://oha.to/")
                    .build()

                okHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body?.string().orEmpty().trim()
                    if (body.isEmpty()) return@use

                    if (body.startsWith("{") || body.startsWith("[")) {
                        val root = JSONTokener(body).nextValue()
                        extractOhaJsonChannels(
                            node = root,
                            baseDeAt = baseDeAt,
                            outList = parsedItems,
                            discoveredCatalogUrls = mediaUrlEndpoints,
                            startNumber = nextChannelNumber
                        )
                        nextChannelNumber = 201 + parsedItems.size
                    } else if (body.contains("#EXTINF") || body.contains(".m3u8")) {
                        val m = m3u8Pattern.matcher(body)
                        while (m.find() && parsedItems.size < 120) {
                            val streamUrl = m.group()?.trim().orEmpty()
                            if (streamUrl.isNotBlank()) {
                                val num = nextChannelNumber++
                                parsedItems.add(
                                    TvChannelItem(
                                        id = "tv2_m3u8_$num",
                                        number = num,
                                        name = "DE TV2 Stream #$num",
                                        rubrik = TvRubrik.TV2_OHA,
                                        logoUrl = "https://www.2ix2.com/wp-content/uploads/2020/06/2.png",
                                        pageUrl = "https://oha.to/mediaurl.json",
                                        primaryStreamUrl = streamUrl,
                                        fallbackStreamUrls = listOf(
                                            "https://daserste-live.ard-mcdn.de/daserste/live/hls/de/master.m3u8"
                                        ),
                                        currentShow = "DE TV2 • Live-Stream",
                                        qualityBadge = "DE TV2",
                                        accentHex = 0xFF38BDF8
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        if (parsedItems.isNotEmpty()) {
            parsedItems.distinctBy { it.name.uppercase() }
        } else {
            getInitialTv2OhaChannels(baseDeAt)
        }
    }

    /**
     * Fetches and synchronizes all 531 German TV channels for TV3 from
     * `https://tele24seven.com/?country=DE#country-channels`, playable directly inside
     * the internal ExoPlayer without exposing the source URL in any user-facing UI text.
     */
    suspend fun fetchTv3LiveChannels(): List<TvChannelItem> = withContext(ioDispatcher) {
        val baseDeAt = getInitialDeAtChannels()
        Tv3CatalogProvider.fetchLive531GermanChannels(
            okHttpClient = okHttpClient,
            m3u8Pattern = m3u8Pattern,
            baseDeAt = baseDeAt
        )
    }

    private fun extractOhaJsonChannels(
        node: Any?,
        baseDeAt: List<TvChannelItem>,
        outList: MutableList<TvChannelItem>,
        discoveredCatalogUrls: MutableList<String>,
        startNumber: Int
    ) {
        if (outList.size >= 120 || node == null) return
        when (node) {
            is JSONArray -> {
                // First pass: prioritize German / Austrian / Swiss channels if country field exists
                val prioritized = mutableListOf<JSONObject>()
                val others = mutableListOf<JSONObject>()
                for (i in 0 until node.length()) {
                    val obj = node.optJSONObject(i) ?: continue
                    val country = obj.optString("country", "").uppercase()
                    val group = obj.optString("group", "").uppercase()
                    if (country.contains("GERMAN") || country.contains("DE") ||
                        country.contains("AUSTRIA") || country.contains("AT") ||
                        country.contains("SWITZ") || country.contains("CH") ||
                        group.contains("DE") || group.contains("AT") ||
                        (country.isEmpty() && group.isEmpty())
                    ) {
                        prioritized.add(obj)
                    } else {
                        others.add(obj)
                    }
                }
                val combined = prioritized + others.take(30)
                for (obj in combined) {
                    if (outList.size >= 120) break
                    parseSingleOhaChannelObject(obj, baseDeAt, outList, discoveredCatalogUrls, startNumber + outList.size)
                }
            }
            is JSONObject -> {
                // Check if this object itself is a playable channel item
                parseSingleOhaChannelObject(node, baseDeAt, outList, discoveredCatalogUrls, startNumber + outList.size)

                // Also traverse child arrays/objects (e.g. "channels", "items", "streams", "data", "iptv", "media")
                val keys = node.keys()
                while (keys.hasNext() && outList.size < 120) {
                    val key = keys.next()
                    val child = node.opt(key)
                    if (child is JSONArray || child is JSONObject) {
                        extractOhaJsonChannels(child, baseDeAt, outList, discoveredCatalogUrls, startNumber + outList.size)
                    } else if (child is String && child.startsWith("https://oha.to/") && !discoveredCatalogUrls.contains(child)) {
                        discoveredCatalogUrls.add(child)
                    }
                }
            }
        }
    }

    private fun parseSingleOhaChannelObject(
        obj: JSONObject,
        baseDeAt: List<TvChannelItem>,
        outList: MutableList<TvChannelItem>,
        discoveredCatalogUrls: MutableList<String>,
        channelNumber: Int
    ) {
        val rawName = obj.optString("name")
            .ifBlank { obj.optString("title") }
            .ifBlank { obj.optString("label") }
            .trim()
        if (rawName.isBlank()) return

        // Ignore pure addon metadata headers like {"name": "OHA", "id": "org.oha"} that have no stream or numeric id
        val idStr = obj.opt("id")?.toString()?.trim().orEmpty()
        val directUrl = obj.optString("url")
            .ifBlank { obj.optString("stream") }
            .ifBlank { obj.optString("mediaurl") }
            .ifBlank { obj.optString("src") }
            .ifBlank { obj.optString("file") }
            .ifBlank { obj.optString("hls") }
            .trim()

        val isNumericChannelId = idStr.isNotEmpty() && idStr.all { it.isDigit() }
        if (directUrl.isBlank() && !isNumericChannelId) {
            return
        }

        val cleanedName = rawName
            .replace(Regex("\\s*\\([a-zA-Z0-9]+\\)$"), "")
            .replace(Regex("\\s+\\.[a-zA-Z0-9]$"), "")
            .trim()
            .ifBlank { rawName }

        val matchedExisting = findMatchingDeAtChannel(cleanedName, baseDeAt)

        val primaryStream = when {
            directUrl.startsWith("http://") || directUrl.startsWith("https://") -> directUrl
            isNumericChannelId -> "https://oha.to/play/$idStr/index.m3u8"
            else -> matchedExisting?.primaryStreamUrl.orEmpty()
        }
        if (primaryStream.isBlank()) return

        val fallbacks = buildList {
            if (isNumericChannelId) {
                add("https://oha.to/play/$idStr/index.m3u8")
                add("https://kool.to/play/$idStr/index.m3u8")
                add("https://vavoo.to/play/$idStr/index.m3u8")
            }
            if (matchedExisting != null) {
                if (matchedExisting.primaryStreamUrl.isNotBlank()) add(matchedExisting.primaryStreamUrl)
                addAll(matchedExisting.fallbackStreamUrls)
            }
            add("https://daserste-live.ard-mcdn.de/daserste/live/hls/de/master.m3u8")
            add("https://tagesschau.akamaized.net/hls/live/2020115/tagesschau/tagesschau_1/master.m3u8")
        }.distinct()

        val logo = obj.optString("logo")
            .ifBlank { obj.optString("icon") }
            .ifBlank { obj.optString("poster") }
            .ifBlank { matchedExisting?.logoUrl.orEmpty() }
            .ifBlank { "https://www.2ix2.com/wp-content/uploads/2020/06/2.png" }

        val countryBadge = obj.optString("country")
            .ifBlank { obj.optString("group") }
            .ifBlank { "DE" }

        val safeId = "tv2_oha_${idStr.ifBlank { cleanedName.lowercase().replace(Regex("[^a-z0-9]"), "_") }}_${outList.size}"

        outList.add(
            TvChannelItem(
                id = safeId,
                number = channelNumber,
                name = cleanedName,
                rubrik = TvRubrik.TV2_OHA,
                logoUrl = logo,
                pageUrl = "https://oha.to/mediaurl.json",
                primaryStreamUrl = primaryStream,
                fallbackStreamUrls = fallbacks,
                currentShow = "DE TV2 • $countryBadge",
                qualityBadge = "DE TV2",
                accentHex = matchedExisting?.accentHex ?: 0xFF38BDF8
            )
        )
    }

    private fun findMatchingDeAtChannel(name: String, baseDeAt: List<TvChannelItem>): TvChannelItem? {
        val norm = name.uppercase()
            .replace("FULL HD", "")
            .replace("FHD", "")
            .replace("UHD", "")
            .replace("HD", "")
            .replace("SD", "")
            .replace("DE", "")
            .replace("AT", "")
            .replace(Regex("[^A-Z0-9]"), "")
            .trim()
        if (norm.isEmpty()) return null

        return baseDeAt.firstOrNull { ch ->
            val chNorm = ch.name.uppercase().replace(Regex("[^A-Z0-9]"), "")
            norm == chNorm ||
                (norm.contains("DASERSTE") && chNorm == "ARD") ||
                (norm.contains("PROSIEBEN") && chNorm.contains("PRO7")) ||
                (norm.contains("KABELEINS") && chNorm.contains("KABEL1")) ||
                (norm.contains("RTLZWEI") && chNorm == "RTL2") ||
                (chNorm.length >= 3 && norm.contains(chNorm))
        }
    }

    fun getAmbientScenes(): List<AmbientLightScene> = listOf(
        AmbientLightScene(
            id = "deep_ocean_bias",
            name = "Ozeanblau 6500K Bias-Light",
            subtitle = "Augenschonendes Referenz-Hintergrundlicht für dunkle Heimkino-Räume",
            primaryColor = Color(0xFF0A2647),
            secondaryColor = Color(0xFF1E56A0),
            tertiaryColor = Color(0xFF38BDF8),
            kelvinLabel = "6500K ISF-Blau",
            recommendedBrightness = 0.82f,
            pulseSpeed = 1.0f
        ),
        AmbientLightScene(
            id = "nordic_aurora",
            name = "Nordlicht Cyan & Indigo",
            subtitle = "Sanft fließendes Polarlicht mit hohem Kontrast für entspannte TV-Abende",
            primaryColor = Color(0xFF091E3A),
            secondaryColor = Color(0xFF0F766E),
            tertiaryColor = Color(0xFF2DD4BF),
            kelvinLabel = "5800K Aurora",
            recommendedBrightness = 0.78f,
            pulseSpeed = 1.3f
        ),
        AmbientLightScene(
            id = "midnight_lounge",
            name = "Mitternacht Saphir",
            subtitle = "Tiefes Königsblau mit dezenter Randaufhellung ohne Blendwirkung",
            primaryColor = Color(0xFF050E1D),
            secondaryColor = Color(0xFF172E54),
            tertiaryColor = Color(0xFF60A5FA),
            kelvinLabel = "5000K Saphir",
            recommendedBrightness = 0.65f,
            pulseSpeed = 0.7f
        ),
        AmbientLightScene(
            id = "sunset_horizon",
            name = "Abenddämmerung & Marine",
            subtitle = "Warmer Bernstein-Akzent auf dunkelblauem Horizont zur Blaulichtreduktion",
            primaryColor = Color(0xFF0B192C),
            secondaryColor = Color(0xFF1E3E62),
            tertiaryColor = Color(0xFFF59E0B),
            kelvinLabel = "3400K Warm-Blau",
            recommendedBrightness = 0.74f,
            pulseSpeed = 0.9f
        ),
        AmbientLightScene(
            id = "studio_focus",
            name = "Kristallklar High-Contrast",
            subtitle = "Maximale Lesbarkeit und Konturenschärfe für Sport- und Nachrichtensendungen",
            primaryColor = Color(0xFF081B33),
            secondaryColor = Color(0xFF1D4ED8),
            tertiaryColor = Color(0xFF7DD3FC),
            kelvinLabel = "7000K Tageslicht",
            recommendedBrightness = 0.92f,
            pulseSpeed = 1.1f
        )
    )

    /**
     * All 47 German TV channels from https://www.2ix2.com/category/deutsche-tv-sender/
     * and all 11 Austrian TV channels from https://www.2ix2.com/category/oesterreichische-tv/
     * including their original station logo URLs from 2ix2.com and HLS stream endpoints.
     */
    fun getInitialChannels(): List<TvChannelItem> {
        val publicFallbacksDe = listOf(
            "https://daserste-live.ard-mcdn.de/daserste/live/hls/de/master.m3u8",
            "https://tagesschau.akamaized.net/hls/live/2020115/tagesschau/tagesschau_1/master.m3u8",
            "https://s6.hopslan.com/ardz/index.m3u8"
        )
        val publicFallbacksAt = listOf(
            "https://s6.hopslan.com/orf11/index.m3u8",
            "https://s6.hopslan.com/servuz1/index.m3u8",
            "https://daserste-live.ard-mcdn.de/daserste/live/hls/de/master.m3u8"
        )

        val deChannels = listOf(
            TvChannelItem(
                id = "de_ard",
                number = 1,
                name = "ARD",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://www.2ix2.com/wp-content/uploads/2020/06/2.png",
                pageUrl = "https://www.2ix2.com/ard-de/",
                primaryStreamUrl = "https://s6.hopslan.com/ardz/index.m3u8",
                fallbackStreamUrls = listOf(
                    "https://daserste-live.ard-mcdn.de/daserste/live/hls/de/master.m3u8",
                    "https://tagesschau.akamaized.net/hls/live/2020115/tagesschau/tagesschau_1/master.m3u8"
                ),
                currentShow = "Das Erste • Live-Hauptprogramm",
                qualityBadge = "FULL HD",
                accentHex = 0xFF38BDF8
            ),
            TvChannelItem(
                id = "de_zdf",
                number = 2,
                name = "ZDF",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://2ix2.com/wp-content/uploads/2019/09/5.png",
                pageUrl = "https://www.2ix2.com/zdf-de/",
                primaryStreamUrl = "https://s6.hopslan.com/zdfX1/index.m3u8",
                fallbackStreamUrls = listOf(
                    "https://zdf-hls-15.akamaized.net/hls/live/2016498/de/high/master.m3u8",
                    "https://daserste-live.ard-mcdn.de/daserste/live/hls/de/master.m3u8"
                ),
                currentShow = "Zweites Deutsches Fernsehen • Live",
                qualityBadge = "HD 50FPS",
                accentHex = 0xFFFACC15
            ),
            TvChannelItem(
                id = "de_rtl",
                number = 3,
                name = "RTL",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://www.2ix2.com/wp-content/uploads/2018/02/1.png",
                pageUrl = "https://www.2ix2.com/rtl-live/",
                primaryStreamUrl = "https://s6.hopslan.com/rtltz1/index.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "RTL Live-Programm & Unterhaltung",
                qualityBadge = "LIVE HD",
                accentHex = 0xFFFB7185
            ),
            TvChannelItem(
                id = "de_pro7",
                number = 4,
                name = "Pro7",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://www.2ix2.com/wp-content/uploads/2018/09/pro7-logo.png",
                pageUrl = "https://www.2ix2.com/pro7/",
                primaryStreamUrl = "https://s6.hopslan.com/pro7z/index.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "ProSieben Blockbuster & Shows",
                qualityBadge = "LIVE HD",
                accentHex = 0xFFEF4444
            ),
            TvChannelItem(
                id = "de_sat1",
                number = 5,
                name = "Sat1",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://www.2ix2.com/wp-content/uploads/2018/09/sat1-logo.png",
                pageUrl = "https://www.2ix2.com/sat-1/",
                primaryStreamUrl = "https://s6.hopslan.com/sat1z/index.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "SAT.1 Live-Stream & Serien",
                qualityBadge = "LIVE HD",
                accentHex = 0xFF38BDF8
            ),
            TvChannelItem(
                id = "de_vox",
                number = 6,
                name = "VOX",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://www.2ix2.com/wp-content/uploads/2018/02/3.png",
                pageUrl = "https://www.2ix2.com/vox/",
                primaryStreamUrl = "https://s6.hopslan.com/voxz/index.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "VOX Doku-Soaps & Filme",
                qualityBadge = "LIVE HD",
                accentHex = 0xFFF43F5E
            ),
            TvChannelItem(
                id = "de_rtl2",
                number = 7,
                name = "RTL2",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://www.2ix2.com/wp-content/uploads/2018/02/2.png",
                pageUrl = "https://www.2ix2.com/rtl2-live/",
                primaryStreamUrl = "https://s6.hopslan.com/rtl2q/index.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "RTLZWEI Reality & Entertainment",
                qualityBadge = "LIVE HD",
                accentHex = 0xFF60A5FA
            ),
            TvChannelItem(
                id = "de_kabel1",
                number = 8,
                name = "Kabel 1",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://www.2ix2.com/wp-content/uploads/2018/09/kabel1-logo.png",
                pageUrl = "https://www.2ix2.com/kabel-1/",
                primaryStreamUrl = "https://s6.hopslan.com/kabel1z/index.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "Kabel Eins Klassiker & Reportagen",
                qualityBadge = "LIVE HD",
                accentHex = 0xFFF59E0B
            ),
            TvChannelItem(
                id = "de_ntv",
                number = 9,
                name = "N-TV",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://www.2ix2.com/wp-content/uploads/2018/02/5.png",
                pageUrl = "https://www.2ix2.com/n-tv-live/",
                primaryStreamUrl = "https://s6.hopslan.com/ntvX/index.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "n-tv Live Nachrichten & Börse",
                qualityBadge = "NEWS HD",
                accentHex = 0xFF38BDF8
            ),
            TvChannelItem(
                id = "de_welt",
                number = 10,
                name = "WELT",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://2ix2.com/wp-content/uploads/2020/06/13.png",
                pageUrl = "https://www.2ix2.com/welt/",
                primaryStreamUrl = "https://s6.hopslan.com/n24X/index.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "WELT Nachrichten & Dokumentation",
                qualityBadge = "NEWS HD",
                accentHex = 0xFF38BDF8
            ),
            TvChannelItem(
                id = "de_rtlup",
                number = 11,
                name = "RTLup",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://www.2ix2.com/wp-content/uploads/2018/02/65.png",
                pageUrl = "https://www.2ix2.com/rtlup/",
                primaryStreamUrl = "https://s6.hopslan.com/rtltupz/index.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "RTLup Kultserien & Gerichtsshows",
                qualityBadge = "LIVE",
                accentHex = 0xFF34D399
            ),
            TvChannelItem(
                id = "de_voxup",
                number = 12,
                name = "VOXup",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://www.2ix2.com/wp-content/uploads/2018/02/64.png",
                pageUrl = "https://www.2ix2.com/voxup/",
                primaryStreamUrl = "https://s6.hopslan.com/voxupz/index.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "VOXup Lifestyle & Dokus",
                qualityBadge = "LIVE",
                accentHex = 0xFFFB7185
            ),
            TvChannelItem(
                id = "de_rtl_nitro",
                number = 13,
                name = "RTL Nitro",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://www.2ix2.com/wp-content/uploads/2018/02/6.png",
                pageUrl = "https://www.2ix2.com/rtl-nitro-tv/",
                primaryStreamUrl = "https://s6.hopslan.com/nitro1/index.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "NITRO Action, Crime & Fußball",
                qualityBadge = "LIVE HD",
                accentHex = 0xFFFACC15
            ),
            TvChannelItem(
                id = "de_super_rtl",
                number = 14,
                name = "Super RTL",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://www.2ix2.com/wp-content/uploads/2018/02/4.png",
                pageUrl = "https://www.2ix2.com/super-rtl-live/",
                primaryStreamUrl = "https://s6.hopslan.com/superX/index.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "SUPER RTL Familienprogramm & Primetime",
                qualityBadge = "LIVE",
                accentHex = 0xFF60A5FA
            ),
            TvChannelItem(
                id = "de_sixx",
                number = 15,
                name = "Sixx",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://www.2ix2.com/wp-content/uploads/2018/09/sixx-logo.png",
                pageUrl = "https://www.2ix2.com/sixx/",
                primaryStreamUrl = "https://s6.hopslan.com/sixX/index.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "sixx US-Serien & Lifestyle",
                qualityBadge = "LIVE",
                accentHex = 0xFFA78BFA
            ),
            TvChannelItem(
                id = "de_dmax",
                number = 16,
                name = "Dmax",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://www.2ix2.com/wp-content/uploads/2018/09/dmax-logo.png",
                pageUrl = "https://www.2ix2.com/dmax/",
                primaryStreamUrl = "https://s6.hopslan.com/dmaxX/index.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "DMAX Abenteuer, Technik & Outdoor",
                qualityBadge = "LIVE HD",
                accentHex = 0xFFFACC15
            ),
            TvChannelItem(
                id = "de_sky_sport_news",
                number = 17,
                name = "Sky Sport News HD",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://2ix2.com/wp-content/uploads/2019/09/6.png",
                pageUrl = "https://www.2ix2.com/sky-sport-news-hd-snhd/",
                primaryStreamUrl = "https://tagesschau.akamaized.net/hls/live/2020115/tagesschau/tagesschau_1/master.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "Sport-Nachrichten & Live-Ticker",
                qualityBadge = "SPORT HD",
                accentHex = 0xFF38BDF8
            ),
            TvChannelItem(
                id = "de_sport1",
                number = 18,
                name = "Sport1",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://2ix2.com/wp-content/uploads/2019/09/7.png",
                pageUrl = "https://www.2ix2.com/sport1/",
                primaryStreamUrl = "https://s1.hopslan.com/rtl/index.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "SPORT1 Live-Sport & Doppelpass",
                qualityBadge = "SPORT HD",
                accentHex = 0xFF34D399
            ),
            TvChannelItem(
                id = "de_prosieben_maxx",
                number = 19,
                name = "ProSieben Maxx",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://www.2ix2.com/wp-content/uploads/2018/09/pro7maxx-logo.png",
                pageUrl = "https://www.2ix2.com/prosieben-maxx-tv/",
                primaryStreamUrl = "https://s6.hopslan.com/pro7maxxX/index.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "ProSieben MAXX Anime, Sport & Doku",
                qualityBadge = "LIVE HD",
                accentHex = 0xFF38BDF8
            ),
            TvChannelItem(
                id = "de_kabel1_doku",
                number = 20,
                name = "Kabel 1 Doku",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://www.2ix2.com/wp-content/uploads/2018/09/kabel1doku-logo.png",
                pageUrl = "https://www.2ix2.com/kabel-1-doku/",
                primaryStreamUrl = "https://s6.hopslan.com/dokuX/index.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "Kabel Eins Doku Geschichte & Natur",
                qualityBadge = "DOKU HD",
                accentHex = 0xFFF59E0B
            ),
            TvChannelItem(
                id = "de_tlc",
                number = 21,
                name = "TLC",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://www.2ix2.com/wp-content/uploads/2020/10/l1.png",
                pageUrl = "https://www.2ix2.com/tlc/",
                primaryStreamUrl = "https://s6.hopslan.com/tlcX/index.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "TLC Real-Life Entertainment & True Crime",
                qualityBadge = "LIVE",
                accentHex = 0xFFFB7185
            ),
            TvChannelItem(
                id = "de_sat1_gold",
                number = 22,
                name = "Sat1 Gold",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://www.2ix2.com/wp-content/uploads/2018/09/sat1-gold-logo.png",
                pageUrl = "https://www.2ix2.com/sat1-gold/",
                primaryStreamUrl = "https://s6.hopslan.com/goldX/index.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "SAT.1 Gold Kult-Krimis & Ratgeber",
                qualityBadge = "LIVE",
                accentHex = 0xFFFACC15
            ),
            TvChannelItem(
                id = "de_kika",
                number = 23,
                name = "Kika",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://2ix2.com/wp-content/uploads/2019/09/8.png",
                pageUrl = "https://www.2ix2.com/kika/",
                primaryStreamUrl = "https://kikageohls.akamaized.net/hls/live/2022693/livetvkika_de/master.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "KiKA von ARD und ZDF • Kinderprogramm",
                qualityBadge = "FULL HD",
                accentHex = 0xFF34D399
            ),
            TvChannelItem(
                id = "de_tele5",
                number = 24,
                name = "Tele 5",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://www.2ix2.com/wp-content/uploads/2018/09/tele5-logo.png",
                pageUrl = "https://www.2ix2.com/tele-5/",
                primaryStreamUrl = "https://s6.hopslan.com/tele5X/index.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "TELE 5 Sci-Fi, Spielfilme & Kult",
                qualityBadge = "LIVE HD",
                accentHex = 0xFF818CF8
            ),
            TvChannelItem(
                id = "de_comedy_central",
                number = 25,
                name = "Comedy Central",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://2ix2.com/wp-content/uploads/2019/09/10.png",
                pageUrl = "https://www.2ix2.com/comedy-central/",
                primaryStreamUrl = "https://s6.hopslan.com/vivaX/index.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "Comedy Central Sitcoms & Stand-up",
                qualityBadge = "LIVE",
                accentHex = 0xFFFACC15
            ),
            TvChannelItem(
                id = "de_3sat",
                number = 26,
                name = "3Sat",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://2ix2.com/wp-content/uploads/2020/06/8.png",
                pageUrl = "https://www.2ix2.com/3sat/",
                primaryStreamUrl = "https://s6.hopslan.com/3sat/index.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "3sat Kultur, Wissenschaft & Natur",
                qualityBadge = "HD 50FPS",
                accentHex = 0xFFEF4444
            ),
            TvChannelItem(
                id = "de_zdfinfo",
                number = 27,
                name = "ZDFinfo",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://2ix2.com/wp-content/uploads/2020/06/6.png",
                pageUrl = "https://www.2ix2.com/zdfinfo/",
                primaryStreamUrl = "https://s6.hopslan.com/zdfX1/index.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "ZDFinfo Dokumentationen & Zeitgeschichte",
                qualityBadge = "DOKU HD",
                accentHex = 0xFF38BDF8
            ),
            TvChannelItem(
                id = "de_zdfneo",
                number = 28,
                name = "ZDFneo",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://2ix2.com/wp-content/uploads/2020/06/7.png",
                pageUrl = "https://www.2ix2.com/zdfneo/",
                primaryStreamUrl = "https://s6.hopslan.com/zdfneoX/index.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "ZDFneo Serien, Shows & Filme",
                qualityBadge = "FULL HD",
                accentHex = 0xFF2DD4BF
            ),
            TvChannelItem(
                id = "de_arte",
                number = 29,
                name = "Arte",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://2ix2.com/wp-content/uploads/2020/06/4.png",
                pageUrl = "https://www.2ix2.com/arte/",
                primaryStreamUrl = "https://s6.hopslan.com/3sat/index.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "ARTE Europäischer Kulturkanal",
                qualityBadge = "FULL HD",
                accentHex = 0xFFF97316
            ),
            TvChannelItem(
                id = "de_disney_channel",
                number = 30,
                name = "Disney Channel",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://www.2ix2.com/wp-content/uploads/2018/02/9.png",
                pageUrl = "https://www.2ix2.com/disney-channel/",
                primaryStreamUrl = "https://s6.hopslan.com/disneyX/index.m3u8",
                fallbackStreamUrls = listOf(
                    "https://kikageohls.akamaized.net/hls/live/2022693/livetvkika_de/master.m3u8",
                    "https://s6.hopslan.com/superX/index.m3u8"
                ),
                currentShow = "Disney Channel Familienfilme & Animation",
                qualityBadge = "LIVE HD",
                accentHex = 0xFF60A5FA
            ),
            TvChannelItem(
                id = "de_deutsche_welle",
                number = 31,
                name = "Deutsche Welle",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://2ix2.com/wp-content/uploads/2020/10/77.png",
                pageUrl = "https://www.2ix2.com/deutsche-welle/",
                primaryStreamUrl = "https://dwamdstream102.akamaized.net/hls/live/2015525/dwstream102/index.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "DW Live • Internationale Nachrichten",
                qualityBadge = "FULL HD",
                accentHex = 0xFF38BDF8
            ),
            TvChannelItem(
                id = "de_tagesschau",
                number = 32,
                name = "Tagesschau",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://2ix2.com/wp-content/uploads/2020/10/78.png",
                pageUrl = "https://www.2ix2.com/tagesschau/",
                primaryStreamUrl = "https://tagesschau.akamaized.net/hls/live/2020115/tagesschau/tagesschau_1/master.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "tagesschau24 • Nachrichten rund um die Uhr",
                qualityBadge = "FULL HD",
                accentHex = 0xFF38BDF8
            ),
            TvChannelItem(
                id = "de_mdr",
                number = 33,
                name = "MDR Fernsehen",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://2ix2.com/wp-content/uploads/2020/10/79.png",
                pageUrl = "https://www.2ix2.com/mdr-fernsehen/",
                primaryStreamUrl = "https://daserste-live.ard-mcdn.de/daserste/live/hls/de/master.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "MDR Fernsehen Mitteldeutschland",
                qualityBadge = "HD",
                accentHex = 0xFF38BDF8
            ),
            TvChannelItem(
                id = "de_wdr",
                number = 34,
                name = "WDR Fernsehen",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://2ix2.com/wp-content/uploads/2020/10/80.png",
                pageUrl = "https://www.2ix2.com/wdr-fernsehen/",
                primaryStreamUrl = "https://daserste-live.ard-mcdn.de/daserste/live/hls/de/master.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "WDR Fernsehen Nordrhein-Westfalen",
                qualityBadge = "HD",
                accentHex = 0xFF38BDF8
            ),
            TvChannelItem(
                id = "de_br",
                number = 35,
                name = "BR Fernsehen",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://www.2ix2.com/wp-content/uploads/2021/06/45.png",
                pageUrl = "https://www.2ix2.com/br-fernsehen/",
                primaryStreamUrl = "https://mcdn.br.de/br/fs/bfs_sued/hls/de/master.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "BR Fernsehen Bayern Live",
                qualityBadge = "FULL HD",
                accentHex = 0xFF38BDF8
            ),
            TvChannelItem(
                id = "de_mdf1",
                number = 36,
                name = "MDF 1",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://www.2ix2.com/wp-content/uploads/2021/06/39.png",
                pageUrl = "https://www.2ix2.com/mdf-1/",
                primaryStreamUrl = "https://s4.hopslan.com/alex/index.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "MDF.1 Regionalfernsehen Magdeburg",
                qualityBadge = "REGIONAL",
                accentHex = 0xFF60A5FA
            ),
            TvChannelItem(
                id = "de_one_tv",
                number = 37,
                name = "ONE TV",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://2ix2.com/wp-content/uploads/2020/06/14.png",
                pageUrl = "https://www.2ix2.com/one-tv/",
                primaryStreamUrl = "https://s6.hopslan.com/onetv/index.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "ONE • ARD Unterhaltung & Festivals",
                qualityBadge = "FULL HD",
                accentHex = 0xFF34D399
            ),
            TvChannelItem(
                id = "de_rbb",
                number = 38,
                name = "RBB Fernsehen",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://www.2ix2.com/wp-content/uploads/2021/06/40.png",
                pageUrl = "https://www.2ix2.com/rbb-fernsehen/",
                primaryStreamUrl = "https://s4.hopslan.com/alex/index.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "rbb Fernsehen Berlin & Brandenburg",
                qualityBadge = "HD",
                accentHex = 0xFFFB7185
            ),
            TvChannelItem(
                id = "de_welt_der_wunder",
                number = 39,
                name = "Welt der Wunder",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://www.2ix2.com/wp-content/uploads/2021/06/41.png",
                pageUrl = "https://www.2ix2.com/welt-der-wunder/",
                primaryStreamUrl = "https://s6.hopslan.com/dokuX/index.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "Welt der Wunder TV • Wissen & Technik",
                qualityBadge = "DOKU",
                accentHex = 0xFFFACC15
            ),
            TvChannelItem(
                id = "de_phoenix",
                number = 40,
                name = "Phoenix",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://www.2ix2.com/wp-content/uploads/2021/06/42.png",
                pageUrl = "https://www.2ix2.com/phoenix/",
                primaryStreamUrl = "https://tagesschau.akamaized.net/hls/live/2020115/tagesschau/tagesschau_1/master.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "phoenix • Politik, Debatten & Doku",
                qualityBadge = "HD",
                accentHex = 0xFF2DD4BF
            ),
            TvChannelItem(
                id = "de_hr",
                number = 41,
                name = "HR Fernsehen",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://www.2ix2.com/wp-content/uploads/2021/06/43.png",
                pageUrl = "https://www.2ix2.com/hr-fernsehen/",
                primaryStreamUrl = "https://hrhls.akamaized.net/hls/live/2024525/hrhls/index.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "hr-fernsehen Hessen Live",
                qualityBadge = "FULL HD",
                accentHex = 0xFF38BDF8
            ),
            TvChannelItem(
                id = "de_alex_berlin",
                number = 42,
                name = "Alex Berlin",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://www.2ix2.com/wp-content/uploads/2021/06/44.png",
                pageUrl = "https://www.2ix2.com/alex-berlin/",
                primaryStreamUrl = "https://s4.hopslan.com/alex/index.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "ALEX Berlin Kultur & Events",
                qualityBadge = "LIVE",
                accentHex = 0xFFA78BFA
            ),
            TvChannelItem(
                id = "de_ndr",
                number = 43,
                name = "NDR Fernsehen",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://2ix2.com/wp-content/uploads/2020/10/81.png",
                pageUrl = "https://www.2ix2.com/ndr-fernsehen/",
                primaryStreamUrl = "https://mcdn.ndr.de/ndr/hls/ndr_fs/ndr_hh/master.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "NDR Fernsehen Norddeutschland",
                qualityBadge = "FULL HD",
                accentHex = 0xFF38BDF8
            ),
            TvChannelItem(
                id = "de_mtv",
                number = 44,
                name = "MTV",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://www.2ix2.com/wp-content/uploads/2021/06/46.png",
                pageUrl = "https://www.2ix2.com/mtv/",
                primaryStreamUrl = "https://s6.hopslan.com/vivaX/index.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "MTV Germany Music & Charts",
                qualityBadge = "MUSIC",
                accentHex = 0xFFFACC15
            ),
            TvChannelItem(
                id = "de_bloomberg",
                number = 45,
                name = "Bloomberg TV",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://www.2ix2.com/wp-content/uploads/2021/06/47.png",
                pageUrl = "https://www.2ix2.com/bloomberg-tv/",
                primaryStreamUrl = "https://dwamdstream102.akamaized.net/hls/live/2015525/dwstream102/index.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "Bloomberg Television Markets Live",
                qualityBadge = "FINANCE",
                accentHex = 0xFF60A5FA
            ),
            TvChannelItem(
                id = "de_swr",
                number = 46,
                name = "SWR Fernsehen",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://www.2ix2.com/wp-content/uploads/2021/06/48.png",
                pageUrl = "https://www.2ix2.com/swr-fernsehen/",
                primaryStreamUrl = "https://mcdn.br.de/br/fs/bfs_sued/hls/de/master.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "SWR Fernsehen Südwestrundfunk",
                qualityBadge = "HD",
                accentHex = 0xFF38BDF8
            ),
            TvChannelItem(
                id = "de_sr",
                number = 47,
                name = "SR Fernsehen",
                rubrik = TvRubrik.DEUTSCHES_TV,
                logoUrl = "https://www.2ix2.com/wp-content/uploads/2021/06/49.png",
                pageUrl = "https://www.2ix2.com/sr-fernsehen/",
                primaryStreamUrl = "https://hrhls.akamaized.net/hls/live/2024525/hrhls/index.m3u8",
                fallbackStreamUrls = publicFallbacksDe,
                currentShow = "SR Fernsehen Saarländischer Rundfunk",
                qualityBadge = "HD",
                accentHex = 0xFF38BDF8
            )
        )

        val atChannels = listOf(
            TvChannelItem(
                id = "at_orf1",
                number = 101,
                name = "ORF1",
                rubrik = TvRubrik.OESTERREICH_TV,
                logoUrl = "https://2ix2.com/wp-content/uploads/2020/07/29.png",
                pageUrl = "https://www.2ix2.com/orf1/",
                primaryStreamUrl = "https://s6.hopslan.com/orf11/index.m3u8",
                fallbackStreamUrls = publicFallbacksAt,
                currentShow = "ORF 1 Live • Film, Serie & Sport Österreich",
                qualityBadge = "AT HD",
                accentHex = 0xFF34D399
            ),
            TvChannelItem(
                id = "at_orf2",
                number = 102,
                name = "ORF2",
                rubrik = TvRubrik.OESTERREICH_TV,
                logoUrl = "https://www.2ix2.com/wp-content/uploads/2021/06/30.png",
                pageUrl = "https://www.2ix2.com/orf2/",
                primaryStreamUrl = "https://s6.hopslan.com/orf11/index.m3u8",
                fallbackStreamUrls = publicFallbacksAt,
                currentShow = "ORF 2 • Zeit im Bild, Kultur & Bundesländer",
                qualityBadge = "AT HD",
                accentHex = 0xFFEF4444
            ),
            TvChannelItem(
                id = "at_orf3",
                number = 103,
                name = "ORF3",
                rubrik = TvRubrik.OESTERREICH_TV,
                logoUrl = "https://www.2ix2.com/wp-content/uploads/2021/06/31.png",
                pageUrl = "https://www.2ix2.com/orf3/",
                primaryStreamUrl = "https://s6.hopslan.com/3sat/index.m3u8",
                fallbackStreamUrls = publicFallbacksAt,
                currentShow = "ORF III Kultur und Information",
                qualityBadge = "AT HD",
                accentHex = 0xFF38BDF8
            ),
            TvChannelItem(
                id = "at_servus_tv",
                number = 104,
                name = "Servus TV",
                rubrik = TvRubrik.OESTERREICH_TV,
                logoUrl = "https://www.2ix2.com/wp-content/uploads/2021/05/31.png",
                pageUrl = "https://www.2ix2.com/servus-tv/",
                primaryStreamUrl = "https://s6.hopslan.com/servuz1/index.m3u8",
                fallbackStreamUrls = publicFallbacksAt,
                currentShow = "ServusTV Österreich • Heimat, Natur & Live-Sport",
                qualityBadge = "FULL HD",
                accentHex = 0xFFFACC15
            ),
            TvChannelItem(
                id = "at_laendle_tv",
                number = 105,
                name = "Ländle TV",
                rubrik = TvRubrik.OESTERREICH_TV,
                logoUrl = "https://www.2ix2.com/wp-content/uploads/2021/06/34.png",
                pageUrl = "https://www.2ix2.com/laendle-tv/",
                primaryStreamUrl = "https://s4.hopslan.com/landle/index.m3u8",
                fallbackStreamUrls = publicFallbacksAt,
                currentShow = "Ländle TV • Vorarlberg Regionalfernsehen",
                qualityBadge = "AT LIVE",
                accentHex = 0xFF38BDF8
            ),
            TvChannelItem(
                id = "at_fs1",
                number = 106,
                name = "FS1",
                rubrik = TvRubrik.OESTERREICH_TV,
                logoUrl = "https://www.2ix2.com/wp-content/uploads/2021/06/32.png",
                pageUrl = "https://www.2ix2.com/fs1/",
                primaryStreamUrl = "https://s4.hopslan.com/fs1/index.m3u8",
                fallbackStreamUrls = publicFallbacksAt,
                currentShow = "FS1 • Freies Fernsehen Salzburg",
                qualityBadge = "AT LIVE",
                accentHex = 0xFF2DD4BF
            ),
            TvChannelItem(
                id = "at_m4tv",
                number = 107,
                name = "M4TV",
                rubrik = TvRubrik.OESTERREICH_TV,
                logoUrl = "https://www.2ix2.com/wp-content/uploads/2021/06/35.png",
                pageUrl = "https://www.2ix2.com/m4tv/",
                primaryStreamUrl = "https://s4.hopslan.com/m4tv/index.m3u8",
                fallbackStreamUrls = publicFallbacksAt,
                currentShow = "M4TV • Mostviertel Fernsehen Niederösterreich",
                qualityBadge = "AT LIVE",
                accentHex = 0xFF60A5FA
            ),
            TvChannelItem(
                id = "at_w24",
                number = 108,
                name = "W24 TV",
                rubrik = TvRubrik.OESTERREICH_TV,
                logoUrl = "https://www.2ix2.com/wp-content/uploads/2021/06/37.png",
                pageUrl = "https://www.2ix2.com/w24-tv/",
                primaryStreamUrl = "https://s4.hopslan.com/w24/index.m3u8",
                fallbackStreamUrls = publicFallbacksAt,
                currentShow = "W24 • Das Stadtfernsehen für Wien",
                qualityBadge = "WIEN HD",
                accentHex = 0xFFFB7185
            ),
            TvChannelItem(
                id = "at_tirol_tv",
                number = 109,
                name = "Tirol TV",
                rubrik = TvRubrik.OESTERREICH_TV,
                logoUrl = "https://www.2ix2.com/wp-content/uploads/2021/06/38.png",
                pageUrl = "https://www.2ix2.com/tirol-tv/",
                primaryStreamUrl = "https://s4.hopslan.com/tirol/index.m3u8",
                fallbackStreamUrls = publicFallbacksAt,
                currentShow = "tirol tv • Nachrichten & Bergwelt aus Tirol",
                qualityBadge = "TIROL HD",
                accentHex = 0xFF34D399
            ),
            TvChannelItem(
                id = "at_okto_tv",
                number = 110,
                name = "OKTO TV",
                rubrik = TvRubrik.OESTERREICH_TV,
                logoUrl = "https://www.2ix2.com/wp-content/uploads/2021/06/33.png",
                pageUrl = "https://www.2ix2.com/okto-tv/",
                primaryStreamUrl = "https://s4.hopslan.com/okto/index.m3u8",
                fallbackStreamUrls = publicFallbacksAt,
                currentShow = "OKTO Community TV Wien",
                qualityBadge = "AT LIVE",
                accentHex = 0xFFA78BFA
            ),
            TvChannelItem(
                id = "at_dorf_tv",
                number = 111,
                name = "DORF TV",
                rubrik = TvRubrik.OESTERREICH_TV,
                logoUrl = "https://www.2ix2.com/wp-content/uploads/2021/06/36.png",
                pageUrl = "https://www.2ix2.com/dorf-tv/",
                primaryStreamUrl = "https://s4.hopslan.com/dorf/index.m3u8",
                fallbackStreamUrls = publicFallbacksAt,
                currentShow = "DORF TV • Oberösterreich Regionalprogramm",
                qualityBadge = "AT LIVE",
                accentHex = 0xFFFACC15
            )
        )

        val deAndAt = deChannels + atChannels
        return deAndAt + getInitialTv2OhaChannels(deAndAt) + getInitialTv3LiveChannels(deAndAt)
    }

    private fun getInitialDeAtChannels(): List<TvChannelItem> {
        return getInitialChannels().filter {
            it.rubrik == TvRubrik.DEUTSCHES_TV || it.rubrik == TvRubrik.OESTERREICH_TV
        }
    }

    /**
     * Pre-populated DE TV2 channel lineup so the TV2 tab is immediately ready for instant
     * streaming in the internal ExoPlayer while `fetchTv2OhaChannels()` synchronizes live entries.
     */
    private fun getInitialTv2OhaChannels(deAndAt: List<TvChannelItem>): List<TvChannelItem> {
        return deAndAt.mapIndexed { idx, ch ->
            val ohaChannelId = 3182315100L + idx
            val ohaPlayUrl = "https://oha.to/play/$ohaChannelId/index.m3u8"
            TvChannelItem(
                id = "tv2_init_${ch.id}",
                number = 201 + idx,
                name = "${ch.name} (DE TV2)",
                rubrik = TvRubrik.TV2_OHA,
                logoUrl = ch.logoUrl,
                pageUrl = "https://oha.to/mediaurl.json",
                primaryStreamUrl = ch.primaryStreamUrl,
                fallbackStreamUrls = buildList {
                    add(ohaPlayUrl)
                    addAll(ch.fallbackStreamUrls)
                }.distinct(),
                currentShow = "DE TV2 • ${ch.currentShow}",
                qualityBadge = "DE TV2",
                accentHex = ch.accentHex
            )
        }
    }

    /**
     * Pre-populated TV3 lineup of 531 German TV channels without exposing the source URL
     * in any user-visible fields.
     */
    private fun getInitialTv3LiveChannels(deAndAt: List<TvChannelItem>): List<TvChannelItem> {
        return Tv3CatalogProvider.getInitial531GermanChannels(deAndAt)
    }

    suspend fun loadQuickLaunchApps(): List<QuickLaunchTile> = withContext(ioDispatcher) {
        val curated = listOf(
            QuickLaunchTile(
                id = "app_2ix2_de",
                title = "1: Deutsches TV (47 Sender)",
                subtitle = "2ix2.com/category/deutsche-tv-sender/",
                categoryLabel = "Rubrik 1",
                packageOrAction = "internal_rubrik_de",
                deepLinkUri = "https://www.2ix2.com/category/deutsche-tv-sender/",
                accentHex = 0xFF38BDF8
            ),
            QuickLaunchTile(
                id = "app_2ix2_at",
                title = "2: Österreich TV (11 Sender)",
                subtitle = "2ix2.com/category/oesterreichische-tv/",
                categoryLabel = "Rubrik 2",
                packageOrAction = "internal_rubrik_at",
                deepLinkUri = "https://www.2ix2.com/category/oesterreichische-tv/",
                accentHex = 0xFF34D399
            ),
            QuickLaunchTile(
                id = "app_tv2_oha",
                title = "DE TV2",
                subtitle = "DE TV2 Live-Sender",
                categoryLabel = "DE TV2",
                packageOrAction = "internal_rubrik_tv2",
                deepLinkUri = "https://oha.to/mediaurl.json",
                accentHex = 0xFFFACC15
            ),
            QuickLaunchTile(
                id = "app_tv3_live",
                title = "TV3 (531 Sender)",
                subtitle = "531 Deutsche TV-Kanäle Live",
                categoryLabel = "TV3",
                packageOrAction = "internal_rubrik_tv3",
                deepLinkUri = "https://tele24seven.com/?country=DE#country-channels",
                accentHex = 0xFF34D399
            )
        )
        curated
    }
}
