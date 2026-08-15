package com.craftworks.music.player

import org.junit.Assert.assertEquals
import org.junit.Test

class StablePlaybackPositionTest {
    @Test
    fun `invalid session refresh does not rewind active playback`() {
        assertEquals(
            3_000L,
            stablePlaybackPosition(
                previousPositionMs = 3_000L,
                reportedPositionMs = -1L,
                isPlaying = true
            )
        )
    }

    @Test
    fun `periodic zero position does not create a three second loop`() {
        assertEquals(
            3_000L,
            stablePlaybackPosition(
                previousPositionMs = 3_000L,
                reportedPositionMs = 0L,
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
                isPlaying = true,
                allowDiscontinuity = true
            )
        )
    }

    @Test
    fun `reported position never exceeds known duration`() {
        assertEquals(
            10_000L,
            stablePlaybackPosition(
                previousPositionMs = 9_900L,
                reportedPositionMs = 10_500L,
                isPlaying = true,
                durationMs = 10_000L
            )
        )
    }

    @Test
    fun `media player remains authoritative for normal clock corrections`() {
        assertEquals(
            4_600L,
            stablePlaybackPosition(
                previousPositionMs = 5_000L,
                reportedPositionMs = 4_600L,
                isPlaying = true
            )
        )
    }

    @Test
    fun `stale player report is held instead of inventing elapsed playback`() {
        assertEquals(
            5_000L,
            stablePlaybackPosition(
                previousPositionMs = 5_000L,
                reportedPositionMs = 3_000L,
                isPlaying = true
            )
        )
    }
}
