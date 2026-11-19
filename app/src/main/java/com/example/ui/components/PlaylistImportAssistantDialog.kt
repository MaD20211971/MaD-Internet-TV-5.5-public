package com.example.ui.components

import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.PlaylistType
import com.example.data.parser.ImportedPlaylistCandidate
import com.example.data.parser.PlaylistImportExtractor
import com.example.data.parser.VpnRequirementHelper
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentEmerald
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PlaylistImportAssistantDialog(
    onDismiss: () -> Unit,
    onImportConfirmed: (candidates: List<ImportedPlaylistCandidate>, rawM3uText: String?, fileUri: Uri?) -> Unit,
    isEnglish: Boolean = false
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Datei auswählen, 1: Copy & Paste
    var rawInputText by remember { mutableStateOf("") }
    var loadedFileName by remember { mutableStateOf<String?>(null) }
    var rawM3uContentHolder by remember { mutableStateOf<String?>(null) }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }

    val candidates = remember { mutableStateListOf<ImportedPlaylistCandidate>() }
    var isAnalyzing by remember { mutableStateOf(false) }
    var hasAnalyzed by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    fun processTextInBackground(text: String, fileName: String? = null, isDirectM3uContent: Boolean = false) {
        selectedFileUri = null
        if (text.isBlank()) {
            candidates.clear()
            hasAnalyzed = false
            rawM3uContentHolder = null
            return
        }

        isAnalyzing = true
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val defaultPrefix = fileName?.substringBeforeLast(".") ?: "Import"
                val extracted = PlaylistImportExtractor.extractFromText(
                    rawText = text,
                    defaultNamePrefix = defaultPrefix
                )
                withContext(Dispatchers.Main) {
                    candidates.clear()
                    candidates.addAll(extracted)
                    hasAnalyzed = true
                    if (isDirectM3uContent || text.contains("#EXTM3U", ignoreCase = true)) {
                        rawM3uContentHolder = text
                    } else {
                        rawM3uContentHolder = null
                    }
                    isAnalyzing = false
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    isAnalyzing = false
                    hasAnalyzed = true
                }
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isAnalyzing = true
            val fileName = uri.lastPathSegment ?: "playlist.txt"
            loadedFileName = fileName
            selectedFileUri = uri
            rawM3uContentHolder = null
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val defaultPrefix = fileName.substringBeforeLast(".")
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val extracted = if (inputStream != null) {
                        inputStream.use { stream ->
                            PlaylistImportExtractor.extractFromStream(
                                inputStream = stream,
                                defaultNamePrefix = defaultPrefix
                            )
                        }
                    } else emptyList()

                    withContext(Dispatchers.Main) {
                        candidates.clear()
                        candidates.addAll(extracted)
                        hasAnalyzed = true
                        isAnalyzing = false
                    }
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) {
                        isAnalyzing = false
                        hasAnalyzed = true
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
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(20.dp))
                .border(1.5.dp, GoldPrimary, RoundedCornerShape(20.dp))
                .testTag("playlist_import_assistant_dialog"),
            colors = CardDefaults.cardColors(containerColor = DarkNavyCard)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // 1. Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(GoldLight, GoldDark)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🪄", fontSize = 20.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isEnglish) "Batch Playlist Import" else "Massen-Playlist Import",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = GoldPrimary
                            )
                            Text(
                                text = if (isEnglish) "Registers MAC Portals (00:1A:79), Xtream & M3U without sync" else "Trägt MAC Portale, Xtream Konten & M3U Listen ohne Synchronisation ein",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Schließen",
                            tint = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. Tab Selection (Datei vs Copy & Paste)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurface)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val tab1 = if (isEnglish) "📁 Choose File (.txt, .m3u, .cfg)" else "📁 Textdatei auswählen (.txt, .m3u, .cfg)"
                    val tab2 = if (isEnglish) "📋 Copy & Paste Text" else "📋 Text einfügen (Copy & Paste)"

                    listOf(tab1, tab2).forEachIndexed { index, label ->
                        val isSelected = selectedTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) GoldPrimary else Color.Transparent)
                                .clickable { selectedTab = index }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) ObsidianBlack else TextSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3. Tab Content
                when (selectedTab) {
                    0 -> {
                        // File picker view
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, BorderGlow, RoundedCornerShape(12.dp))
                                .clickable { filePickerLauncher.launch("*/*") },
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(GoldPrimary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = "File Picker",
                                        tint = GoldPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = loadedFileName ?: if (isEnglish) "Tap to choose file (.txt, .m3u, .m3u8, .cfg)" else "Textdatei auf dem Gerät auswählen (.txt, .m3u, .cfg)",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (loadedFileName != null) AccentCyan else TextPrimary
                                    )
                                    Text(
                                        text = if (isAnalyzing) "Analysiere Datei im Hintergrund..." else if (loadedFileName != null) "Datei geladen – ${candidates.size} Playlisten extrahiert" else "Liest auch riesige Listen mit hunderten Accounts oder M3U Playlisten",
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }
                                Button(
                                    onClick = { filePickerLauncher.launch("*/*") },
                                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                                ) {
                                    Text(
                                        text = if (loadedFileName != null) "Datei wechseln" else "Durchsuchen",
                                        color = ObsidianBlack,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                    1 -> {
                        // Copy & Paste View
                        Column(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = rawInputText,
                                onValueChange = {
                                    rawInputText = it
                                    processTextInBackground(it)
                                },
                                label = { Text(if (isEnglish) "Paste MAC Portals, Xtream accounts or M3U links..." else "MAC Portale (URL + 00:1A:79:...), Xtream oder M3U hier einfügen...") },
                                placeholder = {
                                    Text(
                                        "Portal: http://server.com:8080/c/\nMAC: 00:1A:79:12:34:56\n\noder Xtream Host / User / Pass / M3U Link...",
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(115.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GoldPrimary,
                                    unfocusedBorderColor = BorderGlow,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedLabelColor = GoldLight,
                                    unfocusedLabelColor = TextMuted,
                                    focusedContainerColor = DarkSurface,
                                    unfocusedContainerColor = DarkSurface
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            try {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                if (clipboard != null && clipboard.hasPrimaryClip() && (clipboard.primaryClip?.itemCount ?: 0) > 0) {
                                                    val item = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                                                    if (!item.isNullOrBlank()) {
                                                        rawInputText = item
                                                        processTextInBackground(item)
                                                    }
                                                }
                                            } catch (e: Throwable) {
                                                e.printStackTrace()
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste", modifier = Modifier.size(14.dp), tint = GoldLight)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Zwischenablage einfügen", fontSize = 11.sp, color = TextPrimary)
                                    }

                                    if (rawInputText.isNotBlank()) {
                                        IconButton(
                                            onClick = {
                                                rawInputText = ""
                                                candidates.clear()
                                                hasAnalyzed = false
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear", tint = TextMuted)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 4. Extraction Results Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isAnalyzing) "🔍 Analysiere Playlisten..." else if (candidates.isNotEmpty()) "Erkannte Playlisten (${candidates.size})" else if (hasAnalyzed) "Keine gültigen Playlisten gefunden" else "Warte auf Datei oder Text...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (candidates.isNotEmpty()) GoldPrimary else TextSecondary
                        )
                        if (isAnalyzing) {
                            Spacer(modifier = Modifier.width(8.dp))
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = GoldPrimary, strokeWidth = 2.dp)
                        }
                    }

                    if (candidates.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val allSelected = candidates.all { it.isSelected }
                            Text(
                                text = if (allSelected) "Alle abwählen" else "Alle auswählen",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AccentCyan,
                                modifier = Modifier
                                    .clickable {
                                        val newState = !allSelected
                                        candidates.forEachIndexed { i, c ->
                                            candidates[i] = c.copy(isSelected = newState)
                                        }
                                    }
                                    .padding(2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 5. Candidates List (O(1) Index-Map für 1.000+ Playlisten ohne Scroll-Ruckler)
                val candidateIndexMap = remember(candidates.size) {
                    val map = HashMap<String, Int>(candidates.size * 2)
                    for (i in 0 until candidates.size) {
                        map[candidates[i].id] = i
                    }
                    map
                }
                val displayedCandidates = remember(candidates.size, searchQuery) {
                    if (searchQuery.isBlank()) {
                        candidates
                    } else {
                        candidates.filter {
                            it.name.contains(searchQuery, ignoreCase = true) ||
                                it.url.contains(searchQuery, ignoreCase = true) ||
                                it.details.contains(searchQuery, ignoreCase = true)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurface)
                        .border(1.dp, BorderGlow.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(8.dp)
                ) {
                    if (candidates.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Icon(
                                    imageVector = if (isAnalyzing) Icons.Default.Search else Icons.Default.Info,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (isAnalyzing) "Datei wird durchsucht..." else if (hasAnalyzed) "Im Text wurden keine MAC Portal-, Xtream- oder M3U-Zugangsdaten erkannt." else "Wähle eine Textdatei (.txt/.m3u) oder füge MAC Portal / Xtream Zugangsdaten ein.",
                                    fontSize = 12.sp,
                                    color = TextMuted,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(
                                items = displayedCandidates,
                                key = { _, item -> item.id },
                                contentType = { _, _ -> "candidate_card" }
                            ) { index, candidate ->
                                val origIndex = candidateIndexMap[candidate.id] ?: index
                                CandidateItemCard(
                                    index = index,
                                    candidate = candidate,
                                    onSelectionChanged = { isSel ->
                                        if (origIndex in 0 until candidates.size) {
                                            candidates[origIndex] = candidate.copy(isSelected = isSel)
                                        }
                                    },
                                    onNameChanged = { newName ->
                                        if (origIndex in 0 until candidates.size) {
                                            candidate.name = newName
                                            candidates[origIndex] = candidate.copy(name = newName)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 6. Action Footer (Import into database)
                val selectedCount = candidates.count { it.isSelected }
                Button(
                    onClick = {
                        if (selectedCount > 0) {
                            onImportConfirmed(candidates.toList(), rawM3uContentHolder, selectedFileUri)
                            onDismiss()
                        }
                    },
                    enabled = selectedCount > 0 && !isAnalyzing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("confirm_import_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldPrimary,
                        disabledContainerColor = DarkSurfaceElevated
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (selectedCount > 0) ObsidianBlack else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (selectedCount > 0) "✅ $selectedCount Playliste(n) / Portale eintragen (ohne Sync)" else "Mindestens 1 Playliste auswählen",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedCount > 0) ObsidianBlack else TextMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CandidateItemCard(
    index: Int,
    candidate: ImportedPlaylistCandidate,
    onSelectionChanged: (Boolean) -> Unit,
    onNameChanged: (String) -> Unit
) {
    val (typeColor, typeLabel) = when (candidate.type) {
        PlaylistType.XTREAM_CODES -> Pair(AccentEmerald, "XTREAM CODES")
        PlaylistType.MAC_PORTAL -> Pair(GoldPrimary, "MAC PORTAL")
        PlaylistType.M3U_URL -> Pair(AccentCyan, "M3U WEB-URL")
        PlaylistType.M3U_FILE -> Pair(Color(0xFFB388FF), "M3U DATEI")
    }

    val formattedName = remember(candidate.name, candidate.type) {
        com.example.data.model.formatPlaylistDisplayName(candidate.name, candidate.type)
    }
    val vpnBadgeCode = remember(candidate.url, candidate.name, candidate.requiresVpn, candidate.vpnCountry) {
        val (isVpn, detectedCountry) = VpnRequirementHelper.detectVpnRequirement(candidate.url, candidate.name)
        if (candidate.requiresVpn || isVpn) {
            VpnRequirementHelper.extractCountryCode(candidate.name)
                ?: if (candidate.vpnCountry.isNotBlank()) candidate.vpnCountry else detectedCountry
        } else null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(
                1.dp,
                if (candidate.isSelected) typeColor.copy(alpha = 0.7f) else BorderGlow.copy(alpha = 0.3f),
                RoundedCornerShape(10.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (candidate.isSelected) DarkNavyCard else DarkSurfaceElevated
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = candidate.isSelected,
                onCheckedChange = { onSelectionChanged(it) },
                colors = CheckboxDefaults.colors(
                    checkedColor = GoldPrimary,
                    checkmarkColor = ObsidianBlack,
                    uncheckedColor = TextMuted
                )
            )

            Spacer(modifier = Modifier.width(4.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(GoldPrimary.copy(alpha = 0.2f))
                            .border(0.5.dp, GoldPrimary, RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "#${index + 1}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldPrimary
                        )
                    }

                    if (vpnBadgeCode != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(GoldPrimary)
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "🔒 $vpnBadgeCode",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = ObsidianBlack
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = formattedName,
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
                            .background(typeColor.copy(alpha = 0.2f))
                            .border(0.5.dp, typeColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = typeLabel,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = typeColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                // Standardized 1-line detail info with clean shortened URL
                val cleanUrl = com.example.data.model.formatShortDisplayUrl(candidate.url, 26)
                val detailText = when (candidate.type) {
                    PlaylistType.XTREAM_CODES -> "Server: $cleanUrl | User: ${candidate.username}"
                    PlaylistType.MAC_PORTAL -> "Portal: $cleanUrl | MAC: ${candidate.macAddress}"
                    PlaylistType.M3U_URL -> cleanUrl
                    PlaylistType.M3U_FILE -> candidate.details
                }
                Text(
                    text = detailText,
                    fontSize = 11.sp,
                    color = if (candidate.type == PlaylistType.MAC_PORTAL) AccentCyan else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
