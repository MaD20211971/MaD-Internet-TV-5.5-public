package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.PlaylistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY lastUpdated DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY lastUpdated DESC")
    suspend fun getAllPlaylistsSync(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    suspend fun getPlaylistById(id: String): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistsRaw(playlists: List<PlaylistEntity>)

    @androidx.room.Transaction
    suspend fun insertPlaylists(playlists: List<PlaylistEntity>, chunkSize: Int = 500) {
        if (playlists.isEmpty()) return
        if (playlists.size <= chunkSize) {
            insertPlaylistsRaw(playlists)
        } else {
            playlists.chunked(chunkSize).forEach { chunk ->
                insertPlaylistsRaw(chunk)
            }
        }
    }

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("UPDATE playlists SET lastUpdated = :lastUpdated WHERE id = :id")
    suspend fun updatePlaylistTimestamp(id: String, lastUpdated: Long = System.currentTimeMillis())

    @Query("""
        UPDATE playlists 
        SET liveCount = :live, 
            radioCount = :radio, 
            movieCount = :movies, 
            seriesCount = :series, 
            lastUpdated = :lastUpdated 
        WHERE id = :id
    """)
    suspend fun updatePlaylistStatistics(
        id: String,
        live: Int,
        radio: Int,
        movies: Int,
        series: Int,
        lastUpdated: Long = System.currentTimeMillis()
    )

    @androidx.room.Transaction
    @Query("UPDATE playlists SET isActive = CASE WHEN id = :playlistId THEN 1 ELSE 0 END")
    suspend fun setActivePlaylist(playlistId: String)

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylistById(id: String)

    @Query("DELETE FROM playlists")
    suspend fun deleteAllPlaylists()
}
