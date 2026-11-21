package com.example.cast.protocol

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Base64
import android.util.Log
import com.example.cast.model.CastDevice
import com.example.cast.model.CastProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Native Kodi (XBMC) Integration über mDNS/NSD-Erkennung (_xbmc-jsonrpc-h._tcp.)
 * sowie vollständige Kodi JSON-RPC 2.0 HTTP-API inkl. optionaler Basic-Authentifizierung.
 */
class KodiCastController(
    private val context: Context,
    private val onDevicesUpdated: (List<CastDevice>) -> Unit
) {
    companion object {
        private const val TAG = "KodiCastController"
        private const val SERVICE_TYPE = "_xbmc-jsonrpc-h._tcp."

        internal fun buildPlayerOpenPayload(streamUrl: String): String {
            return JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "Player.Open")
                put(
                    "params",
                    JSONObject().apply {
                        put("item", JSONObject().apply { put("file", streamUrl) })
                    }
                )
            }.toString()
        }
    }

    private val nsdManager: NsdManager? by lazy {
        context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
    }
    private val discoveredMap = ConcurrentHashMap<String, CastDevice>()
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    fun startDiscovery() {
        val manager = nsdManager ?: return
        if (discoveryListener != null) return

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "Kodi NSD Start fehlgeschlagen: $errorCode")
                stopDiscovery()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                discoveryListener = null
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                resolveService(manager, serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val removed = discoveredMap.keys.filter { it.contains(serviceInfo.serviceName) }
                removed.forEach { discoveredMap.remove(it) }
                onDevicesUpdated(discoveredMap.values.toList())
            }
        }
        discoveryListener = listener
        runCatching {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure {
            discoveryListener = null
        }
    }

    fun stopDiscovery() {
        val listener = discoveryListener ?: return
        discoveryListener = null
        runCatching { nsdManager?.stopServiceDiscovery(listener) }
    }

    @Suppress("DEPRECATION")
    private fun resolveService(manager: NsdManager, info: NsdServiceInfo) {
        runCatching {
            manager.resolveService(
                info,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        val host = resolved.host?.hostAddress ?: return
                        val port = if (resolved.port > 0) resolved.port else 8080
                        val name = resolved.serviceName.ifBlank { "Kodi ($host)" }
                        val device = CastDevice(
                            id = "kodi_${host}_$port",
                            name = name,
                            protocol = CastProtocol.KODI,
                            host = host,
                            port = port,
                            modelName = "Kodi JSON-RPC ($host:$port)"
                        )
                        discoveredMap[device.id] = device
                        onDevicesUpdated(discoveredMap.values.toList())
                    }
                }
            )
        }
    }

    suspend fun castMedia(device: CastDevice, streamUrl: String): Result<Unit> =
        sendJsonRpc(device, buildPlayerOpenPayload(streamUrl))

    suspend fun togglePlayPause(device: CastDevice): Result<Unit> = withContext(Dispatchers.IO) {
        val playerId = getActivePlayerId(device) ?: 1
        val payload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 2)
            put("method", "Player.PlayPause")
            put("params", JSONObject().apply { put("playerid", playerId) })
        }.toString()
        sendJsonRpc(device, payload)
    }

    suspend fun stop(device: CastDevice): Result<Unit> = withContext(Dispatchers.IO) {
        val playerId = getActivePlayerId(device) ?: 1
        val payload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 3)
            put("method", "Player.Stop")
            put("params", JSONObject().apply { put("playerid", playerId) })
        }.toString()
        sendJsonRpc(device, payload)
    }

    suspend fun setVolume(device: CastDevice, volume: Float): Result<Unit> {
        val pct = (volume.coerceIn(0f, 1f) * 100f).toInt()
        val payload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 4)
            put("method", "Application.SetVolume")
            put("params", JSONObject().apply { put("volume", pct) })
        }.toString()
        return sendJsonRpc(device, payload)
    }

    suspend fun setMuted(device: CastDevice, muted: Boolean): Result<Unit> {
        val payload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 5)
            put("method", "Application.SetMute")
            put("params", JSONObject().apply { put("mute", muted) })
        }.toString()
        return sendJsonRpc(device, payload)
    }

    private suspend fun getActivePlayerId(device: CastDevice): Int? = withContext(Dispatchers.IO) {
        try {
            val payload = """{"jsonrpc":"2.0","id":9,"method":"Player.GetActivePlayers"}"""
            val req = buildRequest(device, payload)
            httpClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@withContext null
                val json = JSONObject(res.body?.string().orEmpty())
                val arr = json.optJSONArray("result") ?: return@withContext null
                if (arr.length() > 0) arr.optJSONObject(0)?.optInt("playerid", 1) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun sendJsonRpc(device: CastDevice, jsonPayload: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val req = buildRequest(device, jsonPayload)
                httpClient.newCall(req).execute().use { res ->
                    when {
                        res.code == 401 -> Result.failure(
                            IllegalStateException("Kodi erfordert Benutzername & Passwort (Gerät manuell mit Login anlegen).")
                        )
                        res.isSuccessful -> Result.success(Unit)
                        else -> Result.failure(IllegalStateException("Kodi HTTP-Fehler ${res.code}"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun buildRequest(device: CastDevice, jsonPayload: String): Request {
        val port = if (device.port > 0) device.port else 8080
        val builder = Request.Builder()
            .url("http://${device.host}:$port/jsonrpc")
            .header("Content-Type", "application/json")
            .post(jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType()))

        if (device.username.isNotBlank() || device.password.isNotBlank()) {
            val credentials = "${device.username}:${device.password}"
            val encoded = Base64.encodeToString(credentials.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            builder.header("Authorization", "Basic $encoded")
        }
        return builder.build()
    }
}
