package com.example.androidtvlight.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.androidtvlight.domain.model.AmbientLightScene
import com.example.androidtvlight.domain.model.ExoTrackInfo
import com.example.androidtvlight.domain.model.RecordedVideoItem
import com.example.androidtvlight.domain.model.TvChannelItem
import com.example.androidtvlight.domain.model.TvRubrik
import com.example.androidtvlight.domain.model.TvSection
import com.example.androidtvlight.ui.components.AmbientBiasLightCanvas
import com.example.androidtvlight.ui.components.ChannelLogoBadge
import com.example.androidtvlight.ui.components.TvExoPlayerView
import com.example.androidtvlight.ui.components.TvFocusableCard
import com.example.androidtvlight.ui.theme.TvBrightSky
import com.example.androidtvlight.ui.theme.TvCoralAlert
import com.example.androidtvlight.ui.theme.TvElectricCyan
import com.example.androidtvlight.ui.theme.TvEmeraldLive
import com.example.androidtvlight.ui.theme.TvGoldenFocus
import com.example.androidtvlight.ui.theme.TvOnPrimaryDark
import com.example.androidtvlight.ui.viewmodel.RemoteCommand
import com.example.androidtvlight.ui.viewmodel.TvLightUiState

/**
 * Easy-Handling Startseite:
 * Programm starten und sofort fernsehen!
 * Kein überladener Bildschirm – oben läuft direkt der Live-TV ExoPlayer,
 * darunter die großen Rubrik-Schalter (1: Deutsches TV / 2: Österreich TV)
 * und das übersichtliche Sender-Raster mit Original-Logos.
 */
@Composable
fun HomeDashboardContent(
    uiState: TvLightUiState,
    onSelectChannelForPlayer: (TvChannelItem, Boolean) -> Unit,
    onSelectRubrikSection: (TvRubrik) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onOpenAmbientFullscreen: () -> Unit,
    onNavigateSection: (TvSection) -> Unit,
    onToggleHighContrast: () -> Unit,
    onToggleLargeText: () -> Unit,
    onToggleBackgroundPlayback: () -> Unit,
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
            .testTag("home_dashboard_list")
    ) {
        // 1. DIRECT LIVE TV STAGE (Easy Handling: App starten und direkt fernsehen!)
        if (activeChannel != null) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(2.dp, TvElectricCyan),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(235.dp)
            ) {
                TvExoPlayerView(
                    channel = activeChannel,
                    playerState = uiState.playerState,
                    allChannelsInRubrik = uiState.filteredChannels.ifEmpty { uiState.channels },
                    streamServerCount = uiState.activeStreamUrls.size,
                    onSelectChannel = { ch -> onSelectChannelForPlayer(ch, false) },
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
                    onToggleFavorite = { onToggleFavorite(activeChannel.id) },
                    onShowTrackSelector = onShowTrackSelector,
                    onToggleZapperOverlay = onToggleZapperOverlay,
                    onPlayerStatusChanged = onPlayerStatusChanged,
                    onStreamError = onStreamError,
                    onOpenRecordingsDialog = onOpenRecordingsDialog,
                    onOpenEpg = { onNavigateSection(TvSection.EPG) },
                    onReturnToLiveTv = onReturnToLiveTv,
                    onExitApp = onExitApp,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. CLEAN, UNCLUTTERED RUBRIK SWITCHER BAR (1: Deutsches TV | 2: Österreich TV | DE TV2 | TV3 | Alle)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                FilterChip(
                    selected = uiState.selectedRubrik == TvRubrik.DEUTSCHES_TV,
                    onClick = { onSelectRubrikSection(TvRubrik.DEUTSCHES_TV) },
                    label = {
                        Text(
                            text = "1 : Deutsches TV (${uiState.germanChannels.size})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TvElectricCyan,
                        selectedLabelColor = TvOnPrimaryDark,
                        containerColor = Color(0xFF132D54),
                        labelColor = Color.White
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("home_rubrik_de_button")
                )
            }

            item {
                FilterChip(
                    selected = uiState.selectedRubrik == TvRubrik.OESTERREICH_TV,
                    onClick = { onSelectRubrikSection(TvRubrik.OESTERREICH_TV) },
                    label = {
                        Text(
                            text = "2 : Österreich TV (${uiState.austrianChannels.size})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TvEmeraldLive,
                        selectedLabelColor = TvOnPrimaryDark,
                        containerColor = Color(0xFF132D54),
                        labelColor = Color.White
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("home_rubrik_at_button")
                )
            }

            item {
                FilterChip(
                    selected = uiState.selectedRubrik == TvRubrik.TV2_OHA,
                    onClick = { onSelectRubrikSection(TvRubrik.TV2_OHA) },
                    label = {
                        Text(
                            text = "DE TV2 (${uiState.tv2Channels.size})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
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
                        .height(48.dp)
                        .testTag("home_rubrik_tv2_button")
                )
            }

            item {
                FilterChip(
                    selected = uiState.selectedRubrik == TvRubrik.TV3_LIVE,
                    onClick = { onSelectRubrikSection(TvRubrik.TV3_LIVE) },
                    label = {
                        Text(
                            text = "TV3 (${uiState.tv3Channels.size})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TvEmeraldLive,
                        selectedLabelColor = TvOnPrimaryDark,
                        containerColor = Color(0xFF132D54),
                        labelColor = Color.White
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("home_rubrik_tv3_button")
                )
            }

            item {
                FilterChip(
                    selected = uiState.selectedRubrik == TvRubrik.ALL,
                    onClick = { onSelectRubrikSection(TvRubrik.ALL) },
                    label = {
                        Text(
                            text = "Alle (${uiState.channels.size})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
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
                        .height(48.dp)
                        .testTag("home_rubrik_all_button")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. EASY-HANDLING CHANNEL GRID: 1 Tap / OK starts channel immediately
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 165.dp),
            contentPadding = PaddingValues(bottom = 88.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            itemsIndexed(
                items = uiState.filteredChannels,
                key = { _, ch -> ch.id }
            ) { index, channel ->
                val isPlayingChannel = channel.id == activeChannel?.id
                EasyChannelTileCard(
                    channel = channel,
                    isActive = isPlayingChannel || index == uiState.focusedItemIndex,
                    isCurrentlyPlaying = isPlayingChannel,
                    onClick = { onSelectChannelForPlayer(channel, false) },
                    onFullscreenClick = { onSelectChannelForPlayer(channel, true) }
                )
            }
        }
    }
}

/**
 * Clean, distraction-free channel tile with large original station logo and 1-click play.
 */
@Composable
private fun EasyChannelTileCard(
    channel: TvChannelItem,
    isActive: Boolean,
    isCurrentlyPlaying: Boolean,
    onClick: () -> Unit,
    onFullscreenClick: () -> Unit
) {
    TvFocusableCard(
        isSelectedOrFocused = isActive,
        onClick = onClick,
        testTagName = "easy_channel_${channel.id}",
        accentColor = if (isCurrentlyPlaying) TvGoldenFocus else Color(channel.accentHex),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    color = if (isCurrentlyPlaying) TvEmeraldLive else Color(0xFF0A192F),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = if (isCurrentlyPlaying) "LIVE" else "#${channel.number}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = if (isCurrentlyPlaying) TvOnPrimaryDark else TvBrightSky,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                ChannelLogoBadge(
                    logoUrl = channel.logoUrl,
                    channelName = channel.name,
                    accentColor = Color(channel.accentHex),
                    size = 66.dp,
                    modifier = Modifier.align(Alignment.Center)
                )

                IconButton(
                    onClick = onFullscreenClick,
                    modifier = Modifier
                        .size(36.dp)
                        .align(Alignment.TopEnd)
                        .testTag("easy_fullscreen_${channel.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "Vollbild starten",
                        tint = TvGoldenFocus,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = channel.rubrik.flagBadge,
                style = MaterialTheme.typography.labelMedium,
                color = TvBrightSky,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun CompactChannelLogoCard(
    channel: TvChannelItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    TvFocusableCard(
        isSelectedOrFocused = isSelected,
        onClick = onClick,
        testTagName = "compact_channel_${channel.id}",
        accentColor = Color(channel.accentHex),
        modifier = Modifier.width(156.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ChannelLogoBadge(
                logoUrl = channel.logoUrl,
                channelName = channel.name,
                accentColor = Color(channel.accentHex),
                size = 64.dp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "#${channel.number} • ${channel.rubrik.flagBadge}",
                style = MaterialTheme.typography.labelMedium,
                color = TvBrightSky,
                maxLines = 1
            )
        }
    }
}

/**
 * Dedicated Rubrik Grid Screen for "1: Deutsches TV" and "2: Österreich TV"
 */
@Composable
fun RubrikChannelsScreen(
    rubrik: TvRubrik,
    uiState: TvLightUiState,
    onSearchQueryChange: (String) -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onSwitchRubrik: (TvRubrik) -> Unit,
    onPlayChannel: (TvChannelItem, Boolean) -> Unit,
    onToggleFavorite: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("rubrik_channels_screen_${rubrik.id}")
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                FilterChip(
                    selected = rubrik == TvRubrik.DEUTSCHES_TV,
                    onClick = { onSwitchRubrik(TvRubrik.DEUTSCHES_TV) },
                    label = {
                        Text(
                            text = "1: Deutsches TV (${uiState.germanChannels.size})",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TvElectricCyan,
                        selectedLabelColor = TvOnPrimaryDark,
                        containerColor = Color(0xFF132D54),
                        labelColor = Color.White
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("chip_rubrik_de")
                )
            }

            item {
                FilterChip(
                    selected = rubrik == TvRubrik.OESTERREICH_TV,
                    onClick = { onSwitchRubrik(TvRubrik.OESTERREICH_TV) },
                    label = {
                        Text(
                            text = "2: Österreich TV (${uiState.austrianChannels.size})",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TvEmeraldLive,
                        selectedLabelColor = TvOnPrimaryDark,
                        containerColor = Color(0xFF132D54),
                        labelColor = Color.White
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("chip_rubrik_at")
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
                        containerColor = Color(0xFF132D54),
                        labelColor = Color.White
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("chip_favorites_only")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Sender suchen...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
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
                unfocusedBorderColor = TvBrightSky.copy(alpha = 0.5f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("channel_search_input")
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 230.dp),
            contentPadding = PaddingValues(bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(
                items = uiState.filteredChannels,
                key = { _, item -> item.id }
            ) { index, channel ->
                val isSelected = index == uiState.focusedItemIndex || channel.id == uiState.activeChannel?.id
                ChannelGridCardWithLogo(
                    channel = channel,
                    isFocused = isSelected,
                    onPlayInExoPlayer = { onPlayChannel(channel, false) },
                    onPlayFullscreen = { onPlayChannel(channel, true) },
                    onToggleFavorite = { onToggleFavorite(channel.id) }
                )
            }
        }
    }
}

@Composable
fun ChannelGridCardWithLogo(
    channel: TvChannelItem,
    isFocused: Boolean,
    onPlayInExoPlayer: () -> Unit,
    onPlayFullscreen: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    TvFocusableCard(
        isSelectedOrFocused = isFocused,
        onClick = onPlayInExoPlayer,
        testTagName = "channel_card_${channel.id}",
        accentColor = Color(channel.accentHex),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    ChannelLogoBadge(
                        logoUrl = channel.logoUrl,
                        channelName = channel.name,
                        accentColor = Color(channel.accentHex),
                        size = 58.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "#${channel.number} • ${channel.qualityBadge}",
                            style = MaterialTheme.typography.labelMedium,
                            color = TvBrightSky
                        )
                        Text(
                            text = channel.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("fav_button_${channel.id}")
                ) {
                    Icon(
                        imageVector = if (channel.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favorit umschalten",
                        tint = if (channel.isFavorite) TvGoldenFocus else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPlayInExoPlayer,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(channel.accentHex),
                        contentColor = TvOnPrimaryDark
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("play_button_${channel.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Abspielen", style = MaterialTheme.typography.labelLarge)
                }

                IconButton(
                    onClick = onPlayFullscreen,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("fullscreen_button_${channel.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "Im Vollbild starten",
                        tint = TvGoldenFocus
                    )
                }
            }
        }
    }
}

@Composable
fun ExoPlayerLiveScreen(
    uiState: TvLightUiState,
    onSelectChannel: (TvChannelItem) -> Unit,
    onStepChannel: (Int) -> Unit,
    onSelectRubrik: (TvRubrik) -> Unit,
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
    onToggleFavorite: (String) -> Unit,
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
    val activeChannel = uiState.activeChannel ?: uiState.channels.firstOrNull() ?: return

    if (uiState.playerState.isFullscreenPlayer) {
        TvExoPlayerView(
            channel = activeChannel,
            playerState = uiState.playerState,
            allChannelsInRubrik = uiState.filteredChannels.ifEmpty { uiState.channels },
            streamServerCount = uiState.activeStreamUrls.size,
            onSelectChannel = onSelectChannel,
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
            onToggleFavorite = { onToggleFavorite(activeChannel.id) },
            onShowTrackSelector = onShowTrackSelector,
            onToggleZapperOverlay = onToggleZapperOverlay,
            onPlayerStatusChanged = onPlayerStatusChanged,
            onStreamError = onStreamError,
            onOpenRecordingsDialog = onOpenRecordingsDialog,
            onReturnToLiveTv = onReturnToLiveTv,
            onExitApp = onExitApp,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .testTag("exoplayer_live_screen")
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(2.dp, TvElectricCyan),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(235.dp)
            ) {
                TvExoPlayerView(
                    channel = activeChannel,
                    playerState = uiState.playerState,
                    allChannelsInRubrik = uiState.filteredChannels.ifEmpty { uiState.channels },
                    streamServerCount = uiState.activeStreamUrls.size,
                    onSelectChannel = onSelectChannel,
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
                    onToggleFavorite = { onToggleFavorite(activeChannel.id) },
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

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    FilterChip(
                        selected = uiState.selectedRubrik == TvRubrik.DEUTSCHES_TV,
                        onClick = { onSelectRubrik(TvRubrik.DEUTSCHES_TV) },
                        label = {
                            Text(
                                text = "1: Deutsches TV (${uiState.germanChannels.size})",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TvElectricCyan,
                            selectedLabelColor = TvOnPrimaryDark,
                            containerColor = Color(0xFF132D54),
                            labelColor = Color.White
                        ),
                        modifier = Modifier
                            .height(46.dp)
                            .testTag("player_rubrik_de_chip")
                    )
                }

                item {
                    FilterChip(
                        selected = uiState.selectedRubrik == TvRubrik.OESTERREICH_TV,
                        onClick = { onSelectRubrik(TvRubrik.OESTERREICH_TV) },
                        label = {
                            Text(
                                text = "2: Österreich TV (${uiState.austrianChannels.size})",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TvEmeraldLive,
                            selectedLabelColor = TvOnPrimaryDark,
                            containerColor = Color(0xFF132D54),
                            labelColor = Color.White
                        ),
                        modifier = Modifier
                            .height(46.dp)
                            .testTag("player_rubrik_at_chip")
                    )
                }

                item {
                    FilterChip(
                        selected = uiState.selectedRubrik == TvRubrik.TV2_OHA,
                        onClick = { onSelectRubrik(TvRubrik.TV2_OHA) },
                        label = {
                            Text(
                                text = "DE TV2 (${uiState.tv2Channels.size})",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
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
                            .height(46.dp)
                            .testTag("player_rubrik_tv2_chip")
                    )
                }

                item {
                    FilterChip(
                        selected = uiState.selectedRubrik == TvRubrik.TV3_LIVE,
                        onClick = { onSelectRubrik(TvRubrik.TV3_LIVE) },
                        label = {
                            Text(
                                text = "TV3 (${uiState.tv3Channels.size})",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TvEmeraldLive,
                            selectedLabelColor = TvOnPrimaryDark,
                            containerColor = Color(0xFF132D54),
                            labelColor = Color.White
                        ),
                        modifier = Modifier
                            .height(46.dp)
                            .testTag("player_rubrik_tv3_chip")
                    )
                }

                item {
                    FilterChip(
                        selected = uiState.selectedRubrik == TvRubrik.ALL,
                        onClick = { onSelectRubrik(TvRubrik.ALL) },
                        label = {
                            Text(
                                text = "Alle (${uiState.channels.size})",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
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
                            .height(46.dp)
                            .testTag("player_rubrik_all_chip")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 165.dp),
                contentPadding = PaddingValues(bottom = 88.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("player_channel_grid")
            ) {
                itemsIndexed(
                    items = uiState.filteredChannels,
                    key = { _, ch -> ch.id }
                ) { index, ch ->
                    val isPlayingChannel = ch.id == activeChannel.id
                    EasyChannelTileCard(
                        channel = ch,
                        isActive = isPlayingChannel || index == uiState.focusedItemIndex,
                        isCurrentlyPlaying = isPlayingChannel,
                        onClick = { onSelectChannel(ch) },
                        onFullscreenClick = {
                            onSelectChannel(ch)
                            onToggleFullscreen()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AmbientLightSectionContent(
    uiState: TvLightUiState,
    onSelectScene: (AmbientLightScene) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onWarmFilterChange: (Float) -> Unit,
    onTogglePulse: () -> Unit,
    onCycleSleepTimer: () -> Unit,
    onStartFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeScene = uiState.activeAmbientScene ?: uiState.ambientScenes.firstOrNull()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("ambient_light_section"),
        contentPadding = PaddingValues(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Android TV Light • Bias-Beleuchtung",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Verwandelt Fernseher oder Tablet in ein augenschonendes Hintergrundlicht mit tiefblauen Kontrasten.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = onStartFullscreen,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TvGoldenFocus,
                        contentColor = TvOnPrimaryDark
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("start_fullscreen_ambient_button")
                ) {
                    Icon(Icons.Default.Fullscreen, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Vollbild starten", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        if (activeScene != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(2.dp, TvElectricCyan),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AmbientBiasLightCanvas(
                            scene = activeScene,
                            brightness = uiState.settings.ambientBrightness,
                            dynamicPulse = uiState.settings.ambientDynamicPulse,
                            warmFilter = uiState.settings.warmEyeCareFilter
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Surface(
                                color = Color(0xFF041022).copy(alpha = 0.85f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, TvBrightSky)
                            ) {
                                Text(
                                    text = "AKTIVE SZENE: ${activeScene.kelvinLabel}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TvBrightSky,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = activeScene.name,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White
                                )
                                Text(
                                    text = activeScene.subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFE2E8F0)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WbSunny, contentDescription = null, tint = TvElectricCyan)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Lichtstärke: ${(uiState.settings.ambientBrightness * 100).toInt()}%",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                        }
                    }

                    Slider(
                        value = uiState.settings.ambientBrightness,
                        onValueChange = onBrightnessChange,
                        valueRange = 0.15f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = TvGoldenFocus,
                            activeTrackColor = TvElectricCyan
                        ),
                        modifier = Modifier.testTag("ambient_brightness_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TvFocusableCard(
                            isSelectedOrFocused = uiState.settings.ambientDynamicPulse,
                            onClick = onTogglePulse,
                            testTagName = "toggle_ambient_pulse_card",
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = TvElectricCyan)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Dynamische Welle", style = MaterialTheme.typography.titleMedium, color = Color.White)
                                    Text(
                                        if (uiState.settings.ambientDynamicPulse) "Pulsieren AN" else "Statisch",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        TvFocusableCard(
                            isSelectedOrFocused = uiState.settings.sleepTimerMinutes > 0,
                            onClick = onCycleSleepTimer,
                            testTagName = "cycle_sleep_timer_card",
                            accentColor = TvGoldenFocus,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Bedtime, contentDescription = null, tint = TvGoldenFocus)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Schlummer-Timer", style = MaterialTheme.typography.titleMedium, color = Color.White)
                                    Text(
                                        if (uiState.settings.sleepTimerMinutes == 0) "Aus" else "${uiState.settings.sleepTimerMinutes} Min.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        itemsIndexed(
            items = uiState.ambientScenes,
            key = { _, item -> item.id }
        ) { index, scene ->
            val isSelected = scene.id == activeScene?.id || index == uiState.focusedItemIndex
            TvFocusableCard(
                isSelectedOrFocused = isSelected,
                onClick = { onSelectScene(scene) },
                testTagName = "ambient_scene_card_${scene.id}",
                accentColor = scene.tertiaryColor,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(scene.primaryColor, scene.secondaryColor, scene.tertiaryColor)
                                    )
                                )
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(scene.name, style = MaterialTheme.typography.titleLarge, color = Color.White)
                            Text(scene.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RemoteAndSettingsSectionContent(
    uiState: TvLightUiState,
    isTelevisionDevice: Boolean,
    hasStoragePermission: Boolean,
    onRequestStoragePermission: () -> Unit,
    onToggleRecording: () -> Unit,
    onTogglePreventStandby: () -> Unit,
    onToggleBackgroundPlayback: () -> Unit,
    onToggleHighContrast: () -> Unit,
    onToggleLargeText: () -> Unit,
    onToggleVirtualRemote: () -> Unit,
    onCommand: (RemoteCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("remote_settings_section"),
        contentPadding = PaddingValues(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Aufnahme (MP4), Standby-Schutz, Premium & Fernbedienung",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Live-Aufnahme in Download/MaDIPTV, Speicherberechtigung, Standby-Sperre & Hintergrundwiedergabe.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // MP4 RECORDING & STORAGE PERMISSION CARD (Download/MaDIPTV)
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    width = 2.dp,
                    color = if (uiState.playerState.isRecordingMp4) Color(0xFFFF4D6D) else TvGoldenFocus
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setting_mp4_recording_card")
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (uiState.playerState.isRecordingMp4) {
                            "● AUFNAHME LÄUFT: ${uiState.activeChannel?.name ?: "Live-TV"} (.MP4)"
                        } else {
                            "MP4 Video-Aufnahme • Zielordner: Download/MaDIPTV"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Text(
                        text = if (uiState.playerState.isRecordingMp4) {
                            "Aktive Datei: ${uiState.playerState.recordingTargetPath} (${uiState.playerState.recordingElapsedSeconds}s)"
                        } else {
                            "Speichert den aktuellen Live-TV-Kanal im MP4-Format direkt im Ordner Download/MaDIPTV. Speicherberechtigung: ${if (hasStoragePermission) "Erteilt ✓" else "Erforderlich"}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TvBrightSky
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onToggleRecording,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.playerState.isRecordingMp4) Color(0xFFFF4D6D) else TvElectricCyan,
                                contentColor = if (uiState.playerState.isRecordingMp4) Color.White else TvOnPrimaryDark
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("settings_toggle_mp4_rec_button")
                        ) {
                            Text(
                                text = if (uiState.playerState.isRecordingMp4) "■ Aufnahme stoppen & speichern" else "● MP4 Aufnahme starten",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        if (!hasStoragePermission) {
                            Button(
                                onClick = onRequestStoragePermission,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TvGoldenFocus,
                                    contentColor = TvOnPrimaryDark
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("settings_grant_storage_permission_button")
                            ) {
                                Text("Speicher-Berechtigung setzen", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }

        // PREVENT STANDBY (KEIN STANDBY / WAKE-LOCK AKTIV)
        item {
            SettingToggleRowCard(
                title = "Standby verhindern (App & Bildschirm nie in Standby versetzen)",
                subtitle = "Hält Bildschirm, CPU-WakeLock und WLAN-Stream dauerhaft aktiv – verhindert automatisches Abschalten beim Fernsehen und bei MP4-Aufnahmen.",
                checked = uiState.settings.preventStandbyEnabled,
                onToggle = onTogglePreventStandby,
                tag = "setting_prevent_standby_switch"
            )
        }

        // PREMIUM FEATURE 1: BACKGROUND PLAYBACK
        item {
            SettingToggleRowCard(
                title = "★ PREMIUM: Hintergrundwiedergabe (Audio & Live-Stream weiterlaufen lassen)",
                subtitle = "Spielt den TV-Ton nahtlos im Hintergrund weiter, auch wenn die App minimiert oder das Display ausgeschaltet wird.",
                checked = uiState.settings.backgroundPlaybackEnabled,
                onToggle = onToggleBackgroundPlayback,
                tag = "setting_background_playback_switch"
            )
        }

        item {
            SettingToggleRowCard(
                title = "Ultra-Kontrast Modus (Dunkelblau / Weiß / Gold)",
                subtitle = "Erhöht den Text- und Rahmenkontrast auf das Maximum für beste Lesbarkeit.",
                checked = uiState.settings.highContrastMode,
                onToggle = onToggleHighContrast,
                tag = "setting_high_contrast_switch"
            )
        }

        item {
            SettingToggleRowCard(
                title = "Große TV-Schrift (10-Foot Lesbarkeits-Skalierung)",
                subtitle = "Vergrößert alle Überschriften, Sendernamen und Bedienelemente um 16%.",
                checked = uiState.settings.largeTextMode,
                onToggle = onToggleLargeText,
                tag = "setting_large_text_switch"
            )
        }

        item {
            SettingToggleRowCard(
                title = "Virtuelles TV-Steuerkreuz auf dem Bildschirm anzeigen",
                subtitle = "Blendet bei Bedarf ein interaktives D-Pad für Touch-Bedienung ein (standardmäßig ausgeblendet für freie Sicht).",
                checked = uiState.settings.showVirtualRemoteOverlay,
                onToggle = onToggleVirtualRemote,
                tag = "setting_virtual_remote_switch"
            )
        }

        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.5.dp, TvElectricCyan),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF0A192F),
                            border = BorderStroke(2.dp, TvGoldenFocus),
                            modifier = Modifier.size(50.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isTelevisionDevice) Icons.Default.LiveTv else Icons.Default.TouchApp,
                                    contentDescription = null,
                                    tint = TvGoldenFocus,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = if (isTelevisionDevice) "Android TV / Leanback" else "Android Touch + Dreh-Vollbild aktiv",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(
                                text = "Letzte Aktion: ${uiState.lastInputSourceLabel}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TvBrightSky
                            )
                        }
                    }

                    Button(
                        onClick = { onCommand(RemoteCommand.CHANNEL_UP) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TvElectricCyan,
                            contentColor = TvOnPrimaryDark
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("test_remote_ch_plus_button")
                    ) {
                        Text("Kanal +", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRowCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit,
    tag: String
) {
    TvFocusableCard(
        isSelectedOrFocused = checked,
        onClick = onToggle,
        testTagName = tag,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
                Spacer(modifier = Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Switch(
                checked = checked,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = TvOnPrimaryDark,
                    checkedTrackColor = TvElectricCyan
                )
            )
        }
    }
}

/**
 * Aufnahmen-Dialog (wird über den Menüeintrag "Aufnahmen" unten im Menü sowie im Player geöffnet):
 * - Zeigt alle gespeicherten MP4-Aufnahmen aus Download/MaDIPTV an.
 * - Aufnahmen können direkt zur Wiedergabe im internen ExoPlayer ausgewählt werden.
 * - Aufnahmen können direkt gelöscht werden.
 */
@Composable
fun RecordingsDialog(
    recordings: List<RecordedVideoItem>,
    activeRecording: RecordedVideoItem?,
    isRecordingActive: Boolean,
    recordingTargetPath: String,
    recordingElapsedSeconds: Long,
    onPlayRecording: (RecordedVideoItem, Boolean) -> Unit,
    onDeleteRecording: (RecordedVideoItem) -> Unit,
    onToggleRecording: () -> Unit,
    onReturnToLiveTv: () -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF08172E),
            border = BorderStroke(2.dp, TvElectricCyan),
            shadowElevation = 20.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .height(560.dp)
                .testTag("recordings_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Dialog Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF132D54),
                            border = BorderStroke(1.5.dp, TvGoldenFocus),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.VideoLibrary,
                                    contentDescription = null,
                                    tint = TvGoldenFocus,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Aufnahmen (${recordings.size})",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = Color.White
                            )
                            Text(
                                text = "Ordner: Download/MaDIPTV • Wiedergabe im internen ExoPlayer",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TvBrightSky
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("recordings_dialog_refresh_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Aufnahmen aktualisieren",
                                tint = TvElectricCyan
                            )
                        }

                        OutlinedButton(
                            onClick = onDismiss,
                            border = BorderStroke(1.5.dp, TvBrightSky),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(42.dp)
                                .testTag("recordings_dialog_close_button")
                        ) {
                            Text("Schließen", color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Active Recording or Live-TV Switch Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onToggleRecording,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecordingActive) TvCoralAlert else Color(0xFF15315B),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("recordings_dialog_toggle_rec_button")
                    ) {
                        Text(
                            text = if (isRecordingActive) {
                                "■ Laufende Aufnahme stoppen (${recordingElapsedSeconds}s)"
                            } else {
                                "● Neue Live-TV Aufnahme starten"
                            },
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    if (activeRecording != null) {
                        Button(
                            onClick = {
                                onReturnToLiveTv()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TvEmeraldLive,
                                contentColor = TvOnPrimaryDark
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(44.dp)
                                .testTag("recordings_dialog_back_to_live_button")
                        ) {
                            Icon(Icons.Default.LiveTv, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Zurück zu Live-TV",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold)
                            )
                        }
                    }
                }

                if (isRecordingActive) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Aktuelle Aufnahme wird gespeichert in: $recordingTargetPath",
                        style = MaterialTheme.typography.labelMedium,
                        color = TvGoldenFocus
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Recordings List or Empty State
                if (recordings.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF0D2142),
                        border = BorderStroke(1.dp, TvBrightSky.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("recordings_empty_state")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideoLibrary,
                                contentDescription = null,
                                tint = TvBrightSky,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Noch keine Aufnahmen in Download/MaDIPTV vorhanden",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Starte eine Aufnahme über 'REC MP4', um Live-TV im MP4-Format aufzunehmen und hier direkt im internen ExoPlayer abzuspielen.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TvBrightSky
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("recordings_dialog_list")
                    ) {
                        itemsIndexed(
                            items = recordings,
                            key = { _, item -> item.id }
                        ) { _, recording ->
                            val isPlayingThis = activeRecording?.id == recording.id ||
                                activeRecording?.fileName == recording.fileName
                            RecordingListItemCard(
                                recording = recording,
                                isCurrentlyPlaying = isPlayingThis,
                                onPlay = { onPlayRecording(recording, false) },
                                onPlayFullscreen = { onPlayRecording(recording, true) },
                                onDelete = { onDeleteRecording(recording) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dedicated Aufnahmen Section Screen (shown when "Aufnahmen" is selected in bottom menu):
 * Embeds the internal ExoPlayer at the top + full list of recordings with Play & Delete actions below,
 * and a button to open the Aufnahmen-Dialog at any time.
 */
@Composable
fun RecordingsSectionContent(
    uiState: TvLightUiState,
    onPlayRecording: (RecordedVideoItem, Boolean) -> Unit,
    onDeleteRecording: (RecordedVideoItem) -> Unit,
    onOpenRecordingsDialog: () -> Unit,
    onReturnToLiveTv: () -> Unit,
    onRefreshRecordings: () -> Unit,
    onSelectChannel: (TvChannelItem) -> Unit,
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
    onToggleFavorite: (String) -> Unit,
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
    onExitApp: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val activeChannel = uiState.activeChannel ?: uiState.channels.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("recordings_section_screen")
    ) {
        // Internal ExoPlayer Stage at the top so selected recordings play right here!
        if (activeChannel != null) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(2.dp, TvElectricCyan),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(235.dp)
            ) {
                TvExoPlayerView(
                    channel = activeChannel,
                    playerState = uiState.playerState,
                    allChannelsInRubrik = uiState.filteredChannels.ifEmpty { uiState.channels },
                    streamServerCount = uiState.activeStreamUrls.size,
                    onSelectChannel = onSelectChannel,
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
                    onToggleFavorite = { onToggleFavorite(activeChannel.id) },
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

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Aufnahmen (${uiState.recordings.size}) • Download/MaDIPTV",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.White
                )
                Text(
                    text = if (uiState.playerState.activeRecording != null) {
                        "Spielt Aufnahme im ExoPlayer: ${uiState.playerState.activeRecording.fileName}"
                    } else {
                        "Aufnahme auswählen zum Abspielen im internen ExoPlayer oder Löschen"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (uiState.playerState.activeRecording != null) TvGoldenFocus else TvBrightSky,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (uiState.playerState.activeRecording != null) {
                    Button(
                        onClick = onReturnToLiveTv,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TvEmeraldLive,
                            contentColor = TvOnPrimaryDark
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(42.dp)
                            .testTag("section_return_to_live_button")
                    ) {
                        Icon(Icons.Default.LiveTv, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Live-TV", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    }
                }

                Button(
                    onClick = onOpenRecordingsDialog,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TvElectricCyan,
                        contentColor = TvOnPrimaryDark
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(42.dp)
                        .testTag("section_open_recordings_dialog_button")
                ) {
                    Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Aufnahmen-Dialog", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }

                IconButton(
                    onClick = onRefreshRecordings,
                    modifier = Modifier
                        .size(42.dp)
                        .testTag("section_refresh_recordings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Aufnahmen aktualisieren",
                        tint = TvElectricCyan
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (uiState.recordings.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF0D2142),
                border = BorderStroke(1.dp, TvBrightSky.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = null,
                        tint = TvBrightSky,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Keine gespeicherten Aufnahmen in Download/MaDIPTV",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = onToggleRecording,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TvCoralAlert,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (uiState.playerState.isRecordingMp4) {
                                "■ Laufende Aufnahme stoppen & speichern"
                            } else {
                                "● Jetzt Live-TV Aufnahme (MP4) starten"
                            }
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 88.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                itemsIndexed(
                    items = uiState.recordings,
                    key = { _, rec -> rec.id }
                ) { index, recording ->
                    val isPlayingThis = uiState.playerState.activeRecording?.id == recording.id ||
                        uiState.playerState.activeRecording?.fileName == recording.fileName
                    RecordingListItemCard(
                        recording = recording,
                        isCurrentlyPlaying = isPlayingThis || index == uiState.focusedItemIndex,
                        onPlay = { onPlayRecording(recording, false) },
                        onPlayFullscreen = { onPlayRecording(recording, true) },
                        onDelete = { onDeleteRecording(recording) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordingListItemCard(
    recording: RecordedVideoItem,
    isCurrentlyPlaying: Boolean,
    onPlay: () -> Unit,
    onPlayFullscreen: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        onClick = onPlay,
        shape = RoundedCornerShape(16.dp),
        color = if (isCurrentlyPlaying) Color(0xFF163564) else Color(0xFF0D2142),
        border = BorderStroke(
            width = if (isCurrentlyPlaying) 2.dp else 1.dp,
            color = if (isCurrentlyPlaying) TvGoldenFocus else TvBrightSky.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recording_item_${recording.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isCurrentlyPlaying) TvGoldenFocus else Color(0xFF132D54),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = if (isCurrentlyPlaying) TvOnPrimaryDark else TvElectricCyan,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isCurrentlyPlaying) {
                            Surface(
                                color = TvGoldenFocus,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "▶ LÄUFT IM PLAYER",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = TvOnPrimaryDark,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = recording.channelTitle,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = "${recording.formattedDate} • ${recording.formattedSize}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TvBrightSky,
                        maxLines = 1
                    )
                    Text(
                        text = recording.displayPath,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onPlay,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TvElectricCyan,
                        contentColor = TvOnPrimaryDark
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .height(42.dp)
                        .testTag("play_recording_${recording.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Abspielen",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold)
                    )
                }

                IconButton(
                    onClick = onPlayFullscreen,
                    modifier = Modifier
                        .size(42.dp)
                        .testTag("fullscreen_recording_${recording.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "Aufnahme im Vollbild abspielen",
                        tint = TvGoldenFocus
                    )
                }

                Surface(
                    onClick = onDelete,
                    shape = RoundedCornerShape(10.dp),
                    color = TvCoralAlert.copy(alpha = 0.2f),
                    border = BorderStroke(1.5.dp, TvCoralAlert),
                    modifier = Modifier
                        .height(42.dp)
                        .testTag("delete_recording_${recording.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Aufnahme löschen",
                            tint = TvCoralAlert,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Löschen",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * DE TV2 Screen:
 * - Opened via the bottom menu entry "DE TV2".
 * - Displays "DE TV2" and the number of available channels (without any oha.to/mediaurl.json notice).
 * - Streams all channels directly inside the internal ExoPlayer.
 */
@Composable
fun Tv2OhaSectionContent(
    uiState: TvLightUiState,
    onSelectChannelForPlayer: (TvChannelItem, Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onRefreshTv2Feed: () -> Unit,
    onToggleFavorite: (String) -> Unit,
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
    val activeChannel = uiState.activeChannel ?: uiState.tv2Channels.firstOrNull() ?: uiState.channels.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("tv2_oha_section_screen")
    ) {
        // 1. INTERNAL EXOPLAYER AT THE TOP
        if (activeChannel != null) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(2.dp, TvGoldenFocus),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(235.dp)
            ) {
                TvExoPlayerView(
                    channel = activeChannel,
                    playerState = uiState.playerState,
                    allChannelsInRubrik = uiState.filteredChannels.ifEmpty { uiState.tv2Channels },
                    streamServerCount = uiState.activeStreamUrls.size,
                    onSelectChannel = { ch -> onSelectChannelForPlayer(ch, false) },
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
                    onToggleFavorite = { onToggleFavorite(activeChannel.id) },
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

        Spacer(modifier = Modifier.height(10.dp))

        // 2. DE TV2 HEADER WITH AVAILABLE CHANNEL COUNT & SEARCH BAR
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color(0xFF132D54),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.5.dp, TvGoldenFocus),
                modifier = Modifier.testTag("tv2_header_channel_count_badge")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LiveTv,
                        contentDescription = null,
                        tint = TvGoldenFocus,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (uiState.isLoadingTv2Channels) {
                            "DE TV2 (${uiState.tv2Channels.size} Sender • lädt...)"
                        } else {
                            "DE TV2 (${uiState.tv2Channels.size} Sender)"
                        },
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = Color.White
                    )
                }
            }

            FilterChip(
                selected = uiState.favoritesOnly,
                onClick = onToggleFavoritesOnly,
                label = {
                    Text(
                        text = "★ Favoriten",
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = TvGoldenFocus,
                    selectedLabelColor = TvOnPrimaryDark,
                    containerColor = Color(0xFF132D54),
                    labelColor = Color.White
                ),
                modifier = Modifier
                    .height(46.dp)
                    .testTag("tv2_chip_favorites_only")
            )

            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("DE TV2 Sender suchen...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
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
                    unfocusedBorderColor = TvBrightSky.copy(alpha = 0.5f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("tv2_search_input")
            )

            IconButton(
                onClick = onRefreshTv2Feed,
                modifier = Modifier
                    .size(46.dp)
                    .testTag("tv2_refresh_feed_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "DE TV2 Senderliste aktualisieren",
                    tint = TvElectricCyan
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. DE TV2 STREAM GRID: 1 Tap / OK streams immediately in the internal ExoPlayer
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 165.dp),
            contentPadding = PaddingValues(bottom = 88.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("tv2_channels_grid")
        ) {
            itemsIndexed(
                items = uiState.filteredChannels,
                key = { _, ch -> ch.id }
            ) { index, channel ->
                val isPlayingChannel = channel.id == activeChannel?.id
                EasyChannelTileCard(
                    channel = channel,
                    isActive = isPlayingChannel || index == uiState.focusedItemIndex,
                    isCurrentlyPlaying = isPlayingChannel,
                    onClick = { onSelectChannelForPlayer(channel, false) },
                    onFullscreenClick = { onSelectChannelForPlayer(channel, true) }
                )
            }
        }
    }
}

/**
 * TV3 Screen:
 * - Opened via the bottom menu entry "TV3".
 * - Provides 531 German TV channels directly inside the internal ExoPlayer with zero URL hint.
 */
@Composable
fun Tv3LiveSectionContent(
    uiState: TvLightUiState,
    onSelectChannelForPlayer: (TvChannelItem, Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onRefreshTv3Feed: () -> Unit,
    onToggleFavorite: (String) -> Unit,
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
    val activeChannel = uiState.activeChannel ?: uiState.tv3Channels.firstOrNull() ?: uiState.channels.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("tv3_live_section_screen")
    ) {
        // 1. INTERNAL EXOPLAYER AT THE TOP
        if (activeChannel != null) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(2.dp, TvEmeraldLive),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(225.dp)
            ) {
                TvExoPlayerView(
                    channel = activeChannel,
                    playerState = uiState.playerState,
                    allChannelsInRubrik = uiState.filteredChannels.ifEmpty { uiState.tv3Channels },
                    streamServerCount = uiState.activeStreamUrls.size,
                    onSelectChannel = { ch -> onSelectChannelForPlayer(ch, false) },
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
                    onToggleFavorite = { onToggleFavorite(activeChannel.id) },
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

        Spacer(modifier = Modifier.height(10.dp))

        // 2. TV3 HEADER & SEARCH BAR (ohne URL-Hinweis)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color(0xFF132D54),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.5.dp, TvEmeraldLive),
                modifier = Modifier.testTag("tv3_header_channel_count_badge")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LiveTv,
                        contentDescription = null,
                        tint = TvEmeraldLive,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (uiState.isLoadingTv3Channels) {
                            "TV3 (${uiState.tv3Channels.size} Sender • Sync)"
                        } else {
                            "TV3 (${uiState.tv3Channels.size} Sender)"
                        },
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            FilterChip(
                selected = uiState.favoritesOnly,
                onClick = onToggleFavoritesOnly,
                label = {
                    Text(
                        text = "★ Favoriten",
                        style = MaterialTheme.typography.labelLarge,
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
                    .height(46.dp)
                    .testTag("tv3_chip_favorites_only")
            )

            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        text = "TV3 Sender suchen (${uiState.tv3Channels.size} Kanäle)...",
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
                    focusedBorderColor = TvEmeraldLive,
                    unfocusedBorderColor = TvBrightSky.copy(alpha = 0.5f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("tv3_search_input")
            )

            IconButton(
                onClick = onRefreshTv3Feed,
                modifier = Modifier
                    .size(46.dp)
                    .testTag("tv3_refresh_feed_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "TV3 Senderliste aktualisieren",
                    tint = TvEmeraldLive
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. TV3 CHANNEL GRID: 1 Tap / OK starts channel immediately in the internal ExoPlayer
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 165.dp),
            contentPadding = PaddingValues(bottom = 88.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("tv3_channels_grid")
        ) {
            itemsIndexed(
                items = uiState.filteredChannels,
                key = { _, ch -> ch.id }
            ) { index, channel ->
                val isPlayingChannel = channel.id == activeChannel?.id
                EasyChannelTileCard(
                    channel = channel,
                    isActive = isPlayingChannel || index == uiState.focusedItemIndex,
                    isCurrentlyPlaying = isPlayingChannel,
                    onClick = { onSelectChannelForPlayer(channel, false) },
                    onFullscreenClick = { onSelectChannelForPlayer(channel, true) }
                )
            }
        }
    }
}



