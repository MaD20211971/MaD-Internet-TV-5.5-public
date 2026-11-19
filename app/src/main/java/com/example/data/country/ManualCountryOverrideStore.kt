package com.example.data.country

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistent store for user-defined manual country assignments.
 * Learned station mappings ensure that subsequent imports and refreshes remember the user's choices.
 */
class ManualCountryOverrideStore(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "country_manual_overrides_v1"
        private const val KEY_PREFIX_ITEM = "item_"
        private const val KEY_PREFIX_STATION = "station_"

        @Volatile
        private var INSTANCE: ManualCountryOverrideStore? = null

        fun getInstance(context: Context): ManualCountryOverrideStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ManualCountryOverrideStore(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * Checks if a manual override exists for a specific item ID or its normalized station ID.
     */
    fun getOverride(itemId: String, normalizedChannelId: String): String? {
        val direct = prefs.getString(KEY_PREFIX_ITEM + itemId, null)
        if (!direct.isNullOrBlank()) return direct

        if (normalizedChannelId.isNotBlank() && normalizedChannelId != "UNKNOWN_CHANNEL") {
            val learned = prefs.getString(KEY_PREFIX_STATION + normalizedChannelId, null)
            if (!learned.isNullOrBlank()) return learned
        }
        return null
    }

    /**
     * Stores a manual country override for an item and learns the mapping for its station ID.
     */
    fun setOverride(itemId: String, normalizedChannelId: String, countryCode: String) {
        val editor = prefs.edit()
        val upper = countryCode.trim().uppercase()
        if (itemId.isNotBlank()) {
            editor.putString(KEY_PREFIX_ITEM + itemId, upper)
        }
        if (normalizedChannelId.isNotBlank() && normalizedChannelId != "UNKNOWN_CHANNEL") {
            editor.putString(KEY_PREFIX_STATION + normalizedChannelId, upper)
        }
        editor.apply()
    }

    /**
     * Removes an override for a given item and/or station ID.
     */
    fun removeOverride(itemId: String, normalizedChannelId: String) {
        val editor = prefs.edit()
        if (itemId.isNotBlank()) {
            editor.remove(KEY_PREFIX_ITEM + itemId)
        }
        if (normalizedChannelId.isNotBlank()) {
            editor.remove(KEY_PREFIX_STATION + normalizedChannelId)
        }
        editor.apply()
    }

    /**
     * Returns all learned station overrides.
     */
    fun getAllStationOverrides(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for ((key, value) in prefs.all) {
            if (key.startsWith(KEY_PREFIX_STATION) && value is String) {
                result[key.removePrefix(KEY_PREFIX_STATION)] = value
            }
        }
        return result
    }

    /**
     * Clears all stored manual overrides.
     */
    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
