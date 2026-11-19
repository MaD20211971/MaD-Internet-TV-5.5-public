package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ChannelItem
import com.example.data.radio.RadioStationEntity
import com.example.data.radio.RadioSyncState
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentRose
import com.example.ui.theme.BorderGlow
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.DarkNavyCard
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.GoldLight
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class RadioRegionFilter(val labelDe: String, val labelEn: String) {
    ALL("Alle (Deutschrock oben)", "All (Deutschrock first)"),
    DEUTSCHROCK("🎸 Deutschrock", "🎸 Deutschrock"),
    FAVORITES("⭐ Favoriten", "⭐ Favorites"),
    GERMANY("🇩🇪 Deutschland", "🇩🇪 Germany"),
    AUSTRIA("🇦🇹 Österreich", "🇦🇹 Austria"),
    SWITZERLAND("🇨🇭 Schweiz", "🇨🇭 Switzerland"),
    ROCK_METAL("🤘 Rock & Metal", "🤘 Rock & Metal"),
    POP_CHARTS("🎵 Pop & Hits", "🎵 Pop & Hits"),
    SCHLAGER("🎉 Schlager", "🎉 Schlager"),
    INFO_NEWS("📰 Info & News", "📰 News & Talk"),
    ELECTRONIC("⚡ Electro & Dance", "⚡ Dance & EDM"),
    KLASSIK_KULTUR("🎻 Klassik & Kultur", "🎻 Classical"),
    IPTV_ONLY("📺 IPTV-Radio", "📺 IPTV Radio")
}

private enum class RadioSearchScope {
    LOCAL_DB,
    ONLINE_WORLDWIDE
}

private data class UnifiedRadioItem(
    val id: String,
    val name: String,
    val subtitle: String,
    val countryCode: String,
    val logoUrl: String?,
    val qualityBadge: String,
    val isMp3: Boolean,
    val isDeutschrock: Boolean,
    val isFavorite: Boolean,
    val isSavedLocally: Boolean,
    val isFromIptv: Boolean,
    val votes: Int,
    val radioBrowserEntity: RadioStationEntity? = null,
    val iptvChannel: ChannelItem? = null
)

@Composable
fun RadioExplorer(
    radioStations: List<ChannelItem>,
    localRadioBrowserStations: List<RadioStationEntity> = emptyList(),
    onlineSearchResults: List<RadioStationEntity> = emptyList(),
    syncState: RadioSyncState = RadioSyncState(),
    isSearchingOnline: Boolean = false,
    onlineSearchError: String? = null,
    onPlayRadio: (ChannelItem) -> Unit,
    onPlayRadioBrowserStation: (RadioStationEntity) -> Unit = { onPlayRadio(it.toChannelItem()) },
    onToggleFavorite: ((ChannelItem) -> Unit)? = null,
    onToggleRadioBrowserFavorite: (RadioStationEntity) -> Unit = {},
    onSyncRadioBrowser: () -> Unit = {},
    onSearchOnline: (query: String, onlyMp3: Boolean) -> Unit = { _, _ -> },
    onClearOnlineSearch: () -> Unit = {},
    onSaveStationLocally: (RadioStationEntity) -> Unit = {},
    onOpenRecordings: () -> Unit = {},
    isEnglish: Boolean = false,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf(RadioRegionFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var searchScope by remember { mutableStateOf(RadioSearchScope.LOCAL_DB) }
    var onlyMp3Filter by remember { mutableStateOf(false) }

    val localUuidSet = remember(localRadioBrowserStations) {
        localRadioBrowserStations.map { it.stationUuid }.toHashSet()
    }

    LaunchedEffect(searchScope, searchQuery, onlyMp3Filter) {
        if (searchScope == RadioSearchScope.ONLINE_WORLDWIDE) {
            if (searchQuery.trim().length >= 2) {
                onSearchOnline(searchQuery, onlyMp3Filter)
            } else {
                onClearOnlineSearch()
            }
        }
    }

    val syncTransition = rememberInfiniteTransition(label = "radio_sync_spin")
    val syncRotation by syncTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(900, easing = LinearEasing)),
        label = "sync_rotation"
    )

    // Combine local Room radio-browser.info stations + existing IPTV radio stations
    val combinedLocalItems = remember(localRadioBrowserStations, radioStations) {
        val list = ArrayList<UnifiedRadioItem>(localRadioBrowserStations.size + radioStations.size)
        for (st in localRadioBrowserStations) {
            list.add(
                UnifiedRadioItem(
                    id = "rb_${st.stationUuid}",
                    name = st.name,
                    subtitle = st.displayCategory(),
                    countryCode = st.countryCode,
                    logoUrl = st.favicon.takeIf { it.isNotBlank() },
                    qualityBadge = st.qualityBadge(),
                    isMp3 = st.isMp3Stream(),
                    isDeutschrock = st.isDeutschrock,
                    isFavorite = st.isFavorite,
                    isSavedLocally = true,
                    isFromIptv = false,
                    votes = st.votes,
                    radioBrowserEntity = st
                )
            )
        }
        for (ch in radioStations) {
            val isDr = RadioStationEntity.detectDeutschrock(ch.name, ch.groupTitle)
            list.add(
                UnifiedRadioItem(
                    id = "iptv_${ch.id}",
                    name = ch.name,
                    subtitle = ch.groupTitle.ifBlank { "IPTV Radio" },
                    countryCode = ch.countryCode.ifBlank { if (ch.isDach) "DE" else "INT" },
                    logoUrl = ch.logoUrl,
                    qualityBadge = ch.resolutionBadge.ifBlank { "AUDIO" },
                    isMp3 = ch.streamUrl.contains(".mp3", ignoreCase = true) || ch.resolutionBadge.contains("MP3", ignoreCase = true),
                    isDeutschrock = isDr,
                    isFavorite = ch.isFavorite,
                    isSavedLocally = true,
                    isFromIptv = true,
                    votes = 0,
                    iptvChannel = ch
                )
            )
        }
        list
    }

    val displayedItems = remember(
        searchScope,
        combinedLocalItems,
        onlineSearchResults,
        localUuidSet,
        selectedFilter,
        searchQuery,
        onlyMp3Filter
    ) {
        if (searchScope == RadioSearchScope.ONLINE_WORLDWIDE) {
            onlineSearchResults
                .filter { !onlyMp3Filter || it.isMp3Stream() }
                .map { st ->
                    UnifiedRadioItem(
                        id = "online_${st.stationUuid}",
                        name = st.name,
                        subtitle = st.displayCategory(),
                        countryCode = st.countryCode,
                        logoUrl = st.favicon.takeIf { it.isNotBlank() },
                        qualityBadge = st.qualityBadge(),
                        isMp3 = st.isMp3Stream(),
                        isDeutschrock = st.isDeutschrock,
                        isFavorite = st.isFavorite,
                        isSavedLocally = st.stationUuid in localUuidSet,
                        isFromIptv = false,
                        votes = st.votes,
                        radioBrowserEntity = st
                    )
                }
        } else {
            var list: List<UnifiedRadioItem> = when (selectedFilter) {
                RadioRegionFilter.ALL -> combinedLocalItems
                RadioRegionFilter.DEUTSCHROCK -> combinedLocalItems.filter { it.isDeutschrock }
                RadioRegionFilter.FAVORITES -> combinedLocalItems.filter { it.isFavorite }
                RadioRegionFilter.GERMANY -> combinedLocalItems.filter { it.countryCode == "DE" }
                RadioRegionFilter.AUSTRIA -> combinedLocalItems.filter { it.countryCode == "AT" }
                RadioRegionFilter.SWITZERLAND -> combinedLocalItems.filter { it.countryCode == "CH" }
                RadioRegionFilter.ROCK_METAL -> combinedLocalItems.filter {
                    val g = "${it.name} ${it.subtitle}".uppercase()
                    it.isDeutschrock || g.contains("ROCK") || g.contains("METAL") || g.contains("ALTERNATIVE") || g.contains("PUNK")
                }
                RadioRegionFilter.POP_CHARTS -> combinedLocalItems.filter {
                    val g = "${it.name} ${it.subtitle}".uppercase()
                    g.contains("POP") || g.contains("HIT") || g.contains("CHARTS") || g.contains("TOP") || g.contains("ANTENNE")
                }
                RadioRegionFilter.SCHLAGER -> combinedLocalItems.filter {
                    val g = "${it.name} ${it.subtitle}".uppercase()
                    g.contains("SCHLAGER") || g.contains("VOLK") || g.contains("HEIMAT") || g.contains("PALOMA") || g.contains("BALLERMANN")
                }
                RadioRegionFilter.INFO_NEWS -> combinedLocalItems.filter {
                    val g = "${it.name} ${it.subtitle}".uppercase()
                    g.contains("INFO") || g.contains("NEWS") || g.contains("AKTUELL") || g.contains("DLF") || g.contains("TALK")
                }
                RadioRegionFilter.ELECTRONIC -> combinedLocalItems.filter {
                    val g = "${it.name} ${it.subtitle}".uppercase()
                    g.contains("ELECTRO") || g.contains("DANCE") || g.contains("HOUSE") || g.contains("TECHNO") || g.contains("EDM")
                }
                RadioRegionFilter.KLASSIK_KULTUR -> combinedLocalItems.filter {
                    val g = "${it.name} ${it.subtitle}".uppercase()
                    g.contains("KLASSIK") || g.contains("KULTUR") || g.contains("JAZZ") || g.contains("CLASSIC")
                }
                RadioRegionFilter.IPTV_ONLY -> combinedLocalItems.filter { it.isFromIptv }
            }

            if (onlyMp3Filter) {
                list = list.filter { it.isMp3 }
            }

            if (searchQuery.isNotBlank()) {
                val q = searchQuery.trim().lowercase()
                list = list.filter {
                    it.name.lowercase().contains(q) ||
                        it.subtitle.lowercase().contains(q) ||
                        it.countryCode.lowercase().contains(q)
                }
            }

            // Deutschrock ALWAYS stands at the very top, then Favorites, then DE -> AT -> CH -> INT, then Votes
            list.sortedWith(
                compareByDescending<UnifiedRadioItem> { it.isDeutschrock }
                    .thenByDescending { it.isFavorite }
                    .thenBy {
                        when (it.countryCode) {
                            "DE" -> 0
                            "AT" -> 1
                            "CH" -> 2
                            else -> 3
                        }
                    }
                    .thenByDescending { it.votes }
                    .thenBy { it.name.lowercase() }
            )
        }
    }

    val lastSyncLabel = remember(syncState.lastSyncedAt, isEnglish) {
        if (syncState.lastSyncedAt > 0L) {
            val fmt = SimpleDateFormat("dd.MM. HH:mm", Locale.getDefault())
            if (isEnglish) "Synced ${fmt.format(Date(syncState.lastSyncedAt))}" else "Sync: ${fmt.format(Date(syncState.lastSyncedAt))}"
        } else {
            if (isEnglish) "Not synced yet" else "Noch nicht synchronisiert"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("radio_explorer_view")
    ) {
        // 1. HEADER WITH TITLE, RECORDINGS SHORTCUT & SYNC BUTTON
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
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
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(colors = listOf(AccentCyan, GoldPrimary))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radio,
                            contentDescription = "Radio",
                            tint = ObsidianBlack,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = if (isEnglish) "Radio • radio-browser.info" else "Radio • radio-browser.info",
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${displayedItems.size} ${if (isEnglish) "shown" else "angezeigt"} • ${localRadioBrowserStations.size} DB • $lastSyncLabel",
                            fontSize = 11.sp,
                            color = GoldLight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // MP3 Recordings Manager shortcut
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DarkSurface,
                        border = BorderStroke(1.dp, BorderGlow),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenRecordings() }
                            .testTag("radio_open_recordings_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = if (isEnglish) "Recordings" else "Aufnahmen",
                                tint = AccentEmerald,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "MP3",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }

                    // Sync with radio-browser.info button
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (syncState.isSyncing) GoldPrimary.copy(alpha = 0.25f) else AccentCyan.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, if (syncState.isSyncing) GoldPrimary else AccentCyan.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !syncState.isSyncing) { onSyncRadioBrowser() }
                            .testTag("radio_sync_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sync",
                                tint = if (syncState.isSyncing) GoldPrimary else AccentCyan,
                                modifier = Modifier
                                    .size(15.dp)
                                    .rotate(if (syncState.isSyncing) syncRotation else 0f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (syncState.isSyncing) "Sync..." else "Sync",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (syncState.isSyncing) GoldLight else AccentCyan
                            )
                        }
                    }
                }
            }

            // Sync status or error banner
            AnimatedVisibility(
                visible = syncState.isSyncing || !syncState.errorMessage.isNullOrBlank(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                val isErr = !syncState.errorMessage.isNullOrBlank() && !syncState.isSyncing
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isErr) AccentRose.copy(alpha = 0.15f) else DarkSurfaceElevated,
                    border = BorderStroke(1.dp, if (isErr) AccentRose.copy(alpha = 0.5f) else GoldPrimary.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (syncState.isSyncing) {
                            CircularProgressIndicator(
                                color = GoldPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Text(
                            text = if (isErr) syncState.errorMessage.orEmpty() else syncState.statusText.ifBlank { "Synchronisiere radio-browser.info..." },
                            fontSize = 11.5.sp,
                            color = if (isErr) AccentRose else GoldLight,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. SEARCH BAR (LOCAL DB + WORLDWIDE ONLINE RADIO-BROWSER.INFO)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("radio_search_input"),
                placeholder = {
                    Text(
                        text = if (searchScope == RadioSearchScope.ONLINE_WORLDWIDE) {
                            if (isEnglish) "Search worldwide on radio-browser.info (min 2 chars)..." else "Weltweit auf radio-browser.info suchen (z. B. Deutschrock, Rock, 80er)..."
                        } else {
                            if (isEnglish) "Filter local radio database..." else "Lokale Radio-Datenbank filtern (Deutschrock, 1LIVE, BOB...)..."
                        },
                        fontSize = 12.5.sp,
                        color = TextMuted
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (searchScope == RadioSearchScope.ONLINE_WORLDWIDE) Icons.Default.Public else Icons.Default.Search,
                        contentDescription = "Suche",
                        tint = if (searchScope == RadioSearchScope.ONLINE_WORLDWIDE) AccentCyan else GoldPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                },
                trailingIcon = {
                    if (isSearchingOnline) {
                        CircularProgressIndicator(
                            color = AccentCyan,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                searchQuery = ""
                                onClearOnlineSearch()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Leeren",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = DarkSurfaceElevated,
                    unfocusedContainerColor = DarkSurface,
                    focusedBorderColor = if (searchScope == RadioSearchScope.ONLINE_WORLDWIDE) AccentCyan else GoldPrimary,
                    unfocusedBorderColor = BorderGlow,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 3. SEARCH SCOPE TOGGLE (LOCAL DB vs ONLINE RADIO-BROWSER.INFO) + MP3 ONLY TOGGLE
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioScopePill(
                    icon = Icons.Default.Storage,
                    label = if (isEnglish) "Local DB (${localRadioBrowserStations.size})" else "Lokal DB (${localRadioBrowserStations.size})",
                    selected = searchScope == RadioSearchScope.LOCAL_DB,
                    accent = GoldPrimary,
                    testTag = "radio_scope_local",
                    onClick = { searchScope = RadioSearchScope.LOCAL_DB }
                )

                RadioScopePill(
                    icon = Icons.Default.Public,
                    label = if (isEnglish) "Online Search" else "Online-Suche",
                    selected = searchScope == RadioSearchScope.ONLINE_WORLDWIDE,
                    accent = AccentCyan,
                    testTag = "radio_scope_online",
                    onClick = { searchScope = RadioSearchScope.ONLINE_WORLDWIDE }
                )

                Spacer(modifier = Modifier.weight(1f))

                // MP3 Only Toggle
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (onlyMp3Filter) AccentEmerald.copy(alpha = 0.22f) else DarkSurface,
                    border = BorderStroke(1.dp, if (onlyMp3Filter) AccentEmerald else BorderSubtle),
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onlyMp3Filter = !onlyMp3Filter }
                        .testTag("radio_filter_only_mp3")
                ) {
                    Text(
                        text = if (onlyMp3Filter) "✓ Nur MP3" else "Nur MP3",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (onlyMp3Filter) AccentEmerald else TextSecondary,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // 4. CATEGORY FILTER CHIPS (LOCAL DB MODE)
        if (searchScope == RadioSearchScope.LOCAL_DB) {
            val availableFilters = remember(radioStations.isNotEmpty()) {
                RadioRegionFilter.values().filter {
                    it != RadioRegionFilter.IPTV_ONLY || radioStations.isNotEmpty()
                }
            }
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(availableFilters) { filter ->
                    val isSelected = selectedFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = filter },
                        label = {
                            Text(
                                text = if (isEnglish) filter.labelEn else filter.labelDe,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected || filter == RadioRegionFilter.DEUTSCHROCK) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (filter == RadioRegionFilter.DEUTSCHROCK) AccentAmber else GoldPrimary,
                            selectedLabelColor = ObsidianBlack,
                            containerColor = if (filter == RadioRegionFilter.DEUTSCHROCK) AccentAmber.copy(alpha = 0.14f) else DarkSurface,
                            labelColor = if (filter == RadioRegionFilter.DEUTSCHROCK) GoldLight else TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (filter == RadioRegionFilter.DEUTSCHROCK) AccentAmber.copy(alpha = 0.55f) else BorderGlow,
                            selectedBorderColor = GoldPrimary,
                            enabled = true,
                            selected = isSelected
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.testTag("radio_filter_${filter.name.lowercase()}")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 5. STATIONS GRID OR EMPTY / LOADING STATE
        if (searchScope == RadioSearchScope.ONLINE_WORLDWIDE && searchQuery.trim().length < 2) {
            RadioEmptyState(
                title = if (isEnglish) "Worldwide radio-browser.info Search" else "Weltweite radio-browser.info Suche",
                subtitle = if (isEnglish) {
                    "Type at least 2 characters above (e.g. Deutschrock, Rock Antenne, Jazz, BBC) to search 40,000+ stations online and save favorites locally."
                } else {
                    "Gib oben mindestens 2 Zeichen ein (z. B. Deutschrock, Rock Antenne, Onkelz, 80er), um weltweit über 40.000 Sender zu durchsuchen und lokal zu speichern."
                },
                actionLabel = if (isEnglish) "Search 'Deutschrock'" else "Nach „Deutschrock“ suchen",
                onAction = { searchQuery = "Deutschrock" }
            )
        } else if (!onlineSearchError.isNullOrBlank() && searchScope == RadioSearchScope.ONLINE_WORLDWIDE) {
            RadioEmptyState(
                title = if (isEnglish) "Online search failed" else "Online-Suche fehlgeschlagen",
                subtitle = onlineSearchError,
                actionLabel = if (isEnglish) "Retry" else "Erneut versuchen",
                onAction = { onSearchOnline(searchQuery, onlyMp3Filter) }
            )
        } else if (displayedItems.isEmpty()) {
            val isDbEmpty = localRadioBrowserStations.isEmpty() && searchScope == RadioSearchScope.LOCAL_DB
            RadioEmptyState(
                title = when {
                    syncState.isSyncing -> if (isEnglish) "Syncing radio-browser.info..." else "Synchronisiere radio-browser.info..."
                    isDbEmpty -> if (isEnglish) "Local radio database is empty" else "Lokale Radio-Datenbank ist noch leer"
                    else -> if (isEnglish) "No matching radio stations" else "Keine passenden Radiosender gefunden"
                },
                subtitle = when {
                    syncState.isSyncing -> syncState.statusText
                    isDbEmpty -> if (isEnglish) "Tap Sync to download Deutschrock and DACH stations into your local database." else "Tippe auf Sync, um Deutschrock- und DACH-Sender von radio-browser.info lokal zu speichern."
                    else -> if (isEnglish) "Try switching to Online Search to query all 40,000+ radio-browser.info stations." else "Wechsle auf „Online-Suche“, um alle 40.000+ Sender direkt auf radio-browser.info zu durchsuchen."
                },
                actionLabel = if (isDbEmpty) "Jetzt synchronisieren" else if (searchScope == RadioSearchScope.LOCAL_DB && searchQuery.isNotBlank()) "Weltweit online suchen" else null,
                onAction = {
                    if (isDbEmpty) {
                        onSyncRadioBrowser()
                    } else if (searchScope == RadioSearchScope.LOCAL_DB && searchQuery.isNotBlank()) {
                        searchScope = RadioSearchScope.ONLINE_WORLDWIDE
                    }
                }
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 164.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
                    .testTag("radio_grid"),
                contentPadding = PaddingValues(top = 4.dp, bottom = 84.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = displayedItems,
                    key = { it.id },
                    contentType = { "radio_card" }
                ) { item ->
                    UnifiedRadioCard(
                        item = item,
                        onPlay = {
                            item.radioBrowserEntity?.let { onPlayRadioBrowserStation(it) }
                                ?: item.iptvChannel?.let { onPlayRadio(it) }
                        },
                        onToggleFavorite = {
                            item.radioBrowserEntity?.let { onToggleRadioBrowserFavorite(it) }
                                ?: item.iptvChannel?.let { ch -> onToggleFavorite?.invoke(ch) }
                        },
                        onSaveLocally = if (!item.isSavedLocally && item.radioBrowserEntity != null) {
                            { onSaveStationLocally(item.radioBrowserEntity) }
                        } else {
                            null
                        },
                        isEnglish = isEnglish
                    )
                }
            }
        }
    }
}

@Composable
private fun RadioScopePill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    accent: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) accent.copy(alpha = 0.22f) else DarkSurface,
        border = BorderStroke(1.dp, if (selected) accent else BorderSubtle),
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) accent else TextSecondary,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) TextPrimary else TextSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun RadioEmptyState(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Radio,
                contentDescription = null,
                tint = GoldPrimary.copy(alpha = 0.7f),
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = TextMuted
            )
            if (actionLabel != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldPrimary,
                        contentColor = ObsidianBlack
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = actionLabel, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun UnifiedRadioCard(
    item: UnifiedRadioItem,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSaveLocally: (() -> Unit)? = null,
    isEnglish: Boolean = false
) {
    val cardBorder = if (item.isDeutschrock) AccentAmber.copy(alpha = 0.7f) else BorderGlow
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, cardBorder, RoundedCornerShape(14.dp))
            .clickable { onPlay() }
            .testTag("radio_card_${item.id}"),
        colors = CardDefaults.cardColors(containerColor = DarkNavyCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            // Top Row: Flag + Codec/Bitrate Badge + Favorite Star
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    val flag = when (item.countryCode) {
                        "DE" -> "🇩🇪"
                        "AT" -> "🇦🇹"
                        "CH" -> "🇨🇭"
                        else -> "🌍"
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = DarkSurfaceElevated
                    ) {
                        Text(
                            text = "$flag ${item.countryCode.take(3)}",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldLight,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    // Codec & Bitrate Badge (e.g. MP3 • 128k or MP3 • 32k)
                    val badgeColor = if (item.isMp3) AccentEmerald else AccentCyan
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeColor.copy(alpha = 0.16f),
                        border = BorderStroke(0.6.dp, badgeColor.copy(alpha = 0.55f))
                    ) {
                        Text(
                            text = item.qualityBadge,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = badgeColor,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Favorit",
                        tint = if (item.isFavorite) GoldPrimary else TextMuted.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (item.isDeutschrock) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AccentAmber.copy(alpha = 0.18f),
                    border = BorderStroke(0.7.dp, AccentAmber.copy(alpha = 0.65f))
                ) {
                    Text(
                        text = "🎸 Deutschrock",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = GoldLight,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Station Logo
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .align(Alignment.CenterHorizontally)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(colors = listOf(DarkSurfaceElevated, DarkSurface)))
                    .border(1.5.dp, if (item.isDeutschrock) AccentAmber else GoldPrimary.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (!item.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.logoUrl,
                        contentDescription = item.name,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Audiotrack,
                        contentDescription = null,
                        tint = GoldPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.name,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = item.subtitle,
                fontSize = 10.5.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom Actions: Play + optional Save to Local DB
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(GoldPrimary.copy(alpha = 0.16f))
                        .clickable { onPlay() }
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = if (isEnglish) "Play" else "Abspielen",
                        tint = GoldPrimary,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (isEnglish) "Listen" else "Anhören",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldPrimary
                    )
                }

                if (onSaveLocally != null) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AccentCyan.copy(alpha = 0.18f))
                            .border(0.8.dp, AccentCyan.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .clickable { onSaveLocally() }
                            .testTag("radio_save_local_${item.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = if (isEnglish) "Save locally" else "Lokal speichern",
                            tint = AccentCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else if (!item.isFromIptv && item.isSavedLocally) {
                    Icon(
                        imageVector = Icons.Default.BookmarkAdded,
                        contentDescription = if (isEnglish) "Saved locally" else "Lokal gespeichert",
                        tint = AccentEmerald.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
