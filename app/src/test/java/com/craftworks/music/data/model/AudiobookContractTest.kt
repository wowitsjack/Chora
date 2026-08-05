package com.craftworks.music.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AudiobookContractTest {
    @Test
    fun `explicit library type wins over compatibility inference`() {
        assertEquals(
            MediaCategory.MUSIC,
            MediaCategory.resolve(explicit = "music", format = "m4b", path = "Audiobooks/book.m4b")
        )
        assertEquals(
            MediaCategory.AUDIOBOOK,
            MediaCategory.resolve(explicit = "audiobook", format = "mp3")
        )
    }

    @Test
    fun `m4b and strong library markers classify legacy audiobook content`() {
        assertEquals(MediaCategory.AUDIOBOOK, MediaCategory.resolve(format = "M4B"))
        assertEquals(
            MediaCategory.AUDIOBOOK,
            MediaCategory.resolve(libraryName = "Spoken Word")
        )
        assertEquals(MediaCategory.MUSIC, MediaCategory.resolve(path = "Music/Books of Love/song.mp3"))
    }
}
