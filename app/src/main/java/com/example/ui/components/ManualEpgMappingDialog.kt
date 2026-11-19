package com.example.ui.components

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.ChannelItem
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

@Composable
fun ManualEpgMappingDialog(
    channel: ChannelItem,
    availableEpgIds: List<String>,
    onDismiss: () -> Unit,
    onAssignEpg: (String) -> Unit,
    onClearMapping: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedEpgId by remember {
        mutableStateOf(channel.manualEpgTvgId ?: channel.epgId ?: channel.tvgId ?: "")
    }

    val filteredEpgIds = remember(searchQuery, availableEpgIds) {
        if (searchQuery.isBlank()) {
            availableEpgIds.take(100)
        } else {
            availableEpgIds.filter { it.contains(searchQuery.trim(), ignoreCase = true) }.take(100)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, BorderGlow, RoundedCornerShape(16.dp))
                .testTag("manual_epg_dialog"),
            colors = CardDefaults.cardColors(containerColor = DarkNavyCard)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "EPG-Senderzuordnung",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldLight
                        )
                        Text(
                            text = channel.name,
                            fontSize = 13.sp,
                            color = TextSecondary,
                            maxLines = 1
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Schließen",
                            tint = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Diagnosis Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurfaceElevated)
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val (statusColor, statusIcon) = when (channel.epgMappingStatus) {
                                "MATCHED" -> Pair(AccentEmerald, Icons.Default.CheckCircle)
                                "MANUAL" -> Pair(GoldPrimary, Icons.Default.CheckCircle)
                                "AMBIGUOUS" -> Pair(AccentRose, Icons.Default.Warning)
                                else -> Pair(TextMuted, Icons.Default.Info)
                            }
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Status: ${channel.epgMappingStatus}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Methode: ${channel.epgMappingMethod}",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }

                        if (channel.isManualEpgOverride) {
                            Text(
                                text = "🔒 Manuell zugewiesen: ${channel.manualEpgTvgId}",
                                fontSize = 12.sp,
                                color = GoldPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else if (!channel.epgId.isNullOrBlank()) {
                            Text(
                                text = "Zugewiesene EPG-ID: ${channel.epgId}",
                                fontSize = 12.sp,
                                color = TextPrimary
                            )
                        }

                        if (channel.epgAmbiguousCandidates.isNotBlank()) {
                            Text(
                                text = "Kandidaten: ${channel.epgAmbiguousCandidates.replace(";", ", ")}",
                                fontSize = 11.sp,
                                color = AccentRose
                            )
                        }

                        if (channel.epgUnassignedReason.isNotBlank()) {
                            Text(
                                text = "Hinweis: ${channel.epgUnassignedReason}",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("epg_search_field"),
                    placeholder = { Text("EPG-Kanal suchen...", color = TextMuted, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = DarkSurface,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = GoldPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // List of available EPG channels
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurface)
                ) {
                    items(filteredEpgIds) { id ->
                        val isSelected = (id == selectedEpgId)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedEpgId = id }
                                .background(if (isSelected) GoldPrimary.copy(alpha = 0.2f) else Color.Transparent)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = id,
                                fontSize = 13.sp,
                                color = if (isSelected) GoldLight else TextPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(GoldPrimary)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (channel.isManualEpgOverride) {
                        TextButton(
                            onClick = {
                                onClearMapping()
                                onDismiss()
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = AccentRose)
                        ) {
                            Text("Manuelle Zuordnung lösen", fontSize = 12.sp)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.textButtonColors(contentColor = TextMuted)
                        ) {
                            Text("Abbrechen")
                        }

                        Button(
                            onClick = {
                                if (selectedEpgId.isNotBlank()) {
                                    onAssignEpg(selectedEpgId)
                                }
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GoldPrimary,
                                contentColor = ObsidianBlack
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("apply_epg_mapping_button")
                        ) {
                            Text("Zuweisen", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
