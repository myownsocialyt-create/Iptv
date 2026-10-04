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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.ad.PosterNativeAdView
import com.example.data.local.CustomPlaylistEntity
import com.example.data.model.Channel
import com.example.data.repository.PlaylistSource
import com.example.data.repository.PresetPlaylists
import com.example.player.IptvPlayerManager
import com.example.player.IptvVideoPlayer
import com.example.ui.components.ChannelListItem
import com.example.ui.theme.LocalAppColors
import com.example.ui.theme.OttGold
import com.example.ui.theme.OttLiveRed
import com.example.ui.theme.OttPrimary

@Composable
fun HypnotixSplitScreen(
    channels: List<Channel>,
    categories: List<String>,
    activeChannel: Channel?,
    playerManager: IptvPlayerManager,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onEnterPip: () -> Unit = {},
    onClosePlayer: () -> Unit = {},
    onChannelSelect: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    currentPlaylist: PlaylistSource = PresetPlaylists.INDIA,
    customPlaylists: List<CustomPlaylistEntity> = emptyList(),
    deletedPresetIds: Set<String> = emptySet(),
    onSelectPlaylist: (PlaylistSource) -> Unit = {},
    isLoading: Boolean = false,
    statusMessage: String? = null,
    onRefresh: (() -> Unit)? = null,
    isXtreamMode: Boolean = false,
    xtreamAccountName: String = "Xtream IPTV",
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    val filteredChannels by remember(channels, searchQuery, selectedCategory) {
        derivedStateOf {
            channels.filter { channel ->
                val matchesCat = selectedCategory == null || channel.category.equals(selectedCategory, ignoreCase = true)
                val matchesQuery = searchQuery.isBlank() || channel.name.contains(searchQuery.trim(), ignoreCase = true)
                matchesCat && matchesQuery
            }
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(appColors.background)) {
        if (isLandscape) {
            // Landscape Hypnotix Layout
            Row(modifier = Modifier.fillMaxSize()) {
                // Video Player Area
                Box(
                    modifier = Modifier
                        .weight(1.5f)
                        .fillMaxHeight()
                        .background(Color.Black)
                ) {
                    if (activeChannel != null) {
                        IptvVideoPlayer(
                            channel = activeChannel,
                            playerManager = playerManager,
                            allChannels = channels,
                            isFullscreen = isFullscreen,
                            isPipMode = false,
                            onToggleFullscreen = onToggleFullscreen,
                            onEnterPip = onEnterPip,
                            onClosePlayer = onClosePlayer,
                            onChannelSelect = onChannelSelect,
                            onToggleFavorite = onToggleFavorite,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Channel Sidebar
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(appColors.surface)
                        .padding(8.dp)
                ) {
                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Filter channels...", fontSize = 12.sp, color = appColors.textMuted) },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = appColors.textMuted, modifier = Modifier.size(16.dp))
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = OttPrimary,
                            unfocusedBorderColor = appColors.border,
                            focusedTextColor = appColors.textPrimary,
                            unfocusedTextColor = appColors.textPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(
                            items = filteredChannels,
                            key = { index, ch -> "${ch.id}_${ch.streamUrl}_$index" }
                        ) { _, channel ->
                            ChannelListItem(
                                channel = channel,
                                isPlaying = channel.streamUrl == activeChannel?.streamUrl,
                                onClick = { onChannelSelect(channel) },
                                onToggleFavorite = { onToggleFavorite(channel) }
                            )
                        }
                    }
                }
            }
        } else {
            // Portrait Dual-View Layout
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. Video Player Top Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                ) {
                    if (activeChannel != null) {
                        IptvVideoPlayer(
                            channel = activeChannel,
                            playerManager = playerManager,
                            allChannels = channels,
                            isFullscreen = isFullscreen,
                            isPipMode = false,
                            onToggleFullscreen = onToggleFullscreen,
                            onEnterPip = onEnterPip,
                            onClosePlayer = onClosePlayer,
                            onChannelSelect = onChannelSelect,
                            onToggleFavorite = onToggleFavorite,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // 2. Active Channel Info Header
                if (activeChannel != null) {
                    Surface(
                        color = appColors.surface,
                        border = BorderStroke(1.dp, appColors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                if (!activeChannel.logoUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = activeChannel.logoUrl,
                                        contentDescription = activeChannel.name,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Column {
                                    Text(
                                        text = activeChannel.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = appColors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(color = OttLiveRed, shape = RoundedCornerShape(3.dp)) {
                                            Text("LIVE", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(activeChannel.category, fontSize = 11.sp, color = appColors.textSecondary)
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(
                                    onClick = { onToggleFavorite(activeChannel) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    border = BorderStroke(1.dp, if (activeChannel.isFavorite) OttGold else appColors.border)
                                ) {
                                    Icon(
                                        imageVector = if (activeChannel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = null,
                                        tint = if (activeChannel.isFavorite) OttGold else appColors.textSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (activeChannel.isFavorite) "Saved" else "Save", fontSize = 10.sp, color = if (activeChannel.isFavorite) OttGold else appColors.textSecondary)
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                IconButton(onClick = onEnterPip, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.PictureInPictureAlt, contentDescription = "PiP", tint = appColors.textSecondary, modifier = Modifier.size(18.dp))
                                }

                                IconButton(onClick = onToggleFullscreen, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen", tint = appColors.textSecondary, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }

                // 3. Search Bar
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Filter channels by name...", color = appColors.textMuted, fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = appColors.textMuted, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = appColors.textMuted, modifier = Modifier.size(18.dp))
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
                        modifier = Modifier.fillMaxWidth().testTag("channel_filter_input")
                    )
                }

                // 4. Poster Native Ad (Directly below search bar, with [X] close button and auto-rotation)
                PosterNativeAdView()

                // 5. Category Chips Row
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null },
                            label = { Text("All", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = OttPrimary,
                                selectedLabelColor = Color.White,
                                containerColor = appColors.surfaceVariant,
                                labelColor = appColors.textSecondary
                            ),
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                    items(categories) { cat ->
                        val isSelected = selectedCategory.equals(cat, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = if (isSelected) null else cat },
                            label = { Text(cat, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = OttPrimary,
                                selectedLabelColor = Color.White,
                                containerColor = appColors.surfaceVariant,
                                labelColor = appColors.textSecondary
                            ),
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                }

                // 6. Channels List Feed
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    itemsIndexed(
                        items = filteredChannels,
                        key = { index, ch -> "${ch.id}_${ch.streamUrl}_$index" }
                    ) { _, channel ->
                        ChannelListItem(
                            channel = channel,
                            isPlaying = channel.streamUrl == activeChannel?.streamUrl,
                            onClick = { onChannelSelect(channel) },
                            onToggleFavorite = { onToggleFavorite(channel) }
                        )
                    }
                }
            }
        }
    }
}
