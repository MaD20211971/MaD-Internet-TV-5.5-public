package com.example.data.livetvlight

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.abs

data class Scraped2ix2Channel(
    val slug: String,
    val pageUrl: String,
    val name: String,
    val logoUrl: String,
    val countryCode: String
)

/**
 * Multi-Page-Scraper für die öffentlichen 2ix2.com-Kategorieseiten (DE & AT).
 * Liest alle Seiten dynamisch über WordPress-Pagination ein und extrahiert pro Sender:
 * - exakte Sender-Seiten-URL (pageUrl)
 * - bereinigten Sendernamen
 * - originales 2ix2.com-Senderlogo (/wp-content/uploads/...)
 */
object LiveTvLight2ix2Scraper {

    const val BASE_URL = "https://www.2ix2.com"
    const val DE_CATEGORY_URL = "$BASE_URL/category/deutsche-tv-sender/"
    const val AT_CATEGORY_URL = "$BASE_URL/category/oesterreichische-tv/"
    private const val MAX_PAGES_PER_CATEGORY = 6

    private const val BROWSER_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    private val ignoredSlugs = setOf(
        "category", "tag", "page", "wp-content", "wp-includes", "wp-json",
        "feed", "impressum", "datenschutz", "kontakt", "kontakt-2", "tv-programm",
        "author", "dmca", "privacy-policy", "cookie-policy", "terms", "suche",
        "search", "live-tv", "senderliste", "radio", "schweizer-tv", "schweiz-tv",
        "deutsche-tv-sender", "oesterreichische-tv", "home", "about", "hilfe",
        "faq", "disclaimer", "sitemap", "wp-login", "wp-admin", "comments"
    )

    private val brandPalette = listOf(
        Color(0xFF00519E),
        Color(0xFFDC2626),
        Color(0xFF0284C7),
        Color(0xFF059669),
        Color(0xFFD97706),
        Color(0xFF7C3AED),
        Color(0xFFE11D48),
        Color(0xFF0D9488),
        Color(0xFF2563EB),
        Color(0xFFB45309)
    )

    suspend fun scrapeAllCategories(httpClient: OkHttpClient): List<Scraped2ix2Channel> = withContext(Dispatchers.IO) {
        coroutineScope {
            val deDeferred = async(Dispatchers.IO) {
                scrapeCategoryAllPages(httpClient, DE_CATEGORY_URL, "DE")
            }
            val atDeferred = async(Dispatchers.IO) {
                scrapeCategoryAllPages(httpClient, AT_CATEGORY_URL, "AT")
            }
            val combined = LinkedHashMap<String, Scraped2ix2Channel>()
            (deDeferred.await() + atDeferred.await()).forEach { ch ->
                val existing = combined[ch.slug]
                if (existing == null || (existing.logoUrl.isBlank() && ch.logoUrl.isNotBlank())) {
                    combined[ch.slug] = ch
                }
            }
            combined.values.toList()
        }
    }

    private fun scrapeCategoryAllPages(
        httpClient: OkHttpClient,
        startUrl: String,
        countryCode: String
    ): List<Scraped2ix2Channel> {
        val discovered = LinkedHashMap<String, Scraped2ix2Channel>()
        val visitedUrls = HashSet<String>()
        var currentUrl: String? = startUrl
        var pageIndex = 1

        while (currentUrl != null && pageIndex <= MAX_PAGES_PER_CATEGORY) {
            val normalizedUrl = normalizePageUrl(currentUrl)
            if (!visitedUrls.add(normalizedUrl)) break

            val html = fetchHtml(httpClient, normalizedUrl) ?: break
            val pageChannels = parseCategoryPage(html, countryCode)
            var addedOnPage = 0
            for (ch in pageChannels) {
                val existing = discovered[ch.slug]
                if (existing == null) {
                    discovered[ch.slug] = ch
                    addedOnPage++
                } else if (existing.logoUrl.isBlank() && ch.logoUrl.isNotBlank()) {
                    discovered[ch.slug] = ch
                }
            }

            if (pageChannels.isEmpty() && pageIndex > 1) break
            val explicitNext = findNextPageUrl(html, startUrl, pageIndex)
            currentUrl = when {
                explicitNext != null -> explicitNext
                pageIndex == 1 && addedOnPage >= 12 -> "${startUrl.trimEnd('/')}/page/2/"
                else -> null
            }
            pageIndex++
        }
        return discovered.values.toList()
    }

    private fun fetchHtml(httpClient: OkHttpClient, url: String): String? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", BROWSER_UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "de-DE,de;q=0.9,en;q=0.7")
                .header("Referer", "$BASE_URL/")
                .build()
            httpClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return null
                res.body?.string()
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parst eine 2ix2.com-Kategorieseite robust über WordPress-<article>-Blöcke sowie
     * Bild-Link-Karten als Fallback und extrahiert echte pageUrl, Namen und Original-Logo.
     */
    internal fun parseCategoryPage(html: String, countryCode: String): List<Scraped2ix2Channel> {
        if (html.isBlank()) return emptyList()
        val mainContent = extractMainContentRegion(html)
        val bySlug = LinkedHashMap<String, Scraped2ix2Channel>()

        // Strategie 1: WordPress <article>...</article> Senderkarten
        val articleRegex = """<article\b[^>]*>(.*?)</article>"""
            .toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        for (match in articleRegex.findAll(mainContent)) {
            parseCardBlock(match.groupValues[1], countryCode)?.let { item ->
                mergeCandidate(bySlug, item)
            }
        }

        // Strategie 2: Direkte Karten-/Anker-Blöcke mit <img ...> (falls Theme <div>/<li> statt <article> nutzt)
        if (bySlug.size < 4) {
            val linkedImgRegex =
                """<a[^>]+href=["'](https?://(?:www\.)?2ix2\.com/([a-z0-9\-]+)/?)["'][^>]*>(.*?)</a>"""
                    .toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            for (match in linkedImgRegex.findAll(mainContent)) {
                val fullTag = match.value
                val pageUrl = normalizePageUrl(match.groupValues[1])
                val slug = match.groupValues[2].trim().lowercase()
                val inner = match.groupValues[3]
                if (!isValidChannelSlug(slug)) continue

                val logoUrl = extractBestImageUrl(inner)
                val hasImg = inner.contains("<img", ignoreCase = true)
                val rawTitle = extractAttribute(fullTag, "title")
                    .ifBlank { extractImgAlt(inner) }
                    .ifBlank { stripTags(inner) }
                    .ifBlank { prettifySlug(slug) }

                // Bei Strategie 2 nur Einträge mit Bild oder sauberem Sendernamen aufnehmen
                if (!hasImg && rawTitle.length > 42) continue
                val cleanName = cleanChannelName(rawTitle, slug)
                if (cleanName.isBlank()) continue

                mergeCandidate(
                    bySlug,
                    Scraped2ix2Channel(
                        slug = slug,
                        pageUrl = pageUrl,
                        name = cleanName,
                        logoUrl = logoUrl,
                        countryCode = countryCode
                    )
                )
            }
        }

        return bySlug.values.toList()
    }

    internal fun findNextPageUrl(html: String, categoryBaseUrl: String, currentPage: Int): String? {
        val nextPageNum = currentPage + 1
        val relNextRegex = """<link[^>]+rel=["']next["'][^>]+href=["']([^"']+)["']"""
            .toRegex(RegexOption.IGNORE_CASE)
        relNextRegex.find(html)?.groupValues?.getOrNull(1)?.let { return normalizePageUrl(it) }

        val anchorNextRegex =
            """<a[^>]+class=["'][^"']*\bnext\b[^"']*["'][^>]+href=["']([^"']+)["']"""
                .toRegex(RegexOption.IGNORE_CASE)
        anchorNextRegex.find(html)?.groupValues?.getOrNull(1)?.let { return normalizePageUrl(it) }

        val anchorNextReverseRegex =
            """<a[^>]+href=["']([^"']+)["'][^>]+class=["'][^"']*\bnext\b[^"']*["']"""
                .toRegex(RegexOption.IGNORE_CASE)
        anchorNextReverseRegex.find(html)?.groupValues?.getOrNull(1)?.let { return normalizePageUrl(it) }

        val baseNoSlash = Regex.escape(categoryBaseUrl.trimEnd('/'))
        val pageNumRegex = """href=["']($baseNoSlash/page/$nextPageNum/?)["']"""
            .toRegex(RegexOption.IGNORE_CASE)
        pageNumRegex.find(html)?.groupValues?.getOrNull(1)?.let { return normalizePageUrl(it) }

        return null
    }

    private fun extractMainContentRegion(html: String): String {
        val withoutNoise = html
            .replace(Regex("""<script\b[^>]*>.*?</script>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("""<style\b[^>]*>.*?</style>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("""<nav\b[^>]*>.*?</nav>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("""<footer\b[^>]*>.*?</footer>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("""<aside\b[^>]*>.*?</aside>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")

        val mainMatch = Regex("""<main\b[^>]*>(.*?)</main>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(withoutNoise)
        val mainBody = mainMatch?.groupValues?.getOrNull(1)
        return if (!mainBody.isNullOrBlank() && mainBody.length > 200) mainBody else withoutNoise
    }

    private fun parseCardBlock(blockHtml: String, countryCode: String): Scraped2ix2Channel? {
        val linkRegex = """<a[^>]+href=["'](https?://(?:www\.)?2ix2\.com/([a-z0-9\-]+)/?)["'][^>]*>"""
            .toRegex(RegexOption.IGNORE_CASE)
        var chosenSlug = ""
        var chosenPageUrl = ""
        var chosenLinkTitle = ""

        for (m in linkRegex.findAll(blockHtml)) {
            val slug = m.groupValues[2].trim().lowercase()
            if (!isValidChannelSlug(slug)) continue
            chosenSlug = slug
            chosenPageUrl = normalizePageUrl(m.groupValues[1])
            chosenLinkTitle = extractAttribute(m.value, "title")
            break
        }
        if (chosenSlug.isBlank()) return null

        val logoUrl = extractBestImageUrl(blockHtml)

        val headingRegex = """<h[1-4][^>]*>(.*?)</h[1-4]>"""
            .toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val headingText = headingRegex.find(blockHtml)?.groupValues?.getOrNull(1)?.let { stripTags(it) }.orEmpty()

        val rawTitle = headingText
            .ifBlank { chosenLinkTitle }
            .ifBlank { extractImgAlt(blockHtml) }
            .ifBlank { prettifySlug(chosenSlug) }

        val cleanName = cleanChannelName(rawTitle, chosenSlug)
        if (cleanName.isBlank()) return null

        return Scraped2ix2Channel(
            slug = chosenSlug,
            pageUrl = chosenPageUrl,
            name = cleanName,
            logoUrl = logoUrl,
            countryCode = countryCode
        )
    }

    private fun mergeCandidate(
        map: LinkedHashMap<String, Scraped2ix2Channel>,
        candidate: Scraped2ix2Channel
    ) {
        val existing = map[candidate.slug]
        if (existing == null) {
            map[candidate.slug] = candidate
        } else {
            val betterLogo = existing.logoUrl.ifBlank { candidate.logoUrl }
            val betterName = if (existing.name.length < 2) candidate.name else existing.name
            map[candidate.slug] = existing.copy(name = betterName, logoUrl = betterLogo)
        }
    }

    internal fun extractBestImageUrl(htmlFragment: String): String {
        val imgRegex = """<img\b[^>]*>""".toRegex(RegexOption.IGNORE_CASE)
        val candidates = ArrayList<String>()

        for (match in imgRegex.findAll(htmlFragment)) {
            val tag = match.value
            val attrsInOrder = listOf(
                extractAttribute(tag, "data-lazy-src"),
                extractAttribute(tag, "data-src"),
                extractAttribute(tag, "data-original"),
                extractAttribute(tag, "src"),
                extractFirstSrcSetUrl(extractAttribute(tag, "data-lazy-srcset")),
                extractFirstSrcSetUrl(extractAttribute(tag, "srcset"))
            )
            for (raw in attrsInOrder) {
                val resolved = resolveImageUrl(raw)
                if (resolved.isNotBlank()) {
                    candidates.add(resolved)
                }
            }
        }

        if (candidates.isEmpty()) return ""
        return candidates.firstOrNull { it.contains("/wp-content/uploads/", ignoreCase = true) }
            ?: candidates.first()
    }

    private fun extractImgAlt(htmlFragment: String): String {
        val imgMatch = """<img\b[^>]*>""".toRegex(RegexOption.IGNORE_CASE).find(htmlFragment) ?: return ""
        return extractAttribute(imgMatch.value, "alt")
    }

    private fun extractAttribute(tag: String, attrName: String): String {
        val regex = """\b$attrName\s*=\s*(?:"([^"]*)"|'([^']*)')""".toRegex(RegexOption.IGNORE_CASE)
        val match = regex.find(tag) ?: return ""
        return (match.groupValues[1].ifBlank { match.groupValues[2] }).trim()
    }

    private fun extractFirstSrcSetUrl(srcSet: String): String {
        if (srcSet.isBlank()) return ""
        return srcSet.split(",")
            .map { it.trim().substringBefore(" ").trim() }
            .firstOrNull { it.isNotBlank() && !it.startsWith("data:", ignoreCase = true) }
            .orEmpty()
    }

    private fun resolveImageUrl(raw: String): String {
        val cleaned = decodeHtmlEntities(raw).trim()
        if (cleaned.isBlank()) return ""
        if (cleaned.startsWith("data:", ignoreCase = true)) return ""
        val lower = cleaned.lowercase()
        if (lower.contains("blank.gif") || lower.contains("placeholder") || lower.contains("spacer.")) {
            return ""
        }
        return when {
            cleaned.startsWith("https://", ignoreCase = true) -> cleaned
            cleaned.startsWith("http://", ignoreCase = true) -> "https://" + cleaned.removePrefix("http://")
            cleaned.startsWith("//") -> "https:$cleaned"
            cleaned.startsWith("/") -> "$BASE_URL$cleaned"
            else -> ""
        }
    }

    private fun isValidChannelSlug(slug: String): Boolean {
        if (slug.length < 2 || slug.length > 64) return false
        if (slug in ignoredSlugs) return false
        if (slug.startsWith("wp-") || slug.startsWith("category-")) return false
        return true
    }

    private fun normalizePageUrl(url: String): String {
        val decoded = decodeHtmlEntities(url).trim()
        val withHttps = when {
            decoded.startsWith("https://", ignoreCase = true) -> decoded
            decoded.startsWith("http://", ignoreCase = true) -> "https://" + decoded.removePrefix("http://")
            decoded.startsWith("//") -> "https:$decoded"
            decoded.startsWith("/") -> "$BASE_URL$decoded"
            else -> "$BASE_URL/$decoded"
        }
        return if (withHttps.endsWith("/")) withHttps else "$withHttps/"
    }

    internal fun cleanChannelName(raw: String, fallbackSlug: String): String {
        var name = decodeHtmlEntities(raw)
            .replace(Regex("""\s+"""), " ")
            .trim()

        val noisePatterns = listOf(
            Regex("""(?i)\s*[-|–—]\s*2ix2.*$"""),
            Regex("""(?i)\s+live\s*stream.*$"""),
            Regex("""(?i)\s+livestream.*$"""),
            Regex("""(?i)\s+live\s+tv.*$"""),
            Regex("""(?i)\s+online\s+schauen.*$"""),
            Regex("""(?i)\s+kostenlos.*$"""),
            Regex("""(?i)\s+live$""")
        )
        for (pattern in noisePatterns) {
            name = pattern.replace(name, "").trim()
        }
        if (name.length < 2) {
            name = prettifySlug(fallbackSlug)
        }
        return name
    }

    private fun prettifySlug(slug: String): String {
        return slug
            .replace("-live-stream", "")
            .replace("-livestream", "")
            .replace("-live", "")
            .split("-")
            .filter { it.isNotBlank() }
            .joinToString(" ") { part ->
                if (part.length <= 3) part.uppercase()
                else part.replaceFirstChar { it.uppercase() }
            }
            .ifBlank { slug.uppercase() }
    }

    private fun stripTags(html: String): String {
        return decodeHtmlEntities(
            html.replace(Regex("""<[^>]+>"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
        )
    }

    private fun decodeHtmlEntities(input: String): String {
        return input
            .replace("&amp;", "&")
            .replace("&#038;", "&")
            .replace("&quot;", "\"")
            .replace("&#8211;", "–")
            .replace("&#8212;", "—")
            .replace("&#8217;", "'")
            .replace("&nbsp;", " ")
            .replace("&auml;", "ä")
            .replace("&ouml;", "ö")
            .replace("&uuml;", "ü")
            .replace("&Auml;", "Ä")
            .replace("&Ouml;", "Ö")
            .replace("&Uuml;", "Ü")
            .replace("&szlig;", "ß")
    }

    fun classifyCategory(name: String, countryCode: String): String {
        if (countryCode.equals("AT", ignoreCase = true)) return "Österreich"
        val lower = name.lowercase()
        return when {
            listOf("ard", "das erste", "zdf", "one").any { lower.contains(it) } -> "Öffentlich-Rechtlich"
            listOf("tagesschau", "welt", "n-tv", "ntv", "phoenix", "dw ", "deutsche welle", "bloomberg", "euronews", "n24")
                .any { lower.contains(it) && !lower.contains("welt der wunder") } -> "Nachrichten & Info"
            listOf("arte", "3sat", "doku", "wunder", "alpha", "bibel", "history", "discovery")
                .any { lower.contains(it) } -> "Doku & Kultur"
            listOf("sport", "eurosport", "sky sport", "df1", "motor")
                .any { lower.contains(it) } -> "Sport"
            listOf("kika", "disney", "nick", "toggo", "ric", "kinder")
                .any { lower.contains(it) } -> "Kinder & Familie"
            listOf("wdr", "ndr", "swr", "br ", "mdr", "hr", "rbb", "sr ", "alex", "mdf", "frank", "rhein", "berlin", "hamburg", "münchen", "muenchen", "baden", "regio")
                .any { lower.contains(it) } -> "Drittes Programm"
            else -> "Privatsender"
        }
    }

    fun generateLogoBadge(name: String): String {
        val cleaned = name
            .replace("HD", "", ignoreCase = true)
            .replace("TV", "", ignoreCase = true)
            .replace("Fernsehen", "", ignoreCase = true)
            .replace("Deutschland", "", ignoreCase = true)
            .trim()
        val words = cleaned.split(Regex("""[\s\-_.]+""")).filter { it.isNotBlank() }
        return when {
            words.isEmpty() -> name.take(4).uppercase()
            words.size == 1 -> words[0].take(5).uppercase()
            else -> words.joinToString("") { it.take(2) }.take(5).uppercase()
        }
    }

    fun pickBrandColor(seedKey: String): Color {
        return brandPalette[abs(seedKey.hashCode()) % brandPalette.size]
    }
}
