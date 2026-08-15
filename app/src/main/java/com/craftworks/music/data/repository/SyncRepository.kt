package com.craftworks.music.data.repository

import android.util.Log
import com.craftworks.music.data.database.ChoraDatabase
import com.craftworks.music.data.database.dao.AlbumDao
import com.craftworks.music.data.database.dao.AlbumPaletteDao
import com.craftworks.music.data.database.dao.ArtistDao
import com.craftworks.music.data.database.dao.SongDao
import com.craftworks.music.data.database.dao.SyncMetadataDao
import com.craftworks.music.data.database.entity.SyncMetadata
import com.craftworks.music.data.database.entity.SongEntity
import com.craftworks.music.data.database.entity.toEntity
import com.craftworks.music.data.datasource.navidrome.NavidromeDataSource
import com.craftworks.music.data.model.MediaData
import com.craftworks.music.data.model.MediaCategory
import com.craftworks.music.data.mediaCategory
import com.craftworks.music.managers.NavidromeManager
import com.craftworks.music.managers.DataRefreshManager
import androidx.room.withTransaction
import com.craftworks.music.data.model.toAlbum
import com.craftworks.music.data.model.toSong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

private const val SONG_SYNC_UPSERT_BATCH_SIZE = 400

internal fun shouldFlushSongSyncBatch(pendingCount: Int): Boolean =
    pendingCount >= SONG_SYNC_UPSERT_BATCH_SIZE

enum class SyncPhase {
    IDLE, FETCHING_COUNTS, ARTISTS, ALBUMS, SONGS, COMPLETE, ERROR
}

data class SyncState(
    val phase: SyncPhase = SyncPhase.IDLE,
    val current: Int = 0,
    val total: Int = 0,
    val newSongs: Int = 0,
    val updatedSongs: Int = 0,
    val isPaused: Boolean = false,
    val message: String = "",
    val failedPhase: SyncPhase? = null
) {
    val percentage: Float
        get() = if (total > 0) {
            (current.toFloat() / total * 100).coerceIn(0f, 100f)
        } else {
            0f
        }

    val hasDeterminateProgress: Boolean
        get() = total > 0 && current > 0

    val shortTitle: String
        get() = when (phase) {
            SyncPhase.IDLE -> "Sync"
            SyncPhase.FETCHING_COUNTS -> "Checking library"
            SyncPhase.ARTISTS -> "Syncing artists"
            SyncPhase.ALBUMS -> "Syncing albums"
            SyncPhase.SONGS -> "Syncing songs"
            SyncPhase.COMPLETE -> "Sync complete"
            SyncPhase.ERROR -> "Sync needs attention"
        }

    val progressUnit: String
        get() = when (phase) {
            SyncPhase.ARTISTS -> "artists"
            SyncPhase.ALBUMS -> "albums"
            SyncPhase.SONGS -> "albums"
            else -> "items"
        }

    val displayText: String
        get() = when (phase) {
            SyncPhase.IDLE -> ""
            SyncPhase.FETCHING_COUNTS -> "Fetching library info..."
            SyncPhase.ARTISTS -> if (total > 0) "Syncing artists ($current of $total)" else "Syncing artists..."
            SyncPhase.ALBUMS -> if (total > 0) "Syncing albums ($current of $total)" else "Syncing albums..."
            SyncPhase.SONGS -> {
                val songInfo = when {
                    newSongs > 0 && updatedSongs > 0 -> "$newSongs new, $updatedSongs updated"
                    newSongs > 0 -> "$newSongs new songs"
                    updatedSongs > 0 -> "$updatedSongs updated"
                    else -> "checking..."
                }
                when {
                    total <= 0 -> "Syncing songs..."
                    current <= 0 -> "Preparing the first of $total albums..."
                    else -> "Processing albums ($current of $total) • $songInfo"
                }
            }
            SyncPhase.COMPLETE -> "Sync complete!"
            SyncPhase.ERROR -> message.ifBlank {
                "Sync stopped before it could finish. Your saved library is still available."
            }
    }
}

internal data class AlbumSyncCursor(
    val libraryIndex: Int,
    val libraryOffset: Int,
    val processedCount: Int
)

internal fun albumLibraryOffsetForResume(
    libraryIndex: Int,
    cursor: AlbumSyncCursor?
): Int? = when {
    cursor == null -> 0
    libraryIndex < cursor.libraryIndex -> null
    libraryIndex == cursor.libraryIndex -> cursor.libraryOffset.coerceAtLeast(0)
    else -> 0
}

@Singleton
class SyncRepository @Inject constructor(
    private val database: ChoraDatabase,
    private val navidromeDataSource: NavidromeDataSource,
    private val songDao: SongDao,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val syncMetadataDao: SyncMetadataDao,
    private val albumPaletteDao: AlbumPaletteDao,
    private val audiobookProgressRepository: AudiobookProgressRepository
) {
    private val syncMutex = Mutex()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncProgress = MutableStateFlow("")
    val syncProgress: StateFlow<String> = _syncProgress.asStateFlow()

    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    @Volatile
    private var cancelRequested = false

    @Volatile
    private var pauseRequested = false

    // State for resuming
    private var pausedPhase: SyncPhase = SyncPhase.IDLE
    private var pausedAlbumCursor: AlbumSyncCursor? = null
    private var pausedForceRefresh: Boolean = false
    private var pausedSyncStartedAt: Long = 0L
    private var cachedAlbumIds: List<String> = emptyList()

    private var failedPhase: SyncPhase = SyncPhase.IDLE
    private var failedForceRefresh: Boolean = false
    private var failedSyncStartedAt: Long = 0L

    fun cancelSync() {
        if (_isSyncing.value) {
            cancelRequested = true
            pauseRequested = false
            _isPaused.value = false
            Log.d("SyncRepository", "Sync cancellation requested")
        }
    }

    fun pauseSync() {
        if (_isSyncing.value && !_isPaused.value) {
            pauseRequested = true
            Log.d("SyncRepository", "Sync pause requested")
        }
    }

    fun resumeSync() {
        // Note: This only resets the pause flag. The caller must also call
        // syncAll(resumeFromPause = true) to actually continue the sync.
        // The isPaused state will be properly cleared when syncAll acquires
        // the mutex and starts processing.
        if (_isPaused.value) {
            pauseRequested = false
            Log.d("SyncRepository", "Sync resume requested")
        }
    }

    fun dismissSyncError() {
        if (!_isSyncing.value && _syncState.value.phase == SyncPhase.ERROR) {
            _syncProgress.value = ""
            _syncState.value = SyncState()
            clearFailedSyncState()
        }
    }

    suspend fun hasCachedData(): Boolean = withContext(Dispatchers.IO) {
        songDao.getCount() > 0 || albumDao.getCount() > 0 || artistDao.getCount() > 0
    }

    suspend fun syncAll(
        forceRefresh: Boolean = false,
        resumeFromPause: Boolean = false,
        retryFromFailure: Boolean = false
    ) = withContext(Dispatchers.IO) {
        // Use tryLock to prevent blocking if already syncing
        if (!syncMutex.tryLock()) {
            Log.d("SyncRepository", "Sync already in progress, skipping")
            return@withContext
        }

        // Track if we should preserve state (only true on clean pause)
        var preserveStateOnExit = false
        var terminalFailureState: SyncState? = null

        try {
            // If resuming, clear the paused state now that we have the mutex
            if (resumeFromPause && _isPaused.value) {
                _isPaused.value = false
            }

            _isSyncing.value = true
            cancelRequested = false

            val canRetryFailure = retryFromFailure &&
                failedPhase != SyncPhase.IDLE &&
                failedSyncStartedAt > 0L
            val startPhase = when {
                resumeFromPause -> pausedPhase
                canRetryFailure -> failedPhase.retryStartPhase()
                else -> SyncPhase.FETCHING_COUNTS
            }
            val effectiveForceRefresh = when {
                resumeFromPause -> pausedForceRefresh
                canRetryFailure -> failedForceRefresh
                else -> forceRefresh
            }
            val syncStartedAt = when {
                resumeFromPause && pausedSyncStartedAt > 0L -> pausedSyncStartedAt
                canRetryFailure -> failedSyncStartedAt
                else -> System.currentTimeMillis()
            }
            pausedForceRefresh = effectiveForceRefresh
            pausedSyncStartedAt = syncStartedAt

            Log.d("SyncRepository", "Starting sync, forceRefresh=$effectiveForceRefresh, resumeFrom=$startPhase")

            // Phase 1: Fetch counts first for progress tracking
            if (startPhase == SyncPhase.FETCHING_COUNTS) {
                _syncState.value = SyncState(phase = SyncPhase.FETCHING_COUNTS)
                _syncProgress.value = "Fetching library info..."

                val fetchedLibraries = navidromeDataSource.getNavidromeLibraries(
                    requireSuccess = true
                )
                NavidromeManager.reconcileCurrentServerLibraries(fetchedLibraries)

                // Fetch artists to count them
                val musicLibraryIds = NavidromeManager.getEnabledLibraryIdsForCurrentServer(MediaCategory.MUSIC)
                val artists = if (musicLibraryIds.isEmpty()) {
                    emptyList()
                } else {
                    navidromeDataSource.getNavidromeArtists(
                        ignoreCachedResponse = effectiveForceRefresh,
                        musicFolderIds = musicLibraryIds,
                        requireSuccess = true
                    )
                }
                val artistCount = artists.size

                if (shouldPauseOrCancel()) {
                    preserveStateOnExit = handlePauseOrCancel(SyncPhase.ARTISTS)
                    return@withContext
                }

                // Sync artists (we already have them)
                _syncState.value = SyncState(phase = SyncPhase.ARTISTS, current = 0, total = artistCount)
                _syncProgress.value = "Syncing artists..."

                val entities = artists.map {
                    it.toEntity().copy(lastSyncedAt = syncStartedAt)
                }
                database.withTransaction {
                    artistDao.insertAll(entities)
                    val removed = artistDao.deleteNotSeenSince(syncStartedAt)
                    syncMetadataDao.upsert(
                        SyncMetadata(
                            key = SyncMetadata.KEY_ARTISTS,
                            lastSyncTimestamp = System.currentTimeMillis(),
                            itemCount = entities.size
                        )
                    )
                    if (removed > 0) {
                        Log.d("SyncRepository", "Removed $removed stale artists")
                    }
                }
                _syncState.value = _syncState.value.copy(current = artistCount)
                Log.d("SyncRepository", "Synced ${entities.size} artists")

                if (shouldPauseOrCancel()) {
                    preserveStateOnExit = handlePauseOrCancel(SyncPhase.ALBUMS)
                    return@withContext
                }
            }

            // Phase 2: Sync albums with progress
            if (startPhase.ordinal <= SyncPhase.ALBUMS.ordinal) {
                val resumeCursor = if (resumeFromPause && startPhase == SyncPhase.ALBUMS) {
                    pausedAlbumCursor
                } else {
                    null
                }
                preserveStateOnExit = syncAlbumsWithProgress(
                    forceRefresh = effectiveForceRefresh,
                    syncStartedAt = syncStartedAt,
                    resumeCursor = resumeCursor
                )

                if (preserveStateOnExit || shouldPauseOrCancel()) {
                    return@withContext
                }
            }

            // Phase 3: Sync songs via albums
            if (startPhase.ordinal <= SyncPhase.SONGS.ordinal) {
                // Album song requests complete out of order, so a numeric index
                // is not a safe resume cursor. Restart this phase and upsert;
                // already-seen rows are cheap and reconciliation stays correct.
                val startIndex = 0
                preserveStateOnExit = syncSongsWithProgress(
                    forceRefresh = effectiveForceRefresh,
                    syncStartedAt = syncStartedAt,
                    startIndex = startIndex
                )

                if (preserveStateOnExit || shouldPauseOrCancel()) {
                    return@withContext
                }
            }

            audiobookProgressRepository.reconcileWithServer()

            // Complete
            _syncState.value = SyncState(phase = SyncPhase.COMPLETE)
            _syncProgress.value = "Sync complete!"
            Log.d("SyncRepository", "Full sync completed")

            // Record sync completion time for daily sync check
            recordFullSyncTime()

            // Brief delay to show completion, then reset
            kotlinx.coroutines.delay(1500)

        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("SyncRepository", "Sync failed", e)
            val interruptedState = _syncState.value
            failedPhase = interruptedState.phase
            failedForceRefresh = pausedForceRefresh
            failedSyncStartedAt = pausedSyncStartedAt
            terminalFailureState = interruptedState.copy(
                phase = SyncPhase.ERROR,
                isPaused = false,
                message = syncFailureMessage(interruptedState.phase),
                failedPhase = interruptedState.phase
            )
            preserveStateOnExit = false
            _isPaused.value = false
        } finally {
            if (!preserveStateOnExit) {
                Log.d("SyncRepository", "Resetting sync state")
                _syncProgress.value = terminalFailureState?.message.orEmpty()
                _syncState.value = terminalFailureState ?: SyncState()
                _isSyncing.value = false
                _isPaused.value = false
                cancelRequested = false
                pauseRequested = false
                pausedPhase = SyncPhase.IDLE
                pausedAlbumCursor = null
                pausedSyncStartedAt = 0L
                cachedAlbumIds = emptyList()
                if (terminalFailureState == null) {
                    clearFailedSyncState()
                }
            } else {
                Log.d("SyncRepository", "Preserving sync state for resume")
            }
            // Always unlock the mutex - we always acquired it at the start
            syncMutex.unlock()
        }
    }

    private fun shouldPauseOrCancel(): Boolean = cancelRequested || pauseRequested

    private fun SyncPhase.retryStartPhase(): SyncPhase = when (this) {
        SyncPhase.ALBUMS -> SyncPhase.ALBUMS
        SyncPhase.SONGS -> SyncPhase.SONGS
        else -> SyncPhase.FETCHING_COUNTS
    }

    private fun syncFailureMessage(phase: SyncPhase): String = when (phase) {
        SyncPhase.FETCHING_COUNTS,
        SyncPhase.ARTISTS -> "Couldn't reach the music server. Your saved library is still available. Check the connection and retry."
        SyncPhase.ALBUMS -> "Album sync stopped before it finished. Your saved library is still available. Check the connection and retry."
        SyncPhase.SONGS -> "Song sync stopped before it finished. Your saved library is still available. Check the connection and retry."
        else -> "Sync stopped before it could finish. Your saved library is still available. Check the connection and retry."
    }

    private fun clearFailedSyncState() {
        failedPhase = SyncPhase.IDLE
        failedForceRefresh = false
        failedSyncStartedAt = 0L
    }

    /**
     * Handles pause or cancel request.
     * @return true if paused (state should be preserved), false if cancelled (state should reset)
     */
    private fun handlePauseOrCancel(nextPhase: SyncPhase): Boolean {
        return if (pauseRequested) {
            _isPaused.value = true
            pausedPhase = nextPhase
            _syncState.value = _syncState.value.copy(isPaused = true)
            Log.d("SyncRepository", "Sync paused at phase $nextPhase")
            true // Preserve state
        } else if (cancelRequested) {
            Log.d("SyncRepository", "Sync cancelled at phase $nextPhase")
            false // Don't preserve state
        } else {
            false
        }
    }

    /**
     * Syncs albums with progress tracking.
     * @return true if paused (state should be preserved), false otherwise
     */
    private suspend fun syncAlbumsWithProgress(
        forceRefresh: Boolean,
        syncStartedAt: Long,
        resumeCursor: AlbumSyncCursor? = null
    ): Boolean {
        try {
            var processedCount = resumeCursor?.processedCount?.coerceAtLeast(0) ?: 0
            var activeLibraryIndex = resumeCursor?.libraryIndex?.coerceAtLeast(0) ?: 0
            var activeLibraryOffset = resumeCursor?.libraryOffset?.coerceAtLeast(0) ?: 0
            val pageSize = 500
            val enabledLibraries = NavidromeManager.getEnabledLibrariesForCurrentServer()
            val estimatedTotal = if (resumeCursor != null) {
                _syncState.value.total
            } else {
                maxOf(albumDao.getCount(), pageSize * enabledLibraries.size.coerceAtLeast(1))
            }

            _syncState.value = SyncState(
                phase = SyncPhase.ALBUMS,
                current = processedCount,
                total = estimatedTotal
            )
            _syncProgress.value = if (resumeCursor != null) {
                "Resuming albums... ($processedCount)"
            } else {
                "Syncing albums..."
            }

            val librariesToSync = enabledLibraries.map { it to listOf(it.id) }
                .ifEmpty { listOf(null to null) }
            for ((libraryIndex, libraryAndFolders) in librariesToSync.withIndex()) {
                val resumeOffset = albumLibraryOffsetForResume(libraryIndex, resumeCursor)
                    ?: continue
                val (library, folderIds) = libraryAndFolders
                activeLibraryIndex = libraryIndex
                var libraryOffset = resumeOffset
                activeLibraryOffset = libraryOffset
                while (!shouldPauseOrCancel()) {
                    val albums = navidromeDataSource.getNavidromeAlbums(
                        sort = "alphabeticalByName",
                        size = pageSize,
                        offset = libraryOffset,
                        ignoreCachedResponse = forceRefresh,
                        musicFolderIds = folderIds,
                        requireSuccess = true
                    )

                    if (albums.isEmpty()) break

                    // Insert this batch immediately - appears in UI right away
                    val entities = albums.map {
                        val parsed = it.toAlbum()
                        parsed.copy(
                            musicFolderId = parsed.musicFolderId ?: library?.id,
                            mediaCategory = MediaCategory.resolve(
                                explicit = parsed.mediaCategory ?: library?.mediaCategory,
                                libraryName = library?.name
                            )
                        ).toEntity().copy(lastSyncedAt = syncStartedAt)
                    }
                    albumDao.insertAll(entities)
                    libraryOffset += albums.size
                    activeLibraryOffset = libraryOffset
                    processedCount += albums.size

                    _syncState.value = _syncState.value.copy(
                        current = processedCount,
                        total = if (albums.size < pageSize && library == enabledLibraries.lastOrNull()) {
                            processedCount
                        } else {
                            maxOf(processedCount + pageSize, estimatedTotal)
                        }
                    )
                    _syncProgress.value = "Syncing albums... ($processedCount)"

                    if (albums.size < pageSize) break
                }
                if (shouldPauseOrCancel()) break
            }

            if (shouldPauseOrCancel()) {
                pausedAlbumCursor = AlbumSyncCursor(
                    libraryIndex = activeLibraryIndex,
                    libraryOffset = activeLibraryOffset,
                    processedCount = processedCount
                )
                return handlePauseOrCancel(SyncPhase.ALBUMS)
            }

            pausedAlbumCursor = null

            currentCoroutineContext().ensureActive()
            val (removed, totalAlbums) = database.withTransaction {
                val removedCount = albumDao.deleteNotSeenSince(syncStartedAt)
                val currentCount = albumDao.getCount()
                syncMetadataDao.upsert(
                    SyncMetadata(
                        key = SyncMetadata.KEY_ALBUMS,
                        lastSyncTimestamp = System.currentTimeMillis(),
                        itemCount = currentCount
                    )
                )
                removedCount to currentCount
            }
            _syncState.value = _syncState.value.copy(current = totalAlbums, total = totalAlbums)
            Log.d("SyncRepository", "Synced $totalAlbums albums; removed $removed stale albums")
            return false
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("SyncRepository", "Failed to sync albums", e)
            throw e
        }
    }

    /**
     * Syncs songs with progress tracking.
     * @return true if paused (state should be preserved), false otherwise
     */
    private suspend fun syncSongsWithProgress(
        forceRefresh: Boolean,
        syncStartedAt: Long,
        startIndex: Int = 0
    ): Boolean {
        try {
            val albums = if (cachedAlbumIds.isNotEmpty() && startIndex > 0) {
                // Resume with cached album list
                albumDao.getAllAlbumsOnce().filter { it.navidromeID in cachedAlbumIds }
            } else {
                albumDao.getAllAlbumsOnce().also {
                    cachedAlbumIds = it.map { album -> album.navidromeID }
                }
            }

            // Load existing song IDs for delta sync - use HashSet for O(1) lookup
            // and clear after use to prevent memory retention
            val existingSongIds: Set<String> = HashSet(songDao.getAllNavidromeIds())
            Log.d("SyncRepository", "Delta sync: ${existingSongIds.size} existing songs in database")

            val totalAlbums = albums.size
            val processedCount = AtomicInteger(startIndex)
            val newSongsCount = AtomicInteger(0)
            val updatedSongsCount = AtomicInteger(0)
            val failedAlbums = mutableListOf<String>()
            val failedAlbumsMutex = Mutex()

            _syncState.value = SyncState(
                phase = SyncPhase.SONGS,
                current = startIndex,
                total = totalAlbums,
                newSongs = 0,
                updatedSongs = 0
            )
            _syncProgress.value = "Syncing songs..."

            // Parallel sync with semaphore to limit concurrent requests
            val concurrency = 4 // Avoid saturating high-latency VPN links during large syncs.
            val semaphore = Semaphore(concurrency)
            val songInsertMutex = Mutex()
            val pendingSongEntities = mutableListOf<SongEntity>()
            val progressUpdateMutex = Mutex()

            coroutineScope {
                val albumsToProcess = if (startIndex < albums.size) albums.subList(startIndex, albums.size) else emptyList()

                val jobs = albumsToProcess.map { album ->
                    async {
                        if (shouldPauseOrCancel()) {
                            return@async
                        }

                        semaphore.withPermit {
                            if (shouldPauseOrCancel()) {
                                return@withPermit
                            }

                            val success = syncAlbumSongsWithDelta(
                                album,
                                forceRefresh,
                                syncStartedAt,
                                existingSongIds,
                                songInsertMutex,
                                pendingSongEntities
                            ) { newCount, updatedCount ->
                                newSongsCount.addAndGet(newCount)
                                updatedSongsCount.addAndGet(updatedCount)
                            }

                            if (!success) {
                                failedAlbumsMutex.withLock {
                                    failedAlbums.add(album.navidromeID)
                                }
                            }

                            val current = processedCount.incrementAndGet()

                            // Publish every completed album so the UI never appears stuck at 0%.
                            progressUpdateMutex.withLock {
                                _syncState.value = _syncState.value.copy(
                                    current = current,
                                    newSongs = newSongsCount.get(),
                                    updatedSongs = updatedSongsCount.get()
                                )
                                _syncProgress.value = "Syncing songs... ($current/$totalAlbums albums)"
                            }
                        }
                    }
                }

                jobs.awaitAll()
            }

            songInsertMutex.withLock {
                flushPendingSongEntities(pendingSongEntities)
            }

            // Check for pause/cancel after parallel section
            if (shouldPauseOrCancel()) {
                return handlePauseOrCancel(SyncPhase.SONGS)
            }

            // Let the parallel request pressure subside, then give transient failures one
            // final serial attempt before failing the whole sync.
            val serialRetryIds = failedAlbumsMutex.withLock { failedAlbums.toList() }
            if (serialRetryIds.isNotEmpty()) {
                _syncProgress.value = "Retrying ${serialRetryIds.size} album${if (serialRetryIds.size == 1) "" else "s"}..."
                val albumsById = albums.associateBy { it.navidromeID }
                val recoveredIds = mutableSetOf<String>()
                serialRetryIds.forEach { albumId ->
                    val album = albumsById[albumId] ?: return@forEach
                    if (shouldPauseOrCancel()) {
                        return@forEach
                    }
                    kotlinx.coroutines.delay(1_000L)
                    val recovered = syncAlbumSongsWithDelta(
                        album = album,
                        forceRefresh = true,
                        syncStartedAt = syncStartedAt,
                        existingSongIds = existingSongIds,
                        insertMutex = songInsertMutex,
                        pendingSongEntities = pendingSongEntities,
                        maxRetries = 0,
                        onSongCounts = { newCount, updatedCount ->
                            newSongsCount.addAndGet(newCount)
                            updatedSongsCount.addAndGet(updatedCount)
                        }
                    )
                    if (recovered) recoveredIds.add(albumId)
                }
                failedAlbumsMutex.withLock { failedAlbums.removeAll(recoveredIds) }
                songInsertMutex.withLock {
                    flushPendingSongEntities(pendingSongEntities)
                }
            }

            if (failedAlbumsMutex.withLock { failedAlbums.isNotEmpty() }) {
                throw IllegalStateException(
                    "Could not sync songs from ${failedAlbumsMutex.withLock { failedAlbums.size }} albums"
                )
            }

            // Clear cached album IDs after sync to free memory
            if (!shouldPauseOrCancel()) {
                cachedAlbumIds = emptyList()
            }

            val finalNewCount = newSongsCount.get()
            val finalUpdatedCount = updatedSongsCount.get()
            currentCoroutineContext().ensureActive()
            val (removed, totalInDb) = database.withTransaction {
                val removedCount = songDao.deleteNotSeenSince(syncStartedAt)
                val currentCount = songDao.getCount()
                syncMetadataDao.upsert(
                    SyncMetadata(
                        key = SyncMetadata.KEY_SONGS,
                        lastSyncTimestamp = System.currentTimeMillis(),
                        itemCount = currentCount
                    )
                )
                removedCount to currentCount
            }
            Log.d(
                "SyncRepository",
                "Sync complete: $finalNewCount new, $finalUpdatedCount updated, " +
                    "$removed stale removed, $totalInDb total in DB"
            )
            return false
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("SyncRepository", "Failed to sync songs", e)
            throw e
        }
    }

    private suspend fun syncAlbumSongsWithDelta(
        album: com.craftworks.music.data.database.entity.AlbumEntity,
        forceRefresh: Boolean,
        syncStartedAt: Long,
        existingSongIds: Set<String>,
        insertMutex: Mutex,
        pendingSongEntities: MutableList<SongEntity>,
        maxRetries: Int = 2,
        onSongCounts: (newCount: Int, updatedCount: Int) -> Unit
    ): Boolean {
        var lastException: Exception? = null

        repeat(maxRetries + 1) { attempt ->
            try {
                val albumSongs = navidromeDataSource.getNavidromeAlbum(
                    albumId = album.navidromeID,
                    ignoreCachedResponse = forceRefresh || attempt > 0,
                    requireSuccess = true
                )
                val songs = albumSongs?.drop(1)?.map { it.toSong() } ?: emptyList()

                if (songs.isNotEmpty()) {
                    val entities = songs.map { song ->
                        val inheritedCategory = MediaCategory.resolve(
                            explicit = song.mediaCategory ?: album.mediaCategory,
                            path = song.path,
                            format = song.format
                        )
                        song.copy(
                            musicFolderId = song.musicFolderId ?: album.musicFolderId,
                            mediaCategory = inheritedCategory
                        ).toEntity().copy(lastSyncedAt = syncStartedAt)
                    }

                    // Separate new songs from updates
                    val (newSongs, existingSongs) = entities.partition { it.navidromeID !in existingSongIds }

                    insertMutex.withLock {
                        pendingSongEntities.addAll(entities)
                        if (shouldFlushSongSyncBatch(pendingSongEntities.size)) {
                            flushPendingSongEntities(pendingSongEntities)
                        }
                        if (
                            album.mediaCategory != MediaCategory.AUDIOBOOK &&
                            entities.any { it.mediaCategory == MediaCategory.AUDIOBOOK }
                        ) {
                            albumDao.insert(
                                album.copy(
                                    mediaCategory = MediaCategory.AUDIOBOOK,
                                    lastSyncedAt = syncStartedAt
                                )
                            )
                        }
                    }

                    onSongCounts(newSongs.size, existingSongs.size)
                }
                return true
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                lastException = e
                if (attempt < maxRetries) {
                    kotlinx.coroutines.delay(1_000L * (1 shl attempt))
                }
            }
        }

        Log.e("SyncRepository", "Failed to sync album ${album.navidromeID} after $maxRetries retries", lastException)
        return false
    }

    private suspend fun flushPendingSongEntities(pending: MutableList<SongEntity>) {
        if (pending.isEmpty()) return
        val batch = pending.toList()
        pending.clear()
        songDao.insertAll(batch)
        batch.forEach { audiobookProgressRepository.seedFromServerSong(it) }
    }

    private suspend fun syncAlbumSongsParallel(
        albumId: String,
        forceRefresh: Boolean,
        insertMutex: Mutex,
        onSongCount: (Int) -> Unit
    ): Boolean {
        var lastException: Exception? = null
        val maxRetries = 2

        repeat(maxRetries + 1) { attempt ->
            try {
                val albumSongs = navidromeDataSource.getNavidromeAlbum(
                    albumId = albumId,
                    ignoreCachedResponse = forceRefresh || attempt > 0
                )
                val songs = albumSongs?.drop(1)?.map { it.toSong() } ?: emptyList()

                if (songs.isNotEmpty()) {
                    val entities = songs.map { it.toEntity() }
                    insertMutex.withLock {
                        songDao.insertAll(entities)
                    }
                    onSongCount(entities.size)
                }
                return true
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                lastException = e
                if (attempt < maxRetries) {
                    kotlinx.coroutines.delay(50L * (1 shl attempt))
                }
            }
        }

        Log.e("SyncRepository", "Failed to sync album $albumId after $maxRetries retries", lastException)
        return false
    }

    private suspend fun syncAlbumWithRetry(
        albumId: String,
        forceRefresh: Boolean,
        maxRetries: Int,
        onSuccess: suspend (List<MediaData.Song>) -> Unit
    ): Boolean {
        var lastException: Exception? = null

        repeat(maxRetries + 1) { attempt ->
            try {
                val albumSongs = navidromeDataSource.getNavidromeAlbum(
                    albumId = albumId,
                    ignoreCachedResponse = forceRefresh || attempt > 0
                )
                val songs = albumSongs?.drop(1)?.map { it.toSong() } ?: emptyList()
                onSuccess(songs)
                return true
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                lastException = e
                if (attempt < maxRetries) {
                    // Exponential backoff: 100ms, 200ms, 400ms...
                    kotlinx.coroutines.delay(100L * (1 shl attempt))
                }
            }
        }

        Log.e("SyncRepository", "Failed to sync album $albumId after $maxRetries retries", lastException)
        return false
    }

    suspend fun getLastSyncTime(key: String): Long? = withContext(Dispatchers.IO) {
        syncMetadataDao.getLastSyncTime(key)
    }

    private val ONE_DAY_MS = 24 * 60 * 60 * 1000L

    suspend fun shouldSyncToday(): Boolean = withContext(Dispatchers.IO) {
        val lastSync = syncMetadataDao.getLastSyncTime(SyncMetadata.KEY_LAST_FULL_SYNC)
        if (lastSync == null) return@withContext true
        val timeSinceLastSync = System.currentTimeMillis() - lastSync
        timeSinceLastSync >= ONE_DAY_MS
    }

    private suspend fun recordFullSyncTime() {
        syncMetadataDao.upsert(
            SyncMetadata(
                key = SyncMetadata.KEY_LAST_FULL_SYNC,
                lastSyncTimestamp = System.currentTimeMillis(),
                itemCount = 0
            )
        )
    }

    // Flag to prevent auto-sync after clearing cache
    @Volatile
    private var justCleared = false

    fun wasJustCleared(): Boolean {
        val result = justCleared
        justCleared = false
        return result
    }

    suspend fun clearAllCache() = withContext(Dispatchers.IO) {
        justCleared = true
        // Use transaction to ensure all-or-nothing deletion
        database.withTransaction {
            songDao.deleteAll()
            albumDao.deleteAll()
            artistDao.deleteAll()
            syncMetadataDao.deleteAll()
            albumPaletteDao.clear()
        }
        // Notify all observers that data has changed
        DataRefreshManager.notifyDataSourcesChanged()
    }
}
