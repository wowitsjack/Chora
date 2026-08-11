package com.craftworks.music.data.repository

import androidx.media3.common.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryNoveltyTest {
    @Test
    fun `repeated discovery excludes recent tracks and removes duplicate candidates`() {
        val selected = selectNovelDiscoverySongs(
            songs = listOf(song("old-1"), song("new-1"), song("new-1"), song("new-2")),
            excludedIds = setOf("old-1"),
            limit = 50
        )

        assertEquals(listOf("new-1", "new-2"), selected.map(MediaItem::mediaId))
    }

    @Test
    fun `fully repeated discovery returns no stale fallback`() {
        val selected = selectNovelDiscoverySongs(
            songs = listOf(song("old-1"), song("old-2")),
            excludedIds = setOf("old-1", "old-2"),
            limit = 50
        )

        assertTrue(selected.isEmpty())
    }

    private fun song(id: String): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .build()
}
