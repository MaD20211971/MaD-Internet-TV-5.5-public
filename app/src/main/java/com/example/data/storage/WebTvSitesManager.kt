package com.example.data.storage

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentRose
import com.example.ui.theme.BorderGlow
import com.example.ui.theme.DarkNavyCard
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.GoldDark
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

@Immutable
data class WebTvSiteItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val category: String = "Live TV & Stream",
    val description: String = "",
    val badge: String = "Web TV",
    val iconEmoji: String = "🌐"
) {
    val badgeColor: Color
        get() = when {
            iconEmoji == "📺" || badge.contains("Live", ignoreCase = true) -> AccentEmerald
            iconEmoji == "📡" || badge.contains("Sport", ignoreCase = true) -> AccentCyan
            iconEmoji == "🎬" || badge.contains("Film", ignoreCase = true) -> GoldPrimary
            iconEmoji == "🍿" || badge.contains("Kino", ignoreCase = true) -> AccentRose
            else -> GoldDark
        }
}

object WebTvSitesManager {

    private const val PREFS_NAME = "web_tv_custom_sites_prefs"
    private const val KEY_SITES_JSON = "web_tv_sites_json_v1"

    private val _sitesFlow = MutableStateFlow<List<WebTvSiteItem>>(emptyList())
    val sitesFlow: StateFlow<List<WebTvSiteItem>> = _sitesFlow.asStateFlow()

    @Volatile
    private var isInitialized = false

    fun ensureInitialized(context: Context) {
        if (isInitialized && _sitesFlow.value.isNotEmpty()) return
        synchronized(this) {
            if (isInitialized && _sitesFlow.value.isNotEmpty()) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val rawJson = prefs.getString(KEY_SITES_JSON, null)
            val loaded = if (rawJson != null) {
                var parsed = parseSitesJson(rawJson)
                var changed = false
                val defaultTele24 = defaultSites().first { it.id == "default_tele24seven_de" }
                if (parsed.none { it.url.contains("tele24seven.com", ignoreCase = true) }) {
                    parsed = listOf(defaultTele24) + parsed
                    changed = true
                }
                val defaultKoolTo = defaultSites().first { it.id == "default_kool_to" }
                if (parsed.none { it.url.contains("kool.to", ignoreCase = true) }) {
                    parsed = listOf(defaultKoolTo) + parsed
                    changed = true
                }
                // Migrate any saved kool.to / kool.ws entries from https:// to http:// to prevent mixed-content blocking
                parsed = parsed.map { site ->
                    if (requiresCleartextHttp(site.url) && site.url.startsWith("https://", ignoreCase = true)) {
                        changed = true
                        site.copy(url = "http://" + site.url.substring("https://".length))
                    } else {
                        site
                    }
                }
                if (changed) {
                    saveSitesToPrefs(context.applicationContext, parsed)
                }
                parsed
            } else {
                val defaults = defaultSites()
                saveSitesToPrefs(context.applicationContext, defaults)
                defaults
            }
            _sitesFlow.value = loaded
            isInitialized = true
        }
    }

    fun requiresCleartextHttp(rawUrl: String): Boolean {
        val lower = rawUrl.trim().lowercase()
        return lower.contains("kool.to") ||
            lower.contains("kool.ws") ||
            lower.contains("nydus.org") ||
            lower.contains("megakino")
    }

    fun normalizeWebUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return ""
        if (requiresCleartextHttp(trimmed)) {
            return when {
                trimmed.startsWith("https://", ignoreCase = true) -> "http://" + trimmed.substring("https://".length)
                trimmed.startsWith("http://", ignoreCase = true) -> trimmed
                else -> "http://$trimmed"
            }
        }
        return if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    fun addSite(
        context: Context,
        name: String,
        url: String,
        category: String = "Eigene Webseite",
        description: String = "",
        badge: String = "Web TV",
        iconEmoji: String = "🌐"
    ): WebTvSiteItem? {
        ensureInitialized(context)
        val cleanUrl = normalizeWebUrl(url)
        if (cleanUrl.isBlank()) return null
        val cleanName = name.trim().ifBlank {
            runCatching { java.net.URI(cleanUrl).host?.removePrefix("www.") }.getOrNull()
                ?.takeIf { it.isNotBlank() } ?: cleanUrl
        }
        val cleanDesc = description.trim().ifBlank { cleanUrl }

        val newItem = WebTvSiteItem(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            url = cleanUrl,
            category = category.trim().ifBlank { "Web TV" },
            description = cleanDesc,
            badge = badge.trim().ifBlank { "Web TV" },
            iconEmoji = iconEmoji.trim().ifBlank { "🌐" }
        )

        val updated = _sitesFlow.value + newItem
        _sitesFlow.value = updated
        saveSitesToPrefs(context.applicationContext, updated)
        return newItem
    }

    fun updateSite(context: Context, updatedSite: WebTvSiteItem) {
        ensureInitialized(context)
        val cleanUrl = normalizeWebUrl(updatedSite.url)
        if (cleanUrl.isBlank()) return
        val cleanName = updatedSite.name.trim().ifBlank { cleanUrl }
        val finalSite = updatedSite.copy(
            name = cleanName,
            url = cleanUrl,
            category = updatedSite.category.trim().ifBlank { "Web TV" },
            description = updatedSite.description.trim().ifBlank { cleanUrl },
            badge = updatedSite.badge.trim().ifBlank { "Web TV" },
            iconEmoji = updatedSite.iconEmoji.trim().ifBlank { "🌐" }
        )
        val updatedList = _sitesFlow.value.map {
            if (it.id == finalSite.id) finalSite else it
        }
        _sitesFlow.value = updatedList
        saveSitesToPrefs(context.applicationContext, updatedList)
    }

    fun deleteSite(context: Context, siteId: String) {
        ensureInitialized(context)
        val updatedList = _sitesFlow.value.filterNot { it.id == siteId }
        _sitesFlow.value = updatedList
        saveSitesToPrefs(context.applicationContext, updatedList)
    }

    fun resetToDefaults(context: Context) {
        val defaults = defaultSites()
        _sitesFlow.value = defaults
        saveSitesToPrefs(context.applicationContext, defaults)
        isInitialized = true
    }

    private fun saveSitesToPrefs(context: Context, sites: List<WebTvSiteItem>) {
        runCatching {
            val arr = JSONArray()
            for (site in sites) {
                val obj = JSONObject().apply {
                    put("id", site.id)
                    put("name", site.name)
                    put("url", site.url)
                    put("category", site.category)
                    put("description", site.description)
                    put("badge", site.badge)
                    put("iconEmoji", site.iconEmoji)
                }
                arr.put(obj)
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SITES_JSON, arr.toString())
                .apply()
        }
    }

    private fun parseSitesJson(json: String): List<WebTvSiteItem> {
        return runCatching {
            val arr = JSONArray(json)
            val list = ArrayList<WebTvSiteItem>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val name = obj.optString("name", "").trim()
                val url = obj.optString("url", "").trim()
                if (name.isNotBlank() && url.isNotBlank()) {
                    list.add(
                        WebTvSiteItem(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            name = name,
                            url = url,
                            category = obj.optString("category", "Web TV"),
                            description = obj.optString("description", url),
                            badge = obj.optString("badge", "Web TV"),
                            iconEmoji = obj.optString("iconEmoji", "🌐")
                        )
                    )
                }
            }
            list
        }.getOrElse { defaultSites() }
    }

    fun defaultSites(): List<WebTvSiteItem> = listOf(
        WebTvSiteItem(
            id = "default_tele24seven_de",
            name = "Tele24Seven (DE)",
            url = "https://tele24seven.com/?country=DE#country-channels",
            category = "Deutsche TV Sender",
            description = "24/7 Deutsche Live-TV Sender & Länder-Kanäle (tele24seven.com)",
            badge = "DE Live TV",
            iconEmoji = "📺"
        ),
        WebTvSiteItem(
            id = "default_2ix2",
            name = "2ix2.com",
            url = "https://www.2ix2.com/",
            category = "Deutsche TV Sender",
            description = "Kostenloses deutsches Live TV (ARD, ZDF, RTL, Pro7, etc.)",
            badge = "German Live TV",
            iconEmoji = "📺"
        ),
        WebTvSiteItem(
            id = "default_nydus",
            name = "Nydus Live Stream",
            url = "http://nydus.org/stream/live",
            category = "Live TV & Sport",
            description = "Live TV & Sport Streams, Events und Live-Übertragungen",
            badge = "Live Streams",
            iconEmoji = "📡"
        ),
        WebTvSiteItem(
            id = "default_movie2k",
            name = "Movie2k",
            url = "https://movie2k.cx/",
            category = "Filme & Serien",
            description = "Aktuelle Filme, Serien und Kinofilme kostenlos im Stream",
            badge = "Filme & Serien",
            iconEmoji = "🎬"
        ),
        WebTvSiteItem(
            id = "default_kool_to",
            name = "Kool.to (Live TV & Kino)",
            url = "http://kool.to/",
            category = "Live TV & Kino",
            description = "Öffentliches Kool.to Portal: Deutsche & internationale Live-TV-Sender (/channels), Sport & VoD-Streams",
            badge = "Live + Kino",
            iconEmoji = "🦅"
        ),
        WebTvSiteItem(
            id = "default_kool",
            name = "Kool.ws",
            url = "http://kool.ws/",
            category = "Kino & Video On Demand",
            description = "Kostenlose Kinofilme, beliebte Serien und Video-Streams (Mirror)",
            badge = "Kino Streams",
            iconEmoji = "🍿"
        ),
        WebTvSiteItem(
            id = "default_megakino",
            name = "Megakino",
            url = "http://megakino18.com/",
            category = "Filme & Serien",
            description = "Große Auswahl an Spielfilmen, Blockbustern und Serien",
            badge = "Blockbuster",
            iconEmoji = "🎥"
        )
    )
}

@Composable
fun WebTvSiteEditDialog(
    initialSite: WebTvSiteItem? = null,
    isEnglish: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (name: String, url: String, category: String, description: String, iconEmoji: String) -> Unit
) {
    var name by remember(initialSite) { mutableStateOf(initialSite?.name ?: "") }
    var url by remember(initialSite) { mutableStateOf(initialSite?.url ?: "https://") }
    var category by remember(initialSite) { mutableStateOf(initialSite?.category ?: "Live TV & Stream") }
    var description by remember(initialSite) { mutableStateOf(initialSite?.description ?: "") }
    var selectedEmoji by remember(initialSite) { mutableStateOf(initialSite?.iconEmoji ?: "🌐") }
    var showError by remember { mutableStateOf(false) }

    val emojiOptions = listOf("🌐", "📺", "📡", "🎬", "🍿", "🎥", "⚽", "🎵")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkNavyCard,
        title = {
            Text(
                text = if (initialSite == null) {
                    if (isEnglish) "Add Web TV Website" else "Web TV Webseite hinzufügen"
                } else {
                    if (isEnglish) "Edit Web TV Website" else "Web TV Webseite bearbeiten"
                },
                color = GoldPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        showError = false
                    },
                    label = { Text(if (isEnglish) "Website Name" else "Name der Webseite (z.B. 2ix2, Sport TV)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("web_tv_site_name_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = BorderGlow,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface
                    )
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        showError = false
                    },
                    label = { Text(if (isEnglish) "Website URL (https://...)" else "Webadresse / URL (https://...)") },
                    placeholder = { Text("https://www.beispiel.de", color = TextMuted) },
                    singleLine = true,
                    isError = showError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("web_tv_site_url_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = if (showError) AccentRose else BorderGlow,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface
                    )
                )

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text(if (isEnglish) "Category / Badge" else "Kategorie (z.B. Live TV, Filme, Sport)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = BorderGlow,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface
                    )
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(if (isEnglish) "Description (optional)" else "Kurzbeschreibung (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = BorderGlow,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface
                    )
                )

                Text(
                    text = if (isEnglish) "Select Icon:" else "Symbol wählen:",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    emojiOptions.forEach { emoji ->
                        val isSelected = selectedEmoji == emoji
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) GoldPrimary else DarkSurfaceElevated)
                                .border(
                                    1.dp,
                                    if (isSelected) Color.Black else BorderGlow,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedEmoji = emoji },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = emoji, fontSize = 17.sp)
                        }
                    }
                }

                if (showError) {
                    Text(
                        text = if (isEnglish) "Please enter a valid website URL." else "Bitte eine gültige Webadresse (URL) eingeben.",
                        color = AccentRose,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanedUrl = WebTvSitesManager.normalizeWebUrl(url)
                    if (cleanedUrl.isBlank() || cleanedUrl == "https://" || cleanedUrl == "http://") {
                        showError = true
                    } else {
                        onSave(name, cleanedUrl, category, description, selectedEmoji)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = GoldPrimary,
                    contentColor = Color.Black
                ),
                modifier = Modifier.testTag("web_tv_site_save_button")
            ) {
                Text(
                    text = if (isEnglish) "Save" else "Speichern",
                    fontWeight = FontWeight.ExtraBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = if (isEnglish) "Cancel" else "Abbrechen",
                    color = TextSecondary
                )
            }
        }
    )
}
