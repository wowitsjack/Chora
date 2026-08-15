package com.craftworks.music.providers.navidrome

import com.craftworks.music.data.requireUsableNavidromeServerUrl
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Serializable
data class StemSplitResponse(
    val trackId: String = "",
    val status: String = "idle",
    val progress: Int = 0,
    val message: String = "",
    val stem: List<String> = emptyList(),
    val profile: String = "4stems",
    val bitrate: Int = 320
) {
    val isReady: Boolean get() = status == "ready"
    val isWorking: Boolean get() = status in setOf("checking", "queued", "uploading", "splitting", "finalizing")
    val isTerminalFailure: Boolean get() = status == "failed" || status == "disabled"
}

fun parseNavidromeStemSplitJSON(response: String): StemSplitResponse? {
    val parser = Json { ignoreUnknownKeys = true }
    val element = parser.parseToJsonElement(response).jsonObject["subsonic-response"]
        ?: return null
    return runCatching {
        parser.decodeFromJsonElement<SubsonicResponse>(element).stemSplit
    }.getOrNull()
}

internal val supportedStemNames = listOf("vocals", "drums", "bass", "other")
internal val supportedStemProfiles = listOf("2stems", "4stems")
internal val supportedStemBitrates = listOf(192, 256, 320)

internal fun buildNavidromeStemStreamUrl(
    serverUrl: String,
    username: String,
    password: String,
    songId: String,
    stem: String,
    profile: String = "4stems",
    bitrate: Int = 320,
    salt: String = generateSalt(8)
): String {
    require(profile in supportedStemProfiles) { "Unsupported stem profile: $profile" }
    val profileStems = if (profile == "2stems") listOf("vocals", "instrumental") else supportedStemNames
    require(stem in profileStems) { "Unsupported stem: $stem" }
    fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    val query = buildString {
        append("id=${encode(songId)}")
        append("&stem=${encode(stem)}")
        append("&profile=${encode(profile)}")
        append("&bitrate=${bitrate.coerceIn(192, 320)}")
        append("&u=${encode(username)}")
        append("&t=${md5Hash(password + salt)}")
        append("&s=${encode(salt)}")
        append("&v=1.16.1&c=Chora")
    }
    return "${requireUsableNavidromeServerUrl(serverUrl)}/rest/streamStem.view?$query"
}
