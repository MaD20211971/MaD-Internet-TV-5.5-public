package com.example.androidtvlight.data.recorder

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.androidtvlight.domain.model.RecordedVideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class RecordingStatus(
    val isRecording: Boolean = false,
    val recordingChannelId: String? = null,
    val recordingChannelName: String? = null,
    val elapsedSeconds: Long = 0L,
    val bytesWritten: Long = 0L,
    val targetFilePath: String = "Download/MaDIPTV",
    val lastSavedFileMessage: String? = null
)

/**
 * Live-TV Stream Recorder & Recording Manager:
 * - Captures the active HLS live stream and writes it directly as an `.mp4` video file into `Download/MaDIPTV`.
 * - Lists all saved `.mp4` recordings from `Download/MaDIPTV` (MediaStore + filesystem fallback).
 * - Supports deleting individual `.mp4` recordings.
 */
class TvStreamRecorder(
    private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recordingJob: Job? = null
    private var timerJob: Job? = null

    @Volatile
    private var activeMediaStoreUri: Uri? = null

    @Volatile
    private var activeLegacyFile: File? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _status = MutableStateFlow(RecordingStatus())
    val status: StateFlow<RecordingStatus> = _status.asStateFlow()

    private val _recordings = MutableStateFlow<List<RecordedVideoItem>>(emptyList())
    val recordings: StateFlow<List<RecordedVideoItem>> = _recordings.asStateFlow()

    init {
        refreshRecordings()
    }

    fun startRecording(
        channelId: String,
        channelName: String,
        streamUrl: String
    ) {
        if (_status.value.isRecording) {
            stopRecording()
            return
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.GERMANY).format(Date())
        val sanitizedChannel = channelName
            .replace(Regex("[^a-zA-Z0-9äöüÄÖÜß_-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
        val fileName = "${sanitizedChannel}_$timestamp.mp4"
        val displayPath = "Download/MaDIPTV/$fileName"

        _status.update {
            RecordingStatus(
                isRecording = true,
                recordingChannelId = channelId,
                recordingChannelName = channelName,
                elapsedSeconds = 0L,
                bytesWritten = 0L,
                targetFilePath = displayPath,
                lastSavedFileMessage = "Aufnahme gestartet: $displayPath"
            )
        }

        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive && _status.value.isRecording) {
                delay(1_000L)
                _status.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1L) }
            }
        }

        recordingJob?.cancel()
        recordingJob = scope.launch {
            var outputStream: OutputStream? = null
            var mediaStoreUri: Uri? = null
            var legacyFile: File? = null

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                        put(
                            MediaStore.MediaColumns.RELATIVE_PATH,
                            "${Environment.DIRECTORY_DOWNLOADS}/MaDIPTV"
                        )
                    }
                    val resolver = context.contentResolver
                    mediaStoreUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    activeMediaStoreUri = mediaStoreUri
                    if (mediaStoreUri != null) {
                        outputStream = resolver.openOutputStream(mediaStoreUri)
                    }
                }

                if (outputStream == null) {
                    val downloadsRoot = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS
                    )
                    val targetDir = File(downloadsRoot, "MaDIPTV")
                    if (!targetDir.exists()) {
                        targetDir.mkdirs()
                    }
                    legacyFile = File(targetDir, fileName)
                    activeLegacyFile = legacyFile
                    outputStream = FileOutputStream(legacyFile)
                }

                refreshRecordingsInternal()

                val seenSegments = LinkedHashSet<String>()
                var currentVariantUrl = streamUrl

                while (isActive && _status.value.isRecording) {
                    val playlistText = fetchText(currentVariantUrl)
                    if (playlistText.isBlank()) {
                        delay(1_500L)
                        continue
                    }

                    val lines = playlistText.lines().map { it.trim() }
                    val isMasterPlaylist = lines.any { it.startsWith("#EXT-X-STREAM-INF") }

                    if (isMasterPlaylist) {
                        val variantLine = lines.firstOrNull { line ->
                            line.isNotEmpty() && !line.startsWith("#")
                        }
                        if (variantLine != null) {
                            currentVariantUrl = resolveRelativeUrl(currentVariantUrl, variantLine)
                            continue
                        }
                    }

                    // Check for #EXT-X-MAP (fMP4 initialization segment)
                    lines.filter { it.startsWith("#EXT-X-MAP:URI=") }.forEach { mapLine ->
                        val uriPart = mapLine.substringAfter("URI=\"").substringBefore("\"")
                        if (uriPart.isNotBlank()) {
                            val fullInitUrl = resolveRelativeUrl(currentVariantUrl, uriPart)
                            if (seenSegments.add(fullInitUrl)) {
                                downloadSegmentToStream(fullInitUrl, outputStream)
                            }
                        }
                    }

                    val segmentUrls = lines
                        .filter { it.isNotEmpty() && !lineStartsWithComment(it) }
                        .map { resolveRelativeUrl(currentVariantUrl, it) }

                    val newSegments = segmentUrls.filter { !seenSegments.contains(it) }
                    val segmentsToDownload = if (seenSegments.isEmpty() && newSegments.size > 3) {
                        newSegments.takeLast(3)
                    } else {
                        newSegments
                    }

                    for (segUrl in segmentsToDownload) {
                        if (!isActive || !_status.value.isRecording) break
                        seenSegments.add(segUrl)
                        if (seenSegments.size > 200) {
                            val first = seenSegments.first()
                            seenSegments.remove(first)
                        }
                        downloadSegmentToStream(segUrl, outputStream)
                    }

                    delay(2_000L)
                }
            } catch (_: Exception) {
                // Stream closed or interrupted cleanly
            } finally {
                runCatching { outputStream?.flush() }
                runCatching { outputStream?.close() }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mediaStoreUri != null) {
                    runCatching {
                        val completedValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.IS_PENDING, 0)
                        }
                        context.contentResolver.update(mediaStoreUri, completedValues, null, null)
                    }
                } else if (legacyFile != null) {
                    runCatching {
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(legacyFile.absolutePath),
                            arrayOf("video/mp4"),
                            null
                        )
                    }
                }
                activeMediaStoreUri = null
                activeLegacyFile = null
                refreshRecordingsInternal()
            }
        }
    }

    fun stopRecording() {
        val current = _status.value
        if (!current.isRecording) return

        timerJob?.cancel()
        recordingJob?.cancel()

        val sizeMb = String.format(Locale.GERMANY, "%.2f MB", current.bytesWritten / (1024f * 1024f))
        _status.update {
            it.copy(
                isRecording = false,
                lastSavedFileMessage = "Gespeichert in ${current.targetFilePath} ($sizeMb)"
            )
        }

        scope.launch {
            delay(350L)
            refreshRecordingsInternal()
        }
    }

    fun clearSavedMessage() {
        _status.update { it.copy(lastSavedFileMessage = null) }
    }

    fun refreshRecordings() {
        scope.launch {
            refreshRecordingsInternal()
        }
    }

    fun deleteRecording(item: RecordedVideoItem, onDeleted: (() -> Unit)? = null) {
        scope.launch {
            runCatching {
                if (item.playbackUri.startsWith("content://")) {
                    context.contentResolver.delete(Uri.parse(item.playbackUri), null, null)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching {
                    context.contentResolver.delete(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                        arrayOf(item.fileName)
                    )
                }
            }
            runCatching {
                val downloadsRoot = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )
                val targetFile = File(File(downloadsRoot, "MaDIPTV"), item.fileName)
                if (targetFile.exists()) {
                    targetFile.delete()
                }
            }
            refreshRecordingsInternal()
            onDeleted?.invoke()
        }
    }

    private suspend fun refreshRecordingsInternal() = withContext(Dispatchers.IO) {
        val byFileName = LinkedHashMap<String, RecordedVideoItem>()
        val downloadsRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val madIptvDir = File(downloadsRoot, "MaDIPTV")

        // 1. Query MediaStore.Downloads on Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val projection = arrayOf(
                    MediaStore.Downloads._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.DATE_MODIFIED,
                    MediaStore.MediaColumns.DATE_ADDED,
                    MediaStore.MediaColumns.RELATIVE_PATH
                )
                val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"
                context.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    sortOrder
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    val modCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                    val addCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                    val pathCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)

                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameCol).orEmpty()
                        val relPath = cursor.getString(pathCol).orEmpty()
                        if (!name.endsWith(".mp4", ignoreCase = true)) continue
                        if (!relPath.contains("MaDIPTV", ignoreCase = true)) continue

                        val id = cursor.getLong(idCol)
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            id
                        ).toString()

                        val diskFile = File(madIptvDir, name)
                        val rawSize = cursor.getLong(sizeCol)
                        val effectiveSize = when {
                            rawSize > 0L -> rawSize
                            diskFile.exists() && diskFile.length() > 0L -> diskFile.length()
                            _status.value.isRecording && _status.value.targetFilePath.endsWith(name) ->
                                _status.value.bytesWritten
                            else -> 0L
                        }

                        val dateSec = cursor.getLong(modCol).takeIf { it > 0L } ?: cursor.getLong(addCol)
                        val createdMillis = if (dateSec > 0L) dateSec * 1000L else System.currentTimeMillis()

                        byFileName[name] = createRecordedVideoItem(
                            id = "ms_$id",
                            fileName = name,
                            playbackUri = contentUri,
                            sizeBytes = effectiveSize,
                            createdAtMillis = createdMillis
                        )
                    }
                }
            }
        }

        // 2. Also scan Download/MaDIPTV directory directly for any MP4 files
        runCatching {
            if (madIptvDir.exists() && madIptvDir.isDirectory) {
                val files = madIptvDir.listFiles { f ->
                    f.isFile && f.name.endsWith(".mp4", ignoreCase = true)
                }.orEmpty()

                for (file in files) {
                    val existing = byFileName[file.name]
                    val fileSize = file.length().coerceAtLeast(existing?.sizeBytes ?: 0L)
                    if (existing == null) {
                        val createdMillis = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
                        byFileName[file.name] = createRecordedVideoItem(
                            id = "file_${file.name.hashCode()}",
                            fileName = file.name,
                            playbackUri = Uri.fromFile(file).toString(),
                            sizeBytes = fileSize,
                            createdAtMillis = createdMillis
                        )
                    } else if (existing.sizeBytes == 0L && fileSize > 0L) {
                        byFileName[file.name] = existing.copy(
                            sizeBytes = fileSize,
                            formattedSize = formatSizeBytes(fileSize)
                        )
                    }
                }
            }
        }

        val sorted = byFileName.values.sortedByDescending { it.createdAtMillis }
        _recordings.value = sorted
    }

    private fun createRecordedVideoItem(
        id: String,
        fileName: String,
        playbackUri: String,
        sizeBytes: Long,
        createdAtMillis: Long
    ): RecordedVideoItem {
        val rawTitle = fileName
            .removeSuffix(".mp4")
            .removeSuffix(".MP4")
            .replace(Regex("_\\d{8}_\\d{6}$"), "")
            .replace("_", " ")
            .trim()
            .ifBlank { fileName }

        val dateFormat = SimpleDateFormat("dd.MM.yyyy • HH:mm 'Uhr'", Locale.GERMANY)
        val formattedDate = dateFormat.format(Date(createdAtMillis))

        return RecordedVideoItem(
            id = id,
            fileName = fileName,
            channelTitle = rawTitle,
            playbackUri = playbackUri,
            displayPath = "Download/MaDIPTV/$fileName",
            sizeBytes = sizeBytes,
            createdAtMillis = createdAtMillis,
            formattedDate = formattedDate,
            formattedSize = formatSizeBytes(sizeBytes)
        )
    }

    private fun formatSizeBytes(bytes: Long): String {
        return when {
            bytes >= 1024L * 1024L -> String.format(
                Locale.GERMANY,
                "%.2f MB",
                bytes / (1024f * 1024f)
            )
            bytes >= 1024L -> String.format(
                Locale.GERMANY,
                "%d KB",
                bytes / 1024L
            )
            else -> "$bytes B"
        }
    }

    private fun lineStartsWithComment(line: String): Boolean = line.startsWith("#")

    private suspend fun fetchText(url: String): String = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(url)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                )
                .header("Referer", "https://www.2ix2.com/")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
            }
        }.getOrDefault("")
    }

    private suspend fun downloadSegmentToStream(
        segmentUrl: String,
        outputStream: OutputStream
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(segmentUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                )
                .header("Referer", "https://www.2ix2.com/")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bodyStream = resp.body?.byteStream() ?: return@use
                    val buffer = ByteArray(16 * 1024)
                    var read: Int
                    var totalAdded = 0L
                    while (bodyStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                        totalAdded += read
                    }
                    outputStream.flush()
                    _status.update { it.copy(bytesWritten = it.bytesWritten + totalAdded) }
                }
            }
        }
    }

    private fun resolveRelativeUrl(baseUrl: String, relativeOrAbsolute: String): String {
        return runCatching {
            if (relativeOrAbsolute.startsWith("http://") || relativeOrAbsolute.startsWith("https://")) {
                relativeOrAbsolute
            } else {
                URI(baseUrl).resolve(relativeOrAbsolute).toString()
            }
        }.getOrDefault(relativeOrAbsolute)
    }
}
