package com.craftworks.music.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.craftworks.music.data.model.DiscoveryMixMode

class SmartDjSequencerTest {
    @Test
    fun `classical and aggressive electronic are not adjacent when compatible alternatives exist`() {
        val sequence = SmartDjSequencer.sequenceCandidates(
            listOf(
                track("symphony", genre = "Classical; Symphony"),
                track("knife-party", genre = "Dubstep; Bass Music"),
                track("diplo", genre = "Electronic"),
                track("air", genre = "Ambient; Downtempo"),
                track("house", genre = "House"),
                track("techno", genre = "Techno"),
                track("dnb", genre = "Drum and Bass")
            ),
            outputLimit = 7
        ).ids()

        sequence.zipWithNext().forEach { (left, right) ->
            assertFalse(
                "Classical and bass tracks should not be adjacent: $sequence",
                setOf(left, right) == setOf("symphony", "knife-party") ||
                    setOf(left, right) == setOf("symphony", "dnb")
            )
        }

        assertFalse(
            "Air should not be stranded after an aggressive bass track when smoother ordering exists: $sequence",
            sequence.indexOf("air") == sequence.indexOf("knife-party") + 1 ||
                sequence.indexOf("air") == sequence.indexOf("dnb") + 1
        )
    }

    @Test
    fun `exact and shared genres outrank unrelated genres`() {
        val sequence = SmartDjSequencer.sequenceCandidates(
            listOf(
                track("house-seed", genre = "Electronic, House"),
                track("classical", genre = "Classical"),
                track("house-match", genre = "House"),
                track("metal", genre = "Metal")
            ),
            outputLimit = 4
        ).ids()

        assertTrue(sequence.indexOf("house-match") <= 1)
        assertFalse(sequence.take(2).contains("classical"))
        assertFalse(sequence.take(2).contains("metal"))
    }

    @Test
    fun `energy changes are progressive within a compatible electronic lane`() {
        val sequence = SmartDjSequencer.sequenceCandidates(
            listOf(
                track("electronic-mid", genre = "Electronic"),
                track("bass-high", genre = "Dubstep; Bass Music"),
                track("ambient-low", genre = "Ambient; Downtempo"),
                track("house-step", genre = "House")
            ),
            outputLimit = 4
        ).ids()

        assertTrue(
            "A smoother middle-energy track should be selected before the high-energy bass track: $sequence",
            sequence.indexOf("house-step") < sequence.indexOf("bass-high") ||
                sequence.indexOf("electronic-mid") < sequence.indexOf("bass-high")
        )
    }

    @Test
    fun `uses BPM only when both values are credible`() {
        val sequence = SmartDjSequencer.sequenceCandidates(
            listOf(
                track("electronic-seed", genre = "Electronic", bpm = 126),
                track("classical-close-bpm", genre = "Classical", bpm = 128),
                track("electronic-no-bpm", genre = "Electronic"),
                track("electronic-bad-bpm", genre = "House", bpm = 400)
            ),
            outputLimit = 4
        ).ids()

        val classicalIndex = sequence.indexOf("classical-close-bpm")
        assertTrue(classicalIndex == -1 || sequence.indexOf("electronic-no-bpm") < classicalIndex)
        assertTrue(classicalIndex == -1 || sequence.indexOf("electronic-bad-bpm") < classicalIndex)
    }

    @Test
    fun `artist spacing avoids immediate repeats when alternatives exist`() {
        val sequence = SmartDjSequencer.sequenceCandidates(
            listOf(
                track("a1", genre = "House", artist = "Artist A"),
                track("a2", genre = "House", artist = "Artist A"),
                track("b1", genre = "House", artist = "Artist B"),
                track("c1", genre = "House", artist = "Artist C"),
                track("a3", genre = "House", artist = "Artist A")
            ),
            outputLimit = 5
        )

        assertEquals(
            listOf("Artist A", "Artist B", "Artist C"),
            sequence.take(3).map { it.artistKey }
        )
    }

    @Test
    fun `album repeats are penalized but not impossible`() {
        val spaced = SmartDjSequencer.sequenceCandidates(
            listOf(
                track("same-1", genre = "House", album = "Same Album"),
                track("same-2", genre = "House", album = "Same Album"),
                track("other-1", genre = "House", album = "Other Album")
            ),
            outputLimit = 3
        )

        assertNotEquals(
            spaced[0].albumKey,
            spaced[1].albumKey
        )

        val singleAlbum = SmartDjSequencer.sequenceCandidates(
            listOf(
                track("album-1", genre = "House", album = "Only Album"),
                track("album-2", genre = "House", album = "Only Album")
            ),
            outputLimit = 2
        )

        assertEquals(listOf("album-1", "album-2"), singleAlbum.ids())
    }

    @Test
    fun `unknown metadata falls back to bounded distinct playlist`() {
        val sequence = SmartDjSequencer.sequenceCandidates(
            listOf(
                track("unknown-1", genre = null),
                track("unknown-2", genre = null),
                track("unknown-1", genre = null),
                track("unknown-3", genre = null)
            ),
            outputLimit = 3
        ).ids()

        assertEquals(listOf("unknown-1", "unknown-2", "unknown-3"), sequence)
    }

    @Test
    fun `audiobooks are excluded`() {
        val sequence = SmartDjSequencer.sequenceCandidates(
            listOf(
                track("music", genre = "House"),
                track("audiobook-category", genre = "House", isAudiobook = true),
                track("audiobook-type", genre = "House", isAudiobook = true)
            ),
            outputLimit = 3
        ).ids()

        assertEquals(listOf("music"), sequence)
    }

    @Test
    fun `output and candidate bounds are enforced`() {
        val candidates = (1..205).map { index ->
            track("song-$index", genre = "House")
        }

        val sequence = SmartDjSequencer.sequenceCandidates(
            candidates,
            outputLimit = 100,
            candidateLimit = 500
        ).ids()

        assertEquals(50, sequence.size)
        assertFalse(sequence.contains("song-201"))
        assertFalse(sequence.contains("song-205"))
    }

    @Test
    fun `energy lift starts low and finishes high`() {
        val sequence = SmartDjSequencer.sequenceCandidates(
            listOf(
                track("high", genre = "Electronic", energy = 0.92f),
                track("low", genre = "Electronic", energy = 0.12f),
                track("upper-mid", genre = "Electronic", energy = 0.70f),
                track("lower-mid", genre = "Electronic", energy = 0.38f)
            ),
            mode = DiscoveryMixMode.ENERGY_RISE,
            outputLimit = 4
        )

        assertEquals("low", sequence.first().key)
        assertEquals("high", sequence.last().key)
    }

    @Test
    fun `wind down starts moderate and finishes calm`() {
        val sequence = SmartDjSequencer.sequenceCandidates(
            listOf(
                track("calm", genre = "Downtempo", energy = 0.10f),
                track("moderate", genre = "Downtempo", energy = 0.55f),
                track("gentle", genre = "Downtempo", energy = 0.30f)
            ),
            mode = DiscoveryMixMode.COOLDOWN,
            outputLimit = 3
        )

        assertEquals("moderate", sequence.first().key)
        assertEquals("calm", sequence.last().key)
    }

    @Test
    fun `harmonic mode prefers compatible camelot neighbours`() {
        val sequence = SmartDjSequencer.sequenceCandidates(
            listOf(
                track("seed", genre = "House", camelot = "8A"),
                track("wrong", genre = "House", camelot = "2B"),
                track("same", genre = "House", camelot = "8A"),
                track("adjacent", genre = "House", camelot = "9A")
            ),
            mode = DiscoveryMixMode.HARMONIC,
            outputLimit = 4
        ).ids()

        assertTrue(sequence.indexOf("same") < sequence.indexOf("wrong"))
        assertTrue(sequence.indexOf("adjacent") < sequence.indexOf("wrong"))
    }

    private fun track(
        id: String,
        genre: String?,
        artist: String = "Artist $id",
        album: String = "Album $id",
        bpm: Int? = null,
        energy: Float? = null,
        camelot: String? = null,
        isAudiobook: Boolean = false
    ): SmartDjCandidate =
        SmartDjCandidate(
            key = id,
            genre = genre,
            artistKey = artist,
            albumKey = album,
            bpm = bpm,
            energy = energy,
            camelot = camelot,
            isAudiobook = isAudiobook
        )

    private fun List<SmartDjCandidate>.ids(): List<String> = map { it.key }
}
