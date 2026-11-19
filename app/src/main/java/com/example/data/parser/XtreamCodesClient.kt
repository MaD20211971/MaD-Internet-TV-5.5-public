package com.example.data.parser

import android.util.JsonReader
import android.util.JsonToken
import com.example.data.model.ChannelItem
import com.example.data.model.ContentType
import com.example.data.model.EpgProgram
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.TimeUnit

import okhttp3.ConnectionPool
import okhttp3.Dispatcher

class XtreamCodesClient {

    private val client = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(24, 5, TimeUnit.MINUTES))
        .dispatcher(Dispatcher().apply {
            maxRequests = 24
            maxRequestsPerHost = 12
        })
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    companion object {
        private const val USER_AGENT = "IPTVSmartersPro/3.1.5.1"
    }

    private suspend fun executeWithBackoff(
        request: Request,
        maxRetries: Int = 2,
        initialDelayMs: Long = 250L
    ): okhttp3.Response {
        var currentDelay = initialDelayMs
        var lastException: Exception? = null
        for (attempt in 1..maxRetries) {
            try {
                val response = client.newCall(request).execute()
                if (response.code == 429 || response.code == 503) {
                    response.close()
                    if (attempt < maxRetries) {
                        kotlinx.coroutines.delay(currentDelay)
                        currentDelay *= 2
                        continue
                    }
                }
                return response
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries) {
                    kotlinx.coroutines.delay(currentDelay)
                    currentDelay *= 2
                }
            }
        }
        throw lastException ?: java.io.IOException("Verbindung fehlgeschlagen nach $maxRetries Versuchen")
    }

    suspend fun fetchXtreamContent(
        serverUrl: String,
        username: String,
        password: String,
        playlistId: String,
        syncOnlyGerman: Boolean = false,
        allowedCountries: Set<String> = if (syncOnlyGerman) setOf("DE", "AT", "CH", "ADULT") else emptySet(),
        includeVodAndSeries: Boolean = true,
        onProgress: ((step: String, detail: String, liveCount: Int, movieCount: Int, seriesCount: Int) -> Unit)? = null,
        onBatchParsed: (suspend (batch: List<ChannelItem>, isDachBatch: Boolean) -> Unit)? = null,
        onCategoriesDiscovered: (suspend (List<com.example.data.model.ProviderCategoryEntity>) -> Unit)? = null
    ): Result<List<ChannelItem>> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = cleanServerUrl(serverUrl)
            val effectiveAllowedCountries: Set<String> = when {
                allowedCountries.isNotEmpty() && "ALL" !in allowedCountries && "ALLE" !in allowedCountries ->
                    allowedCountries.map { it.trim().uppercase() }.toSet()
                syncOnlyGerman -> setOf("DE", "AT", "CH", "ADULT")
                else -> emptySet()
            }
            val filterByCountry = effectiveAllowedCountries.isNotEmpty()
            val wantsDach = !filterByCountry || effectiveAllowedCountries.any { it in listOf("DE", "AT", "CH") }

            val channels = java.util.Collections.synchronizedList(mutableListOf<ChannelItem>())
            val dachBuffer = mutableListOf<ChannelItem>()
            val nonDachBuffer = mutableListOf<ChannelItem>()
            val bufferMutex = kotlinx.coroutines.sync.Mutex()
            val totalParsedCount = java.util.concurrent.atomic.AtomicInteger(0)
            val liveCount = java.util.concurrent.atomic.AtomicInteger(0)
            val movieCount = java.util.concurrent.atomic.AtomicInteger(0)
            val seriesCount = java.util.concurrent.atomic.AtomicInteger(0)

            suspend fun emitParsedChannel(ch: ChannelItem, isDach: Boolean) {
                bufferMutex.lock()
                try {
                    totalParsedCount.incrementAndGet()
                    if (onBatchParsed != null) {
                        if (isDach) {
                            dachBuffer.add(ch)
                            if (channels.size < 5000) channels.add(ch)
                            if (dachBuffer.size >= 500) {
                                val snapshot = ArrayList(dachBuffer)
                                dachBuffer.clear()
                                onBatchParsed(snapshot, true)
                            }
                        } else {
                            nonDachBuffer.add(ch)
                            if (channels.size < 3000 && dachBuffer.isEmpty()) channels.add(ch)
                            if (nonDachBuffer.size >= 2000) {
                                val snapshot = ArrayList(nonDachBuffer)
                                nonDachBuffer.clear()
                                onBatchParsed(snapshot, false)
                            }
                        }
                    } else {
                        if (channels.size < 25000) {
                            channels.add(ch)
                        }
                    }
                } finally {
                    bufferMutex.unlock()
                }
            }

            suspend fun flushBuffers() {
                bufferMutex.lock()
                try {
                    if (onBatchParsed != null) {
                        if (dachBuffer.isNotEmpty()) {
                            val snapshot = ArrayList(dachBuffer)
                            dachBuffer.clear()
                            onBatchParsed(snapshot, true)
                        }
                        if (nonDachBuffer.isNotEmpty()) {
                            val snapshot = ArrayList(nonDachBuffer)
                            nonDachBuffer.clear()
                            onBatchParsed(snapshot, false)
                        }
                    }
                } finally {
                    bufferMutex.unlock()
                }
            }

            fun classifyCategories(
                categories: Map<String, String>,
                isVodOrSeries: Boolean
            ): Pair<Set<String>, Set<String>> {
                if (!filterByCountry) return Pair(emptySet(), emptySet())
                val matchingIds = mutableSetOf<String>()
                val excludedForeignIds = mutableSetOf<String>()
                for ((catId, catName) in categories) {
                    if (ContentClassifier.isCategoryMatchingCountries(catName, effectiveAllowedCountries, isVodOrSeries)) {
                        matchingIds.add(catId)
                    } else {
                        val detected = com.example.data.country.CountryNormalizer.extractCountryFromCategory(catName)
                            ?: ContentClassifier.detectCountryForGroup(catName).takeIf { it != "INT" && it != "SPORT" && it != "OTHER" }
                        if (detected != null && detected !in effectiveAllowedCountries) {
                            val isUkMatch = (detected == "GB" && "UK" in effectiveAllowedCountries) || (detected == "UK" && "GB" in effectiveAllowedCountries)
                            val isAlbMatch = (detected == "AL" && "ALB" in effectiveAllowedCountries) || (detected == "ALB" && "AL" in effectiveAllowedCountries)
                            if (!isUkMatch && !isAlbMatch) {
                                excludedForeignIds.add(catId)
                            }
                        } else if (isVodOrSeries && wantsDach && !ContentClassifier.isGermanOrDachVodCategory(catName)) {
                            val isAdultMatched = "ADULT" in effectiveAllowedCountries && ContentClassifier.isAdultContent("", catName)
                            if (!isAdultMatched && ContentClassifier.hasExplicitForeignVodMarker(catName)) {
                                excludedForeignIds.add(catId)
                            }
                        }
                    }
                }
                return Pair(matchingIds, excludedForeignIds)
            }

            fun isCountryMatch(countryCode: String, isDach: Boolean): Boolean {
                if (!filterByCountry) return true
                val upper = countryCode.uppercase()
                if (upper in effectiveAllowedCountries) return true
                if (isDach && wantsDach) return true
                if (upper == "GB" && "UK" in effectiveAllowedCountries) return true
                if (upper == "UK" && "GB" in effectiveAllowedCountries) return true
                if (upper == "AL" && "ALB" in effectiveAllowedCountries) return true
                if (upper == "ALB" && "AL" in effectiveAllowedCountries) return true
                return false
            }

            onProgress?.invoke("Verbindung herstellen", "Verbinde mit Xtream Server...", 0, 0, 0)

            // 1. Fetch Live + VOD + Series Category maps concurrently
            val (liveCategories, vodCategories, seriesCategories) = kotlinx.coroutines.coroutineScope {
                val liveCatsDeferred = async { fetchCategories(baseUrl, username, password, "get_live_categories") }
                val vodCatsDeferred = async {
                    if (includeVodAndSeries) fetchCategories(baseUrl, username, password, "get_vod_categories") else emptyMap()
                }
                val seriesCatsDeferred = async {
                    if (includeVodAndSeries) fetchCategories(baseUrl, username, password, "get_series_categories") else emptyMap()
                }
                Triple(liveCatsDeferred.await(), vodCatsDeferred.await(), seriesCatsDeferred.await())
            }

            val (matchingLiveCatIds, excludedLiveCatIds) = classifyCategories(liveCategories, isVodOrSeries = false)
            val (matchingVodCatIds, excludedVodCatIds) = classifyCategories(vodCategories, isVodOrSeries = true)
            val (matchingSeriesCatIds, excludedSeriesCatIds) = classifyCategories(seriesCategories, isVodOrSeries = true)

            if (onCategoriesDiscovered != null) {
                val discovered = ArrayList<com.example.data.model.ProviderCategoryEntity>(
                    liveCategories.size + vodCategories.size + seriesCategories.size
                )
                var liveOrder = 0
                for ((catId, catName) in liveCategories) {
                    val parsed = com.example.data.category.ProviderCategoryEngine.parseCategory(
                        rawGroup = catName,
                        providerType = com.example.data.country.ProviderType.XTREAM
                    )
                    val isSynced = !filterByCountry || catId in matchingLiveCatIds || parsed.countryCode in effectiveAllowedCountries
                    discovered.add(
                        com.example.data.model.ProviderCategoryEntity(
                            id = "${playlistId}_LIVE_TV_${catId}",
                            playlistId = playlistId,
                            contentType = ContentType.LIVE_TV,
                            categoryId = catId,
                            rawCategoryName = catName,
                            countryCode = parsed.countryCode,
                            parentCategory = parsed.parentCategory,
                            subCategory = parsed.subCategory,
                            sortOrder = liveOrder++,
                            isSynced = isSynced
                        )
                    )
                }
                var vodOrder = 0
                for ((catId, catName) in vodCategories) {
                    val parsed = com.example.data.category.ProviderCategoryEngine.parseCategory(
                        rawGroup = catName,
                        providerType = com.example.data.country.ProviderType.XTREAM
                    )
                    val isSynced = !filterByCountry || catId in matchingVodCatIds || parsed.countryCode in effectiveAllowedCountries
                    discovered.add(
                        com.example.data.model.ProviderCategoryEntity(
                            id = "${playlistId}_MOVIE_${catId}",
                            playlistId = playlistId,
                            contentType = ContentType.MOVIE,
                            categoryId = catId,
                            rawCategoryName = catName,
                            countryCode = parsed.countryCode,
                            parentCategory = parsed.parentCategory,
                            subCategory = parsed.subCategory,
                            sortOrder = vodOrder++,
                            isSynced = isSynced
                        )
                    )
                }
                var seriesOrder = 0
                for ((catId, catName) in seriesCategories) {
                    val parsed = com.example.data.category.ProviderCategoryEngine.parseCategory(
                        rawGroup = catName,
                        providerType = com.example.data.country.ProviderType.XTREAM
                    )
                    val isSynced = !filterByCountry || catId in matchingSeriesCatIds || parsed.countryCode in effectiveAllowedCountries
                    discovered.add(
                        com.example.data.model.ProviderCategoryEntity(
                            id = "${playlistId}_SERIES_${catId}",
                            playlistId = playlistId,
                            contentType = ContentType.SERIES,
                            categoryId = catId,
                            rawCategoryName = catName,
                            countryCode = parsed.countryCode,
                            parentCategory = parsed.parentCategory,
                            subCategory = parsed.subCategory,
                            sortOrder = seriesOrder++,
                            isSynced = isSynced
                        )
                    )
                }
                if (discovered.isNotEmpty()) {
                    onCategoriesDiscovered(discovered)
                }
            }

            suspend fun parseLiveStreamFromUrl(url: String, enforceFilter: Boolean): Int {
                var parsedInCall = 0
                val liveRequest = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .build()
                val liveResponse = executeWithBackoff(liveRequest)
                liveResponse.use { resp ->
                    val bodyStream = resp.body?.byteStream() ?: return 0
                    val reader = JsonReader(java.io.BufferedReader(InputStreamReader(bodyStream, Charsets.UTF_8), 65536))
                    val peekToken = try { reader.peek() } catch (e: Exception) { null }
                    if (peekToken == JsonToken.BEGIN_ARRAY) {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            try {
                                if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                                    reader.skipValue()
                                    continue
                                }
                                reader.beginObject()
                                var streamId = ""
                                var name = ""
                                var icon: String? = null
                                var catId = ""
                                var catName = ""
                                var epgId: String? = null
                                var numOrder = 0

                                while (reader.hasNext()) {
                                    when (reader.nextName()) {
                                        "stream_id" -> streamId = nextStringSafe(reader)
                                        "name" -> name = nextStringSafe(reader)
                                        "stream_icon" -> icon = nextStringSafe(reader).takeIf { it.isNotBlank() }
                                        "category_id" -> catId = nextStringSafe(reader)
                                        "category_name" -> catName = nextStringSafe(reader)
                                        "epg_channel_id" -> epgId = nextStringSafe(reader)
                                        "num" -> numOrder = nextStringSafe(reader).toIntOrNull() ?: 0
                                        else -> reader.skipValue()
                                    }
                                }
                                reader.endObject()

                                if (streamId.isBlank()) continue
                                if (enforceFilter && catId.isNotBlank() && catId in excludedLiveCatIds) continue

                                val displayName = name.ifBlank { "Live Channel $streamId" }
                                val category = catName.ifBlank { liveCategories[catId] ?: "Live TV" }
                                val isCatMatched = catId.isNotBlank() && catId in matchingLiveCatIds

                                val (fastDach, fastCountry) = ContentClassifier.detectCountryAndDach("", category)
                                if (enforceFilter && !isCatMatched && !isCountryMatch(fastCountry, fastDach)) {
                                    continue
                                }

                                val streamUrl = "$baseUrl/live/$username/$password/$streamId.m3u8"
                                val effectiveGroup = category.ifBlank { ContentClassifier.resolveLiveGroupTitle(displayName, category, fastCountry, fastDach) }
                                if (ContentClassifier.isHeaderOrDividerChannel(displayName, streamUrl, effectiveGroup)) continue

                                val tvgId = if (!epgId.isNullOrBlank()) epgId else streamId
                                val isRadio = category.contains("RADIO", ignoreCase = true) || displayName.contains("RADIO", ignoreCase = true)
                                val isVip = ContentClassifier.detectVip(displayName, category)
                                val is24_7 = ContentClassifier.detect24_7(displayName, category)

                                val parsedHierarchy = com.example.data.category.ProviderCategoryEngine.parseCategory(
                                    rawGroup = effectiveGroup,
                                    providerType = com.example.data.country.ProviderType.XTREAM,
                                    fallbackName = ""
                                )
                                val providerCatCountry = com.example.data.country.CountryNormalizer.extractCountryFromCategory(category)
                                    ?: parsedHierarchy.countryCode.takeIf { it !in listOf("UNKNOWN", "CONFLICT", "OTHER", "INT") }

                                val countryResult = com.example.data.country.CountryResolver.resolve(
                                    title = displayName,
                                    groupTitle = effectiveGroup,
                                    providerCategory = category,
                                    tvgId = tvgId,
                                    tvgName = displayName,
                                    streamUrl = streamUrl,
                                    providerType = com.example.data.country.ProviderType.XTREAM,
                                    ignoreChannelName = true
                                )

                                val resolvedCountry = com.example.data.country.CountryConstants.normalizeCountryCode(
                                    when {
                                        !providerCatCountry.isNullOrBlank() -> providerCatCountry
                                        countryResult.countryCode !in listOf("UNKNOWN", "CONFLICT", "INT") -> countryResult.countryCode
                                        fastCountry != "INT" -> fastCountry
                                        isCatMatched && effectiveAllowedCountries.size == 1 -> effectiveAllowedCountries.first()
                                        isCatMatched && wantsDach -> "DE"
                                        else -> "OTHER"
                                    }
                                )
                                val resolvedDach = countryResult.isDach || fastDach || resolvedCountry in listOf("DE", "AT", "CH")

                                if (enforceFilter && !isCatMatched && !isCountryMatch(resolvedCountry, resolvedDach)) {
                                    continue
                                }

                                val curLive = liveCount.incrementAndGet()

                                emitParsedChannel(
                                    ChannelItem(
                                        id = "xt_live_${playlistId}_${streamId}",
                                        playlistId = playlistId,
                                        name = displayName,
                                        streamUrl = streamUrl,
                                        logoUrl = icon,
                                        groupTitle = effectiveGroup,
                                        providerCategory = category,
                                        parentCategory = parsedHierarchy.parentCategory,
                                        subCategory = parsedHierarchy.subCategory,
                                        providerSortOrder = if (numOrder > 0) numOrder else curLive,
                                        type = if (isRadio) ContentType.RADIO else ContentType.LIVE_TV,
                                        tvgId = tvgId,
                                        tvgName = displayName,
                                        isDach = resolvedDach,
                                        countryCode = resolvedCountry,
                                        is24_7 = is24_7,
                                        isVip = isVip,
                                        countryConfidence = countryResult.confidence.coerceAtLeast(if (isCatMatched || !providerCatCountry.isNullOrBlank()) 90 else 0),
                                        classificationStatus = if (!providerCatCountry.isNullOrBlank()) "CONFIRMED" else countryResult.status.name,
                                        countryEvidenceJson = countryResult.evidenceDescriptions.joinToString("; "),
                                        normalizedChannelId = countryResult.normalizedChannelId,
                                        normalizedTitle = com.example.data.country.CountryNormalizer.cleanText(displayName),
                                        sourceId = playlistId,
                                        providerId = streamId
                                    ),
                                    isDach = resolvedDach
                                )
                                parsedInCall++
                                if (curLive % 250 == 0) {
                                    kotlinx.coroutines.yield()
                                    onProgress?.invoke("TV-Kanäle laden", "Lade TV Channels: $curLive...", curLive, movieCount.get(), seriesCount.get())
                                }
                            } catch (itemErr: Exception) {
                                skipToEndOfObject(reader)
                                continue
                            }
                        }
                        reader.endArray()
                    }
                    reader.close()
                }
                return parsedInCall
            }

            // 2. Fetch Live Streams: Use category-targeted fast fetch when selective country sync is active
            try {
                var liveLoadedByCategory = false
                if (filterByCountry && matchingLiveCatIds.isNotEmpty() && matchingLiveCatIds.size <= 120 && liveCategories.size > matchingLiveCatIds.size) {
                    val semaphore = kotlinx.coroutines.sync.Semaphore(10)
                    val totalCatItems = kotlinx.coroutines.coroutineScope {
                        matchingLiveCatIds.map { catId ->
                            async {
                                semaphore.acquire()
                                try {
                                    val catUrl = "$baseUrl/player_api.php?username=$username&password=$password&action=get_live_streams&category_id=$catId"
                                    parseLiveStreamFromUrl(catUrl, enforceFilter = false)
                                } catch (_: Throwable) {
                                    0
                                } finally {
                                    semaphore.release()
                                }
                            }
                        }.sumOf { it.await() }
                    }
                    if (totalCatItems > 0) {
                        liveLoadedByCategory = true
                        flushBuffers()
                    }
                }

                if (!liveLoadedByCategory) {
                    val liveUrl = "$baseUrl/player_api.php?username=$username&password=$password&action=get_live_streams"
                    parseLiveStreamFromUrl(liveUrl, enforceFilter = filterByCountry)
                    flushBuffers()
                }
            } catch (e: Throwable) {
                android.util.Log.w("XtreamCodesClient", "Live fetch warning: ${e.message}")
            }

            if (includeVodAndSeries) {
                onProgress?.invoke("VOD & Serien laden", "TV Channels: ${liveCount.get()} • Lade Filme & Serien...", liveCount.get(), 0, 0)

                suspend fun parseVodFromUrl(url: String, enforceFilter: Boolean): Int {
                    var parsedInCall = 0
                    val vodRequest = Request.Builder()
                        .url(url)
                        .header("User-Agent", USER_AGENT)
                        .build()
                    val vodResponse = executeWithBackoff(vodRequest)
                    vodResponse.use { resp ->
                        val vodStream = resp.body?.byteStream() ?: return 0
                        val reader = JsonReader(java.io.BufferedReader(InputStreamReader(vodStream, Charsets.UTF_8), 65536))
                        val peekToken = try { reader.peek() } catch (e: Exception) { null }
                        if (peekToken == JsonToken.BEGIN_ARRAY) {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                try {
                                    if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                                        reader.skipValue()
                                        continue
                                    }
                                    reader.beginObject()
                                    var streamId = ""
                                    var name = ""
                                    var icon: String? = null
                                    var catId = ""
                                    var catName = ""
                                    var ext = "mp4"
                                    var numOrder = 0

                                    while (reader.hasNext()) {
                                        when (reader.nextName()) {
                                            "stream_id" -> streamId = nextStringSafe(reader)
                                            "name" -> name = nextStringSafe(reader)
                                            "stream_icon" -> icon = nextStringSafe(reader).takeIf { it.isNotBlank() }
                                            "category_id" -> catId = nextStringSafe(reader)
                                            "category_name" -> catName = nextStringSafe(reader)
                                            "container_extension" -> ext = nextStringSafe(reader).ifBlank { "mp4" }
                                            "num" -> numOrder = nextStringSafe(reader).toIntOrNull() ?: 0
                                            else -> reader.skipValue()
                                        }
                                    }
                                    reader.endObject()

                                    if (streamId.isBlank()) continue
                                    if (enforceFilter && catId.isNotBlank() && catId in excludedVodCatIds) continue

                                    val movieName = name.ifBlank { "Movie $streamId" }
                                    val category = catName.ifBlank { vodCategories[catId] ?: "Filme" }
                                    val isCatMatched = catId.isNotBlank() && catId in matchingVodCatIds
                                    val isGermanVod = wantsDach && (isCatMatched || ContentClassifier.isGermanOrDachVodItem(movieName, category))
                                    val (fastDach, fastCountry) = ContentClassifier.detectCountryAndDach(movieName, category)

                                    if (enforceFilter && !isCatMatched && !isGermanVod && !isCountryMatch(fastCountry, fastDach)) {
                                        continue
                                    }

                                    val streamUrl = "$baseUrl/movie/$username/$password/$streamId.$ext"
                                    val isVip = ContentClassifier.detectVip(movieName, category)
                                    val effectiveGroup = category.ifBlank { ContentClassifier.resolveMovieGroupTitle(movieName, category, fastCountry, fastDach || isGermanVod) }

                                    val parsedHierarchy = com.example.data.category.ProviderCategoryEngine.parseCategory(
                                        rawGroup = effectiveGroup,
                                        providerType = com.example.data.country.ProviderType.XTREAM,
                                        fallbackName = movieName
                                    )
                                    val providerCatCountry = com.example.data.country.CountryNormalizer.extractCountryFromCategory(category)
                                        ?: parsedHierarchy.countryCode.takeIf { it !in listOf("UNKNOWN", "CONFLICT", "OTHER", "INT", "SPORT", "VIP") }

                                    val countryResult = com.example.data.country.CountryResolver.resolve(
                                        title = movieName,
                                        groupTitle = effectiveGroup,
                                        providerCategory = category,
                                        streamUrl = streamUrl,
                                        providerType = com.example.data.country.ProviderType.XTREAM,
                                        ignoreChannelName = true
                                    )
                                    val effectiveCountry = com.example.data.country.CountryConstants.normalizeCountryCode(
                                        when {
                                            !providerCatCountry.isNullOrBlank() -> providerCatCountry
                                            countryResult.countryCode !in listOf("UNKNOWN", "CONFLICT", "INT", "OTHER", "SPORT", "VIP") -> countryResult.countryCode
                                            fastCountry !in listOf("INT", "OTHER", "UNKNOWN", "SPORT", "VIP") -> fastCountry
                                            isGermanVod -> "DE"
                                            isCatMatched && effectiveAllowedCountries.size == 1 -> effectiveAllowedCountries.first()
                                            isCatMatched && wantsDach -> "DE"
                                            wantsDach -> "DE"
                                            else -> countryResult.countryCode
                                        }
                                    )
                                    val effectiveDach = countryResult.isDach || fastDach || isGermanVod || effectiveCountry in listOf("DE", "AT", "CH")

                                    val curMovies = movieCount.incrementAndGet()

                                    emitParsedChannel(
                                        ChannelItem(
                                            id = "xt_vod_${playlistId}_${streamId}",
                                            playlistId = playlistId,
                                            name = movieName,
                                            streamUrl = streamUrl,
                                            logoUrl = icon,
                                            groupTitle = effectiveGroup,
                                            providerCategory = category,
                                            parentCategory = parsedHierarchy.parentCategory,
                                            subCategory = parsedHierarchy.subCategory,
                                            providerSortOrder = if (numOrder > 0) numOrder else curMovies,
                                            type = ContentType.MOVIE,
                                            isDach = effectiveDach,
                                            countryCode = effectiveCountry,
                                            isVip = isVip,
                                            countryConfidence = countryResult.confidence.coerceAtLeast(if (effectiveDach || !providerCatCountry.isNullOrBlank()) 90 else 0),
                                            classificationStatus = if (!providerCatCountry.isNullOrBlank() || effectiveDach) "CONFIRMED" else countryResult.status.name,
                                            countryEvidenceJson = countryResult.evidenceDescriptions.joinToString("; "),
                                            normalizedChannelId = countryResult.normalizedChannelId,
                                            normalizedTitle = com.example.data.country.CountryNormalizer.cleanText(movieName),
                                            sourceId = playlistId,
                                            providerId = streamId
                                        ),
                                        isDach = effectiveDach
                                    )
                                    parsedInCall++
                                    if (curMovies % 250 == 0) {
                                        kotlinx.coroutines.yield()
                                        onProgress?.invoke("VOD & Serien laden", "TV Channels: ${liveCount.get()} • Filme: $curMovies • Serien: ${seriesCount.get()}...", liveCount.get(), curMovies, seriesCount.get())
                                    }
                                } catch (itemErr: Exception) {
                                    skipToEndOfObject(reader)
                                    continue
                                }
                            }
                            reader.endArray()
                        }
                        reader.close()
                    }
                    return parsedInCall
                }

                suspend fun parseSeriesFromUrl(url: String, enforceFilter: Boolean): Int {
                    var parsedInCall = 0
                    val seriesRequest = Request.Builder()
                        .url(url)
                        .header("User-Agent", USER_AGENT)
                        .build()
                    val seriesResponse = executeWithBackoff(seriesRequest)
                    seriesResponse.use { resp ->
                        val seriesStream = resp.body?.byteStream() ?: return 0
                        val reader = JsonReader(java.io.BufferedReader(InputStreamReader(seriesStream, Charsets.UTF_8), 65536))
                        val peekToken = try { reader.peek() } catch (e: Exception) { null }
                        if (peekToken == JsonToken.BEGIN_ARRAY) {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                try {
                                    if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                                        reader.skipValue()
                                        continue
                                    }
                                    reader.beginObject()
                                    var seriesId = ""
                                    var title = ""
                                    var cover: String? = null
                                    var catId = ""
                                    var catName = ""
                                    var plot: String? = null
                                    var numOrder = 0

                                    while (reader.hasNext()) {
                                        when (reader.nextName()) {
                                            "series_id" -> seriesId = nextStringSafe(reader)
                                            "name" -> title = nextStringSafe(reader)
                                            "cover" -> cover = nextStringSafe(reader).takeIf { it.isNotBlank() }
                                            "category_id" -> catId = nextStringSafe(reader)
                                            "category_name" -> catName = nextStringSafe(reader)
                                            "plot" -> plot = nextStringSafe(reader).takeIf { it.isNotBlank() }
                                            "num" -> numOrder = nextStringSafe(reader).toIntOrNull() ?: 0
                                            else -> reader.skipValue()
                                        }
                                    }
                                    reader.endObject()

                                    if (seriesId.isBlank()) continue
                                    if (enforceFilter && catId.isNotBlank() && catId in excludedSeriesCatIds) continue

                                    val sTitle = title.ifBlank { "Series $seriesId" }
                                    val category = catName.ifBlank { seriesCategories[catId] ?: "Serien" }
                                    val isCatMatched = catId.isNotBlank() && catId in matchingSeriesCatIds
                                    val isGermanSeries = wantsDach && (isCatMatched || ContentClassifier.isGermanOrDachVodItem(sTitle, category))
                                    val (fastDach, fastCountry) = ContentClassifier.detectCountryAndDach(sTitle, category)

                                    if (enforceFilter && !isCatMatched && !isGermanSeries && !isCountryMatch(fastCountry, fastDach)) {
                                        continue
                                    }

                                    val streamUrl = "$baseUrl/series/$username/$password/$seriesId.mp4"
                                    val parsedHierarchy = com.example.data.category.ProviderCategoryEngine.parseCategory(
                                        rawGroup = category,
                                        providerType = com.example.data.country.ProviderType.XTREAM,
                                        fallbackName = sTitle
                                    )
                                    val providerCatCountry = com.example.data.country.CountryNormalizer.extractCountryFromCategory(category)
                                        ?: parsedHierarchy.countryCode.takeIf { it !in listOf("UNKNOWN", "CONFLICT", "OTHER", "INT", "SPORT", "VIP") }

                                    val countryResult = com.example.data.country.CountryResolver.resolve(
                                        title = sTitle,
                                        groupTitle = category,
                                        providerCategory = category,
                                        streamUrl = streamUrl,
                                        providerType = com.example.data.country.ProviderType.XTREAM,
                                        ignoreChannelName = true
                                    )
                                    val effectiveCountry = com.example.data.country.CountryConstants.normalizeCountryCode(
                                        when {
                                            !providerCatCountry.isNullOrBlank() -> providerCatCountry
                                            countryResult.countryCode !in listOf("UNKNOWN", "CONFLICT", "INT", "OTHER", "SPORT", "VIP") -> countryResult.countryCode
                                            fastCountry !in listOf("INT", "OTHER", "UNKNOWN", "SPORT", "VIP") -> fastCountry
                                            isGermanSeries -> "DE"
                                            isCatMatched && effectiveAllowedCountries.size == 1 -> effectiveAllowedCountries.first()
                                            isCatMatched && wantsDach -> "DE"
                                            wantsDach -> "DE"
                                            else -> countryResult.countryCode
                                        }
                                    )
                                    val effectiveDach = countryResult.isDach || fastDach || isGermanSeries || effectiveCountry in listOf("DE", "AT", "CH")

                                    val curSeries = seriesCount.incrementAndGet()

                                    emitParsedChannel(
                                        ChannelItem(
                                            id = "xt_ser_${playlistId}_${seriesId}",
                                            playlistId = playlistId,
                                            name = sTitle,
                                            streamUrl = streamUrl,
                                            logoUrl = cover,
                                            groupTitle = category,
                                            providerCategory = category,
                                            parentCategory = parsedHierarchy.parentCategory,
                                            subCategory = parsedHierarchy.subCategory,
                                            providerSortOrder = if (numOrder > 0) numOrder else curSeries,
                                            type = ContentType.SERIES,
                                            seriesTitle = sTitle,
                                            seasonNumber = 1,
                                            episodeNumber = 1,
                                            plot = plot,
                                            isDach = effectiveDach,
                                            countryCode = effectiveCountry,
                                            countryConfidence = countryResult.confidence.coerceAtLeast(if (effectiveDach || !providerCatCountry.isNullOrBlank()) 90 else 0),
                                            classificationStatus = if (!providerCatCountry.isNullOrBlank() || effectiveDach) "CONFIRMED" else countryResult.status.name,
                                            countryEvidenceJson = countryResult.evidenceDescriptions.joinToString("; "),
                                            normalizedChannelId = countryResult.normalizedChannelId,
                                            normalizedTitle = com.example.data.country.CountryNormalizer.cleanText(sTitle),
                                            providerId = seriesId,
                                            sourceId = playlistId
                                        ),
                                        isDach = effectiveDach
                                    )
                                    parsedInCall++
                                    if (curSeries % 250 == 0) {
                                        kotlinx.coroutines.yield()
                                        onProgress?.invoke("VOD & Serien laden", "TV Channels: ${liveCount.get()} • Filme: ${movieCount.get()} • Serien: $curSeries...", liveCount.get(), movieCount.get(), curSeries)
                                    }
                                } catch (itemErr: Exception) {
                                    skipToEndOfObject(reader)
                                    continue
                                }
                            }
                            reader.endArray()
                        }
                        reader.close()
                    }
                    return parsedInCall
                }

                // 3 & 4. Fetch VOD Movies and Series IN PARALLEL using single bulk stream first (avoids Xtream max_connections bans)
                kotlinx.coroutines.coroutineScope {
                    val vodJob = async {
                        kotlinx.coroutines.withTimeoutOrNull(45_000L) {
                            try {
                                val vodUrl = "$baseUrl/player_api.php?username=$username&password=$password&action=get_vod_streams"
                                val bulkVodCount = try {
                                    parseVodFromUrl(vodUrl, enforceFilter = filterByCountry)
                                } catch (_: Throwable) {
                                    0
                                }

                                if (bulkVodCount == 0 && matchingVodCatIds.isNotEmpty()) {
                                    val semaphore = kotlinx.coroutines.sync.Semaphore(4)
                                    kotlinx.coroutines.coroutineScope {
                                        matchingVodCatIds.map { catId ->
                                            async {
                                                semaphore.acquire()
                                                try {
                                                    val catUrl = "$baseUrl/player_api.php?username=$username&password=$password&action=get_vod_streams&category_id=$catId"
                                                    parseVodFromUrl(catUrl, enforceFilter = false)
                                                } catch (_: Throwable) {
                                                    0
                                                } finally {
                                                    semaphore.release()
                                                }
                                            }
                                        }.sumOf { it.await() }
                                    }
                                }
                            } catch (e: Throwable) {
                                android.util.Log.w("XtreamCodesClient", "VOD fetch warning: ${e.message}")
                            }
                        }
                    }

                    val seriesJob = async {
                        kotlinx.coroutines.withTimeoutOrNull(45_000L) {
                            try {
                                val seriesUrl = "$baseUrl/player_api.php?username=$username&password=$password&action=get_series"
                                val bulkSeriesCount = try {
                                    parseSeriesFromUrl(seriesUrl, enforceFilter = filterByCountry)
                                } catch (_: Throwable) {
                                    0
                                }

                                if (bulkSeriesCount == 0 && matchingSeriesCatIds.isNotEmpty()) {
                                    val semaphore = kotlinx.coroutines.sync.Semaphore(4)
                                    kotlinx.coroutines.coroutineScope {
                                        matchingSeriesCatIds.map { catId ->
                                            async {
                                                semaphore.acquire()
                                                try {
                                                    val catUrl = "$baseUrl/player_api.php?username=$username&password=$password&action=get_series&category_id=$catId"
                                                    parseSeriesFromUrl(catUrl, enforceFilter = false)
                                                } catch (_: Throwable) {
                                                    0
                                                } finally {
                                                    semaphore.release()
                                                }
                                            }
                                        }.sumOf { it.await() }
                                    }
                                }
                            } catch (e: Throwable) {
                                android.util.Log.w("XtreamCodesClient", "Series fetch warning: ${e.message}")
                            }
                        }
                    }

                    vodJob.await()
                    seriesJob.await()
                }
            }

            flushBuffers()
            val finalLive = liveCount.get()
            val finalMovies = movieCount.get()
            val finalSeries = seriesCount.get()
            onProgress?.invoke("Import abgeschlossen", "TV Channels: $finalLive • Filme: $finalMovies • Serien: $finalSeries", finalLive, finalMovies, finalSeries)

            if (channels.isEmpty() && totalParsedCount.get() == 0) {
                Result.failure(Exception("Keine passenden Kanäle auf dem Server gefunden."))
            } else {
                Result.success(channels)
            }
        } catch (e: Throwable) {
            Result.failure(Exception(e.message ?: "Unbekannter Fehler bei Xtream Verbindung", e))
        }
    }

    private fun skipToEndOfObject(reader: JsonReader) {
        try {
            while (reader.hasNext()) {
                when (reader.peek()) {
                    JsonToken.END_OBJECT -> {
                        reader.endObject()
                        return
                    }
                    JsonToken.END_ARRAY -> return
                    JsonToken.NAME -> {
                        reader.nextName()
                        reader.skipValue()
                    }
                    else -> reader.skipValue()
                }
            }
        } catch (_: Exception) {
            // best effort recovery
        }
    }

    private fun nextStringSafe(reader: JsonReader): String {
        return try {
            when (reader.peek()) {
                JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
                JsonToken.BOOLEAN -> reader.nextBoolean().toString()
                JsonToken.NULL -> {
                    reader.nextNull()
                    ""
                }
                else -> {
                    reader.skipValue()
                    ""
                }
            }
        } catch (e: Exception) {
            ""
        }
    }

    private suspend fun fetchCategories(baseUrl: String, user: String, pass: String, action: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val url = "$baseUrl/player_api.php?username=$user&password=$pass&action=$action"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()
            val resp = executeWithBackoff(req)
            val body = resp.body?.string() ?: ""
            if (body.startsWith("[")) {
                val arr = JSONArray(body)
                val rawCategories = mutableListOf<com.example.data.model.Category>()
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val id = item.optString("category_id")
                    val name = item.optString("category_name")
                    if (id.isNotBlank() && name.isNotBlank()) {
                        rawCategories.add(com.example.data.model.Category(id = id, name = name))
                    }
                }
                // Sort IPTV provider categories according to DACH priorities with stable sort
                val prioritized = com.example.data.category.prioritizeIptvCategories(rawCategories)
                for (cat in prioritized) {
                    map[cat.id] = cat.name
                }
            }
        } catch (e: Exception) {
            // fallback
        }
        return map
    }

    private fun cleanServerUrl(url: String): String {
        var clean = url.trim().removeSuffix("/")
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "http://$clean"
        }
        return clean
    }

    suspend fun checkAccountConnections(serverUrl: String, username: String, password: String): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = cleanServerUrl(serverUrl)
            val url = "$baseUrl/player_api.php?username=$username&password=$password"
            val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (body.contains("\"user_info\"")) {
                val json = JSONObject(body)
                val userInfo = json.optJSONObject("user_info")
                if (userInfo != null) {
                    val activeCons = userInfo.optString("active_cons", "0").toIntOrNull() ?: 0
                    val maxCons = userInfo.optString("max_connections", "1").toIntOrNull() ?: 1
                    if (maxCons > 0 && activeCons >= maxCons) {
                        return@withContext Pair(true, "Portal belegt: Verbindungslimit erreicht ($activeCons von maximal $maxCons Verbindung(en) aktiv).")
                    }
                }
            }
        } catch (e: Exception) {
            // ignore network issues during check
        }
        Pair(false, null)
    }

    suspend fun fetchXtreamEpg(
        serverUrl: String,
        username: String,
        password: String,
        streamId: String,
        channelName: String = ""
    ): List<EpgProgram> = withContext(Dispatchers.IO) {
        val programs = mutableListOf<EpgProgram>()
        try {
            val baseUrl = cleanServerUrl(serverUrl)
            val url = "$baseUrl/player_api.php?username=$username&password=$password&action=get_simple_data_table&stream_id=$streamId"
            val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""

            if (body.contains("\"epg_listings\"")) {
                val json = JSONObject(body)
                val listings = json.optJSONArray("epg_listings")
                if (listings != null) {
                    for (i in 0 until listings.length()) {
                        val item = listings.optJSONObject(i) ?: continue
                        val title = item.optString("title", "").trim()
                        val desc = item.optString("description", "").trim()
                        val startSec = item.optLong("start_timestamp", 0L)
                        val endSec = item.optLong("stop_timestamp", 0L)
                        val epgId = item.optString("epg_id", streamId).ifBlank { streamId }

                        if (startSec > 0 && endSec > startSec && title.isNotBlank()) {
                            val startMs = startSec * 1000L
                            val endMs = endSec * 1000L
                            programs.add(
                                EpgProgram(
                                    id = "xtream_${streamId}_${startMs}",
                                    channelTvgId = epgId,
                                    channelName = channelName,
                                    title = title,
                                    description = desc,
                                    startTime = startMs,
                                    endTime = endMs,
                                    category = "Unterhaltung",
                                    genre = "Unterhaltung",
                                    sourceUrl = "$baseUrl/player_api.php?action=get_simple_data_table"
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Handle error safely without failing
        }
        programs
    }

    suspend fun fetchSeriesInfo(
        baseUrl: String,
        username: String,
        password: String,
        seriesId: String
    ): Result<List<ChannelItem>> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = cleanServerUrl(baseUrl)
            val url = "$cleanUrl/player_api.php?username=$username&password=$password&action=get_series_info&series_id=$seriesId"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (body.isBlank() || body == "[]" || body == "{}") {
                return@withContext Result.failure(Exception("Empty series info"))
            }

            val json = JSONObject(body)
            val info = json.optJSONObject("info")
            val seriesTitle = info?.optString("name")?.takeIf { it.isNotBlank() } ?: "Series $seriesId"
            val seriesPlot = info?.optString("plot")?.takeIf { it.isNotBlank() }
            val seriesCover = info?.optString("cover")?.takeIf { it.isNotBlank() }
            var seriesBackdrop: String? = null
            val backdropArr = info?.optJSONArray("backdrop_path")
            if (backdropArr != null && backdropArr.length() > 0) {
                seriesBackdrop = backdropArr.optString(0).takeIf { it.isNotBlank() }
            }

            val (isDach, countryCode) = ContentClassifier.detectCountryAndDach(seriesTitle, "Serien")
            val items = mutableListOf<ChannelItem>()

            // 1. Parse season mapping if 'seasons' array is provided by server
            val seasonIdToNumber = mutableMapOf<String, Int>()
            val seasonsArr = json.optJSONArray("seasons")
            if (seasonsArr != null) {
                for (s in 0 until seasonsArr.length()) {
                    val sObj = seasonsArr.optJSONObject(s) ?: continue
                    val sId = sObj.optString("id", sObj.optString("season_id", "")).trim()
                    val sNum = sObj.optInt("season_number", sObj.optInt("season", s + 1))
                    if (sId.isNotBlank()) {
                        seasonIdToNumber[sId] = sNum
                    }
                }
            }

            fun parseEpisodeObj(epObj: JSONObject, fallbackSeason: Int, fallbackIdx: Int): ChannelItem? {
                val epId = epObj.optString("id").ifBlank {
                    epObj.optString("stream_id").ifBlank {
                        epObj.optString("episode_id").ifBlank {
                            val idInt = epObj.optInt("id", epObj.optInt("stream_id", epObj.optInt("episode_id", 0)))
                            if (idInt > 0) idInt.toString() else ""
                        }
                    }
                }.trim()
                if (epId.isBlank()) return null

                // Detect season from episode object first, then fallback
                val epSeasonInObj = epObj.optInt("season", epObj.optInt("season_num", epObj.optInt("season_number", 0)))
                val finalSeason = if (epSeasonInObj > 0) epSeasonInObj else fallbackSeason

                val epNum = epObj.optInt(
                    "episode_num",
                    epObj.optInt("episode", epObj.optInt("num", epObj.optInt("episode_number", fallbackIdx + 1)))
                )
                val rawTitle = epObj.optString("title").ifBlank {
                    epObj.optString("name").ifBlank { "Episode $epNum" }
                }
                val epTitle = rawTitle.trim()

                val rawExt = epObj.optString("container_extension").ifBlank {
                    epObj.optString("extension", "mp4")
                }.trim().removePrefix(".")
                val containerExt = if (rawExt.isNotBlank()) rawExt else "mp4"

                val streamUrl = "$cleanUrl/series/$username/$password/$epId.$containerExt"
                val epInfo = epObj.optJSONObject("info")
                val epDuration = epInfo?.optInt("duration_secs", 0) ?: epObj.optInt("duration_secs", 0)
                val epPlot = epInfo?.optString("plot")?.takeIf { it.isNotBlank() }
                    ?: epObj.optString("plot")?.takeIf { it.isNotBlank() }
                    ?: seriesPlot
                val epThumb = epInfo?.optString("movie_image")?.takeIf { it.isNotBlank() }
                    ?: epObj.optString("movie_image")?.takeIf { it.isNotBlank() }
                    ?: seriesCover

                return ChannelItem(
                    id = UUID.randomUUID().toString(),
                    playlistId = "",
                    name = epTitle,
                    streamUrl = streamUrl,
                    logoUrl = epThumb,
                    groupTitle = "Serien",
                    type = ContentType.SERIES,
                    seriesTitle = seriesTitle,
                    seasonNumber = finalSeason,
                    episodeNumber = epNum,
                    plot = epPlot,
                    durationSeconds = epDuration,
                    backdropUrl = seriesBackdrop,
                    posterUrl = seriesCover,
                    isDach = isDach,
                    countryCode = countryCode,
                    providerId = seriesId
                )
            }

            val episodesObj = json.optJSONObject("episodes")
            if (episodesObj != null) {
                val seasonKeys = episodesObj.keys()
                while (seasonKeys.hasNext()) {
                    val sKey = seasonKeys.next()
                    val sNum = seasonIdToNumber[sKey]
                        ?: sKey.toIntOrNull()
                        ?: Regex("""\d+""").find(sKey)?.value?.toIntOrNull()
                        ?: 1

                    val epArr = episodesObj.optJSONArray(sKey)
                    if (epArr != null) {
                        for (i in 0 until epArr.length()) {
                            val epObj = epArr.optJSONObject(i) ?: continue
                            val parsedItem = parseEpisodeObj(epObj, sNum, i)
                            if (parsedItem != null) {
                                items.add(parsedItem)
                            }
                        }
                    } else {
                        // In case episodesObj[sKey] is a single JSONObject
                        val singleEp = episodesObj.optJSONObject(sKey)
                        if (singleEp != null) {
                            val parsedItem = parseEpisodeObj(singleEp, sNum, 0)
                            if (parsedItem != null) {
                                items.add(parsedItem)
                            }
                        }
                    }
                }
            } else {
                // Some servers return episodes as a direct JSONArray
                val epArr = json.optJSONArray("episodes")
                if (epArr != null) {
                    for (i in 0 until epArr.length()) {
                        val nestedArr = epArr.optJSONArray(i)
                        if (nestedArr != null) {
                            val sNum = i + 1
                            for (j in 0 until nestedArr.length()) {
                                val epObj = nestedArr.optJSONObject(j) ?: continue
                                val parsedItem = parseEpisodeObj(epObj, sNum, j)
                                if (parsedItem != null) {
                                    items.add(parsedItem)
                                }
                            }
                        } else {
                            val epObj = epArr.optJSONObject(i) ?: continue
                            val parsedItem = parseEpisodeObj(epObj, 1, i)
                            if (parsedItem != null) {
                                items.add(parsedItem)
                            }
                        }
                    }
                }
            }

            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
