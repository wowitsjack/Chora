package com.craftworks.music.managers

import com.craftworks.music.data.NavidromeLibrary
import org.junit.Assert.assertEquals
import org.junit.Test

class NavidromeLibraryReconciliationTest {
    @Test
    fun `new server libraries are enabled without changing saved choices`() {
        val saved = listOf(
            NavidromeLibrary(id = 1, name = "Old music name", kind = "music") to false
        )
        val fetched = listOf(
            NavidromeLibrary(id = 1, name = "Music Library", kind = "music"),
            NavidromeLibrary(id = 2, name = "Audiobooks", kind = "audiobook")
        )

        val reconciled = reconcileNavidromeLibraries(saved, fetched)

        assertEquals(
            listOf(
                NavidromeLibrary(id = 1, name = "Music Library", kind = "music") to false,
                NavidromeLibrary(id = 2, name = "Audiobooks", kind = "audiobook") to true
            ),
            reconciled
        )
    }

    @Test
    fun `libraries removed by server are removed from saved selection`() {
        val saved = listOf(
            NavidromeLibrary(id = 1, name = "Music Library", kind = "music") to true,
            NavidromeLibrary(id = 2, name = "Audiobooks", kind = "audiobook") to true
        )

        val reconciled = reconcileNavidromeLibraries(
            savedLibraries = saved,
            fetchedLibraries = listOf(
                NavidromeLibrary(id = 2, name = "Books", kind = "audiobook")
            )
        )

        assertEquals(
            listOf(NavidromeLibrary(id = 2, name = "Books", kind = "audiobook") to true),
            reconciled
        )
    }
}
