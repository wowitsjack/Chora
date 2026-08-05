package com.craftworks.music.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncStateTest {

    @Test
    fun `percentage is clamped to a valid progress range`() {
        assertEquals(0f, SyncState(current = -5, total = 10).percentage)
        assertEquals(50f, SyncState(current = 5, total = 10).percentage)
        assertEquals(100f, SyncState(current = 15, total = 10).percentage)
        assertEquals(0f, SyncState(current = 5, total = 0).percentage)
    }

    @Test
    fun `song progress explains that albums are being processed`() {
        val state = SyncState(
            phase = SyncPhase.SONGS,
            current = 125,
            total = 500
        )

        assertEquals("Syncing songs", state.shortTitle)
        assertEquals("albums", state.progressUnit)
        assertEquals(25f, state.percentage)
    }

    @Test
    fun `known total stays indeterminate until the first item completes`() {
        val preparing = SyncState(
            phase = SyncPhase.SONGS,
            current = 0,
            total = 500
        )

        assertEquals(false, preparing.hasDeterminateProgress)
        assertEquals("Preparing the first of 500 albums...", preparing.displayText)
        assertEquals(true, preparing.copy(current = 1).hasDeterminateProgress)
    }

    @Test
    fun `all active sync phases have concise titles`() {
        assertEquals("Checking library", SyncState(phase = SyncPhase.FETCHING_COUNTS).shortTitle)
        assertEquals("Syncing artists", SyncState(phase = SyncPhase.ARTISTS).shortTitle)
        assertEquals("Syncing albums", SyncState(phase = SyncPhase.ALBUMS).shortTitle)
        assertEquals("Sync complete", SyncState(phase = SyncPhase.COMPLETE).shortTitle)
        assertEquals("Sync needs attention", SyncState(phase = SyncPhase.ERROR).shortTitle)
    }

    @Test
    fun `sync failure keeps a useful message and failed phase`() {
        val state = SyncState(
            phase = SyncPhase.ERROR,
            message = "Song sync stopped. Check the connection and retry.",
            failedPhase = SyncPhase.SONGS
        )

        assertEquals("Song sync stopped. Check the connection and retry.", state.displayText)
        assertEquals(SyncPhase.SONGS, state.failedPhase)
    }

    @Test
    fun `album resume skips completed libraries and keeps the exact page offset`() {
        val cursor = AlbumSyncCursor(
            libraryIndex = 1,
            libraryOffset = 1_000,
            processedCount = 1_500
        )

        assertNull(albumLibraryOffsetForResume(libraryIndex = 0, cursor = cursor))
        assertEquals(1_000, albumLibraryOffsetForResume(libraryIndex = 1, cursor = cursor))
        assertEquals(0, albumLibraryOffsetForResume(libraryIndex = 2, cursor = cursor))
        assertEquals(1_500, cursor.processedCount)
    }
}
