package com.craftworks.music.player

import org.junit.Assert.assertEquals
import org.junit.Test

class StablePlaybackPositionTest {
    @Test
    fun `invalid session refresh does not rewind active playback`() {
        assertEquals(
            3_500L,
            stablePlaybackPosition(
                previousPositionMs = 3_000L,
                reportedPositionMs = -1L,
                elapsedMs = 500L,
                isPlaying = true
            )
        )
    }

    @Test
    fun `periodic zero position does not create a three second loop`() {
        assertEquals(
            3_500L,
            stablePlaybackPosition(
                previousPositionMs = 3_000L,
                reportedPositionMs = 0L,
                elapsedMs = 500L,
                isPlaying = true
            )
        )
    }

    @Test
    fun `explicit seek may move playback backwards`() {
        assertEquals(
            1_000L,
            stablePlaybackPosition(
                previousPositionMs = 30_000L,
                reportedPositionMs = 1_000L,
                elapsedMs = 500L,
                isPlaying = true,
                allowDiscontinuity = true
            )
        )
    }

    @Test
    fun `projection never exceeds known duration`() {
        assertEquals(
            10_000L,
            stablePlaybackPosition(
                previousPositionMs = 9_900L,
                reportedPositionMs = -1L,
                elapsedMs = 500L,
                isPlaying = true,
                durationMs = 10_000L
            )
        )
    }
}
