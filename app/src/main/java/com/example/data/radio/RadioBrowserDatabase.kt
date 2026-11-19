package com.example.data.radio

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Isolated local Room database exclusively for cached radio-browser.info stations.
 * Keeps the protected main AppDatabase untouched and migration-free.
 */
@Database(
    entities = [RadioStationEntity::class],
    version = 1,
    exportSchema = false
)
abstract class RadioBrowserDatabase : RoomDatabase() {

    abstract fun radioStationDao(): RadioStationDao

    companion object {
        @Volatile
        private var INSTANCE: RadioBrowserDatabase? = null

        fun getDatabase(context: Context): RadioBrowserDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RadioBrowserDatabase::class.java,
                    "radio_browser.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
