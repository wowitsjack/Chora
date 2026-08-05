package com.craftworks.music.data.repository

import android.content.Context
import androidx.media3.common.MediaMetadata
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.craftworks.music.data.database.dao.DownloadDao
import com.craftworks.music.data.database.dao.OfflineSongDao
import com.craftworks.music.data.database.entity.DownloadEntity
import com.craftworks.music.data.database.entity.DownloadStatus
import com.craftworks.music.data.database.entity.MediaType
import com.craftworks.music.worker.DownloadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

internal fun downloadConstraints(): Constraints = Constraints.Builder()
    .setRequiresStorageNotLow(true)
    .build()

@Singleton
class DownloadRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao,
    private val offlineSongDao: OfflineSongDao
) {
    private val workManager = WorkManager.getInstance(context)
    private val hasReconciledQueuedDownloads = AtomicBoolean(false)

    // Flows for UI observation
    val allDownloads: Flow<List<DownloadEntity>> = downloadDao.getAllDownloads()
    val activeDownloads: Flow<List<DownloadEntity>> = downloadDao.getActiveDownloads()
    val completedDownloads: Flow<List<DownloadEntity>> = downloadDao.getCompletedDownloads()
    val failedDownloads: Flow<List<DownloadEntity>> = downloadDao.getFailedDownloads()
    val activeDownloadCount: Flow<Int> = downloadDao.getActiveDownloadCount()

    val hasActiveDownloads: Flow<Boolean> = activeDownloadCount.map { it > 0 }

    suspend fun queueSongDownload(song: MediaMetadata): String {
        val mediaId = song.extras?.getString("navidromeID") ?: return ""
        val format = song.extras?.getString("format") ?: "mp3"

        val offlineSong = offlineSongDao.getAvailableOfflineSong(mediaId)
        if (offlineSong != null && java.io.File(offlineSong.localFilePath).exists()) {
            return ""
        }
        if (offlineSong != null) {
            offlineSongDao.markUnavailable(mediaId)
        }

        val downloadId = UUID.randomUUID().toString()

        val downloadEntity = DownloadEntity(
            id = downloadId,
            mediaId = mediaId,
            mediaType = MediaType.SONG,
            title = song.title?.toString() ?: "Unknown",
            artist = song.artist?.toString() ?: "Unknown",
            albumTitle = song.albumTitle?.toString(),
            imageUrl = song.artworkUri?.toString(),
            status = DownloadStatus.QUEUED,
            queuedAt = System.currentTimeMillis(),
            format = format
        )

        // Use insertOrIgnore to handle race conditions atomically
        // If mediaId already exists (unique constraint), insertion returns -1
        val inserted = downloadDao.insertOrIgnore(downloadEntity)
        if (inserted == -1L) {
            // Already exists in downloads table - check if it's a completed download
            val existingDownload = downloadDao.getDownloadByMediaId(mediaId)
            if (existingDownload != null) {
                when (existingDownload.status) {
                    DownloadStatus.FAILED,
                    DownloadStatus.COMPLETED -> {
                        downloadDao.resetForRetry(existingDownload.id)
                        scheduleDownload(
                            existingDownload.copy(
                                status = DownloadStatus.QUEUED,
                                progress = 0f,
                                bytesDownloaded = 0L,
                                totalBytes = 0L,
                                localFilePath = null,
                                completedAt = null,
                                failureReason = null,
                                retryCount = 0
                            ),
                            ExistingWorkPolicy.REPLACE
                        )
                    }
                    DownloadStatus.QUEUED -> {
                        scheduleDownload(existingDownload, ExistingWorkPolicy.KEEP)
                    }
                    DownloadStatus.DOWNLOADING,
                    DownloadStatus.PAUSED -> Unit
                }
                return existingDownload.id
            }
            return ""
        }

        scheduleDownload(downloadEntity, ExistingWorkPolicy.KEEP)

        return downloadId
    }

    suspend fun queueSongsDownload(songs: List<MediaMetadata>): List<String> {
        return songs.mapNotNull { song ->
            queueSongDownload(song).takeIf { it.isNotEmpty() }
        }
    }

    private fun scheduleDownload(
        download: DownloadEntity,
        existingWorkPolicy: ExistingWorkPolicy
    ) {
        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(downloadConstraints())
            .setInputData(workDataOf(
                DownloadWorker.KEY_DOWNLOAD_ID to download.id,
                DownloadWorker.KEY_MEDIA_ID to download.mediaId,
                DownloadWorker.KEY_TITLE to download.title,
                DownloadWorker.KEY_ARTIST to download.artist,
                DownloadWorker.KEY_FORMAT to download.format,
                DownloadWorker.KEY_IMAGE_URL to download.imageUrl
            ))
            .addTag("download")
            .addTag("download_${download.mediaId}")
            .build()

        workManager.enqueueUniqueWork(
            "download_${download.mediaId}",
            existingWorkPolicy,
            workRequest
        )
    }

    /**
     * Re-enqueues jobs left waiting by older builds that required Android's
     * validated-internet signal. A configured LAN or USB-forwarded server can
     * be reachable even when that signal is unavailable.
     */
    suspend fun reconcileQueuedDownloads() {
        if (!hasReconciledQueuedDownloads.compareAndSet(false, true)) return

        downloadDao.getQueuedDownloadsOnce().forEach { download ->
            scheduleDownload(download, ExistingWorkPolicy.REPLACE)
        }
    }

    suspend fun cancelDownload(downloadId: String) {
        val download = downloadDao.getDownloadById(downloadId) ?: return

        // Cancel the work
        workManager.cancelUniqueWork("download_${download.mediaId}")

        // Remove from database
        downloadDao.deleteById(downloadId)
    }

    suspend fun pauseDownload(downloadId: String) {
        val download = downloadDao.getDownloadById(downloadId) ?: return

        // Cancel the work (WorkManager doesn't support true pause)
        workManager.cancelUniqueWork("download_${download.mediaId}")

        // Update status
        downloadDao.updateStatus(downloadId, DownloadStatus.PAUSED)
    }

    suspend fun resumeDownload(downloadId: String) {
        val download = downloadDao.getDownloadById(downloadId) ?: return

        if (download.status != DownloadStatus.PAUSED) return

        // Reset status and re-queue
        downloadDao.updateStatus(downloadId, DownloadStatus.QUEUED)

        scheduleDownload(download, ExistingWorkPolicy.REPLACE)
    }

    suspend fun retryDownload(downloadId: String) {
        val download = downloadDao.getDownloadById(downloadId) ?: return

        if (download.status != DownloadStatus.FAILED) return

        // Reset for retry
        downloadDao.resetForRetry(downloadId)

        scheduleDownload(download, ExistingWorkPolicy.REPLACE)
    }

    suspend fun clearCompleted() {
        downloadDao.deleteCompleted()
    }

    suspend fun deleteOfflineSong(songId: String) {
        val offlineSong = offlineSongDao.getOfflineSong(songId) ?: return

        val file = java.io.File(offlineSong.localFilePath)
        if (file.exists() && !file.delete()) {
            throw IOException("Could not delete the offline audio file")
        }

        // Only forget the database entry after the file is gone. Otherwise a
        // failed deletion would leak storage with no way to remove it in-app.
        offlineSongDao.deleteBySongId(songId)
    }

    suspend fun isOfflineAvailable(songId: String): Boolean {
        return offlineSongDao.isOfflineAvailable(songId)
    }

    fun isOfflineAvailableFlow(songId: String): Flow<Boolean> {
        return offlineSongDao.isOfflineAvailableFlow(songId)
    }

    suspend fun getOfflinePath(songId: String): String? {
        return offlineSongDao.getAvailableOfflineSong(songId)?.localFilePath
    }

    suspend fun pauseAll() {
        workManager.cancelAllWorkByTag("download")
        // Also update DB status for all active downloads
        downloadDao.pauseAllActive()
    }

    suspend fun resumeAll() {
        // Get all paused downloads and resume them
        val pausedDownloads = downloadDao.getPausedDownloads()
        pausedDownloads.forEach { download ->
            resumeDownload(download.id)
        }
    }

    /**
     * Cleans up orphaned OfflineSongEntity records where the local file no longer exists.
     * This handles cases where files were deleted externally or through system cleanup.
     * Returns the number of orphaned entries cleaned up.
     */
    suspend fun cleanupOrphanedOfflineSongs(): Int {
        var cleanedUp = 0
        val allOfflineSongs = offlineSongDao.getAllAvailableOnce()

        for (offlineSong in allOfflineSongs) {
            val file = java.io.File(offlineSong.localFilePath)
            if (!file.exists()) {
                // File is missing, mark as unavailable
                offlineSongDao.markUnavailable(offlineSong.songId)
                cleanedUp++
            }
        }

        return cleanedUp
    }

    /**
     * Removes all unavailable offline song entries from the database.
     * Call this after cleanupOrphanedOfflineSongs() to permanently delete the records.
     */
    suspend fun purgeUnavailableOfflineSongs() {
        offlineSongDao.deleteUnavailable()
    }
}
