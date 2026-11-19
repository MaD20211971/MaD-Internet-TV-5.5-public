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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.data.model.ChannelItem
import com.example.data.model.MAX_VOD_DOWNLOAD_QUEUE_SIZE
import com.example.data.model.RecordingItem
import com.example.data.model.RecordingStatus
import com.example.data.parser.ContentClassifier
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

enum class MovieLanguageFilter {
    ALL,          // Alle Filme (DE: -> AT: -> CH: -> ADULT -> Andere)
    DE,           // 🇩🇪 DE: Deutsch
    AT,           // 🇦🇹 AT: Österreich
    CH,           // 🇨🇭 CH: Schweiz
    ADULT,        // 🔞 ADULT
    ENGLISH_ONLY, // 🇬🇧 Englisch / US / UK
    INTERNATIONAL // 🌍 Weitere Länder
}

private data class MovieSortEntry(
    val item: ChannelItem,
    val isFavorite: Boolean,
    val categoryPriority: Int,
    val vodLanguageRank: Int,
    val providerSortOrder: Int,
    val cleanSortTitle: String
)

@Composable
fun MovieExplorer(
    movies: List<ChannelItem>,
    recordings: List<RecordingItem> = emptyList(),
    onPlayMovie: (ChannelItem) -> Unit,
    onDownloadMovie: (ChannelItem) -> Unit,
    onEnqueueMovies: (List<ChannelItem>) -> Unit = { list -> list.forEach(onDownloadMovie) },
    onOpenDownloadManager: () -> Unit = {},
    isEnglish: Boolean = false
) {
    var selectedLanguageFilter by remember { mutableStateOf(MovieLanguageFilter.ALL) }
    var selectedCategoryFolder by remember { mutableStateOf<String?>(null) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedMovieIds by remember { mutableStateOf<List<String>>(emptyList()) }

    fun toggleMarkMovie(movieId: String) {
        selectedMovieIds = if (selectedMovieIds.contains(movieId)) {
            selectedMovieIds - movieId
        } else if (selectedMovieIds.size < MAX_VOD_DOWNLOAD_QUEUE_SIZE) {
            selectedMovieIds + movieId
        } else {
            selectedMovieIds
        }
    }

    // Asynchronous background filtering and sorting (prevents UI freeze on 20,000+ items)
    var filteredMovies by remember { mutableStateOf<List<ChannelItem>>(movies) }
    var movieFolders by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var isFilteringMovies by remember { mutableStateOf(false) }

    LaunchedEffect(movies, selectedLanguageFilter) {
        if (movies.isEmpty()) {
            filteredMovies = emptyList()
            movieFolders = emptyList()
            return@LaunchedEffect
        }
        isFilteringMovies = true
        val (filtered, folders) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val f = when (selectedLanguageFilter) {
                MovieLanguageFilter.DE -> {
                    val de = movies.filter {
                        !ContentClassifier.isAdultContent(it.name, it.groupTitle) &&
                        (it.countryCode == "DE" || it.normalizedCountryCode == "DE" || it.vodLanguageRank == 1)
                    }
                    if (de.isNotEmpty()) de else movies.filter { it.vodLanguageRank != 4 }
                }
                MovieLanguageFilter.AT -> {
                    movies.filter {
                        !ContentClassifier.isAdultContent(it.name, it.groupTitle) &&
                        (it.countryCode == "AT" || it.normalizedCountryCode == "AT" ||
                         ContentClassifier.isAtOrAustriaContent(it.name, it.groupTitle))
                    }
                }
                MovieLanguageFilter.CH -> {
                    movies.filter {
                        !ContentClassifier.isAdultContent(it.name, it.groupTitle) &&
                        (it.countryCode == "CH" || it.normalizedCountryCode == "CH" ||
                         it.groupTitle.contains("CH", ignoreCase = true) ||
                         it.groupTitle.contains("SCHWEIZ", ignoreCase = true) ||
                         it.groupTitle.contains("SWISS", ignoreCase = true))
                    }
                }
                MovieLanguageFilter.ADULT -> movies.filter {
                    !ContentClassifier.isAtOrAustriaContent(it.name, it.groupTitle) &&
                    it.countryCode.uppercase() != "AT" &&
                    it.normalizedCountryCode != "AT" &&
                    (it.vodLanguageRank == 4 ||
                     ContentClassifier.isAdultContent(it.name, it.groupTitle) ||
                     it.countryCode.uppercase() == "ADULT" ||
                     ContentClassifier.isAdultGroup(it.groupTitle))
                }
                MovieLanguageFilter.ENGLISH_ONLY -> {
                    val en = movies.filter {
                        !ContentClassifier.isAdultContent(it.name, it.groupTitle) &&
                        (it.vodLanguageRank == 2 || it.countryCode.uppercase() in listOf("US", "UK", "GB"))
                    }
                    if (en.isNotEmpty()) en else movies.filter { it.vodLanguageRank != 4 }
                }
                MovieLanguageFilter.INTERNATIONAL -> movies.filter {
                    !ContentClassifier.isAdultContent(it.name, it.groupTitle) &&
                    it.countryCode.uppercase() !in listOf("DE", "AT", "CH", "ADULT") &&
                    it.vodLanguageRank != 4
                }
                MovieLanguageFilter.ALL -> movies
            }
            val sorted = if (selectedLanguageFilter == MovieLanguageFilter.ADULT) {
                f.map { item ->
                    Triple(
                        item,
                        if (item.providerSortOrder > 0) item.providerSortOrder else Int.MAX_VALUE,
                        item.cleanSortTitle
                    )
                }.sortedWith(
                    compareBy<Triple<ChannelItem, Int, String>> { it.second }
                        .thenBy { it.first.groupTitle }
                        .thenBy { it.third }
                ).map { it.first }
            } else if (selectedLanguageFilter == MovieLanguageFilter.ALL) {
                // movies from MainViewModel is already pre-sorted
                f
            } else {
                f.map { item ->
                    MovieSortEntry(
                        item = item,
                        isFavorite = item.isFavorite,
                        categoryPriority = ContentClassifier.getCategoryPriority(item.groupTitle),
                        vodLanguageRank = item.vodLanguageRank,
                        providerSortOrder = if (item.providerSortOrder > 0) item.providerSortOrder else Int.MAX_VALUE,
                        cleanSortTitle = item.cleanSortTitle
                    )
                }.sortedWith(
                    compareByDescending<MovieSortEntry> { it.isFavorite }
                        .thenBy { it.categoryPriority }
                        .thenBy { it.vodLanguageRank }
                        .thenBy { it.providerSortOrder }
                        .thenBy { it.cleanSortTitle }
                ).map { it.item }
            }
            // Preserve provider category & subcategory structure in native provider order
            val foldersResult = sorted
                .groupBy { it.groupTitle.ifBlank { "VOD Filme" } }
                .entries
                .map { (folder, list) ->
                    val minOrder = list.minOfOrNull { if (it.providerSortOrder > 0) it.providerSortOrder else Int.MAX_VALUE } ?: Int.MAX_VALUE
                    Triple(folder, list.size, minOrder)
                }
                .sortedWith(
                    compareBy<Triple<String, Int, Int>> { ContentClassifier.getCategoryPriority(it.first) }
                        .thenBy { it.third }
                        .thenBy { it.first }
                )
                .map { Pair(it.first, it.second) }
            Pair(sorted, foldersResult)
        }
        filteredMovies = filtered
        movieFolders = folders
        isFilteringMovies = false
    }

    var displayedMovies by remember { mutableStateOf<List<ChannelItem>>(emptyList()) }
    LaunchedEffect(filteredMovies, selectedCategoryFolder) {
        val folder = selectedCategoryFolder
        displayedMovies = if (folder == null || folder.equals("ALLE", ignoreCase = true)) {
            filteredMovies
        } else {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                filteredMovies.filter {
                    it.groupTitle.equals(folder, ignoreCase = true) ||
                    it.subCategory.equals(folder, ignoreCase = true)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Priority Filter Chips: VOD Download-Manager + ALLE, DE:, AT:, CH:, ADULT, EN, INT
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            item {
                val activeCount = recordings.count {
                    it.status == RecordingStatus.DOWNLOADING ||
                        it.status == RecordingStatus.QUEUED ||
                        it.status == RecordingStatus.RECORDING ||
                        it.status == RecordingStatus.SCHEDULED
                }
                val offlineCount = recordings.count { it.status == RecordingStatus.DOWNLOADED }
                FilterChip(
                    selected = activeCount > 0,
                    onClick = onOpenDownloadManager,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "VOD Download Manager",
                            tint = Color(0xFF22C55E),
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    label = {
                        Text(
                            text = if (activeCount > 0) {
                                "DOWNLOADS ($activeCount/$MAX_VOD_DOWNLOAD_QUEUE_SIZE • $offlineCount MP4)"
                            } else {
                                "DOWNLOAD-MANAGER ($offlineCount MP4)"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF22C55E).copy(alpha = 0.22f),
                        selectedLabelColor = Color(0xFF22C55E),
                        containerColor = DarkSurfaceElevated,
                        labelColor = Color(0xFF22C55E)
                    ),
                    modifier = Modifier.testTag("movies_open_download_manager_button")
                )
            }
            item {
                FilterChip(
                    selected = isSelectionMode || selectedMovieIds.isNotEmpty(),
                    onClick = { isSelectionMode = !isSelectionMode },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isSelectionMode || selectedMovieIds.isNotEmpty()) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                            contentDescription = "Filme für Download markieren",
                            tint = if (isSelectionMode || selectedMovieIds.isNotEmpty()) Color.Black else GoldPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    label = {
                        Text(
                            text = if (selectedMovieIds.isNotEmpty()) {
                                "MARKIERT (${selectedMovieIds.size}/$MAX_VOD_DOWNLOAD_QUEUE_SIZE)"
                            } else {
                                "FILME MARKIEREN (MAX $MAX_VOD_DOWNLOAD_QUEUE_SIZE)"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GoldPrimary,
                        selectedLabelColor = Color.Black,
                        containerColor = DarkSurfaceElevated,
                        labelColor = GoldPrimary
                    ),
                    modifier = Modifier.testTag("movies_toggle_mark_mode_button")
                )
            }
            item {
                FilterChip(
                    selected = selectedLanguageFilter == MovieLanguageFilter.ALL,
                    onClick = {
                        selectedLanguageFilter = MovieLanguageFilter.ALL
                        selectedCategoryFolder = null
                    },
                    label = { Text(if (isEnglish) "ALL MOVIES" else "ALLE FILME", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BorderGlow,
                        selectedLabelColor = Color.White,
                        containerColor = DarkSurfaceElevated,
                        labelColor = TextPrimary
                    )
                )
            }
            item {
                FilterChip(
                    selected = selectedLanguageFilter == MovieLanguageFilter.DE,
                    onClick = {
                        selectedLanguageFilter = MovieLanguageFilter.DE
                        selectedCategoryFolder = null
                    },
                    label = { Text("🇩🇪 DE: DEUTSCH", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GoldPrimary,
                        selectedLabelColor = Color.Black,
                        containerColor = DarkSurfaceElevated,
                        labelColor = TextPrimary
                    )
                )
            }
            item {
                FilterChip(
                    selected = selectedLanguageFilter == MovieLanguageFilter.AT,
                    onClick = {
                        selectedLanguageFilter = MovieLanguageFilter.AT
                        selectedCategoryFolder = null
                    },
                    label = { Text("🇦🇹 AT: ÖSTERREICH", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GoldPrimary,
                        selectedLabelColor = Color.Black,
                        containerColor = DarkSurfaceElevated,
                        labelColor = TextPrimary
                    )
                )
            }
            item {
                FilterChip(
                    selected = selectedLanguageFilter == MovieLanguageFilter.CH,
                    onClick = {
                        selectedLanguageFilter = MovieLanguageFilter.CH
                        selectedCategoryFolder = null
                    },
                    label = { Text("🇨🇭 CH: SCHWEIZ", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GoldPrimary,
                        selectedLabelColor = Color.Black,
                        containerColor = DarkSurfaceElevated,
                        labelColor = TextPrimary
                    )
                )
            }
            item {
                FilterChip(
                    selected = selectedLanguageFilter == MovieLanguageFilter.ADULT,
                    onClick = {
                        selectedLanguageFilter = MovieLanguageFilter.ADULT
                        selectedCategoryFolder = null
                    },
                    label = { Text("🔞 ADULT / 18+", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentRose,
                        selectedLabelColor = Color.White,
                        containerColor = DarkSurfaceElevated,
                        labelColor = TextPrimary
                    )
                )
            }
            item {
                FilterChip(
                    selected = selectedLanguageFilter == MovieLanguageFilter.ENGLISH_ONLY,
                    onClick = {
                        selectedLanguageFilter = MovieLanguageFilter.ENGLISH_ONLY
                        selectedCategoryFolder = null
                    },
                    label = { Text("🇬🇧 US / UK", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentCyan,
                        selectedLabelColor = Color.Black,
                        containerColor = DarkSurfaceElevated,
                        labelColor = TextPrimary
                    )
                )
            }
            item {
                FilterChip(
                    selected = selectedLanguageFilter == MovieLanguageFilter.INTERNATIONAL,
                    onClick = {
                        selectedLanguageFilter = MovieLanguageFilter.INTERNATIONAL
                        selectedCategoryFolder = null
                    },
                    label = { Text("🌍 WEITERE LÄNDER", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentCyan,
                        selectedLabelColor = Color.Black,
                        containerColor = DarkSurfaceElevated,
                        labelColor = TextPrimary
                    )
                )
            }
        }

        // Provider Category & Sub-Category filter chips in native provider order
        if (movieFolders.size > 1) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                item {
                    val isAllSelected = selectedCategoryFolder == null || selectedCategoryFolder.equals("ALLE", ignoreCase = true)
                    FilterChip(
                        selected = isAllSelected,
                        onClick = { selectedCategoryFolder = null },
                        label = {
                            Text(
                                text = "📁 Alle Kategorien (${filteredMovies.size})",
                                fontSize = 11.sp,
                                fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium
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
                items(movieFolders) { (folderName, count) ->
                    val isFolderSelected = selectedCategoryFolder.equals(folderName, ignoreCase = true)
                    val cleanFolder = ContentClassifier.getCleanSubFolderName(folderName).ifBlank { folderName }
                    val icon = when {
                        folderName.contains("Kino", ignoreCase = true) -> "🎬"
                        folderName.contains("Netflix", ignoreCase = true) -> "🍿"
                        folderName.contains("Amazon", ignoreCase = true) -> "⭐"
                        folderName.contains("Disney", ignoreCase = true) -> "✨"
                        folderName.contains("4K", ignoreCase = true) -> "⚡"
                        folderName.contains("Adult", ignoreCase = true) || folderName.contains("XXX", ignoreCase = true) || folderName.contains("18+", ignoreCase = true) -> "🔞"
                        else -> "📁"
                    }
                    FilterChip(
                        selected = isFolderSelected,
                        onClick = {
                            selectedCategoryFolder = if (isFolderSelected) null else folderName
                        },
                        label = {
                            Text(
                                text = "$icon $cleanFolder ($count)",
                                fontSize = 11.sp,
                                fontWeight = if (isFolderSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentCyan,
                            selectedLabelColor = Color.Black,
                            containerColor = DarkSurfaceElevated,
                            labelColor = TextPrimary
                        )
                    )
                }
            }
        }

        // Header Title & Count
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Movie,
                contentDescription = "Movies",
                tint = GoldPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            val headerTitle = if (selectedCategoryFolder != null) {
                selectedCategoryFolder!!
            } else {
                when (selectedLanguageFilter) {
                    MovieLanguageFilter.DE -> if (isEnglish) "DE: German Movies" else "DE: Deutsche Filme & Mediathek"
                    MovieLanguageFilter.AT -> if (isEnglish) "AT: Austrian Movies" else "AT: Österreich Filme & VOD"
                    MovieLanguageFilter.CH -> if (isEnglish) "CH: Swiss Movies" else "CH: Schweiz Filme & VOD"
                    MovieLanguageFilter.ADULT -> if (isEnglish) "🔞 Adult 18+ Movies" else "🔞 ADULT: 18+ Filme & VOD"
                    MovieLanguageFilter.ENGLISH_ONLY -> if (isEnglish) "US / UK Movies" else "US / UK Englische Filme & VOD"
                    MovieLanguageFilter.INTERNATIONAL -> "Weitere Länder Filme & VOD"
                    MovieLanguageFilter.ALL -> if (isEnglish) "All Movies" else "Alle Filme & VOD"
                }
            }
            Text(
                text = headerTitle,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = GoldLight,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "(${displayedMovies.size} Filme)",
                fontSize = 11.sp,
                color = TextMuted
            )
            if (selectedCategoryFolder != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "✖ Alle",
                    fontSize = 11.sp,
                    color = AccentRose,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { selectedCategoryFolder = null }
                )
            }
        }

        // Batch Selection Bar for marking up to 99 movies for sequential download
        if (isSelectionMode || selectedMovieIds.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                color = DarkNavyCard,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, GoldPrimary.copy(alpha = 0.7f))
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
                            text = "${selectedMovieIds.size} / $MAX_VOD_DOWNLOAD_QUEUE_SIZE Filme für Download markiert",
                            color = GoldPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Werden in den Download-Manager eingetragen und einzeln nacheinander geladen.",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                val candidates = displayedMovies
                                    .map { it.id }
                                    .filter { id -> !selectedMovieIds.contains(id) }
                                val space = (MAX_VOD_DOWNLOAD_QUEUE_SIZE - selectedMovieIds.size).coerceAtLeast(0)
                                selectedMovieIds = selectedMovieIds + candidates.take(space)
                            },
                            modifier = Modifier.height(34.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan)
                        ) {
                            Text(
                                text = "Max. $MAX_VOD_DOWNLOAD_QUEUE_SIZE füllen",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (selectedMovieIds.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { selectedMovieIds = emptyList() },
                                modifier = Modifier.height(34.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRose)
                            ) {
                                Text(
                                    text = "Leeren",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = {
                                    val movieMap = movies.associateBy { it.id }
                                    val orderedMovies = selectedMovieIds.mapNotNull { movieMap[it] }
                                    if (orderedMovies.isNotEmpty()) {
                                        onEnqueueMovies(orderedMovies)
                                        selectedMovieIds = emptyList()
                                        isSelectionMode = false
                                    }
                                },
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("movies_enqueue_selected_button"),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = GoldPrimary,
                                    contentColor = Color.Black
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "In Download-Manager (${selectedMovieIds.size})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }
        }

        if (displayedMovies.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isEnglish) "No movies found for selected filter." else "Keine Filme in diesem Filter vorhanden.",
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(
                    items = displayedMovies,
                    key = { it.id },
                    contentType = { "movie_card" }
                ) { movie ->
                    val downloadItem = remember(recordings, movie.streamUrl, movie.name) {
                        recordings.find {
                            it.streamUrl == movie.streamUrl || it.title.equals(movie.name, ignoreCase = true)
                        }
                    }
                    val isDownloading = downloadItem?.status == RecordingStatus.DOWNLOADING
                    val isQueued = downloadItem?.status == RecordingStatus.QUEUED
                    val isDownloaded = downloadItem?.status == RecordingStatus.DOWNLOADED || downloadItem?.status == RecordingStatus.COMPLETED
                    val markOrderIndex = selectedMovieIds.indexOf(movie.id)
                    val isMarkedForDownload = markOrderIndex >= 0

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                width = if (isMarkedForDownload) 2.dp else 1.dp,
                                color = when {
                                    isMarkedForDownload -> GoldPrimary
                                    isDownloading -> AccentCyan.copy(alpha = 0.85f)
                                    isQueued -> GoldPrimary.copy(alpha = 0.75f)
                                    isDownloaded -> Color(0xFF00E676).copy(alpha = 0.6f)
                                    else -> BorderGlow.copy(alpha = 0.3f)
                                },
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                if (isSelectionMode) {
                                    toggleMarkMovie(movie.id)
                                } else {
                                    onPlayMovie(movie)
                                }
                            }
                            .testTag("movie_card_${movie.id}"),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface)
                    ) {
                        Column {
                            // Poster with Overlaid Badges
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .background(ObsidianBlack),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!movie.logoUrl.isNullOrBlank()) {
                                    val context = LocalContext.current
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(movie.logoUrl)
                                            .crossfade(true)
                                            .size(coil.size.Size(360, 500))
                                            .memoryCachePolicy(CachePolicy.ENABLED)
                                            .diskCachePolicy(CachePolicy.ENABLED)
                                            .build(),
                                        contentDescription = movie.name,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Movie,
                                        contentDescription = "Movie",
                                        tint = GoldPrimary,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }

                                // Dark Gradient at bottom of poster
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(70.dp)
                                        .align(Alignment.BottomCenter)
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(Color.Transparent, DarkSurface)
                                            )
                                        )
                                    )

                                // Country and Resolution Badges on Top Left
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val isAdult = movie.countryCode.uppercase() == "ADULT" || ContentClassifier.isAdultContent(movie.name, movie.groupTitle)
                                    val flagText = when {
                                        isAdult -> "🔞 18+"
                                        movie.countryCode.uppercase() == "DE" -> "🇩🇪 DE"
                                        movie.countryCode.uppercase() == "AT" -> "🇦🇹 AT"
                                        movie.countryCode.uppercase() == "CH" -> "🇨🇭 CH"
                                        movie.countryCode.uppercase() == "EXYU" -> "🇷🇸 EX-YU"
                                        movie.countryCode.uppercase() == "TR" -> "🇹🇷 TR"
                                        movie.countryCode.uppercase() in listOf("UK", "GB") -> "🇬🇧 UK"
                                        movie.countryCode.uppercase() == "US" -> "🇺🇸 US"
                                        movie.countryCode.uppercase() == "IT" -> "🇮🇹 IT"
                                        movie.countryCode.uppercase() == "FR" -> "🇫🇷 FR"
                                        movie.countryCode.uppercase() == "ES" -> "🇪🇸 ES"
                                        else -> if (movie.isDach) "🇩🇪 DE" else "🌐 INT"
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                when {
                                                    isAdult -> AccentRose
                                                    movie.countryCode.uppercase() in listOf("AT", "CH") -> AccentRose
                                                    movie.countryCode.uppercase() == "DE" -> GoldPrimary
                                                    else -> if (movie.isDach) GoldPrimary else DarkNavyCard
                                                }
                                            )
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = flagText,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            color = when {
                                                isAdult -> Color.White
                                                movie.countryCode.uppercase() in listOf("AT", "CH") -> Color.White
                                                movie.countryCode.uppercase() == "DE" -> Color.Black
                                                else -> if (movie.isDach) Color.Black else TextPrimary
                                            }
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(AccentCyan.copy(alpha = 0.85f))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = movie.resolutionBadge,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black
                                        )
                                    }
                                }

                                // Download Status Badge / Mark Checkbox & Direct Download Button on Top Right
                                if (isDownloading) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(ObsidianBlack.copy(alpha = 0.85f))
                                            .border(1.dp, AccentCyan, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(
                                                progress = { (downloadItem?.progressPercent ?: 0) / 100f },
                                                modifier = Modifier.size(12.dp),
                                                color = AccentCyan,
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "${downloadItem?.progressPercent ?: 0}% Lädt",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AccentCyan
                                            )
                                        }
                                    }
                                } else if (isQueued) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(ObsidianBlack.copy(alpha = 0.88f))
                                            .border(1.dp, GoldPrimary, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Schedule,
                                                contentDescription = null,
                                                tint = GoldPrimary,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "Warteschlange",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = GoldPrimary
                                            )
                                        }
                                    }
                                } else if (isDownloaded) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF00E676))
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = Color.Black,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "Offline MP4",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.Black
                                            )
                                        }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Mark for sequential download queue (up to 99)
                                        Box(
                                            modifier = Modifier
                                                .height(30.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (isMarkedForDownload) GoldPrimary else ObsidianBlack.copy(alpha = 0.78f)
                                                )
                                                .border(
                                                    1.dp,
                                                    if (isMarkedForDownload) GoldPrimary else GoldPrimary.copy(alpha = 0.55f),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .clickable { toggleMarkMovie(movie.id) }
                                                .padding(horizontal = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = if (isMarkedForDownload) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                                    contentDescription = "Für Download markieren",
                                                    tint = if (isMarkedForDownload) Color.Black else GoldPrimary,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                if (isMarkedForDownload) {
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    Text(
                                                        text = "#${markOrderIndex + 1}",
                                                        color = Color.Black,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.ExtraBold
                                                    )
                                                }
                                            }
                                        }

                                        // Direct single enqueue button
                                        IconButton(
                                            onClick = { onDownloadMovie(movie) },
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(CircleShape)
                                                .background(ObsidianBlack.copy(alpha = 0.78f))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = "In Download-Manager eintragen",
                                                tint = AccentCyan,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                // Big Play Floating Button
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(GoldPrimary)
                                        .align(Alignment.Center)
                                        .clickable { onPlayMovie(movie) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        tint = Color.Black,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }

                            // Text details
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = movie.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (isDownloading) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { (downloadItem?.progressPercent ?: 0) / 100f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = AccentCyan,
                                        trackColor = DarkSurfaceElevated
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${downloadItem?.progressPercent ?: 0}% Heruntergeladen",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentCyan
                                    )
                                } else if (isQueued) {
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Schedule,
                                            contentDescription = null,
                                            tint = GoldPrimary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "In Download-Warteschlange",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = GoldPrimary
                                        )
                                    }
                                } else if (isDownloaded) {
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF00E676),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "Download abgeschlossen (MP4)",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF00E676)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = movie.groupTitle,
                                    fontSize = 10.sp,
                                    color = TextMuted,
                                    maxLines = 1
                                )

                                if (!movie.plot.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = movie.plot,
                                        fontSize = 11.sp,
                                        color = TextSecondary,
                                        maxLines = 2,
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

