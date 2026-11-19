package com.example.data.parser

import android.net.Uri
import android.util.Log
import com.example.data.model.ChannelItem
import com.example.data.model.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class MacPortalProgress(
    val step: String,
    val detail: String,
    val liveCount: Int = 0,
    val movieCount: Int = 0,
    val seriesCount: Int = 0,
    val isComplete: Boolean = false,
    val isTimeoutOrRejected: Boolean = false,
    val errorMessage: String? = null
)

class MacPortalClient {

    companion object {
        private const val STB_USER_AGENT = "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3"

        fun extractQueryParam(url: String, param: String): String {
            return try {
                Uri.parse(url).getQueryParameter(param) ?: ""
            } catch (e: Exception) {
                ""
            }
        }
    }

    enum class MacProtocolMode(val querySuffix: String) {
        JS_XML("&JsHttpRequest=1-xml"),
        PLAIN(""),
        JS_UTF("&JsHttpRequest=1-utf")
    }

    data class HandshakeOutcome(
        val token: String = "",
        val errorMessage: String? = null
    )

    // Bounded concurrency for fast parallel genre/category requests without server rate-limiting
    private val requestSemaphore = Semaphore(8)

    private val client = OkHttpClient.Builder()
        .connectionPool(okhttp3.ConnectionPool(16, 5, TimeUnit.MINUTES))
        .dispatcher(Dispatcher().apply {
            maxRequests = 20
            maxRequestsPerHost = 10
        })
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(18, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val discoveryClient = OkHttpClient.Builder()
        .connectionPool(okhttp3.ConnectionPool(10, 2, TimeUnit.MINUTES))
        .dispatcher(Dispatcher().apply {
            maxRequests = 16
            maxRequestsPerHost = 10
        })
        .connectTimeout(2500, TimeUnit.MILLISECONDS)
        .readTimeout(3500, TimeUnit.MILLISECONDS)
        .callTimeout(4500, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // Cache active tokens per endpoint + MAC
    private val tokenCache = ConcurrentHashMap<String, String>()
    // Cache resolved endpoint URL per portal URL
    private val endpointCache = ConcurrentHashMap<String, String>()
    // Cache detected protocol mode per endpoint
    private val protocolModeCache = ConcurrentHashMap<String, MacProtocolMode>()
    // Cache working VOD and Series query templates per endpoint to avoid repeated probing per category
    private val vodQueryTemplateCache = ConcurrentHashMap<String, Pair<String, String>>()
    private val seriesQueryTemplateCache = ConcurrentHashMap<String, Pair<String, String>>()
    // Fast LRU Cache for resolved create_link URLs (TTL: 10 minutes) for instant zapping
    private val resolvedLinkCache = ConcurrentHashMap<String, Pair<Long, PortalResolutionResult>>()
    private val batchMutex = kotlinx.coroutines.sync.Mutex()

    suspend fun fetchMacPortalChannels(
        portalUrl: String,
        macAddress: String,
        playlistId: String,
        syncOnlyGerman: Boolean = false,
        allowedCountries: Set<String> = if (syncOnlyGerman) setOf("DE", "AT", "CH", "ADULT") else emptySet(),
        includeVodAndSeries: Boolean = true,
        onProgress: ((MacPortalProgress) -> Unit)? = null,
        onBatchParsed: (suspend (batch: List<ChannelItem>, isDachBatch: Boolean) -> Unit)? = null,
        onCategoriesDiscovered: (suspend (List<com.example.data.model.ProviderCategoryEntity>) -> Unit)? = null
    ): Result<List<ChannelItem>> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = PlaylistImportExtractor.cleanMacPortalUrl(portalUrl).ifBlank { portalUrl.trim() }
            val cleanMac = PlaylistImportExtractor.cleanMacAddress(macAddress)
            if (cleanMac.isBlank()) {
                val err = "MAC-Adresse darf nicht leer sein."
                onProgress?.invoke(MacPortalProgress(step = "Fehler", detail = err, isTimeoutOrRejected = true, errorMessage = err))
                return@withContext Result.failure(Exception(err))
            }

            onProgress?.invoke(MacPortalProgress(
                step = "Verbindung aufbauen",
                detail = "Prüfe Portal-URL und ermittle Server-Endpunkt..."
            ))

            // 1. Discover load.php Endpoint
            val endpoint = resolvePortalEndpoint(cleanUrl, cleanMac)
            Log.d("MacPortalClient", "Using endpoint: $endpoint with MAC: $cleanMac")

            onProgress?.invoke(MacPortalProgress(
                step = "Handshake wird gesendet",
                detail = "Sende Authentifizierungs-Handshake an Portal (15 Sek. Schutz)..."
            ))

            // 2. Handshake & Token Acquisition with fast 8-second timeout & token cache reuse from discovery
            val startTime = System.currentTimeMillis()
            val handshakeOutcome = withTimeoutOrNull(8_000L) {
                getOrRefreshHandshakeToken(endpoint, cleanMac, forceRefresh = false)
            }

            if (handshakeOutcome == null) {
                val timeoutMsg = "Verbindungs-Timeout: Das MAC-Portal antwortet nicht oder die Verbindung wurde abgewiesen. Bitte prüfen Sie die MAC-Adresse ($cleanMac), die Portal-URL oder den Server-Status (ggf. deutsches VPN erforderlich)."
                onProgress?.invoke(MacPortalProgress(
                    step = "Portal-Abweisung / Timeout",
                    detail = timeoutMsg,
                    isTimeoutOrRejected = true,
                    errorMessage = timeoutMsg
                ))
                return@withContext Result.failure(Exception(timeoutMsg))
            }

            val token = handshakeOutcome.token
            if (token.isBlank()) {
                val elapsed = System.currentTimeMillis() - startTime
                val prefix = if (elapsed >= 7_500L) "Timeout / " else ""
                val errorMsg = "${prefix}Portal-Abweisung: " + (handshakeOutcome.errorMessage
                    ?: "Das MAC-Portal hat die Verbindung mit MAC $cleanMac abgewiesen. Bitte MAC-Adresse oder Account-Status prüfen.")
                onProgress?.invoke(MacPortalProgress(
                    step = "Portal-Abweisung",
                    detail = errorMsg,
                    isTimeoutOrRejected = true,
                    errorMessage = errorMsg
                ))
                return@withContext Result.failure(Exception(errorMsg))
            }

            onProgress?.invoke(MacPortalProgress(
                step = "Handshake autorisiert",
                detail = "Token erfolgreich erhalten. Lade TV-Kategorien & Sender..."
            ))

            val baseUrl = extractBaseDomain(endpoint)

            // 3. Fetch Live TV Channels (with live counter and immediate DACH batch emission)
            val liveChannels = fetchLiveChannels(
                endpoint = endpoint,
                cleanMac = cleanMac,
                token = token,
                baseUrl = baseUrl,
                playlistId = playlistId,
                syncOnlyGerman = syncOnlyGerman,
                allowedCountries = allowedCountries,
                onBatchParsed = onBatchParsed,
                onCategoriesDiscovered = onCategoriesDiscovered
            ) { count ->
                onProgress?.invoke(MacPortalProgress(
                    step = "TV-Kanäle laden",
                    detail = "Lade TV Channels: $count...",
                    liveCount = count,
                    movieCount = 0,
                    seriesCount = 0
                ))
            }

            // 4. Fetch VOD Movies and Series IN PARALLEL with 12s time budget
            val (vodMovies, seriesEpisodes) = if (includeVodAndSeries) {
                onProgress?.invoke(MacPortalProgress(
                    step = "VOD & Serien laden",
                    detail = "TV Channels: ${liveChannels.size} • Lade Filme & Serien...",
                    liveCount = liveChannels.size,
                    movieCount = 0,
                    seriesCount = 0
                ))
                val curMovieCount = java.util.concurrent.atomic.AtomicInteger(0)
                val curSeriesCount = java.util.concurrent.atomic.AtomicInteger(0)

                coroutineScope {
                    val vodDeferred = async(Dispatchers.IO) {
                        withTimeoutOrNull(90_000L) {
                            fetchVodMovies(
                                endpoint = endpoint,
                                cleanMac = cleanMac,
                                token = token,
                                baseUrl = baseUrl,
                                playlistId = playlistId,
                                syncOnlyGerman = syncOnlyGerman,
                                allowedCountries = allowedCountries,
                                onBatchParsed = onBatchParsed,
                                onCategoriesDiscovered = onCategoriesDiscovered
                            ) { count ->
                                curMovieCount.set(count)
                                onProgress?.invoke(MacPortalProgress(
                                    step = "VOD & Serien laden",
                                    detail = "TV Channels: ${liveChannels.size} • Filme: $count • Serien: ${curSeriesCount.get()}...",
                                    liveCount = liveChannels.size,
                                    movieCount = count,
                                    seriesCount = curSeriesCount.get()
                                ))
                            }
                        } ?: emptyList()
                    }

                    val seriesDeferred = async(Dispatchers.IO) {
                        withTimeoutOrNull(90_000L) {
                            fetchSeriesAndEpisodes(
                                endpoint = endpoint,
                                cleanMac = cleanMac,
                                token = token,
                                baseUrl = baseUrl,
                                playlistId = playlistId,
                                syncOnlyGerman = syncOnlyGerman,
                                allowedCountries = allowedCountries,
                                onBatchParsed = onBatchParsed,
                                onCategoriesDiscovered = onCategoriesDiscovered
                            ) { count ->
                                curSeriesCount.set(count)
                                onProgress?.invoke(MacPortalProgress(
                                    step = "VOD & Serien laden",
                                    detail = "TV Channels: ${liveChannels.size} • Filme: ${curMovieCount.get()} • Serien: $count...",
                                    liveCount = liveChannels.size,
                                    movieCount = curMovieCount.get(),
                                    seriesCount = count
                                ))
                            }
                        } ?: emptyList()
                    }

                    Pair(vodDeferred.await(), seriesDeferred.await())
                }
            } else {
                Pair(emptyList(), emptyList())
            }

            val totalItems = ArrayList<ChannelItem>(liveChannels.size + vodMovies.size + seriesEpisodes.size)
            totalItems.addAll(liveChannels)
            totalItems.addAll(vodMovies)
            totalItems.addAll(seriesEpisodes)

            if (totalItems.isEmpty()) {
                val noContentMsg = "Keine Inhalte vom MAC Portal erhalten. Bitte Zugangsdaten und Portal-Status prüfen."
                onProgress?.invoke(MacPortalProgress(
                    step = "Keine Inhalte",
                    detail = noContentMsg,
                    isTimeoutOrRejected = true,
                    errorMessage = noContentMsg
                ))
                return@withContext Result.failure(Exception(noContentMsg))
            }

            Log.d("MacPortalClient", "Fetched from MAC portal: ${liveChannels.size} Live, ${vodMovies.size} Movies, ${seriesEpisodes.size} Series episodes")
            onProgress?.invoke(MacPortalProgress(
                step = "Fertig",
                detail = "Erfolgreich geladen: ${liveChannels.size} TV-Kanäle, ${vodMovies.size} Filme, ${seriesEpisodes.size} Serien",
                liveCount = liveChannels.size,
                movieCount = vodMovies.size,
                seriesCount = seriesEpisodes.size,
                isComplete = true
            ))
            Result.success(totalItems)
        } catch (e: Exception) {
            val msg = e.message ?: "Fehler beim Laden des MAC Portals"
            onProgress?.invoke(MacPortalProgress(
                step = "Fehler",
                detail = msg,
                isTimeoutOrRejected = true,
                errorMessage = msg
            ))
            Log.e("MacPortalClient", "Error fetching MAC portal content", e)
            Result.failure(e)
        }
    }

    private suspend fun fetchLiveChannels(
        endpoint: String,
        cleanMac: String,
        token: String,
        baseUrl: String,
        playlistId: String,
        syncOnlyGerman: Boolean = false,
        allowedCountries: Set<String> = emptySet(),
        onBatchParsed: (suspend (batch: List<ChannelItem>, isDachBatch: Boolean) -> Unit)? = null,
        onCategoriesDiscovered: (suspend (List<com.example.data.model.ProviderCategoryEntity>) -> Unit)? = null,
        onLiveCountUpdate: ((Int) -> Unit)? = null
    ): List<ChannelItem> = withContext(Dispatchers.IO) {
        try {
            val effectiveAllowed = if (allowedCountries.isNotEmpty()) {
                allowedCountries.map { it.uppercase() }.toSet()
            } else if (syncOnlyGerman) {
                setOf("DE", "AT", "CH", "ADULT")
            } else {
                emptySet()
            }

            val genreMap = fetchGenreMap(endpoint, cleanMac, token)
            Log.d("MacPortalClient", "Discovered ${genreMap.size} genres/categories for endpoint: $endpoint")

            if (onCategoriesDiscovered != null && genreMap.isNotEmpty()) {
                var liveOrder = 0
                val discoveredGenres = genreMap.entries.map { (gId, gTitle) ->
                    val parsed = com.example.data.category.ProviderCategoryEngine.parseCategory(
                        rawGroup = gTitle,
                        providerType = com.example.data.country.ProviderType.MAC
                    )
                    val isSynced = effectiveAllowed.isEmpty() ||
                        ContentClassifier.isCategoryMatchingCountries(gTitle, effectiveAllowed, isVodOrSeries = false) ||
                        parsed.countryCode in effectiveAllowed
                    com.example.data.model.ProviderCategoryEntity(
                        id = "${playlistId}_LIVE_TV_${gId}",
                        playlistId = playlistId,
                        contentType = ContentType.LIVE_TV,
                        categoryId = gId,
                        rawCategoryName = gTitle,
                        countryCode = parsed.countryCode,
                        parentCategory = parsed.parentCategory,
                        subCategory = parsed.subCategory,
                        sortOrder = liveOrder++,
                        isSynced = isSynced
                    )
                }
                onCategoriesDiscovered(discoveredGenres)
            }

            val allChannels = mutableListOf<ChannelItem>()
            val seenKeys = mutableSetOf<String>()

            // STRATEGY 1: Try get_all_channels first (fetches entire portal lineup in 1 HTTP call without page truncation)
            val fullChannels = tryGetAllChannels(endpoint, cleanMac, token, baseUrl, playlistId, genreMap, effectiveAllowed)
            if (fullChannels.isNotEmpty()) {
                val isSingleTruncatedPage = fullChannels.size in 1..20 && genreMap.size > 2
                if (!isSingleTruncatedPage) {
                    Log.i("MacPortalClient", "Successfully fetched ${fullChannels.size} live channels via get_all_channels")
                    for (ch in fullChannels) {
                        val key = "${ch.name.lowercase().trim()}_${ch.groupTitle.lowercase().trim()}"
                        if (seenKeys.add(key)) {
                            allChannels.add(ch)
                        }
                    }
                    val sorted = sortLiveTvChannels(allChannels)
                    if (onBatchParsed != null && sorted.isNotEmpty()) {
                        val (dach, others) = sorted.partition { it.isDach || it.countryCode in setOf("DE", "AT", "CH") }
                        if (dach.isNotEmpty()) {
                            batchMutex.lock()
                            try { onBatchParsed(dach, true) } finally { batchMutex.unlock() }
                        }
                        if (others.isNotEmpty()) {
                            batchMutex.lock()
                            try { onBatchParsed(others, false) } finally { batchMutex.unlock() }
                        }
                    }
                    onLiveCountUpdate?.invoke(sorted.size)
                }
            }

            // STRATEGY 2 (SELECTIVE GENRE SYNC / SUPPLEMENT): Fetch any matched priority genres not yet populated
            if (effectiveAllowed.isNotEmpty() && genreMap.isNotEmpty()) {
                val matchedGenres = genreMap.entries.filter { (_, gTitle) ->
                    ContentClassifier.isCategoryMatchingCountries(gTitle, effectiveAllowed)
                }
                val populatedGroups = allChannels.map { it.groupTitle.lowercase().trim() }.toSet()
                val genresToFetch = matchedGenres.filter { (_, gTitle) ->
                    gTitle.lowercase().trim() !in populatedGroups
                }
                if (genresToFetch.isNotEmpty()) {
                    coroutineScope {
                        genresToFetch.chunked(8).forEach { chunk ->
                            chunk.map { (gId, gTitle) ->
                                async(Dispatchers.IO) {
                                    requestSemaphore.withPermit {
                                        val chList = fetchGenreChannelsWithPagination(
                                            endpoint = endpoint,
                                            cleanMac = cleanMac,
                                            token = token,
                                            baseUrl = baseUrl,
                                            playlistId = playlistId,
                                            genreId = gId,
                                            genreTitle = gTitle,
                                            maxPagesToFetch = 35,
                                            allowedCountries = effectiveAllowed
                                        )
                                        if (chList.isNotEmpty()) {
                                            batchMutex.lock()
                                            try {
                                                val newlyAdded = mutableListOf<ChannelItem>()
                                                for (ch in chList) {
                                                    val key = "${ch.name.lowercase().trim()}_${ch.groupTitle.lowercase().trim()}"
                                                    if (seenKeys.add(key)) {
                                                        allChannels.add(ch)
                                                        newlyAdded.add(ch)
                                                    }
                                                }
                                                if (newlyAdded.isNotEmpty()) {
                                                    val isDachBatch = newlyAdded.any { it.isDach || it.countryCode in setOf("DE", "AT", "CH") }
                                                    onBatchParsed?.invoke(newlyAdded, isDachBatch)
                                                    onLiveCountUpdate?.invoke(allChannels.size)
                                                }
                                            } finally {
                                                batchMutex.unlock()
                                            }
                                        }
                                    }
                                }
                            }.awaitAll()
                        }
                    }
                }
                if (allChannels.isNotEmpty()) {
                    Log.i("MacPortalClient", "Fetched ${allChannels.size} selective live channels across ${matchedGenres.size} genres")
                    onLiveCountUpdate?.invoke(allChannels.size)
                    return@withContext sortLiveTvChannels(allChannels)
                }
            }

            if (allChannels.isNotEmpty()) {
                onLiveCountUpdate?.invoke(allChannels.size)
                return@withContext sortLiveTvChannels(allChannels)
            }

            // STRATEGY 3: Genre-based pagination fallback when genre names have no explicit country prefix
            if (genreMap.isNotEmpty()) {
                val targetGenres = genreMap.entries.take(25)

                coroutineScope {
                    targetGenres.map { (gId, gTitle) ->
                        async(Dispatchers.IO) {
                            requestSemaphore.withPermit {
                                val chList = fetchGenreChannelsWithPagination(
                                    endpoint = endpoint,
                                    cleanMac = cleanMac,
                                    token = token,
                                    baseUrl = baseUrl,
                                    playlistId = playlistId,
                                    genreId = gId,
                                    genreTitle = gTitle,
                                    maxPagesToFetch = 20,
                                    allowedCountries = effectiveAllowed
                                )
                                if (chList.isNotEmpty()) {
                                    batchMutex.lock()
                                    try {
                                        val newlyAdded = mutableListOf<ChannelItem>()
                                        for (ch in chList) {
                                            val key = "${ch.name.lowercase().trim()}_${ch.groupTitle.lowercase().trim()}"
                                            if (seenKeys.add(key)) {
                                                allChannels.add(ch)
                                                newlyAdded.add(ch)
                                            }
                                        }
                                        if (newlyAdded.isNotEmpty()) {
                                            val isDachBatch = newlyAdded.any { it.isDach || it.countryCode in setOf("DE", "AT", "CH") }
                                            onBatchParsed?.invoke(newlyAdded, isDachBatch)
                                            onLiveCountUpdate?.invoke(allChannels.size)
                                        }
                                    } finally {
                                        batchMutex.unlock()
                                    }
                                }
                            }
                        }
                    }.awaitAll()
                }

                if (allChannels.isNotEmpty()) {
                    onLiveCountUpdate?.invoke(allChannels.size)
                    return@withContext sortLiveTvChannels(allChannels)
                }
            }

            // STRATEGY 4: Global direct pagination fallback
            val fallbackChannels = fetchChannelsWithDirectPagination(
                endpoint = endpoint,
                cleanMac = cleanMac,
                token = token,
                baseUrl = baseUrl,
                playlistId = playlistId,
                genreMap = genreMap,
                allowedCountries = effectiveAllowed
            )
            val newlyAdded = mutableListOf<ChannelItem>()
            for (ch in fallbackChannels) {
                val key = "${ch.name.lowercase().trim()}_${ch.groupTitle.lowercase().trim()}"
                if (seenKeys.add(key)) {
                    allChannels.add(ch)
                    newlyAdded.add(ch)
                }
            }
            if (newlyAdded.isNotEmpty() && onBatchParsed != null) {
                val isDachBatch = newlyAdded.any { it.isDach || it.countryCode in setOf("DE", "AT", "CH") }
                batchMutex.lock()
                try { onBatchParsed(newlyAdded, isDachBatch) } finally { batchMutex.unlock() }
            }
            onLiveCountUpdate?.invoke(allChannels.size)

            sortLiveTvChannels(allChannels)
        } catch (e: Exception) {
            Log.e("MacPortalClient", "Error fetching live channels", e)
            emptyList()
        }
    }

    private fun tryGetAllChannels(
        endpoint: String,
        cleanMac: String,
        token: String,
        baseUrl: String,
        playlistId: String,
        genreMap: Map<String, String>,
        allowedCountries: Set<String> = emptySet()
    ): List<ChannelItem> {
        val mode = protocolModeCache[endpoint] ?: MacProtocolMode.JS_XML
        val suffixes = listOf(mode.querySuffix, "").distinct().take(2)
        val queries = listOf(
            "type=itv&action=get_all_channels",
            "type=itv&action=get_ordered_list&genre=*&all=1"
        )

        for (suffix in suffixes) {
            for (q in queries) {
                try {
                    val url = "$endpoint?$q&mac=$cleanMac&token=$token$suffix"
                    val req = Request.Builder()
                        .url(url)
                        .addHeader("User-Agent", STB_USER_AGENT)
                        .addHeader("Cookie", "mac=$cleanMac; stb_lang=en; timezone=Europe/Berlin; token=$token")
                        .addHeader("Authorization", "Bearer $token")
                        .addHeader("X-User-Agent", "Model: MAG250; Link: WiFi")
                        .build()
                    val res = client.newCall(req).execute()
                    val body = res.body?.string() ?: continue
                    val array = extractChannelsArray(body)
                    if (array != null && array.length() > 0) {
                        val channels = mutableListOf<ChannelItem>()
                        for (i in 0 until array.length()) {
                            val obj = array.optJSONObject(i) ?: continue
                            val ch = parseChannelFromJson(
                                obj = obj,
                                endpoint = endpoint,
                                cleanMac = cleanMac,
                                token = token,
                                baseUrl = baseUrl,
                                playlistId = playlistId,
                                genreMap = genreMap,
                                indexHint = i,
                                allowedCountries = allowedCountries
                            )
                            if (ch != null) {
                                channels.add(ch)
                            }
                        }
                        if (channels.isNotEmpty() || array.length() > 25) {
                            return channels
                        }
                    }
                } catch (ignored: Exception) {}
            }
        }
        return emptyList()
    }

    private fun fetchGenreChannelsWithPagination(
        endpoint: String,
        cleanMac: String,
        token: String,
        baseUrl: String,
        playlistId: String,
        genreId: String,
        genreTitle: String,
        maxPagesToFetch: Int = 35,
        allowedCountries: Set<String> = emptySet()
    ): List<ChannelItem> {
        val result = mutableListOf<ChannelItem>()
        val mode = protocolModeCache[endpoint] ?: MacProtocolMode.JS_XML
        val suffixes = listOf(mode.querySuffix, "").distinct().take(2)

        try {
            var page = 1
            var totalItems = 0
            var maxPageItems = 14
            var workingQueryPrefix = "type=itv&action=get_ordered_list&genre=$genreId"
            var workingSuffix = suffixes.first()

            val queryPrefixes = listOf(
                "type=itv&action=get_ordered_list&genre=$genreId&force_ch_link_check=&fav=0&sortby=number&hd=0",
                "type=itv&action=get_ordered_list&genre=$genreId",
                "type=itv&action=get_ordered_list&category=$genreId"
            )

            while (page <= maxPagesToFetch) {
                var curDataArray: JSONArray? = null
                var lastTotal = 0
                var lastMax = 14

                val prefixCandidates = if (page == 1) queryPrefixes else listOf(workingQueryPrefix)
                val suffixCandidates = if (page == 1) suffixes else listOf(workingSuffix)

                candidateLoop@ for (qPrefix in prefixCandidates) {
                    for (suf in suffixCandidates) {
                        val url = "$endpoint?$qPrefix&p=$page&mac=$cleanMac&token=$token$suf"
                        try {
                            val req = Request.Builder()
                                .url(url)
                                .addHeader("User-Agent", STB_USER_AGENT)
                                .addHeader("Cookie", "mac=$cleanMac; stb_lang=en; timezone=Europe/Berlin; token=$token")
                                .addHeader("Authorization", "Bearer $token")
                                .addHeader("X-User-Agent", "Model: MAG250; Link: WiFi")
                                .build()
                            val res = client.newCall(req).execute()
                            val body = res.body?.string() ?: continue
                            val parsed = extractChannelsArray(body)
                            if (parsed != null && parsed.length() > 0) {
                                curDataArray = parsed
                                workingQueryPrefix = qPrefix
                                workingSuffix = suf
                                try {
                                    val root = JSONObject(body.trim())
                                    val jsObj = root.optJSONObject("js") ?: root
                                    lastTotal = jsObj.optInt("total_items", 0)
                                    lastMax = jsObj.optInt("max_page_items", 14).takeIf { it > 0 } ?: 14
                                } catch (ignored: Exception) {}
                                break@candidateLoop
                            }
                        } catch (ignored: Exception) {}
                    }
                }

                if (curDataArray == null || curDataArray.length() == 0) {
                    break
                }

                if (page == 1) {
                    totalItems = lastTotal
                    maxPageItems = lastMax
                }

                for (i in 0 until curDataArray.length()) {
                    val obj = curDataArray.optJSONObject(i) ?: continue
                    val ch = parseChannelFromJson(
                        obj = obj,
                        endpoint = endpoint,
                        cleanMac = cleanMac,
                        token = token,
                        baseUrl = baseUrl,
                        playlistId = playlistId,
                        fallbackGroup = genreTitle,
                        indexHint = (page - 1) * maxPageItems + i,
                        allowedCountries = allowedCountries
                    )
                    if (ch != null) {
                        result.add(ch)
                    }
                }

                if (totalItems > 0 && (page * maxPageItems) >= totalItems) {
                    break
                }
                if (curDataArray.length() < maxPageItems) {
                    break
                }

                page++
            }
        } catch (e: Exception) {
            Log.w("MacPortalClient", "Error fetching genre $genreTitle ($genreId) on page", e)
        }
        return result
    }

    private fun fetchChannelsWithDirectPagination(
        endpoint: String,
        cleanMac: String,
        token: String,
        baseUrl: String,
        playlistId: String,
        genreMap: Map<String, String>,
        allowedCountries: Set<String> = emptySet()
    ): List<ChannelItem> {
        val result = mutableListOf<ChannelItem>()
        val mode = protocolModeCache[endpoint] ?: MacProtocolMode.JS_XML
        val suffixes = listOf(mode.querySuffix, "&JsHttpRequest=1-xml", "", "&JsHttpRequest=1-utf").distinct()

        try {
            var page = 1
            var totalItems = 0
            var maxPageItems = 14
            var workingQuery = "type=itv&action=get_ordered_list&genre=*&p="
            var workingSuffix = suffixes.first()

            val queryPatterns = listOf(
                "type=itv&action=get_ordered_list&genre=*&p=",
                "type=itv&action=get_ordered_list&genre=0&p=",
                "type=itv&action=get_ordered_list&p=",
                "type=itv&action=get_ordered_list&not_all=0&p=",
                "type=itv&action=get_ordered_list&all=1&p=",
                "type=itv&action=get_all_channels&p="
            )

            while (page <= 80) {
                var curDataArray: JSONArray? = null
                var lastTotal = 0
                var lastMax = 14

                val queriesToTry = if (page == 1) queryPatterns else listOf(workingQuery)
                val suffixCandidates = if (page == 1) suffixes else listOf(workingSuffix)

                candidateLoop@ for (queryPat in queriesToTry) {
                    for (suf in suffixCandidates) {
                        val url = "$endpoint?$queryPat$page&mac=$cleanMac&token=$token$suf"
                        try {
                            val req = Request.Builder()
                                .url(url)
                                .addHeader("User-Agent", STB_USER_AGENT)
                                .addHeader("Cookie", "mac=$cleanMac; stb_lang=en; timezone=Europe/Berlin; token=$token")
                                .addHeader("Authorization", "Bearer $token")
                                .addHeader("X-User-Agent", "Model: MAG250; Link: WiFi")
                                .build()
                            val res = client.newCall(req).execute()
                            val body = res.body?.string() ?: continue
                            val parsed = extractChannelsArray(body)
                            if (parsed != null && parsed.length() > 0) {
                                curDataArray = parsed
                                workingQuery = queryPat
                                workingSuffix = suf
                                try {
                                    val root = JSONObject(body.trim())
                                    val jsObj = root.optJSONObject("js") ?: root
                                    lastTotal = jsObj.optInt("total_items", 0)
                                    lastMax = jsObj.optInt("max_page_items", 14).takeIf { it > 0 } ?: 14
                                } catch (ignored: Exception) {}
                                break@candidateLoop
                            }
                        } catch (ignored: Exception) {}
                    }
                }

                if (curDataArray == null || curDataArray.length() == 0) {
                    break
                }

                if (page == 1) {
                    totalItems = lastTotal
                    maxPageItems = lastMax
                }

                for (i in 0 until curDataArray.length()) {
                    val obj = curDataArray.optJSONObject(i) ?: continue
                    val ch = parseChannelFromJson(
                        obj = obj,
                        endpoint = endpoint,
                        cleanMac = cleanMac,
                        token = token,
                        baseUrl = baseUrl,
                        playlistId = playlistId,
                        genreMap = genreMap,
                        indexHint = (page - 1) * maxPageItems + i,
                        allowedCountries = allowedCountries
                    )
                    if (ch != null) {
                        result.add(ch)
                    }
                }

                if (totalItems > 0 && (page * maxPageItems) >= totalItems) {
                    break
                }
                if (curDataArray.length() < maxPageItems) {
                    break
                }

                page++
            }
        } catch (e: Exception) {
            Log.w("MacPortalClient", "Error in fetchChannelsWithDirectPagination", e)
        }
        return result
    }

    private fun parseChannelFromJson(
        obj: JSONObject,
        endpoint: String,
        cleanMac: String,
        token: String,
        baseUrl: String,
        playlistId: String,
        fallbackGroup: String = "",
        genreMap: Map<String, String> = emptyMap(),
        indexHint: Int = 0,
        allowedCountries: Set<String> = emptySet()
    ): ChannelItem? {
        val id = obj.optString("id", obj.optString("ch_id", obj.optString("channel_id", obj.optString("number", "${indexHint}")))).trim()
        val rawName = obj.optString("name", obj.optString("caption", obj.optString("title", obj.optString("tv_name", obj.optString("channel_name", ""))))).trim()
        if (rawName.isBlank()) return null

        val rawCmd = obj.optString("cmd", obj.optString("url", obj.optString("stream_url", obj.optString("link", obj.optString("stream", ""))))).trim()
        var logo = obj.optString("logo", obj.optString("logo_url", obj.optString("screenshot_uri", obj.optString("icon", obj.optString("image", ""))))).trim()
        val tvgId = obj.optString("tvg_id", obj.optString("xmltv_id", obj.optString("epg_channel_id", id))).ifBlank { id }
        val tvgName = obj.optString("tvg_name", obj.optString("channel_name", rawName)).ifBlank { rawName }

        val genreId = obj.optString("tv_genre_id", obj.optString("genre_id", obj.optString("category_id", obj.optString("cat_id", obj.optString("genre", ""))))).trim()
        val genreFromMap = genreMap[genreId]
        val genreFromName = obj.optString("category_name", obj.optString("genre_title", obj.optString("group", obj.optString("genre_name", fallbackGroup)))).trim()

        if (logo.isNotBlank() && !logo.startsWith("http://", ignoreCase = true) && !logo.startsWith("https://", ignoreCase = true)) {
            logo = if (logo.startsWith("/")) "$baseUrl$logo" else "$baseUrl/$logo"
        }

        val streamUrl = extractPlayableStreamUrl(endpoint, cleanMac, token, rawCmd, typeParam = "itv")

        var effectiveGroup = when {
            fallbackGroup.isNotBlank() && fallbackGroup != "All" && fallbackGroup != "Alle" -> fallbackGroup
            !genreFromMap.isNullOrBlank() -> genreFromMap
            genreFromName.isNotBlank() && genreFromName != "All" && genreFromName != "Alle" -> genreFromName
            else -> ""
        }

        val isCategoryMatched = allowedCountries.isEmpty() ||
            ContentClassifier.isCategoryMatchingCountries(effectiveGroup, allowedCountries)

        // Fast O(1) pre-filter before expensive ContentClassifier & CountryResolver regex evaluation
        if (allowedCountries.isNotEmpty() && !isCategoryMatched) {
            val (fastDach, fastCountry) = ContentClassifier.detectCountryAndDach("", effectiveGroup)
            val wantsDach = allowedCountries.any { it in setOf("DE", "AT", "CH") }
            val fastMatches = (fastDach && wantsDach) ||
                fastCountry in allowedCountries ||
                ("ADULT" in allowedCountries && ContentClassifier.isAdultGroup(effectiveGroup))
            if (!fastMatches) {
                return null
            }
        }

        val isRadio = ContentClassifier.isRadioStation(rawName, effectiveGroup, streamUrl, "live")
        val classification = ContentClassifier.classify(
            name = rawName,
            group = effectiveGroup,
            streamUrl = streamUrl,
            rawCmd = rawCmd,
            tvgType = if (isRadio) "radio" else "live"
        )

        val resolvedGroup = if (effectiveGroup.isNotBlank()) effectiveGroup else classification.groupTitle

        if (ContentClassifier.isHeaderOrDividerChannel(rawName, streamUrl, resolvedGroup)) {
            return null
        }

        val parsedHierarchy = com.example.data.category.ProviderCategoryEngine.parseCategory(
            rawGroup = resolvedGroup,
            providerType = com.example.data.country.ProviderType.MAC,
            fallbackName = ""
        )

        val countryResult = com.example.data.country.CountryResolver.resolve(
            title = rawName,
            groupTitle = resolvedGroup,
            tvgId = tvgId,
            tvgName = tvgName,
            streamUrl = streamUrl,
            providerType = com.example.data.country.ProviderType.MAC,
            ignoreChannelName = true
        )

        val providerCatCountry = com.example.data.country.CountryNormalizer.extractCountryFromCategory(resolvedGroup)
            ?: parsedHierarchy.countryCode.takeIf { it !in setOf("OTHER", "INT", "UNKNOWN", "CONFLICT", "SPORT") }

        val resolvedCountryCode = when {
            ContentClassifier.isAtOrAustriaContent("", resolvedGroup) -> "AT"
            ContentClassifier.isChOrSwissContent("", resolvedGroup) -> "CH"
            providerCatCountry != null && (countryResult.countryCode in setOf("UNKNOWN", "CONFLICT", "OTHER") || providerCatCountry in setOf("DE", "AT", "CH", "ADULT")) -> providerCatCountry
            countryResult.countryCode !in setOf("UNKNOWN", "CONFLICT", "OTHER") -> countryResult.countryCode
            classification.countryCode !in setOf("INT", "OTHER", "UNKNOWN") -> classification.countryCode
            isCategoryMatched && allowedCountries.size == 1 -> allowedCountries.first()
            isCategoryMatched && allowedCountries.any { it in setOf("DE", "AT", "CH") } -> "DE"
            else -> "OTHER"
        }

        val normalizedCountry = com.example.data.country.CountryConstants.normalizeCountryCode(resolvedCountryCode)
        val isDachChannel = countryResult.isDach || classification.isDach || normalizedCountry in setOf("DE", "AT", "CH")

        if (allowedCountries.isNotEmpty() && !isCategoryMatched) {
            val matchesAllowed = normalizedCountry in allowedCountries ||
                countryResult.countryCode.uppercase() in allowedCountries ||
                (isDachChannel && allowedCountries.any { it in setOf("DE", "AT", "CH") }) ||
                ("ADULT" in allowedCountries && (normalizedCountry == "ADULT" || ContentClassifier.isAdultGroup(resolvedGroup)))
            if (!matchesAllowed) {
                return null
            }
        }

        val numOrder = obj.optInt("number", obj.optInt("num", indexHint))

        return ChannelItem(
            id = UUID.randomUUID().toString(),
            playlistId = playlistId,
            name = rawName,
            streamUrl = streamUrl,
            logoUrl = if (logo.isNotBlank()) logo else null,
            groupTitle = resolvedGroup,
            providerCategory = resolvedGroup,
            parentCategory = parsedHierarchy.parentCategory,
            subCategory = parsedHierarchy.subCategory,
            providerSortOrder = numOrder,
            type = if (isRadio) ContentType.RADIO else ContentType.LIVE_TV,
            seriesTitle = null,
            seasonNumber = null,
            episodeNumber = null,
            tvgId = tvgId,
            tvgName = tvgName,
            isDach = isDachChannel,
            countryCode = normalizedCountry,
            originalCountry = resolvedCountryCode,
            sourceId = playlistId,
            providerId = tvgId ?: rawName,
            is24_7 = classification.is24_7,
            isVip = classification.isVip,
            countryConfidence = if (providerCatCountry != null && countryResult.confidence < 85) 90 else countryResult.confidence,
            classificationStatus = if (providerCatCountry != null) com.example.data.country.ClassificationStatus.VERIFIED.name else countryResult.status.name,
            countryEvidenceJson = countryResult.evidenceDescriptions.joinToString("; "),
            normalizedChannelId = countryResult.normalizedChannelId,
            normalizedTitle = com.example.data.country.CountryNormalizer.cleanText(rawName)
        )
    }

    private fun sortLiveTvChannels(channels: List<ChannelItem>): List<ChannelItem> {
        return channels.sortedWith(
            compareBy<ChannelItem> { it.dachSortRank }
                .thenBy { it.liveTvSortRank }
                .thenBy { it.cleanSortTitle.lowercase() }
        )
    }

    private suspend fun fetchVodMovies(
        endpoint: String,
        cleanMac: String,
        token: String,
        baseUrl: String,
        playlistId: String,
        syncOnlyGerman: Boolean = false,
        allowedCountries: Set<String> = emptySet(),
        onBatchParsed: (suspend (batch: List<ChannelItem>, isDachBatch: Boolean) -> Unit)? = null,
        onCategoriesDiscovered: (suspend (List<com.example.data.model.ProviderCategoryEntity>) -> Unit)? = null,
        onCountUpdate: ((Int) -> Unit)? = null
    ): List<ChannelItem> = withContext(Dispatchers.IO) {
        try {
            val mode = protocolModeCache[endpoint] ?: MacProtocolMode.JS_XML
            val suffixes = listOf(mode.querySuffix, "&JsHttpRequest=1-xml", "", "&JsHttpRequest=1-utf").distinct()
            var catsArray: JSONArray? = null

            val catActions = listOf("get_categories", "get_genres")
            var usedAction = ""
            actionLoop@ for (actionName in catActions) {
                for (suf in suffixes) {
                    val categoriesUrl = "$endpoint?type=vod&action=$actionName&mac=$cleanMac&token=$token$suf"
                    val catsReq = Request.Builder()
                        .url(categoriesUrl)
                        .addHeader("User-Agent", STB_USER_AGENT)
                        .addHeader("Cookie", "mac=$cleanMac; stb_lang=en; timezone=Europe/Berlin; token=$token")
                        .addHeader("Authorization", "Bearer $token")
                        .addHeader("X-User-Agent", "Model: MAG250; Link: WiFi")
                        .build()

                    try {
                        val catsRes = client.newCall(catsReq).execute()
                        val catsBody = catsRes.body?.string() ?: ""
                        val arr = extractChannelsArray(catsBody)
                        if (arr != null && arr.length() > 0) {
                            catsArray = arr
                            usedAction = actionName
                            break@actionLoop
                        }
                    } catch (ignored: Exception) {}
                }
            }

            // Also fetch VOD genre dictionary (e.g. Horror, Comedy, Action) when get_categories returned broad categories
            val vodGenreMap = HashMap<String, String>()
            if (usedAction == "get_categories") {
                for (suf in suffixes.take(2)) {
                    val genresUrl = "$endpoint?type=vod&action=get_genres&mac=$cleanMac&token=$token$suf"
                    val genresReq = Request.Builder()
                        .url(genresUrl)
                        .addHeader("User-Agent", STB_USER_AGENT)
                        .addHeader("Cookie", "mac=$cleanMac; stb_lang=en; timezone=Europe/Berlin; token=$token")
                        .addHeader("Authorization", "Bearer $token")
                        .addHeader("X-User-Agent", "Model: MAG250; Link: WiFi")
                        .build()
                    try {
                        val gRes = client.newCall(genresReq).execute()
                        val gBody = gRes.body?.string() ?: ""
                        val gArr = extractChannelsArray(gBody)
                        if (gArr != null && gArr.length() > 0) {
                            for (i in 0 until gArr.length()) {
                                val gObj = gArr.optJSONObject(i) ?: continue
                                val gId = gObj.optString("id", "").trim()
                                val gTitle = gObj.optString("title", gObj.optString("name", "")).trim()
                                if (gId.isNotBlank() && gId != "*" && gTitle.isNotBlank() && !gTitle.equals("All", ignoreCase = true) && !gTitle.equals("Alle", ignoreCase = true)) {
                                    vodGenreMap[gId] = gTitle
                                }
                            }
                            break
                        }
                    } catch (ignored: Exception) {}
                }
            }

            val categoryList = mutableListOf<Pair<String, String>>()
            if (catsArray != null) {
                for (i in 0 until catsArray.length()) {
                    val catObj = catsArray.optJSONObject(i) ?: continue
                    val catId = catObj.optString("id", "").trim()
                    val catTitle = catObj.optString("title", catObj.optString("name", "")).trim()
                    if (catId.isNotBlank() && catId != "*" && !catTitle.equals("All", ignoreCase = true) && !catTitle.equals("Alle", ignoreCase = true)) {
                        categoryList.add(catId to catTitle)
                    }
                }
            }

            val effectiveAllowed = if (allowedCountries.isNotEmpty()) {
                allowedCountries.map { it.uppercase() }.toSet()
            } else if (syncOnlyGerman) {
                setOf("DE", "AT", "CH", "ADULT")
            } else {
                emptySet()
            }
            val filterByCountry = effectiveAllowed.isNotEmpty()
            val filterGermanVod = syncOnlyGerman || (effectiveAllowed.isNotEmpty() && effectiveAllowed.all { it in setOf("DE", "AT", "CH", "ADULT") })

            if (onCategoriesDiscovered != null && categoryList.isNotEmpty()) {
                var vodOrder = 0
                val discoveredVod = categoryList.map { (catId, catTitle) ->
                    val parsed = com.example.data.category.ProviderCategoryEngine.parseCategory(
                        rawGroup = catTitle,
                        providerType = com.example.data.country.ProviderType.MAC
                    )
                    val isSynced = !filterByCountry ||
                        ContentClassifier.isCategoryMatchingCountries(catTitle, effectiveAllowed, isVodOrSeries = true) ||
                        parsed.countryCode in effectiveAllowed
                    com.example.data.model.ProviderCategoryEntity(
                        id = "${playlistId}_MOVIE_${catId}",
                        playlistId = playlistId,
                        contentType = ContentType.MOVIE,
                        categoryId = catId,
                        rawCategoryName = catTitle,
                        countryCode = parsed.countryCode,
                        parentCategory = parsed.parentCategory,
                        subCategory = parsed.subCategory,
                        sortOrder = vodOrder++,
                        isSynced = isSynced
                    )
                }
                onCategoriesDiscovered(discoveredVod)
            }

            val priorityCategories = mutableListOf<Pair<String, String>>()
            val standardCategories = mutableListOf<Pair<String, String>>()

            for (pair in categoryList) {
                val parsedCat = com.example.data.category.ProviderCategoryEngine.parseCategory(
                    rawGroup = pair.second,
                    providerType = com.example.data.country.ProviderType.MAC
                )
                if (filterByCountry) {
                    if (ContentClassifier.isCategoryMatchingCountries(pair.second, effectiveAllowed, isVodOrSeries = true) ||
                        parsedCat.countryCode in effectiveAllowed
                    ) {
                        priorityCategories.add(pair)
                    } else if (parsedCat.countryCode in setOf("OTHER", "INT", "UNKNOWN")) {
                        standardCategories.add(pair)
                    }
                } else if (ContentClassifier.isGermanOrDachVodCategory(pair.second) || parsedCat.countryCode in setOf("DE", "AT", "CH")) {
                    priorityCategories.add(pair)
                } else {
                    standardCategories.add(pair)
                }
            }

            // Fallback: if portal VOD categories have no country prefixes at all (e.g. "Action", "Kino", "4K", "Drama"),
            // include all non-foreign categories so VOD movies and subcategories are 100% preserved!
            if (priorityCategories.isEmpty() && standardCategories.isNotEmpty()) {
                priorityCategories.addAll(standardCategories)
                standardCategories.clear()
            } else if (priorityCategories.isEmpty() && categoryList.isEmpty()) {
                priorityCategories.add("*" to "DE | Filme")
            }

            val movies = mutableListOf<ChannelItem>()
            val seenMovieKeys = HashSet<String>()

            // Fetch ALL matched priority VOD categories 1:1 with generous page limit so subcategories (Horror, Comedy, etc.) are completely loaded
            val maxPagesPerCat = when {
                priorityCategories.size <= 5 -> 60
                priorityCategories.size <= 15 -> 40
                else -> 25
            }
            val priorityChunks = priorityCategories.chunked(6)
            for (chunk in priorityChunks) {
                coroutineScope {
                    val deferredList = chunk.map { (catId, catTitle) ->
                        async(Dispatchers.IO) {
                            requestSemaphore.withPermit {
                                val catMovies = fetchMoviesForCategory(
                                    endpoint = endpoint,
                                    cleanMac = cleanMac,
                                    token = token,
                                    baseUrl = baseUrl,
                                    playlistId = playlistId,
                                    catId = catId,
                                    catTitle = catTitle,
                                    maxPages = maxPagesPerCat,
                                    filterGermanVod = false,
                                    defaultCountryCode = if (filterGermanVod) "DE" else null,
                                    vodGenreMap = vodGenreMap
                                )
                                if (catMovies.isNotEmpty()) {
                                    batchMutex.lock()
                                    try {
                                        val newlyAdded = mutableListOf<ChannelItem>()
                                        for (m in catMovies) {
                                            val key = "${m.providerId.ifBlank { m.name.lowercase().trim() }}_${m.groupTitle.lowercase().trim()}"
                                            if (seenMovieKeys.add(key)) {
                                                movies.add(m)
                                                newlyAdded.add(m)
                                            }
                                        }
                                        if (newlyAdded.isNotEmpty()) {
                                            onBatchParsed?.invoke(newlyAdded, true)
                                            onCountUpdate?.invoke(movies.size)
                                        }
                                    } finally {
                                        batchMutex.unlock()
                                    }
                                }
                                catMovies
                            }
                        }
                    }
                    deferredList.awaitAll()
                }
            }

            if (!filterByCountry && standardCategories.isNotEmpty()) {
                val standardChunks = standardCategories.chunked(6)
                for (chunk in standardChunks) {
                    coroutineScope {
                        val deferredList = chunk.map { (catId, catTitle) ->
                            async(Dispatchers.IO) {
                                requestSemaphore.withPermit {
                                    val catMovies = fetchMoviesForCategory(
                                        endpoint = endpoint,
                                        cleanMac = cleanMac,
                                        token = token,
                                        baseUrl = baseUrl,
                                        playlistId = playlistId,
                                        catId = catId,
                                        catTitle = catTitle,
                                        maxPages = 15,
                                        filterGermanVod = false,
                                        vodGenreMap = vodGenreMap
                                    )
                                    if (catMovies.isNotEmpty()) {
                                        batchMutex.lock()
                                        try {
                                            val newlyAdded = mutableListOf<ChannelItem>()
                                            for (m in catMovies) {
                                                val key = "${m.providerId.ifBlank { m.name.lowercase().trim() }}_${m.groupTitle.lowercase().trim()}"
                                                if (seenMovieKeys.add(key)) {
                                                    movies.add(m)
                                                    newlyAdded.add(m)
                                                }
                                            }
                                            if (newlyAdded.isNotEmpty()) {
                                                onBatchParsed?.invoke(newlyAdded, false)
                                                onCountUpdate?.invoke(movies.size)
                                            }
                                        } finally {
                                            batchMutex.unlock()
                                        }
                                    }
                                    catMovies
                                }
                            }
                        }
                        deferredList.awaitAll()
                    }
                }
            }

            Log.i("MacPortalClient", "Successfully fetched ${movies.size} VOD movies across ${priorityCategories.size + standardCategories.size} categories")
            movies
        } catch (e: Exception) {
            Log.e("MacPortalClient", "Error fetching VOD movies", e)
            emptyList()
        }
    }

    private fun resolveSubcategoryGroup(
        catTitle: String,
        itemObj: JSONObject,
        genreMap: Map<String, String>,
        defaultFallback: String
    ): String {
        val baseCat = catTitle.trim().ifBlank { defaultFallback }
        val explicitGenreStr = itemObj.optString("genres_str", itemObj.optString("genre", "")).trim()
            .substringBefore(",")
            .substringBefore("/")
            .trim()
        val genreId = itemObj.optString(
            "cat_genre_id_1",
            itemObj.optString("genre_id_1", itemObj.optString("genre_id", ""))
        ).trim()
        val mappedGenre = when {
            explicitGenreStr.isNotBlank() && !explicitGenreStr.equals("All", ignoreCase = true) && !explicitGenreStr.all { it.isDigit() } -> explicitGenreStr
            genreId.isNotBlank() && genreId != "0" && genreMap.containsKey(genreId) -> genreMap[genreId] ?: ""
            else -> ""
        }
        if (mappedGenre.isBlank()) return baseCat

        // Check if baseCat is a broad country/umbrella category without a specific subcategory yet
        val parsed = com.example.data.category.ProviderCategoryEngine.parseCategory(
            rawGroup = baseCat,
            providerType = com.example.data.country.ProviderType.MAC
        )
        val subUpper = parsed.subCategory.uppercase().trim()
        val isGenericUmbrella = subUpper in setOf(
            "ALLGEMEIN", "DEUTSCHLAND", "DEUTSCH", "GERMAN", "GERMANY", "DE",
            "FILME", "MOVIES", "VOD", "SERIEN", "SERIES", "DE FILME", "DE SERIEN",
            "AUSTRIA", "ÖSTERREICH", "SWISS", "SCHWEIZ"
        )
        return if (isGenericUmbrella && !baseCat.contains(mappedGenre, ignoreCase = true)) {
            "$baseCat | $mappedGenre"
        } else {
            baseCat
        }
    }

    private fun fetchMoviesForCategory(
        endpoint: String,
        cleanMac: String,
        token: String,
        baseUrl: String,
        playlistId: String,
        catId: String,
        catTitle: String,
        maxPages: Int = 30,
        filterGermanVod: Boolean = false,
        defaultCountryCode: String? = "DE",
        vodGenreMap: Map<String, String> = emptyMap()
    ): List<ChannelItem> {
        val result = mutableListOf<ChannelItem>()
        try {
            val mode = protocolModeCache[endpoint] ?: MacProtocolMode.JS_XML
            val suffixes = listOf(mode.querySuffix, "&JsHttpRequest=1-xml", "").distinct()

            val cachedTemplate = vodQueryTemplateCache[endpoint]
            var page = 1
            var workingQueryPrefix = cachedTemplate?.first?.replace("{CAT_ID}", catId)
                ?: "type=vod&action=get_ordered_list&category=$catId"
            var workingSuffix = cachedTemplate?.second ?: suffixes.first()

            val queryTemplates = listOf(
                "type=vod&action=get_ordered_list&category={CAT_ID}",
                "type=vod&action=get_ordered_list&movie_id=0&season_id=0&episode_id=0&row=0&category={CAT_ID}&fav=0&sortby=added&hd=0&not_ended=0",
                "type=vod&action=get_ordered_list&genre={CAT_ID}"
            )

            var maxPageItems = -1
            var totalItemsForCat = -1
            val seenIdsInCat = HashSet<String>()

            while (page <= maxPages) {
                var movieArray: JSONArray? = null

                val prefixCandidates = if (page == 1 && cachedTemplate == null) {
                    queryTemplates.map { it.replace("{CAT_ID}", catId) }
                } else if (page == 1 && cachedTemplate != null) {
                    (listOf(workingQueryPrefix) + queryTemplates.map { it.replace("{CAT_ID}", catId) }).distinct()
                } else {
                    listOf(workingQueryPrefix)
                }
                val suffixCandidates = if (page == 1 && cachedTemplate == null) suffixes else listOf(workingSuffix)

                candidateLoop@ for (qPrefix in prefixCandidates) {
                    for (suf in suffixCandidates) {
                        val pUrl = "$endpoint?$qPrefix&p=$page&mac=$cleanMac&token=$token$suf"
                        val req = Request.Builder()
                            .url(pUrl)
                            .addHeader("User-Agent", STB_USER_AGENT)
                            .addHeader("Cookie", "mac=$cleanMac; stb_lang=en; timezone=Europe/Berlin; token=$token")
                            .addHeader("Authorization", "Bearer $token")
                            .addHeader("X-User-Agent", "Model: MAG250; Link: WiFi")
                            .build()

                        try {
                            val res = client.newCall(req).execute()
                            val body = res.body?.string() ?: ""

                            try {
                                val rootObj = JSONObject(body)
                                val jsObj = rootObj.optJSONObject("js") ?: rootObj
                                if (totalItemsForCat == -1 && jsObj.has("total_items")) {
                                    totalItemsForCat = jsObj.optString("total_items", "-1").toIntOrNull() ?: -1
                                }
                                if (maxPageItems == -1 && jsObj.has("max_page_items")) {
                                    maxPageItems = jsObj.optString("max_page_items", "-1").toIntOrNull() ?: -1
                                }
                            } catch (ignored: Exception) {}

                            val arr = extractChannelsArray(body)
                            if (arr != null && arr.length() > 0) {
                                movieArray = arr
                                workingQueryPrefix = qPrefix
                                workingSuffix = suf
                                if (cachedTemplate == null) {
                                    val matchingTpl = queryTemplates.firstOrNull { it.replace("{CAT_ID}", catId) == qPrefix }
                                    if (matchingTpl != null) {
                                        vodQueryTemplateCache[endpoint] = matchingTpl to suf
                                    }
                                }
                                break@candidateLoop
                            }
                        } catch (ignored: Exception) {}
                    }
                }

                if (movieArray == null || movieArray.length() == 0) {
                    break
                }

                val isSeriesCategory = catTitle.uppercase().let {
                    it.contains("SERIEN") || it.contains("SERIES") || it.contains("STAFFEL") || it.contains("TV SHOW")
                }

                val pageLen = movieArray.length()
                if (maxPageItems <= 0) {
                    maxPageItems = pageLen
                }
                var newlyAddedOnPage = 0

                for (i in 0 until pageLen) {
                    val mObj = movieArray.optJSONObject(i) ?: continue
                    val mId = mObj.optString("id", "").trim()
                    val rawName = mObj.optString("name", mObj.optString("o_name", mObj.optString("title", "Movie"))).trim()
                    if (rawName.isBlank()) continue
                    val dedupKey = mId.ifBlank { rawName }
                    if (!seenIdsInCat.add(dedupKey)) continue
                    newlyAddedOnPage++

                    if (filterGermanVod && !ContentClassifier.isGermanOrDachVodItem(rawName, catTitle)) {
                        continue
                    }
                    var rawCmd = mObj.optString("cmd", mId).trim()
                    if (rawCmd.isBlank()) rawCmd = mId

                    var poster = mObj.optString("screenshot_uri", mObj.optString("pic", mObj.optString("poster", mObj.optString("cover", "")))).trim()
                    if (poster.isNotBlank() && !poster.startsWith("http://", ignoreCase = true) && !poster.startsWith("https://", ignoreCase = true)) {
                        poster = if (poster.startsWith("/")) "$baseUrl$poster" else "$baseUrl/$poster"
                    }

                    val desc = mObj.optString("description", mObj.optString("plot", ""))
                    val timeStr = mObj.optString("time", "0").trim()
                    val durationSec = timeStr.filter { it.isDigit() }.toIntOrNull()?.let { it * 60 }

                    val resBadge = when {
                        rawName.contains("4K", ignoreCase = true) || rawName.contains("UHD", ignoreCase = true) -> "4K"
                        rawName.contains("1080", ignoreCase = true) || rawName.contains("FHD", ignoreCase = true) -> "FHD"
                        else -> "HD"
                    }

                    val effectiveGroup = resolveSubcategoryGroup(catTitle, mObj, vodGenreMap, "DE: Filme")
                    val parsedHierarchy = com.example.data.category.ProviderCategoryEngine.parseCategory(
                        rawGroup = effectiveGroup,
                        providerType = com.example.data.country.ProviderType.MAC,
                        fallbackName = rawName
                    )
                    val (detectedDach, detectedCountry) = ContentClassifier.detectCountryAndDach(rawName, effectiveGroup)
                    val countryCode = when {
                        ContentClassifier.isAtOrAustriaContent(rawName, effectiveGroup) -> "AT"
                        ContentClassifier.isChOrSwissContent(rawName, effectiveGroup) -> "CH"
                        parsedHierarchy.countryCode !in setOf("OTHER", "INT", "UNKNOWN", "CONFLICT", "SPORT", "VIP") -> parsedHierarchy.countryCode
                        detectedCountry in setOf("DE", "AT", "CH", "ADULT") -> detectedCountry
                        defaultCountryCode != null -> defaultCountryCode
                        detectedDach -> "DE"
                        else -> detectedCountry.takeIf { it !in setOf("INT", "OTHER", "UNKNOWN", "SPORT", "VIP") } ?: "DE"
                    }
                    val isDach = countryCode in setOf("DE", "AT", "CH") || detectedDach
                    val isVip = ContentClassifier.detectVip(rawName, effectiveGroup)
                    val isSeriesItem = isSeriesCategory || mObj.optInt("is_series", 0) == 1 || mObj.optString("is_series", "") == "1"
                    val streamUrl = extractPlayableStreamUrl(
                        endpoint = endpoint,
                        cleanMac = cleanMac,
                        token = token,
                        rawCmd = rawCmd,
                        seriesParam = if (isSeriesItem) "1" else "",
                        typeParam = "vod"
                    )

                    result.add(
                        ChannelItem(
                            id = UUID.randomUUID().toString(),
                            playlistId = playlistId,
                            name = rawName,
                            streamUrl = streamUrl,
                            logoUrl = if (poster.isNotBlank()) poster else null,
                            posterUrl = if (poster.isNotBlank()) poster else null,
                            groupTitle = effectiveGroup,
                            providerCategory = effectiveGroup,
                            parentCategory = parsedHierarchy.parentCategory,
                            subCategory = parsedHierarchy.subCategory,
                            providerSortOrder = ((page - 1) * maxPageItems.coerceAtLeast(1)) + i + 1,
                            type = if (isSeriesItem) ContentType.SERIES else ContentType.MOVIE,
                            seriesTitle = if (isSeriesItem) rawName else null,
                            seasonNumber = if (isSeriesItem) 1 else null,
                            episodeNumber = if (isSeriesItem) 1 else null,
                            plot = if (desc.isNotBlank()) desc else null,
                            durationSeconds = durationSec ?: 0,
                            resolutionBadge = resBadge,
                            isDach = isDach,
                            countryCode = com.example.data.country.CountryConstants.normalizeCountryCode(countryCode),
                            originalCountry = countryCode,
                            sourceId = playlistId,
                            providerId = mId.ifBlank { rawName },
                            isVip = isVip
                        )
                    )
                }

                if (newlyAddedOnPage == 0) {
                    break
                }
                if (totalItemsForCat > 0 && seenIdsInCat.size >= totalItemsForCat) {
                    break
                }
                if (maxPageItems > 0 && pageLen < maxPageItems) {
                    break
                }

                page++
            }
        } catch (e: Exception) {
            Log.w("MacPortalClient", "Error fetching movies for category $catId: ${e.message}")
        }
        return result
    }

    private suspend fun fetchSeriesAndEpisodes(
        endpoint: String,
        cleanMac: String,
        token: String,
        baseUrl: String,
        playlistId: String,
        syncOnlyGerman: Boolean = false,
        allowedCountries: Set<String> = emptySet(),
        onBatchParsed: (suspend (batch: List<ChannelItem>, isDachBatch: Boolean) -> Unit)? = null,
        onCategoriesDiscovered: (suspend (List<com.example.data.model.ProviderCategoryEntity>) -> Unit)? = null,
        onCountUpdate: ((Int) -> Unit)? = null
    ): List<ChannelItem> = withContext(Dispatchers.IO) {
        try {
            val mode = protocolModeCache[endpoint] ?: MacProtocolMode.JS_XML
            val suffixes = listOf(mode.querySuffix, "&JsHttpRequest=1-xml", "", "&JsHttpRequest=1-utf").distinct()
            var catsArray: JSONArray? = null

            val catActions = listOf("get_categories", "get_genres")
            var usedAction = ""
            actionLoop@ for (actionName in catActions) {
                for (suf in suffixes) {
                    val categoriesUrl = "$endpoint?type=series&action=$actionName&mac=$cleanMac&token=$token$suf"
                    val catsReq = Request.Builder()
                        .url(categoriesUrl)
                        .addHeader("User-Agent", STB_USER_AGENT)
                        .addHeader("Cookie", "mac=$cleanMac; stb_lang=en; timezone=Europe/Berlin; token=$token")
                        .addHeader("Authorization", "Bearer $token")
                        .addHeader("X-User-Agent", "Model: MAG250; Link: WiFi")
                        .build()

                    try {
                        val catsRes = client.newCall(catsReq).execute()
                        val catsBody = catsRes.body?.string() ?: ""
                        val arr = extractChannelsArray(catsBody)
                        if (arr != null && arr.length() > 0) {
                            catsArray = arr
                            usedAction = actionName
                            break@actionLoop
                        }
                    } catch (ignored: Exception) {}
                }
            }

            val seriesGenreMap = HashMap<String, String>()
            if (usedAction == "get_categories") {
                for (suf in suffixes.take(2)) {
                    val genresUrl = "$endpoint?type=series&action=get_genres&mac=$cleanMac&token=$token$suf"
                    val genresReq = Request.Builder()
                        .url(genresUrl)
                        .addHeader("User-Agent", STB_USER_AGENT)
                        .addHeader("Cookie", "mac=$cleanMac; stb_lang=en; timezone=Europe/Berlin; token=$token")
                        .addHeader("Authorization", "Bearer $token")
                        .addHeader("X-User-Agent", "Model: MAG250; Link: WiFi")
                        .build()
                    try {
                        val gRes = client.newCall(genresReq).execute()
                        val gBody = gRes.body?.string() ?: ""
                        val gArr = extractChannelsArray(gBody)
                        if (gArr != null && gArr.length() > 0) {
                            for (i in 0 until gArr.length()) {
                                val gObj = gArr.optJSONObject(i) ?: continue
                                val gId = gObj.optString("id", "").trim()
                                val gTitle = gObj.optString("title", gObj.optString("name", "")).trim()
                                if (gId.isNotBlank() && gId != "*" && gTitle.isNotBlank() && !gTitle.equals("All", ignoreCase = true) && !gTitle.equals("Alle", ignoreCase = true)) {
                                    seriesGenreMap[gId] = gTitle
                                }
                            }
                            break
                        }
                    } catch (ignored: Exception) {}
                }
            }

            val categoryList = mutableListOf<Pair<String, String>>()
            if (catsArray != null) {
                for (i in 0 until catsArray.length()) {
                    val catObj = catsArray.optJSONObject(i) ?: continue
                    val catId = catObj.optString("id", "").trim()
                    val catTitle = catObj.optString("title", catObj.optString("name", "")).trim()
                    if (catId.isNotBlank() && catId != "*" && !catTitle.equals("All", ignoreCase = true) && !catTitle.equals("Alle", ignoreCase = true)) {
                        categoryList.add(catId to catTitle)
                    }
                }
            }

            val effectiveAllowed = if (allowedCountries.isNotEmpty()) {
                allowedCountries.map { it.uppercase() }.toSet()
            } else if (syncOnlyGerman) {
                setOf("DE", "AT", "CH", "ADULT")
            } else {
                emptySet()
            }
            val filterByCountry = effectiveAllowed.isNotEmpty()
            val filterGermanVod = syncOnlyGerman || (effectiveAllowed.isNotEmpty() && effectiveAllowed.all { it in setOf("DE", "AT", "CH", "ADULT") })

            if (onCategoriesDiscovered != null && categoryList.isNotEmpty()) {
                var seriesOrder = 0
                val discoveredSeries = categoryList.map { (catId, catTitle) ->
                    val parsed = com.example.data.category.ProviderCategoryEngine.parseCategory(
                        rawGroup = catTitle,
                        providerType = com.example.data.country.ProviderType.MAC
                    )
                    val isSynced = !filterByCountry ||
                        ContentClassifier.isCategoryMatchingCountries(catTitle, effectiveAllowed, isVodOrSeries = true) ||
                        parsed.countryCode in effectiveAllowed
                    com.example.data.model.ProviderCategoryEntity(
                        id = "${playlistId}_SERIES_${catId}",
                        playlistId = playlistId,
                        contentType = ContentType.SERIES,
                        categoryId = catId,
                        rawCategoryName = catTitle,
                        countryCode = parsed.countryCode,
                        parentCategory = parsed.parentCategory,
                        subCategory = parsed.subCategory,
                        sortOrder = seriesOrder++,
                        isSynced = isSynced
                    )
                }
                onCategoriesDiscovered(discoveredSeries)
            }

            val seriesEpisodes = mutableListOf<ChannelItem>()
            val seenEpisodeKeys = HashSet<String>()

            val matchedSeriesCategories = categoryList.filter { (_, title) ->
                val parsedCat = com.example.data.category.ProviderCategoryEngine.parseCategory(
                    rawGroup = title,
                    providerType = com.example.data.country.ProviderType.MAC
                )
                if (filterByCountry) {
                    ContentClassifier.isCategoryMatchingCountries(title, effectiveAllowed, isVodOrSeries = true) ||
                        parsedCat.countryCode in effectiveAllowed
                } else if (filterGermanVod) {
                    ContentClassifier.isGermanOrDachVodCategory(title) || parsedCat.countryCode in setOf("DE", "AT", "CH")
                } else {
                    true
                }
            }

            // Fallback: if portal Series categories have no explicit country prefixes (e.g. "Netflix", "Drama", "Action"),
            // include non-foreign categories so Series and subcategories are 100% preserved!
            val categoriesToFetch = when {
                matchedSeriesCategories.isNotEmpty() -> matchedSeriesCategories
                categoryList.isNotEmpty() -> categoryList.filter { (_, title) ->
                    val parsedCat = com.example.data.category.ProviderCategoryEngine.parseCategory(
                        rawGroup = title,
                        providerType = com.example.data.country.ProviderType.MAC
                    )
                    parsedCat.countryCode in setOf("DE", "AT", "CH", "OTHER", "INT", "UNKNOWN")
                }.ifEmpty { categoryList }
                else -> listOf("*" to "DE | Serien")
            }

            val maxSeriesPages = when {
                categoriesToFetch.size <= 5 -> 50
                categoriesToFetch.size <= 15 -> 30
                else -> 20
            }
            val chunks = categoriesToFetch.chunked(6)
            for (chunk in chunks) {
                coroutineScope {
                    val deferredList = chunk.map { (catId, catTitle) ->
                        async(Dispatchers.IO) {
                            requestSemaphore.withPermit {
                                val catEps = fetchSeriesForCategory(
                                    endpoint = endpoint,
                                    cleanMac = cleanMac,
                                    token = token,
                                    baseUrl = baseUrl,
                                    playlistId = playlistId,
                                    catId = catId,
                                    catTitle = catTitle,
                                    maxPages = maxSeriesPages,
                                    filterGermanVod = false,
                                    defaultCountryCode = if (filterGermanVod) "DE" else null,
                                    seriesGenreMap = seriesGenreMap
                                )
                                if (catEps.isNotEmpty()) {
                                    batchMutex.lock()
                                    try {
                                        val newlyAdded = mutableListOf<ChannelItem>()
                                        for (ep in catEps) {
                                            val key = "${ep.providerId.ifBlank { ep.seriesTitle?.lowercase() ?: ep.name.lowercase() }}_${ep.seasonNumber}_${ep.episodeNumber}_${ep.groupTitle.lowercase()}"
                                            if (seenEpisodeKeys.add(key)) {
                                                seriesEpisodes.add(ep)
                                                newlyAdded.add(ep)
                                            }
                                        }
                                        if (newlyAdded.isNotEmpty()) {
                                            onBatchParsed?.invoke(newlyAdded, true)
                                            onCountUpdate?.invoke(seriesEpisodes.size)
                                        }
                                    } finally {
                                        batchMutex.unlock()
                                    }
                                }
                                catEps
                            }
                        }
                    }
                    deferredList.awaitAll()
                }
            }

            Log.i("MacPortalClient", "Successfully fetched ${seriesEpisodes.size} series across ${categoriesToFetch.size} categories")
            onCountUpdate?.invoke(seriesEpisodes.size)
            seriesEpisodes
        } catch (e: Exception) {
            Log.e("MacPortalClient", "Error fetching series", e)
            emptyList()
        }
    }

    private fun fetchSeriesForCategory(
        endpoint: String,
        cleanMac: String,
        token: String,
        baseUrl: String,
        playlistId: String,
        catId: String,
        catTitle: String,
        maxPages: Int = 25,
        filterGermanVod: Boolean = false,
        defaultCountryCode: String? = "DE",
        seriesGenreMap: Map<String, String> = emptyMap()
    ): List<ChannelItem> {
        val result = mutableListOf<ChannelItem>()
        try {
            val mode = protocolModeCache[endpoint] ?: MacProtocolMode.JS_XML
            val suffixes = listOf(mode.querySuffix, "&JsHttpRequest=1-xml", "").distinct()

            val cachedTemplate = seriesQueryTemplateCache[endpoint]
            var page = 1
            var workingQueryPrefix = cachedTemplate?.first?.replace("{CAT_ID}", catId)
                ?: "type=series&action=get_ordered_list&category=$catId"
            var workingSuffix = cachedTemplate?.second ?: suffixes.first()

            val queryTemplates = listOf(
                "type=series&action=get_ordered_list&category={CAT_ID}",
                "type=series&action=get_ordered_list&movie_id=0&season_id=0&episode_id=0&row=0&category={CAT_ID}&fav=0&sortby=added&hd=0&not_ended=0",
                "type=series&action=get_ordered_list&genre={CAT_ID}",
                "type=vod&action=get_ordered_list&category={CAT_ID}"
            )

            var maxPageItems = -1
            var totalItemsForCat = -1
            val seenSeriesIdsInCat = HashSet<String>()

            while (page <= maxPages) {
                var seriesArray: JSONArray? = null
                val prefixCandidates = if (page == 1 && cachedTemplate == null) {
                    queryTemplates.map { it.replace("{CAT_ID}", catId) }
                } else if (page == 1 && cachedTemplate != null) {
                    (listOf(workingQueryPrefix) + queryTemplates.map { it.replace("{CAT_ID}", catId) }).distinct()
                } else {
                    listOf(workingQueryPrefix)
                }
                val suffixCandidates = if (page == 1 && cachedTemplate == null) suffixes else listOf(workingSuffix)

                candidateLoop@ for (qPrefix in prefixCandidates) {
                    for (suf in suffixCandidates) {
                        val seriesListUrl = "$endpoint?$qPrefix&p=$page&mac=$cleanMac&token=$token$suf"
                        val req = Request.Builder()
                            .url(seriesListUrl)
                            .addHeader("User-Agent", STB_USER_AGENT)
                            .addHeader("Cookie", "mac=$cleanMac; stb_lang=en; timezone=Europe/Berlin; token=$token")
                            .addHeader("Authorization", "Bearer $token")
                            .addHeader("X-User-Agent", "Model: MAG250; Link: WiFi")
                            .build()

                        try {
                            val res = client.newCall(req).execute()
                            val body = res.body?.string() ?: ""

                            try {
                                val rootObj = JSONObject(body)
                                val jsObj = rootObj.optJSONObject("js") ?: rootObj
                                if (totalItemsForCat == -1 && jsObj.has("total_items")) {
                                    totalItemsForCat = jsObj.optString("total_items", "-1").toIntOrNull() ?: -1
                                }
                                if (maxPageItems == -1 && jsObj.has("max_page_items")) {
                                    maxPageItems = jsObj.optString("max_page_items", "-1").toIntOrNull() ?: -1
                                }
                            } catch (ignored: Exception) {}

                            val arr = extractChannelsArray(body)
                            if (arr != null && arr.length() > 0) {
                                seriesArray = arr
                                workingQueryPrefix = qPrefix
                                workingSuffix = suf
                                if (cachedTemplate == null) {
                                    val matchingTpl = queryTemplates.firstOrNull { it.replace("{CAT_ID}", catId) == qPrefix }
                                    if (matchingTpl != null) {
                                        seriesQueryTemplateCache[endpoint] = matchingTpl to suf
                                    }
                                }
                                break@candidateLoop
                            }
                        } catch (ignored: Exception) {}
                    }
                }

                if (seriesArray == null || seriesArray.length() == 0) {
                    break
                }

                val seriesLen = seriesArray.length()
                if (maxPageItems <= 0) {
                    maxPageItems = seriesLen
                }
                var newlyAddedOnPage = 0

                for (i in 0 until seriesLen) {
                    val sObj = seriesArray.optJSONObject(i) ?: continue
                    val rawId = sObj.optString("id", sObj.optString("series_id", sObj.optString("movie_id", ""))).trim()
                    val seriesId = rawId.substringBefore(":")
                    val seriesTitle = sObj.optString("name", sObj.optString("o_name", sObj.optString("title", "Series"))).trim()
                    if (seriesTitle.isBlank()) continue
                    val dedupKey = seriesId.ifBlank { seriesTitle }
                    if (!seenSeriesIdsInCat.add(dedupKey)) continue
                    newlyAddedOnPage++

                    if (filterGermanVod && !ContentClassifier.isGermanOrDachVodItem(seriesTitle, catTitle)) {
                        continue
                    }
                    var poster = sObj.optString("screenshot_uri", sObj.optString("pic", sObj.optString("poster", sObj.optString("cover", "")))).trim()
                    if (poster.isNotBlank() && !poster.startsWith("http://", ignoreCase = true) && !poster.startsWith("https://", ignoreCase = true)) {
                        poster = if (poster.startsWith("/")) "$baseUrl$poster" else "$baseUrl/$poster"
                    }
                    val seriesDesc = sObj.optString("description", sObj.optString("plot", ""))
                    val effectiveGroup = resolveSubcategoryGroup(catTitle, sObj, seriesGenreMap, "DE: Serien")
                    val parsedCatHierarchy = com.example.data.category.ProviderCategoryEngine.parseCategory(
                        rawGroup = effectiveGroup,
                        providerType = com.example.data.country.ProviderType.MAC,
                        fallbackName = seriesTitle
                    )
                    val (detectedDach, detectedCountry) = ContentClassifier.detectCountryAndDach(seriesTitle, effectiveGroup)
                    val countryCode = when {
                        ContentClassifier.isAtOrAustriaContent(seriesTitle, effectiveGroup) -> "AT"
                        ContentClassifier.isChOrSwissContent(seriesTitle, effectiveGroup) -> "CH"
                        parsedCatHierarchy.countryCode !in setOf("OTHER", "INT", "UNKNOWN", "CONFLICT", "SPORT", "VIP") -> parsedCatHierarchy.countryCode
                        detectedCountry in setOf("DE", "AT", "CH", "ADULT") -> detectedCountry
                        defaultCountryCode != null -> defaultCountryCode
                        detectedDach -> "DE"
                        else -> detectedCountry.takeIf { it !in setOf("INT", "OTHER", "UNKNOWN", "SPORT", "VIP") } ?: "DE"
                    }
                    val isDach = countryCode in setOf("DE", "AT", "CH") || detectedDach
                    val normalizedCountry = com.example.data.country.CountryConstants.normalizeCountryCode(countryCode)

                    val rawCmd = sObj.optString("cmd", seriesId)
                    val streamUrl = extractPlayableStreamUrl(
                        endpoint = endpoint,
                        cleanMac = cleanMac,
                        token = token,
                        rawCmd = rawCmd,
                        seriesParam = "1",
                        typeParam = "vod"
                    )

                    val provId = if (seriesId.isNotBlank()) seriesId else rawId.ifBlank { seriesTitle }
                    val sortOrd = ((page - 1) * maxPageItems.coerceAtLeast(1)) + i + 1

                    // Check if sObj already contains inline series episodes
                    val inlineSeriesArr = sObj.optJSONArray("series")
                    if (inlineSeriesArr != null && inlineSeriesArr.length() > 0) {
                        for (sIdx in 0 until inlineSeriesArr.length()) {
                            val epElem = inlineSeriesArr.opt(sIdx)
                            val epNum = if (epElem is JSONObject) {
                                epElem.optInt("series_num", epElem.optInt("episode_num", sIdx + 1))
                            } else {
                                epElem.toString().toIntOrNull() ?: (sIdx + 1)
                            }
                            val epCmd = if (epElem is JSONObject) epElem.optString("cmd", rawCmd) else rawCmd
                            val epStreamUrl = extractPlayableStreamUrl(
                                endpoint = endpoint,
                                cleanMac = cleanMac,
                                token = token,
                                rawCmd = epCmd,
                                seriesParam = epNum.toString(),
                                typeParam = "vod"
                            )
                            result.add(
                                ChannelItem(
                                    id = UUID.randomUUID().toString(),
                                    playlistId = playlistId,
                                    name = "$seriesTitle - Episode $epNum",
                                    streamUrl = epStreamUrl,
                                    logoUrl = if (poster.isNotBlank()) poster else null,
                                    posterUrl = if (poster.isNotBlank()) poster else null,
                                    groupTitle = effectiveGroup,
                                    providerCategory = effectiveGroup,
                                    parentCategory = parsedCatHierarchy.parentCategory,
                                    subCategory = parsedCatHierarchy.subCategory,
                                    providerSortOrder = sortOrd,
                                    type = ContentType.SERIES,
                                    seriesTitle = seriesTitle,
                                    seasonNumber = 1,
                                    episodeNumber = epNum,
                                    plot = if (seriesDesc.isNotBlank()) seriesDesc else null,
                                    isDach = isDach,
                                    countryCode = normalizedCountry,
                                    originalCountry = countryCode,
                                    sourceId = playlistId,
                                    providerId = provId
                                )
                            )
                        }
                    } else {
                        // Add series item - folder counts in SeriesExplorer count distinct seriesTitle
                        result.add(
                            ChannelItem(
                                id = UUID.randomUUID().toString(),
                                playlistId = playlistId,
                                name = seriesTitle,
                                streamUrl = streamUrl,
                                logoUrl = if (poster.isNotBlank()) poster else null,
                                posterUrl = if (poster.isNotBlank()) poster else null,
                                groupTitle = effectiveGroup,
                                providerCategory = effectiveGroup,
                                parentCategory = parsedCatHierarchy.parentCategory,
                                subCategory = parsedCatHierarchy.subCategory,
                                providerSortOrder = sortOrd,
                                type = ContentType.SERIES,
                                seriesTitle = seriesTitle,
                                seasonNumber = 1,
                                episodeNumber = 1,
                                plot = if (seriesDesc.isNotBlank()) seriesDesc else null,
                                isDach = isDach,
                                countryCode = normalizedCountry,
                                originalCountry = countryCode,
                                sourceId = playlistId,
                                providerId = provId
                            )
                        )
                    }
                }

                if (newlyAddedOnPage == 0) {
                    break
                }
                if (totalItemsForCat > 0 && seenSeriesIdsInCat.size >= totalItemsForCat) {
                    break
                }
                if (maxPageItems > 0 && seriesLen < maxPageItems) {
                    break
                }
                page++
            }
        } catch (e: Exception) {
            Log.w("MacPortalClient", "Error fetching series for category $catId: ${e.message}")
        }
        return result
    }

    suspend fun fetchSeriesEpisodes(
        portalUrl: String,
        macAddress: String,
        seriesId: String,
        seriesTitle: String,
        playlistId: String = ""
    ): List<ChannelItem> = withContext(Dispatchers.IO) {
        val episodes = mutableListOf<ChannelItem>()
        try {
            val cleanMac = PlaylistImportExtractor.cleanMacAddress(macAddress)
            val cleanUrl = PlaylistImportExtractor.cleanMacPortalUrl(portalUrl).ifBlank { portalUrl.trim() }
            val endpoint = resolvePortalEndpoint(cleanUrl, cleanMac)
            if (endpoint.isBlank() || cleanMac.isBlank() || seriesId.isBlank()) {
                return@withContext emptyList()
            }

            val handshake = getOrRefreshHandshakeToken(endpoint, cleanMac, forceRefresh = false)
            val token = handshake.token
            if (token.isBlank()) return@withContext emptyList()

            val baseUrl = extractBaseDomain(endpoint)
            val mode = protocolModeCache[endpoint] ?: MacProtocolMode.JS_XML
            val suffixes = listOf(mode.querySuffix, "&JsHttpRequest=1-xml", "", "&JsHttpRequest=1-utf").distinct()

            val numericId = seriesId.filter { it.isDigit() }
            val idCandidates = listOf(seriesId, numericId).filter { it.isNotBlank() }.distinct()

            val candidateQueries = mutableListOf<String>()
            for (id in idCandidates) {
                candidateQueries.add("type=series&action=get_ordered_list&movie_id=$id&p=1")
                candidateQueries.add("type=series&action=get_ordered_list&series_id=$id&p=1")
                candidateQueries.add("type=series&action=get_ordered_list&category=$id&p=1")
                candidateQueries.add("type=series&action=get_ordered_list&season_id=$id&p=1")
                candidateQueries.add("type=vod&action=get_ordered_list&movie_id=$id&p=1")
                candidateQueries.add("type=vod&action=get_ordered_list&series_id=$id&p=1")
                candidateQueries.add("type=series&action=get_ordered_list&movie_id=$id")
                candidateQueries.add("type=series&action=get_ordered_list&series_id=$id")
                candidateQueries.add("type=series&action=get_ordered_list&category=$id")
                candidateQueries.add("type=series&action=get_series_info&series_id=$id")
                candidateQueries.add("type=series&action=get_episodes&movie_id=$id")
                candidateQueries.add("type=series&action=get_episodes&series_id=$id")
                candidateQueries.add("type=series&action=get_ordered_list&movie_id=$id&p=0")
            }

            var epArray: JSONArray? = null

            candidateLoop@ for (cand in candidateQueries) {
                for (suf in suffixes) {
                    val epUrl = "$endpoint?$cand&mac=$cleanMac&token=$token$suf"
                    val req = Request.Builder()
                        .url(epUrl)
                        .addHeader("User-Agent", STB_USER_AGENT)
                        .addHeader("Cookie", "mac=$cleanMac; stb_lang=en; timezone=Europe/Berlin; token=$token")
                        .addHeader("Authorization", "Bearer $token")
                        .addHeader("X-User-Agent", "Model: MAG250; Link: WiFi")
                        .build()

                    try {
                        val res = client.newCall(req).execute()
                        val body = res.body?.string() ?: ""
                        val arr = extractChannelsArray(body)
                        if (arr != null && arr.length() > 0) {
                            epArray = arr
                            break@candidateLoop
                        }
                    } catch (_: Exception) {}
                }
            }

            if (epArray != null && epArray.length() > 0) {
                val (isDach, countryCode) = ContentClassifier.detectCountryAndDach(seriesTitle, "Serien")
                val normCountry = com.example.data.country.CountryConstants.normalizeIndexCountryCode(countryCode)

                for (i in 0 until epArray.length()) {
                    val itemObj = epArray.optJSONObject(i) ?: continue
                    val itemId = itemObj.optString("id", itemObj.optString("series_id", itemObj.optString("movie_id", ""))).trim()
                    val rawName = itemObj.optString("name", itemObj.optString("o_name", itemObj.optString("title", ""))).trim()
                    var rawCmd = itemObj.optString("cmd", itemObj.optString("url", itemId)).trim()
                    if (rawCmd.isBlank()) rawCmd = if (itemId.isNotBlank()) itemId else seriesId

                    var poster = itemObj.optString("screenshot_uri", itemObj.optString("pic", itemObj.optString("poster", ""))).trim()
                    if (poster.isNotBlank() && !poster.startsWith("http://", ignoreCase = true) && !poster.startsWith("https://", ignoreCase = true)) {
                        poster = if (poster.startsWith("/")) "$baseUrl$poster" else "$baseUrl/$poster"
                    }
                    val desc = itemObj.optString("description", itemObj.optString("plot", ""))
                    val timeStr = itemObj.optString("time", "0").trim()
                    val durationSec = timeStr.filter { it.isDigit() }.toIntOrNull()?.let { it * 60 } ?: 0

                    val seasonNumInObj = itemObj.optInt("season_num", itemObj.optInt("season", itemObj.optInt("season_number", 0)))
                    val parsedSeasonFromTitle = ContentClassifier.extractSeriesMetadata(rawName, seriesTitle).second
                    val defaultSeason = if (seasonNumInObj > 0) seasonNumInObj else (parsedSeasonFromTitle ?: (i + 1))

                    // Check Case A: The item is a SEASON containing an array of episodes (e.g. "series": [1, 2, 3, ...])
                    val seriesArray = itemObj.optJSONArray("series")
                    val filesArray = itemObj.optJSONArray("files")
                    val episodesArray = itemObj.optJSONArray("episodes")

                    if (seriesArray != null && seriesArray.length() > 0) {
                        for (sIdx in 0 until seriesArray.length()) {
                            val epElem = seriesArray.opt(sIdx)
                            val epNum: Int
                            val epTitle: String
                            val epCmd: String
                            if (epElem is JSONObject) {
                                epNum = epElem.optInt("series_num", epElem.optInt("episode_num", epElem.optInt("num", sIdx + 1)))
                                val specificTitle = epElem.optString("name", epElem.optString("title", "")).trim()
                                epTitle = if (specificTitle.isNotBlank() && !specificTitle.equals(seriesTitle, ignoreCase = true)) {
                                    specificTitle
                                } else {
                                    "Staffel $defaultSeason - Episode $epNum"
                                }
                                epCmd = epElem.optString("cmd", rawCmd)
                            } else {
                                epNum = epElem.toString().toIntOrNull() ?: (sIdx + 1)
                                epTitle = "Staffel $defaultSeason - Episode $epNum"
                                epCmd = rawCmd
                            }

                            val streamUrl = extractPlayableStreamUrl(
                                endpoint = endpoint,
                                cleanMac = cleanMac,
                                token = token,
                                rawCmd = epCmd,
                                seriesParam = epNum.toString(),
                                typeParam = "vod"
                            )

                            episodes.add(
                                ChannelItem(
                                    id = UUID.randomUUID().toString(),
                                    playlistId = playlistId,
                                    name = epTitle,
                                    streamUrl = streamUrl,
                                    logoUrl = if (poster.isNotBlank()) poster else null,
                                    groupTitle = "Serien",
                                    type = ContentType.SERIES,
                                    seriesTitle = seriesTitle,
                                    seasonNumber = defaultSeason,
                                    episodeNumber = epNum,
                                    plot = if (desc.isNotBlank()) desc else null,
                                    durationSeconds = durationSec,
                                    isDach = isDach,
                                    countryCode = normCountry,
                                    originalCountry = countryCode,
                                    sourceId = playlistId,
                                    providerId = seriesId
                                )
                            )
                        }
                    } else if (filesArray != null && filesArray.length() > 0) {
                        for (fIdx in 0 until filesArray.length()) {
                            val fObj = filesArray.optJSONObject(fIdx) ?: continue
                            val fNum = fObj.optInt("series_num", fObj.optInt("episode_num", fIdx + 1))
                            val fTitle = fObj.optString("name", fObj.optString("title", "Episode $fNum")).trim()
                            val fCmd = fObj.optString("cmd", rawCmd)
                            val streamUrl = extractPlayableStreamUrl(
                                endpoint = endpoint,
                                cleanMac = cleanMac,
                                token = token,
                                rawCmd = fCmd,
                                seriesParam = fNum.toString(),
                                typeParam = "vod"
                            )
                            episodes.add(
                                ChannelItem(
                                    id = UUID.randomUUID().toString(),
                                    playlistId = playlistId,
                                    name = fTitle,
                                    streamUrl = streamUrl,
                                    logoUrl = if (poster.isNotBlank()) poster else null,
                                    groupTitle = "Serien",
                                    type = ContentType.SERIES,
                                    seriesTitle = seriesTitle,
                                    seasonNumber = defaultSeason,
                                    episodeNumber = fNum,
                                    plot = if (desc.isNotBlank()) desc else null,
                                    durationSeconds = durationSec,
                                    isDach = isDach,
                                    countryCode = normCountry,
                                    originalCountry = countryCode,
                                    sourceId = playlistId,
                                    providerId = seriesId
                                )
                            )
                        }
                    } else if (episodesArray != null && episodesArray.length() > 0) {
                        for (eIdx in 0 until episodesArray.length()) {
                            val eObj = episodesArray.optJSONObject(eIdx) ?: continue
                            val eNum = eObj.optInt("series_num", eObj.optInt("episode_num", eIdx + 1))
                            val eTitle = eObj.optString("name", eObj.optString("title", "Episode $eNum")).trim()
                            val eCmd = eObj.optString("cmd", rawCmd)
                            val streamUrl = extractPlayableStreamUrl(
                                endpoint = endpoint,
                                cleanMac = cleanMac,
                                token = token,
                                rawCmd = eCmd,
                                seriesParam = eNum.toString(),
                                typeParam = "vod"
                            )
                            episodes.add(
                                ChannelItem(
                                    id = UUID.randomUUID().toString(),
                                    playlistId = playlistId,
                                    name = eTitle,
                                    streamUrl = streamUrl,
                                    logoUrl = if (poster.isNotBlank()) poster else null,
                                    groupTitle = "Serien",
                                    type = ContentType.SERIES,
                                    seriesTitle = seriesTitle,
                                    seasonNumber = defaultSeason,
                                    episodeNumber = eNum,
                                    plot = if (desc.isNotBlank()) desc else null,
                                    durationSeconds = durationSec,
                                    isDach = isDach,
                                    countryCode = normCountry,
                                    originalCountry = countryCode,
                                    sourceId = playlistId,
                                    providerId = seriesId
                                )
                            )
                        }
                    } else {
                        // Check Case B: It is a Season requiring a sub-query for its episodes
                        var subEpisodesFound = false
                        val seasonId = itemObj.optString("season_id", itemId).trim()
                        val isSeasonName = rawName.contains("Season", ignoreCase = true) || rawName.contains("Staffel", ignoreCase = true)
                        if (seasonId.isNotBlank() && (isSeasonName || itemObj.optString("is_series") == "1" || itemObj.optString("is_season") == "1")) {
                            for (suf in suffixes) {
                                val seasonUrl = "$endpoint?type=series&action=get_ordered_list&movie_id=$seriesId&season_id=$seasonId&p=1&mac=$cleanMac&token=$token$suf"
                                try {
                                    val req = Request.Builder().url(seasonUrl)
                                        .addHeader("User-Agent", STB_USER_AGENT)
                                        .addHeader("Cookie", "mac=$cleanMac; stb_lang=en; timezone=Europe/Berlin; token=$token")
                                        .addHeader("Authorization", "Bearer $token")
                                        .build()
                                    val res = client.newCall(req).execute()
                                    val body = res.body?.string() ?: ""
                                    val subArr = extractChannelsArray(body)
                                    if (subArr != null && subArr.length() > 0) {
                                        for (subIdx in 0 until subArr.length()) {
                                            val subObj = subArr.optJSONObject(subIdx) ?: continue
                                            val subCmd = subObj.optString("cmd", rawCmd)
                                            var subEpNum = subObj.optInt("series_num", subObj.optInt("episode_num", subIdx + 1))
                                            val subName = subObj.optString("name", subObj.optString("title", "Episode $subEpNum")).trim()
                                            val streamUrl = extractPlayableStreamUrl(
                                                endpoint = endpoint,
                                                cleanMac = cleanMac,
                                                token = token,
                                                rawCmd = subCmd,
                                                seriesParam = subEpNum.toString(),
                                                typeParam = "vod"
                                            )
                                            episodes.add(
                                                ChannelItem(
                                                    id = UUID.randomUUID().toString(),
                                                    playlistId = playlistId,
                                                    name = subName,
                                                    streamUrl = streamUrl,
                                                    logoUrl = if (poster.isNotBlank()) poster else null,
                                                    groupTitle = "Serien",
                                                    type = ContentType.SERIES,
                                                    seriesTitle = seriesTitle,
                                                    seasonNumber = defaultSeason,
                                                    episodeNumber = subEpNum,
                                                    plot = if (desc.isNotBlank()) desc else null,
                                                    durationSeconds = durationSec,
                                                    isDach = isDach,
                                                    countryCode = normCountry,
                                                    originalCountry = countryCode,
                                                    sourceId = playlistId,
                                                    providerId = seriesId
                                                )
                                            )
                                        }
                                        subEpisodesFound = true
                                        break
                                    }
                                } catch (_: Exception) {}
                            }
                        }

                        // Case C: The item itself is a direct Episode
                        if (!subEpisodesFound) {
                            var sNum = itemObj.optInt("season_num", itemObj.optInt("season", 0))
                            var eNum = itemObj.optInt("series_num", itemObj.optInt("episode_num", itemObj.optInt("episode", 0)))

                            if (sNum <= 0 || eNum <= 0) {
                                val (_, parsedSeason, parsedEpisode) = ContentClassifier.extractSeriesMetadata(rawName, seriesTitle)
                                if (sNum <= 0) sNum = parsedSeason ?: defaultSeason
                                if (eNum <= 0) eNum = parsedEpisode ?: (i + 1)
                            }

                            val finalTitle = if (rawName.isNotBlank() && !rawName.equals(seriesTitle, ignoreCase = true)) {
                                rawName
                            } else {
                                "Episode $eNum"
                            }

                            val streamUrl = extractPlayableStreamUrl(
                                endpoint = endpoint,
                                cleanMac = cleanMac,
                                token = token,
                                rawCmd = rawCmd,
                                seriesParam = eNum.toString(),
                                typeParam = "vod"
                            )

                            episodes.add(
                                ChannelItem(
                                    id = UUID.randomUUID().toString(),
                                    playlistId = playlistId,
                                    name = finalTitle,
                                    streamUrl = streamUrl,
                                    logoUrl = if (poster.isNotBlank()) poster else null,
                                    groupTitle = "Serien",
                                    type = ContentType.SERIES,
                                    seriesTitle = seriesTitle,
                                    seasonNumber = sNum,
                                    episodeNumber = eNum,
                                    plot = if (desc.isNotBlank()) desc else null,
                                    durationSeconds = durationSec,
                                    isDach = isDach,
                                    countryCode = normCountry,
                                    originalCountry = countryCode,
                                    sourceId = playlistId,
                                    providerId = seriesId
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MacPortalClient", "Error fetching series episodes for series $seriesId: ${e.message}")
        }
        episodes
    }

    data class PortalResolutionResult(
        val streamUrl: String,
        val isOccupied: Boolean = false,
        val errorMessage: String? = null
    )

    private data class PortalLinkExecution(
        val url: String = "",
        val isOccupied: Boolean = false,
        val errorMessage: String? = null
    )

    suspend fun resolveStreamResult(
        streamUrl: String,
        macAddress: String = "",
        countryCode: String? = null,
        channelName: String? = null
    ): PortalResolutionResult = withContext(Dispatchers.IO) {
        try {
            var url = streamUrl.trim()

            // 0. Direct local file path or local URI (recordings/downloads)
            if (url.startsWith("/") || url.startsWith("file://", ignoreCase = true) || url.startsWith("content://", ignoreCase = true)) {
                return@withContext PortalResolutionResult(streamUrl = url)
            }

            val isInternalStalker = url.contains("action=create_link") ||
                    url.contains("load.php") ||
                    url.startsWith("mac_portal://") ||
                    url.contains("localhost") ||
                    url.contains("127.0.0.1") ||
                    url.contains("/ch/") ||
                    url.startsWith("auto", ignoreCase = true) ||
                    url.startsWith("ffmpeg", ignoreCase = true) ||
                    url.startsWith("ffrt", ignoreCase = true) ||
                    url.startsWith("custom", ignoreCase = true)

            // 1. Direct stream URL without stalker create_link needed
            if (!isInternalStalker && (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true) || url.startsWith("rtmp://", ignoreCase = true))) {
                val directClean = cleanStreamUrl(url)
                if (directClean.isNotBlank() && !directClean.contains("localhost") && !directClean.contains("127.0.0.1")) {
                    return@withContext PortalResolutionResult(streamUrl = directClean)
                }
            }

            // 2. If it is a create_link request URL or encoded Stalker stream
            val cleanMac = if (macAddress.isNotBlank()) {
                macAddress.trim().uppercase()
            } else {
                extractQueryParam(url, "mac").uppercase()
            }

            var rawCmd = extractQueryParam(url, "cmd")
            if (rawCmd.isBlank()) {
                rawCmd = url
            }

            val seriesParam = extractQueryParam(url, "series")
            val primaryType = extractQueryParam(url, "type").ifBlank {
                if (url.contains("type=vod", ignoreCase = true)) "vod" else "itv"
            }
            val secondaryType = if (primaryType == "vod") "itv" else "vod"

            val endpoint = when {
                url.startsWith("mac_portal://") -> url.substring("mac_portal://".length).substringBefore("?")
                url.contains("?") -> url.substringBefore("?")
                else -> url
            }

            if (endpoint.isNotBlank() && cleanMac.isNotBlank()) {
                val linkCacheKey = "$endpoint|$cleanMac|$rawCmd|$seriesParam|$primaryType"
                val cachedEntry = resolvedLinkCache[linkCacheKey]
                val now = System.currentTimeMillis()
                if (cachedEntry != null && (now - cachedEntry.first) < 600_000L && cachedEntry.second.streamUrl.isNotBlank()) {
                    return@withContext cachedEntry.second
                }

                // Try primary type with cached token
                var exec = executeCreateLink(endpoint, cleanMac, rawCmd, seriesParam, primaryType, forceRefreshToken = false, countryCode = countryCode, channelName = channelName)
                if (exec.isOccupied) {
                    return@withContext PortalResolutionResult(streamUrl = "", isOccupied = true, errorMessage = exec.errorMessage)
                }

                if (exec.url.isBlank()) {
                    exec = executeCreateLink(endpoint, cleanMac, rawCmd, seriesParam, primaryType, forceRefreshToken = true, countryCode = countryCode, channelName = channelName)
                    if (exec.isOccupied) {
                        return@withContext PortalResolutionResult(streamUrl = "", isOccupied = true, errorMessage = exec.errorMessage)
                    }
                }

                // Fallback to secondary type if needed
                if (exec.url.isBlank()) {
                    kotlinx.coroutines.delay(150)
                    exec = executeCreateLink(endpoint, cleanMac, rawCmd, seriesParam, secondaryType, forceRefreshToken = false, countryCode = countryCode, channelName = channelName)
                    if (exec.isOccupied) {
                        return@withContext PortalResolutionResult(streamUrl = "", isOccupied = true, errorMessage = exec.errorMessage)
                    }
                }

                if (exec.url.isNotBlank()) {
                    val res = PortalResolutionResult(streamUrl = exec.url)
                    if (resolvedLinkCache.size > 512) resolvedLinkCache.clear()
                    resolvedLinkCache[linkCacheKey] = now to res
                    return@withContext res
                } else {
                    val directFromRaw = cleanStreamUrl(rawCmd)
                    if ((directFromRaw.startsWith("http://", ignoreCase = true) || directFromRaw.startsWith("https://", ignoreCase = true)) &&
                        !directFromRaw.contains("localhost") && !directFromRaw.contains("127.0.0.1") && !directFromRaw.contains("/ch/")) {
                        return@withContext PortalResolutionResult(streamUrl = directFromRaw)
                    }

                    val errMsg = (exec.errorMessage ?: "").ifBlank {
                        VpnRequirementHelper.formatStatusErrorMessage(
                            statusCode = 456,
                            channelName = channelName,
                            countryCode = countryCode,
                            url = endpoint
                        )
                    }
                    return@withContext PortalResolutionResult(streamUrl = "", isOccupied = true, errorMessage = errMsg)
                }
            }

            val fallbackClean = cleanStreamUrl(url)
            if (fallbackClean.startsWith("http://", ignoreCase = true) || fallbackClean.startsWith("https://", ignoreCase = true)) {
                if (!fallbackClean.contains("action=create_link") && !fallbackClean.contains("localhost") && !fallbackClean.contains("127.0.0.1")) {
                    return@withContext PortalResolutionResult(streamUrl = fallbackClean)
                }
            }

            val defaultErr = VpnRequirementHelper.formatStatusErrorMessage(
                statusCode = 456,
                channelName = channelName,
                countryCode = countryCode,
                url = url
            )
            PortalResolutionResult(streamUrl = "", isOccupied = true, errorMessage = defaultErr)
        } catch (e: Exception) {
            Log.e("MacPortalClient", "Error resolving MAC stream URL", e)
            PortalResolutionResult(streamUrl = cleanStreamUrl(streamUrl).ifBlank { streamUrl })
        }
    }

    suspend fun resolveStreamUrl(streamUrl: String, macAddress: String = ""): String = withContext(Dispatchers.IO) {
        resolveStreamResult(streamUrl, macAddress).streamUrl
    }

    private fun executeCreateLink(
        endpoint: String,
        cleanMac: String,
        rawCmd: String,
        seriesParam: String = "",
        typeParam: String = "itv",
        forceRefreshToken: Boolean,
        countryCode: String? = null,
        channelName: String? = null
    ): PortalLinkExecution {
        try {
            val handshake = getOrRefreshHandshakeToken(endpoint, cleanMac, forceRefresh = forceRefreshToken, countryCode = countryCode, channelName = channelName)
            val token = handshake.token
            if (token.isBlank()) {
                return PortalLinkExecution(
                    url = "",
                    isOccupied = true,
                    errorMessage = handshake.errorMessage ?: VpnRequirementHelper.formatStatusErrorMessage(456, channelName, countryCode, endpoint)
                )
            }

            val cleanCmd = rawCmd.removePrefix("ffmpeg ").removePrefix("ffrt ").removePrefix("ffrt3 ").removePrefix("auto ").trim()
            val seriesQuery = if (seriesParam.isNotBlank()) "&series=$seriesParam" else ""
            val detectedMode = protocolModeCache[endpoint] ?: MacProtocolMode.JS_XML

            // Streamlined create_link URL queries: Prioritize detected protocol mode and stop quickly
            val candidateQueries = listOf(
                // Primary: Detected protocol mode with cleanCmd
                "$endpoint?type=$typeParam&action=create_link&cmd=${URLEncoder.encode(cleanCmd, "UTF-8")}$seriesQuery&mac=$cleanMac&token=$token${detectedMode.querySuffix}",
                // Fallback 1: Detected protocol mode with rawCmd
                "$endpoint?type=$typeParam&action=create_link&cmd=${URLEncoder.encode(rawCmd, "UTF-8")}$seriesQuery&mac=$cleanMac&token=$token${detectedMode.querySuffix}",
                // Fallback 2: Standard Stalker with JsHttpRequest
                "$endpoint?type=$typeParam&action=create_link&cmd=${URLEncoder.encode(cleanCmd, "UTF-8")}$seriesQuery&mac=$cleanMac&token=$token&JsHttpRequest=1-xml",
                // Fallback 3: Pure JSON (Ministra)
                "$endpoint?type=$typeParam&action=create_link&cmd=${URLEncoder.encode(cleanCmd, "UTF-8")}$seriesQuery&mac=$cleanMac&token=$token"
            ).distinct()

            var lastStatus = 200
            var lastErrorMsg: String? = null

            for (reqUrl in candidateQueries) {
                try {
                    val req = Request.Builder()
                        .url(reqUrl)
                        .addHeader("User-Agent", STB_USER_AGENT)
                        .addHeader("Cookie", "mac=$cleanMac; stb_lang=en; timezone=Europe/Berlin; token=$token")
                        .addHeader("Authorization", "Bearer $token")
                        .addHeader("X-User-Agent", "Model: MAG250; Link: WiFi")
                        .addHeader("Accept", "*/*")
                        .build()

                    val res = client.newCall(req).execute()
                    val statusCode = res.code
                    lastStatus = statusCode
                    val body = res.body?.string() ?: ""

                    // Check for HTTP errors
                    if (statusCode == 456 || statusCode == 409) {
                        return PortalLinkExecution(
                            url = "",
                            isOccupied = true,
                            errorMessage = VpnRequirementHelper.formatStatusErrorMessage(
                                statusCode = statusCode,
                                channelName = channelName,
                                countryCode = countryCode,
                                url = endpoint
                            )
                        )
                    }
                    if (statusCode == 429) {
                        return PortalLinkExecution(
                            url = "",
                            isOccupied = true,
                            errorMessage = VpnRequirementHelper.formatStatusErrorMessage(
                                statusCode = 429,
                                channelName = channelName,
                                countryCode = countryCode,
                                url = endpoint
                            )
                        )
                    }
                    if (statusCode == 401 || statusCode == 403) {
                        return PortalLinkExecution(
                            url = "",
                            isOccupied = true,
                            errorMessage = VpnRequirementHelper.formatStatusErrorMessage(
                                statusCode = statusCode,
                                channelName = channelName,
                                countryCode = countryCode,
                                url = endpoint
                            )
                        )
                    }
                    if (statusCode !in 200..299) {
                        lastErrorMsg = VpnRequirementHelper.formatStatusErrorMessage(statusCode, channelName, countryCode, endpoint)
                        continue
                    }

                    if (body.contains("{")) {
                        val json = JSONObject(body)
                        val js = json.opt("js")
                        var extractedCmd = ""
                        var errorMsg = ""

                        if (js is JSONObject) {
                            extractedCmd = js.optString("cmd", js.optString("url", js.optString("link", js.optString("stream_url", ""))))
                            errorMsg = js.optString("error", "")
                        } else if (js is String) {
                            extractedCmd = js
                        }

                        if (extractedCmd.isBlank()) {
                            extractedCmd = json.optString("cmd", json.optString("url", json.optString("link", json.optString("stream_url", ""))))
                        }
                        if (errorMsg.isBlank()) {
                            errorMsg = json.optString("error", json.optString("text", ""))
                        }

                        val lowerError = errorMsg.lowercase()
                        val lowerBody = body.lowercase()

                        val isOccupied = lowerError.contains("already in use") ||
                                lowerError.contains("already_in_use") ||
                                lowerError.contains("is in use") ||
                                lowerError.contains("device is already in use") ||
                                lowerError.contains("mac address is already in use") ||
                                lowerError.contains("limit reached") ||
                                lowerError.contains("limit_exceeded") ||
                                lowerError.contains("connection limit") ||
                                lowerError.contains("maximum active") ||
                                lowerError.contains("max connections") ||
                                lowerError.contains("occupied") ||
                                lowerBody.contains("already in use") ||
                                lowerBody.contains("mac address is already in use") ||
                                lowerBody.contains("connection limit")

                        if (isOccupied) {
                            val detail = if (lowerError.contains("limit") || lowerBody.contains("limit")) {
                                "Portal belegt: Das maximale Verbindungslimit für dieses Portal wurde erreicht."
                            } else {
                                "Portal belegt: Die MAC-Adresse ($cleanMac) wird derzeit bereits auf einem anderen Gerät genutzt."
                            }
                            return PortalLinkExecution(url = "", isOccupied = true, errorMessage = detail)
                        }

                        if (lowerError.contains("expired") || lowerBody.contains("account_expired")) {
                            return PortalLinkExecution(url = "", isOccupied = false, errorMessage = "Portal-Zugang ist abgelaufen.")
                        }

                        val clean = cleanStreamUrl(extractedCmd)
                        if (clean.isNotBlank() && !clean.contains("localhost") && !clean.contains("127.0.0.1")) {
                            var finalUrl = clean
                            if (finalUrl.contains("stream=&") || finalUrl.endsWith("stream=")) {
                                val origStreamMatch = Regex("[?&]stream=([^&]+)").find(rawCmd)
                                if (origStreamMatch != null) {
                                    val origStreamId = origStreamMatch.groupValues[1]
                                    finalUrl = finalUrl.replace("stream=&", "stream=$origStreamId&")
                                    if (finalUrl.endsWith("stream=")) {
                                        finalUrl += origStreamId
                                    }
                                }
                            }
                            return PortalLinkExecution(url = finalUrl, isOccupied = false)
                        }
                    }
                } catch (ignored: Exception) {}
            }

            if (lastErrorMsg != null) {
                return PortalLinkExecution(url = "", isOccupied = true, errorMessage = lastErrorMsg)
            }
        } catch (e: Exception) {
            Log.w("MacPortalClient", "create_link attempt failed: ${e.message}")
        }
        return PortalLinkExecution(url = "", isOccupied = false)
    }

    private fun extractPlayableStreamUrl(
        endpoint: String,
        cleanMac: String,
        token: String,
        rawCmd: String,
        seriesParam: String = "",
        typeParam: String = "itv"
    ): String {
        val direct = cleanStreamUrl(rawCmd)
        val isInternalMagCmd = rawCmd.contains("localhost", ignoreCase = true) ||
                rawCmd.contains("127.0.0.1") ||
                rawCmd.contains("/ch/") ||
                rawCmd.startsWith("auto", ignoreCase = true) ||
                rawCmd.startsWith("ffmpeg", ignoreCase = true) ||
                rawCmd.startsWith("ffrt", ignoreCase = true) ||
                rawCmd.startsWith("custom", ignoreCase = true) ||
                seriesParam.isNotBlank() ||
                typeParam == "vod" ||
                !direct.startsWith("http", ignoreCase = true)

        return if (!isInternalMagCmd && (direct.startsWith("http://", ignoreCase = true) || direct.startsWith("https://", ignoreCase = true))) {
            direct
        } else {
            val seriesQuery = if (seriesParam.isNotBlank()) "&series=$seriesParam" else ""
            "$endpoint?type=$typeParam&action=create_link&cmd=${URLEncoder.encode(rawCmd, "UTF-8")}$seriesQuery&mac=$cleanMac"
        }
    }

    private fun cleanStreamUrl(raw: String): String {
        var s = raw.trim()
        if (s.isBlank()) return ""

        val prefixes = listOf("ffmpeg ", "ffrt ", "ffrt3 ", "auto ")
        for (prefix in prefixes) {
            if (s.startsWith(prefix, ignoreCase = true)) {
                s = s.substring(prefix.length).trim()
            }
        }

        val httpIdx = when {
            s.contains("http://", ignoreCase = true) -> s.indexOf("http://", ignoreCase = true)
            s.contains("https://", ignoreCase = true) -> s.indexOf("https://", ignoreCase = true)
            s.contains("rtmp://", ignoreCase = true) -> s.indexOf("rtmp://", ignoreCase = true)
            else -> -1
        }

        if (httpIdx != -1) {
            s = s.substring(httpIdx).trim()
            val spaceIdx = s.indexOf(" ")
            if (spaceIdx != -1) {
                val candidate = s.substring(0, spaceIdx).trim()
                if (candidate.startsWith("http://", ignoreCase = true) ||
                    candidate.startsWith("https://", ignoreCase = true) ||
                    candidate.startsWith("rtmp://", ignoreCase = true)
                ) {
                    s = candidate
                }
            }
        }

        return s.removeSurrounding("\"", "\"").removeSurrounding("'", "'")
    }

    private suspend fun resolvePortalEndpoint(rawUrl: String, cleanMac: String): String {
        val cached = endpointCache[rawUrl]
        if (!cached.isNullOrBlank()) return cached

        val baseClean = PlaylistImportExtractor.cleanMacPortalUrl(rawUrl)
        val clean = if (baseClean.isNotBlank()) {
            baseClean
        } else {
            var s = rawUrl.trim()
            if (!s.startsWith("http://", ignoreCase = true) && !s.startsWith("https://", ignoreCase = true)) {
                s = "http://$s"
            }
            s.removeSuffix("/").removeSuffix("/c").removeSuffix("/c/")
        }

        if (rawUrl.trim().endsWith(".php", ignoreCase = true) && !rawUrl.contains("get.php", ignoreCase = true)) {
            val directPhp = rawUrl.trim()
            endpointCache[rawUrl] = directPhp
            return directPhp
        }

        val candidates = listOf(
            "$clean/c/portal.php",
            "$clean/portal.php",
            "$clean/server/load.php",
            "$clean/c/server/load.php",
            "$clean/stalker_portal/server/load.php",
            "$clean/stalker_portal/c/portal.php",
            "$clean/load.php",
            "$clean/c/load.php"
        )

        // Probe candidate endpoints in parallel within a 4.5s window instead of 60 sequential requests
        val matchedCandidate = withTimeoutOrNull(4_500L) {
            coroutineScope {
                val deferredProbes = candidates.map { candidate ->
                    async(Dispatchers.IO) {
                        for (withJsHttp in listOf(true, false)) {
                            try {
                                val queryParam = if (withJsHttp) "&JsHttpRequest=1-xml" else ""
                                val testUrl = "$candidate?type=stb&action=handshake&token=&mac=$cleanMac$queryParam"
                                val req = Request.Builder()
                                    .url(testUrl)
                                    .addHeader("User-Agent", STB_USER_AGENT)
                                    .addHeader("Cookie", "mac=$cleanMac; stb_lang=en; timezone=Europe/Berlin")
                                    .addHeader("X-User-Agent", "Model: MAG250; Link: WiFi")
                                    .addHeader("Accept", "*/*")
                                    .build()

                                discoveryClient.newCall(req).execute().use { res ->
                                    val body = res.body?.string() ?: ""
                                    if (res.isSuccessful && (body.contains("\"token\"") || body.contains("\"js\"") || body.contains("\"random\"") || body.contains("real_ip"))) {
                                        protocolModeCache[candidate] = if (withJsHttp) MacProtocolMode.JS_XML else MacProtocolMode.PLAIN
                                        // Extract & cache token immediately so getOrRefreshHandshakeToken doesn't repeat the network call
                                        val tokenMatch = Regex("\"token\"\\s*:\\s*\"([^\"]+)\"").find(body)
                                        val extractedToken = tokenMatch?.groupValues?.getOrNull(1) ?: ""
                                        if (extractedToken.isNotBlank()) {
                                            tokenCache["$candidate|$cleanMac"] = extractedToken
                                        }
                                        return@async candidate
                                    }
                                }
                            } catch (ignored: Exception) {}
                        }
                        null
                    }
                }
                deferredProbes.awaitAll().firstOrNull { it != null }
            }
        }

        if (!matchedCandidate.isNullOrBlank()) {
            endpointCache[rawUrl] = matchedCandidate
            return matchedCandidate
        }

        val fallback = when {
            rawUrl.contains("/c", ignoreCase = true) || rawUrl.contains("dragoniptv", ignoreCase = true) || rawUrl.contains("weaseltv", ignoreCase = true) ->
                "$clean/c/portal.php"
            else ->
                "$clean/server/load.php"
        }
        endpointCache[rawUrl] = fallback
        return fallback
    }

    private fun getOrRefreshHandshakeToken(
        endpoint: String,
        cleanMac: String,
        forceRefresh: Boolean = false,
        countryCode: String? = null,
        channelName: String? = null
    ): HandshakeOutcome {
        val cacheKey = "$endpoint|$cleanMac"
        if (!forceRefresh) {
            val cached = tokenCache[cacheKey]
            if (!cached.isNullOrBlank()) {
                return HandshakeOutcome(token = cached)
            }
        }

        val baseSep = if (endpoint.contains("?")) "&" else "?"
        val macVariants = listOf(cleanMac).distinct()
        val protocolModes = listOf(
            protocolModeCache[endpoint] ?: MacProtocolMode.JS_XML,
            MacProtocolMode.PLAIN
        ).distinct()

        var lastStatusCode = 0
        var lastBody = ""

        for (macCandidate in macVariants) {
            for (mode in protocolModes) {
                val handshakeUrl = "$endpoint${baseSep}type=stb&action=handshake&token=&mac=$macCandidate${mode.querySuffix}"
                val handshakeReq = Request.Builder()
                    .url(handshakeUrl)
                    .addHeader("User-Agent", STB_USER_AGENT)
                    .addHeader("Cookie", "mac=$macCandidate; stb_lang=en; timezone=Europe/Berlin")
                    .addHeader("X-User-Agent", "Model: MAG250; Link: WiFi")
                    .addHeader("Accept", "*/*")
                    .build()

                try {
                    val hsResponse = client.newCall(handshakeReq).execute()
                    val statusCode = hsResponse.code
                    lastStatusCode = statusCode
                    val hsBody = hsResponse.body?.string() ?: ""
                    lastBody = hsBody

                    var token = ""
                    try {
                        val json = JSONObject(hsBody)
                        val js = json.opt("js")
                        if (js is JSONObject) {
                            token = js.optString("token", "")
                        } else if (js is String) {
                            token = js
                        } else {
                            token = json.optString("token", "")
                        }
                    } catch (e: Exception) {
                        val tokenMatch = Regex("\"token\"\\s*:\\s*\"([^\"]+)\"").find(hsBody)
                        if (tokenMatch != null) {
                            token = tokenMatch.groupValues[1]
                        }
                    }

                    if (token.isNotBlank()) {
                        tokenCache[cacheKey] = token
                        protocolModeCache[endpoint] = mode

                        // Activate profile / session on the backend (MAG250/254/270/XUI/Ministra universal compatibility)
                        try {
                            val cleanHex = macCandidate.replace(":", "").uppercase()
                            val sn = "002019$cleanHex".take(13)
                            val devId = (cleanHex + cleanHex + cleanHex + cleanHex).take(32)
                            val profileUrl = "$endpoint${baseSep}type=stb&action=get_profile&hd=1&ver=ImageDescription:%200.2.18-r14-pub-250&num_banks=2&sn=$sn&stb_type=MAG250&image_version=218&video_out=hdmi&device_id=$devId&device_id2=$devId&auth_second_step=1&hw_version=1.7-BD-00&not_valid_token=0&mac=$macCandidate&token=$token${mode.querySuffix}"
                            val profileReq = Request.Builder()
                                .url(profileUrl)
                                .addHeader("User-Agent", STB_USER_AGENT)
                                .addHeader("Cookie", "mac=$macCandidate; stb_lang=en; timezone=Europe/Berlin; token=$token")
                                .addHeader("Authorization", "Bearer $token")
                                .addHeader("X-User-Agent", "Model: MAG250; Link: WiFi")
                                .build()
                            client.newCall(profileReq).execute().close()
                        } catch (ignored: Exception) {}

                        return HandshakeOutcome(token = token)
                    }
                } catch (ignored: Exception) {}
            }
        }

        val lowerBody = lastBody.lowercase()
        val errorMsg = when {
            lastStatusCode == 456 || lowerBody.contains("456") ->
                VpnRequirementHelper.formatStatusErrorMessage(456, channelName, countryCode, endpoint)
            lastStatusCode in listOf(401, 403) || lowerBody.contains("cloudflare") || lowerBody.contains("access denied") || lowerBody.contains("forbidden") ->
                VpnRequirementHelper.formatStatusErrorMessage(lastStatusCode.takeIf { it > 0 } ?: 403, channelName, countryCode, endpoint)
            lastStatusCode == 429 ->
                VpnRequirementHelper.formatStatusErrorMessage(429, channelName, countryCode, endpoint)
            lowerBody.contains("already in use") || lowerBody.contains("occupied") ->
                "Portal belegt: Die MAC-Adresse ($cleanMac) wird bereits auf einem anderen Gerät genutzt."
            lowerBody.contains("expired") ->
                "Portal-Abonnement ist abgelaufen."
            lastStatusCode !in 200..299 && lastStatusCode > 0 ->
                VpnRequirementHelper.formatStatusErrorMessage(lastStatusCode, channelName, countryCode, endpoint)
            else ->
                VpnRequirementHelper.formatStatusErrorMessage(456, channelName, countryCode, endpoint)
        }
        return HandshakeOutcome(token = "", errorMessage = errorMsg)
    }

    private fun fetchGenreMap(endpoint: String, cleanMac: String, token: String): Map<String, String> {
        val genreMap = mutableMapOf<String, String>()
        val mode = protocolModeCache[endpoint] ?: MacProtocolMode.JS_XML
        val suffixes = listOf(mode.querySuffix, "").distinct().take(2)
        val actions = listOf(
            "type=itv&action=get_genres",
            "type=itv&action=get_categories"
        )

        for (actionQuery in actions) {
            for (suffix in suffixes) {
                try {
                    val url = "$endpoint?$actionQuery&mac=$cleanMac&token=$token$suffix"
                    val req = Request.Builder()
                        .url(url)
                        .addHeader("User-Agent", STB_USER_AGENT)
                        .addHeader("Cookie", "mac=$cleanMac; stb_lang=en; timezone=Europe/Berlin; token=$token")
                        .addHeader("Authorization", "Bearer $token")
                        .addHeader("X-User-Agent", "Model: MAG250; Link: WiFi")
                        .build()
                    val res = client.newCall(req).execute()
                    val body = res.body?.string() ?: continue

                    val jsArray = extractChannelsArray(body)
                    if (jsArray != null && jsArray.length() > 0) {
                        for (i in 0 until jsArray.length()) {
                            val gObj = jsArray.optJSONObject(i) ?: continue
                            val gId = gObj.optString("id", gObj.optString("genre_id", gObj.optString("cat_id", ""))).trim()
                            val gTitle = gObj.optString("title", gObj.optString("name", gObj.optString("category_name", ""))).trim()
                            if (gId.isNotBlank() && gTitle.isNotBlank() && gId != "*") {
                                genreMap[gId] = gTitle
                            }
                        }
                    }

                    // Also check for associative map in root/js
                    if (genreMap.isEmpty() && body.contains("{")) {
                        val root = JSONObject(body.trim())
                        val jsObj = root.optJSONObject("js") ?: root
                        val keys = jsObj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            if (k == "total_items" || k == "max_page_items" || k == "selected_item") continue
                            val valObj = jsObj.optJSONObject(k)
                            if (valObj != null) {
                                val gId = valObj.optString("id", k).trim()
                                val gTitle = valObj.optString("title", valObj.optString("name", "")).trim()
                                if (gId.isNotBlank() && gTitle.isNotBlank() && gId != "*") {
                                    genreMap[gId] = gTitle
                                }
                            } else {
                                val strTitle = jsObj.optString(k, "").trim()
                                if (k.isNotBlank() && strTitle.isNotBlank() && k != "*") {
                                    genreMap[k] = strTitle
                                }
                            }
                        }
                    }

                    if (genreMap.isNotEmpty()) {
                        return genreMap
                    }
                } catch (ignored: Exception) {}
            }
        }
        return genreMap
    }

    private fun fetchChannelDataArray(
        endpoint: String,
        cleanMac: String,
        token: String,
        action: String,
        extraParams: String = ""
    ): JSONArray? {
        val mode = protocolModeCache[endpoint] ?: MacProtocolMode.JS_XML
        val url = "$endpoint?type=itv&action=$action$extraParams&mac=$cleanMac&token=$token${mode.querySuffix}"
        val req = Request.Builder()
            .url(url)
            .addHeader("User-Agent", STB_USER_AGENT)
            .addHeader("Cookie", "mac=$cleanMac; stb_lang=en; timezone=Europe/Berlin; token=$token")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("X-User-Agent", "Model: MAG250; Link: WiFi")
            .build()

        val res = client.newCall(req).execute()
        val body = res.body?.string() ?: return null
        return extractChannelsArray(body)
    }

    private fun extractChannelsArray(body: String): JSONArray? {
        if (body.isBlank()) return null
        try {
            val trimmed = body.trim()
            if (trimmed.startsWith("[")) {
                return JSONArray(trimmed)
            }
            val root = JSONObject(trimmed)

            val jsArray = root.optJSONArray("js")
            if (jsArray != null && jsArray.length() > 0) return jsArray

            val jsObj = root.optJSONObject("js")
            if (jsObj != null) {
                val data = jsObj.optJSONArray("data") ?: jsObj.optJSONArray("channels") ?: jsObj.optJSONArray("items")
                if (data != null && data.length() > 0) return data
                val converted = jsonObjectValuesToJSONArray(jsObj)
                if (converted != null && converted.length() > 0) return converted
            }

            val dataArray = root.optJSONArray("data")
                ?: root.optJSONArray("channels")
                ?: root.optJSONArray("items")
                ?: root.optJSONArray("results")
            if (dataArray != null && dataArray.length() > 0) return dataArray

            val rootDataObj = root.optJSONObject("data")
            if (rootDataObj != null) {
                val inner = rootDataObj.optJSONArray("data") ?: rootDataObj.optJSONArray("channels")
                if (inner != null && inner.length() > 0) return inner
                val converted = jsonObjectValuesToJSONArray(rootDataObj)
                if (converted != null && converted.length() > 0) return converted
            }

            val rootConverted = jsonObjectValuesToJSONArray(root)
            if (rootConverted != null && rootConverted.length() > 0) return rootConverted
        } catch (e: Exception) {
            Log.e("MacPortalClient", "Error parsing channel JSON", e)
        }
        return null
    }

    private fun jsonObjectValuesToJSONArray(obj: JSONObject): JSONArray? {
        val result = JSONArray()
        val keys = obj.keys()
        var foundValidChild = false
        while (keys.hasNext()) {
            val key = keys.next()
            if (key == "total_items" || key == "max_page_items" || key == "selected_item") continue
            val child = obj.optJSONObject(key)
            if (child != null) {
                val hasNameOrId = child.has("name") || child.has("caption") || child.has("title") ||
                        child.has("cmd") || child.has("id") || child.has("ch_id")
                if (hasNameOrId) {
                    result.put(child)
                    foundValidChild = true
                }
            }
        }
        return if (foundValidChild && result.length() > 0) result else null
    }

    private fun extractBaseDomain(endpoint: String): String {
        return try {
            val uri = Uri.parse(endpoint)
            "${uri.scheme}://${uri.authority}"
        } catch (e: Exception) {
            ""
        }
    }

    private fun extractQueryParam(url: String, param: String): String {
        return try {
            Uri.parse(url).getQueryParameter(param) ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun resolveGroupTitle(
        name: String,
        genreFromMap: String?,
        genreFromName: String?,
        countryCode: String,
        isDach: Boolean
    ): String {
        if (!genreFromMap.isNullOrBlank()) {
            return genreFromMap
        }
        if (!genreFromName.isNullOrBlank() && genreFromName != "All" && genreFromName != "Alle") {
            return genreFromName
        }

        val nameUpper = name.uppercase()
        return when {
            nameUpper.contains("SPORT") || nameUpper.contains("BUNDESLIGA") || nameUpper.contains("DAZN") -> "SPORT HD"
            countryCode == "DE" -> "DEUTSCHLAND"
            countryCode == "AT" -> "ÖSTERREICH"
            countryCode == "CH" -> "SCHWEIZ"
            isDach -> "DEUTSCHLAND"
            else -> "INTERNATIONAL"
        }
    }
}