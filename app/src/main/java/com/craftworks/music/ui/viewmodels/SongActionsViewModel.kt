package com.craftworks.music.ui.viewmodels

import androidx.lifecycle.ViewModel
import android.net.Uri
import androidx.media3.common.MediaItem
import com.craftworks.music.data.model.DiscoveryMixMode
import com.craftworks.music.data.repository.SongRepository
import com.craftworks.music.data.repository.StarredRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SongActionsViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val starredRepository: StarredRepository
) : ViewModel() {
    suspend fun setFavorite(song: MediaItem, favorite: Boolean): Boolean {
        val itemId = song.mediaMetadata.extras?.getString("navidromeID")
            ?.takeIf { it.isNotBlank() }
            ?: return false
        return if (favorite) {
            starredRepository.starItem(itemId, ignoreCachedResponse = true)
        } else {
            starredRepository.unStarItem(itemId, ignoreCachedResponse = true)
        }
    }

    suspend fun buildInstantMix(seed: MediaItem): List<MediaItem> {
        return buildRadio(listOf(seed))
    }

    suspend fun buildRadio(seeds: List<MediaItem>): List<MediaItem> {
        return songRepository.getRadioMix(seeds)
    }

    suspend fun hideSong(song: MediaItem): Boolean = songRepository.hideSong(song)

    suspend fun replaceSongFile(song: MediaItem, uri: Uri): Boolean =
        songRepository.replaceSongFile(song, uri)

    suspend fun buildDiscoveryMix(
        mode: DiscoveryMixMode,
		count: Int = 50,
		intent: String = ""
    ): List<MediaItem> {
		return songRepository.getDiscoveryMix(count, mode, intent = intent)
    }
}
