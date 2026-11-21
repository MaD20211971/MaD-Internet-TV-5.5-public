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
import java.util.concurrent.TimeUnit

/**
 * Native DLNA / UPnP AVTransport & RenderingControl Implementierung:
 * - SSDP-Multicast-Erkennung (239.255.255.250:1900) für Samsung, LG, Sony, Philips, Panasonic, AV-Receiver etc.
 * - Device-XML-Auflösung (friendlyName, modelName, AVTransport & RenderingControl URLs)
 * - SOAP-Steuerung: SetAVTransportURI (mit DIDL-Lite), Play, Pause, Stop, SetVolume, SetMute
 */
class DlnaCastController {

    companion object {
        private const val TAG = "DlnaCastController"
        private const val SSDP_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val AV_TRANSPORT_SERVICE = "urn:schemas-upnp-org:service:AVTransport:1"
        private const val RENDERING_CONTROL_SERVICE = "urn:schemas-upnp-org:service:RenderingControl:1"

        internal fun xmlEscape(value: String): String = value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

        internal fun buildDidlLiteMetadata(streamUrl: String, media: CastMediaItem): String {
            val mime = media.inferredMimeType()
            val upnpClass = if (media.isAudioOnly) {
                "object.item.audioItem.audioBroadcast"
            } else {
                "object.item.videoItem.videoBroadcast"
            }
            val protocolInfo = "http-get:*:$mime:*"
            return """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"><item id="0" parentID="-1" restricted="1"><dc:title>${xmlEscape(media.title)}</dc:title><upnp:class>$upnpClass</upnp:class><res protocolInfo="$protocolInfo">${xmlEscape(streamUrl)}</res></item></DIDL-Lite>"""
        }

        internal fun parseDeviceDescriptionXml(locationUrl: String, xml: String): CastDevice? {
            val friendlyName = extractXmlTag(xml, "friendlyName")?.trim() ?: return null
            val modelName = extractXmlTag(xml, "modelName")?.trim().orEmpty()
            val avControl = extractServiceControlUrl(xml, AV_TRANSPORT_SERVICE) ?: return null
            val renderingControl = extractServiceControlUrl(xml, RENDERING_CONTROL_SERVICE).orEmpty()

            val baseUri = runCatching { URI(locationUrl) }.getOrNull() ?: return null
            val resolvedAvControl = runCatching { baseUri.resolve(avControl).toString() }.getOrDefault(avControl)
            val resolvedRenderControl = if (renderingControl.isNotBlank()) {
                runCatching { baseUri.resolve(renderingControl).toString() }.getOrDefault(renderingControl)
            } else {
                ""
            }

            val host = baseUri.host.orEmpty()
            val port = if (baseUri.port > 0) baseUri.port else 80
            val udn = extractXmlTag(xml, "UDN")?.trim().orEmpty()
            val stableId = if (udn.isNotBlank()) "dlna_${udn.hashCode()}" else "dlna_${host}_$port"

            return CastDevice(
                id = stableId,
                name = friendlyName,
                protocol = CastProtocol.DLNA,
                host = host,
                port = port,
                modelName = modelName.ifBlank { "DLNA Media Renderer" },
                controlUrl = resolvedAvControl,
                renderingControlUrl = resolvedRenderControl
            )
        }

        private fun extractXmlTag(xml: String, tagName: String): String? {
            val regex = Regex("<$tagName[^>]*>([^<]+)</$tagName>", RegexOption.IGNORE_CASE)
            return regex.find(xml)?.groupValues?.getOrNull(1)
        }

        private fun extractServiceControlUrl(xml: String, serviceType: String): String? {
            val serviceBlocks = Regex("<service>(.*?)</service>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                .findAll(xml)
            for (match in serviceBlocks) {
                val block = match.groupValues[1]
                if (block.contains(serviceType, ignoreCase = true)) {
                    val ctrl = extractXmlTag(block, "controlURL")
                    if (!ctrl.isNullOrBlank()) return ctrl.trim()
                }
            }
            return null
        }
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    suspend fun discoverDevices(timeoutMs: Int = 2600): List<CastDevice> = withContext(Dispatchers.IO) {
        val locations = LinkedHashSet<String>()
        val searchTargets = listOf(
            AV_TRANSPORT_SERVICE,
            "urn:schemas-upnp-org:device:MediaRenderer:1"
        )

        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket().apply {
                reuseAddress = true
                soTimeout = 450
            }
            val group = InetAddress.getByName(SSDP_ADDRESS)

            searchTargets.forEach { st ->
                val msg = buildString {
                    append("M-SEARCH * HTTP/1.1\r\n")
                    append("HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n")
                    append("MAN: \"ssdp:discover\"\r\n")
                    append("MX: 2\r\n")
                    append("ST: $st\r\n\r\n")
                }.toByteArray(Charsets.UTF_8)
                runCatching {
                    socket.send(DatagramPacket(msg, msg.size, group, SSDP_PORT))
                }
            }

            val deadline = System.currentTimeMillis() + timeoutMs
            val buf = ByteArray(4096)
            while (System.currentTimeMillis() < deadline) {
                val packet = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(packet)
                    val response = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    val location = response.lineSequence()
                        .firstOrNull { it.startsWith("LOCATION:", ignoreCase = true) }
                        ?.substringAfter(':')
                        ?.trim()
                    if (!location.isNullOrBlank() &&
                        location.startsWith("http", ignoreCase = true) &&
                        !response.contains("roku:ecp", ignoreCase = true)
                    ) {
                        locations.add(location)
                    }
                } catch (_: SocketTimeoutException) {
                    // Weiter bis Timeout abläuft
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "DLNA SSDP Scan Hinweis: ${e.message}")
        } finally {
            runCatching { socket?.close() }
        }

        val devices = LinkedHashMap<String, CastDevice>()
        for (location in locations) {
            try {
                val req = Request.Builder().url(location).get().build()
                httpClient.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        val xml = res.body?.string().orEmpty()
                        val parsed = parseDeviceDescriptionXml(location, xml)
                        if (parsed != null) {
                            devices[parsed.id] = parsed
                        }
                    }
                }
            } catch (_: Exception) {
                // Nicht erreichbare XML ignorieren
            }
        }
        devices.values.toList()
    }

    suspend fun castMedia(device: CastDevice, streamUrl: String, media: CastMediaItem): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val controlUrl = resolveControlUrl(device)
                    ?: return@withContext Result.failure(
                        IllegalStateException("Keine DLNA AVTransport-Steuerungs-URL für '${device.name}' gefunden.")
                    )

                val didl = buildDidlLiteMetadata(streamUrl, media)
                val setUriBody = """
                    <?xml version="1.0" encoding="utf-8"?>
                    <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                      <s:Body>
                        <u:SetAVTransportURI xmlns:u="$AV_TRANSPORT_SERVICE">
                          <InstanceID>0</InstanceID>
                          <CurrentURI>${xmlEscape(streamUrl)}</CurrentURI>
                          <CurrentURIMetaData>${xmlEscape(didl)}</CurrentURIMetaData>
                        </u:SetAVTransportURI>
                      </s:Body>
                    </s:Envelope>
                """.trimIndent()

                val setUriResult = sendSoapAction(controlUrl, AV_TRANSPORT_SERVICE, "SetAVTransportURI", setUriBody)
                if (setUriResult.isFailure) return@withContext setUriResult

                play(device.copy(controlUrl = controlUrl))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun play(device: CastDevice): Result<Unit> = withContext(Dispatchers.IO) {
        val controlUrl = resolveControlUrl(device) ?: return@withContext Result.failure(IllegalStateException("Kein DLNA Control-Endpunkt"))
        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
              <s:Body>
                <u:Play xmlns:u="$AV_TRANSPORT_SERVICE">
                  <InstanceID>0</InstanceID>
                  <Speed>1</Speed>
                </u:Play>
              </s:Body>
            </s:Envelope>
        """.trimIndent()
        sendSoapAction(controlUrl, AV_TRANSPORT_SERVICE, "Play", body)
    }

    suspend fun pause(device: CastDevice): Result<Unit> = withContext(Dispatchers.IO) {
        val controlUrl = resolveControlUrl(device) ?: return@withContext Result.failure(IllegalStateException("Kein DLNA Control-Endpunkt"))
        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
              <s:Body>
                <u:Pause xmlns:u="$AV_TRANSPORT_SERVICE">
                  <InstanceID>0</InstanceID>
                </u:Pause>
              </s:Body>
            </s:Envelope>
        """.trimIndent()
        sendSoapAction(controlUrl, AV_TRANSPORT_SERVICE, "Pause", body)
    }

    suspend fun stop(device: CastDevice): Result<Unit> = withContext(Dispatchers.IO) {
        val controlUrl = resolveControlUrl(device) ?: return@withContext Result.failure(IllegalStateException("Kein DLNA Control-Endpunkt"))
        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
              <s:Body>
                <u:Stop xmlns:u="$AV_TRANSPORT_SERVICE">
                  <InstanceID>0</InstanceID>
                </u:Stop>
              </s:Body>
            </s:Envelope>
        """.trimIndent()
        sendSoapAction(controlUrl, AV_TRANSPORT_SERVICE, "Stop", body)
    }

    suspend fun setVolume(device: CastDevice, volume: Float): Result<Unit> = withContext(Dispatchers.IO) {
        val renderUrl = device.renderingControlUrl.takeIf { it.isNotBlank() } ?: return@withContext Result.success(Unit)
        val pct = (volume.coerceIn(0f, 1f) * 100f).toInt()
        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
              <s:Body>
                <u:SetVolume xmlns:u="$RENDERING_CONTROL_SERVICE">
                  <InstanceID>0</InstanceID>
                  <Channel>Master</Channel>
                  <DesiredVolume>$pct</DesiredVolume>
                </u:SetVolume>
              </s:Body>
            </s:Envelope>
        """.trimIndent()
        sendSoapAction(renderUrl, RENDERING_CONTROL_SERVICE, "SetVolume", body)
    }

    suspend fun setMuted(device: CastDevice, muted: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val renderUrl = device.renderingControlUrl.takeIf { it.isNotBlank() } ?: return@withContext Result.success(Unit)
        val flag = if (muted) "1" else "0"
        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
              <s:Body>
                <u:SetMute xmlns:u="$RENDERING_CONTROL_SERVICE">
                  <InstanceID>0</InstanceID>
                  <Channel>Master</Channel>
                  <DesiredMute>$flag</DesiredMute>
                </u:SetMute>
              </s:Body>
            </s:Envelope>
        """.trimIndent()
        sendSoapAction(renderUrl, RENDERING_CONTROL_SERVICE, "SetMute", body)
    }

    private fun resolveControlUrl(device: CastDevice): String? {
        if (device.controlUrl.isNotBlank()) return device.controlUrl
        if (device.host.isBlank()) return null
        val port = if (device.port > 0) device.port else 1400
        return "http://${device.host}:$port/upnp/control/AVTransport1"
    }

    private fun sendSoapAction(
        controlUrl: String,
        serviceType: String,
        action: String,
        xmlBody: String
    ): Result<Unit> {
        return try {
            val req = Request.Builder()
                .url(controlUrl)
                .header("SOAPACTION", "\"$serviceType#$action\"")
                .header("Content-Type", "text/xml; charset=\"utf-8\"")
                .post(xmlBody.toRequestBody("text/xml; charset=utf-8".toMediaType()))
                .build()
            httpClient.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalStateException("DLNA $action Fehler (HTTP ${res.code})"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
