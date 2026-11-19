package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ChannelItem
import com.example.data.model.MAX_VOD_DOWNLOAD_QUEUE_SIZE
import com.example.data.model.RecordingItem
import com.example.data.model.RecordingStatus
import com.example.data.parser.ContentClassifier
import com.example.data.series.SeriesAggregationEngine
import com.example.domain.model.PlaybackProgress
import com.example.domain.model.SeriesSortOption
import com.example.domain.model.SeriesStatusFilter
import com.example.domain.model.UnifiedEpisode
import com.example.domain.model.UnifiedSeason
import com.example.domain.model.UnifiedSeries
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

/**
 * Modern, High-Performance Series & TV Shows Explorer.
 * Implements Prompt 10:
 * - Hierarchy: Land -> Serien -> Serie -> Staffel -> Episode
 * - Multi-source support (M3U, Xtream Codes, MAC Portals)
 * - Provider category preservation
 * - Multi-provider aggregation with alternative stream failovers
 * - Progress tracking & "Nächste Episode" & "Weiter ansehen"
 * - Full Android TV D-Pad & Touchscreen navigation
 */
@Composable
fun SeriesExplorer(
    seriesChannels: List<ChannelItem>,
    recordings: List<RecordingItem> = emptyList(),
    onPlayEpisode: (ChannelItem) -> Unit,
    onDownloadEpisode: (ChannelItem) -> Unit = {},
    onEnqueueEpisodes: (List<ChannelItem>) -> Unit = { list -> list.forEach(onDownloadEpisode) },
    onOpenDownloadManager: () -> Unit = {},
    onFetchEpisodes: suspend (ChannelItem) -> List<ChannelItem> = { emptyList() },
    progressMap: Map<String, PlaybackProgress> = emptyMap(),
    isEnglish: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Local in-memory playback progress map (persisted in database, reactive here)
    val localProgressMap = remember { mutableStateMapOf<String, PlaybackProgress>() }
    androidx.compose.runtime.LaunchedEffect(progressMap) {
        if (progressMap.isNotEmpty()) {
            localProgressMap.putAll(progressMap)
        }
    }
    val localFavoritesSet = remember { mutableStateOf(setOf<String>()) }

    // In-memory cache for dynamically fetched episodes (e.g. from Xtream Codes get_series_info or MAC Portals)
    val dynamicEpisodesMap = remember { mutableStateMapOf<String, List<UnifiedSeason>>() }
    val dynamicRawItemsMap = remember { mutableStateMapOf<String, ChannelItem>() }
    var isLoadingEpisodes by remember { mutableStateOf(false) }

    // Aggregate raw channel items into Unified Series hierarchy asynchronously on background thread (prevents ANR)
    var unifiedSeriesList by remember { mutableStateOf<List<UnifiedSeries>>(emptyList()) }
    var isAggregatingSeries by remember { mutableStateOf(false) }

    LaunchedEffect(seriesChannels, localProgressMap.size, localFavoritesSet.value) {
        if (seriesChannels.isEmpty()) {
            unifiedSeriesList = emptyList()
            return@LaunchedEffect
        }
        isAggregatingSeries = true
        val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            SeriesAggregationEngine.buildUnifiedSeriesList(
                items = seriesChannels,
                progressMap = localProgressMap,
                favoritesSet = localFavoritesSet.value
            )
        }
        unifiedSeriesList = result
        isAggregatingSeries = false
    }

    // Filter & Search states
    var selectedCountry by remember { mutableStateOf("ALL") }
    var selectedCategory by remember { mutableStateOf("ALL") }
    var selectedStatusFilter by remember { mutableStateOf(SeriesStatusFilter.ALL) }
    var selectedSortOption by remember { mutableStateOf(SeriesSortOption.PROVIDER_DEFAULT) }
    var searchQuery by remember { mutableStateOf("") }

    // Active Navigation State (Overview -> Series Detail -> Episode Detail Dialog)
    var selectedSeriesId by remember { mutableStateOf<String?>(null) }
    var selectedSeasonNumber by remember { mutableStateOf(1) }
    var dialogEpisode by remember { mutableStateOf<UnifiedEpisode?>(null) }

    // Selected Series (for Detail View), merged with dynamic seasons if loaded
    val currentSeries = remember(selectedSeriesId, unifiedSeriesList, dynamicEpisodesMap[selectedSeriesId]) {
        val base = unifiedSeriesList.find { it.id == selectedSeriesId }
        val dynamicSeasons = dynamicEpisodesMap[selectedSeriesId]
        if (base != null && !dynamicSeasons.isNullOrEmpty()) {
            val allEps = dynamicSeasons.flatMap { it.episodes }
            val totalEps = allEps.size
            val watchedEps = allEps.count { it.isWatched }
            base.copy(
                seasons = dynamicSeasons,
                totalEpisodesCount = totalEps,
                watchedEpisodesCount = watchedEps,
                progressPercent = if (totalEps > 0) watchedEps.toFloat() / totalEps else 0f,
                nextEpisodeToWatch = allEps.firstOrNull { !it.isWatched } ?: allEps.firstOrNull()
            )
        } else {
            base
        }
    }

    // Handle system back navigation when a series detail is open
    BackHandler(enabled = selectedSeriesId != null) {
        selectedSeriesId = null
    }

    // On-demand fetch of full seasons and episodes when a series is opened
    LaunchedEffect(selectedSeriesId) {
        val seriesId = selectedSeriesId ?: return@LaunchedEffect
        val series = unifiedSeriesList.find { it.id == seriesId } ?: return@LaunchedEffect
        val needsFetch = dynamicEpisodesMap[seriesId] == null && (
            series.totalEpisodesCount <= 1 ||
            series.seasons.all { it.episodes.size <= 1 }
        )
        if (needsFetch) {
            val rawItem = seriesChannels.find {
                it.id == series.id ||
                it.seriesTitle.equals(series.title, ignoreCase = true) ||
                it.name.equals(series.title, ignoreCase = true) ||
                com.example.data.parser.ContentClassifier.cleanSeriesTitle(it.seriesTitle ?: it.name).equals(series.title, ignoreCase = true) ||
                com.example.data.parser.ContentClassifier.canonicalSeriesTitle(it.seriesTitle ?: it.name) == com.example.data.parser.ContentClassifier.canonicalSeriesTitle(series.title)
            } ?: seriesChannels.find { it.id == series.id } ?: seriesChannels.firstOrNull()
            if (rawItem != null) {
                isLoadingEpisodes = true
                try {
                    val fetchedItems = onFetchEpisodes(rawItem)
                    if (fetchedItems.isNotEmpty()) {
                        fetchedItems.forEach { epItem ->
                            dynamicRawItemsMap[epItem.id] = epItem
                            dynamicRawItemsMap[epItem.streamUrl] = epItem
                        }
                        val seasonGroups = fetchedItems.groupBy { it.seasonNumber ?: 1 }
                        val seasons = seasonGroups.toSortedMap().map { (seasonNum, eps) ->
                            val sortedEps = eps.sortedBy { it.episodeNumber ?: 1 }
                            val allSameEp = sortedEps.size > 1 && sortedEps.all { (it.episodeNumber ?: 1) == (sortedEps.first().episodeNumber ?: 1) }
                            UnifiedSeason(
                                seasonNumber = seasonNum,
                                episodes = sortedEps.mapIndexed { idx, epItem ->
                                    val epId = epItem.id
                                    val progress = localProgressMap[epId]
                                    val epNumber = if (allSameEp) idx + 1 else (epItem.episodeNumber ?: (idx + 1))
                                    val epTitle = if (epItem.name.isNotBlank() && !epItem.name.equals("Episode $epNumber", ignoreCase = true)) {
                                        epItem.name
                                    } else {
                                        "Episode $epNumber"
                                    }
                                    UnifiedEpisode(
                                        id = epId,
                                        externalId = epItem.tvgId ?: epId,
                                        title = epTitle,
                                        seasonNumber = epItem.seasonNumber ?: seasonNum,
                                        episodeNumber = epNumber,
                                        streamUrl = epItem.streamUrl,
                                        thumbnail = epItem.logoUrl ?: epItem.backdropUrl,
                                        description = epItem.plot ?: "",
                                        durationMinutes = if (epItem.durationSeconds > 0) epItem.durationSeconds / 60 else 45,
                                        isWatched = progress?.isWatched ?: false,
                                        progress = progress,
                                        progressPercent = progress?.progressPercent?.toFloat() ?: 0f,
                                        language = epItem.language
                                    )
                                }
                            )
                        }
                        dynamicEpisodesMap[seriesId] = seasons
                        if (seasons.isNotEmpty() && (selectedSeasonNumber <= 0 || seasons.none { it.seasonNumber == selectedSeasonNumber })) {
                            selectedSeasonNumber = seasons.first().seasonNumber
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SeriesExplorer", "Error fetching series info: ${e.message}")
                } finally {
                    isLoadingEpisodes = false
                }
            }
        }
    }

    // Distinct countries for country selector chips, strictly prioritized: ALL -> DE -> AT -> CH -> ADULT -> others
    var availableCountries by remember { mutableStateOf(listOf("ALL")) }
    LaunchedEffect(unifiedSeriesList) {
        availableCountries = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val codes = unifiedSeriesList
                .map { it.countryCode.uppercase() }
                .distinct()
                .sortedWith(
                    compareBy<String> { code ->
                        when (code) {
                            "DE" -> 1
                            "AT" -> 2
                            "CH" -> 3
                            "ADULT" -> 4
                            else -> ContentClassifier.getCountryInfo(code).rank + 10
                        }
                    }.thenBy { it }
                )
            listOf("ALL") + codes
        }
    }

    // Distinct provider categories for category chips (preserving exact provider category order)
    var availableCategories by remember { mutableStateOf(listOf("ALL")) }
    LaunchedEffect(unifiedSeriesList, selectedCountry) {
        availableCategories = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val inCountry = if (selectedCountry == "ALL") {
                unifiedSeriesList
            } else {
                unifiedSeriesList.filter { it.countryCode.equals(selectedCountry, ignoreCase = true) }
            }
            val cats = ContentClassifier.sortCategoriesWithDachPriority(
                inCountry.map { it.categoryName }.filter { it.isNotBlank() }.distinct()
            )
            listOf("ALL") + cats
        }
    }

    // Filtered and Sorted Series offloaded to Dispatchers.Default
    var displayedSeries by remember { mutableStateOf<List<UnifiedSeries>>(emptyList()) }
    LaunchedEffect(
        unifiedSeriesList,
        selectedCountry,
        selectedCategory,
        selectedStatusFilter,
        searchQuery,
        selectedSortOption
    ) {
        displayedSeries = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            SeriesAggregationEngine.filterAndSort(
                seriesList = unifiedSeriesList,
                countryCode = selectedCountry,
                category = selectedCategory,
                statusFilter = selectedStatusFilter,
                query = searchQuery,
                sortOption = selectedSortOption
            )
        }
    }

    // Helper to find raw ChannelItem for playing/downloading
    fun findChannelItemForEpisode(episode: UnifiedEpisode): ChannelItem? {
        val directlyCached = dynamicRawItemsMap[episode.id] ?: dynamicRawItemsMap[episode.streamUrl]
        if (directlyCached != null) return directlyCached

        val matchedInList = seriesChannels.find {
            it.streamUrl == episode.streamUrl ||
                    it.id == episode.externalId ||
                    (it.name.equals(episode.title, ignoreCase = true) && it.seasonNumber == episode.seasonNumber && it.episodeNumber == episode.episodeNumber)
        }
        if (matchedInList != null) return matchedInList

        val parentSeriesItem = seriesChannels.firstOrNull {
            it.id == currentSeries?.id || it.seriesTitle.equals(currentSeries?.title, ignoreCase = true)
        } ?: seriesChannels.firstOrNull()

        return parentSeriesItem?.copy(
            id = episode.id,
            name = episode.title,
            streamUrl = episode.streamUrl,
            seriesTitle = currentSeries?.title ?: parentSeriesItem.seriesTitle,
            seasonNumber = episode.seasonNumber,
            episodeNumber = episode.episodeNumber,
            logoUrl = episode.thumbnail ?: parentSeriesItem.logoUrl,
            plot = episode.description.ifBlank { parentSeriesItem.plot },
            durationSeconds = episode.durationMinutes * 60
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        if (currentSeries == null) {
            // =========================================================================
            // 1. SERIEN-ÜBERSICHT (Grid, Filter, Suche, D-Pad)
            // =========================================================================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Top Search & Country Selector Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("series_search_input"),
                        placeholder = {
                            Text(
                                text = if (isEnglish) "Search series, seasons, episodes..." else "Serien, Staffeln, Episoden suchen...",
                                fontSize = 13.sp,
                                color = TextMuted
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Suchen",
                                tint = GoldPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Löschen",
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = BorderGlow,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = DarkSurfaceElevated,
                            unfocusedContainerColor = DarkSurface
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    val activeDl = recordings.count {
                        it.status == RecordingStatus.DOWNLOADING ||
                            it.status == RecordingStatus.QUEUED ||
                            it.status == RecordingStatus.RECORDING ||
                            it.status == RecordingStatus.SCHEDULED
                    }
                    val doneDl = recordings.count { it.status == RecordingStatus.DOWNLOADED }
                    OutlinedButton(
                        onClick = onOpenDownloadManager,
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("series_open_download_manager_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentEmerald),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.65f)),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "VOD Download Manager",
                            tint = AccentEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (activeDl > 0) "Downloads ($activeDl/$MAX_VOD_DOWNLOAD_QUEUE_SIZE)" else "Downloads ($doneDl MP4)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // Global Country Selector Bar
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    items(availableCountries) { country ->
                        val isSelected = selectedCountry.equals(country, ignoreCase = true)
                        val flagAndName = when (country.uppercase()) {
                            "ALL" -> if (isEnglish) "🌍 ALL COUNTRIES" else "🌍 ALLE LÄNDER"
                            "DE" -> "🇩🇪 DE: DEUTSCHLAND"
                            "AT" -> "🇦🇹 AT: ÖSTERREICH"
                            "CH" -> "🇨🇭 CH: SCHWEIZ"
                            "ADULT" -> "🔞 ADULT: 18+"
                            else -> {
                                val info = com.example.data.parser.ContentClassifier.getCountryInfo(country)
                                "${info.flag} ${info.name}"
                            }
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedCountry = country
                                selectedCategory = "ALL"
                            },
                            label = {
                                Text(
                                    text = flagAndName,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GoldPrimary,
                                selectedLabelColor = Color.Black,
                                containerColor = DarkSurfaceElevated,
                                labelColor = TextSecondary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                // Provider Category Chips (Preserving original provider categories)
                if (availableCategories.size > 2) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    ) {
                        items(availableCategories) { cat ->
                            val isSelected = selectedCategory.equals(cat, ignoreCase = true)
                            val icon = when {
                                cat.equals("ALL", ignoreCase = true) -> "📁"
                                cat.contains("Netflix", ignoreCase = true) -> "🍿"
                                cat.contains("Amazon", ignoreCase = true) -> "⭐"
                                cat.contains("Sky", ignoreCase = true) -> "✨"
                                cat.contains("Disney", ignoreCase = true) -> "🏰"
                                cat.contains("Apple", ignoreCase = true) -> "🍎"
                                cat.contains("Action", ignoreCase = true) -> "💥"
                                cat.contains("Doku", ignoreCase = true) -> "📜"
                                cat.contains("Comedy", ignoreCase = true) || cat.contains("Komödie", ignoreCase = true) -> "😂"
                                else -> "📁"
                            }
                            val labelText = if (cat.equals("ALL", ignoreCase = true)) {
                                if (isEnglish) "All Categories" else "Alle Kategorien"
                            } else cat

                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedCategory = cat },
                                label = {
                                    Text(
                                        text = "$icon $labelText",
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentCyan,
                                    selectedLabelColor = Color.Black,
                                    containerColor = DarkSurface,
                                    labelColor = TextPrimary
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }

                // Status Chips (Alle, Weiter ansehen, Ungesehen, Favoriten) & Sortierung
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        item {
                            FilterChip(
                                selected = selectedStatusFilter == SeriesStatusFilter.ALL,
                                onClick = { selectedStatusFilter = SeriesStatusFilter.ALL },
                                label = { Text(if (isEnglish) "All (${displayedSeries.size})" else "Alle (${displayedSeries.size})", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GoldDark,
                                    selectedLabelColor = Color.White,
                                    containerColor = DarkSurfaceElevated,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedStatusFilter == SeriesStatusFilter.IN_PROGRESS,
                                onClick = { selectedStatusFilter = SeriesStatusFilter.IN_PROGRESS },
                                label = { Text("▶ Weiter ansehen", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentEmerald,
                                    selectedLabelColor = Color.Black,
                                    containerColor = DarkSurfaceElevated,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedStatusFilter == SeriesStatusFilter.UNWATCHED,
                                onClick = { selectedStatusFilter = SeriesStatusFilter.UNWATCHED },
                                label = { Text("🆕 Ungesehen", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentCyan,
                                    selectedLabelColor = Color.Black,
                                    containerColor = DarkSurfaceElevated,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedStatusFilter == SeriesStatusFilter.FAVORITES,
                                onClick = { selectedStatusFilter = SeriesStatusFilter.FAVORITES },
                                label = { Text("⭐ Favoriten", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GoldPrimary,
                                    selectedLabelColor = Color.Black,
                                    containerColor = DarkSurfaceElevated,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }
                }

                // Empty State, Aggregating Progress, or Series Grid
                if (seriesChannels.isNotEmpty() && unifiedSeriesList.isEmpty() && isAggregatingSeries) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = GoldPrimary)
                            Text(
                                text = if (isEnglish) "Organizing series..." else "Serien werden vorbereitet...",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else if (displayedSeries.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(64.dp)
                            )
                            Text(
                                text = if (isEnglish) "No series found for this filter" else "Keine Serien für diese Filterkriterien gefunden",
                                color = TextSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (isEnglish) "Try adjusting country, category, or search term" else "Passe Land, Kategorie oder den Suchbegriff an",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    // Responsive Grid of Series Cards
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 145.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 90.dp, top = 6.dp),
                        modifier = Modifier.fillMaxSize().testTag("series_grid")
                    ) {
                        items(
                            items = displayedSeries,
                            key = { it.id }
                        ) { series ->
                            val dynamicSeasons = dynamicEpisodesMap[series.id]
                            val effectiveSeries = if (!dynamicSeasons.isNullOrEmpty()) {
                                val allEps = dynamicSeasons.flatMap { it.episodes }
                                series.copy(
                                    seasons = dynamicSeasons,
                                    totalEpisodesCount = allEps.size,
                                    watchedEpisodesCount = allEps.count { it.isWatched }
                                )
                            } else {
                                series
                            }
                            SeriesCard(
                                series = effectiveSeries,
                                isEnglish = isEnglish,
                                onClick = {
                                    selectedSeriesId = series.id
                                    selectedSeasonNumber = effectiveSeries.seasons.firstOrNull()?.seasonNumber ?: 1
                                },
                                onToggleFavorite = {
                                    val current = localFavoritesSet.value
                                    localFavoritesSet.value = if (current.contains(series.id)) {
                                        current - series.id
                                    } else {
                                        current + series.id
                                    }
                                }
                            )
                        }
                    }
                }
            }
        } else {
            // =========================================================================
            // 2. SERIEN-DETAILANSICHT (Backdrop, Staffeln, Episoden, Fortschritt, D-Pad)
            // =========================================================================
            SeriesDetailScreen(
                series = currentSeries,
                recordings = recordings,
                selectedSeasonNumber = selectedSeasonNumber,
                isLoadingEpisodes = isLoadingEpisodes,
                onSeasonSelected = { selectedSeasonNumber = it },
                onBack = { selectedSeriesId = null },
                onPlayEpisode = { ep ->
                    val ch = findChannelItemForEpisode(ep)
                    if (ch != null) onPlayEpisode(ch)
                },
                onDownloadEpisode = { ep ->
                    val ch = findChannelItemForEpisode(ep)
                    if (ch != null) onDownloadEpisode(ch)
                },
                onEnqueueEpisodes = { episodes ->
                    val channelItems = episodes.mapNotNull { findChannelItemForEpisode(it) }
                    if (channelItems.isNotEmpty()) {
                        onEnqueueEpisodes(channelItems)
                    }
                },
                onOpenDownloadManager = onOpenDownloadManager,
                onOpenEpisodeDetails = { ep ->
                    dialogEpisode = ep
                },
                onToggleSeriesFavorite = {
                    val current = localFavoritesSet.value
                    localFavoritesSet.value = if (current.contains(currentSeries.id)) {
                        current - currentSeries.id
                    } else {
                        current + currentSeries.id
                    }
                },
                isEnglish = isEnglish
            )
        }

        // =========================================================================
        // 3. EPISODEN-DETAILS DIALOG (Stream-Auswahl, Play/Resume/Restart)
        // =========================================================================
        dialogEpisode?.let { ep ->
            EpisodeDetailDialog(
                episode = ep,
                seriesTitle = currentSeries?.title ?: "",
                isEnglish = isEnglish,
                onDismiss = { dialogEpisode = null },
                onPlay = {
                    dialogEpisode = null
                    val ch = findChannelItemForEpisode(ep)
                    if (ch != null) onPlayEpisode(ch)
                },
                onRestart = {
                    dialogEpisode = null
                    localProgressMap[ep.id] = PlaybackProgress(
                        contentId = ep.id,
                        positionMs = 0L,
                        durationMs = (ep.durationMinutes * 60_000L).coerceAtLeast(1000L),
                        isCompleted = false
                    )
                    val ch = findChannelItemForEpisode(ep)
                    if (ch != null) onPlayEpisode(ch)
                },
                onMarkWatched = { isWatched ->
                    localProgressMap[ep.id] = PlaybackProgress(
                        contentId = ep.id,
                        positionMs = (ep.durationMinutes * 60_000L),
                        durationMs = (ep.durationMinutes * 60_000L).coerceAtLeast(1000L),
                        isCompleted = isWatched
                    )
                },
                onDownload = {
                    val ch = findChannelItemForEpisode(ep)
                    if (ch != null) onDownloadEpisode(ch)
                }
            )
        }
    }
}

/**
 * Single Series Card in the Grid.
 * Displays poster, title, year, rating, progress bar, seasons count, and favorite button.
 */
@Composable
private fun SeriesCard(
    series: UnifiedSeries,
    isEnglish: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) GoldPrimary else BorderGlow,
                shape = RoundedCornerShape(12.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(onClick = onClick)
            .testTag("series_card_${series.id}"),
        colors = CardDefaults.cardColors(containerColor = DarkNavyCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            // Poster Box with Aspect Ratio
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.68f)
                    .background(DarkSurfaceElevated)
            ) {
                if (!series.poster.isNullOrBlank()) {
                    AsyncImage(
                        model = series.poster,
                        contentDescription = series.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Fallback placeholder with TV icon
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(DarkSurfaceElevated, DarkSurface)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = null,
                                tint = GoldPrimary.copy(alpha = 0.6f),
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = series.title,
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Top Bar on Poster (Country flag & Favorite Star)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Country Badge
                    Surface(
                        color = ObsidianBlack.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = series.countryCode.uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldLight,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }

                    // Favorite Button
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = if (series.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favorit",
                            tint = if (series.isFavorite) GoldPrimary else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Rating Badge (Bottom Left)
                if (series.rating != null && series.rating > 0f) {
                    Surface(
                        color = ObsidianBlack.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(text = "⭐ ", fontSize = 9.sp)
                            Text(
                                text = "%.1f".format(series.rating),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Watched Checkmark if 100% complete
                if (series.isCompleted) {
                    Surface(
                        color = AccentEmerald.copy(alpha = 0.9f),
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Komplett gesehen",
                            tint = Color.Black,
                            modifier = Modifier
                                .size(20.dp)
                                .padding(2.dp)
                        )
                    }
                }
            }

            // Series Info Under Poster
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(
                    text = series.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${series.seasons.size} ${if (series.seasons.size == 1) "Staffel" else "Staffeln"}",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                    series.releaseYear?.let { year ->
                        Text(
                            text = year.toString(),
                            fontSize = 10.sp,
                            color = GoldLight,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Progress Bar if in progress
                if (series.isStarted) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { series.progressPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = if (series.isCompleted) AccentEmerald else GoldPrimary,
                        trackColor = DarkSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${series.watchedEpisodesCount}/${series.totalEpisodesCount} Folgen (${series.progressPercent}%)",
                        fontSize = 9.sp,
                        color = if (series.isCompleted) AccentEmerald else TextMuted
                    )
                }
            }
        }
    }
}

/**
 * Detailed Series View:
 * Backdrop, Poster, Metadata, Progress, Season Tabs, and Episode List.
 */
@Composable
private fun SeriesDetailScreen(
    series: UnifiedSeries,
    recordings: List<RecordingItem> = emptyList(),
    selectedSeasonNumber: Int,
    isLoadingEpisodes: Boolean = false,
    onSeasonSelected: (Int) -> Unit,
    onBack: () -> Unit,
    onPlayEpisode: (UnifiedEpisode) -> Unit,
    onDownloadEpisode: (UnifiedEpisode) -> Unit,
    onEnqueueEpisodes: (List<UnifiedEpisode>) -> Unit = {},
    onOpenDownloadManager: () -> Unit = {},
    onOpenEpisodeDetails: (UnifiedEpisode) -> Unit,
    onToggleSeriesFavorite: () -> Unit,
    isEnglish: Boolean
) {
    val currentSeason = remember(series, selectedSeasonNumber) {
        series.seasons.find { it.seasonNumber == selectedSeasonNumber }
            ?: series.seasons.firstOrNull()
    }

    val episodesInCurrentSeason = remember(currentSeason) {
        currentSeason?.episodes?.sortedBy { it.episodeNumber } ?: emptyList()
    }

    // Multi-selection for marking up to 99 episodes across seasons for sequential download
    var selectedEpisodes by remember { mutableStateOf<List<UnifiedEpisode>>(emptyList()) }
    val selectedEpisodeIds = remember(selectedEpisodes) { selectedEpisodes.map { it.id }.toSet() }

    val recordingsByUrl = remember(recordings) {
        recordings.associateBy { it.streamUrl }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        // 1. Header with Backdrop and Back button
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
            ) {
                // Backdrop Image with Gradient
                if (!series.backdrop.isNullOrBlank() || !series.poster.isNullOrBlank()) {
                    AsyncImage(
                        model = series.backdrop ?: series.poster,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(DarkNavyCard)
                    )
                }

                // Gradient Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    ObsidianBlack.copy(alpha = 0.6f),
                                    ObsidianBlack
                                )
                            )
                        )
                )

                // Back Button & Favorite Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(38.dp)
                            .background(ObsidianBlack.copy(alpha = 0.7f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zurück",
                            tint = GoldPrimary
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onOpenDownloadManager,
                            modifier = Modifier.height(36.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = ObsidianBlack.copy(alpha = 0.75f),
                                contentColor = AccentEmerald
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.7f)),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Download-Manager",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(
                            onClick = onToggleSeriesFavorite,
                            modifier = Modifier
                                .size(38.dp)
                                .background(ObsidianBlack.copy(alpha = 0.7f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (series.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Favorit",
                                tint = if (series.isFavorite) GoldPrimary else Color.White
                            )
                        }
                    }
                }

                // Title and Metadata overlaid on lower backdrop
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Poster thumbnail
                    Box(
                        modifier = Modifier
                            .width(85.dp)
                            .aspectRatio(0.68f)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, BorderGlow, RoundedCornerShape(8.dp))
                            .background(DarkSurfaceElevated)
                    ) {
                        AsyncImage(
                            model = series.poster,
                            contentDescription = series.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = series.title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        series.originalTitle?.let { orig ->
                            if (!orig.equals(series.title, ignoreCase = true)) {
                                Text(
                                    text = orig,
                                    fontSize = 12.sp,
                                    color = TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Metadata pills
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            series.releaseYear?.let {
                                Text(text = "$it", fontSize = 11.sp, color = GoldLight, fontWeight = FontWeight.Bold)
                            }
                            series.rating?.let {
                                Text(text = "⭐ %.1f".format(it), fontSize = 11.sp, color = Color.White)
                            }
                            Text(
                                text = "${series.seasons.size} ${if (series.seasons.size == 1) "Staffel" else "Staffeln"}",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                            Text(
                                text = "• ${series.totalEpisodesCount} Folgen",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }

        // 2. Action Button (Continue Watching / Start Season 1 Episode 1) & Description
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Resume or Play First Button
                val nextEp = series.nextEpisodeToWatch
                Button(
                    onClick = {
                        val epToPlay = nextEp ?: currentSeason?.episodes?.firstOrNull()
                        if (epToPlay != null) onPlayEpisode(epToPlay)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("series_play_action_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldPrimary,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (series.isCompleted) {
                            if (isEnglish) "Restart Series" else "Serie von vorne starten"
                        } else if (nextEp != null && series.isStarted) {
                            if (isEnglish) "Continue: S${nextEp.seasonNumber}E${nextEp.episodeNumber} • ${nextEp.title}"
                            else "Fortsetzen: S${nextEp.seasonNumber}E${nextEp.episodeNumber} • ${nextEp.title}"
                        } else {
                            if (isEnglish) "Play Season 1 Episode 1" else "Staffel 1 Episode 1 abspielen"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                // Progress Bar
                if (series.totalEpisodesCount > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEnglish) "Series Progress" else "Serien-Gesamtfortschritt",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "${series.watchedEpisodesCount} von ${series.totalEpisodesCount} Folgen gesehen (${series.progressPercent}%)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (series.isCompleted) AccentEmerald else GoldLight
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { series.progressPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (series.isCompleted) AccentEmerald else GoldPrimary,
                        trackColor = DarkSurfaceElevated
                    )
                }

                // Description
                if (!series.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = series.description,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        // 3. Season Tabs (Staffel 1, Staffel 2, Staffel 3...)
        item {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    text = if (isEnglish) "SEASONS" else "STAFFELN",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(series.seasons.sortedBy { it.seasonNumber }) { season ->
                        val isSelected = season.seasonNumber == selectedSeasonNumber
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) GoldPrimary else DarkSurfaceElevated)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) GoldPrimary else BorderGlow,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { onSeasonSelected(season.seasonNumber) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Staffel ${season.seasonNumber}",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.Black else TextSecondary,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "(${season.episodes.size})",
                                    fontSize = 10.sp,
                                    color = if (isSelected) Color.Black.copy(alpha = 0.7f) else TextMuted
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Episode List Header + Batch Marking Bar (up to 99 episodes)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Staffel $selectedSeasonNumber • ${episodesInCurrentSeason.size} Episoden",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldLight
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                val currentList = selectedEpisodes.toMutableList()
                                for (ep in episodesInCurrentSeason) {
                                    if (currentList.size >= MAX_VOD_DOWNLOAD_QUEUE_SIZE) break
                                    if (currentList.none { it.id == ep.id }) {
                                        currentList.add(ep)
                                    }
                                }
                                selectedEpisodes = currentList
                            },
                            enabled = selectedEpisodes.size < MAX_VOD_DOWNLOAD_QUEUE_SIZE && episodesInCurrentSeason.isNotEmpty(),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("series_mark_season_button"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldPrimary),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f)),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Text(
                                text = if (isEnglish) "☑ Mark Season (max $MAX_VOD_DOWNLOAD_QUEUE_SIZE)" else "☑ Staffel markieren (max. $MAX_VOD_DOWNLOAD_QUEUE_SIZE)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (selectedEpisodes.isNotEmpty()) {
                    Surface(
                        color = DarkSurfaceElevated,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.7f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isEnglish) {
                                        "${selectedEpisodes.size} / $MAX_VOD_DOWNLOAD_QUEUE_SIZE episodes marked for download"
                                    } else {
                                        "${selectedEpisodes.size} / $MAX_VOD_DOWNLOAD_QUEUE_SIZE Serien-Folgen für Download markiert"
                                    },
                                    color = AccentEmerald,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = if (isEnglish) {
                                        "Sequential queue: Downloads strictly 1 episode at a time"
                                    } else {
                                        "Werden im Download-Manager nacheinander geladen (immer nur 1 Download gleichzeitig)"
                                    },
                                    color = TextSecondary,
                                    fontSize = 10.sp
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { selectedEpisodes = emptyList() },
                                    modifier = Modifier.height(34.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRose),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentRose.copy(alpha = 0.5f)),
                                    contentPadding = PaddingValues(horizontal = 10.dp)
                                ) {
                                    Text(
                                        text = if (isEnglish) "Clear" else "Auswahl leeren",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Button(
                                    onClick = {
                                        onEnqueueEpisodes(selectedEpisodes)
                                        selectedEpisodes = emptyList()
                                    },
                                    modifier = Modifier
                                        .height(34.dp)
                                        .testTag("series_enqueue_marked_episodes_button"),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AccentEmerald,
                                        contentColor = Color.Black
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isEnglish) {
                                            "To Download Manager (${selectedEpisodes.size})"
                                        } else {
                                            "In Download-Manager (${selectedEpisodes.size})"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isLoadingEpisodes) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = GoldPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isEnglish) "Loading episodes..." else "Episoden werden geladen...",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // 5. Episode Cards
        items(
            items = episodesInCurrentSeason,
            key = { it.id }
        ) { ep ->
            val selectionIndex = selectedEpisodes.indexOfFirst { it.id == ep.id }
            val isMarked = selectionIndex >= 0
            val recItem = recordingsByUrl[ep.streamUrl]
            EpisodeRowItem(
                episode = ep,
                recordingItem = recItem,
                isMarkedForDownload = isMarked,
                selectionNumber = if (isMarked) selectionIndex + 1 else null,
                onToggleMarkForDownload = {
                    selectedEpisodes = if (isMarked) {
                        selectedEpisodes.filterNot { it.id == ep.id }
                    } else if (selectedEpisodes.size < MAX_VOD_DOWNLOAD_QUEUE_SIZE) {
                        selectedEpisodes + ep
                    } else {
                        selectedEpisodes
                    }
                },
                onClick = { onOpenEpisodeDetails(ep) },
                onQuickPlay = { onPlayEpisode(ep) },
                onDownload = { onDownloadEpisode(ep) }
            )
        }
    }
}

/**
 * Single Episode Card in the Season List.
 */
@Composable
private fun EpisodeRowItem(
    episode: UnifiedEpisode,
    recordingItem: RecordingItem? = null,
    isMarkedForDownload: Boolean = false,
    selectionNumber: Int? = null,
    onToggleMarkForDownload: () -> Unit = {},
    onClick: () -> Unit,
    onQuickPlay: () -> Unit,
    onDownload: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = if (isFocused || isMarkedForDownload) 2.dp else 1.dp,
                color = when {
                    isMarkedForDownload -> AccentEmerald
                    isFocused -> GoldPrimary
                    else -> BorderGlow
                },
                shape = RoundedCornerShape(10.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mark Checkbox for Sequential Download Manager Queue (up to 99 items)
            Surface(
                color = if (isMarkedForDownload) AccentEmerald else DarkSurface,
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isMarkedForDownload) Color.Black else GoldPrimary.copy(alpha = 0.6f)
                ),
                modifier = Modifier
                    .clickable { onToggleMarkForDownload() }
                    .testTag("episode_mark_checkbox_${episode.id}")
            ) {
                Text(
                    text = if (isMarkedForDownload && selectionNumber != null) "☑ #$selectionNumber" else "☐",
                    color = if (isMarkedForDownload) Color.Black else GoldLight,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Thumbnail / Icon with Play overlay
            Box(
                modifier = Modifier
                    .width(76.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(DarkSurface),
                contentAlignment = Alignment.Center
            ) {
                if (!episode.thumbnail.isNullOrBlank()) {
                    AsyncImage(
                        model = episode.thumbnail,
                        contentDescription = episode.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Play Button overlay
                IconButton(
                    onClick = onQuickPlay,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Abspielen",
                        tint = GoldPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Episode Details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "E${episode.episodeNumber}",
                        fontWeight = FontWeight.Bold,
                        color = GoldPrimary,
                        fontSize = 12.sp
                    )

                    Text(
                        text = episode.title,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!episode.description.isNullOrBlank()) {
                    Text(
                        text = episode.description,
                        fontSize = 10.sp,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (episode.durationMinutes > 0) {
                        Text(
                            text = "${episode.durationMinutes} Min.",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }

                    if (episode.alternativeStreams.size > 1) {
                        Text(
                            text = "• ${episode.alternativeStreams.size} Streams",
                            fontSize = 10.sp,
                            color = AccentCyan
                        )
                    }

                    when (recordingItem?.status) {
                        RecordingStatus.DOWNLOADING -> {
                            Text(
                                text = "⬇ Lädt ${recordingItem.progressPercent}%",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = AccentCyan
                            )
                        }
                        RecordingStatus.QUEUED -> {
                            Text(
                                text = "⏳ Warteschlange",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = GoldLight
                            )
                        }
                        RecordingStatus.DOWNLOADED -> {
                            Text(
                                text = "✓ Offline MP4",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = AccentEmerald
                            )
                        }
                        else -> {}
                    }

                    if (episode.isWatched) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = AccentEmerald,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "Gesehen",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentEmerald
                            )
                        }
                    } else if (episode.progress != null && episode.progress.progressPercent > 0) {
                        Text(
                            text = "${episode.progress.progressPercent}%",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldLight
                        )
                    }
                }
            }

            // Direct Episode Offline MP4 Download Button
            IconButton(
                onClick = onDownload,
                modifier = Modifier
                    .size(36.dp)
                    .background(DarkSurface, CircleShape)
                    .border(1.dp, AccentEmerald.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Episode in Download-Manager eintragen (MP4)",
                    tint = AccentEmerald,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Episode Details Modal Dialog:
 * Shows synopsis, stream failover options, Play, Resume, and Restart buttons.
 */
@Composable
private fun EpisodeDetailDialog(
    episode: UnifiedEpisode,
    seriesTitle: String,
    isEnglish: Boolean,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onRestart: () -> Unit,
    onMarkWatched: (Boolean) -> Unit,
    onDownload: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = seriesTitle,
                    fontSize = 13.sp,
                    color = GoldLight,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Staffel ${episode.seasonNumber} Episode ${episode.episodeNumber}: ${episode.title}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!episode.description.isNullOrBlank()) {
                    Text(
                        text = episode.description,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (episode.durationMinutes > 0) {
                        Text(
                            text = "Dauer: ${episode.durationMinutes} Min.",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                    episode.language?.let {
                        Text(text = "• Sprache: $it", fontSize = 11.sp, color = TextMuted)
                    }
                }

                // Alternative Streams / Multi-Provider options
                if (episode.alternativeStreams.size > 1) {
                    Text(
                        text = "Verfügbare Streams (${episode.alternativeStreams.size}):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan
                    )
                    episode.alternativeStreams.forEachIndexed { idx, opt ->
                        Text(
                            text = "Stream ${idx + 1}: ${opt.providerName} (${opt.quality})",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                // Playback Progress
                episode.progress?.let { prog ->
                    if (prog.progressPercent > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Bisheriger Fortschritt: ${prog.progressPercent}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldLight
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onPlay,
                colors = ButtonDefaults.buttonColors(
                    containerColor = GoldPrimary,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (episode.progress != null && episode.progress.progressPercent > 0 && !episode.isWatched) {
                        if (isEnglish) "Resume (${episode.progress.progressPercent}%)" else "Fortsetzen (${episode.progress.progressPercent}%)"
                    } else {
                        if (isEnglish) "Play" else "Abspielen"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(
                    onClick = {
                        onDownload()
                        onDismiss()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentEmerald),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Download MP4", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                if (episode.progress != null && episode.progress.progressPercent > 0) {
                    OutlinedButton(
                        onClick = onRestart,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Von vorne", fontSize = 11.sp)
                    }
                }

                OutlinedButton(
                    onClick = {
                        onMarkWatched(!episode.isWatched)
                        onDismiss()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (episode.isWatched) AccentEmerald else TextSecondary
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (episode.isWatched) "Als ungesehen" else "Als gesehen",
                        fontSize = 11.sp
                    )
                }
            }
        },
        containerColor = DarkNavyCard,
        shape = RoundedCornerShape(14.dp)
    )
}
