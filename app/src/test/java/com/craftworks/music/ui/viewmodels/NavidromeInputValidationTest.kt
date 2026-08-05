package com.craftworks.music.ui.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavidromeInputValidationTest {

    @Test
    fun `empty fields report the first required field`() {
        assertEquals("Enter a server URL", validateNavidromeInput("", "", ""))
        assertEquals(
            "Enter a username",
            validateNavidromeInput("http://music.local", "", "secret")
        )
        assertEquals(
            "Enter a password",
            validateNavidromeInput("http://music.local", "user", "")
        )
    }

    @Test
    fun `unsupported or malformed URLs are rejected`() {
        assertEquals(
            "Enter a valid HTTP or HTTPS URL",
            validateNavidromeInput("ftp://music.local", "user", "secret")
        )
        assertEquals(
            "Enter a valid HTTP or HTTPS URL",
            validateNavidromeInput("http://bad host", "user", "secret")
        )
    }

    @Test
    fun `host and port without scheme normalize to HTTP`() {
        assertEquals(
            "http://musicbox.zerotier:4533",
            normalizeNavidromeUrl("  musicbox.zerotier:4533/ ")
        )
    }

    @Test
    fun `HTTPS base paths are preserved without a trailing slash`() {
        assertEquals(
            "https://music.example.test/navidrome",
            normalizeNavidromeUrl("https://music.example.test/navidrome/")
        )
        assertNull(normalizeNavidromeUrl("https://user@music.example.test"))
    }

    @Test
    fun `complete credentials pass validation`() {
        assertNull(validateNavidromeInput("musicbox.zerotier:4533", "user", "secret"))
    }
}
