package com.example.ui.screens

import android.app.Activity
import com.example.ad.AdManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import com.example.R
import com.example.data.local.CustomPlaylistEntity
import com.example.data.model.Channel
import com.example.ui.components.ExitConfirmationDialog
import com.example.ui.components.LanguageSelectionDialog
import com.example.ui.components.MediaNoticeLetterDialog
import com.example.ui.components.NavigationItem
import com.example.ui.components.ThemeSelectionDialog
import com.example.ui.theme.LocalAppColors
import com.example.ui.theme.OttGold
import com.example.ui.theme.OttLiveRed
import com.example.ui.theme.OttPrimary
import com.example.ui.theme.Slate900
import com.example.ui.viewmodel.IptvViewModel

@Composable
fun MainAppScreen(
    viewModel: IptvViewModel,
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val recents by viewModel.recents.collectAsState()
    val customPlaylists by viewModel.customPlaylists.collectAsState()
    val xtreamAccounts by viewModel.xtreamAccounts.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    var activeXtreamAccount by remember { mutableStateOf<com.example.data.local.XtreamAccountEntity?>(null) }
    var xtreamChannels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var isXtreamLoading by remember { mutableStateOf(false) }

    var selectedNav by remember { mutableStateOf(NavigationItem.HOME) }
    var showAddPlaylistScreen by remember { mutableStateOf(false) }

    // Dialog states
    var showExitDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showNoticeDialog by remember { mutableStateOf(false) }

    // Back button handling
    BackHandler {
        when {
            uiState.isFullscreen -> viewModel.setFullscreen(false)
            uiState.isSplitViewMode -> viewModel.closePlayer()
            showAddPlaylistScreen -> showAddPlaylistScreen = false
            selectedNav != NavigationItem.HOME -> selectedNav = NavigationItem.HOME
            else -> showExitDialog = true
        }
    }

    // Dialogs
    if (showExitDialog) {
        ExitConfirmationDialog(
            onConfirm = {
                showExitDialog = false
                viewModel.closePlayer()
                (context as? Activity)?.finish()
            },
            onDismiss = { showExitDialog = false }
        )
    }

    if (showThemeDialog) {
        ThemeSelectionDialog(
            isDarkTheme = isDarkTheme,
            onThemeSelected = {
                onToggleTheme(it)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showLanguageDialog) {
        LanguageSelectionDialog(
            currentLanguageCode = "en",
            onLanguageSelected = { showLanguageDialog = false },
            onDismiss = { showLanguageDialog = false }
        )
    }

    if (showNoticeDialog) {
        MediaNoticeLetterDialog(onAccept = { showNoticeDialog = false })
    }

    // If split-view mode is active (video playing), render HypnotixSplitScreen
    if (uiState.isSplitViewMode && uiState.activeChannel != null) {
        HypnotixSplitScreen(
            channels = uiState.filteredChannels,
            categories = uiState.categories,
            activeChannel = uiState.activeChannel,
            playerManager = viewModel.playerManager,
            isFullscreen = uiState.isFullscreen,
            onToggleFullscreen = { viewModel.toggleFullscreen() },
            onClosePlayer = { viewModel.closePlayer() },
            onChannelSelect = { viewModel.playChannel(it) },
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            modifier = modifier.fillMaxSize()
        )
        return
    }

    // If Add Playlist screen is active
    if (showAddPlaylistScreen) {
        AddPlaylistScreen(
            onAddPlaylist = { name, url ->
                val entity = CustomPlaylistEntity(
                    id = "custom_${System.currentTimeMillis()}",
                    name = name,
                    url = url
                )
                viewModel.addCustomPlaylist(entity)
                showAddPlaylistScreen = false
            },
            onBackClick = { showAddPlaylistScreen = false }
        )
        return
    }

    val appColors = LocalAppColors.current

    Scaffold(
        bottomBar = {
            Column {
                // Docked MiniPlayer if a channel is paused/active
                if (uiState.activeChannel != null) {
                    DockedMiniPlayer(
                        channel = uiState.activeChannel!!,
                        playerManager = viewModel.playerManager,
                        onExpandFullscreen = { viewModel.setFullscreen(true) },
                        onClose = { viewModel.closePlayer() }
                    )
                }

                // Bottom Navigation Bar
                NavigationBar(
                    containerColor = appColors.surface,
                    contentColor = appColors.textPrimary
                ) {
                    val navItems = listOf(
                        NavigationItem.HOME,
                        NavigationItem.CHANNELS,
                        NavigationItem.CATEGORIES,
                        NavigationItem.FAVORITES,
                        NavigationItem.PLAYLISTS,
                        NavigationItem.XTREAM,
                        NavigationItem.SETTINGS
                    )
                    navItems.forEach { item ->
                        val isSelected = item == selectedNav
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                selectedNav = item
                            },
                            icon = { Icon(item.icon, contentDescription = item.label, modifier = Modifier.size(20.dp)) },
                            label = { Text(item.label, fontSize = 9.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = OttPrimary,
                                selectedTextColor = OttPrimary,
                                unselectedIconColor = appColors.textSecondary,
                                unselectedTextColor = appColors.textSecondary,
                                indicatorColor = OttPrimary.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }
        },
        containerColor = appColors.background,
        modifier = modifier.fillMaxSize()
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (selectedNav) {
                NavigationItem.HOME -> HomeScreen(
                    channels = uiState.filteredChannels,
                    favorites = favorites,
                    recents = recents,
                    playingChannelUrl = uiState.activeChannel?.streamUrl,
                    isLoading = uiState.isLoading,
                    onPlayChannel = { viewModel.playChannel(it) },
                    onToggleFavorite = { viewModel.toggleFavorite(it) }
                )
                NavigationItem.CHANNELS -> ChannelListScreen(
                    title = "All Channels",
                    channels = uiState.filteredChannels,
                    allChannels = uiState.channels,
                    searchQuery = uiState.searchQuery,
                    selectedCategory = uiState.selectedCategory,
                    categories = uiState.categories,
                    currentPlayingChannel = uiState.activeChannel,
                    isLoading = uiState.isLoading,
                    onSearchQueryChange = { viewModel.onSearchQueryChanged(it) },
                    onCategorySelect = { viewModel.selectCategory(it ?: "All") },
                    onChannelClick = { viewModel.playChannel(it) },
                    onToggleFavorite = { viewModel.toggleFavorite(it) }
                )
                NavigationItem.CATEGORIES -> CategoriesScreen(
                    categories = uiState.categories,
                    channels = uiState.channels,
                    onSelectCategory = {
                        viewModel.selectCategory(it)
                        selectedNav = NavigationItem.CHANNELS
                    }
                )
                NavigationItem.FAVORITES -> FavoritesScreen(
                    favorites = favorites,
                    playingChannelUrl = uiState.activeChannel?.streamUrl,
                    onPlayChannel = { viewModel.playChannel(it) },
                    onToggleFavorite = { viewModel.toggleFavorite(it) }
                )
                NavigationItem.PLAYLISTS -> PlaylistsScreen(
                    currentSource = uiState.activePlaylistSource,
                    customPlaylists = customPlaylists,
                    onSelectSource = { viewModel.loadPlaylist(it) },
                    onNavigateToAddPlaylist = { showAddPlaylistScreen = true },
                    onDeleteCustomPlaylist = {
                        viewModel.deleteCustomPlaylist(it)
                    }
                )
                NavigationItem.XTREAM -> XtreamScreen(
                    accounts = xtreamAccounts,
                    activeAccount = activeXtreamAccount ?: xtreamAccounts.firstOrNull(),
                    xtreamChannels = xtreamChannels,
                    isLoading = isXtreamLoading,
                    currentPlayingChannel = uiState.activeChannel,
                    onAddAccount = { name, url, user, pass, onComplete ->
                        coroutineScope.launch {
                            isXtreamLoading = true
                            val authOk = viewModel.xtreamRepository.authenticate(url, user, pass)
                            if (authOk) {
                                val entity = com.example.data.local.XtreamAccountEntity(
                                    id = "xtream_${System.currentTimeMillis()}",
                                    name = name,
                                    serverUrl = url,
                                    username = user,
                                    password = pass
                                )
                                viewModel.xtreamRepository.saveAccount(entity)
                                activeXtreamAccount = entity
                                xtreamChannels = viewModel.xtreamRepository.loadXtreamChannels(entity)
                                onComplete(true, "Connected successfully")
                            } else {
                                onComplete(false, "Authentication failed. Check credentials/URL.")
                            }
                            isXtreamLoading = false
                        }
                    },
                    onSetActiveAccount = { accountId ->
                        val account = xtreamAccounts.firstOrNull { it.id == accountId }
                        activeXtreamAccount = account
                        if (account != null) {
                            coroutineScope.launch {
                                isXtreamLoading = true
                                xtreamChannels = viewModel.xtreamRepository.loadXtreamChannels(account)
                                isXtreamLoading = false
                            }
                        }
                    },
                    onDeleteAccount = { accountId ->
                        coroutineScope.launch {
                            viewModel.xtreamRepository.deleteAccount(accountId)
                            if (activeXtreamAccount?.id == accountId) {
                                activeXtreamAccount = null
                                xtreamChannels = emptyList()
                            }
                        }
                    },
                    onPlayChannel = { viewModel.playChannel(it) },
                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                    onBackClick = { selectedNav = NavigationItem.HOME }
                )
                NavigationItem.SETTINGS -> SettingsScreen(
                    playerManager = viewModel.playerManager,
                    isDarkTheme = isDarkTheme,
                    onOpenThemeDialog = { showThemeDialog = true },
                    onOpenLanguageDialog = { showLanguageDialog = true },
                    onOpenNoticeDialog = { showNoticeDialog = true },
                    onClearRecents = {
                        viewModel.clearRecents()
                    }
                )
            }
        }
    }
}

@Composable
fun DockedMiniPlayer(
    channel: Channel,
    playerManager: com.example.player.IptvPlayerManager,
    onExpandFullscreen: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPlaying by playerManager.isPlaying.collectAsState()
    val duration by playerManager.duration.collectAsState()
    val isMovie = channel.isMovieOrVod

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(onClick = onExpandFullscreen)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("docked_mini_player")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                if (!channel.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(4.dp)
                    )
                } else {
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = R.drawable.ic_iptv_logo),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(4.dp)
                    )
                }

                Surface(
                    color = if (isMovie) OttGold else OttLiveRed,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp)
                ) {
                    Text(
                        text = if (isMovie) "MOVIE" else "LIVE",
                        color = if (isMovie) Color.Black else Color.White,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f).clickable(onClick = onExpandFullscreen)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = channel.category, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                    if (isMovie && duration > 0) {
                        val totalMinutes = duration / (1000 * 60)
                        val hours = totalMinutes / 60
                        val minutes = totalMinutes % 60
                        val durStr = if (hours > 0) " • ${hours} hr ${minutes} min" else " • ${minutes} min"
                        Text(text = durStr, color = OttGold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Text(
                    text = channel.name,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = { playerManager.togglePlayPause() }) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White
                )
            }

            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}
