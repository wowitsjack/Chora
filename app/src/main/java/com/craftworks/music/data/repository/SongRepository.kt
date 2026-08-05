package com.craftworks.music.data.repository

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.craftworks.music.data.database.dao.SongDao
import com.craftworks.music.data.database.entity.toMediaDataSong
import com.craftworks.music.data.model.MediaCategory
import com.craftworks.music.data.datasource.local.LocalDataSource
import com.craftworks.music.data.datasource.navidrome.NavidromeDataSource
import com.craftworks.music.data.model.toMediaItem
import com.craftworks.music.managers.LocalProviderManager
import com.craftworks.music.managers.NavidromeManager
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SongRepository @Inject constructor(
    private val localDataSource: LocalDataSource,
    private val navidromeDataSource: NavidromeDataSource,
    private val songDao: SongDao
) {

    suspend fun getSongs(
        query: String? = "",
        songCount: Int = 100,
        songOffset: Int = 0,
        ignoreCachedResponse: Boolean = false
    ): List<MediaItem> = supervisorScope {
        val deferredSongs = mutableListOf<Deferred<List<MediaItem>>>()

        if (LocalProviderManager.checkActiveFolders())
            if (query.isNullOrEmpty() && songOffset == 0)
                deferredSongs.add(async {
                    try {
                        localDataSource.getLocalSongs()
                    } catch (e: Exception) {
                        Log.e("SongRepository", "Failed to fetch local songs", e)
                        emptyList()
                    }
                })

        if (NavidromeManager.checkActiveServers())
            deferredSongs.add(async {
                try {
                    val libraryIds = NavidromeManager.getEnabledLibraryIdsForCurrentServer(MediaCategory.MUSIC)
                    if (libraryIds.isEmpty()) emptyList() else navidromeDataSource.getNavidromeSongs(
                        query,
                        songCount,
                        songOffset,
                        ignoreCachedResponse,
                        musicFolderIds = libraryIds
                    ).filter { it.mediaMetadata.extras?.getString("mediaCategory") != MediaCategory.AUDIOBOOK }
                } catch (e: Exception) {
                    Log.e("SongRepository", "Failed to fetch Navidrome songs", e)
                    emptyList()
                }
            })

        deferredSongs.awaitAll().flatten()
    }

    suspend fun getSong(songId: String, ignoreCachedResponse: Boolean = false): MediaItem? = supervisorScope {
        if (songId.startsWith("Local_"))
            async { localDataSource.getLocalSong(songId) }.await()
        else
            async { navidromeDataSource.getNavidromeSong(songId, ignoreCachedResponse) }.await()
    }

    suspend fun searchSongs(query: String, ignoreCachedResponse: Boolean = false): List<MediaItem> = supervisorScope {
        val deferredSongs = mutableListOf<Deferred<List<MediaItem>>>()

        if (LocalProviderManager.checkActiveFolders())
            deferredSongs.add(async {
                try {
                    localDataSource.searchLocalSongs(query)
                } catch (e: Exception) {
                    Log.e("SongRepository", "Failed to search local songs", e)
                    emptyList()
                }
            })

        if (NavidromeManager.checkActiveServers())
            deferredSongs.add(async {
                try {
                    val libraryIds = NavidromeManager.getEnabledLibraryIdsForCurrentServer(MediaCategory.MUSIC)
                    if (libraryIds.isEmpty()) emptyList() else navidromeDataSource.getNavidromeSongs(
                        query,
                        ignoreCachedResponse = ignoreCachedResponse,
                        musicFolderIds = libraryIds
                    ).filter { it.mediaMetadata.extras?.getString("mediaCategory") != MediaCategory.AUDIOBOOK }
                } catch (e: Exception) {
                    Log.e("SongRepository", "Failed to search Navidrome songs", e)
                    emptyList()
                }
            })

        deferredSongs.awaitAll().flatten()
    }

    suspend fun scrobbleSong(songId: String, submission: Boolean) {
        if (songId.startsWith("Local_"))
            return

        navidromeDataSource.scrobbleSong(songId, submission)
    }

    suspend fun getRandomSongs(
        size: Int = 50,
        ignoreCachedResponse: Boolean = true
    ): List<MediaItem> = supervisorScope {
        val deferredSongs = mutableListOf<Deferred<List<MediaItem>>>()

        if (LocalProviderManager.checkActiveFolders())
            deferredSongs.add(async {
                try {
                    localDataSource.getLocalSongs().shuffled().take(size / 2)
                } catch (e: Exception) {
                    Log.e("SongRepository", "Failed to get random local songs", e)
                    emptyList()
                }
            })

        if (NavidromeManager.checkActiveServers())
            deferredSongs.add(async {
                try {
                    val libraryIds = NavidromeManager.getEnabledLibraryIdsForCurrentServer(MediaCategory.MUSIC)
                    if (libraryIds.isEmpty()) emptyList() else navidromeDataSource.getRandomSongs(
                        size,
                        ignoreCachedResponse,
                        musicFolderIds = libraryIds
                    ).filter { it.mediaMetadata.extras?.getString("mediaCategory") != MediaCategory.AUDIOBOOK }
                } catch (e: Exception) {
                    Log.e("SongRepository", "Failed to get random Navidrome songs", e)
                    emptyList()
                }
            })

        boundedDistinctSongs(deferredSongs.awaitAll().flatten().shuffled(), size)
    }

    suspend fun getDiscoveryMix(size: Int = 50): List<MediaItem> {
        val outputLimit = size.coerceIn(0, DISCOVERY_OUTPUT_LIMIT)
        if (outputLimit == 0) return emptyList()

        val liveSongs = getRandomSongs(DISCOVERY_CANDIDATE_LIMIT)
        if (liveSongs.isNotEmpty()) {
            return SmartDjSequencer.sequence(
                liveSongs,
                outputLimit = outputLimit,
                candidateLimit = DISCOVERY_CANDIDATE_LIMIT
            )
        }

        return SmartDjSequencer.sequence(
            songDao.getRandomSongsOnce(DISCOVERY_CANDIDATE_LIMIT)
                .map { it.toMediaDataSong().toMediaItem() },
            outputLimit = outputLimit,
            candidateLimit = DISCOVERY_CANDIDATE_LIMIT
        )
    }

    suspend fun getInstantMix(seed: MediaItem, count: Int = 100): List<MediaItem> {
        if (seed.isAudiobook()) return emptyList()

        val seedId = seed.mediaMetadata.extras?.getString("navidromeID")
            ?.takeUnless { it.startsWith("Local_") }
            ?: return emptyList()
        val similarSongs = navidromeDataSource.getSimilarSongs(seedId, count)
            .filterNot { it.isAudiobook() }
        return boundedDistinctSongs(listOf(seed) + similarSongs, count + 1)
    }
}

private fun MediaItem.isAudiobook(): Boolean {
    val metadata = mediaMetadata
    if (
        metadata.mediaType == MediaMetadata.MEDIA_TYPE_AUDIO_BOOK ||
        metadata.mediaType == MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER
    ) {
        return true
    }

    return metadata.extras
        ?.getString("mediaCategory")
        .equals(MediaCategory.AUDIOBOOK, ignoreCase = true)
}

internal fun boundedDistinctSongs(songs: List<MediaItem>, limit: Int): List<MediaItem> {
    if (limit <= 0) return emptyList()
    return songs.distinctBy { song ->
        song.mediaMetadata.extras?.getString("navidromeID") ?: song.mediaId
    }.take(limit)
}
