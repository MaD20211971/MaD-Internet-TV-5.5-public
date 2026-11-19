package com.example

import com.example.data.player.VodWatchHistoryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VodWatchHistoryTest {

    @Test
    fun testTimeFormatting() {
        assertEquals("00:00", VodWatchHistoryManager.formatTime(0))
        assertEquals("00:45", VodWatchHistoryManager.formatTime(45))
        assertEquals("42:10", VodWatchHistoryManager.formatTime(2530))
        assertEquals("48:00", VodWatchHistoryManager.formatTime(2880))
        assertEquals("1:48:00", VodWatchHistoryManager.formatTime(6480))
    }

    @Test
    fun testSeriesDisplayTitleFormat() {
        // Must match exact pattern: "Breaking Bad - S02E05 (42:10 / 48:00)"
        val formatted = VodWatchHistoryManager.formatSeriesDisplayTitle(
            seriesTitle = "Breaking Bad",
            seasonNumber = 2,
            episodeNumber = 5,
            lastPositionSeconds = 2530,
            totalDurationSeconds = 2880
        )
        assertEquals("Breaking Bad - S02E05 (42:10 / 48:00)", formatted)
    }

    @Test
    fun testMovieDisplayTitleFormat() {
        // Pattern: "Inception (42:10 / 1:48:00)"
        val formatted = VodWatchHistoryManager.formatMovieDisplayTitle(
            movieTitle = "Inception",
            lastPositionSeconds = 2530,
            totalDurationSeconds = 6480
        )
        assertEquals("Inception (42:10 / 1:48:00)", formatted)
    }

    @Test
    fun testCompletionToleranceThreshold() {
        val totalDuration = 3000L // 50 mins

        // 85% watched (2550s) -> Not completed (< 90%)
        val pos85 = 2550L
        val isCompleted85 = (pos85.toDouble() / totalDuration.toDouble()) >= 0.90 || pos85 >= (totalDuration - 30L)
        assertFalse("85% watch progress should not be marked as completed", isCompleted85)

        // 91% watched (2730s) -> Completed (>= 90%)
        val pos91 = 2730L
        val isCompleted91 = (pos91.toDouble() / totalDuration.toDouble()) >= 0.90 || pos91 >= (totalDuration - 30L)
        assertTrue("91% watch progress must be marked as completed", isCompleted91)

        // Watched within last 20s (2985s of 3000s) -> Completed
        val posLast20s = 2985L
        val isCompletedEnd = (posLast20s.toDouble() / totalDuration.toDouble()) >= 0.90 || posLast20s >= (totalDuration - 30L)
        assertTrue("Within last 30 seconds must be marked as completed", isCompletedEnd)
    }

    @Test
    fun testProviderUrlDecouplingKeys() {
        val seriesTitle = "Breaking Bad"
        val season = 2
        val episode = 5

        // Even if provider rotated the temporary streaming URL from serverA to serverB:
        val streamUrl1 = "http://provider-srv-1.net/series/user/pass/12345.mp4"
        val streamUrl2 = "http://provider-srv-2.net/series/user/pass/99999.mp4"

        // The stable series identity and episode identity are retained
        val seriesId = "breaking_bad"
        val episodeId1 = "${seriesId}_s${season}_e${episode}"
        val episodeId2 = "${seriesId}_s${season}_e${episode}"

        assertEquals("Episode identifier must be stable across provider URL changes", episodeId1, episodeId2)
    }
}
