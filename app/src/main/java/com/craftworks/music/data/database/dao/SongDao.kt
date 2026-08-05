package com.craftworks.music.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.craftworks.music.data.database.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs WHERE mediaCategory = 'music' ORDER BY title COLLATE NOCASE ASC")
    fun getAllSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE mediaCategory = 'music' ORDER BY title COLLATE NOCASE ASC")
    suspend fun getAllSongsOnce(): List<SongEntity>

    @Query("SELECT * FROM songs WHERE albumId = :albumId AND mediaCategory = 'music' ORDER BY discNumber, track")
    fun getSongsByAlbum(albumId: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE artistId = :artistId AND mediaCategory = 'music' ORDER BY album, discNumber, track")
    fun getSongsByArtist(artistId: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE navidromeID = :id")
    suspend fun getSongById(id: String): SongEntity?

    @Query("SELECT * FROM songs WHERE mediaCategory = 'music' ORDER BY dateAdded DESC LIMIT :limit")
    fun getRecentlyAdded(limit: Int = 50): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE mediaCategory = 'music' ORDER BY timesPlayed DESC LIMIT :limit")
    fun getMostPlayed(limit: Int = 50): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE mediaCategory = 'music' AND lastPlayed IS NOT NULL AND lastPlayed != '' ORDER BY lastPlayed DESC LIMIT :limit")
    fun getRecentlyPlayed(limit: Int = 50): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE mediaCategory = 'music' ORDER BY RANDOM() LIMIT :limit")
    fun getRandomSongs(limit: Int = 50): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE mediaCategory = 'music' ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomSongsOnce(limit: Int = 50): List<SongEntity>

    @Query("SELECT * FROM songs WHERE mediaCategory = 'music' AND starred IS NOT NULL AND starred != ''")
    fun getStarredSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE mediaCategory = 'audiobook' ORDER BY album, discNumber, track, title COLLATE NOCASE ASC")
    fun getAllAudiobookParts(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE mediaCategory = 'audiobook' ORDER BY album, discNumber, track, title COLLATE NOCASE ASC")
    suspend fun getAllAudiobookPartsOnce(): List<SongEntity>

    @Query("SELECT * FROM songs WHERE albumId = :albumId AND mediaCategory = 'audiobook' ORDER BY discNumber, track, title COLLATE NOCASE ASC")
    fun getAudiobookPartsByAlbum(albumId: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE albumId = :albumId AND mediaCategory = 'audiobook' ORDER BY discNumber, track, title COLLATE NOCASE ASC")
    suspend fun getAudiobookPartsByAlbumOnce(albumId: String): List<SongEntity>

    @Query("SELECT * FROM songs WHERE albumId = :albumId AND mediaCategory = 'audiobook' ORDER BY discNumber, track, title COLLATE NOCASE ASC LIMIT 1")
    suspend fun getFirstAudiobookPartByAlbum(albumId: String): SongEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<SongEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: SongEntity)

    @Query("DELETE FROM songs WHERE navidromeID = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM songs")
    suspend fun deleteAll()

    @Query("DELETE FROM songs WHERE lastSyncedAt < :syncStartedAt")
    suspend fun deleteNotSeenSince(syncStartedAt: Long): Int

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM songs")
    fun getCountFlow(): Flow<Int>

    // Get all existing song IDs for delta sync
    @Query("SELECT navidromeID FROM songs")
    suspend fun getAllNavidromeIds(): List<String>

    @Query("SELECT * FROM songs WHERE albumId IN (:albumIds) AND mediaCategory = 'music' ORDER BY albumId, discNumber, track")
    suspend fun getSongsByAlbumIds(albumIds: List<String>): List<SongEntity>

    @Query("SELECT * FROM songs WHERE artistId IN (:artistIds) AND mediaCategory = 'music' ORDER BY album, discNumber, track")
    suspend fun getSongsByArtistIds(artistIds: List<String>): List<SongEntity>

    @Query("SELECT * FROM songs WHERE albumId = :albumId AND mediaCategory = 'music' ORDER BY discNumber, track")
    suspend fun getSongsByAlbumOnce(albumId: String): List<SongEntity>

    @Query("SELECT * FROM songs WHERE album = :albumName COLLATE NOCASE AND mediaCategory = 'music' ORDER BY discNumber, track")
    suspend fun getSongsByAlbumNameOnce(albumName: String): List<SongEntity>

    /**
     * Replaces all songs atomically.
     * Uses batch processing to avoid memory issues with large datasets.
     * The @Transaction annotation ensures all-or-nothing semantics.
     */
    @Transaction
    suspend fun replaceAll(songs: List<SongEntity>) {
        deleteAll()
        // Batch insert to avoid memory pressure with large lists
        songs.chunked(500).forEach { batch ->
            insertAll(batch)
        }
    }

    @Query("UPDATE songs SET starred = :starred WHERE navidromeID = :id")
    suspend fun updateStarred(id: String, starred: String?)

    @Query("UPDATE songs SET bookmarkPosition = :positionMs WHERE navidromeID = :id")
    suspend fun updateBookmarkPosition(id: String, positionMs: Long)

    @Upsert
    suspend fun upsert(song: SongEntity)

    @Upsert
    suspend fun upsertAll(songs: List<SongEntity>)
}
