package com.example.domain.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.DarkNavyCard
import com.example.ui.theme.GoldLight
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.flow.StateFlow

data class PlaybackStatus(
    val isFallbackActive: Boolean = false,
    val availableStreams: List<String> = emptyList(),
    val currentStreamIndex: Int = 0
)

interface PlayerController {
    val playbackStatus: StateFlow<PlaybackStatus>
    fun seekBackward(ms: Long = 10000L)
    fun seekForward(ms: Long = 10000L)
    fun togglePlay()
    fun resume()
    fun pause()
    fun selectStreamManually(index: Int)
}

@Composable
fun StreamSelectionDialog(
    status: PlaybackStatus,
    onSelectStream: (Int) -> Unit,
    onDismiss: () -> Unit,
    isEnglish: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEnglish) "Available Stream Sources" else "Verfügbare Stream-Quellen",
                color = GoldLight,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(status.availableStreams) { index, streamUrl ->
                    val isSelected = index == status.currentStreamIndex
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onSelectStream(index)
                                onDismiss()
                            }
                            .border(
                                1.dp,
                                if (isSelected) AccentEmerald else GoldPrimary.copy(alpha = 0.3f),
                                RoundedCornerShape(8.dp)
                            ),
                        color = if (isSelected) AccentEmerald.copy(alpha = 0.15f) else DarkNavyCard
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = if (isSelected) AccentEmerald else GoldLight,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (index == 0) "Hauptstream (Primary)" else "Backup Stream $index",
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = streamUrl.take(45) + if (streamUrl.length > 45) "..." else "",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isEnglish) "Close" else "Schließen", color = GoldLight)
            }
        },
        containerColor = DarkNavyCard
    )
}
