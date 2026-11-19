package com.example

import com.example.data.radio.RadioBrowserApiClient
import com.example.data.radio.RadioStationEntity
import com.example.data.radio.RadioStreamRecorder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RadioBrowserAndRecorderTest {

    @Test
    fun testDeutschrockDetectionAndQualityBadge() {
        assertTrue(RadioStationEntity.detectDeutschrock("RADIO BOB! Deutschrock", "rock,german"))
        assertTrue(RadioStationEntity.detectDeutschrock("laut.fm/deutschrock-radio", "rock"))
        assertTrue(RadioStationEntity.detectDeutschrock("Rock Antenne", "deutschrock,rock"))
        assertFalse(RadioStationEntity.detectDeutschrock("Klassik Radio", "classical,piano"))

        val lowBitrateMp3 = RadioStationEntity(
            stationUuid = "uuid-32",
            name = "Low Bitrate Rock",
            streamUrl = "https://example.com/stream.mp3",
            codec = "MP3",
            bitrate = 32,
            isDeutschrock = true
        )
        assertEquals("MP3 • 32k", lowBitrateMp3.qualityBadge())
        assertTrue(lowBitrateMp3.isMp3Stream())

        val highBitrateMp3 = lowBitrateMp3.copy(bitrate = 128)
        assertEquals("MP3 • 128k", highBitrateMp3.qualityBadge())
    }

    @Test
    fun testAudioFormatPreservesOriginalStreamBitrate() {
        val mp3Low = RadioStreamRecorder.detectAudioFormat(
            contentType = "audio/mpeg",
            streamUrl = "https://stream.example.com/live",
            hintCodec = "MP3",
            icyBitrateHeader = "32",
            hintBitrateKbps = 128
        )
        assertEquals("mp3", mp3Low.extension)
        assertEquals("audio/mpeg", mp3Low.mimeType)
        assertEquals("MP3", mp3Low.codecLabel)
        assertEquals(32, mp3Low.bitrateKbps)
        assertEquals("MP3 32 kbps", mp3Low.qualityDescription())

        val aacStream = RadioStreamRecorder.detectAudioFormat(
            contentType = "audio/aacp",
            streamUrl = "https://stream.example.com/aac",
            hintCodec = "AAC+",
            icyBitrateHeader = "64",
            hintBitrateKbps = 64
        )
        assertEquals("aac", aacStream.extension)
        assertEquals("AAC+", aacStream.codecLabel)
        assertEquals(64, aacStream.bitrateKbps)
    }

    @Test
    fun testIcyDemuxStripsMetadataAndExtractsSongTitle() = runBlocking {
        val audioChunk1 = byteArrayOf(0x11, 0x22, 0x33, 0x44)
        val audioChunk2 = byteArrayOf(0x55, 0x66, 0x77, 0x7F)
        val rawMetaStr = "StreamTitle='Frei.Wild - Land der Vollidioten';"
        val paddedLen = ((rawMetaStr.toByteArray().size + 15) / 16) * 16
        val metaPadded = ByteArray(paddedLen)
        rawMetaStr.toByteArray().copyInto(metaPadded)

        val streamBytes = ByteArrayOutputStream().apply {
            write(audioChunk1)
            write(paddedLen / 16)
            write(metaPadded)
            write(audioChunk2)
        }.toByteArray()

        val output = ByteArrayOutputStream()
        var capturedTitle: String? = null

        val (totalBytes, finalTitle) = RadioStreamRecorder.copyWithIcyDemux(
            input = ByteArrayInputStream(streamBytes),
            output = output,
            icyMetaInt = 4,
            isRecordingActive = { true },
            onChunkWritten = { _, title ->
                if (title != null) capturedTitle = title
            }
        )

        assertEquals(8L, totalBytes)
        assertArrayEquals(audioChunk1 + audioChunk2, output.toByteArray())
        assertEquals("Frei.Wild - Land der Vollidioten", finalTitle)
        assertEquals("Frei.Wild - Land der Vollidioten", capturedTitle)
    }

    @Test
    fun testApiClientJsonParsing() {
        val sampleJson = """
            [
              {
                "stationuuid": "abc-123",
                "name": "RADIO BOB! Deutschrock",
                "url": "http://fallback.example/stream.mp3",
                "url_resolved": "https://streams.radiobob.de/deutschrock/mp3-192",
                "favicon": "https://example.com/bob.png",
                "countrycode": "DE",
                "country": "Germany",
                "language": "german",
                "tags": "deutschrock,rock,german",
                "codec": "MP3",
                "bitrate": 192,
                "votes": 4200,
                "clickcount": 900
              }
            ]
        """.trimIndent()

        val client = RadioBrowserApiClient()
        val parsed = client.parseStationsJson(sampleJson, syncTimestamp = 123456L)

        assertEquals(1, parsed.size)
        val st = parsed.first()
        assertEquals("abc-123", st.stationUuid)
        assertEquals("https://streams.radiobob.de/deutschrock/mp3-192", st.streamUrl)
        assertEquals("MP3", st.codec)
        assertEquals(192, st.bitrate)
        assertTrue(st.isDeutschrock)
        assertEquals("DE", st.countryCode)
    }
}
