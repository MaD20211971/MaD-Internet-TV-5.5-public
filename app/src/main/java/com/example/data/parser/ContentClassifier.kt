package com.example.data.parser

import com.example.data.model.ChannelItem
import com.example.data.model.ContentType
import com.example.util.FastLruCache
import java.util.regex.Pattern

data class ContentClassification(
    val type: ContentType,
    val seriesTitle: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val isDach: Boolean = false,
    val countryCode: String = "INT",
    val groupTitle: String = "",
    val is24_7: Boolean = false,
    val isVip: Boolean = false
)

data class CountryFolder(
    val code: String,              // "DE", "AT", "CH", "SPORT", "UK", "US", "TR", "EXYU", "IT", "FR", "ES", "ADULT", "OTHER"
    val displayName: String,       // "Deutschland", "Österreich", "Schweiz", "Sport", etc.
    val flagEmoji: String,         // "🇩🇪", "🇦🇹", "🇨🇭", "⚽", "🇬🇧", etc.
    val subFolders: List<String>,  // raw groupTitle strings
    val channelCount: Int = 0,
    val subFolderCounts: Map<String, Int> = emptyMap()
)

object ContentClassifier {

    private val NUMBERED_MOVIE_REGEX = Pattern.compile("""^\s*\d{1,4}\s*[-–.:|]\s*([A-Za-z0-9\p{L}].*)""")
    private val MOVIE_YEAR_REGEX = Pattern.compile("""[\(\[\{](\d{4})[\)\]\}]""")
    private val SERIES_S_E_REGEX = Pattern.compile("""(?i)(?:^|[\s\[\(_\-|/])S(\d{1,3})\s*[-._/ ]?\s*(?:E|EP|Folge|Episode)\s*\.?\s*(\d{1,4})(?:$|[\s\]\)_–\-\.:|])""")
    private val SERIES_STAFFEL_REGEX = Pattern.compile("""(?i)(?:^|[\s\[\(_\-|/])Staffel\s*(\d{1,3})\s*[-.,/_ ]*\s*(?:Folge|Episode|Ep|E)\s*\.?\s*(\d{1,4})(?:$|[\s\]\)_–\-\.:|])""")
    private val SERIES_SEASON_REGEX = Pattern.compile("""(?i)(?:^|[\s\[\(_\-|/])Season\s*(\d{1,3})\s*[-.,/_ ]*\s*(?:Episode|Folge|Ep|E)\s*\.?\s*(\d{1,4})(?:$|[\s\]\)_–\-\.:|])""")
    private val SERIES_NUM_X_NUM_REGEX = Pattern.compile("""(?i)(?:^|[\s\[\(_\-|/])(\d{1,2})\s*[xX]\s*(\d{1,4})(?:$|[\s\]\)_–\-\.:|])""")
    private val SERIES_S_D_REGEX = Pattern.compile("""(?i)(?:^|[\s\[\(_\-|/])S(\d{1,3})\s*[-._/ ]+\s*(\d{1,4})(?:$|[\s\]\)_–\-\.:|])""")
    private val SERIES_STAFFEL_NUM_REGEX = Pattern.compile("""(?i)(?:^|[\s\[\(_\-|/])(?:Staffel|Season)\s*(\d{1,3})\s*[-._/ ]+\s*(\d{1,4})(?:$|[\s\]\)_–\-\.:|])""")
    private val SERIES_EP_ONLY_REGEX = Pattern.compile("""(?i)(?:^|[\s\[\(_\-|/])(?:Folge|Episode|Ep\.?|E)\s*(\d{1,4})(?:$|[\s\]\)_–\-\.:|])""")
    private val SERIES_SEASON_ONLY_REGEX = Pattern.compile("""(?i)(?:^|[\s\[\(_\-|/])(?:Staffel|Season|S)\s*(\d{1,3})(?:$|[\s\]\)_–\-\.:|])""")

    private val PREFIX_DELIMITER_REGEX = Pattern.compile("""^([A-Z0-9\+]{2,6})\s*[:\|\┃\║\-\–\—\*•✪★☆~>\/\\]\s*""")
    private val BRACKET_TAG_REGEX = Pattern.compile("""^(?:[\[\(【]([A-Z0-9\+]{2,6})[\]\)】]|\|([A-Z0-9\+]{2,6})\||┃([A-Z0-9\+]{2,6})┃|║([A-Z0-9\+]{2,6})║)\s*""")
    private val EU_PREFIX_REGEX = Pattern.compile("""^EU\s*[\-\|\┃\*\–]?\s*([A-Z0-9\+]{2,12})\b""")
    private val SPELLED_COUNTRY_REGEX = Pattern.compile("""^([A-Z]{3,14})\s*[:\|\┃\║\-\–\—\*•✪★☆~>\/\\]\s*""")

    private val CLEAN_PREFIX_REGEX = Regex("""^([A-Za-z0-9\+]{2,6})\s*[:\|\┃\║\-\–\—\*•✪★☆~>\/\\]\s*|^[\[\(【]([A-Za-z0-9\+]{2,6})[\]\)】]\s*|^[\|\┃\║]([A-Za-z0-9\+]{2,6})[\|\┃\║]\s*|^EU\s*[\-\|\┃\*\–]?\s*([A-Za-z0-9\+]{2,12})\s*[:\|\┃\-\*•✪★☆]?\s*""", RegexOption.IGNORE_CASE)
    private val SUB_HEADER_REGEX = Regex("""^(?:Kino|Cinema|Filme|Movies|Serien|Series|Live|Adult|Erotik)\s*[:\|\-\–\—\*]\s*""", RegexOption.IGNORE_CASE)
    private val LEADING_NUM_REGEX = Regex("""^\d{1,4}\s*[-.|)]\s*""")
    private val BANNER_REGEX = Regex("""^[\s\-_=*\#\+~<>\!\|★►◄●■♦✦▼▲━─═]{2,}.+[\s\-_=*\#\+~<>\!\|★►◄●■♦✦▼▲━─═]{2,}$""")

    private val cleanSortTitleCache = FastLruCache<String, String>(16384)
    private val prefixCountryCache = FastLruCache<String, String>(4096)
    private val categoryPriorityCache = FastLruCache<String, Int>(2048)
    private val categorySubRankCache = FastLruCache<String, Int>(2048)
    private val normalizedCountryCodeCache = FastLruCache<String, String>(16384)
    private val liveTvSortRankCache = FastLruCache<String, Int>(16384)

    fun classify(
        name: String,
        group: String?,
        streamUrl: String?,
        rawCmd: String? = null,
        tvgType: String? = null,
        tvgCountry: String? = null
    ): ContentClassification {
        val cleanName = name.trim()
        val rawGroup = group?.trim() ?: ""
        val (isDach, countryCode) = detectCountryAndDach(cleanName, rawGroup, tvgCountry)
        val is24_7 = detect24_7(cleanName, rawGroup)
        val isVip = detectVip(cleanName, rawGroup)

        // 1. Radio check (Explicit TVG type, Radio Groups, Audio streams, or known German/Austrian/Swiss Radio stations)
        if (isRadioStation(cleanName, rawGroup, streamUrl, tvgType)) {
            val radioGroup = resolveRadioGroupTitle(cleanName, rawGroup, countryCode, isDach)
            return ContentClassification(
                type = ContentType.RADIO,
                isDach = isDach,
                countryCode = countryCode,
                groupTitle = radioGroup,
                is24_7 = false,
                isVip = isVip
            )
        }

        // 2. Explicit TVG Type (Series / Movies)
        if (tvgType.equals("series", ignoreCase = true) || tvgType.equals("tvshow", ignoreCase = true)) {
            val (sTitle, sSeason, sEp) = extractSeriesMetadata(cleanName, rawGroup)
            return ContentClassification(
                type = ContentType.SERIES,
                seriesTitle = sTitle ?: cleanSeriesTitle(cleanName),
                season = sSeason ?: 1,
                episode = sEp ?: 1,
                isDach = isDach,
                countryCode = countryCode,
                groupTitle = if (rawGroup.isNotBlank() && rawGroup != "All") rawGroup else if (isDach) "DEUTSCH | Serien" else "Serien",
                is24_7 = false,
                isVip = isVip
            )
        }
        if (tvgType.equals("movie", ignoreCase = true) || tvgType.equals("vod", ignoreCase = true)) {
            val movieGroup = resolveMovieGroupTitle(cleanName, rawGroup, countryCode, isDach)
            return ContentClassification(
                type = ContentType.MOVIE,
                isDach = isDach,
                countryCode = countryCode,
                groupTitle = movieGroup,
                is24_7 = false,
                isVip = isVip
            )
        }

        // 3. Explicit Live TV / ITV Type
        if (tvgType.equals("live", ignoreCase = true) || tvgType.equals("itv", ignoreCase = true)) {
            val liveGroup = if (rawGroup.isNotBlank() && rawGroup != "All" && rawGroup != "Alle") rawGroup else resolveLiveGroupTitle(cleanName, rawGroup, countryCode, isDach)
            return ContentClassification(
                type = ContentType.LIVE_TV,
                isDach = isDach,
                countryCode = countryCode,
                groupTitle = liveGroup,
                is24_7 = is24_7,
                isVip = isVip
            )
        }

        // 4. Known Live TV Broadcasters check (prevents false positives for 24/7 TV channels)
        if (isKnownLiveBroadcastStation(cleanName, rawGroup)) {
            return ContentClassification(
                type = ContentType.LIVE_TV,
                isDach = isDach,
                countryCode = countryCode,
                groupTitle = resolveLiveGroupTitle(cleanName, rawGroup, countryCode, isDach),
                is24_7 = is24_7,
                isVip = isVip
            )
        }

        // 3. Series Metadata in Title (S01E02, 1x04, Staffel 1 Folge 3, etc.)
        val (sTitle, sSeason, sEp) = extractSeriesMetadata(cleanName, rawGroup)
        if (sSeason != null || sEp != null) {
            return ContentClassification(
                type = ContentType.SERIES,
                seriesTitle = sTitle ?: cleanSeriesTitle(cleanName),
                season = sSeason ?: 1,
                episode = sEp ?: 1,
                isDach = isDach,
                countryCode = countryCode,
                groupTitle = if (rawGroup.isNotBlank() && rawGroup != "All") rawGroup else if (isDach) "DEUTSCH | Serien" else "Serien",
                is24_7 = false,
                isVip = isVip
            )
        }

        // 4. Series Category, Group, or Series Loop Box (e.g. Stargate Atlantis, Prime Box, etc.)
        val groupUpper = rawGroup.uppercase()
        val nameUpper = cleanName.uppercase()
        val isSeriesLoopBox = nameUpper.contains("PRIME BOX") || nameUpper.contains("STARGATE") ||
                              nameUpper.contains("ATLANTIS") || nameUpper.contains("TOP SERIEN") ||
                              nameUpper.contains("TV SERIEN") || groupUpper.contains("SERIEN 24/7") ||
                              groupUpper.contains("TOP SERIEN") || groupUpper.contains("TV SERIEN")
        if (groupUpper.contains("SERIEN") || groupUpper.contains("SERIES") || groupUpper.contains("STAFFEL") ||
            groupUpper.contains("TV SHOWS") || groupUpper.contains("DIZI") || groupUpper.contains("ANIME") ||
            groupUpper.contains("EPISODE") || isSeriesLoopBox
        ) {
            return ContentClassification(
                type = ContentType.SERIES,
                seriesTitle = sTitle ?: cleanSeriesTitle(cleanName),
                season = sSeason ?: 1,
                episode = sEp ?: 1,
                isDach = isDach,
                countryCode = countryCode,
                groupTitle = if (rawGroup.isNotBlank() && rawGroup != "All") rawGroup else if (isDach) "DEUTSCH | Serien" else "Serien",
                is24_7 = is24_7 || isSeriesLoopBox,
                isVip = isVip
            )
        }

        // 5. Numbered Movie Pattern (e.g., "1- Greenland 2", "2- Final Fight", "3- Dune", "01 - Avatar", "1. Inception")
        val isLiveGroupContext = groupUpper.contains("LIVE") || groupUpper.contains("TV") ||
                groupUpper.contains("DE:") || groupUpper.contains("DE|") || groupUpper.contains("DE -") ||
                groupUpper.contains("SPORT") || groupUpper.contains("SKY") || groupUpper.contains("DACH") ||
                groupUpper.contains("BUNDESLIGA") || groupUpper.contains("NEWS") || groupUpper.contains("KINDER") ||
                groupUpper.contains("DOKU") || groupUpper.contains("REGIONAL") || groupUpper.contains("ALLGEMEIN")

        if (NUMBERED_MOVIE_REGEX.matcher(cleanName).find() && !isLiveGroupContext &&
            !cleanName.uppercase().contains("24/7") && !rawGroup.uppercase().contains("24/7") &&
            !isKnownLiveBroadcastStation(cleanName, rawGroup)
        ) {
            val movieGroup = resolveMovieGroupTitle(cleanName, rawGroup, countryCode, isDach)
            return ContentClassification(
                type = ContentType.MOVIE,
                isDach = isDach,
                countryCode = countryCode,
                groupTitle = movieGroup,
                is24_7 = false,
                isVip = isVip
            )
        }

        // 6. VOD / Movie Keywords in Category or Movie Loop Box (e.g. Blackbox Cinema, Netflix Box, Kriegsfilm, etc.)
        val isMovieLoopBox = nameUpper.contains("BLACKBOX") || nameUpper.contains("KINO LOUNGE") ||
                             nameUpper.contains("KRIEGSFILM") || nameUpper.contains("NETFLIX BOX") ||
                             nameUpper.contains("BOX NETFLIX") || nameUpper.contains("CINEMA 24/7") ||
                             nameUpper.contains("TV CINEMA 24/7") || groupUpper.contains("CINEMA 24/7") ||
                             groupUpper.contains("TV CINEMA") || groupUpper.contains("KINO LOUNGE")
        if (isMovieCategory(groupUpper) || isMovieLoopBox) {
            val movieGroup = resolveMovieGroupTitle(cleanName, rawGroup, countryCode, isDach)
            return ContentClassification(
                type = ContentType.MOVIE,
                isDach = isDach,
                countryCode = countryCode,
                groupTitle = movieGroup,
                is24_7 = is24_7 || isMovieLoopBox,
                isVip = isVip
            )
        }

        // 7. Video file extensions or VOD stream URLs / commands
        val url = (streamUrl ?: "").lowercase()
        val cmd = (rawCmd ?: "").lowercase()
        if (url.endsWith(".mp4") || url.endsWith(".mkv") || url.endsWith(".avi") ||
            url.contains("/movie/") || url.contains("/vod/") || cmd.contains("type=vod") ||
            cmd.contains(".mp4") || cmd.contains(".mkv")
        ) {
            val movieGroup = resolveMovieGroupTitle(cleanName, rawGroup, countryCode, isDach)
            return ContentClassification(
                type = ContentType.MOVIE,
                isDach = isDach,
                countryCode = countryCode,
                groupTitle = movieGroup,
                is24_7 = false,
                isVip = isVip
            )
        }

        // 8. Release Year in Title (e.g. "Oppenheimer (2023)") unless live stream / 24/7
        if (MOVIE_YEAR_REGEX.matcher(cleanName).find() && !cleanName.uppercase().contains("LIVE") && !rawGroup.uppercase().contains("LIVE") && !is24_7) {
            val movieGroup = resolveMovieGroupTitle(cleanName, rawGroup, countryCode, isDach)
            return ContentClassification(
                type = ContentType.MOVIE,
                isDach = isDach,
                countryCode = countryCode,
                groupTitle = movieGroup,
                is24_7 = false,
                isVip = isVip
            )
        }

        // Default: Live TV Channel
        return ContentClassification(
            type = ContentType.LIVE_TV,
            isDach = isDach,
            countryCode = countryCode,
            groupTitle = resolveLiveGroupTitle(cleanName, rawGroup, countryCode, isDach),
            is24_7 = is24_7,
            isVip = isVip
        )
    }

    fun detect24_7(name: String, group: String?): Boolean {
        val check = "$name ${group ?: ""}".uppercase()
        return check.contains("24/7") || check.contains("24-7") || check.contains("24X7") || check.contains("24/7 SENDER") ||
               check.contains("PRIME BOX") || check.contains("BLACKBOX") || check.contains("BLACK BOX") ||
               check.contains("NETFLIX BOX") || check.contains("BOX NETFLIX") || check.contains("SKY BOX") ||
               check.contains("KINO LOUNGE") || check.contains("NETFLIX TOP10") || check.contains("NETFLIX TOP 10") ||
               check.contains("TV ACTOR 24/7") || check.contains("TV CINEMA 24/7") || check.contains("TV SERIEN 24/7") ||
               check.contains("TOP SERIEN 24/7")
    }

    /**
     * Identifies non-linear theme loops, VOD/series boxes, or web-doc topic streams
     * (e.g. Stargate Atlantis, Zweiter Weltkrieg, Krieg und Militär, etc.) that do not belong
     * in the primary linear broadcast television lineup.
     */
    fun isNonLinearOrThematicLoop(name: String, group: String?): Boolean {
        val upperName = name.uppercase().trim()
        val upperGroup = (group ?: "").uppercase().trim()

        if (detect24_7(name, group)) return true

        // Explicit series/movie box loops
        if (upperName.contains("| PRIME BOX") || upperName.contains("| NETFLIX") ||
            upperName.contains("PRIME BOX") || upperName.contains("BLACKBOX") ||
            upperName.contains("BLACK BOX") || upperName.contains("SKY BOX") ||
            upperName.contains("NETFLIX BOX") || upperName.contains("BOX NETFLIX") ||
            upperName.contains("GERMANY BOX") || upperName.contains("STARGATE ATLANTIS") ||
            upperName.contains("ATLANTIS |") || upperName.startsWith("ATLANTIS") ||
            upperName.contains("KINO LOUNGE") || upperName.contains("NETFLIX TOP10") ||
            upperName.contains("NETFLIX TOP 10") || upperName.contains("TV ACTOR 24/7") ||
            upperName.contains("TV CINEMA 24/7") || upperName.contains("TV SERIEN 24/7") ||
            upperName.contains("TOP SERIEN 24/7")
        ) {
            return true
        }

        // Looped web/documentary themes (often found in DOKU genres or non-linear channels)
        val loopThematicTitles = listOf(
            "DER ZWEITE WELTKRIEG", "ZWEITER WELTKRIEG", "KRIEG UND MILITÄR", "KRIEGSTÜCHTIG",
            "KRIEGSFILM", "MR WISSEN 2GO", "QUARKS", "STRG F", "TIMELINE DEUTSCHLAND",
            "UNENDLICH WELT", "FLINT TOWN", "FEUER UND FLAMME", "ANWAELTE DER TOTEN",
            "LEBEN AUF UNSEREM PLANETEN", "DAS UNIVERSUM", "DANGEROUS ANIMALS",
            "UNSERE WINZIGEN NACHBARN", "UNSER PLANET", "BILDSCHOENE WELT",
            "DER MENSCH INNERE WELTEN", "GESELLSCHAFT UND KRIMINALITÄT", "SPACERACE",
            "ASTRONOMIE", "ÄGYPTOLOGIE", "ÄRCHÄOLOGIE", "ARCHITEKTUR", "ANCIENT ALIENS",
            "SCHIFFFAHRTSKUNDE", "KATASTROPHEN", "COMPUTER & INTERNET", "RUBRIK DROGEN",
            "MITTELALTER & ANTIKE", "LUFT / RAUMFAHRT", "TIER UND PFLANZEN", "WISSEN A BIS Z"
        )
        if (loopThematicTitles.any { upperName.contains(it) }) {
            return true
        }

        // War/military thematic loops (unless it is an official linear broadcast station)
        if ((upperName.contains("WELTKRIEG") || upperName.contains("KRIEG UND") ||
             upperName.contains("KRIEGSTÜCHTIG") || upperName.contains("KRIEGSFILM")) &&
            !isKnownLiveBroadcastStation(name, group ?: "")
        ) {
            return true
        }

        return false
    }

    fun isDocumentaryContent(name: String, group: String?): Boolean {
        val check = "$name ${group ?: ""}".uppercase()
        val docKeywords = listOf(
            "DOKU", "DOCUMENTARY", "DOKUMENTATION", "WISSEN", "HISTORY",
            "DISCOVERY", "GEO TELEVISION", "NAT GEO", "NATIONAL GEOGRAPHIC",
            "PLANET", "TERRA X", "SPIEGEL GESCHICHTE", "SPIEGEL TV", "KABEL EINS DOKU",
            "N24 DOKU", "ZDFINFO", "ANIMAL PLANET", "CRIME + INVESTIGATION",
            "CURIOSITY", "MARCO POLO", "WELT DER WUNDER", "PHOENIX", "TAGESSCHAU24",
            "DW-TV", "ARTE", "3SAT", "MR WISSEN 2GO", "QUARKS", "STRG F",
            "ASTRONOMIE", "ÄGYPTOLOGIE", "ARCHÄOLOGIE", "UNIVERSUM"
        )
        return docKeywords.any { check.contains(it) }
    }

    fun detectVip(name: String, group: String?): Boolean {
        val check = "$name ${group ?: ""}".uppercase()
        return check.contains("★") || check.contains("⭐") || check.contains("✪") || check.contains("VIP") || check.contains("PREMIUM")
    }

    private fun isKnownLiveBroadcastStation(name: String, group: String): Boolean {
        val upper = "$name $group".uppercase()
        val stations = listOf(
            "DAS ERSTE", "ARD", "ZDF", "ZDFINFO", "ZDFNEO", "RTL DE", "RTL TELEVISION", "RTL ZWEI", "SAT.1", "SAT1", "PROSIEBEN", "PRO7", "VOX",
            "KABEL EINS", "WDR", "NDR", "SWR", "BR FERNSEHEN", "MDR", "HR FERNSEHEN", "RBB",
            "TAGESSCHAU", "PHOENIX", "KIKA", "3SAT", "ARTE", "SPORT1", "EUROSPORT 1 DE", "EUROSPORT 2 DE", "DAZN",
            "SKY SPORT DE", "BUNDESLIGA", "N-TV", "WELT HD", "ORF 1", "ORF 2", "ORF 3", "SRF 1", "SRF 2", "BBC",
            "CNN", "CNBC", "EURONEWS", "AL JAZEERA", "SERVUSTV", "SUPER RTL", "NITRO",
            "SIXX", "PRO7 MAXX", "SAT.1 GOLD", "KABEL EINS DOKU", "N24 DOKU", "COMEDY CENTRAL DE",
            "MTV DE", "DELUXE MUSIC", "TELE 5", "TLC DE", "DMAX DE", "DISCOVERY DE", "NAT GEO DE"
        )
        for (st in stations) {
            if (upper.contains(st)) return true
        }
        return false
    }

    private fun isMovieCategory(groupUpper: String): Boolean {
        if (groupUpper.isBlank()) return false
        val keywords = listOf(
            "VOD", "FILM", "MOVIE", "CINEMA", "KINO", "BLOCKBUSTER", "PELICULAS", "NETFLIX",
            "AMAZON", "DISNEY+", "APPLE TV", "PARAMOUNT", "BOX OFFICE", "VOD VIP",
            "KINO VIP", "KINO AUF ABRUF"
        )
        return keywords.any { kw -> groupUpper.contains(kw) }
    }

    fun extractSeriesMetadata(name: String, group: String? = null): Triple<String?, Int?, Int?> {
        val clean = name.trim()

        // 1. Try S01E01 / S01 E01 / S01-E01 / S01.E01 / S01_E01 / S01 - EP01
        val m1 = SERIES_S_E_REGEX.matcher(clean)
        if (m1.find()) {
            val season = m1.group(1)?.toIntOrNull() ?: 1
            val episode = m1.group(2)?.toIntOrNull() ?: 1
            val title = extractTitleAroundMatch(clean, m1.start(), m1.end(), group)
            return Triple(title, season, episode)
        }

        // 2. Try Staffel 1 Folge 1 / Staffel 1 Episode 1 / Staffel 1 - Folge 1
        val m2 = SERIES_STAFFEL_REGEX.matcher(clean)
        if (m2.find()) {
            val season = m2.group(1)?.toIntOrNull() ?: 1
            val episode = m2.group(2)?.toIntOrNull() ?: 1
            val title = extractTitleAroundMatch(clean, m2.start(), m2.end(), group)
            return Triple(title, season, episode)
        }

        // 3. Try Season 1 Episode 1 / Season 1 - Episode 1
        val m3 = SERIES_SEASON_REGEX.matcher(clean)
        if (m3.find()) {
            val season = m3.group(1)?.toIntOrNull() ?: 1
            val episode = m3.group(2)?.toIntOrNull() ?: 1
            val title = extractTitleAroundMatch(clean, m3.start(), m3.end(), group)
            return Triple(title, season, episode)
        }

        // 4. Try 1x01 / 01x01 / 1 x 01
        val m4 = SERIES_NUM_X_NUM_REGEX.matcher(clean)
        if (m4.find()) {
            val season = m4.group(1)?.toIntOrNull() ?: 1
            val episode = m4.group(2)?.toIntOrNull() ?: 1
            val title = extractTitleAroundMatch(clean, m4.start(), m4.end(), group)
            return Triple(title, season, episode)
        }

        // 4b. Try S01 01 / S01-01 / S1 02 (without E/Folge)
        val mSD = SERIES_S_D_REGEX.matcher(clean)
        if (mSD.find()) {
            val season = mSD.group(1)?.toIntOrNull() ?: 1
            val episode = mSD.group(2)?.toIntOrNull() ?: 1
            val title = extractTitleAroundMatch(clean, mSD.start(), mSD.end(), group)
            return Triple(title, season, episode)
        }

        // 4c. Try Staffel 1 01 / Season 1 01
        val mStaffelNum = SERIES_STAFFEL_NUM_REGEX.matcher(clean)
        if (mStaffelNum.find()) {
            val season = mStaffelNum.group(1)?.toIntOrNull() ?: 1
            val episode = mStaffelNum.group(2)?.toIntOrNull() ?: 1
            val title = extractTitleAroundMatch(clean, mStaffelNum.start(), mStaffelNum.end(), group)
            return Triple(title, season, episode)
        }

        // 5. Check if episode only in name (e.g. "Folge 02", "Episode 05") and season in group or earlier in name
        val mEp = SERIES_EP_ONLY_REGEX.matcher(clean)
        if (mEp.find()) {
            val episode = mEp.group(1)?.toIntOrNull() ?: 1
            var season: Int? = null
            var groupTitleCandidate: String? = null
            if (!group.isNullOrBlank()) {
                val mGroupSeason = SERIES_SEASON_ONLY_REGEX.matcher(group)
                if (mGroupSeason.find()) {
                    season = mGroupSeason.group(1)?.toIntOrNull()
                    groupTitleCandidate = extractTitleAroundMatch(group, mGroupSeason.start(), mGroupSeason.end(), null)
                }
            }
            if (season == null) {
                val mCleanSeason = SERIES_SEASON_ONLY_REGEX.matcher(clean)
                if (mCleanSeason.find() && mCleanSeason.start() < mEp.start()) {
                    season = mCleanSeason.group(1)?.toIntOrNull()
                }
            }
            val title = groupTitleCandidate ?: extractTitleAroundMatch(clean, mEp.start(), mEp.end(), group)
            return Triple(title, season, episode)
        }

        // 6. Check if Season only in name (e.g. "Show Name - Staffel 2")
        val mSeason = SERIES_SEASON_ONLY_REGEX.matcher(clean)
        if (mSeason.find()) {
            val season = mSeason.group(1)?.toIntOrNull() ?: 1
            val title = extractTitleAroundMatch(clean, mSeason.start(), mSeason.end(), group)
            return Triple(title, season, null)
        }

        return Triple(null, null, null)
    }

    private fun extractTitleAroundMatch(text: String, matchStart: Int, matchEnd: Int, fallbackGroup: String?): String? {
        var before = text.substring(0, matchStart).trim()
        val after = text.substring(matchEnd).trim()

        before = before.trimEnd('-', ':', '|', '/', '–', '—', '*', '•', '✪', '★', '(', '[', '{', '.', ',')
            .trimStart('-', ':', '|', '/', '–', '—', '*', '•', '✪', '★', ')', ']', '}', '.', ',')
            .trim()

        val candidate = if (before.isNotBlank()) {
            cleanSeriesTitle(before)
        } else if (after.isNotBlank()) {
            val cleanAfter = after.trimStart('-', ':', '|', '/', '–', '—', '*', '•', '✪', '★', ')', ']', '}', '.', ',')
                .trimEnd('-', ':', '|', '/', '–', '—', '*', '•', '✪', '★', '(', '[', '{', '.', ',')
                .trim()
            cleanSeriesTitle(cleanAfter)
        } else if (!fallbackGroup.isNullOrBlank()) {
            cleanSeriesTitle(fallbackGroup)
        } else {
            ""
        }
        return candidate.ifBlank { null }
    }

    fun cleanSeriesTitle(raw: String): String {
        var s = raw.trim()
        s = CLEAN_PREFIX_REGEX.replace(s, "").trim()
        s = SUB_HEADER_REGEX.replace(s, "").trim()
        s = LEADING_NUM_REGEX.replace(s, "").trim()
        if (s.contains(".") && !s.contains(" ")) {
            s = s.replace(".", " ")
        }
        s = s.replace(Regex("""\s*[\(\[]\d{4}[\)\]]\s*$"""), "").trim()

        // Strip embedded/trailing season and episode patterns to guarantee unified aggregation
        s = SERIES_S_E_REGEX.matcher(s).replaceAll(" ").trim()
        s = SERIES_STAFFEL_REGEX.matcher(s).replaceAll(" ").trim()
        s = SERIES_SEASON_REGEX.matcher(s).replaceAll(" ").trim()
        s = SERIES_NUM_X_NUM_REGEX.matcher(s).replaceAll(" ").trim()
        s = SERIES_S_D_REGEX.matcher(s).replaceAll(" ").trim()
        s = SERIES_STAFFEL_NUM_REGEX.matcher(s).replaceAll(" ").trim()
        s = SERIES_EP_ONLY_REGEX.matcher(s).replaceAll(" ").trim()
        s = SERIES_SEASON_ONLY_REGEX.matcher(s).replaceAll(" ").trim()

        s = s.trimEnd('-', ':', '|', '/', '–', '—', '*', '•', '✪', '★', '(', '[', '{', '.', ',')
            .trimStart('-', ':', '|', '/', '–', '—', '*', '•', '✪', '★', ')', ']', '}', '.', ',')
            .trim()
        return s
    }

    fun canonicalSeriesTitle(title: String): String {
        return cleanSeriesTitle(title).lowercase().replace(Regex("""\s+"""), " ").trim()
    }

    fun extractEpisodeTitle(name: String, seasonNum: Int, episodeNum: Int): String {
        val clean = name.trim()
        val matchEnd = when {
            SERIES_S_E_REGEX.matcher(clean).find() -> {
                val m = SERIES_S_E_REGEX.matcher(clean)
                if (m.find()) m.end() else -1
            }
            SERIES_STAFFEL_REGEX.matcher(clean).find() -> {
                val m = SERIES_STAFFEL_REGEX.matcher(clean)
                if (m.find()) m.end() else -1
            }
            SERIES_SEASON_REGEX.matcher(clean).find() -> {
                val m = SERIES_SEASON_REGEX.matcher(clean)
                if (m.find()) m.end() else -1
            }
            SERIES_NUM_X_NUM_REGEX.matcher(clean).find() -> {
                val m = SERIES_NUM_X_NUM_REGEX.matcher(clean)
                if (m.find()) m.end() else -1
            }
            SERIES_EP_ONLY_REGEX.matcher(clean).find() -> {
                val m = SERIES_EP_ONLY_REGEX.matcher(clean)
                if (m.find()) m.end() else -1
            }
            else -> -1
        }

        if (matchEnd in 0 until clean.length) {
            val after = clean.substring(matchEnd).trim()
                .trimStart('-', ':', '|', '/', '–', '—', '*', '•', '✪', '★', ')', ']', '}', '.', ',')
                .trim()
            if (after.isNotBlank() && after.length > 1) {
                return after
            }
        }
        return "Episode $episodeNum"
    }

    /**
     * Checks if a group or explicit tag is unambiguously Austrian (AT / Austria / Österreich).
     * Never matches ambiguous station names like "ATV" across foreign categories.
     */
    fun isAtOrAustriaContent(name: String, group: String?): Boolean {
        val groupU = (group ?: "").trim().uppercase()

        // 1. If provider group/category is present, strictly decide by the group title (0% channel name interference)
        if (groupU.isNotBlank() && groupU != "ALL" && groupU != "ALLE") {
            return groupU.startsWith("AT:") || groupU.startsWith("AT :") || groupU.startsWith("AT|") ||
                groupU.startsWith("AT |") || groupU.startsWith("AT-") || groupU.startsWith("AT -") ||
                groupU.startsWith("AT*") || groupU.startsWith("AT *") || groupU.startsWith("[AT]") ||
                groupU.startsWith("(AT)") || groupU.startsWith("|AT|") || groupU.startsWith("┃AT┃") ||
                groupU.startsWith("AT ✪") || groupU.startsWith("AT✪") ||
                groupU.startsWith("AT ") || groupU.startsWith("AUT:") || groupU.startsWith("AUT ") ||
                groupU.startsWith("EU AUSTRIA") || groupU.startsWith("EU | AUSTRIA") || groupU.startsWith("EU-AUSTRIA") ||
                (groupU.contains("AUSTRIA") && !groupU.contains("AUSTRALIA")) ||
                groupU.contains("ÖSTERREICH") || groupU.contains("OESTERREICH") ||
                groupU == "AT" || groupU == "AUT"
        }

        // 2. Only when no group is provided, check explicit delimiter prefix tag on name
        val nameU = name.trim().uppercase()
        if (nameU.isEmpty()) return false
        return extractCountryCodeFromPrefix(nameU) == "AT"
    }

    /**
     * Checks if a group or explicit tag is unambiguously Swiss (CH / Switzerland / Schweiz).
     * Never matches ambiguous station names like "RTS 1", "TELE 1", "3+", or "S1" across foreign categories.
     */
    fun isChOrSwissContent(name: String, group: String?): Boolean {
        val groupU = (group ?: "").trim().uppercase()

        // 1. If provider group/category is present, strictly decide by the group title (0% channel name interference)
        if (groupU.isNotBlank() && groupU != "ALL" && groupU != "ALLE") {
            return groupU.startsWith("CH:") || groupU.startsWith("CH :") || groupU.startsWith("CH|") ||
                groupU.startsWith("CH |") || groupU.startsWith("CH-") || groupU.startsWith("CH -") ||
                groupU.startsWith("CH*") || groupU.startsWith("CH *") || groupU.startsWith("[CH]") ||
                groupU.startsWith("(CH)") || groupU.startsWith("|CH|") || groupU.startsWith("┃CH┃") ||
                groupU.startsWith("CH ✪") || groupU.startsWith("CH✪") ||
                groupU.startsWith("CH ") || groupU.startsWith("SUI:") || groupU.startsWith("SUI ") ||
                groupU.startsWith("EU SWITZERLAND") || groupU.contains("SCHWEIZ") ||
                groupU.contains("SWITZERLAND") || groupU.contains("SWISS") || groupU.contains("SUISSE") ||
                groupU.contains("SVIZZERA") || groupU == "CH" || groupU == "SUI"
        }

        // 2. Only when no group is provided, check explicit delimiter prefix tag on name
        val nameU = name.trim().uppercase()
        if (nameU.isEmpty()) return false
        return extractCountryCodeFromPrefix(nameU) == "CH"
    }

    /**
     * Checks if a group / playlist header explicitly represents an adult category.
     * Content marked with AT / Austria / Österreich is NEVER an adult group!
     */
    fun isAdultGroup(group: String?): Boolean {
        if (group.isNullOrBlank()) return false
        val g = group.trim().uppercase()
        if (g == "ALL" || g == "ALLE") return false

        // Content with Austrian markers belongs to AT: and is strictly NOT an adult group
        if (isAtOrAustriaContent("", g)) return false

        val prefix = extractCountryCodeFromPrefix(g)
        if (prefix == "ADULT") return true
        if (prefix in listOf("DE", "AT", "CH", "US", "UK", "TR", "IT", "FR", "ES", "NL", "PL")) {
            // Country category: only adult if explicitly marked with an adult subtag
            val hasAdultSubtag = g.contains("XXX") || g.contains("18+") || g.contains("+18") ||
                    g.contains("ADULT") || g.contains("EROTIK") || g.contains("EROTIC") ||
                    g.contains("HENTAI") || g.contains("PORN") || g.contains("REDLIGHT") ||
                    g.contains("X-RATED") || g.contains("XRATED")
            if (!hasAdultSubtag) {
                return false
            }
        }

        val adultGroupKeywords = listOf(
            "XXX", "ADULT", "18+", "+18", "EROTIK", "EROTIC", "EROTIQUE", "REDLIGHT", "RED LIGHT",
            "PORN", "PORNO", "PLAYBOY", "BRAZZERS", "HUSTLER", "DORCEL", "PASSION XXX", "PASSIONXXX",
            "PENTHOUSE", "VIVID", "PRIVATE TV", "CENTO X CENTO", "PINK O",
            "SEXTOSENSO", "HARDCORE", "EVIL ANGEL", "REALITY KINGS", "MOFOS", "BLUE HUSTLER",
            "BARELY LEGAL", "BANG BROS", "CENTOXCENTO", "TELECLAP", "EROTICA", "X-RATED",
            "XRATED", "HENTAI", "SWINGERS", "FETISH", "BDSM", "ADULTOS", "ADULTE", "FOR ADULTS"
        )
        return adultGroupKeywords.any { g.contains(it) }
    }

    /**
     * Checks if content is Adult / 18+ / Erotik.
     * Categorizes strictly by the header / groupTitle, NOT by the channel or film title.
     */
    fun isAdultContent(name: String, group: String?): Boolean {
        if (isAtOrAustriaContent("", group)) {
            return false
        }

        val groupUpper = (group ?: "").trim().uppercase()
        if (groupUpper.isNotBlank() && groupUpper != "ALL" && groupUpper != "ALLE") {
            return isAdultGroup(groupUpper)
        }

        val nameUpper = name.trim().uppercase()
        val prefix = extractCountryCodeFromPrefix(nameUpper)
        if (prefix == "ADULT") return true
        if (prefix != null && prefix != "INT" && prefix != "OTHER") return false

        return nameUpper.startsWith("XXX") || nameUpper.startsWith("18+") || nameUpper.startsWith("+18")
    }

    private val LEADING_TIER_PREFIX_REGEX = Pattern.compile(
        """^(?:(?:VOD|SERIES|SERIEN|SRS|MOVIES|FILME|LIVE|TV|VIP|RAW|HEVC|4K|UHD|FHD|HD|\d{1,3})\s*[:\|\┃\║\-\–\—\*•✪★☆~>\/\\]\s*)+"""
    )

    fun normalizeRawCountryCode(raw: String): String? {
        val c = raw.trim().uppercase()
        return when (c) {
            "DE", "GER", "DEU", "GERMANY", "DEUTSCHLAND", "GERMAN", "DEUTSCH", "DEUTSCHE" -> "DE"
            "AT", "AUT", "AUSTRIA", "ÖSTERREICH", "OESTERREICH" -> "AT"
            "CH", "SUI", "SWISS", "SCHWEIZ", "SWITZERLAND" -> "CH"
            "XXX", "ADULT", "ADULTS", "18+", "+18", "PORN", "PORNO", "EROTIC", "EROTIK", "REDLIGHT", "FOR ADULTS" -> "ADULT"
            "GB", "UK", "ENG", "EN", "ENGLAND", "BRITISH", "UNITED KINGDOM", "ENGLISH" -> "UK"
            "US", "USA", "UNITED STATES" -> "US"
            "TR", "TUR", "TURKEY", "TÜRKIYE", "TURKISH" -> "TR"
            "IT", "ITA", "ITALY", "ITALIA", "ITALIAN" -> "IT"
            "FR", "FRA", "FRANCE", "FRANCAIS", "FRENCH" -> "FR"
            "ES", "ESP", "SPAIN", "ESPANA", "ESPAÑA", "SPANISH" -> "ES"
            "NL", "NED", "NLD", "NETHERLANDS", "NEDERLAND", "DUTCH" -> "NL"
            "PL", "POL", "POLAND", "POLSKA", "POLISH" -> "PL"
            "EXYU", "EX-YU", "YU", "SRB", "CRO", "BIH", "MK", "SLO", "MNE", "BALKAN", "HRVATSKA", "SRBIJA", "BOSNA" -> "EXYU"
            "ALB", "AL", "ALBANIA", "SHQIP" -> "ALB"
            "GR", "GRE", "CY", "GREECE", "GREEK", "CYPRUS" -> "GR"
            "BE", "BEL", "BELGIUM", "BELGIQUE" -> "BE"
            "RO", "ROU", "ROMANIA", "ROMANIAN" -> "RO"
            "PT", "POR", "PORTUGAL", "BR", "BRA", "BRAZIL", "BRASIL" -> "PT"
            "SE", "SWE", "SWEDEN", "NO", "NOR", "NORWAY", "DK", "DEN", "DENMARK", "FI", "FIN", "FINLAND" -> "SE"
            "AR", "ARA", "ARABIC", "ARAB" -> "AR"
            "RU", "RUS", "RUSSIA", "RUSSIAN" -> "RU"
            "CZ", "CZE", "CZECH", "SK", "SVK", "SLOVAKIA" -> "CZ"
            "BG", "BGR", "BULGARIA", "BULGARIAN" -> "BG"
            "IN", "IND", "INDIA", "HINDI" -> "IN"
            "CA", "CAN", "CANADA" -> "CA"
            "AF", "AFG", "AFGHANISTAN" -> "AF"
            else -> null
        }
    }

    fun extractCountryCodeFromPrefix(text: String): String? {
        val rawUpper = text.trim().uppercase()
        if (rawUpper.isEmpty()) return null
        val cached = prefixCountryCache.get(rawUpper)
        if (cached != null) return if (cached.isEmpty()) null else cached

        // Strip leading tier/type prefixes like "VOD | DE | Horror", "SRS - DE - Drama", "VIP | DE | Kino", "01 | DE | Action"
        val strippedMatcher = LEADING_TIER_PREFIX_REGEX.matcher(rawUpper)
        val t = if (strippedMatcher.find()) {
            rawUpper.substring(strippedMatcher.end()).trim().ifEmpty { rawUpper }
        } else {
            rawUpper
        }

        // Fast-path: Explicit AT / Austria / Österreich prefixes (e.g. "AT  Austria", "AT Austria", "AT: ORF 1", "AUSTRIA")
        if (t.startsWith("AT:") || t.startsWith("AT :") || t.startsWith("AT|") || t.startsWith("AT |") ||
            t.startsWith("AT-") || t.startsWith("AT -") || t.startsWith("AT*") || t.startsWith("AT *") ||
            t.startsWith("AT ✪") || t.startsWith("AT✪") || t.startsWith("AT ") || t.startsWith("AUT:") ||
            t.startsWith("AUT ") || t.startsWith("EU AUSTRIA") || t.startsWith("EU | AUSTRIA") ||
            t.startsWith("AUSTRIA") || t.startsWith("ÖSTERREICH") || t.startsWith("OESTERREICH") ||
            t == "AT"
        ) {
            prefixCountryCache.put(rawUpper, "AT")
            return "AT"
        }

        // Fast-path: Explicit DE / Germany prefixes
        if (t.startsWith("DE:") || t.startsWith("DE :") || t.startsWith("DE|") || t.startsWith("DE |") ||
            t.startsWith("DE-") || t.startsWith("DE -") || t.startsWith("DE*") || t.startsWith("DE *") ||
            t.startsWith("DE ✪") || t.startsWith("DE✪") || t.startsWith("DE ") || t.startsWith("GER:") ||
            t.startsWith("GER ") || t.startsWith("EU DE") || t.startsWith("EU | DE") ||
            t.startsWith("DEUTSCHLAND") || t.startsWith("GERMANY") || t == "DE"
        ) {
            prefixCountryCache.put(rawUpper, "DE")
            return "DE"
        }

        // Fast-path: Explicit CH / Switzerland prefixes
        if (t.startsWith("CH:") || t.startsWith("CH :") || t.startsWith("CH|") || t.startsWith("CH |") ||
            t.startsWith("CH-") || t.startsWith("CH -") || t.startsWith("CH*") || t.startsWith("CH *") ||
            t.startsWith("CH ✪") || t.startsWith("CH✪") || t.startsWith("CH ") || t.startsWith("SUI:") ||
            t.startsWith("SUI ") || t.startsWith("EU SWITZERLAND") || t.startsWith("SCHWEIZ") ||
            t.startsWith("SWITZERLAND") || t.startsWith("SWISS") || t == "CH"
        ) {
            prefixCountryCache.put(rawUpper, "CH")
            return "CH"
        }

        // Fast-path: Explicit US & UK prefixes
        if (t.startsWith("US:") || t.startsWith("US :") || t.startsWith("US|") || t.startsWith("US |") ||
            t.startsWith("US-") || t.startsWith("US -") || t.startsWith("US*") || t.startsWith("US *") ||
            t.startsWith("US ") || t.startsWith("USA") || t.startsWith("UNITED STATES") || t == "US"
        ) {
            prefixCountryCache.put(rawUpper, "US")
            return "US"
        }
        if (t.startsWith("UK:") || t.startsWith("UK :") || t.startsWith("UK|") || t.startsWith("UK |") ||
            t.startsWith("UK-") || t.startsWith("UK -") || t.startsWith("UK*") || t.startsWith("UK *") ||
            t.startsWith("UK ") || t.startsWith("GB:") || t.startsWith("GB ") || t.startsWith("UNITED KINGDOM") ||
            t == "UK" || t == "GB"
        ) {
            prefixCountryCache.put(rawUpper, "UK")
            return "UK"
        }

        // 1. Tag followed by delimiter: *, :, |, -, –, —, •, ✪, ★, ☆, ~, >, /, \
        // e.g. "DE * Kino", "AT * Austria", "CH * Swiss", "ES * Hentai", "XXX * For  Adults"
        val m1 = PREFIX_DELIMITER_REGEX.matcher(t)
        if (m1.find()) {
            val norm = normalizeRawCountryCode(m1.group(1))
            if (norm != null) {
                prefixCountryCache.put(rawUpper, norm)
                return norm
            }
        }
        // 2. Bracketed tag: [DE], (DE), 【DE】, |DE|, ┃DE┃, ║DE║
        val m2 = BRACKET_TAG_REGEX.matcher(t)
        if (m2.find()) {
            val rawTag = m2.group(1) ?: m2.group(2) ?: m2.group(3) ?: m2.group(4)
            val norm = if (rawTag != null) normalizeRawCountryCode(rawTag) else null
            if (norm != null) {
                prefixCountryCache.put(rawUpper, norm)
                return norm
            }
        }
        // 3. EU prefix with optional delimiter: EU DE, EU | DE, EU-DE, EU * DE, EU AUSTRIA
        val m3 = EU_PREFIX_REGEX.matcher(t)
        if (m3.find()) {
            val norm = normalizeRawCountryCode(m3.group(1))
            if (norm != null) {
                prefixCountryCache.put(rawUpper, norm)
                return norm
            }
        }
        // 4. Spelled-out country or adult name followed by delimiter
        val m4 = SPELLED_COUNTRY_REGEX.matcher(t)
        if (m4.find()) {
            val norm = normalizeRawCountryCode(m4.group(1))
            if (norm != null) {
                prefixCountryCache.put(rawUpper, norm)
                return norm
            }
        }
        prefixCountryCache.put(rawUpper, "")
        return null
    }

    /**
     * Accurately detects the country of a channel or movie without false positive German classifications.
     * Returns Pair(isDach, countryCode).
     */
    fun detectCountryAndDach(name: String, group: String?, tvgCountry: String? = null): Pair<Boolean, String> {
        val groupUpper = (group ?: "").uppercase().trim()

        // 1. Authoritative Provider Category (groupTitle)
        if (groupUpper.isNotEmpty() && groupUpper != "ALL" && groupUpper != "ALLE") {
            val groupPrefix = extractCountryCodeFromPrefix(groupUpper)
            if (groupPrefix != null) {
                val isAdult = isAdultGroup(groupUpper)
                val finalCode = if (isAdult && groupPrefix !in listOf("ES", "DE", "AT", "CH", "US", "UK")) "ADULT" else groupPrefix
                return Pair(finalCode in listOf("DE", "AT", "CH") && !isAdult, finalCode)
            }

            if (isAdultGroup(groupUpper)) {
                return Pair(false, "ADULT")
            }

            if (isAtOrAustriaContent("", groupUpper)) {
                return Pair(true, "AT")
            }
            if (isChOrSwissContent("", groupUpper)) {
                return Pair(true, "CH")
            }

            val explicitCatCountry = com.example.data.country.CountryNormalizer.extractCountryFromCategory(groupUpper)
            if (explicitCatCountry != null && explicitCatCountry !in listOf("SPORT", "OTHER", "UNKNOWN")) {
                return Pair(explicitCatCountry in listOf("DE", "AT", "CH"), explicitCatCountry)
            }

            val groupCountry = detectCountryForGroup(groupUpper)
            if (groupCountry !in listOf("OTHER", "SPORT", "VIP", "UNKNOWN")) {
                return Pair(groupCountry in listOf("DE", "AT", "CH"), groupCountry)
            }
        }

        // 2. Explicit TVG Country attribute
        if (!tvgCountry.isNullOrBlank()) {
            val norm = normalizeRawCountryCode(tvgCountry)
            if (norm != null && norm !in listOf("INT", "OTHER", "UNKNOWN", "SPORT")) {
                return Pair(norm in listOf("DE", "AT", "CH"), norm)
            }
        }

        return Pair(false, "INT")
    }

    fun isSportContent(name: String, group: String?): Boolean {
        val upper = "$name ${group ?: ""}".uppercase().trim()
        val sportKeywords = listOf(
            "SPORT", "BUNDESLIGA", "DAZN", "SKY SPORT", "MAGENTASPORT", "MAGENTA SPORT",
            "EUROSPORT", "SPORT1", "SPORT 1", "SPORTDIGITAL", "FUSSBALL", "FOOTBALL",
            "SOCCER", "BASKETBALL", "FORMEL 1", "FORMULA 1", "FORMEL1", "F1 ", "F1:", "F1-",
            "MOTOGP", "MOTO GP", "WWE", "UFC", "BOXEN", "BOXING", "NBA", "NFL", "NHL", "MLB", "TENNIS",
            "GOLF", "DARTS", "RACING", "EXTREME SPORT", "MYSPORTS", "BLUE SPORT",
            "ORF SPORT", "ARENA SPORT", "BEIN SPORT", "BEIN SPORTS", "SUPER SPORT",
            "ELEVEN SPORT", "ELEVEN SPORTS", "SETANTA", "MATCH TV", "TNT SPORTS",
            "PREMIER SPORTS", "CANAL+ SPORT", "RMC SPORT", "ZIGGO SPORT", "VIAPLAY",
            "DYN SPORT", "DYN ", "AUTO MOTOR SPORT", "MOTORVISION", "FIGHTBOX",
            "FIGHT SPORTS", "RED BULL TV", "SUPERBIKE", "NASCAR", "INDYCAR", "ESPN",
            "CHAMPIONS LEAGUE", "PREMIER LEAGUE", "LA LIGA", "SERIE A", "LIGUE 1",
            "SKY BUNDESLIGA", "SKY SELECT EVENT", "SKY SPORT AUSTRIA", "SKY SPORT F1",
            "SKY SPORT PREMIER LEAGUE", "SKY SPORT TOP EVENT", "SKY SPORT MIX",
            "SKY SPORT TENNIS", "SKY SPORT GOLF", "FC BAYERN TV", "REAL MADRID TV",
            "BARCA TV", "LIVERPOOL TV", "MUTV", "CHELSEA TV", "KAMPFSPORT", "WRESTLING",
            "SNOOKER", "E-SPORTS", "ESPORTS", "POLO TV", "EQUESTRIAN", "HORSE RACING",
            // German DEL / Eishockey / Frauen sport
            "DEL EVENTS", "DEL EVENT", "DEL 1", "DEL 2", "DEL:", "DEL|", "DEL -",
            "DEUTSCHE EISHOCKEY LIGA", "EISHOCKEY", "ICE HOCKEY",
            "FRAUEN:", "FRAUEN|", "FRAUEN -", "FRAUEN-BUNDESLIGA", "FRAUEN BUNDESLIGA",
            "FRAUEN SPORT", "FRAUEN FUSSBALL", "WOMEN SPORT", "W-SPORT", "WSL",
            "GOL PLAY", "SUPERSPORT", "SPORT TV", "DIEMA SPORT", "MAX SPORT", "STAN SPORT",
            "POLSAT SPORT", "TVP SPORT", "CANAL+ LIGUE", "LALIGA TV", "RACING TV",
            "TOUR DE FRANCE", "RALLY", "WRC", "RALLYE", "CRICKET", "RUGBY"
        )
        return sportKeywords.any { upper.contains(it) }
    }

    fun formatAdultGroupTitle(group: String?, isMovie: Boolean): String {
        val raw = (group ?: "").trim()
        if (raw.isNotBlank() && !raw.equals("All", ignoreCase = true) && !raw.equals("Alle", ignoreCase = true)) {
            return raw
        }
        return if (isMovie) "ADULT | Filme" else "ADULT | Live TV"
    }

    fun resolveMovieGroupTitle(
        name: String,
        group: String?,
        countryCode: String,
        isDach: Boolean
    ): String {
        val raw = group?.trim() ?: ""
        if (raw.isNotBlank() && !raw.equals("All", ignoreCase = true) && !raw.equals("Alle", ignoreCase = true)) {
            return raw
        }
        if (isAdultContent(name, group) || countryCode == "ADULT") {
            return "ADULT | Filme"
        }
        return if (isDach) "DEUTSCH | Filme" else "International | Filme"
    }

    fun resolveLiveGroupTitle(
        name: String,
        group: String?,
        countryCode: String,
        isDach: Boolean
    ): String {
        val raw = group?.trim() ?: ""
        if (raw.isNotBlank() && !raw.equals("All", ignoreCase = true) && !raw.equals("Alle", ignoreCase = true)) {
            return raw
        }
        if (isAdultContent(name, group) || countryCode == "ADULT") {
            return "ADULT | Live TV"
        }
        val nameUpper = name.uppercase()
        return when {
            isSportContent(name, group) -> if (isDach) "DEUTSCHLAND | Sport" else "SPORT HD"
            countryCode == "DE" -> "DEUTSCHLAND"
            countryCode == "AT" -> "ÖSTERREICH"
            countryCode == "CH" -> "SCHWEIZ"
            countryCode == "EXYU" -> "EX-YU"
            isDach -> "DEUTSCHLAND"
            else -> "INTERNATIONAL"
        }
    }

    /**
     * Strips prefixes like "DE: ", "DE | ", "EN: ", "1- ", leading digits, emojis,
     * so titles can be sorted cleanly alphabetically.
     */
    fun cleanSortTitle(name: String): String {
        val cached = cleanSortTitleCache.get(name)
        if (cached != null) return cached

        var n = name.trim()
        if (n.startsWith("🔞")) n = n.removePrefix("🔞").trim()
        if (n.startsWith("🇩🇪") || n.startsWith("🇦🇹") || n.startsWith("🇨🇭") || n.startsWith("🇬🇧") || n.startsWith("🇺🇸") || n.startsWith("🇪🇸")) {
            if (n.length >= 2) n = n.substring(2).trim()
        }

        // Strip header prefixes like "DE * ", "AT * ", "CH * ", "ES * ", "XXX * ", "DE: ", "[DE] ", etc.
        val match = CLEAN_PREFIX_REGEX.find(n)
        if (match != null) {
            val stripped = n.substring(match.range.last + 1).trim()
            if (stripped.isNotBlank()) {
                n = stripped
            }
        }

        // Strip sub-header category prefixes like "Kino | Inception" or "Kino - Inception"
        n = SUB_HEADER_REGEX.replace(n, "").trim()

        n = LEADING_NUM_REGEX.replace(n, "").trim()
        val result = n.lowercase()
        cleanSortTitleCache.put(name, result)
        return result
    }

    /**
     * VOD Language Hierarchy:
     * 1 = Deutschsprachig (DE / AT / CH / German / Deutsch) -> Ganz oben!
     * 2 = Englischsprachig (EN / UK / US / English / Englisch) -> Danach!
     * 3 = Der Rest (Sonstige Sprachen: Französisch, Spanisch, Italienisch, Türkisch, etc.)
     * 4 = Adult / 18+ -> Ganz am Ende
     */
    fun getVodLanguageRank(name: String, group: String?, countryCode: String, isDach: Boolean): Int {
        val upperName = name.uppercase().trim()
        val upperGroup = (group ?: "").uppercase().trim()
        val upperCountry = countryCode.uppercase().trim()
        val combined = "$upperName $upperGroup"

        // 0. Austrian / AT content is DACH (Rank 1), NEVER Adult!
        if (isAtOrAustriaContent(name, group) || upperCountry == "AT") {
            return 1
        }

        // Adult content isolated at the very bottom
        if (upperCountry == "ADULT" || isAdultContent(name, group) ||
            upperGroup.contains("ADULT") || upperGroup.contains("XXX") || upperGroup.contains("18+")
        ) {
            return 4
        }

        // Fast Header Prefix check (e.g. "DE * Kino", "AT * Austria", "CH * Swiss")
        val groupPrefix = extractCountryCodeFromPrefix(upperGroup)
        val namePrefix = extractCountryCodeFromPrefix(upperName)
        if (groupPrefix in listOf("DE", "AT", "CH") || namePrefix in listOf("DE", "AT", "CH")) {
            return 1
        }
        if (groupPrefix in listOf("UK", "US", "GB", "CA", "AU") || namePrefix in listOf("UK", "US", "GB", "CA", "AU")) {
            return 2
        }

        // 1. Deutschsprachig
        val nonDeCountries = listOf("UK", "US", "GB", "CA", "FR", "ES", "IT", "TR", "EXYU", "PL", "NL", "AL", "AR", "RU", "PT", "BR", "GR", "RO", "SE")
        val isExplicitGerman = upperName.startsWith("DE:") || upperName.startsWith("DE :") || upperName.startsWith("DE|") ||
            upperName.startsWith("DE |") || upperName.startsWith("DE -") || upperName.startsWith("DE *") || upperName.startsWith("[DE]") || upperName.startsWith("(DE)") ||
            upperName.startsWith("GER:") || upperName.startsWith("GER :") || upperName.startsWith("GER|") ||
            upperName.startsWith("AT:") || upperName.startsWith("AT :") || upperName.startsWith("AT|") || upperName.startsWith("AT *") ||
            upperName.startsWith("CH:") || upperName.startsWith("CH :") || upperName.startsWith("CH|") || upperName.startsWith("CH *") ||
            upperGroup.startsWith("DE:") || upperGroup.startsWith("DE :") || upperGroup.startsWith("DE|") ||
            upperGroup.startsWith("DE |") || upperGroup.startsWith("DE -") || upperGroup.startsWith("DE *") || upperGroup.startsWith("[DE]") || upperGroup.startsWith("(DE)") ||
            upperGroup.startsWith("GER:") || upperGroup.startsWith("GER :") || upperGroup.startsWith("GER|") ||
            upperGroup.startsWith("AT:") || upperGroup.startsWith("AT :") || upperGroup.startsWith("AT *") || upperGroup.startsWith("CH:") || upperGroup.startsWith("CH :") || upperGroup.startsWith("CH *") ||
            upperGroup.contains("DEUTSCH") || upperGroup.contains("GERMAN") || upperGroup.contains("DEUTSCHLAND") ||
            upperGroup.contains("ÖSTERREICH") || upperGroup.contains("SCHWEIZ") || upperGroup.contains("VOD DE") ||
            upperGroup.contains("FILME DE") || upperGroup.contains("SERIEN DE") || upperGroup.contains("CINEMA DE") ||
            combined.contains(" GERMAN ") || combined.endsWith(" GERMAN") || combined.contains(".GERMAN.") ||
            combined.contains(" DEUTSCH ") || combined.endsWith(" DEUTSCH") || combined.contains(".DL.") || combined.contains(" DUAL ")

        if ((isExplicitGerman || isDach || upperCountry in listOf("DE", "AT", "CH")) && upperCountry !in nonDeCountries) {
            return 1
        }

        // 2. Englischsprachig
        val isExplicitEnglish = upperCountry in listOf("UK", "US", "GB", "CA", "AU") ||
            upperName.startsWith("EN:") || upperName.startsWith("EN :") || upperName.startsWith("EN|") ||
            upperName.startsWith("EN |") || upperName.startsWith("EN -") || upperName.startsWith("EN *") || upperName.startsWith("[EN]") || upperName.startsWith("(EN)") ||
            upperName.startsWith("ENG:") || upperName.startsWith("ENG :") || upperName.startsWith("ENG|") || upperName.startsWith("ENG |") ||
            upperName.startsWith("US:") || upperName.startsWith("US :") || upperName.startsWith("US|") || upperName.startsWith("US |") || upperName.startsWith("US *") ||
            upperName.startsWith("UK:") || upperName.startsWith("UK :") || upperName.startsWith("UK|") || upperName.startsWith("UK |") || upperName.startsWith("UK *") ||
            upperGroup.startsWith("EN:") || upperGroup.startsWith("EN :") || upperGroup.startsWith("EN|") || upperGroup.startsWith("EN |") || upperGroup.startsWith("EN *") ||
            upperGroup.startsWith("ENG:") || upperGroup.startsWith("ENG :") || upperGroup.startsWith("ENG|") || upperGroup.startsWith("ENG |") ||
            upperGroup.startsWith("US:") || upperGroup.startsWith("US :") || upperGroup.startsWith("US|") || upperGroup.startsWith("US |") || upperGroup.startsWith("US *") ||
            upperGroup.startsWith("UK:") || upperGroup.startsWith("UK :") || upperGroup.startsWith("UK|") || upperGroup.startsWith("UK |") || upperGroup.startsWith("UK *") ||
            upperGroup.contains("ENGLISH") || upperGroup.contains("ENGLISCH") || upperGroup.contains("VOD EN") ||
            upperGroup.contains("MOVIES EN") || upperGroup.contains("SERIES EN") || upperGroup.contains("CINEMA EN") ||
            upperGroup.contains("VOD US") || upperGroup.contains("VOD UK") ||
            combined.contains(" ENGLISH ") || combined.endsWith(" ENGLISH") || combined.contains(".ENGLISH.")

        if (isExplicitEnglish) {
            return 2
        }

        // 3. Der Rest
        return 3
    }

    /**
     * Determines whether a channel or VOD item is German/DACH (DE/AT/CH) content.
     * When the app language is set to German, international content (non-German TV, foreign movies & series)
     * can be skipped during playlist synchronization for maximum database and UI performance.
     */
    fun isGermanOrDachContent(
        name: String,
        group: String?,
        countryCode: String,
        isDach: Boolean,
        contentType: com.example.data.model.ContentType = com.example.data.model.ContentType.LIVE_TV
    ): Boolean {
        // Adult content is explicitly preserved and kept accessible alongside DACH content
        if (countryCode.equals("ADULT", ignoreCase = true) || isAdultContent(name, group)) {
            return true
        }
        if (contentType == com.example.data.model.ContentType.MOVIE || contentType == com.example.data.model.ContentType.SERIES) {
            val rank = getVodLanguageRank(name, group, countryCode, isDach)
            return rank == 1
        }
        if (isDach || countryCode.uppercase() in listOf("DE", "AT", "CH")) {
            return true
        }
        val nonDeCountries = listOf("UK", "US", "GB", "CA", "FR", "ES", "IT", "TR", "EXYU", "PL", "NL", "AL", "AR", "RU", "PT", "BR", "GR", "RO", "SE")
        if (countryCode.uppercase() in nonDeCountries) {
            return false
        }
        val upperGroup = (group ?: "").uppercase().trim()
        val upperName = name.uppercase().trim()

        val groupPrefix = extractCountryCodeFromPrefix(upperGroup)
        if (groupPrefix in listOf("DE", "AT", "CH")) return true
        if (groupPrefix != null && groupPrefix in nonDeCountries) return false

        val namePrefix = extractCountryCodeFromPrefix(upperName)
        if (namePrefix in listOf("DE", "AT", "CH")) return true
        if (namePrefix != null && namePrefix in nonDeCountries) return false

        if (upperGroup.startsWith("DE:") || upperGroup.startsWith("DE|") || upperGroup.startsWith("DE ") || upperGroup.startsWith("DE *") ||
            upperGroup.startsWith("GER:") || upperGroup.startsWith("AT:") || upperGroup.startsWith("AT *") || upperGroup.startsWith("CH:") || upperGroup.startsWith("CH *") ||
            upperGroup.contains("DEUTSCH") || upperGroup.contains("GERMAN") ||
            upperGroup.contains("ÖSTERREICH") || upperGroup.contains("SCHWEIZ") ||
            upperName.startsWith("DE:") || upperName.startsWith("DE|") || upperName.startsWith("GER:") || upperName.startsWith("DE *") ||
            upperName.startsWith("[DE]") || upperName.startsWith("(DE)")
        ) {
            return true
        }
        return false
    }

    fun isGermanOrDachContent(channel: com.example.data.model.ChannelItem): Boolean {
        return isGermanOrDachContent(
            name = channel.name,
            group = channel.groupTitle,
            countryCode = channel.countryCode,
            isDach = channel.isDach,
            contentType = channel.type
        )
    }

    /**
     * Strict Live TV Channel Ordering:
     * 100-160: Öffentlich-Rechtliche (Das Erste, ZDF, Dritte Programme, 3sat, Arte, KiKa, Phoenix, tagesschau24, ONE, ZDFneo/info, ORF, SRF)
     * 200-224: Große deutsche Privatsender / Free-TV (RTL, Sat.1, ProSieben, VOX, Kabel Eins, Tele 5, DMAX...)
     * 300-328: Sky & Pay-TV (Sky Cinema, Sky Atlantic, Sky One, 13th Street, SYFY... OHNE Sport!)
     * 400-450: Sonstige reguläre Sender & Dokus (DACH 400, International 450)
     * 500: 24/7 Streams
     * 600-690: Sportkanäle (Sky Sport, Sky Bundesliga, DAZN, Magenta, Eurosport, Sport1... GANZ AM ENDE!)
     * 900: Adult / 18+ Kanäle
     */
    fun getLiveTvSortRank(name: String, group: String?, countryCode: String, isDach: Boolean): Int {
        val cacheKey = "$name|${group ?: ""}|$countryCode|$isDach"
        liveTvSortRankCache.get(cacheKey)?.let { return it }
        val computed = computeLiveTvSortRank(name, group, countryCode, isDach)
        liveTvSortRankCache.put(cacheKey, computed)
        return computed
    }

    private fun computeLiveTvSortRank(name: String, group: String?, countryCode: String, isDach: Boolean): Int {
        val upperName = name.uppercase().trim()
        val upperGroup = (group ?: "").uppercase().trim()
        val upperCountry = countryCode.uppercase().trim()

        // 0. Adult content isolated
        if (upperCountry == "ADULT" || isAdultContent(name, group) ||
            upperGroup.contains("ADULT") || upperGroup.contains("XXX") || upperGroup.contains("18+")
        ) {
            return 900
        }

        // 1. Sportkanäle - User requirement: "die Sportkanaele sollen unter ganz am Ende einsortiert sein"
        if (isSportContent(name, group)) {
            val core = cleanSortTitle(name).uppercase()
            return when {
                core.contains("BUNDESLIGA") || upperGroup.contains("BUNDESLIGA") -> 600
                core.contains("SKY SPORT AUSTRIA") -> 605
                core.contains("SKY SPORT") || upperGroup.contains("SKY SPORT") -> 610
                core.contains("DAZN") || upperGroup.contains("DAZN") -> 620
                core.contains("MAGENTA") || upperGroup.contains("MAGENTA") -> 630
                core.contains("EUROSPORT") || upperGroup.contains("EUROSPORT") -> 640
                core.contains("SPORT1") || core.contains("SPORT 1") -> 650
                core.contains("SPORTDIGITAL") || core.contains("DYN") -> 660
                core.contains("BLUE SPORT") || core.contains("BLUE ZOOM") -> 665
                core.contains("ORF SPORT") || core.contains("MYSPORTS") -> 670
                else -> 680
            }
        }

        // 2. Non-linear theme loops, VOD/series boxes, 24/7 channels, or thematic web doc streams
        if (isNonLinearOrThematicLoop(name, group)) {
            return 750
        }

        val core = cleanSortTitle(name).uppercase()
        val isExplicitAt = upperCountry == "AT" || core.contains("AUSTRIA") || core.contains("ÖSTERREICH") || core.contains("OESTERREICH")
        val isExplicitCh = upperCountry == "CH" || core.contains("SCHWEIZ") || core.contains("SWISS") || core.endsWith(" CH") || core.contains(" CH ")
        val isDachStation = isDach || upperCountry in listOf("DE", "AT", "CH") ||
            upperGroup.startsWith("DE") || upperGroup.startsWith("AT") || upperGroup.startsWith("CH") ||
            upperGroup.contains("DEUTSCH") || upperGroup.contains("GERMAN") ||
            upperGroup.contains("AUSTRIA") || upperGroup.contains("SWISS") ||
            upperName.startsWith("DE:") || upperName.startsWith("DE|") || upperName.startsWith("DE -") ||
            upperName.startsWith("DE ✪") || upperName.startsWith("DE✪") ||
            com.example.data.country.DachMasterDatabase.findMatch(name).isMatch

        // 3. Öffentlich-Rechtliche (ÖRR) - User requirement: "die oeffentlich rechtlichen zuerst"
        // a) Hauptsender ARD & ZDF (DE)
        if (isDachStation && (core.contains("DAS ERSTE") || core == "ARD" || core.startsWith("ARD ") ||
            core.contains("ARD HD") || core.contains("ERSTES DEUTSCHES"))
        ) {
            return 100
        }
        if (isDachStation && ((core.contains("ZDF") && !core.contains("ZDFINFO") && !core.contains("ZDF INFO") &&
             !core.contains("ZDFNEO") && !core.contains("ZDF NEO")) || core.contains("ZWEITES DEUTSCHES"))
        ) {
            return 102
        }

        // Österreich ÖRR (ORF 1, 2, III, KIDS) + native Österreich Privatsender vor DE-Werbefenstern
        if (core.contains("ORF 1") || core.contains("ORF1") || core.contains("ORF EINS")) return 100
        if (core.contains("ORF 2") || core.contains("ORF2")) return 102
        if (core.contains("ORF 3") || core.contains("ORF3") || core.contains("ORF III")) return 104
        if (core.contains("ORF KIDS")) return 105

        if (isDachStation && (core == "ATV" || core.startsWith("ATV HD") || core.startsWith("ATV FHD") || core.contains("ATV AUSTRIA") || core.contains("ATV ÖSTERREICH"))) return 125
        if (isDachStation && (core.contains("PULS 4") || core.contains("PULS4"))) return 126
        if (isDachStation && core.contains("SERVUS") && (isExplicitAt || !core.contains("DEUTSCHLAND"))) return 127
        if (isDachStation && (core.contains("ATV 2") || core.contains("ATV2") || core.contains("ATV II"))) return 128
        if (isDachStation && (core.contains("PULS 24") || core.contains("PULS24"))) return 129
        if (isDachStation && (core.contains("OE24") || core.contains("OE 24"))) return 130
        if (isDachStation && (core.contains("KRONE TV") || core.contains("KRONE.TV") || core.contains("KRONETV"))) return 131

        // Schweiz ÖRR (SRF 1, 2, info, RTS, RSI) + native Schweizer Privatsender vor DE-Werbefenstern
        if (core.contains("SRF 1") || core.contains("SRF1") || core.contains("SF 1")) return 100
        if (core.contains("SRF 2") || core.contains("SRF2") || core.contains("SRF ZWEI") || core.contains("SF 2")) return 102
        if (core.contains("SRF INFO")) return 104
        if (core.contains("RTS 1") || core.contains("RTS UN")) return 106
        if (core.contains("RTS 2") || core.contains("RTS DEUX")) return 108
        if (core.contains("RSI LA 1") || core.contains("RSI LA1")) return 109
        if (core.contains("RSI LA 2") || core.contains("RSI LA2")) return 110

        if (isDachStation && (core.contains("3+") || core.contains("3 PLUS") || core.contains("3PLUS"))) return 122
        if (isDachStation && (core.contains("4+") || core.contains("4 PLUS") || core.contains("4PLUS"))) return 123
        if (isDachStation && (core.contains("TV24") || core.contains("TV 24"))) return 124
        if (isDachStation && (core.contains("5+") || core.contains("5 PLUS") || core.contains("5PLUS"))) return 125
        if (isDachStation && (core.contains("TV25") || core.contains("TV 25"))) return 126
        if (isDachStation && (core.contains("6+") || core.contains("6 PLUS") || core.contains("6PLUS"))) return 127
        if (isDachStation && (core.contains("7+") || core.contains("7 PLUS") || core.contains("7PLUS"))) return 128
        if (isDachStation && (core.contains("PULS 8") || core.contains("PULS8"))) return 129
        if (isDachStation && (core == "S1" || core.startsWith("S1 ") || core.contains("S1 TV") || core.contains("SWISS 1") || core.contains("SWISS1"))) return 130
        if (isDachStation && (core.contains("TELEZÜRI") || core.contains("TELEZURI") || core.contains("TELE ZÜRI") || core.contains("TELEBÄRN") || core.contains("TELEBASEL") || core.contains("TELE M1") || core.contains("TELE 1") || core.contains("TELEOSTSCHWEIZ") || core.contains("TVO"))) return 131

        // b) Dritte Programme (ARD Regional)
        if (isDachStation && (core == "WDR" || core.startsWith("WDR ") || core.startsWith("WDR-") || core.contains("WDR FERNSEHEN") || core.contains("WDR HD") || core.contains("WDR FHD"))) return 110
        if (isDachStation && (core == "NDR" || core.startsWith("NDR ") || core.startsWith("NDR-") || core.contains("NDR FERNSEHEN") || core.contains("NDR HD") || core.contains("NDR FHD") || core.contains("NDR INFO"))) return 111
        if (isDachStation && (core == "BR" || core == "BR HD" || core.startsWith("BR ") || core.startsWith("BR-") || core.contains("BR FERNSEHEN") || core.contains("BAYERISCHES") || core.contains("BAYERN"))) return 112
        if (isDachStation && (core == "SWR" || core.startsWith("SWR ") || core.startsWith("SWR-") || core.contains("SWR FERNSEHEN") || core.contains("SWR HD") || core.contains("SWR FHD"))) return 113
        if (isDachStation && (core == "MDR" || core.startsWith("MDR ") || core.startsWith("MDR-") || core.contains("MDR FERNSEHEN") || core.contains("MDR HD") || core.contains("MDR FHD"))) return 114
        if (isDachStation && (core == "RBB" || core.startsWith("RBB ") || core.startsWith("RBB-") || core.contains("RBB FERNSEHEN") || core.contains("RBB HD") || core.contains("RBB FHD"))) return 115
        if (isDachStation && (core == "HR" || core.startsWith("HR ") || core.startsWith("HR-") || core.contains("HR FERNSEHEN") || core.contains("HR HD") || core.contains("HESSEN"))) return 116
        if (isDachStation && (core == "SR" || core.startsWith("SR ") || core.startsWith("SR-") || core.contains("SR FERNSEHEN") || core.contains("SR HD"))) return 117
        if (isDachStation && core.contains("RADIO BREMEN")) return 118
        if (core.contains("DEUTSCHE WELLE") || core.contains("DW ")) return 119

        // c) Spartensender & Partner ÖRR
        if (core.contains("3SAT")) return 120
        if (core.contains("ARTE")) return 121
        if (core.contains("KIKA") || core.contains("KI.KA")) return 122
        if (core.contains("PHOENIX")) return 123
        if (core.contains("ONE HD") || core.contains("ARD ONE") || core == "ONE") return 124
        if (core.contains("TAGESSCHAU24") || core.contains("TAGESSCHAU 24")) return 125
        if (core.contains("ZDFINFO") || core.contains("ZDF INFO")) return 126
        if (core.contains("ZDFNEO") || core.contains("ZDF NEO")) return 127
        if (core.contains("ARD ALPHA") || core.contains("ARD-ALPHA") || core.contains("BR ALPHA")) return 128

        // 4. Private Anbieter (Free-TV)
        // a) RTL Group
        if (isDachStation && core.contains("RTL") && !core.contains("RTL 2") && !core.contains("RTL II") &&
            !core.contains("RTL2") && !core.contains("SUPER RTL") && !core.contains("NITRO") &&
            !core.contains("RTL CRIME") && !core.contains("RTL LIVING") && !core.contains("RTL PASSION") &&
            !core.contains("RTLUP") && !core.contains("RTL UP") && !core.contains("RTL+") && !core.contains("RTL PLUS")
        ) return 135
        if (isDachStation && (core.contains("RTLUP") || core.contains("RTL UP") || core.contains("RTL PLUS"))) return 137
        if (isDachStation && (core.contains("RTL 2") || core.contains("RTL II") || core.contains("RTL2"))) return 139
        if (isDachStation && core.contains("VOX") && !core.contains("VOXUP") && !core.contains("VOX UP")) return 141
        if (isDachStation && (core.contains("VOXUP") || core.contains("VOX UP"))) return 142
        if (isDachStation && core.contains("NITRO")) return 143
        if (isDachStation && (core.contains("SUPER RTL") || core.contains("TOGGO"))) return 144
        if (isDachStation && (core.contains("N-TV") || core.contains("NTV"))) return 145

        // b) ProSiebenSat.1
        if (isDachStation && (core.contains("SAT.1") || core.contains("SAT 1") || core.contains("SAT1")) &&
            !core.contains("GOLD") && !core.contains("EMOTIONS")
        ) return 150
        if (isDachStation && (core.contains("SAT.1 GOLD") || core.contains("SAT 1 GOLD"))) return 152
        if (isDachStation && (core.contains("PROSIEBEN") || core.contains("PRO 7") || core.contains("PRO7")) &&
            !core.contains("MAXX") && !core.contains("FUN")
        ) return 154
        if (isDachStation && (core.contains("PROSIEBEN MAXX") || core.contains("PRO 7 MAXX") || core.contains("PRO7 MAXX"))) return 156
        if (isDachStation && (core.contains("KABEL EINS") || core.contains("KABEL 1")) &&
            !core.contains("DOKU") && !core.contains("CLASSIC")
        ) return 158
        if (isDachStation && (core.contains("KABEL EINS DOKU") || core.contains("KABEL 1 DOKU"))) return 160
        if (isDachStation && core.contains("SIXX")) return 162

        // c) Unabhängige & Sparten Free-TV
        if (isDachStation && (core.contains("TELE 5") || core.contains("TELE5"))) return 170
        if (isDachStation && core.contains("DMAX")) return 172
        if (isDachStation && core.contains("TLC")) return 174
        val isWelt = (core == "WELT" || core == "WELT HD" || core == "WELT FHD" || core == "WELT TV" ||
                      core.startsWith("WELT ") || core.contains("WELT FERNSEHEN") || core.contains("N24")) &&
                     !core.contains("WELTKRIEG") && !core.contains("WELTRAUM") && !core.contains("WELT DER WUNDER") &&
                     !core.contains("UNENDLICH") && !core.contains("BILDSCHOENE") && !core.contains("KRIEG")
        if (isWelt && isDachStation) return 176
        if (isDachStation && core.contains("DISNEY")) return 178
        if (isDachStation && core.contains("COMEDY CENTRAL")) return 180
        if (isDachStation && core.contains("DELUXE MUSIC")) return 182
        if (isDachStation && core.contains("SERVUS")) return 184
        if (isDachStation && (core.contains("BILD HD") || core.contains("BILD TV"))) return 186
        if (isDachStation && core.contains("DF1")) return 188
        if (isDachStation && (core.contains("ANIXE+") || core.contains("ANIXE HD") || core.contains("ANIXE"))) return 190
        if (isDachStation && core.contains("MTV")) return 192
        if (isDachStation && (core.contains("R9") || core.contains("W24") || core.contains("LT1") || core.contains("SCHAUTV") || core.contains("KURIER TV") || core.contains("OKTO") || core.contains("TIROL TV") || core.contains("LÄNDLE TV"))) return 194

        // 5. Sky Entertainment & Pay-TV (User requirement: "dann Sky usw.....")
        // a) Sky Cinema & Film-Sender
        if (core.contains("SKY CINEMA PREMIERE") || core.contains("SKY PREMIERE")) return 200
        if (core.contains("SKY CINEMA HIGHLIGHTS") || core.contains("SKY HIGHLIGHTS")) return 202
        if (core.contains("SKY CINEMA ACTION") || core.contains("SKY ACTION") || core.contains("SKY THRILLER")) return 204
        if (core.contains("SKY CINEMA FAMILY") || core.contains("SKY FAMILY") || core.contains("SKY FUN")) return 206
        if (core.contains("SKY CINEMA CLASSICS") || core.contains("SKY CLASSICS") || core.contains("SKY NOSTALGIE")) return 208
        if (core.contains("SKY CINEMA") || core.contains("SKY COMEDY") || core.contains("SKY SPECIAL")) return 210
        if (core.contains("WARNER TV FILM") || core.contains("TNT FILM")) return 212
        if (core.contains("KINOWELT")) return 214

        // b) Sky Serien, Dokus & Premium Entertainment
        if (core.contains("SKY ONE")) return 220
        if (core.contains("SKY SHOWCASE")) return 222
        if (core.contains("SKY ATLANTIC")) return 224
        if (core.contains("SKY KRIMI") || core.contains("SKY CRIME")) return 226
        if (core.contains("SKY DOCUMENTARIES")) return 228
        if (core.contains("SKY NATURE")) return 230
        if (core.contains("SKY REPLAY") || core.contains("SKY SCI-FI") || core.contains("SKY ")) return 232
        if (core.contains("13TH STREET") || core.contains("13. STREET")) return 236
        if (core.contains("UNIVERSAL TV") || core.contains("UNIVERSAL HD")) return 238
        if (core.contains("WARNER TV SERIE") || core.contains("TNT SERIE")) return 240
        if (core.contains("WARNER TV COMEDY") || core.contains("TNT COMEDY")) return 242
        if (core.contains("SYFY") || core.contains("SCI-FI")) return 244
        if (core.contains("CRIME+INVESTIGATION") || core.contains("CRIME INVESTIGATION")) return 246
        if (core.contains("DISCOVERY") || core.contains("DISCOVERY CHANNEL")) return 248
        if (core.contains("NATIONAL GEOGRAPHIC") || core.contains("NAT GEO")) return 250
        if (core.contains("HISTORY") || core.contains("THE HISTORY CHANNEL")) return 252
        if (core.contains("GEO TELEVISION") || core.contains("SPIEGEL GESCHICHTE") || core.contains("CURIOSITY")) return 254
        if (core.contains("ROMANCE TV") || core.contains("HEIMATKANAL")) return 256
        if (isDachStation && (core.contains("RTL CRIME") || core.contains("RTL PASSION") || core.contains("RTL LIVING"))) return 258
        if (isDachStation && (core.contains("SAT.1 EMOTIONS") || core.contains("PROSIEBEN FUN") || core.contains("KABEL EINS CLASSICS"))) return 259

        // 6. Sonstige reguläre deutsche/DACH TV-Sender
        if (isDachStation) {
            return 280
        }
        return 350
    }

    /**
     * Sorts server-provided categories ensuring:
     * 1. FAVORITEN (Rank 0)
     * 2. ALLE (Rank 1)
     * 3. DE : / DEUTSCHLAND / DE ✪ with exact sub-group ordering from screenshot (Rank 2)
     * 4. AT : / ÖSTERREICH / AT ✪ (Rank 3)
     * 5. CH : / SCHWEIZ / CH ✪ (Rank 4)
     * 6. SPORT / FUSSBALL / BUNDESLIGA / DAZN (Rank 5)
     * 7. ADULT / 18+ / XXX (Rank 99)
     * 8. All other countries & categories sorted alphabetically
     */
    fun getCategorySubRank(cat: String): Int {
        categorySubRankCache.get(cat)?.let { return it }
        val u = cat.trim().uppercase()
        val computed = when {
            // 1. DEUTSCHLAND (Main German Live TV)
            (u.contains("DEUTSCHLAND") || u.contains("DEUTSCH") || u.contains("GERMANY")) &&
                !u.contains("REGIONAL") && !u.contains("TIMELINE") && !u.contains("BOX") -> 10
            u == "DE" || u == "DE:" || u == "DE :" || u == "DE|" || u == "[DE]" -> 11

            // 2. Technical variants (HEVC, RAW, UHD/QHD, FHD, SD)
            u.contains("HEVC") -> 20
            u.contains("RAW") -> 21
            u.contains("UHD") || u.contains("QHD") || u.contains("4K") -> 22
            u.contains("FHD") -> 23

            // 3. Regional / Lokal
            u.contains("REGIONAL") || u.contains("LOKAL") -> 30

            // 4. News
            u.contains("NEWS") || u.contains("NACHRICHTEN") || u.contains("INFO") -> 40

            // 5. Doku
            u.contains("DOKU") || u.contains("DOCU") || u.contains("DOCUMENTARY") -> 50

            // 6. RTL+ & Unterhaltung
            u.contains("RTL+ EVENT") || u.contains("RTL PLUS EVENT") -> 108
            u.contains("RTL+") || u.contains("RTL PLUS") -> 60
            u.contains("UNTERHALTUNG") || u.contains("PROGRAMM") -> 65

            // 7. Sky Cinema, Select & Kino
            u.contains("SKY CINEMA") -> 70
            u.contains("SKY SELECT") || u.contains("SELECT KINO") || u.contains("SELECT") -> 71
            u.contains("SKY ENTERTAINMENT") || u.contains("SKY SERIEN") -> 72
            u.contains("SKY") && !u.contains("SPORT") && !u.contains("BUNDESLIGA") -> 73
            u.contains("KINO LOUNGE") || u.contains("KINO") || u.contains("CINEMA") -> 75

            // 8. Kinder & 24/7 Series/Cinema
            u.contains("KINDER") || u.contains("KIDS") || u.contains("CARTOON") -> 80
            u.contains("TV ACTOR") || u.contains("ACTOR") -> 81
            u.contains("TV SERIEN") || u.contains("SERIEN 24/7") || u.contains("SERIEN 7/24") -> 82
            u.contains("TV CINEMA") || u.contains("CINEMA 24/7") || u.contains("CINEMA 7/24") -> 83
            u.contains("TOP SERIEN") -> 84
            u.contains("24/7") || u.contains("24-7") || u.contains("24X7") || u.contains("7/24") -> 85

            // 9. Streaming Top 10
            u.contains("NETFLIX TOP10") || u.contains("NETFLIX TOP 10") || u.contains("NETFLIX") -> 91
            u.contains("AMAZON") || u.contains("PRIME") || u.contains("DISNEY") || u.contains("APPLE") -> 92

            // 10. Sport Channels (Sky Bundesliga, DAZN, UEFA, Sky Sport, etc.)
            u.contains("BUNDESLIGA") -> 100
            u.contains("DAZN SENDER") -> 101
            u.contains("DAZN FUSSBALL") -> 102
            u.contains("DAZN EVENT") || u.contains("DAZN") -> 103
            u.contains("CHAMPIONS LEAGUE") || u.contains("UEFA") -> 104
            u.contains("SKY SPORT F1") -> 105
            u.contains("SKY SPORT") -> 106
            u.contains("SPORT") || u.contains("FUSSBALL") -> 107
            u.contains("RTL+ EVENT") || u.contains("RTL PLUS EVENT") -> 108
            u.contains("MAGENTA") -> 109
            u.contains("MYTEAM") -> 110
            u.contains("LEAGUES") -> 111
            u.contains("DYN SPORT") || u.contains("DYN") -> 112
            u.contains("DEL.2") || u.contains("DEL 2") || u.contains("DEL EVENT") || u.contains("DEL") -> 113
            u.contains("SPORTEUROPE") -> 114

            // 11. Musik & Radio
            u.contains("MUSIK") || u.contains("MUSIC") -> 120
            u.contains("RADIO") || u.contains("RADYO") -> 121

            // AT & CH sub categories
            u.contains("AUSTRIA SPORT") -> 10
            u == "AT ✪ AUSTRIA" || u.contains("AUSTRIA") -> 5
            u.contains("BLUE SPORT") -> 10
            u.contains("MYSPORT") -> 11
            u == "CH ✪ SWISS" || u.contains("SWISS") || u.contains("SCHWEIZ") -> 5

            else -> 95
        }
        categorySubRankCache.put(cat, computed)
        return computed
    }

    fun getCategoryPriority(cat: String): Int {
        categoryPriorityCache.get(cat)?.let { return it }
        val upper = cat.trim().uppercase()
        val prefixCode = extractCountryCodeFromPrefix(upper)
        val computed = when {
            // Rank 0: Favoriten
            upper == "FAVORITEN" || upper == "FAVORITES" || upper.startsWith("FAVORIT") -> 0

            // Rank 1: Alle
            upper == "ALLE" || upper == "ALL" -> 1

            // Rank 2: DE (Deutschland)
            prefixCode == "DE" ||
            upper.startsWith("DE *") || upper.startsWith("DE*") ||
            upper.startsWith("DE :") || upper.startsWith("DE:") || upper.startsWith("DE|") ||
            upper.startsWith("DE -") || upper.startsWith("[DE]") || upper.startsWith("(DE)") ||
            upper.startsWith("DE ✪") || upper.startsWith("DE✪") || upper.startsWith("DE ") ||
            upper.startsWith("GER:") || upper.startsWith("GER :") || upper.startsWith("GER|") ||
            upper.startsWith("DEU:") || upper.startsWith("DEU :") ||
            upper.startsWith("EU DE") || upper.startsWith("EU | DE") || upper.startsWith("EU|DE") ||
            upper.startsWith("EU-DE") || upper.contains("EU DE ") ||
            upper.startsWith("DEUTSCHLAND") || upper.startsWith("DEUTSCH") ||
            upper.contains(" DE :") || upper.contains("| DE :") || upper == "DE" -> 2

            // Rank 3: AT (Österreich)
            prefixCode == "AT" ||
            upper.startsWith("AT *") || upper.startsWith("AT*") ||
            upper.startsWith("AT :") || upper.startsWith("AT:") || upper.startsWith("AT|") ||
            upper.startsWith("AT -") || upper.startsWith("[AT]") || upper.startsWith("(AT)") ||
            upper.startsWith("AT ✪") || upper.startsWith("AT✪") || upper.startsWith("AT ") ||
            upper.startsWith("AUT:") || upper.startsWith("AUT :") || upper.startsWith("AUT|") ||
            upper.startsWith("EU AUSTRIA") || upper.startsWith("EU | AUSTRIA") || upper.startsWith("EU-AUSTRIA") ||
            upper.contains("EU AUSTRIA") ||
            upper.startsWith("ÖSTERREICH") || upper.startsWith("AUSTRIA") ||
            upper.contains(" AT :") || upper.contains("| AT :") || upper == "AT" -> 3

            // Rank 4: CH (Schweiz)
            prefixCode == "CH" ||
            upper.startsWith("CH *") || upper.startsWith("CH*") ||
            upper.startsWith("CH :") || upper.startsWith("CH:") || upper.startsWith("CH|") ||
            upper.startsWith("CH -") || upper.startsWith("[CH]") || upper.startsWith("(CH)") ||
            upper.startsWith("CH ✪") || upper.startsWith("CH✪") || upper.startsWith("CH ") ||
            upper.startsWith("SUI:") || upper.startsWith("SUI :") || upper.startsWith("SUI|") ||
            upper.startsWith("EU SWITZERLAND") || upper.startsWith("EU | SWITZERLAND") ||
            upper.startsWith("EU-SWITZERLAND") || upper.contains("EU SWITZERLAND") ||
            upper.startsWith("SCHWEIZ") || upper.startsWith("SWISS") || upper.startsWith("SWITZERLAND") ||
            upper.contains(" CH :") || upper.contains("| CH :") || upper == "CH" -> 4

            // Rank 5: Adult (between Schweiz and international countries)
            prefixCode == "ADULT" || isAdultGroup(upper) -> 5

            // Rank 6: Other known countries ordered by country rank (e.g. UK=15, US=16, TR=17, IT=18, FR=19, ES=20...)
            prefixCode != null && prefixCode != "SPORT" -> 10 + getCountrySortRank(prefixCode)

            // Rank 7: Sport
            prefixCode == "SPORT" || upper.contains("SPORT") || upper.contains("FUSSBALL") || upper.contains("BUNDESLIGA") || upper.contains("DAZN") -> 80

            // Rank 8: All other categories alphabetically
            else -> 50
        }
        categoryPriorityCache.put(cat, computed)
        return computed
    }

    /**
     * Sorts category groups according to user priorities:
     * 1. FAVORITEN (Rank 0)
     * 2. ALLE (Rank 1)
     * 3. DE : / DEUTSCHLAND / DE ✪ (Rank 2)
     * 4. AT : / ÖSTERREICH / AT ✪ (Rank 3)
     * 5. CH : / SCHWEIZ / CH ✪ (Rank 4)
     * 6. SPORT / FUSSBALL / BUNDESLIGA / DAZN (Rank 5)
     * 7. ADULT / 18+ / XXX (Rank 99)
     * 8. All other countries & categories sorted alphabetically
     */
    fun sortCategoriesWithDachPriority(categories: List<String>): List<String> {
        fun cleanSortKey(cat: String): String {
            return getCleanSubFolderName(cat).lowercase()
        }

        return categories
            .filter { it.isNotBlank() }
            .distinct()
            .sortedWith(
                compareBy<String> { getCategoryPriority(it) }
                    .thenBy { getCategorySubRank(it) }
                    .thenBy { cleanSortKey(it) }
            )
    }

    /**
     * Maps any playlist group title to a normalized country / category code.
     */
    fun detectCountryForGroup(groupTitle: String): String {
        val upper = groupTitle.trim().uppercase()
        if (upper.isBlank()) return "OTHER"

        // 0. AT / Austria priority
        if (isAtOrAustriaContent("", upper)) return "AT"

        // 1. Fast Prefix detection based on header tag (e.g. "DE * Kino", "AT * Austria", "CH * Swiss", "ES * Hentai", "XXX * For  Adults")
        val prefixCode = extractCountryCodeFromPrefix(upper)
        if (prefixCode != null) return prefixCode

        // 2. Adult content check if not explicitly prefixed
        if (isAdultContent("", upper)) return "ADULT"

        if (upper.startsWith("EU DE") || upper.startsWith("EU | DE") || upper.startsWith("EU|DE") ||
            upper.startsWith("EU-DE") || upper.contains("EU DE ") ||
            upper.startsWith("DE :") || upper.startsWith("DE:") || upper.startsWith("DE|") ||
            upper.startsWith("DE -") || upper.startsWith("[DE]") || upper.startsWith("(DE)") ||
            upper.startsWith("DE ✪") || upper.startsWith("DE✪") || upper.startsWith("DE ") ||
            upper.startsWith("GER:") || upper.startsWith("GER|") || upper.startsWith("DEU:") ||
            upper.contains("DEUTSCHLAND") || upper.contains("GERMANY") || upper.contains("GERMAN") ||
            upper.contains("DEUTSCH") || upper == "DE"
        ) {
            return "DE"
        }

        if (upper.startsWith("EU AUSTRIA") || upper.startsWith("EU | AUSTRIA") || upper.startsWith("EU-AUSTRIA") ||
            upper.contains("EU AUSTRIA") ||
            upper.startsWith("AT :") || upper.startsWith("AT:") || upper.startsWith("AT|") ||
            upper.startsWith("AT -") || upper.startsWith("[AT]") || upper.startsWith("(AT)") ||
            upper.startsWith("AT ✪") || upper.startsWith("AT✪") || upper.startsWith("AT ") ||
            upper.startsWith("AUT:") || upper.contains("ÖSTERREICH") || upper.contains("AUSTRIA") || upper == "AT"
        ) {
            return "AT"
        }

        if (upper.startsWith("EU SWITZERLAND") || upper.startsWith("EU | SWITZERLAND") || upper.startsWith("EU-SWITZERLAND") ||
            upper.contains("EU SWITZERLAND") ||
            upper.startsWith("CH :") || upper.startsWith("CH:") || upper.startsWith("CH|") ||
            upper.startsWith("CH -") || upper.startsWith("[CH]") || upper.startsWith("(CH)") ||
            upper.startsWith("CH ✪") || upper.startsWith("CH✪") || upper.startsWith("CH ") ||
            upper.startsWith("SUI:") || upper.contains("SCHWEIZ") || upper.contains("SWISS") ||
            upper.contains("SWITZERLAND") || upper == "CH"
        ) {
            return "CH"
        }

        if (upper.startsWith("EU UK") || upper.startsWith("UK:") || upper.startsWith("UK|") ||
            upper.startsWith("UK -") || upper.startsWith("[UK]") || upper.contains("UNITED KINGDOM") ||
            upper.contains("ENGLAND") || upper.contains("BRITISH") || upper == "UK" || upper == "GB"
        ) {
            return "UK"
        }

        if (upper.startsWith("EU US") || upper.startsWith("US:") || upper.startsWith("US|") ||
            upper.startsWith("US -") || upper.startsWith("[US]") || upper.contains("USA") ||
            upper.contains("UNITED STATES") || upper == "US"
        ) {
            return "US"
        }

        if (upper.startsWith("TR:") || upper.startsWith("TR|") || upper.startsWith("TR -") ||
            upper.startsWith("[TR]") || upper.contains("TURKEY") || upper.contains("TÜRKIYE") || upper.contains("TURKISH") || upper == "TR"
        ) {
            return "TR"
        }

        if (upper.startsWith("EXYU:") || upper.startsWith("EX-YU:") || upper.startsWith("EX YU:") ||
            upper.contains("EXYU") || upper.contains("EX-YU") || upper.contains("BALKAN") || upper == "EXYU"
        ) {
            return "EXYU"
        }

        if (upper.startsWith("IT:") || upper.startsWith("IT|") || upper.startsWith("[IT]") ||
            upper.contains("ITALY") || upper.contains("ITALIA") || upper.contains("ITALIAN") || upper == "IT"
        ) {
            return "IT"
        }

        if (upper.startsWith("FR:") || upper.startsWith("FR|") || upper.startsWith("[FR]") ||
            upper.contains("FRANCE") || upper.contains("FRANCAIS") || upper.contains("FRENCH") || upper == "FR"
        ) {
            return "FR"
        }

        if (upper.startsWith("ES:") || upper.startsWith("ES|") || upper.startsWith("[ES]") ||
            upper.contains("SPAIN") || upper.contains("ESPANA") || upper.contains("SPANISH") || upper == "ES"
        ) {
            return "ES"
        }

        if (upper.startsWith("PL:") || upper.contains("POLAND") || upper.contains("POLSKA") || upper == "PL") return "PL"
        if (upper.startsWith("NL:") || upper.contains("NETHERLANDS") || upper.contains("NEDERLAND") || upper == "NL") return "NL"
        if (upper.startsWith("AL:") || upper.startsWith("ALB:") || upper.contains("ALBANIA") || upper.contains("SHQIP") || upper == "ALB" || upper == "AL") return "ALB"
        if (upper.startsWith("GR:") || upper.startsWith("GRE:") || upper.contains("GREECE") || upper.contains("GREEK") || upper == "GR") return "GR"
        if (upper.startsWith("BE:") || upper.startsWith("BEL:") || upper.contains("BELGIUM") || upper.contains("BELGIQUE") || upper == "BE") return "BE"
        if (upper.startsWith("RO:") || upper.startsWith("ROU:") || upper.contains("ROMANIA") || upper.contains("ROMANIAN") || upper == "RO") return "RO"
        if (upper.startsWith("PT:") || upper.contains("PORTUGAL") || upper.contains("BRAZIL") || upper == "PT" || upper == "BR") return "PT"
        if (upper.startsWith("SE:") || upper.contains("SWEDEN") || upper.contains("NORWAY") || upper.contains("DENMARK") || upper == "SE") return "SE"
        if (upper.startsWith("AR:") || upper.startsWith("ARA:") || upper.contains("ARABIC") || upper.contains("ARAB") || upper == "AR") return "AR"
        if (upper.startsWith("RU:") || upper.startsWith("RUS:") || upper.contains("RUSSIA") || upper.contains("RUSSIAN") || upper == "RU") return "RU"
        if (upper.startsWith("CZ:") || upper.startsWith("CZE:") || upper.contains("CZECH") || upper.contains("SLOWAK") || upper == "CZ") return "CZ"
        if (upper.startsWith("BG:") || upper.startsWith("BGR:") || upper.contains("BULGARIA") || upper.contains("BULGARIAN") || upper == "BG") return "BG"
        if (upper.startsWith("IN:") || upper.startsWith("IND:") || upper.contains("INDIA") || upper.contains("HINDI") || upper == "IN") return "IN"
        if (upper.startsWith("CA:") || upper.startsWith("CAN:") || upper.contains("CANADA") || upper == "CA") return "CA"
        if (upper.startsWith("AF:") || upper.startsWith("AFG:") || upper.contains("AFGHANISTAN") || upper == "AF") return "AF"
        if (upper.contains("VIP")) return "VIP"
        if (upper.contains("SPORT") || upper.contains("FUSSBALL") || upper.contains("DAZN") || upper.contains("BUNDESLIGA")) return "SPORT"

        return "OTHER"
    }

    data class CountryInfo(
        val code: String,
        val name: String,
        val flag: String,
        val rank: Int
    ) : Comparable<CountryInfo> {
        override fun compareTo(other: CountryInfo): Int {
            val r = rank.compareTo(other.rank)
            return if (r != 0) r else name.compareTo(other.name)
        }
    }

    val ALL_KNOWN_COUNTRIES: List<CountryInfo> = listOf(
        CountryInfo("DE", "DE: Deutschland", "🇩🇪", 1),
        CountryInfo("AT", "AT: Österreich", "🇦🇹", 2),
        CountryInfo("CH", "CH: Schweiz", "🇨🇭", 3),
        CountryInfo("US", "US: USA", "🇺🇸", 4),
        CountryInfo("UK", "UK: GB", "🇬🇧", 5),
        CountryInfo("TR", "TR: Türkei", "🇹🇷", 6),
        CountryInfo("IT", "IT: Italien", "🇮🇹", 7),
        CountryInfo("FR", "FR: Frankreich", "🇫🇷", 8),
        CountryInfo("ES", "ES: Spanien", "🇪🇸", 9),
        CountryInfo("NL", "NL: Niederlande", "🇳🇱", 10),
        CountryInfo("PL", "PL: Polen", "🇵🇱", 11),
        CountryInfo("EXYU", "EXYU: Balkan", "🇷🇸", 12),
        CountryInfo("ALB", "ALB: Albanien", "🇦🇱", 13),
        CountryInfo("PT", "PT: Portugal & Brasilien", "🇵🇹", 14),
        CountryInfo("RO", "RO: Rumänien", "🇷🇴", 15),
        CountryInfo("RU", "RU: Russland", "🇷🇺", 16),
        CountryInfo("AR", "AR: Arabisch", "🇸🇦", 17),
        CountryInfo("GR", "GR: Griechenland", "🇬🇷", 18),
        CountryInfo("BE", "BE: Belgien", "🇧🇪", 19),
        CountryInfo("CZ", "CZ: Tschechien & Slowakei", "🇨🇿", 20),
        CountryInfo("BG", "BG: Bulgarien", "🇧🇬", 21),
        CountryInfo("SE", "SE: Skandinavien", "🇸🇪", 22),
        CountryInfo("IN", "IN: Indien", "🇮🇳", 23),
        CountryInfo("CA", "CA: Kanada", "🇨🇦", 24),
        CountryInfo("AF", "AF: Afghanistan", "🇦🇫", 25),
        CountryInfo("VIP", "VIP / Specials", "⭐", 30),
        CountryInfo("SPORT", "Sportkanäle", "⚽", 80),
        CountryInfo("ADULT", "ADULT: 18+", "🔞", 90),
        CountryInfo("CONFLICT", "Konflikte", "⚠️", 95),
        CountryInfo("OTHER", "International / Andere", "🌍", 98),
        CountryInfo("UNKNOWN", "Nicht zugeordnet", "❓", 99)
    )

    fun getCountryInfo(rawCode: String, name: String = "", group: String = ""): CountryInfo {
        val detectedCode = detectNormalizedCountryCode(rawCode, name, group)
        return when (detectedCode) {
            "DE" -> CountryInfo("DE", "DE: Deutschland", "🇩🇪", 1)
            "AT" -> CountryInfo("AT", "AT: Österreich", "🇦🇹", 2)
            "CH" -> CountryInfo("CH", "CH: Schweiz", "🇨🇭", 3)
            "US" -> CountryInfo("US", "US: USA", "🇺🇸", 4)
            "UK" -> CountryInfo("UK", "UK: GB", "🇬🇧", 5)
            "TR" -> CountryInfo("TR", "TR: Türkei", "🇹🇷", 6)
            "IT" -> CountryInfo("IT", "IT: Italien", "🇮🇹", 7)
            "FR" -> CountryInfo("FR", "FR: Frankreich", "🇫🇷", 8)
            "ES" -> CountryInfo("ES", "ES: Spanien", "🇪🇸", 9)
            "NL" -> CountryInfo("NL", "NL: Niederlande", "🇳🇱", 10)
            "PL" -> CountryInfo("PL", "PL: Polen", "🇵🇱", 11)
            "EXYU" -> CountryInfo("EXYU", "EXYU: Balkan", "🇷🇸", 12)
            "ALB" -> CountryInfo("ALB", "ALB: Albanien", "🇦🇱", 13)
            "PT" -> CountryInfo("PT", "PT: Portugal & Brasilien", "🇵🇹", 14)
            "RO" -> CountryInfo("RO", "RO: Rumänien", "🇷🇴", 15)
            "RU" -> CountryInfo("RU", "RU: Russland", "🇷🇺", 16)
            "AR" -> CountryInfo("AR", "AR: Arabisch", "🇸🇦", 17)
            "GR" -> CountryInfo("GR", "GR: Griechenland", "🇬🇷", 18)
            "BE" -> CountryInfo("BE", "BE: Belgien", "🇧🇪", 19)
            "CZ" -> CountryInfo("CZ", "CZ: Tschechien & Slowakei", "🇨🇿", 20)
            "BG" -> CountryInfo("BG", "BG: Bulgarien", "🇧🇬", 21)
            "SE" -> CountryInfo("SE", "SE: Skandinavien", "🇸🇪", 22)
            "IN" -> CountryInfo("IN", "IN: Indien", "🇮🇳", 23)
            "CA" -> CountryInfo("CA", "CA: Kanada", "🇨🇦", 24)
            "AF" -> CountryInfo("AF", "AF: Afghanistan", "🇦🇫", 25)
            "VIP" -> CountryInfo("VIP", "VIP / Specials", "⭐", 30)
            "SPORT" -> CountryInfo("SPORT", "Sportkanäle", "⚽", 80)
            "ADULT" -> CountryInfo("ADULT", "ADULT: 18+", "🔞", 90)
            "CONFLICT" -> CountryInfo("CONFLICT", "Konflikte", "⚠️", 95)
            "OTHER" -> CountryInfo("OTHER", "International / Andere", "🌍", 98)
            "UNKNOWN" -> CountryInfo("UNKNOWN", "Nicht zugeordnet", "❓", 99)
            else -> CountryInfo("UNKNOWN", "Nicht zugeordnet", "❓", 99)
        }
    }

    fun getCountrySortRank(countryCode: String): Int {
        return getCountryInfo(countryCode).rank
    }

    fun detectNormalizedCountryCode(countryCode: String, name: String, groupTitle: String): String {
        val cacheKey = "$countryCode|$groupTitle"
        normalizedCountryCodeCache.get(cacheKey)?.let { return it }
        val computed = computeNormalizedCountryCode(countryCode, name, groupTitle)
        normalizedCountryCodeCache.put(cacheKey, computed)
        return computed
    }

    private fun computeNormalizedCountryCode(countryCode: String, @Suppress("UNUSED_PARAMETER") name: String, groupTitle: String): String {
        val cleanGroup = groupTitle.trim()
        val upperCountry = countryCode.trim().uppercase()

        // 1. Authoritative Provider Category (1:1 Provider Structure Priority)
        if (cleanGroup.isNotEmpty() && cleanGroup.uppercase() != "ALL" && cleanGroup.uppercase() != "ALLE") {
            val groupUpper = cleanGroup.uppercase()

            val groupPrefix = extractCountryCodeFromPrefix(groupUpper)
            if (groupPrefix != null) {
                val isAdult = isAdultGroup(groupUpper)
                return if (isAdult && groupPrefix !in listOf("ES", "DE", "AT", "CH", "US", "UK")) "ADULT" else groupPrefix
            }

            if (isAdultGroup(groupUpper)) return "ADULT"
            if (isAtOrAustriaContent("", groupUpper)) return "AT"
            if (isChOrSwissContent("", groupUpper)) return "CH"

            val explicitCatCountry = com.example.data.country.CountryNormalizer.extractCountryFromCategory(groupUpper)
            if (explicitCatCountry != null && explicitCatCountry !in listOf("SPORT", "OTHER", "UNKNOWN")) {
                return explicitCatCountry
            }

            val groupCountry = detectCountryForGroup(groupUpper)
            if (groupCountry !in listOf("OTHER", "SPORT", "VIP", "UNKNOWN")) {
                return groupCountry
            }
        }

        // 2. Explicit Country Metadata (countryCode / tvg-country / catalogCountry)
        if (upperCountry in listOf("DE", "AT", "CH", "ADULT", "OTHER", "SPORT", "VIP")) {
            return upperCountry
        }
        if (upperCountry == "INT") {
            return "OTHER"
        }
        if (cleanGroup.isEmpty()) {
            if (upperCountry == "UNKNOWN") return "UNKNOWN"
            if (upperCountry == "CONFLICT") return "CONFLICT"
        }

        val normalizedParam = normalizeRawCountryCode(upperCountry)
        if (normalizedParam != null && normalizedParam !in listOf("SPORT", "OTHER", "UNKNOWN", "INT")) {
            return normalizedParam
        }

        // 3. Central CountryResolver (strictly ignoring channel name)
        if (cleanGroup.isNotEmpty()) {
            val resolution = com.example.data.country.CountryResolver.resolve(
                title = "",
                groupTitle = cleanGroup,
                tvgCountry = if (upperCountry !in listOf("INT", "ALL", "OTHER", "UNKNOWN", "CONFLICT", "")) upperCountry else null,
                ignoreChannelName = true
            )
            if (resolution.countryCode.isNotBlank() && resolution.countryCode !in listOf("UNKNOWN", "CONFLICT", "INT")) {
                return resolution.countryCode
            }
            if (isSportContent("", cleanGroup)) return "SPORT"
            // 100% deterministic assignment for channels with a generic international category
            return "OTHER"
        }

        return if (normalizedParam != null && normalizedParam != "INT") normalizedParam else "UNKNOWN"
    }

    /**
     * Cleans raw group titles for clean sub-folder pills in the UI.
     * e.g. "EU DE Allgemeines" -> "Allgemeines"
     *      "EU DE 24/7" -> "24/7"
     *      "EU DE Dokumentar" -> "Dokumentar"
     *      "EU Austria" -> "Austria"
     *      "DE : Cinema" -> "Cinema"
     */
    fun getCleanSubFolderName(groupTitle: String): String {
        var s = groupTitle.trim()
        val exactMatches = listOf(
            "EU DE Allgemeines" to "Allgemeines",
            "EU DE 24/7" to "24/7",
            "EU DE Dokumentar" to "Dokumentar",
            "EU DE Sport" to "Sport",
            "EU DE Cinema" to "Cinema",
            "EU DE Filme" to "Filme",
            "EU DE Serien" to "Serien",
            "EU DE Kinder" to "Kinder",
            "EU DE Musik" to "Musik",
            "EU Austria" to "Austria",
            "EU Switzerland" to "Schweiz"
        )
        for ((pattern, clean) in exactMatches) {
            if (s.equals(pattern, ignoreCase = true)) return clean
        }

        val stripPrefixes = listOf(
            "EU | DE ", "EU|DE ", "EU DE ", "EU-DE ", "EU | ", "EU ",
            "DE * ", "DE* ", "DE : ", "DE: ", "DE | ", "DE|", "DE - ", "[DE] ", "(DE) ", "GER : ", "GER: ", "GER * ",
            "AT * ", "AT* ", "AT : ", "AT: ", "AT | ", "AT|", "AT - ", "[AT] ", "(AT) ", "AUT : ", "AUT: ", "AUT * ",
            "CH * ", "CH* ", "CH : ", "CH: ", "CH | ", "CH|", "CH - ", "[CH] ", "(CH) ", "SUI : ", "SUI: ", "SUI * ",
            "UK * ", "UK: ", "UK | ", "UK|", "UK - ", "[UK] ", "(UK) ",
            "US * ", "US: ", "US | ", "US|", "US - ", "[US] ", "(US) ",
            "TR * ", "TR: ", "TR | ", "TR|", "TR - ", "[TR] ", "(TR) ",
            "EXYU * ", "EXYU : ", "EXYU: ", "EX-YU : ", "EX-YU: ", "EX YU : ",
            "IT * ", "IT : ", "IT: ", "IT | ", "IT|", "[IT] ",
            "FR * ", "FR : ", "FR: ", "FR | ", "FR|", "[FR] ",
            "ES * ", "ES : ", "ES: ", "ES | ", "ES|", "[ES] ",
            "XXX * ", "XXX: ", "XXX - ", "XXX | ",
            "PL : ", "NL : ", "AL : ", "ALB : ", "GR : ", "BE : ", "RO : ", "AR : ", "RU : ", "CZ : ", "BG : ", "IN : ", "CA : ", "AF : ", "PT : ", "SE : ",
            "🔞 ", "★ ", "✪ ", "| ", "* "
        )
        for (p in stripPrefixes) {
            if (s.startsWith(p, ignoreCase = true)) {
                s = s.substring(p.length).trim()
            }
        }
        s = s.replace(CLEAN_PREFIX_REGEX, "").trim()
        s = s.removePrefix(":").removePrefix("|").removePrefix("-").removePrefix("*").trim()
        return s.ifBlank { groupTitle }
    }

    /**
     * Prioritization weight for German sub-folders:
     * Allgemeines -> Dokumentar -> Cinema/Filme -> Sky -> Serien -> 24/7 -> Kinder -> Musik -> Sport at the very end!
     */
    fun getSubFolderSortWeight(rawOrCleanName: String): Int {
        val u = rawOrCleanName.uppercase()
        return when {
            u.contains("ALLGEMEIN") || u.contains("FREE") || u.contains("HAUPT") || u.contains("BASIC") -> 10
            u.contains("DOKU") || u.contains("WISSEN") -> 20
            u.contains("CINEMA") || u.contains("FILM") || u.contains("KINO") || u.contains("MOVIE") -> 30
            u.contains("SKY") -> 40
            u.contains("SERIE") || u.contains("SHOW") -> 50
            u.contains("24/7") || u.contains("247") -> 60
            u.contains("KIND") || u.contains("KIDS") || u.contains("TOON") || u.contains("JUNIOR") -> 70
            u.contains("MUSIK") || u.contains("MUSIC") -> 80
            u.contains("NACHRICHT") || u.contains("NEWS") || u.contains("INFO") -> 90
            u.contains("SPORT") || u.contains("FUSSBALL") || u.contains("BUNDESLIGA") || u.contains("DAZN") -> 500 // Sport strictly at the end!
            else -> 100
        }
    }

    /**
     * Builds structured country folders from raw playlist groups.
     * Every country is listed ONLY ONCE!
     * Prioritized order:
     * DE, AT, CH are at the front, followed by Adult (between Schweiz and other countries),
     * followed by other countries, and Sport at the end.
     */
    fun buildCountryFolders(
        rawGroups: List<String>,
        channels: List<ChannelItem> = emptyList()
    ): List<CountryFolder> {
        val groupsByCountry = mutableMapOf<String, MutableList<String>>()

        for (g in rawGroups) {
            if (g.isBlank()) continue
            val code = detectCountryForGroup(g)
            groupsByCountry.getOrPut(code) { mutableListOf() }.add(g)
        }

        // Also ensure US and UK folders are present if any channels exist
        val hasUs = channels.any { it.normalizedCountryCode == "US" }
        if (hasUs && !groupsByCountry.containsKey("US")) {
            groupsByCountry["US"] = mutableListOf("USA")
        }
        val hasUk = channels.any { it.normalizedCountryCode == "UK" }
        if (hasUk && !groupsByCountry.containsKey("UK")) {
            groupsByCountry["UK"] = mutableListOf("UK / GB")
        }

        // Also ensure ADULT folder is present if any channels are genuinely classified as adult (excluding AT)
        val hasAdultChannels = channels.any {
            !isAtOrAustriaContent("", it.groupTitle) && it.countryCode.uppercase() != "AT" &&
            (it.countryCode.equals("ADULT", ignoreCase = true) || isAdultGroup(it.groupTitle))
        }
        if (hasAdultChannels && !groupsByCountry.containsKey("ADULT")) {
            groupsByCountry["ADULT"] = mutableListOf("18+ / Adult")
        }

        val subCountMap = mutableMapOf<String, Int>()
        val countryCountMap = mutableMapOf<String, Int>()
        if (channels.isNotEmpty()) {
            for (ch in channels) {
                val isAt = isAtOrAustriaContent("", ch.groupTitle) || ch.countryCode.uppercase() == "AT" || ch.normalizedCountryCode == "AT"
                val isAdult = !isAt && (isAdultGroup(ch.groupTitle) || ch.countryCode.equals("ADULT", ignoreCase = true))
                if (ch.type == ContentType.LIVE_TV || isAdult || isAt) {
                    val group = ch.groupTitle
                    if (group.isNotBlank()) {
                        subCountMap[group] = (subCountMap[group] ?: 0) + 1
                    }
                    val code = when {
                        isAt -> "AT"
                        isAdult -> "ADULT"
                        else -> ch.normalizedCountryCode.ifBlank { detectCountryForGroup(ch.groupTitle) }
                    }
                    countryCountMap[code] = (countryCountMap[code] ?: 0) + 1
                }
            }
        }

        return groupsByCountry.map { (code, rawSubGroups) ->
            val info = getCountryInfo(code)
            // Preserve the provider's native category & subcategory order (distinct in appearance order)
            val sortedSub = rawSubGroups.distinct()
            val cCount = countryCountMap[code] ?: channels.count {
                if (code == "ADULT") {
                    !isAtOrAustriaContent("", it.groupTitle) && it.countryCode.uppercase() != "AT" &&
                    (it.countryCode.equals("ADULT", ignoreCase = true) || isAdultGroup(it.groupTitle))
                } else if (code == "AT") {
                    isAtOrAustriaContent("", it.groupTitle) || it.countryCode.uppercase() == "AT" || it.normalizedCountryCode == "AT"
                } else {
                    it.type == ContentType.LIVE_TV && (detectCountryForGroup(it.groupTitle) == code || it.countryCode.equals(code, ignoreCase = true) || it.normalizedCountryCode.equals(code, ignoreCase = true))
                }
            }
            CountryFolder(
                code = code,
                displayName = info.name,
                flagEmoji = info.flag,
                subFolders = sortedSub,
                channelCount = cCount,
                subFolderCounts = subCountMap
            )
        }.sortedWith(
            compareBy<CountryFolder> {
                val rank = getCountrySortRank(it.code)
                if (rank in 1..4) 0 else if (it.channelCount > 0) 1 else 2
            }
                .thenBy { getCountrySortRank(it.code) }
                .thenBy { it.displayName }
        )
    }

    /**
     * Search relevance scoring tailored to user's strict priorities:
     * 1. Exact/prefix match on channel/content name
     * 2. German/DACH priority (DE first, AT second, CH third)
     * 3. Live TV: ÖRR > Private > Sky > Sonstige > 24/7 > Sport (strictly at the bottom!)
     * 4. VOD: German audio/movies first > English next > Rest
     * 5. Adult penalized to bottom
     * Returns a composite score. Higher score = higher ranking.
     */
    fun calculateSearchRelevance(
        name: String,
        groupTitle: String,
        cleanSortTitle: String,
        isFavorite: Boolean,
        contentType: ContentType,
        countryCode: String,
        isDach: Boolean,
        liveTvSortRank: Int,
        vodLanguageRank: Int,
        query: String
    ): Int {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return 0
        val rawName = name.lowercase()
        val cleanName = cleanSortTitle.lowercase()
        val group = groupTitle.lowercase()

        val hasTitleMatch = rawName.contains(q) || cleanName.contains(q)
        val hasGroupMatch = group.contains(q)

        if (!hasTitleMatch && !hasGroupMatch) return -100000

        var score = 0

        // 1. Text Match Quality
        when {
            cleanName == q || rawName == q -> score += 60000
            cleanName.startsWith(q) || rawName.startsWith(q) -> score += 40000
            cleanName.split(" ", "-", "_", "|", ":", ".").any { it.startsWith(q) } -> score += 25000
            hasTitleMatch -> score += 15000
            else -> score += 4000 // Only matched in groupTitle
        }

        // 2. User priority: DACH / DE first!
        val country = countryCode.uppercase()
        when {
            country == "DE" || (isDach && country != "AT" && country != "CH") -> score += 20000
            country == "AT" -> score += 16000
            country == "CH" -> score += 14000
            country in listOf("UK", "US") -> score += 5000
            country == "ADULT" -> score -= 30000
            else -> score += 2000
        }

        // 3. Live TV Priorities (ÖRR > Private > Sky > Sonstige > Sport ganz am Ende)
        if (contentType == ContentType.LIVE_TV) {
            when (liveTvSortRank) {
                in 100..160 -> score += 10000 // ÖRR (ARD, ZDF, 3sat, Dritte, ORF, SRF)
                in 200..250 -> score += 7500  // Große Privatsender (RTL, Sat.1, Pro7, VOX)
                in 300..350 -> score += 5000  // Sky & Pay-TV Entertainment
                in 400..500 -> score += 2500  // Sonstige reguläre Sender
                in 600..690 -> score -= 8000  // Sportkanäle am Ende!
                else -> {}
            }
        }

        // 4. VOD Language Hierarchy (DE > EN > Rest > Adult)
        if (contentType == ContentType.MOVIE || contentType == ContentType.SERIES) {
            when (vodLanguageRank) {
                1 -> score += 12000 // Deutsch
                2 -> score += 6000  // Englisch
                3 -> score += 1000  // Rest
                4 -> score -= 20000 // Adult
            }
        }

        // 5. Favorite bonus
        if (isFavorite) {
            score += 4000
        }

        return score
    }

    /**
     * Checks whether an entry in the playlist represents a section header, group divider,
     * or info banner rather than a playable TV or radio channel.
     */
    fun isHeaderOrDividerChannel(name: String, streamUrl: String? = null, group: String? = null): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return true

        val upper = trimmed.uppercase()
        val url = (streamUrl ?: "").trim().lowercase()

        // 1. Placeholder or non-playable URLs frequently assigned to header channels
        if (url.isNotBlank()) {
            val isStalkerReq = url.contains("action=create_link") || url.contains("/ch/") ||
                    url.contains("type=itv") || url.contains("type=vod") || url.contains("type=series") ||
                    url.startsWith("mac_portal://") || url.startsWith("ffmpeg") || url.startsWith("ffrt")
            if (!isStalkerReq) {
                if (url == "http://" || url == "https://" || url == "#" || url == "about:blank" || url == "http://localhost" || url == "http://localhost/") return true
                if (url.contains("/dummy") || url.contains("/placeholder") ||
                    url.contains("dummy.mp4") || url.contains("dummy.ts") || url.contains("dummy.m3u8") ||
                    url.contains("placeholder.mp4") || url.contains("/ch_dummy") || url.contains("/ch_fake") ||
                    url.contains("/header") || url.contains("/separator") || url.contains("/divider") ||
                    url.contains("0.0.0.0") ||
                    ((url.startsWith("http://127.0.0.1") || url.startsWith("http://localhost")) && !url.contains("/ch/"))
                ) {
                    return true
                }
            }
        }

        // 2. Pure divider strings containing no letters or digits (e.g. "----------------", "================")
        val letterOrDigitCount = trimmed.count { it.isLetterOrDigit() }
        if (letterOrDigitCount == 0 && trimmed.length >= 2) {
            return true
        }

        // 3. Almost entirely separator characters with minimal text (e.g. "---- SKY ----", "== DE ==")
        val symbolChars = "-=*#_~+|<>/\\━─═▬█░▒▓.:•★⭐►◄▶◀▼▲■◆"
        val symbolCount = trimmed.count { it in symbolChars }
        if (symbolCount >= 4 && letterOrDigitCount <= 3 && trimmed.length >= 4) {
            return true
        }

        // 4. Starts and ends with 2 or more decorative / divider symbols
        if (BANNER_REGEX.matches(trimmed)) {
            return true
        }

        // 5. Starts with 3 or more decorative separator characters
        val startDecorators = listOf("---", "===", "***", "###", "<<<", ">>>", "___", "+++", "►►", "◄◄", "■■", "✦✦", "━━", "──", "══", "••••", "⬇️⬇️", "↓↓")
        if (startDecorators.any { trimmed.startsWith(it) }) {
            return true
        }

        // 6. Ends with 3 or more decorative separator characters
        val endDecorators = listOf("---", "===", "***", "###", "<<<", ">>>", "___", "+++", "►►", "◄◄", "■■", "✦✦", "━━", "──", "══")
        if (endDecorators.any { trimmed.endsWith(it) }) {
            return true
        }

        // 7. Informational / announcement headers (not real TV channels)
        val announcementPrefixes = listOf(
            "INFO :", "INFO:", "SERVER INFO", "SERVERSTATUS", "WICHTIGE INFO", "BITTE LESEN",
            "WICHTIG :", "WICHTIG:", "HINWEIS :", "HINWEIS:", "ACHTUNG :", "ACHTUNG:",
            "TELEGRAM :", "TELEGRAM:", "WHATSAPP :", "WHATSAPP:", "DISCORD :", "SUPPORT :",
            "REGELN :", "RULES :", "ANNOUNCEMENT :", "NEWS :", "NEUIGKEITEN :", "UPDATE :",
            "WILLKOMMEN", "WELCOME TO", "SERVER EXPIRE", "EXPIRATION DATE", "EXPIRE DATE"
        )
        if (announcementPrefixes.any { upper.startsWith(it) }) {
            return true
        }

        // 8. Exact section header keywords
        val exactHeaderKeywords = listOf(
            "KATEGORIE", "CATEGORY", "CHANNELS", "SENDERLISTE", "TV GUIDE", "EPG GUIDE",
            "PLAYLIST INFO", "--- ENDE ---", "--- END ---"
        )
        if (exactHeaderKeywords.any { upper == it }) {
            return true
        }

        return false
    }

    fun isRadioStation(
        name: String,
        group: String?,
        streamUrl: String?,
        tvgType: String?
    ): Boolean {
        if (tvgType.equals("radio", ignoreCase = true) || tvgType.equals("audio", ignoreCase = true)) {
            return true
        }

        val nameUpper = name.uppercase().trim()
        val groupUpper = (group ?: "").uppercase().trim()
        val url = (streamUrl ?: "").lowercase().trim()

        // 0. Audio Stream URL extensions & servers
        if (url.endsWith(".mp3") || url.endsWith(".aac") || url.endsWith(".m4a") ||
            url.endsWith(".ogg") || url.endsWith(".pls") || url.endsWith(".asx") ||
            url.contains("/icecast") || url.contains("/shoutcast") ||
            url.contains(":8000/") || url.contains(":8002/") || url.contains(":8004/") ||
            url.contains("stream.laut.fm") || url.contains("radioplayer") || url.contains("radio.net")
        ) {
            return true
        }

        // 1. Explicit TV Station exceptions (Prevent false positive TV stations)
        if (nameUpper.contains("RADIO BREMEN TV") || nameUpper.contains("RADIO BREMEN FERNSEHEN") ||
            nameUpper.contains("RADIO BREMEN HD") || nameUpper.contains("BREMEN EINS TV") ||
            nameUpper.contains("TV BREMEN")
        ) {
            return false
        }
        if (nameUpper.endsWith(" TV") || nameUpper.endsWith(" FERNSEHEN") ||
            nameUpper.contains(" TV ") || nameUpper.contains("FERNSEHEN")
        ) {
            if (!nameUpper.contains("RADIO") && !groupUpper.contains("RADIO")) {
                return false
            }
        }

        // 2. Radio Category / Group
        if (groupUpper.contains("RADIO") || groupUpper.contains("RADIOSENDER") ||
            groupUpper.contains("WEBRADIO") || groupUpper.contains("UKW") ||
            groupUpper.contains("DAB") || groupUpper.contains("FM SENDER") ||
            groupUpper.contains("FM RADIO") || groupUpper.contains("AUDIO") ||
            groupUpper.contains("PODCAST") || groupUpper.startsWith("DE: RADIO") ||
            groupUpper.startsWith("DE | RADIO") || groupUpper.startsWith("GERMAN RADIO")
        ) {
            return true
        }

        // 3. Known Public and Commercial DACH Radio Broadcasters
        val knownRadioStations = listOf(
            "1LIVE", "1 LIVE", "WDR 2", "WDR 3", "WDR 4", "WDR 5", "WDR EVENT", "COSMO RADIO",
            "SWR1", "SWR 1", "SWR2", "SWR 2", "SWR3", "SWR 3", "SWR4", "SWR 4", "SWR KULTUR", "DASDING", "SWR AKTUELL",
            "BAYERN 1", "BAYERN 2", "BAYERN 3", "BR-KLASSIK", "BR KLASSIK", "BR24 RADIO", "BR24 LIVE", "BR SCHLAGER", "BR HEIMAT", "PULS RADIO",
            "NDR 1", "NDR 2", "NDR KULTUR", "NDR INFO", "N-JOY", "NJOY", "NDR 90,3", "NDR 90.3", "NDR PLUS", "NDR BLUE", "NDR SCHLAGER",
            "HR1", "HR 1", "HR2", "HR 2", "HR3", "HR 3", "HR4", "HR 4", "YOU FM", "YOUFM", "HR-INFO", "HR INFO",
            "MDR JUMP", "MDR AKTUELL", "MDR KULTUR", "MDR SPUTNIK", "MDR THÜRINGEN", "MDR SACHSEN", "MDR SACHSEN-ANHALT", "MDR SCHLAGERWELT", "MDR KLASSIK",
            "RBB 88.8", "RADIOEINS", "RADIO EINS", "FRITZ RADIO", "ANTENNE BRANDENBURG", "RBBKULTUR", "INFORADIO", "RBB 24 INFORADIO",
            "SR 1", "SR 2", "SR 3", "UNSERDING", "SR ANTENNE SAAR",
            "DEUTSCHLANDFUNK", "DLF", "DLF KULTUR", "DLF NOVA", "DOKUMENTE UND DEBATTEN",
            "ANTENNE BAYERN", "ROCK ANTENNE", "HIT RADIO FFH", "HITRADIO FFH", "RADIO BOB", "RADIO HAMBURG", "RADIO NRW", "RADIO FFN", "RPR1", "RPR 1",
            "BIGFM", "BIG FM", "SUNSHINE LIVE", "RADIO REGENBOGEN", "RADIO PSR", "R.SH", "ENERGY 93.3", "ENERGY NRJ", "DELTA RADIO", "STAR FM",
            "80S80S", "90S90S", "ROCKLAND RADIO", "SCHLAGERPARADIES", "RADIO PALOMA", "BALLERMANN RADIO", "KLASSIK RADIO", "JAZZ RADIO", "LOUNGE FM",
            "DEFJAY", "JAM FM", "PLANET RADIO", "HARMONY FM", "RADIO TON", "CHARIVARI", "GONG 96.3", "RADIO ARABELLA", "RADIO 7", "RADIO SALÜ",
            "RTL RADIO", "HITRADIO RTL", "RADIO BROCKEN", "RADIO SAW", "OSTSEEWELLE", "ANTENNE THÜRINGEN", "LANDESWELLE", "SCHWARZWALDRADIO",
            "EGOFM", "TEUTORADIO", "RADIO LIPPE", "RADIO BIELEFELD", "RADIO GÜTERSLOH", "RADIO HERFORD", "RADIO BONN", "RADIO KÖLN", "RADIO ERFT",
            "ANTENNE DÜSSELDORF", "RADIO DUISBURG", "RADIO ESSEN", "RADIO BOCHUM", "RADIO DORTMUND",
            "KRONEHIT", "HITRADIO Ö3", "HIT RADIO Ö3", "Ö1", "Ö2", "Ö3", "FM4", "RADIO WIEN", "RADIO TIROL", "RADIO SALZBURG", "RADIO STEIERMARK",
            "RADIO OÖ", "RADIO NÖ", "RADIO KÄRNTEN", "RADIO BURGENLAND", "RADIO VORARLBERG", "ANTENNE STEIERMARK", "ANTENNE KÄRNTEN", "ANTENNE SALZBURG",
            "ANTENNE VORARLBERG", "RADIO 88.6", "SUPERFLY FM", "LIFE RADIO", "WELLE 1", "ENERGY ÖSTERREICH", "LOUNGEFM ÖSTERREICH",
            "RADIO SRF 1", "RADIO SRF 2", "RADIO SRF 3", "RADIO SRF 4", "RADIO SRF MUSIKWELLE", "RADIO SRF VIRUS", "RADIO SWISS POP",
            "RADIO SWISS JAZZ", "RADIO SWISS CLASSIC", "RADIO 24", "ENERGY ZÜRICH", "RADIO ZÜRISEE", "RADIO ARGOVIA", "RADIO PILATUS",
            "RADIO TOP", "RADIO BASILISK", "RADIO FM1", "RADIO CENTRAL", "SUNSHINE RADIO", "RADIO 1"
        )

        for (st in knownRadioStations) {
            if (nameUpper.contains(st)) return true
        }

        // 4. General Keyword search
        if (nameUpper.contains("RADIO") || nameUpper.contains("WEBRADIO") ||
            nameUpper.contains("HITRADIO") || nameUpper.contains("ANTENNE ") ||
            nameUpper.contains(" FM") || nameUpper.contains("FM ") ||
            nameUpper.contains("DAB+") || nameUpper.contains("DAB ")
        ) {
            // Confirm it's not a HD TV channel (e.g., "Sky Cinema HD")
            if (!nameUpper.contains(" HD") && !nameUpper.contains(" FHD") &&
                !nameUpper.contains(" 4K") && !nameUpper.contains(" UHD")
            ) {
                return true
            }
        }

        return false
    }

    fun resolveRadioGroupTitle(
        name: String,
        group: String?,
        countryCode: String,
        isDach: Boolean
    ): String {
        if (!group.isNullOrBlank() && group != "All" && group != "Alle") {
            if (group.uppercase().contains("RADIO") || group.uppercase().contains("FM")) {
                return group
            }
        }
        val nameUpper = name.uppercase()
        return when {
            nameUpper.contains("ROCK") || nameUpper.contains("METAL") -> "Rock & Metal"
            nameUpper.contains("POP") || nameUpper.contains("HIT") || nameUpper.contains("CHARTS") -> "Pop & Charts"
            nameUpper.contains("KLASSIK") || nameUpper.contains("KULTUR") || nameUpper.contains("JAZZ") -> "Klassik & Kultur"
            nameUpper.contains("INFO") || nameUpper.contains("NEWS") || nameUpper.contains("AKTUELL") || nameUpper.contains("DLF") -> "Info & Nachrichten"
            nameUpper.contains("SCHLAGER") || nameUpper.contains("VOLK") -> "Schlager & Volksmusik"
            nameUpper.contains("ELECTRO") || nameUpper.contains("DANCE") || nameUpper.contains("SUNSHINE") -> "Electronic & Dance"
            countryCode == "DE" || isDach -> "DEUTSCHLAND | Radio"
            countryCode == "AT" -> "ÖSTERREICH | Radio"
            countryCode == "CH" -> "SCHWEIZ | Radio"
            else -> "INTERNATIONAL | Radio"
        }
    }

    private val FOREIGN_VOD_PREFIXES = listOf(
        "EN:", "EN :", "EN|", "EN |", "EN-", "EN -", "EN *", "EN ✪", "[EN]", "(EN)", "|EN|", "┃EN┃", "ENG:", "ENG|", "ENG -", "[ENG]", "ENGLISH",
        "US:", "US :", "US|", "US |", "US-", "US -", "US *", "[US]", "(US)", "|US|", "USA:", "USA|", "[USA]",
        "UK:", "UK :", "UK|", "UK |", "UK-", "UK -", "UK *", "[UK]", "(UK)", "|UK|", "GB:", "GB|", "[GB]",
        "TR:", "TR :", "TR|", "TR |", "TR-", "TR -", "TR *", "TR ✪", "[TR]", "(TR)", "|TR|", "┃TR┃", "TUR:", "TUR|", "TURK:", "TURKISH", "TÜRKIYE", "TURKEY", "YERLI", "YABANCI", "FILMLER", "DIZILER",
        "AR:", "AR :", "AR|", "AR |", "AR-", "AR -", "AR *", "[AR]", "(AR)", "|AR|", "ARA:", "ARA|", "ARABIC", "ARAB", "SHAHID", "AFLAM", "MOSALSALAT",
        "FR:", "FR :", "FR|", "FR |", "FR-", "FR -", "FR *", "[FR]", "(FR)", "|FR|", "FRA:", "FRA|", "FRENCH", "FRANCAIS", "FRANÇAIS", "VOSTFR", "VF:", "VF|",
        "ES:", "ES :", "ES|", "ES |", "ES-", "ES -", "ES *", "[ES]", "(ES)", "|ES|", "ESP:", "ESP|", "SPANISH", "ESPANOL", "ESPAÑOL", "LATINO", "LATAM", "PELICULAS", "CASTELLANO",
        "IT:", "IT :", "IT|", "IT |", "IT-", "IT -", "IT *", "[IT]", "(IT)", "|IT|", "ITA:", "ITA|", "ITALIAN", "ITALIANO",
        "NL:", "NL :", "NL|", "NL |", "NL-", "NL -", "NL *", "[NL]", "(NL)", "|NL|", "NED:", "DUTCH", "NEDERLAND",
        "PL:", "PL :", "PL|", "PL |", "PL-", "PL -", "PL *", "[PL]", "(PL)", "|PL|", "POL:", "POLISH", "POLSKA", "FILMY", "SERIALE", "LEKTOR",
        "RU:", "RU :", "RU|", "RU |", "RU-", "RU -", "RU *", "[RU]", "(RU)", "|RU|", "RUS:", "RUSSIAN",
        "PT:", "PT :", "PT|", "PT |", "PT -", "BR:", "BR :", "BR|", "BR |", "BR -", "[PT]", "[BR]", "(PT)", "(BR)", "PORTUGAL", "BRAZIL", "BRASIL", "DUBLADO", "LEGENDADO",
        "EXYU", "EX-YU", "EX YU", "BALKAN", "SRB:", "SRB|", "HR:", "HR|", "BIH:", "BIH|", "FILMOVI", "SERIJE", "DOMACI",
        "AL:", "AL|", "ALB:", "ALB|", "[AL]", "[ALB]", "SHQIP", "ALBANIA", "KOSOVO", "FILMA",
        "GR:", "GR|", "GR -", "[GR]", "GREEK", "GREECE",
        "RO:", "RO|", "RO -", "[RO]", "ROMANIA", "SUBTITRAT",
        "IN:", "IND:", "HINDI", "PUNJABI", "TAMIL", "TELUGU", "MALAYALAM",
        "KR:", "KOREAN", "CN:", "CHINESE", "JP:", "JAPANESE", "TH:", "THAI",
        "SE:", "NO:", "DK:", "FI:", "SWEDISH", "NORWEGIAN", "DANISH", "FINNISH", "NORDIC", "SCANDINAVIA",
        "CZ:", "CZ|", "SK:", "SK|", "CZ/SK", "HU:", "HU|", "BG:", "BG|", "UA:", "UA|", "AF:", "IR:", "KURD"
    )

    private val GERMAN_VOD_KEYWORDS = listOf(
        // Explicit DACH & German identifiers
        "DEUTSCH", "DEUTSCHE", "GERMAN", "GERMANY", "DEUTSCHLAND", "ÖSTERREICH", "OESTERREICH", "SCHWEIZ", "SWISS", "AUSTRIA",
        "D-A-CH", "DACH", "DE:", "DE :", "DE|", "DE |", "DE┃", "DE ┃", "DE -", "DE –", "DE *", "DE ✪", "DE ★", "DE ❖", "DE •",
        "[DE]", "(DE)", "{DE}", "|DE|", "┃DE┃", "║DE║", "【DE】", "GER:", "GER|", "GER┃", "GER -", "[GER]", "(GER)", "|GER|", "┃GER┃",
        "DEU:", "DEU|", "AT:", "AT :", "AT|", "AT |", "AT┃", "AT -", "[AT]", "|AT|", "┃AT┃",
        "CH:", "CH :", "CH|", "CH |", "CH┃", "CH -", "[CH]", "|CH|", "┃CH┃",
        "VOD DE", "VOD | DE", "VOD - DE", "SRS DE", "SRS | DE", "SRS - DE",
        "FILME DE", "FILME", "SERIEN DE", "SERIEN", "KINO DE", "KINO", "KINOFILME", "CINEMA DE", "CINEMA",
        "DEUTSCHE FILME", "DEUTSCHE SERIEN", "4K DE", "UHD DE", "FHD DE", "HEVC DE", "DE_FILME", "DE_SERIEN",
        // Streaming Platforms & Studios
        "NETFLIX", "DISNEY+", "DISNEY", "AMAZON", "PRIME", "APPLE TV", "APPLE+", "SKY CINEMA", "SKY",
        "PARAMOUNT+", "PARAMOUNT", "WOW", "RTL+", "JOYN", "ARD MEDIATHEK", "ZDF MEDIATHEK", "HBO", "MAX", "MARVEL", "DC",
        // All VOD & Series Genre Subcategories (German & International Genre Names)
        "HORROR", "COMEDY", "KOMÖDIE", "KOMOEDIE", "ACTION", "THRILLER", "DRAMA", "DRAMEN",
        "SCI-FI", "SCIFI", "SCIENCE FICTION", "SCIENCE-FICTION", "ANIMATION", "ANIME", "ZEICHENTRICK",
        "FANTASY", "ROMANCE", "ROMANTIK", "LIEBESFILM", "LIEBESFILME", "CRIME", "KRIMI", "KRIMINAL",
        "ADVENTURE", "ABENTEUER", "FAMILY", "FAMILIE", "FAMILIENFILM", "KIDS", "KINDER", "KINDERFILME",
        "MYSTERY", "WESTERN", "WAR", "KRIEG", "KRIEGSFILM", "KRIEGSFILME", "HISTORY", "HISTORIE", "HISTORISCH",
        "BIOGRAPHY", "BIOGRAFIE", "MUSIC", "MUSIK", "MUSICAL", "DOCUMENTARY", "DOKUMENTATION", "DOKU",
        "SPORT", "BLOCKBUSTER", "NEUERSCHEINUNGEN", "NEUHEITEN", "HEIMAT", "HEIMATFILME",
        "KLASSIKER", "CLASSIC", "CLASSICS", "KULT", "CULT", "COLLECTION", "COLLECTIONS", "FILMREIHEN",
        "BOX OFFICE", "TOP ", "IMDB", "OSCAR", "WEIHNACHTEN", "CHRISTMAS", "HALLOWEEN", "BOLLYWOOD",
        "4K", "UHD", "FHD", "3D", "HEVC", "VIP",
        "2026", "2025", "2024", "2023", "2022", "2021", "2020", "2019", "2018",
        "MOVIES", "SERIES", "STAFFEL", "TV SHOW", "UNTERHALTUNG", "ENTERTAINMENT"
    )

    fun hasExplicitForeignVodMarker(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val upper = text.trim().uppercase()
        if (upper.contains("🇩🇪") || upper.contains("🇦🇹") || upper.contains("🇨🇭")) return false

        val prefixCode = extractCountryCodeFromPrefix(upper)
        if (prefixCode in listOf("DE", "AT", "CH")) return false
        if (prefixCode != null && prefixCode !in listOf("DE", "AT", "CH", "ADULT", "SPORT", "VIP", "OTHER", "INT")) {
            return true
        }

        val catCountry = com.example.data.country.CountryNormalizer.extractCountryFromCategory(text)
        if (catCountry in listOf("DE", "AT", "CH")) return false
        if (catCountry != null && catCountry !in listOf("DE", "AT", "CH", "ADULT", "SPORT", "VIP", "OTHER", "INT")) {
            return true
        }

        return FOREIGN_VOD_PREFIXES.any { marker ->
            upper.startsWith(marker) ||
                upper.contains(" $marker") ||
                upper.contains("|$marker") ||
                upper.contains("┃$marker") ||
                upper.contains("-$marker") ||
                upper.contains(":$marker")
        }
    }

    fun isGermanOrDachVodCategory(groupTitle: String?): Boolean {
        if (groupTitle.isNullOrBlank()) return false
        val upper = groupTitle.trim().uppercase()

        if (upper.contains("🇩🇪") || upper.contains("🇦🇹") || upper.contains("🇨🇭")) return true

        val prefixCountry = extractCountryCodeFromPrefix(upper)
        if (prefixCountry in listOf("DE", "AT", "CH")) return true
        if (prefixCountry != null && prefixCountry !in listOf("DE", "AT", "CH", "ADULT", "SPORT", "VIP", "OTHER", "INT")) return false

        val catCountry = com.example.data.country.CountryNormalizer.extractCountryFromCategory(groupTitle)
            ?: detectCountryForGroup(groupTitle).takeIf { it !in listOf("INT", "SPORT", "VIP", "ADULT", "OTHER", "UNKNOWN") }

        if (catCountry in listOf("DE", "AT", "CH")) return true
        if (catCountry != null && catCountry !in listOf("DE", "AT", "CH")) return false

        if (hasExplicitForeignVodMarker(groupTitle)) {
            return false
        }

        return GERMAN_VOD_KEYWORDS.any { upper.contains(it) }
    }

    fun isGermanOrDachVodItem(name: String, groupTitle: String?): Boolean {
        val upperName = name.trim().uppercase()
        if (upperName.contains("🇩🇪") || upperName.contains("🇦🇹") || upperName.contains("🇨🇭")) return true

        if (isGermanOrDachVodCategory(groupTitle)) {
            if (!hasExplicitForeignVodMarker(name)) return true
        }

        if (hasExplicitForeignVodMarker(name)) return false

        return upperName.startsWith("DE:") || upperName.startsWith("DE :") || upperName.startsWith("DE|") ||
            upperName.startsWith("DE |") || upperName.startsWith("DE -") || upperName.startsWith("DE *") ||
            upperName.startsWith("DE ✪") || upperName.startsWith("[DE]") || upperName.startsWith("(DE)") ||
            upperName.startsWith("GER:") || upperName.startsWith("GER|") || upperName.startsWith("GER -") ||
            upperName.startsWith("[GER]") || upperName.startsWith("(GER)") ||
            upperName.startsWith("AT:") || upperName.startsWith("AT|") || upperName.startsWith("AT -") ||
            upperName.startsWith("CH:") || upperName.startsWith("CH|") || upperName.startsWith("CH -") ||
            upperName.endsWith(" (DE)") || upperName.endsWith(" [DE]") || upperName.endsWith(" DE") ||
            upperName.endsWith(" (GER)") || upperName.endsWith(" [GER]") || upperName.endsWith(" GER") ||
            upperName.contains(" GERMAN ") || upperName.endsWith(" GERMAN") || upperName.contains(".GERMAN.") ||
            upperName.contains(" DEUTSCH ") || upperName.endsWith(" DEUTSCH") || upperName.contains(".DEUTSCH.") ||
            upperName.contains(".GER.") || upperName.contains(".DL.")
    }

    /**
     * Checks whether a provider category matches the requested set of allowed country codes.
     * Supports priority countries (DE, AT, CH, ADULT) and on-demand countries for LiveTV, Movies, and Series.
     */
    fun isCategoryMatchingCountries(
        groupTitle: String?,
        allowedCountries: Set<String>,
        isVodOrSeries: Boolean = false
    ): Boolean {
        if (allowedCountries.isEmpty() || "ALL" in allowedCountries || "ALLE" in allowedCountries) return true
        if (groupTitle.isNullOrBlank()) return false

        val normalizedAllowed = allowedCountries.map { it.uppercase().trim() }.toSet()

        if ("ADULT" in normalizedAllowed && isAdultContent("", groupTitle)) {
            return true
        }

        val wantsDach = normalizedAllowed.any { it in listOf("DE", "AT", "CH") }
        if (isVodOrSeries && wantsDach && isGermanOrDachVodCategory(groupTitle)) {
            val specificCatCountry = detectCountryForGroup(groupTitle)
            if (specificCatCountry in listOf("DE", "AT", "CH")) {
                return specificCatCountry in normalizedAllowed
            }
            return "DE" in normalizedAllowed || wantsDach
        }

        val catCountry = com.example.data.country.CountryNormalizer.extractCountryFromCategory(groupTitle)
            ?: detectCountryForGroup(groupTitle).takeIf { it !in listOf("INT", "SPORT", "VIP", "OTHER", "UNKNOWN") }

        if (catCountry != null) {
            val upperCat = catCountry.uppercase()
            if (upperCat in normalizedAllowed) return true
            if (upperCat == "GB" && "UK" in normalizedAllowed) return true
            if (upperCat == "UK" && "GB" in normalizedAllowed) return true
            if (upperCat == "AL" && "ALB" in normalizedAllowed) return true
            if (upperCat == "ALB" && "AL" in normalizedAllowed) return true
            if (upperCat == "EN" && ("UK" in normalizedAllowed || "US" in normalizedAllowed)) return true
            return false
        }

        val upper = groupTitle.trim().uppercase()
        if (wantsDach && (upper.contains("DACH") || upper.contains("D-A-CH") || upper.contains("DE/AT/CH"))) {
            return true
        }

        if (isVodOrSeries) {
            if (("US" in normalizedAllowed || "UK" in normalizedAllowed) &&
                (upper.contains("ENGLISH") || upper.contains("VOD EN") || upper.contains("MOVIES EN") || upper.contains("SERIES EN") || upper.startsWith("EN:") || upper.startsWith("EN |"))
            ) {
                return true
            }
        }

        return false
    }

    fun clearCache() {
        cleanSortTitleCache.evictAll()
        prefixCountryCache.evictAll()
        categoryPriorityCache.evictAll()
        categorySubRankCache.evictAll()
        normalizedCountryCodeCache.evictAll()
    }
}


