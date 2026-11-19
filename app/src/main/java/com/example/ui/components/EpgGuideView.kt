package com.example.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.model.ChannelItem
import com.example.data.model.EpgProgram
import com.example.data.parser.EpgSyncState
import com.example.data.parser.FreeEpgService
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.min

private val NavyDark = DarkNavyBackground
private val SurfaceCard = DarkNavyCard
private val NavyDeep = DarkSurface
private val SurfaceHover = DarkSurfaceElevated
private val GlassBorder = BorderSubtle
private val ElectricBlue = AccentCyan
private val CyberCyan = GoldPrimary

private val TightTextStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false)
)

private fun formatEpgTime(timestampMs: Long, timezoneId: String): String {
    return try {
        val sdf = SimpleDateFormat("HH:mm", Locale.GERMANY)
        sdf.timeZone = TimeZone.getTimeZone(timezoneId.ifBlank { "Europe/Berlin" })
        sdf.format(Date(timestampMs))
    } catch (_: Exception) {
        val sdf = SimpleDateFormat("HH:mm", Locale.GERMANY)
        sdf.format(Date(timestampMs))
    }
}

private enum class EpgDisplayMode {
    GRID,
    SINGLE_CHANNEL
}

private data class SelectedProgramDetail(
    val channel: ChannelItem,
    val program: EpgProgram
)

@Composable
fun EpgGuideView(
    channels: List<ChannelItem>,
    onSelectChannel: (ChannelItem) -> Unit,
    epgTimezone: String = "Europe/Berlin",
    epgTimeOffsetHours: Int = 0,
    isEnglish: Boolean = false,
    epgGridMap: Map<String, List<EpgProgram>> = emptyMap(),
    syncState: EpgSyncState = EpgSyncState.Idle,
    onSyncEpg: () -> Unit = {},
    loadChannelSchedule: suspend (ChannelItem) -> List<EpgProgram> = { emptyList() }
) {
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    var displayMode by remember { mutableStateOf(EpgDisplayMode.GRID) }
    var showOnlyVerified by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedProgramDetail by remember { mutableStateOf<SelectedProgramDetail?>(null) }

    // Automatically trigger EPG sync/preload when opening EPG Guide if not yet loaded
    LaunchedEffect(channels.size) {
        if (channels.isNotEmpty() && epgGridMap.isEmpty() && syncState !is EpgSyncState.Syncing) {
            onSyncEpg()
        }
    }

    // Live clock that ticks every 30 seconds
    var currentTickMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            currentTickMs = System.currentTimeMillis()
        }
    }

    val effectiveNowMs = currentTickMs + (epgTimeOffsetHours * 3600 * 1000L)

    // Timeline parameters: -2 hours to +18 hours (20 hours total = 40 half-hour slots)
    val halfHourMs = 30 * 60 * 1000L
    val gridStartMs = remember(effectiveNowMs / halfHourMs) {
        val roundedHalfHour = (effectiveNowMs / halfHourMs) * halfHourMs
        roundedHalfHour - (4 * halfHourMs) // 2 hours before current slot
    }
    val totalSlots = 40 // 20 hours
    val gridEndMs = gridStartMs + (totalSlots * halfHourMs)

    val verifiedChannelsCount = remember(channels, epgGridMap) {
        channels.count { ch ->
            !epgGridMap[ch.id].isNullOrEmpty()
        }
    }

    val filteredChannels = remember(channels, epgGridMap, showOnlyVerified, searchQuery) {
        val base = if (showOnlyVerified) {
            val verifiedOnly = channels.filter { ch ->
                !epgGridMap[ch.id].isNullOrEmpty() &&
                    (ch.isManualEpgOverride || ch.epgMappingStatus == "MATCHED" || !ch.epgId.isNullOrBlank())
            }
            verifiedOnly.ifEmpty { channels }
        } else {
            channels
        }

        if (searchQuery.isBlank()) {
            base
        } else {
            val q = searchQuery.trim().lowercase()
            base.filter { ch ->
                ch.name.lowercase().contains(q) ||
                    ch.groupTitle.lowercase().contains(q) ||
                    (epgGridMap[ch.id]?.any { p -> p.title.lowercase().contains(q) } == true)
            }
        }
    }

    var selectedSingleChannel by remember(filteredChannels) {
        mutableStateOf(filteredChannels.firstOrNull() ?: channels.firstOrNull())
    }

    val dpPerMinute = if (isPortrait) 3.4f else 3.8f
    val slotWidthDp: Dp = (30f * dpPerMinute).dp
    val totalTimelineWidthDp: Dp = (totalSlots * 30f * dpPerMinute).dp
    val channelColumnWidthDp: Dp = if (isPortrait) 134.dp else 186.dp
    val rowHeightDp: Dp = if (isPortrait) 68.dp else 72.dp

    val horizontalScrollState = rememberScrollState()

    fun scrollToTime(targetTimeMs: Long) {
        val minutesFromStart = ((targetTimeMs - gridStartMs) / 60_000f).coerceIn(0f, totalSlots * 30f)
        val targetDp = (minutesFromStart * dpPerMinute - 48f).coerceAtLeast(0f).dp
        val targetPx = with(density) { targetDp.roundToPx() }
        coroutineScope.launch {
            horizontalScrollState.animateScrollTo(targetPx)
        }
    }

    fun scrollToNow() {
        scrollToTime(effectiveNowMs)
    }

    fun scrollToPrimetime() {
        val tz = try {
            TimeZone.getTimeZone(epgTimezone.ifBlank { "Europe/Berlin" })
        } catch (_: Exception) {
            TimeZone.getTimeZone("Europe/Berlin")
        }
        val cal = Calendar.getInstance(tz).apply {
            timeInMillis = effectiveNowMs
            set(Calendar.HOUR_OF_DAY, 20)
            set(Calendar.MINUTE, 15)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis < gridStartMs) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        scrollToTime(cal.timeInMillis)
    }

    // Scroll to "NOW" automatically when the EPG Grid first opens
    LaunchedEffect(gridStartMs) {
        val minutesFromStart = ((effectiveNowMs - gridStartMs) / 60_000f).coerceAtLeast(0f)
        val targetDp = (minutesFromStart * dpPerMinute - 48f).coerceAtLeast(0f).dp
        val targetPx = with(density) { targetDp.roundToPx() }
        horizontalScrollState.scrollTo(targetPx)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyDark)
            .padding(horizontal = if (isPortrait) 6.dp else 12.dp, vertical = 6.dp)
    ) {
        // =========================================================================
        // COMPACT HEADER & CONTROLS (Responsive for Phone Portrait & TV Landscape)
        // =========================================================================
        Surface(
            color = SurfaceCard,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, GlassBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Row 1: Title, Verified Badge & Quick Jump / Sync Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (isEnglish) "📅 EPG Guide" else "📅 EPG Programm",
                            color = TextPrimary,
                            fontSize = if (isPortrait) 14.sp else 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TightTextStyle
                        )
                        Surface(
                            color = Color(0xFF10B981).copy(alpha = 0.16f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "${filteredChannels.size} Sender",
                                color = Color(0xFF10B981),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                style = TightTextStyle,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (displayMode == EpgDisplayMode.GRID) {
                            // Jump to NOW button
                            Surface(
                                color = Color(0xFFEF4444).copy(alpha = 0.18f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFFEF4444)),
                                modifier = Modifier
                                    .testTag("epg_jump_now_button")
                                    .clickable { scrollToNow() }
                            ) {
                                Text(
                                    text = if (isEnglish) "● NOW" else "● JETZT",
                                    color = Color(0xFFFF6B6B),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    style = TightTextStyle,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
                                )
                            }

                            // Jump to 20:15 Primetime button
                            Surface(
                                color = CyberCyan.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.7f)),
                                modifier = Modifier
                                    .testTag("epg_jump_primetime_button")
                                    .clickable { scrollToPrimetime() }
                            ) {
                                Text(
                                    text = "★ 20:15",
                                    color = GoldLight,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    style = TightTextStyle,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
                                )
                            }
                        }

                        // Sync EPG button
                        val isSyncing = syncState is EpgSyncState.Syncing
                        Surface(
                            color = ElectricBlue.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, ElectricBlue),
                            modifier = Modifier
                                .testTag("epg_sync_now_button")
                                .clickable(enabled = !isSyncing) { onSyncEpg() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(11.dp),
                                        strokeWidth = 1.8.dp,
                                        color = ElectricBlue
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "EPG Sync",
                                        tint = ElectricBlue,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                Text(
                                    text = if (isSyncing) "Sync..." else "Sync",
                                    color = ElectricBlue,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    style = TightTextStyle
                                )
                            }
                        }
                    }
                }

                // Row 2: Mode Switches + Search Input (Adaptive so phone portrait never squishes)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Grid vs Single-Channel toggle
                        Surface(
                            color = if (displayMode == EpgDisplayMode.GRID) ElectricBlue.copy(alpha = 0.22f) else NavyDeep,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (displayMode == EpgDisplayMode.GRID) ElectricBlue else GlassBorder),
                            modifier = Modifier
                                .testTag("epg_mode_grid_button")
                                .clickable { displayMode = EpgDisplayMode.GRID }
                        ) {
                            Text(
                                text = if (isEnglish) "📊 Grid" else "📊 Raster",
                                color = if (displayMode == EpgDisplayMode.GRID) ElectricBlue else TextSecondary,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                style = TightTextStyle,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }

                        Surface(
                            color = if (displayMode == EpgDisplayMode.SINGLE_CHANNEL) ElectricBlue.copy(alpha = 0.22f) else NavyDeep,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (displayMode == EpgDisplayMode.SINGLE_CHANNEL) ElectricBlue else GlassBorder),
                            modifier = Modifier
                                .testTag("epg_mode_single_button")
                                .clickable { displayMode = EpgDisplayMode.SINGLE_CHANNEL }
                        ) {
                            Text(
                                text = if (isEnglish) "📋 List" else "📋 Liste",
                                color = if (displayMode == EpgDisplayMode.SINGLE_CHANNEL) ElectricBlue else TextSecondary,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                style = TightTextStyle,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }

                        // All Channels vs Strictly XMLTV Verified Filter Toggle
                        Surface(
                            color = if (showOnlyVerified) Color(0xFF10B981).copy(alpha = 0.18f) else NavyDeep,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (showOnlyVerified) Color(0xFF10B981) else GlassBorder),
                            modifier = Modifier
                                .testTag("epg_filter_verified_button")
                                .clickable { showOnlyVerified = !showOnlyVerified }
                        ) {
                            Text(
                                text = if (showOnlyVerified) {
                                    if (isEnglish) "✓ Verified" else "✓ Verifiziert ($verifiedChannelsCount)"
                                } else {
                                    if (isEnglish) "All Channels" else "Alle Sender"
                                },
                                color = if (showOnlyVerified) Color(0xFF10B981) else TextSecondary,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                style = TightTextStyle,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }

                    // Compact Search Input
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(min = 105.dp)
                            .height(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(NavyDeep)
                            .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 7.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Suche",
                                tint = TextMuted,
                                modifier = Modifier.size(12.dp)
                            )
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = if (isEnglish) "Search..." else "Sender / Show...",
                                        color = TextMuted,
                                        fontSize = 10.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = TightTextStyle
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = TextPrimary,
                                        fontSize = 10.5.sp,
                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                                    ),
                                    cursorBrush = SolidColor(ElectricBlue),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("epg_search_input")
                                )
                            }
                            if (searchQuery.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Löschen",
                                    tint = TextSecondary,
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clickable { searchQuery = "" }
                                )
                            }
                        }
                    }
                }

                // Sync progress banner if currently syncing
                if (syncState is EpgSyncState.Syncing) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { (syncState.progressPercent / 100f).coerceIn(0.05f, 1f) },
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = ElectricBlue,
                            trackColor = NavyDeep
                        )
                        Text(
                            text = syncState.message,
                            color = ElectricBlue,
                            fontSize = 9.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TightTextStyle
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // =========================================================================
        // MAIN EPG BODY (Uses weight(1f) so bottom edge is NEVER clipped or distorted)
        // =========================================================================
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (filteredChannels.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceCard)
                        .border(1.dp, GlassBorder, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        if (syncState is EpgSyncState.Syncing) {
                            CircularProgressIndicator(color = ElectricBlue)
                            Text(
                                text = syncState.message,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = if (channels.isEmpty()) {
                                    if (isEnglish) "No live channels loaded in the active playlist."
                                    else "Keine Live-TV-Sender in der aktiven Playliste geladen."
                                } else {
                                    if (isEnglish) "No channels match your current filter."
                                    else "Keine passenden Sender für diesen Suchbegriff gefunden."
                                },
                                color = TextSecondary,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = onSyncEpg,
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                                ) {
                                    Text(
                                        text = if (isEnglish) "🔄 Sync EPG Now" else "🔄 EPG jetzt synchronisieren",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (showOnlyVerified || searchQuery.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = {
                                            showOnlyVerified = false
                                            searchQuery = ""
                                        },
                                        border = BorderStroke(1.dp, GlassBorder)
                                    ) {
                                        Text(
                                            text = if (isEnglish) "Show All Channels" else "Alle Sender anzeigen",
                                            color = TextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (displayMode == EpgDisplayMode.GRID) {
                // =========================================================================
                // 2D EPG PROGRAM GRID (Sticky Channel Column + Synchronized Timeline Rows)
                // =========================================================================
                Surface(
                    color = SurfaceCard,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, GlassBorder),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // 1. Sticky Top Timeline Header Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .background(NavyDeep)
                        ) {
                            // Top-left corner header
                            Box(
                                modifier = Modifier
                                    .width(channelColumnWidthDp)
                                    .fillMaxHeight()
                                    .background(NavyDeep)
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = if (isEnglish) "CHANNEL (${filteredChannels.size})" else "SENDER (${filteredChannels.size})",
                                    color = ElectricBlue,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = TightTextStyle
                                )
                            }

                            // Vertical divider
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(GlassBorder)
                            )

                            // Scrollable 30-min time slot headers
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .horizontalScroll(horizontalScrollState)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .width(totalTimelineWidthDp)
                                        .fillMaxHeight()
                                ) {
                                    for (slotIndex in 0 until totalSlots) {
                                        val slotTimeMs = gridStartMs + (slotIndex * halfHourMs)
                                        val isCurrentSlot = effectiveNowMs in slotTimeMs until (slotTimeMs + halfHourMs)
                                        val timeLabel = formatEpgTime(slotTimeMs, epgTimezone)

                                        Box(
                                            modifier = Modifier
                                                .width(slotWidthDp)
                                                .fillMaxHeight()
                                                .background(
                                                    if (isCurrentSlot) ElectricBlue.copy(alpha = 0.16f)
                                                    else Color.Transparent
                                                )
                                                .border(
                                                    width = 0.5.dp,
                                                    color = GlassBorder.copy(alpha = 0.5f)
                                                )
                                                .padding(horizontal = 8.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                if (isCurrentSlot) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFFEF4444))
                                                    )
                                                }
                                                Text(
                                                    text = timeLabel,
                                                    color = if (isCurrentSlot) ElectricBlue else TextSecondary,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = if (isCurrentSlot) FontWeight.ExtraBold else FontWeight.SemiBold,
                                                    style = TightTextStyle
                                                )
                                            }
                                        }
                                    }
                                }

                                // Red "NOW" marker tick in header
                                if (effectiveNowMs in gridStartMs..gridEndMs) {
                                    val nowOffsetDp = (((effectiveNowMs - gridStartMs) / 60_000f) * dpPerMinute).dp
                                    Box(
                                        modifier = Modifier
                                            .offset(x = nowOffsetDp)
                                            .width(2.dp)
                                            .fillMaxHeight()
                                            .background(Color(0xFFEF4444))
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = GlassBorder, thickness = 1.dp)

                        // 2. Vertically Scrollable Channel Rows with Synchronized Horizontal Timeline
                        // Uses weight(1f) + generous bottom contentPadding so the last rows are completely visible
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .testTag("epg_grid_lazy_column"),
                            contentPadding = PaddingValues(bottom = 32.dp)
                        ) {
                            items(
                                items = filteredChannels,
                                key = { it.id },
                                contentType = { "epg_grid_channel_row" }
                            ) { channel ->
                                val channelPrograms = remember(channel.id, channel.name, channel.tvgId, channel.epgId, epgGridMap[channel.id], gridStartMs, gridEndMs, epgTimezone) {
                                    val fromMap = epgGridMap[channel.id]
                                    if (!fromMap.isNullOrEmpty()) {
                                        fromMap
                                    } else {
                                        FreeEpgService.getScheduleForWindow(
                                            tvgId = channel.epgId ?: channel.tvgId,
                                            channelName = channel.name,
                                            windowStartMs = gridStartMs,
                                            windowEndMs = gridEndMs,
                                            timezoneId = epgTimezone
                                        )
                                    }
                                }

                                EpgGridChannelRow(
                                    channel = channel,
                                    programs = channelPrograms,
                                    gridStartMs = gridStartMs,
                                    gridEndMs = gridEndMs,
                                    effectiveNowMs = effectiveNowMs,
                                    dpPerMinute = dpPerMinute,
                                    totalTimelineWidthDp = totalTimelineWidthDp,
                                    channelColumnWidthDp = channelColumnWidthDp,
                                    rowHeightDp = rowHeightDp,
                                    horizontalScrollState = horizontalScrollState,
                                    epgTimezone = epgTimezone,
                                    onClickChannel = { onSelectChannel(channel) },
                                    onClickProgram = { prog ->
                                        selectedProgramDetail = SelectedProgramDetail(channel, prog)
                                    }
                                )
                                HorizontalDivider(color = GlassBorder.copy(alpha = 0.45f), thickness = 0.5.dp)
                            }
                        }
                    }
                }
            } else {
                // =========================================================================
                // SINGLE-CHANNEL FULL SCHEDULE VIEW (Adaptive for Phone Portrait & TV)
                // =========================================================================
                SingleChannelEpgView(
                    channels = filteredChannels,
                    selectedChannel = selectedSingleChannel,
                    onSelectChannelForSchedule = { selectedSingleChannel = it },
                    onPlayChannel = onSelectChannel,
                    epgGridMap = epgGridMap,
                    loadChannelSchedule = loadChannelSchedule,
                    effectiveNowMs = effectiveNowMs,
                    epgTimezone = epgTimezone,
                    isPortrait = isPortrait,
                    isEnglish = isEnglish,
                    onSelectProgramDetail = { ch, prog ->
                        selectedProgramDetail = SelectedProgramDetail(ch, prog)
                    }
                )
            }
        }
    }

    // Program Details & Play Dialog when clicking any cell in the EPG Grid
    selectedProgramDetail?.let { detail ->
        EpgProgramDetailDialog(
            channel = detail.channel,
            program = detail.program,
            effectiveNowMs = effectiveNowMs,
            epgTimezone = epgTimezone,
            isEnglish = isEnglish,
            onDismiss = { selectedProgramDetail = null },
            onWatchChannel = {
                selectedProgramDetail = null
                onSelectChannel(detail.channel)
            }
        )
    }
}

@Composable
private fun EpgGridChannelRow(
    channel: ChannelItem,
    programs: List<EpgProgram>,
    gridStartMs: Long,
    gridEndMs: Long,
    effectiveNowMs: Long,
    dpPerMinute: Float,
    totalTimelineWidthDp: Dp,
    channelColumnWidthDp: Dp,
    rowHeightDp: Dp,
    horizontalScrollState: androidx.compose.foundation.ScrollState,
    epgTimezone: String,
    onClickChannel: () -> Unit,
    onClickProgram: (EpgProgram) -> Unit
) {
    val visiblePrograms = remember(programs, gridStartMs, gridEndMs) {
        programs
            .filter { it.endTime > gridStartMs && it.startTime < gridEndMs && it.endTime > it.startTime }
            .sortedBy { it.startTime }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeightDp)
            .background(SurfaceCard)
    ) {
        // Sticky Left Channel Info Cell
        Row(
            modifier = Modifier
                .width(channelColumnWidthDp)
                .fillMaxHeight()
                .background(NavyDark.copy(alpha = 0.94f))
                .clickable { onClickChannel() }
                .padding(horizontal = 7.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(NavyDeep)
                    .border(1.dp, GlassBorder, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (!channel.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(3.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(
                        text = channel.name.take(2).uppercase(),
                        color = ElectricBlue,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        style = TightTextStyle
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = channel.name,
                    color = TextPrimary,
                    fontSize = 11.5.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TightTextStyle
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "EPG Aktiv",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(9.dp)
                    )
                    Text(
                        text = channel.countryCode.ifBlank { "TV" },
                        color = Color(0xFF10B981),
                        fontSize = 8.5.sp,
                        lineHeight = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TightTextStyle
                    )
                }
            }
        }

        // Vertical Divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(GlassBorder)
        )

        // Synchronized Horizontal Program Timeline Row
        // Uses exact coordinate offsets on the fixed timeline canvas so cells never drift or distort
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(horizontalScrollState)
        ) {
            Box(
                modifier = Modifier
                    .width(totalTimelineWidthDp)
                    .fillMaxHeight()
                    .background(NavyDeep.copy(alpha = 0.22f))
            ) {
                var lastEndMs = gridStartMs

                for (prog in visiblePrograms) {
                    val clampedStart = max(prog.startTime, max(gridStartMs, lastEndMs))
                    val clampedEnd = min(prog.endTime, gridEndMs)
                    if (clampedEnd <= clampedStart) continue
                    lastEndMs = clampedEnd

                    val startMinutes = (clampedStart - gridStartMs) / 60_000f
                    val durationMinutes = (clampedEnd - clampedStart) / 60_000f
                    val offsetDp = (startMinutes * dpPerMinute).dp
                    val cellWidthDp = (durationMinutes * dpPerMinute).dp
                    if (cellWidthDp < 4.dp) continue

                    val isLiveNow = effectiveNowMs in prog.startTime until prog.endTime
                    val isPast = prog.endTime <= effectiveNowMs
                    val isCompactCell = cellWidthDp < 72.dp

                    val progress = if (isLiveNow) {
                        val total = (prog.endTime - prog.startTime).coerceAtLeast(1L)
                        val elapsed = (effectiveNowMs - prog.startTime).coerceAtLeast(0L)
                        (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    Box(
                        modifier = Modifier
                            .offset(x = offsetDp)
                            .width(cellWidthDp)
                            .fillMaxHeight()
                            .padding(horizontal = 1.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clipToBounds()
                            .background(
                                when {
                                    isLiveNow -> Brush.horizontalGradient(
                                        listOf(
                                            ElectricBlue.copy(alpha = 0.28f),
                                            CyberCyan.copy(alpha = 0.16f)
                                        )
                                    )
                                    isPast -> Brush.horizontalGradient(
                                        listOf(
                                            NavyDeep.copy(alpha = 0.65f),
                                            NavyDeep.copy(alpha = 0.65f)
                                        )
                                    )
                                    else -> Brush.horizontalGradient(
                                        listOf(
                                            SurfaceHover.copy(alpha = 0.88f),
                                            NavyDeep.copy(alpha = 0.88f)
                                        )
                                    )
                                }
                            )
                            .border(
                                width = if (isLiveNow) 1.5.dp else 0.5.dp,
                                color = if (isLiveNow) ElectricBlue else GlassBorder.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { onClickProgram(prog) }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = if (isCompactCell) 4.dp else 7.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Top line: LIVE badge + start/end time
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isLiveNow && !isCompactCell) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(Color(0xFFEF4444))
                                            .padding(horizontal = 3.5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "LIVE",
                                            color = Color.White,
                                            fontSize = 7.5.sp,
                                            lineHeight = 8.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1,
                                            style = TightTextStyle
                                        )
                                    }
                                }
                                Text(
                                    text = if (isCompactCell) {
                                        formatEpgTime(prog.startTime, epgTimezone)
                                    } else {
                                        "${formatEpgTime(prog.startTime, epgTimezone)} - ${formatEpgTime(prog.endTime, epgTimezone)}"
                                    },
                                    color = if (isLiveNow) ElectricBlue else TextMuted,
                                    fontSize = 9.sp,
                                    lineHeight = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                    style = TightTextStyle
                                )
                            }

                            // Middle line: Program Title (guaranteed visible, never clipped vertically)
                            Text(
                                text = prog.title,
                                color = if (isPast) TextSecondary else TextPrimary,
                                fontSize = 11.sp,
                                lineHeight = 12.5.sp,
                                fontWeight = if (isLiveNow) FontWeight.ExtraBold else FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TightTextStyle,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Bottom line: Progress bar if live, or subtle category/spacer
                            if (isLiveNow) {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = ElectricBlue,
                                    trackColor = NavyDark
                                )
                            } else {
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                        }
                    }
                }

                // Live Red Vertical "JETZT" Line across the channel row
                if (effectiveNowMs in gridStartMs..gridEndMs) {
                    val nowOffsetDp = (((effectiveNowMs - gridStartMs) / 60_000f) * dpPerMinute).dp
                    Box(
                        modifier = Modifier
                            .offset(x = nowOffsetDp)
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(Color(0xFFEF4444).copy(alpha = 0.88f))
                    )
                }
            }
        }
    }
}

@Composable
private fun SingleChannelEpgView(
    channels: List<ChannelItem>,
    selectedChannel: ChannelItem?,
    onSelectChannelForSchedule: (ChannelItem) -> Unit,
    onPlayChannel: (ChannelItem) -> Unit,
    epgGridMap: Map<String, List<EpgProgram>>,
    loadChannelSchedule: suspend (ChannelItem) -> List<EpgProgram>,
    effectiveNowMs: Long,
    epgTimezone: String,
    isPortrait: Boolean,
    isEnglish: Boolean,
    onSelectProgramDetail: (ChannelItem, EpgProgram) -> Unit
) {
    var schedule by remember { mutableStateOf<List<EpgProgram>>(emptyList()) }

    LaunchedEffect(selectedChannel, epgGridMap, effectiveNowMs / 60_000L) {
        val ch = selectedChannel ?: return@LaunchedEffect
        val cached = epgGridMap[ch.id]
        if (!cached.isNullOrEmpty()) {
            schedule = cached
        } else {
            val loaded = loadChannelSchedule(ch)
            schedule = if (loaded.isNotEmpty()) {
                loaded
            } else {
                FreeEpgService.getScheduleForWindow(
                    tvgId = ch.epgId ?: ch.tvgId,
                    channelName = ch.name,
                    windowStartMs = effectiveNowMs - (4 * 3600_000L),
                    windowEndMs = effectiveNowMs + (24 * 3600_000L),
                    timezoneId = epgTimezone
                )
            }
        }
    }

    if (isPortrait) {
        // Smartphone Portrait Layout: Top Horizontal Channel Ribbon + Full-Width Schedule List below
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(channels, key = { it.id }) { ch ->
                    val isSelected = selectedChannel?.id == ch.id
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) ElectricBlue.copy(alpha = 0.22f) else SurfaceCard)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) ElectricBlue else GlassBorder,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectChannelForSchedule(ch) }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = ch.name,
                            color = if (isSelected) ElectricBlue else TextPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                            maxLines = 1,
                            style = TightTextStyle
                        )
                    }
                }
            }

            SingleChannelScheduleCard(
                channel = selectedChannel,
                schedule = schedule,
                effectiveNowMs = effectiveNowMs,
                epgTimezone = epgTimezone,
                isEnglish = isEnglish,
                onPlayChannel = onPlayChannel,
                onSelectProgramDetail = onSelectProgramDetail,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    } else {
        // Landscape / Android TV Layout: Left Channel Sidebar + Right Schedule List
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color = SurfaceCard,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, GlassBorder),
                modifier = Modifier
                    .width(230.dp)
                    .fillMaxHeight()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(channels, key = { it.id }) { ch ->
                        val isSelected = selectedChannel?.id == ch.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) ElectricBlue.copy(alpha = 0.2f) else Color.Transparent)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) ElectricBlue else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onSelectChannelForSchedule(ch) }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = ch.name,
                                color = if (isSelected) ElectricBlue else TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                                style = TightTextStyle
                            )
                        }
                    }
                }
            }

            SingleChannelScheduleCard(
                channel = selectedChannel,
                schedule = schedule,
                effectiveNowMs = effectiveNowMs,
                epgTimezone = epgTimezone,
                isEnglish = isEnglish,
                onPlayChannel = onPlayChannel,
                onSelectProgramDetail = onSelectProgramDetail,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun SingleChannelScheduleCard(
    channel: ChannelItem?,
    schedule: List<EpgProgram>,
    effectiveNowMs: Long,
    epgTimezone: String,
    isEnglish: Boolean,
    onPlayChannel: (ChannelItem) -> Unit,
    onSelectProgramDetail: (ChannelItem, EpgProgram) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SurfaceCard,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, GlassBorder),
        modifier = modifier
    ) {
        if (channel == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (isEnglish) "Select a channel" else "Bitte einen Sender auswählen",
                    color = TextMuted
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = channel.name,
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TightTextStyle
                        )
                        Text(
                            text = "${channel.groupTitle.ifBlank { "Live TV" }} • ${schedule.size} Sendungen",
                            color = Color(0xFF10B981),
                            fontSize = 11.sp,
                            style = TightTextStyle
                        )
                    }
                    Button(
                        onClick = { onPlayChannel(channel) },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isEnglish) "Watch" else "Einschalten",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            style = TightTextStyle
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = GlassBorder)
                Spacer(modifier = Modifier.height(8.dp))

                val upcomingAndCurrent = remember(schedule, effectiveNowMs) {
                    schedule.filter { it.endTime > effectiveNowMs - 1800_000L }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(upcomingAndCurrent, key = { it.id }) { prog ->
                        val isNow = effectiveNowMs in prog.startTime until prog.endTime
                        Surface(
                            color = if (isNow) ElectricBlue.copy(alpha = 0.14f) else NavyDeep,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (isNow) ElectricBlue else GlassBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectProgramDetail(channel, prog) }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${formatEpgTime(prog.startTime, epgTimezone)} - ${formatEpgTime(prog.endTime, epgTimezone)} Uhr",
                                        color = if (isNow) ElectricBlue else TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        style = TightTextStyle
                                    )
                                    if (isNow) {
                                        Surface(
                                            color = Color(0xFFEF4444),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "LIVE",
                                                color = Color.White,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                style = TightTextStyle,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = prog.title,
                                    color = TextPrimary,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    style = TightTextStyle
                                )
                                if (prog.description.isNotBlank()) {
                                    Text(
                                        text = prog.description,
                                        color = TextSecondary,
                                        fontSize = 11.5.sp,
                                        lineHeight = 15.sp,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
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
    channel: ChannelItem,
    program: EpgProgram,
    effectiveNowMs: Long,
    epgTimezone: String,
    isEnglish: Boolean,
    onDismiss: () -> Unit,
    onWatchChannel: () -> Unit
) {
    val isLiveNow = effectiveNowMs in program.startTime until program.endTime
    val durationMinutes = ((program.endTime - program.startTime) / 60_000L).coerceAtLeast(1L)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = SurfaceCard,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, ElectricBlue),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = channel.name,
                            color = ElectricBlue,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "✓ EPG Programm (${program.channelTvgId.ifBlank { channel.countryCode.ifBlank { "Live TV" } }})",
                            color = Color(0xFF10B981),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (isLiveNow) {
                        Surface(
                            color = Color(0xFFEF4444),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (isEnglish) "● LIVE NOW" else "● LÄUFT JETZT",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Text(
                    text = program.title,
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = NavyDeep,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, GlassBorder)
                    ) {
                        Text(
                            text = "🕒 ${formatEpgTime(program.startTime, epgTimezone)} - ${formatEpgTime(program.endTime, epgTimezone)} Uhr ($durationMinutes Min.)",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    if (program.category.isNotBlank()) {
                        Surface(
                            color = ElectricBlue.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = program.category,
                                color = ElectricBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                if (program.description.isNotBlank()) {
                    Surface(
                        color = NavyDeep,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, GlassBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = program.description,
                            color = TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = if (isEnglish) "Close" else "Schließen",
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onWatchChannel,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isEnglish) "Watch Channel Now" else "Sender jetzt einschalten",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
