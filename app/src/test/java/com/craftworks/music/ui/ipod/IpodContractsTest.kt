package com.craftworks.music.ui.ipod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IpodContractsTest {
    @Test
    fun tabOrderIncludesFirstClassAudiobooksNavigation() {
        assertEquals(
            listOf("Playlists", "Artists", "Songs", "Albums", "Books", "More"),
            IpodTab.entries.map { it.label }
        )
    }

    @Test
    fun audiobooksOwnADedicatedTab() {
        assertEquals("Books", IpodTab.AUDIOBOOKS.label)
    }

    @Test
    fun timeFormattingUsesUnboundedMinutesAndTwoDigitSeconds() {
        assertEquals("0:00", formatIpodTime(0L))
        assertEquals("3:07", formatIpodTime(187_000L))
        assertEquals("61:01", formatIpodTime(3_661_000L))
    }

    @Test
    fun sliderTapAndDragPositionsSnapAcrossTheWholeRange() {
        assertEquals(0f, sliderValueForPosition(-10f, 200f, 0f..1f), 0.001f)
        assertEquals(0.25f, sliderValueForPosition(50f, 200f, 0f..1f), 0.001f)
        assertEquals(0.5f, sliderValueForPosition(100f, 200f, 0f..1f), 0.001f)
        assertEquals(1f, sliderValueForPosition(250f, 200f, 0f..1f), 0.001f)
    }

    @Test
    fun volumeFractionMapsToTheDeviceMusicStream() {
        assertEquals(0, volumeIndexForFraction(-1f, 15))
        assertEquals(4, volumeIndexForFraction(0.25f, 15))
        assertEquals(8, volumeIndexForFraction(0.5f, 15))
        assertEquals(15, volumeIndexForFraction(2f, 15))
        assertEquals(0, volumeIndexForFraction(0.5f, 0))
    }

    @Test
    fun missingAndPlaceholderArtworkUseGeneratedFallback() {
        assertTrue(shouldGenerateArtwork(null))
        assertTrue(shouldGenerateArtwork("https://server/coverArt?id=&size=300"))
        assertTrue(shouldGenerateArtwork("https://server/placeholder.png"))
        assertFalse(shouldGenerateArtwork("https://server/coverArt?id=album-7&size=300"))
    }

    @Test
    fun nowPlayingArtworkIsCappedToFirstGenWidthOnTallPhones() {
        val geometry = calculateIpodNowPlayingMediaGeometry(
            containerWidthDp = 360f,
            mediaRegionHeightDp = 600f
        )

        assertEquals(320f, geometry.artworkSizeDp, 0.01f)
        assertEquals(140f, geometry.reflectionHeightDp, 0.01f)
    }

    @Test
    fun nowPlayingArtworkUsesBalancedMirroredFill() {
        val geometry = calculateIpodNowPlayingMediaGeometry(
            containerWidthDp = 320f,
            mediaRegionHeightDp = 500f
        )

        assertEquals(320f, geometry.artworkSizeDp, 0.01f)
        assertEquals(90f, geometry.reflectionHeightDp, 0.01f)
    }
}
