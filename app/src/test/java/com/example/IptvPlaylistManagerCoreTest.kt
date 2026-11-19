package com.example

import com.example.data.iptv.core.IptvContentType
import com.example.data.iptv.core.IptvM3uParser
import com.example.data.iptv.core.IptvMacPortalParser
import com.example.data.iptv.core.IptvPlaylistManager
import com.example.data.iptv.core.IptvRegionClassifier
import com.example.data.iptv.core.IptvRegionTier
import com.example.data.iptv.core.IptvXtreamParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IptvPlaylistManagerCoreTest {

    @Test
    fun testRegionClassifier_identifiesAllowedTiersAndRejectsOthers() {
        // Erlaubte Regionen
        assertEquals(IptvRegionTier.DE, IptvRegionClassifier.classify("DE | Sport", "Sky Sport 1"))
        assertEquals(IptvRegionTier.DE, IptvRegionClassifier.classify("Deutschland Live", "Das Erste HD"))
        assertEquals(IptvRegionTier.AT, IptvRegionClassifier.classify("AT: Regional", "ORF 1 HD"))
        assertEquals(IptvRegionTier.AT, IptvRegionClassifier.classify("Austria News", "PULS 24 HD"))
        assertEquals(IptvRegionTier.CH, IptvRegionClassifier.classify("CH: Info", "SRF 1 HD"))
        assertEquals(IptvRegionTier.CH, IptvRegionClassifier.classify("Switzerland TV", "3+ HD"))
        assertEquals(IptvRegionTier.US, IptvRegionClassifier.classify("US | Entertainment", "NBC East"))
        assertEquals(IptvRegionTier.US, IptvRegionClassifier.classify("USA Cinema", "HBO HD"))
        assertEquals(IptvRegionTier.UK, IptvRegionClassifier.classify("UK | General", "BBC One HD"))
        assertEquals(IptvRegionTier.UK, IptvRegionClassifier.classify("United Kingdom Sports", "Sky Sports Main Event"))
        assertEquals(IptvRegionTier.ADULT, IptvRegionClassifier.classify("XXX For Adults", "Brazzers TV"))
        assertEquals(IptvRegionTier.ADULT, IptvRegionClassifier.classify("DE * Erotik", "Hustler HD"))

        // Streng abgewiesene Regionen (müssen null zurückgeben)
        assertNull(IptvRegionClassifier.classify("FRANCE | General", "TF1 HD"))
        assertNull(IptvRegionClassifier.classify("TURKEY | Ulusal", "TRT 1 HD"))
        assertNull(IptvRegionClassifier.classify("ITALY | Calcio", "Sky Sport Serie A"))
        assertNull(IptvRegionClassifier.classify("SPAIN | Deportes", "Movistar LaLiga"))
        assertNull(IptvRegionClassifier.classify("POLAND | Wiadomości", "TVP 1 HD"))
        assertNull(IptvRegionClassifier.classify("RUSSIA | News", "Channel One"))
        assertNull(IptvRegionClassifier.classify("ARABIC | MBC", "MBC 1"))
        assertNull(IptvRegionClassifier.classify("BALKAN EXYU", "RTS 1"))
    }

    @Test
    fun testM3uParser_filteringAndStrictSortingHierarchy() {
        val m3uContent = """
            #EXTM3U
            #EXTINF:-1 tvg-id="tf1.fr" group-title="France | General",TF1 HD
            http://example.com/live/tf1.m3u8
            #EXTINF:-1 tvg-id="bbc1.uk" group-title="UK | News",BBC One HD
            http://example.com/live/bbc1.m3u8
            #EXTINF:-1 tvg-id="hbo.us" group-title="US | Cinema",HBO HD
            http://example.com/live/hbo.m3u8
            #EXTINF:-1 tvg-id="srf1.ch" group-title="CH | Regional",SRF 1 HD
            http://example.com/live/srf1.m3u8
            #EXTINF:-1 tvg-id="orf1.at" group-title="AT | Sport",ORF 1 HD
            http://example.com/live/orf1.m3u8
            #EXTINF:-1 tvg-id="daserste.de" group-title="DE | Hauptsender",Das Erste HD
            http://example.com/live/daserste.m3u8
            #EXTINF:-1 tvg-id="brazzers.xxx" group-title="XXX | Adult",Brazzers TV HD
            http://example.com/live/brazzers.m3u8
            #EXTINF:-1 tvg-id="trt1.tr" group-title="Turkey | Live",TRT 1 HD
            http://example.com/live/trt1.m3u8
            #EXTINF:-1 tvg-id="zdf.de" group-title="DE | Hauptsender",ZDF HD
            http://example.com/live/zdf.m3u8
        """.trimIndent()

        val result = IptvPlaylistManager.importFromM3u(m3uContent)

        // 1. France und Turkey wurden strikt ignoriert
        assertEquals(7, result.allItems.size)
        assertTrue(result.allItems.none { it.name.contains("TF1") || it.name.contains("TRT 1") })

        // 2. Strikte Sortier-Hierarchie: DE -> AT -> CH -> US -> UK -> ADULT
        val expectedTiers = listOf(
            IptvRegionTier.DE,    // Das Erste HD
            IptvRegionTier.DE,    // ZDF HD
            IptvRegionTier.AT,    // ORF 1 HD
            IptvRegionTier.CH,    // SRF 1 HD
            IptvRegionTier.US,    // HBO HD
            IptvRegionTier.UK,    // BBC One HD
            IptvRegionTier.ADULT  // Brazzers TV HD
        )

        val actualTiers = result.allItems.map { it.regionTier }
        assertEquals(expectedTiers, actualTiers)

        // 3. Provider-Struktur innerhalb der Blöcke erhalten
        val deItems = result.allItems.filter { it.regionTier == IptvRegionTier.DE }
        assertEquals("Das Erste HD", deItems[0].name)
        assertEquals("ZDF HD", deItems[1].name)
        assertEquals("DE | Hauptsender", deItems[0].originalCategory)

        // Adult steht unmittelbar nach UK
        val ukIndex = result.allItems.indexOfFirst { it.regionTier == IptvRegionTier.UK }
        val adultIndex = result.allItems.indexOfFirst { it.regionTier == IptvRegionTier.ADULT }
        assertEquals(ukIndex + 1, adultIndex)
    }

    @Test
    fun testXtreamParser_liveVodSeriesWithStrictHierarchy() {
        val categoriesJson = """
            [
                {"category_id": "1", "category_name": "UK | Entertainment"},
                {"category_id": "2", "category_name": "DE | Dokumentation"},
                {"category_id": "3", "category_name": "Adult | Erotik 18+"},
                {"category_id": "4", "category_name": "US | Series"},
                {"category_id": "5", "category_name": "France | Cinema"}
            ]
        """.trimIndent()

        val liveStreamsJson = """
            [
                {"stream_id": "10", "name": "ITV 1 HD", "category_id": "1"},
                {"stream_id": "20", "name": "Terra X", "category_id": "2"},
                {"stream_id": "30", "name": "Playboy TV", "category_id": "3"},
                {"stream_id": "50", "name": "Canal+ Cinema", "category_id": "5"}
            ]
        """.trimIndent()

        val seriesJson = """
            [
                {"series_id": "40", "name": "Stranger Things", "category_id": "4"}
            ]
        """.trimIndent()

        val result = IptvPlaylistManager.importFromXtream(
            baseUrl = "http://xtream.test:8080",
            username = "user1",
            password = "pass1",
            liveStreamsJson = liveStreamsJson,
            liveCategoriesJson = categoriesJson,
            seriesJson = seriesJson,
            seriesCategoriesJson = categoriesJson
        )

        // France wurde gefiltert
        assertEquals(4, result.allItems.size)

        // Sortier-Reihenfolge: DE (Terra X) -> US (Stranger Things) -> UK (ITV 1 HD) -> ADULT (Playboy TV)
        assertEquals("Terra X", result.allItems[0].name)
        assertEquals(IptvRegionTier.DE, result.allItems[0].regionTier)

        assertEquals("Stranger Things", result.allItems[1].name)
        assertEquals(IptvRegionTier.US, result.allItems[1].regionTier)
        assertEquals(IptvContentType.SERIES, result.allItems[1].type)

        assertEquals("ITV 1 HD", result.allItems[2].name)
        assertEquals(IptvRegionTier.UK, result.allItems[2].regionTier)

        assertEquals("Playboy TV", result.allItems[3].name)
        assertEquals(IptvRegionTier.ADULT, result.allItems[3].regionTier)
    }

    @Test
    fun testMacPortalParser_parsesStalkerEnvelopesAndEnforcesHierarchy() {
        val itvGenres = """
            {"js": {"data": [
                {"id": "100", "title": "AT: Österreich Allgemein"},
                {"id": "200", "title": "CH: Schweiz Regional"},
                {"id": "300", "title": "Italy Calcio"}
            ]}}
        """.trimIndent()

        val itvChannels = """
            {"js": {"data": [
                {"id": "1", "name": "ORF 2 Wien HD", "cmd": "ffrt3 http://stream.server/at/orf2.m3u8", "tv_genre_id": "100"},
                {"id": "2", "name": "TeleZüri HD", "cmd": "auto http://stream.server/ch/telezueri.m3u8", "tv_genre_id": "200"},
                {"id": "3", "name": "Rai 1 HD", "cmd": "http://stream.server/it/rai1.m3u8", "tv_genre_id": "300"}
            ]}}
        """.trimIndent()

        val result = IptvPlaylistManager.importFromMacPortal(
            baseUrl = "http://stalker.portal:88",
            itvChannelsJson = itvChannels,
            itvGenresJson = itvGenres
        )

        // Italy Calcio wurde verworfen
        assertEquals(2, result.allItems.size)

        // AT vor CH
        assertEquals("ORF 2 Wien HD", result.allItems[0].name)
        assertEquals(IptvRegionTier.AT, result.allItems[0].regionTier)
        assertEquals("http://stream.server/at/orf2.m3u8", result.allItems[0].streamUrl)

        assertEquals("TeleZüri HD", result.allItems[1].name)
        assertEquals(IptvRegionTier.CH, result.allItems[1].regionTier)
        assertEquals("http://stream.server/ch/telezueri.m3u8", result.allItems[1].streamUrl)
    }

    @Test
    fun testCategoryGroups_preserveProviderCategoryOrderWithinTiers() {
        val m3uContent = """
            #EXTM3U
            #EXTINF:-1 group-title="DE | Sport",Sky Sport 1
            http://example.com/1
            #EXTINF:-1 group-title="DE | Cinema",Sky Cinema 1
            http://example.com/2
            #EXTINF:-1 group-title="DE | Sport",Sky Sport 2
            http://example.com/3
            #EXTINF:-1 group-title="AT: Info",ORF III
            http://example.com/4
            #EXTINF:-1 group-title="UK | Sports",TNT Sports 1
            http://example.com/5
            #EXTINF:-1 group-title="XXX | Hardcore",Hustler TV
            http://example.com/6
        """.trimIndent()

        val result = IptvPlaylistManager.importFromM3u(m3uContent)
        val groups = result.categoryGroups

        assertEquals(5, groups.size)
        // 1. DE | Sport
        assertEquals(IptvRegionTier.DE, groups[0].regionTier)
        assertEquals("DE | Sport", groups[0].originalCategoryName)
        assertEquals(2, groups[0].itemCount)

        // 2. DE | Cinema
        assertEquals(IptvRegionTier.DE, groups[1].regionTier)
        assertEquals("DE | Cinema", groups[1].originalCategoryName)
        assertEquals(1, groups[1].itemCount)

        // 3. AT: Info
        assertEquals(IptvRegionTier.AT, groups[2].regionTier)
        assertEquals("AT: Info", groups[2].originalCategoryName)

        // 4. UK | Sports
        assertEquals(IptvRegionTier.UK, groups[3].regionTier)
        assertEquals("UK | Sports", groups[3].originalCategoryName)

        // 5. XXX | Hardcore
        assertEquals(IptvRegionTier.ADULT, groups[4].regionTier)
        assertEquals("XXX | Hardcore", groups[4].originalCategoryName)
    }
}
