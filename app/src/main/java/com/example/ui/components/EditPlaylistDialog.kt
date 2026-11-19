package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.platform.testTag
import com.example.data.parser.VpnRequirementHelper
import com.example.ui.theme.ObsidianBlack
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.PlaylistEntity
import com.example.data.model.PlaylistType
import com.example.ui.theme.AccentCyan
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
fun EditPlaylistDialog(
    playlist: PlaylistEntity,
    onDismiss: () -> Unit,
    onSave: (PlaylistEntity) -> Unit,
    isEnglish: Boolean = false
) {
    var name by remember { mutableStateOf(playlist.name) }
    val initialCountryCode = VpnRequirementHelper.extractCountryCode(playlist.name)
    val (detectedNeedsVpn, detectedCountry) = VpnRequirementHelper.detectVpnRequirement(playlist.url, playlist.name)
    var requiresVpn by remember { mutableStateOf(initialCountryCode != null || detectedNeedsVpn) }
    var selectedVpnCountry by remember { mutableStateOf(initialCountryCode ?: detectedCountry) }
    var url by remember { mutableStateOf(playlist.url) }
    var username by remember { mutableStateOf(playlist.username) }
    var password by remember { mutableStateOf(playlist.password) }
    var macAddress by remember { mutableStateOf(playlist.macAddress) }
    var customEpgUrl by remember { mutableStateOf(playlist.customEpgUrl) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, GoldPrimary, RoundedCornerShape(16.dp))
                .testTag("edit_playlist_dialog"),
            colors = CardDefaults.cardColors(containerColor = DarkNavyCard)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = GoldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEnglish) "Edit Playlist" else "Playliste bearbeiten",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldPrimary
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Type Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(DarkSurfaceElevated)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Typ: ${playlist.type.name}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentCyan
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Name field
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (isEnglish) "Playlist Name" else "Playlisten-Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_playlist_name_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = BorderGlow.copy(alpha = 0.5f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
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
                                            "Prefixes country code [DE], [AT], [CH] to name"
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
                                name = name.ifBlank { playlist.name },
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

                // Type specific fields (visible in full because user authenticated with PIN password)
                when (playlist.type) {
                    PlaylistType.M3U_URL -> {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text("M3U / M3U8 URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DarkSurface,
                                unfocusedContainerColor = DarkSurface,
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = BorderGlow.copy(alpha = 0.5f),
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                    }
                    PlaylistType.XTREAM_CODES -> {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text(if (isEnglish) "Server URL (e.g. http://line.xyz:80)" else "Server-Adresse (z.B. http://line.xyz:80)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DarkSurface,
                                unfocusedContainerColor = DarkSurface,
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = BorderGlow.copy(alpha = 0.5f),
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text(if (isEnglish) "Username" else "Benutzername") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DarkSurface,
                                unfocusedContainerColor = DarkSurface,
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = BorderGlow.copy(alpha = 0.5f),
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(if (isEnglish) "Password" else "Passwort") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DarkSurface,
                                unfocusedContainerColor = DarkSurface,
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = BorderGlow.copy(alpha = 0.5f),
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                    }
                    PlaylistType.MAC_PORTAL -> {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text(if (isEnglish) "MAC Portal URL & Port (e.g. http://portal.xyz:8080/c/)" else "MAC Portal URL & Port (z.B. http://portal.xyz:8080/c/)") },
                            supportingText = {
                                Text(
                                    text = if (isEnglish) "Full portal URL including port (e.g. :8080 or /c/)" else "Komplette Portaladresse inkl. Port (z.B. :8080 oder /c/)",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DarkSurface,
                                unfocusedContainerColor = DarkSurface,
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = BorderGlow.copy(alpha = 0.5f),
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = macAddress,
                            onValueChange = { macAddress = it },
                            label = { Text(if (isEnglish) "MAC Address (00:1A:79:...)" else "MAC-Adresse (00:1A:79:...)") },
                            supportingText = {
                                Text(
                                    text = if (isEnglish) "Format: 00:1A:79:XX:XX:XX" else "Format: 00:1A:79:XX:XX:XX",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DarkSurface,
                                unfocusedContainerColor = DarkSurface,
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = BorderGlow.copy(alpha = 0.5f),
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                    }
                    PlaylistType.M3U_FILE -> {
                        Text(
                            text = if (isEnglish) "Local file path: ${playlist.url}" else "Lokaler Dateipfad: ${playlist.url}",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Custom EPG URL (optional)
                OutlinedTextField(
                    value = customEpgUrl,
                    onValueChange = { customEpgUrl = it },
                    label = { Text(if (isEnglish) "Custom EPG XMLTV URL (optional)" else "Eigener EPG XMLTV Link (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = BorderGlow.copy(alpha = 0.5f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = if (isEnglish) "Cancel" else "Abbrechen",
                            color = TextMuted
                        )
                    }

                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                val finalUrl = if (playlist.type == PlaylistType.MAC_PORTAL) {
                                    com.example.data.parser.PlaylistImportExtractor.cleanMacPortalUrl(url)
                                } else {
                                    url.trim()
                                }
                                val finalMac = if (playlist.type == PlaylistType.MAC_PORTAL) {
                                    com.example.data.parser.PlaylistImportExtractor.cleanMacAddress(macAddress)
                                } else {
                                    ""
                                }
                                val formattedName = VpnRequirementHelper.formatPlaylistNameWithVpn(
                                    name = name.trim(),
                                    requiresVpn = requiresVpn,
                                    countryCode = selectedVpnCountry
                                )
                                val updated = playlist.copy(
                                    name = formattedName,
                                    url = finalUrl,
                                    username = if (playlist.type == PlaylistType.MAC_PORTAL) "" else username.trim(),
                                    password = if (playlist.type == PlaylistType.MAC_PORTAL) "" else password.trim(),
                                    macAddress = finalMac,
                                    customEpgUrl = customEpgUrl.trim(),
                                    lastUpdated = System.currentTimeMillis()
                                )
                                onSave(updated)
                                onDismiss()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_edited_playlist_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save",
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isEnglish) "Save & Refresh" else "Speichern",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
