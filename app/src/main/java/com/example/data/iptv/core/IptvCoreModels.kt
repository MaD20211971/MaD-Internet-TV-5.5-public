package com.example.data.iptv.core

/**
 * Typ des IPTV-Inhalts: Live-TV, Filme (VOD) oder Serien.
 */
enum class IptvContentType(val label: String) {
    LIVE_TV("Live TV"),
    VOD_MOVIE("VOD Film"),
    SERIES("Serie")
}

/**
 * Streng definierte Regionen-/Länder-Hierarchie gemäß Anforderung:
 * 1. Deutschland (DE)
 * 2. Österreich (AT)
 * 3. Schweiz (CH)
 * 4. USA (US)
 * 5. Großbritannien (UK)
 * 6. Adult / Erotik (direkt nach UK)
 */
enum class IptvRegionTier(
    val priority: Int,
    val code: String,
    val displayName: String,
    val flag: String
) {
    DE(1, "DE", "Deutschland", "🇩🇪"),
    AT(2, "AT", "Österreich", "🇦🇹"),
    CH(3, "CH", "Schweiz", "🇨🇭"),
    US(4, "US", "USA", "🇺🇸"),
    UK(5, "UK", "Großbritannien", "🇬🇧"),
    ADULT(6, "ADULT", "Adult / Erotik", "🔞");

    companion object {
        val ORDERED_TIERS = entries.sortedBy { it.priority }

        fun fromCode(code: String?): IptvRegionTier? {
            if (code.isNullOrBlank()) return null
            val upper = code.trim().uppercase()
            return entries.firstOrNull { it.code == upper }
        }
    }
}

/**
 * Einheitliches Datenmodell für jeden IPTV-Datenstrom (Kanal, Film oder Serien-Episode).
 * Bewahrt die originale Provider-Kategorie 1:1, ergänzt um die normalisierte Region und Metadaten.
 */
data class IptvStreamItem(
    val id: String,
    val name: String,
    val streamUrl: String,
    val type: IptvContentType,
    val originalCategory: String,
    val regionTier: IptvRegionTier,
    val tvgId: String? = null,
    val tvgName: String? = null,
    val logoUrl: String? = null,
    val seriesTitle: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val durationSeconds: Int = -1,
    val containerExtension: String? = null,
    val sourceProvider: String = "UNKNOWN", // "M3U", "XTREAM", "MAC_PORTAL"
    val extraAttributes: Map<String, String> = emptyMap(),
    val originalProviderIndex: Int = 0 // Zur Erhaltung der exakten internen Provider-Reihenfolge (Stable Sort)
)

/**
 * Gruppierung einer originalen Provider-Kategorie unter ihrem jeweiligen Länder-/Regionen-Tier.
 */
data class IptvCategoryGroup(
    val regionTier: IptvRegionTier,
    val originalCategoryName: String,
    val type: IptvContentType,
    val items: List<IptvStreamItem>
) {
    val itemCount: Int get() = items.size
}

/**
 * Gesamtergebnis eines IPTV-Import- und Filtervorgangs.
 */
data class IptvImportResult(
    val allItems: List<IptvStreamItem>,
    val liveItems: List<IptvStreamItem>,
    val vodItems: List<IptvStreamItem>,
    val seriesItems: List<IptvStreamItem>,
    val categoryGroups: List<IptvCategoryGroup>,
    val totalImported: Int,
    val totalDiscarded: Int,
    val countsByTier: Map<IptvRegionTier, Int>
)
