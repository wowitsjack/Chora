package com.craftworks.music.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncBatchPolicyTest {
    @Test
    fun `song writes wait for a useful batch`() {
        assertFalse(shouldFlushSongSyncBatch(399))
        assertTrue(shouldFlushSongSyncBatch(400))
    }
}
