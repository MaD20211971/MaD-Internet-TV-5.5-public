package com.example.data.model

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.parser.ContentClassifier
import java.util.UUID

enum class PlaylistType {
    M3U_URL,
    M3U_FILE,
    XTREAM_CODES,
    MAC_PORTAL
}

enum class ContentType {
    LIVE_TV,
    RADIO,
    MOVIE,
    SERIES,
    UNKNOWN
}

/**
 * Model representation of an IPTV Category delivered by a provider
 * (e.g. Xtream Codes, M3U group-title, or MAC Portal category).
 */
@Immutable
data class Category(
    val id: String = "",
    val name: String = "",
    val parentId: Int = 0
)

enum class CachingMode(
    val titleDe: String,
    val titleEn: String,
    val descriptionDe: String,
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int
) {
    ULTRA_FAST(
        "Niedrige Latenz (Ultra Fast)",
        "Low Latency (Ultra Fast)",
        "Schneller Senderwechsel für flottes Zappen (<1s Startzeit)",
        6000, 20000, 800
    ),
    BALANCED(
        "Ausgeglichen (Empfohlen)",
        "Balanced (Recommended)",
        "Optimaler Kompromiss: Schneller Start & unterbrechungsfreier Stream (30s Puffer)",
        15000, 45000, 1800
    ),
    STABLE(
        "Hohe Stabilität (Großer Puffer)",
        "High Stability (Large Buffer)",
        "Verhindert Mikroruckler & Aussetzer bei schwankendem WLAN (60s Puffer)",
        60000, 150000, 5000
    ),
    WEAK_INTERNET(
        "Schwaches Internet (Maximaler Schutz)",
        "Weak Internet (Max Buffer Protection)",
        "Maximaler Vorlauf gegen Ladekreise und Stottern (90s Puffer)",
        90000, 240000, 7000
    )
}

enum class PlayerEngine(
    val displayName: String,
    val packageName: String?,
    val fallbackPackages: List<String> = emptyList()
) {
    BUILT_IN_NEXUS("Nexus TV Player (Integriert)", null),
    VLC("VLC for Android", "org.videolan.vlc", listOf("org.videolan.vlc.betav7neon")),
    MX_PLAYER("MX Player", "com.mxtech.videoplayer.ad", listOf("com.mxtech.videoplayer.pro")),
    XPLAYER("XPlayer (Video Player All Format)", "video.player.videoplayer"),
    MPV("MPV Player", "is.xyz.mpv", listOf("is.xyz.mpv.nightly")),
    WUFFY("Wuffy Media Player", "co.wuffy.player");

    val allPackages: List<String>
        get() = listOfNotNull(packageName) + fallbackPackages
}

@Immutable
@Entity(
    tableName = "playlists",
    indices = [
        Index(value = ["isActive"]),
        Index(value = ["type"]),
        Index(value = ["lastUpdated"])
    ]
)
data class PlaylistEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: PlaylistType,
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val macAddress: String = "",
    val customEpgUrl: String = "",
    val lastUpdated: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
    val isPasswordProtected: Boolean = false,
    val protectionPassword: String = "",
    val liveCount: Int = 0,
    val radioCount: Int = 0,
    val movieCount: Int = 0,
    val seriesCount: Int = 0
) {
    /**
     * Returns an empty string by default so raw URLs are not exposed in unauthenticated overviews.
     */
    fun getCleanDisplayUrl(maxLength: Int = 34): String {
        return ""
    }

    /**
     * Returns the clean display name (preserving host/label while stripping raw MACs, passwords, or M3U query params).
     */
    fun getDisplayName(): String {
        return formatPlaylistDisplayName(
            name = name,
            type = type,
            url = url,
            username = username,
            password = password,
            macAddress = macAddress,
            maskDomain = false
        )
    }

    /**
     * Returns a masked title specifically for the unauthenticated Settings playlist overview
     * so playlists are not completely readable without entering the password.
     */
    fun getMaskedSettingsOverviewTitle(): String {
        return formatPlaylistDisplayName(
            name = name,
            type = type,
            url = url,
            username = username,
            password = password,
            macAddress = macAddress,
            maskDomain = true
        )
    }

    /**
     * Returns masked credential/portal details for the Settings playlist overview when locked.
     */
    fun getMaskedSettingsOverviewDetails(isEnglish: Boolean = false): String {
        return when (type) {
            PlaylistType.MAC_PORTAL -> {
                val maskedMac = if (macAddress.length >= 8) {
                    "${macAddress.take(8)}:••:••:••"
                } else {
                    "••:••:••:••:••:••"
                }
                if (isEnglish) "🔒 Portal: •••••••• • MAC: $maskedMac"
                else "🔒 Portal: •••••••• • MAC: $maskedMac"
            }
            PlaylistType.XTREAM_CODES -> {
                if (isEnglish) "🔒 Server: •••••••• • User: •••••• • Pass: ••••••"
                else "🔒 Server: •••••••• • User: •••••• • Pass: ••••••"
            }
            PlaylistType.M3U_URL -> {
                if (isEnglish) "🔒 M3U Link: ••••••••••••••••"
                else "🔒 M3U-Link: ••••••••••••••••"
            }
            PlaylistType.M3U_FILE -> {
                if (isEnglish) "🔒 Local M3U file (path hidden)"
                else "🔒 Lokale M3U-Datei (Pfad ausgeblendet)"
            }
        }
    }

    /**
     * Returns the complete, unmasked playlist data for authorized users who entered the password.
     */
    fun getFullUnmaskedDetails(): String {
        return when (type) {
            PlaylistType.MAC_PORTAL -> "🔓 Portal: $url • MAC: $macAddress"
            PlaylistType.XTREAM_CODES -> "🔓 Server: $url • User: $username • Pass: $password"
            PlaylistType.M3U_URL -> "🔓 M3U-Link: $url"
            PlaylistType.M3U_FILE -> "🔓 Datei: $url"
        }
    }
}

private val DISPLAY_NAME_MAC_REGEX = Regex("""(?i)\b([0-9a-f]{2}[:-]){2,5}[0-9a-f]{2}\b""")
private val DISPLAY_NAME_URL_REGEX = Regex("""(?i)https?://\S+""")
private val DISPLAY_NAME_DOMAIN_PORT_REGEX = Regex("""(?i)\b([a-z0-9.-]+)\.([a-z]{2,10})(:\d+)?(/\S*)?\b""")
private val DISPLAY_NAME_VPN_PREFIX_REGEX = Regex("""^(\[[A-Z]{2,3}\]\s*)""")
private val DISPLAY_NAME_PORT_STRIP_REGEX = Regex(""":\d{2,5}\b""")
private val DISPLAY_NAME_EMPTY_PARENS_REGEX = Regex("""\(\s*\)""")
private val DISPLAY_NAME_EMPTY_BRACKETS_REGEX = Regex("""\[\s*\]""")
private val DISPLAY_NAME_MAC_LABEL_REGEX = Regex("""(?i)\bMAC\s*:\s*""")

private val playlistDisplayNameCache = com.example.util.FastLruCache<String, String>(4096)

private fun maskDomainToken(domain: String): String {
    val clean = domain.substringBefore(':').substringBefore('/')
    val parts = clean.split('.')
    if (parts.size < 2) return "••••"
    val main = parts.dropLast(1).joinToString(".")
    val tld = parts.last()
    val visiblePrefix = main.take(2)
    return if (visiblePrefix.isNotEmpty()) "${visiblePrefix}••••.$tld" else "••••.$tld"
}

/**
 * Formats playlist display names with O(1) LRU caching so scrolling 1,000+ playlists never runs
 * URI parsing or regexes on the main UI thread.
 */
fun formatPlaylistDisplayName(
    name: String,
    type: PlaylistType? = null,
    url: String = "",
    username: String = "",
    password: String = "",
    macAddress: String = "",
    maskDomain: Boolean = false
): String {
    val trimmed = name.trim()
    if (trimmed.isBlank()) return "Playliste"
    if (type == PlaylistType.M3U_FILE) {
        return trimmed
    }

    val cacheKey = "$trimmed|${type?.ordinal ?: -1}|$url|$username|$macAddress|$maskDomain"
    playlistDisplayNameCache.get(cacheKey)?.let { return it }

    val match = DISPLAY_NAME_VPN_PREFIX_REGEX.find(trimmed)
    val vpnPrefix = match?.groupValues?.get(1) ?: ""
    var coreName = trimmed.substring(vpnPrefix.length).trim()

    // Extract host from url if present (fast string slice before URI fallback)
    val urlHost = extractFastHost(url)

    // Replace full http/https URLs with either the host (or masked host)
    if (coreName.contains("http://", ignoreCase = true) || coreName.contains("https://", ignoreCase = true)) {
        coreName = DISPLAY_NAME_URL_REGEX.replace(coreName) { m ->
            val h = extractFastHost(m.value)
            if (maskDomain) maskDomainToken(h) else h
        }.trim()
    }

    if (coreName.contains(':') || coreName.contains('-')) {
        coreName = DISPLAY_NAME_MAC_REGEX.replace(coreName, "").trim()
    }
    if (macAddress.isNotBlank()) {
        coreName = coreName.replace(macAddress, "", ignoreCase = true).trim()
    }
    if (username.isNotBlank() && !username.equals(coreName, ignoreCase = true) && !username.equals(urlHost, ignoreCase = true)) {
        coreName = coreName.replace("($username)", "", ignoreCase = true)
            .replace(username, "", ignoreCase = true)
            .trim()
    }
    if (password.isNotBlank() && !password.equals(urlHost, ignoreCase = true)) {
        coreName = coreName.replace("($password)", "", ignoreCase = true)
            .replace(password, "", ignoreCase = true)
            .trim()
    }

    if (maskDomain) {
        if (urlHost.isNotBlank()) {
            coreName = coreName.replace(urlHost, maskDomainToken(urlHost), ignoreCase = true).trim()
        }
        coreName = DISPLAY_NAME_DOMAIN_PORT_REGEX.replace(coreName) { m ->
            maskDomainToken(m.value)
        }.trim()
    }

    // Remove empty parentheses, ports, or leftover separators from stripped credentials
    coreName = coreName
        .replace(DISPLAY_NAME_PORT_STRIP_REGEX, "")
        .replace(DISPLAY_NAME_EMPTY_PARENS_REGEX, "")
        .replace(DISPLAY_NAME_EMPTY_BRACKETS_REGEX, "")
        .replace(DISPLAY_NAME_MAC_LABEL_REGEX, "")
        .trim()
        .trimStart('-', '|', ':', '•')
        .trimEnd('-', '|', ':', '•')
        .trim()

    val lower = coreName.lowercase()
    if (lower.startsWith("mac portal - ")) {
        coreName = coreName.substring("mac portal - ".length).trim()
    } else if (lower.startsWith("mac portal: ")) {
        coreName = coreName.substring("mac portal: ".length).trim()
    } else if (lower.startsWith("mac portal ")) {
        coreName = coreName.substring("mac portal ".length).trim()
    }

    if (coreName.lowercase().endsWith(" - mac portal")) {
        coreName = coreName.dropLast(" - mac portal".length).trim()
    } else if (coreName.lowercase().endsWith(" mac portal")) {
        coreName = coreName.dropLast(" mac portal".length).trim()
    }

    if (coreName.isBlank() || coreName.equals("mac portal", ignoreCase = true) || coreName.equals("mac", ignoreCase = true) || coreName.equals("xtream", ignoreCase = true) || coreName.equals("m3u", ignoreCase = true)) {
        coreName = when (type) {
            PlaylistType.MAC_PORTAL -> if (!maskDomain && urlHost.isNotBlank()) urlHost else "MAC Portal"
            PlaylistType.XTREAM_CODES -> if (!maskDomain && urlHost.isNotBlank()) urlHost else "Xtream Playliste"
            PlaylistType.M3U_URL, PlaylistType.M3U_FILE -> if (!maskDomain && urlHost.isNotBlank()) urlHost else "M3U Playliste"
            null -> "Playliste"
        }
    }

    val result = "$vpnPrefix$coreName"
    playlistDisplayNameCache.put(cacheKey, result)
    return result
}

private fun extractFastHost(rawUrl: String): String {
    if (rawUrl.isBlank()) return ""
    val startIdx = when {
        rawUrl.startsWith("http://", ignoreCase = true) -> 7
        rawUrl.startsWith("https://", ignoreCase = true) -> 8
        else -> 0
    }
    val sub = rawUrl.substring(startIdx)
    val endIdx = sub.indexOfAny(charArrayOf('/', ':', '?', '#'))
    return (if (endIdx != -1) sub.substring(0, endIdx) else sub).trim()
}

/**
 * Masked URL helper: never returns raw M3U links or portal credentials in unauthenticated views.
 */
fun formatShortDisplayUrl(rawUrl: String, maxLength: Int = 34): String {
    return ""
}

interface ContentItem {
    val id: String
    val playlistId: String
    val name: String
    val streamUrl: String
    val countryCode: String
    val sourceId: String
    val providerId: String
    val title: String get() = name
}

@Entity(
    tableName = "channels",
    indices = [
        Index(value = ["countryCode"]),
        Index(value = ["countryCode", "type"]),
        Index(value = ["countryCode", "type", "groupTitle"]),
        Index(value = ["countryCode", "groupTitle"]),
        Index(value = ["countryCode", "normalizedTitle"]),
        Index(value = ["countryCode", "name"]),
        Index(value = ["countryCode", "type", "name"]),
        Index(value = ["isFavorite"]),
        Index(value = ["isFavorite", "name"]),
        Index(value = ["playlistId"]),
        Index(value = ["playlistId", "countryCode"]),
        Index(value = ["playlistId", "countryCode", "type"]),
        Index(value = ["normalizedTitle"]),
        Index(value = ["playlistId", "type"]),
        Index(value = ["playlistId", "isFavorite"]),
        Index(value = ["isDach", "name"]),
        Index(value = ["playlistId", "isDach", "name"]),
        Index(value = ["countryCode", "isFavorite", "name"]),
        Index(value = ["playlistId", "countryCode", "isFavorite"]),
        Index(value = ["normalizedChannelId"]),
        Index(value = ["tvgId"]),
        Index(value = ["epgId"]),
        Index(value = ["playlistId", "type", "countryCode", "parentCategory", "subCategory"]),
        Index(value = ["playlistId", "countryCode", "providerSortOrder"])
    ]
)
@Immutable
data class ChannelItem(
    @PrimaryKey
    override val id: String = UUID.randomUUID().toString(),
    override val playlistId: String,
    override val name: String,
    override val streamUrl: String,
    val logoUrl: String? = null,
    val groupTitle: String = "Allgemein",
    val type: ContentType = ContentType.LIVE_TV,
    val tvgId: String? = null,
    val tvgName: String? = null,
    val isDach: Boolean = false, // DE, AT, CH priority
    override val countryCode: String = "INT", // DE, AT, CH, ADULT, EXYU, TR, UK, US, etc.
    val seriesTitle: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val plot: String? = null,
    val durationSeconds: Int = 0,
    val resolutionBadge: String = "HD",
    val isFavorite: Boolean = false,
    val lastWatchedTimestamp: Long = 0L,
    val playbackPositionMs: Long = 0L,
    val is24_7: Boolean = false,
    val isVip: Boolean = false,
    // Country Classification Engine fields
    val countryConfidence: Int = 0,
    val classificationStatus: String = "UNKNOWN", // VERIFIED, PROBABLE, UNKNOWN, CONFLICT
    val countryEvidenceJson: String = "",
    val manualCountryOverride: Boolean = false,
    val manualCountry: String? = null,
    val normalizedChannelId: String = "",
    // Normalized IPTV Content fields
    val normalizedTitle: String = "",
    override val sourceId: String = "",
    override val providerId: String = "",
    val language: String? = null,
    val category: String = "Allgemein",
    val genre: String? = null,
    val tvgCountry: String? = null,
    val tvgLanguage: String? = null,
    val epgId: String? = null,
    val providerCategory: String? = null,
    val parentCategory: String = com.example.data.category.ProviderCategoryEngine.parseCategory(providerCategory ?: groupTitle, fallbackName = "").parentCategory,
    val subCategory: String = com.example.data.category.ProviderCategoryEngine.parseCategory(providerCategory ?: groupTitle, fallbackName = "").subCategory,
    val providerSortOrder: Int = 0,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val originalCountry: String? = null,
    val providerCountry: String? = null,
    val catalogCountry: String? = null,
    val lastUpdated: Long = System.currentTimeMillis(),
    // EPG Mapping & Diagnosis fields
    val epgMappingMethod: String = "NONE", // EXACT_TVG_ID, NORMALIZED_ID, ALIAS_MATCH, NORMALIZED_NAME, MANUAL, NONE
    val epgMappingStatus: String = "UNASSIGNED", // MATCHED, AMBIGUOUS, UNASSIGNED, MANUAL
    val epgAmbiguousCandidates: String = "",
    val epgUnassignedReason: String = "",
    val manualEpgTvgId: String? = null,
    val isManualEpgOverride: Boolean = false
) : ContentItem {
    val normalizedCountryCode: String
        get() {
            if (manualCountryOverride && !manualCountry.isNullOrBlank()) return manualCountry
            val raw = ContentClassifier.detectNormalizedCountryCode(countryCode, "", providerCategory ?: groupTitle)
            return if (raw in listOf("UNKNOWN", "CONFLICT", "INT", "")) "OTHER" else raw
        }

    val countryFlag: String
        get() = ContentClassifier.getCountryInfo(normalizedCountryCode, "", providerCategory ?: groupTitle).flag

    /**
     * Strict country sorting rank:
     * 1 = DE: (Deutschland - Top Priority)
     * 2 = AT: (Österreich)
     * 3 = CH: (Schweiz)
     * 4 = ADULT (18+ / Erotik)
     * 5+ = Alle anderen Länder (erst bei Auswahl im Länderselektor synchronisiert)
     */
    val dachSortRank: Int
        get() = ContentClassifier.getCountrySortRank(normalizedCountryCode)

    /**
     * Strict Live TV Channel Ordering:
     * 100-160: Öffentlich-Rechtliche (Das Erste, ZDF, Dritte Programme, 3sat, Arte, KiKa, Phoenix, tagesschau24, ONE, ZDFneo/info, ORF, SRF)
     * 200-224: Große Privatsender / Free-TV (RTL, Sat.1, ProSieben, VOX, Kabel Eins, Tele 5, DMAX...)
     * 300-328: Sky & Pay-TV (Sky Cinema, Sky Atlantic, Sky One, 13th Street, SYFY... OHNE Sport!)
     * 400-450: Sonstige reguläre Sender & Dokus (DACH 400, International 450)
     * 500: 24/7 Streams
     * 600-690: Sportkanäle (Sky Sport, Sky Bundesliga, DAZN, Magenta, Eurosport, Sport1... GANZ AM ENDE!)
     * 900: Adult / 18+ Kanäle
     */
    val liveTvSortRank: Int
        get() = ContentClassifier.getLiveTvSortRank(name, groupTitle, countryCode, isDach)

    /**
     * VOD Language Hierarchy:
     * 1 = Deutschsprachig (DE / AT / CH / German / Deutsch) -> Ganz oben!
     * 2 = Englischsprachig (EN / UK / US / English / Englisch) -> Danach!
     * 3 = Der Rest (Sonstige Sprachen: Französisch, Spanisch, Italienisch, Türkisch, etc.)
     * 4 = Adult / 18+ -> Ganz am Ende
     */
    val vodLanguageRank: Int
        get() = ContentClassifier.getVodLanguageRank(name, groupTitle, countryCode, isDach)

    /**
     * Title with prefixes stripped for clean alphabetical sorting.
     */
    val cleanSortTitle: String
        get() = ContentClassifier.cleanSortTitle(name)
}

@Entity(
    tableName = "provider_categories",
    indices = [
        Index(value = ["playlistId", "contentType", "countryCode", "sortOrder"]),
        Index(value = ["playlistId", "countryCode", "isSynced"])
    ]
)
@Immutable
data class ProviderCategoryEntity(
    @PrimaryKey
    val id: String,
    val playlistId: String,
    val contentType: ContentType,
    val categoryId: String,
    val rawCategoryName: String,
    val countryCode: String,
    val parentCategory: String,
    val subCategory: String,
    val sortOrder: Int = 0,
    val itemCount: Int = 0,
    val isSynced: Boolean = false
)

@Entity(
    tableName = "epg_programs",
    indices = [
        Index(value = ["channelTvgId", "startTime", "endTime"]),
        Index(value = ["channelName", "startTime", "endTime"]),
        Index(value = ["startTime", "endTime"]),
        Index(value = ["startTime"]),
        Index(value = ["endTime"]),
        Index(value = ["sourceUrl"])
    ]
)
@Immutable
data class EpgProgram(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val channelTvgId: String,
    val channelName: String = "",
    val title: String,
    val description: String = "",
    val startTime: Long,
    val endTime: Long,
    val category: String = "Unterhaltung",
    val genre: String? = null,
    val iconUrl: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val rating: String? = null,
    val sourceUrl: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    val progress: Float
        get() {
            val now = System.currentTimeMillis()
            if (now <= startTime) return 0f
            if (now >= endTime) return 1f
            val total = (endTime - startTime).toFloat()
            if (total <= 0f) return 0f
            return ((now - startTime) / total).coerceIn(0f, 1f)
        }

    val isCurrent: Boolean
        get() {
            val now = System.currentTimeMillis()
            return now in startTime..endTime
        }

    val durationMinutes: Int
        get() {
            val diff = (endTime - startTime) / (1000 * 60)
            return if (diff > 0) diff.toInt() else 0
        }
}

const val MAX_VOD_DOWNLOAD_QUEUE_SIZE = 99

enum class RecordingStatus {
    SCHEDULED,
    QUEUED,
    RECORDING,
    DOWNLOADING,
    COMPLETED,
    DOWNLOADED,
    FAILED
}

@Immutable
@Entity(tableName = "recordings")
data class RecordingItem(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val channelName: String,
    val streamUrl: String,
    val contentType: ContentType = ContentType.LIVE_TV,
    val startTime: Long = System.currentTimeMillis(),
    val durationMinutes: Int = 60,
    val status: RecordingStatus = RecordingStatus.COMPLETED,
    val filePath: String? = null,
    val fileSizeFormatted: String = "1.2 GB",
    val progressPercent: Int = 100
)

@Immutable
data class AppSettings(
    val language: String = "de", // "de" or "en"
    val syncOnlyGermanWhenGerman: Boolean = true, // Priority sync: DE:, AT:, CH:, ADULT first; other countries on-demand via Country Selector
    val cachingMode: CachingMode = CachingMode.BALANCED,
    val preferredPlayer: PlayerEngine = PlayerEngine.BUILT_IN_NEXUS,
    val hardwareAcceleration: Boolean = true,
    val backgroundAudioPlayback: Boolean = true,
    val customEpgUrl: String = "https://www.free-epg.de/api/epg/de.xml.gz",
    val epgTimezone: String = "Europe/Berlin", // Default: Deutschland / Berlin (CET/CEST)
    val epgTimeOffsetHours: Int = 0, // Global manual shift (+/- hours)
    val providerEpgOffsets: Map<String, Int> = emptyMap(), // Per-provider / playlist offset in hours (playlistId -> offsetHours)
    val aspectRatio: String = "FILL", // Default: FILL (Vollbild) for Nexus Player
    val nexusFullScreenEnabled: Boolean = true, // Immer echter Vollbildmodus im Nexus Player
    val autoUpdateOnStart: Boolean = false, // Updates nicht beim Start vom Server holen, sondern lokal gespeicherte Playlisten sofort laden
    val masterPassword: String = ""
)

@Immutable
data class PlaylistUpdateNoticeState(
    val isVisible: Boolean = false,
    val title: String = "Playlist wird aktualisiert...",
    val message: String = "Deutsche Sender werden geladen und sortiert...",
    val playlistName: String = "",
    val isLoading: Boolean = true,
    val isReady: Boolean = false,
    val germanCount: Int = 0,
    val isError: Boolean = false
)

@Immutable
data class MacPortalConnectionDialogState(
    val isVisible: Boolean = true,
    val playlistName: String = "",
    val portalUrl: String = "",
    val macAddress: String = "",
    val playlistId: String = "",
    val stepTitle: String = "Verbinde mit MAC Portal...",
    val stepDetail: String = "Prüfe Portal-URL und Authentifizierung...",
    val liveCount: Int = 0,
    val movieCount: Int = 0,
    val seriesCount: Int = 0,
    val isLoading: Boolean = true,
    val isFinished: Boolean = false,
    val isTimeoutOrRejected: Boolean = false,
    val errorMessage: String? = null
)

