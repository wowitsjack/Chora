package com.craftworks.music.ui.screens

import androidx.media3.common.MediaItem

internal data class ArtistSongGroup(
    val key: String,
    val album: MediaItem?,
    val title: String,
    val year: Int?,
    val songs: List<MediaItem>
)

internal data class ArtistDiscography(
    val groups: List<ArtistSongGroup>
) {
    val orderedSongs: List<MediaItem> = groups.flatMap(ArtistSongGroup::songs)
}

internal fun buildArtistDiscography(
    albums: List<MediaItem>,
    songs: List<MediaItem>
): ArtistDiscography {
    fun normalized(value: String): String = value.trim().lowercase()
    fun albumKey(album: MediaItem): String = album.mediaMetadata.extras
        ?.getString("navidromeID")
        ?.takeIf(String::isNotBlank)
        ?: album.mediaId

    val albumsById = albums.associateBy(::albumKey)
    val albumsByTitle = albums.associateBy { album ->
        normalized(
            album.mediaMetadata.albumTitle?.toString()
                ?: album.mediaMetadata.title?.toString()
                ?: ""
        )
    }

    fun resolvedAlbum(song: MediaItem): MediaItem? {
        val songAlbumId = song.mediaMetadata.extras?.getString("albumId").orEmpty()
        return albumsById[songAlbumId]
            ?: albumsByTitle[normalized(song.mediaMetadata.albumTitle?.toString().orEmpty())]
    }

    fun resolvedKey(song: MediaItem): String {
        resolvedAlbum(song)?.let { return albumKey(it) }
        val songAlbumId = song.mediaMetadata.extras?.getString("albumId").orEmpty()
        val albumTitle = normalized(song.mediaMetadata.albumTitle?.toString().orEmpty())
        return "other:${songAlbumId.ifBlank { albumTitle.ifBlank { "unknown" } }}"
    }

    fun ordered(groupSongs: List<MediaItem>): List<MediaItem> = groupSongs.sortedWith(
        compareBy<MediaItem> {
            it.mediaMetadata.discNumber?.takeIf { number -> number > 0 } ?: Int.MAX_VALUE
        }.thenBy {
            it.mediaMetadata.trackNumber?.takeIf { number -> number > 0 } ?: Int.MAX_VALUE
        }.thenBy {
            it.mediaMetadata.title?.toString().orEmpty().lowercase()
        }.thenBy(MediaItem::mediaId)
    )

    val songsByAlbum = songs.groupBy(::resolvedKey)
    val knownGroups = albums.mapNotNull { album ->
        val key = albumKey(album)
        val groupSongs = songsByAlbum[key].orEmpty()
        if (groupSongs.isEmpty()) return@mapNotNull null
        ArtistSongGroup(
            key = key,
            album = album,
            title = album.mediaMetadata.albumTitle?.toString()
                ?: album.mediaMetadata.title?.toString()
                ?: "Unknown Album",
            year = album.mediaMetadata.recordingYear?.takeIf { it > 0 },
            songs = ordered(groupSongs)
        )
    }
    val knownKeys = knownGroups.mapTo(mutableSetOf(), ArtistSongGroup::key)
    val otherGroups = songs
        .filter { resolvedKey(it) !in knownKeys }
        .groupBy(::resolvedKey)
        .map { (key, groupSongs) ->
            val first = groupSongs.first()
            ArtistSongGroup(
                key = key,
                album = null,
                title = first.mediaMetadata.albumTitle?.toString()?.takeIf(String::isNotBlank)
                    ?: "Other Songs",
                year = first.mediaMetadata.recordingYear?.takeIf { it > 0 },
                songs = ordered(groupSongs)
            )
        }
        .sortedBy { it.title.lowercase() }

    return ArtistDiscography(knownGroups + otherGroups)
}
