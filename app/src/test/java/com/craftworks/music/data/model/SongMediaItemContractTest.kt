package com.craftworks.music.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SongMediaItemContractTest {
    @Test
    fun `toMediaItem preserves bpm genre media category and ids`() {
        val fields = song(
            genre = "Legacy Electronic",
            genres = listOf(Genre("Electronic"), Genre("House")),
            bpm = 128
        ).toMediaItemFields()

        assertEquals("Electronic, House", fields.genre)
        assertEquals("song-1", fields.navidromeID)
        assertEquals("album-1", fields.albumId)
        assertEquals("artist-1", fields.artistId)
        assertEquals(MediaCategory.MUSIC, fields.mediaCategory)
        assertEquals(128, fields.bpm)
    }

    @Test
    fun `toSong helpers restore bpm and genre metadata`() {
        assertEquals(128, credibleBpmFromMetadataValue(128))
        assertEquals(130, credibleBpmFromMetadataValue("130"))
        assertEquals(0, credibleBpmFromMetadataValue("fast"))
        assertEquals(0, credibleBpmFromMetadataValue(400))
        assertEquals(
            listOf("Electronic", "House"),
            simpleGenresFromMetadata("Electronic, House").map { it.name }
        )
    }

    @Test
    fun `genre falls back to legacy genre string when genres list is empty`() {
        val fields = song(
            genre = "Downtempo",
            genres = emptyList(),
            bpm = 90
        ).toMediaItemFields()

        assertEquals("Downtempo", fields.genre)
        assertEquals(listOf("Downtempo"), simpleGenresFromMetadata(fields.genre.orEmpty()).map { it.name })
        assertEquals(90, fields.bpm)
    }

    private fun song(
        genre: String?,
        genres: List<Genre>?,
        bpm: Int
    ): MediaData.Song =
        MediaData.Song(
            navidromeID = "song-1",
            parent = "album-1",
            title = "Smart Song",
            album = "Smart Album",
            artist = "Smart Artist",
            artists = listOf(Artists("artist-1", "Smart Artist")),
            track = 1,
            year = 2024,
            genre = genre,
            imageUrl = "https://example.test/cover.jpg",
            format = "flac",
            duration = 180,
            bitrate = 900,
            path = "/music/smart-song.flac",
            discNumber = 1,
            dateAdded = "2024-01-01T00:00:00Z",
            albumId = "album-1",
            artistId = "artist-1",
            bpm = bpm,
            genres = genres,
            mediaCategory = MediaCategory.MUSIC,
            media = "https://example.test/song.flac"
        )
}
