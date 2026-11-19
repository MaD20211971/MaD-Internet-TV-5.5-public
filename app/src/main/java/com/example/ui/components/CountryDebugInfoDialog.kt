package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.country.ClassificationStatus
import com.example.data.country.CountryConstants
import com.example.data.country.CountryResolutionResult
import com.example.data.model.ChannelItem
import com.example.ui.theme.*

/**
 * Inspection & Manual Correction Dialog for Country Classification.
 * Displays title, detected country, confidence, classification status, full evidence trail,
 * and allows immediate one-click manual overrides with persistent station learning.
 */
@Composable
fun CountryDebugInfoDialog(
    channel: ChannelItem,
    resolutionResult: CountryResolutionResult,
    onDismissRequest: () -> Unit,
    onApplyManualOverride: (channelId: String, normalizedStationId: String, newCountry: String) -> Unit,
    onRemoveManualOverride: (channelId: String, normalizedStationId: String) -> Unit
) {
    var selectedOverride by remember {
        mutableStateOf(if (channel.manualCountryOverride) channel.countryCode else null)
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(20.dp))
                .border(1.5.dp, BorderGlow.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .testTag("country_debug_dialog"),
            color = DarkCardBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(GoldPrimary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Country Debug",
                                tint = GoldLight,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Country Classification Inspector",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Provider-unabhängige Diagnose & Manuelle Zuweisung",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.testTag("btn_close_country_debug")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Schließen",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Item Title & IDs
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = DarkSurface,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "SENDER / CONTENT TITEL",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextMuted
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = channel.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text(
                                        text = "Gruppe: ${channel.groupTitle.ifBlank { "(Keine)" }}",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                    Text(
                                        text = "Station-ID: ${resolutionResult.normalizedChannelId}",
                                        fontSize = 11.sp,
                                        color = GoldLight,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }

                    // Classification Summary Card
                    item {
                        val statusColor = when (resolutionResult.status) {
                            ClassificationStatus.VERIFIED -> AccentEmerald
                            ClassificationStatus.PROBABLE -> AccentCyan
                            ClassificationStatus.UNKNOWN -> AccentAmber
                            ClassificationStatus.CONFLICT -> AccentRose
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                            color = DarkSurface,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "ERKANNTES LAND",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextMuted
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val uiInfo = CountryConstants.getUiInfo(resolutionResult.countryCode)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = uiInfo.flagEmoji, fontSize = 22.sp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "${uiInfo.displayName} (${resolutionResult.countryCode})",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Black,
                                                color = TextPrimary
                                            )
                                        }
                                    }

                                    // Status Badge
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(statusColor.copy(alpha = 0.15f))
                                            .border(1.dp, statusColor.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = resolutionResult.status.name,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = statusColor
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Confidence Bar
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "Konfidenz (Confidence)", fontSize = 11.sp, color = TextSecondary)
                                    Text(
                                        text = "${resolutionResult.confidence}%",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = statusColor
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { resolutionResult.confidence / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = statusColor,
                                    trackColor = DarkCardBackground
                                )

                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Entscheidungsgrund: ${resolutionResult.reason}",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    // Evidence Trail List
                    item {
                        Text(
                            text = "ERFASSTE BEWEISE & METADATEN (EVIDENCE TRAIL)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted
                        )
                    }

                    if (resolutionResult.evidence.isEmpty()) {
                        item {
                            Text(
                                text = "Keine Signale gefunden (Klassifizierung verbleibt strikt auf UNKNOWN).",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                    } else {
                        items(resolutionResult.evidence) { ev ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = DarkSurface.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${ev.source} ➔ ${ev.detectedCountry}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = GoldLight,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = ev.description,
                                            fontSize = 10.sp,
                                            color = TextSecondary
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(AccentCyan.copy(alpha = 0.15f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "+${ev.weight} Pkt",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AccentCyan
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Manual Override Section
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "MANUELLE KORREKTUR (HÖCHSTE PRIORITÄT)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldLight
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Korrigiere die Zuweisung. Die Entscheidung wird persistent gespeichert und für zukünftige Scans dieses Senders gelernt.",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        val manualOptions = listOf(
                            Triple("DE", "Deutschland", "🇩🇪"),
                            Triple("AT", "Österreich", "🇦🇹"),
                            Triple("CH", "Schweiz", "🇨🇭"),
                            Triple("OTHER", "Andere", "🌍"),
                            Triple("UNKNOWN", "Unbekannt", "❓")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            manualOptions.forEach { (code, name, flag) ->
                                val isSelected = selectedOverride == code
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) GoldPrimary else DarkSurface)
                                        .border(
                                            1.dp,
                                            if (isSelected) GoldPrimary else BorderGlow.copy(alpha = 0.3f),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            selectedOverride = code
                                            onApplyManualOverride(
                                                channel.id,
                                                resolutionResult.normalizedChannelId,
                                                code
                                            )
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = flag, fontSize = 18.sp)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = code,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.Black else TextPrimary
                                        )
                                    }
                                }
                            }
                        }

                        if (channel.manualCountryOverride || selectedOverride != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = {
                                    selectedOverride = null
                                    onRemoveManualOverride(channel.id, resolutionResult.normalizedChannelId)
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text(
                                    text = "Manuelle Zuweisung zurücksetzen (Auto-Modus)",
                                    fontSize = 11.sp,
                                    color = AccentRose
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Close button
                Button(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                ) {
                    Text(text = "Schließen", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
