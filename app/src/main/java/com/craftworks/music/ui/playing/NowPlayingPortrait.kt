@file:OptIn(UnstableApi::class)

package com.craftworks.music.ui.playing

import android.content.res.Configuration
import android.os.Build
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Wallpapers
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.craftworks.music.R
import com.craftworks.music.data.model.MediaCategory
import com.craftworks.music.managers.settings.AppearanceSettingsManager
import com.craftworks.music.ui.util.LayoutMode
import com.craftworks.music.ui.util.TextDisplayUtils
import com.craftworks.music.ui.util.rememberFoldableState
import com.gigamole.composefadingedges.marqueeHorizontalFadingEdges

@kotlin.OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@RequiresApi(Build.VERSION_CODES.O)
@Preview(
    showSystemUi = false, device = "id:pixel",
    wallpaper = Wallpapers.BLUE_DOMINATED_EXAMPLE,
    uiMode = Configuration.UI_MODE_TYPE_VR_HEADSET, showBackground = true
)
@Composable
fun NowPlayingPortrait(
    mediaController: MediaController? = null,
    iconColor: Color = Color.Black,
    metadata: MediaMetadata? = null,
    onOpenStemMixer: () -> Unit = {},
    isFavorite: Boolean = false,
    downloadQueued: Boolean = false,
    actionsEnabled: Boolean = false,
    downloadEnabled: Boolean = false,
    onDownload: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onHide: () -> Unit = {},
    overflowMenu: @Composable (Color, androidx.compose.ui.unit.Dp) -> Unit = { _, _ -> }
) {
    val iconTextColor by animateColorAsState(
        targetValue = iconColor,
        animationSpec = tween(1500, 0, FastOutSlowInEasing),
        label = "Animated text color"
    )

    val context = LocalContext.current
    val settingsManager = remember { AppearanceSettingsManager(context) }
    val showMoreInfo by settingsManager.showMoreInfoFlow.collectAsStateWithLifecycle(true)
    val titleAlignment by settingsManager.nowPlayingTitleAlignment.collectAsStateWithLifecycle(NowPlayingTitleAlignment.LEFT)
    val stripTrackNumbers by settingsManager.stripTrackNumbersFromTitlesFlow.collectAsStateWithLifecycle(false)
    val isAudiobook = metadata?.extras?.getString("mediaCategory") == MediaCategory.AUDIOBOOK

    // Responsive sizing based on screen size
    // On unfolded/large screens, keep elements constrained to fit the card
    val foldableState = rememberFoldableState()
    val compactControls = foldableState.layoutMode == LayoutMode.COMPACT
    val maxArtHeight = when (foldableState.layoutMode) {
        LayoutMode.EXPANDED, LayoutMode.BOOK_MODE -> 360.dp  // Smaller on unfolded
        LayoutMode.MEDIUM -> 400.dp
        else -> 420.dp
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(vertical = 8.dp)
    ) {
        val compactHeight = maxHeight < 760.dp
        val denseControls = compactControls || compactHeight
        val smallButtonSize = if (denseControls) 28.dp else 32.dp
        val transportButtonSize = if (denseControls) 40.dp else 48.dp
        val playButtonSize = if (denseControls) 68.dp else 84.dp
        val secondaryButtonSize = 48.dp

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compactHeight) 6.dp else 10.dp)
        ) {

        /* Album Cover + Lyrics */
        AnimatedContent(
            lyricsOpen,
            label = "Crossfade between lyrics",
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { it ->
            if (it) {
                Box(Modifier.fillMaxSize()) {
                    LyricsView(
                        iconTextColor,
                        false,
                        mediaController,
                        PaddingValues(horizontal = 32.dp)
                    )
                    LyricsCloseButton(
                        color = iconTextColor,
                        onClick = { lyricsOpen = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 24.dp)
                    )
                }
            } else {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val artworkSize = minOf(maxWidth, maxHeight, maxArtHeight)
                    NowPlayingArtwork(
                        metadata = metadata,
                        targetSize = 1024,
                        crossfade = false,
                        modifier = Modifier
                            .size(artworkSize)
                            .shadow(4.dp, RoundedCornerShape(24.dp))
                            .clip(RoundedCornerShape(24.dp))
                    )
                }
            }
        }

        Row(
            Modifier
                .padding(horizontal = 32.dp)
        ) {
            /* Song Title + Artist */
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Crossfade(
                    targetState = TextDisplayUtils.formatSongTitle(metadata?.title.toString(), stripTrackNumbers),
                    animationSpec = tween(durationMillis = 500),
                    label = "Animated Song Title"
                ) { title ->
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMediumEmphasized,
                        color = iconTextColor,
                        maxLines = 1, overflow = TextOverflow.Visible,
                        softWrap = false,
                        textAlign = when (titleAlignment) {
                            NowPlayingTitleAlignment.LEFT -> TextAlign.Start
                            NowPlayingTitleAlignment.CENTER -> TextAlign.Center
                            NowPlayingTitleAlignment.RIGHT -> TextAlign.End
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .marqueeHorizontalFadingEdges(marqueeProvider = { Modifier.basicMarquee() })
                    )
                }

                Crossfade(
                    targetState = TextDisplayUtils.formatArtistName(metadata?.artist?.toString()) + if (metadata?.recordingYear != null && metadata?.recordingYear != 0 && metadata?.mediaType != MediaMetadata.MEDIA_TYPE_RADIO_STATION) " • " + metadata?.recordingYear else "",
                    animationSpec = tween(durationMillis = 500),
                    label = "Animated Artist"
                ) { artistInfo ->
                    Text(
                        text = artistInfo,
                        style = MaterialTheme.typography.bodyLarge,
                        color = iconTextColor,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = when (titleAlignment) {
                            NowPlayingTitleAlignment.LEFT -> TextAlign.Start
                            NowPlayingTitleAlignment.CENTER -> TextAlign.Center
                            NowPlayingTitleAlignment.RIGHT -> TextAlign.End
                        },
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .marqueeHorizontalFadingEdges(marqueeProvider = { Modifier.basicMarquee() })
                    )
                }

                if (showMoreInfo && metadata?.mediaType != MediaMetadata.MEDIA_TYPE_RADIO_STATION) {
                    Crossfade(
                        targetState = "${
                            metadata?.extras?.getString("format")?.uppercase()
                        } • ${metadata?.extras?.getLong("bitrate")} • ${
                            if (metadata?.extras?.getString("navidromeID")
                                    ?.startsWith("Local_") == true
                            )
                                stringResource(R.string.Source_Local)
                            else
                                stringResource(R.string.Source_Navidrome)
                        } ",
                    ) { moreInfo ->
                        Text(
                            text = moreInfo,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Light,
                            color = iconTextColor.copy(alpha = 0.5f),
                            maxLines = 1,
                            textAlign = when (titleAlignment) {
                                NowPlayingTitleAlignment.LEFT -> TextAlign.Start
                                NowPlayingTitleAlignment.CENTER -> TextAlign.Center
                                NowPlayingTitleAlignment.RIGHT -> TextAlign.End
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        if (metadata?.mediaType != MediaMetadata.MEDIA_TYPE_RADIO_STATION)
            PlaybackProgressSlider(iconTextColor, mediaController, metadata)

        VolumeSlider(iconTextColor, mediaController)

        //region Buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(playButtonSize)
                    .padding(horizontal = if (denseControls) 8.dp else 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                mediaController?.let {
                    if (isAudiobook) {
                        AudiobookTransportControls(
                            controller = it,
                            color = iconTextColor,
                            playButtonSize = playButtonSize,
                            chapterButtonSize = transportButtonSize,
                            seekButtonSize = smallButtonSize
                        )
                    } else {
                        ShuffleButton(
                            it,
                            iconTextColor,
                            Modifier.size(smallButtonSize)
                        )

                        PreviousSongButton(
                            it,
                            iconTextColor,
                            Modifier.size(transportButtonSize)
                        )

                        PlayPauseButton(
                            it,
                            iconTextColor,
                            Modifier.size(playButtonSize)
                        )

                        NextSongButton(
                            it,
                            iconTextColor,
                            Modifier.size(transportButtonSize)
                        )

                        RepeatButton(
                            it,
                            iconTextColor,
                            Modifier.size(smallButtonSize)
                        )
                    }
                }
            }

            if (!isAudiobook) {
                NowPlayingLibraryActions(
                    color = iconTextColor,
                    isFavorite = isFavorite,
                    downloadQueued = downloadQueued,
                    actionsEnabled = actionsEnabled,
                    downloadEnabled = downloadEnabled,
                    onDownload = onDownload,
                    onToggleFavorite = onToggleFavorite,
                    onHide = onHide
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(secondaryButtonSize + 8.dp)
                    .padding(horizontal = if (denseControls) 4.dp else 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (isAudiobook && mediaController != null) {
                    AudiobookSecondaryControls(
                        controller = mediaController,
                        color = iconTextColor,
                        metadata = metadata,
                        buttonSize = secondaryButtonSize
                    )
                } else {
                    LyricsButton(iconTextColor, secondaryButtonSize)

                    StemMixerButton(iconTextColor, secondaryButtonSize, metadata, onOpenStemMixer)

                    PlayQueueButton(iconTextColor, secondaryButtonSize)

                    overflowMenu(iconTextColor, secondaryButtonSize)
                }
            }
        }
        //endregion
        }
    }
}
