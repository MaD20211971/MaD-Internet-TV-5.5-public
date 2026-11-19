package com.example.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.local.entity.ChannelFts
import com.example.data.model.ChannelItem
import com.example.data.model.ContentType
import com.example.data.model.ContentTypeCount
import com.example.data.model.CountryCount
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Query("SELECT COUNT(*) FROM channels WHERE playlistId = :playlistId")
    suspend fun getChannelCountByPlaylist(playlistId: String): Int

    @Query("SELECT COUNT(*) FROM channels")
    suspend fun getTotalChannelCount(): Int

    // Fast Summary Aggregate Queries (O(1) index scans for Country Selector)
    @Query("SELECT countryCode, COUNT(*) as count FROM channels GROUP BY countryCode")
    suspend fun getCountryCounts(): List<CountryCount>

    @Query("SELECT countryCode, COUNT(*) as count FROM channels WHERE playlistId = :playlistId GROUP BY countryCode")
    suspend fun getCountryCountsByPlaylist(playlistId: String): List<CountryCount>

    @Query("SELECT type, COUNT(*) as count FROM channels WHERE (:playlistId IS NULL OR playlistId = :playlistId) GROUP BY type")
    suspend fun getTypeCounts(playlistId: String?): List<ContentTypeCount>

    @Query("SELECT COUNT(*) FROM channels WHERE (:playlistId IS NULL OR playlistId = :playlistId) AND isFavorite = 1")
    suspend fun getFavoriteCount(playlistId: String?): Int

    // Indexed Targeted Country Queries with Pagination (Avoids full scans)
    @Query("""
        SELECT * FROM channels 
        WHERE (:playlistId IS NULL OR playlistId = :playlistId)
          AND countryCode = :countryCode
          AND (:type IS NULL OR type = :type)
          AND (:groupTitle IS NULL OR groupTitle = :groupTitle)
          AND id NOT IN (:hiddenIds)
        ORDER BY name ASC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getChannelsByCountryPaged(
        countryCode: String,
        playlistId: String?,
        type: ContentType?,
        groupTitle: String?,
        hiddenIds: List<String>,
        limit: Int,
        offset: Int
    ): List<ChannelItem>

    @Query("""
        SELECT * FROM channels 
        WHERE (:playlistId IS NULL OR playlistId = :playlistId)
          AND (:countryCode IS NULL OR countryCode = :countryCode)
          AND (:type IS NULL OR type = :type)
          AND (:groupTitle IS NULL OR groupTitle = :groupTitle)
          AND id NOT IN (:hiddenIds)
        ORDER BY isDach DESC, name ASC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getChannelsPaged(
        countryCode: String?,
        playlistId: String?,
        type: ContentType?,
        groupTitle: String?,
        hiddenIds: List<String>,
        limit: Int,
        offset: Int
    ): List<ChannelItem>

    @Query("""
        SELECT COUNT(*) FROM channels 
        WHERE (:playlistId IS NULL OR playlistId = :playlistId)
          AND countryCode = :countryCode
          AND (:type IS NULL OR type = :type)
          AND (:groupTitle IS NULL OR groupTitle = :groupTitle)
          AND id NOT IN (:hiddenIds)
    """)
    suspend fun getChannelCountForCountry(
        countryCode: String,
        playlistId: String?,
        type: ContentType?,
        groupTitle: String?,
        hiddenIds: List<String>
    ): Int

    // Paging 3 Indexed Data Sources (Zero UI-thread blocking for 100k+ catalogs)
    @Query("""
        SELECT * FROM channels 
        WHERE (:playlistId IS NULL OR playlistId = :playlistId)
          AND (:countryCode IS NULL OR countryCode = :countryCode)
          AND (:type IS NULL OR type = :type)
          AND (:groupTitle IS NULL OR groupTitle = :groupTitle)
        ORDER BY isDach DESC, name ASC
    """)
    fun getChannelsPagingSource(
        countryCode: String?,
        playlistId: String?,
        type: ContentType?,
        groupTitle: String?
    ): PagingSource<Int, ChannelItem>

    @Query("""
        SELECT * FROM channels 
        WHERE (:playlistId IS NULL OR playlistId = :playlistId)
          AND countryCode = :countryCode
          AND (:type IS NULL OR type = :type)
          AND (:groupTitle IS NULL OR groupTitle = :groupTitle)
        ORDER BY name ASC
    """)
    fun getChannelsByCountryPagingSource(
        countryCode: String,
        playlistId: String?,
        type: ContentType?,
        groupTitle: String?
    ): PagingSource<Int, ChannelItem>

    @Query("""
        SELECT * FROM channels 
        WHERE (:playlistId IS NULL OR playlistId = :playlistId)
          AND isFavorite = 1
        ORDER BY name ASC
    """)
    fun getFavoriteChannelsPagingSource(playlistId: String?): PagingSource<Int, ChannelItem>

    // Full-Text Search (FTS4) for sub-50ms search on massive catalogs
    @Insert
    suspend fun insertFtsRaw(entries: List<ChannelFts>)

    @Query("""
        SELECT c.* FROM channels c
        JOIN channels_fts ON c.id = channels_fts.channelId
        WHERE channels_fts MATCH :query
        ORDER BY c.isDach DESC, c.name ASC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun searchChannelsFts(query: String, limit: Int = 100, offset: Int = 0): List<ChannelItem>

    @Query("DELETE FROM channels_fts")
    suspend fun clearFts()

    // Indexed Search Query with Pagination (Fallback when FTS query syntax is not applicable)
    @Query("""
        SELECT * FROM channels 
        WHERE (:playlistId IS NULL OR playlistId = :playlistId)
          AND (:countryCode IS NULL OR countryCode = :countryCode)
          AND (:type IS NULL OR type = :type)
          AND id NOT IN (:hiddenIds)
          AND (name LIKE '%' || :query || '%' OR normalizedTitle LIKE '%' || :query || '%' OR groupTitle LIKE '%' || :query || '%')
        ORDER BY name ASC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun searchChannelsPaged(
        query: String,
        countryCode: String?,
        playlistId: String?,
        type: ContentType?,
        hiddenIds: List<String>,
        limit: Int,
        offset: Int
    ): List<ChannelItem>

    // Indexed Category Groups per Country
    @Query("""
        SELECT DISTINCT groupTitle FROM channels 
        WHERE (:playlistId IS NULL OR playlistId = :playlistId)
          AND countryCode = :countryCode
          AND (:type IS NULL OR type = :type)
        ORDER BY groupTitle ASC
    """)
    suspend fun getGroupsByCountry(
        countryCode: String,
        playlistId: String?,
        type: ContentType?
    ): List<String>

    // Favorites with Optional Country Filter & Pagination
    @Query("""
        SELECT * FROM channels 
        WHERE (:playlistId IS NULL OR playlistId = :playlistId)
          AND (:countryCode IS NULL OR countryCode = :countryCode)
          AND isFavorite = 1
          AND id NOT IN (:hiddenIds)
        ORDER BY name ASC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getFavoritesPaged(
        countryCode: String?,
        playlistId: String?,
        hiddenIds: List<String>,
        limit: Int,
        offset: Int
    ): List<ChannelItem>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND (isDach = 1 OR countryCode IN ('DE', 'AT', 'CH')) ORDER BY isDach DESC, name ASC")
    fun getDachChannelsByPlaylist(playlistId: String): Flow<List<ChannelItem>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND (isDach = 1 OR countryCode IN (:countryCodes)) ORDER BY isDach DESC, name ASC")
    fun getChannelsByCountriesAndPlaylist(playlistId: String, countryCodes: List<String>): Flow<List<ChannelItem>>

    @Query("SELECT COUNT(*) FROM channels WHERE playlistId = :playlistId AND countryCode = :countryCode")
    suspend fun getCountryChannelCountByPlaylist(playlistId: String, countryCode: String): Int

    @Query("SELECT COUNT(*) FROM channels WHERE playlistId = :playlistId AND (isDach = 1 OR countryCode IN ('DE', 'AT', 'CH'))")
    suspend fun getDachChannelCount(playlistId: String): Int

    @Query("SELECT COUNT(*) FROM channels WHERE playlistId = :playlistId AND (isDach = 1 OR countryCode IN ('DE', 'AT', 'CH')) AND type = 'LIVE_TV' AND streamUrl != ''")
    suspend fun getDachLiveChannelCount(playlistId: String): Int

    @Query("SELECT COUNT(*) FROM channels WHERE playlistId = :playlistId AND type = :type")
    suspend fun getChannelCountByType(playlistId: String, type: ContentType): Int

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId ORDER BY isDach DESC, groupTitle ASC, name ASC")
    fun getChannelsByPlaylist(playlistId: String): Flow<List<ChannelItem>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND type = :type ORDER BY isDach DESC, groupTitle ASC, name ASC")
    fun getChannelsByPlaylistAndType(playlistId: String, type: ContentType): Flow<List<ChannelItem>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND isFavorite = 1")
    fun getFavoriteChannels(playlistId: String): Flow<List<ChannelItem>>

    @Query("SELECT DISTINCT groupTitle FROM channels WHERE playlistId = :playlistId AND type = :type ORDER BY groupTitle ASC")
    fun getGroupsForType(playlistId: String, type: ContentType): Flow<List<String>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND (name LIKE '%' || :query || '%' OR groupTitle LIKE '%' || :query || '%')")
    fun searchChannels(playlistId: String, query: String): Flow<List<ChannelItem>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId")
    suspend fun getChannelsByPlaylistSync(playlistId: String): List<ChannelItem>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND (isDach = 1 OR countryCode IN ('DE', 'AT', 'CH')) ORDER BY isDach DESC, name ASC")
    suspend fun getDachChannelsByPlaylistSync(playlistId: String): List<ChannelItem>

    @Query("SELECT * FROM channels WHERE type = 'LIVE_TV'")
    suspend fun getAllLiveChannelsSync(): List<ChannelItem>

    @Query("""
        UPDATE channels 
        SET epgId = CASE WHEN isManualEpgOverride = 1 THEN manualEpgTvgId ELSE NULL END,
            epgMappingStatus = CASE WHEN isManualEpgOverride = 1 THEN 'MANUAL' ELSE 'UNMATCHED' END,
            epgMappingMethod = CASE WHEN isManualEpgOverride = 1 THEN 'MANUAL' ELSE 'NONE' END,
            epgAmbiguousCandidates = '',
            epgUnassignedReason = CASE WHEN isManualEpgOverride = 1 THEN 'Manuelle Zuordnung durch Nutzer' ELSE 'EPG-Daten gelöscht' END
    """)
    suspend fun resetAllAutoEpgMappings()

    @Query("UPDATE channels SET type = :type, seriesTitle = :seriesTitle, seasonNumber = :season, episodeNumber = :episode WHERE id = :id")
    suspend fun updateChannelMetadata(id: String, type: ContentType, seriesTitle: String?, season: Int?, episode: Int?)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannelsRaw(channels: List<ChannelItem>)

    @Transaction
    suspend fun insertChannelsBatch(channels: List<ChannelItem>) {
        if (channels.isEmpty()) return
        if (channels.size <= 2500) {
            insertChannelsRaw(channels)
        } else {
            channels.chunked(2500).forEach { chunk ->
                insertChannelsRaw(chunk)
            }
        }
    }

    @Transaction
    suspend fun insertChannels(
        channels: List<ChannelItem>,
        chunkSize: Int = 2500,
        onProgress: ((saved: Int, total: Int) -> Unit)? = null
    ) {
        if (channels.isEmpty()) return
        val total = channels.size
        var saved = 0
        val effectiveChunkSize = chunkSize.coerceAtLeast(500)
        channels.chunked(effectiveChunkSize).forEach { chunk ->
            val validChannels = ArrayList<ChannelItem>(chunk.size)
            for (ch in chunk) {
                if (!com.example.data.parser.ContentClassifier.isHeaderOrDividerChannel(ch.name, ch.streamUrl, ch.groupTitle)) {
                    validChannels.add(ch)
                }
            }
            if (validChannels.isNotEmpty()) {
                insertChannelsRaw(validChannels)
            }
            saved += chunk.size
            onProgress?.invoke(saved, total)
        }
    }

    @Transaction
    suspend fun replacePlaylistChannels(playlistId: String, channels: List<ChannelItem>, syncOnlyGerman: Boolean = false) {
        if (syncOnlyGerman) {
            deleteChannelsByPlaylistAndType(playlistId, ContentType.LIVE_TV)
        } else {
            deleteChannelsByPlaylist(playlistId)
        }
        insertChannels(channels, chunkSize = 2500)
    }

    @Update
    suspend fun updateChannel(channel: ChannelItem)

    @Query("DELETE FROM channels WHERE id = :id")
    suspend fun deleteChannelById(id: String)

    @Query("UPDATE channels SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: String, isFavorite: Boolean)

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deleteChannelsByPlaylist(playlistId: String)

    @Query("DELETE FROM channels WHERE playlistId = :playlistId AND isDach = 0 AND countryCode NOT IN ('DE', 'AT', 'CH', 'ADULT')")
    suspend fun deleteInternationalChannels(playlistId: String): Int

    @Query("DELETE FROM channels WHERE isDach = 0 AND countryCode NOT IN ('DE', 'AT', 'CH', 'ADULT')")
    suspend fun deleteAllInternationalChannels(): Int

    @Query("DELETE FROM channels WHERE playlistId = :playlistId AND type = :type")
    suspend fun deleteChannelsByPlaylistAndType(playlistId: String, type: ContentType)

    @Query("""
        UPDATE channels 
        SET epgId = :epgId,
            epgMappingMethod = :method,
            epgMappingStatus = :status,
            epgAmbiguousCandidates = :candidates,
            epgUnassignedReason = :reason,
            manualEpgTvgId = :manualTvgId,
            isManualEpgOverride = :isManualOverride
        WHERE id = :channelId
    """)
    suspend fun updateEpgMapping(
        channelId: String,
        epgId: String?,
        method: String,
        status: String,
        candidates: String,
        reason: String,
        manualTvgId: String?,
        isManualOverride: Boolean
    )

    @Transaction
    suspend fun updateEpgMappingsChunk(updates: List<EpgMappingUpdate>) {
        for (u in updates) {
            updateEpgMapping(
                channelId = u.channelId,
                epgId = u.epgId,
                method = u.method,
                status = u.status,
                candidates = u.candidates,
                reason = u.reason,
                manualTvgId = u.manualTvgId,
                isManualOverride = u.isManualOverride
            )
        }
    }

    suspend fun updateEpgMappingsBatch(updates: List<EpgMappingUpdate>, chunkSize: Int = 500) {
        if (updates.isEmpty()) return
        updates.chunked(chunkSize).forEach { chunk ->
            updateEpgMappingsChunk(chunk)
            kotlinx.coroutines.yield()
        }
    }

    @Query("SELECT * FROM channels WHERE isManualEpgOverride = 1")
    suspend fun getManualEpgChannels(): List<ChannelItem>

    @Query("SELECT * FROM channels WHERE id = :id LIMIT 1")
    suspend fun getChannelById(id: String): ChannelItem?

    @Query("SELECT * FROM channels WHERE tvgId = :tvgId OR epgId = :tvgId LIMIT 1")
    suspend fun getChannelByTvgId(tvgId: String): ChannelItem?

    @Query("SELECT * FROM channels WHERE tvgId IN (:tvgIds) OR epgId IN (:tvgIds)")
    suspend fun getChannelsByTvgIds(tvgIds: List<String>): List<ChannelItem>

    @Query("DELETE FROM channels")
    suspend fun deleteAllChannels()

    // --- PROVIDER CATEGORIES & SUBCATEGORIES (1:1 PROVIDER HIERARCHY & ON-DEMAND SYNC) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProviderCategoriesRaw(categories: List<com.example.data.model.ProviderCategoryEntity>)

    @Transaction
    suspend fun insertProviderCategories(categories: List<com.example.data.model.ProviderCategoryEntity>) {
        if (categories.isEmpty()) return
        categories.chunked(1000).forEach { chunk ->
            insertProviderCategoriesRaw(chunk)
        }
    }

    @Query("SELECT * FROM provider_categories WHERE playlistId = :playlistId ORDER BY sortOrder ASC")
    fun getProviderCategoriesFlow(playlistId: String): Flow<List<com.example.data.model.ProviderCategoryEntity>>

    @Query("SELECT * FROM provider_categories WHERE playlistId = :playlistId ORDER BY sortOrder ASC")
    suspend fun getProviderCategoriesSync(playlistId: String): List<com.example.data.model.ProviderCategoryEntity>

    @Query("SELECT * FROM provider_categories WHERE playlistId = :playlistId AND contentType = :contentType ORDER BY sortOrder ASC")
    suspend fun getProviderCategoriesByTypeSync(
        playlistId: String,
        contentType: ContentType
    ): List<com.example.data.model.ProviderCategoryEntity>

    @Query("SELECT * FROM provider_categories WHERE playlistId = :playlistId AND countryCode = :countryCode ORDER BY sortOrder ASC")
    suspend fun getProviderCategoriesByCountrySync(
        playlistId: String,
        countryCode: String
    ): List<com.example.data.model.ProviderCategoryEntity>

    @Query("UPDATE provider_categories SET isSynced = 1 WHERE playlistId = :playlistId AND countryCode = :countryCode")
    suspend fun markCountryCategoriesSynced(playlistId: String, countryCode: String)

    @Query("DELETE FROM provider_categories WHERE playlistId = :playlistId")
    suspend fun deleteProviderCategoriesByPlaylist(playlistId: String)

    @Query("DELETE FROM provider_categories")
    suspend fun deleteAllProviderCategories()
}

data class EpgMappingUpdate(
    val channelId: String,
    val epgId: String?,
    val method: String,
    val status: String,
    val candidates: String,
    val reason: String,
    val manualTvgId: String?,
    val isManualOverride: Boolean
)