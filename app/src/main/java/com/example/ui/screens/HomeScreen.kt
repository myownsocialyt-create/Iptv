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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Channel
import com.example.ui.components.ChannelCarousel
import com.example.ui.components.ChannelListShimmer
import com.example.ui.components.HeroBanner
import com.example.ui.components.PosterAdCard

@Composable
fun HomeScreen(
    channels: List<Channel>,
    favorites: List<Channel>,
    recents: List<Channel>,
    playingChannelUrl: String?,
    isLoading: Boolean,
    onPlayChannel: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    if (isLoading && channels.isEmpty()) {
        ChannelListShimmer(modifier = modifier.fillMaxSize().padding(top = 16.dp))
        return
    }

    val featuredChannel = channels.firstOrNull()
    val newsChannels = channels.filter { it.category.contains("News", ignoreCase = true) }
    val sportsChannels = channels.filter { it.category.contains("Sports", ignoreCase = true) }
    val moviesChannels = channels.filter { it.category.contains("Movie", ignoreCase = true) || it.isMovieOrVod }
    val musicChannels = channels.filter { it.category.contains("Music", ignoreCase = true) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        // Featured Hero Banner
        item {
            HeroBanner(
                featuredChannel = featuredChannel,
                onPlayChannel = onPlayChannel
            )
        }

        // Native Poster Ad Card (Seamlessly embedded in the feed)
        item {
            PosterAdCard(modifier = Modifier.fillMaxWidth())
        }

        // Recently Watched
        if (recents.isNotEmpty()) {
            item {
                ChannelCarousel(
                    title = "Recently Watched",
                    channels = recents,
                    playingChannelUrl = playingChannelUrl,
                    onChannelClick = onPlayChannel,
                    onToggleFavorite = onToggleFavorite
                )
            }
        }

        // Favorites
        if (favorites.isNotEmpty()) {
            item {
                ChannelCarousel(
                    title = "My Favorites",
                    channels = favorites,
                    playingChannelUrl = playingChannelUrl,
                    onChannelClick = onPlayChannel,
                    onToggleFavorite = onToggleFavorite
                )
            }
        }

        // Live News
        if (newsChannels.isNotEmpty()) {
            item {
                ChannelCarousel(
                    title = "Top Live News",
                    channels = newsChannels,
                    playingChannelUrl = playingChannelUrl,
                    onChannelClick = onPlayChannel,
                    onToggleFavorite = onToggleFavorite
                )
            }
        }

        // Movies & Cinema
        if (moviesChannels.isNotEmpty()) {
            item {
                ChannelCarousel(
                    title = "Movies & Cinema",
                    channels = moviesChannels,
                    playingChannelUrl = playingChannelUrl,
                    onChannelClick = onPlayChannel,
                    onToggleFavorite = onToggleFavorite
                )
            }
        }

        // Live Sports
        if (sportsChannels.isNotEmpty()) {
            item {
                ChannelCarousel(
                    title = "Live Sports",
                    channels = sportsChannels,
                    playingChannelUrl = playingChannelUrl,
                    onChannelClick = onPlayChannel,
                    onToggleFavorite = onToggleFavorite
                )
            }
        }

        // Music Channels
        if (musicChannels.isNotEmpty()) {
            item {
                ChannelCarousel(
                    title = "Non-Stop Music",
                    channels = musicChannels,
                    playingChannelUrl = playingChannelUrl,
                    onChannelClick = onPlayChannel,
                    onToggleFavorite = onToggleFavorite
                )
            }
        }
    }
}
