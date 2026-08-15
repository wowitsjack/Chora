package com.craftworks.music.managers.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchHistoryManagerTest {
    @Test
    fun `recent searches are normalized deduplicated and newest first`() {
        assertEquals(
            listOf("Massive Attack", "Portishead"),
            updatedRecentSearches(
                existing = listOf("Portishead", "massive attack"),
                query = "  Massive   Attack  "
            )
        )
    }

    @Test
    fun `recent searches retain only the configured limit`() {
        assertEquals(
            listOf("new", "one", "two"),
            updatedRecentSearches(listOf("one", "two", "three"), "new", limit = 3)
        )
    }
}
