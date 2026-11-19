package com.example.data.parser

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import com.example.data.model.ChannelItem

data class GeoCheckResult(
    val requiresVpn: Boolean,
    val countryCode: String,
    val countryName: String,
    val reason: String = ""
)

object VpnRequirementHelper {

    val POPULAR_VPN_COUNTRIES = listOf(
        "DE" to "Deutschland",
        "AT" to "Österreich",
        "CH" to "Schweiz",
        "UK" to "Großbritannien",
        "US" to "USA",
        "FR" to "Frankreich",
        "IT" to "Italien",
        "ES" to "Spanien",
        "NL" to "Niederlande",
        "TR" to "Türkei",
        "PL" to "Polen",
        "AL" to "Albanien",
        "RO" to "Rumänien"
    )

    val COUNTRY_CODES = POPULAR_VPN_COUNTRIES.map { it.first }

    /**
     * Extracts existing leading country code from a playlist name, e.g.:
     * "[DE] OnAir8K Portal" -> "DE"
     * "[AT] Servus TV" -> "AT"
     * "CH - Swiss Live" -> "CH"
     */
    fun extractCountryCode(name: String?): String? {
        if (name.isNullOrBlank()) return null
        val trimmed = name.trim()
        val bracketMatch = Regex("^\\[([A-Za-z]{2,4})\\]").find(trimmed)
        if (bracketMatch != null) {
            return bracketMatch.groupValues[1].uppercase()
        }
        val prefixMatch = Regex("^([A-Za-z]{2,4})\\s*[-–:]").find(trimmed)
        if (prefixMatch != null) {
            val code = prefixMatch.groupValues[1].uppercase()
            if (COUNTRY_CODES.contains(code) || code.length == 2) {
                return code
            }
        }
        return null
    }

    /**
     * Returns the base playlist name without any leading country code prefix.
     */
    fun getCleanBaseName(name: String?): String {
        if (name.isNullOrBlank()) return ""
        return name.trim()
            .replace(Regex("^\\[[A-Za-z]{2,4}\\]\\s*"), "")
            .replace(Regex("^[A-Za-z]{2,4}\\s*[-–:]\\s*"), "")
            .trim()
    }

    /**
     * Formats a playlist name with a country prefix if VPN is required.
     * When VPN is required, sets the country code (e.g. "[DE]", "[AT]", "[CH]") at the beginning of the name.
     * If VPN is not required, strips leading brackets or keeps base name.
     */
    fun formatPlaylistNameWithVpn(
        name: String,
        requiresVpn: Boolean,
        countryCode: String = "DE"
    ): String {
        val baseName = getCleanBaseName(name).ifBlank { "Playliste" }
        if (!requiresVpn) {
            return baseName
        }
        val cleanCountry = countryCode.uppercase().trim().ifBlank { "DE" }
        return "[$cleanCountry] $baseName"
    }

    /**
     * Performs a comprehensive Geo-Check on a playlist based on:
     * 1. URL / Domain indicators (e.g. vpn.onair8k.xyz, .at, .ch, .de, geoblock, vpn-*, etc.)
     * 2. Existing Name indicators
     * 3. Channel contents & group categories (e.g. ORF/ServusTV -> AT, SRF -> CH, ARD/ZDF -> DE, BBC -> UK)
     * 4. Connection / HTTP error codes (403 Forbidden, 451 Unavailable for Legal Reasons, 456 Geo-Blocked)
     */
    fun performGeoCheck(
        url: String,
        name: String = "",
        channels: List<ChannelItem> = emptyList(),
        statusCode: Int? = null,
        errorMessage: String? = null
    ): GeoCheckResult {
        val combined = "$url $name".lowercase()

        // 1. Check HTTP response / errors for geo-restriction signatures
        val isGeoHttpError = statusCode == 456 ||
                statusCode == 451 ||
                statusCode == 403 ||
                (errorMessage?.contains("geo", ignoreCase = true) == true) ||
                (errorMessage?.contains("region", ignoreCase = true) == true) ||
                (errorMessage?.contains("standort", ignoreCase = true) == true) ||
                (errorMessage?.contains("vpn", ignoreCase = true) == true) ||
                (errorMessage?.contains("blocked", ignoreCase = true) == true)

        // 2. Check URL & Host indicators
        val isVpnDomainOrKeyword = combined.contains("vpn.") ||
                combined.contains(".vpn") ||
                combined.contains("onair8k") ||
                combined.contains("geoblock") ||
                combined.contains("vpn-") ||
                combined.contains("stalker_vpn") ||
                combined.contains("/c/vpn")

        // 3. Channel content and group distribution analysis
        var detectedFromChannels: String? = null
        var isGeoBroadcasterFound = false
        if (channels.isNotEmpty()) {
            var atCount = 0
            var chCount = 0
            var deCount = 0
            var ukCount = 0
            var usCount = 0
            var itCount = 0
            var frCount = 0
            var esCount = 0
            var trCount = 0

            for (ch in channels) {
                val chName = ch.name.lowercase()
                val group = ch.groupTitle.lowercase()

                // High-confidence geo-blocked public & private regional broadcasters
                val isAtBroadcaster = chName.contains("orf") || chName.contains("servustv") || chName.contains("atv") || chName.contains("puls 4") || group.contains("austria") || group.contains("österreich")
                val isChBroadcaster = chName.contains("srf") || chName.contains("rts") || chName.contains("rsi") || chName.contains("telezüri") || group.contains("swiss") || group.contains("schweiz")
                val isDeBroadcaster = chName.contains("ard") || chName.contains("zdf") || chName.contains("wdr") || chName.contains("ndr") || chName.contains("br fernsehen") || group.contains("deutschland") || group.contains("germany")
                val isUkBroadcaster = chName.contains("bbc") || chName.contains("itv") || chName.contains("channel 4") || group.contains("united kingdom") || group.contains("uk")

                if (isAtBroadcaster) { atCount++; isGeoBroadcasterFound = true }
                if (isChBroadcaster) { chCount++; isGeoBroadcasterFound = true }
                if (isDeBroadcaster) { deCount++ }
                if (isUkBroadcaster) { ukCount++; isGeoBroadcasterFound = true }
                if (group.contains("usa") || group.contains("united states")) usCount++
                if (group.contains("italy") || group.contains("italia")) itCount++
                if (group.contains("france")) frCount++
                if (group.contains("spain") || group.contains("espana")) esCount++
                if (group.contains("turkey") || group.contains("türkiye")) trCount++
            }

            val maxCountry = listOf(
                "AT" to atCount,
                "CH" to chCount,
                "DE" to deCount,
                "UK" to ukCount,
                "US" to usCount,
                "IT" to itCount,
                "FR" to frCount,
                "ES" to esCount,
                "TR" to trCount
            ).maxByOrNull { it.second }

            if (maxCountry != null && maxCountry.second > 0) {
                detectedFromChannels = maxCountry.first
            }
        }

        // Determine country code
        val existingCode = extractCountryCode(name)
        val detectedCountry = when {
            existingCode != null -> existingCode
            combined.contains("vpn-at") || combined.contains("austria") || combined.contains("österreich") || combined.contains("orf") || combined.contains(".at/") || combined.contains(".at:") -> "AT"
            combined.contains("vpn-ch") || combined.contains("swiss") || combined.contains("schweiz") || combined.contains("srf") || combined.contains(".ch/") || combined.contains(".ch:") -> "CH"
            combined.contains("vpn-de") || combined.contains("germany") || combined.contains("deutschland") || combined.contains(".de/") || combined.contains(".de:") -> "DE"
            combined.contains("vpn-uk") || combined.contains("bbc") || combined.contains("uk.") || combined.contains(".co.uk") -> "UK"
            combined.contains("vpn-us") || combined.contains("usa") -> "US"
            combined.contains("vpn-fr") || combined.contains("france") -> "FR"
            combined.contains("vpn-it") || combined.contains("italy") || combined.contains("italia") -> "IT"
            combined.contains("vpn-es") || combined.contains("spain") -> "ES"
            combined.contains("vpn-tr") || combined.contains("turkey") -> "TR"
            combined.contains("vpn-pl") || combined.contains("poland") -> "PL"
            combined.contains("vpn-nl") || combined.contains("netherlands") -> "NL"
            detectedFromChannels != null -> detectedFromChannels
            else -> "DE"
        }

        val requiresVpn = isGeoHttpError ||
                isVpnDomainOrKeyword ||
                (isGeoBroadcasterFound && (detectedCountry == "AT" || detectedCountry == "CH" || detectedCountry == "UK")) ||
                existingCode != null

        val countryName = getCountryDisplayName(detectedCountry) ?: "Deutschland"
        val reason = when {
            isGeoHttpError -> "Geo-Sperre / Server-Zugriffsbeschränkung erkannt (Status $statusCode)"
            isVpnDomainOrKeyword -> "VPN-Gateway im Portal/Server erkannt"
            isGeoBroadcasterFound -> "Länderspezifische Sender mit Geo-Restriktion ($countryName [$detectedCountry])"
            existingCode != null -> "Vom Nutzer zugewiesene Länderkennung ($existingCode)"
            else -> "Standard-Verbindung"
        }

        return GeoCheckResult(
            requiresVpn = requiresVpn,
            countryCode = detectedCountry,
            countryName = countryName,
            reason = reason
        )
    }

    /**
     * Automatically detects if a playlist URL or name indicates that a VPN is needed
     * and suggests the appropriate country code.
     */
    fun detectVpnRequirement(url: String, name: String = ""): Pair<Boolean, String> {
        val result = performGeoCheck(url, name)
        return Pair(result.requiresVpn, result.countryCode)
    }

    fun getCountryDisplayName(countryCode: String?): String? {
        if (countryCode.isNullOrBlank()) return null
        return when (countryCode.uppercase().trim()) {
            "DE" -> "Deutschland"
            "AT" -> "Österreich"
            "CH" -> "Schweiz"
            "UK", "GB" -> "Großbritannien"
            "US", "USA" -> "USA"
            "IT" -> "Italien"
            "FR" -> "Frankreich"
            "ES" -> "Spanien"
            "TR" -> "Türkei"
            "PL" -> "Polen"
            "NL" -> "Niederlande"
            "AL" -> "Albanien"
            "AR" -> "den arabischen Raum"
            "RU" -> "Russland"
            "PT" -> "Portugal"
            "BR" -> "Brasilien"
            "GR" -> "Griechenland"
            "RO" -> "Rumänien"
            "SE", "SWE", "NO", "DK", "FI" -> "Skandinavien"
            "CA" -> "Kanada"
            "IN" -> "Indien"
            "AF" -> "Afghanistan"
            "EXYU" -> "Ex-Jugoslawien"
            else -> null
        }
    }

    fun formatStatusErrorMessage(
        statusCode: Int,
        channelName: String? = null,
        countryCode: String? = null,
        url: String = ""
    ): String {
        val countryName = getCountryDisplayName(countryCode)
        val channelLabel = if (!channelName.isNullOrBlank()) " für „$channelName“" else ""

        return when (statusCode) {
            456 -> {
                if (countryName != null) {
                    "Wiedergabe gesperrt (Status 456)$channelLabel:\n" +
                    "Der Stream-Server blockiert den Zugriff.\n" +
                    "• Erforderlich: IP-Standort in $countryName (Länder-VPN nach $countryName).\n" +
                    "• Falls Ihr VPN bereits aktiv ist: Manche IPTV-Server erkennen und sperren Rechenzentrums-IPs von VPN-Anbietern oder verlangen eine private IP.\n" +
                    "• Im Browser-Emulator: Der Emulator läuft in der Cloud und nutzt nicht Ihr lokales PC-VPN (APK auf echtem Gerät installieren).\n" +
                    "• Möglicherweise vorübergehende IP-Sperre oder Verbindungslimit."
                } else {
                    "Wiedergabe gesperrt (Status 456)$channelLabel:\n" +
                    "Der Stream-Server verweigert die Verbindung.\n" +
                    "• Mögliche länderspezifische Geo-Sperre (passendes Länder-VPN erforderlich).\n" +
                    "• Falls ein VPN aktiv ist: Der Server sperrt möglicherweise die IP-Adresse des VPN-Dienstes oder den Cloud-Emulator.\n" +
                    "• Möglicherweise vorübergehende IP-Sperre oder Verbindungslimit des Anbieters."
                }
            }
            429 -> {
                "Server überlastet (Status 429 / Too Many Requests):\n" +
                "Der Portal-Server verarbeitet derzeit zu viele gleichzeitige Anfragen. Bitte wenige Sekunden warten."
            }
            401, 403 -> {
                if (countryName != null) {
                    "Zugriff verweigert (Status $statusCode)$channelLabel:\n" +
                    "Keine Autorisierung für diesen Stream. Möglicherweise ist ein VPN nach $countryName erforderlich oder dieser Sender ist im Portal-Paket nicht freigeschaltet."
                } else {
                    "Zugriff verweigert (Status $statusCode)$channelLabel:\n" +
                    "Keine Autorisierung für diesen Stream. Möglicherweise Länder-Sperre oder Sender im Paket nicht freigeschaltet."
                }
            }
            404 -> {
                "Sender nicht erreichbar (Status 404)$channelLabel:\n" +
                "Der Stream-Endpunkt ist auf dem Server derzeit nicht auffindbar."
            }
            500, 502, 503, 504 -> {
                "Serverfehler (Status $statusCode)$channelLabel:\n" +
                "Der Streaming-Server des Portals meldet eine vorübergehende Betriebsstörung."
            }
            else -> {
                "Server-Fehler (Status $statusCode)$channelLabel:\n" +
                "Die Wiedergabe wurde vom Server mit Fehlercode $statusCode abgelehnt."
            }
        }
    }

    fun detectPortalOccupancyOrBlock(
        error: PlaybackException,
        url: String = "",
        channelName: String? = null,
        countryCode: String? = null
    ): Pair<Boolean, String?> {
        val isBadHttpStatus = error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                error.errorCodeName.contains("BAD_HTTP_STATUS", ignoreCase = true) ||
                error.message?.contains("bad http status", ignoreCase = true) == true

        var detectedCode: Int? = null
        var responseBodyString = ""

        var cause: Throwable? = error
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) {
                detectedCode = cause.responseCode
                try {
                    cause.responseBody?.let { responseBodyString = String(it, Charsets.UTF_8) }
                } catch (e: Exception) { "" }
                break
            }
            val msg = cause.message ?: ""
            val matchedCode = Regex("(?:Response code|status|code):?\\s*(\\d{3})", RegexOption.IGNORE_CASE)
                .find(msg)?.groupValues?.get(1)?.toIntOrNull()
            if (matchedCode != null && detectedCode == null) {
                detectedCode = matchedCode
            }
            cause = cause.cause
        }

        val lowerBody = responseBodyString.lowercase()

        // 1. Explicit Stalker / Portal Occupied detection (verified device concurrency / limit)
        val isExplicitOccupied = lowerBody.contains("already in use") ||
                lowerBody.contains("already_in_use") ||
                lowerBody.contains("device is already in use") ||
                lowerBody.contains("mac address is already in use") ||
                lowerBody.contains("maximum active") ||
                lowerBody.contains("max connections") ||
                lowerBody.contains("max_connections") ||
                lowerBody.contains("limit reached") ||
                lowerBody.contains("limit_exceeded") ||
                lowerBody.contains("connection limit") ||
                (detectedCode == 409)

        if (isExplicitOccupied) {
            val msg = if (lowerBody.contains("limit")) {
                "Portal belegt: Das maximale Verbindungslimit für diesen Zugang wurde erreicht."
            } else {
                "Portal belegt: Diese MAC-Adresse wird derzeit bereits auf einem anderen Gerät genutzt."
            }
            return Pair(true, msg)
        }

        // 2. HTTP Status 456, 429, 403, 401, etc.
        val codeToReport = detectedCode ?: if (isBadHttpStatus) 456 else null
        if (codeToReport != null) {
            val msg = formatStatusErrorMessage(
                statusCode = codeToReport,
                channelName = channelName,
                countryCode = countryCode,
                url = url
            )
            return Pair(true, msg)
        }

        return Pair(false, null)
    }
}
