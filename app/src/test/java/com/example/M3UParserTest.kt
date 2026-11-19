package com.example

import com.example.data.model.ContentType
import com.example.data.parser.M3UParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class M3UParserTest {

    @Test
    fun testStandardM3uParsing() {
        val m3u = """
            #EXTM3U url-tvg="https://epg.example.com/guide.xml.gz" tvg-shift="0"
            #EXTINF:-1 tvg-id="ARD.de" tvg-name="Das Erste HD" tvg-logo="https://logo.com/ard.png" group-title="DE | Öffentlich-Rechtlich",Das Erste HD
            http://stream.example.com/ard.m3u8
            #EXTINF:-1 tvg-id="ZDF.de" tvg-name="ZDF HD" tvg-logo="https://logo.com/zdf.png" group-title="DE | Öffentlich-Rechtlich",ZDF HD
            http://stream.example.com/zdf.m3u8
        """.trimIndent()

        val result = M3UParser.parseDetailed(m3u, "test_pl")
        assertEquals(1, result.header.epgUrls.size)
        assertEquals("https://epg.example.com/guide.xml.gz", result.header.epgUrls[0])
        assertEquals("0", result.header.tvgShift)

        val channels = result.channels
        assertEquals(2, channels.size)

        val ard = channels[0]
        assertEquals("Das Erste HD", ard.name)
        assertEquals("http://stream.example.com/ard.m3u8", ard.streamUrl)
        assertEquals("ARD.de", ard.tvgId)
        assertEquals("https://logo.com/ard.png", ard.logoUrl)
        assertEquals(ContentType.LIVE_TV, ard.type)
        assertTrue(ard.isDach)
        assertEquals("DE", ard.countryCode)
    }

    @Test
    fun testUnquotedAttributesAndExtGrp() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id=orf1.at tvg-logo=https://logo.com/orf1.png country=AT,ORF 1 HD
            #EXTGRP:Österreich TV
            http://stream.example.com/orf1.m3u8
            #EXTINF:-1 tvg-id=srf1.ch group-title="CH | Sender",SRF 1 HD
            http://stream.example.com/srf1.m3u8
        """.trimIndent()

        val channels = M3UParser.parse(m3u, "test_pl")
        assertEquals(2, channels.size)

        val orf = channels[0]
        assertEquals("ORF 1 HD", orf.name)
        assertEquals("orf1.at", orf.tvgId)
        assertEquals("Österreich TV", orf.groupTitle)
        assertTrue(orf.isDach)
        assertEquals("AT", orf.countryCode)

        val srf = channels[1]
        assertEquals("SRF 1 HD", srf.name)
        assertEquals("srf1.ch", srf.tvgId)
        assertTrue(srf.isDach)
        assertEquals("CH", srf.countryCode)
    }

    @Test
    fun testSeriesAndMovieDetection() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 group-title="DE | Serien",Breaking Bad S02E05 - Bruch
            http://vod.example.com/bb_s2_e5.mkv
            #EXTINF:-1 group-title="DE | Serien",Stranger Things 3x04 - Der Sauna-Test
            http://vod.example.com/st_3x4.mkv
            #EXTINF:-1 group-title="DE | Filme",Inception (2010) 4K UHD
            http://vod.example.com/inception.mp4
        """.trimIndent()

        val channels = M3UParser.parse(m3u, "test_pl")
        assertEquals(3, channels.size)

        val bb = channels[0]
        assertEquals(ContentType.SERIES, bb.type)
        assertEquals("Breaking Bad", bb.seriesTitle)
        assertEquals(2, bb.seasonNumber)
        assertEquals(5, bb.episodeNumber)

        val st = channels[1]
        assertEquals(ContentType.SERIES, st.type)
        assertEquals("Stranger Things", st.seriesTitle)
        assertEquals(3, st.seasonNumber)
        assertEquals(4, st.episodeNumber)

        val movie = channels[2]
        assertEquals(ContentType.MOVIE, movie.type)
        assertEquals("4K UHD", movie.resolutionBadge)
    }

    @Test
    fun testInputStreamStreaming() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="ProSieben.de",ProSieben HD
            http://stream.example.com/pro7.ts
        """.trimIndent()

        val inputStream = ByteArrayInputStream(m3u.toByteArray(Charsets.UTF_8))
        val channels = M3UParser.parse(inputStream, "pl_stream")
        assertEquals(1, channels.size)
        assertEquals("ProSieben HD", channels[0].name)
    }
}
