package com.craftworks.music.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

object MediaCategory {
    const val MUSIC = "music"
    const val AUDIOBOOK = "audiobook"

    fun resolve(
        explicit: String? = null,
        libraryName: String? = null,
        path: String? = null,
        format: String? = null
    ): String {
        val normalized = explicit?.trim()?.lowercase()
        if (normalized == MUSIC || normalized == AUDIOBOOK) return normalized

        if (format.equals("m4b", ignoreCase = true)) return AUDIOBOOK

        val markers = listOfNotNull(libraryName, path)
            .joinToString(" ")
            .lowercase()
        return if (
            markers.contains("audiobook") ||
            markers.contains("audio book") ||
            markers.contains("spoken word")
        ) {
            AUDIOBOOK
        } else {
            MUSIC
        }
    }
}

@Immutable
@Serializable
data class AudiobookChapter(
    val id: String,
    val title: String,
    val startTimeMs: Long,
    val endTimeMs: Long
)

@Immutable
@Serializable
data class NavidromeBookmark(
    val entry: MediaData.Song,
    val position: Long = 0L,
    val username: String = "",
    val comment: String = "",
    val created: String = "",
    val changed: String = ""
)

fun isAudiobookMedia(category: String?, format: String? = null, path: String? = null): Boolean =
    MediaCategory.resolve(explicit = category, path = path, format = format) == MediaCategory.AUDIOBOOK
