package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.ChannelItem
import com.example.data.model.ContentType
import com.example.data.model.EpgProgram
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabasePerformanceTest {

    private lateinit var database: AppDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testPragmaAndWalConfiguration() {
        val db = database.openHelper.writableDatabase
        val cursor = db.query("PRAGMA temp_store")
        var tempStore = -1
        if (cursor.moveToFirst()) {
            tempStore = cursor.getInt(0)
        }
        cursor.close()
        // temp_store in memory or default
        assertTrue("Database helper is active and queryable", tempStore >= 0)

        // Test WAL checkpoint function executes without exception
        database.checkpointWal("PASSIVE")
    }

    @Test
    fun testChannelIndexedLookups() = runBlocking {
        val channelDao = database.channelDao()

        val channels = (1..50).map { i ->
            ChannelItem(
                id = "ch_$i",
                playlistId = "p1",
                name = "Sender $i HD",
                streamUrl = "http://stream/$i",
                tvgId = "tvg_id_$i",
                epgId = "epg_id_$i",
                countryCode = if (i % 2 == 0) "DE" else "AT",
                isFavorite = (i <= 5)
            )
        }

        channelDao.insertChannels(channels)

        // 1. Indexed lookup by tvgId
        val foundByTvg = channelDao.getChannelByTvgId("tvg_id_10")
        assertNotNull("Channel with tvgId should be found via index", foundByTvg)
        assertEquals("Sender 10 HD", foundByTvg?.name)

        // 2. Indexed lookup by epgId
        val foundByEpg = channelDao.getChannelByTvgId("epg_id_25")
        assertNotNull("Channel with epgId should be found via index", foundByEpg)
        assertEquals("Sender 25 HD", foundByEpg?.name)

        // 3. Batch lookup by tvgIds
        val batchFound = channelDao.getChannelsByTvgIds(listOf("tvg_id_1", "tvg_id_2", "tvg_id_3"))
        assertEquals(3, batchFound.size)

        // 4. Country paged query with name ordering
        val pagedDe = channelDao.getChannelsByCountryPaged(
            countryCode = "DE",
            playlistId = "p1",
            type = null,
            groupTitle = null,
            hiddenIds = emptyList(),
            limit = 10,
            offset = 0
        )
        assertTrue(pagedDe.isNotEmpty())
        assertEquals("DE", pagedDe.first().countryCode)

        // 5. Favorites query with name ordering
        val favorites = channelDao.getFavoritesPaged(
            countryCode = null,
            playlistId = "p1",
            hiddenIds = emptyList(),
            limit = 10,
            offset = 0
        )
        assertEquals(5, favorites.size)
    }

    @Test
    fun testEpgBatchInsertionAndWindowQueryPerformance() = runBlocking {
        val epgDao = database.epgDao()
        val now = System.currentTimeMillis()

        // Generate 1,000 EPG programs
        val programs = (1..1000).map { i ->
            val start = now + (i * 1000L)
            val end = start + 3600000L
            EpgProgram(
                id = "prog_$i",
                channelTvgId = "ch_${i % 50}",
                channelName = "Channel ${i % 50}",
                title = "Show $i",
                description = "Description for show $i",
                startTime = start,
                endTime = end,
                category = "Unterhaltung"
            )
        }

        // Test bulk batch insertion inside transaction
        val insertStart = System.currentTimeMillis()
        epgDao.insertProgramsBatch(programs, chunkSize = 500)
        val insertDuration = System.currentTimeMillis() - insertStart

        val count = epgDao.getProgramCount()
        assertEquals(1000, count)
        assertTrue("Batch insertion of 1,000 programs should be fast (took $insertDuration ms)", insertDuration < 5000)

        // Test window query
        val windowPrograms = epgDao.getProgramsInTimeWindow(now, now + 100000L)
        assertTrue("Window query should return matching programs", windowPrograms.isNotEmpty())

        // Test deleteOldPrograms with new endTime index
        val cutoff = now + 50000L + 3600000L
        epgDao.deleteOldPrograms(cutoff)
        val remainingCount = epgDao.getProgramCount()
        assertTrue("Old programs should have been deleted", remainingCount < 1000)
    }

    @Test
    fun testFtsSearchPerformance() = runBlocking {
        val channelDao = database.channelDao()

        val sampleChannels = listOf(
            ChannelItem(id = "ch_ard", playlistId = "p1", name = "Das Erste HD", streamUrl = "http://1", tvgId = "daserste.de"),
            ChannelItem(id = "ch_zdf", playlistId = "p1", name = "ZDF HD", streamUrl = "http://2", tvgId = "zdf.de"),
            ChannelItem(id = "ch_sky_bundesliga", playlistId = "p1", name = "Sky Sport Bundesliga 1 HD", streamUrl = "http://3", tvgId = "sky1.de"),
            ChannelItem(id = "ch_sky_cinema", playlistId = "p1", name = "Sky Cinema Action HD", streamUrl = "http://4", tvgId = "sky2.de"),
            ChannelItem(id = "ch_rtl", playlistId = "p1", name = "RTL Television HD", streamUrl = "http://5", tvgId = "rtl.de")
        )

        channelDao.insertChannels(sampleChannels)
        val ftsEntries = sampleChannels.map {
            com.example.data.local.entity.ChannelFts(
                channelId = it.id,
                name = it.name,
                normalizedTitle = it.name.lowercase(),
                groupTitle = it.groupTitle ?: ""
            )
        }
        channelDao.insertFtsRaw(ftsEntries)

        // FTS search for "Sky*"
        val ftsStart = System.currentTimeMillis()
        val skyResults = channelDao.searchChannelsFts("Sky*", limit = 10, offset = 0)
        val ftsDuration = System.currentTimeMillis() - ftsStart

        assertEquals(2, skyResults.size)
        assertTrue("FTS search must be sub-500ms in virtualized test runner (took $ftsDuration ms)", ftsDuration < 500)

        // FTS search for "Erste*"
        val ersteResults = channelDao.searchChannelsFts("Erste*", limit = 10, offset = 0)
        assertEquals(1, ersteResults.size)
        assertEquals("Das Erste HD", ersteResults.first().name)
    }

    @Test
    fun testPagingSourceInitialization() {
        val channelDao = database.channelDao()
        val pagingSource = channelDao.getChannelsPagingSource(countryCode = null, playlistId = null, type = null, groupTitle = null)
        assertNotNull("PagingSource should be created successfully", pagingSource)
    }

    @Test
    fun testMediaCacheHolder() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val cache = com.example.data.player.MediaCacheHolder.getCache(context)
        assertNotNull("MediaCacheHolder should provide a valid SimpleCache", cache)
    }
}
