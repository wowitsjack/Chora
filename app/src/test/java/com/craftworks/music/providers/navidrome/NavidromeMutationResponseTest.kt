package com.craftworks.music.providers.navidrome

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavidromeMutationResponseTest {
    @Test
    fun `accepts a successful Subsonic mutation response`() {
        assertTrue(
            isSuccessfulSubsonicResponse(
                """{"subsonic-response":{"status":"ok","version":"1.16.1"}}"""
            )
        )
    }

    @Test
    fun `rejects an error Subsonic mutation response`() {
        assertFalse(
            isSuccessfulSubsonicResponse(
                """{"subsonic-response":{"status":"failed","error":{"code":"70","message":"Not found"}}}"""
            )
        )
    }

    @Test
    fun `rejects malformed or unrelated mutation responses`() {
        assertFalse(isSuccessfulSubsonicResponse("not-json"))
        assertFalse(isSuccessfulSubsonicResponse("""{"status":"ok"}"""))
    }
}
