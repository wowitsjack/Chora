package com.craftworks.music.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioSeedSelectorTest {
    @Test
    fun `album radio samples the whole running order`() {
        val selected = selectRadioSeedIds(
            (1..16).map { RadioSeedCandidate("track-$it", "album-a") }
        )

        assertEquals(8, selected.size)
        assertEquals("track-1", selected.first())
        assertEquals("track-16", selected.last())
    }

    @Test
    fun `artist radio spreads seeds across albums`() {
        val candidates = (1..12).flatMap { album ->
            (1..3).map { track ->
                RadioSeedCandidate("album-$album-track-$track", "album-$album")
            }
        }

        val selected = selectRadioSeedIds(candidates)
        val representedAlbums = selected.map { it.substringBefore("-track") }.toSet()

        assertEquals(8, selected.size)
        assertEquals(8, representedAlbums.size)
    }

    @Test
    fun `radio seed selection removes blanks and duplicates`() {
        val selected = selectRadioSeedIds(
            listOf(
                RadioSeedCandidate("", "album-a"),
                RadioSeedCandidate("song-a", "album-a"),
                RadioSeedCandidate("song-a", "album-b"),
                RadioSeedCandidate("song-b", "album-b")
            )
        )

        assertEquals(listOf("song-a", "song-b"), selected)
        assertTrue(selected.none(String::isBlank))
    }
}
