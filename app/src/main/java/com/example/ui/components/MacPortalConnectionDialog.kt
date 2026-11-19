package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.MacPortalConnectionDialogState
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentEmerald
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
fun MacPortalConnectionDialog(
    state: MacPortalConnectionDialogState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    isEnglish: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            state.isTimeoutOrRejected -> AccentRose
            state.isFinished -> GoldPrimary
            else -> GoldPrimary.copy(alpha = 0.6f)
        },
        animationSpec = tween(400),
        label = "borderColor"
    )

    Dialog(
        onDismissRequest = {
            if (!state.isLoading) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !state.isLoading,
            dismissOnClickOutside = !state.isLoading,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .border(1.5.dp, borderColor, RoundedCornerShape(22.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkNavyCard)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Bar with Icon & Dismiss (if not loading)
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
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            state.isTimeoutOrRejected -> AccentRose.copy(alpha = 0.15f)
                                            state.isFinished -> AccentEmerald.copy(alpha = 0.15f)
                                            else -> GoldPrimary.copy(alpha = 0.15f)
                                        }
                                    )
                                    .border(
                                        1.dp,
                                        when {
                                            state.isTimeoutOrRejected -> AccentRose.copy(alpha = 0.6f)
                                            state.isFinished -> AccentEmerald.copy(alpha = 0.6f)
                                            else -> GoldPrimary.copy(alpha = 0.5f)
                                        },
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    state.isTimeoutOrRejected -> {
                                        Icon(
                                            imageVector = Icons.Default.ErrorOutline,
                                            contentDescription = "Timeout",
                                            tint = AccentRose,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    state.isFinished -> {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Fertig",
                                            tint = AccentEmerald,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    else -> {
                                        Icon(
                                            imageVector = Icons.Default.Router,
                                            contentDescription = "MAC Portal",
                                            tint = GoldPrimary,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .rotate(rotation)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = when {
                                        state.isTimeoutOrRejected -> if (isEnglish) "Portal Timeout (15s)" else "Portal-Abweisung / Timeout (15s)"
                                        state.isFinished -> if (isEnglish) "Playlist Import Finished!" else "Playlist fertig geladen!"
                                        else -> if (isEnglish) "Connecting to MAC Portal..." else "Verbinde mit MAC Portal..."
                                    },
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        state.isTimeoutOrRejected -> AccentRose
                                        state.isFinished -> GoldLight
                                        else -> GoldPrimary
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = state.playlistName.ifBlank { "MAC Portal" },
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("mac_portal_connection_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = if (isEnglish) "Close" else "Schließen",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Step Status Banner
                    Surface(
                        color = DarkSurface,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (state.isTimeoutOrRejected) AccentRose.copy(alpha = 0.35f) else BorderGlow.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = state.stepTitle,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = if (state.isTimeoutOrRejected) AccentRose else GoldPrimary
                                )

                                if (state.isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = GoldPrimary,
                                        strokeWidth = 2.dp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = state.stepDetail,
                                fontSize = 12.sp,
                                color = if (state.isTimeoutOrRejected) Color(0xFFFFB4A9) else TextSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Real-Time Counter Grid (Counting Live TV channels, Movies, Series)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Live TV Card (Animated Counter)
                        CounterStatCard(
                            title = if (isEnglish) "Live TV" else "TV-Kanäle",
                            count = state.liveCount,
                            icon = Icons.Default.Tv,
                            color = GoldPrimary,
                            modifier = Modifier.weight(1f)
                        )

                        // Movies Card
                        CounterStatCard(
                            title = if (isEnglish) "Movies" else "VOD Filme",
                            count = state.movieCount,
                            icon = Icons.Default.Movie,
                            color = AccentCyan,
                            modifier = Modifier.weight(1f)
                        )

                        // Series Card
                        CounterStatCard(
                            title = if (isEnglish) "Series" else "Serien",
                            count = state.seriesCount,
                            icon = Icons.Default.VideoLibrary,
                            color = AccentEmerald,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Active Progress indicator
                    if (state.isLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = GoldPrimary,
                            trackColor = DarkSurfaceElevated
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isEnglish)
                                "Receiving and organizing channels... Please wait"
                            else
                                "Playliste füllt sich... TV-Kanäle werden empfangen und sortiert",
                            fontSize = 11.sp,
                            color = TextMuted,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Timeout or Rejection Guidance
                    if (state.isTimeoutOrRejected) {
                        Surface(
                            color = AccentRose.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentRose.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = if (isEnglish) "Diagnostic hints:" else "Lösungs-Hinweise:",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = AccentRose
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isEnglish)
                                        "• 15s Timeout: Check MAC address activation\n• Verify Portal URL\n• German VPN might be required by provider"
                                    else
                                        "• 15s Timeout erreicht: Server antwortet nicht oder Abweisung\n• MAC-Adresse beim Anbieter freigeschaltet?\n• Portal-URL prüfen (z.B. mit /c/ Endung)\n• Ggf. VPN (Deutschland) aktivieren",
                                    fontSize = 11.sp,
                                    color = TextPrimary,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (state.isTimeoutOrRejected) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(if (isEnglish) "Close" else "Schließen", color = TextSecondary, fontSize = 13.sp)
                            }

                            Button(
                                onClick = onRetry,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isEnglish) "Retry" else "Wiederholen", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        } else if (state.isFinished) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(if (isEnglish) "Close" else "Schließen", color = TextSecondary, fontSize = 13.sp)
                            }

                            Button(
                                onClick = {
                                    if (state.playlistId.isNotBlank()) {
                                        onOpenPlaylist(state.playlistId)
                                    }
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .weight(1.3f)
                                    .testTag("open_mac_playlist_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isEnglish) "Open Playlist" else "Fertig / Öffnen", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CounterStatCard(
    title: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = count.toString(),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (count > 0) TextPrimary else TextMuted
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                fontSize = 10.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
