package com.example.ui.screens

import android.view.SurfaceView
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.MainActivity
import com.example.data.local.CustomPlaylistEntity
import com.example.data.model.AppLanguage
import com.example.data.model.Channel
import com.example.data.model.CommunityPlaylists
import com.example.data.repository.PlaylistSource
import com.example.data.repository.PresetPlaylists
import com.example.player.IptvPlayerManager
import com.example.player.IptvVideoPlayer
import com.example.player.PlayerState
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.i18n.stringsForLanguage
import com.example.ui.theme.LocalAppColors
import com.example.ui.theme.OttGold
import com.example.ui.viewmodel.IptvViewModel

enum class NavigationItem(val label: String, val icon: ImageVector, val tag: String) {
    HOME("Home", Icons.Default.Home, "nav_home"),
    CATEGORIES("Categories", Icons.Default.Category, "nav_categories"),
    FAVORITES("Favorites", Icons.Default.Favorite, "nav_favorites"),
    PLAYLISTS("Playlists", Icons.Default.PlaylistPlay, "nav_playlists"),
    SETTINGS("Settings", Icons.Default.Settings, "nav_settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    viewModel: IptvViewModel,
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    isInPictureInPictureMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val recents by viewModel.recents.collectAsState()
    val customPlaylists by viewModel.customPlaylists.collectAsState()

    val appColors = LocalAppColors.current
    val strings = stringsForLanguage(uiState.currentLanguage)

    var selectedNav by rememberSaveable { mutableStateOf(NavigationItem.HOME) }
    var showAddPlaylistDialog by rememberSaveable { mutableStateOf(false) }

    CompositionLocalProvider(LocalAppStrings provides strings) {
        // When in Picture-in-Picture mode and a channel is active, render only the video player
        if (isInPictureInPictureMode && uiState.activeChannel != null) {
            IptvVideoPlayer(
                channel = uiState.activeChannel!!,
                playerManager = viewModel.playerManager,
                isFullscreen = true,
                isPipMode = true,
                onToggleFullscreen = {},
                onEnterPip = {},
                onMinimizeToMiniPlayer = {},
                modifier = Modifier.fillMaxSize()
            )
            return@CompositionLocalProvider
        }

        // Split-screen / Full-player view when a channel is expanded
        if (uiState.isSplitViewMode && uiState.activeChannel != null) {
            BackHandler {
                if (uiState.isFullscreen) {
                    viewModel.setFullscreen(false)
                } else {
                    viewModel.minimizeToMiniPlayer()
                }
            }
            HypnotixSplitScreen(
                channel = uiState.activeChannel!!,
                channels = uiState.channels,
                categories = uiState.categories,
                selectedCategory = uiState.selectedCategory,
                playerManager = viewModel.playerManager,
                isFullscreen = uiState.isFullscreen,
                onSelectCategory = viewModel::selectCategory,
                onSelectChannel = { viewModel.playChannel(it, openSplitView = true) },
                onToggleFavorite = viewModel::toggleFavorite,
                onToggleFullscreen = { viewModel.setFullscreen(!uiState.isFullscreen) },
                onMinimizeToMiniPlayer = viewModel::minimizeToMiniPlayer,
                onEnterPip = {
                    (context as? MainActivity)?.enterPipMode()
                },
                modifier = modifier.fillMaxSize()
            )
            return@CompositionLocalProvider
        }

        if (selectedNav != NavigationItem.HOME) {
            BackHandler {
                selectedNav = NavigationItem.HOME
            }
        }

        Scaffold(
            containerColor = appColors.background,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LiveTv,
                                contentDescription = null,
                                tint = OttGold,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = strings.appName,
                                    color = appColors.textPrimary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = uiState.activePlaylistSource.title,
                                    color = OttGold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showAddPlaylistDialog = true },
                            modifier = Modifier.testTag("top_add_playlist_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = strings.addPlaylist,
                                tint = OttGold
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = appColors.surface
                    )
                )
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(appColors.surface)
                ) {
                    // YouTube-style Bottom Mini-Player (when channel is playing & minimized)
                    if (uiState.activeChannel != null && !uiState.isSplitViewMode) {
                        DockedMiniPlayer(
                            channel = uiState.activeChannel!!,
                            playerManager = viewModel.playerManager,
                            onExpandFullscreen = { viewModel.restoreFromMiniPlayer() },
                            onEnterPipMode = {
                                (context as? MainActivity)?.enterPipMode()
                            },
                            onClose = { viewModel.closePlayer() }
                        )
                    }

                    NavigationBar(
                        containerColor = appColors.surface,
                        tonalElevation = 8.dp
                    ) {
                        NavigationItem.entries.forEach { item ->
                            NavigationBarItem(
                                selected = selectedNav == item,
                                onClick = { selectedNav = item },
                                icon = {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = item.label,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.Black,
                                    selectedTextColor = OttGold,
                                    indicatorColor = OttGold,
                                    unselectedIconColor = appColors.textSecondary,
                                    unselectedTextColor = appColors.textSecondary
                                ),
                                modifier = Modifier.testTag(item.tag)
                            )
                        }
                    }
                }
            },
            modifier = modifier
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (selectedNav) {
                    NavigationItem.HOME -> HomeScreenContent(
                        channels = uiState.channels,
                        categories = uiState.categories,
                        selectedCategory = uiState.selectedCategory,
                        searchQuery = uiState.searchQuery,
                        recents = recents,
                        activeChannel = uiState.activeChannel,
                        isLoading = uiState.isLoading,
                        onSearchQueryChange = viewModel::updateSearchQuery,
                        onSelectCategory = viewModel::selectCategory,
                        onSelectChannel = { ch -> viewModel.playChannel(ch, openSplitView = true) },
                        onToggleFavorite = viewModel::toggleFavorite
                    )
                    NavigationItem.CATEGORIES -> CategoriesScreenContent(
                        categories = uiState.categories,
                        selectedCategory = uiState.selectedCategory,
                        channels = uiState.channels,
                        onSelectCategory = viewModel::selectCategory,
                        onSelectChannel = { ch -> viewModel.playChannel(ch, openSplitView = true) },
                        onToggleFavorite = viewModel::toggleFavorite
                    )
                    NavigationItem.FAVORITES -> FavoritesScreenContent(
                        favorites = favorites,
                        onSelectChannel = { ch -> viewModel.playChannel(ch, openSplitView = true) },
                        onToggleFavorite = viewModel::toggleFavorite
                    )
                    NavigationItem.PLAYLISTS -> PlaylistsScreenContent(
                        activeSource = uiState.activePlaylistSource,
                        customPlaylists = customPlaylists,
                        onSelectSource = viewModel::loadPlaylist,
                        onDeleteCustom = viewModel::deleteCustomPlaylist,
                        onOpenAddDialog = { showAddPlaylistDialog = true }
                    )
                    NavigationItem.SETTINGS -> SettingsScreenContent(
                        isDarkTheme = isDarkTheme,
                        currentLanguage = uiState.currentLanguage,
                        onToggleTheme = onToggleTheme,
                        onSelectLanguage = viewModel::setLanguage
                    )
                }
            }
        }

        if (showAddPlaylistDialog) {
            AddPlaylistDialog(
                onDismiss = { showAddPlaylistDialog = false },
                onAdd = { name, url ->
                    viewModel.addCustomPlaylist(name, url)
                    showAddPlaylistDialog = false
                }
            )
        }
    }
}

/**
 * YouTube-style Docked Bottom Mini-Player with:
 * - Live video preview surface on the left
 * - Channel title + LIVE / Playing status in the middle
 * - Picture-in-Picture (PiP) button on the left of Play/Pause (`mini_player_pip`)
 * - Play/Pause button (`mini_player_play_pause`)
 * - Close / Cut button (`mini_player_close`)
 */
@Composable
fun DockedMiniPlayer(
    channel: Channel,
    playerManager: IptvPlayerManager,
    onExpandFullscreen: () -> Unit,
    onEnterPipMode: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val isPlaying by playerManager.isPlaying.collectAsState()
    val playbackState by playerManager.playbackState.collectAsState()
    val currentPosition by playerManager.currentPosition.collectAsState()
    val duration by playerManager.duration.collectAsState()

    val isMovie = channel.isMovieOrVod() || duration > 0L
    val progressFraction = if (duration > 0L) {
        (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        1f
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (appColors.isDark) Color(0xFF141824) else Color(0xFF1E223B)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable { onExpandFullscreen() }
            .testTag("docked_mini_player")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                // Left: Mini video surface preview
                Box(
                    modifier = Modifier
                        .width(110.dp)
                        .height(62.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black)
                        .clickable { onExpandFullscreen() }
                ) {
                    AndroidView(
                        factory = { ctx ->
                            SurfaceView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                playerManager.attachSurfaceView(this)
                            }
                        },
                        update = { surfaceView ->
                            playerManager.attachSurfaceView(surfaceView)
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (playbackState == PlayerState.BUFFERING) {
                        CircularProgressIndicator(
                            color = OttGold,
                            strokeWidth = 2.dp,
                            modifier = Modifier
                                .size(20.dp)
                                .align(Alignment.Center)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Middle: Channel title + LIVE / status badge
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onExpandFullscreen() }
                ) {
                    Text(
                        text = channel.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    if (isMovie) OttGold else Color(0xFFFF3B30),
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isMovie) "MOVIE" else "LIVE",
                            color = if (isMovie) OttGold else Color(0xFFFF3B30),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isPlaying) " • Playing" else " • Paused",
                            color = if (isPlaying) Color(0xFF4ADE80) else Color.White.copy(alpha = 0.6f),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }

                // Right controls: [PiP] [Play/Pause] [Close / Cut]
                IconButton(
                    onClick = onEnterPipMode,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("mini_player_pip")
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureInPictureAlt,
                        contentDescription = "Picture in Picture",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = { playerManager.togglePlayPause() },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("mini_player_play_pause")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("mini_player_close")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Player",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { progressFraction },
                color = if (isMovie) OttGold else Color(0xFFFF3B30),
                trackColor = Color.White.copy(alpha = 0.12f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
            )
        }
    }
}

@Composable
fun HypnotixSplitScreen(
    channel: Channel,
    channels: List<Channel>,
    categories: List<String>,
    selectedCategory: String,
    playerManager: IptvPlayerManager,
    isFullscreen: Boolean,
    onSelectCategory: (String) -> Unit,
    onSelectChannel: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onToggleFullscreen: () -> Unit,
    onMinimizeToMiniPlayer: () -> Unit,
    onEnterPip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current

    if (isFullscreen) {
        IptvVideoPlayer(
            channel = channel,
            playerManager = playerManager,
            isFullscreen = true,
            isPipMode = false,
            onToggleFullscreen = onToggleFullscreen,
            onEnterPip = onEnterPip,
            onMinimizeToMiniPlayer = onMinimizeToMiniPlayer,
            modifier = modifier.fillMaxSize()
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(appColors.background)
    ) {
        IptvVideoPlayer(
            channel = channel,
            playerManager = playerManager,
            isFullscreen = false,
            isPipMode = false,
            onToggleFullscreen = onToggleFullscreen,
            onEnterPip = onEnterPip,
            onMinimizeToMiniPlayer = onMinimizeToMiniPlayer,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        )

        // Active channel details strip with Minimize & PiP shortcuts
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(appColors.surface)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    color = appColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${channel.category} • ${channel.country}",
                    color = appColors.textSecondary,
                    fontSize = 12.sp
                )
            }
            IconButton(
                onClick = { onToggleFavorite(channel) },
                modifier = Modifier.testTag("split_favorite_button")
            ) {
                Icon(
                    imageVector = if (channel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (channel.isFavorite) Color(0xFFFF3B30) else appColors.textSecondary
                )
            }
            IconButton(
                onClick = onEnterPip,
                modifier = Modifier.testTag("split_pip_button")
            ) {
                Icon(
                    imageVector = Icons.Default.PictureInPictureAlt,
                    contentDescription = "Picture in Picture",
                    tint = OttGold
                )
            }
        }

        // Category filter row
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            categories.forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { onSelectCategory(cat) },
                    label = { Text(cat, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = OttGold,
                        selectedLabelColor = Color.Black
                    )
                )
            }
        }

        // Channel list below the video player
        LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(channels, key = { it.id }) { item ->
                ChannelRowCard(
                    channel = item,
                    isActive = item.id == channel.id,
                    onClick = { onSelectChannel(item) },
                    onToggleFavorite = { onToggleFavorite(item) }
                )
            }
        }
    }
}

@Composable
private fun HomeScreenContent(
    channels: List<Channel>,
    categories: List<String>,
    selectedCategory: String,
    searchQuery: String,
    recents: List<Channel>,
    activeChannel: Channel?,
    isLoading: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSelectCategory: (String) -> Unit,
    onSelectChannel: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit
) {
    val appColors = LocalAppColors.current
    val strings = LocalAppStrings.current

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text(strings.searchPlaceholder, fontSize = 13.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = OttGold)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("channel_search_input")
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            categories.forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { onSelectCategory(cat) },
                    label = { Text(cat, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = OttGold,
                        selectedLabelColor = Color.Black
                    )
                )
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = OttGold)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (recents.isNotEmpty() && searchQuery.isBlank()) {
                    item {
                        Text(
                            text = strings.continueWatching,
                            color = appColors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(recents, key = { "recent_${it.id}" }) { recent ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = appColors.cardBackground),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .width(160.dp)
                                        .clickable { onSelectChannel(recent) }
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = recent.name,
                                            color = appColors.textPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = recent.category,
                                            color = OttGold,
                                            fontSize = 11.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "${strings.featuredChannels} (${channels.size})",
                        color = appColors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                items(channels, key = { it.id }) { channel ->
                    ChannelRowCard(
                        channel = channel,
                        isActive = activeChannel?.id == channel.id,
                        onClick = { onSelectChannel(channel) },
                        onToggleFavorite = { onToggleFavorite(channel) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelRowCard(
    channel: Channel,
    isActive: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val appColors = LocalAppColors.current
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) OttGold.copy(alpha = 0.16f) else appColors.cardBackground
        ),
        border = if (isActive) BorderStroke(1.dp, OttGold) else BorderStroke(1.dp, appColors.border),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("channel_card_${channel.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(OttGold.copy(alpha = 0.2f))
            ) {
                Icon(
                    imageVector = Icons.Default.LiveTv,
                    contentDescription = null,
                    tint = OttGold,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    color = appColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${channel.category} • ${channel.country}",
                    color = appColors.textSecondary,
                    fontSize = 12.sp
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (channel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (channel.isFavorite) Color(0xFFFF3B30) else appColors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun CategoriesScreenContent(
    categories: List<String>,
    selectedCategory: String,
    channels: List<Channel>,
    onSelectCategory: (String) -> Unit,
    onSelectChannel: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit
) {
    HomeScreenContent(
        channels = channels,
        categories = categories,
        selectedCategory = selectedCategory,
        searchQuery = "",
        recents = emptyList(),
        activeChannel = null,
        isLoading = false,
        onSearchQueryChange = {},
        onSelectCategory = onSelectCategory,
        onSelectChannel = onSelectChannel,
        onToggleFavorite = onToggleFavorite
    )
}

@Composable
private fun FavoritesScreenContent(
    favorites: List<Channel>,
    onSelectChannel: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit
) {
    val appColors = LocalAppColors.current
    val strings = LocalAppStrings.current

    if (favorites.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = strings.noFavoritesYet,
                color = appColors.textSecondary,
                fontSize = 15.sp
            )
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(favorites, key = { it.id }) { channel ->
                ChannelRowCard(
                    channel = channel,
                    isActive = false,
                    onClick = { onSelectChannel(channel) },
                    onToggleFavorite = { onToggleFavorite(channel) }
                )
            }
        }
    }
}

@Composable
private fun PlaylistsScreenContent(
    activeSource: PlaylistSource,
    customPlaylists: List<CustomPlaylistEntity>,
    onSelectSource: (PlaylistSource) -> Unit,
    onDeleteCustom: (Long) -> Unit,
    onOpenAddDialog: () -> Unit
) {
    val appColors = LocalAppColors.current

    LazyColumn(
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Built-in & Community M3U Playlists",
                    color = appColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Button(
                    onClick = onOpenAddDialog,
                    colors = ButtonDefaults.buttonColors(containerColor = OttGold, contentColor = Color.Black),
                    modifier = Modifier.testTag("add_custom_playlist_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add M3U", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        items(PresetPlaylists.defaultPresets, key = { it.id }) { preset ->
            val isSelected = activeSource.id == preset.id
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) OttGold.copy(alpha = 0.18f) else appColors.cardBackground
                ),
                border = BorderStroke(1.dp, if (isSelected) OttGold else appColors.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectSource(preset) }
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(preset.title, color = appColors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(preset.description, color = appColors.textSecondary, fontSize = 12.sp)
                }
            }
        }

        items(customPlaylists, key = { "custom_${it.id}" }) { custom ->
            val source = PlaylistSource.Custom(custom.id, custom.name, custom.url)
            val isSelected = activeSource.id == source.id
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) OttGold.copy(alpha = 0.18f) else appColors.cardBackground
                ),
                border = BorderStroke(1.dp, if (isSelected) OttGold else appColors.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectSource(source) }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(14.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(custom.name, color = appColors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(custom.url, color = appColors.textSecondary, fontSize = 11.sp, maxLines = 1)
                    }
                    IconButton(onClick = { onDeleteCustom(custom.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF3B30))
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Community Curated Packs",
                color = appColors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        items(CommunityPlaylists.featured, key = { it.id }) { pack ->
            Card(
                colors = CardDefaults.cardColors(containerColor = appColors.cardBackground),
                border = BorderStroke(1.dp, appColors.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onSelectSource(PlaylistSource.Preset(pack.id, pack.name, pack.url, pack.description))
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(14.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(pack.name, color = appColors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(pack.description, color = appColors.textSecondary, fontSize = 12.sp)
                    }
                    Text(pack.channelCountHint, color = OttGold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SettingsScreenContent(
    isDarkTheme: Boolean,
    currentLanguage: AppLanguage,
    onToggleTheme: (Boolean) -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit
) {
    val appColors = LocalAppColors.current

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = appColors.cardBackground),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column {
                    Text("Dark Mode Theme", color = appColors.textPrimary, fontWeight = FontWeight.Bold)
                    Text("Cinema dark appearance for live TV", color = appColors.textSecondary, fontSize = 12.sp)
                }
                Switch(
                    checked = isDarkTheme,
                    onCheckedChange = onToggleTheme,
                    modifier = Modifier.testTag("dark_theme_switch")
                )
            }
        }

        Text(
            text = "App Language",
            color = appColors.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )

        AppLanguage.entries.forEach { lang ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (currentLanguage == lang) OttGold.copy(alpha = 0.2f) else appColors.cardBackground
                ),
                border = BorderStroke(1.dp, if (currentLanguage == lang) OttGold else appColors.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectLanguage(lang) }
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Text(lang.displayName, color = appColors.textPrimary, fontWeight = FontWeight.Medium)
                    Text(lang.nativeName, color = OttGold, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun AddPlaylistDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add M3U Playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    modifier = Modifier.testTag("playlist_name_input")
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("M3U / M3U8 URL") },
                    singleLine = true,
                    modifier = Modifier.testTag("playlist_url_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(name, url) },
                enabled = name.isNotBlank() && url.isNotBlank(),
                modifier = Modifier.testTag("confirm_add_playlist_button")
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
