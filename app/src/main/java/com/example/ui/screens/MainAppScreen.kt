package com.example.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import android.view.ViewGroup
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.ad.AdManager
import com.example.data.local.CustomPlaylistEntity
import com.example.data.model.AppThemeMode
import com.example.data.model.Channel
import com.example.player.PlayerState
import com.example.data.repository.PlaylistSource
import com.example.data.repository.PresetPlaylists
import com.example.ui.components.AddPlaylistBottomSheet
import com.example.ui.components.ExitConfirmationDialog
import com.example.ui.components.LanguageSelectionDialog
import com.example.ui.components.MediaNoticeLetterDialog
import com.example.ui.components.NavigationItem
import com.example.ui.components.NavigationSidebar
import com.example.ui.components.ThemeSelectionDialog
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.LocalAppColors
import com.example.ui.theme.OttGold
import com.example.ui.theme.OttLiveRed
import com.example.ui.theme.OttPrimary
import com.example.ui.theme.Slate900
import com.example.ui.viewmodel.IptvViewModel
import kotlinx.coroutines.launch

@Composable
fun MainAppScreen(
    viewModel: IptvViewModel,
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    isPipMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val uiState by viewModel.uiState.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val recents by viewModel.recents.collectAsState()
    val customPlaylists by viewModel.customPlaylists.collectAsState()
    val xtreamAccounts by viewModel.xtreamAccounts.collectAsState()
    val activeXtreamAccount by viewModel.activeXtreamAccount.collectAsState()
    val xtreamChannels by viewModel.xtreamChannels.collectAsState()
    val xtreamVodChannels by viewModel.xtreamVodChannels.collectAsState()
    val isXtreamLoading by viewModel.isXtreamLoading.collectAsState()
    val xtreamAuthError by viewModel.xtreamAuthError.collectAsState()
    val isXtreamPlaybackMode by viewModel.isXtreamPlaybackMode.collectAsState()
    val showMediaDisclaimerDialog by viewModel.showMediaDisclaimerDialog.collectAsState()

    var selectedNav by remember { mutableStateOf(NavigationItem.HOME) }
    var showAddPlaylistScreen by remember { mutableStateOf(false) }
    var addPlaylistInitialTab by remember { mutableStateOf(0) }
    var playlistSearchQuery by remember { mutableStateOf("") }
    var showAddPlaylistBottomSheet by remember { mutableStateOf(false) }

    // Dialog states
    var showExitDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showNoticeDialog by remember { mutableStateOf(false) }

    // Back button handling
    BackHandler {
        when {
            showAddPlaylistBottomSheet -> showAddPlaylistBottomSheet = false
            drawerState.isOpen -> coroutineScope.launch { drawerState.close() }
            uiState.isFullscreen -> {
                com.example.ad.InStreamAdManager.suppressAdsTemporarily(3000L)
                viewModel.setFullscreen(false)
                (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            }
            uiState.isSplitViewMode -> {
                com.example.ad.InStreamAdManager.suppressAdsTemporarily(3000L)
                viewModel.minimizeToMiniPlayer()
                (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            }
            showAddPlaylistScreen -> showAddPlaylistScreen = false
            selectedNav != NavigationItem.HOME -> selectedNav = NavigationItem.HOME
            else -> showExitDialog = true
        }
    }

    // Dialogs
    if (showExitDialog) {
        ExitConfirmationDialog(
            onConfirmExit = {
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
            currentLanguageCode = uiState.currentLanguage.code,
            onLanguageSelected = {
                viewModel.setLanguage(it)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    if (showNoticeDialog || showMediaDisclaimerDialog) {
        MediaNoticeLetterDialog(
            onAccept = {
                showNoticeDialog = false
                viewModel.acceptMediaDisclaimer()
            },
            onDismiss = {
                showNoticeDialog = false
                viewModel.dismissMediaDisclaimer()
            }
        )
    }

    if (showAddPlaylistBottomSheet) {
        AddPlaylistBottomSheet(
            onDismissRequest = { showAddPlaylistBottomSheet = false },
            onAddPlaylistUrlClick = {
                showAddPlaylistBottomSheet = false
                addPlaylistInitialTab = 0
                showAddPlaylistScreen = true
            },
            onUploadFileClick = {
                showAddPlaylistBottomSheet = false
                addPlaylistInitialTab = 1
                showAddPlaylistScreen = true
            }
        )
    }

    // If split-view mode is active (video playing), render HypnotixSplitScreen with strict mode separation
    if (uiState.isSplitViewMode && uiState.activeChannel != null) {
        val isXtreamPlayer = isXtreamPlaybackMode || uiState.activeChannel!!.isXtreamChannel
        val favUrls = remember(favorites) { favorites.map { it.streamUrl }.toSet() }
        val xtreamCombinedStreams = remember(xtreamVodChannels, xtreamChannels, favUrls) {
            (xtreamVodChannels + xtreamChannels).map { ch ->
                ch.copy(isFavorite = favUrls.contains(ch.streamUrl) || ch.isFavorite)
            }
        }
        val xtreamCategories = remember(xtreamCombinedStreams) {
            xtreamCombinedStreams.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
        }
        val effectiveXtreamAccount = activeXtreamAccount ?: xtreamAccounts.firstOrNull()

        HypnotixSplitScreen(
            channels = if (isXtreamPlayer) xtreamCombinedStreams else uiState.channels,
            categories = if (isXtreamPlayer) xtreamCategories else uiState.categories,
            activeChannel = uiState.activeChannel,
            playerManager = viewModel.playerManager,
            isFullscreen = uiState.isFullscreen,
            isPipMode = isPipMode,
            onToggleFullscreen = {
                com.example.ad.InStreamAdManager.suppressAdsTemporarily(3000L)
                val activity = context as? Activity
                val willBeFullscreen = !uiState.isFullscreen
                viewModel.setFullscreen(willBeFullscreen)
                if (willBeFullscreen) {
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                } else {
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            },
            onEnterPip = {
                com.example.ad.InStreamAdManager.suppressAdsTemporarily(3000L)
                (context as? com.example.MainActivity)?.enterPipMode()
            },
            onClosePlayer = {
                com.example.ad.InStreamAdManager.suppressAdsTemporarily(3000L)
                viewModel.minimizeToMiniPlayer()
                (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            },
            onChannelSelect = { selected ->
                if (isXtreamPlayer) {
                    viewModel.playXtreamChannel(selected)
                } else {
                    viewModel.playChannel(selected, forceXtreamMode = false)
                }
            },
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            currentPlaylist = uiState.activePlaylistSource,
            customPlaylists = customPlaylists,
            deletedPresetIds = uiState.deletedPresetIds,
            onSelectPlaylist = { viewModel.loadPlaylist(it) },
            isLoading = if (isXtreamPlayer) isXtreamLoading else uiState.isLoading,
            statusMessage = if (isXtreamPlayer) {
                if (isXtreamLoading) "Loading Xtream Content..." else null
            } else {
                uiState.statusMessage
            },
            onRefresh = {
                if (isXtreamPlayer) {
                    viewModel.refreshXtreamChannels()
                } else {
                    viewModel.refreshPlaylistChannels()
                }
            },
            isXtreamMode = isXtreamPlayer,
            xtreamAccountName = effectiveXtreamAccount?.name ?: "Xtream Account",
            xtreamAccounts = xtreamAccounts,
            activeXtreamAccount = effectiveXtreamAccount,
            onSelectXtreamAccount = { accountId ->
                viewModel.setActiveXtreamAccount(accountId)
            },
            modifier = modifier.fillMaxSize()
        )
        return
    }

    // If Add Playlist screen is active
    if (showAddPlaylistScreen) {
        AddPlaylistScreen(
            initialTab = addPlaylistInitialTab,
            onAddPlaylist = { name, url ->
                val entity = CustomPlaylistEntity(
                    title = name,
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
    val strings = LocalAppStrings.current

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = if (appColors.isDark) Color(0xFF0D0D0D) else Color.White,
                drawerContentColor = appColors.textPrimary,
                drawerTonalElevation = 0.dp,
                modifier = Modifier.width(280.dp)
            ) {
                NavigationSidebar(
                    selectedItem = selectedNav,
                    onItemSelected = { item ->
                        selectedNav = item
                        coroutineScope.launch { drawerState.close() }
                    },
                    onOpenDisclaimerDialog = {
                        showNoticeDialog = true
                        coroutineScope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                if (selectedNav != NavigationItem.XTREAM && selectedNav != NavigationItem.SETTINGS) {
                    // Top App Bar matching Screenshot
                    Surface(
                        color = appColors.surface,
                        shadowElevation = 0.5.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                    ) {
                        var showPlaylistMenu by remember { mutableStateOf(false) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Hamburger Menu button
                            IconButton(
                                onClick = { coroutineScope.launch { drawerState.open() } },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Open Drawer",
                                    tint = appColors.textPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // 2. App Logo: Red rounded square with white TV screen matching screenshot
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFE50914),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Tv,
                                        contentDescription = "Hypnotix Logo",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            // 3. Title & Subtitle matching user requirements
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = when (selectedNav) {
                                        NavigationItem.CHANNELS -> strings.channel
                                        NavigationItem.PLAYLISTS -> strings.playlists
                                        NavigationItem.XTREAM -> strings.xtream
                                        NavigationItem.SETTINGS -> strings.settings
                                        NavigationItem.FAVORITES -> strings.favorites
                                        NavigationItem.CATEGORIES -> strings.categories
                                        else -> "Hypnotix"
                                    },
                                    color = appColors.textPrimary,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = when (selectedNav) {
                                        NavigationItem.CHANNELS -> {
                                            if (!uiState.hasSelectedFirstPlaylist || uiState.channelSelectedPlaylist.id == "none") {
                                                "No Playlist"
                                            } else {
                                                val pl = when (uiState.channelSelectedPlaylist.id) {
                                                    PresetPlaylists.INDIA.id -> "India"
                                                    PresetPlaylists.GLOBAL.id -> "Global"
                                                    PresetPlaylists.ALL.id -> "All"
                                                    else -> uiState.channelSelectedPlaylist.title.replace(" Channels", "").ifBlank { "Global" }
                                                }
                                                "$pl ${strings.channel}"
                                            }
                                        }
                                        NavigationItem.PLAYLISTS -> {
                                            if (!uiState.hasSelectedFirstPlaylist || uiState.selectedPlaylist.id == "none") {
                                                "No Playlist"
                                            } else {
                                                strings.managePlaylists
                                            }
                                        }
                                        NavigationItem.XTREAM -> strings.iptvAccounts
                                        NavigationItem.SETTINGS -> strings.preferences
                                        NavigationItem.FAVORITES -> strings.savedChannels
                                        NavigationItem.CATEGORIES -> strings.browseCategories
                                        else -> if (!uiState.hasSelectedFirstPlaylist || uiState.selectedPlaylist.id == "none") "No Playlist" else "Global Channels"
                                    },
                                    color = appColors.textSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            }

                            // 4. Search icon button
                            IconButton(
                                onClick = {
                                    if (selectedNav != NavigationItem.PLAYLISTS && selectedNav != NavigationItem.CHANNELS) {
                                        selectedNav = NavigationItem.CHANNELS
                                    }
                                },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = appColors.textPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // 5. Playlist Dropdown Pill Button only on HOME tab
                            if (selectedNav == NavigationItem.HOME) {
                                Spacer(modifier = Modifier.width(6.dp))

                                Box {
                                    val isNone = !uiState.hasSelectedFirstPlaylist || uiState.selectedPlaylist.id == "none"
                                    val playlistLabel = if (isNone) {
                                        "Add Playlist"
                                    } else {
                                        when (uiState.selectedPlaylist.id) {
                                            PresetPlaylists.INDIA.id -> "India"
                                            PresetPlaylists.GLOBAL.id -> "Global"
                                            PresetPlaylists.ALL.id -> "All"
                                            else -> uiState.selectedPlaylist.title.replace(" Channels", "").ifBlank { "Global" }
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = if (appColors.isDark) Color(0xFF1E293B) else Color(0xFFE0F2FE),
                                        border = BorderStroke(1.dp, if (appColors.isDark) Color(0xFF334155) else Color(0xFFBAE6FD)),
                                        modifier = Modifier
                                            .height(34.dp)
                                            .clickable {
                                                if (isNone) {
                                                    selectedNav = NavigationItem.PLAYLISTS
                                                } else {
                                                    showPlaylistMenu = true
                                                }
                                            }
                                            .testTag("top_bar_playlist_pill")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (isNone) Icons.Default.Add else Icons.Default.Language,
                                                contentDescription = null,
                                                tint = if (isNone) OttPrimary else Color(0xFF0284C7),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = playlistLabel,
                                                color = appColors.textPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = null,
                                                tint = appColors.textPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = showPlaylistMenu,
                                        onDismissRequest = { showPlaylistMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Global Channels") },
                                            onClick = {
                                                viewModel.selectPlaylist(PresetPlaylists.GLOBAL)
                                                showPlaylistMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("India Channels") },
                                            onClick = {
                                                viewModel.selectPlaylist(PresetPlaylists.INDIA)
                                                showPlaylistMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("All Channels") },
                                            onClick = {
                                                viewModel.selectPlaylist(PresetPlaylists.ALL)
                                                showPlaylistMenu = false
                                            }
                                        )
                                        customPlaylists.forEach { cp ->
                                            DropdownMenuItem(
                                                text = { Text(cp.title) },
                                                onClick = {
                                                    viewModel.selectPlaylist(
                                                        PlaylistSource(
                                                            id = "custom_${cp.id}",
                                                            title = cp.title,
                                                            url = cp.url,
                                                            isPreset = false
                                                        )
                                                    )
                                                    showPlaylistMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            bottomBar = {
                // Window Insets calculation for navigation bar
                val navBarInsets = WindowInsets.navigationBars.asPaddingValues()
                val navBottomInset = navBarInsets.calculateBottomPadding()

                Column(
                    modifier = if (selectedNav == NavigationItem.SETTINGS) {
                        Modifier.padding(bottom = if (navBottomInset > 0.dp) navBottomInset else 4.dp)
                    } else {
                        Modifier
                    }
                ) {
                    // YouTube-style Docked MiniPlayer with live video playback when minimized
                    if (uiState.activeChannel != null && !uiState.isSplitViewMode) {
                        DockedMiniPlayer(
                            channel = uiState.activeChannel!!,
                            playerManager = viewModel.playerManager,
                            onExpandFullscreen = { viewModel.restoreFromMiniPlayer() },
                            onClose = { viewModel.closePlayer() }
                        )
                    }

                    // Completely hide 5 bottom buttons when inside Settings (matching screenshot)
                    if (selectedNav != NavigationItem.SETTINGS) {
                        // Bottom Navigation Bar with 3D popped-out center red (+) button
                        // Box container allows the center button to stick out of the top without being clipped
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            // 1. Bottom Surface: Starts 18.dp lower so center red button pops out cleanly above it
                            Surface(
                                color = appColors.surface,
                                shadowElevation = 8.dp,
                                border = BorderStroke(0.5.dp, appColors.border),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .padding(top = 18.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            start = 4.dp,
                                            end = 4.dp,
                                            top = 6.dp,
                                            bottom = if (navBottomInset > 24.dp) navBottomInset else 6.dp
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceAround
                                ) {
                                    // 1. Home
                                    BottomNavPillItem(
                                        icon = Icons.Default.Home,
                                        label = strings.home,
                                        isSelected = selectedNav == NavigationItem.HOME,
                                        onClick = {
                                            selectedNav = NavigationItem.HOME
                                            AdManager.onUserNavigated(context as? Activity)
                                        }
                                    )

                                    // 2. Channel
                                    BottomNavPillItem(
                                        icon = Icons.Default.Tv,
                                        label = strings.channel,
                                        isSelected = selectedNav == NavigationItem.CHANNELS,
                                        onClick = {
                                            selectedNav = NavigationItem.CHANNELS
                                            AdManager.onUserNavigated(context as? Activity)
                                        }
                                    )

                                    // Gap for center button
                                    Spacer(modifier = Modifier.width(58.dp))

                                    // 4. Xtream
                                    BottomNavPillItem(
                                        icon = Icons.Default.VideoLibrary,
                                        label = strings.xtream,
                                        isSelected = selectedNav == NavigationItem.XTREAM,
                                        onClick = {
                                            selectedNav = NavigationItem.XTREAM
                                            AdManager.onUserNavigated(context as? Activity)
                                        }
                                    )

                                    // 5. Playlists
                                    BottomNavPillItem(
                                        icon = Icons.Default.GridView,
                                        label = strings.playlists,
                                        isSelected = selectedNav == NavigationItem.PLAYLISTS,
                                        onClick = {
                                            selectedNav = NavigationItem.PLAYLISTS
                                            AdManager.onUserNavigated(context as? Activity)
                                        }
                                    )
                                }
                            }

                            // 2. Center 3D Popped-out Squircle Red Plus Button
                            // Sits at Alignment.TopCenter of the Box, fully visible with 0 top cutoff!
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(bottom = if (navBottomInset > 24.dp) (navBottomInset - 2.dp) else 4.dp)
                            ) {
                                Center3DNavFab(
                                    onClick = {
                                        showAddPlaylistBottomSheet = true
                                    }
                                )
                            }
                        }
                    }
                }
            },
            containerColor = appColors.background,
            modifier = modifier.fillMaxSize()
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (selectedNav) {
                    NavigationItem.HOME -> HomeScreen(
                        channels = uiState.channels,
                        allChannels = uiState.allChannels,
                        favorites = favorites,
                        recents = recents,
                        currentPlayingChannel = uiState.activeChannel,
                        currentPlaylist = uiState.selectedPlaylist,
                        currentLanguage = uiState.currentLanguage,
                        isLoading = uiState.isLoading,
                        statusMessage = uiState.statusMessage,
                        searchQuery = uiState.searchQuery,
                        isSearchActive = uiState.searchQuery.isNotEmpty(),
                        hasSelectedFirstPlaylist = uiState.hasSelectedFirstPlaylist,
                        onChannelClick = { viewModel.playChannel(it, forceXtreamMode = false) },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onCategoryClick = {
                            viewModel.selectCategory(it)
                            selectedNav = NavigationItem.CHANNELS
                        },
                        onAddPlaylistClick = {
                            showAddPlaylistBottomSheet = true
                        },
                        onViewAllPlaylistsClick = { selectedNav = NavigationItem.PLAYLISTS },
                        onXtreamClick = { selectedNav = NavigationItem.XTREAM },
                        onRefresh = { viewModel.refreshPlaylistChannels() }
                    )
                    NavigationItem.CHANNELS -> ChannelListScreen(
                        title = "Channels",
                        channels = uiState.channels,
                        allChannels = uiState.allChannels,
                        searchQuery = uiState.searchQuery,
                        selectedCategory = uiState.selectedCategory,
                        categories = uiState.categories,
                        currentPlayingChannel = uiState.activeChannel,
                        currentPlaylist = uiState.channelSelectedPlaylist,
                        isLoading = uiState.isLoading,
                        statusMessage = uiState.statusMessage,
                        hasSelectedFirstPlaylist = uiState.hasSelectedFirstPlaylist,
                        onSearchQueryChange = { viewModel.onSearchQueryChanged(it) },
                        onCategorySelect = { viewModel.selectCategory(it) },
                        onChannelClick = { viewModel.playChannel(it, forceXtreamMode = false) },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onRefresh = { viewModel.refreshPlaylistChannels() },
                        onNavigateToPlaylists = { selectedNav = NavigationItem.PLAYLISTS }
                    )
                    NavigationItem.CATEGORIES -> CategoriesScreen(
                        channels = uiState.channels,
                        searchQuery = uiState.searchQuery,
                        onSearchQueryChange = { viewModel.setSearchQuery(it) },
                        hasSelectedFirstPlaylist = uiState.hasSelectedFirstPlaylist,
                        onNavigateToPlaylists = { selectedNav = NavigationItem.PLAYLISTS },
                        onSelectCategory = {
                            viewModel.selectCategory(it)
                            selectedNav = NavigationItem.CHANNELS
                        }
                    )
                    NavigationItem.FAVORITES -> FavoritesScreen(
                        favorites = favorites,
                        currentPlayingChannel = uiState.activeChannel,
                        searchQuery = uiState.searchQuery,
                        onSearchQueryChange = { viewModel.setSearchQuery(it) },
                        onChannelClick = { viewModel.playChannel(it) },
                        onToggleFavorite = { viewModel.toggleFavorite(it) }
                    )
                    NavigationItem.PLAYLISTS -> PlaylistsScreen(
                        currentPlaylist = uiState.selectedPlaylist,
                        customPlaylists = customPlaylists,
                        searchQuery = playlistSearchQuery,
                        onSearchQueryChange = { playlistSearchQuery = it },
                        deletedPresetIds = uiState.deletedPresetIds,
                        onDeletePresetPlaylist = { viewModel.deletePresetPlaylist(it) },
                        hasSelectedFirstPlaylist = uiState.hasSelectedFirstPlaylist,
                        onSelectPlaylist = { viewModel.selectPlaylist(it) },
                        onDeleteCustomPlaylist = { viewModel.deleteCustomPlaylist(it) },
                        onNavigateToAddPlaylist = { tab ->
                            addPlaylistInitialTab = tab
                            showAddPlaylistScreen = true
                        }
                    )
                    NavigationItem.XTREAM -> XtreamScreen(
                        accounts = xtreamAccounts,
                        activeAccount = activeXtreamAccount ?: xtreamAccounts.firstOrNull(),
                        xtreamChannels = xtreamChannels,
                        xtreamVodChannels = xtreamVodChannels,
                        isLoading = isXtreamLoading,
                        authError = xtreamAuthError,
                        currentPlayingChannel = uiState.activeChannel,
                        onAddAccount = { name, url, user, pass, onComplete ->
                            viewModel.addXtreamAccount(name, url, user, pass, onComplete)
                        },
                        onClearAuthError = { viewModel.clearXtreamAuthError() },
                        onSetActiveAccount = { viewModel.setActiveXtreamAccount(it) },
                        onDeleteAccount = { viewModel.deleteXtreamAccount(it) },
                        onPlayChannel = { viewModel.playXtreamChannel(it) },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onBackClick = { selectedNav = NavigationItem.HOME }
                    )
                    NavigationItem.SETTINGS -> SettingsScreen(
                        currentLanguage = uiState.currentLanguage,
                        currentThemeMode = uiState.currentThemeMode,
                        autoReconnectEnabled = uiState.autoReconnectEnabled,
                        searchQuery = uiState.searchQuery,
                        onSearchQueryChange = { viewModel.setSearchQuery(it) },
                        onToggleAutoReconnect = { viewModel.setAutoReconnectEnabled(it) },
                        onSelectLanguage = { viewModel.setLanguage(it) },
                        onSelectThemeMode = {
                            viewModel.setThemeMode(it)
                            onToggleTheme(it == AppThemeMode.DARK)
                        },
                        onBackClick = { selectedNav = NavigationItem.HOME }
                    )
                }
            }
        }
    }
}

@Composable
private fun Center3DNavFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 3D Popped-out Squircle Red Plus Button with ambient glow halo
    // Fully rendered with zero top cutoff
    Box(
        modifier = modifier
            .size(54.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer soft red ambient glow halo
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFFE50914).copy(alpha = 0.28f))
        )

        // Main 3D Squircle Button
        Box(
            modifier = Modifier
                .size(46.dp)
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(16.dp))
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.65f),
                            Color(0xFFFF7B7B).copy(alpha = 0.3f),
                            Color(0xFF7F1D1D)
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .clip(RoundedCornerShape(16.dp))
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color(0xFFDC2626), // Vibrant red top
                            Color(0xFFB91C1C), // Rich crimson mid
                            Color(0xFF7F1D1D)  // Deep shadow bottom
                        )
                    )
                )
                .clickable(onClick = onClick)
                .testTag("center_nav_fab_button"),
            contentAlignment = Alignment.Center
        ) {
            // Upper glossy specular highlight
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(18.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.35f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Bold White Plus Icon
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Playlist",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun BottomNavPillItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val appColors = LocalAppColors.current
    val unselectedColor = if (appColors.isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) Color(0xFFDC2626) else unselectedColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = if (isSelected) Color(0xFFDC2626) else unselectedColor,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun DockedMiniPlayer(
    channel: Channel,
    playerManager: com.example.player.IptvPlayerManager,
    onExpandFullscreen: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val isPlaying by playerManager.isPlaying.collectAsState()
    val playbackState by playerManager.playbackState.collectAsState()
    val currentPosition by playerManager.currentPosition.collectAsState()
    val duration by playerManager.duration.collectAsState()
    val isMovie = channel.isMovieOrVod || duration > 0L

    fun formatMiniDuration(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val hrs = totalSec / 3600
        val mins = (totalSec % 3600) / 60
        val secs = totalSec % 60
        return if (hrs > 0) {
            String.format("%d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format("%02d:%02d", mins, secs)
        }
    }

    val progressFraction = if (duration > 0L) {
        (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        1f
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (appColors.isDark) Color(0xFF141824) else Color(0xFF1E293B)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount < -22f) {
                        onExpandFullscreen()
                    } else if (dragAmount > 35f) {
                        onClose()
                    }
                }
            }
            .clickable(onClick = onExpandFullscreen)
            .testTag("docked_mini_player")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .padding(start = 6.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // YouTube-style 16:9 Live Video Preview Box on the left
                Box(
                    modifier = Modifier
                        .width(108.dp)
                        .height(58.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color.Black)
                        .clickable(onClick = onExpandFullscreen)
                        .testTag("mini_player_video_box"),
                    contentAlignment = Alignment.Center
                ) {
                    // Backdrop Channel Logo while frames render
                    com.example.ui.components.ChannelLogo(
                        channel = channel,
                        modifier = Modifier.fillMaxSize(),
                        imagePadding = 8.dp,
                        fontSize = 13.sp,
                        showCategoryIcon = false,
                        cornerRadius = 9.dp
                    )

                    // Live ExoPlayer SurfaceView inside Mini-Player
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                useController = false
                                keepScreenOn = true
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                val activeExo = playerManager.getPlayer()
                                player = activeExo
                                (videoSurfaceView as? android.view.SurfaceView)?.let {
                                    activeExo.setVideoSurfaceView(it)
                                }
                            }
                        },
                        update = { playerView ->
                            val activeExo = playerManager.getPlayer()
                            if (playerView.player != activeExo) {
                                playerView.player = activeExo
                            }
                            (playerView.videoSurfaceView as? android.view.SurfaceView)?.let {
                                activeExo.setVideoSurfaceView(it)
                            }
                        },
                        onReset = {},
                        onRelease = { playerView ->
                            playerView.player = null
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Subtle buffering spinner overlay
                    if (playbackState is PlayerState.Connecting || playbackState is PlayerState.Buffering) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = OttPrimary
                            )
                        }
                    }

                    // LIVE / MOVIE Badge in bottom-left of mini video
                    Surface(
                        color = if (isMovie) OttGold else OttLiveRed,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(3.dp)
                    ) {
                        Text(
                            text = if (isMovie) "MOVIE" else "LIVE",
                            color = if (isMovie) Color.Black else Color.White,
                            fontSize = 7.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 3.5.dp, vertical = 1.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Title & Category / Time Info
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onExpandFullscreen),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = channel.name,
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = channel.category.ifBlank { if (channel.isXtreamChannel) "Xtream" else "Live TV" },
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isMovie && duration > 0L) {
                            Text(
                                text = " • ${formatMiniDuration(currentPosition)} / ${formatMiniDuration(duration)}",
                                color = OttGold,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        } else {
                            Text(
                                text = if (isPlaying) " • Playing" else " • Paused",
                                color = if (isPlaying) Color(0xFF4ADE80) else Color.White.copy(alpha = 0.6f),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Play / Pause Button
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

                // Close (X) Button to completely exit video
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

            // Bottom YouTube-style Red Progress / Live Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .background(Color.White.copy(alpha = 0.15f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction)
                        .height(2.5.dp)
                        .background(Color(0xFFE50914))
                )
            }
        }
    }
}
