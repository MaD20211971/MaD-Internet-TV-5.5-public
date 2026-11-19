package com.example.data.player

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.entity.MovieWatchHistoryEntity
import com.example.data.local.entity.SeriesWatchHistoryEntity
import com.example.data.model.ChannelItem
import com.example.data.model.ContentType
import com.example.data.parser.ContentClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Unified representation of a VOD Resume item for UI display.
 */
data class VodResumeItem(
    val id: String, // movieId or episodeId
    val isSeries: Boolean,
    val seriesId: String? = null,
    val seriesTitle: String? = null,
    val title: String, // E.g. "Breaking Bad - S02E05 (42:10 / 48:00)" or "Inception (42:10 / 1:48:00)"
    val mainTitle: String, // Overarching title: "Breaking Bad" or "Inception"
    val subtitle: String, // "S02E05 (42:10 / 48:00)" or "42:10 / 1:48:00"
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeId: String? = null,
    val streamUrl: String,
    val lastPositionSeconds: Long,
    val totalDurationSeconds: Long,
    val watchedAtTimestamp: Long,
    val progressPercent: Float,
    val isCompleted: Boolean,
    val posterUrl: String? = null
)

/**
 * Production-ready VOD Watch History & Resume Manager.
 * Handles tracking, >90% completion tolerance, asynchronous Room persistence,
 * and decoupling of provider stream URLs from stable series/movie IDs.
 */
object VodWatchHistoryManager {

    private const val TAG = "VodWatchHistoryManager"
    private const val COMPLETION_THRESHOLD_PERCENT = 0.90 // 90% tolerance limit
    private const val COMPLETION_REMAINING_SECONDS = 30L // Within last 30s is also completed
    private const val MIN_TRACKING_SECONDS = 5L // Ignore trivial clicks under 5s

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Formats seconds into human-readable HH:MM:SS or MM:SS string.
     * Examples:
     *   2530s -> "42:10"
     *   2880s -> "48:00"
     *   4530s -> "1:15:30"
     */
    fun formatTime(seconds: Long): String {
        if (seconds <= 0) return "00:00"
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, secs)
        }
    }

    /**
     * Builds the formatted series label according to specification:
     * e.g. "Breaking Bad - S02E05 (42:10 / 48:00)"
     */
    fun formatSeriesDisplayTitle(
        seriesTitle: String,
        seasonNumber: Int,
        episodeNumber: Int,
        lastPositionSeconds: Long,
        totalDurationSeconds: Long
    ): String {
        val sStr = String.format(Locale.US, "S%02dE%02d", seasonNumber.coerceAtLeast(1), episodeNumber.coerceAtLeast(1))
        val posStr = formatTime(lastPositionSeconds)
        return if (totalDurationSeconds > 0) {
            val durStr = formatTime(totalDurationSeconds)
            "$seriesTitle - $sStr ($posStr / $durStr)"
        } else {
            "$seriesTitle - $sStr ($posStr)"
        }
    }

    /**
     * Builds the formatted movie label:
     * e.g. "Inception (42:10 / 1:48:00)"
     */
    fun formatMovieDisplayTitle(
        movieTitle: String,
        lastPositionSeconds: Long,
        totalDurationSeconds: Long
    ): String {
        val posStr = formatTime(lastPositionSeconds)
        return if (totalDurationSeconds > 0) {
            val durStr = formatTime(totalDurationSeconds)
            "$movieTitle ($posStr / $durStr)"
        } else {
            "$movieTitle ($posStr)"
        }
    }

    /**
     * Asynchronously records playback progress into Room database.
     */
    fun recordProgress(
        context: Context,
        channel: ChannelItem,
        positionSeconds: Long,
        durationSeconds: Long
    ) {
        if (channel.type != ContentType.MOVIE && channel.type != ContentType.SERIES && channel.playlistId != "recordings") {
            return
        }

        val posSec = positionSeconds.coerceAtLeast(0L)
        val durSec = if (durationSeconds > 0L) durationSeconds else channel.durationSeconds.toLong()

        // Evaluate 90% tolerance threshold
        val isCompleted = if (durSec > 0L) {
            val ratio = posSec.toDouble() / durSec.toDouble()
            ratio >= COMPLETION_THRESHOLD_PERCENT || posSec >= (durSec - COMPLETION_REMAINING_SECONDS)
        } else {
            false
        }

        scope.launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val dao = db.watchHistoryDao()
                val now = System.currentTimeMillis()

                if (channel.type == ContentType.SERIES) {
                    val (extractedTitle, extractedSeason, extractedEp) =
                        ContentClassifier.extractSeriesMetadata(channel.name, channel.groupTitle)

                    val seriesTitle = when {
                        !channel.seriesTitle.isNullOrBlank() && channel.seriesTitle != channel.name -> channel.seriesTitle
                        !extractedTitle.isNullOrBlank() -> extractedTitle
                        else -> ContentClassifier.cleanSeriesTitle(channel.name)
                    }

                    val seasonNum = channel.seasonNumber ?: extractedSeason ?: 1
                    val epNum = channel.episodeNumber ?: extractedEp ?: 1
                    val seriesId = channel.sourceId.ifBlank {
                        ContentClassifier.canonicalSeriesTitle(seriesTitle).ifBlank { channel.id }
                    }
                    val episodeId = channel.id.ifBlank { "${seriesId}_s${seasonNum}_e${epNum}" }
                    val cleanEpTitle = ContentClassifier.extractEpisodeTitle(channel.name, seasonNum, epNum)
                    val episodeTitle = if (cleanEpTitle.isNotBlank() && !cleanEpTitle.equals("Episode $epNum", ignoreCase = true)) {
                        cleanEpTitle
                    } else if (channel.name.isNotBlank() && !channel.name.equals(seriesTitle, ignoreCase = true)) {
                        channel.name
                    } else {
                        "Episode $epNum"
                    }

                    val entity = SeriesWatchHistoryEntity(
                        episodeId = episodeId,
                        seriesId = seriesId,
                        seriesTitle = seriesTitle,
                        seasonNumber = seasonNum,
                        episodeNumber = epNum,
                        episodeTitle = episodeTitle,
                        streamUrl = channel.streamUrl,
                        lastPositionSeconds = posSec,
                        totalDurationSeconds = durSec,
                        watchedAtTimestamp = now,
                        isCompleted = isCompleted,
                        posterUrl = channel.backdropUrl ?: channel.posterUrl ?: channel.logoUrl
                    )
                    dao.insertOrUpdateSeries(entity)
                } else {
                    // Movie or recording
                    val movieId = channel.sourceId.ifBlank { channel.id }
                    val entity = MovieWatchHistoryEntity(
                        movieId = movieId,
                        title = channel.name,
                        streamUrl = channel.streamUrl,
                        lastPositionSeconds = posSec,
                        totalDurationSeconds = durSec,
                        watchedAtTimestamp = now,
                        isCompleted = isCompleted,
                        posterUrl = channel.posterUrl ?: channel.logoUrl
                    )
                    dao.insertOrUpdateMovie(entity)
                }

                // Keep legacy WatchProgressHelper SharedPreferences in sync for backwards-compatibility
                WatchProgressHelper.saveProgress(context, channel.streamUrl, posSec * 1000L, durSec * 1000L)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to persist watch progress for ${channel.name}: ${e.message}", e)
            }
        }
    }

    /**
     * Resolves the saved position in seconds for a movie.
     */
    suspend fun getMoviePositionSeconds(context: Context, movieId: String): Long {
        return try {
            val db = AppDatabase.getDatabase(context)
            val entity = db.watchHistoryDao().getMovie(movieId)
            if (entity != null && !entity.isCompleted) entity.lastPositionSeconds else 0L
        } catch (_: Throwable) {
            0L
        }
    }

    /**
     * Resolves the saved position in seconds for a series episode.
     */
    suspend fun getEpisodePositionSeconds(context: Context, episodeId: String): Long {
        return try {
            val db = AppDatabase.getDatabase(context)
            val entity = db.watchHistoryDao().getEpisode(episodeId)
            if (entity != null && !entity.isCompleted) entity.lastPositionSeconds else 0L
        } catch (_: Throwable) {
            0L
        }
    }

    /**
     * Deletes a single item from the history.
     */
    fun deleteHistoryItem(context: Context, isSeries: Boolean, id: String) {
        scope.launch {
            try {
                val db = AppDatabase.getDatabase(context)
                if (isSeries) {
                    db.watchHistoryDao().deleteEpisode(id)
                } else {
                    db.watchHistoryDao().deleteMovie(id)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to delete history item $id: ${e.message}", e)
            }
        }
    }

    /**
     * Clears all movie and series history.
     */
    fun clearAllHistory(context: Context) {
        scope.launch {
            try {
                val db = AppDatabase.getDatabase(context)
                db.watchHistoryDao().clearAllMovies()
                db.watchHistoryDao().clearAllSeries()
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to clear watch history: ${e.message}", e)
            }
        }
    }

    /**
     * Observes the active Continue Watching (Fortsetzen) list (uncompleted, >5s watched),
     * sorted strictly by watched_at_timestamp DESC.
     */
    fun getContinueWatchingFlow(context: Context): Flow<List<VodResumeItem>> {
        return try {
            val db = AppDatabase.getDatabase(context)
            val moviesFlow = db.watchHistoryDao().getActiveResumeMoviesFlow()
            val seriesFlow = db.watchHistoryDao().getActiveResumeSeriesFlow()

            combine(moviesFlow, seriesFlow) { movies, seriesEpisodes ->
                val movieItems = movies.map { m ->
                    val progressRatio = if (m.totalDurationSeconds > 0) {
                        (m.lastPositionSeconds.toFloat() / m.totalDurationSeconds.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    val posStr = formatTime(m.lastPositionSeconds)
                    val durStr = if (m.totalDurationSeconds > 0) formatTime(m.totalDurationSeconds) else null
                    val sub = if (durStr != null) "$posStr / $durStr" else posStr

                    VodResumeItem(
                        id = m.movieId,
                        isSeries = false,
                        title = formatMovieDisplayTitle(m.title, m.lastPositionSeconds, m.totalDurationSeconds),
                        mainTitle = m.title,
                        subtitle = sub,
                        streamUrl = m.streamUrl,
                        lastPositionSeconds = m.lastPositionSeconds,
                        totalDurationSeconds = m.totalDurationSeconds,
                        watchedAtTimestamp = m.watchedAtTimestamp,
                        progressPercent = progressRatio,
                        isCompleted = m.isCompleted,
                        posterUrl = m.posterUrl
                    )
                }

                // For series: group by series_id and pick the most recently watched episode per series
                val seriesItems = seriesEpisodes
                    .groupBy { it.seriesId }
                    .map { (_, eps) ->
                        val latestEp = eps.maxByOrNull { it.watchedAtTimestamp } ?: eps.first()
                        val progressRatio = if (latestEp.totalDurationSeconds > 0) {
                            (latestEp.lastPositionSeconds.toFloat() / latestEp.totalDurationSeconds.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        val sStr = String.format(Locale.US, "S%02dE%02d", latestEp.seasonNumber.coerceAtLeast(1), latestEp.episodeNumber.coerceAtLeast(1))
                        val posStr = formatTime(latestEp.lastPositionSeconds)
                        val durStr = if (latestEp.totalDurationSeconds > 0) formatTime(latestEp.totalDurationSeconds) else null
                        val sub = if (durStr != null) "$sStr ($posStr / $durStr)" else "$sStr ($posStr)"

                        VodResumeItem(
                            id = latestEp.episodeId,
                            isSeries = true,
                            seriesId = latestEp.seriesId,
                            seriesTitle = latestEp.seriesTitle,
                            title = formatSeriesDisplayTitle(
                                latestEp.seriesTitle,
                                latestEp.seasonNumber,
                                latestEp.episodeNumber,
                                latestEp.lastPositionSeconds,
                                latestEp.totalDurationSeconds
                            ),
                            mainTitle = latestEp.seriesTitle,
                            subtitle = sub,
                            seasonNumber = latestEp.seasonNumber,
                            episodeNumber = latestEp.episodeNumber,
                            episodeId = latestEp.episodeId,
                            streamUrl = latestEp.streamUrl,
                            lastPositionSeconds = latestEp.lastPositionSeconds,
                            totalDurationSeconds = latestEp.totalDurationSeconds,
                            watchedAtTimestamp = latestEp.watchedAtTimestamp,
                            progressPercent = progressRatio,
                            isCompleted = latestEp.isCompleted,
                            posterUrl = latestEp.posterUrl
                        )
                    }

                (movieItems + seriesItems).sortedByDescending { it.watchedAtTimestamp }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error creating continue watching flow: ${e.message}", e)
            flowOf(emptyList())
        }
    }

    /**
     * Observes all watch history (including completed items).
     */
    fun getAllWatchHistoryFlow(context: Context): Flow<List<VodResumeItem>> {
        return try {
            val db = AppDatabase.getDatabase(context)
            val moviesFlow = db.watchHistoryDao().getAllMoviesFlow()
            val seriesFlow = db.watchHistoryDao().getAllSeriesEpisodesFlow()

            combine(moviesFlow, seriesFlow) { movies, seriesEpisodes ->
                val movieItems = movies.map { m ->
                    val progressRatio = if (m.totalDurationSeconds > 0) {
                        (m.lastPositionSeconds.toFloat() / m.totalDurationSeconds.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                    val posStr = formatTime(m.lastPositionSeconds)
                    val durStr = if (m.totalDurationSeconds > 0) formatTime(m.totalDurationSeconds) else null
                    val sub = if (durStr != null) "$posStr / $durStr" else posStr

                    VodResumeItem(
                        id = m.movieId,
                        isSeries = false,
                        title = formatMovieDisplayTitle(m.title, m.lastPositionSeconds, m.totalDurationSeconds),
                        mainTitle = m.title,
                        subtitle = sub,
                        streamUrl = m.streamUrl,
                        lastPositionSeconds = m.lastPositionSeconds,
                        totalDurationSeconds = m.totalDurationSeconds,
                        watchedAtTimestamp = m.watchedAtTimestamp,
                        progressPercent = progressRatio,
                        isCompleted = m.isCompleted,
                        posterUrl = m.posterUrl
                    )
                }

                val seriesItems = seriesEpisodes.map { ep ->
                    val progressRatio = if (ep.totalDurationSeconds > 0) {
                        (ep.lastPositionSeconds.toFloat() / ep.totalDurationSeconds.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                    val sStr = String.format(Locale.US, "S%02dE%02d", ep.seasonNumber.coerceAtLeast(1), ep.episodeNumber.coerceAtLeast(1))
                    val posStr = formatTime(ep.lastPositionSeconds)
                    val durStr = if (ep.totalDurationSeconds > 0) formatTime(ep.totalDurationSeconds) else null
                    val sub = if (durStr != null) "$sStr ($posStr / $durStr)" else "$sStr ($posStr)"

                    VodResumeItem(
                        id = ep.episodeId,
                        isSeries = true,
                        seriesId = ep.seriesId,
                        seriesTitle = ep.seriesTitle,
                        title = formatSeriesDisplayTitle(
                            ep.seriesTitle,
                            ep.seasonNumber,
                            ep.episodeNumber,
                            ep.lastPositionSeconds,
                            ep.totalDurationSeconds
                        ),
                        mainTitle = ep.seriesTitle,
                        subtitle = sub,
                        seasonNumber = ep.seasonNumber,
                        episodeNumber = ep.episodeNumber,
                        episodeId = ep.episodeId,
                        streamUrl = ep.streamUrl,
                        lastPositionSeconds = ep.lastPositionSeconds,
                        totalDurationSeconds = ep.totalDurationSeconds,
                        watchedAtTimestamp = ep.watchedAtTimestamp,
                        progressPercent = progressRatio,
                        isCompleted = ep.isCompleted,
                        posterUrl = ep.posterUrl
                    )
                }

                (movieItems + seriesItems).sortedByDescending { it.watchedAtTimestamp }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error creating all history flow: ${e.message}", e)
            flowOf(emptyList())
        }
    }
}
