package com.example.ui.components

import androidx.compose.runtime.Composable
import com.example.androidtvlight.AndroidTvLightHost
import com.example.data.model.ChannelItem

/**
 * Rubrik "Live TV Light":
 * Vollständige 1:1-Integration aus dem GitHub-Projekt "Android-TV-Light"
 * (inkl. Original-ExoPlayer, Senderkatalog, EPG, MP4-Recorder, Ambient Light & Fernbedienung).
 */
@Composable
fun LiveTvLightView(
    onPlayInAppPlayer: (ChannelItem) -> Unit = {},
    isEnglish: Boolean = false,
    onFullscreenChanged: (Boolean) -> Unit = {},
    onExitToMainApp: () -> Unit = {}
) {
    AndroidTvLightHost(
        onFullscreenChanged = onFullscreenChanged,
        onExitToMainApp = onExitToMainApp
    )
}
