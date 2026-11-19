package com.example.data.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import com.example.data.storage.DownloadStorageHelper
import java.io.File
import java.io.OutputStream

/**
 * Singleton sink that receives media bytes directly from the active playback connection.
 * This allows recording the stream currently being watched WITHOUT opening a second connection,
 * perfectly adhering to IPTV portals that permit only 1 concurrent connection.
 */
object LiveRecordingSink {
    private const val TAG = "LiveRecordingSink"

    private var outputStream: OutputStream? = null
    private var currentFile: File? = null
    private var recordingStartTime: Long = 0L
    private var totalBytesWritten: Long = 0L
    private var lastProgressTime: Long = 0L
    private var progressListener: ((bytes: Long, durationSec: Long) -> Unit)? = null
    private val lock = Any()

    val isRecordingActive: Boolean
        get() = synchronized(lock) { outputStream != null }

    val currentBytes: Long
        get() = synchronized(lock) { totalBytesWritten }

    fun startRecording(
        context: Context,
        targetFile: File,
        onProgress: (bytes: Long, durationSec: Long) -> Unit
    ): File? {
        synchronized(lock) {
            stopRecordingInternal(context)
            return try {
                val target = DownloadStorageHelper.openOutputStreamAndFile(context, targetFile, "video/mp4")
                currentFile = target.actualFile
                recordingStartTime = System.currentTimeMillis()
                totalBytesWritten = 0L
                lastProgressTime = recordingStartTime
                progressListener = onProgress
                outputStream = target.outputStream
                Log.i(TAG, "Live recording started concurrently with playback: ${target.actualFile.absolutePath}")
                target.actualFile
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start live recording stream: ${e.message}", e)
                outputStream = null
                currentFile = null
                null
            }
        }
    }

    fun write(buffer: ByteArray, offset: Int, length: Int) {
        synchronized(lock) {
            val stream = outputStream ?: return
            if (length <= 0) return
            try {
                stream.write(buffer, offset, length)
                totalBytesWritten += length

                val now = System.currentTimeMillis()
                if (now - lastProgressTime >= 1000L) {
                    lastProgressTime = now
                    try { stream.flush() } catch (_: Exception) {}
                    val durationSec = ((now - recordingStartTime) / 1000L).coerceAtLeast(1)
                    val bytes = totalBytesWritten
                    progressListener?.invoke(bytes, durationSec)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error writing to recording stream: ${e.message}")
            }
        }
    }

    fun stopRecording(context: Context): Pair<File?, Long> {
        synchronized(lock) {
            return stopRecordingInternal(context)
        }
    }

    private fun stopRecordingInternal(context: Context): Pair<File?, Long> {
        val file = currentFile
        val bytes = totalBytesWritten
        try {
            outputStream?.flush()
            outputStream?.close()
        } catch (_: Exception) {}
        outputStream = null
        currentFile = null
        progressListener = null

        if (file != null && file.exists() && bytes > 0L) {
            DownloadStorageHelper.scanFileForDownloads(context, file)
            Log.i(TAG, "Live recording finalized: ${file.name} ($bytes bytes)")
        } else if (file != null && file.exists() && bytes == 0L) {
            try { file.delete() } catch (_: Exception) {}
            Log.w(TAG, "Live recording discarded due to zero data")
        }
        return Pair(file, bytes)
    }
}

/**
 * An HttpDataSource decorator that taps the media byte stream of the active ExoPlayer instance.
 * Media chunks are forwarded to [LiveRecordingSink], while HLS playlist manifests (#EXTM3U)
 * and HTML error pages are excluded from being written to the video file.
 */
@OptIn(UnstableApi::class)
class LiveRecordingHttpDataSource(
    private val upstream: HttpDataSource,
    private val sink: LiveRecordingSink = LiveRecordingSink
) : HttpDataSource by upstream {

    private var isMediaChunk: Boolean = false
    private var checkedFirstBytes: Boolean = false

    override fun open(dataSpec: DataSpec): Long {
        val uriStr = dataSpec.uri.toString()
        val isM3u8 = uriStr.contains(".m3u8", ignoreCase = true) || uriStr.contains("type=m3u", ignoreCase = true)
        isMediaChunk = !isM3u8
        checkedFirstBytes = false
        return upstream.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val bytesRead = upstream.read(buffer, offset, length)
        if (bytesRead > 0) {
            if (!checkedFirstBytes) {
                checkedFirstBytes = true
                if (bytesRead >= 7) {
                    val prefix = String(buffer, offset, minOf(bytesRead, 32))
                    if (prefix.startsWith("#EXTM3U") || prefix.startsWith("<!DOCTYPE", ignoreCase = true) || prefix.startsWith("<html", ignoreCase = true)) {
                        isMediaChunk = false
                    }
                }
            }
            if (isMediaChunk && sink.isRecordingActive) {
                sink.write(buffer, offset, bytesRead)
            }
        }
        return bytesRead
    }

    override fun close() {
        upstream.close()
    }
}
