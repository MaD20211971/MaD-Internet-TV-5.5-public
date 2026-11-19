package com.example

import com.example.data.model.PlaylistType
import com.example.data.parser.PlaylistImportExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaylistImportExtractorTest {

    @Test
    fun testExtractXtreamBlock() {
        val dump = """
            Hier ist mein Xtream Testkonto:
            Host: http://premium.iptv-server.net:8080
            Username: testuser123
            Password: mysecretpass456
            Ablaufdatum: 2027-12-31
        """.trimIndent()

        val candidates = PlaylistImportExtractor.extractFromText(dump)
        assertEquals(1, candidates.size)

        val xtream = candidates[0]
        assertEquals(PlaylistType.XTREAM_CODES, xtream.type)
        assertEquals("http://premium.iptv-server.net:8080", xtream.url)
        assertEquals("testuser123", xtream.username)
        assertEquals("mysecretpass456", xtream.password)
        assertTrue(xtream.name.contains("premium.iptv-server.net"))
    }

    @Test
    fun testExtractMacPortalBlock() {
        val dump = """
            Stalker Portal:
            URL: http://mag.vip-portal.tv:88/c/
            MAC: 00:1A:79:3F:8A:1B
            Device: MAG 322
        """.trimIndent()

        val candidates = PlaylistImportExtractor.extractFromText(dump)
        assertEquals(1, candidates.size)

        val mac = candidates[0]
        assertEquals(PlaylistType.MAC_PORTAL, mac.type)
        assertEquals("http://mag.vip-portal.tv:88", mac.url)
        assertEquals("00:1A:79:3F:8A:1B", mac.macAddress)
    }

    @Test
    fun testExtractXtreamUrlWithParams() {
        val link = "http://line.streamking.org:8000/get.php?username=ottking&password=ottkingpass&type=m3u_plus&output=ts"
        val candidates = PlaylistImportExtractor.extractFromText(link)

        assertEquals(1, candidates.size)
        val xtream = candidates[0]
        assertEquals(PlaylistType.XTREAM_CODES, xtream.type)
        assertEquals("http://line.streamking.org:8000", xtream.url)
        assertEquals("ottking", xtream.username)
        assertEquals("ottkingpass", xtream.password)
    }

    @Test
    fun testExtractDirectM3uUrl() {
        val text = "Hier der freie Stream-Link: https://iptv-org.github.io/iptv/countries/de.m3u"
        val candidates = PlaylistImportExtractor.extractFromText(text)

        assertEquals(1, candidates.size)
        val m3u = candidates[0]
        assertEquals(PlaylistType.M3U_URL, m3u.type)
        assertEquals("https://iptv-org.github.io/iptv/countries/de.m3u", m3u.url)
    }

    @Test
    fun testExtractMixedMultiAccountTextDump() {
        val textDump = """
            =========================
            IPTV SAMMLUNG TEST
            =========================
            
            1) Xtream Server Deutschland
            URL: http://de-tv.server.to:8080/
            User: user_germany
            Pass: pass_germany123
            
            2) MAC Portal
            Portal: http://portal.stalkertv.org:80/c/
            Mac: 00:1a:79:cd:ef:12
            
            3) Inline Pipe Xtream
            http://fast.stream.cx:9000 | speeduser | speedpass99
            
            4) M3U Web Playlist
            https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8
        """.trimIndent()

        val candidates = PlaylistImportExtractor.extractFromText(textDump)
        assertEquals(4, candidates.size)

        val types = candidates.map { it.type }
        assertTrue(types.contains(PlaylistType.XTREAM_CODES))
        assertTrue(types.contains(PlaylistType.MAC_PORTAL))
        assertTrue(types.contains(PlaylistType.M3U_URL))
    }

    @Test
    fun testExtractOnAir8kMacPortal() {
        val userText = """
            importiere dieses MAC Portal

            http://vpn.onair8k.xyz:80/c/
            00:1A:79:B6:C4:82

            und teste bis es laeuft
        """.trimIndent()

        val candidates = PlaylistImportExtractor.extractFromText(userText)
        assertEquals(1, candidates.size)

        val portal = candidates[0]
        assertEquals(PlaylistType.MAC_PORTAL, portal.type)
        assertEquals("http://vpn.onair8k.xyz:80", portal.url)
        assertEquals("00:1A:79:B6:C4:82", portal.macAddress)
    }
}
