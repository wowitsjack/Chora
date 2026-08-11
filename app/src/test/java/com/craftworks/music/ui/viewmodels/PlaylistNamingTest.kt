package com.craftworks.music.ui.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistNamingTest {
    @Test
    fun discoveryPlaylistNamesRemainDistinctAcrossRepeatedBuilds() {
        assertEquals(
            "Hidden Gems Mix",
            uniquePlaylistName("Hidden Gems Mix", emptySet())
        )
        assertEquals(
            "Hidden Gems Mix 2",
            uniquePlaylistName("Hidden Gems Mix", setOf("Hidden Gems Mix"))
        )
        assertEquals(
            "Hidden Gems Mix 4",
            uniquePlaylistName(
                "Hidden Gems Mix",
                setOf("Hidden Gems Mix", "Hidden Gems Mix 2", "Hidden Gems Mix 3")
            )
        )
    }
}
