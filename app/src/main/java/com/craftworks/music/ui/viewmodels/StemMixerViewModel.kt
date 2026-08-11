package com.craftworks.music.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.craftworks.music.data.datasource.navidrome.NavidromeDataSource
import com.craftworks.music.providers.navidrome.StemSplitResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class StemMixerUiState(
    val songId: String = "",
    val status: String = "idle",
    val progress: Int = 0,
    val message: String = "",
    val stemUrls: Map<String, String> = emptyMap(),
    val profile: String = "4stems",
    val bitrate: Int = 256
) {
    val isReady: Boolean get() = status == "ready" && stemUrls.isNotEmpty()
    val isWorking: Boolean get() = status in setOf("checking", "queued", "uploading", "splitting", "finalizing")
    val isFailure: Boolean get() = status == "failed" || status == "disabled"
}

@HiltViewModel
class StemMixerViewModel @Inject constructor(
    private val navidromeDataSource: NavidromeDataSource
) : ViewModel() {
    private val _state = MutableStateFlow(StemMixerUiState())
    val state: StateFlow<StemMixerUiState> = _state.asStateFlow()
    private var preparationJob: Job? = null

    fun check(songId: String, profile: String = "4stems", bitrate: Int = 256) {
        if (songId.isBlank()) {
            _state.value = StemMixerUiState(
                status = "failed",
                message = "This item cannot be split"
            )
            return
        }
        if (_state.value.songId == songId && _state.value.profile == profile &&
            _state.value.bitrate == bitrate && (_state.value.isWorking || _state.value.isReady)
        ) return

        preparationJob?.cancel()
        preparationJob = viewModelScope.launch {
            _state.value = StemMixerUiState(
                songId = songId,
                status = "checking",
                progress = 0,
                message = "Checking for prepared stems",
                profile = profile,
                bitrate = bitrate
            )
            try {
                val response = navidromeDataSource.getStemSplitStatus(songId, profile, bitrate)
                publish(songId, response)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _state.value = StemMixerUiState(
                    songId = songId,
                    status = "failed",
                    message = error.message ?: "Could not check song stems",
                    profile = profile,
                    bitrate = bitrate
                )
            }
        }
    }

    fun start(songId: String, profile: String, bitrate: Int) {
        preparationJob?.cancel()
        preparationJob = viewModelScope.launch {
            _state.value = StemMixerUiState(
                songId = songId,
                status = "checking",
                message = "Checking the selected stem cache",
                profile = profile,
                bitrate = bitrate
            )
            try {
                var response = navidromeDataSource.getStemSplitStatus(songId, profile, bitrate)
                if (!response.isReady) {
                    response = navidromeDataSource.startStemSplit(songId, profile, bitrate)
                }
                publish(songId, response)
                while (isActive && !response.isReady && !response.isTerminalFailure) {
                    delay(1_500L)
                    response = navidromeDataSource.getStemSplitStatus(songId, profile, bitrate)
                    publish(songId, response)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _state.value = StemMixerUiState(
                    songId = songId,
                    status = "failed",
                    message = error.message ?: "Could not prepare song stems",
                    profile = profile,
                    bitrate = bitrate
                )
            }
        }
    }

    fun retryCheck() {
        val current = _state.value
        check(current.songId, current.profile, current.bitrate)
    }

    fun showConfiguration() {
        val current = _state.value
        preparationJob?.cancel()
        _state.value = current.copy(status = "idle", progress = 0, message = "")
    }

    private fun publish(songId: String, response: StemSplitResponse) {
        val urls = if (response.isReady) {
            try {
                navidromeDataSource.buildStemStreamUrls(
                    songId = songId,
                    stems = response.stem,
                    profile = response.profile,
                    bitrate = response.bitrate
                )
            } catch (error: IllegalArgumentException) {
                throw IOException(error.message ?: "Cannot build stem stream URLs", error)
            }
        } else {
            emptyMap()
        }
        _state.value = StemMixerUiState(
            songId = songId,
            status = response.status,
            progress = response.progress.coerceIn(0, 100),
            message = response.message,
            stemUrls = urls,
            profile = response.profile,
            bitrate = response.bitrate
        )
    }
}
