package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import com.example.data.storage.DownloadStorageHelper
import com.example.data.storage.WebTvSiteEditDialog
import com.example.data.storage.WebTvSiteItem
import com.example.data.storage.WebTvSitesManager
import com.example.data.model.AppSettings
import com.example.data.model.CachingMode
import com.example.data.model.PlayerEngine
import com.example.data.model.PlaylistEntity
import com.example.data.model.RecordingItem
import com.example.data.model.RecordingStatus
import com.example.data.parser.EpgSyncState
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentRose
import com.example.ui.theme.BorderGlow
import com.example.ui.theme.DarkNavyCard
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.GoldDark
import com.example.ui.theme.GoldLight
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsView(
    settings: AppSettings,
    playlists: List<PlaylistEntity>,
    recordings: List<RecordingItem>,
    onUpdateCachingMode: (CachingMode) -> Unit,
    onUpdatePreferredPlayer: (PlayerEngine) -> Unit,
    onToggleBackgroundPlayback: (Boolean) -> Unit,
    onUpdateLanguage: (String) -> Unit,
    onOpenAddPlaylist: () -> Unit,
    onOpenAddMacPortal: () -> Unit = {},
    onOpenImportAssistant: () -> Unit = {},
    onUpdatePlaylist: (PlaylistEntity) -> Unit = {},
    onDeletePlaylist: (PlaylistEntity) -> Unit,
    onRefreshPlaylist: (PlaylistEntity) -> Unit,
    onOpenBackupDialog: () -> Unit,
    onOpenRestoreDialog: () -> Unit,
    onDeleteRecording: (RecordingItem) -> Unit,
    onPlayRecording: (RecordingItem) -> Unit = {},
    onCancelDownload: (RecordingItem) -> Unit = {},
    onUpdateEpgTimezone: (String) -> Unit = {},
    onUpdateEpgTimeOffsetHours: (Int) -> Unit = {},
    onUpdateProviderEpgOffset: (String, Int) -> Unit = { _, _ -> },
    onForceEpgRemap: () -> Unit = {},
    onUpdateCustomEpgUrl: (String) -> Unit = {},
    onClearAllEpg: () -> Unit = {},
    onSyncBothEpgFeeds: () -> Unit = {},
    syncState: EpgSyncState = EpgSyncState.Idle,
    onTriggerEpgSync: (String?) -> Unit = {},
    onUpdateAutoUpdateOnStart: (Boolean) -> Unit = {},
    onUpdateNexusFullScreen: (Boolean) -> Unit = {},
    onUpdateAspectRatio: (String) -> Unit = {},
    onLockApp: () -> Unit = {},
    onDeleteAllPlaylists: () -> Unit = {},
    onUpdateSyncOnlyGermanWhenGerman: (Boolean) -> Unit = {},
    onOptimizeDatabase: () -> Unit = {},
    isEnglish: Boolean = false,
    activePlaylistId: String? = null,
    onSelectPlaylist: (String) -> Unit = {}
) {
    var showDeleteAllConfirmation by remember { mutableStateOf(false) }
    var playlistToUnlock by remember { mutableStateOf<PlaylistEntity?>(null) }
    var playlistToEdit by remember { mutableStateOf<PlaylistEntity?>(null) }
    var playlistForActionsDialog by remember { mutableStateOf<PlaylistEntity?>(null) }
    var showPinPrompt by remember { mutableStateOf(false) }
    var isPlaylistOverviewUnlocked by remember { mutableStateOf(false) }
    var showUnlockOverviewPinPrompt by remember { mutableStateOf(false) }
    var customEpgInput by remember(settings.customEpgUrl) { mutableStateOf(settings.customEpgUrl) }
    var isTimezoneDropdownOpen by remember { mutableStateOf(false) }
    var playlistSettingsSearch by remember { mutableStateOf("") }
    var showAllConfiguredPlaylists by remember { mutableStateOf(false) }
    var providerOffsetSearch by remember { mutableStateOf("") }
    var showAllProviderOffsets by remember { mutableStateOf(false) }

    val webTvContext = LocalContext.current
    LaunchedEffect(webTvContext) {
        WebTvSitesManager.ensureInitialized(webTvContext)
    }
    val webTvSites by WebTvSitesManager.sitesFlow.collectAsStateWithLifecycle()
    var showAddWebTvSiteDialog by remember { mutableStateOf(false) }
    var webTvSiteToEdit by remember { mutableStateOf<WebTvSiteItem?>(null) }
    var quickWebTvName by remember { mutableStateOf("") }
    var quickWebTvUrl by remember { mutableStateOf("") }
    var isWebTvSettingsUnlocked by remember { mutableStateOf(false) }
    var showWebTvSettingsPinPrompt by remember { mutableStateOf(false) }
    var showAllWebTvSites by remember { mutableStateOf(false) }

    if (showWebTvSettingsPinPrompt) {
        PinPromptDialog(
            title = if (isEnglish) "Unlock Web TV Settings" else "Web TV Verwaltung freischalten",
            subtitle = if (isEnglish) {
                "Enter security PIN to view and manage Web TV addresses"
            } else {
                "Sicherheits-PIN eingeben, um hinterlegte Web-TV-Adressen einzusehen und zu verwalten."
            },
            expectedPin = "1971",
            onDismiss = { showWebTvSettingsPinPrompt = false },
            onPinSuccess = {
                showWebTvSettingsPinPrompt = false
                isWebTvSettingsUnlocked = true
            },
            isEnglish = isEnglish
        )
    }

    if ((showAddWebTvSiteDialog || webTvSiteToEdit != null) && isWebTvSettingsUnlocked) {
        WebTvSiteEditDialog(
            initialSite = webTvSiteToEdit,
            isEnglish = isEnglish,
            onDismiss = {
                showAddWebTvSiteDialog = false
                webTvSiteToEdit = null
            },
            onSave = { name, url, category, description, iconEmoji ->
                val editing = webTvSiteToEdit
                if (editing != null) {
                    WebTvSitesManager.updateSite(
                        context = webTvContext,
                        updatedSite = editing.copy(
                            name = name,
                            url = url,
                            category = category,
                            description = description,
                            badge = category,
                            iconEmoji = iconEmoji
                        )
                    )
                } else {
                    WebTvSitesManager.addSite(
                        context = webTvContext,
                        name = name,
                        url = url,
                        category = category,
                        description = description,
                        badge = category,
                        iconEmoji = iconEmoji
                    )
                }
                showAddWebTvSiteDialog = false
                webTvSiteToEdit = null
            }
        )
    }

    if (playlistForActionsDialog != null) {
        PlaylistActionsDialog(
            playlist = playlistForActionsDialog!!,
            isActive = playlistForActionsDialog?.id == activePlaylistId,
            onDismiss = { playlistForActionsDialog = null },
            onDeletePlaylist = { pl ->
                playlistForActionsDialog = null
                onDeletePlaylist(pl)
            },
            onEditPlaylist = { pl ->
                playlistForActionsDialog = null
                if (isPlaylistOverviewUnlocked) {
                    playlistToEdit = pl
                } else {
                    playlistToUnlock = pl
                    showPinPrompt = true
                }
            },
            onRefreshPlaylist = { pl ->
                playlistForActionsDialog = null
                onRefreshPlaylist(pl)
            },
            onSelectPlaylist = { id ->
                playlistForActionsDialog = null
                onSelectPlaylist(id)
            },
            isEnglish = isEnglish
        )
    }

    if (showUnlockOverviewPinPrompt) {
        PinPromptDialog(
            title = if (isEnglish) "Unlock Playlist Data" else "Playlisten-Daten freischalten",
            subtitle = if (isEnglish) "Enter password/PIN to view complete playlist URLs, MAC addresses, and credentials" else "Passwort/PIN eingeben, um komplette Playlisten, Portaldaten und MAC-Adressen anzuzeigen.",
            expectedPin = "1971",
            onDismiss = { showUnlockOverviewPinPrompt = false },
            onPinSuccess = {
                showUnlockOverviewPinPrompt = false
                isPlaylistOverviewUnlocked = true
            },
            isEnglish = isEnglish
        )
    }

    if (showPinPrompt && playlistToUnlock != null) {
        PinPromptDialog(
            title = if (isEnglish) "PIN Verification" else "PIN-Abfrage: ${playlistToUnlock?.getMaskedSettingsOverviewTitle()}",
            subtitle = if (isEnglish) "Enter PIN to view and edit this playlist" else "Gib das Passwort/PIN ein, um die kompletten Daten dieser Playliste zu sehen und zu bearbeiten.",
            expectedPin = "1971",
            onDismiss = {
                showPinPrompt = false
                playlistToUnlock = null
            },
            onPinSuccess = {
                showPinPrompt = false
                isPlaylistOverviewUnlocked = true
                playlistToEdit = playlistToUnlock
                playlistToUnlock = null
            },
            isEnglish = isEnglish
        )
    }

    if (playlistToEdit != null) {
        EditPlaylistDialog(
            playlist = playlistToEdit!!,
            onDismiss = { playlistToEdit = null },
            onSave = { updated ->
                onUpdatePlaylist(updated)
                playlistToEdit = null
            },
            isEnglish = isEnglish
        )
    }

    if (showDeleteAllConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirmation = false },
            containerColor = DarkNavyCard,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = null,
                            tint = AccentRose,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEnglish) "Delete All Playlists?" else "Alle Playlisten löschen?",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = AccentRose
                        )
                    }
                    IconButton(
                        onClick = { showDeleteAllConfirmation = false },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = if (isEnglish) "Close" else "Schließen",
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            text = {
                Text(
                    text = if (isEnglish)
                        "Are you sure you want to permanently delete ALL playlists and channels from the local database? This cannot be undone."
                    else
                        "Möchtest du wirklich ALLE Playlisten und Sender vollständig aus der Datenbank löschen? Die Playlistenliste wird komplett geleert.",
                    fontSize = 14.sp,
                    color = TextPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAllConfirmation = false
                        onDeleteAllPlaylists()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRose),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isEnglish) "Yes, Delete All" else "Ja, alle löschen",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteAllConfirmation = false },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isEnglish) "Cancel" else "Abbrechen",
                        color = TextSecondary
                    )
                }
            }
        )
    }

    val playlistNumberMap = remember(playlists) {
        playlists.withIndex().associate { (idx, pl) -> pl.id to (idx + 1) }
    }
    val filteredConfiguredPlaylists = remember(playlists, playlistSettingsSearch, isPlaylistOverviewUnlocked) {
        if (playlistSettingsSearch.isBlank()) playlists
        else playlists.filter {
            val searchableTitle = if (isPlaylistOverviewUnlocked) it.name else it.getMaskedSettingsOverviewTitle()
            searchableTitle.contains(playlistSettingsSearch, ignoreCase = true)
        }
    }
    val displayedConfiguredPlaylists = remember(
        filteredConfiguredPlaylists,
        showAllConfiguredPlaylists,
        playlistSettingsSearch,
        activePlaylistId
    ) {
        if (showAllConfiguredPlaylists || playlistSettingsSearch.isNotBlank() || filteredConfiguredPlaylists.size <= 4) {
            filteredConfiguredPlaylists
        } else {
            val topFour = filteredConfiguredPlaylists.take(4).toMutableList()
            val activeItem = filteredConfiguredPlaylists.firstOrNull { it.id == activePlaylistId }
            if (activeItem != null && topFour.none { it.id == activeItem.id }) {
                topFour[topFour.lastIndex] = activeItem
            }
            topFour
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 90.dp)
    ) {
        // Section: Compact App Header & Abo-Status in Settings
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, BorderGlow.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ObsidianBlack)
                                .border(1.dp, GoldPrimary, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.app_logo),
                                contentDescription = "MaD IPTV public",
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "MaD IPTV public",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GoldPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(AccentEmerald)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isEnglish) "Active (Lifetime)" else "Abo: Aktiv (Lifetime)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AccentEmerald
                                )
                            }
                            Text(
                                text = if (isEnglish) "Version V5.5 • Unlimited Playlists • Ultra HD • PVR" else "Version V5.5 • Unbegrenzte Playlisten • Ultra HD • PVR Cloud",
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(GoldPrimary.copy(alpha = 0.15f))
                            .border(1.dp, GoldPrimary.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "V.5.5 Aurora",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldPrimary
                        )
                    }
                }
            }
        }

        // Rubrik Web TV in Einstellungen (Streng PIN-geschützt: 1971 — keine Webadressen sichtbar ohne PIN!)
        item {
            SettingsSectionHeader(
                icon = Icons.Default.Language,
                title = if (isEnglish) "Rubrik Web TV • Manage Websites" else "Rubrik Web TV • Webseiten verwalten",
                subtitle = if (isWebTvSettingsUnlocked) {
                    if (isEnglish) {
                        "Manually add, edit and delete websites for Web TV (${webTvSites.size} websites)"
                    } else {
                        "Webseiten für Web TV manuell hinzufügen, bearbeiten und löschen (${webTvSites.size} hinterlegt)"
                    }
                } else {
                    if (isEnglish) {
                        "PIN-protected section • Web addresses are hidden until unlocked"
                    } else {
                        "PIN-geschützter Bereich • Webadressen sind ohne PIN-Freigabe ausgeblendet"
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkNavyCard)
                    .border(
                        1.dp,
                        if (isWebTvSettingsUnlocked) AccentEmerald.copy(alpha = 0.6f) else GoldPrimary.copy(alpha = 0.55f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(14.dp)
            ) {
                if (!isWebTvSettingsUnlocked) {
                    // Gesperrter Zustand: KEINE Web-TV-Adressen oder Portalnamen sichtbar!
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DarkSurfaceElevated)
                                    .border(1.dp, GoldPrimary.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "PIN-Schutz",
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (isEnglish) "🔒 Web TV Addresses Locked" else "🔒 Web-TV-Adressen sind PIN-geschützt",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isEnglish) {
                                        "Enter your security PIN to view, add or edit Web TV URLs."
                                    } else {
                                        "Hinterlegte Webadressen werden erst nach Eingabe der Sicherheits-PIN angezeigt."
                                    },
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        }

                        Button(
                            onClick = { showWebTvSettingsPinPrompt = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GoldPrimary,
                                contentColor = ObsidianBlack
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("settings_web_tv_unlock_button")
                        ) {
                            Text(
                                text = if (isEnglish) "🔓 Unlock (PIN)" else "🔓 PIN-Freigabe",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                } else {
                    // Entsperrter Zustand nach PIN 1971
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEnglish) "Add new Web TV website:" else "Neue Web TV Webseite direkt eintragen:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldLight
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AccentEmerald.copy(alpha = 0.18f))
                                .border(1.dp, AccentEmerald, RoundedCornerShape(6.dp))
                                .clickable { isWebTvSettingsUnlocked = false }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("settings_web_tv_lock_button")
                        ) {
                            Text(
                                text = if (isEnglish) "🔒 Lock Web TV" else "🔒 Adressen sperren",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentEmerald
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = quickWebTvName,
                            onValueChange = { quickWebTvName = it },
                            placeholder = {
                                Text(
                                    text = if (isEnglish) "Name (e.g. Sport TV)" else "Name (z.B. Live TV)",
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .weight(0.85f)
                                .height(48.dp)
                                .testTag("settings_web_tv_quick_name_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = BorderGlow,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedContainerColor = DarkSurface,
                                unfocusedContainerColor = DarkSurface
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )

                        OutlinedTextField(
                            value = quickWebTvUrl,
                            onValueChange = { quickWebTvUrl = it },
                            placeholder = {
                                Text(
                                    text = "https://www.beispiel.de",
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1.15f)
                                .height(48.dp)
                                .testTag("settings_web_tv_quick_url_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = BorderGlow,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedContainerColor = DarkSurface,
                                unfocusedContainerColor = DarkSurface
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )

                        Button(
                            onClick = {
                                val cleanUrl = WebTvSitesManager.normalizeWebUrl(quickWebTvUrl)
                                if (cleanUrl.isNotBlank() && cleanUrl != "https://" && cleanUrl != "http://") {
                                    WebTvSitesManager.addSite(
                                        context = webTvContext,
                                        name = quickWebTvName,
                                        url = cleanUrl
                                    )
                                    quickWebTvName = ""
                                    quickWebTvUrl = ""
                                } else {
                                    webTvSiteToEdit = null
                                    showAddWebTvSiteDialog = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentEmerald,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("settings_web_tv_quick_add_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Hinzufügen",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isEnglish) "Add" else "Hinzufügen",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                webTvSiteToEdit = null
                                showAddWebTvSiteDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GoldPrimary,
                                contentColor = ObsidianBlack
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .testTag("settings_web_tv_add_site_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Webseite hinzufügen",
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isEnglish) "Add Website (Icon & Category)" else "Mit Symbol & Kategorie hinzufügen",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                WebTvSitesManager.resetToDefaults(webTvContext)
                            },
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlow),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("settings_web_tv_reset_defaults_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Restore,
                                contentDescription = "Standard wiederherstellen",
                                tint = GoldLight,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isEnglish) "Defaults" else "Standard",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GoldLight
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (webTvSites.isEmpty()) {
                        Text(
                            text = if (isEnglish) {
                                "No Web TV websites saved. Tap 'Add Website' or 'Defaults'."
                            } else {
                                "Keine Web TV Webseiten hinterlegt. Tippe auf 'Webseite hinzufügen' oder 'Standard'."
                            },
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    } else {
                        val visibleSites = if (showAllWebTvSites) webTvSites else webTvSites.take(4)
                        visibleSites.forEachIndexed { index, site ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DarkSurface)
                                    .border(1.dp, BorderGlow.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DarkSurfaceElevated)
                                        .border(1.dp, site.badgeColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = site.iconEmoji, fontSize = 16.sp)
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = site.name,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(site.badgeColor.copy(alpha = 0.16f))
                                                .border(1.dp, site.badgeColor.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = site.category,
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = site.badgeColor
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = site.url,
                                        fontSize = 10.5.sp,
                                        color = AccentCyan,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                IconButton(
                                    onClick = {
                                        webTvSiteToEdit = site
                                        showAddWebTvSiteDialog = false
                                    },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("settings_web_tv_edit_${site.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Webseite bearbeiten",
                                        tint = GoldLight,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        WebTvSitesManager.deleteSite(webTvContext, site.id)
                                    },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("settings_web_tv_delete_${site.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Webseite löschen",
                                        tint = AccentRose,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            if (index < visibleSites.lastIndex) {
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                        }

                        if (webTvSites.size > 4) {
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = { showAllWebTvSites = !showAllWebTvSites },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlow)
                            ) {
                                Text(
                                    text = if (showAllWebTvSites) {
                                        if (isEnglish) "▲ Collapse Web TV List (Show 4)" else "▲ Kompaktansicht (4 von ${webTvSites.size} anzeigen)"
                                    } else {
                                        if (isEnglish) "▼ Show All ${webTvSites.size} Web TV Addresses" else "▼ Alle ${webTvSites.size} Web-TV-Adressen anzeigen"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GoldLight
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 1: Playlists Management (Kompaktes 2x2 Aktions-Grid + höhenbegrenzte Liste)
        item {
            SettingsSectionHeader(
                icon = Icons.Default.Tv,
                title = if (isEnglish) "Playlists & MAC Portals" else "Playlisten & MAC Portale",
                subtitle = if (isEnglish) "Manage MAC Portals (00:1A:79), Xtream Codes & M3U" else "Verwalte MAC Portale (Stalker/MAG), Xtream Codes & M3U"
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Kompakte 2x2 Aktions-Reihen statt 4 überhoher Einzel-Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenImportAssistant,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("settings_import_assistant_button"),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldLight)
                ) {
                    Text("🪄", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isEnglish) "Import (.txt / Paste)" else "Import-Assistent",
                        color = GoldLight,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = onOpenAddMacPortal,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("settings_add_mac_portal_button"),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan)
                ) {
                    Text("📡", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isEnglish) "+ MAC Portal" else "+ MAC Portal",
                        color = AccentCyan,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenAddPlaylist,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("settings_add_playlist_button"),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlow.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Hinzufügen",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (isEnglish) "M3U / Xtream" else "Manuell (M3U/Xtream)",
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = { showDeleteAllConfirmation = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("settings_delete_all_playlists_button"),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentRose.copy(alpha = 0.8f))
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = "Alle Playlisten löschen",
                        tint = AccentRose,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (isEnglish) "Delete All (${playlists.size})" else "Alle löschen (${playlists.size})",
                        color = AccentRose,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (playlists.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEnglish) "Configured Playlists (${playlists.size}):" else "Konfigurierte Playlisten (${playlists.size}):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextMuted
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isPlaylistOverviewUnlocked) AccentEmerald.copy(alpha = 0.18f) else DarkSurfaceElevated)
                            .border(
                                1.dp,
                                if (isPlaylistOverviewUnlocked) AccentEmerald else GoldPrimary.copy(alpha = 0.6f),
                                RoundedCornerShape(6.dp)
                            )
                            .clickable {
                                if (isPlaylistOverviewUnlocked) {
                                    isPlaylistOverviewUnlocked = false
                                } else {
                                    showUnlockOverviewPinPrompt = true
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("toggle_playlist_overview_unlock_button")
                    ) {
                        Text(
                            text = if (isPlaylistOverviewUnlocked) {
                                if (isEnglish) "🔒 Hide Data" else "🔒 Daten maskieren"
                            } else {
                                if (isEnglish) "🔓 Show Full Data (PIN)" else "🔓 Komplettansicht (Passwort)"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPlaylistOverviewUnlocked) AccentEmerald else GoldLight
                        )
                    }
                }

                if (playlists.size > 4) {
                    OutlinedTextField(
                        value = playlistSettingsSearch,
                        onValueChange = { playlistSettingsSearch = it },
                        placeholder = { Text(if (isEnglish) "Search playlists..." else "Playlisten filtern (${playlists.size})...", fontSize = 12.sp, color = TextMuted) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 2.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = TextPrimary),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = BorderGlow.copy(alpha = 0.5f),
                            focusedContainerColor = DarkSurface,
                            unfocusedContainerColor = DarkSurface
                        )
                    )
                }
            }
        }

        items(
            items = displayedConfiguredPlaylists,
            key = { "cfg_pl_${it.id}" },
            contentType = { "configured_playlist_card" }
        ) { pl ->
            val number = playlistNumberMap[pl.id] ?: 1
            val isCurrentActive = pl.id == activePlaylistId
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(
                            width = if (isCurrentActive) 1.5.dp else 1.dp,
                            color = if (isCurrentActive) GoldPrimary else BorderGlow.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .combinedClickable(
                            onClick = { onSelectPlaylist(pl.id) },
                            onLongClick = { playlistForActionsDialog = pl }
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCurrentActive) DarkSurfaceElevated else DarkSurface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkNavyCard)
                                    .border(1.dp, GoldPrimary.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "#$number",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GoldPrimary
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (isPlaylistOverviewUnlocked) pl.name else pl.getMaskedSettingsOverviewTitle(),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(DarkNavyCard)
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = pl.type.name,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = GoldLight
                                        )
                                    }
                                    if (isCurrentActive) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(AccentEmerald.copy(alpha = 0.2f))
                                                .border(0.5.dp, AccentEmerald, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = if (isEnglish) "ACTIVE" else "AKTIV",
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AccentEmerald
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = if (isPlaylistOverviewUnlocked) pl.getFullUnmaskedDetails() else pl.getMaskedSettingsOverviewDetails(isEnglish),
                                    fontSize = 9.5.sp,
                                    color = if (isPlaylistOverviewUnlocked) AccentCyan else TextMuted,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${pl.liveCount} Live TV • ${pl.movieCount} Filme • ${pl.seriesCount} Serien",
                                    fontSize = 9.5.sp,
                                    color = TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(
                                onClick = {
                                    if (isPlaylistOverviewUnlocked) {
                                        playlistToEdit = pl
                                    } else {
                                        playlistToUnlock = pl
                                        showPinPrompt = true
                                    }
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("edit_playlist_${pl.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Bearbeiten (PIN-geschützt)",
                                    tint = GoldLight,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            IconButton(
                                onClick = { onRefreshPlaylist(pl) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Aktualisieren",
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            IconButton(
                                onClick = { playlistForActionsDialog = pl },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("delete_playlist_${pl.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Löschen (Kein PIN erforderlich)",
                                    tint = AccentRose,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

        if (filteredConfiguredPlaylists.size > 4 && playlistSettingsSearch.isBlank()) {
            item(key = "configured_playlists_expand_toggle") {
                OutlinedButton(
                    onClick = { showAllConfiguredPlaylists = !showAllConfiguredPlaylists },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlow)
                ) {
                    Text(
                        text = if (showAllConfiguredPlaylists) {
                            if (isEnglish) "▲ Show Compact View (4 of ${filteredConfiguredPlaylists.size})" else "▲ Kompaktansicht (4 von ${filteredConfiguredPlaylists.size} Playlisten anzeigen)"
                        } else {
                            if (isEnglish) "▼ Show All ${filteredConfiguredPlaylists.size} Playlists" else "▼ Alle ${filteredConfiguredPlaylists.size} Playlisten einblenden"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldLight
                    )
                }
            }
        }

        // Section 2: Caching & Weak Internet Buffer
        item {
            SettingsSectionHeader(
                icon = Icons.Default.Speed,
                title = if (isEnglish) "Caching & Stream Stability" else "Caching & Puffer-Einstellungen",
                subtitle = if (isEnglish) "Smooth TV playback even with slow/unstable internet" else "Ruckelfreies Fernsehen auch bei schwachem/instabilem Internet"
            )

            Spacer(modifier = Modifier.height(8.dp))

            CachingMode.values().forEach { mode ->
                val isSelected = mode == settings.cachingMode
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(
                            1.dp,
                            if (isSelected) GoldPrimary else BorderGlow.copy(alpha = 0.2f),
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { onUpdateCachingMode(mode) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) DarkSurfaceElevated else DarkSurface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .border(2.dp, if (isSelected) GoldPrimary else TextMuted, CircleShape)
                                .background(if (isSelected) GoldPrimary else Color.Transparent)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = mode.titleDe,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) GoldLight else TextPrimary
                            )
                            Text(
                                text = mode.descriptionDe,
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        }

        // Section 2b: Lokale Playlisten-Speicherung & Start-Performance
        item {
            SettingsSectionHeader(
                icon = Icons.Default.Dns,
                title = if (isEnglish) "Local Playlist Cache & Start Speed" else "Lokaler Speicher & Start-Performance",
                subtitle = if (isEnglish) "Playlists are stored locally in Room SQLite database" else "Playlisten werden dauerhaft lokal im Gerätespeicher abgelegt"
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, BorderGlow.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isEnglish) "Update playlists on app launch" else "Playlisten beim App-Start aktualisieren",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (settings.autoUpdateOnStart)
                                    (if (isEnglish) "Active: Fetches server updates at launch" else "Aktiv: Ruft beim Start Updates vom Server ab (kann Start verzögern)")
                                else
                                    (if (isEnglish) "Disabled: Starts instantly without network delay using local database" else "Deaktiviert: Blitzschneller Sofort-Start ohne Wartezeit aus lokaler SQLite-Datenbank"),
                                fontSize = 11.sp,
                                color = if (settings.autoUpdateOnStart) AccentRose else AccentEmerald
                            )
                        }

                        Switch(
                            checked = settings.autoUpdateOnStart,
                            onCheckedChange = { onUpdateAutoUpdateOnStart(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = GoldPrimary,
                                checkedTrackColor = GoldDark,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = DarkSurfaceElevated
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (isEnglish)
                            "Playlists and channels remain saved on this device. You can manually refresh any playlist at any time via the refresh button in the top bar or playlist menu."
                        else
                            "Playlisten und Sender bleiben dauerhaft lokal gespeichert. Manuelle Updates können jederzeit über das Aktualisieren-Symbol in der oberen Leiste oder im Playlisten-Menü ausgeführt werden.",
                        fontSize = 10.sp,
                        color = TextMuted,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        // Section 3: Player Engine Selection
        item {
            SettingsSectionHeader(
                icon = Icons.Default.PlayCircle,
                title = if (isEnglish) "Video Player Engine" else "Video Player Auswahl",
                subtitle = if (isEnglish) "Nexus Player (integrated) or external media players" else "Nexus Player (integriert) oder externe Medienplayer"
            )

            Spacer(modifier = Modifier.height(8.dp))

            PlayerEngine.values().forEach { engine ->
                val isSelected = engine == settings.preferredPlayer
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(
                            1.dp,
                            if (isSelected) GoldPrimary else BorderGlow.copy(alpha = 0.2f),
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { onUpdatePreferredPlayer(engine) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) DarkSurfaceElevated else DarkSurface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .border(2.dp, if (isSelected) GoldPrimary else TextMuted, CircleShape)
                                .background(if (isSelected) GoldPrimary else Color.Transparent)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = engine.displayName,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) GoldLight else TextPrimary
                        )
                    }
                }
            }

            if (settings.preferredPlayer == PlayerEngine.BUILT_IN_NEXUS) {
                Spacer(modifier = Modifier.height(10.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, GoldPrimary.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isEnglish) "Nexus Player True Fullscreen" else "Nexus Player Vollbild-Modus",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GoldLight
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isEnglish)
                                        "Hides status bar and navigation buttons for true edge-to-edge playback"
                                    else
                                        "Blendet Statusleiste und Navigationstasten für echte randlose Vollbild-Wiedergabe aus",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }

                            Switch(
                                checked = settings.nexusFullScreenEnabled,
                                onCheckedChange = { onUpdateNexusFullScreen(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = GoldPrimary,
                                    checkedTrackColor = GoldDark,
                                    uncheckedThumbColor = TextMuted,
                                    uncheckedTrackColor = DarkSurface
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = if (isEnglish) "Default Video Aspect Ratio:" else "Standard Bildformat im Vollbild:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                Triple("FILL", "Vollbild (FILL)", "Vollbild gestreckt"),
                                Triple("ZOOM", "Zoom (ZOOM)", "Vollbild beschnitten"),
                                Triple("FIT", "16:9 (FIT)", "Standard 16:9")
                            ).forEach { (modeKey, modeTitle, _) ->
                                val isSelected = settings.aspectRatio.equals(modeKey, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) GoldPrimary else DarkSurface)
                                        .border(
                                            1.dp,
                                            if (isSelected) GoldLight else BorderGlow.copy(alpha = 0.3f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { onUpdateAspectRatio(modeKey) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = modeTitle,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) ObsidianBlack else TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 4: Premium Features (Background Playback & Display Off)
        item {
            SettingsSectionHeader(
                icon = Icons.Default.Headphones,
                title = if (isEnglish) "Background Playback" else "Hintergrund-Wiedergabe",
                subtitle = if (isEnglish) "Continue audio playback when screen is turned off" else "Audiowiedergabe fortsetzen bei ausgeschaltetem Display"
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, BorderGlow.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEnglish) "Foreground Service & Background Playback" else "Vordergrunddienst & Standby-Schutz (Android 14/15/16)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (isEnglish) "Keeps stream active via Media Foreground Service, WakeLock & WiFi-Lock when app is minimized or screen is off" else "Hält den Stream über Android Foreground-Service, WakeLock & WiFi-Lock dauerhaft aktiv – kein Abreißen im Standby oder beim App-Wechsel.",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }

                    Switch(
                        checked = settings.backgroundAudioPlayback,
                        onCheckedChange = onToggleBackgroundPlayback,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = GoldPrimary,
                            uncheckedTrackColor = DarkNavyCard
                        )
                    )
                }
            }
        }

        // Section: EPG Timezone & Synchronization Settings
        item {
            val clipboardManager = LocalClipboardManager.current

            SettingsSectionHeader(
                icon = Icons.Default.Tv,
                title = if (isEnglish) "EPG Custom URL & Synchronization" else "EPG Eigene URL & Aktualisierung",
                subtitle = if (isEnglish) "Configure custom XMLTV URL (.xml / .xml.gz), refresh guide data, and adjust timezone" else "Eigene EPG-URL eingeben (.xml / .xml.gz), Programmzeitschrift aktualisieren und Zeitzone anpassen"
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, BorderGlow.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Custom EPG Source URL Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEnglish) "Custom EPG URL (.xml / .xml.gz)" else "Eigene EPG-Server URL (.xml / .xml.gz):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldLight
                        )

                        // Quick Paste from Clipboard
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    val clipText = clipboardManager.getText()?.text?.trim()
                                    if (!clipText.isNullOrBlank()) {
                                        customEpgInput = clipText
                                        onUpdateCustomEpgUrl(clipText)
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste",
                                    tint = AccentCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isEnglish) "Paste" else "Einfügen",
                                    fontSize = 11.sp,
                                    color = AccentCyan,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (customEpgInput.isNotBlank()) {
                                androidx.compose.material3.TextButton(
                                    onClick = {
                                        customEpgInput = ""
                                        onUpdateCustomEpgUrl("")
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = TextMuted,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = if (isEnglish) "Clear" else "Leeren",
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    androidx.compose.material3.OutlinedTextField(
                        value = customEpgInput,
                        onValueChange = {
                            customEpgInput = it
                            onUpdateCustomEpgUrl(it)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_custom_epg_url_input"),
                        singleLine = false,
                        maxLines = 3,
                        placeholder = {
                            Text("https://www.free-epg.de/api/epg/de.xml.gz", color = TextMuted, fontSize = 11.sp)
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkNavyCard,
                            unfocusedContainerColor = DarkNavyCard,
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = BorderGlow.copy(alpha = 0.3f),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Quick Select EPG Presets
                    Text(
                        text = if (isEnglish) "Recommended EPG Feeds (1-Click selection):" else "Empfohlene EPG-Feeds (1-Klick Vorlagen):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    com.example.data.parser.FreeEpgService.DEFAULT_EPG_SERVERS.forEach { server ->
                        val isSelected = customEpgInput.trim() == server.url.trim()
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) GoldPrimary else BorderGlow.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    customEpgInput = server.url
                                    onUpdateCustomEpgUrl(server.url)
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) DarkNavyCard else DarkSurfaceElevated
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = server.name,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) GoldLight else TextPrimary
                                    )
                                    Text(
                                        text = server.url,
                                        fontSize = 10.sp,
                                        color = TextMuted,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) GoldPrimary.copy(alpha = 0.2f) else DarkSurface)
                                        .border(0.5.dp, if (isSelected) GoldPrimary else BorderGlow.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = server.country,
                                        fontSize = 10.sp,
                                        color = if (isSelected) GoldPrimary else TextSecondary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // BIG EPG REFRESH / SYNC BUTTON
                    val isSyncing = syncState is EpgSyncState.Syncing

                    Button(
                        onClick = { onTriggerEpgSync(customEpgInput) },
                        enabled = !isSyncing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldPrimary,
                            contentColor = ObsidianBlack,
                            disabledContainerColor = DarkNavyCard,
                            disabledContentColor = TextMuted
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("settings_epg_refresh_button")
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = GoldPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isEnglish) "Updating EPG Guide..." else "EPG wird aktualisiert...",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = GoldLight
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = "EPG Refresh",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isEnglish) "Refresh EPG Guide Now" else "🔄 EPG jetzt aktualisieren",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = ObsidianBlack
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onSyncBothEpgFeeds() },
                            enabled = !isSyncing,
                            modifier = Modifier.weight(1f).height(38.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan.copy(alpha = 0.6f)),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isEnglish) "Sync Both Feeds" else "Beide Feeds laden",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = { onClearAllEpg() },
                            modifier = Modifier.weight(1f).height(38.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRose),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentRose.copy(alpha = 0.6f)),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isEnglish) "Clear EPG Data" else "Alle EPG löschen",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // SYNC STATE DISPLAY BANNER
                    when (syncState) {
                        is EpgSyncState.Syncing -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = DarkNavyCard),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = AccentCyan,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = syncState.message,
                                            fontSize = 12.sp,
                                            color = AccentCyan
                                        )
                                    }
                                    if (syncState.progressPercent > 0) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        LinearProgressIndicator(
                                            progress = { syncState.progressPercent / 100f },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(2.dp)),
                                            color = AccentCyan,
                                            trackColor = DarkSurface
                                        )
                                    }
                                }
                            }
                        }

                        is EpgSyncState.Success -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = DarkNavyCard),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = AccentEmerald,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = syncState.message,
                                        fontSize = 12.sp,
                                        color = AccentEmerald,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        is EpgSyncState.Error -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = DarkNavyCard),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AccentRose.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = AccentRose,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = syncState.message,
                                        fontSize = 12.sp,
                                        color = AccentRose
                                    )
                                }
                            }
                        }

                        is EpgSyncState.Idle -> {
                            // Nothing to show
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Timezone Dropdown Selector
                    Text(
                        text = if (isEnglish) "EPG Display Timezone" else "EPG Anzeige-Zeitzone",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldLight
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    val selectedTz = com.example.data.parser.FreeEpgService.SUPPORTED_TIMEZONES.find { it.id == settings.epgTimezone }
                        ?: com.example.data.parser.FreeEpgService.SUPPORTED_TIMEZONES.first()

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkNavyCard)
                            .border(1.dp, GoldPrimary, RoundedCornerShape(8.dp))
                            .clickable { isTimezoneDropdownOpen = true }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedTz.label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Select",
                                tint = GoldLight,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        androidx.compose.material3.DropdownMenu(
                            expanded = isTimezoneDropdownOpen,
                            onDismissRequest = { isTimezoneDropdownOpen = false },
                            modifier = Modifier.background(DarkNavyCard)
                        ) {
                            com.example.data.parser.FreeEpgService.SUPPORTED_TIMEZONES.forEach { tz ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = tz.label,
                                            color = if (tz.id == settings.epgTimezone) GoldPrimary else TextPrimary,
                                            fontWeight = if (tz.id == settings.epgTimezone) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        onUpdateEpgTimezone(tz.id)
                                        isTimezoneDropdownOpen = false
                                    }
                                )
                            }
                        }
                    }

                    // Globale EPG-Zeitanpassung (Global Time Offset)
                    Spacer(modifier = Modifier.height(14.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurfaceElevated)
                            .border(1.dp, GoldPrimary.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = "Global Offset",
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = if (isEnglish) "Global EPG Time Offset" else "Globale EPG-Zeitanpassung",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = if (isEnglish) "Unified time shift for all channels and providers (+/- hours)" else "Einheitlicher Zeitversatz für alle Sender und Feeds (+/- Stunden)",
                                        fontSize = 10.sp,
                                        color = TextMuted
                                    )
                                }
                            }

                            // Current offset badge
                            val currentGlobalOffset = settings.epgTimeOffsetHours
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkSurface)
                                    .border(1.dp, GoldPrimary, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (currentGlobalOffset == 0) "±0h (Live)" else "${if (currentGlobalOffset > 0) "+" else ""}${currentGlobalOffset}h Versatz",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GoldPrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Slider for global offset
                        Slider(
                            value = settings.epgTimeOffsetHours.toFloat(),
                            onValueChange = { newVal ->
                                onUpdateEpgTimeOffsetHours(newVal.roundToInt())
                            },
                            valueRange = -12f..12f,
                            steps = 23,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("epg_global_offset_slider"),
                            colors = SliderDefaults.colors(
                                thumbColor = GoldPrimary,
                                activeTrackColor = GoldPrimary,
                                inactiveTrackColor = DarkSurface
                            )
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Quick buttons: -1h, 0h (Reset), +1h
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "-12h",
                                fontSize = 10.sp,
                                color = TextMuted
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = { onUpdateEpgTimeOffsetHours(settings.epgTimeOffsetHours - 1) },
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("-1h", color = GoldLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                Button(
                                    onClick = { onUpdateEpgTimeOffsetHours(0) },
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("±0h (Reset)", color = if (settings.epgTimeOffsetHours == 0) AccentEmerald else TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                Button(
                                    onClick = { onUpdateEpgTimeOffsetHours(settings.epgTimeOffsetHours + 1) },
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("+1h", color = GoldLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Text(
                                text = "+12h",
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Live Time & Timezone Helper Card
                    val berlinTime = com.example.data.parser.FreeEpgService.formatProgramTime(System.currentTimeMillis(), "Europe/Berlin")
                    val effectiveEpgTime = com.example.data.parser.FreeEpgService.formatProgramTime(
                        System.currentTimeMillis() + settings.epgTimeOffsetHours * 3600 * 1000L,
                        settings.epgTimezone
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🕒 Aktuelle TV-Zeit (Berlin/DE):",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "$berlinTime Uhr",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GoldLight
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📺 Berechnete EPG-Anzeigezeit:",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "$effectiveEpgTime Uhr",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentCyan
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "💡 Info Sommerzeit: In der Sommerzeit (CEST, UTC+2) beträgt der Zeitunterschied zu Thailand (UTC+7) genau 5 Stunden (im Winter 6 Stunden).",
                                fontSize = 10.sp,
                                color = TextMuted,
                                lineHeight = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Force Refresh & Re-Map Button
                    Button(
                        onClick = {
                            onForceEpgRemap()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("force_epg_remap_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldDark),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Force Re-map",
                            tint = GoldLight,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEnglish) "Force EPG Re-map & Sync Now" else "Jetzt neu kalibrieren & EPG aktualisieren",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldLight
                        )
                    }
                }
            }
        }

        // Section 5: Backup & Restore with Password Protection
        item {
            SettingsSectionHeader(
                icon = Icons.Default.Security,
                title = if (isEnglish) "Backup & Restore (Password Protected)" else "Backup & Wiederherstellung (Passwortschutz)",
                subtitle = if (isEnglish) "Export or import playlists securely without exposing credentials" else "Playlisten sicher exportieren oder importieren ohne Zugangsdaten preiszugeben"
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenBackupDialog,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlow)
                ) {
                    Icon(
                        imageVector = Icons.Default.Backup,
                        contentDescription = "Backup",
                        tint = GoldPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isEnglish) "Export Backup" else "Backup erstellen",
                        fontSize = 12.sp,
                        color = GoldLight
                    )
                }

                Button(
                    onClick = onOpenRestoreDialog,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlow)
                ) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = "Restore",
                        tint = AccentCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isEnglish) "Restore" else "Wiederherstellen",
                        fontSize = 12.sp,
                        color = AccentCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Installations-Schutz Status & App sperren
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, GoldPrimary.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Security",
                                tint = GoldPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isEnglish) "Installation Protection" else "Installations-Schutz",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = GoldLight
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isEnglish)
                                "App access is secured with the installation password (supports 4 to 12 digits)."
                            else
                                "Der Zugriff auf die App ist durch das Installations-Kennwort geschützt (4 bis 12 Zahlen zulässig).",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }

                    OutlinedButton(
                        onClick = onLockApp,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldPrimary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock",
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isEnglish) "Lock App" else "App sperren",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Section 6: Language
        item {
            SettingsSectionHeader(
                icon = Icons.Default.Language,
                title = if (isEnglish) "Language / Sprache" else "Sprache / Language",
                subtitle = if (isEnglish) "German is pre-selected by default" else "Deutsch ist standardmäßig vorgewählt"
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isDe = settings.language == "de"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isDe) GoldPrimary else DarkSurface)
                        .border(1.dp, if (isDe) GoldPrimary else BorderGlow, RoundedCornerShape(10.dp))
                        .clickable { onUpdateLanguage("de") }
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🇩🇪 Deutsch (Standard)",
                        fontWeight = if (isDe) FontWeight.Bold else FontWeight.Medium,
                        color = if (isDe) Color.Black else TextPrimary,
                        fontSize = 12.sp
                    )
                }

                val isEn = settings.language == "en"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isEn) GoldPrimary else DarkSurface)
                        .border(1.dp, if (isEn) GoldPrimary else BorderGlow, RoundedCornerShape(10.dp))
                        .clickable { onUpdateLanguage("en") }
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🇬🇧 English",
                        fontWeight = if (isEn) FontWeight.Bold else FontWeight.Medium,
                        color = if (isEn) Color.Black else TextPrimary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // DACH & German-Only Performance Sync Option
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, GoldPrimary.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onUpdateSyncOnlyGermanWhenGerman(!settings.syncOnlyGermanWhenGerman) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isEnglish) "⚡ Fast Sync: German / DACH Only" else "⚡ High-Speed: Nur deutsche Inhalte",
                                    fontWeight = FontWeight.Bold,
                                    color = GoldPrimary,
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isEnglish)
                                    "When app language is German, international live channels, movies, and series are skipped during sync. Dramatically speeds up movie & series browsing!"
                                else
                                    "Wenn App-Sprache Deutsch ist, werden internationale TV-Sender, fremdsprachige Filme und Serien nicht synchronisiert. Filme & Serien öffnen blitzschnell!",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                lineHeight = 15.sp
                            )
                        }
                        Switch(
                            checked = settings.syncOnlyGermanWhenGerman,
                            onCheckedChange = { onUpdateSyncOnlyGermanWhenGerman(it) },
                            modifier = Modifier.testTag("fast_sync_german_switch"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = GoldPrimary,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = DarkSurface
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onOptimizeDatabase,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkSurface,
                            contentColor = GoldPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEnglish) "Optimize Database Now" else "Filme & Serien Datenbank jetzt optimieren",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Section 7: Live Recordings & Downloads Manager
        item {
            val context = LocalContext.current
            val displayFolder = remember(context) { DownloadStorageHelper.getDisplayFolderPath(context) }

            SettingsSectionHeader(
                icon = Icons.Default.Download,
                title = if (isEnglish) "Recordings & Downloads" else "Aufnahmen & Mediathek Downloads",
                subtitle = if (isEnglish) "Saved directly in system Downloads folder ($displayFolder)" else "Gespeichert im System-Downloads-Ordner ($displayFolder)"
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Storage Folder Info Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, GoldPrimary.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isEnglish) "Storage Location: $displayFolder" else "Speicherort: $displayFolder",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (isEnglish) "All recordings & movie downloads are saved here." else "Alle TV-Mitschnitte & Mediathek-Filme werden hier abgelegt.",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedButton(
                        onClick = { DownloadStorageHelper.openDownloadsFolder(context) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldPrimary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f)),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isEnglish) "Open Downloads" else "Ordner öffnen",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            val hasWritePermission = remember(context) { DownloadStorageHelper.hasStoragePermission(context) }

            // Storage Permission Status Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(
                        1.dp,
                        if (hasWritePermission) AccentEmerald.copy(alpha = 0.5f) else AccentRose.copy(alpha = 0.6f),
                        RoundedCornerShape(10.dp)
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = if (hasWritePermission) DarkSurfaceElevated else AccentRose.copy(alpha = 0.08f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (hasWritePermission) Icons.Default.CheckCircle else Icons.Default.Security,
                            contentDescription = null,
                            tint = if (hasWritePermission) AccentEmerald else AccentRose,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (hasWritePermission) {
                                    if (isEnglish) "Storage Write Permission: Active" else "Schreibrechte für Speicher: Aktiviert"
                                } else {
                                    if (isEnglish) "Storage Write Permission: Not Granted" else "Schreibrechte für Speicher: Erforderlich"
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (hasWritePermission) AccentEmerald else AccentRose
                            )
                            Text(
                                text = if (hasWritePermission) {
                                    if (isEnglish) "Full write access to Downloads/IPTV is active." else "Voller Schreibzugriff auf Downloads/IPTV aktiv."
                                } else {
                                    if (isEnglish) "Required to save recordings and videos into Downloads/IPTV." else "Erforderlich, um Mitschnitte & Videos in Downloads/IPTV zu speichern."
                                },
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }

                    if (!hasWritePermission) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { DownloadStorageHelper.requestStoragePermission(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentRose, contentColor = Color.White),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (isEnglish) "Grant Access" else "Rechte erteilen",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (recordings.isEmpty()) {
                Text(
                    text = if (isEnglish) "No recordings yet. Tap the REC button in player or download icon on movies." else "Noch keine Aufnahmen vorhanden. Nutze den REC-Button im Player oder das Download-Symbol bei Filmen.",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            } else {
                recordings.forEach { rec ->
                    val isDownloading = rec.status == RecordingStatus.DOWNLOADING
                    val isDownloaded = rec.status == RecordingStatus.DOWNLOADED || rec.status == RecordingStatus.COMPLETED
                    val canPlay = !isDownloading && (isDownloaded || !rec.filePath.isNullOrBlank() || rec.streamUrl.isNotBlank())

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                width = 1.dp,
                                color = when {
                                    isDownloading -> GoldPrimary.copy(alpha = 0.6f)
                                    isDownloaded -> Color(0xFF00E676).copy(alpha = 0.4f)
                                    else -> BorderGlow.copy(alpha = 0.2f)
                                },
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable(enabled = canPlay) { onPlayRecording(rec) },
                        colors = CardDefaults.cardColors(containerColor = DarkSurface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isDownloading) {
                                    CircularProgressIndicator(
                                        progress = { rec.progressPercent / 100f },
                                        modifier = Modifier.size(28.dp),
                                        color = GoldPrimary,
                                        strokeWidth = 2.5.dp
                                    )
                                } else {
                                    IconButton(
                                        onClick = { onPlayRecording(rec) },
                                        enabled = canPlay,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayCircle,
                                            contentDescription = "Abspielen",
                                            tint = if (canPlay) Color(0xFF00E676) else GoldPrimary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rec.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(2.dp))

                                    if (isDownloading) {
                                        Text(
                                            text = "${rec.progressPercent}% Heruntergeladen",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = GoldPrimary
                                        )
                                    } else if (isDownloaded) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(0xFF00E676),
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (isEnglish) "Ready to play" else "Wiedergabebereit",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF00E676)
                                            )
                                            if (rec.fileSizeFormatted.isNotBlank()) {
                                                Text(
                                                    text = " • ${rec.fileSizeFormatted}",
                                                    fontSize = 11.sp,
                                                    color = TextMuted
                                                )
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = "${rec.fileSizeFormatted} • ${rec.status.name}",
                                            fontSize = 11.sp,
                                            color = AccentCyan
                                        )
                                    }

                                    if (!rec.filePath.isNullOrBlank()) {
                                        val fileName = try { File(rec.filePath).name } catch (e: Exception) { "" }
                                        if (fileName.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Folder,
                                                    contentDescription = null,
                                                    tint = GoldPrimary.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(11.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Downloads/${DownloadStorageHelper.SUBFOLDER}/$fileName",
                                                    fontSize = 10.sp,
                                                    color = TextMuted,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }

                                if (isDownloading) {
                                    IconButton(onClick = { onCancelDownload(rec) }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Download abbrechen",
                                            tint = AccentRose,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                } else {
                                    IconButton(onClick = { onDeleteRecording(rec) }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Löschen",
                                            tint = AccentRose,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            if (isDownloading) {
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { rec.progressPercent / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = GoldPrimary,
                                    trackColor = DarkSurfaceElevated
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurfaceElevated)
                .border(1.dp, BorderGlow, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = GoldPrimary,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
    }
}
