package com.craftworks.music.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import com.craftworks.music.data.model.Genre
import com.craftworks.music.data.model.MediaData
import com.craftworks.music.data.model.MediaCategory

@Entity(
    tableName = "albums",
    indices = [
        Index(value = ["artistId"]),
        Index(value = ["starred"]),
        Index(value = ["created"]),
        Index(value = ["name"]),
        Index(value = ["year"]),
        Index(value = ["mediaCategory"]),
        Index(value = ["musicFolderId"])
    ]
)
data class AlbumEntity(
    @PrimaryKey
    val navidromeID: String,
    val parent: String?,
    val album: String?,
    val title: String?,
    val name: String?,
    val coverArt: String?,
    val songCount: Int,
    val played: String?,
    val created: String?,
    val duration: Int?,
    val playCount: Int?,
    val artistId: String?,
    val artist: String,
    val year: Int?,
    val genre: String?,
    val genresJson: String?,
    val starred: String?,
    val musicFolderId: Int? = null,
    @ColumnInfo(defaultValue = "'music'")
    val mediaCategory: String = MediaCategory.MUSIC,
    val lastSyncedAt: Long = System.currentTimeMillis()
)

fun AlbumEntity.toMediaDataAlbum(): MediaData.Album {
    val genres = try {
        genresJson?.let { kotlinx.serialization.json.Json.decodeFromString<List<Genre>>(it) }
    } catch (e: Exception) {
        null
    }

    return MediaData.Album(
        navidromeID = navidromeID,
        parent = parent,
        album = album,
        title = title,
        name = name,
        coverArt = coverArt,
        songCount = songCount,
        played = played,
        created = created,
        duration = duration,
        playCount = playCount,
        artistId = artistId,
        artist = artist,
        year = year,
        genre = genre,
        genres = genres,
        starred = starred,
        songs = null,
        musicFolderId = musicFolderId,
        mediaCategory = mediaCategory
    )
}

fun MediaData.Album.toEntity(): AlbumEntity {
    val genresJson = try {
        genres?.let { kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(Genre.serializer()), it) }
    } catch (e: Exception) {
        null
    }

    return AlbumEntity(
        navidromeID = navidromeID,
        parent = parent,
        album = album,
        title = title,
        name = name,
        coverArt = coverArt,
        songCount = songCount,
        played = played,
        created = created,
        duration = duration,
        playCount = playCount,
        artistId = artistId,
        artist = artist,
        year = year,
        genre = genre,
        genresJson = genresJson,
        starred = starred,
        musicFolderId = musicFolderId,
        mediaCategory = MediaCategory.resolve(explicit = mediaCategory)
    )
}
