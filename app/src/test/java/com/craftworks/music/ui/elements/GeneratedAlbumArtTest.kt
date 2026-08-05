package com.craftworks.music.ui.elements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class GeneratedAlbumArtTest {

    @Test
    fun `static artwork identity is deterministic`() {
        val first = generatedArtworkSeed("Album", "Artist", "Release")
        val second = generatedArtworkSeed("Album", "Artist", "Release")

        assertEquals(first, second)
    }

    @Test
    fun `presentation seed mutates the same artwork identity`() {
        val first = generatedArtworkSeed("Album", "Artist", "Release", variationSeed = 1L)
        val second = generatedArtworkSeed("Album", "Artist", "Release", variationSeed = 2L)

        assertNotEquals(first, second)
    }

    @Test
    fun `different library identities get different stable artwork seeds`() {
        val first = generatedArtworkSeed("Born Gangstaz", "Boss", "album-1")
        val second = generatedArtworkSeed("The Sound of Dubstep", "Breakage", "album-2")

        assertNotEquals(first, second)
    }

    @Test
    fun `same visible metadata still varies by library identity`() {
        val first = generatedArtworkSeed("Greatest Hits", "The Artist", "album-1")
        val second = generatedArtworkSeed("Greatest Hits", "The Artist", "album-2")

        assertNotEquals(first, second)
        assertNotEquals(
            generatedArtworkColorSignature("Greatest Hits", "The Artist", "album-1"),
            generatedArtworkColorSignature("Greatest Hits", "The Artist", "album-2")
        )
    }
}
