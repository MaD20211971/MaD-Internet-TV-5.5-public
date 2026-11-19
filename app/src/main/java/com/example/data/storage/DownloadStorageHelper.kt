package com.example.data.storage

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DownloadStorageHelper {

    private const val TAG = "DownloadStorageHelper"
    const val SUBFOLDER = "MaDIPTVPublic"

    /**
     * Checks if storage write permissions are granted.
     * On Android 11+ (API 30+), checks Environment.isExternalStorageManager() or legacy write permission.
     * On Android 10 and below, checks WRITE_EXTERNAL_STORAGE.
     */
    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager() || hasLegacyStoragePermission(context)
        } else {
            hasLegacyStoragePermission(context)
        }
    }

    /**
     * Checks legacy storage permissions (WRITE_EXTERNAL_STORAGE).
     */
    fun hasLegacyStoragePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Guides the user to grant storage write access.
     */
    fun requestStoragePermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                try {
                    val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(fallback)
                    return
                } catch (ex: Exception) {
                    Log.w(TAG, "Cannot launch manage storage permission: ${ex.message}")
                }
            }
        }

        if (context is Activity) {
            ActivityCompat.requestPermissions(
                context,
                arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ),
                2001
            )
        } else {
            try {
                val appSettingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(appSettingsIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Cannot launch app settings: ${e.message}")
            }
        }
    }

    data class OutputTarget(val outputStream: OutputStream, val actualFile: File)

    /**
     * Resolves or creates the primary Downloads folder (e.g. /storage/emulated/0/Download/IPTV).
     * Creates the directory if it does not exist. Tests real writability to prevent Scoped Storage failures.
     */
    fun getDownloadsDir(context: Context): File {
        // 1. Primary: Standard public Downloads/IPTV directory if permission is granted and writable
        if (hasStoragePermission(context)) {
            try {
                val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val iptvDir = File(publicDownloads, SUBFOLDER)
                if (!iptvDir.exists()) {
                    iptvDir.mkdirs()
                }
                if (iptvDir.exists()) {
                    // Test real write ability with a temporary probe file
                    val testFile = File(iptvDir, ".write_probe_${System.currentTimeMillis()}")
                    if (testFile.createNewFile()) {
                        testFile.delete()
                        return iptvDir
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Public Downloads folder creation failed or not directly writable: ${e.message}")
            }

            // 2. Direct path check /storage/emulated/0/Download/IPTV
            try {
                val direct = File("/storage/emulated/0/Download", SUBFOLDER)
                if (!direct.exists()) {
                    direct.mkdirs()
                }
                if (direct.exists()) {
                    val testFile = File(direct, ".write_probe_${System.currentTimeMillis()}")
                    if (testFile.createNewFile()) {
                        testFile.delete()
                        return direct
                    }
                }
            } catch (_: Exception) {}
        }

        // 3. App-specific external Downloads directory (always 100% accessible and writable without permissions)
        val appDownloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val fallbackDir = File(appDownloads, SUBFOLDER)
        if (!fallbackDir.exists()) {
            fallbackDir.mkdirs()
        }
        return fallbackDir
    }

    /**
     * Opens an OutputStream for writing to target file, returning both the stream and the actual File
     * that is being written to (accounting for Scoped Storage fallback if needed).
     */
    fun openOutputStreamAndFile(context: Context, targetFile: File, mimeType: String = "video/mp4"): OutputTarget {
        try {
            targetFile.parentFile?.mkdirs()
            val stream = FileOutputStream(targetFile)
            return OutputTarget(stream, targetFile)
        } catch (e: Exception) {
            Log.w(TAG, "Direct FileOutputStream failed for ${targetFile.absolutePath}: ${e.message}. Using safe app external storage fallback...")
        }

        // Ultimate fallback to app files directory (guaranteed writable without permission prompts)
        val appDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val fallbackFile = File(appDir, "$SUBFOLDER/${targetFile.name}")
        fallbackFile.parentFile?.mkdirs()
        val fallbackStream = FileOutputStream(fallbackFile)
        return OutputTarget(fallbackStream, fallbackFile)
    }

    /**
     * Opens an OutputStream for writing to the target file.
     */
    fun openOutputStream(context: Context, targetFile: File, mimeType: String = "video/mp4"): OutputStream {
        return openOutputStreamAndFile(context, targetFile, mimeType).outputStream
    }

    /**
     * Migrates files that were previously saved in the hidden app-specific directory
     * to the public Downloads/IPTV directory as soon as write access is available.
     */
    fun migrateFallbackFiles(context: Context) {
        try {
            val appDownloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return
            val fallbackDir = File(appDownloads, SUBFOLDER)
            if (!fallbackDir.exists() || !fallbackDir.isDirectory) return

            val publicDir = getDownloadsDir(context)
            if (publicDir.absolutePath == fallbackDir.absolutePath) return

            val files = fallbackDir.listFiles() ?: return
            for (f in files) {
                if (f.isFile && f.length() > 0) {
                    val dest = File(publicDir, f.name)
                    if (!dest.exists() || dest.length() != f.length()) {
                        f.copyTo(dest, overwrite = true)
                    }
                    f.delete()
                    scanFileForDownloads(context, dest)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Migration of fallback files failed: ${e.message}")
        }
    }

    /**
     * Returns a user-friendly display path for the Downloads directory.
     */
    fun getDisplayFolderPath(context: Context): String {
        return try {
            val dir = getDownloadsDir(context)
            if (dir.absolutePath.contains("/Download")) {
                "Downloads/$SUBFOLDER"
            } else {
                dir.name
            }
        } catch (e: Exception) {
            "Downloads/$SUBFOLDER"
        }
    }

    /**
     * Sanitizes file names for Android file systems.
     */
    fun sanitizeFileName(rawName: String, extension: String): String {
        val clean = rawName
            .replace(Regex("[^a-zA-Z0-9äöüÄÖÜß._ -]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
        val base = if (clean.isBlank()) "recording_${System.currentTimeMillis()}" else clean
        val safeExt = if (extension.startsWith(".")) extension else ".$extension"
        val trimmedBase = if (base.endsWith(safeExt, ignoreCase = true)) {
            base.substring(0, base.length - safeExt.length)
        } else {
            base
        }
        return "$trimmedBase$safeExt"
    }

    /**
     * Creates a unique File in the Downloads folder.
     */
    fun createOutputFile(context: Context, rawName: String, extension: String): File {
        val dir = getDownloadsDir(context)
        val sanitized = sanitizeFileName(rawName, extension)
        var target = File(dir, sanitized)
        if (target.exists()) {
            val dotIndex = sanitized.lastIndexOf('.')
            val base = if (dotIndex > 0) sanitized.substring(0, dotIndex) else sanitized
            val ext = if (dotIndex > 0) sanitized.substring(dotIndex) else ""
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            target = File(dir, "${base}_$timestamp$ext")
        }
        return target
    }

    /**
     * Formats bytes to readable MB / GB string.
     */
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            else -> String.format(Locale.US, "%.0f KB", kb)
        }
    }

    /**
     * Scans the file so it appears immediately in the Android system Downloads app and MediaStore.
     */
    fun scanFileForDownloads(context: Context, file: File) {
        try {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf("video/mp4", "video/mp2t", "video/*"),
                null
            )
        } catch (e: Exception) {
            Log.w(TAG, "MediaScanner scan failed: ${e.message}")
        }
    }

    /**
     * Opens the Android system Downloads application or Files manager.
     */
    fun openDownloadsFolder(context: Context): Boolean {
        return try {
            val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            try {
                val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val uri = Uri.parse(publicDownloads.path)
                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "*/*")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
                true
            } catch (ex: Exception) {
                Log.e(TAG, "Could not open downloads manager: ${ex.message}")
                false
            }
        }
    }

    /**
     * Deletes a recording or downloaded file from the storage.
     */
    fun deleteFile(filePath: String?): Boolean {
        if (filePath.isNullOrBlank()) return false
        return try {
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete file $filePath: ${e.message}")
            false
        }
    }

    /**
     * Cleans up broken or corrupted dummy recordings (e.g. 169 byte HLS playlist text or tiny error files)
     * so that the user's IPTV folder only contains real, playable video files.
     */
    fun cleanupCorruptedDummyFiles(context: Context): Int {
        var deletedCount = 0
        try {
            val dir = getDownloadsDir(context)
            val files = dir.listFiles() ?: return 0
            for (file in files) {
                if (file.isFile && file.length() in 1L..512L) {
                    try {
                        val header = file.inputStream().use { stream ->
                            val buf = ByteArray(minOf(file.length().toInt(), 256))
                            val r = stream.read(buf)
                            if (r > 0) String(buf, 0, r) else ""
                        }
                        if (header.contains("#EXTM3U") || header.contains("<html", ignoreCase = true) || header.contains("HTTP/")) {
                            Log.i(TAG, "Deleting corrupt dummy recording: ${file.name} (${file.length()} bytes)")
                            if (file.delete()) {
                                deletedCount++
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning corrupt files: ${e.message}")
        }
        return deletedCount
    }
}
