package com.craftworks.music.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import com.craftworks.music.data.repository.AlbumRepository
import com.craftworks.music.data.repository.SongRepository
import com.craftworks.music.managers.DataRefreshManager
import com.craftworks.music.managers.NavidromeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeScreenViewModel @Inject constructor(
    private val albumRepository: AlbumRepository,
    private val songRepository: SongRepository
) : ViewModel() {
    private val _recentlyPlayedAlbums = MutableStateFlow<List<MediaItem>>(emptyList())
    val recentlyPlayedAlbums: StateFlow<List<MediaItem>> = _recentlyPlayedAlbums.asStateFlow()

    private val _recentAlbums = MutableStateFlow<List<MediaItem>>(emptyList())
    val recentAlbums: StateFlow<List<MediaItem>> = _recentAlbums.asStateFlow()

    private val _mostPlayedAlbums = MutableStateFlow<List<MediaItem>>(emptyList())
    val mostPlayedAlbums: StateFlow<List<MediaItem>> = _mostPlayedAlbums.asStateFlow()

    private val _shuffledAlbums = MutableStateFlow<List<MediaItem>>(emptyList())
    val shuffledAlbums: StateFlow<List<MediaItem>> = _shuffledAlbums.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _discoveryMixState = MutableStateFlow<DiscoveryMixState>(DiscoveryMixState.Idle)
    val discoveryMixState: StateFlow<DiscoveryMixState> = _discoveryMixState.asStateFlow()

    // Track active load job to prevent redundant concurrent loads
    private var loadJob: Job? = null
    private var loadGeneration = 0L

    init {
        loadHomeScreenData()

        // Separate coroutines for each flow to prevent blocking
        viewModelScope.launch {
            try {
                DataRefreshManager.dataSourceChangedEvent.collect {
                    loadHomeScreenData()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("HomeScreenViewModel", "Data-source refresh observer failed", e)
            }
        }

        viewModelScope.launch {
            try {
                combine(
                    NavidromeManager.currentServerId,
                    NavidromeManager.libraries
                ) { serverId, libs -> serverId to libs }
                    .distinctUntilChanged()
                    .collect { (serverId, libs) ->
                        if (serverId != null && libs.isNotEmpty()) {
                            loadHomeScreenData()
                        }
                    }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("HomeScreenViewModel", "Provider observer failed", e)
            }
        }
    }

    fun loadHomeScreenData(forceRefresh: Boolean = false) {
        val generation = ++loadGeneration
        // Cancel any existing load to prevent redundant concurrent loads
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                coroutineScope {
                    // Use cache by default, only bypass on explicit refresh
                    val recentlyPlayedDeferred = async { albumRepository.getAlbums("recent", 20, 0, forceRefresh) }
                    val recentDeferred = async { albumRepository.getAlbums("newest", 20, 0, forceRefresh) }
                    val mostPlayedDeferred = async { albumRepository.getAlbums("frequent", 20, 0, forceRefresh) }
                    val shuffledDeferred = async { albumRepository.getAlbums("random", 20, 0, forceRefresh) }

                    _recentlyPlayedAlbums.value = recentlyPlayedDeferred.await()
                    _recentAlbums.value = recentDeferred.await()
                    _mostPlayedAlbums.value = mostPlayedDeferred.await()
                    _shuffledAlbums.value = shuffledDeferred.await()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("HomeScreenViewModel", "Failed to load home data", e)
            } finally {
                if (generation == loadGeneration) {
                    _isLoading.value = false
                }
            }
        }
    }

    suspend fun getAlbumSongs(albumId: String): List<MediaItem> {
        return albumRepository.getAlbum(albumId) ?: emptyList()
    }

    fun buildDiscoveryMix() {
        if (_discoveryMixState.value is DiscoveryMixState.Loading) return

        viewModelScope.launch {
            _discoveryMixState.value = DiscoveryMixState.Loading
            try {
                val songs = songRepository.getDiscoveryMix(DISCOVERY_MIX_SIZE)
                _discoveryMixState.value = if (songs.isEmpty()) {
                    DiscoveryMixState.Empty
                } else {
                    DiscoveryMixState.Ready(songs)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("HomeScreenViewModel", "Could not build discovery mix", e)
                _discoveryMixState.value = DiscoveryMixState.Error
            }
        }
    }

    fun consumeDiscoveryMix() {
        if (_discoveryMixState.value is DiscoveryMixState.Ready) {
            _discoveryMixState.value = DiscoveryMixState.Idle
        }
    }

    companion object {
        const val DISCOVERY_MIX_SIZE = 50
    }
}

sealed interface DiscoveryMixState {
    data object Idle : DiscoveryMixState
    data object Loading : DiscoveryMixState
    data class Ready(val songs: List<MediaItem>) : DiscoveryMixState
    data object Empty : DiscoveryMixState
    data object Error : DiscoveryMixState
}
