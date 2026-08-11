package com.craftworks.music.ui.ipod

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.craftworks.music.data.model.Screen
import com.craftworks.music.data.model.DiscoveryMixMode
import com.craftworks.music.data.model.MediaData
import com.craftworks.music.data.model.isFavorite
import com.craftworks.music.player.SongHelper
import com.craftworks.music.player.AudiobookPlaybackHelper
import com.craftworks.music.ui.playing.StemMixerDialog
import com.craftworks.music.ui.playing.stemMixerSongId
import com.craftworks.music.ui.viewmodels.AlbumScreenViewModel
import com.craftworks.music.ui.viewmodels.ArtistsScreenViewModel
import com.craftworks.music.ui.viewmodels.DownloadViewModel
import com.craftworks.music.ui.viewmodels.PlaylistScreenViewModel
import com.craftworks.music.ui.viewmodels.RadioScreenViewModel
import com.craftworks.music.ui.viewmodels.SongActionsViewModel
import com.craftworks.music.ui.viewmodels.SongsScreenViewModel
import com.craftworks.music.ui.viewmodels.AudiobooksViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun IpodTouchApp(
    mediaController: MediaController?,
    onOpenChoraRoute: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTabName by rememberSaveable { mutableStateOf(IpodTab.SONGS.name) }
    val selectedTab = IpodTab.entries.firstOrNull { it.name == selectedTabName } ?: IpodTab.SONGS
    val navigation = remember { IpodNavigationStack<IpodDestination>() }
    val destination = navigation.current
    val screenStateHolder = rememberSaveableStateHolder()
    var nowPlayingOpen by rememberSaveable { mutableStateOf(false) }
    var actionSong by remember { mutableStateOf<MediaItem?>(null) }
    var actionAlbum by remember { mutableStateOf<MediaItem?>(null) }
    var actionArtist by remember { mutableStateOf<MediaData.Artist?>(null) }
    var playlistSong by remember { mutableStateOf<MediaItem?>(null) }
    var stemMixerSong by remember { mutableStateOf<MediaItem?>(null) }
    var radioBuilding by remember { mutableStateOf(false) }
    var discoveryMixBuilding by remember { mutableStateOf<DiscoveryMixMode?>(null) }
    var discoveryMixMode by remember { mutableStateOf<DiscoveryMixMode?>(null) }
    var discoveryMixSongs by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var favoriteOverrides by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    val controllerPlaybackState = rememberIpodPlaybackState(mediaController)
    val actionScope = rememberCoroutineScope()
    val context = LocalContext.current
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    var deviceVolume by remember(audioManager) {
        mutableFloatStateOf(audioManager.musicVolumeFraction())
    }
    val playbackState = controllerPlaybackState.copy(volume = deviceVolume)
    val playlistViewModel: PlaylistScreenViewModel = hiltViewModel()
    val actionPlaylists by playlistViewModel.allPlaylists.collectAsStateWithLifecycle()
    val downloadViewModel: DownloadViewModel = hiltViewModel()
    val songActionsViewModel: SongActionsViewModel = hiltViewModel()
    val albumActionsViewModel: AlbumScreenViewModel = hiltViewModel()
    val artistActionsViewModel: ArtistsScreenViewModel = hiltViewModel()
    val view = LocalView.current

    fun startRadio(label: String, loadSeeds: suspend () -> List<MediaItem>) {
        actionScope.launch {
            radioBuilding = true
            Toast.makeText(context, "Starting $label radio…", Toast.LENGTH_SHORT).show()
            try {
                val mix = songActionsViewModel.buildRadio(loadSeeds())
                if (mix.isNotEmpty()) {
                    SongHelper.play(mix, 0, mediaController)
                    nowPlayingOpen = true
                } else {
                    Toast.makeText(context, "Could not start $label radio", Toast.LENGTH_SHORT).show()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                Toast.makeText(context, "Could not start $label radio", Toast.LENGTH_SHORT).show()
            } finally {
                radioBuilding = false
            }
        }
    }

    DisposableEffect(context, audioManager) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                deviceVolume = audioManager.musicVolumeFraction()
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        if (window == null) return@DisposableEffect onDispose { }
        val previousStatusColor = window.statusBarColor
        val previousNavigationColor = window.navigationBarColor
        val insetsController = WindowCompat.getInsetsController(window, view)
        val previousLightStatus = insetsController.isAppearanceLightStatusBars
        val previousLightNavigation = insetsController.isAppearanceLightNavigationBars

        window.statusBarColor = Color.Black.toArgb()
        window.navigationBarColor = Color.Black.toArgb()
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false

        onDispose {
            window.statusBarColor = previousStatusColor
            window.navigationBarColor = previousNavigationColor
            insetsController.isAppearanceLightStatusBars = previousLightStatus
            insetsController.isAppearanceLightNavigationBars = previousLightNavigation
        }
    }

    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        window.statusBarColor = Color.Black.toArgb()
        window.navigationBarColor = Color.Black.toArgb()
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    BackHandler(enabled = nowPlayingOpen || destination != null) {
        when {
            nowPlayingOpen -> nowPlayingOpen = false
            destination != null -> navigation.pop()
        }
    }

    IpodTypography {
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Box(
                modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .widthIn(max = 600.dp)
            ) {
                if (nowPlayingOpen) {
                    val currentSong = playbackState.currentItem
                    val currentSongId = currentSong?.mediaMetadata?.extras?.getString("navidromeID")
                    val currentIsFavorite = currentSongId?.let { favoriteOverrides[it] }
                        ?: currentSong?.isFavorite()
                        ?: false
                    IpodNowPlaying(
                        state = playbackState,
                        isFavorite = currentIsFavorite,
                        onBack = { nowPlayingOpen = false },
                        onTogglePlay = {
                            actionScope.launch {
                                SongHelper.togglePlayback(mediaController)
                            }
                        },
                        onPrevious = {
                            mediaController?.let { controller ->
                                if (AudiobookPlaybackHelper.isAudiobook(controller)) {
                                    AudiobookPlaybackHelper.previousChapter(controller)
                                } else if (controller.currentPosition > 3_000L) {
                                    controller.seekTo(0L)
                                } else {
                                    controller.seekToPreviousMediaItem()
                                }
                            }
                        },
                        onNext = {
                            if (AudiobookPlaybackHelper.isAudiobook(mediaController)) {
                                AudiobookPlaybackHelper.nextChapter(mediaController)
                            } else {
                                mediaController?.seekToNextMediaItem()
                            }
                        },
                        onSeek = { mediaController?.seekTo(it) },
                        onSeekBack = { AudiobookPlaybackHelper.seekBack(mediaController) },
                        onSeekForward = { AudiobookPlaybackHelper.seekForward(mediaController) },
                        onSpeedChange = { mediaController?.setPlaybackSpeed(it) },
                        onToggleShuffle = {
                            mediaController?.let { controller ->
                                controller.shuffleModeEnabled = !controller.shuffleModeEnabled
                            }
                        },
                        onCycleRepeat = {
                            mediaController?.let { controller ->
                                controller.repeatMode = when (controller.repeatMode) {
                                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                    else -> Player.REPEAT_MODE_OFF
                                }
                            }
                        },
                        onVolumeChange = { requestedVolume ->
                            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            val targetVolume = volumeIndexForFraction(requestedVolume, maxVolume)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
                            deviceVolume = if (maxVolume > 0) {
                                targetVolume.toFloat() / maxVolume.toFloat()
                            } else {
                                0f
                            }
                        },
                        onShowQueue = {
                            nowPlayingOpen = false
                            selectedTabName = IpodTab.MORE.name
                            navigation.clear()
                            navigation.push(IpodDestination.Queue)
                        },
                        onToggleFavorite = {
                            currentSong?.let { song ->
                                val itemId = song.mediaMetadata.extras?.getString("navidromeID")
                                    ?: return@let
                                val desired = !currentIsFavorite
                                favoriteOverrides = favoriteOverrides + (itemId to desired)
                                actionScope.launch {
                                    if (!songActionsViewModel.setFavorite(song, desired)) {
                                        favoriteOverrides = favoriteOverrides + (itemId to currentIsFavorite)
                                        Toast.makeText(
                                            context,
                                            "Could not update Favorites",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        },
                        onOpenStemMixer = {
                            playbackState.currentItem
                                ?.takeIf { stemMixerSongId(it.mediaMetadata) != null }
                                ?.let { stemMixerSong = it }
                        }
                    )
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        IpodNavigationBar(
                            title = destination?.title() ?: selectedTab.label,
                            backLabel = destination?.let {
                                navigation.previous?.title() ?: selectedTab.label
                            },
                            onBack = destination?.let { { navigation.pop() } }
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            if (destination == null) {
                                screenStateHolder.SaveableStateProvider("tab:${selectedTab.name}") {
                                    IpodTabRoot(
                                        selectedTab = selectedTab,
                                        playbackState = playbackState,
                                        mediaController = mediaController,
                                        discoveryMixBuilding = discoveryMixBuilding,
                                        discoveryMixMode = discoveryMixMode,
                                        discoveryMixSongs = discoveryMixSongs,
                                        onBuildDiscoveryMix = { mode ->
                                            actionScope.launch {
                                                discoveryMixBuilding = mode
                                                try {
                                                    val songs = songActionsViewModel.buildDiscoveryMix(mode)
                                                    discoveryMixSongs = songs
                                                    discoveryMixMode = mode
                                                    if (songs.isEmpty()) {
                                                        Toast.makeText(
                                                            context,
                                                            "Could not build ${mode.title}",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                } catch (error: CancellationException) {
                                                    throw error
                                                } catch (_: Exception) {
                                                    Toast.makeText(
                                                        context,
                                                        "Could not build ${mode.title}",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                } finally {
                                                    discoveryMixBuilding = null
                                                }
                                            }
                                        },
                                        onPlayDiscoveryMix = { index ->
                                            actionScope.launch {
                                                SongHelper.play(discoveryMixSongs, index, mediaController)
                                                nowPlayingOpen = true
                                            }
                                        },
                                        onSaveDiscoveryMix = {
                                            discoveryMixMode?.let { mode ->
                                                actionScope.launch {
                                                    val savedName = playlistViewModel.createDiscoveryPlaylist(
                                                        mode.title,
                                                        discoveryMixSongs
                                                    )
                                                    Toast.makeText(
                                                        context,
                                                        savedName?.let { "Saved $it" }
                                                            ?: "Could not save playlist",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        },
                                        onDestination = { navigation.push(it) },
                                        onOpenChoraRoute = onOpenChoraRoute,
                                        onNowPlaying = { nowPlayingOpen = true },
                                        onSongLongClick = { actionSong = it },
                                        onAlbumLongClick = { actionAlbum = it },
                                        onArtistLongClick = { actionArtist = it }
                                    )
                                }
                            } else {
                                val destinationKey = navigation.pathKey(
                                    root = "tab:${selectedTab.name}",
                                    keyOf = IpodDestination::stateKey
                                )
                                screenStateHolder.SaveableStateProvider(destinationKey) {
                                    IpodDestinationContent(
                                        destination = destination,
                                        playbackState = playbackState,
                                        mediaController = mediaController,
                                        onDestination = { navigation.push(it) },
                                        onNowPlaying = { nowPlayingOpen = true },
                                        onSongLongClick = { actionSong = it },
                                        onAlbumLongClick = { actionAlbum = it },
                                        onStartAlbumRadio = { album ->
                                            val albumId = album.mediaMetadata.extras
                                                ?.getString("navidromeID") ?: album.mediaId
                                            startRadio("album") {
                                                albumActionsViewModel.getAlbum(albumId)
                                                    .filter {
                                                        it.mediaMetadata.mediaType != MediaMetadata.MEDIA_TYPE_ALBUM
                                                    }
                                            }
                                        },
                                        onStartArtistRadio = { artist ->
                                            startRadio("artist") {
                                                artistActionsViewModel.getSongsForArtists(listOf(artist))
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        if (playbackState.currentItem != null) {
                            IpodMiniPlayer(
                                state = playbackState,
                                onOpen = { nowPlayingOpen = true },
                                onTogglePlay = {
                                    actionScope.launch {
                                        SongHelper.togglePlayback(mediaController)
                                    }
                                },
                                onNext = { mediaController?.seekToNextMediaItem() }
                            )
                        }

                        IpodTabBar(
                            selectedTab = selectedTab,
                            onSelect = { tab ->
                                selectedTabName = tab.name
                                navigation.clear()
                            }
                        )
                    }
                }
            }

            actionSong?.let { song ->
                val navidromeId = song.mediaMetadata.extras?.getString("navidromeID")
                val remoteSong = navidromeId != null && !navidromeId.startsWith("Local_")
                val isFavorite = navidromeId?.let { favoriteOverrides[it] } ?: song.isFavorite()
                IpodSongActionsDialog(
                    song = song,
                    isFavorite = isFavorite,
                    favoriteEnabled = navidromeId != null,
                    radioEnabled = remoteSong && !radioBuilding,
                    downloadEnabled = remoteSong,
                    stemMixerEnabled = remoteSong,
                    onDismiss = {
                        actionScope.launch {
                            delay(120L)
                            actionSong = null
                        }
                    },
                    onToggleFavorite = {
                        val itemId = navidromeId ?: return@IpodSongActionsDialog
                        val desired = !isFavorite
                        favoriteOverrides = favoriteOverrides + (itemId to desired)
                        actionScope.launch {
                            if (!songActionsViewModel.setFavorite(song, desired)) {
                                favoriteOverrides = favoriteOverrides + (itemId to isFavorite)
                                Toast.makeText(
                                    context,
                                    "Could not update Favorites",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else if (!desired &&
                                playlistViewModel.selectedPlaylist.value
                                    ?.mediaMetadata?.extras?.getString("navidromeID") == "favourites"
                            ) {
                                playlistViewModel.fetchPlaylistDetails()
                            }
                            delay(120L)
                            actionSong = null
                        }
                    },
                    onAddToPlaylist = {
                        actionScope.launch {
                            delay(120L)
                            actionSong = null
                            playlistSong = song
                        }
                    },
                    onAddToQueueTop = {
                        actionScope.launch {
                            SongHelper.addToQueueTop(song, mediaController)
                            delay(120L)
                            actionSong = null
                        }
                    },
                    onAddToQueueBottom = {
                        actionScope.launch {
                            SongHelper.addToQueueBottom(song, mediaController)
                            delay(120L)
                            actionSong = null
                        }
                    },
                    onStartRadio = {
                        actionScope.launch {
                            delay(120L)
                            actionSong = null
                            startRadio("song") { listOf(song) }
                        }
                    },
                    onOpenStemMixer = {
                        actionScope.launch {
                            delay(120L)
                            actionSong = null
                            SongHelper.playNow(song, mediaController)
                            nowPlayingOpen = true
                            stemMixerSong = song
                        }
                    },
                    onDownload = {
                        actionScope.launch {
                            delay(120L)
                            actionSong = null
                            downloadViewModel.queueDownload(song.mediaMetadata)
                        }
                    }
                )
            }

            actionAlbum?.let { album ->
                val albumId = album.mediaMetadata.extras?.getString("navidromeID") ?: album.mediaId
                val title = album.mediaMetadata.albumTitle?.toString()
                    ?: album.mediaMetadata.title?.toString()
                    ?: "Unknown Album"
                IpodEntityRadioDialog(
                    title = title,
                    entityLabel = "Album",
                    enabled = !albumId.startsWith("Local_") && !radioBuilding,
                    onDismiss = { actionAlbum = null },
                    onStartRadio = {
                        actionAlbum = null
                        startRadio("album") {
                            albumActionsViewModel.getAlbum(albumId)
                                .filter {
                                    it.mediaMetadata.mediaType != MediaMetadata.MEDIA_TYPE_ALBUM
                                }
                        }
                    }
                )
            }

            actionArtist?.let { artist ->
                IpodEntityRadioDialog(
                    title = artist.name,
                    entityLabel = "Artist",
                    enabled = !artist.navidromeID.startsWith("Local_") && !radioBuilding,
                    onDismiss = { actionArtist = null },
                    onStartRadio = {
                        actionArtist = null
                        startRadio("artist") {
                            artistActionsViewModel.getSongsForArtists(listOf(artist))
                        }
                    }
                )
            }

            playlistSong?.let { song ->
                IpodPlaylistPickerDialog(
                    song = song,
                    playlists = actionPlaylists,
                    onDismiss = { playlistSong = null },
                    onPlaylistClick = { playlist ->
                        val playlistId = playlist.mediaMetadata.extras?.getString("navidromeID")
                        val songId = song.mediaMetadata.extras?.getString("navidromeID")
                        if (playlistId != null && songId != null) {
                            playlistViewModel.addSongToPlaylist(playlistId, songId)
                        }
                        playlistSong = null
                    }
                )
            }

            stemMixerSong?.let { song ->
                StemMixerDialog(
                    song = song,
                    mediaController = mediaController,
                    classicStyle = true,
                    onDismiss = { stemMixerSong = null }
                )
            }
        }
    }
}

private fun AudioManager.musicVolumeFraction(): Float {
    val maxVolume = getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    if (maxVolume <= 0) return 0f
    return getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume.toFloat()
}

internal fun volumeIndexForFraction(fraction: Float, maxVolume: Int): Int {
    if (maxVolume <= 0) return 0
    return (fraction.coerceIn(0f, 1f) * maxVolume).roundToInt().coerceIn(0, maxVolume)
}

@Composable
private fun IpodTabRoot(
    selectedTab: IpodTab,
    playbackState: IpodPlaybackState,
    mediaController: MediaController?,
    discoveryMixBuilding: DiscoveryMixMode?,
    discoveryMixMode: DiscoveryMixMode?,
    discoveryMixSongs: List<MediaItem>,
    onBuildDiscoveryMix: (DiscoveryMixMode) -> Unit,
    onPlayDiscoveryMix: (Int) -> Unit,
    onSaveDiscoveryMix: () -> Unit,
    onDestination: (IpodDestination) -> Unit,
    onOpenChoraRoute: (String) -> Unit,
    onNowPlaying: () -> Unit,
    onSongLongClick: (MediaItem) -> Unit,
    onAlbumLongClick: (MediaItem) -> Unit,
    onArtistLongClick: (MediaData.Artist) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    when (selectedTab) {
        IpodTab.PLAYLISTS -> {
            val viewModel: PlaylistScreenViewModel = hiltViewModel()
            val playlists by viewModel.allPlaylists.collectAsStateWithLifecycle()
            val loading by viewModel.isLoading.collectAsStateWithLifecycle()
            val hasLoaded by viewModel.hasLoaded.collectAsStateWithLifecycle()
            IpodPlaylistsScreen(
                playlists = playlists,
                loading = loading || !hasLoaded,
                discoveryMixBuilding = discoveryMixBuilding,
                discoveryMixMode = discoveryMixMode,
                discoveryMixSongs = discoveryMixSongs,
                onBuildDiscoveryMix = onBuildDiscoveryMix,
                onPlayDiscoveryMix = onPlayDiscoveryMix,
                onSaveDiscoveryMix = onSaveDiscoveryMix,
                onPlaylistClick = { onDestination(IpodDestination.Playlist(it)) }
            )
        }

        IpodTab.ARTISTS -> {
            val viewModel: ArtistsScreenViewModel = hiltViewModel()
            val artists by viewModel.allArtists.collectAsStateWithLifecycle()
            val loading by viewModel.isLoading.collectAsStateWithLifecycle()
            val hasLoaded by viewModel.hasLoaded.collectAsStateWithLifecycle()
            IpodArtistsScreen(
                artists = artists,
                loading = loading || !hasLoaded,
                onArtistClick = { onDestination(IpodDestination.Artist(it)) },
                onArtistLongClick = onArtistLongClick
            )
        }

        IpodTab.SONGS -> {
            val viewModel: SongsScreenViewModel = hiltViewModel()
            val songs by viewModel.allSongs.collectAsStateWithLifecycle()
            val loading by viewModel.isLoading.collectAsStateWithLifecycle()
            val hasLoaded by viewModel.hasLoaded.collectAsStateWithLifecycle()
            IpodSongsScreen(
                songs = songs,
                loading = loading || !hasLoaded,
                currentMediaId = playbackState.currentItem?.mediaId,
                onSongClick = { song ->
                    coroutineScope.launch {
                        SongHelper.playNow(song, mediaController)
                    }
                },
                onSongLongClick = onSongLongClick
            )
        }

        IpodTab.ALBUMS -> {
            val viewModel: AlbumScreenViewModel = hiltViewModel()
            val albums by viewModel.allAlbums.collectAsStateWithLifecycle()
            val loading by viewModel.isLoading.collectAsStateWithLifecycle()
            val hasLoaded by viewModel.hasLoaded.collectAsStateWithLifecycle()
            IpodAlbumsScreen(
                albums = albums,
                loading = loading || !hasLoaded,
                onAlbumClick = { onDestination(IpodDestination.Album(it)) },
                onAlbumLongClick = onAlbumLongClick
            )
        }

        IpodTab.AUDIOBOOKS -> {
            val viewModel: AudiobooksViewModel = hiltViewModel()
            val books by viewModel.books.collectAsStateWithLifecycle()
            val hasLoaded by viewModel.hasLoaded.collectAsStateWithLifecycle()
            IpodAudiobooksScreen(
                books = books,
                loading = !hasLoaded,
                currentMediaId = playbackState.currentItem?.mediaId,
                onBookClick = { onDestination(IpodDestination.Audiobook(it)) },
                onResume = { book ->
                    viewModel.resume(book, mediaController)
                    onNowPlaying()
                }
            )
        }

        IpodTab.MORE -> IpodMoreScreen(
            onQueue = { onDestination(IpodDestination.Queue) },
            onRadio = { onDestination(IpodDestination.Radio) },
            onDownloads = { onOpenChoraRoute(Screen.S_Downloads.route) },
            onSettings = { onOpenChoraRoute(Screen.Setting.route) },
            onReturnToChora = { onOpenChoraRoute(Screen.Home.route) }
        )
    }
}

@Composable
private fun IpodDestinationContent(
    destination: IpodDestination,
    playbackState: IpodPlaybackState,
    mediaController: MediaController?,
    onDestination: (IpodDestination) -> Unit,
    onNowPlaying: () -> Unit,
    onSongLongClick: (MediaItem) -> Unit,
    onAlbumLongClick: (MediaItem) -> Unit,
    onStartAlbumRadio: (MediaItem) -> Unit,
    onStartArtistRadio: (MediaData.Artist) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    when (destination) {
        is IpodDestination.Audiobook -> {
            val viewModel: AudiobooksViewModel = hiltViewModel()
            val books by viewModel.books.collectAsStateWithLifecycle()
            val book = books.firstOrNull { it.id == destination.book.id } ?: destination.book
            IpodAudiobookDetail(
                book = book,
                playbackState = playbackState,
                onResume = {
                    viewModel.resume(book, mediaController)
                    onNowPlaying()
                },
                onRestart = {
                    viewModel.restart(book, mediaController)
                    onNowPlaying()
                },
                onDownloadBook = { viewModel.downloadBook(book) },
                onSpeedChange = { speed ->
                    viewModel.setSpeed(book, speed, mediaController)
                },
                onPlayPart = { index, position ->
                    viewModel.playPart(
                        book,
                        index,
                        position,
                        mediaController
                    )
                    onNowPlaying()
                },
                onDownloadPart = { index -> viewModel.downloadPart(book, index) },
                onPartLongClick = onSongLongClick
            )
        }

        is IpodDestination.Album -> {
            val viewModel: AlbumScreenViewModel = hiltViewModel()
            val albumId = destination.item.mediaMetadata.extras?.getString("navidromeID")
                ?: destination.item.mediaId
            var songs by remember(albumId) { mutableStateOf<List<MediaItem>>(emptyList()) }
            var loading by remember(albumId) { mutableStateOf(true) }
            LaunchedEffect(albumId) {
                loading = true
                songs = viewModel.getAlbum(albumId)
                    .filter { it.mediaMetadata.mediaType != MediaMetadata.MEDIA_TYPE_ALBUM }
                loading = false
            }
            IpodAlbumDetail(
                album = destination.item,
                songs = songs,
                loading = loading,
                currentMediaId = playbackState.currentItem?.mediaId,
                onPlayAll = {
                    coroutineScope.launch {
                        SongHelper.play(songs, 0, mediaController)
                        onNowPlaying()
                    }
                },
                onStartRadio = { onStartAlbumRadio(destination.item) },
                onSongClick = { song ->
                    coroutineScope.launch {
                        SongHelper.playNow(song, mediaController)
                    }
                },
                onSongLongClick = onSongLongClick
            )
        }

        is IpodDestination.Artist -> {
            val viewModel: ArtistsScreenViewModel = hiltViewModel()
            val artist by viewModel.selectedArtist.collectAsStateWithLifecycle()
            val albums by viewModel.artistAlbums.collectAsStateWithLifecycle()
            val songs by viewModel.artistSongs.collectAsStateWithLifecycle()
            val loading by viewModel.isLoading.collectAsStateWithLifecycle()
            val detailsLoaded by viewModel.artistDetailsLoaded.collectAsStateWithLifecycle()
            LaunchedEffect(destination.item.navidromeID, destination.item.name) {
                viewModel.setSelectedArtist(destination.item)
            }
            IpodArtistDetail(
                artist = artist ?: destination.item,
                albums = albums,
                songs = songs,
                loading = loading || !detailsLoaded,
                currentMediaId = playbackState.currentItem?.mediaId,
                onPlayAll = { orderedSongs ->
                    coroutineScope.launch {
                        SongHelper.play(orderedSongs, 0, mediaController)
                        onNowPlaying()
                    }
                },
                onAlbumClick = { onDestination(IpodDestination.Album(it)) },
                onAlbumLongClick = onAlbumLongClick,
                onStartRadio = { onStartArtistRadio(artist ?: destination.item) },
                onSongClick = { song ->
                    coroutineScope.launch {
                        SongHelper.playNow(song, mediaController)
                    }
                },
                onSongLongClick = onSongLongClick
            )
        }

        is IpodDestination.Playlist -> {
            val viewModel: PlaylistScreenViewModel = hiltViewModel()
            val songs by viewModel.selectedPlaylistSongs.collectAsStateWithLifecycle()
            val loading by viewModel.isLoading.collectAsStateWithLifecycle()
            val detailsLoaded by viewModel.playlistDetailsLoaded.collectAsStateWithLifecycle()
            LaunchedEffect(destination.item.mediaId) {
                viewModel.setCurrentPlaylist(destination.item)
            }
            IpodPlaylistDetail(
                songs = songs,
                loading = loading || !detailsLoaded,
                currentMediaId = playbackState.currentItem?.mediaId,
                onPlayAll = {
                    coroutineScope.launch {
                        SongHelper.play(songs, 0, mediaController)
                        onNowPlaying()
                    }
                },
                onSongClick = { song ->
                    coroutineScope.launch {
                        SongHelper.playNow(song, mediaController)
                    }
                },
                onSongLongClick = onSongLongClick
            )
        }

        IpodDestination.Queue -> {
            IpodQueueScreen(
                songs = playbackState.queue,
                currentIndex = playbackState.currentIndex,
                onSongClick = { index ->
                    coroutineScope.launch {
                        SongHelper.playQueueItem(index, mediaController)
                    }
                },
                onSongLongClick = onSongLongClick
            )
        }

        IpodDestination.Radio -> {
            val viewModel: RadioScreenViewModel = hiltViewModel()
            val stations by viewModel.radioStations.collectAsStateWithLifecycle()
            val loading by viewModel.isLoading.collectAsStateWithLifecycle()
            IpodRadioScreen(
                stations = stations,
                loading = loading,
                onStationClick = { station ->
                    coroutineScope.launch {
                        SongHelper.play(listOf(station), 0, mediaController)
                        onNowPlaying()
                    }
                }
            )
        }
    }
}
