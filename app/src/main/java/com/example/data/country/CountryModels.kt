package com.example.data.country

/**
 * Source provider types supported by the IPTV application.
 */
enum class ProviderType {
    M3U,
    XTREAM,
    MAC
}

/**
 * Unified content types for IPTV media.
 */
enum class UnifiedContentType {
    LIVE,
    MOVIE,
    SERIES,
    EPISODE
}

/**
 * Classification status for country resolution.
 * - VERIFIED: confidence >= 90 without strong contradictory signals.
 * - PROBABLE: multiple consistent signals or confidence >= 60.
 * - UNKNOWN: insufficient evidence (< 60) — NEVER guess or fallback to DE!
 * - CONFLICT: contradictory strong signals between different countries.
 */
enum class ClassificationStatus {
    VERIFIED,
    PROBABLE,
    UNKNOWN,
    CONFLICT
}

/**
 * Individual piece of evidence recorded during country resolution.
 */
data class CountryEvidence(
    val source: String,
    val detectedCountry: String,
    val weight: Int,
    val description: String
) {
    override fun toString(): String = "$source=$detectedCountry (+$weight: $description)"
}

/**
 * Result produced by the central CountryResolver.
 */
data class CountryResolutionResult(
    val countryCode: String,
    val confidence: Int,
    val status: ClassificationStatus,
    val evidence: List<CountryEvidence>,
    val decision: String,
    val reason: String,
    val normalizedChannelId: String,
    val isDach: Boolean = countryCode in listOf("DE", "AT", "CH"),
    val isManualOverride: Boolean = false
) {
    val evidenceDescriptions: List<String>
        get() = evidence.map { it.toString() }
}

/**
 * Provider-independent unified content model normalizing all M3U, Xtream, and MAC inputs.
 */
data class UnifiedContentItem(
    val id: String,
    val title: String,
    val normalizedTitle: String,
    val type: UnifiedContentType,
    val countryCode: String,
    val countryConfidence: Int,
    val countryEvidence: List<String> = emptyList(),
    val language: String? = null,
    val category: String? = null,
    val genre: String? = null,
    val providerType: ProviderType,
    val providerCategory: String? = null,
    val groupTitle: String? = null,
    val tvgId: String? = null,
    val tvgName: String? = null,
    val tvgCountry: String? = null,
    val tvgLanguage: String? = null,
    val epgId: String? = null,
    val logo: String? = null,
    val poster: String? = null,
    val streamUrl: String,
    val originalCountry: String? = null,
    val providerCountry: String? = null,
    val catalogCountry: String? = null,
    val classificationStatus: ClassificationStatus = ClassificationStatus.UNKNOWN,
    val manualCountryOverride: Boolean = false,
    val manualCountry: String? = null,
    val normalizedChannelId: String = ""
)

/**
 * Constants and UI representations for country classification.
 */
object CountryConstants {
    const val CODE_DE = "DE"
    const val CODE_AT = "AT"
    const val CODE_CH = "CH"
    const val CODE_OTHER = "OTHER"
    const val CODE_UNKNOWN = "UNKNOWN"
    const val CODE_CONFLICT = "CONFLICT"

    val VALID_COUNTRY_CODES: Set<String> = setOf(
        CODE_DE, CODE_AT, CODE_CH, "US", "UK", "TR", "IT", "FR", "ES", "NL", "PL",
        "EXYU", "ALB", "GR", "BE", "RO", "PT", "SE", "NO", "DK", "FI", "AR", "RU",
        "CZ", "SK", "HU", "BG", "UA", "IN", "CA", "AF",
        "VIP", "SPORT", "ADULT", CODE_OTHER, CODE_UNKNOWN, CODE_CONFLICT
    )

    /**
     * Normalizes input country code for the main Country Index / Filter Tabs
     * (DE, AT, CH, OTHER, UNKNOWN, CONFLICT). All international countries map to OTHER.
     */
    fun normalizeIndexCountryCode(raw: String?): String {
        if (raw.isNullOrBlank()) return CODE_UNKNOWN
        val upper = raw.trim().uppercase()
        return when (upper) {
            CODE_DE -> CODE_DE
            CODE_AT -> CODE_AT
            CODE_CH -> CODE_CH
            CODE_OTHER -> CODE_OTHER
            CODE_UNKNOWN, "INT" -> CODE_UNKNOWN
            CODE_CONFLICT -> CODE_CONFLICT
            else -> CODE_OTHER
        }
    }

    /**
     * Normalizes general country codes, preserving recognized international countries (US, UK, TR, IT, FR, etc.).
     */
    fun normalizeCountryCode(raw: String?): String {
        if (raw.isNullOrBlank()) return CODE_UNKNOWN
        val upper = raw.trim().uppercase()
        return when {
            upper == CODE_DE -> CODE_DE
            upper == CODE_AT -> CODE_AT
            upper == CODE_CH -> CODE_CH
            upper == CODE_OTHER -> CODE_OTHER
            upper == CODE_UNKNOWN || upper == "INT" -> CODE_UNKNOWN
            upper == CODE_CONFLICT -> CODE_CONFLICT
            VALID_COUNTRY_CODES.contains(upper) -> upper
            upper.length == 2 && upper.all { it in 'A'..'Z' } -> upper
            else -> CODE_OTHER
        }
    }

    data class CountryUiInfo(
        val code: String,
        val displayName: String,
        val flagEmoji: String,
        val rank: Int,
        val description: String
    )

    val UI_COUNTRIES: List<CountryUiInfo> = listOf(
        CountryUiInfo(CODE_DE, "Deutschland", "🇩🇪", 1, "Deutschland (DE)"),
        CountryUiInfo(CODE_AT, "Österreich", "🇦🇹", 2, "Österreich (AT)"),
        CountryUiInfo(CODE_CH, "Schweiz", "🇨🇭", 3, "Schweiz (CH)"),
        CountryUiInfo(CODE_OTHER, "International / Andere", "🌍", 4, "Andere Länder weltweit"),
        CountryUiInfo(CODE_UNKNOWN, "Nicht zugeordnet", "❓", 5, "Unbekannt / Nicht eindeutig"),
        CountryUiInfo(CODE_CONFLICT, "Konflikte", "⚠️", 6, "Widersprüchliche Metadaten")
    )

    fun getUiInfo(code: String): CountryUiInfo {
        val upper = code.trim().uppercase()
        return UI_COUNTRIES.firstOrNull { it.code == upper } ?: CountryUiInfo(
            code = upper,
            displayName = upper,
            flagEmoji = "🌍",
            rank = 80,
            description = upper
        )
    }
}
