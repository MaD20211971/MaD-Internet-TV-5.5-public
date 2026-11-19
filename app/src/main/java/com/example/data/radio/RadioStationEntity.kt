package com.example.data.radio

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.ChannelItem
import com.example.data.model.ContentType

@Immutable
@Entity(
    tableName = "radio_stations",
    indices = [
        Index(value = ["isDeutschrock"]),
        Index(value = ["isFavorite"]),
        Index(value = ["countryCode"]),
        Index(value = ["codec"]),
        Index(value = ["name"])
    ]
)
data class RadioStationEntity(
    @PrimaryKey
    val stationUuid: String,
    val name: String,
    val streamUrl: String,
    val homepage: String = "",
    val favicon: String = "",
    val countryCode: String = "INT",
    val country: String = "",
    val language: String = "",
    val tags: String = "",
    val codec: String = "MP3",
    val bitrate: Int = 0,
    val votes: Int = 0,
    val clickCount: Int = 0,
    val isDeutschrock: Boolean = false,
    val isFavorite: Boolean = false,
    val lastSyncedAt: Long = System.currentTimeMillis()
) {
    fun normalizedCodec(): String {
        val upper = codec.trim().uppercase()
        return when {
            upper.contains("MP3") || upper.contains("MPEG") -> "MP3"
            upper.contains("AAC+") || upper.contains("AACP") -> "AAC+"
            upper.contains("AAC") -> "AAC"
            upper.contains("OGG") -> "OGG"
            upper.contains("OPUS") -> "OPUS"
            upper.contains("FLAC") -> "FLAC"
            upper.isNotBlank() -> upper.take(6)
            streamUrl.contains(".mp3", ignoreCase = true) -> "MP3"
            streamUrl.contains(".aac", ignoreCase = true) -> "AAC"
            streamUrl.contains(".ogg", ignoreCase = true) -> "OGG"
            else -> "AUDIO"
        }
    }

    fun isMp3Stream(): Boolean {
        val c = normalizedCodec()
        return c == "MP3" || streamUrl.contains(".mp3", ignoreCase = true)
    }

    fun qualityBadge(): String {
        val c = normalizedCodec()
        return if (bitrate > 0) "$c • ${bitrate}k" else c
    }

    fun displayCategory(): String {
        val cleanTags = tags.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(3)
            .joinToString(" • ")
        return when {
            isDeutschrock && cleanTags.isNotBlank() -> "Deutschrock • $cleanTags"
            isDeutschrock -> "Deutschrock"
            cleanTags.isNotBlank() -> cleanTags
            country.isNotBlank() -> country
            else -> "Internet Radio"
        }
    }

    fun toChannelItem(): ChannelItem {
        val cc = countryCode.trim().uppercase().ifBlank { "INT" }
        val dach = cc == "DE" || cc == "AT" || cc == "CH"
        return ChannelItem(
            id = "$CHANNEL_ID_PREFIX$stationUuid",
            playlistId = RADIO_BROWSER_PLAYLIST_ID,
            name = name,
            streamUrl = streamUrl,
            logoUrl = favicon.takeIf { it.isNotBlank() },
            groupTitle = displayCategory(),
            type = ContentType.RADIO,
            isDach = dach,
            countryCode = cc,
            resolutionBadge = qualityBadge(),
            isFavorite = isFavorite,
            language = language.takeIf { it.isNotBlank() },
            category = if (isDeutschrock) "Deutschrock" else "Radio",
            genre = tags.takeIf { it.isNotBlank() },
            providerId = RADIO_BROWSER_PLAYLIST_ID
        )
    }

    companion object {
        const val RADIO_BROWSER_PLAYLIST_ID = "radio_browser_local_db"
        const val CHANNEL_ID_PREFIX = "rb_"

        private val DEUTSCHROCK_KEYWORDS = listOf(
            "deutschrock",
            "deutsch-rock",
            "deutsch rock",
            "german rock",
            "germanrock",
            "ostrock",
            "deutschpunk",
            "deutsch punk",
            "böhse onkelz",
            "boehse onkelz",
            "onkelz",
            "frei.wild",
            "freiwild",
            "rammstein",
            "toten hosen",
            "broilers",
            "in extremo",
            "unheilig",
            "betontod",
            "kärbholz",
            "kaerbholz",
            "goitzsche front",
            "deutschmetal",
            "neue deutsche härte",
            "neue deutsche haerte"
        )

        fun detectDeutschrock(name: String, tags: String): Boolean {
            val combined = "${name.lowercase()} ${tags.lowercase()}"
            if (DEUTSCHROCK_KEYWORDS.any { combined.contains(it) }) {
                return true
            }
            val hasGerman = combined.contains("deutsch") || combined.contains("german")
            val hasRock = combined.contains("rock")
            return hasGerman && hasRock && (
                combined.contains("deutschrock") ||
                    name.lowercase().contains("deutsch") && name.lowercase().contains("rock")
                )
        }

        fun extractUuidFromChannelId(channelId: String): String? {
            return if (channelId.startsWith(CHANNEL_ID_PREFIX)) {
                channelId.removePrefix(CHANNEL_ID_PREFIX).takeIf { it.isNotBlank() }
            } else {
                null
            }
        }
    }
}
