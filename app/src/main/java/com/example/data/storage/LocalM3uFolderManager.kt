package com.example.data.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.compose.runtime.Immutable
import com.example.data.model.PlaylistEntity
import com.example.data.model.PlaylistType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

@Immutable
data class LocalM3uFileCandidate(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val uri: Uri,
    val folderName: String,
    val fileSizeBytes: Long,
    val fileSizeFormatted: String,
    val channelCountEstimate: Int,
    val contentHash: String,
    val isDuplicate: Boolean,
    val duplicateReason: String? = null,
    val existingPlaylistId: String? = null,
    val isSelected: Boolean = !isDuplicate
)

@Immutable
data class LocalM3uFolderScanResult(
    val folderDisplayName: String,
    val folderUri: Uri?,
    val candidates: List<LocalM3uFileCandidate>,
    val totalFound: Int = candidates.size,
    val newFilesCount: Int = candidates.count { !it.isDuplicate },
    val duplicateFilesCount: Int = candidates.count { it.isDuplicate }
)

object LocalM3uFolderManager {

    private const val PREFS_NAME = "local_m3u_folder_prefs"
    private const val KEY_LAST_FOLDER_URI = "last_folder_uri"
    private const val KEY_LAST_FOLDER_NAME = "last_folder_name"
    private const val LOCAL_M3U_CACHE_DIR = "local_m3u"

    fun saveLastSelectedFolder(context: Context, uri: Uri, displayName: String) {
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_FOLDER_URI, uri.toString())
                .putString(KEY_LAST_FOLDER_NAME, displayName)
                .apply()
        }
    }

    fun getLastSelectedFolderUri(context: Context): Uri? {
        return runCatching {
            val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LAST_FOLDER_URI, null)
            if (!raw.isNullOrBlank()) Uri.parse(raw) else null
        }.getOrNull()
    }

    fun getLastSelectedFolderName(context: Context): String? {
        return runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LAST_FOLDER_NAME, null)
        }.getOrNull()
    }

    fun normalizeM3uBaseName(name: String): String {
        return name.trim()
            .lowercase(Locale.ROOT)
            .removeSuffix(".m3u8")
            .removeSuffix(".m3u")
            .trim()
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 KB"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format(Locale.US, "%.2f MB", mb)
    }

    fun getCachedM3uFile(context: Context, playlistId: String): File {
        val dir = File(context.filesDir, LOCAL_M3U_CACHE_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "$playlistId.m3u")
    }

    fun deleteCachedM3uFile(context: Context, playlistId: String) {
        runCatching {
            val file = getCachedM3uFile(context, playlistId)
            if (file.exists()) {
                file.delete()
            }
        }
    }

    fun cacheLocalM3uCopy(context: Context, playlistId: String, sourceUri: Uri): File? {
        return runCatching {
            val targetFile = getCachedM3uFile(context, playlistId)
            openRawUriStream(context, sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output, bufferSize = 32 * 1024)
                }
            }
            if (targetFile.exists() && targetFile.length() > 0L) targetFile else null
        }.getOrNull()
    }

    fun openLocalM3uStream(context: Context, playlist: PlaylistEntity): InputStream? {
        val cached = getCachedM3uFile(context, playlist.id)
        if (cached.exists() && cached.length() > 0L) {
            runCatching { return FileInputStream(cached) }
        }
        if (playlist.url.isNotBlank()) {
            val uri = runCatching { Uri.parse(playlist.url) }.getOrNull()
            if (uri != null) {
                return openRawUriStream(context, uri)
            }
        }
        return null
    }

    private fun openRawUriStream(context: Context, uri: Uri): InputStream? {
        return try {
            if (uri.scheme.equals("file", ignoreCase = true)) {
                val path = uri.path ?: return null
                val f = File(path)
                if (f.exists() && f.canRead()) FileInputStream(f) else null
            } else {
                context.contentResolver.openInputStream(uri)
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun scanFolderUri(
        context: Context,
        treeUri: Uri,
        existingPlaylists: List<PlaylistEntity>
    ): LocalM3uFolderScanResult = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        val folderDisplayName = resolveFolderDisplayName(context, treeUri)
        saveLastSelectedFolder(context, treeUri, folderDisplayName)

        val discoveredFiles = mutableListOf<DiscoveredDocEntry>()

        if (treeUri.scheme.equals("file", ignoreCase = true)) {
            val rootFile = treeUri.path?.let { File(it) }
            if (rootFile != null && rootFile.exists()) {
                collectFileSystemEntries(rootFile, rootFile.name.ifBlank { folderDisplayName }, 0, discoveredFiles)
            }
        } else if (DocumentsContract.isTreeUri(treeUri)) {
            val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            if (rootDocId != null) {
                collectDocumentTreeEntries(
                    context = context,
                    treeUri = treeUri,
                    parentDocId = rootDocId,
                    currentFolderName = folderDisplayName,
                    depth = 0,
                    out = discoveredFiles
                )
            }
        } else {
            // Single document URI fallback
            val name = resolveSingleDocName(context, treeUri)
            val size = resolveSingleDocSize(context, treeUri)
            if (name != null) {
                discoveredFiles.add(
                    DiscoveredDocEntry(
                        uri = treeUri,
                        fileName = name,
                        folderName = folderDisplayName,
                        sizeBytes = size
                    )
                )
            }
        }

        val candidates = buildCandidatesWithDuplicateCheck(
            context = context,
            entries = discoveredFiles,
            existingPlaylists = existingPlaylists
        )

        LocalM3uFolderScanResult(
            folderDisplayName = folderDisplayName,
            folderUri = treeUri,
            candidates = candidates
        )
    }

    suspend fun scanDocumentUris(
        context: Context,
        uris: List<Uri>,
        existingPlaylists: List<PlaylistEntity>
    ): LocalM3uFolderScanResult = withContext(Dispatchers.IO) {
        val entries = mutableListOf<DiscoveredDocEntry>()
        for (uri in uris) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val fileName = resolveSingleDocName(context, uri) ?: continue
            val size = resolveSingleDocSize(context, uri)
            entries.add(
                DiscoveredDocEntry(
                    uri = uri,
                    fileName = fileName,
                    folderName = "Dateiauswahl",
                    sizeBytes = size
                )
            )
        }

        val candidates = buildCandidatesWithDuplicateCheck(
            context = context,
            entries = entries,
            existingPlaylists = existingPlaylists
        )

        LocalM3uFolderScanResult(
            folderDisplayName = "Ausgewählte M3U-Dateien (${entries.size})",
            folderUri = null,
            candidates = candidates
        )
    }

    private data class DiscoveredDocEntry(
        val uri: Uri,
        val fileName: String,
        val folderName: String,
        val sizeBytes: Long
    )

    private fun collectFileSystemEntries(
        dir: File,
        folderLabel: String,
        depth: Int,
        out: MutableList<DiscoveredDocEntry>
    ) {
        if (depth > 3) return
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.isDirectory) {
                if (!child.name.startsWith(".")) {
                    collectFileSystemEntries(
                        dir = child,
                        folderLabel = "$folderLabel/${child.name}",
                        depth = depth + 1,
                        out = out
                    )
                }
            } else if (child.isFile && isLikelyM3uFileName(child.name)) {
                out.add(
                    DiscoveredDocEntry(
                        uri = Uri.fromFile(child),
                        fileName = child.name,
                        folderName = folderLabel,
                        sizeBytes = child.length()
                    )
                )
            }
        }
    }

    private fun collectDocumentTreeEntries(
        context: Context,
        treeUri: Uri,
        parentDocId: String,
        currentFolderName: String,
        depth: Int,
        out: MutableList<DiscoveredDocEntry>
    ) {
        if (depth > 3) return
        val childrenUri = runCatching {
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        }.getOrNull() ?: return

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )

        runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)

                while (cursor.moveToNext()) {
                    val docId = if (idIdx >= 0) cursor.getString(idIdx) else null
                    val displayName = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                    val mimeType = if (mimeIdx >= 0) cursor.getString(mimeIdx) else null
                    val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L

                    if (docId.isNullOrBlank() || displayName.isNullOrBlank()) continue

                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (!displayName.startsWith(".")) {
                            collectDocumentTreeEntries(
                                context = context,
                                treeUri = treeUri,
                                parentDocId = docId,
                                currentFolderName = "$currentFolderName/$displayName",
                                depth = depth + 1,
                                out = out
                            )
                        }
                    } else {
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        if (isLikelyM3uFileName(displayName) || isM3uMimeType(mimeType)) {
                            out.add(
                                DiscoveredDocEntry(
                                    uri = docUri,
                                    fileName = displayName,
                                    folderName = currentFolderName,
                                    sizeBytes = size
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun isLikelyM3uFileName(name: String): Boolean {
        val lower = name.trim().lowercase(Locale.ROOT)
        return lower.endsWith(".m3u") ||
            lower.endsWith(".m3u8") ||
            lower.endsWith(".m3u_plus")
    }

    private fun isM3uMimeType(mime: String?): Boolean {
        if (mime.isNullOrBlank()) return false
        val lower = mime.lowercase(Locale.ROOT)
        return lower.contains("mpegurl") || lower.contains("m3u")
    }

    private fun buildCandidatesWithDuplicateCheck(
        context: Context,
        entries: List<DiscoveredDocEntry>,
        existingPlaylists: List<PlaylistEntity>
    ): List<LocalM3uFileCandidate> {
        val existingExactNames = HashMap<String, PlaylistEntity>()
        val existingBaseNames = HashMap<String, PlaylistEntity>()
        val existingUris = HashMap<String, PlaylistEntity>()
        val existingHashes = HashMap<String, PlaylistEntity>()

        for (pl in existingPlaylists) {
            val cleanExact = pl.name.trim().lowercase(Locale.ROOT)
            if (cleanExact.isNotEmpty()) {
                existingExactNames[cleanExact] = pl
            }
            val baseName = normalizeM3uBaseName(pl.name)
            if (baseName.isNotEmpty()) {
                existingBaseNames[baseName] = pl
            }
            if (pl.url.isNotBlank()) {
                existingUris[pl.url.trim()] = pl
            }
            if (pl.type == PlaylistType.M3U_FILE) {
                if (pl.username.length == 64) {
                    existingHashes[pl.username.lowercase(Locale.ROOT)] = pl
                } else {
                    val cached = getCachedM3uFile(context, pl.id)
                    if (cached.exists() && cached.length() > 0L) {
                        val hash = computeFileHashOnly(cached)
                        if (hash.isNotBlank()) {
                            existingHashes[hash] = pl
                        }
                    }
                }
            }
        }

        val seenScanNames = HashSet<String>()
        val seenScanBaseNames = HashSet<String>()
        val seenScanHashes = HashSet<String>()
        val seenScanUris = HashSet<String>()

        val sortedEntries = entries.sortedBy { it.fileName.lowercase(Locale.ROOT) }
        val result = ArrayList<LocalM3uFileCandidate>(sortedEntries.size)

        for (entry in sortedEntries) {
            val exactFileName = entry.fileName.trim()
            if (exactFileName.isBlank()) continue

            val uriStr = entry.uri.toString()
            if (!seenScanUris.add(uriStr)) {
                continue
            }

            val inspection = inspectM3uStream(context, entry.uri, entry.sizeBytes)
            // Skip completely empty or unreadable non-M3U files
            if (!isLikelyM3uFileName(exactFileName) && inspection.channelCount == 0 && !inspection.hasM3uHeader) {
                continue
            }

            val lowerExactName = exactFileName.lowercase(Locale.ROOT)
            val lowerBaseName = normalizeM3uBaseName(exactFileName)
            val hash = inspection.sha256Hash.lowercase(Locale.ROOT)

            var isDup = false
            var dupReason: String? = null
            var matchedExistingId: String? = null

            val matchedByUri = existingUris[uriStr]
            val matchedByExactName = existingExactNames[lowerExactName]
            val matchedByBaseName = existingBaseNames[lowerBaseName]
            val matchedByHash = if (hash.isNotBlank()) existingHashes[hash] else null

            when {
                matchedByUri != null -> {
                    isDup = true
                    dupReason = "Bereits hinzugefügt (gleicher Dateipfad: ${matchedByUri.name})"
                    matchedExistingId = matchedByUri.id
                }
                matchedByExactName != null -> {
                    isDup = true
                    dupReason = "Bereits vorhanden (gleicher M3U-Name: ${matchedByExactName.name})"
                    matchedExistingId = matchedByExactName.id
                }
                matchedByBaseName != null -> {
                    isDup = true
                    dupReason = "Bereits vorhanden (Name: ${matchedByBaseName.name})"
                    matchedExistingId = matchedByBaseName.id
                }
                matchedByHash != null -> {
                    isDup = true
                    dupReason = "Bereits vorhanden (identischer Inhalt wie ${matchedByHash.name})"
                    matchedExistingId = matchedByHash.id
                }
                !seenScanNames.add(lowerExactName) || (lowerBaseName.isNotEmpty() && !seenScanBaseNames.add(lowerBaseName)) -> {
                    isDup = true
                    dupReason = "Doppelter Dateiname im ausgewählten Ordner"
                }
                hash.isNotBlank() && !seenScanHashes.add(hash) -> {
                    isDup = true
                    dupReason = "Doppelter Dateiinhalt im ausgewählten Ordner"
                }
            }

            // Ensure hashes and names are tracked even if not triggered above
            seenScanNames.add(lowerExactName)
            if (lowerBaseName.isNotEmpty()) seenScanBaseNames.add(lowerBaseName)
            if (hash.isNotBlank()) seenScanHashes.add(hash)

            val actualSize = if (entry.sizeBytes > 0L) entry.sizeBytes else inspection.bytesRead

            result.add(
                LocalM3uFileCandidate(
                    fileName = exactFileName,
                    uri = entry.uri,
                    folderName = entry.folderName,
                    fileSizeBytes = actualSize,
                    fileSizeFormatted = formatFileSize(actualSize),
                    channelCountEstimate = inspection.channelCount,
                    contentHash = hash,
                    isDuplicate = isDup,
                    duplicateReason = dupReason,
                    existingPlaylistId = matchedExistingId,
                    isSelected = !isDup
                )
            )
        }

        return result
    }

    private data class StreamInspectionResult(
        val channelCount: Int,
        val hasM3uHeader: Boolean,
        val sha256Hash: String,
        val bytesRead: Long
    )

    private fun inspectM3uStream(context: Context, uri: Uri, knownSize: Long): StreamInspectionResult {
        var channelCount = 0
        var hasHeader = false
        var totalBytes = 0L
        val digest = MessageDigest.getInstance("SHA-256")

        runCatching {
            openRawUriStream(context, uri)?.use { rawIn ->
                BufferedReader(InputStreamReader(rawIn, Charsets.UTF_8), 32 * 1024).use { reader ->
                    var line: String?
                    var lineIndex = 0
                    while (reader.readLine().also { line = it } != null) {
                        val current = line ?: continue
                        val bytes = current.toByteArray(Charsets.UTF_8)
                        totalBytes += bytes.size + 1
                        digest.update(bytes)

                        val trimmed = current.trim()
                        if (trimmed.isEmpty()) continue
                        lineIndex++

                        if (lineIndex <= 5 && trimmed.startsWith("#EXTM3U", ignoreCase = true)) {
                            hasHeader = true
                        }
                        if (trimmed.startsWith("#EXTINF", ignoreCase = true)) {
                            channelCount++
                        } else if (!trimmed.startsWith("#") && (
                                trimmed.startsWith("http://", ignoreCase = true) ||
                                    trimmed.startsWith("https://", ignoreCase = true) ||
                                    trimmed.startsWith("rtmp://", ignoreCase = true) ||
                                    trimmed.startsWith("rtsp://", ignoreCase = true) ||
                                    trimmed.startsWith("udp://", ignoreCase = true) ||
                                    trimmed.startsWith("mms://", ignoreCase = true)
                            )
                        ) {
                            if (!hasHeader && channelCount == 0) {
                                channelCount++
                            }
                        }
                    }
                }
            }
        }

        val hashHex = if (totalBytes > 0L) {
            digest.digest().joinToString("") { "%02x".format(it) }
        } else {
            ""
        }

        return StreamInspectionResult(
            channelCount = channelCount,
            hasM3uHeader = hasHeader,
            sha256Hash = hashHex,
            bytesRead = if (knownSize > 0L) knownSize else totalBytes
        )
    }

    private fun computeFileHashOnly(file: File): String {
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buffer = ByteArray(16 * 1024)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrDefault("")
    }

    private fun resolveFolderDisplayName(context: Context, treeUri: Uri): String {
        runCatching {
            if (DocumentsContract.isTreeUri(treeUri)) {
                val docId = DocumentsContract.getTreeDocumentId(treeUri)
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                context.contentResolver.query(
                    docUri,
                    arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        if (idx >= 0) {
                            val name = cursor.getString(idx)
                            if (!name.isNullOrBlank()) return name
                        }
                    }
                }
                val decoded = URLDecoder.decode(docId, "UTF-8")
                val afterColon = decoded.substringAfterLast(':', decoded)
                if (afterColon.isNotBlank()) return afterColon
            }
        }
        val decodedUri = runCatching { URLDecoder.decode(treeUri.toString(), "UTF-8") }.getOrDefault(treeUri.toString())
        return decodedUri.substringAfterLast('/').substringAfterLast(':').ifBlank { "Lokaler M3U-Ordner" }
    }

    private fun resolveSingleDocName(context: Context, uri: Uri): String? {
        if (uri.scheme.equals("file", ignoreCase = true)) {
            return uri.path?.let { File(it).name }
        }
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = cursor.getString(idx)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        }
        val fallback = runCatching { URLDecoder.decode(uri.lastPathSegment ?: "", "UTF-8") }.getOrDefault("")
        val clean = fallback.substringAfterLast('/').substringAfterLast(':')
        return clean.ifBlank { null }
    }

    private fun resolveSingleDocSize(context: Context, uri: Uri): Long {
        if (uri.scheme.equals("file", ignoreCase = true)) {
            return uri.path?.let { File(it).length() } ?: 0L
        }
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && !cursor.isNull(idx)) {
                        return cursor.getLong(idx)
                    }
                }
            }
        }
        return 0L
    }
}
