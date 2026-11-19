package com.example.data.iptv.core

import java.util.regex.Pattern

/**
 * Hochpräziser Klassifizierungs- und Filter-Layer für IPTV-Kategorien und Kanäle.
 *
 * Filter-Kriterium:
 * Erlaubt AUSSCHLIESSLICH die Regionen:
 * - DE (Deutschland)
 * - AT (Österreich)
 * - CH (Schweiz)
 * - US (USA)
 * - UK (Großbritannien)
 * - ADULT (Erotik / 18+)
 *
 * Alle anderen Länder (z. B. FR, IT, ES, TR, PL, RU, AR, NL, PT, EXYU etc.)
 * werden strikt abgewiesen (Rückgabe: null) und somit ignoriert.
 */
object IptvRegionClassifier {

    // Regex-Muster für Länder-Präfixe in Provider-Gruppentiteln oder Sendernamen
    private val PREFIX_DE_REGEX = Pattern.compile(
        """(?i)^(?:\[(?:DE|GER|DEUTSCHLAND)\]|\((?:DE|GER|DEUTSCHLAND)\)|(?:DE|GER|DEUTSCHLAND)\s*[:\|\-\–\—\*•✪★☆~>\/\\]|(?:DE|GER)\b)"""
    )
    private val PREFIX_AT_REGEX = Pattern.compile(
        """(?i)^(?:\[(?:AT|AUT|AUSTRIA|ÖSTERREICH)\]|\((?:AT|AUT|AUSTRIA|ÖSTERREICH)\)|(?:AT|AUT|AUSTRIA|ÖSTERREICH)\s*[:\|\-\–\—\*•✪★☆~>\/\\]|(?:AT|AUT)\b)"""
    )
    private val PREFIX_CH_REGEX = Pattern.compile(
        """(?i)^(?:\[(?:CH|SUI|SWITZERLAND|SCHWEIZ)\]|\((?:CH|SUI|SWITZERLAND|SCHWEIZ)\)|(?:CH|SUI|SWITZERLAND|SCHWEIZ)\s*[:\|\-\–\—\*•✪★☆~>\/\\]|(?:CH|SUI)\b)"""
    )
    private val PREFIX_US_REGEX = Pattern.compile(
        """(?i)^(?:\[(?:US|USA|UNITED\s*STATES)\]|\((?:US|USA|UNITED\s*STATES)\)|(?:US|USA|UNITED\s*STATES)\s*[:\|\-\–\—\*•✪★☆~>\/\\]|(?:US|USA)\b)"""
    )
    private val PREFIX_UK_REGEX = Pattern.compile(
        """(?i)^(?:\[(?:UK|GB|UNITED\s*KINGDOM|ENGLAND)\]|\((?:UK|GB|UNITED\s*KINGDOM|ENGLAND)\)|(?:UK|GB|UNITED\s*KINGDOM|ENGLAND)\s*[:\|\-\–\—\*•✪★☆~>\/\\]|(?:UK|GB)\b)"""
    )

    // Explizite Ausschluss-Länder (werden sofort verworfen)
    private val EXCLUDED_COUNTRIES_REGEX = Pattern.compile(
        """(?i)\b(?:FRANCE|FR|FRANKREICH|ITALY|ITALIEN|IT|SPAIN|SPANIEN|ES|TURKEY|TÜRKEI|TR|POLAND|POLEN|PL|RUSSIA|RUSSLAND|RU|NETHERLANDS|HOLLAND|NL|PORTUGAL|PT|ARABIC|ARAB|ALBANIA|ALB|GREECE|GR|ROMANIA|RO|EXYU|BALKAN|SERBIA|CROATIA|BOSNIA|SWEDEN|SE|NORWAY|NO|DENMARK|DK|FINLAND|FI|CZECH|CZ|HUNGARY|HU|BULGARIA|BG|AFRICA|ISRAEL|INDIA|LATINO|BRAZIL|MEXICO)\b"""
    )

    // Schlüsselwörter für Adult / Erotik
    private val ADULT_KEYWORDS = listOf(
        "XXX", "ADULT", "18+", "+18", "EROTIK", "EROTIC", "EROTIQUE", "REDLIGHT", "RED LIGHT",
        "PORN", "PORNO", "PLAYBOY", "BRAZZERS", "HUSTLER", "DORCEL", "PASSION XXX", "PASSIONXXX",
        "PENTHOUSE", "VIVID", "PRIVATE TV", "CENTO X CENTO", "PINK O", "SEXTOSENSO", "HARDCORE",
        "EVIL ANGEL", "REALITY KINGS", "MOFOS", "BLUE HUSTLER", "BARELY LEGAL", "BANG BROS",
        "CENTOXCENTO", "TELECLAP", "EROTICA", "X-RATED", "XRATED", "HENTAI", "SWINGERS",
        "FETISH", "BDSM", "ADULTOS", "ADULTE", "FOR ADULTS"
    )

    /**
     * Ermittelt das zugehörige [IptvRegionTier] anhand der verfügbaren Metadaten.
     *
     * Gibt null zurück, wenn der Inhalt nicht zu den 6 geforderten Kategorien gehört.
     */
    fun classify(
        rawCategory: String?,
        name: String,
        tvgCountry: String? = null,
        tvgId: String? = null
    ): IptvRegionTier? {
        val catUpper = (rawCategory ?: "").trim().uppercase()
        val nameUpper = name.trim().uppercase()
        val combined = "$catUpper $nameUpper"

        // 1. PRIORITÄT: Adult / Erotik (Kategorie-Ebene hat Vorrang)
        if (isAdultCategoryOrChannel(catUpper, nameUpper)) {
            return IptvRegionTier.ADULT
        }

        // 2. Expliziter Ausschluss anderer Länder, sofern keine explizite DE/AT/CH/US/UK Kennung vorliegt
        val normTvgCountry = tvgCountry?.trim()?.uppercase()
        if (normTvgCountry != null && normTvgCountry !in listOf("DE", "AT", "CH", "US", "UK", "GB", "USA", "AUT", "SUI", "GER")) {
            // Explizites tvg-country einer anderen Nation (z.B. FR, IT, TR)
            return null
        }

        // 3. Explizite Ausschluss-Länder in Kategorietiteln (z.B. FRANCE, TURKEY, ITALY, RUSSIA)
        if (EXCLUDED_COUNTRIES_REGEX.matcher(catUpper).find()) {
            return null
        }

        // 4. Österreich (AT) - Prüfung VOR allgemeinem Deutsch/DE
        if (isAustrianContent(catUpper, nameUpper, normTvgCountry, tvgId)) {
            return IptvRegionTier.AT
        }

        // 5. Schweiz (CH) - Prüfung VOR allgemeinem Deutsch/DE
        if (isSwissContent(catUpper, nameUpper, normTvgCountry, tvgId)) {
            return IptvRegionTier.CH
        }

        // 6. Deutschland (DE)
        if (isGermanContent(catUpper, nameUpper, normTvgCountry, tvgId)) {
            return IptvRegionTier.DE
        }

        // 7. USA (US)
        if (isUsContent(catUpper, nameUpper, normTvgCountry, tvgId)) {
            return IptvRegionTier.US
        }

        // 8. Großbritannien (UK)
        if (isUkContent(catUpper, nameUpper, normTvgCountry, tvgId)) {
            return IptvRegionTier.UK
        }

        // Nicht zweifelsfrei einer der 6 Regionen zuzuordnen -> Streng filtern / verwerfen
        return null
    }

    private fun isAdultCategoryOrChannel(catUpper: String, nameUpper: String): Boolean {
        // Prüfen, ob die Kategorie explizit als Adult gekennzeichnet ist
        val catMatches = ADULT_KEYWORDS.any { keyword ->
            catUpper.contains(keyword) || catUpper.matches(".*\\b${Pattern.quote(keyword)}\\b.*".toRegex())
        }
        if (catMatches) return true

        // Wenn die Kategorie leer oder generisch ist, prüfen ob der Sendername ein dedizierter Erotiksender ist
        if (catUpper.isBlank() || catUpper == "ALL" || catUpper == "ALLE" || catUpper == "LIVE") {
            return ADULT_KEYWORDS.any { keyword ->
                nameUpper.contains(keyword) || nameUpper.matches(".*\\b${Pattern.quote(keyword)}\\b.*".toRegex())
            }
        }

        return false
    }

    private fun isAustrianContent(cat: String, name: String, tvgCountry: String?, tvgId: String?): Boolean {
        if (tvgCountry == "AT" || tvgCountry == "AUT") return true
        if (tvgId?.lowercase()?.endsWith(".at") == true || tvgId?.lowercase()?.startsWith("at.") == true) return true

        if (PREFIX_AT_REGEX.matcher(cat).find() || PREFIX_AT_REGEX.matcher(name).find()) return true

        val combined = "$cat $name"
        if (combined.contains("ÖSTERREICH") || combined.contains("OESTERREICH") || combined.contains("AUSTRIA")) {
            // Ausschließen, wenn z.B. nur "Australia" gemeint ist
            if (!combined.contains("AUSTRALIA")) return true
        }

        // Österreichische Sender-Signaturen
        val atBroadcasters = listOf(
            "ORF 1", "ORF 2", "ORF III", "ORF SPORT", "ATV HD", "ATV 2", "ATV II",
            "PULS 4", "PULS 24", "KRONE TV", "KRONEHIT", "HITRADIO Ö3", "OE24", "Ö24",
            "SERVUS TV AT", "SERVUSTV ÖSTERREICH", "SCHLÜSSELBLUME"
        )
        return atBroadcasters.any { name.contains(it) || cat.contains(it) }
    }

    private fun isSwissContent(cat: String, name: String, tvgCountry: String?, tvgId: String?): Boolean {
        if (tvgCountry == "CH" || tvgCountry == "SUI") return true
        if (tvgId?.lowercase()?.endsWith(".ch") == true || tvgId?.lowercase()?.startsWith("ch.") == true) return true

        if (PREFIX_CH_REGEX.matcher(cat).find() || PREFIX_CH_REGEX.matcher(name).find()) return true

        val combined = "$cat $name"
        if (combined.contains("SCHWEIZ") || combined.contains("SWITZERLAND") || combined.contains("SUISSE") || combined.contains("SVIZZERA")) {
            return true
        }

        // Schweizer Sender-Signaturen
        val chBroadcasters = listOf(
            "SRF 1", "SRF ZWEI", "SRF INFO", "3+ HD", "4+ HD", "5+ HD", "6+ HD",
            "TV24", "TV25", "TELE ZÜRI", "TELEZUERI", "TELE BÄRN", "TELEBARN",
            "RTS UN", "RTS DEUX", "RSI LA 1", "RSI LA 2"
        )
        return chBroadcasters.any { name.contains(it) || cat.contains(it) }
    }

    private fun isGermanContent(cat: String, name: String, tvgCountry: String?, tvgId: String?): Boolean {
        if (tvgCountry == "DE" || tvgCountry == "GER") return true
        if (tvgId?.lowercase()?.endsWith(".de") == true || tvgId?.lowercase()?.startsWith("de.") == true) return true

        if (PREFIX_DE_REGEX.matcher(cat).find() || PREFIX_DE_REGEX.matcher(name).find()) return true

        val combined = "$cat $name"
        if (cat.contains("DEUTSCHLAND") || cat.contains("GERMANY") || cat.contains("GERMAN") ||
            cat.contains("DEUTSCH") || cat.contains("BUNDESLIGA")
        ) {
            // Nicht matchen, wenn es sich um Ausdrücke wie "GERMAN SUB" in fremdsprachigen Streams handelt
            return true
        }

        // Deutsche Hauptsender
        val deBroadcasters = listOf(
            "DAS ERSTE", "ARD", "ZDF", "WDR", "NDR", "BR FERNSEHEN", "BAYERISCHES FS",
            "SWR", "MDR", "HR FERNSEHEN", "RBB", "TAGESSCHAU24", "PHOENIX", "KIKA",
            "ZDFNEO", "ZDFINFO", "PROSIEBEN", "SAT.1", "RTL DE", "RTL TELEVISION",
            "VOX", "KABEL EINS", "RTL ZWEI", "SUPER RTL", "NITRO", "NTV", "WELT",
            "SKY SPORT BUNDESLIGA", "DAZN 1 BAR", "DAZN 2 BAR", "MAGENTA SPORT"
        )
        return deBroadcasters.any { name.contains(it) }
    }

    private fun isUsContent(cat: String, name: String, tvgCountry: String?, tvgId: String?): Boolean {
        if (tvgCountry == "US" || tvgCountry == "USA") return true
        if (tvgId?.lowercase()?.endsWith(".us") == true || tvgId?.lowercase()?.startsWith("us.") == true) return true

        if (PREFIX_US_REGEX.matcher(cat).find() || PREFIX_US_REGEX.matcher(name).find()) return true

        val combined = "$cat $name"
        if (cat.contains("UNITED STATES") || cat.matches(".*\\bUSA\\b.*".toRegex()) || cat.contains("AMERICAN")) {
            return true
        }

        // Typische US Broadcast Networks
        val usBroadcasters = listOf(
            "NBC", "CBS", "ABC NEWS", "FOX NEWS", "ESPN", "HBO", "SHOWTIME",
            "CINEMAX", "STARZ", "AMC", "TBS", "TNT US", "USA NETWORK",
            "CNN US", "MSNBC", "HGTV US", "DISCOVERY US"
        )
        return usBroadcasters.any { name.contains(it) }
    }

    private fun isUkContent(cat: String, name: String, tvgCountry: String?, tvgId: String?): Boolean {
        if (tvgCountry == "UK" || tvgCountry == "GB") return true
        if (tvgId?.lowercase()?.endsWith(".uk") == true || tvgId?.lowercase()?.startsWith("uk.") == true) return true

        if (PREFIX_UK_REGEX.matcher(cat).find() || PREFIX_UK_REGEX.matcher(name).find()) return true

        val combined = "$cat $name"
        if (cat.contains("UNITED KINGDOM") || cat.contains("BRITISH") || cat.contains("ENGLAND") || cat.contains("SCOTLAND") || cat.contains("WALES")) {
            return true
        }

        // Typische UK Broadcast Networks
        val ukBroadcasters = listOf(
            "BBC ONE", "BBC TWO", "BBC THREE", "BBC FOUR", "BBC NEWS", "ITV1", "ITV 1",
            "ITV2", "ITV 2", "ITV3", "ITV 3", "ITV4", "ITV 4", "CHANNEL 4", "CHANNEL 5",
            "SKY SPORTS", "SKY CINEMA UK", "SKY ATLANTIC UK", "TNT SPORTS", "BT SPORT"
        )
        return ukBroadcasters.any { name.contains(it) }
    }
}
