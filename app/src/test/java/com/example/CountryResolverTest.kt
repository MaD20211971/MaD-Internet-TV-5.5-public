package com.example

import com.example.data.country.ClassificationStatus
import com.example.data.country.CountryConstants
import com.example.data.country.CountryNormalizer
import com.example.data.country.CountryResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CountryResolverTest {

    @Test
    fun testGermanBroadcastersResolveToDE() {
        val channels = listOf("Das Erste HD", "ARD Das Erste", "ZDF HD", "WDR Köln HD", "NDR Hamburg", "BR Fernsehen Süd", "tagesschau24 HD", "Phoenix HD")
        for (name in channels) {
            val result = CountryResolver.resolve(title = name)
            assertEquals("Expected DE for $name", CountryConstants.CODE_DE, result.countryCode)
            assertEquals("Expected VERIFIED for $name", ClassificationStatus.VERIFIED, result.status)
            assertTrue("Expected high confidence for $name", result.confidence >= 90)
        }
    }

    @Test
    fun testAustrianBroadcastersResolveToAT() {
        val channels = listOf("ORF 1 HD", "ORF 2 Wien HD", "ORF III HD", "ORF SPORT+", "ATV HD", "ATV 2", "PULS 4 HD", "PULS 24 HD", "Krone TV HD")
        for (name in channels) {
            val result = CountryResolver.resolve(title = name)
            assertEquals("Expected AT for $name", CountryConstants.CODE_AT, result.countryCode)
            assertEquals("Expected VERIFIED for $name", ClassificationStatus.VERIFIED, result.status)
            assertTrue("Expected high confidence for $name", result.confidence >= 90)
        }
    }

    @Test
    fun testSwissBroadcastersResolveToCH() {
        val channels = listOf("SRF 1 HD", "SRF zwei HD", "SRF info HD", "3+ HD", "4+ HD", "TV24 HD", "TeleZüri HD")
        for (name in channels) {
            val result = CountryResolver.resolve(title = name)
            assertEquals("Expected CH for $name", CountryConstants.CODE_CH, result.countryCode)
            assertEquals("Expected VERIFIED for $name", ClassificationStatus.VERIFIED, result.status)
            assertTrue("Expected high confidence for $name", result.confidence >= 90)
        }
    }

    @Test
    fun testRegionalBroadcasterVariants() {
        val rtlAt = CountryResolver.resolve(title = "RTL Austria")
        assertEquals(CountryConstants.CODE_AT, rtlAt.countryCode)

        val rtlCh = CountryResolver.resolve(title = "RTL Schweiz")
        assertEquals(CountryConstants.CODE_CH, rtlCh.countryCode)

        val sat1At = CountryResolver.resolve(title = "Sat.1 Austria HD")
        assertEquals(CountryConstants.CODE_AT, sat1At.countryCode)

        val pro7Ch = CountryResolver.resolve(title = "ProSieben CH HD")
        assertEquals(CountryConstants.CODE_CH, pro7Ch.countryCode)

        val rtlDe = CountryResolver.resolve(title = "RTL Deutschland HD")
        assertEquals(CountryConstants.CODE_DE, rtlDe.countryCode)
    }

    @Test
    fun testGermanLanguageAloneNeverDefaultsToDE() {
        // Strict user rule: "Ein Inhalt darf NICHT einfach Deutschland zugeordnet werden, nur weil die Sprache Deutsch ist."
        // "NIEMALS raten. Wenn die verfügbaren Informationen nicht eindeutig genug sind, muss der Inhalt in UNKNOWN landen."
        val result = CountryResolver.resolve(
            title = "Top Action Movie 2024",
            language = "German"
        )
        assertNotEquals("Must NEVER default to DE just because language is German!", CountryConstants.CODE_DE, result.countryCode)
        assertEquals("Must be UNKNOWN when only language is known", CountryConstants.CODE_UNKNOWN, result.countryCode)
        assertEquals(ClassificationStatus.UNKNOWN, result.status)
    }

    @Test
    fun testConflictingMetadataResultsInConflictStatus() {
        // tvg-country says CH (weight 100), group-title says DE (weight 70) -> Conflict!
        val result = CountryResolver.resolve(
            title = "Action Stream",
            tvgCountry = "CH",
            groupTitle = "DE: Allgemein"
        )
        assertEquals(ClassificationStatus.CONFLICT, result.status)
        assertEquals(CountryConstants.CODE_CONFLICT, result.countryCode)
        assertTrue(result.reason.contains("Konflikt"))
    }

    @Test
    fun testNormalizedChannelIdUnifiesStationVariants() {
        val id1 = CountryNormalizer.computeNormalizedChannelId("Das Erste HD")
        val id2 = CountryNormalizer.computeNormalizedChannelId("ARD Das Erste FHD 1080p")
        val id3 = CountryNormalizer.computeNormalizedChannelId("ARD (Das Erste) [RAW]")
        assertEquals("ARD / Das Erste variants must produce identical station IDs", id1, id2)
        assertEquals("ARD / Das Erste variants must produce identical station IDs", id2, id3)

        val orf1 = CountryNormalizer.computeNormalizedChannelId("ORF 1 HD")
        val orf2 = CountryNormalizer.computeNormalizedChannelId("ORF Eins Full HD")
        assertEquals("ORF 1 variants must produce identical station IDs", orf1, orf2)
    }

    @Test
    fun testNormalizeIndexCountryCodeStrictValues() {
        assertEquals("DE", CountryConstants.normalizeIndexCountryCode("DE"))
        assertEquals("AT", CountryConstants.normalizeIndexCountryCode("AT"))
        assertEquals("CH", CountryConstants.normalizeIndexCountryCode("CH"))
        assertEquals("OTHER", CountryConstants.normalizeIndexCountryCode("OTHER"))
        assertEquals("UNKNOWN", CountryConstants.normalizeIndexCountryCode("UNKNOWN"))
        assertEquals("CONFLICT", CountryConstants.normalizeIndexCountryCode("CONFLICT"))

        // Any other international countries must map to fixed index OTHER
        assertEquals("OTHER", CountryConstants.normalizeIndexCountryCode("US"))
        assertEquals("OTHER", CountryConstants.normalizeIndexCountryCode("UK"))
        assertEquals("OTHER", CountryConstants.normalizeIndexCountryCode("TR"))
        assertEquals("OTHER", CountryConstants.normalizeIndexCountryCode("FR"))
        assertEquals("OTHER", CountryConstants.normalizeIndexCountryCode("IT"))
        assertEquals("OTHER", CountryConstants.normalizeIndexCountryCode("EXYU"))

        // Null, empty, or INT must normalize to UNKNOWN
        assertEquals("UNKNOWN", CountryConstants.normalizeIndexCountryCode(null))
        assertEquals("UNKNOWN", CountryConstants.normalizeIndexCountryCode(""))
        assertEquals("UNKNOWN", CountryConstants.normalizeIndexCountryCode("INT"))
    }

    @Test
    fun testContentItemUnifiedModel() {
        val item = com.example.data.model.ChannelItem(
            playlistId = "pl-1",
            name = "Das Erste HD",
            streamUrl = "http://stream.example/live.m3u8",
            countryCode = CountryConstants.CODE_DE,
            countryConfidence = 95,
            classificationStatus = "VERIFIED"
        )
        assertEquals("Das Erste HD", item.name)
        assertEquals("DE", item.countryCode)
        assertEquals("pl-1", item.playlistId)
    }

    @Test
    fun testAustrianChannelsInGermanGroupsResolveToAT() {
        // Specific station identity or AT prefix must NOT be overridden by a broad German folder category
        val orfInGermanGroup = CountryResolver.resolve(
            title = "ORF 1 HD",
            groupTitle = "DE: Allgemein"
        )
        assertEquals("ORF 1 in German group must resolve to AT", CountryConstants.CODE_AT, orfInGermanGroup.countryCode)
        assertEquals(ClassificationStatus.VERIFIED, orfInGermanGroup.status)

        val rtlAustriaInGermanGroup = CountryResolver.resolve(
            title = "RTL Austria",
            groupTitle = "DEUTSCHLAND"
        )
        assertEquals("RTL Austria in DE folder must resolve to AT", CountryConstants.CODE_AT, rtlAustriaInGermanGroup.countryCode)
        assertEquals(ClassificationStatus.VERIFIED, rtlAustriaInGermanGroup.status)

        val skySportAustria = CountryResolver.resolve(
            title = "Sky Sport Austria 1 HD",
            groupTitle = "DE: Sky Sport"
        )
        assertEquals("Sky Sport Austria in DE Sport group must resolve to AT", CountryConstants.CODE_AT, skySportAustria.countryCode)
        assertEquals(ClassificationStatus.VERIFIED, skySportAustria.status)

        val atPrefixedChannel = CountryResolver.resolve(
            title = "[AT] ServusTV",
            groupTitle = "Germany Live"
        )
        assertEquals("[AT] prefix must override Germany folder", CountryConstants.CODE_AT, atPrefixedChannel.countryCode)
    }

    @Test
    fun testSwissChannelsInGermanGroupsResolveToCH() {
        val srfInGermanGroup = CountryResolver.resolve(
            title = "SRF 1 HD",
            groupTitle = "DE: Allgemein"
        )
        assertEquals("SRF 1 in German group must resolve to CH", CountryConstants.CODE_CH, srfInGermanGroup.countryCode)
        assertEquals(ClassificationStatus.VERIFIED, srfInGermanGroup.status)

        val threePlus = CountryResolver.resolve(
            title = "3+ HD",
            groupTitle = "DE: Sender"
        )
        assertEquals("3+ in German folder must resolve to CH", CountryConstants.CODE_CH, threePlus.countryCode)
        assertEquals(ClassificationStatus.VERIFIED, threePlus.status)

        val pro7Ch = CountryResolver.resolve(
            title = "ProSieben CH HD",
            groupTitle = "DE: Unterhaltung"
        )
        assertEquals("ProSieben CH in German folder must resolve to CH", CountryConstants.CODE_CH, pro7Ch.countryCode)
        assertEquals(ClassificationStatus.VERIFIED, pro7Ch.status)
    }

    @Test
    fun testTitleSuffixResolution() {
        val rtlAt = CountryResolver.resolve(title = "RTL HD AT")
        assertEquals(CountryConstants.CODE_AT, rtlAt.countryCode)

        val sat1Ch = CountryResolver.resolve(title = "Sat.1 HD CH")
        assertEquals(CountryConstants.CODE_CH, sat1Ch.countryCode)

        val dasErsteDe = CountryResolver.resolve(title = "Das Erste HD (DE)")
        assertEquals(CountryConstants.CODE_DE, dasErsteDe.countryCode)

        val orfAt = CountryResolver.resolve(title = "ORF 1 HD [AT]")
        assertEquals(CountryConstants.CODE_AT, orfAt.countryCode)

        val bbcUk = CountryResolver.resolve(title = "BBC One HD (UK)")
        assertEquals("UK", bbcUk.countryCode)

        val tf1Fr = CountryResolver.resolve(title = "TF1 HD (FR)")
        assertEquals("FR", tf1Fr.countryCode)

        val canale5It = CountryResolver.resolve(title = "Canale 5 HD IT")
        assertEquals("IT", canale5It.countryCode)
    }

    @Test
    fun testDisambiguationCollisionGuards() {
        // "Discovery CH" -> "CH" stands for Channel, NOT Switzerland!
        val discoveryCh = CountryResolver.resolve(title = "Discovery CH HD")
        assertNotEquals("Discovery CH must NEVER resolve to Switzerland!", CountryConstants.CODE_CH, discoveryCh.countryCode)

        // "De Pelicula" -> Spanish title starting with "DE ", NOT Germany!
        val dePelicula = CountryResolver.resolve(title = "De Pelicula Plus")
        assertNotEquals("De Pelicula must NEVER resolve to Germany!", CountryConstants.CODE_DE, dePelicula.countryCode)
        assertEquals("ES", dePelicula.countryCode)

        // "Al Jazeera" -> Arabic, NOT Albania!
        val alJazeera = CountryResolver.resolve(title = "AL: Al Jazeera News")
        assertNotEquals("Al Jazeera must NOT resolve to Albania!", "ALB", alJazeera.countryCode)
    }

    @Test
    fun testMultiCountryCategoriesDoNotForceDE() {
        // Multi-country neutral category like "DACH" or "DE/AT/CH" must NOT force channels to DE
        val neutralStream = CountryResolver.resolve(
            title = "International Action 2024",
            groupTitle = "DACH | All Streams"
        )
        assertNotEquals("DACH category must NEVER default generic titles to DE!", CountryConstants.CODE_DE, neutralStream.countryCode)
        assertEquals(CountryConstants.CODE_UNKNOWN, neutralStream.countryCode)
    }
}
