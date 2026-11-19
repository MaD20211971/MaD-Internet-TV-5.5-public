package com.example.data.country

/**
 * Evaluates pieces of evidence from multiple tiers to identify and resolve conflicts.
 */
object ConflictResolver {

    data class ConflictEvaluation(
        val isConflict: Boolean,
        val conflictingCountries: Set<String>,
        val reason: String
    )

    private val DACH_SET = setOf("DE", "AT", "CH")

    /**
     * Checks if the collected evidence contains significant contradictory signals.
     *
     * SPECIFICITY PRECEDENCE:
     * 1. Verified station identity (dach-master-match, station-signature, austria-priority with weight >= 95)
     *    or explicit regional suffix (e.g. "RTL Austria", "ProSieben CH") ALWAYS overrides a generic
     *    provider prefix ("DE:", "DE |"), EPG tvg-id (".de"), or folder ("DE | Allgemein").
     * 2. If one country's total evidence weight clearly dominates (margin >= 15 points), it wins
     *    without triggering a conflict.
     */
    fun evaluate(evidenceList: List<CountryEvidence>): ConflictEvaluation {
        val validEvidence = evidenceList.filter { it.detectedCountry != "DACH_CONTEXT" }
        if (validEvidence.isEmpty()) {
            return ConflictEvaluation(
                isConflict = false,
                conflictingCountries = emptySet(),
                reason = "Keine Signale."
            )
        }

        val allDistinct = validEvidence.map { it.detectedCountry }.distinct()
        if (allDistinct.size <= 1) {
            return ConflictEvaluation(
                isConflict = false,
                conflictingCountries = allDistinct.toSet(),
                reason = "Keine widersprüchlichen Signale."
            )
        }

        // 1. Verified station identity (weight >= 95) is authoritative
        val verifiedStation = validEvidence.firstOrNull {
            (it.source == "dach-master-match" || it.source == "station-signature" || it.source == "austria-priority") &&
                it.weight >= 95
        }
        if (verifiedStation != null) {
            return ConflictEvaluation(
                isConflict = false,
                conflictingCountries = setOf(verifiedStation.detectedCountry),
                reason = "Verifizierte Senderkennung (${verifiedStation.detectedCountry}) hat Vorrang."
            )
        }

        // 2. Within DACH (DE, AT, CH), specific AT or CH markers (suffix/prefix/group) take precedence over generic DE tags
        // UNLESS there is an explicit tvg-country vs group-title contradiction with no title/station marker
        val hasTvgCountryConflict = validEvidence.any { it.source == "tvg-country" } &&
            validEvidence.any { it.source == "group-title" || it.source == "provider-category" } &&
            validEvidence.filter { it.source in setOf("tvg-country", "group-title", "provider-category") }
                .map { it.detectedCountry }.distinct().size > 1 &&
            validEvidence.none { it.source in setOf("dach-master-match", "station-signature", "title-prefix", "title-suffix") }

        if (hasTvgCountryConflict) {
            val summary = validEvidence.joinToString("; ") { "${it.source}=${it.detectedCountry} (+${it.weight})" }
            return ConflictEvaluation(
                isConflict = true,
                conflictingCountries = allDistinct.toSet(),
                reason = "Konflikt: Widersprüchliche Metadaten zwischen ${allDistinct.joinToString(", ")}: $summary"
            )
        }

        if (allDistinct.all { it in DACH_SET }) {
            val specificAlpine = allDistinct.firstOrNull { it == "AT" || it == "CH" }
            if (specificAlpine != null) {
                return ConflictEvaluation(
                    isConflict = false,
                    conflictingCountries = setOf(specificAlpine),
                    reason = "Spezifische DACH-Region ($specificAlpine) hat Vorrang vor generischem DE-Tag."
                )
            }
        }

        // 3. Explicit title country marker (title-prefix or title-suffix) takes precedence over broad group-title
        val hasTitleMarker = validEvidence.any { (it.source == "title-prefix" || it.source == "title-suffix") && it.weight >= 80 }
        val channelSpecificEvidence = validEvidence.filter { it.weight >= 80 }
        val distinctChannelCountries = channelSpecificEvidence.map { it.detectedCountry }.distinct()

        if (hasTitleMarker && distinctChannelCountries.size == 1) {
            val authoritativeCountry = distinctChannelCountries.first()
            return ConflictEvaluation(
                isConflict = false,
                conflictingCountries = setOf(authoritativeCountry),
                reason = "Explizite Titel-Kennung ($authoritativeCountry) hat Vorrang vor Gruppenkategorie."
            )
        }

        // 4. Compare aggregated scores: if the top country leads by >= 15 points, it is not a conflict
        val scores = mutableMapOf<String, Int>()
        for (ev in validEvidence) {
            scores[ev.detectedCountry] = (scores[ev.detectedCountry] ?: 0) + ev.weight
        }
        val sortedScores = scores.entries.sortedByDescending { it.value }
        if (sortedScores.size >= 2) {
            val top = sortedScores[0]
            val second = sortedScores[1]
            if (top.value >= second.value + 15) {
                return ConflictEvaluation(
                    isConflict = false,
                    conflictingCountries = setOf(top.key),
                    reason = "Eindeutiges Übergewicht für ${top.key} (${top.value} vs ${second.value})."
                )
            }
        }

        if (distinctChannelCountries.size > 1) {
            val summary = channelSpecificEvidence.joinToString("; ") { "${it.source}=${it.detectedCountry} (+${it.weight})" }
            return ConflictEvaluation(
                isConflict = true,
                conflictingCountries = distinctChannelCountries.toSet(),
                reason = "Konflikt: Widersprüchliche Sender-Metadaten zwischen ${distinctChannelCountries.joinToString(", ")}: $summary"
            )
        }

        val strongEvidence = validEvidence.filter { it.weight >= 60 }
        val strongCountries = strongEvidence.map { it.detectedCountry }.distinct()
        if (strongCountries.size > 1) {
            val summary = strongEvidence.joinToString("; ") { "${it.source}=${it.detectedCountry} (+${it.weight})" }
            return ConflictEvaluation(
                isConflict = true,
                conflictingCountries = strongCountries.toSet(),
                reason = "Konflikt: Widersprüchliche Metadaten zwischen ${strongCountries.joinToString(", ")}: $summary"
            )
        }

        return ConflictEvaluation(
            isConflict = false,
            conflictingCountries = allDistinct.toSet(),
            reason = "Keine widersprüchlichen Signale."
        )
    }
}

