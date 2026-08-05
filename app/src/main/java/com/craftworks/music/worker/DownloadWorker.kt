package com.craftworks.music.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.system.ErrnoException
import android.system.Os
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.media3.common.util.NotificationUtil.IMPORTANCE_LOW
import androidx.media3.common.util.NotificationUtil.createNotificationChannel
import androidx.media3.common.util.UnstableApi
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.craftworks.music.R
import com.craftworks.music.data.database.dao.DownloadDao
import com.craftworks.music.data.database.dao.OfflineSongDao
import com.craftworks.music.data.database.entity.DownloadStatus
import com.craftworks.music.data.database.entity.OfflineSongEntity
import com.craftworks.music.data.requireUsableNavidromeServerUrl
import com.craftworks.music.managers.NavidromeManager
import com.craftworks.music.providers.navidrome.generateSalt
import com.craftworks.music.providers.navidrome.md5Hash
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import androidx.room.withTransaction
import com.craftworks.music.data.database.ChoraDatabase

private val SAFE_EXTENSION = Regex("^[a-z0-9]{1,10}$")
private val UNSAFE_FILE_CHARACTERS = Regex("[^\\p{L}\\p{N} _-]")
private val REPEATED_WHITESPACE = Regex("\\s+")

internal fun buildDownloadFileName(
    title: String,
    artist: String,
    format: String,
    mediaId: String
): String {
    val safeTitle = sanitizeFileComponent(title, "Untitled", 64)
    val safeArtist = sanitizeFileComponent(artist, "Unknown Artist", 48)
    val extensionCandidate = format.trim().removePrefix(".").lowercase(Locale.ROOT)
    val extension = extensionCandidate.takeIf(SAFE_EXTENSION::matches) ?: "audio"
    val mediaSuffix = stableMediaSuffix(mediaId)
    return "$safeTitle - $safeArtist [$mediaSuffix].$extension"
}

private fun sanitizeFileComponent(value: String, fallback: String, maxLength: Int): String {
    val normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .replace(UNSAFE_FILE_CHARACTERS, "")
        .replace(REPEATED_WHITESPACE, " ")
        .trim(' ', '.', '-', '_')
        .take(maxLength)
        .trimEnd()
    return normalized.ifBlank { fallback }
}

private fun stableMediaSuffix(mediaId: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(mediaId.toByteArray(StandardCharsets.UTF_8))
    val hex = "0123456789abcdef"
    return buildString(12) {
        repeat(6) { index ->
            val value = digest[index].toInt() and 0xff
            append(hex[value ushr 4])
            append(hex[value and 0x0f])
        }
    }
}

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val workerParams: WorkerParameters,
    private val database: ChoraDatabase,
    private val downloadDao: DownloadDao,
    private val offlineSongDao: OfflineSongDao
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_MEDIA_ID = "media_id"
        const val KEY_TITLE = "title"
        const val KEY_ARTIST = "artist"
        const val KEY_FORMAT = "format"
        const val KEY_IMAGE_URL = "image_url"

        const val KEY_PROGRESS = "progress"
        const val KEY_BYTES_DOWNLOADED = "bytes_downloaded"
        const val KEY_TOTAL_BYTES = "total_bytes"

        private const val CHANNEL_ID = "download_channel"
        private const val UNKNOWN_LENGTH_REPORT_INTERVAL_BYTES = 256L * 1024L
        private const val DOWNLOAD_READ_TIMEOUT_MS = 5 * 60 * 1000
        private val notificationCounter = AtomicInteger(2000)
    }

    // Unique notification ID for this worker instance
    private val notificationId = notificationCounter.getAndIncrement()

    @androidx.annotation.OptIn(UnstableApi::class)
    override suspend fun doWork(): Result {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: return Result.failure()
        val mediaId = inputData.getString(KEY_MEDIA_ID) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: "Unknown"
        val artist = inputData.getString(KEY_ARTIST) ?: "Unknown"
        val format = inputData.getString(KEY_FORMAT) ?: "mp3"

        Log.d("DownloadWorker", "Starting download for: $title - $artist")

        // Update status to downloading
        downloadDao.updateStatus(downloadId, DownloadStatus.DOWNLOADING)

        // Set foreground with notification
        setForeground(createForegroundInfo(title, artist, null))

        return try {
            val localPath = downloadFile(downloadId, mediaId, title, artist, format)

            // Atomic completion: mark completed AND create offline entry in single transaction
            val file = File(localPath)
            val offlineSong = OfflineSongEntity(
                id = UUID.randomUUID().toString(),
                songId = mediaId,
                localFilePath = localPath,
                fileSize = file.length(),
                downloadedAt = System.currentTimeMillis(),
                lastAccessedAt = System.currentTimeMillis(),
                isAvailable = true
            )

            currentCoroutineContext().ensureActive()
            database.withTransaction {
                val markedCompleted = downloadDao.markCompleted(
                    downloadId,
                    System.currentTimeMillis(),
                    localPath,
                    file.length()
                )
                if (markedCompleted != 1) {
                    throw CancellationException("Download is no longer active")
                }
                offlineSongDao.insert(offlineSong)
            }

            Log.d("DownloadWorker", "Download completed: $localPath")
            Result.success()

        } catch (e: CancellationException) {
            Log.d("DownloadWorker", "Download stopped")
            throw e

        } catch (e: java.net.SocketTimeoutException) {
            Log.e("DownloadWorker", "Network timeout: ${e.message}", e)
            downloadDao.markFailed(downloadId, "Network timeout")
            handleRetry(downloadId)

        } catch (e: java.net.UnknownHostException) {
            Log.e("DownloadWorker", "No network: ${e.message}", e)
            downloadDao.markFailed(downloadId, "No network connection")
            handleRetry(downloadId)

        } catch (e: java.io.IOException) {
            Log.e("DownloadWorker", "IO error: ${e.message}", e)
            downloadDao.markFailed(downloadId, "Download failed: ${e.message}")
            handleRetry(downloadId)

        } catch (e: IllegalStateException) {
            // Config error (no server) - don't retry
            Log.e("DownloadWorker", "Config error: ${e.message}", e)
            downloadDao.markFailed(downloadId, e.message ?: "Configuration error")
            Result.failure()

        } catch (e: Exception) {
            Log.e("DownloadWorker", "Download failed: ${e.message}", e)
            downloadDao.markFailed(downloadId, e.message ?: "Unknown error")
            handleRetry(downloadId)
        }
    }

    private suspend fun handleRetry(downloadId: String): Result {
        val download = downloadDao.getDownloadById(downloadId)
        return if (download != null && download.retryCount < 3) {
            Result.retry()
        } else {
            Result.failure()
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private suspend fun downloadFile(
        downloadId: String,
        mediaId: String,
        title: String,
        artist: String,
        format: String
    ): String = withContext(Dispatchers.IO) {
        val server = NavidromeManager.getCurrentServer()
            ?: throw IllegalStateException("No server configured")
        val serverUrl = requireUsableNavidromeServerUrl(server.url)

        val passwordSalt = generateSalt(8)
        val passwordHash = md5Hash(server.password + passwordSalt)

        // URL encode parameters to handle special characters
        val encodedMediaId = URLEncoder.encode(mediaId, "UTF-8")
        val encodedUsername = URLEncoder.encode(server.username, "UTF-8")
        val encodedHash = URLEncoder.encode(passwordHash, "UTF-8")
        val encodedSalt = URLEncoder.encode(passwordSalt, "UTF-8")

        val downloadUrl = "$serverUrl/rest/download.view?id=$encodedMediaId&u=$encodedUsername&t=$encodedHash&s=$encodedSalt&v=1.16.1&c=Chora"

        Log.d("DownloadWorker", "Downloading from: $serverUrl/rest/download.view?id=$encodedMediaId")

        // Use app-specific external storage (no permissions needed on Android 10+)
        val musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?: throw IllegalStateException("Cannot access music directory")

        if (!musicDir.exists()) {
            musicDir.mkdirs()
        }

        val fileName = buildDownloadFileName(title, artist, format, mediaId)
        val outputFile = File(musicDir, fileName)

        // Each worker owns its temp file, so a resumed replacement cannot race
        // or delete the partial file of a worker that is still winding down.
        val tempFile = File(musicDir, ".$fileName.${workerParams.id}.tmp")

        val url = URL(downloadUrl)
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.connectTimeout = 30000
            // Large local-library files can pause while the server or disk catches up.
            // Keep a finite timeout so dead transfers still retry, but do not kill a
            // healthy audiobook download after a brief 30-second gap.
            connection.readTimeout = DOWNLOAD_READ_TIMEOUT_MS
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("Server returned HTTP $responseCode")
            }

            val contentType = connection.contentType.orEmpty().lowercase(Locale.ROOT)
            if (
                contentType.startsWith("text/") ||
                "json" in contentType ||
                "xml" in contentType
            ) {
                throw IOException("Server returned $contentType instead of audio")
            }

            val totalBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connection.contentLengthLong
            } else {
                connection.contentLength.toLong()
            }
            downloadDao.updateProgress(downloadId, 0f, 0L, totalBytes.coerceAtLeast(0L))

            connection.inputStream.use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    var lastReportedProgress = -1
                    var lastReportedBytes = 0L

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        currentCoroutineContext().ensureActive()
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        // Update progress
                        val progress = if (totalBytes > 0) {
                            (totalBytesRead.toFloat() / totalBytes).coerceIn(0f, 1f)
                        } else {
                            0f
                        }

                        val currentPercentage = (progress * 100).toInt()
                        val shouldReport = if (totalBytes > 0) {
                            lastReportedProgress < 0 ||
                                currentPercentage >= lastReportedProgress + 2 ||
                                currentPercentage == 100
                        } else {
                            lastReportedBytes == 0L ||
                                totalBytesRead - lastReportedBytes >= UNKNOWN_LENGTH_REPORT_INTERVAL_BYTES
                        }

                        if (shouldReport) {
                            lastReportedProgress = currentPercentage
                            lastReportedBytes = totalBytesRead

                            // Update database
                            downloadDao.updateProgress(
                                downloadId,
                                progress,
                                totalBytesRead,
                                totalBytes.coerceAtLeast(0L)
                            )

                            // Update notification
                            setForeground(
                                createForegroundInfo(
                                    title,
                                    artist,
                                    currentPercentage.takeIf { totalBytes > 0 }
                                )
                            )

                            // Set progress for observers
                            setProgress(workDataOf(
                                KEY_PROGRESS to progress,
                                KEY_BYTES_DOWNLOADED to totalBytesRead,
                                KEY_TOTAL_BYTES to totalBytes
                            ))
                        }
                    }

                    if (totalBytesRead == 0L) {
                        throw EOFException("Server returned an empty download")
                    }
                    if (totalBytes > 0L && totalBytesRead != totalBytes) {
                        throw EOFException("Download ended before all bytes were received")
                    }

                    val finalSize = if (totalBytes > 0L) totalBytes else totalBytesRead
                    downloadDao.updateProgress(downloadId, 1f, totalBytesRead, finalSize)
                    setProgress(
                        workDataOf(
                            KEY_PROGRESS to 1f,
                            KEY_BYTES_DOWNLOADED to totalBytesRead,
                            KEY_TOTAL_BYTES to finalSize
                        )
                    )
                }
            }

            currentCoroutineContext().ensureActive()
            try {
                // Both files are in the same app directory, so this atomically
                // replaces any older copy without exposing a partial download.
                Os.rename(tempFile.absolutePath, outputFile.absolutePath)
            } catch (e: ErrnoException) {
                throw IOException("Could not finalize downloaded audio", e)
            }

            outputFile.absolutePath
        } catch (e: Exception) {
            // This worker owns only its uniquely named temp file. Never delete
            // the final path here because a replacement worker may own it.
            tempFile.delete()
            throw e
        } finally {
            // Always disconnect the connection
            connection.disconnect()
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun createForegroundInfo(
        title: String,
        artist: String,
        progressPercent: Int?
    ): ForegroundInfo {
        createNotificationChannel(
            context,
            CHANNEL_ID,
            R.string.Notification_Download_Name,
            R.string.Notification_Download_Desc,
            IMPORTANCE_LOW
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.Notification_Download_Progress))
            .setContentText("$title - $artist")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progressPercent ?: 0, progressPercent == null)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }
}
