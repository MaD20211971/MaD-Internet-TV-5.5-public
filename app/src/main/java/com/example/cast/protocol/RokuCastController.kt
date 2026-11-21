package com.example.cast.protocol

import android.util.Log
import com.example.cast.model.CastDevice
import com.example.cast.model.CastMediaItem
import com.example.cast.model.CastProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Native Roku External Control Protocol (ECP) Integration:
 * - SSDP-Erkennung (ST: roku:ecp) + Abfrage von /query/device-info
 * - Direkte Stream-Übergabe über PlayOnRoku (Channel 15985) sowie ECP-Steuerung (Play/Pause, Home/Stop, Lautstärke)
 */
class RokuCastController {

    companion object {
        private const val TAG = "RokuCastController"
        private const val SSDP_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900

        internal fun buildPlayOnRokuEndpoint(
            host: String,
            port: Int,
            streamUrl: String,
            media: CastMediaItem
        ): String {
            val encodedUrl = URLEncoder.encode(streamUrl, "UTF-8")
            val encodedTitle = URLEncoder.encode(media.title.ifBlank { "MaD IPTV public" }, "UTF-8")
            val format = when {
                media.isAudioOnly -> "mp3"
                streamUrl.lowercase().contains(".m3u8") || media.isLive -> "hls"
                else -> "mp4"
            }
            val typeFlag = if (media.isAudioOnly) "a" else "v"
            return "http://$host:$port/input/15985?t=$typeFlag&u=$encodedUrl&videoName=$encodedTitle&videoFormat=$format"
        }
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    suspend fun discoverDevices(timeoutMs: Int = 2200): List<CastDevice> = withContext(Dispatchers.IO) {
        val locations = LinkedHashSet<String>()
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket().apply {
                reuseAddress = true
                soTimeout = 400
            }
            val group = InetAddress.getByName(SSDP_ADDRESS)
            val msg = buildString {
                append("M-SEARCH * HTTP/1.1\r\n")
                append("HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n")
                append("MAN: \"ssdp:discover\"\r\n")
                append("MX: 2\r\n")
                append("ST: roku:ecp\r\n\r\n")
            }.toByteArray(Charsets.UTF_8)
            socket.send(DatagramPacket(msg, msg.size, group, SSDP_PORT))

            val deadline = System.currentTimeMillis() + timeoutMs
            val buf = ByteArray(2048)
            while (System.currentTimeMillis() < deadline) {
                val packet = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(packet)
                    val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    val location = text.lineSequence()
                        .firstOrNull { it.startsWith("LOCATION:", ignoreCase = true) }
                        ?.substringAfter(':')
                        ?.trim()
                    if (!location.isNullOrBlank()) {
                        locations.add(location)
                    }
                } catch (_: SocketTimeoutException) {
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Roku SSDP Scan Hinweis: ${e.message}")
        } finally {
            runCatching { socket?.close() }
        }

        val devices = mutableListOf<CastDevice>()
        for (location in locations) {
            val uri = runCatching { URI(location) }.getOrNull() ?: continue
            val host = uri.host ?: continue
            val port = if (uri.port > 0) uri.port else 8060
            var friendlyName = "Roku ($host)"
            var modelName = "Roku Streaming Device"

            runCatching {
                val req = Request.Builder().url("http://$host:$port/query/device-info").get().build()
                httpClient.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        val xml = res.body?.string().orEmpty()
                        extractTag(xml, "user-device-name")?.takeIf { it.isNotBlank() }?.let { friendlyName = it }
                            ?: extractTag(xml, "friendly-device-name")?.takeIf { it.isNotBlank() }?.let { friendlyName = it }
                        extractTag(xml, "model-name")?.takeIf { it.isNotBlank() }?.let { modelName = it }
                    }
                }
            }

            devices.add(
                CastDevice(
                    id = "roku_${host}_$port",
                    name = friendlyName,
                    protocol = CastProtocol.ROKU,
                    host = host,
                    port = port,
                    modelName = modelName
                )
            )
        }
        devices
    }

    suspend fun castMedia(device: CastDevice, streamUrl: String, media: CastMediaItem): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val port = if (device.port > 0) device.port else 8060
                val endpoint = buildPlayOnRokuEndpoint(device.host, port, streamUrl, media)
                val emptyBody = "".toRequestBody("text/plain".toMediaType())
                val req = Request.Builder().url(endpoint).post(emptyBody).build()
                httpClient.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        Result.success(Unit)
                    } else {
                        // Fallback: PlayOnRoku zuerst starten und erneut versuchen
                        val launchReq = Request.Builder()
                            .url("http://${device.host}:$port/launch/15985")
                            .post(emptyBody)
                            .build()
                        runCatching { httpClient.newCall(launchReq).execute().close() }
                        Result.failure(
                            IllegalStateException(
                                "Roku lehnte Stream ab (HTTP ${res.code}). Bitte prüfe, ob 'Steuerung durch mobile Apps' auf dem Roku aktiv ist."
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun sendKeypress(device: CastDevice, key: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val port = if (device.port > 0) device.port else 8060
            val emptyBody = "".toRequestBody("text/plain".toMediaType())
            val req = Request.Builder()
                .url("http://${device.host}:$port/keypress/$key")
                .post(emptyBody)
                .build()
            httpClient.newCall(req).execute().use { res ->
                if (res.isSuccessful) Result.success(Unit)
                else Result.failure(IllegalStateException("Roku Keypress $key fehlgeschlagen (${res.code})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractTag(xml: String, tag: String): String? {
        val regex = Regex("<$tag[^>]*>([^<]+)</$tag>", RegexOption.IGNORE_CASE)
        return regex.find(xml)?.groupValues?.getOrNull(1)?.trim()
    }
}
