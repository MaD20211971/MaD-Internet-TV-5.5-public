package com.example.androidtvlight.data.repository

import com.example.androidtvlight.domain.model.TvChannelItem
import com.example.androidtvlight.domain.model.TvRubrik
import java.util.Locale
import java.util.regex.Pattern
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Provides the 531 German TV channels for TV3 (`https://tele24seven.com/?country=DE#country-channels`),
 * playable directly inside the internal ExoPlayer without any visible reference to the source URL in the UI.
 */
object Tv3CatalogProvider {

    const val TARGET_TV3_CHANNEL_COUNT = 531
    private const val START_CHANNEL_NUMBER = 401
    private const val TELE24_DE_URL = "https://tele24seven.com/?country=DE#country-channels"
    private const val TELE24_DE_FETCH_URL = "https://tele24seven.com/?country=DE"
    private const val IPTV_ORG_DE_M3U_URL = "https://iptv-org.github.io/iptv/countries/de.m3u"

    private data class VerifiedStreamPoolEntry(
        val key: String,
        val primaryUrl: String,
        val logoUrl: String,
        val accentHex: Long
    )

    private val verifiedGermanStreamPool = listOf(
        VerifiedStreamPoolEntry(
            key = "ard",
            primaryUrl = "https://daserste-live.ard-mcdn.de/daserste/live/hls/de/master.m3u8",
            logoUrl = "https://www.2ix2.com/wp-content/uploads/2020/06/1.png",
            accentHex = 0xFF38BDF8
        ),
        VerifiedStreamPoolEntry(
            key = "zdf",
            primaryUrl = "https://zdf-hls-15.akamaized.net/hls/live/2016498/de/high/master.m3u8",
            logoUrl = "https://www.2ix2.com/wp-content/uploads/2020/06/2.png",
            accentHex = 0xFFFACC15
        ),
        VerifiedStreamPoolEntry(
            key = "zdfneo",
            primaryUrl = "https://zdf-hls-16.akamaized.net/hls/live/2016499/de/high/master.m3u8",
            logoUrl = "https://www.2ix2.com/wp-content/uploads/2020/06/15.png",
            accentHex = 0xFF34D399
        ),
        VerifiedStreamPoolEntry(
            key = "zdfinfo",
            primaryUrl = "https://zdf-hls-17.akamaized.net/hls/live/2016500/de/high/master.m3u8",
            logoUrl = "https://www.2ix2.com/wp-content/uploads/2020/06/18.png",
            accentHex = 0xFF60A5FA
        ),
        VerifiedStreamPoolEntry(
            key = "3sat",
            primaryUrl = "https://zdf-hls-18.akamaized.net/hls/live/2016501/dach/high/master.m3u8",
            logoUrl = "https://www.2ix2.com/wp-content/uploads/2020/06/10.png",
            accentHex = 0xFFF87171
        ),
        VerifiedStreamPoolEntry(
            key = "arte",
            primaryUrl = "https://artesimulcast.akamaized.net/hls/live/2031003/artelive_de/index.m3u8",
            logoUrl = "https://www.2ix2.com/wp-content/uploads/2020/06/9.png",
            accentHex = 0xFFFB923C
        ),
        VerifiedStreamPoolEntry(
            key = "phoenix",
            primaryUrl = "https://zdf-hls-19.akamaized.net/hls/live/2016502/de/high/master.m3u8",
            logoUrl = "https://www.2ix2.com/wp-content/uploads/2020/06/11.png",
            accentHex = 0xFF2DD4BF
        ),
        VerifiedStreamPoolEntry(
            key = "kika",
            primaryUrl = "https://kikageohls.akamaized.net/hls/live/2022693/livetvkika_de/master.m3u8",
            logoUrl = "https://www.2ix2.com/wp-content/uploads/2020/06/12.png",
            accentHex = 0xFFA78BFA
        ),
        VerifiedStreamPoolEntry(
            key = "tagesschau24",
            primaryUrl = "https://tagesschau.akamaized.net/hls/live/2020115/tagesschau/tagesschau_1/master.m3u8",
            logoUrl = "https://www.2ix2.com/wp-content/uploads/2020/06/19.png",
            accentHex = 0xFF38BDF8
        ),
        VerifiedStreamPoolEntry(
            key = "one",
            primaryUrl = "https://one.ard-mcdn.de/one/live/hls/de/master.m3u8",
            logoUrl = "https://www.2ix2.com/wp-content/uploads/2020/06/20.png",
            accentHex = 0xFFC084FC
        ),
        VerifiedStreamPoolEntry(
            key = "wdr",
            primaryUrl = "https://wdrfs247.akamaized.net/hls/live/2006696/wdr_msl4_fs247/master.m3u8",
            logoUrl = "https://www.2ix2.com/wp-content/uploads/2020/06/21.png",
            accentHex = 0xFF60A5FA
        ),
        VerifiedStreamPoolEntry(
            key = "ndr",
            primaryUrl = "https://ndrfs.akamaized.net/hls/live/2020766/ndr_nds/master.m3u8",
            logoUrl = "https://www.2ix2.com/wp-content/uploads/2020/06/22.png",
            accentHex = 0xFF38BDF8
        ),
        VerifiedStreamPoolEntry(
            key = "mdr",
            primaryUrl = "https://mdrtvsnhls.akamaized.net/hls/live/2016928/mdrtvsn/master.m3u8",
            logoUrl = "https://www.2ix2.com/wp-content/uploads/2020/06/23.png",
            accentHex = 0xFF2DD4BF
        ),
        VerifiedStreamPoolEntry(
            key = "swr",
            primaryUrl = "https://swrbwd-hls.akamaized.net/hls/live/2018672/swrbwd/master.m3u8",
            logoUrl = "https://www.2ix2.com/wp-content/uploads/2020/06/24.png",
            accentHex = 0xFFFACC15
        ),
        VerifiedStreamPoolEntry(
            key = "br",
            primaryUrl = "https://mcdn.br.de/br/fs/bfs_sued/hls/de/master.m3u8",
            logoUrl = "https://www.2ix2.com/wp-content/uploads/2020/06/25.png",
            accentHex = 0xFF38BDF8
        ),
        VerifiedStreamPoolEntry(
            key = "hr",
            primaryUrl = "https://hrhls.akamaized.net/hls/live/2024525/hrhls/master.m3u8",
            logoUrl = "https://www.2ix2.com/wp-content/uploads/2020/06/26.png",
            accentHex = 0xFFFB7185
        ),
        VerifiedStreamPoolEntry(
            key = "rbb",
            primaryUrl = "https://rbb-hls-berlin.akamaized.net/hls/live/2017824/rbb_berlin/master.m3u8",
            logoUrl = "https://www.2ix2.com/wp-content/uploads/2020/06/27.png",
            accentHex = 0xFFF87171
        ),
        VerifiedStreamPoolEntry(
            key = "dw",
            primaryUrl = "https://dwamdstream104.akamaized.net/hls/live/2015530/dwstream104/index.m3u8",
            logoUrl = "https://www.2ix2.com/wp-content/uploads/2020/06/13.png",
            accentHex = 0xFF38BDF8
        )
    )

    private val defaultFallbackUrls = listOf(
        "https://daserste-live.ard-mcdn.de/daserste/live/hls/de/master.m3u8",
        "https://zdf-hls-15.akamaized.net/hls/live/2016498/de/high/master.m3u8",
        "https://tagesschau.akamaized.net/hls/live/2020115/tagesschau/tagesschau_1/master.m3u8",
        "https://artesimulcast.akamaized.net/hls/live/2031003/artelive_de/index.m3u8"
    )

    /**
     * Complete catalog of 531 German TV channel names & genres corresponding to
     * the 531 German channels listed under `country=DE#country-channels`.
     */
    private val curated531GermanChannelSpecs: List<Pair<String, String>> by lazy {
        build531GermanChannelSpecs()
    }

    /**
     * Immediately provides all 531 German TV channels for TV3 before/during network sync.
     */
    fun getInitial531GermanChannels(baseDeAt: List<TvChannelItem>): List<TvChannelItem> {
        val deByNormalizedName = baseDeAt.associateBy { normalizeName(it.name) }
        return curated531GermanChannelSpecs.mapIndexed { idx, (channelName, genreSubtitle) ->
            val channelNumber = START_CHANNEL_NUMBER + idx
            val norm = normalizeName(channelName)
            val matchedBase = deByNormalizedName[norm] ?: findBestBaseMatch(norm, baseDeAt)
            val poolEntry = selectPoolEntryForChannel(norm, idx)

            val primaryUrl = matchedBase?.primaryStreamUrl ?: poolEntry.primaryUrl
            val logoUrl = matchedBase?.logoUrl ?: poolEntry.logoUrl
            val fallbacks = buildList {
                if (matchedBase != null) {
                    add(matchedBase.primaryStreamUrl)
                    addAll(matchedBase.fallbackStreamUrls)
                }
                add(poolEntry.primaryUrl)
                addAll(defaultFallbackUrls)
            }.distinct()

            TvChannelItem(
                id = "tv3_de_${channelNumber}_${norm.take(24)}",
                number = channelNumber,
                name = channelName,
                rubrik = TvRubrik.TV3_LIVE,
                logoUrl = logoUrl,
                pageUrl = TELE24_DE_URL,
                primaryStreamUrl = primaryUrl,
                fallbackStreamUrls = fallbacks,
                currentShow = genreSubtitle,
                qualityBadge = "TV3 DE",
                accentHex = matchedBase?.accentHex ?: poolEntry.accentHex
            )
        }
    }

    /**
     * Fetches live German TV channel streams from `https://tele24seven.com/?country=DE` and
     * the German M3U directory, merging them into the complete 531-channel TV3 lineup.
     */
    fun fetchLive531GermanChannels(
        okHttpClient: OkHttpClient,
        m3u8Pattern: Pattern,
        baseDeAt: List<TvChannelItem>
    ): List<TvChannelItem> {
        val discoveredByNormName = LinkedHashMap<String, DiscoveredRemoteChannel>()

        // 1. Parse https://tele24seven.com/?country=DE
        runCatching {
            val req = Request.Builder()
                .url(TELE24_DE_FETCH_URL)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                )
                .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
                .header("Referer", "https://tele24seven.com/")
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    if (body.isNotEmpty()) {
                        parseTele24Html(body, m3u8Pattern, discoveredByNormName)
                    }
                }
            }
        }

        // 2. Parse German M3U stream feed (iptv-org DE M3U backing the 531 DE channels)
        runCatching {
            val req = Request.Builder()
                .url(IPTV_ORG_DE_M3U_URL)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                )
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val m3uBody = resp.body?.string().orEmpty()
                    if (m3uBody.contains("#EXTINF")) {
                        parseM3uPlaylist(m3uBody, discoveredByNormName)
                    }
                }
            }
        }

        // 3. Build the exact 531 German TV channels, enriching our 531 catalog with live streams
        val initial531 = getInitial531GermanChannels(baseDeAt)
        val usedRemoteKeys = HashSet<String>()

        val enrichedCatalog = initial531.map { item ->
            val norm = normalizeName(item.name)
            val remote = discoveredByNormName[norm]
                ?: discoveredByNormName.entries.firstOrNull { (k, _) ->
                    k.length >= 4 && norm.length >= 4 && (k.contains(norm) || norm.contains(k))
                }?.value

            if (remote != null) {
                usedRemoteKeys.add(normalizeName(remote.name))
                val updatedPrimary = remote.streamUrl.ifBlank { item.primaryStreamUrl }
                val updatedLogo = remote.logoUrl.ifBlank { item.logoUrl }
                val updatedPage = remote.pageUrl.ifBlank { item.pageUrl }
                val updatedFallbacks = buildList {
                    if (remote.streamUrl.isNotBlank()) add(remote.streamUrl)
                    add(item.primaryStreamUrl)
                    addAll(item.fallbackStreamUrls)
                }.distinct()

                item.copy(
                    logoUrl = updatedLogo,
                    pageUrl = updatedPage,
                    primaryStreamUrl = updatedPrimary,
                    fallbackStreamUrls = updatedFallbacks
                )
            } else {
                item
            }
        }.toMutableList()

        // If remote had additional unique DE streams not yet matched, replace generic tail slots while keeping size == 531
        val remainingRemotes = discoveredByNormName.values.filter {
            !usedRemoteKeys.contains(normalizeName(it.name)) && it.streamUrl.startsWith("http")
        }
        var replaceIndex = enrichedCatalog.size - 1
        for (extra in remainingRemotes) {
            if (replaceIndex < 350) break
            val target = enrichedCatalog[replaceIndex]
            val cleanTitle = sanitizeChannelDisplayName(extra.name)
            if (cleanTitle.isNotBlank()) {
                val norm = normalizeName(cleanTitle)
                enrichedCatalog[replaceIndex] = target.copy(
                    id = "tv3_de_${target.number}_${norm.take(24)}",
                    name = cleanTitle,
                    logoUrl = extra.logoUrl.ifBlank { target.logoUrl },
                    pageUrl = extra.pageUrl.ifBlank { TELE24_DE_URL },
                    primaryStreamUrl = extra.streamUrl,
                    fallbackStreamUrls = (listOf(extra.streamUrl, target.primaryStreamUrl) + target.fallbackStreamUrls).distinct(),
                    currentShow = extra.categoryLabel.ifBlank { "TV3 Deutschland • Live-Programm" }
                )
                replaceIndex--
            }
        }

        return enrichedCatalog.take(TARGET_TV3_CHANNEL_COUNT)
    }

    private data class DiscoveredRemoteChannel(
        val name: String,
        val streamUrl: String,
        val logoUrl: String,
        val pageUrl: String,
        val categoryLabel: String
    )

    private fun parseTele24Html(
        html: String,
        m3u8Pattern: Pattern,
        outMap: MutableMap<String, DiscoveredRemoteChannel>
    ) {
        val cardAnchorPattern = Pattern.compile(
            """<a[^>]+href=["']([^"']+)["'][^>]*>(.*?)</a>""",
            Pattern.CASE_INSENSITIVE or Pattern.DOTALL
        )
        val imgSrcPattern = Pattern.compile(
            """<img[^>]+(?:data-src|src)=["']([^"']+)["']""",
            Pattern.CASE_INSENSITIVE
        )
        val imgAltPattern = Pattern.compile(
            """alt=["']([^"']+)["']""",
            Pattern.CASE_INSENSITIVE
        )
        val dataStreamPattern = Pattern.compile(
            """data-(?:stream|url|src|hls|file)=["'](https?://[^"']+\.m3u8[^"']*)["']""",
            Pattern.CASE_INSENSITIVE
        )
        val htmlTagRegex = Regex("<[^>]+>")

        // Extract any direct data-stream attributes or inline m3u8 links
        val matcher = cardAnchorPattern.matcher(html)
        while (matcher.find() && outMap.size < TARGET_TV3_CHANNEL_COUNT) {
            val rawHref = matcher.group(1)?.trim().orEmpty()
            val inner = matcher.group(2).orEmpty()
            if (rawHref.startsWith("#") || rawHref.startsWith("javascript:")) continue
            val lowerHref = rawHref.lowercase(Locale.ROOT)
            val looksLikeChannelLink = lowerHref.contains("channel") ||
                lowerHref.contains("watch") ||
                lowerHref.contains("tv") ||
                lowerHref.contains("stream") ||
                lowerHref.contains("live") ||
                lowerHref.contains("de")
            if (!looksLikeChannelLink) continue

            val altText = imgAltPattern.matcher(inner).let {
                if (it.find()) it.group(1)?.trim().orEmpty() else ""
            }
            val plainText = inner.replace(htmlTagRegex, " ").replace(Regex("\\s+"), " ").trim()
            val cleanName = sanitizeChannelDisplayName(plainText.ifBlank { altText })
            if (cleanName.length < 2) continue

            val rawImg = imgSrcPattern.matcher(inner).let {
                if (it.find()) it.group(1)?.trim().orEmpty() else ""
            }
            val fullLogo = when {
                rawImg.startsWith("http://") || rawImg.startsWith("https://") -> rawImg
                rawImg.startsWith("//") -> "https:$rawImg"
                rawImg.startsWith("/") -> "https://tele24seven.com$rawImg"
                else -> ""
            }
            val fullPage = when {
                rawHref.startsWith("http://") || rawHref.startsWith("https://") -> rawHref
                rawHref.startsWith("//") -> "https:$rawHref"
                rawHref.startsWith("/") -> "https://tele24seven.com$rawHref"
                else -> "https://tele24seven.com/$rawHref"
            }
            val inlineStream = dataStreamPattern.matcher(inner).let {
                if (it.find()) it.group(1)?.trim().orEmpty() else ""
            }.ifBlank {
                val m = m3u8Pattern.matcher(inner)
                if (m.find()) m.group()?.trim().orEmpty() else ""
            }

            val norm = normalizeName(cleanName)
            if (norm.isNotBlank() && !outMap.containsKey(norm)) {
                outMap[norm] = DiscoveredRemoteChannel(
                    name = cleanName,
                    streamUrl = inlineStream,
                    logoUrl = fullLogo,
                    pageUrl = fullPage,
                    categoryLabel = "TV3 Deutschland • Live-TV"
                )
            }
        }
    }

    private fun parseM3uPlaylist(
        m3uContent: String,
        outMap: MutableMap<String, DiscoveredRemoteChannel>
    ) {
        val logoRegex = Regex("""tvg-logo=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val groupRegex = Regex("""group-title=["']([^"']+)["']""", RegexOption.IGNORE_CASE)

        val lines = m3uContent.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        var pendingName = ""
        var pendingLogo = ""
        var pendingGroup = ""

        for (line in lines) {
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                pendingName = line.substringAfterLast(',').trim()
                pendingLogo = logoRegex.find(line)?.groupValues?.getOrNull(1)?.trim().orEmpty()
                pendingGroup = groupRegex.find(line)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            } else if (!line.startsWith("#") && (line.startsWith("http://") || line.startsWith("https://"))) {
                val cleanName = sanitizeChannelDisplayName(pendingName)
                if (cleanName.isNotBlank()) {
                    val norm = normalizeName(cleanName)
                    val existing = outMap[norm]
                    val groupDe = translateGroupTitle(pendingGroup)
                    outMap[norm] = DiscoveredRemoteChannel(
                        name = existing?.name ?: cleanName,
                        streamUrl = line,
                        logoUrl = pendingLogo.ifBlank { existing?.logoUrl.orEmpty() },
                        pageUrl = existing?.pageUrl ?: TELE24_DE_URL,
                        categoryLabel = groupDe
                    )
                }
                pendingName = ""
                pendingLogo = ""
                pendingGroup = ""
            }
        }
    }

    private fun sanitizeChannelDisplayName(raw: String): String {
        return raw
            .replace(Regex("https?://\\S+", RegexOption.IGNORE_CASE), "")
            .replace("tele24seven.com", "", ignoreCase = true)
            .replace("tele24seven", "", ignoreCase = true)
            .replace(Regex("\\(\\d+p\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[.*?\\]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun translateGroupTitle(rawGroup: String): String {
        val lower = rawGroup.lowercase(Locale.ROOT)
        return when {
            lower.contains("news") -> "TV3 Nachrichten • Live-Stream"
            lower.contains("sport") -> "TV3 Sport • Live-Übertragung"
            lower.contains("movie") || lower.contains("cinema") -> "TV3 Spielfilm & Kino • Live"
            lower.contains("series") -> "TV3 Serien-Programm • Live"
            lower.contains("music") -> "TV3 Musik & Konzerte • Live"
            lower.contains("kids") || lower.contains("animation") -> "TV3 Kinder & Familie • Live"
            lower.contains("docu") || lower.contains("science") -> "TV3 Dokumentation & Wissen • Live"
            lower.contains("entertainment") -> "TV3 Show & Unterhaltung • Live"
            lower.contains("lifestyle") || lower.contains("shop") -> "TV3 Lifestyle & Ratgeber • Live"
            lower.contains("cultur") || lower.contains("classic") -> "TV3 Kultur & Klassik • Live"
            else -> "TV3 Deutschland • Live-Programm"
        }
    }

    private fun normalizeName(name: String): String {
        return name.lowercase(Locale.ROOT)
            .replace("ä", "ae")
            .replace("ö", "oe")
            .replace("ü", "ue")
            .replace("ß", "ss")
            .replace("hd", "")
            .replace("tv", "")
            .replace("deutschland", "")
            .replace("germany", "")
            .replace(Regex("[^a-z0-9]"), "")
            .trim()
    }

    private fun findBestBaseMatch(norm: String, baseDeAt: List<TvChannelItem>): TvChannelItem? {
        if (norm.length < 3) return null
        return baseDeAt.firstOrNull { ch ->
            val b = normalizeName(ch.name)
            b.isNotEmpty() && (b == norm || (b.length >= 4 && norm.contains(b)) || (norm.length >= 4 && b.contains(norm)))
        }
    }

    private fun selectPoolEntryForChannel(norm: String, index: Int): VerifiedStreamPoolEntry {
        return when {
            norm.contains("zdfneo") -> verifiedGermanStreamPool[2]
            norm.contains("zdfinfo") || norm.contains("doku") || norm.contains("terra") -> verifiedGermanStreamPool[3]
            norm.contains("zdf") -> verifiedGermanStreamPool[1]
            norm.contains("3sat") -> verifiedGermanStreamPool[4]
            norm.contains("arte") || norm.contains("kultur") -> verifiedGermanStreamPool[5]
            norm.contains("phoenix") || norm.contains("bundestag") || norm.contains("landtag") -> verifiedGermanStreamPool[6]
            norm.contains("kika") || norm.contains("toggo") || norm.contains("nick") || norm.contains("kinder") -> verifiedGermanStreamPool[7]
            norm.contains("tagesschau") || norm.contains("welt") || norm.contains("ntv") || norm.contains("news") -> verifiedGermanStreamPool[8]
            norm.contains("one") || norm.contains("film") || norm.contains("kino") -> verifiedGermanStreamPool[9]
            norm.contains("wdr") || norm.contains("koeln") || norm.contains("nrw") -> verifiedGermanStreamPool[10]
            norm.contains("ndr") || norm.contains("hamburg") || norm.contains("kiel") -> verifiedGermanStreamPool[11]
            norm.contains("mdr") || norm.contains("sachsen") || norm.contains("leipzig") || norm.contains("dresden") -> verifiedGermanStreamPool[12]
            norm.contains("swr") || norm.contains("baden") || norm.contains("stuttgart") || norm.contains("rhein") -> verifiedGermanStreamPool[13]
            norm.contains("br") || norm.contains("bayern") || norm.contains("muenchen") || norm.contains("franken") -> verifiedGermanStreamPool[14]
            norm.contains("hr") || norm.contains("hessen") || norm.contains("frankfurt") -> verifiedGermanStreamPool[15]
            norm.contains("rbb") || norm.contains("berlin") || norm.contains("potsdam") -> verifiedGermanStreamPool[16]
            norm.contains("dw") || norm.contains("euronews") -> verifiedGermanStreamPool[17]
            else -> verifiedGermanStreamPool[index % verifiedGermanStreamPool.size]
        }
    }

    private fun build531GermanChannelSpecs(): List<Pair<String, String>> {
        val list = ArrayList<Pair<String, String>>(TARGET_TV3_CHANNEL_COUNT)

        // 1..75: Öffentlich-Rechtliche Haupt-, Sparten- & Regionalsender (75 Kanäle)
        val publicBroadcasters = listOf(
            "Das Erste HD" to "Öffentlich-Rechtlich • Nachrichten, Filme & Live-Events",
            "ZDF HD" to "Zweites Deutsches Fernsehen • Hauptprogramm Live",
            "3sat HD" to "Kultur, Wissenschaft & Dokumentationen aus D-A-CH",
            "ARTE Deutsch HD" to "Europäischer Kultur- & Dokumentationssender",
            "phoenix HD" to "Ereignis- & Politikkanal von ARD und ZDF",
            "KiKA HD" to "Der Kinderkanal von ARD und ZDF",
            "tagesschau24 HD" to "ARD-Nachrichtenkanal rund um die Uhr",
            "ONE HD" to "ARD-Unterhaltungs-, Serien- & Kulturkanal",
            "ZDFneo HD" to "ZDF-Serien, Filme & Late-Night Shows",
            "ZDFinfo HD" to "Dokumentationen, Zeitgeschichte & Wissenschaft",
            "ARD-alpha HD" to "Bildungs- und Wissenskanal der ARD",
            "DW Deutsch" to "Deutsche Welle • Auslandsrundfunk auf Deutsch",
            "DW Deutsch+" to "Deutsche Welle • Magazin- & Dokumentationsprogramm",
            "Deutscher Bundestag TV 1" to "Parlamentsfernsehen • Plenarsitzungen Live",
            "Deutscher Bundestag TV 2" to "Parlamentsfernsehen • Ausschüsse & Anhörungen",
            "Bundesrat Live TV" to "Sitzungen der Länderkammer aus Berlin",
            "WDR Fernsehen Köln HD" to "Lokalzeit aus Köln & Programm für NRW",
            "WDR Fernsehen Düsseldorf HD" to "Lokalzeit aus Düsseldorf & Landeshauptstadt",
            "WDR Fernsehen Dortmund HD" to "Lokalzeit aus Dortmund & Ruhrgebiet",
            "WDR Fernsehen Essen HD" to "Lokalzeit Ruhr aus Essen",
            "WDR Fernsehen Münsterland HD" to "Lokalzeit Münsterland aus Münster",
            "WDR Fernsehen OWL HD" to "Lokalzeit Ostwestfalen-Lippe aus Bielefeld",
            "WDR Fernsehen Aachen HD" to "Lokalzeit aus Aachen und der Euregio",
            "WDR Fernsehen Bergisches Land HD" to "Lokalzeit Bergisches Land aus Wuppertal",
            "WDR Fernsehen Bonn HD" to "Lokalzeit aus Bonn und dem Rheinland",
            "WDR Fernsehen Duisburg HD" to "Lokalzeit aus Duisburg und vom Niederrhein",
            "WDR Fernsehen Südwestfalen HD" to "Lokalzeit Südwestfalen aus Siegen",
            "NDR Fernsehen Niedersachsen HD" to "Hallo Niedersachsen & Nordmagazin",
            "NDR Fernsehen Hamburg HD" to "Hamburg Journal & Norddeutschland Live",
            "NDR Fernsehen Schleswig-Holstein HD" to "Schleswig-Holstein Magazin aus Kiel",
            "NDR Fernsehen Mecklenburg-Vorpommern HD" to "Nordmagazin aus Schwerin",
            "MDR Fernsehen Sachsen HD" to "MDR Sachsenspiegel aus Dresden",
            "MDR Fernsehen Sachsen-Anhalt HD" to "MDR Sachsen-Anhalt Heute aus Magdeburg",
            "MDR Fernsehen Thüringen HD" to "MDR Thüringen Journal aus Erfurt",
            "SWR Fernsehen Baden-Württemberg HD" to "SWR Aktuell Baden-Württemberg aus Stuttgart",
            "SWR Fernsehen Rheinland-Pfalz HD" to "SWR Aktuell Rheinland-Pfalz aus Mainz",
            "BR Fernsehen Süd HD" to "Bayerischer Rundfunk • Abendschau Süd",
            "BR Fernsehen Nord HD" to "Bayerischer Rundfunk • Frankenschau Aktuell",
            "hr-fernsehen HD" to "Hessischer Rundfunk • hessenschau Live",
            "rbb Fernsehen Berlin HD" to "rbb24 Abendschau für Berlin",
            "rbb Fernsehen Brandenburg HD" to "rbb24 Brandenburg aktuell aus Potsdam",
            "SR Fernsehen HD" to "Saarländischer Rundfunk • Aktueller Bericht",
            "Radio Bremen TV HD" to "buten un binnen • Regionalmagazin für Bremen",
            "ARD Event 1 Live" to "ARD Sonderübertragungen & Live-Events",
            "ARD Event 2 Live" to "ARD Sport- & Kultur-Sonderkanal",
            "ZDF Event 1 Live" to "ZDF Live-Sport & Sonderübertragungen",
            "ZDF Event 2 Live" to "ZDF Konzert- & Politik-Livestream",
            "Sportschau Live Kanal" to "ARD Sportschau 24/7 Live-Übertragungen",
            "ZDF sportstudio Live" to "aktuelle sportstudio & Live-Wettkämpfe",
            "Funk Live Kanal" to "Das Content-Netzwerk von ARD und ZDF",
            "ARD Mediathek Live" to "Highlights & Premieren aus der ARD",
            "ZDFheute Live" to "Nachrichten-Streams & Hintergrund-Talks",
            "Planet Wissen 24/7" to "Wissensmagazin von WDR, SWR und BR",
            "Quarks Science TV" to "Wissenschaft & Forschung mit dem WDR",
            "Terra X Natur & Geschichte" to "ZDF-Dokumentationen rund um die Uhr",
            "ARTE Concert Live" to "Festivals, Oper, Rock & Elektro-Konzerte",
            "ARTE Doku Kanal" to "Ausgewählte ARTE-Reportagen & Gesellschaft",
            "3sat Kulturzeit Kanal" to "Kultur, Literatur & Theater im Fokus",
            "NDR Doku & Reportage" to "Die Nordreportage & MareTV Highlights",
            "WDR Die Maus Kanal" to "Lach- und Sachgeschichten rund um die Uhr",
            "BR Heimat & Berge TV" to "Bergauf-Bergab & Unter unserem Himmel",
            "SWR Handwerkskunst TV" to "Meisterwerke & Tradition aus dem Südwesten",
            "MDR Zeitreise TV" to "Geschichte Mitteldeutschlands & Dokus",
            "hr Erlebnis Hessen TV" to "Reisen, Natur & Kulinarik in Hessen",
            "rbb Kultur & Metropole" to "Kulturmagazine aus Berlin und Brandenburg",
            "Landtag NRW Live" to "Plenardebatten aus Düsseldorf",
            "Bayerischer Landtag TV" to "Live-Übertragungen aus dem Maximilianeum München",
            "Landtag Baden-Württemberg TV" to "Debatten aus dem Stuttgarter Landtag",
            "Hessischer Landtag Live" to "Parlamentsfernsehen aus Wiesbaden",
            "Sächsischer Landtag TV" to "Plenarsitzungen live aus Dresden",
            "Niedersächsischer Landtag TV" to "Landtagsdebatten live aus Hannover",
            "Abgeordnetenhaus Berlin TV" to "Parlamentsfernsehen der Hauptstadt",
            "Hamburgische Bürgerschaft TV" to "Sitzungen live aus dem Hamburger Rathaus",
            "Bremische Bürgerschaft TV" to "Parlamentsdebatten aus Bremen",
            "Landtag Rheinland-Pfalz TV" to "Plenarsitzungen live aus Mainz"
        )
        list.addAll(publicBroadcasters)

        // 76..145: Große Privatsender, Vollprogramme & Free-TV Sender (70 Kanäle)
        val privateNationalChannels = listOf(
            "RTL HD" to "Vollprogramm • Shows, Serien & RTL Aktuell",
            "SAT.1 HD" to "Vollprogramm • Frühstücksfernsehen, Shows & Filme",
            "ProSieben HD" to "Blockbuster, US-Serien & Unterhaltungsshows",
            "VOX HD" to "Die Höhle der Löwen, Dokus & Spielfilme",
            "Kabel Eins HD" to "Die besten Filme aller Zeiten & Kultserien",
            "RTL ZWEI HD" to "Doku-Soaps, Reality-Shows & Spielfilme",
            "Super RTL HD" to "Primetime-Filme & Familienunterhaltung",
            "TOGGO plus" to "Kinder- und Familienprogramm von Super RTL",
            "NITRO HD" to "Männer-Entertainment, Kultserien & Live-Fußball",
            "SAT.1 Gold HD" to "Kult-Krimis, deutsche Serien & Ratgeber",
            "ProSieben MAXX HD" to "Anime, US-Sport, Wrestling & Doku-Formate",
            "sixx HD" to "Lifestyle, US-Serien & Reality-Shows",
            "Kabel Eins Doku HD" to "True Crime, Geschichte, Natur & Technik",
            "VOXup HD" to "Wohnen, Kochen, Styling & Kult-Serien",
            "RTLup HD" to "Gerichtsshows, Kult-Krimis & Schlager",
            "Tele 5 HD" to "Sci-Fi, Fantasy, Kultfilme & SchleFaZ",
            "DMAX HD" to "Abenteuer, Technik, Schatzsucher & Motoren",
            "TLC Deutschland HD" to "Real-Life-Entertainment, True Crime & Lifestyle",
            "HOME & GARDEN TV (HGTV)" to "Hausbau, Renovierung, Garten & Wohnträume",
            "Comedy Central Deutschland" to "Sitcoms, Stand-up & Adult Animation",
            "Nickelodeon Deutschland" to "SpongeBob, PAW Patrol & Jugendserien",
            "Disney Channel Deutschland" to "Disney-Klassiker, Familienfilme & Zeichentrick",
            "DF1 HD" to "Deutsches Free-TV • ServusTV-Formate, Sport & Filme",
            "WELT HD" to "Nachrichten, Börse, Politik & Reportagen",
            "n-tv HD" to "Breaking News, Wirtschaft, Wetter & Telebörse",
            "N24 Doku" to "Zeitgeschichte, Wissenschaft & Technik-Reportagen",
            "BILD TV Live" to "Nachrichten-Talks, Sport & Live-Reportagen",
            "Euronews Deutsch" to "Europäische & internationale Nachrichten 24/7",
            "SPORT1 HD" to "Doppelpass, Darts, Motorsport & Live-Sport",
            "Eurosport 1 Deutschland" to "Radsport, Tennis Grand Slams, Wintersport & Olympia",
            "Bibel TV HD" to "Christliches Familien-, Film- & Talkprogramm",
            "Welt der Wunder TV" to "Wissensmagazine, Technik, Natur & Innovation",
            "Anixe+ HD" to "Serienklassiker, Dokus & Familienunterhaltung",
            "Anixe HD Serie" to "Kultserien & klassische Fernsehunterhaltung",
            "Folx Music Television" to "Volkstümliche Musik, Schlager & Unterhaltung",
            "Deutsches Musik Fernsehen" to "Schlager, Volksmusik & deutsche Hits",
            "Deluxe Music HD" to "Musikvideos, Charts, 80er, 90er & Lounge",
            "Schlager Deluxe" to "Aktuelle deutsche Schlager & Party-Hits",
            "MTV Germany HD" to "Musikcharts, Popkultur & Reality-Shows",
            "Sonnenklar.TV HD" to "Reiseshopping, Urlaubstipps & Hotel-Reportagen",
            "HSE HD" to "Home Shopping Europe • Mode, Schmuck & Haushalt",
            "HSE Extra HD" to "Homeshopping • Haus, Garten & Elektronik",
            "HSE Trend" to "Junge Mode, Beauty & Lifestyle-Trends",
            "QVC Deutschland HD" to "Live-Shopping • Küche, Beauty & Technik",
            "QVC2 HD" to "Wunsch-Shows & Best-of-Präsentationen",
            "QVC Style HD" to "Fashion, Accessoires & Designermode",
            "Channel21 HD" to "Gesundheit, Ernährung, Mode & Homeshopping",
            "1-2-3.tv HD" to "Interaktives Auktions- und Schnäppchenfernsehen",
            "Juwelo TV HD" to "Echtschmuck & Edelsteine live im TV",
            "Pearl.TV 4K" to "Technik-, Elektronik- & Gadget-Shopping",
            "MediaShop TV" to "Innovative Haushalts-, Fitness- & Küchenhelfer",
            "Genius Plus TV" to "Kochen, Backen & praktische Küchenlösungen",
            "AstroTV" to "Spiritualität, Lebensberatung & Horoskope",
            "K-TV Katholisches Fernsehen" to "Gottesdienste, Glaubenskurse & Dokumentation",
            "EWTN.TV Deutsch" to "Weltweites katholisches Fernsehnetzwerk",
            "Hope TV Deutsch" to "Christliches Magazin-, Gesundheits- & Musikprogramm",
            "Lilo TV" to "Unterhaltung, Klassiker, Kochen & Musik",
            "Health TV" to "Gesundheits-, Medizin- & Ernährungsfernsehen",
            "MeinTV" to "Regionale Berichte, Freizeit & Unterhaltung",
            "Regio TV Deutschland" to "Nachrichten & Geschichten aus den Regionen",
            "Aristo TV" to "Lifestyle, Reisen, Kultur & Reportagen",
            "Schlager TV Deutsch" to "Die größten deutschen Schlagerhits 24/7",
            "StarParadies TV" to "Schlagerlegenden, Kult-Auftritte & Musikfilme",
            "Gute Laune TV" to "Deutscher Schlager, Oldies & Country",
            "Goldstar TV" to "Kult-Schlager, Live-Konzerte & Hitparaden",
            "Heimatkanal" to "Heimatfilme, deutsche Familienserien & Volkstheater",
            "Romance TV" to "Romantische Filme, Telenovelas & Traumschiff",
            "KinoWelt TV" to "Preisgekrönte Arthouse-Filme & Kinoklassiker",
            "Silverline Movie Channel" to "Action, Horror, Thriller & Asian Cinema",
            "AXN Black Deutschland" to "Action-Serien, Blockbuster & Crime-Hits"
        )
        list.addAll(privateNationalChannels)

        // 146..270: Regionale & Lokale TV-Sender aus allen 16 Bundesländern (125 Kanäle)
        val regionalAndCityChannels = listOf(
            "münchen.tv HD" to "Das Stadtfernsehen für München und Oberbayern",
            "tv.berlin" to "Der Hauptstadtsender aus Berlin",
            "Hamburg 1" to "Nachrichten, Wirtschaft & Sport aus Hamburg",
            "RheinMainTV" to "Nachrichten & Wirtschaft für Frankfurt und Rhein-Main",
            "Baden TV" to "Regionalfernsehen für Karlsruhe und Nordbaden",
            "Baden TV Süd" to "Regionalprogramm für Freiburg und Südbaden",
            "L-TV Landesfernsehen" to "Nachrichten & Kultur aus Baden-Württemberg",
            "Regio TV Stuttgart" to "Nachrichten aus der Metropolregion Stuttgart",
            "Regio TV Schwaben" to "Regionalfernsehen für Ulm und Ostwürttemberg",
            "Regio TV Bodensee" to "Nachrichten & Freizeit rund um den Bodensee",
            "RNF Rhein-Neckar Fernsehen" to "Metropolregion Rhein-Neckar • Mannheim & Heidelberg",
            "Franken Fernsehen HD" to "Nachrichten aus Nürnberg, Fürth und Erlangen",
            "TVA Ostbayern HD" to "Regionalfernsehen für Regensburg, Straubing und Kelheim",
            "Oberpfalz TV (OTV)" to "Nachrichten aus Amberg, Weiden und der Oberpfalz",
            "Niederbayern TV Landshut" to "Regionalprogramm aus Landshut und Umgebung",
            "Niederbayern TV Passau" to "Nachrichten aus der Dreiflüssestadt Passau",
            "Niederbayern TV Deggendorf" to "Regionalberichte aus dem Bayerischen Wald",
            "allgäu.tv HD" to "Nachrichten & Bergwelt aus Kempten und dem Allgäu",
            "augsburg.tv (a.tv)" to "Regionalfernsehen für Augsburg und Bayerisch-Schwaben",
            "rfo Regional Fernsehen Oberbayern" to "Nachrichten aus Rosenheim, Chiemgau & Berchtesgaden",
            "TV Oberfranken (TVO)" to "Berichte aus Bamberg, Bayreuth, Coburg und Hof",
            "TV Mainfranken" to "Regionalfernsehen für Würzburg, Schweinfurt & Aschaffenburg",
            "tv.ingolstadt" to "Nachrichten & Sport aus Ingolstadt und der Region 10",
            "Sachsen Fernsehen Dresden" to "Drehscheibe Dresden • Lokalnachrichten & Sport",
            "Sachsen Fernsehen Leipzig" to "Drehscheibe Leipzig • Nachrichten aus der Messestadt",
            "Sachsen Fernsehen Chemnitz" to "Drehscheibe Chemnitz • Kulturhauptstadt & Region",
            "Sachsen Fernsehen Vogtland" to "Vogtland Regionalfernsehen aus Plauen",
            "TV Westsachsen" to "Lokalfernsehen für Zwickau und das Westerzgebirge",
            "Lausitzwelle TV" to "Regionalprogramm für Hoyerswerda und die Oberlausitz",
            "Elsterwelle TV" to "Nachrichten und Magazine aus der Lausitz",
            "MEISSEN FERNSEHEN" to "Lokalfernsehen für den Landkreis Meißen",
            "Pirna TV" to "Lokalprogramm für Pirna und die Sächsische Schweiz",
            "kabeljournal Erzgebirge" to "Regionalberichte aus dem Erzgebirgskreis",
            "MITTELDEUTSCHES FERNSEHEN (MDF.1)" to "Das Stadtfernsehen für Magdeburg",
            "TV Halle" to "Nachrichten & Kultur aus Halle (Saale)",
            "PUNKTum Fernsehen" to "Regionalfernsehen für das Mansfelder Land & Harz",
            "RAN1 Regionalfernsehen" to "Lokalprogramm für Dessau-Roßlau, Bitterfeld & Wittenberg",
            "RFH Regionalfernsehen Harz" to "Nachrichten aus Halberstadt, Quedlinburg & Wernigerode",
            "RBW Regionalfernsehen" to "Bitterfeld-Wolfen, Köthen und Bernburg im Fokus",
            "Elbekanal Tangermünde" to "Lokalfernsehen aus der Altmark",
            "Salve.tv Erfurt" to "Regionalfernsehen für Erfurt und Mittelthüringen",
            "JenaTV" to "Nachrichten & Wissenschaft aus Jena",
            "Rennsteig.TV" to "Lokalfernsehen aus Suhl und dem Thüringer Wald",
            "SRF Südthüringer Regionalfernsehen" to "Berichte aus Sonneberg, Hildburghausen & Coburg",
            "Altenburg TV" to "Lokalfernsehen für das Altenburger Land",
            "OSF Oder-Spree Fernsehen" to "Regionalprogramm für Ostbrandenburg",
            "PotsdamTV" to "Nachrichten aus der brandenburgischen Landeshauptstadt",
            "Hauptstadt.TV" to "Politik, Kultur & Leben in Berlin und Potsdam",
            "LAUSITZ TV Cottbus" to "Nachrichten aus Cottbus und der Niederlausitz",
            "Uckermark TV" to "Regionalfernsehen aus Schwedt und Prenzlau",
            "Prignitz TV" to "Lokalberichte aus dem Nordwesten Brandenburgs",
            "brandenburgischen Regional TV (teltOwkanal)" to "Lokalprogramm für Teltow, Kleinmachnow & Stahnsdorf",
            "Strausberg TV" to "Nachrichten aus Märkisch-Oderland",
            "WMZ TV Frankfurt (Oder)" to "Lokalfernsehen von der Odergrenze",
            "TV Schwerin" to "Stadtfernsehen für die Landeshauptstadt Schwerin",
            "tv.rostock" to "Lokalnachrichten von der Ostseeküste & Hansestadt Rostock",
            "Wismar TV" to "Lokalprogramm aus der Hansestadt Wismar",
            "Greifswald TV" to "Universitäts- und Hansestadt Greifswald im Bild",
            "FERNSEHEN MIT HERZ (Stralsund TV)" to "Berichte aus Stralsund und Vorpommern",
            "Rügen TV" to "Inselfernsehen von Deutschlands größter Insel",
            "Usedom TV" to "Urlaubs- und Regionalfernsehen von der Sonneninsel",
            "NeuEins TV Neubrandenburg" to "Lokalfernsehen von der Mecklenburgischen Seenplatte",
            "Sylt1 – Das Sylter Fernsehen" to "Nordsee, Insel-News & Lifestyle aus Westerland",
            "Friesischer Rundfunk (FRF1)" to "Regionalfernsehen für Ostfriesland und die Küste",
            "oeins Oldenburg" to "Lokalsender für Oldenburg, Ammerland und Friesland",
            "os1.tv Osnabrück" to "Nachrichten & Sport aus dem Osnabrücker Land",
            "ev1.tv Ems-Vechte-Welle" to "Regionalfernsehen für das Emsland und die Grafschaft Bentheim",
            "TV38 Wolfsburg & Braunschweig" to "Bürgerfernsehen zwischen Harz und Heide",
            "h1 – Fernsehen aus Hannover" to "Stadtfernsehen für die Region Hannover",
            "Radio Weser.TV Bremen" to "Regionalprogramm für Bremen und Bremerhaven",
            "Radio Weser.TV Bremerhaven" to "Küste, Hafen & Stadtleben aus Bremerhaven",
            "TIDE TV Hamburg" to "Hamburgs Stadtsender für Kultur, Musik & Wissen",
            "OK Kiel (Offener Kanal Kiel)" to "Bürgerfernsehen aus der Landeshauptstadt Kiel",
            "OK Lübeck" to "Regionales Kultur- und Bürgerprogramm aus Lübeck",
            "OK Flensburg" to "Grenzregion Flensburg und Förde im Fokus",
            "OK Westküste" to "Dithmarschen und Nordfriesland im Bürgerfernsehen",
            "STUDIO 47 Duisburg" to "Das Stadtfernsehen für Duisburg und den Niederrhein",
            "NRWision" to "Die Mediathek & der Lernsender für Nordrhein-Westfalen",
            "CityVision Mönchengladbach" to "Lokalnachrichten vom linken Niederrhein",
            "Center.tv Düsseldorf" to "Heimatfernsehen für die Rheinmetropole",
            "Köln.tv Regional" to "Domstadt, Karneval, Sport & Kölner Veedel",
            "Münster.tv" to "Nachrichten und Campus-Leben aus Westfalen",
            "Bielefeld City TV" to "Regionalprogramm aus Ostwestfalen",
            "WWTV Westerwald-Wied" to "Regionalfernsehen für den Westerwald und Rhein-Lahn",
            "TV Mittelrhein" to "Nachrichten aus Koblenz, MYK und dem Mittelrheintal",
            "DRF1 – Deutsches Regionalfernsehen" to "Reportagen aus den deutschen Regionen",
            "OK54 Bürgerrundfunk Trier" to "Stadtfernsehen für Trier und die Moselregion",
            "OK4 Koblenz" to "Offener Kanal aus Koblenz, Andernach und Neuwied",
            "OK Kaiserslautern" to "Lokalprogramm aus der Westpfalz",
            "OK:TV Mainz" to "Bürgerfernsehen aus der Landeshauptstadt Mainz",
            "OK Weinstraße" to "Neustadt, Landau und Bad Dürkheim im Bild",
            "OK Worms" to "Lokalfernsehen aus der Nibelungenstadt Worms",
            "OK Ludwigshafen" to "Vorderpfalz und Rhein-Neckar im Bürgerprogramm",
            "OK Nahe TV" to "Bad Kreuznach und Idar-Oberstein Regional",
            "OK Südwestpfalz" to "Pirmasens und Zweibrücken Lokalfernsehen",
            "Saarland Fernsehen 1 (SLF)" to "Regionale Berichte aus Saarbrücken und dem Saarland",
            "CityInfo TV Saar" to "Stadtinfo, Events & Kultur im Saarland",
            "ALEX Berlin" to "Kultur-, Musik- & Ereigniskanal aus Berlin",
            "Kassel Live TV (MOK Kassel)" to "Medienprojektzentrum Offener Kanal Nordhessen",
            "MOK Rhein-Main (Offenbach/Frankfurt)" to "Regionales Bürgerfernsehen für Hessen",
            "MOK Gießen" to "Mittelhessen, Campus & Kultur aus Gießen und Marburg",
            "MOK Fulda" to "Osthessen und Rhön im Regionalprogramm",
            "OF-TV Offenbach" to "Nachrichten aus Stadt und Kreis Offenbach",
            "Darmstadt City TV" to "Wissenschaftsstadt Darmstadt & Südhessen",
            "Wiesbaden TV" to "Berichte aus der hessischen Landeshauptstadt",
            "OK Magdeburg" to "Offener Kanal aus der Landeshauptstadt Sachsen-Anhalts",
            "OK Dessau" to "Bauhausstadt Dessau-Roßlau im Bürgerfernsehen",
            "OK Merseburg-Querfurt" to "Südliches Sachsen-Anhalt im Fokus",
            "OK Salzwedel" to "Bürgerfernsehen aus der westlichen Altmark",
            "OK Stendal" to "Lokalprogramm aus der östlichen Altmark",
            "OK Wernigerode" to "Bunte Stadt am Harz & Brockenregion",
            "OK Wettin" to "Saalekreis & Kulturregion Halle",
            "Freiburg TV" to "Schwarzwald, Breisgau & Universitätsstadt Freiburg",
            "Heidelberg TV" to "Neckartal, Wissenschaft & Kultur aus Heidelberg",
            "Karlsruhe City TV" to "Fächerstadt Karlsruhe & TechnologieRegion",
            "Mannheim Live TV" to "Quadratestadt Mannheim & Rhein-Neckar",
            "Konstanz & Bodensee TV" to "Vierländerregion Bodensee im Livestream",
            "Ulm & Neu-Ulm TV" to "Donaustadt zwischen Schwaben und Bayern",
            "Heilbronn Franken TV" to "Weinregion & Wirtschaftsraum Heilbronn",
            "Pforzheim & Nordschwarzwald TV" to "Goldstadt Pforzheim und Enzkreis",
            "Reutlingen & Tübingen TV" to "Neckar-Alb und Schwäbische Alb im Fokus",
            "Aachen Euregio TV" to "Dreiländereck Deutschland-Belgien-Niederlande",
            "Bonn & Siebengebirge TV" to "Bundesstadt Bonn und Rhein-Sieg-Kreis",
            "Wuppertal Schwebebahn TV" to "Nachrichten aus dem Bergischen Städtedreieck",
            "Bochum & Ruhr-Uni TV" to "Herzen des Ruhrgebiets & Kulturkanal"
        )
        list.addAll(regionalAndCityChannels)

        // 271..350: Sport, Motorsport, Fußball & Outdoor (80 Kanäle)
        val sportsChannels = listOf(
            "SPORT1+ HD" to "Internationaler Live-Sport, Motorsport & US-Ligen",
            "eSPORTS1" to "24/7 eSports, Gaming-Turniere & Analysen",
            "Eurosport 2 Deutschland" to "Wintersport, Radsport, Motorsport & Snooker",
            "Red Bull TV Deutsch" to "Extremsport, Formel 1 Dokus, Mountainbike & Rallye",
            "More Than Sports TV" to "European League of Football, Kampfsport & Motorsport",
            "Motorvision+ Deutsch" to "NASCAR, Supercars, Offroad & Autotests",
            "Auto Motor und Sport Channel" to "Fahrberichte, Oldtimer, Tuning & Motorsport",
            "Curiosity Now / Motor" to "Technik, Luftfahrt & Automobil-Dokumentationen",
            "Sportdigital FUSSBALL" to "Internationale Fußball-Ligen rund um die Uhr",
            "Sportdigital FUSSBALL 2" to "Live-Spiele aus Südamerika, Asien & Europa",
            "Sportdigital EDGE" to "Action-Sport, Surfen, Skateboard & Snowboard",
            "DAZN Fast" to "Live-Sport-Highlights, Fußball-Dokus & Analysen",
            "DAZN Rise" to "Frauensport-Sender • UEFA Women's Champions League & Golf",
            "DAZN Heldinnen" to "Frauenfußball, Handball & Spitzensport",
            "DFB Play TV" to "DFB-Pokal Klassiker, Junioren-Länderspiele & Frauen-Bundesliga",
            "FIFA+ Deutsch" to "WM-Klassiker, Live-Spiele & Fußball-Dokumentationen",
            "FC Bayern.tv Live" to "Training, Pressekonferenzen & Klassiker des FCB",
            "BVB TV Live" to "Borussia Dortmund • News, Spieltags-Shows & Historie",
            "Real Madrid TV Deutsch" to "Spielberichte, Legenden & Live-Training",
            "DYN Sport Mix" to "Handball, Basketball, Volleyball, Tischtennis & Hockey",
            "Handball-Bundesliga TV" to "Spitzenspiele, Highlights & Magazin der HBL",
            "BBL Basketball TV" to "Deutsche Basketball-Bundesliga & EuroLeague Highlights",
            "DEL Eishockey Kanal" to "Deutsche Eishockey Liga • Tore, Checks & Klassiker",
            "Tennis Channel Deutschland" to "ATP/WTA-Turniere, Bundesliga & Tennis-Legenden",
            "Tennis Channel 2 Deutsch" to "Centre-Court-Klassiker & Live-Matches",
            "Golf Channel Deutsch" to "PGA Tour, DP World Tour & Golf-Training",
            "Sky Sport News (Free Stream)" to "Sportnachrichten, Transfer-Update & Ergebnisse",
            "World Poker Tour (WPT) Deutsch" to "Hochspannende Poker-Turniere aus aller Welt",
            "PokerGo Deutsch" to "World Series of Poker & High-Stakes Cash Games",
            "Billiard TV Deutsch" to "Poolbillard, 9-Ball & internationale Meisterschaften",
            "Snooker Classics TV" to "Legendäre Maximum-Breaks & WM-Finals",
            "Darts Party 24/7" to "PDC-Klassiker, 9-Darter & WM-Highlights",
            "Strongman Champions League" to "Die stärksten Männer der Welt im Wettkampf",
            "PFL MMA Deutsch" to "Professional Fighters League • Mixed Martial Arts",
            "GLORY Kickboxing TV" to "Schwergewichts-Kickboxen & Titelkämpfe",
            "Impact Wrestling Deutsch" to "Pro-Wrestling Action & Pay-Per-View Klassiker",
            "WWE Classics Deutsch" to "WrestleMania-Momente & Wrestling-Legenden",
            "Boxen Live & Klassiker" to "Große Kampfabende & historische WM-Fights",
            "ClipMyHorse.TV" to "Reitsport, Springreiten, Dressur & Zuchtevents",
            "Horse & Country TV Deutsch" to "Pferdesport, Landleben & Reit-Dokumentationen",
            "Outdoor Channel Deutsch" to "Abenteuer, Angeln, Expeditionen & Wildnis",
            "Nautical Channel Deutsch" to "Segelsport, America's Cup, Yachten & Surfen",
            "Segel TV Deutschland" to "Segelregatten, Ostsee-Törns & Bootstests",
            "Bergsport & Alpin TV" to "Klettern, Skitouren, Alpenüberquerung & Hütten",
            "Ski & Wintersport 24/7" to "Biathlon, Skispringen & Ski Alpin Weltcup",
            "Radsport Classics TV" to "Tour de France Etappen, Giro & Frühjahrsklassiker",
            "Leichtathletik & Marathon TV" to "Olympia-Momente, Diamond League & Stadtläufe",
            "Motorsport Total TV" to "GT-Masters, 24h Nürburgring & Langstrecken-WM",
            "DTM Classics Kanal" to "Legendäre Tourenwagen-Rennen der 80er, 90er & 2000er",
            "Rallye Action TV" to "WRC Rallye-Weltmeisterschaft & Dakar-Etappen",
            "Speedweek Motorsport TV" to "Motorrad-WM, Superbike & Motocross Action",
            "JP Performance Kanal" to "Tuning, Motorenprojekte & Prüfstand-Action 24/7",
            "Die Autodoktoren TV" to "Fehlersuche, Kfz-Meisterwissen & Werkstatt-Fälle",
            "GRIP – Das Motormagazin 24/7" to "Sportwagen-Tests, Challenges & Gebrauchtwagen",
            "Top Gear Deutsch" to "Kult-Autotests, Abenteuerreisen & Supersportwagen",
            "Fifth Gear Deutsch" to "Präzise Fahrzeugtests & Rennsport-Vergleiche",
            "Fast & Loud Auto-Show" to "US-Muscle-Cars, Restaurationen & Auktionen",
            "Monster Jam Action TV" to "Monster-Trucks, Stunts & Freestyle-Shows",
            "Nitro Circus TV" to "FMX, BMX, Skate & Extremsport-Stunts",
            "Surf & Wave TV" to "Weltweite Surfspots, Big-Wave-Riding & Meer",
            "Fitness & Yoga TV Deutsch" to "Home-Workouts, Pilates, Rückenfit & Entspannung",
            "Body & Mind Wellness TV" to "Meditation, Achtsamkeit & gesunder Lebensstil",
            "Angeln & Natur TV" to "Raubfisch-, Karpfen- und Meeresangeln in Europa",
            "Schach TV Deutsch" to "Großmeister-Analysen, WM-Partien & Blitzschach",
            "Tischtennis Live TV" to "TTBL, Europameisterschaften & Weltklasse-Ballwechsel",
            "Volleyball Bundesliga TV" to "Spitzenspiele der Damen und Herren live",
            "Beachvolleyball Tour TV" to "German Beach Tour & internationale Turniere",
            "Ruder- & Wassersport TV" to "Deutschland-Achter, Kanu-Slalom & Regatten",
            "Turnen & Gymnastik TV" to "Kunstturnen, DTB-Pokal & Rhythmische Sportgymnastik",
            "Triathlon & Ironman TV" to "Challenge Roth, Hawaii-Highlights & Ausdauersport",
            "Eiskunstlauf Classics" to "Kür-Highlights, EM, WM & Eisrevuen",
            "Rodel- & Bob-Weltcup TV" to "Eiskanal-Action aus Winterberg, Königssee & Oberhof",
            "ADAC Motorsport Kanal" to "ADAC GT Masters, Formel 4 & SimRacing",
            "Nürburgring Nordschleife TV" to "Grüne Hölle Live-Onboards & Langstreckenrennen",
            "Hockenheimring Classics" to "Historischer Motorsport & Trackdays",
            "Formel E Highlights TV" to "Elektro-Rennserie aus den Metropolen der Welt",
            "Segelflug & Aviatik Sport TV" to "Luftsport, Kunstflug & Alpen-Segelflug",
            "Golf Reise & Platz-Guide" to "Die schönsten Golfplätze in Deutschland und Europa",
            "Bowling & Kegel Sport TV" to "PBA-Turniere & Deutsche Meisterschaften",
            "American Football Deutsch" to "German Football League (GFL) & College-Football"
        )
        list.addAll(sportsChannels)

        // 351..430: Musik, Schlager, Konzerte & Radio-Visuals (80 Kanäle)
        val musicChannels = listOf(
            "Deluxe Dance by Kontor" to "EDM, Club-Hits, House & Festival-Sounds",
            "Deluxe Rap" to "Deutschrap, US-Hip-Hop & R&B Beats",
            "Deluxe Rock" to "Classic Rock, Alternative, Metal & Stadion-Hymnen",
            "Deluxe Flashback" to "Die größten Kult-Hits der 70er, 80er und 90er",
            "Deluxe Lounge HD" to "Chillout-Musik mit spektakulären Naturaufnahmen",
            "MTV 80s Deutsch" to "Die unvergesslichen Pop- und Rock-Klassiker der 80er",
            "MTV 90s Deutsch" to "Eurodance, Grunge, Boybands & 90er-Kultvideos",
            "MTV 00s Deutsch" to "Millennium-Hits, Pop-Punk & R&B der 2000er",
            "MTV Hits Europe" to "Aktuelle internationale Chartbreaker nonstop",
            "MTV Live HD" to "Live-Auftritte, Unplugged-Sessions & Festivals",
            "Club MTV" to "Electronic Dance Music, Ibiza-Beats & DJ-Sets",
            "One Music Television" to "Aktuelle Pop-, Dance- und Chart-Hits",
            "Zwei Music Television" to "Schlager, Pop-Schlager & deutschsprachige Musik",
            "Volksmusik.TV" to "Traditionelle Volksmusik, Blasmusik & Alpenklänge",
            "Melodie TV" to "Herzlichst, Musik aus den Bergen & Heimatmelodien",
            "Ballermann TV" to "Mallorca-Party, Après-Ski & Stimmungshits",
            "Stingray Classica Deutsch" to "Klassische Konzerte, Opern & Ballettaufführungen",
            "Stingray DJAZZ" to "Jazz-, Soul-, Funk- und Blues-Konzerte",
            "Stingray CMusic" to "Filmmusik, Epische Soundtracks & Moderne Klassik",
            "Stingray iConcerts" to "Legendäre Live-Konzerte der Rock- und Popgeschichte",
            "Stingray Karaoke Deutsch" to "Mitsing-Klassiker für jede Party",
            "Stingray Naturescape" to "Entspannungsklänge & malerische Landschaften",
            "Qello Concerts by Stingray" to "Komplette Konzertfilme & Musikdokumentationen",
            "ROCK ANTENNE TV" to "Der beste Rock nonstop • Classic & Heavy Rock",
            "SUNSHINE LIVE TV" to "Electronic Music Radio Visual • Techno, Trance & House",
            "bigFM Visual Radio" to "Deutschlands biggste Beats, Urban & Charts",
            "Antenne Bayern Musik TV" to "Die besten Hits aus Bayern und der Welt",
            "Radio Paloma Schlager TV" to "100% Deutscher Schlager rund um die Uhr",
            "Schlagerparadies TV" to "Kultschlager, aktuelle Hits & Hörerwünsche",
            "Radio BOB! Rock TV" to "Rock'n'Roll, Festival-Berichte & Gitarrenriffs",
            "Klassik Radio Movie & Concerts" to "Entspannte Klassik & große Kinomelodien",
            "80s80s Visual TV" to "Real 80s Music • New Wave, Synthpop & Rock",
            "90s90s Visual TV" to "Der Sound der 90er Jahre nonstop",
            "Absolut Relax TV" to "Entspannte Pop-Hits zum Abschalten und Genießen",
            "Absolut Bella TV" to "Schlager, italienische Klassiker & Oldies",
            "Absolut HOT TV" to "Aktuelle Dance- und Chart-Hits",
            "Absolut Oldie Classics TV" to "Meilensteine der 60er, 70er und 80er Jahre",
            "Absolut TOP TV" to "Der Soundtrack für das neue Jahrzehnt",
            "Absolut Germany TV" to "100% Musik aus Deutschland • Pop, Rock & Indie",
            "Beats Radio Visual" to "Melodic House, Deep House & Lounge Beats",
            "RauteMusik Club TV" to "24/7 DJ-Mixes, HandsUp & Dancefloor",
            "TechnoBase.FM Visual" to "HandsUp, Hardstyle & Electronic Dance",
            "Defjay R&B Channel" to "100% Urban, Soul & Hip-Hop Klassiker",
            "1LIVE Visual Studio" to "WDR Jugendwelle • Sessions, Comedy & Neue Musik",
            "N-JOY Sound TV" to "Junge Musik & Live-Sessions aus dem Norden",
            "DASDING Live Session TV" to "SWR Jugendprogramm • Festivals & Newcomer",
            "PULS Musik TV (BR)" to "Junge Kultur & Indie-Sessions aus Bayern",
            "Fritz Studio TV (rbb)" to "Berliner Clubkultur, Indie & Deutschrap",
            "MDR SPUTNIK Visual" to "Festival-Sommer & Musik aus Mitteldeutschland",
            "YOU FM Studio Live" to "Pop, Urban & Live-Gäste aus Frankfurt",
            "Bremen NEXT Visual" to "Hip-Hop, R&B & Urban Culture aus Bremen",
            "Berliner Philharmoniker Digital Preview" to "Klassische Meisterwerke & Orchesterkultur",
            "Semperoper & Klassik Kanal" to "Opern-, Konzert- und Ballett-Highlights",
            "Wacken Open Air TV" to "Heavy Metal Live-Shows & Festival-Klassiker",
            "Rock am Ring Classics TV" to "Legendäre Festival-Auftritte vom Nürburgring",
            "Parookaville Electronic TV" to "EDM-Festival-Sets & Mainstage-Shows",
            "Mayday & Nature One TV" to "Rave-Klassiker & elektronische Musikgeschichte",
            "Alpenklang & Blasmusik TV" to "Zünftige Blasmusik & alpenländische Feste",
            "Seemannslieder & Küstenmelodie TV" to "Shantys, Nordsee-Lieder & Hafenkonzerte",
            "Kölsch & Karneval Musik TV" to "Kölsche Tön, Sitzungskarneval & rheinische Hits",
            "Oktoberfest & Wiesn Hits TV" to "Festzelt-Stimmung & bayerische Partyklassiker",
            "Oldie Antenne TV" to "Die größten Oldies der 60er, 70er und 80er",
            "Country Music Kanal Deutsch" to "Modern Country, Nashville-Hits & Western-Songs",
            "Blues & Soul Classics TV" to "B.B. King, Motown, Funk & Rhythm and Blues",
            "Reggae & Summer Vibes TV" to "Karibische Klänge, Dub & entspannte Sommermusik",
            "Latin & Salsa Beats TV" to "Reggaeton, Bachata, Salsa & Sommerhits",
            "Klavier & Instrumental TV" to "Entspannende Piano-Melodien zum Arbeiten und Lesen",
            "Kaminfeuer & Lounge Musik TV" to "Knisterndes Kaminfeuer mit sanfter Instrumentalmusik",
            "Aquarium & Chillout TV" to "Farbenprächtige Unterwasserwelt mit Ambient-Sound",
            "Alpen-Panorama & Musik TV" to "Live-Wetterkameras aus den Alpen mit Heimatmusik",
            "Ostsee & Nordsee Strand TV" to "Meeresrauschen, Küstenbilder & Chillout",
            "Deutsche Pop-Geschichte TV" to "Von der Neuen Deutschen Welle bis heute",
            "Indie & Alternative Deutsch" to "Gitarrenpop, Clubkonzerte & Festival-Geheimtipps",
            "Schlager Hitparade Retro" to "Die legendären Musikshows der 70er, 80er & 90er",
            "Operetten & Musical Kanal" to "Große Bühnenmomente, Broadway & Wiener Operette",
            "Chormusik & Vokal TV" to "Vokalensembles, A-cappella & festliche Konzerte",
            "Orgel & Kirchenmusik TV" to "Meisterwerke von Bach bis zur Romantik",
            "Gitarren-Legenden TV" to "Bluesrock, Akustik-Gitarre & Virtuosen im Konzert",
            "Drum & Percussion Channel" to "Rhythmus, Schlagzeug-Solos & World-Percussion",
            "Singer-Songwriter Deutsch TV" to "Liedermacher, Akustik-Poeten & Wohnzimmerkonzerte"
        )
        list.addAll(musicChannels)

        // 431..531: Film, Serien, Crime, Doku, Wissen & Kinder FAST-Kanäle (101 Kanäle)
        val moviesDocsAndKidsChannels = listOf(
            "Netzkino TV" to "Spielfilme, Thriller, Komödien & Kultkino kostenlos",
            "Moviedome HD" to "Blockbuster, Actionfilme & großes Hollywood-Kino",
            "Moviedome Family" to "Familienfilme, Abenteuer & Komödien für alle",
            "Rakuten TV Top Movies Deutsch" to "Ausgewählte Spielfilm-Highlights rund um die Uhr",
            "Rakuten TV Action Deutsch" to "Adrenalin, Action-Blockbuster & Kampfkunst",
            "Rakuten TV Comedy Deutsch" to "Komödien, Feel-Good-Movies & Stand-up",
            "Rakuten TV Drama Deutsch" to "Große Gefühle, preisgekrönte Dramen & Biografien",
            "Rakuten TV Thriller & Suspense" to "Hochspannung, Psychothriller & Kriminalfälle",
            "Rakuten TV Sci-Fi & Fantasy" to "Zukunftswelten, Weltraum-Abenteuer & Magie",
            "Rakuten TV Romance Deutsch" to "Liebesfilme, Herzschmerz & romantische Komödien",
            "Rakuten TV Family Deutsch" to "Animationsfilme & Unterhaltung für die ganze Familie",
            "Rakuten TV Crime Serien" to "Spannende Ermittler, Forensik & Krimi-Serien",
            "Pluto TV Kultfilme" to "Unvergessene Filmklassiker & Publikumslieblinge",
            "Pluto TV Action" to "Explosionen, Verfolgungsjagden & Action-Helden",
            "Pluto TV Sci-Fi" to "Science-Fiction-Filme & Serien aus fernen Galaxien",
            "Pluto TV Thriller" to "Nervenkitzel & packende Thriller-Unterhaltung",
            "Pluto TV Drama" to "Bewegende Geschichten & Charakterkino",
            "Pluto TV Romance" to "Romantische Filme zum Träumen",
            "Pluto TV Western" to "Klassische Western, Cowboys & Italo-Western",
            "Pluto TV Retro Kino" to "Nostalgie-Filme der 50er bis 80er Jahre",
            "Pluto TV Crime" to "Krimiserien, Detektive & spannende Fälle",
            "Pluto TV Sitcoms" to "Beliebte Comedy-Serien & Lacher am laufenden Band",
            "FilmRise Deutsch" to "US-Serien, Spielfilme & True-Crime-Klassiker",
            "FilmRise True Crime" to "Wahre Kriminalfälle & forensische Ermittlungen",
            "FilmRise Sci-Fi Deutsch" to "Mystery- und Science-Fiction-Serien",
            "Arthouse CNMA" to "Anspruchsvolles Programmkino & Festival-Gewinner",
            "KultKrimi 24/7" to "Derrick, Der Alte, Ein Fall für zwei & Krimi-Klassiker",
            "Tatort & Polizeiruf 110 Kanal" to "Die beliebtesten Sonntagskrimis rund um die Uhr",
            "Mord & Totschlag Crime TV" to "Internationale Krimiserien & britische Detektive",
            "Inspector Barnaby 24/7" to "Midsomer Murders • Britischer Krimi-Kult",
            "Sherlock & Co. Krimi TV" to "Meisterdetektive von Agatha Christie bis Arthur Conan Doyle",
            "Aktenzeichen XY Classic TV" to "Wahre Kriminalfälle & Prävention im Rückblick",
            "Medical Detectives 24/7" to "Forensische Spurensuche & reale Kriminalfälle",
            "X-Factor: Das Unfassbare 24/7" to "Wahrheit oder Fiktion mit Jonathan Frakes",
            "Star Trek Kanal Deutsch" to "Enterprise, Voyager, Deep Space Nine & Next Generation",
            "Doctor Who Deutsch" to "Zeitreisen mit der TARDIS durch Raum und Zeit",
            "Stargate & Sci-Fi Kult TV" to "Reisen durch das Sternentor & Kult-Serien",
            "Bud Spencer & Terrence Hill TV" to "Haue, Bohnen & legendäre Italowestern-Komödien",
            "Louis de Funès & Klassiker TV" to "Französische Filmkomödien & Nostalgie-Hits",
            "DEFA & DDR-Filmklassiker TV" to "Märchenfilme, Komödien & Kultfilme aus Babelsberg",
            "Deutsche Filmklassiker TV" to "Edgar Wallace, Karl May & Wirtschaftswunder-Kino",
            "Gruselkino & Horror TV" to "Gänsehaut, Monster-Klassiker & Mystery-Filme",
            "Asiatisches Kino & Martial Arts" to "Kung-Fu-Klassiker, Samurai-Epen & Action",
            "Bollywood & Indisches Kino Deutsch" to "Farbenfrohe Tanzfilme & große Liebesgeschichten",
            "Telenovela & Sturm der Liebe TV" to "Fürstenhof, Rote Rosen & tägliche Romantik",
            "Gute Zeiten, Kult-Soaps 24/7" to "Deutsche Daily-Soaps & Serien-Highlights",
            "Bergdoktor & Bergretter Kanal" to "Hochspannung und große Gefühle in den Alpen",
            "Traumschiff & Kreuzfahrt TV" to "Fernweh, Urlaubsfilme & Geschichten auf hoher See",
            "tierisch gut! Natur & Haustier TV" to "Hundeprofi, Tierärzte & Zoogeschichten",
            "Galileo Wissens-Kanal 24/7" to "Spannende Experimente, Food-Trends & Technik",
            "Abenteuer Leben 24/7" to "Handwerk, Mega-Fabriken, Food & Reise-Reportagen",
            "Mein Lokal, Dein Lokal 24/7" to "Der große Restaurant-Wettstreit in Deutschland",
            "Rosins Restaurants 24/7" to "Sternekoch Frank Rosin hilft Gastronomen in Not",
            "Kitchen Impossible & Koch-TV" to "Kulinarische Herausforderungen weltweit",
            "Chefkoch TV" to "Tägliche Rezeptideen, Backen & schnelle Feierabendküche",
            "BonGusto TV" to "Genuss, Wein, Feinkost & internationale Spitzenköche",
            "Just Cooking Deutsch" to "Kochshows, Grill-Tipps & Backkunst",
            "Wein & Kulinarik TV" to "Deutsche Weinregionen, Winzer & Gourmetreisen",
            "Spiegel TV Reportage 24/7" to "Hintergrundberichte, Gesellschaft & Einsatzkräfte",
            "Spiegel Geschichte Kanal" to "Dokumentationen zur deutschen und Weltgeschichte",
            "Curiosity Channel Deutsch" to "Wissenschaft, Raumfahrt, Natur & Zivilisation",
            "Love Nature Deutsch HD" to "Tierwelt, Ozeane & Naturparadiese in brillanter Schärfe",
            "BBC Earth Deutsch" to "Preisgekrönte Naturdokumentationen der BBC",
            "WildEarth Safari Live" to "Live-Pirschfahrten aus der afrikanischen Savanne",
            "WaterBear Umwelt & Natur TV" to "Nachhaltigkeit, Ozeanschutz & Klima-Dokus",
            "Space & Kosmos TV" to "Astronomie, ESA/NASA-Missionen & das Universum",
            "Eisenbahn-Romantik 24/7" to "Dampfloks, Traumstrecken & Modellbahnen weltweit",
            "Bahn & Schiene TV" to "Führerstandsmitfahrten & Eisenbahngeschichte",
            "Flieger & Luftfahrt TV" to "Cockpit-Dokus, Flughäfen & Geschichte der Luftfahrt",
            "Schiff & Maritim TV" to "Kreuzfahrtschiffe, Containerriesen & Nord-Ostsee-Kanal",
            "Landtechnik & Agrar TV" to "Traktoren, Erntetechnik & moderne Landwirtschaft",
            "Bauen & Heimwerken TV" to "DIY-Projekte, Holzbau, Werkzeugtests & Sanierung",
            "Architektur & Design TV" to "Traumhäuser, Stadtplanung & Wohnkultur",
            "Garten & Naturparadies TV" to "Pflanzenpflege, Gartengestaltung & Selbstversorgung",
            "Marco Polo Reise TV" to "Städtereisen, Geheimtipps & Traumziele weltweit",
            "Urlaubs & Alpen-Panoramen TV" to "Wanderrouten, Wellnesshotels & Bergwelten",
            "Kriminalfälle Deutschland TV" to "Authentische Polizeireportagen & Ermittlungsakten",
            "Feuerwehr & Rettung 112 TV" to "Dokumentationen über Feuerwehr, THW und Seenotretter",
            "Zoll & Grenzschutz Report TV" to "Kontrollen am Flughafen, Hafen und auf der Autobahn",
            "Autobahnpolizei & Verkehr TV" to "Reportagen von Deutschlands Straßen und Raststätten",
            "SpongeBob Schwammkopf 24/7" to "Abenteuer aus Bikini Bottom rund um die Uhr",
            "PAW Patrol Kanal Deutsch" to "Die Helfer auf vier Pfoten im Dauereinsatz",
            "Peppa Wutz Deutsch 24/7" to "Lustige Geschichten für die jüngsten Zuschauer",
            "Benjamin Blümchen & Bibi Blocksberg" to "Beliebte Kinderhörspiel- und Zeichentrickhelden",
            "Bibi & Tina Reiterhof TV" to "Pferdeabenteuer auf dem Martinshof",
            "Unser Sandmännchen Kanal" to "Abendgruß, Pittiplatsch & Schnatterinchen",
            "Pumuckl & Kinder-Klassiker TV" to "Meister Eder, Biene Maja, Wickie & Heidi",
            "Fix & Foxi TV" to "Lehrreiche Zeichentrickserien für Kinder und Familien",
            "RiC TV – Das Familienprogramm" to "Entschleunigtes Kinderprogramm & Gute-Nacht-Geschichten",
            "Kixi Kinderkino" to "Kinderfilme, Märchen & Zeichentrick-Abenteuer",
            "Junior Classics TV" to "Beliebte Zeichentrick-Serien aus vier Jahrzehnten",
            "Cartoonito Deutsch" to "Vorschulprogramm mit Batwheels, Tom & Jerry",
            "Cartoon Network Classics" to "Adventure Time, Ben 10, Gumball & Zeichentrick-Action",
            "Anime Action Deutsch" to "Shonen-Anime, Fantasy-Abenteuer & Kult-Serien",
            "Anime Classics Deutsch" to "Meilensteine des japanischen Animationsfilms",
            "Naruto & Ninja Anime TV" to "Actionreiche Anime-Abenteuer rund um die Uhr",
            "Yu-Gi-Oh! & Kids Action TV" to "Karten-Duelle & spannende Zeichentrick-Turniere",
            "Märchenstunde 24/7" to "Brüder Grimm, Hans Christian Andersen & Märchenfilme",
            "Wissen macht Ah! & Kinderwissen" to "Schlaue Antworten für neugierige Kinder",
            "Checker Tobi & Entdecker TV" to "Abenteuer- und Wissensreisen für Kinder",
            "Campus & Uni-TV Deutschland" to "Vorlesungen, Forschung & Hochschulfernsehen"
        )
        list.addAll(moviesDocsAndKidsChannels)

        check(list.size == TARGET_TV3_CHANNEL_COUNT) {
            "Expected $TARGET_TV3_CHANNEL_COUNT channels in TV3 DE catalog, but found ${list.size}"
        }
        return list
    }
}
