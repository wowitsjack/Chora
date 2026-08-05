package com.craftworks.music.ui.elements

private val COVER_ART_SIZE_PARAMETER = Regex("([?&])size=\\d+(?=(&|$))", RegexOption.IGNORE_CASE)

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
