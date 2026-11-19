package com.example.data.database.entity

data class EpgSourceEntity(
    val id: String = "",
    val name: String = "",
    val url: String = "",
    val isEnabled: Boolean = true,
    val retentionDays: Int = 7
)

data class ManualEpgMappingEntity(
    val channelId: String = "",
    val channelName: String = "",
    val tvgId: String? = null,
    val epgId: String = "",
    val mappedName: String = ""
)
