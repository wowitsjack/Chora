package com.craftworks.music.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackUriResolverTest {
    @Test
    fun `stream url uses active server instead of cached media origin`() {
        val result = buildNavidromeStreamUrl(
            serverUrl = "http://192.0.2.10:4533/",
            username = "test user",
            password = "secret",
            songId = "song/id",
            bitrateOptions = "&maxBitRate=320&format=opus",
            salt = "fixedSalt"
        )

        assertTrue(result.startsWith("http://192.0.2.10:4533/rest/stream.view?"))
        assertTrue(result.contains("id=song%2Fid"))
        assertTrue(result.contains("u=test+user"))
        assertTrue(result.contains("s=fixedSalt"))
        assertTrue(result.endsWith("&maxBitRate=320&format=opus"))
        assertFalse(result.contains("secret"))
        assertFalse(result.contains("127.0.0.1"))
    }

    @Test
    fun `stream url normalizes active server before building playback uri`() {
        val result = buildNavidromeStreamUrl(
            serverUrl = "192.0.2.10:4533/",
            username = "user",
            password = "secret",
            songId = "song-id",
            bitrateOptions = "",
            salt = "fixedSalt"
        )

        assertTrue(result.startsWith("http://192.0.2.10:4533/rest/stream.view?"))
    }

    @Test
    fun `stream url rejects loopback active server`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            buildNavidromeStreamUrl(
                serverUrl = "http://127.0.0.1:14533",
                username = "user",
                password = "secret",
                songId = "song-id",
                bitrateOptions = "",
                salt = "fixedSalt"
            )
        }

        assertEquals("Loopback URL points to this device", error.message)
    }
}
