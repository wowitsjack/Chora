package com.craftworks.music.ui.elements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class ArtworkQualityTest {
    @Test
    fun replacesExistingCoverArtSizeWithoutChangingOtherParameters() {
        assertEquals(
            "https://server/rest/getCoverArt.view?id=album-7&size=1024&u=user",
            artworkDataAtSize(
                "https://server/rest/getCoverArt.view?id=album-7&size=128&u=user",
                1024
            )
        )
    }

    @Test
    fun appendsSizeToCoverArtUrlThatDoesNotHaveOne() {
        assertEquals(
            "https://server/rest/getCoverArt.view?id=album-7&u=user&size=1024",
            artworkDataAtSize(
                "https://server/rest/getCoverArt.view?id=album-7&u=user",
                1024
            )
        )
    }

    @Test
    fun leavesLocalAndUnrelatedArtworkDataUntouched() {
        val localArtwork = Any()

        assertSame(localArtwork, artworkDataAtSize(localArtwork, 1024))
        assertEquals(
            "https://images.example/album-7.jpg",
            artworkDataAtSize("https://images.example/album-7.jpg", 1024)
        )
    }

    @Test
    fun rejectsInvalidTargetSize() {
        assertThrows(IllegalArgumentException::class.java) {
            coverArtUrlAtSize("https://server/coverArt?id=album-7", 0)
        }
    }

    @Test
    fun rapidTrackChangesAlwaysReceiveADistinctRenderKey() {
        val requestKey = artworkRequestKey(
            namespace = "player",
            identity = "song-1",
            artworkData = "https://server/coverArt?id=album-7&token=secret",
            size = 512
        )

        assertEquals(
            requestKey,
            artworkRequestKey(
                namespace = "player",
                identity = "song-2",
                artworkData = "https://server/coverArt?id=album-7&token=secret",
                size = 512
            )
        )
        org.junit.Assert.assertNotEquals(
            artworkRenderKey("song-1", requestKey),
            artworkRenderKey("song-2", requestKey)
        )
    }

    @Test
    fun changedArtworkCannotReuseAStaleCacheEntry() {
        val first = artworkRequestKey("player", "song-1", "https://server/coverArt?id=first", 512)
        val second = artworkRequestKey("player", "song-1", "https://server/coverArt?id=second", 512)

        org.junit.Assert.assertNotEquals(first, second)
        org.junit.Assert.assertFalse(first.contains("https://"))
    }
}
