package com.craftworks.music.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavidromeProviderUrlTest {

    @Test
    fun `connection URLs preserve primary order and remove invalid duplicates`() {
        val server = NavidromeProvider(
            id = "server",
            url = "musicbox.local:4533/",
            username = "user",
            password = "password",
            fallbackUrls = listOf(
                "http://musicbox.local:4533",
                "https://music.example.test/",
                "ftp://invalid.example.test"
            )
        )

        assertEquals(
            listOf("http://musicbox.local:4533", "https://music.example.test"),
            navidromeConnectionUrls(server)
        )
    }

    @Test
    fun `fallback URL input normalizes lines and omits the primary`() {
        assertEquals(
            listOf("https://backup.example.test", "http://192.168.1.10:4533"),
            normalizeNavidromeFallbackUrls(
                input = "https://backup.example.test/\n192.168.1.10:4533\nhttps://primary.example.test",
                primaryUrl = "https://primary.example.test/"
            )
        )
        assertNull(
            normalizeNavidromeFallbackUrls(
                input = "ftp://invalid.example.test",
                primaryUrl = "https://primary.example.test"
            )
        )
    }
    @Test
    fun `normalizes reachable Navidrome base URLs`() {
        assertEquals(
            "http://musicbox.zerotier:4533",
            normalizeNavidromeServerUrl("  musicbox.zerotier:4533/ ")
        )
        assertEquals(
            "https://music.example.test/navidrome",
            normalizeNavidromeServerUrl("https://music.example.test/navidrome/")
        )
    }

    @Test
    fun `rejects malformed or credential-bearing URLs`() {
        assertNull(normalizeNavidromeServerUrl("ftp://music.example.test"))
        assertNull(normalizeNavidromeServerUrl("http://bad host"))
        assertNull(normalizeNavidromeServerUrl("https://user@music.example.test"))
        assertEquals("Invalid URL", navidromeServerUrlConnectionProblem("ftp://music.example.test"))
    }

    @Test
    fun `classifies loopback URLs before opening a socket`() {
        assertEquals(
            "Loopback URL points to this device",
            navidromeServerUrlConnectionProblem("http://127.0.0.1:14533")
        )
        assertEquals(
            "Loopback URL points to this device",
            navidromeServerUrlConnectionProblem("http://localhost:14533")
        )
    }

    @Test
    fun `allows routable hosts for connection attempts`() {
        assertNull(navidromeServerUrlConnectionProblem("http://10.147.17.23:4533"))
        assertNull(navidromeServerUrlConnectionProblem("http://musicbox.zerotier:4533"))
        assertEquals("http://10.147.17.23:4533", requireUsableNavidromeServerUrl("10.147.17.23:4533/"))
    }
}
