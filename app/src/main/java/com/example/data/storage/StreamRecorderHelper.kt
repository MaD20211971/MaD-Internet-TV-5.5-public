package com.example.data.storage

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Robust IPTV Stream Recorder.
 * Supports:
 * 1) Continuous HTTP live streams (MPEG-TS, MP4, FLV)
 * 2) HLS (.m3u8) live streams by polling and appending .ts/.mp4 chunks
 * 3) Automatic resolution of relative HLS segment and master playlist URLs
 * 4) No callTimeout limit, ensuring long recordings don't abort
 * 5) Saves directly as MP4 video in Downloads/IPTV
 */
object StreamRecorderHelper {

    private const val TAG = "StreamRecorderHelper"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS) // No timeout limit for live recording
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    sealed class RecordingResult {
        data class Success(val file: File, val totalBytes: Long, val durationSeconds: Long) : RecordingResult()
        data class Error(val message: String, val file: File? = null) : RecordingResult()
    }

    /**
     * Resolves a potentially relative URL against a base URL.
     */
    fun resolveUrl(baseUrl: String, candidate: String): String {
        val trimmed = candidate.trim()
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            return trimmed
        }
        return try {
            val baseUri = URI(baseUrl)
            baseUri.resolve(trimmed).toString()
        } catch (e: Exception) {
            val cleanBase = if (baseUrl.contains("?")) baseUrl.substringBefore("?") else baseUrl
            val separator = if (cleanBase.endsWith("/")) "" else "/"
            val prefix = if (trimmed.startsWith("/")) {
                val hostUri = URI(cleanBase)
                "${hostUri.scheme}://${hostUri.host}${if (hostUri.port != -1) ":${hostUri.port}" else ""}"
            } else {
                cleanBase.substringBeforeLast("/", cleanBase)
            }
            if (trimmed.startsWith("/")) "$prefix$trimmed" else "$prefix$separator$trimmed"
        }
    }

    /**
     * Records a live stream to targetFile.
     * Automatically differentiates between HLS playlist (.m3u8) and continuous direct streams.
     */
    suspend fun recordStream(
        context: Context,
        streamUrl: String,
        targetFile: File,
        recId: String,
        userAgent: String? = null,
        headers: Map<String, String> = emptyMap(),
        isRecordingActive: () -> Boolean,
        onProgressUpdate: suspend (totalBytes: Long, durationSeconds: Long) -> Unit
    ): RecordingResult = withContext(Dispatchers.IO) {
        var outputStream: OutputStream? = null
        val startTime = System.currentTimeMillis()
        var totalBytesWritten = 0L
        val effectiveUa = userAgent ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        var actualFile = targetFile

        try {
            val target = DownloadStorageHelper.openOutputStreamAndFile(context, targetFile, "video/mp4")
            outputStream = target.outputStream
            actualFile = target.actualFile

            // Probe stream URL to determine stream type (HLS vs Direct Stream)
            val initialRequestBuilder = Request.Builder()
                .url(streamUrl)
                .header("User-Agent", effectiveUa)
                .header("Accept", "*/*")
            for ((k, v) in headers) {
                initialRequestBuilder.header(k, v)
            }
            val initialRequest = initialRequestBuilder.build()

            val response: Response
            try {
                response = client.newCall(initialRequest).execute()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to stream $streamUrl: ${e.message}")
                return@withContext RecordingResult.Error("Verbindung zum Sender fehlgeschlagen: ${e.message}", actualFile)
            }

            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                return@withContext RecordingResult.Error("Server antwortete mit HTTP $code", actualFile)
            }

            val body = response.body
            if (body == null) {
                response.close()
                return@withContext RecordingResult.Error("Leere Antwort vom Server", actualFile)
            }

            val contentType = (response.header("Content-Type") ?: "").lowercase()
            val finalUrl = response.request.url.toString()

            // Read the first 2KB to inspect content
            val inputStream = body.byteStream()
            val peekBuffer = ByteArray(2048)
            val peekBytesRead = inputStream.read(peekBuffer)
            val isHlsByUrl = streamUrl.contains(".m3u8", ignoreCase = true) || finalUrl.contains(".m3u8", ignoreCase = true)
            val isHlsByHeader = contentType.contains("mpegurl") || contentType.contains("m3u8")
            val peekString = if (peekBytesRead > 0) String(peekBuffer, 0, peekBytesRead) else ""
            val isHlsByContent = peekString.contains("#EXTM3U")

            if (isHlsByUrl || isHlsByHeader || isHlsByContent) {
                // Close initial probe response and switch to HLS recording loop
                response.close()
                Log.i(TAG, "Detected HLS live stream. Starting HLS chunk recorder...")

                totalBytesWritten = recordHlsStream(
                    initialUrl = finalUrl,
                    initialContent = if (isHlsByContent) peekString else null,
                    outputStream = outputStream,
                    startTime = startTime,
                    userAgent = effectiveUa,
                    headers = headers,
                    isRecordingActive = isRecordingActive,
                    onProgressUpdate = onProgressUpdate
                )
            } else {
                // Continuous byte stream (MPEG-TS, MP4, FLV)
                Log.i(TAG, "Detected direct continuous stream. Streaming bytes...")

                // Write the initial peek buffer first
                if (peekBytesRead > 0) {
                    outputStream.write(peekBuffer, 0, peekBytesRead)
                    totalBytesWritten += peekBytesRead
                }

                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                var lastUpdate = System.currentTimeMillis()

                while (currentCoroutineContext().isActive && isRecordingActive()) {
                    bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) break
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesWritten += bytesRead

                    val now = System.currentTimeMillis()
                    if (now - lastUpdate >= 1000) {
                        lastUpdate = now
                        outputStream.flush()
                        val duration = ((now - startTime) / 1000).coerceAtLeast(1)
                        onProgressUpdate(totalBytesWritten, duration)
                    }
                }
                response.close()
            }

            outputStream.flush()
        } catch (e: Exception) {
            if (e !is kotlinx.coroutines.CancellationException) {
                Log.e(TAG, "Recording stream error: ${e.message}", e)
            }
        } finally {
            try { outputStream?.close() } catch (_: Exception) {}
        }

        // Validate final file size: if 0 bytes, no data arrived
        val fileLength = if (actualFile.exists()) actualFile.length() else totalBytesWritten
        val durationSec = ((System.currentTimeMillis() - startTime) / 1000).coerceAtLeast(1)

        if (fileLength == 0L) {
            Log.w(TAG, "Recording produced 0 byte file. Deleting target file.")
            try { actualFile.delete() } catch (_: Exception) {}
            return@withContext RecordingResult.Error("Aufnahme fehlgeschlagen: Der Sender lieferte keine Videodaten.", actualFile)
        }

        DownloadStorageHelper.scanFileForDownloads(context, actualFile)
        return@withContext RecordingResult.Success(actualFile, fileLength, durationSec)
    }

    /**
     * Downloads and appends HLS segments (.ts / .mp4) to outputStream in a continuous loop.
     */
    private suspend fun recordHlsStream(
        initialUrl: String,
        initialContent: String?,
        outputStream: OutputStream,
        startTime: Long,
        userAgent: String,
        headers: Map<String, String>,
        isRecordingActive: () -> Boolean,
        onProgressUpdate: suspend (totalBytes: Long, durationSeconds: Long) -> Unit
    ): Long {
        var currentPlaylistUrl = initialUrl
        var totalBytesWritten = 0L
        val downloadedSegments = LinkedHashSet<String>()
        var lastUpdate = System.currentTimeMillis()

        // If master playlist, resolve to media playlist
        val firstContent = initialContent ?: fetchText(currentPlaylistUrl, userAgent, headers)
        if (firstContent != null && firstContent.contains("#EXT-X-STREAM-INF")) {
            val mediaUrl = parseMasterPlaylist(currentPlaylistUrl, firstContent)
            if (mediaUrl != null) {
                currentPlaylistUrl = mediaUrl
                Log.i(TAG, "Resolved HLS Master playlist to media playlist: $currentPlaylistUrl")
            }
        }

        var pollIntervalMs = 2500L

        while (currentCoroutineContext().isActive && isRecordingActive()) {
            val playlistText = fetchText(currentPlaylistUrl, userAgent, headers)
            if (playlistText == null) {
                delay(2000L)
                continue
            }

            // Extract target duration if present
            val targetDurationMatch = Regex("#EXT-X-TARGETDURATION:(\\d+)").find(playlistText)
            if (targetDurationMatch != null) {
                val sec = targetDurationMatch.groupValues[1].toLongOrNull() ?: 3L
                pollIntervalMs = (sec * 800L).coerceIn(1500L, 5000L)
            }

            // Parse segment URLs
            val segments = parseMediaSegments(currentPlaylistUrl, playlistText)

            for (segUrl in segments) {
                if (!isRecordingActive() || !currentCoroutineContext().isActive) break

                if (downloadedSegments.add(segUrl)) {
                    // Prevent memory buildup in very long recordings
                    if (downloadedSegments.size > 800) {
                        val iterator = downloadedSegments.iterator()
                        repeat(200) { if (iterator.hasNext()) { iterator.next(); iterator.remove() } }
                    }

                    val segBytes = downloadAndAppendSegment(segUrl, outputStream, userAgent, headers)
                    totalBytesWritten += segBytes

                    val now = System.currentTimeMillis()
                    if (now - lastUpdate >= 1000) {
                        lastUpdate = now
                        outputStream.flush()
                        val duration = ((now - startTime) / 1000).coerceAtLeast(1)
                        onProgressUpdate(totalBytesWritten, duration)
                    }
                }
            }

            outputStream.flush()

            // Check if stream finished (VOD / end of live event)
            if (playlistText.contains("#EXT-X-ENDLIST")) {
                Log.i(TAG, "HLS stream reached #EXT-X-ENDLIST.")
                break
            }

            delay(pollIntervalMs)
        }

        return totalBytesWritten
    }

    private fun parseMasterPlaylist(masterUrl: String, content: String): String? {
        val lines = content.lines()
        for (i in lines.indices) {
            if (lines[i].startsWith("#EXT-X-STREAM-INF")) {
                for (j in (i + 1) until lines.size) {
                    val candidate = lines[j].trim()
                    if (candidate.isNotBlank() && !candidate.startsWith("#")) {
                        return resolveUrl(masterUrl, candidate)
                    }
                }
            }
        }
        return null
    }

    private fun parseMediaSegments(mediaPlaylistUrl: String, content: String): List<String> {
        val list = mutableListOf<String>()
        val lines = content.lines()
        var hasExtinf = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF")) {
                hasExtinf = true
            } else if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                list.add(resolveUrl(mediaPlaylistUrl, trimmed))
                hasExtinf = false
            }
        }
        return list
    }

    private fun downloadAndAppendSegment(
        segmentUrl: String,
        outputStream: OutputStream,
        userAgent: String,
        headers: Map<String, String>
    ): Long {
        var bytesWritten = 0L
        try {
            val reqBuilder = Request.Builder()
                .url(segmentUrl)
                .header("User-Agent", userAgent)
                .header("Accept", "*/*")
            for ((k, v) in headers) {
                reqBuilder.header(k, v)
            }
            val req = reqBuilder.build()

            val res = client.newCall(req).execute()
            if (res.isSuccessful) {
                res.body?.byteStream()?.use { input ->
                    val buf = ByteArray(32 * 1024)
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        outputStream.write(buf, 0, read)
                        bytesWritten += read
                    }
                }
            }
            res.close()
        } catch (e: Exception) {
            Log.w(TAG, "Failed downloading HLS segment $segmentUrl: ${e.message}")
        }
        return bytesWritten
    }

    private fun fetchText(
        url: String,
        userAgent: String,
        headers: Map<String, String>
    ): String? {
        return try {
            val reqBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
            for ((k, v) in headers) {
                reqBuilder.header(k, v)
            }
            val req = reqBuilder.build()
            val res = client.newCall(req).execute()
            if (res.isSuccessful) {
                val text = res.body?.string()
                res.close()
                text
            } else {
                res.close()
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
