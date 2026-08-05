@file:OptIn(UnstableApi::class) package com.craftworks.music.player

import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class SongHelper {
    companion object {
        private const val TAG = "SongHelper"
        internal const val QUEUE_INDEX_EXTRA = "com.craftworks.music.extra.QUEUE_INDEX"

        // Maximum items to send to MediaController to avoid Binder transaction limit
        // Android's Binder buffer is ~1MB, and each MediaItem can be several KB
        // Reduced from 200 to 50 to ensure IPC doesn't truncate
        private const val MAX_QUEUE_SIZE = 50

        // Use a single lock object to avoid synchronizing on a mutable field
        private val tracklistLock = Any()

        // Thread-safe backing field for the tracklist - no need for synchronizedList since we use explicit locking
        private var _currentTracklist: MutableList<MediaItem> = mutableListOf()

        private val _currentTracklistFlow = MutableStateFlow<List<MediaItem>>(emptyList())
        val currentTracklistFlow: StateFlow<List<MediaItem>> = _currentTracklistFlow.asStateFlow()

        // Track the offset of the current window in the full tracklist
        private var windowStartOffset: Int = 0
        private var tracklistVersion: Long = 0L

        // Thread-safe getter/setter - returns a defensive copy (not synchronized, since it's a copy)
        var currentTracklist: MutableList<MediaItem>
            get() = synchronized(tracklistLock) {
                _currentTracklist.toMutableList()
            }
            set(value) = synchronized(tracklistLock) {
                _currentTracklist.clear()
                _currentTracklist.addAll(value)
                windowStartOffset = 0
                publishTracklistLocked()
            }

        private fun publishTracklistLocked() {
            tracklistVersion += 1L
            _currentTracklistFlow.value = _currentTracklist.toList()
        }

        /**
         * Thread-safe index lookup by mediaId.
         * Returns -1 if not found.
         */
        fun indexOfMediaId(mediaId: String): Int = synchronized(tracklistLock) {
            _currentTracklist.indexOfFirst { it.mediaId == mediaId }
        }

        /**
         * Thread-safe move operation for reordering.
         */
        fun moveItem(fromIndex: Int, toIndex: Int) = synchronized(tracklistLock) {
            if (fromIndex in _currentTracklist.indices && toIndex in _currentTracklist.indices) {
                val item = _currentTracklist.removeAt(fromIndex)
                _currentTracklist.add(toIndex, item)
                publishTracklistLocked()
            }
        }

        /**
         * Get the current size of the tracklist in a thread-safe manner.
         */
        fun tracklistSize(): Int = synchronized(tracklistLock) {
            _currentTracklist.size
        }

        internal fun queueInsertionIndex(
            queueSize: Int,
            currentIndex: Int?,
            addToBottom: Boolean
        ): Int {
            if (addToBottom) return queueSize
            return ((currentIndex ?: -1) + 1).coerceIn(0, queueSize)
        }

        internal fun insertQueueItem(
            queue: MutableList<MediaItem>,
            song: MediaItem,
            currentIndex: Int?,
            addToBottom: Boolean
        ): Int {
            val insertionIndex = queueInsertionIndex(
                queueSize = queue.size,
                currentIndex = currentIndex,
                addToBottom = addToBottom
            )
            queue.add(insertionIndex, song)
            return insertionIndex
        }

        suspend fun play(
            mediaItems: List<MediaItem>,
            index: Int,
            mediaController: MediaController?,
            startPositionMs: Long = 0L
        ) {
            if (mediaItems.isEmpty())
                return

            val safeIndex = index.coerceIn(mediaItems.indices)

            // Store the full tracklist in memory
            currentTracklist = mediaItems.toMutableList()

            // Calculate the window to send to MediaController
            val (windowItems, windowIndex, windowOffset) = synchronized(tracklistLock) {
                if (mediaItems.size <= MAX_QUEUE_SIZE) {
                    // Small enough to send all items
                    windowStartOffset = 0
                    Triple(mediaItems, safeIndex, 0)
                } else {
                    // Calculate window centered around the selected index
                    val halfWindow = MAX_QUEUE_SIZE / 2
                    val start = (safeIndex - halfWindow).coerceAtLeast(0)
                    val end = (start + MAX_QUEUE_SIZE).coerceAtMost(mediaItems.size)
                    val adjustedStart = (end - MAX_QUEUE_SIZE).coerceAtLeast(0)

                    windowStartOffset = adjustedStart
                    val windowList = mediaItems.subList(adjustedStart, end)
                    val windowIdx = safeIndex - adjustedStart

                    Log.d(TAG, "Queue windowing: total=${mediaItems.size}, window=$adjustedStart-$end, windowIndex=$windowIdx")
                    Triple(windowList, windowIdx, adjustedStart)
                }
            }

            withContext(Dispatchers.Main) {
                // NOTE: Media3 IPC truncates large item lists due to Binder limits.
                // We only send the starting item; MusicService.onSetMediaItems will
                // retrieve the full window from SongHelper.currentTracklist
                val startingItem = windowItems.getOrNull(windowIndex) ?: windowItems.firstOrNull()
                if (startingItem != null) {
                    val indexedExtras = Bundle(startingItem.mediaMetadata.extras ?: Bundle()).apply {
                        putInt(QUEUE_INDEX_EXTRA, safeIndex)
                    }
                    val indexedStartingItem = startingItem.buildUpon()
                        .setMediaMetadata(
                            startingItem.mediaMetadata.buildUpon()
                                .setExtras(indexedExtras)
                                .build()
                        )
                        .build()
                    Log.d(TAG, "Setting starting item on MediaController: ${startingItem.mediaMetadata.title}")
                    mediaController?.setMediaItems(
                        listOf(indexedStartingItem),
                        0,
                        startPositionMs.coerceAtLeast(0L)
                    )
                    mediaController?.prepare()
                    mediaController?.play()
                    if (mediaController != null) {
                        NowPlayingOpenRequest.emit()
                    }
                }
            }
        }

        internal data class QueueWindowExtension(
            val items: List<MediaItem>,
            val insertAtStart: Boolean,
            val expectedWindowStartOffset: Int,
            val resultingWindowStartOffset: Int,
            val tracklistVersion: Long
        )

        /**
         * Plans the next bounded Media3 window without changing the logical queue.
         * The service resolves and inserts these items before playback reaches an edge.
         */
        internal fun planQueueWindowExtension(
            currentIndex: Int,
            visibleItemCount: Int,
            edgeThreshold: Int = 10
        ): QueueWindowExtension? = synchronized(tracklistLock) {
            if (
                currentIndex < 0 ||
                visibleItemCount <= 0 ||
                _currentTracklist.size <= visibleItemCount
            ) {
                return@synchronized null
            }

            val threshold = edgeThreshold.coerceAtLeast(0)
            val nearEnd = currentIndex >= (visibleItemCount - 1 - threshold).coerceAtLeast(0)
            val visibleEnd = (windowStartOffset + visibleItemCount)
                .coerceAtMost(_currentTracklist.size)

            if (nearEnd && visibleEnd < _currentTracklist.size) {
                val nextEnd = (visibleEnd + MAX_QUEUE_SIZE).coerceAtMost(_currentTracklist.size)
                return@synchronized QueueWindowExtension(
                    items = _currentTracklist.subList(visibleEnd, nextEnd).toList(),
                    insertAtStart = false,
                    expectedWindowStartOffset = windowStartOffset,
                    resultingWindowStartOffset = windowStartOffset,
                    tracklistVersion = tracklistVersion
                )
            }

            val nearStart = currentIndex <= threshold
            if (nearStart && windowStartOffset > 0) {
                val previousStart = (windowStartOffset - MAX_QUEUE_SIZE).coerceAtLeast(0)
                return@synchronized QueueWindowExtension(
                    items = _currentTracklist.subList(previousStart, windowStartOffset).toList(),
                    insertAtStart = true,
                    expectedWindowStartOffset = windowStartOffset,
                    resultingWindowStartOffset = previousStart,
                    tracklistVersion = tracklistVersion
                )
            }

            null
        }

        internal fun isQueueWindowExtensionCurrent(extension: QueueWindowExtension): Boolean =
            synchronized(tracklistLock) {
                extension.tracklistVersion == tracklistVersion &&
                    extension.expectedWindowStartOffset == windowStartOffset
            }

        internal fun commitQueueWindowExtension(extension: QueueWindowExtension): Boolean =
            synchronized(tracklistLock) {
                if (
                    extension.tracklistVersion != tracklistVersion ||
                    extension.expectedWindowStartOffset != windowStartOffset
                ) {
                    false
                } else {
                    windowStartOffset = extension.resultingWindowStartOffset
                    true
                }
            }

        private data class PlayerQueueSnapshot(
            val currentIndex: Int?,
            val itemCount: Int,
            val currentMediaId: String?,
            val playbackState: Int,
            val hasPlayerError: Boolean
        )

        internal fun needsPlaybackRebuild(playbackState: Int, hasPlayerError: Boolean): Boolean =
            hasPlayerError || playbackState == Player.STATE_IDLE

        private suspend fun playerQueueSnapshot(
            mediaController: MediaController?
        ): PlayerQueueSnapshot = withContext(Dispatchers.Main) {
            PlayerQueueSnapshot(
                currentIndex = mediaController?.currentMediaItemIndex?.takeIf { it >= 0 },
                itemCount = mediaController?.mediaItemCount ?: 0,
                currentMediaId = mediaController?.currentMediaItem?.mediaId,
                playbackState = mediaController?.playbackState ?: Player.STATE_IDLE,
                hasPlayerError = mediaController?.playerError != null
            )
        }

        private suspend fun insertIntoQueue(
            song: MediaItem,
            mediaController: MediaController?,
            addToBottom: Boolean,
            playImmediately: Boolean,
            startPositionMs: Long = 0L
        ) {
            val snapshot = playerQueueSnapshot(mediaController)
            val (fullInsertionIndex, playerInsertionIndex) = synchronized(tracklistLock) {
                val currentFullIndex = snapshot.currentIndex
                    ?.let { windowStartOffset + it }
                    ?.takeIf { it in _currentTracklist.indices }
                val insertionIndex = insertQueueItem(
                    queue = _currentTracklist,
                    song = song,
                    currentIndex = currentFullIndex,
                    addToBottom = addToBottom
                )
                publishTracklistLocked()
                val visibleIndex = if (snapshot.itemCount == 0) {
                    windowStartOffset = insertionIndex
                    0
                } else {
                    val visibleEnd = windowStartOffset + snapshot.itemCount
                    if (insertionIndex in windowStartOffset..visibleEnd) {
                        insertionIndex - windowStartOffset
                    } else {
                        null
                    }
                }
                insertionIndex to visibleIndex
            }

            if (playImmediately) {
                play(currentTracklist, fullInsertionIndex, mediaController, startPositionMs)
                return
            }

            if (mediaController != null && playerInsertionIndex != null) {
                try {
                    withContext(Dispatchers.Main) {
                        mediaController.addMediaItem(playerInsertionIndex, song)
                    }
                } catch (e: IllegalStateException) {
                    synchronized(tracklistLock) {
                        if (_currentTracklist.getOrNull(fullInsertionIndex) === song) {
                            _currentTracklist.removeAt(fullInsertionIndex)
                            publishTracklistLocked()
                        }
                    }
                    throw e
                }
            }
        }

        /**
         * Add a song to the queue without interrupting playback.
         * @param addToBottom true = add to end of queue, false = add after current song
         */
        suspend fun addToQueue(song: MediaItem, mediaController: MediaController?, addToBottom: Boolean = true) {
            insertIntoQueue(song, mediaController, addToBottom, playImmediately = false)
        }

        suspend fun addToQueueTop(song: MediaItem, mediaController: MediaController?) {
            addToQueue(song, mediaController, addToBottom = false)
        }

        suspend fun addToQueueBottom(song: MediaItem, mediaController: MediaController?) {
            addToQueue(song, mediaController, addToBottom = true)
        }

        /**
         * Insert a song immediately after the currently playing track
         */
        suspend fun playNext(song: MediaItem, mediaController: MediaController?) {
            addToQueueTop(song, mediaController)
        }

        /**
         * Start one selected song while preserving every item already queued.
         * The selected song is inserted immediately after the current item and
         * becomes the active item; the previous upcoming item follows it.
         */
        suspend fun playNow(
            song: MediaItem,
            mediaController: MediaController?,
            startPositionMs: Long = 0L
        ) {
            if (mediaController == null) return

            val snapshot = playerQueueSnapshot(mediaController)
            val hasLogicalQueue = synchronized(tracklistLock) {
                _currentTracklist.isNotEmpty()
            }
            if (!hasLogicalQueue || snapshot.itemCount == 0) {
                play(listOf(song), 0, mediaController, startPositionMs)
                return
            }

            if (snapshot.currentMediaId == song.mediaId) {
                if (needsPlaybackRebuild(snapshot.playbackState, snapshot.hasPlayerError)) {
                    val queue = currentTracklist
                    val songIndex = queue.indexOfFirst { it.mediaId == song.mediaId }
                    if (songIndex >= 0) {
                        play(queue, songIndex, mediaController)
                    } else {
                        play(listOf(song), 0, mediaController)
                    }
                    return
                }
                withContext(Dispatchers.Main) {
                    mediaController.seekTo(startPositionMs.coerceAtLeast(0L))
                    mediaController.play()
                    NowPlayingOpenRequest.emit()
                }
                return
            }

            insertIntoQueue(
                song = song,
                mediaController = mediaController,
                addToBottom = false,
                playImmediately = true,
                startPositionMs = startPositionMs
            )
        }

        /**
         * Inserts a whole book/album after the current item and starts the requested
         * part without discarding the logical queue that was already there.
         */
        suspend fun playCollectionNow(
            items: List<MediaItem>,
            index: Int,
            mediaController: MediaController?,
            startPositionMs: Long = 0L
        ) {
            if (items.isEmpty() || mediaController == null) return
            val safeIndex = index.coerceIn(items.indices)
            val snapshot = playerQueueSnapshot(mediaController)
            val insertionIndex = synchronized(tracklistLock) {
                if (_currentTracklist.isEmpty() || snapshot.itemCount == 0) {
                    _currentTracklist.clear()
                    _currentTracklist.addAll(items)
                    windowStartOffset = 0
                    publishTracklistLocked()
                    0
                } else {
                    val currentFullIndex = snapshot.currentIndex
                        ?.let { windowStartOffset + it }
                        ?.takeIf { it in _currentTracklist.indices }
                        ?: _currentTracklist.lastIndex
                    val insertAt = (currentFullIndex + 1).coerceIn(0, _currentTracklist.size)
                    _currentTracklist.addAll(insertAt, items)
                    publishTracklistLocked()
                    insertAt
                }
            }
            play(
                mediaItems = currentTracklist,
                index = insertionIndex + safeIndex,
                mediaController = mediaController,
                startPositionMs = startPositionMs
            )
        }

        suspend fun togglePlayback(mediaController: MediaController?) {
            if (mediaController == null) return

            val snapshot = playerQueueSnapshot(mediaController)
            val isPlaying = withContext(Dispatchers.Main) {
                mediaController.isPlaying
            }
            if (isPlaying) {
                withContext(Dispatchers.Main) {
                    mediaController.pause()
                }
                return
            }

            if (needsPlaybackRebuild(snapshot.playbackState, snapshot.hasPlayerError)) {
                val queue = currentTracklist
                val currentIndex = snapshot.currentMediaId
                    ?.let { mediaId -> queue.indexOfFirst { it.mediaId == mediaId } }
                    ?.takeIf { it >= 0 }
                if (currentIndex != null) {
                    play(queue, currentIndex, mediaController)
                    return
                }
            }

            withContext(Dispatchers.Main) {
                mediaController.play()
            }
        }

        /**
         * Jump to an item already in the logical queue without rebuilding it.
         */
        suspend fun playQueueItem(index: Int, mediaController: MediaController?) {
            if (mediaController == null) return

            val snapshot = playerQueueSnapshot(mediaController)
            val playerIndex = synchronized(tracklistLock) {
                if (index !in _currentTracklist.indices) return@synchronized null
                (index - windowStartOffset).takeIf {
                    it in 0 until snapshot.itemCount
                }
            }

            if (
                playerIndex != null &&
                !needsPlaybackRebuild(snapshot.playbackState, snapshot.hasPlayerError)
            ) {
                withContext(Dispatchers.Main) {
                    mediaController.seekTo(playerIndex, 0L)
                    mediaController.play()
                    NowPlayingOpenRequest.emit()
                }
            } else {
                play(currentTracklist, index, mediaController)
            }
        }

        /**
         * Remove a song from the queue at the given index.
         * Returns true if removal was successful, false if index was invalid.
         */
        suspend fun removeFromQueue(index: Int, mediaController: MediaController?): Boolean {
            val playerIndex = synchronized(tracklistLock) {
                if (index in _currentTracklist.indices) {
                    _currentTracklist.removeAt(index)
                    val visibleIndex = index - windowStartOffset
                    if (index < windowStartOffset) {
                        windowStartOffset = (windowStartOffset - 1).coerceAtLeast(0)
                    }
                    publishTracklistLocked()
                    visibleIndex
                } else {
                    null
                }
            }
            if (playerIndex != null) {
                withContext(Dispatchers.Main) {
                    try {
                        if (playerIndex in 0 until (mediaController?.mediaItemCount ?: 0)) {
                            mediaController?.removeMediaItem(playerIndex)
                        }
                    } catch (e: IllegalStateException) {
                        // Player may have been released or index became invalid
                    }
                }
            }
            return playerIndex != null
        }

        /**
         * Clear all songs from queue except the currently playing one
         */
        suspend fun clearQueue(mediaController: MediaController?) {
            val currentIdx = withContext(Dispatchers.Main) {
                mediaController?.currentMediaItemIndex ?: 0
            }

            val (newQueue, currentItem) = synchronized(tracklistLock) {
                val currentSong = _currentTracklist.getOrNull(windowStartOffset + currentIdx)
                _currentTracklist.clear()
                currentSong?.let { _currentTracklist.add(it) }
                windowStartOffset = 0
                publishTracklistLocked()
                Pair(_currentTracklist.toList(), currentSong)
            }

            withContext(Dispatchers.Main) {
                try {
                    if (currentItem != null) {
                        // atomic update: set queue to just the current item, preserving position
                        val pos = mediaController?.currentPosition ?: 0L
                        mediaController?.setMediaItems(newQueue, 0, pos)
                        mediaController?.prepare()
                        mediaController?.play()
                    } else {
                        mediaController?.clearMediaItems()
                    }
                } catch (e: IllegalStateException) {
                    // Player may have been released
                }
            }
        }

        /**
         * Shuffle the queue (keeping current song in place)
         */
        suspend fun shuffleQueue(mediaController: MediaController?) {
            if (mediaController == null) return

            val currentIdx = withContext(Dispatchers.Main) {
                mediaController.currentMediaItemIndex
            }

            val (shouldShuffle, windowQueue) = synchronized(tracklistLock) {
                if (_currentTracklist.size <= 1) {
                    Pair(false, emptyList())
                } else {
                    // Get actual index in full tracklist
                    val actualIdx = windowStartOffset + currentIdx
                    val currentSong = _currentTracklist.getOrNull(actualIdx)
                    val songsToShuffle = _currentTracklist.filterIndexed { idx, _ -> idx != actualIdx }
                    val shuffled = songsToShuffle.shuffled()

                    _currentTracklist.clear()
                    currentSong?.let { _currentTracklist.add(it) }
                    _currentTracklist.addAll(shuffled)

                    // Reset window to start and apply windowing
                    windowStartOffset = 0
                    publishTracklistLocked()
                    val windowSize = _currentTracklist.size.coerceAtMost(MAX_QUEUE_SIZE)
                    Pair(true, _currentTracklist.take(windowSize))
                }
            }

            if (shouldShuffle) {
                // Rebuild the player queue atomically with windowed items
                withContext(Dispatchers.Main) {
                    try {
                        val currentPosition = mediaController.currentPosition
                        mediaController.setMediaItems(windowQueue, 0, currentPosition)
                        mediaController.prepare()
                        mediaController.play()
                    } catch (e: IllegalStateException) {
                        // Player may have been released
                    }
                }
            }
        }
    }
}
