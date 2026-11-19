package com.example.data.parser

import android.util.Log
import com.example.data.local.EpgDao
import com.example.data.local.EpgMappingUpdate
import com.example.data.model.ChannelItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

sealed class EpgSyncState {
    object Idle : EpgSyncState()
    data class Syncing(val message: String, val progressPercent: Int = 0) : EpgSyncState()
    data class Success(val message: String, val programCount: Int, val timestamp: Long = System.currentTimeMillis()) : EpgSyncState()
    data class Error(val message: String) : EpgSyncState()
}

class EpgSyncManager(
    private val epgDao: EpgDao,
    private val channelDao: com.example.data.local.ChannelDao? = null
) {

    private val syncMutex = Mutex()

    private val client: OkHttpClient = try {
        val trustAllCerts = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
        )
        val sslContext = SSLContext.getInstance("SSL").apply {
            init(null, trustAllCerts, SecureRandom())
        }
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(4, 5, TimeUnit.MINUTES))
            .dispatcher(Dispatcher().apply {
                maxRequests = 2
                maxRequestsPerHost = 1
            })
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    } catch (_: Exception) {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(4, 5, TimeUnit.MINUTES))
            .dispatcher(Dispatcher().apply {
                maxRequests = 2
                maxRequestsPerHost = 1
            })
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val _syncState = MutableStateFlow<EpgSyncState>(EpgSyncState.Idle)
    val syncState: StateFlow<EpgSyncState> = _syncState.asStateFlow()

    fun notifySuccess(message: String, count: Int) {
        _syncState.value = EpgSyncState.Success(message, count)
    }

    suspend fun syncEpgData(
        epgUrl: String,
        channels: List<ChannelItem> = emptyList(),
        timezoneId: String = "Europe/Berlin",
        offsetHours: Int = 0
    ): Result<Int> = withContext(Dispatchers.IO) {
        // Prevent overlapping concurrent sync runs that cause OOM and SQLite contention
        if (!syncMutex.tryLock()) {
            val currentCount = runCatching { epgDao.getProgramCount() }.getOrDefault(0)
            return@withContext Result.success(currentCount)
        }

        try {
            val cleanUrl = epgUrl.trim()
            if (cleanUrl.isBlank() || cleanUrl.startsWith("local", ignoreCase = true) || cleanUrl.contains("smart_generator", ignoreCase = true)) {
                _syncState.value = EpgSyncState.Error("Keine gültige EPG-URL konfiguriert")
                return@withContext Result.failure(IllegalArgumentException("Keine gültige EPG-URL konfiguriert"))
            }

            val urlList = cleanUrl.split(",", ";", "\n")
                .map { it.trim() }
                .filter { it.isNotBlank() && (it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)) }

            val urlsToProcess = if (urlList.isNotEmpty()) urlList else listOf(cleanUrl)
            val xmlDisplayNameById = HashMap<String, String>(1024)
            var totalInserted = 0

            // Clean up expired programs first
            val cutoff = System.currentTimeMillis() - (6 * 3600 * 1000L)
            runCatching { epgDao.deleteOldPrograms(cutoff) }

            for ((index, singleUrl) in urlsToProcess.withIndex()) {
                val stepPercent = 15 + ((index.toFloat() / urlsToProcess.size) * 60).toInt()
                _syncState.value = EpgSyncState.Syncing("Lade EPG-Feed ${index + 1}/${urlsToProcess.size}...", stepPercent)
                Log.d("EpgSyncManager", "Starting EPG sync for [$singleUrl] (${index + 1}/${urlsToProcess.size})")

                try {
                    val request = Request.Builder()
                        .url(singleUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                        .header("Accept", "application/xml, text/xml, */*")
                        .header("Accept-Encoding", "gzip, deflate, identity")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful && response.body != null) {
                            _syncState.value = EpgSyncState.Syncing("Verarbeite EPG-Feed ${index + 1}/${urlsToProcess.size}...", stepPercent + 10)
                            val inputStream: InputStream = response.body!!.byteStream()
                            val (xmlChannels, xmlPrograms) = XmltvParser.parse(
                                inputStream = inputStream,
                                isGzip = singleUrl.endsWith(".gz", ignoreCase = true) || singleUrl.contains(".xml.gz", ignoreCase = true),
                                sourceUrl = singleUrl,
                                maxPrograms = 22000
                            )
                            for (c in xmlChannels) {
                                xmlDisplayNameById[c.id.lowercase()] = c.displayName
                            }
                            if (xmlPrograms.isNotEmpty()) {
                                _syncState.value = EpgSyncState.Syncing("Speichere ${xmlPrograms.size} Sendungen (Feed ${index + 1})...", stepPercent + 20)
                                epgDao.insertProgramsBatch(xmlPrograms, chunkSize = 500)
                                totalInserted += xmlPrograms.size
                            }
                            Log.d("EpgSyncManager", "Saved ${xmlPrograms.size} programs and ${xmlChannels.size} channels from $singleUrl")
                        } else {
                            Log.w("EpgSyncManager", "Remote EPG HTTP ${response.code} for $singleUrl")
                        }
                    }
                } catch (urlErr: Exception) {
                    Log.w("EpgSyncManager", "Failed downloading from $singleUrl: ${urlErr.message}")
                }
            }

            val count = runCatching { epgDao.getProgramCount() }.getOrDefault(totalInserted)
            if (count > 0) {
                val effectiveChannels = if (channels.isNotEmpty()) {
                    channels
                } else {
                    channelDao?.getAllLiveChannelsSync() ?: emptyList()
                }

                if (effectiveChannels.isNotEmpty() && channelDao != null) {
                    _syncState.value = EpgSyncState.Syncing("Prüfe 100% eindeutige Senderzuordnung...", 90)
                    val distinctFromDb = try {
                        epgDao.getDistinctEpgChannels()
                    } catch (_: Exception) {
                        emptyList()
                    }

                    val availableEpg = ArrayList<EpgChannelInfo>(distinctFromDb.size)
                    val seenIds = HashSet<String>(distinctFromDb.size * 2)

                    for (tuple in distinctFromDb) {
                        val lowerId = tuple.channelTvgId.lowercase()
                        if (seenIds.add(lowerId)) {
                            val dName = xmlDisplayNameById[lowerId]?.takeIf { it.isNotBlank() }
                                ?: tuple.channelName.ifBlank { tuple.channelTvgId }
                            availableEpg.add(
                                EpgChannelInfo(
                                    id = tuple.channelTvgId,
                                    displayName = dName,
                                    programCount = tuple.programCount
                                )
                            )
                        }
                    }

                    if (availableEpg.isNotEmpty()) {
                        val lookupIndex = EpgMatcher.buildIndex(availableEpg)
                        val pendingUpdates = ArrayList<EpgMappingUpdate>(256)

                        for (ch in effectiveChannels) {
                            val result = EpgMatcher.matchChannelIndexed(ch, lookupIndex)
                            val resolvedEpgId = if (ch.isManualEpgOverride && !ch.manualEpgTvgId.isNullOrBlank()) {
                                ch.manualEpgTvgId
                            } else if (result.status == EpgMappingStatus.MATCHED) {
                                result.matchedTvgId
                            } else {
                                null
                            }
                            val newMethod = if (ch.isManualEpgOverride) "MANUAL" else result.method.name
                            val newStatus = if (ch.isManualEpgOverride) "MANUAL" else result.status.name
                            val newCandidates = result.candidates.joinToString(";")
                            val newReason = if (ch.isManualEpgOverride) "Manuelle Zuordnung durch Nutzer" else result.reason
                            val newManualTvgId = if (ch.isManualEpgOverride) ch.manualEpgTvgId else resolvedEpgId

                            // Only write to SQLite if the mapping actually changed (prevents Room Flow invalidation storms)
                            if (ch.epgId != resolvedEpgId || ch.epgMappingStatus != newStatus || ch.epgMappingMethod != newMethod) {
                                pendingUpdates.add(
                                    EpgMappingUpdate(
                                        channelId = ch.id,
                                        epgId = resolvedEpgId,
                                        method = newMethod,
                                        status = newStatus,
                                        candidates = newCandidates,
                                        reason = newReason,
                                        manualTvgId = newManualTvgId,
                                        isManualOverride = ch.isManualEpgOverride
                                    )
                                )
                            }
                        }

                        if (pendingUpdates.isNotEmpty()) {
                            channelDao.updateEpgMappingsBatch(pendingUpdates, chunkSize = 500)
                        }
                    }
                }

                val successMsg = "EPG-Feed aktiv! $count verifizierte Sendungen geladen."
                _syncState.value = EpgSyncState.Success(successMsg, count)
                return@withContext Result.success(count)
            } else {
                Log.w("EpgSyncManager", "Remote XMLTV parsed 0 programs")
                val errMsg = "Keine passenden EPG-Daten im Feed gefunden"
                _syncState.value = EpgSyncState.Error(errMsg)
                return@withContext Result.failure(Exception(errMsg))
            }
        } catch (e: Exception) {
            Log.w("EpgSyncManager", "EPG Sync remote failure: ${e.message}", e)
            val errMsg = e.localizedMessage ?: "Verbindungsfehler beim EPG-Laden"
            _syncState.value = EpgSyncState.Error(errMsg)
            return@withContext Result.failure(e)
        } finally {
            syncMutex.unlock()
        }
    }

    suspend fun clearAllEpgData() = withContext(Dispatchers.IO) {
        epgDao.clearAllEpg()
        channelDao?.resetAllAutoEpgMappings()
        _syncState.value = EpgSyncState.Success("Alle EPG-Einträge gelöscht (0 Sendungen).", 0)
    }

    suspend fun populatePreloadedSchedules(
        channels: List<ChannelItem>,
        timezoneId: String = "Europe/Berlin",
        offsetHours: Int = 0,
        force: Boolean = false
    ) = withContext(Dispatchers.IO) {
        // Keine synthetischen Sendedaten generieren - nur verifizierte XMLTV-Daten anzeigen
    }
}
