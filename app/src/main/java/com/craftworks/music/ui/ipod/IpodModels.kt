package com.craftworks.music.ui.ipod

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
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

@Stable
internal class IpodNavigationStack<T> {
    private val entries = mutableStateListOf<T>()

    val current: T?
        get() = entries.lastOrNull()

    val previous: T?
        get() = entries.getOrNull(entries.lastIndex - 1)

    val size: Int
        get() = entries.size

    fun push(destination: T) {
        entries.add(destination)
    }

    fun pop(): T? {
        if (entries.isEmpty()) return null
        return entries.removeAt(entries.lastIndex)
    }

    fun clear() {
        entries.clear()
    }

    fun pathKey(root: String, keyOf: (T) -> String): String {
        return buildString {
            append(root)
            entries.forEach { destination ->
                append('/')
                append(keyOf(destination))
            }
        }
    }
}

internal data class IpodDiscographySortEntry(
    val sourceIndex: Int,
    val albumKey: String,
    val albumTitle: String,
    val discNumber: Int?,
    val trackNumber: Int?,
    val title: String
)

internal fun orderedIpodDiscographyIndices(
    albumOrder: List<String>,
    entries: List<IpodDiscographySortEntry>
): List<Int> {
    val albumPositions = albumOrder.withIndex().associate { (index, key) -> key to index }
    return entries.sortedWith(
        compareBy<IpodDiscographySortEntry> {
            albumPositions[it.albumKey] ?: Int.MAX_VALUE
        }.thenBy {
            if (albumPositions.containsKey(it.albumKey)) "" else it.albumTitle.lowercase()
        }.thenBy {
            it.discNumber?.takeIf { number -> number > 0 } ?: Int.MAX_VALUE
        }.thenBy {
            it.trackNumber?.takeIf { number -> number > 0 } ?: Int.MAX_VALUE
        }.thenBy { it.title.lowercase() }
            .thenBy { it.sourceIndex }
    ).map { it.sourceIndex }
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

internal fun IpodDestination.stateKey(): String = when (this) {
    is IpodDestination.Album -> "album:${item.stableIpodId()}"
    is IpodDestination.Artist -> "artist:${item.navidromeID.ifBlank { item.name }}"
    is IpodDestination.Playlist -> "playlist:${item.stableIpodId()}"
    is IpodDestination.Audiobook -> "audiobook:${book.id}"
    IpodDestination.Queue -> "queue"
    IpodDestination.Radio -> "radio"
}

private fun MediaItem.stableIpodId(): String {
    return mediaMetadata.extras?.getString("navidromeID")
        ?.takeIf { it.isNotBlank() }
        ?: mediaId
}
