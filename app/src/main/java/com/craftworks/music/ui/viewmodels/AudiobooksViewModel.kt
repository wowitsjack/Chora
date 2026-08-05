package com.craftworks.music.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.session.MediaController
import com.craftworks.music.data.repository.AudiobookBook
import com.craftworks.music.data.repository.AudiobookProgressRepository
import com.craftworks.music.data.repository.AudiobookRepository
import com.craftworks.music.data.repository.DownloadRepository
import com.craftworks.music.player.SongHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AudiobooksViewModel @Inject constructor(
    audiobookRepository: AudiobookRepository,
    private val progressRepository: AudiobookProgressRepository,
    private val downloadRepository: DownloadRepository
) : ViewModel() {
    val books: StateFlow<List<AudiobookBook>> = audiobookRepository.books.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    fun resume(book: AudiobookBook, mediaController: MediaController?) {
        val currentIndex = if (!book.hasStarted || book.isFinished) {
            0
        } else book.currentPartId
            ?.let { id ->
                book.parts.indexOfFirst {
                    it.mediaMetadata.extras?.getString("navidromeID") == id
                }
            }
            ?.takeIf { it >= 0 }
            ?: 0
        playPart(
            book = book,
            partIndex = currentIndex,
            positionMs = if (book.isFinished) 0L else book.resumePositionMs,
            mediaController = mediaController
        )
    }

    fun playPart(
        book: AudiobookBook,
        partIndex: Int,
        positionMs: Long,
        mediaController: MediaController?
    ) {
        if (book.parts.isEmpty()) return
        viewModelScope.launch {
            queueBookDownload(book, priorityPartIndex = partIndex)
            SongHelper.playCollectionNow(
                items = book.parts,
                index = partIndex.coerceIn(book.parts.indices),
                mediaController = mediaController,
                startPositionMs = positionMs.coerceAtLeast(0L)
            )
        }
    }

    fun restart(book: AudiobookBook, mediaController: MediaController?) {
        viewModelScope.launch {
            progressRepository.restartBook(book.id)
            queueBookDownload(book, priorityPartIndex = 0)
            SongHelper.playCollectionNow(
                items = book.parts,
                index = 0,
                mediaController = mediaController,
                startPositionMs = 0L
            )
        }
    }

    fun setSpeed(book: AudiobookBook, speed: Float, mediaController: MediaController?) {
        viewModelScope.launch {
            progressRepository.setBookSpeed(book.id, speed)
            val currentBookId = mediaController?.currentMediaItem
                ?.mediaMetadata
                ?.extras
                ?.getString("albumId")
            if (currentBookId == book.id) {
                mediaController.setPlaybackSpeed(speed)
            }
        }
    }

    fun downloadBook(book: AudiobookBook) {
        viewModelScope.launch {
            queueBookDownload(book)
        }
    }

    fun downloadPart(book: AudiobookBook, partIndex: Int) {
        val part = book.parts.getOrNull(partIndex) ?: return
        viewModelScope.launch {
            downloadRepository.queueSongDownload(part.mediaMetadata)
        }
    }

    private suspend fun queueBookDownload(
        book: AudiobookBook,
        priorityPartIndex: Int = 0
    ) {
        if (book.isDownloaded || book.parts.isEmpty()) return
        val safePriority = priorityPartIndex.coerceIn(book.parts.indices)
        val orderedParts = buildList {
            add(book.parts[safePriority])
            book.parts.forEachIndexed { index, part ->
                if (index != safePriority) add(part)
            }
        }
        downloadRepository.queueSongsDownload(orderedParts.map { it.mediaMetadata })
    }
}
