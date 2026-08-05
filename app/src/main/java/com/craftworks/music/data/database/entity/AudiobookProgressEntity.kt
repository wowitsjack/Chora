package com.craftworks.music.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audiobook_progress",
    indices = [
        Index(value = ["albumId"]),
        Index(value = ["updatedAt"]),
        Index(value = ["completed"]),
        Index(value = ["syncPending"])
    ]
)
data class AudiobookProgressEntity(
    @PrimaryKey
    val songId: String,
    val albumId: String,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val completed: Boolean = false,
    val playbackSpeed: Float = 1f,
    val updatedAt: Long = System.currentTimeMillis(),
    val serverUpdatedAt: Long = 0L,
    val syncPending: Boolean = true
)
