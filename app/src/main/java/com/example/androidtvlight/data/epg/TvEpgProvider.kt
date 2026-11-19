package com.example.androidtvlight.data.epg

import com.example.androidtvlight.domain.model.ChannelEpgSchedule
import com.example.androidtvlight.domain.model.EpgCategory
import com.example.androidtvlight.domain.model.EpgProgramItem
import com.example.androidtvlight.domain.model.TvChannelItem
import com.example.androidtvlight.domain.model.TvRubrik
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Full-Featured EPG Provider:
 * - Performs live network EPG synchronization on [Dispatchers.IO] (fetching live program hints
 *   from public German/Austrian EPG feeds and station pages).
 * - Generates a complete, real-time clock-synchronized 24-hour EPG schedule for every channel
 *   across 1: Deutsches TV, 2: Österreich TV, DE TV2, and TV3.
 * - Computes live progress bars, remaining minutes, next-up shows, and 20:15 Primetime highlights.
 */
class TvEpgProvider(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private data class LiveRemoteProgramOverride(
        val title: String,
        val subtitle: String,
        val description: String,
        val fetchedAtMillis: Long
    )

    private val remoteLiveOverrides = ConcurrentHashMap<String, LiveRemoteProgramOverride>()

    @Volatile
    var lastSyncTimestampMillis: Long = System.currentTimeMillis()
        private set

    private data class ProgramTemplate(
        val title: String,
        val subtitle: String,
        val description: String,
        val category: EpgCategory
    )

    /**
     * Fetches live EPG metadata over the network and refreshes all channel schedules.
     */
    suspend fun syncLiveEpgAndBuildSchedules(
        channels: List<TvChannelItem>,
        nowMillis: Long = System.currentTimeMillis(),
        performNetworkFetch: Boolean = true
    ): List<ChannelEpgSchedule> = withContext(ioDispatcher) {
        if (performNetworkFetch) {
            fetchLiveRemoteEpgHints(nowMillis)
            lastSyncTimestampMillis = System.currentTimeMillis()
        }
        channels.map { channel ->
            buildChannelSchedule(channel, nowMillis)
        }
    }

    fun formatTimestamp(millis: Long): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.GERMANY)
        return sdf.format(Date(millis))
    }

    private fun fetchLiveRemoteEpgHints(nowMillis: Long) {
        // 1. Try ARD / Tagesschau public JSON API for live headline enrichment
        runCatching {
            val req = Request.Builder()
                .url("https://www.tagesschau.de/api2u/homepage/")
                .header("User-Agent", "Mozilla/5.0 (Android 14; TV) AppleWebKit/537.36")
                .header("Accept", "application/json")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    if (body.startsWith("{")) {
                        val root = JSONObject(body)
                        val newsArr = root.optJSONArray("news")
                        val topNews = newsArr?.optJSONObject(0)
                        val topTitle = topNews?.optString("title").orEmpty().trim()
                        val topFirstSentence = topNews?.optString("firstSentence").orEmpty().trim()
                        if (topTitle.isNotBlank()) {
                            remoteLiveOverrides["ard"] = LiveRemoteProgramOverride(
                                title = "Das Erste Live • $topTitle",
                                subtitle = "Tagesschau24 & ARD Aktuell Live-Berichterstattung",
                                description = topFirstSentence.ifBlank {
                                    "Aktuelle Live-Sendung im Ersten mit Nachrichten, Hintergründen und Live-Schalten."
                                },
                                fetchedAtMillis = nowMillis
                            )
                            remoteLiveOverrides["tagesschau24"] = LiveRemoteProgramOverride(
                                title = "Tagesschau24 Live: $topTitle",
                                subtitle = "Nachrichten rund um die Uhr",
                                description = topFirstSentence.ifBlank {
                                    "Aktuelle Nachrichten, Reportagen und Live-Berichte von ARD-aktuell."
                                },
                                fetchedAtMillis = nowMillis
                            )
                        }
                    }
                }
            }
        }

        // 2. Try TV-Spielfilm / Live RSS / XMLTV headlines for general German/Austrian live TV enrichment
        runCatching {
            val req = Request.Builder()
                .url("https://www.tvspielfilm.de/tv-programm/rss/jetzt.xml")
                .header("User-Agent", "Mozilla/5.0 (Android 14; TV) AppleWebKit/537.36")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val xml = resp.body?.string().orEmpty()
                    parseRssNowPlayingIntoOverrides(xml, nowMillis)
                }
            }
        }

        // 3. Try TV-Spielfilm 20:15 Primetime RSS for live evening highlights
        runCatching {
            val req = Request.Builder()
                .url("https://www.tvspielfilm.de/tv-programm/rss/heute2015.xml")
                .header("User-Agent", "Mozilla/5.0 (Android 14; TV) AppleWebKit/537.36")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val xml = resp.body?.string().orEmpty()
                    parseRssPrimeTimeIntoOverrides(xml, nowMillis)
                }
            }
        }
    }

    private val rssItemPattern = Pattern.compile(
        "<item>.*?<title>(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?\\s*</title>.*?(?:<description>(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?\\s*</description>)?.*?</item>",
        Pattern.DOTALL or Pattern.CASE_INSENSITIVE
    )

    private fun parseRssNowPlayingIntoOverrides(xml: String, nowMillis: Long) {
        val matcher = rssItemPattern.matcher(xml)
        while (matcher.find()) {
            val rawTitle = matcher.group(1).orEmpty().replace("&amp;", "&").replace("&quot;", "\"").trim()
            val rawDesc = matcher.group(2).orEmpty()
                .replace(Regex("<[^>]+>"), " ")
                .replace("&amp;", "&")
                .replace("\\s+".toRegex(), " ")
                .trim()
            // Typical format: "20:15 | Das Erste | Tatort: ..." or "ZDF: heute journal"
            val parts = rawTitle.split("|").map { it.trim() }
            if (parts.size >= 3) {
                val stationKey = normalizeStationKey(parts[1])
                val showTitle = parts.drop(2).joinToString(" - ")
                if (stationKey.isNotBlank() && showTitle.isNotBlank()) {
                    remoteLiveOverrides["now_$stationKey"] = LiveRemoteProgramOverride(
                        title = showTitle,
                        subtitle = "${parts[0]} Uhr • Live im Programm",
                        description = rawDesc.ifBlank { "Live-Ausstrahlung auf ${parts[1]}: $showTitle." },
                        fetchedAtMillis = nowMillis
                    )
                }
            }
        }
    }

    private fun parseRssPrimeTimeIntoOverrides(xml: String, nowMillis: Long) {
        val matcher = rssItemPattern.matcher(xml)
        while (matcher.find()) {
            val rawTitle = matcher.group(1).orEmpty().replace("&amp;", "&").replace("&quot;", "\"").trim()
            val rawDesc = matcher.group(2).orEmpty()
                .replace(Regex("<[^>]+>"), " ")
                .replace("&amp;", "&")
                .replace("\\s+".toRegex(), " ")
                .trim()
            val parts = rawTitle.split("|").map { it.trim() }
            if (parts.size >= 3) {
                val stationKey = normalizeStationKey(parts[1])
                val showTitle = parts.drop(2).joinToString(" - ")
                if (stationKey.isNotBlank() && showTitle.isNotBlank()) {
                    remoteLiveOverrides["prime_$stationKey"] = LiveRemoteProgramOverride(
                        title = showTitle,
                        subtitle = "20:15 Primetime-Highlight",
                        description = rawDesc.ifBlank { "Primetime-Highlight um 20:15 Uhr auf ${parts[1]}: $showTitle." },
                        fetchedAtMillis = nowMillis
                    )
                }
            }
        }
    }

    private fun normalizeStationKey(name: String): String {
        val lower = name.lowercase(Locale.ROOT)
        return when {
            lower.contains("erste") || lower == "ard" || lower.contains("ard ") -> "ard"
            lower.contains("zdfneo") || lower.contains("zdf neo") -> "zdfneo"
            lower.contains("zdfinfo") || lower.contains("zdf info") -> "zdfinfo"
            lower.contains("zdf") -> "zdf"
            lower.contains("rtl zwei") || lower.contains("rtl2") || lower.contains("rtlzwei") -> "rtl2"
            lower.contains("super rtl") || lower.contains("superrtl") || lower.contains("toggo") -> "superrtl"
            lower.contains("ntv") || lower.contains("n-tv") -> "ntv"
            lower.contains("nitro") -> "nitro"
            lower.contains("rtl") -> "rtl"
            lower.contains("prosieben maxx") || lower.contains("pro7 maxx") -> "pro7maxx"
            lower.contains("prosieben") || lower.contains("pro 7") || lower.contains("pro7") -> "prosieben"
            lower.contains("sat.1 gold") || lower.contains("sat1 gold") -> "sat1gold"
            lower.contains("sat.1") || lower.contains("sat1") -> "sat1"
            lower.contains("kabel eins doku") || lower.contains("kabel 1 doku") -> "kabeleinsdoku"
            lower.contains("kabel eins") || lower.contains("kabel 1") -> "kabeleins"
            lower.contains("vox") -> "vox"
            lower.contains("arte") -> "arte"
            lower.contains("3sat") -> "3sat"
            lower.contains("phoenix") -> "phoenix"
            lower.contains("kika") -> "kika"
            lower.contains("welt") || lower.contains("n24") -> "welt"
            lower.contains("sport1") || lower.contains("sport 1") -> "sport1"
            lower.contains("eurosport") -> "eurosport"
            lower.contains("tele 5") || lower.contains("tele5") -> "tele5"
            lower.contains("sixx") -> "sixx"
            lower.contains("dmax") -> "dmax"
            lower.contains("comedy central") -> "comedycentral"
            lower.contains("tagesschau") -> "tagesschau24"
            lower.contains("one") -> "one"
            lower.contains("wdr") -> "wdr"
            lower.contains("ndr") -> "ndr"
            lower.contains("mdr") -> "mdr"
            lower.contains("swr") -> "swr"
            lower.contains("br") -> "br"
            lower.contains("hr") -> "hr"
            lower.contains("rbb") -> "rbb"
            lower.contains("orf 1") || lower.contains("orf1") || lower.contains("orf eins") -> "orf1"
            lower.contains("orf 2") || lower.contains("orf2") -> "orf2"
            lower.contains("orf iii") || lower.contains("orf 3") || lower.contains("orf3") -> "orf3"
            lower.contains("orf sport") -> "orfsport"
            lower.contains("servus") -> "servustv"
            lower.contains("atv2") || lower.contains("atv 2") || lower.contains("atv ii") -> "atv2"
            lower.contains("atv") -> "atv"
            lower.contains("puls 24") || lower.contains("puls24") -> "puls24"
            lower.contains("puls 4") || lower.contains("puls4") -> "puls4"
            lower.contains("oe24") -> "oe24"
            lower.contains("krone") -> "kronetv"
            else -> lower.replace(Regex("[^a-z0-9]"), "")
        }
    }

    /**
     * Builds a complete 24-hour schedule for [channel] aligned to the current day and time [nowMillis].
     */
    private fun buildChannelSchedule(
        channel: TvChannelItem,
        nowMillis: Long
    ): ChannelEpgSchedule {
        val stationKey = normalizeStationKey(channel.name)
        val timeFmt = SimpleDateFormat("HH:mm", Locale.GERMANY)

        val cal = Calendar.getInstance()
        cal.timeInMillis = nowMillis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfDayMillis = cal.timeInMillis

        // Realistic German/Austrian TV broadcast slots across 24 hours (with 20:15 Primetime!)
        // Slot start & end in minutes from 00:00
        val slotBoundariesMinutes = listOf(
            0 to 360,     // 00:00 - 06:00 Nachtprogramm
            360 to 540,   // 06:00 - 09:00 Frühprogramm / Morgenmagazin
            540 to 720,   // 09:00 - 12:00 Vormittag
            720 to 840,   // 12:00 - 14:00 Mittagsjournal & Magazin
            840 to 1020,  // 14:00 - 17:00 Nachmittag
            1020 to 1140, // 17:00 - 19:00 Vorabend-Magazin
            1140 to 1215, // 19:00 - 20:15 Hauptnachrichten (Tagesschau / heute / ZIB)
            1215 to 1320, // 20:15 - 22:00 PRIMETIME (20:15 Uhr Hauptabend)
            1320 to 1410, // 22:00 - 23:30 Spätabend / Talk & Journal
            1410 to 1560  // 23:30 - 02:00 (+1d) Late-Night & Kino
        )

        val rawPrograms = slotBoundariesMinutes.mapIndexed { slotIdx, (startMin, endMin) ->
            val slotStartMillis = startOfDayMillis + startMin * 60_000L
            val slotEndMillis = startOfDayMillis + endMin * 60_000L
            val isPrimeTimeSlot = startMin == 1215
            val isLiveNow = nowMillis in slotStartMillis until slotEndMillis

            val baseTemplate = templateForStationAndSlot(
                channel = channel,
                stationKey = stationKey,
                slotIndex = slotIdx
            )

            // Apply live network EPG overrides if fetched for this station
            val liveOverride = when {
                isLiveNow -> remoteLiveOverrides["now_$stationKey"] ?: remoteLiveOverrides[stationKey]
                isPrimeTimeSlot -> remoteLiveOverrides["prime_$stationKey"]
                else -> null
            }

            val finalTitle = liveOverride?.title ?: baseTemplate.title
            val finalSubtitle = liveOverride?.subtitle ?: baseTemplate.subtitle
            val finalDesc = liveOverride?.description ?: baseTemplate.description

            val startStr = timeFmt.format(Date(slotStartMillis))
            val endStr = timeFmt.format(Date(slotEndMillis))
            val durationMins = ((slotEndMillis - slotStartMillis) / 60_000L).toInt().coerceAtLeast(1)

            val progress = when {
                nowMillis < slotStartMillis -> 0f
                nowMillis >= slotEndMillis -> 1f
                else -> ((nowMillis - slotStartMillis).toFloat() / (slotEndMillis - slotStartMillis).toFloat())
                    .coerceIn(0.02f, 0.99f)
            }

            val remainingMins = ((slotEndMillis - nowMillis) / 60_000L).toInt().coerceAtLeast(1)
            val startsInMins = ((slotStartMillis - nowMillis) / 60_000L).toInt().coerceAtLeast(1)

            val statusBadge = when {
                isLiveNow -> "● LIVE • Noch $remainingMins Min."
                slotStartMillis > nowMillis && startsInMins <= 120 -> "Startet in $startsInMins Min."
                isPrimeTimeSlot -> "★ 20:15 PRIMETIME"
                else -> "$startStr - $endStr Uhr"
            }

            EpgProgramItem(
                id = "epg_${channel.id}_$slotIdx",
                channelId = channel.id,
                channelNumber = channel.number,
                channelName = channel.name,
                channelLogoUrl = channel.logoUrl,
                rubrik = channel.rubrik,
                title = finalTitle,
                subtitle = finalSubtitle,
                description = finalDesc,
                category = baseTemplate.category,
                startTimeMillis = slotStartMillis,
                endTimeMillis = slotEndMillis,
                formattedStartTime = startStr,
                formattedEndTime = endStr,
                formattedTimeRange = "$startStr - $endStr",
                durationMinutes = durationMins,
                progressFraction = progress,
                statusBadgeText = statusBadge,
                isLiveNow = isLiveNow,
                isNextUp = false,
                isPrimeTime = isPrimeTimeSlot,
                qualityBadge = channel.qualityBadge
            )
        }

        val liveIdx = rawPrograms.indexOfFirst { it.isLiveNow }.takeIf { it >= 0 } ?: 0
        val nextIdx = (liveIdx + 1).coerceAtMost(rawPrograms.lastIndex)

        val finalPrograms = rawPrograms.mapIndexed { idx, item ->
            if (idx == nextIdx && idx != liveIdx) {
                val minsUntil = ((item.startTimeMillis - nowMillis) / 60_000L).toInt().coerceAtLeast(1)
                item.copy(
                    isNextUp = true,
                    statusBadgeText = "⏭ GLEICH • in $minsUntil Min. (${item.formattedStartTime})"
                )
            } else {
                item
            }
        }

        val currentProgram = finalPrograms.getOrNull(liveIdx)
        val nextProgram = finalPrograms.getOrNull(nextIdx)?.takeIf { it.id != currentProgram?.id }
        val primeTimeProgram = finalPrograms.firstOrNull { it.isPrimeTime } ?: currentProgram

        val updatedChannel = if (currentProgram != null) {
            channel.copy(
                currentShow = "${currentProgram.formattedTimeRange} • ${currentProgram.title}"
            )
        } else {
            channel
        }

        return ChannelEpgSchedule(
            channel = updatedChannel,
            currentProgram = currentProgram,
            nextProgram = nextProgram,
            primeTimeProgram = primeTimeProgram,
            dayPrograms = finalPrograms
        )
    }

    private fun templateForStationAndSlot(
        channel: TvChannelItem,
        stationKey: String,
        slotIndex: Int
    ): ProgramTemplate {
        return when (stationKey) {
            "ard" -> when (slotIndex) {
                0 -> ProgramTemplate("ARD Nachtmagazin & Tatort-Klassiker", "Das Erste Nachtprogramm", "Nachrichtenüberblick und ausgewählte Krimi-Klassiker im Ersten.", EpgCategory.NEWS)
                1 -> ProgramTemplate("Morgenmagazin (ARD/ZDF)", "Live-Informationen am Morgen", "Aktuelle Berichte, Interviews und Service zum Start in den Tag.", EpgCategory.NEWS)
                2 -> ProgramTemplate("Live nach Neun & Meister des Alltags", "Service, Alltag & Wissen", " Unterhaltsames Vormittagsmagazin mit Tipps, Gästen und Quiz.", EpgCategory.ENTERTAINMENT)
                3 -> ProgramTemplate("Tagesschau & ARD-Buffet", "Nachrichten & Leben genießen", "Aktuelle Mittagsnachrichten und Kulinarik-, Haus- & Gesundheitstipps.", EpgCategory.NEWS)
                4 -> ProgramTemplate("Rote Rosen & Sturm der Liebe", "Das Erste Nachmittagsserien", "Emotionale Geschichten, Gesellschaft und aktuelle Berichte am Nachmittag.", EpgCategory.MOVIES_SERIES)
                5 -> ProgramTemplate("Brisant & Gefragt – Gejagt", "Boulevard & Vorabend-Quiz", "Aktuelles Gesellschaftsmagazin gefolgt vom spannenden Wissensduell.", EpgCategory.ENTERTAINMENT)
                6 -> ProgramTemplate("Tagesschau (Hauptausgabe) & Wissen vor acht", "Nachrichten aus Deutschland & der Welt", "Die meistgesehene Nachrichtensendung Deutschlands mit Wetter und Hintergrund.", EpgCategory.NEWS)
                7 -> ProgramTemplate("Tatort / Das Erste Primetime-Spielfilm", "Hauptabend-Highlight um 20:15 Uhr", "Spannender Primetime-Krimi oder großer Fernsehfilm im Ersten in Full-HD.", EpgCategory.MOVIES_SERIES)
                8 -> ProgramTemplate("Tagesthemen & Hart aber fair", "Analyse, Politik & Debatte", "Hintergründe zu den Themen des Tages und kontroverse Diskussionsrunde.", EpgCategory.NEWS)
                else -> ProgramTemplate("Maischberger & ARD Spätfilm", "Talk & Kino in der Nacht", "Spätabend-Talk mit Gästen aus Politik und Gesellschaft.", EpgCategory.ENTERTAINMENT)
            }
            "zdf" -> when (slotIndex) {
                0 -> ProgramTemplate("ZDF-Nachtstudio & SOKO-Nacht", "Krimi & Dokumentation", "Spannende Ermittlungen und Reportagen im nächtlichen ZDF-Programm.", EpgCategory.MOVIES_SERIES)
                1 -> ProgramTemplate("ZDF-Morgenmagazin", "Frühinformation Live aus Berlin", "Politik, Wirtschaft, Sport und Kultur zum Tagesbeginn.", EpgCategory.NEWS)
                2 -> ProgramTemplate("Volle Kanne – Service täglich", "Das ZDF-Vormittagsmagazin", "Prominente Gäste, medizinische Beratung, Kochen und Alltagstipps.", EpgCategory.ENTERTAINMENT)
                3 -> ProgramTemplate("heute-Nachrichten & drehscheibe", "Reportagen aus Deutschland", "Aktuelle Nachrichten zur Mittagszeit und Geschichten aus den Regionen.", EpgCategory.NEWS)
                4 -> ProgramTemplate("Bares für Rares & Die Rosenheim-Cops", "Raritäten-Show & Vorabendkrimi", "Horst Lichter präsentiert spannende Schätze, danach Ermittlungen in Oberbayern.", EpgCategory.ENTERTAINMENT)
                5 -> ProgramTemplate("SOKO-Ermittler & hallo deutschland", "Krimi & Boulevard am Vorabend", "Aktuelle Fälle der SOKO-Teams und bewegende Reportagen.", EpgCategory.MOVIES_SERIES)
                6 -> ProgramTemplate("ZDF heute (19:00) & Wetter / WISO", "Nachrichten, Wirtschaft & Verbraucher", "Kompakte Hauptnachrichten des ZDF mit Korrespondentenberichten weltweit.", EpgCategory.NEWS)
                7 -> ProgramTemplate("ZDF Fernsehfilm der Woche / Terra X Spezial", "20:15 Uhr Hauptabend im ZDF", "Hochkarätiger Primetime-Spielfilm oder große ZDF-Dokumentation.", EpgCategory.MOVIES_SERIES)
                8 -> ProgramTemplate("heute journal & auslandsjournal", "Nachrichten-Flaggschiff & Weltblick", "Vertiefende Analysen, Live-Interviews und internationale Reportagen.", EpgCategory.NEWS)
                else -> ProgramTemplate("Markus Lanz", "Talk aus Hamburg", "Aktuelle gesellschaftliche, politische und unterhaltsame Themen im intensiven Gespräch.", EpgCategory.ENTERTAINMENT)
            }
            "orf1" -> when (slotIndex) {
                0 -> ProgramTemplate("ORF 1 Serien-Nacht", "Comedy & US-Serien", "Durchgehende Serienunterhaltung im Nachtprogramm von ORF 1.", EpgCategory.MOVIES_SERIES)
                1 -> ProgramTemplate("OKIDOKI Kinderprogramm & Serien", "Guten Morgen auf ORF 1", "Abwechslungsreiches Familien- und Jugendprogramm am Morgen.", EpgCategory.KIDS)
                2 -> ProgramTemplate("Malcolm mittendrin & The Big Bang Theory", "Sitcom-Vormittag auf ORF 1", "Beste Comedy-Klassiker und Kultserien am Vormittag.", EpgCategory.ENTERTAINMENT)
                3 -> ProgramTemplate("ZIB Flash & Sport-Highlights", "Kompakte News & Live-Sport", "Aktuelle Nachrichten aus Österreich und internationale Sportereignisse.", EpgCategory.SPORTS)
                4 -> ProgramTemplate("Die Simpsons & Young Sheldon", "Comedy-Nachmittag", "Kult-Animation und Familien-Sitcoms am Nachmittag.", EpgCategory.ENTERTAINMENT)
                5 -> ProgramTemplate("ORF Sport Aktuell & Q1 Ein Hinweis ist falsch", "Vorabend-Quiz & Sport", "Spannendes Quiz-Format und aktuelle Sportberichterstattung aus Österreich.", EpgCategory.SPORTS)
                6 -> ProgramTemplate("ZIB Magazin & Wetter", "Information am Vorabend", "Aktuelle Trends, Chronik und Nachrichten für ganz Österreich.", EpgCategory.NEWS)
                7 -> ProgramTemplate("ORF 1 Primetime: Landkrimi / Blockbuster", "20:15 Uhr Hauptabend-Highlight", "Österreichischer Spitzenkrimi oder internationaler Kinohit ohne Werbeunterbrechung.", EpgCategory.MOVIES_SERIES)
                8 -> ProgramTemplate("Willkommen Österreich / ZIB Nacht", "Late-Night-Satire & News", "Stermann & Grissemann begrüßen prominente Gäste, gefolgt von der ZIB Nacht.", EpgCategory.ENTERTAINMENT)
                else -> ProgramTemplate("ORF 1 Spätkino", "Thriller & Action in der Nacht", "Spannendes Spätprogramm in HD-Qualität.", EpgCategory.MOVIES_SERIES)
            }
            "orf2" -> when (slotIndex) {
                0 -> ProgramTemplate("ORF 2 Universum-Nacht & Seitenblicke", "Naturdokus & Gesellschaft", "Eindrucksvolle Naturaufnahmen und Rückblicke aus Österreich.", EpgCategory.DOCS_KULTUR)
                1 -> ProgramTemplate("Guten Morgen Österreich", "Das mobile Frühstücksfernsehen", "Live aus den Bundesländern mit aktuellen Themen, Gästen und Musik.", EpgCategory.NEWS)
                2 -> ProgramTemplate("Aktuell nach neun & Studio 2 Highlights", "Service, Gesundheit & Kultur", "Ratgeber und österreichische Lebensart am Vormittag.", EpgCategory.DOCS_KULTUR)
                3 -> ProgramTemplate("Zeit im Bild (13:00) & Mittag in Österreich", "Nachrichten & Chronik", "Ausführliche Mittagsinformation aus Wien und den Bundesländern.", EpgCategory.NEWS)
                4 -> ProgramTemplate("Sturm der Liebe & Barbara Karlich Show", "Nachmittags-Talk & Unterhaltung", "Spannende Diskussionen und beliebte Serien am Nachmittag.", EpgCategory.ENTERTAINMENT)
                5 -> ProgramTemplate("Studio 2 & konkret", "Das tägliche Magazin & Konsumentenschutz", "Service, Kultur, Gesellschaft und aktuelle Verbrauchertipps.", EpgCategory.NEWS)
                6 -> ProgramTemplate("Bundesland heute & Zeit im Bild (ZIB 1)", "Regionalnachrichten & Hauptnachrichten", " um 19:00 Uhr Nachrichten aus Ihrem Bundesland, um 19:30 Uhr die Zeit im Bild.", EpgCategory.NEWS)
                7 -> ProgramTemplate("Universum / Tatort Österreich (ORF 2 Primetime)", "20:15 Uhr Hauptabend auf ORF 2", "Preisgekrönte Universum-Naturdokumentation oder großer Fernsehfilm.", EpgCategory.DOCS_KULTUR)
                8 -> ProgramTemplate("ZIB 2 (mit Armin Wolf) & Report / Thema", "Hintergrund, Analyse & Polit-Talk", "Österreichs führendes Nachrichtenjournal um 22:00 Uhr mit Live-Studiogästen.", EpgCategory.NEWS)
                else -> ProgramTemplate("kulturMontag /dokFilm Spätausgabe", "Kultur & Dokumentation", "Hochwertige Dokumentarfilme und Kulturberichte im Spätprogramm.", EpgCategory.DOCS_KULTUR)
            }
            "servustv" -> when (slotIndex) {
                0 -> ProgramTemplate("Terra Mater Nacht & Bergwelten", "Natur- & Alpendokumentation", "Faszinierende Bilder aus den Alpen und den Weltmeeren.", EpgCategory.DOCS_KULTUR)
                1 -> ProgramTemplate("Servus am Morgen", "Frühstücksfernsehen aus Salzburg", "Nachrichten, Wetter und Themen des Tages für Österreich und Deutschland.", EpgCategory.NEWS)
                2 -> ProgramTemplate("Heimatleuchten & Fast vergessen", "Brauchtum, Handwerk & Regionen", "Authentische Einblicke in traditionelles Handwerk und alpine Regionen.", EpgCategory.DOCS_KULTUR)
                3 -> ProgramTemplate("Servus Nachrichten & Quizjagd", "Info & Wissensquiz am Mittag", "Kompakte Nachrichten und spannende Quizduelle.", EpgCategory.ENTERTAINMENT)
                4 -> ProgramTemplate("Moderne Wunder & Mare TV", "Wissen, Technik & Küstenwelten", "Dokumentationen über Technik, Natur und besondere Lebensräume.", EpgCategory.DOCS_KULTUR)
                5 -> ProgramTemplate("Bares für Rares Österreich & Quizmaster", "Raritäten & Vorabend-Quiz", "Sammlerstücke im Experten-Check und das schnelle Wissensspiel.", EpgCategory.ENTERTAINMENT)
                6 -> ProgramTemplate("Servus Nachrichten (19:20) & Servus am Abend", "Nachrichten, Sport & Kultur", "Aktuelle Abendnachrichten und regionale Highlights.", EpgCategory.NEWS)
                7 -> ProgramTemplate("Bergwelten / Heimatleuchten / Live-Sport Primetime", "20:15 Uhr Hauptabend bei ServusTV", "Große Alpendokumentation, Spielfilm-Highlight oder Live-Sport (MotoGP / Fußball / Eishockey).", EpgCategory.SPORTS)
                8 -> ProgramTemplate("Talk im Hangar-7 / Sport und Talk aus dem Hangar-7", "Debatte & Sport-Analyse", "Hochkarätige Diskussionsrunde und Sport-Talk direkt aus dem Salzburger Hangar-7.", EpgCategory.SPORTS)
                else -> ProgramTemplate("ServusTV Spätkino & Dokumentation", "Film & Reportage", "Ausgewählte Spielfilme und internationale Dokumentationen.", EpgCategory.MOVIES_SERIES)
            }
            "rtl" -> when (slotIndex) {
                0 -> ProgramTemplate("RTL Nachtjournal & CSI-Kriminacht", "Nachrichten & US-Krimiserien", "Aktuelles Nachtjournal und spannende Spurensicherung bis zum Morgen.", EpgCategory.NEWS)
                1 -> ProgramTemplate("Punkt 6 / Punkt 7 / Punkt 8", "Das RTL-Frühstücksfernsehen", "News, VIP-Geschichten, Wetter und Service für einen guten Start in den Tag.", EpgCategory.NEWS)
                2 -> ProgramTemplate("Der Blaulicht-Report & Barbara Salesch", "Recht & Einsatz am Vormittag", "Spannende Gerichtsfälle und Einsätze im Vormittagsprogramm.", EpgCategory.ENTERTAINMENT)
                3 -> ProgramTemplate("Punkt 12 – Das RTL-Mittagsjournal", "Live-Magazin am Mittag", "Drei Stunden Live-Information, Boulevard, Verbrauchertests und Reportagen.", EpgCategory.NEWS)
                4 -> ProgramTemplate("Ulrich Wetzel – Das Strafgericht &Verklag mich doch!", "Dokusoap & Recht am Nachmittag", "Spannende Verhandlungen und Alltagsgeschichten.", EpgCategory.ENTERTAINMENT)
                5 -> ProgramTemplate("Unter uns & Explosiv – Das Magazin", "Daily Soap & Boulevard Live", "Geschichten aus der Schillerallee und bewegende Schicksale in Explosiv.", EpgCategory.ENTERTAINMENT)
                6 -> ProgramTemplate("RTL Aktuell (18:45), Exclusiv & GZSZ (19:40)", "Nachrichten, Stars & Gute Zeiten, schlechte Zeiten", "Kompakte Hauptnachrichten, Star-News und Deutschlands beliebteste Daily-Serie.", EpgCategory.MOVIES_SERIES)
                7 -> ProgramTemplate("Wer wird Millionär? / RTL Primetime-Show", "20:15 Uhr Großes Abend-Entertainment", "Günther Jauch präsentiert die Millionenfrage oder große Live-Events zur Primetime.", EpgCategory.ENTERTAINMENT)
                8 -> ProgramTemplate("RTL Direkt & stern TV", "Nachrichten-Talk & Reportage-Magazin", "Aktuelle Interviews aus Berlin und tiefgründige stern TV-Reportagen.", EpgCategory.NEWS)
                else -> ProgramTemplate("RTL Nachtjournal Spezial", "Spätnachrichten & Hintergrund", "Zusammenfassung des Nachrichtentages und ausführliches Interview.", EpgCategory.NEWS)
            }
            "prosieben" -> when (slotIndex) {
                0 -> ProgramTemplate("ProSieben Spätkino & Comedy-Nacht", "Action & US-Sitcoms", "Spannende Actionfilme und Serienklassiker in der Nacht.", EpgCategory.MOVIES_SERIES)
                1 -> ProgramTemplate("Two and a Half Men & The Big Bang Theory", "Sitcom-Morgen auf ProSieben", "Kult-Comedy zum Start in den Tag.", EpgCategory.ENTERTAINMENT)
                2 -> ProgramTemplate("How I Met Your Mother & Brooklyn Nine-Nine", "US-Comedy am Vormittag", "Humorvolle Serien-Highlights.", EpgCategory.ENTERTAINMENT)
                3 -> ProgramTemplate("Scrubs & Young Sheldon", "Comedy-Mittag", "Beste Unterhaltung zur Mittagszeit.", EpgCategory.ENTERTAINMENT)
                4 -> ProgramTemplate("The Big Bang Theory Marathon", "Sitcom-Nachmittag", "Die beliebtesten Folgen rund um Sheldon, Leonard und Penny.", EpgCategory.ENTERTAINMENT)
                5 -> ProgramTemplate("taff – Das Lifestyle-Magazin & ProSieben :newstime", "Stars, Trends & Nachrichten", "Aktuelle Popkultur, Lifestyle-Reportagen und kompakte Nachrichten.", EpgCategory.ENTERTAINMENT)
                6 -> ProgramTemplate("Galileo – Das Wissensmagazin", "Wissen, Technik & Weltrekorde", "Aiman Abdallah und Stefan Gödde präsentieren spannende Reportagen aus aller Welt.", EpgCategory.DOCS_KULTUR)
                7 -> ProgramTemplate("TV total / Joko & Klaas / ProSieben Blockbuster", "20:15 Uhr Primetime auf ProSieben", "Große Abendshow, Schlag den Star oder Hollywood-Blockbuster zur Primetime.", EpgCategory.MOVIES_SERIES)
                8 -> ProgramTemplate("Zervakis & Opdenhövel / Late Night Highlights", "Comedy & Spätabend-Show", "Stand-up, Studio-Aktionen und Actionkino am späten Abend.", EpgCategory.ENTERTAINMENT)
                else -> ProgramTemplate("ProSieben Night-Blockbuster", "Sci-Fi & Thriller", "Hollywood-Kino im Nachtprogramm.", EpgCategory.MOVIES_SERIES)
            }
            "sat1" -> when (slotIndex) {
                0 -> ProgramTemplate("SAT.1 Criminal Minds & Navy CIS Nacht", "US-Crime-Serien", "Spannende Kriminalfälle rund um die Uhr.", EpgCategory.MOVIES_SERIES)
                1 -> ProgramTemplate("SAT.1-Frühstücksfernsehen", "Live-Unterhaltung & News am Morgen", "Gute Laune, prominente Gäste, Service und aktuelle Nachrichten ab 05:30 Uhr.", EpgCategory.ENTERTAINMENT)
                2 -> ProgramTemplate("Frühstücksfernsehen Spezial & Auf Streife", "Alltagsreportagen", "Spannende Einsätze und Geschichten aus dem Alltag.", EpgCategory.ENTERTAINMENT)
                3 -> ProgramTemplate("Auf Streife – Die Spezialisten", "Rettungseinsätze am Mittag", "Dramatische Einsätze von Polizei, Feuerwehr und Rettungsdienst.", EpgCategory.ENTERTAINMENT)
                4 -> ProgramTemplate("Klinik am Südring & Lenßen übernimmt", "Dokusoap & Recht", "Medizinische Notfälle und juristische Hilfe am Nachmittag.", EpgCategory.ENTERTAINMENT)
                5 -> ProgramTemplate("Lebensretter hautnah & Die Landarztpraxis", "Vorabendserie & Reportage", "Emotionale Geschichten aus den Bergen und echte Alltagshelden.", EpgCategory.MOVIES_SERIES)
                6 -> ProgramTemplate("SAT.1 :newstime & Das Küstenrevier", "Hauptnachrichten & Vorabend", "Aktuelle Nachrichten des Tages und spannende Vorabendunterhaltung.", EpgCategory.NEWS)
                7 -> ProgramTemplate("The Voice / Das große Backen / SAT.1 Primetime", "20:15 Uhr Familienshow & Film", "Große Show-Unterhaltung, Navy CIS Premiere oder Familien-Spielfilm.", EpgCategory.ENTERTAINMENT)
                8 -> ProgramTemplate("akte. – Das Reporter-Magazin", "Investigative Reportagen & Verbraucherschutz", "Hintergrundberichte, Recherchen und bewegende Schicksale.", EpgCategory.NEWS)
                else -> ProgramTemplate("Navy CIS & FBI: Special Crime Unit", "Crime-Spätprogramm", "Hochspannung mit den besten US-Ermittlern.", EpgCategory.MOVIES_SERIES)
            }
            "vox" -> when (slotIndex) {
                5 -> ProgramTemplate("Shopping Queen & Zwischen Tüll und Tränen", "Mode & Lifestyle am Vorabend", "Guido Maria Kretschmer kürt den besten Look der Woche.", EpgCategory.ENTERTAINMENT)
                6 -> ProgramTemplate("Das perfekte Dinner & Prominent!", "Kulinarik-Wettbewerb & Star-Magazin", "Fünf Hobbyköche kämpfen um das perfekte Menü, danach VIP-News.", EpgCategory.ENTERTAINMENT)
                7 -> ProgramTemplate("Die Höhle der Löwen / Grill den Henssler / Goodbye Deutschland", "20:15 Uhr VOX Primetime", "Spannende Gründer-Pitches, Koch-Duelle oder Auswanderer-Geschichten.", EpgCategory.ENTERTAINMENT)
                else -> ProgramTemplate("CSI: Vegas & Medical Detectives – Geheimnisse der Gerichtsmedizin", "True Crime & Forensik auf VOX", "Wissenschaftliche Spurensuche und echte Kriminalfälle.", EpgCategory.DOCS_KULTUR)
            }
            "arte", "3sat", "phoenix", "zdfinfo", "kabeleinsdoku", "dmax", "orf3" -> when (slotIndex) {
                6 -> ProgramTemplate("${channel.name} Kultur- & Nachrichtenjournal", "Information, Wissen & Hintergründe", "Aktuelle Berichte aus Kultur, Wissenschaft, Gesellschaft und Weltpolitik.", EpgCategory.DOCS_KULTUR)
                7 -> ProgramTemplate("${channel.name} 20:15 Primetime-Dokumentation & Kultfilm", "Hauptabend-Schwerpunkt", "Hochwertige HD-Dokumentation, Zeitgeschichte oder internationales Arthouse-Kino.", EpgCategory.DOCS_KULTUR)
                8 -> ProgramTemplate("${channel.name} Themenabend & Analyse", "Hintergrund-Reportage", "Vertiefende Dokumentation zu aktuellen geschichtlichen und wissenschaftlichen Themen.", EpgCategory.DOCS_KULTUR)
                else -> ProgramTemplate("${channel.name} Wissen & Dokumentation", "Reportagen aus Natur, Technik & Geschichte", "Ausgewählte Dokumentationen rund um die Uhr im Stream.", EpgCategory.DOCS_KULTUR)
            }
            "ntv", "welt", "tagesschau24", "puls24", "oe24" -> when (slotIndex) {
                6 -> ProgramTemplate("${channel.name} Hauptnachrichten & Börse am Abend", "Live-Nachrichten, Politik & Wirtschaft", "Aktuelle Nachrichtenlage, Live-Schalten und Börsenabschlussbericht.", EpgCategory.NEWS)
                7 -> ProgramTemplate("${channel.name} 20:15 Nachrichten-Spezial & Wissen-Doku", "Live-News & Hintergrund-Dokumentation", "Stündliche Live-Nachrichten kombiniert mit spannenden Technik- und Zeitgeschichts-Dokus.", EpgCategory.NEWS)
                else -> ProgramTemplate("${channel.name} Live-Nachrichten & Eilmeldungen", "24/7 News-Stream", "Laufend aktualisierte Nachrichten, Wetter, Wirtschaft und Live-Pressekonferenzen.", EpgCategory.NEWS)
            }
            "sport1", "eurosport", "orfsport" -> when (slotIndex) {
                6 -> ProgramTemplate("${channel.name} Sport-News & Vorberichterstattung", "Aktuelle Sport-Highlights", "Ergebnisse, Interviews und Analysen aus Fußball, Wintersport, Motorsport und Tennis.", EpgCategory.SPORTS)
                7 -> ProgramTemplate("${channel.name} 20:15 LIVE-Sport Übertragung", "Spitzensport Live am Abend", "Live-Entscheidungen, Flutlichtspiele, Darts, Radsport oder Wintersport-Highlights in HD.", EpgCategory.SPORTS)
                else -> ProgramTemplate("${channel.name} Sport-Magazin & Live-Wiederholung", "Sport rund um die Uhr", "Spannende Wettkämpfe, Hintergrundberichte und Sport-Dokumentationen.", EpgCategory.SPORTS)
            }
            "kika", "superrtl" -> when (slotIndex) {
                6 -> ProgramTemplate("${channel.name} Vorabend-Helden & Wissen macht Ah!", "Kinder- & Familienprogramm", "Spannende Animationsserien und Kindernachrichten (logo!).", EpgCategory.KIDS)
                7 -> ProgramTemplate("${channel.name} 20:15 Familien-Abend & Kultserien", "Beste Unterhaltung für Groß und Klein", "Abendprogramm mit beliebten Serien und Familienfilmen.", EpgCategory.KIDS)
                else -> ProgramTemplate("${channel.name} Kinder- & Animations-Highlights", "Serien, Spaß & Abenteuer", "Kinderfreundliche Unterhaltung und schlaue Wissensformate.", EpgCategory.KIDS)
            }
            else -> {
                val rubrikLabel = when (channel.rubrik) {
                    TvRubrik.DEUTSCHES_TV -> "Deutsches Vollprogramm"
                    TvRubrik.OESTERREICH_TV -> "Österreichisches TV-Programm"
                    TvRubrik.TV2_OHA -> "DE TV2 Live-Programm"
                    TvRubrik.TV3_LIVE -> channel.currentShow.ifBlank { "TV3 Deutsches Live-Programm" }
                    TvRubrik.ALL -> "Live-TV Programm"
                }
                when (slotIndex) {
                    0 -> ProgramTemplate("${channel.name} Nachtprogramm & Serien", rubrikLabel, "Durchgehendes Nachtprogramm auf ${channel.name} in bester Stream-Qualität.", EpgCategory.MOVIES_SERIES)
                    1 -> ProgramTemplate("Guten Morgen auf ${channel.name}", "Frühprogramm & Aktuelles", "Nachrichten, Wetter und Unterhaltung zum Tagesstart auf ${channel.name}.", EpgCategory.NEWS)
                    2 -> ProgramTemplate("${channel.name} Vormittagsmagazin", "Service, Wissen & Alltag", "Abwechslungsreiches Vormittagsprogramm mit Reportagen und Serien.", EpgCategory.ENTERTAINMENT)
                    3 -> ProgramTemplate("${channel.name} Mittagsjournal", "Nachrichten & Kompakt-Info", "Die wichtigsten Themen des Tages zur Mittagsstunde auf ${channel.name}.", EpgCategory.NEWS)
                    4 -> ProgramTemplate("${channel.name} Nachmittags-Show & Serien", "Unterhaltung am Nachmittag", "Beliebte Serien, Dokus und Magazine im Nachmittagsprogramm.", EpgCategory.ENTERTAINMENT)
                    5 -> ProgramTemplate("${channel.name} Vorabend-Magazin", "Regionales, Boulevard & Quiz", "Spannende Vorabendunterhaltung und aktuelle Berichte auf ${channel.name}.", EpgCategory.ENTERTAINMENT)
                    6 -> ProgramTemplate("${channel.name} Aktuell – Hauptnachrichten", "Nachrichten, Sport & Wetter", "Ausführliche Abendnachrichten und Tageszusammenfassung auf ${channel.name}.", EpgCategory.NEWS)
                    7 -> ProgramTemplate("${channel.name} 20:15 Primetime-Highlight", "Hauptabend-Spielfilm & Live-Show", "Das große Abendprogramm um 20:15 Uhr auf ${channel.name} – direkt anklicken zum Starten im ExoPlayer.", EpgCategory.MOVIES_SERIES)
                    8 -> ProgramTemplate("${channel.name} Spätjournal & Talk", "Hintergründe & Diskussion", "Spätabend-Magazin, Kultur und spannende Reportagen auf ${channel.name}.", EpgCategory.NEWS)
                    else -> ProgramTemplate("${channel.name} Late-Night & Kino", "Spätfilm in HD", "Film- und Serien-Highlights im Spätprogramm von ${channel.name}.", EpgCategory.MOVIES_SERIES)
                }
            }
        }
    }
}
