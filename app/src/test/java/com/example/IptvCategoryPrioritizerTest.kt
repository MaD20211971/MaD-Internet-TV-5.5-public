package com.example

import com.example.data.category.prioritizeIptvCategories
import com.example.data.category.prioritizeIptvCategoryNames
import com.example.data.model.Category
import org.junit.Assert.assertEquals
import org.junit.Test

class IptvCategoryPrioritizerTest {

    @Test
    fun testPrioritizeIptvCategories_ordersByGermanAustrianSwissAndOthers() {
        val input = listOf(
            Category(id = "1", name = "UK | Sports"),
            Category(id = "2", name = "CH: Regional"),
            Category(id = "3", name = "US | News"),
            Category(id = "4", name = "Österreich TV"),
            Category(id = "5", name = "DE: Bundesliga"),
            Category(id = "6", name = "France 24"),
            Category(id = "7", name = "German Movies"),
            Category(id = "8", name = "Switzerland Info"),
            Category(id = "9", name = "Austria Cinema"),
            Category(id = "10", name = "Deutsche Unterhaltung")
        )

        val result = prioritizeIptvCategories(input)

        // 1. German categories
        assertEquals("DE: Bundesliga", result[0].name)
        assertEquals("German Movies", result[1].name)
        assertEquals("Deutsche Unterhaltung", result[2].name)

        // 2. Austrian categories
        assertEquals("Österreich TV", result[3].name)
        assertEquals("Austria Cinema", result[4].name)

        // 3. Swiss categories
        assertEquals("CH: Regional", result[5].name)
        assertEquals("Switzerland Info", result[6].name)

        // 4. Other categories in their original relative sequence
        assertEquals("UK | Sports", result[7].name)
        assertEquals("US | News", result[8].name)
        assertEquals("France 24", result[9].name)
    }

    @Test
    fun testStableSort_preservesExactInternalStructureForOtherCategories() {
        val input = listOf(
            Category(id = "101", name = "Kino & Blockbuster"),
            Category(id = "102", name = "Dokumentation"),
            Category(id = "103", name = "Kinder & Zeichentrick"),
            Category(id = "104", name = "Musik"),
            Category(id = "105", name = "Sport International")
        )

        val result = prioritizeIptvCategories(input)

        // All are priority 4, so exact order must be preserved untouched
        for (i in input.indices) {
            assertEquals("Index $i should match exactly", input[i].name, result[i].name)
            assertEquals("Index $i id should match exactly", input[i].id, result[i].id)
        }
    }

    @Test
    fun testStableSort_preservesOriginalOrderWithinPriorityGroups() {
        val input = listOf(
            Category(id = "de2", name = "German Live"),
            Category(id = "de1", name = "Deutsche Dokus"),
            Category(id = "de3", name = "DE: Sky Sport"),
            Category(id = "at2", name = "Austria Sport"),
            Category(id = "at1", name = "Österreich Info"),
            Category(id = "ch2", name = "Switzerland Live"),
            Category(id = "ch1", name = "Schweiz Film")
        )

        val result = prioritizeIptvCategories(input)

        // German group must preserve internal order: German Live, Deutsche Dokus, DE: Sky Sport
        assertEquals("German Live", result[0].name)
        assertEquals("Deutsche Dokus", result[1].name)
        assertEquals("DE: Sky Sport", result[2].name)

        // Austrian group must preserve internal order: Austria Sport, Österreich Info
        assertEquals("Austria Sport", result[3].name)
        assertEquals("Österreich Info", result[4].name)

        // Swiss group must preserve internal order: Switzerland Live, Schweiz Film
        assertEquals("Switzerland Live", result[5].name)
        assertEquals("Schweiz Film", result[6].name)
    }

    @Test
    fun testPrioritizeIptvCategoryNames_overloadWorksIdentically() {
        val input = listOf(
            "International News",
            "Switzerland HD",
            "Oesterreich Heute",
            "DE: Das Erste",
            "Balkan ExYu"
        )

        val result = prioritizeIptvCategoryNames(input)

        assertEquals("DE: Das Erste", result[0])
        assertEquals("Oesterreich Heute", result[1])
        assertEquals("Switzerland HD", result[2])
        assertEquals("International News", result[3])
        assertEquals("Balkan ExYu", result[4])
    }

    @Test
    fun testRegexBoundaryCheck_doesNotMatchInsideWords() {
        // Words containing "de", "at", "ch" as substrings should NOT trigger priority unless matching \b
        val input = listOf(
            Category(id = "1", name = "Modern Family Series"),
            Category(id = "2", name = "Catch Me If You Can"),
            Category(id = "3", name = "Chat Show"),
            Category(id = "4", name = "DE: Sports")
        )

        val result = prioritizeIptvCategories(input)

        assertEquals("DE: Sports", result[0].name)
        // Others should not be categorized as DE/AT/CH and stay in their original relative sequence
        assertEquals("Modern Family Series", result[1].name)
        assertEquals("Catch Me If You Can", result[2].name)
        assertEquals("Chat Show", result[3].name)
    }
}
