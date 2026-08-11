package com.craftworks.music.ui.playing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaMetadata
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.craftworks.music.managers.settings.ArtworkSettingsManager
import com.craftworks.music.ui.elements.GeneratedAlbumArtStatic
import com.craftworks.music.ui.elements.artworkDataAtSize
import com.craftworks.music.ui.elements.artworkRenderKey
import com.craftworks.music.ui.elements.artworkRequestKey
import kotlinx.coroutines.delay

@Composable
internal fun NowPlayingArtwork(
    metadata: MediaMetadata?,
    modifier: Modifier = Modifier,
    targetSize: Int = 1024,
    contentDescription: String = "Album Cover Art",
    crossfade: Boolean = true,
    requestDelayMillis: Long = 0L
) {
    val context = LocalContext.current
    val settings = remember { ArtworkSettingsManager(context) }
    val generatedArtworkEnabled by settings.generatedArtworkEnabledFlow.collectAsStateWithLifecycle(true)
    val fallbackMode by settings.fallbackModeFlow.collectAsStateWithLifecycle(
        ArtworkSettingsManager.FallbackMode.PLACEHOLDER_DETECT
    )
    val artwork = metadata?.artworkUri
    val artworkValue = artwork?.toString()
    val identity = metadata?.extras?.getString("navidromeID")
        ?: metadata?.albumTitle?.toString()
        ?: metadata?.title?.toString()
        ?: "unknown"
    val artworkData = artworkDataAtSize(artwork, targetSize)
    val requestKey = artworkRequestKey("now_playing", identity, artworkData, targetSize)
    val renderKey = artworkRenderKey(identity, requestKey)
    val shouldGenerate = generatedArtworkEnabled && when {
        fallbackMode == ArtworkSettingsManager.FallbackMode.ALWAYS -> true
        artworkValue.isNullOrBlank() || artworkValue == "null" -> true
        fallbackMode == ArtworkSettingsManager.FallbackMode.PLACEHOLDER_DETECT ->
            artworkValue.contains("placeholder", ignoreCase = true) ||
                artworkValue.endsWith("/coverArt") ||
                artworkValue.contains("coverArt?id=&") ||
                (artworkValue.contains("coverArt?size=") && !artworkValue.contains("id="))
        else -> false
    }
    val fallback: @Composable () -> Unit = {
        if (generatedArtworkEnabled) {
            GeneratedAlbumArtStatic(
                title = metadata?.title?.toString() ?: "?",
                artist = metadata?.artist?.toString(),
                album = identity,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
            )
        }
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center
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

            if (shouldGenerate || !requestReady) {
                fallback()
            } else {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(artworkData)
                        .diskCacheKey(requestKey)
                        .memoryCacheKey(requestKey)
                        .crossfade(crossfade)
                        .build(),
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    loading = { fallback() },
                    error = { fallback() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
