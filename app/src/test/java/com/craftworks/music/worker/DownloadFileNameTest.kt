package com.craftworks.music.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadFileNameTest {

    @Test
    fun `path characters cannot escape the music directory`() {
        val fileName = buildDownloadFileName(
            title = "../../A/B:C?",
            artist = "../Artist\\Name",
            format = "../../apk",
            mediaId = "song-1"
        )

        assertFalse('/' in fileName)
        assertFalse('\\' in fileName)
        assertTrue(fileName.endsWith(".audio"))
    }

    @Test
    fun `blank components receive readable fallbacks`() {
        val fileName = buildDownloadFileName("???", "...", "mp3", "song-2")

        assertTrue(fileName.startsWith("Untitled - Unknown Artist"))
        assertTrue(fileName.endsWith(".mp3"))
    }

    @Test
    fun `same metadata with different media ids cannot overwrite`() {
        val first = buildDownloadFileName("Title", "Artist", "flac", "id-one")
        val second = buildDownloadFileName("Title", "Artist", "flac", "id-two")

        assertNotEquals(first, second)
    }

    @Test
    fun `unicode names are preserved and extension is normalized`() {
        val fileName = buildDownloadFileName("Beyoncé", "Björk", ".FLAC", "song-3")

        assertTrue(fileName.startsWith("Beyoncé - Björk"))
        assertEquals("flac", fileName.substringAfterLast('.'))
    }
}
