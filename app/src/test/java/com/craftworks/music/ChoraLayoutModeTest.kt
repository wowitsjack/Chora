package com.craftworks.music

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChoraLayoutModeTest {
    @Test
    fun portraitPhoneUsesNowPlayingBottomSheet() {
        assertTrue(
            shouldUseNowPlayingBottomSheet(
                isTv = false,
                isTableTopMode = false,
                isLandscape = false,
                showOnboarding = false
            )
        )
    }

    @Test
    fun landscapePhoneUsesDedicatedContentLayout() {
        assertFalse(
            shouldUseNowPlayingBottomSheet(
                isTv = false,
                isTableTopMode = false,
                isLandscape = true,
                showOnboarding = false
            )
        )
    }

    @Test
    fun specialLayoutsAndOnboardingNeverUseThePlayerSheet() {
        assertFalse(
            shouldUseNowPlayingBottomSheet(
                isTv = true,
                isTableTopMode = false,
                isLandscape = false,
                showOnboarding = false
            )
        )
        assertFalse(
            shouldUseNowPlayingBottomSheet(
                isTv = false,
                isTableTopMode = true,
                isLandscape = false,
                showOnboarding = false
            )
        )
        assertFalse(
            shouldUseNowPlayingBottomSheet(
                isTv = false,
                isTableTopMode = false,
                isLandscape = false,
                showOnboarding = true
            )
        )
    }
}
