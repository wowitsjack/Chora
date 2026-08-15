package com.craftworks.music.ui.elements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MixBuild3DEffectTest {
    @Test
    fun `orbit joins cleanly at loop boundary`() {
        val start = mixBuildTransform(0f)
        val end = mixBuildTransform(1f)

        assertEquals(start.rotationX, end.rotationX, 0.001f)
        assertEquals(start.rotationZ, end.rotationZ, 0.001f)
        assertEquals(start.scale, end.scale, 0.001f)
        assertEquals(start.translationYDp, end.translationYDp, 0.001f)
        assertEquals(start.shadowElevationDp, end.shadowElevationDp, 0.001f)
    }

    @Test
    fun `motion creates depth without spinning the interface`() {
        val quarter = mixBuildTransform(0.25f)
        val halfway = mixBuildTransform(0.5f)

        assertTrue(quarter.rotationY in 6.9f..7.1f)
        assertTrue(halfway.rotationY in -0.001f..0.001f)
        assertEquals(0f, halfway.rotationZ, 0.001f)
        assertTrue(halfway.scale in 0.96f..0.97f)
        assertEquals(-7f, halfway.translationYDp, 0.001f)
        assertEquals(26f, halfway.shadowElevationDp, 0.001f)
    }
}
