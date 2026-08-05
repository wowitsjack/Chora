package com.craftworks.music.ui.ipod

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftworks.music.R

internal object IpodColors {
    val Content = Color(0xFFFFFFFF)
    val Text = Color(0xFF111111)
    val SecondaryText = Color(0xFF666666)
    val Separator = Color(0xFFC7C7C7)
    val Blue = Color(0xFF2E72B8)
    val SelectedBlue = Color(0xFF4A9EE5)
    val NavTop = Color(0xFFB4BFCD)
    val NavBottom = Color(0xFF6E85A2)
    val NavBorder = Color(0xFF2D3033)
    val TabTop = Color(0xFF3B3B3B)
    val TabBottom = Color(0xFF070707)
    val TabIcon = Color(0xFFE3EAF2)
    val PlayerTop = Color(0xFF3A3A3A)
    val PlayerBottom = Color(0xFF101010)
    val PlayerText = Color(0xFFF4F4F4)
}

internal object IpodDimensions {
    val NavigationBarHeight = 44.dp
    val TabBarHeight = 49.dp
    val MiniPlayerHeight = 54.dp
    val RowHeight = 44.dp
    val SeparatorHeight = 1.dp
}

internal val IpodSteelBlueGlass = Brush.verticalGradient(
    0f to IpodColors.NavTop,
    0.49f to Color(0xFF889BB3),
    0.50f to Color(0xFF8095AF),
    1f to IpodColors.NavBottom
)

internal val IpodBlackGlass = Brush.verticalGradient(
    0f to Color.Black,
    0.04f to Color(0xFF545454),
    0.08f to Color(0xFF3B3B3B),
    0.50f to Color(0xFF1D1D1D),
    0.51f to Color(0xFF080808),
    1f to Color(0xFF080808)
)

internal val IpodGraphiteTabGlass = Brush.verticalGradient(
    0f to Color(0xFF5A5A5A),
    0.04f to Color(0xFF4A4A4A),
    0.50f to IpodColors.TabTop,
    0.51f to Color(0xFF2A2A2A),
    1f to IpodColors.TabBottom
)

internal val IpodFontFamily = FontFamily(
    Font(R.font.helvetica_neue_regular, FontWeight.Normal),
    Font(R.font.helvetica_neue_bold, FontWeight.Bold)
)

@Composable
internal fun IpodTypography(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalTextStyle provides LocalTextStyle.current.copy(
            fontFamily = IpodFontFamily,
            letterSpacing = 0.sp
        ),
        content = content
    )
}

@Composable
internal fun IpodNavigationBar(
    title: String,
    backLabel: String? = null,
    onBack: (() -> Unit)? = null,
    showNowPlaying: Boolean = false,
    onNowPlaying: () -> Unit = {}
) {
    val sideSlotWidth = if (onBack != null || showNowPlaying) 100.dp else 6.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(IpodDimensions.NavigationBarHeight)
            .background(IpodSteelBlueGlass)
            .border(1.dp, IpodColors.NavBorder, RectangleShape)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .width(sideSlotWidth)
                    .fillMaxHeight()
            ) {
                if (onBack != null) {
                    IpodBarButton(
                        label = "‹ ${backLabel ?: "Back"}",
                        onClick = onBack,
                        modifier = Modifier.padding(start = 5.dp)
                    )
                }
            }

            Text(
                text = title,
                color = Color.White,
                fontSize = 22.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier
                    .width(sideSlotWidth)
                    .fillMaxHeight()
            ) {
                if (showNowPlaying) {
                    IpodBarButton(
                        label = "Now Playing",
                        onClick = onNowPlaying,
                        modifier = Modifier.padding(end = 5.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun IpodBarButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(5.dp)
    val colors = if (pressed) {
        listOf(Color(0xFF3B70A4), Color(0xFF1B4E81))
    } else {
        listOf(Color(0xFF7BA5CD), Color(0xFF326B9F))
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(30.dp)
            .widthIn(min = 44.dp, max = 92.dp)
            .clip(shape)
            .background(Brush.verticalGradient(colors))
            .border(1.dp, Color(0xFF244B70), shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 8.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 13.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
internal fun IpodTabBar(
    selectedTab: IpodTab,
    onSelect: (IpodTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IpodDimensions.TabBarHeight)
            .background(IpodGraphiteTabGlass)
    ) {
        IpodTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            val interactionSource = remember(tab) { MutableInteractionSource() }
            val pressed by interactionSource.collectIsPressedAsState()
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        when {
                            pressed -> Color.Black.copy(alpha = 0.13f)
                            selected -> Color.Black.copy(alpha = 0.08f)
                            else -> Color.Transparent
                        }
                    )
                    .semantics { this.selected = selected }
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Tab,
                        onClick = { onSelect(tab) }
                    )
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(tab.iconResource()),
                    contentDescription = null,
                    tint = if (selected) Color.White else IpodColors.TabIcon.copy(alpha = 0.82f),
                    modifier = Modifier.size(26.dp)
                )
                Text(
                    text = tab.label,
                    color = if (selected) Color.White else IpodColors.TabIcon.copy(alpha = 0.82f),
                    fontSize = 11.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 1.dp)
                )
            }
        }
    }
}

@DrawableRes
private fun IpodTab.iconResource(): Int = when (this) {
    IpodTab.PLAYLISTS -> R.drawable.rounded_playlist_play_24
    IpodTab.ARTISTS -> R.drawable.rounded_artist_24
    IpodTab.SONGS -> R.drawable.round_music_note_24
    IpodTab.ALBUMS -> R.drawable.rounded_library_music_24
    IpodTab.AUDIOBOOKS -> R.drawable.rounded_auto_stories_24
    IpodTab.MORE -> R.drawable.placeholder
}

@Composable
internal fun IpodMessage(
    title: String,
    detail: String,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .background(IpodColors.Content)
            .padding(28.dp)
    ) {
        Text(
            text = title,
            color = IpodColors.Text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = detail,
            color = IpodColors.SecondaryText,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 5.dp)
        )
    }
}
