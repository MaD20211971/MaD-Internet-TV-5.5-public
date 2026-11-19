package com.example.androidtvlight.domain.usecase

import com.example.androidtvlight.data.repository.TvLightRepository
import com.example.androidtvlight.domain.model.AmbientLightScene
import com.example.androidtvlight.domain.model.ChannelEpgSchedule
import com.example.androidtvlight.domain.model.QuickLaunchTile
import com.example.androidtvlight.domain.model.TvChannelItem
import com.example.androidtvlight.domain.model.TvLightSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveTvDashboardUseCase(
    private val repository: TvLightRepository
) {
    private val baseChannels = repository.getInitialChannels()
    private val ambientScenes = repository.getAmbientScenes()

    fun observeSettings(): Flow<TvLightSettings> = repository.settingsFlow

    fun observeChannels(): Flow<List<TvChannelItem>> = repository.settingsFlow.map { settings ->
        baseChannels.map { channel ->
            channel.copy(isFavorite = settings.favoriteChannelIds.contains(channel.id))
        }
    }

    fun getScenes(): List<AmbientLightScene> = ambientScenes

    suspend fun loadQuickLaunchApps(): List<QuickLaunchTile> = repository.loadQuickLaunchApps()

    suspend fun resolveChannelStreams(channel: TvChannelItem): List<String> =
        repository.resolveLiveStreamUrls(channel)

    suspend fun fetchTv2OhaChannels(): List<TvChannelItem> =
        repository.fetchTv2OhaChannels()

    suspend fun fetchTv3LiveChannels(): List<TvChannelItem> =
        repository.fetchTv3LiveChannels()

    suspend fun syncEpgSchedules(
        channels: List<TvChannelItem>,
        performNetworkFetch: Boolean = true
    ): List<ChannelEpgSchedule> =
        repository.syncEpgSchedules(channels, performNetworkFetch)

    fun getLastEpgSyncFormatted(): String =
        repository.getLastEpgSyncFormatted()
}

class UpdateTvSettingsUseCase(
    private val repository: TvLightRepository
) {
    suspend fun setInstallationPinVerified(verified: Boolean): Result<Unit> =
        repository.updateInstallationPinVerified(verified)

    suspend fun setBackgroundPlayback(enabled: Boolean): Result<Unit> =
        repository.updateBackgroundPlayback(enabled)

    suspend fun setPreventStandby(enabled: Boolean): Result<Unit> =
        repository.updatePreventStandby(enabled)

    suspend fun setEpgAutoUpdateEnabled(enabled: Boolean): Result<Unit> =
        repository.updateEpgAutoUpdateEnabled(enabled)

    suspend fun setEpgAutoUpdateIntervalMinutes(minutes: Int): Result<Unit> =
        repository.updateEpgAutoUpdateIntervalMinutes(minutes)

    suspend fun setHighContrast(enabled: Boolean): Result<Unit> =
        repository.updateHighContrast(enabled)

    suspend fun setLargeText(enabled: Boolean): Result<Unit> =
        repository.updateLargeText(enabled)

    suspend fun setVirtualRemoteVisible(visible: Boolean): Result<Unit> =
        repository.updateVirtualRemoteOverlay(visible)

    suspend fun selectAmbientScene(sceneId: String): Result<Unit> =
        repository.selectAmbientScene(sceneId)

    suspend fun setAmbientBrightness(brightness: Float): Result<Unit> =
        repository.updateAmbientBrightness(brightness)

    suspend fun setAmbientPulse(enabled: Boolean): Result<Unit> =
        repository.updateAmbientPulse(enabled)

    suspend fun setWarmEyeCareFilter(value: Float): Result<Unit> =
        repository.updateWarmEyeCareFilter(value)

    suspend fun setSleepTimerMinutes(minutes: Int): Result<Unit> =
        repository.updateSleepTimer(minutes)

    suspend fun toggleFavoriteChannel(channelId: String): Result<Unit> =
        repository.toggleFavoriteChannel(channelId)

    suspend fun setLastSelectedChannel(channelId: String): Result<Unit> =
        repository.setLastSelectedChannel(channelId)
}
