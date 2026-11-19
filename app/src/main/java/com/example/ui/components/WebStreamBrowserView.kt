package com.example.ui.components

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChromeReaderMode
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.parser.KoolToCatalogHelper
import com.example.data.storage.WebTvSiteEditDialog
import com.example.data.storage.WebTvSiteItem
import com.example.data.storage.WebTvSitesManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentRose
import com.example.ui.theme.BorderGlow
import com.example.ui.theme.DarkNavyBackground
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

private const val WEB_TV_PIN_CODE = "1971"

data class WebPortalPreset(
    val name: String,
    val url: String,
    val category: String,
    val description: String,
    val badge: String,
    val badgeColor: Color,
    val iconEmoji: String
)

data class DirectWebChannel(
    val id: String,
    val name: String,
    val category: String,
    val streamUrl: String,
    val webUrl: String,
    val logoTag: String,
    val resolution: String = "1080p HD",
    val is2ix2: Boolean = true
)

enum class WebTvMode {
    DIRECT_CHANNELS,   // 100% crash-safe direct stream player (Nexus HD Player)
    CUSTOM_TAB_PORTAL, // Chrome Custom Tab High-Speed Browser (Android 16 / S25 Ultra Optimized)
    SAFE_EMBEDDED      // Sandboxed In-App WebView with Full Crash Guards
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebStreamBrowserView(
    onPlayDetectedStream: (streamUrl: String, title: String) -> Unit,
    onWebFullscreenChanged: (Boolean) -> Unit = {},
    isEnglish: Boolean = false
) {
    // Strikter PIN-Schutz für die Rubrik Web TV (PIN: 1971, 12-Stellen-Vorlage):
    // Solange nicht entsperrt, wird keinerlei Web-TV-Inhalt gerendert oder preisgegeben.
    var isWebTvUnlocked by remember { mutableStateOf(false) }

    if (!isWebTvUnlocked) {
        LaunchedEffect(Unit) {
            onWebFullscreenChanged(false)
        }
        WebTvPinLockScreen(
            expectedPin = WEB_TV_PIN_CODE,
            onPinSuccess = { isWebTvUnlocked = true },
            isEnglish = isEnglish
        )
        return
    }

    val context = LocalContext.current
    val hostView = LocalView.current

    LaunchedEffect(context) {
        WebTvSitesManager.ensureInitialized(context)
    }
    val webTvSites by WebTvSitesManager.sitesFlow.collectAsStateWithLifecycle()

    var selectedMode by remember { mutableStateOf(WebTvMode.CUSTOM_TAB_PORTAL) }
    var isEmbeddedFullscreen by remember { mutableStateOf(true) }
    var showAddSiteDialog by remember { mutableStateOf(false) }
    var siteToEdit by remember { mutableStateOf<WebTvSiteItem?>(null) }
    var inAppTargetUrl by remember { mutableStateOf("http://kool.to/") }

    val isFullWebViewActive = selectedMode == WebTvMode.SAFE_EMBEDDED && isEmbeddedFullscreen

    LaunchedEffect(isFullWebViewActive) {
        onWebFullscreenChanged(isFullWebViewActive)
    }

    DisposableEffect(isFullWebViewActive) {
        val window = (context as? Activity)?.window
        val controller = if (window != null) WindowCompat.getInsetsController(window, hostView) else null
        if (isFullWebViewActive) {
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onWebFullscreenChanged(false)
        }
    }

    // Direct, crystal clear 2ix2 & DACH Web TV Channels (native Nexus Player streams)
    val directChannels = remember {
        listOf(
            DirectWebChannel(
                id = "ard",
                name = "Das Erste (ARD)",
                category = "Öffentlich-Rechtlich",
                streamUrl = "https://mcdn.daserste.de/daserste/de/master.m3u8",
                webUrl = "https://www.2ix2.com/das-erste-live-stream/",
                logoTag = "ARD"
            ),
            DirectWebChannel(
                id = "zdf",
                name = "ZDF HD",
                category = "Öffentlich-Rechtlich",
                streamUrl = "https://zdf-hls-15.akamaized.net/hls/live/2016498/de/veryhigh/master.m3u8",
                webUrl = "https://www.2ix2.com/zdf-live-stream/",
                logoTag = "ZDF"
            ),
            DirectWebChannel(
                id = "arte",
                name = "ARTE Deutsch HD",
                category = "Kultur & Doku",
                streamUrl = "https://artesimulcast.akamaized.net/hls/live/2031003/artelive_de/master.m3u8",
                webUrl = "https://www.2ix2.com/arte-live-stream/",
                logoTag = "ARTE"
            ),
            DirectWebChannel(
                id = "3sat",
                name = "3sat HD",
                category = "Kultur & Doku",
                streamUrl = "https://zdf-hls-18.akamaized.net/hls/live/2016501/de/veryhigh/master.m3u8",
                webUrl = "https://www.2ix2.com/3sat-live-stream/",
                logoTag = "3SAT"
            ),
            DirectWebChannel(
                id = "kika",
                name = "KiKA Kinderkanal HD",
                category = "Kinder",
                streamUrl = "https://kika-hls.akamaized.net/hls/live/2022693/live/master.m3u8",
                webUrl = "https://www.2ix2.com/kika-live-stream/",
                logoTag = "KIKA"
            ),
            DirectWebChannel(
                id = "phoenix",
                name = "PHOENIX HD",
                category = "Doku & Politik",
                streamUrl = "https://zdf-hls-19.akamaized.net/hls/live/2016502/de/veryhigh/master.m3u8",
                webUrl = "https://www.2ix2.com/phoenix-live-stream/",
                logoTag = "PHOENIX"
            ),
            DirectWebChannel(
                id = "tagesschau24",
                name = "tagesschau24 HD",
                category = "Nachrichten",
                streamUrl = "https://tagesschau.akamaized.net/hls/live/2020115/tagesschau/tagesschau_1/master.m3u8",
                webUrl = "https://www.2ix2.com/tagesschau24-live-stream/",
                logoTag = "TS24"
            ),
            DirectWebChannel(
                id = "welt",
                name = "WELT Nachrichtensender",
                category = "Nachrichten",
                streamUrl = "https://live2.welt.de/live2/hls/master.m3u8",
                webUrl = "https://www.2ix2.com/welt-live-stream/",
                logoTag = "WELT"
            ),
            DirectWebChannel(
                id = "zdfinfo",
                name = "ZDFinfo HD",
                category = "Doku & Wissen",
                streamUrl = "https://zdf-hls-17.akamaized.net/hls/live/2016500/de/veryhigh/master.m3u8",
                webUrl = "https://www.2ix2.com/zdfinfo-live-stream/",
                logoTag = "INFO"
            ),
            DirectWebChannel(
                id = "zdfneo",
                name = "ZDFneo HD",
                category = "Unterhaltung",
                streamUrl = "https://zdf-hls-16.akamaized.net/hls/live/2016499/de/veryhigh/master.m3u8",
                webUrl = "https://www.2ix2.com/zdfneo-live-stream/",
                logoTag = "NEO"
            ),
            DirectWebChannel(
                id = "wdr",
                name = "WDR Köln HD",
                category = "Regional (Drittes)",
                streamUrl = "https://wdrfsgeo.akamaized.net/hls/live/2015750/wdrfsgeo/master.m3u8",
                webUrl = "https://www.2ix2.com/wdr-live-stream/",
                logoTag = "WDR"
            ),
            DirectWebChannel(
                id = "ndr",
                name = "NDR Fernsehen HD",
                category = "Regional (Drittes)",
                streamUrl = "https://ndrfs-lh.akamaihd.net/i/ndrfs_nds@430233/master.m3u8",
                webUrl = "https://www.2ix2.com/ndr-live-stream/",
                logoTag = "NDR"
            ),
            DirectWebChannel(
                id = "swr",
                name = "SWR Fernsehen BW HD",
                category = "Regional (Drittes)",
                streamUrl = "https://swrbw-hls.akamaized.net/hls/live/2018674/swrbw/master.m3u8",
                webUrl = "https://www.2ix2.com/swr-live-stream/",
                logoTag = "SWR"
            ),
            DirectWebChannel(
                id = "br",
                name = "BR Fernsehen Süd HD",
                category = "Regional (Drittes)",
                streamUrl = "https://brfs-lh.akamaihd.net/i/brfs_sued@119890/master.m3u8",
                webUrl = "https://www.2ix2.com/br-live-stream/",
                logoTag = "BR"
            ),
            DirectWebChannel(
                id = "servustv",
                name = "ServusTV On",
                category = "Sport & Doku",
                streamUrl = "https://rbmn-live.akamaized.net/hls/live/2002825/geoSTVDEweb/master.m3u8",
                webUrl = "https://www.2ix2.com/servustv-live-stream/",
                logoTag = "SERVUS"
            ),
            DirectWebChannel(
                id = "rtl",
                name = "RTL Television",
                category = "Privatsender",
                streamUrl = "https://www.2ix2.com/rtl-live-stream/",
                webUrl = "https://www.2ix2.com/rtl-live-stream/",
                logoTag = "RTL"
            ),
            DirectWebChannel(
                id = "pro7",
                name = "ProSieben HD",
                category = "Privatsender",
                streamUrl = "https://www.2ix2.com/prosieben-live-stream/",
                webUrl = "https://www.2ix2.com/prosieben-live-stream/",
                logoTag = "PRO7"
            ),
            DirectWebChannel(
                id = "sat1",
                name = "SAT.1",
                category = "Privatsender",
                streamUrl = "https://www.2ix2.com/sat1-live-stream/",
                webUrl = "https://www.2ix2.com/sat1-live-stream/",
                logoTag = "SAT1"
            ),
            DirectWebChannel(
                id = "vox",
                name = "VOX HD",
                category = "Privatsender",
                streamUrl = "https://www.2ix2.com/vox-live-stream/",
                webUrl = "https://www.2ix2.com/vox-live-stream/",
                logoTag = "VOX"
            ),
            DirectWebChannel(
                id = "kabel1",
                name = "Kabel Eins",
                category = "Privatsender",
                streamUrl = "https://www.2ix2.com/kabel-eins-live-stream/",
                webUrl = "https://www.2ix2.com/kabel-eins-live-stream/",
                logoTag = "K1"
            ),
            DirectWebChannel(
                id = "rtl2",
                name = "RTL II",
                category = "Privatsender",
                streamUrl = "https://www.2ix2.com/rtl2-live-stream/",
                webUrl = "https://www.2ix2.com/rtl2-live-stream/",
                logoTag = "RTL2"
            ),
            DirectWebChannel(
                id = "super_rtl",
                name = "Super RTL",
                category = "Unterhaltung",
                streamUrl = "https://www.2ix2.com/super-rtl-live-stream/",
                webUrl = "https://www.2ix2.com/super-rtl-live-stream/",
                logoTag = "SRTL"
            ),
            DirectWebChannel(
                id = "ntv",
                name = "n-tv Nachrichten",
                category = "Nachrichten",
                streamUrl = "https://www.2ix2.com/ntv-live-stream/",
                webUrl = "https://www.2ix2.com/ntv-live-stream/",
                logoTag = "NTV"
            ),
            DirectWebChannel(
                id = "nitro",
                name = "NITRO",
                category = "Unterhaltung",
                streamUrl = "https://www.2ix2.com/nitro-live-stream/",
                webUrl = "https://www.2ix2.com/nitro-live-stream/",
                logoTag = "NITRO"
            ),
            DirectWebChannel(
                id = "dmax",
                name = "DMAX Deutschland",
                category = "Unterhaltung",
                streamUrl = "https://www.2ix2.com/dmax-live-stream/",
                webUrl = "https://www.2ix2.com/dmax-live-stream/",
                logoTag = "DMAX"
            ),
            DirectWebChannel(
                id = "sport1",
                name = "SPORT1 HD",
                category = "Sport",
                streamUrl = "https://www.2ix2.com/sport1-live-stream/",
                webUrl = "https://www.2ix2.com/sport1-live-stream/",
                logoTag = "SPORT1"
            ),
            DirectWebChannel(
                id = "eurosport",
                name = "Eurosport 1",
                category = "Sport",
                streamUrl = "https://www.2ix2.com/eurosport-live-stream/",
                webUrl = "https://www.2ix2.com/eurosport-live-stream/",
                logoTag = "EURO1"
            ),
            DirectWebChannel(
                id = "orf1",
                name = "ORF 1 HD",
                category = "Österreich (ORF)",
                streamUrl = "https://www.2ix2.com/orf1-live-stream/",
                webUrl = "https://www.2ix2.com/orf1-live-stream/",
                logoTag = "ORF1"
            ),
            DirectWebChannel(
                id = "orf2",
                name = "ORF 2 HD",
                category = "Österreich (ORF)",
                streamUrl = "https://www.2ix2.com/orf2-live-stream/",
                webUrl = "https://www.2ix2.com/orf2-live-stream/",
                logoTag = "ORF2"
            ),
            DirectWebChannel(
                id = "srf1",
                name = "SRF 1 HD",
                category = "Schweiz (SRF)",
                streamUrl = "https://www.2ix2.com/srf1-live-stream/",
                webUrl = "https://www.2ix2.com/srf1-live-stream/",
                logoTag = "SRF1"
            ),
            DirectWebChannel(
                id = "srf2",
                name = "SRF zwei HD",
                category = "Schweiz (SRF)",
                streamUrl = "https://www.2ix2.com/srf2-live-stream/",
                webUrl = "https://www.2ix2.com/srf2-live-stream/",
                logoTag = "SRF2"
            ),
            DirectWebChannel(
                id = "nydus_live",
                name = "Nydus Live Stream",
                category = "Live TV & Sport",
                streamUrl = "http://nydus.org/stream/live",
                webUrl = "http://nydus.org/stream/live",
                logoTag = "NYDUS",
                resolution = "Live HD",
                is2ix2 = false
            ),
            DirectWebChannel(
                id = "movie2k_portal",
                name = "Movie2k.cx",
                category = "Filme & Serien",
                streamUrl = "https://movie2k.cx/",
                webUrl = "https://movie2k.cx/",
                logoTag = "M2K",
                resolution = "HD / VoD",
                is2ix2 = false
            ),
            DirectWebChannel(
                id = "kool_to_portal",
                name = "Kool.to (Live TV & Kino)",
                category = "Deutsche TV Sender",
                streamUrl = "http://kool.to/",
                webUrl = "http://kool.to/",
                logoTag = "KOOL",
                resolution = "Live HD / VoD",
                is2ix2 = false
            ),
            DirectWebChannel(
                id = "kool_ws_portal",
                name = "Kool.ws",
                category = "Filme & Serien",
                streamUrl = "http://kool.ws/",
                webUrl = "http://kool.ws/",
                logoTag = "KOOL",
                resolution = "HD / VoD",
                is2ix2 = false
            ),
            DirectWebChannel(
                id = "tele24seven_de",
                name = "Tele24Seven (DE Channels)",
                category = "Deutsche TV Sender",
                streamUrl = "https://tele24seven.com/?country=DE#country-channels",
                webUrl = "https://tele24seven.com/?country=DE#country-channels",
                logoTag = "T24",
                resolution = "Live HD",
                is2ix2 = false
            ),
            DirectWebChannel(
                id = "megakino_portal",
                name = "Megakino",
                category = "Filme & Serien",
                streamUrl = "http://megakino18.com/",
                webUrl = "http://megakino18.com/",
                logoTag = "MEGA",
                resolution = "HD / VoD",
                is2ix2 = false
            )
        )
    }

    // Function to launch high-performance Chrome Custom Tab (enforces http:// for cleartext stream sites)
    val launchCustomTab: (String) -> Unit = { rawUrl ->
        val url = WebTvSitesManager.normalizeWebUrl(rawUrl)
        try {
            val darkParams = CustomTabColorSchemeParams.Builder()
                .setToolbarColor(ObsidianBlack.toArgb())
                .setSecondaryToolbarColor(DarkNavyCard.toArgb())
                .setNavigationBarColor(ObsidianBlack.toArgb())
                .build()

            val customTabsIntent = CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(darkParams)
                .setShowTitle(true)
                .setUrlBarHidingEnabled(true)
                .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
                .build()

            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            customTabsIntent.launchUrl(context, Uri.parse(url))
        } catch (e: Exception) {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(browserIntent)
            } catch (err: Exception) {
                Toast.makeText(context, "Browser konnte nicht gestartet werden", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Startet die Web-Ansicht direkt im Vollbildmodus (löst HTTP/HTTPS Mixed-Content für kool.to & Co.)
    val openInAppFullscreen: (String) -> Unit = { rawUrl ->
        val normalized = WebTvSitesManager.normalizeWebUrl(rawUrl)
        inAppTargetUrl = normalized
        isEmbeddedFullscreen = true
        selectedMode = WebTvMode.SAFE_EMBEDDED
    }

    val openSmartWebPortal: (String) -> Unit = { rawUrl ->
        val normalized = WebTvSitesManager.normalizeWebUrl(rawUrl)
        if (WebTvSitesManager.requiresCleartextHttp(normalized)) {
            openInAppFullscreen(normalized)
        } else {
            openInAppFullscreen(normalized)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavyBackground)
            .testTag("web_stream_browser_screen")
    ) {
        if (!isFullWebViewActive) {
            // Security Header Bar with instant Re-Lock action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkNavyCard)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = AccentEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isEnglish) "Web TV Unlocked (PIN Protected)" else "Web TV freigeschaltet (PIN-geschützt)",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            selectedMode = WebTvMode.CUSTOM_TAB_PORTAL
                            showAddSiteDialog = true
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentEmerald),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.65f)),
                        modifier = Modifier
                            .height(30.dp)
                            .testTag("web_tv_header_add_site_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = AccentEmerald,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isEnglish) "Add Website" else "Webseite +",
                            color = AccentEmerald,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = { isWebTvUnlocked = false },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(30.dp)
                            .testTag("web_tv_relock_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = if (isEnglish) "Lock Web TV" else "Web TV sperren",
                            tint = GoldPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isEnglish) "Lock" else "Sperren",
                            color = GoldPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Mode Selector Tab Row
            TabRow(
                selectedTabIndex = selectedMode.ordinal,
                containerColor = DarkNavyCard,
                contentColor = GoldPrimary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedMode.ordinal]),
                        color = GoldPrimary,
                        height = 3.dp
                    )
                }
            ) {
                Tab(
                    selected = selectedMode == WebTvMode.DIRECT_CHANNELS,
                    onClick = { selectedMode = WebTvMode.DIRECT_CHANNELS },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LiveTv, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isEnglish) "Live TV Channels" else "2ix2 & Web TV",
                                fontWeight = if (selectedMode == WebTvMode.DIRECT_CHANNELS) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        }
                    }
                )

                Tab(
                    selected = selectedMode == WebTvMode.CUSTOM_TAB_PORTAL,
                    onClick = { selectedMode = WebTvMode.CUSTOM_TAB_PORTAL },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isEnglish) "Websites (${webTvSites.size})" else "Webseiten (${webTvSites.size})",
                                fontWeight = if (selectedMode == WebTvMode.CUSTOM_TAB_PORTAL) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        }
                    }
                )

                Tab(
                    selected = selectedMode == WebTvMode.SAFE_EMBEDDED,
                    onClick = {
                        isEmbeddedFullscreen = true
                        selectedMode = WebTvMode.SAFE_EMBEDDED
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ChromeReaderMode, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isEnglish) "In-App Browser" else "In-App Ansicht",
                                fontWeight = if (selectedMode == WebTvMode.SAFE_EMBEDDED) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        }
                    }
                )
            }
        }

        when (selectedMode) {
            WebTvMode.DIRECT_CHANNELS -> {
                DirectChannelsScreen(
                    channels = directChannels,
                    sites = webTvSites,
                    onPlayChannel = { ch ->
                        if (ch.streamUrl.contains(".m3u8", ignoreCase = true)) {
                            onPlayDetectedStream(ch.streamUrl, ch.name)
                        } else {
                            openInAppFullscreen(ch.webUrl)
                        }
                    },
                    onOpenInBrowser = { ch ->
                        openInAppFullscreen(ch.webUrl)
                    },
                    onOpenSite = { site ->
                        openInAppFullscreen(site.url)
                    },
                    onAddSiteClick = {
                        showAddSiteDialog = true
                    },
                    onEditSite = { site ->
                        siteToEdit = site
                    },
                    onDeleteSite = { site ->
                        WebTvSitesManager.deleteSite(context, site.id)
                    },
                    isEnglish = isEnglish
                )
            }

            WebTvMode.CUSTOM_TAB_PORTAL -> {
                // Web TV Websites Hub with full manual Add, Edit, and Delete support
                WebPortalsHubScreen(
                    sites = webTvSites,
                    onOpenPortal = { site ->
                        openInAppFullscreen(site.url)
                    },
                    onOpenInApp = { site ->
                        openInAppFullscreen(site.url)
                    },
                    onAddSiteClick = {
                        showAddSiteDialog = true
                    },
                    onEditSite = { site ->
                        siteToEdit = site
                    },
                    onDeleteSite = { site ->
                        WebTvSitesManager.deleteSite(context, site.id)
                    },
                    onSaveCustomSite = { rawName, rawUrl ->
                        val added = WebTvSitesManager.addSite(
                            context = context,
                            name = rawName,
                            url = rawUrl
                        )
                        if (added != null) {
                            Toast.makeText(context, "Webseite '${added.name}' hinzugefügt!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onCustomUrlLaunch = { url ->
                        openInAppFullscreen(url)
                    },
                    isEnglish = isEnglish
                )
            }

            WebTvMode.SAFE_EMBEDDED -> {
                // Sandboxed In-App Browser starting directly in Fullscreen Mode
                SafeEmbeddedBrowserScreen(
                    sites = webTvSites,
                    initialUrl = inAppTargetUrl,
                    isFullscreen = isEmbeddedFullscreen,
                    onToggleFullscreen = { isEmbeddedFullscreen = !isEmbeddedFullscreen },
                    onExitFullscreenWebView = {
                        isEmbeddedFullscreen = false
                        selectedMode = WebTvMode.CUSTOM_TAB_PORTAL
                    },
                    onPlayDetectedStream = onPlayDetectedStream,
                    onLaunchExternal = launchCustomTab,
                    onAddCurrentPageToSites = { title, url ->
                        val added = WebTvSitesManager.addSite(
                            context = context,
                            name = title,
                            url = url
                        )
                        if (added != null) {
                            Toast.makeText(context, "Webseite '${added.name}' unter Web TV gespeichert!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    isEnglish = isEnglish
                )
            }
        }
    }

    if (showAddSiteDialog) {
        WebTvSiteEditDialog(
            initialSite = null,
            isEnglish = isEnglish,
            onDismiss = { showAddSiteDialog = false },
            onSave = { name, url, category, description, emoji ->
                WebTvSitesManager.addSite(
                    context = context,
                    name = name,
                    url = url,
                    category = category,
                    description = description,
                    badge = category,
                    iconEmoji = emoji
                )
                showAddSiteDialog = false
            }
        )
    }

    siteToEdit?.let { target ->
        WebTvSiteEditDialog(
            initialSite = target,
            isEnglish = isEnglish,
            onDismiss = { siteToEdit = null },
            onSave = { name, url, category, description, emoji ->
                WebTvSitesManager.updateSite(
                    context = context,
                    updatedSite = target.copy(
                        name = name,
                        url = url,
                        category = category,
                        description = description,
                        badge = category,
                        iconEmoji = emoji
                    )
                )
                siteToEdit = null
            }
        )
    }
}

/**
 * Vollflächige, blickdichte PIN-Sperre für die Rubrik Web TV (PIN: 1971).
 * Kein Zugang und keine Einsicht ohne korrekten PIN.
 * Die PIN-Vorlage zeigt stets 12 Stellen an (PinTwelveSlotTemplate).
 */
@Composable
private fun WebTvPinLockScreen(
    expectedPin: String = WEB_TV_PIN_CODE,
    onPinSuccess: () -> Unit,
    isEnglish: Boolean = false
) {
    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    fun verifyPin(candidate: String) {
        if (candidate == expectedPin) {
            onPinSuccess()
        } else {
            isError = true
            enteredPin = ""
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavyBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp)
            .testTag("web_tv_pin_lock_screen"),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(22.dp))
                .border(1.5.dp, GoldPrimary, RoundedCornerShape(22.dp)),
            colors = CardDefaults.cardColors(containerColor = DarkNavyCard)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(GoldPrimary.copy(alpha = 0.15f))
                        .border(1.dp, GoldPrimary.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = if (isEnglish) "Web TV Locked" else "Web TV gesperrt",
                        tint = GoldPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (isEnglish) "Web TV — PIN Protected" else "Rubrik Web TV — PIN-geschützt",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = GoldPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isEnglish)
                        "No access and no view without valid PIN authorization."
                    else
                        "Kein Zugang und keine Einsicht ohne gültige PIN-Autorisierung.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 12-Stellen PIN-Vorlage (zeigt exakt 12 visuelle Stellen an)
                PinTwelveSlotTemplate(
                    enteredLength = enteredPin.length,
                    isError = isError
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = enteredPin,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }
                        if (digits.length <= 12) {
                            enteredPin = digits
                            isError = false
                            if (digits == expectedPin) {
                                verifyPin(digits)
                            }
                        }
                    },
                    label = { Text(if (isEnglish) "PIN Template (12 Digits)" else "PIN-Vorlage (12 Stellen)") },
                    placeholder = { Text("•••• •••• ••••") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { verifyPin(enteredPin) }),
                    singleLine = true,
                    isError = isError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("web_tv_pin_input_field"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = if (isError) AccentRose else BorderGlow.copy(alpha = 0.5f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                if (isError) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isEnglish)
                            "Incorrect PIN. Access denied."
                        else
                            "Falscher PIN! Kein Zugang zu Web TV.",
                        color = AccentRose,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // TV Remote & Touch Numpad (1-9, C, 0, OK)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val rows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("C", "0", "OK")
                    )

                    rows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { key ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            when (key) {
                                                "OK" -> GoldPrimary
                                                "C" -> DarkSurfaceElevated
                                                else -> DarkSurface
                                            }
                                        )
                                        .border(
                                            1.dp,
                                            if (key == "OK") GoldLight else BorderGlow.copy(alpha = 0.3f),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            when (key) {
                                                "C" -> {
                                                    if (enteredPin.isNotEmpty()) {
                                                        enteredPin = enteredPin.dropLast(1)
                                                    }
                                                    isError = false
                                                }
                                                "OK" -> verifyPin(enteredPin)
                                                else -> {
                                                    if (enteredPin.length < 12) {
                                                        val updated = enteredPin + key
                                                        enteredPin = updated
                                                        isError = false
                                                        if (updated == expectedPin) {
                                                            verifyPin(updated)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        .testTag("web_tv_pin_key_$key"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (key == "C") {
                                        Icon(
                                            imageVector = Icons.Default.Backspace,
                                            contentDescription = "Backspace",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    } else {
                                        Text(
                                            text = key,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (key == "OK") Color.Black else TextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = { verifyPin(enteredPin) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("web_tv_pin_unlock_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEnglish) "Unlock Web TV" else "Web TV freischalten",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

/**
 * 1. Direct Channels Screen: Lists curated 2ix2 and German Web TV Channels
 */
@Composable
private fun DirectChannelsScreen(
    channels: List<DirectWebChannel>,
    sites: List<WebTvSiteItem> = emptyList(),
    onPlayChannel: (DirectWebChannel) -> Unit,
    onOpenInBrowser: (DirectWebChannel) -> Unit,
    onOpenSite: (WebTvSiteItem) -> Unit = {},
    onAddSiteClick: () -> Unit = {},
    onEditSite: (WebTvSiteItem) -> Unit = {},
    onDeleteSite: (WebTvSiteItem) -> Unit = {},
    isEnglish: Boolean
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    val categories = remember(channels) {
        listOf("Alle") + channels.map { it.category }.distinct()
    }

    val filteredChannels = remember(channels, searchQuery, selectedCategory) {
        channels.filter { ch ->
            val matchesCategory = selectedCategory == null || selectedCategory == "Alle" || ch.category == selectedCategory
            val matchesSearch = searchQuery.isBlank() || ch.name.contains(searchQuery, ignoreCase = true) || ch.category.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Info Header Banner + Quick Website Management Bar
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .border(1.dp, BorderGlow.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = DarkNavyCard)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(AccentEmerald.copy(alpha = 0.15f))
                                .border(1.dp, AccentEmerald.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isEnglish) "Web TV Websites & Channels" else "Web TV Webseiten & Sender",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = GoldPrimary
                            )
                            Text(
                                text = if (isEnglish) "Add, edit, delete & launch Web TV websites" else "Webseiten manuell hinzufügen, bearbeiten & löschen (${sites.size} Webseiten)",
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Button(
                        onClick = onAddSiteClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentEmerald,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isEnglish) "Add Website" else "Webseite hinzufügen",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp
                        )
                    }
                }

                if (sites.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sites.forEach { site ->
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkSurfaceElevated)
                                    .border(1.dp, GoldPrimary.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                    .clickable { onOpenSite(site) }
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(text = site.iconEmoji, fontSize = 13.sp)
                                Text(
                                    text = site.name,
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Bearbeiten",
                                    tint = GoldLight,
                                    modifier = Modifier
                                        .size(15.dp)
                                        .clickable { onEditSite(site) }
                                )
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Löschen",
                                    tint = AccentRose,
                                    modifier = Modifier
                                        .size(15.dp)
                                        .clickable { onDeleteSite(site) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(if (isEnglish) "Search channel (e.g. RTL, Pro7, ARD)..." else "Sender suchen (z.B. RTL, Pro7, ARD)...", fontSize = 12.sp, color = TextMuted) },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = TextPrimary),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoldPrimary,
                unfocusedBorderColor = BorderGlow.copy(alpha = 0.5f),
                focusedContainerColor = DarkSurface,
                unfocusedContainerColor = DarkSurface
            ),
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categories.forEach { cat ->
                val isSelected = (selectedCategory == null && cat == "Alle") || selectedCategory == cat
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) GoldPrimary else DarkSurfaceElevated)
                        .border(1.dp, if (isSelected) GoldLight else BorderGlow.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .clickable {
                            selectedCategory = if (cat == "Alle") null else cat
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = cat,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) ObsidianBlack else TextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Grid of Channels
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(filteredChannels, key = { it.id }) { ch ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, BorderGlow.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .clickable { onPlayChannel(ch) },
                    colors = CardDefaults.cardColors(containerColor = DarkNavyCard)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkSurfaceElevated)
                                    .border(1.dp, GoldPrimary.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = ch.logoTag,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = GoldPrimary
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(AccentCyan.copy(alpha = 0.15f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = ch.resolution,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentCyan
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = ch.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = ch.category,
                            fontSize = 10.sp,
                            color = TextSecondary,
                            maxLines = 1
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Button(
                                onClick = { onPlayChannel(ch) },
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f).height(32.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = ObsidianBlack, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(text = "Play", color = ObsidianBlack, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }

                            IconButton(
                                onClick = { onOpenInBrowser(ch) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "2ix2 Web", tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 2. Web Portals Hub Screen: Manage and launch Web TV Websites (Add, Edit, Delete, Open)
 */
@Composable
private fun WebPortalsHubScreen(
    sites: List<WebTvSiteItem>,
    onOpenPortal: (WebTvSiteItem) -> Unit,
    onOpenInApp: (WebTvSiteItem) -> Unit,
    onAddSiteClick: () -> Unit,
    onEditSite: (WebTvSiteItem) -> Unit,
    onDeleteSite: (WebTvSiteItem) -> Unit,
    onSaveCustomSite: (String, String) -> Unit,
    onCustomUrlLaunch: (String) -> Unit,
    isEnglish: Boolean
) {
    var quickNameInput by remember { mutableStateOf("") }
    var customUrlInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // Header with Add Website Button + Direct Inline Add Form
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GoldPrimary.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkNavyCard)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "🌐", fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (isEnglish) "Web TV Websites & Portals" else "Web TV Webseiten verwalten",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GoldPrimary
                                )
                                Text(
                                    text = if (isEnglish) "Manually add, edit, delete & launch websites" else "Webseiten manuell hinzufügen, bearbeiten, löschen & starten",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        Button(
                            onClick = onAddSiteClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentEmerald,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("web_tv_add_site_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isEnglish) "Add Website" else "Webseite hinzufügen",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Inline Quick-Add Form (Name + URL + Hinzufügen)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = quickNameInput,
                            onValueChange = { quickNameInput = it },
                            placeholder = { Text(if (isEnglish) "Name (e.g. Sport TV)" else "Name (z.B. Live TV)", fontSize = 12.sp, color = TextMuted) },
                            modifier = Modifier
                                .weight(0.8f)
                                .height(46.dp)
                                .testTag("web_tv_quick_name_input"),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = TextPrimary),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = BorderGlow.copy(alpha = 0.5f),
                                focusedContainerColor = DarkSurface,
                                unfocusedContainerColor = DarkSurface
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )

                        OutlinedTextField(
                            value = customUrlInput,
                            onValueChange = { customUrlInput = it },
                            placeholder = { Text("https://www.beispiel.de", fontSize = 12.sp, color = TextMuted) },
                            modifier = Modifier
                                .weight(1.2f)
                                .height(46.dp)
                                .testTag("web_tv_quick_url_input"),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = TextPrimary),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = BorderGlow.copy(alpha = 0.5f),
                                focusedContainerColor = DarkSurface,
                                unfocusedContainerColor = DarkSurface
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )

                        Button(
                            onClick = {
                                val target = WebTvSitesManager.normalizeWebUrl(customUrlInput)
                                if (target.isNotBlank() && target != "https://" && target != "http://") {
                                    onSaveCustomSite(quickNameInput, target)
                                    quickNameInput = ""
                                    customUrlInput = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GoldPrimary,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(46.dp)
                                .testTag("web_tv_quick_save_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Speichern", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEnglish) "Saved Web TV Websites (${sites.size}):" else "Gespeicherte Web TV Webseiten (${sites.size}):",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = GoldLight
                )
            }
        }

        items(sites, key = { it.id }) { portal ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, BorderGlow.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .clickable { onOpenPortal(portal) }
                    .testTag("web_tv_site_card_${portal.id}"),
                colors = CardDefaults.cardColors(containerColor = DarkNavyCard)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DarkSurfaceElevated)
                                    .border(1.dp, portal.badgeColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = portal.iconEmoji, fontSize = 22.sp)
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = portal.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(portal.badgeColor.copy(alpha = 0.15f))
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = portal.category.ifBlank { portal.badge },
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = portal.badgeColor
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = portal.url,
                                    fontSize = 11.sp,
                                    color = AccentCyan,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (portal.description.isNotBlank() && portal.description != portal.url) {
                                    Text(
                                        text = portal.description,
                                        fontSize = 11.sp,
                                        color = TextSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Action Row: Öffnen, In-App, Bearbeiten, Löschen
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onOpenPortal(portal) },
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(34.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Launch, contentDescription = null, tint = ObsidianBlack, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Öffnen", color = ObsidianBlack, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { onOpenInApp(portal) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(34.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.ChromeReaderMode, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "In-App", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { onEditSite(portal) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldLight),
                                border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("web_tv_edit_site_${portal.id}"),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Bearbeiten", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Bearbeiten", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { onDeleteSite(portal) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRose),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AccentRose.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("web_tv_delete_site_${portal.id}"),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Löschen", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Löschen", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderGlow.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = if (isEnglish) "Open custom URL directly in Browser:" else "Beliebige Webadresse direkt im Browser starten:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldLight
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customUrlInput,
                            onValueChange = { customUrlInput = it },
                            placeholder = { Text("https://...", fontSize = 12.sp, color = TextMuted) },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = TextPrimary),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = BorderGlow.copy(alpha = 0.5f),
                                focusedContainerColor = DarkNavyBackground,
                                unfocusedContainerColor = DarkNavyBackground
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )

                        Button(
                            onClick = {
                                val target = WebTvSitesManager.normalizeWebUrl(customUrlInput)
                                if (target.isNotBlank()) {
                                    onCustomUrlLaunch(target)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(46.dp)
                        ) {
                            Text("Start", color = ObsidianBlack, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 3. Safe Embedded Browser Screen: Hardened WebView with zero crash propagation
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SafeEmbeddedBrowserScreen(
    sites: List<WebTvSiteItem>,
    initialUrl: String = "http://kool.to/",
    isFullscreen: Boolean = true,
    onToggleFullscreen: () -> Unit = {},
    onExitFullscreenWebView: () -> Unit = {},
    onPlayDetectedStream: (streamUrl: String, title: String) -> Unit,
    onLaunchExternal: (String) -> Unit,
    onAddCurrentPageToSites: (title: String, url: String) -> Unit = { _, _ -> },
    isEnglish: Boolean
) {
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    var currentUrl by remember(initialUrl) { mutableStateOf(initialUrl) }
    var inputUrlText by remember(initialUrl) { mutableStateOf(initialUrl) }
    var pageTitle by remember { mutableStateOf("Web TV") }
    var pageLoadingProgress by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(false) }
    var detectedStreamUrl by remember { mutableStateOf<String?>(null) }
    var detectedStreamTitle by remember { mutableStateOf("Stream") }
    var webViewError by remember { mutableStateOf<String?>(null) }
    var webViewKey by remember { mutableIntStateOf(1) }
    var canGoBack by remember { mutableStateOf(false) }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var customVideoView by remember { mutableStateOf<View?>(null) }
    var customVideoCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    fun triggerSafeStreamPlayback(streamUrl: String, title: String) {
        val cleanUrl = streamUrl.trim()
        if (cleanUrl.isBlank() || (!cleanUrl.startsWith("http://", ignoreCase = true) && !cleanUrl.startsWith("https://", ignoreCase = true))) {
            return
        }
        mainHandler.post {
            runCatching {
                webViewRef?.evaluateJavascript(
                    "try{document.querySelectorAll('video,audio').forEach(function(v){v.pause();});}catch(e){}",
                    null
                )
                onPlayDetectedStream(cleanUrl, title.ifBlank { pageTitle.ifBlank { "Live Stream" } })
            }
        }
    }

    BackHandler(enabled = true) {
        try {
            if (customVideoView != null) {
                customVideoCallback?.onCustomViewHidden()
                customVideoView = null
                customVideoCallback = null
            } else if (webViewRef?.canGoBack() == true) {
                webViewRef?.goBack()
            } else {
                onExitFullscreenWebView()
            }
        } catch (e: Exception) {
            onExitFullscreenWebView()
        }
    }

    DisposableEffect(webViewKey) {
        onDispose {
            try {
                customVideoCallback?.onCustomViewHidden()
                customVideoView = null
                customVideoCallback = null
                val wb = webViewRef
                webViewRef = null
                wb?.let {
                    it.stopLoading()
                    it.loadUrl("about:blank")
                    (it.parent as? ViewGroup)?.removeView(it)
                    it.destroy()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        if (!isFullscreen) {
            // Navigation Toolbar (only shown when user exits fullscreen toolbar mode)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkNavyCard)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = {
                        if (webViewRef?.canGoBack() == true) {
                            runCatching { webViewRef?.goBack() }
                        } else {
                            onExitFullscreenWebView()
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = GoldPrimary)
                }

                IconButton(
                    onClick = { runCatching { webViewRef?.reload() } },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = GoldLight)
                }

                OutlinedTextField(
                    value = inputUrlText,
                    onValueChange = { inputUrlText = it },
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = TextPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = BorderGlow.copy(alpha = 0.5f),
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface
                    ),
                    shape = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            val target = WebTvSitesManager.normalizeWebUrl(inputUrlText)
                            if (target.isNotBlank()) {
                                currentUrl = target
                                inputUrlText = target
                                detectedStreamUrl = null
                                webViewRef?.loadUrl(target)
                            }
                        }
                    )
                )

                // Enter Fullscreen Mode button
                IconButton(
                    onClick = onToggleFullscreen,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Fullscreen, contentDescription = "Vollbildmodus", tint = GoldPrimary)
                }

                // Save current page to Web TV websites
                IconButton(
                    onClick = { onAddCurrentPageToSites(pageTitle.ifBlank { currentUrl }, currentUrl) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Webseite speichern", tint = AccentEmerald)
                }

                // Open in High-Speed Custom Tab
                IconButton(
                    onClick = { onLaunchExternal(currentUrl) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open in Chrome/Samsung", tint = GoldPrimary)
                }
            }

            // Dynamic Portal Shortcuts from WebTvSitesManager
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkNavyCard)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                sites.forEach { site ->
                    val isCurrent = currentUrl.startsWith(site.url.removeSuffix("/"))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isCurrent) GoldPrimary.copy(alpha = 0.25f) else DarkSurface)
                            .border(1.dp, if (isCurrent) GoldPrimary else BorderGlow.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .clickable {
                                currentUrl = site.url
                                inputUrlText = site.url
                                detectedStreamUrl = null
                                webViewRef?.loadUrl(site.url)
                            }
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${site.iconEmoji} ${site.name}",
                            fontSize = 11.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCurrent) GoldPrimary else TextSecondary
                        )
                    }
                }
            }

            // Stream Detected Banner (when not in fullscreen)
            detectedStreamUrl?.let { stream ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .border(1.5.dp, GoldPrimary, RoundedCornerShape(10.dp)),
                    colors = CardDefaults.cardColors(containerColor = DarkNavyCard)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("⚡ Video-Stream erkannt", color = GoldPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(detectedStreamTitle.ifBlank { pageTitle }, color = TextSecondary, fontSize = 11.sp, maxLines = 1)
                        }
                        Button(
                            onClick = { triggerSafeStreamPlayback(stream, detectedStreamTitle) },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Im Nexus Player abspielen", color = ObsidianBlack, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        if (isLoading) {
            LinearProgressIndicator(
                progress = { pageLoadingProgress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = GoldPrimary,
                trackColor = DarkSurface
            )
        }

        // Fullscreen-capable WebView Container
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(ObsidianBlack)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    try {
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )

                            // Do NOT override layer type to avoid Samsung S25 Ultra Vulkan driver conflicts
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                mediaPlaybackRequiresUserGesture = false
                                loadsImagesAutomatically = true
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                setSupportMultipleWindows(false)
                                javaScriptCanOpenWindowsAutomatically = false
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                allowFileAccess = false
                            }

                            try {
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    return try {
                                        val reqUrl = request?.url?.toString()?.trim() ?: return false
                                        // Block non-HTTP schemes (e.g. intent://, market://, tg://, javascript:) from crashing WebView
                                        if (!reqUrl.startsWith("http://", ignoreCase = true) &&
                                            !reqUrl.startsWith("https://", ignoreCase = true)
                                        ) {
                                            return true
                                        }

                                        // Direct .m3u8 file link clicked -> play in Nexus Player on UI thread
                                        if (reqUrl.endsWith(".m3u8", ignoreCase = true) ||
                                            reqUrl.contains(".m3u8?", ignoreCase = true)
                                        ) {
                                            mainHandler.post {
                                                runCatching {
                                                    detectedStreamUrl = reqUrl
                                                    detectedStreamTitle = pageTitle.ifBlank { "Live Stream" }
                                                    triggerSafeStreamPlayback(reqUrl, detectedStreamTitle)
                                                }
                                            }
                                            return true
                                        }

                                        false
                                    } catch (_: Throwable) {
                                        true
                                    }
                                }

                                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                    super.doUpdateVisitedHistory(view, url, isReload)
                                    runCatching { canGoBack = view?.canGoBack() == true }
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isLoading = true
                                    runCatching { canGoBack = view?.canGoBack() == true }
                                    url?.let {
                                        currentUrl = it
                                        inputUrlText = it
                                    }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isLoading = false
                                    runCatching { canGoBack = view?.canGoBack() == true }
                                    url?.let {
                                        currentUrl = it
                                        inputUrlText = it
                                        runCatching {
                                            val t = view?.title
                                            if (!t.isNullOrBlank()) pageTitle = t
                                        }
                                    }

                                    // Inject non-looping anti-pause & inline video helper
                                    runCatching {
                                        view?.evaluateJavascript(
                                            """
                                            (function() {
                                                try {
                                                    if (!window.__madPatched) {
                                                        window.__madPatched = true;
                                                        var origPause = HTMLVideoElement.prototype.pause;
                                                        HTMLVideoElement.prototype.pause = function() {
                                                            if (this.currentTime > 0.5 && !this.ended && this.readyState >= 2) {
                                                                return;
                                                            }
                                                            return origPause.apply(this, arguments);
                                                        };
                                                        window.open = function() { return null; };
                                                    }
                                                    var vids = document.querySelectorAll('video');
                                                    for (var i = 0; i < vids.length; i++) {
                                                        vids[i].setAttribute('playsinline', 'true');
                                                        vids[i].setAttribute('webkit-playsinline', 'true');
                                                        vids[i].autoplay = true;
                                                    }
                                                } catch(e) {}
                                            })();
                                            """.trimIndent(), null
                                        )
                                    }
                                }

                                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                                    webViewRef = null
                                    view?.let {
                                        try {
                                            (it.parent as? ViewGroup)?.removeView(it)
                                            it.destroy()
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                    webViewKey++
                                    return true
                                }

                                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                    // CRITICAL: shouldInterceptRequest runs on Chromium's background IO thread (Chrome_IOThread)!
                                    // Never touch `view` on this thread; always post UI state updates via `mainHandler`.
                                    // Do NOT interrupt WebView playback here so kool.to streams play seamlessly inside the Web-Ansicht.
                                    try {
                                        val reqUrl = request?.url?.toString()?.trim() ?: return super.shouldInterceptRequest(view, request)
                                        val koolStream = KoolToCatalogHelper.extractPlayableStreamUrl(reqUrl)
                                        val isGenericHls = reqUrl.contains(".m3u8", ignoreCase = true)
                                        if (koolStream != null || isGenericHls) {
                                            val targetStream = koolStream ?: reqUrl
                                            mainHandler.post {
                                                runCatching {
                                                    detectedStreamUrl = targetStream
                                                    detectedStreamTitle = pageTitle.ifBlank { "Web Stream" }
                                                }
                                            }
                                        }
                                    } catch (_: Throwable) {
                                    }
                                    return super.shouldInterceptRequest(view, request)
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    pageLoadingProgress = newProgress / 100f
                                    if (newProgress == 100) isLoading = false
                                }

                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    super.onReceivedTitle(view, title)
                                    if (!title.isNullOrBlank()) pageTitle = title
                                }

                                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                    if (view == null) return
                                    customVideoView = view
                                    customVideoCallback = callback
                                }

                                override fun onHideCustomView() {
                                    customVideoCallback?.onCustomViewHidden()
                                    customVideoView = null
                                    customVideoCallback = null
                                }
                            }

                            loadUrl(currentUrl)
                            webViewRef = this
                        }
                    } catch (t: Throwable) {
                        t.printStackTrace()
                        webViewError = t.localizedMessage
                        View(ctx)
                    }
                },
                update = { view ->
                    if (view is WebView) webViewRef = view
                }
            )

            // HTML5 Video Fullscreen Custom View Overlay (when user taps fullscreen inside the web video player)
            customVideoView?.let { videoView ->
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    factory = { ctx ->
                        FrameLayout(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            (videoView.parent as? ViewGroup)?.removeView(videoView)
                            addView(
                                videoView,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                        }
                    }
                )
            }

            // Compact Floating Control Pill in Fullscreen Web-Ansicht
            if (isFullscreen) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 10.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(ObsidianBlack.copy(alpha = 0.78f))
                        .border(1.dp, GoldPrimary.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (canGoBack) {
                        IconButton(
                            onClick = { runCatching { webViewRef?.goBack() } },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Zurück",
                                tint = GoldPrimary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { runCatching { webViewRef?.reload() } },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Neu laden",
                            tint = GoldLight,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onToggleFullscreen,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FullscreenExit,
                            contentDescription = "Leiste einblenden",
                            tint = AccentCyan,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    IconButton(
                        onClick = onExitFullscreenWebView,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Vollbild schließen",
                            tint = AccentRose,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Fast multi-stage asynchronous resolver to extract underlying HLS stream URLs from web TV pages
 */
private suspend fun tryResolveLiveStream(pageUrl: String): String? {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            val request = Request.Builder()
                .url(pageUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Referer", pageUrl)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return@withContext null

            // 1. Direct .m3u8 regex
            val m3u8Regex = """https?://[^\s"'<>]+\.m3u8[^\s"'<>]*""".toRegex()
            val directMatch = m3u8Regex.find(html)
            if (directMatch != null) {
                return@withContext directMatch.value
            }

            // 2. Check for player iframe sources
            val iframeRegex = """<iframe[^>]+src=["'](https?://[^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
            val iframeMatch = iframeRegex.find(html)
            if (iframeMatch != null) {
                val iframeUrl = iframeMatch.groupValues[1]
                val iframeReq = Request.Builder()
                    .url(iframeUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", pageUrl)
                    .build()
                val iframeRes = client.newCall(iframeReq).execute()
                val iframeHtml = iframeRes.body?.string() ?: ""
                val nestedM3u8 = m3u8Regex.find(iframeHtml)
                if (nestedM3u8 != null) {
                    return@withContext nestedM3u8.value
                }
            }

            // 3. Check for base64 encoded stream sources (e.g., atob("..."))
            val atobRegex = """atob\(["']([A-Za-z0-9+/=]{20,})["']\)""".toRegex()
            val atobMatch = atobRegex.find(html)
            if (atobMatch != null) {
                try {
                    val decoded = String(android.util.Base64.decode(atobMatch.groupValues[1], android.util.Base64.DEFAULT))
                    val decodedM3u8 = m3u8Regex.find(decoded)
                    if (decodedM3u8 != null) {
                        return@withContext decodedM3u8.value
                    }
                } catch (e: Exception) {
                    // Ignore decoding errors
                }
            }

            null
        } catch (e: Exception) {
            null
        }
    }
}
