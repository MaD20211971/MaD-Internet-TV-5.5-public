package com.example.ui.viewmodel

import com.example.data.country.CountryResolver
import com.example.data.country.DachMasterDatabase
import com.example.data.country.DachMatchResult
import com.example.data.country.NaturalOrderComparator
import com.example.data.country.StreamQualityRanker
import com.example.data.model.ChannelItem
import com.example.data.model.ChannelSortMode
import com.example.data.model.ContentType
import com.example.data.parser.ContentClassifier
import com.example.util.FastLruCache

data class ChannelBadgeCounts(
    val liveCount: Int = 0,
    val favoriteCount: Int = 0,
    val sportCount: Int = 0,
    val adultCount: Int = 0,
    val internationalCount: Int = 0,
    val radioCount: Int = 0,
    val movieCount: Int = 0,
    val seriesCount: Int = 0,
    val docCount: Int = 0
)

/**
 * Background-threaded high-performance filter & sort engine.
 * Moves all expensive linear iterations, regular expressions, and sorting
 * off the Android Main/UI Thread onto background dispatchers to prevent
 * UI freezes, jank, and Android ANR ("Application Not Responding") dialogs.
 */
object ChannelFilterEngine {

    private val NON_DE_COUNTRIES = listOf(
        "EXYU", "TR", "UK", "US", "IT", "FR", "ES", "CA", "IN", "AF", "PL", "NL", "AL", "AR", "RU", "PT", "BR", "GR", "RO", "SE", "LATAM"
    )

    fun calculateBadgeCounts(channels: List<ChannelItem>): ChannelBadgeCounts {
        var live = 0
        var fav = 0
        var sport = 0
        var adult = 0
        var intl = 0
        var radio = 0
        var movie = 0
        var series = 0
        var doc = 0

        for (ch in channels) {
            if (ContentClassifier.isDocumentaryContent(ch.name, ch.groupTitle)) {
                doc++
            }
            when (ch.type) {
                ContentType.LIVE_TV -> {
                    val isSport = ContentClassifier.isSportContent(ch.name, ch.groupTitle)
                    val isAt = ContentClassifier.isAtOrAustriaContent("", ch.groupTitle) || ch.countryCode.uppercase() == "AT" || ch.normalizedCountryCode == "AT"
                    val isAdult = !isAt && (ContentClassifier.isAdultGroup(ch.groupTitle) || ch.countryCode.equals("ADULT", ignoreCase = true))
                    if (ch.isFavorite) fav++
                    if (isSport) sport++
                    if (isAdult) adult++
                    live++
                    if (!isSport && !isAdult && (ch.countryCode in NON_DE_COUNTRIES || (!ch.isDach && ch.countryCode != "DE" && ch.countryCode != "AT" && ch.countryCode != "CH"))) {
                        intl++
                    }
                }
                ContentType.RADIO -> radio++
                ContentType.MOVIE -> movie++
                ContentType.SERIES -> series++
                ContentType.UNKNOWN -> live++
            }
        }

        return ChannelBadgeCounts(
            liveCount = live,
            favoriteCount = fav,
            sportCount = sport,
            adultCount = adult,
            internationalCount = intl,
            radioCount = radio,
            movieCount = movie,
            seriesCount = series,
            docCount = doc
        )
    }

    fun extractCategories(channels: List<ChannelItem>, selectedTab: MainTab): List<String> {
        val targetChannels = when (selectedTab) {
            MainTab.LIVE_TV -> channels.filter { it.type == ContentType.LIVE_TV }
            MainTab.MOVIES -> channels.filter { it.type == ContentType.MOVIE }
            MainTab.SERIES -> channels.filter { it.type == ContentType.SERIES }
            MainTab.RADIO -> channels.filter { it.type == ContentType.RADIO }
            else -> channels
        }
        val rawCategories = targetChannels.map { it.groupTitle }.filter { it.isNotBlank() }.toMutableList()
        val hasAdult = targetChannels.any {
            !ContentClassifier.isAtOrAustriaContent("", it.groupTitle) && it.countryCode.uppercase() != "AT" &&
            (it.countryCode.equals("ADULT", ignoreCase = true) || ContentClassifier.isAdultGroup(it.groupTitle))
        }
        if (hasAdult && rawCategories.none { ContentClassifier.detectCountryForGroup(it) == "ADULT" || it.contains("ADULT", ignoreCase = true) || it.contains("XXX", ignoreCase = true) }) {
            rawCategories.add(if (selectedTab == MainTab.MOVIES) "ADULT | Filme" else "ADULT | Live TV")
        }
        return ContentClassifier.sortCategoriesWithDachPriority(rawCategories.distinct())
    }

    /**
     * Builds structured provider category hierarchy.
     * Preserves provider category naming 1:1, organizing parent and child sub-groups.
     */
    fun extractProviderCategoryHierarchy(channels: List<ChannelItem>, selectedTab: MainTab): List<com.example.data.category.ProviderCategoryGroup> {
        val targetChannels = when (selectedTab) {
            MainTab.LIVE_TV -> channels.filter { it.type == ContentType.LIVE_TV }
            MainTab.MOVIES -> channels.filter { it.type == ContentType.MOVIE }
            MainTab.SERIES -> channels.filter { it.type == ContentType.SERIES }
            MainTab.RADIO -> channels.filter { it.type == ContentType.RADIO }
            else -> channels
        }
        return com.example.data.category.ProviderCategoryEngine.buildCategoryHierarchy(targetChannels)
    }

    fun filterAndSortLiveChannels(
        allChannels: List<ChannelItem>,
        searchQuery: String,
        selectedCategory: String,
        hiddenChannelIds: Set<String> = emptySet(),
        disabledCountries: Set<String> = emptySet(),
        sortMode: ChannelSortMode = ChannelSortMode.DACH_CANONICAL
    ): List<ChannelItem> {
        val nonHeaderChannels = allChannels.filter {
            !ContentClassifier.isHeaderOrDividerChannel(it.name, it.streamUrl, it.groupTitle) &&
            (hiddenChannelIds.isEmpty() || it.id !in hiddenChannelIds) &&
            (disabledCountries.isEmpty() || it.normalizedCountryCode !in disabledCountries)
        }
        val candidates = if (selectedCategory == "ADULT" || selectedCategory.contains("ADULT", ignoreCase = true) || selectedCategory.contains("XXX", ignoreCase = true) || selectedCategory == "18+ / Adult") {
            nonHeaderChannels
        } else if (nonHeaderChannels.any { it.type == ContentType.LIVE_TV }) {
            nonHeaderChannels.filter { it.type == ContentType.LIVE_TV }
        } else {
            nonHeaderChannels
        }

        val list = when {
            // 1. Exact match against raw group title, provider category, or subCategory (1:1 preservation)
            candidates.any {
                it.groupTitle.equals(selectedCategory, ignoreCase = true) ||
                (it.providerCategory != null && it.providerCategory.equals(selectedCategory, ignoreCase = true)) ||
                (it.subCategory.isNotBlank() && it.subCategory.equals(selectedCategory, ignoreCase = true))
            } -> {
                candidates.filter {
                    it.groupTitle.equals(selectedCategory, ignoreCase = true) ||
                    (it.providerCategory != null && it.providerCategory.equals(selectedCategory, ignoreCase = true)) ||
                    (it.subCategory.isNotBlank() && it.subCategory.equals(selectedCategory, ignoreCase = true))
                }
            }
            // 2. Hierarchical parent category match (e.g. user selected root "DE: Deutschland", "AT: Österreich", etc.)
            candidates.any {
                it.parentCategory.equals(selectedCategory, ignoreCase = true) ||
                com.example.data.category.ProviderCategoryEngine.parseCategory(it.groupTitle).parentCategory.equals(selectedCategory, ignoreCase = true)
            } -> {
                candidates.filter {
                    it.parentCategory.equals(selectedCategory, ignoreCase = true) ||
                    com.example.data.category.ProviderCategoryEngine.parseCategory(it.groupTitle).parentCategory.equals(selectedCategory, ignoreCase = true)
                }
            }
            selectedCategory == "FAVORITEN" || selectedCategory == "FAVORITES" -> {
                candidates.filter { it.isFavorite }
            }
            selectedCategory == "ALLE" || selectedCategory.isBlank() -> {
                candidates
            }
            selectedCategory == "SPORT" || selectedCategory == "SPORTS" -> {
                candidates.filter {
                    it.genre == "SPORT" ||
                    com.example.data.category.ProviderCategoryEngine.parseCategory(it.groupTitle).normalizedGenre == com.example.data.category.NormalizedGenre.SPORT ||
                    ContentClassifier.isSportContent(it.name, it.groupTitle) ||
                    it.groupTitle.contains("SPORT", ignoreCase = true) ||
                    it.countryCode.uppercase() == "SPORT"
                }
            }
            selectedCategory == "CINEMA" || selectedCategory == "KINO" || selectedCategory == "MOVIES" || selectedCategory == "FILME" -> {
                candidates.filter {
                    it.genre == "CINEMA" ||
                    com.example.data.category.ProviderCategoryEngine.parseCategory(it.groupTitle).normalizedGenre == com.example.data.category.NormalizedGenre.CINEMA ||
                    it.groupTitle.contains("CINEMA", ignoreCase = true) ||
                    it.groupTitle.contains("KINO", ignoreCase = true) ||
                    it.groupTitle.contains("MOVIES", ignoreCase = true) ||
                    it.groupTitle.contains("FILME", ignoreCase = true)
                }
            }
            selectedCategory == "NEWS" || selectedCategory == "NACHRICHTEN" -> {
                candidates.filter {
                    it.genre == "NEWS" ||
                    com.example.data.category.ProviderCategoryEngine.parseCategory(it.groupTitle).normalizedGenre == com.example.data.category.NormalizedGenre.NEWS ||
                    it.groupTitle.contains("NEWS", ignoreCase = true) ||
                    it.groupTitle.contains("NACHRICHTEN", ignoreCase = true) ||
                    it.groupTitle.contains("INFO", ignoreCase = true)
                }
            }
            selectedCategory == "ENTERTAINMENT" || selectedCategory == "UNTERHALTUNG" -> {
                candidates.filter {
                    it.genre == "ENTERTAINMENT" ||
                    com.example.data.category.ProviderCategoryEngine.parseCategory(it.groupTitle).normalizedGenre == com.example.data.category.NormalizedGenre.ENTERTAINMENT ||
                    it.groupTitle.contains("ENTERTAINMENT", ignoreCase = true) ||
                    it.groupTitle.contains("UNTERHALTUNG", ignoreCase = true)
                }
            }
            selectedCategory == "ADULT" || selectedCategory.startsWith("ADULT", ignoreCase = true) || selectedCategory.contains("ADULT", ignoreCase = true) || selectedCategory == "18+ / Adult" || selectedCategory.contains("XXX", ignoreCase = true) || selectedCategory.contains("18+", ignoreCase = true) -> {
                candidates.filter {
                    !ContentClassifier.isAtOrAustriaContent("", it.groupTitle) &&
                    it.countryCode.uppercase() != "AT" && it.normalizedCountryCode != "AT" &&
                    (it.countryCode.uppercase() == "ADULT" ||
                     it.normalizedCountryCode == "ADULT" ||
                     ContentClassifier.isAdultGroup(it.groupTitle))
                }
            }
            // Country: US (USA)
            selectedCategory == "US" || selectedCategory in listOf("US:", "US :", "US: USA", "USA", "UNITED STATES") -> {
                candidates.filter {
                    !ContentClassifier.isAdultGroup(it.groupTitle) &&
                    (it.countryCode.uppercase() == "US" ||
                     it.normalizedCountryCode == "US" ||
                     ContentClassifier.detectCountryForGroup(it.groupTitle) == "US" ||
                     it.groupTitle.uppercase().startsWith("US") ||
                     it.groupTitle.uppercase().startsWith("EU US") ||
                     it.groupTitle.contains("USA", ignoreCase = true) ||
                     it.groupTitle.contains("UNITED STATES", ignoreCase = true))
                }
            }
            // Country: UK (GB / Great Britain)
            selectedCategory == "UK" || selectedCategory in listOf("UK:", "UK :", "UK: GB", "GB", "UNITED KINGDOM", "ENGLAND") -> {
                candidates.filter {
                    !ContentClassifier.isAdultGroup(it.groupTitle) &&
                    (it.countryCode.uppercase() in listOf("UK", "GB") ||
                     it.normalizedCountryCode == "UK" ||
                     ContentClassifier.detectCountryForGroup(it.groupTitle) == "UK" ||
                     it.groupTitle.uppercase().startsWith("UK") ||
                     it.groupTitle.uppercase().startsWith("EU UK") ||
                     it.groupTitle.contains("UNITED KINGDOM", ignoreCase = true) ||
                     it.groupTitle.contains("BRITISH", ignoreCase = true))
                }
            }
            // Country: Deutschland (Priority #1: DE:)
            selectedCategory == "DE" || selectedCategory in listOf("DE:", "DE :", "DE: DEUTSCHLAND", "DEUTSCHLAND", "DE: GERMANY", "GERMANY") -> {
                candidates.filter {
                    !ContentClassifier.isAdultGroup(it.groupTitle) &&
                    (extractSortKey(it).effectiveCountry == "DE" ||
                     it.countryCode == "DE" || it.normalizedCountryCode == "DE" ||
                     ContentClassifier.detectCountryForGroup(it.groupTitle) == "DE" ||
                     it.groupTitle.uppercase().startsWith("DE") ||
                     it.groupTitle.uppercase().startsWith("EU DE"))
                }
            }
            // Country: Österreich (Priority #2: AT:)
            selectedCategory == "AT" || selectedCategory in listOf("AT:", "AT :", "AT: ÖSTERREICH", "ÖSTERREICH", "AUSTRIA", "AT: AUSTRIA") ||
            selectedCategory.contains("AUSTRIA", ignoreCase = true) || selectedCategory.contains("ÖSTERREICH", ignoreCase = true) -> {
                candidates.filter {
                    extractSortKey(it).effectiveCountry == "AT" ||
                    ContentClassifier.isAtOrAustriaContent("", it.groupTitle) ||
                    it.countryCode == "AT" || it.normalizedCountryCode == "AT" ||
                    ContentClassifier.detectCountryForGroup(it.groupTitle) == "AT" ||
                    it.groupTitle.uppercase().startsWith("AT") ||
                    it.groupTitle.uppercase().startsWith("EU AUSTRIA") ||
                    it.groupTitle.contains("AUSTRIA", ignoreCase = true) ||
                    it.groupTitle.contains("ÖSTERREICH", ignoreCase = true)
                }
            }
            // Country: Schweiz (Priority #3: CH:)
            selectedCategory == "CH" || selectedCategory in listOf("CH:", "CH :", "CH: SCHWEIZ", "SCHWEIZ", "CH: SWITZERLAND", "SWITZERLAND") -> {
                candidates.filter {
                    !ContentClassifier.isAdultGroup(it.groupTitle) &&
                    (extractSortKey(it).effectiveCountry == "CH" ||
                     it.countryCode == "CH" || it.normalizedCountryCode == "CH" ||
                     ContentClassifier.detectCountryForGroup(it.groupTitle) == "CH" ||
                     it.groupTitle.uppercase().startsWith("CH") ||
                     it.groupTitle.uppercase().startsWith("EU SWITZERLAND") ||
                     it.groupTitle.contains("SCHWEIZ", ignoreCase = true) ||
                     it.groupTitle.contains("SWISS", ignoreCase = true))
                }
            }
            // Country: UNKNOWN (Nicht zugeordnet)
            selectedCategory in listOf("UNKNOWN", "NICHT ZUGEORDNET", "❓ NICHT ZUGEORDNET", "?") -> {
                candidates.filter {
                    !ContentClassifier.isAdultGroup(it.groupTitle) &&
                    (it.normalizedCountryCode == "UNKNOWN" || it.classificationStatus == "UNKNOWN" || it.countryCode == "UNKNOWN")
                }
            }
            // Country: CONFLICT (Widersprüchliche Metadaten)
            selectedCategory in listOf("CONFLICT", "KONFLIKTE", "⚠️ KONFLIKTE") -> {
                candidates.filter {
                    it.normalizedCountryCode == "CONFLICT" || it.classificationStatus == "CONFLICT" || it.countryCode == "CONFLICT"
                }
            }
            // Country: OTHER / INTERNATIONAL
            selectedCategory in listOf("OTHER", "INTERNATIONAL", "INT", "INT: ALL", "🌍 INTERNATIONAL / ANDERE", "WEITERE LÄNDER") -> {
                candidates.filter {
                    !ContentClassifier.isAdultGroup(it.groupTitle) &&
                    it.countryCode.uppercase() != "ADULT" &&
                    (it.normalizedCountryCode == "OTHER" || it.countryCode in NON_DE_COUNTRIES || (!it.isDach && it.normalizedCountryCode !in listOf("DE", "AT", "CH", "ADULT", "UNKNOWN", "CONFLICT")))
                }
            }
            selectedCategory.length in 2..6 && selectedCategory.all { it.isLetter() || it == '-' } -> {
                val target = selectedCategory.uppercase().replace("-", "")
                candidates.filter {
                    !ContentClassifier.isAdultGroup(it.groupTitle) &&
                    (it.countryCode.uppercase() == target ||
                     it.normalizedCountryCode.uppercase() == target ||
                     ContentClassifier.detectCountryForGroup(it.groupTitle) == target ||
                     (target == "UK" && (it.countryCode.uppercase() == "GB" || it.normalizedCountryCode.uppercase() == "GB")) ||
                     (target == "SE" && it.countryCode.uppercase() in listOf("SE", "NO", "DK", "FI")) ||
                     (target == "PT" && it.countryCode.uppercase() in listOf("PT", "BR")) ||
                     (target == "EXYU" && (it.countryCode.uppercase() in listOf("EXYU", "YU", "SRB", "HRV", "BIH", "MK", "SLO", "MNE") || it.normalizedCountryCode.uppercase() in listOf("EXYU", "YU"))))
                }
            }
            selectedCategory.startsWith("INT:") -> {
                val targetCountry = selectedCategory.removePrefix("INT:").trim().uppercase().replace("-", "")
                candidates.filter {
                    !ContentClassifier.isAdultGroup(it.groupTitle) &&
                    (it.countryCode.uppercase() == targetCountry ||
                     it.normalizedCountryCode.uppercase() == targetCountry ||
                     (targetCountry == "UK" && (it.countryCode.uppercase() == "GB" || it.normalizedCountryCode.uppercase() == "GB")) ||
                     (targetCountry == "SE" && it.countryCode.uppercase() in listOf("SE", "NO", "DK", "FI")) ||
                     (targetCountry == "PT" && it.countryCode.uppercase() in listOf("PT", "BR")))
                }
            }
            selectedCategory in listOf("RADIO", "FILME", "SERIEN") -> {
                candidates.filter { !ContentClassifier.isAdultGroup(it.groupTitle) }
            }
            else -> {
                candidates.filter {
                    it.groupTitle.equals(selectedCategory, ignoreCase = true) ||
                    it.groupTitle.contains(selectedCategory, ignoreCase = true) ||
                    it.subCategory.equals(selectedCategory, ignoreCase = true) ||
                    it.parentCategory.equals(selectedCategory, ignoreCase = true)
                }
            }
        }

        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim()
            val scoredList = list.mapNotNull { item ->
                val key = extractSortKey(item)
                val score = ContentClassifier.calculateSearchRelevance(
                    name = key.item.name,
                    groupTitle = key.item.groupTitle,
                    cleanSortTitle = key.item.cleanSortTitle,
                    isFavorite = key.item.isFavorite,
                    contentType = key.item.type,
                    countryCode = key.effectiveCountry,
                    isDach = key.item.isDach,
                    liveTvSortRank = key.liveTvSortRank,
                    vodLanguageRank = key.item.vodLanguageRank,
                    query = q
                )
                if (score > -50000) Pair(key, score) else null
            }
            return scoredList.sortedWith(
                compareByDescending<Pair<LiveChannelSortKey, Int>> { it.second }
                    .thenBy { it.first.dachCountryRank }
                    .thenBy { it.first.liveTvSortRank }
                    .thenBy(NaturalOrderComparator) { it.first.canonicalChannelId }
                    .thenBy { it.first.qualityRank }
                    .thenBy(NaturalOrderComparator) { it.first.cleanTitle }
            ).map { it.first.item }
        }

        val isFavoritesOnly = selectedCategory == "FAVORITEN" || selectedCategory == "FAVORITES"
        val keys = list.map { extractSortKey(it) }

        val comparator: Comparator<LiveChannelSortKey> = when (sortMode) {
            ChannelSortMode.DACH_CANONICAL -> {
                compareBy<LiveChannelSortKey> { if (isFavoritesOnly) 0 else if (it.item.isFavorite) 0 else 1 }
                    .thenBy { it.dachCountryRank }
                    .thenBy { it.liveTvSortRank }
                    .thenBy(NaturalOrderComparator) { it.canonicalChannelId }
                    .thenBy { it.qualityRank }
                    .thenBy(NaturalOrderComparator) { it.cleanTitle }
            }
            ChannelSortMode.POPULARITY -> {
                compareBy<LiveChannelSortKey> { if (isFavoritesOnly) 0 else if (it.item.isFavorite) 0 else 1 }
                    .thenBy { it.dachCountryRank }
                    .thenBy { it.popularityRank }
                    .thenBy { it.liveTvSortRank }
                    .thenBy(NaturalOrderComparator) { it.canonicalChannelId }
                    .thenBy { it.qualityRank }
                    .thenBy(NaturalOrderComparator) { it.cleanTitle }
            }
            ChannelSortMode.ALPHABETICAL -> {
                compareBy<LiveChannelSortKey> { if (isFavoritesOnly) 0 else if (it.item.isFavorite) 0 else 1 }
                    .thenBy { it.dachCountryRank }
                    .thenBy(NaturalOrderComparator) { it.cleanTitle }
                    .thenBy { it.qualityRank }
            }
            ChannelSortMode.QUALITY -> {
                compareBy<LiveChannelSortKey> { if (isFavoritesOnly) 0 else if (it.item.isFavorite) 0 else 1 }
                    .thenBy { it.dachCountryRank }
                    .thenBy { it.qualityRank }
                    .thenBy { it.liveTvSortRank }
                    .thenBy(NaturalOrderComparator) { it.canonicalChannelId }
                    .thenBy(NaturalOrderComparator) { it.cleanTitle }
            }
            ChannelSortMode.PROVIDER_GROUPS -> {
                compareBy<LiveChannelSortKey> { if (isFavoritesOnly) 0 else if (it.item.isFavorite) 0 else 1 }
                    .thenBy { it.dachCountryRank }
                    .thenBy { it.categoryPriority }
                    .thenBy { if (it.item.providerSortOrder > 0) it.item.providerSortOrder else Int.MAX_VALUE }
                    .thenBy { it.categorySubRank }
                    .thenBy { it.groupTitle }
                    .thenBy { it.liveTvSortRank }
                    .thenBy(NaturalOrderComparator) { it.canonicalChannelId }
                    .thenBy { it.qualityRank }
                    .thenBy(NaturalOrderComparator) { it.cleanTitle }
            }
        }

        return keys.sortedWith(comparator).map { it.item }
    }

    private data class LiveChannelSortKey(
        val item: ChannelItem,
        val effectiveCountry: String,
        val dachCountryRank: Int,
        val canonicalChannelId: String,
        val popularityRank: Int,
        val liveTvSortRank: Int,
        val qualityRank: Int,
        val cleanTitle: String,
        val categoryPriority: Int,
        val categorySubRank: Int,
        val groupTitle: String
    )

    private val sortKeyCache = FastLruCache<String, LiveChannelSortKey>(16384)

    fun clearCache() {
        sortKeyCache.evictAll()
    }

    /**
     * Resolves the accurate country code for DACH sorting & grouping.
     * Preserves 100% foreign category isolation when a channel is inside an explicit foreign category
     * (e.g., TR, EXYU, US, UK, IT, FR, International), while accurately distinguishing DE (1), AT (2),
     * and CH (3) stations when playlists use generic categories ("Vollprogramm", "Sport", "DE: Allgemein").
     */
    private fun resolveEffectiveCountryCode(item: ChannelItem, dachMatch: DachMatchResult): String {
        if (item.manualCountryOverride && !item.manualCountry.isNullOrBlank()) {
            return item.manualCountry.uppercase().trim()
        }

        val rawGroup = (item.providerCategory ?: item.groupTitle).trim()
        val groupUpper = rawGroup.uppercase()
        if (ContentClassifier.isAdultGroup(rawGroup) && !ContentClassifier.isAtOrAustriaContent("", rawGroup)) {
            return "ADULT"
        }

        val groupCountry = ContentClassifier.detectCountryForGroup(groupUpper)
        val isExplicitForeignGroup = (groupCountry !in setOf("OTHER", "SPORT", "VIP", "UNKNOWN", "DE", "AT", "CH")) ||
            groupUpper.contains("INTERNATIONAL")
        if (isExplicitForeignGroup) {
            return item.normalizedCountryCode
        }

        if (groupCountry == "AT" || item.normalizedCountryCode == "AT") return "AT"
        if (groupCountry == "CH" || item.normalizedCountryCode == "CH") return "CH"

        if (dachMatch.isMatch && dachMatch.channel != null) {
            val masterCountry = dachMatch.channel.countryCode.uppercase()
            if (masterCountry in setOf("DE", "AT", "CH")) {
                return masterCountry
            }
        }

        if (ContentClassifier.isAtOrAustriaContent(item.name, rawGroup)) {
            return "AT"
        }

        val resolved = CountryResolver.resolve(
            title = item.name,
            groupTitle = rawGroup,
            tvgCountry = item.tvgCountry,
            tvgId = item.tvgId ?: item.epgId,
            tvgName = item.tvgName,
            ignoreChannelName = false
        )
        if (resolved.countryCode in setOf("DE", "AT", "CH") && resolved.confidence >= 80) {
            return resolved.countryCode
        }

        return item.normalizedCountryCode
    }

    private fun extractSortKey(item: ChannelItem): LiveChannelSortKey {
        val cacheKey = "${item.id}:${item.name}:${item.groupTitle}:${item.isFavorite}:${item.type}:${item.providerSortOrder}:${item.manualCountryOverride}:${item.manualCountry}"
        val cached = sortKeyCache.get(cacheKey)
        if (cached != null) return cached

        val dachMatch = DachMasterDatabase.findMatch(
            title = item.name,
            tvgName = item.tvgName,
            tvgId = item.tvgId ?: item.epgId
        )
        val canonicalId = if (dachMatch.isMatch && dachMatch.channel != null) {
            dachMatch.channel.channelId
        } else if (item.normalizedChannelId.isNotBlank()) {
            item.normalizedChannelId
        } else {
            item.cleanSortTitle
        }

        val popRank = if (dachMatch.isMatch && dachMatch.channel != null) {
            dachMatch.channel.popularityRank
        } else {
            999
        }

        val effectiveCountry = resolveEffectiveCountryCode(item, dachMatch)
        val dachCountryRank = when (effectiveCountry) {
            "DE" -> 1
            "AT" -> 2
            "CH" -> 3
            "ADULT" -> 4
            else -> 5 + ContentClassifier.getCountrySortRank(effectiveCountry)
        }

        val enrichedItem = if (effectiveCountry in setOf("DE", "AT", "CH") && (item.countryCode != effectiveCountry || !item.isDach)) {
            item.copy(countryCode = effectiveCountry, isDach = true)
        } else {
            item
        }

        val qRank = StreamQualityRanker.getQualityRank(item.name, item.resolutionBadge)

        val key = LiveChannelSortKey(
            item = enrichedItem,
            effectiveCountry = effectiveCountry,
            dachCountryRank = dachCountryRank,
            canonicalChannelId = canonicalId,
            popularityRank = popRank,
            liveTvSortRank = item.liveTvSortRank,
            qualityRank = qRank,
            cleanTitle = item.cleanSortTitle,
            categoryPriority = ContentClassifier.getCategoryPriority(item.groupTitle),
            categorySubRank = ContentClassifier.getCategorySubRank(item.groupTitle),
            groupTitle = item.groupTitle
        )
        sortKeyCache.put(cacheKey, key)
        return key
    }

    fun filterAndSortMovies(
        allChannels: List<ChannelItem>,
        searchQuery: String,
        selectedCategory: String,
        hiddenChannelIds: Set<String> = emptySet(),
        disabledCountries: Set<String> = emptySet()
    ): List<ChannelItem> {
        var list = allChannels.filter {
            it.type == ContentType.MOVIE &&
            (hiddenChannelIds.isEmpty() || it.id !in hiddenChannelIds) &&
            (disabledCountries.isEmpty() || it.normalizedCountryCode !in disabledCountries)
        }
        if (selectedCategory != "ALLE" && selectedCategory != "FILME") {
            if (selectedCategory == "FAVORITEN" || selectedCategory == "FAVORITES") {
                list = list.filter { it.isFavorite }
            } else if (selectedCategory.equals("ADULT", ignoreCase = true) || selectedCategory.contains("ADULT", ignoreCase = true) || selectedCategory == "18+ / Adult" || selectedCategory.contains("XXX", ignoreCase = true)) {
                list = list.filter {
                    !ContentClassifier.isAtOrAustriaContent(it.name, it.groupTitle) &&
                    it.countryCode.uppercase() != "AT" && it.normalizedCountryCode != "AT" &&
                    (ContentClassifier.isAdultContent(it.name, it.groupTitle) ||
                     it.countryCode.uppercase() == "ADULT" ||
                     it.vodLanguageRank == 4 ||
                     ContentClassifier.isAdultGroup(it.groupTitle))
                }
            } else if (list.any {
                it.groupTitle.equals(selectedCategory, ignoreCase = true) ||
                (it.providerCategory != null && it.providerCategory.equals(selectedCategory, ignoreCase = true)) ||
                (it.subCategory.isNotBlank() && it.subCategory.equals(selectedCategory, ignoreCase = true))
            }) {
                list = list.filter {
                    it.groupTitle.equals(selectedCategory, ignoreCase = true) ||
                    (it.providerCategory != null && it.providerCategory.equals(selectedCategory, ignoreCase = true)) ||
                    (it.subCategory.isNotBlank() && it.subCategory.equals(selectedCategory, ignoreCase = true))
                }
            } else if (list.any {
                it.parentCategory.equals(selectedCategory, ignoreCase = true) ||
                com.example.data.category.ProviderCategoryEngine.parseCategory(it.groupTitle).parentCategory.equals(selectedCategory, ignoreCase = true)
            }) {
                list = list.filter {
                    it.parentCategory.equals(selectedCategory, ignoreCase = true) ||
                    com.example.data.category.ProviderCategoryEngine.parseCategory(it.groupTitle).parentCategory.equals(selectedCategory, ignoreCase = true)
                }
            }
        }
        return if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim()
            val scored = list.mapNotNull { item ->
                val cleanTitle = item.cleanSortTitle
                val vodRank = item.vodLanguageRank
                val score = ContentClassifier.calculateSearchRelevance(
                    name = item.name,
                    groupTitle = item.groupTitle,
                    cleanSortTitle = cleanTitle,
                    isFavorite = item.isFavorite,
                    contentType = item.type,
                    countryCode = item.countryCode,
                    isDach = item.isDach,
                    liveTvSortRank = item.liveTvSortRank,
                    vodLanguageRank = vodRank,
                    query = q
                )
                if (score > -50000 || item.plot?.contains(q, ignoreCase = true) == true) {
                    VodSortKey(
                        item = item,
                        score = score,
                        isFavorite = item.isFavorite,
                        vodLanguageRank = vodRank,
                        categoryPriority = ContentClassifier.getCategoryPriority(item.groupTitle),
                        providerSortOrder = if (item.providerSortOrder > 0) item.providerSortOrder else Int.MAX_VALUE,
                        categorySubRank = ContentClassifier.getCategorySubRank(item.groupTitle),
                        groupTitle = item.groupTitle,
                        cleanSortTitle = cleanTitle
                    )
                } else null
            }
            scored.sortedWith(
                compareByDescending<VodSortKey> { it.score }
                    .thenBy { it.vodLanguageRank }
                    .thenBy { it.categoryPriority }
                    .thenBy { it.providerSortOrder }
                    .thenBy { it.categorySubRank }
                    .thenBy { it.groupTitle }
                    .thenBy(NaturalOrderComparator) { it.cleanSortTitle }
            ).map { it.item }
        } else {
            list.map { item ->
                VodSortKey(
                    item = item,
                    score = 0,
                    isFavorite = item.isFavorite,
                    vodLanguageRank = item.vodLanguageRank,
                    categoryPriority = ContentClassifier.getCategoryPriority(item.groupTitle),
                    providerSortOrder = if (item.providerSortOrder > 0) item.providerSortOrder else Int.MAX_VALUE,
                    categorySubRank = ContentClassifier.getCategorySubRank(item.groupTitle),
                    groupTitle = item.groupTitle,
                    cleanSortTitle = item.cleanSortTitle
                )
            }.sortedWith(
                compareByDescending<VodSortKey> { it.isFavorite }
                    .thenBy { it.vodLanguageRank }
                    .thenBy { it.categoryPriority }
                    .thenBy { it.providerSortOrder }
                    .thenBy { it.categorySubRank }
                    .thenBy { it.groupTitle }
                    .thenBy(NaturalOrderComparator) { it.cleanSortTitle }
            ).map { it.item }
        }
    }

    fun filterAndSortSeries(
        allChannels: List<ChannelItem>,
        searchQuery: String,
        selectedCategory: String,
        hiddenChannelIds: Set<String> = emptySet(),
        disabledCountries: Set<String> = emptySet()
    ): List<ChannelItem> {
        var list = allChannels.filter {
            it.type == ContentType.SERIES &&
            (hiddenChannelIds.isEmpty() || it.id !in hiddenChannelIds) &&
            (disabledCountries.isEmpty() || it.normalizedCountryCode !in disabledCountries)
        }
        if (selectedCategory != "ALLE" && selectedCategory != "SERIEN") {
            if (selectedCategory == "FAVORITEN" || selectedCategory == "FAVORITES") {
                list = list.filter { it.isFavorite }
            } else if (list.any {
                it.groupTitle.equals(selectedCategory, ignoreCase = true) ||
                (it.providerCategory != null && it.providerCategory.equals(selectedCategory, ignoreCase = true)) ||
                (it.subCategory.isNotBlank() && it.subCategory.equals(selectedCategory, ignoreCase = true))
            }) {
                list = list.filter {
                    it.groupTitle.equals(selectedCategory, ignoreCase = true) ||
                    (it.providerCategory != null && it.providerCategory.equals(selectedCategory, ignoreCase = true)) ||
                    (it.subCategory.isNotBlank() && it.subCategory.equals(selectedCategory, ignoreCase = true))
                }
            } else if (list.any {
                it.parentCategory.equals(selectedCategory, ignoreCase = true) ||
                com.example.data.category.ProviderCategoryEngine.parseCategory(it.groupTitle).parentCategory.equals(selectedCategory, ignoreCase = true)
            }) {
                list = list.filter {
                    it.parentCategory.equals(selectedCategory, ignoreCase = true) ||
                    com.example.data.category.ProviderCategoryEngine.parseCategory(it.groupTitle).parentCategory.equals(selectedCategory, ignoreCase = true)
                }
            }
        }
        return if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim()
            val scored = list.mapNotNull { item ->
                val cleanTitle = item.cleanSortTitle
                val vodRank = item.vodLanguageRank
                val score = ContentClassifier.calculateSearchRelevance(
                    name = item.name,
                    groupTitle = item.groupTitle,
                    cleanSortTitle = cleanTitle,
                    isFavorite = item.isFavorite,
                    contentType = item.type,
                    countryCode = item.countryCode,
                    isDach = item.isDach,
                    liveTvSortRank = item.liveTvSortRank,
                    vodLanguageRank = vodRank,
                    query = q
                )
                if (score > -50000 || item.seriesTitle?.contains(q, ignoreCase = true) == true) {
                    VodSortKey(
                        item = item,
                        score = score,
                        isFavorite = item.isFavorite,
                        vodLanguageRank = vodRank,
                        categoryPriority = ContentClassifier.getCategoryPriority(item.groupTitle),
                        providerSortOrder = if (item.providerSortOrder > 0) item.providerSortOrder else Int.MAX_VALUE,
                        categorySubRank = ContentClassifier.getCategorySubRank(item.groupTitle),
                        groupTitle = item.groupTitle,
                        cleanSortTitle = cleanTitle
                    )
                } else null
            }
            scored.sortedWith(
                compareByDescending<VodSortKey> { it.score }
                    .thenBy { it.vodLanguageRank }
                    .thenBy { it.categoryPriority }
                    .thenBy { it.providerSortOrder }
                    .thenBy { it.categorySubRank }
                    .thenBy { it.groupTitle }
                    .thenBy(NaturalOrderComparator) { it.cleanSortTitle }
            ).map { it.item }
        } else {
            list.map { item ->
                VodSortKey(
                    item = item,
                    score = 0,
                    isFavorite = item.isFavorite,
                    vodLanguageRank = item.vodLanguageRank,
                    categoryPriority = ContentClassifier.getCategoryPriority(item.groupTitle),
                    providerSortOrder = if (item.providerSortOrder > 0) item.providerSortOrder else Int.MAX_VALUE,
                    categorySubRank = ContentClassifier.getCategorySubRank(item.groupTitle),
                    groupTitle = item.groupTitle,
                    cleanSortTitle = item.cleanSortTitle
                )
            }.sortedWith(
                compareByDescending<VodSortKey> { it.isFavorite }
                    .thenBy { it.vodLanguageRank }
                    .thenBy { it.categoryPriority }
                    .thenBy { it.providerSortOrder }
                    .thenBy { it.categorySubRank }
                    .thenBy { it.groupTitle }
                    .thenBy(NaturalOrderComparator) { it.cleanSortTitle }
            ).map { it.item }
        }
    }

    private data class VodSortKey(
        val item: ChannelItem,
        val score: Int,
        val isFavorite: Boolean,
        val vodLanguageRank: Int,
        val categoryPriority: Int,
        val providerSortOrder: Int,
        val categorySubRank: Int,
        val groupTitle: String,
        val cleanSortTitle: String
    )

    fun filterAndSortRadio(
        allChannels: List<ChannelItem>,
        searchQuery: String,
        selectedCategory: String,
        hiddenChannelIds: Set<String> = emptySet(),
        disabledCountries: Set<String> = emptySet()
    ): List<ChannelItem> {
        var list = allChannels.filter {
            it.type == ContentType.RADIO &&
            (hiddenChannelIds.isEmpty() || it.id !in hiddenChannelIds) &&
            (disabledCountries.isEmpty() || it.normalizedCountryCode !in disabledCountries)
        }
        if (selectedCategory != "ALLE" && selectedCategory != "RADIO") {
            if (selectedCategory == "FAVORITEN" || selectedCategory == "FAVORITES") {
                list = list.filter { it.isFavorite }
            } else if (list.any { it.groupTitle.equals(selectedCategory, ignoreCase = true) }) {
                list = list.filter { it.groupTitle.equals(selectedCategory, ignoreCase = true) }
            }
        }
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim()
            list = list.filter {
                it.name.contains(q, ignoreCase = true) ||
                it.groupTitle.contains(q, ignoreCase = true)
            }
        }
        return list.map { item ->
            VodSortKey(
                item = item,
                score = 0,
                isFavorite = item.isFavorite,
                vodLanguageRank = item.dachSortRank,
                categoryPriority = ContentClassifier.getCategoryPriority(item.groupTitle),
                providerSortOrder = if (item.providerSortOrder > 0) item.providerSortOrder else Int.MAX_VALUE,
                categorySubRank = ContentClassifier.getCategorySubRank(item.groupTitle),
                groupTitle = item.groupTitle,
                cleanSortTitle = item.cleanSortTitle
            )
        }.sortedWith(
            compareByDescending<VodSortKey> { it.isFavorite }
                .thenBy { it.vodLanguageRank }
                .thenBy { it.categoryPriority }
                .thenBy { it.categorySubRank }
                .thenBy { it.groupTitle }
                .thenBy(NaturalOrderComparator) { it.cleanSortTitle }
        ).map { it.item }
    }
}
