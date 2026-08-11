package com.craftworks.music.ui.screens

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.craftworks.music.R
import com.craftworks.music.data.GreetingMessages
import com.craftworks.music.data.model.Screen
import com.craftworks.music.data.model.DiscoveryMixMode
import com.craftworks.music.data.repository.AudiobookBook
import com.craftworks.music.managers.NavidromeManager
import com.craftworks.music.managers.settings.AppearanceSettingsManager
import com.craftworks.music.player.SongHelper
import java.util.Calendar
import com.craftworks.music.ui.elements.AlbumRow
import com.craftworks.music.ui.elements.RippleEffect
import com.craftworks.music.ui.playing.dpToPx
import com.craftworks.music.ui.util.LayoutMode
import com.craftworks.music.ui.util.rememberFoldableState
import com.craftworks.music.ui.util.responsiveAlbumCardWidth
import com.craftworks.music.ui.viewmodels.HomeScreenViewModel
import com.craftworks.music.ui.viewmodels.AudiobooksViewModel
import com.craftworks.music.ui.viewmodels.DiscoveryMixState
import com.craftworks.music.ui.viewmodels.SyncIndicatorViewModel
import com.craftworks.music.ui.viewmodels.SongActionsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.net.URLEncoder

@Stable
@Serializable
data class HomeItem(
    var key: String,
    var enabled: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    navHostController: NavHostController = rememberNavController(),
    mediaController: MediaController? = null,
    viewModel: HomeScreenViewModel = hiltViewModel(),
    syncViewModel: SyncIndicatorViewModel = hiltViewModel(),
    audiobooksViewModel: AudiobooksViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val foldableState = rememberFoldableState()

    val recentlyPlayedAlbums by viewModel.recentlyPlayedAlbums.collectAsStateWithLifecycle()
    val recentAlbums by viewModel.recentAlbums.collectAsStateWithLifecycle()
    val mostPlayedAlbums by viewModel.mostPlayedAlbums.collectAsStateWithLifecycle()
    val shuffledAlbums by viewModel.shuffledAlbums.collectAsStateWithLifecycle()
    val discoveryMixState by viewModel.discoveryMixState.collectAsStateWithLifecycle()
    val audiobooks by audiobooksViewModel.books.collectAsStateWithLifecycle()

    LaunchedEffect(discoveryMixState, mediaController) {
        val ready = discoveryMixState as? DiscoveryMixState.Ready
        if (ready != null && mediaController != null) {
            SongHelper.play(ready.songs, 0, mediaController)
            viewModel.consumeDiscoveryMix()
        }
    }

    val state = rememberPullToRefreshState()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    var showRipple by remember { mutableIntStateOf(0) }
    val rippleXOffset = LocalWindowInfo.current.containerSize.width / 2
    val rippleYOffset = dpToPx(12)
    val onRefresh: () -> Unit = {
        viewModel.loadHomeScreenData(forceRefresh = true)
        showRipple++
    }

    val libraries by NavidromeManager.libraries.collectAsStateWithLifecycle()

    // Move appearanceManager to outer scope so it can be reused throughout the screen
    val appearanceManager = remember { AppearanceSettingsManager(context.applicationContext) }

    PullToRefreshBox(
        modifier = Modifier,
        state = state,
        isRefreshing = isLoading,
        onRefresh = onRefresh,
        indicator = {} // Hidden - using FloatingSyncIndicator instead
    ) {
        // Use rememberSaveable to preserve scroll position across navigation
        val scrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                    )
            ) {
                Row (Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    val username by appearanceManager.usernameFlow.collectAsStateWithLifecycle(
                        AppearanceSettingsManager.DEFAULT_USERNAME
                    )
                    val showNavidromeLogo =
                        appearanceManager.showNavidromeLogoFlow.collectAsStateWithLifecycle(true).value && NavidromeManager.checkActiveServers()

                    // Dynamic greeting based on time of day
                    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
                    val availableIndices = remember(hour) { GreetingMessages.getGreetingIndicesForHour(hour) }

                    // Pick a new greeting from unused pool ONCE per app session
                    // rememberSaveable persists across config changes but resets on app restart
                    var sessionGreetingIndex by rememberSaveable { mutableStateOf(-1) }

                    // Pick greeting asynchronously to properly read from DataStore
                    LaunchedEffect(Unit) {
                        if (sessionGreetingIndex == -1) {
                            val usedSet = appearanceManager.usedGreetingsFlow.first()
                            val unusedIndices = availableIndices.filter { it !in usedSet }
                            sessionGreetingIndex = if (unusedIndices.isEmpty()) {
                                // All used, reset and pick fresh
                                appearanceManager.resetUsedGreetings()
                                availableIndices.randomOrNull() ?: 0
                            } else {
                                unusedIndices.random()
                            }
                            // Track this greeting as used
                            val newUsedSet = usedSet + sessionGreetingIndex
                            appearanceManager.setCurrentGreetingIndex(sessionGreetingIndex, newUsedSet)
                        }
                    }

                    val greeting = GreetingMessages.greetings.getOrNull(sessionGreetingIndex)
                        ?.template?.replace("{name}", username)
                        ?: "Welcome, $username!"

                    if (showNavidromeLogo) NavidromeLogo()

                    AutoShrinkText(
                        text = greeting,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        modifier = Modifier
                            .padding(start = 12.dp),
                    )
                }
                val isSyncing by syncViewModel.isSyncing.collectAsStateWithLifecycle()

                IconButton(
                    onClick = {
                        if (isSyncing) {
                            syncViewModel.cancelSync()
                        } else {
                            syncViewModel.startSync()
                        }
                    }, modifier = Modifier
                        .size(48.dp)
                ) {
                    Icon(
                        ImageVector.vectorResource(
                            if (isSyncing) R.drawable.rounded_cancel_24
                            else R.drawable.rounded_sync_24
                        ),
                        contentDescription = if (isSyncing) "Cancel Sync" else "Sync",
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(
                    onClick = {
                        navHostController.navigate(Screen.Setting.route) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }, modifier = Modifier
                        .padding(end = 12.dp)
                        .size(48.dp)
                ) {
                    Icon(
                        ImageVector.vectorResource(R.drawable.rounded_settings_24),
                        contentDescription = "Settings",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .horizontalScroll(rememberScrollState())
            ) {
                if (libraries.size > 1) {
                    libraries.forEach { (library, isSelected) ->
                        FilterChip(
                            onClick = {
                                NavidromeManager.currentServerId.value?.let { serverId ->
                                    NavidromeManager.toggleServerLibraryEnabled(
                                        serverId,
                                        library.id,
                                        !isSelected
                                    )
                                }
                            },
                            label = {
                                Text(library.name)
                            },
                            selected = isSelected,
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Filled.Done,
                                        contentDescription = "Done icon",
                                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                                    )
                                }
                            } else {
                                null
                            },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
            }

            DiscoveryMixCard(
                state = discoveryMixState,
                onBuildMix = viewModel::buildDiscoveryMix
            )

            FavoritesHomeCard(
                onClick = {
                    navHostController.navigate(Screen.Favorites.route) {
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )

            AudiobooksHomeCard(
                books = audiobooks,
                onClick = {
                    navHostController.navigate(Screen.Audiobooks.route) {
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )


            val orderedHomeItems = appearanceManager.homeItemsItemsFlow.collectAsStateWithLifecycle(
                initialValue = listOf(
                    HomeItem(
                        "recently_played",
                        true
                    ),
                    HomeItem(
                        "recently_added",
                        true
                    ),
                    HomeItem(
                        "most_played",
                        true
                    ),
                    HomeItem(
                        "random_songs",
                        true
                    )
                )
            ).value

            // Move titleMap outside the loop to avoid recreation
            val titleMap = remember {
                mapOf(
                    "recently_played" to R.string.recently_played,
                    "recently_added" to R.string.recently_added,
                    "most_played" to R.string.most_played,
                    "random_songs" to R.string.random_songs
                )
            }

            orderedHomeItems.forEach { item ->
                if (item.enabled) {
                    val albums = when (item.key) {
                        "recently_played" -> recentlyPlayedAlbums
                        "recently_added" -> recentAlbums
                        "most_played" -> mostPlayedAlbums
                        "random_songs" -> shuffledAlbums
                        else -> emptyList()
                    }

                    AlbumRow(
                        item.key,
                        titleMap[item.key],
                        albums,
                        mediaController,
                        navHostController,
                        viewModel
                    )
                }
            }
        }
    }

    RippleEffect(
        center = Offset(rippleXOffset.toFloat(), rippleYOffset.toFloat()),
        color = MaterialTheme.colorScheme.surfaceVariant,
        key = showRipple
    )
}

@Composable
private fun FavoritesHomeCard(onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.round_star_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(38.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Favorites",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Your favorite songs, ready to play",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AudiobooksHomeCard(
    books: List<AudiobookBook>,
    onClick: () -> Unit
) {
    val continueBook = remember(books) {
        books.filter { it.hasStarted && !it.isFinished }.maxByOrNull { it.updatedAt }
    }
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.rounded_auto_stories_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(38.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.Audiobooks),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = continueBook?.let { book ->
                        stringResource(
                            R.string.Home_Audiobooks_Continue,
                            book.album.mediaMetadata.title?.toString().orEmpty()
                        )
                    } ?: stringResource(R.string.Home_Audiobooks_Count, books.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (continueBook != null) {
                    LinearProgressIndicator(
                        progress = { continueBook.progressFraction },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DiscoveryMixCard(
    state: DiscoveryMixState,
	onBuildMix: (DiscoveryMixMode, String) -> Unit
) {
    val modes = remember {
        listOf(
            DiscoveryMixMode.SMART,
            DiscoveryMixMode.HIDDEN_GEMS,
            DiscoveryMixMode.REDISCOVER,
            DiscoveryMixMode.ENERGY_RISE,
            DiscoveryMixMode.COOLDOWN,
            DiscoveryMixMode.INSTRUMENTAL,
            DiscoveryMixMode.HARMONIC
        )
    }
    var selectedModeName by rememberSaveable { mutableStateOf(DiscoveryMixMode.SMART.name) }
	var intent by rememberSaveable { mutableStateOf("") }
    val selectedMode = modes.firstOrNull { it.name == selectedModeName } ?: DiscoveryMixMode.SMART
    val isLoading = state is DiscoveryMixState.Loading
    val errorText = when (state) {
        DiscoveryMixState.Empty -> stringResource(R.string.Home_Discovery_Empty)
        DiscoveryMixState.Error -> stringResource(R.string.Home_Discovery_Error)
        else -> null
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.Home_Discovery_Eyebrow),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.Home_Discovery_Title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.Home_Discovery_Description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                modes.forEach { mode ->
                    FilterChip(
                        selected = selectedMode == mode,
                        onClick = { selectedModeName = mode.name },
                        enabled = !isLoading,
                        label = { Text(mode.title) }
                    )
                }
            }
            Text(
                text = selectedMode.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
			OutlinedTextField(
				value = intent,
				onValueChange = { intent = it },
				enabled = !isLoading,
				singleLine = true,
				label = { Text("Describe the set") },
				placeholder = { Text("Country, UK garage, chill with no drums") },
				modifier = Modifier.fillMaxWidth()
			)
            Button(
				onClick = { onBuildMix(selectedMode, intent) },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.rounded_queue_music_24),
                        contentDescription = null
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isLoading) {
                        stringResource(R.string.Home_Discovery_Building)
                    } else {
                        "Build ${selectedMode.title}"
                    }
                )
            }
            if (errorText != null) {
                Text(
                    text = errorText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable fun NavidromeLogo(){
    var rotation by remember { mutableFloatStateOf(-10f) }
    val animatedRotation by animateFloatAsState(
        targetValue = rotation,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessVeryLow
        ),
        label = "Navidrome Logo Rotate"
    )
    val clickAction = rememberUpdatedState {
        rotation += 180f
    }

    val isClickable =
        if (LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK != Configuration.UI_MODE_TYPE_TELEVISION)
            Modifier.clickable { clickAction.value.invoke() }
        else
            Modifier

    Image(
        painter = painterResource(R.drawable.s_m_navidrome),
        contentDescription = "Navidrome Icon",
        modifier = Modifier
            .padding(start = 12.dp)
            .size(64.dp)
            .shadow(24.dp, CircleShape)
            .graphicsLayer {
                rotationZ = animatedRotation
            }
            .then(isClickable)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable fun AlbumRow(
    key: String,
    title: Int?,
    albums: List<MediaItem>,
    mediaController: MediaController?,
    navHostController: NavHostController,
    viewModel: HomeScreenViewModel
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val songActionsViewModel: SongActionsViewModel = hiltViewModel()
    val cardWidth = responsiveAlbumCardWidth()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    navHostController.navigate(Screen.HomeLists.route + "/$key") {
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Text(
                text = stringResource(title ?: androidx.media3.session.R.string.error_message_fallback),
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                fontSize = MaterialTheme.typography.headlineSmall.fontSize
            )

            Spacer(modifier = Modifier.weight(1f))

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .size(MaterialTheme.typography.headlineSmall.fontSize.value.dp * 1.2f)
            )
        }

        AlbumRow(
            albums,
            onAlbumSelected = { album ->
                val encodedImage = URLEncoder.encode(album.coverArt ?: "", "UTF-8")
                navHostController.navigate(Screen.AlbumDetails.route + "/${album.navidromeID}?image=$encodedImage") {
                    launchSingleTop = true
                }
            },
            onPlay = { album ->
                coroutineScope.launch {
                    try {
                        val mediaItems = viewModel.getAlbumSongs(album.mediaMetadata.extras?.getString("navidromeID") ?: "")
                        if (mediaItems.size > 1)
                            SongHelper.play(
                                mediaItems = mediaItems.subList(1, mediaItems.size),
                                index = 0,
                                mediaController = mediaController
                            )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            },
            onStartRadio = { album ->
                coroutineScope.launch {
                    Toast.makeText(context, "Starting album radio…", Toast.LENGTH_SHORT).show()
                    val albumId = album.mediaMetadata.extras?.getString("navidromeID") ?: album.mediaId
                    val seeds = viewModel.getAlbumSongs(albumId).drop(1)
                    val mix = runCatching {
                        songActionsViewModel.buildRadio(seeds)
                    }.getOrDefault(emptyList())
                    if (mix.isNotEmpty()) {
                        SongHelper.play(mix, 0, mediaController)
                    } else {
                        Toast.makeText(context, "Couldn’t start album radio", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            cardWidth = cardWidth
        )
    }
}

@Composable
fun AutoShrinkText(
    text: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onBackground,
    fontWeight: FontWeight = FontWeight.Normal,
    maxLines: Int = 1,
    minFontSize: androidx.compose.ui.unit.TextUnit = 12.sp
) {
    val initialFontSize = MaterialTheme.typography.headlineSmall.fontSize
    var fontSize by remember(text) { mutableStateOf(initialFontSize) }
    var readyToDraw by remember(text) { mutableStateOf(false) }

    Text(
        text = text,
        color = color,
        fontWeight = fontWeight,
        fontSize = fontSize,
        maxLines = maxLines,
        softWrap = true,
        overflow = TextOverflow.Clip,
        onTextLayout = { textLayoutResult ->
            if (textLayoutResult.hasVisualOverflow && fontSize > minFontSize) {
                fontSize = (fontSize.value * 0.9f).sp
            } else {
                readyToDraw = true
            }
        },
        modifier = modifier.graphicsLayer { alpha = if (readyToDraw) 1f else 0f }
    )
}
