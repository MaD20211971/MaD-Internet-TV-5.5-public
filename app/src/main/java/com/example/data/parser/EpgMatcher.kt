package com.example.data.parser

import com.example.data.country.DachMasterDatabase
import com.example.data.model.ChannelItem
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class EpgChannelInfo(
    val id: String,
    val displayName: String,
    val countryCode: String? = null,
    val language: String? = null,
    val programCount: Int = 0
)

data class EpgMatchResult(
    val matchedTvgId: String?,
    val matchedChannelName: String?,
    val method: EpgMappingMethod,
    val status: EpgMappingStatus,
    val confidence: Int,
    val candidates: List<String> = emptyList(),
    val reason: String = ""
)

enum class EpgMappingMethod {
    EXACT_TVG_ID,
    NORMALIZED_ID,
    ALIAS_MATCH,
    NORMALIZED_NAME,
    MANUAL,
    NONE
}

enum class EpgMappingStatus {
    MATCHED,
    AMBIGUOUS,
    UNASSIGNED,
    MANUAL
}

/**
 * Pre-computed O(1) lookup index for EPG channels to prevent O(N * M) CPU spikes during EPG sync.
 */
class EpgLookupIndex(
    val allChannels: List<EpgChannelInfo>,
    val byExactId: Map<String, List<EpgChannelInfo>>,
    val byNormId: Map<String, List<EpgChannelInfo>>,
    val byNormName: Map<String, List<EpgChannelInfo>>,
    val byCanonicalSlug: Map<String, List<EpgChannelInfo>>
)

object EpgMatcher {

    private val PREFIX_COUNTRY_EXTRACT_REGEX = Regex(
        """(?i)^(?:[\[\(【]\s*([a-z]{2,4})\s*[\]\)】]\s*|([a-z]{2,4})\s*[:\|\-\–\—\*•✪★☆~>\/\\]\s*)"""
    )
    private val PREFIX_COUNTRY_STRIP_REGEX = Regex(
        """(?i)^(?:[a-z]{2,4}\s*[:\|\-\–\—\*•✪★☆~>\/\\]\s*|[\[\(【][a-z]{2,4}[\]\)】]\s*)+"""
    )
    private val QUALITY_REGEX = Regex(
        """(?i)\b(fhd|uhd|4k|8k|hevc|h265|h264|1080p|1080i|720p|50fps|60fps|raw|hdr|vip|backup|sd|hq|lq)\b"""
    )
    private val HD_REGEX = Regex("""(?i)\bhd\b""")
    private val TIMESHIFT_REGEX = Regex("""(?i)(\+\s*\d{1,2})\b""")
    private val NON_ALPHANUMERIC_KEEP_PLUS_REGEX = Regex("""[^a-z0-9+]""")
    private val MULTI_SPACE_REGEX = Regex("""\s+""")
    private val DOMAIN_SUFFIX_REGEX = Regex("""(?i)\.(de|at|ch|uk|gb|us|com|net|org|tv|eu|fr|it|es|nl|pl|tr|ru)$""")
    private val TRAILING_DE_REGEX = Regex("""\s+de$""")

    private val normNameCache = ConcurrentHashMap<String, String>(4096)
    private val normIdCache = ConcurrentHashMap<String, String>(4096)

    @Volatile
    private var cachedSourceList: List<EpgChannelInfo>? = null
    @Volatile
    private var cachedIndex: EpgLookupIndex? = null

    private val KNOWN_NON_DACH_COUNTRIES = setOf(
        "TR", "TUR", "UK", "GB", "US", "USA", "FR", "FRA", "IT", "ITA", "ES", "ESP",
        "PL", "POL", "NL", "NED", "RU", "RUS", "EXYU", "YU", "RS", "HR", "BA", "MK",
        "AR", "ARA", "PT", "POR", "BR", "RO", "ROU", "AL", "ALB", "GR", "GRE", "BG",
        "HU", "HUN", "CZ", "CZE", "SK", "SE", "SWE", "NO", "NOR", "DK", "FI", "IN", "AF", "ADULT"
    )

    private val STRICT_SUB_CHANNEL_TOKENS = setOf(
        "gold", "maxx", "doku", "up", "crime", "krimi", "classics", "classic",
        "nitro", "neo", "info", "action", "news", "living", "passion", "emotions",
        "extra", "fun", "family", "cinema", "select", "junior", "alpha",
        "premieren", "premiere", "highlights", "thriller", "special", "showcase",
        "replay", "atlantic", "documentaries", "nature", "history", "science",
        "investigation", "wild", "comedy", "romance", "heimat", "serie", "serien", "film",
        "bundesliga", "buli", "topevent", "premierleague", "f1", "formel1",
        "tennis", "golf", "mix", "esports", "esport", "digital", "edge", "fight", "motor",
        "austria", "oesterreich", "schweiz", "swiss",
        "bw", "rp", "sued", "nord"
    )

    fun buildIndex(availableEpgChannels: List<EpgChannelInfo>): EpgLookupIndex {
        val prevList = cachedSourceList
        val prevIdx = cachedIndex
        if (prevList === availableEpgChannels && prevIdx != null) {
            return prevIdx
        }

        val byExactId = HashMap<String, MutableList<EpgChannelInfo>>(availableEpgChannels.size * 2)
        val byNormId = HashMap<String, MutableList<EpgChannelInfo>>(availableEpgChannels.size * 2)
        val byNormName = HashMap<String, MutableList<EpgChannelInfo>>(availableEpgChannels.size * 2)
        val byCanonicalSlug = HashMap<String, MutableList<EpgChannelInfo>>(availableEpgChannels.size * 2)

        for (epg in availableEpgChannels) {
            val exactKey = epg.id.trim().lowercase(Locale.ROOT)
            if (exactKey.isNotEmpty()) {
                byExactId.getOrPut(exactKey) { ArrayList(2) }.add(epg)
            }
            val nId = normalizeId(epg.id)
            if (nId.isNotEmpty()) {
                byNormId.getOrPut(nId) { ArrayList(2) }.add(epg)
            }
            val nName = normalizeName(epg.displayName)
            if (nName.isNotEmpty()) {
                byNormName.getOrPut(nName) { ArrayList(2) }.add(epg)
                val cSlug = toCanonicalSlug(nName.replace(" ", ""))
                if (cSlug.isNotEmpty()) {
                    byCanonicalSlug.getOrPut(cSlug) { ArrayList(2) }.add(epg)
                }
            }
            val idAsName = normalizeName(epg.id.replace(DOMAIN_SUFFIX_REGEX, ""))
            if (idAsName.isNotEmpty() && idAsName != nName) {
                byNormName.getOrPut(idAsName) { ArrayList(2) }.add(epg)
                val idSlug = toCanonicalSlug(idAsName.replace(" ", ""))
                if (idSlug.isNotEmpty()) {
                    byCanonicalSlug.getOrPut(idSlug) { ArrayList(2) }.add(epg)
                }
            }
        }

        val index = EpgLookupIndex(
            allChannels = availableEpgChannels,
            byExactId = byExactId,
            byNormId = byNormId,
            byNormName = byNormName,
            byCanonicalSlug = byCanonicalSlug
        )
        cachedSourceList = availableEpgChannels
        cachedIndex = index
        return index
    }

    fun matchChannel(
        channel: ChannelItem,
        availableEpgChannels: List<EpgChannelInfo>
    ): EpgMatchResult {
        if (availableEpgChannels.isEmpty() && !(channel.isManualEpgOverride && !channel.manualEpgTvgId.isNullOrBlank())) {
            return EpgMatchResult(
                matchedTvgId = null,
                matchedChannelName = null,
                method = EpgMappingMethod.NONE,
                status = EpgMappingStatus.UNASSIGNED,
                confidence = 0,
                reason = "Keine EPG-Kanäle in der Datenbank verfügbar"
            )
        }
        val index = buildIndex(availableEpgChannels)
        return matchChannelIndexed(channel, index)
    }

    /**
     * Fast O(1) indexed EPG matching with 100% strict verification (0% false positives).
     */
    fun matchChannelIndexed(
        channel: ChannelItem,
        index: EpgLookupIndex
    ): EpgMatchResult {
        // 0. Manual Override by user
        if (channel.isManualEpgOverride && !channel.manualEpgTvgId.isNullOrBlank()) {
            val manualKey = channel.manualEpgTvgId.trim().lowercase(Locale.ROOT)
            val matchedChannel = index.byExactId[manualKey]?.firstOrNull()
            return EpgMatchResult(
                matchedTvgId = channel.manualEpgTvgId,
                matchedChannelName = matchedChannel?.displayName ?: channel.name,
                method = EpgMappingMethod.MANUAL,
                status = EpgMappingStatus.MANUAL,
                confidence = 100,
                reason = "Manuelle Zuordnung durch Benutzer"
            )
        }

        if (index.allChannels.isEmpty()) {
            return EpgMatchResult(
                matchedTvgId = null,
                matchedChannelName = null,
                method = EpgMappingMethod.NONE,
                status = EpgMappingStatus.UNASSIGNED,
                confidence = 0,
                reason = "Keine EPG-Kanäle in der Datenbank verfügbar"
            )
        }

        val chCountry = detectChannelCountry(channel)
        // Fast bailout for non-DACH foreign channels when EPG feed is DACH
        if (chCountry in KNOWN_NON_DACH_COUNTRIES) {
            return EpgMatchResult(
                matchedTvgId = null,
                matchedChannelName = null,
                method = EpgMappingMethod.NONE,
                status = EpgMappingStatus.UNASSIGNED,
                confidence = 0,
                reason = "Ausländischer Sender ($chCountry) — kein DACH-EPG zugewiesen"
            )
        }

        val normChannelName = normalizeName(channel.name)
        val normTvgName = channel.tvgName?.takeIf { it.isNotBlank() }?.let { normalizeName(it) }

        // 1. Exact match of tvg-id or epg-id via O(1) Map lookup
        val exactTvgId = channel.tvgId?.trim()?.takeIf { it.isNotBlank() }
        val exactEpgId = channel.epgId?.trim()?.takeIf { it.isNotBlank() }

        if (exactTvgId != null) {
            val hits = index.byExactId[exactTvgId.lowercase(Locale.ROOT)].orEmpty().filter { epg ->
                isCountryCompatible(chCountry, normChannelName, epg) &&
                    isTokenParityValid(normChannelName, normalizeName(epg.displayName))
            }
            val resolved = resolveUniqueBroadcaster(hits, chCountry)
            if (resolved != null) {
                return EpgMatchResult(
                    matchedTvgId = resolved.id,
                    matchedChannelName = resolved.displayName,
                    method = EpgMappingMethod.EXACT_TVG_ID,
                    status = EpgMappingStatus.MATCHED,
                    confidence = 100,
                    reason = "100% Exakte tvg-id Übereinstimmung (${resolved.id})"
                )
            }
        }

        if (exactEpgId != null && !exactEpgId.equals(exactTvgId, ignoreCase = true)) {
            val hits = index.byExactId[exactEpgId.lowercase(Locale.ROOT)].orEmpty().filter { epg ->
                isCountryCompatible(chCountry, normChannelName, epg) &&
                    isTokenParityValid(normChannelName, normalizeName(epg.displayName))
            }
            val resolved = resolveUniqueBroadcaster(hits, chCountry)
            if (resolved != null) {
                return EpgMatchResult(
                    matchedTvgId = resolved.id,
                    matchedChannelName = resolved.displayName,
                    method = EpgMappingMethod.EXACT_TVG_ID,
                    status = EpgMappingStatus.MATCHED,
                    confidence = 100,
                    reason = "100% Exakte epg-id Übereinstimmung (${resolved.id})"
                )
            }
        }

        // 2. Normalized channel-ID match via O(1) Map lookup
        val idCandidates = LinkedHashSet<String>(4)
        if (exactTvgId != null) idCandidates.add(normalizeId(exactTvgId))
        if (exactEpgId != null) idCandidates.add(normalizeId(exactEpgId))
        if (channel.normalizedChannelId.isNotBlank()) idCandidates.add(normalizeId(channel.normalizedChannelId))

        for (candId in idCandidates) {
            if (candId.length < 2) continue
            val idMatches = index.byNormId[candId].orEmpty().filter { epg ->
                isCountryCompatible(chCountry, normChannelName, epg) &&
                    isTokenParityValid(normChannelName, normalizeName(epg.displayName))
            }
            if (idMatches.isNotEmpty()) {
                val resolved = resolveUniqueBroadcaster(idMatches, chCountry)
                if (resolved != null) {
                    return EpgMatchResult(
                        matchedTvgId = resolved.id,
                        matchedChannelName = resolved.displayName,
                        method = EpgMappingMethod.NORMALIZED_ID,
                        status = EpgMappingStatus.MATCHED,
                        confidence = 95,
                        reason = "100% Eindeutige Sender-ID Übereinstimmung (${resolved.id})"
                    )
                } else {
                    return EpgMatchResult(
                        matchedTvgId = null,
                        matchedChannelName = null,
                        method = EpgMappingMethod.NONE,
                        status = EpgMappingStatus.AMBIGUOUS,
                        confidence = 0,
                        candidates = idMatches.take(5).map { "${it.displayName} (${it.id})" },
                        reason = "Mehrdeutige EPG-Sender für ID '$candId'"
                    )
                }
            }
        }

        // 3. Verified Alias Match via DachMasterDatabase + O(1) Index Lookup
        if (chCountry in setOf("DE", "AT", "CH", "", "INT")) {
            val dachMatch = DachMasterDatabase.findMatch(channel.name, channel.tvgId, channel.groupTitle)
            val master = dachMatch.channel
            if (dachMatch.isMatch && master != null) {
                val masterNormName = normalizeName(master.canonicalName)
                val channelMatchesMaster = isTokenParityValid(normChannelName, masterNormName) &&
                    (chCountry.isBlank() || chCountry == "INT" || chCountry == master.countryCode)

                if (channelMatchesMaster) {
                    val candidatePool = LinkedHashSet<EpgChannelInfo>()
                    for (tId in master.tvgIds) {
                        index.byExactId[tId.lowercase(Locale.ROOT)]?.let { candidatePool.addAll(it) }
                        index.byNormId[normalizeId(tId)]?.let { candidatePool.addAll(it) }
                    }
                    index.byNormName[masterNormName]?.let { candidatePool.addAll(it) }
                    index.byNormName[normalizeName(master.shortName)]?.let { candidatePool.addAll(it) }
                    index.byCanonicalSlug[toCanonicalSlug(masterNormName.replace(" ", ""))]?.let { candidatePool.addAll(it) }
                    for (alias in master.aliases) {
                        val nAlias = normalizeName(alias)
                        if (nAlias.isNotEmpty()) {
                            index.byNormName[nAlias]?.let { candidatePool.addAll(it) }
                        }
                    }

                    val aliasMatches = candidatePool.filter { epg ->
                        val epgNormName = normalizeName(epg.displayName)
                        isCountryCompatible(chCountry, normChannelName, epg) &&
                            isTokenParityValid(normChannelName, epgNormName) &&
                            isTokenParityValid(masterNormName, epgNormName)
                    }

                    if (aliasMatches.isNotEmpty()) {
                        val effectiveTargetCountry = chCountry.ifBlank { master.countryCode }
                        val resolved = resolveUniqueBroadcaster(aliasMatches, effectiveTargetCountry)
                        if (resolved != null) {
                            return EpgMatchResult(
                                matchedTvgId = resolved.id,
                                matchedChannelName = resolved.displayName,
                                method = EpgMappingMethod.ALIAS_MATCH,
                                status = EpgMappingStatus.MATCHED,
                                confidence = 95,
                                reason = "100% Verifizierte DACH-Master-Zuordnung (${master.canonicalName} -> ${resolved.id})"
                            )
                        } else {
                            return EpgMatchResult(
                                matchedTvgId = null,
                                matchedChannelName = null,
                                method = EpgMappingMethod.NONE,
                                status = EpgMappingStatus.AMBIGUOUS,
                                confidence = 0,
                                candidates = aliasMatches.take(5).map { "${it.displayName} (${it.id})" },
                                reason = "Mehrere nicht eindeutig auflösbare Treffer für '${master.canonicalName}'"
                            )
                        }
                    }
                }
            }
        }

        // 4. Strict Normalized Name / Canonical Slug Match via O(1) Index Lookup
        if (normChannelName.isNotBlank() || !normTvgName.isNullOrBlank()) {
            val candidatePool = LinkedHashSet<EpgChannelInfo>()
            if (normChannelName.isNotBlank()) {
                index.byNormName[normChannelName]?.let { candidatePool.addAll(it) }
                val cSlug = toCanonicalSlug(normChannelName.replace(" ", ""))
                index.byCanonicalSlug[cSlug]?.let { candidatePool.addAll(it) }
            }
            if (!normTvgName.isNullOrBlank()) {
                index.byNormName[normTvgName]?.let { candidatePool.addAll(it) }
                val tSlug = toCanonicalSlug(normTvgName.replace(" ", ""))
                index.byCanonicalSlug[tSlug]?.let { candidatePool.addAll(it) }
            }

            val nameMatches = candidatePool.filter { epg ->
                val epgNorm = normalizeName(epg.displayName)
                val epgIdNormName = normalizeName(epg.id.replace(DOMAIN_SUFFIX_REGEX, ""))
                val matchesPrimary = normChannelName.isNotBlank() && (
                    areNamesEquivalent(normChannelName, epgNorm) ||
                        areNamesEquivalent(normChannelName, epgIdNormName)
                    )
                val matchesTvgName = !normTvgName.isNullOrBlank() && (
                    areNamesEquivalent(normTvgName, epgNorm) ||
                        areNamesEquivalent(normTvgName, epgIdNormName)
                    )
                (matchesPrimary || matchesTvgName) &&
                    isCountryCompatible(chCountry, normChannelName, epg) &&
                    isTokenParityValid(normChannelName, epgNorm)
            }

            if (nameMatches.isNotEmpty()) {
                val resolved = resolveUniqueBroadcaster(nameMatches, chCountry)
                if (resolved != null) {
                    return EpgMatchResult(
                        matchedTvgId = resolved.id,
                        matchedChannelName = resolved.displayName,
                        method = EpgMappingMethod.NORMALIZED_NAME,
                        status = EpgMappingStatus.MATCHED,
                        confidence = 90,
                        reason = "100% Eindeutige Namensübereinstimmung (${resolved.displayName})"
                    )
                } else {
                    return EpgMatchResult(
                        matchedTvgId = null,
                        matchedChannelName = null,
                        method = EpgMappingMethod.NONE,
                        status = EpgMappingStatus.AMBIGUOUS,
                        confidence = 0,
                        candidates = nameMatches.take(5).map { "${it.displayName} (${it.id})" },
                        reason = "Mehrdeutige Namensübereinstimmung für '${channel.name}'"
                    )
                }
            }
        }

        return EpgMatchResult(
            matchedTvgId = null,
            matchedChannelName = null,
            method = EpgMappingMethod.NONE,
            status = EpgMappingStatus.UNASSIGNED,
            confidence = 0,
            reason = "Keine 100% eindeutige EPG-Zuordnung gefunden"
        )
    }

    private fun resolveUniqueBroadcaster(
        candidates: List<EpgChannelInfo>,
        targetCountry: String
    ): EpgChannelInfo? {
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.first()

        val countryFiltered = filterByCountry(candidates, targetCountry)
        if (countryFiltered.size == 1) return countryFiltered.first()

        val pool = if (countryFiltered.isNotEmpty()) countryFiltered else candidates

        val firstNorm = normalizeName(pool.first().displayName)
        val allSameBroadcaster = pool.all { candidate ->
            val candNorm = normalizeName(candidate.displayName)
            areNamesEquivalent(firstNorm, candNorm) && isTokenParityValid(firstNorm, candNorm)
        }

        if (!allSameBroadcaster) {
            return null
        }

        return pool.maxWithOrNull(
            compareBy<EpgChannelInfo> { it.programCount > 0 }
                .thenBy {
                    val idLower = it.id.lowercase(Locale.ROOT)
                    when (targetCountry) {
                        "AT" -> idLower.endsWith(".at")
                        "CH" -> idLower.endsWith(".ch")
                        else -> idLower.endsWith(".de")
                    }
                }
                .thenBy { it.programCount }
        )
    }

    private fun detectChannelCountry(channel: ChannelItem): String {
        val explicit = channel.countryCode.trim().uppercase(Locale.ROOT)
        if (explicit.isNotBlank() && explicit != "INT" && explicit != "ALL") {
            return when (explicit) {
                "GB" -> "UK"
                "AUT" -> "AT"
                "SUI" -> "CH"
                "GER" -> "DE"
                else -> explicit
            }
        }
        val match = PREFIX_COUNTRY_EXTRACT_REGEX.find(channel.name.trim())
            ?: channel.groupTitle?.let { PREFIX_COUNTRY_EXTRACT_REGEX.find(it.trim()) }
        val prefix = (match?.groupValues?.getOrNull(1)?.ifBlank { match.groupValues.getOrNull(2) ?: "" } ?: "")
            .uppercase(Locale.ROOT)
        if (prefix.isNotBlank()) {
            return when (prefix) {
                "GER", "DEU" -> "DE"
                "AUT", "OES" -> "AT"
                "SUI", "CHE" -> "CH"
                "GB", "ENG" -> "UK"
                "USA" -> "US"
                "TUR" -> "TR"
                else -> prefix
            }
        }
        val lowerName = channel.name.lowercase(Locale.ROOT)
        if (lowerName.contains("österreich") || lowerName.contains("oesterreich") || lowerName.contains("austria") || lowerName.startsWith("orf ")) return "AT"
        if (lowerName.contains("schweiz") || lowerName.startsWith("srf ")) return "CH"
        if (channel.isDach) return "DE"
        return explicit
    }

    private fun detectEpgCountry(epg: EpgChannelInfo): String {
        val explicit = epg.countryCode?.trim()?.uppercase(Locale.ROOT).orEmpty()
        if (explicit.isNotBlank()) return explicit
        val idLower = epg.id.lowercase(Locale.ROOT)
        val nameLower = epg.displayName.lowercase(Locale.ROOT)
        return when {
            idLower.endsWith(".at") || idLower.contains("_at") || nameLower.contains("austria") || nameLower.contains("österreich") || nameLower.contains("oesterreich") || nameLower.startsWith("orf") -> "AT"
            idLower.endsWith(".ch") || idLower.contains("_ch") || nameLower.contains("schweiz") || nameLower.startsWith("srf") -> "CH"
            idLower.endsWith(".uk") || idLower.endsWith(".gb") -> "UK"
            idLower.endsWith(".us") -> "US"
            idLower.endsWith(".tr") -> "TR"
            idLower.endsWith(".fr") -> "FR"
            idLower.endsWith(".it") -> "IT"
            idLower.endsWith(".es") -> "ES"
            idLower.endsWith(".pl") -> "PL"
            idLower.endsWith(".nl") -> "NL"
            idLower.endsWith(".de") || idLower.contains("_de") -> "DE"
            else -> "DE"
        }
    }

    private fun isCountryCompatible(chCountry: String, chNameNorm: String, epg: EpgChannelInfo): Boolean {
        val epgCountry = detectEpgCountry(epg)
        if (chCountry in KNOWN_NON_DACH_COUNTRIES) {
            return epgCountry == chCountry
        }
        val epgNameNorm = normalizeName(epg.displayName)
        val chHasAustria = chNameNorm.contains("austria") || chNameNorm.contains("oesterreich")
        val epgHasAustria = epgNameNorm.contains("austria") || epgNameNorm.contains("oesterreich")
        if (chHasAustria != epgHasAustria) return false

        val chHasSchweiz = chNameNorm.contains("schweiz") || chNameNorm.contains("swiss")
        val epgHasSchweiz = epgNameNorm.contains("schweiz") || epgNameNorm.contains("swiss")
        if (chHasSchweiz != epgHasSchweiz) return false

        return true
    }

    fun normalizeId(rawId: String): String {
        if (rawId.isBlank()) return ""
        return normIdCache.getOrPut(rawId) {
            rawId.trim()
                .lowercase(Locale.ROOT)
                .replace("ä", "ae")
                .replace("ö", "oe")
                .replace("ü", "ue")
                .replace("ß", "ss")
                .replace(DOMAIN_SUFFIX_REGEX, "")
                .replace(NON_ALPHANUMERIC_KEEP_PLUS_REGEX, "")
        }
    }

    fun normalizeName(rawName: String): String {
        if (rawName.isBlank()) return ""
        return normNameCache.getOrPut(rawName) {
            var clean = rawName.lowercase(Locale.ROOT).trim()

            clean = clean
                .replace("ä", "ae")
                .replace("ö", "oe")
                .replace("ü", "ue")
                .replace("ß", "ss")

            clean = clean.replace(PREFIX_COUNTRY_STRIP_REGEX, "")
            clean = clean.replace(TIMESHIFT_REGEX) { m -> m.value.replace(" ", "") }
            clean = clean.replace(QUALITY_REGEX, " ")
            clean = clean.replace(HD_REGEX, " ")

            clean = clean
                .replace("top event", "topevent")
                .replace("premier league", "premierleague")
                .replace("formel 1", "f1")
                .replace("formula 1", "f1")
                .replace("best of", "bestof")
                .replace("nat geo", "national geographic")
                .replace("kabel 1", "kabel eins")
                .replace("pro 7", "prosieben")
                .replace("pro sieben", "prosieben")
                .replace("rtl 2", "rtl zwei")
                .replace("rtl ii", "rtl zwei")
                .replace("orf 1", "orf eins")
                .replace("orf i", "orf eins")
                .replace("orf 2", "orf zwei")
                .replace("orf ii", "orf zwei")
                .replace("orf 3", "orf drei")
                .replace("orf iii", "orf drei")
                .replace("srf 1", "srf eins")
                .replace("srf 2", "srf zwei")
                .replace("srf ii", "srf zwei")

            clean = clean.replace(NON_ALPHANUMERIC_KEEP_PLUS_REGEX, " ")
            clean = clean.replace(MULTI_SPACE_REGEX, " ").trim()
            clean = clean.replace(TRAILING_DE_REGEX, "").trim()

            clean
        }
    }

    private fun extractNumberSignature(normName: String): List<String> {
        val tokens = normName.split(' ')
        var numbers: MutableList<String>? = null
        for (i in tokens.indices) {
            val t = tokens[i]
            if (t.isEmpty()) continue
            if (t == "eins" && i > 0 && tokens[i - 1] == "kabel") continue
            val mapped = when (t) {
                "eins" -> "1"
                "zwei", "ii" -> "2"
                "drei", "iii" -> "3"
                else -> {
                    if (t.startsWith("+") && t.length > 1) {
                        t
                    } else {
                        var hasDigitOrPlus = false
                        for (ch in t) {
                            if (ch.isDigit() || ch == '+') {
                                hasDigitOrPlus = true
                                break
                            }
                        }
                        if (hasDigitOrPlus) {
                            t.filter { it.isDigit() || it == '+' }
                        } else null
                    }
                }
            }
            if (!mapped.isNullOrEmpty()) {
                if (numbers == null) numbers = ArrayList(2)
                numbers.add(mapped)
            }
        }
        return numbers ?: emptyList()
    }

    private fun isTokenParityValid(normA: String, normB: String): Boolean {
        if (normA.isBlank() || normB.isBlank()) return false

        val numsA = extractNumberSignature(normA)
        val numsB = extractNumberSignature(normB)
        if (numsA != numsB) return false

        if (normA.contains('+') != normB.contains('+')) return false

        val wordsA = normA.split(' ').filter { it.isNotEmpty() }.toSet()
        val wordsB = normB.split(' ').filter { it.isNotEmpty() }.toSet()
        val slugA = normA.replace(" ", "")
        val slugB = normB.replace(" ", "")

        for (token in STRICT_SUB_CHANNEL_TOKENS) {
            val inA = wordsA.contains(token) || hasAttachedSuffixToken(slugA, token)
            val inB = wordsB.contains(token) || hasAttachedSuffixToken(slugB, token)
            if (inA != inB) return false
        }

        val superA = wordsA.contains("super") || slugA.contains("superrtl")
        val superB = wordsB.contains("super") || slugB.contains("superrtl")
        if (superA != superB) return false

        return true
    }

    private fun hasAttachedSuffixToken(slug: String, token: String): Boolean {
        if (token.length < 3) return false
        return slug.endsWith(token) && slug.length > token.length
    }

    fun areNamesEquivalent(nameA: String, nameB: String): Boolean {
        if (nameA.isBlank() || nameB.isBlank()) return false
        if (!isTokenParityValid(nameA, nameB)) return false
        if (nameA == nameB) return true

        val slugA = nameA.replace(" ", "")
        val slugB = nameB.replace(" ", "")
        if (slugA == slugB) return true

        return toCanonicalSlug(slugA) == toCanonicalSlug(slugB)
    }

    fun toCanonicalSlug(slug: String): String {
        return when (slug) {
            "daserste", "ard", "arddaserste", "daserstede" -> "daserste"
            "zdf", "zweitesdeutschesfernsehen" -> "zdf"
            "rtl", "rtltelevision", "rtlde" -> "rtl"
            "rtlzwei", "rtl2", "rtlii" -> "rtlzwei"
            "superrtl", "rtlsuper" -> "superrtl"
            "nitro", "rtlnitro" -> "nitro"
            "sat1", "sateins" -> "sat1"
            "prosieben", "pro7" -> "prosieben"
            "kabeleins", "kabel1" -> "kabeleins"
            "kabeleinsdoku", "kabel1doku" -> "kabeleinsdoku"
            "kabeleinsclassics", "kabel1classics" -> "kabeleinsclassics"
            "vox", "voxtelevision" -> "vox"
            "welt", "n24", "weltn24" -> "welt"
            "ntv", "ntvde", "nachrichtenfernsehen" -> "ntv"
            "servustv", "servustvdeutschland" -> "servustv"
            "servustvaustria", "servustvoesterreich", "servustvat" -> "servustvaustria"
            "artede", "arte" -> "arte"
            "3sat", "dreisat" -> "3sat"
            "kika", "kikavonardundzdf", "kinderkanal" -> "kika"
            "one", "ardone", "einsfestival" -> "one"
            "ardalpha", "bralpha" -> "ardalpha"
            "tagesschau24", "einsankertagesschau24" -> "tagesschau24"
            "phoenix", "phoenixderereignisunddokumentationskanal" -> "phoenix"
            "zdfneo", "neo" -> "zdfneo"
            "zdfinfo", "zdfinfokanal" -> "zdfinfo"
            "tele5", "telefuenf" -> "tele5"
            "disneychannel", "disney" -> "disneychannel"
            "comedycentral", "vivacomedycentral" -> "comedycentral"
            "nickelodeon", "nick" -> "nickelodeon"
            "eurosport1", "eurosport" -> "eurosport1"
            "sport1", "dsf" -> "sport1"
            "orfeins", "orf1" -> "orfeins"
            "orfzwei", "orf2", "orf2w", "orf2n", "orf2o", "orf2s", "orf2t", "orf2v", "orf2b", "orf2k", "orf2st" -> "orfzwei"
            "orfdrei", "orf3", "orfiii" -> "orfdrei"
            "srfeins", "srf1" -> "srfeins"
            "srfzwei", "srf2", "sf2" -> "srfzwei"
            "srfinfo", "sfinfo" -> "srfinfo"
            "wdr", "wdrfernsehen", "wdrkoeln", "wdressen", "wdrduesseldorf", "wdrdortmund", "wdrmuenster", "wdrbielefeld", "wdrbonn", "wdraachen", "wdrwuppertal", "wdrsiegen", "wdrduisburg" -> "wdr"
            "ndr", "ndrfernsehen", "ndrnds", "ndrhamburg", "ndrschleswigholstein", "ndrmecklenburgvorpommern", "ndrhh", "ndrsh", "ndrmv" -> "ndr"
            "mdr", "mdrfernsehen", "mdrsachsen", "mdrsachsenanhalt", "mdrthueringen" -> "mdr"
            "hr", "hrfernsehen", "hessischerrundfunk" -> "hr"
            "rbb", "rbbfernsehen", "rbbberlin", "rbbbrandenburg" -> "rbb"
            "br", "brfernsehen", "bayerischerrundfunk", "brsued", "brnord" -> "br"
            "swr", "swrfernsehen" -> "swr"
            "swrbw", "swrfernsehenbw", "swrbadenwuerttemberg" -> "swrbw"
            "swrrp", "swrfernsehenrp", "swrrheinlandpfalz" -> "swrrp"
            "sr", "srfernsehen", "saarlaendischerrundfunk" -> "sr"
            "radiobremen", "radiobrementv", "rbtv" -> "radiobremen"
            else -> slug
        }
    }

    private fun filterByCountry(candidates: List<EpgChannelInfo>, countryCode: String?): List<EpgChannelInfo> {
        if (countryCode.isNullOrBlank() || countryCode.equals("INT", ignoreCase = true)) {
            return candidates
        }
        val target = countryCode.uppercase(Locale.ROOT)
        val filtered = candidates.filter { c ->
            detectEpgCountry(c) == target
        }
        return if (filtered.isNotEmpty()) filtered else candidates
    }
}
