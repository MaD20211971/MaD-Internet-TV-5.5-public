package com.example.data.radio

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class RadioSyncState(
    val isSyncing: Boolean = false,
    val statusText: String = "",
    val lastSyncedAt: Long = 0L,
    val totalSynced: Int = 0,
    val errorMessage: String? = null
)

class RadioBrowserRepository(
    context: Context,
    private val dao: RadioStationDao = RadioBrowserDatabase.getDatabase(context).radioStationDao(),
    private val apiClient: RadioBrowserApiClient = RadioBrowserApiClient()
) {
    companion object {
        private const val TAG = "RadioBrowserRepository"
        private const val PREFS_NAME = "radio_browser_prefs"
        private const val KEY_LAST_SYNCED_AT = "last_synced_at"
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val localStations: Flow<List<RadioStationEntity>> = dao.observeAllStations()
    val localStationCount: Flow<Int> = dao.observeStationCount()

    private val _syncState = MutableStateFlow(
        RadioSyncState(
            lastSyncedAt = prefs.getLong(KEY_LAST_SYNCED_AT, 0L)
        )
    )
    val syncState: StateFlow<RadioSyncState> = _syncState.asStateFlow()

    private val _onlineSearchResults = MutableStateFlow<List<RadioStationEntity>>(emptyList())
    val onlineSearchResults: StateFlow<List<RadioStationEntity>> = _onlineSearchResults.asStateFlow()

    private val _isSearchingOnline = MutableStateFlow(false)
    val isSearchingOnline: StateFlow<Boolean> = _isSearchingOnline.asStateFlow()

    private val _onlineSearchError = MutableStateFlow<String?>(null)
    val onlineSearchError: StateFlow<String?> = _onlineSearchError.asStateFlow()

    suspend fun ensureInitialSyncIfEmpty() {
        val count = withContext(Dispatchers.IO) { dao.getStationCount() }
        if (count == 0 && !_syncState.value.isSyncing) {
            syncFromRadioBrowser()
        } else if (count > 0 && _syncState.value.lastSyncedAt == 0L) {
            val dbTimestamp = withContext(Dispatchers.IO) { dao.getLastSyncedAt() ?: 0L }
            _syncState.value = _syncState.value.copy(lastSyncedAt = dbTimestamp, totalSynced = count)
        }
    }

    suspend fun syncFromRadioBrowser(): Result<Int> = withContext(Dispatchers.IO) {
        if (_syncState.value.isSyncing) {
            return@withContext Result.success(_syncState.value.totalSynced)
        }

        _syncState.value = _syncState.value.copy(
            isSyncing = true,
            statusText = "Verbinde mit radio-browser.info...",
            errorMessage = null
        )

        try {
            val existingFavorites = dao.getFavoriteUuids().toHashSet()
            val fetched = apiClient.fetchSyncCatalog { stage ->
                _syncState.value = _syncState.value.copy(statusText = stage)
            }

            val mergedWithFavorites = fetched.map { station ->
                if (station.stationUuid in existingFavorites) {
                    station.copy(isFavorite = true)
                } else {
                    station
                }
            }

            _syncState.value = _syncState.value.copy(
                statusText = "Speichere ${mergedWithFavorites.size} Sender lokal..."
            )
            dao.upsertAll(mergedWithFavorites)

            val now = System.currentTimeMillis()
            prefs.edit().putLong(KEY_LAST_SYNCED_AT, now).apply()
            val totalCount = dao.getStationCount()

            _syncState.value = RadioSyncState(
                isSyncing = false,
                statusText = "Synchronisiert ($totalCount Sender)",
                lastSyncedAt = now,
                totalSynced = totalCount,
                errorMessage = null
            )
            Result.success(totalCount)
        } catch (e: Exception) {
            Log.e(TAG, "Radio-Browser sync failed: ${e.message}", e)
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                statusText = "",
                errorMessage = e.message ?: "Synchronisierung fehlgeschlagen"
            )
            Result.failure(e)
        }
    }

    suspend fun searchWorldwideOnline(query: String, onlyMp3: Boolean = false) = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.length < 2) {
            _onlineSearchResults.value = emptyList()
            _onlineSearchError.value = null
            return@withContext
        }

        _isSearchingOnline.value = true
        _onlineSearchError.value = null
        try {
            val favs = dao.getFavoriteUuids().toHashSet()
            val hits = apiClient.searchWorldwide(clean, onlyMp3 = onlyMp3).map { st ->
                if (st.stationUuid in favs) st.copy(isFavorite = true) else st
            }
            _onlineSearchResults.value = hits
        } catch (e: Exception) {
            Log.e(TAG, "Online search failed: ${e.message}", e)
            _onlineSearchError.value = e.message ?: "Online-Suche fehlgeschlagen"
        } finally {
            _isSearchingOnline.value = false
        }
    }

    fun clearOnlineSearch() {
        _onlineSearchResults.value = emptyList()
        _onlineSearchError.value = null
    }

    suspend fun saveStationLocally(station: RadioStationEntity) = withContext(Dispatchers.IO) {
        val existing = dao.getByUuid(station.stationUuid)
        val merged = station.copy(
            isFavorite = existing?.isFavorite ?: station.isFavorite,
            lastSyncedAt = System.currentTimeMillis()
        )
        dao.upsert(merged)
    }

    suspend fun toggleFavorite(station: RadioStationEntity): Boolean = withContext(Dispatchers.IO) {
        val existing = dao.getByUuid(station.stationUuid)
        val newFav = !(existing?.isFavorite ?: station.isFavorite)
        if (existing != null) {
            dao.setFavorite(station.stationUuid, newFav)
        } else {
            dao.upsert(station.copy(isFavorite = newFav, lastSyncedAt = System.currentTimeMillis()))
        }
        // Mirror updated favorite flag in current online search results as well
        _onlineSearchResults.value = _onlineSearchResults.value.map {
            if (it.stationUuid == station.stationUuid) it.copy(isFavorite = newFav) else it
        }
        newFav
    }

    suspend fun toggleFavoriteByChannelId(channelId: String): Boolean? = withContext(Dispatchers.IO) {
        val uuid = RadioStationEntity.extractUuidFromChannelId(channelId) ?: return@withContext null
        val existing = dao.getByUuid(uuid) ?: return@withContext null
        val newFav = !existing.isFavorite
        dao.setFavorite(uuid, newFav)
        _onlineSearchResults.value = _onlineSearchResults.value.map {
            if (it.stationUuid == uuid) it.copy(isFavorite = newFav) else it
        }
        newFav
    }

    suspend fun findStationByChannelId(channelId: String): RadioStationEntity? = withContext(Dispatchers.IO) {
        val uuid = RadioStationEntity.extractUuidFromChannelId(channelId) ?: return@withContext null
        dao.getByUuid(uuid)
            ?: _onlineSearchResults.value.firstOrNull { it.stationUuid == uuid }
    }

    suspend fun reportClickForChannel(channelId: String) {
        val uuid = RadioStationEntity.extractUuidFromChannelId(channelId) ?: return
        apiClient.reportStationClick(uuid)
    }
}
