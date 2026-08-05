package com.craftworks.music.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.craftworks.music.data.database.entity.AudiobookProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AudiobookProgressDao {
    @Query("SELECT * FROM audiobook_progress ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<AudiobookProgressEntity>>

    @Query("SELECT * FROM audiobook_progress WHERE songId = :songId")
    fun observeBySongId(songId: String): Flow<AudiobookProgressEntity?>

    @Query("SELECT * FROM audiobook_progress WHERE songId = :songId")
    suspend fun getBySongId(songId: String): AudiobookProgressEntity?

    @Query("SELECT * FROM audiobook_progress WHERE albumId = :albumId ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestForAlbum(albumId: String): AudiobookProgressEntity?

    @Query("SELECT * FROM audiobook_progress WHERE albumId = :albumId ORDER BY updatedAt DESC LIMIT 1")
    fun observeLatestForAlbum(albumId: String): Flow<AudiobookProgressEntity?>

    @Query("SELECT * FROM audiobook_progress WHERE syncPending = 1 ORDER BY updatedAt ASC")
    suspend fun getPending(): List<AudiobookProgressEntity>

    @Query("SELECT * FROM audiobook_progress")
    suspend fun getAllOnce(): List<AudiobookProgressEntity>

    @Upsert
    suspend fun upsert(progress: AudiobookProgressEntity)

    @Query(
        "UPDATE audiobook_progress SET syncPending = 0, serverUpdatedAt = :serverUpdatedAt " +
            "WHERE songId = :songId AND updatedAt <= :localUpdatedAt"
    )
    suspend fun markSynced(songId: String, localUpdatedAt: Long, serverUpdatedAt: Long)

    @Query("DELETE FROM audiobook_progress WHERE songId = :songId")
    suspend fun deleteBySongId(songId: String)

    @Query("DELETE FROM audiobook_progress WHERE albumId = :albumId")
    suspend fun deleteByAlbumId(albumId: String)
}
