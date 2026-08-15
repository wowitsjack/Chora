package com.craftworks.music.data.repository

import android.util.Log
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.craftworks.music.data.database.dao.SongDao
import com.craftworks.music.data.database.entity.toMediaDataSong
import com.craftworks.music.data.model.MediaCategory
import com.craftworks.music.data.model.DiscoveryMixMode
import com.craftworks.music.data.model.DiscoveryMixRequest
import com.craftworks.music.data.datasource.local.LocalDataSource
import com.craftworks.music.data.datasource.navidrome.NavidromeDataSource
import com.craftworks.music.data.model.toMediaItem
import com.craftworks.music.managers.LocalProviderManager
import com.craftworks.music.managers.NavidromeManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SongRepository @Inject constructor(
    private val localDataSource: LocalDataSource,
    private val navidromeDataSource: NavidromeDataSource,
    private val songDao: SongDao
) {
    private val discoveryHistoryMutex = Mutex()
    private val recentDiscoveryIds = mutableMapOf<DiscoveryHistoryKey, ArrayDeque<String>>()

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

        withoutHidden(deferredSongs.awaitAll().flatten())
    }

    suspend fun getSong(songId: String, ignoreCachedResponse: Boolean = false): MediaItem? = supervisorScope {
        if (songDao.isSongHidden(songId)) {
            null
        } else if (songId.startsWith("Local_"))
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
                    if (e is CancellationException) throw e
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
                    if (e is CancellationException) throw e
                    Log.e("SongRepository", "Failed to search Navidrome songs", e)
                    emptyList()
                }
            })

        withoutHidden(deferredSongs.awaitAll().flatten())
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

        boundedDistinctSongs(withoutHidden(deferredSongs.awaitAll().flatten()).shuffled(), size)
    }

    suspend fun getDiscoveryMix(
        size: Int = 50,
        mode: DiscoveryMixMode = DiscoveryMixMode.SMART,
        mood: String? = null,
        intent: String? = null
    ): List<MediaItem> {
        val outputLimit = size.coerceIn(0, DISCOVERY_OUTPUT_LIMIT)
        if (outputLimit == 0) return emptyList()

        val normalizedIntent = intent?.trim()?.lowercase().orEmpty()
        val historyKey = DiscoveryHistoryKey(mode, normalizedIntent)

        val excludedIds = discoveryHistoryMutex.withLock {
            recentDiscoveryIds[historyKey].orEmpty().takeLast(DISCOVERY_HISTORY_LIMIT)
        }

        if (NavidromeManager.checkActiveServers()) {
            val analyzerMix = runCatching {
                navidromeDataSource.getSmartMix(
                    discoveryRequestForMode(
                        mode = mode,
                        count = discoveryServerCandidateCount(mode, outputLimit),
                        excludeIds = excludedIds,
                        randomSeed = "${System.currentTimeMillis().toString(36)}-${System.nanoTime().toString(36)}",
                        requestedMood = mood,
                        intent = normalizedIntent
                    )
                )
            }.onFailure { error ->
                val message = if (normalizedIntent.isEmpty()) {
                    "Analyzer discovery unavailable; using local fallback"
                } else {
                    "Strict analyzer discovery unavailable"
                }
                Log.w("SongRepository", message, error)
            }.getOrDefault(emptyList())
                .let { withoutHidden(it) }
                .filterNot { it.isAudiobook() }
                .let { filterDiscoveryCandidatesForMode(it, mode) }
            if (analyzerMix.isNotEmpty()) {
                val selected = selectNovelDiscoverySongs(analyzerMix, excludedIds, outputLimit)
                if (selected.isNotEmpty()) {
                    rememberDiscoveryMix(historyKey, selected)
                    return selected
                }
            }
        }
        if (normalizedIntent.isNotEmpty()) return emptyList()

        val liveSongs = filterDiscoveryCandidatesForMode(
            getRandomSongs(DISCOVERY_CANDIDATE_LIMIT),
            mode
        )
            .filterNot { it.discoveryId() in excludedIds }
        if (liveSongs.isNotEmpty()) {
            val selected = SmartDjSequencer.sequence(
                liveSongs,
                mode = mode,
                outputLimit = outputLimit,
                candidateLimit = DISCOVERY_CANDIDATE_LIMIT
            )
            rememberDiscoveryMix(historyKey, selected)
            return selected
        }

        val selected = SmartDjSequencer.sequence(
            filterDiscoveryCandidatesForMode(
                songDao.getRandomSongsOnce(DISCOVERY_CANDIDATE_LIMIT)
                    .map { it.toMediaDataSong().toMediaItem() }
                    .filterNot { it.discoveryId() in excludedIds },
                mode
            ),
            mode = mode,
            outputLimit = outputLimit,
            candidateLimit = DISCOVERY_CANDIDATE_LIMIT
        )
        rememberDiscoveryMix(historyKey, selected)
        return selected
    }

    private suspend fun rememberDiscoveryMix(key: DiscoveryHistoryKey, songs: List<MediaItem>) {
        discoveryHistoryMutex.withLock {
            val history = recentDiscoveryIds.getOrPut(key) { ArrayDeque() }
            songs.map(MediaItem::discoveryId)
                .filter(String::isNotBlank)
                .forEach { id ->
                    history.remove(id)
                    history.addLast(id)
                }
            while (history.size > DISCOVERY_HISTORY_LIMIT) history.removeFirst()
        }
    }

    suspend fun getInstantMix(seed: MediaItem, count: Int = 100): List<MediaItem> {
        return getRadioMix(listOf(seed), count)
    }

    suspend fun getRadioMix(seeds: List<MediaItem>, count: Int = 100): List<MediaItem> {
        val seedIds = selectRadioSeedIds(
            seeds.mapNotNull { seed ->
                if (seed.isAudiobook()) return@mapNotNull null
                val extras = seed.mediaMetadata.extras
                val id = extras?.getString("navidromeID")
                    ?.takeUnless { it.startsWith("Local_") }
                    ?: return@mapNotNull null
                RadioSeedCandidate(
                    id = id,
                    albumKey = extras.getString("albumId")
                        ?: seed.mediaMetadata.albumTitle?.toString()
                )
            }
        )
        if (seedIds.isEmpty()) return emptyList()

        val outputLimit = count.coerceIn(1, 200)
        val selectedSeeds = seeds.filter { seed ->
            seed.mediaMetadata.extras?.getString("navidromeID") in seedIds
        }
        val primarySeed = selectedSeeds.firstOrNull()
        if (outputLimit == 1) return listOfNotNull(primarySeed)
        val recommendationLimit = outputLimit - if (primarySeed == null) 0 else 1
        val analyzerMix = runCatching {
            navidromeDataSource.getSmartMix(
                DiscoveryMixRequest(
                    mode = DiscoveryMixMode.SIMILAR,
                    count = recommendationLimit,
                    seedIds = seedIds,
                    discovery = 0.16f,
                    variety = 0.52f,
                    includeSeeds = false
                )
            )
        }.getOrDefault(emptyList())
            .let { withoutHidden(it) }
            .filterNot { it.isAudiobook() }
        if (analyzerMix.isNotEmpty()) {
            return boundedDistinctSongs(listOfNotNull(primarySeed) + analyzerMix, outputLimit)
        }

        val primarySeedId = seedIds.first()
        val similarSongs = withoutHidden(
            navidromeDataSource.getSimilarSongs(primarySeedId, recommendationLimit)
        )
            .filterNot { it.isAudiobook() }
        return boundedDistinctSongs(listOfNotNull(primarySeed) + similarSongs, outputLimit)
    }

    suspend fun hideSong(song: MediaItem): Boolean {
        val id = song.discoveryId().takeIf(String::isNotBlank) ?: return false
        songDao.hideSong(
            com.craftworks.music.data.database.entity.HiddenSongEntity(songId = id)
        )
        return true
    }

    suspend fun unhideSong(songId: String) {
        songDao.unhideSong(songId)
    }

    suspend fun replaceSongFile(song: MediaItem, uri: Uri): Boolean {
        val id = song.discoveryId()
        if (id.isBlank() || id.startsWith("Local_")) return false
        return navidromeDataSource.replaceNavidromeMediaFile(id, uri)
    }

    private suspend fun withoutHidden(songs: List<MediaItem>): List<MediaItem> {
        if (songs.isEmpty()) return songs
        val hiddenIds = songDao.getHiddenSongIds().toHashSet()
        if (hiddenIds.isEmpty()) return songs
        return songs.filterNot { it.discoveryId() in hiddenIds }
    }
}

private data class DiscoveryHistoryKey(
	val mode: DiscoveryMixMode,
	val intent: String
)

private fun MediaItem.discoveryId(): String =
    mediaMetadata.extras?.getString("navidromeID")?.takeIf(String::isNotBlank) ?: mediaId

private const val DISCOVERY_HISTORY_LIMIT = 400

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

internal fun selectNovelDiscoverySongs(
    songs: List<MediaItem>,
    excludedIds: Collection<String>,
    limit: Int
): List<MediaItem> = boundedDistinctSongs(
    songs.filterNot { it.discoveryId() in excludedIds },
    limit
)

internal data class RadioSeedCandidate(
    val id: String,
    val albumKey: String?
)

internal fun selectRadioSeedIds(
    candidates: List<RadioSeedCandidate>,
    maxSeeds: Int = 8
): List<String> {
    if (maxSeeds <= 0) return emptyList()
    val distinct = candidates
        .filter { it.id.isNotBlank() }
        .distinctBy(RadioSeedCandidate::id)
    if (distinct.size <= maxSeeds) return distinct.map(RadioSeedCandidate::id)

    val albumGroups = distinct.groupBy { candidate ->
        candidate.albumKey?.takeIf(String::isNotBlank) ?: candidate.id
    }.values.toList()
    if (albumGroups.size == 1) {
        return evenlySpaced(albumGroups.single(), maxSeeds).map(RadioSeedCandidate::id)
    }

    val selected = LinkedHashMap<String, RadioSeedCandidate>()
    evenlySpaced(albumGroups, minOf(maxSeeds, albumGroups.size)).forEach { group ->
        val representative = group[(group.size - 1) / 2]
        selected[representative.id] = representative
    }
    (evenlySpaced(distinct, minOf(distinct.size, maxSeeds * 2)) + distinct).forEach { candidate ->
        if (selected.size < maxSeeds) selected[candidate.id] = candidate
    }
    return selected.values.take(maxSeeds).map(RadioSeedCandidate::id)
}

private fun <T> evenlySpaced(items: List<T>, count: Int): List<T> {
    if (items.isEmpty() || count <= 0) return emptyList()
    if (count >= items.size) return items
    if (count == 1) return listOf(items[(items.size - 1) / 2])
    return (0 until count).map { index ->
        val sourceIndex = (
            index * items.lastIndex + (count - 1) / 2
        ) / (count - 1)
        items[sourceIndex]
    }
}
