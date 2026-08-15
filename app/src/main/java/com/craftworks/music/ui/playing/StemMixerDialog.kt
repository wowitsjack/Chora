package com.craftworks.music.ui.playing

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.session.MediaController
import com.craftworks.music.R
import com.craftworks.music.data.model.MediaCategory
import com.craftworks.music.ui.viewmodels.StemMixerUiState
import com.craftworks.music.ui.viewmodels.StemMixerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.roundToInt

private const val STEM_DRIFT_SETTLED_MS = 35L
private const val STEM_DRIFT_SEEK_LIMIT_MS = 750L
private const val STEM_DRIFT_CHECK_INTERVAL_MS = 250L
private const val STEM_CATCH_UP_SPEED = 1.04f
private const val STEM_SLOW_DOWN_SPEED = 0.96f
private val stemDisplayNames = mapOf(
    "vocals" to "Vocals",
    "drums" to "Drums",
    "bass" to "Bass",
    "other" to "Other",
    "instrumental" to "Instrumental"
)

private data class LocalStemLoadState(
    val urls: Map<String, String> = emptyMap(),
    val completed: Int = 0,
    val total: Int = 0,
    val error: String? = null
)

internal fun stemMixerSongId(metadata: MediaMetadata?): String? {
    if (metadata == null || metadata.mediaType == MediaMetadata.MEDIA_TYPE_RADIO_STATION) return null
    if (metadata.extras?.getString("mediaCategory") == MediaCategory.AUDIOBOOK) return null
    return metadata.extras?.getString("navidromeID")
        ?.takeIf { it.isNotBlank() && !it.startsWith("Local_") }
}

internal enum class StemSyncAction {
    NONE,
    RESET_SPEED,
    SPEED_UP,
    SLOW_DOWN,
    SEEK
}

internal fun stemSyncAction(
    leaderPositionMs: Long,
    stemPositionMs: Long,
    stemSpeed: Float
): StemSyncAction {
    val driftMs = leaderPositionMs - stemPositionMs
    return when {
        abs(driftMs) > STEM_DRIFT_SEEK_LIMIT_MS -> StemSyncAction.SEEK
        abs(driftMs) <= STEM_DRIFT_SETTLED_MS -> {
            if (stemSpeed == 1f) StemSyncAction.NONE else StemSyncAction.RESET_SPEED
        }
        driftMs > 0L -> StemSyncAction.SPEED_UP
        else -> StemSyncAction.SLOW_DOWN
    }
}

private fun MediaItem.matchesStemSong(songId: String): Boolean =
    stemMixerSongId(mediaMetadata) == songId

@Composable
fun StemMixerButton(
    color: Color,
    size: Dp,
    metadata: MediaMetadata?,
    onClick: () -> Unit
) {
    val enabled = stemMixerSongId(metadata) != null
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.size(size),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            contentColor = color.copy(alpha = 0.5f),
            disabledContentColor = color.copy(alpha = 0.25f)
        )
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.rounded_tune_24),
            contentDescription = "Open stem mixer",
            modifier = Modifier.size(size * 0.58f)
        )
    }
}

@Composable
fun StemMixerDialog(
    song: MediaItem,
    mediaController: MediaController?,
    classicStyle: Boolean = false,
    onDismiss: () -> Unit
) {
    val songId = stemMixerSongId(song.mediaMetadata)
    val viewModel: StemMixerViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val restoredSelection = remember(songId) { viewModel.selectionFor(songId.orEmpty()) }
    var selectedProfile by remember(songId) { mutableStateOf(restoredSelection.profile) }
    var selectedBitrate by remember(songId) { mutableStateOf(restoredSelection.bitrate) }

    LaunchedEffect(songId) {
        viewModel.check(songId.orEmpty(), selectedProfile, selectedBitrate)
    }

    val background = if (classicStyle) Color(0xFF111111) else MaterialTheme.colorScheme.surface
    val foreground = if (classicStyle) Color.White else MaterialTheme.colorScheme.onSurface
    val accent = if (classicStyle) Color(0xFF79B7E5) else MaterialTheme.colorScheme.primary

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = background,
            contentColor = foreground,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                StemMixerHeader(
                    title = song.mediaMetadata.title?.toString() ?: "Stem Mixer",
                    foreground = foreground,
                    classicStyle = classicStyle,
                    onDismiss = onDismiss
                )

                when {
                    state.isReady -> StemMixerReadyContent(
                        song = song,
                        state = state,
                        mediaController = mediaController,
                        foreground = foreground,
                        accent = accent
                    )
                    state.isFailure -> StemMixerFailureContent(
                        state = state,
                        foreground = foreground,
                        accent = accent,
                        onRetry = viewModel::retryCheck,
                        onConfigure = viewModel::showConfiguration
                    )
                    state.status == "idle" -> StemMixerConfigurationContent(
                        profile = selectedProfile,
                        bitrate = selectedBitrate,
                        foreground = foreground,
                        accent = accent,
                        onProfileChange = { selectedProfile = it },
                        onBitrateChange = { selectedBitrate = it },
                        onStart = {
                            viewModel.start(songId.orEmpty(), selectedProfile, selectedBitrate)
                        }
                    )
                    else -> StemMixerPreparingContent(
                        state = state,
                        foreground = foreground,
                        accent = accent
                    )
                }
            }
        }
    }
}

@Composable
private fun StemMixerHeader(
    title: String,
    foreground: Color,
    classicStyle: Boolean,
    onDismiss: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (classicStyle) {
                    Modifier.background(
                        Brush.verticalGradient(listOf(Color(0xFF8A8A8A), Color(0xFF3C3C3C)))
                    )
                } else {
                    Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                }
            )
            .padding(start = 18.dp, top = 8.dp, bottom = 8.dp, end = 6.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Stem Mixer",
                color = foreground,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = title,
                color = foreground,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Rounded.Close, contentDescription = "Close stem mixer", tint = foreground)
        }
    }
}

@Composable
private fun StemMixerPreparingContent(
    state: StemMixerUiState,
    foreground: Color,
    accent: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Text(
            text = preparingTitle(state.status),
            color = foreground,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = state.message.ifBlank {
                "The song keeps playing while ${if (state.profile == "2stems") "two" else "four"} MP3 stems are prepared."
            },
            color = foreground.copy(alpha = 0.72f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp, bottom = 28.dp)
        )
        LinearProgressIndicator(
            progress = { state.progress.coerceIn(0, 100) / 100f },
            color = accent,
            trackColor = foreground.copy(alpha = 0.15f),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .height(8.dp)
        )
        Text(
            text = "${state.progress.coerceIn(0, 100)}%",
            color = foreground.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

@Composable
private fun StemMixerFailureContent(
    state: StemMixerUiState,
    foreground: Color,
    accent: Color,
    onRetry: () -> Unit,
    onConfigure: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Text(
            text = if (state.status == "disabled") "Stem splitting is unavailable" else "Could not prepare stems",
            color = foreground,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = state.message.ifBlank {
                if (state.status == "disabled") {
                    "Enable the portable stem-splitter service on this Navidrome server."
                } else {
                    "The original song was left untouched."
                }
            },
            color = foreground.copy(alpha = 0.72f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp, bottom = 24.dp)
        )
        if (state.status != "disabled") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onConfigure) {
                    Text("Change setup")
                }
                Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Check Again")
                }
            }
        }
    }
}

@Composable
private fun StemMixerConfigurationContent(
    profile: String,
    bitrate: Int,
    foreground: Color,
    accent: Color,
    onProfileChange: (String) -> Unit,
    onBitrateChange: (Int) -> Unit,
    onStart: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 26.dp)
    ) {
        Text(
            text = "Split this song?",
            color = foreground,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "The server will run Demucs once and cache MP3 stems for this setup. The original audio file is never changed.",
            color = foreground.copy(alpha = 0.72f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(max = 560.dp)
                .padding(top = 8.dp, bottom = 24.dp)
        )

        Text("Channels", color = foreground, fontWeight = FontWeight.Bold)
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 8.dp, bottom = 22.dp)
        ) {
            StemChoiceButton(
                label = "2 · Vocals + Music",
                selected = profile == "2stems",
                accent = accent,
                onClick = { onProfileChange("2stems") }
            )
            StemChoiceButton(
                label = "4 · Full Mixer",
                selected = profile == "4stems",
                accent = accent,
                onClick = { onProfileChange("4stems") }
            )
        }

        Text("MP3 quality", color = foreground, fontWeight = FontWeight.Bold)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp, bottom = 26.dp)
        ) {
            listOf(192, 256, 320).forEach { option ->
                StemChoiceButton(
                    label = "$option kbps",
                    selected = bitrate == option,
                    accent = accent,
                    onClick = { onBitrateChange(option) }
                )
            }
        }

        Text(
            text = if (profile == "2stems") {
                "Faster: separate vocals from the complete instrumental."
            } else {
                "Full control: vocals, drums, bass, and everything else."
            },
            color = foreground.copy(alpha = 0.68f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 18.dp)
        )
        Button(
            onClick = onStart,
            colors = ButtonDefaults.buttonColors(containerColor = accent)
        ) {
            Text("Split into ${if (profile == "2stems") 2 else 4} MP3 stems")
        }
    }
}

@Composable
private fun StemChoiceButton(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    if (selected) {
        Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = accent)) {
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

@Composable
private fun StemMixerReadyContent(
    song: MediaItem,
    state: StemMixerUiState,
    mediaController: MediaController?,
    foreground: Color,
    accent: Color
) {
    val context = LocalContext.current
    val songId = state.songId
    val stemNames = state.stemUrls.keys.toList()
    val localStemLoad by produceState(
        initialValue = LocalStemLoadState(total = state.stemUrls.size),
        key1 = songId,
        key2 = state.stemUrls
    ) {
        val cachedUrls = linkedMapOf<String, String>()
        try {
            state.stemUrls.entries.forEachIndexed { index, (stem, sourceUrl) ->
                cachedUrls[stem] = cacheStemFile(context, songId, stem, sourceUrl).toString()
                value = LocalStemLoadState(
                    completed = index + 1,
                    total = state.stemUrls.size
                )
            }
            value = LocalStemLoadState(
                urls = cachedUrls,
                completed = state.stemUrls.size,
                total = state.stemUrls.size
            )
        } catch (error: Exception) {
            value = LocalStemLoadState(
                completed = cachedUrls.size,
                total = state.stemUrls.size,
                error = error.message ?: "Could not download the stems"
            )
        }
    }

    if (localStemLoad.urls.isEmpty()) {
        StemMixerLocalDownloadContent(
            state = localStemLoad,
            foreground = foreground,
            accent = accent
        )
        return
    }
    val localStemUrls = localStemLoad.urls
    val audioAttributes = remember {
        AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
    }
    val players = remember(songId, localStemUrls) {
        stemNames.associateWith { stem ->
            ExoPlayer.Builder(context).build().apply {
                setAudioAttributes(audioAttributes, false)
                setSeekParameters(SeekParameters.EXACT)
                setMediaItem(MediaItem.fromUri(localStemUrls.getValue(stem)))
                prepare()
            }
        }
    }
    val leader = players["other"] ?: players["instrumental"] ?: players.values.first()
    var handoffComplete by remember(players) { mutableStateOf(false) }
    var handoffError by remember(players) { mutableStateOf<String?>(null) }
    var positionMs by remember(players) { mutableLongStateOf(0L) }
    var durationMs by remember(players) { mutableLongStateOf(song.mediaMetadata.durationMs ?: 0L) }
    var mixerPlaying by remember(players) { mutableStateOf(false) }
    var lastDriftCheckAtMs by remember(players) { mutableLongStateOf(0L) }
    var volumes by remember(players) {
        mutableStateOf(stemNames.associateWith { 1f })
    }

    val latestHandoffComplete by rememberUpdatedState(handoffComplete)
    val latestPosition by rememberUpdatedState(positionMs)
    val latestPlaying by rememberUpdatedState(mixerPlaying)

    LaunchedEffect(players, mediaController, songId) {
        while (isActive && players.values.any { it.playbackState != Player.STATE_READY }) {
            val playbackError = players.values.firstNotNullOfOrNull { it.playerError }
            if (playbackError != null) {
                handoffError = playbackError.message ?: "A stem could not be streamed"
                return@LaunchedEffect
            }
            delay(100L)
        }

        val currentItem = mediaController?.currentMediaItem
        if (mediaController == null || currentItem?.matchesStemSong(songId) != true) {
            handoffError = "Playback moved to another song before the mixer was ready."
            return@LaunchedEffect
        }

        val handoffPosition = mediaController.currentPosition.coerceAtLeast(0L)
        val shouldPlay = mediaController.isPlaying
        players.values.forEach { it.seekTo(handoffPosition) }
        mediaController.pause()
        positionMs = handoffPosition
        durationMs = leader.duration.takeIf { it != C.TIME_UNSET && it > 0L }
            ?: durationMs
        handoffComplete = true
        mixerPlaying = shouldPlay
        if (shouldPlay) players.values.forEach(Player::play)
    }

    LaunchedEffect(players, handoffComplete) {
        while (isActive && handoffComplete) {
            if (mediaController?.currentMediaItem?.matchesStemSong(songId) != true) {
                players.values.forEach(Player::pause)
                mixerPlaying = false
                handoffComplete = false
                handoffError = "Playback moved to another song. The stem mix was stopped."
                return@LaunchedEffect
            }
            val playbackError = players.values.firstNotNullOfOrNull { it.playerError }
            if (playbackError != null) {
                players.values.forEach(Player::pause)
                mixerPlaying = false
                handoffError = playbackError.message ?: "A stem stream stopped unexpectedly"
                return@LaunchedEffect
            }
            positionMs = leader.currentPosition.coerceAtLeast(0L)
            durationMs = leader.duration.takeIf { it != C.TIME_UNSET && it > 0L }
                ?: durationMs
            mixerPlaying = players.values.any { it.isPlaying }

            val now = SystemClock.elapsedRealtime()
            if (mixerPlaying && now - lastDriftCheckAtMs >= STEM_DRIFT_CHECK_INTERVAL_MS) {
                players.values
                    .filter { it !== leader }
                    .forEach { stemPlayer ->
                        when (stemSyncAction(
                            leaderPositionMs = positionMs,
                            stemPositionMs = stemPlayer.currentPosition,
                            stemSpeed = stemPlayer.playbackParameters.speed
                        )) {
                            StemSyncAction.NONE -> Unit
                            StemSyncAction.RESET_SPEED -> stemPlayer.playbackParameters = PlaybackParameters.DEFAULT
                            StemSyncAction.SPEED_UP -> stemPlayer.playbackParameters = PlaybackParameters(STEM_CATCH_UP_SPEED)
                            StemSyncAction.SLOW_DOWN -> stemPlayer.playbackParameters = PlaybackParameters(STEM_SLOW_DOWN_SPEED)
                            StemSyncAction.SEEK -> {
                                stemPlayer.pause()
                                stemPlayer.playbackParameters = PlaybackParameters.DEFAULT
                                stemPlayer.seekTo(positionMs)
                                stemPlayer.play()
                            }
                        }
                    }
                lastDriftCheckAtMs = now
            }
            delay(100L)
        }
    }

    DisposableEffect(players, mediaController, songId) {
        onDispose {
            val restorePosition = leader.currentPosition
                .takeIf { latestHandoffComplete }
                ?: latestPosition
            val restorePlaying = players.values.any { it.isPlaying } || latestPlaying
            players.values.forEach {
                it.pause()
                it.release()
            }
            if (latestHandoffComplete && mediaController?.currentMediaItem?.matchesStemSong(songId) == true) {
                mediaController.seekTo(restorePosition.coerceAtLeast(0L))
                if (restorePlaying) mediaController.play() else mediaController.pause()
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        Text(
            text = if (handoffError == null) "${stemNames.size}-channel mix" else "Mixer unavailable",
            color = foreground,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = handoffError ?: if (handoffComplete) {
                "Adjust each part independently. Closing the mixer returns to normal playback at the same position."
            } else {
                "Buffering ${stemNames.size} MP3 channels…"
            },
            color = foreground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(max = 560.dp)
                .padding(top = 6.dp, bottom = 16.dp)
        )

        if (handoffError == null) {
            stemNames.forEach { stem ->
                StemChannelSlider(
                    label = stemDisplayNames[stem] ?: stem.replaceFirstChar(Char::uppercase),
                    value = volumes.getValue(stem),
                    enabled = handoffComplete,
                    foreground = foreground,
                    accent = accent,
                    onValueChange = { value ->
                        val bounded = value.coerceIn(0f, 1f)
                        volumes = volumes + (stem to bounded)
                        players.getValue(stem).volume = bounded
                    }
                )
            }

            Spacer(Modifier.height(8.dp))
            Slider(
                value = positionMs.toFloat().coerceIn(0f, durationMs.coerceAtLeast(1L).toFloat()),
                onValueChange = { requested ->
                    val target = requested.toLong().coerceIn(0L, durationMs.coerceAtLeast(1L))
                    positionMs = target
                    players.values.forEach { it.seekTo(target) }
                },
                enabled = handoffComplete,
                valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                modifier = Modifier.widthIn(max = 600.dp)
            )
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
            ) {
                Text(formatMixerTime(positionMs), color = foreground.copy(alpha = 0.72f))
                Text("-${formatMixerTime((durationMs - positionMs).coerceAtLeast(0L))}", color = foreground.copy(alpha = 0.72f))
            }
            IconButton(
                enabled = handoffComplete,
                onClick = {
                    if (mixerPlaying) {
                        players.values.forEach {
                            it.pause()
                            it.playbackParameters = PlaybackParameters.DEFAULT
                        }
                    } else {
                        val resumePosition = leader.currentPosition.coerceAtLeast(0L)
                        players.values.forEach {
                            it.playbackParameters = PlaybackParameters.DEFAULT
                            it.seekTo(resumePosition)
                        }
                        players.values.forEach(Player::play)
                    }
                    mixerPlaying = !mixerPlaying
                },
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    imageVector = if (mixerPlaying) {
                        ImageVector.vectorResource(R.drawable.media3_notification_pause)
                    } else {
                        Icons.Rounded.PlayArrow
                    },
                    contentDescription = if (mixerPlaying) "Pause stem mix" else "Play stem mix",
                    tint = if (handoffComplete) accent else foreground.copy(alpha = 0.25f),
                    modifier = Modifier.size(54.dp)
                )
            }
        }
    }
}

@Composable
private fun StemMixerLocalDownloadContent(
    state: LocalStemLoadState,
    foreground: Color,
    accent: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Text(
            text = if (state.error == null) "Downloading stems" else "Stem download failed",
            color = foreground,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        if (state.error == null) {
            LinearProgressIndicator(
                progress = { state.completed.toFloat() / state.total.coerceAtLeast(1).toFloat() },
                color = accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
            )
            Text(
                text = "Saving MP3 channels locally ${state.completed}/${state.total}",
                color = foreground.copy(alpha = 0.72f),
                modifier = Modifier.padding(top = 10.dp)
            )
        } else {
            Text(
                text = state.error,
                color = foreground.copy(alpha = 0.72f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StemChannelSlider(
    label: String,
    value: Float,
    enabled: Boolean,
    foreground: Color,
    accent: Color,
    onValueChange: (Float) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .padding(vertical = 3.dp)
    ) {
        Text(
            text = label,
            color = foreground,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.widthIn(min = 64.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${(value * 100f).roundToInt()}%",
            color = if (enabled) accent else foreground.copy(alpha = 0.35f),
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 42.dp)
        )
    }
}

private fun preparingTitle(status: String): String = when (status) {
    "checking" -> "Checking the stem cache"
    "queued" -> "Waiting to split"
    "uploading" -> "Sending the source track"
    "splitting" -> "Separating vocals, drums, bass, and other"
    "finalizing" -> "Preparing MP3 playback"
    else -> "Preparing stem mixer"
}

private fun formatMixerTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}
