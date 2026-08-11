package com.craftworks.music.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.craftworks.music.data.repository.PlaylistRepository
import com.craftworks.music.data.repository.StarredRepository
import com.craftworks.music.managers.DataRefreshManager
import com.craftworks.music.providers.local.localPlaylistImageGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistScreenViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val playlistRepository: PlaylistRepository,
    private val starredRepository: StarredRepository
) : ViewModel() {
    private val _allPlaylists = MutableStateFlow<List<MediaItem>>(emptyList())
    val allPlaylists: StateFlow<List<MediaItem>> = _allPlaylists.asStateFlow()

    private val _selectedPlaylist = MutableStateFlow<MediaItem?>(null)
    val selectedPlaylist: StateFlow<MediaItem?> = _selectedPlaylist.asStateFlow()

    private val _selectedPlaylistSongs = MutableStateFlow<List<MediaItem>>(emptyList())
    val selectedPlaylistSongs: StateFlow<List<MediaItem>> = _selectedPlaylistSongs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hasLoaded = MutableStateFlow(false)
    val hasLoaded: StateFlow<Boolean> = _hasLoaded.asStateFlow()

    private val _playlistDetailsLoaded = MutableStateFlow(false)
    val playlistDetailsLoaded: StateFlow<Boolean> = _playlistDetailsLoaded.asStateFlow()

    private var playlistDetailsJob: Job? = null

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    @OptIn(FlowPreview::class)
    val searchResults: StateFlow<List<MediaItem>> = searchQuery
        .debounce(300L)
        .combine(allPlaylists) { query, playlists ->
            if (query.isBlank()) {
                emptyList()
            } else {
                playlists.filter { playlist ->
                    playlist.mediaMetadata.title?.toString()?.contains(query, ignoreCase = true) == true
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        loadPlaylists()

        viewModelScope.launch {
            try {
                DataRefreshManager.dataSourceChangedEvent.collect {
                    loadPlaylists()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadPlaylists() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _allPlaylists.value = playlistRepository.getPlaylists(true)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
                _hasLoaded.value = true
            }
        }
    }

    fun setCurrentPlaylist(playlist: MediaItem){
        playlistDetailsJob?.cancel()
        _playlistDetailsLoaded.value = false
        _selectedPlaylistSongs.value = emptyList<MediaItem>()
        _selectedPlaylist.value = playlist
        fetchPlaylistDetails() // Fetch details when playlist is set
    }

    suspend fun updatePlaylistsImages() {
        _allPlaylists.value = _allPlaylists.value.map {
            if (it.mediaMetadata.extras?.getString("navidromeID")?.startsWith("Local_") == true && it.mediaMetadata.artworkData == null) {
                val songs = playlistRepository.getPlaylistSongs(it.mediaMetadata.extras?.getString("navidromeID") ?: "", true)
                it.buildUpon()
                    .setMediaMetadata(
                        it.mediaMetadata.buildUpon()
                            .setArtworkData(localPlaylistImageGenerator(songs, appContext), MediaMetadata.PICTURE_TYPE_OTHER)
                            .build()
                    )
                    .build()
            } else {
                it
            }
        }
    }

    fun fetchPlaylistDetails() {
        if (_selectedPlaylist.value == null) {
            _playlistDetailsLoaded.value = true
            return
        }

        val playlistId = _selectedPlaylist.value?.mediaMetadata?.extras?.getString("navidromeID")
        if (playlistId == null) {
            _playlistDetailsLoaded.value = true
            return
        }

        println("Fetching playlist details for playlist ID: $playlistId")

        playlistDetailsJob = viewModelScope.launch {
            val loadingJob = launch {
                delay(1000)
                if (_selectedPlaylistSongs.value.isEmpty()) {
                    _isLoading.value = true
                }
            }
            try {
                _selectedPlaylistSongs.value = if (playlistId == "favourites") {
                    starredRepository.getStarredItems(ignoreCachedResponse = true)
                } else {
                    playlistRepository.getPlaylistSongs(playlistId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                loadingJob.cancel()
                _isLoading.value = false
                _playlistDetailsLoaded.value = true
            }
        }
    }

    fun createPlaylist(name: String, songstoAdd: String, addToNavidrome: Boolean) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                playlistRepository.createPlaylist(name, songstoAdd, addToNavidrome)
                updatePlaylistsImages()
                loadPlaylists()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    suspend fun createDiscoveryPlaylist(
        baseName: String,
        songs: List<MediaItem>
    ): String? {
        val songIds = songs.mapNotNull { song ->
            song.mediaMetadata.extras?.getString("navidromeID")
                ?.takeUnless { it.startsWith("Local_") }
        }.distinct()
        if (songIds.isEmpty()) return null

        val playlistName = uniquePlaylistName(
            baseName = "$baseName Mix",
            existingNames = _allPlaylists.value.mapNotNull {
                it.mediaMetadata.title?.toString()
            }.toSet()
        )

        _isLoading.value = true
        return try {
            if (!playlistRepository.createNavidromePlaylist(playlistName, songIds)) {
                null
            } else {
                _allPlaylists.value = playlistRepository.getPlaylists(true)
                playlistName
            }
        } catch (error: Exception) {
            error.printStackTrace()
            null
        } finally {
            _isLoading.value = false
        }
    }

    fun addSongToPlaylist(playlistId: String, songId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                playlistRepository.addSongToPlaylist(playlistId, songId)
                loadPlaylists()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                playlistRepository.deletePlaylist(playlistId)
                loadPlaylists()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
}

internal fun uniquePlaylistName(baseName: String, existingNames: Set<String>): String {
    if (baseName !in existingNames) return baseName
    var suffix = 2
    while ("$baseName $suffix" in existingNames) suffix += 1
    return "$baseName $suffix"
}
