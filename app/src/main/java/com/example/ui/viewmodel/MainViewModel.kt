package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.country.ClassificationStatus
import com.example.data.country.CountryResolutionResult
import com.example.data.country.CountryResolver
import com.example.data.country.ManualCountryOverrideStore
import com.example.data.local.AppDatabase
import com.example.data.model.AppSettings
import com.example.data.model.CachingMode
import com.example.data.model.ChannelItem
import com.example.data.model.ChannelSortMode
import com.example.data.model.ContentType
import com.example.data.model.EpgProgram
import com.example.data.model.PlayerEngine
import com.example.data.model.PlaylistEntity
import com.example.data.model.PlaylistType
import com.example.data.model.PlaylistUpdateNoticeState
import com.example.data.model.MacPortalConnectionDialogState
import com.example.data.model.formatPlaylistDisplayName
import com.example.data.model.RecordingItem
import com.example.data.model.RecordingStatus
import com.example.data.parser.ContentClassifier
import com.example.data.parser.CountryFolder
import com.example.data.parser.FreeEpgService
import com.example.data.parser.M3UParser
import com.example.data.parser.MacPortalClient
import com.example.data.parser.MacPortalProgress
import com.example.data.parser.PlaylistBackupManager
import com.example.data.parser.SampleData
import com.example.data.parser.XtreamCodesClient
import com.example.data.player.PlayerManager
import com.example.data.player.VodResumeItem
import com.example.data.player.VodWatchHistoryManager
import com.example.domain.model.PlaybackProgress
import com.example.data.model.CountrySummary
import com.example.data.repository.ContentRepository
import com.example.util.PerformanceMonitor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import com.example.data.storage.DiscoveredLocalM3uFile
import com.example.data.storage.DownloadStorageHelper
import com.example.data.storage.LocalM3uDuplicateStatus
import com.example.data.storage.LocalM3uFolderScanner
import com.example.data.storage.LocalM3uScanResult

enum class MainTab {
    LIVE_TV,
    RADIO,
    MOVIES,
    SERIES,
    LIVE_TV_LIGHT,
    LOCAL_M3U,
    SEARCH,
    FAVORITES,
    CONTINUE_WATCHING,
    WEB_STREAMS,
    EPG_GUIDE,
    RECORDINGS,
    SETTINGS
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val playlistDao = db.playlistDao()
    private val channelDao = db.channelDao()
    private val recordingDao = db.recordingDao()
    val epgDao = db.epgDao()

    val playerManager = PlayerManager(application)
    private val xtreamClient = XtreamCodesClient()
    private val macClient = MacPortalClient()
    val epgSyncManager = com.example.data.parser.EpgSyncManager(epgDao, channelDao)
    val epgSyncState = epgSyncManager.syncState

    val contentRepository = ContentRepository(channelDao)

    // Radio-Browser.info lokale Room-Datenbank & Online-Suche
    val radioBrowserRepository = com.example.data.radio.RadioBrowserRepository(application)
    val localRadioStations: StateFlow<List<com.example.data.radio.RadioStationEntity>> =
        radioBrowserRepository.localStations
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val radioBrowserStationCount: StateFlow<Int> =
        radioBrowserRepository.localStationCount
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val radioSyncState = radioBrowserRepository.syncState
    val radioOnlineResults = radioBrowserRepository.onlineSearchResults
    val isSearchingRadioOnline = radioBrowserRepository.isSearchingOnline
    val radioOnlineError = radioBrowserRepository.onlineSearchError

    private val _activeRadioRecordingInfo = MutableStateFlow<String?>(null)
    val activeRadioRecordingInfo: StateFlow<String?> = _activeRadioRecordingInfo.asStateFlow()

    private val _activeRadioSongTitle = MutableStateFlow<String?>(null)
    val activeRadioSongTitle: StateFlow<String?> = _activeRadioSongTitle.asStateFlow()

    private var radioOnlineSearchJob: kotlinx.coroutines.Job? = null
    private var radioRecordingJob: kotlinx.coroutines.Job? = null

    val vodResumeList: StateFlow<List<VodResumeItem>> = VodWatchHistoryManager.getContinueWatchingFlow(application)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allVodHistory: StateFlow<List<VodResumeItem>> = VodWatchHistoryManager.getAllWatchHistoryFlow(application)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val seriesProgressMap: StateFlow<Map<String, PlaybackProgress>> = db.watchHistoryDao().getAllSeriesEpisodesFlow()
        .map { list ->
            list.associate { ep ->
                val posMs = ep.lastPositionSeconds * 1000L
                val durMs = if (ep.totalDurationSeconds > 0) ep.totalDurationSeconds * 1000L else 1000L
                ep.episodeId to PlaybackProgress(
                    contentId = ep.episodeId,
                    positionMs = posMs,
                    durationMs = durMs,
                    isCompleted = ep.isCompleted
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    private val _countrySummary = MutableStateFlow(CountrySummary.EMPTY)
    val countrySummary: StateFlow<CountrySummary> = _countrySummary.asStateFlow()

    private val _selectedCountryCode = MutableStateFlow("ALLE")
    val selectedCountryCode: StateFlow<String> = _selectedCountryCode.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _hasMorePages = MutableStateFlow(true)
    val hasMorePages: StateFlow<Boolean> = _hasMorePages.asStateFlow()

    private val _isLoadingNextPage = MutableStateFlow(false)
    val isLoadingNextPage: StateFlow<Boolean> = _isLoadingNextPage.asStateFlow()

    // App Settings
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    // Navigation & Tab Selection
    private val _selectedTab = MutableStateFlow(MainTab.LIVE_TV)
    val selectedTab: StateFlow<MainTab> = _selectedTab.asStateFlow()

    // Active Playlist
    private val _activePlaylistId = MutableStateFlow<String?>(null)
    val activePlaylistId: StateFlow<String?> = _activePlaylistId.asStateFlow()

    val playlists: StateFlow<List<PlaylistEntity>> = playlistDao.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All Channels in current Playlist
    private val _allChannels = MutableStateFlow<List<ChannelItem>>(emptyList())
    val allChannels: StateFlow<List<ChannelItem>> = _allChannels.asStateFlow()

    // Active Playing Channel / Content
    private val _currentlyPlaying = MutableStateFlow<ChannelItem?>(null)
    val currentlyPlaying: StateFlow<ChannelItem?> = _currentlyPlaying.asStateFlow()

    // Live EPG mapping for all channels (current and next show from Room / XMLTV server)
    private val _liveEpgMap = MutableStateFlow<Map<String, Pair<EpgProgram?, EpgProgram?>>>(emptyMap())
    val liveEpgMap: StateFlow<Map<String, Pair<EpgProgram?, EpgProgram?>>> = _liveEpgMap.asStateFlow()

    // Full timeline schedule map for EPG Grid (only 100% unambiguously matched channels)
    private val _epgGridMap = MutableStateFlow<Map<String, List<EpgProgram>>>(emptyMap())
    val epgGridMap: StateFlow<Map<String, List<EpgProgram>>> = _epgGridMap.asStateFlow()

    @Volatile
    private var isAutoEpgSyncRunning = false


    private val _isPlayerFullscreen = MutableStateFlow(false)
    val isPlayerFullscreen: StateFlow<Boolean> = _isPlayerFullscreen.asStateFlow()

    // Asynchronously filtered channels per tab to completely offload UI thread
    private val _filteredLiveChannels = MutableStateFlow<List<ChannelItem>>(emptyList())
    val filteredLiveChannels: StateFlow<List<ChannelItem>> = _filteredLiveChannels.asStateFlow()

    private val _filteredMovies = MutableStateFlow<List<ChannelItem>>(emptyList())
    val filteredMovies: StateFlow<List<ChannelItem>> = _filteredMovies.asStateFlow()

    private val _filteredSeries = MutableStateFlow<List<ChannelItem>>(emptyList())
    val filteredSeries: StateFlow<List<ChannelItem>> = _filteredSeries.asStateFlow()

    private val _filteredRadio = MutableStateFlow<List<ChannelItem>>(emptyList())
    val filteredRadio: StateFlow<List<ChannelItem>> = _filteredRadio.asStateFlow()

    private val _favoriteChannels = MutableStateFlow<List<ChannelItem>>(emptyList())
    val favoriteChannels: StateFlow<List<ChannelItem>> = _favoriteChannels.asStateFlow()

    private val _searchResults = MutableStateFlow<List<ChannelItem>>(emptyList())
    val searchResults: StateFlow<List<ChannelItem>> = _searchResults.asStateFlow()

    // Pre-computed badge counts and categories offloaded from UI thread
    private val _badgeCounts = MutableStateFlow(ChannelBadgeCounts())
    val badgeCounts: StateFlow<ChannelBadgeCounts> = _badgeCounts.asStateFlow()

    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    private val _providerCategoryGroups = MutableStateFlow<List<com.example.data.category.ProviderCategoryGroup>>(emptyList())
    val providerCategoryGroups: StateFlow<List<com.example.data.category.ProviderCategoryGroup>> = _providerCategoryGroups.asStateFlow()

    private val _countryFolders = MutableStateFlow<List<CountryFolder>>(emptyList())
    val countryFolders: StateFlow<List<CountryFolder>> = _countryFolders.asStateFlow()

    val prioritizedSyncEngine by lazy {
        com.example.data.sync.PrioritizedSyncEngine(
            channelDao = channelDao,
            xtreamClient = xtreamClient,
            macClient = macClient,
            httpClient = httpClient
        )
    }

    // Country & Channel Manager (Länder- & Kanal-Selektor)
    private val countryChannelPrefs by lazy {
        getApplication<Application>().getSharedPreferences("country_channel_manager_prefs_v1", Context.MODE_PRIVATE)
    }

    // Installations-Schutz / Ersteinrichtungs-Sperre (PIN 1971)
    private val installationSecurityPrefs by lazy {
        getApplication<Application>().getSharedPreferences("installation_security_prefs_v1", Context.MODE_PRIVATE)
    }

    private val settingsPrefs by lazy {
        getApplication<Application>().getSharedPreferences("app_settings_prefs_v1", Context.MODE_PRIVATE)
    }

    private var isEpgClearedByUser = false

    private val _isAppUnlocked = MutableStateFlow<Boolean>(loadInstallationUnlockState())
    val isAppUnlocked: StateFlow<Boolean> = _isAppUnlocked.asStateFlow()

    private fun loadInstallationUnlockState(): Boolean {
        return installationSecurityPrefs.getBoolean("app_installation_unlocked_v1", false)
    }

    fun unlockAppWithInstallationPin() {
        installationSecurityPrefs.edit().putBoolean("app_installation_unlocked_v1", true).apply()
        _isAppUnlocked.value = true
    }

    fun lockApp() {
        installationSecurityPrefs.edit().putBoolean("app_installation_unlocked_v1", false).apply()
        _isAppUnlocked.value = false
    }

    private val _disabledCountries = MutableStateFlow<Set<String>>(loadDisabledCountries())
    val disabledCountries: StateFlow<Set<String>> = _disabledCountries.asStateFlow()

    private val _hiddenChannelIds = MutableStateFlow<Set<String>>(loadHiddenChannelIds())
    val hiddenChannelIds: StateFlow<Set<String>> = _hiddenChannelIds.asStateFlow()

    private val _channelSortMode = MutableStateFlow<ChannelSortMode>(loadChannelSortMode())
    val channelSortMode: StateFlow<ChannelSortMode> = _channelSortMode.asStateFlow()

    fun getDefaultDisabledCountries(): Set<String> {
        // Pre-activated by default: DE:, AT:, CH:, and ADULT. All other countries are disabled by default and synced on demand.
        return ContentClassifier.ALL_KNOWN_COUNTRIES
            .map { it.code.trim().uppercase() }
            .filter { it !in listOf("DE", "AT", "CH", "ADULT") }
            .toSet()
    }

    private fun loadDisabledCountries(): Set<String> {
        val stored = countryChannelPrefs.getStringSet("disabled_countries_v6", null)
        return stored?.toSet() ?: getDefaultDisabledCountries()
    }

    private fun loadHiddenChannelIds(): Set<String> {
        return countryChannelPrefs.getStringSet("hidden_channels_v2", emptySet())?.toSet() ?: emptySet()
    }

    private fun saveDisabledCountries(set: Set<String>) {
        countryChannelPrefs.edit().putStringSet("disabled_countries_v6", set).apply()
    }

    private fun saveHiddenChannelIds(set: Set<String>) {
        countryChannelPrefs.edit().putStringSet("hidden_channels_v2", set).apply()
    }

    private fun loadChannelSortMode(): ChannelSortMode {
        val stored = countryChannelPrefs.getString("channel_sort_mode_v3", ChannelSortMode.DACH_CANONICAL.id)
        return ChannelSortMode.fromId(stored)
    }

    private fun saveChannelSortMode(mode: ChannelSortMode) {
        countryChannelPrefs.edit().putString("channel_sort_mode_v3", mode.id).apply()
    }

    fun setChannelSortMode(mode: ChannelSortMode) {
        if (_channelSortMode.value == mode) return
        _channelSortMode.value = mode
        saveChannelSortMode(mode)
        refreshAfterFilterChange()
    }

    fun toggleCountry(countryCode: String) {
        val current = _disabledCountries.value.toMutableSet()
        val upper = countryCode.uppercase().trim()
        val wasDisabled = current.contains(upper)
        if (wasDisabled) {
            current.remove(upper)
        } else {
            current.add(upper)
        }
        _disabledCountries.value = current
        saveDisabledCountries(current)
        refreshAfterFilterChange()

        // If activating a country (e.g. US) and no channels exist in database, download them!
        if (wasDisabled) {
            checkAndDownloadChannelsForCountry(upper)
        }
    }

    fun enableCountry(countryCode: String) {
        val current = _disabledCountries.value.toMutableSet()
        val upper = countryCode.uppercase().trim()
        if (current.remove(upper)) {
            _disabledCountries.value = current
            saveDisabledCountries(current)
            refreshAfterFilterChange()
            checkAndDownloadChannelsForCountry(upper)
        }
    }

    private val activeCountryDownloads = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private fun checkAndDownloadChannelsForCountry(countryCodeUpper: String) {
        val cleanCode = countryCodeUpper.uppercase().trim()
        if (cleanCode in listOf("", "ALLE", "ALL", "OTHER")) return
        val activeId = _activePlaylistId.value ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val hasInMemory = _allChannels.value.any {
                it.normalizedCountryCode.equals(cleanCode, ignoreCase = true) ||
                it.countryCode.equals(cleanCode, ignoreCase = true)
            }
            val countInDb = channelDao.getCountryChannelCountByPlaylist(activeId, cleanCode)
            if (!hasInMemory && countInDb == 0) {
                downloadCountryOnDemand(cleanCode)
            } else if (!hasInMemory && countInDb > 0) {
                loadChannelsForPlaylist(activeId)
            }
        }
    }

    private fun downloadCountryOnDemand(countryCodeUpper: String) {
        val cleanCode = countryCodeUpper.uppercase().trim()
        if (cleanCode in listOf("", "ALLE", "ALL", "OTHER")) return
        val activeId = _activePlaylistId.value ?: return
        val downloadKey = "${activeId}_$cleanCode"
        if (!activeCountryDownloads.add(downloadKey)) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pl = playlistDao.getPlaylistById(activeId) ?: return@launch
                val countryDisplay = ContentClassifier.ALL_KNOWN_COUNTRIES.find { it.code == cleanCode }?.name ?: cleanCode
                _statusMessage.value = "Synchronisiere LiveTV, Filme & Serien für $countryDisplay [$cleanCode]..."
                startPlaylistUpdateNotice(pl.name, "Synchronisiere LiveTV, Filme & Serien für $countryDisplay [$cleanCode]...")

                var addedCount = 0
                val targetSet = setOf(cleanCode)

                when (pl.type) {
                    PlaylistType.XTREAM_CODES -> {
                        val res = xtreamClient.fetchXtreamContent(
                            serverUrl = pl.url,
                            username = pl.username,
                            password = pl.password,
                            playlistId = pl.id,
                            syncOnlyGerman = false,
                            allowedCountries = targetSet,
                            includeVodAndSeries = true,
                            onCategoriesDiscovered = { cats ->
                                if (cats.isNotEmpty()) {
                                    channelDao.insertProviderCategories(cats)
                                }
                            },
                            onBatchParsed = { batch, _ ->
                                if (batch.isNotEmpty()) {
                                    channelDao.insertChannelsBatch(batch)
                                    addedCount += batch.size
                                    updatePlaylistNoticeProgress("$countryDisplay [$cleanCode]: $addedCount Einträge geladen...")
                                }
                            }
                        )
                        res.onSuccess { list ->
                            if (addedCount == 0 && list.isNotEmpty()) {
                                channelDao.insertChannelsBatch(list)
                                addedCount += list.size
                            }
                        }
                    }
                    PlaylistType.MAC_PORTAL -> {
                        val res = macClient.fetchMacPortalChannels(
                            portalUrl = pl.url,
                            macAddress = pl.macAddress,
                            playlistId = pl.id,
                            syncOnlyGerman = false,
                            allowedCountries = targetSet,
                            includeVodAndSeries = true,
                            onCategoriesDiscovered = { cats ->
                                if (cats.isNotEmpty()) {
                                    channelDao.insertProviderCategories(cats)
                                }
                            },
                            onBatchParsed = { batch, _ ->
                                if (batch.isNotEmpty()) {
                                    channelDao.insertChannelsBatch(batch)
                                    addedCount += batch.size
                                    updatePlaylistNoticeProgress("$countryDisplay [$cleanCode]: $addedCount Einträge geladen...")
                                }
                            }
                        )
                        res.onSuccess { list ->
                            if (addedCount == 0 && list.isNotEmpty()) {
                                channelDao.insertChannelsBatch(list)
                                addedCount += list.size
                            }
                        }
                    }
                    PlaylistType.M3U_URL -> {
                        var m3uSuccess = false
                        val (serverUrl, user, pass) = extractCredentialsFromUrlOrEntity(pl)
                        if (serverUrl.isNotBlank() && user.isNotBlank() && pass.isNotBlank()) {
                            val res = xtreamClient.fetchXtreamContent(
                                serverUrl = serverUrl,
                                username = user,
                                password = pass,
                                playlistId = pl.id,
                                syncOnlyGerman = false,
                                allowedCountries = targetSet,
                                includeVodAndSeries = true,
                                onCategoriesDiscovered = { cats ->
                                    if (cats.isNotEmpty()) {
                                        channelDao.insertProviderCategories(cats)
                                    }
                                },
                                onBatchParsed = { batch, _ ->
                                    if (batch.isNotEmpty()) {
                                        channelDao.insertChannelsBatch(batch)
                                        addedCount += batch.size
                                        updatePlaylistNoticeProgress("$countryDisplay [$cleanCode]: $addedCount Einträge geladen...")
                                    }
                                }
                            )
                            res.onSuccess { list ->
                                if (addedCount == 0 && list.isNotEmpty()) {
                                    channelDao.insertChannelsBatch(list)
                                    addedCount += list.size
                                }
                                m3uSuccess = addedCount > 0
                            }
                        }
                        if (!m3uSuccess && pl.url.isNotBlank()) {
                            try {
                                val response = if (com.example.data.parser.KoolToCatalogHelper.isKoolToUrl(pl.url)) {
                                    com.example.data.parser.KoolToCatalogHelper.executeCatalogRequest(httpClient, pl.url)
                                } else {
                                    httpClient.newCall(
                                        Request.Builder()
                                            .url(pl.url)
                                            .header("User-Agent", "IPTVSmartersPro/3.1.5.1")
                                            .header("Accept", "*/*")
                                            .build()
                                    ).execute()
                                }
                                if (response != null && response.isSuccessful) {
                                    val stream = response.body?.byteStream()
                                    if (stream != null) {
                                        M3UParser.parseWithPriority(
                                            inputStream = stream,
                                            playlistId = pl.id,
                                            syncOnlyGerman = false,
                                            allowedCountries = targetSet,
                                            includeVodAndSeries = true,
                                            onCategoriesDiscovered = { cats ->
                                                if (cats.isNotEmpty()) {
                                                    channelDao.insertProviderCategories(cats)
                                                }
                                            },
                                            onDachChannelsReady = { list ->
                                                if (list.isNotEmpty()) {
                                                    channelDao.insertChannelsBatch(list)
                                                    addedCount += list.size
                                                }
                                            },
                                            onBackgroundChunk = { chunk ->
                                                if (chunk.isNotEmpty()) {
                                                    channelDao.insertChannelsBatch(chunk)
                                                    addedCount += chunk.size
                                                    updatePlaylistNoticeProgress("$countryDisplay [$cleanCode]: $addedCount Einträge geladen...")
                                                }
                                            }
                                        )
                                        m3uSuccess = addedCount > 0
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    }
                    PlaylistType.M3U_FILE -> {}
                }

                channelDao.markCountryCategoriesSynced(pl.id, cleanCode)
                updatePlaylistCountsFromDb(pl.id)
                refreshCountrySummary()
                loadChannelsForPlaylist(pl.id)

                if (addedCount > 0) {
                    _statusMessage.value = "$addedCount Einträge (LiveTV, Filme, Serien) für $countryDisplay [$cleanCode] synchronisiert!"
                    _playlistUpdateNotice.value = PlaylistUpdateNoticeState(
                        isVisible = true,
                        title = "$countryDisplay [$cleanCode] bereit",
                        message = "$addedCount Einträge für $countryDisplay synchronisiert",
                        playlistName = pl.name,
                        isLoading = false,
                        isReady = true,
                        germanCount = addedCount,
                        isError = false
                    )
                } else {
                    _statusMessage.value = "Keine Kanäle für $countryDisplay [$cleanCode] auf diesem Server gefunden."
                    _playlistUpdateNotice.value = _playlistUpdateNotice.value.copy(isVisible = false, isLoading = false)
                }
                viewModelScope.launch {
                    kotlinx.coroutines.delay(2500)
                    _playlistUpdateNotice.value = _playlistUpdateNotice.value.copy(isVisible = false)
                }
            } catch (e: Exception) {
                Log.w("MainViewModel", "On-demand country download failed for $cleanCode: ${e.message}")
                _playlistUpdateNotice.value = _playlistUpdateNotice.value.copy(isVisible = false, isLoading = false)
            } finally {
                activeCountryDownloads.remove(downloadKey)
            }
        }
    }

    fun disableCountry(countryCode: String) {
        val current = _disabledCountries.value.toMutableSet()
        val upper = countryCode.uppercase().trim()
        if (current.add(upper)) {
            _disabledCountries.value = current
            saveDisabledCountries(current)
            refreshAfterFilterChange()
        }
    }

    fun enableAllCountries() {
        _disabledCountries.value = emptySet()
        saveDisabledCountries(emptySet())
        refreshAfterFilterChange()
    }

    fun disableAllCountries(except: List<String> = listOf("DE", "AT", "CH", "ADULT")) {
        val allCodes = ContentClassifier.ALL_KNOWN_COUNTRIES.map { it.code }.toSet()
        val toDisable = allCodes - except.map { it.uppercase() }.toSet()
        _disabledCountries.value = toDisable
        saveDisabledCountries(toDisable)
        refreshAfterFilterChange()
    }

    fun toggleChannelHidden(channelId: String) {
        val current = _hiddenChannelIds.value.toMutableSet()
        if (current.contains(channelId)) {
            current.remove(channelId)
        } else {
            current.add(channelId)
        }
        _hiddenChannelIds.value = current
        saveHiddenChannelIds(current)
        refreshAfterFilterChange()
    }

    fun removeChannel(channelId: String) {
        val current = _hiddenChannelIds.value.toMutableSet()
        if (current.add(channelId)) {
            _hiddenChannelIds.value = current
            saveHiddenChannelIds(current)
            refreshAfterFilterChange()
        }
    }

    fun restoreChannel(channelId: String) {
        val current = _hiddenChannelIds.value.toMutableSet()
        if (current.remove(channelId)) {
            _hiddenChannelIds.value = current
            saveHiddenChannelIds(current)
            refreshAfterFilterChange()
        }
    }

    fun removeAllChannelsInCountry(countryCode: String) {
        val target = countryCode.uppercase().trim()
        val channelsToHide = _allChannels.value.filter {
            it.normalizedCountryCode == target || ContentClassifier.detectCountryForGroup(it.groupTitle) == target
        }.map { it.id }
        val current = _hiddenChannelIds.value.toMutableSet()
        current.addAll(channelsToHide)
        _hiddenChannelIds.value = current
        saveHiddenChannelIds(current)
        refreshAfterFilterChange()
    }

    fun restoreAllChannelsInCountry(countryCode: String) {
        val target = countryCode.uppercase().trim()
        val channelsToUnhide = _allChannels.value.filter {
            it.normalizedCountryCode == target || ContentClassifier.detectCountryForGroup(it.groupTitle) == target
        }.map { it.id }.toSet()
        val current = _hiddenChannelIds.value.toMutableSet()
        current.removeAll(channelsToUnhide)
        _hiddenChannelIds.value = current
        saveHiddenChannelIds(current)
        refreshAfterFilterChange()
    }

    fun restoreAllChannels() {
        _hiddenChannelIds.value = emptySet()
        saveHiddenChannelIds(emptySet())
        refreshAfterFilterChange()
    }

    fun resetCountryAndChannelFilters() {
        val defaultDisabled = getDefaultDisabledCountries()
        _disabledCountries.value = defaultDisabled
        _hiddenChannelIds.value = emptySet()
        saveDisabledCountries(defaultDisabled)
        saveHiddenChannelIds(emptySet())
        refreshAfterFilterChange()
    }

    private val manualOverrideStore by lazy {
        ManualCountryOverrideStore.getInstance(getApplication())
    }

    private val _debugInspectionChannel = MutableStateFlow<Pair<ChannelItem, CountryResolutionResult>?>(null)
    val debugInspectionChannel: StateFlow<Pair<ChannelItem, CountryResolutionResult>?> = _debugInspectionChannel.asStateFlow()

    fun inspectChannelCountry(channel: ChannelItem) {
        val result = CountryResolver.resolve(
            id = channel.id,
            title = channel.name,
            groupTitle = channel.groupTitle,
            tvgId = channel.tvgId,
            tvgName = channel.tvgName,
            streamUrl = channel.streamUrl,
            manualOverrideStore = manualOverrideStore
        )
        _debugInspectionChannel.value = Pair(channel, result)
    }

    fun closeCountryInspection() {
        _debugInspectionChannel.value = null
    }

    fun applyCountryOverride(channelId: String, normalizedStationId: String, newCountryCode: String) {
        manualOverrideStore.setOverride(channelId, normalizedStationId, newCountryCode)
        viewModelScope.launch(Dispatchers.IO) {
            val upper = newCountryCode.trim().uppercase()
            val updated = _allChannels.value.map { ch ->
                if (ch.id == channelId || (normalizedStationId.isNotBlank() && ch.normalizedChannelId == normalizedStationId)) {
                    ch.copy(
                        countryCode = upper,
                        isDach = upper in listOf("DE", "AT", "CH"),
                        manualCountryOverride = true,
                        manualCountry = upper,
                        countryConfidence = 100,
                        classificationStatus = ClassificationStatus.VERIFIED.name
                    )
                } else ch
            }
            channelDao.insertChannels(updated.filter { it.id == channelId || (normalizedStationId.isNotBlank() && it.normalizedChannelId == normalizedStationId) })
            _allChannels.value = updated
            refreshAfterFilterChange()

            val currentInspect = _debugInspectionChannel.value
            if (currentInspect != null && currentInspect.first.id == channelId) {
                val newResult = CountryResolver.resolve(
                    id = channelId,
                    title = currentInspect.first.name,
                    groupTitle = currentInspect.first.groupTitle,
                    manualOverrideStore = manualOverrideStore
                )
                _debugInspectionChannel.value = Pair(
                    currentInspect.first.copy(
                        countryCode = upper,
                        manualCountryOverride = true,
                        manualCountry = upper,
                        countryConfidence = 100,
                        classificationStatus = ClassificationStatus.VERIFIED.name
                    ),
                    newResult
                )
            }
        }
    }

    fun removeCountryOverride(channelId: String, normalizedStationId: String) {
        manualOverrideStore.removeOverride(channelId, normalizedStationId)
        viewModelScope.launch(Dispatchers.IO) {
            val updated = _allChannels.value.map { ch ->
                if (ch.id == channelId || (normalizedStationId.isNotBlank() && ch.normalizedChannelId == normalizedStationId)) {
                    val resolved = CountryResolver.resolve(
                        id = ch.id,
                        title = ch.name,
                        groupTitle = ch.groupTitle,
                        tvgId = ch.tvgId,
                        tvgName = ch.tvgName,
                        streamUrl = ch.streamUrl,
                        manualOverrideStore = null
                    )
                    ch.copy(
                        countryCode = resolved.countryCode,
                        isDach = resolved.isDach,
                        manualCountryOverride = false,
                        manualCountry = null,
                        countryConfidence = resolved.confidence,
                        classificationStatus = resolved.status.name,
                        countryEvidenceJson = resolved.evidenceDescriptions.joinToString("; ")
                    )
                } else ch
            }
            channelDao.insertChannels(updated.filter { it.id == channelId || (normalizedStationId.isNotBlank() && it.normalizedChannelId == normalizedStationId) })
            _allChannels.value = updated
            refreshAfterFilterChange()
            _debugInspectionChannel.value = null
        }
    }

    fun addCustomChannel(
        name: String,
        streamUrl: String,
        countryCode: String,
        groupTitle: String = "Allgemein"
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val activePlId = _activePlaylistId.value ?: SampleData.PLUTO_TV_PLAYLIST_ID
            val upperCode = countryCode.uppercase().trim()
            val newChannel = ChannelItem(
                id = "custom_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(6)}",
                playlistId = activePlId,
                name = name.trim(),
                streamUrl = streamUrl.trim(),
                logoUrl = null,
                groupTitle = groupTitle.trim().ifBlank { "Allgemein" },
                type = ContentType.LIVE_TV,
                countryCode = upperCode,
                isDach = upperCode in listOf("DE", "AT", "CH"),
                resolutionBadge = "HD"
            )
            channelDao.insertChannels(listOf(newChannel))
            enableCountry(upperCode)
            withContext(Dispatchers.Main) {
                _statusMessage.value = "Kanal '${name.trim()}' erfolgreich zu $upperCode hinzugefügt!"
            }
        }
    }

    fun deleteChannelPermanently(channelId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            channelDao.deleteChannelById(channelId)
            val current = _hiddenChannelIds.value.toMutableSet()
            if (current.remove(channelId)) {
                _hiddenChannelIds.value = current
                saveHiddenChannelIds(current)
            }
            withContext(Dispatchers.Main) {
                _statusMessage.value = "Kanal dauerhaft gelöscht."
            }
            refreshAfterFilterChange()
        }
    }

    private fun refreshAfterFilterChange() {
        viewModelScope.launch(Dispatchers.Default) {
            val channels = _allChannels.value
            val clean = channels.filter {
                it.id !in _hiddenChannelIds.value && it.normalizedCountryCode !in _disabledCountries.value
            }
            _badgeCounts.value = ChannelFilterEngine.calculateBadgeCounts(clean)
            val cats = ChannelFilterEngine.extractCategories(channels, _selectedTab.value)
            _categories.value = cats
            _providerCategoryGroups.value = ChannelFilterEngine.extractProviderCategoryHierarchy(channels, _selectedTab.value)
            _countryFolders.value = ContentClassifier.buildCountryFolders(cats, channels)
            computeFilteredChannels()
        }
    }

    // Search & Filter
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("ALLE")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    // Loading & Refresh states
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _playlistUpdateNotice = MutableStateFlow(PlaylistUpdateNoticeState())
    val playlistUpdateNotice: StateFlow<PlaylistUpdateNoticeState> = _playlistUpdateNotice.asStateFlow()

    private val _macPortalDialogState = MutableStateFlow<MacPortalConnectionDialogState?>(null)
    val macPortalDialogState: StateFlow<MacPortalConnectionDialogState?> = _macPortalDialogState.asStateFlow()

    fun dismissMacPortalDialog() {
        _macPortalDialogState.value = null
    }

    fun retryMacPortalConnection() {
        val current = _macPortalDialogState.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val existing = if (current.playlistId.isNotBlank()) playlistDao.getPlaylistById(current.playlistId) else null
            if (existing != null) {
                refreshPlaylist(existing)
            } else {
                addMacPortalPlaylist(current.playlistName, current.portalUrl, current.macAddress)
            }
        }
    }

    fun dismissPlaylistUpdateNotice() {
        _playlistUpdateNotice.value = _playlistUpdateNotice.value.copy(isVisible = false)
    }

    fun startPlaylistUpdateNotice(
        playlistName: String,
        initialMessage: String = "Deutsche Sender werden geladen und sortiert..."
    ) {
        _playlistUpdateNotice.value = PlaylistUpdateNoticeState(
            isVisible = true,
            title = "Playlist wird aktualisiert...",
            message = initialMessage,
            playlistName = playlistName,
            isLoading = true,
            isReady = false,
            germanCount = 0,
            isError = false
        )
    }

    fun updatePlaylistNoticeProgress(message: String) {
        if (_playlistUpdateNotice.value.isVisible && _playlistUpdateNotice.value.isLoading) {
            _playlistUpdateNotice.value = _playlistUpdateNotice.value.copy(message = message)
        }
    }

    private val _vpnHintNotice = MutableStateFlow<String?>(null)
    val vpnHintNotice: StateFlow<String?> = _vpnHintNotice.asStateFlow()

    fun showVpnHintNotice(message: String) {
        _vpnHintNotice.value = message
    }

    fun dismissVpnHintNotice() {
        _vpnHintNotice.value = null
    }

    // Recordings & Downloads
    val recordings: StateFlow<List<RecordingItem>> = recordingDao.getAllRecordings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isRecordingNow = MutableStateFlow(false)
    val isRecordingNow: StateFlow<Boolean> = _isRecordingNow.asStateFlow()
    private var currentRecordingEntityId: String? = null
    private var currentRecordingFile: File? = null
    private var liveRecordingJob: kotlinx.coroutines.Job? = null
    private var hasPerformedInitialAutoUpdate = false

    init {
        val savedEpgUrl = settingsPrefs.getString("custom_epg_url", null)
        if (!savedEpgUrl.isNullOrBlank()) {
            _settings.value = _settings.value.copy(customEpgUrl = savedEpgUrl)
        }
        initializeInitialData()
        startPeriodicEpgUpdate()
    }

    private fun startPeriodicEpgUpdate() {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                kotlinx.coroutines.delay(60_000)
                refreshLiveEpgForChannels()
            }
        }
    }

    private fun initializeInitialData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                DownloadStorageHelper.migrateFallbackFiles(getApplication())
                DownloadStorageHelper.cleanupCorruptedDummyFiles(getApplication())
                // Clean up any database recording entries that refer to deleted/corrupted tiny dummy files
                try {
                    val allRecs = recordingDao.getAllRecordingsSync()
                    for (rec in allRecs) {
                        val path = rec.filePath
                        if (!path.isNullOrBlank()) {
                            val f = File(path)
                            if (f.exists() && f.length() in 1L..512L) {
                                f.delete()
                                recordingDao.deleteRecording(rec)
                            }
                        }
                    }
                } catch (_: Exception) {}

                val wipePrefs = getApplication<Application>().getSharedPreferences("app_wipe_prefs", android.content.Context.MODE_PRIVATE)
                val hasWipedInitialPlaylists = wipePrefs.getBoolean("wiped_all_initial_playlists_v1", false)
                if (!hasWipedInitialPlaylists) {
                    channelDao.deleteAllChannels()
                    playlistDao.deleteAllPlaylists()
                    _activePlaylistId.value = null
                    _allChannels.value = emptyList()
                    wipePrefs.edit().putBoolean("wiped_all_initial_playlists_v1", true).apply()
                }

                // Delete all previous EPG entries and reset to verified XMLTV feeds
                val hasWipedOldEpg = wipePrefs.getBoolean("wiped_synthetic_epg_strict_v5", false)
                if (!hasWipedOldEpg) {
                    try {
                        epgDao.clearAllEpg()
                        channelDao.resetAllAutoEpgMappings()
                        _settings.value = _settings.value.copy(
                            customEpgUrl = "https://www.free-epg.de/api/epg/de.xml.gz,https://epg.lat/files/de.xml.gz",
                            providerEpgOffsets = emptyMap()
                        )
                        wipePrefs.edit().putBoolean("wiped_synthetic_epg_strict_v5", true).apply()
                    } catch (e: Exception) {
                        Log.w("MainViewModel", "Error clearing old synthetic EPG entries: ${e.message}")
                    }
                }

                val startupPrefs = getApplication<Application>().getSharedPreferences("iptv_startup_prefs", Context.MODE_PRIVATE)
                val maintenanceDoneV6 = startupPrefs.getBoolean("startup_maintenance_v6_done", false)

                if (!maintenanceDoneV6) {
                    // Remove known obsolete demo/test playlists once (including temporary xpanel.live test portals)
                    try {
                        val allExistingPlaylists = playlistDao.getAllPlaylistsSync()
                        val obsoleteDemoIds = setOf(
                            "default_mad_iptv_playlist",
                            SampleData.XPANEL_TEST_MAC_PLAYLIST_ID,
                            "mac_portal_xpanel_146cfc",
                            "mac_portal_xpanel_test",
                            SampleData.DRAGON_IPTV_PLAYLIST_ID,
                            SampleData.EURO_DELUXE_PLAYLIST_ID,
                            SampleData.ZEROONEX2_PLAYLIST_ID,
                            SampleData.ONAIR8K_PLAYLIST_ID,
                            SampleData.FXMAG_PLAYLIST_ID,
                            SampleData.WEASELTV_PLAYLIST_ID,
                            "mac_portal_zeroonex2",
                            "mac_portal_fxmag",
                            "mac_portal_weaseltv",
                            "mac_portal_dragoniptv",
                            "mac_portal_euro_deluxe"
                        )
                        val obsoletePlaylists = allExistingPlaylists.filter {
                            it.id in obsoleteDemoIds ||
                            it.url.contains("xpanel.live", ignoreCase = true) ||
                            it.macAddress.equals("00:1A:79:B6:DF:6F", ignoreCase = true) ||
                            it.macAddress.equals("00:1A:79:14:6C:FC", ignoreCase = true) ||
                            it.url.contains("euro-deluxe.com", ignoreCase = true) ||
                            it.url.contains("zeroonex2.xyz", ignoreCase = true) ||
                            it.url.contains("dragoniptv.de", ignoreCase = true)
                        }
                        for (deluxePl in obsoletePlaylists) {
                            channelDao.deleteChannelsByPlaylist(deluxePl.id)
                            channelDao.deleteProviderCategoriesByPlaylist(deluxePl.id)
                            playlistDao.deletePlaylist(deluxePl)
                            if (_activePlaylistId.value == deluxePl.id) {
                                _activePlaylistId.value = null
                                _allChannels.value = emptyList()
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("MainViewModel", "Error cleaning up obsolete test playlists: ${e.message}")
                    }
                }

                // Ensure initial playlists are registered in the database without overwriting user sync state
                val initialPlaylists = SampleData.getInitialPlaylists()
                for (pl in initialPlaylists) {
                    val existing = playlistDao.getPlaylistById(pl.id)
                    if (existing == null) {
                        playlistDao.insertPlaylist(pl)
                    } else if (existing.url != pl.url || existing.type != pl.type || (pl.macAddress.isNotBlank() && existing.macAddress != pl.macAddress)) {
                        playlistDao.updatePlaylist(existing.copy(
                            url = pl.url,
                            type = pl.type,
                            macAddress = if (pl.macAddress.isNotBlank()) pl.macAddress else existing.macAddress
                        ))
                    }
                }

                // Determine active playlist: prefer currently active playlist (especially if it already has synced channels in DB)
                val allPlaylists = playlistDao.getAllPlaylistsSync()
                val currentActive = allPlaylists.find { it.isActive }
                val activeWithChannels = allPlaylists.firstOrNull { channelDao.getChannelCountByPlaylist(it.id) > 0 }
                val active = when {
                    currentActive != null && channelDao.getChannelCountByPlaylist(currentActive.id) > 0 -> currentActive
                    activeWithChannels != null -> activeWithChannels
                    currentActive != null -> currentActive
                    else -> allPlaylists.find { it.id == SampleData.PLUTO_TV_PLAYLIST_ID } ?: allPlaylists.firstOrNull()
                }

                if (active != null) {
                    if (!active.isActive) {
                        playlistDao.setActivePlaylist(active.id)
                    }
                    _activePlaylistId.value = active.id
                    // Always load channels directly from the local Room database on startup.
                    // Never trigger network re-synchronization automatically on startup;
                    // re-sync happens strictly when the user clicks the Reload button.
                    loadChannelsForPlaylist(active.id)
                }

                if (!maintenanceDoneV6) {
                    performBackgroundStartupMaintenance(_activePlaylistId.value)
                    startupPrefs.edit().putBoolean("startup_maintenance_v6_done", true).apply()
                }

                // Automatically ensure EPG is active and synced on startup
                preloadEpgIfNeeded()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error in initializeInitialData", e)
            }
        }
    }

    private suspend fun performBackgroundStartupMaintenance(activePlaylistId: String?) {
        try {
            // Lightweight one-time VPN name check for initial playlists without loading full channel tables into RAM
            val initialPlaylists = SampleData.getInitialPlaylists()
            for (initialPl in initialPlaylists) {
                val existingPl = playlistDao.getPlaylistById(initialPl.id) ?: continue
                var currentName = existingPl.getDisplayName()
                val geoResult = com.example.data.parser.VpnRequirementHelper.performGeoCheck(
                    url = existingPl.url,
                    name = currentName,
                    channels = emptyList()
                )
                if (geoResult.requiresVpn) {
                    currentName = com.example.data.parser.VpnRequirementHelper.formatPlaylistNameWithVpn(
                        name = currentName,
                        requiresVpn = true,
                        countryCode = geoResult.countryCode
                    )
                }
                if (currentName != existingPl.name) {
                    playlistDao.updatePlaylist(existingPl.copy(name = currentName))
                }
            }

            // Seed default local fallback channels only for the offline Pluto TV fallback playlist if empty
            if (activePlaylistId == SampleData.PLUTO_TV_PLAYLIST_ID) {
                val count = channelDao.getChannelCountByPlaylist(activePlaylistId)
                if (count < 5) {
                    val defaults = SampleData.getDefaultChannels(activePlaylistId)
                    if (defaults.isNotEmpty()) {
                        channelDao.insertChannels(defaults)
                        updatePlaylistCountsFromDb(activePlaylistId)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error in background maintenance", e)
        }
    }

    private var searchJob: kotlinx.coroutines.Job? = null
    private var filterJob: kotlinx.coroutines.Job? = null

    fun selectTab(tab: MainTab) {
        _selectedTab.value = tab
        _selectedCategory.value = "ALLE"
        _currentPage.value = 0
        _hasMorePages.value = true
        filterJob?.cancel()
        filterJob = viewModelScope.launch(Dispatchers.Default) {
            val channels = _allChannels.value
            if (channels.isNotEmpty()) {
                val cats = ChannelFilterEngine.extractCategories(channels, tab)
                val sortedCats = ContentClassifier.sortCategoriesWithDachPriority(cats)
                _categories.value = sortedCats
                _providerCategoryGroups.value = ChannelFilterEngine.extractProviderCategoryHierarchy(channels, tab)
                _countryFolders.value = ContentClassifier.buildCountryFolders(sortedCats, channels)
            }
            computeFilteredChannels()
        }
        if (tab == MainTab.RADIO) {
            viewModelScope.launch(Dispatchers.IO) {
                radioBrowserRepository.ensureInitialSyncIfEmpty()
            }
        }
    }

    fun syncRadioBrowserStations() {
        viewModelScope.launch(Dispatchers.IO) {
            val res = radioBrowserRepository.syncFromRadioBrowser()
            res.onSuccess { total ->
                _statusMessage.value = "📻 Radio-Datenbank aktualisiert ($total Sender lokal gespeichert)."
            }.onFailure { err ->
                _statusMessage.value = "⚠️ Radio-Sync fehlgeschlagen: ${err.message ?: "Netzwerkfehler"}"
            }
        }
    }

    fun searchRadioBrowserOnline(query: String, onlyMp3: Boolean = false) {
        radioOnlineSearchJob?.cancel()
        if (query.trim().length < 2) {
            radioBrowserRepository.clearOnlineSearch()
            return
        }
        radioOnlineSearchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(350L)
            radioBrowserRepository.searchWorldwideOnline(query, onlyMp3 = onlyMp3)
        }
    }

    fun clearRadioOnlineSearch() {
        radioOnlineSearchJob?.cancel()
        radioBrowserRepository.clearOnlineSearch()
    }

    fun saveRadioStationLocally(station: com.example.data.radio.RadioStationEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            radioBrowserRepository.saveStationLocally(station)
            _statusMessage.value = "💾 '${station.name}' lokal gespeichert."
        }
    }

    fun toggleRadioStationFavorite(station: com.example.data.radio.RadioStationEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val isNowFav = radioBrowserRepository.toggleFavorite(station)
            _currentlyPlaying.value?.let { playing ->
                if (playing.id == "${com.example.data.radio.RadioStationEntity.CHANNEL_ID_PREFIX}${station.stationUuid}") {
                    _currentlyPlaying.value = playing.copy(isFavorite = isNowFav)
                }
            }
            _statusMessage.value = if (isNowFav) {
                "⭐ '${station.name}' zu Radio-Favoriten hinzugefügt"
            } else {
                "☆ '${station.name}' aus Radio-Favoriten entfernt"
            }
        }
    }

    fun playRadioStation(station: com.example.data.radio.RadioStationEntity) {
        val channelItem = station.toChannelItem()
        _activeRadioSongTitle.value = null
        playContent(channelItem)
        viewModelScope.launch(Dispatchers.IO) {
            radioBrowserRepository.reportClickForChannel(channelItem.id)
        }
    }

    private fun mapTabToContentType(tab: MainTab): ContentType? {
        return when (tab) {
            MainTab.LIVE_TV, MainTab.EPG_GUIDE -> ContentType.LIVE_TV
            MainTab.MOVIES -> ContentType.MOVIE
            MainTab.SERIES -> ContentType.SERIES
            MainTab.RADIO -> ContentType.RADIO
            else -> null
        }
    }

    fun refreshCountrySummary() {
        viewModelScope.launch(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val summary = contentRepository.getCountrySummary(_activePlaylistId.value, forceRefresh = true)
            _countrySummary.value = summary
            PerformanceMonitor.logEvent("countrySummaryTime", System.currentTimeMillis() - start, "Total: ${summary.totalCount}")
        }
    }

    fun selectCountry(countryCode: String) {
        val upper = countryCode.trim().uppercase()
        _selectedCountryCode.value = upper
        _currentPage.value = 0
        _hasMorePages.value = true

        // Automatically enable the country if the user explicitly selects it
        if (upper !in listOf("", "ALLE", "ALL", "OTHER") && upper in _disabledCountries.value) {
            val updatedDisabled = _disabledCountries.value.toMutableSet()
            updatedDisabled.remove(upper)
            _disabledCountries.value = updatedDisabled
            saveDisabledCountries(updatedDisabled)
        }

        viewModelScope.launch(Dispatchers.Default) {
            val start = System.currentTimeMillis()
            try {
                val all = _allChannels.value
                val filteredByCountry = if (upper == "ALLE" || upper == "ALL" || upper.isBlank()) {
                    all
                } else if (upper == "OTHER") {
                    all.filter { it.normalizedCountryCode == "OTHER" || it.countryCode.uppercase() !in listOf("DE", "AT", "CH", "ADULT", "UNKNOWN", "CONFLICT") }
                } else if (upper == "ADULT") {
                    all.filter {
                        !ContentClassifier.isAtOrAustriaContent("", it.groupTitle) &&
                        it.countryCode.uppercase() != "AT" &&
                        (it.countryCode.equals("ADULT", ignoreCase = true) || it.normalizedCountryCode == "ADULT" || ContentClassifier.isAdultGroup(it.groupTitle))
                    }
                } else {
                    all.filter { it.normalizedCountryCode.equals(upper, ignoreCase = true) || it.countryCode.equals(upper, ignoreCase = true) }
                }

                val q = _searchQuery.value
                val cat = _selectedCategory.value
                val hidden = _hiddenChannelIds.value
                val disabled = _disabledCountries.value
                val sortMode = _channelSortMode.value

                val live = ChannelFilterEngine.filterAndSortLiveChannels(filteredByCountry, q, cat, hidden, disabled, sortMode)
                val movies = ChannelFilterEngine.filterAndSortMovies(filteredByCountry, q, cat, hidden, disabled)
                val series = ChannelFilterEngine.filterAndSortSeries(filteredByCountry, q, cat, hidden, disabled)
                val radio = ChannelFilterEngine.filterAndSortRadio(filteredByCountry, q, cat, hidden, disabled)

                withContext(Dispatchers.Main) {
                    _filteredLiveChannels.value = live
                    _filteredMovies.value = movies
                    _filteredSeries.value = series
                    _filteredRadio.value = radio
                }

                if (filteredByCountry.isEmpty() && upper !in listOf("ALLE", "ALL", "", "OTHER")) {
                    val contentType = mapTabToContentType(_selectedTab.value)
                    val dbChannels = contentRepository.getByCountry(
                        countryCode = upper,
                        playlistId = _activePlaylistId.value,
                        type = contentType,
                        groupTitle = null,
                        hiddenIds = _hiddenChannelIds.value,
                        page = 0,
                        pageSize = 2000
                    )
                    if (dbChannels.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            when (_selectedTab.value) {
                                MainTab.LIVE_TV, MainTab.EPG_GUIDE -> _filteredLiveChannels.value = dbChannels
                                MainTab.MOVIES -> _filteredMovies.value = dbChannels
                                MainTab.SERIES -> _filteredSeries.value = dbChannels
                                MainTab.RADIO -> _filteredRadio.value = dbChannels
                                else -> _filteredLiveChannels.value = dbChannels
                            }
                        }
                    } else if (upper !in listOf("DE", "AT", "CH")) {
                        // Automatically trigger on-demand download for this country if not yet in DB!
                        downloadCountryOnDemand(upper)
                    }
                }

                val targetForCats = if (filteredByCountry.isNotEmpty()) filteredByCountry else all
                val groups = targetForCats
                    .filter { it.type == mapTabToContentType(_selectedTab.value) }
                    .sortedBy { if (it.providerSortOrder > 0) it.providerSortOrder else Int.MAX_VALUE }
                    .map { it.groupTitle }
                    .filter { it.isNotBlank() }
                    .distinct()
                val sortedGroups = ContentClassifier.sortCategoriesWithDachPriority(groups)
                withContext(Dispatchers.Main) {
                    if (sortedGroups.isNotEmpty()) {
                        _categories.value = listOf("ALLE") + sortedGroups
                    }
                }

                PerformanceMonitor.logEvent("countryQueryTime", System.currentTimeMillis() - start, "Country: $upper, items: ${filteredByCountry.size}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error selecting country $upper", e)
            }
        }
    }

    fun loadNextPage() {
        if (_isLoadingNextPage.value || !_hasMorePages.value) return
        val nextPage = _currentPage.value + 1

        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingNextPage.value = true
            try {
                val q = _searchQuery.value.trim()
                val contentType = mapTabToContentType(_selectedTab.value)
                val newItems = if (q.isNotBlank()) {
                    contentRepository.search(
                        query = q,
                        countryCode = _selectedCountryCode.value,
                        playlistId = _activePlaylistId.value,
                        type = contentType,
                        hiddenIds = _hiddenChannelIds.value,
                        page = nextPage,
                        pageSize = 1000
                    )
                } else {
                    val group = if (_selectedCategory.value in listOf("ALLE", _selectedCountryCode.value)) null else _selectedCategory.value
                    contentRepository.getByCountry(
                        countryCode = _selectedCountryCode.value,
                        playlistId = _activePlaylistId.value,
                        type = contentType,
                        groupTitle = group,
                        hiddenIds = _hiddenChannelIds.value,
                        page = nextPage,
                        pageSize = 1000
                    )
                }

                withContext(Dispatchers.Main) {
                    if (newItems.isEmpty()) {
                        _hasMorePages.value = false
                    } else {
                        _currentPage.value = nextPage
                        when (_selectedTab.value) {
                            MainTab.LIVE_TV, MainTab.EPG_GUIDE -> {
                                _filteredLiveChannels.value = (_filteredLiveChannels.value + newItems).distinctBy { it.id }
                            }
                            MainTab.MOVIES -> {
                                _filteredMovies.value = (_filteredMovies.value + newItems).distinctBy { it.id }
                            }
                            MainTab.SERIES -> {
                                _filteredSeries.value = (_filteredSeries.value + newItems).distinctBy { it.id }
                            }
                            MainTab.RADIO -> {
                                _filteredRadio.value = (_filteredRadio.value + newItems).distinctBy { it.id }
                            }
                            else -> {
                                _filteredLiveChannels.value = (_filteredLiveChannels.value + newItems).distinctBy { it.id }
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error loading next page", e)
            } finally {
                _isLoadingNextPage.value = false
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.Default) {
            kotlinx.coroutines.delay(150)
            computeFilteredChannels()
        }
    }

    fun setSelectedCategory(category: String) {
        _selectedCategory.value = category
        val upper = category.trim().uppercase()
        val knownCountry = ContentClassifier.ALL_KNOWN_COUNTRIES.firstOrNull { it.code.equals(upper, ignoreCase = true) }?.code
        val detectedCountry = when {
            knownCountry != null -> knownCountry
            com.example.data.country.CountryConstants.VALID_COUNTRY_CODES.contains(upper) -> upper
            upper.startsWith("DE:") || upper == "DEUTSCHLAND" || upper == "GERMANY" -> "DE"
            upper.startsWith("AT:") || upper == "ÖSTERREICH" || upper == "AUSTRIA" -> "AT"
            upper.startsWith("CH:") || upper == "SCHWEIZ" || upper == "SWITZERLAND" -> "CH"
            upper.startsWith("ADULT:") || upper == "ADULT" || upper == "18+ / ADULT" -> "ADULT"
            upper.startsWith("US:") || upper == "USA" || upper == "UNITED STATES" -> "US"
            upper.startsWith("UK:") || upper == "GB" || upper == "UNITED KINGDOM" || upper == "ENGLAND" -> "UK"
            upper.startsWith("TR:") || upper == "TURKEY" || upper == "TÜRKIYE" -> "TR"
            upper.startsWith("IT:") || upper == "ITALY" || upper == "ITALIEN" || upper == "ITALIA" -> "IT"
            upper.startsWith("FR:") || upper == "FRANCE" || upper == "FRANKREICH" -> "FR"
            upper.startsWith("ES:") || upper == "SPAIN" || upper == "SPANIEN" || upper == "ESPANA" -> "ES"
            upper.startsWith("NL:") || upper == "NETHERLANDS" || upper == "NIEDERLANDE" || upper == "HOLLAND" -> "NL"
            upper.startsWith("PL:") || upper == "POLAND" || upper == "POLEN" || upper == "POLSKA" -> "PL"
            upper.startsWith("EXYU:") || upper.startsWith("EX-YU:") || upper == "EXYU" || upper == "EX-YU" || upper == "BALKAN" -> "EXYU"
            upper.startsWith("ALB:") || upper.startsWith("AL:") || upper == "ALBANIA" || upper == "ALBANIEN" -> "ALB"
            upper.startsWith("RO:") || upper == "ROMANIA" || upper == "RUMÄNIEN" -> "RO"
            upper.startsWith("PT:") || upper == "PORTUGAL" || upper == "BRASIL" || upper == "BRAZIL" -> "PT"
            upper.startsWith("RU:") || upper == "RUSSIA" || upper == "RUSSLAND" -> "RU"
            upper.startsWith("AR:") || upper == "ARABIC" || upper == "ARABISCH" -> "AR"
            upper.startsWith("GR:") || upper == "GREECE" || upper == "GRIECHENLAND" -> "GR"
            upper.startsWith("BE:") || upper == "BELGIUM" || upper == "BELGIEN" -> "BE"
            upper.startsWith("CZ:") || upper == "CZECH" || upper == "TSCHECHIEN" -> "CZ"
            upper.startsWith("BG:") || upper == "BULGARIA" || upper == "BULGARIEN" -> "BG"
            upper.startsWith("SE:") || upper == "SWEDEN" || upper == "SCHWEDEN" -> "SE"
            upper.startsWith("IN:") || upper == "INDIA" || upper == "INDIEN" -> "IN"
            upper.startsWith("CA:") || upper == "CANADA" || upper == "KANADA" -> "CA"
            upper.startsWith("AF:") || upper == "AFGHANISTAN" -> "AF"
            else -> null
        }

        if (detectedCountry != null) {
            selectCountry(detectedCountry)
            return
        }

        when (upper) {
            "RADIO" -> { _selectedTab.value = MainTab.RADIO; triggerFilterRecompute() }
            "FILME", "MOVIES" -> { _selectedTab.value = MainTab.MOVIES; triggerFilterRecompute() }
            "SERIEN", "SERIES" -> { _selectedTab.value = MainTab.SERIES; triggerFilterRecompute() }
            "SPORT" -> { _selectedTab.value = MainTab.LIVE_TV; triggerFilterRecompute() }
            "FAVORITEN", "FAVORITES" -> {
                _selectedCategory.value = "FAVORITEN"
                triggerFilterRecompute()
            }
            "ALLE" -> {
                _selectedCategory.value = "ALLE"
                triggerFilterRecompute()
            }
            else -> {
                triggerFilterRecompute()
            }
        }
    }

    private fun triggerFilterRecompute() {
        filterJob?.cancel()
        filterJob = viewModelScope.launch(Dispatchers.Default) {
            computeFilteredChannels()
        }
    }

    private suspend fun computeFilteredChannels() = withContext(Dispatchers.Default) {
        val channels = _allChannels.value
        if (channels.isEmpty()) {
            _filteredLiveChannels.value = emptyList()
            _filteredMovies.value = emptyList()
            _filteredSeries.value = emptyList()
            _filteredRadio.value = emptyList()
            _favoriteChannels.value = emptyList()
            _searchResults.value = emptyList()
            return@withContext
        }

        val q = _searchQuery.value
        val cat = _selectedCategory.value
        val tab = _selectedTab.value
        val hidden = _hiddenChannelIds.value
        val disabled = _disabledCountries.value
        val sortMode = _channelSortMode.value

        _favoriteChannels.value = channels.filter { it.isFavorite && (hidden.isEmpty() || it.id !in hidden) }
        _searchResults.value = if (q.isNotBlank()) {
            val trimmedQ = q.trim()
            channels.asSequence()
                .filter {
                    (hidden.isEmpty() || it.id !in hidden) &&
                    (disabled.isEmpty() || it.normalizedCountryCode !in disabled) &&
                    (it.name.contains(trimmedQ, ignoreCase = true) || it.groupTitle.contains(trimmedQ, ignoreCase = true))
                }
                .take(300)
                .toList()
        } else {
            emptyList()
        }

        when (tab) {
            MainTab.LIVE_TV, MainTab.EPG_GUIDE -> {
                _filteredLiveChannels.value = ChannelFilterEngine.filterAndSortLiveChannels(channels, q, cat, hidden, disabled, sortMode)
            }
            MainTab.MOVIES -> {
                _filteredMovies.value = ChannelFilterEngine.filterAndSortMovies(channels, q, cat, hidden, disabled)
            }
            MainTab.SERIES -> {
                _filteredSeries.value = ChannelFilterEngine.filterAndSortSeries(channels, q, cat, hidden, disabled)
            }
            MainTab.RADIO -> {
                _filteredRadio.value = ChannelFilterEngine.filterAndSortRadio(channels, q, cat, hidden, disabled)
            }
            else -> {
                _filteredLiveChannels.value = ChannelFilterEngine.filterAndSortLiveChannels(channels, q, cat, hidden, disabled, sortMode)
            }
        }

        // Pre-compute Movies & Series in background so switching to Filme/Serien is instantaneous
        if (q.isBlank() && (cat.isBlank() || cat.uppercase() == "ALLE")) {
            if (tab != MainTab.MOVIES && _filteredMovies.value.isEmpty()) {
                _filteredMovies.value = ChannelFilterEngine.filterAndSortMovies(channels, "", "ALLE", hidden, disabled)
            }
            if (tab != MainTab.SERIES && _filteredSeries.value.isEmpty()) {
                _filteredSeries.value = ChannelFilterEngine.filterAndSortSeries(channels, "", "ALLE", hidden, disabled)
            }
        }
    }

    fun switchPlaylist(playlistId: String) {
        _activePlaylistId.value = playlistId
        loadChannelsForPlaylist(playlistId)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                playlistDao.setActivePlaylist(playlistId)
                // Do NOT auto-sync on playlist switch; load existing channels from local DB.
                // Re-synchronization is triggered exclusively via the Reload button.
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e("MainViewModel", "Fehler beim Umschalten der Playliste: ${e.message}")
            }
        }
    }

    private var loadChannelsJob: kotlinx.coroutines.Job? = null

    private fun loadChannelsForPlaylist(playlistId: String) {
        loadChannelsJob?.cancel()
        loadChannelsJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val pl = playlistDao.getPlaylistById(playlistId)
                val totalCount = pl?.let { it.liveCount + it.movieCount + it.seriesCount } ?: channelDao.getChannelCountByPlaylist(playlistId)
                val isLargeCatalog = totalCount > 1500 || (pl?.type == PlaylistType.XTREAM_CODES) || (pl?.type == PlaylistType.MAC_PORTAL)

                val enabledCodes = (ContentClassifier.ALL_KNOWN_COUNTRIES.map { it.code }.toSet() - _disabledCountries.value) + setOf("DE", "AT", "CH", "ADULT")
                val channelFlow = if (isLargeCatalog) {
                    channelDao.getChannelsByCountriesAndPlaylist(playlistId, enabledCodes.toList())
                } else {
                    channelDao.getChannelsByPlaylist(playlistId)
                }

                var isInitialPlaylistEmission = true
                channelFlow.collect { rawChannels ->
                    val channels = if (isLargeCatalog && rawChannels.isEmpty()) {
                        channelDao.getChannelsPaged(
                            countryCode = null,
                            playlistId = playlistId,
                            type = null,
                            groupTitle = null,
                            hiddenIds = emptyList(),
                            limit = 2000,
                            offset = 0
                        )
                    } else {
                        rawChannels
                    }

                    val cleanChannels = channels.filter {
                        !ContentClassifier.isHeaderOrDividerChannel(it.name, it.streamUrl, it.groupTitle)
                    }
                    val discoveredProviderCats = try {
                        channelDao.getProviderCategoriesSync(playlistId)
                    } catch (_: Exception) {
                        emptyList()
                    }
                    // Preserve warm LRU caches across DB emissions; only invalidate filter cache
                    ChannelFilterEngine.clearCache()
                    _allChannels.value = cleanChannels

                    withContext(Dispatchers.Default) {
                        val activeChannels = cleanChannels.filter {
                            it.id !in _hiddenChannelIds.value && it.normalizedCountryCode !in _disabledCountries.value
                        }
                        val badges = ChannelFilterEngine.calculateBadgeCounts(activeChannels)
                        _badgeCounts.value = badges
                        val currentType = mapTabToContentType(_selectedTab.value)
                        val catsFromChannels = ChannelFilterEngine.extractCategories(cleanChannels, _selectedTab.value)
                        val catsFromProvider = if (currentType != null) {
                            discoveredProviderCats
                                .filter { it.contentType == currentType && it.countryCode !in _disabledCountries.value }
                                .sortedBy { it.sortOrder }
                                .map { it.rawCategoryName }
                        } else emptyList()
                        val mergedCats = ContentClassifier.sortCategoriesWithDachPriority((catsFromProvider + catsFromChannels).distinct())
                        _categories.value = mergedCats
                        _providerCategoryGroups.value = ChannelFilterEngine.extractProviderCategoryHierarchy(cleanChannels, _selectedTab.value)
                        _countryFolders.value = ContentClassifier.buildCountryFolders(mergedCats, cleanChannels)
                        computeFilteredChannels()
                    }

                    val summary = contentRepository.getCountrySummary(playlistId, forceRefresh = false)
                    _countrySummary.value = summary
                    if (_selectedCountryCode.value.isNotBlank() && _selectedCountryCode.value != "ALLE") {
                        selectCountry(_selectedCountryCode.value)
                    }

                    checkGermanChannelsReadiness(cleanChannels)

                    if (isInitialPlaylistEmission) {
                        isInitialPlaylistEmission = false
                        preloadEpgIfNeeded()
                    } else {
                        refreshLiveEpgForChannels()
                    }
                }
            } catch (e: CancellationException) {
                // Cooperative cancellation when switching playlists or cancelling load
                throw e
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error collecting channels for $playlistId", e)
            }
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .connectionPool(okhttp3.ConnectionPool(16, 5, java.util.concurrent.TimeUnit.MINUTES))
        .dispatcher(okhttp3.Dispatcher().apply {
            maxRequests = 16
            maxRequestsPerHost = 8
        })
        .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(28, java.util.concurrent.TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private suspend fun applyGeoCheckAndVpnHint(
        playlist: PlaylistEntity,
        channels: List<ChannelItem>,
        errorCode: Int? = null,
        errorMessage: String? = null
    ) {
        val geo = com.example.data.parser.VpnRequirementHelper.performGeoCheck(
            url = playlist.url,
            name = playlist.name,
            channels = channels,
            statusCode = errorCode,
            errorMessage = errorMessage
        )
        if (geo.requiresVpn) {
            val updatedName = com.example.data.parser.VpnRequirementHelper.formatPlaylistNameWithVpn(
                name = playlist.name,
                requiresVpn = true,
                countryCode = geo.countryCode
            )
            if (updatedName != playlist.name) {
                playlistDao.updatePlaylist(playlist.copy(name = updatedName))
            }
            if (errorMessage != null || errorCode != null || channels.isEmpty()) {
                val hint = "🛡️ VPN-Hinweis: Verbindung zu '${playlist.name}' eingeschränkt • Eventuell VPN für ${geo.countryName} [${geo.countryCode}] aktivieren."
                _vpnHintNotice.value = hint
                playerManager.setVpnHintNotice(hint)
            }
        }
    }

    private fun checkGermanChannelsReadiness(channels: List<ChannelItem>) {
        if (!_playlistUpdateNotice.value.isVisible) return
        if (_isRefreshing.value) return
        if (_playlistUpdateNotice.value.isReady) return

        val germanChannels = channels.filter {
            (it.isDach || it.countryCode == "DE" || it.countryCode == "AT" || it.countryCode == "CH") &&
            it.type == ContentType.LIVE_TV &&
            it.streamUrl.isNotBlank()
        }

        if (germanChannels.isNotEmpty()) {
            _playlistUpdateNotice.value = PlaylistUpdateNoticeState(
                isVisible = true,
                title = "Deutsche Sender startbereit",
                message = "${germanChannels.size} deutsche Sender bereit (z. B. Das Erste, ZDF, RTL...) – sofort startbar",
                playlistName = _playlistUpdateNotice.value.playlistName,
                isLoading = false,
                isReady = true,
                germanCount = germanChannels.size,
                isError = false
            )
            viewModelScope.launch {
                kotlinx.coroutines.delay(2800)
                if (_playlistUpdateNotice.value.isReady) {
                    _playlistUpdateNotice.value = _playlistUpdateNotice.value.copy(isVisible = false)
                }
            }
        } else if (channels.isNotEmpty()) {
            _playlistUpdateNotice.value = PlaylistUpdateNoticeState(
                isVisible = true,
                title = "Aktualisierung abgeschlossen",
                message = "${channels.size} Sender geladen (keine deutschen Live-Sender gefunden)",
                playlistName = _playlistUpdateNotice.value.playlistName,
                isLoading = false,
                isReady = true,
                germanCount = 0,
                isError = false
            )
            viewModelScope.launch {
                kotlinx.coroutines.delay(3000)
                if (_playlistUpdateNotice.value.isReady) {
                    _playlistUpdateNotice.value = _playlistUpdateNotice.value.copy(isVisible = false)
                }
            }
        }
    }

    private suspend fun finalizePlaylistUpdateNotice(
        playlistId: String,
        playlistName: String,
        knownGermanCount: Int? = null,
        knownTotalCount: Int? = null
    ) {
        val germanCount = knownGermanCount ?: channelDao.getDachLiveChannelCount(playlistId)
        val totalCount = knownTotalCount ?: channelDao.getChannelCountByPlaylist(playlistId)

        if (germanCount > 0) {
            _playlistUpdateNotice.value = PlaylistUpdateNoticeState(
                isVisible = true,
                title = "Deutsche Sender startbereit",
                message = "$germanCount deutsche Sender geladen – sofort startbar",
                playlistName = playlistName,
                isLoading = false,
                isReady = true,
                germanCount = germanCount,
                isError = false
            )
            viewModelScope.launch {
                kotlinx.coroutines.delay(2800)
                if (_playlistUpdateNotice.value.isReady) {
                    _playlistUpdateNotice.value = _playlistUpdateNotice.value.copy(isVisible = false)
                }
            }
        } else if (totalCount > 0) {
            _playlistUpdateNotice.value = PlaylistUpdateNoticeState(
                isVisible = true,
                title = "Aktualisierung abgeschlossen",
                message = "$totalCount Sender geladen",
                playlistName = playlistName,
                isLoading = false,
                isReady = true,
                germanCount = 0,
                isError = false
            )
            viewModelScope.launch {
                kotlinx.coroutines.delay(3000)
                if (_playlistUpdateNotice.value.isReady) {
                    _playlistUpdateNotice.value = _playlistUpdateNotice.value.copy(isVisible = false)
                }
            }
        } else {
            _playlistUpdateNotice.value = PlaylistUpdateNoticeState(
                isVisible = true,
                title = "Aktualisierung abgeschlossen",
                message = "Keine Inhalte empfangen",
                playlistName = playlistName,
                isLoading = false,
                isReady = false,
                germanCount = 0,
                isError = false
            )
            viewModelScope.launch {
                kotlinx.coroutines.delay(3000)
                _playlistUpdateNotice.value = _playlistUpdateNotice.value.copy(isVisible = false)
            }
        }
    }

    private fun failPlaylistUpdateNotice(playlistName: String, errorMsg: String) {
        _playlistUpdateNotice.value = PlaylistUpdateNoticeState(
            isVisible = true,
            title = "Aktualisierung fehlgeschlagen",
            message = errorMsg,
            playlistName = playlistName,
            isLoading = false,
            isReady = false,
            germanCount = 0,
            isError = true
        )
        viewModelScope.launch {
            kotlinx.coroutines.delay(4000)
            _playlistUpdateNotice.value = _playlistUpdateNotice.value.copy(isVisible = false)
        }
    }

    fun refreshPlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            // Cancel background collection while refresh is writing to avoid DB lock contention & UI thread freezes
            loadChannelsJob?.cancel()
            loadChannelsJob = null

            val enabledCountries = (ContentClassifier.ALL_KNOWN_COUNTRIES.map { it.code }.toSet() - _disabledCountries.value) + setOf("DE", "AT", "CH", "ADULT")
            // By default, sync ONLY priority groups: DE:, AT:, CH:, and ADULT (+ German Movies & Series).
            // All other countries are synchronized on-demand when selected in the Country Selector.
            val targetAllowedCountries = if (_settings.value.syncOnlyGermanWhenGerman && enabledCountries.size <= 8) {
                enabledCountries
            } else if (_settings.value.syncOnlyGermanWhenGerman) {
                setOf("DE", "AT", "CH", "ADULT")
            } else {
                emptySet()
            }
            val syncOnlyGerman = targetAllowedCountries.isNotEmpty() && targetAllowedCountries.all { it in setOf("DE", "AT", "CH", "ADULT") }
            startPlaylistUpdateNotice(playlist.name, "DE:, AT:, CH:, ADULT & deutsche Filme/Serien werden synchronisiert...")
            _statusMessage.value = "Aktualisiere Playlist '${playlist.name}' (DE:/AT:/CH:/ADULT & deutsche VOD)..."
            var hadError = false
            var errorMessage: String? = null
            try {
                when (playlist.type) {
                    PlaylistType.M3U_URL -> {
                        if (playlist.url.isNotBlank()) {
                            var success = false
                            val (serverUrl, user, pass) = extractCredentialsFromUrlOrEntity(playlist)

                            // FAST-PATH FOR XTREAM M3U LINKS (get.php?username=...&password=...):
                            // Query Xtream JSON API directly by selective DACH categories in seconds instead of waiting 90-180s for a 250,000-line M3U dump
                            if (user.isNotBlank() && pass.isNotBlank() && serverUrl.isNotBlank()) {
                                updatePlaylistNoticeProgress("Verbinde über Xtream Schnell-Synchronisation...")
                                _statusMessage.value = "Synchronisiere über Xtream API (${playlist.name})..."
                                var totalSaved = 0
                                var dachSaved = 0
                                var streamLive = 0
                                var streamMovie = 0
                                var streamSeries = 0
                                var hasEmittedDachReady = false
                                var lastNoticeTime = 0L
                                channelDao.deleteChannelsByPlaylist(playlist.id)
                                channelDao.deleteProviderCategoriesByPlaylist(playlist.id)

                                val result = xtreamClient.fetchXtreamContent(
                                    serverUrl = serverUrl,
                                    username = user,
                                    password = pass,
                                    playlistId = playlist.id,
                                    syncOnlyGerman = syncOnlyGerman,
                                    allowedCountries = targetAllowedCountries,
                                    includeVodAndSeries = true,
                                    onCategoriesDiscovered = { cats ->
                                        if (cats.isNotEmpty()) {
                                            channelDao.insertProviderCategories(cats)
                                        }
                                    },
                                    onProgress = { _, detail, live, movie, series ->
                                        streamLive = live
                                        streamMovie = movie
                                        streamSeries = series
                                        val now = System.currentTimeMillis()
                                        if (now - lastNoticeTime > 750) {
                                            lastNoticeTime = now
                                            val countInfo = if (totalSaved > 0) " ($totalSaved Einträge gespeichert)" else ""
                                            updatePlaylistNoticeProgress("$detail$countInfo")
                                            _statusMessage.value = "$detail$countInfo"
                                        }
                                    },
                                    onBatchParsed = { batch, isDachBatch ->
                                        if (batch.isNotEmpty()) {
                                            channelDao.insertChannelsBatch(batch)
                                            totalSaved += batch.size
                                            if (isDachBatch) {
                                                dachSaved += batch.size
                                                if (!hasEmittedDachReady && dachSaved >= 50) {
                                                    hasEmittedDachReady = true
                                                    _statusMessage.value = "DE/AT/CH bereit ($dachSaved Sender). Speichere Katalog..."
                                                    updatePlaylistNoticeProgress("DE/AT/CH bereit ($dachSaved Sender). Speichere Katalog...")
                                                    if (_allChannels.value.isEmpty()) {
                                                        _allChannels.value = batch
                                                        _filteredLiveChannels.value = batch.filter { it.type == ContentType.LIVE_TV }
                                                    }
                                                }
                                            } else {
                                                val now = System.currentTimeMillis()
                                                if (now - lastNoticeTime > 750) {
                                                    lastNoticeTime = now
                                                    val progressMsg = "Speichere Einträge: $totalSaved..."
                                                    updatePlaylistNoticeProgress(progressMsg)
                                                    _statusMessage.value = progressMsg
                                                }
                                            }
                                            kotlinx.coroutines.yield()
                                        }
                                    }
                                )
                                result.onSuccess { channels ->
                                    if (totalSaved == 0 && channels.isNotEmpty()) {
                                        val (dach, others) = channels.partition { it.isDach || it.countryCode in listOf("DE", "AT", "CH") }
                                        if (dach.isNotEmpty()) {
                                            channelDao.insertChannelsBatch(dach)
                                            dachSaved += dach.size
                                            totalSaved += dach.size
                                            updatePlaylistNoticeProgress("Speichere DACH: ${dach.size}...")
                                            if (_allChannels.value.isEmpty()) {
                                                _allChannels.value = dach
                                            }
                                            kotlinx.coroutines.yield()
                                        }
                                        if (others.isNotEmpty()) {
                                            var lastOtherTime = 0L
                                            others.chunked(2500).forEach { chunk ->
                                                channelDao.insertChannelsBatch(chunk)
                                                totalSaved += chunk.size
                                                val now = System.currentTimeMillis()
                                                if (now - lastOtherTime > 750 || totalSaved == channels.size) {
                                                    lastOtherTime = now
                                                    val msg = "Speichere $totalSaved von ${channels.size} Einträgen..."
                                                    updatePlaylistNoticeProgress(msg)
                                                    _statusMessage.value = msg
                                                }
                                                kotlinx.coroutines.yield()
                                            }
                                        }
                                    }

                                    if (syncOnlyGerman) {
                                        channelDao.deleteInternationalChannels(playlist.id)
                                    }
                                    val finalCount = if (totalSaved > 0) totalSaved else channels.size
                                    updatePlaylistCountsFromDb(
                                        playlistId = playlist.id,
                                        knownLive = if (streamLive > 0) streamLive else null,
                                        knownRadio = 0,
                                        knownMovies = if (streamMovie > 0) streamMovie else null,
                                        knownSeries = if (streamSeries > 0) streamSeries else null
                                    )
                                    val initialDach = channelDao.getDachChannelsByPlaylistSync(playlist.id)
                                    applyGeoCheckAndVpnHint(playlist, initialDach)
                                    _statusMessage.value = "Playlist '${playlist.name}' synchronisiert ($finalCount Einträge)"
                                    updatePlaylistNoticeProgress("Fertig: $finalCount Einträge gespeichert!")
                                    finalizePlaylistUpdateNotice(
                                        playlistId = playlist.id,
                                        playlistName = playlist.name,
                                        knownGermanCount = if (dachSaved > 0) dachSaved else initialDach.size,
                                        knownTotalCount = finalCount
                                    )
                                    success = true
                                }
                            }

                            // Direct M3U stream download (for pure M3U URLs or if Xtream API fast-path wasn't applicable)
                            if (!success) {
                                updatePlaylistNoticeProgress("Lade M3U Playlist herunter...")
                                try {
                                    var response = if (com.example.data.parser.KoolToCatalogHelper.isKoolToUrl(playlist.url)) {
                                        com.example.data.parser.KoolToCatalogHelper.executeCatalogRequest(httpClient, playlist.url)
                                    } else {
                                        httpClient.newCall(
                                            Request.Builder()
                                                .url(playlist.url)
                                                .header("User-Agent", "IPTVSmartersPro/3.1.5.1")
                                                .header("Accept", "*/*")
                                                .build()
                                        ).execute()
                                    }

                                    if ((response == null || !response.isSuccessful || response.body == null) &&
                                        !com.example.data.parser.KoolToCatalogHelper.isKoolToUrl(playlist.url)
                                    ) {
                                        response?.close()
                                        response = httpClient.newCall(
                                            Request.Builder()
                                                .url(playlist.url)
                                                .header("User-Agent", "VLC/3.0.18 LibVLC/3.0.18")
                                                .header("Accept", "*/*")
                                                .build()
                                        ).execute()
                                    }

                                    if (response != null && response.isSuccessful) {
                                        val inputStream = response.body?.byteStream()
                                        if (inputStream != null) {
                                            updatePlaylistNoticeProgress("Verarbeite M3U und ordne DACH-Sender...")
                                            var m3uSaved = 0
                                            var dachSaved = 0
                                            var dachEmitted = false
                                            var lastNoticeTime = 0L
                                            channelDao.deleteChannelsByPlaylist(playlist.id)
                                            channelDao.deleteProviderCategoriesByPlaylist(playlist.id)

                                            val parseResult = M3UParser.parseWithPriority(
                                                inputStream = inputStream,
                                                playlistId = playlist.id,
                                                syncOnlyGerman = syncOnlyGerman,
                                                allowedCountries = targetAllowedCountries,
                                                includeVodAndSeries = true,
                                                onCategoriesDiscovered = { cats ->
                                                    if (cats.isNotEmpty()) {
                                                        channelDao.insertProviderCategories(cats)
                                                    }
                                                },
                                                onDachChannelsReady = { dachList ->
                                                    if (dachList.isNotEmpty()) {
                                                        channelDao.insertChannelsBatch(dachList)
                                                        dachEmitted = true
                                                        dachSaved += dachList.size
                                                        m3uSaved += dachList.size
                                                        _statusMessage.value = "DE/AT/CH/ADULT bereit (${dachList.size} Sender)..."
                                                        updatePlaylistNoticeProgress("DE/AT/CH/ADULT bereit (${dachList.size} Sender). Speichere Katalog...")
                                                        if (_allChannels.value.isEmpty()) {
                                                            _allChannels.value = dachList
                                                        }
                                                    }
                                                },
                                                onBackgroundChunk = { chunk ->
                                                    if (chunk.isNotEmpty()) {
                                                        channelDao.insertChannelsBatch(chunk)
                                                        m3uSaved += chunk.size
                                                        val now = System.currentTimeMillis()
                                                        if (now - lastNoticeTime > 750) {
                                                            lastNoticeTime = now
                                                            val msg = "Speichere Einträge: $m3uSaved..."
                                                            updatePlaylistNoticeProgress(msg)
                                                            _statusMessage.value = msg
                                                        }
                                                        kotlinx.coroutines.yield()
                                                    }
                                                }
                                            )
                                            if (!dachEmitted && parseResult.channels.isNotEmpty()) {
                                                channelDao.insertChannelsBatch(parseResult.channels)
                                                m3uSaved += parseResult.channels.size
                                            }
                                            if (syncOnlyGerman) {
                                                channelDao.deleteInternationalChannels(playlist.id)
                                            }
                                            val finalCount = if (m3uSaved > 0) m3uSaved else parseResult.totalCount
                                            updatePlaylistCountsFromDb(playlist.id)
                                            val initialDach = channelDao.getDachChannelsByPlaylistSync(playlist.id)
                                            applyGeoCheckAndVpnHint(playlist, initialDach)
                                            _statusMessage.value = "M3U Playlist '${playlist.name}' aktualisiert ($finalCount Sender)"
                                            updatePlaylistNoticeProgress("Fertig: $finalCount Sender gespeichert!")
                                            finalizePlaylistUpdateNotice(
                                                playlistId = playlist.id,
                                                playlistName = playlist.name,
                                                knownGermanCount = if (dachSaved > 0) dachSaved else initialDach.size,
                                                knownTotalCount = finalCount
                                            )
                                            success = true
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.w("MainViewModel", "M3U direct download failed: ${e.message}")
                                    hadError = true
                                    errorMessage = e.message
                                    applyGeoCheckAndVpnHint(playlist, emptyList(), errorMessage = e.message)
                                }
                            }

                            if (!success && _statusMessage.value?.startsWith("Aktualisiere") == true) {
                                _statusMessage.value = "Keine Inhalte in M3U gefunden. Bitte Link oder Server prüfen."
                                applyGeoCheckAndVpnHint(playlist, emptyList(), errorMessage = "Keine Inhalte empfangen")
                            }
                        }
                    }
                    PlaylistType.XTREAM_CODES -> {
                        updatePlaylistNoticeProgress("Verbinde mit Xtream Server...")
                        var totalSaved = 0
                        var dachSaved = 0
                        var streamLive = 0
                        var streamMovie = 0
                        var streamSeries = 0
                        var hasEmittedDachReady = false
                        var lastNoticeTime = 0L
                        channelDao.deleteChannelsByPlaylist(playlist.id)
                        channelDao.deleteProviderCategoriesByPlaylist(playlist.id)

                        val result = xtreamClient.fetchXtreamContent(
                            serverUrl = playlist.url,
                            username = playlist.username,
                            password = playlist.password,
                            playlistId = playlist.id,
                            syncOnlyGerman = syncOnlyGerman,
                            allowedCountries = targetAllowedCountries,
                            includeVodAndSeries = true,
                            onCategoriesDiscovered = { cats ->
                                if (cats.isNotEmpty()) {
                                    channelDao.insertProviderCategories(cats)
                                }
                            },
                            onProgress = { _, detail, live, movie, series ->
                                streamLive = live
                                streamMovie = movie
                                streamSeries = series
                                val now = System.currentTimeMillis()
                                if (now - lastNoticeTime > 750) {
                                    lastNoticeTime = now
                                    val countInfo = if (totalSaved > 0) " ($totalSaved Einträge gespeichert)" else ""
                                    updatePlaylistNoticeProgress("$detail$countInfo")
                                    _statusMessage.value = "$detail$countInfo"
                                }
                            },
                            onBatchParsed = { batch, isDachBatch ->
                                if (batch.isNotEmpty()) {
                                    channelDao.insertChannelsBatch(batch)
                                    totalSaved += batch.size
                                    if (isDachBatch) {
                                        dachSaved += batch.size
                                        if (!hasEmittedDachReady && dachSaved >= 50) {
                                            hasEmittedDachReady = true
                                            _statusMessage.value = "DE/AT/CH/ADULT bereit ($dachSaved Sender). Speichere Katalog..."
                                            updatePlaylistNoticeProgress("DE/AT/CH/ADULT bereit ($dachSaved Sender). Speichere Katalog...")
                                            if (_allChannels.value.isEmpty()) {
                                                _allChannels.value = batch
                                                _filteredLiveChannels.value = batch.filter { it.type == ContentType.LIVE_TV }
                                            }
                                        }
                                    } else {
                                        val now = System.currentTimeMillis()
                                        if (now - lastNoticeTime > 750) {
                                            lastNoticeTime = now
                                            val progressMsg = "Speichere Einträge: $totalSaved..."
                                            updatePlaylistNoticeProgress(progressMsg)
                                            _statusMessage.value = progressMsg
                                        }
                                    }
                                    kotlinx.coroutines.yield()
                                }
                            }
                        )
                        result.onSuccess { channels ->
                            if (totalSaved == 0 && channels.isNotEmpty()) {
                                val (dach, others) = channels.partition { it.isDach || it.countryCode in listOf("DE", "AT", "CH", "ADULT") }
                                if (dach.isNotEmpty()) {
                                    channelDao.insertChannelsBatch(dach)
                                    dachSaved += dach.size
                                    totalSaved += dach.size
                                    updatePlaylistNoticeProgress("Speichere DACH: ${dach.size}...")
                                    if (_allChannels.value.isEmpty()) {
                                        _allChannels.value = dach
                                    }
                                    kotlinx.coroutines.yield()
                                }
                                if (others.isNotEmpty()) {
                                    var lastOtherTime = 0L
                                    others.chunked(2500).forEach { chunk ->
                                        channelDao.insertChannelsBatch(chunk)
                                        totalSaved += chunk.size
                                        val now = System.currentTimeMillis()
                                        if (now - lastOtherTime > 750 || totalSaved == channels.size) {
                                            lastOtherTime = now
                                            val msg = "Speichere $totalSaved von ${channels.size} Einträgen..."
                                            updatePlaylistNoticeProgress(msg)
                                            _statusMessage.value = msg
                                        }
                                        kotlinx.coroutines.yield()
                                    }
                                }
                            }

                            if (syncOnlyGerman) {
                                channelDao.deleteInternationalChannels(playlist.id)
                            }
                            val finalCount = if (totalSaved > 0) totalSaved else channels.size
                            updatePlaylistCountsFromDb(
                                playlistId = playlist.id,
                                knownLive = if (streamLive > 0) streamLive else null,
                                knownRadio = 0,
                                knownMovies = if (streamMovie > 0) streamMovie else null,
                                knownSeries = if (streamSeries > 0) streamSeries else null
                            )
                            val initialDach = channelDao.getDachChannelsByPlaylistSync(playlist.id)
                            applyGeoCheckAndVpnHint(playlist, initialDach)
                            _statusMessage.value = "Xtream Playlist '${playlist.name}' geladen ($finalCount Einträge)"
                            updatePlaylistNoticeProgress("Fertig: $finalCount Einträge gespeichert!")
                            finalizePlaylistUpdateNotice(
                                playlistId = playlist.id,
                                playlistName = playlist.name,
                                knownGermanCount = if (dachSaved > 0) dachSaved else initialDach.size,
                                knownTotalCount = finalCount
                            )
                        }.onFailure { err ->
                            hadError = true
                            errorMessage = err.message
                            _statusMessage.value = "Xtream Fehler: ${err.message}"
                            applyGeoCheckAndVpnHint(playlist, emptyList(), errorMessage = err.message)
                        }
                    }
                    PlaylistType.MAC_PORTAL -> {
                        updatePlaylistNoticeProgress("Verbinde mit MAC Portal...")
                        _macPortalDialogState.value = MacPortalConnectionDialogState(
                            isVisible = true,
                            playlistName = playlist.name,
                            portalUrl = playlist.url,
                            macAddress = playlist.macAddress,
                            playlistId = playlist.id,
                            stepTitle = "Verbinde mit MAC Portal...",
                            stepDetail = "Prüfe Portal-URL und Authentifizierung...",
                            liveCount = 0,
                            movieCount = 0,
                            seriesCount = 0,
                            isLoading = true,
                            isFinished = false,
                            isTimeoutOrRejected = false,
                            errorMessage = null
                        )
                        var macTotalSaved = 0
                        var macDachSaved = 0
                        var macEmittedDach = false
                        channelDao.deleteChannelsByPlaylist(playlist.id)
                        channelDao.deleteProviderCategoriesByPlaylist(playlist.id)

                        val result = macClient.fetchMacPortalChannels(
                            portalUrl = playlist.url,
                            macAddress = playlist.macAddress,
                            playlistId = playlist.id,
                            syncOnlyGerman = syncOnlyGerman,
                            allowedCountries = targetAllowedCountries,
                            includeVodAndSeries = true,
                            onCategoriesDiscovered = { cats ->
                                if (cats.isNotEmpty()) {
                                    channelDao.insertProviderCategories(cats)
                                }
                            },
                            onProgress = { progress ->
                                _macPortalDialogState.value = _macPortalDialogState.value?.copy(
                                    stepTitle = progress.step,
                                    stepDetail = progress.detail,
                                    liveCount = progress.liveCount,
                                    movieCount = progress.movieCount,
                                    seriesCount = progress.seriesCount,
                                    isLoading = !progress.isComplete && !progress.isTimeoutOrRejected,
                                    isFinished = progress.isComplete,
                                    isTimeoutOrRejected = progress.isTimeoutOrRejected,
                                    errorMessage = progress.errorMessage
                                )
                                updatePlaylistNoticeProgress("${progress.step}: ${progress.detail}")
                            },
                            onBatchParsed = { batch, isDachBatch ->
                                if (batch.isNotEmpty()) {
                                    channelDao.insertChannelsBatch(batch)
                                    macTotalSaved += batch.size
                                    if (isDachBatch) {
                                        macDachSaved += batch.size
                                        if (!macEmittedDach && macDachSaved >= 30) {
                                            macEmittedDach = true
                                            if (_allChannels.value.isEmpty()) {
                                                _allChannels.value = batch
                                                _filteredLiveChannels.value = batch.filter { it.type == ContentType.LIVE_TV }
                                            }
                                        }
                                    }
                                    kotlinx.coroutines.yield()
                                }
                            }
                        )
                        result.onSuccess { channels ->
                            if (macTotalSaved > 0 || channels.isNotEmpty()) {
                                if (macTotalSaved == 0 && channels.isNotEmpty()) {
                                    updatePlaylistNoticeProgress("Speichere ${channels.size} Sender und prüfe deutsche Streams...")
                                    channelDao.insertChannelsBatch(channels)
                                    macTotalSaved = channels.size
                                }
                                if (syncOnlyGerman) {
                                    channelDao.deleteInternationalChannels(playlist.id)
                                }
                                updatePlaylistCountsFromDb(playlist.id)
                                val initialDach = channelDao.getDachChannelsByPlaylistSync(playlist.id)
                                applyGeoCheckAndVpnHint(playlist, initialDach)
                                val totalInDb = channelDao.getChannelCountByPlaylist(playlist.id)
                                _statusMessage.value = "MAC Portal '${playlist.name}' synchronisiert ($totalInDb Einträge)"
                                _macPortalDialogState.value = _macPortalDialogState.value?.copy(
                                    stepTitle = "Import abgeschlossen!",
                                    stepDetail = "$totalInDb Einträge erfolgreich empfangen",
                                    isLoading = false,
                                    isFinished = true,
                                    isTimeoutOrRejected = false
                                )
                            } else {
                                _statusMessage.value = "MAC Portal verbunden, aber keine Inhalte empfangen."
                                applyGeoCheckAndVpnHint(playlist, emptyList(), errorMessage = "Keine Inhalte")
                                _macPortalDialogState.value = _macPortalDialogState.value?.copy(
                                    stepTitle = "Keine Inhalte empfangen",
                                    stepDetail = "Das Portal lieferte keine TV-Sender oder VOD-Inhalte.",
                                    isLoading = false,
                                    isFinished = false,
                                    isTimeoutOrRejected = true,
                                    errorMessage = "Keine Inhalte empfangen"
                                )
                            }
                        }.onFailure { err ->
                            hadError = true
                            errorMessage = err.message
                            _statusMessage.value = "MAC Portal Fehler: ${err.message}"
                            applyGeoCheckAndVpnHint(playlist, emptyList(), errorMessage = err.message)
                            val isTimeout = err.message?.contains("Timeout", ignoreCase = true) == true ||
                                            err.message?.contains("15", ignoreCase = true) == true ||
                                            err.message?.contains("abgewiesen", ignoreCase = true) == true
                            _macPortalDialogState.value = _macPortalDialogState.value?.copy(
                                stepTitle = if (isTimeout) "Portal-Abweisung / Timeout (15s)" else "Portal-Verbindungsfehler",
                                stepDetail = err.message ?: "Verbindung zum MAC Portal fehlgeschlagen",
                                isLoading = false,
                                isFinished = false,
                                isTimeoutOrRejected = true,
                                errorMessage = err.message
                            )
                        }
                    }
                    PlaylistType.M3U_FILE -> {
                        val stream = LocalM3uFolderScanner.openLocalPlaylistInputStream(
                            getApplication(),
                            playlist
                        )
                        if (stream != null) {
                            val parsed = M3UParser.parse(stream, playlist.id, syncOnlyGerman)
                            if (parsed.isNotEmpty()) {
                                channelDao.deleteChannelsByPlaylist(playlist.id)
                                channelDao.insertChannels(parsed)
                                if (syncOnlyGerman) {
                                    channelDao.deleteInternationalChannels(playlist.id)
                                }
                                val finalChannels = if (syncOnlyGerman) {
                                    channelDao.getChannelsByPlaylistSync(playlist.id)
                                } else {
                                    parsed
                                }
                                updatePlaylistCounts(playlist.id, finalChannels)
                                _statusMessage.value = "✅ ${playlist.name}: ${finalChannels.size} Sender geladen"
                            } else {
                                _statusMessage.value = "Lokale M3U Playlist bereit (${playlist.name})"
                            }
                        } else {
                            _statusMessage.value = "Lokale M3U Playlist bereit (${playlist.name})"
                        }
                    }
                }
            } catch (e: Exception) {
                hadError = true
                errorMessage = e.message
                _statusMessage.value = "Fehler beim Aktualisieren: ${e.message}"
                applyGeoCheckAndVpnHint(playlist, emptyList(), errorMessage = e.message)
            } finally {
                _isRefreshing.value = false
                if (hadError && errorMessage != null) {
                    failPlaylistUpdateNotice(playlist.name, errorMessage ?: "Fehler beim Aktualisieren")
                } else if (!_playlistUpdateNotice.value.isReady) {
                    finalizePlaylistUpdateNotice(playlist.id, playlist.name)
                }
                // Safely load active playlist channels (DACH first) now that insertion is complete
                loadChannelsForPlaylist(playlist.id)
            }
        }
    }

    private suspend fun updatePlaylistCountsFromDb(
        playlistId: String,
        knownLive: Int? = null,
        knownRadio: Int? = null,
        knownMovies: Int? = null,
        knownSeries: Int? = null
    ) {
        if (knownLive != null && knownMovies != null && knownSeries != null) {
            playlistDao.updatePlaylistStatistics(
                id = playlistId,
                live = knownLive,
                radio = knownRadio ?: 0,
                movies = knownMovies,
                series = knownSeries,
                lastUpdated = System.currentTimeMillis()
            )
            return
        }
        val typeCounts = channelDao.getTypeCounts(playlistId)
        var live = 0
        var radio = 0
        var movies = 0
        var series = 0
        for (tc in typeCounts) {
            when (tc.type) {
                ContentType.LIVE_TV -> live = tc.count
                ContentType.RADIO -> radio = tc.count
                ContentType.MOVIE -> movies = tc.count
                ContentType.SERIES -> series = tc.count
                else -> {}
            }
        }
        playlistDao.updatePlaylistStatistics(
            id = playlistId,
            live = live,
            radio = radio,
            movies = movies,
            series = series,
            lastUpdated = System.currentTimeMillis()
        )
    }

    private suspend fun updatePlaylistCounts(playlistId: String, channels: List<ChannelItem>) {
        updatePlaylistCountsFromDb(playlistId)
    }

    fun playContent(channel: ChannelItem, startPositionMs: Long = 0L) {
        _currentlyPlaying.value = channel
        _isPlayerFullscreen.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val streamUrl = channel.streamUrl.trim()
            val isLocalFile = streamUrl.startsWith("/") ||
                streamUrl.startsWith("file://", ignoreCase = true) ||
                streamUrl.startsWith("content://", ignoreCase = true) ||
                (File(streamUrl).exists() && File(streamUrl).isFile)

            if (isLocalFile || channel.playlistId == "recordings") {
                withContext(Dispatchers.Main) {
                    playerManager.setPortalOccupied(false)
                    _vpnHintNotice.value = null
                    val currentSettings = _settings.value
                    if (currentSettings.preferredPlayer == PlayerEngine.BUILT_IN_NEXUS) {
                        _isPlayerFullscreen.value = true
                        playerManager.playStream(
                            url = streamUrl,
                            cachingMode = currentSettings.cachingMode,
                            backgroundPlayback = false,
                            title = channel.name,
                            subtitle = if (channel.groupTitle.isNotBlank()) channel.groupTitle else "Aufnahme / Live-Mitschnitt",
                            countryCode = channel.countryCode,
                            channel = channel,
                            startPositionMs = startPositionMs
                        )
                    } else {
                        _isPlayerFullscreen.value = false
                        PlayerManager.launchExternalPlayer(
                            getApplication(),
                            currentSettings.preferredPlayer,
                            streamUrl,
                            channel.name
                        )
                    }
                }
                return@launch
            }

            if (channel.playlistId == com.example.data.livetvlight.LIVE_TV_LIGHT_PLAYLIST_ID) {
                val rawId = channel.id.removePrefix("ltv_light_")
                val catalogMatch = com.example.data.livetvlight.LiveTvLightCatalog.findById(rawId)
                val playableUrl = when {
                    catalogMatch == null -> streamUrl
                    com.example.data.livetvlight.LiveTvLightStreamResolver.isAlreadyPlayableWithoutResolve(
                        catalogMatch,
                        streamUrl
                    ) -> streamUrl
                    else -> com.example.data.livetvlight.LiveTvLightStreamResolver.resolvePlayableStream(
                        getApplication(),
                        catalogMatch
                    )
                }
                val resolvedCountry = catalogMatch?.countryCode ?: channel.countryCode.ifBlank { "DE" }
                val updatedChannel = channel.copy(
                    streamUrl = playableUrl,
                    countryCode = resolvedCountry
                )
                withContext(Dispatchers.Main) {
                    _currentlyPlaying.value = updatedChannel
                    playerManager.setPortalOccupied(false)
                    _vpnHintNotice.value = null
                    val currentSettings = _settings.value
                    if (currentSettings.preferredPlayer == PlayerEngine.BUILT_IN_NEXUS) {
                        _isPlayerFullscreen.value = true
                        playerManager.playStream(
                            url = playableUrl,
                            cachingMode = currentSettings.cachingMode,
                            backgroundPlayback = currentSettings.backgroundAudioPlayback,
                            title = updatedChannel.name,
                            subtitle = updatedChannel.groupTitle.ifBlank {
                                if (resolvedCountry == "AT") "Live TV Österreich" else "Live TV Deutschland"
                            },
                            countryCode = resolvedCountry,
                            channel = updatedChannel,
                            startPositionMs = 0L
                        )
                    } else {
                        _isPlayerFullscreen.value = false
                        PlayerManager.launchExternalPlayer(
                            getApplication(),
                            currentSettings.preferredPlayer,
                            playableUrl,
                            updatedChannel.name
                        )
                    }
                }
                return@launch
            }

            val activePl = playlists.value.find { it.id == channel.playlistId }
                ?: playlists.value.find { it.id == _activePlaylistId.value }
                ?: playlists.value.firstOrNull { it.isActive }
            val mac = activePl?.macAddress ?: ""

            withContext(Dispatchers.Main) {
                playerManager.setPortalOccupied(false)
            }

            // Check MAC portal create_link resolution and occupancy
            val portalResult = macClient.resolveStreamResult(
                streamUrl = channel.streamUrl,
                macAddress = mac,
                countryCode = channel.countryCode,
                channelName = channel.name
            )

            if (portalResult.isOccupied) {
                withContext(Dispatchers.Main) {
                    val occupiedMsg = portalResult.errorMessage
                        ?: "Portal belegt: Diese MAC-Adresse ($mac) wird derzeit bereits auf einem anderen Gerät genutzt."
                    playerManager.setPortalOccupied(true, occupiedMsg)
                    _statusMessage.value = "⚠️ $occupiedMsg"

                    val code = channel.countryCode ?: com.example.data.parser.VpnRequirementHelper.extractCountryCode(activePl?.name ?: "") ?: "DE"
                    val countryName = com.example.data.parser.VpnRequirementHelper.getCountryDisplayName(code) ?: "Deutschland"
                    val hint = "🛡️ VPN-Hinweis: Server blockiert Verbindung • Eventuell VPN für $countryName [$code] erforderlich."
                    _vpnHintNotice.value = hint
                    playerManager.setVpnHintNotice(hint)
                }
                return@launch
            }

            val resolvedUrl = portalResult.streamUrl.ifBlank { channel.streamUrl }

            withContext(Dispatchers.Main) {
                _vpnHintNotice.value = null
                val currentSettings = _settings.value
                if (currentSettings.preferredPlayer == PlayerEngine.BUILT_IN_NEXUS) {
                    val currentProg = if (channel.type == ContentType.LIVE_TV || channel.type == ContentType.RADIO) {
                        FreeEpgService.getCurrentAndNextProgram(
                            channel.tvgId,
                            channel.name,
                            currentSettings.epgTimezone,
                            currentSettings.epgTimeOffsetHours
                        ).first?.title ?: if (channel.groupTitle.isNotBlank()) channel.groupTitle else "Live Stream"
                    } else {
                        channel.plot?.take(100) ?: channel.groupTitle.ifBlank { if (channel.type == ContentType.SERIES) "Serie" else "Film" }
                    }

                    // For movies/series, background audio service is disabled to prevent unnecessary CPU load and OS lifecycle crashes
                    val enableBg = currentSettings.backgroundAudioPlayback && (channel.type == ContentType.LIVE_TV || channel.type == ContentType.RADIO)

                    playerManager.playStream(
                        url = resolvedUrl,
                        cachingMode = currentSettings.cachingMode,
                        backgroundPlayback = enableBg,
                        title = channel.name,
                        subtitle = currentProg,
                        countryCode = channel.countryCode,
                        channel = channel,
                        startPositionMs = startPositionMs
                    )
                } else {
                    PlayerManager.launchExternalPlayer(
                        getApplication(),
                        currentSettings.preferredPlayer,
                        resolvedUrl,
                        channel.name
                    )
                }
            }
        }
    }

    fun resumeVodItem(item: VodResumeItem) {
        val targetPosMs = item.lastPositionSeconds * 1000L
        if (item.isSeries) {
            // Decouple stream URL: Bind to stable series_id and episode_id from provider
            val matched = _allChannels.value.find { it.id == item.id }
                ?: _allChannels.value.find {
                    it.type == ContentType.SERIES &&
                    (it.seriesTitle.equals(item.seriesTitle, ignoreCase = true) || it.name.contains(item.seriesTitle ?: "", ignoreCase = true)) &&
                    it.seasonNumber == item.seasonNumber &&
                    it.episodeNumber == item.episodeNumber
                }
                ?: _allChannels.value.find { it.id == item.seriesId }

            if (matched != null) {
                playContent(matched, startPositionMs = targetPosMs)
            } else {
                val synthetic = ChannelItem(
                    id = item.id,
                    playlistId = _activePlaylistId.value ?: "resume",
                    name = item.title,
                    streamUrl = item.streamUrl,
                    type = ContentType.SERIES,
                    seriesTitle = item.seriesTitle,
                    seasonNumber = item.seasonNumber,
                    episodeNumber = item.episodeNumber,
                    posterUrl = item.posterUrl,
                    durationSeconds = item.totalDurationSeconds.toInt()
                )
                playContent(synthetic, startPositionMs = targetPosMs)
            }
        } else {
            // Movie
            val matched = _allChannels.value.find { it.id == item.id }
                ?: _allChannels.value.find { it.type == ContentType.MOVIE && it.name.equals(item.mainTitle, ignoreCase = true) }

            if (matched != null) {
                playContent(matched, startPositionMs = targetPosMs)
            } else {
                val synthetic = ChannelItem(
                    id = item.id,
                    playlistId = _activePlaylistId.value ?: "resume",
                    name = item.mainTitle,
                    streamUrl = item.streamUrl,
                    type = ContentType.MOVIE,
                    posterUrl = item.posterUrl,
                    durationSeconds = item.totalDurationSeconds.toInt()
                )
                playContent(synthetic, startPositionMs = targetPosMs)
            }
        }
    }

    fun deleteVodResumeItem(item: VodResumeItem) {
        VodWatchHistoryManager.deleteHistoryItem(getApplication(), item.isSeries, item.id)
    }

    fun clearAllVodHistory() {
        VodWatchHistoryManager.clearAllHistory(getApplication())
    }

    fun playStreamDirect(url: String, title: String, groupTitle: String = "Web Stream") {
        val tempChannel = ChannelItem(
            playlistId = _activePlaylistId.value ?: "web_streams",
            name = title,
            streamUrl = url,
            groupTitle = groupTitle,
            type = ContentType.LIVE_TV
        )
        playContent(tempChannel)
    }

    fun updatePlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            playlistDao.updatePlaylist(playlist)
            _statusMessage.value = "Playliste '${playlist.name}' aktualisiert. Lade Inhalte neu..."
            refreshPlaylist(playlist)
        }
    }

    fun closePlayer() {
        _isPlayerFullscreen.value = false
        playerManager.saveCurrentProgressNow()
        val current = _currentlyPlaying.value
        val isRecordingOrVod = current?.playlistId == "recordings" || current?.type == ContentType.MOVIE || current?.type == ContentType.SERIES
        if (!_settings.value.backgroundAudioPlayback || isRecordingOrVod) {
            playerManager.pause()
        }
    }

    fun toggleFavorite(channel: ChannelItem) {
        if (channel.playlistId == com.example.data.radio.RadioStationEntity.RADIO_BROWSER_PLAYLIST_ID) {
            viewModelScope.launch(Dispatchers.IO) {
                val existing = radioBrowserRepository.findStationByChannelId(channel.id)
                if (existing != null) {
                    val isNowFav = radioBrowserRepository.toggleFavorite(existing)
                    _currentlyPlaying.value?.let { playing ->
                        if (playing.id == channel.id) {
                            _currentlyPlaying.value = playing.copy(isFavorite = isNowFav)
                        }
                    }
                    _statusMessage.value = if (isNowFav) {
                        "⭐ '${channel.name}' zu Radio-Favoriten hinzugefügt"
                    } else {
                        "☆ '${channel.name}' aus Radio-Favoriten entfernt"
                    }
                }
            }
            return
        }
        val willBeFavorite = !channel.isFavorite
        _allChannels.value = _allChannels.value.map {
            if (it.id == channel.id) it.copy(isFavorite = willBeFavorite) else it
        }
        triggerFilterRecompute()
        viewModelScope.launch(Dispatchers.IO) {
            channelDao.toggleFavorite(channel.id, willBeFavorite)
            _statusMessage.value = if (willBeFavorite) {
                "❤️ '${channel.name}' zu Favoriten hinzugefügt"
            } else {
                "💔 '${channel.name}' aus Favoriten entfernt"
            }
        }
    }

    fun addM3uUrlPlaylist(name: String, url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val extractedBatch = if (url.contains("\n") || url.count { it == ':' } > 3) {
                com.example.data.parser.PlaylistImportExtractor.extractFromText(url, name.ifBlank { "M3U" })
            } else emptyList()
            if (extractedBatch.size > 1) {
                importCandidatesBatch(extractedBatch)
                return@launch
            }

            _isRefreshing.value = true
            _statusMessage.value = "Lade M3U Playlist herunter..."
            val (server, user, pass) = extractCredentialsFromUrl(url)
            val geo = com.example.data.parser.VpnRequirementHelper.performGeoCheck(url = url, name = name)
            val finalName = com.example.data.parser.VpnRequirementHelper.formatPlaylistNameWithVpn(
                name = name,
                requiresVpn = geo.requiresVpn,
                countryCode = geo.countryCode
            )
            val newPl = PlaylistEntity(
                name = finalName,
                type = PlaylistType.M3U_URL,
                url = url,
                username = user,
                password = pass
            )
            playlistDao.insertPlaylist(newPl)
            _activePlaylistId.value = newPl.id
            refreshPlaylist(newPl)
        }
    }

    private fun extractCredentialsFromUrl(url: String): Triple<String, String, String> {
        return try {
            val uri = Uri.parse(url)
            val user = uri.getQueryParameter("username") ?: uri.getQueryParameter("user") ?: ""
            val pass = uri.getQueryParameter("password") ?: uri.getQueryParameter("pass") ?: ""
            val scheme = uri.scheme ?: "http"
            val host = uri.host ?: ""
            val port = if (uri.port != -1) ":${uri.port}" else ""
            val server = if (host.isNotBlank()) "$scheme://$host$port" else ""
            Triple(server, user, pass)
        } catch (e: Exception) {
            Triple("", "", "")
        }
    }

    private fun extractCredentialsFromUrlOrEntity(playlist: PlaylistEntity): Triple<String, String, String> {
        val (serverFromUrl, userFromUrl, passFromUrl) = extractCredentialsFromUrl(playlist.url)
        val user = if (playlist.username.isNotBlank()) playlist.username else userFromUrl
        val pass = if (playlist.password.isNotBlank()) playlist.password else passFromUrl
        val server = if (serverFromUrl.isNotBlank()) {
            serverFromUrl
        } else if (playlist.url.startsWith("http://") || playlist.url.startsWith("https://")) {
            val idx = playlist.url.indexOf("/", 8)
            if (idx != -1) playlist.url.substring(0, idx) else playlist.url
        } else ""
        return Triple(server, user, pass)
    }

    fun addXtreamPlaylist(name: String, serverUrl: String, user: String, pass: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val extractedBatch = if (serverUrl.contains("\n") || user.contains("\n")) {
                com.example.data.parser.PlaylistImportExtractor.extractFromText("$serverUrl\n$user\n$pass", name.ifBlank { "Xtream" })
            } else emptyList()
            if (extractedBatch.size > 1) {
                importCandidatesBatch(extractedBatch)
                return@launch
            }

            _isRefreshing.value = true
            _statusMessage.value = "Verbinde mit Xtream Server..."
            val geo = com.example.data.parser.VpnRequirementHelper.performGeoCheck(url = serverUrl, name = name)
            val finalName = com.example.data.parser.VpnRequirementHelper.formatPlaylistNameWithVpn(
                name = name,
                requiresVpn = geo.requiresVpn,
                countryCode = geo.countryCode
            )
            val newPl = PlaylistEntity(
                name = finalName,
                type = PlaylistType.XTREAM_CODES,
                url = serverUrl,
                username = user,
                password = pass
            )
            playlistDao.insertPlaylist(newPl)
            _activePlaylistId.value = newPl.id
            refreshPlaylist(newPl)
        }
    }

    fun addMacPortalPlaylist(name: String, portalUrl: String, mac: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val extractedBatch = if (portalUrl.contains("\n") || mac.contains("\n") || mac.length > 20) {
                com.example.data.parser.PlaylistImportExtractor.extractFromText("$portalUrl\n$mac", name.ifBlank { "MAC" })
            } else emptyList()
            if (extractedBatch.size > 1) {
                importCandidatesBatch(extractedBatch)
                return@launch
            }

            _isRefreshing.value = true
            _statusMessage.value = "Verbinde mit MAC Portal..."
            val formattedBaseName = formatPlaylistDisplayName(name, PlaylistType.MAC_PORTAL)
            val geo = com.example.data.parser.VpnRequirementHelper.performGeoCheck(url = portalUrl, name = formattedBaseName)
            val finalName = com.example.data.parser.VpnRequirementHelper.formatPlaylistNameWithVpn(
                name = formattedBaseName,
                requiresVpn = geo.requiresVpn,
                countryCode = geo.countryCode
            )
            val newPl = PlaylistEntity(
                name = finalName,
                type = PlaylistType.MAC_PORTAL,
                url = portalUrl,
                macAddress = mac
            )
            playlistDao.insertPlaylist(newPl)
            _activePlaylistId.value = newPl.id

            // Show Verbinde mit MAC Portal Dialog immediately
            _macPortalDialogState.value = MacPortalConnectionDialogState(
                isVisible = true,
                playlistName = finalName,
                portalUrl = portalUrl,
                macAddress = mac,
                playlistId = newPl.id,
                stepTitle = "Verbinde mit MAC Portal...",
                stepDetail = "Prüfe Portal-URL und Authentifizierung...",
                liveCount = 0,
                movieCount = 0,
                seriesCount = 0,
                isLoading = true,
                isFinished = false,
                isTimeoutOrRejected = false,
                errorMessage = null
            )

            refreshPlaylist(newPl)
        }
    }

    private val _localM3uScanResult = MutableStateFlow<LocalM3uScanResult?>(null)
    val localM3uScanResult: StateFlow<LocalM3uScanResult?> = _localM3uScanResult.asStateFlow()

    private val _isScanningLocalM3u = MutableStateFlow(false)
    val isScanningLocalM3u: StateFlow<Boolean> = _isScanningLocalM3u.asStateFlow()

    private val _isImportingLocalM3u = MutableStateFlow(false)
    val isImportingLocalM3u: StateFlow<Boolean> = _isImportingLocalM3u.asStateFlow()

    private val _localM3uImportProgress = MutableStateFlow("")
    val localM3uImportProgress: StateFlow<String> = _localM3uImportProgress.asStateFlow()

    fun addLocalM3uFile(name: String, fileUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            val syncOnlyGerman = _settings.value.language == "de" && _settings.value.syncOnlyGermanWhenGerman
            try {
                LocalM3uFolderScanner.takePersistableReadPermissionSafely(getApplication(), fileUri)
                val existing = playlistDao.getAllPlaylistsSync()
                val cleanExactName = name.trim().ifBlank {
                    fileUri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "local.m3u"
                }
                val normTarget = LocalM3uFolderScanner.normalizeM3uName(cleanExactName)
                val duplicate = existing.firstOrNull {
                    it.type == PlaylistType.M3U_FILE && (
                        it.url == fileUri.toString() ||
                            LocalM3uFolderScanner.normalizeM3uName(it.name) == normTarget
                        )
                }
                if (duplicate != null) {
                    _statusMessage.value = "⚠️ Doppelte Datei übersprungen: „${duplicate.name}“ ist bereits vorhanden."
                    return@launch
                }

                val newPl = PlaylistEntity(
                    name = cleanExactName,
                    type = PlaylistType.M3U_FILE,
                    url = fileUri.toString()
                )
                LocalM3uFolderScanner.cacheM3uContentInternally(getApplication(), newPl.id, fileUri)
                val inputStream = LocalM3uFolderScanner.openLocalPlaylistInputStream(getApplication(), newPl)
                if (inputStream != null) {
                    playlistDao.insertPlaylist(newPl)
                    playlistDao.setActivePlaylist(newPl.id)
                    _activePlaylistId.value = newPl.id
                    val parsed = M3UParser.parse(inputStream, newPl.id, syncOnlyGerman)
                    if (parsed.isNotEmpty()) {
                        channelDao.insertChannels(parsed)
                        if (syncOnlyGerman) {
                            channelDao.deleteInternationalChannels(newPl.id)
                        }
                        val finalChannels = if (syncOnlyGerman) {
                            channelDao.getChannelsByPlaylistSync(newPl.id)
                        } else {
                            parsed
                        }
                        updatePlaylistCounts(newPl.id, finalChannels)
                        loadChannelsForPlaylist(newPl.id)
                        _statusMessage.value = "✅ „$cleanExactName“ (${finalChannels.size} Sender) hinzugefügt!"
                    } else {
                        _statusMessage.value = "✅ „$cleanExactName“ hinzugefügt."
                    }
                } else {
                    _statusMessage.value = "Datei konnte nicht geöffnet werden: $cleanExactName"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Fehler beim Lesen der Datei: ${e.message}"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun scanLocalM3uFolder(treeUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _isScanningLocalM3u.value = true
            try {
                val prefs = getApplication<Application>().getSharedPreferences("local_m3u_prefs", Context.MODE_PRIVATE)
                prefs.edit().putString("last_folder_uri", treeUri.toString()).apply()

                val existing = playlistDao.getAllPlaylistsSync()
                val result = LocalM3uFolderScanner.scanFolderUri(
                    context = getApplication(),
                    treeUri = treeUri,
                    existingPlaylists = existing
                )
                _localM3uScanResult.value = result
                _statusMessage.value = if (result.files.isEmpty()) {
                    "Keine M3U/M3U8-Dateien im Ordner „${result.folderName}“ gefunden."
                } else {
                    "Ordner „${result.folderName}“: ${result.files.size} M3U(s) gefunden (${result.newCount} neu, ${result.duplicateCount} doppelt)."
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error scanning local M3U folder", e)
                _statusMessage.value = "Fehler beim Ordner-Scan: ${e.message}"
            } finally {
                _isScanningLocalM3u.value = false
            }
        }
    }

    fun scanLocalM3uFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            _isScanningLocalM3u.value = true
            try {
                val existing = playlistDao.getAllPlaylistsSync()
                val result = LocalM3uFolderScanner.scanDocumentUris(
                    context = getApplication(),
                    uris = uris,
                    existingPlaylists = existing
                )
                _localM3uScanResult.value = result
                _statusMessage.value = if (result.files.isEmpty()) {
                    "Keine gültigen M3U-Dateien in der Auswahl gefunden."
                } else {
                    "${result.files.size} M3U-Datei(en) geprüft: ${result.newCount} neu, ${result.duplicateCount} doppelt."
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error scanning selected M3U files", e)
                _statusMessage.value = "Fehler bei der Dateiprüfung: ${e.message}"
            } finally {
                _isScanningLocalM3u.value = false
            }
        }
    }

    fun scanStandardLocalM3uFolders() {
        viewModelScope.launch(Dispatchers.IO) {
            _isScanningLocalM3u.value = true
            try {
                val existing = playlistDao.getAllPlaylistsSync()
                val result = LocalM3uFolderScanner.scanStandardLocalFolders(
                    context = getApplication(),
                    existingPlaylists = existing
                )
                _localM3uScanResult.value = result
                _statusMessage.value = if (result.files.isEmpty()) {
                    "Keine M3U-Dateien im lokalen Download-/Dokumente-Ordner gefunden. Bitte Ordner über Dateimanager wählen."
                } else {
                    "${result.files.size} M3U(s) gefunden (${result.newCount} neu, ${result.duplicateCount} doppelt)."
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error scanning standard folders", e)
                _statusMessage.value = "Fehler beim Scan: ${e.message}"
            } finally {
                _isScanningLocalM3u.value = false
            }
        }
    }

    fun rescanLastLocalM3uFolder() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentUri = _localM3uScanResult.value?.folderUri
            if (currentUri != null) {
                scanLocalM3uFolder(currentUri)
                return@launch
            }
            val prefs = getApplication<Application>().getSharedPreferences("local_m3u_prefs", Context.MODE_PRIVATE)
            val savedUriStr = prefs.getString("last_folder_uri", null)
            if (!savedUriStr.isNullOrBlank()) {
                scanLocalM3uFolder(Uri.parse(savedUriStr))
            } else {
                scanStandardLocalM3uFolders()
            }
        }
    }

    fun toggleDiscoveredLocalM3uSelection(fileId: String) {
        val current = _localM3uScanResult.value ?: return
        val updatedFiles = current.files.map { file ->
            if (file.id == fileId) file.copy(isSelected = !file.isSelected) else file
        }
        _localM3uScanResult.value = current.copy(files = updatedFiles)
    }

    fun setAllDiscoveredLocalM3uSelection(selectAll: Boolean, onlyNewFiles: Boolean = true) {
        val current = _localM3uScanResult.value ?: return
        val updatedFiles = current.files.map { file ->
            if (!selectAll) {
                file.copy(isSelected = false)
            } else if (onlyNewFiles) {
                file.copy(isSelected = file.duplicateStatus == LocalM3uDuplicateStatus.NEW)
            } else {
                file.copy(isSelected = true)
            }
        }
        _localM3uScanResult.value = current.copy(files = updatedFiles)
    }

    fun importLocalM3uFilesBatch(
        filesToImport: List<DiscoveredLocalM3uFile>,
        skipDuplicates: Boolean = true
    ) {
        if (filesToImport.isEmpty()) {
            _statusMessage.value = "Keine M3U-Dateien zum Hinzufügen ausgewählt."
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _isImportingLocalM3u.value = true
            val syncOnlyGerman = _settings.value.language == "de" && _settings.value.syncOnlyGermanWhenGerman
            var addedCount = 0
            var skippedDuplicatesCount = 0
            var totalChannelsImported = 0
            var firstAddedPlaylistId: String? = null

            try {
                val existingPlaylists = playlistDao.getAllPlaylistsSync().toMutableList()
                val seenNames = existingPlaylists.map { LocalM3uFolderScanner.normalizeM3uName(it.name) }.toMutableSet()
                val seenUris = existingPlaylists.map { it.url.trim() }.toMutableSet()
                val seenHashes = existingPlaylists
                    .filter { it.customEpgUrl.startsWith("sha256:") }
                    .map { it.customEpgUrl.removePrefix("sha256:").trim() }
                    .toMutableSet()

                for ((index, candidate) in filesToImport.withIndex()) {
                    _localM3uImportProgress.value = "Importiere (${index + 1}/${filesToImport.size}): ${candidate.fileName}"

                    val normName = LocalM3uFolderScanner.normalizeM3uName(candidate.fileName)
                    val uriStr = candidate.uri.toString()
                    val sha = candidate.contentSha256

                    val isDuplicateNow = candidate.duplicateStatus != LocalM3uDuplicateStatus.NEW ||
                        normName in seenNames ||
                        uriStr in seenUris ||
                        (sha.isNotBlank() && sha in seenHashes)

                    if (skipDuplicates && isDuplicateNow) {
                        skippedDuplicatesCount++
                        continue
                    }

                    // If user explicitly chose to overwrite an existing duplicate in the app, remove the old one first
                    if (!skipDuplicates && candidate.matchingPlaylistId != null) {
                        val oldPl = existingPlaylists.find { it.id == candidate.matchingPlaylistId }
                        if (oldPl != null) {
                            channelDao.deleteChannelsByPlaylist(oldPl.id)
                            playlistDao.deletePlaylist(oldPl)
                            LocalM3uFolderScanner.deleteCachedM3uFile(getApplication(), oldPl.id)
                            existingPlaylists.remove(oldPl)
                        }
                    }

                    // Exact M3U filename as requested ("die M3Us werden so angezeigt wie die m3u Namen lauten")
                    val exactM3uName = candidate.fileName.trim()

                    val newPl = PlaylistEntity(
                        name = exactM3uName,
                        type = PlaylistType.M3U_FILE,
                        url = uriStr,
                        customEpgUrl = if (sha.isNotBlank()) "sha256:$sha" else "",
                        lastUpdated = System.currentTimeMillis(),
                        isActive = false
                    )

                    LocalM3uFolderScanner.cacheM3uContentInternally(getApplication(), newPl.id, candidate.uri)
                    val stream = LocalM3uFolderScanner.openLocalPlaylistInputStream(getApplication(), newPl) ?: continue

                    playlistDao.insertPlaylist(newPl)
                    val parsed = M3UParser.parse(stream, newPl.id, syncOnlyGerman)
                    if (parsed.isNotEmpty()) {
                        channelDao.insertChannels(parsed)
                        if (syncOnlyGerman) {
                            channelDao.deleteInternationalChannels(newPl.id)
                        }
                        val finalChannels = if (syncOnlyGerman) {
                            channelDao.getChannelsByPlaylistSync(newPl.id)
                        } else {
                            parsed
                        }
                        updatePlaylistCounts(newPl.id, finalChannels)
                        totalChannelsImported += finalChannels.size
                    }

                    seenNames.add(normName)
                    seenUris.add(uriStr)
                    if (sha.isNotBlank()) seenHashes.add(sha)
                    if (firstAddedPlaylistId == null) {
                        firstAddedPlaylistId = newPl.id
                    }
                    addedCount++
                }

                // Activate the newly added playlist if none was active yet, or if only 1 playlist was imported
                if (firstAddedPlaylistId != null && (_activePlaylistId.value.isNullOrBlank() || addedCount == 1)) {
                    playlistDao.setActivePlaylist(firstAddedPlaylistId)
                    _activePlaylistId.value = firstAddedPlaylistId
                    loadChannelsForPlaylist(firstAddedPlaylistId)
                }

                // Refresh scan result duplicate statuses against updated DB
                val currentScan = _localM3uScanResult.value
                if (currentScan != null) {
                    val updatedDbList = playlistDao.getAllPlaylistsSync()
                    val refreshed = if (currentScan.folderUri != null) {
                        LocalM3uFolderScanner.scanFolderUri(getApplication(), currentScan.folderUri, updatedDbList)
                    } else {
                        LocalM3uFolderScanner.scanDocumentUris(
                            getApplication(),
                            currentScan.files.map { it.uri },
                            updatedDbList
                        )
                    }
                    _localM3uScanResult.value = refreshed
                }

                _statusMessage.value = when {
                    addedCount > 0 && skippedDuplicatesCount > 0 ->
                        "✅ $addedCount M3U-Playliste(n) hinzugefügt ($totalChannelsImported Sender) • $skippedDuplicatesCount Duplikat(e) übersprungen."
                    addedCount > 0 ->
                        "✅ $addedCount M3U-Playliste(n) erfolgreich hinzugefügt ($totalChannelsImported Sender)!"
                    skippedDuplicatesCount > 0 ->
                        "ℹ️ Alle $skippedDuplicatesCount ausgewählten M3U-Dateien sind bereits vorhanden (Duplikate übersprungen)."
                    else ->
                        "Keine neuen M3U-Dateien hinzugefügt."
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error importing local M3U batch", e)
                _statusMessage.value = "Fehler beim M3U-Import: ${e.message}"
            } finally {
                _localM3uImportProgress.value = ""
                _isImportingLocalM3u.value = false
            }
        }
    }

    fun importCandidatesBatch(
        candidates: List<com.example.data.parser.ImportedPlaylistCandidate>,
        rawM3uContent: String? = null,
        fileUri: Uri? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val selected = candidates.filter { it.isSelected }
            if (selected.isEmpty()) {
                _statusMessage.value = "Keine Playlisten zum Eintragen ausgewählt."
                return@launch
            }

            try {
                // O(1) HashSet deduplication against already stored playlists and within the batch
                val existingSignatures = playlists.value.asSequence().map { pl ->
                    "${pl.type.name}|${pl.url.trim().lowercase()}|${pl.username?.trim()?.lowercase().orEmpty()}|${pl.macAddress?.trim()?.uppercase().orEmpty()}"
                }.toHashSet()

                val entities = withContext(Dispatchers.Default) {
                    val result = ArrayList<PlaylistEntity>(selected.size)
                    for (candidate in selected) {
                        val entity = candidate.toPlaylistEntity().copy(
                            lastUpdated = 0L,
                            isActive = false
                        )
                        val sig = "${entity.type.name}|${entity.url.trim().lowercase()}|${entity.username?.trim()?.lowercase().orEmpty()}|${entity.macAddress?.trim()?.uppercase().orEmpty()}"
                        if (existingSignatures.add(sig)) {
                            result.add(entity)
                        }
                    }
                    result
                }

                if (entities.isEmpty()) {
                    _statusMessage.value = "ℹ️ Alle ausgewählten Playlisten sind bereits eingetragen."
                    return@launch
                }

                playlistDao.insertPlaylists(entities, chunkSize = 500)

                if (_activePlaylistId.value.isNullOrBlank()) {
                    val firstId = entities.first().id
                    playlistDao.setActivePlaylist(firstId)
                    _activePlaylistId.value = firstId
                }

                val macCount = entities.count { it.type == PlaylistType.MAC_PORTAL }
                val xtreamCount = entities.count { it.type == PlaylistType.XTREAM_CODES }
                val m3uCount = entities.count { it.type == PlaylistType.M3U_URL || it.type == PlaylistType.M3U_FILE }
                val summaryParts = mutableListOf<String>()
                if (macCount > 0) summaryParts.add("$macCount MAC Portal(e)")
                if (xtreamCount > 0) summaryParts.add("$xtreamCount Xtream Konto/Konten")
                if (m3uCount > 0) summaryParts.add("$m3uCount M3U Liste(n)")
                val detailSummary = if (summaryParts.isNotEmpty()) summaryParts.joinToString(", ") else "${entities.size} Playlisten"

                _statusMessage.value = "✅ $detailSummary eingetragen (ohne Synchronisation)."
            } catch (e: Throwable) {
                Log.e("MainViewModel", "Fehler beim Eintragen der Playlisten: ${e.message}", e)
                _statusMessage.value = "Fehler beim Eintragen der Playlisten: ${e.message}"
            }
        }
    }

    fun renameLocalM3uPlaylist(playlist: PlaylistEntity, newName: String) {
        val clean = newName.trim()
        if (clean.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            playlistDao.updatePlaylist(playlist.copy(name = clean))
            _statusMessage.value = "Playliste umbenannt in „$clean“"
        }
    }

    fun removeDuplicateLocalM3uPlaylists() {
        viewModelScope.launch(Dispatchers.IO) {
            val allLocal = playlistDao.getAllPlaylistsSync().filter { it.type == PlaylistType.M3U_FILE }
            val seenNames = HashSet<String>()
            val seenHashes = HashSet<String>()
            var removed = 0

            for (pl in allLocal) {
                val normName = LocalM3uFolderScanner.normalizeM3uName(pl.name)
                val sha = pl.customEpgUrl.removePrefix("sha256:").trim()
                val isDupName = !seenNames.add(normName)
                val isDupHash = sha.isNotBlank() && !seenHashes.add(sha)

                if (isDupName || isDupHash) {
                    channelDao.deleteChannelsByPlaylist(pl.id)
                    playlistDao.deletePlaylist(pl)
                    LocalM3uFolderScanner.deleteCachedM3uFile(getApplication(), pl.id)
                    removed++
                }
            }

            if (removed > 0) {
                val remaining = playlistDao.getAllPlaylistsSync()
                if (_activePlaylistId.value != null && remaining.none { it.id == _activePlaylistId.value }) {
                    val next = remaining.firstOrNull()
                    if (next != null) switchPlaylist(next.id)
                }
                _statusMessage.value = "✅ $removed doppelte lokale M3U-Playliste(n) entfernt."
            } else {
                _statusMessage.value = "✓ Keine doppelten lokalen M3U-Playlisten gefunden."
            }
        }
    }

    fun deletePlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            channelDao.deleteChannelsByPlaylist(playlist.id)
            playlistDao.deletePlaylist(playlist)
            if (playlist.type == PlaylistType.M3U_FILE) {
                LocalM3uFolderScanner.deleteCachedM3uFile(getApplication(), playlist.id)
            }
            val all = playlistDao.getAllPlaylistsSync()
            val nextActive = all.firstOrNull()
            if (nextActive != null) {
                switchPlaylist(nextActive.id)
            } else {
                _activePlaylistId.value = null
                _allChannels.value = emptyList()
            }
        }
    }

    fun deleteAllPlaylists() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                channelDao.deleteAllChannels()
                playlistDao.deleteAllPlaylists()
                _activePlaylistId.value = null
                _allChannels.value = emptyList()
                _filteredLiveChannels.value = emptyList()
                _filteredMovies.value = emptyList()
                _filteredSeries.value = emptyList()
                _filteredRadio.value = emptyList()
                _badgeCounts.value = ChannelBadgeCounts()
                _categories.value = emptyList()
                _countryFolders.value = emptyList()
                _currentlyPlaying.value = null
                _statusMessage.value = "Alle Playlisten und Inhalte wurden erfolgreich aus der Datenbank gelöscht."
            } catch (e: Exception) {
                Log.e("MainViewModel", "Fehler beim Löschen aller Playlisten", e)
                _statusMessage.value = "Fehler beim Löschen: ${e.message}"
            }
        }
    }

    fun updateCachingMode(mode: CachingMode) {
        _settings.value = _settings.value.copy(cachingMode = mode)
    }

    fun updatePreferredPlayer(player: PlayerEngine) {
        _settings.value = _settings.value.copy(preferredPlayer = player)
    }

    fun updateAutoUpdateOnStart(enabled: Boolean) {
        _settings.value = _settings.value.copy(autoUpdateOnStart = enabled)
    }

    fun updateNexusFullScreenEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(nexusFullScreenEnabled = enabled)
    }

    fun updateAspectRatio(ratio: String) {
        _settings.value = _settings.value.copy(aspectRatio = ratio)
    }

    fun updateLanguage(lang: String) {
        _settings.value = _settings.value.copy(language = lang)
        _activePlaylistId.value?.let { loadChannelsForPlaylist(it) }
    }

    fun updateSyncOnlyGermanWhenGerman(enabled: Boolean) {
        _settings.value = _settings.value.copy(syncOnlyGermanWhenGerman = enabled)
        _activePlaylistId.value?.let { loadChannelsForPlaylist(it) }
    }

    fun optimizeDatabasePerformance() {
        viewModelScope.launch(Dispatchers.IO) {
            _statusMessage.value = "Optimiere TV, Filme & Serien Datenbank..."
            val activeId = _activePlaylistId.value
            val isGerman = _settings.value.language == "de" && _settings.value.syncOnlyGermanWhenGerman
            try {
                var removedCount = 0
                if (activeId != null && isGerman) {
                    removedCount = channelDao.deleteInternationalChannels(activeId)
                    val remaining = channelDao.getChannelsByPlaylistSync(activeId)
                    updatePlaylistCounts(activeId, remaining)
                }
                withContext(Dispatchers.Main) {
                    _statusMessage.value = if (removedCount > 0) {
                        "Datenbank optimiert: $removedCount internationale Inhalte entfernt. Filme & Serien laden sofort!"
                    } else {
                        "Datenbank ist bereits optimal bereinigt!"
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Fehler bei Datenbank-Optimierung", e)
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Optimierung abgeschlossen."
                }
            }
        }
    }

    fun updateEpgTimezone(timezone: String) {
        _settings.value = _settings.value.copy(epgTimezone = timezone)
        refreshLiveEpgForChannels()
    }

    fun updateEpgTimeOffsetHours(offset: Int) {
        _settings.value = _settings.value.copy(epgTimeOffsetHours = offset)
        refreshLiveEpgForChannels()
    }

    fun updateProviderEpgOffset(playlistId: String, offsetHours: Int) {
        val current = _settings.value.providerEpgOffsets.toMutableMap()
        current[playlistId] = offsetHours
        _settings.value = _settings.value.copy(providerEpgOffsets = current)
        refreshLiveEpgForChannels()
    }

    fun forceEpgRemap() {
        isEpgClearedByUser = false
        viewModelScope.launch(Dispatchers.IO) {
            _statusMessage.value = "EPG-Senderzuordnung wird neu berechnet..."

            val currentProgCount = epgDao.getProgramCount()
            if (currentProgCount == 0) {
                val liveChannels = channelDao.getAllLiveChannelsSync()
                epgSyncManager.populatePreloadedSchedules(
                    channels = liveChannels,
                    timezoneId = _settings.value.epgTimezone,
                    offsetHours = _settings.value.epgTimeOffsetHours,
                    force = true
                )
            }

            val count = remapChannelsInternal()
            refreshLiveEpgForChannels()
            val totalLive = channelDao.getAllLiveChannelsSync().size
            val msg = "EPG-Senderzuordnung erfolgreich kalibriert: $count von $totalLive Sendern zugeordnet!"
            _statusMessage.value = msg
            epgSyncManager.notifySuccess(msg, epgDao.getProgramCount())
        }
    }

    fun updateCustomEpgUrl(url: String) {
        val clean = url.trim()
        _settings.value = _settings.value.copy(customEpgUrl = clean)
        settingsPrefs.edit().putString("custom_epg_url", clean).apply()
    }

    fun toggleBackgroundPlayback(enabled: Boolean) {
        _settings.value = _settings.value.copy(backgroundAudioPlayback = enabled)
    }

    fun toggleLiveRecording() {
        val current = _currentlyPlaying.value ?: return
        if (current.type == ContentType.RADIO) {
            toggleRadioRecording(current)
            return
        }
        if (_isRecordingNow.value) {
            _isRecordingNow.value = false
            liveRecordingJob?.cancel()
            liveRecordingJob = null
            val recId = currentRecordingEntityId
            val recordedFile = currentRecordingFile
            currentRecordingEntityId = null
            currentRecordingFile = null

            // Finalize concurrent live recording (single connection, playback remains active)
            val (finalFile, totalBytes) = playerManager.stopLiveRecording()
            val resultFile = finalFile ?: recordedFile

            viewModelScope.launch(Dispatchers.IO) {
                if (recId != null) {
                    val existing = recordingDao.getRecordingById(recId)
                    if (existing != null) {
                        val durationSeconds = ((System.currentTimeMillis() - existing.startTime) / 1000).coerceAtLeast(1)
                        val durationText = "${durationSeconds / 60}m ${durationSeconds % 60}s"
                        val (sizeFormatted, finalPath) = if (resultFile != null && resultFile.exists() && resultFile.length() > 0) {
                            DownloadStorageHelper.scanFileForDownloads(getApplication(), resultFile)
                            Pair(DownloadStorageHelper.formatBytes(resultFile.length()), resultFile.absolutePath)
                        } else {
                            val estMb = (durationSeconds * 0.45).toInt().coerceAtLeast(1)
                            Pair("$estMb MB", existing.filePath)
                        }
                        recordingDao.updateRecording(
                            existing.copy(
                                status = RecordingStatus.DOWNLOADED,
                                filePath = finalPath,
                                fileSizeFormatted = "$sizeFormatted • $durationText • Downloads/${DownloadStorageHelper.SUBFOLDER}"
                            )
                        )
                    }
                }
            }
            _statusMessage.value = "Aufnahme von '${current.name}' beendet & als MP4 gespeichert."
        } else {
            // Ensure playback is active if internal player is used
            if (!playerManager.isPlaying.value && _settings.value.preferredPlayer == PlayerEngine.BUILT_IN_NEXUS) {
                playerManager.playStream(
                    url = current.streamUrl,
                    title = current.name,
                    subtitle = current.groupTitle,
                    cachingMode = _settings.value.cachingMode,
                    backgroundPlayback = _settings.value.backgroundAudioPlayback
                )
            }

            _isRecordingNow.value = true
            val recId = UUID.randomUUID().toString()
            currentRecordingEntityId = recId

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val targetFile = DownloadStorageHelper.createOutputFile(
                getApplication(),
                "Aufnahme_${current.name}_$timestamp",
                "mp4"
            )
            currentRecordingFile = targetFile

            val item = RecordingItem(
                id = recId,
                title = "Live-Mitschnitt: ${current.name}",
                channelName = current.name,
                streamUrl = current.streamUrl,
                contentType = current.type,
                status = RecordingStatus.RECORDING,
                filePath = targetFile.absolutePath,
                fileSizeFormatted = "Aufnahme läuft... • Downloads/${DownloadStorageHelper.SUBFOLDER}"
            )
            viewModelScope.launch(Dispatchers.IO) {
                recordingDao.insertRecording(item)
            }

            // Start live recording directly from ExoPlayer's byte stream (1 concurrent connection!)
            val actualStartedFile = playerManager.startLiveRecording(targetFile) { bytes, durationSec ->
                viewModelScope.launch(Dispatchers.IO) {
                    val existing = recordingDao.getRecordingById(recId)
                    if (existing != null) {
                        val sizeFormatted = DownloadStorageHelper.formatBytes(bytes)
                        val durationText = "${durationSec / 60}m ${durationSec % 60}s"
                        recordingDao.updateRecording(
                            existing.copy(
                                fileSizeFormatted = "$sizeFormatted • $durationText • Live-Aufnahme"
                            )
                        )
                    }
                }
            }

            if (actualStartedFile != null) {
                currentRecordingFile = actualStartedFile
                viewModelScope.launch(Dispatchers.IO) {
                    val existing = recordingDao.getRecordingById(recId)
                    if (existing != null && existing.filePath != actualStartedFile.absolutePath) {
                        recordingDao.updateRecording(existing.copy(filePath = actualStartedFile.absolutePath))
                    }
                }
            }

            _statusMessage.value = "Aufnahme läuft: Stream wird live geschaut und parallel als MP4 aufgenommen."

            // Reliable watchdog: If LiveRecordingSink is idle after 3s (e.g. player buffer full, external player, or paused), engage direct StreamRecorderHelper
            liveRecordingJob = viewModelScope.launch(Dispatchers.IO) {
                delay(3000)
                if (_isRecordingNow.value && currentRecordingEntityId == recId) {
                    val bytesSoFar = playerManager.recordedBytes
                    if (bytesSoFar <= 0L) {
                        android.util.Log.i("MainViewModel", "LiveRecordingSink idle after 3s. Engaging direct stream recorder...")
                        playerManager.stopLiveRecording()
                        streamLiveRecordingToFile(
                            streamUrl = current.streamUrl,
                            targetFile = currentRecordingFile ?: targetFile,
                            recId = recId
                        )
                    }
                }
            }
        }
    }

    private suspend fun streamLiveRecordingToFile(
        streamUrl: String,
        targetFile: File,
        recId: String
    ) {
        val result = com.example.data.storage.StreamRecorderHelper.recordStream(
            context = getApplication(),
            streamUrl = streamUrl,
            targetFile = targetFile,
            recId = recId,
            userAgent = playerManager.resolveUserAgentForUrl(streamUrl),
            headers = playerManager.resolveHeadersForUrl(streamUrl),
            isRecordingActive = { _isRecordingNow.value && currentRecordingEntityId == recId },
            onProgressUpdate = { bytes, durationSec ->
                val existing = recordingDao.getRecordingById(recId)
                if (existing != null) {
                    val sizeFormatted = DownloadStorageHelper.formatBytes(bytes)
                    recordingDao.updateRecording(
                        existing.copy(
                            fileSizeFormatted = "$sizeFormatted • ${durationSec / 60}m ${durationSec % 60}s • Downloads/${DownloadStorageHelper.SUBFOLDER}"
                        )
                    )
                }
            }
        )

        when (result) {
            is com.example.data.storage.StreamRecorderHelper.RecordingResult.Success -> {
                val existing = recordingDao.getRecordingById(recId)
                if (existing != null) {
                    val sizeFormatted = DownloadStorageHelper.formatBytes(result.totalBytes)
                    val durationText = "${result.durationSeconds / 60}m ${result.durationSeconds % 60}s"
                    recordingDao.updateRecording(
                        existing.copy(
                            status = RecordingStatus.DOWNLOADED,
                            filePath = result.file.absolutePath,
                            fileSizeFormatted = "$sizeFormatted • $durationText • Downloads/${DownloadStorageHelper.SUBFOLDER}"
                        )
                    )
                }
            }
            is com.example.data.storage.StreamRecorderHelper.RecordingResult.Error -> {
                android.util.Log.e("MainViewModel", "Live recording stopped: ${result.message}")
                val existing = recordingDao.getRecordingById(recId)
                val target = result.file ?: targetFile
                if (existing != null && (!target.exists() || target.length() == 0L)) {
                    recordingDao.deleteRecording(existing)
                } else if (existing != null && target.exists() && target.length() > 0L) {
                    val sizeFormatted = DownloadStorageHelper.formatBytes(target.length())
                    recordingDao.updateRecording(
                        existing.copy(
                            status = RecordingStatus.DOWNLOADED,
                            filePath = target.absolutePath,
                            fileSizeFormatted = "$sizeFormatted • Downloads/${DownloadStorageHelper.SUBFOLDER}"
                        )
                    )
                }
                _isRecordingNow.value = false
                currentRecordingEntityId = null
                currentRecordingFile = null
            }
        }
    }

    private fun toggleRadioRecording(channel: ChannelItem) {
        if (_isRecordingNow.value) {
            _isRecordingNow.value = false
            _activeRadioRecordingInfo.value = null
            _statusMessage.value = "Radio-Aufnahme von '${channel.name}' wird gespeichert..."
            return
        }

        _isRecordingNow.value = true
        val recId = UUID.randomUUID().toString()
        currentRecordingEntityId = recId
        _activeRadioRecordingInfo.value = "Verbinde..."

        radioRecordingJob?.cancel()
        radioRecordingJob = viewModelScope.launch(Dispatchers.IO) {
            val matchedStation = radioBrowserRepository.findStationByChannelId(channel.id)
            val hintCodec = matchedStation?.codec ?: channel.resolutionBadge.substringBefore("•").trim()
            val hintBitrate = matchedStation?.bitrate
                ?: Regex("(\\d+)\\s*k", RegexOption.IGNORE_CASE).find(channel.resolutionBadge)?.groupValues?.get(1)?.toIntOrNull()
                ?: 0

            val initialItem = RecordingItem(
                id = recId,
                title = "Radio-Mitschnitt: ${channel.name}",
                channelName = channel.name,
                streamUrl = channel.streamUrl,
                contentType = ContentType.RADIO,
                status = RecordingStatus.RECORDING,
                filePath = "",
                fileSizeFormatted = "Radio-Aufnahme startet... • ${com.example.data.radio.RadioStreamRecorder.getDisplayRadioFolderPath()}"
            )
            recordingDao.insertRecording(initialItem)

            val result = com.example.data.radio.RadioStreamRecorder.recordRadioStream(
                context = getApplication(),
                stationName = channel.name,
                streamUrl = channel.streamUrl,
                hintCodec = hintCodec,
                hintBitrateKbps = hintBitrate,
                isRecordingActive = { _isRecordingNow.value && currentRecordingEntityId == recId },
                onProgressUpdate = { progress ->
                    val sizeText = DownloadStorageHelper.formatBytes(progress.totalBytes)
                    val durText = String.format(Locale.US, "%d:%02d", progress.durationSeconds / 60, progress.durationSeconds % 60)
                    val fmtText = progress.format.qualityDescription()
                    _activeRadioRecordingInfo.value = "$fmtText • $durText • $sizeText"
                    if (!progress.currentSongTitle.isNullOrBlank()) {
                        _activeRadioSongTitle.value = progress.currentSongTitle
                    }
                    val existing = recordingDao.getRecordingById(recId)
                    if (existing != null) {
                        recordingDao.updateRecording(
                            existing.copy(
                                fileSizeFormatted = "$fmtText • $sizeText • $durText • ${com.example.data.radio.RadioStreamRecorder.getDisplayRadioFolderPath()}"
                            )
                        )
                    }
                }
            )

            when (result) {
                is com.example.data.radio.RadioStreamRecorder.RadioRecordingResult.Success -> {
                    val sizeText = DownloadStorageHelper.formatBytes(result.totalBytes)
                    val durText = "${result.durationSeconds / 60}m ${result.durationSeconds % 60}s"
                    val fmtText = result.format.qualityDescription()
                    val existing = recordingDao.getRecordingById(recId)
                    if (existing != null) {
                        val finalTitle = if (!result.lastSongTitle.isNullOrBlank()) {
                            "Radio: ${channel.name} (${result.lastSongTitle.take(40)})"
                        } else {
                            "Radio-Mitschnitt: ${channel.name}"
                        }
                        recordingDao.updateRecording(
                            existing.copy(
                                title = finalTitle,
                                status = RecordingStatus.DOWNLOADED,
                                filePath = result.file.absolutePath,
                                fileSizeFormatted = "$fmtText • $sizeText • $durText • ${com.example.data.radio.RadioStreamRecorder.getDisplayRadioFolderPath()}"
                            )
                        )
                    }
                    _statusMessage.value = "🎵 Radio-Aufnahme gespeichert (${result.file.name}, $fmtText, $sizeText)."
                }
                is com.example.data.radio.RadioStreamRecorder.RadioRecordingResult.Error -> {
                    val existing = recordingDao.getRecordingById(recId)
                    if (existing != null) {
                        recordingDao.deleteRecording(existing)
                    }
                    _statusMessage.value = "⚠️ ${result.message}"
                }
            }

            _isRecordingNow.value = false
            _activeRadioRecordingInfo.value = null
            if (currentRecordingEntityId == recId) {
                currentRecordingEntityId = null
            }
        }
    }

    private fun resolvePlayableRecordingFile(recording: RecordingItem): String? {
        val path = recording.filePath
        if (!path.isNullOrBlank()) {
            val direct = File(path)
            if (direct.exists() && direct.length() > 0L) {
                return direct.absolutePath
            }
            val app: Application = getApplication()
            val fileName = direct.name
            val candidateDirs = mutableListOf<File>()
            candidateDirs.add(com.example.data.radio.RadioStreamRecorder.getRadioDownloadsDir(app))
            candidateDirs.add(DownloadStorageHelper.getDownloadsDir(app))
            app.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)?.let {
                candidateDirs.add(File(it, "${DownloadStorageHelper.SUBFOLDER}/${com.example.data.radio.RadioStreamRecorder.RADIO_SUBFOLDER}"))
                candidateDirs.add(File(it, DownloadStorageHelper.SUBFOLDER))
            }
            candidateDirs.add(File("/storage/emulated/0/Download/${DownloadStorageHelper.SUBFOLDER}", com.example.data.radio.RadioStreamRecorder.RADIO_SUBFOLDER))
            candidateDirs.add(File("/storage/emulated/0/Download", DownloadStorageHelper.SUBFOLDER))
            candidateDirs.add(File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), DownloadStorageHelper.SUBFOLDER))
            candidateDirs.add(File(app.filesDir, DownloadStorageHelper.SUBFOLDER))

            for (dir in candidateDirs) {
                val candidate = File(dir, fileName)
                if (candidate.exists() && candidate.length() > 0L) {
                    viewModelScope.launch(Dispatchers.IO) {
                        recordingDao.updateRecording(recording.copy(filePath = candidate.absolutePath))
                    }
                    return candidate.absolutePath
                }
            }
        }
        return null
    }

    fun playRecording(recording: RecordingItem) {
        val resolvedPath = resolvePlayableRecordingFile(recording)
        val playUrl = resolvedPath ?: recording.streamUrl
        val isOffline = resolvedPath != null

        if (playUrl.isBlank()) {
            _statusMessage.value = "⚠️ Aufnahme kann nicht abgespielt werden: Datei nicht gefunden."
            return
        }

        val tempChannel = ChannelItem(
            id = "recording_${recording.id}",
            playlistId = "recordings",
            name = recording.title,
            streamUrl = playUrl,
            groupTitle = "Downloads & Aufnahmen",
            type = if (isOffline) ContentType.MOVIE else recording.contentType
        )
        playContent(tempChannel)
        _statusMessage.value = "Wiedergabe: ${recording.title} (${if (isOffline) "Offline aus Downloads/${DownloadStorageHelper.SUBFOLDER}" else "Online-Stream"})"
    }

    private val downloadJobs = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()

    fun scheduleTimedRecording(
        channel: ChannelItem,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int
    ) {
        val now = System.currentTimeMillis()
        val startCal = java.util.Calendar.getInstance().apply {
            timeInMillis = now
            set(java.util.Calendar.HOUR_OF_DAY, startHour.coerceIn(0, 23))
            set(java.util.Calendar.MINUTE, startMinute.coerceIn(0, 59))
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }

        // If start time is within the current minute (up to 90s ago), start immediately; if further in the past, schedule for tomorrow
        val startTimeMillis = when {
            startCal.timeInMillis in (now - 90_000L)..now -> now
            startCal.timeInMillis < now - 90_000L -> {
                startCal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                startCal.timeInMillis
            }
            else -> startCal.timeInMillis
        }

        val endCal = java.util.Calendar.getInstance().apply {
            timeInMillis = startTimeMillis
            set(java.util.Calendar.HOUR_OF_DAY, endHour.coerceIn(0, 23))
            set(java.util.Calendar.MINUTE, endMinute.coerceIn(0, 59))
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        if (endCal.timeInMillis <= startTimeMillis) {
            endCal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        val endTimeMillis = endCal.timeInMillis
        val durationMinutes = ((endTimeMillis - startTimeMillis) / 60_000L).toInt().coerceAtLeast(1)

        val startStr = String.format(Locale.getDefault(), "%02d:%02d", startHour, startMinute)
        val endStr = String.format(Locale.getDefault(), "%02d:%02d", endHour, endMinute)
        val recId = UUID.randomUUID().toString()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date(startTimeMillis))
        val targetFile = DownloadStorageHelper.createOutputFile(
            getApplication(),
            "Timer_${channel.name}_$timestamp",
            "mp4"
        )

        val scheduledItem = RecordingItem(
            id = recId,
            title = "Timer: ${channel.name} ($startStr–$endStr)",
            channelName = channel.name,
            streamUrl = channel.streamUrl,
            contentType = channel.type,
            startTime = startTimeMillis,
            durationMinutes = durationMinutes,
            status = RecordingStatus.SCHEDULED,
            filePath = targetFile.absolutePath,
            progressPercent = 0,
            fileSizeFormatted = "Geplant: $startStr – $endStr Uhr ($durationMinutes Min.) • Downloads/${DownloadStorageHelper.SUBFOLDER}"
        )

        viewModelScope.launch(Dispatchers.IO) {
            recordingDao.insertRecording(scheduledItem)
            val startsNow = startTimeMillis <= System.currentTimeMillis() + 2_000L
            _statusMessage.value = if (startsNow) {
                "⏱️ Timer-Aufnahme startet jetzt bis $endStr Uhr • Downloads/${DownloadStorageHelper.SUBFOLDER}"
            } else {
                "⏱️ Aufnahme-Timer programmiert: ${channel.name} ($startStr – $endStr Uhr)"
            }

            val timerJob = launch(Dispatchers.IO) {
                try {
                    val waitMs = (startTimeMillis - System.currentTimeMillis()).coerceAtLeast(0L)
                    if (waitMs > 0L) {
                        delay(waitMs)
                    }
                    if (!isActive) return@launch

                    val activePl = playlists.value.find { it.id == channel.playlistId }
                        ?: playlists.value.find { it.id == _activePlaylistId.value }
                        ?: playlists.value.firstOrNull { it.isActive }
                    val mac = activePl?.macAddress ?: ""

                    val portalResult = macClient.resolveStreamResult(
                        streamUrl = channel.streamUrl,
                        macAddress = mac,
                        countryCode = channel.countryCode,
                        channelName = channel.name
                    )
                    val resolvedStreamUrl = portalResult.streamUrl.ifBlank { channel.streamUrl }

                    val currentDbItem = recordingDao.getRecordingById(recId) ?: return@launch
                    recordingDao.updateRecording(
                        currentDbItem.copy(
                            status = RecordingStatus.RECORDING,
                            fileSizeFormatted = "Timer-Aufnahme läuft (bis $endStr Uhr) • Downloads/${DownloadStorageHelper.SUBFOLDER}"
                        )
                    )
                    _statusMessage.value = "🔴 Timer-Aufnahme gestartet: ${channel.name} (bis $endStr Uhr)"

                    val totalDurationMs = (endTimeMillis - System.currentTimeMillis()).coerceAtLeast(5_000L)
                    val recordStartMs = System.currentTimeMillis()

                    // Check if the user is currently watching this exact channel in ExoPlayer
                    val isWatchingSameChannel = _currentlyPlaying.value?.id == channel.id && !_isRecordingNow.value
                    var usedExoTee = false
                    var actualFile = targetFile

                    if (isWatchingSameChannel) {
                        _isRecordingNow.value = true
                        currentRecordingEntityId = recId
                        currentRecordingFile = targetFile

                        val startedFile = playerManager.startLiveRecording(targetFile) { bytes, durationSec ->
                            viewModelScope.launch(Dispatchers.IO) {
                                val existing = recordingDao.getRecordingById(recId)
                                if (existing != null) {
                                    val sizeFormatted = DownloadStorageHelper.formatBytes(bytes)
                                    val pct = (((System.currentTimeMillis() - recordStartMs) * 100L) / totalDurationMs).toInt().coerceIn(1, 99)
                                    recordingDao.updateRecording(
                                        existing.copy(
                                            progressPercent = pct,
                                            fileSizeFormatted = "$sizeFormatted • ${durationSec / 60}m ${durationSec % 60}s (bis $endStr) • Downloads/${DownloadStorageHelper.SUBFOLDER}"
                                        )
                                    )
                                }
                            }
                        }
                        if (startedFile != null) {
                            actualFile = startedFile
                            currentRecordingFile = startedFile
                        }
                        delay(3000)
                        if (playerManager.recordedBytes > 0L) {
                            usedExoTee = true
                            while (isActive && System.currentTimeMillis() < endTimeMillis && _isRecordingNow.value && currentRecordingEntityId == recId) {
                                delay(1000)
                            }
                            val (stoppedFile, _) = playerManager.stopLiveRecording()
                            val finalTeeFile = stoppedFile ?: actualFile
                            _isRecordingNow.value = false
                            currentRecordingEntityId = null
                            currentRecordingFile = null

                            if (finalTeeFile.exists() && finalTeeFile.length() > 0L) {
                                DownloadStorageHelper.scanFileForDownloads(getApplication(), finalTeeFile)
                                val sizeStr = DownloadStorageHelper.formatBytes(finalTeeFile.length())
                                val finalDb = recordingDao.getRecordingById(recId)
                                if (finalDb != null) {
                                    recordingDao.updateRecording(
                                        finalDb.copy(
                                            status = RecordingStatus.DOWNLOADED,
                                            progressPercent = 100,
                                            filePath = finalTeeFile.absolutePath,
                                            fileSizeFormatted = "$sizeStr • Timer ($startStr–$endStr) • Downloads/${DownloadStorageHelper.SUBFOLDER}"
                                        )
                                    )
                                }
                                _statusMessage.value = "✅ Timer-Aufnahme beendet & gespeichert: Downloads/${DownloadStorageHelper.SUBFOLDER}/${finalTeeFile.name}"
                            }
                        } else {
                            playerManager.stopLiveRecording()
                        }
                    }

                    if (!usedExoTee && isActive && System.currentTimeMillis() < endTimeMillis) {
                        val sameChannelActive = _currentlyPlaying.value?.id == channel.id
                        if (sameChannelActive) {
                            _isRecordingNow.value = true
                            currentRecordingEntityId = recId
                            currentRecordingFile = actualFile
                        }

                        val result = com.example.data.storage.StreamRecorderHelper.recordStream(
                            context = getApplication(),
                            streamUrl = resolvedStreamUrl,
                            targetFile = actualFile,
                            recId = recId,
                            userAgent = playerManager.resolveUserAgentForUrl(resolvedStreamUrl),
                            headers = playerManager.resolveHeadersForUrl(resolvedStreamUrl),
                            isRecordingActive = {
                                isActive &&
                                    System.currentTimeMillis() < endTimeMillis &&
                                    (!sameChannelActive || (_isRecordingNow.value && currentRecordingEntityId == recId))
                            },
                            onProgressUpdate = { bytes, durationSec ->
                                val existing = recordingDao.getRecordingById(recId)
                                if (existing != null) {
                                    val sizeFormatted = DownloadStorageHelper.formatBytes(bytes)
                                    val pct = (((System.currentTimeMillis() - recordStartMs) * 100L) / totalDurationMs).toInt().coerceIn(1, 99)
                                    recordingDao.updateRecording(
                                        existing.copy(
                                            progressPercent = pct,
                                            fileSizeFormatted = "$sizeFormatted • ${durationSec / 60}m ${durationSec % 60}s (bis $endStr) • Downloads/${DownloadStorageHelper.SUBFOLDER}"
                                        )
                                    )
                                }
                            }
                        )

                        if (currentRecordingEntityId == recId) {
                            _isRecordingNow.value = false
                            currentRecordingEntityId = null
                            currentRecordingFile = null
                        }

                        when (result) {
                            is com.example.data.storage.StreamRecorderHelper.RecordingResult.Success -> {
                                val existing = recordingDao.getRecordingById(recId)
                                if (existing != null) {
                                    val sizeFormatted = DownloadStorageHelper.formatBytes(result.totalBytes)
                                    recordingDao.updateRecording(
                                        existing.copy(
                                            status = RecordingStatus.DOWNLOADED,
                                            progressPercent = 100,
                                            filePath = result.file.absolutePath,
                                            fileSizeFormatted = "$sizeFormatted • Timer ($startStr–$endStr) • Downloads/${DownloadStorageHelper.SUBFOLDER}"
                                        )
                                    )
                                }
                                _statusMessage.value = "✅ Timer-Aufnahme gespeichert: Downloads/${DownloadStorageHelper.SUBFOLDER}/${result.file.name}"
                            }
                            is com.example.data.storage.StreamRecorderHelper.RecordingResult.Error -> {
                                val existing = recordingDao.getRecordingById(recId)
                                val fileCandidate = result.file ?: actualFile
                                if (existing != null && fileCandidate.exists() && fileCandidate.length() > 0L) {
                                    val sizeFormatted = DownloadStorageHelper.formatBytes(fileCandidate.length())
                                    recordingDao.updateRecording(
                                        existing.copy(
                                            status = RecordingStatus.DOWNLOADED,
                                            progressPercent = 100,
                                            filePath = fileCandidate.absolutePath,
                                            fileSizeFormatted = "$sizeFormatted • Downloads/${DownloadStorageHelper.SUBFOLDER}"
                                        )
                                    )
                                    _statusMessage.value = "✅ Timer-Aufnahme gespeichert: Downloads/${DownloadStorageHelper.SUBFOLDER}/${fileCandidate.name}"
                                } else if (existing != null) {
                                    recordingDao.updateRecording(
                                        existing.copy(
                                            status = RecordingStatus.FAILED,
                                            fileSizeFormatted = "Timer-Fehler: ${result.message}"
                                        )
                                    )
                                    _statusMessage.value = "⚠️ Timer-Aufnahme fehlgeschlagen: ${result.message}"
                                }
                            }
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    if (currentRecordingEntityId == recId) {
                        playerManager.stopLiveRecording()
                        _isRecordingNow.value = false
                        currentRecordingEntityId = null
                        currentRecordingFile = null
                    }
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("MainViewModel", "Timer recording error: ${e.message}", e)
                    if (currentRecordingEntityId == recId) {
                        _isRecordingNow.value = false
                        currentRecordingEntityId = null
                        currentRecordingFile = null
                    }
                } finally {
                    downloadJobs.remove(recId)
                }
            }
            downloadJobs[recId] = timerJob
        }
    }

    private val vodQueueMutex = kotlinx.coroutines.sync.Mutex()
    private val vodQueueChannelMeta = java.util.concurrent.ConcurrentHashMap<String, Pair<String, String>>() // recId -> (playlistId, countryCode)

    fun downloadVodContent(channel: ChannelItem) {
        enqueueVodDownloads(listOf(channel))
    }

    /**
     * Enqueues 1..99 marked Movies or Series Episodes into the VOD Download Manager.
     * Items are registered with status QUEUED and downloaded strictly sequentially
     * one after another (always only 1 active VOD download at a time, max 99 in queue).
     */
    fun enqueueVodDownloads(channels: List<ChannelItem>) {
        if (channels.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val allCurrent = recordingDao.getAllRecordingsSync()
            val activeOrQueuedCount = allCurrent.count {
                it.status == RecordingStatus.DOWNLOADING || it.status == RecordingStatus.QUEUED
            }
            val remainingSlots = (com.example.data.model.MAX_VOD_DOWNLOAD_QUEUE_SIZE - activeOrQueuedCount).coerceAtLeast(0)
            if (remainingSlots <= 0) {
                _statusMessage.value = "⚠️ Download-Manager voll: Maximal ${com.example.data.model.MAX_VOD_DOWNLOAD_QUEUE_SIZE} Einträge gleichzeitig möglich."
                return@launch
            }

            val distinctCandidates = channels.distinctBy { it.streamUrl.ifBlank { it.id } }
            val validToEnqueue = mutableListOf<ChannelItem>()
            var skippedExistingCount = 0

            for (ch in distinctCandidates) {
                val existing = allCurrent.find {
                    (it.streamUrl == ch.streamUrl || it.title.equals(ch.name, ignoreCase = true)) &&
                        (it.status == RecordingStatus.DOWNLOADING ||
                            it.status == RecordingStatus.QUEUED ||
                            (it.status == RecordingStatus.DOWNLOADED && it.filePath != null && File(it.filePath).exists()))
                }
                if (existing != null) {
                    skippedExistingCount++
                } else {
                    validToEnqueue.add(ch)
                }
            }

            if (validToEnqueue.isEmpty()) {
                if (channels.size == 1) {
                    val single = channels.first()
                    val existing = allCurrent.find {
                        it.streamUrl == single.streamUrl || it.title.equals(single.name, ignoreCase = true)
                    }
                    _statusMessage.value = when (existing?.status) {
                        RecordingStatus.DOWNLOADING -> "'${single.name}' wird bereits heruntergeladen (${existing.progressPercent}%)"
                        RecordingStatus.QUEUED -> "'${single.name}' befindet sich bereits in der Download-Warteschlange"
                        else -> "'${single.name}' ist bereits offline in Downloads/${DownloadStorageHelper.SUBFOLDER} verfügbar."
                    }
                } else {
                    _statusMessage.value = "Ausgewählte Titel sind bereits in der Warteschlange oder heruntergeladen."
                }
                return@launch
            }

            val batch = validToEnqueue.take(remainingSlots)
            val baseTimestamp = System.currentTimeMillis()
            val extension = "mp4"

            batch.forEachIndexed { index, channel ->
                val recId = UUID.randomUUID().toString()
                val targetFile = DownloadStorageHelper.createOutputFile(getApplication(), channel.name, extension)
                val queuePos = activeOrQueuedCount + index + 1
                vodQueueChannelMeta[recId] = Pair(channel.playlistId, channel.countryCode)

                val queuedItem = RecordingItem(
                    id = recId,
                    title = channel.name,
                    channelName = channel.seriesTitle?.takeIf { it.isNotBlank() } ?: channel.groupTitle.ifBlank { channel.name },
                    streamUrl = channel.streamUrl,
                    contentType = channel.type,
                    startTime = baseTimestamp + index,
                    status = RecordingStatus.QUEUED,
                    filePath = targetFile.absolutePath,
                    progressPercent = 0,
                    fileSizeFormatted = "Warteschlange Platz #$queuePos (max. ${com.example.data.model.MAX_VOD_DOWNLOAD_QUEUE_SIZE}) • Downloads/${DownloadStorageHelper.SUBFOLDER}"
                )
                recordingDao.insertRecording(queuedItem)
            }

            val totalInQueueNow = activeOrQueuedCount + batch.size
            _statusMessage.value = if (batch.size == 1) {
                "📥 '${batch.first().name}' in Download-Manager eingetragen ($totalInQueueNow/${com.example.data.model.MAX_VOD_DOWNLOAD_QUEUE_SIZE})"
            } else {
                "📥 ${batch.size} Titel in Download-Manager eingetragen ($totalInQueueNow/${com.example.data.model.MAX_VOD_DOWNLOAD_QUEUE_SIZE} • Einzel-Download nacheinander)"
            }

            triggerVodQueueWorker()
        }
    }

    private suspend fun refreshQueuedPositions() {
        val all = recordingDao.getAllRecordingsSync()
        val queued = all.filter { it.status == RecordingStatus.QUEUED }.sortedBy { it.startTime }
        val totalWaiting = queued.size
        queued.forEachIndexed { idx, item ->
            val pos = idx + 1
            val expectedText = "Warteschlange Platz #$pos von $totalWaiting • Downloads/${DownloadStorageHelper.SUBFOLDER}"
            if (item.fileSizeFormatted != expectedText) {
                recordingDao.updateRecording(item.copy(fileSizeFormatted = expectedText))
            }
        }
    }

    private fun triggerVodQueueWorker() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!vodQueueMutex.tryLock()) {
                return@launch
            }
            try {
                while (isActive) {
                    val all = recordingDao.getAllRecordingsSync()
                    val activeDownloading = all.firstOrNull {
                        it.status == RecordingStatus.DOWNLOADING && downloadJobs.containsKey(it.id)
                    }
                    if (activeDownloading != null) {
                        downloadJobs[activeDownloading.id]?.join()
                        continue
                    }

                    val queuedSorted = all
                        .filter { it.status == RecordingStatus.QUEUED }
                        .sortedBy { it.startTime }

                    if (queuedSorted.isEmpty()) {
                        break
                    }

                    val nextItem = queuedSorted.first()
                    refreshQueuedPositions()

                    val workerJob = launch(Dispatchers.IO) {
                        executeSingleVodDownload(nextItem)
                    }
                    downloadJobs[nextItem.id] = workerJob
                    workerJob.join()
                    downloadJobs.remove(nextItem.id)
                }
            } finally {
                vodQueueMutex.unlock()
                // Re-check in case new items were enqueued right as the lock was released
                val remaining = runCatching {
                    recordingDao.getAllRecordingsSync().any { it.status == RecordingStatus.QUEUED }
                }.getOrDefault(false)
                if (remaining && !vodQueueMutex.isLocked) {
                    triggerVodQueueWorker()
                }
            }
        }
    }

    private suspend fun executeSingleVodDownload(initialItem: RecordingItem) = kotlinx.coroutines.coroutineScope {
        val recId = initialItem.id
        val extension = "mp4"
        val targetFile = initialItem.filePath?.let { File(it) }
            ?: DownloadStorageHelper.createOutputFile(getApplication(), initialItem.title, extension)

        var outputStream: OutputStream? = null
        try {
            val currentCheck = recordingDao.getRecordingById(recId) ?: return@coroutineScope
            if (currentCheck.status != RecordingStatus.QUEUED && currentCheck.status != RecordingStatus.DOWNLOADING) {
                return@coroutineScope
            }

            recordingDao.updateRecording(
                currentCheck.copy(
                    status = RecordingStatus.DOWNLOADING,
                    progressPercent = 0,
                    filePath = targetFile.absolutePath,
                    fileSizeFormatted = "Starte Einzel-Download... • Downloads/${DownloadStorageHelper.SUBFOLDER}"
                )
            )
            refreshQueuedPositions()
            _statusMessage.value = "⬇️ VOD Download läuft: '${initialItem.title}'"

            val meta = vodQueueChannelMeta[recId]
            val playlistId = meta?.first.orEmpty()
            val countryCode = meta?.second.orEmpty()

            val activePl = playlists.value.find { it.id == playlistId }
                ?: playlists.value.find { it.id == _activePlaylistId.value }
                ?: playlists.value.firstOrNull { it.isActive }
            val mac = activePl?.macAddress ?: ""

            // Resolve MAC portal create_link or redirect URLs before downloading
            val portalResult = macClient.resolveStreamResult(
                streamUrl = initialItem.streamUrl,
                macAddress = mac,
                countryCode = countryCode,
                channelName = initialItem.title
            )
            val resolvedStreamUrl = portalResult.streamUrl.ifBlank { initialItem.streamUrl }

            val ua = playerManager.resolveUserAgentForUrl(resolvedStreamUrl)
            val hdrs = playerManager.resolveHeadersForUrl(resolvedStreamUrl)

            if (resolvedStreamUrl.contains(".m3u8", ignoreCase = true)) {
                val hlsResult = com.example.data.storage.StreamRecorderHelper.recordStream(
                    context = getApplication(),
                    streamUrl = resolvedStreamUrl,
                    targetFile = targetFile,
                    recId = recId,
                    userAgent = ua,
                    headers = hdrs,
                    isRecordingActive = { isActive },
                    onProgressUpdate = { bytes, durationSec ->
                        val curr = recordingDao.getRecordingById(recId)
                        if (curr != null && curr.status == RecordingStatus.DOWNLOADING) {
                            val sizeStr = DownloadStorageHelper.formatBytes(bytes)
                            val estPct = (durationSec.toInt() % 95).coerceIn(1, 95)
                            recordingDao.updateRecording(
                                curr.copy(
                                    progressPercent = estPct,
                                    fileSizeFormatted = "$ sizeStr • MP4 • Downloads/${DownloadStorageHelper.SUBFOLDER}".replace("$ ", "$")
                                )
                            )
                        }
                    }
                )
                when (hlsResult) {
                    is com.example.data.storage.StreamRecorderHelper.RecordingResult.Success -> {
                        val finalItem = recordingDao.getRecordingById(recId)
                        if (finalItem != null) {
                            val finalSize = DownloadStorageHelper.formatBytes(hlsResult.totalBytes)
                            recordingDao.updateRecording(
                                finalItem.copy(
                                    status = RecordingStatus.DOWNLOADED,
                                    progressPercent = 100,
                                    filePath = hlsResult.file.absolutePath,
                                    fileSizeFormatted = "$finalSize • Offline MP4 • Downloads/${DownloadStorageHelper.SUBFOLDER}"
                                )
                            )
                            _statusMessage.value = "✅ '${initialItem.title}' offline verfügbar in Downloads/${DownloadStorageHelper.SUBFOLDER}"
                        }
                    }
                    is com.example.data.storage.StreamRecorderHelper.RecordingResult.Error -> {
                        throw IOException(hlsResult.message)
                    }
                }
                return@coroutineScope
            }

            val reqBuilder = Request.Builder()
                .url(resolvedStreamUrl)
                .header("User-Agent", ua)
            for ((k, v) in hdrs) {
                reqBuilder.header(k, v)
            }
            val request = reqBuilder.build()

            val response = com.example.data.storage.StreamRecorderHelper.client.newCall(request).execute()
            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                throw IOException("HTTP $code")
            }

            val body = response.body ?: run {
                response.close()
                throw IOException("Leere Server-Antwort")
            }
            val totalBytes = body.contentLength()
            val inputStream = body.byteStream()
            val target = DownloadStorageHelper.openOutputStreamAndFile(getApplication(), targetFile, "video/mp4")
            outputStream = target.outputStream
            val actualTargetFile = target.actualFile
            if (actualTargetFile.absolutePath != targetFile.absolutePath) {
                val curr = recordingDao.getRecordingById(recId)
                if (curr != null) {
                    recordingDao.updateRecording(curr.copy(filePath = actualTargetFile.absolutePath))
                }
            }

            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            var accumulatedBytes = 0L
            var lastUpdate = System.currentTimeMillis()

            while (isActive) {
                bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) break
                outputStream.write(buffer, 0, bytesRead)
                accumulatedBytes += bytesRead

                val now = System.currentTimeMillis()
                if (now - lastUpdate >= 750) {
                    lastUpdate = now
                    val currentItem = recordingDao.getRecordingById(recId) ?: break
                    if (currentItem.status != RecordingStatus.DOWNLOADING) break

                    val percent = if (totalBytes > 0) {
                        ((accumulatedBytes * 100) / totalBytes).toInt().coerceIn(1, 99)
                    } else {
                        ((accumulatedBytes / (1024 * 1024)).toInt() % 95).coerceIn(1, 95)
                    }
                    val sizeText = if (totalBytes > 0) {
                        "${DownloadStorageHelper.formatBytes(accumulatedBytes)} / ${DownloadStorageHelper.formatBytes(totalBytes)}"
                    } else {
                        DownloadStorageHelper.formatBytes(accumulatedBytes)
                    }
                    recordingDao.updateRecording(
                        currentItem.copy(
                            progressPercent = percent,
                            fileSizeFormatted = "$percent% • $sizeText • Downloads/${DownloadStorageHelper.SUBFOLDER}"
                        )
                    )
                }
            }
            outputStream.flush()
            response.close()

            if (isActive && actualTargetFile.length() > 0) {
                DownloadStorageHelper.scanFileForDownloads(getApplication(), actualTargetFile)
                val finalItem = recordingDao.getRecordingById(recId)
                if (finalItem != null) {
                    val finalSize = DownloadStorageHelper.formatBytes(actualTargetFile.length())
                    recordingDao.updateRecording(
                        finalItem.copy(
                            status = RecordingStatus.DOWNLOADED,
                            progressPercent = 100,
                            filePath = actualTargetFile.absolutePath,
                            fileSizeFormatted = "$finalSize • Offline MP4 • Downloads/${DownloadStorageHelper.SUBFOLDER}"
                        )
                    )
                    _statusMessage.value = "✅ '${initialItem.title}' gespeichert in Downloads/${DownloadStorageHelper.SUBFOLDER}"
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) {
                DownloadStorageHelper.deleteFile(targetFile.absolutePath)
                throw e
            }
            android.util.Log.e("MainViewModel", "Download error for ${initialItem.title}: ${e.message}", e)
            val failedItem = recordingDao.getRecordingById(recId)
            if (failedItem != null) {
                recordingDao.updateRecording(
                    failedItem.copy(
                        status = RecordingStatus.FAILED,
                        fileSizeFormatted = "Download fehlgeschlagen: ${e.message}"
                    )
                )
            }
            _statusMessage.value = "⚠️ Download fehlgeschlagen: '${initialItem.title}' (Nächster in Warteschlange folgt)"
        } finally {
            try { outputStream?.close() } catch (ignored: Exception) {}
            vodQueueChannelMeta.remove(recId)
        }
    }

    fun clearQueuedDownloads() {
        viewModelScope.launch(Dispatchers.IO) {
            val all = recordingDao.getAllRecordingsSync()
            val queued = all.filter { it.status == RecordingStatus.QUEUED }
            queued.forEach { item ->
                vodQueueChannelMeta.remove(item.id)
                DownloadStorageHelper.deleteFile(item.filePath)
                recordingDao.deleteRecording(item)
            }
            _statusMessage.value = "Warteschlange geleert (${queued.size} Einträge entfernt)"
        }
    }

    fun cancelDownload(recording: RecordingItem) {
        downloadJobs[recording.id]?.cancel()
        downloadJobs.remove(recording.id)
        vodQueueChannelMeta.remove(recording.id)
        if (currentRecordingEntityId == recording.id) {
            playerManager.stopLiveRecording()
            _isRecordingNow.value = false
            currentRecordingEntityId = null
            currentRecordingFile = null
        }
        viewModelScope.launch(Dispatchers.IO) {
            DownloadStorageHelper.deleteFile(recording.filePath)
            recordingDao.deleteRecording(recording)
            refreshQueuedPositions()
            _statusMessage.value = "Abgebrochen & entfernt: '${recording.title}'"
            triggerVodQueueWorker()
        }
    }

    fun deleteRecording(recording: RecordingItem) {
        downloadJobs[recording.id]?.cancel()
        downloadJobs.remove(recording.id)
        vodQueueChannelMeta.remove(recording.id)
        if (currentRecordingEntityId == recording.id) {
            playerManager.stopLiveRecording()
            _isRecordingNow.value = false
            currentRecordingEntityId = null
            currentRecordingFile = null
        }
        viewModelScope.launch(Dispatchers.IO) {
            DownloadStorageHelper.deleteFile(recording.filePath)
            recordingDao.deleteRecording(recording)
            refreshQueuedPositions()
            _statusMessage.value = "'${recording.title}' aus Downloads/${DownloadStorageHelper.SUBFOLDER} gelöscht"
            triggerVodQueueWorker()
        }
    }

    suspend fun fetchSeriesEpisodes(seriesChannel: ChannelItem): List<ChannelItem> = withContext(Dispatchers.IO) {
        val streamUrl = seriesChannel.streamUrl
        val currentActiveId = _activePlaylistId.value.orEmpty()
        val activePlId = seriesChannel.playlistId.ifBlank { currentActiveId }
        val pl = if (activePlId.isNotBlank()) playlistDao.getPlaylistById(activePlId) else null
        val fallbackPl = if (pl == null && currentActiveId.isNotBlank()) playlistDao.getPlaylistById(currentActiveId) else null
        val effectivePl = pl ?: fallbackPl

        // 1. Try MAC Portal if playlist is MAC_PORTAL or streamUrl looks like MAC Portal
        val isMac = (effectivePl != null && effectivePl.type == PlaylistType.MAC_PORTAL) ||
                streamUrl.startsWith("mac_portal://") ||
                streamUrl.contains("action=create_link") ||
                (effectivePl?.macAddress?.isNotBlank() == true && effectivePl.type == PlaylistType.MAC_PORTAL)

        if (isMac) {
            try {
                val portalUrl = effectivePl?.url?.ifBlank { null }
                    ?: if (streamUrl.startsWith("mac_portal://")) streamUrl.substring("mac_portal://".length).substringBefore("?") else streamUrl.substringBefore("?")
                val mac = effectivePl?.macAddress?.ifBlank { null }
                    ?: MacPortalClient.extractQueryParam(streamUrl, "mac")
                val fromProv: String? = seriesChannel.providerId?.takeIf { it.isNotBlank() }
                val fromParam: String? = MacPortalClient.extractQueryParam(streamUrl, "series_id").takeIf { it.isNotBlank() }
                val fromCmd: String? = MacPortalClient.extractQueryParam(streamUrl, "cmd").takeIf { it.isNotBlank() }
                val seriesId = fromProv ?: fromParam ?: fromCmd ?: ""
                val sTitle = seriesChannel.seriesTitle?.takeIf { it.isNotBlank() } ?: seriesChannel.name

                if (portalUrl.isNotBlank() && mac.isNotBlank() && seriesId.isNotBlank()) {
                    val targetPlId: String = effectivePl?.id ?: activePlId
                    val macEps = macClient.fetchSeriesEpisodes(
                        portalUrl = portalUrl,
                        macAddress = mac,
                        seriesId = seriesId,
                        seriesTitle = sTitle,
                        playlistId = targetPlId
                    )
                    if (macEps.isNotEmpty()) {
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                channelDao.insertChannels(macEps)
                            } catch (_: Exception) {}
                        }
                        return@withContext macEps
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("MainViewModel", "Error fetching MAC portal series episodes: ${e.message}")
            }
        }

        // 2. Try Xtream Codes API if playlist is XTREAM_CODES or streamUrl looks like Xtream
        val isXtream = !isMac && ((effectivePl != null && effectivePl.type == PlaylistType.XTREAM_CODES) ||
                streamUrl.contains("/series/") ||
                streamUrl.contains("player_api.php") ||
                !seriesChannel.providerId.isNullOrBlank())

        if (isXtream) {
            try {
                var baseUrl = effectivePl?.url ?: ""
                var user = effectivePl?.username ?: ""
                var pass = effectivePl?.password ?: ""
                var seriesId = seriesChannel.providerId.takeIf { it.isNotBlank() } ?: ""

                if (streamUrl.contains("/series/")) {
                    if (baseUrl.isBlank()) {
                        baseUrl = streamUrl.substringBefore("/series/")
                    }
                    val afterSeries = streamUrl.substringAfter("/series/")
                    val parts = afterSeries.split("/")
                    if (parts.size >= 3) {
                        if (user.isBlank()) user = parts[0]
                        if (pass.isBlank()) pass = parts[1]
                        if (seriesId.isBlank()) seriesId = parts[2].substringBefore(".")
                    }
                }

                if (baseUrl.isNotBlank() && user.isNotBlank() && pass.isNotBlank() && seriesId.isNotBlank()) {
                    val result = xtreamClient.fetchSeriesInfo(baseUrl, user, pass, seriesId)
                    if (result.isSuccess && !result.getOrNull().isNullOrEmpty()) {
                        val eps = result.getOrNull()!!
                        val targetPlId: String = effectivePl?.id ?: activePlId
                        val epsWithPl = eps.map { it.copy(playlistId = targetPlId) }
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                channelDao.insertChannels(epsWithPl)
                            } catch (_: Exception) {}
                        }
                        return@withContext epsWithPl
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("MainViewModel", "Error fetching Xtream series episodes: ${e.message}")
            }
        }

        // 2. Query in-memory and local Room database for multi-episode series (e.g. M3U / Local)
        try {
            val targetTitle = seriesChannel.seriesTitle?.takeIf { it.isNotBlank() }
                ?: ContentClassifier.cleanSeriesTitle(seriesChannel.name)
            val canonicalTarget = ContentClassifier.canonicalSeriesTitle(targetTitle)

            // Check _allChannels first
            val inMemoryMatches = _allChannels.value.filter {
                it.type == ContentType.SERIES && (
                    it.seriesTitle?.equals(targetTitle, ignoreCase = true) == true ||
                    ContentClassifier.canonicalSeriesTitle(it.seriesTitle ?: it.name) == canonicalTarget ||
                    ContentClassifier.cleanSeriesTitle(it.name).equals(targetTitle, ignoreCase = true) ||
                    ContentClassifier.canonicalSeriesTitle(it.name) == canonicalTarget
                )
            }
            if (inMemoryMatches.size > 1) {
                return@withContext inMemoryMatches
            }

            // Query database by playlist
            val dbTargetPlId: String = effectivePl?.id ?: activePlId
            if (dbTargetPlId.isNotBlank()) {
                val dbChannels = channelDao.getChannelsByPlaylistSync(dbTargetPlId)
                val dbMatches = dbChannels.filter {
                    it.type == ContentType.SERIES && (
                        it.seriesTitle?.equals(targetTitle, ignoreCase = true) == true ||
                        ContentClassifier.canonicalSeriesTitle(it.seriesTitle ?: it.name) == canonicalTarget ||
                        ContentClassifier.cleanSeriesTitle(it.name).equals(targetTitle, ignoreCase = true) ||
                        ContentClassifier.canonicalSeriesTitle(it.name) == canonicalTarget
                    )
                }
                if (dbMatches.size > 1) {
                    return@withContext dbMatches
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("MainViewModel", "Error finding DB series episodes: ${e.message}")
        }

        emptyList()
    }

    fun openDownloadsFolder(): Boolean {
        return DownloadStorageHelper.openDownloadsFolder(getApplication())
    }

    fun exportBackup(password: String?): String {
        val plList = if (playlists.value.isNotEmpty()) {
            playlists.value
        } else {
            runCatching {
                kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                    playlistDao.getAllPlaylistsSync()
                }
            }.getOrDefault(emptyList())
        }

        val chList = if (allChannels.value.isNotEmpty()) {
            allChannels.value
        } else {
            runCatching {
                kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                    val favs = channelDao.getFavoritesPaged(null, null, emptyList(), 120, 0)
                    if (favs.isNotEmpty()) {
                        favs
                    } else {
                        val activeId = activePlaylistId.value
                        if (!activeId.isNullOrBlank()) {
                            channelDao.getDachChannelsByPlaylistSync(activeId).take(120)
                        } else {
                            channelDao.getAllLiveChannelsSync().take(120)
                        }
                    }
                }
            }.getOrDefault(emptyList())
        }

        return PlaylistBackupManager.createBackupJson(plList, chList, password)
    }

    // EPG Room Database Operations
    fun triggerEpgSync(customUrl: String? = null) {
        isEpgClearedByUser = false
        val cleanInput = customUrl?.trim()?.ifBlank { null }
        if (cleanInput != null) {
            _settings.value = _settings.value.copy(customEpgUrl = cleanInput)
            settingsPrefs.edit().putString("custom_epg_url", cleanInput).apply()
        }
        val url = cleanInput ?: _settings.value.customEpgUrl.ifBlank { "https://www.free-epg.de/api/epg/de.xml.gz,https://epg.lat/files/de.xml.gz" }
        viewModelScope.launch(Dispatchers.IO) {
            val liveChannels = if (_allChannels.value.isNotEmpty()) {
                _allChannels.value.filter { it.type == ContentType.LIVE_TV }
            } else {
                channelDao.getAllLiveChannelsSync()
            }
            val result = epgSyncManager.syncEpgData(
                epgUrl = url,
                channels = liveChannels,
                timezoneId = _settings.value.epgTimezone,
                offsetHours = _settings.value.epgTimeOffsetHours
            )
            result.onSuccess { count ->
                _statusMessage.value = "EPG synchronisiert: $count Sendungen in lokaler Datenbank geladen."
                refreshLiveEpgForChannels()
            }.onFailure { err ->
                _statusMessage.value = "EPG Sync: ${err.message}"
            }
        }
    }

    private suspend fun buildAvailableEpgFromDb(): List<com.example.data.parser.EpgChannelInfo> {
        val distinctTuples = try {
            epgDao.getDistinctEpgChannels()
        } catch (_: Exception) {
            emptyList()
        }
        val availableEpg = ArrayList<com.example.data.parser.EpgChannelInfo>(distinctTuples.size)
        val seen = HashSet<String>(distinctTuples.size * 2)
        for (tuple in distinctTuples) {
            val id = tuple.channelTvgId.trim()
            if (id.isNotBlank() && seen.add(id.lowercase())) {
                val dName = tuple.channelName.trim().ifBlank { id }
                availableEpg.add(
                    com.example.data.parser.EpgChannelInfo(
                        id = id,
                        displayName = dName,
                        programCount = tuple.programCount
                    )
                )
            }
        }
        return availableEpg
    }

    private suspend fun remapChannelsInternal(): Int {
        val availableEpg = buildAvailableEpgFromDb()
        if (availableEpg.isEmpty()) return 0

        val lookupIndex = com.example.data.parser.EpgMatcher.buildIndex(availableEpg)
        val targetChannels = if (_allChannels.value.isNotEmpty()) {
            _allChannels.value.filter { it.type == ContentType.LIVE_TV }
        } else {
            channelDao.getAllLiveChannelsSync()
        }
        var mappedCount = 0
        val pendingUpdates = ArrayList<com.example.data.local.EpgMappingUpdate>(256)
        val updatedById = HashMap<String, ChannelItem>(256)

        for (ch in targetChannels) {
            val match = com.example.data.parser.EpgMatcher.matchChannelIndexed(ch, lookupIndex)
            val isVerified = (ch.isManualEpgOverride && !ch.manualEpgTvgId.isNullOrBlank()) ||
                (match.status == com.example.data.parser.EpgMappingStatus.MATCHED && !match.matchedTvgId.isNullOrBlank())

            val resolvedId = when {
                ch.isManualEpgOverride && !ch.manualEpgTvgId.isNullOrBlank() -> ch.manualEpgTvgId
                match.status == com.example.data.parser.EpgMappingStatus.MATCHED -> match.matchedTvgId
                else -> null
            }

            if (isVerified) {
                mappedCount++
            }

            val newMethod = if (ch.isManualEpgOverride) "MANUAL" else match.method.name
            val newStatus = if (ch.isManualEpgOverride) "MANUAL" else match.status.name
            val newCandidates = match.candidates.joinToString(";")
            val newReason = if (ch.isManualEpgOverride) "Manuelle Zuordnung durch Nutzer" else match.reason
            val newManualTvgId = if (ch.isManualEpgOverride) ch.manualEpgTvgId else resolvedId

            if (ch.epgId != resolvedId || ch.epgMappingStatus != newStatus || ch.epgMappingMethod != newMethod) {
                pendingUpdates.add(
                    com.example.data.local.EpgMappingUpdate(
                        channelId = ch.id,
                        epgId = resolvedId,
                        method = newMethod,
                        status = newStatus,
                        candidates = newCandidates,
                        reason = newReason,
                        manualTvgId = newManualTvgId,
                        isManualOverride = ch.isManualEpgOverride
                    )
                )
                updatedById[ch.id] = ch.copy(
                    epgId = resolvedId,
                    epgMappingMethod = newMethod,
                    epgMappingStatus = newStatus,
                    epgAmbiguousCandidates = newCandidates,
                    epgUnassignedReason = newReason,
                    manualEpgTvgId = newManualTvgId
                )
            }
        }

        if (pendingUpdates.isNotEmpty()) {
            channelDao.updateEpgMappingsBatch(pendingUpdates, chunkSize = 500)
            if (_allChannels.value.isNotEmpty() && updatedById.isNotEmpty()) {
                _allChannels.value = _allChannels.value.map { current ->
                    updatedById[current.id] ?: current
                }
            }
        }

        return mappedCount
    }

    fun preloadEpgIfNeeded(force: Boolean = false) {
        if (isEpgClearedByUser && !force) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                val activeWindowPrograms = try {
                    epgDao.getProgramsInTimeWindow(now - 3600_000L, now + 6 * 3600_000L)
                } catch (_: Exception) {
                    emptyList()
                }

                if ((force || activeWindowPrograms.isEmpty()) && !isAutoEpgSyncRunning) {
                    isAutoEpgSyncRunning = true
                    try {
                        val defaultFeeds = "https://www.free-epg.de/api/epg/de.xml.gz,https://epg.lat/files/de.xml.gz"
                        val url = _settings.value.customEpgUrl.trim().ifBlank { defaultFeeds }
                        val liveChannels = if (_allChannels.value.isNotEmpty()) {
                            _allChannels.value.filter { it.type == ContentType.LIVE_TV }
                        } else {
                            channelDao.getAllLiveChannelsSync()
                        }
                        epgSyncManager.syncEpgData(
                            epgUrl = url,
                            channels = liveChannels,
                            timezoneId = _settings.value.epgTimezone,
                            offsetHours = _settings.value.epgTimeOffsetHours
                        )
                    } finally {
                        isAutoEpgSyncRunning = false
                    }
                }
                refreshLiveEpgForChannels()
            } catch (e: Exception) {
                Log.w("MainViewModel", "preloadEpgIfNeeded error: ${e.message}")
            }
        }
    }

    private var epgRefreshJob: kotlinx.coroutines.Job? = null
    private var liveEpgComputeJob: kotlinx.coroutines.Job? = null

    private fun scheduleNextEpgRefresh(nextTransitionTimeMs: Long?) {
        epgRefreshJob?.cancel()
        val now = System.currentTimeMillis()
        val delayMs = if (nextTransitionTimeMs != null && nextTransitionTimeMs > now) {
            (nextTransitionTimeMs - now + 1000L).coerceIn(10_000L, 120_000L)
        } else {
            60_000L
        }
        epgRefreshJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(delayMs)
            refreshLiveEpgForChannels()
        }
    }

    /**
     * Resolves the 100% verified XMLTV channelTvgId for a channel using the O(1) EpgLookupIndex.
     * Returns null if the channel is AMBIGUOUS or UNASSIGNED (0% false positives).
     */
    private fun resolveStrictVerifiedEpgIdIndexed(
        ch: ChannelItem,
        lookupIndex: com.example.data.parser.EpgLookupIndex
    ): String? {
        if (ch.isManualEpgOverride && !ch.manualEpgTvgId.isNullOrBlank()) {
            return ch.manualEpgTvgId.trim()
        }
        if (ch.epgMappingStatus == "MATCHED" && !ch.epgId.isNullOrBlank()) {
            return ch.epgId.trim()
        }
        if (ch.epgMappingStatus == "AMBIGUOUS") {
            return null
        }
        val match = com.example.data.parser.EpgMatcher.matchChannelIndexed(ch, lookupIndex)
        if (match.status == com.example.data.parser.EpgMappingStatus.MATCHED && !match.matchedTvgId.isNullOrBlank()) {
            return match.matchedTvgId.trim()
        }
        return null
    }

    fun refreshLiveEpgForChannels() {
        liveEpgComputeJob?.cancel()
        liveEpgComputeJob = viewModelScope.launch(Dispatchers.IO) {
            if (isEpgClearedByUser) {
                if (epgDao.getProgramCount() == 0) {
                    _liveEpgMap.value = emptyMap()
                    _epgGridMap.value = emptyMap()
                    return@launch
                }
            }

            val liveChannels = _allChannels.value.filter { it.type == ContentType.LIVE_TV }
            if (liveChannels.isEmpty()) return@launch

            val now = System.currentTimeMillis()
            val totalOffsetHours = _settings.value.epgTimeOffsetHours
            val timezoneId = _settings.value.epgTimezone
            val effectiveTime = now + (totalOffsetHours * 3600 * 1000L)
            val windowStart = effectiveTime - (3 * 3600 * 1000L)
            val windowEnd = effectiveTime + (18 * 3600 * 1000L)

            // Batch query: Fetch active window (-3h to +18h) in ONE single database query
            val windowPrograms = try {
                epgDao.getProgramsInTimeWindow(windowStart, windowEnd)
            } catch (e: Exception) {
                emptyList()
            }

            // Pre-group and deduplicate ONCE per tvgId (instead of sorting per channel in the inner loop)
            val rawByTvgId = windowPrograms.groupBy { it.channelTvgId.lowercase() }
            val cleanProgramsByTvgId = HashMap<String, List<EpgProgram>>(rawByTvgId.size * 2)
            val availableEpg = ArrayList<com.example.data.parser.EpgChannelInfo>(rawByTvgId.size)

            for ((lowerTvgId, progs) in rawByTvgId) {
                val deduplicated = ArrayList<EpgProgram>(progs.size)
                var prevStart = -1L
                var prevTitle = ""
                for (p in progs) {
                    if (p.startTime != prevStart || p.title != prevTitle) {
                        deduplicated.add(p)
                        prevStart = p.startTime
                        prevTitle = p.title
                    }
                }
                cleanProgramsByTvgId[lowerTvgId] = deduplicated
                val first = deduplicated.first()
                availableEpg.add(
                    com.example.data.parser.EpgChannelInfo(
                        id = first.channelTvgId,
                        displayName = first.channelName.ifBlank { first.channelTvgId },
                        programCount = deduplicated.size
                    )
                )
            }

            val lookupIndex = if (availableEpg.isNotEmpty()) {
                com.example.data.parser.EpgMatcher.buildIndex(availableEpg)
            } else null

            val map = HashMap<String, Pair<EpgProgram?, EpgProgram?>>(liveChannels.size)
            val gridMap = HashMap<String, List<EpgProgram>>(liveChannels.size)

            for (ch in liveChannels) {
                val verifiedTvgId = if (lookupIndex != null) {
                    resolveStrictVerifiedEpgIdIndexed(ch, lookupIndex)
                } else null

                val matchedFromDb = if (verifiedTvgId != null) {
                    cleanProgramsByTvgId[verifiedTvgId.lowercase()]
                } else null

                val effectivePrograms = if (!matchedFromDb.isNullOrEmpty()) {
                    matchedFromDb
                } else {
                    FreeEpgService.getScheduleForWindow(
                        tvgId = ch.epgId ?: ch.tvgId,
                        channelName = ch.name,
                        windowStartMs = windowStart,
                        windowEndMs = windowEnd,
                        timezoneId = timezoneId
                    )
                }

                if (effectivePrograms.isNotEmpty()) {
                    gridMap[ch.id] = effectivePrograms
                    val (cur, nxt) = FreeEpgService.findCurrentAndNextProgram(effectivePrograms, effectiveTime)
                    if (cur != null || nxt != null) {
                        map[ch.id] = Pair(cur, nxt)
                    }
                }
            }
            _liveEpgMap.value = map
            _epgGridMap.value = gridMap

            // Schedule next automatic refresh at transition time
            val nextTransition = map.values.mapNotNull { it.first?.endTime }.filter { it > effectiveTime }.minOrNull()
            scheduleNextEpgRefresh(nextTransition?.minus(totalOffsetHours * 3600 * 1000L))
        }
    }

    suspend fun resolveChannelEpg(channel: ChannelItem, targetTimeMs: Long = System.currentTimeMillis()): Pair<EpgProgram?, EpgProgram?> {
        _liveEpgMap.value[channel.id]?.let { return it }
        val totalOffsetHours = _settings.value.epgTimeOffsetHours
        val timezoneId = _settings.value.epgTimezone
        val effectiveTime = targetTimeMs + (totalOffsetHours * 3600 * 1000L)
        val availableEpg = buildAvailableEpgFromDb()
        if (availableEpg.isNotEmpty()) {
            val lookupIndex = com.example.data.parser.EpgMatcher.buildIndex(availableEpg)
            val verifiedTvgId = resolveStrictVerifiedEpgIdIndexed(channel, lookupIndex)
            if (verifiedTvgId != null) {
                val programs = try {
                    epgDao.getProgramsForChannelSync(verifiedTvgId)
                } catch (_: Exception) {
                    emptyList()
                }
                if (programs.isNotEmpty()) {
                    return FreeEpgService.findCurrentAndNextProgram(programs, effectiveTime)
                }
            }
        }
        return FreeEpgService.getCurrentAndNextProgram(
            tvgId = channel.epgId ?: channel.tvgId,
            channelName = channel.name,
            timezoneId = timezoneId,
            offsetHours = totalOffsetHours,
            targetTimeMs = targetTimeMs
        )
    }

    fun setManualEpgMapping(channelId: String, epgTvgId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            channelDao.updateEpgMapping(
                channelId = channelId,
                epgId = epgTvgId,
                method = "MANUAL",
                status = "MANUAL",
                candidates = "",
                reason = "Manuelle Zuordnung durch Nutzer",
                manualTvgId = epgTvgId,
                isManualOverride = true
            )
            _allChannels.value = _allChannels.value.map {
                if (it.id == channelId) {
                    it.copy(
                        epgId = epgTvgId,
                        manualEpgTvgId = epgTvgId,
                        isManualEpgOverride = true,
                        epgMappingMethod = "MANUAL",
                        epgMappingStatus = "MANUAL",
                        epgUnassignedReason = "Manuelle Zuordnung durch Nutzer"
                    )
                } else it
            }
            refreshLiveEpgForChannels()
        }
    }

    fun clearManualEpgMapping(channelId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val channel = channelDao.getChannelById(channelId) ?: return@launch
            val availableEpg = buildAvailableEpgFromDb()
            val reMatch = com.example.data.parser.EpgMatcher.matchChannel(channel.copy(isManualEpgOverride = false, manualEpgTvgId = null), availableEpg)

            channelDao.updateEpgMapping(
                channelId = channelId,
                epgId = if (reMatch.status == com.example.data.parser.EpgMappingStatus.MATCHED) reMatch.matchedTvgId else null,
                method = reMatch.method.name,
                status = reMatch.status.name,
                candidates = reMatch.candidates.joinToString(";"),
                reason = reMatch.reason,
                manualTvgId = null,
                isManualOverride = false
            )
            _allChannels.value = _allChannels.value.map {
                if (it.id == channelId) {
                    it.copy(
                        epgId = if (reMatch.status == com.example.data.parser.EpgMappingStatus.MATCHED) reMatch.matchedTvgId else null,
                        manualEpgTvgId = null,
                        isManualEpgOverride = false,
                        epgMappingMethod = reMatch.method.name,
                        epgMappingStatus = reMatch.status.name,
                        epgAmbiguousCandidates = reMatch.candidates.joinToString(";"),
                        epgUnassignedReason = reMatch.reason
                    )
                } else it
            }
            refreshLiveEpgForChannels()
        }
    }

    fun clearAllEpgEntries() {
        viewModelScope.launch(Dispatchers.IO) {
            isEpgClearedByUser = true
            epgRefreshJob?.cancel()
            epgSyncManager.clearAllEpgData()
            channelDao.resetAllAutoEpgMappings()
            _liveEpgMap.value = emptyMap()
            _epgGridMap.value = emptyMap()
            if (_allChannels.value.isNotEmpty()) {
                val liveMap = channelDao.getAllLiveChannelsSync().associateBy { it.id }
                _allChannels.value = _allChannels.value.map { ch ->
                    liveMap[ch.id] ?: ch
                }
            }
            _statusMessage.value = "Alle EPG-Einträge und automatischen Zuordnungen wurden gelöscht (0 Sendungen)."
        }
    }

    fun triggerSyncBothNewFeeds() {
        isEpgClearedByUser = false
        val dualFeeds = "https://www.free-epg.de/api/epg/de.xml.gz,https://epg.lat/files/de.xml.gz"
        _settings.value = _settings.value.copy(customEpgUrl = dualFeeds)
        settingsPrefs.edit().putString("custom_epg_url", dualFeeds).apply()
        triggerEpgSync(dualFeeds)
    }

    suspend fun getFullScheduleForChannel(channel: ChannelItem): List<EpgProgram> = withContext(Dispatchers.IO) {
        // First check pre-verified grid cache
        _epgGridMap.value[channel.id]?.takeIf { it.isNotEmpty() }?.let { return@withContext it }

        val availableEpg = buildAvailableEpgFromDb()
        if (availableEpg.isNotEmpty()) {
            val lookupIndex = com.example.data.parser.EpgMatcher.buildIndex(availableEpg)
            val verifiedTvgId = resolveStrictVerifiedEpgIdIndexed(channel, lookupIndex)
            if (verifiedTvgId != null) {
                val fromDb = try {
                    epgDao.getProgramsForChannelSync(verifiedTvgId)
                } catch (_: Exception) {
                    emptyList()
                }
                if (fromDb.isNotEmpty()) {
                    val now = System.currentTimeMillis()
                    return@withContext fromDb
                        .filter { it.endTime >= now - (12 * 3600 * 1000L) }
                        .sortedBy { it.startTime }
                        .distinctBy { "${it.startTime}_${it.title}" }
                }
            }
        }

        val now = System.currentTimeMillis() + (_settings.value.epgTimeOffsetHours * 3600_000L)
        FreeEpgService.getScheduleForWindow(
            tvgId = channel.epgId ?: channel.tvgId,
            channelName = channel.name,
            windowStartMs = now - (6 * 3600_000L),
            windowEndMs = now + (24 * 3600_000L),
            timezoneId = _settings.value.epgTimezone
        )
    }

    fun saveEpgPrograms(programs: List<EpgProgram>) {
        viewModelScope.launch(Dispatchers.IO) {
            epgDao.insertPrograms(programs)
        }
    }

    fun getEpgForChannelFlow(channelTvgId: String) = epgDao.getProgramsForChannel(channelTvgId)

    fun getCurrentProgramForChannel(channelTvgId: String, currentTime: Long = System.currentTimeMillis()) =
        epgDao.getCurrentProgramForChannel(channelTvgId, currentTime)

    fun getNextProgramForChannel(channelTvgId: String, currentTime: Long = System.currentTimeMillis()) =
        epgDao.getNextProgramForChannel(channelTvgId, currentTime)

    fun cleanOldEpgData(cutoffTimestamp: Long = System.currentTimeMillis() - (24 * 3600 * 1000L)) {
        viewModelScope.launch(Dispatchers.IO) {
            epgDao.deleteOldPrograms(cutoffTimestamp)
        }
    }

    fun restoreBackup(backupJson: String, password: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = PlaylistBackupManager.restoreBackupJson(backupJson, password)
            result.onSuccess { (pls, chs) ->
                if (pls.isEmpty()) {
                    _statusMessage.value = "Backup enthält keine gültigen Playlisten."
                    return@launch
                }
                playlistDao.insertPlaylists(pls)
                if (chs.isNotEmpty()) {
                    channelDao.insertChannels(chs)
                }
                if (_activePlaylistId.value.isNullOrBlank()) {
                    val firstId = pls.first().id
                    playlistDao.setActivePlaylist(firstId)
                    _activePlaylistId.value = firstId
                    loadChannelsForPlaylist(firstId)
                }
                _statusMessage.value = "Backup wiederhergestellt (${pls.size} Playlisten eingetragen, ohne Synchronisation)."
            }.onFailure { err ->
                _statusMessage.value = "Wiederherstellung fehlgeschlagen: ${err.message}"
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun onStoragePermissionChanged() {
        viewModelScope.launch(Dispatchers.IO) {
            DownloadStorageHelper.migrateFallbackFiles(getApplication())
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}
