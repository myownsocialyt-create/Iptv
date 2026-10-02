package com.example.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.CustomPlaylistEntity
import com.example.data.model.Channel
import com.example.data.model.CommunityPlaylists
import com.example.data.repository.PlaylistSource
import com.example.data.repository.PresetPlaylists
import com.example.player.IptvVideoPlayer
import com.example.ui.components.ChannelListItem
import com.example.ui.components.ChannelListShimmer
import com.example.ui.theme.LocalAppColors
import com.example.ui.theme.OttGold
import com.example.ui.theme.OttLiveRed
import com.example.ui.theme.OttPrimary

@Composable
fun HypnotixSplitScreen(
    channels: List<Channel>,
    categories: List<String>,
    activeChannel: Channel?,
    playerManager: com.example.player.IptvPlayerManager,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onEnterPip: () -> Unit = {},
    onClosePlayer: () -> Unit = {},
    onChannelSelect: (Channel) -> Unit = {},
    onToggleFavorite: (Channel) -> Unit = {},
    currentPlaylist: PlaylistSource = PresetPlaylists.INDIA,
    customPlaylists: List<CustomPlaylistEntity> = emptyList(),
    deletedPresetIds: Set<String> = emptySet(),
    onSelectPlaylist: (PlaylistSource) -> Unit = {},
    isLoading: Boolean = false,
    statusMessage: String? = null,
    onRefresh: (() -> Unit)? = null,
    isXtreamMode: Boolean = false,
    xtreamAccountName: String = "Xtream IPTV",
    onBack: () -> Unit = onClosePlayer,
    selectedCategory: String? = null,
    onCategorySelected: ((String) -> Unit)? = null,
    searchQuery: String? = null,
    onSearchQueryChanged: ((String) -> Unit)? = null,
    onChannelClick: (Channel) -> Unit = onChannelSelect,
    onPreviousChannel: (() -> Unit)? = null,
    onNextChannel: (() -> Unit)? = null,
    currentPlaylistSource: PlaylistSource = currentPlaylist,
    onSelectPlaylistSource: (PlaylistSource) -> Unit = onSelectPlaylist,
    onRefreshPlaylist: (() -> Unit)? = onRefresh,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? android.app.Activity
    val appColors = LocalAppColors.current

    var currentFilterQuery by remember(searchQuery) { mutableStateOf(searchQuery ?: "") }
    var currentCategoryFilter by remember(selectedCategory) { mutableStateOf(if (selectedCategory == "All") null else selectedCategory) }

    val onCategoryFilterChange = { cat: String? ->
        currentCategoryFilter = cat
        if (cat == null) {
            onCategorySelected?.invoke("All")
        } else {
            onCategorySelected?.invoke(cat)
        }
        com.example.ad.AdManager.onUserNavigated(activity)
    }

    val onSearchQueryChange = { query: String ->
        currentFilterQuery = query
        onSearchQueryChanged?.invoke(query)
    }

    val activePlaylist = if (currentPlaylistSource != PresetPlaylists.INDIA) currentPlaylistSource else currentPlaylist
    val effectiveSelectPlaylist = { source: PlaylistSource ->
        onSelectPlaylist(source)
        onSelectPlaylistSource(source)
        com.example.ad.AdManager.onUserNavigated(activity)
    }
    val effectiveChannelSelect = { ch: Channel ->
        onChannelSelect(ch)
        onChannelClick(ch)
        com.example.ad.AdManager.onUserNavigated(activity)
    }

    var showSettingsDialog by remember { mutableStateOf(false) }

    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(Unit) {
        val window = (view.context as? android.app.Activity)?.window
        if (window != null) {
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            window.statusBarColor = android.graphics.Color.BLACK
            insetsController.isAppearanceLightStatusBars = false
        }
        onDispose {
            val window = (view.context as? android.app.Activity)?.window
            if (window != null && !appColors.isDark) {
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                window.statusBarColor = android.graphics.Color.WHITE
                insetsController.isAppearanceLightStatusBars = true
            }
        }
    }

    val filteredChannels by remember(channels, currentFilterQuery, currentCategoryFilter) {
        derivedStateOf {
            channels.filter { channel ->
                val matchesCat = currentCategoryFilter == null || currentCategoryFilter.equals("All", ignoreCase = true) || channel.category.equals(currentCategoryFilter, ignoreCase = true)
                val matchesQuery = currentFilterQuery.isBlank() ||
                        channel.name.contains(currentFilterQuery.trim(), ignoreCase = true)
                matchesCat && matchesQuery
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("hypnotix_split_screen")
    ) {
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || maxWidth >= 600.dp
        val inStreamAdState by com.example.ad.InStreamAdManager.adState.collectAsState()

        if (isFullscreen && activeChannel != null) {
            // FULLSCREEN VIDEO PLAYER (Taking 100% of screen in Landscape or Portrait - Photo 1)
            IptvVideoPlayer(
                channel = activeChannel,
                playerManager = playerManager,
                allChannels = channels,
                isFullscreen = true,
                onToggleFullscreen = onToggleFullscreen,
                onEnterPip = onEnterPip,
                onClosePlayer = onClosePlayer,
                onChannelSelect = onChannelSelect,
                onToggleFavorite = onToggleFavorite,
                modifier = Modifier.fillMaxSize()
            )
        } else if (!isLandscape) {
            // PORTRAIT MODE: YouTube-style layout
            // Top: 16:9 Video Player + Channel Info Bar (safe from notch via statusBarsPadding)
            // Downside: Channel search, category filters, and selectable channel list
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .background(appColors.background)
            ) {
                // 1. Top Section: Video Player
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                        .testTag("portrait_player_container")
                ) {
                    if (activeChannel != null) {
                        IptvVideoPlayer(
                            channel = activeChannel,
                            playerManager = playerManager,
                            allChannels = channels,
                            isFullscreen = isFullscreen,
                            onToggleFullscreen = onToggleFullscreen,
                            onEnterPip = onEnterPip,
                            onClosePlayer = onClosePlayer,
                            onChannelSelect = onChannelSelect,
                            onToggleFavorite = onToggleFavorite,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Empty State when no channel is playing yet
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(
                                    color = OttPrimary.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(24.dp),
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.LiveTv,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Select a Channel Below",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Tap any channel to start watching live stream",
                                    color = appColors.textSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // 2. Active Channel Info Bar OR YouTube Companion Ad Card (Photo 1)
                if (inStreamAdState.isAdActive && inStreamAdState.creative != null) {
                    com.example.ad.YouTubeCompanionAdCard(
                        creative = inStreamAdState.creative!!
                    )
                } else if (activeChannel != null) {
                    Surface(
                        color = appColors.surface,
                        shadowElevation = if (appColors.isDark) 0.dp else 2.dp,
                        border = BorderStroke(1.dp, appColors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            // Row 1: Channel logo, name, LIVE badge, category & language, and action buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (!activeChannel.logoUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = activeChannel.logoUrl,
                                            contentDescription = activeChannel.name,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(appColors.surfaceVariant)
                                                .padding(3.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                    } else {
                                        Surface(
                                            modifier = Modifier.size(40.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            color = OttPrimary.copy(alpha = 0.2f)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.LiveTv,
                                                    contentDescription = null,
                                                    tint = OttPrimary,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f, fill = false)) {
                                        Text(
                                            text = activeChannel.name,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = appColors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        val subtitleParts = buildList {
                                            if (activeChannel.category.isNotBlank()) add(activeChannel.category)
                                            if (!activeChannel.language.isNullOrBlank() &&
                                                !activeChannel.language.equals("Unknown", ignoreCase = true) &&
                                                !activeChannel.language.equals(activeChannel.category, ignoreCase = true)
                                            ) {
                                                add(activeChannel.language)
                                            }
                                        }
                                        val subtitleText = subtitleParts.joinToString(" • ")

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (activeChannel.isMovieOrVod) {
                                                Surface(
                                                    color = OttGold,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "MOVIE",
                                                        color = Color.Black,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                    )
                                                }
                                            } else {
                                                Surface(
                                                    color = OttLiveRed,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "LIVE",
                                                        color = Color.White,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                            if (subtitleText.isNotBlank()) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = subtitleText,
                                                    fontSize = 11.sp,
                                                    color = appColors.textSecondary,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedButton(
                                        onClick = { onToggleFavorite(activeChannel) },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        border = BorderStroke(1.dp, if (activeChannel.isFavorite) OttGold else appColors.border)
                                    ) {
                                        Icon(
                                            imageVector = if (activeChannel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = null,
                                            tint = if (activeChannel.isFavorite) OttGold else appColors.textSecondary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (activeChannel.isFavorite) "Saved" else "Favorite",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (activeChannel.isFavorite) OttGold else appColors.textSecondary
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    IconButton(
                                        onClick = onEnterPip,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("portrait_pip_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PictureInPictureAlt,
                                            contentDescription = "Picture in Picture",
                                            tint = appColors.textSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = onToggleFullscreen,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Fullscreen,
                                            contentDescription = "Fullscreen",
                                            tint = appColors.textSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Row 2: YouTube-style Controls: Dual View Badge and Playlist/Xtream Selector
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Dual View Badge in place of Back
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = OttPrimary.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, OttPrimary.copy(alpha = 0.35f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ViewSidebar,
                                            contentDescription = null,
                                            tint = OttPrimary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "Dual View",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = OttPrimary
                                        )
                                    }
                                }

                                // Playlist / Xtream Selector & Left-aligned Refresh Button (No overlap)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (onRefresh != null) {
                                        IconButton(
                                            onClick = onRefresh,
                                            modifier = Modifier
                                                .size(32.dp)
                                                .testTag("split_refresh_playlist_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Refresh",
                                                tint = OttPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            com.example.ad.AdManager.onUserNavigated(activity)
                                            showSettingsDialog = true
                                        },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .testTag("split_settings_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = "Settings",
                                            tint = appColors.textSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    if (isXtreamMode) {
                                        // Xtream Account Pill (No M3U playlist dropdown)
                                        Surface(
                                            shape = RoundedCornerShape(18.dp),
                                            color = OttPrimary.copy(alpha = 0.14f),
                                            border = BorderStroke(1.dp, OttPrimary.copy(alpha = 0.35f)),
                                            modifier = Modifier.testTag("split_xtream_account_pill")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = "⚡ $xtreamAccountName",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = appColors.textPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    } else {
                                        // Playlist Selector Dropdown Pill
                                        Box {
                                            var showPlaylistMenu by remember { mutableStateOf(false) }

                                            Surface(
                                                onClick = { showPlaylistMenu = true },
                                                shape = RoundedCornerShape(18.dp),
                                                color = appColors.surfaceVariant,
                                                border = BorderStroke(1.dp, appColors.border),
                                                modifier = Modifier.testTag("split_playlist_dropdown_trigger")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    val flagIcon = when {
                                                        currentPlaylist.id == PresetPlaylists.INDIA.id || currentPlaylist.title.contains("India", ignoreCase = true) -> "🇮🇳"
                                                        currentPlaylist.id == PresetPlaylists.GLOBAL.id || currentPlaylist.title.contains("Global", ignoreCase = true) -> "🌐"
                                                        currentPlaylist.url.startsWith("file://") -> "📄"
                                                        else -> "📁"
                                                    }
                                                    Text(
                                                        text = "$flagIcon ${currentPlaylist.title}",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = appColors.textPrimary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Icon(
                                                        imageVector = Icons.Default.ArrowDropDown,
                                                        contentDescription = "Switch Playlist",
                                                        tint = appColors.textSecondary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }

                                            DropdownMenu(
                                                expanded = showPlaylistMenu,
                                                onDismissRequest = { showPlaylistMenu = false },
                                                modifier = Modifier
                                                    .background(appColors.surface)
                                                    .heightIn(max = 440.dp)
                                                    .widthIn(min = 260.dp, max = 340.dp)
                                            ) {
                                        Text(
                                            text = "SWITCH PLAYLIST",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = appColors.textMuted,
                                            letterSpacing = 1.sp,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                        )

                                        // SECTION 1: PRESET PLAYLISTS
                                        val showIndia = PresetPlaylists.INDIA.id !in deletedPresetIds
                                        val showGlobal = PresetPlaylists.GLOBAL.id !in deletedPresetIds

                                        if (showIndia || showGlobal) {
                                            Text(
                                                text = "PRESET PLAYLISTS",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = OttPrimary,
                                                letterSpacing = 0.5.sp,
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                            )
                                        }

                                        // Preset 1: India
                                        if (showIndia) {
                                            val isIndiaSelected = currentPlaylist.id == PresetPlaylists.INDIA.id || currentPlaylist.url == PresetPlaylists.INDIA.url
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = "🇮🇳 India Channels",
                                                        color = appColors.textPrimary,
                                                        fontSize = 13.sp,
                                                        fontWeight = if (isIndiaSelected) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                },
                                                trailingIcon = {
                                                    if (isIndiaSelected) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = null,
                                                            tint = OttPrimary,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    showPlaylistMenu = false
                                                    effectiveSelectPlaylist(PresetPlaylists.INDIA)
                                                }
                                            )
                                        }

                                        // Preset 2: Global
                                        if (showGlobal) {
                                            val isGlobalSelected = currentPlaylist.id == PresetPlaylists.GLOBAL.id || currentPlaylist.url == PresetPlaylists.GLOBAL.url
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = "🌐 Global Channels",
                                                        color = appColors.textPrimary,
                                                        fontSize = 13.sp,
                                                        fontWeight = if (isGlobalSelected) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                },
                                                trailingIcon = {
                                                    if (isGlobalSelected) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = null,
                                                            tint = OttPrimary,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    showPlaylistMenu = false
                                                    effectiveSelectPlaylist(PresetPlaylists.GLOBAL)
                                                }
                                            )
                                        }

                                        // SECTION 2: USER ADDED PLAYLISTS (EXCLUDING XTREAM ACCOUNTS)
                                        val filteredPlaylists = customPlaylists.filter {
                                            !it.url.contains("player_api.php") &&
                                            !it.title.contains("xtream", ignoreCase = true)
                                        }

                                        if (filteredPlaylists.isNotEmpty()) {
                                            HorizontalDivider(color = appColors.border, modifier = Modifier.padding(vertical = 4.dp))
                                            Text(
                                                text = "ADDED PLAYLISTS (${filteredPlaylists.size})",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = OttPrimary,
                                                letterSpacing = 0.5.sp,
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                            )
                                            filteredPlaylists.forEach { cp ->
                                                val isSelected = currentPlaylist.id == "custom_${cp.id}" || currentPlaylist.url == cp.url
                                                val iconPrefix = if (cp.url.startsWith("file://")) "📄" else "📁"
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = "$iconPrefix ${cp.title}",
                                                            color = appColors.textPrimary,
                                                            fontSize = 13.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    },
                                                    trailingIcon = {
                                                        if (isSelected) {
                                                            Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = null,
                                                                tint = OttPrimary,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    },
                                                    onClick = {
                                                        showPlaylistMenu = false
                                                        effectiveSelectPlaylist(
                                                            PlaylistSource(
                                                                id = "custom_${cp.id}",
                                                                title = cp.title,
                                                                url = cp.url,
                                                                isPreset = false
                                                            )
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

                // 3. Downside Channel Selector (YouTube recommendations style)
                val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(appColors.background)
                ) {
                    // Search Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        OutlinedTextField(
                            value = currentFilterQuery,
                            onValueChange = {
                                currentFilterQuery = it
                                onSearchQueryChanged?.invoke(it)
                            },
                            placeholder = { Text("Filter channels by name...", color = appColors.textMuted, fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = appColors.textMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                if (currentFilterQuery.isNotEmpty()) {
                                    IconButton(onClick = {
                                        currentFilterQuery = ""
                                        onSearchQueryChanged?.invoke("")
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear",
                                            tint = appColors.textMuted,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OttPrimary,
                                unfocusedBorderColor = appColors.border,
                                focusedContainerColor = appColors.surfaceVariant,
                                unfocusedContainerColor = appColors.surfaceVariant,
                                focusedTextColor = appColors.textPrimary,
                                unfocusedTextColor = appColors.textPrimary,
                                cursorColor = OttPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("channel_filter_input")
                        )
                    }

                    // Category Chips Row
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = currentCategoryFilter == null,
                                onClick = { onCategoryFilterChange(null) },
                                label = { Text("All", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = OttPrimary,
                                    selectedLabelColor = Color.White,
                                    containerColor = appColors.surfaceVariant,
                                    labelColor = appColors.textSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = currentCategoryFilter == null,
                                    borderColor = appColors.border,
                                    selectedBorderColor = OttPrimary
                                ),
                                shape = RoundedCornerShape(14.dp)
                            )
                        }
                        items(categories) { cat ->
                            val isSelected = currentCategoryFilter.equals(cat, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = { onCategoryFilterChange(if (isSelected) null else cat) },
                                label = { Text(cat, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = OttPrimary,
                                    selectedLabelColor = Color.White,
                                    containerColor = appColors.surfaceVariant,
                                    labelColor = appColors.textSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = appColors.border,
                                    selectedBorderColor = OttPrimary
                                ),
                                shape = RoundedCornerShape(14.dp)
                            )
                        }
                    }

                    // Poster Type Ad Card (Photo 1) - strictly below video player, reloads randomly
                    com.example.ad.PosterAdCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    )

                    // Channel Count Subheader
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Channels (${filteredChannels.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = appColors.textSecondary
                        )
                        if (activeChannel != null) {
                            Text(
                                text = "Playing: ${activeChannel.name}",
                                fontSize = 11.sp,
                                color = OttPrimary,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Channel List
                    if (isLoading && channels.isEmpty()) {
                        ChannelListShimmer(
                            count = 6,
                            statusMessage = statusMessage ?: "Parsing & Loading M3U Playlist...",
                            showHeaderBanner = true,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = navBarBottom)
                        )
                    } else if (filteredChannels.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp)
                                .padding(bottom = navBarBottom),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (currentFilterQuery.isNotBlank()) "No channels matching \"$currentFilterQuery\"" else "No channels found in this playlist",
                                color = appColors.textMuted,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(
                                start = 12.dp,
                                end = 12.dp,
                                top = 4.dp,
                                bottom = 8.dp + navBarBottom
                            ),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredChannels, key = { it.streamUrl }) { ch ->
                                ChannelListItem(
                                    channel = ch,
                                    isPlaying = ch.streamUrl == activeChannel?.streamUrl,
                                    onClick = { effectiveChannelSelect(ch) },
                                    onToggleFavorite = { onToggleFavorite(ch) }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // LANDSCAPE MODE: Hypnotix side-by-side Dual Split View
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .background(appColors.background)
            ) {
                // Left Pane: Channel Catalog (Hypnotix Channel Navigator)
                Column(
                    modifier = Modifier
                        .weight(0.42f)
                        .fillMaxHeight()
                        .background(appColors.surface)
                ) {
                    // Top Bar for Landscape: Dual View and Playlist/Xtream Selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Dual View",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = OttPrimary
                            )
                            IconButton(
                                onClick = {
                                    com.example.ad.AdManager.onUserNavigated(activity)
                                    showSettingsDialog = true
                                },
                                modifier = Modifier
                                    .size(26.dp)
                                    .testTag("landscape_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = appColors.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        if (isXtreamMode) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = OttPrimary.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, OttPrimary.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "⚡ $xtreamAccountName",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = appColors.textPrimary,
                                        maxLines = 1
                                    )
                                }
                            }
                        } else {
                            // Compact Playlist Pill
                            Box {
                                var showLandscapeMenu by remember { mutableStateOf(false) }

                                Surface(
                                    onClick = { showLandscapeMenu = true },
                                    shape = RoundedCornerShape(14.dp),
                                    color = appColors.surfaceVariant,
                                    border = BorderStroke(1.dp, appColors.border)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        val icon = when (currentPlaylist.id) {
                                            PresetPlaylists.INDIA.id -> "🇮🇳"
                                            PresetPlaylists.GLOBAL.id -> "🌐"
                                            else -> "📁"
                                        }
                                        Text(
                                            text = "$icon ${currentPlaylist.title}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = appColors.textPrimary,
                                            maxLines = 1
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = appColors.textSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = showLandscapeMenu,
                                    onDismissRequest = { showLandscapeMenu = false },
                                    modifier = Modifier.background(appColors.surface)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("✨ All Playlists", color = appColors.textPrimary, fontSize = 12.sp) },
                                        trailingIcon = {
                                            if (currentPlaylist.id == PresetPlaylists.ALL.id) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = OttPrimary, modifier = Modifier.size(14.dp))
                                            }
                                        },
                                        onClick = {
                                            showLandscapeMenu = false
                                            effectiveSelectPlaylist(PresetPlaylists.ALL)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("🇮🇳 India Channels", color = appColors.textPrimary, fontSize = 12.sp) },
                                        trailingIcon = {
                                            if (currentPlaylist.id == PresetPlaylists.INDIA.id) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = OttPrimary, modifier = Modifier.size(14.dp))
                                            }
                                        },
                                        onClick = {
                                            showLandscapeMenu = false
                                            effectiveSelectPlaylist(PresetPlaylists.INDIA)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("🌐 Global Channels", color = appColors.textPrimary, fontSize = 12.sp) },
                                        trailingIcon = {
                                            if (currentPlaylist.id == PresetPlaylists.GLOBAL.id) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = OttPrimary, modifier = Modifier.size(14.dp))
                                            }
                                        },
                                        onClick = {
                                            showLandscapeMenu = false
                                            effectiveSelectPlaylist(PresetPlaylists.GLOBAL)
                                        }
                                    )
                                    if (customPlaylists.isNotEmpty()) {
                                        HorizontalDivider(color = appColors.border, modifier = Modifier.padding(vertical = 4.dp))
                                        customPlaylists.forEach { cp ->
                                            val isSelected = currentPlaylist.id == "custom_${cp.id}"
                                            DropdownMenuItem(
                                                text = { Text("📁 ${cp.title}", color = appColors.textPrimary, fontSize = 12.sp) },
                                                trailingIcon = {
                                                    if (isSelected) {
                                                        Icon(Icons.Default.Check, contentDescription = null, tint = OttPrimary, modifier = Modifier.size(14.dp))
                                                    }
                                                },
                                                onClick = {
                                                    showLandscapeMenu = false
                                                    effectiveSelectPlaylist(PlaylistSource("custom_${cp.id}", cp.title, cp.url, false))
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Channel Search Bar
                    Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        OutlinedTextField(
                            value = currentFilterQuery,
                            onValueChange = { onSearchQueryChange(it) },
                            placeholder = { Text("Filter channels by name...", color = appColors.textMuted, fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = appColors.textMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                if (currentFilterQuery.isNotEmpty()) {
                                    IconButton(onClick = { onSearchQueryChange("") }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear",
                                            tint = appColors.textMuted,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OttPrimary,
                                unfocusedBorderColor = appColors.border,
                                focusedContainerColor = appColors.surfaceVariant,
                                unfocusedContainerColor = appColors.surfaceVariant,
                                focusedTextColor = appColors.textPrimary,
                                unfocusedTextColor = appColors.textPrimary,
                                cursorColor = OttPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("channel_filter_input")
                        )
                    }

                    // Category Chips Row
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = currentCategoryFilter == null,
                                onClick = { onCategoryFilterChange(null) },
                                label = { Text("All", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = OttPrimary,
                                    selectedLabelColor = Color.White,
                                    containerColor = appColors.surfaceVariant,
                                    labelColor = appColors.textSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = currentCategoryFilter == null,
                                    borderColor = appColors.border,
                                    selectedBorderColor = OttPrimary
                                ),
                                shape = RoundedCornerShape(14.dp)
                            )
                        }
                        items(categories) { cat ->
                            val isSelected = currentCategoryFilter.equals(cat, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = { onCategoryFilterChange(if (isSelected) null else cat) },
                                label = { Text(cat, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = OttPrimary,
                                    selectedLabelColor = Color.White,
                                    containerColor = appColors.surfaceVariant,
                                    labelColor = appColors.textSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = appColors.border,
                                    selectedBorderColor = OttPrimary
                                ),
                                shape = RoundedCornerShape(14.dp)
                            )
                        }
                    }

                    // Channel List
                    if (isLoading && channels.isEmpty()) {
                        ChannelListShimmer(
                            count = 6,
                            statusMessage = statusMessage ?: "Parsing & Loading M3U...",
                            showHeaderBanner = false,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (filteredChannels.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (currentFilterQuery.isNotBlank()) "No channels matching \"$currentFilterQuery\"" else "No channels found in this playlist",
                                color = appColors.textMuted,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredChannels, key = { it.streamUrl }) { ch ->
                                ChannelListItem(
                                    channel = ch,
                                    isPlaying = ch.streamUrl == activeChannel?.streamUrl,
                                    onClick = { effectiveChannelSelect(ch) },
                                    onToggleFavorite = { onToggleFavorite(ch) }
                                )
                            }
                        }
                    }
                }

                // Right Pane: ExoPlayer & Details
                Box(
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxHeight()
                        .background(appColors.background)
                        .padding(12.dp)
                ) {
                    if (activeChannel != null) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Video Player Box
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.Black),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                IptvVideoPlayer(
                                    channel = activeChannel,
                                    playerManager = playerManager,
                                    allChannels = channels,
                                    isFullscreen = isFullscreen,
                                    onToggleFullscreen = onToggleFullscreen,
                                    onEnterPip = onEnterPip,
                                    onClosePlayer = onClosePlayer,
                                    onChannelSelect = onChannelSelect,
                                    onToggleFavorite = onToggleFavorite,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (inStreamAdState.isAdActive && inStreamAdState.creative != null) {
                                com.example.ad.YouTubeCompanionAdCard(
                                    creative = inStreamAdState.creative!!
                                )
                            } else {
                                // Channel Detail Card below Player
                                Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = appColors.surface),
                                border = BorderStroke(1.dp, appColors.border),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        if (!activeChannel.logoUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = activeChannel.logoUrl,
                                                contentDescription = activeChannel.name,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(appColors.surfaceVariant)
                                                    .padding(4.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                        }
                                        Column {
                                            Text(
                                                text = activeChannel.name,
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = appColors.textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                val isMovie = activeChannel.isMovieOrVod
                                                val playerDuration by playerManager.duration.collectAsState()
                                                Surface(
                                                    color = if (isMovie) OttGold else OttLiveRed,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = if (isMovie) "MOVIE" else "LIVE",
                                                        color = if (isMovie) Color.Black else Color.White,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                if (isMovie && playerDuration > 0) {
                                                    val totalMinutes = playerDuration / (1000 * 60)
                                                    val hours = totalMinutes / 60
                                                    val minutes = totalMinutes % 60
                                                    val durStr = if (hours > 0) "${hours} hr ${minutes} min" else "${minutes} min"
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = durStr,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = OttGold
                                                    )
                                                }
                                                val landscapeSubtitleParts = buildList {
                                                    if (activeChannel.category.isNotBlank()) add(activeChannel.category)
                                                    if (!activeChannel.language.isNullOrBlank() &&
                                                        !activeChannel.language.equals("Unknown", ignoreCase = true) &&
                                                        !activeChannel.language.equals(activeChannel.category, ignoreCase = true)
                                                    ) {
                                                        add(activeChannel.language)
                                                    }
                                                }
                                                val landscapeSubtitle = landscapeSubtitleParts.joinToString(" • ")
                                                if (landscapeSubtitle.isNotBlank()) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = landscapeSubtitle,
                                                        fontSize = 12.sp,
                                                        color = appColors.textSecondary,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = onEnterPip,
                                            modifier = Modifier.testTag("landscape_pip_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PictureInPictureAlt,
                                                contentDescription = "Picture in Picture",
                                                tint = appColors.textSecondary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        OutlinedButton(
                                            onClick = { onToggleFavorite(activeChannel) },
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, if (activeChannel.isFavorite) OttGold else appColors.border)
                                        ) {
                                            Icon(
                                                imageVector = if (activeChannel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                contentDescription = null,
                                                tint = if (activeChannel.isFavorite) OttGold else appColors.textSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (activeChannel.isFavorite) "Saved" else "Favorite",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (activeChannel.isFavorite) OttGold else appColors.textSecondary
                                            )
                                        }
                                    }
                                }
                            }
                            }
                        }
                    } else {
                        // No channel selected empty state in Right Pane
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = appColors.surface),
                            border = BorderStroke(1.dp, appColors.border),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Surface(
                                        color = OttPrimary.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier.size(72.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.LiveTv,
                                                contentDescription = null,
                                                tint = OttPrimary,
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Select a Channel",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = appColors.textPrimary
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Tap any channel from the list on the left to start live streaming",
                                        fontSize = 13.sp,
                                        color = appColors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Settings Dialog (Full settings access within Split View)
        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                confirmButton = {
                    TextButton(onClick = { showSettingsDialog = false }) {
                        Text("Done", color = OttPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = OttPrimary)
                        Text("Settings & Preferences", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-Reconnect Stream", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Automatically resume video on network drop", fontSize = 11.sp, color = appColors.textMuted)
                            }
                            val isAutoReconnect by playerManager.isAutoReconnectEnabled.collectAsState()
                            Switch(
                                checked = isAutoReconnect,
                                onCheckedChange = { playerManager.setAutoReconnectEnabled(it) }
                            )
                        }
                        HorizontalDivider(color = appColors.border)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Playback Buffer", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Low Latency Live HLS (Fast Start)", fontSize = 11.sp, color = appColors.textMuted)
                            }
                            Text("Auto (Fast)", color = OttPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider(color = appColors.border)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("App Version", fontSize = 13.sp, color = appColors.textSecondary)
                            Text("v1.2.0 (Hypnotix OTT)", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = appColors.textMuted)
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                containerColor = appColors.surface
            )
        }
    }
}
