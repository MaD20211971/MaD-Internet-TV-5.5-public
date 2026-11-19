package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.entity.ChannelFts
import com.example.data.model.ChannelItem
import com.example.data.model.EpgProgram
import com.example.data.model.PlaylistEntity
import com.example.data.model.ProviderCategoryEntity
import com.example.data.model.RecordingItem

@Database(
    entities = [
        PlaylistEntity::class,
        ChannelItem::class,
        ProviderCategoryEntity::class,
        RecordingItem::class,
        EpgProgram::class,
        ChannelFts::class,
        com.example.data.local.entity.MovieWatchHistoryEntity::class,
        com.example.data.local.entity.SeriesWatchHistoryEntity::class
    ],
    version = 17,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun playlistDao(): PlaylistDao
    abstract fun channelDao(): ChannelDao
    abstract fun recordingDao(): RecordingDao
    abstract fun epgDao(): EpgDao
    abstract fun watchHistoryDao(): WatchHistoryDao

    fun checkpointWal(mode: String = "PASSIVE") {
        try {
            openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint($mode)")
        } catch (_: Throwable) {}
    }

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE recordings ADD COLUMN progressPercent INTEGER NOT NULL DEFAULT 100")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Providers & Credentials
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS providers (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        type TEXT NOT NULL,
                        enabled INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        lastSyncAt INTEGER NOT NULL DEFAULT 0,
                        lastSuccessfulSyncAt INTEGER NOT NULL DEFAULT 0,
                        status TEXT NOT NULL DEFAULT 'IDLE',
                        errorCode TEXT
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_providers_enabled ON providers(enabled)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_providers_type ON providers(type)")

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS provider_credentials (
                        id TEXT NOT NULL PRIMARY KEY,
                        providerId TEXT NOT NULL,
                        username TEXT,
                        encryptedPassword TEXT,
                        portalUrl TEXT,
                        macAddress TEXT,
                        token TEXT,
                        extraJson TEXT,
                        isEncrypted INTEGER NOT NULL DEFAULT 1,
                        updatedAt INTEGER NOT NULL
                    )
                """)
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_provider_credentials_providerId ON provider_credentials(providerId)")

                // Channels & Streams
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS unified_channels (
                        id TEXT NOT NULL PRIMARY KEY,
                        providerId TEXT NOT NULL,
                        externalId TEXT,
                        name TEXT NOT NULL,
                        normalizedName TEXT NOT NULL,
                        streamUrl TEXT NOT NULL,
                        logoUrl TEXT,
                        categoryId TEXT,
                        countryId TEXT,
                        languageId TEXT,
                        epgId TEXT,
                        channelNumber INTEGER,
                        resolution TEXT NOT NULL DEFAULT 'HD',
                        enabled INTEGER NOT NULL DEFAULT 1,
                        isFavorite INTEGER NOT NULL DEFAULT 0,
                        contentHash TEXT,
                        syncStatus TEXT NOT NULL DEFAULT 'ACTIVE',
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_unified_channels_providerId ON unified_channels(providerId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_unified_channels_countryId ON unified_channels(countryId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_unified_channels_categoryId ON unified_channels(categoryId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_unified_channels_languageId ON unified_channels(languageId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_unified_channels_normalizedName ON unified_channels(normalizedName)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_unified_channels_epgId ON unified_channels(epgId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_unified_channels_externalId ON unified_channels(externalId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_unified_channels_enabled ON unified_channels(enabled)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_unified_channels_isFavorite ON unified_channels(isFavorite)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_unified_channels_countryId_enabled_isFavorite ON unified_channels(countryId, enabled, isFavorite)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_unified_channels_providerId_externalId ON unified_channels(providerId, externalId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_unified_channels_countryId_categoryId_enabled ON unified_channels(countryId, categoryId, enabled)")

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS channel_streams (
                        id TEXT NOT NULL PRIMARY KEY,
                        channelId TEXT NOT NULL,
                        streamUrl TEXT NOT NULL,
                        priority INTEGER NOT NULL DEFAULT 0,
                        status TEXT NOT NULL DEFAULT 'ACTIVE',
                        lastTestedAt INTEGER NOT NULL DEFAULT 0,
                        lastError TEXT,
                        isEnabled INTEGER NOT NULL DEFAULT 1
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_channel_streams_channelId ON channel_streams(channelId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_channel_streams_channelId_priority ON channel_streams(channelId, priority)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_channel_streams_isEnabled ON channel_streams(isEnabled)")

                // Movies
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS movies (
                        id TEXT NOT NULL PRIMARY KEY,
                        providerId TEXT NOT NULL,
                        externalId TEXT,
                        title TEXT NOT NULL,
                        normalizedTitle TEXT NOT NULL,
                        description TEXT,
                        poster TEXT,
                        backdrop TEXT,
                        year INTEGER,
                        genre TEXT,
                        countryId TEXT,
                        categoryId TEXT,
                        languageId TEXT,
                        streamUrl TEXT NOT NULL,
                        duration INTEGER NOT NULL DEFAULT 0,
                        rating REAL,
                        enabled INTEGER NOT NULL DEFAULT 1,
                        isFavorite INTEGER NOT NULL DEFAULT 0,
                        contentHash TEXT,
                        syncStatus TEXT NOT NULL DEFAULT 'ACTIVE',
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_movies_providerId ON movies(providerId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_movies_countryId ON movies(countryId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_movies_categoryId ON movies(categoryId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_movies_languageId ON movies(languageId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_movies_normalizedTitle ON movies(normalizedTitle)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_movies_externalId ON movies(externalId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_movies_year ON movies(year)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_movies_genre ON movies(genre)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_movies_enabled ON movies(enabled)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_movies_isFavorite ON movies(isFavorite)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_movies_countryId_enabled ON movies(countryId, enabled)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_movies_providerId_externalId ON movies(providerId, externalId)")

                // Series, Seasons, Episodes
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS series (
                        id TEXT NOT NULL PRIMARY KEY,
                        providerId TEXT NOT NULL,
                        externalId TEXT,
                        title TEXT NOT NULL,
                        normalizedTitle TEXT NOT NULL,
                        description TEXT,
                        poster TEXT,
                        backdrop TEXT,
                        year INTEGER,
                        genre TEXT,
                        countryId TEXT,
                        categoryId TEXT,
                        languageId TEXT,
                        enabled INTEGER NOT NULL DEFAULT 1,
                        isFavorite INTEGER NOT NULL DEFAULT 0,
                        contentHash TEXT,
                        syncStatus TEXT NOT NULL DEFAULT 'ACTIVE',
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_series_providerId ON series(providerId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_series_countryId ON series(countryId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_series_categoryId ON series(categoryId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_series_languageId ON series(languageId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_series_normalizedTitle ON series(normalizedTitle)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_series_externalId ON series(externalId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_series_year ON series(year)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_series_enabled ON series(enabled)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_series_isFavorite ON series(isFavorite)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_series_countryId_enabled ON series(countryId, enabled)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_series_providerId_externalId ON series(providerId, externalId)")

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS seasons (
                        id TEXT NOT NULL PRIMARY KEY,
                        seriesId TEXT NOT NULL,
                        seasonNumber INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        poster TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_seasons_seriesId ON seasons(seriesId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_seasons_seriesId_seasonNumber ON seasons(seriesId, seasonNumber)")

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS episodes (
                        id TEXT NOT NULL PRIMARY KEY,
                        seasonId TEXT NOT NULL,
                        seriesId TEXT NOT NULL,
                        providerId TEXT NOT NULL,
                        externalId TEXT,
                        episodeNumber INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        normalizedTitle TEXT NOT NULL,
                        description TEXT,
                        thumbnail TEXT,
                        streamUrl TEXT NOT NULL,
                        duration INTEGER NOT NULL DEFAULT 0,
                        contentHash TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_episodes_seasonId ON episodes(seasonId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_episodes_seriesId ON episodes(seriesId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_episodes_providerId ON episodes(providerId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_episodes_seasonId_episodeNumber ON episodes(seasonId, episodeNumber)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_episodes_normalizedTitle ON episodes(normalizedTitle)")

                // Countries & Languages
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS countries (
                        id TEXT NOT NULL PRIMARY KEY,
                        isoCode TEXT NOT NULL,
                        name TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        flagReference TEXT NOT NULL,
                        enabled INTEGER NOT NULL DEFAULT 1
                    )
                """)
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_countries_isoCode ON countries(isoCode)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_countries_enabled ON countries(enabled)")

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS languages (
                        id TEXT NOT NULL PRIMARY KEY,
                        isoCode TEXT NOT NULL,
                        name TEXT NOT NULL,
                        displayName TEXT NOT NULL
                    )
                """)
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_languages_isoCode ON languages(isoCode)")

                // Categories
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS categories (
                        id TEXT NOT NULL PRIMARY KEY,
                        providerId TEXT NOT NULL,
                        originalName TEXT NOT NULL,
                        normalizedName TEXT NOT NULL,
                        contentType TEXT NOT NULL,
                        parentId TEXT,
                        countryId TEXT,
                        customCategory TEXT,
                        fullHierarchyPath TEXT NOT NULL,
                        depth INTEGER NOT NULL DEFAULT 0
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_categories_providerId ON categories(providerId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_categories_contentType ON categories(contentType)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_categories_parentId ON categories(parentId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_categories_countryId ON categories(countryId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_categories_normalizedName ON categories(normalizedName)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_categories_providerId_contentType ON categories(providerId, contentType)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_categories_countryId_contentType ON categories(countryId, contentType)")

                // Favorites, Playback Progress, EPG
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS favorites (
                        id TEXT NOT NULL PRIMARY KEY,
                        contentType TEXT NOT NULL,
                        contentId TEXT NOT NULL,
                        normalizedTitle TEXT NOT NULL,
                        tvgId TEXT,
                        providerId TEXT,
                        createdAt INTEGER NOT NULL
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_favorites_contentType ON favorites(contentType)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_favorites_contentId ON favorites(contentId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_favorites_normalizedTitle ON favorites(normalizedTitle)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_favorites_tvgId ON favorites(tvgId)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_favorites_contentType_contentId ON favorites(contentType, contentId)")

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS playback_progress (
                        contentId TEXT NOT NULL PRIMARY KEY,
                        contentType TEXT NOT NULL,
                        positionMs INTEGER NOT NULL DEFAULT 0,
                        durationMs INTEGER NOT NULL DEFAULT 0,
                        isCompleted INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_playback_progress_contentType ON playback_progress(contentType)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_playback_progress_updatedAt ON playback_progress(updatedAt)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_playback_progress_isCompleted ON playback_progress(isCompleted)")

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS unified_epg_programs (
                        id TEXT NOT NULL PRIMARY KEY,
                        channelEpgId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT,
                        startEpochSecond INTEGER NOT NULL,
                        endEpochSecond INTEGER NOT NULL,
                        category TEXT,
                        seasonEpisode TEXT,
                        rating TEXT
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_unified_epg_programs_channelEpgId ON unified_epg_programs(channelEpgId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_unified_epg_programs_startEpochSecond ON unified_epg_programs(startEpochSecond)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_unified_epg_programs_endEpochSecond ON unified_epg_programs(endEpochSecond)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_unified_epg_programs_channelEpgId_startEpochSecond_endEpochSecond ON unified_epg_programs(channelEpgId, startEpochSecond, endEpochSecond)")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Manual EPG Mappings Table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS manual_epg_mappings (
                        channelId TEXT NOT NULL PRIMARY KEY,
                        channelName TEXT NOT NULL,
                        channelTvgId TEXT,
                        mappedEpgId TEXT NOT NULL,
                        mappedChannelName TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """)
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_manual_epg_mappings_channelId ON manual_epg_mappings(channelId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_manual_epg_mappings_channelTvgId ON manual_epg_mappings(channelTvgId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_manual_epg_mappings_mappedEpgId ON manual_epg_mappings(mappedEpgId)")

                // EPG Sources Table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS epg_sources (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        type TEXT NOT NULL,
                        url TEXT NOT NULL,
                        enabled INTEGER NOT NULL DEFAULT 1,
                        lastSyncAt INTEGER NOT NULL DEFAULT 0,
                        channelCount INTEGER NOT NULL DEFAULT 0,
                        programCount INTEGER NOT NULL DEFAULT 0,
                        statusMessage TEXT,
                        retentionDays INTEGER NOT NULL DEFAULT 7
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_epg_sources_name ON epg_sources(name)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_epg_sources_enabled ON epg_sources(enabled)")

                // Additional indices on epg_programs for fast cleanup and search
                try {
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_epg_programs_endTime ON epg_programs(endTime)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_epg_programs_title ON epg_programs(title)")
                } catch (ignored: Throwable) {}
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS movie_watch_history (
                        movie_id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        stream_url TEXT NOT NULL,
                        last_position_seconds INTEGER NOT NULL,
                        total_duration_seconds INTEGER NOT NULL,
                        watched_at_timestamp INTEGER NOT NULL,
                        is_completed INTEGER NOT NULL DEFAULT 0,
                        poster_url TEXT
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_movie_watch_history_watched_at_timestamp ON movie_watch_history(watched_at_timestamp)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_movie_watch_history_is_completed ON movie_watch_history(is_completed)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_movie_watch_history_watched_at_timestamp_is_completed ON movie_watch_history(watched_at_timestamp, is_completed)")

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS series_watch_history (
                        episode_id TEXT NOT NULL PRIMARY KEY,
                        series_id TEXT NOT NULL,
                        series_title TEXT NOT NULL,
                        season_number INTEGER NOT NULL,
                        episode_number INTEGER NOT NULL,
                        episode_title TEXT NOT NULL DEFAULT '',
                        stream_url TEXT NOT NULL,
                        last_position_seconds INTEGER NOT NULL,
                        total_duration_seconds INTEGER NOT NULL,
                        watched_at_timestamp INTEGER NOT NULL,
                        is_completed INTEGER NOT NULL DEFAULT 0,
                        poster_url TEXT
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_series_watch_history_series_id ON series_watch_history(series_id)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_series_watch_history_episode_id ON series_watch_history(episode_id)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_series_watch_history_watched_at_timestamp ON series_watch_history(watched_at_timestamp)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_series_watch_history_is_completed ON series_watch_history(is_completed)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_series_watch_history_series_id_watched_at_timestamp ON series_watch_history(series_id, watched_at_timestamp)")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE INDEX IF NOT EXISTS index_channels_playlistId_isDach_name ON channels(playlistId, isDach, name)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_channels_countryCode_isFavorite_name ON channels(countryCode, isFavorite, name)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_channels_playlistId_countryCode_isFavorite ON channels(playlistId, countryCode, isFavorite)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_channels_normalizedChannelId ON channels(normalizedChannelId)")
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    database.execSQL("ALTER TABLE channels ADD COLUMN parentCategory TEXT NOT NULL DEFAULT ''")
                } catch (_: Throwable) {}
                try {
                    database.execSQL("ALTER TABLE channels ADD COLUMN subCategory TEXT NOT NULL DEFAULT ''")
                } catch (_: Throwable) {}
                try {
                    database.execSQL("ALTER TABLE channels ADD COLUMN providerSortOrder INTEGER NOT NULL DEFAULT 0")
                } catch (_: Throwable) {}

                database.execSQL("CREATE INDEX IF NOT EXISTS index_channels_playlistId_type_countryCode_parentCategory_subCategory ON channels(playlistId, type, countryCode, parentCategory, subCategory)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_channels_playlistId_countryCode_providerSortOrder ON channels(playlistId, countryCode, providerSortOrder)")

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS provider_categories (
                        id TEXT NOT NULL PRIMARY KEY,
                        playlistId TEXT NOT NULL,
                        contentType TEXT NOT NULL,
                        categoryId TEXT NOT NULL,
                        rawCategoryName TEXT NOT NULL,
                        countryCode TEXT NOT NULL,
                        parentCategory TEXT NOT NULL,
                        subCategory TEXT NOT NULL,
                        sortOrder INTEGER NOT NULL DEFAULT 0,
                        itemCount INTEGER NOT NULL DEFAULT 0,
                        isSynced INTEGER NOT NULL DEFAULT 0
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_provider_categories_playlistId_contentType_countryCode_sortOrder ON provider_categories(playlistId, contentType, countryCode, sortOrder)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_provider_categories_playlistId_countryCode_isSynced ON provider_categories(playlistId, countryCode, isSynced)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val start = System.currentTimeMillis()
                val oldDb = context.getDatabasePath("mad_internet_tv.db")
                val newDb = context.getDatabasePath("iptv.db")
                if (!newDb.exists() && oldDb.exists()) {
                    try {
                        oldDb.renameTo(newDb)
                        val oldWal = java.io.File(oldDb.path + "-wal")
                        val newWal = java.io.File(newDb.path + "-wal")
                        if (oldWal.exists()) oldWal.renameTo(newWal)
                        val oldShm = java.io.File(oldDb.path + "-shm")
                        val newShm = java.io.File(newDb.path + "-shm")
                        if (oldShm.exists()) oldShm.renameTo(newShm)
                    } catch (ignored: Throwable) {}
                }

                var instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "iptv.db"
                ).setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                 .addCallback(object : RoomDatabase.Callback() {
                     override fun onOpen(db: SupportSQLiteDatabase) {
                         super.onOpen(db)
                         try {
                             db.execSQL("PRAGMA synchronous = NORMAL")
                             db.execSQL("PRAGMA temp_store = MEMORY")
                             db.execSQL("PRAGMA cache_size = -64000") // 64MB Cache
                             db.execSQL("PRAGMA mmap_size = 268435456") // 256MB memory-mapped I/O
                             db.execSQL("PRAGMA busy_timeout = 15000") // 15s timeout
                         } catch (ignored: Throwable) {}
                     }
                 })
                 .addMigrations(MIGRATION_5_6, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17)
                 .fallbackToDestructiveMigration(dropAllTables = true)
                 .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                 .build()

                try {
                    // Eagerly verify schema integrity to catch any corruption/migration issue before ViewModel runs
                    instance.openHelper.writableDatabase
                } catch (t: Throwable) {
                    android.util.Log.e("AppDatabase", "Error validating/opening database, performing clean rebuild: ${t.message}", t)
                    try { instance.close() } catch (_: Throwable) {}
                    try {
                        context.deleteDatabase("iptv.db")
                        context.deleteDatabase("mad_internet_tv.db")
                    } catch (_: Throwable) {}
                    instance = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "iptv.db"
                    ).setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                     .fallbackToDestructiveMigration(dropAllTables = true)
                     .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                     .build()
                    try {
                        instance.openHelper.writableDatabase
                    } catch (e: Throwable) {
                        android.util.Log.e("AppDatabase", "Fallback database open attempt failed: ${e.message}", e)
                    }
                }

                INSTANCE = instance
                com.example.util.PerformanceMonitor.logEvent("databaseOpenTime", System.currentTimeMillis() - start, "iptv.db")
                instance
            }
        }
    }
}
