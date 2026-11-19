package com.example.data.model

/**
 * Channel sorting modes for Live TV and DACH lists.
 */
enum class ChannelSortMode(
    val id: String,
    val titleDe: String,
    val titleEn: String,
    val descDe: String,
    val descEn: String
) {
    DACH_CANONICAL(
        id = "dach_canonical",
        titleDe = "DACH Standard",
        titleEn = "DACH Standard",
        descDe = "ÖRR (ARD/ZDF/Dritte) ➔ Große Private ➔ Sparten ➔ Pay-TV ➔ Sport ganz am Ende. Streams desselben Senders nach bester Bildqualität gebündelt.",
        descEn = "Public broadcasters ➔ Major Private ➔ Niche ➔ Pay-TV ➔ Sports at end. Streams clustered by quality."
    ),
    POPULARITY(
        id = "popularity",
        titleDe = "Nach Einschaltquote",
        titleEn = "Viewership Ratings",
        descDe = "Sortiert nach offiziellen Marktanteilen (AGF Deutschland, Teletest Österreich, Mediapulse Schweiz).",
        descEn = "Sorted by official market shares (AGF Germany, Teletest Austria, Mediapulse Switzerland)."
    ),
    ALPHABETICAL(
        id = "alphabetical",
        titleDe = "Alphabetisch (A–Z)",
        titleEn = "Alphabetical (A–Z)",
        descDe = "Bereinigte Sendernamen mit natürlicher Zahlensortierung (z. B. Sky Sport 2 vor 10).",
        descEn = "Cleaned titles with natural numerical sorting (e.g. Sky Sport 2 before 10)."
    ),
    QUALITY(
        id = "quality",
        titleDe = "Beste Bildqualität",
        titleEn = "Best Quality",
        descDe = "4K / UHD Kanäle zuerst, gefolgt von FHD (1080p), HD (720p) und SD.",
        descEn = "4K / UHD channels first, followed by FHD (1080p), HD (720p), and SD."
    ),
    PROVIDER_GROUPS(
        id = "provider_groups",
        titleDe = "Provider-Kategorien",
        titleEn = "Provider Groups",
        descDe = "Sortierung nach den Original-Kategorieordnern des IPTV-Anbieters.",
        descEn = "Sorted strictly by the IPTV provider's original category folders."
    );

    companion object {
        fun fromId(id: String?): ChannelSortMode {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: PROVIDER_GROUPS
        }
    }
}
