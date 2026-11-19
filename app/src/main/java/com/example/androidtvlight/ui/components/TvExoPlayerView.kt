package com.example.androidtvlight.ui.components

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.example.androidtvlight.domain.model.ExoPlayerFeatureState
import com.example.androidtvlight.domain.model.ExoTrackInfo
import com.example.androidtvlight.domain.model.TvChannelItem
import com.example.androidtvlight.service.TvMediaPlaybackService
import com.example.androidtvlight.service.TvPlayerManager
import com.example.androidtvlight.ui.theme.TvBrightSky
import com.example.androidtvlight.ui.theme.TvCoralAlert
import com.example.androidtvlight.ui.theme.TvElectricCyan
import com.example.androidtvlight.ui.theme.TvEmeraldLive
import com.example.androidtvlight.ui.theme.TvGoldenFocus
import com.example.androidtvlight.ui.theme.TvOnPrimaryDark
import kotlinx.coroutines.delay

/**
 * Full-Featured Jetpack Media3 ExoPlayer Suite for Android & Android TV:
 * 1. Premium Feature: Background Playback via [TvMediaPlaybackService] & [TvPlayerManager]
 * 2. Zero-Interruption Rotation: Singleton ExoPlayer never stops or reloads when rotating phone
 * 3. Fullscreen Button Placed #1 First in the ExoPlayer feature bar
 * 4. Auto-Hiding OSD in Fullscreen so only the pure TV broadcast is visible
 */
@OptIn(UnstableApi::class)
@Composable
fun TvExoPlayerView(
    channel: TvChannelItem,
    playerState: ExoPlayerFeatureState,
    allChannelsInRubrik: List<TvChannelItem>,
    streamServerCount: Int,
    onSelectChannel: (TvChannelItem) -> Unit,
    onPreviousChannel: () -> Unit,
    onNextChannel: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onToggleMute: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onCycleResizeMode: () -> Unit,
    onCyclePlaybackSpeed: () -> Unit,
    onToggleSubtitles: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onToggleRecording: () -> Unit,
    onDismissRecordingBanner: () -> Unit,
    onToggleBackgroundPlayback: () -> Unit,
    onSwitchStreamServer: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShowTrackSelector: (Boolean) -> Unit,
    onToggleZapperOverlay: () -> Unit,
    onPlayerStatusChanged: (
        isPlaying: Boolean?,
        isBuffering: Boolean?,
        resolution: String?,
        bitrate: String?,
        videoTracks: List<ExoTrackInfo>?,
        audioTracks: List<ExoTrackInfo>?,
        subtitleTracks: List<ExoTrackInfo>?
    ) -> Unit,
    onStreamError: (String) -> Unit,
    onOpenRecordingsDialog: () -> Unit = {},
    onOpenEpg: () -> Unit = {},
    onReturnToLiveTv: () -> Unit = {},
    onExitApp: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var controlsVisible by remember { mutableStateOf(true) }

    // Auto-hide controls after 3.5 seconds when playing so fullscreen is 100% distraction-free
    LaunchedEffect(controlsVisible, playerState.isPlaying, playerState.showChannelZapperOverlay) {
        if (controlsVisible && playerState.isPlaying && !playerState.showChannelZapperOverlay) {
            delay(3_500L)
            controlsVisible = false
        }
    }

    // Auto-dismiss saved recording banner after 5 seconds
    LaunchedEffect(playerState.recordingSavedBanner) {
        if (playerState.recordingSavedBanner != null) {
            delay(5_000L)
            onDismissRecordingBanner()
        }
    }

    // Use the persistent app-wide ExoPlayer singleton so rotating the phone NEVER interrupts playback
    val exoPlayer = remember { TvPlayerManager.getOrCreatePlayer(context) }
    var videoAspectRatio by remember { mutableStateOf(16f / 9f) }

    // Always start the Foreground MediaSessionService so the OS never kills or throttles stream after 10s on screen-off
    LaunchedEffect(Unit, playerState.isBackgroundPlaybackEnabled, playerState.isPreventStandbyEnabled, playerState.isRecordingMp4) {
        TvPlayerManager.ensureForegroundServiceStarted(context)
    }

    val activeRecording = playerState.activeRecording

    // Bind media item whenever the channel, stream URL, or selected MP4 recording changes — clear previous frame and play immediately!
    LaunchedEffect(playerState.currentStreamUrl, channel.id, activeRecording?.id, activeRecording?.playbackUri) {
        val targetUri = activeRecording?.playbackUri
            ?: playerState.currentStreamUrl.ifBlank { channel.primaryStreamUrl }
        val targetMediaId = if (activeRecording != null) {
            "rec_${activeRecording.id}"
        } else {
            channel.id
        }

        if (targetUri.isNotBlank()) {
            TvPlayerManager.ensureForegroundServiceStarted(context)
            val currentUri = exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString()
            val currentMediaId = exoPlayer.currentMediaItem?.mediaId
            if (currentUri != targetUri || currentMediaId != targetMediaId) {
                TvPlayerManager.isUserPlaying = true
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                val metadata = if (activeRecording != null) {
                    MediaMetadata.Builder()
                        .setTitle("Aufnahme: ${activeRecording.channelTitle}")
                        .setSubtitle("${activeRecording.formattedDate} • ${activeRecording.formattedSize}")
                        .build()
                } else {
                    MediaMetadata.Builder()
                        .setTitle(channel.name)
                        .setSubtitle(channel.currentShow)
                        .setArtworkUri(Uri.parse(channel.logoUrl))
                        .build()
                }
                val mediaItem = MediaItem.Builder()
                    .setUri(targetUri)
                    .setMediaId(targetMediaId)
                    .setMediaMetadata(metadata)
                    .build()
                exoPlayer.setMediaItem(mediaItem, true)
                exoPlayer.playWhenReady = true
                exoPlayer.prepare()
                exoPlayer.play()
            } else if (!exoPlayer.isPlaying && playerState.isPlaying) {
                TvPlayerManager.isUserPlaying = true
                exoPlayer.playWhenReady = true
                if (exoPlayer.playbackState == Player.STATE_IDLE || exoPlayer.playbackState == Player.STATE_ENDED) {
                    exoPlayer.prepare()
                }
                exoPlayer.play()
            }
        }
    }

    // Synchronize Play/Pause
    LaunchedEffect(playerState.isPlaying) {
        TvPlayerManager.isUserPlaying = playerState.isPlaying
        if (exoPlayer.playWhenReady != playerState.isPlaying) {
            exoPlayer.playWhenReady = playerState.isPlaying
            if (playerState.isPlaying) {
                exoPlayer.play()
            } else {
                exoPlayer.pause()
            }
        }
    }

    // Synchronize Volume & Mute
    LaunchedEffect(playerState.isMuted, playerState.volume) {
        exoPlayer.volume = if (playerState.isMuted) 0f else playerState.volume
    }

    // Synchronize Playback Speed
    LaunchedEffect(playerState.playbackSpeed) {
        exoPlayer.playbackParameters = PlaybackParameters(playerState.playbackSpeed)
    }

    // Synchronize Subtitles Enable/Disable
    LaunchedEffect(playerState.isSubtitlesEnabled) {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !playerState.isSubtitlesEnabled)
            .build()
    }

    // Attach Player.Listener without releasing the shared singleton on configuration changes
    DisposableEffect(exoPlayer, playerState.isBackgroundPlaybackEnabled, playerState.isRecordingMp4) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val buffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY && exoPlayer.playWhenReady) {
                    exoPlayer.play()
                }
                onPlayerStatusChanged(exoPlayer.playWhenReady, buffering, null, null, null, null, null)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && TvPlayerManager.isUserPlaying &&
                    reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
                ) {
                    exoPlayer.playWhenReady = true
                    exoPlayer.play()
                    return
                }
                onPlayerStatusChanged(playWhenReady, null, null, null, null, null, null)
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val ratio = (videoSize.width * videoSize.pixelWidthHeightRatio) / videoSize.height.toFloat()
                    if (ratio > 0.1f) {
                        videoAspectRatio = ratio
                    }
                    val qualityBadge = when {
                        videoSize.height >= 1080 -> "Full HD (${videoSize.width}x${videoSize.height})"
                        videoSize.height >= 720 -> "HD (${videoSize.width}x${videoSize.height})"
                        else -> "SD (${videoSize.width}x${videoSize.height})"
                    }
                    onPlayerStatusChanged(null, null, qualityBadge, null, null, null, null)
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                val videoList = mutableListOf<ExoTrackInfo>()
                val audioList = mutableListOf<ExoTrackInfo>()
                val subList = mutableListOf<ExoTrackInfo>()
                var activeBitrateLabel: String? = null

                tracks.groups.forEachIndexed { groupIdx, group ->
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val selected = group.isTrackSelected(i)
                        when (group.type) {
                            C.TRACK_TYPE_VIDEO -> {
                                val h = format.height
                                val br = if (format.bitrate > 0) "${format.bitrate / 1000} kbps" else "Auto"
                                if (selected && format.bitrate > 0) {
                                    activeBitrateLabel = br
                                }
                                val label = if (h > 0) "${h}p • $br" else "Auto Stream ($br)"
                                videoList.add(ExoTrackInfo("v_${groupIdx}_$i", groupIdx, i, label, selected))
                            }
                            C.TRACK_TYPE_AUDIO -> {
                                val lang = format.language?.uppercase() ?: "DE/AT"
                                val label = format.label ?: "Audiospur ${audioList.size + 1} ($lang)"
                                audioList.add(ExoTrackInfo("a_${groupIdx}_$i", groupIdx, i, label, selected))
                            }
                            C.TRACK_TYPE_TEXT -> {
                                val lang = format.language?.uppercase() ?: "UT"
                                val label = format.label ?: "Untertitel ($lang)"
                                subList.add(ExoTrackInfo("s_${groupIdx}_$i", groupIdx, i, label, selected))
                            }
                        }
                    }
                }
                onPlayerStatusChanged(
                    null,
                    null,
                    null,
                    activeBitrateLabel,
                    videoList,
                    audioList,
                    subList
                )
            }

            override fun onPlayerError(error: PlaybackException) {
                onStreamError("Stream-Hinweis (${error.errorCodeName}) – wechsle auf Ersatz-Server")
            }
        }
        exoPlayer.addListener(listener)

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            val act = context as? Activity
            val isChangingConfig = act?.isChangingConfigurations == true
            val inPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                act?.isInPictureInPictureMode == true
            } else {
                false
            }
            val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
            val isDisplayOff = pm?.isInteractive == false

            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    if (!isChangingConfig && !inPip) {
                        val keepPlayingInBackground = isDisplayOff ||
                            playerState.isBackgroundPlaybackEnabled ||
                            playerState.isPreventStandbyEnabled ||
                            playerState.isRecordingMp4

                        if (keepPlayingInBackground) {
                            TvPlayerManager.ensureForegroundServiceStarted(context)
                            TvPlayerManager.onScreenTurnedOff(context)
                        } else {
                            exoPlayer.pause()
                        }
                    }
                }
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> {
                    if (playerState.isPlaying && !exoPlayer.isPlaying) {
                        exoPlayer.playWhenReady = true
                        exoPlayer.play()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            exoPlayer.removeListener(listener)
        }
    }

    val recMins = playerState.recordingElapsedSeconds / 60
    val recSecs = playerState.recordingElapsedSeconds % 60
    val recTimeFormatted = "%02d:%02d".format(recMins, recSecs)
    val recMbFormatted = "%.1f MB".format(playerState.recordingBytesWritten / (1024f * 1024f))

    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { controlsVisible = !controlsVisible },
                    onDoubleTap = { offset ->
                        if (offset.x < size.width / 2f) {
                            exoPlayer.seekTo((exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L))
                        } else {
                            exoPlayer.seekTo(exoPlayer.currentPosition + 10_000L)
                        }
                        controlsVisible = true
                    }
                )
            }
            .testTag("exoplayer_container")
    ) {
        // Custom AspectRatioFrameLayout + SurfaceView bound via TvPlayerManager.bindSurfaceView:
        // Hot-swaps to offscreen GL surface on screen-off without resetting tracks or audio buffer!
        AndroidView(
            factory = { ctx ->
                androidx.media3.ui.AspectRatioFrameLayout(ctx).apply {
                    keepScreenOn = true
                    setAspectRatio(videoAspectRatio)
                    resizeMode = playerState.resizeMode.modeValue
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.Gravity.CENTER
                    )
                    val surfaceView = android.view.SurfaceView(ctx).apply {
                        keepScreenOn = true
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.Gravity.CENTER
                        )
                    }
                    addView(surfaceView)
                    TvPlayerManager.bindSurfaceView(ctx, surfaceView)
                }
            },
            update = { container ->
                container.keepScreenOn = true
                container.setAspectRatio(videoAspectRatio)
                container.resizeMode = playerState.resizeMode.modeValue
            },
            modifier = Modifier.fillMaxSize()
        )

        // Saved Recording Notification Toast Banner
        if (playerState.recordingSavedBanner != null) {
            Surface(
                onClick = onDismissRecordingBanner,
                color = Color(0xFF0A2E24).copy(alpha = 0.95f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, TvEmeraldLive),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 14.dp, start = 16.dp, end = 16.dp)
                    .testTag("recording_saved_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "✓ ${playerState.recordingSavedBanner}",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }
        }

        // Buffering Indicator
        if (playerState.isBuffering || playerState.isResolvingPageStream) {
            Surface(
                color = Color(0xFF061326).copy(alpha = 0.85f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.5.dp, TvElectricCyan),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = TvElectricCyan,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Verbinde Live-TV: ${channel.name}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Text(
                            text = playerState.videoResolutionLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = TvBrightSky
                        )
                    }
                }
            }
        }

        // Error Recovery Banner
        if (playerState.errorMessage != null) {
            Surface(
                color = Color(0xFF1F122B).copy(alpha = 0.94f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.5.dp, TvCoralAlert),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = playerState.errorMessage,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                exoPlayer.prepare()
                                exoPlayer.play()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TvElectricCyan,
                                contentColor = TvOnPrimaryDark
                            )
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stream neu verbinden")
                        }
                        if (streamServerCount > 1) {
                            OutlinedButton(
                                onClick = onSwitchStreamServer,
                                border = BorderStroke(1.dp, TvGoldenFocus)
                            ) {
                                Text("Ersatz-Stream wählen", color = TvGoldenFocus)
                            }
                        }
                    }
                }
            }
        }

        // Custom High-Contrast Dark-Blue ExoPlayer OSD Overlay (Auto-Hides for Clean Fullscreen)
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF040D1A).copy(alpha = 0.85f),
                                Color.Transparent,
                                Color.Transparent,
                                Color(0xFF040D1A).copy(alpha = 0.92f)
                            )
                        )
                    )
            ) {
                // TOP BAR: Original Station Logo + Channel Title + Live Badge + Active MP4 Recording Status
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        ChannelLogoBadge(
                            logoUrl = channel.logoUrl,
                            channelName = channel.name,
                            accentColor = Color(channel.accentHex),
                            size = 50.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = if (activeRecording != null) TvGoldenFocus else TvEmeraldLive,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (activeRecording != null) "▶ AUFNAHME MP4" else "● LIVE",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                        color = TvOnPrimaryDark,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                                if (playerState.isRecordingMp4) {
                                    Surface(
                                        color = TvCoralAlert,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "● REC MP4 $recTimeFormatted ($recMbFormatted)",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = if (activeRecording != null) {
                                        activeRecording.channelTitle
                                    } else {
                                        "${channel.number}. ${channel.name}"
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = when {
                                    activeRecording != null ->
                                        "${activeRecording.fileName} • ${activeRecording.formattedDate} (${activeRecording.formattedSize})"
                                    playerState.isRecordingMp4 ->
                                        "Speichert in: ${playerState.recordingTargetPath}"
                                    else ->
                                        "${channel.currentShow} • ${playerState.videoResolutionLabel}"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (playerState.isRecordingMp4 || activeRecording != null) TvGoldenFocus else TvBrightSky,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (activeRecording != null) {
                            Surface(
                                onClick = onReturnToLiveTv,
                                shape = RoundedCornerShape(10.dp),
                                color = TvEmeraldLive,
                                border = BorderStroke(1.5.dp, Color.White),
                                modifier = Modifier
                                    .height(42.dp)
                                    .testTag("exo_return_to_live_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LiveTv,
                                        contentDescription = "Zurück zu Live-TV",
                                        tint = TvOnPrimaryDark,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "LIVE-TV",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                                        color = TvOnPrimaryDark
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("exo_favorite_button")
                        ) {
                            Icon(
                                imageVector = if (channel.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Sender als Favorit merken",
                                tint = if (channel.isFavorite) TvGoldenFocus else Color.White
                            )
                        }

                        IconButton(
                            onClick = onToggleZapperOverlay,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("exo_zapper_list_button"),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color(0xFF132D54),
                                contentColor = TvElectricCyan
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = "Senderliste einblenden"
                            )
                        }

                        Surface(
                            onClick = onExitApp,
                            shape = RoundedCornerShape(10.dp),
                            color = TvCoralAlert,
                            border = BorderStroke(1.5.dp, Color.White),
                            modifier = Modifier
                                .height(42.dp)
                                .testTag("exo_exit_app_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PowerSettingsNew,
                                    contentDescription = "Programm beenden",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "EXIT",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // CENTER TRANSPORT CONTROLS: Prev Channel, -10s, Play/Pause, +10s, Next Channel
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExoCircleIconButton(
                        icon = Icons.Default.SkipPrevious,
                        contentDescription = "Vorheriger Sender (CH-)",
                        tag = "exo_prev_channel_button",
                        onClick = onPreviousChannel
                    )

                    ExoCircleIconButton(
                        icon = Icons.Default.Replay10,
                        contentDescription = "10 Sekunden zurückspulen",
                        tag = "exo_rewind_10_button",
                        onClick = {
                            exoPlayer.seekTo((exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L))
                        }
                    )

                    Surface(
                        onClick = onTogglePlayPause,
                        shape = CircleShape,
                        color = TvElectricCyan,
                        border = BorderStroke(3.dp, TvGoldenFocus),
                        modifier = Modifier
                            .size(66.dp)
                            .testTag("exo_play_pause_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playerState.isPlaying) "Pause" else "Abspielen",
                                tint = TvOnPrimaryDark,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }

                    ExoCircleIconButton(
                        icon = Icons.Default.Forward10,
                        contentDescription = "10 Sekunden vorspulen",
                        tag = "exo_forward_10_button",
                        onClick = {
                            exoPlayer.seekTo(exoPlayer.currentPosition + 10_000L)
                        }
                    )

                    ExoCircleIconButton(
                        icon = Icons.Default.SkipNext,
                        contentDescription = "Nächster Sender (CH+)",
                        tag = "exo_next_channel_button",
                        onClick = onNextChannel
                    )
                }

                // BOTTOM FEATURE BAR: #1 VOLLBILD FIRST, #2 REC MP4 (Download/MaDIPTV), #3 HINTERGRUND-TON (PREMIUM), Ton, Format, Qualität, PiP
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. FIRST POSITION: VOLLBILD BUTTON (Prominent Gold/Cyan Highlight)
                        item {
                            ExoFeatureChip(
                                icon = if (playerState.isFullscreenPlayer) {
                                    Icons.Default.FullscreenExit
                                } else {
                                    Icons.Default.Fullscreen
                                },
                                label = if (playerState.isFullscreenPlayer) {
                                    "Vollbild beenden"
                                } else {
                                    "Vollbild"
                                },
                                active = true,
                                highlightGold = true,
                                tag = "exo_fullscreen_button",
                                onClick = onToggleFullscreen
                            )
                        }

                        // 2. MP4 RECORDING BUTTON (Saves to Download/MaDIPTV)
                        item {
                            ExoFeatureChip(
                                icon = Icons.Default.LiveTv,
                                label = if (playerState.isRecordingMp4) {
                                    "■ STOP REC ($recTimeFormatted • MP4)"
                                } else {
                                    "● Aufnahme MP4"
                                },
                                active = playerState.isRecordingMp4,
                                highlightRed = playerState.isRecordingMp4,
                                tag = "exo_record_mp4_button",
                                onClick = onToggleRecording
                            )
                        }

                        // 2b. AUFNAHMEN DIALOG BUTTON (Show & Play / Delete Recordings)
                        item {
                            ExoFeatureChip(
                                icon = Icons.Default.VideoLibrary,
                                label = "Aufnahmen",
                                active = playerState.showRecordingsDialog || activeRecording != null,
                                tag = "exo_open_recordings_button",
                                onClick = onOpenRecordingsDialog
                            )
                        }

                        // 2c. FULL-FEATURED EPG BUTTON
                        item {
                            ExoFeatureChip(
                                icon = Icons.Default.Schedule,
                                label = "EPG Programm",
                                active = true,
                                tag = "exo_open_epg_button",
                                onClick = onOpenEpg
                            )
                        }

                        if (activeRecording != null) {
                            item {
                                ExoFeatureChip(
                                    icon = Icons.Default.LiveTv,
                                    label = "Zurück zu Live-TV",
                                    active = true,
                                    highlightGold = true,
                                    tag = "exo_chip_return_to_live",
                                    onClick = onReturnToLiveTv
                                )
                            }
                        }

                        // 3. PREMIUM HINTERGRUND-WIEDERGABE
                        item {
                            ExoFeatureChip(
                                icon = Icons.Default.Headphones,
                                label = if (playerState.isBackgroundPlaybackEnabled) {
                                    "Hintergrund-Play: AN ★"
                                } else {
                                    "Hintergrund-Play: AUS"
                                },
                                active = playerState.isBackgroundPlaybackEnabled,
                                tag = "exo_background_play_button",
                                onClick = onToggleBackgroundPlayback
                            )
                        }

                        // 3. Mute / Volume Toggle
                        item {
                            ExoFeatureChip(
                                icon = if (playerState.isMuted) {
                                    Icons.AutoMirrored.Filled.VolumeOff
                                } else {
                                    Icons.AutoMirrored.Filled.VolumeUp
                                },
                                label = if (playerState.isMuted) "Stumm" else "Ton ${(playerState.volume * 100).toInt()}%",
                                active = !playerState.isMuted,
                                tag = "exo_mute_button",
                                onClick = onToggleMute
                            )
                        }

                        // 4. Aspect Ratio / Resize Mode
                        item {
                            ExoFeatureChip(
                                icon = Icons.Default.AspectRatio,
                                label = playerState.resizeMode.label,
                                active = false,
                                tag = "exo_resize_mode_button",
                                onClick = onCycleResizeMode
                            )
                        }

                        // 5. Video / Audio Track & Quality Selector
                        item {
                            ExoFeatureChip(
                                icon = Icons.Default.Tune,
                                label = "Qualität & Ton",
                                active = playerState.showTrackDialog,
                                tag = "exo_tracks_button",
                                onClick = { onShowTrackSelector(true) }
                            )
                        }

                        // 6. Jump to Live Edge Button
                        item {
                            ExoFeatureChip(
                                icon = Icons.Default.LiveTv,
                                label = "LIVE-PUNKT",
                                active = false,
                                tag = "exo_live_edge_button",
                                onClick = {
                                    if (exoPlayer.isCurrentMediaItemLive) {
                                        exoPlayer.seekToDefaultPosition()
                                    } else {
                                        exoPlayer.prepare()
                                    }
                                    exoPlayer.play()
                                }
                            )
                        }

                        // 7. Picture-in-Picture (PiP)
                        item {
                            ExoFeatureChip(
                                icon = Icons.Default.PictureInPictureAlt,
                                label = "PiP",
                                active = false,
                                tag = "exo_pip_button",
                                onClick = {
                                    val activity = context as? Activity
                                    if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        runCatching {
                                            val params = PictureInPictureParams.Builder()
                                                .setAspectRatio(Rational(16, 9))
                                                .build()
                                            activity.enterPictureInPictureMode(params)
                                        }
                                    }
                                }
                            )
                        }

                        // 8. Subtitles (CC)
                        item {
                            ExoFeatureChip(
                                icon = Icons.Default.ClosedCaption,
                                label = if (playerState.isSubtitlesEnabled) "UT: AN" else "UT: AUS",
                                active = playerState.isSubtitlesEnabled,
                                tag = "exo_subtitles_button",
                                onClick = onToggleSubtitles
                            )
                        }

                        // 9. Playback Speed
                        item {
                            ExoFeatureChip(
                                icon = Icons.Default.Speed,
                                label = "${playerState.playbackSpeed}x",
                                active = playerState.playbackSpeed != 1.0f,
                                tag = "exo_speed_button",
                                onClick = onCyclePlaybackSpeed
                            )
                        }

                        // 10. Alternative Stream Server Switcher
                        if (streamServerCount > 1) {
                            item {
                                ExoFeatureChip(
                                    icon = Icons.Default.Dns,
                                    label = "Stream ${playerState.streamSourceIndex + 1}/$streamServerCount",
                                    active = false,
                                    tag = "exo_switch_server_button",
                                    onClick = onSwitchStreamServer
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quick Channel Zapper Sidebar Overlay inside Player
        AnimatedVisibility(
            visible = playerState.showChannelZapperOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(280.dp)
        ) {
            Surface(
                color = Color(0xFF061326).copy(alpha = 0.96f),
                border = BorderStroke(1.5.dp, TvElectricCyan),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Senderwechsel (${allChannelsInRubrik.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = TvGoldenFocus
                        )
                        Text(
                            text = "Schließen",
                            style = MaterialTheme.typography.labelMedium,
                            color = TvBrightSky,
                            modifier = Modifier
                                .clickable { onToggleZapperOverlay() }
                                .padding(6.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(allChannelsInRubrik, key = { it.id }) { item ->
                            val isCurrent = item.id == channel.id
                            Surface(
                                onClick = { onSelectChannel(item) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isCurrent) Color(0xFF173768) else Color(0xFF0D2142),
                                border = BorderStroke(
                                    width = if (isCurrent) 2.dp else 1.dp,
                                    color = if (isCurrent) TvGoldenFocus else TvBrightSky.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ChannelLogoBadge(
                                        logoUrl = item.logoUrl,
                                        channelName = item.name,
                                        accentColor = Color(item.accentHex),
                                        size = 40.dp,
                                        cornerRadius = 8.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${item.number}. ${item.name}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = item.rubrik.flagBadge,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = TvBrightSky
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

    // Track & Quality Selector Dialog
    if (playerState.showTrackDialog) {
        ExoTrackSelectionDialog(
            playerState = playerState,
            onDismiss = { onShowTrackSelector(false) },
            onSelectAutoQuality = {
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                    .build()
                onShowTrackSelector(false)
            },
            onSelectTrack = { trackInfo, trackType ->
                val groups = exoPlayer.currentTracks.groups
                val group = groups.getOrNull(trackInfo.groupIndex)
                if (group != null) {
                    val override = TrackSelectionOverride(group.mediaTrackGroup, listOf(trackInfo.trackIndex))
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .clearOverridesOfType(trackType)
                        .addOverride(override)
                        .build()
                }
                onShowTrackSelector(false)
            },
            onVolumeChange = onVolumeChange
        )
    }
}

@Composable
private fun ExoCircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color(0xFF0D2242).copy(alpha = 0.9f),
        border = BorderStroke(1.5.dp, TvBrightSky),
        modifier = Modifier
            .size(50.dp)
            .testTag(tag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun ExoFeatureChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    tag: String,
    highlightGold: Boolean = false,
    highlightRed: Boolean = false,
    onClick: () -> Unit
) {
    val bgColor = when {
        highlightRed -> TvCoralAlert
        highlightGold -> TvGoldenFocus
        active -> TvElectricCyan
        else -> Color(0xFF0E2344).copy(alpha = 0.92f)
    }
    val fgColor = when {
        highlightRed -> Color.White
        highlightGold || active -> TvOnPrimaryDark
        else -> Color.White
    }
    val iconColor = when {
        highlightRed -> Color.White
        highlightGold || active -> TvOnPrimaryDark
        else -> TvElectricCyan
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(
            width = 1.5.dp,
            color = if (highlightRed || highlightGold) Color.White else if (active) TvGoldenFocus else TvBrightSky.copy(alpha = 0.6f)
        ),
        modifier = Modifier
            .height(44.dp)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = fgColor
            )
        }
    }
}

@Composable
private fun ExoTrackSelectionDialog(
    playerState: ExoPlayerFeatureState,
    onDismiss: () -> Unit,
    onSelectAutoQuality: () -> Unit,
    onSelectTrack: (ExoTrackInfo, Int) -> Unit,
    onVolumeChange: (Float) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF0A192F),
            border = BorderStroke(2.dp, TvElectricCyan),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Hd, contentDescription = null, tint = TvGoldenFocus)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ExoPlayer Qualität, Ton & Untertitel",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                    }
                }

                Text(
                    text = "Aktuelle Auflösung: ${playerState.videoResolutionLabel} (${playerState.videoBitrateLabel})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TvBrightSky
                )

                Column {
                    Text(
                        text = "Player-Lautstärke: ${(playerState.volume * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                    Slider(
                        value = playerState.volume,
                        onValueChange = onVolumeChange,
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = TvGoldenFocus,
                            activeTrackColor = TvElectricCyan
                        )
                    )
                }

                Text(
                    text = "Video-Qualitätsstufen (HLS Adaptive):",
                    style = MaterialTheme.typography.labelLarge,
                    color = TvGoldenFocus
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = false,
                            onClick = onSelectAutoQuality,
                            label = { Text("Automatisch (Optimal)") },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color(0xFF15315B),
                                labelColor = Color.White
                            )
                        )
                    }
                    items(playerState.availableVideoTracks, key = { it.id }) { track ->
                        FilterChip(
                            selected = track.isSelected,
                            onClick = { onSelectTrack(track, C.TRACK_TYPE_VIDEO) },
                            label = { Text(track.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TvElectricCyan,
                                selectedLabelColor = TvOnPrimaryDark
                            )
                        )
                    }
                }

                if (playerState.availableAudioTracks.isNotEmpty()) {
                    Text(
                        text = "Audiospuren:",
                        style = MaterialTheme.typography.labelLarge,
                        color = TvGoldenFocus
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(playerState.availableAudioTracks, key = { it.id }) { track ->
                            FilterChip(
                                selected = track.isSelected,
                                onClick = { onSelectTrack(track, C.TRACK_TYPE_AUDIO) },
                                label = { Text(track.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TvElectricCyan,
                                    selectedLabelColor = TvOnPrimaryDark
                                )
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TvElectricCyan,
                            contentColor = TvOnPrimaryDark
                        )
                    ) {
                        Text("Schließen", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
