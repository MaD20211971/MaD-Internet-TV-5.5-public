package com.example

import com.example.data.parser.FreeEpgService
import com.example.data.parser.XmltvParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpgParsingAndMatchingTest {

    @Test
    fun testXmltvDateParsingWithTimezones() {
        val dateWithOffset = "20260901201500 +0200"
        val parsedMs = XmltvParser.parseXmltvDate(dateWithOffset)
        assertNotNull("Expected timestamp to be parsed", parsedMs)

        // Verify formatting with Berlin timezone (UTC+2 in summer)
        val formattedBerlin = FreeEpgService.formatProgramTime(parsedMs!!, "Europe/Berlin")
        assertEquals("20:15", formattedBerlin)
    }

    @Test
    fun testXmltvParserExtractsChannelsAndProgrammes() {
        val xmlSample = """
            <?xml version="1.0" encoding="utf-8" ?>
            <tv generator-info-name="EPG Generator">
                <channel id="DasErste.de">
                    <display-name>Das Erste HD</display-name>
                    <icon src="https://example.com/daserste.png"/>
                </channel>
                <channel id="ZDF.de">
                    <display-name>ZDF HD</display-name>
                </channel>
                <programme start="20260901201500 +0200" stop="20260901214500 +0200" channel="DasErste.de">
                    <title lang="de">Tatort: Schatten der Nacht</title>
                    <desc lang="de">Spannender Fall für die Ermittler.</desc>
                    <category lang="de">Krimi</category>
                </programme>
                <programme start="20260901201500 +0200" stop="20260901214500 +0200" channel="ZDF.de">
                    <title lang="de">Der Bergdoktor</title>
                    <desc lang="de">Dr. Gruber im Einsatz.</desc>
                    <category lang="de">Serie</category>
                </programme>
            </tv>
        """.trimIndent()

        val (channels, programs) = XmltvParser.parse(
            inputStream = ByteArrayInputStream(xmlSample.toByteArray(Charsets.UTF_8)),
            isGzip = false,
            sourceUrl = "http://test.com/epg.xml"
        )

        assertEquals(2, channels.size)
        assertEquals(2, programs.size)
        assertEquals("DasErste.de", channels[0].id)
        assertEquals("Das Erste HD", channels[0].displayName)
        assertEquals("Tatort: Schatten der Nacht", programs[0].title)
        assertEquals("Krimi", programs[0].category)
    }

    @Test
    fun testChannelNormalizationAndMatching() {
        val verifiedChannels = listOf(
            "DE: DAS ERSTE HD" to "daserste.de",
            "|DE| ZDF FHD (1080p)" to "zdf.de",
            "RTL UHD [RAW]" to "rtl.de",
            "DE: 3SAT HD" to "3sat.de",
            "ORF 1 HD" to "orf1.at",
            "ORF 2 HD" to "orf2.at",
            "CH: 3+ HD" to "3plus.ch"
        )

        for ((rawName, expectedKey) in verifiedChannels) {
            val matchedKey = FreeEpgService.matchKey(null, rawName)
            assertEquals("Expected $rawName to match $expectedKey", expectedKey, matchedKey)
        }

        // Distinct sub-channels must NEVER collapse to the parent main channel
        val p7MaxxKey = FreeEpgService.matchKey(null, "PROSIEBEN MAXX HD")
        assertTrue("ProSieben MAXX must not collapse to prosieben.de", p7MaxxKey != "prosieben.de")

        val sport2Key = FreeEpgService.matchKey(null, "Sky Sport 2 HD")
        assertTrue("Sky Sport 2 must not collapse to Sky Sport 1", !sport2Key.contains("sport 1") && !sport2Key.contains("sport1"))
    }
}
