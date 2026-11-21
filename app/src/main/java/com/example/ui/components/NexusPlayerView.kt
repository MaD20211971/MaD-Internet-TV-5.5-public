package com.example.ui.components

import android.app.Activity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.DisposableEffect
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import com.example.domain.player.PlaybackStatus
import com.example.domain.player.PlayerController
import com.example.domain.player.StreamSelectionDialog
import androidx.compose.runtime.mutableLongStateOf
import androidx.media3.common.C
import com.example.cast.UniversalCastManager
import com.example.cast.model.CastMediaItem
import com.example.cast.ui.CastControlSheet
import com.example.data.player.TrackOption
import com.example.data.player.WatchProgressHelper
import com.example.data.player.VodWatchHistoryManager
import coil.compose.AsyncImage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.model.AppSettings
import com.example.data.model.ChannelItem
import com.example.data.model.ContentType
import com.example.data.model.EpgProgram
import com.example.data.model.PlayerEngine
import com.example.data.parser.FreeEpgService
import com.example.data.player.PlayerManager
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentRose
import com.example.ui.theme.BorderGlow
import com.example.ui.theme.DarkNavyCard
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.GoldDark
import com.example.ui.theme.GoldLight
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun NexusPlayerView(
    channel: ChannelItem,
    allChannels: List<ChannelItem>,
    playerManager: PlayerManager,
    playerController: PlayerController? = null,
    settings: AppSettings,
    isRecording: Boolean,
    onToggleRecording: () -> Unit,
    onScheduleRecording: (startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) -> Unit = { _, _, _, _ -> },
    onDownloadVod: (ChannelItem) -> Unit = {},
    onSelectChannel: (ChannelItem) -> Unit,
    onClose: () -> Unit,
    onToggleFavorite: () -> Unit = {},
    onToggleFavoriteChannel: (ChannelItem) -> Unit = {},
    onUpdateBackgroundAudioPlayback: ((Boolean) -> Unit)? = null,
    currentProgram: EpgProgram? = null,
    nextProgram: EpgProgram? = null,
    radioRecordingInfo: String? = null,
    radioSongTitle: String? = null,
    isEnglish: Boolean = false
) {
    val context = LocalContext.current
    val isPlaying by playerManager.isPlaying.collectAsStateWithLifecycle()
    val isBuffering by playerManager.isBuffering.collectAsStateWithLifecycle()
    val isPortalOccupied by playerManager.isPortalOccupied.collectAsStateWithLifecycle()
    val portalOccupiedMessage by playerManager.portalOccupiedMessage.collectAsStateWithLifecycle()
    val errorMessage by playerManager.errorMessage.collectAsStateWithLifecycle()
    val currentPosition by playerManager.currentPosition.collectAsStateWithLifecycle()
    val duration by playerManager.duration.collectAsStateWithLifecycle()
    val audioTracks by playerManager.audioTracks.collectAsStateWithLifecycle()
    val selectedAudioIndex by playerManager.selectedAudioTrackIndex.collectAsStateWithLifecycle()
    val audioTrackOptions by playerManager.audioTrackOptions.collectAsStateWithLifecycle()
    val subtitleTrackOptions by playerManager.subtitleTrackOptions.collectAsStateWithLifecycle()
    val isSubtitlesEnabled by playerManager.isSubtitlesEnabled.collectAsStateWithLifecycle()
    val vpnHintNotice by playerManager.vpnHintNotice.collectAsStateWithLifecycle()

    val defaultStatus = remember { PlaybackStatus() }
    val playbackStatus by (playerController?.playbackStatus ?: kotlinx.coroutines.flow.MutableStateFlow(defaultStatus)).collectAsStateWithLifecycle()

    var showControls by remember { mutableStateOf(true) }
    var showChannelDrawer by remember { mutableStateOf(false) }
    var showStreamSelectionDialog by remember { mutableStateOf(false) }
    var showAudioMenu by remember { mutableStateOf(false) }
    var showSubtitleMenu by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showExternalMenu by remember { mutableStateOf(false) }
    var showCastDialog by remember { mutableStateOf(false) }
    val castState by UniversalCastManager.uiState.collectAsStateWithLifecycle()
    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableLongStateOf(0L) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(context) {
        UniversalCastManager.ensureInitialized(context)
    }

    val isVod = channel.type == ContentType.MOVIE ||
        channel.type == ContentType.SERIES ||
        channel.streamUrl.startsWith("/") ||
        channel.streamUrl.startsWith("file://") ||
        channel.groupTitle == "Downloads & Aufnahmen" ||
        (duration > 0L && duration != C.TIME_UNSET && channel.type != ContentType.LIVE_TV && channel.type != ContentType.RADIO)

    val currentCastMedia = remember(
        channel.id,
        channel.streamUrl,
        channel.name,
        channel.groupTitle,
        channel.logoUrl,
        isVod,
        playerManager.activeStreamUrl
    ) {
        CastMediaItem(
            streamUrl = playerManager.activeStreamUrl.ifBlank { channel.streamUrl },
            title = channel.name,
            subtitle = channel.groupTitle,
            logoUrl = channel.logoUrl.orEmpty(),
            isLive = !isVod,
            isAudioOnly = channel.type == ContentType.RADIO,
            positionMs = if (isVod) playerManager.currentPosition.value else 0L
        )
    }

    LaunchedEffect(currentCastMedia.streamUrl, currentCastMedia.title, castState.isConnected) {
        if (castState.isConnected && currentCastMedia.streamUrl.isNotBlank()) {
            UniversalCastManager.updateMediaIfConnected(context, currentCastMedia)
        }
    }

    var resumeNoticeMs by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(channel.id, channel.streamUrl, isVod) {
        if (isVod) {
            val savedSec = if (channel.type == ContentType.SERIES) {
                VodWatchHistoryManager.getEpisodePositionSeconds(context, channel.id)
            } else {
                VodWatchHistoryManager.getMoviePositionSeconds(context, channel.sourceId.ifBlank { channel.id })
            }
            val savedMs = if (savedSec > 5L) savedSec * 1000L else WatchProgressHelper.getSavedPosition(context, channel.streamUrl)
            val currentPos = playerManager.currentPosition.value
            // Show prompt only if saved position is >10s and player isn't already positioned at it
            if (savedMs > 10_000L && kotlin.math.abs(currentPos - savedMs) > 10_000L) {
                resumeNoticeMs = savedMs
            }
        }
    }

    val resizeModes = listOf(
        Pair("FILL (Vollbild gestreckt)", AspectRatioFrameLayout.RESIZE_MODE_FILL),
        Pair("ZOOM (Vollbild beschnitten)", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
        Pair("FIT (16:9 Standard)", AspectRatioFrameLayout.RESIZE_MODE_FIT)
    )

    val initialResizeIndex = remember(settings.aspectRatio) {
        when (settings.aspectRatio.uppercase()) {
            "ZOOM" -> 1
            "FIT" -> 2
            else -> 0 // Default: FILL (Vollbild gestreckt)
        }
    }
    var resizeModeIndex by remember { mutableIntStateOf(initialResizeIndex) }
    var hudMessage by remember { mutableStateOf<String?>(null) }
    var showStopRecordingConfirmDialog by remember { mutableStateOf(false) }
    var showRecordingTimerDialog by remember { mutableStateOf(false) }

    LaunchedEffect(hudMessage) {
        if (hudMessage != null) {
            delay(2200)
            hudMessage = null
        }
    }

    // Immersive True Fullscreen: Hide Android Status Bar and Navigation Bar while player is active
    val activity = context as? Activity
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val currentOrientation = configuration.orientation
    val currentScreenWidthDp = configuration.screenWidthDp
    val currentScreenHeightDp = configuration.screenHeightDp

    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var containerSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    DisposableEffect(activity, channel.id, currentOrientation) {
        val window = activity?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (currentOrientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            } else {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            playerManager.saveCurrentProgressNow()
            val window = activity?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                if (currentOrientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) {
                    WindowCompat.setDecorFitsSystemWindows(window, true)
                }
            }
        }
    }

    // Re-assert system bars for portrait vs landscape and force video surface resize on every sensor rotation / size change
    LaunchedEffect(currentOrientation, currentScreenWidthDp, currentScreenHeightDp, containerSize, resizeModeIndex) {
        val window = activity?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (currentOrientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            } else {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        val targetMode = resizeModes[resizeModeIndex].second
        playerViewRef?.let { pv ->
            refreshPlayerSurfaceLayout(pv, targetMode)
        }
        delay(60L)
        playerViewRef?.let { pv ->
            refreshPlayerSurfaceLayout(pv, targetMode)
        }
        delay(140L)
        playerViewRef?.let { pv ->
            refreshPlayerSurfaceLayout(pv, targetMode)
        }
    }

    androidx.activity.compose.BackHandler {
        if (showChannelDrawer) {
            showChannelDrawer = false
        } else if (showControls) {
            showControls = false
        } else {
            onClose()
        }
    }

    var currentSpeed by remember { mutableFloatStateOf(1.0f) }

    val currentMinute = (System.currentTimeMillis() / 30000)
    val (currentEpg, nextEpg) = remember(channel.tvgId, channel.name, settings.epgTimezone, settings.epgTimeOffsetHours, currentMinute, currentProgram, nextProgram) {
        if (currentProgram != null || nextProgram != null) {
            Pair(currentProgram, nextProgram)
        } else {
            FreeEpgService.getCurrentAndNextProgram(
                tvgId = channel.tvgId,
                channelName = channel.name,
                timezoneId = settings.epgTimezone,
                offsetHours = settings.epgTimeOffsetHours,
                targetTimeMs = System.currentTimeMillis()
            )
        }
    }

    // Auto-hide controls after 5 seconds
    LaunchedEffect(showControls, isPlaying, isDraggingSlider) {
        if (showControls && isPlaying && !isDraggingSlider) {
            delay(5000)
            showControls = false
        }
    }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionUp -> {
                            val curIdx = allChannels.indexOfFirst { it.id == channel.id }
                            if (curIdx > 0) {
                                onSelectChannel(allChannels[curIdx - 1])
                                hudMessage = "Sender: ${allChannels[curIdx - 1].name}"
                            }
                            true
                        }
                        Key.DirectionDown -> {
                            val curIdx = allChannels.indexOfFirst { it.id == channel.id }
                            if (curIdx >= 0 && curIdx < allChannels.size - 1) {
                                onSelectChannel(allChannels[curIdx + 1])
                                hudMessage = "Sender: ${allChannels[curIdx + 1].name}"
                            }
                            true
                        }
                        Key.DirectionLeft -> {
                            if (isVod) {
                                playerController?.seekBackward(10000L) ?: playerManager.seekBackward(10000L)
                                hudMessage = "⏪ -10s"
                            } else {
                                showChannelDrawer = !showChannelDrawer
                            }
                            true
                        }
                        Key.DirectionRight -> {
                            if (isVod) {
                                playerController?.seekForward(10000L) ?: playerManager.seekForward(10000L)
                                hudMessage = "⏩ +10s"
                            } else {
                                showChannelDrawer = !showChannelDrawer
                            }
                            true
                        }
                        Key.DirectionCenter, Key.Enter -> {
                            showControls = !showControls
                            true
                        }
                        Key.MediaPlayPause -> {
                            playerController?.togglePlay() ?: run {
                                if (isPlaying) playerManager.pause() else playerManager.resume()
                            }
                            true
                        }
                        Key.MediaPlay -> {
                            playerController?.resume() ?: playerManager.resume()
                            true
                        }
                        Key.MediaPause -> {
                            playerController?.pause() ?: playerManager.pause()
                            true
                        }
                        Key.Back, Key.Escape -> {
                            if (showChannelDrawer) {
                                showChannelDrawer = false
                                true
                            } else if (showControls) {
                                showControls = false
                                true
                            } else {
                                onClose()
                                true
                            }
                        }
                        else -> false
                    }
                } else false
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = {
                        // Double-tap on video screen toggles between Fullscreen (FILL) and 16:9 (FIT)
                        resizeModeIndex = if (resizeModeIndex == 2) 0 else 2
                        hudMessage = if (resizeModeIndex == 2) "16:9 Standard (FIT)" else "Vollbild aktiviert (FILL)"
                    }
                )
            }
            .testTag("nexus_player_view")
    ) {
        // Video Surface via AndroidView (TextureView-backed for artifact-free sensor rotation)
        AndroidView(
            factory = { ctx ->
                val pv = try {
                    android.view.LayoutInflater.from(ctx)
                        .inflate(com.example.R.layout.nexus_player_texture_view, null, false) as PlayerView
                } catch (_: Exception) {
                    PlayerView(ctx)
                }
                pv.apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.Gravity.CENTER
                    )
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    keepScreenOn = true
                    resizeMode = resizeModes[resizeModeIndex].second
                    player = playerManager.getOrCreatePlayer(
                        settings.cachingMode,
                        settings.backgroundAudioPlayback
                    )
                    addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                        val newW = right - left
                        val newH = bottom - top
                        val oldW = oldRight - oldLeft
                        val oldH = oldBottom - oldTop
                        if (newW > 0 && newH > 0 && (newW != oldW || newH != oldH)) {
                            post {
                                val contentFrame = findViewById<AspectRatioFrameLayout>(
                                    androidx.media3.ui.R.id.exo_content_frame
                                )
                                contentFrame?.layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.Gravity.CENTER
                                )
                                videoSurfaceView?.layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.Gravity.CENTER
                                )
                                videoSurfaceView?.requestLayout()
                                contentFrame?.requestLayout()
                            }
                        }
                    }
                    playerViewRef = this
                }
            },
            update = { playerView ->
                playerViewRef = playerView
                playerView.keepScreenOn = true
                if (playerView.player == null) {
                    playerView.player = playerManager.getOrCreatePlayer(
                        settings.cachingMode,
                        settings.backgroundAudioPlayback
                    )
                }
                val targetMode = resizeModes[resizeModeIndex].second
                if (playerView.resizeMode != targetMode) {
                    refreshPlayerSurfaceLayout(playerView, targetMode)
                }
            },
            onRelease = { playerView ->
                if (playerViewRef === playerView) {
                    playerViewRef = null
                }
                playerView.player = null
            },
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { newSize ->
                    if (newSize != containerSize && newSize.width > 0 && newSize.height > 0) {
                        containerSize = newSize
                    }
                }
        )

        // Radio Station Visualizer Canvas
        if (channel.type == ContentType.RADIO) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                DarkSurfaceElevated.copy(alpha = 0.95f),
                                DarkSurface,
                                ObsidianBlack
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(130.dp)
                            .clip(CircleShape)
                            .background(DarkNavyCard)
                            .border(3.dp, GoldPrimary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!channel.logoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = channel.logoUrl,
                                contentDescription = channel.name,
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Radio,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = channel.name,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${channel.groupTitle} • ${channel.resolutionBadge.ifBlank { "Live Radio" }}",
                        fontSize = 14.sp,
                        color = GoldLight
                    )

                    if (!radioSongTitle.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = DarkSurfaceElevated.copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "🎵 $radioSongTitle",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }

                    if (isRecording && !radioRecordingInfo.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = AccentRose.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, AccentRose)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(AccentRose)
                                )
                                Text(
                                    text = "REC Original-Qualität: $radioRecordingInfo",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // Buffering Indicator in Center
        if (isBuffering && !isRecording) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(ObsidianBlack.copy(alpha = 0.7f))
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = GoldPrimary,
                    modifier = Modifier.size(44.dp),
                    strokeWidth = 3.dp
                )
            }
        }

        // Floating Live Recording badge (Livestream plays smoothly while recording simultaneously)
        if (isRecording) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 16.dp, end = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(ObsidianBlack.copy(alpha = 0.85f))
                    .border(1.dp, AccentRose, RoundedCornerShape(20.dp))
                    .clickable { showStopRecordingConfirmDialog = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(AccentRose)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (channel.type == ContentType.RADIO) {
                            "REC • ${radioRecordingInfo ?: "MP3"}"
                        } else {
                            "REC • MP4"
                        },
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // HUD Status Message Badge (e.g. Fullscreen Toggle or Format Change)
        AnimatedVisibility(
            visible = hudMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 70.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(ObsidianBlack.copy(alpha = 0.85f))
                    .border(1.dp, GoldPrimary, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = hudMessage ?: "",
                    color = GoldLight,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Nexus HUD Overlay Controls
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                ObsidianBlack.copy(alpha = 0.85f),
                                Color.Transparent,
                                ObsidianBlack.copy(alpha = 0.9f)
                            )
                        )
                    )
            ) {
                // TOP BAR: Back, Channel Name & Logos, External Players
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(DarkSurface)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Schließen",
                                tint = GoldPrimary
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = channel.name,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${channel.groupTitle} • Nexus Player Engine • ${settings.cachingMode.titleDe}",
                                fontSize = 11.sp,
                                color = GoldLight
                            )
                        }
                    }

                    // Top Action Icons
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val activeChannel = allChannels.find { it.id == channel.id } ?: channel

                        // Fallback Status Badge
                        if (playbackStatus.isFallbackActive && playbackStatus.availableStreams.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(AccentEmerald.copy(alpha = 0.2f))
                                    .border(1.dp, AccentEmerald.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = AccentEmerald,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Backup ${playbackStatus.currentStreamIndex + 1}/${playbackStatus.availableStreams.size}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentEmerald
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        // Stream Selection Button
                        if (playbackStatus.availableStreams.isNotEmpty()) {
                            IconButton(
                                onClick = { showStreamSelectionDialog = true },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (playbackStatus.isFallbackActive) AccentEmerald.copy(alpha = 0.25f) else DarkSurface)
                                    .border(
                                        1.dp,
                                        if (playbackStatus.isFallbackActive) AccentEmerald.copy(alpha = 0.6f) else BorderGlow.copy(alpha = 0.2f),
                                        CircleShape
                                    )
                                    .testTag("player_stream_selection_toggle")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Layers,
                                    contentDescription = "Streams & Quellen",
                                    tint = if (playbackStatus.isFallbackActive) AccentEmerald else GoldLight,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        // Favorite Toggle Button for currently playing channel
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (activeChannel.isFavorite) com.example.ui.theme.AccentRose.copy(alpha = 0.25f) else DarkSurface)
                                .border(
                                    1.dp,
                                    if (activeChannel.isFavorite) com.example.ui.theme.AccentRose.copy(alpha = 0.6f) else BorderGlow.copy(alpha = 0.2f),
                                    CircleShape
                                )
                                .testTag("player_favorite_toggle")
                        ) {
                            Icon(
                                imageVector = if (activeChannel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (activeChannel.isFavorite) "Aus Favoriten entfernen" else "Zu Favoriten hinzufügen",
                                tint = if (activeChannel.isFavorite) com.example.ui.theme.AccentRose else GoldLight,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Quick Channel Drawer Toggle
                        IconButton(
                            onClick = { showChannelDrawer = !showChannelDrawer },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(DarkSurface)
                        ) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = "Senderliste",
                                tint = GoldLight
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Universal Cast Symbol (einziges sichtbares Cast-Element im Player)
                        IconButton(
                            onClick = { showCastDialog = true },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (castState.isConnected) AccentCyan.copy(alpha = 0.22f) else DarkSurface)
                                .border(
                                    1.dp,
                                    if (castState.isConnected) AccentCyan else BorderGlow.copy(alpha = 0.2f),
                                    CircleShape
                                )
                                .testTag("player_cast_button")
                        ) {
                            Icon(
                                imageVector = if (castState.isConnected) Icons.Default.CastConnected else Icons.Default.Cast,
                                contentDescription = if (isEnglish) "Cast" else "Auf Gerät übertragen (Cast)",
                                tint = if (castState.isConnected) AccentCyan else GoldLight,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // External Player Launcher Dropdown
                        Box {
                            IconButton(
                                onClick = { showExternalMenu = true },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(DarkSurface)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = "Externer Player",
                                    tint = AccentCyan
                                )
                            }

                            DropdownMenu(
                                expanded = showExternalMenu,
                                onDismissRequest = { showExternalMenu = false },
                                modifier = Modifier.background(DarkNavyCard)
                            ) {
                                PlayerEngine.values().forEach { engine ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = engine.displayName,
                                                color = if (engine == settings.preferredPlayer) GoldPrimary else TextPrimary
                                            )
                                        },
                                        onClick = {
                                            showExternalMenu = false
                                            if (engine != PlayerEngine.BUILT_IN_NEXUS) {
                                                playerManager.pause()
                                                val targetUrl = playerManager.activeStreamUrl.ifBlank { channel.streamUrl }
                                                PlayerManager.launchExternalPlayer(
                                                    context = context,
                                                    engine = engine,
                                                    streamUrl = targetUrl,
                                                    title = channel.name,
                                                    positionMs = playerManager.currentPosition.value
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // CENTER CONTROLS: Replay 10s, Play/Pause, Forward 10s
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { playerManager.seekBackward(10000L) },
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(DarkSurface.copy(alpha = 0.8f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "-10s",
                            tint = TextPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(GoldPrimary)
                            .clickable {
                                if (isPlaying) playerManager.pause() else playerManager.resume()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    IconButton(
                        onClick = { playerManager.seekForward(10000L) },
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(DarkSurface.copy(alpha = 0.8f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "+10s",
                            tint = TextPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // BOTTOM BAR: Live EPG + Player Controls (Aspect ratio, Audio, Speed, Record, Background audio)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Resume Playback Prompt Banner (for Movies and Series)
                    if (isVod && resumeNoticeMs != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkNavyCard.copy(alpha = 0.95f))
                                .border(1.dp, GoldPrimary, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Fortsetzen bei ${formatVodTime(resumeNoticeMs!!)}?",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GoldLight
                                    )
                                    Text(
                                        text = "Du hast dieses Video bereits begonnen",
                                        fontSize = 10.sp,
                                        color = TextMuted
                                    )
                                }
                                Row {
                                    Button(
                                        onClick = {
                                            val pos = resumeNoticeMs!!
                                            playerManager.seekTo(pos)
                                            resumeNoticeMs = null
                                            hudMessage = "Fortgesetzt bei ${formatVodTime(pos)}"
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text("Fortsetzen", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    OutlinedButton(
                                        onClick = { resumeNoticeMs = null },
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("Von Anfang", fontSize = 11.sp, color = TextSecondary)
                                    }
                                }
                            }
                        }
                    }

                    // VOD Film Statusleiste & Scrubber (Replaces EPG for Movies and Series)
                    if (isVod) {
                        val displayPos = if (isDraggingSlider) dragPositionMs else currentPosition.coerceAtLeast(0L)
                        val safeDur = if (duration > 0L) duration else 1L
                        val progressFraction = (displayPos.toFloat() / safeDur.toFloat()).coerceIn(0f, 1f)
                        val remainingMs = (safeDur - displayPos).coerceAtLeast(0L)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkSurface.copy(alpha = 0.95f))
                                .border(1.dp, GoldPrimary.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Column {
                                // Title & Timestamps
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f, fill = false)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(GoldPrimary.copy(alpha = 0.2f))
                                                .border(1.dp, GoldPrimary.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (channel.type == ContentType.SERIES) "SERIE" else "FILM",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = GoldLight
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = channel.name,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = formatVodTime(displayPos),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = GoldLight
                                        )
                                        Text(
                                            text = " / ${formatVodTime(duration)}",
                                            fontSize = 12.sp,
                                            color = TextSecondary
                                        )
                                        Text(
                                            text = "  (-${formatVodTime(remainingMs)})",
                                            fontSize = 11.sp,
                                            color = TextMuted
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Timeline Scrubber Slider (Click & Drag to seek anywhere!)
                                Slider(
                                    value = progressFraction,
                                    onValueChange = { frac ->
                                        isDraggingSlider = true
                                        dragPositionMs = (frac * safeDur).toLong()
                                    },
                                    onValueChangeFinished = {
                                        playerManager.seekTo(dragPositionMs)
                                        isDraggingSlider = false
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor = GoldPrimary,
                                        activeTrackColor = GoldPrimary,
                                        inactiveTrackColor = BorderGlow.copy(alpha = 0.35f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(28.dp)
                                        .testTag("vod_timeline_slider")
                                )

                                // Quick Jump & Playback Action Buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // -30s
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(DarkNavyCard)
                                            .border(1.dp, BorderGlow.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                            .clickable { playerManager.seekBackward(30000L) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("-30s", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))

                                    // -10s
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(DarkNavyCard)
                                            .border(1.dp, BorderGlow.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                            .clickable { playerManager.seekBackward(10000L) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("-10s", fontSize = 11.sp, color = GoldLight, fontWeight = FontWeight.SemiBold)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))

                                    // Play / Pause mini toggle
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(GoldPrimary)
                                            .clickable {
                                                if (isPlaying) playerManager.pause() else playerManager.resume()
                                            }
                                            .padding(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isPlaying) "Pause" else "Play",
                                            tint = Color.Black,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    // +10s
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(DarkNavyCard)
                                            .border(1.dp, BorderGlow.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                            .clickable { playerManager.seekForward(10000L) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("+10s", fontSize = 11.sp, color = GoldLight, fontWeight = FontWeight.SemiBold)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))

                                    // +30s
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(DarkNavyCard)
                                            .border(1.dp, BorderGlow.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                            .clickable { playerManager.seekForward(30000L) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("+30s", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    } else if (currentEpg != null) {
                        // Live EPG Info Panel (ONLY FOR LIVE BROADCAST TV)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurface.copy(alpha = 0.9f))
                                .border(1.dp, BorderGlow.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Aktuell: ${currentEpg.title} (${FreeEpgService.formatProgramRange(currentEpg.startTime, currentEpg.endTime, settings.epgTimezone)})",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${(currentEpg.progress * 100).toInt()}%",
                                        fontSize = 11.sp,
                                        color = GoldLight,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                LinearProgressIndicator(
                                    progress = { currentEpg.progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = GoldPrimary,
                                    trackColor = BorderGlow.copy(alpha = 0.2f)
                                )

                                if (nextEpg != null) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Danach: ${nextEpg.title}",
                                        fontSize = 10.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Feature Buttons Row (Scrollable horizontally in portrait mode so no button is squished or distorted)
                    val bottomBarScrollState = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(bottomBarScrollState),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Fullscreen / Vollbild Toggle Button
                        Box(
                            modifier = Modifier
                                .height(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (resizeModeIndex != 2) GoldPrimary.copy(alpha = 0.22f) else DarkSurface)
                                .border(
                                    1.dp,
                                    if (resizeModeIndex != 2) GoldPrimary else BorderGlow,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    resizeModeIndex = if (resizeModeIndex == 2) 0 else 2
                                    hudMessage = if (resizeModeIndex == 2) "16:9 Standard (FIT)" else "Vollbild aktiviert (FILL)"
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (resizeModeIndex != 2) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = "Vollbild umschalten",
                                    tint = if (resizeModeIndex != 2) GoldPrimary else GoldLight,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (resizeModeIndex != 2) "Vollbild" else "16:9",
                                    fontSize = 11.sp,
                                    color = if (resizeModeIndex != 2) GoldPrimary else TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        // 2. Aspect Ratio Cycle Button
                        Box(
                            modifier = Modifier
                                .height(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkSurface)
                                .border(1.dp, BorderGlow, RoundedCornerShape(8.dp))
                                .clickable {
                                    resizeModeIndex = (resizeModeIndex + 1) % resizeModes.size
                                    hudMessage = when (resizeModeIndex) {
                                        0 -> "Vollbild (Gestreckt / Fill)"
                                        1 -> "Vollbild (Beschnitten / Zoom)"
                                        else -> "16:9 Standard (Fit)"
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AspectRatio,
                                    contentDescription = "Format",
                                    tint = GoldLight,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = resizeModes[resizeModeIndex].first.take(4),
                                    fontSize = 11.sp,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        // 3. Audio Track Selector (Tonspuren)
                        Box(modifier = Modifier.height(34.dp)) {
                            Box(
                                modifier = Modifier
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkSurface)
                                    .border(1.dp, BorderGlow, RoundedCornerShape(8.dp))
                                    .clickable { showAudioMenu = true }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Audiotrack,
                                        contentDescription = "Audio",
                                        tint = AccentCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Tonspur",
                                        fontSize = 11.sp,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showAudioMenu,
                                onDismissRequest = { showAudioMenu = false },
                                modifier = Modifier.background(DarkNavyCard)
                            ) {
                                if (audioTrackOptions.isNotEmpty()) {
                                    audioTrackOptions.forEach { opt ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = opt.title,
                                                        color = if (opt.isSelected) GoldPrimary else TextPrimary,
                                                        fontWeight = if (opt.isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        fontSize = 12.sp
                                                    )
                                                    if (opt.isSelected) {
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Icon(
                                                            imageVector = Icons.Default.CheckCircle,
                                                            contentDescription = "Aktiv",
                                                            tint = GoldPrimary,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                playerManager.selectAudioTrack(opt)
                                                hudMessage = "Tonspur: ${opt.title}"
                                                showAudioMenu = false
                                            }
                                        )
                                    }
                                } else {
                                    audioTracks.forEachIndexed { idx, track ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = track,
                                                    color = if (idx == selectedAudioIndex) GoldPrimary else TextPrimary,
                                                    fontSize = 12.sp
                                                )
                                            },
                                            onClick = {
                                                playerManager.setAudioTrack(idx)
                                                showAudioMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // 4. Subtitle / Untertitel Selector
                        Box(modifier = Modifier.height(34.dp)) {
                            Box(
                                modifier = Modifier
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSubtitlesEnabled) GoldPrimary.copy(alpha = 0.22f) else DarkSurface)
                                    .border(
                                        1.dp,
                                        if (isSubtitlesEnabled) GoldPrimary else BorderGlow,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { showSubtitleMenu = true }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Subtitles,
                                        contentDescription = "Untertitel",
                                        tint = if (isSubtitlesEnabled) GoldPrimary else TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isSubtitlesEnabled) "UT An" else "UT",
                                        fontSize = 11.sp,
                                        color = if (isSubtitlesEnabled) GoldLight else TextSecondary,
                                        fontWeight = if (isSubtitlesEnabled) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showSubtitleMenu,
                                onDismissRequest = { showSubtitleMenu = false },
                                modifier = Modifier.background(DarkNavyCard)
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Untertitel aus",
                                            color = if (!isSubtitlesEnabled) GoldPrimary else TextPrimary,
                                            fontSize = 12.sp
                                        )
                                    },
                                    onClick = {
                                        playerManager.selectSubtitleTrack(null)
                                        hudMessage = "Untertitel deaktiviert"
                                        showSubtitleMenu = false
                                    }
                                )
                                subtitleTrackOptions.forEach { opt ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = opt.title,
                                                    color = if (opt.isSelected) GoldPrimary else TextPrimary,
                                                    fontWeight = if (opt.isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    fontSize = 12.sp
                                                )
                                                if (opt.isSelected) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = "Aktiv",
                                                        tint = GoldPrimary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            playerManager.selectSubtitleTrack(opt)
                                            hudMessage = "Untertitel: ${opt.title}"
                                            showSubtitleMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // 5. Playback Speed
                        Box(modifier = Modifier.height(34.dp)) {
                            Box(
                                modifier = Modifier
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkSurface)
                                    .border(1.dp, BorderGlow, RoundedCornerShape(8.dp))
                                    .clickable { showSpeedMenu = true }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = "Speed",
                                        tint = GoldPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${currentSpeed}x",
                                        fontSize = 11.sp,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showSpeedMenu,
                                onDismissRequest = { showSpeedMenu = false },
                                modifier = Modifier.background(DarkNavyCard)
                            ) {
                                listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { sp ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "${sp}x",
                                                color = if (sp == currentSpeed) GoldPrimary else TextPrimary
                                            )
                                        },
                                        onClick = {
                                            currentSpeed = sp
                                            playerManager.setSpeed(sp)
                                            showSpeedMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // 6. Background Playback Toggle (Listen with display turned off)
                        Box(
                            modifier = Modifier
                                .height(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (settings.backgroundAudioPlayback) GoldPrimary.copy(alpha = 0.2f) else DarkSurface
                                )
                                .border(
                                    1.dp,
                                    if (settings.backgroundAudioPlayback) GoldPrimary else BorderGlow,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    val newState = !settings.backgroundAudioPlayback
                                    onUpdateBackgroundAudioPlayback?.invoke(newState)
                                    hudMessage = if (newState) {
                                        if (isEnglish) "Background audio: ON" else "Hintergrundton: Aktiviert"
                                    } else {
                                        if (isEnglish) "Background audio: OFF" else "Hintergrundton: Deaktiviert"
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Headphones,
                                    contentDescription = "Hintergrundton",
                                    tint = if (settings.backgroundAudioPlayback) GoldPrimary else TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isEnglish) "Bg Audio" else "Bg Playback",
                                    fontSize = 11.sp,
                                    color = if (settings.backgroundAudioPlayback) GoldLight else TextSecondary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        // 7. Live TV Aufnahme Button & Aufnahme Timer Button (Nur bei Live-TV Kanälen aktiv)
                        if (!isVod) {
                            Box(
                                modifier = Modifier
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isRecording) AccentRose else DarkSurface)
                                    .border(1.dp, if (isRecording) AccentRose else BorderGlow, RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (isRecording) {
                                            showStopRecordingConfirmDialog = true
                                        } else {
                                            onToggleRecording()
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .testTag("player_record_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.FiberManualRecord,
                                        contentDescription = "REC",
                                        tint = if (isRecording) Color.White else AccentRose,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = when {
                                            isRecording -> "AUFNAHME"
                                            channel.type == ContentType.RADIO -> "REC MP3"
                                            else -> "REC"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (isRecording) Color.White else TextPrimary,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            // Aufnahme Timer Button (Digitalanzeige für Start- & Endzeit)
                            Box(
                                modifier = Modifier
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkSurface)
                                    .border(1.dp, GoldPrimary.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                                    .clickable {
                                        showRecordingTimerDialog = true
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .testTag("player_record_timer_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = "Aufnahme Timer",
                                        tint = GoldPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isEnglish) "TIMER" else "AUFNAHME TIMER",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = GoldLight,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        } else if (!channel.streamUrl.startsWith("/") && !channel.streamUrl.startsWith("file://")) {
                            // VOD Offline Download Button (MP4 nach Downloads/MaDIPTVPrivat)
                            Box(
                                modifier = Modifier
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkSurface)
                                    .border(1.dp, AccentEmerald.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
                                    .clickable {
                                        onDownloadVod(channel)
                                        hudMessage = "⬇️ Download gestartet: Downloads/MaDIPTVPrivat"
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .testTag("player_vod_download_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "VOD Download",
                                        tint = AccentEmerald,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "DOWNLOAD MP4",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = AccentEmerald,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Quick Channel Zapping Drawer on the Right
        AnimatedVisibility(
            visible = showChannelDrawer,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(300.dp)
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                color = DarkNavyCard.copy(alpha = 0.95f),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlow)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEnglish) "Channel Switcher" else "Sender Zappen",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldPrimary
                        )
                        IconButton(onClick = { showChannelDrawer = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Schließen",
                                tint = TextSecondary
                            )
                        }
                    }

                    var drawerOnlyFavorites by remember { mutableStateOf(false) }
                    val liveChannels = remember(allChannels) { allChannels.filter { it.type == ContentType.LIVE_TV } }
                    val favLiveCount = remember(liveChannels) { liveChannels.count { it.isFavorite } }
                    val displayedChannels = remember(liveChannels, drawerOnlyFavorites) {
                        if (drawerOnlyFavorites) liveChannels.filter { it.isFavorite } else liveChannels
                    }

                    // Quick Tab Row: Alle vs. Favoriten
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (!drawerOnlyFavorites) GoldPrimary else DarkSurface)
                                .clickable { drawerOnlyFavorites = false }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isEnglish) "All (${liveChannels.size})" else "Alle (${liveChannels.size})",
                                fontSize = 11.sp,
                                fontWeight = if (!drawerOnlyFavorites) FontWeight.Bold else FontWeight.Normal,
                                color = if (!drawerOnlyFavorites) Color.Black else TextSecondary
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (drawerOnlyFavorites) com.example.ui.theme.AccentRose else DarkSurface)
                                .clickable { drawerOnlyFavorites = true }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "❤️ ${if (isEnglish) "Favorites" else "Favoriten"} ($favLiveCount)",
                                fontSize = 11.sp,
                                fontWeight = if (drawerOnlyFavorites) FontWeight.Bold else FontWeight.Normal,
                                color = if (drawerOnlyFavorites) Color.White else com.example.ui.theme.AccentRose
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (displayedChannels.isEmpty() && drawerOnlyFavorites) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "💔", fontSize = 28.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (isEnglish) "No Favorites Yet" else "Noch keine Favoriten",
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(displayedChannels, key = { it.id }) { ch ->
                                val isCurrent = ch.id == channel.id
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isCurrent) DarkSurfaceElevated else DarkSurface)
                                        .border(
                                            1.dp,
                                            if (isCurrent) GoldPrimary else BorderGlow.copy(alpha = 0.2f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            onSelectChannel(ch)
                                            showChannelDrawer = false
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            if (ch.isDach) {
                                                Text(text = "🇩🇪 ", fontSize = 11.sp)
                                            }
                                            Text(
                                                text = ch.name,
                                                fontSize = 13.sp,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isCurrent) GoldPrimary else TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        IconButton(
                                            onClick = { onToggleFavoriteChannel(ch) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (ch.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                contentDescription = "Favorit",
                                                tint = if (ch.isFavorite) com.example.ui.theme.AccentRose else TextMuted,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Generic Playback Error Notification (when not portal occupied)
        if (errorMessage != null && !isPortalOccupied && !isBuffering && !isPlaying) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 64.dp, start = 16.dp, end = 16.dp)
                    .testTag("player_error_banner")
            ) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkNavyCard.copy(alpha = 0.95f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentRose.copy(alpha = 0.8f)),
                    modifier = Modifier.widthIn(max = 500.dp).fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = AccentRose,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = errorMessage ?: "Wiedergabefehler",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { onSelectChannel(channel) },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(GoldPrimary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Erneut versuchen",
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Discrete VPN Activation Hint on connection issues
        if (vpnHintNotice != null && !isPortalOccupied) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = if (errorMessage != null && !isPlaying && !isBuffering) 140.dp else 68.dp, start = 16.dp, end = 16.dp)
                    .testTag("player_vpn_hint_pill")
            ) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkNavyCard.copy(alpha = 0.94f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.7f)),
                    modifier = Modifier.widthIn(max = 500.dp).fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(GoldPrimary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = "VPN Schutz",
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = vpnHintNotice ?: "",
                                color = TextPrimary,
                                fontSize = 12.5.sp,
                                lineHeight = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { playerManager.dismissVpnHintNotice() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Schließen",
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Portal Occupied Warning Overlay
        if (isPortalOccupied) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.88f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        playerManager.setPortalOccupied(false, null)
                        onClose()
                    }
                    .padding(16.dp)
                    .testTag("portal_occupied_overlay"),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkNavyCard),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, AccentRose),
                    modifier = Modifier
                        .widthIn(max = 480.dp)
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* Consume click inside card */ }
                        .testTag("portal_occupied_dialog")
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // Top Right X Button to close the dialog
                        IconButton(
                            onClick = {
                                playerManager.setPortalOccupied(false, null)
                                onClose()
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(36.dp)
                                .testTag("portal_occupied_close_x_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = if (isEnglish) "Close" else "Schließen",
                                tint = TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, start = 20.dp, end = 20.dp, bottom = 20.dp)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(CircleShape)
                                    .background(AccentRose.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Portal belegt",
                                    tint = AccentRose,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            val isVpnOrBlocked = portalOccupiedMessage?.contains("VPN", ignoreCase = true) == true ||
                                    portalOccupiedMessage?.contains("Status 456", ignoreCase = true) == true ||
                                    portalOccupiedMessage?.contains("Bad HTTP", ignoreCase = true) == true ||
                                    portalOccupiedMessage?.contains("Geoblocking", ignoreCase = true) == true ||
                                    portalOccupiedMessage?.contains("gesperrt", ignoreCase = true) == true

                            Text(
                                text = when {
                                    isVpnOrBlocked -> if (isEnglish) "Access Blocked / VPN Required" else "Zugriff blockiert (Status 456 / VPN)"
                                    else -> if (isEnglish) "Portal Occupied" else "Portal belegt"
                                },
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = portalOccupiedMessage
                                    ?: if (isEnglish) "This portal or MAC address is currently in use on another device or the connection limit has been reached."
                                    else "Dieses Portal bzw. diese MAC-Adresse wird derzeit bereits auf einem anderen Gerät genutzt oder das Verbindungslimit wurde erreicht.",
                                fontSize = 14.sp,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DarkSurfaceElevated,
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlow.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = GoldLight,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = when {
                                            isVpnOrBlocked -> if (isEnglish)
                                                "Status 456 means the server rejected the IP. If your VPN is on, try a different server or IP location."
                                            else
                                                "Status 456 bedeutet: Der Server lehnt die IP-Adresse ab (Geo-Block oder gesperrte VPN-IP). Bei aktivem VPN bitte einen anderen Server/Standort wählen."
                                            else -> if (isEnglish)
                                                "Please stop playback on the other device or switch to a different channel."
                                            else
                                                "Bitte beenden Sie die Wiedergabe auf dem anderen Gerät oder wählen Sie einen anderen Sender."
                                        },
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        lineHeight = 16.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        playerManager.setPortalOccupied(false, null)
                                        onClose()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("portal_occupied_close_button"),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlow),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                                ) {
                                    Text(if (isEnglish) "Close" else "Schließen")
                                }

                                Button(
                                    onClick = {
                                        showChannelDrawer = true
                                    },
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .testTag("portal_occupied_switch_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlow)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.List,
                                        contentDescription = null,
                                        tint = GoldPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isEnglish) "Channels" else "Senderliste",
                                        color = TextPrimary,
                                        fontSize = 13.sp
                                    )
                                }

                                Button(
                                    onClick = {
                                        playerManager.setPortalOccupied(false)
                                        onSelectChannel(channel)
                                    },
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .testTag("portal_occupied_retry_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isEnglish) "Retry" else "Erneut prüfen",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Aufnahme Stoppen Dialog
        if (showStopRecordingConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showStopRecordingConfirmDialog = false },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEnglish) "Stop Recording?" else "Aufnahme stoppen?",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        IconButton(
                            onClick = { showStopRecordingConfirmDialog = false },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = if (isEnglish) "Close" else "Schließen",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                text = {
                    Text(
                        text = if (channel.type == ContentType.RADIO) {
                            if (isEnglish) {
                                "Do you want to stop the radio recording for '${channel.name}' and save it in original stream quality to Downloads/MaDIPTVPublic/Radio?"
                            } else {
                                "Möchtest du die laufende Radio-Aufnahme von '${channel.name}' beenden und in Original-Streamqualität (MP3) in Downloads/MaDIPTVPublic/Radio speichern?"
                            }
                        } else if (isEnglish) {
                            "Do you want to stop the recording for '${channel.name}' and save it as MP4 to Downloads/MaDIPTVPublic? Live TV playback will continue uninterrupted."
                        } else {
                            "Möchtest du die laufende Aufnahme von '${channel.name}' beenden und als MP4 in Downloads/MaDIPTVPublic speichern? Die Live-TV-Wiedergabe läuft dabei ununterbrochen weiter."
                        },
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showStopRecordingConfirmDialog = false
                            onToggleRecording()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRose)
                    ) {
                        Text(
                            text = if (isEnglish) "Stop & Save" else "Aufnahme beenden",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showStopRecordingConfirmDialog = false },
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlow)
                    ) {
                        Text(
                            text = if (isEnglish) "Continue Recording" else "Weiter aufnehmen",
                            color = TextPrimary
                        )
                    }
                },
                containerColor = DarkNavyCard
            )
        }

        // Aufnahme Timer Digitalanzeige Dialog
        if (showRecordingTimerDialog) {
            RecordingTimerDialog(
                channelName = channel.name,
                isEnglish = isEnglish,
                onDismiss = { showRecordingTimerDialog = false },
                onConfirmTimer = { startH, startM, endH, endM ->
                    showRecordingTimerDialog = false
                    onScheduleRecording(startH, startM, endH, endM)
                    hudMessage = String.format(
                        java.util.Locale.getDefault(),
                        "⏱️ Timer gesetzt: %02d:%02d – %02d:%02d Uhr",
                        startH, startM, endH, endM
                    )
                }
            )
        }

        // Stream Selection & Multi-Stream Failover Dialog
        if (showStreamSelectionDialog) {
            StreamSelectionDialog(
                status = playbackStatus,
                onSelectStream = { index ->
                    playerController?.selectStreamManually(index)
                },
                onDismiss = { showStreamSelectionDialog = false },
                isEnglish = isEnglish
            )
        }

        // Universal Cast Control Sheet (Google Cast, DLNA, Roku, AirPlay, Kodi, Web-Cast, Miracast)
        if (showCastDialog) {
            CastControlSheet(
                currentMedia = currentCastMedia,
                onDismiss = { showCastDialog = false },
                onCastStartedPauseLocal = { playerManager.pause() },
                isEnglish = isEnglish
            )
        }
    }
}

private fun formatVodTime(timeMs: Long): String {
    if (timeMs <= 0L) return "00:00"
    val totalSec = timeMs / 1000L
    val hours = totalSec / 3600L
    val minutes = (totalSec % 3600L) / 60L
    val seconds = totalSec % 60L
    return if (hours > 0L) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
    }
}

@OptIn(UnstableApi::class)
private fun refreshPlayerSurfaceLayout(playerView: PlayerView, targetResizeMode: Int) {
    playerView.layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
        android.view.Gravity.CENTER
    )
    val contentFrame = playerView.findViewById<AspectRatioFrameLayout>(
        androidx.media3.ui.R.id.exo_content_frame
    )
    contentFrame?.layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
        android.view.Gravity.CENTER
    )
    playerView.videoSurfaceView?.layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
        android.view.Gravity.CENTER
    )
    // Force AspectRatioFrameLayout to recalculate dimensions even when resizeMode enum didn't change
    if (playerView.resizeMode == targetResizeMode) {
        playerView.resizeMode = if (targetResizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
            AspectRatioFrameLayout.RESIZE_MODE_FILL
        } else {
            AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }
    playerView.resizeMode = targetResizeMode
    playerView.videoSurfaceView?.requestLayout()
    playerView.videoSurfaceView?.invalidate()
    contentFrame?.requestLayout()
    contentFrame?.invalidate()
    playerView.requestLayout()
    playerView.invalidate()
}

