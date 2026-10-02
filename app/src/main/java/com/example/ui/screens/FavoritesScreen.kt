package com.example.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Channel
import com.example.ui.components.ChannelListItem
import com.example.ui.components.PosterAdCard
import com.example.ui.theme.LocalAppColors

@Composable
fun FavoritesScreen(
    favorites: List<Channel>,
    playingChannelUrl: String?,
    onPlayChannel: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current

    if (favorites.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = appColors.textSecondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Text(
                    text = "No Favorites Yet",
                    color = appColors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap the heart icon on any channel to save it here.",
                    color = appColors.textSecondary,
                    fontSize = 13.sp
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
    ) {
        item {
            Text(
                text = "Favorite Channels (${favorites.size})",
                color = appColors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )
        }

        // Poster Ad Card
        item {
            PosterAdCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
        }

        items(favorites, key = { it.id }) { channel ->
            ChannelListItem(
                channel = channel,
                isPlaying = channel.streamUrl == playingChannelUrl,
                onChannelClick = { onPlayChannel(channel) },
                onToggleFavorite = { onToggleFavorite(channel) }
            )
        }
    }
}
