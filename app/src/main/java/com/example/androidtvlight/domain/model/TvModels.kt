package com.example.androidtvlight.domain.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

enum class TvSection(val id: String, val title: String) {
    HOME("home", "Startseite"),
    DEUTSCHES_TV("deutsches_tv", "Deutsches TV"),
    OESTERREICH_TV("oesterreich_tv", "Österreich TV"),
    TV2("tv2", "DE TV2"),
    TV3("tv3", "TV3"),
    EPG("epg", "EPG Programm"),
    PLAYER("player", "ExoPlayer Live"),
    RECORDINGS("recordings", "Aufnahmen"),
    AMBIENT("ambient", "Ambient Light"),
    REMOTE("remote", "TV-Steuerung")
}

enum class EpgTimeFilter(val id: String, val label: String) {
    NOW("now", "● Jetzt Live"),
    NEXT("next", "⏭ Gleich danach"),
    PRIMETIME("primetime", "★ 20:15 Primetime"),
    TIMELINE("timeline", "📅 Zeitleiste (24h)")
}

enum class EpgCategory(val id: String, val label: String, val badgeColorHex: Long) {
    ALL("all", "Alle Genres", 0xFF38BDF8),
    NEWS("news", "Nachrichten & Info", 0xFF0EA5E9),
    MOVIES_SERIES("movies", "Film & Serie", 0xFFF59E0B),
    SPORTS("sports", "Sport Live", 0xFF10B981),
    ENTERTAINMENT("entertainment", "Show & Unterhaltung", 0xFFA855F7),
    DOCS_KULTUR("docs", "Doku & Kultur", 0xFF14B8A6),
    KIDS("kids", "Kinder & Familie", 0xFFF43F5E)
}

@Immutable
data class EpgProgramItem(
    val id: String,
    val channelId: String,
    val channelNumber: Int,
    val channelName: String,
    val channelLogoUrl: String,
    val rubrik: TvRubrik,
    val title: String,
    val subtitle: String,
    val description: String,
    val category: EpgCategory,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val formattedStartTime: String,
    val formattedEndTime: String,
    val formattedTimeRange: String,
    val durationMinutes: Int,
    val progressFraction: Float,
    val statusBadgeText: String,
    val isLiveNow: Boolean,
    val isNextUp: Boolean,
    val isPrimeTime: Boolean,
    val qualityBadge: String = "HD EPG"
)

@Immutable
data class ChannelEpgSchedule(
    val channel: TvChannelItem,
    val currentProgram: EpgProgramItem?,
    val nextProgram: EpgProgramItem?,
    val primeTimeProgram: EpgProgramItem?,
    val dayPrograms: List<EpgProgramItem>
)

@Immutable
data class RecordedVideoItem(
    val id: String,
    val fileName: String,
    val channelTitle: String,
    val playbackUri: String,
    val displayPath: String,
    val sizeBytes: Long,
    val createdAtMillis: Long,
    val formattedDate: String,
    val formattedSize: String
)

enum class TvRubrik(val id: String, val title: String, val sourceUrl: String, val flagBadge: String) {
    ALL("all", "Alle Sender", "https://www.2ix2.com/", "DE • AT • DE TV2 • TV3"),
    DEUTSCHES_TV(
        "de",
        "1: Deutsches TV",
        "https://www.2ix2.com/category/deutsche-tv-sender/",
        "DEUTSCHLAND"
    ),
    OESTERREICH_TV(
        "at",
        "2: Österreich TV",
        "https://www.2ix2.com/category/oesterreichische-tv/",
        "ÖSTERREICH"
    ),
    TV2_OHA(
        "tv2",
        "DE TV2",
        "https://oha.to/mediaurl.json",
        "DE TV2"
    ),
    TV3_LIVE(
        "tv3",
        "TV3",
        "https://tele24seven.com/?country=DE#country-channels",
        "TV3"
    )
}

enum class ExoResizeModeOption(val label: String, val modeValue: Int) {
    FIT("Original (Fit)", 0),
    FILL("Vollbild (Stretch)", 3),
    ZOOM("Kino-Zoom (Crop)", 4)
}

@Immutable
data class TvChannelItem(
    val id: String,
    val number: Int,
    val name: String,
    val rubrik: TvRubrik,
    val logoUrl: String,
    val pageUrl: String,
    val primaryStreamUrl: String,
    val fallbackStreamUrls: List<String> = emptyList(),
    val currentShow: String,
    val qualityBadge: String = "LIVE HD",
    val accentHex: Long = 0xFF38BDF8,
    val isFavorite: Boolean = false
)

@Immutable
data class ExoTrackInfo(
    val id: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val isSelected: Boolean
)

@Immutable
data class ExoPlayerFeatureState(
    val isPlaying: Boolean = true,
    val isBuffering: Boolean = false,
    val isMuted: Boolean = false,
    val volume: Float = 1.0f,
    val playbackSpeed: Float = 1.0f,
    val resizeMode: ExoResizeModeOption = ExoResizeModeOption.FIT,
    val isFullscreenPlayer: Boolean = false,
    val isBackgroundPlaybackEnabled: Boolean = true,
    val isPreventStandbyEnabled: Boolean = true,
    val isRecordingMp4: Boolean = false,
    val recordingElapsedSeconds: Long = 0L,
    val recordingBytesWritten: Long = 0L,
    val recordingTargetPath: String = "Download/MaDIPTV",
    val recordingSavedBanner: String? = null,
    val activeRecording: RecordedVideoItem? = null,
    val showRecordingsDialog: Boolean = false,
    val isSubtitlesEnabled: Boolean = false,
    val videoResolutionLabel: String = "Auto HLS Adaptive",
    val videoBitrateLabel: String = "Live-Stream",
    val currentStreamUrl: String = "",
    val streamSourceIndex: Int = 0,
    val errorMessage: String? = null,
    val availableVideoTracks: List<ExoTrackInfo> = emptyList(),
    val availableAudioTracks: List<ExoTrackInfo> = emptyList(),
    val availableSubtitleTracks: List<ExoTrackInfo> = emptyList(),
    val showTrackDialog: Boolean = false,
    val showChannelZapperOverlay: Boolean = false,
    val isResolvingPageStream: Boolean = false
)

@Immutable
data class AmbientLightScene(
    val id: String,
    val name: String,
    val subtitle: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val tertiaryColor: Color,
    val kelvinLabel: String,
    val recommendedBrightness: Float,
    val pulseSpeed: Float
)

@Immutable
data class QuickLaunchTile(
    val id: String,
    val title: String,
    val subtitle: String,
    val categoryLabel: String,
    val packageOrAction: String,
    val deepLinkUri: String,
    val accentHex: Long,
    val isSystemApp: Boolean = false
)

@Immutable
data class TvLightSettings(
    val installationPinVerified: Boolean = false,
    val highContrastMode: Boolean = false,
    val largeTextMode: Boolean = false,
    val showVirtualRemoteOverlay: Boolean = false,
    val backgroundPlaybackEnabled: Boolean = true,
    val preventStandbyEnabled: Boolean = true,
    val epgAutoUpdateEnabled: Boolean = true,
    val epgAutoUpdateIntervalMinutes: Int = 15,
    val activeAmbientSceneId: String = "deep_ocean_bias",
    val ambientBrightness: Float = 0.82f,
    val ambientDynamicPulse: Boolean = true,
    val warmEyeCareFilter: Float = 0.15f,
    val sleepTimerMinutes: Int = 0,
    val favoriteChannelIds: Set<String> = setOf("de_ard", "de_zdf", "de_rtl", "at_orf1", "at_servus_tv"),
    val lastSelectedChannelId: String = "de_ard"
)
