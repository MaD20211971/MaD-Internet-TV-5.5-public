package com.example.data.category

import com.example.data.model.ChannelItem
import com.example.data.parser.ContentClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Prioritizes IPTV channels and provider categories with strict priority:
 * 1. DE: (Deutschland)
 * 2. AT: (Österreich)
 * 3. CH: (Schweiz)
 * 4. ADULT (18+ / Erotik)
 * 5. All other countries on-demand.
 */
object IptvCategoryPrioritizer {

    val PRIORITY_COUNTRY_CODES = listOf("DE", "AT", "CH", "ADULT")

    suspend fun categorizeAndSort(
        rawChannels: List<ChannelItem>
    ): Map<String, List<ChannelItem>> = withContext(Dispatchers.Default) {
        val grouped = rawChannels.groupBy { channel ->
            channel.groupTitle.trim().ifEmpty { "Uncategorized" }
        }

        val sortedKeys = grouped.keys.sortedWith { catA, catB ->
            val rankA = getCategoryPriorityRank(catA)
            val rankB = getCategoryPriorityRank(catB)

            when {
                rankA != rankB -> rankA.compareTo(rankB)
                else -> catA.compareTo(catB, ignoreCase = true)
            }
        }

        val result = LinkedHashMap<String, List<ChannelItem>>(sortedKeys.size)
        for (key in sortedKeys) {
            val channelsInGroup = grouped[key].orEmpty().sortedWith(
                compareBy<ChannelItem> { if (it.providerSortOrder > 0) it.providerSortOrder else Int.MAX_VALUE }
                    .thenBy { it.name.lowercase() }
            )
            result[key] = channelsInGroup
        }

        result
    }

    private val DE_PRIORITY_REGEX = Regex("""\b(DE|GER|DEU|GERMAN|GERMANY|DEUTSCH|DEUTSCHE|DEUTSCHER|DEUTSCHES|DEUTSCHLAND)\b""")
    private val AT_PRIORITY_REGEX = Regex("""\b(AT|AUT|AUSTRIA|AUSTRIAN|OESTERREICH|OESTERREICHISCH)\b""")
    private val CH_PRIORITY_REGEX = Regex("""\b(CH|SUI|CHE|SWISS|SWITZERLAND|SCHWEIZ|SCHWEIZER|SUISSE|SVIZZERA)\b""")
    private val ADULT_PRIORITY_REGEX = Regex("""(\b(ADULT|ADULTS|XXX|EROTIK|EROTIC|PORN|REDLIGHT)\b|18\+|\+18)""")

    fun getCategoryPriorityRank(categoryName: String): Int {
        val normalized = categoryName.trim().uppercase()
            .replace("Ö", "OE")
            .replace("Ä", "AE")
            .replace("Ü", "UE")
        if (normalized.isEmpty()) return 5
        return when {
            DE_PRIORITY_REGEX.containsMatchIn(normalized) -> 1
            AT_PRIORITY_REGEX.containsMatchIn(normalized) -> 2
            CH_PRIORITY_REGEX.containsMatchIn(normalized) -> 3
            ADULT_PRIORITY_REGEX.containsMatchIn(normalized) -> 4
            else -> 5
        }
    }
}

fun prioritizeIptvCategories(categories: List<com.example.data.model.Category>): List<com.example.data.model.Category> {
    return categories.sortedBy { IptvCategoryPrioritizer.getCategoryPriorityRank(it.name) }
}

fun prioritizeIptvCategoryNames(categoryNames: List<String>): List<String> {
    return categoryNames.sortedBy { IptvCategoryPrioritizer.getCategoryPriorityRank(it) }
}
