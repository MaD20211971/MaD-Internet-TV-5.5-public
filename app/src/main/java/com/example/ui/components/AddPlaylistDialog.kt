package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.example.data.parser.VpnRequirementHelper
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentRose
import com.example.ui.theme.BorderGlow
import com.example.ui.theme.DarkNavyCard
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.GoldLight
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun AddPlaylistDialog(
    onDismiss: () -> Unit,
    onAddM3uUrl: (name: String, url: String) -> Unit,
    onAddXtream: (name: String, url: String, user: String, pass: String) -> Unit,
    onAddMacPortal: ((name: String, portalUrl: String, mac: String) -> Unit)? = null,
    onAddLocalFile: (name: String, fileUri: Uri) -> Unit,
    onAddBatchWithoutSync: ((List<com.example.data.parser.ImportedPlaylistCandidate>) -> Unit)? = null,
    onOpenImportAssistant: (() -> Unit)? = null,
    initialTypeIndex: Int = 0,
    isEnglish: Boolean = false
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedTypeIndex by remember(initialTypeIndex) { mutableIntStateOf(initialTypeIndex.coerceIn(0, 3)) }
    val types = listOf(
        "M3U URL",
        "Xtream Codes",
        "MAC Portal",
        if (isEnglish) "Local M3U" else "Lokale M3U"
    )

    var playlistName by remember { mutableStateOf("") }
    var requiresVpn by remember { mutableStateOf(false) }
    var selectedVpnCountry by remember { mutableStateOf("DE") }
    var m3uUrl by remember { mutableStateOf("") }

    var xtreamServer by remember { mutableStateOf("http://") }
    var xtreamUser by remember { mutableStateOf("") }
    var xtreamPass by remember { mutableStateOf("") }

    var macPortalUrl by remember { mutableStateOf("http://") }
    var macAddress by remember { mutableStateOf("00:1A:79:") }

    var detectedBatchCandidates by remember {
        mutableStateOf<List<com.example.data.parser.ImportedPlaylistCandidate>>(emptyList())
    }

    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            selectedFileName = uri.lastPathSegment ?: "m3u_playlist.m3u"
            validationError = null
        }
    }

    val performSave: () -> Unit = {
        if (detectedBatchCandidates.size > 1 && onAddBatchWithoutSync != null) {
            onAddBatchWithoutSync(detectedBatchCandidates)
            onDismiss()
        } else {
            val rawName = playlistName.ifBlank {
                when (selectedTypeIndex) {
                    1 -> "Xtream Codes"
                    2 -> {
                        val cleanHost = com.example.data.parser.PlaylistImportExtractor.cleanMacPortalUrl(macPortalUrl)
                            .removePrefix("http://")
                            .removePrefix("https://")
                            .substringBefore(":")
                            .substringBefore("/")
                            .ifBlank { "MAC Portal" }
                        "MAC - $cleanHost"
                    }
                    3 -> selectedFileName ?: "Lokale Playliste"
                    else -> "Meine Playliste"
                }
            }
            val finalName = VpnRequirementHelper.formatPlaylistNameWithVpn(
                name = rawName,
                requiresVpn = requiresVpn,
                countryCode = selectedVpnCountry
            )
            when (selectedTypeIndex) {
                0 -> {
                    if (m3uUrl.isBlank()) {
                        validationError = if (isEnglish) "Please enter an M3U URL" else "Bitte eine M3U Web-URL eingeben"
                    } else {
                        val multi = if (m3uUrl.contains("\n") || m3uUrl.count { it == ':' } > 3) {
                            com.example.data.parser.PlaylistImportExtractor.extractFromText(m3uUrl, finalName)
                        } else emptyList()
                        if (multi.size > 1 && onAddBatchWithoutSync != null) {
                            onAddBatchWithoutSync(multi)
                        } else {
                            onAddM3uUrl(finalName, m3uUrl.trim())
                        }
                        onDismiss()
                    }
                }
                1 -> {
                    val server = xtreamServer.trim()
                    if (server.isBlank() || server == "http://" || server == "https://") {
                        validationError = if (isEnglish) "Please enter a valid Server URL" else "Bitte eine gültige Server URL (mit Port) eingeben"
                    } else {
                        onAddXtream(finalName, server, xtreamUser.trim(), xtreamPass.trim())
                        onDismiss()
                    }
                }
                2 -> {
                    val cleanedUrl = com.example.data.parser.PlaylistImportExtractor.cleanMacPortalUrl(macPortalUrl)
                    val cleanedMac = com.example.data.parser.PlaylistImportExtractor.cleanMacAddress(macAddress)
                    if (cleanedUrl.isBlank() || cleanedUrl == "http://" || cleanedUrl == "https://") {
                        validationError = if (isEnglish) "Please enter a valid MAC Portal URL" else "Bitte eine gültige MAC Portal-URL eingeben (z.B. http://portal.xyz:8080)"
                    } else if (cleanedMac.length < 17) {
                        validationError = if (isEnglish) "Please enter a complete 17-char MAC address (00:1A:79:XX:XX:XX)" else "Bitte eine vollständige MAC-Adresse eingeben (z.B. 00:1A:79:XX:XX:XX)"
                    } else {
                        onAddMacPortal?.invoke(finalName, cleanedUrl, cleanedMac)
                        onDismiss()
                    }
                }
                3 -> {
                    val uri = selectedFileUri
                    if (uri == null) {
                        validationError = if (isEnglish) "Please choose an M3U file" else "Bitte eine Datei aus dem Dateimanager wählen"
                    } else {
                        onAddLocalFile(finalName, uri)
                        onDismiss()
                    }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 580.dp)
                .imePadding()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, GoldPrimary, RoundedCornerShape(16.dp))
                .testTag("add_playlist_dialog"),
            colors = CardDefaults.cardColors(containerColor = DarkNavyCard)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Dialog Header (Fixed)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEnglish) "Add New Playlist / MAC Portal" else "Neue Playliste / MAC Portal hinzufügen",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldPrimary
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Schließen",
                            tint = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Type Tabs (Fixed at top)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    types.forEachIndexed { idx, label ->
                        val isSelected = idx == selectedTypeIndex
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) GoldPrimary else DarkSurface)
                                .clickable {
                                    selectedTypeIndex = idx
                                    validationError = null
                                }
                                .padding(vertical = 8.dp)
                                .testTag("playlist_type_tab_$idx"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.Black else TextSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable Content Area (Inputs)
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    if (onOpenImportAssistant != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, GoldLight.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                .clickable {
                                    onDismiss()
                                    onOpenImportAssistant()
                                },
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🪄", fontSize = 18.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = if (isEnglish) "Playlist Import Assistant" else "Intelligenter Import-Assistent",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = GoldLight
                                        )
                                        Text(
                                            text = if (isEnglish) "Auto-detect MAC Portals, Xtream & M3U from TXT or Paste" else "Erkennt MAC Portale, Xtream & M3U aus Datei oder Text!",
                                            fontSize = 10.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }
                                Text(
                                    text = "Öffnen →",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentCyan
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Playlist Name Input
                    OutlinedTextField(
                        value = playlistName,
                        onValueChange = {
                            playlistName = it
                            validationError = null
                        },
                        label = { Text(if (isEnglish) "Playlist Name (optional)" else "Name der Playliste (optional, z.B. Mein Portal)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = customTextFieldColors()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // VPN Requirement & Country Prefix Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                1.dp,
                                if (requiresVpn) GoldPrimary else BorderGlow.copy(alpha = 0.3f),
                                RoundedCornerShape(10.dp)
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (requiresVpn) DarkSurfaceElevated else DarkSurface
                        )
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { requiresVpn = !requiresVpn },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (requiresVpn) "🔒" else "🛡️", fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = if (isEnglish) "VPN Required (Country Prefix)" else "VPN erforderlich (Länderkennung)",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (requiresVpn) GoldPrimary else TextPrimary
                                        )
                                        Text(
                                            text = if (isEnglish)
                                                "Sets country code [DE], [AT], [CH] at beginning of name"
                                            else
                                                "Setzt Länderkennung [DE], [AT], [CH] an den Anfang des Namens",
                                            fontSize = 10.sp,
                                            color = TextMuted
                                        )
                                    }
                                }
                                Switch(
                                    checked = requiresVpn,
                                    onCheckedChange = { requiresVpn = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = ObsidianBlack,
                                        checkedTrackColor = GoldPrimary,
                                        uncheckedThumbColor = TextMuted,
                                        uncheckedTrackColor = DarkSurface
                                    )
                                )
                            }

                            if (requiresVpn) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (isEnglish) "Selected Country:" else "Länderkennung wählen:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = GoldLight
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(VpnRequirementHelper.POPULAR_VPN_COUNTRIES) { (code, countryName) ->
                                        val isSelected = selectedVpnCountry == code
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isSelected) GoldPrimary else DarkNavyCard)
                                                .border(
                                                    1.dp,
                                                    if (isSelected) GoldLight else BorderGlow.copy(alpha = 0.4f),
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .clickable { selectedVpnCountry = code }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "[$code] $countryName",
                                                fontSize = 10.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) ObsidianBlack else TextPrimary
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                val previewName = VpnRequirementHelper.formatPlaylistNameWithVpn(
                                    name = playlistName.ifBlank { "Meine Playliste" },
                                    requiresVpn = true,
                                    countryCode = selectedVpnCountry
                                )
                                Text(
                                    text = "Name in Liste: $previewName",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AccentCyan
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Type Specific Inputs
                    when (selectedTypeIndex) {
                        0 -> {
                            // M3U URL
                            OutlinedTextField(
                                value = m3uUrl,
                                onValueChange = {
                                    m3uUrl = it
                                    validationError = null
                                },
                                label = { Text("M3U / M3U_PLUS Web-URL") },
                                placeholder = { Text("https://example.com/playlist.m3u") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = customTextFieldColors()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = performSave,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("submit_m3u_tab_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = "Speichern", tint = Color.Black, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isEnglish) "Save & Load M3U Playlist" else "M3U Playliste speichern & laden",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                        1 -> {
                            // Xtream Codes
                            OutlinedTextField(
                                value = xtreamServer,
                                onValueChange = {
                                    xtreamServer = it
                                    validationError = null
                                },
                                label = { Text("Server URL (mit Port)") },
                                placeholder = { Text("http://mein-server.com:8080") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = customTextFieldColors()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = xtreamUser,
                                onValueChange = {
                                    xtreamUser = it
                                    validationError = null
                                },
                                label = { Text("Benutzername / Username") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = customTextFieldColors()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = xtreamPass,
                                onValueChange = {
                                    xtreamPass = it
                                    validationError = null
                                },
                                label = { Text("Passwort") },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = customTextFieldColors()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = performSave,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("submit_xtream_tab_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = "Speichern", tint = Color.Black, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isEnglish) "Save & Load Xtream Playlist" else "Xtream Playliste speichern & laden",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                        2 -> {
                            // MAC Portal (Stalker / Ministra / XUI / MAG STB)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.dp, AccentCyan.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = if (isEnglish)
                                            "📡 Universal MAC Portal (Stalker / Ministra / XUI / MAG 250-524)"
                                        else
                                            "📡 Universal MAC Portal (Stalker / Ministra / XUI / MAG 250-524)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentCyan
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (isEnglish)
                                            "Supports all portal endpoints (/c/, /stalker_portal/c/, /portal.php). Downloads DE/AT/CH & German VOD first."
                                        else
                                            "Unterstützt alle Portale (/c/, /stalker_portal/c/, /portal.php). Lädt DE, AT, CH & deutsche Filme/Serien sofort.",
                                        fontSize = 10.sp,
                                        color = TextSecondary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            try {
                                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                                val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
                                                if (!clipText.isNullOrBlank()) {
                                                    val allExtracted = com.example.data.parser.PlaylistImportExtractor.extractFromText(clipText, "MAC")
                                                    if (allExtracted.size > 1) {
                                                        detectedBatchCandidates = allExtracted
                                                        val firstMac = allExtracted.firstOrNull { it.type == com.example.data.model.PlaylistType.MAC_PORTAL } ?: allExtracted.first()
                                                        macPortalUrl = firstMac.url
                                                        macAddress = firstMac.macAddress
                                                        validationError = null
                                                    } else {
                                                        detectedBatchCandidates = emptyList()
                                                        val extracted = allExtracted.firstOrNull { it.type == com.example.data.model.PlaylistType.MAC_PORTAL }
                                                        if (extracted != null) {
                                                            macPortalUrl = extracted.url
                                                            macAddress = extracted.macAddress
                                                            if (playlistName.isBlank()) {
                                                                playlistName = extracted.name
                                                            }
                                                            validationError = null
                                                        } else {
                                                            val foundMac = com.example.data.parser.PlaylistImportExtractor.cleanMacAddress(clipText)
                                                            if (foundMac.length == 17) {
                                                                macAddress = foundMac
                                                            }
                                                            if (clipText.contains("http://", ignoreCase = true) || clipText.contains("https://", ignoreCase = true)) {
                                                                macPortalUrl = com.example.data.parser.PlaylistImportExtractor.cleanMacPortalUrl(clipText)
                                                            }
                                                        }
                                                    }
                                                }
                                            } catch (_: Exception) {}
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
                                        shape = RoundedCornerShape(8.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, GoldLight.copy(alpha = 0.6f))
                                    ) {
                                        Text(
                                            text = if (isEnglish) "📋 Paste Portal + MAC from Clipboard" else "📋 Portal-URL & MAC aus Zwischenablage einfügen",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = GoldLight
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = macPortalUrl,
                                onValueChange = { input ->
                                    // If user pastes full block containing multiple portals or URL + MAC into the URL field
                                    val allExtracted = if (input.contains("00:1A:79", ignoreCase = true) || input.contains("\n")) {
                                        com.example.data.parser.PlaylistImportExtractor.extractFromText(input, "MAC")
                                    } else emptyList()
                                    if (allExtracted.size > 1) {
                                        detectedBatchCandidates = allExtracted
                                        val firstMac = allExtracted.firstOrNull { it.type == com.example.data.model.PlaylistType.MAC_PORTAL } ?: allExtracted.first()
                                        macPortalUrl = firstMac.url
                                        macAddress = firstMac.macAddress
                                    } else {
                                        detectedBatchCandidates = emptyList()
                                        val extracted = allExtracted.firstOrNull { it.type == com.example.data.model.PlaylistType.MAC_PORTAL }
                                        if (extracted != null) {
                                            macPortalUrl = extracted.url
                                            macAddress = extracted.macAddress
                                        } else {
                                            macPortalUrl = input
                                        }
                                    }
                                    validationError = null
                                },
                                label = { Text(if (isEnglish) "Portal URL (e.g. http://portal.xyz:8080)" else "Portal-URL (z.B. http://portal.xyz:8080/c/)") },
                                placeholder = { Text("http://line.server.xyz:8080/c/") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("mac_portal_url_input"),
                                singleLine = true,
                                colors = customTextFieldColors()
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = macAddress,
                                onValueChange = { input ->
                                    macAddress = input.uppercase()
                                    validationError = null
                                },
                                label = { Text(if (isEnglish) "MAC Address (00:1A:79:XX:XX:XX)" else "MAC-Adresse (00:1A:79:XX:XX:XX)") },
                                placeholder = { Text("00:1A:79:12:34:56") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("mac_portal_address_input"),
                                singleLine = true,
                                colors = customTextFieldColors()
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(DarkSurfaceElevated)
                                        .border(1.dp, BorderGlow.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                        .clickable {
                                            if (!macAddress.startsWith("00:1A:79:")) {
                                                macAddress = "00:1A:79:"
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "Präfix: 00:1A:79:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = GoldLight
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(DarkSurfaceElevated)
                                        .border(1.dp, BorderGlow.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                        .clickable {
                                            macAddress = com.example.data.parser.PlaylistImportExtractor.cleanMacAddress(macAddress)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = if (isEnglish) "Format MAC" else "MAC formatieren",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AccentCyan
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = performSave,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("submit_mac_portal_tab_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = "Speichern", tint = Color.Black, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (detectedBatchCandidates.size > 1) {
                                        "✅ ${detectedBatchCandidates.size} Portale/Playlisten eintragen (ohne Sync)"
                                    } else {
                                        if (isEnglish) "Connect & Load MAC Portal" else "MAC Portal verbinden & laden"
                                    },
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                        3 -> {
                            // Local File Picker
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.dp, BorderGlow, RoundedCornerShape(10.dp))
                                    .clickable { filePickerLauncher.launch("*/*") },
                                colors = CardDefaults.cardColors(containerColor = DarkSurface)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = "File Manager",
                                        tint = GoldPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = selectedFileName ?: if (isEnglish) "Choose M3U from File Manager" else "M3U aus Dateimanager wählen",
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = if (isEnglish) "Select .m3u or .m3u8 file from storage" else "Wähle .m3u oder .m3u8 Datei auf dem Gerät",
                                            fontSize = 11.sp,
                                            color = TextMuted
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = performSave,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("submit_local_file_tab_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = "Speichern", tint = Color.Black, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isEnglish) "Save & Load Local File" else "Lokale Datei speichern & laden",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (detectedBatchCandidates.size > 1) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "📋 ${detectedBatchCandidates.size} Playlisten / Portale / Konten erkannt – werden nur eingetragen (ohne Synchronisation).",
                            color = AccentCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                // Validation error banner if any
                if (validationError != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentRose),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "⚠️ $validationError",
                            color = AccentRose,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Submit Button - ALWAYS permanently visible at the bottom of the Dialog!
                Button(
                    onClick = performSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("submit_add_playlist_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Speichern",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (detectedBatchCandidates.size > 1) {
                            "✅ ${detectedBatchCandidates.size} Playlisten / Portale eintragen (ohne Sync)"
                        } else when (selectedTypeIndex) {
                            1 -> if (isEnglish) "Save & Load Xtream Playlist" else "Xtream Playliste speichern & laden"
                            2 -> if (isEnglish) "Connect & Load MAC Portal" else "MAC Portal verbinden & laden"
                            3 -> if (isEnglish) "Save & Load Local File" else "Lokale Datei speichern & laden"
                            else -> if (isEnglish) "Save & Load M3U Playlist" else "Playliste speichern & laden"
                        },
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun customTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = DarkSurface,
    unfocusedContainerColor = DarkSurface,
    focusedBorderColor = GoldPrimary,
    unfocusedBorderColor = BorderGlow.copy(alpha = 0.4f),
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedLabelColor = GoldLight,
    unfocusedLabelColor = TextMuted
)
