package com.example.ui.screens

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TvOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Channel
import com.example.data.repository.PlaylistSource
import com.example.data.repository.PresetPlaylists
import com.example.ui.components.ChannelListItem
import com.example.ui.components.ChannelListShimmer
import com.example.ui.components.StartWatchingLiveTvCard
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.LocalAppColors
import com.example.ui.theme.OttPrimary

@Composable
fun ChannelListScreen(
    title: String,
    channels: List<Channel>,
    allChannels: List<Channel> = emptyList(),
    searchQuery: String,
    selectedCategory: String?,
    categories: List<String>,
    currentPlayingChannel: Channel?,
    currentPlaylist: PlaylistSource = PresetPlaylists.ALL,
    isLoading: Boolean,
    statusMessage: String? = null,
    hasSelectedFirstPlaylist: Boolean = true,
    onSearchQueryChange: (String) -> Unit,
    onCategorySelect: (String?) -> Unit,
    onChannelClick: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onRefresh: (() -> Unit)? = null,
    onNavigateToPlaylists: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val strings = LocalAppStrings.current
    val isDark = appColors.isDark
    var isGridView by remember { mutableStateOf(false) }

    val categoryCounts = remember(channels) {
        channels.groupingBy { it.category }.eachCount()
    }

    val searchPool = if (allChannels.isNotEmpty()) allChannels else channels

    val filteredChannels by remember(channels, searchPool, searchQuery, selectedCategory) {
        derivedStateOf {
            if (searchQuery.isNotBlank()) {
                val query = searchQuery.trim()
                searchPool.filter { channel ->
                    val matchesCategory = selectedCategory == null || channel.category.equals(selectedCategory, ignoreCase = true)
                    val matchesSearch = channel.name.contains(query, ignoreCase = true) ||
                            channel.category.contains(query, ignoreCase = true) ||
                            (channel.playlistName != null && channel.playlistName.contains(query, ignoreCase = true))
                    matchesCategory && matchesSearch
                }
            } else {
                channels.filter { channel ->
                    selectedCategory == null || channel.category.equals(selectedCategory, ignoreCase = true)
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(appColors.background)
    ) {
        val screenWidth = maxWidth
        val columns = when {
            screenWidth >= 960.dp -> 3
            screenWidth >= 600.dp -> 2
            else -> 1
        }
        val horizontalListPadding = if (screenWidth >= 600.dp) 20.dp else 14.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 1300.dp)
                .align(Alignment.TopCenter)
        ) {
            // Top Search Bar & Category Filter Chips Row matching screenshot
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(appColors.surface)
                    .padding(horizontal = horizontalListPadding, vertical = 6.dp)
            ) {
                // Channel Search Bar
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = appColors.surfaceVariant,
                    border = BorderStroke(1.dp, appColors.border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .testTag("channel_search_bar")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Channels",
                            tint = appColors.textMuted,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(
                                color = appColors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("channel_search_input"),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = strings.searchChannelsHint,
                                        color = appColors.textMuted,
                                        fontSize = 14.sp
                                    )
                                }
                                innerTextField()
                            }
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onSearchQueryChange("") },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    tint = appColors.textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("channel_category_chips_row")
                    ) {
                        item {
                            val isSelected = selectedCategory == null
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) {
                                    if (isDark) Color(0xFF27272A) else Color(0xFFF1F5F9)
                                } else appColors.surface,
                                border = BorderStroke(1.dp, if (isSelected) appColors.textPrimary else appColors.border),
                                modifier = Modifier.clickable { onCategorySelect(null) }
                            ) {
                                Text(
                                    text = "${strings.viewAll} (${channels.size})",
                                    color = appColors.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }

                        itemsIndexed(categories, key = { index, cat -> "${cat}_$index" }) { _, cat ->
                            val isSelected = selectedCategory.equals(cat, ignoreCase = true)
                            val count = categoryCounts[cat] ?: 0
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) {
                                    if (isDark) Color(0xFF27272A) else Color(0xFFF1F5F9)
                                } else appColors.surface,
                                border = BorderStroke(1.dp, if (isSelected) appColors.textPrimary else appColors.border),
                                modifier = Modifier.clickable { onCategorySelect(if (isSelected) null else cat) }
                            ) {
                                Text(
                                    text = "$cat ($count)",
                                    color = appColors.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    // Grid / List View Toggle Button
                    IconButton(
                        onClick = { isGridView = !isGridView },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("toggle_channel_view_mode")
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.AutoMirrored.Filled.List else Icons.Default.GridView,
                            contentDescription = if (isGridView) "Switch to List View" else "Switch to Grid View",
                            tint = if (isGridView) Color(0xFFDC2626) else appColors.textMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    if (onRefresh != null) {
                        IconButton(
                            onClick = {
                                try {
                                    onRefresh()
                                } catch (e: Exception) {
                                    Log.e("ChannelListScreen", "Error refreshing", e)
                                }
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .padding(start = 2.dp)
                                .testTag("channel_list_refresh_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Channels",
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Active Category Filter Indicator Strip
                if (selectedCategory != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = if (isDark) Color(0xFF261014) else Color(0xFFFFF1F2),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (isDark) Color(0xFF4C1D24) else Color(0xFFFECDD3)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = null,
                                    tint = Color(0xFFE50914),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = strings.filterBy,
                                    color = appColors.textSecondary,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = selectedCategory,
                                    color = Color(0xFFDC2626),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = " (${filteredChannels.size} ${strings.channel})",
                                    color = appColors.textMuted,
                                    fontSize = 13.sp
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { onCategorySelect(null) }
                                    .padding(start = 8.dp)
                            ) {
                                Text(
                                    text = strings.clear,
                                    color = Color(0xFFDC2626),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear category filter",
                                    tint = Color(0xFFDC2626),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Channels List or Empty / Loading State
            if (isLoading && channels.isEmpty()) {
                ChannelListShimmer(
                    count = 10,
                    statusMessage = statusMessage ?: "Parsing & Loading M3U Playlist...",
                    modifier = Modifier.fillMaxSize()
                )
            } else if (!hasSelectedFirstPlaylist || currentPlaylist.id == "none") {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    StartWatchingLiveTvCard(
                        onAddOrSelectPlaylistClick = { onNavigateToPlaylists?.invoke() }
                    )
                }
            } else if (filteredChannels.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.TvOff,
                            contentDescription = null,
                            tint = appColors.textMuted,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No channels matching \"$searchQuery\""
                            else if (selectedCategory != null) "No channels found in \"$selectedCategory\""
                            else "No channels found in playlist",
                            color = appColors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Try clearing filters or search to see all channels",
                            color = appColors.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                val effectiveColumns = if (isGridView) maxOf(columns, 2) else columns
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = horizontalListPadding, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("channel_list_column")
                ) {
                    if (effectiveColumns == 1) {
                        itemsIndexed(
                            items = filteredChannels,
                            key = { index, channel -> "${channel.id}_${channel.streamUrl}_$index" }
                        ) { _, channel ->
                            ChannelListItem(
                                channel = channel,
                                isPlaying = channel.streamUrl == currentPlayingChannel?.streamUrl,
                                onClick = { onChannelClick(channel) },
                                onToggleFavorite = { onToggleFavorite(channel) }
                            )
                        }
                    } else {
                        val chunkedChannels = filteredChannels.chunked(effectiveColumns)
                        itemsIndexed(
                            items = chunkedChannels,
                            key = { index, row -> "${row.firstOrNull()?.id}_${row.firstOrNull()?.streamUrl}_$index" }
                        ) { _, rowChannels ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                for (channel in rowChannels) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (isGridView) {
                                            ChannelGridCard(
                                                channel = channel,
                                                isPlaying = channel.streamUrl == currentPlayingChannel?.streamUrl,
                                                onClick = { onChannelClick(channel) },
                                                onToggleFavorite = { onToggleFavorite(channel) }
                                            )
                                        } else {
                                            ChannelListItem(
                                                channel = channel,
                                                isPlaying = channel.streamUrl == currentPlayingChannel?.streamUrl,
                                                onClick = { onChannelClick(channel) },
                                                onToggleFavorite = { onToggleFavorite(channel) }
                                            )
                                        }
                                    }
                                }
                                val emptySlots = effectiveColumns - rowChannels.size
                                for (i in 0 until emptySlots) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    item(key = "bottom_spacer") {
                        Spacer(modifier = Modifier.height(96.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelGridCard(
    channel: Channel,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val isDark = appColors.isDark

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) {
                if (isDark) Color(0xFF2E1218) else Color(0xFFFEF2F2)
            } else {
                appColors.surface
            }
        ),
        border = BorderStroke(
            1.dp,
            if (isPlaying) Color(0xFFFECDD3) else appColors.border
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("channel_grid_${channel.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Channel Logo in Rounded Box with Favorite button overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(appColors.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                com.example.ui.components.ChannelLogo(
                    channel = channel,
                    fontSize = 16.sp,
                    cornerRadius = 12.dp,
                    imagePadding = 6.dp,
                    modifier = Modifier.fillMaxSize()
                )

                // Heart favorite top-end
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(28.dp)
                ) {
                    Icon(
                        imageVector = if (channel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (channel.isFavorite) OttPrimary else appColors.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Channel name
            Text(
                text = channel.name,
                color = if (isPlaying) OttPrimary else appColors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // LIVE badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFE50914)
                ) {
                    Text(
                        text = "• LIVE",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }

                Text(
                    text = channel.category.ifBlank { "General" },
                    color = appColors.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
