package com.example.data.series

import com.example.data.model.ChannelItem
import com.example.domain.model.PlaybackProgress
import com.example.domain.model.SeriesSortOption
import com.example.domain.model.SeriesStatusFilter
import com.example.domain.model.UnifiedEpisode
import com.example.domain.model.UnifiedSeason
import com.example.domain.model.UnifiedSeries

object SeriesAggregationEngine {

    fun buildUnifiedSeriesList(
        items: List<ChannelItem>,
        progressMap: Map<String, PlaybackProgress>,
        favoritesSet: Set<String>
    ): List<UnifiedSeries> {
        if (items.isEmpty()) return emptyList()

        val seriesMap = LinkedHashMap<String, MutableList<ChannelItem>>()
        for (item in items) {
            val (extractedTitle, extractedSeason, extractedEpisode) =
                com.example.data.parser.ContentClassifier.extractSeriesMetadata(item.name, item.groupTitle)

            val rawSeriesTitle = when {
                !item.seriesTitle.isNullOrBlank() && item.seriesTitle != item.name -> item.seriesTitle
                !extractedTitle.isNullOrBlank() -> extractedTitle
                else -> com.example.data.parser.ContentClassifier.cleanSeriesTitle(item.name)
            }
            val cleanTitle = com.example.data.parser.ContentClassifier.cleanSeriesTitle(rawSeriesTitle)
            val groupingKey = com.example.data.parser.ContentClassifier.canonicalSeriesTitle(cleanTitle)

            val finalSeason = if (item.seasonNumber != null && item.seasonNumber > 0) {
                item.seasonNumber
            } else {
                extractedSeason ?: 1
            }
            val finalEpisode = if (item.episodeNumber != null && item.episodeNumber > 0) {
                item.episodeNumber
            } else {
                extractedEpisode ?: 1
            }

            val refinedItem = item.copy(
                seriesTitle = cleanTitle,
                seasonNumber = finalSeason,
                episodeNumber = finalEpisode
            )
            seriesMap.getOrPut(groupingKey) { mutableListOf() }.add(refinedItem)
        }

        return seriesMap.map { (_, channelItems) ->
            val first = channelItems.first()
            val bestTitle = channelItems.firstNotNullOfOrNull { it.seriesTitle?.takeIf { t -> t.isNotBlank() } }
                ?: com.example.data.parser.ContentClassifier.cleanSeriesTitle(first.name)

            val seasonGroups = channelItems.groupBy { it.seasonNumber ?: 1 }
            val seasons = seasonGroups.toSortedMap().map { (seasonNum, eps) ->
                val sortedEps = eps.sortedBy { it.episodeNumber ?: 1 }
                val allSameEpNumber = sortedEps.size > 1 && sortedEps.all { (it.episodeNumber ?: 1) == (sortedEps.first().episodeNumber ?: 1) }

                UnifiedSeason(
                    seasonNumber = seasonNum,
                    episodes = sortedEps.mapIndexed { idx, epItem ->
                        val epId = epItem.id
                        val progress = progressMap[epId]
                        val epNumber = if (allSameEpNumber) idx + 1 else (epItem.episodeNumber ?: (idx + 1))
                        val cleanEpTitle = com.example.data.parser.ContentClassifier.extractEpisodeTitle(epItem.name, seasonNum, epNumber)
                        val displayTitle = if (cleanEpTitle.isNotBlank() && !cleanEpTitle.equals("Episode $epNumber", ignoreCase = true)) {
                            cleanEpTitle
                        } else if (epItem.name.isNotBlank() && !epItem.name.equals(bestTitle, ignoreCase = true)) {
                            epItem.name
                        } else {
                            "Episode $epNumber"
                        }

                        UnifiedEpisode(
                            id = epId,
                            externalId = epItem.tvgId ?: epId,
                            title = displayTitle,
                            seasonNumber = epItem.seasonNumber ?: seasonNum,
                            episodeNumber = epNumber,
                            streamUrl = epItem.streamUrl,
                            thumbnail = epItem.backdropUrl ?: epItem.logoUrl,
                            description = epItem.plot ?: "",
                            durationMinutes = if (epItem.durationSeconds > 0) epItem.durationSeconds / 60 else 45,
                            isWatched = progress?.isWatched ?: false,
                            progress = progress,
                            progressPercent = progress?.progressPercent?.toFloat() ?: 0f,
                            language = epItem.language
                        )
                    }
                )
            }

            val allEpisodes = seasons.flatMap { it.episodes }
            val totalEps = allEpisodes.size
            val watchedEps = allEpisodes.count { it.isWatched }
            val progressPercent = if (totalEps > 0) watchedEps.toFloat() / totalEps else 0f

            UnifiedSeries(
                id = first.id,
                title = bestTitle,
                originalTitle = first.normalizedTitle.ifBlank { null },
                categoryName = first.groupTitle.ifBlank { "Allgemein" },
                poster = first.posterUrl ?: first.logoUrl,
                backdrop = first.backdropUrl,
                countryCode = first.countryCode,
                isFavorite = favoritesSet.contains(first.id) || first.isFavorite,
                rating = 8.0f,
                releaseYear = null,
                description = first.plot ?: "",
                seasons = seasons,
                totalEpisodesCount = totalEps,
                watchedEpisodesCount = watchedEps,
                progressPercent = progressPercent,
                isStarted = watchedEps > 0,
                isCompleted = totalEps > 0 && watchedEps == totalEps,
                nextEpisodeToWatch = allEpisodes.firstOrNull { !it.isWatched } ?: allEpisodes.firstOrNull()
            )
        }
    }

    fun filterAndSort(
        seriesList: List<UnifiedSeries>,
        countryCode: String,
        category: String,
        statusFilter: SeriesStatusFilter,
        query: String,
        sortOption: SeriesSortOption
    ): List<UnifiedSeries> {
        var list = seriesList

        if (countryCode != "ALL") {
            list = list.filter { it.countryCode.equals(countryCode, ignoreCase = true) }
        }

        if (category != "ALL") {
            list = list.filter { it.categoryName.equals(category, ignoreCase = true) }
        }

        list = when (statusFilter) {
            SeriesStatusFilter.ALL -> list
            SeriesStatusFilter.IN_PROGRESS -> list.filter { it.isStarted && !it.isCompleted }
            SeriesStatusFilter.UNWATCHED -> list.filter { !it.isStarted }
            SeriesStatusFilter.FAVORITES -> list.filter { it.isFavorite }
        }

        if (query.isNotBlank()) {
            val q = query.trim()
            list = list.filter { it.title.contains(q, ignoreCase = true) || it.description.contains(q, ignoreCase = true) }
        }

        return when (sortOption) {
            SeriesSortOption.PROVIDER_DEFAULT -> list
            SeriesSortOption.NAME_ASC -> list.sortedBy { it.title.lowercase() }
            SeriesSortOption.RATING_DESC -> list.sortedByDescending { it.rating }
            SeriesSortOption.YEAR_DESC -> list.sortedByDescending { it.releaseYear ?: "" }
        }
    }
}
