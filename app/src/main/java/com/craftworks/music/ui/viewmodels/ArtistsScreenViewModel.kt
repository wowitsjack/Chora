package com.craftworks.music.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.craftworks.music.data.database.dao.ArtistDao
import com.craftworks.music.data.database.dao.SongDao
import com.craftworks.music.data.database.entity.toMediaDataArtist
import com.craftworks.music.data.database.entity.toMediaDataSong
import com.craftworks.music.data.model.MediaData
import com.craftworks.music.data.model.toMediaItem
import com.craftworks.music.data.repository.AlbumRepository
import com.craftworks.music.data.repository.ArtistRepository
import com.craftworks.music.data.repository.SyncRepository
import com.craftworks.music.ui.util.TextDisplayUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArtistsScreenViewModel @Inject constructor(
    private val artistRepository: ArtistRepository,
    private val albumRepository: AlbumRepository,
    private val syncRepository: SyncRepository,
    private val artistDao: ArtistDao,
    private val songDao: SongDao
) : ViewModel() {

    private val _hasLoaded = MutableStateFlow(false)
    val hasLoaded: StateFlow<Boolean> = _hasLoaded.asStateFlow()

    // Observe Room database directly for instant UI updates
    // Sort using TextDisplayUtils.getSortKey to handle leading quotes/punctuation properly
    val allArtists: StateFlow<List<MediaData.Artist>> = artistDao.getAllArtists()
        .onEach { _hasLoaded.value = true }
        .map { entities ->
            entities.map { it.toMediaDataArtist() }
                .sortedBy { TextDisplayUtils.getSortKey(it.name) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedArtist = MutableStateFlow<MediaData.Artist?>(null)
    val selectedArtist: StateFlow<MediaData.Artist?> = _selectedArtist.asStateFlow()

    private val _artistAlbums = MutableStateFlow<List<MediaItem>>(emptyList())
    val artistAlbums: StateFlow<List<MediaItem>> = _artistAlbums.asStateFlow()

    private val _artistSongs = MutableStateFlow<List<MediaItem>>(emptyList())
    val artistSongs: StateFlow<List<MediaItem>> = _artistSongs.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _artistDetailsLoaded = MutableStateFlow(false)
    val artistDetailsLoaded: StateFlow<Boolean> = _artistDetailsLoaded.asStateFlow()

    private var selectedArtistJob: Job? = null

    init {
        // Load cached data instantly, sync in background (once per day)
        viewModelScope.launch {
            try {
                // Skip auto-sync if cache was just cleared
                if (syncRepository.wasJustCleared()) {
                    return@launch
                }

                if (!syncRepository.hasCachedData()) {
                    _isLoading.value = true
                    syncRepository.syncAll()
                    _isLoading.value = false
                } else if (syncRepository.shouldSyncToday()) {
                    launch { syncRepository.syncAll() }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _isLoading.value = false
            }
        }

    }

    fun refreshArtists() {
        viewModelScope.launch {
            try {
                syncRepository.syncAll(forceRefresh = true)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun getAlbum(id: String): List<MediaItem> {
        return albumRepository.getAlbum(id) ?: emptyList()
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    @OptIn(FlowPreview::class)
    val searchResults: StateFlow<List<MediaData.Artist>> = searchQuery
        .debounce(300L)
        .combine(allArtists) { query, artists ->
            if (query.isBlank()) {
                emptyList()
            } else {
                artists.filter { artist ->
                    artist.name.contains(query, ignoreCase = true)
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    suspend fun getSongsForArtists(artists: List<MediaData.Artist>): List<MediaItem> {
        // Use batch query to avoid N+1 problem
        val localArtists = artists.filter { it.navidromeID.startsWith("Local_") }
        val navidromeArtists = artists.filter { !it.navidromeID.startsWith("Local_") }

        val songs = mutableListOf<MediaItem>()

        // Get cached songs from Room for Navidrome artists
        if (navidromeArtists.isNotEmpty()) {
            val artistIds = navidromeArtists.map { it.navidromeID }
            val cachedSongs = songDao.getSongsByArtistIds(artistIds)
            songs.addAll(cachedSongs.map { it.toMediaDataSong().toMediaItem() })
        }

        // Fallback to repository for local artists
        for (artist in localArtists) {
            val albums = artistRepository.getArtistAlbums(artist.navidromeID)
            for (album in albums) {
                val albumId = album.mediaMetadata.extras?.getString("navidromeID")
                    ?: album.mediaId
                val albumSongs = albumRepository.getAlbum(albumId).orEmpty()
                songs.addAll(
                    albumSongs.filter {
                        it.mediaMetadata.mediaType != MediaMetadata.MEDIA_TYPE_ALBUM
                    }
                )
            }
        }

        return songs
    }

    private suspend fun getSongsForArtist(artist: MediaData.Artist): List<MediaItem> {
        val songs = if (artist.navidromeID.startsWith("Local_")) {
            getSongsForArtists(listOf(artist))
        } else {
            songDao.getSongsByArtistIdentity(artist.navidromeID, artist.name)
                .map { it.toMediaDataSong().toMediaItem() }
        }
        return songs.distinctBy { song ->
            song.mediaMetadata.extras?.getString("navidromeID")
                ?.takeIf { it.isNotBlank() }
                ?: song.mediaId
        }
    }

    fun setSelectedArtist(artist: MediaData.Artist) {
        val currentArtist = _selectedArtist.value
        val sameArtist = currentArtist?.navidromeID == artist.navidromeID &&
            currentArtist.name == artist.name
        if (
            sameArtist &&
            (selectedArtistJob?.isActive == true ||
                _artistAlbums.value.isNotEmpty() ||
                _artistSongs.value.isNotEmpty())
        ) {
            return
        }

        selectedArtistJob?.cancel()
        _artistDetailsLoaded.value = false
        _selectedArtist.value = artist
        _artistAlbums.value = emptyList()
        _artistSongs.value = emptyList()
        selectedArtistJob = viewModelScope.launch {
            val loadingJob = launch {
                delay(1000)
                if (_artistAlbums.value.isEmpty()) {
                    _isLoading.value = true
                }
            }
            try {
                val albums = artistRepository.getArtistAlbums(
                    artistId = artist.navidromeID,
                    artistName = artist.name
                )
                _artistAlbums.value = albums
                _artistSongs.value = getSongsForArtist(artist)

                try {
                    val artistDetails = artistRepository.getArtistInfo(artist.navidromeID)
                    _selectedArtist.value = _selectedArtist.value?.copy(
                        description = artistDetails?.biography ?: "",
                        musicBrainzId = artistDetails?.musicBrainzId,
                        similarArtist = artistDetails?.similarArtist
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                loadingJob.cancel()
                _isLoading.value = false
                _artistDetailsLoaded.value = true
            }
        }
    }

    fun selectArtistById(artistId: String, artistName: String) {
        if (artistId.isBlank()) return
        viewModelScope.launch {
            val artist = artistDao.getArtistById(artistId)?.toMediaDataArtist()
                ?: MediaData.Artist(navidromeID = artistId, name = artistName)
            setSelectedArtist(artist)
        }
    }
}
