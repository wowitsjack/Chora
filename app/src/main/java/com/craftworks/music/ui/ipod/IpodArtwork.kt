package com.craftworks.music.ui.ipod

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.craftworks.music.ui.elements.GeneratedAlbumArtStatic
import com.craftworks.music.ui.elements.artworkDataAtSize
import com.craftworks.music.ui.elements.artworkRenderKey
import com.craftworks.music.ui.elements.artworkRequestKey
import kotlinx.coroutines.delay

@Composable
internal fun IpodArtwork(
    artwork: Any?,
    title: String,
    artist: String?,
    identity: String,
    modifier: Modifier = Modifier,
    requestDelayMillis: Long = 0L
) {
    val artworkValue = artwork?.toString()
    val artworkData = artworkDataAtSize(artwork, 1024)
    val requestKey = artworkRequestKey("ipod", identity, artworkData, 1024)
    val renderKey = artworkRenderKey(identity, requestKey)
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
        key(renderKey) {
            var requestReady by remember(requestDelayMillis) {
                mutableStateOf(requestDelayMillis <= 0L)
            }
            LaunchedEffect(requestDelayMillis) {
                if (requestDelayMillis > 0L) {
                    delay(requestDelayMillis)
                    requestReady = true
                }
            }

            if (shouldGenerateArtwork(artworkValue) || !requestReady) {
                fallback()
            } else {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(artworkData)
                        .diskCacheKey(requestKey)
                        .memoryCacheKey(requestKey)
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
}

internal fun shouldGenerateArtwork(value: String?): Boolean {
    if (value.isNullOrBlank() || value == "null") return true
    return value.contains("placeholder", ignoreCase = true) ||
        value.endsWith("/coverArt") ||
        value.contains("coverArt?id=&") ||
        (value.contains("coverArt?size=") && !value.contains("id="))
}
