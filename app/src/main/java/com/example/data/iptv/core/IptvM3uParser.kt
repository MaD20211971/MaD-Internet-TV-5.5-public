package com.example.data.iptv.core

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.io.StringReader
import java.util.UUID
import java.util.regex.Pattern

/**
 * Performanter und robuster M3U / M3U8 Playlist-Parser.
 * Extrahiert alle #EXTINF Attribute (tvg-id, tvg-name, group-title, tvg-logo, tvg-country etc.)
 * sowie Stream-URLs für Live-TV, Filme (VOD) und Serien.
 */
object IptvM3uParser {

    private val ATTR_REGEX = Pattern.compile("""([a-zA-Z0-9_-]+)=["']([^"']*)["']|([a-zA-Z0-9_-]+)=([^"\s,]+)""")
    private val SERIES_SEASON_EPISODE_REGEX = Pattern.compile("""(?i)(?:[sS]|Staffel\s*)(\d{1,2})\s*(?:[eE]|Folge\s*|Ep\s*|\.)(\d{1,3})""")
    private val SERIES_NUM_X_NUM_REGEX = Pattern.compile("""(?i)\b(\d{1,2})x(\d{1,3})\b""")
    private val MOVIE_YEAR_REGEX = Pattern.compile("""[\(\[\{](\d{4})[\)\]\}]""")

    /**
     * Parst eine M3U-Playlist aus einem String.
     */
    fun parse(content: String): List<IptvStreamItem> {
        return parse(BufferedReader(StringReader(content)))
    }

    /**
     * Parst eine M3U-Playlist aus einem InputStream (unterstützt GZIP automatisch).
     */
    fun parse(inputStream: InputStream): List<IptvStreamItem> {
        val buffered = java.io.BufferedInputStream(inputStream)
        buffered.mark(2)
        val b1 = buffered.read()
        val b2 = buffered.read()
        buffered.reset()
        val effectiveStream = if (b1 == 0x1f && b2 == 0x8b) {
            java.util.zip.GZIPInputStream(buffered)
        } else {
            buffered
        }
        return parse(BufferedReader(InputStreamReader(effectiveStream, Charsets.UTF_8)))
    }

    /**
     * Parst eine M3U-Playlist zeilenweise aus einem Reader.
     */
    fun parse(reader: Reader): List<IptvStreamItem> {
        val bufferedReader = if (reader is BufferedReader) reader else BufferedReader(reader)
        val rawItems = mutableListOf<IptvStreamItem>()

        var currentExtGrp: String? = null
        var currentTvgId: String? = null
        var currentTvgName: String? = null
        var currentLogo: String? = null
        var currentGroup: String? = null
        var currentTvgCountry: String? = null
        var currentChannelName: String = ""
        var currentDuration: Int = -1
        val currentAttributes = mutableMapOf<String, String>()
        var itemCounter = 0

        bufferedReader.useLines { lines ->
            for (rawLine in lines) {
                val line = rawLine.trim()
                if (line.isEmpty()) continue

                // 1. Header-Zeile #EXTM3U überspringen
                if (line.startsWith("#EXTM3U", ignoreCase = true)) {
                    continue
                }

                // 2. Info-Zeile: #EXTINF:
                if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                    currentAttributes.clear()
                    currentExtGrp = null
                    currentTvgId = null
                    currentTvgName = null
                    currentLogo = null
                    currentGroup = null
                    currentTvgCountry = null

                    val infoContent = line.substring("#EXTINF:".length).trim()
                    val commaIdx = infoContent.lastIndexOf(',')

                    val attributesPart: String
                    if (commaIdx != -1) {
                        currentChannelName = infoContent.substring(commaIdx + 1).trim()
                        attributesPart = infoContent.substring(0, commaIdx).trim()
                    } else {
                        currentChannelName = ""
                        attributesPart = infoContent
                    }

                    // Dauer ermitteln
                    val firstSpace = attributesPart.indexOf(' ')
                    val durationStr = if (firstSpace != -1) attributesPart.substring(0, firstSpace).trim() else attributesPart
                    currentDuration = durationStr.toIntOrNull() ?: -1

                    // Attribute parsen
                    val attrMatcher = ATTR_REGEX.matcher(attributesPart)
                    while (attrMatcher.find()) {
                        val key = (attrMatcher.group(1) ?: attrMatcher.group(3))?.lowercase() ?: continue
                        val value = (attrMatcher.group(2) ?: attrMatcher.group(4))?.trim() ?: continue
                        currentAttributes[key] = value

                        when (key) {
                            "tvg-id", "tvgid", "tvg_id" -> currentTvgId = value
                            "tvg-name", "tvgname", "tvg_name" -> currentTvgName = value
                            "tvg-logo", "tvglogo", "tvg_logo", "logo" -> currentLogo = value
                            "group-title", "grouptitle", "group_title", "group" -> currentGroup = value
                            "tvg-country", "country" -> currentTvgCountry = value
                        }
                    }

                    if (currentChannelName.isBlank()) {
                        currentChannelName = currentTvgName ?: "Unbekannter Kanal"
                    }
                    continue
                }

                // 3. Alternative Gruppe: #EXTGRP:
                if (line.startsWith("#EXTGRP:", ignoreCase = true)) {
                    currentExtGrp = line.substring("#EXTGRP:".length).trim()
                    continue
                }

                // 4. Steuerzeilen (VLC options etc.) überspringen
                if (line.startsWith("#")) {
                    continue
                }

                // 5. Stream-URL Zeile
                val streamUrl = line
                if (streamUrl.startsWith("http://", ignoreCase = true) ||
                    streamUrl.startsWith("https://", ignoreCase = true) ||
                    streamUrl.startsWith("rtmp://", ignoreCase = true) ||
                    streamUrl.startsWith("rtsp://", ignoreCase = true)
                ) {
                    val effectiveCategory = (currentExtGrp ?: currentGroup ?: "Allgemein").trim().ifBlank { "Allgemein" }
                    val name = currentChannelName.ifBlank { "Kanal ${itemCounter + 1}" }

                    // Klassifiziere Region
                    val tier = IptvRegionClassifier.classify(
                        rawCategory = effectiveCategory,
                        name = name,
                        tvgCountry = currentTvgCountry,
                        tvgId = currentTvgId
                    )

                    // Wenn Region null ist -> STRIKT VERWERFEN (Kategorien-Filter)
                    if (tier != null) {
                        val contentType = detectContentType(name, effectiveCategory, streamUrl)
                        val (seriesTitle, seasonNum, episodeNum) = extractSeriesMetadata(name, contentType)

                        rawItems.add(
                            IptvStreamItem(
                                id = UUID.nameUUIDFromBytes("${streamUrl}_$itemCounter".toByteArray()).toString(),
                                name = name,
                                streamUrl = streamUrl,
                                type = contentType,
                                originalCategory = effectiveCategory,
                                regionTier = tier,
                                tvgId = currentTvgId,
                                tvgName = currentTvgName,
                                logoUrl = currentLogo,
                                seriesTitle = seriesTitle,
                                seasonNumber = seasonNum,
                                episodeNumber = episodeNum,
                                durationSeconds = currentDuration,
                                sourceProvider = "M3U",
                                extraAttributes = currentAttributes.toMap(),
                                originalProviderIndex = itemCounter
                            )
                        )
                    }

                    itemCounter++
                    currentChannelName = ""
                    currentExtGrp = null
                    currentGroup = null
                    currentTvgId = null
                    currentTvgName = null
                    currentLogo = null
                    currentTvgCountry = null
                    currentAttributes.clear()
                }
            }
        }

        return rawItems
    }

    private fun detectContentType(name: String, group: String, url: String): IptvContentType {
        val lowerUrl = url.lowercase()
        val upperName = name.uppercase()
        val upperGroup = group.uppercase()

        // 1. Serien-Erkennung
        if (SERIES_SEASON_EPISODE_REGEX.matcher(name).find() ||
            SERIES_NUM_X_NUM_REGEX.matcher(name).find() ||
            upperGroup.contains("SERIE") || upperGroup.contains("SERIES") ||
            lowerUrl.contains("/series/")
        ) {
            return IptvContentType.SERIES
        }

        // 2. VOD / Movie Erkennung
        if (lowerUrl.contains("/movie/") || lowerUrl.contains("/vod/") ||
            lowerUrl.endsWith(".mp4") || lowerUrl.endsWith(".mkv") || lowerUrl.endsWith(".avi") ||
            upperGroup.contains("VOD") || upperGroup.contains("KINO") || upperGroup.contains("MOVIES") ||
            (MOVIE_YEAR_REGEX.matcher(name).find() && !upperName.contains("LIVE"))
        ) {
            return IptvContentType.VOD_MOVIE
        }

        // 3. Default: Live TV
        return IptvContentType.LIVE_TV
    }

    private fun extractSeriesMetadata(name: String, type: IptvContentType): Triple<String?, Int?, Int?> {
        if (type != IptvContentType.SERIES) return Triple(null, null, null)

        val seMatcher = SERIES_SEASON_EPISODE_REGEX.matcher(name)
        if (seMatcher.find()) {
            val season = seMatcher.group(1)?.toIntOrNull()
            val episode = seMatcher.group(2)?.toIntOrNull()
            val cleanTitle = name.substring(0, seMatcher.start()).trim().trimEnd('-', ':', '|', '/')
            return Triple(cleanTitle.ifBlank { name }, season, episode)
        }

        val nxnMatcher = SERIES_NUM_X_NUM_REGEX.matcher(name)
        if (nxnMatcher.find()) {
            val season = nxnMatcher.group(1)?.toIntOrNull()
            val episode = nxnMatcher.group(2)?.toIntOrNull()
            val cleanTitle = name.substring(0, nxnMatcher.start()).trim().trimEnd('-', ':', '|', '/')
            return Triple(cleanTitle.ifBlank { name }, season, episode)
        }

        return Triple(name, null, null)
    }
}
