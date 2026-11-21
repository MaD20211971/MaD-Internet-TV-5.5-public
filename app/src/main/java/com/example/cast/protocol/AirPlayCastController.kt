package com.example.cast.protocol

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.example.cast.model.CastDevice
import com.example.cast.model.CastProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Apple AirPlay Integration:
 * - mDNS/NSD-Erkennung (_airplay._tcp.) für Apple TV, AirReceiver & AirPlay-Smart-TVs
 * - AirPlay 1 HTTP-Steuerung (/play, /rate, /stop auf Port 7000)
 * - Saubere Fehlerdiagnose, falls ein Empfänger verschlüsseltes AirPlay 2 (HAP/FairPlay) erzwingt
 */
class AirPlayCastController(
    private val context: Context,
    private val onDevicesUpdated: (List<CastDevice>) -> Unit
) {
    companion object {
        private const val TAG = "AirPlayCastController"
        private const val SERVICE_TYPE = "_airplay._tcp."

        internal fun buildAirPlayPlayBody(streamUrl: String, startPositionRatio: Double = 0.0): String {
            return "Content-Location: $streamUrl\nStart-Position: $startPositionRatio\n"
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
                Log.w(TAG, "AirPlay NSD Start fehlgeschlagen: $errorCode")
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
                        val port = if (resolved.port > 0) resolved.port else 7000
                        val name = resolved.serviceName.ifBlank { "AirPlay ($host)" }
                        val device = CastDevice(
                            id = "airplay_${host}_$port",
                            name = name,
                            protocol = CastProtocol.AIRPLAY,
                            host = host,
                            port = port,
                            modelName = "AirPlay Receiver ($host:$port)"
                        )
                        discoveredMap[device.id] = device
                        onDevicesUpdated(discoveredMap.values.toList())
                    }
                }
            )
        }
    }

    suspend fun castMedia(device: CastDevice, streamUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val port = if (device.port > 0) device.port else 7000
            val body = buildAirPlayPlayBody(streamUrl)
            val req = Request.Builder()
                .url("http://${device.host}:$port/play")
                .header("User-Agent", "MediaControl/1.0")
                .header("Content-Type", "text/parameters")
                .post(body.toRequestBody("text/parameters".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { res ->
                when {
                    res.isSuccessful -> Result.success(Unit)
                    res.code == 401 || res.code == 403 || res.code == 470 -> Result.failure(
                        IllegalStateException(
                            "Dieses Gerät erfordert verschlüsseltes Apple AirPlay 2 (HomeKit-Pairing). Nutze stattdessen 'Web-Cast' für sofortige Wiedergabe."
                        )
                    )
                    else -> Result.failure(IllegalStateException("AirPlay Fehler (HTTP ${res.code})"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setRate(device: CastDevice, isPlaying: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val port = if (device.port > 0) device.port else 7000
            val value = if (isPlaying) "1.0" else "0.0"
            val req = Request.Builder()
                .url("http://${device.host}:$port/rate?value=$value")
                .header("User-Agent", "MediaControl/1.0")
                .post("".toRequestBody("text/plain".toMediaType()))
                .build()
            httpClient.newCall(req).execute().use { res ->
                if (res.isSuccessful) Result.success(Unit)
                else Result.failure(IllegalStateException("AirPlay Rate-Befehl fehlgeschlagen (${res.code})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun stop(device: CastDevice): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val port = if (device.port > 0) device.port else 7000
            val req = Request.Builder()
                .url("http://${device.host}:$port/stop")
                .header("User-Agent", "MediaControl/1.0")
                .post("".toRequestBody("text/plain".toMediaType()))
                .build()
            httpClient.newCall(req).execute().use { res ->
                if (res.isSuccessful) Result.success(Unit)
                else Result.failure(IllegalStateException("AirPlay Stop fehlgeschlagen (${res.code})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
