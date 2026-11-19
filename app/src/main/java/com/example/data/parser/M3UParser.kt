package com.example.data.parser

import com.example.data.model.ChannelItem
import com.example.data.model.ContentType
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.io.StringReader
import java.util.UUID
import java.util.regex.Pattern

/**
 * High-performance, robust IPTV M3U / M3U8 Playlist Parser.
 *
 * Supports:
 * - Extended M3U headers (#EXTM3U with url-tvg, x-tvg-url, tvg-shift)
 * - Standard and extended #EXTINF directives with quoted and unquoted attributes
 * - #EXTGRP, #EXTVLCOPT, #EXTHTTP, and #KODIPROP tags
 * - Multi-protocol stream URLs (HTTP, HTTPS, HLS/M3U8, RTMP, RTSP, TS, MP4, MKV)
 * - Advanced categorization for Live TV, Movies (VOD), and Series (with Season/Episode extraction)
 * - German & DACH channel detection (DE, AT, CH) as well as international country identification
 * - Streaming parser support for large playlist files to avoid memory pressure
 */
object M3UParser {

    private val ATTR_REGEX = Pattern.compile("""([a-zA-Z0-9_-]+)=["']([^"']*)["']|([a-zA-Z0-9_-]+)=([^"\s,]+)""")
    private val HEADER_EPG_REGEX = Pattern.compile("""(?:url-tvg|x-tvg-url|tvg-url)=["']([^"']*)["']|(?:url-tvg|x-tvg-url|tvg-url)=([^"\s]+)""", Pattern.CASE_INSENSITIVE)
    private val HEADER_SHIFT_REGEX = Pattern.compile("""tvg-shift=["']?([^"'\s]+)["']?""", Pattern.CASE_INSENSITIVE)

    // Series Season & Episode patterns: S01E02, S1 E2, 1x02, Staffel 1 Folge 2, Season 1 Episode 2
    private val SERIES_S_E_REGEX = Pattern.compile("""(?i)(?:[sS]|Staffel\s*)(\d{1,2})\s*(?:[eE]|Folge\s*|Ep\s*|\.)(\d{1,3})""")
    private val SERIES_NUM_X_NUM_REGEX = Pattern.compile("""(?i)\b(\d{1,2})x(\d{1,3})\b""")
    private val SERIES_SEASON_WORD_REGEX = Pattern.compile("""(?i)Season\s*(\d{1,2})\s*(?:Episode|Ep)?\s*(\d{1,3})?""")

    // Movie Year pattern: e.g. "Inception (2010)" or "[2023]"
    private val MOVIE_YEAR_REGEX = Pattern.compile("""[\(\[\{](\d{4})[\)\]\}]""")

    data class M3UHeader(
        val epgUrls: List<String> = emptyList(),
        val tvgShift: String? = null
    )

    data class M3UParseResult(
        val header: M3UHeader,
        val channels: List<ChannelItem>,
        val totalCount: Int = channels.size
    )

    /**
     * Parses an M3U playlist string and returns a list of [ChannelItem]s.
     */
    fun parse(content: String, playlistId: String, syncOnlyGerman: Boolean = false): List<ChannelItem> {
        return parseDetailed(content, playlistId, syncOnlyGerman).channels
    }

    /**
     * Parses an M3U playlist from an [InputStream] (streamed line-by-line).
     * Automatically detects and decompresses GZIP streams if needed.
     */
    fun parse(inputStream: InputStream, playlistId: String, syncOnlyGerman: Boolean = false): List<ChannelItem> {
        val buffered = java.io.BufferedInputStream(inputStream)
        buffered.mark(2)
        val b1 = buffered.read()
        val b2 = buffered.read()
        buffered.reset()
        val decompressedStream = if (b1 == 0x1f && b2 == 0x8b) {
            java.util.zip.GZIPInputStream(buffered)
        } else {
            buffered
        }
        val effectiveStream = KoolToCatalogHelper.wrapInputStreamIfJson(decompressedStream)
        return parseDetailed(BufferedReader(InputStreamReader(effectiveStream, Charsets.UTF_8)), playlistId, syncOnlyGerman).channels
    }

    /**
     * Parses an M3U playlist from a [Reader].
     */
    fun parse(reader: Reader, playlistId: String, syncOnlyGerman: Boolean = false): List<ChannelItem> {
        val bufferedReader = if (reader is BufferedReader) reader else BufferedReader(reader)
        return parseDetailed(bufferedReader, playlistId, syncOnlyGerman).channels
    }

    /**
     * Parses an M3U playlist and returns detailed results including header metadata and channels.
     */
    fun parseDetailed(content: String, playlistId: String, syncOnlyGerman: Boolean = false): M3UParseResult {
        val trimmed = content.trimStart()
        val effectiveContent = if (trimmed.startsWith("[") || (trimmed.startsWith("{") && trimmed.contains("\"country\""))) {
            KoolToCatalogHelper.convertJsonCatalogToM3u(trimmed)
        } else {
            content
        }
        return parseDetailed(BufferedReader(StringReader(effectiveContent)), playlistId, syncOnlyGerman)
    }

    /**
     * Parses an M3U playlist from any [Reader] and returns detailed results.
     */
    fun parseDetailed(reader: Reader, playlistId: String, syncOnlyGerman: Boolean = false): M3UParseResult {
        val bufferedReader = if (reader is BufferedReader) reader else BufferedReader(reader)
        return parseDetailedStreamed(bufferedReader, playlistId, syncOnlyGerman)
    }

    /**
     * Primary streamed parser method.
     */
    private fun parseDetailedStreamed(reader: BufferedReader, playlistId: String, syncOnlyGerman: Boolean = false): M3UParseResult {
        val channels = mutableListOf<ChannelItem>()
        var header = M3UHeader()

        var currentExtInf: String? = null
        var currentExtGrp: String? = null
        var currentTvgId: String? = null
        var currentTvgName: String? = null
        var currentLogo: String? = null
        var currentGroup: String? = null
        var currentTvgCountry: String? = null
        var currentTvgLanguage: String? = null
        var currentTvgType: String? = null
        var currentRadio: Boolean = false
        var currentCatchup: String? = null
        var currentCatchupDays: String? = null
        var currentCatchupSource: String? = null
        var currentChannelName: String = ""
        var currentDuration: Int = -1
        val currentHeaders = mutableMapOf<String, String>()

        reader.useLines { lines ->
            for (rawLine in lines) {
                val line = rawLine.trim()
                if (line.isEmpty()) continue

                // 1. Header line: #EXTM3U
                if (line.startsWith("#EXTM3U", ignoreCase = true)) {
                    val epgUrls = mutableListOf<String>()
                    val epgMatcher = HEADER_EPG_REGEX.matcher(line)
                    while (epgMatcher.find()) {
                        val urlStr = epgMatcher.group(1) ?: epgMatcher.group(2)
                        if (!urlStr.isNullOrBlank()) {
                            urlStr.split(',', ';').map { it.trim() }.filter { it.isNotBlank() }.forEach {
                                epgUrls.add(it)
                            }
                        }
                    }

                    val shiftMatcher = HEADER_SHIFT_REGEX.matcher(line)
                    val shift = if (shiftMatcher.find()) shiftMatcher.group(1) else null

                    header = M3UHeader(epgUrls = epgUrls, tvgShift = shift)
                    continue
                }

                // 2. Channel info line: #EXTINF:
                if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                    currentExtInf = line
                    currentHeaders.clear()

                    val parsedInf = parseExtInfAttributes(line)
                    currentTvgId = parsedInf.tvgId
                    currentTvgName = parsedInf.tvgName
                    currentLogo = parsedInf.logoUrl
                    currentGroup = parsedInf.groupTitle
                    currentTvgCountry = parsedInf.tvgCountry
                    currentTvgLanguage = parsedInf.tvgLanguage
                    currentTvgType = parsedInf.tvgType
                    currentRadio = parsedInf.radio
                    currentCatchup = parsedInf.catchup
                    currentCatchupDays = parsedInf.catchupDays
                    currentCatchupSource = parsedInf.catchupSource
                    currentDuration = parsedInf.duration
                    currentChannelName = if (parsedInf.channelName.isNotBlank()) {
                        parsedInf.channelName
                    } else {
                        currentTvgName ?: "Unbekannter Sender"
                    }
                    continue
                }

                // 3. Alternative Group Line: #EXTGRP:
                if (line.startsWith("#EXTGRP:", ignoreCase = true)) {
                    currentExtGrp = line.substring("#EXTGRP:".length).trim()
                    continue
                }

                // 4. VLC / Kodi / HTTP header directives
                if (line.startsWith("#EXTVLCOPT:", ignoreCase = true)) {
                    val opt = line.substring("#EXTVLCOPT:".length).trim()
                    val eqIdx = opt.indexOf('=')
                    if (eqIdx != -1) {
                        val k = opt.substring(0, eqIdx).trim()
                        val v = opt.substring(eqIdx + 1).trim()
                        currentHeaders[k] = v
                    }
                    continue
                }

                if (line.startsWith("#KODIPROP:", ignoreCase = true)) {
                    val prop = line.substring("#KODIPROP:".length).trim()
                    val eqIdx = prop.indexOf('=')
                    if (eqIdx != -1) {
                        val k = prop.substring(0, eqIdx).trim()
                        val v = prop.substring(eqIdx + 1).trim()
                        currentHeaders[k] = v
                    }
                    continue
                }

                // 5. Stream URL Line
                if (!line.startsWith("#") && (isStreamUrlCandidate(line) || currentExtInf != null)) {
                    try {
                        val streamUrl = line
                        val finalGroup = currentGroup ?: currentExtGrp ?: ""
                        val displayName = currentChannelName.ifBlank { currentTvgName ?: "Kanal ${channels.size + 1}" }

                        val classification = ContentClassifier.classify(
                            name = displayName,
                            group = finalGroup,
                            streamUrl = streamUrl,
                            tvgType = if (currentRadio) "radio" else currentTvgType,
                            tvgCountry = currentTvgCountry
                        )

                        // If German-only synchronization is requested, skip non-German / foreign content immediately
                        if (syncOnlyGerman && !ContentClassifier.isGermanOrDachContent(
                                name = displayName,
                                group = finalGroup,
                                countryCode = classification.countryCode,
                                isDach = classification.isDach,
                                contentType = classification.type
                            )
                        ) {
                            continue
                        }

                        val resolution = detectResolution(displayName, finalGroup)
                        val effectiveGroup = if (finalGroup.isNotBlank()) finalGroup else if (classification.groupTitle.isNotBlank()) classification.groupTitle else formatGroupTitle(finalGroup, classification.isDach, classification.type)

                        val countryResult = com.example.data.country.CountryResolver.resolve(
                            title = displayName,
                            groupTitle = finalGroup,
                            tvgCountry = currentTvgCountry,
                            tvgId = currentTvgId,
                            tvgName = currentTvgName,
                            language = currentTvgLanguage,
                            streamUrl = streamUrl,
                            providerType = com.example.data.country.ProviderType.M3U
                        )

                        val parsedCategory = com.example.data.category.ProviderCategoryEngine.parseCategory(finalGroup.ifBlank { effectiveGroup })
                        val isDachResolved = countryResult.isDach || classification.isDach || parsedCategory.normalizedCountry in listOf("DE", "AT", "CH")
                        val effectiveCountryCode = if (countryResult.countryCode !in setOf("INT", "UNKNOWN", "OTHER", "CONFLICT")) {
                            countryResult.countryCode
                        } else if (parsedCategory.normalizedCountry != "OTHER") {
                            parsedCategory.normalizedCountry
                        } else if (classification.countryCode !in setOf("INT", "UNKNOWN", "OTHER")) {
                            classification.countryCode
                        } else {
                            countryResult.countryCode
                        }

                        val channel = ChannelItem(
                            id = UUID.randomUUID().toString(),
                            playlistId = playlistId,
                            name = displayName,
                            streamUrl = streamUrl,
                            logoUrl = currentLogo,
                            groupTitle = effectiveGroup,
                            type = classification.type,
                            tvgId = currentTvgId ?: generateTvgId(displayName),
                            tvgName = currentTvgName ?: displayName,
                            isDach = isDachResolved,
                            countryCode = effectiveCountryCode,
                            seriesTitle = classification.seriesTitle,
                            seasonNumber = classification.season,
                            episodeNumber = classification.episode,
                            durationSeconds = if (currentDuration > 0) currentDuration else 0,
                            resolutionBadge = resolution,
                            is24_7 = classification.is24_7,
                            isVip = classification.isVip,
                            countryConfidence = countryResult.confidence,
                            classificationStatus = countryResult.status.name,
                            countryEvidenceJson = countryResult.evidenceDescriptions.joinToString("; "),
                            normalizedChannelId = countryResult.normalizedChannelId,
                            normalizedTitle = com.example.data.country.CountryNormalizer.cleanText(displayName),
                            sourceId = playlistId,
                            category = parsedCategory.subCategory,
                            genre = parsedCategory.normalizedGenre.name,
                            providerCategory = finalGroup.ifBlank { effectiveGroup },
                            parentCategory = parsedCategory.parentCategory,
                            subCategory = parsedCategory.subCategory,
                            providerSortOrder = channels.size,
                            providerCountry = parsedCategory.normalizedCountry,
                            tvgCountry = currentTvgCountry,
                            tvgLanguage = currentTvgLanguage,
                            lastUpdated = System.currentTimeMillis()
                        )
                        if (!ContentClassifier.isHeaderOrDividerChannel(displayName, streamUrl, effectiveGroup)) {
                            channels.add(channel)
                        }
                    } catch (itemErr: Throwable) {
                        // Skip corrupted M3U item quietly to protect whole playlist load
                    } finally {
                        currentExtInf = null
                        currentExtGrp = null
                        currentTvgId = null
                        currentTvgName = null
                        currentLogo = null
                        currentGroup = null
                        currentTvgCountry = null
                        currentTvgLanguage = null
                        currentTvgType = null
                        currentRadio = false
                        currentCatchup = null
                        currentCatchupDays = null
                        currentCatchupSource = null
                        currentChannelName = ""
                        currentDuration = -1
                        currentHeaders.clear()
                    }
                }
            }
        }

        return M3UParseResult(header = header, channels = channels)
    }

    /**
     * High-performance prioritized streaming parser for IPTV playlists.
     * Prioritizes DE:, AT:, CH:, and ADULT channels during sync so that the UI can display
     * them and allow immediate playback without waiting for tens of thousands of international
     * channels or VOD items.
     *
     * Phase 1: Emits DE/AT/CH/ADULT channels via [onDachChannelsReady] as soon as extracted.
     * Phase 2: Emits remaining channels in chunks via [onBackgroundChunk] with coroutine yield.
     */
    suspend fun parseWithPriority(
        inputStream: InputStream,
        playlistId: String,
        syncOnlyGerman: Boolean = false,
        allowedCountries: Set<String> = if (syncOnlyGerman) setOf("DE", "AT", "CH", "ADULT") else emptySet(),
        includeVodAndSeries: Boolean = true,
        onCategoriesDiscovered: (suspend (List<com.example.data.model.ProviderCategoryEntity>) -> Unit)? = null,
        onDachChannelsReady: suspend (List<ChannelItem>) -> Unit,
        onBackgroundChunk: suspend (List<ChannelItem>) -> Unit
    ): M3UParseResult {
        val buffered = java.io.BufferedInputStream(inputStream, 65536)
        buffered.mark(2)
        val b1 = buffered.read()
        val b2 = buffered.read()
        buffered.reset()
        val decompressedStream = if (b1 == 0x1f && b2 == 0x8b) {
            java.util.zip.GZIPInputStream(buffered, 65536)
        } else {
            buffered
        }
        val effectiveStream = KoolToCatalogHelper.wrapInputStreamIfJson(decompressedStream)
        return parseDetailedStreamedWithPriority(
            BufferedReader(InputStreamReader(effectiveStream, Charsets.UTF_8), 65536),
            playlistId,
            syncOnlyGerman,
            allowedCountries,
            includeVodAndSeries,
            onCategoriesDiscovered,
            onDachChannelsReady,
            onBackgroundChunk
        )
    }

    /**
     * Internal implementation of prioritized streaming M3U parser.
     */
    suspend fun parseDetailedStreamedWithPriority(
        reader: BufferedReader,
        playlistId: String,
        syncOnlyGerman: Boolean = false,
        allowedCountries: Set<String> = if (syncOnlyGerman) setOf("DE", "AT", "CH", "ADULT") else emptySet(),
        includeVodAndSeries: Boolean = true,
        onCategoriesDiscovered: (suspend (List<com.example.data.model.ProviderCategoryEntity>) -> Unit)? = null,
        onDachChannelsReady: suspend (List<ChannelItem>) -> Unit,
        onBackgroundChunk: suspend (List<ChannelItem>) -> Unit
    ): M3UParseResult {
        val allChannels = mutableListOf<ChannelItem>()
        val dachChannels = mutableListOf<ChannelItem>()
        val nonDachBuffer = mutableListOf<ChannelItem>()
        var totalItemsCount = 0
        var streamSortIndex = 0
        var header = M3UHeader()
        var hasEmittedDach = false
        val discoveredGroups = LinkedHashMap<String, Int>()

        var currentExtInf: String? = null
        var currentExtGrp: String? = null
        var currentTvgId: String? = null
        var currentTvgName: String? = null
        var currentLogo: String? = null
        var currentGroup: String? = null
        var currentTvgCountry: String? = null
        var currentTvgLanguage: String? = null
        var currentTvgType: String? = null
        var currentRadio: Boolean = false
        var currentCatchup: String? = null
        var currentCatchupDays: String? = null
        var currentCatchupSource: String? = null
        var currentChannelName: String = ""
        var currentDuration: Int = -1
        val currentHeaders = mutableMapOf<String, String>()

        val effectiveAllowed = if (allowedCountries.isNotEmpty()) {
            allowedCountries.map { it.uppercase() }.toSet()
        } else if (syncOnlyGerman) {
            setOf("DE", "AT", "CH", "ADULT")
        } else {
            emptySet()
        }

        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val l = line?.trim() ?: continue
            if (l.isEmpty()) continue

            if (l.startsWith("#EXTM3U", ignoreCase = true)) {
                header = parseHeader(l)
                continue
            }

            if (l.startsWith("#EXTINF:", ignoreCase = true)) {
                currentExtInf = l
                currentHeaders.clear()
                val parsedInf = parseExtInfAttributes(l)
                currentTvgId = parsedInf.tvgId
                currentTvgName = parsedInf.tvgName
                currentLogo = parsedInf.logoUrl
                currentGroup = parsedInf.groupTitle
                currentTvgCountry = parsedInf.tvgCountry
                currentTvgLanguage = parsedInf.tvgLanguage
                currentTvgType = parsedInf.tvgType
                currentRadio = parsedInf.radio
                currentCatchup = parsedInf.catchup
                currentCatchupDays = parsedInf.catchupDays
                currentCatchupSource = parsedInf.catchupSource
                currentDuration = parsedInf.duration

                currentChannelName = if (parsedInf.channelName.isNotBlank()) {
                    parsedInf.channelName
                } else {
                    currentTvgName ?: "Unbekannter Sender"
                }
                continue
            }

            if (l.startsWith("#EXTGRP:", ignoreCase = true)) {
                currentExtGrp = l.substring("#EXTGRP:".length).trim()
                continue
            }

            if (l.startsWith("#EXTVLCOPT:", ignoreCase = true)) {
                val opt = l.substring("#EXTVLCOPT:".length).trim()
                val eqIdx = opt.indexOf('=')
                if (eqIdx != -1) {
                    val k = opt.substring(0, eqIdx).trim()
                    val v = opt.substring(eqIdx + 1).trim()
                    currentHeaders[k] = v
                }
                continue
            }

            if (l.startsWith("#KODIPROP:", ignoreCase = true)) {
                val prop = l.substring("#KODIPROP:".length).trim()
                val eqIdx = prop.indexOf('=')
                if (eqIdx != -1) {
                    val k = prop.substring(0, eqIdx).trim()
                    val v = prop.substring(eqIdx + 1).trim()
                    currentHeaders[k] = v
                }
                continue
            }

            if (!l.startsWith("#") && (isStreamUrlCandidate(l) || currentExtInf != null)) {
                try {
                    val streamUrl = l
                    val finalGroup = currentGroup ?: currentExtGrp ?: ""
                    val displayName = currentChannelName.ifBlank { currentTvgName ?: "Kanal ${allChannels.size + 1}" }
                    streamSortIndex++

                    if (finalGroup.isNotBlank() && discoveredGroups.size < 4000) {
                        discoveredGroups[finalGroup] = (discoveredGroups[finalGroup] ?: 0) + 1
                    }

                    // Fast O(1) pre-filter BEFORE expensive ContentClassifier / CountryResolver regexes on 250,000+ M3U playlists
                    if (effectiveAllowed.isNotEmpty()) {
                        val (fastDach, fastCountry) = ContentClassifier.detectCountryAndDach("", finalGroup, currentTvgCountry)
                        val wantsDach = effectiveAllowed.any { it in setOf("DE", "AT", "CH") }
                        val wantsAdult = "ADULT" in effectiveAllowed
                        val isAdultFast = wantsAdult && ContentClassifier.isAdultGroup(finalGroup)
                        val hasDachTvgId = wantsDach && !currentTvgId.isNullOrBlank() && (
                            currentTvgId.endsWith(".de", ignoreCase = true) ||
                            currentTvgId.endsWith(".at", ignoreCase = true) ||
                            currentTvgId.endsWith(".ch", ignoreCase = true)
                        )
                        val matchesFast = (fastDach && wantsDach) ||
                            isAdultFast ||
                            hasDachTvgId ||
                            fastCountry in effectiveAllowed ||
                            (!currentTvgCountry.isNullOrBlank() && currentTvgCountry.uppercase() in effectiveAllowed) ||
                            ContentClassifier.isCategoryMatchingCountries(finalGroup, effectiveAllowed) ||
                            (wantsDach && ContentClassifier.isGermanOrDachVodItem(displayName, finalGroup)) ||
                            (wantsDach && com.example.data.country.DachMasterDatabase.findMatch(displayName, currentTvgName, currentTvgId).isMatch)

                        if (!matchesFast) {
                            continue
                        }
                    }

                    val classification = ContentClassifier.classify(
                        name = displayName,
                        group = finalGroup,
                        streamUrl = streamUrl,
                        tvgType = if (currentRadio) "radio" else currentTvgType,
                        tvgCountry = currentTvgCountry
                    )

                    if (!includeVodAndSeries && (classification.type == ContentType.MOVIE || classification.type == ContentType.SERIES)) {
                        continue
                    }

                    val isVodOrSeries = classification.type == ContentType.MOVIE || classification.type == ContentType.SERIES
                    if (effectiveAllowed.isNotEmpty() && isVodOrSeries) {
                        val isDachRequested = effectiveAllowed.any { it in setOf("DE", "AT", "CH") }
                        val isOnlyPriority = effectiveAllowed.all { it in setOf("DE", "AT", "CH", "ADULT") }
                        val isAdultItem = ContentClassifier.isAdultGroup(finalGroup)
                        if (isOnlyPriority && isDachRequested && !isAdultItem && !ContentClassifier.isGermanOrDachVodItem(displayName, finalGroup)) {
                            continue
                        }
                    }

                    val resolution = detectResolution(displayName, finalGroup)
                    val effectiveGroup = if (finalGroup.isNotBlank()) finalGroup else if (classification.groupTitle.isNotBlank()) classification.groupTitle else formatGroupTitle(finalGroup, classification.isDach, classification.type)

                    val parsedCategory = com.example.data.category.ProviderCategoryEngine.parseCategory(finalGroup.ifBlank { effectiveGroup })
                    val groupCountryCheck = ContentClassifier.detectCountryForGroup(finalGroup)
                    val isExplicitForeignGroup = (groupCountryCheck !in setOf("OTHER", "SPORT", "VIP", "UNKNOWN", "DE", "AT", "CH")) ||
                        finalGroup.contains("International", ignoreCase = true)

                    val countryResult = com.example.data.country.CountryResolver.resolve(
                        title = displayName,
                        groupTitle = finalGroup,
                        tvgCountry = currentTvgCountry,
                        tvgId = currentTvgId,
                        tvgName = currentTvgName,
                        language = currentTvgLanguage,
                        streamUrl = streamUrl,
                        providerType = com.example.data.country.ProviderType.M3U,
                        ignoreChannelName = isExplicitForeignGroup
                    )

                    val isDachResolved = countryResult.isDach || classification.isDach || parsedCategory.normalizedCountry in listOf("DE", "AT", "CH")
                    val effectiveCountryCode = if (!isExplicitForeignGroup && countryResult.countryCode in setOf("DE", "AT", "CH")) {
                        countryResult.countryCode
                    } else if (countryResult.countryCode !in setOf("INT", "UNKNOWN", "OTHER", "CONFLICT")) {
                        countryResult.countryCode
                    } else if (parsedCategory.normalizedCountry != "OTHER") {
                        parsedCategory.normalizedCountry
                    } else if (classification.countryCode !in setOf("INT", "UNKNOWN", "OTHER")) {
                        classification.countryCode
                    } else {
                        "OTHER"
                    }

                    val normalizedCountry = com.example.data.country.CountryConstants.normalizeIndexCountryCode(effectiveCountryCode)
                    if (effectiveAllowed.isNotEmpty()) {
                        val isAdultMatched = "ADULT" in effectiveAllowed && (parsedCategory.countryCode == "ADULT" || ContentClassifier.isAdultGroup(finalGroup))
                        val matchesCountry = normalizedCountry in effectiveAllowed ||
                            effectiveCountryCode.uppercase() in effectiveAllowed ||
                            parsedCategory.countryCode in effectiveAllowed ||
                            isAdultMatched ||
                            (isDachResolved && effectiveAllowed.any { it in setOf("DE", "AT", "CH") }) ||
                            (isVodOrSeries && effectiveAllowed.any { it in setOf("DE", "AT", "CH") } && ContentClassifier.isGermanOrDachVodItem(displayName, finalGroup))
                        if (!matchesCountry) {
                            continue
                        }
                    }

                    val finalCountry = if (parsedCategory.countryCode == "ADULT" || ContentClassifier.isAdultGroup(finalGroup)) "ADULT" else normalizedCountry

                    val channel = ChannelItem(
                        id = UUID.randomUUID().toString(),
                        playlistId = playlistId,
                        name = displayName,
                        streamUrl = streamUrl,
                        logoUrl = currentLogo,
                        groupTitle = effectiveGroup,
                        type = classification.type,
                        tvgId = currentTvgId ?: generateTvgId(displayName),
                        tvgName = currentTvgName ?: displayName,
                        isDach = isDachResolved,
                        countryCode = finalCountry,
                        seriesTitle = classification.seriesTitle,
                        seasonNumber = classification.season,
                        episodeNumber = classification.episode,
                        durationSeconds = if (currentDuration > 0) currentDuration else 0,
                        resolutionBadge = resolution,
                        is24_7 = classification.is24_7,
                        isVip = classification.isVip,
                        countryConfidence = countryResult.confidence,
                        classificationStatus = countryResult.status.name,
                        countryEvidenceJson = countryResult.evidenceDescriptions.joinToString("; "),
                        normalizedChannelId = countryResult.normalizedChannelId,
                        normalizedTitle = com.example.data.country.CountryNormalizer.cleanText(displayName),
                        sourceId = playlistId,
                        category = parsedCategory.subCategory,
                        genre = parsedCategory.normalizedGenre.name,
                        providerCategory = finalGroup.ifBlank { effectiveGroup },
                        parentCategory = parsedCategory.parentCategory,
                        subCategory = parsedCategory.subCategory,
                        providerSortOrder = streamSortIndex,
                        providerCountry = parsedCategory.normalizedCountry,
                        tvgCountry = currentTvgCountry,
                        tvgLanguage = currentTvgLanguage,
                        lastUpdated = System.currentTimeMillis()
                    )

                    if (!ContentClassifier.isHeaderOrDividerChannel(displayName, streamUrl, effectiveGroup)) {
                        totalItemsCount++
                        if (allChannels.size < 5000) {
                            allChannels.add(channel)
                        }
                        if ((isDachResolved || finalCountry == "ADULT") && !isVodOrSeries) {
                            dachChannels.add(channel)
                        } else {
                            nonDachBuffer.add(channel)
                        }

                        // Flush DACH channels as soon as we have collected a solid initial set (>= 60)
                        if (!hasEmittedDach && dachChannels.isNotEmpty() && (nonDachBuffer.size >= 80 || dachChannels.size >= 120)) {
                            onDachChannelsReady(dachChannels.toList())
                            hasEmittedDach = true
                        }

                        // Flush background chunks of 2500 items to minimize DB transactions
                        if (hasEmittedDach && nonDachBuffer.size >= 2500) {
                            onBackgroundChunk(nonDachBuffer.toList())
                            nonDachBuffer.clear()
                            kotlinx.coroutines.yield()
                        }
                    }
                } catch (itemErr: Throwable) {
                    // Skip corrupted item quietly to protect whole playlist load
                } finally {
                    currentExtInf = null
                    currentExtGrp = null
                    currentTvgId = null
                    currentTvgName = null
                    currentLogo = null
                    currentGroup = null
                    currentTvgCountry = null
                    currentTvgLanguage = null
                    currentTvgType = null
                    currentRadio = false
                    currentCatchup = null
                    currentCatchupDays = null
                    currentCatchupSource = null
                    currentChannelName = ""
                    currentDuration = -1
                    currentHeaders.clear()
                }
            }
        }

        // Final flushes
        if (!hasEmittedDach) {
            onDachChannelsReady(dachChannels.toList())
            hasEmittedDach = true
        }
        if (nonDachBuffer.isNotEmpty()) {
            onBackgroundChunk(nonDachBuffer.toList())
            nonDachBuffer.clear()
        }

        if (onCategoriesDiscovered != null && discoveredGroups.isNotEmpty()) {
            val entities = discoveredGroups.entries.mapIndexed { idx, (groupName, count) ->
                val parsed = com.example.data.category.ProviderCategoryEngine.parseCategory(groupName)
                val contentType = when {
                    groupName.contains("SERIE", ignoreCase = true) || groupName.contains("SERIES", ignoreCase = true) -> ContentType.SERIES
                    groupName.contains("VOD", ignoreCase = true) || groupName.contains("FILM", ignoreCase = true) || groupName.contains("MOVIE", ignoreCase = true) || groupName.contains("KINO", ignoreCase = true) -> ContentType.MOVIE
                    else -> ContentType.LIVE_TV
                }
                val isSynced = effectiveAllowed.isEmpty() || parsed.countryCode in effectiveAllowed
                com.example.data.model.ProviderCategoryEntity(
                    id = "${playlistId}_M3U_${idx}_${groupName.hashCode()}",
                    playlistId = playlistId,
                    categoryId = idx.toString(),
                    rawCategoryName = groupName,
                    parentCategory = parsed.parentCategory,
                    subCategory = parsed.subCategory,
                    countryCode = parsed.countryCode,
                    contentType = contentType,
                    sortOrder = idx,
                    itemCount = count,
                    isSynced = isSynced
                )
            }
            onCategoriesDiscovered(entities)
        }

        return M3UParseResult(
            header = header,
            channels = if (totalItemsCount > 5000) dachChannels else allChannels,
            totalCount = totalItemsCount
        )
    }

    private fun isStreamUrlCandidate(line: String): Boolean {
        return line.startsWith("http://", ignoreCase = true) ||
                line.startsWith("https://", ignoreCase = true) ||
                line.startsWith("rtmp://", ignoreCase = true) ||
                line.startsWith("rtmps://", ignoreCase = true) ||
                line.startsWith("rtsp://", ignoreCase = true) ||
                line.startsWith("udp://", ignoreCase = true) ||
                line.startsWith("mms://", ignoreCase = true) ||
                line.startsWith("file://", ignoreCase = true) ||
                line.startsWith("/") ||
                line.endsWith(".m3u8", ignoreCase = true) ||
                line.endsWith(".ts", ignoreCase = true) ||
                line.endsWith(".mp4", ignoreCase = true) ||
                line.endsWith(".mkv", ignoreCase = true) ||
                line.endsWith(".avi", ignoreCase = true)
    }

    private fun detectContentTypeAndSeries(
        name: String,
        group: String,
        url: String,
        tvgType: String?,
        isRadio: Boolean
    ): ContentDetectionResult {
        val classification = ContentClassifier.classify(
            name = name,
            group = group,
            streamUrl = url,
            tvgType = if (isRadio) "radio" else tvgType
        )
        return ContentDetectionResult(
            type = classification.type,
            seriesTitle = classification.seriesTitle,
            season = classification.season,
            episode = classification.episode
        )
    }

    private fun extractSeriesMetadata(name: String): Triple<String?, Int?, Int?> {
        val m1 = SERIES_S_E_REGEX.matcher(name)
        if (m1.find()) {
            val season = m1.group(1)?.toIntOrNull() ?: 1
            val episode = m1.group(2)?.toIntOrNull() ?: 1
            val title = name.substring(0, m1.start()).trim().removeSuffix("-").removeSuffix(":").removeSuffix("|").trim()
            return Triple(title.ifBlank { null }, season, episode)
        }

        val m2 = SERIES_NUM_X_NUM_REGEX.matcher(name)
        if (m2.find()) {
            val season = m2.group(1)?.toIntOrNull() ?: 1
            val episode = m2.group(2)?.toIntOrNull() ?: 1
            val title = name.substring(0, m2.start()).trim().removeSuffix("-").removeSuffix(":").removeSuffix("|").trim()
            return Triple(title.ifBlank { null }, season, episode)
        }

        val m3 = SERIES_SEASON_WORD_REGEX.matcher(name)
        if (m3.find()) {
            val season = m3.group(1)?.toIntOrNull() ?: 1
            val episode = m3.group(2)?.toIntOrNull() ?: 1
            val title = name.substring(0, m3.start()).trim().removeSuffix("-").removeSuffix(":").removeSuffix("|").trim()
            return Triple(title.ifBlank { null }, season, episode)
        }

        return Triple(null, null, null)
    }

    private fun isDachContent(name: String, group: String, tvgCountry: String?): Boolean {
        if (tvgCountry != null) {
            val c = tvgCountry.uppercase().trim()
            if (c == "DE" || c == "AT" || c == "CH" || c == "GER" || c == "AUT" || c == "SUI") return true
        }

        val nameU = name.uppercase().trim()
        val groupU = group.uppercase().trim()
        val check = "$nameU $groupU"

        return check.contains("DEUTSCH") || check.contains("GERMANY") || check.contains("GERMAN") ||
                check.contains("ÖSTERREICH") || check.contains("AUSTRIA") || check.contains("SCHWEIZ") ||
                check.contains("SWITZERLAND") || check.contains("SWISS") || check.contains("DACH") ||
                nameU.startsWith("DE:") || nameU.startsWith("DE |") || nameU.startsWith("DE-") || nameU.startsWith("[DE]") || nameU.startsWith("(DE)") ||
                groupU.startsWith("DE:") || groupU.startsWith("DE |") || groupU.startsWith("DE-") || groupU.startsWith("[DE]") || groupU.startsWith("(DE)") ||
                nameU.startsWith("AT:") || nameU.startsWith("AT |") || nameU.startsWith("AT-") || nameU.startsWith("[AT]") || nameU.startsWith("(AT)") ||
                groupU.startsWith("AT:") || groupU.startsWith("AT |") || groupU.startsWith("AT-") || groupU.startsWith("[AT]") || groupU.startsWith("(AT)") ||
                nameU.startsWith("CH:") || nameU.startsWith("CH |") || nameU.startsWith("CH-") || nameU.startsWith("[CH]") || nameU.startsWith("(CH)") ||
                groupU.startsWith("CH:") || groupU.startsWith("CH |") || groupU.startsWith("CH-") || groupU.startsWith("[CH]") || groupU.startsWith("(CH)") ||
                check.contains(" DE:") || check.contains(" AT:") || check.contains(" CH:") ||
                check.contains(" DE |") || check.contains(" AT |") || check.contains(" CH |") ||
                check.contains("ARD") || check.contains("DAS ERSTE") || check.contains("ZDF") || check.contains("RTL") ||
                check.contains("PRO7") || check.contains("PROSIEBEN") || check.contains("SAT.1") ||
                check.contains("SAT1") || check.contains("VOX") || check.contains("KABEL EINS") ||
                check.contains("ORF") || check.contains("SRF") || check.contains("TAGESSCHAU") ||
                check.contains("WELT") || check.contains("NTV") || check.contains("N-TV") || check.contains("3SAT") ||
                check.contains("KINO HIGHLIGHTS")
    }

    private fun detectCountry(name: String, group: String, tvgCountry: String?): String {
        if (!tvgCountry.isNullOrBlank()) {
            val c = tvgCountry.uppercase().trim()
            when (c) {
                "DE", "GER", "GERMANY" -> return "DE"
                "AT", "AUT", "AUSTRIA" -> return "AT"
                "CH", "SUI", "SWITZERLAND" -> return "CH"
                "GB", "UK", "UNITED KINGDOM" -> return "UK"
                "US", "USA", "UNITED STATES" -> return "US"
                "TR", "TUR", "TURKEY" -> return "TR"
                "IT", "ITA", "ITALY" -> return "IT"
                "FR", "FRA", "FRANCE" -> return "FR"
                "ES", "ESP", "SPAIN" -> return "ES"
                else -> if (c.length <= 3) return c
            }
        }

        val nameU = name.uppercase().trim()
        val groupU = group.uppercase().trim()
        val check = "$nameU $groupU"

        return when {
            nameU.startsWith("AT:") || nameU.startsWith("AT |") || nameU.startsWith("AT-") || nameU.startsWith("[AT]") || nameU.startsWith("(AT)") ||
                    groupU.startsWith("AT:") || groupU.startsWith("AT |") || groupU.startsWith("AT-") || groupU.startsWith("[AT]") || groupU.startsWith("(AT)") ||
                    check.contains(" AT:") || check.contains(" AT |") || check.contains("ÖSTERREICH") || check.contains("AUSTRIA") || check.contains("ORF") || check.contains("SERVUS TV AT") -> "AT"

            nameU.startsWith("CH:") || nameU.startsWith("CH |") || nameU.startsWith("CH-") || nameU.startsWith("[CH]") || nameU.startsWith("(CH)") ||
                    groupU.startsWith("CH:") || groupU.startsWith("CH |") || groupU.startsWith("CH-") || groupU.startsWith("[CH]") || groupU.startsWith("(CH)") ||
                    check.contains(" CH:") || check.contains(" CH |") || check.contains("SCHWEIZ") || check.contains("SWITZERLAND") || check.contains("SWISS") || check.contains("SRF") -> "CH"

            nameU.startsWith("DE:") || nameU.startsWith("DE |") || nameU.startsWith("DE-") || nameU.startsWith("[DE]") || nameU.startsWith("(DE)") ||
                    groupU.startsWith("DE:") || groupU.startsWith("DE |") || groupU.startsWith("DE-") || groupU.startsWith("[DE]") || groupU.startsWith("(DE)") ||
                    check.contains(" DE:") || check.contains(" DE |") || isDachContent(name, group, null) -> "DE"

            check.contains("TR:") || check.contains("[TR]") || check.contains("TURK") || check.contains("TÜRKIYE") -> "TR"
            check.contains("UK:") || check.contains("[UK]") || check.contains("ENGLAND") || check.contains("BRITISH") -> "UK"
            check.contains("US:") || check.contains("[US]") || check.contains("USA") || check.contains("AMERICA") -> "US"
            check.contains("IT:") || check.contains("[IT]") || check.contains("ITALIA") || check.contains("ITALY") -> "IT"
            check.contains("FR:") || check.contains("[FR]") || check.contains("FRANCE") || check.contains("FRENCH") -> "FR"
            check.contains("ES:") || check.contains("[ES]") || check.contains("SPAIN") || check.contains("ESPAÑA") -> "ES"
            check.contains("PL:") || check.contains("[PL]") || check.contains("POLAND") || check.contains("POLSKA") -> "PL"
            check.contains("NL:") || check.contains("[NL]") || check.contains("NETHERLANDS") || check.contains("NEDERLAND") -> "NL"
            else -> "INT"
        }
    }

    private fun detectResolution(name: String, group: String): String {
        fun matchesRes(s: String): String? {
            if (s.isEmpty()) return null
            if (s.contains("4K", ignoreCase = true) || s.contains("UHD", ignoreCase = true) || s.contains("2160")) return "4K UHD"
            if (s.contains("FHD", ignoreCase = true) || s.contains("1080")) return "FHD"
            if (s.contains("HD", ignoreCase = true) || s.contains("720")) return "HD"
            if (s.contains("SD", ignoreCase = true) || s.contains("576") || s.contains("480")) return "SD"
            if (s.contains("HEVC", ignoreCase = true) || s.contains("H265", ignoreCase = true) || s.contains("H.265", ignoreCase = true)) return "HEVC"
            return null
        }
        return matchesRes(name) ?: matchesRes(group) ?: "HD"
    }

    private fun detectGroupFromContext(name: String, url: String): String {
        return if (isDachContent(name, "", null)) "DEUTSCH" else "Allgemein"
    }

    private fun formatGroupTitle(group: String, isDach: Boolean, type: ContentType): String {
        var cleanGroup = group.trim()
        if (cleanGroup.isEmpty()) {
            cleanGroup = when (type) {
                ContentType.LIVE_TV -> if (isDach) "DEUTSCH | Live TV" else "International | Live TV"
                ContentType.RADIO -> if (isDach) "DEUTSCH | Radio" else "International | Radio"
                ContentType.MOVIE -> if (isDach) "DEUTSCH | Filme" else "International | Filme"
                ContentType.SERIES -> if (isDach) "DEUTSCH | Serien" else "International | Serien"
                else -> if (isDach) "DEUTSCH | Allgemein" else "International | Allgemein"
            }
        }
        return cleanGroup
    }

    private fun generateTvgId(name: String): String {
        if (name.isEmpty()) return ""
        val sb = java.lang.StringBuilder(name.length)
        var lastDot = false
        for (i in 0 until name.length) {
            val ch = name[i].lowercaseChar()
            if ((ch in 'a'..'z') || (ch in '0'..'9')) {
                sb.append(ch)
                lastDot = false
            } else if (!lastDot && sb.isNotEmpty()) {
                sb.append('.')
                lastDot = true
            }
        }
        while (sb.isNotEmpty() && sb[sb.length - 1] == '.') {
            sb.deleteCharAt(sb.length - 1)
        }
        return sb.toString()
    }

    data class ExtInfSplit(
        val attributesPart: String,
        val channelName: String,
        val duration: Int
    )

    fun splitExtInfLine(line: String): ExtInfSplit {
        val prefixLen = if (line.startsWith("#EXTINF:", ignoreCase = true)) 8 else 0
        var i = prefixLen
        val len = line.length
        while (i < len && line[i].isWhitespace()) i++

        val durStart = i
        if (i < len && (line[i] == '-' || line[i] == '+')) i++
        while (i < len && line[i].isDigit()) i++
        val durationStr = if (durStart < i) line.substring(durStart, i) else ""
        val duration = durationStr.toIntOrNull() ?: -1

        var inDoubleQuote = false
        var inSingleQuote = false
        var separatorCommaIdx = -1

        val scanStart = i
        var ptr = scanStart
        while (ptr < len) {
            val c = line[ptr]
            if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote
            } else if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote
            } else if (c == ',' && !inDoubleQuote && !inSingleQuote) {
                separatorCommaIdx = ptr
                break
            }
            ptr++
        }

        return if (separatorCommaIdx != -1) {
            val attrs = line.substring(prefixLen, separatorCommaIdx).trim()
            val title = line.substring(separatorCommaIdx + 1).trim()
            ExtInfSplit(attrs, title, duration)
        } else {
            val remaining = line.substring(prefixLen).trim()
            val lastComma = remaining.lastIndexOf(',')
            if (lastComma != -1) {
                ExtInfSplit(remaining.substring(0, lastComma).trim(), remaining.substring(lastComma + 1).trim(), duration)
            } else {
                ExtInfSplit(remaining, "", duration)
            }
        }
    }

    inline fun parseAttributesFast(attributesPart: String, onAttr: (key: String, value: String) -> Unit) {
        var i = 0
        val len = attributesPart.length
        while (i < len) {
            while (i < len && attributesPart[i].isWhitespace()) i++
            if (i >= len) break

            val keyStart = i
            while (i < len && attributesPart[i] != '=' && !attributesPart[i].isWhitespace()) i++
            val key = attributesPart.substring(keyStart, i).trim().lowercase()

            while (i < len && attributesPart[i].isWhitespace()) i++
            if (i >= len || attributesPart[i] != '=') continue
            i++ // skip '='

            while (i < len && attributesPart[i].isWhitespace()) i++
            if (i >= len) break

            val value: String
            if (attributesPart[i] == '"') {
                i++ // skip quote
                val valStart = i
                while (i < len && attributesPart[i] != '"') i++
                value = attributesPart.substring(valStart, i)
                if (i < len && attributesPart[i] == '"') i++
            } else if (attributesPart[i] == '\'') {
                i++ // skip quote
                val valStart = i
                while (i < len && attributesPart[i] != '\'') i++
                value = attributesPart.substring(valStart, i)
                if (i < len && attributesPart[i] == '\'') i++
            } else {
                val valStart = i
                while (i < len && !attributesPart[i].isWhitespace() && attributesPart[i] != ',') i++
                value = attributesPart.substring(valStart, i)
            }

            if (key.isNotEmpty()) {
                onAttr(key, value.trim())
            }
        }
    }

    private data class ParsedExtInf(
        val tvgId: String? = null,
        val tvgName: String? = null,
        val logoUrl: String? = null,
        val groupTitle: String? = null,
        val tvgCountry: String? = null,
        val tvgLanguage: String? = null,
        val tvgType: String? = null,
        val radio: Boolean = false,
        val catchup: String? = null,
        val catchupDays: String? = null,
        val catchupSource: String? = null,
        val duration: Int = -1,
        val channelName: String = ""
    )

    private fun parseHeader(line: String): M3UHeader {
        val epgUrls = mutableListOf<String>()
        val epgMatcher = HEADER_EPG_REGEX.matcher(line)
        while (epgMatcher.find()) {
            val urlStr = epgMatcher.group(1) ?: epgMatcher.group(2)
            if (!urlStr.isNullOrBlank()) {
                urlStr.split(',', ';').map { it.trim() }.filter { it.isNotBlank() }.forEach {
                    epgUrls.add(it)
                }
            }
        }
        val shiftMatcher = HEADER_SHIFT_REGEX.matcher(line)
        val shift = if (shiftMatcher.find()) shiftMatcher.group(1) else null
        return M3UHeader(epgUrls = epgUrls, tvgShift = shift)
    }

    private fun parseExtInfAttributes(line: String): ParsedExtInf {
        val split = splitExtInfLine(line)
        var tvgId: String? = null
        var tvgName: String? = null
        var logoUrl: String? = null
        var groupTitle: String? = null
        var tvgCountry: String? = null
        var tvgLanguage: String? = null
        var tvgType: String? = null
        var radio = false
        var catchup: String? = null
        var catchupDays: String? = null
        var catchupSource: String? = null

        parseAttributesFast(split.attributesPart) { key, value ->
            when (key) {
                "tvg-id", "tvgid", "tvg_id" -> tvgId = value
                "tvg-name", "tvgname", "tvg_name" -> tvgName = value
                "tvg-logo", "tvglogo", "tvg_logo", "logo" -> logoUrl = value
                "group-title", "grouptitle", "group_title", "group" -> groupTitle = value
                "tvg-country", "country" -> tvgCountry = value
                "tvg-language", "language" -> tvgLanguage = value
                "tvg-type", "type" -> tvgType = value
                "radio" -> radio = value.equals("true", ignoreCase = true) || value == "1"
                "catchup" -> catchup = value
                "catchup-days", "timeshift" -> catchupDays = value
                "catchup-source" -> catchupSource = value
            }
        }

        return ParsedExtInf(
            tvgId = tvgId,
            tvgName = tvgName,
            logoUrl = logoUrl,
            groupTitle = groupTitle,
            tvgCountry = tvgCountry,
            tvgLanguage = tvgLanguage,
            tvgType = tvgType,
            radio = radio,
            catchup = catchup,
            catchupDays = catchupDays,
            catchupSource = catchupSource,
            duration = split.duration,
            channelName = split.channelName
        )
    }

    data class ContentDetectionResult(
        val type: ContentType,
        val seriesTitle: String?,
        val season: Int?,
        val episode: Int?
    )
}
