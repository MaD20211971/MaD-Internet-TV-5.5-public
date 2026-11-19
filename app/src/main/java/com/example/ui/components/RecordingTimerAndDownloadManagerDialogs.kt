package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.PlayerEngine
import com.example.data.model.RecordingItem
import com.example.data.model.RecordingStatus
import com.example.data.player.PlayerManager
import com.example.data.storage.DownloadStorageHelper
import com.example.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Digital Recording Timer Dialog ("Digitalanzeige") for Live TV in NexusPlayerView.
 * Allows entering Start Time (HH:mm) and End Time (HH:mm) via digital keypad or +/- controls
 * and confirming with OK.
 */
@Composable
fun RecordingTimerDialog(
    channelName: String,
    isEnglish: Boolean = false,
    onDismiss: () -> Unit,
    onConfirmTimer: (startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) -> Unit
) {
    val nowCal = remember { Calendar.getInstance() }
    val endCal = remember {
        Calendar.getInstance().apply { add(Calendar.MINUTE, 60) }
    }

    var startHour by remember { mutableIntStateOf(nowCal.get(Calendar.HOUR_OF_DAY)) }
    var startMinute by remember { mutableIntStateOf(nowCal.get(Calendar.MINUTE)) }
    var endHour by remember { mutableIntStateOf(endCal.get(Calendar.HOUR_OF_DAY)) }
    var endMinute by remember { mutableIntStateOf(endCal.get(Calendar.MINUTE)) }

    // 0 = editing Start Time, 1 = editing End Time
    var activeField by remember { mutableIntStateOf(0) }
    var digitBuffer by remember { mutableStateOf("") }

    val durationMinutes = remember(startHour, startMinute, endHour, endMinute) {
        val startTotal = startHour * 60 + startMinute
        val endTotal = endHour * 60 + endMinute
        val diff = if (endTotal > startTotal) {
            endTotal - startTotal
        } else {
            (24 * 60 - startTotal) + endTotal
        }
        diff.coerceAtLeast(1)
    }

    val isNextDayEnd = remember(startHour, startMinute, endHour, endMinute) {
        (endHour * 60 + endMinute) <= (startHour * 60 + startMinute)
    }

    fun applyDigitInput(digit: Int) {
        val nextBuf = (digitBuffer + digit.toString()).takeLast(4)
        digitBuffer = nextBuf
        val padded = nextBuf.padStart(4, '0')
        val rawH = padded.substring(0, 2).toIntOrNull() ?: 0
        val rawM = padded.substring(2, 4).toIntOrNull() ?: 0
        val clampedH = rawH.coerceIn(0, 23)
        val clampedM = rawM.coerceIn(0, 59)

        if (activeField == 0) {
            startHour = clampedH
            startMinute = clampedM
        } else {
            endHour = clampedH
            endMinute = clampedM
        }

        if (nextBuf.length == 4 && activeField == 0) {
            activeField = 1
            digitBuffer = ""
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 340.dp, max = 500.dp)
                .fillMaxWidth(0.94f)
                .padding(12.dp)
                .border(1.5.dp, GoldPrimary.copy(alpha = 0.7f), RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            color = DarkNavyCard,
            tonalElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(Color(0xFFEF4444).copy(alpha = 0.2f), CircleShape)
                                .border(1.dp, Color(0xFFEF4444), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = if (isEnglish) "RECORDING TIMER" else "AUFNAHME-TIMER",
                                color = GoldPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = channelName,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Schließen",
                            tint = TextMuted
                        )
                    }
                }

                // Digital LED Display Panel ("Digitalanzeige")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF050811))
                        .border(1.5.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEnglish) "DIGITAL TIMER DISPLAY" else "DIGITALANZEIGE (START & ENDE)",
                            color = AccentCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Downloads/${DownloadStorageHelper.SUBFOLDER} • MP4",
                            color = Color(0xFF22C55E),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Start Time Digital Box
                        DigitalTimeBox(
                            label = if (isEnglish) "START TIME" else "AUFNAHMEBEGINN",
                            hour = startHour,
                            minute = startMinute,
                            isSelected = activeField == 0,
                            accentColor = Color(0xFF22C55E),
                            onSelect = {
                                activeField = 0
                                digitBuffer = ""
                            },
                            onHourDelta = { delta ->
                                startHour = (startHour + delta + 24) % 24
                            },
                            onMinuteDelta = { delta ->
                                startMinute = (startMinute + delta + 60) % 60
                            },
                            modifier = Modifier.weight(1f)
                        )

                        // End Time Digital Box
                        DigitalTimeBox(
                            label = if (isEnglish) "END TIME" else "AUFNAHMEENDE",
                            hour = endHour,
                            minute = endMinute,
                            isSelected = activeField == 1,
                            accentColor = Color(0xFFEF4444),
                            onSelect = {
                                activeField = 1
                                digitBuffer = ""
                            },
                            onHourDelta = { delta ->
                                endHour = (endHour + delta + 24) % 24
                            },
                            onMinuteDelta = { delta ->
                                endMinute = (endMinute + delta + 60) % 60
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Status & Duration Row inside Digital Display
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F172A))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = String.format(
                                Locale.getDefault(),
                                "%02d:%02d Uhr  ➔  %02d:%02d Uhr%s",
                                startHour,
                                startMinute,
                                endHour,
                                endMinute,
                                if (isNextDayEnd) " (+1 Tag)" else ""
                            ),
                            color = GoldLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Dauer: $durationMinutes Min.",
                            color = AccentCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Quick Preset Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val presets = listOf(
                        "Jetzt" to 0,
                        "+30m" to 30,
                        "+60m" to 60,
                        "+90m" to 90,
                        "+120m" to 120
                    )
                    presets.forEach { (title, addMins) ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (addMins == 0) {
                                        val c = Calendar.getInstance()
                                        startHour = c.get(Calendar.HOUR_OF_DAY)
                                        startMinute = c.get(Calendar.MINUTE)
                                    } else {
                                        val totalEnd = startHour * 60 + startMinute + addMins
                                        endHour = (totalEnd / 60) % 24
                                        endMinute = totalEnd % 60
                                    }
                                },
                            color = DarkSurfaceElevated,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = title,
                                color = GoldLight,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                }

                // Digital Numeric Keypad (0-9) for fast remote/touch time entry
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (activeField == 0) {
                            "Direkteingabe für Aufnahmebeginn (z.B. 2015 für 20:15 Uhr):"
                        } else {
                            "Direkteingabe für Aufnahmeende (z.B. 2200 für 22:00 Uhr):"
                        },
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        (0..4).forEach { digit ->
                            DigitalKeypadButton(
                                text = digit.toString(),
                                onClick = { applyDigitInput(digit) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        (5..9).forEach { digit ->
                            DigitalKeypadButton(
                                text = digit.toString(),
                                onClick = { applyDigitInput(digit) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Action Buttons (Abbrechen / OK)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                    ) {
                        Text(
                            text = if (isEnglish) "Cancel" else "Abbrechen",
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = {
                            onConfirmTimer(startHour, startMinute, endHour, endMinute)
                        },
                        modifier = Modifier
                            .weight(1.4f)
                            .height(46.dp)
                            .testTag("confirm_recording_timer_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldPrimary,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "OK",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DigitalTimeBox(
    label: String,
    hour: Int,
    minute: Int,
    isSelected: Boolean,
    accentColor: Color,
    onSelect: () -> Unit,
    onHourDelta: (Int) -> Unit,
    onMinuteDelta: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) accentColor else Color(0xFF334155),
        label = "digitalBorder"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) accentColor.copy(alpha = 0.08f) else Color(0xFF0A0F1D))
            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onSelect() }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) accentColor else TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace
        )

        // + Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SmallStepButton(text = "+ Std", onClick = { onSelect(); onHourDelta(1) })
            SmallStepButton(text = "+ Min", onClick = { onSelect(); onMinuteDelta(1) })
        }

        // Large 7-segment style Digital Time Readout
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
                .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = String.format(Locale.getDefault(), "%02d:%02d", hour, minute),
                color = accentColor,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )
        }

        // - Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SmallStepButton(text = "- Std", onClick = { onSelect(); onHourDelta(-1) })
            SmallStepButton(text = "- Min", onClick = { onSelect(); onMinuteDelta(-1) })
        }
    }
}

@Composable
private fun SmallStepButton(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() },
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            color = TextPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun DigitalKeypadButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * VOD Download Manager & Recording Manager Dialog.
 * Shows all VOD Downloads (Movies & Series) and Live TV Recordings / Timer Recordings
 * stored in Downloads/MaDIPTV in MP4 / VLC-compatible format.
 * Supports up to 99 queued VOD items downloaded strictly sequentially one after another.
 */
@Composable
fun VodDownloadManagerDialog(
    recordings: List<RecordingItem>,
    isEnglish: Boolean = false,
    onDismiss: () -> Unit,
    onPlayOffline: (RecordingItem) -> Unit,
    onCancelDownload: (RecordingItem) -> Unit,
    onDeleteRecording: (RecordingItem) -> Unit,
    onClearQueue: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedFilter by remember { mutableIntStateOf(0) } // 0 = Alle, 1 = Warteschlange & Aktiv, 2 = Offline Verfügbar (MP4)

    val sortedRecordings = remember(recordings) {
        recordings.sortedWith(
            compareBy<RecordingItem> {
                when (it.status) {
                    RecordingStatus.DOWNLOADING -> 0
                    RecordingStatus.RECORDING -> 1
                    RecordingStatus.QUEUED -> 2
                    RecordingStatus.SCHEDULED -> 3
                    RecordingStatus.DOWNLOADED, RecordingStatus.COMPLETED -> 4
                    RecordingStatus.FAILED -> 5
                }
            }.thenBy {
                if (it.status == RecordingStatus.QUEUED) it.startTime else -it.startTime
            }
        )
    }

    val queuedItems = remember(sortedRecordings) {
        sortedRecordings.filter { it.status == RecordingStatus.QUEUED }
    }
    val downloadingCount = remember(sortedRecordings) {
        sortedRecordings.count { it.status == RecordingStatus.DOWNLOADING }
    }
    val vodQueueTotalCount = remember(sortedRecordings) {
        sortedRecordings.count {
            it.status == RecordingStatus.DOWNLOADING || it.status == RecordingStatus.QUEUED
        }
    }

    val activeItems = remember(sortedRecordings) {
        sortedRecordings.filter {
            it.status == RecordingStatus.DOWNLOADING ||
                it.status == RecordingStatus.QUEUED ||
                it.status == RecordingStatus.RECORDING ||
                it.status == RecordingStatus.SCHEDULED
        }
    }
    val completedItems = remember(sortedRecordings) {
        sortedRecordings.filter { it.status == RecordingStatus.DOWNLOADED || it.status == RecordingStatus.COMPLETED }
    }

    val displayedItems = remember(sortedRecordings, activeItems, completedItems, selectedFilter) {
        when (selectedFilter) {
            1 -> activeItems
            2 -> completedItems
            else -> sortedRecordings
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 350.dp, max = 680.dp)
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .padding(10.dp)
                .border(1.5.dp, GoldPrimary.copy(alpha = 0.65f), RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            color = DarkNavyCard,
            tonalElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
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
                                .background(GoldPrimary.copy(alpha = 0.18f), CircleShape)
                                .border(1.dp, GoldPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = if (isEnglish) "VOD DOWNLOAD & RECORDING MANAGER" else "VOD DOWNLOAD- & AUFNAHME-MANAGER",
                                color = GoldPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Speicherort: Downloads/${DownloadStorageHelper.SUBFOLDER} • Max. ${com.example.data.model.MAX_VOD_DOWNLOAD_QUEUE_SIZE} Titel (Einzel-Download)",
                                color = Color(0xFF22C55E),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { DownloadStorageHelper.openDownloadsFolder(context) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldLight)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isEnglish) "Folder" else "Ordner",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
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
                }

                // Sequential Queue Status Banner (1 active at a time, up to 99 entries)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF0A0F1D),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Warteschlange: $vodQueueTotalCount / ${com.example.data.model.MAX_VOD_DOWNLOAD_QUEUE_SIZE} Plätze belegt • $downloadingCount aktiv • ${queuedItems.size} wartend",
                                color = AccentCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Eingetragene Filme & Serien werden einzeln nacheinander heruntergeladen (immer 1 Download gleichzeitig).",
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                        }
                        if (queuedItems.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = onClearQueue,
                                modifier = Modifier.height(30.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                            ) {
                                Text(
                                    text = "Warteschlange leeren (${queuedItems.size})",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Filter Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val tabs = listOf(
                        "Alle (${recordings.size})" to 0,
                        "Warteschlange & Aktiv (${activeItems.size}/${com.example.data.model.MAX_VOD_DOWNLOAD_QUEUE_SIZE})" to 1,
                        "Offline MP4 (${completedItems.size})" to 2
                    )
                    tabs.forEach { (title, idx) ->
                        val isSelected = selectedFilter == idx
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selectedFilter = idx },
                            color = if (isSelected) GoldPrimary else DarkSurfaceElevated,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = title,
                                color = if (isSelected) Color.Black else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                            )
                        }
                    }
                }

                // Content List
                if (displayedItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = if (isEnglish) {
                                    "No downloads or recordings in this view"
                                } else {
                                    "Keine Downloads oder Aufnahmen in dieser Ansicht"
                                },
                                color = TextSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Markiere bis zu 99 Filme & Serien für die Download-Warteschlange oder nutze den Aufnahme-Timer im Live-TV.",
                                color = TextMuted,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(displayedItems, key = { it.id }) { item ->
                            val queueIndex = if (item.status == RecordingStatus.QUEUED) {
                                queuedItems.indexOfFirst { it.id == item.id } + 1
                            } else 0
                            DownloadManagerItemCard(
                                item = item,
                                queuePosition = queueIndex,
                                totalQueued = queuedItems.size,
                                isEnglish = isEnglish,
                                onPlayOffline = {
                                    onDismiss()
                                    onPlayOffline(item)
                                },
                                onOpenInVlc = {
                                    val path = item.filePath
                                    if (!path.isNullOrBlank()) {
                                        PlayerManager.launchExternalPlayer(
                                            context = context,
                                            engine = PlayerEngine.VLC,
                                            streamUrl = path,
                                            title = item.title
                                        )
                                    }
                                },
                                onCancel = { onCancelDownload(item) },
                                onDelete = { onDeleteRecording(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadManagerItemCard(
    item: RecordingItem,
    queuePosition: Int = 0,
    totalQueued: Int = 0,
    isEnglish: Boolean,
    onPlayOffline: () -> Unit,
    onOpenInVlc: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    val statusBadgeColor = when (item.status) {
        RecordingStatus.DOWNLOADED, RecordingStatus.COMPLETED -> Color(0xFF22C55E)
        RecordingStatus.DOWNLOADING -> AccentCyan
        RecordingStatus.QUEUED -> GoldPrimary
        RecordingStatus.RECORDING -> Color(0xFFEF4444)
        RecordingStatus.SCHEDULED -> GoldPrimary
        RecordingStatus.FAILED -> Color(0xFFEF4444)
    }

    val statusLabel = when (item.status) {
        RecordingStatus.DOWNLOADED, RecordingStatus.COMPLETED -> "OFFLINE VERFÜGBAR (MP4)"
        RecordingStatus.DOWNLOADING -> "AKTIVER EINZEL-DOWNLOAD (${item.progressPercent}%)"
        RecordingStatus.QUEUED -> if (queuePosition > 0) "IN WARTESCHLANGE (#$queuePosition von $totalQueued)" else "IN WARTESCHLANGE ⏳"
        RecordingStatus.RECORDING -> "LIVE-AUFNAHME LÄUFT ●"
        RecordingStatus.SCHEDULED -> "TIMER GEPLANT 🕒"
        RecordingStatus.FAILED -> "FEHLGESCHLAGEN"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, statusBadgeColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        shape = RoundedCornerShape(12.dp)
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
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = statusBadgeColor.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = statusLabel,
                                color = statusBadgeColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Text(
                            text = dateFormatter.format(Date(item.startTime)),
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = item.title,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = item.fileSizeFormatted.ifBlank { "Downloads/${DownloadStorageHelper.SUBFOLDER}" },
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            if (item.status == RecordingStatus.DOWNLOADING || item.status == RecordingStatus.RECORDING) {
                LinearProgressIndicator(
                    progress = { (item.progressPercent.coerceIn(0, 100)) / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = statusBadgeColor,
                    trackColor = DarkSurface
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.status == RecordingStatus.DOWNLOADED || item.status == RecordingStatus.COMPLETED) {
                    Button(
                        onClick = onPlayOffline,
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldPrimary,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isEnglish) "Play Offline" else "Offline Abspielen",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = onOpenInVlc,
                        modifier = Modifier.height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "VLC / Player",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (item.status == RecordingStatus.DOWNLOADING ||
                    item.status == RecordingStatus.QUEUED ||
                    item.status == RecordingStatus.RECORDING ||
                    item.status == RecordingStatus.SCHEDULED
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when (item.status) {
                                RecordingStatus.SCHEDULED -> "Timer abbrechen"
                                RecordingStatus.QUEUED -> "Aus Warteschlange entfernen"
                                else -> "Stoppen"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Löschen",
                            tint = Color(0xFFEF4444)
                        )
                    }
                }
            }
        }
    }
}
