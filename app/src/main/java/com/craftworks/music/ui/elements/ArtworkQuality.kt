package com.craftworks.music.ui.elements

private val COVER_ART_SIZE_PARAMETER = Regex("([?&])size=\\d+(?=(&|$))", RegexOption.IGNORE_CASE)

internal const val PLAYER_CARD_ARTWORK_DEBOUNCE_MS = 150L

/**
 * Requests a larger Navidrome/Subsonic cover while leaving local and unrelated artwork untouched.
 * Existing synced media can keep its original 128 px URL; display surfaces upgrade it on demand.
 */
internal fun artworkDataAtSize(artwork: Any?, size: Int): Any? {
    if (artwork == null) return null

    val value = artwork.toString()
    val isRemoteCoverArt = (value.startsWith("http://") || value.startsWith("https://")) &&
        (value.contains("getCoverArt", ignoreCase = true) || value.contains("/coverArt", ignoreCase = true))

    return if (isRemoteCoverArt) coverArtUrlAtSize(value, size) else artwork
}

/**
 * Builds a credential-safe cache key from the resolved artwork source. When no source exists,
 * the media identity keeps generated and placeholder artwork isolated per item.
 */
internal fun artworkRequestKey(
    namespace: String,
    identity: String,
    artworkData: Any?,
    size: Int
): String {
    val source = artworkData?.toString()?.takeIf { it.isNotBlank() && it != "null" } ?: identity
    return "${namespace}_${Integer.toHexString(source.hashCode())}_art_$size"
}

/** A render key must change for every media transition even when two tracks share one cover. */
internal fun artworkRenderKey(identity: String, requestKey: String): String = "$identity|$requestKey"

internal fun coverArtUrlAtSize(url: String, size: Int): String {
    require(size > 0) { "Artwork size must be positive" }

    if (COVER_ART_SIZE_PARAMETER.containsMatchIn(url)) {
        return COVER_ART_SIZE_PARAMETER.replace(url) { match ->
            "${match.groupValues[1]}size=$size"
        }
    }

    val separator = if ('?' in url) '&' else '?'
    return "${url}${separator}size=$size"
}
