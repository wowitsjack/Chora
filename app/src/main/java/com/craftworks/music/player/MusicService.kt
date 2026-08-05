package com.craftworks.music.player

import android.app.PendingIntent
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.ui.util.fastFilter
import androidx.core.math.MathUtils.clamp
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionError
import com.craftworks.music.MainActivity
import com.craftworks.music.R
import com.craftworks.music.data.requireUsableNavidromeServerUrl
import com.craftworks.music.data.model.toMediaItem
import com.craftworks.music.data.repository.AlbumRepository
import com.craftworks.music.data.repository.AudiobookBook
import com.craftworks.music.data.repository.AudiobookProgressRepository
import com.craftworks.music.data.repository.AudiobookRepository
import com.craftworks.music.data.repository.ArtistRepository
import com.craftworks.music.data.repository.LyricsRepository
import com.craftworks.music.data.repository.PlaylistRepository
import com.craftworks.music.data.repository.RadioRepository
import com.craftworks.music.data.repository.SongRepository
import com.craftworks.music.data.repository.StarredRepository
import com.craftworks.music.data.model.MediaCategory
import com.craftworks.music.managers.NavidromeManager
import com.craftworks.music.managers.settings.LocalDataSettingsManager
import com.craftworks.music.managers.settings.PlaybackSettingsManager
import com.craftworks.music.providers.navidrome.generateSalt
import com.craftworks.music.providers.navidrome.md5Hash
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import kotlin.math.pow

internal fun buildNavidromeStreamUrl(
    serverUrl: String,
    username: String,
    password: String,
    songId: String,
    bitrateOptions: String,
    salt: String = generateSalt(8)
): String {
    fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    val query = buildString {
        append("id=${encode(songId)}")
        append("&u=${encode(username)}")
        append("&t=${md5Hash(password + salt)}")
        append("&s=${encode(salt)}")
        append("&v=1.16.1&c=Chora")
        append(bitrateOptions)
    }
    val usableServerUrl = requireUsableNavidromeServerUrl(serverUrl)
    return "$usableServerUrl/rest/stream.view?$query"
}

/*
    Thanks to Yurowitz on StackOverflow for this! Used it as a template.
    https://stackoverflow.com/questions/76838126/can-i-define-a-medialibraryservice-without-an-app
*/

@UnstableApi
@AndroidEntryPoint
class ChoraMediaLibraryService : MediaLibraryService() {
    //region Vars
    lateinit var player: Player
    var session: MediaLibrarySession? = null

    private var scrobbleJob: Job? = null
    private var playerListener: Player.Listener? = null
    private val artistNamesById = ConcurrentHashMap<String, String>()
    private val queueWindowExtensionInProgress = AtomicBoolean(false)

    @Inject lateinit var playbackSettingsManager: PlaybackSettingsManager
    @Inject lateinit var localDataSettingsManager: LocalDataSettingsManager

    @Inject lateinit var albumRepository: AlbumRepository
    @Inject lateinit var artistRepository: ArtistRepository
    @Inject lateinit var songRepository: SongRepository
    @Inject lateinit var radioRepository: RadioRepository
    @Inject lateinit var playlistRepository: PlaylistRepository
    @Inject lateinit var lyricsRepository: LyricsRepository
    @Inject lateinit var starredRepository: StarredRepository
    @Inject lateinit var offlineMediaResolver: OfflineMediaResolver
    @Inject lateinit var audiobookProgressRepository: AudiobookProgressRepository
    @Inject lateinit var audiobookRepository: AudiobookRepository

    companion object {
        private var instance: ChoraMediaLibraryService? = null

        fun getInstance(): ChoraMediaLibraryService? {
            return instance
        }
    }

    private val rootItem = MediaItem.Builder()
        .setMediaId("nodeROOT")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setIsBrowsable(false)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build()
        )
        .build()

    private val homeItem = MediaItem.Builder()
        .setMediaId("nodeHOME")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS)
                .setTitle("Home")
                .setExtras(Bundle().apply {
                    putInt(
                        MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                        MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
                    )
                })
                .build()
        )
        .build()

    private val radiosItem = MediaItem.Builder()
        .setMediaId("nodeRADIOS")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                .setTitle("Radios")
                .setExtras(Bundle().apply {
                    putInt(
                        MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                        MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
                    )
                })
                .build()
        )
        .build()

    private val playlistsItem = MediaItem.Builder()
        .setMediaId("nodePLAYLISTS")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
                .setTitle("Playlists")
                .setExtras(Bundle().apply {
                    putInt(
                        MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                        MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
                    )
                })
                .build()
        )
        .build()

    private val favoritesItem = MediaItem.Builder()
        .setMediaId("nodeFAVORITES")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .setTitle("Favorites")
                .setExtras(Bundle().apply {
                    putInt(
                        MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                        MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
                    )
                })
                .build()
        )
        .build()

    private val shuffleAllItem = MediaItem.Builder()
        .setMediaId("action_shuffle_all")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle("Shuffle All")
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build()
        )
        .build()

    private val albumsItem = MediaItem.Builder()
        .setMediaId("nodeALBUMS")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS)
                .setTitle("Albums")
                .setExtras(Bundle().apply {
                    putInt(
                        MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                        MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
                    )
                })
                .build()
        )
        .build()

    private val audiobooksItem = MediaItem.Builder()
        .setMediaId("nodeAUDIOBOOKS")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS)
                .setTitle("Audiobooks")
                .setExtras(Bundle().apply {
                    putInt(
                        MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                        MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
                    )
                })
                .build()
        )
        .build()

    private val artistsItem = MediaItem.Builder()
        .setMediaId("nodeARTISTS")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS)
                .setTitle("Artists")
                .setExtras(Bundle().apply {
                    putInt(
                        MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                        MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
                    )
                })
                .build()
        )
        .build()

    private val rootHierarchy = listOf(
        shuffleAllItem,
        homeItem,
        audiobooksItem,
        albumsItem,
        artistsItem,
        favoritesItem,
        playlistsItem,
        radiosItem
    )

    private val serviceMainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val serviceIOScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val playbackPersistenceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var playbackStateSaveJob: Job? = null
    private var lastPeriodicPlaybackSaveAt = 0L
    private var lastAudiobookSnapshot: AudiobookPlaybackSnapshot? = null

    // Thread-safe synchronized lists for Android Auto browsing
    private val aHomeScreenItems: MutableList<MediaItem> = Collections.synchronizedList(mutableListOf())
    private val aRadioScreenItems: MutableList<MediaItem> = Collections.synchronizedList(mutableListOf())
    private val aPlaylistScreenItems: MutableList<MediaItem> = Collections.synchronizedList(mutableListOf())
    private val aAlbumScreenItems: MutableList<MediaItem> = Collections.synchronizedList(mutableListOf())
    private val aAudiobookScreenItems: MutableList<MediaItem> = Collections.synchronizedList(mutableListOf())
    private val aArtistScreenItems: MutableList<MediaItem> = Collections.synchronizedList(mutableListOf())
    private val aFavoriteScreenItems: MutableList<MediaItem> = Collections.synchronizedList(mutableListOf())
    private val aFolderSongs: MutableList<MediaItem> = Collections.synchronizedList(mutableListOf())

    //endregion

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // Clear any stale instance before setting new one
        instance?.let { oldInstance ->
            if (oldInstance !== this) {
                Log.w("AA", "Replacing stale service instance")
            }
        }
        instance = this

        Log.d("AA", "onCreate Android Auto")

        if (session == null)
            initializePlayer()
        else
            Log.d("AA", "MediaSession already initialized, not recreating")
    }

    @OptIn(UnstableApi::class)
    fun initializePlayer() {
        player = ExoPlayer.Builder(this)
            .setSeekParameters(SeekParameters.EXACT)
            .setWakeMode(
                if (NavidromeManager.checkActiveServers())
                    C.WAKE_MODE_NETWORK
                else
                    C.WAKE_MODE_LOCAL
            )
            .setHandleAudioBecomingNoisy(true)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .build()

        player.repeatMode = Player.REPEAT_MODE_OFF
        player.shuffleModeEnabled = false

        // Use AtomicBoolean for thread-safe scrobble state
        val playerScrobbled = java.util.concurrent.atomic.AtomicBoolean(false)

        playerListener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                lastAudiobookSnapshot?.let { snapshot ->
                    persistAudiobookSnapshot(snapshot, syncImmediately = true)
                }
                lastAudiobookSnapshot = null

                // Apply ReplayGain
                if (mediaItem?.mediaMetadata?.extras?.getFloat("replayGain") != null) {
                    player.volume = clamp(
                        (10f.pow(
                            ((mediaItem.mediaMetadata.extras?.getFloat("replayGain") ?: 0f) / 20f)
                        )), 0f, 1f
                    )
                    Log.d("REPLAY GAIN", "Setting ReplayGain to ${player.volume}")
                }

                playerScrobbled.set(false)

                val isAudiobook = mediaItem?.mediaMetadata?.extras
                    ?.getString("mediaCategory") == MediaCategory.AUDIOBOOK
                if (isAudiobook) {
                    val albumId = mediaItem?.mediaMetadata?.extras?.getString("albumId")
                    serviceIOScope.launch {
                        val speed = albumId?.let { audiobookProgressRepository.getBookSpeed(it) } ?: 1f
                        withContext(Dispatchers.Main) {
                            if (player.currentMediaItem?.mediaId == mediaItem?.mediaId) {
                                player.setPlaybackSpeed(speed)
                            }
                        }
                    }
                } else {
                    player.setPlaybackSpeed(1f)
                }

                super.onMediaItemTransition(mediaItem, reason)
                extendPlaybackQueueWindowIfNeeded()

                serviceIOScope.launch {
                    try {
                        if (!isAudiobook) {
                            songRepository.scrobbleSong(mediaItem?.mediaMetadata?.extras?.getString("navidromeID") ?: "", false)
                            lyricsRepository.getLyrics(mediaItem?.mediaMetadata)
                        }
                    } catch (e: Exception) {
                        Log.e("PLAYER", "Error scrobbling or fetching lyrics", e)
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                error.printStackTrace()
                Log.e("PLAYER", error.stackTraceToString())
            }

            override fun onEvents(player: Player, events: Player.Events) {
                val stateChanged =
                    events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                        events.contains(Player.EVENT_TIMELINE_CHANGED) ||
                        events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
                        events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) ||
                        events.contains(Player.EVENT_IS_PLAYING_CHANGED) ||
                        events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) ||
                        events.contains(Player.EVENT_PLAYBACK_PARAMETERS_CHANGED)

                if (stateChanged) {
                    val saveDelay = if (
                        !player.isPlaying || player.playbackState == Player.STATE_ENDED
                    ) {
                        0L
                    } else {
                        500L
                    }
                    schedulePlaybackStateSave(saveDelay)
                }

                val audiobookStateChanged =
                    events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
                        events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) ||
                        events.contains(Player.EVENT_IS_PLAYING_CHANGED) ||
                        events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) ||
                        events.contains(Player.EVENT_PLAYBACK_PARAMETERS_CHANGED)
                if (audiobookStateChanged) {
                    captureAudiobookSnapshot(player)?.let { snapshot ->
                        lastAudiobookSnapshot = snapshot
                        val immediate = !player.isPlaying || player.playbackState == Player.STATE_ENDED
                        persistAudiobookSnapshot(snapshot, syncImmediately = immediate)
                    }
                }

                if (
                    events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                    events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
                    events.contains(Player.EVENT_TIMELINE_CHANGED)
                ) {
                    extendPlaybackQueueWindowIfNeeded()
                }
            }
        }
        playerListener?.let { player.addListener(it) }

        val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        session = MediaLibrarySession.Builder(this, player, LibrarySessionCallback())
            .setId("AutoSession")
            .setSessionActivity(sessionActivityPendingIntent)
            .build()

        restoreSavedPlaybackQueue()

        scrobbleJob = serviceMainScope.launch {
            while (isActive) {
                // Guard against accessing player after release
                if (!::player.isInitialized) {
                    delay(1000)
                    continue
                }

                try {
                    val duration = player.duration
                    val mediaItem = player.currentMediaItem

                    captureAudiobookSnapshot(player)?.let { snapshot ->
                        lastAudiobookSnapshot = snapshot
                    }

                    val now = SystemClock.elapsedRealtime()
                    if (player.isPlaying && now - lastPeriodicPlaybackSaveAt >= 15_000L) {
                        lastPeriodicPlaybackSaveAt = now
                        schedulePlaybackStateSave(delayMs = 0L)
                        lastAudiobookSnapshot?.let { snapshot ->
                            persistAudiobookSnapshot(snapshot, syncImmediately = false)
                        }
                    }

                    if (duration > 0 && !playerScrobbled.get()) {
                        val currentPosition = player.currentPosition
                        // Use Double to avoid integer overflow and precision loss
                        val progress = (currentPosition.toDouble() * 100.0 / duration.toDouble()).toInt()
                        val scrobblePercentage = playbackSettingsManager.scrobblePercentFlow.first() * 10

                        if (progress >= scrobblePercentage) {
                            playerScrobbled.set(true)
                            if (NavidromeManager.checkActiveServers() &&
                                mediaItem?.mediaMetadata?.extras?.getString("navidromeID")
                                    ?.startsWith("Local") == false &&
                                mediaItem.mediaMetadata.mediaType != MediaMetadata.MEDIA_TYPE_RADIO_STATION &&
                                mediaItem.mediaMetadata.extras?.getString("mediaCategory") != MediaCategory.AUDIOBOOK
                            ) {
                                serviceIOScope.launch {
                                    try {
                                        songRepository.scrobbleSong(mediaItem.mediaMetadata.extras?.getString("navidromeID") ?: "", true)
                                    } catch (e: Exception) {
                                        Log.e("PLAYER", "Error submitting scrobble", e)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: IllegalStateException) {
                    // Player may have been released during access
                    Log.d("PLAYER", "Player released during scrobble check")
                }
                delay(1000)
            }
        }

        Log.d("AA", "Initialized MediaLibraryService.")
    }

    private fun extendPlaybackQueueWindowIfNeeded() {
        if (!::player.isInitialized) return

        val extension = SongHelper.planQueueWindowExtension(
            currentIndex = player.currentMediaItemIndex,
            visibleItemCount = player.mediaItemCount
        ) ?: return

        if (!queueWindowExtensionInProgress.compareAndSet(false, true)) return

        serviceIOScope.launch {
            try {
                val resolvedItems = resolveMediaItemsForPlayback(extension.items)
                if (resolvedItems.isEmpty()) return@launch

                withContext(Dispatchers.Main) {
                    if (
                        !::player.isInitialized ||
                        !SongHelper.isQueueWindowExtensionCurrent(extension)
                    ) {
                        return@withContext
                    }

                    if (extension.insertAtStart) {
                        player.addMediaItems(0, resolvedItems)
                    } else {
                        player.addMediaItems(resolvedItems)
                    }
                    SongHelper.commitQueueWindowExtension(extension)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MusicService", "Could not extend the playback queue window", e)
            } finally {
                queueWindowExtensionInProgress.set(false)
                withContext(Dispatchers.Main) {
                    if (::player.isInitialized) {
                        extendPlaybackQueueWindowIfNeeded()
                    }
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return session
    }

    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {
        override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
            serviceIOScope.launch {
                try {
                    println("ONPOSTCONNTECT MUSIC SERVICE!")

                    if (session.isAutoCompanionController(controller))
                        getHomeScreenItems()

                    this@ChoraMediaLibraryService.session?.notifyChildrenChanged(
                        "nodeHOME",
                        aHomeScreenItems.size,
                        null
                    )
                } catch (e: Exception) {
                    Log.e("MusicService", "Error in onPostConnect", e)
                }
            }
            super.onPostConnect(session, controller)
        }

        @OptIn(UnstableApi::class)
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> {
            val resultFuture = SettableFuture.create<List<MediaItem>>()
            serviceIOScope.launch {
                try {
                    resultFuture.set(resolveMediaItemsForPlayback(mediaItems))
                } catch (e: CancellationException) {
                    resultFuture.cancel(false)
                    throw e
                } catch (e: Exception) {
                    Log.e("MusicService", "Error resolving items added to the queue", e)
                    resultFuture.setException(e)
                }
            }
            return resultFuture
        }

        @OptIn(UnstableApi::class)
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaItemsWithStartPosition> {
            // Handle empty media items list to prevent IndexOutOfBoundsException
            if (mediaItems.isEmpty()) {
                return Futures.immediateFuture(
                    MediaItemsWithStartPosition(emptyList(), 0, 0L)
                )
            }

            // Handle Shuffle All action
            if (mediaItems.firstOrNull()?.mediaId == "action_shuffle_all") {
                val shuffleFuture = SettableFuture.create<MediaItemsWithStartPosition>()
                serviceIOScope.launch {
                    try {
                        val randomSongs = songRepository.getRandomSongs(100)
                        val resolvedItems = resolveMediaItemsForPlayback(randomSongs)
                        withContext(Dispatchers.Main) {
                            player.shuffleModeEnabled = true
                        }
                        SongHelper.currentTracklist = resolvedItems.toMutableList()
                        shuffleFuture.set(MediaItemsWithStartPosition(resolvedItems, 0, 0L))
                    } catch (e: Exception) {
                        Log.e("MusicService", "Error handling shuffle all", e)
                        shuffleFuture.set(MediaItemsWithStartPosition(emptyList(), 0, 0L))
                    }
                }
                return shuffleFuture
            }

            // IMPORTANT: Use the already-windowed mediaItems passed to us, NOT SongHelper.currentTracklist
            // SongHelper.currentTracklist may contain thousands of items which exceeds the Binder limit
            // The mediaItems parameter is already windowed by SongHelper.play() to ~200 items

            val connectivityManager =
                this@ChoraMediaLibraryService.baseContext.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

            val networkCapabilities =
                connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)

            // Resolve offline media items asynchronously - move ALL suspend calls into the coroutine
            val resultFuture = SettableFuture.create<MediaItemsWithStartPosition>()

            serviceIOScope.launch {
                // Get bitrate inside the coroutine to avoid runBlocking
                val bitrate: String? = when {
                    networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> {
                        Log.d("NetworkCheck", "Device is on Wi-Fi")
                        playbackSettingsManager.wifiTranscodingBitrateFlow.first()
                    }
                    networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> {
                        Log.d("NetworkCheck", "Device is on Mobile Data")
                        playbackSettingsManager.mobileDataTranscodingBitrateFlow.first()
                    }
                    else -> {
                        Log.d("NetworkCheck", "Device is on another network type")
                        playbackSettingsManager.wifiTranscodingBitrateFlow.first()
                    }
                }

                val bitrateOptions = if (bitrate != null && bitrate != "No Transcoding" && bitrate.isNotEmpty()) {
                    "&maxBitRate=$bitrate&format=${playbackSettingsManager.transcodingFormatFlow.first()}"
                } else {
                    ""
                }
                try {
                    // Get the full tracklist from SongHelper - this contains all items with metadata
                    // We only receive 1 item through IPC due to Binder limits, but we use the
                    // full tracklist to build the proper queue
                    val fullTracklist = SongHelper.currentTracklist
                    Log.d("MusicService", "onSetMediaItems: received=${mediaItems.size}, fullTracklist=${fullTracklist.size}, bitrateOptions=$bitrateOptions")

                    // Find the starting item's index in the full tracklist
                    val startingMediaId = mediaItems.firstOrNull()?.mediaId
                    val requestedQueueIndex = mediaItems.firstOrNull()
                        ?.mediaMetadata
                        ?.extras
                        ?.getInt(SongHelper.QUEUE_INDEX_EXTRA, -1)
                        ?: -1
                    val startingIndex = requestedQueueIndex.takeIf {
                        it in fullTracklist.indices &&
                            fullTracklist[it].mediaId == startingMediaId
                    } ?: if (startingMediaId != null) {
                        fullTracklist.indexOfFirst { it.mediaId == startingMediaId }.coerceAtLeast(0)
                    } else {
                        0
                    }

                    // Window the tracklist around the starting index (max 50 items to avoid memory issues)
                    val windowSize = 50
                    val halfWindow = windowSize / 2
                    val windowStart = (startingIndex - halfWindow).coerceAtLeast(0)
                    val windowEnd = (windowStart + windowSize).coerceAtMost(fullTracklist.size)
                    val adjustedWindowStart = (windowEnd - windowSize).coerceAtLeast(0)
                    val windowedTracklist = fullTracklist.subList(adjustedWindowStart, windowEnd)
                    val adjustedStartIndex = startingIndex - adjustedWindowStart

                    Log.d("MusicService", "Window: $adjustedWindowStart-$windowEnd, startIndex=$adjustedStartIndex")

                    val resolvedItems = windowedTracklist.map { originalItem ->
                        val songId = originalItem.mediaMetadata.extras?.getString("navidromeID")
                        val offlinePath = if (songId != null) {
                            offlineMediaResolver.getOfflinePath(songId)
                        } else null

                        val baseItem = if (offlinePath != null) {
                            // Use offline file - no transcoding needed
                            Log.d("OfflinePlayback", "Using offline file for: ${originalItem.mediaMetadata.title}")
                            MediaItem.Builder()
                                .setMediaId(originalItem.mediaId)
                                .setMediaMetadata(originalItem.mediaMetadata)
                                .setUri(offlinePath)
                                .build()
                        } else {
                            val finalUri = resolveOnlinePlaybackUri(originalItem, bitrateOptions)
                            MediaItem.Builder()
                                .setMediaId(originalItem.mediaId)
                                .setMediaMetadata(originalItem.mediaMetadata)
                                .setUri(finalUri)
                                .build()
                        }

                        baseItem
                    }

                    Log.d("MusicService", "Resolved ${resolvedItems.size} items, startAt=$adjustedStartIndex, first: ${resolvedItems.firstOrNull()?.mediaMetadata?.title}")

                    val result = MediaItemsWithStartPosition(
                        resolvedItems,
                        adjustedStartIndex,
                        startPositionMs
                    )
                    resultFuture.set(result)
                } catch (e: Exception) {
                    Log.e("MusicService", "Error resolving offline media items", e)
                    resultFuture.setException(e)
                }
            }

            return resultFuture
        }


        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        @OptIn(UnstableApi::class)
        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            // For root hierarchy, return immediately
            if (parentId == "nodeROOT") {
                return Futures.immediateFuture(LibraryResult.ofItemList(rootHierarchy, params))
            }

            // For all other nodes, use async operations
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            val offset = page * pageSize
            val limit = pageSize.coerceIn(1, 100)

            serviceIOScope.launch {
                try {
                    val items: List<MediaItem> = when {
                        parentId == "nodeHOME" -> getHomeScreenItems()
                        parentId == "nodeALBUMS" -> getAlbumItems(limit, offset)
                        parentId == "nodeAUDIOBOOKS" -> getAudiobookItems(limit, offset)
                        parentId == "nodeARTISTS" -> getArtistItems(limit, offset)
                        parentId == "nodeFAVORITES" -> getFavoriteItems()
                        parentId == "nodeRADIOS" -> getRadioItems()
                        parentId == "nodePLAYLISTS" -> getPlaylistItems()
                        parentId.startsWith("artist_") -> {
                            val artistId = parentId.removePrefix("artist_")
                            getArtistAlbums(artistId, artistNamesById[artistId])
                        }
                        else -> {
                            val mediaItem =
                                aHomeScreenItems.find { it.mediaId == parentId }
                                    ?: aPlaylistScreenItems.find { it.mediaId == parentId }
                                    ?: aAlbumScreenItems.find { it.mediaId == parentId }
                                    ?: aAudiobookScreenItems.find { it.mediaId == parentId }
                                    ?: aFavoriteScreenItems.find { it.mediaId == parentId }
                            getFolderItems(
                                parentId,
                                mediaItem?.mediaMetadata?.mediaType
                                    ?: MediaMetadata.MEDIA_TYPE_ALBUM
                            )
                        }
                    }
                    future.set(LibraryResult.ofItemList(items, params))
                } catch (e: Exception) {
                    Log.e("MusicService", "Error in onGetChildren for $parentId", e)
                    future.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
                }
            }
            return future
        }


        @OptIn(UnstableApi::class)
        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val mediaItem = aFolderSongs.find { it.mediaId == mediaId }
                ?: aRadioScreenItems.find { it.mediaId == mediaId }
                ?: aAudiobookScreenItems.find { it.mediaId == mediaId }
                ?: return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))

            return Futures.immediateFuture(
                LibraryResult.ofItem(
                    mediaItem,
                    LibraryParams.Builder().build()
                )
            )
        }

        override fun onSubscribe(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            session.notifyChildrenChanged(
                parentId,
                when (parentId) {
                    "nodeROOT" -> rootHierarchy.size
                    "nodeHOME" -> aHomeScreenItems.size
                    "nodeAUDIOBOOKS" -> aAudiobookScreenItems.size
                    "nodeRADIOS" -> aRadioScreenItems.size
                    "nodePLAYLISTS" -> aPlaylistScreenItems.size
                    else -> 0
                },
                params
            )

            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaItemsWithStartPosition> {
            val settable = SettableFuture.create<MediaItemsWithStartPosition>()
            serviceIOScope.launch {
                try {
                    Log.d("RESUMPTION", "Getting onPlaybackResumption")
                    val playbackState = loadSavedPlaybackState()
                    if (playbackState.mediaItems.isEmpty()) {
                        Log.w("RESUMPTION", "Empty playlist, skipping resumption")
                        settable.set(MediaItemsWithStartPosition(emptyList(), 0, 0L))
                        return@launch
                    }
                    settable.set(playbackState)
                    Log.d(
                        "RESUMPTION",
                        "Returned ${playbackState.mediaItems.size} items at index " +
                            "${playbackState.startIndex}, position ${playbackState.startPositionMs}"
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("RESUMPTION", "Error during playback resumption", e)
                    settable.set(MediaItemsWithStartPosition(emptyList(), 0, 0L))
                }
            }
            return settable
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()

            serviceIOScope.launch {
                try {
                    val results = mutableListOf<MediaItem>()

                    // Albums first - grouped
                    albumRepository.searchAlbum(query).forEach { album ->
                        results.add(album.withGroupTitle("Albums"))
                    }

                    // Artists second - grouped
                    artistRepository.getArtists("alphabeticalByName", 50, 0)
                        .filter { it.name.contains(query, ignoreCase = true) }
                        .forEach { artist ->
                            results.add(
                                MediaItem.Builder()
                                    .setMediaId("artist_${artist.navidromeID}")
                                    .setMediaMetadata(
                                        MediaMetadata.Builder()
                                            .setTitle(artist.name)
                                            .setArtist(artist.name)
                                            .setArtworkUri(Uri.parse(artist.artistImageUrl ?: ""))
                                            .setIsBrowsable(true)
                                            .setIsPlayable(false)
                                            .setMediaType(MediaMetadata.MEDIA_TYPE_ARTIST)
                                            .setExtras(Bundle().apply {
                                                putString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, "Artists")
                                            })
                                            .build()
                                    )
                                    .build()
                            )
                        }

                    // Songs third - grouped
                    val songs = songRepository.getSongs(query).take(20)
                    songs.forEach { song ->
                        results.add(song.withGroupTitle("Songs"))
                    }

                    // Playlists fourth - grouped
                    playlistRepository.getPlaylists().fastFilter {
                        it.mediaMetadata.title?.contains(query, ignoreCase = true) == true
                    }.forEach { playlist ->
                        results.add(playlist.withGroupTitle("Playlists"))
                    }

                    // Store songs for playback
                    SongHelper.currentTracklist = songs.toMutableList()

                    future.set(LibraryResult.ofItemList(results, LibraryParams.Builder().build()))
                } catch (e: Exception) {
                    Log.e("MusicService", "Error in onGetSearchResult", e)
                    future.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
                }
            }
            return future
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            Log.d("AA", "onSearch: $query")

            serviceIOScope.launch {
                try {
                    val totalCount = songRepository.getSongs(query).size +
                            albumRepository.searchAlbum(query).size +
                            artistRepository.getArtists("alphabeticalByName", 50, 0)
                                .filter { it.name.contains(query, ignoreCase = true) }.size +
                            playlistRepository.getPlaylists().fastFilter {
                                it.mediaMetadata.title?.contains(query, ignoreCase = true) == true
                            }.size

                    session.notifySearchResultChanged(
                        browser,
                        query,
                        totalCount,
                        LibraryParams.Builder().build()
                    )
                } catch (e: Exception) {
                    Log.e("MusicService", "Error in onSearch", e)
                }
            }

            return Futures.immediateFuture(LibraryResult.ofVoid())
        }
    }


    override fun onDestroy() {
        // Capture once more without blocking the main thread. Regular player
        // events and the periodic checkpoint already keep this nearly current.
        schedulePlaybackStateSave(delayMs = 0L)
        if (::player.isInitialized) {
            captureAudiobookSnapshot(player)?.let { snapshot ->
                persistAudiobookSnapshot(snapshot, syncImmediately = true)
            }
        }
        scrobbleJob?.cancel()
        scrobbleJob = null
        // Cancel coroutine scopes to prevent memory leaks and orphaned coroutines
        (serviceMainScope.coroutineContext[Job])?.cancel()
        (serviceIOScope.coroutineContext[Job])?.cancel()
        session?.release()
        session = null
        if (::player.isInitialized) {
            playerListener?.let { player.removeListener(it) }
            playerListener = null
            player.release()
        }
        instance = null
        super.onDestroy()
    }

    private data class PlaybackSnapshot(
        val mediaItems: List<MediaItem>,
        val currentIndex: Int,
        val currentPosition: Long
    )

    private data class AudiobookPlaybackSnapshot(
        val mediaItem: MediaItem,
        val positionMs: Long,
        val durationMs: Long,
        val playbackSpeed: Float
    )

    private fun captureAudiobookSnapshot(activePlayer: Player?): AudiobookPlaybackSnapshot? {
        if (activePlayer == null) return null
        return try {
            val item = activePlayer.currentMediaItem ?: return null
            if (
                item.mediaMetadata.extras?.getString("mediaCategory") !=
                MediaCategory.AUDIOBOOK
            ) {
                return null
            }
            AudiobookPlaybackSnapshot(
                mediaItem = item,
                positionMs = activePlayer.currentPosition.coerceAtLeast(0L),
                durationMs = activePlayer.duration.takeIf { it > 0L }
                    ?: item.mediaMetadata.durationMs
                    ?: 0L,
                playbackSpeed = activePlayer.playbackParameters.speed
            )
        } catch (_: IllegalStateException) {
            null
        }
    }

    private fun persistAudiobookSnapshot(
        snapshot: AudiobookPlaybackSnapshot,
        syncImmediately: Boolean
    ) {
        playbackPersistenceScope.launch {
            audiobookProgressRepository.savePlayback(
                mediaItem = snapshot.mediaItem,
                positionMs = snapshot.positionMs,
                durationMs = snapshot.durationMs,
                playbackSpeed = snapshot.playbackSpeed,
                syncImmediately = syncImmediately
            )
        }
    }

    private fun capturePlaybackSnapshot(): PlaybackSnapshot? {
        if (!::player.isInitialized) {
            return null
        }

        return try {
            val mediaItemCount = player.mediaItemCount
            val mediaItems = List(mediaItemCount) { i -> player.getMediaItemAt(i) }
            PlaybackSnapshot(
                mediaItems = mediaItems,
                currentIndex = player.currentMediaItemIndex,
                currentPosition = player.currentPosition
            )
        } catch (e: IllegalStateException) {
            Log.d("AA", "Player was released before state capture")
            null
        }
    }

    private fun schedulePlaybackStateSave(delayMs: Long = 500L) {
        val snapshot = capturePlaybackSnapshot() ?: return
        playbackStateSaveJob?.cancel()
        playbackStateSaveJob = playbackPersistenceScope.launch {
            try {
                if (delayMs > 0L) {
                    delay(delayMs)
                }
                localDataSettingsManager.setPlaybackResumption(
                    snapshot.mediaItems,
                    snapshot.currentIndex,
                    snapshot.currentPosition
                )
                Log.d(
                    "AA",
                    "Saved playback state: ${snapshot.mediaItems.size} items, " +
                        "index ${snapshot.currentIndex}, position ${snapshot.currentPosition}"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("AA", "Could not save playback state", e)
            }
        }
    }

    //region getChildren - All suspend functions for async Android Auto browsing

    private suspend fun getHomeScreenItems(): List<MediaItem> = coroutineScope {
        Log.d("AA", "Getting home screen items")
        if (aHomeScreenItems.isEmpty()) {
            // Fetch all sections concurrently
            val recentlyPlayed = async { albumRepository.getAlbums("recent", 6) }
            val recentlyAdded = async { albumRepository.getAlbums("newest", 6) }
            val mostPlayed = async { albumRepository.getAlbums("frequent", 6) }
            val randomAlbums = async { albumRepository.getAlbums("random", 6) }

            recentlyPlayed.await().forEach { album ->
                aHomeScreenItems.add(album.withGroupTitle(getString(R.string.recently_played)))
            }

            recentlyAdded.await().forEach { album ->
                aHomeScreenItems.add(album.withGroupTitle(getString(R.string.recently_added)))
            }

            mostPlayed.await().forEach { album ->
                aHomeScreenItems.add(album.withGroupTitle(getString(R.string.most_played)))
            }

            randomAlbums.await().forEach { album ->
                aHomeScreenItems.add(album.withGroupTitle(getString(R.string.random_songs)))
            }
        }
        aHomeScreenItems
    }

    private suspend fun getAlbumItems(limit: Int, offset: Int): List<MediaItem> {
        Log.d("AA", "Getting album items: limit=$limit, offset=$offset")
        val albums = albumRepository.getAlbums("alphabeticalByName", limit, offset)
        aAlbumScreenItems.clear()
        aAlbumScreenItems.addAll(albums)
        return albums
    }

    private suspend fun getAudiobookItems(limit: Int, offset: Int): List<MediaItem> {
        Log.d("AA", "Getting audiobook items: limit=$limit, offset=$offset")
        val books = audiobookRepository.getBooks().drop(offset).take(limit).map(AudiobookBook::album)
        aAudiobookScreenItems.clear()
        aAudiobookScreenItems.addAll(books)
        return books
    }

    private suspend fun getArtistItems(limit: Int, offset: Int): List<MediaItem> {
        Log.d("AA", "Getting artist items: limit=$limit, offset=$offset")
        val artists = artistRepository.getArtists("alphabeticalByName", limit, offset)
        artists.forEach { artist ->
            artistNamesById[artist.navidromeID] = artist.name
        }
        aArtistScreenItems.clear()
        aArtistScreenItems.addAll(
            artists.map { artist ->
                MediaItem.Builder()
                    .setMediaId("artist_${artist.navidromeID}")
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(artist.name)
                            .setArtist(artist.name)
                            .setArtworkUri(Uri.parse(artist.artistImageUrl ?: ""))
                            .setIsBrowsable(true)
                            .setIsPlayable(false)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_ARTIST)
                            .setExtras(Bundle().apply {
                                putString("navidromeID", artist.navidromeID)
                                putString("artistName", artist.name)
                                putInt("albumCount", artist.albumCount ?: 0)
                            })
                            .build()
                    )
                    .build()
            }
        )
        return aArtistScreenItems
    }

    private suspend fun getArtistAlbums(
        artistId: String,
        artistName: String? = artistNamesById[artistId]
    ): List<MediaItem> {
        Log.d("AA", "Getting albums for artist: $artistId")
        return artistRepository.getArtistAlbums(artistId, artistName)
    }

    private suspend fun getFavoriteItems(): List<MediaItem> {
        Log.d("AA", "Getting favorite items")
        aFavoriteScreenItems.clear()
        val starred = starredRepository.getStarredItems()
        aFavoriteScreenItems.addAll(starred)
        return starred
    }

    private suspend fun getRadioItems(): List<MediaItem> {
        Log.d("AA", "Getting radio items")
        aRadioScreenItems.clear()
        aRadioScreenItems.addAll(
            radioRepository.getRadios().map { radio ->
                radio.toMediaItem()
            }
        )
        SongHelper.currentTracklist = aRadioScreenItems
        return aRadioScreenItems
    }

    private suspend fun getPlaylistItems(): List<MediaItem> {
        Log.d("AA", "Getting playlist items")
        if (aPlaylistScreenItems.isEmpty()) {
            aPlaylistScreenItems.addAll(playlistRepository.getPlaylists())
        }
        return aPlaylistScreenItems
    }

    private suspend fun getFolderItems(parentId: String, type: Int): List<MediaItem> {
        Log.d("AA", "Getting folder items: $parentId, type=$type")
        aFolderSongs.clear()
        when (type) {
            MediaMetadata.MEDIA_TYPE_ALBUM -> {
                val albumSongs = albumRepository.getAlbum(parentId)
                aFolderSongs.addAll(
                    if (albumSongs != null && albumSongs.size > 1) albumSongs.subList(1, albumSongs.size) else emptyList()
                )
            }
            MediaMetadata.MEDIA_TYPE_AUDIO_BOOK -> {
                aFolderSongs.addAll(audiobookRepository.getBook(parentId)?.parts.orEmpty())
            }
            MediaMetadata.MEDIA_TYPE_PLAYLIST -> {
                aFolderSongs.addAll(playlistRepository.getPlaylistSongs(parentId))
            }
            else -> aFolderSongs.clear()
        }
        SongHelper.currentTracklist = aFolderSongs
        return aFolderSongs
    }

    //endregion

    //region Helper functions

    private fun restoreSavedPlaybackQueue() {
        serviceIOScope.launch {
            try {
                val playbackState = loadSavedPlaybackState()
                if (playbackState.mediaItems.isEmpty()) return@launch

                withContext(Dispatchers.Main) {
                    // A user action always wins if it races service startup.
                    if (player.mediaItemCount > 0) return@withContext

                    player.setMediaItems(
                        playbackState.mediaItems,
                        playbackState.startIndex,
                        playbackState.startPositionMs
                    )
                    player.playWhenReady = false
                    SongHelper.currentTracklist = playbackState.mediaItems
                    Log.d(
                        "RESUMPTION",
                        "Restored paused queue: ${playbackState.mediaItems.size} items, " +
                            "index ${playbackState.startIndex}, position ${playbackState.startPositionMs}"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("RESUMPTION", "Could not restore saved playback queue", e)
            }
        }
    }

    private suspend fun loadSavedPlaybackState(): MediaItemsWithStartPosition {
        val savedState = localDataSettingsManager
            .playbackResumptionPlaylistWithStartPosition.first()
        if (savedState.mediaItems.isEmpty()) {
            return MediaItemsWithStartPosition(emptyList(), 0, 0L)
        }

        val resolvedItems = resolveMediaItemsForPlayback(savedState.mediaItems)
        val safeStartIndex = savedState.startIndex.coerceIn(0, resolvedItems.lastIndex)
        return MediaItemsWithStartPosition(
            resolvedItems,
            safeStartIndex,
            savedState.startPositionMs.coerceAtLeast(0L)
        )
    }

    private fun MediaItem.withGroupTitle(title: String): MediaItem {
        return MediaItem.Builder()
            .setMediaId(this.mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .populate(this.mediaMetadata)
                    .setExtras(Bundle().apply {
                        this@withGroupTitle.mediaMetadata.extras?.let { putAll(it) }
                        putString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, title)
                    })
                    .build()
            )
            .build()
    }

    private suspend fun resolveMediaItemsForPlayback(mediaItems: List<MediaItem>): List<MediaItem> {
        val connectivityManager = baseContext.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)

        val bitrate: String? = when {
            networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> {
                playbackSettingsManager.wifiTranscodingBitrateFlow.first()
            }
            networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> {
                playbackSettingsManager.mobileDataTranscodingBitrateFlow.first()
            }
            else -> {
                playbackSettingsManager.wifiTranscodingBitrateFlow.first()
            }
        }

        val bitrateOptions = if (bitrate != null && bitrate != "No Transcoding" && bitrate.isNotEmpty()) {
            "&maxBitRate=$bitrate&format=${playbackSettingsManager.transcodingFormatFlow.first()}"
        } else {
            ""
        }

        return mediaItems.map { mediaItem ->
            val songId = mediaItem.mediaMetadata.extras?.getString("navidromeID")
            val offlinePath = if (songId != null) {
                offlineMediaResolver.getOfflinePath(songId)
            } else null

            val baseItem = if (offlinePath != null) {
                MediaItem.Builder()
                    .setMediaId(mediaItem.mediaId)
                    .setMediaMetadata(mediaItem.mediaMetadata)
                    .setUri(offlinePath)
                    .build()
            } else {
                MediaItem.Builder()
                    .setMediaId(mediaItem.mediaId)
                    .setMediaMetadata(mediaItem.mediaMetadata)
                    .setUri(resolveOnlinePlaybackUri(mediaItem, bitrateOptions))
                    .build()
            }

            baseItem
        }
    }

    private fun resolveOnlinePlaybackUri(
        mediaItem: MediaItem,
        bitrateOptions: String
    ): String {
        val extras = mediaItem.mediaMetadata.extras
        val songId = extras?.getString("navidromeID")
        val isRadio = mediaItem.mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_RADIO_STATION ||
            extras?.getBoolean("isRadio") == true
        val isRemoteSong = !songId.isNullOrBlank() &&
            !songId.startsWith("Local_") &&
            !isRadio
        val server = NavidromeManager.getCurrentServer()

        if (!isRemoteSong || server == null) {
            return mediaItem.mediaId
        }

        return try {
            buildNavidromeStreamUrl(
                serverUrl = server.url,
                username = server.username,
                password = server.password,
                songId = songId,
                bitrateOptions = bitrateOptions
            )
        } catch (e: IllegalArgumentException) {
            Log.w("MusicService", "Cannot build Navidrome stream URL: ${e.message}")
            ""
        }
    }

    //endregion
}
