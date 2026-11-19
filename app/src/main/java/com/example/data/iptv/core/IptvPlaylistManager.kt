package com.example.data.iptv.core

import com.example.data.model.ChannelItem
import com.example.data.model.ContentType
import java.io.InputStream
import java.io.Reader

/**
 * Zentraler IPTV Playlist Manager & Ingestion Core.
 *
 * Führt Daten aus M3U, Xtream Codes und MAC-Portalen zusammen, filtert sie strikt nach den 6 erlaubten
 * Regionen (DE, AT, CH, US, UK, ADULT) und ordnet sie in der exakten Sortier-Hierarchie an:
 * 1. Deutschland (DE)
 * 2. Österreich (AT)
 * 3. Schweiz (CH)
 * 4. USA (US)
 * 5. Großbritannien (UK)
 * 6. Adult (Erotik direkt nach UK)
 *
 * Innerhalb jedes Regionen-Blocks bleibt die originale Kanalstruktur und Kategorien-Reihenfolge
 * des IPTV-Providers 100% erhalten (Stable Sort).
 */
object IptvPlaylistManager {

    /**
     * Importiert und vereinheitlicht M3U/M3U8 Playlist-Inhalte aus einem String.
     */
    fun importFromM3u(content: String): IptvImportResult {
        val parsedItems = IptvM3uParser.parse(content)
        return processAndSortItems(parsedItems)
    }

    /**
     * Importiert und vereinheitlicht M3U/M3U8 Playlist-Inhalte aus einem InputStream.
     */
    fun importFromM3u(inputStream: InputStream): IptvImportResult {
        val parsedItems = IptvM3uParser.parse(inputStream)
        return processAndSortItems(parsedItems)
    }

    /**
     * Importiert und vereinheitlicht M3U/M3U8 Playlist-Inhalte aus einem Reader.
     */
    fun importFromM3u(reader: Reader): IptvImportResult {
        val parsedItems = IptvM3uParser.parse(reader)
        return processAndSortItems(parsedItems)
    }

    /**
     * Importiert und vereinheitlicht Xtream Codes API Antworten für Live, VOD und Serien.
     */
    fun importFromXtream(
        baseUrl: String,
        username: String,
        password: String,
        liveStreamsJson: String? = null,
        liveCategoriesJson: String? = null,
        vodStreamsJson: String? = null,
        vodCategoriesJson: String? = null,
        seriesJson: String? = null,
        seriesCategoriesJson: String? = null
    ): IptvImportResult {
        val allRaw = mutableListOf<IptvStreamItem>()
        var runningIndex = 0

        // 1. Live TV
        if (!liveStreamsJson.isNullOrBlank()) {
            val liveCats = if (!liveCategoriesJson.isNullOrBlank()) IptvXtreamParser.parseCategories(liveCategoriesJson) else emptyMap()
            val liveItems = IptvXtreamParser.parseLiveStreams(liveStreamsJson, baseUrl, username, password, liveCats, runningIndex)
            allRaw.addAll(liveItems)
            runningIndex += liveItems.size
        }

        // 2. VOD Filme
        if (!vodStreamsJson.isNullOrBlank()) {
            val vodCats = if (!vodCategoriesJson.isNullOrBlank()) IptvXtreamParser.parseCategories(vodCategoriesJson) else emptyMap()
            val vodItems = IptvXtreamParser.parseVodStreams(vodStreamsJson, baseUrl, username, password, vodCats, runningIndex)
            allRaw.addAll(vodItems)
            runningIndex += vodItems.size
        }

        // 3. Serien
        if (!seriesJson.isNullOrBlank()) {
            val seriesCats = if (!seriesCategoriesJson.isNullOrBlank()) IptvXtreamParser.parseCategories(seriesCategoriesJson) else emptyMap()
            val seriesItems = IptvXtreamParser.parseSeries(seriesJson, baseUrl, username, password, seriesCats, runningIndex)
            allRaw.addAll(seriesItems)
            runningIndex += seriesItems.size
        }

        return processAndSortItems(allRaw)
    }

    /**
     * Importiert und vereinheitlicht MAC-basierte Portal-Antworten (Stalker Middleware).
     */
    fun importFromMacPortal(
        baseUrl: String,
        itvChannelsJson: String? = null,
        itvGenresJson: String? = null,
        vodRecordsJson: String? = null,
        vodCategoriesJson: String? = null,
        seriesJson: String? = null,
        seriesCategoriesJson: String? = null
    ): IptvImportResult {
        val allRaw = mutableListOf<IptvStreamItem>()
        var runningIndex = 0

        // 1. Live Kanäle
        if (!itvChannelsJson.isNullOrBlank()) {
            val itvCats = if (!itvGenresJson.isNullOrBlank()) IptvMacPortalParser.parseCategories(itvGenresJson) else emptyMap()
            val liveItems = IptvMacPortalParser.parseLiveChannels(itvChannelsJson, baseUrl, itvCats, runningIndex)
            allRaw.addAll(liveItems)
            runningIndex += liveItems.size
        }

        // 2. VOD Filme
        if (!vodRecordsJson.isNullOrBlank()) {
            val vodCats = if (!vodCategoriesJson.isNullOrBlank()) IptvMacPortalParser.parseCategories(vodCategoriesJson) else emptyMap()
            val vodItems = IptvMacPortalParser.parseVodRecords(vodRecordsJson, baseUrl, vodCats, runningIndex)
            allRaw.addAll(vodItems)
            runningIndex += vodItems.size
        }

        // 3. Serien
        if (!seriesJson.isNullOrBlank()) {
            val seriesCats = if (!seriesCategoriesJson.isNullOrBlank()) IptvMacPortalParser.parseCategories(seriesCategoriesJson) else emptyMap()
            val seriesItems = IptvMacPortalParser.parseSeries(seriesJson, baseUrl, seriesCats, runningIndex)
            allRaw.addAll(seriesItems)
            runningIndex += seriesItems.size
        }

        return processAndSortItems(allRaw)
    }

    /**
     * Kern-Verarbeitungs- und Sortier-Algorithmus:
     * Wendet die strikte Sortier-Hierarchie an:
     * DE -> AT -> CH -> US -> UK -> ADULT
     * Behält aber die originale relative Reihenfolge der Provider-Kategorien und Items stabil bei.
     */
    fun processAndSortItems(items: List<IptvStreamItem>): IptvImportResult {
        // Zähle Elemente nach Regionen-Tier
        val countsByTier = mutableMapOf<IptvRegionTier, Int>()
        IptvRegionTier.ORDERED_TIERS.forEach { countsByTier[it] = 0 }

        for (item in items) {
            countsByTier[item.regionTier] = (countsByTier[item.regionTier] ?: 0) + 1
        }

        // 1. Sortiere die Elemente nach Tier-Priorität (1..6) und originalem Provider-Index (Stabile Sortierung)
        val sortedAll = items.sortedWith(
            compareBy<IptvStreamItem> { it.regionTier.priority }
                .thenBy { it.originalProviderIndex }
        )

        // 2. Aufteilung nach Medientyp (Live TV, VOD Filme, Serien) unter Beibehaltung der Hierarchie
        val sortedLive = sortedAll.filter { it.type == IptvContentType.LIVE_TV }
        val sortedVod = sortedAll.filter { it.type == IptvContentType.VOD_MOVIE }
        val sortedSeries = sortedAll.filter { it.type == IptvContentType.SERIES }

        // 3. Erstellung der geordneten Kategorie-Gruppen
        // Kategorien werden nach ihrer ersten Erscheinung innerhalb des jeweiligen Tiers geordnet.
        val categoryGroups = buildOrderedCategoryGroups(sortedAll)

        return IptvImportResult(
            allItems = sortedAll,
            liveItems = sortedLive,
            vodItems = sortedVod,
            seriesItems = sortedSeries,
            categoryGroups = categoryGroups,
            totalImported = sortedAll.size,
            totalDiscarded = 0, // Gefilterte Elemente wurden bereits in den Parsern verworfen
            countsByTier = countsByTier
        )
    }

    /**
     * Erstellt geordnete Kategorie-Gruppen.
     * Strikte Reihenfolge: DE Kategorien -> AT Kategorien -> CH Kategorien -> US Kategorien -> UK Kategorien -> Adult Kategorien.
     * Innerhalb jedes Tiers bleibt die originale relative Reihenfolge der Provider-Kategorien erhalten.
     */
    fun buildOrderedCategoryGroups(items: List<IptvStreamItem>): List<IptvCategoryGroup> {
        val groups = mutableListOf<IptvCategoryGroup>()

        for (tier in IptvRegionTier.ORDERED_TIERS) {
            val tierItems = items.filter { it.regionTier == tier }
            if (tierItems.isEmpty()) continue

            // Gruppiere nach Medientyp und originalem Kategorienamen
            // Verwende LinkedHashMap zur Erhaltung der Einfügereihenfolge des Providers
            val typeMap = linkedMapOf<Pair<IptvContentType, String>, MutableList<IptvStreamItem>>()

            for (item in tierItems) {
                val key = Pair(item.type, item.originalCategory)
                val list = typeMap.getOrPut(key) { mutableListOf() }
                list.add(item)
            }

            for ((key, groupItems) in typeMap) {
                groups.add(
                    IptvCategoryGroup(
                        regionTier = tier,
                        originalCategoryName = key.second,
                        type = key.first,
                        items = groupItems
                    )
                )
            }
        }

        return groups
    }

    /**
     * Konvertiert ein [IptvStreamItem] in das app-interne [ChannelItem] Modell für nahtlose UI-Integration.
     */
    fun toChannelItem(item: IptvStreamItem, playlistId: String): ChannelItem {
        val appContentType = when (item.type) {
            IptvContentType.LIVE_TV -> ContentType.LIVE_TV
            IptvContentType.VOD_MOVIE -> ContentType.MOVIE
            IptvContentType.SERIES -> ContentType.SERIES
        }

        val isDach = item.regionTier in listOf(IptvRegionTier.DE, IptvRegionTier.AT, IptvRegionTier.CH)

        return ChannelItem(
            id = item.id,
            playlistId = playlistId,
            name = item.name,
            streamUrl = item.streamUrl,
            logoUrl = item.logoUrl,
            groupTitle = item.originalCategory,
            tvgId = item.tvgId,
            tvgName = item.tvgName,
            countryCode = item.regionTier.code,
            isDach = isDach,
            type = appContentType,
            seriesTitle = item.seriesTitle,
            seasonNumber = item.seasonNumber,
            episodeNumber = item.episodeNumber
        )
    }

    /**
     * Konvertiert eine Liste von [IptvStreamItem]s in eine Liste von [ChannelItem]s.
     */
    fun toChannelItems(items: List<IptvStreamItem>, playlistId: String): List<ChannelItem> {
        return items.map { toChannelItem(it, playlistId) }
    }
}
