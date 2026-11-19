package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.AppSettings
import com.example.data.model.ChannelItem
import com.example.data.model.EpgProgram
import com.example.data.player.VodResumeItem
import com.example.data.player.VodWatchHistoryManager
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.BorderGlow
import com.example.ui.theme.DarkNavyCard
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.GoldLight
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class HistoryFilter {
    RESUME_ACTIVE,   // Nur unvollendete Filme & Serien (<90%)
    ALL_VOD_HISTORY, // Vollständiger VOD-Verlauf
    LIVE_TV_HISTORY  // Live-TV Sender
}

@Composable
fun VodContinueWatchingScreen(
    resumeItems: List<VodResumeItem>,
    allVodItems: List<VodResumeItem>,
    liveTvChannels: List<ChannelItem>,
    liveEpgMap: Map<String, Pair<EpgProgram?, EpgProgram?>>,
    settings: AppSettings,
    onResumeItem: (VodResumeItem) -> Unit,
    onRestartItem: (VodResumeItem) -> Unit,
    onDeleteItem: (VodResumeItem) -> Unit,
    onClearAllVodHistory: () -> Unit,
    onPlayLiveChannel: (ChannelItem) -> Unit,
    onToggleFavorite: (ChannelItem) -> Unit,
    isEnglish: Boolean = false,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf(HistoryFilter.RESUME_ACTIVE) }
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text(
                    text = if (isEnglish) "Clear Watch History?" else "Wiedergabe-Historie leeren?",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (isEnglish) {
                        "Are you sure you want to delete all saved movie and series playback progress?"
                    } else {
                        "Möchtest du wirklich den gesamten Verlauf und alle gespeicherten Wiedergabestände für Filme und Serien löschen?"
                    },
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllVodHistory()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    Text(if (isEnglish) "Delete All" else "Alle löschen", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(if (isEnglish) "Cancel" else "Abbrechen", color = TextSecondary)
                }
            },
            containerColor = DarkSurfaceElevated
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkSurface)
    ) {
        // Filter Chips & Header Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = selectedFilter == HistoryFilter.RESUME_ACTIVE,
                    onClick = { selectedFilter = HistoryFilter.RESUME_ACTIVE },
                    label = {
                        Text(
                            text = if (isEnglish) "Continue Watching (${resumeItems.size})" else "Fortsetzen (${resumeItems.size})",
                            fontSize = 12.sp,
                            fontWeight = if (selectedFilter == HistoryFilter.RESUME_ACTIVE) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GoldPrimary,
                        selectedLabelColor = Color.Black,
                        containerColor = DarkSurfaceElevated,
                        labelColor = TextPrimary
                    )
                )

                FilterChip(
                    selected = selectedFilter == HistoryFilter.ALL_VOD_HISTORY,
                    onClick = { selectedFilter = HistoryFilter.ALL_VOD_HISTORY },
                    label = {
                        Text(
                            text = if (isEnglish) "All VOD (${allVodItems.size})" else "Alle Filme & Serien (${allVodItems.size})",
                            fontSize = 12.sp,
                            fontWeight = if (selectedFilter == HistoryFilter.ALL_VOD_HISTORY) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GoldPrimary,
                        selectedLabelColor = Color.Black,
                        containerColor = DarkSurfaceElevated,
                        labelColor = TextPrimary
                    )
                )

                FilterChip(
                    selected = selectedFilter == HistoryFilter.LIVE_TV_HISTORY,
                    onClick = { selectedFilter = HistoryFilter.LIVE_TV_HISTORY },
                    label = {
                        Text(
                            text = if (isEnglish) "Live TV" else "Live-TV",
                            fontSize = 12.sp,
                            fontWeight = if (selectedFilter == HistoryFilter.LIVE_TV_HISTORY) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GoldPrimary,
                        selectedLabelColor = Color.Black,
                        containerColor = DarkSurfaceElevated,
                        labelColor = TextPrimary
                    )
                )
            }

            if (selectedFilter != HistoryFilter.LIVE_TV_HISTORY && (resumeItems.isNotEmpty() || allVodItems.isNotEmpty())) {
                IconButton(
                    onClick = { showClearDialog = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Verlauf leeren",
                        tint = TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Content List
        when (selectedFilter) {
            HistoryFilter.RESUME_ACTIVE -> {
                if (resumeItems.isEmpty()) {
                    VodEmptyHistoryState(
                        title = if (isEnglish) "No unfinished movies or series" else "Keine unvollendeten Filme oder Serien",
                        subtitle = if (isEnglish) {
                            "When you pause a movie or episode, you can resume it here exactly where you left off."
                        } else {
                            "Wenn du einen Film oder eine Episode pausierst, merkt sich die App die exakte Sekunde und zeigt sie hier zum schnellen Fortsetzen an."
                        }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(resumeItems, key = { it.id }) { item ->
                            VodResumeCard(
                                item = item,
                                onResume = { onResumeItem(item) },
                                onRestart = { onRestartItem(item) },
                                onDelete = { onDeleteItem(item) },
                                isEnglish = isEnglish
                            )
                        }
                    }
                }
            }
            HistoryFilter.ALL_VOD_HISTORY -> {
                if (allVodItems.isEmpty()) {
                    VodEmptyHistoryState(
                        title = if (isEnglish) "No watch history available" else "Noch kein VOD-Verlauf vorhanden",
                        subtitle = if (isEnglish) {
                            "Started movies and series episodes will appear here."
                        } else {
                            "Hier erscheinen alle Filme und Serien-Episoden, die du gestartet hast."
                        }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(allVodItems, key = { it.id }) { item ->
                            VodResumeCard(
                                item = item,
                                onResume = { onResumeItem(item) },
                                onRestart = { onRestartItem(item) },
                                onDelete = { onDeleteItem(item) },
                                isEnglish = isEnglish
                            )
                        }
                    }
                }
            }
            HistoryFilter.LIVE_TV_HISTORY -> {
                val channels = liveTvChannels.take(25)
                if (channels.isEmpty()) {
                    VodEmptyHistoryState(
                        title = if (isEnglish) "No Live TV channels watched yet" else "Noch keine Live-TV Sender gesehen",
                        subtitle = if (isEnglish) "Tune into channels to see them in this history." else "Schalte Live-Sender ein, um sie hier im Verlauf zu sehen."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(channels, key = { it.id }) { ch ->
                            val livePrograms = liveEpgMap[ch.id] ?: ch.tvgId?.let { liveEpgMap[it] } ?: ch.epgId?.let { liveEpgMap[it] }
                            ChannelCard(
                                channel = ch,
                                onClick = { onPlayLiveChannel(ch) },
                                onToggleFavorite = { onToggleFavorite(ch) },
                                currentProgram = livePrograms?.first,
                                nextProgram = livePrograms?.second,
                                epgTimezone = settings.epgTimezone,
                                epgTimeOffsetHours = settings.epgTimeOffsetHours
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VodResumeCard(
    item: VodResumeItem,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onDelete: () -> Unit,
    isEnglish: Boolean
) {
    val dateStr = remember(item.watchedAtTimestamp) {
        val sdf = SimpleDateFormat("dd.MM. HH:mm", Locale.GERMAN)
        sdf.format(Date(item.watchedAtTimestamp))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, BorderGlow, RoundedCornerShape(12.dp))
            .clickable { onResume() }
            .testTag("vod_resume_card_${item.id}"),
        colors = CardDefaults.cardColors(containerColor = DarkNavyCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Poster / Thumbnail with Fallback
                Box(
                    modifier = Modifier
                        .size(width = 85.dp, height = 110.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceElevated)
                        .border(0.5.dp, BorderGlow, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!item.posterUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = item.posterUrl,
                            contentDescription = item.mainTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = if (item.isSeries) Icons.Default.Tv else Icons.Default.Movie,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Content type badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (item.isSeries) Color(0xFF1976D2) else Color(0xFFC2185B))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (item.isSeries) "SERIE" else "FILM",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Completed checkmark overlay if >90%
                    if (item.isCompleted) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.7f))
                                .padding(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Beendet",
                                tint = AccentCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Metadata Column
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 2.dp)
                ) {
                    // Strict Title requirement: "Breaking Bad - S02E05 (42:10 / 48:00)"
                    Text(
                        text = item.title,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Secondary info
                    Text(
                        text = if (item.isSeries && item.seasonNumber != null && item.episodeNumber != null) {
                            "Staffel ${item.seasonNumber} • Episode ${item.episodeNumber}"
                        } else {
                            item.mainTitle
                        },
                        color = GoldLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Zuletzt gesehen: $dateStr • ${(item.progressPercent * 100).toInt()}% abgeschlossen",
                        color = TextMuted,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Progress Bar
                    LinearProgressIndicator(
                        progress = { item.progressPercent },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (item.isCompleted) AccentCyan else GoldPrimary,
                        trackColor = Color.White.copy(alpha = 0.12f)
                    )
                }

                // Delete button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Eintrag entfernen",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onRestart,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isEnglish) "From Start" else "Von Anfang",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onResume,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    val posStr = VodWatchHistoryManager.formatTime(item.lastPositionSeconds)
                    Text(
                        text = if (isEnglish) "Resume at $posStr" else "Fortsetzen ($posStr)",
                        fontSize = 12.sp,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun VodEmptyHistoryState(
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceElevated)
                    .border(1.dp, BorderGlow, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = GoldPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = title,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = subtitle,
                color = TextMuted,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.widthIn(max = 380.dp)
            )
        }
    }
}
