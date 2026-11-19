package com.example.data.country

import java.text.Normalizer
import java.util.Locale

/**
 * Normalization utilities for country strings, codes, channel titles, and station identities.
 */
object CountryNormalizer {

    private val DIACRITICS_REGEX = "\\p{InCombiningDiacriticalMarks}+".toRegex()
    private val SANITIZE_REGEX = "[^A-Z0-9+ ]".toRegex()
    private val MULTI_SPACE_REGEX = "\\s+".toRegex()

    // Non-country 2-letter tokens that frequently appear in IPTV titles/categories
    private val NON_COUNTRY_TWO_LETTER_TOKENS = setOf(
        "HD", "SD", "TV", "EU", "VO", "ON", "GO", "MY", "LA", "LE", "EL",
        "DO", "DA", "DI", "DU", "EN", "II", "IV", "VI", "IX", "XL", "UP",
        "TO", "OF", "OR", "BY", "IN", "ME", "SI", "BA", "NO", "IS", "AM",
        "AN", "IM", "UM", "AB", "ZU", "SO", "AT" // Note: "AT" is handled explicitly with context
    )

    private val LEADING_CATEGORY_TIER_TOKENS = setOf(
        "VIP", "RAW", "HEVC", "H265", "H264", "4K", "UHD", "FHD", "QHD", "HD", "SD",
        "1080P", "720P", "50FPS", "60FPS", "TV", "LIVE", "LIVETV", "VOD", "MOVIE",
        "MOVIES", "FILM", "FILME", "SERIES", "SERIEN", "SRS", "SPORT", "SPORTS",
        "CINEMA", "KINO", "EU", "EUROPE", "LOCAL", "PREMIUM", "GOLD", "PLATINUM",
        "SERVER", "STREAM", "IPTV", "CHANNEL", "CHANNELS", "PLAY", "PLUS", "MAX",
        "TOP", "NEW", "NEU", "NEUHEITEN", "COLLECTION", "COLLECTIONS"
    )

    /**
     * Extracts country code from Unicode flag emojis if present in the raw string.
     */
    fun extractEmojiCountry(input: String?): String? {
        if (input.isNullOrBlank()) return null
        return when {
            input.contains("🇩🇪") -> CountryConstants.CODE_DE
            input.contains("🇦🇹") -> CountryConstants.CODE_AT
            input.contains("🇨🇭") -> CountryConstants.CODE_CH
            input.contains("🇺🇸") -> "US"
            input.contains("🇬🇧") || input.contains("🏴󠁧󠁢󠁥󠁮󠁧󠁿") || input.contains("🏴󠁧󠁢󠁳󠁣󠁴󠁿") -> "UK"
            input.contains("🇹🇷") -> "TR"
            input.contains("🇮🇹") -> "IT"
            input.contains("🇫🇷") -> "FR"
            input.contains("🇪🇸") -> "ES"
            input.contains("🇳🇱") -> "NL"
            input.contains("🇵🇱") -> "PL"
            input.contains("🇵🇹") || input.contains("🇧🇷") -> "PT"
            input.contains("🇷🇺") -> "RU"
            input.contains("🇺🇦") -> "UA"
            input.contains("🇬🇷") -> "GR"
            input.contains("🇷🇴") -> "RO"
            input.contains("🇧🇬") -> "BG"
            input.contains("🇭🇺") -> "HU"
            input.contains("🇨🇿") -> "CZ"
            input.contains("🇸🇰") -> "SK"
            input.contains("🇧🇪") -> "BE"
            input.contains("🇦🇱") || input.contains("🇽🇰") -> "ALB"
            input.contains("🇷🇸") || input.contains("🇭🇷") || input.contains("🇧🇦") ||
            input.contains("🇸🇮") || input.contains("🇲🇰") || input.contains("🇲🇪") -> "EXYU"
            input.contains("🇸🇪") || input.contains("🇳🇴") || input.contains("🇩🇰") || input.contains("🇫🇮") -> "SE"
            input.contains("🇨🇦") -> "CA"
            input.contains("🇮🇳") -> "IN"
            input.contains("🇦🇫") -> "AF"
            input.contains("🇸🇦") || input.contains("🇦🇪") || input.contains("🇪🇬") ||
            input.contains("🇲🇦") || input.contains("🇩🇿") || input.contains("🇹🇳") ||
            input.contains("🇱🇧") || input.contains("🇮🇶") || input.contains("🇯🇴") ||
            input.contains("🇰🇼") || input.contains("🇶🇦") -> "AR"
            else -> null
        }
    }

    /**
     * Strips accents, punctuation, and extra whitespace, converting to uppercase.
     */
    fun cleanText(input: String?): String {
        if (input.isNullOrBlank()) return ""
        val normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
        val withoutDiacritics = DIACRITICS_REGEX.replace(normalized, "")
        val sanitized = SANITIZE_REGEX.replace(withoutDiacritics.uppercase(Locale.ROOT), " ")
        return MULTI_SPACE_REGEX.replace(sanitized, " ").trim()
    }

    /**
     * Normalizes a raw country string or abbreviation into an ISO-like code.
     * Returns null if the string does not clearly map to a known country.
     */
    fun normalizeCountryToken(token: String?): String? {
        if (token.isNullOrBlank()) return null
        extractEmojiCountry(token)?.let { return it }

        val clean = cleanText(token)
        if (clean.isBlank()) return null

        // Neutral multi-country contexts must NOT resolve to a single country
        if (clean == "DACH" || clean == "DE AT CH" || clean == "DE/AT/CH" || clean == "D-A-CH" ||
            clean == "EUROPE" || clean == "INTERNATIONAL" || clean == "WORLD" || clean == "GLOBAL" ||
            clean in LEADING_CATEGORY_TIER_TOKENS
        ) {
            return null
        }

        return when {
            // DEUTSCHLAND / GERMANY
            clean == "DE" || clean == "DEU" || clean == "GER" ||
            clean == "GERMANY" || clean == "DEUTSCHLAND" || clean == "GERMAN" ||
            clean == "DEUTSCH" || clean == "DEUTSCHE" || clean == "DEUTSCHER" ||
            clean == "DEUTSCHES" || clean == "DEUTSCHSPRACHIG" ||
            clean == "ALLEMAGNE" || clean == "ALEMANIA" -> CountryConstants.CODE_DE

            // ÖSTERREICH / AUSTRIA
            clean == "AT" || clean == "AUT" || clean == "AUSTRIA" ||
            clean == "OESTERREICH" || clean == "OESTERREICHISCH" ||
            clean == "AUSTRIAN" || clean == "AUTRICHE" -> CountryConstants.CODE_AT

            // SCHWEIZ / SWITZERLAND
            clean == "CH" || clean == "CHE" || clean == "SUI" ||
            clean == "SWITZERLAND" || clean == "SCHWEIZ" || clean == "SWISS" ||
            clean == "SCHWEIZER" || clean == "SCHWEIZERISCH" ||
            clean == "SUISSE" || clean == "SVIZZERA" -> CountryConstants.CODE_CH

            // OTHER KNOWN COUNTRIES (Explicit Verified Mapping Only)
            clean == "US" || clean == "USA" || clean == "UNITED STATES" || clean == "AMERICA" -> "US"
            clean == "UK" || clean == "GB" || clean == "GBR" || clean == "ENG" || clean == "UNITED KINGDOM" || clean == "ENGLAND" || clean == "BRITAIN" -> "UK"
            clean == "CA" || clean == "CAN" || clean == "CANADA" || clean == "KANADA" -> "CA"
            clean == "TR" || clean == "TUR" || clean == "TURKEY" || clean == "TURKIYE" || clean == "TUERKEI" || clean == "TURK" || clean == "TURKISH" -> "TR"
            clean == "IT" || clean == "ITA" || clean == "ITALY" || clean == "ITALIEN" || clean == "ITALIA" || clean == "ITALIANO" -> "IT"
            clean == "FR" || clean == "FRA" || clean == "FRANCE" || clean == "FRANKREICH" || clean == "FRENCH" || clean == "FRANCAIS" -> "FR"
            clean == "ES" || clean == "ESP" || clean == "SPAIN" || clean == "SPANIEN" || clean == "ESPANA" || clean == "SPANISH" || clean == "ESPANOL" || clean == "LATAM" || clean == "LATINO" -> "ES"
            clean == "NL" || clean == "NLD" || clean == "NED" || clean == "NETHERLANDS" || clean == "NIEDERLANDE" || clean == "HOLLAND" || clean == "DUTCH" -> "NL"
            clean == "BE" || clean == "BEL" || clean == "BELGIUM" || clean == "BELGIEN" || clean == "BELGIQUE" -> "BE"
            clean == "PL" || clean == "POL" || clean == "POLAND" || clean == "POLEN" || clean == "POLSKA" || clean == "POLISH" -> "PL"
            clean == "CZ" || clean == "CZE" || clean == "CZECH" || clean == "TSCHECHIEN" || clean == "CESKO" -> "CZ"
            clean == "SK" || clean == "SVK" || clean == "SLOVAKIA" || clean == "SLOWAKEI" -> "SK"
            clean == "HU" || clean == "HUN" || clean == "HUNGARY" || clean == "UNGARN" || clean == "MAGYAR" -> "HU"
            clean == "RO" || clean == "ROU" || clean == "ROM" || clean == "ROMANIA" || clean == "RUMAENIEN" -> "RO"
            clean == "BG" || clean == "BGR" || clean == "BUL" || clean == "BULGARIA" || clean == "BULGARIEN" -> "BG"
            clean == "GR" || clean == "GRC" || clean == "GRE" || clean == "GREECE" || clean == "GRIECHENLAND" || clean == "HELLAS" -> "GR"
            clean == "PT" || clean == "PRT" || clean == "POR" || clean == "PORTUGAL" || clean == "BR" || clean == "BRA" || clean == "BRAZIL" || clean == "BRASIL" -> "PT"
            clean == "RU" || clean == "RUS" || clean == "RUSSIA" || clean == "RUSSLAND" || clean == "RUSSIAN" -> "RU"
            clean == "UA" || clean == "UKR" || clean == "UKRAINE" -> "UA"
            clean == "SE" || clean == "SWE" || clean == "SWEDEN" || clean == "SCHWEDEN" || clean == "SCANDINAVIA" || clean == "NORDIC" -> "SE"
            clean == "NO" || clean == "NOR" || clean == "NORWAY" || clean == "NORWEGEN" -> "NO"
            clean == "DK" || clean == "DNK" || clean == "DENMARK" || clean == "DAENEMARK" -> "DK"
            clean == "FI" || clean == "FIN" || clean == "FINLAND" || clean == "FINNLAND" -> "FI"
            clean == "EXYU" || clean == "YU" || clean == "EX YU" || clean == "BALKAN" ||
            clean == "SRB" || clean == "RS" || clean == "SERBIA" || clean == "SERBIEN" ||
            clean == "HRV" || clean == "HR" || clean == "CRO" || clean == "CROATIA" || clean == "KROATIEN" ||
            clean == "BIH" || clean == "BA" || clean == "BOSNIA" || clean == "BOSNIEN" ||
            clean == "SVN" || clean == "SI" || clean == "SLO" || clean == "SLOVENIA" ||
            clean == "MKD" || clean == "MK" || clean == "MACEDONIA" ||
            clean == "MNE" || clean == "ME" || clean == "MONTENEGRO" -> "EXYU"
            clean == "ALB" || clean == "AL" || clean == "ALBANIA" || clean == "ALBANIEN" || clean == "SHQIP" || clean == "KOSOVO" || clean == "XK" || clean == "KS" -> "ALB"
            clean == "AR" || clean == "ARA" || clean == "ARB" || clean == "ARABIC" || clean == "ARAB" || clean == "ARABISCH" -> "AR"
            clean == "IN" || clean == "IND" || clean == "INDIA" || clean == "INDIEN" || clean == "HINDI" -> "IN"
            clean == "AF" || clean == "AFG" || clean == "AFGHANISTAN" -> "AF"
            clean == "SPORT" || clean == "SPORTS" -> "SPORT"
            clean == "ADULT" || clean == "XXX" || clean == "EROTIK" || clean == "18+" || clean == "FOR ADULTS" -> "ADULT"
            else -> null
        }
    }

    /**
     * Extracts an unambiguous country code from a group-title/category if one is present.
     */
    fun extractCountryFromCategory(category: String?): String? {
        if (category.isNullOrBlank()) return null

        // 0. Check Unicode Flag Emojis first before stripping non-ASCII characters
        extractEmojiCountry(category)?.let { return it }

        val clean = cleanText(category)
        if (clean.isBlank()) return null

        // Multi-country or neutral categories MUST NOT be forced into a single country
        if (clean == "DACH" || clean.startsWith("DACH ") || clean.contains("DE AT CH") ||
            clean.contains("DE/AT/CH") || clean.contains("DE-AT-CH") || clean.contains("D-A-CH") ||
            clean == "EUROPE" || clean == "INTERNATIONAL" || clean == "WORLD" || clean == "GLOBAL"
        ) {
            return null
        }

        // Strict priority: Adult markers (unless AT/Austria)
        if (!clean.contains("AUSTRIA") && !clean.contains("OESTERREICH") && !clean.startsWith("AT ") &&
            (clean.contains("ADULT") || clean.contains("XXX") || clean.contains("18+") || clean.contains("EROTIK") || clean.contains("PORN"))
        ) {
            return "ADULT"
        }

        // Strict priority: Austrian markers ALWAYS map to AT
        if (clean.contains("AUSTRIA") || clean.contains("OESTERREICH") || clean.contains("AUTRICHE") ||
            clean.startsWith("AT ") || clean.startsWith("AT:") || clean.startsWith("AT-") ||
            clean.startsWith("AT|") || clean.startsWith("AUT ") || clean == "AT" || clean == "AUT" ||
            clean.endsWith(" AT") || clean.endsWith(" AUT") || clean.contains(" AT ")
        ) {
            return CountryConstants.CODE_AT
        }

        // Strict priority: Swiss markers ALWAYS map to CH
        if ((clean.contains("SCHWEIZ") || clean.contains("SWITZERLAND") || clean.contains("SWISS") ||
            clean.contains("SUISSE") || clean.contains("SVIZZERA") ||
            clean.startsWith("CH ") || clean.startsWith("CH:") || clean.startsWith("CH-") ||
            clean.startsWith("CH|") || clean.startsWith("SUI ") || clean == "CH" || clean == "SUI" ||
            clean.endsWith(" CH") || clean.endsWith(" SUI") || clean.contains(" CH ")) &&
            !clean.contains("DISCOVERY CH") && !clean.contains("HISTORY CH") && !clean.contains("CARTOON CH")
        ) {
            return CountryConstants.CODE_CH
        }

        // Strip leading quality/type/number tokens (e.g. "01 | DE - HORROR", "EU | DE - SPORT", "VIP | DE", "VOD | DE - KINO", "4K | DE")
        val tokens = clean.split(" ").filter { it.isNotBlank() }
        var tokenIndex = 0
        while (tokenIndex < tokens.size && (tokens[tokenIndex] in LEADING_CATEGORY_TIER_TOKENS || tokens[tokenIndex].all { it.isDigit() })) {
            tokenIndex++
        }

        if (tokenIndex < tokens.size) {
            val candidate = tokens[tokenIndex]
            // Guard against Spanish/French "DE" preposition when it's not a country tag
            val nextWord = tokens.getOrNull(tokenIndex + 1) ?: ""
            val isRomanceDe = candidate == "DE" && nextWord in setOf("PELICULA", "CINE", "CANAL", "NOTICIAS", "LA", "LOS", "LAS", "EL", "FRANCE", "JANEIRO", "FUTBOL", "ESPORTE")
            if (!isRomanceDe && (tokenIndex == 0 || candidate !in NON_COUNTRY_TWO_LETTER_TOKENS || candidate in setOf("DE", "AT", "CH", "US", "UK", "TR", "IT", "FR", "ES", "NL", "PL", "PT", "RU", "GR", "RO", "SE", "AR"))) {
                val mappedCandidate = normalizeCountryToken(candidate)
                if (mappedCandidate != null && mappedCandidate != "SPORT") {
                    return mappedCandidate
                }
            }
        }

        // Check delimiter-separated segments (e.g. "┃DE┃ Kino", "VOD - DE - Action", "NETFLIX | DE | Serien")
        val segments = category.uppercase(Locale.ROOT)
            .split('|', '┃', '║', ':', '-', '–', '—', '[', ']', '(', ')', '{', '}', '✪', '★', '•', '❖', '»', '«', '▶', '◀', '/')
            .map { cleanText(it) }
            .filter { it.isNotBlank() }
        for (seg in segments) {
            if (seg in NON_COUNTRY_TWO_LETTER_TOKENS && seg != "AT") continue
            val mappedSeg = normalizeCountryToken(seg)
            if (mappedSeg != null && mappedSeg != "SPORT" && mappedSeg != "ADULT") {
                return mappedSeg
            }
        }

        // Check trailing token (e.g. "NETFLIX DE", "DISNEY+ DE", "MOVIES GER", "SKY SPORT DE", "VIP US", "MOVIES TR")
        if (tokens.size >= 2) {
            val lastToken = tokens.last()
            if (lastToken !in NON_COUNTRY_TWO_LETTER_TOKENS || lastToken in setOf("DE", "AT", "CH", "US", "UK", "TR", "IT", "FR", "ES", "NL", "PL", "PT", "RU", "GR", "RO", "SE")) {
                val mappedLast = normalizeCountryToken(lastToken)
                if (mappedLast != null && mappedLast != "SPORT" && mappedLast != "ADULT") {
                    return mappedLast
                }
            }
        }

        // Check Adult category markers (when not an Austrian AT category)
        if (clean.contains("XXX") || clean.contains("18+") || clean.contains("ADULT") ||
            clean.contains("EROTIK") || clean.contains("EROTIC") || clean.contains("PORN") ||
            clean.contains("FOR ADULTS") || clean.contains("REDLIGHT")
        ) {
            return "ADULT"
        }

        // Full country category names (checked BEFORE generic DACH genre names so "SKY CINEMA ITALIA" -> IT)
        return when {
            clean.contains("UNITED STATES") || clean.contains("USA") || clean.startsWith("US ") || clean == "US" -> "US"
            clean.contains("UNITED KINGDOM") || clean.contains("ENGLAND") || clean.contains("BRITISH") || clean.contains("SCOTLAND") || clean.startsWith("UK ") || clean.startsWith("GB ") || clean == "UK" || clean == "GB" -> "UK"
            clean.contains("TURKEY") || clean.contains("TUERKEI") || clean.contains("TURKIYE") || clean.contains("TURKISH") || clean.contains("TURKCE") || clean.contains("ULUSAL") || clean.startsWith("TR ") || clean == "TR" -> "TR"
            clean.contains("ITALY") || clean.contains("ITALIEN") || clean.contains("ITALIA") || clean.contains("ITALIANO") || clean.startsWith("IT ") || clean == "IT" -> "IT"
            clean.contains("FRANCE") || clean.contains("FRANKREICH") || clean.contains("FRENCH") || clean.contains("FRANCAIS") || clean.startsWith("FR ") || clean == "FR" -> "FR"
            clean.contains("SPAIN") || clean.contains("SPANIEN") || clean.contains("ESPANA") || clean.contains("SPANISH") || clean.contains("ESPANOL") || clean.contains("CASTELLANO") || clean.contains("LATINO") || clean.startsWith("ES ") || clean == "ES" -> "ES"
            clean.contains("POLAND") || clean.contains("POLEN") || clean.contains("POLSKA") || clean.contains("POLISH") || clean.startsWith("PL ") || clean == "PL" -> "PL"
            clean.contains("NETHERLANDS") || clean.contains("NIEDERLANDE") || clean.contains("NEDERLAND") || clean.contains("HOLLAND") || clean.contains("DUTCH") || clean.startsWith("NL ") || clean == "NL" -> "NL"
            clean.contains("ALBANIA") || clean.contains("ALBANIEN") || clean.contains("SHQIP") || clean.contains("KOSOVO") || clean.contains("KOSOVA") || clean.startsWith("ALB ") || clean.startsWith("AL ") || clean == "ALB" || clean == "AL" -> "ALB"
            clean.contains("EXYU") || clean.contains("EX YU") || clean.contains("BALKAN") || clean.contains("SERBIA") || clean.contains("SRBIJA") || clean.contains("CROATIA") || clean.contains("HRVATSKA") || clean.contains("BOSNIA") || clean.contains("BOSNA") || clean.contains("SLOVENIA") || clean.contains("MACEDONIA") || clean.contains("MONTENEGRO") -> "EXYU"
            clean.contains("ARABIC") || clean.contains("ARABISCH") || clean.contains("ARAB ") || clean.contains("MIDDLE EAST") || clean.startsWith("AR ") || clean == "AR" -> "AR"
            clean.contains("RUSSIA") || clean.contains("RUSSLAND") || clean.contains("RUSSIAN") || clean.contains("UKRAINE") || clean.startsWith("RU ") || clean.startsWith("UA ") || clean == "RU" -> "RU"
            clean.contains("PORTUGAL") || clean.contains("PORTUGUESE") || clean.contains("BRAZIL") || clean.contains("BRASIL") || clean.startsWith("PT ") || clean.startsWith("BR ") || clean == "PT" || clean == "BR" -> "PT"
            clean.contains("GREECE") || clean.contains("GRIECHENLAND") || clean.contains("GREEK") || clean.contains("HELLAS") || clean.contains("CYPRUS") || clean.startsWith("GR ") || clean == "GR" -> "GR"
            clean.contains("BELGIUM") || clean.contains("BELGIEN") || clean.contains("BELGIQUE") || clean.contains("VLAANDEREN") || clean.startsWith("BE ") || clean == "BE" -> "BE"
            clean.contains("ROMANIA") || clean.contains("RUMAENIEN") || clean.contains("ROMANIAN") || clean.startsWith("RO ") || clean == "RO" -> "RO"
            clean.contains("CZECH") || clean.contains("TSCHECHIEN") || clean.contains("SLOVAKIA") || clean.contains("SLOWAKEI") || clean.contains("HUNGARY") || clean.contains("UNGARN") || clean.startsWith("CZ ") || clean.startsWith("SK ") || clean == "CZ" -> "CZ"
            clean.contains("BULGARIA") || clean.contains("BULGARIEN") || clean.contains("BULGARIAN") || clean.startsWith("BG ") || clean == "BG" -> "BG"
            clean.contains("SWEDEN") || clean.contains("SCHWEDEN") || clean.contains("NORWAY") || clean.contains("NORWEGEN") || clean.contains("DENMARK") || clean.contains("DAENEMARK") || clean.contains("FINLAND") || clean.contains("SCANDINAVIA") || clean.contains("SKANDINAVIEN") || clean.contains("NORDIC") || clean.startsWith("SE ") || clean == "SE" -> "SE"
            clean.contains("CANADA") || clean.contains("KANADA") || clean.contains("CANADIAN") || clean.startsWith("CA ") || clean == "CA" -> "CA"
            clean.contains("INDIA") || clean.contains("INDIEN") || clean.contains("INDIAN") || clean.contains("HINDI") || clean.contains("PUNJABI") || clean.contains("TAMIL") || clean.contains("PAKISTAN") || clean.startsWith("IN ") || clean == "IN" -> "IN"
            clean.contains("AFGHANISTAN") || clean.contains("AFGHAN") || clean.contains("IRAN") || clean.contains("PERSIAN") || clean.contains("KURD") || clean.startsWith("AF ") || clean == "AF" -> "AF"
            clean.contains("DEUTSCHLAND") || clean.contains("GERMANY") || clean.contains("DEUTSCH") ||
            clean.contains("GERMAN") || clean.startsWith("DE ") || clean == "DE" || clean.contains(" DE ") ||
            clean.contains("OEFFENTLICH") || clean.contains("HAUPTSENDER") || clean.contains("PRIVATSENDER") ||
            clean.contains("UNTERHALTUNG") || clean.contains("KOMOEDIE") || clean.contains("ABENTEUER") ||
            clean.contains("NEUERSCHEINUNGEN") || clean.contains("DOKUMENTATION") || clean.contains("KINDERFILME") ||
            clean.contains("HEIMATFILME") || clean.contains("HEIMAT") || clean.contains("NACHRICHTEN") ||
            clean.contains("ARD MEDIATHEK") || clean.contains("ZDF MEDIATHEK") ||
            clean.contains("SKY CINEMA") || clean.contains("SKY BUNDESLIGA") || clean.contains("BUNDESLIGA") ||
            clean.contains("DFB POKAL") || clean.contains("DYN SPORT") ||
            clean.contains("MAGENTA SPORT") || clean.contains("MAGENTASPORT") -> CountryConstants.CODE_DE
            else -> null
        }
    }

    /**
     * Computes a stable, normalized station identity (e.g., "ARD_DAS_ERSTE" for all ARD variants).
     * This avoids treating duplicates across sources as distinct stations and enables persistent learning.
     */
    fun computeNormalizedChannelId(name: String?): String {
        if (name.isNullOrBlank()) return "UNKNOWN_CHANNEL"
        val clean = cleanText(name)

        // Remove resolution / stream tags
        var s = clean
            .replace("1080P", "")
            .replace("720P", "")
            .replace("4K", "")
            .replace("UHD", "")
            .replace("FHD", "")
            .replace("HD", "")
            .replace("SD", "")
            .replace("HEVC", "")
            .replace("RAW", "")
            .replace("BACKUP", "")
            .replace("VIP", "")
            .replace("50FPS", "")
            .replace("60FPS", "")
            .trim()

        s = MULTI_SPACE_REGEX.replace(s, " ").trim()

        return when {
            s.contains("DAS ERSTE") || s.contains("ARD") -> "ARD_DAS_ERSTE"
            s.contains("ZDF") && !s.contains("NEO") && !s.contains("INFO") -> "ZDF"
            s.contains("ZDFNEO") || (s.contains("ZDF") && s.contains("NEO")) -> "ZDF_NEO"
            s.contains("ZDFINFO") || (s.contains("ZDF") && s.contains("INFO")) -> "ZDF_INFO"
            s.contains("ORF 1") || s.contains("ORF1") || s.contains("ORF EINS") -> "ORF_1"
            s.contains("ORF 2") || s.contains("ORF2") || s.contains("ORF ZWEI") -> "ORF_2"
            s.contains("ORF III") || s.contains("ORF 3") || s.contains("ORF3") || s.contains("ORF DREI") -> "ORF_3"
            s.contains("ORF SPORT") -> "ORF_SPORT"
            s.contains("SRF 1") || s.contains("SRF1") -> "SRF_1"
            s.contains("SRF ZWEI") || s.contains("SRF 2") || s.contains("SRF2") -> "SRF_2"
            s.contains("SRF INFO") -> "SRF_INFO"
            s.contains("3SAT") -> "3SAT"
            s.contains("ARTE") -> "ARTE"
            s.contains("RTL DEUTSCHLAND") -> "RTL_DE"
            s.contains("RTL AUSTRIA") || s.contains("RTL OESTERREICH") -> "RTL_AT"
            s.contains("RTL SCHWEIZ") || s.contains("RTL CH") -> "RTL_CH"
            s.contains("PROSIEBEN DEUTSCHLAND") -> "PRO7_DE"
            s.contains("PROSIEBEN AUSTRIA") -> "PRO7_AT"
            s.contains("PROSIEBEN SCHWEIZ") || s.contains("PRO7 CH") -> "PRO7_CH"
            s.contains("SAT 1 DEUTSCHLAND") || s.contains("SAT1 DEUTSCHLAND") -> "SAT1_DE"
            s.contains("SAT 1 AUSTRIA") || s.contains("SAT1 AUSTRIA") || s.contains("SAT 1 OESTERREICH") -> "SAT1_AT"
            s.contains("SAT 1 SCHWEIZ") || s.contains("SAT1 SCHWEIZ") || s.contains("SAT 1 CH") -> "SAT1_CH"
            s.contains("PULS 4") || s.contains("PULS4") -> "PULS_4"
            s.contains("PULS 24") || s.contains("PULS24") -> "PULS_24"
            s.contains("ATV 2") || s.contains("ATV2") -> "ATV_2"
            s == "ATV" || s.startsWith("ATV ") -> "ATV_1"
            s.contains("SERVUSTV OESTERREICH") || s.contains("SERVUS TV AT") || s.contains("SERVUS TV OESTERREICH") -> "SERVUS_AT"
            s.contains("SERVUSTV DEUTSCHLAND") || s.contains("SERVUS TV DE") -> "SERVUS_DE"
            s.contains("TELEZUERI") || s.contains("TELE ZUERI") -> "TELE_ZUERI"
            s.contains("3+") || s == "3 PLUS" -> "3_PLUS"
            s.contains("4+") || s == "4 PLUS" -> "4_PLUS"
            s.contains("TV24") -> "TV24"
            s.contains("KIKA") -> "KIKA"
            s.contains("PHOENIX") -> "PHOENIX"
            s.contains("TAGESSCHAU24") || s.contains("TAGESSCHAU 24") -> "TAGESSCHAU_24"
            s.contains("WDR") -> "WDR"
            s.contains("NDR") -> "NDR"
            s.contains("BR FERNSEHEN") || s.contains("BAYERISCHES FS") -> "BR"
            s.contains("SWR") -> "SWR"
            s.contains("MDR") -> "MDR"
            s.contains("HR FERNSEHEN") -> "HR"
            s.contains("RBB") -> "RBB"
            else -> s.replace(" ", "_").take(32)
        }
    }
}

