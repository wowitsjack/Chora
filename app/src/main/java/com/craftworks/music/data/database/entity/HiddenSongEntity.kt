package com.craftworks.music.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hidden_songs")
data class HiddenSongEntity(
    @PrimaryKey
    val songId: String,
    val hiddenAt: Long = System.currentTimeMillis()
)
