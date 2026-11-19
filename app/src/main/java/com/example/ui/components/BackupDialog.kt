package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.parser.PlaylistBackupManager
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.BorderGlow
import com.example.ui.theme.DarkNavyCard
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.GoldLight
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BackupExportDialog(
    onDismiss: () -> Unit,
    onGenerateBackup: (password: String?) -> String,
    playlistsCount: Int = 0,
    isEnglish: Boolean = false
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var generatedBackupJson by remember { mutableStateOf<String?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    var isCopied by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, GoldPrimary, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = DarkNavyCard)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEnglish) "Export Backup & Protect" else "Backup exportieren & schützen",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldPrimary
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Hinweistext gemäß Anforderung
                Text(
                    text = if (isEnglish) "The backup function requires a password (e.g. PIN 1971)." else "Die Backupfunktion erfordert ein Passwort (z. B. PIN 1971).",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )

                if (playlistsCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isEnglish) "Playlists to backup: $playlistsCount" else "Verfügbare Playlisten zum Sichern: $playlistsCount",
                        fontSize = 11.sp,
                        color = GoldLight,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (generatedBackupJson == null) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            passwordError = null
                        },
                        label = { Text(if (isEnglish) "Password" else "Passwort") },
                        placeholder = { Text(if (isEnglish) "Enter password (e.g. 1971)" else "Passwort eingeben (z. B. 1971)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                        ),
                        isError = passwordError != null,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkSurface,
                            unfocusedContainerColor = DarkSurface,
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = if (passwordError != null) com.example.ui.theme.AccentRose else BorderGlow.copy(alpha = 0.4f),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    if (passwordError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = passwordError!!,
                            color = com.example.ui.theme.AccentRose,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            if (password.isBlank()) {
                                passwordError = if (isEnglish) "The backup function requires a password." else "Die Backupfunktion erfordert ein Passwort."
                            } else if (password.length < 4) {
                                passwordError = if (isEnglish) "Password must be at least 4 characters (e.g. PIN 1971)." else "Das Passwort muss mindestens 4 Zeichen lang sein (z. B. PIN 1971)."
                            } else {
                                isGenerating = true
                                passwordError = null
                                coroutineScope.launch {
                                    try {
                                        val json = withContext(Dispatchers.IO) {
                                            onGenerateBackup(password.trim())
                                        }
                                        if (json.isNotBlank()) {
                                            generatedBackupJson = json
                                            passwordError = null
                                        } else {
                                            passwordError = if (isEnglish) "No backup could be generated. Please try again." else "Backup konnte nicht generiert werden. Bitte erneut versuchen."
                                        }
                                    } catch (e: Throwable) {
                                        passwordError = if (isEnglish) "Error creating backup: ${e.message}" else "Fehler beim Erstellen des Backups: ${e.message}"
                                    } finally {
                                        isGenerating = false
                                    }
                                }
                            }
                        },
                        enabled = !isGenerating,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isEnglish) "Generating Backup..." else "Backup wird generiert...",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = if (isEnglish) "Create Backup JSON" else "Backup-Code generieren",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    val previewText = remember(generatedBackupJson) {
                        val json = generatedBackupJson ?: ""
                        if (json.length > 1500) {
                            json.take(1200) + "\n\n... [Vollständiges Backup: ${json.length} Zeichen]"
                        } else {
                            json
                        }
                    }

                    val backupSizeKb = remember(generatedBackupJson) {
                        val length = generatedBackupJson?.length ?: 0
                        (length / 1024) + 1
                    }

                    Text(
                        text = if (isEnglish) "Backup generated successfully (~$backupSizeKb KB)" else "Backup erfolgreich generiert (~$backupSizeKb KB)",
                        fontSize = 12.sp,
                        color = GoldLight,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = previewText,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkSurface,
                            unfocusedContainerColor = DarkSurface,
                            focusedTextColor = GoldLight,
                            unfocusedTextColor = GoldLight
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val data = generatedBackupJson ?: ""
                            val copied = PlaylistBackupManager.copyToClipboard(context, data)
                            if (copied) {
                                isCopied = true
                                Toast.makeText(
                                    context,
                                    if (isEnglish) "Backup copied to clipboard!" else "Backup in Zwischenablage kopiert!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    if (isEnglish) "Clipboard restricted on device. Opening share dialog..." else "Zwischenablage auf Gerät beschränkt. Öffne Teilen-Menü...",
                                    Toast.LENGTH_LONG
                                ).show()
                                PlaylistBackupManager.shareBackup(context, data)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = if (isCopied) AccentEmerald else AccentCyan),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = Color.Black
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isCopied) {
                                if (isEnglish) "Copied to Clipboard!" else "In Zwischenablage kopiert!"
                            } else {
                                if (isEnglish) "Copy Backup to Clipboard" else "In Zwischenablage kopieren"
                            },
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            val data = generatedBackupJson ?: ""
                            PlaylistBackupManager.shareBackup(context, data)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = GoldLight,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isEnglish) "Share / Save as File" else "Backup teilen / als Datei speichern",
                            color = GoldLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isEnglish) "Close" else "Fertig / Schließen",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BackupRestoreDialog(
    onDismiss: () -> Unit,
    onRestoreBackup: (json: String, password: String?) -> Unit,
    isEnglish: Boolean = false
) {
    val context = LocalContext.current
    var backupJsonInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var restoreError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, AccentCyan, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = DarkNavyCard)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEnglish) "Restore Playlists" else "Playlisten wiederherstellen",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Hinweistext gemäß Anforderung
                Text(
                    text = if (isEnglish) "The backup function requires a password." else "Die Backupfunktion erfordert ein Passwort.",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEnglish) "Backup JSON Data:" else "Backup-JSON Daten:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan
                    )

                    TextButton(
                        onClick = {
                            try {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                if (clipboard != null && clipboard.hasPrimaryClip() && (clipboard.primaryClip?.itemCount ?: 0) > 0) {
                                    val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                                    if (!clipText.isNullOrBlank()) {
                                        backupJsonInput = clipText
                                        Toast.makeText(
                                            context,
                                            if (isEnglish) "Pasted from clipboard" else "Aus Zwischenablage eingefügt",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            } catch (e: Throwable) {
                                e.printStackTrace()
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Paste",
                            tint = AccentCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isEnglish) "Paste" else "Einfügen",
                            fontSize = 11.sp,
                            color = AccentCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = backupJsonInput,
                    onValueChange = {
                        backupJsonInput = it
                        restoreError = null
                    },
                    label = { Text(if (isEnglish) "Paste Backup JSON here" else "Backup-JSON hier einfügen") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = BorderGlow.copy(alpha = 0.4f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = {
                        passwordInput = it
                        restoreError = null
                    },
                    label = { Text(if (isEnglish) "Password" else "Passwort") },
                    placeholder = { Text(if (isEnglish) "Enter password (e.g. 1971)" else "Passwort eingeben (z. B. 1971)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                    ),
                    isError = restoreError != null,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = if (restoreError != null) com.example.ui.theme.AccentRose else BorderGlow.copy(alpha = 0.4f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                if (restoreError != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = restoreError!!,
                        color = com.example.ui.theme.AccentRose,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        val trimmedJson = backupJsonInput.trim()
                        val trimmedPassword = passwordInput.trim()
                        if (trimmedJson.isBlank()) {
                            restoreError = if (isEnglish) "Please enter backup JSON data." else "Bitte Backup-JSON Daten eingeben."
                        } else if (trimmedPassword.isBlank()) {
                            restoreError = if (isEnglish) "The backup function requires a password." else "Die Backupfunktion erfordert ein Passwort."
                        } else if (trimmedPassword.length < 4) {
                            restoreError = if (isEnglish) "Password must be at least 4 characters (e.g. PIN 1971)." else "Das Passwort muss mindestens 4 Zeichen lang sein (z. B. PIN 1971)."
                        } else {
                            onRestoreBackup(trimmedJson, trimmedPassword)
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (isEnglish) "Restore All Playlists" else "Alle Playlisten wiederherstellen",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
