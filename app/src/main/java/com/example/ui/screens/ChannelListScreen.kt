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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TvOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Channel
import com.example.data.repository.PlaylistSource
import com.example.data.repository.PresetPlaylists
import com.example.ui.components.ChannelListItem
import com.example.ui.components.ChannelListShimmer
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
            // Category Filter Chips & Refresh Button Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(appColors.surface)
                    .padding(horizontal = horizontalListPadding, vertical = 8.dp)
            ) {
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
                            FilterChip(
                                selected = selectedCategory == null,
                                onClick = { onCategorySelect(null) },
                                label = {
                                    Text(
                                        text = "All (${channels.size})",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = OttPrimary,
                                    selectedLabelColor = Color.White,
                                    containerColor = appColors.surfaceVariant,
                                    labelColor = appColors.textSecondary
                                ),
                                shape = RoundedCornerShape(16.dp)
                            )
                        }

                        itemsIndexed(categories, key = { index, cat -> "${cat}_$index" }) { _, cat ->
                            val isSelected = selectedCategory.equals(cat, ignoreCase = true)
                            val count = categoryCounts[cat] ?: 0
                            FilterChip(
                                selected = isSelected,
                                onClick = { onCategorySelect(if (isSelected) null else cat) },
                                label = {
                                    Text(
                                        text = "$cat ($count)",
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = OttPrimary,
                                    selectedLabelColor = Color.White,
                                    containerColor = appColors.surfaceVariant,
                                    labelColor = appColors.textSecondary
                                ),
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
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
                                .size(48.dp)
                                .padding(start = 4.dp)
                                .testTag("channel_list_refresh_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Channels",
                                tint = OttPrimary
                            )
                        }
                    }
                }

                // Active Category Filter Indicator Strip
                if (selectedCategory != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = OttPrimary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, OttPrimary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
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
                                    tint = OttPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Filtered by: ",
                                    color = appColors.textSecondary,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = selectedCategory,
                                    color = OttPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = " (${filteredChannels.size} channels)",
                                    color = appColors.textMuted,
                                    fontSize = 12.sp
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { onCategorySelect(null) }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Clear",
                                    color = OttPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear category filter",
                                    tint = OttPrimary,
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
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Surface(
                            color = OttPrimary.copy(alpha = 0.12f),
                            shape = androidx.compose.foundation.shape.CircleShape,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.TvOff,
                                    contentDescription = null,
                                    tint = OttPrimary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Playlist Selected",
                            color = appColors.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Please select or add a playlist to view channels.",
                            color = appColors.textSecondary,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        if (onNavigateToPlaylists != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onNavigateToPlaylists,
                                colors = ButtonDefaults.buttonColors(containerColor = OttPrimary),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                            ) {
                                Text("Add or Select Playlist", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
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
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = horizontalListPadding, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("channel_list_column")
                ) {
                    if (columns == 1) {
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
                        val chunkedChannels = filteredChannels.chunked(columns)
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
                                        ChannelListItem(
                                            channel = channel,
                                            isPlaying = channel.streamUrl == currentPlayingChannel?.streamUrl,
                                            onClick = { onChannelClick(channel) },
                                            onToggleFavorite = { onToggleFavorite(channel) }
                                        )
                                    }
                                }
                                val emptySlots = columns - rowChannels.size
                                for (i in 0 until emptySlots) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    item(key = "bottom_spacer") {
                        Spacer(modifier = Modifier.height(88.dp))
                    }
                }
            }
        }
    }
}
