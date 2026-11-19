package com.example

import com.example.data.country.ClassificationStatus
import com.example.data.country.CountryConstants
import com.example.data.country.CountryResolver
import com.example.data.country.DachMasterDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DachMasterDatabaseTest {

    @Test
    fun testChannelRecognitionCanonical() {
        val dasErste = DachMasterDatabase.findMatch("Das Erste HD")
        assertTrue(dasErste.isMatch)
        assertEquals("DE_ARD_DAS_ERSTE", dasErste.channel?.channelId)
        assertEquals("DE", dasErste.channel?.countryCode)

        val zdf = DachMasterDatabase.findMatch("ZDF FHD 1080p")
        assertTrue(zdf.isMatch)
        assertEquals("DE_ZDF", zdf.channel?.channelId)
        assertEquals("DE", zdf.channel?.countryCode)

        val orf1 = DachMasterDatabase.findMatch("AT: ORF 1 HD (50fps)")
        assertTrue(orf1.isMatch)
        assertEquals("AT_ORF_1", orf1.channel?.channelId)
        assertEquals("AT", orf1.channel?.countryCode)

        val srf1 = DachMasterDatabase.findMatch("CH| SRF 1 HD")
        assertTrue(srf1.isMatch)
        assertEquals("CH_SRF_1", srf1.channel?.channelId)
        assertEquals("CH", srf1.channel?.countryCode)
    }

    @Test
    fun testMultilingualSwissChannelsRecognizedAsCH() {
        // Romandie (French Switzerland)
        val rts1 = DachMasterDatabase.findMatch("RTS 1 HD")
        assertTrue(rts1.isMatch)
        assertEquals("CH_RTS_1", rts1.channel?.channelId)
        assertEquals("CH", rts1.channel?.countryCode)
        assertEquals("FR-CH", rts1.channel?.region)

        // Svizzera italiana (Italian Switzerland)
        val rsi1 = DachMasterDatabase.findMatch("RSI LA 1 HD")
        assertTrue(rsi1.isMatch)
        assertEquals("CH_RSI_LA_1", rsi1.channel?.channelId)
        assertEquals("CH", rsi1.channel?.countryCode)
        assertEquals("IT-CH", rsi1.channel?.region)
    }

    @Test
    fun testDisambiguationDreiSatVsDreiPlus() {
        // 3sat is DE Kultur, 3+ is CH Private
        val dreisat = DachMasterDatabase.findMatch("3sat HD")
        assertTrue(dreisat.isMatch)
        assertEquals("DE_3SAT", dreisat.channel?.channelId)
        assertEquals("DE", dreisat.channel?.countryCode)

        val dreiplus = DachMasterDatabase.findMatch("3+ HD")
        assertTrue(dreiplus.isMatch)
        assertEquals("CH_3_PLUS", dreiplus.channel?.channelId)
        assertEquals("CH", dreiplus.channel?.countryCode)
    }

    @Test
    fun testDisambiguationPulsChannels() {
        // PULS 4 (AT) != PULS 24 (AT) != Puls 8 (CH)
        val puls4 = DachMasterDatabase.findMatch("PULS 4 HD")
        assertTrue(puls4.isMatch)
        assertEquals("AT_PULS_4", puls4.channel?.channelId)
        assertEquals("AT", puls4.channel?.countryCode)

        val puls24 = DachMasterDatabase.findMatch("PULS 24 HD")
        assertTrue(puls24.isMatch)
        assertEquals("AT_PULS_24", puls24.channel?.channelId)
        assertEquals("AT", puls24.channel?.countryCode)

        val puls8 = DachMasterDatabase.findMatch("Puls 8 HD")
        assertTrue(puls8.isMatch)
        assertEquals("CH_PULS_8", puls8.channel?.channelId)
        assertEquals("CH", puls8.channel?.countryCode)
    }

    @Test
    fun testDisambiguationAtvChannels() {
        // ATV != ATV II / ATV 2
        val atv = DachMasterDatabase.findMatch("ATV HD")
        assertTrue(atv.isMatch)
        assertEquals("AT_ATV", atv.channel?.channelId)

        val atv2 = DachMasterDatabase.findMatch("ATV 2 HD")
        assertTrue(atv2.isMatch)
        assertEquals("AT_ATV2", atv2.channel?.channelId)
    }

    @Test
    fun testDeduplicationDetection() {
        // Variants of the same channel should be detected as duplicates
        assertTrue(DachMasterDatabase.areDuplicates("Das Erste HD", "Das Erste FHD"))
        assertTrue(DachMasterDatabase.areDuplicates("ORF 1 HD", "ORF 1 (50fps) [HEVC]"))

        // Distinct channels must NOT be duplicates
        assertFalse(DachMasterDatabase.areDuplicates("PULS 4 HD", "PULS 24 HD"))
        assertFalse(DachMasterDatabase.areDuplicates("ORF 1 HD", "ORF 2 HD"))
        assertFalse(DachMasterDatabase.areDuplicates("SRF 1 HD", "SRF zwei HD"))
    }

    @Test
    fun testQualityTagExtraction() {
        val q1 = DachMasterDatabase.extractQuality("DE: Das Erste FHD (50fps) [HEVC]")
        assertEquals("FHD", q1.resolution)
        assertEquals("50fps", q1.frameRate)
        assertEquals("HEVC", q1.codec)
        assertEquals("Das Erste", q1.cleanTitle)

        val q2 = DachMasterDatabase.extractQuality("ORF 1 UHD 4K 60FPS")
        assertEquals("4K", q2.resolution)
        assertEquals("60fps", q2.frameRate)
        assertEquals("ORF 1", q2.cleanTitle)
    }

    @Test
    fun testPopularityRankings() {
        // AT ranking
        val atChannels = DachMasterDatabase.getChannelsByPopularity("AT")
        assertTrue(atChannels.isNotEmpty())
        assertEquals("AT_ORF_2", atChannels[0].channelId) // ORF 2 is rank 1 (20.8% market share)
        assertEquals("AT_ORF_1", atChannels[1].channelId) // ORF 1 is rank 2 (10.8% market share)

        // CH ranking
        val chChannels = DachMasterDatabase.getChannelsByPopularity("CH")
        assertTrue(chChannels.isNotEmpty())
        assertEquals("CH_SRF_1", chChannels[0].channelId) // SRF 1 is rank 1 (19.2% market share)
        assertEquals("CH_SRF_ZWEI", chChannels[1].channelId) // SRF zwei is rank 2 (9.1% market share)

        // DE ranking
        val deChannels = DachMasterDatabase.getChannelsByPopularity("DE")
        assertTrue(deChannels.isNotEmpty())
        assertEquals("DE_ZDF", deChannels[0].channelId) // ZDF is rank 1 (14.6% market share)
        assertEquals("DE_ARD_DAS_ERSTE", deChannels[1].channelId) // Das Erste is rank 2 (11.9% market share)
    }

    @Test
    fun testSearchChannels() {
        val orfResults = DachMasterDatabase.search(query = "orf", countryCode = "AT")
        assertTrue(orfResults.isNotEmpty())
        assertTrue(orfResults.all { it.countryCode == "AT" })

        val srfResults = DachMasterDatabase.search(query = "srf", countryCode = "CH")
        assertTrue(srfResults.isNotEmpty())
        assertTrue(srfResults.all { it.countryCode == "CH" })
    }

    @Test
    fun testCountryResolverIntegrationWithDachMasterDatabase() {
        val resOrf = CountryResolver.resolve(title = "ORF 1 HD")
        assertEquals(CountryConstants.CODE_AT, resOrf.countryCode)
        assertEquals(ClassificationStatus.VERIFIED, resOrf.status)
        assertTrue(resOrf.confidence >= 95)
        assertTrue(resOrf.evidence.any { it.source == "dach-master-match" })

        val resSrf = CountryResolver.resolve(title = "SRF 1 HD")
        assertEquals(CountryConstants.CODE_CH, resSrf.countryCode)
        assertEquals(ClassificationStatus.VERIFIED, resSrf.status)
        assertTrue(resSrf.confidence >= 95)

        val res3sat = CountryResolver.resolve(title = "3sat HD")
        assertEquals(CountryConstants.CODE_DE, res3sat.countryCode)
        assertEquals(ClassificationStatus.VERIFIED, res3sat.status)

        val res3plus = CountryResolver.resolve(title = "3+ HD")
        assertEquals(CountryConstants.CODE_CH, res3plus.countryCode)
        assertEquals(ClassificationStatus.VERIFIED, res3plus.status)
    }
}
