package com.craftworks.music.providers.navidrome

import androidx.media3.common.MediaItem
import com.craftworks.music.data.model.MediaData
import com.craftworks.music.data.model.NavidromeBookmark
import com.craftworks.music.data.model.toMediaItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import java.net.URLEncoder

fun parseNavidromeSongJSON(
    response: String,
    navidromeUrl: String,
    navidromeUsername: String,
    navidromePassword: String
): List<MediaItem> {
    val parsed = parseSubsonicResponse(response)?.song ?: return emptyList()
    return listOf(
        parsed.withNavidromeMediaUrls(
            navidromeUrl = navidromeUrl,
            navidromeUsername = navidromeUsername,
            navidromePassword = navidromePassword
        ).toMediaItem()
    )
}

fun parseNavidromeBookmarksJSON(
    response: String,
    navidromeUrl: String,
    navidromeUsername: String,
    navidromePassword: String
): List<NavidromeBookmark> {
    return parseSubsonicResponse(response)
        ?.bookmarks
        ?.bookmark
        ?.map { bookmark ->
            bookmark.copy(
                entry = bookmark.entry.withNavidromeMediaUrls(
                    navidromeUrl = navidromeUrl,
                    navidromeUsername = navidromeUsername,
                    navidromePassword = navidromePassword
                )
            )
        }
        .orEmpty()
}

private fun parseSubsonicResponse(response: String): SubsonicResponse? {
    val parser = Json { ignoreUnknownKeys = true }
    val element = parser.parseToJsonElement(response).jsonObject["subsonic-response"]
        ?: return null
    return runCatching {
        parser.decodeFromJsonElement<SubsonicResponse>(element)
    }.getOrNull()
}

private fun MediaData.Song.withNavidromeMediaUrls(
    navidromeUrl: String,
    navidromeUsername: String,
    navidromePassword: String
): MediaData.Song {
    val salt = generateSalt(8)
    val token = md5Hash(navidromePassword + salt)
    val username = URLEncoder.encode(navidromeUsername, "UTF-8")
    val encodedId = URLEncoder.encode(navidromeID, "UTF-8")
    return copy(
        media = "$navidromeUrl/rest/stream.view?id=$encodedId&u=$username&t=$token&s=$salt&v=1.16.1&c=Chora",
        imageUrl = "$navidromeUrl/rest/getCoverArt.view?id=$encodedId&u=$username&t=$token&s=$salt&v=1.16.1&c=Chora&size=512"
    )
}
