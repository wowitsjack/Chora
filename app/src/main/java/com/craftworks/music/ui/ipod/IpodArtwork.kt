package com.craftworks.music.ui.ipod

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.craftworks.music.ui.elements.GeneratedAlbumArtStatic
import com.craftworks.music.ui.elements.artworkDataAtSize

@Composable
internal fun IpodArtwork(
    artwork: Any?,
    title: String,
    artist: String?,
    identity: String,
    modifier: Modifier = Modifier
) {
    val artworkValue = artwork?.toString()
    val fallback: @Composable () -> Unit = {
        GeneratedAlbumArtStatic(
            title = title,
            artist = artist,
            album = identity,
            modifier = Modifier.fillMaxSize()
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.background(Color.Black)
    ) {
        if (shouldGenerateArtwork(artworkValue)) {
            fallback()
        } else {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(artworkDataAtSize(artwork, 1024))
                    .diskCacheKey("ipod_${identity}_art_1024")
                    .memoryCacheKey("ipod_${identity}_art_1024")
                    .crossfade(false)
                    .build(),
                contentDescription = "$title album artwork",
                contentScale = ContentScale.Crop,
                loading = { fallback() },
                error = { fallback() },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

internal fun shouldGenerateArtwork(value: String?): Boolean {
    if (value.isNullOrBlank() || value == "null") return true
    return value.contains("placeholder", ignoreCase = true) ||
        value.endsWith("/coverArt") ||
        value.contains("coverArt?id=&") ||
        (value.contains("coverArt?size=") && !value.contains("id="))
}
