package com.example.ui.components

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

object TvUiUtils {
    /**
     * Prüft, ob das aktuelle Gerät ein Android TV / Google TV / Leanback-Gerät ist.
     */
    fun isTvDevice(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            val isLeanback = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
            val isTelevision = pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
            val uiMode = (context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK)
            val isTvUi = uiMode == Configuration.UI_MODE_TYPE_TELEVISION
            isLeanback || isTelevision || isTvUi
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * Top-level Shortcut zur bequemen Nutzung in Composables
 */
fun isTvDevice(context: Context): Boolean = TvUiUtils.isTvDevice(context)
