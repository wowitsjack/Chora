package com.craftworks.music.ui.elements

import org.junit.Assert.assertEquals
import org.junit.Test

class AlphabetFastScrollerTest {
    @Test
    fun touchPositionIsClampedAcrossTheWholeStrip() {
        assertEquals(0, alphabetLetterIndex(-20f, 280, 28))
        assertEquals(14, alphabetLetterIndex(145f, 280, 28))
        assertEquals(27, alphabetLetterIndex(400f, 280, 28))
    }

    @Test
    fun missingLetterSelectsTheNextAvailableSection() {
        val items = listOf("Air", "Blossom", "Diplo", "Sweeney Todd")

        assertEquals(2, alphabetTargetIndex(items, 'C') { it.first() })
        assertEquals(3, alphabetTargetIndex(items, 'S') { it.first() })
    }

    @Test
    fun aLetterAfterTheLastSectionFallsBackToThePreviousSectionStart() {
        val items = listOf("Air", "Blossom", "Blues", "Diplo")

        assertEquals(3, alphabetTargetIndex(items, 'Z') { it.first() })
    }

    @Test
    fun specialBucketsAlwaysResolveInsideTheList() {
        val items = listOf("Air", "Blossom", "Unknown")

        assertEquals(0, alphabetTargetIndex(items, '#') { it.first() })
        assertEquals(2, alphabetTargetIndex(items, '?') { if (it == "Unknown") '?' else it.first() })
    }
}
