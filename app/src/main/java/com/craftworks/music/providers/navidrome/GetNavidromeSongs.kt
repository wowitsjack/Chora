package com.craftworks.music.providers.navidrome

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.craftworks.music.data.model.DiscoveryMetadataKeys
import com.craftworks.music.data.model.MediaData
import com.craftworks.music.data.model.albumList
import com.craftworks.music.data.model.artistList
import com.craftworks.music.data.model.songsList
import com.craftworks.music.data.model.toMediaItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import java.net.URLEncoder

@Serializable
data class SearchResult3(
    val song: List<MediaData.Song>? = listOf(),
    val album: List<MediaData.Album>? = listOf(),
    val artist: List<MediaData.Artist>? = listOf(),
)

@Serializable
data class SmartMixResponse(
    val mode: String = "smart",
    val analyzedTracks: Int = 0,
    val song: List<MediaData.Song> = emptyList(),
    val match: List<SmartMixMatch> = emptyList()
)

@Serializable
data class SmartMixMatch(
    val entry: MediaData.Song,
    val score: Float = 0f,
    val similarity: Float = 0f,
    val analysis: DiscoveryTrackAnalysis = DiscoveryTrackAnalysis(),
    val reason: List<DiscoveryReason> = emptyList()
)

@Serializable
data class DiscoveryTrackAnalysis(
    val available: Boolean = false,
    val bpm: Float = 0f,
    val bpmConfidence: Float = 0f,
    val key: String = "",
    val camelot: String = "",
    val keyConfidence: Float = 0f,
    val danceability: Float = 0f,
    val energy: Float = 0f,
    val arousal: Float = 0f,
    val valence: Float = 0f,
    val voiceInstrumental: String = "",
    val voiceConfidence: Float = 0f,
    val loudnessLUFS: Float = 0f,
    val loudnessRange: Float = 0f,
    val hasEmbedding: Boolean = false,
    val hasStructure: Boolean = false,
    val structureSectionCount: Int = 0,
    val mood: DiscoveryMood = DiscoveryMood(),
    val genre: List<DiscoveryGenre> = emptyList()
)

@Serializable
data class DiscoveryMood(
    val happy: Float = 0f,
    val sad: Float = 0f,
    val aggressive: Float = 0f,
    val relaxed: Float = 0f,
    val party: Float = 0f
) {
    fun dominantLabel(): String = listOf(
        "Happy" to happy,
        "Sad" to sad,
        "Aggressive" to aggressive,
        "Relaxed" to relaxed,
        "Party" to party
    ).maxByOrNull { it.second }?.takeIf { it.second > 0f }?.first.orEmpty()
}

@Serializable
data class DiscoveryGenre(
    val label: String = "",
    val score: Float = 0f
)

@Serializable
data class DiscoveryReason(
    val code: String = "",
    val label: String = "",
    val strength: Float = 0f
)

@Serializable
data class DiscoveryStatusResponse(
    val enabled: Boolean = false,
    val loaded: Boolean = false,
    val provider: String = "",
    val analyzerTracks: Int = 0,
    val embeddingTracks: Int = 0,
    val lastError: String = ""
)

@OptIn(UnstableApi::class)
fun parseNavidromeRandomSongsJSON(
    response: String,
    navidromeUrl: String,
    navidromeUsername: String,
    navidromePassword: String
): List<MediaItem> {
    return parseNavidromeSongCollectionJSON(
        response = response,
        navidromeUrl = navidromeUrl,
        navidromeUsername = navidromeUsername,
        navidromePassword = navidromePassword
    ) { it.randomSongs?.song }
}

@OptIn(UnstableApi::class)
fun parseNavidromeSimilarSongsJSON(
    response: String,
    navidromeUrl: String,
    navidromeUsername: String,
    navidromePassword: String
): List<MediaItem> {
    return parseNavidromeSongCollectionJSON(
        response = response,
        navidromeUrl = navidromeUrl,
        navidromeUsername = navidromeUsername,
        navidromePassword = navidromePassword
    ) { it.similarSongs2?.song }
}

@OptIn(UnstableApi::class)
fun parseNavidromeSmartMixJSON(
    response: String,
    navidromeUrl: String,
    navidromeUsername: String,
    navidromePassword: String
): List<MediaItem> {
    val jsonParser = Json { ignoreUnknownKeys = true }
    val jsonElement = jsonParser.parseToJsonElement(response).jsonObject["subsonic-response"]
        ?: return emptyList()
    val subsonicResponse = runCatching {
        jsonParser.decodeFromJsonElement<SubsonicResponse>(jsonElement)
    }.getOrNull() ?: return emptyList()
    val mix = subsonicResponse.smartMix ?: return emptyList()

    val passwordSaltMedia = generateSalt(8)
    val passwordHashMedia = md5Hash(navidromePassword + passwordSaltMedia)
    val encodedUsername = URLEncoder.encode(navidromeUsername, "UTF-8")
    val matchesById = mix.match.associateBy { it.entry.navidromeID }
    val orderedSongs = if (mix.match.isNotEmpty()) mix.match.map { it.entry } else mix.song

    return orderedSongs.map { song ->
        val item = song.withNavidromePlaybackUrls(
            navidromeUrl,
            encodedUsername,
            passwordHashMedia,
            passwordSaltMedia
        ).toMediaItem()
        val match = matchesById[song.navidromeID]
        if (match == null) {
            item
        } else {
            val analysis = match.analysis
            val extras = Bundle(item.mediaMetadata.extras ?: Bundle()).apply {
                putString(DiscoveryMetadataKeys.MODE, mix.mode)
                putFloat(DiscoveryMetadataKeys.SCORE, match.score)
                putFloat(DiscoveryMetadataKeys.SIMILARITY, match.similarity)
                putString(
                    DiscoveryMetadataKeys.REASON,
                    match.reason.joinToString(" · ") { it.label }.ifBlank { "Smart musical match" }
                )
                putFloat(DiscoveryMetadataKeys.BPM, analysis.bpm)
                putString(DiscoveryMetadataKeys.KEY, analysis.key)
                putString(DiscoveryMetadataKeys.CAMELOT, analysis.camelot)
                putFloat(DiscoveryMetadataKeys.ENERGY, analysis.energy)
                putFloat(DiscoveryMetadataKeys.AROUSAL, analysis.arousal)
                putFloat(DiscoveryMetadataKeys.VALENCE, analysis.valence)
                putFloat(DiscoveryMetadataKeys.DANCEABILITY, analysis.danceability)
                putString(DiscoveryMetadataKeys.MOOD, analysis.mood.dominantLabel())
                putFloat(DiscoveryMetadataKeys.MOOD_AGGRESSIVE, analysis.mood.aggressive)
                putFloat(DiscoveryMetadataKeys.MOOD_RELAXED, analysis.mood.relaxed)
                putFloat(DiscoveryMetadataKeys.MOOD_PARTY, analysis.mood.party)
                putString(DiscoveryMetadataKeys.GENRE, analysis.genre.firstOrNull()?.label.orEmpty())
                putString(
                    DiscoveryMetadataKeys.GENRES,
                    analysis.genre.joinToString(" | ") { it.label }
                )
                putString(DiscoveryMetadataKeys.VOICE, analysis.voiceInstrumental)
                putFloat(DiscoveryMetadataKeys.VOICE_CONFIDENCE, analysis.voiceConfidence)
            }
            item.buildUpon()
                .setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(extras).build())
                .build()
        }
    }
}

@OptIn(UnstableApi::class)
private fun parseNavidromeSongCollectionJSON(
    response: String,
    navidromeUrl: String,
    navidromeUsername: String,
    navidromePassword: String,
    songs: (SubsonicResponse) -> List<MediaData.Song>?
): List<MediaItem> {
    val jsonParser = Json { ignoreUnknownKeys = true }
    val jsonElement = jsonParser.parseToJsonElement(response).jsonObject["subsonic-response"]
        ?: return emptyList()
    val subsonicResponse = try {
        jsonParser.decodeFromJsonElement<SubsonicResponse>(jsonElement)
    } catch (e: Exception) {
        return emptyList()
    }

    val passwordSaltMedia = generateSalt(8)
    val passwordHashMedia = md5Hash(navidromePassword + passwordSaltMedia)
    val encodedUsername = URLEncoder.encode(navidromeUsername, "UTF-8")

    return songs(subsonicResponse)?.map {
        it.withNavidromePlaybackUrls(
            navidromeUrl,
            encodedUsername,
            passwordHashMedia,
            passwordSaltMedia
        ).toMediaItem()
    } ?: emptyList()
}

private fun MediaData.Song.withNavidromePlaybackUrls(
    navidromeUrl: String,
    encodedUsername: String,
    passwordHash: String,
    passwordSalt: String
): MediaData.Song = copy(
    media = "$navidromeUrl/rest/stream.view?&id=$navidromeID&u=$encodedUsername&t=$passwordHash&s=$passwordSalt&v=1.12.0&c=Chora",
    imageUrl = "$navidromeUrl/rest/getCoverArt.view?&id=$navidromeID&u=$encodedUsername&t=$passwordHash&s=$passwordSalt&v=1.16.1&c=Chora&size=512"
)

@OptIn(UnstableApi::class)
fun parseNavidromeSearch3JSON(
    response: String,
    navidromeUrl: String,
    navidromeUsername: String,
    navidromePassword: String,
) : List<Any> {

    val jsonParser = Json { ignoreUnknownKeys = true }
    val jsonElement = jsonParser.parseToJsonElement(response).jsonObject["subsonic-response"]
        ?: return emptyList()
    val subsonicResponse = try {
        jsonParser.decodeFromJsonElement<SubsonicResponse>(jsonElement)
    } catch (e: Exception) {
        return emptyList()
    }

    // Generate password salt and hash
    val passwordSaltMedia = generateSalt(8)
    val passwordHashMedia = md5Hash(navidromePassword + passwordSaltMedia)
    val encodedUsername = URLEncoder.encode(navidromeUsername, "UTF-8")

    val updatedSongs = subsonicResponse.searchResult3?.song?.map {
        it.copy(
            media = "$navidromeUrl/rest/stream.view?&id=${it.navidromeID}&u=$encodedUsername&t=$passwordHashMedia&s=$passwordSaltMedia&v=1.12.0&c=Chora",
            imageUrl = "$navidromeUrl/rest/getCoverArt.view?&id=${it.navidromeID}&u=$encodedUsername&t=$passwordHashMedia&s=$passwordSaltMedia&v=1.16.1&c=Chora&size=128"
        )
    }

    val updatedAlbums = subsonicResponse.searchResult3?.album?.map {
        it.copy(coverArt = "$navidromeUrl/rest/getCoverArt.view?&id=${it.navidromeID}&u=$encodedUsername&t=$passwordHashMedia&s=$passwordSaltMedia&v=1.16.1&c=Chora&size=128")
    }

    var mediaDataSongs = emptyList<MediaItem>()
    var mediaDataAlbums = emptyList<MediaItem>()
    var mediaDataArtists = emptyList<MediaData.Artist>()

    updatedSongs?.filterNot { newSong ->
        songsList.any { existingSong ->
            existingSong.navidromeID == newSong.navidromeID
        }
    }?.let { mediaDataSongs = it.map {
        it.copy(
            media = "$navidromeUrl/rest/stream.view?&id=${it.navidromeID}&u=$encodedUsername&t=$passwordHashMedia&s=$passwordSaltMedia&v=1.12.0&c=Chora"
        ).toMediaItem()
        }
    }

    updatedAlbums?.filterNot { newAlbum ->
        albumList.any { existingAlbum ->
            existingAlbum.navidromeID == newAlbum.navidromeID
        }
    }?.let { mediaDataAlbums = it.map { it.toMediaItem() } }

    subsonicResponse.searchResult3?.artist?.filterNot { newArtist ->
        artistList.any { existingArtist ->
            existingArtist.navidromeID == newArtist.navidromeID
        }
    }?.let { mediaDataArtists = it }

    return when {
        mediaDataSongs.isNotEmpty() -> mediaDataSongs
        mediaDataAlbums.isNotEmpty() -> mediaDataAlbums
        else -> mediaDataArtists
    }
}
