package com.craftworks.music.ui.playing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StemMixerContractsTest {
    @Test
    fun `small playback skew does not cause seek churn`() {
        assertFalse(shouldCorrectStemDrift(10_000L, 9_881L))
        assertFalse(shouldCorrectStemDrift(10_000L, 10_120L))
    }

    @Test
    fun `large playback skew is corrected in either direction`() {
        assertTrue(shouldCorrectStemDrift(10_000L, 9_879L))
        assertTrue(shouldCorrectStemDrift(10_000L, 10_121L))
    }
}
