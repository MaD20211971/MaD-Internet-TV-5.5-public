package com.example.cast

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log
import com.example.cast.model.CastConnectionStatus
import com.example.cast.model.CastDevice
import com.example.cast.model.CastMediaItem
import com.example.cast.model.CastProtocol
import com.example.cast.model.CastUiState
import com.example.cast.protocol.AirPlayCastController
import com.example.cast.protocol.DlnaCastController
import com.example.cast.protocol.GoogleCastController
import com.example.cast.protocol.KodiCastController
import com.example.cast.protocol.LocalCastProxyAndWebServer
import com.example.cast.protocol.MiracastLauncher
import com.example.cast.protocol.RokuCastController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Zentraler Orchestrator für alle 7 Cast-Technologien:
 * Google Cast, DLNA/UPnP, Roku ECP, AirPlay, Kodi JSON-RPC, Web-Cast (HTML5/HLS Receiver) & Miracast.
 */
object UniversalCastManager {

    private const val TAG = "UniversalCastManager"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _uiState = MutableStateFlow(CastUiState())
    val uiState: StateFlow<CastUiState> = _uiState.asStateFlow()

    @Volatile
    private var appContext: Context? = null

    private var googleCastController: GoogleCastController? = null
    private val dlnaController = DlnaCastController()
    private val rokuController = RokuCastController()
    private var kodiController: KodiCastController? = null
    private var airPlayController: AirPlayCastController? = null
    private val localServer = LocalCastProxyAndWebServer()

    private val devicesByProtocol = ConcurrentHashMap<CastProtocol, List<CastDevice>>()
    private var multicastLock: WifiManager.MulticastLock? = null
    private var scanJob: Job? = null

    @Synchronized
    fun ensureInitialized(context: Context) {
        val ctx = context.applicationContext
        if (appContext == null) {
            appContext = ctx
            googleCastController = GoogleCastController(
                context = ctx,
                onDevicesUpdated = { list -> updateDiscoveredForProtocol(CastProtocol.GOOGLE_CAST, list) },
                onSessionConnected = { dev ->
                    _uiState.update {
                        it.copy(
                            activeDevice = dev,
                            connectionStatus = CastConnectionStatus.CONNECTED,
                            isRemotePlaying = true,
                            statusMessage = "Verbunden mit ${dev.name}",
                            errorMessage = null
                        )
                    }
                },
                onSessionEnded = {
                    if (_uiState.value.activeDevice?.protocol == CastProtocol.GOOGLE_CAST) {
                        _uiState.update {
                            it.copy(
                                activeDevice = null,
                                connectionStatus = CastConnectionStatus.IDLE,
                                isRemotePlaying = false,
                                statusMessage = null
                            )
                        }
                    }
                },
                onPlaybackStateChanged = { playing ->
                    _uiState.update { it.copy(isRemotePlaying = playing) }
                },
                onError = { msg ->
                    _uiState.update {
                        it.copy(
                            connectionStatus = CastConnectionStatus.ERROR,
                            errorMessage = msg
                        )
                    }
                }
            )
            kodiController = KodiCastController(ctx) { list ->
                updateDiscoveredForProtocol(CastProtocol.KODI, list)
            }
            airPlayController = AirPlayCastController(ctx) { list ->
                updateDiscoveredForProtocol(CastProtocol.AIRPLAY, list)
            }
            googleCastController?.initializeSafely()
        }

        val manual = ManualCastDeviceStore.loadDevices(ctx)
        val localIp = LocalCastProxyAndWebServer.detectLocalIpv4Address()
        val wifiOk = isWifiOrEthernetConnected(ctx) || localIp != null
        _uiState.update {
            it.copy(
                manualDevices = manual,
                localIpAddress = localIp,
                isWifiConnected = wifiOk
            )
        }
    }

    fun startDiscovery(context: Context) {
        ensureInitialized(context)
        val ctx = context.applicationContext
        val localIp = LocalCastProxyAndWebServer.detectLocalIpv4Address()
        val wifiOk = isWifiOrEthernetConnected(ctx) || localIp != null

        _uiState.update {
            it.copy(
                isScanning = true,
                isWifiConnected = wifiOk,
                localIpAddress = localIp,
                errorMessage = if (!wifiOk) {
                    "Kein WLAN/LAN erkannt. Bitte verbinde dich mit demselben Heimnetzwerk wie dein TV."
                } else null
            )
        }

        acquireMulticastLock(ctx)
        googleCastController?.startDiscovery()
        kodiController?.startDiscovery()
        airPlayController?.startDiscovery()

        scanJob?.cancel()
        scanJob = scope.launch {
            try {
                val dlnaDeferred = async(Dispatchers.IO) { dlnaController.discoverDevices() }
                val rokuDeferred = async(Dispatchers.IO) { rokuController.discoverDevices() }

                val dlnaDevices = dlnaDeferred.await()
                val rokuDevices = rokuDeferred.await()

                updateDiscoveredForProtocol(CastProtocol.DLNA, dlnaDevices)
                updateDiscoveredForProtocol(CastProtocol.ROKU, rokuDevices)
            } catch (e: Exception) {
                Log.w(TAG, "Discovery Fehler: ${e.message}")
            } finally {
                _uiState.update { it.copy(isScanning = false) }
            }
        }
    }

    fun stopDiscovery() {
        scanJob?.cancel()
        googleCastController?.stopDiscovery()
        kodiController?.stopDiscovery()
        airPlayController?.stopDiscovery()
        releaseMulticastLock()
        _uiState.update { it.copy(isScanning = false) }
    }

    fun setUseRelayProxy(enabled: Boolean) {
        _uiState.update { it.copy(useRelayProxy = enabled) }
    }

    fun addManualDevice(
        context: Context,
        name: String,
        protocol: CastProtocol,
        host: String,
        port: Int,
        username: String = "",
        password: String = ""
    ) {
        ensureInitialized(context)
        ManualCastDeviceStore.addDevice(
            context = context,
            name = name,
            protocol = protocol,
            host = host,
            port = port,
            username = username,
            password = password
        )
        _uiState.update {
            it.copy(
                manualDevices = ManualCastDeviceStore.loadDevices(context),
                statusMessage = "Gerät '$name' gespeichert"
            )
        }
    }

    fun deleteManualDevice(context: Context, deviceId: String) {
        ensureInitialized(context)
        val updated = ManualCastDeviceStore.deleteDevice(context, deviceId)
        _uiState.update { it.copy(manualDevices = updated) }
    }

    /**
     * Startet Web-Cast (eingebetteter HTTP-Server mit HTML5/HLS.js Receiver) und liefert die lokale URL.
     */
    fun startWebCast(context: Context, media: CastMediaItem): String? {
        ensureInitialized(context)
        val url = localServer.ensureStarted()
        if (url == null) {
            _uiState.update {
                it.copy(
                    connectionStatus = CastConnectionStatus.ERROR,
                    errorMessage = "Web-Cast benötigt eine aktive WLAN-/LAN-Verbindung."
                )
            }
            return null
        }
        localServer.updateMedia(media)
        val webDevice = CastDevice(
            id = "web_cast_local",
            name = "Web-Cast ($url)",
            protocol = CastProtocol.WEB_CAST,
            host = LocalCastProxyAndWebServer.detectLocalIpv4Address().orEmpty(),
            port = localServer.listeningPort,
            modelName = "Jeder Smart TV, PC oder Browser im WLAN"
        )
        _uiState.update {
            it.copy(
                activeDevice = webDevice,
                activeMedia = media,
                connectionStatus = CastConnectionStatus.CONNECTED,
                isRemotePlaying = true,
                webCastUrl = url,
                isWebCastRunning = true,
                statusMessage = "Web-Cast aktiv unter $url",
                errorMessage = null
            )
        }
        return url
    }

    fun launchMiracast(context: Context): Boolean {
        ensureInitialized(context)
        val launched = MiracastLauncher.openSystemMiracastSettings(context)
        if (!launched) {
            _uiState.update {
                it.copy(errorMessage = "Miracast / Smart View wird von diesem Gerät nicht direkt unterstützt.")
            }
        }
        return launched
    }

    /**
     * Übergibt einen Stream an das ausgewählte Zielgerät.
     */
    fun castToDevice(context: Context, device: CastDevice, media: CastMediaItem) {
        ensureInitialized(context)
        if (media.streamUrl.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Kein aktiver Stream zum Übertragen vorhanden.") }
            return
        }

        if (device.protocol == CastProtocol.WEB_CAST) {
            startWebCast(context, media)
            return
        }
        if (device.protocol == CastProtocol.MIRACAST) {
            launchMiracast(context)
            return
        }

        val effectiveUrl = resolveDeliveryUrl(media)
        _uiState.update {
            it.copy(
                activeDevice = device,
                activeMedia = media,
                connectionStatus = CastConnectionStatus.CONNECTING,
                statusMessage = "Verbinde mit ${device.name}...",
                errorMessage = null
            )
        }

        when (device.protocol) {
            CastProtocol.GOOGLE_CAST -> {
                googleCastController?.castMedia(device, effectiveUrl, media)
            }
            CastProtocol.DLNA -> {
                scope.launch {
                    val res = dlnaController.castMedia(device, effectiveUrl, media)
                    handleProtocolCastResult(device, media, res)
                }
            }
            CastProtocol.ROKU -> {
                scope.launch {
                    val res = rokuController.castMedia(device, effectiveUrl, media)
                    handleProtocolCastResult(device, media, res)
                }
            }
            CastProtocol.KODI -> {
                scope.launch {
                    val res = kodiController?.castMedia(device, effectiveUrl)
                        ?: Result.failure(IllegalStateException("Kodi-Controller nicht initialisiert"))
                    handleProtocolCastResult(device, media, res)
                }
            }
            CastProtocol.AIRPLAY -> {
                scope.launch {
                    val res = airPlayController?.castMedia(device, effectiveUrl)
                        ?: Result.failure(IllegalStateException("AirPlay-Controller nicht initialisiert"))
                    handleProtocolCastResult(device, media, res)
                }
            }
            else -> Unit
        }
    }

    /**
     * Wenn der Nutzer während einer aktiven Cast-Sitzung den Sender wechselt,
     * wird das Zielgerät (Web-Cast, Chromecast, DLNA, Roku, Kodi, AirPlay) automatisch umgeschaltet.
     */
    fun updateMediaIfConnected(context: Context, media: CastMediaItem) {
        val state = _uiState.value
        val device = state.activeDevice ?: return
        if (!state.isConnected) return
        if (state.activeMedia?.streamUrl == media.streamUrl && state.activeMedia.title == media.title) return

        if (device.protocol == CastProtocol.WEB_CAST) {
            localServer.updateMedia(media)
            _uiState.update {
                it.copy(
                    activeMedia = media,
                    isRemotePlaying = true,
                    statusMessage = "Web-Cast umgeschaltet: ${media.title}"
                )
            }
        } else {
            castToDevice(context, device, media)
        }
    }

    fun togglePlayPause() {
        val state = _uiState.value
        val device = state.activeDevice ?: return
        val nextPlay = !state.isRemotePlaying

        when (device.protocol) {
            CastProtocol.GOOGLE_CAST -> googleCastController?.togglePlayPause(nextPlay)
            CastProtocol.WEB_CAST -> {
                localServer.setRemoteCommand(if (nextPlay) "play" else "pause")
                _uiState.update { it.copy(isRemotePlaying = nextPlay) }
            }
            CastProtocol.DLNA -> scope.launch {
                val res = if (nextPlay) dlnaController.play(device) else dlnaController.pause(device)
                if (res.isSuccess) _uiState.update { it.copy(isRemotePlaying = nextPlay) }
            }
            CastProtocol.KODI -> scope.launch {
                val res = kodiController?.togglePlayPause(device)
                if (res?.isSuccess == true) _uiState.update { it.copy(isRemotePlaying = nextPlay) }
            }
            CastProtocol.ROKU -> scope.launch {
                val res = rokuController.sendKeypress(device, "Play")
                if (res.isSuccess) _uiState.update { it.copy(isRemotePlaying = nextPlay) }
            }
            CastProtocol.AIRPLAY -> scope.launch {
                val res = airPlayController?.setRate(device, nextPlay)
                if (res?.isSuccess == true) _uiState.update { it.copy(isRemotePlaying = nextPlay) }
            }
            CastProtocol.MIRACAST -> Unit
        }
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        val device = _uiState.value.activeDevice ?: return
        val prev = _uiState.value.remoteVolume
        _uiState.update { it.copy(remoteVolume = clamped, isRemoteMuted = clamped <= 0.01f) }

        when (device.protocol) {
            CastProtocol.GOOGLE_CAST -> googleCastController?.setVolume(clamped)
            CastProtocol.WEB_CAST -> localServer.setRemoteVolume(clamped, clamped <= 0.01f)
            CastProtocol.DLNA -> scope.launch { dlnaController.setVolume(device, clamped) }
            CastProtocol.KODI -> scope.launch { kodiController?.setVolume(device, clamped) }
            CastProtocol.ROKU -> scope.launch {
                val key = if (clamped >= prev) "VolumeUp" else "VolumeDown"
                rokuController.sendKeypress(device, key)
            }
            CastProtocol.AIRPLAY, CastProtocol.MIRACAST -> Unit
        }
    }

    fun toggleMute() {
        val state = _uiState.value
        val device = state.activeDevice ?: return
        val nextMuted = !state.isRemoteMuted
        _uiState.update { it.copy(isRemoteMuted = nextMuted) }

        when (device.protocol) {
            CastProtocol.GOOGLE_CAST -> googleCastController?.setMuted(nextMuted)
            CastProtocol.WEB_CAST -> localServer.setRemoteVolume(state.remoteVolume, nextMuted)
            CastProtocol.DLNA -> scope.launch { dlnaController.setMuted(device, nextMuted) }
            CastProtocol.KODI -> scope.launch { kodiController?.setMuted(device, nextMuted) }
            CastProtocol.ROKU -> scope.launch { rokuController.sendKeypress(device, "VolumeMute") }
            CastProtocol.AIRPLAY, CastProtocol.MIRACAST -> Unit
        }
    }

    fun stopAndDisconnect() {
        val device = _uiState.value.activeDevice
        if (device != null) {
            when (device.protocol) {
                CastProtocol.GOOGLE_CAST -> {
                    googleCastController?.stopPlayback()
                    googleCastController?.disconnect()
                }
                CastProtocol.DLNA -> scope.launch { dlnaController.stop(device) }
                CastProtocol.KODI -> scope.launch { kodiController?.stop(device) }
                CastProtocol.ROKU -> scope.launch { rokuController.sendKeypress(device, "Home") }
                CastProtocol.AIRPLAY -> scope.launch { airPlayController?.stop(device) }
                CastProtocol.WEB_CAST -> {
                    localServer.setRemoteCommand("stop")
                    localServer.stop()
                }
                CastProtocol.MIRACAST -> Unit
            }
        } else {
            localServer.stop()
        }

        _uiState.update {
            it.copy(
                activeDevice = null,
                activeMedia = null,
                connectionStatus = CastConnectionStatus.IDLE,
                isRemotePlaying = false,
                isWebCastRunning = false,
                webCastUrl = null,
                statusMessage = null,
                errorMessage = null
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun resolveDeliveryUrl(media: CastMediaItem): String {
        val isLocalFile = media.streamUrl.startsWith("/") || media.streamUrl.startsWith("file://")
        val needsProxy = _uiState.value.useRelayProxy || isLocalFile || media.headers.isNotEmpty()
        if (needsProxy) {
            val proxied = localServer.buildProxiedStreamUrl(media)
            if (!proxied.isNullOrBlank()) return proxied
        }
        return media.streamUrl
    }

    private fun handleProtocolCastResult(device: CastDevice, media: CastMediaItem, result: Result<Unit>) {
        if (result.isSuccess) {
            _uiState.update {
                it.copy(
                    activeDevice = device,
                    activeMedia = media,
                    connectionStatus = CastConnectionStatus.CONNECTED,
                    isRemotePlaying = true,
                    statusMessage = "Übertragung aktiv auf ${device.name}",
                    errorMessage = null
                )
            }
        } else {
            val msg = result.exceptionOrNull()?.localizedMessage
                ?: "Verbindung zu ${device.name} fehlgeschlagen."
            _uiState.update {
                it.copy(
                    activeDevice = null,
                    connectionStatus = CastConnectionStatus.ERROR,
                    isRemotePlaying = false,
                    errorMessage = msg
                )
            }
        }
    }

    private fun updateDiscoveredForProtocol(protocol: CastProtocol, devices: List<CastDevice>) {
        devicesByProtocol[protocol] = devices
        val merged = LinkedHashMap<String, CastDevice>()
        val order = listOf(
            CastProtocol.GOOGLE_CAST,
            CastProtocol.DLNA,
            CastProtocol.ROKU,
            CastProtocol.KODI,
            CastProtocol.AIRPLAY
        )
        order.forEach { proto ->
            devicesByProtocol[proto].orEmpty().forEach { d -> merged[d.id] = d }
        }
        _uiState.update { it.copy(discoveredDevices = merged.values.toList()) }
    }

    private fun isWifiOrEthernetConnected(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } catch (_: Exception) {
            true
        }
    }

    private fun acquireMulticastLock(context: Context) {
        try {
            if (multicastLock == null) {
                val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                multicastLock = wifi?.createMulticastLock("MaDIPTV_UniversalCast")?.apply {
                    setReferenceCounted(false)
                }
            }
            if (multicastLock?.isHeld == false) {
                multicastLock?.acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "MulticastLock Hinweis: ${e.message}")
        }
    }

    private fun releaseMulticastLock() {
        runCatching {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        }
    }
}
