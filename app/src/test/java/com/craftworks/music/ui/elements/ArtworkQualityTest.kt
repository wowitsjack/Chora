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
}
