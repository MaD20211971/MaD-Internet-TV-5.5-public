package com.example.cast.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cast.UniversalCastManager
import com.example.cast.model.CastConnectionStatus
import com.example.cast.model.CastDevice
import com.example.cast.model.CastMediaItem
import com.example.cast.model.CastProtocol
import com.example.cast.model.CastUiState
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
 * Einheitliches Universal-Cast-Steuerdialog-Sheet für beide Player (NexusPlayerView & TvExoPlayerView).
 * Unterstützt Google Cast, DLNA, Roku, AirPlay, Kodi, Web-Cast (inkl. QR-Code) & Miracast.
 */
@Composable
fun CastControlSheet(
    currentMedia: CastMediaItem,
    onDismiss: () -> Unit,
    onCastStartedPauseLocal: () -> Unit,
    isEnglish: Boolean = false
) {
    val context = LocalContext.current
    val castState by UniversalCastManager.uiState.collectAsStateWithLifecycle()

    var showDevicePickerOverride by remember { mutableStateOf(false) }
    var showManualAddForm by remember { mutableStateOf(false) }
    var showWebCastQrPanel by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        UniversalCastManager.startDiscovery(context)
    }

    DisposableEffect(Unit) {
        onDispose {
            UniversalCastManager.stopDiscovery()
        }
    }

    // Sobald eine Verbindung erfolgreich steht, lokale Wiedergabe automatisch pausieren (Option 2A)
    LaunchedEffect(castState.isConnected) {
        if (castState.isConnected) {
            onCastStartedPauseLocal()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 640.dp)
                .testTag("universal_cast_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = DarkNavyCard,
            border = BorderStroke(1.2.dp, GoldPrimary.copy(alpha = 0.65f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
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
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (castState.isConnected) AccentCyan.copy(alpha = 0.18f)
                                    else GoldPrimary.copy(alpha = 0.16f)
                                )
                                .border(
                                    1.dp,
                                    if (castState.isConnected) AccentCyan else GoldPrimary.copy(alpha = 0.55f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (castState.isConnected) Icons.Default.CastConnected else Icons.Default.Cast,
                                contentDescription = "Cast",
                                tint = if (castState.isConnected) AccentCyan else GoldPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (castState.isConnected && !showDevicePickerOverride) {
                                    if (isEnglish) "Casting Active" else "Übertragung aktiv"
                                } else {
                                    if (isEnglish) "Cast to Device" else "Auf Gerät übertragen"
                                },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentMedia.title.ifBlank { "MaD IPTV public" },
                                fontSize = 11.5.sp,
                                color = GoldLight,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (!castState.isConnected || showDevicePickerOverride) {
                        IconButton(
                            onClick = { UniversalCastManager.startDiscovery(context) },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("cast_rescan_button")
                        ) {
                            if (castState.isScanning) {
                                CircularProgressIndicator(
                                    color = GoldPrimary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = if (isEnglish) "Rescan" else "Neu suchen",
                                    tint = GoldLight
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("cast_close_dialog_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = if (isEnglish) "Close" else "Schließen",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Warnung bei fehlendem WLAN
                if (!castState.isWifiConnected) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(AccentRose.copy(alpha = 0.14f))
                            .border(1.dp, AccentRose.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = AccentRose,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEnglish) {
                                "No Wi-Fi/LAN detected. Connect to the same network as your TV."
                            } else {
                                "Kein WLAN/LAN erkannt. Verbinde dich mit demselben Netzwerk wie dein TV."
                            },
                            fontSize = 11.5.sp,
                            color = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Fehlermeldung falls vorhanden
                castState.errorMessage?.let { err ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(AccentRose.copy(alpha = 0.14f))
                            .border(1.dp, AccentRose.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = err,
                            fontSize = 11.5.sp,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { UniversalCastManager.clearError() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "OK", tint = AccentRose, modifier = Modifier.size(15.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Aktive Cast-Fernbedienung (Vorschlag C) ODER Geräte-Auswahl
                if (castState.isConnected && !showDevicePickerOverride) {
                    ConnectedRemoteControlPanel(
                        castState = castState,
                        context = context,
                        isEnglish = isEnglish,
                        onSwitchDevice = {
                            showDevicePickerOverride = true
                            UniversalCastManager.startDiscovery(context)
                        },
                        onDisconnect = {
                            UniversalCastManager.stopAndDisconnect()
                            showDevicePickerOverride = false
                        }
                    )
                } else if (showManualAddForm) {
                    ManualDeviceAddPanel(
                        isEnglish = isEnglish,
                        onCancel = { showManualAddForm = false },
                        onSave = { name, protocol, host, port, user, pass ->
                            UniversalCastManager.addManualDevice(
                                context = context,
                                name = name,
                                protocol = protocol,
                                host = host,
                                port = port,
                                username = user,
                                password = pass
                            )
                            showManualAddForm = false
                        }
                    )
                } else {
                    DeviceSelectionContent(
                        castState = castState,
                        currentMedia = currentMedia,
                        context = context,
                        showWebCastQrPanel = showWebCastQrPanel,
                        onToggleWebCastQr = {
                            val url = UniversalCastManager.startWebCast(context, currentMedia)
                            if (url != null) {
                                showWebCastQrPanel = true
                                onCastStartedPauseLocal()
                            }
                        },
                        onSelectDevice = { device ->
                            showDevicePickerOverride = false
                            onCastStartedPauseLocal()
                            UniversalCastManager.castToDevice(context, device, currentMedia)
                        },
                        onDeleteManualDevice = { device ->
                            UniversalCastManager.deleteManualDevice(context, device.id)
                        },
                        onLaunchMiracast = {
                            UniversalCastManager.launchMiracast(context)
                        },
                        onOpenManualAdd = {
                            showManualAddForm = true
                        },
                        isEnglish = isEnglish
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectedRemoteControlPanel(
    castState: CastUiState,
    context: Context,
    isEnglish: Boolean,
    onSwitchDevice: () -> Unit,
    onDisconnect: () -> Unit
) {
    val device = castState.activeDevice ?: return
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Zielgerät-Karte
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DarkSurface)
                .border(1.dp, AccentCyan.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Tv,
                contentDescription = null,
                tint = AccentCyan,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${device.protocol.displayName} • ${castState.activeMedia?.title.orEmpty()}",
                    fontSize = 11.5.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            ProtocolBadge(protocol = device.protocol)
        }

        // Web-Cast QR-Code direkt sichtbar wenn Web-Cast aktiv ist
        if (device.protocol == CastProtocol.WEB_CAST && !castState.webCastUrl.isNullOrBlank()) {
            WebCastQrCard(url = castState.webCastUrl, context = context, isEnglish = isEnglish)
        }

        // Medien-Steuerung (Play/Pause, Mute, Stop)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { UniversalCastManager.togglePlayPause() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = GoldPrimary,
                    contentColor = ObsidianBlack
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .testTag("cast_remote_play_pause")
            ) {
                Icon(
                    imageVector = if (castState.isRemotePlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (castState.isRemotePlaying) "Pause" else "Wiedergabe"
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (castState.isRemotePlaying) "Pause" else if (isEnglish) "Play" else "Abspielen",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                )
            }

            OutlinedButton(
                onClick = { UniversalCastManager.toggleMute() },
                border = BorderStroke(1.dp, if (castState.isRemoteMuted) AccentRose else BorderGlow),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(46.dp)
                    .testTag("cast_remote_mute")
            ) {
                Icon(
                    imageVector = if (castState.isRemoteMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Stumm",
                    tint = if (castState.isRemoteMuted) AccentRose else GoldLight
                )
            }
        }

        // Lautstärke-Regler
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurface)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEnglish) "Remote Volume" else "TV-Lautstärke",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )
                Text(
                    text = "${(castState.remoteVolume * 100).toInt()} %",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = GoldPrimary
                )
            }
            Slider(
                value = castState.remoteVolume,
                onValueChange = { UniversalCastManager.setVolume(it) },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = GoldPrimary,
                    activeTrackColor = AccentCyan,
                    inactiveTrackColor = DarkSurfaceElevated
                ),
                modifier = Modifier.testTag("cast_remote_volume_slider")
            )
        }

        // Gerät wechseln & Trennen
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onSwitchDevice,
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("cast_switch_device_btn")
            ) {
                Icon(Icons.Default.Devices, contentDescription = null, tint = GoldLight, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isEnglish) "Switch" else "Wechseln",
                    color = GoldLight,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = onDisconnect,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentRose,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1.2f)
                    .height(44.dp)
                    .testTag("cast_disconnect_btn")
            ) {
                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isEnglish) "Stop & Disconnect" else "Stopp & Trennen",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun DeviceSelectionContent(
    castState: CastUiState,
    currentMedia: CastMediaItem,
    context: Context,
    showWebCastQrPanel: Boolean,
    onToggleWebCastQr: () -> Unit,
    onSelectDevice: (CastDevice) -> Unit,
    onDeleteManualDevice: (CastDevice) -> Unit,
    onLaunchMiracast: () -> Unit,
    onOpenManualAdd: () -> Unit,
    isEnglish: Boolean
) {
    val devices = castState.allDevices

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 6.dp)
    ) {
        // IPTV Kompatibilitäts-Proxy Schalter (Vorschlag A)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface)
                    .border(1.dp, BorderGlow.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isEnglish) "IPTV Compatibility Proxy (Recommended)" else "IPTV-Kompatibilitäts-Proxy (Empfohlen)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldLight
                    )
                    Text(
                        text = if (isEnglish) {
                            "Forwards headers & CORS locally for Chromecast, DLNA, Roku & Web"
                        } else {
                            "Reicht Header & CORS lokal für Chromecast, DLNA, Roku & Browser durch"
                        },
                        fontSize = 10.5.sp,
                        color = TextSecondary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = castState.useRelayProxy,
                    onCheckedChange = { UniversalCastManager.setUseRelayProxy(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ObsidianBlack,
                        checkedTrackColor = AccentEmerald
                    ),
                    modifier = Modifier.testTag("cast_proxy_switch")
                )
            }
        }

        // Verbindungs-Status bei laufendem Connect
        if (castState.connectionStatus == CastConnectionStatus.CONNECTING) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(AccentCyan.copy(alpha = 0.12f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = AccentCyan,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = castState.statusMessage ?: "Verbinde...",
                        fontSize = 12.sp,
                        color = AccentCyan,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Sektion: Gefundene & gespeicherte Netzwerk-Geräte
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEnglish) {
                        "Network Devices (Google Cast, DLNA, Roku, AirPlay, Kodi)"
                    } else {
                        "Netzwerk-Geräte (Google Cast, DLNA, Roku, AirPlay, Kodi)"
                    },
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                if (castState.isScanning) {
                    Text(
                        text = if (isEnglish) "Scanning..." else "Suche...",
                        fontSize = 11.sp,
                        color = GoldPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (devices.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurface)
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (castState.isScanning) {
                            if (isEnglish) "Searching for Chromecast, DLNA TVs, Roku, AirPlay & Kodi in Wi-Fi..."
                            else "Suche nach Chromecast, DLNA-TVs, Roku, AirPlay & Kodi im WLAN..."
                        } else {
                            if (isEnglish) "No network devices found yet. Use Web-Cast, Miracast or add IP manually."
                            else "Noch keine Netzwerk-Geräte gefunden. Nutze Web-Cast, Miracast oder manuelle IP."
                        },
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
            }
        } else {
            items(devices, key = { it.id }) { device ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurface)
                        .border(1.dp, BorderGlow.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .clickable { onSelectDevice(device) }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("cast_device_item_${device.id}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = protocolColor(device.protocol),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = device.name,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (device.modelName.isNotBlank()) {
                            Text(
                                text = device.modelName,
                                fontSize = 10.5.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    ProtocolBadge(protocol = device.protocol)
                    if (device.isManual) {
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { onDeleteManualDevice(device) },
                            modifier = Modifier.size(32.dp)
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

        item {
            HorizontalDivider(
                color = BorderGlow.copy(alpha = 0.3f),
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Text(
                text = if (isEnglish) "Universal Cast & Screen Mirroring" else "Universelles Casting & Bildschirmspiegelung",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary
            )
        }

        // Web-Cast (Browser / Smart TV) Karte + QR-Code
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface)
                    .border(1.dp, AccentCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .clickable { onToggleWebCastQr() }
                    .padding(12.dp)
                    .testTag("cast_web_cast_tile"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Web-Cast (Smart TV / PC / Browser)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (isEnglish) {
                                "Opens local receiver with QR code & automatic channel switching"
                            } else {
                                "Lokaler Empfänger mit QR-Code & automatischem Senderwechsel"
                            },
                            fontSize = 10.5.sp,
                            color = TextSecondary
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.QrCode2,
                        contentDescription = "QR-Code",
                        tint = GoldPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                if (showWebCastQrPanel && !castState.webCastUrl.isNullOrBlank()) {
                    WebCastQrCard(url = castState.webCastUrl, context = context, isEnglish = isEnglish)
                }
            }
        }

        // Miracast / Wi-Fi Display / Smart View Karte
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface)
                    .border(1.dp, GoldPrimary.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .clickable { onLaunchMiracast() }
                    .padding(12.dp)
                    .testTag("cast_miracast_tile"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ScreenShare,
                    contentDescription = null,
                    tint = GoldPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Miracast / Wireless Display / Smart View",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (isEnglish) {
                            "Opens Android system wireless display settings"
                        } else {
                            "Öffnet die Android-Systemeinstellungen für kabellose Displays"
                        },
                        fontSize = 10.5.sp,
                        color = TextSecondary
                    )
                }
                ProtocolBadge(protocol = CastProtocol.MIRACAST)
            }
        }

        // Manuelles Gerät (IP / Kodi / Roku / DLNA / AirPlay)
        item {
            OutlinedButton(
                onClick = onOpenManualAdd,
                border = BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.65f)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("cast_add_manual_device_btn")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isEnglish) {
                        "Add Device Manually (IP / Kodi / Roku / DLNA / AirPlay)"
                    } else {
                        "Gerät manuell hinzufügen (IP / Kodi / Roku / DLNA / AirPlay)"
                    },
                    color = AccentEmerald,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun WebCastQrCard(
    url: String,
    context: Context,
    isEnglish: Boolean
) {
    val qrBitmap = remember(url) { QrCodeGenerator.generateBitmap(url, 320) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkNavyBackground)
            .border(1.dp, GoldPrimary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (isEnglish) {
                "Open this address in any Smart TV, PC or phone browser:"
            } else {
                "Öffne diese Adresse im Browser auf TV, PC, Tablet oder Konsole:"
            },
            fontSize = 11.sp,
            color = TextSecondary
        )
        if (qrBitmap != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .padding(6.dp)
            ) {
                Image(
                    bitmap = qrBitmap,
                    contentDescription = "Web-Cast QR-Code",
                    modifier = Modifier.size(148.dp)
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurfaceElevated)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = url,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = AccentCyan,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    cm?.setPrimaryClip(ClipData.newPlainText("Web-Cast URL", url))
                    Toast.makeText(
                        context,
                        if (isEnglish) "Web-Cast URL copied" else "Web-Cast Adresse kopiert",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Kopieren",
                    tint = GoldLight,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ManualDeviceAddPanel(
    isEnglish: Boolean,
    onCancel: () -> Unit,
    onSave: (name: String, protocol: CastProtocol, host: String, port: Int, username: String, password: String) -> Unit
) {
    val supportedManual = listOf(
        CastProtocol.KODI,
        CastProtocol.ROKU,
        CastProtocol.DLNA,
        CastProtocol.AIRPLAY
    )
    var selectedProtocol by remember { mutableStateOf(CastProtocol.KODI) }
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var portText by remember { mutableStateOf(CastProtocol.KODI.defaultPort.toString()) }
    var username by remember { mutableStateOf("kodi") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = if (isEnglish) "Add Cast Device Manually" else "Cast-Gerät manuell anlegen",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = GoldPrimary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            supportedManual.forEach { proto ->
                val isSelected = proto == selectedProtocol
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) GoldPrimary else DarkSurface)
                        .border(1.dp, if (isSelected) GoldLight else BorderGlow.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .clickable {
                            selectedProtocol = proto
                            portText = proto.defaultPort.toString()
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = proto.shortBadge,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) ObsidianBlack else TextPrimary
                    )
                }
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(if (isEnglish) "Name (e.g. Living Room Kodi)" else "Name (z.B. Wohnzimmer Kodi)", fontSize = 12.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoldPrimary,
                unfocusedBorderColor = BorderGlow,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("IP-Adresse (z.B. 192.168.178.40)", fontSize = 11.5.sp) },
                singleLine = true,
                modifier = Modifier.weight(1.4f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GoldPrimary,
                    unfocusedBorderColor = BorderGlow,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            OutlinedTextField(
                value = portText,
                onValueChange = { portText = it.filter { ch -> ch.isDigit() } },
                label = { Text("Port", fontSize = 11.5.sp) },
                singleLine = true,
                modifier = Modifier.weight(0.6f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GoldPrimary,
                    unfocusedBorderColor = BorderGlow,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
        }

        if (selectedProtocol == CastProtocol.KODI) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Kodi Benutzer", fontSize = 11.5.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = BorderGlow,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Passwort (opt.)", fontSize = 11.5.sp) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = BorderGlow,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                border = BorderStroke(1.dp, BorderGlow),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                Text(if (isEnglish) "Cancel" else "Abbrechen", color = TextSecondary, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    val cleanHost = host.trim()
                    if (cleanHost.isNotBlank()) {
                        onSave(
                            name,
                            selectedProtocol,
                            cleanHost,
                            portText.toIntOrNull() ?: selectedProtocol.defaultPort,
                            if (selectedProtocol == CastProtocol.KODI) username else "",
                            if (selectedProtocol == CastProtocol.KODI) password else ""
                        )
                    }
                },
                enabled = host.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentEmerald,
                    contentColor = ObsidianBlack
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1.2f)
                    .height(44.dp)
                    .testTag("cast_manual_save_btn")
            ) {
                Text(if (isEnglish) "Save Device" else "Speichern", fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun ProtocolBadge(protocol: CastProtocol) {
    val color = protocolColor(protocol)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            text = protocol.shortBadge,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

private fun protocolColor(protocol: CastProtocol): Color = when (protocol) {
    CastProtocol.GOOGLE_CAST -> AccentCyan
    CastProtocol.DLNA -> AccentEmerald
    CastProtocol.ROKU -> Color(0xFFB388FF)
    CastProtocol.AIRPLAY -> Color(0xFFFF8A65)
    CastProtocol.KODI -> Color(0xFF4FC3F7)
    CastProtocol.WEB_CAST -> GoldPrimary
    CastProtocol.MIRACAST -> GoldLight
}
