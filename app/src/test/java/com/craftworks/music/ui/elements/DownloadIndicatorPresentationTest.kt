package com.craftworks.music.ui.elements

import androidx.work.NetworkType
import com.craftworks.music.data.database.entity.DownloadEntity
import com.craftworks.music.data.database.entity.DownloadStatus
import com.craftworks.music.data.database.entity.MediaType
import com.craftworks.music.data.repository.downloadConstraints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadIndicatorPresentationTest {

    @Test
    fun `downloads do not require Android validated internet`() {
        val constraints = downloadConstraints()

        assertEquals(NetworkType.NOT_REQUIRED, constraints.requiredNetworkType)
        assertTrue(constraints.requiresStorageNotLow())
    }

    @Test
    fun `queued download reports that it is waiting instead of zero percent`() {
        val download = download(status = DownloadStatus.QUEUED)

        val presentation = downloadIndicatorPresentation(download, activeCount = 1)

        assertEquals("Waiting to start", presentation.statusText)
        assertNull(presentation.progress)
        assertFalse(presentation.isIndeterminate)
        assertEquals("1 download waiting to start", activeDownloadSummary(listOf(download)))
    }

    @Test
    fun `download with no received bytes reports connection state`() {
        val presentation = downloadIndicatorPresentation(
            download(status = DownloadStatus.DOWNLOADING),
            activeCount = 1
        )

        assertEquals("Connecting to server", presentation.statusText)
        assertNull(presentation.progress)
        assertTrue(presentation.isIndeterminate)
    }

    @Test
    fun `unknown length download reports transferred bytes`() {
        val presentation = downloadIndicatorPresentation(
            download(
                status = DownloadStatus.DOWNLOADING,
                bytesDownloaded = 1024L * 1024L
            ),
            activeCount = 1
        )

        assertEquals("1.0 MB downloaded", presentation.statusText)
        assertTrue(presentation.isIndeterminate)
    }

    private fun download(
        status: DownloadStatus,
        bytesDownloaded: Long = 0L,
        totalBytes: Long = 0L,
        progress: Float = 0f
    ) = DownloadEntity(
        id = "download-id",
        mediaId = "media-id",
        mediaType = MediaType.SONG,
        title = "Test Song",
        artist = "Test Artist",
        albumTitle = null,
        imageUrl = null,
        status = status,
        bytesDownloaded = bytesDownloaded,
        totalBytes = totalBytes,
        progress = progress
    )
}
