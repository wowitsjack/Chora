package com.craftworks.music.data.model

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.craftworks.music.R

const val FAVORITES_PLAYLIST_ID = "favourites"

fun MediaMetadata.isFavorite(): Boolean =
    !extras?.getString("starred").isNullOrBlank()

fun MediaItem.isFavorite(): Boolean = mediaMetadata.isFavorite()

fun favoritesPlaylistMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(FAVORITES_PLAYLIST_ID)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle("Favorites")
            .setIsPlayable(false)
            .setIsBrowsable(true)
            .setArtworkUri("android.resource://com.craftworks.music/${R.drawable.favourites}".toUri())
            .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
            .setExtras(Bundle().apply {
                putString("navidromeID", FAVORITES_PLAYLIST_ID)
            })
            .build()
    )
    .build()
