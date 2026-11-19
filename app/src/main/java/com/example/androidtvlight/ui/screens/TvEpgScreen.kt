package com.example.androidtvlight.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.androidtvlight.domain.model.ChannelEpgSchedule
import com.example.androidtvlight.domain.model.EpgCategory
import com.example.androidtvlight.domain.model.EpgProgramItem
import com.example.androidtvlight.domain.model.EpgTimeFilter
import com.example.androidtvlight.domain.model.ExoTrackInfo
import com.example.androidtvlight.domain.model.TvChannelItem
import com.example.androidtvlight.domain.model.TvRubrik
import com.example.androidtvlight.ui.components.ChannelLogoBadge
import com.example.androidtvlight.ui.components.TvExoPlayerView
import com.example.androidtvlight.ui.components.TvFocusableCard
import com.example.androidtvlight.ui.theme.TvBrightSky
import com.example.androidtvlight.ui.theme.TvCoralAlert
import com.example.androidtvlight.ui.theme.TvElectricCyan
import com.example.androidtvlight.ui.theme.TvEmeraldLive
import com.example.androidtvlight.ui.theme.TvGoldenFocus
import com.example.androidtvlight.ui.theme.TvOnPrimaryDark
import com.example.androidtvlight.ui.viewmodel.TvLightUiState

/**
 * Full-Featured EPG (Electronic Program Guide) Screen:
 * - Click on ANY EPG program card or timeline slot immediately tunes and plays the channel
 *   in the internal ExoPlayer.
 * - Includes automatic background EPG updates (EPG Auto-Update toggle, configurable interval,
 *   live progress synchronization, and manual instant refresh).
 * - Supports "Jetzt Live", "Gleich danach", "20:15 Primetime", and "24h Zeitleiste" views,
 *   plus Rubrik filters, Genre filters, and full-text program search.
 */
@Composable
fun EpgFullFeaturedScreen(
    uiState: TvLightUiState,
    onClickProgramToPlayChannel: (EpgProgramItem, Boolean) -> Unit,
    onShowProgramDetails: (EpgProgramItem?) -> Unit,
    onSelectTimeFilter: (EpgTimeFilter) -> Unit,
    onSelectCategoryFilter: (EpgCategory) -> Unit,
    onSelectRubrik: (TvRubrik) -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleEpgAutoUpdate: () -> Unit,
    onCycleEpgInterval: () -> Unit,
    onManualRefreshEpg: () -> Unit,
    onToggleFavoriteChannel: (String) -> Unit,
    onSelectChannelInPlayer: (TvChannelItem) -> Unit,
    onStepChannel: (Int) -> Unit,
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
    onReturnToLiveTv: () -> Unit = {},
    onExitApp: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val activeChannel = uiState.activeChannel ?: uiState.channels.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("epg_full_featured_screen")
    ) {
        // 1. EMBEDDED INTERNAL EXOPLAYER AT THE TOP (Instant playback when clicking any show in EPG!)
        if (activeChannel != null) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(2.dp, TvElectricCyan),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
            ) {
                TvExoPlayerView(
                    channel = activeChannel,
                    playerState = uiState.playerState,
                    allChannelsInRubrik = uiState.filteredChannels.ifEmpty { uiState.channels },
                    streamServerCount = uiState.activeStreamUrls.size,
                    onSelectChannel = onSelectChannelInPlayer,
                    onPreviousChannel = { onStepChannel(-1) },
                    onNextChannel = { onStepChannel(1) },
                    onTogglePlayPause = onTogglePlayPause,
                    onToggleMute = onToggleMute,
                    onVolumeChange = onVolumeChange,
                    onCycleResizeMode = onCycleResizeMode,
                    onCyclePlaybackSpeed = onCyclePlaybackSpeed,
                    onToggleSubtitles = onToggleSubtitles,
                    onToggleFullscreen = onToggleFullscreen,
                    onToggleRecording = onToggleRecording,
                    onDismissRecordingBanner = onDismissRecordingBanner,
                    onToggleBackgroundPlayback = onToggleBackgroundPlayback,
                    onSwitchStreamServer = onSwitchStreamServer,
                    onToggleFavorite = { onToggleFavoriteChannel(activeChannel.id) },
                    onShowTrackSelector = onShowTrackSelector,
                    onToggleZapperOverlay = onToggleZapperOverlay,
                    onPlayerStatusChanged = onPlayerStatusChanged,
                    onStreamError = onStreamError,
                    onOpenRecordingsDialog = onOpenRecordingsDialog,
                    onReturnToLiveTv = onReturnToLiveTv,
                    onExitApp = onExitApp,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2. EPG AUTO-UPDATE CONTROL BAR + LIVE SYNC STATUS (Responsive for Phone & TV)
        Surface(
            color = Color(0xFF0A1D37),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, TvElectricCyan.copy(alpha = 0.55f)),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .testTag("epg_auto_update_bar")
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                val isCompactBar = maxWidth < 540.dp

                if (isCompactBar) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (uiState.isUpdatingEpg) {
                                CircularProgressIndicator(
                                    color = TvGoldenFocus,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = if (uiState.settings.epgAutoUpdateEnabled) TvEmeraldLive else TvBrightSky,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "EPG (${uiState.filteredEpgSchedules.size} Sender) • Tippen startet Sender",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (uiState.settings.epgAutoUpdateEnabled) {
                                        "Auto-Update: AN (${uiState.settings.epgAutoUpdateIntervalMinutes} Min.) • ${uiState.lastEpgUpdateFormatted}"
                                    } else {
                                        "Auto-Update: AUS • Stand: ${uiState.lastEpgUpdateFormatted}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (uiState.settings.epgAutoUpdateEnabled) TvEmeraldLive else TvBrightSky,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = onManualRefreshEpg,
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("epg_manual_refresh_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "EPG jetzt aktualisieren",
                                    tint = TvElectricCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = onToggleEpgAutoUpdate,
                                shape = RoundedCornerShape(8.dp),
                                color = if (uiState.settings.epgAutoUpdateEnabled) TvEmeraldLive else Color(0xFF132D54),
                                border = BorderStroke(
                                    1.dp,
                                    if (uiState.settings.epgAutoUpdateEnabled) Color.White else TvBrightSky.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("epg_auto_update_toggle")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Autorenew,
                                        contentDescription = "EPG Auto-Update umschalten",
                                        tint = if (uiState.settings.epgAutoUpdateEnabled) TvOnPrimaryDark else Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (uiState.settings.epgAutoUpdateEnabled) "Auto-Update: AN" else "Auto-Update: AUS",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                        color = if (uiState.settings.epgAutoUpdateEnabled) TvOnPrimaryDark else Color.White,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            Surface(
                                onClick = onCycleEpgInterval,
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF132D54),
                                border = BorderStroke(1.dp, TvGoldenFocus),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("epg_interval_button")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 9.dp)
                                ) {
                                    Text(
                                        text = "${uiState.settings.epgAutoUpdateIntervalMinutes} Min.",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = TvGoldenFocus,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (uiState.isUpdatingEpg) {
                                CircularProgressIndicator(
                                    color = TvGoldenFocus,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = if (uiState.settings.epgAutoUpdateEnabled) TvEmeraldLive else TvBrightSky,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "EPG Programmführer (${uiState.filteredEpgSchedules.size} Sender) • Klick auf Sendung startet Sender direkt",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (uiState.settings.epgAutoUpdateEnabled) {
                                        "EPG Auto-Update: AKTIV (alle ${uiState.settings.epgAutoUpdateIntervalMinutes} Min.) • Stand: ${uiState.lastEpgUpdateFormatted}"
                                    } else {
                                        "EPG Auto-Update: AUS • Letzter Stand: ${uiState.lastEpgUpdateFormatted}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (uiState.settings.epgAutoUpdateEnabled) TvEmeraldLive else TvBrightSky,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = onToggleEpgAutoUpdate,
                                shape = RoundedCornerShape(10.dp),
                                color = if (uiState.settings.epgAutoUpdateEnabled) TvEmeraldLive else Color(0xFF132D54),
                                border = BorderStroke(
                                    1.dp,
                                    if (uiState.settings.epgAutoUpdateEnabled) Color.White else TvBrightSky.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("epg_auto_update_toggle")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Autorenew,
                                        contentDescription = "EPG Auto-Update umschalten",
                                        tint = if (uiState.settings.epgAutoUpdateEnabled) TvOnPrimaryDark else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (uiState.settings.epgAutoUpdateEnabled) "Auto-Update: AN" else "Auto-Update: AUS",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                        color = if (uiState.settings.epgAutoUpdateEnabled) TvOnPrimaryDark else Color.White,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            Surface(
                                onClick = onCycleEpgInterval,
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF132D54),
                                border = BorderStroke(1.dp, TvGoldenFocus),
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("epg_interval_button")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 10.dp)
                                ) {
                                    Text(
                                        text = "${uiState.settings.epgAutoUpdateIntervalMinutes} Min.",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = TvGoldenFocus,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            IconButton(
                                onClick = onManualRefreshEpg,
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("epg_manual_refresh_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "EPG jetzt aktualisieren",
                                    tint = TvElectricCyan
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 3. TIME MODE + RUBRIK + GENRE FILTER ROW
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(
                items = EpgTimeFilter.entries,
                key = { it.id }
            ) { timeFilter ->
                val selected = uiState.epgTimeFilter == timeFilter
                FilterChip(
                    selected = selected,
                    onClick = { onSelectTimeFilter(timeFilter) },
                    label = {
                        Text(
                            text = timeFilter.label,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TvGoldenFocus,
                        selectedLabelColor = TvOnPrimaryDark,
                        containerColor = Color(0xFF132D54),
                        labelColor = Color.White
                    ),
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("epg_time_filter_${timeFilter.id}")
                )
            }

            item {
                FilterChip(
                    selected = uiState.selectedRubrik == TvRubrik.DEUTSCHES_TV,
                    onClick = { onSelectRubrik(TvRubrik.DEUTSCHES_TV) },
                    label = {
                        Text(
                            text = "1: DE TV (${uiState.germanChannels.size})",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TvElectricCyan,
                        selectedLabelColor = TvOnPrimaryDark,
                        containerColor = Color(0xFF0E2240),
                        labelColor = Color.White
                    ),
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("epg_rubrik_de")
                )
            }

            item {
                FilterChip(
                    selected = uiState.selectedRubrik == TvRubrik.OESTERREICH_TV,
                    onClick = { onSelectRubrik(TvRubrik.OESTERREICH_TV) },
                    label = {
                        Text(
                            text = "2: AT TV (${uiState.austrianChannels.size})",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TvEmeraldLive,
                        selectedLabelColor = TvOnPrimaryDark,
                        containerColor = Color(0xFF0E2240),
                        labelColor = Color.White
                    ),
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("epg_rubrik_at")
                )
            }

            item {
                FilterChip(
                    selected = uiState.selectedRubrik == TvRubrik.TV2_OHA,
                    onClick = { onSelectRubrik(TvRubrik.TV2_OHA) },
                    label = {
                        Text(
                            text = "DE TV2 (${uiState.tv2Channels.size})",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TvGoldenFocus,
                        selectedLabelColor = TvOnPrimaryDark,
                        containerColor = Color(0xFF0E2240),
                        labelColor = Color.White
                    ),
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("epg_rubrik_tv2")
                )
            }

            item {
                FilterChip(
                    selected = uiState.selectedRubrik == TvRubrik.TV3_LIVE,
                    onClick = { onSelectRubrik(TvRubrik.TV3_LIVE) },
                    label = {
                        Text(
                            text = "TV3 (${uiState.tv3Channels.size})",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TvEmeraldLive,
                        selectedLabelColor = TvOnPrimaryDark,
                        containerColor = Color(0xFF0E2240),
                        labelColor = Color.White
                    ),
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("epg_rubrik_tv3")
                )
            }

            item {
                FilterChip(
                    selected = uiState.selectedRubrik == TvRubrik.ALL,
                    onClick = { onSelectRubrik(TvRubrik.ALL) },
                    label = {
                        Text(
                            text = "Alle (${uiState.channels.size})",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TvElectricCyan,
                        selectedLabelColor = TvOnPrimaryDark,
                        containerColor = Color(0xFF0E2240),
                        labelColor = Color.White
                    ),
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("epg_rubrik_all")
                )
            }

            item {
                FilterChip(
                    selected = uiState.favoritesOnly,
                    onClick = onToggleFavoritesOnly,
                    label = {
                        Text(
                            text = "★ Favoriten",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TvGoldenFocus,
                        selectedLabelColor = TvOnPrimaryDark,
                        containerColor = Color(0xFF0E2240),
                        labelColor = Color.White
                    ),
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("epg_favorites_filter")
                )
            }

            items(
                items = EpgCategory.entries,
                key = { "cat_${it.id}" }
            ) { category ->
                val selected = uiState.epgCategoryFilter == category
                FilterChip(
                    selected = selected,
                    onClick = { onSelectCategoryFilter(category) },
                    label = {
                        Text(
                            text = category.label,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(category.badgeColorHex),
                        selectedLabelColor = TvOnPrimaryDark,
                        containerColor = Color(0xFF0A192F),
                        labelColor = TvBrightSky
                    ),
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("epg_category_${category.id}")
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 4. COMPACT EPG SEARCH FIELD
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = {
                Text(
                    text = "Sendung, Thema oder Sender im EPG suchen...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = TvElectricCyan)
            },
            trailingIcon = {
                if (uiState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Suche löschen", tint = Color.White)
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TvGoldenFocus,
                unfocusedBorderColor = TvBrightSky.copy(alpha = 0.45f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("epg_search_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 5. EPG PROGRAM GUIDE LIST / 24H TIMELINE (Click on ANY show directly calls & starts the channel!)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 88.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("epg_programs_list")
        ) {
            itemsIndexed(
                items = uiState.filteredEpgSchedules,
                key = { _, sched -> sched.channel.id }
            ) { index, schedule ->
                val isPlayingThisChannel = schedule.channel.id == activeChannel?.id
                val isFocused = isPlayingThisChannel || index == uiState.focusedItemIndex

                if (uiState.epgTimeFilter == EpgTimeFilter.TIMELINE) {
                    EpgChannelTimelineRowCard(
                        schedule = schedule,
                        isPlayingChannel = isPlayingThisChannel,
                        isFocused = isFocused,
                        onClickProgramToPlay = { prog -> onClickProgramToPlayChannel(prog, false) },
                        onClickFullscreen = { prog -> onClickProgramToPlayChannel(prog, true) },
                        onShowDetails = { prog -> onShowProgramDetails(prog) }
                    )
                } else {
                    val primaryProgram = when (uiState.epgTimeFilter) {
                        EpgTimeFilter.NOW -> schedule.currentProgram
                        EpgTimeFilter.NEXT -> schedule.nextProgram ?: schedule.currentProgram
                        EpgTimeFilter.PRIMETIME -> schedule.primeTimeProgram ?: schedule.currentProgram
                        EpgTimeFilter.TIMELINE -> schedule.currentProgram
                    } ?: schedule.dayPrograms.firstOrNull()

                    if (primaryProgram != null) {
                        EpgChannelProgramCard(
                            schedule = schedule,
                            program = primaryProgram,
                            nextProgram = schedule.nextProgram,
                            isPlayingChannel = isPlayingThisChannel,
                            isFocused = isFocused,
                            onClickPlayChannel = { onClickProgramToPlayChannel(primaryProgram, false) },
                            onClickPlayFullscreen = { onClickProgramToPlayChannel(primaryProgram, true) },
                            onShowDetails = { onShowProgramDetails(primaryProgram) },
                            onToggleFavorite = { onToggleFavoriteChannel(schedule.channel.id) }
                        )
                    }
                }
            }
        }
    }

    // 6. FULL EPG PROGRAM DETAILS & 24-HOUR CHANNEL SCHEDULE DIALOG
    val detailProgram = uiState.selectedEpgDetailProgram
    if (detailProgram != null) {
        val matchingSchedule = uiState.epgSchedules.find { it.channel.id == detailProgram.channelId }
        EpgProgramDetailDialog(
            program = detailProgram,
            schedule = matchingSchedule,
            onPlayChannelNow = {
                onClickProgramToPlayChannel(detailProgram, false)
            },
            onPlayChannelFullscreen = {
                onClickProgramToPlayChannel(detailProgram, true)
            },
            onSelectOtherProgramToPlay = { prog ->
                onClickProgramToPlayChannel(prog, false)
            },
            onDismiss = { onShowProgramDetails(null) }
        )
    }
}

@Composable
private fun EpgChannelProgramCard(
    schedule: ChannelEpgSchedule,
    program: EpgProgramItem,
    nextProgram: EpgProgramItem?,
    isPlayingChannel: Boolean,
    isFocused: Boolean,
    onClickPlayChannel: () -> Unit,
    onClickPlayFullscreen: () -> Unit,
    onShowDetails: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val channel = schedule.channel
    val combinedDescription = remember(program.subtitle, program.description) {
        listOf(program.subtitle.trim(), program.description.trim())
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" — ")
    }

    TvFocusableCard(
        isSelectedOrFocused = isFocused,
        onClick = onClickPlayChannel,
        testTagName = "epg_program_card_${channel.id}",
        accentColor = if (isPlayingChannel) TvGoldenFocus else Color(channel.accentHex),
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            val isCompact = maxWidth < 540.dp

            if (isCompact) {
                // COMPACT SMARTPHONE LAYOUT: Variable height strictly driven by available content.
                // Logo sits cleanly at top-left, full width for channel/badges, compact action row below.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    // Header Row: Top-aligned Logo + Channel Name / Badges + Favorite Star
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        ChannelLogoBadge(
                            logoUrl = channel.logoUrl,
                            channelName = channel.name,
                            accentColor = Color(channel.accentHex),
                            size = 44.dp,
                            cornerRadius = 10.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Surface(
                                    color = if (isPlayingChannel) TvEmeraldLive else Color(0xFF132D54),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (isPlayingChannel) "▶ LIVE" else "#${channel.number}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                        color = if (isPlayingChannel) TvOnPrimaryDark else TvBrightSky,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = channel.name,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = program.formattedTimeRange,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = TvGoldenFocus,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Surface(
                                    color = Color(program.category.badgeColorHex).copy(alpha = 0.22f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, Color(program.category.badgeColorHex))
                                ) {
                                    Text(
                                        text = program.category.label,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                                if (program.statusBadgeText.isNotBlank()) {
                                    Text(
                                        text = "• ${program.statusBadgeText}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (program.isLiveNow) TvEmeraldLive else TvBrightSky,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = if (channel.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Favorit",
                                tint = if (channel.isFavorite) TvGoldenFocus else Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Program Title (only if present)
                    if (program.title.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = program.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Subtitle / Description (only rendered when non-blank -> 0dp when empty!)
                    if (combinedDescription.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = combinedDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE2E8F0),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (program.isLiveNow) {
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { program.progressFraction },
                            color = TvEmeraldLive,
                            trackColor = Color(0xFF17335C),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }

                    if (nextProgram != null && nextProgram.id != program.id && nextProgram.title.isNotBlank()) {
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = "⏭ Gleich danach (${nextProgram.formattedStartTime} Uhr): ${nextProgram.title}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = TvBrightSky,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Compact Bottom Action Row (Never squeezes the header!)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onClickPlayChannel,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TvElectricCyan,
                                contentColor = TvOnPrimaryDark
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .testTag("epg_play_channel_${channel.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Sender starten",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        IconButton(
                            onClick = onClickPlayFullscreen,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("epg_fullscreen_channel_${channel.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Sendung im Vollbild starten",
                                tint = TvGoldenFocus,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = onShowDetails,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("epg_details_button_${channel.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "EPG Sendungsdetails & 24h Programm",
                                tint = TvBrightSky,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            } else {
                // WIDE / ANDROID TV LAYOUT (Horizontal header with guaranteed non-wrapping badges)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        color = if (isPlayingChannel) TvEmeraldLive else Color(0xFF132D54),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = if (isPlayingChannel) "▶ LÄUFT IM PLAYER" else "#${channel.number}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                            color = if (isPlayingChannel) TvOnPrimaryDark else TvBrightSky,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Surface(
                                        color = Color(program.category.badgeColorHex).copy(alpha = 0.22f),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, Color(program.category.badgeColorHex))
                                    ) {
                                        Text(
                                            text = program.category.label,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = channel.name,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                }

                                Spacer(modifier = Modifier.height(3.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = program.formattedTimeRange,
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                                        color = TvGoldenFocus,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    if (program.statusBadgeText.isNotBlank()) {
                                        Text(
                                            text = "• ${program.statusBadgeText}",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (program.isLiveNow) TvEmeraldLive else TvBrightSky,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = onClickPlayChannel,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TvElectricCyan,
                                    contentColor = TvOnPrimaryDark
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .height(38.dp)
                                    .testTag("epg_play_channel_${channel.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Sender starten",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            IconButton(
                                onClick = onClickPlayFullscreen,
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("epg_fullscreen_channel_${channel.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = "Sendung im Vollbild starten",
                                    tint = TvGoldenFocus
                                )
                            }

                            IconButton(
                                onClick = onShowDetails,
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("epg_details_button_${channel.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "EPG Sendungsdetails & 24h Programm",
                                    tint = TvBrightSky
                                )
                            }

                            IconButton(
                                onClick = onToggleFavorite,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = if (channel.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Favorit",
                                    tint = if (channel.isFavorite) TvGoldenFocus else Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    if (program.title.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = program.title,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (combinedDescription.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = combinedDescription,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFE2E8F0),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (program.isLiveNow) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { program.progressFraction },
                            color = TvEmeraldLive,
                            trackColor = Color(0xFF17335C),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }

                    if (nextProgram != null && nextProgram.id != program.id && nextProgram.title.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "⏭ Gleich danach (${nextProgram.formattedStartTime} Uhr): ${nextProgram.title}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = TvBrightSky,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * 24-Hour Horizontal Timeline Row per Channel:
 * Clicking any program slot in the 24h timeline directly tunes and starts that channel!
 */
@Composable
private fun EpgChannelTimelineRowCard(
    schedule: ChannelEpgSchedule,
    isPlayingChannel: Boolean,
    isFocused: Boolean,
    onClickProgramToPlay: (EpgProgramItem) -> Unit,
    onClickFullscreen: (EpgProgramItem) -> Unit,
    onShowDetails: (EpgProgramItem) -> Unit
) {
    val channel = schedule.channel
    val currentProg = schedule.currentProgram ?: schedule.dayPrograms.firstOrNull()

    TvFocusableCard(
        isSelectedOrFocused = isFocused,
        onClick = {
            if (currentProg != null) onClickProgramToPlay(currentProg)
        },
        testTagName = "epg_timeline_row_${channel.id}",
        accentColor = if (isPlayingChannel) TvGoldenFocus else Color(channel.accentHex),
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(12.dp)
        ) {
            val isCompact = maxWidth < 520.dp

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                            size = if (isCompact) 40.dp else 44.dp,
                            cornerRadius = 10.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${channel.number}. ${channel.name}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isCompact) "Tippen startet Sender" else "Sendung in der Zeitleiste anklicken → Sender startet direkt im ExoPlayer",
                                style = MaterialTheme.typography.labelSmall,
                                color = TvBrightSky,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (currentProg != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { onClickProgramToPlay(currentProg) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TvElectricCyan,
                                    contentColor = TvOnPrimaryDark
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = if (isCompact) 8.dp else 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (isCompact) "Live" else "Live starten",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            IconButton(
                                onClick = { onClickFullscreen(currentProg) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = "Vollbild starten",
                                    tint = TvGoldenFocus
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(end = 8.dp)
                ) {
                    items(
                        items = schedule.dayPrograms,
                        key = { it.id }
                    ) { slotProgram ->
                        Surface(
                            onClick = { onClickProgramToPlay(slotProgram) },
                            shape = RoundedCornerShape(12.dp),
                            color = when {
                                slotProgram.isLiveNow -> Color(0xFF143866)
                                slotProgram.isPrimeTime -> Color(0xFF1B2A4A)
                                else -> Color(0xFF0A192F)
                            },
                            border = BorderStroke(
                                width = if (slotProgram.isLiveNow || slotProgram.isPrimeTime) 2.dp else 1.dp,
                                color = when {
                                    slotProgram.isLiveNow -> TvEmeraldLive
                                    slotProgram.isPrimeTime -> TvGoldenFocus
                                    else -> TvBrightSky.copy(alpha = 0.35f)
                                }
                            ),
                            modifier = Modifier
                                .width(if (isCompact) 210.dp else 240.dp)
                                .wrapContentHeight()
                                .testTag("epg_slot_${slotProgram.id}")
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = slotProgram.formattedTimeRange,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                        color = if (slotProgram.isLiveNow) TvEmeraldLive else TvGoldenFocus,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    IconButton(
                                        onClick = { onShowDetails(slotProgram) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = "Details",
                                            tint = TvBrightSky,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = slotProgram.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (slotProgram.subtitle.isNotBlank()) {
                                    Text(
                                        text = slotProgram.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TvBrightSky,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (slotProgram.isLiveNow) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { slotProgram.progressFraction },
                                        color = TvEmeraldLive,
                                        trackColor = Color(0xFF061224),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
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

@Composable
private fun EpgProgramDetailDialog(
    program: EpgProgramItem,
    schedule: ChannelEpgSchedule?,
    onPlayChannelNow: () -> Unit,
    onPlayChannelFullscreen: () -> Unit,
    onSelectOtherProgramToPlay: (EpgProgramItem) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            val maxDialogHeight = (maxHeight * 0.84f).coerceAtLeast(300.dp)
            val isCompactDialog = maxWidth < 520.dp

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF07152B),
                border = BorderStroke(2.dp, TvElectricCyan),
                shadowElevation = 18.dp,
                modifier = Modifier
                    .fillMaxWidth(if (isCompactDialog) 1f else 0.90f)
                    .heightIn(max = maxDialogHeight)
                    .wrapContentHeight()
                    .testTag("epg_program_detail_dialog")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(if (isCompactDialog) 14.dp else 18.dp)
                ) {
                    // Sticky Header: Logo + Channel + Close Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            ChannelLogoBadge(
                                logoUrl = program.channelLogoUrl,
                                channelName = program.channelName,
                                accentColor = TvElectricCyan,
                                size = if (isCompactDialog) 44.dp else 52.dp,
                                cornerRadius = 10.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${program.channelNumber}. ${program.channelName}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${program.formattedTimeRange} Uhr • ${program.statusBadgeText}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TvGoldenFocus,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, TvBrightSky),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("Schließen", color = Color.White, maxLines = 1, softWrap = false)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Variable, Scrollable Content Body (grows only as tall as needed, scrolls smoothly if long)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (program.title.isNotBlank()) {
                            Text(
                                text = program.title,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                color = Color.White
                            )
                        }
                        val subLine = remember(program.category, program.subtitle) {
                            listOf(program.category.label, program.subtitle.trim())
                                .filter { it.isNotBlank() }
                                .joinToString(" • ")
                        }
                        if (subLine.isNotBlank()) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = subLine,
                                style = MaterialTheme.typography.titleSmall,
                                color = TvBrightSky
                            )
                        }
                        if (program.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = program.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFE2E8F0)
                            )
                        }

                        if (program.isLiveNow) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { program.progressFraction },
                                color = TvEmeraldLive,
                                trackColor = Color(0xFF17335C),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        }

                        if (schedule != null && schedule.dayPrograms.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Tagesprogramm auf ${program.channelName}:",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                                color = TvBrightSky
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                schedule.dayPrograms.forEach { slot ->
                                    Surface(
                                        onClick = { onSelectOtherProgramToPlay(slot) },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (slot.isLiveNow) Color(0xFF163868) else Color(0xFF0D2142),
                                        border = BorderStroke(
                                            1.dp,
                                            if (slot.isLiveNow) TvEmeraldLive else TvBrightSky.copy(alpha = 0.3f)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 7.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "${slot.formattedTimeRange} • ${slot.title}",
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (slot.subtitle.isNotBlank()) {
                                                    Text(
                                                        text = slot.subtitle,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = TvBrightSky,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (slot.isLiveNow) "● LIVE" else "▶ Start",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                                color = if (slot.isLiveNow) TvEmeraldLive else TvElectricCyan,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Sticky Footer Action Buttons (Always visible & reachable!)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onPlayChannelNow,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TvElectricCyan,
                                contentColor = TvOnPrimaryDark
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("epg_dialog_play_now_button")
                        ) {
                            Icon(Icons.Default.LiveTv, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isCompactDialog) "Sender starten" else "Sender jetzt im ExoPlayer starten",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = onPlayChannelFullscreen,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TvGoldenFocus,
                                contentColor = TvOnPrimaryDark
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(44.dp)
                                .testTag("epg_dialog_fullscreen_button")
                        ) {
                            Icon(Icons.Default.Fullscreen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Vollbild",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
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
