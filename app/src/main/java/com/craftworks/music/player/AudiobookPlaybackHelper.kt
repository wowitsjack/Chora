package com.craftworks.music.player

import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.craftworks.music.data.model.AudiobookChapter
import com.craftworks.music.data.model.MediaCategory
import com.craftworks.music.data.model.toSong

object AudiobookPlaybackHelper {
    private const val PREVIOUS_RESTART_THRESHOLD_MS = 3_000L

    fun isAudiobook(player: Player?): Boolean =
        player?.currentMediaItem?.mediaMetadata?.extras
            ?.getString("mediaCategory") == MediaCategory.AUDIOBOOK

    fun seekBack(player: Player?, amountMs: Long = 15_000L) {
        player ?: return
        player.seekTo((player.currentPosition - amountMs).coerceAtLeast(0L))
    }

    fun seekForward(player: Player?, amountMs: Long = 30_000L) {
        player ?: return
        val duration = player.duration.takeIf { it > 0L }
            ?: player.currentMediaItem?.mediaMetadata?.durationMs
            ?: Long.MAX_VALUE
        player.seekTo((player.currentPosition + amountMs).coerceAtMost(duration))
    }

    fun previousChapter(controller: MediaController?) {
        controller ?: return
        val chapters = controller.currentMediaItem?.toSong()?.chapters.orEmpty()
        val destination = previousChapterPosition(chapters, controller.currentPosition)
        if (destination != null) {
            controller.seekTo(destination)
        } else if (controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
        } else {
            controller.seekTo(0L)
        }
    }

    fun nextChapter(controller: MediaController?) {
        controller ?: return
        val chapters = controller.currentMediaItem?.toSong()?.chapters.orEmpty()
        val destination = nextChapterPosition(chapters, controller.currentPosition)
        if (destination != null) {
            controller.seekTo(destination)
        } else if (controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
        }
    }

    internal fun previousChapterPosition(
        chapters: List<AudiobookChapter>,
        positionMs: Long
    ): Long? {
        if (chapters.isEmpty()) return null
        val ordered = chapters.sortedBy(AudiobookChapter::startTimeMs)
        val currentIndex = ordered.indexOfLast { it.startTimeMs <= positionMs }
        if (currentIndex < 0) return null
        val current = ordered[currentIndex]
        return if (positionMs - current.startTimeMs > PREVIOUS_RESTART_THRESHOLD_MS) {
            current.startTimeMs
        } else {
            ordered.getOrNull(currentIndex - 1)?.startTimeMs
        }
    }

    internal fun nextChapterPosition(
        chapters: List<AudiobookChapter>,
        positionMs: Long
    ): Long? = chapters
        .asSequence()
        .map(AudiobookChapter::startTimeMs)
        .sorted()
        .firstOrNull { it > positionMs + 500L }
}
