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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.PlaylistEntity
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentRose
import com.example.ui.theme.BorderGlow
import com.example.ui.theme.DarkNavyBackground
import com.example.ui.theme.DarkNavyCard
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.GoldLight
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Dialog shown upon long-pressing any playlist in the TopBar or dropdown menu.
 * Offers:
 * - 🗑️ Delete playlist (NO PIN REQUIRED!)
 * - ✏️ Edit playlist (PIN 1971 protected)
 * - 🔄 Refresh playlist channels
 * - ▶️ Select as active playlist
 */
@Composable
fun PlaylistActionsDialog(
    playlist: PlaylistEntity,
    isActive: Boolean,
    onDismiss: () -> Unit,
    onDeletePlaylist: (PlaylistEntity) -> Unit,
    onEditPlaylist: (PlaylistEntity) -> Unit,
    onRefreshPlaylist: (PlaylistEntity) -> Unit,
    onSelectPlaylist: (String) -> Unit,
    isEnglish: Boolean = false
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, GoldPrimary, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = DarkNavyCard)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(GoldPrimary.copy(alpha = 0.15f))
                                .border(1.dp, GoldPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isEnglish) "Playlist Options" else "Playlisten-Optionen",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = GoldLight
                            )
                            Text(
                                text = playlist.getDisplayName(),
                                fontSize = 13.sp,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Schließen",
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (showDeleteConfirm) {
                    // Confirmation View for Delete (Explicitly stating: No PIN required)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, AccentRose.copy(alpha = 0.7f), RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = AccentRose,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (isEnglish) "Delete Playlist?" else "Playliste wirklich löschen?",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = AccentRose
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (isEnglish)
                                    "Are you sure you want to delete '${playlist.getDisplayName()}' and all associated channels? (No PIN required)"
                                else
                                    "Möchtest du '${playlist.getDisplayName()}' und alle zugehörigen Sender wirklich löschen? (Kein PIN erforderlich)",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showDeleteConfirm = false },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(if (isEnglish) "Cancel" else "Abbrechen", color = TextMuted)
                                }

                                Button(
                                    onClick = {
                                        onDismiss()
                                        onDeletePlaylist(playlist)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("confirm_delete_playlist_btn"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AccentRose,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isEnglish) "Delete" else "Löschen",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Playlist Details Badge
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderGlow.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(DarkNavyBackground)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = playlist.type.name,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = GoldLight
                                        )
                                    }
                                    if (isActive) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(AccentEmerald.copy(alpha = 0.2f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (isEnglish) "ACTIVE" else "AKTIV",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AccentEmerald
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${playlist.liveCount} Live TV • ${playlist.movieCount} Filme • ${playlist.seriesCount} Serien",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 1. DELETE BUTTON (NO PIN REQUIRED)
                    PlaylistActionItem(
                        icon = Icons.Default.Delete,
                        iconTint = AccentRose,
                        title = if (isEnglish) "Delete Playlist" else "Playliste löschen",
                        subtitle = if (isEnglish) "Permanently remove playlist (No PIN required)" else "Playliste dauerhaft entfernen (Kein PIN erforderlich)",
                        onClick = { showDeleteConfirm = true },
                        testTag = "playlist_action_delete"
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 2. EDIT BUTTON (PIN 1971 REQUIRED)
                    PlaylistActionItem(
                        icon = Icons.Default.Edit,
                        iconTint = GoldLight,
                        title = if (isEnglish) "Edit Playlist" else "Playliste bearbeiten",
                        subtitle = if (isEnglish) "Change server, credentials or name (PIN protected)" else "Server, Zugangsdaten oder Name anpassen (PIN-geschützt)",
                        trailingIcon = Icons.Default.Lock,
                        onClick = {
                            onDismiss()
                            onEditPlaylist(playlist)
                        },
                        testTag = "playlist_action_edit"
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 3. REFRESH BUTTON
                    PlaylistActionItem(
                        icon = Icons.Default.Refresh,
                        iconTint = AccentCyan,
                        title = if (isEnglish) "Refresh Playlist" else "Inhalte neu synchronisieren",
                        subtitle = if (isEnglish) "Reload channels, VODs & series" else "Senderliste & Medien neu laden",
                        onClick = {
                            onDismiss()
                            onRefreshPlaylist(playlist)
                        },
                        testTag = "playlist_action_refresh"
                    )

                    if (!isActive) {
                        Spacer(modifier = Modifier.height(8.dp))

                        // 4. SET ACTIVE BUTTON
                        PlaylistActionItem(
                            icon = Icons.Default.PlayArrow,
                            iconTint = AccentEmerald,
                            title = if (isEnglish) "Set as Active Playlist" else "Als aktive Playliste setzen",
                            subtitle = if (isEnglish) "Switch channel view to this playlist" else "Sender & Kategorien dieser Liste laden",
                            onClick = {
                                onDismiss()
                                onSelectPlaylist(playlist.id)
                            },
                            testTag = "playlist_action_set_active"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistActionItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    trailingIcon: ImageVector? = null,
    onClick: () -> Unit,
    testTag: String = ""
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, BorderGlow.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
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
                        .background(iconTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = subtitle,
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
            }

            if (trailingIcon != null) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
