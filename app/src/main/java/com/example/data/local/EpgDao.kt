package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.EpgProgram
import kotlinx.coroutines.flow.Flow

@Dao
interface EpgDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgram(program: EpgProgram)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrograms(programs: List<EpgProgram>)

    suspend fun insertProgramsBatch(programs: List<EpgProgram>, chunkSize: Int = 500) {
        if (programs.isEmpty()) return
        programs.chunked(chunkSize).forEach { chunk ->
            insertPrograms(chunk)
            kotlinx.coroutines.yield()
        }
    }

    @Update
    suspend fun updateProgram(program: EpgProgram)

    @Delete
    suspend fun deleteProgram(program: EpgProgram)

    @Query("SELECT * FROM epg_programs WHERE channelTvgId = :channelTvgId ORDER BY startTime ASC")
    fun getProgramsForChannel(channelTvgId: String): Flow<List<EpgProgram>>

    @Query("SELECT * FROM epg_programs WHERE channelTvgId = :channelTvgId ORDER BY startTime ASC")
    suspend fun getProgramsForChannelSync(channelTvgId: String): List<EpgProgram>

    @Query("""
        SELECT * FROM epg_programs 
        WHERE channelTvgId IN (:candidateKeys) 
           OR channelName IN (:candidateKeys)
           OR LOWER(channelTvgId) IN (:candidateKeys) 
           OR LOWER(channelName) IN (:candidateKeys)
        ORDER BY startTime ASC
    """)
    suspend fun getProgramsForCandidateKeys(candidateKeys: List<String>): List<EpgProgram>

    @Query("""
        SELECT * FROM epg_programs 
        WHERE (channelTvgId IN (:candidateKeys) OR channelName IN (:candidateKeys)
               OR LOWER(channelTvgId) IN (:candidateKeys) OR LOWER(channelName) IN (:candidateKeys))
          AND startTime <= :currentTime 
          AND endTime > :currentTime 
        LIMIT 1
    """)
    suspend fun getCurrentProgramForCandidateKeys(candidateKeys: List<String>, currentTime: Long): EpgProgram?

    @Query("""
        SELECT * FROM epg_programs 
        WHERE (channelTvgId IN (:candidateKeys) OR channelName IN (:candidateKeys)
               OR LOWER(channelTvgId) IN (:candidateKeys) OR LOWER(channelName) IN (:candidateKeys))
          AND startTime > :currentTime 
        ORDER BY startTime ASC 
        LIMIT 1
    """)
    suspend fun getNextProgramForCandidateKeys(candidateKeys: List<String>, currentTime: Long): EpgProgram?

    @Query("""
        SELECT * FROM epg_programs 
        WHERE channelTvgId = :channelTvgId 
           OR channelTvgId = :canonicalKey 
           OR channelName = :channelName 
        ORDER BY startTime ASC
    """)
    suspend fun getProgramsForChannelFlexibleSync(
        channelTvgId: String,
        canonicalKey: String,
        channelName: String
    ): List<EpgProgram>

    @Query("""
        SELECT * FROM epg_programs 
        WHERE channelTvgId = :channelTvgId 
          AND endTime >= :startTimestamp 
          AND startTime <= :endTimestamp 
        ORDER BY startTime ASC
    """)
    fun getProgramsForChannelAndTimeRange(
        channelTvgId: String,
        startTimestamp: Long,
        endTimestamp: Long
    ): Flow<List<EpgProgram>>

    @Query("""
        SELECT * FROM epg_programs 
        WHERE (channelName = :channelName OR channelTvgId = :channelName)
          AND endTime >= :startTimestamp 
          AND startTime <= :endTimestamp 
        ORDER BY startTime ASC
    """)
    fun getProgramsByChannelNameAndTimeRange(
        channelName: String,
        startTimestamp: Long,
        endTimestamp: Long
    ): Flow<List<EpgProgram>>

    @Query("""
        SELECT * FROM epg_programs 
        WHERE (channelTvgId = :channelTvgId OR channelName = :channelName)
          AND startTime <= :currentTime 
          AND endTime > :currentTime 
        LIMIT 1
    """)
    suspend fun getCurrentProgramForChannelSync(
        channelTvgId: String,
        channelName: String,
        currentTime: Long
    ): EpgProgram?

    @Query("""
        SELECT * FROM epg_programs 
        WHERE channelTvgId = :channelTvgId 
          AND startTime <= :currentTime 
          AND endTime > :currentTime 
        LIMIT 1
    """)
    fun getCurrentProgramForChannel(channelTvgId: String, currentTime: Long): Flow<EpgProgram?>

    @Query("""
        SELECT * FROM epg_programs 
        WHERE channelTvgId = :channelTvgId 
          AND startTime <= :currentTime 
          AND endTime > :currentTime 
        LIMIT 1
    """)
    suspend fun getCurrentProgramForChannelSync(channelTvgId: String, currentTime: Long): EpgProgram?

    @Query("""
        SELECT * FROM epg_programs 
        WHERE channelTvgId = :channelTvgId 
          AND startTime >= :currentTime 
        ORDER BY startTime ASC 
        LIMIT 1
    """)
    fun getNextProgramForChannel(channelTvgId: String, currentTime: Long): Flow<EpgProgram?>

    @Query("""
        SELECT * FROM epg_programs 
        WHERE channelTvgId = :channelTvgId 
          AND startTime >= :currentTime 
        ORDER BY startTime ASC 
        LIMIT :limit
    """)
    fun getUpcomingProgramsForChannel(
        channelTvgId: String,
        currentTime: Long,
        limit: Int = 10
    ): Flow<List<EpgProgram>>

    @Query("""
        SELECT * FROM epg_programs 
        WHERE startTime <= :currentTime 
          AND endTime > :currentTime 
        ORDER BY startTime ASC
    """)
    fun getAllCurrentPrograms(currentTime: Long): Flow<List<EpgProgram>>

    @Query("""
        SELECT * FROM epg_programs 
        WHERE (title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%')
          AND endTime >= :currentTime 
        ORDER BY startTime ASC
    """)
    fun searchPrograms(query: String, currentTime: Long): Flow<List<EpgProgram>>

    @Query("SELECT COUNT(*) FROM epg_programs")
    suspend fun getProgramCount(): Int

    @Query("SELECT COUNT(*) FROM epg_programs")
    fun getProgramCountFlow(): Flow<Int>

    @Query("DELETE FROM epg_programs WHERE endTime < :cutoffTimestamp")
    suspend fun deleteOldPrograms(cutoffTimestamp: Long)

    @Query("DELETE FROM epg_programs WHERE channelTvgId = :channelTvgId")
    suspend fun deleteProgramsForChannel(channelTvgId: String)

    @Query("DELETE FROM epg_programs WHERE sourceUrl = :sourceUrl")
    suspend fun deleteProgramsBySource(sourceUrl: String)

    @Query("""
        SELECT * FROM epg_programs 
        WHERE endTime >= :windowStart AND startTime <= :windowEnd
        ORDER BY startTime ASC
    """)
    suspend fun getProgramsInTimeWindow(windowStart: Long, windowEnd: Long): List<EpgProgram>

    @Query("SELECT DISTINCT channelTvgId FROM epg_programs")
    suspend fun getAllEpgChannelIds(): List<String>

    @Query("SELECT DISTINCT channelName FROM epg_programs WHERE channelName != ''")
    suspend fun getAllEpgChannelNames(): List<String>

    @Query("SELECT DISTINCT channelTvgId FROM epg_programs WHERE channelTvgId != ''")
    fun getAllEpgChannelIdsFlow(): Flow<List<String>>

    @Query("""
        SELECT channelTvgId, MAX(channelName) AS channelName, COUNT(*) AS programCount
        FROM epg_programs
        WHERE channelTvgId != ''
        GROUP BY channelTvgId
        ORDER BY programCount DESC
    """)
    suspend fun getDistinctEpgChannels(): List<EpgChannelTuple>

    @Query("""
        SELECT * FROM epg_programs
        WHERE channelTvgId IN (:channelTvgIds)
          AND endTime >= :startTimestamp
          AND startTime <= :endTimestamp
        ORDER BY startTime ASC
    """)
    suspend fun getProgramsForChannelsInWindow(
        channelTvgIds: List<String>,
        startTimestamp: Long,
        endTimestamp: Long
    ): List<EpgProgram>

    @Query("DELETE FROM epg_programs")
    suspend fun clearAllEpg()
}

data class EpgChannelTuple(
    val channelTvgId: String,
    val channelName: String,
    val programCount: Int = 0
)

