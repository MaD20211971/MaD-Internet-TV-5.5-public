package com.example.data.parser

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.util.Base64
import com.example.data.model.ChannelItem
import com.example.data.model.PlaylistEntity
import com.example.data.model.PlaylistType
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object PlaylistBackupManager {

    data class BackupPayload(
        val version: Int = 1,
        val app: String = "MaD IPTV public",
        val timestamp: Long = System.currentTimeMillis(),
        val isProtected: Boolean,
        val passwordHash: String?,
        val playlists: List<PlaylistEntity>,
        val channels: List<ChannelItem>
    )

    fun createBackupJson(
        playlists: List<PlaylistEntity>,
        channels: List<ChannelItem>,
        passwordProtection: String?
    ): String {
        val hasPassword = !passwordProtection.isNullOrBlank()
        val root = JSONObject()
        root.put("app", "MaD IPTV public")
        root.put("version", 1)
        root.put("timestamp", System.currentTimeMillis())
        root.put("isProtected", hasPassword)

        val passHash = if (hasPassword) sha256(passwordProtection!!) else ""
        root.put("protectionHash", passHash)

        val playlistsArray = JSONArray()
        for (pl in playlists) {
            val plObj = JSONObject()
            plObj.put("id", pl.id)
            plObj.put("name", pl.name)
            plObj.put("type", pl.type.name)
            plObj.put("customEpgUrl", pl.customEpgUrl)
            plObj.put("liveCount", pl.liveCount)
            plObj.put("movieCount", pl.movieCount)
            plObj.put("seriesCount", pl.seriesCount)

            if (hasPassword) {
                // Encrypt sensitive credentials
                plObj.put("url", encrypt(pl.url, passwordProtection!!))
                plObj.put("username", encrypt(pl.username, passwordProtection))
                plObj.put("password", encrypt(pl.password, passwordProtection))
                plObj.put("macAddress", encrypt(pl.macAddress, passwordProtection))
                plObj.put("isMasked", true)
            } else {
                plObj.put("url", pl.url)
                plObj.put("username", pl.username)
                plObj.put("password", pl.password)
                plObj.put("macAddress", pl.macAddress)
                plObj.put("isMasked", false)
            }
            playlistsArray.put(plObj)
        }
        root.put("playlists", playlistsArray)

        val channelsArray = JSONArray()
        // Prioritize user-specific channels: favorites, custom/manual streams, and a compact fallback sample.
        // Capped to max 120 items so the payload remains compact (~5KB to 30KB).
        // This avoids Android Binder TransactionTooLargeException and Samsung Clipboard crashes on Android 16.
        val prioritizedChannels = mutableListOf<ChannelItem>()
        // 1. Favorites
        prioritizedChannels.addAll(channels.filter { it.isFavorite })
        // 2. Custom/manual web streams
        val customStreams = channels.filter {
            !it.isFavorite && (it.playlistId == "web_streams" || it.playlistId == "manual" || it.playlistId.startsWith("custom_"))
        }
        prioritizedChannels.addAll(customStreams)
        // 3. Fallback up to 100 channels if library is small
        if (prioritizedChannels.size < 100) {
            val existingIds = prioritizedChannels.map { it.id }.toHashSet()
            val remaining = channels.asSequence()
                .filter { it.id !in existingIds }
                .take(100 - prioritizedChannels.size)
                .toList()
            prioritizedChannels.addAll(remaining)
        }
        val exportChannels = prioritizedChannels.take(120)

        for (ch in exportChannels) {
            val chObj = JSONObject()
            chObj.put("id", ch.id)
            chObj.put("playlistId", ch.playlistId)
            chObj.put("name", ch.name)
            chObj.put("groupTitle", ch.groupTitle)
            chObj.put("type", ch.type.name)
            chObj.put("tvgId", ch.tvgId ?: "")
            chObj.put("logoUrl", ch.logoUrl ?: "")
            chObj.put("isDach", ch.isDach)
            chObj.put("countryCode", ch.countryCode)
            chObj.put("seriesTitle", ch.seriesTitle ?: "")
            chObj.put("seasonNumber", ch.seasonNumber ?: 0)
            chObj.put("episodeNumber", ch.episodeNumber ?: 0)
            chObj.put("resolutionBadge", ch.resolutionBadge)

            if (hasPassword) {
                chObj.put("streamUrl", encrypt(ch.streamUrl, passwordProtection!!))
            } else {
                chObj.put("streamUrl", ch.streamUrl)
            }
            channelsArray.put(chObj)
        }
        root.put("channels", channelsArray)

        return root.toString()
    }

    /**
     * Safely copies backup data to system clipboard with Android 13/14/15/16 security
     * and Binder TransactionTooLargeException / Samsung Clipboard protection.
     */
    fun copyToClipboard(context: Context, text: String, label: String = "MaD IPTV public Backup"): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return false
            val clip = ClipData.newPlainText(label, text)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                clip.description.extras = PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
            }
            clipboard.setPrimaryClip(clip)
            true
        } catch (e: Throwable) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Shares backup via standard Android ShareSheet to avoid any clipboard size limitations.
     */
    fun shareBackup(context: Context, backupJson: String, title: String = "MaD IPTV public Backup") {
        try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_TEXT, backupJson)
                putExtra(Intent.EXTRA_SUBJECT, title)
                type = "text/plain"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(sendIntent, "Backup teilen / speichern")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun restoreBackupJson(
        jsonString: String,
        enteredPassword: String?
    ): Result<Pair<List<PlaylistEntity>, List<ChannelItem>>> {
        return try {
            val cleanJson = jsonString.trim()
            if (cleanJson.isBlank()) {
                return Result.failure(Exception("Backup-Daten sind leer."))
            }
            val root = JSONObject(cleanJson)
            val isProtected = root.optBoolean("isProtected", false)
            val storedHash = root.optString("protectionHash", "")

            if (isProtected) {
                if (enteredPassword.isNullOrBlank()) {
                    return Result.failure(Exception("Dieses Backup ist passwortgeschützt. Bitte Passwort eingeben."))
                }
                val enteredHash = sha256(enteredPassword)
                if (enteredHash != storedHash) {
                    return Result.failure(Exception("Falsches Passwort. Wiederherstellung fehlgeschlagen."))
                }
            }

            val playlistsArray = root.optJSONArray("playlists") ?: JSONArray()
            val restoredPlaylists = mutableListOf<PlaylistEntity>()

            for (i in 0 until playlistsArray.length()) {
                val plObj = playlistsArray.getJSONObject(i)
                val isMasked = plObj.optBoolean("isMasked", false)

                val rawUrl = plObj.optString("url", "")
                val rawUser = plObj.optString("username", "")
                val rawPass = plObj.optString("password", "")
                val rawMac = plObj.optString("macAddress", "")

                val url = if (isMasked && isProtected) decrypt(rawUrl, enteredPassword!!) else rawUrl
                val username = if (isMasked && isProtected) decrypt(rawUser, enteredPassword!!) else rawUser
                val password = if (isMasked && isProtected) decrypt(rawPass, enteredPassword!!) else rawPass
                val mac = if (isMasked && isProtected) decrypt(rawMac, enteredPassword!!) else rawMac

                restoredPlaylists.add(
                    PlaylistEntity(
                        id = plObj.optString("id", java.util.UUID.randomUUID().toString()),
                        name = plObj.optString("name", "Wiederhergestellte Playlist"),
                        type = try {
                            PlaylistType.valueOf(plObj.optString("type", PlaylistType.M3U_URL.name))
                        } catch (_: Exception) {
                            PlaylistType.M3U_URL
                        },
                        url = url,
                        username = username,
                        password = password,
                        macAddress = mac,
                        customEpgUrl = plObj.optString("customEpgUrl", ""),
                        liveCount = plObj.optInt("liveCount", 0),
                        movieCount = plObj.optInt("movieCount", 0),
                        seriesCount = plObj.optInt("seriesCount", 0),
                        isPasswordProtected = isProtected
                    )
                )
            }

            val channelsArray = root.optJSONArray("channels") ?: JSONArray()
            val restoredChannels = mutableListOf<ChannelItem>()

            for (i in 0 until channelsArray.length()) {
                val chObj = channelsArray.getJSONObject(i)
                val rawUrl = chObj.optString("streamUrl", "")
                val streamUrl = if (isProtected) decrypt(rawUrl, enteredPassword!!) else rawUrl

                restoredChannels.add(
                    ChannelItem(
                        id = chObj.optString("id", java.util.UUID.randomUUID().toString()),
                        playlistId = chObj.optString("playlistId", ""),
                        name = chObj.optString("name", "Sender"),
                        streamUrl = streamUrl,
                        logoUrl = chObj.optString("logoUrl").takeIf { it.isNotBlank() },
                        groupTitle = chObj.optString("groupTitle", "Allgemein"),
                        type = try {
                            com.example.data.model.ContentType.valueOf(chObj.optString("type", "LIVE_TV"))
                        } catch (_: Exception) {
                            com.example.data.model.ContentType.LIVE_TV
                        },
                        tvgId = chObj.optString("tvgId").takeIf { it.isNotBlank() },
                        isDach = chObj.optBoolean("isDach", false),
                        countryCode = chObj.optString("countryCode", "DE"),
                        seriesTitle = chObj.optString("seriesTitle").takeIf { it.isNotBlank() },
                        seasonNumber = chObj.optInt("seasonNumber").takeIf { it > 0 },
                        episodeNumber = chObj.optInt("episodeNumber").takeIf { it > 0 },
                        resolutionBadge = chObj.optString("resolutionBadge", "HD")
                    )
                )
            }

            Result.success(Pair(restoredPlaylists, restoredChannels))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun encrypt(value: String, secret: String): String {
        if (value.isEmpty()) return ""
        return try {
            val keyBytes = MessageDigest.getInstance("SHA-256").digest(secret.toByteArray()).copyOf(16)
            val key = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val iv = ByteArray(16) { 7 }
            cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
            val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        } catch (e: Exception) {
            value
        }
    }

    private fun decrypt(encryptedBase64: String, secret: String): String {
        if (encryptedBase64.isEmpty()) return ""
        return try {
            val keyBytes = MessageDigest.getInstance("SHA-256").digest(secret.toByteArray()).copyOf(16)
            val key = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val iv = ByteArray(16) { 7 }
            cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
            val decoded = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            String(cipher.doFinal(decoded), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            encryptedBase64
        }
    }
}
