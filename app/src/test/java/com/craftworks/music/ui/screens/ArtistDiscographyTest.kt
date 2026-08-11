package com.craftworks.music.ui.screens

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistDiscographyTest {
    @Test
    fun groupsEveryArtistSongByAlbumAndOrdersTracksWithinEachAlbum() {
        val albums = listOf(
            album("new", "New Album"),
            album("old", "Old Album")
        )
        val songs = listOf(
            song("new-2", "Second", "New Album", disc = 1, track = 2),
            song("old-1", "Old First", "Old Album", disc = 1, track = 1),
            song("new-1", "First", "New Album", disc = 1, track = 1),
            song("new-d2", "Disc Two", "New Album", disc = 2, track = 1)
        )

        val discography = buildArtistDiscography(albums, songs)

        assertEquals(listOf("New Album", "Old Album"), discography.groups.map { it.title })
        assertEquals(
            listOf("new-1", "new-2", "new-d2", "old-1"),
            discography.orderedSongs.map { it.mediaId }
        )
    }

    @Test
    fun unmatchedSongsRemainVisibleInAlphabeticalAlbumGroups() {
        val songs = listOf(
            song("z", "Zed", "Zulu", disc = null, track = null),
            song("a", "Alpha", "Alpha", disc = null, track = null)
        )

        val discography = buildArtistDiscography(emptyList(), songs)

        assertEquals(listOf("Alpha", "Zulu"), discography.groups.map { it.title })
        assertEquals(listOf("a", "z"), discography.orderedSongs.map { it.mediaId })
    }

    private fun album(id: String, title: String): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setAlbumTitle(title)
                .build()
        )
        .build()

    private fun song(
        id: String,
        title: String,
        album: String,
        disc: Int?,
        track: Int?
    ): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setAlbumTitle(album)
                .setDiscNumber(disc)
                .setTrackNumber(track)
                .build()
        )
        .build()
}
