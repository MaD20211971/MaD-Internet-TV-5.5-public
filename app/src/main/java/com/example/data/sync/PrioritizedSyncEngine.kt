package com.example.data.sync

import android.util.Log
import com.example.data.local.ChannelDao
import com.example.data.model.ChannelItem
import com.example.data.model.PlaylistEntity
import com.example.data.model.PlaylistType
import com.example.data.parser.M3UParser
import com.example.data.parser.MacPortalClient
import com.example.data.parser.XtreamCodesClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * High-performance non-blocking IPTV synchronization engine.
 *
 * Implements a strict two-phase synchronization strategy:
 * - Phase 1 (DACH Fast-Lane): DE, AT, and CH channels are extracted, verified, and saved to the
 *   Room database within the first 1-2 seconds, instantly unblocking the UI and allowing playback.
 * - Phase 2 (Background Hydration): International channels, VOD movies, and series are streamed
 *   and batch-inserted in the background without UI lag, stutter, or blocking.
 */
class PrioritizedSyncEngine(
    private val channelDao: ChannelDao,
    private val xtreamClient: XtreamCodesClient = XtreamCodesClient(),
    private val macClient: MacPortalClient = MacPortalClient(),
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(okhttp3.ConnectionPool(4, 5, TimeUnit.MINUTES))
        .dispatcher(okhttp3.Dispatcher().apply {
            maxRequests = 3
            maxRequestsPerHost = 2
        })
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {

    data class SyncStatus(
        val isSyncing: Boolean = false,
        val isDachReady: Boolean = false,
        val playlistId: String = "",
        val playlistName: String = "",
        val phaseName: String = "",
        val message: String = "",
        val dachCount: Int = 0,
        val totalCount: Int = 0,
        val isComplete: Boolean = false,
        val error: String? = null
    )

    private val _syncStatus = MutableStateFlow(SyncStatus())
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    /**
     * Executes prioritized non-blocking synchronization for an M3U stream.
     */
    suspend fun syncM3uStream(
        inputStream: InputStream,
        playlist: PlaylistEntity,
        syncOnlyGerman: Boolean = false,
        onDachReady: suspend (List<ChannelItem>) -> Unit,
        onProgressUpdate: (SyncStatus) -> Unit
    ): Result<List<ChannelItem>> = withContext(Dispatchers.IO) {
        try {
            val start = System.currentTimeMillis()
            var dachEmitted = false
            var totalSaved = 0
            val allAccumulated = mutableListOf<ChannelItem>()

            _syncStatus.value = SyncStatus(
                isSyncing = true,
                isDachReady = false,
                playlistId = playlist.id,
                playlistName = playlist.name,
                phaseName = "Phase 1: DACH Fast-Lane",
                message = "Deutsche, österreichische und Schweizer Sender werden priorisiert geladen..."
            )
            onProgressUpdate(_syncStatus.value)

            val parseResult = M3UParser.parseWithPriority(
                inputStream = inputStream,
                playlistId = playlist.id,
                syncOnlyGerman = syncOnlyGerman,
                onDachChannelsReady = { dachList ->
                    if (dachList.isNotEmpty()) {
                        // Clear old channels and insert prioritized DACH channels first
                        channelDao.deleteChannelsByPlaylist(playlist.id)
                        channelDao.insertChannels(dachList)
                        totalSaved += dachList.size
                        allAccumulated.addAll(dachList)
                        dachEmitted = true

                        _syncStatus.value = _syncStatus.value.copy(
                            isDachReady = true,
                            phaseName = "Phase 2: Hintergrund-Sync",
                            message = "DE/AT/CH bereit (${dachList.size} Sender) • Weitere Sender werden im Hintergrund geladen...",
                            dachCount = dachList.size,
                            totalCount = totalSaved
                        )
                        onProgressUpdate(_syncStatus.value)

                        // Immediately notify UI so channels can be displayed & played right away!
                        onDachReady(dachList)
                    }
                },
                onBackgroundChunk = { chunk ->
                    if (chunk.isNotEmpty()) {
                        channelDao.insertChannelsRaw(chunk)
                        totalSaved += chunk.size
                        if (allAccumulated.size < 2000) {
                            allAccumulated.addAll(chunk.take(2000 - allAccumulated.size))
                        }

                        _syncStatus.value = _syncStatus.value.copy(
                            message = "${totalSaved} Einträge synchronisiert...",
                            totalCount = totalSaved
                        )
                        onProgressUpdate(_syncStatus.value)
                        yield()
                    }
                }
            )

            // If no DACH channels were encountered (e.g. international-only list), flush remaining channels
            if (!dachEmitted && parseResult.channels.isNotEmpty()) {
                channelDao.deleteChannelsByPlaylist(playlist.id)
                channelDao.insertChannels(parseResult.channels)
                allAccumulated.addAll(parseResult.channels.take(1000))
                onDachReady(parseResult.channels)
            }

            val durationMs = System.currentTimeMillis() - start
            val finalChannels = if (allAccumulated.isNotEmpty()) allAccumulated else parseResult.channels

            _syncStatus.value = SyncStatus(
                isSyncing = false,
                isDachReady = true,
                playlistId = playlist.id,
                playlistName = playlist.name,
                phaseName = "Abgeschlossen",
                message = "Playlist '${playlist.name}' vollständig synchronisiert (${finalChannels.size} Einträge in ${durationMs / 1000}s)",
                totalCount = finalChannels.size,
                isComplete = true
            )
            onProgressUpdate(_syncStatus.value)

            Result.success(finalChannels)
        } catch (e: Exception) {
            Log.e("PrioritizedSyncEngine", "Error in syncM3uStream", e)
            _syncStatus.value = _syncStatus.value.copy(
                isSyncing = false,
                error = e.message
            )
            onProgressUpdate(_syncStatus.value)
            Result.failure(e)
        }
    }

    /**
     * Executes prioritized non-blocking synchronization for Xtream Codes.
     */
    suspend fun syncXtreamCodes(
        serverUrl: String,
        user: String,
        pass: String,
        playlist: PlaylistEntity,
        syncOnlyGerman: Boolean = false,
        onDachReady: suspend (List<ChannelItem>) -> Unit,
        onProgressUpdate: (SyncStatus) -> Unit
    ): Result<List<ChannelItem>> = withContext(Dispatchers.IO) {
        try {
            _syncStatus.value = SyncStatus(
                isSyncing = true,
                isDachReady = false,
                playlistId = playlist.id,
                playlistName = playlist.name,
                phaseName = "Verbindungsaufbau",
                message = "Verbinde mit Xtream Server..."
            )
            onProgressUpdate(_syncStatus.value)

            var totalSavedCount = 0
            val initialDachBatch = mutableListOf<ChannelItem>()
            var emittedDach = false

            channelDao.deleteChannelsByPlaylist(playlist.id)

            // Fetch live streams and prioritize DACH in stream batches
            val result = xtreamClient.fetchXtreamContent(
                serverUrl = serverUrl,
                username = user,
                password = pass,
                playlistId = playlist.id,
                syncOnlyGerman = syncOnlyGerman,
                onBatchParsed = { batch, isDachBatch ->
                    if (batch.isNotEmpty()) {
                        channelDao.insertChannelsBatch(batch)
                        totalSavedCount += batch.size
                        if (isDachBatch) {
                            if (initialDachBatch.size < 100) {
                                initialDachBatch.addAll(batch.take(100 - initialDachBatch.size))
                            }
                            if (!emittedDach && totalSavedCount >= 50) {
                                emittedDach = true
                                _syncStatus.value = SyncStatus(
                                    isSyncing = true,
                                    isDachReady = true,
                                    playlistId = playlist.id,
                                    playlistName = playlist.name,
                                    phaseName = "Phase 2: Hintergrund-Sync",
                                    message = "DE/AT/CH bereit ($totalSavedCount Sender)",
                                    dachCount = totalSavedCount,
                                    totalCount = totalSavedCount
                                )
                                onProgressUpdate(_syncStatus.value)
                                onDachReady(initialDachBatch.toList())
                            }
                        }
                        yield()
                    }
                }
            )

            result.fold(
                onSuccess = { channels ->
                    val finalCount = if (totalSavedCount > 0) totalSavedCount else channels.size
                    if (finalCount == 0) {
                        return@withContext Result.failure(Exception("Keine Inhalte auf dem Xtream-Server gefunden."))
                    }

                    if (!emittedDach) {
                        val (dachChannels, otherChannels) = channels.partition {
                            it.isDach || it.countryCode in listOf("DE", "AT", "CH")
                        }
                        val initialDach = if (dachChannels.isNotEmpty()) dachChannels else channels.take(100)
                        if (totalSavedCount == 0) {
                            channelDao.insertChannels(initialDach)
                            if (otherChannels.isNotEmpty()) {
                                otherChannels.chunked(500).forEach { chunk ->
                                    channelDao.insertChannelsRaw(chunk)
                                    yield()
                                }
                            }
                        }
                        onDachReady(initialDach)
                    }

                    _syncStatus.value = _syncStatus.value.copy(
                        isSyncing = false,
                        isDachReady = true,
                        phaseName = "Abgeschlossen",
                        message = "Xtream Server synchronisiert ($finalCount Einträge)",
                        totalCount = finalCount,
                        isComplete = true
                    )
                    onProgressUpdate(_syncStatus.value)

                    Result.success(channels)
                },
                onFailure = { err ->
                    _syncStatus.value = _syncStatus.value.copy(
                        isSyncing = false,
                        error = err.message
                    )
                    onProgressUpdate(_syncStatus.value)
                    Result.failure(err)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Executes prioritized non-blocking synchronization for MAC / Stalker Portals.
     */
    suspend fun syncMacPortal(
        portalUrl: String,
        macAddress: String,
        playlist: PlaylistEntity,
        syncOnlyGerman: Boolean = false,
        onDachReady: suspend (List<ChannelItem>) -> Unit,
        onProgressUpdate: (SyncStatus) -> Unit
    ): Result<List<ChannelItem>> = withContext(Dispatchers.IO) {
        try {
            _syncStatus.value = SyncStatus(
                isSyncing = true,
                isDachReady = false,
                playlistId = playlist.id,
                playlistName = playlist.name,
                phaseName = "Verbindungsaufbau",
                message = "Verbinde mit MAC Portal..."
            )
            onProgressUpdate(_syncStatus.value)

            val result = macClient.fetchMacPortalChannels(
                portalUrl = portalUrl,
                macAddress = macAddress,
                playlistId = playlist.id,
                syncOnlyGerman = syncOnlyGerman,
                onProgress = { progress ->
                    _syncStatus.value = _syncStatus.value.copy(
                        phaseName = progress.step,
                        message = progress.detail,
                        totalCount = progress.liveCount + progress.movieCount + progress.seriesCount
                    )
                    onProgressUpdate(_syncStatus.value)
                }
            )

            result.fold(
                onSuccess = { channels ->
                    val (dachChannels, otherChannels) = channels.partition {
                        it.isDach || it.countryCode in listOf("DE", "AT", "CH")
                    }

                    channelDao.deleteChannelsByPlaylist(playlist.id)
                    val initialDach = if (dachChannels.isNotEmpty()) dachChannels else channels.take(100)
                    channelDao.insertChannels(initialDach)

                    _syncStatus.value = SyncStatus(
                        isSyncing = otherChannels.isNotEmpty(),
                        isDachReady = true,
                        playlistId = playlist.id,
                        playlistName = playlist.name,
                        phaseName = "Phase 2: Hintergrund-Sync",
                        message = "DE/AT/CH bereit (${initialDach.size} Sender)",
                        dachCount = initialDach.size,
                        totalCount = initialDach.size
                    )
                    onProgressUpdate(_syncStatus.value)
                    onDachReady(initialDach)

                    if (otherChannels.isNotEmpty()) {
                        otherChannels.chunked(500).forEach { chunk ->
                            channelDao.insertChannelsRaw(chunk)
                            yield()
                        }
                    }

                    _syncStatus.value = _syncStatus.value.copy(
                        isSyncing = false,
                        phaseName = "Abgeschlossen",
                        message = "MAC Portal synchronisiert (${channels.size} Einträge)",
                        totalCount = channels.size,
                        isComplete = true
                    )
                    onProgressUpdate(_syncStatus.value)

                    Result.success(channels)
                },
                onFailure = { err ->
                    _syncStatus.value = _syncStatus.value.copy(
                        isSyncing = false,
                        error = err.message
                    )
                    onProgressUpdate(_syncStatus.value)
                    Result.failure(err)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
