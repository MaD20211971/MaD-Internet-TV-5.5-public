package com.example.data.livetvlight

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

data class CatalogSyncResult(
    val success: Boolean,
    val discoveredSlugsCount: Int,
    val updatedChannelsCount: Int,
    val totalChannelsCount: Int = LiveTvLightCatalog.allChannels.size,
    val addedNewChannelsCount: Int = 0,
    val logosUpdatedCount: Int = 0,
    val syncedAtMs: Long = System.currentTimeMillis()
)

data class ExtractedStreamCandidate(
    val url: String,
    val referer: String,
    val origin: String,
    val cookie: String = ""
)

/**
 * Hochleistungs-Stream-Resolver für Live TV light (direkte 2ix2.com-Integration):
 * - Liest alle DE- & AT-Senderseiten (inkl. echter pageUrl & Originallogo) direkt von 2ix2.com ein
 * - Löst Streams direkt über die echte 2ix2.com-Senderseite auf (HTTP + 2-stufige Iframes + Headless-Interceptor)
 * - Echte #EXTM3U-2-KB-Inhaltsprüfung (inkl. Range-freiem Retry bei 400/405/416)
 * - 0 ms Cache-Hit-Fast-Path für bereits verifizierte Streams (20 Min. TTL)
 * - Dynamische Header-Weitergabe (Referer, Origin, Cookie) pro URL & Stream-Host bis in den PlayerManager
 */
object LiveTvLightStreamResolver {

    private const val CACHE_TTL_MS = 20 * 60 * 1000L // 20 Minuten
    private const val MAX_PEEK_BYTES = 2048
    private const val HEADLESS_TIMEOUT_MS = 5500L
    private const val RACE_TIMEOUT_MS = 5000L
    const val BROWSER_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    private const val DIRECTORY_BASE_URL = LiveTvLight2ix2Scraper.BASE_URL

    private val resolvedCache = ConcurrentHashMap<String, Pair<String, Long>>()
    private val resolvedHeadersByUrl = ConcurrentHashMap<String, Map<String, String>>()
    private val resolvedHeadersByHost = ConcurrentHashMap<String, Map<String, String>>()
    private val resolvedRelayHosts = ConcurrentHashMap.newKeySet<String>()
    private val inFlightRequests = ConcurrentHashMap<String, CompletableDeferred<String>>()
    private val resolverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initialDirectorySyncTriggered = AtomicBoolean(false)

    private val blockedAssetExtensions = setOf(
        ".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg", ".ico", ".bmp",
        ".css", ".woff", ".woff2", ".ttf", ".otf", ".eot", ".map"
    )
    private val blockedAdHosts = listOf(
        "doubleclick.net", "googlesyndication.com", "google-analytics.com",
        "googletagmanager.com", "facebook.net", "facebook.com", "adnxs.com",
        "popads.net", "exoclick.com", "histats.com", "mc.yandex.ru",
        "taboola.com", "outbrain.com", "amazon-adsystem.com", "scorecardresearch.com"
    )

    private val httpClient: OkHttpClient by lazy {
        val dispatcher = Dispatcher().apply {
            maxRequests = 40
            maxRequestsPerHost = 14
        }
        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(ConnectionPool(20, 5, TimeUnit.MINUTES))
            .connectTimeout(3000, TimeUnit.MILLISECONDS)
            .readTimeout(4000, TimeUnit.MILLISECONDS)
            .callTimeout(5500, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun getCachedStreamUrl(channelId: String): String? {
        val entry = resolvedCache[channelId] ?: return null
        if (System.currentTimeMillis() - entry.second < CACHE_TTL_MS) {
            return entry.first
        }
        resolvedCache.remove(channelId)
        return null
    }

    fun getInstantStartUrl(channel: LiveTvLightChannel): String? {
        return getCachedStreamUrl(channel.id)
    }

    fun isAlreadyPlayableWithoutResolve(channel: LiveTvLightChannel, candidateUrl: String): Boolean {
        if (candidateUrl.isBlank()) return false
        val cached = getCachedStreamUrl(channel.id)
        return cached != null && cached == candidateUrl
    }

    /**
     * Liefert die exakt benötigten HTTP-Header (Referer, Origin, Cookie) für jede dynamisch
     * über 2ix2 aufgelöste Stream-URL sowie deren Varianten- und .ts-Segment-Requests.
     */
    fun getHeadersForUrl(url: String): Map<String, String> {
        resolvedHeadersByUrl[url]?.let { return it }
        val host = extractHostLower(url)
        if (!host.isNullOrBlank()) {
            resolvedHeadersByHost[host]?.let { return it }
        }
        return emptyMap()
    }

    fun isResolvedRelayUrl(url: String): Boolean {
        if (url.contains("hopslan.com", ignoreCase = true)) return true
        val host = extractHostLower(url) ?: return false
        return host in resolvedRelayHosts
    }

    internal fun registerDynamicHeaders(
        streamUrl: String,
        referer: String = "$DIRECTORY_BASE_URL/",
        origin: String = DIRECTORY_BASE_URL,
        cookie: String = "",
        isRelay: Boolean = true
    ) {
        val headers = buildMap {
            if (referer.isNotBlank()) put("Referer", referer)
            if (origin.isNotBlank()) put("Origin", origin)
            if (cookie.isNotBlank()) put("Cookie", cookie)
            put("User-Agent", BROWSER_UA)
            put("Sec-Fetch-Dest", "empty")
            put("Sec-Fetch-Mode", "cors")
            put("Sec-Fetch-Site", "cross-site")
        }
        resolvedHeadersByUrl[streamUrl] = headers
        val host = extractHostLower(streamUrl)
        if (!host.isNullOrBlank()) {
            resolvedHeadersByHost[host] = headers
            if (isRelay) {
                resolvedRelayHosts.add(host)
            }
        }
    }

    fun invalidateCache(channelId: String) {
        resolvedCache.remove(channelId)
    }

    fun clearCache() {
        resolvedCache.clear()
    }

    /**
     * Initialisiert sofort (0 ms) den persistenten JSON-Cache und startet einmalig pro App-Sitzung
     * still im Hintergrund den vollständigen Multi-Page-Import aller DE- & AT-Sender von 2ix2.com.
     */
    fun ensureBackgroundCatalogSyncOnce(context: Context? = null) {
        if (context != null) {
            LiveTvLightCatalog.initializeFromCache(context)
        }
        if (initialDirectorySyncTriggered.compareAndSet(false, true)) {
            val appCtx = context?.applicationContext
            resolverScope.launch {
                try {
                    syncCatalogFromDirectory(appCtx)
                } catch (_: Exception) {
                }
            }
        }
    }

    /**
     * Löst einen garantiert abspielbaren, #EXTM3U-verifizierten HLS-Stream direkt von 2ix2.com auf:
     * 1. 0 ms Cache-Hit bei bereits verifiziertem Stream (sofern !forceRefresh)
     * 2. In-Flight-Deduplizierung bei mehrfachem schnellem Antippen
     * 3. Phase A: Direkte 2ix2.com-Extraktion (echte pageUrl -> HTML + 2-stufige Iframes) parallel zu bekannten Relays
     * 4. Phase B: Asset-/Ad-blockender Headless-WebView-Interceptor direkt auf der echten 2ix2-pageUrl (5,5 s + Cookie)
     * 5. Phase C: Nachrangige direkte CDN-Fallbacks (nur wenn #EXTM3U-verifiziert)
     */
    suspend fun resolvePlayableStream(
        context: Context,
        channel: LiveTvLightChannel,
        forceRefresh: Boolean = false
    ): String {
        val currentChannel = LiveTvLightCatalog.findById(channel.id) ?: channel
        if (!forceRefresh) {
            getInstantStartUrl(currentChannel)?.let { return it }
        } else {
            invalidateCache(currentChannel.id)
        }

        val existing = inFlightRequests[currentChannel.id]
        if (existing != null && existing.isActive) {
            return try {
                existing.await()
            } catch (_: Exception) {
                currentChannel.primaryStreamUrl
            }
        }

        val deferred = CompletableDeferred<String>()
        val previous = inFlightRequests.putIfAbsent(currentChannel.id, deferred)
        if (previous != null && previous.isActive) {
            return try {
                previous.await()
            } catch (_: Exception) {
                currentChannel.primaryStreamUrl
            }
        }

        try {
            val resolved = resolveInternal(context, currentChannel, excludedUrls = emptySet())
            deferred.complete(resolved)
            return resolved
        } catch (ce: CancellationException) {
            deferred.cancel(ce)
            throw ce
        } catch (_: Exception) {
            val fallback = currentChannel.primaryStreamUrl
            deferred.complete(fallback)
            return fallback
        } finally {
            inFlightRequests.remove(currentChannel.id, deferred)
        }
    }

    private suspend fun resolveInternal(
        context: Context,
        channel: LiveTvLightChannel,
        excludedUrls: Set<String>
    ): String = withContext(Dispatchers.IO) {
        val pageUrls = LiveTvLightCatalog.effectivePageUrls(channel)
        val allUrls = buildList {
            if (channel.primaryStreamUrl.isNotBlank()) add(channel.primaryStreamUrl)
            channel.fallbackStreamUrls.forEach { if (it.isNotBlank()) add(it) }
        }.distinct().filterNot { it in excludedUrls }

        val (geoFreeRelayUrls, directCdnUrls) = allUrls.partition { isGeoFreeRelayUrl(it) }

        // Phase A: Direkte 2ix2.com-HTTP-/Iframe-Extraktion auf echter pageUrl (parallel mit bekannten Relays)
        val phaseAWinner = raceFastestReachableStream(
            candidateUrls = geoFreeRelayUrls,
            resolverPageUrl = pageUrls.firstOrNull(),
            probeFn = { url -> verifyValidM3u8Content(url) },
            extractFn = {
                extractVerifiedFromPageUrls(pageUrls, excludedUrls)
            }
        )

        if (!phaseAWinner.isNullOrBlank() && phaseAWinner !in excludedUrls) {
            val optimized = upgradeToDirectMuxedVariant(phaseAWinner)
            resolvedCache[channel.id] = optimized to System.currentTimeMillis()
            return@withContext optimized
        }

        // Phase B: Asset-/Ad-blockender Headless-WebView-Interceptor direkt auf der echten 2ix2.com-Senderseite
        for (pageUrl in pageUrls.take(2)) {
            val captured = extractM3u8ViaHeadlessInterceptor(context, pageUrl)
            if (captured != null && captured.url !in excludedUrls) {
                registerDynamicHeaders(
                    streamUrl = captured.url,
                    referer = captured.referer,
                    origin = captured.origin,
                    cookie = captured.cookie,
                    isRelay = true
                )
                val optimized = upgradeToDirectMuxedVariant(captured.url)
                if (optimized != captured.url) {
                    registerDynamicHeaders(
                        streamUrl = optimized,
                        referer = captured.referer,
                        origin = captured.origin,
                        cookie = captured.cookie,
                        isRelay = true
                    )
                }
                if (verifyValidM3u8Content(optimized)) {
                    resolvedCache[channel.id] = optimized to System.currentTimeMillis()
                    return@withContext optimized
                }
                if (optimized != captured.url && verifyValidM3u8Content(captured.url)) {
                    resolvedCache[channel.id] = captured.url to System.currentTimeMillis()
                    return@withContext captured.url
                }
            }
        }

        // Phase C: Nachrangige direkte CDN-Streams nur nach echter #EXTM3U-Verifikation
        if (directCdnUrls.isNotEmpty()) {
            val cdnWinner = raceFastestReachableStream(
                candidateUrls = directCdnUrls,
                resolverPageUrl = null,
                probeFn = { url -> verifyValidM3u8Content(url) },
                extractFn = { null }
            )
            if (!cdnWinner.isNullOrBlank() && cdnWinner !in excludedUrls) {
                resolvedCache[channel.id] = cdnWinner to System.currentTimeMillis()
                return@withContext cdnWinner
            }
        }

        allUrls.firstOrNull() ?: channel.primaryStreamUrl
    }

    /**
     * Wird aufgerufen, wenn ein Live-TV-Light-Stream im Player einen Fehler oder Buffering-Hänger hat.
     * Schließt alle bereits fehlgeschlagenen URLs aus und sucht sofort einen verifizierten Ersatz.
     */
    suspend fun resolveFallbackAfterError(
        context: Context,
        channelId: String,
        failedUrls: Set<String>,
        probeFn: ((String) -> Boolean)? = null,
        extractFn: ((String) -> String?)? = null
    ): String? = withContext(Dispatchers.IO) {
        val cleanId = channelId.removePrefix("ltv_light_")
        val channel = LiveTvLightCatalog.findById(cleanId) ?: return@withContext null
        invalidateCache(channel.id)

        val isCustomProbe = probeFn != null || extractFn != null
        if (!isCustomProbe) {
            val resolved = resolveInternal(context, channel, excludedUrls = failedUrls)
            return@withContext resolved.takeIf { it.isNotBlank() && it !in failedUrls }
        }

        val remainingCandidates = buildList {
            if (channel.primaryStreamUrl.isNotBlank()) add(channel.primaryStreamUrl)
            channel.fallbackStreamUrls.forEach { if (it.isNotBlank()) add(it) }
        }.distinct().filterNot { it in failedUrls }

        val pageUrls = LiveTvLightCatalog.effectivePageUrls(channel)
        val resolverPageUrl = pageUrls.firstOrNull()

        val effectiveProbe: (String) -> Boolean = probeFn ?: { verifyValidM3u8Content(it) }
        val effectiveExtract: (String) -> String? = extractFn ?: { null }

        val winner = raceFastestReachableStream(
            candidateUrls = remainingCandidates,
            resolverPageUrl = resolverPageUrl,
            probeFn = effectiveProbe,
            extractFn = { page ->
                effectiveExtract(page)?.takeIf { it.isNotBlank() && it !in failedUrls }
            }
        )

        if (!winner.isNullOrBlank() && winner !in failedUrls) {
            resolvedCache[channel.id] = winner to System.currentTimeMillis()
            return@withContext winner
        }

        remainingCandidates.firstOrNull()
    }

    fun resolveFallbackAfterErrorAsync(
        context: Context,
        channelId: String,
        failedUrls: Set<String>,
        onResolved: (String?) -> Unit
    ) {
        resolverScope.launch {
            val result = try {
                resolveFallbackAfterError(context, channelId, failedUrls)
            } catch (_: Exception) {
                null
            }
            onResolved(result)
        }
    }

    /**
     * Startet URL-Probes und die Live-2ix2-HTTP-Extraktion parallel.
     * Sobald der erste #EXTM3U-verifizierte Stream antwortet, wird dieser SOFORT zurückgegeben
     * und alle übrigen Tasks werden abgebrochen.
     */
    internal suspend fun raceFastestReachableStream(
        candidateUrls: List<String>,
        resolverPageUrl: String?,
        probeFn: (String) -> Boolean = { verifyValidM3u8Content(it) },
        extractFn: (String) -> String? = { extractAllM3u8CandidatesViaHttp(it).firstOrNull()?.url }
    ): String? {
        val totalTasks = candidateUrls.size + if (resolverPageUrl != null) 1 else 0
        if (totalTasks == 0) return null

        val resultChannel = Channel<String?>(Channel.UNLIMITED)
        val remaining = AtomicInteger(totalTasks)

        val jobs = buildList {
            if (resolverPageUrl != null) {
                add(
                    resolverScope.launch {
                        val extracted = try {
                            extractFn(resolverPageUrl)
                        } catch (_: Exception) {
                            null
                        }
                        if (!extracted.isNullOrBlank()) {
                            resultChannel.trySend(extracted)
                        } else if (remaining.decrementAndGet() == 0) {
                            resultChannel.trySend(null)
                        }
                    }
                )
            }
            candidateUrls.forEach { url ->
                add(
                    resolverScope.launch {
                        val hit = try {
                            if (probeFn(url)) url else null
                        } catch (_: Exception) {
                            null
                        }
                        if (hit != null) {
                            resultChannel.trySend(hit)
                        } else if (remaining.decrementAndGet() == 0) {
                            resultChannel.trySend(null)
                        }
                    }
                )
            }
        }

        return try {
            withTimeoutOrNull(RACE_TIMEOUT_MS) {
                resultChannel.receive()
            }
        } finally {
            jobs.forEach { it.cancel() }
            resultChannel.close()
        }
    }

    private fun extractVerifiedFromPageUrls(pageUrls: List<String>, excludedUrls: Set<String>): String? {
        for (pageUrl in pageUrls.take(3)) {
            val candidates = extractAllM3u8CandidatesViaHttp(pageUrl)
            for (candidate in candidates) {
                if (candidate.url in excludedUrls) continue
                registerDynamicHeaders(
                    streamUrl = candidate.url,
                    referer = candidate.referer,
                    origin = candidate.origin,
                    cookie = candidate.cookie,
                    isRelay = true
                )
                if (verifyValidM3u8Content(candidate.url)) {
                    return candidate.url
                }
            }
        }
        return null
    }

    /**
     * Lädt alle Seiten beider 2ix2.com-Kategorien (DE + AT) vollständig ein, aktualisiert
     * exakte pageUrls & originale 2ix2.com-Senderlogos und speichert den Stand lokal im Cache.
     */
    suspend fun syncCatalogFromDirectory(context: Context? = null): CatalogSyncResult = withContext(Dispatchers.IO) {
        val scraped = LiveTvLight2ix2Scraper.scrapeAllCategories(httpClient)
        if (scraped.isEmpty()) {
            return@withContext CatalogSyncResult(
                success = false,
                discoveredSlugsCount = 0,
                updatedChannelsCount = 0,
                totalChannelsCount = LiveTvLightCatalog.allChannels.size
            )
        }

        val mergeResult = LiveTvLightCatalog.applyScrapedCatalog(scraped, context)
        if (mergeResult.matchedWithLive2ix2Count > 0 || mergeResult.addedNewChannelsCount > 0) {
            clearCache()
        }

        CatalogSyncResult(
            success = true,
            discoveredSlugsCount = scraped.size,
            updatedChannelsCount = mergeResult.matchedWithLive2ix2Count + mergeResult.addedNewChannelsCount,
            totalChannelsCount = mergeResult.totalChannelsCount,
            addedNewChannelsCount = mergeResult.addedNewChannelsCount,
            logosUpdatedCount = mergeResult.logosUpdatedCount
        )
    }

    internal fun parseDirectorySlugs(html: String): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        val ignoredPrefixes = setOf(
            "category", "tag", "page", "wp-content", "wp-includes", "wp-json",
            "feed", "impressum", "datenschutz", "kontakt", "tv-programm", "author"
        )
        val linkRegex =
            """<a[^>]+href=["']https?://(?:www\.)?2ix2\.com/([a-z0-9\-]+)/?["'][^>]*>(.*?)</a>"""
                .toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

        for (match in linkRegex.findAll(html)) {
            val slug = match.groupValues[1].trim().lowercase()
            if (slug.length < 2 || slug in ignoredPrefixes) continue
            val rawAnchorText = match.groupValues[2]
                .replace(Regex("<[^>]+>"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()

            map.putIfAbsent(LiveTvLightCatalog.normalizeForMatch(slug), slug)
            if (rawAnchorText.length in 2..48) {
                val normalizedText = LiveTvLightCatalog.normalizeForMatch(rawAnchorText)
                if (normalizedText.length >= 2) {
                    map.putIfAbsent(normalizedText, slug)
                }
            }
        }
        return map
    }

    private fun upgradeToDirectMuxedVariant(url: String): String {
        if (url.contains("hopslan.com", ignoreCase = true) && url.endsWith("/index.m3u8", ignoreCase = true)) {
            val muxedCandidate = url.substringBeforeLast("/index.m3u8") + "/tracks-v1a1/mono.ts.m3u8"
            if (verifyValidM3u8Content(muxedCandidate)) {
                return muxedCandidate
            }
        }
        return url
    }

    /**
     * Echte #EXTM3U-Inhaltsprüfung (2 KB Peek):
     * Liest maximal 2048 Bytes per GET + Range-Header (mit automatischem Range-freiem Retry bei 400/405/416)
     * und akzeptiert die URL NUR, wenn tatsächlich ein gültiges HLS-Manifest (#EXTM3U) geliefert wird.
     */
    internal fun verifyValidM3u8Content(url: String): Boolean {
        val firstAttempt = executeM3u8Peek(url, useRangeHeader = true)
        if (firstAttempt == PeekOutcome.VALID) return true
        if (firstAttempt == PeekOutcome.RETRY_WITHOUT_RANGE) {
            return executeM3u8Peek(url, useRangeHeader = false) == PeekOutcome.VALID
        }
        return false
    }

    private enum class PeekOutcome { VALID, INVALID, RETRY_WITHOUT_RANGE }

    private fun executeM3u8Peek(url: String, useRangeHeader: Boolean): PeekOutcome {
        return try {
            val reqBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", BROWSER_UA)
                .header("Accept", "*/*")

            if (useRangeHeader) {
                reqBuilder.header("Range", "bytes=0-${MAX_PEEK_BYTES - 1}")
            }

            val dynamicHeaders = getHeadersForUrl(url)
            if (dynamicHeaders.isNotEmpty()) {
                dynamicHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }
            } else if (url.contains("hopslan.com", ignoreCase = true) || url.contains("2ix2.com", ignoreCase = true)) {
                reqBuilder.header("Referer", "$DIRECTORY_BASE_URL/")
                reqBuilder.header("Origin", DIRECTORY_BASE_URL)
            }

            httpClient.newCall(reqBuilder.build()).execute().use { res ->
                if (useRangeHeader && res.code in setOf(400, 405, 416)) {
                    return PeekOutcome.RETRY_WITHOUT_RANGE
                }
                if (!res.isSuccessful && res.code != 206) return PeekOutcome.INVALID
                val contentType = res.header("Content-Type").orEmpty().lowercase()
                if (contentType.contains("text/html")) return PeekOutcome.INVALID

                val bodyStream = res.body?.byteStream() ?: return PeekOutcome.INVALID
                val buffer = ByteArray(MAX_PEEK_BYTES)
                var totalRead = 0
                while (totalRead < MAX_PEEK_BYTES) {
                    val read = bodyStream.read(buffer, totalRead, MAX_PEEK_BYTES - totalRead)
                    if (read <= 0) break
                    totalRead += read
                }
                if (totalRead <= 0) return PeekOutcome.INVALID
                val snippet = String(buffer, 0, totalRead, Charsets.UTF_8)
                if (isValidM3u8Payload(snippet)) PeekOutcome.VALID else PeekOutcome.INVALID
            }
        } catch (_: Exception) {
            PeekOutcome.INVALID
        }
    }

    internal fun isValidM3u8Payload(payload: String): Boolean {
        val trimmed = payload.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        if (trimmed.isEmpty()) return false
        val lower = trimmed.lowercase()
        if (lower.startsWith("<!doctype") || lower.startsWith("<html") || lower.contains("<body")) {
            return false
        }
        if (lower.contains("geoblock") || lower.contains("not available in your country")) {
            return false
        }
        return trimmed.startsWith("#EXTM3U") || trimmed.contains("#EXTM3U")
    }

    private fun isGeoFreeRelayUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("hopslan.com") || lower.contains("2ix2.com")
    }

    private fun extractHostLower(url: String): String? {
        return try {
            Uri.parse(url).host?.lowercase()
        } catch (_: Exception) {
            null
        }
    }

    private fun extractOrigin(url: String): String {
        return try {
            val uri = Uri.parse(url)
            val scheme = uri.scheme ?: "https"
            val host = uri.host ?: return DIRECTORY_BASE_URL
            "$scheme://$host"
        } catch (_: Exception) {
            DIRECTORY_BASE_URL
        }
    }

    private data class FetchedPage(
        val html: String,
        val cookieHeader: String
    )

    private fun fetchPageWithCookies(url: String, referer: String = "$DIRECTORY_BASE_URL/"): FetchedPage? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", BROWSER_UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "de-DE,de;q=0.9,en;q=0.7")
                .header("Referer", referer)
                .build()
            httpClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return null
                val body = res.body?.string() ?: return null
                val cookies = res.headers("Set-Cookie")
                    .mapNotNull { it.substringBefore(';').trim().takeIf { c -> c.contains('=') } }
                    .distinct()
                    .joinToString("; ")
                FetchedPage(html = body, cookieHeader = cookies)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extrahiert alle .m3u8-Stream-Kandidaten direkt aus einer 2ix2.com-Senderseite sowie aus
     * bis zu 2 verschachtelten Player-Iframes (inkl. Referer, Origin und Cookies).
     */
    internal fun extractAllM3u8CandidatesViaHttp(
        pageUrl: String,
        referer: String = "$DIRECTORY_BASE_URL/",
        depth: Int = 0,
        visited: MutableSet<String> = LinkedHashSet()
    ): List<ExtractedStreamCandidate> {
        if (depth > 2 || !visited.add(pageUrl)) return emptyList()
        val fetched = fetchPageWithCookies(pageUrl, referer = referer) ?: return emptyList()
        val results = LinkedHashMap<String, ExtractedStreamCandidate>()
        val pageOrigin = extractOrigin(pageUrl)

        findAllM3u8InHtml(fetched.html).forEach { foundUrl ->
            if (!isBlockedAssetOrAdUrl(foundUrl)) {
                results.putIfAbsent(
                    foundUrl,
                    ExtractedStreamCandidate(
                        url = foundUrl,
                        referer = pageUrl,
                        origin = pageOrigin,
                        cookie = fetched.cookieHeader
                    )
                )
            }
        }

        if (depth < 2) {
            val iframeRegex = """<iframe[^>]+(?:src|data-src)=["']((?:https?:)?//[^"']+|/[^"']+)["']"""
                .toRegex(RegexOption.IGNORE_CASE)
            for (match in iframeRegex.findAll(fetched.html)) {
                val rawIframe = match.groupValues[1].replace("&amp;", "&").trim()
                val iframeUrl = when {
                    rawIframe.startsWith("https://", ignoreCase = true) ||
                        rawIframe.startsWith("http://", ignoreCase = true) -> rawIframe
                    rawIframe.startsWith("//") -> "https:$rawIframe"
                    rawIframe.startsWith("/") -> "$pageOrigin$rawIframe"
                    else -> continue
                }
                if (isBlockedAssetOrAdUrl(iframeUrl)) continue
                extractAllM3u8CandidatesViaHttp(
                    pageUrl = iframeUrl,
                    referer = pageUrl,
                    depth = depth + 1,
                    visited = visited
                ).forEach { nested ->
                    results.putIfAbsent(nested.url, nested)
                }
            }
        }

        return results.values.toList()
    }

    internal fun findM3u8InHtml(content: String): String? = findAllM3u8InHtml(content).firstOrNull()

    internal fun findAllM3u8InHtml(content: String): List<String> {
        val unescaped = content
            .replace("\\/", "/")
            .replace("\\u002F", "/")
            .replace("\\u002f", "/")
            .replace("&amp;", "&")

        val found = LinkedHashSet<String>()
        val m3u8Regex = """(?:https?:)?//[^\s"'<>\\]+\.m3u8[^\s"'<>\\]*""".toRegex(RegexOption.IGNORE_CASE)
        for (match in m3u8Regex.findAll(unescaped)) {
            val raw = match.value.trimEnd(',', ';', ')', ']')
            val normalized = if (raw.startsWith("//")) "https:$raw" else raw
            found.add(normalized)
        }

        val atobRegex = """atob\(["']([A-Za-z0-9+/=]{20,})["']\)""".toRegex()
        for (match in atobRegex.findAll(unescaped)) {
            try {
                val decoded = String(android.util.Base64.decode(match.groupValues[1], android.util.Base64.DEFAULT))
                for (inner in m3u8Regex.findAll(decoded)) {
                    val raw = inner.value.trimEnd(',', ';', ')', ']')
                    val normalized = if (raw.startsWith("//")) "https:$raw" else raw
                    found.add(normalized)
                }
            } catch (_: Exception) {
            }
        }
        return found.toList()
    }

    internal fun isBlockedAssetOrAdUrl(url: String): Boolean {
        val lower = url.lowercase()
        if (lower.contains(".m3u8")) return false
        if (blockedAdHosts.any { lower.contains(it) }) return true
        val pathOnly = lower.substringBefore('?').substringBefore('#')
        return blockedAssetExtensions.any { pathOnly.endsWith(it) }
    }

    private fun emptyBlockedResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            204,
            "No Content",
            emptyMap(),
            ByteArrayInputStream(ByteArray(0))
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun extractM3u8ViaHeadlessInterceptor(
        context: Context,
        pageUrl: String
    ): ExtractedStreamCandidate? {
        return withTimeoutOrNull(HEADLESS_TIMEOUT_MS) {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    var webView: WebView? = null
                    var completed = false

                    fun finish(result: ExtractedStreamCandidate?) {
                        if (!completed) {
                            completed = true
                            try {
                                webView?.stopLoading()
                                webView?.destroy()
                            } catch (_: Exception) {
                            }
                            webView = null
                            if (cont.isActive) {
                                cont.resume(result)
                            }
                        }
                    }

                    try {
                        val wv = WebView(context.applicationContext).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.blockNetworkImage = true
                            settings.loadsImagesAutomatically = false
                            settings.cacheMode = WebSettings.LOAD_NO_CACHE
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            settings.userAgentString = BROWSER_UA

                            try {
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            } catch (_: Exception) {
                            }

                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): WebResourceResponse? {
                                    val reqUrl = request?.url?.toString()
                                        ?: return super.shouldInterceptRequest(view, request)
                                    if (reqUrl.contains(".m3u8", ignoreCase = true) && !isBlockedAssetOrAdUrl(reqUrl)) {
                                        val reqHeaders = request.requestHeaders ?: emptyMap()
                                        val capturedReferer = reqHeaders["Referer"]
                                            ?: reqHeaders["referer"]
                                            ?: pageUrl
                                        val capturedOrigin = reqHeaders["Origin"]
                                            ?: reqHeaders["origin"]
                                            ?: extractOrigin(capturedReferer)
                                        val capturedCookie = try {
                                            CookieManager.getInstance().getCookie(reqUrl).orEmpty()
                                        } catch (_: Exception) {
                                            ""
                                        }
                                        view?.post {
                                            finish(
                                                ExtractedStreamCandidate(
                                                    url = reqUrl,
                                                    referer = capturedReferer,
                                                    origin = capturedOrigin,
                                                    cookie = capturedCookie
                                                )
                                            )
                                        }
                                        return emptyBlockedResponse()
                                    }
                                    if (isBlockedAssetOrAdUrl(reqUrl)) {
                                        return emptyBlockedResponse()
                                    }
                                    return super.shouldInterceptRequest(view, request)
                                }
                            }
                        }
                        webView = wv
                        cont.invokeOnCancellation {
                            try {
                                wv.stopLoading()
                                wv.destroy()
                            } catch (_: Exception) {
                            }
                        }
                        wv.loadUrl(pageUrl, mapOf("Referer" to "$DIRECTORY_BASE_URL/"))
                    } catch (_: Throwable) {
                        finish(null)
                    }
                }
            }
        }
    }
}
