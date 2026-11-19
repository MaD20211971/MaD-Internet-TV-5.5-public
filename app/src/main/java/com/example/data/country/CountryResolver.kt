package com.example.data.country

import com.example.data.model.ChannelItem
import com.example.data.model.ContentType
import com.example.util.FastLruCache

/**
 * Central, provider-independent Country Classification Engine.
 *
 * GOLDEN RULE: CERTAINTY > COMPLETENESS
 * - NEVER guess.
 * - NEVER default to DE if unknown.
 * - If data is insufficient -> UNKNOWN.
 * - If data contains conflicting strong signals -> CONFLICT.
 */
object CountryResolver {

    private val resolveCache = FastLruCache<String, CountryResolutionResult>(32768)

    fun clearCache() {
        resolveCache.evictAll()
    }

    /**
     * Resolves the country classification for any IPTV content item across M3U, Xtream, and MAC portals.
     */
    fun resolve(
        id: String = "",
        title: String,
        groupTitle: String = "",
        providerCategory: String = "",
        tvgCountry: String? = null,
        tvgId: String? = null,
        tvgName: String? = null,
        epgId: String? = null,
        epgCountry: String? = null,
        language: String? = null,
        streamUrl: String = "",
        providerCountry: String? = null,
        catalogCountry: String? = null,
        providerType: ProviderType = ProviderType.M3U,
        manualOverrideStore: ManualCountryOverrideStore? = null,
        ignoreChannelName: Boolean = false
    ): CountryResolutionResult {
        val cacheKey = if (manualOverrideStore == null) {
            if (ignoreChannelName) {
                "IGN_NAME|$groupTitle|${providerCategory ?: ""}|${tvgCountry ?: ""}|${providerCountry ?: ""}|${catalogCountry ?: ""}|${tvgId ?: ""}|${epgCountry ?: ""}|$streamUrl"
            } else {
                "$title|$groupTitle|${tvgCountry ?: ""}|${tvgId ?: ""}|${tvgName ?: ""}|${epgCountry ?: ""}|$streamUrl"
            }
        } else null

        if (cacheKey != null) {
            val cached = resolveCache.get(cacheKey)
            if (cached != null) return cached
        }

        val earlyDachMatch = DachMasterDatabase.findMatch(title = title, tvgName = tvgName, tvgId = tvgId ?: epgId)
        val normalizedChannelId = if (earlyDachMatch.isMatch && earlyDachMatch.channel != null) {
            earlyDachMatch.channel.channelId
        } else {
            CountryNormalizer.computeNormalizedChannelId(title.ifBlank { tvgName ?: "" })
        }

        // STEP 0: Manual User Override (Highest Priority: 100%)
        if (manualOverrideStore != null) {
            val override = manualOverrideStore.getOverride(id, normalizedChannelId)
            if (!override.isNullOrBlank()) {
                val upperOverride = override.trim().uppercase()
                return CountryResolutionResult(
                    countryCode = upperOverride,
                    confidence = 100,
                    status = ClassificationStatus.VERIFIED,
                    evidence = listOf(
                        CountryEvidence(
                            source = "manual",
                            detectedCountry = upperOverride,
                            weight = 100,
                            description = "Manuelle Benutzer-Zuweisung (höchste Priorität)"
                        )
                    ),
                    decision = upperOverride,
                    reason = "Vom Benutzer manuell als $upperOverride festgelegt.",
                    normalizedChannelId = normalizedChannelId,
                    isManualOverride = true
                )
            }
        }

        // STRICT CATEGORY / METADATA-ONLY MODE (0% Channel Name Interference, 100% Assignment Guarantee)
        if (ignoreChannelName) {
            val effectiveCategory = providerCategory?.takeIf { it.isNotBlank() } ?: groupTitle
            val catCountry = CountryNormalizer.extractCountryFromCategory(effectiveCategory)
                ?: CountryNormalizer.extractCountryFromCategory(groupTitle)
                ?: com.example.data.parser.ContentClassifier.detectCountryForGroup(effectiveCategory)
                    .takeIf { it !in listOf("OTHER", "INT", "UNKNOWN", "CONFLICT", "SPORT", "VIP") }

            if (catCountry != null) {
                val catResult = CountryResolutionResult(
                    countryCode = catCountry,
                    confidence = 100,
                    status = ClassificationStatus.VERIFIED,
                    evidence = listOf(
                        CountryEvidence(
                            source = "provider-category",
                            detectedCountry = catCountry,
                            weight = 100,
                            description = "Provider-Kategorie ($effectiveCategory)"
                        )
                    ),
                    decision = catCountry,
                    reason = "1:1 Zuordnung über Provider-Kategorie ($effectiveCategory)",
                    normalizedChannelId = normalizedChannelId
                )
                return cacheAndReturn(cacheKey, catResult)
            }

            val metaCountry = CountryNormalizer.normalizeCountryToken(tvgCountry)
                ?: CountryNormalizer.normalizeCountryToken(providerCountry)
                ?: CountryNormalizer.normalizeCountryToken(catalogCountry)
                ?: CountryNormalizer.normalizeCountryToken(epgCountry)
                ?: detectCountryFromTvgId(tvgId ?: epgId)

            if (metaCountry != null && metaCountry !in listOf("SPORT", "OTHER", "UNKNOWN", "CONFLICT")) {
                val metaResult = CountryResolutionResult(
                    countryCode = metaCountry,
                    confidence = 95,
                    status = ClassificationStatus.VERIFIED,
                    evidence = listOf(
                        CountryEvidence(
                            source = "metadata-country",
                            detectedCountry = metaCountry,
                            weight = 95,
                            description = "Explizite Länder-Metadaten ($metaCountry)"
                        )
                    ),
                    decision = metaCountry,
                    reason = "Zuordnung über explizite Metadaten ($metaCountry)",
                    normalizedChannelId = normalizedChannelId
                )
                return cacheAndReturn(cacheKey, metaResult)
            }

            val fallbackOther = CountryResolutionResult(
                countryCode = CountryConstants.CODE_OTHER,
                confidence = 80,
                status = ClassificationStatus.VERIFIED,
                evidence = listOf(
                    CountryEvidence(
                        source = "category-fallback-other",
                        detectedCountry = CountryConstants.CODE_OTHER,
                        weight = 80,
                        description = "Internationale / allgemeine Provider-Kategorie ($effectiveCategory)"
                    )
                ),
                decision = CountryConstants.CODE_OTHER,
                reason = "Internationale / allgemeine Kategorie ohne Ländercode",
                normalizedChannelId = normalizedChannelId
            )
            return cacheAndReturn(cacheKey, fallbackOther)
        }

        // STEP 0.5: Strict Priority: Austrian AT markers ALWAYS resolve to AT (unless matched in DACH Master DB)
        if (!earlyDachMatch.isMatch && com.example.data.parser.ContentClassifier.isAtOrAustriaContent(title, groupTitle.ifBlank { providerCategory })) {
            val atResult = CountryResolutionResult(
                countryCode = CountryConstants.CODE_AT,
                confidence = 100,
                status = ClassificationStatus.VERIFIED,
                evidence = listOf(
                    CountryEvidence(
                        source = "austria-priority",
                        detectedCountry = CountryConstants.CODE_AT,
                        weight = 100,
                        description = "Eindeutige österreichische Senderkennung (AT: / Austria)"
                    )
                ),
                decision = CountryConstants.CODE_AT,
                reason = "Als AT: Österreich identifiziert",
                normalizedChannelId = normalizedChannelId
            )
            return cacheAndReturn(cacheKey, atResult)
        }

        val evidenceList = mutableListOf<CountryEvidence>()

        // STEP 1: Explicit reliable country metadata (Tier 1: Weight 100)
        val normTvgCountry = CountryNormalizer.normalizeCountryToken(tvgCountry)
        if (normTvgCountry != null) {
            evidenceList.add(
                CountryEvidence(
                    source = "tvg-country",
                    detectedCountry = normTvgCountry,
                    weight = 100,
                    description = "Explizites M3U tvg-country Attribut"
                )
            )
        }

        val normProviderCountry = CountryNormalizer.normalizeCountryToken(providerCountry)
        if (normProviderCountry != null) {
            evidenceList.add(
                CountryEvidence(
                    source = "provider-country",
                    detectedCountry = normProviderCountry,
                    weight = 100,
                    description = "Explizite Provider-Länderinformation"
                )
            )
        }

        val normCatalogCountry = CountryNormalizer.normalizeCountryToken(catalogCountry)
        if (normCatalogCountry != null) {
            evidenceList.add(
                CountryEvidence(
                    source = "catalog-country",
                    detectedCountry = normCatalogCountry,
                    weight = 90,
                    description = "Katalog-Länderzuordnung des Providers"
                )
            )
        }

        // STEP 2: Verifiable station/content data (Tier 2: Weight 85 - 95)
        // 2a. DACH Master Database match (Verified Station Match - Weight 95)
        if (earlyDachMatch.isMatch && earlyDachMatch.channel != null) {
            evidenceList.add(
                CountryEvidence(
                    source = "dach-master-match",
                    detectedCountry = earlyDachMatch.channel.countryCode,
                    weight = 95,
                    description = "DACH Masterdatenbank: ${earlyDachMatch.channel.canonicalName} (${earlyDachMatch.channel.countryCode}) via ${earlyDachMatch.matchedRule}"
                )
            )
        } else {
            // 2b. Known station signature fallback (ORF, SRF, ARD, ZDF, etc.)
            val stationCountry = detectKnownStationCountry(title, tvgName)
            if (stationCountry != null) {
                evidenceList.add(
                    CountryEvidence(
                        source = "station-signature",
                        detectedCountry = stationCountry.first,
                        weight = 95,
                        description = stationCountry.second
                    )
                )
            }
        }

        // 2b. EPG country (Weight 90)
        val normEpgCountry = CountryNormalizer.normalizeCountryToken(epgCountry)
        if (normEpgCountry != null) {
            evidenceList.add(
                CountryEvidence(
                    source = "epg-country",
                    detectedCountry = normEpgCountry,
                    weight = 90,
                    description = "EPG Senderländer-Metadaten"
                )
            )
        }

        // 2c. tvg-id country suffix / prefix (Weight 85)
        val tvgIdCountry = detectCountryFromTvgId(tvgId ?: epgId)
        if (tvgIdCountry != null) {
            evidenceList.add(
                CountryEvidence(
                    source = "tvg-id",
                    detectedCountry = tvgIdCountry,
                    weight = 85,
                    description = "Eindeutige Länderkennung in tvg-id"
                )
            )
        }

        // STEP 3: Provider Categories (Tier 3: Weight 70)
        val groupCountry = CountryNormalizer.extractCountryFromCategory(groupTitle)
        if (groupCountry != null) {
            evidenceList.add(
                CountryEvidence(
                    source = "group-title",
                    detectedCountry = groupCountry,
                    weight = 70,
                    description = "Eindeutige Gruppenkategorie ($groupTitle)"
                )
            )
        }

        val providerCatCountry = CountryNormalizer.extractCountryFromCategory(providerCategory)
        if (providerCatCountry != null && providerCatCountry != groupCountry) {
            evidenceList.add(
                CountryEvidence(
                    source = "provider-category",
                    detectedCountry = providerCatCountry,
                    weight = 70,
                    description = "Eindeutige Provider-Kategorie ($providerCategory)"
                )
            )
        }

        // STEP 4: Channel Title Hints (Tier 3: Weight 85 - High Specificity)
        val titlePrefixCountry = detectCountryFromTitlePrefix(title)
        if (titlePrefixCountry != null) {
            evidenceList.add(
                CountryEvidence(
                    source = "title-prefix",
                    detectedCountry = titlePrefixCountry,
                    weight = 85,
                    description = "Länder-Präfix im Sendernamen ($titlePrefixCountry)"
                )
            )
        }

        val titleSuffixCountry = detectCountryFromTitleSuffix(title)
        if (titleSuffixCountry != null && titleSuffixCountry != titlePrefixCountry) {
            evidenceList.add(
                CountryEvidence(
                    source = "title-suffix",
                    detectedCountry = titleSuffixCountry,
                    weight = 85,
                    description = "Länder-Suffix im Sendernamen ($titleSuffixCountry)"
                )
            )
        }

        // STEP 5: Language (Tier 5: Weight 10 - WEAK SIGNAL)
        // German language CANNOT differentiate between DE, AT, and CH.
        // It provides only context if language is present.
        if (!language.isNullOrBlank()) {
            val cleanLang = CountryNormalizer.cleanText(language)
            if (cleanLang in listOf("DE", "GER", "GERMAN", "DEUTSCH")) {
                evidenceList.add(
                    CountryEvidence(
                        source = "language",
                        detectedCountry = "DACH_CONTEXT",
                        weight = 10,
                        description = "Sprache Deutsch (Indiz für DE/AT/CH, nicht alleine entscheidend)"
                    )
                )
            }
        }

        // STEP 6: Conflict Evaluation
        val conflictEvaluation = ConflictResolver.evaluate(evidenceList)
        if (conflictEvaluation.isConflict) {
            return cacheAndReturn(
                cacheKey,
                CountryResolutionResult(
                    countryCode = CountryConstants.CODE_CONFLICT,
                    confidence = 0,
                    status = ClassificationStatus.CONFLICT,
                    evidence = evidenceList,
                    decision = CountryConstants.CODE_CONFLICT,
                    reason = conflictEvaluation.reason,
                    normalizedChannelId = normalizedChannelId
                )
            )
        }

        // STEP 7: Score Aggregation
        val countryScores = mutableMapOf<String, Int>()
        for (ev in evidenceList) {
            if (ev.detectedCountry == "DACH_CONTEXT") continue
            countryScores[ev.detectedCountry] = (countryScores[ev.detectedCountry] ?: 0) + ev.weight
        }

        // If ConflictResolver resolved to a single authoritative country (e.g. AT/CH station inside a DE group), boost its score
        if (conflictEvaluation.conflictingCountries.size == 1) {
            val authCountry = conflictEvaluation.conflictingCountries.first()
            if (countryScores.containsKey(authCountry)) {
                val maxOther = countryScores.filterKeys { it != authCountry }.values.maxOrNull() ?: 0
                if ((countryScores[authCountry] ?: 0) <= maxOther) {
                    countryScores[authCountry] = maxOther + 20
                }
            }
        }

        if (countryScores.isEmpty()) {
            // Absolutely no reliable country signals found.
            // GOLDEN RULE: NEVER FALLBACK TO DE!
            return cacheAndReturn(
                cacheKey,
                CountryResolutionResult(
                    countryCode = CountryConstants.CODE_UNKNOWN,
                    confidence = 0,
                    status = ClassificationStatus.UNKNOWN,
                    evidence = evidenceList,
                    decision = CountryConstants.CODE_UNKNOWN,
                    reason = "Keine verlässlichen Länderinformationen vorhanden.",
                    normalizedChannelId = normalizedChannelId
                )
            )
        }

        // Find the country with highest accumulated weight
        val bestEntry = countryScores.maxByOrNull { it.value } ?: return cacheAndReturn(
            cacheKey,
            CountryResolutionResult(
                countryCode = CountryConstants.CODE_UNKNOWN,
                confidence = 0,
                status = ClassificationStatus.UNKNOWN,
                evidence = evidenceList,
                decision = CountryConstants.CODE_UNKNOWN,
                reason = "Keine eindeutige Länderzuordnung möglich.",
                normalizedChannelId = normalizedChannelId
            )
        )

        val bestCountry = bestEntry.key
        val rawScore = bestEntry.value
        val confidence = rawScore.coerceAtMost(100)

        // Evaluate ClassificationStatus based on strict confidence thresholds
        val finalRes = when {
            confidence >= 90 -> {
                CountryResolutionResult(
                    countryCode = bestCountry,
                    confidence = confidence,
                    status = ClassificationStatus.VERIFIED,
                    evidence = evidenceList,
                    decision = bestCountry,
                    reason = "Eindeutig verifiziert durch starke Metadaten (${evidenceList.filter { it.detectedCountry == bestCountry }.joinToString(", ") { it.source }}).",
                    normalizedChannelId = normalizedChannelId
                )
            }
            confidence >= 60 -> {
                CountryResolutionResult(
                    countryCode = bestCountry,
                    confidence = confidence,
                    status = ClassificationStatus.PROBABLE,
                    evidence = evidenceList,
                    decision = bestCountry,
                    reason = "Wahrscheinlich $bestCountry (${evidenceList.filter { it.detectedCountry == bestCountry }.joinToString(", ") { it.source }}).",
                    normalizedChannelId = normalizedChannelId
                )
            }
            else -> {
                // Confidence < 60 is not sufficient to be certain.
                CountryResolutionResult(
                    countryCode = CountryConstants.CODE_UNKNOWN,
                    confidence = confidence,
                    status = ClassificationStatus.UNKNOWN,
                    evidence = evidenceList,
                    decision = CountryConstants.CODE_UNKNOWN,
                    reason = "Signalstärke ($confidence%) unter Schwellenwert (60%). Zuordnung abgelehnt.",
                    normalizedChannelId = normalizedChannelId
                )
            }
        }
        return cacheAndReturn(cacheKey, finalRes)
    }

    private fun cacheAndReturn(cacheKey: String?, result: CountryResolutionResult): CountryResolutionResult {
        if (cacheKey != null) {
            resolveCache.put(cacheKey, result)
        }
        return result
    }

    /**
     * Identifies known, verified broadcast station signatures for DE, AT, CH, and major international networks.
     * Returns Pair(CountryCode, Description) or null.
     */
    private fun detectKnownStationCountry(title: String?, tvgName: String?): Pair<String, String>? {
        val clean1 = CountryNormalizer.cleanText(title)
        val clean2 = CountryNormalizer.cleanText(tvgName)
        val clean = "$clean1 $clean2".trim()

        // 1. Explicit national commercial broadcaster window variants in channel title
        val hasAtToken = clean.contains("AUSTRIA") || clean.contains("OESTERREICH") || clean.contains("AUTRICHE") ||
                clean.endsWith(" AT") || clean.contains(" AT ") || clean.endsWith(" AUT") || clean.contains(" AUT ")
        if (hasAtToken) {
            val isCommercialOrRegional = clean.contains("RTL") || clean.contains("SAT 1") || clean.contains("SAT1") ||
                    clean.contains("PROSIEBEN") || clean.contains("PRO7") || clean.contains("PRO 7") ||
                    clean.contains("VOX") || clean.contains("KABEL EINS") || clean.contains("KABEL 1") ||
                    clean.contains("SIXX") || clean.contains("NITRO") || clean.contains("SUPER RTL") ||
                    clean.contains("SERVUSTV") || clean.contains("SERVUS TV") || clean.contains("PULS") ||
                    clean.contains("SKY SPORT") || clean.contains("CANAL+") || clean.contains("CANAL PLUS") ||
                    clean.contains("EUROSPORT") || clean.contains("DISCOVERY") || clean.contains("TLC") ||
                    clean.contains("COMEDY CENTRAL") || clean.contains("NICKELODEON") || clean.contains("NICK") ||
                    clean.contains("DMAX") || clean.contains("WELT") || clean.contains("N24")
            if (isCommercialOrRegional) {
                return Pair(CountryConstants.CODE_AT, "Österreichischer Regionalsender ($title)")
            }
        }

        val hasChToken = (clean.contains("SCHWEIZ") || clean.contains("SWITZERLAND") || clean.contains("SUISSE") ||
                clean.contains("SVIZZERA") || clean.endsWith(" CH") || clean.contains(" CH ") ||
                clean.endsWith(" SUI") || clean.contains(" SUI ")) &&
                !clean.contains("DISCOVERY CH") && !clean.contains("CARTOON CH") && !clean.contains("HISTORY CH")
        if (hasChToken) {
            val isCommercialOrRegional = clean.contains("RTL") || clean.contains("SAT 1") || clean.contains("SAT1") ||
                    clean.contains("PROSIEBEN") || clean.contains("PRO7") || clean.contains("PRO 7") ||
                    clean.contains("VOX") || clean.contains("KABEL EINS") || clean.contains("KABEL 1") ||
                    clean.contains("SIXX") || clean.contains("NITRO") || clean.contains("SUPER RTL") ||
                    clean.contains("3+") || clean.contains("4+") || clean.contains("5+") || clean.contains("6+") ||
                    clean.contains("7+") || clean.contains("BLUE SPORT") || clean.contains("MYSPORTS") ||
                    clean.contains("SWISS SPORT") || clean.contains("NICK") || clean.contains("COMEDY CENTRAL")
            if (isCommercialOrRegional) {
                return Pair(CountryConstants.CODE_CH, "Schweizer Regionalsender ($title)")
            }
        }

        val isRomancePreposition = clean.contains("CASA DE ") || clean.contains("TOUR DE ") ||
                clean.contains("RIO DE ") || clean.contains("JOURNAL DE ") || clean.contains("CINE DE ") ||
                clean.contains("CANAL DE ") || clean.contains("NOTICIAS DE ") || clean.contains("FUTBOL DE ")
        val hasDeToken = !isRomancePreposition && (clean.contains("DEUTSCHLAND") || clean.contains("GERMANY") ||
                clean.endsWith(" DE") || clean.startsWith("DE ") || clean.endsWith(" GER") || clean.contains(" GER "))
        if (hasDeToken && !hasAtToken && !hasChToken) {
            val isCommercialOrRegional = clean.contains("RTL") || clean.contains("SAT 1") || clean.contains("SAT1") ||
                    clean.contains("PROSIEBEN") || clean.contains("PRO7") || clean.contains("PRO 7") ||
                    clean.contains("VOX") || clean.contains("KABEL EINS") || clean.contains("KABEL 1") ||
                    clean.contains("SERVUSTV") || clean.contains("SERVUS TV") || clean.contains("SKY SPORT") ||
                    clean.contains("SKY CINEMA") || clean.contains("DAZN")
            if (isCommercialOrRegional) {
                return Pair(CountryConstants.CODE_DE, "Deutscher Regionalsender ($title)")
            }
        }

        // 2. ÖSTERREICH: ORF, ATV, Puls 4, Puls 24, ServusTV AT, Krone TV, oe24, W24, LT1, Schau TV, Canal+ AT, Sky Sport Austria
        if (clean.contains("ORF 1") || clean.contains("ORF1") || clean.contains("ORF EINS") ||
            clean.contains("ORF 2") || clean.contains("ORF2") || clean.contains("ORF ZWEI") ||
            clean.contains("ORF III") || clean.contains("ORF 3") || clean.contains("ORF3") || clean.contains("ORF DREI") ||
            clean.contains("ORF SPORT") || clean.contains("ORF KIDS") ||
            clean.contains("ORF 2 W") || clean.contains("ORF 2 WIEN") || clean.contains("ORF 2 N") ||
            clean.contains("ORF 2 NOE") || clean.contains("ORF 2 B") || clean.contains("ORF 2 BURGENLAND") ||
            clean.contains("ORF 2 O") || clean.contains("ORF 2 OOE") || clean.contains("ORF 2 S") ||
            clean.contains("ORF 2 SALZBURG") || clean.contains("ORF 2 T") || clean.contains("ORF 2 TIROL") ||
            clean.contains("ORF 2 V") || clean.contains("ORF 2 VORARLBERG") || clean.contains("ORF 2 ST") ||
            clean.contains("ORF 2 STEIERMARK") || clean.contains("ORF 2 K") || clean.contains("ORF 2 KAERNTEN") ||
            clean.contains("ATV 2") || clean.contains("ATV2") || clean.contains("ATV II") ||
            ((clean == "ATV" || clean.startsWith("ATV ")) && !clean.contains("TURK") && !clean.contains("TR ")) ||
            clean.contains("PULS 4") || clean.contains("PULS4") ||
            clean.contains("PULS 24") || clean.contains("PULS24") ||
            clean.contains("KRONE TV") || clean.contains("KRONETV") || clean.contains("KRONE HIT") ||
            clean.contains("OE24") || clean.contains("OE24 TV") ||
            clean.contains("W24") || clean.contains("LT1") || clean.contains("SCHAU TV") || clean.contains("KURIER TV") ||
            clean.contains("R9 OESTERREICH") || clean.contains("TIROL TV") || clean.contains("LAENDLE TV") ||
            clean.contains("MUEHLVIERTEL TV") || clean.contains("KT1") || clean.contains("DORF TV") ||
            clean.contains("SERVUS TV OESTERREICH") || clean.contains("SERVUSTV OESTERREICH") ||
            clean.contains("SERVUS TV AT") || clean.contains("SERVUSTV AT") ||
            clean.contains("SERVUS TV AUSTRIA") || clean.contains("SERVUSTV AUSTRIA") ||
            clean.contains("SKY SPORT AUSTRIA") ||
            clean.contains("CANAL+ FIRST") || clean.contains("CANAL+ AUSTRIA")
        ) {
            return Pair(CountryConstants.CODE_AT, "Österreichischer Hauptsender")
        }

        // 3. SCHWEIZ: SRF, 3+, 4+, 5+, 6+, 7+, TV24, TV25, TeleZüri, TeleBärn, Telebasel, Tele M1, RTS, RSI, Blue Sport, MySports
        if (clean.contains("SRF 1") || clean.contains("SRF1") ||
            clean.contains("SRF ZWEI") || clean.contains("SRF 2") || clean.contains("SRF2") ||
            clean.contains("SRF INFO") || clean.contains("SRFINFO") ||
            clean.contains("TELEZURI") || clean.contains("TELEZUERI") ||
            clean.contains("TELEBARN") || clean.contains("TELEBAERN") ||
            clean.contains("TELEBASEL") || clean.contains("TELE M1") || clean.contains("TELE 1") ||
            clean.contains("TELE TOP") || clean.contains("TELETOP") || clean.contains("TELETICINO") ||
            clean.contains("TV SUEDOSTSCHWEIZ") || clean.contains("SWISS 1") || clean.contains("SWISS1") ||
            clean.contains("STAR TV CH") ||
            clean.contains("LEMAN BLEU") || clean.contains("CANAL ALPHA") || clean.contains("LA TELE") ||
            clean.contains("3+") || clean.contains("3 PLUS") ||
            clean.contains("4+") || clean.contains("4 PLUS") ||
            clean.contains("5+") || clean.contains("5 PLUS") ||
            clean.contains("6+") || clean.contains("6 PLUS") ||
            clean.contains("7+") || clean.contains("7 PLUS") ||
            clean.contains("TV24") || clean.contains("TV25") ||
            clean.contains("PULS 8") || clean.contains("PULS8") ||
            clean.contains("RTS 1") || clean.contains("RTS 2") || clean.contains("RTS UN") || clean.contains("RTS DEUX") ||
            clean.contains("RSI LA 1") || clean.contains("RSI LA 2") || clean.contains("RSI LA1") || clean.contains("RSI LA2") ||
            clean.contains("BLUE SPORT") || clean.contains("MYSPORTS") || clean.contains("SWISS SPORT")
        ) {
            return Pair(CountryConstants.CODE_CH, "Schweizer Hauptsender")
        }

        // 4. DEUTSCHLAND: ARD (Das Erste), ZDF, Dritte Programme, Arte, KiKa, Phoenix, tagesschau24, ONE, commercial & Pay-TV networks
        if (!hasAtToken && !hasChToken) {
            if (clean.contains("DAS ERSTE") || clean == "ARD" || clean.startsWith("ARD ") || clean.endsWith(" ARD") ||
                (clean.contains("ZDF") && !clean.contains("ORF") && !clean.contains("SRF")) ||
                clean.contains("ZDFNEO") || clean.contains("ZDFINFO") ||
                clean.contains("TAGESSCHAU24") || clean.contains("TAGESSCHAU 24") || clean.contains("PHOENIX") ||
                clean.contains("KIKA") || clean.contains("WDR") || clean.contains("NDR") ||
                clean.contains("BR FERNSEHEN") || clean.contains("BAYERISCHES FS") ||
                clean.contains("BAYERISCHES FERNSEHEN") ||
                clean.contains("SWR") || clean.contains("MDR") || clean.contains("HR FERNSEHEN") ||
                clean.contains("RBB") || clean.contains("SAARLAENDISCHER") || clean.contains("SR FERNSEHEN") ||
                clean.contains("RADIO BREMEN") || clean.contains("3SAT") || clean.contains("ARD ALPHA") ||
                clean.contains("RTL DEUTSCHLAND") || clean.contains("RTL TELEVISION") ||
                clean.contains("RTL ZWEI") || clean.contains("RTL 2") || clean.contains("RTL II") ||
                clean.contains("SUPER RTL") || clean.contains("TOGGO PLUS") || clean.contains("NITRO") ||
                clean.contains("RTL UP") || clean.contains("RTLUP") ||
                clean.contains("RTL CRIME") || clean.contains("RTL LIVING") || clean.contains("RTL PASSION") ||
                clean.contains("VOX UP") || clean.contains("VOXUP") ||
                clean.contains("N-TV") || clean.contains("NTV") || clean.contains("WELT DER WUNDER") ||
                clean.contains("N24 DOKU") ||
                clean.contains("SIXX") || clean.contains("PROSIEBEN MAXX") || clean.contains("PRO7 MAXX") ||
                clean.contains("PROSIEBEN FUN") || clean.contains("SAT.1 GOLD") || clean.contains("SAT 1 GOLD") ||
                clean.contains("SAT 1 EMOTIONS") || clean.contains("KABEL EINS DOKU") || clean.contains("KABEL 1 DOKU") ||
                clean.contains("KABEL EINS CLASSICS") || clean.contains("TELE 5") || clean.contains("DMAX") ||
                clean.contains("SPORT1") || clean.contains("SPORT 1") || clean.contains("SPORTDIGITAL") ||
                clean.contains("MAGENTA SPORT") || clean.contains("MAGENTASPORT") || clean.contains("DYN SPORT") ||
                clean.contains("EUROSPORT 1 DE") || clean.contains("EUROSPORT 2 DE") || clean.contains("DF1") ||
                clean.contains("SERVUSTV DEUTSCHLAND") || clean.contains("SKY SPORT BUNDESLIGA") ||
                clean.contains("SKY SPORT TOP EVENT") || clean.contains("SKY SPORT NEWS") ||
                clean.contains("SKY CINEMA") || clean.contains("SKY ATLANTIC") || clean.contains("SKY ONE") ||
                clean.contains("SKY KRIMI") || clean.contains("SKY DOCUMENTARIES") || clean.contains("SKY NATURE") ||
                clean.contains("SKY SHOWCASE") || clean.contains("SKY REPLAY") ||
                clean.contains("HEIMATKANAL") || clean.contains("ROMANCE TV") || clean.contains("GOLDSTAR TV") ||
                clean.contains("SPIEGEL GESCHICHTE") || clean.contains("13TH STREET") || clean.contains("WARNER TV") ||
                clean.contains("KINOWELT") || clean.contains("SILVERLINE") || clean.contains("DELUXE MUSIC") ||
                clean.contains("SCHLAGER DELUXE") || clean.contains("DEUTSCHES MUSIK") || clean.contains("DOKUSAT") ||
                clean.contains("LILO TV") || clean.contains("ANIXE") || clean.contains("BIBEL TV")
            ) {
                return Pair(CountryConstants.CODE_DE, "Deutscher Hauptsender")
            }
        }

        // 5. INTERNATIONAL BROADCASTERS
        // UK
        if (clean.contains("BBC ONE") || clean.contains("BBC TWO") || clean.contains("BBC THREE") ||
            clean.contains("BBC FOUR") || clean.contains("BBC NEWS") || clean.contains("BBC PARLIAMENT") ||
            clean.contains("ITV 1") || clean.contains("ITV 2") || clean.contains("ITV 3") || clean.contains("ITV 4") ||
            clean.contains("ITV1") || clean.contains("ITV2") || clean.contains("ITV3") || clean.contains("ITV4") ||
            clean.contains("CHANNEL 4") || clean.contains("CHANNEL 5") || clean.contains("SKY NEWS UK") ||
            clean.contains("SKY SPORTS MAIN EVENT") || clean.contains("SKY SPORTS PREMIER LEAGUE") ||
            clean.contains("SKY SPORTS UK") || clean.contains("TNT SPORTS")
        ) {
            return Pair("UK", "Britischer Hauptsender")
        }

        // US
        if (clean.contains("NBC NEWS") || clean.contains("CBS NEWS") || clean.contains("ABC NEWS") ||
            clean.contains("FOX NEWS") || clean.contains("MSNBC") || clean.contains("CNN US") ||
            clean.contains("ESPN US") || clean.contains("ESPN 2") || clean.contains("HBO US") ||
            clean.contains("SHOWTIME") || clean.contains("STARZ") || clean.contains("USA NETWORK")
        ) {
            return Pair("US", "US Hauptsender")
        }

        // TR (Türkei)
        if (clean.contains("TRT 1") || clean.contains("TRT HABER") || clean.contains("TRT SPOR") ||
            clean.contains("TRT COCUK") || clean.contains("TRT MUZIK") ||
            clean.contains("ATV TURKIYE") || clean.contains("KANAL D") || clean.contains("SHOW TV") ||
            clean.contains("STAR TV TR") || clean.contains("TV8") || clean.contains("NOW TV TURKIYE") ||
            clean.contains("BEYAZ TV") || clean.contains("A HABER") || clean.contains("HALK TV") ||
            clean.contains("KANAL 7") || clean.contains("HABERTURK") || clean.contains("CNN TURK") ||
            clean.contains("NTV SPOR") || clean.contains("BEIN SPORTS TR")
        ) {
            return Pair("TR", "Türkischer Hauptsender")
        }

        // IT (Italien)
        if (clean.contains("RAI 1") || clean.contains("RAI 2") || clean.contains("RAI 3") ||
            clean.contains("RAI 4") || clean.contains("RAI 5") || clean.contains("RAI NEWS 24") ||
            clean.contains("RAI SPORT") || clean.contains("CANALE 5") || clean.contains("ITALIA 1") ||
            clean.contains("RETE 4") || clean.contains("LA7") || clean.contains("SKY TG24") ||
            clean.contains("SKY SPORT UNO") || clean.contains("SKY SPORT CALCIO")
        ) {
            return Pair("IT", "Italienischer Hauptsender")
        }

        // FR (Frankreich)
        if (clean.contains("TF1") || clean.contains("FRANCE 2") || clean.contains("FRANCE 3") ||
            clean.contains("FRANCE 4") || clean.contains("FRANCE 5") || clean.contains("M6") ||
            clean.contains("W9") || clean.contains("TMC") || clean.contains("C8") ||
            clean.contains("BFM TV") || clean.contains("CNEWS") || clean.contains("LCI") ||
            clean.contains("RMC SPORT") || clean.contains("CANAL+ FRANCE")
        ) {
            return Pair("FR", "Französischer Hauptsender")
        }

        // ES (Spanien)
        if (clean.contains("LA 1") || clean.contains("LA 2") || clean.contains("ANTENA 3") ||
            clean.contains("TELECINCO") || clean.contains("CUATRO") || clean.contains("LA SEXTA") ||
            clean.contains("MOVISTAR") || clean.contains("DAZN LALIGA")
        ) {
            return Pair("ES", "Spanischer Hauptsender")
        }

        // NL (Niederlande)
        if (clean.contains("NPO 1") || clean.contains("NPO 2") || clean.contains("NPO 3") ||
            clean.contains("RTL 4") || clean.contains("RTL 5") || clean.contains("RTL 7") ||
            clean.contains("RTL 8") || clean.contains("SBS 6") || clean.contains("VERONICA") ||
            clean.contains("ZIGGO SPORT") || clean.contains("ESPN NL")
        ) {
            return Pair("NL", "Niederländischer Hauptsender")
        }

        // PL (Polen)
        if (clean.contains("TVP 1") || clean.contains("TVP 2") || clean.contains("TVP INFO") ||
            clean.contains("TVP SPORT") || clean.contains("POLSAT") || clean.contains("TVN") ||
            clean.contains("TVN24") || clean.contains("CANAL+ POLSKA")
        ) {
            return Pair("PL", "Polnischer Hauptsender")
        }

        // EX-YU (Balkan)
        if (clean.contains("HRT 1") || clean.contains("HRT 2") || clean.contains("HRT 3") || clean.contains("HRT 4") ||
            clean.contains("RTS 1") || clean.contains("RTS 2") || clean.contains("ARENA SPORT") ||
            clean.contains("NOVA BH") || clean.contains("NOVA S") || clean.contains("NOVA HR") ||
            clean.contains("B92") || clean.contains("PRVA TV") || clean.contains("PINK ") || clean.contains("HAYAT")
        ) {
            return Pair("EXYU", "Balkan Hauptsender")
        }

        // ALB (Albanien)
        if (clean.contains("RTSH") || clean.contains("TOP CHANNEL") || clean.contains("TV KLAN") ||
            clean.contains("KLAN KOSOVA") || clean.contains("ALSAT M") || clean.contains("SUPERSPORT AL") ||
            clean.contains("DIGITALB") || clean.contains("TRING")
        ) {
            return Pair("ALB", "Albanischer Hauptsender")
        }

        return null
    }

    private val TITLE_PREFIX_REGEX = java.util.regex.Pattern.compile(
        """^(?:(?:EU|VIP|RAW|HEVC|4K|UHD|FHD|HD|SD|LIVE|VOD|SRS|SPORT|PPV)\s*[:\|\-\*•❖◆✪★»\/]?\s*)*(?:\[([A-Za-z0-9\+]{2,12})\]|\(([A-Za-z0-9\+]{2,12})\)|\{([A-Za-z0-9\+]{2,12})\}|【([A-Za-z0-9\+]{2,12})】|\|([A-Za-z0-9\+]{2,12})\|\s*|([A-Za-z0-9\+]{2,12})\s*[:\|\-\–\—\*•❖◆◇✪★☆»›~>\/\\_])\s*"""
    )

    private val TITLE_SUFFIX_BRACKET_REGEX = java.util.regex.Pattern.compile(
        """[\s\-_]+(?:\[([A-Za-z0-9\+]{2,12})\]|\(([A-Za-z0-9\+]{2,12})\)|\|([A-Za-z0-9\+]{2,12})\|)\s*${'$'}"""
    )

    private val TITLE_SUFFIX_WORD_REGEX = java.util.regex.Pattern.compile(
        """[\s\-_]+([A-Za-z]{2,12})\s*${'$'}"""
    )

    /**
     * Extracts country code from tvg-id patterns like "ard.de", "orf1.at", "srf1.ch".
     * Checks Austrian and Swiss station IDs first because German XMLTV feeds often suffix them with ".de".
     */
    private fun detectCountryFromTvgId(tvgId: String?): String? {
        if (tvgId.isNullOrBlank()) return null
        val lower = tvgId.trim().lowercase()
        val baseId = lower.substringBefore(".")

        // 1. Austrian station IDs in German EPG feeds (e.g. "ORF1.de", "Puls4.de", "ATV.de", "ServusTVAT.de")
        if (baseId.startsWith("orf") || baseId == "atv" || baseId == "atv2" || baseId == "atvii" ||
            baseId.startsWith("puls4") || baseId.startsWith("puls24") || baseId.startsWith("oe24") ||
            baseId.startsWith("krone") || baseId.contains("austria") || baseId.contains("oesterreich") ||
            baseId == "servustvat" || baseId == "w24" || baseId == "lt1"
        ) {
            return CountryConstants.CODE_AT
        }

        // 2. Swiss station IDs in German EPG feeds (e.g. "SRF1.de", "SRF2.de", "3plus.de", "TV24.de")
        if (baseId.startsWith("srf") || baseId == "3plus" || baseId == "3+" ||
            baseId == "4plus" || baseId == "4+" || baseId == "5plus" || baseId == "5+" ||
            baseId == "6plus" || baseId == "6+" || baseId == "tv24" || baseId == "tv25" ||
            baseId == "puls8" || baseId.startsWith("telezueri") || baseId.startsWith("telebaern") ||
            baseId.startsWith("telebasel") || baseId.startsWith("telem1") || baseId.startsWith("bluesport") ||
            baseId.startsWith("mysports") || baseId.contains("schweiz") || baseId.contains("swiss")
        ) {
            return CountryConstants.CODE_CH
        }

        return when {
            lower.endsWith(".at") || lower.startsWith("at.") -> CountryConstants.CODE_AT
            lower.endsWith(".ch") || lower.startsWith("ch.") -> CountryConstants.CODE_CH
            lower.endsWith(".de") || lower.startsWith("de.") -> CountryConstants.CODE_DE
            lower.endsWith(".us") || lower.startsWith("us.") -> "US"
            lower.endsWith(".uk") || lower.startsWith("uk.") || lower.endsWith(".gb") -> "UK"
            lower.endsWith(".fr") || lower.startsWith("fr.") -> "FR"
            lower.endsWith(".it") || lower.startsWith("it.") -> "IT"
            lower.endsWith(".es") || lower.startsWith("es.") -> "ES"
            lower.endsWith(".tr") || lower.startsWith("tr.") -> "TR"
            lower.endsWith(".nl") || lower.startsWith("nl.") -> "NL"
            lower.endsWith(".pl") || lower.startsWith("pl.") -> "PL"
            lower.endsWith(".ro") || lower.startsWith("ro.") -> "RO"
            lower.endsWith(".pt") || lower.startsWith("pt.") -> "PT"
            lower.endsWith(".ru") || lower.startsWith("ru.") -> "RU"
            lower.endsWith(".se") || lower.startsWith("se.") -> "SE"
            lower.endsWith(".no") || lower.startsWith("no.") -> "NO"
            lower.endsWith(".dk") || lower.startsWith("dk.") -> "DK"
            lower.endsWith(".fi") || lower.startsWith("fi.") -> "FI"
            lower.endsWith(".gr") || lower.startsWith("gr.") -> "GR"
            lower.endsWith(".cz") || lower.startsWith("cz.") -> "CZ"
            lower.endsWith(".sk") || lower.startsWith("sk.") -> "SK"
            lower.endsWith(".hu") || lower.startsWith("hu.") -> "HU"
            lower.endsWith(".be") || lower.startsWith("be.") -> "BE"
            lower.endsWith(".ca") || lower.startsWith("ca.") -> "CA"
            lower.endsWith(".al") || lower.startsWith("al.") -> "ALB"
            lower.endsWith(".ba") || lower.endsWith(".rs") || lower.endsWith(".hr") || lower.endsWith(".si") || lower.endsWith(".me") -> "EXYU"
            lower.endsWith(".ua") || lower.startsWith("ua.") -> "UA"
            else -> null
        }
    }

    /**
     * Extracts country prefix from title patterns like "DE: Das Erste", "AT| ORF 1", "CH - SRF 1".
     * Includes collision guards against Spanish titles ("DE PELICULA") and channel abbreviations ("CH 1").
     */
    private fun detectCountryFromTitlePrefix(title: String?): String? {
        if (title.isNullOrBlank()) return null
        val trimmed = title.trim()

        // 0. Check Unicode Flag Emoji first
        CountryNormalizer.extractEmojiCountry(trimmed)?.let { return it }

        // Guard: Spanish/Portuguese/French titles starting with "DE ..."
        val upperTrimmed = trimmed.uppercase()
        if (upperTrimmed.startsWith("DE PELICULA") || upperTrimmed.startsWith("DE CASA") ||
            upperTrimmed.startsWith("DE VIAJE") || upperTrimmed.startsWith("DE FIESTA") ||
            upperTrimmed.startsWith("DE TOROS") || upperTrimmed.startsWith("DEPORTES") ||
            upperTrimmed.startsWith("DE LA ") || upperTrimmed.startsWith("DE LOS ") ||
            upperTrimmed.startsWith("DE LAS ")
        ) {
            return "ES"
        }

        val matcher = TITLE_PREFIX_REGEX.matcher(trimmed)
        if (!matcher.find()) return null
        val rawPrefix = (matcher.group(1) ?: matcher.group(2) ?: matcher.group(3)
            ?: matcher.group(4) ?: matcher.group(5) ?: matcher.group(6))?.uppercase() ?: return null
        val remainder = trimmed.substring(matcher.end()).trim()

        // Guard: "CH 1", "CH 2" -> abbreviation for Channel, not Switzerland
        if (rawPrefix == "CH" && remainder.isNotEmpty() && remainder.first().isDigit()) {
            return null
        }
        // Guard: "AL JAZEERA", "AL ARABIYA", "AL HADATH" -> Arabic, not Albania
        val upperRemainder = remainder.uppercase()
        if ((rawPrefix == "AL" || rawPrefix == "ALB") && (upperRemainder.contains("JAZEERA") || upperRemainder.contains("ARABIYA") || upperRemainder.contains("HADATH") || upperRemainder.contains("AULA") || upperRemainder.contains("KASS") || upperRemainder.contains("MAJD") || upperRemainder.contains("MANAR") || upperRemainder.contains("JADEED"))) {
            return "AR"
        }

        return when (rawPrefix) {
            "DE", "GER", "DEU", "DEUTSCHLAND", "GERMANY", "DEUTSCH" -> CountryConstants.CODE_DE
            "AT", "AUT", "AUSTRIA", "OESTERREICH" -> CountryConstants.CODE_AT
            "CH", "SUI", "CHE", "SCHWEIZ", "SWITZERLAND", "SWISS" -> CountryConstants.CODE_CH
            "US", "USA" -> "US"
            "UK", "GB", "GBR", "ENG" -> "UK"
            "TR", "TUR" -> "TR"
            "IT", "ITA" -> "IT"
            "FR", "FRA" -> "FR"
            "ES", "ESP", "LAT", "LATAM" -> "ES"
            "NL", "NED", "NLD" -> "NL"
            "PL", "POL" -> "PL"
            "EXYU", "YU", "SRB", "HRV", "BIH", "MKD", "SVN", "MNE", "RS", "HR", "BA" -> "EXYU"
            "ALB", "AL", "KS", "XK" -> "ALB"
            "RO", "ROU", "ROM" -> "RO"
            "PT", "POR", "PRT", "BR", "BRA" -> "PT"
            "RU", "RUS" -> "RU"
            "GR", "GRE", "GRC" -> "GR"
            "CZ", "CZE" -> "CZ"
            "SK", "SVK" -> "SK"
            "HU", "HUN" -> "HU"
            "BG", "BGR", "BUL" -> "BG"
            "SE", "SWE" -> "SE"
            "NO", "NOR" -> "NO"
            "DK", "DNK" -> "DK"
            "FI", "FIN" -> "FI"
            "AR", "ARA", "ARB" -> "AR"
            "SPORT", "SPORTS" -> "SPORT"
            "ADULT", "XXX", "18+" -> "ADULT"
            else -> CountryNormalizer.normalizeCountryToken(rawPrefix)
        }
    }

    /**
     * Extracts country suffix from title patterns like "RTL HD AT", "Sat.1 HD CH", "Das Erste HD (DE)".
     * Includes collision guards against "DISCOVERY CH" and non-country words.
     */
    private fun detectCountryFromTitleSuffix(title: String?): String? {
        if (title.isNullOrBlank()) return null
        val trimmed = title.trim()

        // 1. Check bracketed suffix, e.g. "Das Erste HD (DE)", "ORF 1 HD [AT]", "SRF 1 HD (CH)", "Movie [GER]"
        val bracketMatcher = TITLE_SUFFIX_BRACKET_REGEX.matcher(trimmed)
        if (bracketMatcher.find()) {
            val tag = (bracketMatcher.group(1) ?: bracketMatcher.group(2) ?: bracketMatcher.group(3))?.uppercase()
            if (tag != null) {
                val normalized = CountryNormalizer.normalizeCountryToken(tag)
                if (normalized != null) return normalized
            }
        }

        // 2. Check plain word suffix, e.g. "RTL HD AT", "Sat.1 HD CH", "ProSieben HD CH", "BBC One UK"
        val wordMatcher = TITLE_SUFFIX_WORD_REGEX.matcher(trimmed)
        if (!wordMatcher.find()) return null
        val suffix = wordMatcher.group(1)?.uppercase() ?: return null
        val prefixText = trimmed.substring(0, wordMatcher.start()).trim().uppercase()

        // Collision guards:
        // "DISCOVERY CH", "CARTOON CH", "HISTORY CH" -> "CH" stands for Channel, not Switzerland
        if ((suffix == "CH" || suffix == "SUI") && (prefixText.endsWith("DISCOVERY") || prefixText.endsWith("CARTOON") || prefixText.endsWith("HISTORY") || prefixText.endsWith("ACTION") || prefixText.endsWith("MOVIE") || prefixText.endsWith("THE") || prefixText.endsWith("DISNEY") || prefixText.endsWith("GOLF"))) {
            return null
        }

        return when (suffix) {
            "AT", "AUT", "AUSTRIA", "OESTERREICH" -> CountryConstants.CODE_AT
            "CH", "SUI", "CHE", "SCHWEIZ", "SWITZERLAND" -> CountryConstants.CODE_CH
            "DE", "GER", "DEU", "DEUTSCHLAND", "GERMANY", "DEUTSCH", "GERMAN" -> CountryConstants.CODE_DE
            "UK", "GB", "GBR" -> "UK"
            "US", "USA" -> "US"
            "TR", "TUR" -> "TR"
            "IT", "ITA" -> "IT"
            "FR", "FRA" -> "FR"
            "ES", "ESP" -> "ES"
            "NL", "NED" -> "NL"
            "PL", "POL" -> "PL"
            "RO", "ROU" -> "RO"
            "PT", "BR" -> "PT"
            "RU", "RUS" -> "RU"
            "GR", "GRE" -> "GR"
            "CZ", "CZE" -> "CZ"
            "SE", "SWE" -> "SE"
            else -> null
        }
    }

    /**
     * Helper to convert ChannelItem directly into a UnifiedContentItem.
     */
    fun ChannelItem.toUnifiedContentItem(providerType: ProviderType = ProviderType.M3U): UnifiedContentItem {
        val result = resolve(
            id = id,
            title = name,
            groupTitle = groupTitle,
            tvgId = tvgId,
            tvgName = tvgName,
            streamUrl = streamUrl,
            providerType = providerType
        )
        return UnifiedContentItem(
            id = id,
            title = name,
            normalizedTitle = CountryNormalizer.cleanText(name),
            type = when (type) {
                ContentType.LIVE_TV -> UnifiedContentType.LIVE
                ContentType.RADIO -> UnifiedContentType.LIVE
                ContentType.MOVIE -> UnifiedContentType.MOVIE
                ContentType.SERIES -> UnifiedContentType.SERIES
                else -> UnifiedContentType.LIVE
            },
            countryCode = result.countryCode,
            countryConfidence = result.confidence,
            countryEvidence = result.evidenceDescriptions,
            providerType = providerType,
            groupTitle = groupTitle,
            tvgId = tvgId,
            tvgName = tvgName,
            streamUrl = streamUrl,
            classificationStatus = result.status,
            manualCountryOverride = result.isManualOverride,
            manualCountry = if (result.isManualOverride) result.countryCode else null,
            normalizedChannelId = result.normalizedChannelId
        )
    }
}
