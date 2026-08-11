package com.craftworks.music.ui.ipod

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import com.craftworks.music.managers.SleepTimerManager

private val ipodAudiobookSpeeds = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)

@Composable
internal fun IpodPlaybackSpeedDialog(
    currentSpeed: Float,
    onDismiss: () -> Unit,
    onSelected: (Float) -> Unit
) {
    IpodBottomDialog(onDismiss = onDismiss) {
        Text(
            text = "Playback Speed",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
        ipodAudiobookSpeeds.forEach { speed ->
            IpodActionSheetButton(
                label = if (speed.toInt().toFloat() == speed) "${speed.toInt()}×" else "${speed}×",
                emphasized = speed == currentSpeed,
                onClick = { onSelected(speed) }
            )
        }
        IpodActionSheetButton("Cancel", emphasized = true, onClick = onDismiss)
    }
}

@Composable
internal fun IpodSleepTimerDialog(onDismiss: () -> Unit) {
    IpodBottomDialog(onDismiss = onDismiss) {
        Text(
            text = "Sleep Timer",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
        SleepTimerManager.TimerDuration.entries.forEach { duration ->
            IpodActionSheetButton(
                label = duration.displayName,
                onClick = {
                    SleepTimerManager.startTimer(duration)
                    onDismiss()
                }
            )
        }
        IpodActionSheetButton("Cancel", emphasized = true, onClick = onDismiss)
    }
}

@Composable
internal fun IpodSongActionsDialog(
    song: MediaItem,
    isFavorite: Boolean,
    favoriteEnabled: Boolean,
    radioEnabled: Boolean,
    downloadEnabled: Boolean,
    stemMixerEnabled: Boolean,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onAddToQueueTop: () -> Unit,
    onAddToQueueBottom: () -> Unit,
    onStartRadio: () -> Unit,
    onOpenStemMixer: () -> Unit,
    onDownload: () -> Unit
) {
    IpodBottomDialog(onDismiss = onDismiss) {
        Text(
            text = song.mediaMetadata.title?.toString() ?: "Unknown Song",
            color = Color.White,
            fontSize = 16.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
        IpodActionSheetButton(
            label = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
            enabled = favoriteEnabled,
            onClick = onToggleFavorite
        )
        IpodActionSheetButton("Add to Playlist", onClick = onAddToPlaylist)
        IpodActionSheetButton("Add to Top of Queue", onClick = onAddToQueueTop)
        IpodActionSheetButton("Add to Bottom of Queue", onClick = onAddToQueueBottom)
        IpodActionSheetButton(
            label = "Start Radio",
            enabled = radioEnabled,
            onClick = onStartRadio
        )
        IpodActionSheetButton(
            label = "Stem Mixer",
            enabled = stemMixerEnabled,
            onClick = onOpenStemMixer
        )
        IpodActionSheetButton(
            label = "Download",
            enabled = downloadEnabled,
            onClick = onDownload
        )
        IpodActionSheetButton(
            label = "Cancel",
            emphasized = true,
            onClick = onDismiss,
            modifier = Modifier.padding(top = 7.dp)
        )
    }
}

@Composable
internal fun IpodEntityRadioDialog(
    title: String,
    entityLabel: String,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onStartRadio: () -> Unit
) {
    IpodBottomDialog(onDismiss = onDismiss) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 16.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
        IpodActionSheetButton(
            label = "Start $entityLabel Radio",
            enabled = enabled,
            onClick = onStartRadio
        )
        IpodActionSheetButton(
            label = "Cancel",
            emphasized = true,
            onClick = onDismiss,
            modifier = Modifier.padding(top = 7.dp)
        )
    }
}

@Composable
internal fun IpodPlaylistPickerDialog(
    song: MediaItem,
    playlists: List<MediaItem>,
    onDismiss: () -> Unit,
    onPlaylistClick: (MediaItem) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val outerInteraction = remember { MutableInteractionSource() }
        val panelInteraction = remember { MutableInteractionSource() }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.58f))
                .clickable(
                    interactionSource = outerInteraction,
                    indication = null,
                    onClick = onDismiss
                )
                .padding(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 360.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFFE8E8E8))
                    .border(1.dp, Color(0xFF505050), RoundedCornerShape(9.dp))
                    .clickable(
                        interactionSource = panelInteraction,
                        indication = null,
                        onClick = {}
                    )
            ) {
                Text(
                    text = "Add to Playlist",
                    color = IpodColors.Text,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFFF8F8F8), Color(0xFFB8B8B8))
                            )
                        )
                        .padding(vertical = 11.dp, horizontal = 14.dp)
                )
                Text(
                    text = song.mediaMetadata.title?.toString() ?: "Unknown Song",
                    color = IpodColors.SecondaryText,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                )
                if (playlists.isEmpty()) {
                    Text(
                        text = "No playlists available",
                        color = IpodColors.SecondaryText,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(24.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .background(Color.White)
                    ) {
                        itemsIndexed(
                            items = playlists,
                            key = { index, playlist -> "picker-${playlist.mediaId}-$index" }
                        ) { _, playlist ->
                            val enabled = playlistMatchesSong(song, playlist)
                            IpodPlaylistPickerRow(
                                playlist = playlist,
                                enabled = enabled,
                                onClick = { onPlaylistClick(playlist) }
                            )
                        }
                    }
                }
                IpodActionSheetButton(
                    label = "Cancel",
                    emphasized = true,
                    onClick = onDismiss,
                    modifier = Modifier.padding(7.dp)
                )
            }
        }
    }
}

@Composable
private fun IpodBottomDialog(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val outerInteraction = remember { MutableInteractionSource() }
        val panelInteraction = remember { MutableInteractionSource() }
        Box(
            contentAlignment = Alignment.BottomCenter,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.54f))
                .clickable(
                    interactionSource = outerInteraction,
                    indication = null,
                    onClick = onDismiss
                )
                .navigationBarsPadding()
                .padding(horizontal = 9.dp, vertical = 10.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 360.dp)
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState())
                    .clickable(
                        interactionSource = panelInteraction,
                        indication = null,
                        onClick = {}
                    )
                    .background(Color.Black.copy(alpha = 0.33f), RoundedCornerShape(10.dp))
                    .padding(7.dp),
                content = content
            )
        }
    }
}

@Composable
private fun IpodPlaylistPickerRow(
    playlist: MediaItem,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(if (pressed) IpodColors.SelectedBlue else Color.White)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 12.dp)
    ) {
        Text(
            text = playlist.mediaMetadata.title?.toString() ?: "Untitled Playlist",
            color = when {
                pressed -> Color.White
                enabled -> IpodColors.Text
                else -> IpodColors.SecondaryText.copy(alpha = 0.48f)
            },
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "›",
            color = if (pressed) Color.White else Color(0xFF8C8C8C).copy(alpha = if (enabled) 1f else 0.4f),
            fontSize = 26.sp
        )
    }
}

@Composable
private fun IpodActionSheetButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasized: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val colors = when {
        !enabled -> listOf(Color(0xFF6B6B6B), Color(0xFF424242))
        pressed -> listOf(Color(0xFF6E9CC8), Color(0xFF275E92))
        emphasized -> listOf(Color(0xFF8BB8E2), Color(0xFF356FA8))
        else -> listOf(Color(0xFFF7F7F7), Color(0xFFB6B6B6))
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(Brush.verticalGradient(colors))
            .border(1.dp, Color.Black.copy(alpha = 0.72f), RoundedCornerShape(7.dp))
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
    ) {
        Text(
            text = label,
            color = when {
                !enabled -> Color.White.copy(alpha = 0.45f)
                emphasized || pressed -> Color.White
                else -> IpodColors.Text
            },
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
    }
}

private fun playlistMatchesSong(song: MediaItem, playlist: MediaItem): Boolean {
    val songId = song.mediaMetadata.extras?.getString("navidromeID") ?: return false
    val playlistId = playlist.mediaMetadata.extras?.getString("navidromeID") ?: return false
    return songId.startsWith("Local_") == playlistId.startsWith("Local_")
}
