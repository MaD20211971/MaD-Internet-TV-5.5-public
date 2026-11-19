package com.example.ui.navigation

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DensitySmall
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.ContentType
import com.example.data.model.PlaylistEntity
import com.example.ui.components.AddPlaylistDialog
import com.example.ui.components.BackupExportDialog
import com.example.ui.components.BackupRestoreDialog
import com.example.ui.components.BottomNavigationSection
import com.example.ui.components.ChannelCard
import com.example.ui.components.CountrySectionHeader
import com.example.ui.components.EditPlaylistDialog
import com.example.ui.components.EpgGuideView
import com.example.ui.components.InstallationPasswordDialog
import com.example.data.livetvlight.LIVE_TV_LIGHT_PLAYLIST_ID
import com.example.data.livetvlight.LiveTvLightCatalog
import com.example.ui.components.LiveTvLightView
import com.example.ui.components.LocalM3uManagerView
import com.example.ui.components.MacPortalConnectionDialog
import com.example.ui.components.MovieExplorer
import com.example.ui.components.NexusPlayerView
import com.example.ui.components.PinPromptDialog
import com.example.ui.components.PlaylistImportAssistantDialog
import com.example.ui.components.PlaylistUpdateBanner
import com.example.ui.components.RadioExplorer
import com.example.ui.components.SeriesExplorer
import com.example.ui.components.SettingsView
import com.example.ui.components.TopBarSection
import com.example.ui.components.TvNavigationRail
import com.example.ui.components.VodContinueWatchingScreen
import com.example.ui.components.VodDownloadManagerDialog
import com.example.ui.components.WebStreamBrowserView
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentRose
import com.example.ui.theme.BorderGlow
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.DarkNavyBackground
import com.example.ui.theme.DarkNavyCard
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.GoldLight
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.MainTab
import com.example.ui.viewmodel.MainViewModel

private enum class LiveChannelLayoutMode {
    GRID,
    COMFORT_LIST,
    COMPACT_LIST
}

@Composable
fun AppNavigation(
    viewModel: MainViewModel = viewModel()
) {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val isTvOrWide = configuration.smallestScreenWidthDp >= 600

    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val allChannels by viewModel.allChannels.collectAsStateWithLifecycle()
    val currentlyPlaying by viewModel.currentlyPlaying.collectAsStateWithLifecycle()
    val isPlayerFullscreen by viewModel.isPlayerFullscreen.collectAsStateWithLifecycle()
    val badgeCounts by viewModel.badgeCounts.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val activePlaylistId by viewModel.activePlaylistId.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val countryFolders by viewModel.countryFolders.collectAsStateWithLifecycle()
    val filteredLiveChannels by viewModel.filteredLiveChannels.collectAsStateWithLifecycle()
    val countrySummary by viewModel.countrySummary.collectAsStateWithLifecycle()
    val disabledCountries by viewModel.disabledCountries.collectAsStateWithLifecycle()
    val hiddenChannelIds by viewModel.hiddenChannelIds.collectAsStateWithLifecycle()
    val channelSortMode by viewModel.channelSortMode.collectAsStateWithLifecycle()
    val isRecordingNow by viewModel.isRecordingNow.collectAsStateWithLifecycle()
    val isAppUnlocked by viewModel.isAppUnlocked.collectAsStateWithLifecycle()
    val playlistUpdateNotice by viewModel.playlistUpdateNotice.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val macPortalDialogState by viewModel.macPortalDialogState.collectAsStateWithLifecycle()

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.clearStatusMessage()
        }
    }

    val isEnglish = settings.language.equals("en", ignoreCase = true)
    val activePlaylist = playlists.firstOrNull { it.id == activePlaylistId } ?: playlists.firstOrNull()

    // Dialog states
    var showAddPlaylistDialog by remember { mutableStateOf(false) }
    var addPlaylistInitialTab by remember { mutableIntStateOf(0) }
    var playlistToUnlock by remember { mutableStateOf<PlaylistEntity?>(null) }
    var playlistToEdit by remember { mutableStateOf<PlaylistEntity?>(null) }
    var showPinPrompt by remember { mutableStateOf(false) }
    var showImportAssistantDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showVodDownloadManager by remember { mutableStateOf(false) }
    var isWebViewFullscreen by remember { mutableStateOf(false) }
    var isLiveTvLightFullscreen by remember { mutableStateOf(false) }
    val recordings by viewModel.recordings.collectAsStateWithLifecycle()
    val localRadioBrowserStations by viewModel.localRadioStations.collectAsStateWithLifecycle()
    val radioBrowserStationCount by viewModel.radioBrowserStationCount.collectAsStateWithLifecycle()
    val activeRadioRecordingInfo by viewModel.activeRadioRecordingInfo.collectAsStateWithLifecycle()
    val activeRadioSongTitle by viewModel.activeRadioSongTitle.collectAsStateWithLifecycle()
    val totalRadioCount = radioBrowserStationCount + badgeCounts.radioCount

    LaunchedEffect(selectedTab) {
        if (selectedTab != MainTab.WEB_STREAMS) {
            isWebViewFullscreen = false
        }
        if (selectedTab != MainTab.LIVE_TV_LIGHT) {
            isLiveTvLightFullscreen = false
        } else {
            // Vorschlag A: Haupt-Player beim Wechsel in Live TV Light pausieren (kein Doppelton)
            viewModel.playerManager.pause()
        }
    }

    val isFullscreenPlaybackActive = isPlayerFullscreen && currentlyPlaying != null
    val isWebFullscreenActive = (isWebViewFullscreen && selectedTab == MainTab.WEB_STREAMS) ||
        (isLiveTvLightFullscreen && selectedTab == MainTab.LIVE_TV_LIGHT)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        ObsidianBlack,
                        DarkNavyBackground,
                        ObsidianBlack
                    )
                )
            )
    ) {
        if (!isFullscreenPlaybackActive) {
            if (isTvOrWide) {
                Row(modifier = Modifier.fillMaxSize()) {
                    if (!isWebFullscreenActive) {
                        TvNavigationRail(
                            selectedTab = selectedTab,
                            onTabSelected = { viewModel.selectTab(it) },
                            liveCount = badgeCounts.liveCount,
                            radioCount = totalRadioCount,
                            movieCount = badgeCounts.movieCount,
                            seriesCount = badgeCounts.seriesCount,
                            favoriteCount = badgeCounts.favoriteCount,
                            isEnglish = isEnglish
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        if (!isWebFullscreenActive) {
                            TopBarSection(
                            playlists = playlists,
                            activePlaylist = activePlaylist,
                            onPlaylistSelected = { viewModel.switchPlaylist(it) },
                            onRefreshPlaylist = { viewModel.refreshPlaylist(it) },
                            isRefreshing = isRefreshing,
                            searchQuery = searchQuery,
                            onSearchQueryChange = { viewModel.setSearchQuery(it) },
                            categories = categories,
                            selectedCategory = selectedCategory,
                            onCategorySelected = { viewModel.setSelectedCategory(it) },
                            liveCount = badgeCounts.liveCount,
                            favoriteCount = badgeCounts.favoriteCount,
                            sportCount = badgeCounts.sportCount,
                            adultCount = badgeCounts.adultCount,
                            internationalCount = badgeCounts.internationalCount,
                            radioCount = badgeCounts.radioCount,
                            movieCount = badgeCounts.movieCount,
                            seriesCount = badgeCounts.seriesCount,
                            channels = allChannels,
                            precomputedCountryFolders = countryFolders,
                            isEnglish = isEnglish,
                            countrySummary = countrySummary,
                            disabledCountries = disabledCountries,
                            hiddenChannelIds = hiddenChannelIds,
                            onToggleCountry = { viewModel.toggleCountry(it) },
                            onToggleChannelHidden = { viewModel.toggleChannelHidden(it) },
                            onOpenAddPlaylist = {
                                addPlaylistInitialTab = 0
                                showAddPlaylistDialog = true
                            },
                            onOpenAddMacPortal = {
                                addPlaylistInitialTab = 2
                                showAddPlaylistDialog = true
                            },
                            onOpenImportAssistant = { showImportAssistantDialog = true },
                            onEditPlaylistRequested = {
                                playlistToUnlock = it
                                showPinPrompt = true
                            },
                            onDeletePlaylistRequested = { viewModel.deletePlaylist(it) },
                            onDeleteAllPlaylistsRequested = { viewModel.deleteAllPlaylists() },
                            isTvMode = true,
                            compactMode = true,
                            channelSortMode = channelSortMode,
                            onSortModeSelected = { viewModel.setChannelSortMode(it) },
                            onEnableAllCountries = { viewModel.enableAllCountries() },
                            onDisableAllCountries = { viewModel.disableAllCountries() },
                            onRemoveAllChannelsInCountry = { viewModel.removeAllChannelsInCountry(it) },
                            onRestoreAllChannelsInCountry = { viewModel.restoreAllChannelsInCountry(it) },
                            onRestoreAllChannels = { viewModel.restoreAllChannels() },
                            onResetCountryAndChannelFilters = { viewModel.resetCountryAndChannelFilters() },
                            onAddCustomChannel = { name, url, code, group ->
                                viewModel.addCustomChannel(name, url, code, group)
                            },
                            showPlaylistSelector = true,
                            onOpenSettings = { viewModel.selectTab(MainTab.SETTINGS) },
                            selectedTab = selectedTab
                        )
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                if (playlistUpdateNotice.isVisible && !isWebFullscreenActive) {
                                    PlaylistUpdateBanner(
                                        notice = playlistUpdateNotice,
                                        onDismiss = { viewModel.dismissPlaylistUpdateNotice() },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                    MainScreenContent(
                                        viewModel = viewModel,
                                        selectedTab = selectedTab,
                                        isTvOrWide = true,
                                        isEnglish = isEnglish,
                                        onOpenAddPlaylist = {
                                            addPlaylistInitialTab = 0
                                            showAddPlaylistDialog = true
                                        },
                                        onOpenAddMacPortal = {
                                            addPlaylistInitialTab = 2
                                            showAddPlaylistDialog = true
                                        },
                                        onOpenImportAssistant = { showImportAssistantDialog = true },
                                        onEditPlaylist = {
                                            playlistToUnlock = it
                                            showPinPrompt = true
                                        },
                                        onOpenBackupDialog = { showBackupDialog = true },
                                        onOpenRestoreDialog = { showRestoreDialog = true },
                                        onOpenDownloadManager = { showVodDownloadManager = true },
                                        onWebFullscreenChanged = { isWebViewFullscreen = it },
                                        onLiveTvLightFullscreenChanged = { isLiveTvLightFullscreen = it }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    topBar = {
                        if (!isWebFullscreenActive) {
                        TopBarSection(
                            playlists = playlists,
                            activePlaylist = activePlaylist,
                            onPlaylistSelected = { viewModel.switchPlaylist(it) },
                            onRefreshPlaylist = { viewModel.refreshPlaylist(it) },
                            isRefreshing = isRefreshing,
                            searchQuery = searchQuery,
                            onSearchQueryChange = { viewModel.setSearchQuery(it) },
                            categories = categories,
                            selectedCategory = selectedCategory,
                            onCategorySelected = { viewModel.setSelectedCategory(it) },
                            liveCount = badgeCounts.liveCount,
                            favoriteCount = badgeCounts.favoriteCount,
                            sportCount = badgeCounts.sportCount,
                            adultCount = badgeCounts.adultCount,
                            internationalCount = badgeCounts.internationalCount,
                            radioCount = badgeCounts.radioCount,
                            movieCount = badgeCounts.movieCount,
                            seriesCount = badgeCounts.seriesCount,
                            channels = allChannels,
                            precomputedCountryFolders = countryFolders,
                            isEnglish = isEnglish,
                            countrySummary = countrySummary,
                            disabledCountries = disabledCountries,
                            hiddenChannelIds = hiddenChannelIds,
                            onToggleCountry = { viewModel.toggleCountry(it) },
                            onToggleChannelHidden = { viewModel.toggleChannelHidden(it) },
                            onOpenAddPlaylist = {
                                addPlaylistInitialTab = 0
                                showAddPlaylistDialog = true
                            },
                            onOpenAddMacPortal = {
                                addPlaylistInitialTab = 2
                                showAddPlaylistDialog = true
                            },
                            onOpenImportAssistant = { showImportAssistantDialog = true },
                            onEditPlaylistRequested = {
                                playlistToUnlock = it
                                showPinPrompt = true
                            },
                            onDeletePlaylistRequested = { viewModel.deletePlaylist(it) },
                            onDeleteAllPlaylistsRequested = { viewModel.deleteAllPlaylists() },
                            isTvMode = false,
                            compactMode = true,
                            channelSortMode = channelSortMode,
                            onSortModeSelected = { viewModel.setChannelSortMode(it) },
                            onEnableAllCountries = { viewModel.enableAllCountries() },
                            onDisableAllCountries = { viewModel.disableAllCountries() },
                            onRemoveAllChannelsInCountry = { viewModel.removeAllChannelsInCountry(it) },
                            onRestoreAllChannelsInCountry = { viewModel.restoreAllChannelsInCountry(it) },
                            onRestoreAllChannels = { viewModel.restoreAllChannels() },
                            onResetCountryAndChannelFilters = { viewModel.resetCountryAndChannelFilters() },
                            onAddCustomChannel = { name, url, code, group ->
                                viewModel.addCustomChannel(name, url, code, group)
                            },
                            showPlaylistSelector = true,
                            onOpenSettings = { viewModel.selectTab(MainTab.SETTINGS) },
                            selectedTab = selectedTab
                        )
                        }
                    },
                    bottomBar = {
                        if (!isWebFullscreenActive) {
                            BottomNavigationSection(
                                selectedTab = selectedTab,
                                onTabSelected = { viewModel.selectTab(it) },
                                liveCount = badgeCounts.liveCount,
                                radioCount = totalRadioCount,
                                movieCount = badgeCounts.movieCount,
                                seriesCount = badgeCounts.seriesCount,
                                favoriteCount = badgeCounts.favoriteCount,
                                isEnglish = isEnglish
                            )
                        }
                    }
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (playlistUpdateNotice.isVisible && !isWebFullscreenActive) {
                                PlaylistUpdateBanner(
                                    notice = playlistUpdateNotice,
                                    onDismiss = { viewModel.dismissPlaylistUpdateNotice() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                MainScreenContent(
                                    viewModel = viewModel,
                                    selectedTab = selectedTab,
                                    isTvOrWide = false,
                                    isEnglish = isEnglish,
                                    onOpenAddPlaylist = {
                                        addPlaylistInitialTab = 0
                                        showAddPlaylistDialog = true
                                    },
                                    onOpenAddMacPortal = {
                                        addPlaylistInitialTab = 2
                                        showAddPlaylistDialog = true
                                    },
                                    onOpenImportAssistant = { showImportAssistantDialog = true },
                                    onEditPlaylist = {
                                        playlistToUnlock = it
                                        showPinPrompt = true
                                    },
                                    onOpenBackupDialog = { showBackupDialog = true },
                                    onOpenRestoreDialog = { showRestoreDialog = true },
                                    onOpenDownloadManager = { showVodDownloadManager = true },
                                    onWebFullscreenChanged = { isWebViewFullscreen = it },
                                    onLiveTvLightFullscreenChanged = { isLiveTvLightFullscreen = it }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Fullscreen Video Player overlay when a channel is actively playing
        if (isFullscreenPlaybackActive) {
            currentlyPlaying?.let { channel ->
                val playerChannelList = when {
                    channel.playlistId == LIVE_TV_LIGHT_PLAYLIST_ID -> LiveTvLightCatalog.asChannelItems()
                    channel.type == ContentType.RADIO && localRadioBrowserStations.isNotEmpty() ->
                        localRadioBrowserStations.map { it.toChannelItem() }
                    else -> filteredLiveChannels.ifEmpty { allChannels }
                }
                NexusPlayerView(
                    channel = channel,
                    allChannels = playerChannelList,
                    playerManager = viewModel.playerManager,
                    settings = settings,
                    isRecording = isRecordingNow,
                    onToggleRecording = { viewModel.toggleLiveRecording() },
                    onScheduleRecording = { startH, startM, endH, endM ->
                        viewModel.scheduleTimedRecording(channel, startH, startM, endH, endM)
                    },
                    onDownloadVod = { vodCh ->
                        viewModel.downloadVodContent(vodCh)
                    },
                    onSelectChannel = { newCh -> viewModel.playContent(newCh) },
                    onClose = { viewModel.closePlayer() },
                    onToggleFavorite = { viewModel.toggleFavorite(channel) },
                    radioRecordingInfo = activeRadioRecordingInfo,
                    radioSongTitle = activeRadioSongTitle,
                    isEnglish = isEnglish
                )
            }
        }

        // VOD Download & Recording Manager Dialog
        if (showVodDownloadManager) {
            VodDownloadManagerDialog(
                recordings = recordings,
                isEnglish = isEnglish,
                onDismiss = { showVodDownloadManager = false },
                onPlayOffline = { item -> viewModel.playRecording(item) },
                onCancelDownload = { item -> viewModel.cancelDownload(item) },
                onDeleteRecording = { item -> viewModel.deleteRecording(item) },
                onClearQueue = { viewModel.clearQueuedDownloads() }
            )
        }

        // Add Playlist Dialog
        if (showAddPlaylistDialog) {
            AddPlaylistDialog(
                onDismiss = { showAddPlaylistDialog = false },
                onAddM3uUrl = { name, url ->
                    viewModel.addM3uUrlPlaylist(name, url)
                    showAddPlaylistDialog = false
                },
                onAddXtream = { name, url, user, pass ->
                    viewModel.addXtreamPlaylist(name, url, user, pass)
                    showAddPlaylistDialog = false
                },
                onAddMacPortal = { name, portalUrl, mac ->
                    viewModel.addMacPortalPlaylist(name, portalUrl, mac)
                    showAddPlaylistDialog = false
                },
                onAddLocalFile = { name, uri ->
                    viewModel.addLocalM3uFile(name, uri)
                    showAddPlaylistDialog = false
                },
                onAddBatchWithoutSync = { candidates ->
                    viewModel.importCandidatesBatch(candidates)
                    showAddPlaylistDialog = false
                },
                onOpenImportAssistant = {
                    showAddPlaylistDialog = false
                    showImportAssistantDialog = true
                },
                initialTypeIndex = addPlaylistInitialTab,
                isEnglish = isEnglish
            )
        }

        // MAC Portal Connection Progress & Live Channel Counter Dialog
        val currentMacState = macPortalDialogState
        if (currentMacState != null && currentMacState.isVisible) {
            MacPortalConnectionDialog(
                state = currentMacState,
                onDismiss = { viewModel.dismissMacPortalDialog() },
                onRetry = { viewModel.retryMacPortalConnection() },
                onOpenPlaylist = { playlistId ->
                    viewModel.switchPlaylist(playlistId)
                    viewModel.selectTab(MainTab.LIVE_TV)
                },
                isEnglish = isEnglish
            )
        }

        // Playlist Edit PIN Prompt Dialog
        if (showPinPrompt && playlistToUnlock != null) {
            PinPromptDialog(
                title = if (isEnglish) "PIN Verification" else "PIN-Abfrage: ${playlistToUnlock?.name}",
                subtitle = if (isEnglish) "Enter PIN to edit this playlist" else "Gib die Sicherheits-PIN ein, um die Playliste zu bearbeiten.",
                expectedPin = "1971",
                onDismiss = {
                    showPinPrompt = false
                    playlistToUnlock = null
                },
                onPinSuccess = {
                    showPinPrompt = false
                    playlistToEdit = playlistToUnlock
                    playlistToUnlock = null
                },
                isEnglish = isEnglish
            )
        }

        // Edit Playlist Dialog
        if (playlistToEdit != null) {
            EditPlaylistDialog(
                playlist = playlistToEdit!!,
                onDismiss = { playlistToEdit = null },
                onSave = { updated ->
                    viewModel.updatePlaylist(updated)
                    playlistToEdit = null
                },
                isEnglish = isEnglish
            )
        }

        // Playlist Import Assistant Dialog
        if (showImportAssistantDialog) {
            PlaylistImportAssistantDialog(
                onDismiss = { showImportAssistantDialog = false },
                onImportConfirmed = { candidates, rawM3uText, fileUri ->
                    val selected = candidates.filter { it.isSelected }
                    viewModel.importCandidatesBatch(selected, rawM3uText, fileUri)
                    showImportAssistantDialog = false
                },
                isEnglish = isEnglish
            )
        }

        // Backup Export Dialog
        if (showBackupDialog) {
            BackupExportDialog(
                onDismiss = { showBackupDialog = false },
                onGenerateBackup = { pwd ->
                    viewModel.exportBackup(pwd)
                },
                playlistsCount = playlists.size,
                isEnglish = isEnglish
            )
        }

        // Backup Restore Dialog
        if (showRestoreDialog) {
            BackupRestoreDialog(
                onDismiss = { showRestoreDialog = false },
                onRestoreBackup = { json, pwd ->
                    viewModel.restoreBackup(json, pwd)
                    showRestoreDialog = false
                },
                isEnglish = isEnglish
            )
        }

        // Installation PIN Lock Dialog (Passwort 1971 erforderlich vor Erstnutzung/Installation)
        if (!isAppUnlocked) {
            InstallationPasswordDialog(
                expectedPin = "1971",
                onPinSuccess = { viewModel.unlockAppWithInstallationPin() },
                isEnglish = isEnglish
            )
        }
    }
}

@Composable
private fun MainScreenContent(
    viewModel: MainViewModel,
    selectedTab: MainTab,
    isTvOrWide: Boolean,
    isEnglish: Boolean,
    onOpenAddPlaylist: () -> Unit = {},
    onOpenAddMacPortal: () -> Unit = {},
    onOpenImportAssistant: () -> Unit = {},
    onEditPlaylist: (PlaylistEntity) -> Unit = {},
    onOpenBackupDialog: () -> Unit = {},
    onOpenRestoreDialog: () -> Unit = {},
    onOpenDownloadManager: () -> Unit = {},
    onWebFullscreenChanged: (Boolean) -> Unit = {},
    onLiveTvLightFullscreenChanged: (Boolean) -> Unit = {}
) {
    val filteredLiveChannels by viewModel.filteredLiveChannels.collectAsStateWithLifecycle()
    val filteredMovies by viewModel.filteredMovies.collectAsStateWithLifecycle()
    val filteredSeries by viewModel.filteredSeries.collectAsStateWithLifecycle()
    val filteredRadio by viewModel.filteredRadio.collectAsStateWithLifecycle()
    val favoriteChannels by viewModel.favoriteChannels.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val liveEpgMap by viewModel.liveEpgMap.collectAsStateWithLifecycle()
    val epgGridMap by viewModel.epgGridMap.collectAsStateWithLifecycle()
    val epgSyncState by viewModel.epgSyncState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val activePlaylistId by viewModel.activePlaylistId.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val vodResumeList by viewModel.vodResumeList.collectAsStateWithLifecycle()
    val allVodHistory by viewModel.allVodHistory.collectAsStateWithLifecycle()

    // Adaptive Layout Mode for Live TV: defaults to GRID on Android TV / Wide Screens, COMFORT_LIST on phones
    var liveLayoutMode by remember(isTvOrWide) {
        mutableStateOf(
            if (isTvOrWide) LiveChannelLayoutMode.GRID else LiveChannelLayoutMode.COMFORT_LIST
        )
    }

    when (selectedTab) {
        MainTab.LIVE_TV -> {
            val displayChannels = filteredLiveChannels

            Column(modifier = Modifier.fillMaxSize()) {
                // =====================================================================
                // LIVE STUDIO STATUS- & ANSICHTS-LEISTE (Grid / Komfort / Kompakt / EPG)
                // =====================================================================
                Surface(
                    color = DarkNavyCard.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(AccentEmerald)
                            )
                            Text(
                                text = when {
                                    selectedCategory.equals("ALLE", ignoreCase = true) || selectedCategory.isBlank() ->
                                        if (isEnglish) "All Live Channels" else "Alle Live-Sender"
                                    selectedCategory.equals("FAVORITEN", ignoreCase = true) ->
                                        if (isEnglish) "Favorite Channels" else "Favoriten"
                                    else -> selectedCategory
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Surface(
                                color = GoldPrimary.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "${displayChannels.size}",
                                    color = GoldLight,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Rechts: EPG-Schnellsprung + Layout-Modus-Schalter (Grid / Komfort / Kompakt)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // EPG Guide Quick Jump
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DarkSurface)
                                    .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                                    .clickable { viewModel.selectTab(MainTab.EPG_GUIDE) }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarMonth,
                                        contentDescription = "EPG Guide",
                                        tint = GoldPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "EPG",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }
                            }

                            // Segmented View Mode Switcher
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ObsidianBlack)
                                    .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                                    .padding(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Grid View
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (liveLayoutMode == LiveChannelLayoutMode.GRID) GoldPrimary
                                            else Color.Transparent
                                        )
                                        .clickable { liveLayoutMode = LiveChannelLayoutMode.GRID }
                                        .padding(horizontal = 7.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GridView,
                                        contentDescription = "Kachel-Ansicht",
                                        tint = if (liveLayoutMode == LiveChannelLayoutMode.GRID) ObsidianBlack else TextSecondary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }

                                // Comfort List View
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (liveLayoutMode == LiveChannelLayoutMode.COMFORT_LIST) GoldPrimary
                                            else Color.Transparent
                                        )
                                        .clickable { liveLayoutMode = LiveChannelLayoutMode.COMFORT_LIST }
                                        .padding(horizontal = 7.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ViewList,
                                        contentDescription = "Komfort-Liste",
                                        tint = if (liveLayoutMode == LiveChannelLayoutMode.COMFORT_LIST) ObsidianBlack else TextSecondary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }

                                // Compact List View
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (liveLayoutMode == LiveChannelLayoutMode.COMPACT_LIST) GoldPrimary
                                            else Color.Transparent
                                        )
                                        .clickable { liveLayoutMode = LiveChannelLayoutMode.COMPACT_LIST }
                                        .padding(horizontal = 7.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DensitySmall,
                                        contentDescription = "Kompakt-Liste",
                                        tint = if (liveLayoutMode == LiveChannelLayoutMode.COMPACT_LIST) ObsidianBlack else TextSecondary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (displayChannels.isEmpty()) {
                    StudioEmptyStateCard(
                        icon = Icons.Default.Tv,
                        title = if (isEnglish) "No TV channels found" else "Keine TV-Sender in dieser Auswahl",
                        subtitle = if (isEnglish) {
                            "Reset your active filter, add a playlist, or launch German Live TV Light directly."
                        } else {
                            "Setze den aktiven Länder-/Kategoriefilter zurück, füge eine Playliste hinzu oder starte direkt Live TV Light."
                        },
                        primaryActionText = if (selectedCategory != "ALLE" || searchQuery.isNotBlank()) {
                            if (isEnglish) "Reset Filters" else "Alle Filter zurücksetzen"
                        } else {
                            if (isEnglish) "+ Add Playlist" else "+ Playliste hinzufügen"
                        },
                        onPrimaryAction = {
                            if (selectedCategory != "ALLE" || searchQuery.isNotBlank()) {
                                viewModel.setSelectedCategory("ALLE")
                                viewModel.setSearchQuery("")
                            } else {
                                onOpenAddPlaylist()
                            }
                        },
                        secondaryActionText = "⚡ Live TV Light öffnen",
                        onSecondaryAction = { viewModel.selectTab(MainTab.LIVE_TV_LIGHT) }
                    )
                } else {
                    val showCountryHeaders = (selectedCategory.equals("ALLE", ignoreCase = true) || selectedCategory.isBlank()) && searchQuery.isBlank()

                    val liveListItems = remember(displayChannels, showCountryHeaders) {
                        if (!showCountryHeaders) {
                            displayChannels.map { LiveChannelItemEntry.Channel(it) }
                        } else {
                            val result = ArrayList<LiveChannelItemEntry>(displayChannels.size + 32)
                            var currentHeaderCountry: String? = null

                            val countryCounts = HashMap<String, Int>(32)
                            for (ch in displayChannels) {
                                val code = ch.countryCode.uppercase().trim().ifBlank { if (ch.isDach) "DE" else "INT" }
                                countryCounts[code] = (countryCounts[code] ?: 0) + 1
                            }

                            for (ch in displayChannels) {
                                val code = ch.countryCode.uppercase().trim().ifBlank { if (ch.isDach) "DE" else "INT" }
                                if (code != currentHeaderCountry) {
                                    currentHeaderCountry = code
                                    val cCount = countryCounts[code] ?: 0
                                    val flag = when (code) {
                                        "DE" -> "🇩🇪"
                                        "AT" -> "🇦🇹"
                                        "CH" -> "🇨🇭"
                                        "TR" -> "🇹🇷"
                                        "UK", "GB" -> "🇬🇧"
                                        "US" -> "🇺🇸"
                                        "IT" -> "🇮🇹"
                                        "FR" -> "🇫🇷"
                                        "ES" -> "🇪🇸"
                                        "PL" -> "🇵🇱"
                                        "NL" -> "🇳🇱"
                                        "EXYU" -> "🇷🇸"
                                        "RU" -> "🇷🇺"
                                        "ADULT" -> "🔞"
                                        else -> "🌐"
                                    }
                                    val cName = when (code) {
                                        "DE" -> "Deutschland"
                                        "AT" -> "Österreich"
                                        "CH" -> "Schweiz"
                                        "TR" -> "Türkei"
                                        "UK", "GB" -> "United Kingdom"
                                        "US" -> "USA"
                                        "IT" -> "Italien"
                                        "FR" -> "Frankreich"
                                        "ES" -> "Spanien"
                                        "PL" -> "Polen"
                                        "NL" -> "Niederlande"
                                        "EXYU" -> "Ex-Jugoslawien"
                                        "RU" -> "Russland"
                                        "ADULT" -> "18+ Adult"
                                        else -> "International"
                                    }
                                    result.add(
                                        LiveChannelItemEntry.Header(
                                            key = "hdr_${code}_${ch.id}",
                                            countryName = cName,
                                            countryCode = code,
                                            flagEmoji = flag,
                                            channelCount = cCount
                                        )
                                    )
                                }
                                result.add(LiveChannelItemEntry.Channel(ch))
                            }
                            result
                        }
                    }

                    if (liveLayoutMode == LiveChannelLayoutMode.GRID) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = if (isTvOrWide) 320.dp else 260.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(
                                items = liveListItems,
                                key = { entry ->
                                    when (entry) {
                                        is LiveChannelItemEntry.Header -> entry.key
                                        is LiveChannelItemEntry.Channel -> entry.channel.id
                                    }
                                },
                                span = { entry ->
                                    when (entry) {
                                        is LiveChannelItemEntry.Header -> GridItemSpan(maxLineSpan)
                                        is LiveChannelItemEntry.Channel -> GridItemSpan(1)
                                    }
                                },
                                contentType = { entry ->
                                    when (entry) {
                                        is LiveChannelItemEntry.Header -> 0
                                        is LiveChannelItemEntry.Channel -> 1
                                    }
                                }
                            ) { entry ->
                                when (entry) {
                                    is LiveChannelItemEntry.Header -> {
                                        CountrySectionHeader(
                                            countryName = entry.countryName,
                                            countryCode = entry.countryCode,
                                            flagEmoji = entry.flagEmoji,
                                            channelCount = entry.channelCount
                                        )
                                    }
                                    is LiveChannelItemEntry.Channel -> {
                                        val ch = entry.channel
                                        val livePrograms = liveEpgMap[ch.id]
                                        ChannelCard(
                                            channel = ch,
                                            onClick = { viewModel.playContent(ch) },
                                            onToggleFavorite = { viewModel.toggleFavorite(ch) },
                                            currentProgram = livePrograms?.first,
                                            nextProgram = livePrograms?.second,
                                            epgTimezone = settings.epgTimezone,
                                            epgTimeOffsetHours = settings.epgTimeOffsetHours,
                                            isCompact = false
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        val isCompactCard = liveLayoutMode == LiveChannelLayoutMode.COMPACT_LIST
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(if (isCompactCard) 6.dp else 9.dp)
                        ) {
                            items(
                                items = liveListItems,
                                key = { entry ->
                                    when (entry) {
                                        is LiveChannelItemEntry.Header -> entry.key
                                        is LiveChannelItemEntry.Channel -> entry.channel.id
                                    }
                                },
                                contentType = { entry ->
                                    when (entry) {
                                        is LiveChannelItemEntry.Header -> 0
                                        is LiveChannelItemEntry.Channel -> 1
                                    }
                                }
                            ) { entry ->
                                when (entry) {
                                    is LiveChannelItemEntry.Header -> {
                                        CountrySectionHeader(
                                            countryName = entry.countryName,
                                            countryCode = entry.countryCode,
                                            flagEmoji = entry.flagEmoji,
                                            channelCount = entry.channelCount
                                        )
                                    }
                                    is LiveChannelItemEntry.Channel -> {
                                        val ch = entry.channel
                                        val livePrograms = liveEpgMap[ch.id]
                                        ChannelCard(
                                            channel = ch,
                                            onClick = { viewModel.playContent(ch) },
                                            onToggleFavorite = { viewModel.toggleFavorite(ch) },
                                            currentProgram = livePrograms?.first,
                                            nextProgram = livePrograms?.second,
                                            epgTimezone = settings.epgTimezone,
                                            epgTimeOffsetHours = settings.epgTimeOffsetHours,
                                            isCompact = isCompactCard
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        MainTab.SEARCH -> {
            if (searchQuery.isBlank()) {
                StudioEmptyStateCard(
                    icon = Icons.Default.Search,
                    title = if (isEnglish) "Global Studio Search" else "Globale Studio-Suche",
                    subtitle = if (isEnglish) {
                        "Enter a channel, movie, or series name in the search bar at the top."
                    } else {
                        "Gib oben im Suchfeld den Namen eines TV-Senders, Films oder einer Serie ein."
                    }
                )
            } else if (searchResults.isEmpty()) {
                StudioEmptyStateCard(
                    icon = Icons.Default.Search,
                    title = if (isEnglish) "No results for \"$searchQuery\"" else "Keine Treffer für \"$searchQuery\"",
                    subtitle = if (isEnglish) "Try a shorter keyword or clear the search query." else "Versuche einen kürzeren Suchbegriff oder setze die Suche zurück.",
                    primaryActionText = if (isEnglish) "Clear Search" else "Suche zurücksetzen",
                    onPrimaryAction = { viewModel.setSearchQuery("") }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(searchResults, key = { it.id }) { ch ->
                        val livePrograms = liveEpgMap[ch.id]
                        ChannelCard(
                            channel = ch,
                            onClick = { viewModel.playContent(ch) },
                            onToggleFavorite = { viewModel.toggleFavorite(ch) },
                            currentProgram = livePrograms?.first,
                            nextProgram = livePrograms?.second,
                            epgTimezone = settings.epgTimezone,
                            epgTimeOffsetHours = settings.epgTimeOffsetHours
                        )
                    }
                }
            }
        }
        MainTab.FAVORITES -> {
            val favorites = favoriteChannels
            if (favorites.isEmpty()) {
                StudioEmptyStateCard(
                    icon = Icons.Default.FavoriteBorder,
                    title = if (isEnglish) "No favorites saved yet" else "Noch keine Favoriten gespeichert",
                    subtitle = if (isEnglish) {
                        "Tap the heart icon on any channel card to pin your favorite stations here."
                    } else {
                        "Tippe bei einem beliebigen Sender auf das Herz-Symbol, um deine Lieblingssender hier anzuheften."
                    },
                    primaryActionText = if (isEnglish) "Browse Live TV" else "Zu Live TV wechseln",
                    onPrimaryAction = { viewModel.selectTab(MainTab.LIVE_TV) }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(favorites, key = { it.id }) { ch ->
                        val livePrograms = liveEpgMap[ch.id]
                        ChannelCard(
                            channel = ch,
                            onClick = { viewModel.playContent(ch) },
                            onToggleFavorite = { viewModel.toggleFavorite(ch) },
                            currentProgram = livePrograms?.first,
                            nextProgram = livePrograms?.second,
                            epgTimezone = settings.epgTimezone,
                            epgTimeOffsetHours = settings.epgTimeOffsetHours
                        )
                    }
                }
            }
        }
        MainTab.CONTINUE_WATCHING -> {
            VodContinueWatchingScreen(
                resumeItems = vodResumeList,
                allVodItems = allVodHistory,
                liveTvChannels = filteredLiveChannels.take(20),
                liveEpgMap = liveEpgMap,
                settings = settings,
                onResumeItem = { item -> viewModel.resumeVodItem(item) },
                onRestartItem = { item ->
                    viewModel.resumeVodItem(item.copy(lastPositionSeconds = 0L))
                },
                onDeleteItem = { item -> viewModel.deleteVodResumeItem(item) },
                onClearAllVodHistory = { viewModel.clearAllVodHistory() },
                onPlayLiveChannel = { viewModel.playContent(it) },
                onToggleFavorite = { viewModel.toggleFavorite(it) },
                isEnglish = isEnglish
            )
        }
        MainTab.RADIO -> {
            val localRadioStations by viewModel.localRadioStations.collectAsStateWithLifecycle()
            val radioOnlineResults by viewModel.radioOnlineResults.collectAsStateWithLifecycle()
            val radioSyncState by viewModel.radioSyncState.collectAsStateWithLifecycle()
            val isSearchingRadioOnline by viewModel.isSearchingRadioOnline.collectAsStateWithLifecycle()
            val radioOnlineError by viewModel.radioOnlineError.collectAsStateWithLifecycle()
            RadioExplorer(
                radioStations = filteredRadio,
                localRadioBrowserStations = localRadioStations,
                onlineSearchResults = radioOnlineResults,
                syncState = radioSyncState,
                isSearchingOnline = isSearchingRadioOnline,
                onlineSearchError = radioOnlineError,
                onPlayRadio = { viewModel.playContent(it) },
                onPlayRadioBrowserStation = { viewModel.playRadioStation(it) },
                onToggleFavorite = { viewModel.toggleFavorite(it) },
                onToggleRadioBrowserFavorite = { viewModel.toggleRadioStationFavorite(it) },
                onSyncRadioBrowser = { viewModel.syncRadioBrowserStations() },
                onSearchOnline = { query, onlyMp3 -> viewModel.searchRadioBrowserOnline(query, onlyMp3) },
                onClearOnlineSearch = { viewModel.clearRadioOnlineSearch() },
                onSaveStationLocally = { viewModel.saveRadioStationLocally(it) },
                onOpenRecordings = onOpenDownloadManager,
                isEnglish = isEnglish
            )
        }
        MainTab.MOVIES -> {
            val recordings by viewModel.recordings.collectAsStateWithLifecycle()
            MovieExplorer(
                movies = filteredMovies,
                recordings = recordings,
                onPlayMovie = { viewModel.playContent(it) },
                onDownloadMovie = { viewModel.downloadVodContent(it) },
                onEnqueueMovies = { viewModel.enqueueVodDownloads(it) },
                onOpenDownloadManager = onOpenDownloadManager,
                isEnglish = isEnglish
            )
        }
        MainTab.SERIES -> {
            val recordings by viewModel.recordings.collectAsStateWithLifecycle()
            SeriesExplorer(
                seriesChannels = filteredSeries,
                recordings = recordings,
                onPlayEpisode = { viewModel.playContent(it) },
                onDownloadEpisode = { viewModel.downloadVodContent(it) },
                onEnqueueEpisodes = { viewModel.enqueueVodDownloads(it) },
                onOpenDownloadManager = onOpenDownloadManager,
                onFetchEpisodes = { viewModel.fetchSeriesEpisodes(it) },
                isEnglish = isEnglish
            )
        }
        MainTab.LIVE_TV_LIGHT -> {
            LiveTvLightView(
                onPlayInAppPlayer = { channelItem -> viewModel.playContent(channelItem) },
                isEnglish = isEnglish,
                onFullscreenChanged = onLiveTvLightFullscreenChanged,
                onExitToMainApp = { viewModel.selectTab(MainTab.LIVE_TV) }
            )
        }
        MainTab.LOCAL_M3U -> {
            val scanResult by viewModel.localM3uScanResult.collectAsStateWithLifecycle()
            val isScanning by viewModel.isScanningLocalM3u.collectAsStateWithLifecycle()
            val isImporting by viewModel.isImportingLocalM3u.collectAsStateWithLifecycle()
            val importProgress by viewModel.localM3uImportProgress.collectAsStateWithLifecycle()

            LocalM3uManagerView(
                playlists = playlists,
                activePlaylistId = activePlaylistId,
                scanResult = scanResult,
                isScanning = isScanning,
                isImporting = isImporting,
                importProgressText = importProgress,
                onScanFolderUri = { uri -> viewModel.scanLocalM3uFolder(uri) },
                onScanDocumentUris = { uris -> viewModel.scanLocalM3uFiles(uris) },
                onScanStandardFolders = { viewModel.scanStandardLocalM3uFolders() },
                onRescanLastFolder = { viewModel.rescanLastLocalM3uFolder() },
                onToggleFileSelection = { id -> viewModel.toggleDiscoveredLocalM3uSelection(id) },
                onSelectAllFiles = { selectAll, onlyNew ->
                    viewModel.setAllDiscoveredLocalM3uSelection(selectAll, onlyNew)
                },
                onImportFilesBatch = { files, skipDuplicates ->
                    viewModel.importLocalM3uFilesBatch(files, skipDuplicates)
                },
                onActivatePlaylist = { playlistId ->
                    viewModel.switchPlaylist(playlistId)
                },
                onActivateAndOpenLiveTv = { playlistId ->
                    viewModel.switchPlaylist(playlistId)
                    viewModel.selectTab(MainTab.LIVE_TV)
                },
                onRefreshPlaylist = { pl -> viewModel.refreshPlaylist(pl) },
                onRenamePlaylist = { pl, newName -> viewModel.renameLocalM3uPlaylist(pl, newName) },
                onDeletePlaylist = { pl -> viewModel.deletePlaylist(pl) },
                onRemoveDuplicates = { viewModel.removeDuplicateLocalM3uPlaylists() },
                isEnglish = isEnglish
            )
        }
        MainTab.WEB_STREAMS -> {
            WebStreamBrowserView(
                onPlayDetectedStream = { url, title -> viewModel.playStreamDirect(url, title) },
                onWebFullscreenChanged = onWebFullscreenChanged,
                isEnglish = isEnglish
            )
        }
        MainTab.EPG_GUIDE -> {
            EpgGuideView(
                channels = filteredLiveChannels,
                onSelectChannel = { viewModel.playContent(it) },
                epgTimezone = settings.epgTimezone,
                epgTimeOffsetHours = settings.epgTimeOffsetHours,
                isEnglish = isEnglish,
                epgGridMap = epgGridMap,
                syncState = epgSyncState,
                onSyncEpg = { viewModel.preloadEpgIfNeeded(force = true) },
                loadChannelSchedule = { viewModel.getFullScheduleForChannel(it) }
            )
        }
        MainTab.SETTINGS -> {
            SettingsView(
                settings = settings,
                playlists = playlists,
                recordings = viewModel.recordings.collectAsStateWithLifecycle().value,
                activePlaylistId = activePlaylistId,
                onSelectPlaylist = { viewModel.switchPlaylist(it) },
                onUpdateCachingMode = { viewModel.updateCachingMode(it) },
                onUpdatePreferredPlayer = { viewModel.updatePreferredPlayer(it) },
                onToggleBackgroundPlayback = { viewModel.toggleBackgroundPlayback(it) },
                onUpdateLanguage = { viewModel.updateLanguage(it) },
                onOpenAddPlaylist = onOpenAddPlaylist,
                onOpenAddMacPortal = onOpenAddMacPortal,
                onOpenImportAssistant = onOpenImportAssistant,
                onUpdatePlaylist = onEditPlaylist,
                onDeletePlaylist = { viewModel.deletePlaylist(it) },
                onRefreshPlaylist = { viewModel.refreshPlaylist(it) },
                onOpenBackupDialog = onOpenBackupDialog,
                onOpenRestoreDialog = onOpenRestoreDialog,
                onDeleteRecording = { viewModel.deleteRecording(it) },
                onPlayRecording = { viewModel.playRecording(it) },
                onCancelDownload = { viewModel.cancelDownload(it) },
                onDeleteAllPlaylists = { viewModel.deleteAllPlaylists() },
                onUpdateSyncOnlyGermanWhenGerman = { viewModel.updateSyncOnlyGermanWhenGerman(it) },
                onOptimizeDatabase = { viewModel.optimizeDatabasePerformance() },
                onUpdateAutoUpdateOnStart = { viewModel.updateAutoUpdateOnStart(it) },
                onUpdateNexusFullScreen = { viewModel.updateNexusFullScreenEnabled(it) },
                onUpdateAspectRatio = { viewModel.updateAspectRatio(it) },
                onUpdateEpgTimezone = { viewModel.updateEpgTimezone(it) },
                onUpdateEpgTimeOffsetHours = { viewModel.updateEpgTimeOffsetHours(it) },
                onUpdateProviderEpgOffset = { p, o -> viewModel.updateProviderEpgOffset(p, o) },
                onForceEpgRemap = { viewModel.forceEpgRemap() },
                onUpdateCustomEpgUrl = { viewModel.updateCustomEpgUrl(it) },
                onTriggerEpgSync = { viewModel.triggerEpgSync(it) },
                onSyncBothEpgFeeds = { viewModel.triggerSyncBothNewFeeds() },
                onClearAllEpg = { viewModel.clearAllEpgEntries() },
                syncState = viewModel.epgSyncState.collectAsStateWithLifecycle().value,
                onLockApp = { viewModel.lockApp() },
                isEnglish = isEnglish
            )
        }
        else -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = selectedTab.name, color = TextPrimary)
            }
        }
    }
}

@Composable
private fun StudioEmptyStateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    primaryActionText: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    secondaryActionText: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = DarkNavyCard,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, BorderGlow),
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(DarkSurfaceElevated.copy(alpha = 0.6f), DarkNavyCard)
                        )
                    )
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(GoldPrimary.copy(alpha = 0.16f))
                        .border(1.dp, GoldPrimary.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = GoldPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )

                if (primaryActionText != null && onPrimaryAction != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onPrimaryAction,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GoldPrimary,
                                contentColor = ObsidianBlack
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = primaryActionText,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.5.sp
                            )
                        }

                        if (secondaryActionText != null && onSecondaryAction != null) {
                            OutlinedButton(
                                onClick = onSecondaryAction,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, BorderGlow)
                            ) {
                                Text(
                                    text = secondaryActionText,
                                    color = GoldLight,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private sealed interface LiveChannelItemEntry {
    data class Header(
        val key: String,
        val countryName: String,
        val countryCode: String,
        val flagEmoji: String,
        val channelCount: Int
    ) : LiveChannelItemEntry

    data class Channel(
        val channel: com.example.data.model.ChannelItem
    ) : LiveChannelItemEntry
}
