package com.craftworks.music.player

import com.craftworks.music.data.model.AudiobookChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudiobookPlaybackHelperTest {
    private val chapters = listOf(
        AudiobookChapter("one", "One", 0L, 60_000L),
        AudiobookChapter("two", "Two", 60_000L, 120_000L),
        AudiobookChapter("three", "Three", 120_000L, 180_000L)
    )

    @Test
    fun `previous restarts a chapter before crossing its boundary`() {
        assertEquals(
            60_000L,
            AudiobookPlaybackHelper.previousChapterPosition(chapters, 75_000L)
        )
        assertEquals(
            0L,
            AudiobookPlaybackHelper.previousChapterPosition(chapters, 61_000L)
        )
        assertNull(AudiobookPlaybackHelper.previousChapterPosition(chapters, 1_000L))
    }

    @Test
    fun `next selects the following embedded marker`() {
        assertEquals(
            60_000L,
            AudiobookPlaybackHelper.nextChapterPosition(chapters, 10_000L)
        )
        assertEquals(
            120_000L,
            AudiobookPlaybackHelper.nextChapterPosition(chapters, 60_000L)
        )
        assertNull(AudiobookPlaybackHelper.nextChapterPosition(chapters, 179_000L))
    }
}
