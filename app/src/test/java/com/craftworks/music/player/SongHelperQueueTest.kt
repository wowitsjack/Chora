package com.craftworks.music.player

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongHelperQueueTest {
    @Test
    fun `top insertion keeps the queue and places the song after current`() {
        val queue = mutableListOf(item("current"), item("next"), item("later"))

        val insertedAt = SongHelper.insertQueueItem(
            queue = queue,
            song = item("selected"),
            currentIndex = 0,
            addToBottom = false
        )

        assertEquals(1, insertedAt)
        assertEquals(listOf("current", "selected", "next", "later"), queue.ids())
    }

    @Test
    fun `bottom insertion appends without replacing queued songs`() {
        val queue = mutableListOf(item("current"), item("next"), item("later"))

        val insertedAt = SongHelper.insertQueueItem(
            queue = queue,
            song = item("selected"),
            currentIndex = 0,
            addToBottom = true
        )

        assertEquals(3, insertedAt)
        assertEquals(listOf("current", "next", "later", "selected"), queue.ids())
    }

    @Test
    fun `top insertion starts the queue when there is no current song`() {
        val queue = mutableListOf<MediaItem>()

        val insertedAt = SongHelper.insertQueueItem(
            queue = queue,
            song = item("selected"),
            currentIndex = null,
            addToBottom = false
        )

        assertEquals(0, insertedAt)
        assertEquals(listOf("selected"), queue.ids())
    }

    @Test
    fun `logical queue publishes replacements and reorders to every shell`() {
        SongHelper.currentTracklist = mutableListOf(item("one"), item("two"), item("three"))

        assertEquals(
            listOf("one", "two", "three"),
            SongHelper.currentTracklistFlow.value.ids()
        )

        SongHelper.moveItem(fromIndex = 2, toIndex = 0)

        assertEquals(
            listOf("three", "one", "two"),
            SongHelper.currentTracklistFlow.value.ids()
        )

        SongHelper.currentTracklist = mutableListOf()
    }

    @Test
    fun `errored or idle playback rebuilds the current source`() {
        assertTrue(SongHelper.needsPlaybackRebuild(Player.STATE_READY, hasPlayerError = true))
        assertTrue(SongHelper.needsPlaybackRebuild(Player.STATE_IDLE, hasPlayerError = false))
        assertFalse(SongHelper.needsPlaybackRebuild(Player.STATE_READY, hasPlayerError = false))
    }

    @Test
    fun `queue window extends before playback reaches item 51`() {
        try {
            SongHelper.currentTracklist = (0 until 120)
                .map { item("track-$it") }
                .toMutableList()

            val extension = SongHelper.planQueueWindowExtension(
                currentIndex = 45,
                visibleItemCount = 50
            )

            requireNotNull(extension)
            assertFalse(extension.insertAtStart)
            assertEquals(
                (50 until 100).map { "track-$it" },
                extension.items.ids()
            )
            assertTrue(SongHelper.isQueueWindowExtensionCurrent(extension))
            assertTrue(SongHelper.commitQueueWindowExtension(extension))
        } finally {
            SongHelper.currentTracklist = mutableListOf()
        }
    }

    @Test
    fun `stale queue extension is rejected after the logical queue changes`() {
        try {
            SongHelper.currentTracklist = (0 until 60)
                .map { item("track-$it") }
                .toMutableList()
            val extension = requireNotNull(
                SongHelper.planQueueWindowExtension(
                    currentIndex = 45,
                    visibleItemCount = 50
                )
            )

            SongHelper.currentTracklist = (0 until 70)
                .map { item("replacement-$it") }
                .toMutableList()

            assertFalse(SongHelper.isQueueWindowExtensionCurrent(extension))
            assertFalse(SongHelper.commitQueueWindowExtension(extension))
        } finally {
            SongHelper.currentTracklist = mutableListOf()
        }
    }

    private fun item(id: String): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .build()

    private fun List<MediaItem>.ids(): List<String> = map(MediaItem::mediaId)
}
