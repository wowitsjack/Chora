package com.craftworks.music.ui.elements

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import com.craftworks.music.R
import com.craftworks.music.data.model.MediaData
import com.craftworks.music.data.model.favoritesPlaylistMediaItem
import com.craftworks.music.data.model.albumList
import com.craftworks.music.data.model.songsList
import com.craftworks.music.data.model.toAlbum
import com.craftworks.music.managers.NavidromeManager
import com.craftworks.music.managers.settings.AppearanceSettingsManager
import com.craftworks.music.player.SongHelper
import com.craftworks.music.ui.viewmodels.AlbumScreenViewModel
import com.craftworks.music.ui.viewmodels.SongsScreenViewModel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import androidx.compose.material3.MaterialTheme as M3Theme

//region Songs
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongsHorizontalColumn(
    songList: List<MediaItem>,
    onSongSelected: (itemsList: List<MediaItem>, index: Int) -> Unit,
    isSearch: Boolean? = false,
    viewModel: SongsScreenViewModel? = null,
    useMultiColumn: Boolean = false,
    enableSelection: Boolean = true,
    onDownloadSelected: ((List<MediaItem>) -> Unit)? = null,
    onDownload: ((MediaItem) -> Unit)? = null,
    onInstantMix: (suspend (MediaItem) -> List<MediaItem>)? = null,
    // Performance: Accept offline song IDs set from parent
    offlineSongIds: Set<String> = emptySet(),
    showEmptyState: Boolean = true
){
    if (songList.isEmpty()) {
        if (showEmptyState) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.Songs_Empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
        return
    }

    // Selection state
    var isInSelectionMode by rememberSaveable { mutableStateOf(false) }
    val selectedSongIds = remember { mutableStateListOf<String>() }

    fun exitSelectionMode() {
        isInSelectionMode = false
        selectedSongIds.clear()
    }

    // Read settings ONCE at parent level, not per-item
    val context = LocalContext.current
    val appearanceSettings = remember { AppearanceSettingsManager(context) }
    val showDividers by appearanceSettings.showProviderDividersFlow.collectAsStateWithLifecycle(true)
    val stripTrackNumbers by appearanceSettings.stripTrackNumbersFromTitlesFlow.collectAsStateWithLifecycle(false)

    val artworkSettings = remember { com.craftworks.music.managers.settings.ArtworkSettingsManager(context) }
    val generatedArtworkEnabled by artworkSettings.generatedArtworkEnabledFlow.collectAsStateWithLifecycle(true)
    val fallbackMode by artworkSettings.fallbackModeFlow.collectAsStateWithLifecycle(com.craftworks.music.managers.settings.ArtworkSettingsManager.FallbackMode.PLACEHOLDER_DETECT)
    val artworkStyle by artworkSettings.artworkStyleFlow.collectAsStateWithLifecycle(com.craftworks.music.managers.settings.ArtworkSettingsManager.ArtworkStyle.GRADIENT)
    val colorPalette by artworkSettings.colorPaletteFlow.collectAsStateWithLifecycle(com.craftworks.music.managers.settings.ArtworkSettingsManager.ColorPalette.MATERIAL_YOU)
    val showInitials by artworkSettings.showInitialsFlow.collectAsStateWithLifecycle(true)

    // Group songs by their source (Local or Navidrome)
    val groupedSongs = remember(songList) {
        songList.groupBy { song ->
            if (song.mediaMetadata.extras?.getString("navidromeID")?.startsWith("Local_") == true) "Local" else "Navidrome"
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Selection Action Bar
        AnimatedVisibility(
            visible = isInSelectionMode,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            SelectionActionBar(
                selectedCount = selectedSongIds.size,
                onCancel = { exitSelectionMode() },
                onSelectAll = {
                    selectedSongIds.clear()
                    selectedSongIds.addAll(songList.map { it.mediaId })
                },
                onDownload = {
                    val selected = songList.filter { selectedSongIds.contains(it.mediaId) }
                    onDownloadSelected?.invoke(selected)
                    exitSelectionMode()
                }
            )
        }

        if (useMultiColumn) {
            val gridState = rememberLazyGridState()

            Box(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 28.dp)
                        .drawVerticalScrollbar(gridState, color = M3Theme.colorScheme.onSurface),
                    state = gridState,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groupedSongs.forEach { (groupName, songsInGroup) ->
                        if (showDividers && groupedSongs.size > 1) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Column {
                                    HorizontalDivider(
                                        modifier = Modifier
                                            .height(1.dp)
                                            .fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                    )
                                    Text(
                                        text = when (groupName) {
                                            "Navidrome" -> stringResource(R.string.Source_Navidrome)
                                            "Local" -> stringResource(R.string.Source_Local)
                                            else -> ""
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp)
                                    )
                                }
                            }
                        }
                        itemsIndexed(
                            items = songsInGroup,
                            key = { _, song -> song.mediaId }
                        ) { index, song ->
                            val songId = song.mediaMetadata.extras?.getString("navidromeID") ?: song.mediaId
                            HorizontalSongCard(
                                song = song,
                                isInSelectionMode = isInSelectionMode,
                                isSelected = selectedSongIds.contains(song.mediaId),
                                onEnterSelectionMode = if (enableSelection) {
                                    {
                                        isInSelectionMode = true
                                        selectedSongIds.add(song.mediaId)
                                    }
                                } else null,
                                onSelectionChange = { selected ->
                                    if (selected) selectedSongIds.add(song.mediaId)
                                    else selectedSongIds.remove(song.mediaId)
                                },
                                onClick = {
                                    onSongSelected(songsInGroup, index)
                                },
                                onDownload = onDownload,
                                onInstantMix = onInstantMix,
                                // Pass settings from parent for performance
                                isOffline = offlineSongIds.contains(songId),
                                generatedArtworkEnabled = generatedArtworkEnabled,
                                fallbackMode = fallbackMode,
                                stripTrackNumbers = stripTrackNumbers,
                                artworkStyle = artworkStyle,
                                colorPalette = colorPalette,
                                showInitials = showInitials
                            )
                        }
                    }
                }

                AlphabetFastScroller(
                    items = songList,
                    getSectionLetter = { it.mediaMetadata.title?.firstOrNull() ?: '#' },
                    gridState = gridState,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        } else {
            val listState = rememberLazyListState()

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .wrapContentHeight()
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 28.dp)
                        .drawVerticalScrollbar(listState, color = M3Theme.colorScheme.onSurface),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    groupedSongs.forEach { (groupName, songsInGroup) ->
                        if (showDividers && groupedSongs.size > 1) {
                            item {
                                HorizontalDivider(
                                    modifier = Modifier
                                        .height(1.dp)
                                        .fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                )
                                Text(
                                    text = when (groupName) {
                                        "Navidrome" -> stringResource(R.string.Source_Navidrome)
                                        "Local" -> stringResource(R.string.Source_Local)
                                        else -> ""
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp)
                                )
                            }
                        }
                        itemsIndexed(
                            items = songsInGroup,
                            key = { _, song -> song.mediaId }
                        ) { index, song ->
                            val songId = song.mediaMetadata.extras?.getString("navidromeID") ?: song.mediaId
                            HorizontalSongCard(
                                song = song,
                                isInSelectionMode = isInSelectionMode,
                                isSelected = selectedSongIds.contains(song.mediaId),
                                onEnterSelectionMode = if (enableSelection) {
                                    {
                                        isInSelectionMode = true
                                        selectedSongIds.add(song.mediaId)
                                    }
                                } else null,
                                onSelectionChange = { selected ->
                                    if (selected) selectedSongIds.add(song.mediaId)
                                    else selectedSongIds.remove(song.mediaId)
                                },
                                onClick = {
                                    onSongSelected(songsInGroup, index)
                                },
                                onDownload = onDownload,
                                onInstantMix = onInstantMix,
                                // Pass settings from parent for performance
                                isOffline = offlineSongIds.contains(songId),
                                generatedArtworkEnabled = generatedArtworkEnabled,
                                fallbackMode = fallbackMode,
                                stripTrackNumbers = stripTrackNumbers,
                                artworkStyle = artworkStyle,
                                colorPalette = colorPalette,
                                showInitials = showInitials
                            )
                        }
                    }
                }

                AlphabetFastScrollerList(
                    items = songList,
                    getSectionLetter = { it.mediaMetadata.title?.firstOrNull() ?: '#' },
                    listState = listState,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
    }
}

@Composable
fun SelectionActionBar(
    selectedCount: Int,
    onCancel: () -> Unit,
    onSelectAll: () -> Unit,
    onDownload: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onCancel) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Cancel selection",
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Text(
            text = "$selectedCount selected",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Row {
            TextButton(onClick = onSelectAll) {
                Text(
                    "All",
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            IconButton(
                onClick = onDownload,
                enabled = selectedCount > 0
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.rounded_download_24),
                    contentDescription = "Download selected",
                    tint = if (selectedCount > 0)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.38f)
                )
            }
        }
    }
}
//endregion

//region Albums
@ExperimentalFoundationApi
@Composable
fun AlbumGrid(
    albums: List<MediaItem>,
    mediaController: MediaController?,
    onAlbumSelected: (album: MediaData.Album) -> Unit,
    isSearch: Boolean? = false,
    viewModel: AlbumScreenViewModel = viewModel(),
    gridColumns: Int = 0,
    onStartRadio: ((MediaItem) -> Unit)? = null,
    showAlphabetScroller: Boolean = true,
    showEmptyState: Boolean = true
){
    if (albums.isEmpty()) {
        if (showEmptyState) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.Albums_Empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
        return
    }

    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()

    val showDividers by AppearanceSettingsManager(LocalContext.current).showProviderDividersFlow.collectAsStateWithLifecycle(true)

    // Group songs by their source (Local or Navidrome)
    val groupedAlbums = albums.groupBy { song ->
        if (song.mediaMetadata.extras?.getString("navidromeID")?.startsWith("Local_") == true) "Local" else "Navidrome"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = if (gridColumns > 0) GridCells.Fixed(gridColumns) else GridCells.Adaptive(128.dp),
            modifier = Modifier
                .wrapContentWidth()
                .fillMaxHeight()
                .padding(end = if (showAlphabetScroller) 28.dp else 0.dp)
                .drawVerticalScrollbar(gridState, color = M3Theme.colorScheme.onSurface),
            state = gridState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (showDividers && groupedAlbums.size > 1 && !showAlphabetScroller) {
                groupedAlbums.forEach { (groupName, albumsInGroup) ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column (Modifier.padding(start = 12.dp)) {
                            HorizontalDivider(
                                modifier = Modifier
                                    .height(1.dp)
                                    .fillMaxWidth(),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                            )
                            Text(
                                text = when (groupName) {
                                    "Navidrome" -> stringResource(R.string.Source_Navidrome)
                                    "Local" -> stringResource(R.string.Source_Local)
                                    else -> ""
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                            )
                        }

                    }
                    itemsIndexed(
                        items = albumsInGroup,
                        key = { _, album -> album.mediaId }
                    ) { index, album ->
                        AlbumCard(album = album,
                            onClick = {
                                onAlbumSelected(album.toAlbum())
                            },
                            onPlay = {
                                coroutineScope.launch {
                                    val mediaItems = viewModel.getAlbum(album.mediaMetadata.extras?.getString("navidromeID") ?: album.mediaId)
                                    if (mediaItems.size > 1)
                                        SongHelper.play(
                                            mediaItems = mediaItems.subList(1, mediaItems.size),
                                            index = 0,
                                            mediaController = mediaController
                                        )
                                }
                            },
                            onStartRadio = onStartRadio
                        )
                    }
                }
            }
            else {
                items(
                    items = albums,
                    key = { it.mediaId }
                ) { album ->
                    AlbumCard(album = album,
                        onClick = {
                            onAlbumSelected(album.toAlbum())
                        },
                        onPlay = {
                            coroutineScope.launch {
                                val mediaItems = viewModel.getAlbum(album.mediaMetadata.extras?.getString("navidromeID") ?: album.mediaId)
                                if (mediaItems.size > 1)
                                    SongHelper.play(
                                        mediaItems = mediaItems.subList(1, mediaItems.size),
                                        index = 0,
                                        mediaController = mediaController
                                    )
                            }
                        },
                        onStartRadio = onStartRadio
                    )
                }
            }
        }

        if (showAlphabetScroller) {
            AlphabetFastScroller(
                items = albums,
                getSectionLetter = {
                    (it.mediaMetadata.albumTitle ?: it.mediaMetadata.title)
                        ?.firstOrNull() ?: '#'
                },
                gridState = gridState,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@ExperimentalFoundationApi
@Composable
fun AlbumGrid(
    albums: List<MediaItem>,
    mediaController: MediaController?,
    onAlbumSelected: (album: MediaData.Album) -> Unit,
    onGetAlbum: suspend (albumID: String) -> List<MediaItem>,
    gridColumns: Int = 0,
    onStartRadio: ((MediaItem) -> Unit)? = null,
    showAlphabetScroller: Boolean = true,
    showEmptyState: Boolean = true
) {
    if (albums.isEmpty()) {
        if (showEmptyState) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.Albums_Empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
        return
    }

    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()

    val showDividers by AppearanceSettingsManager(LocalContext.current).showProviderDividersFlow.collectAsStateWithLifecycle(true)

    // Group songs by their source (Local or Navidrome)
    val groupedAlbums = albums.groupBy { song ->
        if (song.mediaMetadata.extras?.getString("navidromeID")?.startsWith("Local_") == true) "Local" else "Navidrome"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = if (gridColumns > 0) GridCells.Fixed(gridColumns) else GridCells.Adaptive(128.dp),
            modifier = Modifier
                .wrapContentWidth()
                .fillMaxHeight()
                .padding(end = if (showAlphabetScroller) 28.dp else 0.dp)
                .drawVerticalScrollbar(gridState, color = M3Theme.colorScheme.onSurface),
            state = gridState
        ) {
            if (showDividers && groupedAlbums.size > 1 && !showAlphabetScroller) {
                groupedAlbums.forEach { (groupName, albumsInGroup) ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column (Modifier.padding(start = 12.dp)) {
                            HorizontalDivider(
                                modifier = Modifier
                                    .height(1.dp)
                                    .fillMaxWidth(),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                            )
                            Text(
                                text = when (groupName) {
                                    "Navidrome" -> stringResource(R.string.Source_Navidrome)
                                    "Local" -> stringResource(R.string.Source_Local)
                                    else -> ""
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                            )
                        }

                    }
                    itemsIndexed(
                        items = albumsInGroup,
                        key = { _, album -> album.mediaId }
                    ) { index, album ->
                        AlbumCard(album = album,
                            onClick = {
                                onAlbumSelected(album.toAlbum())
                            },
                            onPlay = {
                                coroutineScope.launch {
                                    val mediaItems = onGetAlbum(album.mediaMetadata.extras?.getString("navidromeID") ?: album.mediaId)
                                    if (mediaItems.size > 1)
                                        SongHelper.play(
                                            mediaItems = mediaItems.subList(1, mediaItems.size),
                                            index = 0,
                                            mediaController = mediaController
                                        )
                                }
                            },
                            onStartRadio = onStartRadio
                        )
                    }
                }
            }
            else {
                items(
                    items = albums,
                    key = { it.mediaId }
                ) { album ->
                    AlbumCard(album = album,
                        onClick = {
                            onAlbumSelected(album.toAlbum())
                        },
                        onPlay = {
                            coroutineScope.launch {
                                val mediaItems = onGetAlbum(album.mediaMetadata.extras?.getString("navidromeID") ?: album.mediaId)
                                if (mediaItems.size > 1)
                                    SongHelper.play(
                                        mediaItems = mediaItems.subList(1, mediaItems.size),
                                        index = 0,
                                        mediaController = mediaController
                                    )
                            }
                        },
                        onStartRadio = onStartRadio
                    )
                }
            }
        }

        if (showAlphabetScroller) {
            AlphabetFastScroller(
                items = albums,
                getSectionLetter = {
                    (it.mediaMetadata.albumTitle ?: it.mediaMetadata.title)
                        ?.firstOrNull() ?: '#'
                },
                gridState = gridState,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@ExperimentalFoundationApi
@Composable
fun AlbumRow(
    albums: List<MediaItem>,
    onAlbumSelected: (album: MediaData.Album) -> Unit,
    onPlay: (album: MediaItem) -> Unit,
    onStartRadio: ((MediaItem) -> Unit)? = null,
    cardWidth: Int = 128
){
    val cardHeight = (cardWidth * 1.34f).toInt()

    if (albums.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = cardHeight.dp)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = stringResource(R.string.Home_Coming_Soon),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        return
    }

    val showProviderDividers by AppearanceSettingsManager(LocalContext.current).showProviderDividersFlow.collectAsStateWithLifecycle(true)
    val dividerIndex = albums.indexOfFirst { it.mediaMetadata.extras?.getString("navidromeID")?.startsWith("Local_") == true }

    LazyRow(
        modifier = Modifier.fillMaxSize().heightIn(min = cardHeight.dp),
        contentPadding = PaddingValues(
            end = 12.dp
        )
    ) {
        itemsIndexed(
            items = albums,
            key = { _, album -> album.mediaId }
        ) { index, album ->
            // Show divider between local and navidrome albums
            if (showProviderDividers) {
                if (index == dividerIndex && index != albums.lastIndex && index != 0) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        VerticalDivider(
                            modifier = Modifier
                                .height(cardHeight.dp)
                                .width(1.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                        )
                        Text(
                            text = stringResource(R.string.Source_Local),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier
                                .rotateVertically(),
                        )
                    }
                }
            }

            AlbumCard(
                album = album,
                onClick = {
                    onAlbumSelected(album.toAlbum())
                },
                onPlay = {
                    onPlay(album)
                },
                onStartRadio = onStartRadio,
                modifier = Modifier.animateItem(),
                cardWidth = cardWidth
            )
        }
    }
}
//endregion

//region Artists
@ExperimentalFoundationApi
@Composable
fun ArtistsGrid(
    artists: List<MediaData.Artist>,
    onArtistSelected: (artist: MediaData.Artist) -> Unit,
    onStartRadio: ((MediaData.Artist) -> Unit)? = null,
    showAlphabetScroller: Boolean = true,
    showEmptyState: Boolean = true
){
    if (artists.isEmpty()) {
        if (showEmptyState) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.Artists_Empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
        return
    }

    val gridState = rememberLazyGridState()
    val showProviderDividers by AppearanceSettingsManager(LocalContext.current).showProviderDividersFlow.collectAsStateWithLifecycle(true)

    val groupedArtists = artists.groupBy { artist ->
        if (artist.navidromeID.startsWith("Local_")) "Local" else "Navidrome"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(128.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(end = if (showAlphabetScroller) 28.dp else 0.dp),
            state = gridState
        ) {
            if (showProviderDividers && groupedArtists.size > 1 && !showAlphabetScroller) {
                groupedArtists.forEach { (groupName, artistsInGroup) ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(Modifier.padding(start = 12.dp)) {
                            HorizontalDivider(
                                modifier = Modifier
                                    .height(1.dp)
                                    .fillMaxWidth(),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                            )
                            Text(
                                text = when (groupName) {
                                    "Navidrome" -> stringResource(R.string.Source_Navidrome)
                                    "Local" -> stringResource(R.string.Source_Local)
                                    else -> ""
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                            )
                        }

                    }
                    itemsIndexed(
                        items = artistsInGroup,
                        key = { _, artist -> artist.navidromeID }
                    ) { index, artist ->
                        ArtistCard(
                            artist = artist,
                            onClick = { onArtistSelected(artist) },
                            onStartRadio = onStartRadio
                        )
                    }
                }
            } else {
                items(
                    items = artists,
                    key = { it.navidromeID }
                ) { artist ->
                    ArtistCard(
                        artist = artist,
                        onClick = { onArtistSelected(artist) },
                        onStartRadio = onStartRadio
                    )
            }
        }
        }

        if (showAlphabetScroller) {
            AlphabetFastScroller(
                items = artists,
                getSectionLetter = { artist ->
                    val name = artist.name
                    when {
                        name.isBlank() || name.contains("unknown", ignoreCase = true) -> '?'
                        name.firstOrNull()?.isLetter() == true -> name.first().uppercaseChar()
                        else -> '#'
                    }
                },
                gridState = gridState,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}
//endregion

//region Playlists
@ExperimentalFoundationApi
@Composable
fun PlaylistGrid(playlists: List<MediaItem>, onPlaylistSelected: (playlist: MediaItem) -> Unit, onDeletePlaylist: (String) -> Unit = {}){
    val gridState = rememberLazyGridState()

    val allPlaylists = listOf(favoritesPlaylistMediaItem()) + playlists.filterNot {
        it.mediaMetadata.extras?.getString("navidromeID") == "favourites"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(128.dp),
            modifier = Modifier
                .wrapContentWidth()
                .fillMaxHeight()
                .padding(end = 28.dp)
                .drawVerticalScrollbar(gridState, color = M3Theme.colorScheme.onSurface),
            state = gridState
        ) {
            items(
                items = allPlaylists,
                key = { it.mediaId }
            ) { playlist ->
                PlaylistCard(playlist = playlist,
                    onClick = {
                        onPlaylistSelected(playlist)
                        Log.d("PLAYLISTS", "CLICKED PLAYLIST!")
                    },
                    onDeletePlaylist = onDeletePlaylist)
            }
        }

        // iPod-style A-Z fast scroller
        AlphabetFastScroller(
            items = allPlaylists,
            getSectionLetter = { it.mediaMetadata.title?.firstOrNull() ?: '#' },
            gridState = gridState,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}
//endregion
