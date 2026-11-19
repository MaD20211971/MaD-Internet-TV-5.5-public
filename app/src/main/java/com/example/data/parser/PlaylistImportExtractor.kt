package com.example.data.parser

import android.net.Uri
import com.example.data.model.PlaylistEntity
import com.example.data.model.PlaylistType
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.net.URI
import java.util.UUID
import java.util.regex.Pattern

data class ImportedPlaylistCandidate(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    val type: PlaylistType,
    var url: String = "",
    var username: String = "",
    var password: String = "",
    var macAddress: String = "",
    var details: String = "",
    var isSelected: Boolean = true,
    var channelCountHint: Int = 0,
    var requiresVpn: Boolean = false,
    var vpnCountry: String = "DE"
) {
    fun toPlaylistEntity(): PlaylistEntity {
        val finalUrl = if (type == PlaylistType.MAC_PORTAL) {
            PlaylistImportExtractor.cleanMacPortalUrl(url)
        } else {
            url.trim()
        }
        val finalMac = if (type == PlaylistType.MAC_PORTAL) {
            PlaylistImportExtractor.cleanMacAddress(macAddress)
        } else {
            ""
        }
        val baseName = name.ifBlank {
            when (type) {
                PlaylistType.XTREAM_CODES -> "Xtream - ${extractDomain(finalUrl)}"
                PlaylistType.MAC_PORTAL -> "MAC - ${extractDomain(finalUrl)}"
                PlaylistType.M3U_URL -> "M3U - ${extractDomain(finalUrl)}"
                PlaylistType.M3U_FILE -> "M3U Import"
            }
        }
        val (autoVpn, autoCountry) = VpnRequirementHelper.detectVpnRequirement(finalUrl, baseName)
        val shouldPrefixVpn = requiresVpn || autoVpn
        val country = if (vpnCountry.isNotBlank()) vpnCountry else autoCountry
        val finalName = VpnRequirementHelper.formatPlaylistNameWithVpn(baseName, shouldPrefixVpn, country)

        return PlaylistEntity(
            id = id,
            name = finalName,
            type = type,
            url = finalUrl,
            username = if (type == PlaylistType.MAC_PORTAL) "" else username.trim(),
            password = if (type == PlaylistType.MAC_PORTAL) "" else password.trim(),
            macAddress = finalMac,
            lastUpdated = 0L,
            isActive = false
        )
    }

    private fun extractDomain(rawUrl: String): String {
        return PlaylistImportExtractor.extractHostFast(rawUrl)
    }
}

object PlaylistImportExtractor {

    // Regex for MAC address: standard 00:1A:79:XX:XX:XX or 00-1A-79-XX-XX-XX
    private val MAC_REGEX = Pattern.compile(
        "\\b([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})\\b",
        Pattern.CASE_INSENSITIVE
    )

    // Regex for HTTP/HTTPS URLs
    private val URL_REGEX = Pattern.compile(
        "https?://[a-zA-Z0-9.-]+(:[0-9]+)?(/[a-zA-Z0-9_.~:/?#\\[\\]@!$&'()*+,;=-]*)?",
        Pattern.CASE_INSENSITIVE
    )

    // Regex for stream path matching: /live/user/pass/..., /movie/user/pass/..., /series/user/pass/...
    private val STREAM_PATH_REGEX = Pattern.compile(
        "/(live|movie|series)/([^/]+)/([^/]+)/",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Cleans a MAC portal URL so that ONLY the portal address and port are returned (e.g. http://portalname.xyz:80)
     * Strips any trailing paths like /c/, /c, /stalker_portal, /get.php, query parameters, etc.
     */
    fun cleanMacPortalUrl(rawUrl: String): String {
        if (rawUrl.isBlank()) return ""
        var s = rawUrl.trim()
        if (!s.startsWith("http://", ignoreCase = true) && !s.startsWith("https://", ignoreCase = true)) {
            s = "http://$s"
        }
        val schemeEnd = s.indexOf("://")
        if (schemeEnd != -1) {
            val scheme = s.substring(0, schemeEnd).lowercase()
            val afterScheme = s.substring(schemeEnd + 3)
            val pathStart = afterScheme.indexOfAny(charArrayOf('/', '?', '#'))
            val authority = (if (pathStart != -1) afterScheme.substring(0, pathStart) else afterScheme).trim()
            if (authority.isNotBlank() && !authority.contains(' ')) {
                return "$scheme://$authority"
            }
        }
        return cleanMacPortalUrlFallback(s)
    }

    private fun cleanMacPortalUrlFallback(s: String): String {
        val beforeSlash = s.substringBefore("?").substringBefore("#")
        val slashIdx = beforeSlash.indexOf("/", 8)
        return if (slashIdx != -1) beforeSlash.substring(0, slashIdx) else beforeSlash.trimEnd('/')
    }

    /**
     * Cleans a MAC address so that ONLY the valid 17-character MAC address (e.g. 00:1A:79:XX:XX:XX) is returned.
     * Strips labels like "MAC:", "mac=", quotes, spaces.
     */
    fun cleanMacAddress(rawMac: String): String {
        if (rawMac.isBlank()) return ""
        val matcher = MAC_REGEX.matcher(rawMac)
        if (matcher.find()) {
            return matcher.group().uppercase().replace("-", ":")
        }
        val stripped = rawMac.replace("mac:", "", ignoreCase = true)
            .replace("mac=", "", ignoreCase = true)
            .replace("\"", "")
            .replace("'", "")
            .trim()
            .uppercase()
            .replace("-", ":")
        val secondTry = MAC_REGEX.matcher(stripped)
        if (secondTry.find()) {
            return secondTry.group().uppercase().replace("-", ":")
        }
        // Support 12 unseparated hex characters e.g. 001A79C7071D
        if (stripped.length == 12 && stripped.all { it.isDigit() || it in 'A'..'F' }) {
            return stripped.chunked(2).joinToString(":")
        }
        return stripped
    }

    /**
     * Finds any MAC address in a string, including URL query params like username=00:1A:79... or mac=00:1A:79...
     */
    fun findMacInString(str: String): String? {
        if (str.isBlank()) return null
        val matcher = MAC_REGEX.matcher(str)
        if (matcher.find()) {
            return matcher.group().uppercase().replace("-", ":")
        }
        val unseparatedMatch = Regex("(?:mac|username|user|user_id|usr)=([0-9a-fA-F]{12})\\b", RegexOption.IGNORE_CASE).find(str)
        if (unseparatedMatch != null) {
            return unseparatedMatch.groupValues[1].chunked(2).joinToString(":").uppercase()
        }
        return null
    }

    fun extractFromStream(
        inputStream: InputStream,
        defaultNamePrefix: String = "Import",
        isDirectM3uContent: Boolean = false
    ): List<ImportedPlaylistCandidate> {
        val buffered = java.io.BufferedInputStream(inputStream)
        buffered.mark(2)
        val b1 = buffered.read()
        val b2 = buffered.read()
        buffered.reset()
        val effectiveStream = if (b1 == 0x1f && b2 == 0x8b) {
            java.util.zip.GZIPInputStream(buffered)
        } else {
            buffered
        }
        return extractFromReader(
            reader = BufferedReader(InputStreamReader(effectiveStream, Charsets.UTF_8)),
            defaultNamePrefix = defaultNamePrefix,
            isDirectM3uContent = isDirectM3uContent
        )
    }

    fun extractFromReader(
        reader: Reader,
        defaultNamePrefix: String = "Import",
        isDirectM3uContent: Boolean = false
    ): List<ImportedPlaylistCandidate> {
        val bufferedReader = if (reader is BufferedReader) reader else BufferedReader(reader)
        val candidates = mutableListOf<ImportedPlaylistCandidate>()

        var extinfCount = 0
        var hasExtM3uHeader = false
        val nonM3uLines = ArrayList<String>(2048)
        val maxNonM3uLines = 25000 // Supports 1,000+ multi-line MAC/Xtream account blocks without truncation

        try {
            bufferedReader.forEachLine { rawLine ->
                val line = rawLine.trim()
                if (line.isNotEmpty()) {
                    if (line.startsWith("#EXTINF", ignoreCase = true)) {
                        extinfCount++
                    } else if (line.startsWith("#EXTM3U", ignoreCase = true)) {
                        hasExtM3uHeader = true
                    } else if (!line.startsWith("#")) {
                        if (nonM3uLines.size < maxNonM3uLines) {
                            nonM3uLines.add(line)
                        }
                    } else {
                        // Other comments or metadata lines that might contain MAC/Xtream credentials
                        if (nonM3uLines.size < maxNonM3uLines) {
                            nonM3uLines.add(line)
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 1. Check if the text is a direct M3U / M3U_PLUS file
        if (isDirectM3uContent || hasExtM3uHeader || extinfCount >= 2) {
            candidates.add(
                ImportedPlaylistCandidate(
                    name = "$defaultNamePrefix (M3U - $extinfCount Sender)",
                    type = PlaylistType.M3U_FILE,
                    url = "raw_m3u_text",
                    details = "Vollständige M3U-Playlist mit $extinfCount erkannten Sendern",
                    channelCountHint = extinfCount
                )
            )
        }

        // 2. Parse MAC blocks & single lines
        try {
            val foundMacBlocks = extractMacPortalBlocks(nonM3uLines)
            candidates.addAll(foundMacBlocks)
        } catch (_: Exception) {}

        // 3. Parse Xtream blocks & single lines
        try {
            val foundXtreamBlocks = extractXtreamBlocks(nonM3uLines)
            candidates.addAll(foundXtreamBlocks)
        } catch (_: Exception) {}

        // 4. Parse from URLs, M3U links, Stream links
        try {
            val foundUrlAccounts = extractFromUrlsAndLinks(nonM3uLines)
            candidates.addAll(foundUrlAccounts)
        } catch (_: Exception) {}

        // 5. Deduplicate and prioritize clean MAC Portals
        val distinctList = mutableListOf<ImportedPlaylistCandidate>()
        val signatures = mutableSetOf<String>()
        val macHosts = mutableSetOf<String>()

        for (cand in candidates) {
            if (cand.type == PlaylistType.MAC_PORTAL) {
                val host = extractHost(cand.url).lowercase()
                val mac = cand.macAddress.uppercase()
                macHosts.add("$host|$mac")
            }
        }

        for (cand in candidates) {
            if (cand.type != PlaylistType.MAC_PORTAL) {
                val host = extractHost(cand.url).lowercase()
                val userMac = findMacInString(cand.username) ?: findMacInString(cand.url)
                if (userMac != null && macHosts.contains("$host|$userMac")) {
                    continue
                }
            }

            val sig = "${cand.type}|${cand.url.lowercase().trim()}|${cand.username.lowercase().trim()}|${cand.macAddress.uppercase().trim()}"
            if (sig !in signatures) {
                signatures.add(sig)
                if (cand.name.isBlank()) {
                    cand.name = generateSensibleName(cand)
                }
                distinctList.add(cand)
            }
        }

        return distinctList
    }

    fun extractFromText(rawText: String, defaultNamePrefix: String = "Import"): List<ImportedPlaylistCandidate> {
        if (rawText.isBlank()) return emptyList()
        return extractFromReader(
            reader = java.io.StringReader(rawText),
            defaultNamePrefix = defaultNamePrefix,
            isDirectM3uContent = rawText.contains("#EXTM3U", ignoreCase = true)
        )
    }

    /**
     * Extracts MAC / Stalker Portals from line blocks & single lines
     * e.g.
     * URL: http://portal.xyz:8080/c/
     * MAC: 00:1A:79:34:56:78
     * or: http://portal.xyz:8080/c/ | 00:1A:79:34:56:78
     * or M3U link with MAC: http://portal.xyz:80/get.php?username=00:1A:79:XX:XX:XX...
     */
    private fun extractMacPortalBlocks(lines: List<String>): List<ImportedPlaylistCandidate> {
        val result = mutableListOf<ImportedPlaylistCandidate>()

        var currentUrl: String? = null
        var currentMac: String? = null
        var currentName: String? = null

        fun checkAndAdd() {
            if (!currentUrl.isNullOrBlank() && !currentMac.isNullOrBlank()) {
                val cleanUrl = cleanMacPortalUrl(currentUrl!!)
                val cleanMac = cleanMacAddress(currentMac!!)
                if (cleanUrl.isNotBlank() && cleanMac.isNotBlank()) {
                    val domain = extractHost(cleanUrl)
                    val name = if (!currentName.isNullOrBlank()) {
                        if (currentName!!.contains("MAC Portal", ignoreCase = true)) currentName!! else "$currentName - MAC Portal"
                    } else {
                        "$domain - MAC Portal"
                    }
                    result.add(
                        ImportedPlaylistCandidate(
                            name = name,
                            type = PlaylistType.MAC_PORTAL,
                            url = cleanUrl,
                            macAddress = cleanMac,
                            details = "Portal: $cleanUrl | MAC: $cleanMac"
                        )
                    )
                }
            }
            currentUrl = null
            currentMac = null
            currentName = null
        }

        for (line in lines) {
            if (line.isBlank() || line.startsWith("----") || line.startsWith("====") || line.startsWith("####")) {
                checkAndAdd()
                continue
            }

            // Check if single line has both URL and MAC (or M3U link with MAC)
            val foundMacInLine = findMacInString(line)
            val urlMatcher = URL_REGEX.matcher(line)

            if (foundMacInLine != null && urlMatcher.find()) {
                val foundUrl = cleanMacPortalUrl(urlMatcher.group())
                val domain = extractHost(foundUrl)
                result.add(
                    ImportedPlaylistCandidate(
                        name = "$domain - MAC Portal",
                        type = PlaylistType.MAC_PORTAL,
                        url = foundUrl,
                        macAddress = foundMacInLine,
                        details = "Portal: $foundUrl | MAC: $foundMacInLine"
                    )
                )
                continue
            }

            // Check key-value lines
            val lower = line.lowercase()
            when {
                lower.startsWith("url:") || lower.startsWith("portal:") || lower.startsWith("host:") || lower.startsWith("server:") || lower.startsWith("dns:") -> {
                    val matcher = URL_REGEX.matcher(line)
                    if (matcher.find()) {
                        currentUrl = matcher.group()
                    }
                }
                lower.startsWith("mac:") || lower.startsWith("mac address:") || lower.startsWith("mac-adresse:") || lower.startsWith("mac_address:") || lower.startsWith("mac =") -> {
                    currentMac = findMacInString(line)
                }
                lower.startsWith("name:") || lower.startsWith("title:") || lower.startsWith("playlist:") -> {
                    val colonIdx = line.indexOf(':')
                    if (colonIdx != -1 && colonIdx < line.length - 1) {
                        currentName = line.substring(colonIdx + 1).trim()
                    }
                }
                else -> {
                    val macMatch = findMacInString(line)
                    val urlMatch = URL_REGEX.matcher(line)
                    if (macMatch != null) {
                        currentMac = macMatch
                    } else if (urlMatch.find() && (lower.contains("/c") || lower.contains("stalker") || lower.contains("portal"))) {
                        currentUrl = urlMatch.group()
                    }
                }
            }

            if (currentUrl != null && currentMac != null) {
                checkAndAdd()
            }
        }

        checkAndAdd()
        return result
    }

    /**
     * Extracts Xtream Codes accounts from blocks (URL, Username, Password)
     * and single-line combo formats. If username/password is a MAC address, converts to MAC Portal.
     */
    private fun extractXtreamBlocks(lines: List<String>): List<ImportedPlaylistCandidate> {
        val result = mutableListOf<ImportedPlaylistCandidate>()

        var currentServer: String? = null
        var currentUser: String? = null
        var currentPass: String? = null
        var currentName: String? = null

        fun checkAndAdd() {
            if (!currentServer.isNullOrBlank() && !currentUser.isNullOrBlank() && !currentPass.isNullOrBlank()) {
                val macInXtream = findMacInString(currentUser!!) ?: findMacInString(currentPass!!) ?: findMacInString(currentServer!!)
                if (macInXtream != null) {
                    val cleanPortal = cleanMacPortalUrl(currentServer!!)
                    val domain = extractHost(cleanPortal)
                    val name = if (!currentName.isNullOrBlank()) {
                        if (currentName!!.contains("MAC Portal", ignoreCase = true)) currentName!! else "$currentName - MAC Portal"
                    } else {
                        "$domain - MAC Portal"
                    }
                    result.add(
                        ImportedPlaylistCandidate(
                            name = name,
                            type = PlaylistType.MAC_PORTAL,
                            url = cleanPortal,
                            macAddress = macInXtream,
                            details = "MAC-Portal erkannt (Portal: $cleanPortal | MAC: $macInXtream)"
                        )
                    )
                } else {
                    val cleanServer = cleanServerUrl(currentServer!!)
                    val domain = extractHost(cleanServer)
                    val name = currentName ?: "Xtream - $domain"
                    result.add(
                        ImportedPlaylistCandidate(
                            name = name,
                            type = PlaylistType.XTREAM_CODES,
                            url = cleanServer,
                            username = currentUser!!,
                            password = currentPass!!,
                            details = "Host: $cleanServer | User: $currentUser"
                        )
                    )
                }
            }
            currentServer = null
            currentUser = null
            currentPass = null
            currentName = null
        }

        for (line in lines) {
            if (line.isBlank() || line.startsWith("----") || line.startsWith("====") || line.startsWith("####")) {
                checkAndAdd()
                continue
            }

            // Pipe, Semicolon, Comma, or Space separated on one line: http://server:port | username | password
            val delimiters = listOf("|", ";", ",", "\t")
            for (delim in delimiters) {
                if (line.contains(delim)) {
                    val parts = line.split(delim).map { it.trim() }.filter { it.isNotEmpty() }
                    if (parts.size >= 3) {
                        val urlMatch = URL_REGEX.matcher(parts[0])
                        if (urlMatch.find()) {
                            val rawUrl = urlMatch.group()
                            val u = parts[1].replace("user:", "", ignoreCase = true).replace("username:", "", ignoreCase = true).trim()
                            val p = parts[2].replace("pass:", "", ignoreCase = true).replace("password:", "", ignoreCase = true).trim()

                            val macDetected = findMacInString(u) ?: findMacInString(p) ?: findMacInString(rawUrl)
                            if (macDetected != null) {
                                val srv = cleanMacPortalUrl(rawUrl)
                                val dom = extractHost(srv)
                                result.add(
                                    ImportedPlaylistCandidate(
                                        name = "$dom - MAC Portal",
                                        type = PlaylistType.MAC_PORTAL,
                                        url = srv,
                                        macAddress = macDetected,
                                        details = "Portal: $srv | MAC: $macDetected"
                                    )
                                )
                                continue
                            }

                            if (u.isNotBlank() && p.isNotBlank() && !u.contains(" ") && !p.contains(" ")) {
                                val srv = cleanServerUrl(rawUrl)
                                val dom = extractHost(srv)
                                result.add(
                                    ImportedPlaylistCandidate(
                                        name = "Xtream - $dom",
                                        type = PlaylistType.XTREAM_CODES,
                                        url = srv,
                                        username = u,
                                        password = p,
                                        details = "Host: $srv | User: $u"
                                    )
                                )
                                continue
                            }
                        }
                    }
                }
            }

            val lower = line.lowercase()
            when {
                lower.startsWith("url:") || lower.startsWith("host:") || lower.startsWith("server:") || lower.startsWith("dns:") || lower.startsWith("portal:") -> {
                    val matcher = URL_REGEX.matcher(line)
                    if (matcher.find()) {
                        currentServer = matcher.group()
                    }
                }
                lower.startsWith("username:") || lower.startsWith("user:") || lower.startsWith("benutzer:") || lower.startsWith("login:") || lower.startsWith("account:") || lower.startsWith("usr:") -> {
                    val colonIdx = line.indexOf(':')
                    if (colonIdx != -1 && colonIdx < line.length - 1) {
                        currentUser = line.substring(colonIdx + 1).trim()
                    }
                }
                lower.startsWith("password:") || lower.startsWith("pass:") || lower.startsWith("passwort:") || lower.startsWith("pw:") || lower.startsWith("kennwort:") || lower.startsWith("pwd:") -> {
                    val colonIdx = line.indexOf(':')
                    if (colonIdx != -1 && colonIdx < line.length - 1) {
                        currentPass = line.substring(colonIdx + 1).trim()
                    }
                }
                lower.startsWith("name:") || lower.startsWith("title:") || lower.startsWith("playlist:") -> {
                    val colonIdx = line.indexOf(':')
                    if (colonIdx != -1 && colonIdx < line.length - 1) {
                        currentName = line.substring(colonIdx + 1).trim()
                    }
                }
                else -> {
                    // Check if line is a plain URL
                    val urlMatch = URL_REGEX.matcher(line)
                    if (urlMatch.find() && currentServer == null && !line.contains(".m3u", ignoreCase = true)) {
                        currentServer = urlMatch.group()
                    }
                }
            }

            if (currentServer != null && currentUser != null && currentPass != null) {
                checkAndAdd()
            }
        }

        checkAndAdd()
        return result
    }

    /**
     * Extracts Xtream and M3U links embedded in direct URLs and stream paths.
     * If an M3U link contains a MAC address in query parameters or line, it is isolated
     * as a MAC Portal with clean portal:port URL and MAC address.
     */
    private fun extractFromUrlsAndLinks(lines: List<String>): List<ImportedPlaylistCandidate> {
        val result = mutableListOf<ImportedPlaylistCandidate>()

        for (line in lines) {
            val matcher = URL_REGEX.matcher(line)
            while (matcher.find()) {
                val fullUrl = matcher.group().trim()

                // Check if this URL or line contains a MAC address (e.g. generated M3U link with MAC)
                val macInUrlOrLine = findMacInString(fullUrl) ?: findMacInString(line)
                if (macInUrlOrLine != null) {
                    val portalUrl = cleanMacPortalUrl(fullUrl)
                    val dom = extractHost(portalUrl)
                    result.add(
                        ImportedPlaylistCandidate(
                            name = "$dom - MAC Portal",
                            type = PlaylistType.MAC_PORTAL,
                            url = portalUrl,
                            macAddress = macInUrlOrLine,
                            details = "MAC-Portal erkannt (M3U-Link in Portal & MAC bereinigt)"
                        )
                    )
                    continue
                }

                // Check for Xtream parameters inside URL (?username=...&password=...)
                if (fullUrl.contains("username=", ignoreCase = true) && fullUrl.contains("password=", ignoreCase = true)) {
                    val (server, user, pass) = parseXtreamUrlParams(fullUrl)
                    if (user.isNotBlank() && pass.isNotBlank()) {
                        val dom = extractHost(server)
                        result.add(
                            ImportedPlaylistCandidate(
                                name = "Xtream - $dom",
                                type = PlaylistType.XTREAM_CODES,
                                url = server,
                                username = user,
                                password = pass,
                                details = "Xtream Konto erkannt (Server: $server)"
                            )
                        )
                    } else {
                        val dom = extractHost(fullUrl)
                        result.add(
                            ImportedPlaylistCandidate(
                                name = "M3U - $dom",
                                type = PlaylistType.M3U_URL,
                                url = fullUrl,
                                details = "M3U Web-URL mit Authentifizierung"
                            )
                        )
                    }
                } else {
                    // Check for stream path pattern: /live/user/pass/... or /movie/user/pass/...
                    val streamMatcher = STREAM_PATH_REGEX.matcher(fullUrl)
                    if (streamMatcher.find()) {
                        val user = streamMatcher.group(2)
                        val pass = streamMatcher.group(3)
                        val server = extractServerBase(fullUrl)
                        if (!user.isNullOrBlank() && !pass.isNullOrBlank() && !server.isNullOrBlank()) {
                            val dom = extractHost(server)
                            result.add(
                                ImportedPlaylistCandidate(
                                    name = "Xtream - $dom",
                                    type = PlaylistType.XTREAM_CODES,
                                    url = server,
                                    username = user,
                                    password = pass,
                                    details = "Xtream Zugangsdaten aus Stream-Link extrahiert"
                                )
                            )
                            continue
                        }
                    }

                    // Direct M3U Web Link
                    if (fullUrl.contains(".m3u", ignoreCase = true) || fullUrl.contains(".m3u8", ignoreCase = true) || fullUrl.contains("playlist", ignoreCase = true)) {
                        val dom = extractHost(fullUrl)
                        result.add(
                            ImportedPlaylistCandidate(
                                name = "M3U - $dom",
                                type = PlaylistType.M3U_URL,
                                url = fullUrl,
                                details = "Direkter M3U/M3U8 Web-Stream-Link"
                            )
                        )
                    }
                }
            }
        }

        return result.map { enrichCandidate(it) }
    }

    fun enrichCandidate(candidate: ImportedPlaylistCandidate): ImportedPlaylistCandidate {
        val (autoVpn, autoCountry) = VpnRequirementHelper.detectVpnRequirement(candidate.url, candidate.name)
        if (autoVpn) {
            candidate.requiresVpn = true
            candidate.vpnCountry = autoCountry
            candidate.name = VpnRequirementHelper.formatPlaylistNameWithVpn(
                name = candidate.name,
                requiresVpn = true,
                countryCode = autoCountry
            )
        }
        return candidate
    }

    private fun extractServerBase(streamUrl: String): String {
        return try {
            val uri = URI(streamUrl)
            val scheme = uri.scheme ?: "http"
            val host = uri.host ?: return ""
            val port = if (uri.port != -1) ":${uri.port}" else ""
            "$scheme://$host$port"
        } catch (_: Exception) {
            ""
        }
    }

    private fun parseXtreamUrlParams(url: String): Triple<String, String, String> {
        return try {
            val uri = Uri.parse(url)
            val user = uri.getQueryParameter("username") ?: uri.getQueryParameter("user") ?: ""
            val pass = uri.getQueryParameter("password") ?: uri.getQueryParameter("pass") ?: uri.getQueryParameter("pw") ?: ""
            val host = uri.host ?: ""
            val scheme = uri.scheme ?: "http"
            val port = if (uri.port != -1) ":${uri.port}" else ""
            val server = "$scheme://$host$port"
            Triple(server, user, pass)
        } catch (_: Exception) {
            Triple(url, "", "")
        }
    }

    private fun cleanServerUrl(url: String): String {
        var clean = url.trim()
        if (!clean.startsWith("http://", ignoreCase = true) && !clean.startsWith("https://", ignoreCase = true)) {
            clean = "http://$clean"
        }
        val endPoints = listOf("/player_api.php", "/get.php", "/c/", "/c", "/live", "/series", "/movie")
        for (ep in endPoints) {
            if (clean.endsWith(ep, ignoreCase = true)) {
                clean = clean.substring(0, clean.length - ep.length)
            }
        }
        return clean.trimEnd('/')
    }

    internal fun extractHostFast(rawUrl: String): String {
        if (rawUrl.isBlank()) return ""
        val trimmed = rawUrl.trim()
        val startIdx = when {
            trimmed.startsWith("http://", ignoreCase = true) -> 7
            trimmed.startsWith("https://", ignoreCase = true) -> 8
            else -> 0
        }
        val sub = trimmed.substring(startIdx)
        val endIdx = sub.indexOfAny(charArrayOf('/', ':', '?', '#'))
        val host = (if (endIdx != -1) sub.substring(0, endIdx) else sub).trim()
        return host.ifBlank { trimmed.take(20) }
    }

    private fun extractHost(rawUrl: String): String = extractHostFast(rawUrl)

    private fun generateSensibleName(cand: ImportedPlaylistCandidate): String {
        val dom = extractHost(cand.url)
        return when (cand.type) {
            PlaylistType.XTREAM_CODES -> "Xtream - $dom"
            PlaylistType.MAC_PORTAL -> "MAC - $dom"
            PlaylistType.M3U_URL -> "M3U - $dom"
            PlaylistType.M3U_FILE -> "M3U Datei"
        }
    }
}
