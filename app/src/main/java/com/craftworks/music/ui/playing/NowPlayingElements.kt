@file:androidx.annotation.OptIn(UnstableApi::class)

package com.craftworks.music.ui.playing

import android.util.Log
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.ui.compose.state.rememberNextButtonState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberPreviousButtonState
import androidx.media3.ui.compose.state.rememberRepeatButtonState
import androidx.media3.ui.compose.state.rememberShuffleButtonState
import com.craftworks.music.R
import com.craftworks.music.data.repository.LyricsState
import com.craftworks.music.formatMilliseconds
import com.craftworks.music.managers.SleepTimerManager
import com.craftworks.music.player.AudiobookPlaybackHelper
import com.craftworks.music.player.ChoraMediaLibraryService
import com.craftworks.music.player.stablePlaybackPosition
import com.craftworks.music.providers.navidrome.downloadNavidromeSong
import com.craftworks.music.data.model.isFavorite
import com.craftworks.music.ui.viewmodels.SongActionsViewModel
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.craftworks.music.ui.elements.bounceClick
import com.craftworks.music.ui.elements.dialogs.SleepTimerDialog
import com.craftworks.music.ui.elements.moveClick
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun NowPlayingLibraryActions(
    color: Color,
    isFavorite: Boolean,
    downloadQueued: Boolean,
    actionsEnabled: Boolean,
    downloadEnabled: Boolean,
    onDownload: () -> Unit,
    onToggleFavorite: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NowPlayingLibraryActionButton(
            label = if (downloadQueued) "Queued" else "Download",
            icon = ImageVector.vectorResource(R.drawable.rounded_download_24),
            contentDescription = if (downloadQueued) "Download queued" else "Download song",
            color = color,
            selected = downloadQueued,
            enabled = downloadEnabled,
            weight = 1f,
            onClick = onDownload
        )
        NowPlayingLibraryActionButton(
            label = "Favorite",
            icon = ImageVector.vectorResource(
                if (isFavorite) R.drawable.round_star_24 else R.drawable.round_star_border_24
            ),
            contentDescription = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
            color = color,
            selected = isFavorite,
            enabled = actionsEnabled,
            weight = 1.15f,
            onClick = onToggleFavorite
        )
        NowPlayingLibraryActionButton(
            label = "Hide",
            icon = ImageVector.vectorResource(R.drawable.round_visibility_off_24),
            contentDescription = "Hide song from library",
            color = color,
            selected = false,
            enabled = actionsEnabled,
            weight = 0.85f,
            onClick = onHide
        )
    }
}

@Composable
private fun RowScope.NowPlayingLibraryActionButton(
    label: String,
    icon: ImageVector,
    contentDescription: String,
    color: Color,
    selected: Boolean,
    enabled: Boolean,
    weight: Float,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .weight(weight)
            .height(44.dp),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color.copy(alpha = if (selected) 0.24f else 0.12f),
            contentColor = color,
            disabledContainerColor = color.copy(alpha = 0.06f),
            disabledContentColor = color.copy(alpha = 0.30f)
        )
    ) {
        Icon(icon, contentDescription, modifier = Modifier.size(19.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun PlaybackProgressSlider(
    color: Color = MaterialTheme.colorScheme.onBackground,
    mediaController: MediaController? = null,
    metadata: MediaMetadata? = null
) {
    var currentValue by remember { mutableLongStateOf(0L) }
    var currentDuration by remember(mediaController, metadata?.durationMs) {
        mutableLongStateOf(
            mediaController?.duration
                ?.takeIf { it != C.TIME_UNSET && it > 0L }
                ?: metadata?.durationMs?.takeIf { it > 0L }
                ?: 0L
        )
    }

    val animatedValue by animateFloatAsState(
        targetValue = currentValue.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "Smooth Slider Update"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val focused = remember { mutableStateOf(false) }
    var isInteracting by remember { mutableStateOf(false) }

    var isPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(mediaController, isPlaying, isInteracting) {
        if (mediaController != null && !isInteracting) {
            do {
                currentDuration = mediaController.duration
                    .takeIf { it != C.TIME_UNSET && it > 0L }
                    ?: metadata?.durationMs?.takeIf { it > 0L }
                    ?: 0L
                currentValue = stablePlaybackPosition(
                    previousPositionMs = currentValue,
                    reportedPositionMs = mediaController.currentPosition,
                    isPlaying = mediaController.isPlaying,
                    durationMs = currentDuration
                )
                if (mediaController.isPlaying) delay(100L)
            } while (mediaController.isPlaying && !isInteracting)
        }
    }

    DisposableEffect(mediaController) {
        if (mediaController == null) {
            return@DisposableEffect onDispose { }
        }

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                Log.d("TAG", "MediaController isPlaying changed: $playing")
                isPlaying = playing
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                super.onPositionDiscontinuity(oldPosition, newPosition, reason)
                if (
                    reason == Player.DISCONTINUITY_REASON_SEEK ||
                    reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT ||
                    reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION
                ) {
                    currentValue = stablePlaybackPosition(
                        previousPositionMs = currentValue,
                        reportedPositionMs = newPosition.positionMs,
                        isPlaying = mediaController.isPlaying,
                        durationMs = currentDuration,
                        allowDiscontinuity = true
                    )
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentValue = mediaController.currentPosition.coerceAtLeast(0L)
                currentDuration = mediaController.duration
                    .takeIf { it != C.TIME_UNSET && it > 0L }
                    ?: mediaItem?.mediaMetadata?.durationMs?.takeIf { it > 0L }
                    ?: 0L
            }
        }

        mediaController.addListener(listener)

        // Initial check in case state changed before listener was attached or for initial setup
        isPlaying = mediaController.isPlaying
        currentValue = mediaController.currentPosition.coerceAtLeast(0L)

        onDispose {
            mediaController.removeListener(listener)
        }
    }

    Column(
        Modifier.focusable(false)
    ) {
        Slider(
            enabled = metadata?.mediaType != MediaMetadata.MEDIA_TYPE_RADIO_STATION,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .onFocusChanged {
                    focused.value = it.isFocused
                }
                .onKeyEvent { keyEvent ->
                    val duration = currentDuration
                    when {
                        keyEvent.key == Key.DirectionRight && keyEvent.type == KeyEventType.KeyDown -> {
                            currentValue = if (duration > 0L) {
                                (currentValue + 5000).coerceIn(0L, duration)
                            } else {
                                currentValue + 5000
                            }
                            mediaController?.seekTo(currentValue)
                            true
                        }

                        keyEvent.key == Key.DirectionLeft && keyEvent.type == KeyEventType.KeyDown -> {
                            currentValue = (currentValue - 5000).coerceAtLeast(0L)
                            mediaController?.seekTo(currentValue)
                            true
                        }

                        else -> false
                    }
                },
            value = animatedValue,
            onValueChange = {
                isInteracting = true
                currentValue = it.toLong()
            },
            onValueChangeFinished = {
                isInteracting = false
                mediaController?.seekTo(currentValue)
            },
            valueRange = 0f..currentDuration.coerceAtLeast(1L).toFloat(),
            colors = SliderDefaults.colors(
                activeTrackColor = color,
                inactiveTrackColor = color.copy(alpha = 0.25f),
                thumbColor = color
            ),
            interactionSource = interactionSource,
        )

        // Time thingies
        Box(
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = remember(currentValue) { formatMilliseconds(currentValue.toInt() / 1000) },
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Start,
                color = color.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(64.dp),
                maxLines = 1
            )
            Text(
                text = remember(currentDuration, currentValue) {
                    if (currentDuration > 0) formatMilliseconds((currentDuration / 1000).toInt())
                    else formatMilliseconds((currentValue / 1000).toInt())
                },
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.End,
                color = color.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(64.dp),
                maxLines = 1
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun PreviousSongButton(player: Player, color: Color, modifier: Modifier = Modifier) {
    val state = rememberPreviousButtonState(player)
    IconButton(onClick = state::onClick, modifier = modifier
        .bounceClick(state.isEnabled)
        .moveClick(false, state.isEnabled), enabled = state.isEnabled) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.media3_notification_seek_to_previous),
            contentDescription = "Previous song",
            modifier = modifier,
            tint = if (state.isEnabled) color else color.copy(0.5f)
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun PlayPauseButton(player: Player, color: Color, modifier: Modifier = Modifier) {
    val state = rememberPlayPauseButtonState(player)
    val icon = if (state.showPlay) Icons.Rounded.PlayArrow else ImageVector.vectorResource(R.drawable.media3_notification_pause)
    val contentDescription =
        if (state.showPlay) "play"
        else "pause"

    IconButton(onClick = state::onClick, modifier = modifier.bounceClick(state.isEnabled), enabled = state.isEnabled) {
        Icon(icon, contentDescription = contentDescription, modifier = modifier, tint = if (state.isEnabled) color else color.copy(0.5f))
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun NextSongButton(player: Player, color: Color, modifier: Modifier = Modifier) {
    val state = rememberNextButtonState(player)
    IconButton(onClick = state::onClick, modifier = modifier
        .bounceClick(state.isEnabled)
        .moveClick(true, state.isEnabled), enabled = state.isEnabled) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.media3_notification_seek_to_next),
            contentDescription = "Next song",
            modifier = modifier,
            tint = if (state.isEnabled) color else color.copy(0.5f)
        )
    }
}

@Composable
internal fun AudiobookTransportControls(
    controller: MediaController,
    color: Color,
    playButtonSize: Dp = 92.dp,
    chapterButtonSize: Dp = 48.dp,
    seekButtonSize: Dp = 42.dp
) {
    TimedSeekButton(
        label = "−15",
        contentDescription = "Back 15 seconds",
        color = color,
        modifier = Modifier.size(seekButtonSize),
        onClick = { AudiobookPlaybackHelper.seekBack(controller) }
    )
    IconButton(
        onClick = { AudiobookPlaybackHelper.previousChapter(controller) },
        modifier = Modifier.size(chapterButtonSize)
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.media3_notification_seek_to_previous),
            contentDescription = "Previous chapter",
            tint = color,
            modifier = Modifier.size(chapterButtonSize)
        )
    }
    PlayPauseButton(controller, color, Modifier.size(playButtonSize))
    IconButton(
        onClick = { AudiobookPlaybackHelper.nextChapter(controller) },
        modifier = Modifier.size(chapterButtonSize)
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.media3_notification_seek_to_next),
            contentDescription = "Next chapter",
            tint = color,
            modifier = Modifier.size(chapterButtonSize)
        )
    }
    TimedSeekButton(
        label = "+30",
        contentDescription = "Forward 30 seconds",
        color = color,
        modifier = Modifier.size(seekButtonSize),
        onClick = { AudiobookPlaybackHelper.seekForward(controller) }
    )
}

@Composable
private fun TimedSeekButton(
    label: String,
    contentDescription: String,
    color: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
internal fun AudiobookSecondaryControls(
    controller: MediaController,
    color: Color,
    metadata: MediaMetadata?,
    buttonSize: Dp = 48.dp
) {
    val speeds = remember { listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f) }
    var currentSpeed by remember(controller) { mutableStateOf(controller.playbackParameters.speed) }
    var speedMenuOpen by remember { mutableStateOf(false) }
    var sleepDialogOpen by remember { mutableStateOf(false) }

    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                currentSpeed = playbackParameters.speed
            }
        }
        controller.addListener(listener)
        currentSpeed = controller.playbackParameters.speed
        onDispose { controller.removeListener(listener) }
    }

    Box {
        Button(
            onClick = { speedMenuOpen = true },
            modifier = Modifier.size(buttonSize + 6.dp),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(2.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = color.copy(alpha = 0.72f)
            )
        ) {
            Text(
                text = formatPlaybackSpeed(currentSpeed),
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
        DropdownMenu(
            expanded = speedMenuOpen,
            onDismissRequest = { speedMenuOpen = false }
        ) {
            speeds.forEach { speed ->
                DropdownMenuItem(
                    text = { Text(formatPlaybackSpeed(speed)) },
                    onClick = {
                        controller.setPlaybackSpeed(speed)
                        speedMenuOpen = false
                    }
                )
            }
        }
    }

    Button(
        onClick = { sleepDialogOpen = true },
        modifier = Modifier.height(buttonSize + 6.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = color.copy(alpha = 0.72f)
        )
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.s_p_sleep_timer),
            contentDescription = "Sleep timer",
            modifier = Modifier.size(buttonSize)
        )
    }

    DownloadButton(
        color = color,
        size = buttonSize,
        metadata = metadata,
        enabled = metadata?.extras?.getString("navidromeID")?.startsWith("Local_") == false
    )
    PlayQueueButton(color, buttonSize)

    if (sleepDialogOpen) {
        SleepTimerDialog(
            setShowDialog = { sleepDialogOpen = it },
            onDurationSelected = SleepTimerManager::startTimer
        )
    }
}

private fun formatPlaybackSpeed(speed: Float): String =
    if (speed.toInt().toFloat() == speed) "${speed.toInt()}×" else "${speed}×"

@Composable
@Preview
fun LyricsButton(
    color: Color = Color.Black,
    size: Dp = 64.dp,
){
    val lyrics by LyricsState.lyrics.collectAsStateWithLifecycle()
    val isLoading by LyricsState.isLoading.collectAsStateWithLifecycle()

    Button(
        onClick = {
            if (lyricsOpen) {
                lyricsOpen = false
            } else {
                lyricsOpen = true
                ChoraMediaLibraryService.getInstance()?.requestLyricsForCurrentItem()
            }
        },
        shape = RoundedCornerShape(12.dp),
        modifier = if (lyrics.isNotEmpty() || isLoading) {
            Modifier.size(size).bounceClick()
        } else {
            Modifier.size(size)
        },
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            contentColor = color.copy(0.5f),
            disabledContentColor = color.copy(0.25f)
        ),
        enabled = true
    ) {
        Crossfade(targetState = isLoading to lyricsOpen, label = "Lyrics Icon Crossfade") { (loading, open) ->
            when {
                loading -> CircularProgressIndicator(
                    modifier = Modifier.size(size * 0.55f),
                    color = color,
                    strokeWidth = 2.dp
                )
                open -> Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.lyrics_active),
                    contentDescription = "Close Lyrics",
                    modifier = Modifier
                        .size(size * 0.58f)
                )

                else -> Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.lyrics_inactive),
                    contentDescription = "View Lyrics",
                    modifier = Modifier
                        .size(size * 0.58f)
                )
            }
        }
    }
}

@Composable
fun LyricsCloseButton(
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
    ) {
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "Close lyrics",
            tint = color,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun PlayQueueButton(
    color: Color = Color.Black,
    size: Dp = 64.dp
){
    Button(
        onClick = { playQueueOpen = true },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.size(size),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            contentColor = color.copy(0.5f),
            disabledContentColor = color.copy(0.25f)
        )
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.s_m_playback),
            contentDescription = stringResource(R.string.Queue_Title),
            modifier = Modifier
                .size(size * 0.58f)
        )
    }
}


@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun DownloadButton(color: Color, size: Dp, metadata: MediaMetadata?, enabled: Boolean) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    Button(
        onClick = {
            coroutineScope.launch {
                try {
                    metadata?.let {
                        downloadNavidromeSong(context, it)
                    }
                } catch (e: Exception) {
                    Log.e("DownloadButton", "Failed to download song", e)
                }
            }
        },
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        modifier = if (enabled) {
            Modifier.size(size).bounceClick()
        } else {
            Modifier.size(size)
        },
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            contentColor = color.copy(alpha = 0.5f),
            disabledContentColor = color.copy(alpha = 0.25f)
        )
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.rounded_download_24),
            contentDescription = "Download Song",
            modifier = Modifier
                .size(size * 0.58f)
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun FavoriteButton(color: Color, size: Dp, metadata: MediaMetadata?, enabled: Boolean) {
    val coroutineScope = rememberCoroutineScope()
    val songActionsViewModel: SongActionsViewModel = hiltViewModel()
    // Use rememberSaveable to persist starred state across configuration changes
    // This prevents flickering when folding/unfolding the device
    var isStarred by rememberSaveable { mutableStateOf(false) }

    // Sync with metadata when song changes (using navidromeID as key to detect song change)
    val songId = metadata?.extras?.getString("navidromeID")
    LaunchedEffect(songId) {
        isStarred = metadata?.isFavorite() ?: false
    }

    Button(
        onClick = {
            coroutineScope.launch {
                metadata?.extras?.getString("navidromeID")?.let { songId ->
                    val previousState = isStarred
                    isStarred = !isStarred
                    val item = MediaItem.Builder()
                        .setMediaId(songId)
                        .setMediaMetadata(metadata)
                        .build()
                    if (!songActionsViewModel.setFavorite(item, isStarred)) {
                        isStarred = previousState
                        Log.e("FavoriteButton", "Failed to update star status")
                    }
                }
            }
        },
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        modifier = if (enabled) {
            Modifier.size(size).bounceClick()
        } else {
            Modifier.size(size)
        },
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            contentColor = color.copy(alpha = if (isStarred) 1f else 0.5f),
            disabledContentColor = color.copy(alpha = 0.25f)
        )
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(
                if (isStarred) R.drawable.round_star_24
                else R.drawable.round_star_border_24
            ),
            contentDescription = if (isStarred) "Remove from Favorites" else "Add to Favorites",
            modifier = Modifier
                .size(size * 0.58f),
            tint = color.copy(alpha = if (isStarred) 1f else 0.5f)
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun ShuffleButton(player: Player, color: Color, modifier: Modifier = Modifier) {
    val state = rememberShuffleButtonState(player)
    IconButton(onClick = state::onClick, modifier = modifier.bounceClick(), enabled = state.isEnabled) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.round_shuffle_28),
            contentDescription = "Shuffle",
            modifier = modifier,
            tint = color.copy(if (state.shuffleOn) 1f else 0.5f)
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun RepeatButton(player: Player, color: Color, modifier: Modifier = Modifier) {
    val state = rememberRepeatButtonState(player)
    val icon = repeatModeIcon(state.repeatModeState)
    IconButton(onClick = state::onClick, modifier = modifier.bounceClick(), enabled = state.isEnabled) {
        Icon(
            imageVector = icon,
            contentDescription = "Repeat",
            modifier = modifier,
            tint = color.copy(if (state.repeatModeState == Player.REPEAT_MODE_OFF) 0.5f else 1f)
        )
    }
}
@Composable
private fun repeatModeIcon(repeatMode: @Player.RepeatMode Int): ImageVector {
    return when (repeatMode) {
        Player.REPEAT_MODE_OFF -> ImageVector.vectorResource(R.drawable.rounded_repeat_24)
        Player.REPEAT_MODE_ONE -> ImageVector.vectorResource(R.drawable.rounded_repeat1_24)
        else -> ImageVector.vectorResource(R.drawable.rounded_repeat_24)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumeSlider(
    color: Color,
    mediaController: MediaController?,
    modifier: Modifier = Modifier
) {
    var volume by remember { mutableStateOf(mediaController?.volume ?: 1f) }
    var isInteracting by remember { mutableStateOf(false) }

    // Listen for volume changes from external sources
    DisposableEffect(mediaController) {
        if (mediaController == null) {
            return@DisposableEffect onDispose { }
        }

        val listener = object : Player.Listener {
            override fun onDeviceVolumeChanged(deviceVolume: Int, muted: Boolean) {
                // Device volume changed (hardware buttons) - update slider if not interacting
                if (!isInteracting) {
                    volume = mediaController.volume
                }
            }

            override fun onVolumeChanged(newVolume: Float) {
                // Player volume changed programmatically - update slider if not interacting
                if (!isInteracting) {
                    volume = newVolume
                }
            }
        }

        // Set initial volume
        volume = mediaController.volume

        mediaController.addListener(listener)
        onDispose {
            mediaController.removeListener(listener)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(
                when {
                    volume == 0f -> R.drawable.round_volume_off_24
                    volume < 0.5f -> R.drawable.round_volume_down_24
                    else -> R.drawable.round_volume_up_24
                }
            ),
            contentDescription = "Volume",
            tint = color.copy(alpha = 0.5f),
            modifier = Modifier.size(24.dp)
        )

        Slider(
            value = volume,
            onValueChange = {
                isInteracting = true
                volume = it
                mediaController?.volume = it
            },
            onValueChangeFinished = {
                isInteracting = false
            },
            valueRange = 0f..1f,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            colors = SliderDefaults.colors(
                activeTrackColor = color.copy(alpha = 0.5f),
                inactiveTrackColor = color.copy(alpha = 0.2f),
                thumbColor = color.copy(alpha = 0.5f)
            )
        )
    }
}
