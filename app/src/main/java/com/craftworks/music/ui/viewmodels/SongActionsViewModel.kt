package com.craftworks.music.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import com.craftworks.music.data.repository.SongRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SongActionsViewModel @Inject constructor(
    private val songRepository: SongRepository
) : ViewModel() {
    suspend fun buildInstantMix(seed: MediaItem): List<MediaItem> {
        return songRepository.getInstantMix(seed)
    }
}
