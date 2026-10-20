package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.AppLanguage
import com.example.data.model.Channel
import com.example.data.repository.PlaylistSource
import com.example.ui.components.ChannelListItem
import com.example.ui.components.ChannelListShimmer
import com.example.ui.components.StartWatchingLiveTvCard
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.LocalAppColors
import com.example.ui.theme.OttGold
import com.example.ui.theme.OttPrimary

@Composable
fun HomeScreen(
    channels: List<Channel>,
    allChannels: List<Channel>,
    favorites: List<Channel>,
    recents: List<Channel>,
    currentPlayingChannel: Channel?,
    currentPlaylist: PlaylistSource,
    currentLanguage: AppLanguage,
    isLoading: Boolean,
    statusMessage: String?,
    searchQuery: String,
    isSearchActive: Boolean,
    hasSelectedFirstPlaylist: Boolean,
    onChannelClick: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onCategoryClick: (String) -> Unit,
    onAddPlaylistClick: () -> Unit,
    onViewAllPlaylistsClick: () -> Unit,
    onXtreamClick: () -> Unit,
    onRefresh: () -> Unit,
    onPlayChannel: ((Channel) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val strings = LocalAppStrings.current
    val isDark = appColors.isDark
    val handlePlayChannel = onPlayChannel ?: onChannelClick

    if (isLoading && channels.isEmpty()) {
        ChannelListShimmer(
            count = 10,
            statusMessage = statusMessage ?: "Loading Hypnotix Channels...",
            modifier = modifier.fillMaxSize()
        )
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(appColors.background),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // 1. Hero Featured Carousel (Screenshot 1)
        item {
            val firstChannel = channels.firstOrNull() ?: allChannels.firstOrNull()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF0D0D0D) else Color(0xFF0F172A)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(205.dp)
                        .clickable { firstChannel?.let { handlePlayChannel(it) } }
                        .testTag("hero_featured_card")
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Background image with retro TV feel
                        Image(
                            painter = painterResource(id = R.drawable.img_banner_live_tv),
                            contentDescription = "Live TV Banner",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Dark gradient overlay for contrast
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color.Black.copy(alpha = 0.88f),
                                            Color.Black.copy(alpha = 0.65f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )

                        // Left content column
                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(20.dp)
                        ) {
                            Text(
                                text = strings.liveTv,
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val countText = if (channels.isNotEmpty()) "${channels.size}+ ${strings.channel}" else "11143+ ${strings.channel}"
                            Text(
                                text = countText,
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(18.dp))

                            // Watch Now Pill Button
                            Button(
                                onClick = { firstChannel?.let { handlePlayChannel(it) } },
                                colors = ButtonDefaults.buttonColors(containerColor = OttPrimary),
                                shape = RoundedCornerShape(22.dp),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = strings.play,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Right side Retro IPTV icon badge
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 20.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.Black.copy(alpha = 0.8f),
                                border = BorderStroke(1.5.dp, Color(0xFFEF4444)),
                                modifier = Modifier.size(80.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_iptv_logo),
                                        contentDescription = "Hypnotix TV",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                    )
                                }
                            }
                        }
                    }
                }

                // Page indicator dots below carousel
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(22.dp)
                            .height(5.5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(OttPrimary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .size(5.5.dp)
                                .clip(CircleShape)
                                .background(if (isDark) Color(0xFF3F3F46) else Color(0xFFCBD5E1))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                }
            }
        }

        // 2. Active Playlist Card (Screenshot 1) - Only if playlist selected
        if (hasSelectedFirstPlaylist && currentPlaylist.id != "none") {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = appColors.surface),
                    border = BorderStroke(1.dp, appColors.border),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .clickable { onViewAllPlaylistsClick() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left light red rounded icon
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0xFF2E1218) else Color(0xFFFFEEEE)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notes,
                            contentDescription = null,
                            tint = OttPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    // Title & details
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${currentPlaylist.title} ${strings.channel}",
                                color = appColors.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isDark) Color(0xFF0F3820) else Color(0xFFDCFCE7)
                            ) {
                                Text(
                                    text = strings.active,
                                    color = if (isDark) Color(0xFF4ADE80) else Color(0xFF16A34A),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "${channels.size} ${strings.liveTvStreams}",
                            color = appColors.textSecondary,
                            fontSize = 12.sp
                        )
                    }

                    // Refresh button & Chevron
                    IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = OttPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Open Playlists",
                        tint = appColors.textMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        }

        // 3. Add your Playlist Navy Card (Screenshots 1 & 2)
        if (hasSelectedFirstPlaylist) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF111111) else Color(0xFF0F172A)),
                    border = BorderStroke(1.dp, if (isDark) Color(0xFF262626) else Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Top 3 Mini Category Highlights
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MiniHighlightCard(
                            tag = "Sport",
                            tagColor = Color(0xFFEA580C),
                            title = "This Week's Highlights",
                            modifier = Modifier.weight(1f),
                            onClick = { onCategoryClick("Sports") }
                        )
                        MiniHighlightCard(
                            tag = "Movie",
                            tagColor = Color(0xFF7C3AED),
                            title = "Timeless Stories Retold",
                            modifier = Modifier.weight(1f),
                            onClick = { onCategoryClick("Movies") }
                        )
                        MiniHighlightCard(
                            tag = "News",
                            tagColor = Color(0xFFDC2626),
                            title = "Headlines You Need Today",
                            modifier = Modifier.weight(1f),
                            onClick = { onCategoryClick("News") }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Title row with TV/HD/+999 badges
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.addPlaylist,
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = strings.watchManyChannels,
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }

                        // Badges
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFDC2626)
                            ) {
                                Text(
                                    text = "TV",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFDC2626)
                            ) {
                                Text(
                                    text = "HD",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF1E293B)
                            ) {
                                Text(
                                    text = "+999",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Full-width + Import Button
                    Button(
                        onClick = onAddPlaylistClick,
                        colors = ButtonDefaults.buttonColors(containerColor = OttPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("home_import_playlist_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = strings.importAction,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
        }

        // 4. Add your Xtream account Card (Screenshot 2)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = appColors.surface),
                border = BorderStroke(1.dp, appColors.border),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .clickable { onXtreamClick() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDark) Color(0xFF2E1218) else Color(0xFFFFEEEE))
                            .border(1.dp, if (isDark) Color(0xFF4C1D24) else Color(0xFFFFCDD2), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = OttPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.addXtreamAccount,
                            color = appColors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = strings.addXtreamDesc,
                            color = appColors.textSecondary,
                            fontSize = 12.sp
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = appColors.surfaceVariant,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.VideoLibrary,
                                contentDescription = null,
                                tint = OttPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }

        // 5. Category Filter Pills (Screenshot 2)
        item {
            val filterCategories = listOf("News", "Sports", "Movies", "Entertainment", "Music", "Kids", "Animation", "General")
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filterCategories) { cat ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = appColors.surface,
                        border = BorderStroke(1.dp, appColors.border),
                        modifier = Modifier.clickable { onCategoryClick(cat) }
                    ) {
                        Text(
                            text = cat,
                            color = appColors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (!hasSelectedFirstPlaylist) {
            // First time: Start Watching Live TV Card with "Add or Select Playlist" (Matching user screenshot)
            item(key = "start_watching_live_tv_home") {
                Spacer(modifier = Modifier.height(6.dp))
                StartWatchingLiveTvCard(
                    onAddOrSelectPlaylistClick = onViewAllPlaylistsClick,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        } else {
            // 6. Recently Watched (Screenshot 2)
            val recentsList = if (recents.isNotEmpty()) recents else channels.take(5)
            if (recentsList.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = OttPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.recentChannels,
                        color = appColors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = " (${recentsList.size})",
                        color = appColors.textMuted,
                        fontSize = 14.sp
                    )
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(recentsList) { ch ->
                        HomeChannelCard(
                            channel = ch,
                            onPlay = { handlePlayChannel(ch) },
                            onToggleFavorite = { onToggleFavorite(ch) }
                        )
                    }
                }
            }
        }

        // 7. My Favorites (Screenshot 2)
        val favsList = if (favorites.isNotEmpty()) favorites else channels.take(4)
        if (favsList.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = OttPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.favorites,
                        color = appColors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = " (${favsList.size})",
                        color = appColors.textMuted,
                        fontSize = 14.sp
                    )
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(favsList) { ch ->
                        HomeChannelCard(
                            channel = ch,
                            onPlay = { handlePlayChannel(ch) },
                            onToggleFavorite = { onToggleFavorite(ch) }
                        )
                    }
                }
            }
        }

        // 8. All Live Channels Section
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${strings.allLiveChannels} (${channels.size})",
                    color = appColors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = OttPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

            itemsIndexed(
                items = channels,
                key = { index, ch -> "${ch.id}_${ch.streamUrl}_$index" }
            ) { _, channel ->
                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                    ChannelListItem(
                        channel = channel,
                        isPlaying = channel.streamUrl == currentPlayingChannel?.streamUrl,
                        onClick = { handlePlayChannel(channel) },
                        onToggleFavorite = { onToggleFavorite(channel) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniHighlightCard(
    tag: String,
    tagColor: Color,
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1E293B),
        modifier = modifier
            .clickable(onClick = onClick)
            .height(78.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = RoundedCornerShape(3.dp),
                color = tagColor
            ) {
                Text(
                    text = tag,
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
            Text(
                text = title,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HomeChannelCard(
    channel: Channel,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = appColors.surface),
        border = BorderStroke(1.dp, appColors.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .width(165.dp)
            .clickable(onClick = onPlay)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Badges row: LIVE & Favorite
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    color = OttPrimary,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "• LIVE",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (channel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (channel.isFavorite) OttGold else appColors.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Channel Thumbnail / Logo with instant monogram fallback
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(appColors.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                com.example.ui.components.ChannelLogo(
                    channel = channel,
                    fontSize = 15.sp,
                    cornerRadius = 10.dp,
                    imagePadding = 6.dp,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Channel title
            Text(
                text = channel.name,
                color = appColors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Bottom row: Category Pill + Red Play circle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    color = appColors.surfaceVariant,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = channel.category.ifBlank { "Live" },
                        color = appColors.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = if (appColors.isDark) Color(0xFF2E1218) else Color(0xFFFFEEEE),
                    modifier = Modifier
                        .size(26.dp)
                        .clickable(onClick = onPlay)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = OttPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}
