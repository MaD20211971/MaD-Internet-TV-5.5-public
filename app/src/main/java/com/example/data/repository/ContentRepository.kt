package com.example.data.repository

import com.example.data.local.ChannelDao
import com.example.data.model.ChannelItem
import com.example.data.model.ContentType
import com.example.data.model.CountrySummary
import com.example.util.PerformanceMonitor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Repository layer adhering to clean architecture.
 * Mediates between database, in-memory cache, and view models.
 * Completely isolates the UI from database queries and storage representations.
 */
class ContentRepository(
    private val channelDao: ChannelDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val memoryCache = CountryMemoryCache()
    private val summaryCache = ConcurrentHashMap<String, CountrySummary>()
    private val prefetchScope = CoroutineScope(Dispatchers.IO)

    /**
     * Retrieves lightweight Country Summary (counts for DE, AT, CH, OTHER, UNKNOWN, CONFLICT).
     * O(1) indexed SQL count query.
     */
    suspend fun getCountrySummary(
        playlistId: String? = null,
        forceRefresh: Boolean = false
    ): CountrySummary = withContext(ioDispatcher) {
        val cacheKey = playlistId ?: "ALL"
        if (!forceRefresh) {
            summaryCache[cacheKey]?.let { return@withContext it }
        }

        PerformanceMonitor.measureSuspend("countrySummaryTime") {
            val counts = if (playlistId != null) {
                channelDao.getCountryCountsByPlaylist(playlistId)
            } else {
                channelDao.getCountryCounts()
            }
            val summary = CountrySummary.fromCounts(counts)
            summaryCache[cacheKey] = summary
            summary
        }
    }

    /**
     * Targeted Country query with pagination.
     * Checks memory cache first. If absent, executes indexed database query without full scan.
     */
    suspend fun getByCountry(
        countryCode: String,
        playlistId: String? = null,
        type: ContentType? = null,
        groupTitle: String? = null,
        hiddenIds: Set<String> = emptySet(),
        page: Int = 0,
        pageSize: Int = 50000
    ): List<ChannelItem> = withContext(ioDispatcher) {
        val upperCode = countryCode.trim().uppercase()
        val effectiveGroup = if (groupTitle.isNullOrBlank() || groupTitle.equals("ALLE", ignoreCase = true)) null else groupTitle
        val cacheKey = CountryMemoryCache.CacheKey(
            countryCode = upperCode,
            playlistId = playlistId,
            type = type,
            groupTitle = effectiveGroup,
            page = page
        )

        // Memory cache check
        if (hiddenIds.isEmpty()) {
            memoryCache.get(cacheKey)?.let { return@withContext it }
        }

        PerformanceMonitor.measureSuspend("countryQueryTime") {
            val offset = page * pageSize
            val hiddenList = hiddenIds.toList()
            val results = if (upperCode == "ALLE" || upperCode == "ALL" || upperCode.isBlank()) {
                channelDao.getChannelsPaged(
                    countryCode = null,
                    playlistId = playlistId,
                    type = type,
                    groupTitle = effectiveGroup,
                    hiddenIds = hiddenList,
                    limit = pageSize,
                    offset = offset
                )
            } else if (upperCode == "OTHER") {
                // Fetch other countries
                val deAtChUnknown = listOf("DE", "AT", "CH", "UNKNOWN", "CONFLICT")
                val allPaged = channelDao.getChannelsPaged(
                    countryCode = null,
                    playlistId = playlistId,
                    type = type,
                    groupTitle = effectiveGroup,
                    hiddenIds = hiddenList,
                    limit = pageSize * 2,
                    offset = offset
                )
                allPaged.filter { it.countryCode.uppercase() !in deAtChUnknown }.take(pageSize)
            } else {
                channelDao.getChannelsByCountryPaged(
                    countryCode = upperCode,
                    playlistId = playlistId,
                    type = type,
                    groupTitle = effectiveGroup,
                    hiddenIds = hiddenList,
                    limit = pageSize,
                    offset = offset
                )
            }

            if (hiddenIds.isEmpty()) {
                memoryCache.put(cacheKey, results)
            }
            results
        }
    }

    /**
     * Database-driven search across titles, normalized titles, and categories.
     * Uses FTS4 for sub-50ms execution on massive databases, with fallback to indexed search.
     */
    suspend fun search(
        query: String,
        countryCode: String? = null,
        playlistId: String? = null,
        type: ContentType? = null,
        hiddenIds: Set<String> = emptySet(),
        page: Int = 0,
        pageSize: Int = 50000
    ): List<ChannelItem> = withContext(ioDispatcher) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        PerformanceMonitor.measureSuspend("searchTime") {
            val offset = page * pageSize
            val effectiveCountry = if (countryCode.isNullOrBlank() || countryCode.equals("ALLE", ignoreCase = true) || countryCode.equals("OTHER", ignoreCase = true)) null else countryCode.uppercase()

            // Try lightning fast FTS4 prefix search first (< 10ms for 100k+ catalogs)
            if (offset == 0 && effectiveCountry == null && playlistId == null && type == null) {
                try {
                    val sanitizedQuery = trimmed.replace(Regex("""[^\p{L}\p{Nd}\s]"""), "").trim()
                    if (sanitizedQuery.isNotBlank()) {
                        val ftsQuery = sanitizedQuery.split(Regex("""\s+""")).joinToString(" ") { "$it*" }
                        val ftsResults = channelDao.searchChannelsFts(ftsQuery, limit = pageSize, offset = 0)
                        if (ftsResults.isNotEmpty()) {
                            val hiddenList = hiddenIds
                            return@measureSuspend if (hiddenList.isEmpty()) ftsResults else ftsResults.filter { it.id !in hiddenList }
                        }
                    }
                } catch (_: Throwable) {}
            }

            channelDao.searchChannelsPaged(
                query = trimmed,
                countryCode = effectiveCountry,
                playlistId = playlistId,
                type = type,
                hiddenIds = hiddenIds.toList(),
                limit = pageSize,
                offset = offset
            )
        }
    }

    /**
     * Paging 3 Flow provider for zero-overhead lazy loading in Compose.
     */
    fun getPagingFlow(
        countryCode: String? = null,
        playlistId: String? = null,
        type: ContentType? = null,
        groupTitle: String? = null,
        pageSize: Int = 50
    ): kotlinx.coroutines.flow.Flow<androidx.paging.PagingData<ChannelItem>> {
        val effectiveGroup = if (groupTitle.isNullOrBlank() || groupTitle.equals("ALLE", ignoreCase = true)) null else groupTitle
        val upperCode = countryCode?.trim()?.uppercase()
        return androidx.paging.Pager(
            config = androidx.paging.PagingConfig(
                pageSize = pageSize,
                prefetchDistance = 15,
                enablePlaceholders = false,
                initialLoadSize = pageSize * 2
            )
        ) {
            if (upperCode.isNullOrBlank() || upperCode == "ALLE" || upperCode == "ALL") {
                channelDao.getChannelsPagingSource(null, playlistId, type, effectiveGroup)
            } else {
                channelDao.getChannelsByCountryPagingSource(upperCode, playlistId, type, effectiveGroup)
            }
        }.flow
    }

    /**
     * Retrieves category groups for a specific country using DB index.
     */
    suspend fun getGroupsByCountry(
        countryCode: String,
        playlistId: String? = null,
        type: ContentType? = null
    ): List<String> = withContext(ioDispatcher) {
        PerformanceMonitor.measureSuspend("categoryQueryTime") {
            val upper = countryCode.trim().uppercase()
            if (upper == "ALLE" || upper == "ALL" || upper.isBlank()) {
                emptyList()
            } else {
                channelDao.getGroupsByCountry(
                    countryCode = upper,
                    playlistId = playlistId,
                    type = type
                )
            }
        }
    }

    /**
     * Retrieves favorites with optional country filter.
     */
    suspend fun getFavorites(
        countryCode: String? = null,
        playlistId: String? = null,
        hiddenIds: Set<String> = emptySet(),
        page: Int = 0,
        pageSize: Int = 50000
    ): List<ChannelItem> = withContext(ioDispatcher) {
        val effectiveCountry = if (countryCode.isNullOrBlank() || countryCode.equals("ALLE", ignoreCase = true)) null else countryCode.uppercase()
        val offset = page * pageSize
        channelDao.getFavoritesPaged(
            countryCode = effectiveCountry,
            playlistId = playlistId,
            hiddenIds = hiddenIds.toList(),
            limit = pageSize,
            offset = offset
        )
    }

    /**
     * Asynchronously prefetches page 0 for LIVE, MOVIE, and SERIES for the selected country.
     * UI never waits for this.
     */
    fun prefetchCountry(countryCode: String, playlistId: String? = null) {
        val upper = countryCode.trim().uppercase()
        if (upper.isBlank()) return
        prefetchScope.launch {
            try {
                listOf(ContentType.LIVE_TV, ContentType.MOVIE, ContentType.SERIES).forEach { ct ->
                    val key = CountryMemoryCache.CacheKey(
                        countryCode = upper,
                        playlistId = playlistId,
                        type = ct,
                        groupTitle = null,
                        page = 0
                    )
                    if (memoryCache.get(key) == null) {
                        val channels = channelDao.getChannelsByCountryPaged(
                            countryCode = upper,
                            playlistId = playlistId,
                            type = ct,
                            groupTitle = null,
                            hiddenIds = emptyList(),
                            limit = 50,
                            offset = 0
                        )
                        memoryCache.put(key, channels)
                    }
                }
            } catch (ignored: Throwable) {}
        }
    }

    /**
     * Clears all memory caches when playlists are updated, imported or modified.
     */
    fun invalidateCache() {
        memoryCache.invalidate()
        summaryCache.clear()
    }
}
