package com.example.data.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.core.content.FileProvider
import com.example.data.model.CachingMode
import com.example.data.model.PlayerEngine
import com.example.data.parser.VpnRequirementHelper
import com.example.service.StreamPlaybackService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

data class TrackOption(
    val id: String,
    val title: String,
    val language: String?,
    val isSelected: Boolean,
    val group: Tracks.Group,
    val trackIndex: Int
)

@OptIn(UnstableApi::class)
class PlayerManager(private val context: Context) {

    private var exoPlayer: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(32, 5, TimeUnit.MINUTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private var currentTitle: String = "MaD IPTV Privat"
    private var currentSubtitle: String = "Live Stream"
    private var isBackgroundEnabled: Boolean = true
    private var lastStreamUrl: String = ""
    val activeStreamUrl: String get() = lastStreamUrl
    private var currentCountryCode: String? = null
    private var currentChannelName: String? = null
    private var retryCount = 0
    private val failedLiveTvLightUrls = mutableSetOf<String>()
    private var isResolvingLiveTvLightFallback = false
    private var bufferingWatchdogHandler: android.os.Handler? = null
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * Optional callback for PlayerController to intercept stream errors for multi-stream fallback.
     * Return true if handled externally (cancels internal retry loop).
     */
    var onStreamErrorCallback: ((streamUrl: String, error: PlaybackException) -> Boolean)? = null

    /**
     * Optional callback when buffering hangs for >12s, allowing PlayerController to switch streams.
     * Return true if handled externally.
     */
    var onBufferingTimeoutCallback: ((streamUrl: String) -> Boolean)? = null

    fun runOnMainThread(action: () -> Unit) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    init {
        StreamPlaybackService.playerControlCallback = { action ->
            runOnMainThread {
                when (action) {
                    StreamPlaybackService.PlayerAction.PLAY -> resume()
                    StreamPlaybackService.PlayerAction.PAUSE -> pause()
                    StreamPlaybackService.PlayerAction.TOGGLE_PLAY -> {
                        if (exoPlayer?.isPlaying == true) pause() else resume()
                    }
                    StreamPlaybackService.PlayerAction.STOP -> pause()
                }
            }
        }
    }

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _bufferedPercentage = MutableStateFlow(0)
    val bufferedPercentage: StateFlow<Int> = _bufferedPercentage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isPortalOccupied = MutableStateFlow(false)
    val isPortalOccupied: StateFlow<Boolean> = _isPortalOccupied.asStateFlow()

    private val _portalOccupiedMessage = MutableStateFlow<String?>(null)
    val portalOccupiedMessage: StateFlow<String?> = _portalOccupiedMessage.asStateFlow()

    private val _vpnHintNotice = MutableStateFlow<String?>(null)
    val vpnHintNotice: StateFlow<String?> = _vpnHintNotice.asStateFlow()

    fun setVpnHintNotice(notice: String?) {
        _vpnHintNotice.value = notice
    }

    fun dismissVpnHintNotice() {
        _vpnHintNotice.value = null
    }

    fun setPortalOccupied(occupied: Boolean, message: String? = null) {
        runOnMainThread {
            _isPortalOccupied.value = occupied
            _portalOccupiedMessage.value = message
            if (occupied) {
                _errorMessage.value = message
                _isBuffering.value = false
                try {
                    exoPlayer?.stop()
                } catch (e: Exception) {}
            }
        }
    }

    private val _audioTracks = MutableStateFlow<List<String>>(emptyList())
    val audioTracks: StateFlow<List<String>> = _audioTracks.asStateFlow()

    private val _selectedAudioTrackIndex = MutableStateFlow(0)
    val selectedAudioTrackIndex: StateFlow<Int> = _selectedAudioTrackIndex.asStateFlow()

    private val _audioTrackOptions = MutableStateFlow<List<TrackOption>>(emptyList())
    val audioTrackOptions: StateFlow<List<TrackOption>> = _audioTrackOptions.asStateFlow()

    private val _subtitleTrackOptions = MutableStateFlow<List<TrackOption>>(emptyList())
    val subtitleTrackOptions: StateFlow<List<TrackOption>> = _subtitleTrackOptions.asStateFlow()

    private val _isSubtitlesEnabled = MutableStateFlow(false)
    val isSubtitlesEnabled: StateFlow<Boolean> = _isSubtitlesEnabled.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    var activeChannel: com.example.data.model.ChannelItem? = null
        private set

    private var lastRecordedPosSec = 0L
    private var lastRecordTimestamp = 0L

    fun saveCurrentProgressNow() {
        val p = exoPlayer ?: return
        val pos = p.currentPosition.coerceAtLeast(0L)
        val dur = p.duration
        val posSec = pos / 1000L
        val durSec = if (dur > 0L) dur / 1000L else (activeChannel?.durationSeconds?.toLong() ?: 0L)
        activeChannel?.let { ch ->
            VodWatchHistoryManager.recordProgress(context, ch, posSec, durSec)
        }
        if (pos > 0L && lastStreamUrl.isNotBlank()) {
            WatchProgressHelper.saveProgress(context, lastStreamUrl, pos, dur)
        }
    }

    private val progressTicker = object : Runnable {
        override fun run() {
            val player = exoPlayer
            if (player != null) {
                val pos = player.currentPosition.coerceAtLeast(0L)
                _currentPosition.value = pos
                val dur = player.duration
                if (dur > 0L) {
                    _duration.value = dur
                }
                _bufferedPercentage.value = player.bufferedPercentage
                if (pos > 5_000L && lastStreamUrl.isNotBlank()) {
                    val now = System.currentTimeMillis()
                    val posSec = pos / 1000L
                    val durSec = if (dur > 0L) dur / 1000L else (activeChannel?.durationSeconds?.toLong() ?: 0L)
                    // Periodic asynchronous save every 5 seconds or upon significant jumps
                    if (now - lastRecordTimestamp >= 5000L || kotlin.math.abs(posSec - lastRecordedPosSec) >= 5L) {
                        lastRecordTimestamp = now
                        lastRecordedPosSec = posSec
                        activeChannel?.let { ch ->
                            VodWatchHistoryManager.recordProgress(context, ch, posSec, durSec)
                        }
                    }
                    if (dur > 0L) {
                        WatchProgressHelper.saveProgress(context, lastStreamUrl, pos, dur)
                    }
                }
                if (player.isPlaying) {
                    mainHandler.postDelayed(this, 350L)
                }
            }
        }
    }

    private var currentCachingMode: CachingMode? = null

    fun getOrCreatePlayer(cachingMode: CachingMode, backgroundPlayback: Boolean): ExoPlayer {
        if (exoPlayer != null && currentCachingMode == cachingMode) return exoPlayer!!

        if (exoPlayer != null && currentCachingMode != cachingMode) {
            try {
                exoPlayer?.stop()
                exoPlayer?.release()
            } catch (e: Exception) {}
            exoPlayer = null
        }
        currentCachingMode = cachingMode

        trackSelector = DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setAllowMultipleAdaptiveSelections(true)
                .setExceedRendererCapabilitiesIfNecessary(true)
                .setAllowVideoMixedMimeTypeAdaptiveness(true)
                .setAllowVideoNonSeamlessAdaptiveness(true)
                .setTunnelingEnabled(false)
                .build()
        }

        // Optimized LoadControl tailored for ultra-fast live zapping and smooth 4K Ultra HD playback
        val minBuffer = cachingMode.minBufferMs.coerceIn(5000, 30000)
        val maxBuffer = cachingMode.maxBufferMs.coerceIn(15000, 60000)
        val bufferForPlayback = cachingMode.bufferForPlaybackMs.coerceIn(800, 3500)
        val bufferAfterRebuffer = (bufferForPlayback * 2).coerceIn(1600, 6000)

        val allocator = DefaultAllocator(true, 64 * 1024)

        val loadControl = DefaultLoadControl.Builder()
            .setAllocator(allocator)
            .setBufferDurationsMs(
                minBuffer,
                maxBuffer,
                bufferForPlayback,
                bufferAfterRebuffer
            )
            .setPrioritizeTimeOverSizeThresholds(true) // Crucial: prioritizes buffer duration over restrictive byte cap so 4K video decoders never starve
            .setBackBuffer(5000, false)
            .build()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val renderersFactory = DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            setEnableDecoderFallback(true) // Software or secondary codec fallback if hardware decoder rejects 4K/HEVC/HDR movie
            setAllowedVideoJoiningTimeMs(2000L) // Fast frame presentation during channel zapping
        }

        // Dynamic, intelligent HTTP Data Source factory with adaptive User-Agent & Referer injection
        val dynamicHttpDataSourceFactory = createDynamicHttpDataSourceFactory()
        val dataSourceFactory = DefaultDataSource.Factory(context, dynamicHttpDataSourceFactory)
        val extractorsFactory = DefaultExtractorsFactory().apply {
            setConstantBitrateSeekingEnabled(true)
            setTsExtractorFlags(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
            )
            setTsExtractorTimestampSearchBytes(1500 * 188)
        }
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(3))

        val player = ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector!!)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(if (backgroundPlayback) C.WAKE_MODE_NETWORK else C.WAKE_MODE_NONE)
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                if (playing) {
                    retryCount = 0
                    mainHandler.removeCallbacks(progressTicker)
                    mainHandler.post(progressTicker)
                } else {
                    mainHandler.removeCallbacks(progressTicker)
                    saveCurrentProgressNow()
                    val p = exoPlayer
                    if (p != null) {
                        val pos = p.currentPosition.coerceAtLeast(0L)
                        val dur = p.duration
                        _currentPosition.value = pos
                        if (dur > 0L) _duration.value = dur
                        _bufferedPercentage.value = p.bufferedPercentage
                        if (pos > 0L && lastStreamUrl.isNotBlank()) {
                            WatchProgressHelper.saveProgress(context, lastStreamUrl, pos, dur)
                        }
                    }
                }
                if (isBackgroundEnabled) {
                    StreamPlaybackService.update(context, currentTitle, currentSubtitle, isPlaying = playing)
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                extractTracks(tracks)
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                val p = exoPlayer ?: return
                _currentPosition.value = p.currentPosition.coerceAtLeast(0L)
                val dur = p.duration
                if (dur > 0L) _duration.value = dur
            }

            override fun onPlaybackStateChanged(state: Int) {
                _isBuffering.value = state == Player.STATE_BUFFERING
                if (state == Player.STATE_BUFFERING) {
                    bufferingWatchdogHandler?.removeCallbacksAndMessages(null)
                    val handler = android.os.Handler(android.os.Looper.getMainLooper())
                    bufferingWatchdogHandler = handler
                    val isLiveTvLight = activeChannel?.playlistId == "live_tv_light"
                    val watchdogTimeoutMs = if (isLiveTvLight) 6000L else 12000L
                    handler.postDelayed({
                        // If stream remains stuck in buffering, trigger Live TV Light fallback or standard reconnect
                        if (exoPlayer?.playbackState == Player.STATE_BUFFERING && exoPlayer?.playWhenReady == true) {
                            if (onBufferingTimeoutCallback?.invoke(lastStreamUrl) == true) {
                                return@postDelayed
                            }
                            val chSnapshot = activeChannel
                            val chId = chSnapshot?.id
                            if (chSnapshot?.playlistId == "live_tv_light" &&
                                !chId.isNullOrBlank() &&
                                !isResolvingLiveTvLightFallback &&
                                retryCount < 6
                            ) {
                                retryCount++
                                if (lastStreamUrl.isNotBlank()) {
                                    failedLiveTvLightUrls.add(lastStreamUrl)
                                }
                                isResolvingLiveTvLightFallback = true
                                val failedSnapshot = failedLiveTvLightUrls.toSet()
                                com.example.data.livetvlight.LiveTvLightStreamResolver.resolveFallbackAfterErrorAsync(
                                    context = context,
                                    channelId = chId,
                                    failedUrls = failedSnapshot
                                ) { fallbackUrl ->
                                    runOnMainThread {
                                        isResolvingLiveTvLightFallback = false
                                        if (activeChannel?.id != chId) return@runOnMainThread
                                        val targetUrl = fallbackUrl?.takeIf { it.isNotBlank() } ?: lastStreamUrl
                                        lastStreamUrl = targetUrl
                                        try {
                                            val p = exoPlayer ?: return@runOnMainThread
                                            val item = createMediaItem(targetUrl)
                                            p.setMediaItem(item)
                                            p.seekToDefaultPosition()
                                            p.prepare()
                                            p.play()
                                        } catch (_: Exception) {}
                                    }
                                }
                                return@postDelayed
                            }
                            try {
                                if (lastStreamUrl.isNotBlank()) {
                                    exoPlayer?.seekToDefaultPosition()
                                    exoPlayer?.prepare()
                                    exoPlayer?.play()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }, watchdogTimeoutMs)
                } else if (state == Player.STATE_READY) {
                    bufferingWatchdogHandler?.removeCallbacksAndMessages(null)
                    val dur = player.duration
                    if (dur > 0L) _duration.value = dur
                    _currentPosition.value = player.currentPosition.coerceAtLeast(0L)
                    _errorMessage.value = null
                    _isPortalOccupied.value = false
                    _portalOccupiedMessage.value = null
                    _vpnHintNotice.value = null
                    retryCount = 0
                    extractTracks(player.currentTracks)
                } else if (state == Player.STATE_ENDED) {
                    bufferingWatchdogHandler?.removeCallbacksAndMessages(null)
                    saveCurrentProgressNow()
                    // For live streams reaching edge or ending prematurely, auto-reconnect
                    if (lastStreamUrl.contains(".m3u8", ignoreCase = true) || lastStreamUrl.contains("/live/", ignoreCase = true)) {
                        player.seekToDefaultPosition()
                        player.prepare()
                        player.play()
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // Allow PlayerController to intercept and handle multi-stream failover
                if (onStreamErrorCallback?.invoke(lastStreamUrl, error) == true) {
                    return
                }

                // Fast live-edge recovery for BEHIND_LIVE_WINDOW on any live stream
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    _errorMessage.value = null
                    _vpnHintNotice.value = null
                    try {
                        exoPlayer?.seekToDefaultPosition()
                        exoPlayer?.prepare()
                        exoPlayer?.play()
                    } catch (_: Exception) {}
                    return
                }

                val isLiveTvLightStream = activeChannel?.playlistId == "live_tv_light" ||
                    lastStreamUrl.contains("hopslan.com", ignoreCase = true)

                // For Live TV light / hopslan.com streams: never trigger false VPN or portal-occupied errors;
                // instead resync to the live edge or alternate between direct muxed variant and master playlist.
                if (isLiveTvLightStream) {
                    _vpnHintNotice.value = null
                    _isPortalOccupied.value = false
                    _portalOccupiedMessage.value = null

                    if (lastStreamUrl.isNotBlank()) {
                        failedLiveTvLightUrls.add(lastStreamUrl)
                    }

                    if (retryCount < 6) {
                        retryCount++
                        val channelSnapshot = activeChannel
                        val channelId = channelSnapshot?.id
                        val isCatalogChannel = channelSnapshot?.playlistId == "live_tv_light" && !channelId.isNullOrBlank()

                        val hopslanToggleCandidate = when {
                            lastStreamUrl.contains("hopslan.com", ignoreCase = true) &&
                                lastStreamUrl.endsWith("/index.m3u8", ignoreCase = true) ->
                                lastStreamUrl.substringBeforeLast("/index.m3u8") + "/tracks-v1a1/mono.ts.m3u8"
                            lastStreamUrl.contains("hopslan.com", ignoreCase = true) &&
                                lastStreamUrl.endsWith("/tracks-v1a1/mono.ts.m3u8", ignoreCase = true) ->
                                lastStreamUrl.substringBeforeLast("/tracks-v1a1/mono.ts.m3u8") + "/index.m3u8"
                            else -> null
                        }?.takeIf { it !in failedLiveTvLightUrls }

                        if (hopslanToggleCandidate != null) {
                            lastStreamUrl = hopslanToggleCandidate
                            _errorMessage.value = null
                            mainHandler.postDelayed({
                                try {
                                    if (activeChannel?.id != channelId) return@postDelayed
                                    val p = exoPlayer ?: return@postDelayed
                                    val item = createMediaItem(lastStreamUrl)
                                    p.setMediaItem(item)
                                    p.seekToDefaultPosition()
                                    p.prepare()
                                    p.play()
                                } catch (_: Exception) {}
                            }, 200L)
                        } else if (isCatalogChannel && !isResolvingLiveTvLightFallback) {
                            isResolvingLiveTvLightFallback = true
                            _errorMessage.value = null
                            _isBuffering.value = true
                            val failedSnapshot = failedLiveTvLightUrls.toSet()
                            com.example.data.livetvlight.LiveTvLightStreamResolver.resolveFallbackAfterErrorAsync(
                                context = context,
                                channelId = channelId!!,
                                failedUrls = failedSnapshot
                            ) { fallbackUrl ->
                                runOnMainThread {
                                    isResolvingLiveTvLightFallback = false
                                    if (activeChannel?.id != channelId) return@runOnMainThread
                                    val targetUrl = fallbackUrl?.takeIf { it.isNotBlank() } ?: lastStreamUrl
                                    lastStreamUrl = targetUrl
                                    try {
                                        val p = exoPlayer ?: return@runOnMainThread
                                        val item = createMediaItem(targetUrl)
                                        p.setMediaItem(item)
                                        p.seekToDefaultPosition()
                                        p.prepare()
                                        p.play()
                                    } catch (_: Exception) {
                                        _errorMessage.value = "Live-Signal wird neu synchronisiert – bitte kurz antippen."
                                    }
                                }
                            }
                        } else {
                            _errorMessage.value = null
                            mainHandler.postDelayed({
                                try {
                                    if (channelId != null && activeChannel?.id != channelId) return@postDelayed
                                    val p = exoPlayer ?: return@postDelayed
                                    val item = createMediaItem(lastStreamUrl)
                                    p.setMediaItem(item)
                                    p.seekToDefaultPosition()
                                    p.prepare()
                                    p.play()
                                } catch (_: Exception) {}
                            }, 250L)
                        }
                    } else {
                        _errorMessage.value = "Live-Signal wird neu synchronisiert – bitte kurz antippen."
                    }
                    return
                }

                val (isBlockedOrOccupied, blockMessage) = VpnRequirementHelper.detectPortalOccupancyOrBlock(
                    error = error,
                    url = lastStreamUrl,
                    channelName = currentChannelName,
                    countryCode = currentCountryCode
                )

                val detectedCountry = currentCountryCode ?: VpnRequirementHelper.extractCountryCode(currentChannelName) ?: "DE"
                val countryName = VpnRequirementHelper.getCountryDisplayName(detectedCountry) ?: "Deutschland"
                _vpnHintNotice.value = "🛡️ VPN-Hinweis: Verbindung eingeschränkt • Eventuell VPN ($countryName [$detectedCountry]) aktivieren."

                if (isBlockedOrOccupied && !blockMessage.isNullOrBlank()) {
                    setPortalOccupied(true, blockMessage)
                    _errorMessage.value = blockMessage
                    retryCount = 999
                    return
                }

                val errorCode = error.errorCodeName
                if (errorCode.contains("BAD_HTTP_STATUS", ignoreCase = true) || error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) {
                    val statusMsg = VpnRequirementHelper.formatStatusErrorMessage(
                        statusCode = 456,
                        channelName = currentChannelName,
                        countryCode = currentCountryCode,
                        url = lastStreamUrl
                    )
                    setPortalOccupied(true, statusMsg)
                    _errorMessage.value = statusMsg
                    retryCount = 999
                    return
                } else {
                    _errorMessage.value = "Signal wird optimiert ($errorCode)..."
                }

                if (retryCount < 4) {
                    retryCount++
                    val nextUrl = when {
                        retryCount == 1 && lastStreamUrl.contains("/live/") && lastStreamUrl.endsWith(".m3u8", ignoreCase = true) ->
                            lastStreamUrl.substring(0, lastStreamUrl.length - 5) + ".ts"
                        retryCount == 1 && lastStreamUrl.contains("/live/") && lastStreamUrl.endsWith(".ts", ignoreCase = true) ->
                            lastStreamUrl.substring(0, lastStreamUrl.length - 3) + ".m3u8"
                        retryCount == 2 && lastStreamUrl.contains("/live/") && (lastStreamUrl.endsWith(".ts", ignoreCase = true) || lastStreamUrl.endsWith(".m3u8", ignoreCase = true)) ->
                            lastStreamUrl.replace("/live/", "/").substringBeforeLast(".")
                        else -> lastStreamUrl
                    }
                    val delayMs = if (nextUrl != lastStreamUrl) 200L else (retryCount * 500L).coerceAtMost(2000L)
                    lastStreamUrl = nextUrl
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        try {
                            if (lastStreamUrl.isNotBlank() && exoPlayer != null) {
                                val item = createMediaItem(lastStreamUrl)
                                exoPlayer?.setMediaItem(item)
                                exoPlayer?.prepare()
                                exoPlayer?.play()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, delayMs)
                } else {
                    _errorMessage.value = "Wiedergabefehler: Sender vorübergehend offline. Tippen Sie auf Play zum erneuten Verbinden."
                }
            }
        })

        exoPlayer = player
        return player
    }

    private fun createDynamicHttpDataSourceFactory(): HttpDataSource.Factory {
        val baseHttpFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")

        return object : HttpDataSource.Factory {
            private val defaultRequestProperties = HttpDataSource.RequestProperties()

            override fun createDataSource(): HttpDataSource {
                val factory = OkHttpDataSource.Factory(okHttpClient)
                    .setUserAgent(resolveUserAgent(lastStreamUrl))

                val headers = resolveHeaders(lastStreamUrl)
                if (headers.isNotEmpty()) {
                    factory.setDefaultRequestProperties(headers)
                }
                val rawDataSource = factory.createDataSource()
                return LiveRecordingHttpDataSource(rawDataSource, LiveRecordingSink)
            }

            override fun setDefaultRequestProperties(defaultRequestProperties: Map<String, String>): HttpDataSource.Factory {
                this.defaultRequestProperties.clearAndSet(defaultRequestProperties)
                baseHttpFactory.setDefaultRequestProperties(defaultRequestProperties)
                return this
            }
        }
    }

    fun resolveUserAgentForUrl(url: String): String = resolveUserAgent(url)

    fun resolveHeadersForUrl(url: String): Map<String, String> = resolveHeaders(url)

    fun playStream(
        url: String,
        cachingMode: CachingMode,
        backgroundPlayback: Boolean,
        title: String? = null,
        subtitle: String? = null,
        countryCode: String? = null,
        channel: com.example.data.model.ChannelItem? = null,
        startPositionMs: Long = 0L
    ) {
        runOnMainThread {
            lastStreamUrl = url
            activeChannel = channel
            lastRecordedPosSec = startPositionMs / 1000L
            lastRecordTimestamp = System.currentTimeMillis()
            retryCount = 0
            failedLiveTvLightUrls.clear()
            isResolvingLiveTvLightFallback = false
            currentCountryCode = countryCode
            currentChannelName = title ?: "Live Stream"
            if (!title.isNullOrBlank()) currentTitle = title
            if (!subtitle.isNullOrBlank()) currentSubtitle = subtitle
            isBackgroundEnabled = backgroundPlayback

            val player = getOrCreatePlayer(cachingMode, backgroundPlayback)
            _errorMessage.value = null
            _isPortalOccupied.value = false
            _portalOccupiedMessage.value = null
            _vpnHintNotice.value = null

            try {
                val mediaItem = createMediaItem(url)
                if (startPositionMs > 0L) {
                    player.setMediaItem(mediaItem, startPositionMs)
                    player.seekTo(startPositionMs)
                } else {
                    player.setMediaItem(mediaItem)
                }
                player.prepare()
                player.playWhenReady = true
            } catch (e: Throwable) {
                Log.e("PlayerManager", "Failed to prepare stream: ${e.message}", e)
                _errorMessage.value = "Wiedergabefehler: ${e.message}"
            }

            if (backgroundPlayback) {
                acquireWakeLock()
                try {
                    StreamPlaybackService.start(context, currentTitle, currentSubtitle, isPlaying = true)
                } catch (e: Throwable) {
                    Log.w("PlayerManager", "Background service start suppressed: ${e.message}")
                }
            }
        }
    }

    private fun createMediaItem(url: String): MediaItem {
        val uri = if (url.startsWith("/") && !url.contains("://")) {
            Uri.fromFile(java.io.File(url))
        } else {
            Uri.parse(url)
        }
        val builder = MediaItem.Builder().setUri(uri)

        val isLiveManifest = url.contains(".m3u8", ignoreCase = true) ||
            url.contains(".mpd", ignoreCase = true) ||
            url.contains("type=m3u", ignoreCase = true) ||
            url.contains("extension=m3u8", ignoreCase = true) ||
            url.contains("manifest", ignoreCase = true)

        if (isLiveManifest) {
            // For short-window HLS relays (hopslan.com / dynamically resolved 2ix2 relay hosts),
            // enforce a tight live-edge offset; official ARD/ZDF/Akamai CDNs use standard live offsets.
            val isShortWindowHls = com.example.data.livetvlight.LiveTvLightStreamResolver.isResolvedRelayUrl(url)
            val (targetOffset, minOffset, maxOffset) = if (isShortWindowHls) {
                Triple(3500L, 1500L, 9000L)
            } else {
                when (currentCachingMode) {
                    CachingMode.ULTRA_FAST -> Triple(3500L, 1500L, 15000L)
                    CachingMode.BALANCED -> Triple(7000L, 3500L, 30000L)
                    CachingMode.STABLE -> Triple(15000L, 8000L, 45000L)
                    CachingMode.WEAK_INTERNET -> Triple(25000L, 12000L, 60000L)
                    null -> Triple(7000L, 3500L, 30000L)
                }
            }
            val liveConfig = MediaItem.LiveConfiguration.Builder()
                .setTargetOffsetMs(targetOffset)
                .setMinOffsetMs(minOffset)
                .setMaxOffsetMs(maxOffset)
                .setMinPlaybackSpeed(1.0f)
                .setMaxPlaybackSpeed(1.0f)
                .build()
            builder.setLiveConfiguration(liveConfig)
        }

        // Set MIME type when container is explicitly evident from URL;
        // otherwise let DefaultExtractorsFactory sniff the header bytes automatically
        if (url.contains(".m3u8", ignoreCase = true) ||
            url.contains("type=m3u", ignoreCase = true) ||
            url.contains("extension=m3u8", ignoreCase = true)
        ) {
            builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        } else if (url.contains(".mpd", ignoreCase = true)) {
            builder.setMimeType(MimeTypes.APPLICATION_MPD)
        } else if (url.endsWith(".ts", ignoreCase = true) || url.contains(".ts?", ignoreCase = true)) {
            builder.setMimeType(MimeTypes.VIDEO_MP2T)
        }

        // For local recordings/files, inspect file header bytes to guarantee correct extractor routing
        val localTargetFile = when {
            url.startsWith("file://", ignoreCase = true) -> {
                try { java.io.File(java.net.URI(url).path) } catch (_: Exception) { java.io.File(url.substring(7)) }
            }
            url.startsWith("/") -> java.io.File(url)
            else -> {
                val f = java.io.File(url)
                if (f.exists()) f else null
            }
        }

        if (localTargetFile != null && localTargetFile.exists() && localTargetFile.length() > 0) {
            try {
                val headerBytes = ByteArray(2048)
                val bytesRead = localTargetFile.inputStream().use { it.read(headerBytes) }
                if (bytesRead > 0) {
                    var isMpegTs = false
                    // 1. Direct MPEG-TS sync byte 0x47 at index 0
                    if (headerBytes[0] == 0x47.toByte()) {
                        isMpegTs = true
                    } else {
                        // 2. Scan for 0x47 with standard 188-byte step (handles ID3 header or padding)
                        for (i in 0 until minOf(bytesRead - 188, 1024)) {
                            if (headerBytes[i] == 0x47.toByte() && headerBytes[i + 188] == 0x47.toByte()) {
                                isMpegTs = true
                                break
                            }
                        }
                    }

                    if (isMpegTs) {
                        builder.setMimeType(MimeTypes.VIDEO_MP2T)
                    } else if (bytesRead >= 8 && headerBytes[4] == 'f'.code.toByte() && headerBytes[5] == 't'.code.toByte() && headerBytes[6] == 'y'.code.toByte() && headerBytes[7] == 'p'.code.toByte()) {
                        builder.setMimeType(MimeTypes.VIDEO_MP4)
                    } else if (bytesRead >= 4 && headerBytes[0] == 0x1A.toByte() && headerBytes[1] == 0x45.toByte() && headerBytes[2] == 0xDF.toByte() && headerBytes[3] == 0xA3.toByte()) {
                        builder.setMimeType(MimeTypes.VIDEO_MATROSKA)
                    }
                }
            } catch (e: Exception) {
                Log.w("PlayerManager", "File header inspection error for ${localTargetFile.absolutePath}: ${e.message}")
            }
        }

        return builder.build()
    }

    fun pause() {
        runOnMainThread {
            saveCurrentProgressNow()
            exoPlayer?.pause()
            if (isBackgroundEnabled) {
                StreamPlaybackService.update(context, currentTitle, currentSubtitle, isPlaying = false)
            }
        }
    }

    fun stop() {
        runOnMainThread {
            saveCurrentProgressNow()
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()
            if (isBackgroundEnabled) {
                StreamPlaybackService.stop(context)
            }
        }
    }

    /**
     * Starts recording the stream currently playing without interrupting playback.
     * Reuses the single active IPTV portal connection.
     */
    fun startLiveRecording(
        targetFile: File,
        onProgress: (bytes: Long, durationSec: Long) -> Unit
    ): File? {
        return LiveRecordingSink.startRecording(context, targetFile, onProgress)
    }

    /**
     * Stops the live recording and finalizes the file, leaving playback active.
     */
    fun stopLiveRecording(): Pair<File?, Long> {
        return LiveRecordingSink.stopRecording(context)
    }

    val isLiveRecordingActive: Boolean
        get() = LiveRecordingSink.isRecordingActive

    val recordedBytes: Long
        get() = LiveRecordingSink.currentBytes

    /**
     * Completely stops playback and clears media items so that the IPTV portal connection
     * socket is closed and freed up for single-connection stream recording.
     */
    fun stopForRecording() {
        runOnMainThread {
            bufferingWatchdogHandler?.removeCallbacksAndMessages(null)
            try {
                exoPlayer?.stop()
                exoPlayer?.clearMediaItems()
            } catch (e: Exception) {
                Log.w("PlayerManager", "Error stopping player for recording: ${e.message}")
            }
            _isBuffering.value = false
            _isPlaying.value = false
            if (isBackgroundEnabled) {
                StreamPlaybackService.update(context, currentTitle, "Aufnahme läuft • Livestream pausiert", isPlaying = false)
            }
        }
    }

    /**
     * Resumes the previously playing stream when recording finishes.
     */
    fun resumePlayback(cachingMode: CachingMode, backgroundPlayback: Boolean) {
        runOnMainThread {
            if (lastStreamUrl.isNotBlank()) {
                playStream(
                    url = lastStreamUrl,
                    title = currentTitle,
                    subtitle = currentSubtitle,
                    cachingMode = cachingMode,
                    backgroundPlayback = backgroundPlayback,
                    countryCode = currentCountryCode
                )
            }
        }
    }

    fun resume() {
        runOnMainThread {
            exoPlayer?.play()
            if (isBackgroundEnabled) {
                StreamPlaybackService.update(context, currentTitle, currentSubtitle, isPlaying = true)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        runOnMainThread {
            exoPlayer?.seekTo(positionMs)
        }
    }

    fun seekForward(offsetMs: Long = 10000L) {
        runOnMainThread {
            val current = exoPlayer?.currentPosition ?: 0L
            exoPlayer?.seekTo(current + offsetMs)
        }
    }

    fun seekBackward(offsetMs: Long = 10000L) {
        runOnMainThread {
            val current = exoPlayer?.currentPosition ?: 0L
            exoPlayer?.seekTo((current - offsetMs).coerceAtLeast(0L))
        }
    }

    fun setSpeed(speed: Float) {
        runOnMainThread {
            exoPlayer?.setPlaybackSpeed(speed)
        }
    }

    fun setAudioTrack(index: Int) {
        runOnMainThread {
            val options = _audioTrackOptions.value
            if (index in options.indices) {
                selectAudioTrack(options[index])
            } else {
                _selectedAudioTrackIndex.value = index
            }
        }
    }

    fun selectAudioTrack(option: TrackOption) {
        runOnMainThread {
            val player = exoPlayer ?: return@runOnMainThread
            try {
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                    .setOverrideForType(
                        TrackSelectionOverride(option.group.mediaTrackGroup, listOf(option.trackIndex))
                    )
                    .build()
                extractTracks(player.currentTracks)
            } catch (e: Exception) {
                Log.e("PlayerManager", "Failed to switch audio track: ${e.message}")
            }
        }
    }

    fun selectSubtitleTrack(option: TrackOption?) {
        runOnMainThread {
            val player = exoPlayer ?: return@runOnMainThread
            try {
                if (option == null) {
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        .build()
                    _isSubtitlesEnabled.value = false
                } else {
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(
                            TrackSelectionOverride(option.group.mediaTrackGroup, listOf(option.trackIndex))
                        )
                        .build()
                    _isSubtitlesEnabled.value = true
                }
                extractTracks(player.currentTracks)
            } catch (e: Exception) {
                Log.e("PlayerManager", "Failed to switch subtitle track: ${e.message}")
            }
        }
    }

    fun extractTracks(tracks: Tracks) {
        val audioList = mutableListOf<TrackOption>()
        val subtitleList = mutableListOf<TrackOption>()

        for (group in tracks.groups) {
            val trackType = group.type
            if (trackType == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val isSelected = group.isTrackSelected(i)
                    val label = formatTrackTitle(format, C.TRACK_TYPE_AUDIO, audioList.size)
                    audioList.add(
                        TrackOption(
                            id = format.id ?: "audio_${audioList.size}",
                            title = label,
                            language = format.language,
                            isSelected = isSelected,
                            group = group,
                            trackIndex = i
                        )
                    )
                }
            } else if (trackType == C.TRACK_TYPE_TEXT) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val isSelected = group.isTrackSelected(i)
                    val label = formatTrackTitle(format, C.TRACK_TYPE_TEXT, subtitleList.size)
                    subtitleList.add(
                        TrackOption(
                            id = format.id ?: "sub_${subtitleList.size}",
                            title = label,
                            language = format.language,
                            isSelected = isSelected,
                            group = group,
                            trackIndex = i
                        )
                    )
                }
            }
        }

        _audioTrackOptions.value = audioList
        _subtitleTrackOptions.value = subtitleList
        _isSubtitlesEnabled.value = subtitleList.any { it.isSelected }

        val simpleAudioNames = if (audioList.isNotEmpty()) {
            audioList.map { it.title }
        } else {
            listOf("Stereo (Standard)")
        }
        _audioTracks.value = simpleAudioNames
        val selectedIdx = audioList.indexOfFirst { it.isSelected }
        _selectedAudioTrackIndex.value = if (selectedIdx >= 0) selectedIdx else 0
    }

    private fun formatTrackTitle(format: Format, trackType: Int, index: Int): String {
        val langStr = format.language?.let { code ->
            try {
                val loc = java.util.Locale(code)
                val display = loc.getDisplayLanguage(java.util.Locale.GERMAN)
                if (display.isNotBlank()) display.replaceFirstChar { it.uppercase() } else code.uppercase()
            } catch (_: Exception) {
                code.uppercase()
            }
        } ?: ""

        val labelStr = format.label?.trim() ?: ""

        val details = when (trackType) {
            C.TRACK_TYPE_AUDIO -> {
                val channels = when (format.channelCount) {
                    6 -> "5.1 Surround"
                    8 -> "7.1 Surround"
                    2 -> "Stereo"
                    1 -> "Mono"
                    else -> if (format.channelCount > 2) "${format.channelCount}ch" else ""
                }
                val mime = when {
                    format.sampleMimeType?.contains("eac3", ignoreCase = true) == true -> "Dolby Digital Plus (E-AC3)"
                    format.sampleMimeType?.contains("ac3", ignoreCase = true) == true -> "Dolby Digital (AC3)"
                    format.sampleMimeType?.contains("dts", ignoreCase = true) == true -> "DTS"
                    format.sampleMimeType?.contains("flac", ignoreCase = true) == true -> "FLAC"
                    format.sampleMimeType?.contains("opus", ignoreCase = true) == true -> "Opus"
                    format.sampleMimeType?.contains("mp4a", ignoreCase = true) == true -> "AAC"
                    else -> ""
                }
                listOf(channels, mime).filter { it.isNotBlank() }.joinToString(" • ")
            }
            C.TRACK_TYPE_TEXT -> {
                if (labelStr.isNotBlank()) "" else "Untertitel"
            }
            else -> ""
        }

        val parts = listOf(labelStr, langStr, details).filter { it.isNotBlank() }.distinct()
        return if (parts.isNotEmpty()) parts.joinToString(" • ") else "Spur ${index + 1}"
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MaDInternetTV:PlaybackWakeLock")
            wakeLock?.acquire(3 * 60 * 60 * 1000L) // 3 hours max
        }
    }

    fun release() {
        runOnMainThread {
            saveCurrentProgressNow()
            try {
                LiveRecordingSink.stopRecording(context)
            } catch (_: Exception) {}
            bufferingWatchdogHandler?.removeCallbacksAndMessages(null)
            bufferingWatchdogHandler = null
            StreamPlaybackService.stop(context)
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
            wakeLock = null
            try {
                exoPlayer?.stop()
                exoPlayer?.release()
            } catch (e: Exception) {}
            exoPlayer = null
        }
    }

    companion object {
        fun resolveUserAgent(url: String): String {
            return when {
                url.contains("kool.to", ignoreCase = true) ||
                url.contains("kool.ws", ignoreCase = true) -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
                url.contains("onair8k", ignoreCase = true) ||
                url.contains("mac_portal://", ignoreCase = true) ||
                url.contains("stalker_portal", ignoreCase = true) ||
                url.contains("weaseltv", ignoreCase = true) ||
                url.contains("play/live.php", ignoreCase = true) ||
                url.contains("/play/", ignoreCase = true) ||
                url.contains("/live/play/", ignoreCase = true) ||
                (url.contains(":8080") && url.contains("/ch/")) -> "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3"
                url.contains("2ix2.com", ignoreCase = true) -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                else -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            }
        }

        fun resolveHeaders(url: String): Map<String, String> {
            val headers = mutableMapOf<String, String>()
            headers["Accept"] = "*/*"
            headers["Accept-Language"] = "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7"
            headers["Connection"] = "keep-alive"

            when {
                url.contains("2ix2.com", ignoreCase = true) ||
                url.contains("hopslan.com", ignoreCase = true) -> {
                    headers["Referer"] = "https://www.2ix2.com/"
                    headers["Origin"] = "https://www.2ix2.com"
                    headers["Sec-Fetch-Dest"] = "empty"
                    headers["Sec-Fetch-Mode"] = "cors"
                    headers["Sec-Fetch-Site"] = "cross-site"
                }
                url.contains("nydus.org", ignoreCase = true) -> {
                    headers["Referer"] = "http://nydus.org/"
                    headers["Origin"] = "http://nydus.org"
                }
                url.contains("movie2k", ignoreCase = true) -> {
                    headers["Referer"] = "https://movie2k.cx/"
                    headers["Origin"] = "https://movie2k.cx"
                }
                url.contains("kool.to", ignoreCase = true) -> {
                    val scheme = if (url.startsWith("http://", ignoreCase = true)) "http" else "https"
                    headers["Referer"] = "$scheme://kool.to/"
                    headers["Origin"] = "$scheme://kool.to"
                    headers["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
                }
                url.contains("kool.ws", ignoreCase = true) -> {
                    val scheme = if (url.startsWith("http://", ignoreCase = true)) "http" else "https"
                    headers["Referer"] = "$scheme://kool.ws/"
                    headers["Origin"] = "$scheme://kool.ws"
                    headers["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
                }
                url.contains("megakino", ignoreCase = true) -> {
                    headers["Referer"] = "http://megakino18.com/"
                    headers["Origin"] = "http://megakino18.com"
                }
                url.contains("onair8k", ignoreCase = true) ||
                url.contains("mac_portal://", ignoreCase = true) ||
                url.contains("stalker_portal", ignoreCase = true) ||
                url.contains("weaseltv", ignoreCase = true) ||
                url.contains("play/live.php", ignoreCase = true) ||
                url.contains(":80/play/", ignoreCase = true) ||
                url.contains("/play/", ignoreCase = true) ||
                url.contains("/live/play/", ignoreCase = true) -> {
                    val macMatch = Regex("[?&]mac=([^&]+)", RegexOption.IGNORE_CASE).find(url)?.groupValues?.get(1) ?: "00:1A:79:B6:C4:82"
                    val tokenMatch = Regex("[?&](?:play_token|token)=([^&]+)", RegexOption.IGNORE_CASE).find(url)?.groupValues?.get(1) ?: ""
                    val cookie = if (tokenMatch.isNotBlank()) {
                        "mac=$macMatch; stb_lang=en; timezone=Europe/Berlin; token=$tokenMatch"
                    } else {
                        "mac=$macMatch; stb_lang=en; timezone=Europe/Berlin"
                    }
                    headers["X-User-Agent"] = "Model: MAG250; Link: WiFi"
                    headers["Cookie"] = cookie
                    if (tokenMatch.isNotBlank()) {
                        headers["Authorization"] = "Bearer $tokenMatch"
                    }
                }
            }
            val dynamicLiveTvLightHeaders = com.example.data.livetvlight.LiveTvLightStreamResolver.getHeadersForUrl(url)
            if (dynamicLiveTvLightHeaders.isNotEmpty()) {
                headers.putAll(dynamicLiveTvLightHeaders)
            }
            return headers
        }

        fun launchExternalPlayer(
            context: Context,
            engine: PlayerEngine,
            streamUrl: String,
            title: String,
            positionMs: Long = 0L
        ): Boolean {
            val cleanUrl = streamUrl.trim()
            if (cleanUrl.isBlank()) {
                try {
                    android.widget.Toast.makeText(context, "Kein gültiger Stream-Link vorhanden", android.widget.Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {}
                return false
            }

            val isLocal = cleanUrl.startsWith("/") || cleanUrl.startsWith("file://", ignoreCase = true) || File(cleanUrl).exists()
            val uri: Uri = if (isLocal) {
                val f = if (cleanUrl.startsWith("file://", ignoreCase = true)) {
                    try { java.io.File(java.net.URI(cleanUrl).path) } catch (_: Exception) { java.io.File(cleanUrl.substring(7)) }
                } else {
                    java.io.File(cleanUrl)
                }
                try {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        f
                    )
                } catch (e: Exception) {
                    Log.w("PlayerManager", "FileProvider failed for ${f.absolutePath}: ${e.message}, falling back to Uri.fromFile")
                    Uri.fromFile(f)
                }
            } else {
                Uri.parse(cleanUrl)
            }
            val isHls = !isLocal && (cleanUrl.contains(".m3u8", ignoreCase = true) || cleanUrl.contains("/live/", ignoreCase = true))
            val mimeType = if (isHls) "application/x-mpegURL" else "video/*"

            val userAgent = resolveUserAgent(cleanUrl)
            val headersMap = resolveHeaders(cleanUrl)
            val headersList = ArrayList<String>()
            headersList.add("User-Agent: $userAgent")
            headersMap.forEach { (k, v) ->
                if (!k.equals("User-Agent", ignoreCase = true)) {
                    headersList.add("$k: $v")
                }
            }
            val headersArray = headersList.toTypedArray()

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                // Universal title extras for all video players
                putExtra("title", title)
                putExtra("forcename", title)
                putExtra("name", title)

                // Headers & User-Agent (VLC, MX Player, XPlayer, MPV, Wuffy)
                putExtra("user_agent", userAgent)
                putExtra("http-user-agent", userAgent)
                putExtra("headers", headersArray)

                // MX Player & XPlayer extras
                putExtra("secure_uri", true)
                putExtra("return_result", true)
                if (positionMs > 0) {
                    putExtra("position", positionMs.toInt())
                }

                // VLC extras
                if (positionMs > 0) {
                    putExtra("from_start", false)
                    putExtra("position", positionMs)
                }
            }

            val packageManager = context.packageManager
            val installedPackage = engine.allPackages.firstOrNull { pkg ->
                try {
                    packageManager.getPackageInfo(pkg, 0)
                    true
                } catch (_: Exception) {
                    false
                }
            }

            if (installedPackage != null) {
                intent.setPackage(installedPackage)
                return try {
                    context.startActivity(intent)
                    true
                } catch (e: Exception) {
                    Log.w("PlayerManager", "Direct launch of $installedPackage failed: ${e.message}, falling back to chooser", e)
                    launchGenericChooser(context, uri, mimeType, title, headersArray, userAgent, positionMs)
                }
            } else {
                val msg = "${engine.displayName} ist nicht installiert. Öffne App-Auswahl..."
                try {
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {}
                return launchGenericChooser(context, uri, mimeType, title, headersArray, userAgent, positionMs)
            }
        }

        private fun launchGenericChooser(
            context: Context,
            uri: Uri,
            mimeType: String,
            title: String,
            headersArray: Array<String>,
            userAgent: String,
            positionMs: Long
        ): Boolean {
            val chooserIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra("title", title)
                putExtra("forcename", title)
                putExtra("user_agent", userAgent)
                putExtra("headers", headersArray)
                if (positionMs > 0) {
                    putExtra("position", positionMs)
                }
            }
            return try {
                val chooser = Intent.createChooser(chooserIntent, "$title öffnen mit...").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
                true
            } catch (e: Exception) {
                try {
                    val rawIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra("title", title)
                    }
                    context.startActivity(rawIntent)
                    true
                } catch (e2: Exception) {
                    Log.e("PlayerManager", "Cannot launch any external player: ${e2.message}")
                    false
                }
            }
        }
    }
}
