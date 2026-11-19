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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.model.ChannelItem
import com.example.data.model.ChannelSortMode
import com.example.data.model.CountrySummary
import com.example.data.model.PlaylistEntity
import com.example.data.parser.ContentClassifier
import com.example.data.parser.CountryFolder
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
import com.example.ui.viewmodel.MainTab

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TopBarSection(
    playlists: List<PlaylistEntity>,
    activePlaylist: PlaylistEntity?,
    onPlaylistSelected: (String) -> Unit,
    onRefreshPlaylist: (PlaylistEntity) -> Unit,
    isRefreshing: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    liveCount: Int,
    favoriteCount: Int = 0,
    sportCount: Int = 0,
    adultCount: Int = 0,
    internationalCount: Int = 0,
    radioCount: Int = 0,
    movieCount: Int,
    seriesCount: Int,
    channels: List<ChannelItem> = emptyList(),
    precomputedCountryFolders: List<CountryFolder> = emptyList(),
    isEnglish: Boolean = false,
    countrySummary: CountrySummary = CountrySummary.EMPTY,
    disabledCountries: Set<String> = emptySet(),
    hiddenChannelIds: Set<String> = emptySet(),
    onToggleCountry: (String) -> Unit = {},
    onEnableAllCountries: () -> Unit = {},
    onDisableAllCountries: () -> Unit = {},
    onToggleChannelHidden: (String) -> Unit = {},
    onRemoveAllChannelsInCountry: (String) -> Unit = {},
    onRestoreAllChannelsInCountry: (String) -> Unit = {},
    onRestoreAllChannels: () -> Unit = {},
    onResetCountryAndChannelFilters: () -> Unit = {},
    onAddCustomChannel: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    onOpenAddPlaylist: () -> Unit,
    onOpenAddMacPortal: () -> Unit = {},
    onOpenImportAssistant: () -> Unit = {},
    onEditPlaylistRequested: (PlaylistEntity) -> Unit = {},
    onDeletePlaylistRequested: (PlaylistEntity) -> Unit = {},
    onDeleteAllPlaylistsRequested: () -> Unit = {},
    isTvMode: Boolean = false,
    compactMode: Boolean = true,
    channelSortMode: ChannelSortMode = ChannelSortMode.DACH_CANONICAL,
    onSortModeSelected: (ChannelSortMode) -> Unit = {},
    selectedCountryCode: String = "DE",
    onSelectCountry: (String) -> Unit = {},
    showPlaylistSelector: Boolean = true,
    onOpenSettings: () -> Unit = {},
    selectedTab: MainTab = MainTab.LIVE_TV
) {
    var showDeleteAllConfirmation by remember { mutableStateOf(false) }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    var playlistMenuSearch by remember { mutableStateOf("") }
    var playlistForActionsDialog by remember { mutableStateOf<PlaylistEntity?>(null) }
    var isSearchExpanded by remember { mutableStateOf(searchQuery.isNotEmpty()) }
    var isHeaderMinimized by remember { mutableStateOf(false) }
    var showSubFoldersRow by remember { mutableStateOf(true) }
    var showCountrySelectorDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "refresh_ring")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing)
        ),
        label = "rotation"
    )

    val showLiveFilterRibbon = (selectedTab == MainTab.LIVE_TV) && !isHeaderMinimized

    Surface(
        color = ObsidianBlack,
        tonalElevation = 4.dp,
        border = BorderStroke(0.7.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ObsidianBlack,
                            DarkNavyCard
                        )
                    )
                )
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            // =========================================================================
            // 1. KOMPAKTE 1-ZEILIGE HAUPT-KOMMANDOLEISTE (Kein Umbruch auf S25 Ultra!)
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Links: Kompaktes App-Emblem (auf Smartphones)
                if (!isTvMode) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(DarkSurfaceElevated, ObsidianBlack)
                                )
                            )
                            .border(1.dp, GoldPrimary.copy(alpha = 0.8f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.app_logo),
                            contentDescription = "MaD IPTV public",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Mitte-Links (flexibles Gewicht): Playlisten-Auswahl-Kapsel (immer 1-zeilig!)
                if (showPlaylistSelector) {
                    val activeIndex = playlists.indexOfFirst { it.id == activePlaylist?.id }
                    val activeNumPrefix = if (activeIndex >= 0) "#${activeIndex + 1} " else ""

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        DarkSurfaceElevated,
                                        DarkSurface
                                    )
                                )
                            )
                            .border(1.dp, BorderGlow, RoundedCornerShape(18.dp))
                            .combinedClickable(
                                onClick = { isDropdownExpanded = true },
                                onLongClick = {
                                    activePlaylist?.let { pl ->
                                        playlistForActionsDialog = pl
                                    }
                                }
                            )
                            .focusable()
                            .padding(horizontal = 10.dp)
                            .testTag("playlist_selector_dropdown"),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (activePlaylist != null) AccentEmerald else AccentAmber)
                                )
                                Icon(
                                    imageVector = Icons.Default.Tv,
                                    contentDescription = "Playlist",
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "$activeNumPrefix${activePlaylist?.getDisplayName() ?: if (isEnglish) "Select Playlist" else "Playliste wählen"}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                activePlaylist?.let { pl ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(GoldPrimary.copy(alpha = 0.18f))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = pl.type.name.take(6),
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = GoldLight,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Open",
                                tint = GoldLight,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                // Rechts: Kompakte Schnellaktions-Buttons (Live-Badge auf TV, Suche, Sync, Einstellungen, Filter)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isTvMode) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = DarkSurface,
                            border = BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(AccentEmerald)
                                )
                                Text(
                                    text = "$liveCount LIVE",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = AccentEmerald,
                                    fontSize = 10.5.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Suche Umschalten
                    IconButton(
                        onClick = {
                            isSearchExpanded = !isSearchExpanded
                            if (!isSearchExpanded && searchQuery.isNotEmpty()) {
                                onSearchQueryChange("")
                            }
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSearchExpanded || searchQuery.isNotEmpty()) GoldPrimary.copy(alpha = 0.2f)
                                else DarkSurface
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSearchExpanded || searchQuery.isNotEmpty()) GoldPrimary else BorderSubtle,
                                shape = CircleShape
                            )
                            .focusable()
                            .testTag("toggle_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Suche",
                            tint = if (isSearchExpanded || searchQuery.isNotEmpty()) GoldPrimary else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Playlist Aktualisieren Button
                    IconButton(
                        onClick = { activePlaylist?.let { onRefreshPlaylist(it) } },
                        enabled = !isRefreshing && activePlaylist != null,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(DarkSurface)
                            .border(1.dp, BorderSubtle, CircleShape)
                            .focusable()
                            .testTag("playlist_refresh_ring_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Aktualisieren",
                            tint = if (isRefreshing) GoldLight else GoldPrimary,
                            modifier = Modifier
                                .size(16.dp)
                                .rotate(if (isRefreshing) rotation else 0f)
                        )
                    }

                    // Einstellungen Button
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(DarkSurface)
                            .border(1.dp, BorderSubtle, CircleShape)
                            .focusable()
                            .testTag("topbar_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = if (isEnglish) "Settings" else "Einstellungen",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Filterleiste Minimieren / Erweitern (bei Live TV)
                    if (selectedTab == MainTab.LIVE_TV) {
                        IconButton(
                            onClick = { isHeaderMinimized = !isHeaderMinimized },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isHeaderMinimized) GoldPrimary.copy(alpha = 0.2f) else DarkSurface
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isHeaderMinimized) GoldPrimary else BorderSubtle,
                                    shape = CircleShape
                                )
                                .focusable()
                                .testTag("toggle_minimize_header")
                        ) {
                            Icon(
                                imageVector = if (isHeaderMinimized) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                contentDescription = if (isHeaderMinimized) "Filter einblenden" else "Filter ausblenden",
                                tint = if (isHeaderMinimized) GoldPrimary else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // 2. AUSKLAPPBARES GLOBALES SUCHFELD
            // =========================================================================
            AnimatedVisibility(
                visible = isSearchExpanded || searchQuery.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_input_field"),
                        placeholder = {
                            Text(
                                text = if (isEnglish) "Search channels, movies, series..." else "TV-Sender, Filme oder Serien sofort filtern...",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = GoldPrimary,
                                modifier = Modifier.size(17.dp)
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (searchQuery.isNotEmpty()) {
                                        onSearchQueryChange("")
                                    } else {
                                        isSearchExpanded = false
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkSurfaceElevated,
                            unfocusedContainerColor = DarkSurface,
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = BorderGlow,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }
            }

            // =========================================================================
            // 3. KONTEXTSENSITIVE LIVE-TV LÄNDER- & BOUQUET-LEISTE (Kompakt)
            // =========================================================================
            val countryFolders = remember(precomputedCountryFolders, disabledCountries) {
                if (disabledCountries.isEmpty()) {
                    precomputedCountryFolders
                } else {
                    precomputedCountryFolders.filter { it.code.uppercase() !in disabledCountries }
                }
            }

            val activeCountryCode = remember(selectedCategory, countryFolders) {
                when {
                    selectedCategory.equals("FAVORITEN", ignoreCase = true) || selectedCategory.equals("FAVORITES", ignoreCase = true) -> null
                    selectedCategory.equals("ALLE", ignoreCase = true) || selectedCategory.isBlank() -> null
                    countryFolders.any { it.code.equals(selectedCategory, ignoreCase = true) } -> selectedCategory.uppercase()
                    else -> {
                        val found = countryFolders.firstOrNull { folder ->
                            folder.subFolders.any { it.equals(selectedCategory, ignoreCase = true) }
                        }
                        found?.code ?: ContentClassifier.detectCountryForGroup(selectedCategory)
                    }
                }
            }

            AnimatedVisibility(
                visible = showLiveFilterRibbon,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(5.dp))

                    // Länder-, Favoriten- & Sortier-Kapseln (1 kompakte Zeile)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 0a. Länder-Manager Dialog
                        item {
                            val hasDisabled = disabledCountries.isNotEmpty()
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (hasDisabled) GoldPrimary.copy(alpha = 0.22f) else DarkSurface
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (hasDisabled) GoldPrimary else BorderSubtle,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable { showCountrySelectorDialog = true }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                    .testTag("open_country_manager_chip")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "🌐 ", fontSize = 11.sp)
                                    Text(
                                        text = if (isEnglish) "Countries" else "Länder",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (hasDisabled) GoldLight else TextPrimary
                                    )
                                    if (disabledCountries.isNotEmpty()) {
                                        Text(
                                            text = " (${ContentClassifier.ALL_KNOWN_COUNTRIES.size - disabledCountries.size})",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = GoldLight
                                        )
                                    }
                                }
                            }
                        }

                        // 0b. Sortierung Dialog
                        item {
                            val isCustomSort = channelSortMode != ChannelSortMode.DACH_CANONICAL
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (isCustomSort) GoldPrimary.copy(alpha = 0.22f) else DarkSurface
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isCustomSort) GoldPrimary else BorderSubtle,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable { showSortDialog = true }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                    .testTag("open_channel_sort_chip")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.SwapVert,
                                        contentDescription = "Sortierung öffnen",
                                        tint = if (isCustomSort) GoldLight else GoldPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = if (isEnglish) channelSortMode.titleEn else channelSortMode.titleDe,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCustomSort) GoldLight else TextPrimary
                                    )
                                }
                            }
                        }

                        // 1. Favoriten Chip
                        item {
                            val isFavSelected = selectedCategory.equals("FAVORITEN", ignoreCase = true) ||
                                selectedCategory.equals("FAVORITES", ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (isFavSelected) AccentRose else AccentRose.copy(alpha = 0.14f)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isFavSelected) AccentRose else AccentRose.copy(alpha = 0.45f),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable { onCategorySelected("FAVORITEN") }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                    .testTag("filter_chip_favoriten")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "♥ ", fontSize = 11.sp, color = if (isFavSelected) Color.White else AccentRose)
                                    Text(
                                        text = if (isEnglish) "Favorites" else "Favoriten",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isFavSelected) Color.White else AccentRose
                                    )
                                    if (favoriteCount > 0) {
                                        Text(
                                            text = " ($favoriteCount)",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isFavSelected) Color.White.copy(alpha = 0.9f) else TextSecondary
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Alle Sender Chip
                        item {
                            val isAlleSelected = (selectedCategory.equals("ALLE", ignoreCase = true) || selectedCategory.isBlank()) && activeCountryCode == null
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isAlleSelected) GoldPrimary else DarkSurface)
                                    .border(
                                        width = 1.dp,
                                        color = if (isAlleSelected) GoldPrimary else BorderSubtle,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable { onCategorySelected("ALLE") }
                                    .padding(horizontal = 11.dp, vertical = 4.dp)
                                    .testTag("filter_chip_alle")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "📺 ", fontSize = 10.5.sp)
                                    Text(
                                        text = if (isEnglish) "All Channels" else "Alle Sender",
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isAlleSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (isAlleSelected) ObsidianBlack else TextPrimary
                                    )
                                    if (liveCount > 0) {
                                        Text(
                                            text = " ($liveCount)",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isAlleSelected) ObsidianBlack.copy(alpha = 0.8f) else TextMuted
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Länder-Ordner Pills
                        items(countryFolders) { folder ->
                            val isCurrentCountryActive = activeCountryCode.equals(folder.code, ignoreCase = true)
                            val isDirectSelected = selectedCategory.equals(folder.code, ignoreCase = true)
                            val isDach = folder.code in listOf("DE", "AT", "CH")
                            val isSport = folder.code == "SPORT"
                            val isAdult = folder.code == "ADULT"

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        when {
                                            isDirectSelected -> GoldPrimary
                                            isCurrentCountryActive -> GoldPrimary.copy(alpha = 0.25f)
                                            isDach -> DarkSurfaceElevated
                                            isSport -> AccentEmerald.copy(alpha = 0.14f)
                                            isAdult -> AccentRose.copy(alpha = 0.14f)
                                            else -> DarkSurface
                                        }
                                    )
                                    .border(
                                        width = if (isDirectSelected || isCurrentCountryActive) 1.5.dp else 1.dp,
                                        color = when {
                                            isDirectSelected || isCurrentCountryActive -> GoldPrimary
                                            isDach -> BorderGlow
                                            isSport -> AccentEmerald.copy(alpha = 0.45f)
                                            isAdult -> AccentRose.copy(alpha = 0.45f)
                                            else -> BorderSubtle
                                        },
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable { onCategorySelected(folder.code) }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                    .testTag("filter_chip_country_${folder.code.lowercase()}")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "${folder.flagEmoji} ", fontSize = 11.sp)
                                    Text(
                                        text = folder.displayName,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isDirectSelected || isCurrentCountryActive || isDach) FontWeight.Bold else FontWeight.Medium,
                                        color = when {
                                            isDirectSelected -> ObsidianBlack
                                            isCurrentCountryActive || isDach -> TextPrimary
                                            isSport -> AccentEmerald
                                            isAdult -> AccentRose
                                            else -> TextSecondary
                                        }
                                    )
                                    if (folder.channelCount > 0) {
                                        Text(
                                            text = " (${folder.channelCount})",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isDirectSelected) ObsidianBlack.copy(alpha = 0.8f) else TextMuted
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. Bouquet / Unterordner Leiste (NUR wenn ein spezifisches Land aktiv gewählt ist)
                    val displayedFolder = if (activeCountryCode != null) {
                        countryFolders.firstOrNull { it.code.equals(activeCountryCode, ignoreCase = true) }
                    } else {
                        null
                    }

                    if (displayedFolder != null && displayedFolder.subFolders.isNotEmpty() && showSubFoldersRow) {
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            item {
                                val isAllOfCountrySelected = selectedCategory.equals(displayedFolder.code, ignoreCase = true)
                                val countBadge = if (displayedFolder.channelCount > 0) " (${displayedFolder.channelCount})" else ""
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isAllOfCountrySelected) GoldPrimary else DarkSurfaceElevated)
                                        .border(
                                            width = 1.dp,
                                            color = if (isAllOfCountrySelected) GoldPrimary else BorderSubtle,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { onCategorySelected(displayedFolder.code) }
                                        .padding(horizontal = 9.dp, vertical = 3.dp)
                                        .testTag("sub_folder_all_${displayedFolder.code.lowercase()}")
                                ) {
                                    Text(
                                        text = "📁 Alle ${displayedFolder.displayName}$countBadge",
                                        fontSize = 10.5.sp,
                                        fontWeight = if (isAllOfCountrySelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isAllOfCountrySelected) ObsidianBlack else TextPrimary
                                    )
                                }
                            }

                            items(displayedFolder.subFolders) { subFolder ->
                                val isSubSelected = selectedCategory.equals(subFolder, ignoreCase = true)
                                val cleanName = ContentClassifier.getCleanSubFolderName(subFolder)
                                val subCount = displayedFolder.subFolderCounts[subFolder] ?: 0
                                val countBadge = if (subCount > 0) " ($subCount)" else ""

                                val subIcon = when {
                                    cleanName.contains("Allgemein", ignoreCase = true) || cleanName.contains("Free", ignoreCase = true) -> "📺"
                                    cleanName.contains("24/7", ignoreCase = true) || cleanName.contains("247", ignoreCase = true) -> "⏱️"
                                    cleanName.contains("Doku", ignoreCase = true) || cleanName.contains("Wissen", ignoreCase = true) -> "📚"
                                    cleanName.contains("Cinema", ignoreCase = true) || cleanName.contains("Film", ignoreCase = true) || cleanName.contains("Kino", ignoreCase = true) -> "🎬"
                                    cleanName.contains("Sky", ignoreCase = true) -> "⭐"
                                    cleanName.contains("Serie", ignoreCase = true) -> "🍿"
                                    cleanName.contains("Sport", ignoreCase = true) || cleanName.contains("Fussball", ignoreCase = true) || cleanName.contains("Bundesliga", ignoreCase = true) -> "⚽"
                                    cleanName.contains("Kids", ignoreCase = true) || cleanName.contains("Kinder", ignoreCase = true) -> "🧸"
                                    cleanName.contains("Musik", ignoreCase = true) || cleanName.contains("Music", ignoreCase = true) -> "🎵"
                                    cleanName.contains("News", ignoreCase = true) || cleanName.contains("Nachrichten", ignoreCase = true) -> "📰"
                                    cleanName.contains("Austria", ignoreCase = true) || cleanName.contains("Österreich", ignoreCase = true) -> "🇦🇹"
                                    cleanName.contains("Schweiz", ignoreCase = true) || cleanName.contains("Swiss", ignoreCase = true) -> "🇨🇭"
                                    cleanName.contains("Adult", ignoreCase = true) || cleanName.contains("XXX", ignoreCase = true) -> "🔞"
                                    else -> "📁"
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSubSelected) AccentCyan else DarkSurface
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isSubSelected) AccentCyan else BorderSubtle,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { onCategorySelected(subFolder) }
                                        .padding(horizontal = 9.dp, vertical = 3.dp)
                                        .testTag("sub_folder_${cleanName.lowercase().replace(" ", "_")}")
                                ) {
                                    Text(
                                        text = "$subIcon $cleanName$countBadge",
                                        fontSize = 10.5.sp,
                                        fontWeight = if (isSubSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSubSelected) ObsidianBlack else TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // KOMPAKTER MODAL-DIALOG FÜR DIE PLAYLISTENAUSWAHL (Kein überhohes Dropdown!)
    // =========================================================================
    if (isDropdownExpanded) {
        Dialog(
            onDismissRequest = { isDropdownExpanded = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 430.dp)
                    .heightIn(max = 460.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.2.dp, GoldPrimary, RoundedCornerShape(18.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkNavyCard)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    // Dialog Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isEnglish) "Select Playlist (${playlists.size})" else "Playliste auswählen (${playlists.size})",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                        }
                        IconButton(
                            onClick = { isDropdownExpanded = false },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Schließen",
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Kompaktes 2x2 Aktions-Grid für Import / Hinzufügen / Verwaltung
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                isDropdownExpanded = false
                                onOpenImportAssistant()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.65f))
                        ) {
                            Text(
                                text = if (isEnglish) "🪄 Import (.txt)" else "🪄 Import-Assistent",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GoldLight,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = {
                                isDropdownExpanded = false
                                onOpenAddMacPortal()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.65f))
                        ) {
                            Text(
                                text = if (isEnglish) "📡 + MAC Portal" else "📡 + MAC Portal",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentCyan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                isDropdownExpanded = false
                                onOpenAddPlaylist()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, BorderGlow)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isEnglish) "M3U / Xtream" else "M3U / Xtream",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = {
                                isDropdownExpanded = false
                                showDeleteAllConfirmation = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, AccentRose.copy(alpha = 0.65f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = null,
                                tint = AccentRose,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isEnglish) "Delete All" else "Alle löschen",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentRose,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (playlists.size > 5) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = playlistMenuSearch,
                            onValueChange = { playlistMenuSearch = it },
                            placeholder = {
                                Text(
                                    if (isEnglish) "Filter playlists..." else "Playliste suchen (${playlists.size})...",
                                    fontSize = 11.5.sp,
                                    color = TextMuted
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            textStyle = TextStyle(fontSize = 12.sp, color = TextPrimary),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = BorderSubtle,
                                focusedContainerColor = DarkSurface,
                                unfocusedContainerColor = DarkSurface
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = BorderSubtle, thickness = 0.8.dp)
                    Spacer(modifier = Modifier.height(6.dp))

                    val playlistNumberMap = remember(playlists) {
                        playlists.withIndex().associate { (idx, item) -> item.id to (idx + 1) }
                    }
                    val filteredMenuPlaylists = remember(playlists, playlistMenuSearch) {
                        if (playlistMenuSearch.isBlank()) {
                            playlists
                        } else {
                            playlists.filter {
                                it.getDisplayName().contains(playlistMenuSearch, ignoreCase = true)
                            }
                        }
                    }

                    if (filteredMenuPlaylists.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isEnglish) "No playlists found" else "Keine Playlisten gefunden",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                    } else {
                        // Virtualisierte, höhenbegrenzte Liste (O(1) Index-Lookup für flüssiges Scrollen bei 1.000+ Playlisten)
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 230.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            items(
                                items = filteredMenuPlaylists,
                                key = { it.id },
                                contentType = { "playlist_selector_row" }
                            ) { pl ->
                                val number = playlistNumberMap[pl.id] ?: 1
                                val isCurrent = pl.id == activePlaylist?.id

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isCurrent) DarkSurfaceElevated else DarkSurface)
                                        .border(
                                            width = if (isCurrent) 1.2.dp else 1.dp,
                                            color = if (isCurrent) GoldPrimary else BorderSubtle,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            onPlaylistSelected(pl.id)
                                            isDropdownExpanded = false
                                        }
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(5.dp))
                                                .background(if (isCurrent) GoldPrimary else DarkNavyCard)
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "#$number",
                                                color = if (isCurrent) ObsidianBlack else GoldLight,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(7.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = pl.getDisplayName(),
                                                color = if (isCurrent) GoldLight else TextPrimary,
                                                fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.SemiBold,
                                                fontSize = 12.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${pl.type.name} • ${pl.liveCount} Live • ${pl.movieCount} VOD",
                                                fontSize = 9.5.sp,
                                                color = TextMuted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isCurrent) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Aktiv",
                                                tint = AccentEmerald,
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .padding(end = 2.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                isDropdownExpanded = false
                                                onEditPlaylistRequested(pl)
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit Playlist with PIN",
                                                tint = GoldLight,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                isDropdownExpanded = false
                                                playlistForActionsDialog = pl
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Löschen / Optionen",
                                                tint = AccentRose.copy(alpha = 0.85f),
                                                modifier = Modifier.size(14.dp)
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
    }

    // Playlist Actions Dialog
    if (playlistForActionsDialog != null) {
        PlaylistActionsDialog(
            playlist = playlistForActionsDialog!!,
            isActive = playlistForActionsDialog?.id == activePlaylist?.id,
            onDismiss = { playlistForActionsDialog = null },
            onDeletePlaylist = { pl ->
                playlistForActionsDialog = null
                onDeletePlaylistRequested(pl)
            },
            onEditPlaylist = { pl ->
                playlistForActionsDialog = null
                onEditPlaylistRequested(pl)
            },
            onRefreshPlaylist = { pl ->
                playlistForActionsDialog = null
                onRefreshPlaylist(pl)
            },
            onSelectPlaylist = { id ->
                playlistForActionsDialog = null
                onPlaylistSelected(id)
            },
            isEnglish = isEnglish
        )
    }

    if (showDeleteAllConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirmation = false },
            containerColor = DarkNavyCard,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
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
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = null,
                            tint = AccentRose,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEnglish) "Delete All Playlists?" else "Alle Playlisten löschen?",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = AccentRose
                        )
                    }
                    IconButton(
                        onClick = { showDeleteAllConfirmation = false },
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
                Text(
                    text = if (isEnglish) {
                        "Are you sure you want to permanently delete ALL playlists and channels from the local database? This cannot be undone."
                    } else {
                        "Möchtest du wirklich ALLE Playlisten und Sender vollständig aus der Datenbank löschen? Die Playlistenliste wird komplett geleert."
                    },
                    fontSize = 14.sp,
                    color = TextPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAllConfirmation = false
                        onDeleteAllPlaylistsRequested()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRose),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (isEnglish) "Yes, Delete All" else "Ja, alle löschen",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteAllConfirmation = false },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (isEnglish) "Cancel" else "Abbrechen",
                        color = TextSecondary
                    )
                }
            }
        )
    }

    if (showCountrySelectorDialog) {
        CountrySelectorDialog(
            allChannels = channels,
            disabledCountries = disabledCountries,
            hiddenChannelIds = hiddenChannelIds,
            selectedCategory = selectedCategory,
            countrySummary = countrySummary,
            isTvMode = isTvMode,
            onSelectCountry = { countryCode ->
                onSelectCountry(countryCode)
                onCategorySelected(countryCode)
            },
            onToggleCountry = onToggleCountry,
            onEnableAllCountries = onEnableAllCountries,
            onDisableAllCountries = onDisableAllCountries,
            onToggleChannelHidden = onToggleChannelHidden,
            onRemoveAllChannelsInCountry = onRemoveAllChannelsInCountry,
            onRestoreAllChannelsInCountry = onRestoreAllChannelsInCountry,
            onRestoreAllChannels = onRestoreAllChannels,
            onResetAll = onResetCountryAndChannelFilters,
            onAddCustomChannel = onAddCustomChannel,
            onDismissRequest = { showCountrySelectorDialog = false }
        )
    }

    if (showSortDialog) {
        ChannelSortDialog(
            currentMode = channelSortMode,
            onModeSelected = { mode ->
                onSortModeSelected(mode)
            },
            onDismiss = { showSortDialog = false },
            isEnglish = isEnglish
        )
    }
}
