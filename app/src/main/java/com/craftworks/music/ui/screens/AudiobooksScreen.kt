package com.craftworks.music.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.navigation.NavHostController
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.craftworks.music.R
import com.craftworks.music.data.model.AudiobookChapter
import com.craftworks.music.data.model.Screen
import com.craftworks.music.data.model.toSong
import com.craftworks.music.data.repository.AudiobookBook
import com.craftworks.music.managers.SleepTimerManager
import com.craftworks.music.player.SongHelper
import com.craftworks.music.ui.elements.GeneratedAlbumArtStatic
import com.craftworks.music.ui.viewmodels.AudiobooksViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val audiobookSpeeds = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)

internal fun formatAudiobookTime(milliseconds: Long): String {
    val totalMinutes = milliseconds.coerceAtLeast(0L) / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L && minutes > 0L -> "${hours}h ${minutes}m"
        hours > 0L -> "${hours}h"
        else -> "${minutes}m"
    }
}

private fun Float.speedLabel(): String =
    if (this == roundToInt().toFloat()) "${roundToInt()}×" else "${this}×"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiobooksScreen(
    navController: NavHostController,
    mediaController: MediaController?,
    viewModel: AudiobooksViewModel = hiltViewModel()
) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    val continueListening = remember(books) {
        books.filter { it.hasStarted && !it.isFinished }.sortedByDescending { it.updatedAt }
    }
    val downloaded = remember(books) { books.filter(AudiobookBook::isDownloaded) }
    val finished = remember(books) { books.filter(AudiobookBook::isFinished) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.Audiobooks), fontWeight = FontWeight.SemiBold)
                        if (books.isNotEmpty()) {
                            Text(
                                text = "${books.size} ${if (books.size == 1) "book" else "books"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        if (books.isEmpty()) {
            AudiobookEmptyState(Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (continueListening.isNotEmpty()) {
                    item("continue-title") {
                        AudiobookSectionTitle(stringResource(R.string.Audiobooks_Continue))
                    }
                    item("continue-row") {
                        LazyRow(
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(continueListening, key = { it.id }) { book ->
                                ContinueListeningCard(
                                    book = book,
                                    onResume = { viewModel.resume(book, mediaController) },
                                    onOpen = {
                                        navController.navigate("${Screen.AudiobookDetails.route}/${book.id}")
                                    }
                                )
                            }
                        }
                    }
                }

                if (downloaded.isNotEmpty()) {
                    item("downloaded-title") {
                        AudiobookSectionTitle(stringResource(R.string.Audiobooks_Downloaded))
                    }
                    item("downloaded-row") {
                        CompactAudiobookRow(downloaded) { book ->
                            navController.navigate("${Screen.AudiobookDetails.route}/${book.id}")
                        }
                    }
                }

                if (finished.isNotEmpty()) {
                    item("finished-title") {
                        AudiobookSectionTitle(stringResource(R.string.Audiobooks_Finished))
                    }
                    item("finished-row") {
                        CompactAudiobookRow(finished) { book ->
                            navController.navigate("${Screen.AudiobookDetails.route}/${book.id}")
                        }
                    }
                }

                item("all-title") {
                    AudiobookSectionTitle(stringResource(R.string.Audiobooks_All))
                }
                items(books.chunked(2), key = { row -> row.joinToString("|") { it.id } }) { rowBooks ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        rowBooks.forEach { book ->
                            AudiobookGridCard(
                                book = book,
                                onClick = {
                                    navController.navigate("${Screen.AudiobookDetails.route}/${book.id}")
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowBooks.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                item("bottom-space") { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun AudiobookEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.rounded_auto_stories_24),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(18.dp))
        Text(
            stringResource(R.string.Audiobooks_Empty_Title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.Audiobooks_Empty_Description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AudiobookSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
    )
}

@Composable
private fun ContinueListeningCard(
    book: AudiobookBook,
    onResume: () -> Unit,
    onOpen: () -> Unit
) {
    ElevatedCard(onClick = onOpen, modifier = Modifier.width(300.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AudiobookCover(book, Modifier.size(92.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = book.album.mediaMetadata.title?.toString().orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = book.album.mediaMetadata.artist?.toString().orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                LinearProgressIndicator(
                    progress = { book.progressFraction },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(
                            R.string.Audiobooks_Time_Left,
                            formatAudiobookTime(book.remainingMs)
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalButton(onClick = onResume, modifier = Modifier.height(36.dp)) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.Audiobooks_Resume))
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactAudiobookRow(books: List<AudiobookBook>, onOpen: (AudiobookBook) -> Unit) {
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(books, key = { it.id }) { book ->
            AudiobookGridCard(book, { onOpen(book) }, Modifier.width(150.dp))
        }
    }
}

@Composable
private fun AudiobookGridCard(
    book: AudiobookBook,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onClick)
            .padding(6.dp)
    ) {
        Box {
            AudiobookCover(book, Modifier.fillMaxWidth())
            if (book.isDownloaded) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.rounded_download_24),
                    contentDescription = stringResource(R.string.Audiobooks_Downloaded),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(22.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                        .padding(3.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = book.album.mediaMetadata.title?.toString().orEmpty(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = book.album.mediaMetadata.artist?.toString().orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (book.hasStarted) {
            Spacer(Modifier.height(7.dp))
            LinearProgressIndicator(
                progress = { book.progressFraction },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AudiobookCover(book: AudiobookBook, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val artworkUri = book.album.mediaMetadata.artworkUri?.toString()
    val identity = book.album.mediaMetadata.extras?.getString("navidromeID") ?: book.album.mediaId
    val coverModifier = modifier
        .aspectRatio(1f)
        .clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
    if (artworkUri.isNullOrBlank() || artworkUri.contains("coverArt?id=&")) {
        GeneratedAlbumArtStatic(
            title = book.album.mediaMetadata.title?.toString() ?: "?",
            artist = book.album.mediaMetadata.artist?.toString(),
            album = identity,
            modifier = coverModifier
        )
    } else {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(artworkUri.replace("size=128", "size=512"))
                .memoryCacheKey("audiobook_${identity}_512")
                .diskCacheKey("audiobook_${identity}_512")
                .crossfade(true)
                .build(),
            contentDescription = book.album.mediaMetadata.title?.toString(),
            contentScale = ContentScale.Crop,
            modifier = coverModifier,
            error = {
                GeneratedAlbumArtStatic(
                    title = book.album.mediaMetadata.title?.toString() ?: "?",
                    artist = book.album.mediaMetadata.artist?.toString(),
                    album = identity,
                    modifier = Modifier.fillMaxSize()
                )
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AudiobookDetailsScreen(
    albumId: String,
    navController: NavHostController,
    mediaController: MediaController?,
    viewModel: AudiobooksViewModel = hiltViewModel()
) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    val book = books.firstOrNull { it.id == albumId }
    var speedMenuOpen by remember { mutableStateOf(false) }
    var sleepMenuOpen by remember { mutableStateOf(false) }
    val sleepRemaining by SleepTimerManager.remainingTimeMs.collectAsStateWithLifecycle()
    val sleepActive by SleepTimerManager.isTimerActive.collectAsStateWithLifecycle()
    val playback = rememberAudiobookPlayback(mediaController)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(book?.album?.mediaMetadata?.title?.toString() ?: stringResource(R.string.Audiobooks)) },
                navigationIcon = {
                    IconButton(onClick = navController::popBackStack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (book == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "This book is not available in the synced library.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        val chapterSets = remember(book.parts) { book.parts.map { it.toSong().chapters } }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item("book-header") {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AudiobookCover(book, Modifier.size(224.dp))
                    Spacer(Modifier.height(18.dp))
                    Text(
                        book.album.mediaMetadata.title?.toString().orEmpty(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        book.album.mediaMetadata.artist?.toString().orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { book.progressFraction },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Text(
                            "${(book.progressFraction * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            stringResource(
                                R.string.Audiobooks_Time_Left,
                                formatAudiobookTime(book.remainingMs)
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.resume(book, mediaController) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (book.isDownloaded) {
                                    Icons.Rounded.PlayArrow
                                } else {
                                    ImageVector.vectorResource(R.drawable.rounded_download_24)
                                },
                                contentDescription = null
                            )
                            Text(
                                if (!book.isDownloaded) {
                                    stringResource(R.string.Audiobooks_Download_Listen)
                                } else if (book.hasStarted && !book.isFinished) {
                                    stringResource(R.string.Audiobooks_Resume)
                                } else {
                                    stringResource(R.string.Audiobooks_Start)
                                }
                            )
                        }
                        FilledTonalButton(
                            onClick = { viewModel.downloadBook(book) },
                            enabled = !book.isDownloaded,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                ImageVector.vectorResource(R.drawable.rounded_download_24),
                                contentDescription = null
                            )
                            Text(
                                stringResource(
                                    if (book.isDownloaded) {
                                        R.string.Audiobooks_Available_Offline
                                    } else {
                                        R.string.Audiobooks_Download_Book
                                    }
                                )
                            )
                        }
                    }
                    if (book.hasPendingProgress) {
                        Text(
                            text = stringResource(R.string.Audiobooks_Progress_Saved_Offline),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(Modifier.weight(1f)) {
                            FilledTonalButton(
                                onClick = { speedMenuOpen = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("${stringResource(R.string.Audiobooks_Speed)} · ${book.playbackSpeed.speedLabel()}")
                            }
                            DropdownMenu(
                                expanded = speedMenuOpen,
                                onDismissRequest = { speedMenuOpen = false }
                            ) {
                                audiobookSpeeds.forEach { speed ->
                                    DropdownMenuItem(
                                        text = { Text(speed.speedLabel()) },
                                        onClick = {
                                            viewModel.setSpeed(book, speed, mediaController)
                                            speedMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            FilledTonalButton(
                                onClick = { sleepMenuOpen = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (sleepActive) {
                                        "${stringResource(R.string.Audiobooks_Sleep)} · ${SleepTimerManager.formatRemainingTime(sleepRemaining)}"
                                    } else {
                                        stringResource(R.string.Audiobooks_Sleep)
                                    }
                                )
                            }
                            DropdownMenu(
                                expanded = sleepMenuOpen,
                                onDismissRequest = { sleepMenuOpen = false }
                            ) {
                                SleepTimerManager.TimerDuration.entries.forEach { duration ->
                                    DropdownMenuItem(
                                        text = { Text(duration.displayName) },
                                        onClick = {
                                            SleepTimerManager.startTimer(duration)
                                            sleepMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    if (book.hasStarted) {
                        FilledTonalButton(
                            onClick = { viewModel.restart(book, mediaController) },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(stringResource(R.string.Audiobooks_Restart))
                        }
                    }
                }
            }

            item("chapters-title") {
                Text(
                    stringResource(R.string.Audiobooks_Parts_Chapters),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                )
            }

            book.parts.forEachIndexed { partIndex, part ->
                item("part-${part.mediaId}") {
                    AudiobookPartRow(
                        part = part,
                        partIndex = partIndex,
                        partCount = book.parts.size,
                        isPlaying = playback.mediaId == part.mediaId && playback.isPlaying,
                        isDownloaded = book.isPartDownloaded(part),
                        onPlay = { viewModel.playPart(book, partIndex, 0L, mediaController) },
                        onAddNext = {
                            SongHelper.addToQueueTop(part, mediaController)
                        },
                        onAddBottom = {
                            SongHelper.addToQueueBottom(part, mediaController)
                        },
                        onDownload = { viewModel.downloadPart(book, partIndex) }
                    )
                }
                val chapters = chapterSets[partIndex]
                if (chapters.isEmpty() && book.parts.size == 1) {
                    item("no-chapters-${part.mediaId}") {
                        Text(
                            stringResource(R.string.Audiobooks_No_Chapters),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 52.dp, end = 20.dp, bottom = 12.dp)
                        )
                    }
                } else {
                    chapters.forEach { chapter ->
                        item("chapter-${part.mediaId}-${chapter.id}") {
                            AudiobookChapterRow(
                                chapter = chapter,
                                isCurrent = playback.mediaId == part.mediaId &&
                                    playback.positionMs in chapter.startTimeMs until chapter.endTimeMs,
                                onClick = {
                                    viewModel.playPart(
                                        book,
                                        partIndex,
                                        chapter.startTimeMs,
                                        mediaController
                                    )
                                }
                            )
                        }
                    }
                }
            }
            item("detail-bottom") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private data class AudiobookPlaybackUiState(
    val mediaId: String? = null,
    val positionMs: Long = 0L,
    val isPlaying: Boolean = false
)

@Composable
private fun rememberAudiobookPlayback(mediaController: MediaController?): AudiobookPlaybackUiState {
    var mediaId by remember { mutableStateOf(mediaController?.currentMediaItem?.mediaId) }
    var position by remember { mutableLongStateOf(mediaController?.currentPosition ?: 0L) }
    var isPlaying by remember { mutableStateOf(mediaController?.isPlaying == true) }

    DisposableEffect(mediaController) {
        if (mediaController == null) return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaId = mediaItem?.mediaId
                position = mediaController.currentPosition
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        mediaController.addListener(listener)
        onDispose { mediaController.removeListener(listener) }
    }
    LaunchedEffect(mediaController, isPlaying, mediaId) {
        while (isActive) {
            position = mediaController?.currentPosition ?: 0L
            delay(if (isPlaying) 500L else 1_500L)
        }
    }
    return AudiobookPlaybackUiState(mediaId, position, isPlaying)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AudiobookPartRow(
    part: MediaItem,
    partIndex: Int,
    partCount: Int,
    isPlaying: Boolean,
    isDownloaded: Boolean,
    onPlay: () -> Unit,
    onAddNext: suspend () -> Unit,
    onAddBottom: suspend () -> Unit,
    onDownload: () -> Unit
) {
    var menuOpen by remember(part.mediaId) { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onPlay, onLongClick = { menuOpen = true })
                .padding(horizontal = 20.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isPlaying) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (partIndex + 1).toString(),
                    fontSize = 12.sp,
                    color = if (isPlaying) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = part.mediaMetadata.title?.toString().orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (partCount > 1) {
                        buildString {
                            append("Part ${partIndex + 1} · ${formatAudiobookTime(part.mediaMetadata.durationMs ?: 0L)}")
                            if (isDownloaded) append(" · Downloaded")
                        }
                    } else {
                        buildString {
                            append(formatAudiobookTime(part.mediaMetadata.durationMs ?: 0L))
                            if (isDownloaded) append(" · Downloaded")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Rounded.KeyboardArrowRight, contentDescription = null)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Play") },
                onClick = { menuOpen = false; onPlay() }
            )
            DropdownMenuItem(
                text = { Text("Add Next") },
                onClick = {
                    menuOpen = false
                    coroutineScope.launch { onAddNext() }
                }
            )
            DropdownMenuItem(
                text = { Text("Add to End") },
                onClick = {
                    menuOpen = false
                    coroutineScope.launch { onAddBottom() }
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.Audiobooks_Download_Part)) },
                onClick = { menuOpen = false; onDownload() }
            )
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 60.dp))
}

@Composable
private fun AudiobookChapterRow(
    chapter: AudiobookChapter,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onClick)
            .padding(start = 60.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                chapter.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                formatAudiobookTime((chapter.endTimeMs - chapter.startTimeMs).coerceAtLeast(0L)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isCurrent) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = "Playing", tint = MaterialTheme.colorScheme.primary)
        }
    }
}
