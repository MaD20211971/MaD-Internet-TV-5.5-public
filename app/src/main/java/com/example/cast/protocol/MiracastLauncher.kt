package com.example.cast.protocol

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Öffnet den offiziellen Android-Miracast- / Wi-Fi-Display-Systemdialog (inkl. Samsung Smart View Fallback).
 */
object MiracastLauncher {

    fun openSystemMiracastSettings(context: Context): Boolean {
        val intents = listOf(
            // 1. Standard Android Wireless Display / Cast Settings
            Intent(Settings.ACTION_CAST_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            // 2. Samsung Smart View / Wi-Fi Display Fallback
            Intent("android.settings.WIFI_DISPLAY_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent().apply {
                component = ComponentName(
                    "com.samsung.android.smartmirroring",
                    "com.samsung.android.smartmirroring.CastingDialog"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            // 3. Generic Connected Devices Settings Fallback
            Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )

        for (intent in intents) {
            try {
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
                // Nächsten Intent testen
            }
        }
        return false
    }
}
