package com.craftworks.music.data

import com.craftworks.music.data.model.MediaCategory
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URI

@Serializable
data class NavidromeProvider (
    val id: String = "0",
    var url:String,
    var username:String,
    val password:String,
    val enabled:Boolean? = true,
    var allowSelfSignedCert: Boolean? = false,
    // List of library folders and if they're enabled or not.
    var libraryIds: List<Pair<NavidromeLibrary, Boolean>> = listOf(Pair(NavidromeLibrary(0, "Media Library"), true)),
    var fallbackUrls: List<String> = emptyList()
)

@Serializable
data class NavidromeLibrary (
    val id: Int = 0,
    var name:String,
    @SerialName("type")
    val kind: String? = null,
)

val NavidromeLibrary.mediaCategory: String
    get() = MediaCategory.resolve(explicit = kind, libraryName = name)

internal fun normalizeNavidromeServerUrl(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null

    val candidate = when {
        trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        "://" in trimmed -> return null
        else -> "http://$trimmed"
    }

    val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
    if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
    if (uri.host.isNullOrBlank() || uri.userInfo != null || uri.query != null || uri.fragment != null) {
        return null
    }

    return candidate.trimEnd('/')
}

internal fun navidromeConnectionUrls(server: NavidromeProvider): List<String> =
    (listOf(server.url) + server.fallbackUrls)
        .mapNotNull(::normalizeNavidromeServerUrl)
        .distinct()

internal fun normalizeNavidromeFallbackUrls(input: String, primaryUrl: String): List<String>? {
    val candidates = input.lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toList()
    val normalized = candidates.map { normalizeNavidromeServerUrl(it) ?: return null }
    val primary = normalizeNavidromeServerUrl(primaryUrl)
    return normalized.distinct().filterNot { it == primary }
}

internal fun navidromeServerUrlConnectionProblem(serverUrl: String): String? {
    val normalized = normalizeNavidromeServerUrl(serverUrl) ?: return "Invalid URL"
    val host = URI(normalized).host?.lowercase() ?: return "Invalid URL"
    return if (host.isDeviceLoopbackHost()) {
        "Loopback URL points to this device"
    } else {
        null
    }
}

internal fun requireUsableNavidromeServerUrl(serverUrl: String): String {
    val normalized = normalizeNavidromeServerUrl(serverUrl)
        ?: throw IllegalArgumentException("Invalid URL")
    navidromeServerUrlConnectionProblem(normalized)?.let { problem ->
        throw IllegalArgumentException(problem)
    }
    return normalized
}

private fun String.isDeviceLoopbackHost(): Boolean =
    this == "localhost" ||
        this == "127.0.0.1" ||
        this == "::1" ||
        this == "0:0:0:0:0:0:0:1"
