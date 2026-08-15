package com.craftworks.music.ui.ipod

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.craftworks.music.R
import com.craftworks.music.data.model.MediaCategory
import com.craftworks.music.player.SongHelper
import com.craftworks.music.player.stablePlaybackPosition
import com.craftworks.music.ui.elements.PLAYER_CARD_ARTWORK_DEBOUNCE_MS
import com.craftworks.music.ui.playing.stemMixerSongId
import kotlinx.coroutines.delay

internal data class IpodPlaybackState(
    val currentItem: MediaItem? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queue: List<MediaItem> = emptyList(),
    val currentIndex: Int = -1,
    val itemCount: Int = 0,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffleEnabled: Boolean = false,
    val volume: Float = 1f,
    val playbackSpeed: Float = 1f
)

internal data class IpodNowPlayingMediaGeometry(
    val artworkSizeDp: Float,
    val reflectionHeightDp: Float
)

internal fun calculateIpodNowPlayingMediaGeometry(
    containerWidthDp: Float,
    mediaRegionHeightDp: Float
): IpodNowPlayingMediaGeometry {
    val width = containerWidthDp.coerceAtLeast(0f)
    val height = mediaRegionHeightDp.coerceAtLeast(0f)
    if (width == 0f || height == 0f) {
        return IpodNowPlayingMediaGeometry(0f, 0f)
    }

    val referenceScale = (width / 320f).coerceIn(0.9f, 1f)
    val firstGenArtworkCap = 320f * referenceScale
    val artworkSize = minOf(width, height, firstGenArtworkCap)
    return IpodNowPlayingMediaGeometry(
        artworkSizeDp = artworkSize,
        reflectionHeightDp = ((height - artworkSize) / 2f).coerceAtLeast(0f)
    )
}

@Composable
internal fun rememberIpodPlaybackState(controller: MediaController?): IpodPlaybackState {
    val logicalQueue by SongHelper.currentTracklistFlow.collectAsStateWithLifecycle()
    var state by remember(controller) { mutableStateOf(controller.toIpodPlaybackState()) }
    DisposableEffect(controller) {
        if (controller == null) return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                val reported = controller.toIpodPlaybackState()
                state = reported.copy(
                    positionMs = stablePlaybackPosition(
                        previousPositionMs = state.positionMs,
                        reportedPositionMs = reported.positionMs,
                        isPlaying = reported.isPlaying,
                        durationMs = reported.durationMs
                    )
                )
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (
                    reason == Player.DISCONTINUITY_REASON_SEEK ||
                    reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT ||
                    reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION
                ) {
                    state = controller.toIpodPlaybackState().copy(
                        positionMs = newPosition.positionMs.coerceAtLeast(0L)
                    )
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                state = controller.toIpodPlaybackState()
            }
        }
        controller.addListener(listener)
        state = controller.toIpodPlaybackState()
        onDispose { controller.removeListener(listener) }
    }

    LaunchedEffect(controller, state.isPlaying, state.currentItem?.mediaId) {
        if (controller == null) return@LaunchedEffect
        do {
            val reported = controller.toIpodPlaybackState()
            state = reported.copy(
                positionMs = stablePlaybackPosition(
                    previousPositionMs = state.positionMs,
                    reportedPositionMs = reported.positionMs,
                    isPlaying = reported.isPlaying,
                    durationMs = reported.durationMs
                )
            )
            if (state.isPlaying) delay(500L)
        } while (state.isPlaying)
    }

    val queue = logicalQueue.ifEmpty { state.queue }
    val logicalCurrentIndex = state.currentItem?.mediaId?.let { mediaId ->
        queue.indexOfFirst { it.mediaId == mediaId }
    } ?: -1
    return state.copy(
        queue = queue,
        currentIndex = logicalCurrentIndex.takeIf { it >= 0 } ?: state.currentIndex,
        itemCount = queue.size
    )
}

private fun MediaController?.toIpodPlaybackState(): IpodPlaybackState {
    if (this == null) return IpodPlaybackState()
    val resolvedDuration = duration
        .takeIf { it != C.TIME_UNSET && it > 0L }
        ?: currentMediaItem?.mediaMetadata?.durationMs?.takeIf { it > 0L }
        ?: 0L
    return IpodPlaybackState(
        currentItem = currentMediaItem,
        isPlaying = isPlaying,
        positionMs = currentPosition.coerceAtLeast(0L),
        durationMs = resolvedDuration,
        queue = List(mediaItemCount) { index -> getMediaItemAt(index) },
        currentIndex = currentMediaItemIndex,
        itemCount = mediaItemCount,
        repeatMode = repeatMode,
        shuffleEnabled = shuffleModeEnabled,
        volume = volume.coerceIn(0f, 1f),
        playbackSpeed = playbackParameters.speed
    )
}

@Composable
internal fun IpodNowPlaying(
    state: IpodPlaybackState,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onShowQueue: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenStemMixer: () -> Unit
) {
    val item = state.currentItem
    val metadata = item?.mediaMetadata
    val isAudiobook = metadata?.extras?.getString("mediaCategory") == MediaCategory.AUDIOBOOK
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val referenceScale = (maxWidth.value / 320f).coerceIn(0.9f, 1f)
        val headerHeight = (60f * referenceScale).dp
        val footerHeight = (92f * referenceScale).dp

        Column(modifier = Modifier.fillMaxSize()) {
            IpodNowPlayingHeader(
                item = item,
                height = headerHeight,
                onBack = onBack,
                onShowQueue = onShowQueue
            )

            if (item == null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(28.dp)
                ) {
                    Text(
                        text = "Nothing Playing",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Choose a song from your library.",
                        color = Color.White.copy(alpha = 0.64f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                }
                return@Column
            }

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black)
            ) {
                val mediaGeometry = remember(maxWidth, maxHeight) {
                    calculateIpodNowPlayingMediaGeometry(
                        containerWidthDp = maxWidth.value,
                        mediaRegionHeightDp = maxHeight.value
                    )
                }
                val artworkSize = mediaGeometry.artworkSizeDp.dp
                val reflectionHeight = mediaGeometry.reflectionHeightDp.dp

                IpodArtworkReflection(
                    artwork = metadata?.artworkUri,
                    title = metadata?.title?.toString() ?: "Unknown Song",
                    artist = metadata?.artist?.toString(),
                    identity = metadata?.extras?.getString("navidromeID") ?: item.mediaId,
                    artworkWidth = artworkSize,
                    aboveArtwork = true,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(reflectionHeight)
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(artworkSize)
                        .background(Color.Black)
                ) {
                    IpodArtwork(
                        artwork = metadata?.artworkUri,
                        title = metadata?.title?.toString() ?: "Unknown Song",
                        artist = metadata?.artist?.toString(),
                        identity = metadata?.extras?.getString("navidromeID") ?: item.mediaId,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                IpodArtworkReflection(
                    artwork = metadata?.artworkUri,
                    title = metadata?.title?.toString() ?: "Unknown Song",
                    artist = metadata?.artist?.toString(),
                    identity = metadata?.extras?.getString("navidromeID") ?: item.mediaId,
                    artworkWidth = artworkSize,
                    aboveArtwork = false,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(reflectionHeight)
                )

                IpodTimingControls(
                    state = state,
                    onSeek = onSeek,
                    onCycleRepeat = onCycleRepeat,
                    onShowQueue = onShowQueue,
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    onToggleShuffle = onToggleShuffle,
                    isAudiobook = isAudiobook,
                    onSeekBack = onSeekBack,
                    onSeekForward = onSeekForward,
                    onSpeed = { showSpeedDialog = true },
                    onSleepTimer = { showSleepDialog = true },
                    stemMixerEnabled = stemMixerSongId(metadata) != null,
                    onOpenStemMixer = onOpenStemMixer,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }

            IpodNowPlayingFooter(
                state = state,
                height = footerHeight,
                isAudiobook = isAudiobook,
                onPrevious = onPrevious,
                onTogglePlay = onTogglePlay,
                onNext = onNext,
                onVolumeChange = onVolumeChange
            )
        }
    }

    if (showSpeedDialog) {
        IpodPlaybackSpeedDialog(
            currentSpeed = state.playbackSpeed,
            onDismiss = { showSpeedDialog = false },
            onSelected = { speed ->
                onSpeedChange(speed)
                showSpeedDialog = false
            }
        )
    }
    if (showSleepDialog) {
        IpodSleepTimerDialog(onDismiss = { showSleepDialog = false })
    }
}

@Composable
private fun IpodArtworkReflection(
    artwork: Any?,
    title: String,
    artist: String?,
    identity: String,
    artworkWidth: Dp,
    aboveArtwork: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clipToBounds()
            .background(Color.Black)
    ) {
        IpodArtwork(
            artwork = artwork,
            title = title,
            artist = artist,
            identity = identity,
            modifier = Modifier
                .align(if (aboveArtwork) Alignment.BottomCenter else Alignment.TopCenter)
                .width(artworkWidth)
                .aspectRatio(1f)
                .graphicsLayer {
                    scaleY = -1f
                    alpha = 0.28f
                }
                .clearAndSetSemantics { }
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = if (aboveArtwork) {
                            arrayOf(
                                0f to Color.Black,
                                0.46f to Color.Black,
                                0.76f to Color.Black.copy(alpha = 0.76f),
                                1f to Color.Black.copy(alpha = 0.34f)
                            )
                        } else {
                            arrayOf(
                                0f to Color.Black.copy(alpha = 0.34f),
                                0.24f to Color.Black.copy(alpha = 0.76f),
                                0.54f to Color.Black,
                                1f to Color.Black
                            )
                        }
                    )
                )
        )
    }
}

@Composable
internal fun IpodMiniPlayer(
    state: IpodPlaybackState,
    onOpen: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit
) {
    val item = state.currentItem ?: return
    val metadata = item.mediaMetadata
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(IpodDimensions.MiniPlayerHeight)
            .background(IpodSteelBlueGlass)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(start = 5.dp)
        ) {
            IpodArtwork(
                artwork = metadata.artworkUri,
                title = metadata.title?.toString() ?: "Unknown Song",
                artist = metadata.artist?.toString(),
                identity = metadata.extras?.getString("navidromeID") ?: item.mediaId,
                requestDelayMillis = PLAYER_CARD_ARTWORK_DEBOUNCE_MS,
                modifier = Modifier
                    .size(44.dp)
                    .border(1.dp, Color(0xFF616161))
            )
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 5.dp)
            ) {
                Text(
                    text = metadata.title?.toString() ?: "Unknown Song",
                    color = Color.White,
                    fontSize = 14.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = metadata.artist?.toString().orEmpty(),
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IpodTransportButton(
            type = if (state.isPlaying) IpodTransportType.PAUSE else IpodTransportType.PLAY,
            contentDescription = if (state.isPlaying) "Pause" else "Play",
            compact = true,
            onClick = onTogglePlay
        )
        IpodTransportButton(
            type = IpodTransportType.NEXT,
            contentDescription = "Next song",
            compact = true,
            onClick = onNext
        )
    }
}

@Composable
private fun IpodNowPlayingHeader(
    item: MediaItem?,
    height: Dp,
    onBack: () -> Unit,
    onShowQueue: () -> Unit
) {
    val metadata = item?.mediaMetadata
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(IpodGraphiteTabGlass)
            .border(1.dp, Color(0xFF303030))
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(54.dp)
                .fillMaxHeight()
                .clickable(role = Role.Button, onClick = onBack)
        ) {
            Canvas(
                modifier = Modifier
                    .size(27.dp)
                    .semantics { contentDescription = "Back to Music" }
            ) {
                val stroke = 3.dp.toPx()
                drawLine(Color.White, Offset(size.width * 0.68f, size.height * 0.16f), Offset(size.width * 0.30f, size.height * 0.50f), stroke)
                drawLine(Color.White, Offset(size.width * 0.30f, size.height * 0.50f), Offset(size.width * 0.68f, size.height * 0.84f), stroke)
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = metadata?.artist?.toString().orEmpty(),
                color = Color.White,
                fontSize = 13.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = metadata?.title?.toString() ?: "Now Playing",
                color = Color.White,
                fontSize = 13.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = metadata?.albumTitle?.toString().orEmpty(),
                color = Color.White,
                fontSize = 13.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(54.dp)
                .fillMaxHeight()
                .clickable(role = Role.Button, onClick = onShowQueue)
        ) {
            if (item != null) {
                IpodArtwork(
                    artwork = metadata?.artworkUri,
                    title = metadata?.title?.toString() ?: "Unknown Song",
                    artist = metadata?.artist?.toString(),
                    identity = metadata?.extras?.getString("navidromeID") ?: item.mediaId,
                    modifier = Modifier
                        .size(34.dp)
                        .border(1.dp, Color(0xFF777777))
                )
            }
        }
    }
}

@Composable
private fun IpodTimingControls(
    state: IpodPlaybackState,
    onSeek: (Long) -> Unit,
    onCycleRepeat: () -> Unit,
    onShowQueue: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleShuffle: () -> Unit,
    isAudiobook: Boolean,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSpeed: () -> Unit,
    onSleepTimer: () -> Unit,
    stemMixerEnabled: Boolean,
    onOpenStemMixer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(82.dp)
            .background(Color.Black.copy(alpha = 0.66f))
            .border(1.dp, Color.Black.copy(alpha = 0.86f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(41.dp)
                .padding(horizontal = 10.dp)
        ) {
            Text(
                text = formatIpodTime(state.positionMs),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
                maxLines = 1,
                modifier = Modifier.width(43.dp)
            )
            ClassicSlider(
                value = state.positionMs.toFloat(),
                valueRange = 0f..state.durationMs.coerceAtLeast(1L).toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "-${formatIpodTime((state.durationMs - state.positionMs).coerceAtLeast(0L))}",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.width(49.dp)
            )
        }
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(41.dp)
                .padding(horizontal = 15.dp)
        ) {
            if (isAudiobook) {
                IpodClassicTextButton("−15", "Back 15 seconds", onSeekBack)
                IpodClassicTextButton(
                    if (state.playbackSpeed.toInt().toFloat() == state.playbackSpeed) {
                        "${state.playbackSpeed.toInt()}×"
                    } else {
                        "${state.playbackSpeed}×"
                    },
                    "Playback speed",
                    onSpeed
                )
                IpodClassicTextButton("Sleep", "Sleep timer", onSleepTimer)
                IpodClassicTextButton("+30", "Forward 30 seconds", onSeekForward)
            } else {
                IpodClassicModeButton(
                    icon = ImageVector.vectorResource(
                        if (state.repeatMode == Player.REPEAT_MODE_ONE) {
                            R.drawable.rounded_repeat1_24
                        } else {
                            R.drawable.rounded_repeat_24
                        }
                    ),
                    contentDescription = when (state.repeatMode) {
                        Player.REPEAT_MODE_ONE -> "Repeat one"
                        Player.REPEAT_MODE_ALL -> "Repeat all"
                        else -> "Repeat off"
                    },
                    selected = state.repeatMode != Player.REPEAT_MODE_OFF,
                    onClick = onCycleRepeat
                )
                IpodClassicModeButton(
                    icon = ImageVector.vectorResource(R.drawable.rounded_queue_music_24),
                    contentDescription = "Show queue",
                    selected = false,
                    onClick = onShowQueue
                )
                IpodClassicModeButton(
                    icon = ImageVector.vectorResource(
                        if (isFavorite) R.drawable.round_star_24
                        else R.drawable.round_star_border_24
                    ),
                    contentDescription = if (isFavorite) {
                        "Remove from Favorites"
                    } else {
                        "Add to Favorites"
                    },
                    selected = isFavorite,
                    onClick = onToggleFavorite
                )
                IpodClassicModeButton(
                    icon = ImageVector.vectorResource(R.drawable.rounded_tune_24),
                    contentDescription = "Open stem mixer",
                    selected = false,
                    enabled = stemMixerEnabled,
                    onClick = onOpenStemMixer
                )
                IpodClassicModeButton(
                    icon = ImageVector.vectorResource(R.drawable.rounded_shuffle_24),
                    contentDescription = if (state.shuffleEnabled) "Shuffle on" else "Shuffle off",
                    selected = state.shuffleEnabled,
                    onClick = onToggleShuffle
                )
            }
        }
    }
}

@Composable
private fun IpodNowPlayingFooter(
    state: IpodPlaybackState,
    height: Dp,
    isAudiobook: Boolean,
    onPrevious: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onVolumeChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(IpodGraphiteTabGlass)
            .border(1.dp, Color.Black)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            IpodTransportButton(
                type = IpodTransportType.PREVIOUS,
                contentDescription = if (isAudiobook) "Previous chapter" else "Previous song",
                onClick = onPrevious
            )
            IpodTransportButton(
                type = if (state.isPlaying) IpodTransportType.PAUSE else IpodTransportType.PLAY,
                contentDescription = if (state.isPlaying) "Pause" else "Play",
                prominent = true,
                onClick = onTogglePlay
            )
            IpodTransportButton(
                type = IpodTransportType.NEXT,
                contentDescription = if (isAudiobook) "Next chapter" else "Next song",
                onClick = onNext
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(43.dp)
                .padding(horizontal = 25.dp)
        ) {
            Text(
                text = "−",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            ClassicSlider(
                value = state.volume,
                valueRange = 0f..1f,
                onValueChange = onVolumeChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 7.dp)
            )
            Text(
                text = "+",
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun IpodClassicTextButton(
    label: String,
    contentDescription: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(38.dp)
            .width(58.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (pressed) Color.White.copy(alpha = 0.11f) else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .semantics { this.contentDescription = contentDescription }
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.86f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

private enum class IpodTransportType { PREVIOUS, PLAY, PAUSE, NEXT }

@Composable
private fun IpodTransportButton(
    type: IpodTransportType,
    contentDescription: String,
    prominent: Boolean = false,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val buttonSize = when {
        compact -> 44.dp
        prominent -> 60.dp
        else -> 54.dp
    }
    val symbolSize = when {
        compact -> 23.dp
        prominent -> 34.dp
        else -> 30.dp
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(buttonSize)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .semantics { this.contentDescription = contentDescription }
    ) {
        Canvas(modifier = Modifier.size(symbolSize)) {
            val symbolBrush = Brush.verticalGradient(
                if (pressed) {
                    listOf(Color(0xFFB8B8B8), Color(0xFF777777))
                } else {
                    listOf(Color.White, Color(0xFFC7C7C7))
                }
            )
            fun triangle(left: Float, right: Float, pointsLeft: Boolean = false) {
                val path = Path().apply {
                    if (pointsLeft) {
                        moveTo(right, size.height * 0.08f)
                        lineTo(left, size.height * 0.50f)
                        lineTo(right, size.height * 0.92f)
                    } else {
                        moveTo(left, size.height * 0.08f)
                        lineTo(right, size.height * 0.50f)
                        lineTo(left, size.height * 0.92f)
                    }
                    close()
                }
                drawPath(path, symbolBrush)
            }

            when (type) {
                IpodTransportType.PLAY -> triangle(size.width * 0.22f, size.width * 0.88f)
                IpodTransportType.PAUSE -> {
                    drawRoundRect(
                        brush = symbolBrush,
                        topLeft = Offset(size.width * 0.20f, size.height * 0.10f),
                        size = Size(size.width * 0.22f, size.height * 0.80f),
                        cornerRadius = CornerRadius(1.dp.toPx())
                    )
                    drawRoundRect(
                        brush = symbolBrush,
                        topLeft = Offset(size.width * 0.58f, size.height * 0.10f),
                        size = Size(size.width * 0.22f, size.height * 0.80f),
                        cornerRadius = CornerRadius(1.dp.toPx())
                    )
                }
                IpodTransportType.PREVIOUS -> {
                    drawRect(
                        brush = symbolBrush,
                        topLeft = Offset(size.width * 0.05f, size.height * 0.10f),
                        size = Size(size.width * 0.11f, size.height * 0.80f)
                    )
                    triangle(size.width * 0.17f, size.width * 0.56f, pointsLeft = true)
                    triangle(size.width * 0.50f, size.width * 0.91f, pointsLeft = true)
                }
                IpodTransportType.NEXT -> {
                    drawRect(
                        brush = symbolBrush,
                        topLeft = Offset(size.width * 0.84f, size.height * 0.10f),
                        size = Size(size.width * 0.11f, size.height * 0.80f)
                    )
                    triangle(size.width * 0.09f, size.width * 0.50f)
                    triangle(size.width * 0.44f, size.width * 0.83f)
                }
            }
        }
    }
}

@Composable
private fun IpodClassicModeButton(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 54.dp, height = 38.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (pressed) Color.White.copy(alpha = 0.11f) else Color.Transparent)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = when {
                !enabled -> Color.White.copy(alpha = 0.28f)
                selected -> Color(0xFF55BFF2)
                else -> Color.White.copy(alpha = 0.82f)
            },
            modifier = Modifier.size(23.dp)
        )
    }
}

@Composable
internal fun ClassicSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val range = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val normalized = ((value - valueRange.start) / range).coerceIn(0f, 1f)
    Canvas(
        modifier = modifier
            .height(25.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange)
                setProgress { target ->
                    onValueChange(target.coerceIn(valueRange.start, valueRange.endInclusive))
                    true
                }
            }
            .pointerInput(valueRange) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    currentOnValueChange(
                        sliderValueForPosition(
                            x = down.position.x,
                            width = size.width.toFloat(),
                            valueRange = valueRange
                        )
                    )
                    down.consume()

                    do {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                            ?: break
                        if (change.pressed) {
                            currentOnValueChange(
                                sliderValueForPosition(
                                    x = change.position.x,
                                    width = size.width.toFloat(),
                                    valueRange = valueRange
                                )
                            )
                            change.consume()
                        }
                    } while (change.pressed)
                }
            }
    ) {
        val trackHeight = 8.dp.toPx()
        val y = size.height / 2f
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(Color(0xFFDADADA), Color(0xFFA6A6A6), Color.White)
            ),
            topLeft = Offset(0f, y - trackHeight / 2f),
            size = Size(size.width, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2f)
        )
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(Color(0xFFCDDDF1), Color(0xFF7DAEF5), Color(0xFF3297EC))
            ),
            topLeft = Offset(0f, y - trackHeight / 2f),
            size = Size(size.width * normalized, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2f)
        )
        drawCircle(
            brush = Brush.verticalGradient(listOf(Color.White, Color(0xFFB7B7B7))),
            radius = 7.5.dp.toPx(),
            center = Offset(size.width * normalized, y)
        )
        drawCircle(
            color = Color.Black.copy(alpha = 0.44f),
            radius = 7.5.dp.toPx(),
            center = Offset(size.width * normalized, y),
            style = Stroke(width = 1.dp.toPx())
        )
    }
}

internal fun sliderValueForPosition(
    x: Float,
    width: Float,
    valueRange: ClosedFloatingPointRange<Float>
): Float {
    if (width <= 0f) return valueRange.start
    val range = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: return valueRange.start
    return valueRange.start + (x / width).coerceIn(0f, 1f) * range
}

internal fun formatIpodTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
