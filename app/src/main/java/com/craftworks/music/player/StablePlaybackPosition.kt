package com.craftworks.music.player

private const val POSITION_REWIND_TOLERANCE_MS = 750L

internal fun stablePlaybackPosition(
    previousPositionMs: Long,
    reportedPositionMs: Long,
    isPlaying: Boolean,
    durationMs: Long = 0L,
    allowDiscontinuity: Boolean = false
): Long {
    val previous = previousPositionMs.coerceAtLeast(0L)
    val reported = reportedPositionMs.coerceAtLeast(0L)

    val resolved = when {
        allowDiscontinuity -> reported
        reportedPositionMs < 0L -> previous
        isPlaying && reported + POSITION_REWIND_TOLERANCE_MS < previous -> previous
        else -> reported
    }

    return if (durationMs > 0L) resolved.coerceIn(0L, durationMs) else resolved
}
