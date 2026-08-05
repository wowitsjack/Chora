package com.craftworks.music.data.model

import android.os.Bundle
import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Collections
import java.util.UUID

/**
 * @deprecated Global mutable state is deprecated. Use SongRepository instead.
 * This is kept for backwards compatibility with legacy code.
 * TODO: Remove once all usages are migrated to repository pattern.
 */
@Deprecated("Use SongRepository instead of global mutable state")
val songsList: MutableList<MediaData.Song> = Collections.synchronizedList(mutableListOf())

@Immutable
@Serializable
data class Genre(
    val name: String? = ""
)

@Immutable
@Serializable
data class ReplayGain(
    val trackGain: Float? = 0f,
    //val trackPeak: Float? = 0f,
    //val albumPeak: Float? = 0f
)

@Immutable
@Serializable
data class Artists(
    val id: String? = "",
    val name: String? = ""
)

internal data class SongMediaItemFields(
    val navidromeID: String,
    val albumId: String,
    val artistId: String?,
    val mediaCategory: String,
    val genre: String?,
    val bpm: Int?
)

private fun MediaData.Song.resolvedGenreMetadata(): String? {
    val modernGenres = genres.orEmpty()
        .mapNotNull { it.name?.trim()?.takeIf(String::isNotEmpty) }
        .distinctBy { it.lowercase() }
        .joinToString(", ")

    return modernGenres.ifBlank { genre?.trim().orEmpty() }
        .takeIf(String::isNotEmpty)
}

internal fun simpleGenresFromMetadata(genre: String): List<Genre> =
    genre.split(Regex("[,;/|]"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }
        .map { Genre(it) }

private fun Int.credibleBpmOrNull(): Int? = takeIf { it in 40..220 }

internal fun credibleBpmFromMetadataValue(value: Any?): Int =
    when (value) {
        is Int -> value
        is Number -> value.toInt()
        is String -> value.toIntOrNull() ?: 0
        else -> 0
    }.credibleBpmOrNull() ?: 0

@Suppress("DEPRECATION")
private fun Bundle.readCredibleBpm(): Int =
    credibleBpmFromMetadataValue(
        if (containsKey("bpm")) get("bpm") else getInt("bpm", 0)
    )

internal fun MediaData.Song.toMediaItemFields(): SongMediaItemFields {
    val resolvedCategory = MediaCategory.resolve(
        explicit = mediaCategory,
        path = path,
        format = format
    )

    return SongMediaItemFields(
        navidromeID = navidromeID,
        albumId = albumId,
        artistId = artistId,
        mediaCategory = resolvedCategory,
        genre = resolvedGenreMetadata(),
        bpm = bpm.credibleBpmOrNull()
    )
}

fun MediaData.Song.toMediaItem(): MediaItem {
    val fields = toMediaItemFields()
    val mediaMetadata =
        MediaMetadata.Builder()
            .setTitle(this@toMediaItem.title)
            .setArtist(this@toMediaItem.artist)
            .setAlbumTitle(this@toMediaItem.album)
            .setArtworkUri(this@toMediaItem.imageUrl.toUri())
            .setRecordingYear(this@toMediaItem.year)
            .setDiscNumber(this@toMediaItem.discNumber)
            .setTrackNumber(this@toMediaItem.track)
            .setIsBrowsable(false).setIsPlayable(true)
            .setMediaType(
                if (fields.mediaCategory == MediaCategory.AUDIOBOOK) {
                    MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER
                } else {
                    MediaMetadata.MEDIA_TYPE_MUSIC
                }
            )
            .setDurationMs(this@toMediaItem.duration.times(1000).toLong())
            .setGenre(fields.genre)
            .setExtras(Bundle().apply {
                putString("navidromeID", fields.navidromeID)
                putString("albumId", fields.albumId)
                putString("artistId", fields.artistId)
                putString("lyricsArtist", this@toMediaItem.artists.firstOrNull()?.name ?: this@toMediaItem.artist)
                putInt("duration", this@toMediaItem.duration)
                putString("format", this@toMediaItem.format)
                putLong("bitrate", this@toMediaItem.bitrate?.toLong() ?: 0)
                putBoolean("isRadio", this@toMediaItem.isRadio == true)
                putString("mediaCategory", fields.mediaCategory)
                fields.bpm?.let { putInt("bpm", it) }
                this@toMediaItem.musicFolderId?.let { putInt("musicFolderId", it) }
                putLong("bookmarkPosition", this@toMediaItem.bookmarkPosition.coerceAtLeast(0L))
                if (this@toMediaItem.chapters.isNotEmpty()) {
                    putString(
                        "audiobookChapters",
                        Json.encodeToString(this@toMediaItem.chapters)
                    )
                }
                if (this@toMediaItem.replayGain?.trackGain != null)
                    putFloat("replayGain", this@toMediaItem.replayGain.trackGain)
            }).build()

    return MediaItem.Builder()
        .setMediaId(this@toMediaItem.media.toString())
        .setUri(this@toMediaItem.media?.toUri())
        .setMediaMetadata(mediaMetadata)
        .build()
}

fun MediaItem.toSong(): MediaData.Song {
    val mediaMetadata = this@toSong.mediaMetadata
    val extras = mediaMetadata.extras
    val genre = mediaMetadata.genre?.toString()?.trim().orEmpty()

    val chapters = try {
        extras?.getString("audiobookChapters")
            ?.let { Json.decodeFromString<List<AudiobookChapter>>(it) }
            .orEmpty()
    } catch (_: Exception) {
        emptyList()
    }

    return MediaData.Song(
        navidromeID = extras?.getString("navidromeID") ?: "",
        title = mediaMetadata.title.toString(),
        artist = mediaMetadata.artist.toString(),
        artists = listOf(Artists(UUID.randomUUID().toString(), mediaMetadata.artist.toString())),
        album = mediaMetadata.albumTitle.toString(),
        imageUrl = mediaMetadata.artworkUri.toString(),
        year = mediaMetadata.recordingYear ?: 0,
        duration = mediaMetadata.durationMs?.toInt()?.div(1000) ?: 0,
        format = extras?.getString("format") ?: "",
        bitrate = extras?.getLong("bitrate")?.toInt(),
        media = this@toSong.mediaId.toString(),
        replayGain = ReplayGain(
            trackGain = extras?.getFloat("replayGain") ?: 0f
        ),
        isRadio = extras?.getBoolean("isRadio"),
        path = "",
        parent = "",
        dateAdded = "",
        bpm = extras?.readCredibleBpm() ?: 0,
        albumId = extras?.getString("albumId") ?: "",
        artistId = extras?.getString("artistId"),
        musicFolderId = extras?.takeIf { it.containsKey("musicFolderId") }
            ?.getInt("musicFolderId"),
        mediaCategory = extras?.getString("mediaCategory"),
        bookmarkPosition = extras?.getLong("bookmarkPosition") ?: 0L,
        chapters = chapters,
        genre = genre,
        genres = simpleGenresFromMetadata(genre)
    )
}
