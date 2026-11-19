package com.example.data.livetvlight

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.example.data.model.ChannelItem
import com.example.data.model.ContentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

const val LIVE_TV_LIGHT_PLAYLIST_ID = "live_tv_light"

data class CatalogMergeResult(
    val totalChannelsCount: Int,
    val matchedWithLive2ix2Count: Int,
    val addedNewChannelsCount: Int,
    val logosUpdatedCount: Int
)

@Immutable
data class LiveTvLightChannel(
    val id: String,
    val name: String,
    val category: String,
    val countryCode: String,
    val logoUrl: String,
    val logoBadge: String,
    val brandColor: Color,
    val primaryStreamUrl: String,
    val fallbackStreamUrls: List<String> = emptyList(),
    val internalResolverKey: String = "",
    val alternateResolverKeys: List<String> = emptyList(),
    val tvgId: String = "",
    val supportsInstantStart: Boolean = false,
    val pageUrl: String = "",
    val fallbackLogoUrl: String = ""
) {
    val searchKey: String = "$name $logoBadge $category $countryCode".lowercase()

    val effectivePageUrl: String
        get() = when {
            pageUrl.isNotBlank() -> pageUrl
            internalResolverKey.isNotBlank() -> "${LiveTvLight2ix2Scraper.BASE_URL}/${internalResolverKey.trim('/')}/"
            else -> ""
        }

    val effectiveLogoUrl: String
        get() = logoUrl.ifBlank { fallbackLogoUrl }

    fun allResolverSlugs(dynamicOverride: String? = null): List<String> {
        val pageSlug = pageUrl
            .removePrefix("https://www.2ix2.com/")
            .removePrefix("http://www.2ix2.com/")
            .removePrefix("https://2ix2.com/")
            .removePrefix("http://2ix2.com/")
            .trim('/')
            .takeIf { it.isNotBlank() && !it.contains("/") }
        return buildList {
            if (!dynamicOverride.isNullOrBlank()) add(dynamicOverride.trim('/'))
            if (!pageSlug.isNullOrBlank()) add(pageSlug)
            if (internalResolverKey.isNotBlank()) add(internalResolverKey.trim('/'))
            alternateResolverKeys.forEach { if (it.isNotBlank()) add(it.trim('/')) }
        }.distinct()
    }

    fun toChannelItem(resolvedUrl: String = primaryStreamUrl): ChannelItem {
        return ChannelItem(
            id = "ltv_light_$id",
            playlistId = LIVE_TV_LIGHT_PLAYLIST_ID,
            name = name,
            streamUrl = resolvedUrl,
            logoUrl = effectiveLogoUrl,
            groupTitle = category,
            type = ContentType.LIVE_TV,
            tvgId = tvgId,
            countryCode = countryCode
        )
    }
}

/**
 * Reaktiver 2ix2.com-Katalog für Live TV light:
 * - Offline-Seed mit 47 DE + 11 AT Sendern (= 58 Sender) für sofortigen Erststart (0 ms)
 * - Persistenter JSON-Cache (LiveTvLightCatalogStore) für sofortigen Start mit echten 2ix2-URLs & Originallogos
 * - Live-Import aller Seiten von 2ix2.com (DE + AT) inkl. exakter pageUrl und originaler Senderlogos
 */
object LiveTvLightCatalog {

    private const val LOGO_DE = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/germany"
    private const val LOGO_AT = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/austria"
    private const val LOGO_US = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/united-states"

    private val dynamicSlugOverrides = ConcurrentHashMap<String, String>()
    private val cacheInitialized = AtomicBoolean(false)

    val seedGermanChannels: List<LiveTvLightChannel> = listOf(
        // 1. Öffentlich-Rechtlich (5)
        LiveTvLightChannel(
            id = "das_erste",
            name = "Das Erste HD",
            category = "Öffentlich-Rechtlich",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/das-erste-de.png",
            logoBadge = "ARD",
            brandColor = Color(0xFF00519E),
            primaryStreamUrl = "https://s6.hopslan.com/ardX/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/ardX/index.m3u8",
                "https://daserste-live.ard-mcdn.de/daserste/live/hls/int/master.m3u8",
                "https://daserste-live.ard-mcdn.de/daserste/live/hls/de/master.m3u8",
                "https://derste247livede.akamaized.net/hls/live/658317/daserste_de/master.m3u8"
            ),
            internalResolverKey = "ard-de",
            alternateResolverKeys = listOf("ard", "ard-live", "das-erste", "das-erste-live-stream"),
            tvgId = "DasErste.de"
        ),
        LiveTvLightChannel(
            id = "zdf",
            name = "ZDF HD",
            category = "Öffentlich-Rechtlich",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/zdf-de.png",
            logoBadge = "ZDF",
            brandColor = Color(0xFFFA7D19),
            primaryStreamUrl = "https://s6.hopslan.com/zdfX1/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/zdfX1/index.m3u8",
                "https://zdf-hls-15.akamaized.net/hls/live/2016498/none/veryhigh/master.m3u8",
                "https://zdf-hls-15.akamaized.net/hls/live/2016498/de/veryhigh/master.m3u8",
                "https://zdf-hls-15.akamaized.net/hls/live/2016498/de/high/master.m3u8"
            ),
            internalResolverKey = "zdf-de",
            alternateResolverKeys = listOf("zdf", "zdf-live", "zdf-live-stream"),
            tvgId = "ZDF.de"
        ),
        LiveTvLightChannel(
            id = "zdf_neo",
            name = "ZDFneo HD",
            category = "Öffentlich-Rechtlich",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/zdf-neo-de.png",
            logoBadge = "neo",
            brandColor = Color(0xFF009B72),
            primaryStreamUrl = "https://s6.hopslan.com/zdfneoX/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/zdfneoX/index.m3u8",
                "https://zdf-hls-16.akamaized.net/hls/live/2016499/none/veryhigh/master.m3u8",
                "https://zdf-hls-16.akamaized.net/hls/live/2016499/de/veryhigh/master.m3u8",
                "https://zdf-hls-16.akamaized.net/hls/live/2016499/de/high/master.m3u8"
            ),
            internalResolverKey = "zdfneo",
            alternateResolverKeys = listOf("zdf-neo", "zdfneo-live-stream"),
            tvgId = "ZDFneo.de"
        ),
        LiveTvLightChannel(
            id = "zdf_info",
            name = "ZDFinfo HD",
            category = "Öffentlich-Rechtlich",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/zdf-info-de.png",
            logoBadge = "info",
            brandColor = Color(0xFF0369A1),
            primaryStreamUrl = "https://s6.hopslan.com/zdfinfoX/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/zdfinfoX/index.m3u8",
                "https://zdf-hls-17.akamaized.net/hls/live/2016500/none/veryhigh/master.m3u8",
                "https://zdf-hls-17.akamaized.net/hls/live/2016500/de/veryhigh/master.m3u8",
                "https://zdf-hls-17.akamaized.net/hls/live/2016500/de/high/master.m3u8"
            ),
            internalResolverKey = "zdfinfo",
            alternateResolverKeys = listOf("zdf-info", "zdfinfo-live-stream"),
            tvgId = "ZDFinfo.de"
        ),
        LiveTvLightChannel(
            id = "one",
            name = "ONE HD",
            category = "Öffentlich-Rechtlich",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/one-de.png",
            logoBadge = "ONE",
            brandColor = Color(0xFF7B2CBF),
            primaryStreamUrl = "https://s6.hopslan.com/onetv/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/onetv/index.m3u8",
                "https://mcdn-one.ard.de/ardone/hls/master.m3u8",
                "https://mcdn.one.ard.de/ardone/hls/master.m3u8"
            ),
            internalResolverKey = "one-tv",
            alternateResolverKeys = listOf("one", "ard-one", "one-live-stream"),
            tvgId = "ONE.de"
        ),

        // 2. Privatsender (18)
        LiveTvLightChannel(
            id = "rtl",
            name = "RTL",
            category = "Privatsender",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/rtl-de.png",
            logoBadge = "RTL",
            brandColor = Color(0xFFE30613),
            primaryStreamUrl = "https://s6.hopslan.com/rtlt11/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/rtlt11/index.m3u8",
                "https://s1.hopslan.com/rtl/index.m3u8"
            ),
            internalResolverKey = "rtl-live",
            alternateResolverKeys = listOf("rtl", "rtl-live-stream"),
            tvgId = "RTL.de"
        ),
        LiveTvLightChannel(
            id = "rtl2",
            name = "RTL II",
            category = "Privatsender",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/rtl-zwei-de.png",
            logoBadge = "RTL2",
            brandColor = Color(0xFF00A8E8),
            primaryStreamUrl = "https://s6.hopslan.com/rtl2q/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/rtl2q/index.m3u8"
            ),
            internalResolverKey = "rtl2-live",
            alternateResolverKeys = listOf("rtl2", "rtl-2", "rtl2-live-stream"),
            tvgId = "RTL2.de"
        ),
        LiveTvLightChannel(
            id = "vox",
            name = "VOX",
            category = "Privatsender",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/vox-de.png",
            logoBadge = "VOX",
            brandColor = Color(0xFFC8102E),
            primaryStreamUrl = "https://s6.hopslan.com/voxz/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/voxz/index.m3u8"
            ),
            internalResolverKey = "vox",
            alternateResolverKeys = listOf("vox-live", "vox-live-stream"),
            tvgId = "VOX.de"
        ),
        LiveTvLightChannel(
            id = "rtlup",
            name = "RTLup",
            category = "Privatsender",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/rtl-up-de.png",
            logoBadge = "RTLup",
            brandColor = Color(0xFF9B1C31),
            primaryStreamUrl = "https://s6.hopslan.com/rtlplusX/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/rtlplusX/index.m3u8",
                "https://s6.hopslan.com/rtlupX/tracks-v1a1/mono.ts.m3u8"
            ),
            internalResolverKey = "rtlup",
            alternateResolverKeys = listOf("rtl-plus", "rtlplus", "rtl-up", "rtlup-live"),
            tvgId = "RTLup.de"
        ),
        LiveTvLightChannel(
            id = "voxup",
            name = "VOXup",
            category = "Privatsender",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/vox-up-de.png",
            logoBadge = "VOXup",
            brandColor = Color(0xFF9F1239),
            primaryStreamUrl = "https://s6.hopslan.com/voxupX/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/voxupX/index.m3u8"
            ),
            internalResolverKey = "voxup",
            alternateResolverKeys = listOf("vox-up", "voxup-live"),
            tvgId = "VOXup.de"
        ),
        LiveTvLightChannel(
            id = "nitro",
            name = "NITRO",
            category = "Privatsender",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/nitro-de.png",
            logoBadge = "NITRO",
            brandColor = Color(0xFFFF5500),
            primaryStreamUrl = "https://s6.hopslan.com/nitro1/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/nitro1/index.m3u8"
            ),
            internalResolverKey = "rtl-nitro-tv",
            alternateResolverKeys = listOf("rtl-nitro", "nitro", "nitro-live-stream"),
            tvgId = "NITRO.de"
        ),
        LiveTvLightChannel(
            id = "super_rtl",
            name = "Super RTL",
            category = "Privatsender",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/super-rtl-de.png",
            logoBadge = "SRTL",
            brandColor = Color(0xFFFF4500),
            primaryStreamUrl = "https://s6.hopslan.com/superX/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/superX/index.m3u8"
            ),
            internalResolverKey = "super-rtl-live",
            alternateResolverKeys = listOf("super-rtl", "super-rtl-live-stream"),
            tvgId = "SuperRTL.de"
        ),
        LiveTvLightChannel(
            id = "prosieben",
            name = "ProSieben",
            category = "Privatsender",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/prosieben-de.png",
            logoBadge = "PRO7",
            brandColor = Color(0xFFE60000),
            primaryStreamUrl = "https://s6.hopslan.com/pro7z/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/pro7z/index.m3u8"
            ),
            internalResolverKey = "pro7",
            alternateResolverKeys = listOf("prosieben", "pro7-live", "prosieben-live-stream"),
            tvgId = "ProSieben.de"
        ),
        LiveTvLightChannel(
            id = "sat1",
            name = "SAT.1",
            category = "Privatsender",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/sat-1-de.png",
            logoBadge = "SAT.1",
            brandColor = Color(0xFF009FE3),
            primaryStreamUrl = "https://s6.hopslan.com/sat1z/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/sat1z/index.m3u8"
            ),
            internalResolverKey = "sat-1",
            alternateResolverKeys = listOf("sat1", "sat1-live", "sat1-live-stream"),
            tvgId = "Sat1.de"
        ),
        LiveTvLightChannel(
            id = "kabel_eins",
            name = "Kabel Eins",
            category = "Privatsender",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/kabel-eins-de.png",
            logoBadge = "K1",
            brandColor = Color(0xFFFF6600),
            primaryStreamUrl = "https://s6.hopslan.com/kabel1z/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/kabel1z/index.m3u8"
            ),
            internalResolverKey = "kabel-1",
            alternateResolverKeys = listOf("kabel-eins", "kabel1", "kabel-eins-live-stream"),
            tvgId = "KabelEins.de"
        ),
        LiveTvLightChannel(
            id = "sixx",
            name = "sixx",
            category = "Privatsender",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/sixx-de.png",
            logoBadge = "SIXX",
            brandColor = Color(0xFF84CC16),
            primaryStreamUrl = "https://s6.hopslan.com/sixX/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/sixX/index.m3u8"
            ),
            internalResolverKey = "sixx",
            alternateResolverKeys = listOf("sixx-live", "sixx-tv"),
            tvgId = "sixx.de"
        ),
        LiveTvLightChannel(
            id = "prosieben_maxx",
            name = "ProSieben MAXX",
            category = "Privatsender",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/prosieben-maxx-de.png",
            logoBadge = "MAXX",
            brandColor = Color(0xFF1E3A8A),
            primaryStreamUrl = "https://s6.hopslan.com/pro7maxxX/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/pro7maxxX/index.m3u8"
            ),
            internalResolverKey = "prosieben-maxx-tv",
            alternateResolverKeys = listOf("prosieben-maxx", "pro7-maxx", "pro7maxx"),
            tvgId = "ProSiebenMaxx.de"
        ),
        LiveTvLightChannel(
            id = "sat1_gold",
            name = "SAT.1 Gold",
            category = "Privatsender",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/sat-1-gold-de.png",
            logoBadge = "GOLD",
            brandColor = Color(0xFFD97706),
            primaryStreamUrl = "https://s6.hopslan.com/goldX/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/goldX/index.m3u8"
            ),
            internalResolverKey = "sat1-gold",
            alternateResolverKeys = listOf("sat-1-gold", "sat1-gold-live"),
            tvgId = "Sat1Gold.de"
        ),
        LiveTvLightChannel(
            id = "tele5",
            name = "TELE 5",
            category = "Privatsender",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/tele-5-de.png",
            logoBadge = "T5",
            brandColor = Color(0xFF9333EA),
            primaryStreamUrl = "https://s6.hopslan.com/tele5X/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/tele5X/index.m3u8"
            ),
            internalResolverKey = "tele-5",
            alternateResolverKeys = listOf("tele5", "tele-5-live"),
            tvgId = "Tele5.de"
        ),
        LiveTvLightChannel(
            id = "dmax",
            name = "DMAX",
            category = "Privatsender",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/dmax-de.png",
            logoBadge = "DMAX",
            brandColor = Color(0xFFFF8800),
            primaryStreamUrl = "https://s6.hopslan.com/dmaxX/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/dmaxX/index.m3u8"
            ),
            internalResolverKey = "dmax",
            alternateResolverKeys = listOf("dmax-live", "dmax-live-stream"),
            tvgId = "DMAX.de"
        ),
        LiveTvLightChannel(
            id = "tlc",
            name = "TLC Deutschland",
            category = "Privatsender",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/tlc-de.png",
            logoBadge = "TLC",
            brandColor = Color(0xFFEC4899),
            primaryStreamUrl = "https://s6.hopslan.com/tlcX/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/tlcX/index.m3u8"
            ),
            internalResolverKey = "tlc",
            alternateResolverKeys = listOf("tlc-live", "tlc-deutschland"),
            tvgId = "TLC.de"
        ),
        LiveTvLightChannel(
            id = "comedy_central",
            name = "Comedy Central",
            category = "Privatsender",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/comedy-central-de.png",
            logoBadge = "CC",
            brandColor = Color(0xFFD97706),
            primaryStreamUrl = "https://s6.hopslan.com/vivaX/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/vivaX/index.m3u8"
            ),
            internalResolverKey = "comedy-central",
            alternateResolverKeys = listOf("comedy-central-live"),
            tvgId = "ComedyCentral.de"
        ),
        LiveTvLightChannel(
            id = "mtv",
            name = "MTV Deutschland",
            category = "Privatsender",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/mtv-de.png",
            logoBadge = "MTV",
            brandColor = Color(0xFFE11D48),
            primaryStreamUrl = "https://s6.hopslan.com/mtvX/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/mtvX/index.m3u8"
            ),
            internalResolverKey = "mtv",
            alternateResolverKeys = listOf("mtv-live", "mtv-germany"),
            tvgId = "MTV.de"
        ),

        // 3. Nachrichten & Info (6)
        LiveTvLightChannel(
            id = "tagesschau24",
            name = "tagesschau24 HD",
            category = "Nachrichten & Info",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/tagesschau24-de.png",
            logoBadge = "TS24",
            brandColor = Color(0xFF003B7E),
            primaryStreamUrl = "https://tagesschau.akamaized.net/hls/live/2020115/tagesschau/tagesschau_1/master.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/tagesschauX/tracks-v1a1/mono.ts.m3u8",
                "https://s6.hopslan.com/tagesschauX/index.m3u8"
            ),
            internalResolverKey = "tagesschau",
            alternateResolverKeys = listOf("tagesschau24", "tagesschau24-live-stream"),
            tvgId = "tagesschau24.de"
        ),
        LiveTvLightChannel(
            id = "welt",
            name = "WELT",
            category = "Nachrichten & Info",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/welt-de.png",
            logoBadge = "WELT",
            brandColor = Color(0xFF004B87),
            primaryStreamUrl = "https://s6.hopslan.com/n24X/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/n24X/index.m3u8",
                "https://live2.welt.de/live2/hls/master.m3u8"
            ),
            internalResolverKey = "welt",
            alternateResolverKeys = listOf("n24", "welt-live", "welt-live-stream"),
            tvgId = "WELT.de"
        ),
        LiveTvLightChannel(
            id = "ntv",
            name = "n-tv",
            category = "Nachrichten & Info",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/n-tv-de.png",
            logoBadge = "n-tv",
            brandColor = Color(0xFFB91C1C),
            primaryStreamUrl = "https://s6.hopslan.com/ntvX/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/ntvX/index.m3u8"
            ),
            internalResolverKey = "n-tv-live",
            alternateResolverKeys = listOf("n-tv", "ntv", "ntv-live-stream"),
            tvgId = "ntv.de"
        ),
        LiveTvLightChannel(
            id = "phoenix",
            name = "PHOENIX HD",
            category = "Nachrichten & Info",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/phoenix-de.png",
            logoBadge = "PHX",
            brandColor = Color(0xFF0284C7),
            primaryStreamUrl = "https://s6.hopslan.com/phoenixX/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/phoenixX/index.m3u8",
                "https://zdf-hls-19.akamaized.net/hls/live/2016502/none/veryhigh/master.m3u8",
                "https://zdf-hls-19.akamaized.net/hls/live/2016502/de/veryhigh/master.m3u8",
                "https://zdf-hls-19.akamaized.net/hls/live/2016502/de/high/master.m3u8"
            ),
            internalResolverKey = "phoenix",
            alternateResolverKeys = listOf("phoenix-live", "phoenix-live-stream"),
            tvgId = "Phoenix.de"
        ),
        LiveTvLightChannel(
            id = "dw_deutsch",
            name = "DW Deutsch HD",
            category = "Nachrichten & Info",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/dw-de.png",
            logoBadge = "DW",
            brandColor = Color(0xFF0066B3),
            primaryStreamUrl = "https://dwamdstream104.akamaized.net/hls/live/2015530/dwstream104/index.m3u8",
            fallbackStreamUrls = listOf(
                "https://dwamdstream102.akamaized.net/hls/live/2015525/dwstream102/index.m3u8"
            ),
            internalResolverKey = "deutsche-welle",
            alternateResolverKeys = listOf("dw", "dw-deutsch", "deutsche-welle-live"),
            tvgId = "DW.de"
        ),
        LiveTvLightChannel(
            id = "bloomberg_tv",
            name = "Bloomberg TV",
            category = "Nachrichten & Info",
            countryCode = "DE",
            logoUrl = "$LOGO_US/bloomberg-television-us.png",
            logoBadge = "BBG",
            brandColor = Color(0xFF1F2937),
            primaryStreamUrl = "https://www.bloomberg.com/media-manifest/streams/eu.m3u8",
            fallbackStreamUrls = listOf(
                "https://bloomberg-bloomberg-1-eu.rakuten.wurl.tv/playlist.m3u8"
            ),
            internalResolverKey = "bloomberg-tv",
            alternateResolverKeys = listOf("bloomberg", "bloomberg-live"),
            tvgId = "Bloomberg.eu"
        ),

        // 4. Doku & Kultur (4)
        LiveTvLightChannel(
            id = "arte",
            name = "ARTE Deutsch HD",
            category = "Doku & Kultur",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/arte-de.png",
            logoBadge = "ARTE",
            brandColor = Color(0xFFFA481C),
            primaryStreamUrl = "https://s6.hopslan.com/arteX/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/arteX/index.m3u8",
                "https://artesimulcast.akamaized.net/hls/live/2031003/artelive_de/master.m3u8"
            ),
            internalResolverKey = "arte",
            alternateResolverKeys = listOf("arte-live", "arte-live-stream"),
            tvgId = "ARTE.de"
        ),
        LiveTvLightChannel(
            id = "3sat",
            name = "3sat HD",
            category = "Doku & Kultur",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/3sat-de.png",
            logoBadge = "3sat",
            brandColor = Color(0xFFDC2626),
            primaryStreamUrl = "https://s6.hopslan.com/3sat/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/3sat/index.m3u8",
                "https://zdf-hls-18.akamaized.net/hls/live/2016501/none/veryhigh/master.m3u8",
                "https://zdf-hls-18.akamaized.net/hls/live/2016501/de/veryhigh/master.m3u8",
                "https://zdf-hls-18.akamaized.net/hls/live/2016501/de/high/master.m3u8"
            ),
            internalResolverKey = "3sat",
            alternateResolverKeys = listOf("3sat-live", "3sat-live-stream"),
            tvgId = "3sat.de"
        ),
        LiveTvLightChannel(
            id = "kabel_eins_doku",
            name = "Kabel Eins Doku",
            category = "Doku & Kultur",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/kabel-eins-doku-de.png",
            logoBadge = "K1D",
            brandColor = Color(0xFFB45309),
            primaryStreamUrl = "https://s6.hopslan.com/dokuX/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/dokuX/index.m3u8"
            ),
            internalResolverKey = "kabel-1-doku",
            alternateResolverKeys = listOf("kabel-eins-doku", "kabel1-doku"),
            tvgId = "KabelEinsDoku.de"
        ),
        LiveTvLightChannel(
            id = "welt_der_wunder",
            name = "Welt der Wunder",
            category = "Doku & Kultur",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/welt-der-wunder-de.png",
            logoBadge = "WdW",
            brandColor = Color(0xFF0E7490),
            primaryStreamUrl = "https://s6.hopslan.com/wdwX/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/wdwX/index.m3u8"
            ),
            internalResolverKey = "welt-der-wunder",
            alternateResolverKeys = listOf("welt-der-wunder-tv", "welt-der-wunder-live"),
            tvgId = "WeltDerWunder.de"
        ),

        // 5. Sport (2)
        LiveTvLightChannel(
            id = "sport1",
            name = "SPORT1",
            category = "Sport",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/sport1-de.png",
            logoBadge = "SP1",
            brandColor = Color(0xFFEAB308),
            primaryStreamUrl = "https://s6.hopslan.com/sport1X/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/sport1X/index.m3u8",
                "https://s6.hopslan.com/sport1/tracks-v1a1/mono.ts.m3u8"
            ),
            internalResolverKey = "sport1",
            alternateResolverKeys = listOf("sport-1", "sport1-live", "sport1-live-stream"),
            tvgId = "Sport1.de"
        ),
        LiveTvLightChannel(
            id = "sky_sport_news",
            name = "Sky Sport News HD",
            category = "Sport",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/sky-sport-news-de.png",
            logoBadge = "SSN",
            brandColor = Color(0xFF1D4ED8),
            primaryStreamUrl = "https://s6.hopslan.com/skysportnewsX/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/skysportnewsX/index.m3u8"
            ),
            internalResolverKey = "sky-sport-news-hd",
            alternateResolverKeys = listOf("sky-sport-news", "sky-sport-news-live"),
            tvgId = "SkySportNews.de"
        ),

        // 6. Kinder & Familie (2)
        LiveTvLightChannel(
            id = "kika",
            name = "KiKA HD",
            category = "Kinder & Familie",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/kika-de.png",
            logoBadge = "KiKA",
            brandColor = Color(0xFF16A34A),
            primaryStreamUrl = "https://s6.hopslan.com/kikaX/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/kikaX/index.m3u8",
                "https://kika-hls.akamaized.net/hls/live/2022693/live/master.m3u8"
            ),
            internalResolverKey = "kika",
            alternateResolverKeys = listOf("kika-live", "kika-live-stream"),
            tvgId = "KiKA.de"
        ),
        LiveTvLightChannel(
            id = "disney_channel",
            name = "Disney Channel",
            category = "Kinder & Familie",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/disney-channel-de.png",
            logoBadge = "DISNEY",
            brandColor = Color(0xFF2563EB),
            primaryStreamUrl = "https://s6.hopslan.com/disneyX/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/disneyX/index.m3u8"
            ),
            internalResolverKey = "disney-channel",
            alternateResolverKeys = listOf("disney", "disney-channel-live"),
            tvgId = "DisneyChannel.de"
        ),

        // 7. Regional / Drittes Programm (10)
        LiveTvLightChannel(
            id = "wdr",
            name = "WDR HD",
            category = "Drittes Programm",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/wdr-de.png",
            logoBadge = "WDR",
            brandColor = Color(0xFF003865),
            primaryStreamUrl = "https://s6.hopslan.com/wdrX/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/wdrX/index.m3u8",
                "https://wdr-live.ard-mcdn.de/wdr/live/hls/de/master.m3u8",
                "https://mcdn.wdr.de/wdr/wdrfs/de/master.m3u8",
                "https://wdrfs247.akamaized.net/hls/live/681509/wdr_msl4_fs247/master.m3u8"
            ),
            internalResolverKey = "wdr-fernsehen",
            alternateResolverKeys = listOf("wdr", "wdr-live", "wdr-live-stream"),
            tvgId = "WDR.de"
        ),
        LiveTvLightChannel(
            id = "ndr",
            name = "NDR HD",
            category = "Drittes Programm",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/ndr-de.png",
            logoBadge = "NDR",
            brandColor = Color(0xFF003D7A),
            primaryStreamUrl = "https://s6.hopslan.com/ndrX/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/ndrX/index.m3u8",
                "https://mcdn.ndr.de/ndr/hls/ndr_fs/ndr_nds/master.m3u8",
                "https://ndrfs-lh.akamaihd.net/i/ndrfs_nds@430233/master.m3u8"
            ),
            internalResolverKey = "ndr-fernsehen",
            alternateResolverKeys = listOf("ndr", "ndr-live", "ndr-live-stream"),
            tvgId = "NDR.de"
        ),
        LiveTvLightChannel(
            id = "swr",
            name = "SWR HD",
            category = "Drittes Programm",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/swr-de.png",
            logoBadge = "SWR",
            brandColor = Color(0xFF0F172A),
            primaryStreamUrl = "https://s6.hopslan.com/swrX/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/swrX/index.m3u8",
                "https://swrbwd-hls.akamaized.net/hls/live/2018672/swrbwd/master.m3u8",
                "https://swrrpd-hls.akamaized.net/hls/live/2018676/swrrpd/master.m3u8"
            ),
            internalResolverKey = "swr-fernsehen",
            alternateResolverKeys = listOf("swr", "swr-live", "swr-live-stream"),
            tvgId = "SWR.de"
        ),
        LiveTvLightChannel(
            id = "br",
            name = "BR Fernsehen HD",
            category = "Drittes Programm",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/br-de.png",
            logoBadge = "BR",
            brandColor = Color(0xFF0066CC),
            primaryStreamUrl = "https://s6.hopslan.com/brX/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/brX/index.m3u8",
                "https://mcdn.br.de/br/fs/bfs_sued/hls/de/master.m3u8",
                "https://mcdn.br.de/br/fs/bfs_nord/hls/de/master.m3u8"
            ),
            internalResolverKey = "br-fernsehen",
            alternateResolverKeys = listOf("br", "br-live", "br-live-stream"),
            tvgId = "BR.de"
        ),
        LiveTvLightChannel(
            id = "mdr",
            name = "MDR HD",
            category = "Drittes Programm",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/mdr-de.png",
            logoBadge = "MDR",
            brandColor = Color(0xFF0055A5),
            primaryStreamUrl = "https://s6.hopslan.com/mdrX/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/mdrX/index.m3u8",
                "https://mdrtvsnhls.akamaized.net/hls/live/2016928/mdrtvsn/master.m3u8",
                "https://mdrtvsahls.akamaized.net/hls/live/2016879/mdrtvsa/master.m3u8",
                "https://mdrtvthhls.akamaized.net/hls/live/2016880/mdrtvth/master.m3u8"
            ),
            internalResolverKey = "mdr-fernsehen",
            alternateResolverKeys = listOf("mdr", "mdr-live", "mdr-live-stream"),
            tvgId = "MDR.de"
        ),
        LiveTvLightChannel(
            id = "hr",
            name = "hr-fernsehen HD",
            category = "Drittes Programm",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/hr-de.png",
            logoBadge = "HR",
            brandColor = Color(0xFF004C97),
            primaryStreamUrl = "https://s6.hopslan.com/hrX/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/hrX/index.m3u8",
                "https://hrhls.akamaized.net/hls/live/2024525/hrhls/master.m3u8"
            ),
            internalResolverKey = "hr-fernsehen",
            alternateResolverKeys = listOf("hr", "hr-live", "hr-live-stream"),
            tvgId = "HR.de"
        ),
        LiveTvLightChannel(
            id = "rbb",
            name = "rbb Fernsehen HD",
            category = "Drittes Programm",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/rbb-de.png",
            logoBadge = "rbb",
            brandColor = Color(0xFFD90429),
            primaryStreamUrl = "https://s6.hopslan.com/rbbX/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/rbbX/index.m3u8",
                "https://rbb-hls-berlin.akamaized.net/hls/live/2017824/rbb_berlin/master.m3u8",
                "https://rbb-hls-brandenburg.akamaized.net/hls/live/2017825/rbb_brandenburg/master.m3u8"
            ),
            internalResolverKey = "rbb-fernsehen",
            alternateResolverKeys = listOf("rbb", "rbb-live", "rbb-live-stream"),
            tvgId = "RBB.de"
        ),
        LiveTvLightChannel(
            id = "sr",
            name = "SR Fernsehen HD",
            category = "Drittes Programm",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/sr-de.png",
            logoBadge = "SR",
            brandColor = Color(0xFF00519E),
            primaryStreamUrl = "https://s6.hopslan.com/srX/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/srX/index.m3u8",
                "https://srfshls.akamaized.net/hls/live/2020118/srfs/master.m3u8"
            ),
            internalResolverKey = "sr-fernsehen",
            alternateResolverKeys = listOf("sr", "sr-live", "sr-live-stream"),
            tvgId = "SR.de"
        ),
        LiveTvLightChannel(
            id = "alex_berlin",
            name = "ALEX Berlin",
            category = "Drittes Programm",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/alex-berlin-de.png",
            logoBadge = "ALEX",
            brandColor = Color(0xFF009688),
            primaryStreamUrl = "https://s4.hopslan.com/alex/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s4.hopslan.com/alex/index.m3u8",
                "https://alex-stream.rosebud-media.de/live/_definst_/smil:alexlivetv.smil/playlist.m3u8"
            ),
            internalResolverKey = "alex-berlin",
            alternateResolverKeys = listOf("alex", "alex-berlin-live"),
            tvgId = "ALEXBerlin.de"
        ),
        LiveTvLightChannel(
            id = "mdf1",
            name = "MDF.1",
            category = "Drittes Programm",
            countryCode = "DE",
            logoUrl = "$LOGO_DE/mdf-1-de.png",
            logoBadge = "MDF1",
            brandColor = Color(0xFF0284C7),
            primaryStreamUrl = "https://s6.hopslan.com/mdf1/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/mdf1/index.m3u8",
                "https://58bd5b7a98e04.streamlock.net/medienportal/livestream/playlist.m3u8"
            ),
            internalResolverKey = "mdf-1",
            alternateResolverKeys = listOf("mdf1", "mdf-1-live"),
            tvgId = "MDF1.de"
        )
    )

    // 8. Österreichische TV-Sender (Exakt 11 Seed-Sender – geo-freie 2ix2-Priorität!)
    val seedAustrianChannels: List<LiveTvLightChannel> = listOf(
        LiveTvLightChannel(
            id = "orf1",
            name = "ORF 1 HD",
            category = "Österreich",
            countryCode = "AT",
            logoUrl = "$LOGO_AT/orf-1-at.png",
            logoBadge = "ORF1",
            brandColor = Color(0xFFDC2626),
            primaryStreamUrl = "https://s6.hopslan.com/orf1X/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/orf1X/index.m3u8",
                "https://orf1.mdn.ors.at/out/u/orf1/qxb/manifest.m3u8"
            ),
            internalResolverKey = "orf1",
            alternateResolverKeys = listOf("orf-1", "orf1-live", "orf1-live-stream"),
            tvgId = "ORF1.at"
        ),
        LiveTvLightChannel(
            id = "orf2",
            name = "ORF 2 HD",
            category = "Österreich",
            countryCode = "AT",
            logoUrl = "$LOGO_AT/orf-2-at.png",
            logoBadge = "ORF2",
            brandColor = Color(0xFFB91C1C),
            primaryStreamUrl = "https://s6.hopslan.com/orf2X/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/orf2X/index.m3u8",
                "https://orf2.mdn.ors.at/out/u/orf2/qxb/manifest.m3u8"
            ),
            internalResolverKey = "orf2",
            alternateResolverKeys = listOf("orf-2", "orf2-live", "orf2-live-stream"),
            tvgId = "ORF2.at"
        ),
        LiveTvLightChannel(
            id = "orf3",
            name = "ORF III HD",
            category = "Österreich",
            countryCode = "AT",
            logoUrl = "$LOGO_AT/orf-3-at.png",
            logoBadge = "ORF3",
            brandColor = Color(0xFF991B1B),
            primaryStreamUrl = "https://s6.hopslan.com/orf3X/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/orf3X/index.m3u8",
                "https://orf3.mdn.ors.at/out/u/orf3/qxb/manifest.m3u8"
            ),
            internalResolverKey = "orf3",
            alternateResolverKeys = listOf("orf-3", "orf-iii", "orf3-live"),
            tvgId = "ORF3.at"
        ),
        LiveTvLightChannel(
            id = "servustv_at",
            name = "ServusTV",
            category = "Österreich",
            countryCode = "AT",
            logoUrl = "$LOGO_AT/servus-tv-at.png",
            logoBadge = "SERVUS",
            brandColor = Color(0xFF1D4ED8),
            primaryStreamUrl = "https://s6.hopslan.com/servusX/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://s6.hopslan.com/servusX/index.m3u8",
                "https://rbmn-live.akamaized.net/hls/live/2002825/geoSTVDEweb/master.m3u8",
                "https://rbmn-live.akamaized.net/hls/live/2002830/geoSTVATweb/master.m3u8"
            ),
            internalResolverKey = "servus-tv",
            alternateResolverKeys = listOf("servustv", "servus-tv-live", "servustv-live-stream"),
            tvgId = "ServusTV.at"
        ),
        LiveTvLightChannel(
            id = "laendle_tv",
            name = "Ländle TV",
            category = "Österreich",
            countryCode = "AT",
            logoUrl = "$LOGO_AT/laendle-tv-at.png",
            logoBadge = "LÄNDLE",
            brandColor = Color(0xFFEA580C),
            primaryStreamUrl = "https://s6.hopslan.com/laendletv/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://cdn.laendletv.com/live/laendletv/playlist.m3u8"
            ),
            internalResolverKey = "laendle-tv",
            alternateResolverKeys = listOf("landle-tv", "laendletv"),
            tvgId = "LaendleTV.at"
        ),
        LiveTvLightChannel(
            id = "fs1",
            name = "FS1",
            category = "Österreich",
            countryCode = "AT",
            logoUrl = "$LOGO_AT/fs1-at.png",
            logoBadge = "FS1",
            brandColor = Color(0xFF0D9488),
            primaryStreamUrl = "https://s6.hopslan.com/fs1/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://stream.fs1.tv/live/fs1.m3u8"
            ),
            internalResolverKey = "fs1",
            alternateResolverKeys = listOf("fs1-tv", "fs1-live"),
            tvgId = "FS1.at"
        ),
        LiveTvLightChannel(
            id = "m4tv",
            name = "M4TV",
            category = "Österreich",
            countryCode = "AT",
            logoUrl = "$LOGO_AT/m4tv-at.png",
            logoBadge = "M4TV",
            brandColor = Color(0xFF2563EB),
            primaryStreamUrl = "https://s6.hopslan.com/m4tv/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://stream.m4tv.at/live/m4tv/playlist.m3u8"
            ),
            internalResolverKey = "m4tv",
            alternateResolverKeys = listOf("m4-tv", "m4tv-live"),
            tvgId = "M4TV.at"
        ),
        LiveTvLightChannel(
            id = "w24_tv",
            name = "W24 TV",
            category = "Österreich",
            countryCode = "AT",
            logoUrl = "$LOGO_AT/w24-at.png",
            logoBadge = "W24",
            brandColor = Color(0xFFE11D48),
            primaryStreamUrl = "https://s6.hopslan.com/w24/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://ms01.w24.at/W24/smil:liveevent.smil/playlist.m3u8"
            ),
            internalResolverKey = "w24-tv",
            alternateResolverKeys = listOf("w24", "w24-live"),
            tvgId = "W24.at"
        ),
        LiveTvLightChannel(
            id = "tirol_tv",
            name = "Tirol TV",
            category = "Österreich",
            countryCode = "AT",
            logoUrl = "$LOGO_AT/tirol-tv-at.png",
            logoBadge = "TIROL",
            brandColor = Color(0xFFDC2626),
            primaryStreamUrl = "https://s6.hopslan.com/tiroltv/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://stream.tiroltv.at/live/tiroltv/playlist.m3u8"
            ),
            internalResolverKey = "tirol-tv",
            alternateResolverKeys = listOf("tiroltv", "tirol-tv-live"),
            tvgId = "TirolTV.at"
        ),
        LiveTvLightChannel(
            id = "okto_tv",
            name = "OKTO TV",
            category = "Österreich",
            countryCode = "AT",
            logoUrl = "$LOGO_AT/okto-tv-at.png",
            logoBadge = "OKTO",
            brandColor = Color(0xFF7C3AED),
            primaryStreamUrl = "https://s6.hopslan.com/okto/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://cdn3.wowza.com/1/M2h4b1l4L1p4/c1l5/hls/live/playlist.m3u8"
            ),
            internalResolverKey = "okto-tv",
            alternateResolverKeys = listOf("okto", "okto-live"),
            tvgId = "OktoTV.at"
        ),
        LiveTvLightChannel(
            id = "dorf_tv",
            name = "DORF TV",
            category = "Österreich",
            countryCode = "AT",
            logoUrl = "$LOGO_AT/dorf-tv-at.png",
            logoBadge = "DORF",
            brandColor = Color(0xFF059669),
            primaryStreamUrl = "https://s6.hopslan.com/dorftv/tracks-v1a1/mono.ts.m3u8",
            fallbackStreamUrls = listOf(
                "https://stream.dorftv.at/live/dorftv/playlist.m3u8"
            ),
            internalResolverKey = "dorf-tv",
            alternateResolverKeys = listOf("dorftv", "dorf-tv-live"),
            tvgId = "DorfTV.at"
        )
    )

    val seedAllChannels: List<LiveTvLightChannel> = seedGermanChannels + seedAustrianChannels

    private val seedChannelsById: Map<String, LiveTvLightChannel> = seedAllChannels.associateBy { it.id }

    private val _channelsFlow = MutableStateFlow<List<LiveTvLightChannel>>(seedAllChannels)
    val channelsFlow: StateFlow<List<LiveTvLightChannel>> = _channelsFlow.asStateFlow()

    @Volatile
    private var activeChannelsById: Map<String, LiveTvLightChannel> = seedChannelsById

    val allChannels: List<LiveTvLightChannel>
        get() = _channelsFlow.value

    val germanChannels: List<LiveTvLightChannel>
        get() = _channelsFlow.value.filter { it.countryCode == "DE" }

    val austrianChannels: List<LiveTvLightChannel>
        get() = _channelsFlow.value.filter { it.countryCode == "AT" }

    private val _catalogRevision = MutableStateFlow(0)
    val catalogRevision: StateFlow<Int> = _catalogRevision.asStateFlow()

    fun findById(id: String): LiveTvLightChannel? = activeChannelsById[id] ?: seedChannelsById[id]

    /**
     * Lädt beim ersten Öffnen in 0 ms den lokal gespeicherten 2ix2.com-Katalog
     * (inkl. echter pageUrls & Originallogos), falls bereits ein Sync stattgefunden hat.
     */
    fun initializeFromCache(context: Context): Boolean {
        if (!cacheInitialized.compareAndSet(false, true)) {
            return false
        }
        val snapshot = LiveTvLightCatalogStore.loadCachedChannels(context) ?: return false
        if (snapshot.channels.isEmpty()) return false
        publishChannels(snapshot.channels)
        return true
    }

    /**
     * Führt die frisch von 2ix2.com eingelesenen DE- & AT-Sender (inkl. echter pageUrl und
     * originalem 2ix2-Senderlogo) mit dem Offline-Seed zusammen, übernimmt alle neuen Sender
     * von 2ix2.com und speichert das Ergebnis persistent im lokalen Cache.
     */
    fun applyScrapedCatalog(
        scraped: List<Scraped2ix2Channel>,
        context: Context? = null
    ): CatalogMergeResult {
        if (scraped.isEmpty()) {
            return CatalogMergeResult(
                totalChannelsCount = _channelsFlow.value.size,
                matchedWithLive2ix2Count = 0,
                addedNewChannelsCount = 0,
                logosUpdatedCount = 0
            )
        }

        val scrapedByMatchKey = LinkedHashMap<String, Scraped2ix2Channel>()
        val usedScrapedSlugs = HashSet<String>()
        for (item in scraped) {
            val slugKey = normalizeForMatch(item.slug)
            val nameKey = normalizeForMatch(item.name)
            if (slugKey.length >= 2) scrapedByMatchKey.putIfAbsent(slugKey, item)
            if (nameKey.length >= 2) scrapedByMatchKey.putIfAbsent(nameKey, item)
        }

        var matchedCount = 0
        var logosUpdated = 0
        val merged = ArrayList<LiveTvLightChannel>(seedAllChannels.size + scraped.size)

        // 1. Alle bekannten Seed-Sender mit echter 2ix2.com pageUrl & Originallogo anreichern
        for (seed in seedAllChannels) {
            val lookupKeys = buildList {
                add(normalizeForMatch(seed.internalResolverKey))
                add(normalizeForMatch(seed.name))
                add(normalizeForMatch(seed.id))
                add(normalizeForMatch(seed.logoBadge))
                seed.alternateResolverKeys.forEach { add(normalizeForMatch(it)) }
            }.filter { it.length >= 2 }.distinct()

            val match = lookupKeys.firstNotNullOfOrNull { key -> scrapedByMatchKey[key] }
            if (match != null) {
                matchedCount++
                usedScrapedSlugs.add(match.slug)
                dynamicSlugOverrides[seed.id] = match.slug

                val hasLiveLogo = match.logoUrl.isNotBlank()
                if (hasLiveLogo && match.logoUrl != seed.logoUrl) {
                    logosUpdated++
                }
                val primaryLogo = if (hasLiveLogo) match.logoUrl else seed.logoUrl
                val secondaryLogo = if (hasLiveLogo && match.logoUrl != seed.logoUrl) {
                    seed.logoUrl
                } else {
                    seed.fallbackLogoUrl
                }

                merged.add(
                    seed.copy(
                        logoUrl = primaryLogo,
                        fallbackLogoUrl = secondaryLogo,
                        pageUrl = match.pageUrl,
                        internalResolverKey = match.slug,
                        alternateResolverKeys = (listOf(match.slug, seed.internalResolverKey) + seed.alternateResolverKeys)
                            .filter { it.isNotBlank() }
                            .distinct()
                    )
                )
            } else {
                merged.add(
                    seed.copy(
                        pageUrl = seed.effectivePageUrl
                    )
                )
            }
        }

        // 2. Zusätzliche auf 2ix2.com gefundene Sender automatisch mit aufnehmen
        var addedNewCount = 0
        val existingIds = merged.map { it.id }.toHashSet()
        for (item in scraped) {
            if (item.slug in usedScrapedSlugs) continue
            val newId = "2ix2_${item.slug.replace('-', '_')}"
            if (!existingIds.add(newId)) continue
            addedNewCount++
            if (item.logoUrl.isNotBlank()) logosUpdated++

            merged.add(
                LiveTvLightChannel(
                    id = newId,
                    name = item.name,
                    category = LiveTvLight2ix2Scraper.classifyCategory(item.name, item.countryCode),
                    countryCode = item.countryCode,
                    logoUrl = item.logoUrl,
                    fallbackLogoUrl = "",
                    logoBadge = LiveTvLight2ix2Scraper.generateLogoBadge(item.name),
                    brandColor = LiveTvLight2ix2Scraper.pickBrandColor(item.slug),
                    primaryStreamUrl = "",
                    fallbackStreamUrls = emptyList(),
                    internalResolverKey = item.slug,
                    alternateResolverKeys = listOf(item.slug),
                    tvgId = "",
                    supportsInstantStart = false,
                    pageUrl = item.pageUrl
                )
            )
        }

        publishChannels(merged)
        if (context != null) {
            LiveTvLightCatalogStore.saveCachedChannels(context, merged)
        }

        return CatalogMergeResult(
            totalChannelsCount = merged.size,
            matchedWithLive2ix2Count = matchedCount,
            addedNewChannelsCount = addedNewCount,
            logosUpdatedCount = logosUpdated
        )
    }

    private fun publishChannels(channels: List<LiveTvLightChannel>) {
        activeChannelsById = channels.associateBy { it.id }
        _channelsFlow.value = channels
        _catalogRevision.value = _catalogRevision.value + 1
    }

    internal fun resetToSeedForTesting() {
        dynamicSlugOverrides.clear()
        cacheInitialized.set(false)
        publishChannels(seedAllChannels)
    }

    fun effectiveResolverKey(channel: LiveTvLightChannel): String {
        return dynamicSlugOverrides[channel.id]?.takeIf { it.isNotBlank() } ?: channel.internalResolverKey
    }

    fun effectiveResolverSlugs(channel: LiveTvLightChannel): List<String> {
        return channel.allResolverSlugs(dynamicSlugOverrides[channel.id])
    }

    fun effectivePageUrls(channel: LiveTvLightChannel): List<String> {
        val urls = LinkedHashSet<String>()
        if (channel.pageUrl.isNotBlank()) {
            urls.add(channel.pageUrl)
        }
        effectiveResolverSlugs(channel).forEach { slug ->
            urls.add("${LiveTvLight2ix2Scraper.BASE_URL}/${slug.trim('/')}/")
        }
        return urls.toList()
    }

    /**
     * Aktualisiert erkannte Resolver-Slugs aus einem Hintergrund-Katalogabgleich,
     * ohne den Offline-Bestand jemals zu verkleinern.
     */
    fun applySlugOverrides(discoveredSlugs: Map<String, String>): Int {
        if (discoveredSlugs.isEmpty()) return 0
        var updatedCount = 0
        for (channel in allChannels) {
            val lookupKeys = buildList {
                add(normalizeForMatch(channel.internalResolverKey))
                add(normalizeForMatch(channel.name))
                add(normalizeForMatch(channel.id))
                add(normalizeForMatch(channel.logoBadge))
                channel.alternateResolverKeys.forEach { add(normalizeForMatch(it)) }
            }.filter { it.length >= 2 }.distinct()

            val matchedSlug = lookupKeys.firstNotNullOfOrNull { key -> discoveredSlugs[key] }

            if (!matchedSlug.isNullOrBlank() && matchedSlug != effectiveResolverKey(channel)) {
                dynamicSlugOverrides[channel.id] = matchedSlug
                updatedCount++
            }
        }
        if (updatedCount > 0) {
            _catalogRevision.value = _catalogRevision.value + 1
        }
        return updatedCount
    }

    fun asChannelItems(): List<ChannelItem> {
        return allChannels.map { ch ->
            val cachedUrl = LiveTvLightStreamResolver.getCachedStreamUrl(ch.id) ?: ch.primaryStreamUrl
            ch.toChannelItem(cachedUrl)
        }
    }

    internal fun normalizeForMatch(raw: String): String {
        return raw.lowercase()
            .replace("ä", "ae")
            .replace("ö", "oe")
            .replace("ü", "ue")
            .replace("ß", "ss")
            .replace(" hd", "")
            .replace(" live", "")
            .replace(" stream", "")
            .replace(" fernsehen", "")
            .replace(" deutschland", "")
            .replace(" deutsch", "")
            .replace(Regex("[^a-z0-9]"), "")
    }
}
