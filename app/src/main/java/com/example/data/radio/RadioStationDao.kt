package com.example.data.radio

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RadioStationDao {

    @Query(
        """
        SELECT * FROM radio_stations
        ORDER BY
            isFavorite DESC,
            isDeutschrock DESC,
            CASE countryCode
                WHEN 'DE' THEN 0
                WHEN 'AT' THEN 1
                WHEN 'CH' THEN 2
                ELSE 3
            END ASC,
            votes DESC,
            clickCount DESC,
            name COLLATE NOCASE ASC
        """
    )
    fun observeAllStations(): Flow<List<RadioStationEntity>>

    @Query("SELECT COUNT(*) FROM radio_stations")
    fun observeStationCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM radio_stations")
    suspend fun getStationCount(): Int

    @Query("SELECT stationUuid FROM radio_stations WHERE isFavorite = 1")
    suspend fun getFavoriteUuids(): List<String>

    @Query("SELECT * FROM radio_stations WHERE stationUuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): RadioStationEntity?

    @Query("SELECT MAX(lastSyncedAt) FROM radio_stations")
    suspend fun getLastSyncedAt(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(stations: List<RadioStationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(station: RadioStationEntity)

    @Query("UPDATE radio_stations SET isFavorite = :isFavorite WHERE stationUuid = :uuid")
    suspend fun setFavorite(uuid: String, isFavorite: Boolean)

    @Query("DELETE FROM radio_stations WHERE isFavorite = 0 AND stationUuid NOT IN (:keepUuids)")
    suspend fun pruneNonFavoritesNotIn(keepUuids: List<String>)
}
