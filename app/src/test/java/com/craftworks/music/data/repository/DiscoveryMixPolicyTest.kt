package com.craftworks.music.data.repository

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.craftworks.music.data.model.DiscoveryMixMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryMixPolicyTest {
	@Test
	fun `free form intent is trimmed and strict`() {
		val request = discoveryRequestForMode(
			mode = DiscoveryMixMode.SMART,
			count = 50,
			excludeIds = emptyList(),
			randomSeed = "seed",
			requestedMood = null,
			intent = "  uk garage  "
		)

		assertEquals("uk garage", request.intent)
		assertTrue(request.strictIntent)
	}
    @Test
    fun `every discovery button sends a distinct musical policy`() {
        val requests = DiscoveryMixMode.entries.associateWith { mode ->
            discoveryRequestForMode(
                mode = mode,
                count = 25,
                excludeIds = listOf("old-song"),
                randomSeed = "seed",
                requestedMood = null
            )
        }

        assertEquals("arc", requests.getValue(DiscoveryMixMode.SMART).energyCurve)
        assertEquals("rise", requests.getValue(DiscoveryMixMode.ENERGY_RISE).energyCurve)
        assertEquals("fall", requests.getValue(DiscoveryMixMode.COOLDOWN).energyCurve)
        assertEquals("relaxed", requests.getValue(DiscoveryMixMode.COOLDOWN).mood)
        assertEquals(0.28f, requests.getValue(DiscoveryMixMode.COOLDOWN).energy)
        assertEquals("instrumental", requests.getValue(DiscoveryMixMode.INSTRUMENTAL).voice)
        assertTrue(
            requests.getValue(DiscoveryMixMode.HIDDEN_GEMS).discovery!! >
                requests.getValue(DiscoveryMixMode.REDISCOVER).discovery!!
        )
        assertTrue(
            requests.getValue(DiscoveryMixMode.HARMONIC).variety!! <
                requests.getValue(DiscoveryMixMode.SMART).variety!!
        )
        assertNull(requests.getValue(DiscoveryMixMode.HIDDEN_GEMS).energyCurve)
    }

    @Test
	fun `wind down uses analyzer drive instead of genre names`() {
		assertTrue(!isCredibleCooldownAnalysis(0.72f, 0.72f, 0.78f, 0.1f, 0.1f))
		assertTrue(isCredibleCooldownAnalysis(0.20f, 0.20f, 0.18f, 0.1f, 0.1f))
    }

    @Test
    fun `smart dj does not inherit wind down exclusions`() {
        val songs = listOf(song("dnb", "Drum and Bass"), song("ambient", "Ambient"))

        assertEquals(
            listOf("dnb", "ambient"),
            filterDiscoveryCandidatesForMode(songs, DiscoveryMixMode.SMART).map(MediaItem::mediaId)
        )
    }

    @Test
	fun `wind down fails small instead of padding with unknown or unsafe tracks`() {
		assertTrue(filterDiscoveryCandidatesForMode(listOf(song("unknown", null)), DiscoveryMixMode.COOLDOWN).isEmpty())
    }

    @Test
    fun `instrumental focus rejects analyzer voice label`() {
        assertTrue(!isCredibleInstrumentalClassification("voice", 0.91f, "Pop"))
        assertTrue(!isCredibleInstrumentalClassification("vocal", 0.91f, "Soul"))
        assertTrue(isCredibleInstrumentalClassification("instrumental", 0.91f, "Ambient"))
    }

    @Test
    fun `analyzer arousal uses the deployed one to nine scale`() {
        assertEquals(0f, 1f.normalizedAnalyzerValue(), 0.0001f)
        assertEquals(0.5f, 5f.normalizedAnalyzerValue(), 0.0001f)
        assertEquals(1f, 9f.normalizedAnalyzerValue(), 0.0001f)
    }

    private fun song(id: String, genre: String?): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(MediaMetadata.Builder().setGenre(genre).build())
        .build()

}
