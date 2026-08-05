package com.craftworks.music.ui.ipod

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import com.craftworks.music.data.model.toSong
import com.craftworks.music.data.repository.AudiobookBook

@Composable
internal fun IpodAudiobooksScreen(
    books: List<AudiobookBook>,
    currentMediaId: String?,
    onBookClick: (AudiobookBook) -> Unit,
    onResume: (AudiobookBook) -> Unit
) {
    if (books.isEmpty()) {
        IpodMessage("No Audiobooks", "Enable an audiobook library in Chora, then sync.")
        return
    }

    val continueListening = remember(books) {
        books.filter { it.hasStarted && !it.isFinished }.sortedByDescending { it.updatedAt }
    }
    val downloaded = remember(books) { books.filter(AudiobookBook::isDownloaded) }
    val finished = remember(books) { books.filter(AudiobookBook::isFinished) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(IpodColors.Content)
    ) {
        if (continueListening.isNotEmpty()) {
            item("continue-header") { IpodSectionHeader("Continue Listening") }
            items(continueListening, key = { "continue-${it.id}" }) { book ->
                IpodAudiobookRow(
                    book = book,
                    isCurrent = book.parts.any { it.mediaId == currentMediaId },
                    actionLabel = "Resume",
                    onClick = { onResume(book) }
                )
            }
        }
        if (downloaded.isNotEmpty()) {
            item("downloaded-header") { IpodSectionHeader("Downloaded") }
            items(downloaded, key = { "downloaded-${it.id}" }) { book ->
                IpodAudiobookRow(book, false, null) { onBookClick(book) }
            }
        }
        if (finished.isNotEmpty()) {
            item("finished-header") { IpodSectionHeader("Finished") }
            items(finished, key = { "finished-${it.id}" }) { book ->
                IpodAudiobookRow(book, false, null) { onBookClick(book) }
            }
        }
        item("all-header") { IpodSectionHeader("All Books") }
        items(books, key = AudiobookBook::id) { book ->
            IpodAudiobookRow(
                book = book,
                isCurrent = book.parts.any { it.mediaId == currentMediaId },
                actionLabel = null,
                onClick = { onBookClick(book) }
            )
        }
    }
}

@Composable
private fun IpodAudiobookRow(
    book: AudiobookBook,
    isCurrent: Boolean,
    actionLabel: String?,
    onClick: () -> Unit
) {
    val metadata = book.album.mediaMetadata
    val title = metadata.title?.toString() ?: metadata.albumTitle?.toString() ?: "Untitled Book"
    val author = metadata.artist?.toString() ?: metadata.albumArtist?.toString().orEmpty()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(IpodColors.Content)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        IpodArtwork(
            artwork = metadata.artworkUri,
            title = title,
            artist = author,
            identity = book.id,
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(3.dp))
        )
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 9.dp)
        ) {
            Text(
                text = title,
                color = if (isCurrent) IpodColors.Blue else IpodColors.Text,
                fontSize = 16.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = author,
                color = IpodColors.SecondaryText,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IpodProgressBar(book.progressFraction, Modifier.padding(top = 5.dp))
        }
        IpodActionButton(
            label = actionLabel ?: "View",
            onClick = onClick
        )
    }
}

@Composable
internal fun IpodAudiobookDetail(
    book: AudiobookBook,
    playbackState: IpodPlaybackState,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onDownloadBook: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onPlayPart: (Int, Long) -> Unit,
    onDownloadPart: (Int) -> Unit,
    onPartLongClick: (MediaItem) -> Unit
) {
    val metadata = book.album.mediaMetadata
    val title = metadata.title?.toString() ?: metadata.albumTitle?.toString() ?: "Untitled Book"
    val author = metadata.artist?.toString() ?: metadata.albumArtist?.toString().orEmpty()
    var speedDialogOpen by remember { mutableStateOf(false) }
    var sleepDialogOpen by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(IpodColors.Content)
    ) {
        item("header") {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                IpodArtwork(
                    artwork = metadata.artworkUri,
                    title = title,
                    artist = author,
                    identity = book.id,
                    modifier = Modifier
                        .size(112.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = IpodColors.Text,
                        fontSize = 18.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = author,
                        color = IpodColors.SecondaryText,
                        fontSize = 13.sp,
                        lineHeight = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    IpodProgressBar(book.progressFraction, Modifier.padding(top = 9.dp))
                    Text(
                        text = "${(book.progressFraction * 100).toInt()}% · ${ipodBookTime(book.remainingMs)} left",
                        color = IpodColors.SecondaryText,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                    IpodActionButton(
                        label = when {
                            !book.isDownloaded -> "Download & Play"
                            book.hasStarted && !book.isFinished -> "Resume"
                            else -> "Play"
                        },
                        onClick = onResume,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        item("options-header") { IpodSectionHeader("Book Controls") }
        item("speed") {
            IpodListRow(
                title = "Playback Speed",
                subtitle = ipodSpeedLabel(book.playbackSpeed),
                showChevron = true,
                onClick = { speedDialogOpen = true }
            )
        }
        item("sleep") {
            IpodListRow(
                title = "Sleep Timer",
                subtitle = "Stop after a chosen time or part",
                showChevron = true,
                onClick = { sleepDialogOpen = true }
            )
        }
        item("download") {
            IpodListRow(
                title = if (book.isDownloaded) "Available Offline" else "Download Book",
                subtitle = if (book.isDownloaded) "Every part is stored on this iPod" else "Store every part on this iPod",
                onClick = onDownloadBook
            )
        }
        if (book.hasPendingProgress) {
            item("progress-sync") {
                IpodListRow(
                    title = "Progress Saved",
                    subtitle = "Stored on this iPod; syncs when online",
                    onClick = { }
                )
            }
        }
        if (book.hasStarted) {
            item("restart") {
                IpodListRow(
                    title = "Start Over",
                    subtitle = "Clear saved progress",
                    onClick = onRestart
                )
            }
        }

        item("parts-header") { IpodSectionHeader("Parts & Chapters") }
        book.parts.forEachIndexed { partIndex, part ->
            item("part-${part.mediaId}") {
                IpodListRow(
                    title = part.mediaMetadata.title?.toString() ?: "Part ${partIndex + 1}",
                    subtitle = buildString {
                        append("Part ${partIndex + 1} · ${ipodBookTime(part.mediaMetadata.durationMs ?: 0L)}")
                        if (book.isPartDownloaded(part)) append(" · Downloaded")
                    },
                    prefix = (partIndex + 1).toString(),
                    isCurrent = playbackState.currentItem?.mediaId == part.mediaId,
                    onClick = { onPlayPart(partIndex, 0L) },
                    onLongClick = { onPartLongClick(part) }
                )
            }

            val chapters = part.toSong().chapters
            chapters.forEachIndexed { chapterIndex, chapter ->
                item("chapter-${part.mediaId}-${chapter.id}") {
                    val isCurrentChapter = playbackState.currentItem?.mediaId == part.mediaId &&
                        playbackState.positionMs in chapter.startTimeMs until chapter.endTimeMs
                    IpodListRow(
                        title = chapter.title,
                        subtitle = ipodBookTime(chapter.endTimeMs - chapter.startTimeMs),
                        prefix = "${partIndex + 1}.${chapterIndex + 1}",
                        isCurrent = isCurrentChapter,
                        onClick = { onPlayPart(partIndex, chapter.startTimeMs) }
                    )
                }
            }
            if (chapters.isEmpty() && book.parts.size == 1) {
                item("no-chapters-${part.mediaId}") {
                    IpodListRow(
                        title = "No embedded chapter markers",
                        subtitle = "The complete book is one playable part",
                        onClick = { onPlayPart(partIndex, 0L) }
                    )
                }
            }
            item("download-part-${part.mediaId}") {
                IpodListRow(
                    title = "Download Part ${partIndex + 1}",
                    subtitle = "Make this part available offline",
                    onClick = { onDownloadPart(partIndex) }
                )
            }
        }
    }

    if (speedDialogOpen) {
        IpodPlaybackSpeedDialog(
            currentSpeed = book.playbackSpeed,
            onDismiss = { speedDialogOpen = false },
            onSelected = { speed ->
                onSpeedChange(speed)
                speedDialogOpen = false
            }
        )
    }
    if (sleepDialogOpen) {
        IpodSleepTimerDialog(onDismiss = { sleepDialogOpen = false })
    }
}

@Composable
private fun IpodProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFFD2D2D2))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(5.dp)
                .background(IpodColors.Blue)
        )
    }
}

private fun ipodBookTime(milliseconds: Long): String {
    val totalMinutes = milliseconds.coerceAtLeast(0L) / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}

private fun ipodSpeedLabel(speed: Float): String =
    if (speed.toInt().toFloat() == speed) "${speed.toInt()}×" else "${speed}×"
