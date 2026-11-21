package com.example

import com.example.cast.ManualCastDeviceStore
import com.example.cast.model.CastMediaItem
import com.example.cast.model.CastProtocol
import com.example.cast.protocol.AirPlayCastController
import com.example.cast.protocol.DlnaCastController
import com.example.cast.protocol.KodiCastController
import com.example.cast.protocol.LocalCastProxyAndWebServer
import com.example.cast.protocol.RokuCastController
import com.example.cast.ui.QrCodeGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UniversalCastEngineTest {

    @Test
    fun hlsPlaylistRewriting_rewritesRelativeAndAbsoluteUrlsAndKeys() {
        val m3u8 = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-KEY:METHOD=AES-128,URI="keys/stream.key"
            #EXTINF:6.0,
            segment_001.ts
            #EXTINF:6.0,
            https://cdn.example.org/live/segment_002.ts
        """.trimIndent()

        val rewritten = LocalCastProxyAndWebServer.rewriteHlsPlaylist(
            playlistText = m3u8,
            sourcePlaylistUrl = "https://cdn.example.org/live/master.m3u8",
            proxyBaseUrl = "http://192.168.178.20:8099"
        )

        val lines = rewritten.lines().filter { it.isNotBlank() }
        assertTrue(lines.any { it.contains("http://192.168.178.20:8099/proxy?u=") })

        val seg1Line = lines.first { it.startsWith("http://192.168.178.20:8099/proxy?u=") }
        val decodedSeg1 = LocalCastProxyAndWebServer.decodeUrlBase64(seg1Line.substringAfter("u="))
        assertEquals("https://cdn.example.org/live/segment_001.ts", decodedSeg1)

        val keyLine = lines.first { it.startsWith("#EXT-X-KEY") }
        val keyEncoded = Regex("""URI="http://192\.168\.178\.20:8099/proxy\?u=([^"]+)"""").find(keyLine)?.groupValues?.get(1)
        assertNotNull(keyEncoded)
        assertEquals("https://cdn.example.org/live/keys/stream.key", LocalCastProxyAndWebServer.decodeUrlBase64(keyEncoded!!))
    }

    @Test
    fun dlnaDeviceXmlParsing_extractsFriendlyNameAndResolvedControlUrls() {
        val sampleXml = """
            <?xml version="1.0"?>
            <root xmlns="urn:schemas-upnp-org:device-1-0">
              <device>
                <friendlyName>Wohnzimmer OLED TV</friendlyName>
                <modelName>LG OLED C4</modelName>
                <UDN>uuid:1234-5678-90ab</UDN>
                <serviceList>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                    <controlURL>/upnp/control/AVTransport1</controlURL>
                  </service>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
                    <controlURL>/upnp/control/RenderingControl1</controlURL>
                  </service>
                </serviceList>
              </device>
            </root>
        """.trimIndent()

        val device = DlnaCastController.parseDeviceDescriptionXml(
            locationUrl = "http://192.168.178.55:1400/dmr.xml",
            xml = sampleXml
        )

        assertNotNull(device)
        assertEquals("Wohnzimmer OLED TV", device!!.name)
        assertEquals("LG OLED C4", device.modelName)
        assertEquals(CastProtocol.DLNA, device.protocol)
        assertEquals("http://192.168.178.55:1400/upnp/control/AVTransport1", device.controlUrl)
        assertEquals("http://192.168.178.55:1400/upnp/control/RenderingControl1", device.renderingControlUrl)
    }

    @Test
    fun dlnaDidlLiteMetadata_escapesSpecialCharacters() {
        val media = CastMediaItem(
            streamUrl = "http://192.168.1.5:8099/stream.m3u8?a=1&b=2",
            title = "ARD & ZDF <HD>",
            isLive = true
        )
        val didl = DlnaCastController.buildDidlLiteMetadata(media.streamUrl, media)
        assertTrue(didl.contains("ARD &amp; ZDF &lt;HD&gt;"))
        assertTrue(didl.contains("a=1&amp;b=2"))
    }

    @Test
    fun rokuPlayOnRokuEndpoint_setsCorrectFormatAndQuery() {
        val media = CastMediaItem(
            streamUrl = "http://192.168.1.10:8099/stream.m3u8",
            title = "Das Erste HD",
            isLive = true
        )
        val endpoint = RokuCastController.buildPlayOnRokuEndpoint("192.168.1.44", 8060, media.streamUrl, media)
        assertTrue(endpoint.startsWith("http://192.168.1.44:8060/input/15985?t=v&"))
        assertTrue(endpoint.contains("videoFormat=hls"))
    }

    @Test
    fun kodiJsonRpcAndAirPlayPayloads_areValid() {
        val kodiJson = KodiCastController.buildPlayerOpenPayload("http://192.168.1.10:8099/stream.m3u8")
        val parsed = org.json.JSONObject(kodiJson)
        assertEquals("2.0", parsed.getString("jsonrpc"))
        assertEquals("Player.Open", parsed.getString("method"))
        assertEquals(
            "http://192.168.1.10:8099/stream.m3u8",
            parsed.getJSONObject("params").getJSONObject("item").getString("file")
        )

        val airPlayBody = AirPlayCastController.buildAirPlayPlayBody("http://192.168.1.10:8099/stream.m3u8")
        assertTrue(airPlayBody.contains("Content-Location: http://192.168.1.10:8099/stream.m3u8"))
        assertTrue(airPlayBody.contains("Start-Position: 0.0"))
    }

    @Test
    fun manualDeviceJsonParsing_loadsKodiConfigSafely() {
        val json = """
            [
              {
                "id": "manual_kodi_1",
                "name": "Kodi Wohnzimmer",
                "protocol": "KODI",
                "host": "192.168.178.88",
                "port": 8080,
                "username": "kodi",
                "password": "secret"
              }
            ]
        """.trimIndent()
        val parsed = ManualCastDeviceStore.parseJson(json)
        assertEquals(1, parsed.size)
        assertEquals("Kodi Wohnzimmer", parsed[0].name)
        assertEquals(CastProtocol.KODI, parsed[0].protocol)
        assertEquals("192.168.178.88", parsed[0].host)
        assertEquals(8080, parsed[0].port)
        assertTrue(parsed[0].isManual)
    }

    @Test
    fun qrCodeGenerator_producesValidMatrixForWebCastUrl() {
        val matrix = QrCodeGenerator.generateMatrix("http://192.168.178.20:8099", 128)
        assertNotNull(matrix)
        assertEquals(128 * 128, matrix!!.size)
        assertTrue(matrix.any { it })
        assertFalse(matrix.all { it })
    }
}
