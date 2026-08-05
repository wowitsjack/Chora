package com.craftworks.music.ui.playing

import androidx.annotation.OptIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.craftworks.music.player.SongHelper
import com.craftworks.music.R
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(UnstableApi::class, ExperimentalFoundationApi::class)
@Composable
fun PlayQueueContent(
    mediaController: MediaController?
) {
    if (mediaController == null) return

    var currentMediaItem by remember { mutableStateOf<MediaItem?>(null) }
    val queueItems by SongHelper.currentTracklistFlow.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    // Observe controller changes
    DisposableEffect(mediaController) {
        val callback = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentMediaItem = mediaItem
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) = Unit
        }

        // Initial state
        currentMediaItem = mediaController.currentMediaItem

        mediaController.addListener(callback)
        onDispose {
            mediaController.removeListener(callback)
        }
    }

    val lazyListState = rememberLazyListState()
    val reorderableLazyColumnState =
        rememberReorderableLazyListState(lazyListState) { from, to ->
            //TODO: Reorder list items.
        }

    if (queueItems.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.Queue_Empty_Title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.Queue_Empty_Description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            state = lazyListState
        ) {
            itemsIndexed(
                items = queueItems,
                key = { index, mediaItem -> "${mediaItem.mediaId}-$index" }
            ) { index, mediaItem ->
                ReorderableItem(reorderableLazyColumnState, "${mediaItem.mediaId}-$index") {
                    val isPlaying = mediaItem.mediaId == currentMediaItem?.mediaId

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .animateItem()
                            .height(64.dp)
                            .fillMaxWidth()
                            .background(
                                if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                else Color.Transparent
                            )
                            .clickable {
                                coroutineScope.launch {
                                    SongHelper.playQueueItem(index, mediaController)
                                }
                            }
                            .padding(start = 16.dp, end = 4.dp)
                    ) {
                    // Track number or playing indicator
                    Box(modifier = Modifier.width(36.dp)) {
                        if (isPlaying) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Now Playing",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Text(
                                text = "${index + 1}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.CenterStart)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))


                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                        Text(
                            text = mediaItem.mediaMetadata.title?.toString() ?: "Unknown",
                            fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = mediaItem.mediaMetadata.artist?.toString() ?: "Unknown Artist",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        }

                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    SongHelper.removeFromQueue(index, mediaController)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.rounded_delete_24),
                                contentDescription = stringResource(
                                    R.string.Queue_Remove_Item,
                                    mediaItem.mediaMetadata.title?.toString() ?: ""
                                ),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

//                    Icon(
//                        imageVector = ImageVector.vectorResource(R.drawable.baseline_drag_handle_24),
//                        contentDescription = "Reorder",
//                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
//                        modifier = Modifier.draggableHandle()
//                    )
                    }
                }
            }
        }
    }
}
