package com.craftworks.music.providers.navidrome

import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StemSplitTest {
    @Test
    fun `stem URL is authenticated and MP3 endpoint is stem-specific`() {
        val url = buildNavidromeStemStreamUrl(
            serverUrl = "https://music.example.test/",
            username = "listener name",
            password = "secret",
            songId = "song/id",
            stem = "vocals",
            salt = "fixedsalt"
        )

        assertTrue(url.startsWith("https://music.example.test/rest/streamStem.view?"))
        val decoded = URLDecoder.decode(url.substringAfter('?'), Charsets.UTF_8.name())
        assertTrue("id=song/id" in decoded)
        assertTrue("stem=vocals" in decoded)
        assertTrue("u=listener name" in decoded)
        assertTrue("s=fixedsalt" in decoded)
        assertTrue("t=${md5Hash("secretfixedsalt")}" in decoded)
    }

    @Test
    fun `unsupported stem cannot become a stream path`() {
        assertThrows(IllegalArgumentException::class.java) {
            buildNavidromeStemStreamUrl(
                serverUrl = "https://music.example.test",
                username = "user",
                password = "secret",
                songId = "song",
                stem = "../../source",
                salt = "salt"
            )
        }
    }

    @Test
    fun `stem response parser reads all mixer channels`() {
        val parsed = parseNavidromeStemSplitJSON(
            """{"subsonic-response":{"status":"ok","version":"1.16.1","type":"navidrome","serverVersion":"1","openSubsonic":true,"stemSplit":{"trackId":"song","status":"ready","progress":100,"stem":["vocals","drums","bass","other"]}}}"""
        )

        assertEquals("ready", parsed?.status)
        assertEquals(supportedStemNames, parsed?.stem)
    }
}
