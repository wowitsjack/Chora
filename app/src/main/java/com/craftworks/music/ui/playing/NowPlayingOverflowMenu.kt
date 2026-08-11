package com.craftworks.music.ui.playing

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.media3.common.MediaItem

@Composable
internal fun NowPlayingOverflowMenu(
    song: MediaItem?,
    color: Color,
    size: Dp,
    isFavorite: Boolean,
    onGoToArtist: () -> Unit,
    onGoToAlbum: () -> Unit,
    onToggleFavorite: () -> Unit,
    onHide: () -> Unit,
    onReplace: () -> Unit
) {
    var expanded by remember(song?.mediaId) { mutableStateOf(false) }
    val extras = song?.mediaMetadata?.extras
    val hasArtist = !extras?.getString("artistId").isNullOrBlank()
    val hasAlbum = !extras?.getString("albumId").isNullOrBlank()
    val navidromeId = extras?.getString("navidromeID")
    val isServerSong = !navidromeId.isNullOrBlank() && !navidromeId.startsWith("Local_")

    IconButton(
        enabled = song != null,
        modifier = Modifier.size(size),
        onClick = { expanded = true }
    ) {
        Icon(
            imageVector = Icons.Rounded.MoreVert,
            contentDescription = "More song actions",
            tint = color,
            modifier = Modifier.size(size * 0.5f)
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text("Go to artist") },
            enabled = hasArtist,
            onClick = {
                expanded = false
                onGoToArtist()
            }
        )
        DropdownMenuItem(
            text = { Text("Go to album") },
            enabled = hasAlbum,
            onClick = {
                expanded = false
                onGoToAlbum()
            }
        )
        DropdownMenuItem(
            text = { Text(if (isFavorite) "Remove from Favorites" else "Add to Favorites") },
            enabled = !navidromeId.isNullOrBlank(),
            onClick = {
                expanded = false
                onToggleFavorite()
            }
        )
        DropdownMenuItem(
            text = { Text("Hide from library") },
            enabled = !navidromeId.isNullOrBlank(),
            onClick = {
                expanded = false
                onHide()
            }
        )
        DropdownMenuItem(
            text = { Text("Replace audio file") },
            enabled = isServerSong,
            onClick = {
                expanded = false
                onReplace()
            }
        )
    }
}
