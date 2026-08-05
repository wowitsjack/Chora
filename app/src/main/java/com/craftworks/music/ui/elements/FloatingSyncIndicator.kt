package com.craftworks.music.ui.elements

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.craftworks.music.data.repository.SyncPhase
import com.craftworks.music.data.repository.SyncState

@Composable
fun FloatingSyncIndicator(
    isVisible: Boolean,
    isPaused: Boolean,
    syncState: SyncState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
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
        val isError = syncState.phase == SyncPhase.ERROR
        val percent = syncState.percentage.toInt()
        val title = when {
            isError -> "Sync needs attention"
            isPaused -> "Sync paused"
            else -> syncState.shortTitle
        }
        val detail = when {
            isError -> when (syncState.failedPhase) {
                SyncPhase.SONGS -> "Song sync stopped • Tap for details"
                SyncPhase.ALBUMS -> "Album sync stopped • Tap for details"
                else -> "Couldn't reach the server • Tap for details"
            }
            syncState.phase == SyncPhase.COMPLETE -> "Library is ready"
            syncState.hasDeterminateProgress -> {
                val current = syncState.current.coerceIn(0, syncState.total)
                "$percent% • $current of ${syncState.total} ${syncState.progressUnit}"
            }
            syncState.total > 0 -> "Preparing ${syncState.total} ${syncState.progressUnit}"
            else -> "Tap for details"
        }
        val accessibilityLabel = if (isError) {
            "$title. ${syncState.displayText} Tap for sync details."
        } else {
            "$title. $detail. Tap for sync details."
        }

        Surface(
            onClick = onClick,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = accessibilityLabel
                },
            shape = RoundedCornerShape(18.dp),
            color = when {
                isError -> MaterialTheme.colorScheme.errorContainer
                isPaused -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            },
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(38.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isError) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    } else if (syncState.hasDeterminateProgress) {
                        CircularProgressIndicator(
                            progress = { syncState.percentage / 100f },
                            modifier = Modifier.size(38.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = "$percent",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(30.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isError) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isError) {
                            MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
