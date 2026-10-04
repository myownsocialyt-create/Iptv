package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Channel
import com.example.ui.components.ChannelListItem
import com.example.ui.theme.LocalAppColors

@Composable
fun FavoritesScreen(
    favorites: List<Channel>,
    currentPlayingChannel: Channel?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onChannelClick: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current

    val filteredFavorites by remember(favorites, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) favorites else {
                favorites.filter {
                    it.name.contains(searchQuery.trim(), ignoreCase = true) ||
                            it.category.contains(searchQuery.trim(), ignoreCase = true)
                }
            }
        }
    }

    if (favorites.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().background(appColors.background),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Icon(
                    imageVector = Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = appColors.textMuted,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "No Saved Channels",
                    color = appColors.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Tap the heart icon on any channel to add it to your favorites.",
                    color = appColors.textSecondary,
                    fontSize = 13.sp
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(appColors.background),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "Favorite Channels (${filteredFavorites.size})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = appColors.textPrimary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        itemsIndexed(
            items = filteredFavorites,
            key = { index, ch -> "${ch.id}_${ch.streamUrl}_$index" }
        ) { _, channel ->
            ChannelListItem(
                channel = channel,
                isPlaying = channel.streamUrl == currentPlayingChannel?.streamUrl,
                onClick = { onChannelClick(channel) },
                onToggleFavorite = { onToggleFavorite(channel) }
            )
        }
    }
}
