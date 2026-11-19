package com.example

import com.example.data.model.ChannelItem
import com.example.data.model.ContentType
import com.example.data.model.EpgProgram
import com.example.data.parser.EpgChannelInfo
import com.example.data.parser.EpgMappingMethod
import com.example.data.parser.EpgMappingStatus
import com.example.data.parser.EpgMatcher
import com.example.data.parser.FreeEpgService
import com.example.data.parser.XmltvParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpgOptimizationTest {

    // 1. Exakte ID-Zuordnung
    @Test
    fun testExactIdMapping() {
        val channel = ChannelItem(
            playlistId = "p1",
            name = "Das Erste HD",
            streamUrl = "http://stream/1",
            tvgId = "daserste.de"
        )
        val available = listOf(
            EpgChannelInfo("daserste.de", "Das Erste"),
            EpgChannelInfo("zdf.de", "ZDF")
        )

        val result = EpgMatcher.matchChannel(channel, available)
        assertEquals(EpgMappingStatus.MATCHED, result.status)
        assertEquals(EpgMappingMethod.EXACT_TVG_ID, result.method)
        assertEquals("daserste.de", result.matchedTvgId)
        assertEquals(100, result.confidence)
    }

    // 2. Alias- und Namenszuordnung
    @Test
    fun testAliasAndNameMapping() {
        // Known Master Database alias
        val channelWithAlias = ChannelItem(
            playlistId = "p1",
            name = "ARD Das Erste",
            streamUrl = "http://stream/2",
            tvgId = null
        )
        val available = listOf(
            EpgChannelInfo("daserste.de", "Das Erste", "DE"),
            EpgChannelInfo("zdf.de", "ZDF", "DE")
        )

        val resultAlias = EpgMatcher.matchChannel(channelWithAlias, available)
        assertEquals(EpgMappingStatus.MATCHED, resultAlias.status)
        assertTrue(resultAlias.method in listOf(EpgMappingMethod.ALIAS_MATCH, EpgMappingMethod.NORMALIZED_NAME))
        assertEquals("daserste.de", resultAlias.matchedTvgId)

        // Normalized name without exact alias
        val channelCustom = ChannelItem(
            playlistId = "p1",
            name = "ServusTV",
            streamUrl = "http://stream/3",
            tvgId = null,
            countryCode = "DE"
        )
        val availableCustom = listOf(
            EpgChannelInfo("servustv.de", "Servus TV", "DE")
        )
        val resultName = EpgMatcher.matchChannel(channelCustom, availableCustom)
        assertEquals(EpgMappingStatus.MATCHED, resultName.status)
        assertEquals("servustv.de", resultName.matchedTvgId)
    }

    // 3. HD-/UHD-Zusätze und ähnliche Sendernamen
    @Test
    fun testQualityAdditionsDoNotCollapseDistinctChannels() {
        val channel1 = ChannelItem(playlistId = "p1", name = "Sky Sport 1 HD", streamUrl = "http://1")
        val channel2 = ChannelItem(playlistId = "p1", name = "Sky Sport 2 UHD", streamUrl = "http://2")

        val available = listOf(
            EpgChannelInfo("skysport1.de", "Sky Sport 1"),
            EpgChannelInfo("skysport2.de", "Sky Sport 2")
        )

        val res1 = EpgMatcher.matchChannel(channel1, available)
        val res2 = EpgMatcher.matchChannel(channel2, available)

        assertEquals("skysport1.de", res1.matchedTvgId)
        assertEquals("skysport2.de", res2.matchedTvgId)
        assertTrue("Channel 1 and 2 must have distinct EPG mappings", res1.matchedTvgId != res2.matchedTvgId)
    }

    // 4. Gleichnamige Sender in verschiedenen Ländern
    @Test
    fun testSameNameInDifferentCountries() {
        val channelDe = ChannelItem(playlistId = "p1", name = "RTL Television HD", streamUrl = "http://de", countryCode = "DE")
        val channelNl = ChannelItem(playlistId = "p1", name = "RTL 4", streamUrl = "http://nl", countryCode = "NL")

        val available = listOf(
            EpgChannelInfo("rtl.de", "RTL", "DE"),
            EpgChannelInfo("rtl4.nl", "RTL 4", "NL")
        )

        val resDe = EpgMatcher.matchChannel(channelDe, available)
        val resNl = EpgMatcher.matchChannel(channelNl, available)

        assertEquals("rtl.de", resDe.matchedTvgId)
        assertEquals("rtl4.nl", resNl.matchedTvgId)
    }

    // 5. Mehrdeutige und nicht zugeordnete Sender
    @Test
    fun testAmbiguousAndUnassignedChannels() {
        // Channel matches multiple EPG channels and country is ambiguous
        val ambiguousChannel = ChannelItem(
            playlistId = "p1",
            name = "Eurosport",
            streamUrl = "http://euro",
            countryCode = "INT"
        )
        val availableAmbiguous = listOf(
            EpgChannelInfo("eurosport1.de", "Eurosport 1", "DE"),
            EpgChannelInfo("eurosport2.de", "Eurosport 2", "DE")
        )

        val ambigResult = EpgMatcher.matchChannel(ambiguousChannel, availableAmbiguous)
        // If multiple distinct channels match without exact specifier, should be AMBIGUOUS or UNASSIGNED
        assertTrue(ambigResult.status == EpgMappingStatus.AMBIGUOUS || ambigResult.status == EpgMappingStatus.UNASSIGNED)

        // Completely unknown channel
        val unknownChannel = ChannelItem(
            playlistId = "p1",
            name = "NonExistentChannelXYZ123",
            streamUrl = "http://xyz"
        )
        val unassignedResult = EpgMatcher.matchChannel(unknownChannel, availableAmbiguous)
        assertEquals(EpgMappingStatus.UNASSIGNED, unassignedResult.status)
        assertEquals(EpgMappingMethod.NONE, unassignedResult.method)
        assertTrue(unassignedResult.reason.isNotBlank())
    }

    // 6. Korrekte Bestimmung von „Jetzt läuft“ und „Als Nächstes“
    @Test
    fun testCurrentAndNextProgramDetermination() {
        val now = 1000000L

        val pPast = EpgProgram(
            id = "1", channelTvgId = "ch1", title = "Alte Show",
            startTime = now - 5000L, endTime = now - 1000L
        )
        val pCurrent = EpgProgram(
            id = "2", channelTvgId = "ch1", title = "Jetzt läuft Show",
            startTime = now - 1000L, endTime = now + 2000L
        )
        val pNext = EpgProgram(
            id = "3", channelTvgId = "ch1", title = "Nächste Show",
            startTime = now + 2000L, endTime = now + 5000L
        )
        val pFuture = EpgProgram(
            id = "4", channelTvgId = "ch1", title = "Spätere Show",
            startTime = now + 5000L, endTime = now + 8000L
        )

        val list = listOf(pPast, pCurrent, pNext, pFuture)
        val (cur, next) = FreeEpgService.findCurrentAndNextProgram(list, now)

        assertNotNull("Current program must not be null", cur)
        assertEquals("Jetzt läuft Show", cur?.title)
        assertTrue("Current program must satisfy startTime <= now < endTime", cur!!.startTime <= now && now < cur.endTime)

        assertNotNull("Next program must not be null", next)
        assertEquals("Nächste Show", next?.title)
        assertTrue("Next program must satisfy startTime > now", next!!.startTime > now)
    }

    // 7. Zeitzonen und Sommerzeit
    @Test
    fun testTimezonesAndDaylightSavingTime() {
        // Berlin in Summer (+0200 CEST)
        val summerDate = "20260715201500 +0200"
        val summerMs = XmltvParser.parseXmltvDate(summerDate)
        assertNotNull(summerMs)

        val formattedSummer = FreeEpgService.formatProgramTime(summerMs!!, "Europe/Berlin")
        assertEquals("20:15", formattedSummer)

        // UTC Date with Z
        val utcDate = "2026-07-15T18:15:00Z"
        val utcMs = XmltvParser.parseXmltvDate(utcDate)
        assertNotNull(utcMs)
        assertEquals(summerMs, utcMs) // 18:15 UTC is exactly 20:15 CEST (+2h)
    }

    // 8. Überlappende, doppelte und ungültige EPG-Einträge
    @Test
    fun testOverlappingDuplicateAndInvalidEntries() {
        val now = 2000000L

        // Invalid: stop before start
        val pInvalid = EpgProgram(id = "inv", channelTvgId = "c", title = "Fehler", startTime = 2500L, endTime = 2000L)
        // Duplicates
        val pDup1 = EpgProgram(id = "d1", channelTvgId = "c", title = "Show A", startTime = now - 500L, endTime = now + 1000L)
        val pDup2 = EpgProgram(id = "d2", channelTvgId = "c", title = "Show A", startTime = now - 500L, endTime = now + 1000L)
        // Overlap: more specific show
        val pOverlap = EpgProgram(id = "ov", channelTvgId = "c", title = "Spezial Show", startTime = now - 200L, endTime = now + 800L)
        // Valid next
        val pNext = EpgProgram(id = "nxt", channelTvgId = "c", title = "Show B", startTime = now + 1000L, endTime = now + 3000L)

        val list = listOf(pInvalid, pDup1, pDup2, pOverlap, pNext)
        val (cur, next) = FreeEpgService.findCurrentAndNextProgram(list, now)

        assertNotNull(cur)
        // Should resolve to the overlapping or deduplicated valid current program
        assertTrue(cur?.title in listOf("Show A", "Spezial Show"))
        assertEquals("Show B", next?.title)
    }

    // 9. Manuelle Zuordnung bleibt nach Import erhalten
    @Test
    fun testManualMappingPreservation() {
        val manualChannel = ChannelItem(
            playlistId = "p1",
            name = "Unbekannter Sender",
            streamUrl = "http://test",
            tvgId = "random_unmatched_id",
            manualEpgTvgId = "daserste.de",
            isManualEpgOverride = true
        )

        val available = listOf(
            EpgChannelInfo("daserste.de", "Das Erste"),
            EpgChannelInfo("zdf.de", "ZDF")
        )

        val result = EpgMatcher.matchChannel(manualChannel, available)
        assertEquals(EpgMappingStatus.MANUAL, result.status)
        assertEquals(EpgMappingMethod.MANUAL, result.method)
        assertEquals("daserste.de", result.matchedTvgId)
        assertEquals(100, result.confidence)
        assertTrue(result.reason.contains("Manuell"))
    }

    // 10. Große EPG-Datenmengen & Robustheit
    @Test
    fun testLargeEpgDataPerformanceAndRobustness() {
        val available = (1..500).map { i ->
            EpgChannelInfo("channel_$i.de", "Sender $i HD", "DE")
        }

        val testChannels = (1..100).map { i ->
            ChannelItem(
                playlistId = "p1",
                name = "DE: Sender $i HD (1080p)",
                streamUrl = "http://stream/$i",
                countryCode = "DE"
            )
        }

        val startTime = System.currentTimeMillis()
        var matchedCount = 0
        for (ch in testChannels) {
            val res = EpgMatcher.matchChannel(ch, available)
            if (res.status == EpgMappingStatus.MATCHED) {
                matchedCount++
            }
        }
        val duration = System.currentTimeMillis() - startTime

        assertEquals(100, matchedCount)
        assertTrue("Matching 100 channels against 500 EPG feeds should take < 2500ms, took $duration ms", duration < 2500)
    }
}
