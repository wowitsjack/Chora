package com.craftworks.music.data.repository

import android.util.Log
import androidx.media3.common.MediaItem
import com.craftworks.music.data.database.dao.AlbumDao
import com.craftworks.music.data.database.entity.toMediaDataAlbum
import com.craftworks.music.data.datasource.local.LocalDataSource
import com.craftworks.music.data.datasource.navidrome.NavidromeDataSource
import com.craftworks.music.data.model.MediaData
import com.craftworks.music.data.model.MediaCategory
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
class ArtistRepository @Inject constructor(
    private val localDataSource: LocalDataSource,
    private val navidromeDataSource: NavidromeDataSource,
    private val albumDao: AlbumDao
) {

    suspend fun getArtists(
        sort: String? = "alphabeticalByName",
        size: Int? = 100,
        offset: Int? = 0,
        ignoreCachedResponse: Boolean = false
    ): List<MediaData.Artist> = supervisorScope {
        val deferredArtists = mutableListOf<Deferred<List<MediaData.Artist>>>()

        if (LocalProviderManager.checkActiveFolders())
            if (offset == 0)
                deferredArtists.add(async {
                    try {
                        localDataSource.getLocalArtists()
                    } catch (e: Exception) {
                        Log.e("ArtistRepository", "Failed to fetch local artists", e)
                        emptyList()
                    }
                })

        if (NavidromeManager.checkActiveServers()) {
            deferredArtists.add(async {
                try {
                    val libraryIds = NavidromeManager.getEnabledLibraryIdsForCurrentServer(MediaCategory.MUSIC)
                    if (libraryIds.isEmpty()) emptyList() else navidromeDataSource.getNavidromeArtists(
                        ignoreCachedResponse = ignoreCachedResponse,
                        musicFolderIds = libraryIds
                    )
                } catch (e: Exception) {
                    Log.e("ArtistRepository", "Failed to fetch Navidrome artists", e)
                    emptyList()
                }
            })
        }

        deferredArtists.awaitAll().flatten()
    }

    suspend fun searchArtists(
        query: String,
        ignoreCachedResponse: Boolean = false
    ): List<MediaData.Artist> = supervisorScope {
        val deferredArtists = mutableListOf<Deferred<List<MediaData.Artist>>>()

        if (LocalProviderManager.checkActiveFolders())
            deferredArtists.add(async {
                try {
                    localDataSource.searchLocalArtists(query)
                } catch (e: Exception) {
                    Log.e("ArtistRepository", "Failed to search local artists", e)
                    emptyList()
                }
            })

        if (NavidromeManager.checkActiveServers())
            deferredArtists.add(async {
                try {
                    val libraryIds = NavidromeManager.getEnabledLibraryIdsForCurrentServer(MediaCategory.MUSIC)
                    if (libraryIds.isEmpty()) emptyList() else navidromeDataSource.searchNavidromeArtists(
                        query,
                        ignoreCachedResponse,
                        musicFolderIds = libraryIds
                    )
                } catch (e: Exception) {
                    Log.e("ArtistRepository", "Failed to search Navidrome artists", e)
                    emptyList()
                }
            })

        deferredArtists.awaitAll().flatten()
    }

    suspend fun getArtistAlbums(
        artistId: String,
        artistName: String? = null,
        ignoreCachedResponse: Boolean = false
    ): List<MediaItem> = supervisorScope {
        if (artistId.startsWith("Local_")) {
            async { localDataSource.getLocalArtistAlbums(artistId) }.await()
        } else {
            // Cache-first strategy: check Room database first
            if (!ignoreCachedResponse) {
                val cachedById = async { albumDao.getAlbumsByArtistOnce(artistId) }.await()
                val cachedAlbums = if (cachedById.isNotEmpty() || artistName.isNullOrBlank()) {
                    cachedById
                } else {
                    async { albumDao.getAlbumsByArtistNameOnce(artistName) }.await()
                }
                if (cachedAlbums.isNotEmpty()) {
                    return@supervisorScope cachedAlbums.map { it.toMediaDataAlbum().toMediaItem() }
                }
            }
            async {
                navidromeDataSource.getNavidromeArtistAlbums(artistId, ignoreCachedResponse)
                    .filter { it.mediaMetadata.extras?.getString("mediaCategory") != MediaCategory.AUDIOBOOK }
            }.await()
        }
    }

    suspend fun getArtistInfo(artistId: String, ignoreCachedResponse: Boolean = false): MediaData.ArtistInfo? = supervisorScope {
        if (artistId.startsWith("Local_"))
            null
        else
            async { navidromeDataSource.getNavidromeArtistInfo(artistId, ignoreCachedResponse) }.await()
}
}
