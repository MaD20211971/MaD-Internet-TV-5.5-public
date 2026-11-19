package com.example

import com.example.data.model.ContentType
import com.example.data.parser.ContentClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentClassifierTest {

    @Test
    fun testNumberedMovieTitlesClassifiedAsMovie() {
        val titles = listOf(
            "1- Greenland 2",
            "2- Final Fight",
            "3- The Creator",
            "10 - Dune: Part Two",
            "01. Inception",
            "005 | Avatar 2",
            "1 - Fast & Furious 10 (2023)"
        )

        for (title in titles) {
            val result = ContentClassifier.classify(
                name = title,
                group = "DEUTSCHLAND",
                streamUrl = "http://server:8080/stream/123.ts"
            )
            assertEquals("Expected $title to be classified as MOVIE", ContentType.MOVIE, result.type)
        }
    }

    @Test
    fun testLiveTvChannelsClassifiedAsLiveTv() {
        val channels = listOf(
            "Das Erste HD",
            "ZDF HD",
            "RTL HD",
            "PROSIEBEN",
            "SAT.1",
            "Sky Sport Bundesliga 1 HD",
            "DAZN 1 Bar HD",
            "ORF 1 HD",
            "SRF 1 HD"
        )

        for (channel in channels) {
            val result = ContentClassifier.classify(
                name = channel,
                group = "DEUTSCHLAND",
                streamUrl = "http://server:8080/stream/123.ts"
            )
            assertEquals("Expected $channel to be classified as LIVE_TV", ContentType.LIVE_TV, result.type)
        }
    }

    @Test
    fun testSeriesClassifiedAsSeries() {
        val seriesTitles = listOf(
            "Stranger Things S01E02",
            "Breaking Bad 1x04",
            "Dark Staffel 2 Folge 3",
            "The Mandalorian Season 2 Episode 1"
        )

        for (title in seriesTitles) {
            val result = ContentClassifier.classify(
                name = title,
                group = "DE: SERIEN",
                streamUrl = "http://server:8080/series/123.mp4"
            )
            assertEquals("Expected $title to be classified as SERIES", ContentType.SERIES, result.type)
            assertTrue("Expected season to be extracted", (result.season ?: 0) >= 1)
        }
    }

    @Test
    fun testStalkerPortalChannelsNotTreatedAsHeaders() {
        val stalkerUrls = listOf(
            "http://dragoniptv.de:80/c/portal.php?type=itv&action=create_link&cmd=ffmpeg%20http%3A%2F%2Flocalhost%2Fch%2F9763_&mac=00:1A:79:50:D3:DD",
            "ffmpeg http://localhost/ch/9763_",
            "http://localhost/ch/9763_",
            "mac_portal://dragoniptv.de:80/c/portal.php?type=itv&action=create_link&cmd=ffmpeg%20http%3A%2F%2Flocalhost%2Fch%2F9763_&mac=00:1A:79:50:D3:DD"
        )

        val channelNames = listOf(
            "(DE) Das Erste HD ᵘˡᵗʳᵃ",
            "(DE) ZDF HD ᵘˡᵗʳᵃ",
            "(DE) Sky Sport Bundesliga 1 HD",
            "(DE) RTL HD ᵘˡᵗʳᵃ"
        )

        for (url in stalkerUrls) {
            for (name in channelNames) {
                val isHeader = ContentClassifier.isHeaderOrDividerChannel(name, url, "DE - Deutschland")
                org.junit.Assert.assertFalse("Channel $name with URL $url should NOT be treated as header/divider", isHeader)
            }
        }
    }

    @Test
    fun testRealDividersAreStillDetected() {
        val dividers = listOf(
            "----------------",
            "================",
            "---- SKY CINEMA ----",
            "INFO: Bitte lesen",
            "SERVER INFO"
        )

        for (div in dividers) {
            val isHeader = ContentClassifier.isHeaderOrDividerChannel(div, null, null)
            assertTrue("Header/divider '$div' should be detected as header", isHeader)
        }
    }

    @Test
    fun testDragonIptvCategorySortingPriority() {
        val categories = listOf(
            "DE - Sky Bundesliga",
            "DE - Deutschland",
            "DE - Deutschland ᵘˡᵗʳᵃ (Testphase)",
            "CH - Schweiz",
            "NL - Nederland",
            "DE - Kino",
            "DE - Dazn Sport",
            "DE - Dokumentarfilm & Realität",
            "All",
            "ADULT / 18+"
        )

        val sorted = ContentClassifier.sortCategoriesWithDachPriority(categories)

        assertEquals("All", sorted[0])
        assertEquals("DE - Deutschland", sorted[1])
        assertEquals("DE - Deutschland ᵘˡᵗʳᵃ (Testphase)", sorted[2])
        assertEquals("DE - Dokumentarfilm & Realität", sorted[3])
        assertEquals("DE - Kino", sorted[4])
        assertTrue(sorted.indexOf("CH - Schweiz") < sorted.indexOf("ADULT / 18+"))
        assertTrue(sorted.indexOf("ADULT / 18+") < sorted.indexOf("NL - Nederland"))
    }

    @Test
    fun testAsteriskHeaderPrefixExtraction() {
        assertEquals("DE", ContentClassifier.extractCountryCodeFromPrefix("DE * Kino"))
        assertEquals("AT", ContentClassifier.extractCountryCodeFromPrefix("AT * Austria"))
        assertEquals("CH", ContentClassifier.extractCountryCodeFromPrefix("CH * Swiss"))
        assertEquals("ES", ContentClassifier.extractCountryCodeFromPrefix("ES * Hentai"))
        assertEquals("ADULT", ContentClassifier.extractCountryCodeFromPrefix("XXX * For  Adults"))
    }

    @Test
    fun testAsteriskHeaderCleanSubFolderName() {
        assertEquals("Kino", ContentClassifier.getCleanSubFolderName("DE * Kino"))
        assertEquals("Austria", ContentClassifier.getCleanSubFolderName("AT * Austria"))
        assertEquals("Swiss", ContentClassifier.getCleanSubFolderName("CH * Swiss"))
        assertEquals("Hentai", ContentClassifier.getCleanSubFolderName("ES * Hentai"))
        assertEquals("For  Adults", ContentClassifier.getCleanSubFolderName("XXX * For  Adults"))
    }

    @Test
    fun testAsteriskHeaderCategorySorting() {
        val categories = listOf(
            "XXX * For  Adults",
            "ES * Hentai",
            "DE * Kino",
            "CH * Swiss",
            "AT * Austria",
            "All"
        )
        val sorted = ContentClassifier.sortCategoriesWithDachPriority(categories)

        assertEquals("All", sorted[0])
        assertEquals("DE * Kino", sorted[1])
        assertEquals("AT * Austria", sorted[2])
        assertEquals("CH * Swiss", sorted[3])
        assertEquals("XXX * For  Adults", sorted[4])
        assertEquals("ES * Hentai", sorted[5])
    }

    @Test
    fun testAsteriskHeaderContentClassification() {
        // 1. DE * Kino
        val deMovie = ContentClassifier.classify(
            name = "Inception (2010)",
            group = "DE * Kino",
            streamUrl = "http://server:8080/movie/123.mp4"
        )
        assertEquals("DE", deMovie.countryCode)
        assertTrue(deMovie.isDach)
        assertEquals(ContentType.MOVIE, deMovie.type)

        // 2. AT * Austria
        val atChannel = ContentClassifier.classify(
            name = "ORF 1 HD",
            group = "AT * Austria",
            streamUrl = "http://server:8080/live/orf1.ts"
        )
        assertEquals("AT", atChannel.countryCode)
        assertTrue(atChannel.isDach)
        assertEquals(ContentType.LIVE_TV, atChannel.type)

        // 3. CH * Swiss
        val chChannel = ContentClassifier.classify(
            name = "SRF 1 HD",
            group = "CH * Swiss",
            streamUrl = "http://server:8080/live/srf1.ts"
        )
        assertEquals("CH", chChannel.countryCode)
        assertTrue(chChannel.isDach)
        assertEquals(ContentType.LIVE_TV, chChannel.type)

        // 4. ES * Hentai
        val esAdult = ContentClassifier.classify(
            name = "Anime Episode 1",
            group = "ES * Hentai",
            streamUrl = "http://server:8080/movie/hentai1.mp4"
        )
        assertEquals("ES", esAdult.countryCode)
        org.junit.Assert.assertFalse(esAdult.isDach)

        // 5. XXX * For Adults
        val xxxAdult = ContentClassifier.classify(
            name = "Hustler TV HD",
            group = "XXX * For  Adults",
            streamUrl = "http://server:8080/live/hustler.ts"
        )
        assertEquals("ADULT", xxxAdult.countryCode)
        org.junit.Assert.assertFalse(xxxAdult.isDach)
    }

    @Test
    fun testAdultStatusStrictlyFollowsHeaderNotFilmTitle() {
        // Documentaries, shows, movies with words like 'Porn', 'Adult', 'Beauties' in title
        // must NOT be classified as adult when their group is a standard DE group!
        org.junit.Assert.assertFalse(
            "Porn Culture in Doku must not be adult",
            ContentClassifier.isAdultContent("Porn Culture (2019) DE", "DE ✪ DOKU / SHOW")
        )
        org.junit.Assert.assertFalse(
            "Robot Chicken Adult Swim in Kino Highlights must not be adult",
            ContentClassifier.isAdultContent("Robot Chicken Adult Swim", "DE ✪ KINO HIGHLIGHTS")
        )
        org.junit.Assert.assertFalse(
            "Bring Me the Beauties in Doku must not be adult",
            ContentClassifier.isAdultContent("Bring Me the Beauties (2020)", "DE ✪ DOKU / SHOW")
        )

        val docuClassified = ContentClassifier.classify(
            name = "Porn Culture (2019) DE",
            group = "DE ✪ DOKU / SHOW",
            streamUrl = "http://server:8080/movie/123.mp4"
        )
        assertEquals("DE", docuClassified.countryCode)
        assertTrue(docuClassified.isDach)

        // Explicit adult groups MUST be recognized as adult
        assertTrue(ContentClassifier.isAdultGroup("XXX * For  Adults"))
        assertTrue(ContentClassifier.isAdultGroup("DE * Erotik"))
        assertTrue(ContentClassifier.isAdultGroup("ES * Hentai"))
        assertTrue(ContentClassifier.isAdultGroup("ADULT / 18+"))
        assertTrue(ContentClassifier.isAdultContent("Any Video", "XXX * For  Adults"))
    }

    @Test
    fun testCountrySelectorOrderAndUniqueness() {
        val allKnown = ContentClassifier.ALL_KNOWN_COUNTRIES
        val codes = allKnown.map { it.code }

        // All codes in ALL_KNOWN_COUNTRIES must be unique
        assertEquals("Known country codes must be unique", codes.distinct().size, codes.size)

        // DE must be rank 1, AT rank 2, CH rank 3, US rank 4, UK rank 5
        val de = allKnown.first { it.code == "DE" }
        val at = allKnown.first { it.code == "AT" }
        val ch = allKnown.first { it.code == "CH" }
        val us = allKnown.first { it.code == "US" }
        val uk = allKnown.first { it.code == "UK" }
        assertEquals(1, de.rank)
        assertEquals(2, at.rank)
        assertEquals(3, ch.rank)
        assertEquals(4, us.rank)
        assertEquals(5, uk.rank)

        // TR must be rank 6, IT rank 7, FR rank 8
        val tr = allKnown.first { it.code == "TR" }
        val itCountry = allKnown.first { it.code == "IT" }
        val fr = allKnown.first { it.code == "FR" }
        assertEquals(6, tr.rank)
        assertEquals(7, itCountry.rank)
        assertEquals(8, fr.rank)

        // OTHER must be rank 98
        val other = ContentClassifier.getCountryInfo("OTHER")
        assertEquals("OTHER", other.code)
        assertEquals(98, other.rank)
        assertEquals("International / Andere", other.name)

        // Any arbitrary unknown code without channel metadata must map cleanly to UNKNOWN (rank 99)
        val unknown1 = ContentClassifier.getCountryInfo("UNKNOWN_XYZ")
        val unknown2 = ContentClassifier.getCountryInfo("")
        assertEquals("UNKNOWN", unknown1.code)
        assertEquals("UNKNOWN", unknown2.code)
        assertEquals(99, unknown1.rank)
        assertEquals(99, unknown2.rank)

        // Deduplication test: Even when channels have multiple unknown codes, distinctBy guarantees exactly one OTHER
        val simulatedChannelCodes = listOf("US", "UK", "DE", "OTHER", "UNKNOWN_1", "UNKNOWN_2", "", "INT")
        val countryMap = LinkedHashMap<String, ContentClassifier.CountryInfo>()
        for (item in allKnown) {
            countryMap[item.code.uppercase()] = item
        }
        for (rawCode in simulatedChannelCodes) {
            val upper = rawCode.trim().uppercase()
            if (upper.isNotBlank()) {
                val info = ContentClassifier.getCountryInfo(upper)
                val targetCode = info.code.trim().uppercase()
                if (!countryMap.containsKey(targetCode)) {
                    countryMap[targetCode] = info
                }
            }
        }
        val result = countryMap.values.distinctBy { it.code.uppercase() }
        val resultingCodes = result.map { it.code }
        assertEquals(resultingCodes.distinct().size, resultingCodes.size)
        // Verify exactly one OTHER exists
        assertEquals(1, result.count { it.code == "OTHER" })
    }

    @Test
    fun testProviderCountryOrderingAndSorting() {
        // 1. Verify country detection from common provider group titles
        val testGroups = mapOf(
            "|TR| TURKCE" to "TR",
            "TR - ULUSAL" to "TR",
            "|IT| ITALIA" to "IT",
            "IT: GENERALI" to "IT",
            "|FR| FRANCE" to "FR",
            "FR: GENERALISTES" to "FR",
            "|ES| ESPANA" to "ES",
            "ES: DEPORTES" to "ES",
            "|NL| NEDERLAND" to "NL",
            "NL: ALGEMEEN" to "NL",
            "|PL| POLSKA" to "PL",
            "PL: OGOLNE" to "PL",
            "|EXYU| BALKAN" to "EXYU",
            "EX-YU: SRBIJA" to "EXYU",
            "|ALB| SHQIP" to "ALB",
            "ALB: KOSOVO" to "ALB",
            "|US| USA LIVE" to "US",
            "USA: NEWS" to "US",
            "|UK| UK LIVE" to "UK",
            "UK: ENTERTAINMENT" to "UK"
        )

        for ((group, expectedCode) in testGroups) {
            val detected = ContentClassifier.detectCountryForGroup(group)
            assertEquals("Group '$group' must resolve to country code '$expectedCode'", expectedCode, detected)
        }

        // 2. Simulated provider channel distribution
        val channelsByCountry = mapOf(
            "TR" to listOf("TRT 1", "ATV"),
            "DE" to listOf("Das Erste", "ZDF", "RTL"),
            "US" to listOf("NBC", "CBS"),
            "EXYU" to listOf("HRT 1", "RTS 1"),
            "NL" to listOf("NPO 1")
        )

        val countryMap = LinkedHashMap<String, ContentClassifier.CountryInfo>()
        for (item in ContentClassifier.ALL_KNOWN_COUNTRIES) {
            countryMap[item.code.uppercase()] = item
        }
        for (rawCode in channelsByCountry.keys) {
            val upper = rawCode.trim().uppercase()
            if (upper.isNotBlank()) {
                val info = ContentClassifier.getCountryInfo(upper)
                val targetCode = info.code.trim().uppercase()
                if (!countryMap.containsKey(targetCode)) {
                    countryMap[targetCode] = info
                }
            }
        }

        val ordered = countryMap.values
            .distinctBy { it.code.trim().uppercase() }
            .sortedWith(
                compareBy<ContentClassifier.CountryInfo> {
                    val count = channelsByCountry[it.code.trim().uppercase()]?.size ?: 0
                    if (count > 0) 0 else 1
                }
                .thenBy { it.rank }
                .thenBy { it.name }
            )

        // The top countries MUST be the ones with active provider channels, in strict canonical rank:
        // DE (rank 1), US (rank 4), TR (rank 6), NL (rank 10), EXYU (rank 12)
        val activeCodes = ordered.take(5).map { it.code }
        assertEquals(listOf("DE", "US", "TR", "NL", "EXYU"), activeCodes)

        // The remaining countries (with count == 0) must follow in strictly ascending canonical rank
        val inactiveCodes = ordered.drop(5).map { it.code }
        assertTrue("AT must be in inactive countries", inactiveCodes.contains("AT"))
        assertTrue("CH must be in inactive countries", inactiveCodes.contains("CH"))
        assertTrue("UK must be in inactive countries", inactiveCodes.contains("UK"))
        // Check order of first inactive countries: AT (#2), CH (#3), UK (#5), IT (#7), FR (#8), ES (#9)...
        val atIndex = inactiveCodes.indexOf("AT")
        val chIndex = inactiveCodes.indexOf("CH")
        val ukIndex = inactiveCodes.indexOf("UK")
        val itIndex = inactiveCodes.indexOf("IT")
        val otherIndex = inactiveCodes.indexOf("OTHER")
        val unknownIndex = inactiveCodes.indexOf("UNKNOWN")
        assertTrue("AT must precede CH", atIndex < chIndex)
        assertTrue("CH must precede UK", chIndex < ukIndex)
        assertTrue("UK must precede IT", ukIndex < itIndex)
        assertTrue("IT must precede OTHER", itIndex < otherIndex)
        assertTrue("OTHER must precede UNKNOWN", otherIndex < unknownIndex)
    }

    @Test
    fun testChannelNameIgnoredForCountrySelection100PercentAccuracy() {
        data class AmbiguousChannelCase(
            val channelName: String,
            val providerCategory: String,
            val expectedCountry: String
        )

        val cases = listOf(
            // Same channel name "ATV HD" in Turkey vs Austria vs Peru/International
            AmbiguousChannelCase("ATV HD", "TR | Ulusal", "TR"),
            AmbiguousChannelCase("ATV HD", "AT | Österreich", "AT"),
            AmbiguousChannelCase("ATV HD", "International Live", "OTHER"),

            // Same channel name "RTS 1 HD" in Serbia (EXYU) vs Switzerland (CH)
            AmbiguousChannelCase("RTS 1 HD", "EX-YU | Srbija", "EXYU"),
            AmbiguousChannelCase("RTS 1 HD", "CH | Schweiz", "CH"),

            // Same channel name "RTL HD" / "RTL 4" in Netherlands, Croatia, Germany
            AmbiguousChannelCase("RTL 4 HD", "NL | Nederland", "NL"),
            AmbiguousChannelCase("RTL HD", "EX-YU | Hrvatska", "EXYU"),
            AmbiguousChannelCase("RTL HD", "DE | Unterhaltung", "DE"),

            // Same channel name "Sky Sport 1 HD" / "Sky Cinema" in Italy, UK, Germany, Austria
            AmbiguousChannelCase("Sky Sport 1 HD", "IT | Sport", "IT"),
            AmbiguousChannelCase("Sky Sport 1 HD", "UK | Sky Sports", "UK"),
            AmbiguousChannelCase("Sky Sport 1 HD", "DE | Sky Sport", "DE"),
            AmbiguousChannelCase("Sky Cinema Action", "SKY CINEMA ITALIA", "IT"),
            AmbiguousChannelCase("Sky Cinema Premiere", "UK | Movies", "UK"),

            // Same channel name "DMAX", "TLC", "Eurosport 1", "Discovery Channel", "Disney Channel"
            AmbiguousChannelCase("DMAX HD", "IT | Intrattenimento", "IT"),
            AmbiguousChannelCase("DMAX HD", "ES | Entretenimiento", "ES"),
            AmbiguousChannelCase("DMAX HD", "TR | Belgesel", "TR"),
            AmbiguousChannelCase("DMAX HD", "DE | Doku", "DE"),
            AmbiguousChannelCase("Eurosport 1 HD", "FR | Sport", "FR"),
            AmbiguousChannelCase("Eurosport 1 HD", "PL | Sport", "PL"),
            AmbiguousChannelCase("Discovery Channel HD", "US | Documentary", "US"),
            AmbiguousChannelCase("Disney Channel HD", "ES | Infantil", "ES"),

            // Same channel name "Tele 1" in Turkey vs Switzerland
            AmbiguousChannelCase("Tele 1 HD", "TR | Haber", "TR"),
            AmbiguousChannelCase("Tele 1 HD", "CH | Regional", "CH"),

            // Same channel name "3+" in Switzerland vs Denmark/Baltics
            AmbiguousChannelCase("3+ HD", "SE | Scandinavia", "SE"),
            AmbiguousChannelCase("3+ HD", "CH | Schweiz", "CH"),

            // German broadcaster names placed inside foreign categories must NOT override the provider category
            AmbiguousChannelCase("ZDF HD", "TR | Ulusal", "TR"),
            AmbiguousChannelCase("ORF 1 HD", "UK | Entertainment", "UK"),
            AmbiguousChannelCase("SRF 1 HD", "FR | Général", "FR")
        )

        for (testCase in cases) {
            val classified = ContentClassifier.classify(
                name = testCase.channelName,
                group = testCase.providerCategory,
                streamUrl = "http://server:8080/live/123.ts"
            )
            val item = com.example.data.model.ChannelItem(
                id = "test_${testCase.channelName}_${testCase.providerCategory}",
                playlistId = "pl1",
                name = testCase.channelName,
                streamUrl = "http://server:8080/live/123.ts",
                groupTitle = testCase.providerCategory,
                providerCategory = testCase.providerCategory,
                countryCode = classified.countryCode,
                isDach = classified.isDach,
                type = ContentType.LIVE_TV
            )

            assertEquals(
                "Channel '${testCase.channelName}' in category '${testCase.providerCategory}' must map to '${testCase.expectedCountry}'",
                testCase.expectedCountry,
                item.normalizedCountryCode
            )
            org.junit.Assert.assertNotEquals("Must never be UNKNOWN", "UNKNOWN", item.normalizedCountryCode)
            org.junit.Assert.assertNotEquals("Must never be CONFLICT", "CONFLICT", item.normalizedCountryCode)
        }
    }

    @Test
    fun testVodAndSeriesSubcategories100PercentMatchedAndAssigned() {
        val allowedDach = setOf("DE", "AT", "CH", "ADULT")

        val dachVodSubcategories = listOf(
            "DE | Horror",
            "DE | Comedy",
            "DE | Komödie",
            "DE | Action",
            "DE | Thriller",
            "DE | Sci-Fi",
            "DE | Drama",
            "DE | Krimi",
            "DE | Animation",
            "DE | Kids & Familie",
            "DE | Dokumentation",
            "DE | Kino 2025",
            "DE | 4K UHD Filme",
            "VOD | DE | Horror",
            "VOD - DE - Comedy",
            "SRS | DE | Netflix",
            "VIP | DE | Blockbuster",
            "01 | DE | Action & Abenteuer",
            "AT | Österreich Filme",
            "CH | Schweizer Filme",
            "Horror",
            "Comedy",
            "Komödie",
            "Action",
            "Thriller",
            "Drama",
            "Sci-Fi & Fantasy",
            "Animation & Anime",
            "Crime / Krimi",
            "Family & Kids",
            "Netflix Originals",
            "Disney+",
            "Amazon Prime",
            "Apple TV+",
            "Filme in 4K"
        )

        for (cat in dachVodSubcategories) {
            val isMatched = ContentClassifier.isCategoryMatchingCountries(
                groupTitle = cat,
                allowedCountries = allowedDach,
                isVodOrSeries = true
            )
            assertTrue("VOD/Series subcategory '$cat' must be matched for DACH sync", isMatched)

            val parsed = com.example.data.category.ProviderCategoryEngine.parseCategory(
                rawGroup = cat,
                providerType = com.example.data.country.ProviderType.MAC,
                fallbackName = "Despicable Me 4 (2024)"
            )
            assertTrue(
                "Parsed country '${parsed.countryCode}' for '$cat' must be in DACH (DE/AT/CH)",
                parsed.countryCode in setOf("DE", "AT", "CH")
            )
            org.junit.Assert.assertTrue(
                "Subcategory '${parsed.subCategory}' for '$cat' must not be blank",
                parsed.subCategory.isNotBlank()
            )
        }

        // Foreign VOD subcategories must be strictly excluded when only DACH is requested
        val foreignVodSubcategories = listOf(
            "TR | Horror",
            "TR | Komedi",
            "EN | Comedy",
            "UK | Action",
            "US | Thriller",
            "FR | Comédie",
            "ES | Terror",
            "IT | Commedia",
            "NL | Actie",
            "PL | Horror",
            "AR | Drama",
            "RU | Боевик",
            "EX-YU | Filmovi",
            "ALB | Filma",
            "VOD | EN | Horror",
            "VOD - TR - Comedy"
        )

        for (foreignCat in foreignVodSubcategories) {
            val isMatched = ContentClassifier.isCategoryMatchingCountries(
                groupTitle = foreignCat,
                allowedCountries = allowedDach,
                isVodOrSeries = true
            )
            org.junit.Assert.assertFalse(
                "Foreign VOD subcategory '$foreignCat' must NOT be synced when only DACH is selected",
                isMatched
            )
        }
    }
}
