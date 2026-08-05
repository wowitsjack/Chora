package com.craftworks.music.data.repository

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.craftworks.music.data.database.dao.AudiobookProgressDao
import com.craftworks.music.data.database.dao.SongDao
import com.craftworks.music.data.database.entity.AudiobookProgressEntity
import com.craftworks.music.data.database.entity.SongEntity
import com.craftworks.music.data.datasource.navidrome.NavidromeDataSource
import com.craftworks.music.data.model.MediaCategory
import com.craftworks.music.managers.NavidromeManager
import com.craftworks.music.worker.AudiobookProgressSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

private const val BOOKMARK_SYNC_DEBOUNCE_MS = 1_500L
private const val DURABLE_BOOKMARK_SYNC_DELAY_MS = 15_000L
private const val DURABLE_BOOKMARK_SYNC_BACKOFF_SECONDS = 30L

internal fun audiobookProgressSyncConstraints(): Constraints = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .build()

data class AudiobookResumePoint(
    val songId: String,
    val albumId: String,
    val positionMs: Long,
    val playbackSpeed: Float
)

internal fun isAudiobookCompleted(positionMs: Long, durationMs: Long): Boolean {
    if (durationMs <= 0L) return false
    val remainingMs = (durationMs - positionMs).coerceAtLeast(0L)
    return positionMs.toDouble() / durationMs.toDouble() >= 0.95 || remainingMs <= 60_000L
}

internal fun normalizedAudiobookSpeed(speed: Float): Float =
    speed.coerceIn(0.5f, 3f)

internal fun bookmarkPositionMs(positionMs: Long): Long =
    positionMs.coerceAtLeast(0L)

internal fun shouldApplyRemoteBookmark(localUpdatedAt: Long?, remoteUpdatedAt: Long): Boolean =
    localUpdatedAt == null || remoteUpdatedAt > localUpdatedAt

internal fun reconciledBookmarkPositionMs(
    localPositionMs: Long?,
    localUpdatedAt: Long?,
    remotePositionMs: Long,
    remoteUpdatedAt: Long
): Long {
    val remotePosition = bookmarkPositionMs(remotePositionMs)
    val localPosition = localPositionMs?.coerceAtLeast(0L)
    return if (shouldApplyRemoteBookmark(localUpdatedAt, remoteUpdatedAt)) {
        remotePosition
    } else {
        localPosition ?: remotePosition
    }
}

internal fun isRemoteBookmarkSyncable(songId: String): Boolean =
    !songId.startsWith("Local_")

internal fun remotePendingBookmarks(
    pending: Iterable<AudiobookProgressEntity>
): List<AudiobookProgressEntity> =
    pending.filter { isRemoteBookmarkSyncable(it.songId) }

internal fun pendingBookmarkFlushCanFinish(
    pending: Iterable<AudiobookProgressEntity>,
    hasActiveServer: Boolean
): Boolean =
    remotePendingBookmarks(pending).isEmpty() || hasActiveServer

internal fun parseBookmarkTimestamp(value: String): Long {
    if (value.isBlank()) return 0L
    val normalized = value.replace(
        Regex("(\\.\\d{3})\\d+(?=Z|[+-]\\d{2}:?\\d{2}$)"),
        "$1"
    )
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'"
    )
    for (pattern in patterns) {
        val parsed = runCatching {
            SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(normalized)?.time
        }.getOrNull()
        if (parsed != null) return parsed
    }
    return 0L
}

@Singleton
class AudiobookProgressRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val progressDao: AudiobookProgressDao,
    private val songDao: SongDao,
    private val navidromeDataSource: NavidromeDataSource
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingSyncJobs = ConcurrentHashMap<String, Job>()

    init {
        scheduleDurableBookmarkSync(initialDelayMs = 0L)
    }

    val allProgress: Flow<List<AudiobookProgressEntity>> = progressDao.observeAll()

    fun observeProgress(songId: String): Flow<AudiobookProgressEntity?> =
        progressDao.observeBySongId(songId)

    fun observeLatestForBook(albumId: String): Flow<AudiobookProgressEntity?> =
        progressDao.observeLatestForAlbum(albumId)

    suspend fun getResumePoint(albumId: String): AudiobookResumePoint? {
        val progress = progressDao.getLatestForAlbum(albumId) ?: return null
        return AudiobookResumePoint(
            songId = progress.songId,
            albumId = progress.albumId,
            positionMs = progress.positionMs,
            playbackSpeed = normalizedAudiobookSpeed(progress.playbackSpeed)
        )
    }

    suspend fun getBookSpeed(albumId: String): Float =
        progressDao.getLatestForAlbum(albumId)
            ?.playbackSpeed
            ?.let(::normalizedAudiobookSpeed)
            ?: 1f

    suspend fun setBookSpeed(albumId: String, speed: Float) {
        val normalizedSpeed = normalizedAudiobookSpeed(speed)
        val existing = progressDao.getLatestForAlbum(albumId)
        if (existing != null) {
            progressDao.upsert(existing.copy(playbackSpeed = normalizedSpeed))
            return
        }

        val firstPart = songDao.getFirstAudiobookPartByAlbum(albumId) ?: return
        progressDao.upsert(
            AudiobookProgressEntity(
                songId = firstPart.navidromeID,
                albumId = albumId,
                durationMs = firstPart.duration * 1_000L,
                playbackSpeed = normalizedSpeed,
                syncPending = false
            )
        )
    }

    suspend fun savePlayback(
        mediaItem: MediaItem?,
        positionMs: Long,
        durationMs: Long,
        playbackSpeed: Float,
        syncImmediately: Boolean = false
    ) {
        val extras = mediaItem?.mediaMetadata?.extras ?: return
        if (extras.getString("mediaCategory") != MediaCategory.AUDIOBOOK) return

        val songId = extras.getString("navidromeID")?.takeIf { it.isNotBlank() } ?: return
        val albumId = extras.getString("albumId")?.takeIf { it.isNotBlank() } ?: return
        val safeDuration = durationMs.takeIf { it > 0L }
            ?: mediaItem.mediaMetadata.durationMs?.takeIf { it > 0L }
            ?: 0L
        saveProgress(
            songId = songId,
            albumId = albumId,
            positionMs = positionMs,
            durationMs = safeDuration,
            playbackSpeed = playbackSpeed,
            syncImmediately = syncImmediately
        )
    }

    suspend fun saveProgress(
        songId: String,
        albumId: String,
        positionMs: Long,
        durationMs: Long,
        playbackSpeed: Float,
        syncImmediately: Boolean = false
    ) {
        val existing = progressDao.getBySongId(songId)
        val safeDuration = durationMs.coerceAtLeast(existing?.durationMs ?: 0L)
        val safePosition = positionMs.coerceAtLeast(0L).let { position ->
            if (safeDuration > 0L) position.coerceAtMost(safeDuration) else position
        }
        val now = System.currentTimeMillis()
        val canSyncToServer = isRemoteBookmarkSyncable(songId)
        val progress = AudiobookProgressEntity(
            songId = songId,
            albumId = albumId,
            positionMs = safePosition,
            durationMs = safeDuration,
            completed = isAudiobookCompleted(safePosition, safeDuration),
            playbackSpeed = normalizedAudiobookSpeed(playbackSpeed),
            updatedAt = now,
            serverUpdatedAt = existing?.serverUpdatedAt ?: 0L,
            syncPending = canSyncToServer
        )
        progressDao.upsert(progress)
        songDao.updateBookmarkPosition(songId, safePosition)
        if (canSyncToServer) {
            scheduleDurableBookmarkSync()
        }

        if (!canSyncToServer) {
            return
        } else if (syncImmediately) {
            pendingSyncJobs.remove(songId)?.cancel()
            syncBookmark(progress)
        } else {
            scheduleBookmarkSync(progress)
        }
    }

    suspend fun seedFromServerSong(song: SongEntity) {
        if (song.mediaCategory != MediaCategory.AUDIOBOOK || song.bookmarkPosition <= 0L) return
        val existing = progressDao.getBySongId(song.navidromeID)
        val remotePosition = song.bookmarkPosition.coerceAtLeast(0L)
        if (existing == null) {
            val durationMs = song.duration * 1_000L
            progressDao.upsert(
                AudiobookProgressEntity(
                    songId = song.navidromeID,
                    albumId = song.albumId,
                    positionMs = remotePosition,
                    durationMs = durationMs,
                    completed = isAudiobookCompleted(remotePosition, durationMs),
                    updatedAt = 0L,
                    serverUpdatedAt = 0L,
                    syncPending = false
                )
            )
        } else {
            songDao.updateBookmarkPosition(song.navidromeID, existing.positionMs.coerceAtLeast(0L))
        }
    }

    suspend fun reconcileWithServer() {
        if (!NavidromeManager.checkActiveServers()) return
        val remoteBookmarks = try {
            navidromeDataSource.getNavidromeBookmarks()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.d("AudiobookProgress", "Bookmark reconciliation deferred (${e::class.simpleName})")
            return
        }

        val localBySong = progressDao.getAllOnce().associateBy { it.songId }
        val remoteIds = mutableSetOf<String>()
        remoteBookmarks.forEach { bookmark ->
            val song = bookmark.entry
            if (song.mediaCategory != MediaCategory.AUDIOBOOK) return@forEach
            remoteIds += song.navidromeID
            val local = localBySong[song.navidromeID]
            val remoteTimestamp = parseBookmarkTimestamp(bookmark.changed)
            val durationMs = song.duration * 1_000L
            val remotePosition = bookmarkPositionMs(bookmark.position)
            val position = reconciledBookmarkPositionMs(
                localPositionMs = local?.positionMs,
                localUpdatedAt = local?.updatedAt,
                remotePositionMs = remotePosition,
                remoteUpdatedAt = remoteTimestamp
            )
            val localWins = local != null && !shouldApplyRemoteBookmark(local.updatedAt, remoteTimestamp)

            if (localWins) {
                pendingSyncJobs.remove(local.songId)?.cancel()
                songDao.updateBookmarkPosition(song.navidromeID, position)
                syncBookmark(local.copy(positionMs = position))
            } else {
                pendingSyncJobs.remove(song.navidromeID)?.cancel()
                progressDao.upsert(
                    AudiobookProgressEntity(
                        songId = song.navidromeID,
                        albumId = song.albumId,
                        positionMs = position,
                        durationMs = max(local?.durationMs ?: 0L, durationMs),
                        completed = isAudiobookCompleted(position, max(local?.durationMs ?: 0L, durationMs)),
                        playbackSpeed = local?.playbackSpeed ?: 1f,
                        updatedAt = max(local?.updatedAt ?: 0L, remoteTimestamp),
                        serverUpdatedAt = remoteTimestamp,
                        syncPending = false
                    )
                )
                songDao.updateBookmarkPosition(song.navidromeID, position)
            }
        }

        remotePendingBookmarks(progressDao.getPending())
            .filterNot { it.songId in remoteIds }
            .forEach { syncBookmark(it) }
    }

    suspend fun flushPending(): Boolean {
        val allPending = progressDao.getPending()
        val pending = remotePendingBookmarks(allPending)
        if (pending.isEmpty()) return true
        if (!pendingBookmarkFlushCanFinish(allPending, NavidromeManager.checkActiveServers())) {
            return false
        }

        pending.forEach { progress ->
            pendingSyncJobs.remove(progress.songId)?.cancel()
            syncBookmark(progress)
        }
        return remotePendingBookmarks(progressDao.getPending()).isEmpty()
    }

    suspend fun restartBook(albumId: String) {
        val parts = songDao.getAudiobookPartsByAlbumOnce(albumId)
        val existingBySong = progressDao.getAllOnce()
            .filter { it.albumId == albumId }
            .associateBy { it.songId }
        val playbackSpeed = existingBySong.values
            .maxByOrNull { it.updatedAt }
            ?.playbackSpeed
            ?.let(::normalizedAudiobookSpeed)
            ?: 1f
        val restartedAt = System.currentTimeMillis()
        val restartedProgress = parts.map { part ->
            val canSyncToServer = isRemoteBookmarkSyncable(part.navidromeID)
            val existing = existingBySong[part.navidromeID]
            pendingSyncJobs.remove(part.navidromeID)?.cancel()
            AudiobookProgressEntity(
                songId = part.navidromeID,
                albumId = albumId,
                positionMs = 0L,
                durationMs = part.duration * 1_000L,
                completed = false,
                playbackSpeed = playbackSpeed,
                updatedAt = restartedAt,
                serverUpdatedAt = existing?.serverUpdatedAt ?: 0L,
                syncPending = canSyncToServer
            )
        }
        restartedProgress.forEach { progress ->
            progressDao.upsert(progress)
            songDao.updateBookmarkPosition(progress.songId, 0L)
        }
        if (restartedProgress.any { isRemoteBookmarkSyncable(it.songId) }) {
            scheduleDurableBookmarkSync()
        }
        if (NavidromeManager.checkActiveServers()) {
            restartedProgress
                .filter { isRemoteBookmarkSyncable(it.songId) }
                .forEach { progress ->
                    syncBookmark(progress)
                }
        }
    }

    private fun scheduleBookmarkSync(progress: AudiobookProgressEntity) {
        if (!NavidromeManager.checkActiveServers() || !isRemoteBookmarkSyncable(progress.songId)) return
        pendingSyncJobs.remove(progress.songId)?.cancel()
        pendingSyncJobs[progress.songId] = scope.launch {
            delay(BOOKMARK_SYNC_DEBOUNCE_MS)
            syncBookmark(progress)
            pendingSyncJobs.remove(progress.songId)
        }
    }

    private suspend fun syncBookmark(progress: AudiobookProgressEntity): Boolean {
        if (!NavidromeManager.checkActiveServers() || !isRemoteBookmarkSyncable(progress.songId)) {
            return false
        }
        try {
            navidromeDataSource.createNavidromeBookmark(
                songId = progress.songId,
                positionMs = bookmarkPositionMs(progress.positionMs)
            )
            progressDao.markSynced(
                songId = progress.songId,
                localUpdatedAt = progress.updatedAt,
                serverUpdatedAt = System.currentTimeMillis()
            )
            return true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.d("AudiobookProgress", "Bookmark upload deferred (${e::class.simpleName})")
            return false
        }
    }

    private fun scheduleDurableBookmarkSync(
        initialDelayMs: Long = DURABLE_BOOKMARK_SYNC_DELAY_MS
    ) {
        val request = OneTimeWorkRequestBuilder<AudiobookProgressSyncWorker>()
            .setConstraints(audiobookProgressSyncConstraints())
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                DURABLE_BOOKMARK_SYNC_BACKOFF_SECONDS,
                TimeUnit.SECONDS
            )
            .addTag(AudiobookProgressSyncWorker.WORK_NAME)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            AudiobookProgressSyncWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
