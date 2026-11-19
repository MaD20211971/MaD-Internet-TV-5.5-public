package com.example.app.di

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.player.PlayerManager
import com.example.data.radio.RadioBrowserRepository
import com.example.data.repository.ContentRepository

interface AppContainer {
    val database: AppDatabase
    val playerManager: PlayerManager
    val contentRepository: ContentRepository
    val radioBrowserRepository: RadioBrowserRepository
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    override val database: AppDatabase by lazy {
        AppDatabase.getDatabase(context)
    }

    override val playerManager: PlayerManager by lazy {
        PlayerManager(context)
    }

    override val contentRepository: ContentRepository by lazy {
        ContentRepository(database.channelDao())
    }

    override val radioBrowserRepository: RadioBrowserRepository by lazy {
        RadioBrowserRepository(context)
    }
}
