package com.example.androidtvlight

import android.Manifest
import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.androidtvlight.data.recorder.TvStreamRecorder
import com.example.androidtvlight.data.repository.TvLightRepository
import com.example.androidtvlight.domain.model.TvRubrik
import com.example.androidtvlight.domain.model.TvSection
import com.example.androidtvlight.service.TvPlayerManager
import com.example.androidtvlight.ui.components.AmbientBiasLightCanvas
import com.example.androidtvlight.ui.components.TvExoPlayerView
import com.example.androidtvlight.ui.components.VirtualRemotePadDock
import com.example.androidtvlight.ui.screens.AmbientLightSectionContent
import com.example.androidtvlight.ui.screens.EpgFullFeaturedScreen
import com.example.androidtvlight.ui.screens.ExoPlayerLiveScreen
import com.example.androidtvlight.ui.screens.HomeDashboardContent
import com.example.androidtvlight.ui.screens.RecordingsDialog
import com.example.androidtvlight.ui.screens.RecordingsSectionContent
import com.example.androidtvlight.ui.screens.RemoteAndSettingsSectionContent
import com.example.androidtvlight.ui.screens.RubrikChannelsScreen
import com.example.androidtvlight.ui.screens.Tv2OhaSectionContent
import com.example.androidtvlight.ui.screens.Tv3LiveSectionContent
import com.example.androidtvlight.ui.theme.AndroidTvLightTheme
import com.example.androidtvlight.ui.theme.TvBrightSky
import com.example.androidtvlight.ui.theme.TvCoralAlert
import com.example.androidtvlight.ui.theme.TvElectricCyan
import com.example.androidtvlight.ui.theme.TvEmeraldLive
import com.example.androidtvlight.ui.theme.TvGoldenFocus
import com.example.androidtvlight.ui.theme.TvOnPrimaryDark
import com.example.androidtvlight.ui.viewmodel.RemoteCommand
import com.example.androidtvlight.ui.viewmodel.TvLightUiState
import com.example.androidtvlight.ui.viewmodel.TvLightViewModel
import kotlinx.coroutines.delay

@Composable
fun AndroidTvLightHost(
    onFullscreenChanged: (Boolean) -> Unit = {},
    onExitToMainApp: () -> Unit = {}
) {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val repository = remember(appContext) { TvLightRepository(appContext) }
    val streamRecorder = remember(appContext) { TvStreamRecorder(appContext) }
    val tvViewModel: TvLightViewModel = viewModel(
        key = "android_tv_light_embedded_vm",
        factory = TvLightViewModel.Factory(repository, streamRecorder)
    )
    val isTvDevice = remember(context) { detectTelevisionDevice(context) }

    LaunchedEffect(appContext) {
        TvPlayerManager.ensureForegroundServiceStarted(appContext)
    }

    // Vorschlag A: Sauberer Cleanup beim Verlassen der Rubrik Live TV Light (kein Doppelton)
    DisposableEffect(appContext) {
        onDispose {
            onFullscreenChanged(false)
            tvViewModel.stopForAppExit()
            TvPlayerManager.shutdownAndRelease(appContext)
            val activity = context as? Activity
            if (activity != null) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val uiState by tvViewModel.uiState.collectAsStateWithLifecycle()

    AndroidTvLightTheme(
        highContrast = uiState.settings.highContrastMode,
        largeText = uiState.settings.largeTextMode
    ) {
        AndroidTvLightApp(
            uiState = uiState,
            isTelevisionDevice = isTvDevice,
            viewModel = tvViewModel,
            onFullscreenChanged = onFullscreenChanged,
            onExitToMainApp = onExitToMainApp
        )
    }
}

private fun detectTelevisionDevice(context: Context): Boolean {
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    val isTvMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    val hasLeanback = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    return isTvMode || hasLeanback
}

@Composable
fun AndroidTvLightApp(
    uiState: TvLightUiState,
    isTelevisionDevice: Boolean,
    viewModel: TvLightViewModel,
    onFullscreenChanged: (Boolean) -> Unit = {},
    onExitToMainApp: () -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isPhoneLandscape = !isTelevisionDevice &&
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Im Player: Vollbild sauber beenden; außerhalb des Vollbilds zurück zur Hauptansicht von MaD IPTV Privat
    val onExitApp: () -> Unit = {
        if (uiState.playerState.isFullscreenPlayer) {
            viewModel.setFullscreenPlayer(false)
            val activity = context as? Activity
            if (!isTelevisionDevice) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        } else {
            viewModel.stopForAppExit()
            TvPlayerManager.shutdownAndRelease(context)
            onExitToMainApp()
        }
    }

    // Antwort 1: Installations-PIN (1971) wird innerhalb der Rubrik Live TV Light übersprungen

    // Storage permission check (Android 10+ uses MediaStore.Downloads directly without runtime permission;
    // Android 7-9 requires WRITE_EXTERNAL_STORAGE)
    var hasStoragePermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var startRecordingAfterPermission by remember { mutableStateOf(false) }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasStoragePermission = granted || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        if (hasStoragePermission && startRecordingAfterPermission) {
            startRecordingAfterPermission = false
            viewModel.toggleMp4Recording()
        }
    }

    val onTriggerMp4Recording: () -> Unit = {
        if (hasStoragePermission || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            viewModel.toggleMp4Recording()
        } else {
            startRecordingAfterPermission = true
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    val onRequestStoragePermissionExplicitly: () -> Unit = {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            hasStoragePermission = true
        }
    }

    // STANDBY PREVENTION: Keep Screen On + CPU WakeLock whenever preventStandbyEnabled or MP4 recording is active
    DisposableEffect(uiState.settings.preventStandbyEnabled, uiState.playerState.isRecordingMp4) {
        val activity = context as? Activity
        val keepAwake = uiState.settings.preventStandbyEnabled || uiState.playerState.isRecordingMp4
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val cpuWakeLock = if (keepAwake && pm != null) {
            pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AndroidTvLight::AppStandbyGuard"
            ).apply {
                setReferenceCounted(false)
                runCatching { acquire() }
            }
        } else {
            null
        }

        if (keepAwake) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        onDispose {
            runCatching {
                if (cpuWakeLock?.isHeld == true) {
                    cpuWakeLock.release()
                }
            }
        }
    }

    // 2. AUTOMATIC FULLSCREEN WHEN ROTATING PHONE TO LANDSCAPE WITHOUT STOPPING PLAYBACK
    // Ignore orientation changes triggered by the portrait lockscreen when the display is turned off!
    LaunchedEffect(isPhoneLandscape) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isDisplayOn = pm?.isInteractive != false
        if (!isTelevisionDevice && isDisplayOn) {
            viewModel.setFullscreenPlayer(isPhoneLandscape)
        }
    }

    val isTrueFullscreenMode = uiState.playerState.isFullscreenPlayer ||
        uiState.isFullscreenAmbient ||
        isPhoneLandscape

    // 4. TRUE IMMERSIVE FULLSCREEN: Hide WiFi icon, Clock, Battery, Status Bar & Navigation Bar + äußere App-Leisten (Vorschlag B)
    LaunchedEffect(isTrueFullscreenMode) {
        onFullscreenChanged(isTrueFullscreenMode)
        val activity = context as? Activity ?: return@LaunchedEffect
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (isTrueFullscreenMode) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val onToggleManualFullscreen: () -> Unit = {
        val activity = context as? Activity
        val nextFullscreen = !uiState.playerState.isFullscreenPlayer
        viewModel.setFullscreenPlayer(nextFullscreen)
        if (!isTelevisionDevice && activity != null) {
            if (nextFullscreen) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    BackHandler(
        enabled = uiState.isFullscreenAmbient ||
            uiState.playerState.isFullscreenPlayer ||
            uiState.currentSection != TvSection.HOME
    ) {
        when {
            uiState.isFullscreenAmbient -> viewModel.setFullscreenAmbient(false)
            uiState.playerState.isFullscreenPlayer -> {
                viewModel.setFullscreenPlayer(false)
                val activity = context as? Activity
                if (!isTelevisionDevice) {
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
            else -> viewModel.selectSection(TvSection.HOME, "Zurück-Taste")
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isTrueFullscreenMode) Color.Black else MaterialTheme.colorScheme.background)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    val mappedCommand = when (event.key) {
                        Key.DirectionUp -> RemoteCommand.UP
                        Key.DirectionDown -> RemoteCommand.DOWN
                        Key.DirectionLeft -> RemoteCommand.LEFT
                        Key.DirectionRight -> RemoteCommand.RIGHT
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> RemoteCommand.OK
                        Key.ChannelUp, Key.PageUp -> RemoteCommand.CHANNEL_UP
                        Key.ChannelDown, Key.PageDown -> RemoteCommand.CHANNEL_DOWN
                        Key.Escape -> RemoteCommand.BACK
                        else -> null
                    }
                    if (mappedCommand != null) {
                        viewModel.handleRemoteCommand(
                            command = mappedCommand,
                            sourceLabel = "TV-Fernbedienung (D-Pad)"
                        )
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
    ) {
        val isWideTvOrTablet = maxWidth >= 680.dp || isTelevisionDevice

        when {
            uiState.isFullscreenAmbient -> {
                FullscreenAmbientBiasScreen(
                    uiState = uiState,
                    onExitFullscreen = { viewModel.setFullscreenAmbient(false) }
                )
            }

            // Fullscreen TV Player (triggered via #1 Fullscreen button OR automatically when rotating phone!)
            uiState.playerState.isFullscreenPlayer || isPhoneLandscape -> {
                val activeChannel = uiState.activeChannel ?: uiState.channels.firstOrNull()
                if (activeChannel != null) {
                    TvExoPlayerView(
                        channel = activeChannel,
                        playerState = uiState.playerState.copy(isFullscreenPlayer = true),
                        allChannelsInRubrik = uiState.filteredChannels.ifEmpty { uiState.channels },
                        streamServerCount = uiState.activeStreamUrls.size,
                        onSelectChannel = { ch ->
                            viewModel.playChannelInExoPlayer(
                                channel = ch,
                                openPlayerSection = false,
                                enterFullscreen = true,
                                sourceLabel = "Vollbild-Zapping"
                            )
                        },
                        onPreviousChannel = { viewModel.stepChannel(-1, "Vollbild CH-") },
                        onNextChannel = { viewModel.stepChannel(1, "Vollbild CH+") },
                        onTogglePlayPause = viewModel::togglePlayPause,
                        onToggleMute = viewModel::toggleMute,
                        onVolumeChange = viewModel::setVolume,
                        onCycleResizeMode = viewModel::cycleResizeMode,
                        onCyclePlaybackSpeed = viewModel::cyclePlaybackSpeed,
                        onToggleSubtitles = viewModel::toggleSubtitles,
                        onToggleFullscreen = onToggleManualFullscreen,
                        onToggleRecording = onTriggerMp4Recording,
                        onDismissRecordingBanner = viewModel::dismissRecordingBanner,
                        onToggleBackgroundPlayback = viewModel::toggleBackgroundPlayback,
                        onSwitchStreamServer = viewModel::switchToFallbackStream,
                        onToggleFavorite = { viewModel.toggleFavoriteChannel(activeChannel.id) },
                        onShowTrackSelector = viewModel::setShowTrackDialog,
                        onToggleZapperOverlay = viewModel::toggleChannelZapperOverlay,
                        onPlayerStatusChanged = viewModel::updatePlayerStatus,
                        onStreamError = viewModel::onPlayerError,
                        onOpenRecordingsDialog = { viewModel.setShowRecordingsDialog(true) },
                        onOpenEpg = {
                            viewModel.setFullscreenPlayer(false)
                            viewModel.selectSection(TvSection.EPG, "ExoPlayer EPG")
                        },
                        onReturnToLiveTv = viewModel::stopRecordingPlaybackAndReturnToLive,
                        onExitApp = onExitApp,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            else -> {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = {
                        TvCompactTopBar(
                            uiState = uiState,
                            onToggleRecording = onTriggerMp4Recording,
                            onToggleBackgroundPlayback = viewModel::toggleBackgroundPlayback,
                            onToggleRemoteDock = viewModel::toggleVirtualRemoteOverlay,
                            onExitApp = onExitApp
                        )
                    },
                    bottomBar = {
                        if (!isWideTvOrTablet) {
                            TvTouchBottomNavigationBar(
                                currentSection = uiState.currentSection,
                                tv2ChannelCount = uiState.tv2Channels.size,
                                onSelectSection = { section ->
                                    viewModel.selectSection(section, "Touch-Navigation")
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (isWideTvOrTablet) {
                            TvSideNavigationRail(
                                currentSection = uiState.currentSection,
                                tv2ChannelCount = uiState.tv2Channels.size,
                                onSelectSection = { section ->
                                    viewModel.selectSection(section, "TV-Seitenleiste")
                                }
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(horizontal = 16.dp)
                        ) {
                            when (uiState.currentSection) {
                                TvSection.HOME -> {
                                    HomeDashboardContent(
                                        uiState = uiState,
                                        onSelectChannelForPlayer = { ch, fullscreen ->
                                            viewModel.playChannelInExoPlayer(
                                                channel = ch,
                                                openPlayerSection = fullscreen,
                                                enterFullscreen = fullscreen,
                                                sourceLabel = "Direktwahl"
                                            )
                                            if (fullscreen) {
                                                onToggleManualFullscreen()
                                            }
                                        },
                                        onSelectRubrikSection = { rubrik ->
                                            viewModel.selectRubrik(rubrik, openSection = false)
                                        },
                                        onToggleFavorite = viewModel::toggleFavoriteChannel,
                                        onOpenAmbientFullscreen = { viewModel.setFullscreenAmbient(true) },
                                        onNavigateSection = { sec -> viewModel.selectSection(sec) },
                                        onToggleHighContrast = viewModel::toggleHighContrastMode,
                                        onToggleLargeText = viewModel::toggleLargeTextMode,
                                        onToggleBackgroundPlayback = viewModel::toggleBackgroundPlayback,
                                        onStepChannel = { delta -> viewModel.stepChannel(delta, "Startseite Zapping") },
                                        onTogglePlayPause = viewModel::togglePlayPause,
                                        onToggleMute = viewModel::toggleMute,
                                        onVolumeChange = viewModel::setVolume,
                                        onCycleResizeMode = viewModel::cycleResizeMode,
                                        onCyclePlaybackSpeed = viewModel::cyclePlaybackSpeed,
                                        onToggleSubtitles = viewModel::toggleSubtitles,
                                        onToggleFullscreen = onToggleManualFullscreen,
                                        onToggleRecording = onTriggerMp4Recording,
                                        onDismissRecordingBanner = viewModel::dismissRecordingBanner,
                                        onSwitchStreamServer = viewModel::switchToFallbackStream,
                                        onShowTrackSelector = viewModel::setShowTrackDialog,
                                        onToggleZapperOverlay = viewModel::toggleChannelZapperOverlay,
                                        onPlayerStatusChanged = viewModel::updatePlayerStatus,
                                        onStreamError = viewModel::onPlayerError,
                                        onOpenRecordingsDialog = { viewModel.setShowRecordingsDialog(true) },
                                        onReturnToLiveTv = viewModel::stopRecordingPlaybackAndReturnToLive,
                                        onExitApp = onExitApp
                                    )
                                }
                                TvSection.DEUTSCHES_TV -> {
                                    RubrikChannelsScreen(
                                        rubrik = TvRubrik.DEUTSCHES_TV,
                                        uiState = uiState,
                                        onSearchQueryChange = viewModel::updateSearchQuery,
                                        onToggleFavoritesOnly = viewModel::toggleFavoritesFilter,
                                        onSwitchRubrik = { rubrik ->
                                            viewModel.selectRubrik(rubrik, openSection = true)
                                        },
                                        onPlayChannel = { ch, fullscreen ->
                                            viewModel.playChannelInExoPlayer(
                                                channel = ch,
                                                openPlayerSection = true,
                                                enterFullscreen = fullscreen,
                                                sourceLabel = "1: Deutsches TV"
                                            )
                                        },
                                        onToggleFavorite = viewModel::toggleFavoriteChannel
                                    )
                                }
                                TvSection.OESTERREICH_TV -> {
                                    RubrikChannelsScreen(
                                        rubrik = TvRubrik.OESTERREICH_TV,
                                        uiState = uiState,
                                        onSearchQueryChange = viewModel::updateSearchQuery,
                                        onToggleFavoritesOnly = viewModel::toggleFavoritesFilter,
                                        onSwitchRubrik = { rubrik ->
                                            viewModel.selectRubrik(rubrik, openSection = true)
                                        },
                                        onPlayChannel = { ch, fullscreen ->
                                            viewModel.playChannelInExoPlayer(
                                                channel = ch,
                                                openPlayerSection = true,
                                                enterFullscreen = fullscreen,
                                                sourceLabel = "2: Österreich TV"
                                            )
                                        },
                                        onToggleFavorite = viewModel::toggleFavoriteChannel
                                    )
                                }
                                TvSection.TV2 -> {
                                    Tv2OhaSectionContent(
                                        uiState = uiState,
                                        onSelectChannelForPlayer = { ch, fullscreen ->
                                            viewModel.playChannelInExoPlayer(
                                                channel = ch,
                                                openPlayerSection = false,
                                                enterFullscreen = fullscreen,
                                                sourceLabel = "DE TV2"
                                            )
                                            if (fullscreen) {
                                                onToggleManualFullscreen()
                                            }
                                        },
                                        onSearchQueryChange = viewModel::updateSearchQuery,
                                        onToggleFavoritesOnly = viewModel::toggleFavoritesFilter,
                                        onRefreshTv2Feed = viewModel::refreshTv2Channels,
                                        onToggleFavorite = viewModel::toggleFavoriteChannel,
                                        onStepChannel = { delta ->
                                            viewModel.stepChannel(delta, "DE TV2 Zapping")
                                        },
                                        onTogglePlayPause = viewModel::togglePlayPause,
                                        onToggleMute = viewModel::toggleMute,
                                        onVolumeChange = viewModel::setVolume,
                                        onCycleResizeMode = viewModel::cycleResizeMode,
                                        onCyclePlaybackSpeed = viewModel::cyclePlaybackSpeed,
                                        onToggleSubtitles = viewModel::toggleSubtitles,
                                        onToggleFullscreen = onToggleManualFullscreen,
                                        onToggleRecording = onTriggerMp4Recording,
                                        onDismissRecordingBanner = viewModel::dismissRecordingBanner,
                                        onToggleBackgroundPlayback = viewModel::toggleBackgroundPlayback,
                                        onSwitchStreamServer = viewModel::switchToFallbackStream,
                                        onShowTrackSelector = viewModel::setShowTrackDialog,
                                        onToggleZapperOverlay = viewModel::toggleChannelZapperOverlay,
                                        onPlayerStatusChanged = viewModel::updatePlayerStatus,
                                        onStreamError = viewModel::onPlayerError,
                                        onOpenRecordingsDialog = { viewModel.setShowRecordingsDialog(true) },
                                        onReturnToLiveTv = viewModel::stopRecordingPlaybackAndReturnToLive,
                                        onExitApp = onExitApp
                                    )
                                }
                                TvSection.TV3 -> {
                                    Tv3LiveSectionContent(
                                        uiState = uiState,
                                        onSelectChannelForPlayer = { ch, fullscreen ->
                                            viewModel.playChannelInExoPlayer(
                                                channel = ch,
                                                openPlayerSection = false,
                                                enterFullscreen = fullscreen,
                                                sourceLabel = "TV3"
                                            )
                                            if (fullscreen) {
                                                onToggleManualFullscreen()
                                            }
                                        },
                                        onSearchQueryChange = viewModel::updateSearchQuery,
                                        onToggleFavoritesOnly = viewModel::toggleFavoritesFilter,
                                        onRefreshTv3Feed = viewModel::refreshTv3Channels,
                                        onToggleFavorite = viewModel::toggleFavoriteChannel,
                                        onStepChannel = { delta ->
                                            viewModel.stepChannel(delta, "TV3 Zapping")
                                        },
                                        onTogglePlayPause = viewModel::togglePlayPause,
                                        onToggleMute = viewModel::toggleMute,
                                        onVolumeChange = viewModel::setVolume,
                                        onCycleResizeMode = viewModel::cycleResizeMode,
                                        onCyclePlaybackSpeed = viewModel::cyclePlaybackSpeed,
                                        onToggleSubtitles = viewModel::toggleSubtitles,
                                        onToggleFullscreen = onToggleManualFullscreen,
                                        onToggleRecording = onTriggerMp4Recording,
                                        onDismissRecordingBanner = viewModel::dismissRecordingBanner,
                                        onToggleBackgroundPlayback = viewModel::toggleBackgroundPlayback,
                                        onSwitchStreamServer = viewModel::switchToFallbackStream,
                                        onShowTrackSelector = viewModel::setShowTrackDialog,
                                        onToggleZapperOverlay = viewModel::toggleChannelZapperOverlay,
                                        onPlayerStatusChanged = viewModel::updatePlayerStatus,
                                        onStreamError = viewModel::onPlayerError,
                                        onOpenRecordingsDialog = { viewModel.setShowRecordingsDialog(true) },
                                        onReturnToLiveTv = viewModel::stopRecordingPlaybackAndReturnToLive,
                                        onExitApp = onExitApp
                                    )
                                }
                                TvSection.EPG -> {
                                    EpgFullFeaturedScreen(
                                        uiState = uiState,
                                        onClickProgramToPlayChannel = { program, fullscreen ->
                                            viewModel.playChannelFromEpgProgram(
                                                program = program,
                                                openPlayerSection = false,
                                                enterFullscreen = fullscreen
                                            )
                                            if (fullscreen) {
                                                onToggleManualFullscreen()
                                            }
                                        },
                                        onShowProgramDetails = viewModel::setSelectedEpgDetailProgram,
                                        onSelectTimeFilter = viewModel::selectEpgTimeFilter,
                                        onSelectCategoryFilter = viewModel::selectEpgCategoryFilter,
                                        onSelectRubrik = { rubrik ->
                                            viewModel.selectRubrik(rubrik, openSection = false)
                                        },
                                        onToggleFavoritesOnly = viewModel::toggleFavoritesFilter,
                                        onSearchQueryChange = viewModel::updateSearchQuery,
                                        onToggleEpgAutoUpdate = viewModel::toggleEpgAutoUpdate,
                                        onCycleEpgInterval = viewModel::cycleEpgAutoUpdateInterval,
                                        onManualRefreshEpg = { viewModel.refreshEpgNow(manualTrigger = true) },
                                        onToggleFavoriteChannel = viewModel::toggleFavoriteChannel,
                                        onSelectChannelInPlayer = { ch ->
                                            viewModel.playChannelInExoPlayer(
                                                channel = ch,
                                                openPlayerSection = false,
                                                sourceLabel = "EPG Zapping"
                                            )
                                        },
                                        onStepChannel = { delta ->
                                            viewModel.stepChannel(delta, "EPG Zapping")
                                        },
                                        onTogglePlayPause = viewModel::togglePlayPause,
                                        onToggleMute = viewModel::toggleMute,
                                        onVolumeChange = viewModel::setVolume,
                                        onCycleResizeMode = viewModel::cycleResizeMode,
                                        onCyclePlaybackSpeed = viewModel::cyclePlaybackSpeed,
                                        onToggleSubtitles = viewModel::toggleSubtitles,
                                        onToggleFullscreen = onToggleManualFullscreen,
                                        onToggleRecording = onTriggerMp4Recording,
                                        onDismissRecordingBanner = viewModel::dismissRecordingBanner,
                                        onToggleBackgroundPlayback = viewModel::toggleBackgroundPlayback,
                                        onSwitchStreamServer = viewModel::switchToFallbackStream,
                                        onShowTrackSelector = viewModel::setShowTrackDialog,
                                        onToggleZapperOverlay = viewModel::toggleChannelZapperOverlay,
                                        onPlayerStatusChanged = viewModel::updatePlayerStatus,
                                        onStreamError = viewModel::onPlayerError,
                                        onOpenRecordingsDialog = { viewModel.setShowRecordingsDialog(true) },
                                        onReturnToLiveTv = viewModel::stopRecordingPlaybackAndReturnToLive,
                                        onExitApp = onExitApp
                                    )
                                }
                                TvSection.PLAYER -> {
                                    ExoPlayerLiveScreen(
                                        uiState = uiState,
                                        onSelectChannel = { ch ->
                                            viewModel.playChannelInExoPlayer(
                                                channel = ch,
                                                openPlayerSection = true,
                                                sourceLabel = "ExoPlayer Zapping"
                                            )
                                        },
                                        onStepChannel = { delta ->
                                            viewModel.stepChannel(delta, "ExoPlayer Zapping")
                                        },
                                        onSelectRubrik = { rubrik ->
                                            viewModel.selectRubrik(rubrik, openSection = false)
                                        },
                                        onTogglePlayPause = viewModel::togglePlayPause,
                                        onToggleMute = viewModel::toggleMute,
                                        onVolumeChange = viewModel::setVolume,
                                        onCycleResizeMode = viewModel::cycleResizeMode,
                                        onCyclePlaybackSpeed = viewModel::cyclePlaybackSpeed,
                                        onToggleSubtitles = viewModel::toggleSubtitles,
                                        onToggleFullscreen = onToggleManualFullscreen,
                                        onToggleRecording = onTriggerMp4Recording,
                                        onDismissRecordingBanner = viewModel::dismissRecordingBanner,
                                        onToggleBackgroundPlayback = viewModel::toggleBackgroundPlayback,
                                        onSwitchStreamServer = viewModel::switchToFallbackStream,
                                        onToggleFavorite = viewModel::toggleFavoriteChannel,
                                        onShowTrackSelector = viewModel::setShowTrackDialog,
                                        onToggleZapperOverlay = viewModel::toggleChannelZapperOverlay,
                                        onPlayerStatusChanged = viewModel::updatePlayerStatus,
                                        onStreamError = viewModel::onPlayerError,
                                        onOpenRecordingsDialog = { viewModel.setShowRecordingsDialog(true) },
                                        onReturnToLiveTv = viewModel::stopRecordingPlaybackAndReturnToLive,
                                        onExitApp = onExitApp
                                    )
                                }
                                TvSection.RECORDINGS -> {
                                    RecordingsSectionContent(
                                        uiState = uiState,
                                        onPlayRecording = { rec, fullscreen ->
                                            viewModel.playRecordingInExoPlayer(rec, enterFullscreen = fullscreen)
                                            if (fullscreen) {
                                                onToggleManualFullscreen()
                                            }
                                        },
                                        onDeleteRecording = viewModel::deleteRecording,
                                        onOpenRecordingsDialog = { viewModel.setShowRecordingsDialog(true) },
                                        onReturnToLiveTv = viewModel::stopRecordingPlaybackAndReturnToLive,
                                        onRefreshRecordings = viewModel::refreshRecordings,
                                        onSelectChannel = { ch ->
                                            viewModel.playChannelInExoPlayer(
                                                channel = ch,
                                                openPlayerSection = false,
                                                sourceLabel = "Aufnahmen Zapping"
                                            )
                                        },
                                        onStepChannel = { delta ->
                                            viewModel.stepChannel(delta, "Aufnahmen Zapping")
                                        },
                                        onTogglePlayPause = viewModel::togglePlayPause,
                                        onToggleMute = viewModel::toggleMute,
                                        onVolumeChange = viewModel::setVolume,
                                        onCycleResizeMode = viewModel::cycleResizeMode,
                                        onCyclePlaybackSpeed = viewModel::cyclePlaybackSpeed,
                                        onToggleSubtitles = viewModel::toggleSubtitles,
                                        onToggleFullscreen = onToggleManualFullscreen,
                                        onToggleRecording = onTriggerMp4Recording,
                                        onDismissRecordingBanner = viewModel::dismissRecordingBanner,
                                        onToggleBackgroundPlayback = viewModel::toggleBackgroundPlayback,
                                        onSwitchStreamServer = viewModel::switchToFallbackStream,
                                        onToggleFavorite = viewModel::toggleFavoriteChannel,
                                        onShowTrackSelector = viewModel::setShowTrackDialog,
                                        onToggleZapperOverlay = viewModel::toggleChannelZapperOverlay,
                                        onPlayerStatusChanged = viewModel::updatePlayerStatus,
                                        onStreamError = viewModel::onPlayerError,
                                        onExitApp = onExitApp
                                    )
                                }
                                TvSection.AMBIENT -> {
                                    AmbientLightSectionContent(
                                        uiState = uiState,
                                        onSelectScene = { scene ->
                                            viewModel.selectAmbientScene(scene.id, "Ambient Auswahl")
                                        },
                                        onBrightnessChange = viewModel::setAmbientBrightness,
                                        onWarmFilterChange = viewModel::setWarmEyeCareFilter,
                                        onTogglePulse = viewModel::toggleAmbientPulse,
                                        onCycleSleepTimer = viewModel::cycleSleepTimer,
                                        onStartFullscreen = { viewModel.setFullscreenAmbient(true) }
                                    )
                                }
                                TvSection.REMOTE -> {
                                    RemoteAndSettingsSectionContent(
                                        uiState = uiState,
                                        isTelevisionDevice = isTelevisionDevice,
                                        hasStoragePermission = hasStoragePermission,
                                        onRequestStoragePermission = onRequestStoragePermissionExplicitly,
                                        onToggleRecording = onTriggerMp4Recording,
                                        onTogglePreventStandby = viewModel::togglePreventStandby,
                                        onToggleBackgroundPlayback = viewModel::toggleBackgroundPlayback,
                                        onToggleHighContrast = viewModel::toggleHighContrastMode,
                                        onToggleLargeText = viewModel::toggleLargeTextMode,
                                        onToggleVirtualRemote = viewModel::toggleVirtualRemoteOverlay,
                                        onCommand = { cmd ->
                                            viewModel.handleRemoteCommand(cmd, "TV-Teststeuerung")
                                        }
                                    )
                                }
                            }

                            androidx.compose.animation.AnimatedVisibility(
                                visible = uiState.settings.showVirtualRemoteOverlay,
                                enter = fadeIn(),
                                exit = fadeOut(),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(bottom = 14.dp, end = 4.dp)
                            ) {
                                VirtualRemotePadDock(
                                    onCommand = { cmd ->
                                        viewModel.handleRemoteCommand(cmd, "Virtuelles Steuerkreuz")
                                    },
                                    onCloseDock = viewModel::toggleVirtualRemoteOverlay
                                )
                            }
                        }
                    }
                }
            }
        }

        // AUFNAHMEN DIALOG (Opens when selecting "Aufnahmen" in the bottom menu or player bar)
        if (uiState.playerState.showRecordingsDialog) {
            RecordingsDialog(
                recordings = uiState.recordings,
                activeRecording = uiState.playerState.activeRecording,
                isRecordingActive = uiState.playerState.isRecordingMp4,
                recordingTargetPath = uiState.playerState.recordingTargetPath,
                recordingElapsedSeconds = uiState.playerState.recordingElapsedSeconds,
                onPlayRecording = { recording, fullscreen ->
                    viewModel.playRecordingInExoPlayer(recording, enterFullscreen = fullscreen)
                    if (fullscreen) {
                        onToggleManualFullscreen()
                    }
                },
                onDeleteRecording = viewModel::deleteRecording,
                onToggleRecording = onTriggerMp4Recording,
                onReturnToLiveTv = viewModel::stopRecordingPlaybackAndReturnToLive,
                onRefresh = viewModel::refreshRecordings,
                onDismiss = { viewModel.setShowRecordingsDialog(false) }
            )
        }
    }
}

/**
 * 12-Position Installation PIN Dialog:
 * - Visually displays 12 PIN digit slots (up to 12 digits can be entered).
 * - Actual unlock PIN is 1971.
 * - Contains ZERO hints about the PIN or the required PIN length.
 */
@Composable
private fun InstallationPinDialogScreen(
    onVerifyPin: (String) -> Boolean,
    onExitApp: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    // Auto-unlock shortly after "1971" is entered (also unlocks immediately on pressing OK)
    LaunchedEffect(enteredPin) {
        if (enteredPin == "1971") {
            delay(250L)
            onVerifyPin(enteredPin)
        }
    }

    val appendDigit: (Char) -> Unit = { digit ->
        if (enteredPin.length < 12) {
            enteredPin += digit
            showError = false
        }
    }

    val deleteLast: () -> Unit = {
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
            showError = false
        }
    }

    val submitPin: () -> Unit = {
        val ok = onVerifyPin(enteredPin)
        if (!ok) {
            showError = true
            enteredPin = ""
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF040D1A))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Zero, Key.NumPad0 -> { appendDigit('0'); true }
                        Key.One, Key.NumPad1 -> { appendDigit('1'); true }
                        Key.Two, Key.NumPad2 -> { appendDigit('2'); true }
                        Key.Three, Key.NumPad3 -> { appendDigit('3'); true }
                        Key.Four, Key.NumPad4 -> { appendDigit('4'); true }
                        Key.Five, Key.NumPad5 -> { appendDigit('5'); true }
                        Key.Six, Key.NumPad6 -> { appendDigit('6'); true }
                        Key.Seven, Key.NumPad7 -> { appendDigit('7'); true }
                        Key.Eight, Key.NumPad8 -> { appendDigit('8'); true }
                        Key.Nine, Key.NumPad9 -> { appendDigit('9'); true }
                        Key.Backspace, Key.Delete -> { deleteLast(); true }
                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { submitPin(); true }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .testTag("installation_pin_dialog"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF0A192F),
            border = BorderStroke(2.dp, TvElectricCyan),
            shadowElevation = 16.dp,
            modifier = Modifier
                .padding(16.dp)
                .width(520.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF132D54),
                            border = BorderStroke(1.5.dp, TvGoldenFocus),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = TvGoldenFocus,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Installations-PIN",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                color = Color.White
                            )
                            Text(
                                text = "Bitte Autorisierungs-PIN eingeben",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TvBrightSky
                            )
                        }
                    }

                    // EXIT Button inside PIN Dialog
                    Surface(
                        onClick = onExitApp,
                        shape = RoundedCornerShape(10.dp),
                        color = TvCoralAlert,
                        border = BorderStroke(1.5.dp, Color.White),
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("pin_dialog_exit_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = "Programm beenden",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "EXIT",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 12-Slot Visual PIN Display (12 boxes, no hints on actual PIN or length)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pin_12_slots_row"),
                    horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(12) { index ->
                        val isFilled = index < enteredPin.length
                        val isCurrent = index == enteredPin.length
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isFilled) Color(0xFF173768) else Color(0xFF061020),
                            border = BorderStroke(
                                width = if (isCurrent) 2.dp else 1.dp,
                                color = when {
                                    showError -> TvCoralAlert
                                    isCurrent -> TvGoldenFocus
                                    isFilled -> TvElectricCyan
                                    else -> TvBrightSky.copy(alpha = 0.35f)
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (isFilled) "●" else "",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = TvGoldenFocus,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                if (showError) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Ungültige PIN",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = TvCoralAlert,
                        modifier = Modifier.testTag("pin_error_text")
                    )
                } else {
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // On-Screen Keypad (Touch & TV D-Pad Remote compatible)
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9")
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rows.forEach { rowDigits ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowDigits.forEach { digitStr ->
                                OutlinedButton(
                                    onClick = { appendDigit(digitStr[0]) },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, TvBrightSky.copy(alpha = 0.5f)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = Color(0xFF112647),
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("pin_key_$digitStr")
                                ) {
                                    Text(
                                        text = digitStr,
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = deleteLast,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, TvCoralAlert.copy(alpha = 0.7f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(0xFF1F1633),
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("pin_key_backspace")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Backspace,
                                contentDescription = "Löschen",
                                tint = TvCoralAlert,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        OutlinedButton(
                            onClick = { appendDigit('0') },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, TvBrightSky.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(0xFF112647),
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("pin_key_0")
                        ) {
                            Text(
                                text = "0",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }

                        Button(
                            onClick = submitPin,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TvElectricCyan,
                                contentColor = TvOnPrimaryDark
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("pin_key_submit")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Bestätigen",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "OK",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Clean, uncluttered Top Bar (Easy Handling) with 1-Click MP4 Recording (Download/MaDIPTV),
 * 1-Click Premium Background Playback status, Remote Dock button, and prominent EXIT Button.
 */
@Composable
private fun TvCompactTopBar(
    uiState: TvLightUiState,
    onToggleRecording: () -> Unit,
    onToggleBackgroundPlayback: () -> Unit,
    onToggleRemoteDock: () -> Unit,
    onExitApp: () -> Unit
) {
    val isRec = uiState.playerState.isRecordingMp4
    val recMins = uiState.playerState.recordingElapsedSeconds / 60
    val recSecs = uiState.playerState.recordingElapsedSeconds % 60
    val recTimer = "%02d:%02d".format(recMins, recSecs)

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF132D54),
                    border = BorderStroke(1.5.dp, TvElectricCyan),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = TvElectricCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = "Android TV Light",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.White,
                    modifier = Modifier.testTag("app_header_title")
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1-Click MP4 Recording Pill (Saves into Download/MaDIPTV)
                Surface(
                    onClick = onToggleRecording,
                    shape = RoundedCornerShape(12.dp),
                    color = if (isRec) TvCoralAlert else Color(0xFF1E1936),
                    border = BorderStroke(
                        1.5.dp,
                        if (isRec) Color.White else TvCoralAlert
                    ),
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("header_record_mp4_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isRec) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                            contentDescription = "MP4 Aufnahme starten oder stoppen",
                            tint = if (isRec) Color.White else TvCoralAlert,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isRec) "REC $recTimer" else "REC MP4",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color.White
                        )
                    }
                }

                // Premium Feature 1: Background Playback Pill
                Surface(
                    onClick = onToggleBackgroundPlayback,
                    shape = RoundedCornerShape(12.dp),
                    color = if (uiState.settings.backgroundPlaybackEnabled) Color(0xFF13382E) else Color(0xFF132D54),
                    border = BorderStroke(
                        1.5.dp,
                        if (uiState.settings.backgroundPlaybackEnabled) TvEmeraldLive else TvBrightSky.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("header_bg_play_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = "Hintergrundwiedergabe umschalten",
                            tint = if (uiState.settings.backgroundPlaybackEnabled) TvEmeraldLive else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (uiState.settings.backgroundPlaybackEnabled) "Hintergrund AN" else "Hintergrund AUS",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }

                IconButton(
                    onClick = onToggleRemoteDock,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("header_remote_dock_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SettingsRemote,
                        contentDescription = "Virtuelle Fernbedienung ein-/ausblenden",
                        tint = if (uiState.settings.showVirtualRemoteOverlay) TvGoldenFocus else TvBrightSky
                    )
                }
            }
        }
    }
}

@Composable
private fun TvSideNavigationRail(
    currentSection: TvSection,
    tv2ChannelCount: Int,
    onSelectSection: (TvSection) -> Unit
) {
    NavigationRail(
        containerColor = Color(0xFF071326),
        contentColor = Color.White,
        modifier = Modifier
            .fillMaxHeight()
            .padding(end = 4.dp)
            .testTag("tv_side_navigation_rail")
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        TvSection.entries.forEach { section ->
            val selected = currentSection == section
            val labelText = if (section == TvSection.TV2) {
                "DE TV2 ($tv2ChannelCount)"
            } else {
                section.title
            }
            NavigationRailItem(
                selected = selected,
                onClick = { onSelectSection(section) },
                icon = {
                    Icon(
                        imageVector = iconForSection(section),
                        contentDescription = labelText
                    )
                },
                label = {
                    Text(
                        text = labelText,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = TvOnPrimaryDark,
                    selectedTextColor = TvGoldenFocus,
                    indicatorColor = TvElectricCyan,
                    unselectedIconColor = TvBrightSky,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("nav_rail_${section.id}")
            )
        }
    }
}

@Composable
private fun TvTouchBottomNavigationBar(
    currentSection: TvSection,
    tv2ChannelCount: Int,
    onSelectSection: (TvSection) -> Unit
) {
    Surface(
        color = Color(0xFF071326),
        contentColor = Color.White,
        border = BorderStroke(1.dp, TvBrightSky.copy(alpha = 0.25f)),
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .testTag("tv_bottom_navigation_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TvSection.entries.forEach { section ->
                val selected = currentSection == section
                val shortTitle = when (section) {
                    TvSection.HOME -> "Start"
                    TvSection.DEUTSCHES_TV -> "DE TV"
                    TvSection.OESTERREICH_TV -> "AT TV"
                    TvSection.TV2 -> "DE TV2"
                    TvSection.TV3 -> "TV3"
                    TvSection.EPG -> "EPG"
                    TvSection.PLAYER -> "Player"
                    TvSection.RECORDINGS -> "Aufn."
                    TvSection.AMBIENT -> "Ambient"
                    TvSection.REMOTE -> "Steuer."
                }
                Surface(
                    onClick = { onSelectSection(section) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) TvElectricCyan else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("nav_bottom_${section.id}")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = iconForSection(section),
                            contentDescription = if (section == TvSection.TV2) {
                                "DE TV2 ($tv2ChannelCount)"
                            } else {
                                section.title
                            },
                            tint = if (selected) TvOnPrimaryDark else TvBrightSky,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = shortTitle,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                lineHeight = 11.sp,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold
                            ),
                            color = if (selected) TvOnPrimaryDark else Color.White,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FullscreenAmbientBiasScreen(
    uiState: TvLightUiState,
    onExitFullscreen: () -> Unit
) {
    val activeScene = uiState.activeAmbientScene ?: uiState.ambientScenes.firstOrNull()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onExitFullscreen() }
            .testTag("fullscreen_ambient_overlay")
    ) {
        if (activeScene != null) {
            AmbientBiasLightCanvas(
                scene = activeScene,
                brightness = uiState.settings.ambientBrightness,
                dynamicPulse = uiState.settings.ambientDynamicPulse,
                warmFilter = uiState.settings.warmEyeCareFilter
            )
        }

        Surface(
            color = Color(0xFF051124).copy(alpha = 0.85f),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.5.dp, TvElectricCyan),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column {
                    Text(
                        text = activeScene?.name ?: "Ambient Bias-Light",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Text(
                        text = "Tippen oder OK / Zurück auf der Fernbedienung drücken zum Beenden",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TvBrightSky
                    )
                }

                Button(
                    onClick = onExitFullscreen,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TvGoldenFocus,
                        contentColor = TvOnPrimaryDark
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("exit_fullscreen_ambient_button")
                ) {
                    Icon(Icons.Default.FullscreenExit, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Beenden", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

private fun iconForSection(section: TvSection): ImageVector = when (section) {
    TvSection.HOME -> Icons.Default.Home
    TvSection.DEUTSCHES_TV -> Icons.Default.LiveTv
    TvSection.OESTERREICH_TV -> Icons.Default.Public
    TvSection.TV2 -> Icons.Default.Tv
    TvSection.TV3 -> Icons.Default.LiveTv
    TvSection.EPG -> Icons.Default.Schedule
    TvSection.PLAYER -> Icons.Default.PlayCircleFilled
    TvSection.RECORDINGS -> Icons.Default.VideoLibrary
    TvSection.AMBIENT -> Icons.Default.LightMode
    TvSection.REMOTE -> Icons.Default.SettingsRemote
}
