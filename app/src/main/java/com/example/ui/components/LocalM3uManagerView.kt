package com.example.ui.components

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PlaylistEntity
import com.example.data.model.PlaylistType
import com.example.data.storage.DiscoveredLocalM3uFile
import com.example.data.storage.LocalM3uDuplicateStatus
import com.example.data.storage.LocalM3uScanResult
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
import java.util.Locale

@Composable
fun LocalM3uManagerView(
    playlists: List<PlaylistEntity>,
    activePlaylistId: String?,
    scanResult: LocalM3uScanResult?,
    isScanning: Boolean,
    isImporting: Boolean,
    importProgressText: String,
    onScanFolderUri: (Uri) -> Unit,
    onScanDocumentUris: (List<Uri>) -> Unit,
    onScanStandardFolders: () -> Unit,
    onRescanLastFolder: () -> Unit,
    onToggleFileSelection: (String) -> Unit,
    onSelectAllFiles: (Boolean, Boolean) -> Unit,
    onImportFilesBatch: (List<DiscoveredLocalM3uFile>, Boolean) -> Unit,
    onActivatePlaylist: (String) -> Unit,
    onActivateAndOpenLiveTv: (String) -> Unit,
    onRefreshPlaylist: (PlaylistEntity) -> Unit,
    onRenamePlaylist: (PlaylistEntity, String) -> Unit,
    onDeletePlaylist: (PlaylistEntity) -> Unit,
    onRemoveDuplicates: () -> Unit,
    isEnglish: Boolean = false,
    modifier: Modifier = Modifier
) {
    val localPlaylists = remember(playlists) {
        playlists.filter { it.type == PlaylistType.M3U_FILE }
    }

    var skipDuplicatesAutomatically by remember { mutableStateOf(true) }
    var searchFilter by remember { mutableStateOf("") }
    var playlistToRename by remember { mutableStateOf<PlaylistEntity?>(null) }
    var renameInput by remember { mutableStateOf("") }

    // SAF Folder Picker (Dateimanager Ordner-Auswahl)
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            onScanFolderUri(treeUri)
        }
    }

    // SAF Multi-File Picker (Dateimanager Einzel-/Mehrfachdatei-Auswahl)
    val multipleFilesPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            onScanDocumentUris(uris)
        }
    }

    val filteredLocalPlaylists = remember(localPlaylists, searchFilter) {
        if (searchFilter.isBlank()) {
            localPlaylists
        } else {
            val q = searchFilter.trim().lowercase(Locale.ROOT)
            localPlaylists.filter { it.name.lowercase(Locale.ROOT).contains(q) }
        }
    }

    val discoveredFiles = scanResult?.files ?: emptyList()
    val filteredDiscoveredFiles = remember(discoveredFiles, searchFilter) {
        if (searchFilter.isBlank()) {
            discoveredFiles
        } else {
            val q = searchFilter.trim().lowercase(Locale.ROOT)
            discoveredFiles.filter { it.fileName.lowercase(Locale.ROOT).contains(q) }
        }
    }

    val selectedDiscoveredFiles = remember(discoveredFiles) {
        discoveredFiles.filter { it.isSelected }
    }
    val newDiscoveredFiles = remember(discoveredFiles) {
        discoveredFiles.filter { it.duplicateStatus == LocalM3uDuplicateStatus.NEW }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // =========================================================================
        // 1. HEADER & DATEIMANAGER-AKTIONEN (Ordner wählen / Dateien wählen / Scan)
        // =========================================================================
        Surface(
            color = DarkNavyCard,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.55f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(GoldPrimary.copy(alpha = 0.18f))
                                .border(1.dp, GoldPrimary, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Local M3U",
                                tint = GoldPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "LOCAL M3U • EASY MANAGEMENT",
                                color = GoldPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = if (isEnglish) {
                                    "Select a folder via file manager, add all or selected M3U files with duplicate check"
                                } else {
                                    "Ordner über Dateimanager wählen • Alle oder einzelne M3Us hinzufügen • 100% Duplikat-Prüfung"
                                },
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Active Count Pill
                    Surface(
                        color = DarkSurfaceElevated,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "${localPlaylists.size} Local M3U(s)",
                            color = AccentEmerald,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                // Primary File Manager Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Choose Folder via File Manager (OpenDocumentTree)
                    Button(
                        onClick = {
                            try {
                                folderPickerLauncher.launch(null)
                            } catch (_: ActivityNotFoundException) {
                                // Fallback if Android TV firmware lacks DocumentsUI tree activity
                                try {
                                    multipleFilesPickerLauncher.launch(arrayOf("*/*"))
                                } catch (_: Exception) {
                                    onScanStandardFolders()
                                }
                            }
                        },
                        enabled = !isScanning && !isImporting,
                        modifier = Modifier
                            .weight(1.2f)
                            .height(44.dp)
                            .testTag("local_m3u_select_folder_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldPrimary,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isEnglish) "Select Folder (File Manager)" else "Ordner wählen (Dateimanager)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // 2. Choose Individual/Multiple M3U Files via File Manager
                    OutlinedButton(
                        onClick = {
                            try {
                                multipleFilesPickerLauncher.launch(arrayOf("*/*"))
                            } catch (_: Exception) {
                                onScanStandardFolders()
                            }
                        },
                        enabled = !isScanning && !isImporting,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("local_m3u_select_files_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
                        border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.7f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isEnglish) "Pick M3U Files" else "Einzelne M3Us wählen",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // 3. Quick Scan Standard Folders (Downloads / Documents)
                    OutlinedButton(
                        onClick = onScanStandardFolders,
                        enabled = !isScanning && !isImporting,
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("local_m3u_scan_downloads_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldLight),
                        border = BorderStroke(1.dp, BorderGlow),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Downloads scannen",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Search & Duplicate Protection Filter Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchFilter,
                        onValueChange = { searchFilter = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("local_m3u_search_input"),
                        placeholder = {
                            Text(
                                text = if (isEnglish) "Filter M3U filename..." else "M3U-Dateinamen suchen / filtern...",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchFilter.isNotBlank()) {
                                IconButton(onClick = { searchFilter = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = BorderGlow,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = DarkSurfaceElevated,
                            unfocusedContainerColor = DarkSurface
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    FilterChip(
                        selected = skipDuplicatesAutomatically,
                        onClick = { skipDuplicatesAutomatically = !skipDuplicatesAutomatically },
                        label = {
                            Text(
                                text = if (skipDuplicatesAutomatically) {
                                    "🛡️ Duplikat-Schutz AN"
                                } else {
                                    "⚠️ Duplikate überschreiben"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentEmerald,
                            selectedLabelColor = Color.Black,
                            containerColor = DarkSurfaceElevated,
                            labelColor = GoldLight
                        )
                    )

                    if (localPlaylists.size > 1) {
                        OutlinedButton(
                            onClick = onRemoveDuplicates,
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("local_m3u_deduplicate_button"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldLight),
                            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Text(
                                text = "Doppelte bereinigen",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Progress banner during folder scan or batch import
        if (isScanning || isImporting) {
            Surface(
                color = DarkSurfaceElevated,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AccentCyan),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        color = AccentCyan,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (isImporting && importProgressText.isNotBlank()) {
                            importProgressText
                        } else if (isScanning) {
                            "Scanne Ordner nach M3U-Dateien und prüfe auf doppelte Dateien..."
                        } else {
                            "Verarbeite M3U-Dateien..."
                        },
                        color = AccentCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Main Scrollable Content:
        // Section A: Scanned Folder M3U Files (if a folder/file scan was performed)
        // Section B: Saved Local M3U Playlists (Easy Management)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("local_m3u_lazy_column"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // =====================================================================
            // SECTION A: SCANNED FOLDER M3U FILES (WITH BATCH & SINGLE IMPORT)
            // =====================================================================
            if (scanResult != null) {
                item(key = "folder_scan_header") {
                    Surface(
                        color = DarkSurfaceElevated,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.65f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "📂 ORDNER-SCAN: ${scanResult.folderName}",
                                        color = AccentCyan,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = "${scanResult.files.size} M3U-Datei(en) gefunden • ${scanResult.newCount} NEU • ${scanResult.duplicateCount} DOPPELT erkannt",
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }

                                OutlinedButton(
                                    onClick = onRescanLastFolder,
                                    enabled = !isScanning && !isImporting,
                                    modifier = Modifier.height(32.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldLight),
                                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                                    contentPadding = PaddingValues(horizontal = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "Neu prüfen", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (scanResult.files.isNotEmpty()) {
                                // Batch Action Buttons: Add All New / Add Selected / Select All / Deselect All
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 1. Add ALL files from folder (respecting duplicate check)
                                    Button(
                                        onClick = {
                                            val targetList = if (skipDuplicatesAutomatically) {
                                                newDiscoveredFiles
                                            } else {
                                                discoveredFiles
                                            }
                                            onImportFilesBatch(targetList, skipDuplicatesAutomatically)
                                        },
                                        enabled = !isImporting && (if (skipDuplicatesAutomatically) newDiscoveredFiles.isNotEmpty() else discoveredFiles.isNotEmpty()),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                            .testTag("local_m3u_add_all_folder_button"),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = AccentEmerald,
                                            contentColor = Color.Black
                                        ),
                                        contentPadding = PaddingValues(horizontal = 10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        val countToAdd = if (skipDuplicatesAutomatically) newDiscoveredFiles.size else discoveredFiles.size
                                        Text(
                                            text = "Alle im Ordner hinzufügen ($countToAdd)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // 2. Add SELECTED files from folder
                                    Button(
                                        onClick = {
                                            onImportFilesBatch(selectedDiscoveredFiles, skipDuplicatesAutomatically)
                                        },
                                        enabled = !isImporting && selectedDiscoveredFiles.isNotEmpty(),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                            .testTag("local_m3u_add_selected_button"),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = GoldPrimary,
                                            contentColor = Color.Black
                                        ),
                                        contentPadding = PaddingValues(horizontal = 10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = "Ausgewählte hinzufügen (${selectedDiscoveredFiles.size})",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // 3. Select all new
                                    OutlinedButton(
                                        onClick = { onSelectAllFiles(true, skipDuplicatesAutomatically) },
                                        modifier = Modifier.height(38.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
                                        border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f)),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Text(text = "☑ Alle neuen", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // 4. Deselect all
                                    OutlinedButton(
                                        onClick = { onSelectAllFiles(false, false) },
                                        modifier = Modifier.height(38.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                                        border = BorderStroke(1.dp, BorderGlow),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Text(text = "☐ Keine", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                if (filteredDiscoveredFiles.isEmpty()) {
                    item(key = "folder_scan_empty") {
                        Surface(
                            color = DarkNavyCard,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, BorderGlow),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = scanResult.errorMessage
                                    ?: "Keine M3U-Dateien im ausgewählten Ordner gefunden.",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                } else {
                    items(
                        items = filteredDiscoveredFiles,
                        key = { "scan_${it.id}" }
                    ) { fileItem ->
                        DiscoveredLocalM3uFileRow(
                            item = fileItem,
                            isImporting = isImporting,
                            onToggleSelect = { onToggleFileSelection(fileItem.id) },
                            onImportSingle = {
                                onImportFilesBatch(listOf(fileItem), false)
                            }
                        )
                    }
                }
            }

            // =====================================================================
            // SECTION B: SAVED LOCAL M3U PLAYLISTS (EASY MANAGEMENT)
            // =====================================================================
            item(key = "saved_local_m3u_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "GESPEICHERTE LOCAL M3U PLAYLISTS (${filteredLocalPlaylists.size})",
                        color = GoldLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Exakte Anzeige nach M3U-Dateinamen",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
            }

            if (filteredLocalPlaylists.isEmpty()) {
                item(key = "saved_local_m3u_empty") {
                    Surface(
                        color = DarkNavyCard,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BorderGlow),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = GoldPrimary.copy(alpha = 0.7f),
                                modifier = Modifier.size(42.dp)
                            )
                            Text(
                                text = if (isEnglish) {
                                    "No local M3U playlists added yet"
                                } else {
                                    "Noch keine lokalen M3U-Playlisten hinzugefügt"
                                },
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isEnglish) {
                                    "Click 'Select Folder (File Manager)' above to pick a folder containing your .m3u / .m3u8 files."
                                } else {
                                    "Klicke oben auf „Ordner wählen (Dateimanager)“, um einen Ordner mit M3U-Dateien auszuwählen und alle oder einzelne M3Us hinzuzufügen."
                                },
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            } else {
                items(
                    items = filteredLocalPlaylists,
                    key = { "saved_${it.id}" }
                ) { playlist ->
                    val isActive = playlist.id == activePlaylistId || playlist.isActive
                    SavedLocalM3uPlaylistCard(
                        playlist = playlist,
                        isActive = isActive,
                        onActivate = { onActivatePlaylist(playlist.id) },
                        onPlayLiveTv = { onActivateAndOpenLiveTv(playlist.id) },
                        onRefresh = { onRefreshPlaylist(playlist) },
                        onRename = {
                            renameInput = playlist.name
                            playlistToRename = playlist
                        },
                        onDelete = { onDeletePlaylist(playlist) }
                    )
                }
            }
        }
    }

    // Rename Dialog
    playlistToRename?.let { target ->
        AlertDialog(
            onDismissRequest = { playlistToRename = null },
            title = {
                Text(
                    text = "Local M3U umbenennen",
                    color = GoldPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Anzeigename der lokalen M3U-Playliste:",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = BorderGlow,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            onRenamePlaylist(target, renameInput.trim())
                        }
                        playlistToRename = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldPrimary,
                        contentColor = Color.Black
                    )
                ) {
                    Text("Speichern", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { playlistToRename = null }) {
                    Text("Abbrechen", color = TextSecondary)
                }
            },
            containerColor = DarkNavyCard,
            shape = RoundedCornerShape(14.dp)
        )
    }
}

@Composable
private fun DiscoveredLocalM3uFileRow(
    item: DiscoveredLocalM3uFile,
    isImporting: Boolean,
    onToggleSelect: () -> Unit,
    onImportSingle: () -> Unit
) {
    val isDuplicate = item.duplicateStatus != LocalM3uDuplicateStatus.NEW
    val borderColor = when {
        item.isSelected -> AccentEmerald
        isDuplicate -> AccentRose.copy(alpha = 0.55f)
        else -> BorderGlow
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = if (item.isSelected) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onToggleSelect() }
            .testTag("discovered_m3u_row_${item.fileName}"),
        colors = CardDefaults.cardColors(containerColor = DarkNavyCard),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Selection Checkbox
            Surface(
                color = if (item.isSelected) AccentEmerald else DarkSurfaceElevated,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(
                    1.dp,
                    if (item.isSelected) Color.Black else GoldPrimary.copy(alpha = 0.6f)
                ),
                modifier = Modifier.clickable { onToggleSelect() }
            ) {
                Text(
                    text = if (item.isSelected) "☑" else "☐",
                    color = if (item.isSelected) Color.Black else GoldLight,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // Exact M3U Filename & Details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = item.fileName,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Status Badge: NEU vs. DOPPELT
                    Surface(
                        color = if (!isDuplicate) {
                            AccentEmerald.copy(alpha = 0.2f)
                        } else {
                            AccentRose.copy(alpha = 0.2f)
                        },
                        shape = RoundedCornerShape(5.dp),
                        border = BorderStroke(
                            1.dp,
                            if (!isDuplicate) AccentEmerald else AccentRose
                        )
                    ) {
                        Text(
                            text = when (item.duplicateStatus) {
                                LocalM3uDuplicateStatus.NEW -> "✓ NEU"
                                LocalM3uDuplicateStatus.DUPLICATE_IN_APP -> "⚠️ DOPPELT (Bereits in App)"
                                LocalM3uDuplicateStatus.DUPLICATE_IN_FOLDER -> "⚠️ DOPPELT IM ORDNER"
                            },
                            color = if (!isDuplicate) AccentEmerald else AccentRose,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                val kbSize = (item.sizeBytes / 1024L).coerceAtLeast(1L)
                val folderPrefix = if (item.relativeFolder.isNotBlank()) "Ordner: ${item.relativeFolder} • " else ""
                Text(
                    text = "${folderPrefix}~${item.estimatedChannelCount} Sender/Streams • ${kbSize} KB",
                    color = TextSecondary,
                    fontSize = 11.sp
                )

                if (!item.duplicateReason.isNullOrBlank()) {
                    Text(
                        text = item.duplicateReason,
                        color = AccentRose,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Single File Import / Overwrite Button
            OutlinedButton(
                onClick = onImportSingle,
                enabled = !isImporting,
                modifier = Modifier.height(34.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (!isDuplicate) AccentEmerald else GoldLight
                ),
                border = BorderStroke(
                    1.dp,
                    if (!isDuplicate) AccentEmerald.copy(alpha = 0.7f) else GoldPrimary.copy(alpha = 0.5f)
                ),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (!isDuplicate) "Hinzufügen" else "Erneut laden",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SavedLocalM3uPlaylistCard(
    playlist: PlaylistEntity,
    isActive: Boolean,
    onActivate: () -> Unit,
    onPlayLiveTv: () -> Unit,
    onRefresh: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isActive) 2.dp else 1.dp,
                color = if (isActive) GoldPrimary else BorderGlow,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onActivate() }
            .testTag("saved_local_m3u_card_${playlist.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) DarkSurfaceElevated else DarkNavyCard
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Exact M3U Name & Channel Stats
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Exact M3U Filename Display ("die M3Us werden so angezeigt wie die m3u Namen lauten")
                    Text(
                        text = playlist.name,
                        color = if (isActive) GoldPrimary else TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (isActive) {
                        Surface(
                            color = AccentEmerald,
                            shape = RoundedCornerShape(5.dp)
                        ) {
                            Text(
                                text = "AKTIV",
                                color = Color.Black,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                val totalEntries = playlist.liveCount + playlist.movieCount + playlist.seriesCount + playlist.radioCount
                Text(
                    text = "Gesamt: $totalEntries • Live TV: ${playlist.liveCount} • Filme: ${playlist.movieCount} • Serien: ${playlist.seriesCount}",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            // Right: Easy Management Action Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play / Open in Live TV
                Button(
                    onClick = onPlayLiveTv,
                    modifier = Modifier.height(34.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isActive) AccentEmerald else GoldPrimary,
                        contentColor = Color.Black
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Abspielen",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isActive) "TV öffnen" else "Starten",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                // Refresh / Re-parse local M3U file
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .size(34.dp)
                        .background(DarkSurface, CircleShape)
                        .border(1.dp, AccentCyan.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Neu einlesen",
                        tint = AccentCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Rename local M3U
                IconButton(
                    onClick = onRename,
                    modifier = Modifier
                        .size(34.dp)
                        .background(DarkSurface, CircleShape)
                        .border(1.dp, GoldPrimary.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Umbenennen",
                        tint = GoldPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Delete local M3U
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(34.dp)
                        .background(DarkSurface, CircleShape)
                        .border(1.dp, AccentRose.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Löschen",
                        tint = AccentRose,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
