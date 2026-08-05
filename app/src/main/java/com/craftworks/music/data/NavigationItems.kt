package com.craftworks.music.data

import androidx.compose.runtime.Stable
import com.craftworks.music.R
import kotlinx.serialization.Serializable

@Stable
@Serializable
data class BottomNavItem(
    var title: String,
    var icon: Int,
    val screenRoute: String,
    var enabled: Boolean = true
)

fun defaultBottomNavItems(): List<BottomNavItem> = listOf(
    BottomNavItem("Home", R.drawable.rounded_home_24, "home_screen"),
    BottomNavItem("Books", R.drawable.rounded_auto_stories_24, "audiobooks_screen"),
    BottomNavItem("Albums", R.drawable.rounded_library_music_24, "album_screen"),
    BottomNavItem("Songs", R.drawable.round_music_note_24, "songs_screen"),
    BottomNavItem("Artists", R.drawable.rounded_artist_24, "artists_screen"),
    BottomNavItem("Radios", R.drawable.rounded_radio, "radio_screen", enabled = false),
    BottomNavItem("Playlists", R.drawable.placeholder, "playlist_screen")
)

fun normalizeBottomNavItems(items: List<BottomNavItem>): List<BottomNavItem> {
    val migrated = items.map { item ->
        when (item.screenRoute) {
            "audiobooks_screen" -> item.copy(title = "Books")
            "radio_screen" -> item.copy(enabled = false)
            else -> item
        }
    }.toMutableList()
    if (migrated.any { it.screenRoute == "audiobooks_screen" }) return migrated
    migrated.add(
        index = 1.coerceAtMost(migrated.size),
        element = BottomNavItem(
            "Books",
            R.drawable.rounded_auto_stories_24,
            "audiobooks_screen"
        )
    )
    return migrated
}
