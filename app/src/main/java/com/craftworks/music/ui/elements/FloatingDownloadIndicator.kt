package com.craftworks.music.ui.elements

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.craftworks.music.R
import com.craftworks.music.data.database.entity.DownloadEntity
import com.craftworks.music.data.database.entity.DownloadStatus
import kotlinx.coroutines.flow.Flow
import java.util.Locale

internal data class DownloadIndicatorPresentation(
    val statusText: String,
    val progress: Float?,
    val isIndeterminate: Boolean
)

internal fun activeDownloadSummary(downloads: List<DownloadEntity>): String {
    val downloading = downloads.count { it.status == DownloadStatus.DOWNLOADING }
    val queued = downloads.count { it.status == DownloadStatus.QUEUED }
    val paused = downloads.count { it.status == DownloadStatus.PAUSED }
    return when {
        downloading > 0 -> "Downloading $downloading of ${downloads.size}"
        queued > 0 -> if (queued == 1) "1 download waiting to start" else "$queued downloads waiting to start"
        paused > 0 -> if (paused == 1) "1 download paused" else "$paused downloads paused"
        else -> "No active downloads"
    }
}

internal fun isDownloadProgressDeterminate(download: DownloadEntity): Boolean =
    download.bytesDownloaded > 0L && download.totalBytes > 0L

internal fun downloadProgressText(download: DownloadEntity): String = when {
    download.bytesDownloaded <= 0L -> "Connecting to server"
    download.totalBytes > 0L -> {
        val percentage = (download.progress.coerceIn(0f, 1f) * 100).toInt().coerceAtLeast(1)
        "$percentage% downloaded"
    }
    else -> "${formatDownloadBytes(download.bytesDownloaded)} downloaded"
}

internal fun downloadIndicatorPresentation(
    download: DownloadEntity,
    activeCount: Int
): DownloadIndicatorPresentation = when (download.status) {
    DownloadStatus.QUEUED -> DownloadIndicatorPresentation(
        statusText = if (activeCount == 1) "Waiting to start" else "$activeCount downloads queued",
        progress = null,
        isIndeterminate = false
    )

    DownloadStatus.DOWNLOADING -> when {
        isDownloadProgressDeterminate(download) -> DownloadIndicatorPresentation(
            statusText = downloadProgressText(download),
            progress = download.progress.coerceIn(0f, 1f),
            isIndeterminate = false
        )

        else -> DownloadIndicatorPresentation(
            statusText = downloadProgressText(download),
            progress = null,
            isIndeterminate = true
        )
    }

    DownloadStatus.PAUSED -> DownloadIndicatorPresentation(
        statusText = "Download paused",
        progress = download.progress.coerceIn(0f, 1f),
        isIndeterminate = false
    )

    DownloadStatus.COMPLETED -> DownloadIndicatorPresentation(
        statusText = "Download complete",
        progress = 1f,
        isIndeterminate = false
    )

    DownloadStatus.FAILED -> DownloadIndicatorPresentation(
        statusText = "Download failed",
        progress = null,
        isIndeterminate = false
    )
}

@Composable
fun FloatingDownloadIndicator(
    activeDownloads: Flow<List<DownloadEntity>>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val downloads by activeDownloads.collectAsStateWithLifecycle(initialValue = emptyList())
    val currentDownload = downloads.firstOrNull { it.status == DownloadStatus.DOWNLOADING }
        ?: downloads.firstOrNull { it.status == DownloadStatus.QUEUED }
        ?: downloads.firstOrNull()

    AnimatedVisibility(
        visible = currentDownload != null,
        enter = scaleIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + fadeIn(),
        exit = scaleOut(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + fadeOut(),
        modifier = modifier.zIndex(10f)
    ) {
        currentDownload?.let { download ->
            val presentation = downloadIndicatorPresentation(download, downloads.size)
            val title = if (downloads.size > 1) {
                "${download.title} + ${downloads.size - 1} more"
            } else {
                download.title
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(18.dp),
                tonalElevation = 4.dp,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .widthIn(max = 250.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onClick)
                    .semantics {
                        contentDescription =
                            "${presentation.statusText}. $title. Tap for download details."
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Box(
                        modifier = Modifier.size(34.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            presentation.isIndeterminate -> CircularProgressIndicator(
                                modifier = Modifier.size(34.dp),
                                strokeWidth = 3.dp,
                                strokeCap = StrokeCap.Round
                            )

                            presentation.progress != null -> CircularProgressIndicator(
                                progress = { presentation.progress },
                                modifier = Modifier.size(34.dp),
                                strokeWidth = 3.dp,
                                strokeCap = StrokeCap.Round,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }

                        Icon(
                            imageVector = androidx.compose.ui.graphics.vector.ImageVector.vectorResource(
                                R.drawable.rounded_download_24
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = presentation.statusText,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private fun formatDownloadBytes(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    bytes < 1024L * 1024L * 1024L -> String.format(
        Locale.getDefault(),
        "%.1f MB",
        bytes / (1024.0 * 1024.0)
    )
    else -> String.format(
        Locale.getDefault(),
        "%.1f GB",
        bytes / (1024.0 * 1024.0 * 1024.0)
    )
}
