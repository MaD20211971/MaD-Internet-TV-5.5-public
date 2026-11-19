package com.example.data.category

import com.example.data.country.CountryResolver
import com.example.data.country.ProviderType
import com.example.data.model.ChannelItem
import com.example.data.parser.ContentClassifier
import com.example.util.FastLruCache

enum class NormalizedGenre {
    SPORT,
    CINEMA,
    NEWS,
    ENTERTAINMENT,
    DOCUMENTARY,
    KIDS,
    MUSIC,
    ADULT,
    GENERAL
}

data class ProviderCategoryGroup(
    val parentCategory: String,
    val countryCode: String,
    val subCategories: List<String>,
    val totalChannels: Int
)

data class ParsedProviderCategory(
    val rawCategory: String,
    val countryCode: String,
    val parentCategory: String,
    val subCategory: String,
    val isPriorityCountry: Boolean,
    val normalizedCountry: String = countryCode,
    val normalizedGenre: NormalizedGenre = NormalizedGenre.GENERAL
)

/**
 * High-performance provider category parser that preserves the exact 1:1 category and subcategory
 * hierarchy from the IPTV provider (Xtream Codes, MAC/Stalker, M3U) while mapping countries
 * with strict priority for DE:, AT:, CH:, and ADULT.
 */
object ProviderCategoryEngine {

    val PRIORITY_COUNTRIES: Set<String> = setOf("DE", "AT", "CH", "ADULT")

    private val categoryCache = FastLruCache<String, ParsedProviderCategory>(4096)

    private val HIERARCHY_DELIMITERS = listOf(
        " | ", "|", " ┃ ", "┃", " ║ ", "║", " : ", ": ", " - ", " – ", " — ", " * ", " » ", " / ", " >> ", " > ", " ✪ ", " ★ ", " • ", " ❖ "
    )

    private val BRACKET_PREFIX_REGEX = Regex("""^(?:\[([A-Za-z0-9+\-_ ]{2,12})\]|\|([A-Za-z0-9+\-_ ]{2,12})\||┃([A-Za-z0-9+\-_ ]{2,12})┃|║([A-Za-z0-9+\-_ ]{2,12})║)\s*(.+)$""")
    private val PAREN_PREFIX_REGEX = Regex("""^\(([A-Za-z0-9+\-_ ]{2,12})\)\s*(.+)$""")

    fun parseCategory(
        rawGroup: String?,
        providerType: ProviderType = ProviderType.M3U,
        fallbackName: String = ""
    ): ParsedProviderCategory {
        val raw = rawGroup?.trim()?.takeIf { it.isNotEmpty() } ?: "Uncategorized"
        val cacheKey = "${providerType.name}_$raw"
        categoryCache.get(cacheKey)?.let { return it }

        val detectedCountry = resolveCountryForCategory(raw, fallbackName, providerType)
        val (parentCat, subCat) = splitCategoryHierarchy(raw, detectedCountry)
        val isPriority = detectedCountry in PRIORITY_COUNTRIES
        val genre = detectGenre(raw, subCat, detectedCountry)

        val parsed = ParsedProviderCategory(
            rawCategory = raw,
            countryCode = detectedCountry,
            parentCategory = parentCat,
            subCategory = subCat,
            isPriorityCountry = isPriority,
            normalizedCountry = detectedCountry,
            normalizedGenre = genre
        )
        categoryCache.put(cacheKey, parsed)
        return parsed
    }

    private fun detectGenre(raw: String, subCat: String, countryCode: String): NormalizedGenre {
        if (countryCode == "ADULT" || ContentClassifier.isAdultGroup(raw)) return NormalizedGenre.ADULT
        val u = "$raw $subCat".uppercase()
        return when {
            u.contains("SPORT") || u.contains("DAZN") || u.contains("BUNDESLIGA") || u.contains("FUSSBALL") -> NormalizedGenre.SPORT
            u.contains("KINO") || u.contains("CINEMA") || u.contains("FILM") || u.contains("MOVIE") -> NormalizedGenre.CINEMA
            u.contains("NEWS") || u.contains("NACHRICHTEN") || u.contains("INFO") -> NormalizedGenre.NEWS
            u.contains("DOKU") || u.contains("DOCU") -> NormalizedGenre.DOCUMENTARY
            u.contains("KIDS") || u.contains("KINDER") -> NormalizedGenre.KIDS
            u.contains("MUSIC") || u.contains("MUSIK") -> NormalizedGenre.MUSIC
            u.contains("ENTERTAIN") || u.contains("UNTERHALTUNG") -> NormalizedGenre.ENTERTAINMENT
            else -> NormalizedGenre.GENERAL
        }
    }

    fun buildCategoryHierarchy(channels: List<ChannelItem>): List<ProviderCategoryGroup> {
        if (channels.isEmpty()) return emptyList()
        val groupedByParent = LinkedHashMap<String, MutableList<ChannelItem>>()
        val parentCountryMap = HashMap<String, String>()

        for (ch in channels) {
            val parsed = parseCategory(ch.groupTitle)
            val p = ch.parentCategory.ifBlank { parsed.parentCategory }
            groupedByParent.getOrPut(p) { mutableListOf() }.add(ch)
            if (!parentCountryMap.containsKey(p)) {
                parentCountryMap[p] = ch.countryCode.ifBlank { parsed.countryCode }
            }
        }

        return groupedByParent.entries.map { (parent, items) ->
            val cCode = parentCountryMap[parent] ?: "OTHER"
            val subs = items
                .sortedBy { if (it.providerSortOrder > 0) it.providerSortOrder else Int.MAX_VALUE }
                .map { it.groupTitle.ifBlank { it.subCategory } }
                .filter { it.isNotBlank() }
                .distinct()
            ProviderCategoryGroup(
                parentCategory = parent,
                countryCode = cCode,
                subCategories = subs,
                totalChannels = items.size
            )
        }.sortedWith(
            compareBy<ProviderCategoryGroup> {
                when (it.countryCode.uppercase()) {
                    "DE" -> 1
                    "AT" -> 2
                    "CH" -> 3
                    "ADULT" -> 4
                    else -> 10 + ContentClassifier.getCountrySortRank(it.countryCode)
                }
            }.thenBy { it.parentCategory }
        )
    }

    private fun resolveCountryForCategory(
        rawCategory: String,
        @Suppress("UNUSED_PARAMETER") fallbackName: String,
        providerType: ProviderType
    ): String {
        val upper = rawCategory.uppercase().trim()

        // 0. Check explicit provider category country first (1:1 provider category mapping)
        val explicitCategoryCountry = com.example.data.country.CountryNormalizer.extractCountryFromCategory(rawCategory)
        if (explicitCategoryCountry != null && explicitCategoryCountry !in setOf("SPORT", "OTHER", "UNKNOWN")) {
            return explicitCategoryCountry
        }

        if (ContentClassifier.isAtOrAustriaContent("", rawCategory)) {
            return "AT"
        }
        if (ContentClassifier.isChOrSwissContent("", rawCategory)) {
            return "CH"
        }
        if (ContentClassifier.isAdultGroup(rawCategory)) {
            return "ADULT"
        }

        val groupCountry = ContentClassifier.detectCountryForGroup(rawCategory)
        if (groupCountry !in setOf("OTHER", "SPORT", "VIP", "INT", "UNKNOWN")) {
            return groupCountry
        }

        if (ContentClassifier.isGermanOrDachVodCategory(rawCategory)) {
            return when {
                upper.contains("AT:") || upper.contains("AUSTRIA") || upper.contains("ÖSTERREICH") -> "AT"
                upper.contains("CH:") || upper.contains("SWISS") || upper.contains("SCHWEIZ") -> "CH"
                else -> "DE"
            }
        }

        val res = CountryResolver.resolve(
            title = "",
            groupTitle = rawCategory,
            providerType = providerType,
            ignoreChannelName = true
        )
        if (res.countryCode.isNotBlank() && res.countryCode !in setOf("UNKNOWN", "CONFLICT", "INT", "OTHER", "SPORT", "VIP")) {
            return res.countryCode
        }

        return "OTHER"
    }

    /**
     * Splits a provider category string (e.g. "DE: Sky Sport", "DE | Kino & Filme", "[AT] ORF",
     * "VOD - DE - Action", "XXX | 18+ Adult") into its provider parent category and subcategory
     * while preserving the provider's original naming.
     */
    fun splitCategoryHierarchy(rawCategory: String, countryCode: String): Pair<String, String> {
        val trimmed = rawCategory.trim()
        if (trimmed.isEmpty() || trimmed.equals("Uncategorized", ignoreCase = true)) {
            val defaultParent = formatCountryParentPrefix(countryCode)
            return defaultParent to "Allgemein"
        }

        // 1. Check bracketed/pipe prefix: "[DE] Unterhaltung", "|DE| Kino", "┃DE┃ Sport"
        BRACKET_PREFIX_REGEX.matchEntire(trimmed)?.let { match ->
            val prefix = (1..4).map { match.groupValues[it] }.firstOrNull { it.isNotBlank() }?.trim() ?: ""
            val rest = match.groupValues[5].trim()
            if (rest.isNotBlank()) {
                val parent = formatProviderParentHeader(prefix, countryCode)
                val cleanSub = extractNestedSubCategory(rest)
                return parent to cleanSub
            }
        }

        PAREN_PREFIX_REGEX.matchEntire(trimmed)?.let { match ->
            val prefix = match.groupValues[1].trim()
            val rest = match.groupValues[2].trim()
            if (rest.isNotBlank()) {
                val parent = formatProviderParentHeader(prefix, countryCode)
                val cleanSub = extractNestedSubCategory(rest)
                return parent to cleanSub
            }
        }

        // 2. Check standard provider hierarchy delimiters
        for (delim in HIERARCHY_DELIMITERS) {
            val idx = trimmed.indexOf(delim)
            if (idx > 0 && idx < trimmed.length - delim.length) {
                val left = trimmed.substring(0, idx).trim()
                val right = trimmed.substring(idx + delim.length).trim()
                if (left.isNotBlank() && right.isNotBlank()) {
                    // Handle 3-level hierarchy like "EU | DE | Sport" or "VOD | DE | Action"
                    for (subDelim in HIERARCHY_DELIMITERS) {
                        val subIdx = right.indexOf(subDelim)
                        if (subIdx > 0 && subIdx < right.length - subDelim.length) {
                            val mid = right.substring(0, subIdx).trim()
                            val tail = right.substring(subIdx + subDelim.length).trim()
                            if (mid.isNotBlank() && tail.isNotBlank()) {
                                val combinedParent = formatProviderParentHeader("$left $mid", countryCode)
                                return combinedParent to tail
                            }
                        }
                    }
                    val parent = formatProviderParentHeader(left, countryCode)
                    return parent to right
                }
            }
        }

        // 3. Space-separated country prefix (e.g. "DE Sky Cinema", "AT ORF", "CH Swiss")
        val upper = trimmed.uppercase()
        val spacePrefixes = listOf(
            "EU DE ", "DE ", "GER ", "GERMANY ", "DEUTSCHLAND ",
            "EU AUSTRIA ", "AT ", "AUT ", "AUSTRIA ", "ÖSTERREICH ",
            "EU SWITZERLAND ", "CH ", "SUI ", "SWISS ", "SCHWEIZ ",
            "XXX ", "ADULT ", "18+ "
        )
        for (sp in spacePrefixes) {
            if (upper.startsWith(sp) && trimmed.length > sp.length) {
                val rest = trimmed.substring(sp.length).trim().removePrefix("-").removePrefix("|").removePrefix(":").trim()
                if (rest.isNotBlank()) {
                    return formatCountryParentPrefix(countryCode) to rest
                }
            }
        }

        return formatCountryParentPrefix(countryCode) to trimmed
    }

    private fun extractNestedSubCategory(text: String): String {
        val cleaned = text.removePrefix("|").removePrefix(":").removePrefix("-").removePrefix("*").trim()
        return cleaned.ifBlank { text }
    }

    fun formatCountryParentPrefix(countryCode: String): String {
        return when (countryCode.uppercase()) {
            "DE" -> "DE: Deutschland"
            "AT" -> "AT: Österreich"
            "CH" -> "CH: Schweiz"
            "ADULT" -> "ADULT: 18+"
            else -> {
                val info = ContentClassifier.getCountryInfo(countryCode)
                if (info.name.startsWith("${countryCode.uppercase()}:")) info.name else "${countryCode.uppercase()}: ${info.name}"
            }
        }
    }

    private fun formatProviderParentHeader(rawPrefix: String, countryCode: String): String {
        return when (countryCode.uppercase()) {
            "DE" -> "DE: Deutschland"
            "AT" -> "AT: Österreich"
            "CH" -> "CH: Schweiz"
            "ADULT" -> "ADULT: 18+"
            else -> {
                val cleanPrefix = rawPrefix.trim().removeSuffix(":").removeSuffix("|").removeSuffix("-").trim()
                if (cleanPrefix.length <= 4) {
                    val info = ContentClassifier.getCountryInfo(countryCode)
                    if (info.name.startsWith("${countryCode.uppercase()}:")) info.name else "${countryCode.uppercase()}: ${info.name}"
                } else {
                    cleanPrefix
                }
            }
        }
    }

    fun clearCache() {
        categoryCache.evictAll()
    }
}
