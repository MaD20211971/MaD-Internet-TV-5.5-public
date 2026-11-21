package com.example.cast.protocol

import android.util.Base64
import android.util.Log
import com.example.cast.model.CastMediaItem
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Kombinierter lokaler HTTP-Server im Heimnetzwerk:
 * 1. IPTV-Header- & CORS-Relay-Proxy (/stream, /proxy?u=...) mit M3U8-Manifest-Rewriting
 *    und HTTP-Range-Support (auch für lokale Aufnahmen/Downloads).
 * 2. Universeller Web-Cast Receiver (/ und /api/state) mit HTML5 + HLS.js Vollbild-Player
 *    und automatischem Live-Senderwechsel sowie Play/Pause/Volume-Fernsteuerung.
 */
class LocalCastProxyAndWebServer {

    companion object {
        private const val TAG = "LocalCastWebServer"
        private const val PREFERRED_PORT = 8099
        private const val MAX_PORT_ATTEMPTS = 12

        fun detectLocalIpv4Address(): String? {
            return try {
                val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
                var fallbackIp: String? = null
                while (interfaces.hasMoreElements()) {
                    val iface = interfaces.nextElement()
                    if (!iface.isUp || iface.isLoopback || iface.isVirtual) continue
                    val nameLower = iface.name.lowercase()
                    val addrs = iface.inetAddresses
                    while (addrs.hasMoreElements()) {
                        val addr = addrs.nextElement()
                        if (addr is Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress) {
                            val ip = addr.hostAddress ?: continue
                            if (nameLower.startsWith("wlan") || nameLower.startsWith("eth") || nameLower.startsWith("en")) {
                                return ip
                            }
                            if (fallbackIp == null) fallbackIp = ip
                        }
                    }
                }
                fallbackIp
            } catch (e: Exception) {
                Log.w(TAG, "LAN-IP konnte nicht ermittelt werden: ${e.message}")
                null
            }
        }

        internal fun encodeUrlBase64(url: String): String {
            return try {
                Base64.encodeToString(
                    url.toByteArray(Charsets.UTF_8),
                    Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
                )
            } catch (_: Throwable) {
                java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(url.toByteArray(Charsets.UTF_8))
            }
        }

        internal fun decodeUrlBase64(encoded: String): String? {
            return try {
                String(
                    Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
                    Charsets.UTF_8
                )
            } catch (_: Throwable) {
                runCatching {
                    String(java.util.Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8)
                }.getOrNull()
            }
        }

        /**
         * Schreibt alle relativen und absoluten Segment-/Varianten-URLs innerhalb einer M3U8-Playlist
         * auf den lokalen Proxy um, damit Chromecast/DLNA/Browser keine CORS- oder Header-Fehler erhalten.
         */
        internal fun rewriteHlsPlaylist(
            playlistText: String,
            sourcePlaylistUrl: String,
            proxyBaseUrl: String
        ): String {
            val baseUri = runCatching { URI(sourcePlaylistUrl) }.getOrNull()
            val out = StringBuilder(playlistText.length + 256)
            playlistText.lineSequence().forEach { rawLine ->
                val line = rawLine.trimEnd('\r')
                val trimmed = line.trim()
                when {
                    trimmed.isEmpty() -> out.append("\n")
                    trimmed.startsWith("#") -> {
                        // URI="..." innerhalb von #EXT-X-KEY, #EXT-X-MEDIA oder #EXT-X-MAP umschreiben
                        val rewrittenTag = Regex("""URI="([^"]+)"""").replace(line) { match ->
                            val rawRef = match.groupValues[1]
                            val resolved = resolveAgainstBase(baseUri, rawRef)
                            val proxied = "$proxyBaseUrl/proxy?u=${encodeUrlBase64(resolved)}"
                            """URI="$proxied""""
                        }
                        out.append(rewrittenTag).append("\n")
                    }
                    else -> {
                        val resolved = resolveAgainstBase(baseUri, trimmed)
                        out.append("$proxyBaseUrl/proxy?u=${encodeUrlBase64(resolved)}\n")
                    }
                }
            }
            return out.toString()
        }

        private fun resolveAgainstBase(baseUri: URI?, reference: String): String {
            if (reference.startsWith("http://", ignoreCase = true) ||
                reference.startsWith("https://", ignoreCase = true)
            ) {
                return reference
            }
            if (baseUri == null) return reference
            return runCatching { baseUri.resolve(reference).toString() }.getOrDefault(reference)
        }
    }

    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var executor: ExecutorService? = null

    @Volatile
    var listeningPort: Int = PREFERRED_PORT
        private set

    @Volatile
    private var currentMedia: CastMediaItem? = null

    @Volatile
    private var remoteCommand: String = "play" // "play" | "pause" | "stop"

    @Volatile
    private var remoteVolume: Float = 0.8f

    @Volatile
    private var remoteMuted: Boolean = false

    private val stateVersion = AtomicInteger(1)

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    @Synchronized
    fun ensureStarted(): String? {
        val localIp = detectLocalIpv4Address() ?: return null
        if (running.get() && serverSocket != null && serverSocket?.isClosed == false) {
            return "http://$localIp:$listeningPort"
        }

        var boundSocket: ServerSocket? = null
        var boundPort = PREFERRED_PORT
        for (offset in 0 until MAX_PORT_ATTEMPTS) {
            val candidatePort = PREFERRED_PORT + offset
            try {
                boundSocket = ServerSocket(candidatePort, 32, InetAddress.getByName("0.0.0.0")).apply {
                    reuseAddress = true
                }
                boundPort = candidatePort
                break
            } catch (_: Exception) {
                // Nächsten Port probieren
            }
        }
        val socket = boundSocket ?: return null
        serverSocket = socket
        listeningPort = boundPort
        running.set(true)

        val pool = Executors.newCachedThreadPool()
        executor = pool
        pool.execute {
            while (running.get() && !socket.isClosed) {
                try {
                    val client = socket.accept()
                    pool.execute { handleClient(client) }
                } catch (_: Exception) {
                    if (!running.get()) break
                }
            }
        }
        return "http://$localIp:$listeningPort"
    }

    fun updateMedia(media: CastMediaItem) {
        currentMedia = media
        remoteCommand = "play"
        stateVersion.incrementAndGet()
    }

    fun setRemoteCommand(command: String) {
        remoteCommand = command
        stateVersion.incrementAndGet()
    }

    fun setRemoteVolume(volume: Float, muted: Boolean = remoteMuted) {
        remoteVolume = volume.coerceIn(0f, 1f)
        remoteMuted = muted
        stateVersion.incrementAndGet()
    }

    fun buildProxiedStreamUrl(media: CastMediaItem): String? {
        val baseUrl = ensureStarted() ?: return null
        updateMedia(media)
        val ext = when {
            media.isAudioOnly -> ".mp3"
            media.streamUrl.lowercase().contains(".m3u8") -> ".m3u8"
            media.streamUrl.lowercase().contains(".mp4") -> ".mp4"
            media.streamUrl.lowercase().contains(".ts") -> ".ts"
            media.isLive -> ".m3u8"
            else -> ".mp4"
        }
        return "$baseUrl/stream$ext?v=${stateVersion.get()}"
    }

    fun getWebReceiverUrl(): String? = ensureStarted()

    @Synchronized
    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        runCatching { executor?.shutdownNow() }
        executor = null
    }

    private fun handleClient(socket: Socket) {
        socket.use { client ->
            try {
                client.soTimeout = 20_000
                val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0].uppercase()
                val fullPath = parts[1]

                val requestHeaders = mutableMapOf<String, String>()
                while (true) {
                    val headerLine = reader.readLine() ?: break
                    if (headerLine.isEmpty()) break
                    val sep = headerLine.indexOf(':')
                    if (sep > 0) {
                        requestHeaders[headerLine.substring(0, sep).trim().lowercase()] =
                            headerLine.substring(sep + 1).trim()
                    }
                }

                val out = client.getOutputStream()
                if (method == "OPTIONS") {
                    writeCorsPreflight(out)
                    return
                }

                val pathOnly = fullPath.substringBefore('?')
                val query = fullPath.substringAfter('?', "")
                val hostHeader = requestHeaders["host"]
                    ?: "${detectLocalIpv4Address() ?: "127.0.0.1"}:$listeningPort"
                val proxyBaseUrl = "http://$hostHeader"

                when {
                    pathOnly == "/" || pathOnly == "/index.html" -> {
                        writeTextResponse(out, 200, "text/html; charset=utf-8", buildReceiverHtml())
                    }
                    pathOnly == "/api/state" -> {
                        val json = buildStateJson(proxyBaseUrl)
                        writeTextResponse(out, 200, "application/json; charset=utf-8", json)
                    }
                    pathOnly.startsWith("/stream") -> {
                        val media = currentMedia
                        if (media == null || media.streamUrl.isBlank()) {
                            writeTextResponse(out, 404, "text/plain", "No active stream")
                        } else {
                            proxyTargetUrl(
                                targetUrl = media.streamUrl,
                                customHeaders = media.headers,
                                clientRange = requestHeaders["range"],
                                proxyBaseUrl = proxyBaseUrl,
                                isHeadOnly = method == "HEAD",
                                out = out
                            )
                        }
                    }
                    pathOnly == "/proxy" -> {
                        val encoded = extractQueryParam(query, "u")
                        val decodedUrl = encoded?.let { decodeUrlBase64(it) }
                        if (decodedUrl.isNullOrBlank()) {
                            writeTextResponse(out, 400, "text/plain", "Missing target url")
                        } else {
                            proxyTargetUrl(
                                targetUrl = decodedUrl,
                                customHeaders = currentMedia?.headers.orEmpty(),
                                clientRange = requestHeaders["range"],
                                proxyBaseUrl = proxyBaseUrl,
                                isHeadOnly = method == "HEAD",
                                out = out
                            )
                        }
                    }
                    else -> {
                        writeTextResponse(out, 404, "text/plain", "Not Found")
                    }
                }
            } catch (_: Exception) {
                // Client hat Verbindung beendet (normal bei Seek/Zapping)
            }
        }
    }

    private fun proxyTargetUrl(
        targetUrl: String,
        customHeaders: Map<String, String>,
        clientRange: String?,
        proxyBaseUrl: String,
        isHeadOnly: Boolean,
        out: OutputStream
    ) {
        // Lokale Dateien (Downloads & Aufnahmen) mit HTTP-Range direkt ausliefern
        if (targetUrl.startsWith("/") || targetUrl.startsWith("file://")) {
            val filePath = targetUrl.removePrefix("file://")
            serveLocalFile(File(filePath), clientRange, isHeadOnly, out)
            return
        }

        val reqBuilder = Request.Builder()
            .url(targetUrl)
            .header(
                "User-Agent",
                customHeaders["User-Agent"]
                    ?: "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
            )

        customHeaders.forEach { (k, v) ->
            if (!k.equals("Host", ignoreCase = true) && v.isNotBlank()) {
                reqBuilder.header(k, v)
            }
        }
        if (!clientRange.isNullOrBlank()) {
            reqBuilder.header("Range", clientRange)
        }

        val response = try {
            httpClient.newCall(reqBuilder.build()).execute()
        } catch (e: Exception) {
            writeTextResponse(out, 502, "text/plain", "Upstream error: ${e.message}")
            return
        }

        response.use { res ->
            val contentType = res.header("Content-Type")
                ?: currentMedia?.inferredMimeType()
                ?: "application/octet-stream"
            val isM3u8 = targetUrl.lowercase().contains(".m3u8") ||
                contentType.contains("mpegurl", ignoreCase = true)

            if (isM3u8) {
                val bodyText = res.body?.string().orEmpty()
                val finalUrl = res.request.url.toString()
                val rewritten = if (bodyText.contains("#EXTM3U", ignoreCase = true)) {
                    rewriteHlsPlaylist(bodyText, finalUrl, proxyBaseUrl)
                } else {
                    bodyText
                }
                val bytes = rewritten.toByteArray(Charsets.UTF_8)
                val headers = buildString {
                    append("HTTP/1.1 ${res.code} OK\r\n")
                    append("Content-Type: application/vnd.apple.mpegurl; charset=utf-8\r\n")
                    append("Content-Length: ${bytes.size}\r\n")
                    append("Access-Control-Allow-Origin: *\r\n")
                    append("Access-Control-Allow-Headers: *\r\n")
                    append("Cache-Control: no-cache, no-store\r\n")
                    append("Connection: close\r\n\r\n")
                }
                out.write(headers.toByteArray(Charsets.US_ASCII))
                if (!isHeadOnly) {
                    out.write(bytes)
                }
                out.flush()
                return
            }

            val contentLength = res.header("Content-Length")
            val contentRange = res.header("Content-Range")
            val acceptRanges = res.header("Accept-Ranges")
            val headerBlock = buildString {
                append("HTTP/1.1 ${res.code} ${res.message.ifBlank { "OK" }}\r\n")
                append("Content-Type: $contentType\r\n")
                if (!contentLength.isNullOrBlank()) append("Content-Length: $contentLength\r\n")
                if (!contentRange.isNullOrBlank()) append("Content-Range: $contentRange\r\n")
                if (!acceptRanges.isNullOrBlank()) append("Accept-Ranges: $acceptRanges\r\n")
                append("Access-Control-Allow-Origin: *\r\n")
                append("Access-Control-Allow-Headers: *\r\n")
                append("Cache-Control: no-cache\r\n")
                append("Connection: close\r\n\r\n")
            }
            out.write(headerBlock.toByteArray(Charsets.US_ASCII))
            out.flush()

            if (!isHeadOnly) {
                val stream = res.body?.byteStream() ?: return
                val buffered = BufferedInputStream(stream, 32 * 1024)
                val buffer = ByteArray(16 * 1024)
                while (running.get()) {
                    val read = buffered.read(buffer)
                    if (read == -1) break
                    out.write(buffer, 0, read)
                }
                out.flush()
            }
        }
    }

    private fun serveLocalFile(
        file: File,
        clientRange: String?,
        isHeadOnly: Boolean,
        out: OutputStream
    ) {
        if (!file.exists() || !file.isFile) {
            writeTextResponse(out, 404, "text/plain", "Local file not found")
            return
        }
        val fileLen = file.length()
        val mime = when (file.extension.lowercase()) {
            "mp3" -> "audio/mpeg"
            "m4a", "aac" -> "audio/aac"
            "mkv" -> "video/x-matroska"
            "ts" -> "video/mp2t"
            else -> "video/mp4"
        }

        var start = 0L
        var end = fileLen - 1
        var statusCode = 200
        var statusText = "OK"

        if (!clientRange.isNullOrBlank() && clientRange.startsWith("bytes=")) {
            val rangeSpec = clientRange.removePrefix("bytes=").substringBefore(",")
            val rangeParts = rangeSpec.split("-")
            val reqStart = rangeParts.getOrNull(0)?.toLongOrNull()
            val reqEnd = rangeParts.getOrNull(1)?.toLongOrNull()
            if (reqStart != null) start = reqStart.coerceIn(0L, (fileLen - 1).coerceAtLeast(0L))
            if (reqEnd != null) end = reqEnd.coerceIn(start, (fileLen - 1).coerceAtLeast(0L))
            statusCode = 206
            statusText = "Partial Content"
        }

        val lengthToSend = (end - start + 1).coerceAtLeast(0L)
        val headers = buildString {
            append("HTTP/1.1 $statusCode $statusText\r\n")
            append("Content-Type: $mime\r\n")
            append("Content-Length: $lengthToSend\r\n")
            append("Accept-Ranges: bytes\r\n")
            if (statusCode == 206) {
                append("Content-Range: bytes $start-$end/$fileLen\r\n")
            }
            append("Access-Control-Allow-Origin: *\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(headers.toByteArray(Charsets.US_ASCII))
        out.flush()

        if (!isHeadOnly && lengthToSend > 0L) {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(start)
                val buffer = ByteArray(16 * 1024)
                var remaining = lengthToSend
                while (remaining > 0 && running.get()) {
                    val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                    val read = raf.read(buffer, 0, toRead)
                    if (read == -1) break
                    out.write(buffer, 0, read)
                    remaining -= read
                }
                out.flush()
            }
        }
    }

    private fun buildStateJson(proxyBaseUrl: String): String {
        val media = currentMedia
        val ext = when {
            media == null -> ""
            media.isAudioOnly -> ".mp3"
            media.streamUrl.lowercase().contains(".m3u8") -> ".m3u8"
            media.streamUrl.lowercase().contains(".mp4") -> ".mp4"
            media.isLive -> ".m3u8"
            else -> ".mp4"
        }
        val proxiedUrl = if (media != null) "$proxyBaseUrl/stream$ext?v=${stateVersion.get()}" else ""
        return JSONObject().apply {
            put("version", stateVersion.get())
            put("title", media?.title.orEmpty())
            put("subtitle", media?.subtitle.orEmpty())
            put("logoUrl", media?.logoUrl.orEmpty())
            put("streamUrl", proxiedUrl)
            put("directUrl", media?.streamUrl.orEmpty())
            put("mimeType", media?.inferredMimeType().orEmpty())
            put("isLive", media?.isLive ?: true)
            put("isAudio", media?.isAudioOnly ?: false)
            put("command", remoteCommand)
            put("volume", remoteVolume.toDouble())
            put("muted", remoteMuted)
        }.toString()
    }

    private fun extractQueryParam(query: String, key: String): String? {
        if (query.isBlank()) return null
        query.split("&").forEach { pair ->
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2 && parts[0] == key) {
                return runCatching { URLDecoder.decode(parts[1], "UTF-8") }.getOrDefault(parts[1])
            }
        }
        return null
    }

    private fun writeCorsPreflight(out: OutputStream) {
        val res = buildString {
            append("HTTP/1.1 204 No Content\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n")
            append("Access-Control-Allow-Headers: *\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(res.toByteArray(Charsets.US_ASCII))
        out.flush()
    }

    private fun writeTextResponse(out: OutputStream, status: Int, contentType: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val headers = buildString {
            append("HTTP/1.1 $status OK\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Cache-Control: no-cache\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(headers.toByteArray(Charsets.US_ASCII))
        out.write(bytes)
        out.flush()
    }

    private fun buildReceiverHtml(): String = """
        <!DOCTYPE html>
        <html lang="de">
        <head>
          <meta charset="UTF-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1.0" />
          <title>MaD IPTV public • Web-Cast Receiver</title>
          <script src="https://cdn.jsdelivr.net/npm/hls.js@1.5.15/dist/hls.min.js"></script>
          <style>
            * { box-sizing: border-box; margin: 0; padding: 0; }
            body {
              background: #050811; color: #F5F7FA;
              font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
              height: 100vh; width: 100vw; overflow: hidden;
              display: flex; flex-direction: column; align-items: center; justify-content: center;
            }
            #stage { position: relative; width: 100vw; height: 100vh; background: #000; display: flex; align-items: center; justify-content: center; }
            video { width: 100%; height: 100%; object-fit: contain; background: #000; }
            #hud {
              position: fixed; top: 18px; left: 22px; right: 22px;
              display: flex; justify-content: space-between; align-items: center;
              padding: 12px 18px; border-radius: 14px;
              background: rgba(9, 13, 26, 0.82); border: 1px solid rgba(245, 196, 81, 0.45);
              backdrop-filter: blur(8px); z-index: 10; transition: opacity 0.4s;
            }
            #hud.hidden { opacity: 0; pointer-events: none; }
            .title { font-size: 18px; font-weight: 700; color: #F5C451; }
            .sub { font-size: 12px; color: #9FB0C8; margin-top: 2px; }
            .badge {
              background: rgba(0, 212, 255, 0.16); color: #00D4FF;
              border: 1px solid rgba(0, 212, 255, 0.5);
              padding: 5px 10px; border-radius: 999px; font-size: 12px; font-weight: 700;
            }
            #startBtn {
              position: fixed; bottom: 28px; left: 50%; transform: translateX(-50%);
              background: #F5C451; color: #050811; border: none; border-radius: 12px;
              padding: 12px 24px; font-size: 15px; font-weight: 800; cursor: pointer;
              display: none; z-index: 20; box-shadow: 0 8px 28px rgba(0,0,0,0.5);
            }
            #waiting { text-align: center; padding: 24px; }
            #waiting h1 { color: #F5C451; font-size: 24px; margin-bottom: 10px; }
            #waiting p { color: #9FB0C8; font-size: 14px; }
          </style>
        </head>
        <body>
          <div id="stage">
            <div id="waiting">
              <h1>MaD IPTV public • Web-Cast</h1>
              <p>Warte auf aktiven Stream aus der App...</p>
            </div>
            <video id="video" controls autoplay playsinline style="display:none;"></video>
          </div>
          <div id="hud">
            <div>
              <div class="title" id="chTitle">MaD IPTV public</div>
              <div class="sub" id="chSub">Web-Cast Empfänger bereit</div>
            </div>
            <div class="badge" id="statusBadge">● VERBUNDEN</div>
          </div>
          <button id="startBtn">▶ Wiedergabe mit Ton starten</button>
          <script>
            const video = document.getElementById('video');
            const waiting = document.getElementById('waiting');
            const hud = document.getElementById('hud');
            const chTitle = document.getElementById('chTitle');
            const chSub = document.getElementById('chSub');
            const statusBadge = document.getElementById('statusBadge');
            const startBtn = document.getElementById('startBtn');
            let hls = null;
            let currentStream = '';
            let lastVersion = -1;
            let hudTimer = null;

            function flashHud() {
              hud.classList.remove('hidden');
              clearTimeout(hudTimer);
              hudTimer = setTimeout(() => hud.classList.add('hidden'), 5000);
            }

            document.addEventListener('mousemove', flashHud);
            document.addEventListener('click', flashHud);
            startBtn.addEventListener('click', () => {
              video.muted = false;
              video.play().catch(() => {});
              startBtn.style.display = 'none';
            });

            function loadStream(url, mime) {
              if (!url) return;
              waiting.style.display = 'none';
              video.style.display = 'block';
              if (hls) { hls.destroy(); hls = null; }
              const isHls = url.toLowerCase().includes('.m3u8') || (mime && mime.toLowerCase().includes('mpegurl'));
              if (isHls && window.Hls && Hls.isSupported()) {
                hls = new Hls({ enableWorker: true, lowLatencyMode: true });
                hls.loadSource(url);
                hls.attachMedia(video);
                hls.on(Hls.Events.MANIFEST_PARSED, tryPlay);
              } else {
                video.src = url;
                tryPlay();
              }
            }

            function tryPlay() {
              video.play().then(() => {
                startBtn.style.display = 'none';
              }).catch(() => {
                video.muted = true;
                video.play().catch(() => {});
                startBtn.style.display = 'block';
              });
            }

            async function pollState() {
              try {
                const res = await fetch('/api/state?t=' + Date.now(), { cache: 'no-store' });
                if (!res.ok) return;
                const s = await res.json();
                if (s.version !== lastVersion) {
                  lastVersion = s.version;
                  chTitle.textContent = s.title || 'MaD IPTV public';
                  chSub.textContent = s.subtitle || (s.isLive ? 'Live-Stream' : 'Video / Audio');
                  flashHud();
                }
                if (typeof s.volume === 'number') video.volume = Math.max(0, Math.min(1, s.volume));
                if (typeof s.muted === 'boolean' && startBtn.style.display === 'none') video.muted = s.muted;

                if (s.command === 'stop') {
                  statusBadge.textContent = '■ GESTOPPT';
                  video.pause();
                  return;
                }
                if (s.streamUrl && s.streamUrl !== currentStream) {
                  currentStream = s.streamUrl;
                  loadStream(s.streamUrl, s.mimeType);
                }
                if (s.command === 'pause') {
                  statusBadge.textContent = '❚❚ PAUSIERT';
                  video.pause();
                } else {
                  statusBadge.textContent = s.isLive ? '● LIVE' : '▶ WIEDERGABE';
                  if (video.paused && currentStream) video.play().catch(() => {});
                }
              } catch (e) {}
            }
            pollState();
            setInterval(pollState, 1800);
          </script>
        </body>
        </html>
    """.trimIndent()
}
