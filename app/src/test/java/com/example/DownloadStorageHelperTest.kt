package com.example

import com.example.data.storage.DownloadStorageHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadStorageHelperTest {

    @Test
    fun testSanitizeFileName() {
        val raw = "Das Erste HD [1080p] / * : ?"
        val sanitized = DownloadStorageHelper.sanitizeFileName(raw, "ts")
        assertTrue(sanitized.endsWith(".ts"))
        assertTrue(!sanitized.contains("/"))
        assertTrue(!sanitized.contains(":"))
        assertTrue(!sanitized.contains("?"))
        assertTrue(!sanitized.contains("*"))
    }

    @Test
    fun testSanitizeFileNameWithExistingExtension() {
        val raw = "Movie_Action_2024.mp4"
        val sanitized = DownloadStorageHelper.sanitizeFileName(raw, "mp4")
        assertEquals("Movie_Action_2024.mp4", sanitized)
    }

    @Test
    fun testFormatBytes() {
        assertEquals("0 MB", DownloadStorageHelper.formatBytes(0))
        assertEquals("500 KB", DownloadStorageHelper.formatBytes(500 * 1024))
        assertEquals("12.5 MB", DownloadStorageHelper.formatBytes((12.5 * 1024 * 1024).toLong()))
        assertEquals("1.50 GB", DownloadStorageHelper.formatBytes((1.5 * 1024 * 1024 * 1024).toLong()))
    }
}
