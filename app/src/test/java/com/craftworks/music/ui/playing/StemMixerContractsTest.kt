package com.craftworks.music.ui.playing

import com.craftworks.music.ui.viewmodels.normalizedBitrate
import com.craftworks.music.ui.viewmodels.normalizedProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class StemMixerContractsTest {
    @Test
    fun `small playback skew does not cause correction churn`() {
        assertEquals(StemSyncAction.NONE, stemSyncAction(10_000L, 9_980L, 1f))
        assertEquals(StemSyncAction.NONE, stemSyncAction(10_000L, 10_020L, 1f))
    }

    @Test
    fun `ordinary playback skew uses smooth speed correction`() {
        assertEquals(StemSyncAction.SPEED_UP, stemSyncAction(10_000L, 9_880L, 1f))
        assertEquals(StemSyncAction.SLOW_DOWN, stemSyncAction(10_000L, 10_120L, 1f))
    }

    @Test
    fun `settled stem returns to normal speed`() {
        assertEquals(StemSyncAction.RESET_SPEED, stemSyncAction(10_000L, 9_980L, 1.04f))
    }

    @Test
    fun `only severe drift causes a seek`() {
        assertEquals(StemSyncAction.SEEK, stemSyncAction(10_000L, 9_249L, 1f))
        assertEquals(StemSyncAction.SEEK, stemSyncAction(10_000L, 10_751L, 1f))
    }

    @Test
    fun `stem cache keys are stable without exposing source urls`() {
        val source = "http://server/rest/stem?id=song&token=secret"
        val key = stemCacheKey(source)

        assertEquals(key, stemCacheKey(source))
        assertEquals(20, key.length)
        assert(!key.contains("secret"))
    }

    @Test
    fun `restored stem selection keeps valid profile and bitrate`() {
        assertEquals("2stems", normalizedProfile("2stems"))
        assertEquals(192, normalizedBitrate(192))
        assertEquals("4stems", normalizedProfile("unknown"))
        assertEquals(256, normalizedBitrate(0))
    }
}
