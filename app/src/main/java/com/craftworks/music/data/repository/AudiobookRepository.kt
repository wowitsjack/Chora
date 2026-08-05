package com.craftworks.music.data.repository

import androidx.media3.common.MediaItem
import com.craftworks.music.data.database.dao.AlbumDao
import com.craftworks.music.data.database.dao.AudiobookProgressDao
import com.craftworks.music.data.database.dao.OfflineSongDao
import com.craftworks.music.data.database.dao.SongDao
import com.craftworks.music.data.database.entity.AudiobookProgressEntity
import com.craftworks.music.data.database.entity.toMediaDataAlbum
import com.craftworks.music.data.database.entity.toMediaDataSong
import com.craftworks.music.data.model.toMediaItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class AudiobookBook(
    val album: MediaItem,
    val parts: List<MediaItem>,
    val currentPartId: String?,
    val resumePositionMs: Long,
    val progressFraction: Float,
    val remainingMs: Long,
    val playbackSpeed: Float,
    val isFinished: Boolean,
    val isDownloaded: Boolean,
    val downloadedPartIds: Set<String>,
    val hasPendingProgress: Boolean,
    val updatedAt: Long
) {
    val id: String
        get() = album.mediaMetadata.extras?.getString("navidromeID") ?: album.mediaId

    val hasStarted: Boolean
        get() = resumePositionMs > 0L || progressFraction > 0f

    fun isPartDownloaded(part: MediaItem): Boolean {
        val partId = part.mediaMetadata.extras?.getString("navidromeID") ?: return false
        return partId in downloadedPartIds
    }
}

@Singleton
class AudiobookRepository @Inject constructor(
    private val albumDao: AlbumDao,
    private val songDao: SongDao,
    private val progressDao: AudiobookProgressDao,
    private val offlineSongDao: OfflineSongDao
) {
    val books: Flow<List<AudiobookBook>> = combine(
        albumDao.getAllAudiobooks(),
        songDao.getAllAudiobookParts(),
        progressDao.observeAll(),
        offlineSongDao.getAvailableOfflineSongs()
    ) { albums, parts, progress, offline ->
        val partsByAlbum = parts.groupBy { it.albumId }
        val progressBySong = progress.associateBy { it.songId }
        val offlineIds = offline.mapTo(hashSetOf()) { it.songId }

        albums.map { album ->
            buildAudiobookBook(
                album = album.toMediaDataAlbum().toMediaItem(),
                parts = partsByAlbum[album.navidromeID].orEmpty().map {
                    it.toMediaDataSong().toMediaItem()
                },
                progressBySong = progressBySong,
                offlineIds = offlineIds
            )
        }
    }

    fun observeBook(albumId: String): Flow<AudiobookBook?> =
        books.map { list -> list.firstOrNull { it.id == albumId } }

    suspend fun getBook(albumId: String): AudiobookBook? {
        val album = albumDao.getAlbumById(albumId) ?: return null
        val parts = songDao.getAudiobookPartsByAlbumOnce(albumId)
        val progress = progressDao.getAllOnce().associateBy { it.songId }
        val offline = offlineSongDao.getOfflineSongIdsFromList(parts.map { it.navidromeID }).toSet()
        return buildAudiobookBook(
            album = album.toMediaDataAlbum().toMediaItem(),
            parts = parts.map { it.toMediaDataSong().toMediaItem() },
            progressBySong = progress,
            offlineIds = offline
        )
    }

    suspend fun getBooks(): List<AudiobookBook> {
        val albums = albumDao.getAllAudiobooksOnce()
        val parts = songDao.getAllAudiobookPartsOnce()
        val progress = progressDao.getAllOnce().associateBy { it.songId }
        val offlineIds = offlineSongDao.getAllOfflineSongIds().toSet()
        val partsByAlbum = parts.groupBy { it.albumId }
        return albums.map { album ->
            buildAudiobookBook(
                album = album.toMediaDataAlbum().toMediaItem(),
                parts = partsByAlbum[album.navidromeID].orEmpty().map {
                    it.toMediaDataSong().toMediaItem()
                },
                progressBySong = progress,
                offlineIds = offlineIds
            )
        }
    }

    private fun buildAudiobookBook(
        album: MediaItem,
        parts: List<MediaItem>,
        progressBySong: Map<String, AudiobookProgressEntity>,
        offlineIds: Set<String>
    ): AudiobookBook {
        val partProgress = parts.mapNotNull { part ->
            val id = part.mediaMetadata.extras?.getString("navidromeID") ?: return@mapNotNull null
            progressBySong[id]
        }
        val latest = partProgress.maxByOrNull { it.updatedAt }
        val totalDuration = parts.sumOf { it.mediaMetadata.durationMs?.coerceAtLeast(0L) ?: 0L }
        val listenedDuration = parts.sumOf { part ->
            val id = part.mediaMetadata.extras?.getString("navidromeID")
            val duration = part.mediaMetadata.durationMs?.coerceAtLeast(0L) ?: 0L
            val itemProgress = id?.let(progressBySong::get)
            when {
                itemProgress == null -> 0L
                itemProgress.completed -> duration
                duration > 0L -> itemProgress.positionMs.coerceIn(0L, duration)
                else -> itemProgress.positionMs.coerceAtLeast(0L)
            }
        }
        val fraction = if (totalDuration > 0L) {
            (listenedDuration.toDouble() / totalDuration.toDouble()).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }
        val remaining = (totalDuration - listenedDuration).coerceAtLeast(0L)
        val finished = totalDuration > 0L && (fraction >= 0.95f || remaining <= 60_000L)
        val allDownloaded = parts.isNotEmpty() && parts.all { part ->
            val partId = part.mediaMetadata.extras?.getString("navidromeID")
                ?: return@all false
            partId in offlineIds
        }

        return AudiobookBook(
            album = album,
            parts = parts,
            currentPartId = latest?.songId,
            resumePositionMs = latest?.positionMs ?: 0L,
            progressFraction = fraction,
            remainingMs = remaining,
            playbackSpeed = latest?.playbackSpeed?.let(::normalizedAudiobookSpeed) ?: 1f,
            isFinished = finished,
            isDownloaded = allDownloaded,
            downloadedPartIds = offlineIds.intersect(parts.mapNotNullTo(hashSetOf()) { part ->
                part.mediaMetadata.extras?.getString("navidromeID")
            }),
            hasPendingProgress = partProgress.any { it.syncPending },
            updatedAt = latest?.updatedAt ?: 0L
        )
    }
}
