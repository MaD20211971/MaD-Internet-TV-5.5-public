package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.database.entity.EpgSourceEntity
import com.example.data.database.entity.ManualEpgMappingEntity
import com.example.data.local.EpgDao
import com.example.data.model.AppSettings
import com.example.data.model.ChannelItem
import com.example.ui.components.epg.EpgSourceManagementDialog
import com.example.ui.components.epg.ManualEpgMappingDialog
import com.example.data.model.ContentType
import com.example.data.model.EpgProgram
import com.example.data.parser.EpgSyncState
import com.example.data.parser.FreeEpgService
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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class EpgViewMode {
    CHANNEL_TIMELINE, // Single channel vertical timeline
    TIME_GRID // All channels at chosen time slot
}

data class TimeSlotJump(val label: String, val hour: Int, val minute: Int, val iconName: String)

val TIME_SLOT_JUMPS = listOf(
    TimeSlotJump("🔴 Jetzt Live", -1, -1, "live"),
    TimeSlotJump("🌅 06:00", 6, 0, "morning"),
    TimeSlotJump("☀️ 12:00", 12, 0, "noon"),
    TimeSlotJump("☕ 15:00", 15, 0, "afternoon"),
    TimeSlotJump("🌆 18:00", 18, 0, "evening"),
    TimeSlotJump("⭐ 20:15 Primetime", 20, 15, "primetime"),
    TimeSlotJump("🌙 22:30 Spät", 22, 30, "night")
)

val EPG_CATEGORIES = listOf(
    "Alle",
    "🎬 Filme & Serien",
    "⚽ Sport",
    "📰 Nachrichten",
    "🌿 Doku",
    "✨ Show & Unterhaltung",
    "🧸 Kinder",
    "🎭 Kultur & Wissen"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpgScheduleScreen(
    channels: List<ChannelItem>,
    onSelectChannel: (ChannelItem) -> Unit,
    epgDao: EpgDao,
    syncState: EpgSyncState,
    onTriggerSync: (String) -> Unit,
    settings: AppSettings,
    onUpdateEpgTimezone: (String) -> Unit,
    onUpdateCustomEpgUrl: (String) -> Unit,
    isEnglish: Boolean = false,
    onClose: (() -> Unit)? = null,
    autoCloseOnSelect: Boolean = true,
    onToggleFavorite: ((ChannelItem) -> Unit)? = null,
    manualMappings: Map<String, ManualEpgMappingEntity> = emptyMap(),
    onSaveManualMapping: ((channelId: String, channelName: String, tvgId: String?, mappedEpgId: String, mappedName: String) -> Unit)? = null,
    onRemoveManualMapping: ((channelId: String) -> Unit)? = null,
    epgSources: List<EpgSourceEntity> = emptyList(),
    onAddEpgSource: ((name: String, url: String, retentionDays: Int) -> Unit)? = null,
    onToggleEpgSource: ((id: String, enabled: Boolean) -> Unit)? = null,
    onDeleteEpgSource: ((id: String) -> Unit)? = null,
    onSyncEpgSource: ((id: String) -> Unit)? = null,
    onCleanupEpg: ((days: Int) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var showSourceDialog by remember { mutableStateOf(false) }
    var showMappingDialog by remember { mutableStateOf(false) }

    val handleSelectChannel: (ChannelItem) -> Unit = { ch ->
        onSelectChannel(ch)
        if (autoCloseOnSelect) {
            onClose?.invoke()
        }
    }

    val liveChannels = remember(channels) {
        channels.filter { it.type == ContentType.LIVE_TV }
            .sortedWith(
                compareByDescending<ChannelItem> { it.isFavorite }
                    .thenBy { it.liveTvSortRank }
                    .thenBy { it.cleanSortTitle }
            )
    }

    var selectedChannelId by remember(liveChannels) {
        mutableStateOf(liveChannels.firstOrNull()?.id)
    }

    val selectedChannel = remember(selectedChannelId, liveChannels) {
        liveChannels.find { it.id == selectedChannelId } ?: liveChannels.firstOrNull()
    }

    var viewMode by remember { mutableStateOf(EpgViewMode.CHANNEL_TIMELINE) }
    var selectedDayOffset by remember { mutableIntStateOf(0) } // -1=Yesterday, 0=Today, 1=Tomorrow, 2=Day After
    var selectedTimeSlotIndex by remember { mutableIntStateOf(0) } // 0=Jetzt Live
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Alle") }

    // Dialogs & Sheets
    var showServerDialog by remember { mutableStateOf(false) }
    var selectedProgramForDetail by remember { mutableStateOf<EpgProgram?>(null) }
    var dbProgramCount by remember { mutableIntStateOf(0) }

    // Stored programs in Room DB for selected channel
    var roomProgramsForChannel by remember { mutableStateOf<List<EpgProgram>>(emptyList()) }
    var allChannelsCurrentPrograms by remember { mutableStateOf<Map<String, EpgProgram>>(emptyMap()) }

    // Load program count from Room DB
    LaunchedEffect(syncState) {
        try {
            dbProgramCount = epgDao.getProgramCount()
        } catch (_: Exception) {}
    }

    // Load channel schedule from Room DB (or fallback to FreeEpgService)
    LaunchedEffect(selectedChannel, selectedDayOffset, dbProgramCount) {
        if (selectedChannel != null) {
            val tvgId = selectedChannel.tvgId ?: ""
            val name = selectedChannel.name
            val canonicalKey = FreeEpgService.matchKey(tvgId, name)
            val shortName = FreeEpgService.normalizeChannelName(name).split(" ").firstOrNull { it.length > 2 } ?: ""
            val candidateKeys = FreeEpgService.getCandidateKeys(tvgId, name, selectedChannel.tvgName)

            val fromRoom = try {
                val byCandidates = epgDao.getProgramsForCandidateKeys(candidateKeys)
                if (byCandidates.isNotEmpty()) {
                    byCandidates
                } else {
                    epgDao.getProgramsForChannelFlexibleSync(
                        channelTvgId = tvgId,
                        canonicalKey = canonicalKey,
                        channelName = name
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }

            if (fromRoom.isNotEmpty()) {
                roomProgramsForChannel = fromRoom
            } else {
                val now = System.currentTimeMillis()
                roomProgramsForChannel = FreeEpgService.getScheduleForWindow(
                    tvgId = selectedChannel.epgId ?: selectedChannel.tvgId,
                    channelName = name,
                    windowStartMs = now - (36 * 3600_000L),
                    windowEndMs = now + (72 * 3600_000L),
                    timezoneId = settings.epgTimezone
                )
            }
        }
    }

    // Filter programs for the selected day and category/search
    val filteredPrograms = remember(roomProgramsForChannel, selectedDayOffset, searchQuery, selectedCategory, settings.epgTimezone) {
        val targetCal = Calendar.getInstance(TimeZone.getTimeZone(settings.epgTimezone)).apply {
            add(Calendar.DAY_OF_YEAR, selectedDayOffset)
        }
        val targetDayOfYear = targetCal.get(Calendar.DAY_OF_YEAR)
        val targetYear = targetCal.get(Calendar.YEAR)

        roomProgramsForChannel.filter { prog ->
            val progCal = Calendar.getInstance(TimeZone.getTimeZone(settings.epgTimezone)).apply {
                timeInMillis = prog.startTime
            }
            val matchesDay = progCal.get(Calendar.DAY_OF_YEAR) == targetDayOfYear &&
                    progCal.get(Calendar.YEAR) == targetYear

            val matchesSearch = if (searchQuery.isBlank()) true else {
                prog.title.contains(searchQuery, ignoreCase = true) ||
                        prog.description.contains(searchQuery, ignoreCase = true) ||
                        prog.channelName.contains(searchQuery, ignoreCase = true)
            }

            val matchesCategory = if (selectedCategory == "Alle") true else {
                when {
                    selectedCategory.contains("Film") -> prog.category.contains("Film", ignoreCase = true) || prog.category.contains("Movie", ignoreCase = true) || prog.category.contains("Serie", ignoreCase = true)
                    selectedCategory.contains("Sport") -> prog.category.contains("Sport", ignoreCase = true) || prog.category.contains("Fußball", ignoreCase = true)
                    selectedCategory.contains("Nachrichten") -> prog.category.contains("Nachricht", ignoreCase = true) || prog.category.contains("News", ignoreCase = true) || prog.category.contains("Journal", ignoreCase = true)
                    selectedCategory.contains("Doku") -> prog.category.contains("Doku", ignoreCase = true) || prog.category.contains("Wissen", ignoreCase = true)
                    selectedCategory.contains("Show") -> prog.category.contains("Show", ignoreCase = true) || prog.category.contains("Quiz", ignoreCase = true) || prog.category.contains("Magazin", ignoreCase = true)
                    selectedCategory.contains("Kinder") -> prog.category.contains("Kind", ignoreCase = true) || prog.category.contains("Animation", ignoreCase = true)
                    else -> true
                }
            }

            matchesDay && matchesSearch && matchesCategory
        }.sortedBy { it.startTime }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkSurface)
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .testTag("epg_schedule_screen")
    ) {
        // TOP APP BAR / HEADER
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(GoldPrimary.copy(alpha = 0.15f))
                        .border(1.dp, GoldPrimary.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = "EPG",
                        tint = GoldPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = if (isEnglish) "EPG Schedule & Guide" else "EPG Programmzeitschrift",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldLight
                    )
                    Text(
                        text = if (isEnglish) {
                            "Room DB: $dbProgramCount programs • 24/7 Live EPG"
                        } else {
                            "Datenbank: $dbProgramCount Sendungen • 24/7 EPG"
                        },
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }

            // Right Action Controls
            Row(verticalAlignment = Alignment.CenterVertically) {
                // View Mode Toggle Button (Timeline vs Grid)
                IconButton(
                    onClick = {
                        viewMode = if (viewMode == EpgViewMode.CHANNEL_TIMELINE) EpgViewMode.TIME_GRID else EpgViewMode.CHANNEL_TIMELINE
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceElevated)
                        .border(1.dp, BorderGlow.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .testTag("epg_toggle_view_mode")
                ) {
                    Icon(
                        imageVector = if (viewMode == EpgViewMode.CHANNEL_TIMELINE) Icons.Default.ViewModule else Icons.Default.ViewAgenda,
                        contentDescription = "Ansicht wechseln",
                        tint = GoldLight,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // EPG Refresh Button
                val isSyncing = syncState is EpgSyncState.Syncing
                Button(
                    onClick = { onTriggerSync(settings.customEpgUrl) },
                    enabled = !isSyncing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldPrimary,
                        contentColor = ObsidianBlack,
                        disabledContainerColor = DarkNavyCard,
                        disabledContentColor = TextMuted
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("epg_header_refresh_button")
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = GoldLight,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "EPG Aktualisieren",
                            tint = ObsidianBlack,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isSyncing) (if (isEnglish) "Syncing..." else "Laden...") else (if (isEnglish) "Refresh" else "Aktualisieren"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ObsidianBlack
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // EPG Server Button
                Button(
                    onClick = { showServerDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkSurfaceElevated,
                        contentColor = GoldLight
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("epg_open_server_dialog")
                ) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = "Server",
                        tint = GoldPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isEnglish) "EPG URL" else "EPG URL",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // EPG Sources Management Button
                Button(
                    onClick = { showSourceDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkSurfaceElevated,
                        contentColor = GoldLight
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlow.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("epg_open_sources_dialog")
                ) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = "Quellen",
                        tint = AccentCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isEnglish) "Sources" else "Quellen",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (selectedChannel != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    // Manual Mapping Button
                    Button(
                        onClick = { showMappingDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkSurfaceElevated,
                            contentColor = GoldLight
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlow.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("epg_open_mapping_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Zuordnung",
                            tint = GoldPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isEnglish) "Mapping" else "Zuordnung",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (onClose != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(DarkSurfaceElevated)
                            .border(1.dp, GoldPrimary.copy(alpha = 0.5f), CircleShape)
                            .testTag("epg_close_overlay_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = if (isEnglish) "Close EPG Guide" else "EPG Guide schließen",
                            tint = GoldLight,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // SYNC STATUS BANNER (if syncing or error)
        when (syncState) {
            is EpgSyncState.Syncing -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = AccentCyan,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = syncState.message,
                            fontSize = 12.sp,
                            color = AccentCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            is EpgSyncState.Error -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentRose.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = syncState.message,
                            fontSize = 11.sp,
                            color = AccentRose,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        TextButton(
                            onClick = { onTriggerSync(settings.customEpgUrl) },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Wiederholen", fontSize = 11.sp, color = GoldLight)
                        }
                    }
                }
            }
            else -> {}
        }

        // SEARCH BAR
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = if (isEnglish) "Search programs, movies, shows..." else "Sendung, Film oder Serie suchen...",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Suche",
                    tint = GoldPrimary,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Löschen",
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .height(48.dp)
                .testTag("epg_search_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DarkSurfaceElevated,
                unfocusedContainerColor = DarkSurfaceElevated,
                focusedBorderColor = GoldPrimary,
                unfocusedBorderColor = BorderGlow.copy(alpha = 0.3f),
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(10.dp)
        )

        // DAY SELECTOR PILLS
        val days = listOf(
            Pair(-1, if (isEnglish) "Yesterday" else "Gestern"),
            Pair(0, if (isEnglish) "Today" else "Heute"),
            Pair(1, if (isEnglish) "Tomorrow" else "Morgen"),
            Pair(2, if (isEnglish) "Day After" else "Übermorgen")
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            days.forEach { (offset, label) ->
                val isSelected = selectedDayOffset == offset
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) Brush.horizontalGradient(listOf(GoldDark, GoldPrimary))
                            else Brush.horizontalGradient(listOf(DarkSurfaceElevated, DarkSurfaceElevated))
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) GoldLight else BorderGlow.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedDayOffset = offset }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) ObsidianBlack else TextSecondary
                    )
                }
            }
        }

        // TIME SLOT QUICK JUMP BAR
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            items(TIME_SLOT_JUMPS.indices.toList()) { index ->
                val slot = TIME_SLOT_JUMPS[index]
                val isSelected = selectedTimeSlotIndex == index
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) GoldPrimary.copy(alpha = 0.2f) else DarkSurfaceElevated)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) GoldPrimary else BorderGlow.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { selectedTimeSlotIndex = index }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = slot.label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) GoldLight else TextSecondary
                    )
                }
            }
        }

        // CATEGORY FILTER CHIPS
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            items(EPG_CATEGORIES) { cat ->
                val isSelected = selectedCategory == cat
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) DarkSurfaceElevated else Color.Transparent)
                        .border(
                            width = 0.5.dp,
                            color = if (isSelected) AccentCyan else BorderGlow.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable { selectedCategory = cat }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = cat,
                        fontSize = 10.sp,
                        color = if (isSelected) AccentCyan else TextMuted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // MAIN CONTENT (TIMELINE vs GRID VIEW)
        if (viewMode == EpgViewMode.CHANNEL_TIMELINE) {
            // Horizontal Channel Selector
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                items(
                    items = liveChannels,
                    key = { it.id },
                    contentType = { "epg_channel_tab" }
                ) { ch ->
                    val isSelected = ch.id == selectedChannel?.id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) DarkNavyCard else DarkSurfaceElevated)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) GoldPrimary else BorderGlow.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { selectedChannelId = ch.id }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("epg_channel_tab_${ch.id}")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!ch.logoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = ch.logoUrl,
                                    contentDescription = ch.name,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(GoldPrimary.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tv,
                                        contentDescription = null,
                                        tint = GoldPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                            }

                            if (ch.isFavorite) {
                                Text(
                                    text = "❤️",
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }

                            Text(
                                text = ch.name,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) GoldLight else if (ch.isFavorite) AccentRose else TextSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Selected Channel Active Bar with Direct Play
            if (selectedChannel != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkNavyCard)
                        .border(1.dp, GoldPrimary.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = selectedChannel.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = GoldLight,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (selectedChannel.isFavorite) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(AccentRose.copy(alpha = 0.2f))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "❤️ FAVORIT",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentRose
                                    )
                                }
                            }
                        }
                        Text(
                            text = "${selectedChannel.groupTitle} • ${filteredPrograms.size} Sendungen",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onToggleFavorite != null) {
                            IconButton(
                                onClick = { onToggleFavorite(selectedChannel) },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selectedChannel.isFavorite) AccentRose.copy(alpha = 0.2f) else DarkSurface)
                                    .border(
                                        1.dp,
                                        if (selectedChannel.isFavorite) AccentRose.copy(alpha = 0.6f) else BorderGlow.copy(alpha = 0.3f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .testTag("epg_favorite_channel_btn")
                            ) {
                                Icon(
                                    imageVector = if (selectedChannel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = if (selectedChannel.isFavorite) "Aus Favoriten entfernen" else "Zu Favoriten hinzufügen",
                                    tint = if (selectedChannel.isFavorite) AccentRose else GoldLight,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        Button(
                            onClick = { handleSelectChannel(selectedChannel) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentEmerald,
                                contentColor = ObsidianBlack
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("epg_play_channel_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Abspielen",
                                tint = ObsidianBlack,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isEnglish) "Tune In" else "Kanal starten",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Program Schedule List for Selected Channel
            if (filteredPrograms.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isEnglish) "No programs found for this selection." else "Keine Sendungen für diesen Zeitraum gefunden.",
                            fontSize = 13.sp,
                            color = TextMuted
                        )
                    }
                }
            } else {
                val now = System.currentTimeMillis()
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("epg_program_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(
                        items = filteredPrograms,
                        key = { it.id },
                        contentType = { "epg_program" }
                    ) { prog ->
                        val isCurrent = now in prog.startTime..prog.endTime
                        EpgProgramCard(
                            program = prog,
                            isCurrent = isCurrent,
                            timezoneId = settings.epgTimezone,
                            onClick = { selectedProgramForDetail = prog },
                            onPlayChannel = {
                                if (selectedChannel != null) handleSelectChannel(selectedChannel)
                            }
                        )
                    }
                }
            }
        } else {
            // TIME GRID VIEW (All channels for selected time)
            val now = System.currentTimeMillis()
            val targetTime = if (selectedTimeSlotIndex == 0) now else {
                val slot = TIME_SLOT_JUMPS[selectedTimeSlotIndex]
                val cal = Calendar.getInstance(TimeZone.getTimeZone(settings.epgTimezone)).apply {
                    add(Calendar.DAY_OF_YEAR, selectedDayOffset)
                    set(Calendar.HOUR_OF_DAY, slot.hour)
                    set(Calendar.MINUTE, slot.minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                cal.timeInMillis
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("epg_grid_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(
                    items = liveChannels,
                    key = { it.id },
                    contentType = { "epg_grid_channel" }
                ) { ch ->
                    val (currentProg, nextProg) = remember(ch.id, targetTime, settings.epgTimezone, settings.epgTimeOffsetHours) {
                        FreeEpgService.getCurrentAndNextProgram(
                            tvgId = ch.tvgId,
                            channelName = ch.name,
                            timezoneId = settings.epgTimezone,
                            offsetHours = settings.epgTimeOffsetHours,
                            targetTimeMs = targetTime
                        )
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, BorderGlow.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            .clickable { handleSelectChannel(ch) }
                            .testTag("epg_grid_item_${ch.id}"),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Logo Box
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ObsidianBlack),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!ch.logoUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = ch.logoUrl,
                                        contentDescription = ch.name,
                                        modifier = Modifier.size(40.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Tv,
                                        contentDescription = null,
                                        tint = GoldPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Channel & Show Info
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = ch.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GoldLight,
                                        maxLines = 1
                                    )

                                    if (currentProg != null) {
                                        Text(
                                            text = FreeEpgService.formatProgramRange(currentProg.startTime, currentProg.endTime, settings.epgTimezone),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = AccentCyan
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = currentProg?.title ?: "Live Übertragung",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (currentProg != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { currentProg.progress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = GoldPrimary,
                                        trackColor = ObsidianBlack
                                    )
                                }

                                if (nextProg != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Danach: ${nextProg.title} (${FreeEpgService.formatProgramTime(nextProg.startTime, settings.epgTimezone)})",
                                        fontSize = 10.sp,
                                        color = TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Play Button
                            IconButton(
                                onClick = { handleSelectChannel(ch) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(GoldPrimary.copy(alpha = 0.15f))
                                    .border(1.dp, GoldPrimary.copy(alpha = 0.4f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Abspielen",
                                    tint = GoldLight,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // PROGRAM DETAILS MODAL BOTTOM SHEET
    if (selectedProgramForDetail != null) {
        val prog = selectedProgramForDetail!!
        val isCurrent = System.currentTimeMillis() in prog.startTime..prog.endTime
        val durationMins = ((prog.endTime - prog.startTime) / (60 * 1000)).toInt().coerceAtLeast(1)

        AlertDialog(
            onDismissRequest = { selectedProgramForDetail = null },
            confirmButton = {
                Button(
                    onClick = {
                        selectedProgramForDetail = null
                        if (selectedChannel != null) handleSelectChannel(selectedChannel)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentEmerald,
                        contentColor = ObsidianBlack
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Kanal jetzt abspielen", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedProgramForDetail = null }) {
                    Text("Schließen", color = TextSecondary)
                }
            },
            title = {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isCurrent) AccentRose.copy(alpha = 0.2f) else AccentCyan.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isCurrent) "🔴 JETZT LIVE" else prog.category,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isCurrent) AccentRose else AccentCyan
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${durationMins} Min.",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { selectedProgramForDetail = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = if (isEnglish) "Close" else "Schließen",
                                    tint = TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = prog.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldLight
                    )
                }
            },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = FreeEpgService.formatProgramRange(prog.startTime, prog.endTime, settings.epgTimezone),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Sender: ${selectedChannel?.name ?: prog.channelName}",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    if (isCurrent) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { prog.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = GoldPrimary,
                            trackColor = ObsidianBlack
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (prog.description.isNotBlank()) prog.description else "Keine ausführliche Beschreibung für diese Sendung hinterlegt.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                }
            },
            containerColor = DarkNavyCard,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // EPG SERVER MANAGER DIALOG
    if (showServerDialog) {
        var customUrlInput by remember { mutableStateOf(settings.customEpgUrl) }

        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEnglish) "EPG Server Management" else "EPG Server & XMLTV Feeds",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldLight
                        )
                    }
                    IconButton(
                        onClick = { showServerDialog = false },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = if (isEnglish) "Close" else "Schließen",
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Lokale Room-Datenbank: $dbProgramCount Sendungen gespeichert.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentEmerald
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Empfohlene EPG-Server (1-Klick Auswahl):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    FreeEpgService.DEFAULT_EPG_SERVERS.forEach { server ->
                        val isCurrent = customUrlInput.trim() == server.url.trim()
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = 1.dp,
                                    color = if (isCurrent) GoldPrimary else BorderGlow.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    customUrlInput = server.url
                                    onUpdateCustomEpgUrl(server.url)
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCurrent) DarkNavyCard else DarkSurfaceElevated
                            )
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = server.name,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrent) GoldLight else TextPrimary
                                    )
                                    Text(
                                        text = server.country,
                                        fontSize = 10.sp,
                                        color = AccentCyan
                                    )
                                }
                                Text(
                                    text = server.url,
                                    fontSize = 9.sp,
                                    color = TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEnglish) "Custom EPG URL (.xml or .xml.gz):" else "Eigene EPG-URL (.xml oder .xml.gz):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )

                        val clipManager = LocalClipboardManager.current
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    val clipText = clipManager.getText()?.text?.trim()
                                    if (!clipText.isNullOrBlank()) {
                                        customUrlInput = clipText
                                        onUpdateCustomEpgUrl(clipText)
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste",
                                    tint = AccentCyan,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Einfügen", fontSize = 11.sp, color = AccentCyan)
                            }

                            if (customUrlInput.isNotBlank()) {
                                TextButton(
                                    onClick = {
                                        customUrlInput = ""
                                        onUpdateCustomEpgUrl("")
                                    },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = TextMuted,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("Leeren", fontSize = 11.sp, color = TextMuted)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = customUrlInput,
                        onValueChange = {
                            customUrlInput = it
                            onUpdateCustomEpgUrl(it)
                        },
                        placeholder = { Text("https://www.free-epg.de/api/epg/de.xml.gz", fontSize = 11.sp, color = TextMuted) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("epg_dialog_custom_url_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = BorderGlow.copy(alpha = 0.3f),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = false,
                        maxLines = 2
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onTriggerSync(customUrlInput)
                        showServerDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldPrimary,
                        contentColor = ObsidianBlack
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Jetzt synchronisieren", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerDialog = false }) {
                    Text("Abbrechen", color = TextSecondary)
                }
            },
            containerColor = DarkSurfaceElevated,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // EPG SOURCE MANAGEMENT DIALOG
    if (showSourceDialog) {
        EpgSourceManagementDialog(
            sources = epgSources,
            onAddSource = { name, url, retDays -> onAddEpgSource?.invoke(name, url, retDays) },
            onToggleSource = { id, enabled -> onToggleEpgSource?.invoke(id, enabled) },
            onDeleteSource = { id -> onDeleteEpgSource?.invoke(id) },
            onSyncSource = { id -> onSyncEpgSource?.invoke(id) },
            onCleanupEpg = { days -> onCleanupEpg?.invoke(days) },
            onDismiss = { showSourceDialog = false },
            isEnglish = isEnglish
        )
    }

    // MANUAL EPG MAPPING DIALOG
    if (showMappingDialog && selectedChannel != null) {
        val currentMapping = manualMappings[selectedChannel.id] ?: selectedChannel.tvgId?.let { manualMappings[it] }
        ManualEpgMappingDialog(
            channel = selectedChannel,
            currentMapping = currentMapping,
            onSaveMapping = { epgId, mappedName ->
                onSaveManualMapping?.invoke(selectedChannel.id, selectedChannel.name, selectedChannel.tvgId, epgId, mappedName)
            },
            onRemoveMapping = {
                onRemoveManualMapping?.invoke(selectedChannel.id)
            },
            onDismiss = { showMappingDialog = false },
            isEnglish = isEnglish
        )
    }
}

@Composable
private fun EpgProgramCard(
    program: EpgProgram,
    isCurrent: Boolean,
    timezoneId: String,
    onClick: () -> Unit,
    onPlayChannel: () -> Unit
) {
    val durationMins = ((program.endTime - program.startTime) / (60 * 1000)).toInt().coerceAtLeast(1)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isCurrent) 1.5.dp else 0.5.dp,
                color = if (isCurrent) GoldPrimary else BorderGlow.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .testTag("epg_program_item_${program.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) DarkNavyCard else DarkSurfaceElevated
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Top Row: Time Range, Duration, Category Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = FreeEpgService.formatProgramTime(program.startTime, timezoneId),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrent) GoldLight else AccentCyan
                    )
                    Text(
                        text = " - ${FreeEpgService.formatProgramTime(program.endTime, timezoneId)}",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "(${durationMins} Min.)",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }

                if (isCurrent) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(AccentRose.copy(alpha = 0.2f))
                            .border(0.5.dp, AccentRose.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "🔴 JETZT LIVE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentRose
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkSurface)
                            .border(0.5.dp, BorderGlow.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = program.category,
                            fontSize = 9.sp,
                            color = TextMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Program Title
            Text(
                text = program.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isCurrent) GoldLight else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Progress bar if live
            if (isCurrent) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LinearProgressIndicator(
                        progress = { program.progress },
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = GoldPrimary,
                        trackColor = ObsidianBlack
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${(program.progress * 100).toInt()}%",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldLight
                    )
                }
            }

            // Description snippet
            if (program.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = program.description,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
fun EpgOverlayDialog(
    channels: List<ChannelItem>,
    onSelectChannel: (ChannelItem) -> Unit,
    onClose: () -> Unit,
    epgDao: EpgDao,
    syncState: EpgSyncState,
    onTriggerSync: (String) -> Unit,
    settings: AppSettings,
    onUpdateEpgTimezone: (String) -> Unit,
    onUpdateCustomEpgUrl: (String) -> Unit,
    isEnglish: Boolean = false,
    onToggleFavorite: ((ChannelItem) -> Unit)? = null
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(horizontal = 8.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.5.dp, GoldPrimary.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                EpgScheduleScreen(
                    channels = channels,
                    onSelectChannel = onSelectChannel,
                    epgDao = epgDao,
                    syncState = syncState,
                    onTriggerSync = onTriggerSync,
                    settings = settings,
                    onUpdateEpgTimezone = onUpdateEpgTimezone,
                    onUpdateCustomEpgUrl = onUpdateCustomEpgUrl,
                    isEnglish = isEnglish,
                    onClose = onClose,
                    autoCloseOnSelect = true,
                    onToggleFavorite = onToggleFavorite
                )
            }
        }
    }
}
