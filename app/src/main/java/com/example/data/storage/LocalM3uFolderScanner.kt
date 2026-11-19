package com.example.data.storage

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
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
import java.security.MessageDigest
import java.util.Locale

enum class LocalM3uDuplicateStatus {
    NEW,
    DUPLICATE_IN_APP,
    DUPLICATE_IN_FOLDER
}

@Immutable
data class DiscoveredLocalM3uFile(
    val id: String,
    val fileName: String,
    val relativeFolder: String = "",
    val uri: Uri,
    val sizeBytes: Long,
    val lastModified: Long,
    val estimatedChannelCount: Int,
    val contentSha256: String,
    val duplicateStatus: LocalM3uDuplicateStatus,
    val duplicateReason: String? = null,
    val matchingPlaylistId: String? = null,
    val isSelected: Boolean = duplicateStatus == LocalM3uDuplicateStatus.NEW
)

@Immutable
data class LocalM3uScanResult(
    val folderName: String,
    val folderUri: Uri? = null,
    val files: List<DiscoveredLocalM3uFile> = emptyList(),
    val newCount: Int = 0,
    val duplicateCount: Int = 0,
    val errorMessage: String? = null
)

object LocalM3uFolderScanner {

    private const val TAG = "LocalM3uFolderScanner"
    private const val MAX_SCAN_DEPTH = 3
    private const val CACHE_DIR_NAME = "local_m3u_cache"

    private val M3U_EXTENSIONS = setOf("m3u", "m3u8", "m3u_plus")
    private val M3U_MIME_TYPES = setOf(
        "audio/x-mpegurl",
        "application/vnd.apple.mpegurl",
        "application/x-mpegurl",
        "audio/mpegurl",
        "application/mpegurl"
    )

    /**
     * Takes persistable URI permission when supported by the provider.
     */
    fun takePersistableReadPermissionSafely(context: Context, uri: Uri) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: Exception) {
            Log.d(TAG, "Persistable permission not supported for $uri: ${e.message}")
        }
    }

    /**
     * Scans a user-selected SAF directory tree (OpenDocumentTree) for M3U/M3U8 files
     * and performs duplicate detection against both existing app playlists and within the folder.
     */
    suspend fun scanFolderUri(
        context: Context,
        treeUri: Uri,
        existingPlaylists: List<PlaylistEntity>
    ): LocalM3uScanResult = withContext(Dispatchers.IO) {
        takePersistableReadPermissionSafely(context, treeUri)

        val folderDisplayName = resolveTreeDisplayName(context, treeUri)
        val rawCandidates = mutableListOf<RawDocEntry>()

        try {
            if (treeUri.scheme.equals("file", ignoreCase = true)) {
                val rootFile = File(treeUri.path ?: "")
                if (rootFile.exists() && rootFile.isDirectory) {
                    collectFromFileSystem(rootFile, "", 0, rawCandidates)
                }
            } else {
                val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
                collectFromDocumentTree(
                    context = context,
                    treeUri = treeUri,
                    parentDocId = treeDocId,
                    relativePath = "",
                    depth = 0,
                    outList = rawCandidates
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning folder URI $treeUri", e)
            return@withContext LocalM3uScanResult(
                folderName = folderDisplayName,
                folderUri = treeUri,
                files = emptyList(),
                errorMessage = "Ordner konnte nicht gelesen werden: ${e.localizedMessage ?: e.message}"
            )
        }

        buildEvaluatedScanResult(
            context = context,
            folderName = folderDisplayName,
            folderUri = treeUri,
            rawCandidates = rawCandidates,
            existingPlaylists = existingPlaylists
        )
    }

    /**
     * Scans individual or multiple M3U document URIs selected via OpenMultipleDocuments.
     */
    suspend fun scanDocumentUris(
        context: Context,
        uris: List<Uri>,
        existingPlaylists: List<PlaylistEntity>
    ): LocalM3uScanResult = withContext(Dispatchers.IO) {
        val rawCandidates = mutableListOf<RawDocEntry>()

        for (uri in uris) {
            takePersistableReadPermissionSafely(context, uri)
            val meta = resolveSingleDocumentMetadata(context, uri)
            if (meta != null) {
                rawCandidates.add(meta)
            }
        }

        buildEvaluatedScanResult(
            context = context,
            folderName = "Ausgewählte M3U-Dateien (${uris.size})",
            folderUri = null,
            rawCandidates = rawCandidates,
            existingPlaylists = existingPlaylists
        )
    }

    /**
     * Scans standard accessible local folders (Downloads, MaDIPTV, app storage) for M3U/M3U8 files.
     */
    suspend fun scanStandardLocalFolders(
        context: Context,
        existingPlaylists: List<PlaylistEntity>
    ): LocalM3uScanResult = withContext(Dispatchers.IO) {
        val rawCandidates = mutableListOf<RawDocEntry>()
        val seenPaths = HashSet<String>()

        val candidateDirs = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MaDIPTVPublic"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MaDIPTVPrivat"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MaDIPTV"),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            context.getExternalFilesDir(null)
        )

        for (dir in candidateDirs) {
            try {
                if (dir.exists() && dir.isDirectory && dir.canRead()) {
                    val canonical = dir.canonicalPath
                    if (seenPaths.add(canonical)) {
                        collectFromFileSystem(dir, dir.name, 0, rawCandidates)
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Skipping directory ${dir.absolutePath}: ${e.message}")
            }
        }

        buildEvaluatedScanResult(
            context = context,
            folderName = "Lokale Standard-Ordner (Downloads / Dokumente)",
            folderUri = null,
            rawCandidates = rawCandidates,
            existingPlaylists = existingPlaylists
        )
    }

    /**
     * Caches a local M3U file inside the app's private filesDir so it remains 100% readable
     * even if external SAF URI permissions expire or the external file is moved.
     */
    fun cacheM3uContentInternally(context: Context, playlistId: String, sourceUri: Uri): File? {
        return try {
            val cacheDir = File(context.filesDir, CACHE_DIR_NAME)
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val targetFile = File(cacheDir, "$playlistId.m3u")
            openStreamSafely(context, sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output, bufferSize = 32 * 1024)
                }
            }
            if (targetFile.exists() && targetFile.length() > 0L) targetFile else null
        } catch (e: Exception) {
            Log.w(TAG, "Could not cache M3U internally for $playlistId: ${e.message}")
            null
        }
    }

    /**
     * Opens an InputStream for a local M3U playlist, falling back to the internal cache if needed.
     */
    fun openLocalPlaylistInputStream(context: Context, playlist: PlaylistEntity): InputStream? {
        val rawUrl = playlist.url
        if (rawUrl.isNotBlank()) {
            try {
                val uri = Uri.parse(rawUrl)
                val direct = openStreamSafely(context, uri)
                if (direct != null) return direct
            } catch (e: Exception) {
                Log.w(TAG, "Direct URI read failed for ${playlist.name}, trying internal cache: ${e.message}")
            }
        }
        val cachedFile = File(File(context.filesDir, CACHE_DIR_NAME), "${playlist.id}.m3u")
        if (cachedFile.exists() && cachedFile.canRead()) {
            return FileInputStream(cachedFile)
        }
        return null
    }

    /**
     * Deletes the internal cached M3U file when a local playlist is deleted.
     */
    fun deleteCachedM3uFile(context: Context, playlistId: String) {
        try {
            val cachedFile = File(File(context.filesDir, CACHE_DIR_NAME), "$playlistId.m3u")
            if (cachedFile.exists()) {
                cachedFile.delete()
            }
        } catch (_: Exception) {
        }
    }

    private data class RawDocEntry(
        val fileName: String,
        val relativeFolder: String,
        val uri: Uri,
        val sizeBytes: Long,
        val lastModified: Long
    )

    private data class M3uInspection(
        val isValidM3u: Boolean,
        val estimatedChannels: Int,
        val sha256: String
    )

    private fun collectFromDocumentTree(
        context: Context,
        treeUri: Uri,
        parentDocId: String,
        relativePath: String,
        depth: Int,
        outList: MutableList<RawDocEntry>
    ) {
        if (depth > MAX_SCAN_DEPTH) return

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(childrenUri, projection, null, null, null)
            if (cursor == null) return

            val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

            while (cursor.moveToNext()) {
                val docId = if (idIdx >= 0) cursor.getString(idIdx) else continue
                val displayName = (if (nameIdx >= 0) cursor.getString(nameIdx) else null)?.trim() ?: continue
                val mimeType = (if (mimeIdx >= 0) cursor.getString(mimeIdx) else null)?.lowercase(Locale.ROOT) ?: ""
                val sizeBytes = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L
                val lastMod = if (modIdx >= 0 && !cursor.isNull(modIdx)) cursor.getLong(modIdx) else 0L

                if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    if (!displayName.startsWith(".")) {
                        val subPath = if (relativePath.isEmpty()) displayName else "$relativePath/$displayName"
                        collectFromDocumentTree(context, treeUri, docId, subPath, depth + 1, outList)
                    }
                } else if (isLikelyM3uFile(displayName, mimeType)) {
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    outList.add(
                        RawDocEntry(
                            fileName = displayName,
                            relativeFolder = relativePath,
                            uri = docUri,
                            sizeBytes = sizeBytes,
                            lastModified = lastMod
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error querying child documents for $parentDocId: ${e.message}")
        } finally {
            cursor?.close()
        }
    }

    private fun collectFromFileSystem(
        dir: File,
        relativePath: String,
        depth: Int,
        outList: MutableList<RawDocEntry>
    ) {
        if (depth > MAX_SCAN_DEPTH) return
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.name.startsWith(".")) continue
            if (child.isDirectory) {
                val nextRel = if (relativePath.isEmpty()) child.name else "$relativePath/${child.name}"
                collectFromFileSystem(child, nextRel, depth + 1, outList)
            } else if (child.isFile && isLikelyM3uFile(child.name, "")) {
                outList.add(
                    RawDocEntry(
                        fileName = child.name,
                        relativeFolder = relativePath,
                        uri = Uri.fromFile(child),
                        sizeBytes = child.length(),
                        lastModified = child.lastModified()
                    )
                )
            }
        }
    }

    private fun isLikelyM3uFile(fileName: String, mimeType: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext in M3U_EXTENSIONS) return true
        if (mimeType in M3U_MIME_TYPES) return true
        return false
    }

    private fun buildEvaluatedScanResult(
        context: Context,
        folderName: String,
        folderUri: Uri?,
        rawCandidates: List<RawDocEntry>,
        existingPlaylists: List<PlaylistEntity>
    ): LocalM3uScanResult {
        // Normalize existing playlist names, URIs, and stored SHA-256 hashes for O(1) duplicate checks
        val existingByExactName = existingPlaylists.associateBy { normalizeM3uName(it.name) }
        val existingByUri = existingPlaylists.associateBy { it.url.trim() }
        val existingByHash = existingPlaylists
            .filter { it.customEpgUrl.startsWith("sha256:") }
            .associateBy { it.customEpgUrl.removePrefix("sha256:").trim() }

        val seenNamesInFolder = HashMap<String, String>() // normalizedName -> original fileName
        val seenHashesInFolder = HashMap<String, String>() // sha256 -> original fileName

        val discovered = ArrayList<DiscoveredLocalM3uFile>(rawCandidates.size)

        val sortedCandidates = rawCandidates.sortedBy { it.fileName.lowercase(Locale.ROOT) }

        for (candidate in sortedCandidates) {
            val inspection = inspectM3uStream(context, candidate.uri)
            if (!inspection.isValidM3u) continue

            val normName = normalizeM3uName(candidate.fileName)
            val uriStr = candidate.uri.toString()

            val matchByUri = existingByUri[uriStr]
            val matchByName = existingByExactName[normName]
            val matchByHash = if (inspection.sha256.isNotBlank()) existingByHash[inspection.sha256] else null

            val existingMatch = matchByUri ?: matchByName ?: matchByHash
            val folderHashDuplicate = if (inspection.sha256.isNotBlank()) seenHashesInFolder[inspection.sha256] else null
            val folderNameDuplicate = seenNamesInFolder[normName]

            val (status, reason, matchedId) = when {
                existingMatch != null -> {
                    val detail = when {
                        matchByUri != null -> "Bereits importiert (identischer Dateipfad)"
                        matchByName != null -> "Bereits vorhanden als „${existingMatch.name}“"
                        else -> "Inhalt identisch mit „${existingMatch.name}“"
                    }
                    Triple(LocalM3uDuplicateStatus.DUPLICATE_IN_APP, detail, existingMatch.id)
                }
                folderNameDuplicate != null -> {
                    Triple(
                        LocalM3uDuplicateStatus.DUPLICATE_IN_FOLDER,
                        "Doppelter Dateiname im Ordner („$folderNameDuplicate“)",
                        null
                    )
                }
                folderHashDuplicate != null -> {
                    Triple(
                        LocalM3uDuplicateStatus.DUPLICATE_IN_FOLDER,
                        "Identischer Dateiinhalt wie „$folderHashDuplicate“",
                        null
                    )
                }
                else -> Triple(LocalM3uDuplicateStatus.NEW, null, null)
            }

            if (normName.isNotBlank() && !seenNamesInFolder.containsKey(normName)) {
                seenNamesInFolder[normName] = candidate.fileName
            }
            if (inspection.sha256.isNotBlank() && !seenHashesInFolder.containsKey(inspection.sha256)) {
                seenHashesInFolder[inspection.sha256] = candidate.fileName
            }

            discovered.add(
                DiscoveredLocalM3uFile(
                    id = "${candidate.uri}_${candidate.fileName}",
                    fileName = candidate.fileName,
                    relativeFolder = candidate.relativeFolder,
                    uri = candidate.uri,
                    sizeBytes = candidate.sizeBytes,
                    lastModified = candidate.lastModified,
                    estimatedChannelCount = inspection.estimatedChannels,
                    contentSha256 = inspection.sha256,
                    duplicateStatus = status,
                    duplicateReason = reason,
                    matchingPlaylistId = matchedId,
                    isSelected = status == LocalM3uDuplicateStatus.NEW
                )
            )
        }

        val newCount = discovered.count { it.duplicateStatus == LocalM3uDuplicateStatus.NEW }
        val dupCount = discovered.size - newCount

        return LocalM3uScanResult(
            folderName = folderName,
            folderUri = folderUri,
            files = discovered,
            newCount = newCount,
            duplicateCount = dupCount,
            errorMessage = null
        )
    }

    /**
     * Normalizes an M3U filename for duplicate comparison while keeping the exact filename for display.
     */
    fun normalizeM3uName(name: String): String {
        val trimmed = name.trim().lowercase(Locale.ROOT)
        return when {
            trimmed.endsWith(".m3u8") -> trimmed.removeSuffix(".m3u8").trim()
            trimmed.endsWith(".m3u") -> trimmed.removeSuffix(".m3u").trim()
            trimmed.endsWith(".m3u_plus") -> trimmed.removeSuffix(".m3u_plus").trim()
            else -> trimmed
        }
    }

    private fun inspectM3uStream(context: Context, uri: Uri): M3uInspection {
        val digest = MessageDigest.getInstance("SHA-256")
        var extinfCount = 0
        var streamUrlCount = 0
        var hasExtM3uHeader = false

        return try {
            val stream = openStreamSafely(context, uri) ?: return M3uInspection(false, 0, "")
            stream.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8), 16 * 1024).use { reader ->
                    var lineCount = 0
                    var line: String? = reader.readLine()
                    while (line != null) {
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty()) {
                            lineCount++
                            if (lineCount == 1 && trimmed.startsWith("#EXTM3U", ignoreCase = true)) {
                                hasExtM3uHeader = true
                            }
                            if (trimmed.startsWith("#EXTINF", ignoreCase = true)) {
                                extinfCount++
                            } else if (!trimmed.startsWith("#") && (
                                    trimmed.startsWith("http://", ignoreCase = true) ||
                                        trimmed.startsWith("https://", ignoreCase = true) ||
                                        trimmed.startsWith("rtmp://", ignoreCase = true) ||
                                        trimmed.startsWith("rtsp://", ignoreCase = true) ||
                                        trimmed.startsWith("udp://", ignoreCase = true) ||
                                        trimmed.startsWith("mms://", ignoreCase = true)
                                )
                            ) {
                                streamUrlCount++
                            }
                            digest.update(trimmed.toByteArray(Charsets.UTF_8))
                            digest.update('\n'.code.toByte())
                        }
                        line = reader.readLine()
                    }
                }
            }
            val estimated = if (extinfCount > 0) extinfCount else streamUrlCount
            val isValid = hasExtM3uHeader || extinfCount > 0 || streamUrlCount > 0
            val hashBytes = digest.digest()
            val shaHex = hashBytes.joinToString("") { "%02x".format(it) }
            M3uInspection(
                isValidM3u = isValid,
                estimatedChannels = estimated,
                sha256 = shaHex
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to inspect M3U stream $uri: ${e.message}")
            M3uInspection(false, 0, "")
        }
    }

    private fun openStreamSafely(context: Context, uri: Uri): InputStream? {
        return try {
            if (uri.scheme.equals("file", ignoreCase = true)) {
                val path = uri.path ?: return null
                FileInputStream(File(path))
            } else {
                context.contentResolver.openInputStream(uri)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun resolveSingleDocumentMetadata(context: Context, uri: Uri): RawDocEntry? {
        if (uri.scheme.equals("file", ignoreCase = true)) {
            val f = File(uri.path ?: return null)
            if (!f.exists() || !f.isFile) return null
            return RawDocEntry(
                fileName = f.name,
                relativeFolder = f.parentFile?.name ?: "",
                uri = uri,
                sizeBytes = f.length(),
                lastModified = f.lastModified()
            )
        }

        var displayName = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "playlist.m3u"
        var sizeBytes = 0L

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, null, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) {
                    val n = cursor.getString(nameIdx)
                    if (!n.isNullOrBlank()) displayName = n.trim()
                }
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                    sizeBytes = cursor.getLong(sizeIdx)
                }
            }
        } catch (_: Exception) {
        } finally {
            cursor?.close()
        }

        return RawDocEntry(
            fileName = displayName,
            relativeFolder = "",
            uri = uri,
            sizeBytes = sizeBytes,
            lastModified = System.currentTimeMillis()
        )
    }

    private fun resolveTreeDisplayName(context: Context, treeUri: Uri): String {
        if (treeUri.scheme.equals("file", ignoreCase = true)) {
            return File(treeUri.path ?: "").name.ifBlank { "Lokaler Ordner" }
        }
        try {
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
            val cleaned = docId.substringAfterLast(':').substringAfterLast('/')
            if (cleaned.isNotBlank()) return cleaned
        } catch (_: Exception) {
        }
        return treeUri.lastPathSegment?.substringAfterLast(':') ?: "Ausgewählter Ordner"
    }
}
