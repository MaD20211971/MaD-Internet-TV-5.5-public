package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Entity(tableName = "channels_fts")
@Fts4
data class ChannelFts(
    val channelId: String,
    val name: String,
    val normalizedTitle: String,
    val groupTitle: String
)
