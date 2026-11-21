package com.example.cast.model

import androidx.compose.runtime.Immutable

/**
 * Alle unterstützten Cast-Technologien für universelles Streaming im Heimnetzwerk.
 */
enum class CastProtocol(
    val displayName: String,
    val shortBadge: String,
    val defaultPort: Int
) {
    GOOGLE_CAST("Google Cast", "Chromecast", 8009),
    DLNA("DLNA / UPnP", "DLNA", 1900),
    ROKU("Roku TV / Stick", "Roku", 8060),
    AIRPLAY("Apple AirPlay", "AirPlay", 7000),
    KODI("Kodi Media Center", "Kodi", 8080),
    WEB_CAST("Web-Cast (Browser / Smart TV)", "Web-Cast", 8099),
    MIRACAST("Miracast / Smart View", "Miracast", 0)
}

/**
 * Repräsentiert ein gefundenes oder manuell konfiguriertes Zielgerät im Netzwerk.
 */
@Immutable
data class CastDevice(
    val id: String,
    val name: String,
    val protocol: CastProtocol,
    val host: String = "",
    val port: Int = protocol.defaultPort,
    val modelName: String = "",
    val controlUrl: String = "",
    val renderingControlUrl: String = "",
    val routeId: String = "",
    val username: String = "",
    val password: String = "",
    val isManual: Boolean = false
)

/**
 * Medien-Metadaten für die Übergabe an ein Cast-Zielgerät.
 */
@Immutable
data class CastMediaItem(
    val streamUrl: String,
    val title: String,
    val subtitle: String = "",
    val logoUrl: String = "",
    val isLive: Boolean = true,
    val isAudioOnly: Boolean = false,
    val positionMs: Long = 0L,
    val headers: Map<String, String> = emptyMap()
) {
    fun inferredMimeType(): String {
        val lower = streamUrl.lowercase()
        return when {
            isAudioOnly && lower.contains(".aac") -> "audio/aac"
            isAudioOnly && lower.contains(".ogg") -> "audio/ogg"
            isAudioOnly && lower.contains(".flac") -> "audio/flac"
            isAudioOnly -> "audio/mpeg"
            lower.contains(".m3u8") -> "application/x-mpegURL"
            lower.contains(".mpd") -> "application/dash+xml"
            lower.contains(".mp4") -> "video/mp4"
            lower.contains(".mkv") -> "video/x-matroska"
            lower.contains(".webm") -> "video/webm"
            lower.contains(".ts") -> "video/mp2t"
            isLive -> "application/x-mpegURL"
            else -> "video/mp4"
        }
    }
}

enum class CastConnectionStatus {
    IDLE,
    CONNECTING,
    CONNECTED,
    ERROR
}

/**
 * Zustand der zentralen Universal-Cast-Engine für die Compose-UI.
 */
@Immutable
data class CastUiState(
    val isScanning: Boolean = false,
    val isWifiConnected: Boolean = true,
    val localIpAddress: String? = null,
    val discoveredDevices: List<CastDevice> = emptyList(),
    val manualDevices: List<CastDevice> = emptyList(),
    val activeDevice: CastDevice? = null,
    val activeMedia: CastMediaItem? = null,
    val connectionStatus: CastConnectionStatus = CastConnectionStatus.IDLE,
    val isRemotePlaying: Boolean = false,
    val remoteVolume: Float = 0.7f,
    val isRemoteMuted: Boolean = false,
    val useRelayProxy: Boolean = true,
    val webCastUrl: String? = null,
    val isWebCastRunning: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
) {
    val isConnected: Boolean
        get() = connectionStatus == CastConnectionStatus.CONNECTED && activeDevice != null

    val allDevices: List<CastDevice>
        get() {
            val combined = LinkedHashMap<String, CastDevice>()
            discoveredDevices.forEach { combined[it.id] = it }
            manualDevices.forEach { combined[it.id] = it }
            return combined.values.toList()
        }
}
