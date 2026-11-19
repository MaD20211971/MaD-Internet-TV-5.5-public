package com.example.data.country

import android.content.Context
import com.example.util.FastLruCache
import org.json.JSONObject
import java.io.InputStream
import java.util.Locale
import java.util.regex.Pattern

/**
 * Standardized data model for channels in the DACH Master Database.
 * Fulfills all criteria of the DACH Master Specification (DE, AT, CH).
 */
data class DachChannel(
    val channelId: String,
    val canonicalName: String,
    val shortName: String,
    val countryCode: String,
    val countryName: String,
    val region: String,
    val language: String,
    val category: String,
    val genre: String,
    val popularityRank: Int,
    val popularityScore: Double,
    val marketShare: Double,
    val marketShareYear: Int,
    val marketShareSource: String,
    val aliases: List<String>,
    val keywords: List<String>,
    val tvgIds: List<String>,
    val tvgNamePatterns: List<String>,
    val matchPatterns: List<String>,
    val excludePatterns: List<String>,
    val isNational: Boolean,
    val isRegional: Boolean,
    val isPublicService: Boolean,
    val isPrivate: Boolean,
    val logo: String = ""
)

/**
 * Result of channel matching against the DACH Master Database.
 */
data class DachMatchResult(
    val channel: DachChannel?,
    val quality: String?,
    val confidence: Int,
    val matchedRule: String?,
    val isMatch: Boolean = channel != null
) {
    companion object {
        val NO_MATCH = DachMatchResult(null, null, 0, null, false)
    }
}

/**
 * Extracted stream quality tags and cleaned canonical channel title.
 */
data class StreamQualityInfo(
    val cleanTitle: String,
    val resolution: String?,
    val codec: String?,
    val frameRate: String?,
    val isBackup: Boolean
) {
    val qualitySummary: String
        get() = listOfNotNull(resolution, codec, frameRate).joinToString(" ")
}

/**
 * High-performance DACH Channel Master Database for Germany, Austria, and Switzerland.
 *
 * Implements:
 * 1. Sendererkennung (Channel recognition)
 * 2. Alias-Erkennung (Alias detection)
 * 3. Dubletten-Erkennung (Deduplication)
 * 4. CountryResolver Evidence Provider (95% Tier-2 evidence)
 * 5. Länderzuordnung (DE, AT, CH without language bias)
 * 6. Popularitäts-Sortierung (Separate DE, AT, CH rankings)
 * 7. Kategorien (Public, Private, News, Sport, Regional, etc.)
 * 8. Suchfunktion (Multi-faceted search)
 * 9. Sendernormalisierung (Canonical name mapping)
 * 10. Automatische Erkennung & Bereinigung von HD/FHD/UHD/4K/HEVC/FPS
 */
object DachMasterDatabase {

    private val QUALITY_REGEX = Pattern.compile(
        "(?i)\\b(UHD|4K|FHD|1080P|1080I|720P|HD|SD|HEVC|H265|H264|50FPS|60FPS|50\\s*FPS|60\\s*FPS|RAW|HDR|VIP|BACKUP)\\b"
    )

    private val PREFIX_CLEAN_REGEX = Pattern.compile(
        """^(?:[A-Za-z0-9\+]{2,6}\s*[:\|\-\–\—\*•✪★☆~>\/\\]\s*|[\[\(【][A-Za-z0-9\+]{2,6}[\]\)】]\s*)"""
    )

    private val EMPTY_BRACKETS_REGEX = Pattern.compile(
        """[\(\[\{]\s*[\)\]\}]"""
    )

    private val SPACES_REGEX = Pattern.compile(
        "\\s+"
    )

    // In-memory master channel registry
    private val channelsById = LinkedHashMap<String, DachChannel>()
    private val channelsByCountry = LinkedHashMap<String, MutableList<DachChannel>>()
    private val aliasIndex = HashMap<String, DachChannel>()
    private val tvgIdIndex = HashMap<String, DachChannel>()

    // Compiled regex cache for fast matching
    private val compiledPatterns = HashMap<String, Pair<List<Pattern>, List<Pattern>>>()

    // LRU Cache for findMatch lookups (fast O(1) retrieval across sorting and filtering)
    private val matchCache = FastLruCache<String, DachMatchResult>(8192)

    private val NON_DACH_PREFIX_REGEX = Pattern.compile(
        """(?i)^(?:TR|TURK|TURKEY|UK|US|USA|GB|FR|FRA|FRANCE|IT|ITA|ITALY|ES|ESP|SPAIN|EXYU|EX-YU|YU|PL|POL|POLAND|NL|NED|HOLLAND|RU|RUS|RUSSIA|RO|ROU|ROMANIA|AL|ALB|ALBANIA|AR|ARA|ARABIC|PT|POR|PORTUGAL|BR|BRA|BRAZIL|GR|GRE|GREECE|AF|AFG|IN|IND|INDIA|SE|SWE|SWEDEN|NO|NOR|NORWAY|DK|DNK|DENMARK|FI|FIN|FINLAND|BG|BGR|CZ|CZE|HUNGARY|HU|HUN)\s*[:\|\-\–\—\*•✪★☆~>\/\\]\s*"""
    )

    private val ALIAS_NORM_REGEX = Pattern.compile("[^A-Z0-9+]")

    init {
        loadBuiltInChannels()
    }

    @Volatile
    private var isInitializedFromAssets = false

    /**
     * Initializes or refreshes the database from Android asset files if provided.
     * Loads both dach_channels.json and dach_channel_aliases.json.
     */
    fun initFromAssets(context: Context) {
        if (isInitializedFromAssets) return
        try {
            val jsonStream: InputStream = context.assets.open("dach_channels.json")
            val jsonString = jsonStream.bufferedReader().use { it.readText() }
            val root = JSONObject(jsonString)
            val array = root.optJSONArray("channels") ?: return

            val loaded = mutableListOf<DachChannel>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val ch = DachChannel(
                    channelId = obj.getString("channelId"),
                    canonicalName = obj.getString("canonicalName"),
                    shortName = obj.optString("shortName", obj.getString("canonicalName")),
                    countryCode = obj.getString("countryCode"),
                    countryName = obj.getString("countryName"),
                    region = obj.getString("region"),
                    language = obj.getString("language"),
                    category = obj.getString("category"),
                    genre = obj.getString("genre"),
                    popularityRank = obj.optInt("popularityRank", 999),
                    popularityScore = obj.optDouble("popularityScore", 50.0),
                    marketShare = obj.optDouble("marketShare", 0.0),
                    marketShareYear = obj.optInt("marketShareYear", 2025),
                    marketShareSource = obj.optString("marketShareSource", "Marktforschung"),
                    aliases = jsonArrayToList(obj.optJSONArray("aliases")),
                    keywords = jsonArrayToList(obj.optJSONArray("keywords")),
                    tvgIds = jsonArrayToList(obj.optJSONArray("tvgIds")),
                    tvgNamePatterns = jsonArrayToList(obj.optJSONArray("tvgNamePatterns")),
                    matchPatterns = jsonArrayToList(obj.optJSONArray("matchPatterns")),
                    excludePatterns = jsonArrayToList(obj.optJSONArray("excludePatterns")),
                    isNational = obj.optBoolean("isNational", true),
                    isRegional = obj.optBoolean("isRegional", false),
                    isPublicService = obj.optBoolean("isPublicService", false),
                    isPrivate = obj.optBoolean("isPrivate", false),
                    logo = obj.optString("logo", "")
                )
                loaded.add(ch)
            }

            if (loaded.isNotEmpty()) {
                synchronized(this) {
                    channelsById.clear()
                    channelsByCountry.clear()
                    aliasIndex.clear()
                    tvgIdIndex.clear()
                    compiledPatterns.clear()
                    loaded.forEach { registerChannel(it) }

                    // Also ingest extra alias database (dach_channel_aliases.json)
                    try {
                        val aliasStream = context.assets.open("dach_channel_aliases.json")
                        val aliasJsonString = aliasStream.bufferedReader().use { it.readText() }
                        val aliasRoot = JSONObject(aliasJsonString)
                        val aliasArray = aliasRoot.optJSONArray("aliases")
                        if (aliasArray != null) {
                            for (j in 0 until aliasArray.length()) {
                                val aObj = aliasArray.optJSONObject(j) ?: continue
                                val aliasText = aObj.optString("alias", "").trim()
                                val chId = aObj.optString("channelId", "").trim()
                                if (aliasText.isNotEmpty() && chId.isNotEmpty()) {
                                    val targetCh = channelsById[chId]
                                    if (targetCh != null) {
                                        val aKey = normalizeAliasKey(aliasText)
                                        if (aKey.isNotEmpty()) {
                                            aliasIndex[aKey] = targetCh
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}

                    matchCache.evictAll()
                    isInitializedFromAssets = true
                }
            }
        } catch (_: Exception) {
            // Keep built-in channels if asset loading fails
        }
    }

    private fun jsonArrayToList(arr: org.json.JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            list.add(arr.getString(i))
        }
        return list
    }

    private fun registerChannel(ch: DachChannel) {
        channelsById[ch.channelId] = ch
        channelsByCountry.getOrPut(ch.countryCode) { mutableListOf() }.add(ch)

        // Index aliases
        for (alias in ch.aliases) {
            val key = normalizeAliasKey(alias)
            if (key.isNotEmpty() && !aliasIndex.containsKey(key)) {
                aliasIndex[key] = ch
            }
        }
        val canonicalKey = normalizeAliasKey(ch.canonicalName)
        if (canonicalKey.isNotEmpty() && !aliasIndex.containsKey(canonicalKey)) {
            aliasIndex[canonicalKey] = ch
        }

        // Index tvgIds
        for (tvgId in ch.tvgIds) {
            val lower = tvgId.trim().lowercase(Locale.ROOT)
            if (lower.isNotEmpty() && !tvgIdIndex.containsKey(lower)) {
                tvgIdIndex[lower] = ch
            }
        }

        // Compile regex patterns
        val matchP = ch.matchPatterns.mapNotNull {
            try { Pattern.compile(it) } catch (_: Exception) { null }
        }
        val excludeP = ch.excludePatterns.mapNotNull {
            try { Pattern.compile(it) } catch (_: Exception) { null }
        }
        compiledPatterns[ch.channelId] = Pair(matchP, excludeP)
    }

    /**
     * Extracts quality parameters (FHD, UHD, 4K, 50fps, HEVC) and produces clean title.
     */
    fun extractQuality(rawTitle: String?): StreamQualityInfo {
        if (rawTitle.isNullOrBlank()) {
            return StreamQualityInfo("", null, null, null, false)
        }

        var text = rawTitle.trim()
        val upper = text.uppercase(Locale.ROOT)

        var resolution: String? = null
        var codec: String? = null
        var frameRate: String? = null
        val isBackup = upper.contains("BACKUP") || upper.contains("BKUP")

        when {
            upper.contains("4K") || upper.contains("2160P") -> resolution = "4K"
            upper.contains("UHD") -> resolution = "UHD"
            upper.contains("FHD") || upper.contains("1080P") -> resolution = "FHD"
            upper.contains("1080I") -> resolution = "1080i"
            upper.contains("720P") || upper.contains("HD") -> resolution = "HD"
            upper.contains("SD") -> resolution = "SD"
        }

        when {
            upper.contains("HEVC") || upper.contains("H.265") || upper.contains("H265") -> codec = "HEVC"
            upper.contains("H.264") || upper.contains("H264") -> codec = "H.264"
        }

        when {
            upper.contains("50FPS") || upper.contains("50 FPS") -> frameRate = "50fps"
            upper.contains("60FPS") || upper.contains("60 FPS") -> frameRate = "60fps"
        }

        // Remove prefix country codes like "DE: ", "DE|", "AT - "
        text = PREFIX_CLEAN_REGEX.matcher(text).replaceFirst("").trim()
        // Strip quality tags from clean title
        var cleaned = QUALITY_REGEX.matcher(text).replaceAll(" ")
        cleaned = EMPTY_BRACKETS_REGEX.matcher(cleaned).replaceAll(" ")
        cleaned = SPACES_REGEX.matcher(cleaned).replaceAll(" ").trim()

        return StreamQualityInfo(
            cleanTitle = cleaned,
            resolution = resolution,
            codec = codec,
            frameRate = frameRate,
            isBackup = isBackup
        )
    }

    /**
     * Finds the canonical DACH channel match for a stream, checking aliases, TVG-ID, and regex patterns.
     * Prevents false collisions between similar channels (e.g., PULS 4 vs PULS 24 vs Puls 8, ATV vs ATV II, 3sat vs 3+).
     */
    fun findMatch(title: String?, tvgName: String? = null, tvgId: String? = null): DachMatchResult {
        val rawInput = (title?.ifBlank { null } ?: tvgName ?: "").trim()
        if (rawInput.isBlank()) return DachMatchResult.NO_MATCH

        val cacheKey = "$rawInput|${tvgName ?: ""}|${tvgId ?: ""}"
        val cached = matchCache.get(cacheKey)
        if (cached != null) return cached

        val qualityInfo = extractQuality(rawInput)
        val cleanCandidate = qualityInfo.cleanTitle

        // Step 1: EPG / tvg-id lookup (Strict match)
        if (!tvgId.isNullOrBlank()) {
            val lowerTvg = tvgId.trim().lowercase(Locale.ROOT)
            val directTvg = tvgIdIndex[lowerTvg]
            if (directTvg != null) {
                val result = DachMatchResult(
                    channel = directTvg,
                    quality = qualityInfo.qualitySummary.ifBlank { null },
                    confidence = 98,
                    matchedRule = "TVG_ID_MATCH (${directTvg.channelId})"
                )
                matchCache.put(cacheKey, result)
                return result
            }
        }

        // Step 2: Normalized Alias Map Lookup
        val cleanKey = normalizeAliasKey(cleanCandidate)
        if (cleanKey.isNotEmpty()) {
            val aliasMatch = aliasIndex[cleanKey]
            if (aliasMatch != null) {
                val result = DachMatchResult(
                    channel = aliasMatch,
                    quality = qualityInfo.qualitySummary.ifBlank { null },
                    confidence = 95,
                    matchedRule = "ALIAS_EXACT_MATCH (${aliasMatch.canonicalName})"
                )
                matchCache.put(cacheKey, result)
                return result
            }
        }

        // Fast-path bailout: If the channel explicitly has an international non-DACH prefix
        // (e.g. "TR: Show TV", "UK: BBC One", "FR: TF1") and didn't match via TVG-ID or alias,
        // do not run the 100+ DACH channel regex patterns loop!
        if (NON_DACH_PREFIX_REGEX.matcher(rawInput).find()) {
            matchCache.put(cacheKey, DachMatchResult.NO_MATCH)
            return DachMatchResult.NO_MATCH
        }

        // Step 3: Pattern matching with disambiguation guards
        val testText = "$rawInput ${tvgName ?: ""}".trim()

        for (channel in channelsById.values) {
            val patterns = compiledPatterns[channel.channelId] ?: continue
            val matchPatterns = patterns.first
            val excludePatterns = patterns.second

            // Check exclusion patterns first
            var isExcluded = false
            for (ep in excludePatterns) {
                if (ep.matcher(testText).find()) {
                    isExcluded = true
                    break
                }
            }
            if (isExcluded) continue

            // Check match patterns
            for (mp in matchPatterns) {
                if (mp.matcher(testText).find()) {
                    val result = DachMatchResult(
                        channel = channel,
                        quality = qualityInfo.qualitySummary.ifBlank { null },
                        confidence = 92,
                        matchedRule = "PATTERN_MATCH (${channel.channelId})"
                    )
                    matchCache.put(cacheKey, result)
                    return result
                }
            }
        }

        matchCache.put(cacheKey, DachMatchResult.NO_MATCH)
        return DachMatchResult.NO_MATCH
    }

    fun clearCache() {
        matchCache.evictAll()
    }

    /**
     * Checks if two channel titles or entries represent duplicates of the exact same TV channel.
     */
    fun areDuplicates(title1: String?, title2: String?): Boolean {
        if (title1.isNullOrBlank() || title2.isNullOrBlank()) return false
        val match1 = findMatch(title1)
        val match2 = findMatch(title2)

        return if (match1.isMatch && match2.isMatch) {
            match1.channel!!.channelId == match2.channel!!.channelId
        } else {
            val q1 = extractQuality(title1).cleanTitle.uppercase(Locale.ROOT)
            val q2 = extractQuality(title2).cleanTitle.uppercase(Locale.ROOT)
            q1.isNotEmpty() && q1 == q2
        }
    }

    /**
     * Returns channels sorted by popularity rank for a specific country (DE, AT, or CH).
     */
    fun getChannelsByPopularity(countryCode: String): List<DachChannel> {
        val upper = countryCode.trim().uppercase(Locale.ROOT)
        return channelsByCountry[upper]?.sortedBy { it.popularityRank } ?: emptyList()
    }

    /**
     * Searches channels across DACH master database by query, country, category, and region.
     */
    fun search(
        query: String = "",
        countryCode: String? = null,
        category: String? = null,
        region: String? = null
    ): List<DachChannel> {
        val q = query.trim().lowercase(Locale.ROOT)
        val targetCountry = countryCode?.trim()?.uppercase(Locale.ROOT)

        var list: Sequence<DachChannel> = channelsById.values.asSequence()

        if (!targetCountry.isNullOrBlank()) {
            list = list.filter { it.countryCode == targetCountry }
        }
        if (!category.isNullOrBlank()) {
            list = list.filter { it.category.equals(category, ignoreCase = true) }
        }
        if (!region.isNullOrBlank()) {
            list = list.filter { it.region.equals(region, ignoreCase = true) }
        }
        if (q.isNotEmpty()) {
            list = list.filter { ch ->
                ch.canonicalName.lowercase(Locale.ROOT).contains(q) ||
                ch.shortName.lowercase(Locale.ROOT).contains(q) ||
                ch.aliases.any { it.lowercase(Locale.ROOT).contains(q) } ||
                ch.keywords.any { it.lowercase(Locale.ROOT).contains(q) }
            }
        }

        return list.sortedWith(compareBy({ it.countryCode }, { it.popularityRank })).toList()
    }

    fun getAllChannels(): List<DachChannel> = channelsById.values.toList()

    /**
     * Total channel count.
     */
    val totalCount: Int
        get() = channelsById.size

    val deCount: Int
        get() = channelsByCountry[CountryConstants.CODE_DE]?.size ?: 0

    val atCount: Int
        get() = channelsByCountry[CountryConstants.CODE_AT]?.size ?: 0

    val chCount: Int
        get() = channelsByCountry[CountryConstants.CODE_CH]?.size ?: 0

    private fun normalizeAliasKey(alias: String): String {
        return ALIAS_NORM_REGEX.matcher(alias.uppercase(Locale.ROOT))
            .replaceAll("")
    }

    /**
     * Built-in fallback seed to ensure instant operation without blocking asset reads.
     */
    private fun loadBuiltInChannels() {
        val seed = listOf(
            // DEUTSCHLAND
            DachChannel(
                channelId = "DE_ARD_DAS_ERSTE", canonicalName = "Das Erste", shortName = "ARD",
                countryCode = "DE", countryName = "Deutschland", region = "DE-NAT", language = "de",
                category = "Public", genre = "Vollprogramm", popularityRank = 2, popularityScore = 98.0,
                marketShare = 11.9, marketShareYear = 2024, marketShareSource = "AGF Videoforschung 2024",
                aliases = listOf("ARD", "Das Erste HD", "Das Erste FHD", "Das Erste UHD", "ARD HD", "DE: Das Erste"),
                keywords = listOf("ard", "das erste"), tvgIds = listOf("DasErste.de", "ard.de"),
                tvgNamePatterns = listOf("das erste*"), matchPatterns = listOf("(?i)\\b(das\\s*erste|ard)\\b"),
                excludePatterns = listOf("(?i)\\b(ard\\s*alpha|radio)\\b"), isNational = true, isRegional = false,
                isPublicService = true, isPrivate = false
            ),
            DachChannel(
                channelId = "DE_ZDF", canonicalName = "ZDF", shortName = "ZDF",
                countryCode = "DE", countryName = "Deutschland", region = "DE-NAT", language = "de",
                category = "Public", genre = "Vollprogramm", popularityRank = 1, popularityScore = 100.0,
                marketShare = 14.6, marketShareYear = 2024, marketShareSource = "AGF Videoforschung 2024",
                aliases = listOf("ZDF HD", "ZDF FHD", "ZDF UHD", "DE: ZDF"),
                keywords = listOf("zdf"), tvgIds = listOf("ZDF.de"),
                tvgNamePatterns = listOf("zdf*"), matchPatterns = listOf("(?i)\\bzdf\\b"),
                excludePatterns = listOf("(?i)\\b(zdfneo|zdfinfo|neo|info)\\b"), isNational = true, isRegional = false,
                isPublicService = true, isPrivate = false
            ),
            DachChannel(
                channelId = "DE_3SAT", canonicalName = "3sat", shortName = "3sat",
                countryCode = "DE", countryName = "Deutschland", region = "DE-NAT", language = "de",
                category = "Public", genre = "Kultur", popularityRank = 20, popularityScore = 72.0,
                marketShare = 1.4, marketShareYear = 2024, marketShareSource = "AGF Videoforschung 2024",
                aliases = listOf("3sat HD", "3sat FHD", "DE: 3sat"),
                keywords = listOf("3sat", "dreisat"), tvgIds = listOf("3sat.de"),
                tvgNamePatterns = listOf("3sat*"), matchPatterns = listOf("(?i)\\b3sat\\b"),
                excludePatterns = listOf("(?i)\\b3\\+\\b"), isNational = true, isRegional = false,
                isPublicService = true, isPrivate = false
            ),
            DachChannel(
                channelId = "DE_RTL", canonicalName = "RTL", shortName = "RTL",
                countryCode = "DE", countryName = "Deutschland", region = "DE-NAT", language = "de",
                category = "Private", genre = "Vollprogramm", popularityRank = 3, popularityScore = 95.0,
                marketShare = 8.0, marketShareYear = 2024, marketShareSource = "AGF Videoforschung 2024",
                aliases = listOf("RTL Television", "RTL HD", "DE: RTL HD"),
                keywords = listOf("rtl"), tvgIds = listOf("RTL.de"),
                tvgNamePatterns = listOf("rtl*"), matchPatterns = listOf("(?i)\\brtl(\\s*television)?\\b"),
                excludePatterns = listOf("(?i)\\b(austria|schweiz|ch|at|rtl\\s*2|rtl\\s*zwei|nitro|up)\\b"),
                isNational = true, isRegional = false, isPublicService = false, isPrivate = true
            ),
            // ÖSTERREICH
            DachChannel(
                channelId = "AT_ORF_1", canonicalName = "ORF 1", shortName = "ORF 1",
                countryCode = "AT", countryName = "Österreich", region = "AT-NAT", language = "de",
                category = "ORF", genre = "Vollprogramm", popularityRank = 2, popularityScore = 97.0,
                marketShare = 10.8, marketShareYear = 2025, marketShareSource = "Teletest / ORF Jahresbericht 2025",
                aliases = listOf("ORF1", "ORF 1 HD", "ORF1 HD", "ORF 1 FHD", "AT: ORF 1 HD"),
                keywords = listOf("orf1", "orf 1", "orf eins"), tvgIds = listOf("ORF1.at"),
                tvgNamePatterns = listOf("orf 1*", "orf1*"), matchPatterns = listOf("(?i)\\borf\\s*(1|eins)\\b"),
                excludePatterns = listOf("(?i)\\b(orf\\s*2|orf\\s*3|orf\\s*iii|sport)\\b"), isNational = true, isRegional = false,
                isPublicService = true, isPrivate = false
            ),
            DachChannel(
                channelId = "AT_ORF_2", canonicalName = "ORF 2", shortName = "ORF 2",
                countryCode = "AT", countryName = "Österreich", region = "AT-NAT", language = "de",
                category = "ORF", genre = "Vollprogramm", popularityRank = 1, popularityScore = 100.0,
                marketShare = 20.8, marketShareYear = 2025, marketShareSource = "Teletest / ORF Jahresbericht 2025",
                aliases = listOf("ORF2", "ORF 2 HD", "ORF2 HD", "AT: ORF 2 HD"),
                keywords = listOf("orf2", "orf 2"), tvgIds = listOf("ORF2.at"),
                tvgNamePatterns = listOf("orf 2*", "orf2*"), matchPatterns = listOf("(?i)\\borf\\s*2\\b"),
                excludePatterns = listOf("(?i)\\b(burgenland|kaernten|niederoesterreich|oberoesterreich|salzburg|steiermark|tirol|vorarlberg|wien)\\b"),
                isNational = true, isRegional = false, isPublicService = true, isPrivate = false
            ),
            DachChannel(
                channelId = "AT_ORF_2_WIEN", canonicalName = "ORF 2 Wien", shortName = "ORF 2 W",
                countryCode = "AT", countryName = "Österreich", region = "AT-WI", language = "de",
                category = "ORF", genre = "Regional", popularityRank = 11, popularityScore = 65.0,
                marketShare = 0.6, marketShareYear = 2025, marketShareSource = "Teletest / ORF Jahresbericht 2025",
                aliases = listOf("ORF 2 W HD", "ORF 2 Wien HD", "AT: ORF 2 Wien"),
                keywords = listOf("orf 2 wien"), tvgIds = listOf("ORF2W.at"),
                tvgNamePatterns = listOf("orf 2 wien*"), matchPatterns = listOf("(?i)\\borf\\s*2\\s*wien\\b"),
                excludePatterns = emptyList(), isNational = false, isRegional = true,
                isPublicService = true, isPrivate = false
            ),
            DachChannel(
                channelId = "AT_PULS_4", canonicalName = "PULS 4", shortName = "PULS 4",
                countryCode = "AT", countryName = "Österreich", region = "AT-NAT", language = "de",
                category = "Private", genre = "Vollprogramm", popularityRank = 4, popularityScore = 86.0,
                marketShare = 2.7, marketShareYear = 2025, marketShareSource = "Teletest / ORF Jahresbericht 2025",
                aliases = listOf("PULS4", "PULS 4 HD", "PULS4 HD", "AT: PULS 4 HD"),
                keywords = listOf("puls 4", "puls4"), tvgIds = listOf("Puls4.at"),
                tvgNamePatterns = listOf("puls 4*"), matchPatterns = listOf("(?i)\\bpuls\\s*4\\b"),
                excludePatterns = listOf("(?i)\\b(24|8)\\b"), isNational = true, isRegional = false,
                isPublicService = false, isPrivate = true
            ),
            DachChannel(
                channelId = "AT_PULS_24", canonicalName = "PULS 24", shortName = "PULS 24",
                countryCode = "AT", countryName = "Österreich", region = "AT-NAT", language = "de",
                category = "Private", genre = "News", popularityRank = 8, popularityScore = 76.0,
                marketShare = 1.1, marketShareYear = 2025, marketShareSource = "Teletest / ORF Jahresbericht 2025",
                aliases = listOf("PULS24", "PULS 24 HD", "PULS24 HD", "AT: PULS 24 HD"),
                keywords = listOf("puls 24", "puls24"), tvgIds = listOf("Puls24.at"),
                tvgNamePatterns = listOf("puls 24*"), matchPatterns = listOf("(?i)\\bpuls\\s*24\\b"),
                excludePatterns = listOf("(?i)\\b(4|8)\\b"), isNational = true, isRegional = false,
                isPublicService = false, isPrivate = true
            ),
            DachChannel(
                channelId = "AT_ATV", canonicalName = "ATV", shortName = "ATV",
                countryCode = "AT", countryName = "Österreich", region = "AT-NAT", language = "de",
                category = "Private", genre = "Vollprogramm", popularityRank = 5, popularityScore = 85.0,
                marketShare = 2.6, marketShareYear = 2025, marketShareSource = "Teletest / ORF Jahresbericht 2025",
                aliases = listOf("ATV HD", "ATV Österreich", "AT: ATV HD"),
                keywords = listOf("atv"), tvgIds = listOf("ATV.at"),
                tvgNamePatterns = listOf("atv*"), matchPatterns = listOf("(?i)\\batv\\b"),
                excludePatterns = listOf("(?i)\\b(2|ii|smart)\\b"), isNational = true, isRegional = false,
                isPublicService = false, isPrivate = true
            ),
            DachChannel(
                channelId = "AT_ATV2", canonicalName = "ATV II", shortName = "ATV 2",
                countryCode = "AT", countryName = "Österreich", region = "AT-NAT", language = "de",
                category = "Private", genre = "Entertainment", popularityRank = 9, popularityScore = 74.0,
                marketShare = 1.0, marketShareYear = 2025, marketShareSource = "Teletest / ORF Jahresbericht 2025",
                aliases = listOf("ATV2", "ATV 2", "ATV II HD", "ATV 2 HD", "AT: ATV2 HD"),
                keywords = listOf("atv 2", "atv2", "atv ii"), tvgIds = listOf("ATV2.at"),
                tvgNamePatterns = listOf("atv 2*", "atv2*"), matchPatterns = listOf("(?i)\\batv\\s*(2|ii)\\b"),
                excludePatterns = emptyList(), isNational = true, isRegional = false,
                isPublicService = false, isPrivate = true
            ),
            DachChannel(
                channelId = "AT_SERVUSTV", canonicalName = "ServusTV Österreich", shortName = "ServusTV AT",
                countryCode = "AT", countryName = "Österreich", region = "AT-NAT", language = "de",
                category = "Private", genre = "Vollprogramm", popularityRank = 6, popularityScore = 82.0,
                marketShare = 3.2, marketShareYear = 2025, marketShareSource = "Teletest / ORF Jahresbericht 2025",
                aliases = listOf("ServusTV AT", "ServusTV Österreich HD", "Servus TV AT", "Servus TV Oesterreich", "AT: ServusTV"),
                keywords = listOf("servustv", "servus tv"), tvgIds = listOf("ServusTV.at"),
                tvgNamePatterns = listOf("servustv*"), matchPatterns = listOf("(?i)\\bservustv?(\\s*oesterreich|\\s*österreich|\\s*at)?\\b"),
                excludePatterns = listOf("(?i)\\b(deutschland|de)\\b"), isNational = true, isRegional = false,
                isPublicService = false, isPrivate = true
            ),
            DachChannel(
                channelId = "AT_SKY_SPORT_AUSTRIA", canonicalName = "Sky Sport Austria", shortName = "Sky Sport AT",
                countryCode = "AT", countryName = "Österreich", region = "AT-NAT", language = "de",
                category = "PayTV", genre = "Sport", popularityRank = 15, popularityScore = 80.0,
                marketShare = 0.8, marketShareYear = 2025, marketShareSource = "Teletest / ORF Jahresbericht 2025",
                aliases = listOf("Sky Sport Austria HD", "Sky Sport Austria 1 HD", "Sky Sport Austria 2 HD", "AT: Sky Sport Austria"),
                keywords = listOf("sky sport austria"), tvgIds = listOf("SkySportAustria.at"),
                tvgNamePatterns = listOf("sky sport austria*"), matchPatterns = listOf("(?i)\\bsky\\s*sport\\s*austria\\b"),
                excludePatterns = emptyList(), isNational = true, isRegional = false,
                isPublicService = false, isPrivate = true
            ),
            // SCHWEIZ
            DachChannel(
                channelId = "CH_SRF_1", canonicalName = "SRF 1", shortName = "SRF 1",
                countryCode = "CH", countryName = "Schweiz", region = "DE-CH", language = "de",
                category = "SRF", genre = "Vollprogramm", popularityRank = 1, popularityScore = 100.0,
                marketShare = 19.2, marketShareYear = 2024, marketShareSource = "Mediapulse TV Data 2024",
                aliases = listOf("SRF1", "SRF 1 HD", "SRF1 HD", "CH: SRF 1 HD"),
                keywords = listOf("srf 1", "srf1", "sf 1"), tvgIds = listOf("SRF1.ch"),
                tvgNamePatterns = listOf("srf 1*"), matchPatterns = listOf("(?i)\\b(srf|sf)\\s*1\\b"),
                excludePatterns = listOf("(?i)\\b(zwei|2|info)\\b"), isNational = true, isRegional = false,
                isPublicService = true, isPrivate = false
            ),
            DachChannel(
                channelId = "CH_SRF_ZWEI", canonicalName = "SRF zwei", shortName = "SRF 2",
                countryCode = "CH", countryName = "Schweiz", region = "DE-CH", language = "de",
                category = "SRF", genre = "Vollprogramm", popularityRank = 2, popularityScore = 94.0,
                marketShare = 9.1, marketShareYear = 2024, marketShareSource = "Mediapulse TV Data 2024",
                aliases = listOf("SRF 2", "SRF2", "SRF zwei HD", "SRF 2 HD", "CH: SRF zwei HD"),
                keywords = listOf("srf zwei", "srf 2"), tvgIds = listOf("SRFzwei.ch"),
                tvgNamePatterns = listOf("srf 2*"), matchPatterns = listOf("(?i)\\b(srf|sf)\\s*(2|zwei)\\b"),
                excludePatterns = emptyList(), isNational = true, isRegional = false,
                isPublicService = true, isPrivate = false
            ),
            DachChannel(
                channelId = "CH_3_PLUS", canonicalName = "3+", shortName = "3+",
                countryCode = "CH", countryName = "Schweiz", region = "DE-CH", language = "de",
                category = "Private", genre = "Entertainment", popularityRank = 3, popularityScore = 85.0,
                marketShare = 2.5, marketShareYear = 2024, marketShareSource = "Mediapulse TV Data 2024",
                aliases = listOf("3plus", "3 Plus", "3+ HD", "CH: 3+ HD"),
                keywords = listOf("3+", "3plus", "3 plus"), tvgIds = listOf("3plus.ch"),
                tvgNamePatterns = listOf("3+*"), matchPatterns = listOf("(?i)\\b(3\\+|3\\s*plus)\\b"),
                excludePatterns = listOf("(?i)\\b3sat\\b"), isNational = true, isRegional = false,
                isPublicService = false, isPrivate = true
            ),
            DachChannel(
                channelId = "CH_PULS_8", canonicalName = "Puls 8", shortName = "Puls 8",
                countryCode = "CH", countryName = "Schweiz", region = "DE-CH", language = "de",
                category = "Private", genre = "Entertainment", popularityRank = 7, popularityScore = 72.0,
                marketShare = 0.9, marketShareYear = 2024, marketShareSource = "Mediapulse TV Data 2024",
                aliases = listOf("Puls8", "Puls 8 HD", "CH: Puls 8 HD"),
                keywords = listOf("puls 8", "puls8"), tvgIds = listOf("puls8.ch"),
                tvgNamePatterns = listOf("puls 8*"), matchPatterns = listOf("(?i)\\bpuls\\s*8\\b"),
                excludePatterns = listOf("(?i)\\b(4|24)\\b"), isNational = true, isRegional = false,
                isPublicService = false, isPrivate = true
            ),
            DachChannel(
                channelId = "CH_RTS_1", canonicalName = "RTS 1", shortName = "RTS 1",
                countryCode = "CH", countryName = "Schweiz", region = "FR-CH", language = "fr",
                category = "RTS", genre = "Vollprogramm", popularityRank = 20, popularityScore = 95.0,
                marketShare = 21.5, marketShareYear = 2024, marketShareSource = "Mediapulse Romandie 2024",
                aliases = listOf("RTS Un", "RTS 1 HD", "RTS Un HD", "CH: RTS 1 HD"),
                keywords = listOf("rts 1", "rts un"), tvgIds = listOf("RTS1.ch"),
                tvgNamePatterns = listOf("rts 1*"), matchPatterns = listOf("(?i)\\brts\\s*(1|un)\\b"),
                excludePatterns = listOf("(?i)\\b(deux|2|info)\\b"), isNational = true, isRegional = false,
                isPublicService = true, isPrivate = false
            ),
            DachChannel(
                channelId = "CH_RSI_LA_1", canonicalName = "RSI LA 1", shortName = "LA 1",
                countryCode = "CH", countryName = "Schweiz", region = "IT-CH", language = "it",
                category = "RSI", genre = "Vollprogramm", popularityRank = 30, popularityScore = 95.0,
                marketShare = 18.5, marketShareYear = 2024, marketShareSource = "Mediapulse Svizzera italiana 2024",
                aliases = listOf("RSI LA1", "RSI LA 1 HD", "CH: RSI LA 1 HD"),
                keywords = listOf("rsi la 1", "rsi la1"), tvgIds = listOf("RSILA1.ch"),
                tvgNamePatterns = listOf("rsi la 1*"), matchPatterns = listOf("(?i)\\brsi\\s*la\\s*1\\b"),
                excludePatterns = listOf("(?i)\\bla\\s*2\\b"), isNational = true, isRegional = false,
                isPublicService = true, isPrivate = false
            )
        )
        seed.forEach { registerChannel(it) }
    }
}
