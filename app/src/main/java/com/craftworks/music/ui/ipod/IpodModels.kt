package com.craftworks.music.ui.ipod

import androidx.media3.common.MediaItem
import com.craftworks.music.data.model.MediaData
import com.craftworks.music.data.repository.AudiobookBook

internal enum class IpodTab(val label: String) {
    PLAYLISTS("Playlists"),
    ARTISTS("Artists"),
    SONGS("Songs"),
    ALBUMS("Albums"),
    AUDIOBOOKS("Books"),
    MORE("More")
}

internal sealed interface IpodDestination {
    data class Album(val item: MediaItem) : IpodDestination
    data class Artist(val item: MediaData.Artist) : IpodDestination
    data class Playlist(val item: MediaItem) : IpodDestination
    data class Audiobook(val book: AudiobookBook) : IpodDestination
    data object Queue : IpodDestination
    data object Radio : IpodDestination
}

internal fun IpodDestination.title(): String = when (this) {
    is IpodDestination.Album -> item.mediaMetadata.albumTitle?.toString()
        ?: item.mediaMetadata.title?.toString()
        ?: "Album"
    is IpodDestination.Artist -> item.name
    is IpodDestination.Playlist -> item.mediaMetadata.title?.toString() ?: "Playlist"
    is IpodDestination.Audiobook -> book.album.mediaMetadata.title?.toString() ?: "Audiobook"
    IpodDestination.Queue -> "Queue"
    IpodDestination.Radio -> "Radio"
}
