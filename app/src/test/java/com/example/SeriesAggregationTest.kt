package com.example

import com.example.data.model.ChannelItem
import com.example.data.model.ContentType
import com.example.data.parser.ContentClassifier
import com.example.data.series.SeriesAggregationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SeriesAggregationTest {

    @Test
    fun testGermanSeriesMetadataExtraction() {
        val (t1, s1, e1) = ContentClassifier.extractSeriesMetadata("Dark Staffel 2 Folge 3")
        assertEquals("Dark", t1)
        assertEquals(2, s1)
        assertEquals(3, e1)

        val (t2, s2, e2) = ContentClassifier.extractSeriesMetadata("Babylon Berlin Staffel 3 Episode 5 - Das Phantom")
        assertEquals("Babylon Berlin", t2)
        assertEquals(3, s2)
        assertEquals(5, e2)

        val (t3, s3, e3) = ContentClassifier.extractSeriesMetadata("DE | Breaking Bad S04E10 - Salud")
        assertEquals("Breaking Bad", t3)
        assertEquals(4, s3)
        assertEquals(10, e3)

        val (t4, s4, e4) = ContentClassifier.extractSeriesMetadata("[DE] Game of Thrones 02x09 - Blackwater")
        assertEquals("Game of Thrones", t4)
        assertEquals(2, s4)
        assertEquals(9, e4)
    }

    @Test
    fun testMultiSeasonAndMultiEpisodeAggregation() {
        val rawItems = listOf(
            ChannelItem(
                id = UUID.randomUUID().toString(),
                playlistId = "pl1",
                name = "Dark Staffel 1 Folge 1 - Geheimnisse",
                streamUrl = "http://server/stream1.mp4",
                type = ContentType.SERIES,
                groupTitle = "DE | Serien"
            ),
            ChannelItem(
                id = UUID.randomUUID().toString(),
                playlistId = "pl1",
                name = "Dark Staffel 1 Folge 2 - Lügen",
                streamUrl = "http://server/stream2.mp4",
                type = ContentType.SERIES,
                groupTitle = "DE | Serien"
            ),
            ChannelItem(
                id = UUID.randomUUID().toString(),
                playlistId = "pl1",
                name = "Dark Staffel 2 Folge 1 - Anfänge und Enden",
                streamUrl = "http://server/stream3.mp4",
                type = ContentType.SERIES,
                groupTitle = "DE | Serien"
            ),
            ChannelItem(
                id = UUID.randomUUID().toString(),
                playlistId = "pl1",
                name = "Dark Staffel 2 Folge 2 - Dunkle Materie",
                streamUrl = "http://server/stream4.mp4",
                type = ContentType.SERIES,
                groupTitle = "DE | Serien"
            ),
            ChannelItem(
                id = UUID.randomUUID().toString(),
                playlistId = "pl1",
                name = "Dark Staffel 3 Folge 1 - Deja-vu",
                streamUrl = "http://server/stream5.mp4",
                type = ContentType.SERIES,
                groupTitle = "DE | Serien"
            )
        )

        val seriesList = SeriesAggregationEngine.buildUnifiedSeriesList(
            items = rawItems,
            progressMap = emptyMap(),
            favoritesSet = emptySet()
        )

        assertEquals("Expected exactly 1 aggregated series for Dark", 1, seriesList.size)
        val dark = seriesList.first()
        assertEquals("Dark", dark.title)
        assertEquals("Expected 3 seasons", 3, dark.seasons.size)
        assertEquals("Expected 5 total episodes", 5, dark.totalEpisodesCount)

        val s1 = dark.seasons.find { it.seasonNumber == 1 }
        assertNotNull(s1)
        assertEquals(2, s1!!.episodes.size)
        assertEquals(1, s1.episodes[0].episodeNumber)
        assertEquals(2, s1.episodes[1].episodeNumber)

        val s2 = dark.seasons.find { it.seasonNumber == 2 }
        assertNotNull(s2)
        assertEquals(2, s2!!.episodes.size)
        assertEquals(1, s2.episodes[0].episodeNumber)
        assertEquals(2, s2.episodes[1].episodeNumber)

        val s3 = dark.seasons.find { it.seasonNumber == 3 }
        assertNotNull(s3)
        assertEquals(1, s3!!.episodes.size)
        assertEquals(1, s3.episodes[0].episodeNumber)
    }

    @Test
    fun testFallbackSequentialEpisodeNumberingWhenRawNumbersAreAllOne() {
        val rawItems = listOf(
            ChannelItem(
                id = "ep1",
                playlistId = "pl1",
                name = "Show X Folge A",
                seriesTitle = "Show X",
                seasonNumber = 1,
                episodeNumber = 1,
                streamUrl = "http://server/ep1.mp4",
                type = ContentType.SERIES
            ),
            ChannelItem(
                id = "ep2",
                playlistId = "pl1",
                name = "Show X Folge B",
                seriesTitle = "Show X",
                seasonNumber = 1,
                episodeNumber = 1,
                streamUrl = "http://server/ep2.mp4",
                type = ContentType.SERIES
            ),
            ChannelItem(
                id = "ep3",
                playlistId = "pl1",
                name = "Show X Folge C",
                seriesTitle = "Show X",
                seasonNumber = 1,
                episodeNumber = 1,
                streamUrl = "http://server/ep3.mp4",
                type = ContentType.SERIES
            )
        )

        val seriesList = SeriesAggregationEngine.buildUnifiedSeriesList(
            items = rawItems,
            progressMap = emptyMap(),
            favoritesSet = emptySet()
        )

        val show = seriesList.first()
        val s1 = show.seasons.first()
        assertEquals(3, s1.episodes.size)
        assertEquals(1, s1.episodes[0].episodeNumber)
        assertEquals(2, s1.episodes[1].episodeNumber)
        assertEquals(3, s1.episodes[2].episodeNumber)
    }
}
