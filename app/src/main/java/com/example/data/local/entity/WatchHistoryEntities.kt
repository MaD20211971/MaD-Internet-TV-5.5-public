package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted playback progress for VOD Movies.
 * Tracks exact position and total duration in seconds.
 */
@Entity(
    tableName = "movie_watch_history",
    indices = [
        Index(value = ["watched_at_timestamp"]),
        Index(value = ["is_completed"]),
        Index(value = ["watched_at_timestamp", "is_completed"])
    ]
)
data class MovieWatchHistoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "movie_id")
    val movieId: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "stream_url")
    val streamUrl: String,

    @ColumnInfo(name = "last_position_seconds")
    val lastPositionSeconds: Long,

    @ColumnInfo(name = "total_duration_seconds")
    val totalDurationSeconds: Long,

    @ColumnInfo(name = "watched_at_timestamp")
    val watchedAtTimestamp: Long,

    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean = false,

    @ColumnInfo(name = "poster_url")
    val posterUrl: String? = null
)

/**
 * Persisted playback progress for VOD Series Episodes.
 * Stores overarching series title, season and episode numbers,
 * and stable series_id and episode_id to withstand provider URL rotations.
 */
@Entity(
    tableName = "series_watch_history",
    indices = [
        Index(value = ["series_id"]),
        Index(value = ["episode_id"]),
        Index(value = ["watched_at_timestamp"]),
        Index(value = ["is_completed"]),
        Index(value = ["series_id", "watched_at_timestamp"])
    ]
)
data class SeriesWatchHistoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "episode_id")
    val episodeId: String,

    @ColumnInfo(name = "series_id")
    val seriesId: String,

    @ColumnInfo(name = "series_title")
    val seriesTitle: String,

    @ColumnInfo(name = "season_number")
    val seasonNumber: Int,

    @ColumnInfo(name = "episode_number")
    val episodeNumber: Int,

    @ColumnInfo(name = "episode_title")
    val episodeTitle: String = "",

    @ColumnInfo(name = "stream_url")
    val streamUrl: String,

    @ColumnInfo(name = "last_position_seconds")
    val lastPositionSeconds: Long,

    @ColumnInfo(name = "total_duration_seconds")
    val totalDurationSeconds: Long,

    @ColumnInfo(name = "watched_at_timestamp")
    val watchedAtTimestamp: Long,

    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean = false,

    @ColumnInfo(name = "poster_url")
    val posterUrl: String? = null
)
