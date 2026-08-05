package com.craftworks.music.data

import com.craftworks.music.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationItemsTest {
    @Test
    fun `audiobooks is a primary default destination`() {
        val defaults = defaultBottomNavItems()

        assertEquals("audiobooks_screen", defaults[1].screenRoute)
        assertEquals("Books", defaults[1].title)
        assertTrue(defaults[1].enabled)
    }

    @Test
    fun `legacy navigation preferences gain audiobooks and disable radio`() {
        val migrated = normalizeBottomNavItems(
            listOf(
                BottomNavItem("Home", R.drawable.rounded_home_24, "home_screen"),
                BottomNavItem("Radios", R.drawable.rounded_radio, "radio_screen")
            )
        )

        assertEquals("audiobooks_screen", migrated[1].screenRoute)
        assertEquals("Books", migrated[1].title)
        assertFalse(migrated.single { it.screenRoute == "radio_screen" }.enabled)
    }

    @Test
    fun `saved audiobook navigation labels migrate to one line books copy`() {
        val migrated = normalizeBottomNavItems(
            listOf(
                BottomNavItem("Home", R.drawable.rounded_home_24, "home_screen"),
                BottomNavItem(
                    "Audiobooks",
                    R.drawable.rounded_auto_stories_24,
                    "audiobooks_screen"
                )
            )
        )

        assertEquals("Books", migrated.single { it.screenRoute == "audiobooks_screen" }.title)
    }
}
