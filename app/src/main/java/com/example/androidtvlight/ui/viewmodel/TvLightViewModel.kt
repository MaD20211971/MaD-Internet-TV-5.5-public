package com.example.androidtvlight.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.androidtvlight.data.recorder.TvStreamRecorder
import com.example.androidtvlight.data.repository.TvLightRepository
import com.example.androidtvlight.domain.model.AmbientLightScene
import com.example.androidtvlight.domain.model.ChannelEpgSchedule
import com.example.androidtvlight.domain.model.EpgCategory
import com.example.androidtvlight.domain.model.EpgProgramItem
import com.example.androidtvlight.domain.model.EpgTimeFilter
import com.example.androidtvlight.domain.model.ExoPlayerFeatureState
import com.example.androidtvlight.domain.model.ExoResizeModeOption
import com.example.androidtvlight.domain.model.ExoTrackInfo
import com.example.androidtvlight.domain.model.QuickLaunchTile
import com.example.androidtvlight.domain.model.RecordedVideoItem
import com.example.androidtvlight.domain.model.TvChannelItem
import com.example.androidtvlight.domain.model.TvLightSettings
import com.example.androidtvlight.domain.model.TvRubrik
import com.example.androidtvlight.domain.model.TvSection
import com.example.androidtvlight.domain.usecase.ObserveTvDashboardUseCase
import com.example.androidtvlight.domain.usecase.UpdateTvSettingsUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class RemoteCommand {
    UP, DOWN, LEFT, RIGHT, OK, BACK, HOME, CHANNEL_UP, CHANNEL_DOWN
}

@Immutable
data class TvLightUiState(
    val currentSection: TvSection = TvSection.HOME,
    val selectedRubrik: TvRubrik = TvRubrik.DEUTSCHES_TV,
    val searchQuery: String = "",
    val favoritesOnly: Boolean = false,
    val channels: List<TvChannelItem> = emptyList(),
    val germanChannels: List<TvChannelItem> = emptyList(),
    val austrianChannels: List<TvChannelItem> = emptyList(),
    val tv2Channels: List<TvChannelItem> = emptyList(),
    val isLoadingTv2Channels: Boolean = false,
    val tv3Channels: List<TvChannelItem> = emptyList(),
    val isLoadingTv3Channels: Boolean = false,
    val filteredChannels: List<TvChannelItem> = emptyList(),
    val epgSchedules: List<ChannelEpgSchedule> = emptyList(),
    val filteredEpgSchedules: List<ChannelEpgSchedule> = emptyList(),
    val epgTimeFilter: EpgTimeFilter = EpgTimeFilter.NOW,
    val epgCategoryFilter: EpgCategory = EpgCategory.ALL,
    val selectedEpgDetailProgram: EpgProgramItem? = null,
    val isUpdatingEpg: Boolean = false,
    val lastEpgUpdateFormatted: String = "Live-Sync aktiv",
    val activeChannel: TvChannelItem? = null,
    val activeStreamUrls: List<String> = emptyList(),
    val recordings: List<RecordedVideoItem> = emptyList(),
    val playerState: ExoPlayerFeatureState = ExoPlayerFeatureState(),
    val ambientScenes: List<AmbientLightScene> = emptyList(),
    val activeAmbientScene: AmbientLightScene? = null,
    val isFullscreenAmbient: Boolean = false,
    val quickLaunchApps: List<QuickLaunchTile> = emptyList(),
    val settings: TvLightSettings = TvLightSettings(),
    val focusedItemIndex: Int = 0,
    val lastInputSourceLabel: String = "Touch & Fernbedienung bereit",
    val statusBannerMessage: String? = null
)

private data class LocalInteractionState(
    val currentSection: TvSection = TvSection.HOME,
    val selectedRubrik: TvRubrik = TvRubrik.DEUTSCHES_TV,
    val searchQuery: String = "",
    val favoritesOnly: Boolean = false,
    val epgTimeFilter: EpgTimeFilter = EpgTimeFilter.NOW,
    val epgCategoryFilter: EpgCategory = EpgCategory.ALL,
    val selectedEpgDetailProgram: EpgProgramItem? = null,
    val isFullscreenAmbient: Boolean = false,
    val focusedItemIndex: Int = 0,
    val lastInputSourceLabel: String = "Touch & Fernbedienung bereit",
    val statusBannerMessage: String? = null
)

private data class EpgRuntimeState(
    val schedulesByChannelId: Map<String, ChannelEpgSchedule> = emptyMap(),
    val isUpdating: Boolean = false,
    val lastUpdatedFormatted: String = "Live-Sync aktiv"
)

private data class ExtraStreamsAndRecordingsState(
    val resolvedMap: Map<String, List<String>> = emptyMap(),
    val recordings: List<RecordedVideoItem> = emptyList(),
    val dynamicTv2Channels: List<TvChannelItem> = emptyList(),
    val isLoadingTv2: Boolean = false,
    val dynamicTv3Channels: List<TvChannelItem> = emptyList(),
    val isLoadingTv3: Boolean = false,
    val epgRuntime: EpgRuntimeState = EpgRuntimeState()
)

class TvLightViewModel(
    private val observeDashboardUseCase: ObserveTvDashboardUseCase,
    private val updateSettingsUseCase: UpdateTvSettingsUseCase,
    private val streamRecorder: TvStreamRecorder
) : ViewModel() {

    private val localState = MutableStateFlow(LocalInteractionState())
    private val playerFeatureState = MutableStateFlow(ExoPlayerFeatureState())
    private val resolvedStreamsState = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    private val recordingsListState = MutableStateFlow<List<RecordedVideoItem>>(emptyList())
    private val dynamicTv2ChannelsState = MutableStateFlow<List<TvChannelItem>>(emptyList())
    private val isLoadingTv2State = MutableStateFlow(false)
    private val dynamicTv3ChannelsState = MutableStateFlow<List<TvChannelItem>>(emptyList())
    private val isLoadingTv3State = MutableStateFlow(false)
    private val epgRuntimeState = MutableStateFlow(EpgRuntimeState())
    private val quickAppsState = MutableStateFlow<List<QuickLaunchTile>>(emptyList())
    private val scenes = observeDashboardUseCase.getScenes()
    private var resolveJob: Job? = null
    private var tv2FetchJob: Job? = null
    private var tv3FetchJob: Job? = null
    private var epgSyncJob: Job? = null
    private var lastNetworkEpgFetchMillis: Long = 0L

    init {
        viewModelScope.launch {
            quickAppsState.value = observeDashboardUseCase.loadQuickLaunchApps()
        }
        viewModelScope.launch {
            streamRecorder.status.collect { rec ->
                playerFeatureState.update { prev ->
                    prev.copy(
                        isRecordingMp4 = rec.isRecording,
                        recordingElapsedSeconds = rec.elapsedSeconds,
                        recordingBytesWritten = rec.bytesWritten,
                        recordingTargetPath = rec.targetFilePath,
                        recordingSavedBanner = rec.lastSavedFileMessage
                    )
                }
            }
        }
        viewModelScope.launch {
            streamRecorder.recordings.collect { list ->
                recordingsListState.value = list
            }
        }
        refreshTv2Channels()
        refreshTv3Channels()
        startEpgAutoUpdateLoop()
    }

    private val tv2AndTv3Flow = combine(
        dynamicTv2ChannelsState,
        isLoadingTv2State,
        dynamicTv3ChannelsState,
        isLoadingTv3State
    ) { tv2List, loadingTv2, tv3List, loadingTv3 ->
        Pair(Pair(tv2List, loadingTv2), Pair(tv3List, loadingTv3))
    }

    private val extraStateFlow = combine(
        resolvedStreamsState,
        recordingsListState,
        tv2AndTv3Flow,
        epgRuntimeState
    ) { resolvedMap, recordings, (tv2Pair, tv3Pair), epgRuntime ->
        ExtraStreamsAndRecordingsState(
            resolvedMap = resolvedMap,
            recordings = recordings,
            dynamicTv2Channels = tv2Pair.first,
            isLoadingTv2 = tv2Pair.second,
            dynamicTv3Channels = tv3Pair.first,
            isLoadingTv3 = tv3Pair.second,
            epgRuntime = epgRuntime
        )
    }

    val uiState: StateFlow<TvLightUiState> = combine(
        observeDashboardUseCase.observeSettings(),
        observeDashboardUseCase.observeChannels(),
        localState,
        playerFeatureState,
        extraStateFlow
    ) { settings, baseChannels, local, player, extra ->
        val deAtOnly = baseChannels.filter {
            it.rubrik == TvRubrik.DEUTSCHES_TV || it.rubrik == TvRubrik.OESTERREICH_TV
        }
        val tv2Items = if (extra.dynamicTv2Channels.isNotEmpty()) {
            extra.dynamicTv2Channels.map { ch ->
                ch.copy(isFavorite = settings.favoriteChannelIds.contains(ch.id))
            }
        } else {
            baseChannels.filter { it.rubrik == TvRubrik.TV2_OHA }
        }
        val tv3Items = if (extra.dynamicTv3Channels.isNotEmpty()) {
            extra.dynamicTv3Channels.map { ch ->
                ch.copy(isFavorite = settings.favoriteChannelIds.contains(ch.id))
            }
        } else {
            baseChannels.filter { it.rubrik == TvRubrik.TV3_LIVE }
        }
        val rawChannels = deAtOnly + tv2Items + tv3Items
        val epgMap = extra.epgRuntime.schedulesByChannelId
        val channels = rawChannels.map { ch ->
            val currentEpg = epgMap[ch.id]?.currentProgram
            if (currentEpg != null) {
                ch.copy(currentShow = "${currentEpg.formattedTimeRange} • ${currentEpg.title}")
            } else {
                ch
            }
        }

        val deList = channels.filter { it.rubrik == TvRubrik.DEUTSCHES_TV }
        val atList = channels.filter { it.rubrik == TvRubrik.OESTERREICH_TV }
        val tv2List = channels.filter { it.rubrik == TvRubrik.TV2_OHA }
        val tv3List = channels.filter { it.rubrik == TvRubrik.TV3_LIVE }

        val effectiveRubrik = when (local.currentSection) {
            TvSection.DEUTSCHES_TV -> TvRubrik.DEUTSCHES_TV
            TvSection.OESTERREICH_TV -> TvRubrik.OESTERREICH_TV
            TvSection.TV2 -> TvRubrik.TV2_OHA
            TvSection.TV3 -> TvRubrik.TV3_LIVE
            else -> local.selectedRubrik
        }

        val filtered = channels.filter { ch ->
            val rubrikMatch = effectiveRubrik == TvRubrik.ALL || ch.rubrik == effectiveRubrik
            val favMatch = !local.favoritesOnly || ch.isFavorite
            val queryMatch = local.searchQuery.isBlank() ||
                ch.name.contains(local.searchQuery, ignoreCase = true) ||
                ch.currentShow.contains(local.searchQuery, ignoreCase = true)
            rubrikMatch && favMatch && queryMatch
        }

        val allEpgSchedules = channels.mapNotNull { ch ->
            epgMap[ch.id]?.copy(channel = ch)
        }

        val filteredEpgSchedules = allEpgSchedules.filter { sched ->
            val ch = sched.channel
            val rubrikMatch = effectiveRubrik == TvRubrik.ALL || ch.rubrik == effectiveRubrik
            val favMatch = !local.favoritesOnly || ch.isFavorite
            val activeProgForFilter = when (local.epgTimeFilter) {
                EpgTimeFilter.NOW -> sched.currentProgram
                EpgTimeFilter.NEXT -> sched.nextProgram ?: sched.currentProgram
                EpgTimeFilter.PRIMETIME -> sched.primeTimeProgram ?: sched.currentProgram
                EpgTimeFilter.TIMELINE -> sched.currentProgram
            }
            val categoryMatch = local.epgCategoryFilter == EpgCategory.ALL ||
                activeProgForFilter?.category == local.epgCategoryFilter ||
                sched.dayPrograms.any { it.category == local.epgCategoryFilter }
            val queryMatch = local.searchQuery.isBlank() ||
                ch.name.contains(local.searchQuery, ignoreCase = true) ||
                sched.dayPrograms.any {
                    it.title.contains(local.searchQuery, ignoreCase = true) ||
                        it.subtitle.contains(local.searchQuery, ignoreCase = true) ||
                        it.description.contains(local.searchQuery, ignoreCase = true)
                }
            rubrikMatch && favMatch && categoryMatch && queryMatch
        }

        val activeChannel = channels.find { it.id == settings.lastSelectedChannelId }
            ?: filtered.firstOrNull()
            ?: channels.firstOrNull()

        val activeStreams = if (activeChannel != null) {
            extra.resolvedMap[activeChannel.id] ?: buildList {
                if (activeChannel.primaryStreamUrl.isNotBlank()) add(activeChannel.primaryStreamUrl)
                addAll(activeChannel.fallbackStreamUrls)
            }.distinct()
        } else {
            emptyList()
        }

        val currentUrl = activeStreams.getOrNull(player.streamSourceIndex)
            ?: activeStreams.firstOrNull().orEmpty()

        val activeScene = scenes.find { it.id == settings.activeAmbientSceneId }
            ?: scenes.first()

        val maxIndex = when (local.currentSection) {
            TvSection.HOME,
            TvSection.DEUTSCHES_TV,
            TvSection.OESTERREICH_TV,
            TvSection.TV2,
            TvSection.TV3,
            TvSection.PLAYER -> (filtered.size - 1).coerceAtLeast(0)
            TvSection.EPG -> (filteredEpgSchedules.size - 1).coerceAtLeast(0)
            TvSection.RECORDINGS -> (extra.recordings.size - 1).coerceAtLeast(0)
            TvSection.AMBIENT -> (scenes.size - 1).coerceAtLeast(0)
            TvSection.REMOTE -> 4
        }

        TvLightUiState(
            currentSection = local.currentSection,
            selectedRubrik = effectiveRubrik,
            searchQuery = local.searchQuery,
            favoritesOnly = local.favoritesOnly,
            channels = channels,
            germanChannels = deList,
            austrianChannels = atList,
            tv2Channels = tv2List,
            isLoadingTv2Channels = extra.isLoadingTv2,
            tv3Channels = tv3List,
            isLoadingTv3Channels = extra.isLoadingTv3,
            filteredChannels = filtered,
            epgSchedules = allEpgSchedules,
            filteredEpgSchedules = filteredEpgSchedules,
            epgTimeFilter = local.epgTimeFilter,
            epgCategoryFilter = local.epgCategoryFilter,
            selectedEpgDetailProgram = local.selectedEpgDetailProgram,
            isUpdatingEpg = extra.epgRuntime.isUpdating,
            lastEpgUpdateFormatted = extra.epgRuntime.lastUpdatedFormatted,
            activeChannel = activeChannel,
            activeStreamUrls = activeStreams,
            recordings = extra.recordings,
            playerState = player.copy(
                currentStreamUrl = currentUrl,
                isBackgroundPlaybackEnabled = settings.backgroundPlaybackEnabled,
                isPreventStandbyEnabled = settings.preventStandbyEnabled
            ),
            ambientScenes = scenes,
            activeAmbientScene = activeScene,
            isFullscreenAmbient = local.isFullscreenAmbient,
            quickLaunchApps = quickAppsState.value,
            settings = settings,
            focusedItemIndex = local.focusedItemIndex.coerceIn(0, maxIndex),
            lastInputSourceLabel = local.lastInputSourceLabel,
            statusBannerMessage = local.statusBannerMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TvLightUiState(
            ambientScenes = scenes,
            activeAmbientScene = scenes.firstOrNull()
        )
    )

    private fun startEpgAutoUpdateLoop() {
        refreshEpgNow(manualTrigger = false)
        viewModelScope.launch {
            while (isActive) {
                delay(30_000L)
                val currentSettings = observeDashboardUseCase.observeSettings().first()
                val now = System.currentTimeMillis()
                val intervalMillis = currentSettings.epgAutoUpdateIntervalMinutes.coerceAtLeast(5) * 60_000L
                val shouldFetchNetwork = currentSettings.epgAutoUpdateEnabled &&
                    (now - lastNetworkEpgFetchMillis >= intervalMillis)
                syncEpgInternal(performNetworkFetch = shouldFetchNetwork, isManual = false)
            }
        }
    }

    fun refreshEpgNow(manualTrigger: Boolean = true) {
        epgSyncJob?.cancel()
        epgSyncJob = viewModelScope.launch {
            syncEpgInternal(performNetworkFetch = true, isManual = manualTrigger)
        }
    }

    private suspend fun syncEpgInternal(performNetworkFetch: Boolean, isManual: Boolean) {
        if (performNetworkFetch) {
            epgRuntimeState.update { it.copy(isUpdating = true) }
        }
        val baseChannels = observeDashboardUseCase.observeChannels().first()
        val deAtOnly = baseChannels.filter {
            it.rubrik == TvRubrik.DEUTSCHES_TV || it.rubrik == TvRubrik.OESTERREICH_TV
        }
        val tv2 = dynamicTv2ChannelsState.value.ifEmpty {
            baseChannels.filter { it.rubrik == TvRubrik.TV2_OHA }
        }
        val tv3 = dynamicTv3ChannelsState.value.ifEmpty {
            baseChannels.filter { it.rubrik == TvRubrik.TV3_LIVE }
        }
        val allChannels = deAtOnly + tv2 + tv3

        val schedules = observeDashboardUseCase.syncEpgSchedules(
            channels = allChannels,
            performNetworkFetch = performNetworkFetch
        )
        if (performNetworkFetch) {
            lastNetworkEpgFetchMillis = System.currentTimeMillis()
        }
        val formattedTime = observeDashboardUseCase.getLastEpgSyncFormatted()
        val map = schedules.associateBy { it.channel.id }
        epgRuntimeState.value = EpgRuntimeState(
            schedulesByChannelId = map,
            isUpdating = false,
            lastUpdatedFormatted = "$formattedTime Uhr"
        )
        if (isManual) {
            localState.update {
                it.copy(lastInputSourceLabel = "EPG aktualisiert ($formattedTime Uhr)")
            }
        }
    }

    fun toggleEpgAutoUpdate() {
        val next = !uiState.value.settings.epgAutoUpdateEnabled
        viewModelScope.launch {
            updateSettingsUseCase.setEpgAutoUpdateEnabled(next)
        }
        if (next) {
            refreshEpgNow(manualTrigger = false)
        }
        localState.update {
            it.copy(
                lastInputSourceLabel = if (next) {
                    "EPG Auto-Update: AKTIV (${uiState.value.settings.epgAutoUpdateIntervalMinutes} Min.)"
                } else {
                    "EPG Auto-Update: AUS"
                }
            )
        }
    }

    fun cycleEpgAutoUpdateInterval() {
        val options = listOf(5, 15, 30, 60)
        val current = uiState.value.settings.epgAutoUpdateIntervalMinutes
        val next = options[(options.indexOf(current) + 1) % options.size]
        viewModelScope.launch {
            updateSettingsUseCase.setEpgAutoUpdateIntervalMinutes(next)
        }
        localState.update {
            it.copy(lastInputSourceLabel = "EPG Auto-Update Intervall: $next Min.")
        }
    }

    fun selectEpgTimeFilter(filter: EpgTimeFilter) {
        localState.update {
            it.copy(
                epgTimeFilter = filter,
                focusedItemIndex = 0,
                lastInputSourceLabel = "EPG Ansicht: ${filter.label}"
            )
        }
    }

    fun selectEpgCategoryFilter(category: EpgCategory) {
        localState.update {
            it.copy(
                epgCategoryFilter = category,
                focusedItemIndex = 0,
                lastInputSourceLabel = "EPG Genre: ${category.label}"
            )
        }
    }

    fun setSelectedEpgDetailProgram(program: EpgProgramItem?) {
        localState.update {
            it.copy(selectedEpgDetailProgram = program)
        }
    }

    /**
     * Clicking a show in the EPG directly tunes and starts its channel in the internal ExoPlayer!
     */
    fun playChannelFromEpgProgram(
        program: EpgProgramItem,
        openPlayerSection: Boolean = false,
        enterFullscreen: Boolean = false
    ) {
        val state = uiState.value
        val targetChannel = state.channels.find { it.id == program.channelId }
            ?: state.channels.find { it.name.equals(program.channelName, ignoreCase = true) }
            ?: state.activeChannel
        if (targetChannel != null) {
            localState.update { it.copy(selectedEpgDetailProgram = null) }
            playChannelInExoPlayer(
                channel = targetChannel,
                openPlayerSection = openPlayerSection,
                enterFullscreen = enterFullscreen,
                sourceLabel = "EPG Direktstart (${program.title})"
            )
        }
    }

    fun refreshTv2Channels() {
        tv2FetchJob?.cancel()
        tv2FetchJob = viewModelScope.launch {
            isLoadingTv2State.value = true
            val fetched = observeDashboardUseCase.fetchTv2OhaChannels()
            if (fetched.isNotEmpty()) {
                dynamicTv2ChannelsState.value = fetched
                syncEpgInternal(performNetworkFetch = false, isManual = false)
            }
            isLoadingTv2State.value = false
        }
    }

    fun refreshTv3Channels() {
        tv3FetchJob?.cancel()
        tv3FetchJob = viewModelScope.launch {
            isLoadingTv3State.value = true
            val fetched = observeDashboardUseCase.fetchTv3LiveChannels()
            if (fetched.isNotEmpty()) {
                dynamicTv3ChannelsState.value = fetched
                syncEpgInternal(performNetworkFetch = false, isManual = false)
            }
            isLoadingTv3State.value = false
        }
    }

    fun verifyInstallationPin(pinInput: String): Boolean {
        return if (pinInput.trim() == REQUIRED_INSTALLATION_PIN) {
            viewModelScope.launch {
                updateSettingsUseCase.setInstallationPinVerified(true)
            }
            localState.update {
                it.copy(lastInputSourceLabel = "Installation freigeschaltet")
            }
            true
        } else {
            false
        }
    }

    fun stopForAppExit() {
        if (uiState.value.playerState.isRecordingMp4) {
            streamRecorder.stopRecording()
        }
    }

    fun toggleMp4Recording() {
        val state = uiState.value
        val activeChannel = state.activeChannel ?: return
        val streamUrl = state.playerState.currentStreamUrl.ifBlank { activeChannel.primaryStreamUrl }
        if (state.playerState.isRecordingMp4) {
            streamRecorder.stopRecording()
            localState.update {
                it.copy(lastInputSourceLabel = "MP4-Aufnahme gespeichert in Download/MaDIPTV")
            }
        } else {
            streamRecorder.startRecording(
                channelId = activeChannel.id,
                channelName = activeChannel.name,
                streamUrl = streamUrl
            )
            localState.update {
                it.copy(lastInputSourceLabel = "MP4-Aufnahme läuft: Download/MaDIPTV")
            }
        }
    }

    fun dismissRecordingBanner() {
        streamRecorder.clearSavedMessage()
    }

    fun refreshRecordings() {
        streamRecorder.refreshRecordings()
    }

    fun setShowRecordingsDialog(visible: Boolean) {
        if (visible) {
            streamRecorder.refreshRecordings()
        }
        playerFeatureState.update { it.copy(showRecordingsDialog = visible) }
    }

    fun playRecordingInExoPlayer(
        recording: RecordedVideoItem,
        enterFullscreen: Boolean = false
    ) {
        playerFeatureState.update { prev ->
            prev.copy(
                activeRecording = recording,
                showRecordingsDialog = false,
                isPlaying = true,
                isBuffering = false,
                errorMessage = null,
                isFullscreenPlayer = enterFullscreen || prev.isFullscreenPlayer
            )
        }
        localState.update { prev ->
            val targetSection = when (prev.currentSection) {
                TvSection.HOME, TvSection.TV2, TvSection.TV3, TvSection.PLAYER, TvSection.RECORDINGS -> prev.currentSection
                else -> TvSection.RECORDINGS
            }
            prev.copy(
                currentSection = targetSection,
                lastInputSourceLabel = "Wiedergabe Aufnahme: ${recording.channelTitle}"
            )
        }
    }

    fun stopRecordingPlaybackAndReturnToLive() {
        playerFeatureState.update { prev ->
            prev.copy(
                activeRecording = null,
                isPlaying = true,
                errorMessage = null
            )
        }
        localState.update { prev ->
            prev.copy(lastInputSourceLabel = "Zurück zum Live-TV Stream")
        }
    }

    fun deleteRecording(recording: RecordedVideoItem) {
        val wasPlayingThisRecording = playerFeatureState.value.activeRecording?.id == recording.id ||
            playerFeatureState.value.activeRecording?.fileName == recording.fileName
        if (wasPlayingThisRecording) {
            playerFeatureState.update { prev ->
                prev.copy(
                    activeRecording = null,
                    isPlaying = true,
                    errorMessage = null
                )
            }
        }
        streamRecorder.deleteRecording(recording)
        localState.update { prev ->
            prev.copy(lastInputSourceLabel = "Aufnahme gelöscht: ${recording.fileName}")
        }
    }

    fun selectSection(section: TvSection, sourceLabel: String = "Navigation") {
        if (section == TvSection.RECORDINGS) {
            streamRecorder.refreshRecordings()
            playerFeatureState.update { it.copy(showRecordingsDialog = true) }
        }
        if (section == TvSection.TV2 && dynamicTv2ChannelsState.value.isEmpty() && !isLoadingTv2State.value) {
            refreshTv2Channels()
        }
        if (section == TvSection.TV3 && dynamicTv3ChannelsState.value.isEmpty() && !isLoadingTv3State.value) {
            refreshTv3Channels()
        }
        localState.update { prev ->
            val targetRubrik = when (section) {
                TvSection.DEUTSCHES_TV -> TvRubrik.DEUTSCHES_TV
                TvSection.OESTERREICH_TV -> TvRubrik.OESTERREICH_TV
                TvSection.TV2 -> TvRubrik.TV2_OHA
                TvSection.TV3 -> TvRubrik.TV3_LIVE
                else -> prev.selectedRubrik
            }
            prev.copy(
                currentSection = section,
                selectedRubrik = targetRubrik,
                focusedItemIndex = 0,
                isFullscreenAmbient = false,
                lastInputSourceLabel = sourceLabel
            )
        }
    }

    fun selectRubrik(rubrik: TvRubrik, openSection: Boolean = false) {
        localState.update { prev ->
            val nextSection = if (openSection) {
                when (rubrik) {
                    TvRubrik.DEUTSCHES_TV -> TvSection.DEUTSCHES_TV
                    TvRubrik.OESTERREICH_TV -> TvSection.OESTERREICH_TV
                    TvRubrik.TV2_OHA -> TvSection.TV2
                    TvRubrik.TV3_LIVE -> TvSection.TV3
                    TvRubrik.ALL -> prev.currentSection
                }
            } else {
                prev.currentSection
            }
            prev.copy(
                selectedRubrik = rubrik,
                currentSection = nextSection,
                focusedItemIndex = 0,
                lastInputSourceLabel = "Rubrik: ${rubrik.title}"
            )
        }
    }

    fun updateSearchQuery(query: String) {
        localState.update {
            it.copy(
                searchQuery = query,
                focusedItemIndex = 0
            )
        }
    }

    fun toggleFavoritesFilter() {
        localState.update {
            val next = !it.favoritesOnly
            it.copy(
                favoritesOnly = next,
                focusedItemIndex = 0,
                lastInputSourceLabel = if (next) "Filter: Nur Favoriten" else "Filter: Alle Sender"
            )
        }
    }

    fun playChannelInExoPlayer(
        channel: TvChannelItem,
        openPlayerSection: Boolean = true,
        enterFullscreen: Boolean = false,
        sourceLabel: String = "Senderwahl"
    ) {
        viewModelScope.launch {
            updateSettingsUseCase.setLastSelectedChannel(channel.id)
        }
        val alreadyResolved = resolvedStreamsState.value.containsKey(channel.id)
        playerFeatureState.update {
            it.copy(
                activeRecording = null,
                showRecordingsDialog = false,
                isPlaying = true,
                isBuffering = true,
                streamSourceIndex = 0,
                errorMessage = null,
                isResolvingPageStream = false,
                isFullscreenPlayer = enterFullscreen || it.isFullscreenPlayer
            )
        }
        val idx = uiState.value.filteredChannels.indexOfFirst { it.id == channel.id }
        localState.update {
            it.copy(
                currentSection = if (openPlayerSection) TvSection.PLAYER else it.currentSection,
                selectedRubrik = if (channel.rubrik != TvRubrik.ALL) channel.rubrik else it.selectedRubrik,
                focusedItemIndex = if (idx >= 0) idx else it.focusedItemIndex,
                lastInputSourceLabel = "$sourceLabel: ${channel.name}"
            )
        }
        if (!alreadyResolved) {
            resolveStreamsForChannel(channel)
        }
    }

    private fun resolveStreamsForChannel(channel: TvChannelItem) {
        resolveJob?.cancel()
        resolveJob = viewModelScope.launch {
            val urls = observeDashboardUseCase.resolveChannelStreams(channel)
            if (urls.isNotEmpty()) {
                resolvedStreamsState.update { map -> map + (channel.id to urls) }
            }
        }
    }

    fun switchToFallbackStream() {
        val total = uiState.value.activeStreamUrls.size
        if (total > 1) {
            playerFeatureState.update { state ->
                val nextIdx = (state.streamSourceIndex + 1) % total
                state.copy(
                    streamSourceIndex = nextIdx,
                    errorMessage = null,
                    isPlaying = true
                )
            }
            localState.update {
                it.copy(lastInputSourceLabel = "ExoPlayer: Alternativer Stream-Server gewählt")
            }
        }
    }

    fun onPlayerError(message: String) {
        if (playerFeatureState.value.activeRecording != null) {
            playerFeatureState.update {
                it.copy(errorMessage = message, isBuffering = false)
            }
            return
        }
        val urls = uiState.value.activeStreamUrls
        val curIdx = playerFeatureState.value.streamSourceIndex
        if (curIdx + 1 < urls.size) {
            playerFeatureState.update {
                it.copy(
                    streamSourceIndex = curIdx + 1,
                    errorMessage = null,
                    isPlaying = true
                )
            }
        } else {
            playerFeatureState.update {
                it.copy(errorMessage = message, isBuffering = false)
            }
        }
    }

    fun updatePlayerStatus(
        isPlaying: Boolean? = null,
        isBuffering: Boolean? = null,
        videoResolutionLabel: String? = null,
        videoBitrateLabel: String? = null,
        videoTracks: List<ExoTrackInfo>? = null,
        audioTracks: List<ExoTrackInfo>? = null,
        subtitleTracks: List<ExoTrackInfo>? = null
    ) {
        playerFeatureState.update { prev ->
            prev.copy(
                isPlaying = isPlaying ?: prev.isPlaying,
                isBuffering = isBuffering ?: prev.isBuffering,
                videoResolutionLabel = videoResolutionLabel ?: prev.videoResolutionLabel,
                videoBitrateLabel = videoBitrateLabel ?: prev.videoBitrateLabel,
                availableVideoTracks = videoTracks ?: prev.availableVideoTracks,
                availableAudioTracks = audioTracks ?: prev.availableAudioTracks,
                availableSubtitleTracks = subtitleTracks ?: prev.availableSubtitleTracks,
                errorMessage = if (isPlaying == true) null else prev.errorMessage
            )
        }
    }

    fun togglePlayPause() {
        playerFeatureState.update { it.copy(isPlaying = !it.isPlaying) }
    }

    fun toggleMute() {
        playerFeatureState.update { it.copy(isMuted = !it.isMuted) }
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        playerFeatureState.update {
            it.copy(volume = clamped, isMuted = clamped == 0f)
        }
    }

    fun cycleResizeMode() {
        val modes = ExoResizeModeOption.entries
        playerFeatureState.update { prev ->
            val next = modes[(modes.indexOf(prev.resizeMode) + 1) % modes.size]
            prev.copy(resizeMode = next)
        }
    }

    fun cyclePlaybackSpeed() {
        val speeds = listOf(1.0f, 1.25f, 1.5f, 0.75f)
        playerFeatureState.update { prev ->
            val next = speeds[(speeds.indexOf(prev.playbackSpeed) + 1) % speeds.size]
            prev.copy(playbackSpeed = next)
        }
    }

    fun toggleSubtitles() {
        playerFeatureState.update { it.copy(isSubtitlesEnabled = !it.isSubtitlesEnabled) }
    }

    fun setFullscreenPlayer(fullscreen: Boolean) {
        playerFeatureState.update { it.copy(isFullscreenPlayer = fullscreen) }
    }

    fun setShowTrackDialog(visible: Boolean) {
        playerFeatureState.update { it.copy(showTrackDialog = visible) }
    }

    fun toggleChannelZapperOverlay() {
        playerFeatureState.update { it.copy(showChannelZapperOverlay = !it.showChannelZapperOverlay) }
    }

    fun toggleFavoriteChannel(channelId: String) {
        viewModelScope.launch {
            updateSettingsUseCase.toggleFavoriteChannel(channelId)
        }
    }

    fun selectAmbientScene(sceneId: String, sourceLabel: String = "Ambient Light") {
        viewModelScope.launch {
            updateSettingsUseCase.selectAmbientScene(sceneId)
        }
        val index = scenes.indexOfFirst { it.id == sceneId }
        localState.update {
            it.copy(
                focusedItemIndex = if (index >= 0) index else it.focusedItemIndex,
                lastInputSourceLabel = sourceLabel
            )
        }
    }

    fun setFullscreenAmbient(enabled: Boolean) {
        localState.update {
            it.copy(
                isFullscreenAmbient = enabled,
                lastInputSourceLabel = if (enabled) "Vollbild-Ambient-Light aktiv" else "Vollbild beendet"
            )
        }
    }

    fun setAmbientBrightness(brightness: Float) {
        viewModelScope.launch {
            updateSettingsUseCase.setAmbientBrightness(brightness)
        }
    }

    fun toggleAmbientPulse() {
        val current = uiState.value.settings.ambientDynamicPulse
        viewModelScope.launch {
            updateSettingsUseCase.setAmbientPulse(!current)
        }
    }

    fun setWarmEyeCareFilter(value: Float) {
        viewModelScope.launch {
            updateSettingsUseCase.setWarmEyeCareFilter(value)
        }
    }

    fun cycleSleepTimer() {
        val options = listOf(0, 15, 30, 60, 90)
        val current = uiState.value.settings.sleepTimerMinutes
        val next = options[(options.indexOf(current) + 1) % options.size]
        viewModelScope.launch {
            updateSettingsUseCase.setSleepTimerMinutes(next)
        }
    }

    fun toggleHighContrastMode() {
        val next = !uiState.value.settings.highContrastMode
        viewModelScope.launch {
            updateSettingsUseCase.setHighContrast(next)
        }
    }

    fun toggleLargeTextMode() {
        val next = !uiState.value.settings.largeTextMode
        viewModelScope.launch {
            updateSettingsUseCase.setLargeText(next)
        }
    }

    fun toggleVirtualRemoteOverlay() {
        val next = !uiState.value.settings.showVirtualRemoteOverlay
        viewModelScope.launch {
            updateSettingsUseCase.setVirtualRemoteVisible(next)
        }
    }

    fun toggleBackgroundPlayback() {
        val next = !uiState.value.settings.backgroundPlaybackEnabled
        viewModelScope.launch {
            updateSettingsUseCase.setBackgroundPlayback(next)
        }
        localState.update {
            it.copy(
                lastInputSourceLabel = if (next) {
                    "Premium: Hintergrundwiedergabe AN"
                } else {
                    "Hintergrundwiedergabe AUS"
                }
            )
        }
    }

    fun togglePreventStandby() {
        val next = !uiState.value.settings.preventStandbyEnabled
        viewModelScope.launch {
            updateSettingsUseCase.setPreventStandby(next)
        }
        localState.update {
            it.copy(
                lastInputSourceLabel = if (next) {
                    "Standby-Schutz: AKTIV (Kein Abschalten)"
                } else {
                    "Standby-Schutz: Standard"
                }
            )
        }
    }

    fun handleRemoteCommand(
        command: RemoteCommand,
        sourceLabel: String = "TV-Fernbedienung (D-Pad)"
    ) {
        val state = uiState.value
        if (state.playerState.showRecordingsDialog && command == RemoteCommand.BACK) {
            setShowRecordingsDialog(false)
            return
        }

        if (state.isFullscreenAmbient &&
            (command == RemoteCommand.BACK || command == RemoteCommand.HOME || command == RemoteCommand.OK)
        ) {
            setFullscreenAmbient(false)
            return
        }

        if (state.playerState.isFullscreenPlayer && command == RemoteCommand.BACK) {
            setFullscreenPlayer(false)
            return
        }

        if (state.selectedEpgDetailProgram != null && command == RemoteCommand.BACK) {
            setSelectedEpgDetailProgram(null)
            return
        }

        val itemCount = when (state.currentSection) {
            TvSection.HOME,
            TvSection.DEUTSCHES_TV,
            TvSection.OESTERREICH_TV,
            TvSection.TV2,
            TvSection.TV3,
            TvSection.PLAYER -> state.filteredChannels.size
            TvSection.EPG -> state.filteredEpgSchedules.size
            TvSection.RECORDINGS -> state.recordings.size
            TvSection.AMBIENT -> state.ambientScenes.size
            TvSection.REMOTE -> 5
        }.coerceAtLeast(1)

        when (command) {
            RemoteCommand.UP -> {
                if (state.playerState.isFullscreenPlayer) {
                    stepChannel(-1, "$sourceLabel: Kanal -")
                } else {
                    val nextIdx = (state.focusedItemIndex - 1).coerceAtLeast(0)
                    localState.update {
                        it.copy(focusedItemIndex = nextIdx, lastInputSourceLabel = "$sourceLabel: Oben")
                    }
                }
            }
            RemoteCommand.DOWN -> {
                if (state.playerState.isFullscreenPlayer) {
                    stepChannel(1, "$sourceLabel: Kanal +")
                } else {
                    val nextIdx = (state.focusedItemIndex + 1).coerceAtMost(itemCount - 1)
                    localState.update {
                        it.copy(focusedItemIndex = nextIdx, lastInputSourceLabel = "$sourceLabel: Unten")
                    }
                }
            }
            RemoteCommand.LEFT -> {
                if (state.playerState.isFullscreenPlayer) {
                    toggleChannelZapperOverlay()
                } else {
                    val sections = TvSection.entries
                    val prevSectionIdx = (sections.indexOf(state.currentSection) - 1).coerceAtLeast(0)
                    selectSection(sections[prevSectionIdx], "$sourceLabel: Links (${sections[prevSectionIdx].title})")
                }
            }
            RemoteCommand.RIGHT -> {
                if (state.playerState.isFullscreenPlayer) {
                    cycleResizeMode()
                } else {
                    val sections = TvSection.entries
                    val nextSectionIdx = (sections.indexOf(state.currentSection) + 1).coerceAtMost(sections.size - 1)
                    selectSection(sections[nextSectionIdx], "$sourceLabel: Rechts (${sections[nextSectionIdx].title})")
                }
            }
            RemoteCommand.CHANNEL_UP -> {
                stepChannel(1, "$sourceLabel: CH+")
            }
            RemoteCommand.CHANNEL_DOWN -> {
                stepChannel(-1, "$sourceLabel: CH-")
            }
            RemoteCommand.OK -> {
                if (state.playerState.isFullscreenPlayer) {
                    togglePlayPause()
                    return
                }
                when (state.currentSection) {
                    TvSection.HOME,
                    TvSection.DEUTSCHES_TV,
                    TvSection.OESTERREICH_TV,
                    TvSection.TV2,
                    TvSection.TV3,
                    TvSection.PLAYER -> {
                        val ch = state.filteredChannels.getOrNull(state.focusedItemIndex) ?: state.activeChannel
                        if (ch != null) {
                            playChannelInExoPlayer(
                                channel = ch,
                                openPlayerSection = state.currentSection != TvSection.TV2 &&
                                    state.currentSection != TvSection.TV3 &&
                                    state.currentSection != TvSection.HOME,
                                sourceLabel = "$sourceLabel: OK"
                            )
                        }
                    }
                    TvSection.EPG -> {
                        val sched = state.filteredEpgSchedules.getOrNull(state.focusedItemIndex)
                        val prog = sched?.currentProgram ?: sched?.dayPrograms?.firstOrNull()
                        if (prog != null) {
                            playChannelFromEpgProgram(prog, openPlayerSection = false, enterFullscreen = false)
                        }
                    }
                    TvSection.RECORDINGS -> {
                        state.recordings.getOrNull(state.focusedItemIndex)?.let { rec ->
                            playRecordingInExoPlayer(rec)
                        }
                    }
                    TvSection.AMBIENT -> {
                        state.ambientScenes.getOrNull(state.focusedItemIndex)?.let { scene ->
                            selectAmbientScene(scene.id, "$sourceLabel: Szene ${scene.name}")
                        }
                    }
                    TvSection.REMOTE -> {
                        when (state.focusedItemIndex) {
                            0 -> toggleHighContrastMode()
                            1 -> toggleLargeTextMode()
                            2 -> toggleAmbientPulse()
                            3 -> cycleSleepTimer()
                            4 -> setFullscreenAmbient(true)
                        }
                    }
                }
            }
            RemoteCommand.BACK -> {
                if (state.currentSection != TvSection.HOME) {
                    selectSection(TvSection.HOME, "$sourceLabel: Zurück zur Startseite")
                }
            }
            RemoteCommand.HOME -> {
                setFullscreenPlayer(false)
                selectSection(TvSection.HOME, "$sourceLabel: Startseite")
            }
        }
    }

    fun stepChannel(delta: Int, sourceLabel: String = "Zapping") {
        val list = uiState.value.filteredChannels.ifEmpty { uiState.value.channels }
        if (list.isNotEmpty()) {
            val curIdx = list.indexOfFirst { it.id == uiState.value.activeChannel?.id }.coerceAtLeast(0)
            val nextChannel = list[(curIdx + delta + list.size) % list.size]
            playChannelInExoPlayer(
                channel = nextChannel,
                openPlayerSection = uiState.value.currentSection == TvSection.PLAYER,
                sourceLabel = sourceLabel
            )
        }
    }

    class Factory(
        private val repository: TvLightRepository,
        private val streamRecorder: TvStreamRecorder
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val observeUseCase = ObserveTvDashboardUseCase(repository)
            val updateUseCase = UpdateTvSettingsUseCase(repository)
            return TvLightViewModel(observeUseCase, updateUseCase, streamRecorder) as T
        }
    }

    companion object {
        private const val REQUIRED_INSTALLATION_PIN = "1971"
    }
}
