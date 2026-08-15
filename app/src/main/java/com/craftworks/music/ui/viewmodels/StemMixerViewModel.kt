package com.craftworks.music.ui.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.craftworks.music.data.datasource.navidrome.NavidromeDataSource
import com.craftworks.music.providers.navidrome.StemSplitResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min

data class StemMixerSelection(
    val profile: String = "4stems",
    val bitrate: Int = 256
)

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
    private val navidromeDataSource: NavidromeDataSource,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val restoredSongId = savedStateHandle.get<String>(KEY_SONG_ID).orEmpty()
    private val restoredProfile = normalizedProfile(savedStateHandle[KEY_PROFILE])
    private val restoredBitrate = normalizedBitrate(savedStateHandle[KEY_BITRATE])
    private val _state = MutableStateFlow(
        StemMixerUiState(
            songId = restoredSongId,
            profile = restoredProfile,
            bitrate = restoredBitrate
        )
    )
    val state: StateFlow<StemMixerUiState> = _state.asStateFlow()
    private var preparationJob: Job? = null

    fun selectionFor(songId: String): StemMixerSelection =
        if (songId.isNotBlank() && songId == savedStateHandle.get<String>(KEY_SONG_ID)) {
            StemMixerSelection(restoredProfile = savedStateHandle[KEY_PROFILE], restoredBitrate = savedStateHandle[KEY_BITRATE])
        } else {
            StemMixerSelection()
        }

    fun check(songId: String, profile: String = "4stems", bitrate: Int = 256) {
        if (songId.isBlank()) {
            _state.value = StemMixerUiState(
                status = "failed",
                message = "This item cannot be split"
            )
            return
        }
        val normalizedProfile = normalizedProfile(profile)
        val normalizedBitrate = normalizedBitrate(bitrate)
        if (_state.value.songId == songId && _state.value.profile == normalizedProfile &&
            _state.value.bitrate == normalizedBitrate &&
            (_state.value.isReady || (_state.value.isWorking && preparationJob?.isActive == true))
        ) return

        val resumeStartedSplit = wasSplitActive(songId, normalizedProfile, normalizedBitrate)

        preparationJob?.cancel()
        preparationJob = viewModelScope.launch {
            rememberSelection(songId, normalizedProfile, normalizedBitrate, resumeStartedSplit)
            _state.value = StemMixerUiState(
                songId = songId,
                status = "checking",
                progress = 0,
                message = "Checking for prepared stems",
                profile = normalizedProfile,
                bitrate = normalizedBitrate
            )
            try {
                var response = requestStatusWithRetry(
                    songId = songId,
                    profile = normalizedProfile,
                    bitrate = normalizedBitrate,
                    keepRetrying = resumeStartedSplit
                )
                if (resumeStartedSplit && response.status == "idle") {
                    response = navidromeDataSource.startStemSplit(songId, normalizedProfile, normalizedBitrate)
                }
                pollUntilTerminal(songId, normalizedProfile, normalizedBitrate, response)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _state.value = StemMixerUiState(
                    songId = songId,
                    status = "failed",
                    message = error.message ?: "Could not check song stems",
                    profile = normalizedProfile,
                    bitrate = normalizedBitrate
                )
            }
        }
    }

    fun start(songId: String, profile: String, bitrate: Int) {
        val normalizedProfile = normalizedProfile(profile)
        val normalizedBitrate = normalizedBitrate(bitrate)
        preparationJob?.cancel()
        preparationJob = viewModelScope.launch {
            rememberSelection(songId, normalizedProfile, normalizedBitrate, active = true)
            _state.value = StemMixerUiState(
                songId = songId,
                status = "checking",
                message = "Checking the selected stem cache",
                profile = normalizedProfile,
                bitrate = normalizedBitrate
            )
            try {
                var response = requestStatusWithRetry(
                    songId = songId,
                    profile = normalizedProfile,
                    bitrate = normalizedBitrate,
                    keepRetrying = true
                )
                if (!response.isReady) {
                    response = navidromeDataSource.startStemSplit(songId, normalizedProfile, normalizedBitrate)
                }
                pollUntilTerminal(songId, normalizedProfile, normalizedBitrate, response)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _state.value = StemMixerUiState(
                    songId = songId,
                    status = "failed",
                    message = error.message ?: "Could not prepare song stems",
                    profile = normalizedProfile,
                    bitrate = normalizedBitrate
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
        rememberSelection(current.songId, current.profile, current.bitrate, active = false)
        _state.value = current.copy(status = "idle", progress = 0, message = "")
    }

    private suspend fun pollUntilTerminal(
        songId: String,
        profile: String,
        bitrate: Int,
        initialResponse: StemSplitResponse
    ) {
        var response = initialResponse
        publish(songId, response, profile, bitrate)
        while (currentCoroutineContext().isActive && response.isWorking) {
            delay(POLL_INTERVAL_MS)
            response = requestStatusWithRetry(songId, profile, bitrate, keepRetrying = true)
            publish(songId, response, profile, bitrate)
        }
    }

    private suspend fun requestStatusWithRetry(
        songId: String,
        profile: String,
        bitrate: Int,
        keepRetrying: Boolean
    ): StemSplitResponse {
        var failures = 0
        while (true) {
            try {
                return navidromeDataSource.getStemSplitStatus(songId, profile, bitrate)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                failures += 1
                if (!keepRetrying && failures >= FOREGROUND_CHECK_ATTEMPTS) throw error
                _state.value = _state.value.copy(
                    status = "checking",
                    message = "Connection interrupted. Retrying stem status"
                )
                delay(min(POLL_RETRY_MAX_MS, POLL_INTERVAL_MS * failures))
            }
        }
    }

    private fun publish(
        songId: String,
        response: StemSplitResponse,
        requestedProfile: String,
        requestedBitrate: Int
    ) {
        val profile = normalizedProfile(response.profile.ifBlank { requestedProfile })
        val bitrate = normalizedBitrate(response.bitrate.takeIf { it > 0 } ?: requestedBitrate)
        val urls = if (response.isReady) {
            try {
                navidromeDataSource.buildStemStreamUrls(
                    songId = songId,
                    stems = response.stem,
                    profile = profile,
                    bitrate = bitrate
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
            profile = profile,
            bitrate = bitrate
        )
        rememberSelection(songId, profile, bitrate, active = response.isWorking)
    }

    private fun rememberSelection(songId: String, profile: String, bitrate: Int, active: Boolean) {
        savedStateHandle[KEY_SONG_ID] = songId
        savedStateHandle[KEY_PROFILE] = normalizedProfile(profile)
        savedStateHandle[KEY_BITRATE] = normalizedBitrate(bitrate)
        savedStateHandle[KEY_ACTIVE] = active
    }

    private fun wasSplitActive(songId: String, profile: String, bitrate: Int): Boolean =
        savedStateHandle.get<Boolean>(KEY_ACTIVE) == true &&
            savedStateHandle.get<String>(KEY_SONG_ID) == songId &&
            normalizedProfile(savedStateHandle[KEY_PROFILE]) == profile &&
            normalizedBitrate(savedStateHandle[KEY_BITRATE]) == bitrate

    private companion object {
        const val KEY_SONG_ID = "stem_mixer_song_id"
        const val KEY_PROFILE = "stem_mixer_profile"
        const val KEY_BITRATE = "stem_mixer_bitrate"
        const val KEY_ACTIVE = "stem_mixer_active"
        const val POLL_INTERVAL_MS = 1_500L
        const val POLL_RETRY_MAX_MS = 15_000L
        const val FOREGROUND_CHECK_ATTEMPTS = 3
    }
}

internal fun StemMixerSelection(
    restoredProfile: String?,
    restoredBitrate: Int?
): StemMixerSelection = StemMixerSelection(
    profile = normalizedProfile(restoredProfile),
    bitrate = normalizedBitrate(restoredBitrate)
)

internal fun normalizedProfile(profile: String?): String =
    profile?.takeIf { it == "2stems" || it == "4stems" } ?: "4stems"

internal fun normalizedBitrate(bitrate: Int?): Int =
    bitrate?.takeIf { it == 192 || it == 256 || it == 320 } ?: 256
