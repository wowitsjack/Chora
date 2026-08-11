package com.craftworks.music.data.repository

import android.util.Log
import androidx.media3.common.MediaItem
import com.craftworks.music.data.database.dao.AlbumDao
import com.craftworks.music.data.database.dao.SongDao
import com.craftworks.music.data.database.entity.toMediaDataAlbum
import com.craftworks.music.data.database.entity.toMediaDataSong
import com.craftworks.music.data.datasource.local.LocalDataSource
import com.craftworks.music.data.datasource.navidrome.NavidromeDataSource
import com.craftworks.music.data.model.toMediaItem
import com.craftworks.music.data.model.MediaCategory
import com.craftworks.music.managers.LocalProviderManager
import com.craftworks.music.managers.NavidromeManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlbumRepository @Inject constructor(
    private val localDataSource: LocalDataSource,
    private val navidromeDataSource: NavidromeDataSource,
    private val albumDao: AlbumDao,
    private val songDao: SongDao
) {
    suspend fun getAlbums(
        sort: String? = "alphabeticalByName",
        size: Int? = 100,
        offset: Int? = 0,
        ignoreCachedResponse: Boolean = false
    ): List<MediaItem> = supervisorScope {
        val deferredAlbums = mutableListOf<Deferred<List<MediaItem>>>()

        if (NavidromeManager.checkActiveServers())
            deferredAlbums.add(async {
                try {
                    val libraryIds = NavidromeManager.getEnabledLibraryIdsForCurrentServer(MediaCategory.MUSIC)
                    if (libraryIds.isEmpty()) emptyList() else navidromeDataSource.getNavidromeAlbums(
                        sort,
                        size,
                        offset,
                        ignoreCachedResponse,
                        musicFolderIds = libraryIds
                    ).filter { it.mediaMetadata.extras?.getString("mediaCategory") != MediaCategory.AUDIOBOOK }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("AlbumRepository", "Failed to fetch Navidrome albums", e)
                    emptyList()
                }
            })

        if (LocalProviderManager.checkActiveFolders())
            if (offset == 0)
                deferredAlbums.add(async {
                    try {
                        localDataSource.getLocalAlbums(sort)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e("AlbumRepository", "Failed to fetch local albums", e)
                        emptyList()
                    }
                })


        deferredAlbums.awaitAll().flatten()
    }

    suspend fun getAlbum(albumId: String, ignoreCachedResponse: Boolean = false): List<MediaItem>? = coroutineScope {
        if (albumId.startsWith("Local_")) {
            localDataSource.getLocalAlbum(albumId)?.let { withoutHiddenSongs(it) }
        } else {
            // Cache-first strategy: check Room database first
            if (!ignoreCachedResponse) {
                val album = albumDao.getAlbumById(albumId)
                if (album?.mediaCategory == MediaCategory.AUDIOBOOK) {
                    return@coroutineScope emptyList()
                }
                val cachedById = songDao.getSongsByAlbumOnce(albumId)
                val cachedSongs = if (cachedById.isNotEmpty()) {
                    cachedById
                } else {
                    album?.name?.takeIf { it.isNotBlank() }
                        ?.let { songDao.getSongsByAlbumNameOnce(it) }
                        .orEmpty()
                }
                if (cachedSongs.isNotEmpty()) {
                    return@coroutineScope listOfNotNull(album?.toMediaDataAlbum()?.toMediaItem()) +
                        cachedSongs.map { it.toMediaDataSong().toMediaItem() }
                }
            }
            navidromeDataSource.getNavidromeAlbum(albumId, ignoreCachedResponse)
                ?.filter { item ->
                    item.mediaMetadata.extras?.getString("mediaCategory") != MediaCategory.AUDIOBOOK
                }
                ?.let { withoutHiddenSongs(it) }
        }
    }

    suspend fun searchAlbum(query: String): List<MediaItem> = supervisorScope {
        val deferredAlbums = mutableListOf<Deferred<List<MediaItem>>>()

        if (LocalProviderManager.checkActiveFolders())
            deferredAlbums.add(async {
                try {
                    localDataSource.searchLocalAlbums(query)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("AlbumRepository", "Failed to search local albums", e)
                    emptyList()
                }
            })

        if (NavidromeManager.checkActiveServers())
            deferredAlbums.add(async {
                try {
                    val libraryIds = NavidromeManager.getEnabledLibraryIdsForCurrentServer(MediaCategory.MUSIC)
                    if (libraryIds.isEmpty()) emptyList() else navidromeDataSource.searchNavidromeAlbums(
                        query,
                        musicFolderIds = libraryIds
                    ).filter { it.mediaMetadata.extras?.getString("mediaCategory") != MediaCategory.AUDIOBOOK }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("AlbumRepository", "Failed to search Navidrome albums", e)
                    emptyList()
                }
            })

        deferredAlbums.awaitAll().flatten()
    }

    private suspend fun withoutHiddenSongs(items: List<MediaItem>): List<MediaItem> {
        val hiddenIds = songDao.getHiddenSongIds().toHashSet()
        if (hiddenIds.isEmpty()) return items
        return items.filterNot { item ->
            item.mediaMetadata.mediaType != androidx.media3.common.MediaMetadata.MEDIA_TYPE_ALBUM &&
                (item.mediaMetadata.extras?.getString("navidromeID") ?: item.mediaId) in hiddenIds
        }
    }
}
