package com.example.data.model

/**
 * Lightweight aggregate summary of channel counts per country.
 * Loaded instantly via fast indexed SQL count queries without fetching channel rows.
 */
data class CountryCount(
    val countryCode: String,
    val count: Int
)

data class ContentTypeCount(
    val type: ContentType,
    val count: Int
)

data class CountrySummary(
    val deCount: Int = 0,
    val atCount: Int = 0,
    val chCount: Int = 0,
    val otherCount: Int = 0,
    val unknownCount: Int = 0,
    val conflictCount: Int = 0,
    val totalCount: Int = 0,
    val byCountry: Map<String, Int> = emptyMap()
) {
    fun getCountForCode(code: String): Int {
        val upper = code.trim().uppercase()
        return when (upper) {
            "DE" -> deCount
            "AT" -> atCount
            "CH" -> chCount
            "OTHER" -> otherCount
            "UNKNOWN" -> unknownCount
            "CONFLICT" -> conflictCount
            else -> byCountry[upper] ?: 0
        }
    }

    companion object {
        val EMPTY = CountrySummary()

        fun fromCounts(counts: List<CountryCount>): CountrySummary {
            var de = 0
            var at = 0
            var ch = 0
            var other = 0
            var unknown = 0
            var conflict = 0
            var total = 0
            val map = mutableMapOf<String, Int>()

            for (c in counts) {
                val code = c.countryCode.trim().uppercase()
                val count = c.count
                total += count
                map[code] = (map[code] ?: 0) + count

                when (code) {
                    "DE" -> de += count
                    "AT" -> at += count
                    "CH" -> ch += count
                    "UNKNOWN", "" -> unknown += count
                    "CONFLICT" -> conflict += count
                    else -> other += count
                }
            }

            return CountrySummary(
                deCount = de,
                atCount = at,
                chCount = ch,
                otherCount = other,
                unknownCount = unknown,
                conflictCount = conflict,
                totalCount = total,
                byCountry = map
            )
        }
    }
}
