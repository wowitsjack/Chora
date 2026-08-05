package com.craftworks.music.ui.playing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

@Composable
internal fun NowPlayingArtwork(
    metadata: MediaMetadata?,
    modifier: Modifier = Modifier,
    targetSize: Int = 1024,
    contentDescription: String = "Album Cover Art"
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
        if (shouldGenerate) {
            fallback()
        } else {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(artworkDataAtSize(artwork, targetSize))
                    .diskCacheKey("now_playing_${identity}_art_$targetSize")
                    .memoryCacheKey("now_playing_${identity}_art_$targetSize")
                    .crossfade(true)
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
