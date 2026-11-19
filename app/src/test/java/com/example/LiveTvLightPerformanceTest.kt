package com.example

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.example.data.livetvlight.LiveTvLight2ix2Scraper
import com.example.data.livetvlight.LiveTvLightCatalog
import com.example.data.livetvlight.LiveTvLightCatalogStore
import com.example.data.livetvlight.LiveTvLightStreamResolver
import com.example.data.livetvlight.Scraped2ix2Channel
import com.example.data.player.PlayerManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LiveTvLightPerformanceTest {

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        LiveTvLightCatalogStore.clearCache(ctx)
        LiveTvLightCatalog.resetToSeedForTesting()
        LiveTvLightStreamResolver.clearCache()
    }

    @Test
    fun testCatalogHasExact47GermanAnd11AustrianChannelsWithGeoFreePriority() {
        assertEquals(47, LiveTvLightCatalog.germanChannels.size)
        assertEquals(11, LiveTvLightCatalog.austrianChannels.size)
        assertEquals(58, LiveTvLightCatalog.allChannels.size)

        val uniqueIds = LiveTvLightCatalog.allChannels.map { it.id }.toSet()
        assertEquals(58, uniqueIds.size)

        assertTrue(LiveTvLightCatalog.germanChannels.all { it.countryCode == "DE" })
        assertTrue(LiveTvLightCatalog.austrianChannels.all { it.countryCode == "AT" })
        assertTrue(LiveTvLightCatalog.allChannels.none { it.supportsInstantStart })

        val criticalIds = listOf("das_erste", "zdf", "mdr", "wdr", "swr", "orf1", "orf2", "servustv_at")
        criticalIds.forEach { id ->
            val ch = LiveTvLightCatalog.findById(id)
            assertNotNull("Missing channel $id", ch)
            assertTrue("Expected effective 2ix2 pageUrl for $id", ch!!.effectivePageUrl.startsWith("https://www.2ix2.com/"))
            assertTrue("Expected fallback URLs for $id", ch.fallbackStreamUrls.isNotEmpty())
            assertTrue("Expected alternate slugs for $id", ch.alternateResolverKeys.isNotEmpty())
        }

        val sport1 = LiveTvLightCatalog.findById("sport1")!!
        assertFalse(sport1.primaryStreamUrl.contains("/rtl/", ignoreCase = true))
        assertTrue(sport1.primaryStreamUrl.contains("sport1", ignoreCase = true))
    }

    @Test
    fun testExtM3uPayloadVerificationRejectsHtmlAndGeoblock() {
        assertTrue(
            LiveTvLightStreamResolver.isValidM3u8Payload(
                "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-STREAM-INF:BANDWIDTH=2500000\nchunklist.m3u8"
            )
        )
        assertTrue(
            LiveTvLightStreamResolver.isValidM3u8Payload("\uFEFF#EXTM3U\n#EXT-X-TARGETDURATION:6")
        )
        assertFalse(LiveTvLightStreamResolver.isValidM3u8Payload(""))
        assertFalse(LiveTvLightStreamResolver.isValidM3u8Payload("<!DOCTYPE html><html><body>403 Forbidden</body></html>"))
        assertFalse(LiveTvLightStreamResolver.isValidM3u8Payload("<html><body>Not available in your country</body></html>"))
        assertFalse(LiveTvLightStreamResolver.isValidM3u8Payload("{\"error\":\"geoblock\"}"))
    }

    @Test
    fun testDynamicHeadersIncludingCookiePropagatedToPlayerManagerForStreamAndSegments() {
        val masterUrl = "https://cdn-edge99.example-stream.tv/live/ard/index.m3u8?token=abc"
        val segmentUrl = "https://cdn-edge99.example-stream.tv/live/ard/seg001.ts"

        LiveTvLightStreamResolver.registerDynamicHeaders(
            streamUrl = masterUrl,
            referer = "https://www.2ix2.com/ard-de/",
            origin = "https://www.2ix2.com",
            cookie = "cf_clearance=xyz123; session=ok",
            isRelay = true
        )

        val masterHeaders = PlayerManager.resolveHeaders(masterUrl)
        assertEquals("https://www.2ix2.com/ard-de/", masterHeaders["Referer"])
        assertEquals("https://www.2ix2.com", masterHeaders["Origin"])
        assertEquals("cf_clearance=xyz123; session=ok", masterHeaders["Cookie"])

        val segHeaders = PlayerManager.resolveHeaders(segmentUrl)
        assertEquals("https://www.2ix2.com/ard-de/", segHeaders["Referer"])
        assertEquals("https://www.2ix2.com", segHeaders["Origin"])
        assertEquals("cf_clearance=xyz123; session=ok", segHeaders["Cookie"])
        assertTrue(LiveTvLightStreamResolver.isResolvedRelayUrl(segmentUrl))
    }

    @Test
    fun testCacheFastPathRequiresVerifiedCacheEntry() {
        val ard = LiveTvLightCatalog.findById("das_erste")!!
        assertNull(LiveTvLightStreamResolver.getInstantStartUrl(ard))
        assertFalse(LiveTvLightStreamResolver.isAlreadyPlayableWithoutResolve(ard, ard.primaryStreamUrl))
    }

    @Test
    fun testParallelFastestWinsRacingDoesNotWaitForSlowCandidates() = runBlocking {
        val startMs = System.currentTimeMillis()
        val winner = LiveTvLightStreamResolver.raceFastestReachableStream(
            candidateUrls = listOf(
                "https://slow.example/1.m3u8",
                "https://fast.example/ok.m3u8",
                "https://very-slow.example/2.m3u8"
            ),
            resolverPageUrl = null,
            probeFn = { url ->
                when (url) {
                    "https://fast.example/ok.m3u8" -> {
                        Thread.sleep(40)
                        true
                    }
                    else -> {
                        Thread.sleep(1800)
                        false
                    }
                }
            }
        )
        val elapsedMs = System.currentTimeMillis() - startMs

        assertEquals("https://fast.example/ok.m3u8", winner)
        assertTrue("Expected parallel race < 900ms, took ${elapsedMs}ms", elapsedMs < 900L)
    }

    @Test
    fun testAssetAndAdBlockerAllowsM3u8AndBlocksHeavyAssets() {
        assertFalse(LiveTvLightStreamResolver.isBlockedAssetOrAdUrl("https://s6.hopslan.com/rtl/tracks-v1a1/mono.ts.m3u8"))
        assertFalse(LiveTvLightStreamResolver.isBlockedAssetOrAdUrl("https://www.2ix2.com/rtl-live/"))
        assertTrue(LiveTvLightStreamResolver.isBlockedAssetOrAdUrl("https://www.2ix2.com/wp-content/uploads/logo.png"))
        assertTrue(LiveTvLightStreamResolver.isBlockedAssetOrAdUrl("https://www.2ix2.com/style.css?ver=1.2"))
        assertTrue(LiveTvLightStreamResolver.isBlockedAssetOrAdUrl("https://cdn.example.com/font.woff2"))
        assertTrue(LiveTvLightStreamResolver.isBlockedAssetOrAdUrl("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"))
    }

    @Test
    fun testDirectorySlugParserAndFallbackAfterError() = runBlocking {
        val sampleHtml = """
            <div class="post"><a href="https://www.2ix2.com/orf1/">ORF 1 Live</a></div>
            <div class="post"><a href="https://www.2ix2.com/mdr-live/">MDR Fernsehen</a></div>
            <div class="post"><a href="https://www.2ix2.com/category/deutsche-tv-sender/">Deutsche TV</a></div>
            <div class="post"><a href="https://www.2ix2.com/rtlup-live/">RTLup</a></div>
        """.trimIndent()

        val parsed = LiveTvLightStreamResolver.parseDirectorySlugs(sampleHtml)
        assertEquals("orf1", parsed["orf1"])
        assertEquals("mdr-live", parsed["mdr"])
        assertEquals("rtlup-live", parsed["rtlup"])
        assertFalse(parsed.containsKey("category"))

        LiveTvLightCatalog.applySlugOverrides(parsed)
        assertEquals(58, LiveTvLightCatalog.allChannels.size)
        assertEquals("mdr-live", LiveTvLightCatalog.effectiveResolverKey(LiveTvLightCatalog.findById("mdr")!!))

        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val ard = LiveTvLightCatalog.findById("das_erste")!!
        val failedSet = setOf(ard.primaryStreamUrl)
        val fallbackUrl = LiveTvLightStreamResolver.resolveFallbackAfterError(
            context = ctx,
            channelId = "ltv_light_das_erste",
            failedUrls = failedSet,
            probeFn = { url -> url.contains("/int/master.m3u8") },
            extractFn = { null }
        )

        assertEquals("https://daserste-live.ard-mcdn.de/daserste/live/hls/int/master.m3u8", fallbackUrl)
        assertFalse(fallbackUrl in failedSet)
    }

    @Test
    fun test2ix2ScraperExtractsCardsLogosAndPagination() {
        val sampleArchiveHtml = """
            <html><body>
              <nav><a href="https://www.2ix2.com/impressum/">Impressum</a></nav>
              <main>
                <article class="post">
                  <a href="https://www.2ix2.com/ard-live-stream/" title="ARD Live Stream">
                    <img src="data:image/gif;base64,R0lGODlhAQABAAAAACw=" data-lazy-src="https://www.2ix2.com/wp-content/uploads/2023/ard-logo.png" alt="ARD" />
                  </a>
                  <h2 class="entry-title"><a href="https://www.2ix2.com/ard-live-stream/">Das Erste ARD Live Stream</a></h2>
                </article>
                <article class="post">
                  <a href="https://www.2ix2.com/mdr-tv-live/">
                    <img src="/wp-content/uploads/2023/mdr-logo.png" alt="MDR Fernsehen" />
                  </a>
                  <h3>MDR Fernsehen Live</h3>
                </article>
                <article class="post">
                  <a href="https://www.2ix2.com/laendle-tv/">
                    <img data-src="https://www.2ix2.com/wp-content/uploads/2023/laendle-tv.png" alt="L&auml;ndle TV" />
                  </a>
                  <h2>L&auml;ndle TV Online Schauen</h2>
                </article>
                <a class="next page-numbers" href="https://www.2ix2.com/category/deutsche-tv-sender/page/2/">Weiter</a>
              </main>
            </body></html>
        """.trimIndent()

        val parsed = LiveTvLight2ix2Scraper.parseCategoryPage(sampleArchiveHtml, "DE")
        assertEquals(3, parsed.size)

        val ard = parsed[0]
        assertEquals("ard-live-stream", ard.slug)
        assertEquals("https://www.2ix2.com/ard-live-stream/", ard.pageUrl)
        assertEquals("https://www.2ix2.com/wp-content/uploads/2023/ard-logo.png", ard.logoUrl)
        assertEquals("Das Erste ARD", ard.name)

        val mdr = parsed[1]
        assertEquals("mdr-tv-live", mdr.slug)
        assertEquals("https://www.2ix2.com/wp-content/uploads/2023/mdr-logo.png", mdr.logoUrl)
        assertEquals("MDR Fernsehen", mdr.name)

        val laendle = parsed[2]
        assertEquals("Ländle TV", laendle.name)
        assertEquals("https://www.2ix2.com/wp-content/uploads/2023/laendle-tv.png", laendle.logoUrl)

        val nextPage = LiveTvLight2ix2Scraper.findNextPageUrl(
            sampleArchiveHtml,
            LiveTvLight2ix2Scraper.DE_CATEGORY_URL,
            currentPage = 1
        )
        assertEquals("https://www.2ix2.com/category/deutsche-tv-sender/page/2/", nextPage)
    }

    @Test
    fun testApplyScrapedCatalogMergesLogosPageUrlsAndPersistsToJsonCache() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val originalMdrLogo = LiveTvLightCatalog.findById("mdr")!!.logoUrl

        val scraped = listOf(
            Scraped2ix2Channel(
                slug = "mdr-fernsehen-live",
                pageUrl = "https://www.2ix2.com/mdr-fernsehen-live/",
                name = "MDR Fernsehen",
                logoUrl = "https://www.2ix2.com/wp-content/uploads/mdr-official.png",
                countryCode = "DE"
            ),
            Scraped2ix2Channel(
                slug = "oe24-tv",
                pageUrl = "https://www.2ix2.com/oe24-tv/",
                name = "oe24.TV",
                logoUrl = "https://www.2ix2.com/wp-content/uploads/oe24-tv.png",
                countryCode = "AT"
            )
        )

        val mergeResult = LiveTvLightCatalog.applyScrapedCatalog(scraped, ctx)
        assertEquals(59, mergeResult.totalChannelsCount)
        assertEquals(1, mergeResult.matchedWithLive2ix2Count)
        assertEquals(1, mergeResult.addedNewChannelsCount)
        assertEquals(2, mergeResult.logosUpdatedCount)

        val updatedMdr = LiveTvLightCatalog.findById("mdr")!!
        assertEquals("https://www.2ix2.com/mdr-fernsehen-live/", updatedMdr.pageUrl)
        assertEquals("https://www.2ix2.com/wp-content/uploads/mdr-official.png", updatedMdr.logoUrl)
        assertEquals(originalMdrLogo, updatedMdr.fallbackLogoUrl)
        assertEquals("https://www.2ix2.com/mdr-fernsehen-live/", LiveTvLightCatalog.effectivePageUrls(updatedMdr).first())

        val newAt = LiveTvLightCatalog.findById("2ix2_oe24_tv")
        assertNotNull(newAt)
        assertEquals("AT", newAt!!.countryCode)
        assertEquals("Österreich", newAt.category)
        assertEquals("https://www.2ix2.com/wp-content/uploads/oe24-tv.png", newAt.logoUrl)

        // Simuliere App-Neustart: Katalog auf Seed zurücksetzen und aus JSON-Cache laden (0 ms)
        LiveTvLightCatalog.resetToSeedForTesting()
        assertEquals(58, LiveTvLightCatalog.allChannels.size)

        val loadedFromCache = LiveTvLightCatalog.initializeFromCache(ctx)
        assertTrue(loadedFromCache)
        assertEquals(59, LiveTvLightCatalog.allChannels.size)
        assertEquals(
            "https://www.2ix2.com/wp-content/uploads/mdr-official.png",
            LiveTvLightCatalog.findById("mdr")!!.logoUrl
        )
        assertEquals(
            "https://www.2ix2.com/oe24-tv/",
            LiveTvLightCatalog.findById("2ix2_oe24_tv")!!.pageUrl
        )
    }

    @Test
    fun testFindAllM3u8InHtmlExtractsEscapedAndBase64Candidates() {
        val secretUrl = "https://edge2.streamhost.example/live/mdr/master.m3u8?token=999"
        val encoded = Base64.encodeToString(secretUrl.toByteArray(), Base64.NO_WRAP)
        val html = """
            <script>
              var primary = "https:\/\/s6.hopslan.com\/mdrX\/index.m3u8?v=1&amp;a=2";
              var backup = atob("$encoded");
            </script>
        """.trimIndent()

        val found = LiveTvLightStreamResolver.findAllM3u8InHtml(html)
        assertEquals(2, found.size)
        assertEquals("https://s6.hopslan.com/mdrX/index.m3u8?v=1&a=2", found[0])
        assertEquals(secretUrl, found[1])
    }
}
