package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.MovieWatchHistoryEntity
import com.example.data.local.entity.SeriesWatchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {

    // ==========================================
    // MOVIES
    // ==========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateMovie(item: MovieWatchHistoryEntity)

    @Query("SELECT * FROM movie_watch_history WHERE movie_id = :movieId LIMIT 1")
    suspend fun getMovie(movieId: String): MovieWatchHistoryEntity?

    @Query("SELECT * FROM movie_watch_history ORDER BY watched_at_timestamp DESC")
    fun getAllMoviesFlow(): Flow<List<MovieWatchHistoryEntity>>

    @Query("SELECT * FROM movie_watch_history WHERE is_completed = 0 AND last_position_seconds > 5 ORDER BY watched_at_timestamp DESC")
    fun getActiveResumeMoviesFlow(): Flow<List<MovieWatchHistoryEntity>>

    @Query("DELETE FROM movie_watch_history WHERE movie_id = :movieId")
    suspend fun deleteMovie(movieId: String)

    @Query("DELETE FROM movie_watch_history")
    suspend fun clearAllMovies()

    // ==========================================
    // SERIES
    // ==========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSeries(item: SeriesWatchHistoryEntity)

    @Query("SELECT * FROM series_watch_history WHERE episode_id = :episodeId LIMIT 1")
    suspend fun getEpisode(episodeId: String): SeriesWatchHistoryEntity?

    @Query("SELECT * FROM series_watch_history WHERE series_id = :seriesId ORDER BY watched_at_timestamp DESC LIMIT 1")
    suspend fun getLatestWatchedEpisodeForSeries(seriesId: String): SeriesWatchHistoryEntity?

    @Query("SELECT * FROM series_watch_history ORDER BY watched_at_timestamp DESC")
    fun getAllSeriesEpisodesFlow(): Flow<List<SeriesWatchHistoryEntity>>

    @Query("SELECT * FROM series_watch_history WHERE is_completed = 0 AND last_position_seconds > 5 ORDER BY watched_at_timestamp DESC")
    fun getActiveResumeSeriesFlow(): Flow<List<SeriesWatchHistoryEntity>>

    @Query("DELETE FROM series_watch_history WHERE episode_id = :episodeId")
    suspend fun deleteEpisode(episodeId: String)

    @Query("DELETE FROM series_watch_history WHERE series_id = :seriesId")
    suspend fun deleteEntireSeries(seriesId: String)

    @Query("DELETE FROM series_watch_history")
    suspend fun clearAllSeries()
}
