package com.craftworks.music.data.repository

import androidx.work.NetworkType
import com.craftworks.music.data.database.entity.AudiobookProgressEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudiobookProgressContractTest {
    @Test
    fun `durable bookmark sync waits for a connected network`() {
        assertEquals(
            NetworkType.CONNECTED,
            audiobookProgressSyncConstraints().requiredNetworkType
        )
    }

    @Test
    fun `completion accepts ninety five percent or one minute remaining`() {
        assertTrue(isAudiobookCompleted(positionMs = 95_000L, durationMs = 100_000L))
        assertTrue(isAudiobookCompleted(positionMs = 3_541_000L, durationMs = 3_600_000L))
        assertFalse(isAudiobookCompleted(positionMs = 800_000L, durationMs = 1_000_000L))
        assertFalse(isAudiobookCompleted(positionMs = 10_000L, durationMs = 0L))
    }

    @Test
    fun `bookmark reconciliation follows newest timestamp even for rewinds`() {
        assertEquals(
            40_000L,
            reconciledBookmarkPositionMs(
                localPositionMs = 90_000L,
                localUpdatedAt = 1_000L,
                remotePositionMs = 40_000L,
                remoteUpdatedAt = 2_000L
            )
        )
        assertTrue(shouldApplyRemoteBookmark(localUpdatedAt = 1_000L, remoteUpdatedAt = 2_000L))
    }

    @Test
    fun `older remote bookmark cannot resurrect a local restart`() {
        assertEquals(
            0L,
            reconciledBookmarkPositionMs(
                localPositionMs = 0L,
                localUpdatedAt = 2_000L,
                remotePositionMs = 90_000L,
                remoteUpdatedAt = 1_000L
            )
        )
        assertFalse(shouldApplyRemoteBookmark(localUpdatedAt = 2_000L, remoteUpdatedAt = 1_000L))
    }

    @Test
    fun `pending remote bookmarks cannot finish without a server`() {
        val pending = listOf(
            audiobookProgress(songId = "server-song"),
            audiobookProgress(songId = "Local_file")
        )

        assertEquals(listOf("server-song"), remotePendingBookmarks(pending).map { it.songId })
        assertFalse(pendingBookmarkFlushCanFinish(pending, hasActiveServer = false))
        assertTrue(pendingBookmarkFlushCanFinish(pending, hasActiveServer = true))
        assertTrue(remotePendingBookmarks(listOf(audiobookProgress(songId = "Local_file"))).isEmpty())
        assertTrue(
            pendingBookmarkFlushCanFinish(
                listOf(audiobookProgress(songId = "Local_file")),
                hasActiveServer = false
            )
        )
    }

    @Test
    fun `bookmark positions remain in milliseconds`() {
        assertEquals(90_123L, bookmarkPositionMs(90_123L))
        assertEquals(0L, bookmarkPositionMs(-1L))
    }

    @Test
    fun `server bookmark timestamps support fractional RFC3339 values`() {
        val seconds = parseBookmarkTimestamp("2026-08-04T10:20:30Z")
        val fractional = parseBookmarkTimestamp("2026-08-04T10:20:30.123456Z")

        assertTrue(seconds > 0L)
        assertEquals(seconds + 123L, fractional)
    }

    private fun audiobookProgress(songId: String) = AudiobookProgressEntity(
        songId = songId,
        albumId = "album"
    )
}
