package com.example.domain.model

data class AlternativeStream(
    val providerName: String = "",
    val quality: String = ""
) : Comparable<AlternativeStream> {
    override fun compareTo(other: AlternativeStream): Int {
        return providerName.compareTo(other.providerName)
    }
}

data class PlaybackProgress(
    val contentId: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 1000L,
    val isCompleted: Boolean = false,
    val isWatched: Boolean = isCompleted,
    val progress: Float = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
    val progressPercent: Int = (if (durationMs > 0L) ((positionMs.toFloat() / durationMs) * 100).toInt() else 0).coerceIn(0, 100)
)

enum class SeriesStatusFilter {
    ALL,
    IN_PROGRESS,
    UNWATCHED,
    FAVORITES
}

enum class SeriesSortOption {
    PROVIDER_DEFAULT,
    NAME_ASC,
    RATING_DESC,
    YEAR_DESC
}

data class UnifiedEpisode(
    val id: String = "",
    val externalId: String = "",
    val title: String = "",
    val seasonNumber: Int = 1,
    val episodeNumber: Int = 1,
    val streamUrl: String = "",
    val thumbnail: String? = null,
    val description: String = "",
    val durationMinutes: Int = 0,
    val alternativeStreams: List<AlternativeStream> = emptyList(),
    val isWatched: Boolean = false,
    val progress: PlaybackProgress? = null,
    val progressPercent: Float = 0f,
    val language: String? = null
)

data class UnifiedSeason(
    val seasonNumber: Int = 1,
    val episodes: List<UnifiedEpisode> = emptyList()
)

data class UnifiedSeries(
    val id: String,
    val title: String,
    val originalTitle: String? = null,
    val categoryName: String = "Allgemein",
    val poster: String? = null,
    val backdrop: String? = null,
    val countryCode: String = "",
    val isFavorite: Boolean = false,
    val rating: Float = 0f,
    val releaseYear: String? = null,
    val description: String = "",
    val seasons: List<UnifiedSeason> = emptyList(),
    val totalEpisodesCount: Int = 0,
    val watchedEpisodesCount: Int = 0,
    val progressPercent: Float = 0f,
    val isStarted: Boolean = false,
    val isCompleted: Boolean = false,
    val nextEpisodeToWatch: UnifiedEpisode? = null
)
