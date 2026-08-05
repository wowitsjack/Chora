package com.craftworks.music.ui.ipod

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import com.craftworks.music.data.model.MediaData

@Composable
internal fun IpodSongsScreen(
    songs: List<MediaItem>,
    currentMediaId: String?,
    onSongClick: (MediaItem) -> Unit,
    onSongLongClick: (MediaItem) -> Unit
) {
    if (songs.isEmpty()) {
        IpodMessage("No Songs", "Sync a music source, then return here.")
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(IpodColors.Content)
    ) {
        itemsIndexed(
            items = songs,
            key = { index, song -> "${song.mediaId}-$index" }
        ) { _, song ->
            IpodSongRow(
                song = song,
                isCurrent = song.mediaId == currentMediaId,
                onClick = { onSongClick(song) },
                onLongClick = { onSongLongClick(song) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun IpodArtistsScreen(
    artists: List<MediaData.Artist>,
    onArtistClick: (MediaData.Artist) -> Unit
) {
    if (artists.isEmpty()) {
        IpodMessage("No Artists", "Artist names appear after your library syncs.")
        return
    }
    val groups = remember(artists) {
        artists.groupBy { itemIndexLabel(it.name) }.toSortedMap()
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(IpodColors.Content)
    ) {
        groups.forEach { (letter, group) ->
            stickyHeader(key = "artist-$letter") {
                IpodSectionHeader(letter)
            }
            itemsIndexed(
                items = group,
                key = { index, artist -> "${artist.navidromeID}-$index" }
            ) { _, artist ->
                IpodListRow(
                    title = artist.name,
                    subtitle = artist.albumCount?.let { "$it albums" },
                    showChevron = true,
                    onClick = { onArtistClick(artist) }
                )
            }
        }
    }
}

@Composable
internal fun IpodPlaylistsScreen(
    playlists: List<MediaItem>,
    loading: Boolean,
    onPlaylistClick: (MediaItem) -> Unit
) {
    if (playlists.isEmpty()) {
        IpodMessage(
            title = if (loading) "Loading Playlists…" else "No Playlists",
            detail = if (loading) "Reading your library." else "Create a playlist in Chora or Navidrome."
        )
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(IpodColors.Content)
    ) {
        itemsIndexed(
            items = playlists,
            key = { index, playlist -> "${playlist.mediaId}-$index" }
        ) { _, playlist ->
            val count = playlist.mediaMetadata.extras?.getInt("songCount")
            IpodListRow(
                title = playlist.mediaMetadata.title?.toString() ?: "Untitled Playlist",
                subtitle = count?.takeIf { it > 0 }?.let { "$it songs" },
                showChevron = true,
                onClick = { onPlaylistClick(playlist) }
            )
        }
    }
}

@Composable
internal fun IpodAlbumsScreen(
    albums: List<MediaItem>,
    onAlbumClick: (MediaItem) -> Unit
) {
    if (albums.isEmpty()) {
        IpodMessage("No Albums", "Album covers appear after your library syncs.")
        return
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(IpodColors.Content)
    ) {
        val columns = if (maxWidth >= 600.dp) 4 else 2
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(6.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(
                items = albums,
                key = { index, album -> "${album.mediaId}-$index" }
            ) { _, album ->
                IpodAlbumCell(album = album, onClick = { onAlbumClick(album) })
            }
        }
    }
}

@Composable
private fun IpodAlbumCell(album: MediaItem, onClick: () -> Unit) {
    val title = album.mediaMetadata.albumTitle?.toString()
        ?: album.mediaMetadata.title?.toString()
        ?: "Unknown Album"
    val artist = album.mediaMetadata.albumArtist?.toString()
        ?: album.mediaMetadata.artist?.toString()
        ?: "Unknown Artist"
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    Column(
        modifier = Modifier
            .padding(5.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .background(if (pressed) IpodColors.SelectedBlue.copy(alpha = 0.18f) else Color.Transparent)
            .padding(3.dp)
    ) {
        IpodArtwork(
            artwork = album.mediaMetadata.artworkUri,
            title = title,
            artist = artist,
            identity = album.mediaMetadata.extras?.getString("navidromeID") ?: album.mediaId,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(3.dp))
        )
        Text(
            text = title,
            color = IpodColors.Text,
            fontSize = 13.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            text = artist,
            color = IpodColors.SecondaryText,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            letterSpacing = 0.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun IpodAlbumDetail(
    album: MediaItem,
    songs: List<MediaItem>,
    loading: Boolean,
    currentMediaId: String?,
    onPlayAll: () -> Unit,
    onSongClick: (MediaItem) -> Unit,
    onSongLongClick: (MediaItem) -> Unit
) {
    val title = album.mediaMetadata.albumTitle?.toString()
        ?: album.mediaMetadata.title?.toString()
        ?: "Unknown Album"
    val artist = album.mediaMetadata.albumArtist?.toString()
        ?: album.mediaMetadata.artist?.toString()
        ?: "Unknown Artist"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(IpodColors.Content)
    ) {
        item(key = "album-header") {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                IpodArtwork(
                    artwork = album.mediaMetadata.artworkUri,
                    title = title,
                    artist = artist,
                    identity = album.mediaMetadata.extras?.getString("navidromeID") ?: album.mediaId,
                    modifier = Modifier
                        .size(104.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp)
                ) {
                    Text(
                        text = title,
                        color = IpodColors.Text,
                        fontSize = 16.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = artist,
                        color = IpodColors.Blue,
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    album.mediaMetadata.recordingYear?.takeIf { it > 0 }?.let { year ->
                        Text(
                            text = year.toString(),
                            color = IpodColors.SecondaryText,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.height(7.dp))
                    IpodActionButton(
                        label = "Play All",
                        enabled = songs.isNotEmpty(),
                        onClick = onPlayAll
                    )
                }
            }
            IpodSectionHeader(if (loading && songs.isEmpty()) "Loading…" else "Songs")
        }
        itemsIndexed(
            items = songs,
            key = { index, song -> "${song.mediaId}-$index" }
        ) { index, song ->
            IpodSongRow(
                song = song,
                trackNumber = song.mediaMetadata.trackNumber?.takeIf { it > 0 } ?: index + 1,
                isCurrent = song.mediaId == currentMediaId,
                onClick = { onSongClick(song) },
                onLongClick = { onSongLongClick(song) }
            )
        }
        if (!loading && songs.isEmpty()) {
            item { IpodInlineMessage("No songs are available for this album.") }
        }
    }
}

@Composable
internal fun IpodArtistDetail(
    artist: MediaData.Artist,
    albums: List<MediaItem>,
    loading: Boolean,
    onAlbumClick: (MediaItem) -> Unit
) {
    if (albums.isEmpty()) {
        IpodMessage(
            title = if (loading) "Loading ${artist.name}…" else "No Albums",
            detail = if (loading) "Reading your library." else "No albums are available for this artist."
        )
    } else {
        IpodAlbumsScreen(albums = albums, onAlbumClick = onAlbumClick)
    }
}

@Composable
internal fun IpodPlaylistDetail(
    songs: List<MediaItem>,
    loading: Boolean,
    currentMediaId: String?,
    onPlayAll: () -> Unit,
    onSongClick: (MediaItem) -> Unit,
    onSongLongClick: (MediaItem) -> Unit
) {
    if (songs.isEmpty()) {
        IpodMessage(
            title = if (loading) "Loading Playlist…" else "Empty Playlist",
            detail = if (loading) "Reading its songs." else "This playlist does not contain any songs."
        )
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(IpodColors.Content)
    ) {
        item {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                IpodActionButton(label = "Play All", onClick = onPlayAll)
            }
        }
        itemsIndexed(
            items = songs,
            key = { index, song -> "${song.mediaId}-$index" }
        ) { index, song ->
            IpodSongRow(
                song = song,
                trackNumber = index + 1,
                isCurrent = song.mediaId == currentMediaId,
                onClick = { onSongClick(song) },
                onLongClick = { onSongLongClick(song) }
            )
        }
    }
}

@Composable
internal fun IpodQueueScreen(
    songs: List<MediaItem>,
    currentIndex: Int,
    onSongClick: (Int) -> Unit,
    onSongLongClick: (MediaItem) -> Unit
) {
    if (songs.isEmpty()) {
        IpodMessage("Queue Empty", "Play a song or add music to the queue.")
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(IpodColors.Content)
    ) {
        itemsIndexed(
            items = songs,
            key = { index, song -> "queue-${song.mediaId}-$index" }
        ) { index, song ->
            IpodSongRow(
                song = song,
                trackNumber = index + 1,
                isCurrent = index == currentIndex,
                onClick = { onSongClick(index) },
                onLongClick = { onSongLongClick(song) }
            )
        }
    }
}

@Composable
internal fun IpodRadioScreen(
    stations: List<MediaItem>,
    loading: Boolean,
    onStationClick: (MediaItem) -> Unit
) {
    if (stations.isEmpty()) {
        IpodMessage(
            title = if (loading) "Loading Radio…" else "No Radio Stations",
            detail = if (loading) "Checking your configured providers." else "Add a station from Chora settings."
        )
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(IpodColors.Content)
    ) {
        itemsIndexed(
            items = stations,
            key = { index, station -> "radio-${station.mediaId}-$index" }
        ) { _, station ->
            IpodListRow(
                title = station.mediaMetadata.station?.toString()
                    ?: station.mediaMetadata.artist?.toString()
                    ?: "Radio",
                subtitle = "Internet Radio",
                onClick = { onStationClick(station) }
            )
        }
    }
}

@Composable
internal fun IpodMoreScreen(
    onQueue: () -> Unit,
    onRadio: () -> Unit,
    onDownloads: () -> Unit,
    onSettings: () -> Unit,
    onReturnToChora: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(IpodColors.Content)
    ) {
        item { IpodSectionHeader("Library") }
        item { IpodListRow("Queue", "See what plays next", true, onQueue) }
        item { IpodListRow("Radio", "Internet radio stations", true, onRadio) }
        item { IpodSectionHeader("Chora") }
        item { IpodListRow("Downloads", "Manage offline music", true, onDownloads) }
        item { IpodListRow("Settings", "Providers, playback, and appearance", true, onSettings) }
        item { IpodListRow("Return to Chora", "Use the standard interface", true, onReturnToChora) }
    }
}

@Composable
internal fun IpodSongRow(
    song: MediaItem,
    trackNumber: Int? = null,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val title = song.mediaMetadata.title?.toString()
        ?: song.mediaMetadata.station?.toString()
        ?: "Unknown Song"
    val artist = song.mediaMetadata.artist?.toString().orEmpty()
    val album = song.mediaMetadata.albumTitle?.toString().orEmpty()
    val subtitle = listOf(artist, album).filter { it.isNotBlank() }.joinToString(" – ")
    IpodListRow(
        title = title,
        subtitle = subtitle.takeIf { it.isNotBlank() },
        prefix = trackNumber?.toString(),
        isCurrent = isCurrent,
        onClick = onClick,
        onLongClick = onLongClick
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun IpodListRow(
    title: String,
    subtitle: String? = null,
    showChevron: Boolean = false,
    onClick: () -> Unit,
    prefix: String? = null,
    isCurrent: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val background = if (pressed) IpodColors.SelectedBlue else IpodColors.Content
    val primary = when {
        pressed -> Color.White
        isCurrent -> IpodColors.Blue
        else -> IpodColors.Text
    }
    val secondary = if (pressed) Color.White.copy(alpha = 0.85f) else IpodColors.SecondaryText

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (subtitle == null) IpodDimensions.RowHeight else 52.dp)
            .background(background)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onLongClick = onLongClick,
                onClick = onClick
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 8.dp)
        ) {
            if (prefix != null) {
                Text(
                    text = prefix,
                    color = secondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp,
                    maxLines = 1,
                    modifier = Modifier.width(28.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = primary,
                    fontSize = 18.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = secondary,
                        fontSize = 14.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (isCurrent) {
                Text(
                    text = "▶",
                    color = if (pressed) Color.White else IpodColors.Blue,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(start = 6.dp)
                )
            } else if (showChevron) {
                Text(
                    text = "›",
                    color = if (pressed) Color.White else Color(0xFF8C8C8C),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .fillMaxWidth(if (prefix == null) 0.97f else 0.90f)
                .height(IpodDimensions.SeparatorHeight)
                .background(if (pressed) Color.White.copy(alpha = 0.35f) else IpodColors.Separator)
        )
    }
}

@Composable
internal fun IpodSectionHeader(title: String) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFB1BAC3), Color(0xFF949FA8))
                )
            )
            .padding(horizontal = 11.dp)
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 18.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
internal fun IpodActionButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(5.dp)
    val top = when {
        !enabled -> Color(0xFFD3D3D3)
        pressed -> Color(0xFF5B91C4)
        else -> Color(0xFF8AB9E4)
    }
    val bottom = when {
        !enabled -> Color(0xFFAFAFAF)
        pressed -> Color(0xFF1E5689)
        else -> Color(0xFF3978B1)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(30.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = if (enabled) 1f else 0.65f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun IpodInlineMessage(text: String) {
    Text(
        text = text,
        color = IpodColors.SecondaryText,
        fontSize = 13.sp,
        modifier = Modifier.padding(20.dp)
    )
}

private fun itemIndexLabel(value: String): String {
    val first = value.trim().firstOrNull()?.uppercaseChar() ?: return "#"
    return if (first in 'A'..'Z') first.toString() else "#"
}
