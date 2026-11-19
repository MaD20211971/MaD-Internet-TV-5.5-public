package com.example.data.radio

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import com.example.data.storage.DownloadStorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Dedicated 1:1 audio stream recorder for Internet Radio (radio-browser.info & IPTV Radio).
 *
 * - Records MP3 streams natively as .mp3 in exact original source bitrate (e.g., 32 kbps stays 32 kbps,
 *   128 kbps stays 128 kbps) with zero transcoding overhead or artificial upsampling.
 * - Cleanly strips Shoutcast/Icecast ICY inline metadata blocks (icy-metaint) so recorded MP3 files
 *   never contain audio clicks/glitches, while exposing live StreamTitle updates.
 * - Handles AAC/OGG streams losslessly in their native bitrate/container when a station does not broadcast MP3.
 * - Resolves .m3u / .pls playlist wrapper URLs automatically.
 */
object RadioStreamRecorder {

    private const val TAG = "RadioStreamRecorder"
    const val RADIO_SUBFOLDER = "Radio"
    private const val USER_AGENT = "MaDIPTVPrivat/5.5 (Android Radio Recorder)"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    data class StreamAudioFormat(
        val extension: String,
        val mimeType: String,
        val codecLabel: String,
        val bitrateKbps: Int
    ) {
        fun qualityDescription(): String {
            return if (bitrateKbps > 0) "$codecLabel ${bitrateKbps} kbps" else codecLabel
        }
    }

    data class RecordingProgress(
        val totalBytes: Long,
        val durationSeconds: Long,
        val format: StreamAudioFormat,
        val currentSongTitle: String? = null
    )

    sealed class RadioRecordingResult {
        data class Success(
            val file: File,
            val totalBytes: Long,
            val durationSeconds: Long,
            val format: StreamAudioFormat,
            val lastSongTitle: String?
        ) : RadioRecordingResult()

        data class Error(
            val message: String,
            val file: File? = null
        ) : RadioRecordingResult()
    }

    fun getRadioDownloadsDir(context: Context): File {
        val baseDir = DownloadStorageHelper.getDownloadsDir(context)
        val radioDir = File(baseDir, RADIO_SUBFOLDER)
        if (!radioDir.exists()) {
            radioDir.mkdirs()
        }
        return if (radioDir.exists() && radioDir.isDirectory) {
            radioDir
        } else {
            val fallbackBase = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            val fallback = File(fallbackBase, "${DownloadStorageHelper.SUBFOLDER}/$RADIO_SUBFOLDER")
            fallback.mkdirs()
            fallback
        }
    }

    fun getDisplayRadioFolderPath(): String = "Downloads/${DownloadStorageHelper.SUBFOLDER}/$RADIO_SUBFOLDER"

    fun detectAudioFormat(
        contentType: String?,
        streamUrl: String,
        hintCodec: String?,
        icyBitrateHeader: String?,
        hintBitrateKbps: Int
    ): StreamAudioFormat {
        val ct = (contentType ?: "").lowercase(Locale.US)
        val url = streamUrl.lowercase(Locale.US)
        val hint = (hintCodec ?: "").uppercase(Locale.US)
        val detectedBitrate = icyBitrateHeader?.trim()?.toIntOrNull()?.takeIf { it > 0 }
            ?: hintBitrateKbps.coerceAtLeast(0)

        return when {
            ct.contains("audio/mpeg") || ct.contains("audio/mp3") || hint.contains("MP3") || url.contains(".mp3") -> {
                StreamAudioFormat(
                    extension = "mp3",
                    mimeType = "audio/mpeg",
                    codecLabel = "MP3",
                    bitrateKbps = detectedBitrate
                )
            }
            ct.contains("aac") || ct.contains("audio/mp4") || hint.contains("AAC") || url.contains(".aac") -> {
                StreamAudioFormat(
                    extension = "aac",
                    mimeType = "audio/aac",
                    codecLabel = if (hint.contains("AAC+") || ct.contains("aacp")) "AAC+" else "AAC",
                    bitrateKbps = detectedBitrate
                )
            }
            ct.contains("ogg") || ct.contains("opus") || hint.contains("OGG") || hint.contains("OPUS") || url.contains(".ogg") -> {
                StreamAudioFormat(
                    extension = "ogg",
                    mimeType = "audio/ogg",
                    codecLabel = if (hint.contains("OPUS") || ct.contains("opus")) "OPUS" else "OGG",
                    bitrateKbps = detectedBitrate
                )
            }
            ct.contains("flac") || hint.contains("FLAC") || url.contains(".flac") -> {
                StreamAudioFormat(
                    extension = "flac",
                    mimeType = "audio/flac",
                    codecLabel = "FLAC",
                    bitrateKbps = detectedBitrate
                )
            }
            else -> {
                StreamAudioFormat(
                    extension = "mp3",
                    mimeType = "audio/mpeg",
                    codecLabel = "MP3",
                    bitrateKbps = detectedBitrate
                )
            }
        }
    }

    /**
     * Parses ICY metadata block (e.g. `StreamTitle='Böhse Onkelz - Auf gute Freunde';StreamUrl='';`)
     * and returns the clean track title if present.
     */
    internal fun parseIcyStreamTitle(metaBytes: ByteArray): String? {
        if (metaBytes.isEmpty()) return null
        val raw = String(metaBytes, Charsets.UTF_8).trimEnd('\u0000')
        val marker = "StreamTitle='"
        val start = raw.indexOf(marker, ignoreCase = true)
        if (start < 0) return null
        val after = raw.substring(start + marker.length)
        val end = after.indexOf("';")
        val title = (if (end >= 0) after.substring(0, end) else after.substringBefore("'")).trim()
        return title.takeIf { it.isNotBlank() }
    }

    /**
     * Demuxes pure audio bytes from an ICY-interleaved stream, writing ONLY audio payload to [output]
     * and invoking [onTitleChanged] whenever a non-empty ICY StreamTitle arrives.
     */
    internal suspend fun copyWithIcyDemux(
        input: InputStream,
        output: OutputStream,
        icyMetaInt: Int,
        isRecordingActive: () -> Boolean,
        onChunkWritten: suspend (deltaBytes: Long, latestTitle: String?) -> Unit
    ): Pair<Long, String?> {
        var totalAudioBytes = 0L
        var latestTitle: String? = null

        if (icyMetaInt <= 0) {
            val buffer = ByteArray(16 * 1024)
            while (currentCoroutineContext().isActive && isRecordingActive()) {
                val read = input.read(buffer)
                if (read == -1) break
                if (read > 0) {
                    output.write(buffer, 0, read)
                    totalAudioBytes += read
                    onChunkWritten(read.toLong(), latestTitle)
                }
            }
            return Pair(totalAudioBytes, latestTitle)
        }

        val audioChunkBuf = ByteArray(minOf(icyMetaInt, 16 * 1024))
        var bytesUntilMeta = icyMetaInt

        while (currentCoroutineContext().isActive && isRecordingActive()) {
            val toRead = minOf(bytesUntilMeta, audioChunkBuf.size)
            val read = input.read(audioChunkBuf, 0, toRead)
            if (read == -1) break
            if (read > 0) {
                output.write(audioChunkBuf, 0, read)
                totalAudioBytes += read
                bytesUntilMeta -= read
                onChunkWritten(read.toLong(), latestTitle)
            }

            if (bytesUntilMeta == 0) {
                val lengthByte = input.read()
                if (lengthByte == -1) break
                val metaLength = lengthByte * 16
                if (metaLength > 0) {
                    val metaBytes = ByteArray(metaLength)
                    var offset = 0
                    while (offset < metaLength) {
                        val r = input.read(metaBytes, offset, metaLength - offset)
                        if (r == -1) break
                        offset += r
                    }
                    parseIcyStreamTitle(metaBytes)?.let { title ->
                        if (title != latestTitle) {
                            latestTitle = title
                            onChunkWritten(0L, latestTitle)
                        }
                    }
                }
                bytesUntilMeta = icyMetaInt
            }
        }

        return Pair(totalAudioBytes, latestTitle)
    }

    /**
     * Records a live radio stream directly in its original quality/bitrate.
     */
    suspend fun recordRadioStream(
        context: Context,
        stationName: String,
        streamUrl: String,
        hintCodec: String? = null,
        hintBitrateKbps: Int = 0,
        isRecordingActive: () -> Boolean,
        onProgressUpdate: suspend (RecordingProgress) -> Unit
    ): RadioRecordingResult = withContext(Dispatchers.IO) {
        val resolvedUrl = resolveDirectAudioUrl(streamUrl)
        val startTime = System.currentTimeMillis()
        var outputStream: OutputStream? = null
        var actualFile: File? = null
        var lastTitle: String? = null

        val request = Request.Builder()
            .url(resolvedUrl)
            .header("User-Agent", USER_AGENT)
            .header("Icy-MetaData", "1")
            .header("Accept", "*/*")
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            Log.e(TAG, "Radio stream connect failed: ${e.message}")
            return@withContext RadioRecordingResult.Error("Verbindung zum Radiosender fehlgeschlagen: ${e.message}")
        }

        try {
            if (!response.isSuccessful) {
                val code = response.code
                return@withContext RadioRecordingResult.Error("Radioserver antwortete mit HTTP $code")
            }

            val body = response.body
                ?: return@withContext RadioRecordingResult.Error("Leere Antwort vom Radioserver")

            val contentType = response.header("Content-Type")
            val icyBr = response.header("icy-br")
            val icyMetaInt = response.header("icy-metaint")?.trim()?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val finalUrl = response.request.url.toString()

            val format = detectAudioFormat(
                contentType = contentType,
                streamUrl = finalUrl,
                hintCodec = hintCodec,
                icyBitrateHeader = icyBr,
                hintBitrateKbps = hintBitrateKbps
            )

            val radioDir = getRadioDownloadsDir(context)
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val bitrateSuffix = if (format.bitrateKbps > 0) "_${format.bitrateKbps}k" else ""
            val fileName = DownloadStorageHelper.sanitizeFileName(
                rawName = "${stationName}${bitrateSuffix}_$timestamp",
                extension = format.extension
            )
            val targetFile = File(radioDir, fileName)

            outputStream = try {
                targetFile.parentFile?.mkdirs()
                actualFile = targetFile
                FileOutputStream(targetFile)
            } catch (e: Exception) {
                Log.w(TAG, "Direct Radio folder write failed (${e.message}), using app-external Radio folder")
                val fallbackDir = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
                    "${DownloadStorageHelper.SUBFOLDER}/$RADIO_SUBFOLDER"
                )
                fallbackDir.mkdirs()
                val fallbackFile = File(fallbackDir, fileName)
                actualFile = fallbackFile
                FileOutputStream(fallbackFile)
            }

            var totalBytes = 0L
            var lastNotifyTime = 0L

            onProgressUpdate(
                RecordingProgress(
                    totalBytes = 0L,
                    durationSeconds = 0L,
                    format = format,
                    currentSongTitle = null
                )
            )

            val (written, title) = copyWithIcyDemux(
                input = body.byteStream(),
                output = outputStream,
                icyMetaInt = icyMetaInt,
                isRecordingActive = isRecordingActive
            ) { delta, latestTitle ->
                totalBytes += delta
                lastTitle = latestTitle
                val now = System.currentTimeMillis()
                if (now - lastNotifyTime >= 1000L || (delta == 0L && latestTitle != null)) {
                    lastNotifyTime = now
                    outputStream.flush()
                    val dur = ((now - startTime) / 1000L).coerceAtLeast(1L)
                    onProgressUpdate(
                        RecordingProgress(
                            totalBytes = totalBytes,
                            durationSeconds = dur,
                            format = format,
                            currentSongTitle = latestTitle
                        )
                    )
                }
            }

            outputStream.flush()
            lastTitle = title ?: lastTitle

            val finalFile = actualFile
            val fileSize = if (finalFile != null && finalFile.exists()) finalFile.length() else written
            val durationSec = ((System.currentTimeMillis() - startTime) / 1000L).coerceAtLeast(1L)

            if (finalFile == null || fileSize <= 0L) {
                try { finalFile?.delete() } catch (_: Exception) {}
                return@withContext RadioRecordingResult.Error(
                    "Aufnahme fehlgeschlagen: Der Radiosender lieferte keine Audiodaten.",
                    finalFile
                )
            }

            scanAudioFile(context, finalFile, format.mimeType)
            RadioRecordingResult.Success(
                file = finalFile,
                totalBytes = fileSize,
                durationSeconds = durationSec,
                format = format,
                lastSongTitle = lastTitle
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) {
                val finalFile = actualFile
                val size = if (finalFile != null && finalFile.exists()) finalFile.length() else 0L
                val dur = ((System.currentTimeMillis() - startTime) / 1000L).coerceAtLeast(1L)
                val fallbackFmt = detectAudioFormat(null, resolvedUrl, hintCodec, null, hintBitrateKbps)
                if (finalFile != null && size > 0L) {
                    scanAudioFile(context, finalFile, fallbackFmt.mimeType)
                    return@withContext RadioRecordingResult.Success(finalFile, size, dur, fallbackFmt, lastTitle)
                }
            }
            Log.e(TAG, "Radio recording error: ${e.message}", e)
            RadioRecordingResult.Error("Aufnahme-Fehler: ${e.message}", actualFile)
        } finally {
            try { outputStream?.close() } catch (_: Exception) {}
            try { response.close() } catch (_: Exception) {}
        }
    }

    /**
     * Resolves simple .m3u / .pls playlist wrapper URLs to the direct HTTP audio stream URL.
     */
    internal fun resolveDirectAudioUrl(rawUrl: String): String {
        val lower = rawUrl.lowercase(Locale.US)
        val looksLikePlaylist = lower.endsWith(".pls") || lower.endsWith(".m3u") ||
            lower.contains(".pls?") || lower.contains(".m3u?")
        if (!looksLikePlaylist) return rawUrl

        return try {
            val req = Request.Builder()
                .url(rawUrl)
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return rawUrl
                val text = res.body?.string().orEmpty()
                for (line in text.lines()) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("File1=", ignoreCase = true)) {
                        val candidate = trimmed.substringAfter("=").trim()
                        if (candidate.startsWith("http", ignoreCase = true)) return candidate
                    }
                    if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                        return trimmed
                    }
                }
                rawUrl
            }
        } catch (_: Exception) {
            rawUrl
        }
    }

    private fun scanAudioFile(context: Context, file: File, mimeType: String) {
        try {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf(mimeType, "audio/*"),
                null
            )
        } catch (e: Exception) {
            Log.w(TAG, "Audio MediaScanner scan failed: ${e.message}")
        }
    }
}
