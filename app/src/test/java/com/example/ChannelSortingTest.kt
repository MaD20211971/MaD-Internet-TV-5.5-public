package com.example

import com.example.data.country.NaturalOrderComparator
import com.example.data.country.StreamQualityRanker
import com.example.data.model.ChannelItem
import com.example.data.model.ChannelSortMode
import com.example.data.model.ContentType
import com.example.ui.viewmodel.ChannelFilterEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelSortingTest {

    @Test
    fun testNaturalOrderComparator_numberHandling() {
        val list = listOf(
            "Sky Sport 10 HD",
            "Sky Sport 1 HD",
            "Sky Sport 2 HD",
            "Sky Sport 21 HD",
            "Sky Sport 3 HD"
        )
        val sorted = list.sortedWith(NaturalOrderComparator)

        val expected = listOf(
            "Sky Sport 1 HD",
            "Sky Sport 2 HD",
            "Sky Sport 3 HD",
            "Sky Sport 10 HD",
            "Sky Sport 21 HD"
        )
        assertEquals(expected, sorted)
    }

    @Test
    fun testNaturalOrderComparator_dachChannels() {
        assertTrue(NaturalOrderComparator.compare("ORF 1", "ORF 2") < 0)
        assertTrue(NaturalOrderComparator.compare("SRF 1", "SRF 2") < 0)
        assertTrue(NaturalOrderComparator.compare("3+", "4+") < 0)
        assertTrue(NaturalOrderComparator.compare("Sky Cinema 1", "Sky Cinema 2") < 0)
        assertTrue(NaturalOrderComparator.compare("Sky Sport 2", "Sky Sport 10") < 0)
    }

    @Test
    fun testStreamQualityRanker() {
        assertEquals(1, StreamQualityRanker.getQualityRank("Das Erste UHD"))
        assertEquals(1, StreamQualityRanker.getQualityRank("Das Erste 4K"))
        assertEquals(2, StreamQualityRanker.getQualityRank("Das Erste FHD"))
        assertEquals(2, StreamQualityRanker.getQualityRank("Das Erste 1080p"))
        assertEquals(3, StreamQualityRanker.getQualityRank("Das Erste HD"))
        assertEquals(3, StreamQualityRanker.getQualityRank("Das Erste 720p"))
        assertEquals(4, StreamQualityRanker.getQualityRank("Das Erste SD"))
        assertEquals(99, StreamQualityRanker.getQualityRank("Das Erste Backup HD"))
        assertEquals(99, StreamQualityRanker.getQualityRank("Das Erste (ALT) FHD"))
    }

    @Test
    fun testChannelClusteringAndDachCanonicalSorting() {
        val channels = listOf(
            createChannel("1", "ZDF HD", "DE | GENERAL"),
            createChannel("2", "Das Erste HD", "DE | HD"),
            createChannel("3", "Das Erste UHD", "DE | 4K"),
            createChannel("4", "Das Erste Backup", "DE | BACKUP"),
            createChannel("5", "Das Erste FHD", "DE | FULL HD"),
            createChannel("6", "Sky Sport 10 HD", "DE | SPORT"),
            createChannel("7", "Sky Sport 2 HD", "DE | SPORT"),
            createChannel("8", "Sky Sport 1 HD", "DE | SPORT"),
            createChannel("9", "RTL HD", "DE | RTL")
        )

        val sorted = ChannelFilterEngine.filterAndSortLiveChannels(
            allChannels = channels,
            searchQuery = "",
            selectedCategory = "ALLE",
            sortMode = ChannelSortMode.DACH_CANONICAL
        )

        val names = sorted.map { it.name }

        // Das Erste variants must cluster together, ordered by quality: UHD -> FHD -> HD -> Backup
        val dasErsteIdxUhd = names.indexOf("Das Erste UHD")
        val dasErsteIdxFhd = names.indexOf("Das Erste FHD")
        val dasErsteIdxHd = names.indexOf("Das Erste HD")
        val dasErsteIdxBkp = names.indexOf("Das Erste Backup")

        assertTrue(dasErsteIdxUhd in 0 until dasErsteIdxFhd)
        assertTrue(dasErsteIdxFhd < dasErsteIdxHd)
        assertTrue(dasErsteIdxHd < dasErsteIdxBkp)

        // ZDF must follow Das Erste
        val zdfIdx = names.indexOf("ZDF HD")
        assertTrue(dasErsteIdxBkp < zdfIdx)

        // RTL must follow ZDF
        val rtlIdx = names.indexOf("RTL HD")
        assertTrue(zdfIdx < rtlIdx)

        // Sky Sport must be at the end, and Sky Sport 2 must precede Sky Sport 10
        val sport1Idx = names.indexOf("Sky Sport 1 HD")
        val sport2Idx = names.indexOf("Sky Sport 2 HD")
        val sport10Idx = names.indexOf("Sky Sport 10 HD")

        assertTrue(rtlIdx < sport1Idx)
        assertTrue(sport1Idx < sport2Idx)
        assertTrue(sport2Idx < sport10Idx)
    }

    @Test
    fun testQualitySortMode() {
        val channels = listOf(
            createChannel("1", "Das Erste HD", "DE"),
            createChannel("2", "ZDF UHD", "DE"),
            createChannel("3", "RTL FHD", "DE"),
            createChannel("4", "ProSieben SD", "DE")
        )

        val sorted = ChannelFilterEngine.filterAndSortLiveChannels(
            allChannels = channels,
            searchQuery = "",
            selectedCategory = "ALLE",
            sortMode = ChannelSortMode.QUALITY
        )

        assertEquals("ZDF UHD", sorted[0].name)     // 4K/UHD = rank 1
        assertEquals("RTL FHD", sorted[1].name)     // FHD = rank 2
        assertEquals("Das Erste HD", sorted[2].name) // HD = rank 3
        assertEquals("ProSieben SD", sorted[3].name) // SD = rank 4
    }

    private fun createChannel(id: String, name: String, group: String): ChannelItem {
        return ChannelItem(
            id = id,
            playlistId = "pl1",
            name = name,
            streamUrl = "http://stream/$id",
            groupTitle = group,
            countryCode = "DE",
            isDach = true,
            type = ContentType.LIVE_TV
        )
    }
}
