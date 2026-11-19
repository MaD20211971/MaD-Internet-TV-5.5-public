package com.example.ui.components.epg

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.entity.EpgSourceEntity
import com.example.data.database.entity.ManualEpgMappingEntity
import com.example.data.model.ChannelItem
import com.example.ui.theme.DarkNavyCard
import com.example.ui.theme.GoldLight
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun EpgSourceManagementDialog(
    sources: List<EpgSourceEntity>,
    onAddSource: (String, String, Int) -> Unit,
    onToggleSource: (String, Boolean) -> Unit,
    onDeleteSource: (String) -> Unit,
    onSyncSource: (String) -> Unit,
    onCleanupEpg: (Int) -> Unit,
    onDismiss: () -> Unit,
    isEnglish: Boolean = false
) {
    var newName by remember { mutableStateOf("") }
    var newUrl by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEnglish) "EPG Sources Management" else "EPG-Quellen verwalten",
                fontWeight = FontWeight.Bold,
                color = GoldLight
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(sources) { src ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkNavyCard, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(src.name, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text(src.url.take(35) + "...", fontSize = 11.sp, color = TextSecondary)
                            }
                            IconButton(onClick = { onSyncSource(src.id) }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Sync", tint = GoldLight)
                            }
                            IconButton(onClick = { onDeleteSource(src.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(if (isEnglish) "Source Name" else "Quellen-Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newUrl,
                    onValueChange = { newUrl = it },
                    label = { Text(if (isEnglish) "XMLTV URL" else "XMLTV URL (.xml / .xml.gz)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = {
                        if (newName.isNotBlank() && newUrl.isNotBlank()) {
                            onAddSource(newName, newUrl, 7)
                            newName = ""
                            newUrl = ""
                        }
                    },
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isEnglish) "Add Source" else "Quelle hinzufügen")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isEnglish) "Done" else "Fertig", color = GoldLight)
            }
        },
        containerColor = DarkNavyCard
    )
}

@Composable
fun ManualEpgMappingDialog(
    channel: ChannelItem,
    currentMapping: ManualEpgMappingEntity?,
    onSaveMapping: (String, String) -> Unit,
    onRemoveMapping: () -> Unit,
    onDismiss: () -> Unit,
    isEnglish: Boolean = false
) {
    var epgIdInput by remember { mutableStateOf(currentMapping?.epgId ?: channel.tvgId ?: "") }
    var mappedNameInput by remember { mutableStateOf(currentMapping?.mappedName ?: channel.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEnglish) "Manual EPG Mapping" else "Manuelle EPG-Zuordnung",
                fontWeight = FontWeight.Bold,
                color = GoldLight
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${channel.name} (${channel.groupTitle})",
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                OutlinedTextField(
                    value = epgIdInput,
                    onValueChange = { epgIdInput = it },
                    label = { Text("EPG Channel ID (tvg-id)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = mappedNameInput,
                    onValueChange = { mappedNameInput = it },
                    label = { Text(if (isEnglish) "Mapped Name" else "Zugeordneter Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveMapping(epgIdInput.trim(), mappedNameInput.trim())
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
            ) {
                Text(if (isEnglish) "Save" else "Speichern")
            }
        },
        dismissButton = {
            if (currentMapping != null) {
                TextButton(onClick = {
                    onRemoveMapping()
                    onDismiss()
                }) {
                    Text(if (isEnglish) "Remove" else "Löschen", color = MaterialTheme.colorScheme.error)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(if (isEnglish) "Cancel" else "Abbrechen", color = TextSecondary)
                }
            }
        },
        containerColor = DarkNavyCard
    )
}
